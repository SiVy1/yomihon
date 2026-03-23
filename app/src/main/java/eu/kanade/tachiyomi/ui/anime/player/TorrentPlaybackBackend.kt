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
        val subtitleTracks = selection.availableSubtitleTracks.map { track ->
            track.copy(uri = engineSession.subtitleTrackUrls[track.id] ?: track.uri)
        }
        val snapshot = TorrentPlaybackSnapshot(
            phase = when {
                selection.availableVideoFiles.isEmpty() -> TorrentPlaybackPhase.Error
                selection.selectedVideoFileId == null -> TorrentPlaybackPhase.AwaitingFileSelection
                else -> TorrentPlaybackPhase.AwaitingBackend
            },
            availableVideoFiles = selection.availableVideoFiles,
            availableSubtitleTracks = subtitleTracks,
            selectedVideoFileId = selection.selectedVideoFileId,
            selectedSubtitleTrackId = selection.selectedSubtitleTrackId,
            proxyUrl = engineSession.proxyUrl,
            statusMessage = when {
                selection.availableVideoFiles.isEmpty() -> {
                    "Metadata loaded, but no playable video file was found."
                }
                selection.selectedVideoFileId == null -> {
                    "Choose a file to start playback."
                }
                else -> {
                    "Torrent session prepared. Select the file to start buffering."
                }
            },
            errorMessage = if (selection.availableVideoFiles.isEmpty()) {
                "The torrent metadata does not contain a playable video file."
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
        return if (selection.selectedVideoFileId != null && engineSession.proxyUrl == null) {
            selectVideoFile(selection.selectedVideoFileId)
        } else {
            sessionStore.currentSnapshot()
        }
    }

    override suspend fun selectVideoFile(
        fileId: String,
    ): TorrentPlaybackSnapshot {
        val sessionId = sessionStore.currentSession()?.sessionId
        val playbackTarget = sessionId?.let { engine.selectVideoFile(it, fileId) }
        return sessionStore.updateSnapshot {
            it.copy(
                phase = if (playbackTarget?.proxyUrl != null) TorrentPlaybackPhase.Buffering else TorrentPlaybackPhase.AwaitingBackend,
                selectedVideoFileId = fileId,
                proxyUrl = playbackTarget?.proxyUrl ?: it.proxyUrl,
                statusMessage = if (playbackTarget?.proxyUrl != null) {
                    "Buffering selected torrent file..."
                } else {
                    "Selected file. Waiting for the torrent backend to expose the stream."
                },
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
