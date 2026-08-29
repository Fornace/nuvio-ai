# Nuvio Media Architecture Audit , Lanes 04-06

**Baseline:** repository commit `eca648a8`; audited lanes 04-06 and the cited player, addon, plugin, flavor, and model paths. No application source was changed and no build/runtime result is claimed.

## Severity: HIGH

## Issues Found

### 1. Existing “plugins” are stream discovery mechanisms, not post-selection media processors , HIGH
**Location**: `app/src/main/java/com/nuvio/tv/data/remote/api/AddonApi.kt:13-28`, `app/src/full/java/com/nuvio/tv/core/plugin/PluginRuntime.kt:129-150`, `app/src/main/java/com/nuvio/tv/domain/model/Plugin.kt:100-117`

**Problem**: The three ecosystems mapped in lane 04 cannot implement the proposed live features:

- A Stremio addon receives resource IDs and optional subtitle fingerprint metadata and returns URL records. The official protocol standardizes `/stream/...` and `/subtitles/...`; the subtitle response is a list of `{id,url,lang}`. It does not standardize selected-stream callbacks, PCM, mutable cues, dubbing jobs, seek epochs, or cancellation ([Stremio protocol](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md), [subtitle response](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/subtitles.md)).
- Nuvio JS invokes only `getStreams(tmdbId, mediaType, season, episode)` and parses only `LocalScraperResult` (`PluginRuntime.kt:129-150`, `:1331-1374`).
- CloudStream DEX may emit `SubtitleFile`, but `ExternalExtensionRunner` collects and discards it before returning `LocalScraperResult` (`ExternalExtensionRunner.kt:172-198`, `:324-334`, `:437-471`, `:595-624`).
- `StreamDto.subtitles` is parsed but omitted by `StreamDto.toDomain`, and domain `Stream` has no subtitle or auxiliary-audio field (`StreamResponseDto.kt:12-24`, `StreamMapper.kt:18-34`, `Stream.kt:10-30`).

**Impact**: Adding `dub` to a Stremio manifest, adding fields to `LocalScraperResult`, or calling a JS export would create a Nuvio-private convention while still lacking the player-time, permission, lifecycle, and output-attachment semantics the feature needs. It would also run privileged media access through either unrestricted network-capable JS or arbitrary in-process DEX code.

**Fix**: Keep normal Stremio addons as the completed-file subtitle lookup path only. Create a separate, typed native **Media Transform Provider API** for live/generated media. Do not grant PCM, playback headers, or stream URLs to Stremio, QuickJS, or CloudStream providers.

### 2. “MPV-only dubbing MVP” and “ExoPlayer-only PCM capture” do not form one end-to-end dubbing MVP , HIGH
**Location**: `app/src/main/java/com/nuvio/tv/ui/screens/player/NuvioMpvSurfaceView.kt:35-123`, `:455-481`, `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt:806-848`, `:2065-2087`

**Problem**: MPV is the shortest **output attachment** path because upstream mpv has `audio-add`, `audio-remove`, and `audio-reload`; Nuvio’s wrapper has not exposed them ([mpv track manipulation](https://mpv.io/manual/stable/#track-manipulation)). ExoPlayer is the only observed Kotlin **decoded-input** seam because Nuvio builds a custom `DefaultAudioSink` and installs an `AudioProcessor`. MPV is configured for `audiotrack,opensles` and exposes no decoded-frame callback in this tree.

There is a second, deeper conflict: an AudioSink tap observes the currently selected decoded audio. For live dubbing, selecting the generated dub would stop that tap from receiving the original source track. A one-renderer playback tap therefore cannot both continuously supply original speech and play the generated replacement.

**Impact**: Calling the feature “MPV-only AI dubbing” hides the absence of an MPV input path; calling it “Exo PCM dubbing” hides the source-track feedback problem. Near-real-time dubbing would stall, transcribe its own output, or require an unimplemented second decoder.

**Fix**: Split the first release honestly:

1. **Live Subtitle Plugin MVP:** ExoPlayer-only decoded PCM → mutable cues.
2. **Dub Plugin MVP:** MPV-only attachment of a **completed, seekable, provider-produced audio artifact**. The provider must obtain/generate that artifact without receiving Nuvio playback credentials. This validates output UX, sync calibration, seek behavior, and teardown; it does not claim live generation from the active stream.

Sustained near-real-time dubbing is a later gate requiring either (a) a provider that independently and lawfully acquires source audio, or (b) a host-owned independent source decoder/session proxy. The latter is not a “minimal AudioProcessor change.”

### 3. The proposed PCM tap is feasible only as a prototype; current code does not establish a timestamped, universal capture path , HIGH
**Location**: `app/src/main/java/com/nuvio/tv/ui/screens/player/GainAudioProcessor.kt:11-61`, `app/src/main/java/com/nuvio/tv/ui/screens/player/PlaybackSpeedAwareAudioSink.kt:18-145`, `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt:2065-2087`

**Problem**:

- `GainAudioProcessor` proves byte-level PCM-16/float processing, but it is inactive unless gain is enabled (`isActive()`), and `queueInput` receives no media timestamp.
- `PlaybackSpeedAwareAudioSink` forces PCM only for startup recovery, Bluetooth, or encoded playback at non-1× speed. It has no “capture active” policy.
- The repository does not prove processor invocation for every relevant MediaCodec/FFmpeg, passthrough/offload, tunneling, route-change, or speed path.
- Copying or IPC from `queueInput` can block the real-time playback path unless the tap is strictly bounded and non-blocking.

**Impact**: Cue timestamps can be based on the wrong clock after seek/speed/discontinuity, passthrough may produce no frames, and a slow provider can cause audible underruns.

**Fix**: Add a dedicated `PcmTapAudioProcessor`, not behavior inside `GainAudioProcessor`. Pair its sample counter with host playback/discontinuity anchors; increment an epoch on committed seeks and source/track changes. The processor may only enqueue into a fixed-capacity lock-free/bounded buffer and must never call Binder/network/disk directly. Add an explicit capture-active PCM policy and restore the previous route/passthrough state when the grant ends.

### 4. External audio is viable, but only behind a first-class descriptor and strict transport gates , HIGH
**Location**: `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerMediaSourceFactory.kt:82-207`, `app/src/main/java/com/nuvio/tv/ui/screens/player/NuvioMpvSurfaceView.kt:624-638`, `app/src/main/java/com/nuvio/tv/ui/components/TrailerPlayer.kt:112-114`, `:188-190`

**Problem**: The main player has no auxiliary-audio model. Trailer playback demonstrates only that `MergingMediaSource(videoSource,audioSource)` can compile and work for that narrow source; it does not prove main-player seek, error, period, headers, delay, or failover behavior. Nuvio MPV sets `http-header-fields` globally, so attaching a remote dub while primary credentials remain set can send those credentials to the dub origin or fail because the dub needs different headers.

Official Media3 documents `MergingMediaSource` for parallel sources and recommends timeline-offset/duration handling, but upstream requires compatible period topology and coordinated seeks ([Media3 media-source composition](https://developer.android.com/media/media3/exoplayer/media-sources#advanced_media_source_composition), [Media3 1.11.0 source](https://github.com/androidx/media/blob/1.11.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/source/MergingMediaSource.java)). Nuvio declares Media3 1.8.0 while replacing core modules with local AARs (`gradle/libs.versions.toml`, `app/build.gradle.kts:348-355`, `:452-478`); current upstream behavior cannot be assumed for those binaries.

**Impact**: Direct remote sidecars risk credential disclosure, whole-player failures on an auxiliary 401/404, mismatched seeks, early EOS, or different behavior between the forked Media3 binaries and current docs.

**Fix**:

- Accept completed dub artifacts over a `ParcelFileDescriptor`/content grant, ingest them into app-private storage, and expose a random tokenized `127.0.0.1` URL to MPV. Do not inherit primary headers.
- Add `ExternalAudioDescriptor` with stable key, generation, MIME/container, language/label, media-time origin, known duration, complete/seekable flags, coverage, and integrity hash.
- Gate direct Media3 merge to finalized, seekable VOD with tested period topology and duration tolerance. Keep embedded audio as fallback; do not filter it out. Build primary and sidecar with separate data sources, use `clipDurations=false`, and wrap the final composite once with the existing delay source.
- Use localhost HLS/DASH alternate audio as the later cross-engine live transport, not a growing progressive file.

### 5. The mutable subtitle sidecar direction is correct, but `snapshot(): List` would become an O(n) polling path , MEDIUM
**Location**: `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerSidecarSubtitles.kt:102-200`, `:318-362`, `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeController.kt:347-354`

**Problem**: Today the full immutable cue list is assigned once, then linearly scanned every 100 ms. Replacing it with an ever-growing mutable snapshot would repeatedly copy and scan the transcript. It also needs thread-safe revision, epoch, and size rules that the plain `List<CuesWithTiming>` does not provide.

**Impact**: Long content creates avoidable CPU/GC pressure, stale cues can reappear after seeks, and high-frequency provisional revisions can churn `SubtitleView` and TV focus state.

**Fix**: Generalize the renderer, but expose `activeCues(positionUs, epoch)` and a change/version flow rather than full-list snapshots. Back it with a time-indexed immutable snapshot or interval index; coalesce provider updates; bound cue count/text/rate; reject stale revisions and old epochs. Keep the generated option’s UI key stable. Exo can render live cues directly; MPV and external players receive only a finalized VTT/SRT snapshot until a tested native/segmented mutable path exists.

### 6. Generated media must be durable controller state with one seek/source/failover authority , HIGH
**Location**: `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerMpv.kt:521-563`, `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerEngineFailover.kt:11-132`, `:196-258`, `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerLifecycle.kt:7-75`

**Problem**: User seeks are centralized, but additional direct seeks exist in startup, track switching, watchdog, and recovery paths. Engine failover remembers only ordinary audio language/name/ID, not a dub descriptor/session. Release knows only current sidecar/player jobs. No generated-media generation token is invalidated before episode/source fields change.

**Impact**: Late output from episode A can attach to episode B; retries/failover can silently drop or mis-select a dub; an auxiliary failure can consume primary retry/failover budget; revoked PCM may continue flowing during teardown.

**Fix**: Introduce one `MediaTransformSessionCoordinator` owned by `PlayerRuntimeController`. Every committed seek, retry, source/episode switch, engine switch, and release must call it. Auxiliary errors fall back to original audio/captions without failing video. Persist only the descriptor and desired stable selection in controller session state, never flattened track index.

### 7. Existing flavor/plugin gates cannot deliver the same plugin promise , HIGH
**Location**: `app/build.gradle.kts:152-173`, `:496-521`, `app/src/playstore/java/com/nuvio/tv/core/plugin/PluginManager.kt:13-74`

**Problem**: Full and Play Store builds have different application IDs (`com.nuvio.tv` vs `com.nuvio.app`); JS/DEX dependencies and working `PluginManager` exist only in `full`, while `playstore` hard-disables plugins. MPV and core player code are in both flavors. Treating the new providers as existing “plugins” therefore makes install, discovery, and settings disappear in one distribution.

**Impact**: Provider packages can bind to the wrong host identity, capability UI can be shown where runtime support is stubbed, and release behavior diverges by flavor.

**Fix**: Put the typed provider API and player capability interfaces in `main`, behind a new independent `FEATURE_MEDIA_TRANSFORM_PROVIDERS_ENABLED` policy. Start enabled only in the approved distribution. Provider discovery must recognize the exact host package/signing identities for full, playstore, and debug variants. Enabling external providers in the Play build is a separate distribution/policy acceptance gate; normal Stremio completed-subtitle lookup remains available in both flavors.

## Reconciled Architecture Recommendation

### A. Product boundary: two independently installable logical providers

Implement two separate signed Android bound-service provider packages/records, each independently installable, configurable, revocable, versioned, and selectable:

| Logical plugin | Provider capability | MVP input | MVP output | MVP engine |
|---|---|---|---|---|
| **Live Subtitle Provider** | `SUBTITLE_CUES_V1` | Host-granted selected decoded PCM with epoch/media-time anchors | `CueUpsert` events into host mutable cue store; finalized VTT export | **ExoPlayer only** for live generation; finalized files work on Exo/MPV/external players |
| **Dub Provider** | `DUB_ARTIFACT_V1` | Provider-owned generation workflow; no Nuvio URL/header grant | Completed, seekable audio artifact via FD plus metadata | **MPV only** attachment first; Media3 only after merge gate |

These are not Stremio resources, JS scrapers, or CloudStream DEX extensions. A normal Stremio subtitle addon remains a third-party **batch URL source**, not the Live Subtitle Provider.

### B. Minimal host capability layer

Proposed source seams (new classes; names are architectural targets):

- `app/src/main/aidl/com/nuvio/tv/media/plugin/IMediaTransformProvider.aidl` , typed session IPC only.
- `app/src/main/java/com/nuvio/tv/core/media/plugin/MediaTransformProviderRegistry.kt` , explicit package binding, provider signing digest, capability/version negotiation.
- `.../MediaCapabilityGrantStore.kt` , unforgeable, session/episode/track/plugin-scoped, monotonic-expiry grants and synchronous revocation.
- `.../MediaTransformSessionCoordinator.kt` , generation/epoch state machine and status/progress.
- `.../TransformContracts.kt` , immutable identities, formats, cue/artifact/status messages.
- `app/src/main/java/com/nuvio/tv/ui/screens/player/PcmTapAudioProcessor.kt` , Exo-only bounded frame tap.
- `.../MutableTimedCueStore.kt` plus a generalized `PlayerSidecarSubtitles.kt` , indexed mutable cue rendering.
- `.../ExternalAudioDescriptor.kt` and `DubPlaybackCoordinator.kt` , durable dub attachment/selection/fallback state.
- `.../LocalMediaArtifactServer.kt` , tokenized loopback delivery; no LAN bind and no primary-header forwarding.

Modify these existing seams rather than creating parallel player control paths:

- `PlayerRuntimeControllerInitialization.kt`: install the tap and explicit capture-active PCM policy.
- `PlayerRuntimeControllerPlaybackEvents.kt` / `PlayerRuntimeControllerMpv.kt`: notify committed seek, pause/resume, speed, and track changes.
- `PlayerRuntimeControllerStreams.kt`, `PlayerRuntimeControllerEngineFailover.kt`, `PlayerRuntimeControllerLifecycle.kt`: generation invalidation, idempotent reattach, ordered revoke/cleanup.
- `NuvioMpvSurfaceView.kt`: verified `audio-add/remove/reload` adapter and external track identity.
- `PlayerMediaSourceFactory.kt`: later `createPlaybackMediaSource(primary, finalizedAudio?)` with independent sources.
- `PlayerUiState.kt`, `TrackInfo`, `AudioSelectionOverlay.kt`, `SubtitleSelectionOverlay.kt`: stable key, origin, readiness/coverage, fallback/error state.

### C. Host contract invariants

1. Provider identity is package/module plus signing digest, never display name or addon URL.
2. No provider receives stream URL, headers, cookies, debrid/torrent URL, DRM session, or arbitrary source file access.
3. PCM messages carry `sessionId`, `generation`, `epoch`, `sequence`, `mediaTimeUs`, sample rate/channels/encoding, and bounded payload; IPC is off the audio thread.
4. Cue updates carry stable track/cue IDs, epoch, revision, start/end, text, and final flag. Old generation/epoch or revisions are rejected.
5. Dub artifacts carry stable key, generation, MIME/container, language/label, source-media origin, duration, seekability/completion, hash, and FD. Binder does not carry whole audio byte arrays.
6. Primary and auxiliary credentials are never merged. MPV receives only a credential-free loopback URL.
7. Original audio remains available. Auxiliary failure never stops primary video.
8. Raw PCM and provisional cues are memory-only by default; finalized text/artifacts are profile-scoped, user-clearable, bounded, and excluded from logs.

### D. Engine and lifecycle behavior

| Operation | Live subtitles | Finalized dub MVP | Later adaptive dub |
|---|---|---|---|
| ExoPlayer | PCM tap + direct mutable `SubtitleView` cues | Unsupported until actual-fork merge gate passes | Local HLS/DASH with alternate audio |
| MPV | Finalized subtitle snapshot only; live capture unavailable | `audio-add` local completed asset | Same local HLS/DASH URL if device tests pass |
| External player | Finalized VTT/SRT only | Unsupported unless exported as one muxed/adaptive URL | One reachable finalized/remuxed URL only |
| Seek | Increment epoch; flush queued PCM/provisional cues; resume at committed target | Keep original audio until external track proves target seek; otherwise detach/fail | New generation/coverage request; original-audio fallback until ready |
| Engine failover | Exo→MPV revokes PCM and freezes/finalizes available cues; MPV→Exo starts a new epoch/grant | MPV→Exo explicitly drops dub until Media3 gate; UI must say so | Reopen same session URL and reselect by stable key |
| Episode/source switch | Revoke before identity/URL changes; reject every late callback | Remove track/artifact generation before load | Cancel old generation and ring before new source |

This resolves the apparent contradiction: engine support is **capability-specific**, not a single global “AI plugin support” flag. Phase 1 does not promise simultaneous live subtitles and dubbed playback under one engine.

### E. Required acceptance gates

1. **Ecosystem/contract gate** , Two providers install/remove/configure independently; neither appears as a stream scraper; unsupported capability versions fail closed; Stremio batch subtitles remain unchanged.
2. **Security gate** , Wrong signer/package/session/episode/track, expired/replayed grant, late Binder callback, and provider death all revoke before UI cleanup. Automated network tests prove no Authorization/Cookie/Host/query secret reaches provider or auxiliary host.
3. **PCM gate** , On the actual forked Media3 AARs and target TVs, prove PCM frames and media-time anchors across MediaCodec and FFmpeg, 0.75×/1×/1.5×, seek, pause, route change, and teardown. Passthrough/tunneling either switch visibly to tested PCM or generation is refused. No audio-thread Binder/network/disk work, no underrun regression, bounded memory, and zero frames after revoke.
4. **Subtitle gate** , Out-of-order/duplicate/oversized cue attacks are bounded; seek epochs cannot leak; finalized cues cannot regress; one-hour transcripts do not perform full-list copy/linear scan every 100 ms; repeated updates do not steal D-pad focus or reset the video source/buffer.
5. **MPV artifact gate** , Query/log the bundled mpv version and execute `audio-add/remove/reload` on `mpv-android-lib:0.1.12`; test local AAC/M4A and every claimed codec/container, start/middle/backward/near-EOS seeks, pause/speed/delay, source switch, 404/corruption, 100 attach/remove cycles, and memory/FD cleanup. If command or seek behavior differs from the current manual, the MVP does not ship.
6. **Media3 merge gate** , Against Nuvio’s exact local AARs, test equal/mismatched periods, unknown/short duration, `clipDurations=false`, offsets, track identity, embedded-audio fallback, independent headers, auxiliary-only error handling, repeated seek/rebuild/release, and leak behavior. Until this passes, Exo dub UI is disabled rather than silently dropping the dub.
7. **Input-feasibility gate for dubbing** , Demonstrate where the Dub Provider lawfully obtains original audio. If it needs active-stream PCM, prove an independent source decoder that continues while dub audio is selected; otherwise the product must remain completed-artifact attachment only.
8. **Sync gate** , Use known impulses/dialogue markers at start/middle/end and after seeks. Adopt the lane’s proposed product threshold (median absolute error ≤80 ms, p95 ≤150 ms after settling) or define a reviewed replacement before release; manual delay is calibration, not drift correction.
9. **Failover gate** , Auxiliary failures never consume primary retry/engine-failover budget; original audio/video remains continuous; status truthfully reports generating/recovering/fallback/dropped-on-engine-switch.
10. **Flavor gate** , Full/playstore/debug host identity, feature policy, provider discovery, package visibility, signing, and disabled-state UI are tested separately. No Play Store capability is enabled before its provider-delivery and policy review is approved.
11. **DRM/live gate** , DRM, protected decoded output, live TV, growing progressive audio, and external-player parity are explicitly unsupported until separately designed and tested; no proxy/decrypt workaround is inferred from clear VOD success.

## Summary

Overall assessment: **fix-first**.

The lane research correctly identifies useful player seams, but it conflates discovery addons with privileged media processors and combines an MPV output shortcut with an Exo-only input assumption that cannot sustain live dubbing. Ship two independent logical providers behind a small typed native capability layer: Exo-only live mutable subtitles and MPV-only finalized dub attachment first, with strict security, seek, failover, binary-version, and source-input gates before claiming broader engine parity or near-real-time dubbing.
