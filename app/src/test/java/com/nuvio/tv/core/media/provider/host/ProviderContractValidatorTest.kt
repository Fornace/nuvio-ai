package com.nuvio.tv.core.media.provider.host

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderContractValidatorTest {
    private val signer = "A".repeat(64)
    private val packageName = "com.nuvio.tv.provider.subtitles"
    private val installed = InstalledProviderInfo("0.1.0-preview1", 1, setOf(signer))
    private val entry = ProviderRegistryEntry(
        id = "generated-dialogue-subtitles",
        name = "Generated Dialogue Subtitles",
        capability = "SUBTITLE_CUES_V1",
        status = "preview",
        installable = true,
        description = null,
        version = "0.1.0-preview1",
        apkUrl = "https://nuvio-extensions.fornace.net/artifacts/subtitles.apk",
        apkSha256 = "a".repeat(64),
        minHostAppVersion = 1,
        packageNames = listOf(packageName),
        signingCertSha256 = setOf(signer),
        engineStatus = "contract-preview",
        documentation = null,
        parseWarnings = emptyList(),
    )
    private val service = ProviderServiceDescriptor(
        packageName = packageName,
        className = "$packageName.ProviderContractService",
        exported = true,
        permission = ExternalProviderContract.BIND_PERMISSION,
    )
    private val payload = ProviderNegotiationPayload(
        protocol = 1,
        providerId = entry.id,
        packageName = packageName,
        versionName = entry.version!!,
        capabilities = listOf("SUBTITLE_CUES_V1@1"),
        engineStatus = entry.engineStatus!!,
        hostMinVersionCode = 1,
    )
    private val validator = ProviderContractValidator(hostVersionCode = 1051)

    @Test
    fun `matching registry package signer and reply are trusted`() {
        val result = validator.validate(entry, packageName, installed, service, payload)
        assertTrue(result is ProviderContractResult.Trusted)
        assertEquals(packageName, (result as ProviderContractResult.Trusted).contract.packageName)
    }

    @Test
    fun `wrong installed signer is rejected before binding`() {
        val result = validator.validatePreflight(
            entry,
            packageName,
            installed.copy(signingCertSha256Set = setOf("B".repeat(64))),
        )
        assertEquals(ProviderContractFailure.SIGNER_MISMATCH, result)
    }

    @Test
    fun `untrusted registry release is rejected before binding`() {
        val result = validator.validatePreflight(entry.copy(installable = false), packageName, installed)
        assertEquals(ProviderContractFailure.REGISTRY_RELEASE_UNTRUSTED, result)
    }

    @Test
    fun `service must require host signature permission`() {
        val result = validator.validate(
            entry,
            packageName,
            installed,
            service.copy(permission = null),
            payload,
        )
        assertRejected(ProviderContractFailure.SERVICE_PERMISSION_MISMATCH, result)
    }

    @Test
    fun `provider id and package must match registry target`() {
        assertRejected(
            ProviderContractFailure.PROVIDER_ID_MISMATCH,
            validator.validate(entry, packageName, installed, service, payload.copy(providerId = "other")),
        )
        assertRejected(
            ProviderContractFailure.PACKAGE_NAME_MISMATCH,
            validator.validate(entry, packageName, installed, service, payload.copy(packageName = "other.pkg")),
        )
    }

    @Test
    fun `capability protocol version and engine must match exactly`() {
        assertRejected(
            ProviderContractFailure.CAPABILITY_MISMATCH,
            validator.validate(entry, packageName, installed, service, payload.copy(capabilities = listOf("SUBTITLE_CUES_V1@2"))),
        )
        assertRejected(
            ProviderContractFailure.PROTOCOL_MISMATCH,
            validator.validate(entry, packageName, installed, service, payload.copy(protocol = 2)),
        )
        assertRejected(
            ProviderContractFailure.ENGINE_STATUS_MISMATCH,
            validator.validate(entry, packageName, installed, service, payload.copy(engineStatus = "production")),
        )
    }

    @Test
    fun `host version and package race fail closed`() {
        assertEquals(
            ProviderContractFailure.HOST_VERSION_TOO_OLD,
            ProviderContractValidator(0).validatePreflight(entry, packageName, installed),
        )
        assertRejected(
            ProviderContractFailure.PACKAGE_CHANGED,
            validator.validate(entry, packageName, installed, service, payload, installedAfterReply = null),
        )
    }

    private fun assertRejected(
        expected: ProviderContractFailure,
        result: ProviderContractResult,
    ) = assertEquals(expected, (result as ProviderContractResult.Rejected).reason)
}
