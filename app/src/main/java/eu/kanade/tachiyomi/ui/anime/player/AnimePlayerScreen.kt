package eu.kanade.tachiyomi.ui.anime.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.QueuePlayNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import tachiyomi.presentation.core.components.material.padding

@Composable
fun AnimePlayerScreen(
    state: AnimePlayerState,
    player: ExoPlayer,
    onBack: () -> Unit,
    onSelectVideoFile: (String) -> Unit,
    onSelectSubtitleTrack: (String?) -> Unit,
) {
    var showFileDialog by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()

    LaunchedEffect(state.phase, state.availableVideoFiles, state.selectedVideoFileId) {
        showFileDialog = state.phase == TorrentPlaybackPhase.AwaitingFileSelection &&
            state.availableVideoFiles.isNotEmpty() &&
            state.selectedVideoFileId == null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    this.player = player
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = { it.player = player },
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.72f),
                        1f to Color.Transparent,
                    ),
                )
                .padding(
                    top = safeDrawingPadding.calculateTopPadding(),
                    start = MaterialTheme.padding.small,
                    end = MaterialTheme.padding.small,
                    bottom = MaterialTheme.padding.medium,
                ),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.request.animeTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.request.episodeName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.88f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (state.availableVideoFiles.isNotEmpty()) {
                    IconButton(onClick = { showFileDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.QueuePlayNext,
                            contentDescription = "Choose file",
                            tint = Color.White,
                        )
                    }
                }

                if (state.availableSubtitleTracks.isNotEmpty()) {
                    IconButton(onClick = { showSubtitleDialog = true }) {
                        Icon(
                            imageVector = Icons.Outlined.ClosedCaption,
                            contentDescription = "Subtitles",
                            tint = Color.White,
                        )
                    }
                }
            }
        }

        if (state.statusMessage != null || state.errorMessage != null || shouldShowBlockingOverlay(state)) {
            Surface(
                color = Color.Black.copy(alpha = 0.46f),
                modifier = Modifier.fillMaxSize(),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = MaterialTheme.padding.large)
                            .align(Alignment.Center),
                        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val primaryMessage = when {
                            state.errorMessage != null -> state.errorMessage
                            state.phase == TorrentPlaybackPhase.AwaitingFileSelection -> "Choose a file to start playback"
                            else -> state.statusMessage
                        }
                        primaryMessage?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White,
                            )
                        }

                        val secondaryMessage = when {
                            state.phase == TorrentPlaybackPhase.Buffering -> "Buffering torrent stream..."
                            state.phase == TorrentPlaybackPhase.AwaitingBackend -> "Preparing torrent session..."
                            else -> null
                        }
                        secondaryMessage?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.8f),
                            )
                        }
                    }
                }
            }
        }

        PlayerStatusBar(
            state = state,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.76f),
                    ),
                )
                .padding(
                    start = MaterialTheme.padding.medium,
                    end = MaterialTheme.padding.medium,
                    top = MaterialTheme.padding.extraLarge,
                    bottom = safeDrawingPadding.calculateBottomPadding() + MaterialTheme.padding.small,
                ),
        )
    }

    if (showFileDialog && state.availableVideoFiles.isNotEmpty()) {
        TrackListDialog(
            title = "Choose file",
            selectedId = state.selectedVideoFileId,
            items = state.availableVideoFiles.map { file ->
                file.id to buildString {
                    append(file.name)
                    file.sizeBytes?.let { append(" (${it / (1024 * 1024)} MB)") }
                }
            },
            onSelect = {
                onSelectVideoFile(it)
                showFileDialog = false
            },
            onDismiss = { showFileDialog = false },
        )
    }

    if (showSubtitleDialog) {
        SubtitleTrackDialog(
            state = state,
            onSelect = {
                onSelectSubtitleTrack(it)
                showSubtitleDialog = false
            },
            onDismiss = { showSubtitleDialog = false },
        )
    }
}

@Composable
private fun PlayerStatusBar(
    state: AnimePlayerState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "${state.positionMs.toClockString()} / ${state.durationMs.toClockString()}",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
        )
        state.availableVideoFiles
            .firstOrNull { it.id == state.selectedVideoFileId }
            ?.name
            ?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        if (state.selectedSubtitleTrackId != null) {
            val trackLabel = state.availableSubtitleTracks
                .firstOrNull { it.id == state.selectedSubtitleTrackId }
                ?.label
            if (trackLabel != null) {
                Text(
                    text = trackLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun TrackListDialog(
    title: String,
    selectedId: String?,
    items: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                items.forEach { (id, label) ->
                    Text(
                        text = label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (id == selectedId) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    Color.Transparent
                                },
                                shape = MaterialTheme.shapes.small,
                            )
                            .clickable { onSelect(id) }
                            .padding(MaterialTheme.padding.small),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (id == selectedId) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}

@Composable
private fun SubtitleTrackDialog(
    state: AnimePlayerState,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Subtitle tracks") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                Text(
                    text = "Disable subtitles",
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (state.selectedSubtitleTrackId == null) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            },
                            shape = MaterialTheme.shapes.small,
                        )
                        .clickable { onSelect(null) }
                        .padding(MaterialTheme.padding.small),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (state.selectedSubtitleTrackId == null) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                state.availableSubtitleTracks.forEach { track ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (track.id == state.selectedSubtitleTrackId) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    Color.Transparent
                                },
                                shape = MaterialTheme.shapes.small,
                            )
                            .clickable { onSelect(track.id) }
                            .padding(MaterialTheme.padding.small),
                    ) {
                        Text(
                            text = track.label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (track.id == state.selectedSubtitleTrackId) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        track.language?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}

private fun shouldShowBlockingOverlay(state: AnimePlayerState): Boolean {
    return when (state.phase) {
        TorrentPlaybackPhase.AwaitingBackend,
        TorrentPlaybackPhase.AwaitingFileSelection,
        TorrentPlaybackPhase.Buffering,
        -> true
        else -> false
    }
}

private fun Long.toClockString(): String {
    val totalSeconds = (this / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
