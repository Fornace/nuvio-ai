# Generated Dialogue Subtitles — baseline contract review against current HEAD

Repository: `/Users/ffrappo/works/repos/NuvioTV` · HEAD: `6e225905d658a1fd70a81d5bec5e5e4b42aadc20` (Sat Aug 29 2026, "docs: add Hammersmith AI media build briefs"). Read-only review; no repository files were changed. `git diff eca648a8..HEAD -- app/` is empty: the four commits since the research baseline `eca648a8` touch only `docs/` and `graphify-out/`, so every source claim in the brief and lanes was re-verified against identical HEAD content, with line numbers re-read rather than assumed.

## Summary

The brief is **confirmed against current HEAD**. Every Milestone 0–3 precondition it asserts is still absent or still broken in exactly the way the research lanes describe, and none of the architecture the mission requires (typed native provider API, `TimedCueSource`, `PcmTapAudioProcessor`, `MediaTransformSessionCoordinator`, capture-active PCM policy, independent flavor policy) exists yet anywhere in `app/src`. The mission's "review before fixes" precondition is therefore meaningful: below are nine evidence-backed findings with current line ranges, ordered roughly by milestone.

Verified seam-by-seam status at HEAD:

| Brief claim | HEAD status | Evidence anchor |
|---|---|---|
| `StreamDto.subtitles` parsed but dropped before player state | Confirmed | Finding 1 |
| CloudStream DEX `SubtitleFile`s collected then discarded | Confirmed | Finding 2 |
| Subtitle lookup failures swallowed; no Retry in overlay | Confirmed | Finding 3 |
| `OpenSubtitlesHasher` accepts any 2xx, ignores `Content-Range`, tolerates short reads | Confirmed | Findings 4–5 |
| Sidecar renderer is immutable one-shot `List` + 100 ms polling | Confirmed | Finding 6 |
| No PCM tap; `GainAudioProcessor` is gain-gated and timestamp-free; sink PCM policy covers only BT/speed | Confirmed | Finding 7 |
| No seek/source/episode/engine lifecycle coordinator; identity mutation scattered across ≥7 files | Confirmed | Finding 8 |
| Flavor policies diverge (ids, plugins, stubs); no independent provider policy | Confirmed | Finding 9 |

Milestone 0 is pure data-layer/static hardening (Findings 1–5) and can proceed independently of the player seams. Findings 6–8 are the Milestone 2–3 prerequisites inside the player; Finding 9 is the distribution gate the brief already flags. A disjoint four-lane ownership split is proposed at the end.

### Finding 1: Stream-provided subtitles are parsed and then silently discarded before reaching playback

Finding: Stremio addons may return ready subtitle URLs inline with each stream (`subtitles` array on the stream object). Nuvio parses that array into `SubtitleDto` but drops it in the DTO→domain mapping, so the data never reaches `PlayerUiState.addonSubtitles`.
Evidence:
- `app/src/main/java/com/nuvio/tv/data/remote/dto/StreamResponseDto.kt:23` — `StreamDto.subtitles: List<SubtitleDto>?` is parsed; `SubtitleDto` at `:117-121` carries `id/url/lang`.
- `app/src/main/java/com/nuvio/tv/data/mapper/StreamMapper.kt:18-34` — `StreamDto.toDomain()` maps name/title/description/url/ytId/infoHash/fileIdx/externalUrl/behaviorHints/sources/clientResolve and never reads `subtitles`.
- `app/src/main/java/com/nuvio/tv/domain/model/Stream.kt:13-29` — domain `Stream` has no subtitle field, so the mapper could not map it today.
- `app/src/main/java/com/nuvio/tv/ui/screens/stream/StreamScreenViewModel.kt:1818-1848` — `StreamPlaybackInfo` carries URL/headers/metadata only; no subtitle channel into the player.
- `app/src/main/java/com/nuvio/tv/data/repository/StreamRepositoryImpl.kt:673-684` — inline meta-video streams go through the same `toDomain`, so the inline path drops them too.
- `grep -rn "StreamDto.subtitles\|\.subtitles" app/src/main/java/com/nuvio/tv/data/mapper/ app/src/main/java/com/nuvio/tv/domain/model/Stream.kt` shows no consumer outside the DTO declaration.
Impact: Inline addon subtitles never render; the user sees an empty addon list and the future product would offer unnecessary (and billable) caption generation for media that already has subtitles. This is Milestone 0, bullet 1 of the brief.
Fix: Add `subtitles: List<Subtitle>?` to domain `Stream`, map it in `StreamMapper.toDomain`, merge into `PlayerUiState.addonSubtitles` on stream selection as ordinary completed subtitle records, and add a DTO→domain→player regression test per lane 06 §8.2.
Priority: P1
Confidence: high

### Finding 2: CloudStream DEX `subtitleCallback` results are collected and then thrown away

Finding: All four DEX execution paths accumulate `SubtitleFile`s from `loadLinks`/extractor callbacks but the conversion to `LocalScraperResult` drops them, exactly as lane 04 §3.4 states.
Evidence:
- `app/src/full/java/com/nuvio/tv/core/plugin/cloudstream/ExternalExtensionRunner.kt:172-198`, `:324-334`, `:437-471`, `:595-624` — `val subtitles = mutableListOf<SubtitleFile>()` populated via `subtitleCallback = { subtitles.add(it) }`, used only in diagnostics strings (`:190`, `:333`, `:470`, `:623`).
- `app/src/full/java/com/nuvio/tv/core/plugin/cloudstream/ExternalExtensionRunner.kt:848-871` — `ExtractorLink.toLocalScraperResult()` builds `LocalScraperResult` with title/name/url/quality/type/headers/provider only.
- `app/src/main/java/com/nuvio/tv/domain/model/Plugin.kt:100-117` — `LocalScraperResult` has no subtitle output field.
Impact: Same user-facing impact as Finding 1 for the DEX ecosystem; the brief's Milestone 0 bullet 2 ("preserve CloudStream subtitle callbacks only as ordinary completed subtitle records") has no implementation.
Fix: Extend `LocalScraperResult` with `subtitles: List<LocalSubtitleResult> = emptyList()` (main-safe default), map `SubtitleFile(lang, url)` in `toLocalScraperResult`'s call sites, and feed them into the same completed-subtitle pool as Finding 1. No live-media grant is involved, so this stays within the standard contract.
Priority: P1
Confidence: high

### Finding 3: SubtitleRepository swallows every failure; the overlay has no error/retry surface

Finding: Per-addon HTTP errors, timeouts and parse exceptions are all converted to empty lists, the repository interface returns only `List<Subtitle>`, and the TV overlay renders "no subtitles" identically for failed vs. truly empty lookups with no Retry action.
Evidence:
- `app/src/main/java/com/nuvio/tv/data/repository/SubtitleRepositoryImpl.kt:208-217` — `NetworkResult.Error` logs and returns `emptyList()`; `:214-217` catches all exceptions to `emptyList()`.
- `app/src/main/java/com/nuvio/tv/data/repository/SubtitleRepositoryImpl.kt:83-91, 109-116` — `withTimeoutOrNull` null (20 s timeout) becomes `emptyList()` with only a `Log.w`; a timed-out addon is indistinguishable from one with zero results.
- `app/src/main/java/com/nuvio/tv/domain/repository/SubtitleRepository.kt:16-25` — return type is a bare `List<Subtitle>`; no partial-success/error channel exists.
- `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerObservers.kt:95-151` — `fetchAddonSubtitles` has an exception→`addonSubtitlesError` branch, but the repository never throws non-cancellation exceptions, so the branch is effectively dead (lane 06 finding 4 confirmed).
- `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerUiState.kt:125` — `addonSubtitlesError` exists in state but `app/src/main/java/com/nuvio/tv/ui/screens/player/SubtitleSelectionOverlay.kt:96-111` takes no error parameter and `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerScreen.kt:1534-1552` does not pass it.
- `app/src/main/java/com/nuvio/tv/ui/screens/player/SubtitleSelectionOverlay.kt:707-719` — `options.isEmpty()` renders a plain `OverlayEmptyCard(subtitle_no_addon)` with no error variant.
Impact: Offline, 429, DNS failure and "no subtitles exist" look identical, blocking the brief's UX acceptance item 5 ("offline, 429 … expose recoverable focused actions") and lane 06 §7's failure matrix rows for one/all addons failing. Required by Milestone 0 bullet 3.
Fix: Return a structured result (subtitles + per-addon outcome: completed/empty/failed/timedOut) from `SubtitleRepository.getSubtitles`, surface a concise error with a focused **Retry lookup** action in `SubtitleSelectionOverlay`'s empty state, and keep the generation card reachable after failure per the brief's discovery spec.
Priority: P1
Confidence: high

### Finding 4: OpenSubtitlesHasher accepts any 2xx, never checks Content-Range, and reduces short reads to partial sums

Finding: The Range-read path validates neither the response code exactly (any 2xx other than 206 is accepted) nor `Content-Range` (never read), and a truncated body silently contributes a partial or zero sum instead of failing the compute.
Evidence:
- `app/src/main/java/com/nuvio/tv/core/player/OpenSubtitlesHasher.kt:113-114` — `if (!response.isSuccessful && response.code != 206) return 0L`: a `200` from a server that ignored `Range` is accepted.
- `app/src/main/java/com/nuvio/tv/core/player/OpenSubtitlesHasher.kt:115-131` — the loop reads only `length` bytes and breaks on `read < LONG_SIZE`; the method returns `sum` regardless of how much was consumed, and `Content-Range` is never compared to the requested `bytes=$offset-…`.
- `app/src/main/java/com/nuvio/tv/core/player/OpenSubtitlesHasher.kt:40-42` — outer `catch (_: Exception) { null }` additionally masks transport faults as "no hash".
- No unit test exists: `rg -l OpenSubtitlesHasher app/src/test app/src/androidTest` returns nothing, while a correct `Content-Range` parsing precedent exists for AFR at `app/src/test/java/com/nuvio/tv/core/player/FrameRateUtilsAfrTest.kt:805-812`.
Impact: A plausible-but-wrong hash is cached and sent to addons (`PlayerRuntimeControllerObservers.kt:52-77` caches `result.hash` into `streamLinkCacheDataStore`), poisoning subtitle matches and the future ASR exact-identity cache (lane 06 §1.2: "can produce a plausible false hash"). Milestone 0 bullet 4 is unimplemented.
Fix: In `readChunkSum`, require exactly `206`, parse and match `Content-Range` against the requested window (`start-end/total`, total == known file size), require exactly 64 KiB consumed, and throw (fail compute) on any mismatch or short read. Add MockWebServer-style regression tests: ignored-Range `200`, mismatched `Content-Range`, truncated tail block, rejected 416.
Priority: P1
Confidence: high

### Finding 5: Hasher trusts unvalidated length probes, enabling a concrete false-hash scenario

Finding: Beyond the missing response validation, `getContentLength` trusts the `PlayerMediaSourceFactory` probe cache (`acceptsRanges`/`contentLength`) and any HEAD `200` with a positive length; combined with Finding 4 this yields a realistic wrong-hash path rather than a theoretical one.
Evidence:
- `app/src/main/java/com/nuvio/tv/core/player/OpenSubtitlesHasher.kt:51-65` — cached `probeInfo` with `acceptsRanges=true` and `contentLength>0` short-circuits without any network validation; a HEAD probe returning `200` is accepted at `:79-86` with no `Accept-Ranges` check.
- Concrete scenario at HEAD: server advertises ranges (so HEAD/probe pass) but serves `200` full-body on the tail `GET bytes=(size-64K)-(size-1)`. Code accepts it (`:114`), reads the first 64 KiB of the body (`:119-129`), and computes `hash = fileSize + sum(first 64K) + sum(first 64K again)` — structurally valid, wrong value.
- `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerObservers.kt:52-56` — a `null` result is silently ignored (no identity-quality marker), and a non-null result is persisted into `streamLinkCacheDataStore`.
Impact: Wrong-media subtitle matches today; for the mission, `EXACT_FILE_HASH`-quality cache identity (lane 06 §3.4) cannot be trusted until this is hardened. Compounds Finding 4 rather than duplicating it: this is the input-trust half, that is the response-validation half.
Fix: Treat length/range capability as per-response facts: derive file size from the validated `Content-Range` total of the head-chunk response instead of a separate unvalidated HEAD when possible, and record a fingerprint-quality marker (`EXACT_FILE_HASH`/`WEAK_METADATA`/`SESSION_ONLY`) on failure instead of silently continuing with no hash.
Priority: P2
Confidence: medium

### Finding 6: PlayerSidecarSubtitles is an immutable one-shot parse plus 100 ms linear polling — no mutable cue seam

Finding: The sidecar downloads the whole body once, parses to one `List<CuesWithTiming>` assigned to a plain controller field, and a ticker linearly rescans that list every 100 ms. There is no `TimedCueSource`, no revision/epoch awareness, and no update path after initial parse.
Evidence:
- `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerSidecarSubtitles.kt:98-180` — `startSidecarAddonSubtitle` downloads (`:110`), parses once (`:113-116`), assigns `sidecarTimedCues = parseResult.cues` (`:144`), then loops `renderSidecarCuesAtCurrentPosition(); delay(SIDECAR_RENDER_INTERVAL_MS)` (`:152-155`); `SIDECAR_RENDER_INTERVAL_MS = 100L` at `:412`.
- `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerSidecarSubtitles.kt:341-362` — `collectActiveSidecarCues` iterates the full list every tick (with an early `break` only past `startTimeUs > positionUs`).
- `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeController.kt:349-351` — backing state is `sidecarSubtitleJob: Job?`, `activeSidecarSubtitleKey: String?`, `sidecarTimedCues: List<CuesWithTiming>`; no version/epoch/revision fields.
- `rg -n "TimedCueSource|MutableTimedCueStore|activeCues\(" app/src` — no matches; the brief's Milestone 2 seam does not exist.
Impact: Fine for static files (the audit's severity MEDIUM), but unusable for provisional/final generated cue revisions: feeding mutable snapshots into this design produces repeated full-list copies and scans every 100 ms plus stale-cue resurgence after seeks. Blocks Milestone 2.
Fix: Introduce the brief's `TimedCueSource` interface (`trackKey`, `version: StateFlow<Long>`, `activeCues(positionUs, epoch)`) behind the existing render loop: a `StaticFileCueSource` wrapping today's immutable list (byte-for-byte compatible behavior) and a `MutableTimedCueStore` with a time index, epoch/revision rejection, final-cue immutability, coalesced updates, and bounded cue count/text/rate — plus the one-hour transcript performance test.
Priority: P1
Confidence: high

### Finding 7: No PCM tap exists; the only audio processor is gain-gated and timestamp-free, and sink PCM policy covers only Bluetooth and speed

Finding: None of the Exo PCM seam prerequisites exist: `GainAudioProcessor` is the sole installed processor and is inactive unless gain is enabled and receives no timing; `PlaybackSpeedAwareAudioSink` forces PCM only for Bluetooth/non-1× bitstream; there is no capture-active PCM policy, no epoch, no bounded broker, and no restoration semantics.
Evidence:
- `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt:2065-2096` — `buildAudioSink` installs exactly one processor: `.setAudioProcessors(arrayOf(gainAudioProcessor))` (`:2086`), then wraps with `PlaybackSpeedAwareAudioSink` (`:2088-2095`). Factory wiring at `:805-845` (`gainAudioProcessor = gainAudioProcessor` at `:827`, `initialForcePcm`/`bluetoothForcePcm` at `:832-836`).
- `app/src/main/java/com/nuvio/tv/ui/screens/player/GainAudioProcessor.kt:25-30` — `isActive()` returns true only `super.isActive() && isGainEnabled()`; `:39-61` `queueInput` processes bytes with no presentation timestamp, confirming the audit's "no authoritative PTS" point.
- `app/src/main/java/com/nuvio/tv/ui/screens/player/PlaybackSpeedAwareAudioSink.kt:11-15` — documented policy is only "speed != 1x for bitstream" and "Bluetooth active"; `shouldRejectDirectPlayback` at `:117-131` implements exactly those two conditions. No generation/capture input.
- `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeController.kt:487` — the controller owns one shared `GainAudioProcessor` instance reused across player rebuilds; a tap processor would follow this ownership pattern.
- `rg -n "PcmTap|capture|epoch|AudioFrameBroker" app/src/main/java/com/nuvio/tv/ui/screens/player/` — no capture branch exists anywhere.
Impact: Blocks Milestone 3 entirely: cue timestamps would be derived from the wrong clock (queueInput wall time), passthrough/offload/tunneling sessions would deliver no frames silently, and any naive capture could block the audio thread. The audit's HIGH finding 3 stands unmitigated at HEAD.
Fix: Add a dedicated `PcmTapAudioProcessor` (never behavior inside `GainAudioProcessor`) enqueued behind a fixed-capacity non-blocking buffer with no Binder/network/disk on the audio thread; pair sample counts with host playback/discontinuity anchors; increment an epoch before the first post-seek/source/track sample; extend the sink policy with an explicit capture-active PCM transition that is visibly disclosed and restores prior passthrough/route state on grant end.
Priority: P1
Confidence: high

### Finding 8: No session coordinator — media identity mutation and seek/pause are scattered across at least seven controller files

Finding: Every lifecycle event the brief requires the `MediaTransformSessionCoordinator` to observe (committed seek, pause/resume, audio-track change, source change, retry, episode transition, engine failover, release) is implemented as an independent direct call in different files, with no generation/epoch authority and no pre-mutation revocation hook.
Evidence:
- User seeks: `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerMpv.kt:521-557` (`seekPlaybackTo`). Additional direct seeks outside it: resume-progress `player.seekTo(target)` at `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerObservers.kt:610-629`, stall-watchdog self-seek at `:779-788`, audio-track-switch nudge `player.seekTo((pos - 1))` at `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerTrackSelection.kt:61-84`, retry `player.seekTo((savedPosition - 1))` at `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerErrorRecovery.kt:260-299`, and initialization-time `seekTo(position)` at `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt:1190`.
- Pause/resume: `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerMpv.kt:559-576` (`setPlaybackPaused`).
- Source switch mutates identity (`currentStreamUrl/headers/filename`, hash/size) at `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerStreams.kt:572-615` and `:1325-1420`; episode transition at `:1447-1510` (resets `currentVideoHash/Size/Filename`, season/episode, clears track state) — none calls any revoke-style hook (none exists).
- Engine failover: `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerEngineFailover.kt:11-63` (startup failover) and `:65-128` (manual switch), remembering only ordinary audio/subtitle track identity (`:196-262`).
- Release: `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerLifecycle.kt:7-80` cancels named jobs and stops the sidecar (`:44`) but knows nothing of grants or PCM frames.
- `rg -n "MediaTransformSessionCoordinator|seek epoch|revoke" app/src` — no matches.
Impact: The audit's HIGH finding 6 is unmitigated: once generation exists, late callbacks from episode A could attach to episode B, a retry could re-enable a revoked tap, and no single place increments epochs or rejects stale work. This is the integration-risk hotspot for the whole mission.
Fix: Create one controller-owned `MediaTransformSessionCoordinator` and call it from `seekPlaybackTo`, `setPlaybackPaused`, the audio-track switch, `applySelectedStreamState`/episode-switch paths, both engine-failover entry points, error-recovery retries, and `releasePlayer`, revoking grants before any identity field changes and rejecting late callbacks; persist only stable descriptor identity, never flattened track indices.
Priority: P1
Confidence: high

### Finding 9: Flavor policies diverge exactly as audited — no independent provider policy, divergent host identities, and a stub playstore plugin layer

Finding: The `full`/`playstore` split carries different application IDs (three distinct debug/release identities), hard-disables plugins in playstore via both `BuildConfig` and a stub `PluginManager`, and no `FEATURE_MEDIA_TRANSFORM_PROVIDERS_ENABLED`-style policy exists in `main`.
Evidence:
- `app/build.gradle.kts:152-173` — `full`: `applicationId "com.nuvio.tv"` (defaultConfig `:104`), `FEATURE_PLUGINS_ENABLED=true`; `playstore`: `applicationId "com.nuvio.app"` (`:165`), `FEATURE_PLUGINS_ENABLED=false`.
- `app/build.gradle.kts:336-338` — debug variants rewrite ids again: playstore debug becomes `com.nuvio.appdebug`, full debug `com.nuviodebug.com`.
- `app/build.gradle.kts:504-521` — `quickjs`/`cloudstream`/jsoup/conscrypt are `fullImplementation` only.
- `app/src/full/java/com/nuvio/tv/core/build/AppFeaturePolicy.kt:3-11` (`pluginsEnabled=true`) vs `app/src/playstore/java/com/nuvio/tv/core/build/AppFeaturePolicy.kt:3-10` (`pluginsEnabled=false`).
- `app/src/playstore/java/com/nuvio/tv/core/plugin/PluginManager.kt:13-75` — playstore stub returns empty flows and `UnsupportedOperationException` failures.
- `app/src/main/java/com/nuvio/tv/ui/navigation/NuvioNavHost.kt:1278-1284` — plugin route registered only when `AppFeaturePolicy.pluginsEnabled`.
- `rg -n "FEATURE_MEDIA_TRANSFORM|SUBTITLE_CUES_V1|MediaTransformProvider" app/` — no matches; the typed native provider API the brief mandates in `main` does not exist.
Impact: The audit's HIGH finding 7 stands: building the provider on the existing plugin policy would make it vanish in the Play distribution and bind against the wrong host identity (a signed bound-service provider must match exact package/signing per flavor, including `com.nuvio.appdebug` debug builds). Also relevant to the BYOK fail-closed requirement: in `full`, arbitrary DEX runs in the credential-holding process.
Fix: Put the provider contracts and a new independent feature policy in `app/src/main` (not gated by `FEATURE_PLUGINS_ENABLED`), start it enabled only in the approved distribution, make provider discovery recognize the exact host package/signing identities for full, playstore, and debug variants, and define the DEX/BYOK flavor fail-closed policy before Milestone 1.
Priority: P1
Confidence: high

## Proposed ownership split

Four disjoint implementation lanes. No file appears in more than one lane; cross-lane signature changes (e.g. `TimedCueSource` consumption in the controller) are applied by a single integration owner per the brief's operating contract §9. All new files stay ≤400 lines.

**Lane A — Static subtitle correctness and hasher hardening (Milestone 0; pure data layer, no player files):**
- `app/src/main/java/com/nuvio/tv/data/remote/dto/StreamResponseDto.kt`
- `app/src/main/java/com/nuvio/tv/data/mapper/StreamMapper.kt`
- `app/src/main/java/com/nuvio/tv/domain/model/Stream.kt`
- `app/src/main/java/com/nuvio/tv/domain/model/Plugin.kt` (`LocalScraperResult` subtitle field)
- `app/src/main/java/com/nuvio/tv/domain/repository/SubtitleRepository.kt`
- `app/src/main/java/com/nuvio/tv/data/repository/SubtitleRepositoryImpl.kt`
- `app/src/main/java/com/nuvio/tv/core/player/OpenSubtitlesHasher.kt`
- `app/src/full/java/com/nuvio/tv/core/plugin/cloudstream/ExternalExtensionRunner.kt`
- New tests: `app/src/test/java/com/nuvio/tv/data/mapper/StreamMapperTest.kt`, `app/src/test/java/com/nuvio/tv/core/player/OpenSubtitlesHasherTest.kt`, `app/src/test/java/com/nuvio/tv/data/repository/SubtitleRepositoryImplTest.kt`

**Lane B — Mutable cue foundation and subtitle overlay UX (Milestone 2 + overlay error/retry):**
- `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerSidecarSubtitles.kt`
- `app/src/main/java/com/nuvio/tv/ui/screens/player/SubtitleSelectionOverlay.kt`
- New files: `app/src/main/java/com/nuvio/tv/ui/screens/player/subtitle/TimedCueSource.kt`, `.../subtitle/StaticFileCueSource.kt`, `.../subtitle/MutableTimedCueStore.kt` (plus its unit/perf tests)
- Existing compatibility tests it may extend: `app/src/test/java/com/nuvio/tv/ui/screens/player/SubtitleRaceConditionUpgradeTest.kt`

**Lane C — PCM tap, capture policy and session coordinator (Milestone 3; all controller wiring):**
- `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeController.kt` (incl. sidecar field migration to Lane B's seam)
- `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt`
- `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerMpv.kt`
- `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerObservers.kt`
- `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt`
- `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerStreams.kt`
- `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerEngineFailover.kt`
- `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerErrorRecovery.kt`
- `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerTrackSelection.kt`
- `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerLifecycle.kt`
- `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerUiState.kt`
- `app/src/main/java/com/nuvio/tv/ui/screens/player/PlaybackSpeedAwareAudioSink.kt`, `PlaybackSpeedAwareAudioRenderer.kt`, `GainAudioProcessor.kt` (read-only reference; tap must be a new file)
- New files: `PcmTapAudioProcessor.kt`, `MediaTransformSessionCoordinator.kt`, timing-anchor/broker files and their tests

**Lane D — Provider policy, flavor and distribution gates (Milestone 1 flavor prerequisite):**
- `app/build.gradle.kts`
- `app/src/full/java/com/nuvio/tv/core/build/AppFeaturePolicy.kt`
- `app/src/playstore/java/com/nuvio/tv/core/build/AppFeaturePolicy.kt`
- New files under `app/src/main/java/com/nuvio/tv/core/build/` (independent provider feature policy) and `app/src/main/java/com/nuvio/tv/core/media/provider/` (typed contracts, host identity/signing digest negotiation)
- `app/src/main/java/com/nuvio/tv/ui/navigation/NuvioNavHost.kt` (provider settings route gating only)

Sequencing note: Lane A can start immediately and land first; Lane B's `TimedCueSource` seam must merge before Lane C rewires the controller sidecar fields; Lane D's policy decisions gate the Milestone 1 security foundation but not Lane A.
