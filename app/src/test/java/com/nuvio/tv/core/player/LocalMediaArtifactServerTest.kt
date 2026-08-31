package com.nuvio.tv.core.player

import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LocalMediaArtifactServerTest {

    private lateinit var server: LocalMediaArtifactServer
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    @Before
    fun setUp() {
        server = LocalMediaArtifactServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.stop()
        client.connectionPool.evictAll()
    }

    private fun baseUrl(): String = "http://${server.host}:${server.port}"

    private fun artifactBytes(size: Int): ByteArray =
        ByteArray(size) { index -> ((index * 31 + 7) and 0xFF).toByte() }

    @Test
    fun `server address is 127_0_0_1 on an ephemeral port`() {
        assertEquals("127.0.0.1", server.host)
        assertEquals("127.0.0.1", server.hostname)
        assertTrue("ephemeral port must be assigned", server.port > 0)
        assertTrue(server.isAlive)
    }

    @Test
    fun `published url carries a 32 hex character token under the artifact prefix`() {
        val path = server.publish(artifactBytes(8), "audio/mp4")
        assertTrue("path prefix: $path", path.startsWith("/artifact/"))
        val token = path.removePrefix("/artifact/")
        assertEquals(32, token.length)
        assertTrue(token.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `valid token GET returns 200 with exact bytes and content type`() {
        val payload = artifactBytes(2048)
        val path = server.publish(payload, "audio/mp4")

        val request = Request.Builder()
            .url(server.urlFor(path))
            .header("Authorization", "Bearer primary-playback-secret")
            .header("Cookie", "session=primary-playback-secret")
            .build()

        client.newCall(request).execute().use { response ->
            assertTrue("expected 2xx, was ${response.code}", response.isSuccessful)
            assertEquals(200, response.code)
            assertEquals("audio/mp4", response.header("Content-Type"))
            assertEquals(payload.size.toLong(), response.header("Content-Length")?.toLong())
            assertArrayEquals(payload, response.body?.bytes())
            assertEquals("bytes", response.header("Accept-Ranges"))
            // No upstream playback header is ever echoed back.
            assertNotNull(response.header("Content-Type"))
            assertTrue(
                "Authorization must not be echoed",
                response.headers("Authorization").isEmpty()
            )
            assertTrue(
                "Cookie must not be echoed",
                response.headers("Cookie").isEmpty()
            )
        }
    }

    @Test
    fun `file backed artifact serves full body and range slices`() {
        val payload = artifactBytes(300)
        val file = File.createTempFile("artifact", ".m4a")
        try {
            file.writeBytes(payload)
            val path = server.publish(file, "audio/mp4")

            client.newCall(Request.Builder().url(server.urlFor(path)).build()).execute().use { full ->
                assertEquals(200, full.code)
                assertArrayEquals(payload, full.body?.bytes())
            }

            val ranged = Request.Builder()
                .url(server.urlFor(path))
                .header("Range", "bytes=100-199")
                .build()
            client.newCall(ranged).execute().use { partial ->
                assertEquals(206, partial.code)
                assertEquals("bytes 100-199/300", partial.header("Content-Range"))
                assertArrayEquals(payload.copyOfRange(100, 200), partial.body?.bytes())
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun `missing or malformed token returns 404 with empty body`() {
        val path = server.publish(artifactBytes(16), "audio/mp4")
        val token = path.removePrefix("/artifact/")

        val wrongTokenUrl = "${baseUrl()}/artifact/${token.replaceFirstChar { if (it == '0') '1' else '0' }}"

        for (url in listOf(
            "${baseUrl()}/artifact/0000000000000000000000000000dead",
            "${baseUrl()}/artifact/",
            "${baseUrl()}/other/$token",
            wrongTokenUrl
        )) {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                assertEquals("expected 404 for $url", 404, response.code)
                assertEquals(0L, response.header("Content-Length")?.toLong())
                assertEquals(0, response.body?.bytes()?.size)
                assertTrue(response.headers("Authorization").isEmpty())
                assertTrue(response.headers("Cookie").isEmpty())
            }
        }
    }

    @Test
    fun `range request returns 206 with the correct content-range slice`() {
        val payload = artifactBytes(1024)
        val path = server.publish(payload, "audio/mp4")

        val request = Request.Builder()
            .url(server.urlFor(path))
            .header("Range", "bytes=256-511")
            .build()

        client.newCall(request).execute().use { response ->
            assertEquals(206, response.code)
            assertEquals("bytes 256-511/1024", response.header("Content-Range"))
            assertEquals(256L, response.header("Content-Length")?.toLong())
            assertArrayEquals(payload.copyOfRange(256, 512), response.body?.bytes())
        }
    }

    @Test
    fun `open ended and suffix ranges are satisfied`() {
        val payload = artifactBytes(500)
        val path = server.publish(payload, "audio/mp4")

        client.newCall(
            Request.Builder().url(server.urlFor(path)).header("Range", "bytes=450-").build()
        ).execute().use { response ->
            assertEquals(206, response.code)
            assertEquals("bytes 450-499/500", response.header("Content-Range"))
            assertArrayEquals(payload.copyOfRange(450, 500), response.body?.bytes())
        }

        client.newCall(
            Request.Builder().url(server.urlFor(path)).header("Range", "bytes=-100").build()
        ).execute().use { response ->
            assertEquals(206, response.code)
            assertEquals("bytes 400-499/500", response.header("Content-Range"))
            assertArrayEquals(payload.copyOfRange(400, 500), response.body?.bytes())
        }

        // An end beyond the total is clamped to the last byte, per RFC 9110.
        client.newCall(
            Request.Builder().url(server.urlFor(path)).header("Range", "bytes=480-9999").build()
        ).execute().use { response ->
            assertEquals(206, response.code)
            assertEquals("bytes 480-499/500", response.header("Content-Range"))
        }
    }

    @Test
    fun `unsatisfiable range returns 416 with bytes slash total content-range`() {
        val payload = artifactBytes(128)
        val path = server.publish(payload, "audio/mp4")

        for (range in listOf("bytes=128-", "bytes=9999-10000", "bytes=-0")) {
            val request = Request.Builder()
                .url(server.urlFor(path))
                .header("Range", range)
                .build()
            client.newCall(request).execute().use { response ->
                assertEquals("expected 416 for $range", 416, response.code)
                assertEquals("bytes */128", response.header("Content-Range"))
                assertEquals(0, response.body?.bytes()?.size)
            }
        }
    }

    @Test
    fun `revoked token returns 404 immediately`() {
        val payload = artifactBytes(64)
        val path = server.publish(payload, "audio/mp4")
        val token = path.removePrefix("/artifact/")

        client.newCall(Request.Builder().url(server.urlFor(path)).build()).execute().use { response ->
            assertEquals(200, response.code)
            assertArrayEquals(payload, response.body?.bytes())
        }

        server.revoke(token)

        client.newCall(Request.Builder().url(server.urlFor(path)).build()).execute().use { response ->
            assertEquals(404, response.code)
            assertEquals(0, response.body?.bytes()?.size)
        }
    }

    @Test
    fun `revokeAll invalidates every published artifact`() {
        val pathA = server.publish(artifactBytes(16), "audio/mp4")
        val pathB = server.publish(artifactBytes(16), "audio/aac")

        server.revokeAll()

        for (path in listOf(pathA, pathB)) {
            client.newCall(Request.Builder().url(server.urlFor(path)).build()).execute().use { response ->
                assertEquals(404, response.code)
            }
        }
    }

    @Test
    fun `stop closes the socket and invalidates every token`() {
        val path = server.publish(artifactBytes(16), "audio/mp4")
        val url = server.urlFor(path)

        server.stop()

        val refused = runCatching {
            client.newCall(Request.Builder().url(url).build()).execute().use { it.code }
        }.isFailure
        assertTrue("connection to a stopped server must fail", refused)
        val reissued = runCatching { server.publish(artifactBytes(16), "audio/mp4") }
        assertTrue("publish after stop must fail", reissued.isFailure)
    }

    @Test
    fun `restarted instance does not resurrect previously stopped tokens`() {
        val path = server.publish(artifactBytes(16), "audio/mp4")

        server.stop()
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        try {
            client.newCall(
                Request.Builder().url("http://${server.host}:${server.port}$path").build()
            ).execute().use { response ->
                assertEquals(404, response.code)
            }
            assertTrue("publish must succeed again after a real restart", server.port > 0)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `large range responses are served without whole-slice buffering`() {
        val payload = artifactBytes(2 * 1024 * 1024)
        val path = server.publish(payload, "audio/mp4")

        val request = Request.Builder()
            .url(server.urlFor(path))
            .header("Range", "bytes=0-${payload.size - 2}")
            .build()

        client.newCall(request).execute().use { response ->
            assertEquals(206, response.code)
            assertEquals("bytes 0-${payload.size - 2}/${payload.size}", response.header("Content-Range"))
            assertEquals((payload.size - 1).toLong(), response.header("Content-Length")?.toLong())
            assertArrayEquals(payload.copyOfRange(0, payload.size - 1), response.body?.bytes())
        }
    }
}
