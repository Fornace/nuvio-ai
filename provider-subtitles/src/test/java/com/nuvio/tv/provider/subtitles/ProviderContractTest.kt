package com.nuvio.tv.provider.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the negotiation payload that the service mirrors into
 * the reply Bundle (plain unit tests have no functional android.os.Bundle,
 * so the field set is pinned here and the Bundle assembly is mechanical).
 */
class ProviderContractTest {

    private val packageName = "com.nuvio.tv.provider.subtitles"
    private val versionName = "0.1.0-preview2"

    @Test
    fun negotiationBuilderProducesExactContractFields() {
        val info = ProviderContract.negotiationInfo(packageName, versionName)
        assertEquals(1, info.protocol)
        assertEquals("generated-dialogue-subtitles", info.providerId)
        assertEquals(packageName, info.packageName)
        assertEquals(versionName, info.versionName)
        assertEquals(listOf("SUBTITLE_CUES_V1@1"), info.capabilities)
        assertEquals("contract-preview", info.engineStatus)
        assertEquals(1, info.hostMinVersionCode)
    }

    @Test
    fun negotiationKeysMatchHostWireNames() {
        assertEquals("protocol", ProviderContract.KEY_PROTOCOL)
        assertEquals("providerId", ProviderContract.KEY_PROVIDER_ID)
        assertEquals("packageName", ProviderContract.KEY_PACKAGE_NAME)
        assertEquals("versionName", ProviderContract.KEY_VERSION_NAME)
        assertEquals("capabilities", ProviderContract.KEY_CAPABILITIES)
        assertEquals("engineStatus", ProviderContract.KEY_ENGINE_STATUS)
        assertEquals("hostMinVersionCode", ProviderContract.KEY_HOST_MIN_VERSION_CODE)
        assertEquals("pong", ProviderContract.KEY_PONG)
        assertEquals("error", ProviderContract.KEY_ERROR)
    }

    @Test
    fun bindFilterMatchesHostContract() {
        assertEquals("com.nuvio.tv.provider.BIND", ProviderContract.BIND_ACTION)
        assertEquals("android.intent.category.DEFAULT", ProviderContract.BIND_CATEGORY)
        assertEquals(1, ProviderContract.MSG_NEGOTIATE)
        assertEquals(2, ProviderContract.MSG_PING)
    }

    @Test
    fun negotiateMessageMapsToNegotiationReply() {
        val reply = ProviderContract.replyFor(
            ProviderContract.MSG_NEGOTIATE, packageName, versionName,
        )
        assertTrue(reply is ProviderContract.Reply.Negotiate)
        val info = (reply as ProviderContract.Reply.Negotiate).info
        assertEquals(1, info.protocol)
        assertEquals("generated-dialogue-subtitles", info.providerId)
        assertEquals(packageName, info.packageName)
        assertEquals(versionName, info.versionName)
        assertEquals(listOf("SUBTITLE_CUES_V1@1"), info.capabilities)
        assertEquals("contract-preview", info.engineStatus)
        assertEquals(1, info.hostMinVersionCode)
    }

    @Test
    fun pingMessageMapsToPongOne() {
        val reply = ProviderContract.replyFor(ProviderContract.MSG_PING, packageName, versionName)
        assertTrue(reply is ProviderContract.Reply.Pong)
        assertEquals(1, (reply as ProviderContract.Reply.Pong).pong)
    }

    @Test
    fun unknownMessageCodesMapToUnsupportedError() {
        val unknowns = intArrayOf(0, -1, 3, 99, Int.MIN_VALUE, Int.MAX_VALUE)
        for (what in unknowns) {
            val reply = ProviderContract.replyFor(what, packageName, versionName)
            assertTrue("what=$what should map to an error reply", reply is ProviderContract.Reply.Error)
            assertEquals("unsupported", (reply as ProviderContract.Reply.Error).error)
        }
    }

    @Test
    fun fallbackVersionMatchesPreviewVersion() {
        assertEquals("0.1.0-preview2", ProviderContract.VERSION_NAME)
    }
}
