package eu.kanade.tachiyomi.ui.anime.player

import android.content.Context
import com.frostwire.jlibtorrent.AddTorrentParams
import com.frostwire.jlibtorrent.ErrorCode
import com.frostwire.jlibtorrent.Priority
import com.frostwire.jlibtorrent.SessionHandle
import com.frostwire.jlibtorrent.SessionManager
import com.frostwire.jlibtorrent.TorrentFlags
import com.frostwire.jlibtorrent.TorrentHandle
import com.frostwire.jlibtorrent.TorrentInfo
import com.frostwire.jlibtorrent.swig.error_code
import eu.kanade.tachiyomi.network.NetworkHelper
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import logcat.LogPriority
import tachiyomi.core.common.util.system.logcat
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

class JlibTorrentEngine(
    context: Context,
    private val networkHelper: NetworkHelper = Injekt.get(),
) : TorrentEngine {

    private val appContext = context.applicationContext
    private val sessionManager = SessionManager(false)
    private val sessionMutex = Mutex()
    private val sessions = ConcurrentHashMap<String, ManagedTorrentSession>()
    private val proxyServer = TorrentLoopbackProxyServer(
        fileProvider = ::lookupProxyFile,
        rangePreparer = ::awaitByteRangeAvailable,
    )

    override suspend fun prepareSession(
        request: AnimePlaybackRequest,
    ): TorrentEngineSession = withContext(Dispatchers.IO) {
        sessionMutex.withLock {
            startInfrastructureIfNeeded()

            val sessionDirectory = sessionDirectory(request).apply { mkdirs() }
            val addTorrentParams = buildAddTorrentParams(request, sessionDirectory)
            val handle = addTorrent(addTorrentParams)
            handle.resume()

            val torrentInfo = waitForMetadata(handle)
            val sessionId = UUID.randomUUID().toString()
            val files = buildManagedFiles(
                sessionId = sessionId,
                storageDirectory = sessionDirectory,
                torrentInfo = torrentInfo,
            )
            sessions[sessionId] = ManagedTorrentSession(
                sessionId = sessionId,
                request = request,
                handle = handle,
                torrentInfo = torrentInfo,
                storageDirectory = sessionDirectory,
                filesById = files.associateBy(ManagedTorrentFile::fileId),
            )

            TorrentEngineSession(
                sessionId = sessionId,
                discoveredFiles = files.map {
                    TorrentDiscoveredFile(
                        id = it.fileId,
                        path = it.relativePath.replace(File.separatorChar, '/'),
                        sizeBytes = it.sizeBytes,
                    )
                },
                subtitleTrackUrls = files
                    .filter { it.isSubtitle }
                    .associate { it.fileId to proxyServer.subtitleUrl(sessionId, it.fileId) },
            )
        }
    }

    override suspend fun selectVideoFile(
        sessionId: String,
        fileId: String,
    ): TorrentEnginePlaybackTarget? = withContext(Dispatchers.IO) {
        val session = sessions[sessionId] ?: return@withContext null
        val selectedFile = session.filesById[fileId] ?: return@withContext null

        prioritizeFiles(session, selectedFile)
        session.selectedVideoFileId = fileId

        TorrentEnginePlaybackTarget(
            proxyUrl = proxyServer.streamUrl(sessionId, fileId),
        )
    }

    override suspend fun selectSubtitleTrack(
        sessionId: String,
        trackId: String?,
    ) = withContext(Dispatchers.IO) {
        val session = sessions[sessionId] ?: return@withContext
        session.selectedSubtitleTrackId = trackId
        val subtitleFile = trackId?.let(session.filesById::get)?.takeIf { it.isSubtitle } ?: return@withContext
        session.handle.filePriority(subtitleFile.fileIndex, Priority.FIVE)
    }

    override suspend fun stopSession(
        sessionId: String,
    ) = withContext(Dispatchers.IO) {
        val session = sessions.remove(sessionId) ?: return@withContext
        runCatching {
            sessionManager.remove(session.handle)
        }.onFailure {
            logcat(LogPriority.WARN, it) { "Failed to remove torrent session $sessionId" }
        }

        if (sessions.isEmpty()) {
            proxyServer.stopIfStarted()
            runCatching { sessionManager.stop() }
                .onFailure { logcat(LogPriority.WARN, it) { "Failed to stop jlibtorrent session manager" } }
        }
    }

    private fun startInfrastructureIfNeeded() {
        if (!sessionManager.isRunning()) {
            sessionManager.start()
        }
        proxyServer.startIfNeeded()
    }

    private suspend fun buildAddTorrentParams(
        request: AnimePlaybackRequest,
        sessionDirectory: File,
    ): AddTorrentParams {
        val torrentInfo = request.descriptor.torrentUrl
            ?.takeIf { it.isNotBlank() }
            ?.let { torrentUrl -> downloadTorrentInfo(torrentUrl) }
        val params = when {
            torrentInfo != null -> AddTorrentParams().apply {
                torrentInfo(torrentInfo)
            }
            !request.descriptor.magnetUri.isNullOrBlank() -> {
                AddTorrentParams.parseMagnetUri(request.descriptor.magnetUri!!)
            }
            else -> {
                error("Torrent playback requires either a magnet URI or a torrent URL.")
            }
        }

        params.name(request.descriptor.releaseTitle)
        params.savePath(sessionDirectory.absolutePath)
        params.flags(
            TorrentFlags.PAUSED
                .or_(TorrentFlags.SEQUENTIAL_DOWNLOAD)
                .or_(TorrentFlags.UPDATE_SUBSCRIBE),
        )
        return params
    }

    private suspend fun downloadTorrentInfo(
        torrentUrl: String,
    ): TorrentInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(torrentUrl)
            .build()
        networkHelper.client.newCall(request).await().use { response ->
            check(response.isSuccessful) {
                "Failed to fetch torrent file: ${response.code}"
            }
            TorrentInfo(response.body.bytes())
        }
    }

    private fun addTorrent(
        params: AddTorrentParams,
    ): TorrentHandle {
        val sessionHandle = SessionHandle(sessionManager.swig())
        val errorCode = ErrorCode(error_code())
        val handle = sessionHandle.addTorrent(params, errorCode)
        check(!errorCode.isError()) {
            "Failed to add torrent: ${errorCode.message()}"
        }
        return handle
    }

    private suspend fun waitForMetadata(
        handle: TorrentHandle,
    ): TorrentInfo {
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < METADATA_TIMEOUT_MS) {
            val status = handle.status()
            if (status.hasMetadata()) {
                return handle.torrentFile()
                    ?: error("Metadata became available, but torrent info is still missing.")
            }
            delay(POLL_DELAY_MS)
        }
        error("Timed out while waiting for torrent metadata.")
    }

    private fun buildManagedFiles(
        sessionId: String,
        storageDirectory: File,
        torrentInfo: TorrentInfo,
    ): List<ManagedTorrentFile> {
        val fileStorage = torrentInfo.files()
        return buildList {
            for (index in 0 until fileStorage.numFiles()) {
                val relativePath = fileStorage.filePath(index)
                add(
                    ManagedTorrentFile(
                        sessionId = sessionId,
                        fileId = "file:$index",
                        fileIndex = index,
                        relativePath = relativePath,
                        absoluteFile = File(storageDirectory, relativePath),
                        sizeBytes = fileStorage.fileSize(index),
                        mimeType = relativePath.toMimeType(),
                        isVideo = relativePath.isLikelyVideoFile(),
                        isSubtitle = relativePath.isLikelySubtitleFile(),
                    ),
                )
            }
        }
    }

    private fun prioritizeFiles(
        session: ManagedTorrentSession,
        selectedFile: ManagedTorrentFile,
    ) {
        val priorities = Array(session.torrentInfo.numFiles()) { Priority.IGNORE }
        priorities[selectedFile.fileIndex] = Priority.SEVEN

        session.filesById.values
            .filter { it.isSubtitle }
            .forEach { subtitleFile ->
                priorities[subtitleFile.fileIndex] = Priority.FIVE
            }

        session.handle.prioritizeFiles(priorities)
        session.handle.resume()
    }

    private fun lookupProxyFile(
        sessionId: String,
        fileId: String,
    ): ProxiedTorrentFile? {
        val session = sessions[sessionId] ?: return null
        val file = session.filesById[fileId] ?: return null
        return ProxiedTorrentFile(
            sessionId = sessionId,
            fileId = fileId,
            file = file.absoluteFile,
            sizeBytes = file.sizeBytes,
            mimeType = file.mimeType,
        )
    }

    private suspend fun awaitByteRangeAvailable(
        sessionId: String,
        fileId: String,
        start: Long,
        endExclusive: Long,
    ) {
        val session = sessions[sessionId] ?: return
        val file = session.filesById[fileId] ?: return
        if (endExclusive <= start || file.sizeBytes <= 0L) return

        val safeStart = start.coerceAtLeast(0L)
        val safeEnd = endExclusive.coerceAtMost(file.sizeBytes)
        if (safeEnd <= safeStart) return

        val firstPiece = session.torrentInfo.mapFile(file.fileIndex, safeStart, 1).piece()
        val lastPiece = session.torrentInfo.mapFile(file.fileIndex, max(safeStart, safeEnd - 1L), 1).piece()

        for (piece in firstPiece..lastPiece) {
            session.handle.piecePriority(piece, Priority.SEVEN)
            session.handle.setPieceDeadline(piece, 0)
        }

        val deadline = System.currentTimeMillis() + PIECE_WAIT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if ((firstPiece..lastPiece).all(session.handle::havePiece)) {
                return
            }
            delay(POLL_DELAY_MS)
        }

        error("Timed out while buffering torrent pieces for playback.")
    }

    private fun sessionDirectory(
        request: AnimePlaybackRequest,
    ): File {
        val root = File(appContext.cacheDir, "anime-torrents")
        return File(root, "${request.sourceId}-${request.episodeId}")
    }

    private data class ManagedTorrentSession(
        val sessionId: String,
        val request: AnimePlaybackRequest,
        val handle: TorrentHandle,
        val torrentInfo: TorrentInfo,
        val storageDirectory: File,
        val filesById: Map<String, ManagedTorrentFile>,
        var selectedVideoFileId: String? = null,
        var selectedSubtitleTrackId: String? = null,
    )

    private data class ManagedTorrentFile(
        val sessionId: String,
        val fileId: String,
        val fileIndex: Int,
        val relativePath: String,
        val absoluteFile: File,
        val sizeBytes: Long,
        val mimeType: String,
        val isVideo: Boolean,
        val isSubtitle: Boolean,
    )

    private companion object {
        const val METADATA_TIMEOUT_MS = 120_000L
        const val PIECE_WAIT_TIMEOUT_MS = 60_000L
        const val POLL_DELAY_MS = 250L

        fun String.isLikelyVideoFile(): Boolean {
            return substringAfterLast('.', "").lowercase() in setOf("mkv", "mp4", "avi", "webm", "m4v", "ts")
        }

        fun String.isLikelySubtitleFile(): Boolean {
            return substringAfterLast('.', "").lowercase() in setOf("srt", "ass", "ssa", "vtt")
        }

        fun String.toMimeType(): String {
            return when (substringAfterLast('.', "").lowercase()) {
                "mkv" -> "video/x-matroska"
                "mp4", "m4v" -> "video/mp4"
                "webm" -> "video/webm"
                "ts" -> "video/mp2t"
                "avi" -> "video/x-msvideo"
                "srt" -> "application/x-subrip"
                "vtt" -> "text/vtt"
                "ass", "ssa" -> "text/x-ssa"
                else -> "application/octet-stream"
            }
        }
    }
}
