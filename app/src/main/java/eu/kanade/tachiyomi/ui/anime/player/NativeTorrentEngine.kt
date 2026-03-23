package eu.kanade.tachiyomi.ui.anime.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
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
            sizeBytes = request.descriptor.sizeBytes,
            subtitleHint = request.descriptor.subtitleHint,
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
        )?.let { proxyUrl ->
            TorrentEnginePlaybackTarget(proxyUrl = proxyUrl)
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
        sizeBytes: Long?,
        subtitleHint: String?,
    ): NativePrepareResult {
        return if (NativeTorrentLibraryLoader.isAvailable()) {
            NativeTorrentJni.prepareSessionJson(
                magnetUri = magnetUri,
                torrentUrl = torrentUrl,
                infoHash = infoHash,
                storageDirectory = storageDirectory,
                displayName = displayName,
                sizeBytes = sizeBytes ?: -1L,
                subtitleHint = subtitleHint,
            )?.let(::parsePrepareResult) ?: fallbackPrepareSession(
                magnetUri = magnetUri,
                torrentUrl = torrentUrl,
                infoHash = infoHash,
                storageDirectory = storageDirectory,
                displayName = displayName,
                sizeBytes = sizeBytes,
                subtitleHint = subtitleHint,
            )
        } else {
            fallbackPrepareSession(
                magnetUri = magnetUri,
                torrentUrl = torrentUrl,
                infoHash = infoHash,
                storageDirectory = storageDirectory,
                displayName = displayName,
                sizeBytes = sizeBytes,
                subtitleHint = subtitleHint,
            )
        }
    }

    fun selectVideoFile(
        sessionId: String,
        fileId: String,
    ): String? {
        return if (NativeTorrentLibraryLoader.isAvailable()) {
            NativeTorrentJni.selectVideoFileProxyUrl(
                sessionId = sessionId,
                fileId = fileId,
            )
        } else {
            null
        }
    }

    fun selectSubtitleTrack(
        sessionId: String,
        trackId: String?,
    ) {
        if (NativeTorrentLibraryLoader.isAvailable()) {
            NativeTorrentJni.selectSubtitleTrack(
                sessionId = sessionId,
                trackId = trackId,
            )
        }
    }

    fun stopSession(
        sessionId: String,
    ) {
        if (NativeTorrentLibraryLoader.isAvailable()) {
            NativeTorrentJni.stopSession(sessionId)
        }
    }

    private fun parsePrepareResult(
        json: String,
    ): NativePrepareResult {
        val root = JSONObject(json)
        val files = root.optJSONArray("files") ?: JSONArray()
        return NativePrepareResult(
            sessionId = root.optString("sessionId").ifBlank { "native-session" },
            files = buildList {
                for (index in 0 until files.length()) {
                    val item = files.optJSONObject(index) ?: continue
                    add(
                        TorrentDiscoveredFile(
                            id = item.optString("id").ifBlank { "file-$index" },
                            path = item.optString("path").ifBlank { "episode-$index.mkv" },
                            sizeBytes = item.optLong("sizeBytes").takeIf { it >= 0L },
                        ),
                    )
                }
            },
            proxyUrl = root.optString("proxyUrl").takeIf { it.isNotBlank() },
        )
    }

    private fun fallbackPrepareSession(
        magnetUri: String?,
        torrentUrl: String?,
        infoHash: String?,
        storageDirectory: String,
        displayName: String,
        sizeBytes: Long?,
        subtitleHint: String?,
    ): NativePrepareResult {
        val normalizedName = displayName.substringAfterLast('/').ifBlank { "episode.mkv" }
        val mediaPath = if ('.' in normalizedName) normalizedName else "$normalizedName.mkv"
        val sidecarSubtitle = subtitleHint
            ?.takeIf { it.isNotBlank() }
            ?.let {
                if ('.' in it) it.substringAfterLast('/') else "${it.substringAfterLast('/')}.ass"
            }
            ?: displayName.substringBeforeLast('.', displayName).substringAfterLast('/').ifBlank { "subtitle" } + ".ass"

        return NativePrepareResult(
            sessionId = infoHash
                ?: magnetUri?.hashCode()?.toString()
                ?: torrentUrl?.hashCode()?.toString()
                ?: storageDirectory.hashCode().toString(),
            files = buildList {
                add(
                    TorrentDiscoveredFile(
                        id = "video-hint",
                        path = mediaPath,
                        sizeBytes = sizeBytes,
                    ),
                )
                add(
                    TorrentDiscoveredFile(
                        id = "subtitle-hint",
                        path = sidecarSubtitle,
                    ),
                )
            },
            proxyUrl = null,
        )
    }
}

internal object NativeTorrentJni {

    @JvmStatic
    external fun prepareSessionJson(
        magnetUri: String?,
        torrentUrl: String?,
        infoHash: String?,
        storageDirectory: String,
        displayName: String,
        sizeBytes: Long,
        subtitleHint: String?,
    ): String?

    @JvmStatic
    external fun selectVideoFileProxyUrl(
        sessionId: String,
        fileId: String,
    ): String?

    @JvmStatic
    external fun selectSubtitleTrack(
        sessionId: String,
        trackId: String?,
    )

    @JvmStatic
    external fun stopSession(
        sessionId: String,
    )
}

data class NativePrepareResult(
    val sessionId: String,
    val files: List<TorrentDiscoveredFile>,
    val proxyUrl: String? = null,
)
