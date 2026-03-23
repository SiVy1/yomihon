package eu.kanade.tachiyomi.ui.anime.player

interface TorrentPlaybackBackend {

    suspend fun prepare(
        request: AnimePlaybackRequest,
    ): TorrentPlaybackSnapshot

    suspend fun selectVideoFile(
        fileId: String,
    ): TorrentPlaybackSnapshot

    suspend fun selectSubtitleTrack(
        trackId: String?,
    ): TorrentPlaybackSnapshot

    suspend fun stopSession()
}

class NoopTorrentPlaybackBackend(
    private val sessionStore: TorrentSessionStore = TorrentSessionStore(),
    private val fileSelector: TorrentFileSelector = TorrentFileSelector(),
    private val engine: TorrentEngine = PlaceholderTorrentEngine(),
) : TorrentPlaybackBackend {

    override suspend fun prepare(
        request: AnimePlaybackRequest,
    ): TorrentPlaybackSnapshot {
        val engineSession = engine.prepareSession(request)
        val discoveredFiles = engineSession.discoveredFiles
        val selection = fileSelector.select(
            discoveredFiles = discoveredFiles,
            fileNameHint = request.descriptor.fileNameHint,
            subtitleHint = request.descriptor.subtitleHint,
        )
        val snapshot = TorrentPlaybackSnapshot(
            phase = when {
                selection.availableVideoFiles.isEmpty() -> TorrentPlaybackPhase.Error
                selection.selectedVideoFileId == null -> TorrentPlaybackPhase.AwaitingFileSelection
                else -> TorrentPlaybackPhase.AwaitingBackend
            },
            availableVideoFiles = selection.availableVideoFiles,
            availableSubtitleTracks = selection.availableSubtitleTracks,
            selectedVideoFileId = selection.selectedVideoFileId,
            selectedSubtitleTrackId = selection.selectedSubtitleTrackId,
            proxyUrl = engineSession.proxyUrl,
            statusMessage = when {
                selection.availableVideoFiles.isEmpty() -> {
                    "No playable video files discovered yet. Native torrent metadata is still pending."
                }
                selection.selectedVideoFileId == null -> {
                    "Choose a file to continue. Native torrent streaming integration is the next step."
                }
                else -> {
                    "Session prepared. Native torrent backend will provide the stream URL next."
                }
            },
            errorMessage = if (selection.availableVideoFiles.isEmpty()) {
                "The placeholder backend could not identify any playable video file."
            } else {
                null
            },
        )
        sessionStore.startSession(
            sessionId = engineSession.sessionId,
            request = request,
            discoveredFiles = discoveredFiles,
            snapshot = snapshot,
        )
        return sessionStore.currentSnapshot()
    }

    override suspend fun selectVideoFile(
        fileId: String,
    ): TorrentPlaybackSnapshot {
        val sessionId = sessionStore.currentSession()?.sessionId
        val playbackTarget = sessionId?.let { engine.selectVideoFile(it, fileId) }
        return sessionStore.updateSnapshot {
            it.copy(
                phase = TorrentPlaybackPhase.AwaitingBackend,
                selectedVideoFileId = fileId,
                proxyUrl = playbackTarget?.proxyUrl ?: it.proxyUrl,
                statusMessage = "Selected file. Native torrent backend will attach the stream URL next.",
                errorMessage = null,
            )
        }
    }

    override suspend fun selectSubtitleTrack(
        trackId: String?,
    ): TorrentPlaybackSnapshot {
        sessionStore.currentSession()?.sessionId?.let { sessionId ->
            engine.selectSubtitleTrack(sessionId, trackId)
        }
        return sessionStore.updateSnapshot {
            it.copy(
                selectedSubtitleTrackId = trackId,
                statusMessage = if (trackId == null) {
                    "Subtitles disabled."
                } else {
                    "Selected subtitle track."
                },
            )
        }
    }

    override suspend fun stopSession() {
        sessionStore.currentSession()?.sessionId?.let { sessionId ->
            engine.stopSession(sessionId)
        }
        sessionStore.stopSession()
    }
}
