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

class NoopTorrentPlaybackBackend : TorrentPlaybackBackend {

    private var currentSnapshot = TorrentPlaybackSnapshot(
        phase = TorrentPlaybackPhase.Idle,
        statusMessage = "Idle",
    )

    override suspend fun prepare(
        request: AnimePlaybackRequest,
    ): TorrentPlaybackSnapshot {
        currentSnapshot = TorrentPlaybackSnapshot(
            phase = TorrentPlaybackPhase.AwaitingBackend,
            statusMessage = "Torrent backend placeholder active. Native streaming integration is the next step.",
            availableVideoFiles = buildList {
                request.descriptor.fileNameHint?.takeIf { it.isNotBlank() }?.let {
                    add(
                        TorrentPlayableFile(
                            id = "hint-file",
                            name = it,
                        ),
                    )
                }
            },
            selectedVideoFileId = request.descriptor.fileNameHint
                ?.takeIf { it.isNotBlank() }
                ?.let { "hint-file" },
            availableSubtitleTracks = buildList {
                request.descriptor.subtitleHint?.takeIf { it.isNotBlank() }?.let {
                    add(
                        SubtitleTrack(
                            id = "hint-subtitle",
                            label = it,
                        ),
                    )
                }
            },
        )
        return currentSnapshot
    }

    override suspend fun selectVideoFile(
        fileId: String,
    ): TorrentPlaybackSnapshot {
        currentSnapshot = currentSnapshot.copy(
            selectedVideoFileId = fileId,
            statusMessage = "Selected file: $fileId. Torrent backend placeholder is still active.",
        )
        return currentSnapshot
    }

    override suspend fun selectSubtitleTrack(
        trackId: String?,
    ): TorrentPlaybackSnapshot {
        currentSnapshot = currentSnapshot.copy(
            selectedSubtitleTrackId = trackId,
            statusMessage = if (trackId == null) {
                "Subtitles disabled."
            } else {
                "Selected subtitle: $trackId"
            },
        )
        return currentSnapshot
    }

    override suspend fun stopSession() {
        currentSnapshot = TorrentPlaybackSnapshot(
            phase = TorrentPlaybackPhase.Idle,
            statusMessage = "Idle",
        )
        Unit
    }
}
