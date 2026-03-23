package eu.kanade.tachiyomi.ui.anime.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import tachiyomi.domain.anime.model.EpisodeUpdate
import tachiyomi.domain.anime.repository.EpisodeRepository
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

class AnimePlayerController(
    context: Context,
    private val request: AnimePlaybackRequest,
    private val episodeRepository: EpisodeRepository = Injekt.get(),
) {

    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(
        AnimePlayerState(
            request = request,
            statusMessage = "Player foundation ready. Torrent backend will attach here next.",
        ),
    )
    val state: StateFlow<AnimePlayerState> = _state.asStateFlow()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _state.update {
                it.copy(
                    isLoading = playbackState == Player.STATE_IDLE,
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    phase = when (playbackState) {
                        Player.STATE_BUFFERING -> TorrentPlaybackPhase.Buffering
                        Player.STATE_READY -> TorrentPlaybackPhase.Ready
                        Player.STATE_ENDED -> TorrentPlaybackPhase.Ready
                        else -> it.phase
                    },
                )
            }
        }
    }

    init {
        player.addListener(playerListener)
        restoreSavedProgress()
        observePlayerPositions()
    }

    private fun restoreSavedProgress() {
        scope.launch(Dispatchers.IO) {
            val episode = episodeRepository.getEpisodeById(request.episodeId)
            _state.update {
                it.copy(
                    isLoading = false,
                    positionMs = (episode?.lastSecondsWatched ?: 0L) * 1000L,
                    durationMs = (episode?.totalSeconds ?: 0L) * 1000L,
                )
            }
        }
    }

    private fun observePlayerPositions() {
        scope.launch {
            while (isActive) {
                val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: state.value.durationMs
                _state.update {
                    it.copy(
                        positionMs = player.currentPosition.coerceAtLeast(it.positionMs),
                        durationMs = duration,
                        bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L),
                        availableSubtitleTracks = it.availableSubtitleTracks.ifEmpty { currentSubtitleTracks() },
                    )
                }
                delay(1_000)
            }
        }
    }

    private fun currentSubtitleTracks(): List<SubtitleTrack> {
        return player.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_TEXT && it.length > 0 }
            .mapIndexed { index, group ->
                val format = group.getTrackFormat(0)
                SubtitleTrack(
                    id = "text-$index",
                    label = format.label ?: format.language ?: "Subtitle ${index + 1}",
                    language = format.language,
                )
            }
    }

    fun persistProgress() {
        val currentState = state.value
        val positionSeconds = (player.currentPosition.takeIf { it > 0L } ?: currentState.positionMs) / 1000L
        val durationSeconds = (player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: currentState.durationMs) / 1000L

        scope.launch(Dispatchers.IO) {
            episodeRepository.update(
                EpisodeUpdate(
                    id = request.episodeId,
                    lastSecondsWatched = positionSeconds,
                    totalSeconds = durationSeconds.takeIf { it > 0L },
                ),
            )
        }
    }

    fun release() {
        player.removeListener(playerListener)
        player.release()
        scope.cancel()
    }
}
