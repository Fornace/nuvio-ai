package com.nuvio.tv.core.media.provider.host

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

sealed interface ProviderRegistryResult {
    data class Success(val registry: ParsedProviderRegistry) : ProviderRegistryResult
    data class NetworkError(val causeType: String) : ProviderRegistryResult
    data class HttpStatusError(val statusCode: Int, val responseBodySize: Long?) : ProviderRegistryResult
    data class ParseError(val message: String) : ProviderRegistryResult
}

/** Creates a client using OkHttp's platform trust manager and hostname verifier. */
object ProviderRegistryHttpClientFactory {
    fun create(): OkHttpClient = OkHttpClient.Builder().build()
}

open class ProviderRegistryClient(
    private val httpClient: OkHttpClient = ProviderRegistryHttpClientFactory.create(),
    private val registryUrl: String = OFFICIAL_PROVIDER_REGISTRY_URL,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    open suspend fun fetch(): ProviderRegistryResult = withContext(Dispatchers.IO) {
        val request = try {
            Request.Builder().url(registryUrl).get().build()
        } catch (error: IllegalArgumentException) {
            return@withContext ProviderRegistryResult.ParseError("Invalid registry URL")
        }

        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext ProviderRegistryResult.HttpStatusError(
                        statusCode = response.code,
                        responseBodySize = response.body?.contentLength()?.takeIf { it >= 0 }
                    )
                }
                val body = response.body?.string()
                    ?: return@withContext ProviderRegistryResult.ParseError("Registry response has no body")
                try {
                    val dto = json.decodeFromString<ProviderRegistryDocumentDto>(body)
                    ProviderRegistryResult.Success(dto.toParsedRegistry())
                } catch (error: SerializationException) {
                    ProviderRegistryResult.ParseError(error.safeParseMessage())
                } catch (error: IllegalArgumentException) {
                    ProviderRegistryResult.ParseError(error.safeParseMessage())
                }
            }
        } catch (error: IOException) {
            ProviderRegistryResult.NetworkError(error.javaClass.simpleName)
        }
    }

    private fun Throwable.safeParseMessage(): String =
        message?.lineSequence()?.firstOrNull()?.take(160) ?: "Invalid registry response"

    companion object {
        const val OFFICIAL_PROVIDER_REGISTRY_URL =
            "https://nuvio-extensions.fornace.net/v1/registry.json"
    }
}
