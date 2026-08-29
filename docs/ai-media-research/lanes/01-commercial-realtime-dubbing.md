# Commercial real-time dubbing lane

**Research cutoff:** 2026-08-29  
**Priority window:** **2026-07-31 through 2026-08-29 inclusive**. Material older than 2026-07-31 is explicitly marked **Historical background**.  
**Scope:** commercial automatic dubbing, live speech-to-speech translation, simultaneous interpretation, streaming translation, and voice-preserving output usable with a customer-supplied vendor key (BYOK). The excluded vendor was not searched, fetched, analyzed, or cited in the decision set.

## Executive summary

The best verified NuvioTV integration candidates are **CAMB.AI** for the broadest combination of live broadcast ingest, a dedicated speech-to-speech socket, cloned-voice output, and separate batch dubbing; **OpenAI GPT-Realtime-Translate** for the cleanest inexpensive browser/server live-translation primitive; **Gemini 3.5 Live Translate** for 70+ languages and hardened client tokens; and **Palabra** for voice-preserving live interpretation plus broadcast packaging. Deepdub is newly relevant because its official repository added a live-streaming reference during the window, but that API is early access and has no public price or measured latency. DeepL Voice also deserves a pilot: in-window it added per-project usage tagging, while its live API already returns incremental translated speech using single-use session credentials.

No vendor-published adjective ("low latency", "real time", "fast") is treated as a measurement. Exact latency/RTF is marked **unknown** unless the official material gives a number; only Palabra (<1 s marketing claim), Smartcat (500 ms average marketing claim), CAMB MARS TTS (100 ms/50 ms TTFB, not end-to-end translation), Deepdub TTS/agent voice (~125 ms, not translation), and Cartesia TTS (about/sub-90 ms TTFB, not translation) supplied numeric claims in fetched official pages. None supplied a reproducible, independently comparable end-to-end translation benchmark for this lane.

## Key findings

### 1. In-window changes that matter

**What.** The exact window produced several actionable commercial updates, but only some are translation/dubbing products rather than component releases.

**Evidence.**

- **CAMB.AI real-time S2S documentation and SDK path, updated 2026-08-27.** The beta socket is `wss://realtime.camb.ai/v1/realtime`; first message is `session.update`; PCM16 mono 24 kHz is used both ways; events include source transcript deltas, `response.text.delta`, and base64 `response.audio.delta`; account-owned cloned voices are selectable. The updated docs replaced engine codenames with `fast`/`slow`, and the official Python and TypeScript repositories made that same change on 2026-08-27. [WebSocket reference](https://docs.camb.ai/api-reference/websockets/realtime) · [tutorial](https://docs.camb.ai/tutorials/realtime-translation-with-sdk) · [Python commit](https://github.com/Camb-ai/cambai-python-sdk/commit/94219cb48f42c251805279878fa993a36937253d) · [TypeScript commit](https://github.com/Camb-ai/cambai-typescript-sdk/commit/6243f31cb6392494817f54a292b03a5f0827a9bb)
- **Deepdub live broadcast API documented 2026-08-13.** A repository commit titled “Add Live Streaming API reference” added an early-access API for HLS/SRT/RTMP/CMAF inputs, WebSocket/HLS-WebVTT captions, and HLS/SRT/RTMP dubbed outputs. Authentication remains a Deepdub `x-api-key`; pricing and exact latency are unknown. [Official commit](https://github.com/deepdub-ai/deepdub-api/commit/ef3e2c1acb0731d819b4f61c52499fadc8b7c270) · [live API](https://docs.deepdub.ai/api-reference/live/overview)
- **Palabra SDK added operational controls.** Python v2.1.0 (2026-08-03) added Voices and Glossaries; v2.1.1 (2026-08-20) added `interrupt`. These are meaningful for live playback cancellation and voice-preserving translation, not a new core model launch. [v2.1.0](https://github.com/PalabraAI/palabra-ai-python/releases/tag/v2.1.0) · [v2.1.1](https://github.com/PalabraAI/palabra-ai-python/releases/tag/v2.1.1)
- **DeepL Voice usage attribution arrived 2026-08-18.** `X-DeepL-Reporting-Tag` can be attached to `POST /v3/voice/realtime`; analytics report `speech_to_text_minutes` and `speech_to_speech_minutes`. The tag must be set during HTTPS session creation because the subsequent WebSocket carries no headers. [DeepL changelog](https://developers.deepl.com/docs/resources/roadmap-and-release-notes)
- **Deepgram Flux TTS became GA 2026-08-12.** `/v2/speak` offers streaming WebSocket and batch REST; `Interrupt` returns exact `text_spoken` and `text_remaining`, useful for progressive playback accounting. This is a composable TTS component, not translation. It was free through 2026-09-12, then listed at $0.045/1K characters PAYG. [Changelog](https://developers.deepgram.com/changelog) · [pricing](https://deepgram.com/pricing) · [Flux overview](https://developers.deepgram.com/docs/flux-tts/overview)
- **Gemini 3.5 Transcribe Live became GA 2026-08-26.** It can strengthen a composable Google pipeline, but it is STT only; `gemini-3.5-live-translate-preview` remained the direct S2S product and its model page still says “Latest update June 2026.” [Gemini changelog](https://ai.google.dev/gemini-api/docs/changelog) · [Live Transcribe](https://ai.google.dev/gemini-api/docs/live-api/live-transcribe) · [Live Translate model](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-live-translate-preview)
- **Sarvam documented a new real-time ASR socket and dubbing price in August 2026.** `saaras:v3-realtime` emits partial/final transcripts and supports `mode="translate"`, but returns text rather than translated speech. The separate batch Dubbing API preserves voice identity and costs ₹40/min per target on Starter with `editor_flow:false` (₹80/min with editor flow). This is a regional batch candidate/composable input, not a direct live S2S leader. [Changelog](https://docs.sarvam.ai/changelog) · [real-time STT](https://docs.sarvam.ai/api/api-guides-tutorials/speech-to-text/realtime-streaming) · [pricing](https://docs.sarvam.ai/api/getting-started/pricing) · [dubbing](https://docs.sarvam.ai/creative-dubbing)

**So what.** Prioritize pilots around CAMB, OpenAI, Gemini, Palabra, DeepL and Deepdub. Do not let fresh STT/TTS announcements (Gemini Transcribe, Deepgram Flux, Sarvam Realtime) masquerade as one-call live speech translation.

### 2. One-call batch dubbing is a different lane from composable/live streaming

**What.** Batch systems accept a media asset, create an asynchronous job/project, then expose status and downloadable/exported media. They cannot provide progressive playback unless a separate live API exists.

| Verified batch system | Exact mechanics | Voice/output/languages | Price and timing |
|---|---|---|---|
| **CAMB.AI End-to-End Dubbing** | Auth `x-api-key`; POST end-to-end dubbing with public `video_url`, source locale and an array of target locales; response `task_id`; poll `/dub/{task_id}`; retrieve runs/output URLs/transcript. YouTube, public Drive/direct media supported; multi-target in one job. | MARS/BOLI pipeline; docs say original voice characteristics are preserved; 140+ language platform claim; final media plus timed transcript. | Public plans are credit bundles ($5/10K credits through $900/1.8M monthly), but fetched page did not expose an unambiguous dubbing-credit conversion: **unit price unknown**. Processing latency/RTF **unknown**. [API](https://docs.camb.ai/api-reference/endpoint/end-to-end-dubbing) · [tutorial](https://docs.camb.ai/tutorials/dubbing-with-sdk) · [pricing](https://www.camb.ai/pricing) |
| **Dubformer Platform API** | `POST https://app.dubformer.ai/api/v1/projects`, Bearer key, JSON `source_video_url`, `source_lang`, locale-specific `target_lang`, soundalike flag and mixing mode. Response includes `project_id`, charged minutes, estimated completion and remaining balance; poll project then download video/audio. | 40+ source / 100+ target in API docs (marketing site says 140+); standard, soundalike and emotional transfer; three mix modes. | Per-minute balance, differing by voice type; exact dollar rate **unknown**. Latency/RTF **unknown**. [Create project](https://docs.dubformer.ai/platform/endpoints/projects/create-project.md) · [overview](https://docs.dubformer.ai/platform/overview) |
| **Deepdub Managed Dub** | `POST` Submit Dubbing Job with `x-api-key`, source/target locale, S3 or HTTPS source, optional S3 export, M&E/speech tracks and desired deliverables; response `requestId`; status API exposes progress/export/additional products. | Managed professional dub; source and export paths are explicit. Separate TTS API can clone from reference audio, but the fetched Managed Dub request does not establish automatic same-speaker cloning as a guaranteed field. | Quote only; latency/RTF **unknown**. [Submit job](https://docs.deepdub.ai/api-reference/submit-dubbing-job) |
| **Rask API v2** | Upload media by file/link, create a project, inspect/change speakers and voices, then render/export. Bearer token; multipart SRT ingestion is also available. API v1 is fully deprecated. | 135+ translation/dubbing languages; official site says cloning in 32 languages and ready-to-publish video, subtitles and transcripts. | API limited to Business/Enterprise; public subscription examples range from $60/25 min to $1,500/1,000 min monthly, and extra Business minutes $3, but **API unit economics unknown**. Latency/RTF **unknown**. [API docs](https://docs.api.rask.ai/introduction) · [product](https://www.rask.ai/api) · [languages](https://www.rask.ai/llm-info) · [pricing](https://www.rask.ai/pricing) |
| **Sarvam Dubbing API** | Upload/create Dubbing job; async lifecycle documented in Content Studio/API; pricing is source duration × target languages. | Indian-language localization preserving original voice identity/emotion; multi-speaker and lip-sync-ready output are claimed. | ₹40/min/target Starter (`editor_flow:false`), ₹80/min/target with editor; 60 free dubbing minutes for new Content Studio accounts. Latency/RTF **unknown**. [pricing](https://docs.sarvam.ai/api/getting-started/pricing) · [dubbing](https://docs.sarvam.ai/creative-dubbing) |
| **Papercup / RWS** | Service workflow: ASR + expert review, AI translation + human post-edit, voice selection/cloning, adaptation/speech editing, engineering/QC. No public self-serve endpoint was found. | XLPT preserves tone/pacing/emotion; multiple speakers; licensed or cloned voices; service deliverables. | Quote only; latency/RTF **unknown**; not a direct BYOK API integration. [RWS/Papercup](https://www.rws.com/localization/services/translation-services/video-and-audio-translation/ai-dubbing-and-vo/) |

**So what.** For an on-demand “upload episode, later download localized asset” feature, CAMB and Dubformer are the cleanest self-serve mechanics; Rask is credible but plan-gated; Deepdub and RWS are enterprise workflows. None should be presented to product as progressive live playback.

### 3. Verified direct/live streaming products

| System | Transport, ingest and progressive output | Languages / voice preservation | Key handling | Verified price | Verified latency |
|---|---|---|---|---|---|
| **CAMB.AI Realtime S2S** | `wss://realtime.camb.ai/v1/realtime`; first `session.update`; append base64 PCM16 mono 24 kHz; receive source transcript, translated text deltas and translated audio deltas. Separate broadcast API: `POST /stream` ingests **SRT today** (RTMP/HLS ingest “coming soon”), returns `stream_id` and SRT/RTMP/HLS outputs; audio and subtitle assets are declared together. | 14 realtime languages; account-owned cloned `voice_id`; live broadcast API auto-clones source speakers when voice IDs are omitted. Platform/batch supports 140+. | `x-api-key` WebSocket header or first-message auth; docs warn never expose the key client-side. | Realtime/broadcast unit price **unknown**; plans are credits. | End-to-end **unknown**. `slow` has **30s+ cold boot** (startup, not ongoing translation latency). MARS Flash 100 ms TTFB is TTS only. [S2S](https://docs.camb.ai/api-reference/websockets/realtime) · [broadcast create](https://docs.camb.ai/api-reference/endpoint/streaming/create-new-stream) · [auth](https://docs.camb.ai/getting-started/authentication) |
| **OpenAI GPT-Realtime-Translate** | Browser: server creates `POST /v1/realtime/translations/client_secrets`, client posts SDP to `/v1/realtime/translations/calls`, translated remote WebRTC audio track + transcript data-channel deltas. Server: `wss://api.openai.com/v1/realtime/translations?model=gpt-realtime-translate`; append base64 24 kHz PCM16; receive `session.output_audio.delta`, source/target transcript deltas. One session per target language; `session.close` flushes tail. | Live multilingual audio; official fetched docs do not enumerate a stable language list or promise source-speaker voice replication. Voice consistency is a test item, not a guarantee. | Standard key only server-side; short-lived translation client secret to browser. | **$0.034/min**, duration billed. | **Unknown**; docs instruct customers to measure first-audio and end-of-utterance latency. [guide](https://developers.openai.com/api/docs/guides/realtime-translation) · [model](https://developers.openai.com/api/docs/models/gpt-realtime-translate) · [pricing](https://developers.openai.com/api/docs/pricing) |
| **Gemini 3.5 Live Translate Preview** | Stateful Live API WebSocket (`v1beta ... BidiGenerateContent`); 16-bit mono PCM 16 kHz in, 24 kHz out; Google recommends 100 ms input chunks. `modelTurn.parts[].inlineData` is progressive translated audio; input/output transcripts optional. Audio-only input, one target in `translationConfig`. | **70+** listed languages. Voice replication explicitly **inconsistent**: shifts after pauses, wrong gender, and multi-speaker lockups are documented limitations. | Server key, or one-use ephemeral token (`POST /v1beta/auth_tokens`), default one minute to open and 30 minutes to message; constrain model/translation config server-side. | **~$0.0368/min** blended ($0.0053 input + $0.0315 output estimates). | **Unknown**. “Low-latency” is not a number. [guide](https://ai.google.dev/gemini-api/docs/live-api/live-translate) · [pricing](https://ai.google.dev/gemini-api/docs/pricing#gemini-3.5-live-translate) · [tokens](https://ai.google.dev/gemini-api/docs/live-api/ephemeral-tokens) |
| **Palabra S2S** | WebRTC via LiveKit or single WebSocket. Direct WS: `wss://streaming.palabra.ai/streaming-api/{random}/v1/speech-to-speech/stream?token=...`; `set_task`, then base64 chunks (recommended 320 ms), receive 24 kHz mono PCM `output_audio_data`, source/translated transcripts, `last_chunk`; `interrupt_task` cancels current phrase. File helper is paced at real time and takes about source duration, therefore not batch acceleration. Broadcast product supports RTMP/SRT/HLS. | 60+ on current product; language capability is split into recognition/translation/cloning columns. Zero-shot automatic speaker voices, deaccenting, custom glossaries. | API key server-side; manually created session returns publisher JWT and WS/WebRTC URLs. No browser API-key exposure. | Product hero says **$0.04/min**, while packaged S2S plans are $20/$15/$10 per hour at 3/10/50-hour capacities; contract context must be verified. | Official marketing claim **<1 s**; no methodology/percentile/hardware, so use as claim only. [API](https://docs.palabra.ai/docs/streaming_api) · [audio](https://docs.palabra.ai/docs/streaming_api/publishing_and_receiving_audio) · [auth](https://docs.palabra.ai/docs/auth) · [product](https://www.palabra.ai/voice-translation-api) |
| **DeepL Voice API** | `POST /v3/voice/realtime` fixes formats/languages and returns one-use `streaming_url` + token; WS receives 50–250 ms recommended chunks and emits tentative/concluded source/target transcript updates plus incremental `target_media_chunk`; target audio contains speech only (no silence/padding). Reconnect by exchanging latest rotating token. | One source → up to 5 text targets and **one** speech target. Programmatic support via `GET /v3/languages?resource=voice`; all source languages can translate to any target, but some STT/TTS is supplied by external partners. Voice is selected from enumerated voices; source-speaker cloning/preservation not verified. | Existing paid DeepL API key only on HTTPS setup; the streaming URL/token are one-use and API key never enters WS handshake. | Public fetched page did not expose Voice minute rates: **unknown**. | **Unknown**. Fixed source language is documented to reduce latency, without a number. [overview](https://developers.deepl.com/docs/voice/overview) · [session request](https://developers.deepl.com/api-reference/voice/request-session) · [session mechanics](https://developers.deepl.com/docs/voice/understanding-voice-sessions) |
| **Deepdub Live (early access)** | Long-lived service ingesting HLS/SRT/RTMP/CMAF. Target captions are HLS WebVTT and WebSocket; dubbed audio is packaged to HLS/SRT/RTMP. Discover stream types through enum endpoints; create/reuse/start/stop services. | Multiple `(language, TTS engine, voice)` translations. Dynamic support list; no count in fetched reference. Voice choice is explicit; automatic source-speaker clone not established. | Standard `x-api-key`; no orchestration-provider credential. | **Unknown / contact sales.** | **Unknown.** [live API](https://docs.deepdub.ai/api-reference/live/overview) |
| **Azure Speech Translation / Live Interpreter** | Speech SDK/CLI (REST does **not** support speech translation). Audio stream yields interim source/translation; Standard API outputs text/audio. Live Interpreter continuously identifies language and returns speech preserving style/tone. Voice Live is a separate **agent** WebSocket at `/voice-live/realtime?api-version=2026-04-10`, not a dedicated interpreter endpoint. | Broad per-feature matrix; Live Interpreter described as multi-language with style/tone preservation. Exact count must be queried from current language table, not the STT total. | Prefer Entra managed identity; keys in Key Vault. Voice Live supports Bearer token or `api-key` header/query. | Search/fetched official pricing metadata reported Standard translation **$2.50/audio hour**; live-interpreter page values were dynamically elided in HTTP output, so input/output prices **unknown**. | **Unknown.** [translation overview](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/speech-translation) · [quickstart](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/get-started-speech-translation) · [pricing](https://azure.microsoft.com/en-us/pricing/details/speech/) |
| **Smartcat realtime voice API** | Public product page says send live audio to an API endpoint and receive translated audio stream, but fetched official developer docs expose document/project APIs rather than the promised wire endpoint. | 280+ languages/locales; selectable voices and enterprise custom cloning. | Account-generated API key; no ephemeral-client mechanism verified. | Usage based but **amount unknown**. | Official marketing claim **500 ms average**; no test method/percentile, so claim only. [product](https://www.smartcat.com/software-translator/real-time-voice-translation-api/) |

**So what.** CAMB is the only verified candidate here combining raw-audio S2S, live broadcast packaging and batch dubbing in one vendor. OpenAI/Gemini are simpler direct mobile/browser APIs. Palabra has better explicit voice/broadcast controls but a pricing inconsistency to resolve. DeepL has excellent key isolation and reconnect semantics but does not verify source-voice preservation.

### 4. Composable streaming components, not one-call translators

| Vendor | Verified role | Why it is not ranked as a complete live-dubbing API |
|---|---|---|
| **Speechmatics** | WebSocket real-time ASR; unified speech translation returns transcription + translated **text**; 34 languages to/from English in the historical product release. Pricing page (updated **2026-07-31**, in-window) lists 55+ STT languages and Pro from **$0.129/hour**. | No translated audio/progressive voice output in fetched translation API evidence; must add TTS and voice identity layer. [pricing](https://www.speechmatics.com/pricing) · [historical translation release](https://www.speechmatics.com/company/articles-and-news/our-new-unified-speech-translation-api) |
| **Deepgram** | Streaming Flux/Nova STT plus Flux/Aura TTS. TTS WS receives text and returns binary chunks; temporary tokens supported. Flux TTS at `/v2/speak` became GA in-window with exact interruption accounting. | Translation requires an external MT/LLM stage; no one-call translated speech and no same-speaker cloning. The vendor architecture article’s “under 500 ms” is a design target/third-party pipeline discussion, not a Deepgram API SLA. [TTS WS](https://developers.deepgram.com/reference/text-to-speech/speak-streaming) · [pricing](https://deepgram.com/pricing) |
| **Cartesia** | Ink STT WebSocket accepts binary chunks and finalization; Sonic 3.6 TTS WS returns chunks/timestamps; instant/pro voice cloning; short-lived scoped browser access tokens. 44 TTS languages. | No translation stage. TTS first byte about/sub-90 ms is not S2S translation latency. Pricing: $5/100K credits (~133 TTS min), $49/1.25M, $299/8M. [overview](https://docs.cartesia.ai/get-started/overview) · [STT WS](https://docs.cartesia.ai/api-reference/stt/websocket) · [pricing](https://www.cartesia.ai/pricing) |
| **Google Cloud Speech + Translate + TTS** | gRPC streaming STT, Cloud Translation REST and TTS can be composed. | Three APIs, not one call; no verified cross-language source-voice preservation. Streaming STT has separate limits and TTS character/token billing. Prefer direct Gemini Live Translate unless deterministic cascaded control is required. [streaming STT](https://docs.cloud.google.com/speech-to-text/docs/v1/transcribe-streaming-audio) · [STT pricing](https://cloud.google.com/speech-to-text/pricing) · [TTS pricing](https://cloud.google.com/text-to-speech/pricing) |
| **Soniox** | `wss://stt-rt.soniox.com` accepts binary audio, up to 300-minute sessions, and returns real-time one-/two-way translated **text** tokens; temporary client keys. Separate TTS WS can produce audio. STT $0.12/hour real-time and TTS about $0.70/hour. | Requires app-owned STT/translation → TTS glue; no verified same-speaker preservation. [STT WS](https://soniox.com/docs/api-reference/stt/websocket-api) · [TTS WS](https://soniox.com/docs/api-reference/tts/websocket-api) · [pricing](https://soniox.com/pricing) |
| **Sarvam** | In-window `saaras:v3-realtime` WebSocket offers partial/final ASR and translate mode for 24 language-code values; ₹30/hour STT+translate. Separate Bulbul TTS and batch Dubbing API. | The realtime endpoint returns transcripts, not translated audio; source-voice preservation exists only in batch dubbing evidence. [realtime](https://docs.sarvam.ai/api/api-guides-tutorials/speech-to-text/realtime-streaming) · [pricing](https://docs.sarvam.ai/api/getting-started/pricing) |

## Ranked shortlist

Ranking is for **NuvioTV commercial live/broadcast dubbing with BYOK**, not generic voice quality. Only systems with fetched official mechanics are ranked.

1. **CAMB.AI — best functional fit, pilot first.** Verified raw S2S WS, SRT broadcast ingest and SRT/RTMP/HLS outputs, auto-cloned speakers in broadcast when voice IDs are omitted, and separate multi-target batch endpoint. Risks: real-time API is beta, 14-language direct socket, no public unit economics, no measured E2E latency.
2. **OpenAI GPT-Realtime-Translate — best clean direct integration/value.** Dedicated translation endpoint, WebRTC for Android/browser sidecars, WebSocket for media workers, progressive audio/transcript deltas, safe client secrets, and $0.034/min. Risks: no verified voice preservation or language list, one session per target, no native batch dub or broadcast mux.
3. **Gemini 3.5 Live Translate Preview — best broad-language direct S2S.** 70+ languages, explicit PCM/chunk mechanics, progressive audio and strongly constrained ephemeral tokens at ~$0.0368/min. Risks: preview and documented inconsistent voice replication/multi-speaker behavior.
4. **Palabra — best explicit voice-preserving interpreter/broadcast specialist.** WebRTC/WS, streaming audio and transcript outputs, zero-shot voice cloning, interrupt, glossaries, RTMP/SRT/HLS and official <1 s claim. Risks: conflicting public price presentations ($0.04/min versus packaged hourly plans), source files must run at 1×, and no reproducible latency benchmark.
5. **DeepL Voice — best secure/reconnectable enterprise sidecar.** One-use setup tokens, resumable WS, tentative/concluded text, incremental speech chunks, five text targets and one speech target. Risks: price unknown, one audio target, no verified source-voice preservation.
6. **Deepdub Live — strongest newly documented broadcast challenger.** Native broadcast protocols and output packaging, multi-language services and conventional key. Risks: early access, service cap, dynamic schemas, quote-only pricing, unknown latency/language count and unclear source-voice cloning.
7. **Azure Live Interpreter — enterprise fallback.** Mature identity, SDK and real-time interim output; official style/tone preservation claim. Risks: direct wire details and dynamic Live Interpreter price were not recoverable in fetched docs; no numeric latency; Voice Live is an agent API and should not be conflated with translation.
8. **Dubformer — batch-only leader for a second lane.** Excellent one-call/project mechanics, soundalikes and output controls, but not progressive live media.

## Rejection / hold table

| Candidate | Decision | Reason |
|---|---|---|
| Rask | **Hold for batch only** | Valid API v2 and strong language/cloning claims, but plan-gated, no live/progressive endpoint, API price and RTF unknown. |
| Papercup/RWS | **Reject for BYOK product integration** | Verified managed/hybrid service, no public self-serve API mechanics or price. |
| Speechmatics | **Reject as complete lane; retain as ASR/MT component** | Text translation only in fetched evidence; no translated audio or voice preservation. |
| Deepgram | **Reject as complete lane; retain as STT/TTS component** | Strong in-window TTS GA and interruption controls, but translation must be supplied elsewhere. |
| Cartesia | **Reject as complete lane; retain as cloned TTS component** | STT/TTS/voice cloning but no translation stage. |
| Google Cloud Speech cascade | **Deprioritize** | More integration/cost surfaces than Gemini direct S2S; no source-voice preservation. |
| Soniox | **Retain for cheapest text-translation cascade** | Excellent streaming text translation/key mechanics and public cost, but separate TTS and no voice preservation. |
| Smartcat | **Hold** | Promising 280+ and 500 ms marketing claim, but no fetched wire-level API reference or exact price. |
| Sarvam | **Batch/regional hold** | In-window realtime is text output; one-call voice-preserving output is batch/Indic-oriented. |
| Deepdub Managed Dub | **Enterprise batch hold** | Clear S3/HTTPS job mechanics but quote-only and source-speaker clone behavior not explicit in Managed Dub request. |
| Azure Voice Live | **Not a translator substitute** | Unified speech agent API; translation should use Speech Translation/Live Interpreter. |
| NVIDIA NemotronLabs VoiceChat 11B | **Out of BYOK scope** | In-window open model is conversational S2S, not verified commercial translation API; self-hosting is a different lane. |

## Evidence gaps

1. **Latency:** no shortlisted vendor supplied comparable p50/p95 first-translated-audio and steady-state lag on named language pairs, regions, packet sizes and concurrency. Marketing claims are not benchmark evidence.
2. **Voice identity:** OpenAI and DeepL do not promise source-speaker preservation in fetched docs; Gemini warns it is inconsistent; CAMB/Palabra explicitly support cloning but need consent/rights and multi-speaker tests; Deepdub Live exposes voice selection but not verified auto-cloning.
3. **Pricing:** CAMB realtime/broadcast and dubbing conversion, DeepL Voice, Deepdub Live/Managed, Dubformer dollars/min, Rask API and Azure Live Interpreter need account quotes or authenticated billing pages. Palabra’s $0.04/min hero conflicts with package rates and must be reconciled in writing.
4. **Protocol limits:** concurrent stream limits, session duration, target fan-out, region availability and reconnect semantics remain incomplete for CAMB broadcast, Palabra, Deepdub and Azure.
5. **Language truth:** marketing totals often mix ASR, translation and TTS. Pilot only exact intersections returned by catalog endpoints; do not use the largest homepage number as a pair matrix.
6. **Rights/compliance:** cloned-voice consent, provenance/watermarking, retention, model training, and broadcast redistribution terms require legal review. Palabra states zero retention; this report did not verify equivalent terms for every vendor.

## Data / evidence matrix

Legend: **B** batch one-call/project; **L** direct live S2S; **C** composable; **P** progressive audio; **VP** explicit voice preservation/cloning. “Unknown” means not stated in fetched official evidence.

| Vendor/product | Class | Ingest → output | Coverage | VP | Price | Numeric latency/RTF |
|---|---|---|---|---|---|---|
| CAMB batch | B | URL/YouTube/Drive → async final media + transcript | 140+ platform | Yes claim | Unknown unit | Unknown |
| CAMB realtime | L/P | WS PCM24k → text/audio deltas | 14 | Cloned voice | Unknown | Unknown; 30s+ slow cold boot |
| CAMB broadcast | L/P | SRT → SRT/RTMP/HLS | Catalog API | Auto clone or fixed IDs | Unknown | Unknown |
| OpenAI translate | L/P | WebRTC or WS PCM24k → audio/transcript deltas | Unknown list | Unknown | $0.034/min | Unknown |
| Gemini translate | L/P | WS PCM16k → PCM24k + transcripts | 70+ | Inconsistent | ~$0.0368/min | Unknown |
| Palabra | L/P | LiveKit/WS → PCM24k tracks/chunks + text; RTMP/SRT/HLS product | 60+ | Yes | $0.04/min claim / package rates | <1 s claim, method unknown |
| DeepL Voice | L/P | HTTPS session + WS → text + speech chunks | Catalog API; 5 text/1 audio target | No verified | Unknown | Unknown |
| Deepdub Live | L/P | HLS/SRT/RTMP/CMAF → HLS/SRT/RTMP + captions | Dynamic enums | Voice selection | Unknown | Unknown |
| Azure Interpreter | L/P via SDK | stream → interim/final text/audio | Feature matrix | Style/tone claim | Standard $2.50/h; LI unknown | Unknown |
| Dubformer | B | URL/YouTube → project → media/audio | 40+ source/100+ target API | Soundalike | Unknown $/min | Unknown |
| Rask | B | upload → project → media/subtitle/transcript | 135+; clone 32 | Yes | API unknown | Unknown |
| Deepdub Managed | B | HTTPS/S3 → job/export | Locales dynamic | Not explicit | Unknown | Unknown |
| RWS/Papercup | Managed B | source assets → human-validated deliverables | Unknown exact current API list | Yes | Quote | Unknown |
| Speechmatics | C | WS audio → source/translated text | 34 translation historical; 55+ STT | No | STT from $0.129/h | Unknown |
| Deepgram | C/P | WS STT + external MT + WS TTS | component-dependent | No clone | STT $0.0058/min multilingual; TTS listed | TTS/translation E2E unknown |
| Cartesia | C/P | WS STT + external MT + WS TTS | TTS 44 | Yes clone | $5/100K credits etc. | ~90 ms TTS TTFB only |
| Soniox | C/P | WS audio → translated text; separate TTS WS | 60+ | No | $0.12/h STT, ~$0.70/h TTS | Unknown |
| Sarvam | C + B | realtime WS → translated text; batch dub → media | realtime 24 codes; Indic dub | Batch yes | ₹30/h text translate; ₹40/min/target dub | Unknown |

## Recommendations

1. **Run a three-vendor live bake-off:** CAMB, OpenAI and Gemini. Add Palabra if preserved speaker identity is a hard launch requirement. Test identical clean/noisy, single/multi-speaker and code-switch clips for EN↔ES, EN↔FR plus NuvioTV priority pairs.
2. **Instrument rather than trust labels:** client timestamps for first source audio sent, first translated audio playable, median/95th lag over five minutes, end flush lag, dropout rate, reconnect loss and RTF for file replay. Keep TTS TTFB separate from full translation lag.
3. **Split architecture explicitly:** live sidecar (`WebRTC` for end-user client or media-worker WS) versus asynchronous episode localization (CAMB/Dubformer/Rask). Never route an upload through Palabra’s 1× file replay and call it batch dubbing.
4. **Prototype key broker:** Android authenticates to NuvioTV backend; backend exchanges the user’s stored vendor key for OpenAI client secret, Gemini ephemeral constrained token, Palabra publisher JWT, DeepL one-use token, or Cartesia temporary token. CAMB currently requires server proxy/worker because no ephemeral client credential was verified.
5. **Demand quote/SLA answers before selection:** exact per-input/output minute price; fan-out multiplication; silence billing; p50/p95 by region; max session/concurrency; supported pair intersection; retention/training; cloned-voice consent; redistribution; failure credits.
6. **Keep a cascade fallback:** Soniox translated text plus Cartesia or Deepgram TTS offers transparent component pricing and control, but label output as selected/cloned TTS rather than source-preserving unless tested and contractually supported.

## Open questions

- Which NuvioTV language pairs and maximum tolerable live delay are launch blockers?
- Is preserving the original actor’s identity legally permitted, or should NuvioTV use licensed stock voices?
- Does “BYOK” require keys to remain only on-device, or is encrypted storage and short-lived brokerage by NuvioTV acceptable?
- What media input is primary: player PCM tap, HLS audio track, SRT contribution feed, or uploaded file? This changes the winner.
- Vendor account/API calls were not executed because no user keys were supplied. An authenticated pilot is required to verify catalogs, quotas, pricing ledgers and actual responses.

## Historical background (published before 2026-07-31)

- CAMB batch and broadcast docs were last modified 2026-07-14/23; they establish the batch/live separation used above. [Batch](https://docs.camb.ai/api-reference/endpoint/end-to-end-dubbing) · [broadcast](https://docs.camb.ai/api-reference/endpoint/streaming/overview)
- Gemini 3.5 Live Translate’s guide was last updated 2026-07-23 and its model page identifies June 2026 as the latest model update; in-window edits do not establish an in-window model launch. [guide](https://ai.google.dev/gemini-api/docs/live-api/live-translate)
- OpenAI’s cookbook launch is dated 2026-05-07; current mechanics were fetched from undated live docs. [cookbook](https://developers.openai.com/cookbook/examples/voice_solutions/realtime_translation_guide)
- Speechmatics unified translation announcement is from 2023 and is background only. [announcement](https://www.speechmatics.com/company/articles-and-news/our-new-unified-speech-translation-api)
- Soniox real-time translation launch is from 2025; current API/pricing pages were used for present mechanics. [launch](https://soniox.com/blog/2025-06-17-realtime-multilingual-translation-ai)
- DeepL Voice speech output became GA 2026-07-17; the in-window finding is reporting tags, not GA. [changelog](https://developers.deepl.com/docs/resources/roadmap-and-release-notes)

## Operational receipt

**Research date:** 2026-08-29 (tool timestamps crossed UTC midnight in some search responses; inclusion remained fixed to 2026-07-31..2026-08-29).  
**Discovery:** Parallel CLI was attempted first as requested. Every attempted Parallel search returned the same raw API response and exit code 4:

```json
{
  "error": {
    "message": "Insufficient credit in account, please check your plan and billing details. You can add more funds from the billing page at https://platform.parallel.ai/settings?tab=billing and check for pricing details at https://platform.parallel.ai/pricing.",
    "type": "APIStatusError"
  }
}
```

Per the web-search skill’s failure route, discovery continued with `agent-web search --json`; official pages were then HTTP-fetched with `agent-web scrape`. No authenticated vendor API request was made.

### Search queries executed

1. `AI dubbing live speech translation simultaneous interpretation API launch August 2026 official` (one-month filter)
2. `Palabra AI API docs streaming speech translation pricing languages latency`
3. `CAMB.AI API documentation MARS TTS streaming speech translation pricing`
4. `Deepdub API documentation dubbing API pricing voice cloning official`
5. `Dubformer API documentation official pricing real-time dubbing`
6. `Rask AI API documentation dubbing pricing supported languages voice cloning official`
7. `Papercup AI dubbing API pricing languages voice official`
8. `Azure Speech Services real-time live interpreter voice API pricing official`
9. `site:ai.google.dev Gemini Live API audio translation WebSocket ephemeral tokens pricing languages official`
10. `site:cloud.google.com speech translation streaming API audio pricing official`
11. `site:platform.openai.com/docs/guides/realtime translation audio WebRTC ephemeral key official`
12. `site:openai.com/api/pricing realtime audio pricing official`
13. `site:docs.speechmatics.com realtime translation API languages pricing official`
14. `site:developers.deepgram.com translation streaming speech API pricing languages official`
15. `site:docs.cartesia.ai speech translation websocket TTS pricing languages official`
16. `real-time speech-to-speech translation API August 2026 launch official AI`
17. `"August 2026" "speech translation" API launch`
18. `"August 2026" "AI dubbing" API launch`
19. `site:developers.openai.com gpt-realtime-translate pricing API model official`
20. `Speechmatics translation API docs websocket pricing supported languages official`
21. `Deepgram speech translation API translation feature official docs languages`
22. `Cartesia pricing official Sonic voice clone languages WebSocket`
23. `Smartcat real-time voice translation API docs pricing official languages WebSocket`
24. `Soniox realtime translation API docs pricing languages official websocket`
25. `official voice AI API changelog August 2026 realtime speech translation dubbing`
26. Vendor-specific August 2026 changelog searches for Cartesia, Deepgram, CAMB, Palabra and OpenAI
27. `Sarvam saaras v3 realtime WebSocket API docs pricing languages August 2026 official`
28. `DeepL Voice API release notes August 2026 official docs price API`
29. Official repository searches for CAMB and Gemini live examples
30. Rask endpoint, Deepdub Live, Dubformer create-project, and Papercup API searches

### Official URLs actually fetched

**CAMB.AI:** [docs index](https://docs.camb.ai/llms.txt), [introduction](https://docs.camb.ai/introduction), [auth](https://docs.camb.ai/getting-started/authentication), [realtime WS](https://docs.camb.ai/api-reference/websockets/realtime), [realtime tutorial](https://docs.camb.ai/tutorials/realtime-translation-with-sdk), [streaming overview](https://docs.camb.ai/api-reference/endpoint/streaming/overview), [create stream](https://docs.camb.ai/api-reference/endpoint/streaming/create-new-stream), [batch API](https://docs.camb.ai/api-reference/endpoint/end-to-end-dubbing), [batch tutorial](https://docs.camb.ai/tutorials/dubbing-with-sdk), [pricing](https://www.camb.ai/pricing), [Python repo](https://github.com/Camb-ai/cambai-python-sdk), [TypeScript repo](https://github.com/Camb-ai/cambai-typescript-sdk).

**OpenAI:** [translation guide](https://developers.openai.com/api/docs/guides/realtime-translation), [Realtime overview](https://developers.openai.com/api/docs/guides/realtime), [model](https://developers.openai.com/api/docs/models/gpt-realtime-translate), [pricing](https://developers.openai.com/api/docs/pricing), [cookbook](https://developers.openai.com/cookbook/examples/voice_solutions/realtime_translation_guide), [cookbook repo](https://github.com/openai/openai-cookbook).

**Google:** [Live Translate](https://ai.google.dev/gemini-api/docs/live-api/live-translate), [Live API](https://ai.google.dev/gemini-api/docs/live-api), [Live API reference](https://ai.google.dev/api/live), [model](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-live-translate-preview), [ephemeral tokens](https://ai.google.dev/gemini-api/docs/live-api/ephemeral-tokens), [pricing](https://ai.google.dev/gemini-api/docs/pricing), [changelog](https://ai.google.dev/gemini-api/docs/changelog), [Live Transcribe](https://ai.google.dev/gemini-api/docs/live-api/live-transcribe), [example repo](https://github.com/google-gemini/gemini-live-api-examples), [Cloud STT streaming](https://docs.cloud.google.com/speech-to-text/docs/v1/transcribe-streaming-audio), [STT pricing](https://cloud.google.com/speech-to-text/pricing), [TTS pricing](https://cloud.google.com/text-to-speech/pricing), [Translate](https://docs.cloud.google.com/translate/docs/translate-text).

**Palabra:** [S2S overview](https://docs.palabra.ai/docs/streaming_api), [WS quickstart](https://docs.palabra.ai/docs/quick-start/websockets), [auth](https://docs.palabra.ai/docs/auth), [sessions](https://docs.palabra.ai/docs/streaming_api/session), [audio I/O](https://docs.palabra.ai/docs/streaming_api/publishing_and_receiving_audio), [management](https://docs.palabra.ai/docs/streaming_api/management), [files](https://docs.palabra.ai/docs/streaming_api/processing_files), [languages](https://docs.palabra.ai/docs/languages), [product](https://www.palabra.ai/voice-translation-api), [pricing](https://www.palabra.ai/pricing), [Python repo](https://github.com/PalabraAI/palabra-ai-python).

**DeepL:** [overview](https://developers.deepl.com/docs/voice/overview), [quickstart](https://developers.deepl.com/docs/voice/real-time-voice-quickstart), [sessions](https://developers.deepl.com/docs/voice/understanding-voice-sessions), [session request](https://developers.deepl.com/api-reference/voice/request-session), [languages](https://developers.deepl.com/docs/voice/supported-voice-languages), [changelog](https://developers.deepl.com/docs/resources/roadmap-and-release-notes), [pricing landing page](https://www.deepl.com/en/pro#api), [Python repo](https://github.com/DeepL/deepl-python).

**Deepdub:** [docs index](https://deepdub.mintlify.app/llms.txt), [introduction](https://deepdub.mintlify.app/introduction), [Managed Dub](https://deepdub.mintlify.app/api-reference/submit-dubbing-job), [TTS stream](https://deepdub.mintlify.app/api-reference/tts/generate-and-stream-tts-audio), [Live](https://docs.deepdub.ai/api-reference/live/overview), [product](https://deepdub.ai/api-voices), [repo](https://github.com/deepdub-ai/deepdub-api).

**Dubformer:** [docs index](https://docs.dubformer.ai/llms.txt), [overview](https://docs.dubformer.ai/platform/overview), [auth](https://docs.dubformer.ai/platform/authentication), [create](https://docs.dubformer.ai/platform/endpoints/projects/create-project.md), [options](https://docs.dubformer.ai/platform/endpoints/get-options), [balance](https://docs.dubformer.ai/platform/endpoints/get-balance), [site](https://www.dubformer.ai/).

**Azure:** [translation](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/speech-translation), [quickstart](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/get-started-speech-translation), [languages](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/language-support?tabs=translation), [Voice Live](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/voice-live), [Voice Live how-to](https://learn.microsoft.com/en-us/azure/ai-services/speech-service/voice-live-how-to), [pricing](https://azure.microsoft.com/en-us/pricing/details/speech/), [sample repo](https://github.com/Azure-Samples/cognitive-services-speech-sdk).

**Other official sources:** [Rask docs](https://docs.api.rask.ai/introduction), [Rask product](https://www.rask.ai/api), [Rask language facts](https://www.rask.ai/llm-info), [Rask pricing](https://www.rask.ai/pricing), [RWS/Papercup](https://www.rws.com/localization/services/translation-services/video-and-audio-translation/ai-dubbing-and-vo/), [Speechmatics API](https://docs.speechmatics.com/api-ref/realtime-transcription-websocket), [Speechmatics pricing](https://www.speechmatics.com/pricing), [Deepgram changelog](https://developers.deepgram.com/changelog), [Deepgram TTS](https://developers.deepgram.com/reference/text-to-speech/speak-streaming), [Deepgram pricing](https://deepgram.com/pricing), [Cartesia overview](https://docs.cartesia.ai/get-started/overview), [Cartesia STT](https://docs.cartesia.ai/api-reference/stt/websocket), [Cartesia auth](https://docs.cartesia.ai/get-started/authenticate-your-client-applications), [Cartesia pricing](https://www.cartesia.ai/pricing), [Soniox STT](https://soniox.com/docs/api-reference/stt/websocket-api), [Soniox TTS](https://soniox.com/docs/api-reference/tts/websocket-api), [Soniox pricing](https://soniox.com/pricing), [Smartcat voice API](https://www.smartcat.com/software-translator/real-time-voice-translation-api/), [Sarvam changelog](https://docs.sarvam.ai/changelog), [Sarvam realtime](https://docs.sarvam.ai/api/api-guides-tutorials/speech-to-text/realtime-streaming), [Sarvam pricing](https://docs.sarvam.ai/api/getting-started/pricing).

### Raw API responses

- **Parallel:** exact insufficient-credit JSON reproduced above.
- **GitHub public API (unauthenticated):** repository metadata and commits/releases were fetched for Palabra, CAMB, Deepdub, Google examples, Azure samples, OpenAI Cookbook and DeepL. Relevant response facts: Palabra Python commits/releases on 2026-08-03 and 2026-08-20; CAMB Python/TS pushed mode changes 2026-08-27; Deepdub live-reference commit 2026-08-13. GitHub subsequently rate-limited individual commit-detail requests; no retry loop was used.
- **Vendor product APIs:** not called (no user API keys), so capability claims are documentation-level until pilot validation.
