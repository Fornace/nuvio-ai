package com.nuvio.tv.core.player

import java.io.Closeable
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Deterministic JVM tests for the OpenSubtitles hash range-read contract.
 * okhttp3.mockwebserver is not on the unit-test classpath, so these tests use a
 * minimal loopback ServerSocket stub that answers one request per connection.
 */
class OpenSubtitlesHasherTest {

    private val client = OkHttpClient()

    private class StubResponse(
        val status: String,
        val headers: List<String>,
        val body: ByteArray? = null
    )

    private class StubRequest(val method: String, val path: String, val range: String?)

    /**
     * Minimal single-request-per-connection HTTP/1.1 stub server. Reads the
     * request head, hands it to [handler], writes the scripted response and
     * closes the socket (Connection: close).
     */
    private class StubHttpServer(private val handler: (StubRequest) -> StubResponse) : Closeable {
        private val serverSocket = ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"))
        private val acceptThread = Thread { runLoop() }.apply {
            isDaemon = true
            start()
        }

        val url: String get() = "http://127.0.0.1:${serverSocket.localPort}/video.mkv"

        private fun runLoop() {
            while (!serverSocket.isClosed) {
                val socket = try {
                    serverSocket.accept()
                } catch (_: java.io.IOException) {
                    return
                }
                Thread { serve(socket) }.apply { isDaemon = true }.start()
            }
        }

        private fun serve(socket: Socket) {
            socket.use { s ->
                s.soTimeout = 10_000
                val input = s.getInputStream()
                val head = StringBuilder()
                val crlf = "\r\n\r\n"
                while (!head.contains(crlf)) {
                    val b = input.read()
                    if (b < 0) return
                    head.append(b.toChar())
                }
                val lines = head.toString().split("\r\n")
                val requestLine = lines.firstOrNull()?.split(" ") ?: return
                if (requestLine.size < 2) return
                val range = lines.drop(1)
                    .firstOrNull { it.startsWith("Range:", ignoreCase = true) }
                    ?.substringAfter(':')?.trim()
                val response = handler(StubRequest(requestLine[0], requestLine[1], range))
                val output: OutputStream = s.getOutputStream()
                output.write("HTTP/1.1 ${response.status}\r\n".toByteArray())
                output.write("Connection: close\r\n".toByteArray())
                response.headers.forEach { output.write("$it\r\n".toByteArray()) }
                output.write("\r\n".toByteArray())
                response.body?.let { body ->
                    output.write(body)
                }
                output.flush()
            }
        }

        override fun close() {
            serverSocket.close()
            acceptThread.join(1_000)
        }
    }

    /** Deterministic pseudo-media file larger than two 64 KiB chunks. */
    private fun testFileBytes(): ByteArray {
        val size = CHUNK_BYTES * 2 + 12_345
        return ByteArray(size) { ((it * 31 + 7) and 0xFF).toByte() }
    }

    /** Independent implementation of the documented OpenSubtitles chunk sum. */
    private fun littleEndianLongSum(chunk: ByteArray): Long {
        var sum = 0L
        var i = 0
        while (i + 8 <= chunk.size) {
            var v = 0L
            for (j in 0 until 8) {
                v = v or ((chunk[i + j].toLong() and 0xFF) shl (j * 8))
            }
            sum += v
            i += 8
        }
        return sum
    }

    private fun expectedHash(file: ByteArray): String {
        val head = file.copyOfRange(0, CHUNK_BYTES)
        val tail = file.copyOfRange(file.size - CHUNK_BYTES, file.size)
        return "%016x".format(file.size.toLong() + littleEndianLongSum(head) + littleEndianLongSum(tail))
    }

    @Test
    fun `valid 206 with matching content range produces the documented hash`() = runTest {
        val file = testFileBytes()
        StubHttpServer { request ->
            when {
                request.method == "HEAD" -> StubResponse(
                    "200 OK",
                    listOf("Content-Length: ${file.size}", "Accept-Ranges: bytes")
                )
                request.range != null -> {
                    val start = request.range.substringAfter("bytes=").substringBefore('-').toLong()
                    val end = request.range.substringAfter('-').toLong()
                    StubResponse(
                        "206 Partial Content",
                        listOf(
                            "Content-Range: bytes $start-$end/${file.size}",
                            "Content-Length: ${end - start + 1}"
                        ),
                        file.copyOfRange(start.toInt(), end.toInt() + 1)
                    )
                }
                else -> StubResponse("200 OK", listOf("Content-Length: ${file.size}"), file)
            }
        }.use { server ->
            val result = OpenSubtitlesHasher.compute(server.url, emptyMap(), client)

            assertNotNull(result)
            assertEquals(expectedHash(file), result!!.hash)
            assertEquals(file.size.toLong(), result.fileSize)
        }
    }

    @Test
    fun `200 response that ignored the range request fails the hash`() = runTest {
        val file = testFileBytes()
        StubHttpServer { request ->
            if (request.method == "HEAD") {
                StubResponse("200 OK", listOf("Content-Length: ${file.size}", "Accept-Ranges: bytes"))
            } else {
                // Server ignores Range and answers 200 with the full body.
                StubResponse("200 OK", listOf("Content-Length: ${file.size}"), file)
            }
        }.use { server ->
            assertNull(OpenSubtitlesHasher.compute(server.url, emptyMap(), client))
        }
    }

    @Test
    fun `content range total that mismatches the file size fails the hash`() = runTest {
        val file = testFileBytes()
        StubHttpServer { request ->
            when {
                request.method == "HEAD" -> StubResponse(
                    "200 OK",
                    listOf("Content-Length: ${file.size}", "Accept-Ranges: bytes")
                )
                request.range != null -> {
                    val start = request.range.substringAfter("bytes=").substringBefore('-').toLong()
                    val end = request.range.substringAfter('-').toLong()
                    StubResponse(
                        "206 Partial Content",
                        listOf(
                            // Total disagrees with the size used to build the request.
                            "Content-Range: bytes $start-$end/${file.size + 5}",
                            "Content-Length: ${end - start + 1}"
                        ),
                        file.copyOfRange(start.toInt(), end.toInt() + 1)
                    )
                }
                else -> StubResponse("200 OK", listOf("Content-Length: ${file.size}"), file)
            }
        }.use { server ->
            assertNull(OpenSubtitlesHasher.compute(server.url, emptyMap(), client))
        }
    }

    @Test
    fun `short chunk body fails the hash instead of producing a partial sum`() = runTest {
        val file = testFileBytes()
        StubHttpServer { request ->
            when {
                request.method == "HEAD" -> StubResponse(
                    "200 OK",
                    listOf("Content-Length: ${file.size}", "Accept-Ranges: bytes")
                )
                request.range != null -> {
                    val start = request.range.substringAfter("bytes=").substringBefore('-').toLong()
                    val end = request.range.substringAfter('-').toLong()
                    // Headers promise the full window but the connection delivers
                    // only a truncated body and then closes.
                    StubResponse(
                        "206 Partial Content",
                        listOf("Content-Range: bytes $start-$end/${file.size}"),
                        file.copyOfRange(start.toInt(), start.toInt() + 1_000)
                    )
                }
                else -> StubResponse("200 OK", listOf("Content-Length: ${file.size}"), file)
            }
        }.use { server ->
            assertNull(OpenSubtitlesHasher.compute(server.url, emptyMap(), client))
        }
    }

    @Test
    fun `malformed content range header fails the hash`() = runTest {
        val file = testFileBytes()
        StubHttpServer { request ->
            when {
                request.method == "HEAD" -> StubResponse(
                    "200 OK",
                    listOf("Content-Length: ${file.size}", "Accept-Ranges: bytes")
                )
                request.range != null -> StubResponse(
                    "206 Partial Content",
                    // Unsatisfied-range form carries no usable window.
                    listOf("Content-Range: bytes */${file.size}")
                )
                else -> StubResponse("200 OK", listOf("Content-Length: ${file.size}"), file)
            }
        }.use { server ->
            assertNull(OpenSubtitlesHasher.compute(server.url, emptyMap(), client))
        }
    }

    private companion object {
        private const val CHUNK_BYTES = 65536
    }
}
