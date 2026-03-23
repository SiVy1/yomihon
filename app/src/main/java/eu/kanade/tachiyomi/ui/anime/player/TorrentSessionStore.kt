package eu.kanade.tachiyomi.ui.anime.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TorrentSessionStore {

    private val _session = MutableStateFlow<TorrentPlaybackSession?>(null)
    val session: StateFlow<TorrentPlaybackSession?> = _session.asStateFlow()

    fun startSession(
        sessionId: String,
        request: AnimePlaybackRequest,
        discoveredFiles: List<TorrentDiscoveredFile> = emptyList(),
        snapshot: TorrentPlaybackSnapshot = TorrentPlaybackSnapshot(),
    ) {
        _session.value = TorrentPlaybackSession(
            sessionId = sessionId,
            request = request,
            discoveredFiles = discoveredFiles,
            snapshot = snapshot,
        )
    }

    fun updateSnapshot(
        transform: (TorrentPlaybackSnapshot) -> TorrentPlaybackSnapshot,
    ): TorrentPlaybackSnapshot {
        _session.update { current ->
            current?.copy(snapshot = transform(current.snapshot))
        }
        return currentSnapshot()
    }

    fun updateDiscoveredFiles(
        files: List<TorrentDiscoveredFile>,
    ) {
        _session.update { current ->
            current?.copy(discoveredFiles = files)
        }
    }

    fun currentSession(): TorrentPlaybackSession? = session.value

    fun currentSnapshot(): TorrentPlaybackSnapshot {
        return session.value?.snapshot ?: TorrentPlaybackSnapshot(
            phase = TorrentPlaybackPhase.Idle,
            statusMessage = "Idle",
        )
    }

    fun stopSession() {
        _session.value = null
    }
}
