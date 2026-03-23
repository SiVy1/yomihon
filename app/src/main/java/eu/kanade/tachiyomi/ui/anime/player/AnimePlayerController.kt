package eu.kanade.tachiyomi.ui.anime.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    private val appContext = context.applicationContext

    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(
        AnimePlayerState(
            request = request,
            statusMessage = "Player foundation ready. Torrent backend will attach here next.",
        ),
    )
    val state: StateFlow<AnimePlayerState> = _state.asStateFlow()

    private var playbackService: TorrentPlaybackService? = null
    private var isServiceBound = false
    private var snapshotJob: Job? = null
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? TorrentPlaybackService.LocalBinder ?: return
            playbackService = binder.getService()
            isServiceBound = true
            observeServiceState()
            playbackService?.prepare(request)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            snapshotJob?.cancel()
            snapshotJob = null
            playbackService = null
            isServiceBound = false
            _state.update {
                it.copy(
                    phase = TorrentPlaybackPhase.Error,
                    errorMessage = "Playback service disconnected.",
                )
            }
        }
    }

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
            if (playbackState == Player.STATE_ENDED) {
                persistProgress(forceSeen = true)
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            _state.update {
                it.copy(
                    availableSubtitleTracks = currentSubtitleTracks(),
                    selectedSubtitleTrackId = currentSelectedSubtitleTrackId(),
                )
            }
        }
    }

    init {
        player.addListener(playerListener)
        restoreSavedProgress()
        observePlayerPositions()
        connectToPlaybackService()
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

    private fun connectToPlaybackService() {
        val intent = Intent(appContext, TorrentPlaybackService::class.java).apply {
            putExtra(AnimePlayerActivity.PLAYBACK_REQUEST_KEY, request)
        }
        ContextCompat.startForegroundService(appContext, intent)
        val bound = appContext.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        if (!bound) {
            _state.update {
                it.copy(
                    phase = TorrentPlaybackPhase.Error,
                    isLoading = false,
                    errorMessage = "Failed to bind playback service.",
                )
            }
        }
    }

    private fun observeServiceState() {
        snapshotJob?.cancel()
        val playbackService = playbackService ?: return
        snapshotJob = scope.launch {
            playbackService.snapshot.collectLatest { snapshot ->
                _state.update {
                    it.copy(
                        phase = snapshot.phase,
                        isLoading = snapshot.phase == TorrentPlaybackPhase.Buffering || snapshot.phase == TorrentPlaybackPhase.Idle,
                        isBuffering = snapshot.phase == TorrentPlaybackPhase.Buffering,
                        availableVideoFiles = snapshot.availableVideoFiles,
                        availableSubtitleTracks = snapshot.availableSubtitleTracks.ifEmpty { it.availableSubtitleTracks },
                        selectedVideoFileId = snapshot.selectedVideoFileId,
                        selectedSubtitleTrackId = snapshot.selectedSubtitleTrackId,
                        statusMessage = snapshot.statusMessage,
                        errorMessage = snapshot.errorMessage,
                    )
                }
                preparePlayerIfNeeded(snapshot)
            }
        }
    }

    private fun preparePlayerIfNeeded(snapshot: TorrentPlaybackSnapshot) {
        val proxyUrl = snapshot.proxyUrl ?: return
        if (player.currentMediaItem?.localConfiguration?.uri.toString() == proxyUrl) return

        val mediaItem = MediaItem.fromUri(proxyUrl)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
        val resumePosition = state.value.positionMs
        if (resumePosition > 0L) {
            player.seekTo(resumePosition)
        }
    }

    private fun observePlayerPositions() {
        scope.launch {
            while (isActive) {
                val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: state.value.durationMs
                _state.update {
                    it.copy(
                        positionMs = player.currentPosition.takeIf { it >= 0L } ?: it.positionMs,
                        durationMs = duration,
                        bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L),
                        availableSubtitleTracks = currentSubtitleTracks().ifEmpty { it.availableSubtitleTracks },
                        selectedSubtitleTrackId = currentSelectedSubtitleTrackId() ?: it.selectedSubtitleTrackId,
                    )
                }
                delay(1_000)
            }
        }
    }

    private fun currentSubtitleTracks(): List<SubtitleTrack> {
        return player.currentTracks.groups
            .filter { it.type == C.TRACK_TYPE_TEXT && it.length > 0 }
            .flatMapIndexed { groupIndex, group ->
                (0 until group.length).map { trackIndex ->
                    val format = group.getTrackFormat(trackIndex)
                    SubtitleTrack(
                        id = "embedded:$groupIndex:$trackIndex",
                        label = format.label ?: format.language ?: "Subtitle ${groupIndex + 1}.${trackIndex + 1}",
                        language = format.language,
                    )
                }
            }
    }

    private fun currentSelectedSubtitleTrackId(): String? {
        val groups = player.currentTracks.groups
        groups.forEachIndexed { groupIndex, group ->
            if (group.type != C.TRACK_TYPE_TEXT) return@forEachIndexed
            for (trackIndex in 0 until group.length) {
                if (group.isTrackSelected(trackIndex)) {
                    return "embedded:$groupIndex:$trackIndex"
                }
            }
        }
        return null
    }

    fun selectVideoFile(fileId: String) {
        _state.update {
            it.copy(
                selectedVideoFileId = fileId,
                statusMessage = "Selecting file...",
            )
        }
        playbackService?.selectVideoFile(fileId)
    }

    fun selectSubtitleTrack(trackId: String?) {
        when {
            trackId == null -> {
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
            }
            trackId.startsWith("embedded:") -> {
                val parts = trackId.removePrefix("embedded:").split(':')
                val groupIndex = parts.getOrNull(0)?.toIntOrNull()
                val trackIndex = parts.getOrNull(1)?.toIntOrNull()
                if (groupIndex != null && trackIndex != null) {
                    val group = player.currentTracks.groups
                        .filter { it.type == C.TRACK_TYPE_TEXT }
                        .getOrNull(groupIndex)
                    if (group != null && trackIndex in 0 until group.length) {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                            .setOverrideForType(
                                TrackSelectionOverride(group.mediaTrackGroup, trackIndex),
                            )
                            .build()
                    }
                }
            }
            else -> playbackService?.selectSubtitleTrack(trackId)
        }

        _state.update {
            it.copy(
                selectedSubtitleTrackId = trackId,
                statusMessage = if (trackId == null) "Subtitles disabled" else "Selected subtitle track",
            )
        }
        playbackService?.selectSubtitleTrack(trackId)
    }

    fun persistProgress(forceSeen: Boolean = false) {
        val currentState = state.value
        val positionSeconds = (player.currentPosition.takeIf { it > 0L } ?: currentState.positionMs) / 1000L
        val durationSeconds = (player.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: currentState.durationMs) / 1000L
        val isCompleted = forceSeen ||
            (durationSeconds > 0L && positionSeconds.toDouble() / durationSeconds.toDouble() >= 0.95)

        scope.launch(Dispatchers.IO) {
            episodeRepository.update(
                EpisodeUpdate(
                    id = request.episodeId,
                    seen = true.takeIf { isCompleted },
                    lastSecondsWatched = if (isCompleted && durationSeconds > 0L) durationSeconds else positionSeconds,
                    totalSeconds = durationSeconds.takeIf { it > 0L },
                ),
            )
        }
    }

    fun release(stopPlaybackSession: Boolean) {
        if (stopPlaybackSession) {
            playbackService?.stopPlaybackSession()
        }
        snapshotJob?.cancel()
        snapshotJob = null
        if (isServiceBound) {
            appContext.unbindService(serviceConnection)
            isServiceBound = false
        }
        player.removeListener(playerListener)
        player.release()
        scope.cancel()
    }
}
