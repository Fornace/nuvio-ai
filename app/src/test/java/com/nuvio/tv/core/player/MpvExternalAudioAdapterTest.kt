package com.nuvio.tv.core.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvExternalAudioAdapterTest {

    private class RecordingSink(private var nextResult: Boolean = true) : MpvCommandSink {
        val commands = mutableListOf<List<String>>()

        override fun command(vararg args: String): Boolean {
            commands.add(args.toList())
            return nextResult
        }
    }

    private val loopbackUrl = "http://127.0.0.1:41234/artifact/0123456789abcdef0123456789abcdef"

    @Test
    fun `attach builds exactly audio-add url select title lang`() {
        val sink = RecordingSink()
        val adapter = MpvExternalAudioAdapter(sink)

        val result = adapter.attach(
            url = loopbackUrl,
            title = "Translated voice (Italian)",
            lang = "it"
        )

        assertTrue(result)
        assertEquals(
            listOf(
                listOf("audio-add", loopbackUrl, "select", "Translated voice (Italian)", "it")
            ),
            sink.commands
        )
    }

    @Test
    fun `remove builds audio-remove with the track id`() {
        val sink = RecordingSink()
        val adapter = MpvExternalAudioAdapter(sink)

        val result = adapter.remove(trackId = 3)

        assertTrue(result)
        assertEquals(listOf(listOf("audio-remove", "3")), sink.commands)
    }

    @Test
    fun `reload builds audio-reload with the track id`() {
        val sink = RecordingSink()
        val adapter = MpvExternalAudioAdapter(sink)

        val result = adapter.reload(trackId = 3)

        assertTrue(result)
        assertEquals(listOf(listOf("audio-reload", "3")), sink.commands)
    }

    @Test
    fun `mpv failure flag is surfaced unchanged`() {
        val sink = RecordingSink(nextResult = false)
        val adapter = MpvExternalAudioAdapter(sink)

        assertFalse(adapter.attach(loopbackUrl, "Translated voice (Italian)", "it"))
        assertFalse(adapter.remove(1))
        assertFalse(adapter.reload(1))
        assertEquals(3, sink.commands.size)
    }

    @Test
    fun `sanitizeHeaderValue accepts plain and printable unicode values`() {
        assertEquals("audio/mp4", sanitizeHeaderValue("audio/mp4"))
        assertEquals("it", sanitizeHeaderValue("it"))
        assertEquals(
            "Translated voice (Italiano) — pt-BR",
            sanitizeHeaderValue("Translated voice (Italiano) — pt-BR")
        )
        assertEquals(" Bearer abc.DEF-123_~ ", sanitizeHeaderValue(" Bearer abc.DEF-123_~ "))
    }

    @Test
    fun `sanitizeHeaderValue rejects CR LF NUL DEL and other control characters`() {
        val rejected = listOf(
            "x\r\nHost: evil",
            "a\rb",
            "a\nb",
            "a\u0000b",
            "a\u000Bb",
            "a\u001Fb",
            "a\u007Fb",
            "a\u0085b",
            "a\u009Fb"
        )
        for (value in rejected) {
            try {
                sanitizeHeaderValue(value)
                throw AssertionError("expected IllegalArgumentException for control character in value")
            } catch (expected: IllegalArgumentException) {
                // The message must not contain the raw value (it could hold secrets).
                assertEquals(true, expected.message?.contains("control character") == true)
            }
        }
    }

    @Test
    fun `attach rejects urls that are not credential-free loopback http urls`() {
        val rejected = listOf(
            "https://127.0.0.1:41234/artifact/abc",
            "http://0.0.0.0:41234/artifact/abc",
            "http://localhost:41234/artifact/abc",
            "http://example.com/artifact/abc",
            "http://user:pass@127.0.0.1:41234/artifact/abc",
            "http://127.0.0.1:41234/artifact/abc?token=secret",
            "http://127.0.0.1:41234/artifact/abc#fragment",
            "file:///data/artifact.m4a",
            "not a url"
        )
        for (url in rejected) {
            val sink = RecordingSink()
            val adapter = MpvExternalAudioAdapter(sink)
            try {
                adapter.attach(url, "Translated voice (Italian)", "it")
                throw AssertionError("expected IllegalArgumentException for url: scheme/host withheld")
            } catch (expected: IllegalArgumentException) {
                assertEquals("no command may be issued for a rejected url", emptyList<List<String>>(), sink.commands)
            }
        }
    }

    @Test
    fun `attach validates title and lang before issuing any command`() {
        for (bad in listOf("bad\ntitle", "bad\rtitle", "bad\u0000title")) {
            val sink = RecordingSink()
            val adapter = MpvExternalAudioAdapter(sink)
            try {
                adapter.attach(loopbackUrl, bad, "it")
                throw AssertionError("expected IllegalArgumentException for a control-character title")
            } catch (expected: IllegalArgumentException) {
                assertEquals("no command may be issued for a rejected title", emptyList<List<String>>(), sink.commands)
            }
            try {
                adapter.attach(loopbackUrl, "Translated voice", bad)
                throw AssertionError("expected IllegalArgumentException for a control-character lang")
            } catch (expected: IllegalArgumentException) {
                assertEquals("no command may be issued for a rejected lang", emptyList<List<String>>(), sink.commands)
            }
        }
    }

    @Test
    fun `remove and reload reject non-positive track ids`() {
        val sink = RecordingSink()
        val adapter = MpvExternalAudioAdapter(sink)

        for (trackId in listOf(0, -1)) {
            try {
                adapter.remove(trackId)
                throw AssertionError("expected IllegalArgumentException for remove($trackId)")
            } catch (expected: IllegalArgumentException) {
            }
            try {
                adapter.reload(trackId)
                throw AssertionError("expected IllegalArgumentException for reload($trackId)")
            } catch (expected: IllegalArgumentException) {
            }
        }
        assertEquals(emptyList<List<String>>(), sink.commands)
    }
}
