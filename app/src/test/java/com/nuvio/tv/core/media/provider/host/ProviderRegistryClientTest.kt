package com.nuvio.tv.core.media.provider.host

import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import javax.net.ssl.SSLSocketFactory

class ProviderRegistryClientTest {
    private lateinit var stub: StubRegistryHttp

    private fun client(statusCode: Int = 200, body: String = REGISTRY_FIXTURE, error: IOException? = null): ProviderRegistryClient {
        stub = StubRegistryHttp(statusCode, body, error)
        return ProviderRegistryClient(
            httpClient = OkHttpClient.Builder().addInterceptor(stub).build(),
            registryUrl = REGISTRY_URL
        )
    }

    @Test
    fun `successful response parses registry entries`() = runTest {
        val registryClient = client()

        val result = registryClient.fetch()

        assertTrue(result is ProviderRegistryResult.Success)
        val registry = (result as ProviderRegistryResult.Success).registry
        assertEquals(1, registry.schemaVersion)
        assertEquals(1, registry.providers.size)
        assertEquals("generated-dialogue-subtitles", registry.providers.single().id)
        assertTrue(registry.warnings.isEmpty())
        assertEquals("/v1/registry.json", stub.lastUrl?.encodedPath)
    }

    @Test
    fun `vendor catalog fetch parses from sibling url`() = runTest {
        val registryClient = client(body = VENDORS_FIXTURE)

        val result = registryClient.fetchVendorCatalog()

        assertTrue(result is VendorCatalogResult.Success)
        val catalog = (result as VendorCatalogResult.Success).catalog
        assertEquals(1, catalog.vendors.size)
        assertEquals("groq", catalog.vendors.single().id)
        assertEquals("/v1/vendors.json", stub.lastUrl?.encodedPath)
    }

    @Test
    fun `vendor catalog http error maps to HttpStatusError`() = runTest {
        val registryClient = client(statusCode = 404, body = "missing")

        val result = registryClient.fetchVendorCatalog()

        assertTrue(result is VendorCatalogResult.HttpStatusError)
        assertEquals(404, (result as VendorCatalogResult.HttpStatusError).statusCode)
    }

    @Test
    fun `http error status maps to HttpStatusError with status only`() = runTest {
        val registryClient = client(statusCode = 500, body = "internal registry error")

        val result = registryClient.fetch()

        assertTrue(result is ProviderRegistryResult.HttpStatusError)
        val error = result as ProviderRegistryResult.HttpStatusError
        assertEquals(500, error.statusCode)
        // The failure surface exposes only the size of the body, never its text.
        assertEquals("internal registry error".length.toLong(), error.responseBodySize)
    }

    @Test
    fun `malformed json maps to ParseError`() = runTest {
        val registryClient = client(body = "{\"schemaVersion\": 1, ")

        val result = registryClient.fetch()

        assertTrue(result is ProviderRegistryResult.ParseError)
    }

    @Test
    fun `unsupported schema version maps to ParseError`() = runTest {
        val registryClient = client(body = "{\"schemaVersion\": 9, \"providers\": []}")

        val result = registryClient.fetch()

        assertTrue(result is ProviderRegistryResult.ParseError)
        assertTrue((result as ProviderRegistryResult.ParseError).message.contains("9"))
    }

    @Test
    fun `connection failure maps to NetworkError with exception type only`() = runTest {
        val registryClient = client(error = IOException("connection reset"))

        val result = registryClient.fetch()

        assertTrue(result is ProviderRegistryResult.NetworkError)
        assertEquals("IOException", (result as ProviderRegistryResult.NetworkError).causeType)
    }

    @Test
    fun `default factory builds a client with platform TLS`() {
        val httpClient = ProviderRegistryHttpClientFactory.create()

        // Platform defaults: no custom trust store or verifier was installed.
        assertEquals(SSLSocketFactory.getDefault()::class.java, httpClient.sslSocketFactory::class.java)
        assertEquals(OkHttpClient().hostnameVerifier, httpClient.hostnameVerifier)
    }

    private class StubRegistryHttp(
        private val statusCode: Int,
        private val body: String,
        private val error: IOException?
    ) : Interceptor {
        var lastUrl: HttpUrl? = null
            private set

        override fun intercept(chain: Interceptor.Chain): Response {
            error?.let { throw it }
            lastUrl = chain.request().url
            return Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(statusCode)
                .message("stub")
                .body(body.toResponseBody("application/json".toMediaType()))
                .build()
        }
    }

    companion object {
        private val VENDORS_FIXTURE = """
        {"schemaVersion":1,"updated":"2026-09-01","vendors":[
          {"id":"groq","name":"Groq","capability":"SUBTITLE_CUES_V1","adaptor":"openai-asr",
           "apiBase":"https://api.groq.com/openai/v1","model":"whisper-large-v3-turbo",
           "authFields":["apiKey"],"keyUrl":"https://console.groq.com/keys",
           "docsUrl":"https://console.groq.com/docs/speech-to-text","pricingHint":"x"}]}
    """.trimIndent()

private const val REGISTRY_URL = "https://registry.example.test/v1/registry.json"

        private val REGISTRY_FIXTURE = """
            {
              "schemaVersion": 1,
              "generatedAt": "2026-08-31T17:50:00Z",
              "registry": "https://registry.example.test/v1/registry.json",
              "providers": [
                {
                  "id": "generated-dialogue-subtitles",
                  "name": "Generated Dialogue Subtitles",
                  "capability": "SUBTITLE_CUES_V1",
                  "status": "planned",
                  "installable": false,
                  "description": "Creates dialogue subtitles from the selected program audio.",
                  "release": null,
                  "documentation": "https://github.com/NuvioMedia/NuvioTV"
                }
              ]
            }
        """.trimIndent()
    }
}
