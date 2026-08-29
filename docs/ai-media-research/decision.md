# AI media transformation architecture decision

**Decision date:** 2026-08-29  
**Code baseline:** `eca648a8`  
**Fresh evidence window:** 2026-07-31 through 2026-08-29 inclusive  
**Scope:** research and implementation architecture for generated dialogue subtitles and translated voice in NuvioTV. The excluded vendor does not appear in the decision set.

## Decision status

**Generated dialogue subtitles:** conditional implementation go.

**Near-real-time translated voice:** research and engineering pilot go. Public AI dubbing remains a no-go until source acquisition, dialogue isolation/remix, long-form sync, seek recovery and provider cost pass objective gates.

**Completed translated-audio attachment:** useful internal player pilot, clearly labeled as a translated voice overlay. It is not evidence that the active stream can be dubbed live.

## Product boundary

Build two independently installable logical providers backed by one small native host capability layer:

1. **Generated Dialogue Subtitles Provider**
   - ExoPlayer-only live input for the first implementation.
   - Receives normalized selected-track PCM with media-time anchors, generation and seek epoch.
   - Emits provisional/final cue revisions into a host-owned mutable sidecar renderer.
   - Can export a finalized VTT/SRT snapshot for MPV or external players.

2. **Translated Voice Provider**
   - First input mode is provider-owned or app-owned clear media acquisition that does not expose playback credentials to an extension.
   - First output mode is a completed, seekable audio artifact attached to MPV through a tokenized loopback URL.
   - Near-real-time output is a later local HLS/DASH or remux session proxy with source-time anchors, bounded buffering, seek generations and drift correction.

These providers are **not** ordinary Stremio resources, native JS scrapers or CloudStream DEX extensions. Existing extension protocols are discovery and URL-return mechanisms. They do not have a secure post-selection PCM, mutable cue, external-audio, seek, cancellation or grant contract.

## Why this split is required

### Current ecosystems cannot own live media transforms

- Stremio subtitle resources can return completed `{id,url,lang}` files. They cannot observe active playback or push revisions.
- Native JS exports only `getStreams(...)` and its output parser returns stream records.
- CloudStream DEX runs arbitrary same-process code and currently drops collected subtitle callbacks from `LocalScraperResult`.
- Neither player has a first-class external-audio domain model.

### Player input and output seams differ

- ExoPlayer has a promising decoded PCM seam through Nuvio's custom audio sink, but a raw `AudioProcessor` callback has no authoritative PTS and may stop seeing original audio once a generated track is selected.
- MPV has the shortest output seam through upstream `audio-add`, but Nuvio has no decoded-audio callback in its wrapper.
- Therefore an Exo PCM subtitle MVP and MPV finalized-audio pilot are coherent. Combining them into a claimed live dubbing MVP is not.

### Robust live dubbing needs an independent source timeline

A sustained translated-audio session needs original source audio even while the dub is selected. This requires one of:

1. a provider that independently and lawfully acquires the clear source;
2. a host-owned second decoder or demux path; or
3. a host-owned proxy that transforms/remuxes the source and serves a standards-native adaptive result.

A single currently selected audio renderer cannot supply that requirement safely.

## Shared host layer

### Required components

- `MediaTransformProviderRegistry`: package plus signing-digest identity, capability/version negotiation and exact flavor policy.
- `MediaCapabilityGrantStore`: unforgeable session/profile-generation/episode/audio-track/provider scoped grants with monotonic expiry.
- `MediaTransformSessionCoordinator`: one generation and seek-epoch authority called by every committed seek, source switch, episode switch, engine failover and release.
- `CredentialBroker`: profile-local encrypted provider credentials and operation-based provider calls. No plaintext getter.
- `PcmTapAudioProcessor`: fixed-capacity, non-blocking capture branch paired with host timing/discontinuity anchors.
- `MutableTimedCueStore`: revision- and epoch-aware time index with bounded event/text rates and active-cue queries.
- `ExternalAudioDescriptor`: stable key, generation, language, format, source-time origin, duration, integrity, coverage, seekability and completion.
- `LocalMediaArtifactServer`: loopback-only tokenized transport with no primary header inheritance.

### Security invariants

1. No current Stremio, JS or DEX extension receives AI keys, grant strings, playback URLs, cookies, headers, DRM data or raw source file access.
2. Same-process DEX is excluded from BYOK. Keystore encryption does not create an in-process confidentiality boundary.
3. Provider networking uses a dedicated client with platform TLS validation. It cannot inherit Nuvio's current trust-all clients.
4. AI credentials are profile-local for the first release, encrypted with Android Keystore AES-GCM and AAD bound to installation, profile generation, provider and record ID.
5. Credentials never enter provider sync, addon URLs, navigation args, logs, Sentry, clipboard or generated artifacts.
6. Raw PCM and provisional cues remain memory-only by default. Final artifacts are bounded, profile-scoped and user-clearable.
7. Auxiliary failure never fails primary video. Original audio and existing subtitles remain available.
8. Known encrypted HLS/DASH/DRM inputs cause zero provider calls.

## Provider decisions

No provider has launch confidence. Rankings below are pilot order.

### Direct live translated audio

1. **Qwen3.5 LiveTranslate Flash Realtime:** first pilot. It is the only direct S2ST API successfully smoke-tested in this work. Official mechanics include confirmed plus tentative translated text, incremental audio, hotwords, optional video and clone modes. It supports 60 text outputs and 29 spoken outputs.
2. **OpenAI GPT-Realtime-Translate:** integration fallback. It has a dedicated translation session, WebRTC/WS, short-lived client secrets and an estimated $0.034/min price.
3. **Gemini 3.5 Live Translate Preview:** broad-language fallback. It supports 70+ languages and constrained ephemeral tokens at about $0.0368/min, but preview and documented voice behavior are meaningful risks.
4. **CAMB realtime S2S:** direct-socket specialist. It needs the same corpus and price test.
5. **Palabra and DeepL Voice:** specialist controls for voice preservation or secure reconnect semantics.

### Managed broadcast packaging

1. **CAMB Streaming** for native SRT ingest and SRT/RTMP/HLS output.
2. **Deepdub Live** as the early-access challenger.
3. **Palabra Broadcast** as the voice/protocol specialist.

### Live generated dialogue subtitles

1. **Qwen Audio 3.0 ASR Flash Streaming:** trial leader because one successful test returned sentence and word timestamps. International list price is $0.0054/audio minute.
2. **Deepgram Nova-3:** production-oriented paid control.
3. **Gemini 3.5 Transcribe Live:** language and code-switching control, with ten-minute session rotation and no live word timestamps.
4. **AssemblyAI streaming:** latency-claim control.

### Ahead-of-playback and final subtitles

1. **Cloudflare Whisper Large V3 Turbo:** cheapest measured baseline at the reviewed model-page price of $0.00051/audio minute. One 7.898 s clip finished in 3.875 s.
2. **Groq Whisper Large V3 Turbo:** low-cost high-throughput control.
3. **Qwen Audio 3.0 ASR Flash Filetrans:** long-title control, 12 hours/2 GB, optional diarization, $0.0021/min international list price.
4. **Gemini 3.5 Transcribe batch:** broad-language, word timestamp and diarization control.
5. **WhisperX/faster-whisper/Voxtral:** open and self-hosted controls.

## Measured evidence

### Qwen live translation smoke test

Source: one synthetic English clean-speech clip, 9.814 s, streamed in 100 ms PCM frames to Singapore.

| Configuration | First audio from probe start | First audio after session update | Generated duration | Estimated usage cost |
|---|---:|---:|---:|---:|
| Fixed Tina voice, source ASR off | 2.557 s | 1.305 s | 14.96 s | $0.00613 |
| Default voice, clone once, source ASR on | 4.578 s | 3.458 s | 15.36 s | $0.00628 |

The configurations changed three variables, so the difference is **not** an isolated clone penalty. The generated/source duration ratios were 1.52 and 1.57. This proves the API can stream translated audio, and proves that it does not intrinsically duration-match video.

At the observed output ratios, current pricing estimates are about $0.0374 to $0.0384 per source minute, $0.82 to $0.84 for 22 minutes and $3.74 to $3.84 for 100 minutes, before optional output text, retries and images.

### Subtitle probes

- Qwen Audio 3.0 streaming: 7.898 s source, first transcription event 1.704 s, final event 8.711 s, exact synthetic text, sentence and word times.
- Cloudflare Whisper Large V3 Turbo: same source, 3.875 s complete REST wall time, RTF 0.491, exact synthetic text, segments and word times.
- Cartesia Ink-2: authenticated handshake reached billing enforcement and returned HTTP 402. No latency or quality result exists.

These are transport smoke tests, not a film/TV quality bake-off.

## Television UX

### Settings

- Add **AI media providers** under integrations.
- Connect on phone through provider device authorization when available. If raw-key phone entry ships, use an official HTTPS E2EE handoff, not Nuvio's NanoHTTPD LAN server.
- TV confirmation shows provider, active profile, what leaves the TV, billing owner and hard cap.
- Advanced model, retention, language and cost details live in Settings, not the playback confirmation.

### Generated dialogue subtitles

- Append a stable **Generate dialogue captions (beta)** option to the existing subtitle option rail.
- Setup rail: selected audio, spoken language, subtitle language, provider and cost summary.
- Explicit **Start generating** action. Missing subtitles never auto-start capture.
- Stable state labels: Preparing, Listening, Repositioning, Paused, Live, Complete, Failed.
- Cue arrivals never change option identity, reorder rails or steal focus.
- Back closes the overlay without cancellation. Stop is explicit.
- If MPV is active, offer **Switch player and continue** with position preserved.

### Translated voice

- Use the existing audio overlay geometry and delay controls.
- Show source and target language, provider, readiness/coverage, effective delay and explicit original-audio fallback.
- Public copy says **Translated voice overlay** until objective dubbing quality gates pass.
- Never hide a gap behind silence. Surface generating/recovering/fallback state.

## Delivery sequence

### Shared foundation

1. Fix dropped stream-provided subtitles and structured subtitle lookup errors.
2. Harden OpenSubtitles hash Range validation.
3. Add dedicated provider networking, credential vault and redaction/telemetry policy.
4. Add typed native provider discovery/contracts and session coordinator.
5. Implement provider fakes before real provider calls.

### Subtitle build

1. Refactor static sidecar rendering behind a time-indexed cue source.
2. Add generation/revision/epoch reducer and fake cue provider.
3. Add Exo PCM tap, timing anchors and explicit capture-active PCM policy.
4. Add Qwen streaming adapter first, then a common-corpus provider bake-off.
5. Add batch completion/correction providers and finalized export.
6. Add MPV live capture only after a tested native seam exists.

### Voice build

1. Add external-audio descriptor and MPV `audio-add/remove/reload` wrapper.
2. Validate completed local artifacts, selection identity, delay, seek, fallback and teardown.
3. Build a Qwen direct-session prototype against owned clear fixtures, but keep playback source and generated output separate.
4. Prototype an independent source decoder or local adaptive proxy.
5. Add long-form sync and drift correction.
6. Add Media3 finalized merge only after exact local-AAR tests pass.
7. Consider public release only after separation/remix and provider bake-off gates pass.

## Acceptance gates

### Common

- No provider credential, playback secret, PCM or transcript canary in Logcat, Sentry, support exports, cache names or navigation state.
- Wrong signer, profile generation, episode, audio track, expired grant and replay fail closed.
- Pause, stop, seek, profile switch, episode switch and release stop new frame delivery and reject late callbacks.
- Known protected fixtures send zero bytes to providers.
- D-pad focus remains stable under high-frequency updates.
- Android app build and unit/instrumentation suites pass on configured toolchains; all modified source files remain at or below 400 lines.

### Subtitles

- First stable cue p50 at most 3 s and p95 at most 6 s on the owned media corpus.
- Cue onset median error at most 250 ms, p95 at most 750 ms, and drift under 1 s per 20 minutes.
- Clean-dialogue WER at most 15%, with music and overlap reported separately.
- No playback underrun from a slow provider and no unbounded cue or PCM memory growth.
- Seek and episode epochs cannot contaminate each other.

### Translated voice

- Common-corpus p50/p95 first-playable and steady-state lag are measured, not inferred from first packet.
- Output/source duration, p50/p95 sync error, gaps, repeats, reconnect loss and cost are recorded for three 22-minute episodes and one 100-minute title per finalist.
- Proposed sync target is median absolute error at most 80 ms and p95 at most 150 ms after settling.
- Original audio fallback is continuous and auxiliary failures do not consume primary retry/failover budget.
- The source audio acquisition path continues while translated audio is selected.
- Provider bill agrees with locally metered units within 10% for at least 95% of jobs.

## Explicit non-goals for first public beta

- Current JS or DEX extension access to BYOK or PCM.
- DRM, protected streams, live TV and microphone/MediaProjection fallbacks.
- Secret sync, custodial BYOK, household/admin roles or device-wide grants.
- Background whole-title continuation after leaving playback.
- External-player live generation.
- A public claim of actor-faithful dubbing.

## Evidence and limitations

Detailed evidence lives in:

- `docs/ai-media-research/lanes/`
- `docs/ai-media-research/benchmarks/`
- `docs/ai-media-research/audits/`
- `graphify-out/`

The research used current official sources and a 2026-07-31 to 2026-08-29 freshness window. Parallel Deep Research could not run because the account returned insufficient credit, so official URL discovery and HTTP extraction were used. Real probes were limited to credentials available on the host. No Android build or device run was completed because the research host had no discoverable JDK or Android SDK. No provider currently has green launch confidence.
