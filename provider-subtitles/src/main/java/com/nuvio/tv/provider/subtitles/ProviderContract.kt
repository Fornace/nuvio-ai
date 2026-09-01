package com.nuvio.tv.provider.subtitles

/**
 * Wire contract between this provider package and the NuvioTV AI media host.
 *
 * Pure Kotlin on purpose: plain JVM unit tests cannot rely on android.os
 * storage, so every negotiated field lives here and the service mirrors it
 * verbatim into the reply Bundle. [ProviderContractTest] pins all values.
 */
object ProviderContract {

    /** Intent action the host uses to bind any Nuvio provider. */
    const val BIND_ACTION: String = "com.nuvio.tv.provider.BIND"

    /** Intent category declared alongside [BIND_ACTION]. */
    const val BIND_CATEGORY: String = "android.intent.category.DEFAULT"

    /** Message codes accepted on the Messenger channel. */
    const val MSG_NEGOTIATE: Int = 1
    const val MSG_PING: Int = 2

    /** Reply Bundle keys, fixed by the host protocol. */
    const val KEY_PROTOCOL: String = "protocol"
    const val KEY_PROVIDER_ID: String = "providerId"
    const val KEY_PACKAGE_NAME: String = "packageName"
    const val KEY_VERSION_NAME: String = "versionName"
    const val KEY_CAPABILITIES: String = "capabilities"
    const val KEY_ENGINE_STATUS: String = "engineStatus"
    const val KEY_HOST_MIN_VERSION_CODE: String = "hostMinVersionCode"
    const val KEY_PONG: String = "pong"
    const val KEY_ERROR: String = "error"

    /** Negotiated values for this package. */
    const val PROTOCOL_VERSION: Int = 1
    const val PROVIDER_ID: String = "generated-dialogue-subtitles"
    const val ENGINE_STATUS: String = "contract-preview"
    const val HOST_MIN_VERSION_CODE: Int = 1

    /** Mirrors versionName in build.gradle.kts; used when PackageManager fails. */
    const val VERSION_NAME: String = "0.1.0-preview2"

    /** Exactly one capability in this preview: subtitle cue exchange, v1. */
    val CAPABILITIES: List<String> = listOf("SUBTITLE_CUES_V1@1")

    /** Error value returned for any unsupported message code. */
    const val ERROR_UNSUPPORTED: String = "unsupported"

    /** Name of the background thread serving the Messenger handler. */
    const val THREAD_NAME: String = "nuvio-provider-subtitles"

    /** Everything the host learns from a NEGOTIATE reply. */
    data class NegotiationInfo(
        val protocol: Int,
        val providerId: String,
        val packageName: String,
        val versionName: String,
        val capabilities: List<String>,
        val engineStatus: String,
        val hostMinVersionCode: Int,
    )

    fun negotiationInfo(packageName: String, versionName: String): NegotiationInfo =
        NegotiationInfo(
            protocol = PROTOCOL_VERSION,
            providerId = PROVIDER_ID,
            packageName = packageName,
            versionName = versionName,
            capabilities = CAPABILITIES,
            engineStatus = ENGINE_STATUS,
            hostMinVersionCode = HOST_MIN_VERSION_CODE,
        )

    /** Shape of the reply for a given message code; the service maps it to a Bundle. */
    sealed interface Reply {
        data class Negotiate(val info: NegotiationInfo) : Reply
        data class Pong(val pong: Int) : Reply
        data class Error(val error: String) : Reply
    }

    fun replyFor(what: Int, packageName: String, versionName: String): Reply = when (what) {
        MSG_NEGOTIATE -> Reply.Negotiate(negotiationInfo(packageName, versionName))
        MSG_PING -> Reply.Pong(pong = 1)
        else -> Reply.Error(ERROR_UNSUPPORTED)
    }
}
