package eu.kanade.tachiyomi.ui.anime.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import tachiyomi.presentation.core.components.material.padding

@Composable
fun AnimePlayerScreen(
    state: AnimePlayerState,
    player: androidx.media3.exoplayer.ExoPlayer,
    onBack: () -> Unit,
    onStopPlayback: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSeekBy: (Long) -> Unit,
    onSetPlaybackSpeed: (Float) -> Unit,
    onSelectVideoFile: (String) -> Unit,
    onSelectSubtitleTrack: (String?) -> Unit,
) {
    var showControls by rememberSaveable { mutableStateOf(true) }
    var showFileDialog by rememberSaveable { mutableStateOf(false) }
    var showSubtitleDialog by rememberSaveable { mutableStateOf(false) }
    var showOptionsDialog by rememberSaveable { mutableStateOf(false) }
    var showSpeedDialog by rememberSaveable { mutableStateOf(false) }
    var showResizeDialog by rememberSaveable { mutableStateOf(false) }
    var controlsLocked by rememberSaveable { mutableStateOf(false) }
    var resizeMode by rememberSaveable { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var isSeeking by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    val safeDrawingPadding = WindowInsets.safeDrawing.asPaddingValues()
    val durationForSlider = state.durationMs.coerceAtLeast(1L)

    LaunchedEffect(state.positionMs, durationForSlider, isSeeking) {
        if (!isSeeking) {
            sliderPosition = state.positionMs.coerceIn(0L, durationForSlider).toFloat()
        }
    }

    LaunchedEffect(state.phase, state.availableVideoFiles, state.selectedVideoFileId) {
        if (state.phase == TorrentPlaybackPhase.AwaitingFileSelection &&
            state.availableVideoFiles.isNotEmpty() &&
            state.selectedVideoFileId == null
        ) {
            showFileDialog = true
            showControls = true
        }
    }

    LaunchedEffect(
        showControls,
        controlsLocked,
        state.isPlaying,
        state.isBuffering,
        showOptionsDialog,
        showFileDialog,
        showSubtitleDialog,
        showSpeedDialog,
        showResizeDialog,
    ) {
        if (
            showControls &&
            !controlsLocked &&
            state.isPlaying &&
            !state.isBuffering &&
            !showOptionsDialog &&
            !showFileDialog &&
            !showSubtitleDialog &&
            !showSpeedDialog &&
            !showResizeDialog
        ) {
            delay(3000)
            showControls = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    useController = false
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                    this.player = player
                    this.resizeMode = resizeMode
                }
            },
            modifier = Modifier.fillMaxSize(),
            update = {
                it.player = player
                it.resizeMode = resizeMode
            },
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(controlsLocked, showControls) {
                    detectTapGestures {
                        if (controlsLocked) return@detectTapGestures
                        showControls = !showControls
                    }
                },
        )

        if (controlsLocked) {
            Surface(
                color = Color.Black.copy(alpha = 0.62f),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = safeDrawingPadding.calculateTopPadding() + MaterialTheme.padding.small,
                        end = MaterialTheme.padding.small,
                    ),
            ) {
                IconButton(onClick = { controlsLocked = false; showControls = true }) {
                    Icon(
                        imageVector = Icons.Outlined.LockOpen,
                        contentDescription = "Unlock controls",
                        tint = Color.White,
                    )
                }
            }
        }

        if (showControls && !controlsLocked) {
            TopOverlayBar(
                state = state,
                safeDrawingPadding = safeDrawingPadding,
                onBack = onBack,
                onStopPlayback = onStopPlayback,
                onOpenOptions = { showOptionsDialog = true },
                modifier = Modifier.align(Alignment.TopCenter),
            )

            CenterPlaybackControls(
                state = state,
                onSeekBack = { onSeekBy(-10_000L) },
                onTogglePlayPause = onTogglePlayPause,
                onSeekForward = { onSeekBy(10_000L) },
                modifier = Modifier.align(Alignment.Center),
            )

            BottomOverlayBar(
                state = state,
                sliderPosition = sliderPosition,
                durationForSlider = durationForSlider,
                isSeeking = isSeeking,
                onSliderPositionChange = {
                    isSeeking = true
                    sliderPosition = it
                },
                onSliderPositionChangeFinished = {
                    onSeekTo(sliderPosition.toLong())
                    isSeeking = false
                },
                safeDrawingPadding = safeDrawingPadding,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        PlaybackMessageChip(
            state = state,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = safeDrawingPadding.calculateTopPadding() + 72.dp),
        )
    }

    if (showOptionsDialog) {
        PlayerOptionsDialog(
            state = state,
            controlsLocked = controlsLocked,
            resizeMode = resizeMode,
            onDismiss = { showOptionsDialog = false },
            onOpenFiles = {
                showOptionsDialog = false
                showFileDialog = true
            },
            onOpenSubtitles = {
                showOptionsDialog = false
                showSubtitleDialog = true
            },
            onOpenSpeed = {
                showOptionsDialog = false
                showSpeedDialog = true
            },
            onOpenResize = {
                showOptionsDialog = false
                showResizeDialog = true
            },
            onToggleLock = {
                showOptionsDialog = false
                controlsLocked = !controlsLocked
                showControls = !controlsLocked
            },
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

    if (showSpeedDialog) {
        ChoiceDialog(
            title = "Playback speed",
            selectedValue = state.playbackSpeed.toDialogValue(),
            items = playbackSpeedOptions.map { speed ->
                speed.toDialogValue() to "${speed}x"
            },
            onSelect = {
                onSetPlaybackSpeed(it.toFloat())
                showSpeedDialog = false
            },
            onDismiss = { showSpeedDialog = false },
        )
    }

    if (showResizeDialog) {
        ChoiceDialog(
            title = "Resize mode",
            selectedValue = resizeMode.toString(),
            items = resizeOptions.map { option ->
                option.mode.toString() to option.label
            },
            onSelect = {
                resizeMode = it.toInt()
                showResizeDialog = false
            },
            onDismiss = { showResizeDialog = false },
        )
    }
}

@Composable
private fun TopOverlayBar(
    state: AnimePlayerState,
    safeDrawingPadding: androidx.compose.foundation.layout.PaddingValues,
    onBack: () -> Unit,
    onStopPlayback: () -> Unit,
    onOpenOptions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.74f),
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
                    color = Color.White.copy(alpha = 0.84f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onStopPlayback) {
                Icon(
                    imageVector = Icons.Outlined.Stop,
                    contentDescription = "Stop playback",
                    tint = Color.White,
                )
            }
            IconButton(onClick = onOpenOptions) {
                Icon(
                    imageVector = Icons.Outlined.MoreVert,
                    contentDescription = "Player options",
                    tint = Color.White,
                )
            }
        }
    }
}

@Composable
private fun CenterPlaybackControls(
    state: AnimePlayerState,
    onSeekBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekForward: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
    ) {
        FilledIconButton(onClick = onSeekBack) {
            Icon(
                imageVector = Icons.Filled.Replay10,
                contentDescription = "Seek back 10 seconds",
            )
        }
        FilledIconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier.size(72.dp),
        ) {
            Icon(
                imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (state.isPlaying) "Pause" else "Play",
                modifier = Modifier.size(36.dp),
            )
        }
        FilledIconButton(onClick = onSeekForward) {
            Icon(
                imageVector = Icons.Filled.Forward10,
                contentDescription = "Seek forward 10 seconds",
            )
        }
    }
}

@Composable
private fun BottomOverlayBar(
    state: AnimePlayerState,
    sliderPosition: Float,
    durationForSlider: Long,
    isSeeking: Boolean,
    onSliderPositionChange: (Float) -> Unit,
    onSliderPositionChangeFinished: () -> Unit,
    safeDrawingPadding: androidx.compose.foundation.layout.PaddingValues,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.82f),
                ),
            )
            .padding(
                start = MaterialTheme.padding.medium,
                end = MaterialTheme.padding.medium,
                top = MaterialTheme.padding.extraLarge,
                bottom = safeDrawingPadding.calculateBottomPadding() + MaterialTheme.padding.small,
            ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.extraSmall),
    ) {
        Slider(
            value = if (isSeeking) sliderPosition else state.positionMs.coerceAtLeast(0L).toFloat(),
            onValueChange = onSliderPositionChange,
            onValueChangeFinished = onSliderPositionChangeFinished,
            valueRange = 0f..durationForSlider.toFloat(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.positionMs.toClockString(),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
            Text(
                text = state.durationMs.toClockString(),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.availableVideoFiles.firstOrNull { it.id == state.selectedVideoFileId }?.name
                    ?: state.request.episodeName,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "${state.playbackSpeed.toDialogValue()}x",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.72f),
                modifier = Modifier.padding(start = MaterialTheme.padding.small),
            )
        }
    }
}

@Composable
private fun PlaybackMessageChip(
    state: AnimePlayerState,
    modifier: Modifier = Modifier,
) {
    val message = when {
        state.errorMessage != null -> state.errorMessage
        state.phase == TorrentPlaybackPhase.AwaitingBackend -> state.statusMessage ?: "Preparing torrent session..."
        state.phase == TorrentPlaybackPhase.AwaitingFileSelection -> "Choose a file to start playback"
        state.phase == TorrentPlaybackPhase.Buffering &&
            state.playbackUrl == null &&
            state.positionMs <= 0L -> state.statusMessage ?: "Buffering..."
        state.phase == TorrentPlaybackPhase.Buffering -> state.statusMessage ?: "Buffering..."
        else -> null
    } ?: return

    Surface(
        color = Color.Black.copy(alpha = 0.72f),
        shape = MaterialTheme.shapes.large,
        modifier = modifier,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(
                horizontal = MaterialTheme.padding.medium,
                vertical = MaterialTheme.padding.small,
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
        )
    }
}

@Composable
private fun PlayerOptionsDialog(
    state: AnimePlayerState,
    controlsLocked: Boolean,
    resizeMode: Int,
    onDismiss: () -> Unit,
    onOpenFiles: () -> Unit,
    onOpenSubtitles: () -> Unit,
    onOpenSpeed: () -> Unit,
    onOpenResize: () -> Unit,
    onToggleLock: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Player options") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                if (state.availableVideoFiles.isNotEmpty()) {
                    OptionRow("Choose file", onOpenFiles)
                }
                OptionRow("Subtitles", onOpenSubtitles)
                OptionRow("Playback speed (${state.playbackSpeed.toDialogValue()}x)", onOpenSpeed)
                OptionRow(
                    "Resize mode (${resizeOptions.first { it.mode == resizeMode }.label})",
                    onOpenResize,
                )
                OptionRow(
                    if (controlsLocked) "Unlock controls" else "Lock controls",
                    onToggleLock,
                )
            }
        },
        confirmButton = {},
        dismissButton = {},
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    selectedValue: String,
    items: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small)) {
                items.forEach { (value, label) ->
                    Text(
                        text = label,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (value == selectedValue) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                shape = MaterialTheme.shapes.small,
                            )
                            .clickable { onSelect(value) }
                            .padding(MaterialTheme.padding.small),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (value == selectedValue) {
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
private fun OptionRow(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = MaterialTheme.padding.extraSmall),
        style = MaterialTheme.typography.bodyLarge,
    )
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

private data class ResizeOption(
    val mode: Int,
    val label: String,
)

private val playbackSpeedOptions = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

private val resizeOptions = listOf(
    ResizeOption(AspectRatioFrameLayout.RESIZE_MODE_FIT, "Fit"),
    ResizeOption(AspectRatioFrameLayout.RESIZE_MODE_FILL, "Fill"),
    ResizeOption(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, "Zoom"),
)

private fun Float.toDialogValue(): String {
    return if (this % 1f == 0f) {
        toInt().toString()
    } else {
        toString()
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
