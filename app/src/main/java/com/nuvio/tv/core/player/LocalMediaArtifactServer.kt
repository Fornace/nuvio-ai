package com.nuvio.tv.core.player

import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Loopback-only HTTP server that publishes completed translated-audio artifacts to the
 * local media player over tokenized URLs (Milestone 2, translated-voice overlay).
 *
 * Security invariants:
 *  - Binds [LOOPBACK_HOST] (127.0.0.1) on an ephemeral port. It never binds a wildcard
 *    address, so nothing on the LAN or any other interface can reach it.
 *  - Every published artifact is addressed by a URL path containing a random 128-bit
 *    (32 hex character) token from [SecureRandom]. Unknown, malformed or revoked tokens
 *    return 404 with an empty body. Tokens are unguessable and carry no readable data.
 *  - Adds no upstream playback headers: request headers (Authorization, Cookie, and any
 *    other primary playback header) are never copied into responses and never logged.
 *    Responses contain only Content-Type, Content-Length, Accept-Ranges, Content-Range
 *    (range requests) and the transport headers NanoHTTPD itself adds (Date/Connection).
 *  - [revoke] immediately withdraws a single token; [stop] closes the listening socket
 *    and invalidates every token.
 *
 * Range handling: a valid `Range: bytes=...` request returns 206 with a `Content-Range`
 * slice; an unsatisfiable range returns 416 whose `Content-Range` uses the star-slash
 * total form; a request without `Range` returns the full body with 200. Responses are
 * never gzip-encoded (see [useGzipWhenAccepted]), so Content-Length is always the exact
 * artifact length and range slices stay byte-exact for the player.
 *
 * Lifecycle: [publish] may only be called while the server is running; each start begins
 * with an empty token set (previous tokens stay invalid). Serving reads run on
 * NanoHTTPD's worker threads, so [ArtifactSource] implementations must be safe for
 * concurrent reads.
 */
class LocalMediaArtifactServer : NanoHTTPD(LOOPBACK_HOST, 0) {

    /**
     * Immutable content source for one published artifact. Implementations open a fresh
     * stream for each request; the server positions and bounds range streams itself, so
     * large ranges are served without being copied into one in-memory array.
     */
    interface ArtifactSource {
        /** Declared MIME type, e.g. `audio/mp4`; must not contain control characters. */
        val mimeType: String

        /** Exact content length in bytes; must not change for the life of the artifact. */
        fun length(): Long

        /** Opens a fresh stream over the full artifact starting at byte zero. */
        fun open(): InputStream
    }

    /** [ArtifactSource] backed by an in-memory array, e.g. a decoded artifact held by the host. */
    class ByteArrayArtifactSource(
        private val bytes: ByteArray,
        override val mimeType: String
    ) : ArtifactSource {
        override fun length(): Long = bytes.size.toLong()
        override fun open(): InputStream = ByteArrayInputStream(bytes)
    }

    /** [ArtifactSource] backed by a completed file in bounded app-private storage. */
    class FileArtifactSource(
        private val file: File,
        override val mimeType: String
    ) : ArtifactSource {
        override fun length(): Long = file.length()
        override fun open(): InputStream = FileInputStream(file)
    }

    private val artifacts = ConcurrentHashMap<String, ArtifactSource>()
    private val random = SecureRandom()

    /** Loopback host the server binds; always [LOOPBACK_HOST]. */
    val host: String get() = LOOPBACK_HOST

    /** Bound ephemeral port, or -1 before [start] / after [stop]. */
    val port: Int get() = listeningPort

    /**
     * Publishes [source] under a fresh random token and returns the URL path
     * (`/artifact/<token>`) that serves it. Must be called while the server is running.
     */
    fun publish(source: ArtifactSource): String {
        check(isAlive) { "publish() requires a running server" }
        require(isSafeMimeType(source.mimeType)) {
            "MIME type must be a token without control characters (was rejected)"
        }
        require(source.length() >= 0L) { "artifact length must not be negative" }
        val token = newToken()
        artifacts[token] = source
        return "$PATH_PREFIX/$token"
    }

    /** Publishes [bytes] with [mimeType]; see [publish]. */
    fun publish(bytes: ByteArray, mimeType: String): String =
        publish(ByteArrayArtifactSource(bytes, mimeType))

    /** Publishes the contents of [file] with [mimeType]; see [publish]. */
    fun publish(file: File, mimeType: String): String =
        publish(FileArtifactSource(file, mimeType))

    /** Full URL for a path returned by [publish], once the server is running. */
    fun urlFor(path: String): String = "http://$LOOPBACK_HOST:${listeningPort}$path"

    /** Makes subsequent requests for [token] return 404. Unknown tokens are ignored. */
    fun revoke(token: String) {
        artifacts.remove(token)
    }

    /** Revokes every published token; requests afterwards all return 404. */
    fun revokeAll() {
        artifacts.clear()
    }

    /**
     * Closes the listening socket (via NanoHTTPD) and invalidates every token, so a later
     * restart of the same instance cannot resurrect previously published URLs.
     */
    override fun stop() {
        super.stop()
        revokeAll()
    }

    override fun serve(session: IHTTPSession): Response {
        val method = session.method
        if (method != Method.GET && method != Method.HEAD) return notFound()
        val token = tokenFromPath(session.uri)
        val source = if (token != null) artifacts[token] else null
        if (source == null) return notFound()
        return serveArtifact(source, requestRangeHeader(session))
    }

    /**
     * Never gzip. A range-capable artifact server must always emit the exact bytes with an
     * explicit Content-Length; Content-Encoding would corrupt range semantics and hide the
     * real length from the player.
     */
    override fun useGzipWhenAccepted(response: Response): Boolean = false

    private fun serveArtifact(source: ArtifactSource, rangeHeader: String?): Response {
        val total = source.length()
        if (total < 0L) return notFound()
        if (total == 0L) {
            return if (rangeHeader == null) fullResponse(source, total) else rangeNotSatisfiableResponse(total)
        }
        val range = rangeHeader?.let { parseByteRange(it, total) }
        return when (range) {
            null -> fullResponse(source, total)
            is RangeResult.Invalid -> rangeNotSatisfiableResponse(total)
            is RangeResult.Slice -> partialResponse(source, range, total)
        }
    }

    private fun fullResponse(source: ArtifactSource, total: Long): Response {
        val response = newFixedLengthResponse(
            Response.Status.OK,
            source.mimeType,
            source.open(),
            total
        )
        response.addHeader(HEADER_ACCEPT_RANGES, "bytes")
        return response
    }

    private fun partialResponse(source: ArtifactSource, range: RangeResult.Slice, total: Long): Response {
        val input = source.open()
        try {
            skipFully(input, range.start)
        } catch (error: Throwable) {
            input.close()
            throw error
        }
        val length = range.lengthInclusive
        val response = newFixedLengthResponse(
            Response.Status.PARTIAL_CONTENT,
            source.mimeType,
            BoundedInputStream(input, length),
            length
        )
        response.addHeader(HEADER_CONTENT_RANGE, "bytes ${range.start}-${range.endInclusive}/$total")
        response.addHeader(HEADER_ACCEPT_RANGES, "bytes")
        return response
    }

    private fun rangeNotSatisfiableResponse(total: Long): Response {
        val response = newFixedLengthResponse(
            Response.Status.RANGE_NOT_SATISFIABLE,
            MIME_PLAINTEXT,
            ByteArrayInputStream(ByteArray(0)),
            0L
        )
        response.addHeader(HEADER_CONTENT_RANGE, "bytes */$total")
        return response
    }

    private fun notFound(): Response =
        newFixedLengthResponse(
            Response.Status.NOT_FOUND,
            MIME_PLAINTEXT,
            ByteArrayInputStream(ByteArray(0)),
            0L
        )

    private fun requestRangeHeader(session: IHTTPSession): String? {
        val headers = session.headers ?: return null
        return headers.entries.firstOrNull { it.key.equals("range", ignoreCase = true) }?.value
    }

    private fun tokenFromPath(uri: String?): String? {
        if (uri == null || !uri.startsWith("$PATH_PREFIX/")) return null
        val token = uri.removePrefix("$PATH_PREFIX/")
        return if (token.length == TOKEN_HEX_LENGTH && token.all { it in '0'..'9' || it in 'a'..'f' }) token else null
    }

    private fun skipFully(input: InputStream, byteCount: Long) {
        var remaining = byteCount
        while (remaining > 0L) {
            val skipped = input.skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else {
                check(input.read() >= 0) { "artifact source is shorter than its declared length" }
                remaining--
            }
        }
    }

    private class BoundedInputStream(
        private val upstream: InputStream,
        private var remaining: Long
    ) : InputStream() {
        override fun read(): Int {
            if (remaining == 0L) return -1
            val value = upstream.read()
            if (value >= 0) remaining--
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (remaining == 0L) return -1
            val boundedLength = minOf(length.toLong(), remaining).toInt()
            val read = upstream.read(buffer, offset, boundedLength)
            if (read > 0) remaining -= read.toLong()
            return read
        }

        override fun skip(byteCount: Long): Long {
            val skipped = upstream.skip(minOf(byteCount, remaining))
            remaining -= skipped
            return skipped
        }

        override fun available(): Int = minOf(upstream.available().toLong(), remaining).toInt()

        override fun close() = upstream.close()
    }

    private fun newToken(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        val hex = StringBuilder(TOKEN_HEX_LENGTH)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            hex.append("0123456789abcdef"[v ushr 4])
            hex.append("0123456789abcdef"[v and 0x0F])
        }
        return hex.toString()
    }

    private sealed interface RangeResult {
        data object Invalid : RangeResult
        data class Slice(val start: Long, val endInclusive: Long) : RangeResult {
            val lengthInclusive: Long get() = endInclusive - start + 1L
        }
    }

    private fun parseByteRange(header: String, total: Long): RangeResult? {
        val trimmed = header.trim()
        val prefix = "bytes="
        if (!trimmed.startsWith(prefix, ignoreCase = true)) return null
        val spec = trimmed.substring(prefix.length).trim()
        if (spec.isEmpty() || spec.contains(',')) return null // multi-range unsupported; serve full body
        if (!spec.contains('-')) return RangeResult.Invalid
        return if (spec.startsWith("-")) {
            // Suffix form: last N bytes.
            val suffixLength = spec.substring(1).toLongOrNull() ?: return RangeResult.Invalid
            if (suffixLength <= 0L) RangeResult.Invalid
            else if (suffixLength >= total) RangeResult.Slice(0L, total - 1L)
            else RangeResult.Slice(total - suffixLength, total - 1L)
        } else {
            val start = spec.substringBefore('-').toLongOrNull() ?: return RangeResult.Invalid
            if (start < 0L || start >= total) return RangeResult.Invalid
            val endToken = if (spec.contains('-')) spec.substringAfter('-') else ""
            val endInclusive = when {
                endToken.isEmpty() -> total - 1L
                else -> endToken.toLongOrNull()?.coerceAtMost(total - 1L) ?: return RangeResult.Invalid
            }
            if (endInclusive < start) RangeResult.Invalid else RangeResult.Slice(start, endInclusive)
        }
    }

    companion object {
        /** The only host this server ever binds. */
        const val LOOPBACK_HOST = "127.0.0.1"

        /** URL prefix under which artifact tokens are served. */
        const val PATH_PREFIX = "/artifact"

        private const val TOKEN_BYTES = 16
        private const val TOKEN_HEX_LENGTH = TOKEN_BYTES * 2
        private const val HEADER_ACCEPT_RANGES = "Accept-Ranges"
        private const val HEADER_CONTENT_RANGE = "Content-Range"
        private val MIME_TYPE_PATTERN =
            Regex("""^[A-Za-z0-9][A-Za-z0-9!#$&^_.+-]{0,126}/[A-Za-z0-9][A-Za-z0-9!#$&^_.+-]{0,126}$""")

        /** True when [value] is a safe `type/subtype` MIME string with no header-injection surface. */
        fun isSafeMimeType(value: String): Boolean =
            value.length <= 254 && value.none { it.isISOControl() } && MIME_TYPE_PATTERN.matches(value)
    }
}
