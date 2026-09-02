<div align="center">

  <img src="assets/brand/app_logo_wordmark.png" alt="Nuvio AI" width="300" />

  <p>
    NuvioTV with AI media providers: generated dialogue subtitles and a translated voice overlay,
    running on the TV you already own. Bring your own sources, bring your own keys.
  </p>

  [Android TV developer preview](https://github.com/Fornace/nuvio-ai/releases/tag/ai-dev-2026.09.03) · [Provider registry](https://nuvio-extensions.fornace.net) · [Install guide](https://nuvio-extensions.fornace.net/install.html) · [Upstream project](https://github.com/NuvioMedia/NuvioTV)

</div>

## What this adds on top of NuvioTV

Nuvio AI is a fork of [NuvioTV](https://github.com/NuvioMedia/NuvioTV) with an AI media provider
platform built in:

- **AI Media Providers in-app.** Settings, Content discovery, AI Media Providers: browse the
  provider registry, install updates, and manage everything from the couch with the remote.
- **Generated Dialogue Subtitles (provider, preview).** Creates dialogue subtitles for titles that
  ship none, via your own subtitle API account.
- **Translated Voice Overlay (provider, preview).** Adds a translated dubbing track on top of
  playback, via your own dubbing API account.
- **BYOK.** Every provider credential is yours: you paste it, the vault encrypts it per profile
  with an Android Keystore key. No keys leave the device in plaintext and none are baked into the
  app.
- **Signature-verified provider installs.** Provider APKs are downloaded from the registry, checked
  against the pinned SHA-256 and an exact signer set, then installed through the system installer.
  The host re-verifies identity and engine contract on every negotiation, fail-closed.

Providers are separate thin APKs, not plugins running inside the app runtime, so an update never
touches the host process.

## Status

Preview software. Both providers are labeled preview and signed with the project development
certificate, not a store release certificate. The provider APIs are external paid services: you
need your own account and key for each provider you enable.

## Install

Install the host app first, then the providers from inside the app:

1. Download the arm64 host APK from the [Nuvio AI Android TV developer preview](https://github.com/Fornace/nuvio-ai/releases/tag/ai-dev-2026.09.03) and install it first.
2. Open Settings, Content discovery, AI Media Providers.
3. Allow installs from unknown sources for Nuvio when prompted.
4. Install each provider; download, hash, and signature are verified automatically.
5. Add your API key on each provider card and run Verify.

The full walkthrough with troubleshooting lives at
[nuvio-extensions.fornace.net/install.html](https://nuvio-extensions.fornace.net/install.html).

## Apple development

The AI provider host currently ships on Android TV. Native iOS and macOS integration is tracked in
[Fornace/nuvio-mobile#1](https://github.com/Fornace/nuvio-mobile/issues/1). The existing iOS client
can run on Apple Silicon as Designed for iPad/iPhone, but Android provider APKs do not run on Apple
platforms; Apple adaptors will ship inside the signed host, followed by a native macOS target.

## Build from source

```bash
git clone https://github.com/Fornace/nuvio-ai.git
cd nuvio-ai
./gradlew :app:assembleFullDebug
```

Kotlin, Jetpack Compose, TV Material 3, and Media3. Development requires Android Studio, a JDK,
and the Android SDK. Provider APKs build with `./gradlew :provider-subtitles:assembleDebug` and
`./gradlew :provider-voice:assembleDebug`.

## Credits and license

Fork of [NuvioMedia/NuvioTV](https://github.com/NuvioMedia/NuvioTV), published under
[GNU General Public License v3.0](./LICENSE). AI provider research notes live in
[docs/ai-media-research](./docs/ai-media-research).
