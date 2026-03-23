package eu.kanade.tachiyomi.ui.browse.source.anime.globalsearch

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.domain.source.service.SourcePreferences
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.source.AnimeCatalogueSource
import eu.kanade.tachiyomi.source.model.SAnime
import eu.kanade.tachiyomi.ui.browse.source.globalsearch.SourceFilter
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.mutate
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tachiyomi.core.common.preference.toggle
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.Executors

class AnimeGlobalSearchScreenModel(
    initialQuery: String = "",
    sourcePreferences: SourcePreferences = Injekt.get(),
    private val sourceManager: SourceManager = Injekt.get(),
    private val preferences: SourcePreferences = Injekt.get(),
) : StateScreenModel<AnimeGlobalSearchScreenModel.State>(
    State(searchQuery = initialQuery),
) {

    private val coroutineDispatcher = Executors.newFixedThreadPool(5).asCoroutineDispatcher()
    private var searchJob: Job? = null

    private val enabledLanguages = sourcePreferences.enabledLanguages.get()
    private val disabledSources = sourcePreferences.disabledSources.get()
    private val pinnedSources = sourcePreferences.pinnedSources.get()

    private var lastQuery: String? = null
    private var lastSourceFilter: SourceFilter? = null

    private val sortComparator = { map: Map<AnimeCatalogueSource, AnimeSearchItemResult> ->
        compareBy<AnimeCatalogueSource>(
            { (map[it] as? AnimeSearchItemResult.Success)?.isEmpty ?: true },
            { "${it.id}" !in pinnedSources },
            { "${it.name.lowercase()} (${it.lang})" },
        )
    }

    init {
        screenModelScope.launch {
            preferences.globalSearchFilterState.changes().collectLatest { onlyShowResults ->
                mutableState.update { it.copy(onlyShowHasResults = onlyShowResults) }
            }
        }

        if (initialQuery.isNotBlank()) {
            search()
        }
    }

    fun updateSearchQuery(query: String?) {
        mutableState.update { it.copy(searchQuery = query) }
    }

    fun setSourceFilter(filter: SourceFilter) {
        mutableState.update { it.copy(sourceFilter = filter) }
        search()
    }

    fun toggleFilterResults() {
        preferences.globalSearchFilterState.toggle()
    }

    fun search() {
        val query = state.value.searchQuery
        val sourceFilter = state.value.sourceFilter

        if (query.isNullOrBlank()) return

        val sameQuery = lastQuery == query
        if (sameQuery && lastSourceFilter == sourceFilter) return

        lastQuery = query
        lastSourceFilter = sourceFilter

        searchJob?.cancel()

        val sources = getEnabledSources()

        if (sameQuery) {
            val existingResults = state.value.items
            updateItems(
                sources
                    .associateWith { existingResults[it] ?: AnimeSearchItemResult.Loading }
                    .toPersistentMap(),
            )
        } else {
            updateItems(
                sources
                    .associateWith { AnimeSearchItemResult.Loading }
                    .toPersistentMap(),
            )
        }

        searchJob = ioCoroutineScope.launch {
            sources.map { source ->
                async {
                    if (state.value.items[source] !is AnimeSearchItemResult.Loading) {
                        return@async
                    }

                    try {
                        val page = withContext(coroutineDispatcher) {
                            source.getSearchAnime(1, query, source.getFilterList())
                        }

                        val animes = page.animes.distinctBy { it.url }
                        if (isActive) {
                            updateItem(source, AnimeSearchItemResult.Success(animes))
                        }
                    } catch (e: Exception) {
                        if (isActive) {
                            updateItem(source, AnimeSearchItemResult.Error(e))
                        }
                    }
                }
            }.awaitAll()
        }
    }

    private fun getEnabledSources(): List<AnimeCatalogueSource> {
        return sourceManager.getAnimeCatalogueSources()
            .filter { it.lang in enabledLanguages && "${it.id}" !in disabledSources }
            .filter { state.value.sourceFilter != SourceFilter.PinnedOnly || "${it.id}" in pinnedSources }
            .sortedWith(
                compareBy(
                    { "${it.id}" !in pinnedSources },
                    { "${it.name.lowercase()} (${it.lang})" },
                ),
            )
    }

    private fun updateItems(items: PersistentMap<AnimeCatalogueSource, AnimeSearchItemResult>) {
        mutableState.update {
            it.copy(
                items = items
                    .toSortedMap(sortComparator(items))
                    .toPersistentMap(),
            )
        }
    }

    private fun updateItem(source: AnimeCatalogueSource, result: AnimeSearchItemResult) {
        val newItems = state.value.items.mutate {
            it[source] = result
        }
        updateItems(newItems)
    }

    @Immutable
    data class State(
        val searchQuery: String? = null,
        val sourceFilter: SourceFilter = SourceFilter.PinnedOnly,
        val onlyShowHasResults: Boolean = false,
        val items: PersistentMap<AnimeCatalogueSource, AnimeSearchItemResult> = persistentMapOf(),
    ) {
        val progress: Int = items.count { it.value !is AnimeSearchItemResult.Loading }
        val total: Int = items.size
        val filteredItems = items.filter { (_, result) -> result.isVisible(onlyShowHasResults) }
    }
}

sealed interface AnimeSearchItemResult {
    data object Loading : AnimeSearchItemResult

    data class Error(
        val throwable: Throwable,
    ) : AnimeSearchItemResult

    data class Success(
        val result: List<SAnime>,
    ) : AnimeSearchItemResult {
        val isEmpty: Boolean
            get() = result.isEmpty()
    }

    fun isVisible(onlyShowHasResults: Boolean): Boolean {
        return !onlyShowHasResults || (this is Success && !isEmpty)
    }
}
