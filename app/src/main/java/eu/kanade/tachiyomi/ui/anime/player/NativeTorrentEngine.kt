package eu.kanade.tachiyomi.ui.anime.player

import android.content.Context
import java.io.File

class NativeTorrentEngine(
    context: Context,
    private val nativeBridge: NativeTorrentBridge,
) : TorrentEngine {

    private val appContext = context.applicationContext

    override suspend fun prepareSession(
        request: AnimePlaybackRequest,
    ): TorrentEngineSession {
        val sessionDirectory = sessionDirectory(request)
        sessionDirectory.mkdirs()

        val result = nativeBridge.prepareSession(
            magnetUri = request.descriptor.magnetUri,
            torrentUrl = request.descriptor.torrentUrl,
            infoHash = request.descriptor.infoHash,
            storageDirectory = sessionDirectory.absolutePath,
            displayName = request.descriptor.releaseTitle,
        )

        return TorrentEngineSession(
            sessionId = result.sessionId,
            discoveredFiles = result.files,
            proxyUrl = result.proxyUrl,
        )
    }

    override suspend fun selectVideoFile(
        sessionId: String,
        fileId: String,
    ): TorrentEnginePlaybackTarget? {
        return nativeBridge.selectVideoFile(
            sessionId = sessionId,
            fileId = fileId,
        )?.let {
            TorrentEnginePlaybackTarget(proxyUrl = it)
        }
    }

    override suspend fun selectSubtitleTrack(
        sessionId: String,
        trackId: String?,
    ) {
        nativeBridge.selectSubtitleTrack(
            sessionId = sessionId,
            trackId = trackId,
        )
    }

    override suspend fun stopSession(
        sessionId: String,
    ) {
        nativeBridge.stopSession(sessionId)
    }

    private fun sessionDirectory(
        request: AnimePlaybackRequest,
    ): File {
        val root = File(appContext.cacheDir, "anime-torrents")
        return File(root, "${request.sourceId}-${request.episodeId}")
    }
}

class NativeTorrentBridge {

    fun prepareSession(
        magnetUri: String?,
        torrentUrl: String?,
        infoHash: String?,
        storageDirectory: String,
        displayName: String,
    ): NativePrepareResult {
        return if (NativeTorrentLibraryLoader.isAvailable()) {
            nativePrepareSession(
                magnetUri = magnetUri,
                torrentUrl = torrentUrl,
                infoHash = infoHash,
                storageDirectory = storageDirectory,
                displayName = displayName,
            )
        } else {
            fallbackPrepareSession(
                magnetUri = magnetUri,
                torrentUrl = torrentUrl,
                infoHash = infoHash,
                storageDirectory = storageDirectory,
                displayName = displayName,
            )
        }
    }

    fun selectVideoFile(
        sessionId: String,
        fileId: String,
    ): String? {
        return if (NativeTorrentLibraryLoader.isAvailable()) {
            nativeSelectVideoFile(sessionId, fileId)
        } else {
            null
        }
    }

    fun selectSubtitleTrack(
        sessionId: String,
        trackId: String?,
    ) {
        if (NativeTorrentLibraryLoader.isAvailable()) {
            nativeSelectSubtitleTrack(sessionId, trackId)
        }
    }

    fun stopSession(
        sessionId: String,
    ) {
        if (NativeTorrentLibraryLoader.isAvailable()) {
            nativeStopSession(sessionId)
        }
    }

    private fun fallbackPrepareSession(
        magnetUri: String?,
        torrentUrl: String?,
        infoHash: String?,
        storageDirectory: String,
        displayName: String,
    ): NativePrepareResult {
        val normalizedName = displayName.substringAfterLast('/').ifBlank { "episode.mkv" }
        val mediaPath = if ('.' in normalizedName) normalizedName else "$normalizedName.mkv"
        val subtitlePath = displayName
            .substringBeforeLast('.', displayName)
            .substringAfterLast('/')
            .ifBlank { "subtitle" } + ".ass"

        return NativePrepareResult(
            sessionId = infoHash ?: magnetUri?.hashCode()?.toString() ?: torrentUrl?.hashCode()?.toString() ?: storageDirectory.hashCode().toString(),
            files = buildList {
                add(
                    TorrentDiscoveredFile(
                        id = "video-hint",
                        path = mediaPath,
                    ),
                )
                add(
                    TorrentDiscoveredFile(
                        id = "subtitle-hint",
                        path = subtitlePath,
                    ),
                )
            },
            proxyUrl = null,
        )
    }

    private external fun nativePrepareSession(
        magnetUri: String?,
        torrentUrl: String?,
        infoHash: String?,
        storageDirectory: String,
        displayName: String,
    ): NativePrepareResult

    private external fun nativeSelectVideoFile(
        sessionId: String,
        fileId: String,
    ): String?

    private external fun nativeSelectSubtitleTrack(
        sessionId: String,
        trackId: String?,
    )

    private external fun nativeStopSession(
        sessionId: String,
    )
}

data class NativePrepareResult(
    val sessionId: String,
    val files: List<TorrentDiscoveredFile>,
    val proxyUrl: String? = null,
)

