package eu.kanade.tachiyomi.ui.browse.source.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.core.util.ifSourcesLoaded
import eu.kanade.presentation.browse.MissingSourceScreen
import eu.kanade.presentation.library.components.CommonMangaItemDefaults
import eu.kanade.presentation.library.components.MangaCompactGridItem
import eu.kanade.presentation.library.components.MangaComfortableGridItem
import eu.kanade.presentation.library.components.MangaListItem
import eu.kanade.presentation.browse.components.BrowseSourceToolbar
import eu.kanade.presentation.browse.components.InLibraryBadge
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.source.AnimeCatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.SAnime
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.extension.details.SourcePreferencesScreen
import eu.kanade.tachiyomi.ui.browse.source.anime.BrowseAnimeSourceScreenModel.Listing
import eu.kanade.tachiyomi.ui.browse.source.browse.SourceFilterDialog
import tachiyomi.domain.source.model.StubSource
import tachiyomi.i18n.MR
import tachiyomi.domain.library.model.LibraryDisplayMode
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus
import tachiyomi.domain.manga.model.MangaCover

data class BrowseAnimeSourceScreen(
    val sourceId: Long,
    private val listingQuery: String?,
) : Screen() {

    @Composable
    override fun Content() {
        if (!ifSourcesLoaded()) {
            LoadingScreen()
            return
        }

        val screenModel = rememberScreenModel { BrowseAnimeSourceScreenModel(sourceId, listingQuery) }
        val state by screenModel.state.collectAsState()
        val animeList = screenModel.animePagerFlow.collectAsLazyPagingItems()
        val animeSource = screenModel.source as? AnimeCatalogueSource

        val navigator = LocalNavigator.currentOrThrow
        val snackbarHostState = remember { SnackbarHostState() }

        val navigateUp: () -> Unit = {
            when {
                !state.isUserQuery && state.toolbarQuery != null -> screenModel.setToolbarQuery(null)
                else -> navigator.pop()
            }
        }

        if (screenModel.source is StubSource) {
            MissingSourceScreen(
                source = screenModel.source as StubSource,
                navigateUp = navigateUp,
            )
            return
        }

        val errorState = animeList.loadState.refresh.takeIf { it is LoadState.Error }
            ?: animeList.loadState.append.takeIf { it is LoadState.Error }

        LaunchedEffect(errorState) {
            val error = errorState as? LoadState.Error ?: return@LaunchedEffect
            if (animeList.itemCount == 0) return@LaunchedEffect

            val result = snackbarHostState.showSnackbar(
                message = error.error.message ?: "Failed to load anime.",
                actionLabel = "Retry",
                duration = SnackbarDuration.Indefinite,
            )
            if (result == SnackbarResult.ActionPerformed) {
                animeList.retry()
            }
        }

        Scaffold(
            topBar = {
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                ) {
                    BrowseSourceToolbar(
                        searchQuery = state.toolbarQuery,
                        onSearchQueryChange = screenModel::setToolbarQuery,
                        source = screenModel.source,
                        displayMode = screenModel.displayMode,
                        onDisplayModeChange = { screenModel.displayMode = it },
                        navigateUp = navigateUp,
                        onWebViewClick = {
                            val webUrl = (screenModel.source as? HttpSource)?.baseUrl ?: return@BrowseSourceToolbar
                            navigator.push(
                                eu.kanade.tachiyomi.ui.webview.WebViewScreen(
                                    url = webUrl,
                                    initialTitle = screenModel.source.name,
                                    sourceId = screenModel.source.id,
                                ),
                            )
                        },
                        onHelpClick = {},
                        onSettingsClick = {
                            if (screenModel.source is ConfigurableSource) {
                                navigator.push(SourcePreferencesScreen(sourceId))
                            }
                        },
                        onSearch = { screenModel.search(it) },
                    )

                    Row(
                        modifier = Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = MaterialTheme.padding.small),
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        FilterChip(
                            selected = state.listing == Listing.Popular,
                            onClick = {
                                screenModel.resetFilters()
                                screenModel.setListing(Listing.Popular)
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Favorite,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                                )
                            },
                            label = { Text(stringResource(MR.strings.popular)) },
                        )
                        if (animeSource?.supportsLatest == true) {
                            FilterChip(
                                selected = state.listing == Listing.Latest,
                                onClick = {
                                    screenModel.resetFilters()
                                    screenModel.setListing(Listing.Latest)
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.NewReleases,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                },
                                label = { Text(stringResource(MR.strings.latest)) },
                            )
                        }
                        if (state.filters.isNotEmpty()) {
                            FilterChip(
                                selected = state.listing is Listing.Search,
                                onClick = screenModel::openFilterSheet,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.FilterList,
                                        contentDescription = null,
                                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                                    )
                                },
                                label = { Text(stringResource(MR.strings.action_filter)) },
                            )
                        }
                    }

                    HorizontalDivider()
                }
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            when {
                animeList.itemCount == 0 && animeList.loadState.refresh is LoadState.Loading -> {
                    LoadingScreen(Modifier.padding(paddingValues))
                }

                animeList.itemCount == 0 -> {
                    EmptyScreen(
                        modifier = Modifier.padding(paddingValues),
                        message = (errorState as? LoadState.Error)?.error?.message ?: "No anime found.",
                    )
                }

                else -> {
                    val contentPadding = paddingValues + PaddingValues(
                        start = MaterialTheme.padding.small,
                        top = MaterialTheme.padding.small,
                        end = MaterialTheme.padding.small,
                        bottom = MaterialTheme.padding.medium,
                    )
                    when (screenModel.displayMode) {
                        LibraryDisplayMode.List -> AnimeBrowseList(
                            animeList = animeList,
                            contentPadding = contentPadding,
                            sourceId = sourceId,
                            onAnimeClick = { navigator.push(AnimeDetailsScreen(sourceId, it)) },
                        )
                        LibraryDisplayMode.ComfortableGrid -> AnimeBrowseGrid(
                            animeList = animeList,
                            sourceId = sourceId,
                            contentPadding = contentPadding,
                            columns = screenModel.getColumnsPreference(LocalConfiguration.current.orientation),
                            compact = false,
                            showTitle = true,
                            onAnimeClick = { navigator.push(AnimeDetailsScreen(sourceId, it)) },
                        )
                        LibraryDisplayMode.CompactGrid, LibraryDisplayMode.CoverOnlyGrid -> AnimeBrowseGrid(
                            animeList = animeList,
                            sourceId = sourceId,
                            contentPadding = contentPadding,
                            columns = screenModel.getColumnsPreference(LocalConfiguration.current.orientation),
                            compact = true,
                            showTitle = screenModel.displayMode == LibraryDisplayMode.CompactGrid,
                            onAnimeClick = { navigator.push(AnimeDetailsScreen(sourceId, it)) },
                        )
                    }
                }
            }
        }

        when (state.dialog) {
            BrowseAnimeSourceScreenModel.Dialog.Filter -> {
                SourceFilterDialog(
                    onDismissRequest = { screenModel.setDialog(null) },
                    filters = state.filters,
                    onReset = screenModel::resetFilters,
                    onFilter = { screenModel.search(filters = state.filters) },
                    onUpdate = screenModel::setFilters,
                )
            }
            null -> Unit
        }
    }
}

@Composable
private fun AnimeBrowseList(
    animeList: androidx.paging.compose.LazyPagingItems<SAnime>,
    contentPadding: PaddingValues,
    sourceId: Long,
    onAnimeClick: (SAnime) -> Unit,
) {
    LazyColumn(
        contentPadding = contentPadding + PaddingValues(vertical = 8.dp),
    ) {
        items(
            count = animeList.itemCount,
            key = { index -> animeList[index]?.url ?: index },
        ) { index ->
            val anime = animeList[index] ?: return@items
            MangaListItem(
                title = anime.title,
                coverData = anime.asCoverData(sourceId),
                badge = { InLibraryBadge(enabled = false) },
                onClick = { onAnimeClick(anime) },
                onLongClick = { onAnimeClick(anime) },
            )
        }
    }
}

@Composable
private fun AnimeBrowseGrid(
    animeList: androidx.paging.compose.LazyPagingItems<SAnime>,
    sourceId: Long,
    contentPadding: PaddingValues,
    columns: GridCells,
    compact: Boolean,
    showTitle: Boolean,
    onAnimeClick: (SAnime) -> Unit,
) {
    LazyVerticalGrid(
        columns = columns,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridHorizontalSpacer),
        verticalArrangement = Arrangement.spacedBy(CommonMangaItemDefaults.GridVerticalSpacer),
    ) {
        items(
            count = animeList.itemCount,
            key = { index -> animeList[index]?.url ?: index },
        ) { index ->
            val anime = animeList[index] ?: return@items
            if (compact) {
                MangaCompactGridItem(
                    title = anime.title.takeIf { showTitle },
                    coverData = anime.asCoverData(sourceId),
                    coverBadgeStart = { InLibraryBadge(enabled = false) },
                    onClick = { onAnimeClick(anime) },
                    onLongClick = { onAnimeClick(anime) },
                )
            } else {
                MangaComfortableGridItem(
                    coverData = anime.asCoverData(sourceId),
                    title = anime.title,
                    coverBadgeStart = { InLibraryBadge(enabled = false) },
                    onClick = { onAnimeClick(anime) },
                    onLongClick = { onAnimeClick(anime) },
                )
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            if (animeList.loadState.append is LoadState.Loading) {
                Text(
                    text = "Loading more...",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(MaterialTheme.padding.medium),
                )
            }
        }
    }
}

private fun SAnime.asCoverData(sourceId: Long): MangaCover {
    return MangaCover(
        mangaId = 0L,
        sourceId = sourceId,
        isMangaFavorite = false,
        url = thumbnail_url,
        lastModified = 0L,
    )
}
