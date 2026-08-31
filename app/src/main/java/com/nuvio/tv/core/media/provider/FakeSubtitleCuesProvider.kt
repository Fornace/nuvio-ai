package com.nuvio.tv.core.media.provider

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Deterministic byte-count-driven provider for host integration and JVM tests. */
class FakeSubtitleCuesProvider(
    override val providerId: MediaTransformProviderId,
    private val configuration: Configuration,
    private val dispatchers: MediaProviderDispatchers
) : SubtitleCuesProvider {
    data class Configuration(
        val bytesPerCue: Long,
        val inputLengthBytes: Long,
        val cueTexts: List<String> = emptyList()
    ) {
        init {
            require(bytesPerCue > 0) { "bytesPerCue must be positive" }
            require(inputLengthBytes > 0) { "inputLengthBytes must be positive" }
        }
    }

    override val capabilities: Map<MediaTransformCapability, Int> = mapOf(
        MediaTransformCapability.SUBTITLE_CUES_V1 to MediaTransformCapability.SUBTITLE_CUES_V1.version
    )

    @Volatile
    override var state: SubtitleCuesProviderState = SubtitleCuesProviderState.IDLE
        private set

    private val mutex = Mutex()
    private var format: NormalizedPcmFormat? = null
    private var identity: MediaTransformSessionIdentity? = null
    private var callback: SubtitleCuesCallback? = null
    private var consumedBytes = 0L
    private var epochStartByte = 0L
    private var cueStartByte = 0L
    private var nextCueBoundary = configuration.bytesPerCue.coerceAtMost(configuration.inputLengthBytes)
    private var epochMediaStartUs = 0L
    private var epoch = 0L
    private var revision = 0L

    override suspend fun start(
        format: NormalizedPcmFormat,
        sessionIdentity: MediaTransformSessionIdentity,
        callback: SubtitleCuesCallback
    ) = onProcessingDispatcher {
        mutex.withLock {
            check(state != SubtitleCuesProviderState.ACTIVE && state != SubtitleCuesProviderState.PAUSED) {
                "A subtitle cue session is already running"
            }
            this.format = format
            identity = sessionIdentity
            this.callback = callback
            consumedBytes = 0
            epochStartByte = 0
            cueStartByte = 0
            nextCueBoundary = configuration.bytesPerCue.coerceAtMost(configuration.inputLengthBytes)
            epochMediaStartUs = 0
            epoch = sessionIdentity.initialEpoch
            revision = 0
            state = SubtitleCuesProviderState.ACTIVE
        }
    }

    override suspend fun submitPcm(pcm: ByteArray): Unit = onProcessingDispatcher {
        var cues: List<SubtitleCue> = emptyList()
        var completion: Pair<SubtitleCuesCallback, MediaTransformSessionIdentity>? = null
        mutex.withLock {
            if (state != SubtitleCuesProviderState.ACTIVE || pcm.isEmpty()) return@withLock
            val remaining = configuration.inputLengthBytes - consumedBytes
            consumedBytes += pcm.size.toLong().coerceAtMost(remaining)
            val emitted = mutableListOf<SubtitleCue>()
            while (consumedBytes >= nextCueBoundary) {
                emitted += createCue(nextCueBoundary)
                cueStartByte = nextCueBoundary
                if (nextCueBoundary == configuration.inputLengthBytes) break
                nextCueBoundary = (nextCueBoundary + configuration.bytesPerCue)
                    .coerceAtMost(configuration.inputLengthBytes)
            }
            cues = emitted
            if (consumedBytes == configuration.inputLengthBytes) {
                state = SubtitleCuesProviderState.COMPLETED
                completion = callback?.let { current -> current to checkNotNull(identity) }
            }
        }
        val currentCallback = callback
        cues.forEach { currentCallback?.onCue(it) }
        completion?.let { (target, session) -> target.onCompleted(session.generation, epoch) }
    }

    override suspend fun pause() = onProcessingDispatcher {
        mutex.withLock {
            if (state == SubtitleCuesProviderState.ACTIVE) state = SubtitleCuesProviderState.PAUSED
        }
    }

    override suspend fun resume() = onProcessingDispatcher {
        mutex.withLock {
            if (state == SubtitleCuesProviderState.PAUSED) state = SubtitleCuesProviderState.ACTIVE
        }
    }

    override suspend fun discontinuity(newEpoch: Long, mediaTimeUs: Long) = onProcessingDispatcher {
        mutex.withLock {
            check(state == SubtitleCuesProviderState.ACTIVE || state == SubtitleCuesProviderState.PAUSED) {
                "No running subtitle cue session"
            }
            require(newEpoch > epoch) { "A discontinuity must advance the epoch" }
            require(mediaTimeUs >= 0) { "Discontinuity media time must not be negative" }
            epoch = newEpoch
            epochStartByte = consumedBytes
            cueStartByte = consumedBytes
            nextCueBoundary = (consumedBytes + configuration.bytesPerCue)
                .coerceAtMost(configuration.inputLengthBytes)
            epochMediaStartUs = mediaTimeUs
        }
    }

    override suspend fun cancel(): Unit = onProcessingDispatcher {
        var cancellation: Pair<SubtitleCuesCallback, MediaTransformSessionIdentity>? = null
        mutex.withLock {
            if (state == SubtitleCuesProviderState.ACTIVE || state == SubtitleCuesProviderState.PAUSED) {
                state = SubtitleCuesProviderState.CANCELLED
                cancellation = callback?.let { current -> current to checkNotNull(identity) }
            }
        }
        cancellation?.let { (target, session) -> target.onCancelled(session.generation, epoch) }
    }

    private fun createCue(endByte: Long): SubtitleCue {
        val currentIdentity = checkNotNull(identity)
        val currentFormat = checkNotNull(format)
        revision++
        val textIndex = (revision - 1).toInt()
        return SubtitleCue(
            generation = currentIdentity.generation,
            epoch = epoch,
            revision = revision,
            startMediaTimeUs = mediaTimeUs(cueStartByte, currentFormat),
            endMediaTimeUs = mediaTimeUs(endByte, currentFormat),
            text = configuration.cueTexts.getOrNull(textIndex) ?: "Cue $revision",
            final = true
        )
    }

    private fun mediaTimeUs(bytePosition: Long, pcmFormat: NormalizedPcmFormat): Long =
        epochMediaStartUs +
            ((bytePosition - epochStartByte).coerceAtLeast(0) * MICROS_PER_SECOND) /
            pcmFormat.bytesPerSecond

    private suspend fun <T> onProcessingDispatcher(block: suspend () -> T): T =
        withContext(dispatchers.processing) { block() }

    private companion object {
        const val MICROS_PER_SECOND = 1_000_000L
    }
}
