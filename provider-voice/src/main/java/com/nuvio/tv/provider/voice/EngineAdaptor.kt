package com.nuvio.tv.provider.voice

/**
 * Engine adaptor contract for the translated voice provider.
 *
 * The host resolves the vendor catalog entry for the selected vendor and sends
 * the resolved configuration plus the credential envelope with each engine
 * request. This APK only maps the adaptor id to a session endpoint, so model
 * swaps and console link changes are registry-side JSON edits. Only a new
 * wire protocol shape needs a provider update.
 */
data class EngineConfig(
    val adaptorId: String,
    val vendorId: String,
    val model: String,
    val apiBase: String,
) {
    init {
        require(adaptorId.isNotBlank()) { "adaptorId must not be blank" }
        require(model.isNotBlank()) { "model must not be blank" }
        require(apiBase.isNotBlank()) { "apiBase must not be blank" }
    }
}

/** Decrypted credential envelope as stored by the host vault. */
data class EngineCredential(
    val vendorId: String,
    val apiKey: String,
    val auxFields: Map<String, String> = emptyMap(),
)

/** Session endpoint description produced by an adaptor. Pure data, no I/O. */
data class EngineSessionSpec(
    val url: String,
    val headers: Map<String, String>,
    /** Authentication query fields the transport must append without logging. */
    val sensitiveQueryParameters: Map<String, String> = emptyMap(),
)

sealed interface EngineAdaptorResult {
    data class Spec(val spec: EngineSessionSpec) : EngineAdaptorResult
    data class Unsupported(val reason: String) : EngineAdaptorResult
}

interface EngineAdaptor {
    val id: String
    fun buildSpec(config: EngineConfig, credential: EngineCredential): EngineAdaptorResult
}

/** Registry of adaptors compiled into this APK. */
object EngineAdaptors {
    private val all: List<EngineAdaptor> =
        listOf(QwenLiveTranslateAdaptor, OpenAiRealtimeTranslateAdaptor, GeminiLiveTranslateAdaptor)

    fun ids(): List<String> = all.map { it.id }

    fun byId(adaptorId: String): EngineAdaptor? = all.firstOrNull { it.id == adaptorId }
}

/** Alibaba Model Studio realtime websocket; workspace id comes from the credential aux fields. */
object QwenLiveTranslateAdaptor : EngineAdaptor {
    override val id = "qwen-livetranslate-ws"

    override fun buildSpec(
        config: EngineConfig,
        credential: EngineCredential,
    ): EngineAdaptorResult {
        val workspaceId = credential.auxFields["workspaceId"]
            ?: return EngineAdaptorResult.Unsupported("qwen-livetranslate-ws without workspaceId")
        val url = config.apiBase
            .replace("{workspaceId}", workspaceId)
            .replace("{model}", config.model)
        return EngineAdaptorResult.Spec(
            EngineSessionSpec(
                url = url,
                headers = mapOf("Authorization" to "Bearer ${credential.apiKey}"),
            ),
        )
    }
}

/** OpenAI dedicated realtime translation session over websocket. */
object OpenAiRealtimeTranslateAdaptor : EngineAdaptor {
    override val id = "openai-realtime-translate"

    override fun buildSpec(
        config: EngineConfig,
        credential: EngineCredential,
    ): EngineAdaptorResult = EngineAdaptorResult.Spec(
        EngineSessionSpec(
            url = config.apiBase.replace("{model}", config.model),
            headers = mapOf(
                "Authorization" to "Bearer ${credential.apiKey}",
                "OpenAI-Beta" to "realtime=v1",
            ),
        ),
    )
}

/** Gemini Live API bidirectional websocket; transport appends the key without logging it. */
object GeminiLiveTranslateAdaptor : EngineAdaptor {
    override val id = "gemini-live-translate"

    override fun buildSpec(
        config: EngineConfig,
        credential: EngineCredential,
    ): EngineAdaptorResult = EngineAdaptorResult.Spec(
        EngineSessionSpec(
            url = config.apiBase.replace("{model}", config.model),
            headers = emptyMap(),
            sensitiveQueryParameters = mapOf("key" to credential.apiKey),
        ),
    )
}
