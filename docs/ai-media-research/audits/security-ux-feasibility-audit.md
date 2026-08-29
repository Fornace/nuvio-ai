# Security, UX, and Feasibility Audit

## Severity: CRITICAL

## Issues Found

### 1. Same-process DEX makes an in-process credential broker non-confidential , CRITICAL
**Location**: `docs/ai-media-research/lanes/07-byok-security-tv-ux.md:79-100,224,416`

**Problem**: Lane 07 correctly identifies CloudStream DEX as arbitrary same-UID code, but its proposed MVP control,returning `UNSUPPORTED_UNTRUSTED_RUNTIME` from the broker,is insufficient. DEX is loaded with the app classloader parent and an app/activity context (`ExternalExtensionLoader.kt:236-327`); it can read app-private state, reflect into app code, retain threads/hooks, or observe a first-party request after the broker decrypts a key. Keystore encryption and operation-level authorization do not create a boundary inside that process.

`GrantHandle` is also described as “non-secret” (`07:173`). If possession authorizes `execute`, it is a bearer capability and must be treated as sensitive unless the broker independently authenticates and binds the caller. A copied string handle must not work from another plugin/runtime.

**Impact**: A malicious or compromised DEX can steal a BYOK credential or use it outside the intended grant despite correct encryption at rest. Shipping app-managed BYOK in the current full-flavor process would make the central security claim false.

**Fix**:
- Public MVP: expose BYOK only where no external DEX can execute in the credential-holding process. For the full flavor, either disable DEX before broker initialization and require a process restart, or keep BYOK unavailable.
- Do not expose any extension credential capability in MVP. Restricted QuickJS operations can be evaluated later because QuickJS has a mediated bridge; DEX cannot.
- Future DEX support requires a separately isolated execution process plus broker-mediated networking. That is speculative post-MVP infrastructure, not a launch prerequisite if DEX integration is simply excluded.
- Bind any future capability to runtime instance, profile generation, consumer identity, operation, destination and expiry; never log or persist the capability string.

### 2. Credential transport is presently vulnerable to MITM and telemetry disclosure , CRITICAL
**Location**: `docs/ai-media-research/lanes/07-byok-security-tv-ux.md:64-88,154-168,324-343,409,417`

**Problem**: The common OkHttp client accepts every certificate and hostname (`NetworkModule.kt:103-116`). The CloudStream client separately calls `ignoreAllSSLErrors()` (`PluginRuntimeHooks.kt:51-62`). At the same time, safe-log helpers return raw input and exception messages (`LogDiagnostics.kt:3-25`), account QR code/nonces are logged raw (`AccountViewModel.kt:290-304,338-343,400`), and Sentry removes only `request` and `user` while retaining exception values, breadcrumb paths and error messages (`SentryInitializer.kt:73-76`; `SentryNetworkBreadcrumbInterceptor.kt:48-69`). Sentry is enabled by default (`SentrySettingsDataStore.kt:25-27`), while the UI asserts these values are not sent (`strings.xml:543-544`).

Lane 07 overreaches, however, by making removal of every compatibility TLS bypass and prohibition of all secret-shaped addon URLs a BYOK prerequisite. Existing configurable addon query URLs are an ecosystem compatibility issue. The actual MVP invariant is narrower: no credential-capable client may bypass platform trust, and no AI key may enter a URL or untrusted runtime.

**Impact**: A network attacker can intercept a BYOK request if an adapter inherits the common client. Keys, signed URLs, QR capabilities, prompts or provider responses can also leave through Logcat/Sentry, contradicting the disclosure shown to users.

**Fix**:
- Give the selected AI adapter a dedicated production client using platform certificate and hostname validation; verify with an untrusted-CA MITM test. Never derive it from the current common client.
- Until a tested sink-wide redactor exists, turn Sentry off by default for the beta and emit only enumerated safe reason codes from AI paths. Remove raw QR/code logging and release plugin body/response logging.
- Change the Sentry disclosure now or prove it with a server-side stored-event canary test. A compile-time logging lint and typed `Safe*` API are valuable hardening, but not required if telemetry is disabled for MVP.
- Preserve legacy addon compatibility, but redact query/userinfo/path secrets in logs, support exports and LAN state. Do not attempt to identify and ban every generic `key=` parameter as a release blocker.

### 3. “Broker” conflates two incompatible custody models , HIGH
**Location**: `docs/ai-media-research/lanes/07-byok-security-tv-ux.md:92-115,169-227,410-411`; `docs/ai-media-research/lanes/08-feasibility-economics.md:70-76,117-122,404-413`

**Problem**: Lane 07 recommends a device-local credential vault whose key never syncs. Lane 08 recommends a Nuvio media/job broker where vendor keys remain server-side. These are not one architecture:

1. **Local BYOK**: the TV stores the user's key and calls the provider through a native adapter.
2. **Managed Nuvio mode**: Nuvio owns provider credentials; the TV receives a short-lived job token.
3. **Custodial BYOK**: Nuvio uploads/stores the user's provider key so a backend can run jobs.

The third model is implicitly suggested by parts of the research but is neither designed nor needed for MVP. Calling all three “brokered” obscures who can decrypt the key, who pays, and whether jobs can continue after the TV exits.

**Impact**: Product can accidentally promise “local BYOK” while sending a user's key to Nuvio, or promise durable background jobs with an architecture that deliberately keeps the key only on a TV. Consent, cost ownership and breach scope would all be wrong.

**Fix**: Select one launch mode and name it in UI and protocol. The least speculative public caption beta is native **profile-local BYOK**, one first-party adapter, foreground/session-bound processing, no secret sync and no plugin access. A managed backend can be a separate optional product later. Do not build custodial BYOK or E2EE recovery until there is an explicit requirement and threat model.

### 4. The proposed QR/LAN design contains good controls, but LAN is unnecessary MVP attack surface , HIGH
**Location**: `docs/ai-media-research/lanes/07-byok-security-tv-ux.md:52-63,251-278,372-387,413-414,418`; `docs/ai-media-research/lanes/06-subtitle-integration-ux.md:324-333`

**Problem**: Current NanoHTTPD servers are plain HTTP and unauthenticated, and expose addon/repository state (`AddonConfigServer.kt:34-91`; `AddonManagerViewModel.kt:338-375`; `PluginViewModel.kt:287-314`). TV confirmation limits mutation but not reads, observation or request flooding. They must not be extended for credentials.

The E2EE LAN fallback, confirmation phrases, Origin/CSRF handling and Android 17 LAN permission state machine are conditional designs, not prerequisites for a release that has no credential LAN endpoint. Android 17/API 37 is also not Nuvio's current target.

**Impact**: Reusing the current QR server can expose raw keys to any local observer or state to any LAN client. Building a hardened offline protocol now adds crypto, lifecycle and platform-permission risk without proving user demand.

**Fix**:
- Prefer provider device authorization or one-use provider credentials.
- If raw-key phone entry is in the public MVP, use an official HTTPS page with browser-to-TV E2EE and a ciphertext-only relay, or keep a password-typed on-TV fallback. No third-party scripts, analytics or localStorage on the entry page.
- Do not ship an offline LAN key-import endpoint in MVP. API 37 permission work becomes required only when Nuvio targets 37 and actually starts a LAN listener.
- QR/manual sessions must be one-use, short-lived and attempt-limited. The TV must confirm provider and active profile before save; the phrase is useful human verification, not a substitute for cryptographic session binding.

### 5. Profile/admin semantics in Lane 07 exceed Nuvio's current model , HIGH
**Location**: `docs/ai-media-research/lanes/07-byok-security-tv-ux.md:151,225,282-320,403,415`

**Problem**: `UserProfile` has an ID, presentation fields and source-sharing flags, but no child, adult, owner or admin role (`UserProfile.kt:3-15`). Profile PINs exist, but a PIN protects profile entry; it does not establish a household administrator. Lane 07's child grants, adult/admin confirmation, household budgets and device-wide sublimits therefore depend on a role model that does not exist.

The real profile hazards are ID reuse, asynchronous work crossing a profile switch, `.preferences_pb.bak` surviving deletion, and `usesPrimaryAddons`/`usesPrimaryPlugins` silently redirecting source storage (`ProfileManager.kt:102-130`; `ProfileDataStoreFactory.kt:87-132,184-219`; `AddonPreferences.kt:30-45`; `PluginDataStore.kt:36-55`).

**Impact**: “Adult approved” would be security theater. A stale credential/grant can instead attach to a newly reused profile ID or remain in a shadow backup after profile deletion.

**Fix**:
- MVP scope is active profile only. Defer device/household scope, child policies and shared budgets until roles exist.
- Add an immutable profile generation/UUID to AAD and every in-flight operation; numeric profile ID alone is insufficient.
- Never inherit grants from primary addon/plugin sharing.
- Register the broker in profile/sign-out cleanup; delete ciphertext, grants, caches and backup remnants synchronously before the profile ID can be reused.
- Omit key reveal/export from MVP. “Delete from this TV” must invalidate every local grant; provider-side revocation is required only where the selected provider documents it, and UI must distinguish local deletion from provider revocation.

### 6. Cost arithmetic is conditional, but the character and latency models are unsafe for budgeting , MEDIUM
**Location**: `docs/ai-media-research/lanes/08-feasibility-economics.md:91-99,132-173,206-260`

**Problem**: The transfer calculations and displayed example arithmetic are internally consistent. The modeling assumption `r = 150 billable characters per media minute`, however, is unsupported and appears to risk confusing conversational words/minute with characters/minute. It materially suppresses MT/TTS estimates. The TTFO equations also add extraction, upload and inference serially even though the proposed shard pipeline should overlap them; they are not a valid prediction of either a pipelined critical path or a non-streaming batch.

The cost equations omit per-request billing quanta at shard boundaries. A provider that rounds each retry/shard separately can charge more than `D × p`, even when total successful audio duration is unchanged.

**Impact**: Product may advertise a cap or choose a provider from a several-fold underestimate. TTFO architecture rankings can also be wrong before any device/provider measurement.

**Fix**:
- Replace `r` with measured source and translated character distributions from the pilot corpus; report p50/p95 by language pair.
- For ASR billed per audio time, use `C_ASR = (p_asr/60) × Σ ceil(d_i/q)×q`, including every failed/retried billable request and provider-specific quantum `q`.
- Meter MT/TTS from provider-reported source/target characters per request, not a media-duration proxy.
- Treat TTFO as a measured stage timeline. If modeling it, use the pipeline's critical path/overlap, queueing and first-final-cue policy rather than summing all stages blindly.
- Keep model-only examples clearly separate from all-in cost. Release pricing must use cost per **completed playable minute**, including retries, abandoned work, storage/egress and payment/support costs.

### 7. Durable infrastructure is conditional, not a generated-caption MVP blocker , MEDIUM
**Location**: `docs/ai-media-research/lanes/08-feasibility-economics.md:70-76,264-299,330-334,404-412`; `docs/ai-media-research/lanes/06-subtitle-integration-ux.md:295-310,407-416`

**Problem**: Lane 08 requires both capabilities to use a durable job API because player coroutines are cancelled. That is necessary only if product promises whole-title completion, resume after app exit, cross-device reuse, or paid work continuing in background. Lane 06's progressive caption MVP can honestly be playback-session-bound: stop on exit, retain finalized cues, and discard provisional work.

**Impact**: Treating durability as mandatory can turn a focused caption beta into a backend/foreground-service project. Conversely, silently continuing paid work after exit would surprise users and weaken cancellation/cost guarantees.

**Fix**:
- Caption MVP lifecycle: explicit start, generation ID per source/audio/profile, bounded queue, stop upload on cancel/exit, ignore late callbacks, and optionally cache finalized cues only.
- Show that leaving playback stops generation and may leave incomplete captions. No foreground service is needed if no work continues.
- Require durable job/shard manifests, notification, retention and idempotent cancellation only when “continue after exit” or whole-title voice output is added.
- Player exit must never silently destroy a job the UI explicitly committed as durable; the two lifecycle modes must have different states and copy.

### 8. DRM is a narrow technical product boundary, not a reason to block clear-VOD captions , HIGH
**Location**: `docs/ai-media-research/lanes/08-feasibility-economics.md:38-43,301-330,340-351`; `docs/ai-media-research/lanes/05-player-audio-integration.md:83-91,159`; `docs/ai-media-research/lanes/06-subtitle-integration-ux.md:427`

**Problem**: The reports correctly reject screen/audio capture as a hidden universal fallback and show that Nuvio does not configure Media3 DRM. The Lane 06 open question adds an unspecific protected-content policy blocker, while Lane 08 can be read as requiring universal DRM detection. Neither is needed to ship a clear-source feature.

No preflight can prove every arbitrary source is non-protected from URL shape alone. The enforceable boundary is that known encrypted manifests/tracks are rejected and provider upload starts only after the trusted acquisition path has selected and extracted supported clear audio.

**Impact**: A permissive path can send protected payload or license-bearing headers to a speech vendor. An overbroad interpretation can also stall a clear-VOD caption pilot on DRM infrastructure Nuvio does not currently use.

**Fix**:
- Product copy: “Not available for protected streams”; do not imply playback success proves processability.
- Reject known encrypted HLS/DASH/Media3 DRM state before creating a paid job; never forward license URLs, cookies or playback Authorization.
- Test a representative encrypted fixture corpus with zero provider requests/uploads. Do not claim universal detection beyond the supported input matrix.
- DRM, live, external-player generation and capture fallbacks remain out of scope. Clear seekable VOD is not blocked by them.

### 9. The proposed TV confirmation is too dense for Nuvio's current interaction philosophy , MEDIUM
**Location**: `README.md:3-8`; `docs/ai-media-research/lanes/07-byok-security-tv-ux.md:101-115,251-320`; `docs/ai-media-research/lanes/06-subtitle-integration-ux.md:224-292`

**Problem**: Nuvio presents itself as a free, open-source app that turns user-provided sources into a library with subtitles and continuity. Its current TV UI uses focused rails, normal track overlays and QR handoff. Lane 06's stable **Generate subtitles** card fits that philosophy. Lane 07 instead asks one final TV screen to show account/plan, profile/device scope, model, language, data categories, unit/monthly cost, budgets, consumer grants and sync state. That is an administration console, not a ten-foot confirmation flow.

Lane 05's MPV-only finalized-audio path is a useful engineering spike, not a public “dubbing” release: it lacks engine failover, partial generation, external-player behavior and the media-production quality boundary. Likewise, Lane 03's one short successful provider clip is evidence for a bake-off candidate, not a product selection.

**Impact**: Users will abandon setup, consent without comprehension, or lose focus during playback. Calling a generic ducked overlay “dubbing” would create a quality expectation the pipeline cannot meet.

**Fix**:
- Put provider connection and advanced controls under Settings, preferably completed on phone. TV setup confirms only provider, active profile, what leaves the TV, who is billed and the hard cap.
- In playback, use the existing subtitle overlay: **Generate dialogue captions (beta)** → target language/cost summary → **Start**. Never auto-capture because subtitles are missing.
- Keep one stable focused card with preparing/generating/paused/failed/complete state. Back closes the overlay without cancelling; Stop is explicit; cue updates never move focus.
- No reveal UI, plugin terminology, device scope, model matrix or grant editor in MVP.
- A forced ExoPlayer switch is acceptable in a labeled beta only with explicit confirmation and preserved playback position. MPV external audio remains an internal spike.
- Reserve **translated voice overlay** for a later limited pilot. Do not market “dubbing” until separation/remix, bleed, sync, seek and long-form quality gates pass.

## Release-Stage Decision

| Stage/capability | Decision | Practical boundary |
|---|---|---|
| Internal generated-caption prototype | **GO** | Owned/test clear VOD, one native provider adapter, dedicated secure client, feature flag, telemetry off, no DEX/plugin/LAN path. |
| Public generated-dialogue-caption beta | **CONDITIONAL GO** | Native in both intended distribution flavors; active-profile local BYOK; explicit start; foreground/session-bound; clear seekable VOD; ExoPlayer limitation disclosed; acceptance checks below pass. |
| Raw-key phone setup | **CONDITIONAL** | Provider device auth first. Otherwise official HTTPS E2EE handoff. Do not reuse NanoHTTPD; omit phone setup if this transport is not ready. |
| Extension-facing BYOK | **NO-GO** | No DEX grants; no selected stream URL/headers/PCM to current JS/DEX contracts. Revisit only after a separate threat-tested capability project. |
| Offline LAN key import / secret sync / device scope | **DEFER** | These are speculative infrastructure, not caption MVP controls. |
| Translated voice overlay | **ENGINEERING PILOT ONLY** | Clear 22-minute fixtures, generic voices, original-audio fallback, no public dubbing claim. |
| Public “AI dubbing” | **NO-GO** | Separation/remix, long-form lifecycle, seek/sync quality, cost and cross-engine delivery are not established. |
| DRM/live/external-player generation | **NO-GO** | Explicit product boundary; no hidden capture or decrypt/proxy fallback. |

A mandatory Nuvio cloud service would introduce account, custody and availability dependencies not present in the README's current free/open-source, bring-your-own-sources positioning. It can be an optional managed mode, but must not be quietly required by something labeled local BYOK.

## Risk Matrix

| Risk | Evidence | Likelihood | Impact | MVP treatment |
|---|---|---:|---:|---|
| DEX steals/uses key in process | Same-process `DexClassLoader`, app context/prefs | High in full flavor once malicious DEX runs | Critical | Exclude DEX-capable process from BYOK. |
| Provider MITM | Trust-all common client | High on hostile network if inherited | Critical | Dedicated platform-trust client; MITM acceptance test. |
| Log/Sentry leak | Raw helpers, QR logs, partial `beforeSend`, default-on | High | High | Telemetry off or canary-proven redaction; safe reason codes only. |
| Custody mismatch | Local vault and backend broker recommendations conflict | Medium | High | Name/select one launch mode; no custodial BYOK. |
| LAN/session interception | Current unauthenticated HTTP state servers | High if reused | High | No LAN secret endpoint; device auth or HTTPS E2EE. |
| Cross-profile grant reuse | Numeric IDs reused; sharing flags redirect stores | Medium | High | Profile UUID/generation in AAD and operation checks. |
| Protected-source upload | URL possession/playback does not prove clear extraction | Medium | High | Clear-audio preflight; encrypted fixture veto. |
| Bill shock | Unsupported character rate, retries/rounding omitted | High | High | Hard per-job cap; observed provider ledger reconciliation. |
| Late/stale output after seek/episode | Current player jobs die/rebuild with lifecycle | High | High | Generation IDs, epoch checks, bounded cancellation. |
| TV consent abandonment | Lane 07 confirmation overload | High | Medium | Phone setup plus one concise TV summary; advanced settings elsewhere. |

## Required MVP Controls vs Deferred Infrastructure

| Required before public beta | Deferred unless the product scope expands |
|---|---|
| Dedicated secure provider networking; no cleartext/provider trust bypass | Certificate pinning beyond platform trust |
| One Keystore master version, random IV, AES-GCM AAD bound to install/profile-generation/provider/credential | E2EE cross-device key sync/recovery |
| Profile-local encrypted record; no plaintext DataStore/shadow copy; no AI entry in `ProviderCredentialSyncService` | Migration of all existing debrid/Trakt/MDBList/Simkl credentials |
| Native first-party adapter; no extension receives key, handle, auth header or selected-source credential | Generic plugin grant framework and isolated DEX runner |
| Telemetry disabled or server-side canary-proven; truthful Sentry copy | Compile-time safe-log lint/type system |
| Device authorization or HTTPS E2EE phone link if phone key entry ships | Offline LAN import and API 37 LAN permission flow when no listener exists |
| Clear-source preflight, source/audio identity, hard cost cap and invoice reconciliation | Universal DRM support/detection, live and external-player parity |
| Session generation/seek epoch/cancel semantics and finalized-cue cleanup | Durable backend jobs/foreground service when work stops on exit |
| Stable D-pad focus, visible processing indicator and explicit Stop | Household roles, device credentials and per-child budgets |
| Local revoke/delete; provider revoke only where documented | A universal provider-side revocation API |

## Non-Negotiable Acceptance Checks

1. A production provider call fails under an untrusted MITM CA and hostname mismatch; no AI adapter can obtain the trust-all common or CloudStream client.
2. The BYOK feature cannot initialize in a process that can execute external DEX. Attempting to invoke DEX while BYOK is active fails closed and requires an explicit safe transition/restart.
3. No API returns plaintext credentials to UI, plugins or general repositories. Any authorization capability is runtime/consumer-bound, short-lived and treated as sensitive.
4. Two encryptions of the same key differ; swapping ciphertext across install, provider, credential, profile UUID/generation or scope fails GCM authentication.
5. A binary/string scan of DataStore, `.bak`, cache, SavedState, work data, notifications and support exports finds no plaintext canary key.
6. Profile switch during test/inference rejects the response. Profile deletion/sign-out removes ciphertext, grants, memory cache and every shadow copy before that numeric ID can be reused.
7. AI credentials never enter `ProviderCredentialSyncService`, addon/repository/media URLs, QR payloads, navigation arguments, plugin settings, clipboard or analytics.
8. With a canary in URL path/query, headers, bodies, plugin console, provider response and nested exception causes, the canary is absent from release Logcat and the server-side stored Sentry event. If this cannot be proven, Sentry remains off and the UI does not claim otherwise.
9. Device/manual link sessions expire, are single-use and attempt-limited; wrong profile/provider/session, replay and modified ciphertext fail. Relay storage and analytics contain ciphertext only.
10. No credential endpoint is added to current NanoHTTPD servers. An unauthenticated LAN client obtains no AI provider, profile, grant or key state.
11. Known encrypted HLS/DASH/DRM fixtures cause zero provider calls and zero payload bytes. License URLs, cookies, debrid headers and playback Authorization never leave acquisition.
12. Only the selected audio track's bounded normalized audio is sent. No source URL/header or full video is sent to the speech provider.
13. Start is user-initiated and names cloud/on-device processing, provider, active profile, target language, billing owner and hard cap. Missing subtitles never auto-start capture.
14. Pause/Stop/profile switch/audio-track change/episode change/player release stops new audio delivery; callbacks from an old generation/seek epoch cannot render or accrue new stages.
15. Playback audio thread never blocks on inference. Queue memory and temporary artifacts are bounded; process death leaves no provisional PCM on disk.
16. Generated output is labeled **dialogue captions (beta)**, not SDH/closed captions. Voice output is labeled **translated voice overlay**, not dubbing.
17. Cue updates do not steal focus or reorder the selected card. Back dismisses without implicit cancellation; Stop is explicit; provider failure leaves playback and original subtitles/audio usable.
18. The measured provider bill is reconciled from per-request seconds/characters and billing quanta, including retries. A hard cap is enforced before scheduling each new shard; cancellation copy does not promise a refund.
19. Wrong-edition cache reuse is zero in the acceptance corpus; cache keys include exact media/audio identity, language, model/version and correction revision without raw signed URLs.
20. Public-beta kill conditions are credential/DRM leakage, stale cross-episode output, inability to stop spend, or failure to preserve normal playback,not failure to implement deferred sync, DEX isolation, LAN import or household administration.

## Summary

Overall assessment: **fix-first**.

A narrow, native generated-dialogue-caption beta is feasible and consistent with Nuvio's subtitle-first TV experience, but public BYOK must not ship with the current trust-all client, raw telemetry, or same-process DEX exposure. The practical MVP is profile-local, foreground, clear-VOD and first-party only; backend custody, secret sync, LAN import, plugin grants, household roles, durable whole-title work and public dubbing are later product choices rather than invented launch blockers.
