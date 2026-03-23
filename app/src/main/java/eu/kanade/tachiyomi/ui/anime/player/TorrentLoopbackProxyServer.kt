package eu.kanade.tachiyomi.ui.anime.player

import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import kotlin.math.min

class TorrentLoopbackProxyServer(
    private val fileProvider: (sessionId: String, fileId: String) -> ProxiedTorrentFile?,
    private val rangePreparer: suspend (sessionId: String, fileId: String, start: Long, endExclusive: Long) -> Unit,
) : NanoHTTPD("127.0.0.1", 0) {

    @Volatile
    private var started = false

    @Synchronized
    fun startIfNeeded() {
        if (started) return
        start(SOCKET_READ_TIMEOUT, false)
        started = true
    }

    @Synchronized
    fun stopIfStarted() {
        if (!started) return
        stop()
        started = false
    }

    fun streamUrl(
        sessionId: String,
        fileId: String,
    ): String {
        startIfNeeded()
        return "http://127.0.0.1:$listeningPort/stream/$sessionId/$fileId"
    }

    fun subtitleUrl(
        sessionId: String,
        fileId: String,
    ): String {
        startIfNeeded()
        return "http://127.0.0.1:$listeningPort/subtitle/$sessionId/$fileId"
    }

    override fun serve(session: IHTTPSession): Response {
        val parts = session.uri.trim('/').split('/').filter { it.isNotBlank() }
        if (parts.size != 3) {
            return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }

        val sessionId = parts[1]
        val fileId = parts[2]
        val proxiedFile = fileProvider(sessionId, fileId)
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Missing file")

        val totalLength = proxiedFile.sizeBytes.coerceAtLeast(0L)
        val range = parseRange(session.headers["range"], totalLength)
        val contentLength = range.endInclusive - range.start + 1L
        val response = newFixedLengthResponse(
            if (range.isPartial) Response.Status.PARTIAL_CONTENT else Response.Status.OK,
            proxiedFile.mimeType,
            TorrentStreamingInputStream(
                proxiedFile = proxiedFile,
                startOffset = range.start,
                length = contentLength,
                rangePreparer = rangePreparer,
            ),
            contentLength,
        )

        response.addHeader("Accept-Ranges", "bytes")
        response.addHeader("Content-Length", contentLength.toString())
        if (range.isPartial) {
            response.addHeader(
                "Content-Range",
                "bytes ${range.start}-${range.endInclusive}/$totalLength",
            )
        }
        return response
    }

    private fun parseRange(
        header: String?,
        totalLength: Long,
    ): RequestedRange {
        if (header.isNullOrBlank() || totalLength <= 0L) {
            return RequestedRange(
                start = 0L,
                endInclusive = (totalLength - 1L).coerceAtLeast(0L),
                isPartial = false,
            )
        }

        val rangeValue = header.substringAfter("bytes=", "")
        val start = rangeValue.substringBefore('-', "").toLongOrNull()
        val end = rangeValue.substringAfter('-', "").toLongOrNull()
        val resolvedStart = start ?: 0L
        val resolvedEnd = (end ?: (totalLength - 1L)).coerceAtMost(totalLength - 1L)
        return RequestedRange(
            start = resolvedStart.coerceAtLeast(0L),
            endInclusive = resolvedEnd.coerceAtLeast(resolvedStart),
            isPartial = true,
        )
    }

    private data class RequestedRange(
        val start: Long,
        val endInclusive: Long,
        val isPartial: Boolean,
    )
}

data class ProxiedTorrentFile(
    val sessionId: String,
    val fileId: String,
    val file: File,
    val sizeBytes: Long,
    val mimeType: String,
)

private class TorrentStreamingInputStream(
    private val proxiedFile: ProxiedTorrentFile,
    startOffset: Long,
    length: Long,
    private val rangePreparer: suspend (sessionId: String, fileId: String, start: Long, endExclusive: Long) -> Unit,
) : InputStream() {

    private val raf = RandomAccessFile(proxiedFile.file, "r").apply {
        seek(startOffset)
    }
    private var position = startOffset
    private var remaining = length

    override fun read(): Int {
        val buffer = ByteArray(1)
        val read = read(buffer, 0, 1)
        return if (read == -1) -1 else buffer[0].toInt() and 0xFF
    }

    override fun read(
        buffer: ByteArray,
        offset: Int,
        length: Int,
    ): Int {
        if (remaining <= 0L) return -1

        val requested = min(length.toLong(), remaining).coerceAtMost(CHUNK_SIZE).toInt()
        if (requested <= 0) return -1

        runBlocking {
            rangePreparer(
                proxiedFile.sessionId,
                proxiedFile.fileId,
                position,
                position + requested,
            )
        }

        val read = raf.read(buffer, offset, requested)
        if (read <= 0) return -1

        position += read
        remaining -= read
        return read
    }

    override fun close() {
        raf.close()
    }

    private companion object {
        const val CHUNK_SIZE = 256 * 1024L
    }
}
