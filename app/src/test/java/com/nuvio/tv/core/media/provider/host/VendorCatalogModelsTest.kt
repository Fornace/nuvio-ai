package com.nuvio.tv.core.media.provider.host

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VendorCatalogModelsTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun document(vararg entries: VendorCatalogEntryDto) = VendorCatalogDocumentDto(
        schemaVersion = 1,
        updated = "2026-09-01",
        vendors = entries.toList(),
    )

    private fun validDto(
        id: String = "groq",
        capability: String = "SUBTITLE_CUES_V1",
        adaptor: String = "openai-asr",
        apiBase: String = "https://api.groq.com/openai/v1",
        authFields: List<String> = listOf("apiKey"),
        keyUrl: String = "https://console.groq.com/keys",
    ) = VendorCatalogEntryDto(
        id = id,
        name = "Groq",
        capability = capability,
        adaptor = adaptor,
        apiBase = apiBase,
        model = "whisper-large-v3-turbo",
        authFields = authFields,
        keyUrl = keyUrl,
        docsUrl = "https://console.groq.com/docs/speech-to-text",
        pricingHint = "About $0.04 per audio hour",
        notes = null,
    )

    @Test
    fun `valid catalog parses with all entries`() {
        val parsed = document(
            validDto(),
            validDto(id = "cloudflare", adaptor = "cloudflare-workers-ai", apiBase = "https://api.cloudflare.com/client/v4/accounts/{accountId}/ai/run", authFields = listOf("apiKey", "accountId")),
        ).toParsedVendorCatalog()

        assertEquals(2, parsed.vendors.size)
        assertEquals("groq", parsed.vendors[0].id)
        assertEquals(setOf("apiKey", "accountId"), parsed.vendors[1].authFields.toSet())
        assertTrue(parsed.warnings.isEmpty())
    }

    @Test
    fun `unsupported schema version is rejected`() {
        val dto = document().copy(schemaVersion = 2)
        var threw = false
        try {
            dto.toParsedVendorCatalog()
        } catch (_: IllegalArgumentException) {
            threw = true
        }
        assertTrue(threw)
    }

    @Test
    fun `entries with invalid urls or placeholders drop with warnings`() {
        val parsed = document(
            validDto(id = "badurl", keyUrl = "http://insecure.example"),
            validDto(id = "dangling", apiBase = "wss://example.com/v1?ws={workspaceId}&model={model}"),
            validDto(),
        ).toParsedVendorCatalog()

        assertEquals(listOf("groq"), parsed.vendors.map { it.id })
        assertEquals(2, parsed.warnings.size)
    }

    @Test
    fun `authFields must include apiKey`() {
        val parsed = document(validDto(authFields = listOf("accountId"))).toParsedVendorCatalog()
        assertTrue(parsed.vendors.isEmpty())
        assertTrue(parsed.warnings.single().contains("apiKey"))
    }

    @Test
    fun `duplicate vendor id per capability warns`() {
        val parsed = document(validDto(), validDto(id = "groq", capability = "DUB_ARTIFACT_V1"), validDto(id = "groq")).toParsedVendorCatalog()
        assertEquals(2, parsed.vendors.size)
        assertTrue(parsed.warnings.single().contains("duplicate"))
    }

    @Test
    fun `wire json decodes with unknown keys ignored`() {
        val wire = """
            {"schemaVersion":1,"updated":"2026-09-01","futureField":true,"vendors":[
              {"id":"qwen","name":"Alibaba","capability":"DUB_ARTIFACT_V1","adaptor":"qwen-livetranslate-ws",
               "apiBase":"wss://{workspaceId}.example/api-ws/v1?model={model}","model":"qwen3.5-livetranslate-flash-realtime",
               "authFields":["apiKey","workspaceId"],"keyUrl":"https://example.com/key","docsUrl":"https://example.com/docs",
               "pricingHint":"x","notes":"n"}]}
        """.trimIndent()
        val dto = json.decodeFromString<VendorCatalogDocumentDto>(wire)
        val parsed = dto.toParsedVendorCatalog()
        assertEquals(1, parsed.vendors.size)
        assertEquals("qwen", parsed.vendors[0].id)
    }

    @Test
    fun `malformed json surfaces as serialization failure`() {
        var threw = false
        try {
            json.decodeFromString<VendorCatalogDocumentDto>("{nope")
        } catch (_: SerializationException) {
            threw = true
        }
        assertTrue(threw)
    }
}
