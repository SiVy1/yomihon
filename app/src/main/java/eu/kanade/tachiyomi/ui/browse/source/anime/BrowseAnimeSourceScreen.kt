package eu.kanade.tachiyomi.ui.browse.source.anime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.Public
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.core.util.ifSourcesLoaded
import eu.kanade.presentation.browse.MissingSourceScreen
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.util.rememberResourceBitmapPainter
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.AnimeCatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.SAnime
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.browse.extension.details.SourcePreferencesScreen
import eu.kanade.tachiyomi.ui.browse.source.anime.BrowseAnimeSourceScreenModel.Listing
import eu.kanade.tachiyomi.ui.browse.source.browse.SourceFilterDialog
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.source.model.StubSource
import tachiyomi.i18n.MR
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus

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
                Column(
                    modifier = Modifier.background(MaterialTheme.colorScheme.surface),
                ) {
                    SearchToolbar(
                        titleContent = { AppBarTitle(screenModel.source.name) },
                        searchQuery = state.toolbarQuery,
                        onChangeSearchQuery = screenModel::setToolbarQuery,
                        navigateUp = navigateUp,
                        onSearch = { screenModel.search(it) },
                        actions = {
                            AppBarActions(
                                actions = persistentListOf<AppBar.AppBarAction>().builder()
                                    .apply {
                                        val webUrl = (screenModel.source as? HttpSource)?.baseUrl
                                        if (webUrl != null) {
                                            add(
                                                AppBar.Action(
                                                    title = "Open site",
                                                    icon = Icons.Outlined.Public,
                                                    onClick = {
                                                        navigator.push(
                                                            eu.kanade.tachiyomi.ui.webview.WebViewScreen(
                                                                url = webUrl,
                                                                initialTitle = screenModel.source.name,
                                                                sourceId = screenModel.source.id,
                                                            ),
                                                        )
                                                    },
                                                ),
                                            )
                                        }
                                        if (screenModel.source is ConfigurableSource) {
                                            add(
                                                AppBar.OverflowAction(
                                                    title = "Settings",
                                                    onClick = { navigator.push(SourcePreferencesScreen(sourceId)) },
                                                ),
                                            )
                                        }
                                    }
                                    .build(),
                            )
                        },
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
                    LazyColumn(
                        contentPadding = paddingValues + PaddingValues(vertical = 8.dp),
                    ) {
                        items(count = animeList.itemCount) { index ->
                            val anime = animeList[index] ?: return@items
                            AnimeBrowseItem(
                                anime = anime,
                                onClick = { navigator.push(AnimeDetailsScreen(sourceId, anime)) },
                            )
                        }

                        item {
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
private fun AnimeBrowseItem(
    anime: SAnime,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = anime.thumbnail_url,
            contentDescription = anime.title,
            error = rememberResourceBitmapPainter(id = R.drawable.cover_error),
            modifier = Modifier
                .size(width = 64.dp, height = 96.dp)
                .clip(MaterialTheme.shapes.small),
        )

        Column(
            modifier = Modifier
                .padding(start = MaterialTheme.padding.medium)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = anime.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            anime.genre?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            anime.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
