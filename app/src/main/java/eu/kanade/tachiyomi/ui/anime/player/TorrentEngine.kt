package eu.kanade.tachiyomi.ui.anime.player

import java.util.UUID

interface TorrentEngine {

    suspend fun prepareSession(
        request: AnimePlaybackRequest,
    ): TorrentEngineSession

    suspend fun selectVideoFile(
        sessionId: String,
        fileId: String,
    ): TorrentEnginePlaybackTarget?

    suspend fun selectSubtitleTrack(
        sessionId: String,
        trackId: String?,
    )

    suspend fun stopSession(
        sessionId: String,
    )
}

data class TorrentEngineSession(
    val sessionId: String,
    val discoveredFiles: List<TorrentDiscoveredFile>,
    val proxyUrl: String? = null,
)

data class TorrentEnginePlaybackTarget(
    val proxyUrl: String?,
)

class PlaceholderTorrentEngine : TorrentEngine {

    override suspend fun prepareSession(
        request: AnimePlaybackRequest,
    ): TorrentEngineSession {
        val discoveredFiles = mutableListOf<TorrentDiscoveredFile>()

        request.descriptor.fileNameHint
            ?.takeIf { it.isNotBlank() }
            ?.let {
                discoveredFiles += TorrentDiscoveredFile(
                    id = "video-hint",
                    path = normalizeMediaPath(it),
                    sizeBytes = request.descriptor.sizeBytes,
                )
            }

        if (discoveredFiles.none { it.path.isLikelyVideoFile() }) {
            discoveredFiles += TorrentDiscoveredFile(
                id = "release-title",
                path = normalizeMediaPath(request.descriptor.releaseTitle),
                sizeBytes = request.descriptor.sizeBytes,
            )
        }

        request.descriptor.subtitleHint
            ?.takeIf { it.isNotBlank() }
            ?.let {
                discoveredFiles += TorrentDiscoveredFile(
                    id = "subtitle-hint",
                    path = normalizeSubtitlePath(it),
                )
            }

        return TorrentEngineSession(
            sessionId = UUID.randomUUID().toString(),
            discoveredFiles = discoveredFiles,
            proxyUrl = null,
        )
    }

    override suspend fun selectVideoFile(
        sessionId: String,
        fileId: String,
    ): TorrentEnginePlaybackTarget? {
        return TorrentEnginePlaybackTarget(proxyUrl = null)
    }

    override suspend fun selectSubtitleTrack(
        sessionId: String,
        trackId: String?,
    ) {
        Unit
    }

    override suspend fun stopSession(
        sessionId: String,
    ) {
        Unit
    }

    private fun normalizeMediaPath(
        value: String,
    ): String {
        val sanitized = value.substringAfterLast('/').ifBlank { "episode.mkv" }
        return if ('.' in sanitized) sanitized else "$sanitized.mkv"
    }

    private fun normalizeSubtitlePath(
        value: String,
    ): String {
        val sanitized = value.substringAfterLast('/').ifBlank { "subtitle.ass" }
        return if ('.' in sanitized) sanitized else "$sanitized.ass"
    }

    private fun String.isLikelyVideoFile(): Boolean {
        val extension = substringAfterLast('.', "").lowercase()
        return extension in setOf("mkv", "mp4", "avi", "webm", "m4v", "ts")
    }
}

