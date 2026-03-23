package eu.kanade.tachiyomi.ui.anime.history

import androidx.compose.runtime.Immutable
import cafe.adriel.voyager.core.model.StateScreenModel
import cafe.adriel.voyager.core.model.screenModelScope
import eu.kanade.core.util.insertSeparators
import eu.kanade.presentation.components.SEARCH_DEBOUNCE_MILLIS
import eu.kanade.tachiyomi.util.lang.toLocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import tachiyomi.core.common.util.lang.launchIO
import tachiyomi.domain.anime.interactor.GetAnimeHistory
import tachiyomi.domain.anime.model.AnimeHistoryWithRelations
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeHistoryScreenModel(
    private val getAnimeHistory: GetAnimeHistory = Injekt.get(),
) : StateScreenModel<AnimeHistoryScreenModel.State>(State()) {

    init {
        screenModelScope.launchIO {
            state.map { it.searchQuery.orEmpty() }
                .distinctUntilChanged()
                .debounceLatest()
                .flatMapLatest { query ->
                    getAnimeHistory.subscribe(query)
                        .distinctUntilChanged()
                        .map { it.toUiModels() }
                        .flowOn(Dispatchers.IO)
                }
                .collect { items ->
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

    private fun List<AnimeHistoryWithRelations>.toUiModels(): List<AnimeHistoryUiModel> {
        return map { AnimeHistoryUiModel.Item(it) }
            .insertSeparators { before, after ->
                val beforeDate = before?.item?.watchedAt?.takeIf { it > 0L }?.toLocalDate()
                val afterDate = after?.item?.watchedAt?.takeIf { it > 0L }?.toLocalDate()
                when {
                    beforeDate != afterDate && afterDate != null -> AnimeHistoryUiModel.Header(afterDate)
                    else -> null
                }
            }
    }

    private fun kotlinx.coroutines.flow.Flow<String>.debounceLatest() =
        debounce(SEARCH_DEBOUNCE_MILLIS)

    @Immutable
    data class State(
        val isLoading: Boolean = true,
        val searchQuery: String? = null,
        val items: List<AnimeHistoryUiModel> = emptyList(),
    ) {
        val isEmpty: Boolean
            get() = !isLoading && items.isEmpty()
    }
}

sealed interface AnimeHistoryUiModel {
    data class Header(val date: java.time.LocalDate) : AnimeHistoryUiModel
    data class Item(val item: AnimeHistoryWithRelations) : AnimeHistoryUiModel
}
