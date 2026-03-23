package eu.kanade.tachiyomi.ui.anime.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import tachiyomi.presentation.core.components.material.Scaffold
import tachiyomi.presentation.core.components.material.padding
import tachiyomi.presentation.core.util.plus

@Composable
fun AnimePlayerScreen(
    state: AnimePlayerState,
    player: ExoPlayer,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = MaterialTheme.padding.small, vertical = MaterialTheme.padding.xSmall),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.request.animeTitle,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = state.request.episodeName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = paddingValues + PaddingValues(bottom = MaterialTheme.padding.medium),
            verticalArrangement = Arrangement.spacedBy(MaterialTheme.padding.medium),
        ) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(Color.Black),
                ) {
                    AndroidView(
                        factory = { context ->
                            PlayerView(context).apply {
                                useController = true
                                this.player = player
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { it.player = player },
                    )

                    if (state.statusMessage != null || state.errorMessage != null) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(MaterialTheme.padding.medium),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            state.statusMessage?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White,
                                )
                            }
                            state.errorMessage?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }

            item {
                PlaybackStats(state = state)
            }

            if (state.availableVideoFiles.isNotEmpty()) {
                item {
                    SectionTitle("Torrent files")
                }
                items(state.availableVideoFiles, key = { it.id }) { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MaterialTheme.padding.medium),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.padding.small),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            file.sizeBytes?.let {
                                Text(
                                    text = "${it / (1024 * 1024)} MB",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            if (state.availableSubtitleTracks.isNotEmpty()) {
                item {
                    SectionTitle("Subtitle tracks")
                }
                items(state.availableSubtitleTracks, key = { it.id }) { track ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MaterialTheme.padding.medium),
                    ) {
                        Text(
                            text = track.label,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        track.language?.let {
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
    }
}

@Composable
private fun PlaybackStats(
    state: AnimePlayerState,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.padding.medium),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionTitle("Playback")
        Text(
            text = "Current: ${state.positionMs.toClockString()}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Duration: ${state.durationMs.toClockString()}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Buffered: ${state.bufferedPositionMs.toClockString()}",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = "Phase: ${state.phase.name}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(
    text: String,
) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = MaterialTheme.padding.medium),
        style = MaterialTheme.typography.titleMedium,
    )
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
