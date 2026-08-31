package com.nuvio.tv.core.build

object AppFeaturePolicy {
    val pluginsEnabled: Boolean = false
    val inAppUpdatesEnabled: Boolean = false
    val inAppTrailerPlaybackEnabled: Boolean = false
    val externalTrailerPlaybackEnabled: Boolean = true
    val supportNuvioEnabled: Boolean = false
    val trailerPlaybackMode: TrailerPlaybackMode = TrailerPlaybackMode.EXTERNAL
    val imdbRatingLogoEnabled: Boolean = false

    /**
     * AI media transform providers (generated dialogue subtitles, translated voice) are typed
     * native main-sourceSet components resolved through MediaTransformProviderRegistry with
     * signing-digest identity, exact capability version negotiation and scoped capability
     * grants. They are never QuickJS/DEX plugins and never enter the plugin runtime.
     */
    val mediaTransformProvidersEnabled: Boolean = true
}
