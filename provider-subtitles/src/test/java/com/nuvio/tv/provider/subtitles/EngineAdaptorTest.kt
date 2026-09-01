package com.nuvio.tv.provider.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineAdaptorTest {

    private fun config(adaptorId: String, apiBase: String, model: String = "whisper-large-v3-turbo") =
        EngineConfig(
            adaptorId = adaptorId,
            vendorId = "test",
            model = model,
            apiBase = apiBase,
        )

    @Test
    fun `registry exposes both subtitle adaptors`() {
        assertEquals(listOf("openai-asr", "cloudflare-workers-ai"), EngineAdaptors.ids())
    }

    @Test
    fun `openai-asr posts to transcriptions with model in form body`() {
        val result = EngineAdaptors.byId("openai-asr")!!.buildSpec(
            config("openai-asr", "https://api.groq.com/openai/v1"),
            EngineCredential(vendorId = "groq", apiKey = "gsk_test"),
        )
        val spec = (result as EngineAdaptorResult.Spec).spec
        assertEquals("https://api.groq.com/openai/v1/audio/transcriptions", spec.url)
        assertEquals("POST", spec.method)
        assertEquals("Bearer gsk_test", spec.headers["Authorization"])
        assertEquals("whisper-large-v3-turbo", spec.modelFormField)
    }

    @Test
    fun `cloudflare builds run url from account id and model`() {
        val result = EngineAdaptors.byId("cloudflare-workers-ai")!!.buildSpec(
            config(
                "cloudflare-workers-ai",
                "https://api.cloudflare.com/client/v4/accounts/{accountId}/ai/run",
                model = "@cf/openai/whisper-large-v3-turbo",
            ),
            EngineCredential(
                vendorId = "cloudflare",
                apiKey = "cf_token",
                auxFields = mapOf("accountId" to "abc123"),
            ),
        )
        val spec = (result as EngineAdaptorResult.Spec).spec
        assertEquals(
            "https://api.cloudflare.com/client/v4/accounts/abc123/ai/run/@cf/openai/whisper-large-v3-turbo",
            spec.url,
        )
        assertNull(spec.modelFormField)
    }

    @Test
    fun `cloudflare without account id is unsupported`() {
        val result = EngineAdaptors.byId("cloudflare-workers-ai")!!.buildSpec(
            config("cloudflare-workers-ai", "https://api.cloudflare.com/client/v4/accounts/{accountId}/ai/run"),
            EngineCredential(vendorId = "cloudflare", apiKey = "cf_token"),
        )
        assertTrue(result is EngineAdaptorResult.Unsupported)
    }

    @Test
    fun `unknown adaptor id resolves to null`() {
        assertNull(EngineAdaptors.byId("does-not-exist"))
    }
}
