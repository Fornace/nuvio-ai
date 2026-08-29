# Long-lived Hammersmith build brief: Generated Dialogue Subtitles

## Mission

Own the complete implementation of an independently installable **Generated Dialogue Subtitles Provider** and the minimal NuvioTV host capability layer needed to generate captions for clear seekable media that has no usable subtitle track.

The product label is **Generate dialogue captions (beta)**. The first public-capable path is profile-local BYOK, foreground/session-bound, explicitly started by the user and ExoPlayer-only for live generation. Finalized VTT/SRT can be reused by MPV and external players.

This is a complete build mission. Continue until code, tests, Android TV verification, documentation, graph update and coherent commits satisfy every acceptance gate, or stop in explicit `input-required` with concrete evidence that only Francesco can supply.

## Repository and baseline

- Repository: `/Users/ffrappo/works/repos/NuvioTV`
- Research baseline: `eca648a8`
- Research decision: `docs/ai-media-research/decision.md`
- Detailed lanes: `docs/ai-media-research/lanes/03-fast-subtitle-generation.md`, `04-nuvio-extension-ecosystem.md`, `06-subtitle-integration-ux.md`, `07-byok-security-tv-ux.md`, `08-feasibility-economics.md`
- Audits: `docs/ai-media-research/audits/`
- Benchmark receipt: `docs/ai-media-research/benchmarks/2026-08-29-subtitle-api-probes.json`
- Architecture graph: `graphify-out/graph.json`
- Main build: `./gradlew :app:assembleFullDebug`

Before implementation, rebase or merge the current target branch as Francesco directs, record actual HEAD, Java, Gradle, Android SDK, emulator/device, Media3 AAR and bundled mpv versions, and run the baseline test/build suite. Never assume research line numbers still match after upstream changes.

## Hammersmith operating contract

1. Use one stable Hammersmith `run_name`, suggested `nuvio-generated-dialogue-subtitles`, across every round.
2. Launch the HUD with `hammersmith hud`. Use the webpage at `http://127.0.0.1:8700`; never open the parked Hammersmith.app.
3. Before the first run, inspect `~/.config/hammersmith/config.toml`, run:
   - `hammersmith models --task-type code-feature`
   - `hammersmith models --task-type code-review`
   - `hammersmith models --explore --task-type code-feature`
   - `hammersmith catalog --changes`
4. Ask Francesco to choose the worker engine/model from the top current options. At brief creation, local evidence favored `codex` with `gpt-5.6-sol` at high/xhigh for code-feature work: 22 tasks, 72.7% raw pass, and 94.1% adjusted pass at xhigh after infrastructure/contract exclusions. Recheck live data before execution. Never use an old or superseded model.
5. Browse `/Users/ffrappo/works/repos/hammersmith/templates/README.md`. Use:
   - `review-swarm` for pre-change code and threat review;
   - `repo-feature` for isolated implementation lanes;
   - `test-hardening` for independent test ownership;
   - `adversarial-review` before integration;
   - `probe` or `research-with-proof` only for expensive provider/device proofs.
6. Review comes before fixes. Workers that discover issues do not silently fix them in the same lane.
7. Every task spec is self-contained, declares exact file ownership, commands and output contracts. Never use pointer-only specs.
8. Every check executes substance and prints why it failed. Existence checks alone are invalid.
9. Use isolated worktrees for concurrent code tasks. Source ownership is disjoint across all live lanes. A single integration owner applies reviewed patches and resolves cross-module changes.
10. Hammersmith owns retries while active. Observe it. Do not manually take over failed workers unless it requests input or Francesco asks.
11. Commit every coherent milestone. Production deployment is out of scope; any future deployment must be merge-to-main driven.
12. All source files remain at or below 400 lines. Existing oversized files must be changed through new focused files/extensions rather than made larger.

## Non-negotiable product architecture

### Existing add-ons are not the live provider API

- Keep Stremio subtitle addons as completed URL sources.
- Do not put PCM, playback headers, stream URLs, grants or AI credentials into Stremio requests, QuickJS or CloudStream DEX.
- Do not add a Nuvio-private `subtitlesJob` resource and claim standard addon compatibility.
- Add a typed native provider API in `main`, independent of `FEATURE_PLUGINS_ENABLED`, with explicit distribution policy.

### First provider shape

Implement a signed Android bound-service or signed first-party provider package with capability `SUBTITLE_CUES_V1`.

Host-to-provider messages include:

- immutable provider package and signing digest identity;
- active profile generation/UUID;
- playback session and content/episode identity;
- selected audio-track fingerprint;
- source/target language and model settings;
- normalized PCM format;
- generation, seek epoch, sequence and media-time timestamp;
- pause, resume, discontinuity and end.

Provider-to-host messages include:

- stable track/cue IDs;
- generation and epoch;
- monotonically increasing revision;
- start/end media time;
- text and final/provisional flag;
- bounded status, progress and recoverable error codes.

The exact Binder/AIDL versus signed in-app implementation is a preflight decision, but the trust and lifecycle invariants cannot change.

### ExoPlayer PCM seam

Create a dedicated `PcmTapAudioProcessor` or equivalent capture branch. Do not add capture behavior to `GainAudioProcessor`.

- Pair sample counts with host playback/discontinuity anchors. `queueInput` wall time is not PTS.
- Increment epoch before the first post-seek/source/track sample.
- Copy into a fixed-capacity non-blocking queue. No Binder, network, disk or suspension on the audio thread.
- Resample/downmix on the capture branch without modifying audible playback.
- Add explicit capture-active PCM policy. Passthrough, offload and tunneling must either transition visibly to a tested PCM route or refuse generation.
- Restore prior audio route/passthrough state immediately when generation ends.

### Mutable sidecar rendering

Refactor `PlayerSidecarSubtitles.kt` behind a time-indexed interface, not `snapshot(): List` polling.

Suggested semantic surface:

```kotlin
interface TimedCueSource {
    val trackKey: String
    val version: StateFlow<Long>
    fun activeCues(positionUs: Long, epoch: Long): List<Cue>
}
```

- Static files wrap the existing immutable behavior.
- Generated cues use a bounded interval/time index.
- Stale generation/epoch/revision updates are rejected.
- Final cues are immutable.
- Provisional updates are coalesced.
- Cue and text size/rate limits are enforced.
- Existing style, delay, SDH filtering, sanitization, overlap and `SubtitleView` behavior stay compatible.

### Session coordinator

Create one controller-owned `MediaTransformSessionCoordinator`.

It is called by every committed seek, pause/resume, audio-track change, direct/torrent/cloud source change, retry, episode transition, engine failover, player release and profile switch. It revokes the old grant before identity changes and rejects every late callback.

### Credential and network boundary

First public beta uses one native first-party provider adapter and profile-local BYOK.

- Dedicated provider client with platform TLS and hostname validation.
- Never derive from current trust-all clients.
- One Android Keystore AES-256-GCM master key version.
- AAD binds installation, profile generation, provider and credential record.
- Metadata-only DataStore; no plaintext, SavedState, backup/shadow copy, WorkData or sync.
- No general plaintext getter.
- No AI entry in `ProviderCredentialSyncService`.
- No DEX access. If external DEX can execute in the credential-holding process, BYOK must fail closed according to the chosen flavor policy.
- Telemetry stays off for the beta unless a server-side canary proves complete redaction.

### Provider implementation order

1. Deterministic fake provider.
2. Qwen Audio 3.0 ASR Flash Streaming adapter, because the available test returned word and sentence timestamps.
3. Common provider interface plus paid bake-off lanes for Deepgram Nova-3 and Gemini 3.5 Transcribe Live.
4. Ahead-of-playback/finalization adapters: Cloudflare Whisper Large V3 Turbo baseline, Qwen Filetrans long-title control, then selected winner.

A one-clip result never becomes a production selection.

## Required implementation milestones

### Milestone 0: baseline and static correctness

- Preserve `StreamDto.subtitles` through domain/player state.
- Preserve CloudStream subtitle callbacks only as ordinary completed subtitle records if the existing contract can do so safely. Do not grant live media access.
- Replace swallowed subtitle lookup failures with a structured partial-success/error result and expose Retry in the overlay.
- Harden `OpenSubtitlesHasher`: exact 206, matching `Content-Range`, exact 64 KiB first/last reads, fail compute on malformed/rejected/short reads.
- Add regression tests.
- Commit.

### Milestone 1: shared security/provider foundation

- Dedicated secure network client and provider destination allowlist.
- Profile-generation credential vault and cleanup.
- Typed provider identity/capability/version negotiation.
- Grant issue/revoke/replay/expiry checks.
- Release telemetry policy and canary tests.
- Fake provider package/module.
- Commit.

### Milestone 2: mutable cue foundation

- `TimedCueSource`, static adapter and `MutableTimedCueStore`.
- Revision/epoch/finalization rules, time index, bounds and coalescing.
- Existing static sidecar behavior compatibility tests.
- Long transcript performance test proving no full-list copy/scan every 100 ms.
- Commit.

### Milestone 3: PCM and lifecycle

- Exo capture branch, timing anchors and bounded broker.
- Explicit capture-active PCM transition/restoration.
- Coordinator integration into every seek/source/track/profile/failover/release path.
- Fake PCM-to-cue end-to-end flow.
- Playback underrun, queue pressure and zero-post-revoke tests.
- Commit.

### Milestone 4: TV UX

- Settings entry for provider connection and status.
- Phone device-auth or HTTPS E2EE setup where supported. Never add a credential endpoint to existing NanoHTTPD servers.
- Stable **Generate dialogue captions (beta)** option in the current language → option → right-rail subtitle overlay.
- Setup fields: selected audio, spoken language, target language, provider and concise cost/retention summary.
- Explicit consent and Start.
- Stable Preparing, Listening, Repositioning, Paused, Live, Complete and Failed states.
- Persistent **Audio being transcribed** indicator while frames can flow.
- Back dismisses without canceling, Stop is explicit, updates never steal focus.
- MPV shows **Switch player and continue**, never fake live support.
- Commit.

### Milestone 5: real adapters and cache/export

- Qwen streaming adapter with session/reconnect/error/cancellation mechanics and raw-response redaction.
- Provider-specific hard job cap checked before every new request/shard.
- Batch/final correction adapter after common-corpus selection.
- Finalized VTT/SRT export and profile-scoped exact-identity cache.
- No raw PCM on disk; provisional text memory-only.
- Exact cost ledger and cancellation semantics.
- Commit.

### Milestone 6: device evidence and release review

- Run unit, Robolectric, instrumentation, Compose focus and end-to-end tests.
- Test representative Android TV/Fire TV hardware, MediaCodec and FFmpeg routes, Bluetooth/HDMI, 0.75×/1×/1.5×, repeated seek, pause, route change, background/release and process death.
- Run a common owned film/TV corpus, not synthetic speech only.
- Adversarial security, lifecycle, UX and accessibility reviews.
- Update docs, Graphify, Honcho repo knowledge and Obsidian operational notes.
- Commit final evidence and implementation.

## TV interaction specification

### Discovery

- Existing embedded/addon tracks load normally.
- The generation card remains reachable after lookup failure.
- Empty state offers **Retry lookup** and **Generate dialogue captions**.
- Generation never starts because the track list is empty.

### Setup

Right rail fields:

- Audio
- Spoken language
- Subtitle language
- Provider
- Processing location
- Cost cap
- **Start generating**

Keep labels one line where used as buttons. Use current design tokens, focus rings, rail transitions, RTL behavior and scroll restoration.

### Consent

TV copy states what it is:

> Nuvio will share the selected program audio with Provider to create English dialogue captions. Video links and account headers stay on this TV. Your provider account is billed up to the shown cap.

Actions:

- **Allow for this episode**
- **Cancel**

Do not introduce household admin claims or implicit always-allow for cloud processing.

### Active state

- First renderable cue may select the generated track if the user chose that behavior.
- Closing the overlay does not stop work.
- Pausing playback stops new frame delivery.
- Seek displays Repositioning and creates a new epoch.
- Stop offers keep finalized cues or remove generated output.
- Failure leaves playback and other tracks usable.

## Acceptance checks

### Security

1. Untrusted MITM CA and hostname mismatch fail every provider call.
2. Wrong signer/package/session/profile-generation/episode/track, expired grant and replay fail.
3. Two encryptions differ; ciphertext swapping fails GCM authentication.
4. Canary key is absent from DataStore, backups, cache, SavedState, work data, notifications, Logcat, Sentry, support exports and generated files.
5. AI credentials never enter existing sync, addon/repository URLs, QR payloads, plugin settings or clipboard.
6. Existing NanoHTTPD servers expose no AI state or key endpoint.
7. Protected fixtures produce zero provider calls and bytes.

### Correctness and lifecycle

1. Pause/stop/profile switch/audio change/episode change/release stop frames before UI cleanup.
2. Old generation/epoch callbacks cannot render.
3. A slow provider cannot block playback or grow memory without limit.
4. Passthrough/PCM transitions are truthful and restored.
5. Final cues cannot regress; malicious timing/text/rate inputs are bounded.
6. Exact media/audio/model/language identity controls cache reuse.

### UX and accessibility

1. D-pad and RTL traversal are deterministic across all rails/dialogs.
2. Cue updates do not change focus, selected IDs or scroll position.
3. Back behavior and destructive-action initial focus match the specification.
4. TalkBack never announces secret content and labels program-audio capture accurately.
5. Provider failure, offline, 429 and expired key all expose recoverable focused actions.

### Objective media quality

1. First stable cue p50 ≤3 s, p95 ≤6 s after play begins.
2. Median cue onset error ≤250 ms, p95 ≤750 ms.
3. No cumulative drift >1 s/20 min.
4. Clean-dialogue WER ≤15%; music, overlap, accents and code-switching are separately reported.
5. Seek recovery at 5, 30 and 60 minutes has no old-epoch contamination.
6. Provider bill is within 10% of locally metered units for at least 95% of jobs.

## Hammersmith task/check shape

Every code lane should start from the `repo-feature` template with an isolated worktree and exact owned paths. The check must include:

- targeted unit/test command for that lane;
- `./gradlew :app:assembleFullDebug` at integration rounds;
- required source symbols or behavior assertions;
- git-porcelain allowlist;
- source file line-count validator;
- no-secret/static logging scan;
- task `notes.md` with exact commands and results.

Before merging a milestone, run three read-only adversarial reviewers:

1. security/privacy and cross-profile lifecycle;
2. player timing, audio-thread safety and seek/failover;
3. TV focus/accessibility and truthful product state.

The integration owner reproduces every P0/P1 finding before accepting a fix.

## Explicit non-goals

- AI access for current JS or DEX extensions.
- DRM, live TV, microphone or MediaProjection capture.
- Secret sync, custodial BYOK, device-wide credentials or household roles.
- Background continuation after the user exits playback.
- Claiming generated dialogue captions are SDH/closed captions.
- MPV live generation without a tested native audio-frame seam.

## Completion evidence

The mission is complete only when:

- every milestone has a coherent commit;
- all acceptance checks map to command output, test artifacts, screenshots or device receipts;
- Full Debug builds through Gradle on the recorded toolchain;
- the feature works on a real TV target with remote-only navigation;
- no required issue remains in the Hammersmith artifact;
- `graphify update .` has run successfully or a documented Graphify integrity issue has been handled;
- repo and personal completion reviews are stored in Honcho as appropriate;
- significant operational decisions/recipes are recorded in the shared Obsidian vault;
- the working tree is clean.

Do not mark the mission complete because a provider call works, synthetic tests pass or token budget is low. Unknowns at an acceptance gate mean the mission remains active or becomes explicit `input-required`.
