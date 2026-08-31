package com.nuvio.tv.core.media.provider

enum class NormalizedPcmEncoding(val bytesPerSample: Int) {
    PCM_SIGNED_16_BIT_LITTLE_ENDIAN(2)
}

data class NormalizedPcmFormat(
    val sampleRateHz: Int,
    val channelCount: Int,
    val encoding: NormalizedPcmEncoding = NormalizedPcmEncoding.PCM_SIGNED_16_BIT_LITTLE_ENDIAN
) {
    init {
        require(sampleRateHz > 0) { "Sample rate must be positive" }
        require(channelCount > 0) { "Channel count must be positive" }
    }

    val bytesPerSecond: Long
        get() = sampleRateHz.toLong() * channelCount * encoding.bytesPerSample
}

data class MediaTransformSessionIdentity(
    val sessionId: String,
    val profileGeneration: Long,
    val episodeContentId: String,
    val audioTrackFingerprint: String?,
    val generation: Long,
    val initialEpoch: Long = 0
) {
    init {
        require(sessionId.isNotBlank()) { "Session id must not be blank" }
        require(profileGeneration >= 0) { "Profile generation must not be negative" }
        require(episodeContentId.isNotBlank()) { "Episode/content id must not be blank" }
        require(audioTrackFingerprint == null || audioTrackFingerprint.isNotBlank()) {
            "Audio track fingerprint must be absent or non-blank"
        }
        require(generation >= 0) { "Generation must not be negative" }
        require(initialEpoch >= 0) { "Epoch must not be negative" }
    }
}

data class SubtitleCue(
    val generation: Long,
    val epoch: Long,
    val revision: Long,
    val startMediaTimeUs: Long,
    val endMediaTimeUs: Long,
    val text: String,
    val final: Boolean
) {
    init {
        require(revision > 0) { "Cue revision must be positive" }
        require(startMediaTimeUs >= 0) { "Cue start must not be negative" }
        require(endMediaTimeUs >= startMediaTimeUs) { "Cue end must not precede its start" }
    }
}

interface SubtitleCuesCallback {
    fun onCue(cue: SubtitleCue)
    fun onCompleted(generation: Long, epoch: Long) = Unit
    fun onCancelled(generation: Long, epoch: Long) = Unit
}

enum class SubtitleCuesProviderState {
    IDLE,
    ACTIVE,
    PAUSED,
    COMPLETED,
    CANCELLED
}

/** Typed native contract for [MediaTransformCapability.SUBTITLE_CUES_V1]. */
interface SubtitleCuesProvider : MediaTransformProvider {
    val state: SubtitleCuesProviderState

    suspend fun start(
        format: NormalizedPcmFormat,
        sessionIdentity: MediaTransformSessionIdentity,
        callback: SubtitleCuesCallback
    )

    suspend fun submitPcm(pcm: ByteArray)
    suspend fun pause()
    suspend fun resume()
    suspend fun discontinuity(newEpoch: Long, mediaTimeUs: Long = 0L)
    suspend fun cancel()
}
