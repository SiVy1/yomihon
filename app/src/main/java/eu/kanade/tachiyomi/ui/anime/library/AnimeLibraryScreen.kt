package eu.kanade.tachiyomi.ui.anime.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.domain.anime.model.toSAnime
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.AppBarActions
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.anime.history.AnimeHistoryScreen
import eu.kanade.tachiyomi.ui.browse.source.anime.AnimeDetailsScreen
import kotlinx.collections.immutable.persistentListOf
import tachiyomi.domain.anime.model.LibraryAnime
import tachiyomi.domain.source.service.SourceManager
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimeLibraryScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { AnimeLibraryScreenModel() }
        val state by screenModel.state.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }

        Scaffold(
            topBar = {
                SearchToolbar(
                    titleContent = { AppBarTitle("Anime Library") },
                    searchQuery = state.searchQuery,
                    onChangeSearchQuery = screenModel::search,
                    navigateUp = navigator::pop,
                    actions = {
                        AppBarActions(
                            persistentListOf(
                                AppBar.Action(
                                    title = "Anime history",
                                    icon = Icons.Outlined.History,
                                    onClick = { navigator.push(AnimeHistoryScreen()) },
                                ),
                            ),
                        )
                    },
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            when {
                state.isLoading -> LoadingScreen(Modifier.padding(paddingValues))
                state.isEmpty -> {
                    EmptyScreen(
                        modifier = Modifier.padding(paddingValues),
                        message = "No anime in library yet.",
                    )
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                    ) {
                        AnimeLibraryFilters(
                            selected = state.filter,
                            onSelect = screenModel::setFilter,
                        )

                        LazyColumn(
                            contentPadding = PaddingValues(bottom = MaterialTheme.padding.medium),
                        ) {
                            items(
                                items = state.items,
                                key = { it.id },
                            ) { item ->
                                AnimeLibraryRow(
                                    item = item,
                                    onClick = {
                                        navigator.push(
                                            AnimeDetailsScreen(
                                                sourceId = item.anime.source,
                                                anime = item.anime.toSAnime(),
                                            ),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnimeLibraryFilters(
    selected: AnimeLibraryFilter,
    onSelect: (AnimeLibraryFilter) -> Unit,
) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
    ) {
        AnimeLibraryFilter.entries.forEach { filter ->
            FilterChip(
                selected = selected == filter,
                onClick = { onSelect(filter) },
                label = { Text(filter.label) },
            )
        }
    }

    HorizontalDivider()
}

@Composable
private fun AnimeLibraryRow(
    item: LibraryAnime,
    onClick: () -> Unit,
    sourceManager: SourceManager = Injekt.get(),
) {
    val sourceName = sourceManager.getOrStub(item.anime.source).name
    val metadata = buildList {
        add(sourceName)
        if (item.totalEpisodes > 0L) add("${item.seenCount}/${item.totalEpisodes} seen")
        if (item.bookmarkCount > 0L) add("${item.bookmarkCount} bookmarked")
    }.joinToString(" - ")
    val lastWatchedText = item.lastWatched
        .takeIf { it > 0L }
        ?.let { "Last watched ${relativeTimeSpanString(it)}" }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.anime.thumbnailUrl,
            contentDescription = item.anime.title,
            error = painterResource(R.drawable.cover_error),
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
                text = item.anime.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = metadata,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            lastWatchedText?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            item.anime.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        androidx.compose.material3.Icon(
            imageVector = Icons.Outlined.PlayCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}
