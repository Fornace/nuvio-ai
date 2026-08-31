package com.nuvio.tv.data.repository

import android.content.Context
import android.util.Log
import com.nuvio.tv.data.remote.api.AddonApi
import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.Subtitle
import com.nuvio.tv.domain.model.enabledAddons
import com.nuvio.tv.domain.repository.SubtitleLookupErrorKind
import com.nuvio.tv.domain.repository.SubtitleLookupFailure
import com.nuvio.tv.domain.repository.SubtitleLookupResult
import com.nuvio.tv.domain.repository.SubtitleRepository
import com.squareup.moshi.JsonDataException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

class SubtitleRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: AddonApi,
    private val addonRepository: AddonRepositoryImpl
) : SubtitleRepository {

    companion object {
        private const val TAG = "SubtitleRepository"
        private const val PER_ADDON_TIMEOUT_MS = 20_000L
    }

    /** Per-addon timeout; overridable from unit tests to keep them fast. */
    internal var perAddonTimeoutMs: Long = PER_ADDON_TIMEOUT_MS

    private sealed class AddonOutcome {
        data class Succeeded(val subtitles: List<Subtitle>) : AddonOutcome()
        data class Failed(
            val kind: SubtitleLookupErrorKind,
            val httpStatus: Int?,
            val message: String?
        ) : AddonOutcome()
    }

    override suspend fun getSubtitles(
        type: String,
        id: String,
        videoId: String?,
        videoHash: String?,
        videoSize: Long?,
        filename: String?,
        onProgress: ((completed: Int, total: Int, addonName: String?) -> Unit)?,
        onSubtitlesEmitted: ((List<Subtitle>) -> Unit)?
    ): List<Subtitle> = lookupSubtitlesDetailed(
        type, id, videoId, videoHash, videoSize, filename, onProgress, onSubtitlesEmitted
    ).subtitles

    override suspend fun lookupSubtitlesDetailed(
        type: String,
        id: String,
        videoId: String?,
        videoHash: String?,
        videoSize: Long?,
        filename: String?,
        onProgress: ((completed: Int, total: Int, addonName: String?) -> Unit)?,
        onSubtitlesEmitted: ((List<Subtitle>) -> Unit)?
    ): SubtitleLookupResult = withContext(Dispatchers.IO) {
        val requestType = canonicalSubtitleType(type)
        val startedAtMs = System.currentTimeMillis()
        Log.d(TAG, "Fetching subtitles for type=$requestType, id=$id, videoId=$videoId")

        val addons = try {
            addonRepository.getInstalledAddons().first().enabledAddons()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get installed addons", e)
            return@withContext SubtitleLookupResult(emptyList(), emptyList())
        }

        val subtitleAddons = addons.filter { addon ->
            addon.resources.any { resource ->
                isSubtitleResource(resource.name) && supportsType(addon, resource, requestType, id)
            }
        }

        Log.d(TAG, "Found ${subtitleAddons.size} subtitle addons: ${subtitleAddons.map { it.name }}")

        if (subtitleAddons.isEmpty()) {
            return@withContext SubtitleLookupResult(emptyList(), emptyList())
        }

        val total = subtitleAddons.size
        val completedCount = AtomicInteger(0)
        onProgress?.invoke(0, total, null)

        val accumulatedSubtitles = java.util.Collections.synchronizedList(mutableListOf<Subtitle>())
        val failures = java.util.Collections.synchronizedList(mutableListOf<SubtitleLookupFailure>())

        supervisorScope {
            subtitleAddons.map { addon ->
                async {
                    val addonStartMs = System.currentTimeMillis()
                    val outcome = try {
                        withTimeoutOrNull(perAddonTimeoutMs) {
                            fetchSubtitlesFromAddon(addon, type, id, videoId, videoHash, videoSize, filename)
                        } ?: AddonOutcome.Failed(
                            kind = SubtitleLookupErrorKind.TIMEOUT,
                            httpStatus = null,
                            message = "timed out after ${perAddonTimeoutMs}ms"
                        )
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        AddonOutcome.Failed(classifyException(e), null, e.message)
                    }
                    onProgress?.invoke(completedCount.incrementAndGet(), total, addon.displayName)
                    when (outcome) {
                        is AddonOutcome.Succeeded -> {
                            if (outcome.subtitles.isNotEmpty()) {
                                val snapshot = synchronized(accumulatedSubtitles) {
                                    accumulatedSubtitles.addAll(outcome.subtitles)
                                    accumulatedSubtitles.toList()
                                }
                                if (onSubtitlesEmitted != null) {
                                    withContext(Dispatchers.Main.immediate) {
                                        onSubtitlesEmitted.invoke(snapshot)
                                    }
                                }
                            }
                            Log.d(
                                TAG,
                                "Subtitle fetch done for addon=${addon.name} count=${outcome.subtitles.size} " +
                                    "in ${System.currentTimeMillis() - addonStartMs}ms"
                            )
                        }
                        is AddonOutcome.Failed -> {
                            failures.add(
                                SubtitleLookupFailure(
                                    addonId = addon.id,
                                    addonName = addon.displayName,
                                    kind = outcome.kind,
                                    httpStatus = outcome.httpStatus,
                                    message = outcome.message
                                )
                            )
                            Log.w(
                                TAG,
                                "Subtitle fetch failed for addon=${addon.name}: kind=${outcome.kind.value} " +
                                    "http=${outcome.httpStatus} message=${outcome.message}"
                            )
                        }
                    }
                }
            }.awaitAll()
        }
        val result = SubtitleLookupResult(
            subtitles = synchronized(accumulatedSubtitles) { accumulatedSubtitles.toList() },
            failures = synchronized(failures) { failures.toList() }
        )
        Log.d(
            TAG,
            "Subtitle fetch completed total=${result.subtitles.size} failures=${result.failures.size} " +
                "fromAddons=${subtitleAddons.size} in ${System.currentTimeMillis() - startedAtMs}ms"
        )
        result
    }

    private fun canonicalSubtitleType(type: String): String {
        return if (type.equals("tv", ignoreCase = true)) "series" else type.lowercase()
    }

    private fun supportsType(addon: Addon, resource: com.nuvio.tv.domain.model.AddonResource, type: String, id: String): Boolean {
        // Check if type is supported (normalizing "tv" and "series" to be equivalent)
        if (resource.types.isNotEmpty()) {
            val reqType = canonicalSubtitleType(type)
            val matchesType = resource.types.any { resType ->
                canonicalSubtitleType(resType) == reqType
            }
            if (!matchesType) return false
        }

        // Check if id prefix is supported (check resource first, then fallback to addon top-level idPrefixes)
        val prefixes = resource.idPrefixes?.takeIf { it.isNotEmpty() }
            ?: addon.idPrefixes.takeIf { it.isNotEmpty() }
        if (prefixes != null && prefixes.isNotEmpty()) {
            return prefixes.any { prefix -> id.startsWith(prefix) }
        }

        return true
    }

    private fun isSubtitleResource(name: String): Boolean {
        return name.equals("subtitles", ignoreCase = true) ||
            name.equals("subtitle", ignoreCase = true)
    }

    private suspend fun fetchSubtitlesFromAddon(
        addon: Addon,
        type: String,
        id: String,
        videoId: String?,
        videoHash: String?,
        videoSize: Long?,
        filename: String?
    ): AddonOutcome {
        val normalizedType = canonicalSubtitleType(type)
        val actualId: String = if (normalizedType == "series" && !videoId.isNullOrEmpty()) {
            videoId
        } else {
            videoId?.takeIf { it.isNotBlank() } ?: id
        }

        val encodedType = encodePathSegment(normalizedType)
        val encodedActualId = encodePathSegment(actualId)

        // Build the subtitle URL with optional extra parameters
        val rawBaseUrl = addon.baseUrl.trimEnd('/')
        val queryStart = rawBaseUrl.indexOf('?')
        val basePath = if (queryStart >= 0) rawBaseUrl.substring(0, queryStart).trimEnd('/') else rawBaseUrl
        val baseQuery = if (queryStart >= 0) rawBaseUrl.substring(queryStart) else ""
        val extraParams = buildExtraParams(videoHash, videoSize, filename)
        val subtitleUrl = if (extraParams.isNotEmpty()) {
            "$basePath/subtitles/$encodedType/$encodedActualId/$extraParams.json$baseQuery"
        } else {
            "$basePath/subtitles/$encodedType/$encodedActualId.json$baseQuery"
        }

        Log.d(TAG, "Fetching subtitles from ${addon.name}: $subtitleUrl")

        return try {
            val response = api.getSubtitles(subtitleUrl)
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed to fetch subtitles from ${addon.name}: code=${response.code()}")
                AddonOutcome.Failed(SubtitleLookupErrorKind.HTTP_STATUS, response.code(), response.message())
            } else {
                val body = response.body()
                    ?: return AddonOutcome.Failed(SubtitleLookupErrorKind.PARSE, null, "Empty response body")
                val subtitles = body.subtitles?.mapNotNull { dto ->
                    val url = dto.url?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val lang = dto.lang?.takeIf { it.isNotBlank() } ?: dto.language?.takeIf { it.isNotBlank() } ?: "und"
                    val subId = dto.id?.takeIf { it.isNotBlank() } ?: "$lang-${url.hashCode()}"
                    Subtitle(
                        id = subId,
                        url = url,
                        lang = lang,
                        addonName = addon.displayName,
                        addonLogo = addon.logo
                    )
                } ?: emptyList()

                Log.d(TAG, "Got ${subtitles.size} subtitles from ${addon.name}")
                AddonOutcome.Succeeded(subtitles)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Exception fetching subtitles from ${addon.name}", e)
            AddonOutcome.Failed(classifyException(e), null, e.message)
        }
    }

    private fun classifyException(e: Exception): SubtitleLookupErrorKind = when {
        e is JsonDataException -> SubtitleLookupErrorKind.PARSE
        e is SocketTimeoutException -> SubtitleLookupErrorKind.TIMEOUT
        e is InterruptedIOException && e.message?.contains("timeout", ignoreCase = true) == true ->
            SubtitleLookupErrorKind.TIMEOUT
        else -> SubtitleLookupErrorKind.NETWORK
    }

    private fun buildExtraParams(
        videoHash: String?,
        videoSize: Long?,
        filename: String?
    ): String {
        val params = mutableListOf<String>()

        videoHash?.let { params.add("videoHash=$it") }
        videoSize?.let { params.add("videoSize=$it") }
        filename?.let {
            params.add("filename=${encodePathSegment(it)}")
        }

        return if (params.isNotEmpty()) {
            params.joinToString("&")
        } else {
            ""
        }
    }

    private fun encodePathSegment(value: String): String {
        return java.net.URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}
