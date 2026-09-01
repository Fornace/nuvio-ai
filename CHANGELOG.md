# Changelog

Nuvio AI follows [NuvioTV](https://github.com/NuvioMedia/NuvioTV) upstream and adds the AI media
provider platform. Upstream changes are merged regularly and not repeated here.

## 0.8.11-beta + AI providers (unreleased)

First public preview of the AI media provider platform.

### Added

- AI Media Providers screen (Settings, Content discovery): registry discovery, install and update
  of provider packages, per-provider BYOK credential management, and contract verification with
  on-screen result banners.
- Provider host runtime: registry client for `nuvio-extensions.fornace.net/v1/registry.json`,
  artifact downloader with SHA-256 pinning, exact signer-set signature validation, system installer
  bridge, and fail-closed provider contract negotiation over Messenger.
- Credential vault: per-profile encrypted storage keyed by the Android Keystore, cleared on profile
  removal, never exported.
- Generated Dialogue Subtitles provider APK (`provider-subtitles`), preview 0.1.0-preview2.
- Translated Voice Overlay provider APK (`provider-voice`), preview 0.1.0-preview1.
- Public provider registry and install guide at `nuvio-extensions.fornace.net`.
- Research trail: commercial and open model lanes, API probes, and feasibility notes under
  `docs/ai-media-research`.

### Fixed

- Provider negotiation replies now carry the payload Bundle in `Message.data`; the previous
  `Message.obtain(null, what, payload)` overload left the host reading an empty Bundle and
  fail-closing with `PROTOCOL_MISMATCH` (released as subtitles 0.1.0-preview2).
- Provider center install operations reset on terminal installer states, so cards no longer stay
  busy after an install finishes.
- Completion banners render pinned to the bottom of the screen and auto-dismiss; the install
  permission state is re-checked when the screen resumes.
