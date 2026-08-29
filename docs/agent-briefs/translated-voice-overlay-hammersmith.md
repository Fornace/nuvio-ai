# Long-lived Hammersmith build brief: Translated Voice Overlay

## Mission

Own the complete engineering program for an independently installable **Translated Voice Provider** and the NuvioTV host capabilities needed to attach, synchronize and eventually generate translated speech for clear media using a user-supplied provider key.

The first deliverable is an internal **completed translated-audio attachment** pilot on MPV. The second is a measured near-real-time prototype with an independent original-audio acquisition path and local adaptive/proxy transport. A public release may use the name **Translated voice overlay** only after all pilot gates pass. Do not market this as AI dubbing until dialogue separation/remix, bleed, long-form sync, seek behavior, quality and cost pass objective gates.

This is a complete build mission. Continue through architecture, source implementation, provider bake-off, TV UX, device tests, documentation, graph update and coherent commits. Stop only at complete or explicit `input-required` with concrete evidence that only Francesco can supply.

## Repository and baseline

- Repository: `/Users/ffrappo/works/repos/NuvioTV`
- Research baseline: `eca648a8`
- Decision: `docs/ai-media-research/decision.md`
- Dubbing research: `docs/ai-media-research/lanes/01-commercial-realtime-dubbing.md`, `02-open-realtime-s2st.md`, `05-player-audio-integration.md`, `07-byok-security-tv-ux.md`, `08-feasibility-economics.md`
- Audits: `docs/ai-media-research/audits/`
- Live translation receipt: `docs/ai-media-research/benchmarks/2026-08-29-qwen-live-translate.json`
- Architecture graph: `graphify-out/graph.json`
- Main build: `./gradlew :app:assembleFullDebug`

Before edits, reconcile current HEAD and re-run live official-provider checks for the exact APIs/models selected. Record Java, Gradle, SDK, device, Nuvio Media3 AAR and bundled mpv versions. The research host had no discoverable Android build toolchain, so every Android claim must be validated during this mission.

## Hammersmith operating contract

1. Keep one stable `run_name`, suggested `nuvio-translated-voice-overlay`, across all rounds.
2. Start `hammersmith hud` and review artifacts at `http://127.0.0.1:8700`. Never open Hammersmith.app.
3. Before the first run inspect configured engines, then run:
   - `hammersmith models --task-type code-feature`
   - `hammersmith models --task-type code-review`
   - `hammersmith models --explore --task-type code-feature`
   - `hammersmith catalog --changes`
4. Ask Francesco to select from current model evidence. At brief creation, `codex` `gpt-5.6-sol` high/xhigh had the strongest recorded code-feature evidence: 22 tasks, 72.7% raw pass, and 94.1% adjusted pass at xhigh after infrastructure/contract exclusions. Recheck before execution. Never delegate to an old or superseded model.
5. Read `/Users/ffrappo/works/repos/hammersmith/templates/README.md`. Compose:
   - `review-swarm` for baseline/player and threat analysis;
   - `repo-feature` for isolated implementation lanes;
   - `bakeoff` plus `research-with-proof` for provider/corpus tests;
   - `test-hardening` for independent test ownership;
   - `adversarial-review` before each integration milestone;
   - `probe` for mpv/Media3 binary behavior and provider invoice proofs.
6. Review before fix. Workers do not self-approve findings.
7. Specs are self-contained, state exact owned files, forbidden areas, commands, artifacts and checks.
8. Checks execute real behavior and print precise failure causes. A report-file existence check is invalid.
9. Concurrent code workers use isolated worktrees with disjoint ownership. One integration owner applies reviewed patches.
10. Hammersmith owns recovery and retries. Do not manually take over active runs unless it asks or Francesco directs.
11. Commit each coherent milestone. No production deployment is part of this mission.
12. New or modified source files stay at or below 400 lines. Split by responsibility.

## Non-negotiable architecture

### This is a native media-transform provider

Do not implement live translated voice as:

- a standard Stremio `dub` resource;
- a native JS `getStreams` extension;
- a CloudStream DEX callback;
- a subtitle URL hack;
- a growing progressive file attached separately to both engines;
- microphone or Android MediaProjection capture.

Current extension contracts cannot securely access selected decoded audio or provide lifecycle-aware external audio. Same-process DEX cannot receive app-managed BYOK.

### Two honest product phases

#### Phase A: finalized-artifact attachment pilot

- Provider workflow yields a completed, seekable audio artifact and metadata.
- The provider does not receive Nuvio playback credentials.
- Host ingests by file descriptor/content grant into bounded app-private storage.
- Host serves it to MPV from a random tokenized `127.0.0.1` URL.
- Nuvio exposes upstream mpv `audio-add`, `audio-remove` and `audio-reload` through a tested adapter.
- Original audio remains instantly selectable.
- This validates attachment, track identity, delay, seek, fallback, lifecycle, TV UX and telemetry.

It does not validate live source acquisition or near-real-time generation.

#### Phase B: near-real-time clear-VOD prototype

The translated provider needs original source audio while translated output is selected. Implement and prove one independent acquisition design:

1. provider-owned independent lawful source access;
2. host-owned second demux/decoder path; or
3. host-owned local proxy/remux pipeline.

The target transport is tokenized localhost HLS or DASH with original video and alternate generated audio aligned to one source-time grid. A progressive file with uncertain append/seek behavior is outside the target design.

### Shared host components

- `MediaTransformProviderRegistry`: package and signing digest identity, exact capability/version and flavor policy.
- `MediaCapabilityGrantStore`: session/profile-generation/episode/audio-track/provider/operation/destination scoped grants.
- `CredentialBroker`: profile-local encrypted provider key and operation-based calls, no plaintext getter.
- `MediaTransformSessionCoordinator`: one generation and seek-epoch authority.
- `ExternalAudioDescriptor`: stable key, generation, target language, label, MIME/container, media-time origin, duration, coverage, completion, seekability and hash.
- `DubPlaybackCoordinator`: attachment, selection, fallback, delay and reattach state.
- `LocalMediaArtifactServer`: loopback-only tokenized artifact/segment service with no primary header inheritance.
- Later `DubSessionProxy`: source acquisition, provider session, timestamp mapping, segment ring, correction and metrics.

### Provider API contract

The provider receives only fields required for its declared mode:

- provider identity and capability version;
- active profile generation and opaque credential operation grant;
- content/episode identity and non-secret fingerprint;
- source and target language;
- voice mode and user-approved cloning policy;
- generation, seek epoch and source media time;
- audio frames or host-owned source windows only after the independent source path is implemented;
- pause, resume, seek/discontinuity, cancel and end.

Provider output includes:

- status, progress, cost units and recoverable error codes;
- audio chunks tagged with exact source media time and generation, or a completed artifact FD;
- target sample rate/channels/encoding;
- coverage range, duration, integrity and completion/seekability;
- translated transcript only when explicitly enabled and bounded.

Never send stream URL, cookies, Authorization, debrid/torrent URL, license data or original file descriptor to an untrusted provider.

### Credential/network boundary

- Dedicated provider clients use platform TLS validation.
- No current trust-all client may be inherited.
- AI BYOK is profile-local Android Keystore AES-GCM with AAD bound to install, profile generation, provider and credential record.
- No AI credential sync, URL embedding, QR payload value, SavedState, backup, log, Sentry or clipboard.
- Current DEX has no broker access. If DEX can execute in the credential process, selected launch flavor/policy must fail BYOK closed.
- Telemetry stays disabled for beta until a server-side canary proves redaction.
- Voice cloning requires an explicit, provider-specific user action and a testable consent/data policy. Begin with licensed stock voices unless Francesco explicitly chooses cloning for the pilot.

## Provider pilot order

No provider is selected for public release.

### Direct player/media-worker sessions

1. **Qwen3.5 LiveTranslate Flash Realtime**
   - First pilot because it is the only direct S2ST candidate already smoke-tested with an available key.
   - Official mechanics: 60 text outputs, 29 spoken outputs, tentative plus confirmed text, incremental audio, hotwords, optional visual context and clone modes.
   - International pricing: 7 input audio tokens/s at $7.50/M and 12.5 output tokens/s at $30/M.
   - Equal input/output estimate: $0.02565/source min.
   - Existing 9.814 s smoke test yielded first audio at 2.557 s from probe start with fixed voice and 4.578 s in a confounded clone/source-ASR configuration. Generated durations were 14.96 and 15.36 s, proving duration mismatch.
2. **OpenAI GPT-Realtime-Translate**
   - Dedicated WS/WebRTC translation and short-lived client secret.
   - Estimated $0.034/min.
   - Official cookbook says over 70 input and 13 output languages. Runtime pair matrix still required.
3. **Gemini 3.5 Live Translate Preview**
   - 70+ languages, constrained ephemeral token, about $0.0368/min.
   - Preview and documented long-pause/gender/multi-speaker voice problems are release risks.
4. **CAMB realtime S2S**
   - Direct socket and selectable cloned voice.
   - Beta, 14 listed realtime languages, unknown public unit cost.
5. **Palabra and DeepL Voice**
   - Voice-preserving or secure/reconnect specialist controls.

### Managed broadcast control

CAMB Streaming leads for SRT input plus SRT/RTMP/HLS output. Deepdub Live and Palabra Broadcast are controls. These services are a separate architecture from direct player-side S2ST.

### Batch whole-title control

CAMB End-to-End, Dubformer, Rask, Sarvam and Deepdub Managed belong in asynchronous whole-title evaluation. They must not be called progressive playback services.

## Required implementation milestones

### Milestone 0: baseline and binary proof

- Build/test baseline on a recorded Android toolchain.
- Query actual mpv version and command/property support in `mpv-android-lib:0.1.12`.
- Record exact local Media3 AAR identities and API behavior.
- Create executable mpv command-adapter tests before changing UI.
- Establish owned clear test fixtures with reference source/target transcripts and impulse markers.
- Commit receipts and test harness.

### Milestone 1: shared secure provider foundation

- Typed native provider registry and capability/version handshake.
- Credential vault, dedicated TLS client, destination allowlist, grants, profile generation and cleanup.
- Fake translated-audio provider and deterministic artifact.
- Log/Sentry canary and DEX exclusion policy.
- Commit.

### Milestone 2: MPV finalized-artifact attachment

- `ExternalAudioDescriptor` and stable generated track identity.
- Loopback artifact server with random token, no LAN bind and no primary headers.
- MPV `audio-add/remove/reload` adapter, track-list observation, select, delay and teardown.
- Original-audio fallback on every auxiliary error.
- 100 attach/select/seek/remove cycles with FD/memory checks.
- Commit.

### Milestone 3: TV UX pilot

- Provider connection under **AI media providers** Settings.
- Device authorization where provider supports it. Raw phone key entry uses official HTTPS E2EE, never NanoHTTPD.
- Audio overlay row/card for **Translated voice overlay** with source/target language, provider, status, coverage and effective delay.
- States: Preparing, Generating, Ready, Recovering, Original fallback, Failed.
- Explicit Start/Stop/Original actions.
- Manual base delay reuses current controls but is separate from runtime correction.
- Stable focus/IDs and truthful engine limitations.
- Commit.

### Milestone 4: Qwen and common provider bake-off

- Rebuild the benchmark harness with retained redacted raw events, source SHA-256, exact payload, SDK lock, request IDs, response hashes, monotonic clock and usage record.
- Run controlled clone ablation: hold voice/payload/options constant and cross clone off/once/always independently with source ASR off/on.
- Run Qwen, OpenAI, Gemini and CAMB on identical clips and regions where possible. Add Palabra when voice preservation is a requirement.
- Measure setup, first provisional text, first stable text, first packet, first playable audio, p50/p95 source-to-playback lag, end flush, duration ratio, failure/reconnect and invoice.
- Do not choose a winner from vendor marketing metrics.
- Commit benchmark artifacts and provider adapter selected for next phase.

### Milestone 5: independent source acquisition

Prototype at least two feasible approaches before selecting:

1. host-owned second demux/decoder, preserving the original audio path while generated audio is selected;
2. localhost proxy/remux that reads clear source using host-owned credentials and sends only normalized PCM to the provider.

Reject any design that:

- exposes primary headers to provider/auxiliary origin;
- blocks playback audio thread;
- cannot regenerate an arbitrary seek window;
- lacks source-time PTS per chunk;
- transcribes its own generated output;
- relies on DRM decryption or hidden capture.

Record CPU, PSS, network duplication, startup and seek recovery on low/mid/high TV classes. Commit the selected design and rejected-prototype evidence.

### Milestone 6: adaptive local transport and sync

- Publish tokenized loopback HLS/DASH with original video and alternate generated audio on one source-time grid.
- Atomic complete segments, bounded time/byte ring, no rewriting advertised segments.
- Initial/gap padding, crossfade back to original, seek generation and coverage map.
- Separate delay terms: route/user + provider base + runtime correction.
- Estimate phase error from provider/source timestamps.
- Apply bounded tempo correction, crossfaded insert/drop and hard resync by tested thresholds.
- Reopen same proxy/session on engine failover and restore by stable generated key.
- Commit.

### Milestone 7: Media3 and broader engine gates

- Add finalized-sidecar `MergingMediaSource` only after tests against Nuvio's exact AARs.
- Independent headers/data sources, filtered primary video/text plus sidecar audio, `clipDurations=false`, tested offset/period/duration constraints.
- Auxiliary errors never fail the primary source.
- Live adaptive transport on Media3 only after HLS/DASH fixture tests pass.
- External players receive generated audio only through a single finalized mux/adaptive URL; otherwise launch original after explicit consent.
- Commit.

### Milestone 8: quality, release and final review

- Run long-form common corpus, separation/remix evaluation and billing reconciliation.
- Test seek, pause, 0.75×/1×/1.5×, route change, engine failover, network faults, provider crash, episode switch, process death and cancellation.
- Run adversarial security, playback/sync, UX/accessibility and cost reviews.
- Keep public dubbing disabled unless all release gates pass.
- Update decision docs, Graphify, Honcho and Obsidian.
- Commit final evidence and code.

## Source audio and remix quality boundary

A translated voice track over original mixed audio is not automatically a dub. The mission must test and choose one of:

1. dialogue-isolated source replacement with music/effects retained;
2. provider-supplied music and effects stem plus translated dialogue;
3. original ducking under translated voice, explicitly labeled overlay;
4. whole-title provider output with verified mix.

Measure:

- original-language speech bleed;
- music/effects damage;
- speaker overlap behavior;
- loudness and true peak;
- voice identity and speaker attribution;
- omissions/additions/proper nouns/profanity;
- lip/timing alignment;
- user intelligibility preference.

Until dialogue isolation and mix quality pass, keep the label **Translated voice overlay**.

## TV interaction specification

### Settings

- **AI media providers** lists provider, masked last four, active profile, state, last tested, languages, estimated unit price and hard cap.
- Phone setup preferred. TV fallback is password input with voice dictation and suggestions disabled.
- No reveal/export UI in the first release.
- Disconnect distinguishes local deletion from provider revocation.

### Playback

- Add the generated track to the existing audio option rail with a stable key and generated origin.
- Right rail shows target language, provider, voice mode, status, coverage and effective delay.
- Buttons remain one line: **Start translated voice**, **Use original audio**, **Stop generation**, **Retry**.
- Playback continues on original audio while preparing or recovering.
- Gaps crossfade to original and state says **Original fallback**.
- Seek immediately increments generation, cancels obsolete work and keeps original until target coverage is playable.
- Closing the overlay never hides active privacy/cost state.
- Episode and source switches never reuse a stale descriptor or delay blindly.

## Acceptance checks

### Security

1. Provider traffic fails under untrusted CA/hostname mismatch.
2. Current JS/DEX cannot obtain key, grant, PCM, playback URL/header or artifact FD.
3. Wrong signer/profile generation/session/episode/audio track/destination, expiry and replay fail closed.
4. Canary secret is absent from every store, log, Sentry event, support export and generated artifact.
5. Primary Authorization/Cookie/Host/userinfo/query credentials never reach provider, auxiliary host or loopback logs.
6. Protected fixtures produce zero provider calls and bytes.
7. Voice cloning requires recorded explicit consent and provider-specific deletion/revocation behavior.

### MPV artifact pilot

1. Verify `audio-add/remove/reload` on actual bundled mpv.
2. Local AAC/M4A and every claimed format attach, select and seek at start/middle/backward/near-EOS.
3. Delay survives seek and route reload.
4. 401/404/corruption/short audio fail to original without stopping video.
5. 100 cycles leave no growing FD, native heap, task or artifact leak.
6. Source/episode switch removes the old generation before loading the next source.

### Near-real-time sync

1. Independent source acquisition continues while translated audio is selected.
2. First-playable and steady-state p50/p95 lag are measured from source media time, not socket receipt.
3. On known markers, median absolute sync error ≤80 ms and p95 ≤150 ms after settling, or a formally reviewed replacement threshold.
4. No cumulative drift outside threshold over three 22-minute episodes and one 100-minute title.
5. Seek at 5, 30 and 60 minutes returns to generated audio within a product-defined bound with no stale packets.
6. Gaps and reconnects do not stall video, repeat speech or expose silence as success.
7. Provider output/source duration ratio and correction magnitude remain within quality bounds established by listening tests.

### Quality and economics

1. Bilingual human review covers omissions, additions, proper nouns, profanity and speaker attribution.
2. Voice similarity is measured separately from translation quality.
3. Dialogue bleed, music/effects damage, loudness and true peak pass the selected mix specification.
4. Exact pair catalog, session duration, concurrency and region are recorded.
5. Invoice agrees with locally metered provider units within 10% for at least 95% of jobs.
6. Hard cap is enforced before scheduling each billable chunk or shard.
7. Cost per completed playable minute includes output duration expansion, retries, cancellations, storage, egress and failed work.

### UX and lifecycle

1. D-pad/RTL traversal and focus remain stable under status/coverage updates.
2. Back closes without implicit cancellation; Stop is explicit.
3. Original audio remains usable throughout every failure.
4. Auxiliary failure never consumes primary retry or engine-failover budget.
5. Pause/stop/profile/source/episode/release revoke before callbacks can attach.
6. UI states never claim Ready while target coverage is absent.

## Hammersmith task and check shape

Use `repo-feature` worktrees for code with exact owned paths. Checks combine focused/integration builds, executable player or provider fixtures, secret and network scans, source line-count checks, git allowlists and exact result notes. Use `bakeoff` for identical provider/corpus cells. Before integration, run read-only reviewers for credential isolation, source acquisition, sync/failover and TV UX. The integration owner reproduces every P0/P1 finding.

## Explicit non-goals until separately accepted

- Public actor-faithful AI dubbing claim.
- DRM/protected media, live TV and hidden capture.
- Current JS/DEX provider integration.
- Custodial BYOK, secret sync, device/household scope.
- Background paid work after leaving playback.
- Growing progressive file as a cross-engine live transport.
- External-player live sidecar parity.
- Silent loss of Dolby/bitstream mode.

## Completion conditions

The engineering program is complete only when:

- the finalized-artifact MPV pilot and selected near-real-time architecture both have code, tests and device receipts;
- independent source acquisition is proven while translated output is selected;
- every acceptance gate maps to real artifacts;
- Full Debug builds on the recorded Android toolchain;
- common-corpus and long-form provider evidence exists;
- cost is invoice-reconciled;
- the public feature remains disabled if separation/remix or launch gates fail;
- every milestone has a coherent commit;
- `graphify update .` succeeds or its integrity issue is documented and resolved;
- durable repo facts and operational decisions are stored in Honcho and Obsidian;
- the final working tree is clean and no required Hammersmith issue remains.

A successful short Qwen socket call, an MPV local audio attachment or passing synthetic tests is progress, not completion. Unknown release-gate evidence keeps the mission active or moves it to explicit `input-required`.
