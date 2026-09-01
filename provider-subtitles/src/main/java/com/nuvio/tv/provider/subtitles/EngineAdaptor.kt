package com.nuvio.tv.provider.subtitles

/**
 * Engine adaptor contract for the subtitles provider.
 *
 * The host resolves the vendor catalog entry for the selected vendor and sends
 * the resolved configuration plus the credential envelope with each engine
 * request. This APK only maps the adaptor id to a request builder, so model
 * swaps, price changes and new console links are registry-side JSON edits and
 * never need a provider update. Only a new wire protocol shape does.
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

/** Concrete request description produced by an adaptor. Pure data, no I/O. */
data class EngineRequestSpec(
    val url: String,
    val method: String,
    val headers: Map<String, String>,
    val modelFormField: String?,
)

sealed interface EngineAdaptorResult {
    data class Spec(val spec: EngineRequestSpec) : EngineAdaptorResult
    data class Unsupported(val adaptorId: String) : EngineAdaptorResult
}

interface EngineAdaptor {
    val id: String
    fun buildSpec(config: EngineConfig, credential: EngineCredential): EngineAdaptorResult
}

/** Registry of adaptors compiled into this APK. */
object EngineAdaptors {
    private val all: List<EngineAdaptor> = listOf(OpenAiAsrAdaptor, CloudflareWorkersAiAdaptor)

    fun ids(): List<String> = all.map { it.id }

    fun byId(adaptorId: String): EngineAdaptor? = all.firstOrNull { it.id == adaptorId }
}

/** OpenAI-compatible audio transcription (Groq and similar). Model goes in the form body. */
object OpenAiAsrAdaptor : EngineAdaptor {
    override val id = "openai-asr"

    override fun buildSpec(
        config: EngineConfig,
        credential: EngineCredential,
    ): EngineAdaptorResult = EngineAdaptorResult.Spec(
        EngineRequestSpec(
            url = config.apiBase.trimEnd('/') + "/audio/transcriptions",
            method = "POST",
            headers = mapOf(
                "Authorization" to "Bearer ${credential.apiKey}",
            ),
            modelFormField = config.model,
        ),
    )
}

/** Cloudflare Workers AI REST run endpoint; URL carries account id and model. */
object CloudflareWorkersAiAdaptor : EngineAdaptor {
    override val id = "cloudflare-workers-ai"

    override fun buildSpec(
        config: EngineConfig,
        credential: EngineCredential,
    ): EngineAdaptorResult {
        val accountId = credential.auxFields["accountId"]
            ?: return EngineAdaptorResult.Unsupported("cloudflare-workers-ai without accountId")
        val url = config.apiBase
            .replace("{accountId}", accountId)
            .trimEnd('/') + "/${config.model}"
        return EngineAdaptorResult.Spec(
            EngineRequestSpec(
                url = url,
                method = "POST",
                headers = mapOf(
                    "Authorization" to "Bearer ${credential.apiKey}",
                ),
                modelFormField = null,
            ),
        )
    }
}
