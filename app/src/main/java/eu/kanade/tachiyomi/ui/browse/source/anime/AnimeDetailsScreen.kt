package eu.kanade.tachiyomi.ui.browse.source.anime

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CopyAll
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import coil3.compose.AsyncImage
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.util.Screen
import eu.kanade.presentation.util.relativeTimeSpanString
import eu.kanade.tachiyomi.R
import eu.kanade.tachiyomi.source.model.SAnime
import eu.kanade.tachiyomi.source.model.SEpisode
import eu.kanade.tachiyomi.source.model.TorrentDescriptor
import eu.kanade.tachiyomi.util.system.copyToClipboard
import eu.kanade.tachiyomi.util.system.openInBrowser
import tachiyomi.domain.anime.model.Anime
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen

data class AnimeDetailsScreen(
    val sourceId: Long,
    private val anime: SAnime,
) : Screen() {

    @Composable
    override fun Content() {
        val screenModel = rememberScreenModel { AnimeDetailsScreenModel(sourceId, anime) }
        val state by screenModel.state.collectAsState()
        val navigator = LocalNavigator.currentOrThrow
        val snackbarHostState = remember { SnackbarHostState() }

        LaunchedEffect(state.errorMessage) {
            state.errorMessage?.let {
                snackbarHostState.showSnackbar(it)
            }
        }

        Scaffold(
            topBar = { scrollBehavior ->
                AppBar(
                    title = state.anime.title,
                    navigateUp = navigator::pop,
                    scrollBehavior = scrollBehavior,
                )
            },
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        ) { paddingValues ->
            when {
                state.isLoading -> LoadingScreen(Modifier.padding(paddingValues))
                state.episodes.isEmpty() && state.errorMessage != null -> {
                    EmptyScreen(
                        modifier = Modifier.padding(paddingValues),
                        message = state.errorMessage ?: "Failed to load anime details.",
                    )
                }
                else -> {
                    LazyColumn(
                        contentPadding = paddingValues + PaddingValues(bottom = MaterialTheme.padding.medium),
                    ) {
                        item {
                            AnimeHeader(
                                anime = state.anime,
                                localAnime = state.localAnime,
                                onToggleLibrary = screenModel::toggleLibrary,
                            )
                        }

                        item {
                            HorizontalDivider()
                            Text(
                                text = "Episodes",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(MaterialTheme.padding.medium),
                            )
                        }

                        items(
                            items = state.episodes,
                            key = { it.url },
                        ) { episode ->
                            EpisodeItem(
                                episode = episode,
                                isResolving = state.resolvingEpisodeUrl == episode.url,
                                onClick = { screenModel.selectEpisode(episode) },
                            )
                        }
                    }
                }
            }
        }

        when (val dialog = state.dialog) {
            is AnimeDetailsScreenModel.Dialog.TorrentOptions -> {
                TorrentOptionsDialog(
                    episode = dialog.episode,
                    descriptors = dialog.descriptors,
                    onDismissRequest = { screenModel.setDialog(null) },
                )
            }
            null -> Unit
        }
    }
}

@Composable
private fun AnimeHeader(
    anime: SAnime,
    localAnime: Anime?,
    onToggleLibrary: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(MaterialTheme.padding.medium),
        verticalAlignment = Alignment.Top,
    ) {
        AsyncImage(
            model = anime.thumbnail_url,
            contentDescription = anime.title,
            error = painterResource(R.drawable.cover_error),
            modifier = Modifier
                .size(width = 108.dp, height = 156.dp)
                .clip(MaterialTheme.shapes.medium),
        )

        Column(
            modifier = Modifier
                .padding(start = MaterialTheme.padding.medium)
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = anime.title,
                style = MaterialTheme.typography.headlineSmall,
            )
            anime.genre?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            anime.description?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            FilledTonalButton(
                onClick = onToggleLibrary,
                enabled = localAnime != null,
            ) {
                Text(
                    text = if (localAnime?.favorite == true) {
                        "Remove from library"
                    } else {
                        "Add to library"
                    },
                )
            }
        }
    }
}

@Composable
private fun EpisodeItem(
    episode: SEpisode,
    isResolving: Boolean,
    onClick: () -> Unit,
) {
    val relativeDate = if (episode.date_upload > 0L) {
        relativeTimeSpanString(episode.date_upload)
    } else {
        null
    }

    val metadata = buildList {
        if (episode.episode_number >= 0f) add("Ep ${episode.episode_number}")
        episode.release_group?.let(::add)
        relativeDate?.let(::add)
    }.joinToString(" - ")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = MaterialTheme.padding.medium, vertical = MaterialTheme.padding.small),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
        ) {
            Icon(
                imageVector = Icons.Outlined.PlayCircle,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = episode.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f),
            )
            if (isResolving) {
                Text(
                    text = "Resolving...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (metadata.isNotBlank()) {
            Text(
                text = metadata,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun TorrentOptionsDialog(
    episode: SEpisode,
    descriptors: List<TorrentDescriptor>,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(text = episode.name)
        },
        text = {
            if (descriptors.isEmpty()) {
                Text("No torrent options were returned by this extension.")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                    descriptors.forEach { descriptor ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    descriptor.magnetUri?.let(context::openInBrowser)
                                        ?: descriptor.torrentUrl?.let(context::openInBrowser)
                                }
                                .padding(vertical = MaterialTheme.padding.small),
                        ) {
                            Text(
                                text = buildString {
                                    append(descriptor.quality ?: "Torrent")
                                    descriptor.seeders?.let { append(" - $it seeders") }
                                    descriptor.leechers?.let { append(" - $it leechers") }
                                },
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            descriptor.fileNameHint?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            descriptor.subtitleHint?.takeIf { it.isNotBlank() }?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                descriptor.magnetUri?.let { magnet ->
                                    IconButton(onClick = { context.copyToClipboard("Magnet", magnet) }) {
                                        Icon(Icons.Outlined.CopyAll, contentDescription = "Copy magnet")
                                    }
                                }
                                descriptor.magnetUri?.let { magnet ->
                                    IconButton(onClick = { context.openInBrowser(magnet) }) {
                                        Icon(Icons.Outlined.Link, contentDescription = "Open magnet")
                                    }
                                }
                                descriptor.torrentUrl?.let { torrentUrl ->
                                    IconButton(onClick = { context.openInBrowser(torrentUrl) }) {
                                        Icon(Icons.Outlined.Download, contentDescription = "Open torrent")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text("Close")
            }
        },
    )
}
