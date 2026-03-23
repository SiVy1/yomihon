package eu.kanade.tachiyomi.ui.anime.library

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.anime.interactor.GetLibraryAnime
import tachiyomi.domain.anime.model.LibraryAnime
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeLibraryScreenModel(
    private val getLibraryAnime: GetLibraryAnime = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
) : StateScreenModel<AnimeLibraryScreenModel.State>(State()) {

    init {
        screenModelScope.launchIO {
            combine(
                state.map { it.searchQuery }.distinctUntilChanged().debounce(SEARCH_DEBOUNCE_MILLIS),
                state.map { it.filter }.distinctUntilChanged(),
                getLibraryAnime.subscribe(),
            ) { searchQuery, filter, libraryAnime ->
                val searchFiltered = if (searchQuery.isNullOrBlank()) {
                    libraryAnime
                } else {
                    libraryAnime.filter { it.matches(searchQuery) }
                }
                filter.apply(searchFiltered)
            }.collectLatest { items ->
                mutableState.update {
                    it.copy(
                        isLoading = false,
                        items = items,
                    )
                }
            }
        }
    }

    fun search(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun setFilter(filter: AnimeLibraryFilter) {
        mutableState.update { it.copy(filter = filter) }
    }

    private fun LibraryAnime.matches(query: String): Boolean {
        val sourceName = sourceManager.getOrStub(anime.source).name
        return anime.title.contains(query, ignoreCase = true) ||
            (anime.author?.contains(query, ignoreCase = true) == true) ||
            (anime.artist?.contains(query, ignoreCase = true) == true) ||
            (anime.description?.contains(query, ignoreCase = true) == true) ||
            sourceName.contains(query, ignoreCase = true) ||
            (anime.genre?.any { it.contains(query, ignoreCase = true) } == true)
    }

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val searchQuery: String? = null,
        val filter: AnimeLibraryFilter = AnimeLibraryFilter.All,
        val items: List<LibraryAnime> = emptyList(),
    ) {
        val isEmpty: Boolean
            get() = !isLoading && items.isEmpty()
    }
}

enum class AnimeLibraryFilter(
    val label: String,
) {
    All("All"),
    ContinueWatching("Continue"),
    Unwatched("Unwatched"),
    Bookmarked("Bookmarked"),
    ;

    fun apply(items: List<LibraryAnime>): List<LibraryAnime> {
        val filtered = when (this) {
            All -> items
            ContinueWatching -> items.filter { it.isContinuing }
            Unwatched -> items.filter { it.unseenCount > 0 }
            Bookmarked -> items.filter(LibraryAnime::hasBookmarks)
        }

        return filtered.sortedWith(
            compareByDescending<LibraryAnime> {
                when (this) {
                    ContinueWatching -> it.lastWatched
                    else -> 0L
                }
            }
                .thenByDescending { it.lastWatched }
                .thenBy { it.anime.title.lowercase() },
        )
    }
}
