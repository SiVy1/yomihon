package eu.kanade.tachiyomi.ui.anime.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircle
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
import eu.kanade.presentation.components.AppBarTitle
import eu.kanade.presentation.components.SearchToolbar
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.animateItemFastScroll
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.ui.browse.source.anime.AnimeDetailsScreen
import tachiyomi.domain.anime.model.AnimeHistoryWithRelations
import tachiyomi.presentation.core.components.FastScrollLazyColumn
import tachiyomi.presentation.core.components.ListGroupHeader
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.plus

class AnimeHistoryScreen : Screen() {

    @Composable
    override fun Content() {
        val navigator = LocalNavigator.currentOrThrow
        val screenModel = rememberScreenModel { AnimeHistoryScreenModel() }
        val state by screenModel.state.collectAsState()
        val snackbarHostState = remember { SnackbarHostState() }

        Scaffold(
            topBar = {
                SearchToolbar(
                    titleContent = { AppBarTitle("Anime History") },
                    searchQuery = state.searchQuery,
                    onChangeSearchQuery = screenModel::search,
                    navigateUp = navigator::pop,
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { contentPadding ->
            when {
                state.isLoading -> LoadingScreen(Modifier.padding(contentPadding))
                state.isEmpty -> {
                    EmptyScreen(
                        modifier = Modifier.padding(contentPadding),
                        message = "No anime history yet.",
                    )
                }
                else -> {
                    FastScrollLazyColumn(
                        contentPadding = contentPadding + PaddingValues(bottom = MaterialTheme.padding.medium),
                    ) {
                        items(
                            items = state.items,
                            key = { item ->
                                when (item) {
                                    is AnimeHistoryUiModel.Header -> "header-${item.date}"
                                    is AnimeHistoryUiModel.Item -> "item-${item.item.id}"
                                }
                            },
                            contentType = {
                                when (it) {
                                    is AnimeHistoryUiModel.Header -> "header"
                                    is AnimeHistoryUiModel.Item -> "item"
                                }
                            },
                        ) { item ->
                            when (item) {
                                is AnimeHistoryUiModel.Header -> {
                                    ListGroupHeader(
                                        modifier = Modifier.animateItemFastScroll(),
                                        text = relativeDateText(item.date),
                                    )
                                }
                                is AnimeHistoryUiModel.Item -> {
                                    AnimeHistoryRow(
                                        modifier = Modifier.animateItemFastScroll(),
                                        item = item.item,
                                        onClick = {
                                            navigator.push(
                                                AnimeDetailsScreen(
                                                    sourceId = item.item.sourceId,
                                                    anime = item.item.toSAnime(),
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
}

@Composable
private fun AnimeHistoryRow(
    item: AnimeHistoryWithRelations,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val metadata = buildList {
        if (item.episodeNumber >= 0.0) add("Ep ${item.episodeNumber}")
        add(item.episodeName)
        item.watchedAt?.takeIf { it > 0L }?.let { add(relativeTimeSpanString(it)) }
    }.joinToString(" - ")

    val progressText = buildList {
        if (item.lastSecondsWatched > 0L && !item.seen) add("Resume ${item.lastSecondsWatched}s")
        if (item.bookmark) add("Bookmarked")
    }.joinToString(" - ")

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = item.thumbnailUrl,
            contentDescription = item.title,
            error = painterResource(R.drawable.cover_error),
            modifier = Modifier
                .size(width = 56.dp, height = 84.dp)
                .clip(MaterialTheme.shapes.small),
        )

        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .padding(start = MaterialTheme.padding.medium)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = metadata,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (progressText.isNotBlank()) {
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
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
