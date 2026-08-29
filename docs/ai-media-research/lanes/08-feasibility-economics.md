# Lane 08 , Feasibility and Economics: AI Dubbing and Missing-Caption Generation

**Research cutoff:** 2026-08-29  
**Required publication window reviewed first:** 2026-07-31 through 2026-08-29 inclusive  
**Scope:** challenge two proposed capabilities,AI dubbing and generation of missing captions,for NuvioTV. This is a technical/economic feasibility report, not an implementation specification.

Nuvio can plausibly pilot **generated dialogue captions** for clear, seekable VOD, but it should not promise accessibility-equivalent captions and should not make an arbitrary media URL sufficient proof that a source is processable. A product called “dubbing” is substantially harder: transcription, translation, timing, voice generation, dialogue/background separation, remix, correction, caching, and seek behavior form a stateful media pipeline, not a thin plugin call.

The recommended boundary is a native, brokered, opt-in beta for clear VOD, beginning with 22-minute material and audio-only shards; DRM, live streams, provider-side pulls of header-protected URLs, and unattended whole-title processing on a TV are explicit non-goals. At public rate-card prices, speech/translation/synthesis alone can be below one dollar for many 100-minute jobs, but this figure excludes the technically dominant unknowns: source acquisition, separation/remix, storage/egress, retries, support, payment leakage, and abandoned work.

## Exact-window evidence first: 2026-07-31 through 2026-08-29

Only primary pages whose dated release/update falls inside the required window are used in this section.

| Date | Fact from the window | Relevance and limitation |
|---|---|---|
| 2026-07-31 | Deepgram made model/language changes possible mid-session, added STT latency to its latency report, and imposed a two-hour maximum on Voice Agent sessions; keep-alives do not extend the limit. [Deepgram changelog, 2026-07-31](https://developers.deepgram.com/changelog/2026/7/31) | Dynamic language selection and measured latency are useful, but a two-hour conversational session is not a durable 100-minute media-job protocol. A media job still needs resumable shards and persisted state. |
| 2026-08-05 | Media3 1.11.0 added/fixed several relevant primitives: reduced OOM risk in load control, fragmented-MP4 `mfra` seeking by default, per-stream progression, time-aware audio-processor metadata, and multiple extractor/audio fixes. [AndroidX Media3 1.11.0 release notes](https://developer.android.com/jetpack/androidx/releases/media3#1.11.0) | These changes improve the feasibility of edge extraction and time-based processing. They do **not** prove Nuvio receives them: Nuvio uses local player/extractor AARs plus a stock `media3-database:1.8.0`, and the local AARs’ upstream revision is not declared in the inspected Gradle file (`app/build.gradle.kts:452-478`). |
| 2026-08-12 | Deepgram’s Flux TTS became generally available on REST and WebSocket. The live socket can interrupt an active turn and report `text_spoken` and `text_remaining`; speed is limited to 0.85-1.15 in 0.05 steps; the announced catalog is 36 English voices. [Deepgram changelog, 2026-08-12](https://developers.deepgram.com/changelog/2026/8/12) | Exact interruption accounting is useful for cancel/seek. English-only catalog coverage and the narrow speed range make this release insufficient by itself for multilingual, duration-matched dubbing. |
| 2026-08-14 | Android documents that playback capture requires `RECORD_AUDIO`, one-time user-approved `MediaProjection`, same-profile execution, eligible audio usage, and an effective capture policy that permits capture. The most restrictive policy wins, and `ALLOW_CAPTURE_BY_NONE` prevents even system capture. [Android playback capture](https://developer.android.com/media/platform/av-capture) | Capturing arbitrary playback is not a universal fallback for inaccessible or protected sources. Nuvio should acquire its **own** clear stream before decode or through an explicit in-player tap, not depend on a screen-capture permission flow. |
| 2026-08-27 | Cartesia released Sonic 3.6 with 44 languages/61 locales and a dated production snapshot. Existing voice IDs carry over, and timestamps, codec, speed, volume, and SSML behavior remain as on Sonic 3.5. [Cartesia 2026 changelog](https://docs.cartesia.ai/changelog/2026) | This is a credible multilingual synthesis candidate. It remains TTS, not dialogue separation, translation, timeline fitting, remix, or a complete dubbing service. Pinning the dated snapshot is safer than a moving alias. |
| 2026-08-28 | Deepgram announced Nova-3 quality improvements for ten languages, following several language additions and model updates during August. [Deepgram changelog](https://developers.deepgram.com/changelog) | Language coverage is improving rapidly, which is an opportunity, but model quality is a moving dependency. Acceptance must be corpus-based and model snapshots must be recorded per artifact. |

**Window conclusion , fact:** the window produced meaningful STT/TTS and media-stack improvements, but no release removed the source-access, DRM, separation/remix, durable-job, or cross-flavor blockers.  
**Window conclusion , assumption:** the relevant vendor/API behavior seen on 2026-08-29 will remain available during a pilot.  
**Window conclusion , unknown:** the upstream Media3 revision represented by Nuvio’s local AARs and whether those AARs include the 1.11.0 fixes.

## Key Findings

### 1. URL possession is not media accessibility

**What.** A playable Nuvio source may require request headers, cookies, redirects, range requests, a short-lived URL, debrid resolution, or a local torrent server. A remote AI provider cannot be assumed to reproduce that access merely from the visible URL.

**Evidence.** Nuvio’s stream model carries request and response proxy headers (`app/src/main/java/com/nuvio/tv/domain/model/Stream.kt:181-189`), sanitizes them while intentionally removing caller-supplied `Range` (`app/src/main/java/com/nuvio/tv/data/mapper/StreamMapper.kt:104-122`), and applies the request headers to HLS, DASH, and progressive data sources (`app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerMediaSourceFactory.kt:82-113,197-205`). Authorization is forcibly reattached after cross-host redirects (`app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerPlaybackNetworking.kt:94-117`). By contrast, Deepgram’s public prerecorded URL request schema exposes a required `url` field but no origin-request header map. [Deepgram prerecorded API reference](https://developers.deepgram.com/reference/speech-to-text/listen-pre-recorded)

**So what.** Every job needs an access preflight and an input mode, not just `url`: `PUBLIC_URL`, `SIGNED_QUERY_URL`, `HEADERED_URL`, `LOCAL_HTTP`, `TORRENT`, `UPLOADED_AUDIO`, or `DRM_BLOCKED`. Header-bearing URLs should be fetched by a trusted Nuvio component and reduced to audio shards; raw playback credentials must not be handed to an unrelated speech provider.

### 2. DRM is a hard product boundary, not an expected fallback path

**What.** Supporting DRM playback and extracting reusable audio are separate capabilities. Encrypted media requires a configured DRM system/license exchange, and capture may be prohibited by effective audio-capture policy.

**Evidence.** Media3 requires the DRM UUID and media-item DRM properties for protected playback; it supports scheme/format combinations such as Widevine CENC for DASH/HLS and PlayReady on Android TV. [Media3 DRM guide](https://developer.android.com/media/media3/exoplayer/drm) Nuvio’s `PlayerMediaSourceFactory` creates a `MediaItem` with URI, MIME type, subtitles, and metadata, but never sets `DrmConfiguration` (`app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerMediaSourceFactory.kt:96-113`); a repository search found no media-item DRM/license configuration. Android also states that `ALLOW_CAPTURE_BY_NONE` means audio is not recordable by any app, including a system app. [AudioAttributes capture policy](https://developer.android.com/reference/android/media/AudioAttributes#ALLOW_CAPTURE_BY_NONE)

**So what.** The first release must reject encrypted manifests/segments **before** any paid request. “Playback succeeded” must not be treated as permission or technical proof that plaintext audio can be exported. Whether Nuvio can tee PCM after a secure decoder without violating device/security constraints is an **unknown** and is not a pilot dependency.

### 3. Missing-dialog captions are feasible sooner than accessibility captions

**What.** Clear VOD can be demuxed to audio, transcribed in shards, converted to SRT/WebVTT, and hot-attached without reloading playback. That does not automatically produce speaker attribution, music/effect descriptions, forced narrative, or edited SDH captions.

**Evidence.** Deepgram returns word start/end timestamps and confidence, accepts remote or uploaded prerecorded media, and documents a 2 GB file limit plus 10-minute processing-time timeout for synchronous Nova/Base/Enhanced requests. [Deepgram prerecorded audio](https://developers.deepgram.com/docs/pre-recorded-audio) Google explicitly supports generation of SRT and WebVTT from speech recognition. [Google caption output](https://docs.cloud.google.com/speech-to-text/docs/caption-support) Nuvio already has a buffer-preserving sidecar path that downloads and parses external captions, cancels superseded work, and renders against current player time without reloading the media source (`app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerSidecarSubtitles.kt:26-39,84-175,178-200`).

**So what.** The honest initial label is **“Generated dialogue captions (beta)”**. Do not call the output “closed captions,” “SDH,” or an accessibility replacement until non-speech event coverage, speaker changes, reading speed, line breaks, and human correction pass dedicated tests.

### 4. Dubbing is a media-production pipeline, not captions plus TTS

**What.** A minimally useful dub needs source transcription, translation, utterance segmentation, duration fitting, voice selection, dialogue isolation or ducking, synthesis, remix, loudness control, timeline anchoring, retries, correction, and cache invalidation. TTS alone leaves original-language dialogue audible or destroys music/effects if the whole source track is replaced.

**Evidence.** Deepgram’s current TTS request limit is 2,000 characters and its own latency guide recommends sentence-aware chunking; splitting inside a sentence can sound choppy. [Deepgram TTS latency](https://developers.deepgram.com/docs/text-to-speech-latency) Azure caps real-time TTS output at ten minutes per request and supports longer asynchronous jobs with separate lifecycle limits. [Azure Speech quotas](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/speech-services-quotas-and-limits) Nuvio’s existing `AudioDelayMediaSource` shifts timestamps; it does not expose, isolate, replace, or remix PCM (`app/src/main/java/com/nuvio/tv/ui/screens/player/AudioDelayMediaSource.kt:1-129`).

**So what.** The first audio pilot should be named **“translated voice overlay”** or **“translated narration”**, use generic voices, and duck the original audio. “Dubbing” should remain gated on measured dialogue-bleed and remix quality. Voice cloning, lip sync, songs, overlapping speakers, 5.1 object preservation, and live dubbing are outside the first boundary.

### 5. TV-side demux is useful; TV-side AI is not established

**What.** Demuxing compressed audio at the TV avoids uploading the entire video and preserves access to local/torrent/headered sources. The inspected app has media parsers and software audio decoders, but no on-device ASR, translation, TTS, source separation, or PCM export pipeline.

**Evidence.** Nuvio packages local Media3 player/extractor AARs, FFmpeg audio decoding, libass, MediaInfo, and mpv (`app/build.gradle.kts:452-496`). Its torrent path starts a local TorrServer, resolves metadata for up to 15 seconds, and exposes a loopback HTTP stream (`app/src/main/java/com/nuvio/tv/core/torrent/TorrentService.kt:49-89,120-135`; `app/src/main/java/com/nuvio/tv/core/torrent/TorrServerApi.kt:145-148`). The manifest marks a microphone as optional and requests audio recording, but contains no AI-media worker or data-transfer foreground service (`app/src/main/AndroidManifest.xml:13-29,73-81`).

**So what.** Use the TV as an **access-and-demux edge**, not as the assumed inference host. Device thermal headroom, demux speed, multichannel downmix quality, storage, and decoder contention must be measured across low-end TV hardware. A loopback gateway solves internal access to a torrent; it does not make `127.0.0.1` reachable by a cloud provider.

### 6. Secrets, lifecycle, and flavors prevent a simple downloadable-plugin design

**What.** Direct provider access puts a durable provider credential in an untrusted client/runtime. Long jobs outlive a player coroutine. Moreover, Nuvio’s Play Store flavor disables local plugins entirely.

**Evidence.** The full flavor enables plugins, while the Play Store flavor disables them and custom server connections (`app/build.gradle.kts:152-172`); its `PluginManager` is a no-op stub (`app/src/playstore/java/com/nuvio/tv/core/plugin/PluginManager.kt:12-74`). Stream URLs and raw header values are persisted in profile DataStore when last-link reuse is enabled (`app/src/main/java/com/nuvio/tv/data/local/StreamLinkCacheDataStore.kt:42-75`). Releasing the player cancels subtitle and playback jobs and stops a torrent (`app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerLifecycle.kt:11-18,31-63`). Android warns that background work longer than ten minutes is highly likely to be interrupted and recommends breaking it into smaller tasks or using an appropriate foreground service. [Android background-task guidance](https://developer.android.com/develop/background-work/background-tasks)

**So what.** Treat both capabilities as native feature surfaces backed by a durable job API, not as ordinary scraper plugins. The client should receive a short-lived, scope-limited job token; vendor keys stay at the broker. A user-supplied-key mode can be an advanced full-flavor experiment, but it is not a secure default and does not solve Play Store parity.

## Facts, assumptions, and unknowns

### Established facts

1. Nuvio can apply arbitrary non-empty playback headers, follow redirects, and issue range requests through its media stack (`app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerPlaybackNetworking.kt:67-140`).
2. The current subtitle domain model contains `id`, `url`, language, and addon identity,but no subtitle-specific headers, expiry, content hash, generated status, revision, or job ID (`app/src/main/java/com/nuvio/tv/domain/model/Subtitle.kt:6-18`).
3. Subtitle downloads forward current stream headers only when subtitle and video share a host, then retry; foreign-host header forwarding is deliberately avoided (`app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerSubtitleTiming.kt:196-263`).
4. Addon subtitle lookups run in parallel with a 20-second timeout per addon (`app/src/main/java/com/nuvio/tv/data/repository/SubtitleRepositoryImpl.kt:31-34,77-124`).
5. A torrent is converted to a local HTTP URL; stopping drops the torrent and resets service state (`app/src/main/java/com/nuvio/tv/core/torrent/TorrentService.kt:92-112`).
6. Deepgram prerecorded STT currently lists 2 GB input, up to 100 concurrent requests for its principal prerecorded models, and a processing-time timeout, not a 22- or 100-minute media-duration prohibition. [Deepgram prerecorded audio](https://developers.deepgram.com/docs/pre-recorded-audio)
7. Google batch STT accepts only Cloud Storage URIs, currently permits up to five files per request, and allows each file to be up to eight hours. [Google STT quotas](https://docs.cloud.google.com/speech-to-text/docs/quotas)
8. Azure fast transcription permits files under 500 MB and five hours; batch allows 1 GB input and diarization up to 240 minutes per file. [Azure Speech quotas](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/speech-services-quotas-and-limits)

### Explicit modeling assumptions, not facts

- Reference title durations are **D = 22** and **D = 100** minutes.
- Reference compressed media bitrate is **Bv = 8 Mb/s**; reference demuxed mono audio is **Ba = 0.128 Mb/s**. Real values must come from the selected track/manifest.
- Reference sustained TV uplink is **U = 10 Mb/s**. It is not a product guarantee.
- Progressive shards contain **s = 20 seconds** of audio. This is a pilot parameter, not a provider requirement.
- Source speech produces **r = 150 billable characters per media minute**, including spaces; target expansion factor **e = 1.0**. Actual language, silence, punctuation, and translation change this substantially.
- Reference costs exclude free tiers, promotions, taxes, minimum commitments, retries, failed/abandoned work, separation/remix, Nuvio compute, storage, egress, observability, support, and payment fees.
- A generated artifact is reusable only for the same content fingerprint, exact media edition/cut, audio track, model snapshot, language pair, correction revision, and policy version.

### Material unknowns

- Percentage of real Nuvio starts that are public URL, header-protected, expiring/debrid, local/torrent, or encrypted.
- Whether a provider’s production URL fetch follows all redirects/ranges and supports the source’s TLS/CDN behavior; public prerecorded schemas do not establish header parity.
- Upstream Media3 commit represented by Nuvio’s local AARs.
- Sustainable demux/downmix throughput and thermals on the bottom quartile of supported TVs.
- Whether secure-decoder output can be tapped on target devices; the pilot must not depend on it.
- Accuracy on film/TV audio with music, far-field dialogue, accents, code-switching, overlapping speakers, and invented names.
- Commercial source-separation/remix unit cost and latency; no sourced price was found in the required window.
- Cancellation-to-billing semantics for every batch provider; closing a client connection does not prove server work or billing stopped.
- Storage/retention requirements for source shards, transcripts, translations, generated audio, corrections, and audit records.

## Architecture challenge matrix

| Architecture | Source reach | Secret posture | TV/network load | Lifecycle | Verdict |
|---|---|---|---|---|---|
| **Remote worker**: a plugin/vendor backend receives URL plus authorized fetch context, pulls media, demuxes, and processes | Public and signed URLs; headered URLs only if the worker is trusted with headers; no direct loopback/torrent reach | Worker sees playback credentials and media; provider key can remain server-side | Uploads/fetches full media unless the worker can range/demux efficiently | Durable if job-backed | Feasible for controlled clear HTTP, but highest credential and video-transfer exposure. Not provider-agnostic. |
| **TV edge demux**: Nuvio reads its active source, extracts compressed audio shards, sends only audio | Best reach for clear HTTP/HLS/DASH/debrid and the local TorrServer | Broker token can be short-lived; playback headers remain local | Lowest cloud ingress; adds TV parsing/downmix contention | Needs a resumable uploader or foreground/user-initiated transfer path | **Preferred access path**, subject to low-end TV benchmarks. Do not run source separation or large AI models on TV initially. |
| **Local gateway**: loopback process normalizes source/ranges and exposes an internal audio endpoint | Useful for torrents and irregular local sources | Keeps headers local, but endpoint and tokens must be bound/scoped | Similar to TV edge; cloud cannot fetch loopback | Dies with process/player unless explicitly promoted | A useful adapter **inside** TV edge, not an independent cloud architecture. Never advertise localhost to a provider. |
| **Companion**: phone/PC receives a signed handoff and performs pull/demux/upload | Can handle public/headered sources if handoff securely delegates access; cannot assume access to TV-only cookies/local torrent state | Moves credentials/media to another personal device | Removes TV compute; adds pairing, battery, and second-device dependency | Better background facilities on some devices, still platform-dependent | Good opt-in fallback for weak TVs and full-title jobs; poor default TV UX. |
| **Provider direct**: TV sends URL or audio to speech/TTS provider | URL mode fails for local/header-only sources; upload mode can work after TV demux | Durable provider key or user key is exposed to client/runtime; vendor coupling | Audio-only if edge demux exists; otherwise full source upload | Provider-specific jobs/cancel semantics | Suitable only for BYO-key/full-flavor experiments. Not the production default. |
| **Nuvio broker**: TV sends audio shards under a short-lived job token; broker calls providers and stores manifests/artifacts | Clear sources after client/worker acquisition; broker need not receive playback headers in edge mode | Best vendor-key isolation and centralized spend/rate controls | Audio ingress plus generated-audio egress; broker has infra cost | Durable, resumable, observable, provider-swappable | **Preferred product architecture** if economics and privacy tests pass. Start with no raw-video ingress. |

## Parameterized TTFO model

### Definitions

TTFO means **time from user confirmation to the first usable output anchored to the media timeline**: first stable caption cue for captions, or first playable target-language audio segment for voice output. It is not full-job completion.

Let:

- `D` = media duration in minutes; evaluate at 22 and 100.
- `s` = first shard duration in seconds.
- `Bv`, `Ba` = selected video/media and extracted-audio bitrate in Mb/s.
- `U` = effective upload throughput in Mb/s.
- `q` = source-to-audio extraction speed in audio-seconds per wall-second; `q=1` is realtime.
- `Rasr` = ASR processing speed in audio-seconds per wall-second.
- `Rtts` = TTS production speed in target characters per wall-second.
- `r` = source billable characters per media minute; `e` = target/source character expansion.
- `Lx` = fixed network/queue/start latency for stage `x`.
- `topen`, `tpair`, `tgw`, `tbroker`, `ttorrent` = architecture-specific setup terms.
- `F` = formatting/quality-gate latency; `M` = translation latency; `X` = remix/buffer latency.

For any audio interval `x` seconds:

```text
read(x,q)   = x / q
upload(x)   = x × Ba / U
chars(x)    = x × r × e / 60
ASR(x)      = Lasr + x / Rasr
TTS(x)      = Ltts + chars(x) / Rtts
```

Progressive caption TTFO:

```text
TTFOcap(s) = Tsetup + read(s,q) + upload(s) + ASR(s) + F
```

Progressive translated-voice TTFO:

```text
TTFOvoice(s) = TTFOcap(s) + Lmt + M(chars(s)) + TTS(s) + X
```

A non-progressive whole-title job replaces `s` with `60D`:

```text
FULLcap(D)   = Tsetup + 60D/q + 60D×Ba/U + Lasr + 60D/Rasr + F(D)
FULLvoice(D) = FULLcap(D) + Lmt + M(D×r) + Ltts + D×r×e/Rtts + X(D)
```

Therefore, with fixed `s`, **progressive TTFO is nominally independent of whether D is 22 or 100 minutes**; full completion, cost, expiry exposure, and failure probability are not. A provider that only returns a complete batch result forces TTFO to `FULL(D)`.

### Architecture substitutions for 22/100 minutes

| Architecture | Progressive TTFO for either 22 or 100 min | Whole-job TTFO/completion for 22 min | Whole-job TTFO/completion for 100 min |
|---|---|---|---|
| Remote worker | `tdispatch + tcredential + read(s,qremote) + ASR(s) + [voice stages]` | Substitute `x=1320s`, plus full remote pull/demux and any provider upload | Substitute `x=6000s`; URL expiry and retry exposure are 4.55× the 22-min duration component |
| TV edge demux | `topenTV + read(s,qTV) + sBa/U + ASR(s) + [voice]` | `topenTV + 1320/qTV + 1320Ba/U + processing(1320)` | `topenTV + 6000/qTV + 6000Ba/U + processing(6000)` |
| Local gateway | `tgw + ttorrent/source + read(s,qGW) + sBa/U + ASR(s) + [voice]` | Replace `s` with 1320; torrent metadata alone can consume up to the current 15-second code deadline (`TorrentService.kt:120-135`) | Replace `s` with 6000; peer/range behavior dominates and is unknown |
| Companion | `tpair + thandoff + read(s,qC) + sBa/UC + ASR(s) + [voice]` | `tpair + ...` with `x=1320` | `tpair + ...` with `x=6000`; pairing can be amortized only if already connected |
| Provider direct | URL mode: `tauth + tproviderFetch(s) + ASR(s)`; upload mode: edge formula without `tbroker` | Provider batch equation at `D=22`, subject to its URL/header and job limits | Provider batch equation at `D=100`; direct credentials and cancellation remain provider-specific |
| Nuvio broker | `tbroker + topenTV + read(s,qTV) + sBa/U + Qbroker + ASR(s) + [voice]` | Broker equation at `x=1320`, with resumable shard manifest | Broker equation at `x=6000`; can resume/reuse completed shards instead of restarting |

`[voice stages]` means `Lmt + M(chars(s)) + Ltts + chars(s)/Rtts + X`. None of `q`, `Rasr`, `Rtts`, setup latency, or queue latency should be filled with marketing claims; they are pilot measurements.

### Transfer-only reference calculation

Under the explicitly assumed `Bv=8 Mb/s`, `Ba=0.128 Mb/s`, and `U=10 Mb/s`:

```text
payloadMB(D,B) = 7.5 × D × B
uploadSeconds(D,B,U) = 60 × D × B / U
```

| Input sent | 22 min | Upload-only time at 10 Mb/s | 100 min | Upload-only time at 10 Mb/s |
|---|---:|---:|---:|---:|
| Full 8 Mb/s media | 1,320 MB | 1,056 s (17.6 min) | 6,000 MB | 4,800 s (80 min) |
| 128 kb/s extracted audio | 21.12 MB | 16.9 s | 96 MB | 76.8 s |
| 16 kHz mono 16-bit PCM (`0.256 Mb/s`, assumption) | 42.24 MB | 33.8 s | 192 MB | 153.6 s |

**Opportunity:** compressed-audio edge demux cuts reference ingress by `Bv/Ba = 62.5×` versus full-video upload.  
**Blocker:** if extraction can proceed only at realtime (`q≈1`), whole-title preparation still takes roughly D minutes even though upload is small. Progressive `s`-second shards avoid making that delay part of TTFO.

## Parameterized cost model

### Public rate-card facts observed on 2026-08-29

- Deepgram prerecorded Nova-3 is listed at `$0.0043/min` monolingual and `$0.0052/min` multilingual; Aura-2 is `$0.030/1k` characters. Flux TTS is promotional-free through 2026-09-12 and then listed at `$0.045/1k` characters. [Deepgram pricing](https://deepgram.com/pricing)
- Google STT V2 standard is `$0.016/min` at the first tier; dynamic batch is `$0.003/min`, with requests rounded to one-second increments. [Google STT pricing](https://cloud.google.com/speech-to-text/pricing)
- Google NMT is `$20/million` source characters after its monthly credit; Chirp 3 HD TTS is `$30/million` characters. [Google Translation pricing](https://cloud.google.com/products/translate/pricing) [Google TTS pricing](https://cloud.google.com/text-to-speech/pricing)
- Azure standard batch transcription is `$0.18/hour`, and standard neural/Neural HD Flash TTS is `$15/million` characters; speech is billed in one-second increments and TTS per character. [Azure Speech pricing](https://azure.microsoft.com/en-us/pricing/details/speech/)
- Amazon Translate standard text is `$15/million` characters; Polly Neural is `$16/million`, Generative `$30/million`, and Long-Form `$100/million`. [Amazon Translate pricing](https://aws.amazon.com/translate/pricing/) [Amazon Polly pricing](https://aws.amazon.com/polly/pricing/)

These are metering facts, not end-to-end dubbing quotations.

Let:

- `pasr` = ASR dollars per audio minute.
- `pmt` = translation dollars per million source characters.
- `ptts` = synthesis dollars per million target characters.
- `Nsrc = D×r`; `Ntgt = D×r×e`.
- `psep`, `pmix` = separation and remix dollars per audio minute (**unknown**).
- `Vv=7.5DBv/1000`, `Va=7.5DBa/1000` = decimal GB of full media/audio.
- `ping`, `pegress`, `pstore` = architecture-specific data/storage unit costs (**unknown**).
- `Corch`, `Cretry`, `Csupport`, `Cpay` = orchestration, retry leakage, support, and payment cost (**unknown**).

```text
Ccaption-source(D) = D×pasr
Ccaption-target(D) = D×pasr + Nsrc×pmt/1,000,000
Cdub-model(D)      = D×pasr + Nsrc×pmt/1,000,000
                     + Ntgt×ptts/1,000,000 + D×(psep+pmix)

Cremote(D)    = Cmodel(D) + Vv×ping(video) + Cdemux + storage/egress
Cedge(D)      = Cmodel(D) + Va×ping(audio) + CedgeCPU
ClocalGW(D)   = Cedge(D) + ClocalLifecycle
Ccompanion(D) = Cmodel(D) + Va×ping(audio) + Ccompanion
Cdirect(D)    = Cmodel(D) charged to the user/provider account; Nuvio cost is not the same as total cost
Cbroker(D)    = Cmodel(D) + Va×ping + generatedAudio×pegress
                + pstore×retention + Corch + Cretry + Csupport + Cpay
```

### 22/100-minute model-only examples

The table below applies the explicit assumptions `r=150 source characters/minute` and `e=1.0`, with no free tier/promotion and `psep=pmix=0` only because those prices are unknown. It must **not** be read as an end-to-end dub quote.

| Reference stack | Source-dialogue captions, 22 / 100 min | Translated captions, 22 / 100 min | Voice output before separation/remix, 22 / 100 min |
|---|---:|---:|---:|
| Low rate-card combination: Azure batch STT `$0.003/min`; Amazon MT `$15/M`; Azure TTS `$15/M` | `$0.066 / $0.300` | `$0.116 / $0.525` | `$0.165 / $0.750` |
| Mid reference: Deepgram Nova-3 mono `$0.0043/min`; Google NMT `$20/M`; Polly Neural `$16/M` | `$0.095 / $0.430` | `$0.161 / $0.730` | `$0.213 / $0.970` |
| Higher reference, not a worst case: Google standard STT `$0.016/min`; Google NMT `$20/M`; Deepgram Aura-2 `$30/M` | `$0.352 / $1.600` | `$0.418 / $1.900` | `$0.517 / $2.350` |

**Economic interpretation.** Model metering is not the main reason to reject a pilot. The dangerous business error is pricing from the final column while assigning zero to separation, remix, retries, cache misses, abandoned jobs, storage/egress, support, and fraud. The broker pilot must record all of those as separate meters and report gross margin by completed playable minute, not submitted minute.

### Duration and request-limit implications

- Deepgram TTS’s 2,000-character limit implies `ceil(D×r×e/2000)` synthesis requests: with the reference assumptions, **2 requests for 22 minutes** and **8 for 100 minutes** before finer sentence/timing segmentation. [Deepgram TTS latency](https://developers.deepgram.com/docs/text-to-speech-latency)
- Polly synchronous synthesis permits 3,000 billed characters and cuts output after ten minutes; its asynchronous task permits 100,000 billed characters. [Amazon Polly quotas](https://docs.aws.amazon.com/polly/latest/dg/limits.html)
- Azure realtime TTS also caps produced audio at ten minutes per request; its asynchronous synthesis jobs can retain final state for up to 31 days. [Azure Speech quotas](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/speech-services-quotas-and-limits)

**So what:** split on utterance and media-time boundaries even when a provider accepts a larger batch. Provider maximums are upper bounds, not good seek/cancel units.

## Drift, correction, seek, cancellation, and lifecycle

### Timeline model

Each artifact shard should carry:

```text
contentFingerprint, editionFingerprint, sourceAudioTrackId,
sourceStartUs, sourceEndUs, sourceWords[{startUs,endUs,confidence}],
targetText, generatedDurationUs, timelineMap[], modelSnapshot,
voiceId, revision, status, costMeters, expiresAt
```

**Captions.** Cue times stay in source media time. Nuvio’s sidecar renderer already evaluates cues against `player.currentPosition`, adjusted by audio/subtitle delay (`PlayerSidecarSubtitles.kt:178-199`). A generated track therefore must not use “time since job start.”

**Voice output.** For utterance `i`, define source interval `[si,ei]`, generated duration `gi`, and target interval duration `di=ei-si`. The local fit ratio is `fi=gi/di`. If a provider’s speed control cannot place `fi` inside its supported range, use bounded silence insertion, edit/retranslate the line, split/merge utterances, or mark the segment for correction; do not accumulate the error into later segments. Deepgram Flux’s current speed control is only 0.85-1.15. [Deepgram changelog, 2026-08-12](https://developers.deepgram.com/changelog/2026/8/12)

**Correction.** Store immutable model output plus a correction revision containing text edits, cue boundary edits, speaker/voice reassignment, and piecewise time anchors. A global subtitle delay is useful for a constant offset, but cannot repair rate drift or a wrong edition. Corrected popular artifacts are the principal cache opportunity.

### Seek behavior

- **Generated captions:** seek immediately among available cue shards. If the destination shard is absent, display “Generating near 01:02:00” and prioritize a bounded window around the destination.
- **Voice output:** never play a target segment at the wrong source position. On seek outside generated coverage, fall back to original audio or pause target audio while generating; make this user-configurable.
- **Torrent:** ask the local gateway for the destination media/audio range and let TorrServer reprioritize pieces. The current code delegates seeking and piece management to TorrServer but contains no AI-specific priority contract (`app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerTorrent.kt:14-16`). This behavior is an unknown until measured with weak swarms.
- **HLS/DASH:** key all output to presentation timestamps, not segment ordinal; manifests can change locations or timelines.

### Cancellation and lifecycle contract

1. Client sends idempotent `cancel(jobId, generation)`; broker transitions `RUNNING→CANCEL_REQUESTED→CANCELLED|COMPLETED_RACE`.
2. Client stops demux/upload immediately and deletes uncommitted local shards.
3. Broker stops scheduling new stages and invokes provider cancellation/interrupt where supported. Cartesia exposes cancellation of a WebSocket context. [Cartesia TTS WebSocket reference](https://docs.cartesia.ai/api-reference/tts/websocket) Deepgram Flux can interrupt active TTS and report spoken/remaining text. [Deepgram changelog, 2026-08-12](https://developers.deepgram.com/changelog/2026/8/12)
4. Costs already incurred remain recorded; the UI must not claim that cancel guarantees a refund.
5. Player exit cancels foreground delivery but does not silently destroy committed broker work. Current player release cancels local jobs and torrent state (`PlayerRuntimeControllerLifecycle.kt:11-63`), so broker work needs a separate lifecycle owner.
6. Every shard and callback is generation-checked so a late response cannot attach to a new stream/edition.

## Recommended boundaries

### Ship boundary for generated captions

Include only:

- User-initiated, clear, seekable VOD.
- Direct HTTP/HLS/DASH, cached debrid HTTP, and torrents that the local gateway can read.
- A declared source audio track and one target language.
- 20-30 second resumable audio shards with stable word/cue timestamps.
- Generic “generated” badge, confidence/quality warning, correction offset, delete/regenerate, and original subtitle preference.
- Broker-held provider keys; no playback headers sent beyond the trusted acquisition component.

Exclude initially:

- DRM/encrypted sources, live TV, external-player-only playback, screen/audio capture as a hidden fallback, karaoke/lyrics, and claims of SDH/closed-caption equivalence.
- Whole video upload from a TV.
- Cross-edition cache reuse based only on title/IMDb/TMDB ID.

### Ship boundary for translated voice

Pilot only:

- The same clear-VOD boundary.
- One language pair and a small generic-voice catalog.
- Dialogue ducking/overlay first; no promise of clean dialogue replacement.
- Utterance-level timing with original-audio fallback on missing shards.
- 22-minute titles first; a 100-minute test is an engineering gate, not launch scope.

Exclude initially:

- “Studio dub” positioning, voice cloning, actor identity matching, lip sync, singing, overlapping-dialogue guarantees, live dubbing, 5.1/object-audio preservation, and unattended processing after the user leaves without a durable job/notification design.

### Packaging boundary

These should be **native capabilities with provider adapters**, even if the full flavor exposes an extension hook. A normal plugin cannot provide consistent Play Store behavior because plugins are disabled there (`app/build.gradle.kts:163-171`; `app/src/playstore/java/com/nuvio/tv/core/plugin/PluginManager.kt:12-74`). Any extension contract should return declarative jobs/artifacts and never receive vendor master keys, arbitrary filesystem access, or raw long-lived playback credentials.

## Falsifiable pilots and acceptance tests

### Pilot 0 , access and no-spend preflight

**Corpus:** at least 36 owned/test fixtures, balanced across public MP4/MKV, signed-query URL, header/cookie/referrer URL, HLS, clear DASH, short-lived debrid URL, healthy/weak torrent, wrong edition, malformed media, and DRM controls.

**Acceptance tests:**

1. Classifier returns one of the explicit input modes within 5 seconds for 35/36 fixtures.
2. Every DRM control is rejected before any provider request and before any source payload upload; false acceptance target is **0**.
3. Every supported clear fixture yields the selected audio track’s first timestamped packet without downloading more than `max(2×s×Bv/8, 16 MB)` before first shard completion.
4. Header allowlist logs names only; automated logs, crash reports, analytics, and broker records contain **0 raw Authorization/Cookie values and 0 signed query strings**.
5. Expired URLs fail as `SOURCE_EXPIRED`, not as generic AI failure, and trigger at most one controlled re-resolution.
6. Loopback/torrent URLs are never submitted to a public provider URL-fetch field.

**Kill condition:** any protected fixture sends payload to a speech provider, or any raw credential appears in a remote log.

### Pilot 1 , generated dialogue captions

**Corpus:** 12×22-minute and 6×100-minute clear titles; at least four languages, two code-switch cases, music-heavy scenes, overlapping dialogue, quiet speech, and one deliberately mismatched cut.

**Acceptance tests:**

1. With `s=20s`, broker path caption TTFO is p50 ≤ 12 seconds and p95 ≤ 25 seconds on the declared test network; report each stage separately rather than hiding queue time.
2. A 60-minute seek in a completed 100-minute artifact displays the correct cue in ≤ 500 ms; an absent shard is identified in ≤ 1 second and prioritized without restarting earlier shards.
3. Median absolute speech-onset error ≤ 250 ms, p95 ≤ 750 ms, and no uncorrected cumulative drift > 1 second across any 20-minute interval.
4. Human-corrected reference WER ≤ 15% for the clean-dialogue subset; noisy/overlap subsets are reported separately and may not be averaged away.
5. Wrong-edition fixture has 0 cache hits and is blocked by fingerprint/timeline mismatch.
6. Cancel stops client upload within 2 seconds and new broker stage starts within 3 seconds; post-cancel cost is bounded to already-started shards and displayed.
7. Measured provider charge is within ±10% of the formula using observed audio seconds/characters for ≥95% of successful jobs.
8. The UI never labels output “SDH” unless a separate test achieves non-speech-event precision and recall ≥ 0.80 and speaker-change accuracy ≥ 0.90.

**Kill condition:** p95 TTFO exceeds 40 seconds after warm-up, wrong-edition reuse occurs, or median all-in variable cost exceeds the approved cap after retries are included.

### Pilot 2 , translated voice overlay, not full dubbing

**Corpus:** six 22-minute clear titles in one source/target pair, then two 100-minute gates; include two-speaker, overlap, music, silence, shouting, and whisper scenes.

**Acceptance tests:**

1. First target-language audio segment p50 ≤ 20 seconds and p95 ≤ 40 seconds on the declared test network.
2. Median target speech onset error ≤ 250 ms, p95 ≤ 500 ms, and no segment starts after its source utterance ends.
3. No cumulative drift > 1 second per 20 minutes; any segment requiring a fit outside the configured speed bound is explicitly regenerated/edited or falls back, never silently stretched.
4. Blind bilingual review rates translation adequacy and intelligibility ≥ 4/5 median; results are stratified by speech condition.
5. Original-language dialogue bleed is detected in <5% of sampled dialogue windows after ducking/overlay. Failure means the feature remains “voice overlay,” not “dubbing.”
6. Seek to an ungenerated region never plays stale target audio; original-audio fallback begins within 500 ms.
7. Cancel meets Pilot 1 cancellation timing and leaves no orphaned generated-audio download.
8. Observed model charge is within ±10% of metered seconds/characters, and all-in variable cost,including separation/remix if introduced,is reported separately for 22 and 100 minutes.

**Kill condition:** overlap/music scenes require destructive full-track replacement, p95 onset error exceeds 1 second, or 100-minute artifacts cannot resume from a failed shard without restarting completed work.

### Architecture selection gate

Run Pilot 1 through TV edge + broker, provider-direct BYO key, companion, and remote worker on the same clear fixtures. Select the production path only if it wins a weighted score whose raw values remain visible:

```text
score = 0.30×supported-source success
      + 0.20×TTFO success
      + 0.15×seek/cancel success
      + 0.15×credential/privacy success
      + 0.10×all-in variable-cost success
      + 0.10×low-end-device success
```

Any DRM leakage or credential leakage is a veto regardless of score.

## Recommendations

1. **Build an access preflight before either AI feature.** It should fingerprint the edition and audio track, classify input mode, detect encryption, test ranges/expiry, and estimate bytes/time/cost without invoking a paid model.
2. **Pilot TV edge demux + Nuvio broker.** Send compressed mono audio shards, not video and not raw PCM unless the provider requires it. Keep playback headers and debrid credentials at the acquisition edge.
3. **Launch captions first under a narrow name.** Reuse Nuvio’s sidecar renderer, but extend generated subtitle metadata with headers/expiry, job ID, fingerprint, revision, confidence, and provenance.
4. **Reframe the second plugin.** Pilot “translated voice overlay” using generic voices and ducking; require a separate separation/remix gate before using “dubbing.”
5. **Make jobs durable and segment-addressable.** Persist job/shard state outside `PlayerRuntimeController`; use generation IDs, idempotent cancellation, source-time anchors, retry budgets, and an original-audio fallback on seek.
6. **Pin model snapshots and store provenance.** Cartesia’s dated Sonic snapshot pattern and the rapid August Nova updates show why every cached artifact needs a model/version field. [Cartesia 2026 changelog](https://docs.cartesia.ai/changelog/2026) [Deepgram changelog](https://developers.deepgram.com/changelog)
7. **Meter economics at shard granularity.** Record provider seconds, source/target characters, retries, bytes, storage days, generated-audio egress, correction labor, and abandoned work. Price only after the 22- and 100-minute pilots produce observed distributions.
8. **Keep a companion path as fallback, not default.** It may rescue low-end TVs or long jobs, but pairing and delegated source access are extra failure/security surfaces.
9. **Do not ship provider master keys in TV/plugin code.** Direct/BYO credentials may be an advanced full-flavor experiment; the default uses short-lived broker tokens and budget caps.
10. **Treat Play Store parity as a native-feature requirement.** A feature that exists only as a local plugin does not exist in the Play Store flavor today.

## Data / Evidence

### Repository evidence

- Stream URL/torrent/debrid classification and header-bearing behavior hints: `app/src/main/java/com/nuvio/tv/domain/model/Stream.kt:10-100,181-211`.
- Header sanitation and mapping: `app/src/main/java/com/nuvio/tv/data/mapper/StreamMapper.kt:94-122`.
- HLS/DASH/progressive media construction and sidecar attachment: `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerMediaSourceFactory.kt:82-207`.
- Redirect Authorization behavior and playback network limits: `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerPlaybackNetworking.kt:67-140`.
- Generated-caption-compatible sidecar rendering and cancellation: `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerSidecarSubtitles.kt:26-39,84-200`.
- Subtitle download/header boundary: `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerSubtitleTiming.kt:196-263`.
- Torrent gateway/start/stop/metadata behavior: `app/src/main/java/com/nuvio/tv/core/torrent/TorrentService.kt:49-135`; `app/src/main/java/com/nuvio/tv/core/torrent/TorrServerApi.kt:126-148`.
- Raw URL/header persistence: `app/src/main/java/com/nuvio/tv/data/local/StreamLinkCacheDataStore.kt:42-125`.
- Flavors and media dependencies: `app/build.gradle.kts:98-172,452-510`.
- Play Store plugin stub: `app/src/playstore/java/com/nuvio/tv/core/plugin/PluginManager.kt:12-74`.
- Player-owned lifecycle cancellation: `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerLifecycle.kt:11-77`.

### Primary web evidence

- [Deepgram changelog , 2026-07-31](https://developers.deepgram.com/changelog/2026/7/31)
- [AndroidX Media3 1.11.0 , 2026-08-05](https://developer.android.com/jetpack/androidx/releases/media3#1.11.0)
- [Deepgram changelog , 2026-08-12](https://developers.deepgram.com/changelog/2026/8/12)
- [Android playback capture , updated 2026-08-14](https://developer.android.com/media/platform/av-capture)
- [Cartesia 2026 changelog , Sonic 3.6, 2026-08-27](https://docs.cartesia.ai/changelog/2026)
- [Deepgram changelog , language/model releases through 2026-08-28](https://developers.deepgram.com/changelog)
- [Media3 DRM guide](https://developer.android.com/media/media3/exoplayer/drm)
- [Deepgram prerecorded STT guide](https://developers.deepgram.com/docs/pre-recorded-audio)
- [Deepgram prerecorded API schema](https://developers.deepgram.com/reference/speech-to-text/listen-pre-recorded)
- [Deepgram TTS latency and request sizing](https://developers.deepgram.com/docs/text-to-speech-latency)
- [Deepgram pricing](https://deepgram.com/pricing)
- [Google STT quotas](https://docs.cloud.google.com/speech-to-text/docs/quotas)
- [Google STT pricing](https://cloud.google.com/speech-to-text/pricing)
- [Google TTS pricing](https://cloud.google.com/text-to-speech/pricing)
- [Google Translation pricing](https://cloud.google.com/products/translate/pricing)
- [Azure Speech quotas](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/speech-services-quotas-and-limits)
- [Azure Speech pricing](https://azure.microsoft.com/en-us/pricing/details/speech/)
- [Amazon Transcribe pricing](https://aws.amazon.com/transcribe/pricing/)
- [Amazon Translate pricing](https://aws.amazon.com/translate/pricing/)
- [Amazon Polly pricing](https://aws.amazon.com/polly/pricing/)
- [Amazon Polly quotas](https://docs.aws.amazon.com/polly/latest/dg/limits.html)
- [Cartesia TTS WebSocket cancellation](https://docs.cartesia.ai/api-reference/tts/websocket)
- [Android background-task guidance](https://developer.android.com/develop/background-work/background-tasks)

## Open Questions

1. What share of actual Nuvio viewing minutes falls in each access class, and how often do source URLs expire before a 22- or 100-minute job completes? Resolve with privacy-preserving preflight telemetry before model integration.
2. Which exact Media3 revision and patches are inside Nuvio’s local AARs? Resolve by recording build provenance or rebuilding from a tagged source revision.
3. Can the current player expose selected compressed audio packets without competing with playback, and can it downmix every supported codec/channel layout on low-end TVs? Resolve with a standalone extractor benchmark APK and the acceptance corpus.
4. What provider cancellation actually stops compute/billing for prerecorded STT and asynchronous synthesis? Resolve with paid sandbox experiments and invoice-level reconciliation; do not infer it from socket closure.
5. What source-separation/remix service or local model meets latency, quality, retention, and price requirements? No supported rate/quality answer was established; run a bake-off before naming the feature dubbing.
6. What retention policy is acceptable for audio shards, transcripts, translations, generated audio, and human corrections? Resolve with product/privacy requirements and provider data controls.
7. Is companion delegated access acceptable for header-bearing/debrid sources, and can credentials be made single-use and source-scoped? Resolve with a threat model and pairing prototype.
8. What artifact identity survives remuxes but rejects alternate cuts? Resolve by combining content metadata with sampled audio fingerprints and timeline checks, then test the deliberate wrong-edition fixtures.
9. How should users correct generated text/timing on a TV remote? Resolve with a phone/web correction handoff; Nuvio already uses phone-managed configuration patterns, but no generated-media correction workflow exists in the inspected code.
10. Can the economics support free usage, subscription allocation, or per-job purchase after retries and abandonment? Resolve only after pilots report **all-in cost per completed playable minute**, p50/p95, separately for 22 and 100 minutes.
