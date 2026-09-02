package com.nuvio.tv.provider.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineAdaptorTest {

    private fun credential(aux: Map<String, String> = emptyMap()) =
        EngineCredential(vendorId = "test", apiKey = "sk_test", auxFields = aux)

    @Test
    fun `registry exposes all three dub adaptors`() {
        assertEquals(
            listOf("qwen-livetranslate-ws", "openai-realtime-translate", "gemini-live-translate"),
            EngineAdaptors.ids(),
        )
    }

    @Test
    fun `qwen substitutes workspace id and model into wss url`() {
        val result = EngineAdaptors.byId("qwen-livetranslate-ws")!!.buildSpec(
            EngineConfig(
                adaptorId = "qwen-livetranslate-ws",
                vendorId = "qwen",
                model = "qwen3.5-livetranslate-flash-realtime",
                apiBase = "wss://{workspaceId}.ap-southeast-1.maas.aliyuncs.com/api-ws/v1/realtime?model={model}",
            ),
            credential(mapOf("workspaceId" to "ws42")),
        )
        val spec = (result as EngineAdaptorResult.Spec).spec
        assertEquals(
            "wss://ws42.ap-southeast-1.maas.aliyuncs.com/api-ws/v1/realtime?model=qwen3.5-livetranslate-flash-realtime",
            spec.url,
        )
        assertEquals("Bearer sk_test", spec.headers["Authorization"])
    }

    @Test
    fun `qwen without workspace id is unsupported`() {
        val result = EngineAdaptors.byId("qwen-livetranslate-ws")!!.buildSpec(
            EngineConfig(
                adaptorId = "qwen-livetranslate-ws",
                vendorId = "qwen",
                model = "m",
                apiBase = "wss://{workspaceId}.example/realtime?model={model}",
            ),
            credential(),
        )
        assertTrue(result is EngineAdaptorResult.Unsupported)
    }

    @Test
    fun `openai substitutes model and sets beta header`() {
        val result = EngineAdaptors.byId("openai-realtime-translate")!!.buildSpec(
            EngineConfig(
                adaptorId = "openai-realtime-translate",
                vendorId = "openai",
                model = "gpt-realtime-translate",
                apiBase = "wss://api.openai.com/v1/realtime?model={model}",
            ),
            credential(),
        )
        val spec = (result as EngineAdaptorResult.Spec).spec
        assertEquals("wss://api.openai.com/v1/realtime?model=gpt-realtime-translate", spec.url)
        assertEquals("realtime=v1", spec.headers["OpenAI-Beta"])
    }

    @Test
    fun `gemini keeps api key out of loggable url`() {
        val result = EngineAdaptors.byId("gemini-live-translate")!!.buildSpec(
            EngineConfig(
                adaptorId = "gemini-live-translate",
                vendorId = "google",
                model = "gemini-3.5-live-translate-preview",
                apiBase = "wss://generativelanguage.googleapis.com/ws/bidi?model={model}",
            ),
            credential(),
        )
        val spec = (result as EngineAdaptorResult.Spec).spec
        assertEquals(
            "wss://generativelanguage.googleapis.com/ws/bidi?model=gemini-3.5-live-translate-preview",
            spec.url,
        )
        assertEquals(mapOf("key" to "sk_test"), spec.sensitiveQueryParameters)
        assertTrue(!spec.url.contains("sk_test"))
    }
}
