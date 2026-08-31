package com.nuvio.tv.core.media.provider.host

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderRegistryModelsTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun parse(text: String): ParsedProviderRegistry =
        json.decodeFromString<ProviderRegistryDocumentDto>(text).toParsedRegistry()

    @Test
    fun `parses registry fixture with planned non installable providers`() {
        val registry = parse(PLANNED_REGISTRY_FIXTURE)

        assertEquals(1, registry.schemaVersion)
        assertEquals("2026-08-31T17:50:00Z", registry.generatedAt)
        assertEquals(2, registry.providers.size)
        assertTrue(registry.warnings.isEmpty())

        val subtitles = registry.providers[0]
        assertEquals("generated-dialogue-subtitles", subtitles.id)
        assertEquals("Generated Dialogue Subtitles", subtitles.name)
        assertEquals("SUBTITLE_CUES_V1", subtitles.capability)
        assertEquals("planned", subtitles.status)
        assertFalse(subtitles.installable)
        assertNull(subtitles.version)
        assertNull(subtitles.apkUrl)
        assertNull(subtitles.apkSha256)
        assertEquals(emptyList<String>(), subtitles.packageNames)
        assertEquals(emptySet<String>(), subtitles.signingCertSha256)
        assertEquals("https://github.com/NuvioMedia/NuvioTV", subtitles.documentation)
    }

    @Test
    fun `missing release stays not installable without warnings`() {
        val registry = parse(PLANNED_REGISTRY_FIXTURE)

        val overlay = registry.providers[1]
        assertEquals("translated-voice-overlay", overlay.id)
        assertFalse(overlay.installable)
        assertTrue(overlay.parseWarnings.isEmpty())
        assertNull(overlay.version)
    }

    @Test
    fun `installable release with valid fields is preserved`() {
        val registry = parse(buildRegistryJson(validRelease()))

        val entry = registry.providers.single()
        assertTrue(entry.installable)
        assertTrue(entry.parseWarnings.isEmpty())
        assertEquals("0.1.0-preview1", entry.version)
        assertEquals(
            "https://nuvio-extensions.fornace.net/artifacts/nuvio-subtitles-0.1.0-preview1.apk",
            entry.apkUrl
        )
        assertEquals(SHA_A, entry.apkSha256)
        assertEquals(1, entry.minHostAppVersion)
        assertEquals(listOf("com.nuvio.tv.provider.subtitles"), entry.packageNames)
        assertEquals(setOf(CERT_A, CERT_B), entry.signingCertSha256)
        assertEquals("contract-preview", entry.engineStatus)
    }

    @Test
    fun `installable without release is downgraded with warning`() {
        val registry = parse(
            buildRegistryJson(""" "release": null, "installable": true """.trimIndent())
        )

        val entry = registry.providers.single()
        assertFalse(entry.installable)
        assertEquals(listOf("installable provider has no release"), entry.parseWarnings)
        assertEquals(1, registry.warnings.size)
        assertEquals("nuvio-subtitles", registry.warnings.single().providerId)
    }

    @Test
    fun `short apk sha256 downgrades installable with warning`() {
        val release = validRelease().replace(SHA_A, "abc123")
        val registry = parse(buildRegistryJson(release))

        val entry = registry.providers.single()
        assertFalse(entry.installable)
        assertEquals(1, entry.parseWarnings.size)
        assertTrue("apkSha256" in entry.parseWarnings.single())
        // The malformed digest is still exposed so callers can log diagnostics.
        assertEquals("abc123", entry.apkSha256)
    }

    @Test
    fun `uppercase apk sha256 downgrades installable`() {
        val release = validRelease().replace(SHA_A, SHA_A.uppercase())
        val registry = parse(buildRegistryJson(release))

        assertFalse(registry.providers.single().installable)
        assertTrue(registry.providers.single().parseWarnings.single().contains("apkSha256"))
    }

    @Test
    fun `empty package names or signing certs downgrade installable`() {
        val noPackages = validRelease().replace(
            "\"packageNames\": [\"com.nuvio.tv.provider.subtitles\"]",
            "\"packageNames\": []"
        )
        val noCerts = validRelease().replace(
            "\"signingCertSha256\": [\"$CERT_A\", \"$CERT_B\"]",
            "\"signingCertSha256\": []"
        )

        assertFalse(parse(buildRegistryJson(noPackages)).providers.single().installable)
        assertFalse(parse(buildRegistryJson(noCerts)).providers.single().installable)
        assertTrue(parse(buildRegistryJson(noPackages)).providers.single().parseWarnings.isNotEmpty())
    }

    @Test
    fun `non https apk url downgrades installable`() {
        val release = validRelease().replace("https://", "http://")
        val registry = parse(buildRegistryJson(release))

        assertFalse(registry.providers.single().installable)
        assertTrue(registry.providers.single().parseWarnings.single().contains("HTTPS"))
    }

    @Test
    fun `unknown fields are tolerated`() {
        val registry = parse(PLANNED_REGISTRY_FIXTURE.replace("\"status\": \"planned\"", "\"status\": \"planned\", \"futureStatus\": {\"a\": 1}"))

        assertEquals(2, registry.providers.size)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unsupported schema version is rejected`() {
        parse(PLANNED_REGISTRY_FIXTURE.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2"))
    }

    private fun validRelease(): String = """
        "installable": true,
        "release": {
          "version": "0.1.0-preview1",
          "apkUrl": "https://nuvio-extensions.fornace.net/artifacts/nuvio-subtitles-0.1.0-preview1.apk",
          "apkSha256": "$SHA_A",
          "minHostAppVersion": 1,
          "packageNames": ["com.nuvio.tv.provider.subtitles"],
          "signingCertSha256": ["$CERT_A", "$CERT_B"],
          "engineStatus": "contract-preview"
        }
    """.trimIndent()

    private fun buildRegistryJson(installableFields: String): String = """
        {
          "schemaVersion": 1,
          "providers": [
            {
              "id": "nuvio-subtitles",
              "name": "Nuvio Subtitles",
              "capability": "SUBTITLE_CUES_V1",
              "status": "active",
              "description": "Subtitles",
              "documentation": null,
              $installableFields
            }
          ]
        }
    """.trimIndent()

    companion object {
        private const val SHA_A = "00112233445566778899aabbccddeeff00112233445566778899aabbccddeeff"
        private const val CERT_A = "AABBCCDD00112233445566778899AABBCCDDEEFF00112233445566778899AABB"
        private const val CERT_B = "BBCCDDAA00112233445566778899AABBCCDDEEFF00112233445566778899CCDD"

        private val PLANNED_REGISTRY_FIXTURE = """
            {
              "schemaVersion": 1,
              "generatedAt": "2026-08-31T17:50:00Z",
              "registry": "https://nuvio-extensions.fornace.net/v1/registry.json",
              "providers": [
                {
                  "id": "generated-dialogue-subtitles",
                  "name": "Generated Dialogue Subtitles",
                  "capability": "SUBTITLE_CUES_V1",
                  "status": "planned",
                  "installable": false,
                  "description": "Creates dialogue subtitles from the selected program audio after explicit user consent.",
                  "release": null,
                  "documentation": "https://github.com/NuvioMedia/NuvioTV"
                },
                {
                  "id": "translated-voice-overlay",
                  "name": "Translated Voice Overlay",
                  "capability": "DUB_ARTIFACT_V1",
                  "status": "planned",
                  "installable": false,
                  "description": "Attaches synchronized translated speech while preserving original audio as fallback.",
                  "release": null,
                  "documentation": "https://github.com/NuvioMedia/NuvioTV"
                }
              ]
            }
        """.trimIndent()
    }
}
