package com.nuvio.tv.data.mapper

import com.nuvio.tv.data.remote.dto.StreamDto
import com.nuvio.tv.data.remote.dto.SubtitleDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamMapperTest {

    @Test
    fun `stream subtitles are mapped and deduped by url`() {
        val dto = StreamDto(
            name = "4K HDR",
            title = "Big Buck Bunny",
            url = "https://video.example/file.mkv",
            subtitles = listOf(
                SubtitleDto(id = "sub-1", url = "https://subs.example/eng.vtt", lang = "eng"),
                SubtitleDto(id = null, url = "https://subs.example/eng.vtt", lang = "eng"),
                SubtitleDto(id = null, url = "https://subs.example/ger.vtt", lang = "")
            )
        )

        val stream = dto.toDomain(addonName = "TestAddon", addonLogo = "https://logo.example/x.png")

        assertEquals(2, stream.subtitles.size)
        val first = stream.subtitles[0]
        assertEquals("sub-1", first.id)
        assertEquals("https://subs.example/eng.vtt", first.url)
        assertEquals("eng", first.lang)
        assertEquals("TestAddon", first.addonName)
        assertEquals("https://logo.example/x.png", first.addonLogo)
        // Duplicate URL was dropped in favor of the first record.
        val second = stream.subtitles[1]
        assertEquals("https://subs.example/ger.vtt", second.url)
        assertEquals("und", second.lang)
        assertEquals("und-${"https://subs.example/ger.vtt".hashCode()}", second.id)
    }

    @Test
    fun `absent subtitles produce an empty list and other fields stay unchanged`() {
        val dto = StreamDto(
            name = "1080p",
            title = null,
            description = "A description",
            url = "https://video.example/other.mkv",
            ytId = null,
            infoHash = "0123456789abcdef0123456789abcdef01234567",
            fileIdx = 2,
            subtitles = null
        )

        val stream = dto.toDomain(addonName = "OtherAddon", addonLogo = null)

        assertEquals(emptyList<Any>(), stream.subtitles)
        assertEquals("1080p", stream.name)
        assertNull(stream.title)
        assertEquals("A description", stream.description)
        assertEquals("https://video.example/other.mkv", stream.url)
        assertEquals("0123456789abcdef0123456789abcdef01234567", stream.infoHash)
        assertEquals(2, stream.fileIdx)
        assertNull(stream.addonLogo)
        assertEquals("OtherAddon", stream.addonName)

        val emptySubtitlesDto = dto.copy(subtitles = emptyList())
        assertTrue(emptySubtitlesDto.toDomain("OtherAddon", null).subtitles.isEmpty())
    }
}
