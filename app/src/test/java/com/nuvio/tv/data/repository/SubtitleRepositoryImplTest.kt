package com.nuvio.tv.data.repository

import android.content.Context
import com.nuvio.tv.data.remote.api.AddonApi
import com.nuvio.tv.data.remote.dto.SubtitleItemDto
import com.nuvio.tv.data.remote.dto.SubtitleResponseDto
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.AddonResource
import com.nuvio.tv.domain.repository.SubtitleLookupErrorKind
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import com.squareup.moshi.JsonDataException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class SubtitleRepositoryImplTest {

    private val context = mockk<Context>(relaxed = true)
    private val addonRepository = mockk<AddonRepositoryImpl>()
    private val api = mockk<AddonApi>()

    private fun subtitleAddon(id: String, name: String, baseUrl: String) = Addon(
        id = id,
        name = name,
        displayName = name,
        version = "1.0.0",
        description = null,
        logo = null,
        baseUrl = baseUrl,
        catalogs = emptyList(),
        types = emptyList(),
        resources = listOf(
            AddonResource(name = "subtitles", types = listOf("movie"), idPrefixes = null)
        )
    )

    private fun newRepository(): SubtitleRepositoryImpl =
        SubtitleRepositoryImpl(context, api, addonRepository)

    @Test
    fun `http error and timeout failures are structured while another addon succeeds`() = runTest {
        val good = subtitleAddon("good", "Good Subs", "https://good.example")
        val broken = subtitleAddon("broken", "Broken Subs", "https://broken.example")
        val slow = subtitleAddon("slow", "Slow Subs", "https://slow.example")
        every { addonRepository.getInstalledAddons() } returns flowOf(listOf(good, broken, slow))

        coEvery { api.getSubtitles("https://good.example/subtitles/movie/tt3711878.json") } returns Response.success(
            SubtitleResponseDto(
                subtitles = listOf(
                    SubtitleItemDto(id = "g1", url = "https://subs.example/good-eng.vtt", lang = "eng"),
                    SubtitleItemDto(id = null, url = "https://subs.example/good-deu.vtt", lang = "deu")
                )
            )
        )
        coEvery { api.getSubtitles("https://broken.example/subtitles/movie/tt3711878.json") } returns Response.error(
            500,
            "".toResponseBody("application/json".toMediaType())
        )
        coEvery { api.getSubtitles("https://slow.example/subtitles/movie/tt3711878.json") } throws
            SocketTimeoutException("read timeout")

        val repository = newRepository()

        val detailed = repository.lookupSubtitlesDetailed(type = "movie", id = "tt3711878")

        // Successful records survive alongside the two structured failures.
        assertEquals(
            listOf("g1", "deu-${"https://subs.example/good-deu.vtt".hashCode()}"),
            detailed.subtitles.map { it.id }
        )
        assertEquals(listOf("eng", "deu"), detailed.subtitles.map { it.lang })
        val failuresByName = detailed.failures.associateBy { it.addonName }
        assertEquals(setOf("Broken Subs", "Slow Subs"), failuresByName.keys)

        val httpFailure = failuresByName.getValue("Broken Subs")
        assertEquals(SubtitleLookupErrorKind.HTTP_STATUS, httpFailure.kind)
        assertEquals(500, httpFailure.httpStatus)
        assertEquals("broken", httpFailure.addonId)

        val timeoutFailure = failuresByName.getValue("Slow Subs")
        assertEquals(SubtitleLookupErrorKind.TIMEOUT, timeoutFailure.kind)
        assertEquals(null, timeoutFailure.httpStatus)

        // Legacy path keeps returning only the successes.
        val legacy = repository.getSubtitles(type = "movie", id = "tt3711878")
        assertEquals(detailed.subtitles, legacy)
    }

    @Test
    fun `per addon timeout produces a structured timeout failure`() = runTest {
        val slow = subtitleAddon("slow", "Slow Subs", "https://slow.example")
        every { addonRepository.getInstalledAddons() } returns flowOf(listOf(slow))
        val neverCompletes = CompletableDeferred<Unit>()
        coEvery { api.getSubtitles("https://slow.example/subtitles/movie/tt3711878.json") } coAnswers {
            // Suspends until the per-addon timeout cancels this call.
            neverCompletes.await()
            throw IllegalStateException("unreachable: deferred never completes")
        }

        val repository = newRepository().apply { perAddonTimeoutMs = 50L }

        val detailed = repository.lookupSubtitlesDetailed(type = "movie", id = "tt3711878")

        assertEquals(emptyList<Any>(), detailed.subtitles)
        assertEquals(1, detailed.failures.size)
        assertEquals(SubtitleLookupErrorKind.TIMEOUT, detailed.failures.single().kind)
        assertEquals("Slow Subs", detailed.failures.single().addonName)
    }

    @Test
    fun `malformed payload is classified as a parse failure`() = runTest {
        val addon = subtitleAddon("parse", "Parse Subs", "https://parse.example")
        every { addonRepository.getInstalledAddons() } returns flowOf(listOf(addon))
        coEvery { api.getSubtitles("https://parse.example/subtitles/movie/tt3711878.json") } throws
            JsonDataException("Expected BEGIN_OBJECT but was STRING")

        val detailed = newRepository().lookupSubtitlesDetailed(type = "movie", id = "tt3711878")

        assertEquals(emptyList<Any>(), detailed.subtitles)
        val failure = detailed.failures.single()
        assertEquals(SubtitleLookupErrorKind.PARSE, failure.kind)
        assertEquals("parse", failure.addonId)
    }
}
