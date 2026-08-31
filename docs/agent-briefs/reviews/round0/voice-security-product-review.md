# NuvioTV Translated Voice Overlay — security, cost and TV product review

Reviewed repository: `/Users/ffrappo/works/repos/NuvioTV` (HEAD `6e225905`, post-decision state).
Read-only review: docs (`decision.md`, `translated-voice-overlay-hammersmith.md`, lanes 07/08, model-research and security-ux-feasibility audits) plus direct inspection of provider credential storage and sync, `NetworkModule`, playback networking, logging/Sentry, DEX/JS plugin runtime, flavor policy, audio overlay/settings UX, and the DvMKV extractor. No repository files were changed by this review.

## Summary

The translated-voice program is a **paper architecture sitting on top of a codebase whose credential, transport and telemetry foundations currently violate almost every security invariant the program's own documents declare**. There is no Translated Voice Provider code of any kind in the app today: no provider registry, credential broker, grant store, session coordinator, external-audio descriptor, loopback artifact server, or mpv `audio-add`/`audio-remove`/`audio-reload` adapter (the mpv wrapper exposes only `sub-add`, `loadfile`, `ao-reload`, `aid` and `audio-delay` — `NuvioMpvSurfaceView.kt:68-114,259-265,468`). The only reusable assets are the existing audio overlay geometry and delay controls (`AudioSelectionOverlay.kt:66-262,385-540`), the per-route audio-delay persistence, and the debrid device-authorization QR pattern.

That gap is survivable for an internal pilot; the surrounding code is not. Three findings are worse than the research documents record: (1) the default DI client accepts any certificate and hostname (`NetworkModule.kt:103-116`) and the Trakt client, which carries OAuth tokens, derives from it (`NetworkModule.kt:221-231`); (2) the playback client **automatically retries through a trust-all client on any SSLException** while a network interceptor re-attaches `Authorization` to every request including redirects (`PlayerPlaybackNetworking.kt:24-125`), which converts the fallback into a remotely triggerable interception path; (3) release signing falls back to a committed password (`build.gradle.kts:95,87-93,183-194`). Meanwhile provider credentials (debrid ×3, MDBList, AnimeSkip) are stored in plaintext Preferences DataStore, mirrored into unencrypted `.bak` shadow copies, and pushed/pulled as raw `credential_json` to Supabase/custom-server RPCs (`ProviderCredentialSyncService.kt:150-243`); same-process DEX with an `ignoreAllSSLErrors()` shared client runs in the full flavor; and the telemetry chain (no-op `LogDiagnostics`, raw QR-code/device-code logging, plugin request/response previews, partial Sentry scrubbing against over-promising UI copy) cannot today honor the "no credential canary anywhere" acceptance gate.

On cost: no metering, cap, budget or invoice-reconciliation code exists anywhere; the only measured Qwen evidence shows 1.52–1.57× output-duration expansion, making spend output-duration-sensitive, and the pilot economics rest on an unsupported 150-chars/minute assumption that the program's own audit flags as a several-fold underestimate risk. On product: the "Translated voice overlay" naming discipline, original-audio fallback truth, and protected-source veto are well-specified in the decision and brief but have zero enforcement surface, and the planned TV confirmation flow risks ten-foot density overload unless advanced fields stay in Settings.

Verdicts: **CONDITIONAL** for the internal finalized-artifact pilot (conditions below), **CONDITIONAL** for the internal near-real-time prototype (strictly after pilot gates and measured sync/cost), **NO-GO** for any public release of the feature — and public "AI dubbing" claims remain disallowed by the program's own gates. Internal pilot controls and public gates are distinguished explicitly in the final section.

## Findings

### 1. Default DI OkHttpClient disables all TLS validation

Finding: The unqualified app-wide `OkHttpClient` installs a trust-all `X509TrustManager` and a hostname verifier that always returns true, and this client backs addon traffic, TMDB, MDBList, GitHub updater, playback/auth reports, and the Trakt client that adds OAuth tokens and `trakt-api-key`.

Evidence: `app/src/main/java/com/nuvio/tv/core/di/NetworkModule.kt:103-116` constructs the trust-all manager, `sslSocketFactory` and `hostnameVerifier { _, _ -> true }`; the same builder adds `SentryNetworkBreadcrumbInterceptor` and the logging interceptor (lines 112-144). `provideTraktOkHttpClient` derives from this client via `newBuilder()` and attaches `trakt-api-key` (lines 221-261). Retrofits for TMDB/MDBList/GitHub/aniSkip/ARM/animeSkip/playback reports all take this client (lines 281-605). Lane 07 (`07-byok-security-tv-ux.md`, finding on credential transport) and the security audit (issue 2) identify the static trust-all; it remains unfixed at HEAD.

Impact: Every credential-bearing request through this client (Trakt access/refresh tokens, addon URLs whose query strings commonly embed configuration tokens, MDBList key as query parameter per `TmdbApi.kt:15-69` pattern) is interceptable by any on-path attacker despite `https://`. The decision doc's invariant 3 ("provider networking … cannot inherit Nuvio's current trust-all clients") cannot be satisfied while the app's default client is itself trust-all: a future AI adapter wired through normal DI would inherit it by default.

Fix: Remove the trust-all manager and hostname verifier from `provideOkHttpClient`; give the Trakt client a platform-validated base; add an MITM test (untrusted CA + hostname mismatch must fail) for every credential-bearing client. Any provider-specific client must be separately constructed with platform TLS, never derived from this client. This is a precondition for even an internal BYOK pilot.

Priority: P0

Confidence: high

### 2. Playback networking silently retries through trust-all TLS on handshake failure

Finding: The primary playback client catches any `SSLException` and re-executes the same request on a client that accepts every certificate and hostname; a companion network interceptor re-attaches the saved `Authorization` header to every outgoing request, including cross-host redirects. This is a remotely triggerable downgrade not recorded in the research lanes or audits, which only cite the static trust-all clients.

Evidence: `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerPlaybackNetworking.kt:24-45` defines `trustAllManager`, `playbackHostnameVerifier` and `trustAllPlaybackHttpClient`; lines 66-80 show the primary `playbackHttpClient` interceptor: `catch (e: SSLException) { trustAllPlaybackHttpClient.newCall(request).execute() }`. Lines 84-125 (`createHttpClient`) add the always-on `Authorization` re-attach network interceptor and are used for playback data sources, `OpenSubtitlesHasher.kt:26-48` and subtitle downloads. `app/src/test/java/com/nuvio/tv/ui/screens/player/PlaybackNetworkingSslVerificationTest.kt:20-98` codifies the fallback as intended behavior.

Impact: An active network attacker who forces a certificate-validation failure (present any invalid cert — trivial) causes the app to retry the identical request — with stream `Authorization`/cookie headers and the media URL — through a channel the attacker fully controls. This defeats the primary client's platform TLS by design, exposes debrid/link-resolver credentials, and would equally compromise any future translated-voice artifact fetched over this stack. It also invalidates the "dedicated client with platform TLS" gate if the artifact path is built on `PlayerPlaybackNetworking`.

Fix: Remove the automatic SSLException fallback; self-signed local-server support must be an explicit, per-source, user-visible opt-in (or user-imported CA trust for a pinned local host), never a silent catch-all. Gate translated-voice artifact transport on a client that fails closed under untrusted CA and hostname mismatch. Track this as a new P0 beyond the audits' static list.

Priority: P0

Confidence: high

### 3. Provider credentials are synced as raw JSON to the backend (custodial sync without E2EE), over backends that may be plain HTTP

Finding: `ProviderCredentialSyncService` pushes and pulls complete `credential_json` (raw debrid API keys, MDBList key, AnimeSkip client id) through Supabase RPCs with no client-side encryption, and custom-server discovery accepts HTTP backends while the manifest globally permits cleartext.

Evidence: `app/src/main/java/com/nuvio/tv/core/sync/ProviderCredentialSyncService.kt:150-188` (`pushSnapshot`/`seedSnapshot` call `sync_push_provider_credentials`/`sync_seed_provider_credentials` with `credentialParams` containing `credential_json` per provider, lines 173-186), `:191-243` pull path; `credentialParams` at lines 173-186 shows raw `api_key`/`client_id` values. `app/src/main/java/com/nuvio/tv/data/remote/ServerDiscovery.kt:60-79` accepts non-HTTPS backends; `AndroidManifest.xml:42` sets `usesCleartextTraffic="true"`; `network_security_config.xml:1-9` permits cleartext globally. The lane 07 non-leakage contract (sync invariant) and security audit (custody models) already flag this pattern; it is unchanged.

Impact: A custom-server operator, a compromised backend, or a plaintext-transport configuration obtains every user's debrid and MDBList keys. If AI BYOK keys are ever added to this service — the path of least resistance for a future developer — the "local-only by default, never custodial" invariant fails silently. This is also the direct anti-pattern the brief forbids ("No AI credential sync").

Fix: Keep AI credentials out of this service entirely; sync only credential-ID metadata. For the existing credentials, migrate to the planned Keystore AES-GCM broker records and remove raw `credential_json` from the RPC surface, or wrap in client-held envelope encryption after an explicit opt-in. Require HTTPS for any backend that can receive credentials and drop global cleartext opt-in.

Priority: P0

Confidence: high

### 4. No BYOK custody exists, and the full flavor cannot host a confidential broker today (same-process DEX with an SSL-bypassing shared client)

Finding: There is no credential broker, vault, grant store or `CredentialBroker`-style component anywhere in the app; meanwhile the full flavor loads arbitrary DEX into the app process with app/activity context, and the CloudStream compatibility client explicitly ignores all SSL errors — so the process that would hold BYOK keys is one that executes untrusted same-UID code.

Evidence: Repository search for `CredentialBroker|GrantStore|MediaTransformProviderRegistry|LocalMediaArtifactServer|ExternalAudioDescriptor|DubPlaybackCoordinator|ConsentReceipt|voiceCloneConsent` returns zero app-source hits; the only credential-adjacent classes are `ProfileScopedCredentialStore.kt` (Simkl registration only), `ProviderCredentialSyncService.kt` and `DebridSettingsDataStore.kt` plaintext keys (lines 42-50, 62-89). `app/src/full/java/com/nuvio/tv/core/plugin/cloudstream/ExternalExtensionLoader.kt:15,75-92,186-280,450-454` loads DEX via `DexClassLoader` with `context.classLoader` as parent and passes app/activity context; `app/src/full/java/com/nuvio/tv/core/runtime/PluginRuntimeHooks.kt:52-62` sets `app.baseClient` with `.ignoreAllSSLErrors()`; `PluginRuntime.kt:448-452` injects the compiled TMDB key into every QuickJS runtime and `:506-558` exposes an arbitrary-fetch bridge with caller headers. Lane 07 and the security audit (issue 1, CRITICAL) document this boundary; code confirms it.

Impact: Keystore encryption does not create an in-process confidentiality boundary against DEX that shares the UID, can reflect into app code, and can observe decrypted key material during a first-party call. Building the broker first and "denying DEX" via a returned error code — the MVP control in lane 07 — is insufficient per the security audit: DEX can hook around it. Any pilot with a real key in the full flavor as-shipped would make the central security claim false.

Fix: For any BYOK-bearing build: either run in the playstore flavor (plugin manager is a no-op stub — `app/src/playstore/java/com/nuvio/tv/core/plugin/PluginManager.kt:12-74` returns empty/`UnsupportedOperationException`) or fail BYOK closed in the full flavor whenever DEX extensions are installed/can execute, with an explicit restart/cleanup transition. Implement the broker as profile-local Keystore AES-GCM with AAD bound to install/profile-generation/provider/credential ID, no plaintext getter, no DataStore secret string. Longer-term DEX support requires an isolated-process runtime, which is post-MVP.

Priority: P0

Confidence: high

### 5. Telemetry and logging cannot honor the "no credential canary" gate, and the Sentry UI copy overstates the guarantee

Finding: The redaction helpers are no-ops, raw account QR device codes/nonces/verification URLs are logged, plugin request bodies and 300-character response previews are logged, and Sentry's `beforeSend` only nulls `request`/`user` while breadcrumbs carry raw encoded paths and up to 240 characters of exception messages — yet the settings UI promises tokens, bodies, headers and URL queries are "Not sent".

Evidence: `app/src/main/java/com/nuvio/tv/core/logging/LogDiagnostics.kt:3-25` — `rawForLog`/`urlForLog`/`bodySnippetForLog` return the input unchanged and `diagnosticSummary` concatenates six raw cause messages. `app/src/main/java/com/nuvio/tv/ui/screens/account/AccountViewModel.kt:290-343` logs `nonce=`, `deviceCode=`, `userCode=`, full verification URL via `rawForLog()`. `app/src/full/java/com/nuvio/tv/core/plugin/PluginRuntime.kt:280-300` (plugin console to Logcat), `:506-539` (`Log.d(TAG, "Fetch: $method $url body=${body.take(200)}")` and fetch-bridge error logging), `:582-600` (response `bodyPreview=${responseBody.take(300)}`). `SentryInitializer.kt:73-76` `beforeSend` nulls only `event.request`/`event.user`; `SentryNetworkBreadcrumbInterceptor.kt:48-69` records `url.encodedPath` and `error.message.take(240)`; `SentrySettingsDataStore.kt:30-32` defaults Sentry to enabled; `strings.xml:543-544` states the "Not sent" list. The debug-only Trakt interceptor logs full path+query (`NetworkModule.kt:233-260`).

Impact: A future AI key, provider response, prompt fragment or signed URL that touches any of these paths reaches Logcat or Sentry, contradicting the decision doc's common acceptance gate ("No provider credential … canary in Logcat, Sentry, support exports") and the app's own privacy disclosure. The security audit's kill condition for the beta ("telemetry off or canary-proven") is currently unmeetable.

Fix: For the pilot, disable Sentry for AI-involved builds/paths until a server-side canary test proves redaction. Structurally: make `urlForLog`/`rawForLog` real redactors or delete them, remove raw QR/device-code logging, strip plugin body previews and console passthrough in release, extend `beforeSend` to scrub exception values/breadcrumb messages/extras, and align the Sentry disclosure copy with tested behavior.

Priority: P1

Confidence: high

### 6. Plaintext credential storage with unencrypted shadow backups and deletion gaps

Finding: Debrid (Torbox, Premiumize, Real-Debrid), MDBList and Trakt tokens are stored as plaintext strings in Preferences DataStore; every store is mirrored to an unencrypted `.preferences_pb.bak` shadow copy after each edit; and profile deletion/clearing matches `.preferences_pb` names but not the `.bak` files, so stale secret-bearing copies can survive sign-out.

Evidence: `app/src/main/java/com/nuvio/tv/data/local/DebridSettingsDataStore.kt:42-50,62-89` (three API keys as `stringPreferencesKey` values read directly into settings); `TraktAuthDataStore.kt` and `MDBListSettingsDataStore.kt` per lane 07 (plaintext); the single good baseline is `AndroidSimklAuthStorage.kt` (AES-GCM, Keystore alias, profile-generation invalidation — no AAD). `app/src/main/java/com/nuvio/tv/data/local/ProfileDataStoreFactory.kt:184-219` `writeShadowCopy` copies the plaintext file to `.bak`; `:130-131` `clearProfileScopedData` classification only handles names ending exactly `.preferences_pb`; `ProfileManager.kt:129` deletion matches `_p<id>.preferences_pb`. Lane 07 documents the same gaps; HEAD has not closed them.

Impact: Forensic/root extraction yields two plaintext copies per secret; a deleted or re-created profile can leave or resurrect another profile's credential remnants; and any future AI key stored in a new DataStore would inherit the same shadow-copy behavior by default, violating the backup invariant and the acceptance check requiring profile deletion to remove ciphertext, grants and shadow copies before ID reuse.

Impact extends to the pilot: the broker must own its storage format, not live in a feature DataStore.

Fix: Build the broker-owned encrypted record store before any provider UI; exclude secret-bearing stores from shadow-copy behavior or encrypt the shadow; fix deletion to remove `.bak`; add the canary binary-scan test over DataStore, `.bak`, cache and support exports.

Priority: P1

Confidence: high

### 7. Unauthenticated LAN configuration server is one refactor away from being the key-entry surface

Finding: `AddonConfigServer` (NanoHTTPD) exposes full app state — including addon base URLs with query strings — over unauthenticated plain-HTTP GET routes, and the QR payload is a bare `http://<ip>:<port>` URL; this is the surface a naive "enter your AI key from your phone" feature would be built on.

Evidence: `app/src/main/java/com/nuvio/tv/core/server/AddonConfigServer.kt:34-50,76-91` — `serve()` dispatches `/api/state`, `/api/addons`, `/api/collections`, TMDB/Trakt proxies with no bearer token, session, origin or CSRF check; page state serializes each addon's full `baseUrl` (per lane 07, `AddonManagerViewModel.kt:338-375`, QR is plain HTTP IP/port). The security audit (issue 4) and the brief both forbid reusing these servers for credentials.

Impact: A LAN attacker can read configuration (secret-bearing addon URLs) and flood pending changes today; if extended with a `/api/credentials` route for BYOK, a raw key would be broadcast to the local network in plaintext. Even without that route, copying the pattern for the AI provider setup screen would inherit the flaws.

Fix: Do not add any credential endpoint to NanoHTTPD servers. Use provider device authorization (the Torbox/Premiumize pattern in `DebridSettingsViewModel.kt:251-365` with QR + user code + poll + pre-save validation) or an official HTTPS E2EE phone-link handoff. Keep the LAN permission/Android 17 state machine in mind only if a LAN listener ever ships.

Priority: P1

Confidence: high

### 8. Protected-source veto does not exist, and "it plays" does not mean "it is clear"

Finding: There is no input-mode classifier, no DRM/encryption detection, and no pre-paid-request veto anywhere in the codebase; `PlayerMediaSourceFactory` never sets a `DrmConfiguration`, yet the bundled custom Matroska extractor implements patternless AES-CTR decryption of encrypted MKV samples — so playback success is not proof that a source is unencrypted, and a future ExoPlayer PCM tap could capture decrypted audio from an encrypted source.

Evidence: Search for `DrmConfiguration` in the player/domain layers returns nothing; `PlayerMediaSourceFactory.kt:96-113` builds `MediaItem` with URI/MIME/subtitles/metadata only. `app/src/main/java/com/nuvio/tv/core/player/dvmkv/MatroskaExtractor.java:2045-2126` reads encryption signal bytes/IVs and emits crypto data, `:1161-1171` builds `drmInitData`, `:1649-1653` reads content keys — i.e., the Exo path can decode encrypted MKV internally. No code performs the lane 08 Pilot 0 access preflight (input modes, encrypted-fixture rejection, zero provider bytes).

Impact: Without a veto, the first provider integration that "just sends the stream URL" or taps decoded PCM would send protected payload to a speech provider — the lane 08 kill condition ("any protected fixture sends payload to a speech provider") and the decision doc invariant ("Known encrypted HLS/DASH/DRM inputs cause zero provider calls"). Conversely, universal DRM detection is not required for a clear-VOD pilot; the enforceable boundary is rejecting known-encrypted inputs before any paid call.

Fix: Before any provider call exists, implement the preflight: fingerprint content/audio track, detect encryption (manifest `EXT-X-KEY`/ContentProtection, Matroska `ContentEnc`/track crypto flags, `DrmConfiguration`/DRM init data presence), and hard-veto to "Not available for protected streams" with zero bytes. Add the encrypted-fixture corpus test as a standing public-release kill condition. Never forward license URLs, cookies or playback Authorization beyond the acquisition edge.

Priority: P1

Confidence: high

### 9. No billing caps, metering or invoice reconciliation exists; current cost models are not budgeting-grade

Finding: The repository contains zero cost-control code (no budget, hard cap, per-job cap, metered units, or invoice reconciliation — searches for budget/cap/metering APIs return nothing), while the program's own audits show the economics rest on an unsupported 150-billable-characters-per-minute assumption, omit per-request billing quanta at shard boundaries, and the only measured live-translate evidence shows 1.52–1.57× output-duration expansion that directly scales Qwen-style per-output-token billing.

Evidence: Repository-wide search for `maxRequestCost|monthlyBudget|hardCap|costMicros|provider.*budget|invoice|reconcil` in app source: no hits; the only consent-style dialog is P2P (`strings.xml:1578-1581`). `docs/ai-media-research/audits/model-research-audit.md` issue 5 (Qwen ≈$0.02565–$0.0384/source-min, output-duration-sensitive; ASR list price duration-based) and issue 3 (receipt not a controlled experiment); `security-ux-feasibility-audit.md` issue 6 (`r=150` unsupported, TTFO serial sum invalid, quanta omitted); `decision.md` measured-evidence table (14.96 s / 15.36 s generated for a 9.814 s source).

Impact: A near-real-time prototype with a real key has unbounded spend per session (a 100-minute title at observed expansion is ≈$3.74–$3.84 in model charges alone, before retries, output text and images), with no UI statement of who is billed, no cap enforced before scheduling billable chunks, and no reconciliation — the exact bill-shock risk both audits flag. TV owners (shared living-room device, possibly child profiles) make accidental spend more likely, not less.

Fix: Before the first paid pilot call: a per-profile hard cap evaluated before each billable chunk/shard is scheduled; metering of provider-reported units per request; a cost preview on the TV confirmation naming the billing owner; and the ±10%/95% invoice-reconciliation check as a standing gate. Replace `r=150` with measured per-language-pair character distributions; model output-duration expansion explicitly for token-billed providers; include retry/abandoned-work costs in any "cost per completed playable minute" figure.

Priority: P1

Confidence: high

### 10. Voice-clone consent has no implementation, no schema, and the planned clone-ablation milestone itself needs consent artifacts

Finding: Nothing in the codebase implements consent for voice cloning (no consent receipt, no provider-specific cloning policy, no deletion/revocation flow), while the build brief's Milestone 4 plans a controlled clone ablation against Qwen clone modes and the model audit demands cloning not ship "on documentation claims alone".

Evidence: Search for `voiceCloneConsent|ConsentReceipt|clone` consent surfaces in app source: no hits. `docs/agent-briefs/translated-voice-overlay-hammersmith.md` (credential/network boundary section): "Voice cloning requires an explicit, provider-specific user action and a testable consent/data policy. Begin with licensed stock voices unless Francesco explicitly chooses cloning for the pilot"; Milestone 4 mandates the clone off/once/always × source-ASR ablation. Security audit acceptance check 7 requires recorded explicit consent and provider-specific deletion/revocation. The existing smoke test already confounded clone-once with voice and ASR changes (`model-research-audit.md` issue 3).

Impact: Running the ablation with source-speaker cloning on owned fixtures without a recorded consent artifact and a provider data-policy (training/retention/redistribution) review would violate the program's own acceptance checks and create legal exposure around biometric-style voice data; shipping any clone feature later without consent receipts would be a public-gate failure regardless of quality.

Fix: Default the pilot to licensed stock voices. If the clone ablation proceeds, first record a provider-specific consent artifact (whose voice, what is uploaded, retention/training policy, deletion path) and store consent receipts with policy version. Make "no cloning without recorded explicit consent" a fail-closed check in the provider adapter, not a UI checkbox.

Priority: P1

Confidence: high

### 11. "Translated voice overlay" naming and fallback truth are specified but unenforceable — no feature surface, no state machine, no original-audio fallback

Finding: The decision and brief mandate the public label "Translated voice overlay" (never "dubbing") and require truthful states (Preparing, Generating, Ready, Recovering, Original fallback, Failed) with continuous original-audio availability; no strings, feature flags, overlay states or fallback logic exist in the app, so today nothing can demonstrate or enforce either commitment.

Evidence: `decision.md` television-UX section ("Public copy says Translated voice overlay until objective dubbing quality gates pass. Never hide a gap behind silence") and the brief's Milestone 3/TV-interaction sections define the states and one-line actions; repository searches for `Translated voice overlay|Start translated voice|Original fallback|dubb` in app source return only an unrelated Spanish-language name-cleaning regex (`ExternalExtensionRunner.kt:807`). The only adjacent machinery is the audio track rail and delay controls (`AudioSelectionOverlay.kt:66-262`, delay clamps and ± steppers at `:385-540`) and `AudioDelayMediaSource` (timestamp shifting only — lane 08: "it does not expose, isolate, replace, or remix PCM").

Impact: The naming/truth discipline currently lives only in documents; any interim build (or a well-meaning contributor) could expose the pilot under a "dubbing" label or let the overlay sit silent during gaps, which the program treats as a release-integrity failure. Fallback truth is the product's core safety property — auxiliary failure must never stop video — and it is untested because it is unbuilt.

Fix: Land the label as a localized string constant and a feature flag before any provider code, and make "Original fallback" a real state entered on every auxiliary error with crossfade, alongside explicit Start/Stop/Original actions. Add the brief's acceptance checks (never Ready without coverage; back closes overlay without cancelling; stop is explicit) as tests when the state machine exists.

Priority: P2

Confidence: high

### 12. TV setup density: the specified confirmation risks a ten-foot administration console, and existing "Disconnect" semantics do not transfer to AI keys

Finding: The brief's TV confirmation (provider, account/plan, profile/device scope, model, language, data categories, unit/monthly cost, budgets, consumer grants, sync state) exceeds Nuvio's current focused, QR-handoff settings philosophy, and the existing debrid "Disconnect" only clears the local value — a precedent that would silently misrepresent AI-key disconnection, which must distinguish local deletion from provider revocation.

Evidence: `translated-voice-overlay-hammersmith.md` Settings section and lane 07 §D list the dense field set; `security-ux-feasibility-audit.md` issue 9 calls it "an administration console, not a ten-foot confirmation flow". Current primitives: `DebridSettingsScreen.kt:1360-1382` Disconnect action just calls `onDisconnect()` (local clear; per lane 07, `DebridSettingsScreen.kt:1372-1379`); device-auth QR + large user code + countdown exist (`DebridSettingsViewModel.kt:251-365`, `DebridDeviceCodes` composable); raw-key TV entry dialogs exist for debrid (`strings.xml:1119-1121`) with validate-before-save. The audio overlay's own control rail already juggles delay/amplification/center-mix with careful focus restoration (`AudioSelectionOverlay.kt:96-170,385-540`), showing how dense the playback surface already is.

Impact: A dense confirmation leads to consent-without-comprehension and setup abandonment on TV; copying the debrid Disconnect pattern to an AI key would tell users a key is gone when it remains valid at the provider (spend continues if any other holder uses it), and vice versa. Scope/billing ambiguity on a shared TV is a real-money issue.

Fix: Keep TV confirmation to five items — provider, active profile, what leaves the TV, billing owner, hard cap — with everything else in Settings, ideally completed on phone via device authorization. Implement Disconnect as two explicit actions ("Remove from this TV" vs "Revoke at provider" where documented, with a "provider revocation could not be confirmed" state). Reuse the stable-focus patterns from the audio rail for the future overlay row.

Priority: P2

Confidence: high

### 13. Program-status gap: Phase A/B components and custody model are unbuilt, and the two lanes still disagree on what "broker" means

Finding: None of the shared host components or MPV attachment mechanics exist (registry, grant store, session coordinator, `ExternalAudioDescriptor`, `DubPlaybackCoordinator`, `LocalMediaArtifactServer`, mpv `audio-add`/`audio-remove`/`audio-reload` adapter), and the research lanes still contain two incompatible custody models (device-local vault in lane 07 vs a Nuvio backend broker holding vendor keys in lane 08) that the security audit says must be resolved to one named launch mode.

Evidence: Searches for the component class names and `audio-(add|remove|reload)` across app source return zero hits; the mpv wrapper implements only `sub-add` (`NuvioMpvSurfaceView.kt:468`), `loadfile`, `ao-reload`, `aid` selection and `audio-delay` (`:68-114,259-265`). Lane 07 recommends the app-local Keystore broker ("Ship AI BYOK local-only in v1"); lane 08's architecture matrix prefers a "Nuvio broker" where "vendor keys remain server-side"; `security-ux-feasibility-audit.md` issue 3 (HIGH) requires selecting one mode and naming it. The brief already fixes the pilot order (Phase A artifact attachment on MPV first), which is consistent with the decision doc.

Impact: Custody ambiguity is how "local BYOK" silently becomes custodial (a backend receiving user keys) or how durable background jobs get promised under an architecture that deliberately keeps the key on one TV. Building provider UI before the broker repeats the plaintext-precedent problem in finding 6. The missing mpv adapter means even the internal artifact pilot cannot start until the adapter and its 100-cycle attach/select/seek/remove tests exist.

Fix: Decide and document the single pilot custody model — profile-local BYOK, foreground/session-bound, no secret sync, no plugin access — and name it in UI copy. Then build in the brief's order: registry/grants/vault with fakes, loopback tokenized artifact server, tested mpv audio adapter, before any real provider call. Record the milestone evidence the brief requires (binary versions, toolchain, receipts).

Priority: P1

Confidence: high

### 14. Release signing credentials have a committed fallback password

Finding: The Gradle build falls back to a hardcoded keystore/key password (`"815787"`) and a conventional keystore path (`../nuviotv.jks`) when environment/local properties are absent, putting a usable release-signing password in version control.

Evidence: `app/build.gradle.kts:87-95` — `releaseKeyPasswordValue` and `releaseStorePasswordValue` default to `"815787"` via `localProperties.getProperty(..., "815787")`; the signing config at `:183-194` uses these values with `storeFile = ... ?: file("../nuviotv.jks")` and `keyAlias` defaulting to `"nuviotv"`.

Impact: Anyone who obtains the keystore file (build host, CI cache, backup) can sign releases indistinguishable from official ones; the default also means CI or a fresh checkout silently signs "release" builds with a known-to-the-world key. While not a BYOK path, release-signing compromise would let an attacker ship a build that exfiltrates any future BYOK store, making it part of the same trust boundary.

Fix: Remove the hardcoded fallback; fail the release build when signing material is not provided via environment/CI secrets. Rotate the exposed password (and key, if the keystore may have left controlled machines).

Priority: P2

Confidence: high

## Internal pilot controls vs public gates

The program's documents mix two different kinds of requirements; conflating them produces both over-blocking and under-blocking. This review separates them as follows.

Internal pilot controls (bind the Phase A artifact pilot and Phase B prototype; violations stop the pilot but are not themselves public failures):

- dedicated provider client with platform TLS, MITM-tested; never derived from `NetworkModule` or CloudStream clients;
- profile-local key in Keystore AES-GCM with AAD; no DataStore string, no sync service entry, no `.bak` shadow;
- no DEX/plugin/LAN path to key, grant, PCM or artifact FD; playstore-flavor or DEX-free full-flavor process;
- telemetry off (Sentry disabled for AI-involved builds) until a server-side canary proves redaction;
- owned clear fixtures only; provider never receives playback URLs/headers; loopback tokenized artifact transport;
- explicit start/stop, original audio always selectable, generation-checked callbacks;
- metered spend with a hard cap before each billable chunk; recorded fixture provenance (source hash, payload, SDK lock, request IDs, usage);
- licensed stock voices unless Francesco explicitly records a cloning consent artifact.

Public gates (must pass before any public release of the feature; several are standing kill conditions):

- protected fixtures produce zero provider calls and zero payload bytes (kill condition on any leakage);
- canary secret absent from every store, log, Sentry event, support export and generated artifact;
- separation/remix quality, dialogue bleed, long-form sync/drift and seek-recovery thresholds from the decision doc, measured on the common corpus — until they pass, the public name is "Translated voice overlay" and "dubbing" claims stay disallowed;
- provider bill reconciled within ±10% for ≥95% of jobs; hard cap proven; cancellation copy does not promise refunds;
- fallback truth: UI never claims Ready without playable coverage; original audio continuous through every auxiliary failure;
- trust-all TLS removed from credential-capable paths (findings 1–2) and the raw-credential sync path not extended to AI keys (finding 3);
- billing owner, data-disclosure and consent copy on TV kept to the five-item confirmation (finding 12).

## Release gate table

| Stage | Verdict | Controlling conditions (internal = pilot controls above; public gates additionally apply where noted) |
|---|---|---|
| Artifact pilot (internal MPV completed translated-audio attachment, Phase A) | CONDITIONAL | GO only on owned clear fixtures with the full internal pilot control set: dedicated TLS client, profile-local encrypted key, telemetry off, no extension/LAN access, tested mpv audio-add/remove/reload adapter with 100-cycle attach/select/seek/fallback and leak checks, protected-fixture veto tested before the first paid call. Findings 1, 2, 4, 5, 8 block start until their pilot-level fixes land; findings 9–10 require cap and consent artifacts before any real key is used. |
| Near-real-time prototype (Phase B) | CONDITIONAL | Proceeds only after Phase A gates pass and: independent original-audio acquisition is proven while translated audio is selected; generation/seek-epoch rejection tested (pause/stop/seek/episode-switch/process-death); p50/p95 first-playable and steady-state lag measured from source media time; duration-expansion-aware cost metered per session with the hard cap; three 22-minute episodes plus one 100-minute title evidence recorded. Remains internal, labeled Translated voice overlay, stock voices only, no dubbing claim. Public gates 1–4 must already be demonstrably enforced in the prototype's harness. |
| Public feature (public release of translated voice overlay) | NO-GO | Requires all public gates: protected-source veto with zero-byte encrypted-fixture evidence; separation/remix and bleed thresholds passed (until then the feature may ship at most as an explicitly labeled overlay pilot, which is not this row); billing caps with ±10% invoice reconciliation; fallback-truth state machine; canary-proven telemetry; trust-all clients and raw credential sync eliminated (findings 1–3); BYOK custody with DEX exclusion enforced (finding 4); consent and cap copy on TV (findings 10, 12). Public "AI dubbing" claims remain NO-GO independent of this row until the decision doc's dubbing quality gates pass. |

No patches were produced by this review; every Fix above is a recommendation for the engineering program.
