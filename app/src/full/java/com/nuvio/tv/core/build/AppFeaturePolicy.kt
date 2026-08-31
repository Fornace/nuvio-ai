package com.nuvio.tv.core.build

object AppFeaturePolicy {
    val pluginsEnabled: Boolean = true
    val inAppUpdatesEnabled: Boolean = true
    val inAppTrailerPlaybackEnabled: Boolean = true
    val externalTrailerPlaybackEnabled: Boolean = true
    val supportNuvioEnabled: Boolean = true
    val trailerPlaybackMode: TrailerPlaybackMode = TrailerPlaybackMode.IN_APP
    val imdbRatingLogoEnabled: Boolean = true

    /**
     * AI media transform providers (generated dialogue subtitles, translated voice) are typed
     * native main-sourceSet components resolved through MediaTransformProviderRegistry with
     * signing-digest identity, exact capability version negotiation and scoped capability
     * grants. They are never QuickJS/DEX plugins and never enter the plugin runtime.
     */
    val mediaTransformProvidersEnabled: Boolean = true
}
