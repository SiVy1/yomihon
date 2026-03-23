package eu.kanade.tachiyomi.ui.browse.source.anime

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallExtendedFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.animateFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cafe.adriel.voyager.core.model.rememberScreenModel
import cafe.adriel.voyager.navigator.LocalNavigator
import cafe.adriel.voyager.navigator.currentOrThrow
import eu.kanade.presentation.category.components.ChangeCategoryDialog
import eu.kanade.presentation.components.AppBar
import eu.kanade.presentation.components.relativeDateText
import eu.kanade.presentation.manga.components.ChapterHeader
import eu.kanade.presentation.manga.components.ExpandableMangaDescription
import eu.kanade.presentation.manga.components.MangaActionRow
import eu.kanade.presentation.manga.components.MangaChapterListItem
import eu.kanade.presentation.manga.components.MangaInfoBox
import eu.kanade.presentation.util.Screen
import eu.kanade.tachiyomi.data.download.model.Download
import eu.kanade.tachiyomi.data.track.TrackerManager
import eu.kanade.tachiyomi.source.getNameForMangaInfo
import eu.kanade.tachiyomi.source.model.SAnime
import eu.kanade.tachiyomi.source.model.SEpisode
import eu.kanade.tachiyomi.source.model.TorrentDescriptor
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.ui.anime.player.AnimePlaybackRequest
import eu.kanade.tachiyomi.ui.anime.player.AnimePlayerActivity
import eu.kanade.tachiyomi.ui.webview.WebViewScreen
import eu.kanade.tachiyomi.ui.category.CategoryScreen
import eu.kanade.tachiyomi.ui.setting.SettingsScreen
import eu.kanade.tachiyomi.util.system.copyToClipboard
import kotlinx.coroutines.launch
import tachiyomi.domain.anime.model.Anime
import tachiyomi.domain.anime.model.Episode
import tachiyomi.domain.library.service.LibraryPreferences
import tachiyomi.domain.manga.model.Manga
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.TextButton
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.i18n.stringResource
import tachiyomi.presentation.core.screens.EmptyScreen
import tachiyomi.presentation.core.screens.LoadingScreen
import tachiyomi.presentation.core.util.shouldExpandFAB
import tachiyomi.presentation.core.util.plus
import tachiyomi.i18n.MR
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

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
        val scope = rememberCoroutineScope()
        val context = androidx.compose.ui.platform.LocalContext.current
        val listState = rememberLazyListState()
        val sourceName = remember(screenModel.source) { screenModel.source.getNameForMangaInfo() }
        val trackerManager = remember { Injekt.get<TrackerManager>() }
        val hasLoggedInTrackers = remember { trackerManager.loggedInTrackers().isNotEmpty() }
        val pseudoManga = remember(state.anime, state.localAnime) {
            state.asPseudoManga(sourceId = sourceId)
        }
        val continueEpisode = remember(state.episodes, state.localEpisodes) {
            state.getContinueEpisode()
        }
        val hasResume = remember(continueEpisode, state.localEpisodes) {
            continueEpisode?.let { episode ->
                val localEpisode = state.localEpisodes[episode.url]
                localEpisode != null && !localEpisode.seen && localEpisode.lastSecondsWatched > 0L
            } == true
        }
        val isFabVisible = remember(state.episodes) { state.episodes.isNotEmpty() }

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
            floatingActionButton = {
                if (continueEpisode != null) {
                    SmallExtendedFloatingActionButton(
                        text = {
                            Text(
                                text = stringResource(
                                    if (hasResume) MR.strings.action_resume else MR.strings.action_start,
                                ),
                            )
                        },
                        icon = { Icon(imageVector = Icons.Filled.PlayArrow, contentDescription = null) },
                        onClick = { screenModel.selectEpisode(continueEpisode) },
                        expanded = listState.shouldExpandFAB(),
                        modifier = Modifier.animateFloatingActionButton(
                            visible = isFabVisible,
                            alignment = Alignment.BottomEnd,
                        ),
                    )
                }
            },
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
                        state = listState,
                        contentPadding = paddingValues + PaddingValues(bottom = MaterialTheme.padding.medium),
                    ) {
                        item {
                            MangaInfoBox(
                                isTabletUi = false,
                                appBarPadding = paddingValues.calculateTopPadding(),
                                manga = pseudoManga,
                                sourceName = sourceName,
                                isStubSource = false,
                                onCoverClick = {},
                                doSearch = { _, _ -> },
                            )
                        }

                        item {
                            MangaActionRow(
                                favorite = state.localAnime?.favorite == true,
                                trackingCount = 0,
                                nextUpdate = null,
                                isUserIntervalMode = false,
                                onAddToLibraryClicked = screenModel::toggleLibrary,
                                onWebViewClicked = (screenModel.source as? HttpSource)?.let { source ->
                                    {
                                        navigator.push(
                                            WebViewScreen(
                                                url = source.baseUrl,
                                                initialTitle = source.name,
                                                sourceId = source.id,
                                            ),
                                        )
                                    }
                                },
                                onWebViewLongClicked = null,
                                onTrackingClicked = {
                                    if (!hasLoggedInTrackers) {
                                        navigator.push(SettingsScreen(SettingsScreen.Destination.Tracking))
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("Anime tracking isn't wired yet.")
                                        }
                                    }
                                },
                                onEditIntervalClicked = null,
                                onEditCategory = null,
                            )
                        }

                        item {
                            ExpandableMangaDescription(
                                defaultExpandState = true,
                                description = state.anime.description,
                                tagsProvider = { state.anime.getGenres() },
                                notes = "",
                                onTagSearch = {},
                                onCopyTagToClipboard = { context.copyToClipboard(it, it) },
                                onEditNotes = {},
                            )
                        }

                        item {
                            ChapterHeader(
                                enabled = false,
                                chapterCount = state.episodes.size,
                                missingChapterCount = 0,
                                onClick = {},
                            )
                        }

                        items(
                            items = state.episodes,
                            key = { episode -> episode.url },
                        ) { episode ->
                            EpisodeListItem(
                                episode = episode,
                                localEpisode = state.localEpisodes[episode.url],
                                isResolving = state.resolvingEpisodeUrl == episode.url,
                                onClick = { screenModel.selectEpisode(episode) },
                                onToggleSeen = { screenModel.toggleSeen(episode) },
                                onToggleBookmark = { screenModel.toggleBookmark(episode) },
                            )
                        }
                    }
                }
            }
        }

        when (val dialog = state.dialog) {
            is AnimeDetailsScreenModel.Dialog.ChangeCategory -> {
                ChangeCategoryDialog(
                    initialSelection = dialog.initialSelection,
                    onDismissRequest = { screenModel.setDialog(null) },
                    onEditCategories = { navigator.push(CategoryScreen()) },
                    onConfirm = { include, _ ->
                        screenModel.moveAnimeToCategoriesAndAddToLibrary(dialog.anime, include)
                        screenModel.setDialog(null)
                    },
                )
            }
            is AnimeDetailsScreenModel.Dialog.TorrentOptions -> {
                TorrentOptionsDialog(
                    episode = dialog.episode,
                    descriptors = dialog.descriptors,
                    onSelectDescriptor = { descriptor ->
                        val localEpisode = state.localEpisodes[dialog.episode.url] ?: return@TorrentOptionsDialog
                        screenModel.onEpisodeLaunched(dialog.episode)
                        context.startActivity(
                            AnimePlayerActivity.newIntent(
                                context = context,
                                request = AnimePlaybackRequest(
                                    sourceId = sourceId,
                                    episodeId = localEpisode.id,
                                    episodeUrl = dialog.episode.url,
                                    episodeName = dialog.episode.name,
                                    animeTitle = state.anime.title,
                                    descriptor = descriptor,
                                ),
                            ),
                        )
                        screenModel.setDialog(null)
                    },
                    onDismissRequest = { screenModel.setDialog(null) },
                )
            }
            null -> Unit
        }
    }
}

@Composable
private fun EpisodeListItem(
    episode: SEpisode,
    localEpisode: Episode?,
    isResolving: Boolean,
    onClick: () -> Unit,
    onToggleSeen: () -> Unit,
    onToggleBookmark: () -> Unit,
) {
    MangaChapterListItem(
        title = episode.name,
        date = relativeDateText(episode.date_upload),
        readProgress = localEpisode.asEpisodeProgressText(),
        scanlator = buildString {
            episode.release_group?.takeIf { it.isNotBlank() }?.let { append(it) }
            if (isResolving) {
                if (isNotBlank()) append(" • ")
                append("Resolving...")
            }
        }.takeIf { it.isNotBlank() },
        read = localEpisode?.seen == true,
        bookmark = localEpisode?.bookmark == true,
        selected = false,
        downloadIndicatorEnabled = false,
        downloadStateProvider = { Download.State.NOT_DOWNLOADED },
        downloadProgressProvider = { 0 },
        chapterSwipeStartAction = LibraryPreferences.ChapterSwipeAction.ToggleRead,
        chapterSwipeEndAction = LibraryPreferences.ChapterSwipeAction.ToggleBookmark,
        onLongClick = onToggleBookmark,
        onClick = onClick,
        onDownloadClick = null,
        onChapterSwipe = { action ->
            when (action) {
                LibraryPreferences.ChapterSwipeAction.ToggleRead -> onToggleSeen()
                LibraryPreferences.ChapterSwipeAction.ToggleBookmark -> onToggleBookmark()
                else -> Unit
            }
        },
    )
}

@Composable
private fun TorrentOptionsDialog(
    episode: SEpisode,
    descriptors: List<TorrentDescriptor>,
    onSelectDescriptor: (TorrentDescriptor) -> Unit,
    onDismissRequest: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(text = episode.name) },
        text = {
            if (descriptors.isEmpty()) {
                Text("No torrent options were returned by this extension.")
            } else {
                androidx.compose.foundation.layout.Column(
                    verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                ) {
                    descriptors.forEach { descriptor ->
                        androidx.compose.foundation.layout.Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectDescriptor(descriptor) }
                                .padding(vertical = MaterialTheme.padding.small),
                        ) {
                            Text(
                                text = buildString {
                                    append(descriptor.quality ?: "Torrent")
                                    descriptor.seeders?.let { append(" • $it seeders") }
                                    descriptor.leechers?.let { append(" • $it leechers") }
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

private fun AnimeDetailsScreenModel.State.asPseudoManga(sourceId: Long): Manga {
    val localAnime = localAnime
    return Manga.create().copy(
        id = localAnime?.id ?: -1L,
        source = localAnime?.source ?: sourceId,
        favorite = localAnime?.favorite == true,
        dateAdded = localAnime?.dateAdded ?: 0L,
        coverLastModified = localAnime?.lastModifiedAt ?: 0L,
        url = anime.url,
        title = anime.title,
        artist = anime.artist ?: localAnime?.artist,
        author = anime.author ?: localAnime?.author,
        description = anime.description ?: localAnime?.description,
        genre = anime.getGenres() ?: localAnime?.genre,
        status = anime.status.toLong(),
        thumbnailUrl = anime.thumbnail_url ?: localAnime?.thumbnailUrl,
        initialized = anime.initialized,
        lastModifiedAt = localAnime?.lastModifiedAt ?: 0L,
        favoriteModifiedAt = localAnime?.favoriteModifiedAt,
        version = localAnime?.version ?: 0L,
    )
}

private fun AnimeDetailsScreenModel.State.getContinueEpisode(): SEpisode? {
    return episodes.firstOrNull { episode ->
        val localEpisode = localEpisodes[episode.url]
        localEpisode != null && !localEpisode.seen && localEpisode.lastSecondsWatched > 0L
    } ?: episodes.firstOrNull { episode ->
        localEpisodes[episode.url]?.seen != true
    } ?: episodes.firstOrNull()
}

private fun Episode?.asEpisodeProgressText(): String? {
    if (this == null || seen) return null
    if (totalSeconds > 0L && lastSecondsWatched > 0L) {
        val percent = ((lastSecondsWatched.toDouble() / totalSeconds.toDouble()) * 100.0)
            .toInt()
            .coerceIn(0, 100)
        return "$percent%"
    }
    return lastSecondsWatched
        .takeIf { it > 0L }
        ?.let { "${it}s" }
}
