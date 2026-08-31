package com.nuvio.tv.core.media.provider

/** Native contracts understood by this host build and their exact wire/API versions. */
enum class MediaTransformCapability(val version: Int) {
    SUBTITLE_CUES_V1(1),
    DUB_ARTIFACT_V1(1)
}
