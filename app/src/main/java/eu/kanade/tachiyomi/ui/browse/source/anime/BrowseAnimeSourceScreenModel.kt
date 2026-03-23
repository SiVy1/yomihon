package eu.kanade.tachiyomi.ui.browse.source.anime

import androidx.compose.runtime.Immutable
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.cachedIn
import cafe.adriel.voyager.core.model.StateScreenModel
import eu.kanade.presentation.util.ioCoroutineScope
import eu.kanade.tachiyomi.source.AnimeCatalogueSource
import eu.kanade.tachiyomi.source.Source
import eu.kanade.tachiyomi.source.model.AnimesPage
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.SAnime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import tachiyomi.domain.source.service.SourceManager
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class BrowseAnimeSourceScreenModel(
    private val sourceId: Long,
    listingQuery: String?,
    sourceManager: SourceManager = Injekt.get(),
) : StateScreenModel<BrowseAnimeSourceScreenModel.State>(State(Listing.valueOf(listingQuery))) {

    val source: Source = sourceManager.getOrStub(sourceId)

    init {
        if (source is AnimeCatalogueSource) {
            mutableState.update {
                var query: String? = null
                var listing = it.listing

                if (listing is Listing.Search) {
                    query = listing.query
                    listing = Listing.Search(query, source.getFilterList())
                }

                it.copy(
                    listing = listing,
                    filters = source.getFilterList(),
                    toolbarQuery = query,
                )
            }
        }
    }

    val animePagerFlowFlow = state.map { it.listing }
        .distinctUntilChanged()
        .map { listing ->
            val animeSource = source as? AnimeCatalogueSource ?: return@map emptyFlow()
            Pager(PagingConfig(pageSize = 24)) {
                AnimeBrowsePagingSource(animeSource, listing)
            }.flow.cachedIn(ioCoroutineScope)
        }
        .stateIn(ioCoroutineScope, SharingStarted.Lazily, emptyFlow())

    fun resetFilters() {
        val animeSource = source as? AnimeCatalogueSource ?: return
        mutableState.update { it.copy(filters = animeSource.getFilterList()) }
    }

    fun setListing(listing: Listing) {
        mutableState.update { it.copy(listing = listing, toolbarQuery = null) }
    }

    fun setFilters(filters: FilterList) {
        if (source !is AnimeCatalogueSource) return
        mutableState.update { it.copy(filters = filters) }
    }

    fun search(query: String? = null, filters: FilterList? = null) {
        val animeSource = source as? AnimeCatalogueSource ?: return

        val input = state.value.listing as? Listing.Search
            ?: Listing.Search(query = null, filters = animeSource.getFilterList())

        mutableState.update {
            it.copy(
                listing = input.copy(
                    query = query ?: input.query,
                    filters = filters ?: input.filters,
                ),
                toolbarQuery = query ?: input.query,
            )
        }
    }

    fun openFilterSheet() {
        setDialog(Dialog.Filter)
    }

    fun setDialog(dialog: Dialog?) {
        mutableState.update { it.copy(dialog = dialog) }
    }

    fun setToolbarQuery(query: String?) {
        mutableState.update { it.copy(toolbarQuery = query) }
    }

    sealed class Listing(open val query: String?, open val filters: FilterList) {
        data object Popular : Listing(query = QUERY_POPULAR, filters = FilterList())
        data object Latest : Listing(query = QUERY_LATEST, filters = FilterList())
        data class Search(
            override val query: String?,
            override val filters: FilterList,
        ) : Listing(query = query, filters = filters)

        companion object {
            fun valueOf(query: String?): Listing {
                return when (query) {
                    QUERY_POPULAR -> Popular
                    QUERY_LATEST -> Latest
                    else -> Search(query = query, filters = FilterList())
                }
            }
        }
    }

    sealed interface Dialog {
        data object Filter : Dialog
    }

    @Immutable
    data class State(
        val listing: Listing,
        val filters: FilterList = FilterList(),
        val toolbarQuery: String? = null,
        val dialog: Dialog? = null,
    ) {
        val isUserQuery get() = listing is Listing.Search && !listing.query.isNullOrEmpty()
    }

    companion object {
        const val QUERY_POPULAR = "__popular_anime__"
        const val QUERY_LATEST = "__latest_anime__"
    }
}

private class AnimeBrowsePagingSource(
    private val source: AnimeCatalogueSource,
    private val listing: BrowseAnimeSourceScreenModel.Listing,
) : PagingSource<Int, SAnime>() {

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SAnime> {
        val page = params.key ?: 1
        return try {
            val result = when (listing) {
                BrowseAnimeSourceScreenModel.Listing.Popular -> source.getPopularAnime(page)
                BrowseAnimeSourceScreenModel.Listing.Latest -> source.getLatestUpdates(page)
                is BrowseAnimeSourceScreenModel.Listing.Search -> {
                    source.getSearchAnime(
                        page = page,
                        query = listing.query.orEmpty(),
                        filters = listing.filters,
                    )
                }
            }
            result.toPage(page)
        } catch (e: Throwable) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<Int, SAnime>): Int? {
        return state.anchorPosition?.let { position ->
            state.closestPageToPosition(position)?.prevKey?.plus(1)
                ?: state.closestPageToPosition(position)?.nextKey?.minus(1)
        }
    }

    private fun AnimesPage.toPage(page: Int): LoadResult.Page<Int, SAnime> {
        return LoadResult.Page(
            data = animes,
            prevKey = if (page == 1) null else page - 1,
            nextKey = if (hasNextPage) page + 1 else null,
        )
    }
}
