package eu.kanade.tachiyomi.ui.anime.player

import eu.kanade.tachiyomi.source.model.TorrentDescriptor
import java.io.Serializable

data class AnimePlaybackRequest(
    val sourceId: Long,
    val episodeId: Long,
    val episodeUrl: String,
    val episodeName: String,
    val animeTitle: String,
    val descriptor: TorrentDescriptor,
) : Serializable

enum class TorrentPlaybackPhase {
    Idle,
    AwaitingBackend,
    AwaitingFileSelection,
    Buffering,
    Ready,
    Error,
}

data class TorrentPlayableFile(
    val id: String,
    val name: String,
    val sizeBytes: Long? = null,
    val isVideo: Boolean = true,
) : Serializable

data class SubtitleTrack(
    val id: String,
    val label: String,
    val language: String? = null,
) : Serializable

data class TorrentDiscoveredFile(
    val id: String,
    val path: String,
    val sizeBytes: Long? = null,
) : Serializable

data class TorrentPlaybackSnapshot(
    val phase: TorrentPlaybackPhase = TorrentPlaybackPhase.Idle,
    val availableVideoFiles: List<TorrentPlayableFile> = emptyList(),
    val availableSubtitleTracks: List<SubtitleTrack> = emptyList(),
    val selectedVideoFileId: String? = null,
    val selectedSubtitleTrackId: String? = null,
    val proxyUrl: String? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)

data class TorrentPlaybackSession(
    val sessionId: String,
    val request: AnimePlaybackRequest,
    val discoveredFiles: List<TorrentDiscoveredFile> = emptyList(),
    val snapshot: TorrentPlaybackSnapshot = TorrentPlaybackSnapshot(),
)

data class AnimePlayerState(
    val request: AnimePlaybackRequest,
    val phase: TorrentPlaybackPhase = TorrentPlaybackPhase.AwaitingBackend,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isLoading: Boolean = true,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L,
    val availableVideoFiles: List<TorrentPlayableFile> = emptyList(),
    val availableSubtitleTracks: List<SubtitleTrack> = emptyList(),
    val selectedSubtitleTrackId: String? = null,
    val selectedVideoFileId: String? = null,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
)
