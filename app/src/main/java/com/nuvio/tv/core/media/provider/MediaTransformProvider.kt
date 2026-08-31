package com.nuvio.tv.core.media.provider

/** Common typed identity and capability declaration implemented by every native provider. */
interface MediaTransformProvider {
    val providerId: MediaTransformProviderId

    /** Capability to provider-contract version. Versions are negotiated exactly, never by range. */
    val capabilities: Map<MediaTransformCapability, Int>
}
