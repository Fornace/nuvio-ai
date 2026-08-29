# Fast subtitle generation research

**Research cutoff:** 2026-08-29. **Priority window:** 2026-07-31 through 2026-08-29 inclusive. ElevenLabs was excluded. Older sources are labeled historical background.

## Executive summary

For NuvioTV, the fastest useful subtitle design is not a whole-title batch request. It is a streaming ASR session fed from a host-owned PCM tap, with finalized word or sentence timestamps inserted into the existing sidecar cue renderer. The strongest new in-window candidate is **Gemini 3.5 Transcribe**, introduced August 13 and promoted to GA August 26. It supports incremental live text, 85+ languages, automatic code-switching and hybrid VAD, but its live path has only utterance timestamps and ten-minute sessions. The strongest timestamped streaming candidate that was both documented and measured here is **Alibaba Qwen-Audio-3.0-ASR-Flash-Streaming**: unlimited sessions, word/sentence timestamps, rich audio formats, and a real test produced a correct 7.898 s transcript with first result at 1.704 s and final at 8.711 s.

For ahead-of-playback batch generation, **Cloudflare Whisper Large V3 Turbo** is the cost and integration outlier at $0.00051/audio minute; a real test transcribed the same 7.898 s clip in 3.875 s wall time with exact text and word timestamps. **Groq Whisper Large V3 Turbo** is a mature low-cost alternative at $0.04/audio hour, but it is file/chunk based rather than streaming. **Mistral Voxtral Realtime** is the best open-weight controllable streaming ASR, with a configurable 80–2400 ms delay and a 4B footprint, while **WhisperX** remains the strongest open long-form post-processing option when forced alignment and diarization matter more than time-to-first-caption.

No candidate should be selected from vendor WER or “latency” claims alone. Streaming latency, batch real-time factor, long-form drift, timestamp error, subtitle segmentation, code-switching, background-music robustness, and provider cost must be measured on the same film/TV corpus.

## Priority-window findings

### Gemini 3.5 Transcribe, August 13 launch and August 26 GA

Google's release notes introduced `gemini-3.5-transcribe` and `gemini-3.5-transcribe-live` on August 13 and marked them generally available August 26 [official changelog](https://ai.google.dev/gemini-api/docs/changelog). The batch model detects 85+ languages, handles code-switching, supports diarization, word timestamps, custom vocabulary and Smart transcription. Unary requests allow one hour, reduced to 30 minutes with diarization or word timestamps [model page](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-transcribe).

The live model uses a bidirectional WebSocket, accepts 16-bit PCM in recommended 100 ms chunks, emits speculative `interim_input_transcription` updates and finalized `input_transcription`, and supports server or hybrid VAD. Hybrid VAD lets the client send `audio_stream_end` as soon as local silence is detected to avoid the server's silence wait [live guide](https://ai.google.dev/gemini-api/docs/live-api/live-transcribe). Limitations matter for subtitles: sessions last at most ten minutes, live diarization is unavailable, and live output has utterance-level rather than word-level timestamps. Nuvio would need transparent session rotation and cue boundary derivation.

### Deepgram August 2026 updates

Deepgram updated Nova-3 repeatedly in August: language additions and quality improvements on August 4, 5, 7, 10, 17, 27 and 28, with both batch and streaming variants. On August 28, Flux received runtime turn-ending control through `Configure` messages [official changelog](https://developers.deepgram.com/changelog). These improve coverage and controllable finalization, but the fetched in-window pages do not publish a new cross-vendor-comparable RTF or film/TV WER. Nova-3 remains a serious streaming candidate because its production API already supports interim/final text, word timings, punctuation and diarization, but selection needs a direct paid bake-off.

### Cartesia August updates

Cartesia's August changelog adds keyterm prompting to **Ink-2** and exposes turn-detection tuning in its Playground/API [official changelog](https://docs.cartesia.ai/changelog/2026). Ink-2's `/stt/turns/websocket` takes binary audio, recommends 100 ms chunks, and emits `turn.start`, repeated `turn.update`, eager-end/resume and `turn.end`; it is English-only [official WebSocket reference](https://docs.cartesia.ai/api-reference/stt/turns/websocket). A credentialed handshake test returned HTTP 402, proving the existing key authenticates but has no usable balance. No timing claim is made from that failed probe.

### Qwen current documentation updated August 25–26

Alibaba's raw WebSocket reference was updated August 25 and the model/user pages August 26. The recommended streaming model, `qwen-audio-3.0-asr-flash-streaming`, supports multilingual audio and dialects, hotwords and prompt context, unlimited mono streaming, formats including PCM/WAV/MP3/Opus/AAC, and sentence plus word timestamps by default. `qwen3-asr-flash-realtime` has broader emotion output but currently no timestamps, so it is a worse subtitle choice [model selector](https://www.alibabacloud.com/help/en/model-studio/asr-model), [streaming guide](https://www.alibabacloud.com/help/en/model-studio/real-time-speech-recognition-user-guide), [WebSocket API](https://www.alibabacloud.com/help/en/model-studio/fun-asr-realtime-websocket-api).

## Hosted shortlist

| Rank | Candidate | Mode and subtitle timing | Main advantage | Main limitation |
|---:|---|---|---|---|
| 1 | **Qwen-Audio-3.0-ASR-Flash-Streaming** | True WebSocket/AOQ stream; word + sentence timestamps; unlimited | Best verified match for progressive captions; actual key and timing test succeeded | Workspace/region setup; no diarization in streaming; provider benchmarks need corpus validation |
| 2 | **Gemini 3.5 Transcribe Live + batch** | Incremental live text with utterance timestamps; batch word timestamps/diarization | Fresh GA, 85+ languages, code-switching, Smart/verbatim modes, hybrid VAD | Ten-minute live sessions; no live word times or diarization |
| 3 | **Deepgram Nova-3** | Streaming and prerecorded with interim/final words, timing and diarization | Mature media API, broad rapidly updated language coverage | Current direct price/latency/film-corpus evidence must be measured; no in-window universal benchmark |
| 4 | **AssemblyAI Universal-3.5 Pro Streaming** | Streaming, realtime diarization, native code-switching in 18 languages | Official docs claim sub-300 ms and fastest word emissions | Session-based billing; candidate claim must be independently timed; fewer languages than Gemini/Qwen |
| 5 | **Mistral Voxtral Realtime / Transcribe 2** | Realtime configurable down to sub-200 ms; batch word timestamps + diarization | Open-weight realtime model, controllable delay, 4B edge footprint; batch accepts up to 3 hours | Realtime and diarization cannot be combined; 13 languages |
| 6 | **Groq Whisper Large V3 Turbo** | Batch/file URL with word or segment timestamps | $0.04/audio hour, 100 MB dev file, easy chunking, mature OpenAI shape | No streaming session; URL fetch cannot carry arbitrary source headers; chunk boundary work belongs to Nuvio |
| 7 | **Cloudflare Whisper Large V3 Turbo** | Batch REST; segments and words | $0.00051/min, direct successful test, excellent broker fit | Not streaming; Workers AI limits and long-form accuracy need measurement |
| 8 | **Soniox v4** | Realtime and async text/translation tokens | Extremely low public rates and 60+ languages | Speech-to-text translation is text output, not automatic subtitle timing proof; direct bake-off needed |
| 9 | **Cartesia Ink-2** | Turn streaming, tunable endpointing | Useful provisional/final event model and keyterm prompting | English-only; current local key has no balance; no word timestamps in fetched turn schema |

### Batch-only and component holds

- Google Cloud Speech, Azure Fast/Batch Transcription, Speechmatics and Amazon Transcribe are credible enterprise batch services. They remain controls, not assumed speed leaders, until timed on identical files and regions.
- OpenAI transcription endpoints are easy to integrate but current fetched material does not establish a speed, timestamp or cost advantage over the shortlist.
- Fireworks and fal-hosted Whisper are convenient wrappers; no fresh primary evidence found here justifies choosing them ahead of the underlying model on Groq/Cloudflare or self-hosting.
- Cloudflare's older `@cf/openai/whisper` and tiny English model are retained only as cost/speed controls; use Large V3 Turbo for the quality candidate.

## Open-source shortlist

| Rank | System | Exact class | Evidence and fit |
|---:|---|---|---|
| 1 | **Voxtral Mini Transcribe Realtime 2602** | Apache-2.0 4B streaming ASR | 13 languages, user-selectable delay in 80 ms increments through 1.2 s plus 2.4 s, WebSocket serving, open weights. Best adjustable latency/quality research baseline [model card](https://huggingface.co/mistralai/Voxtral-Mini-4B-Realtime-2602). |
| 2 | **WhisperX** | Batched Whisper + VAD + forced alignment + optional diarization | Repo claims 70× realtime on large-v2 under its benchmark setup, word alignment, VAD and speaker labels. Strong post-pass for finalized VTT/SRT, not a low-latency streaming UI [official repo](https://github.com/m-bain/whisperX). |
| 3 | **faster-whisper** | CTranslate2 Whisper runtime | Excellent throughput/batching and lower memory, useful first-pass transcription. Native Whisper timestamps need forced alignment for production subtitle precision [official repo](https://github.com/SYSTRAN/faster-whisper). |
| 4 | **NVIDIA Nemotron 3.5 ASR Streaming 0.6B** | Open streaming ASR | Exact 80–1120 ms chunk/right-context operating points and large-stream GPU throughput; useful deployment baseline, but Nuvio needs timestamp support verification [model card](https://huggingface.co/nvidia/nemotron-3.5-asr-streaming-0.6b). |
| 5 | **NVIDIA Parakeet/Canary** | ASR or speech-to-text translation | Parakeet is strong English streaming/offline ASR; Canary supplies speech translation text. Neither alone produces multilingual timed subtitles for every target; use as controlled components. |

## Real timing receipts

All probes used a synthetic macOS `say` English clip on an Apple M5 Max, encoded to 16 kHz mono. They test transport/mechanics and timing, not real-film accuracy.

### Qwen streaming ASR

- SDK: `dashscope 1.27.2`; model `qwen-audio-3.0-asr-flash-streaming`; Singapore legacy endpoint; PCM16 streamed in real time as 100 ms frames.
- Source: 7.898 s, 24 recognized words.
- First transcription event: **1.704 s after probe start**.
- Final event: **8.711 s**, wall completion **9.019 s**.
- Final text exactly matched the source sentence.
- Final response included sentence start 120 ms/end 7880 ms and word start/end times. Two standalone-space word tokens appeared, so cue construction must normalize tokens before segmentation.
- Raw local receipt: `/tmp/qwen_asr_probe.json` during the research run. Secret values were not persisted in the receipt.

### Cloudflare batch ASR

- Model `@cf/openai/whisper-large-v3-turbo`; REST API; 166,072-byte FLAC, Base64 JSON, VAD enabled.
- Source: same 7.898 s clip.
- HTTP 200 wall time: **3.875 s**, observed batch RTF **0.491** including local upload/network/API time.
- Exact source text returned, 22 API-counted words, 3 segments, with per-word start/end timing.
- This is one short clean sample. It does not establish long-form RTF or film accuracy.

### Cartesia Ink-2 authentication probe

- Official Python client 4.1.0, `wss://api.cartesia.ai/stt/turns/websocket`, Ink-2, PCM16/16 kHz.
- Server returned **HTTP 402** in 0.682 s. The key is present and API authentication reached billing enforcement, but no model timing result exists.

## Subtitle mechanics and output policy

1. Capture/decode audio locally, downmix on a side branch and stream 100 ms frames without blocking playback.
2. Tag every frame with playback generation, media epoch, sequence and source media time. Never use only socket wall time.
3. Treat provisional hypotheses as replaceable. Store a stable cue ID and revision; render provisional text only if the product accepts visible correction.
4. Commit cues from finalized word/sentence timestamps. Normalize punctuation, standalone whitespace tokens, repeated partials and code-switch metadata.
5. Segment for television readability after ASR: maximum two lines, safe character rate, sentence/punctuation preferences, and no cue overlap. Preserve raw words separately so corrected segmentation does not require ASR rerun.
6. On seek, start a new epoch, cancel old provisional work and prioritize a window around the target. Never attach late old-epoch results.
7. Rotate Gemini ten-minute sessions before the limit with overlap and timestamp deduplication. Qwen's unlimited stream still needs reconnection recovery and durable finalized shards.
8. Cache only finalized cues under exact media/audio-track/model/language fingerprints. Do not include signed URLs or headers in keys.
9. Separate same-language transcription from translation. A speech-translation model that only outputs English is not a general target-language subtitle generator.
10. Label the first product “Generated dialogue subtitles (beta).” None of these ASR models automatically guarantees SDH sound-effect descriptions.

## Reproducible Nuvio benchmark protocol

### Corpus

Use owned/test media with exact references:

- 24 short clips (15–90 s) across clean dialogue, music, effects, whisper, shouting, accents, code-switching and overlap.
- 12 continuous 22-minute episodes across at least four source languages.
- 6 continuous 100-minute films, including alternate cuts.
- Embedded/reference subtitle files manually corrected to word/cue timing.
- Identical 16 kHz mono FLAC/PCM shards for hosted comparisons, plus original compressed tracks for ingestion tests.

### Reported metrics

- Connection setup, first provisional text, first finalized caption, p50/p95 event lag relative to source media time.
- Full wall time and RTF for batch; upload, queue and inference separated where APIs expose it.
- WER/CER by acoustic condition and language; never average unlike corpora or model language intersections silently.
- Word timestamp median/p95 absolute error; cue onset/end error; drift per 20 minutes.
- Partial churn: replacements, retractions, edit distance from partial to final.
- Subtitle metrics: characters/second, line overflow, overlap, orphaned words, reading-time violations.
- Speaker diarization DER only for models/modes that actually support it.
- Exact provider charge, retry/cancel leakage, bytes uploaded/downloaded, and long-session failure rate.
- Seek recovery at 5, 30 and 60 minutes, reconnection/session rotation, and old-epoch contamination.

### Acceptance gates

- First stable cue p50 ≤ 3 s and p95 ≤ 6 s once the media is playing.
- Median cue onset error ≤ 250 ms, p95 ≤ 750 ms, no cumulative drift >1 s per 20 minutes.
- Clean-dialogue WER ≤15%, with music/overlap reported separately.
- 100-minute job resumes after injected failure without retranscribing completed finalized shards.
- No URL credential/header/API key appears in logs, receipts, cache names or subtitle files.
- Provider invoice is within ±10% of locally metered audio duration/tokens for ≥95% of jobs.

## Recommendation

Build one host contract with two provider modes:

1. **Live captions:** Qwen-Audio-3.0 streaming first because it offers verified word timestamps and worked with an available key; Gemini Transcribe Live second for broad code-switching and provider diversity; Deepgram/AssemblyAI in the paid bake-off.
2. **Ahead-of-playback and finalization:** Cloudflare Whisper V3 Turbo as the cheap batch baseline; Groq as the high-throughput API control; WhisperX/Voxtral as self-hosted/open controls.
3. Run a correction pass over live output: finalized Qwen/Gemini cues can be replaced by a slower batch or forced-alignment artifact behind the current playhead, never rewriting cues already shown unless the user requests regeneration.

## Evidence gaps

- No common current benchmark compares the candidates on long-form film audio, same hardware/region and same timestamp metric.
- Vendor “sub-200 ms,” “sub-300 ms,” or “70×” numbers describe different quantities and are not interchangeable with first stable subtitle latency.
- Qwen and Gemini pricing-to-minute conversion requires authenticated usage reconciliation; token-based pricing must be measured from raw provider usage.
- Cloudflare's short successful probe does not prove long-title scaling, request-size limits or language quality.
- Live ASR usually lacks reliable diarization and SDH generation. Those need separate stages and tests.
- Local Android TV capture and thermal behavior remain unmeasured; source access and player integration reports define the required host seam.

## Fetched-source receipt

Reviewed 2026-08-29. Parallel Deep Research returned insufficient credit, so discovery used `agent-web search`, followed by direct official-page extraction. Current sources actually fetched:

- Google: [Gemini changelog](https://ai.google.dev/gemini-api/docs/changelog), [model](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-transcribe), [live guide](https://ai.google.dev/gemini-api/docs/live-api/live-transcribe), [transcription](https://ai.google.dev/gemini-api/docs/transcribe), [pricing](https://ai.google.dev/gemini-api/docs/pricing).
- Alibaba: [model selector](https://www.alibabacloud.com/help/en/model-studio/asr-model), [realtime guide](https://www.alibabacloud.com/help/en/model-studio/real-time-speech-recognition-user-guide), [WebSocket API](https://www.alibabacloud.com/help/en/model-studio/fun-asr-realtime-websocket-api), [pricing](https://www.alibabacloud.com/help/en/model-studio/model-pricing).
- Deepgram: [changelog](https://developers.deepgram.com/changelog), [pricing](https://deepgram.com/pricing), current STT documentation discovered/fetched through official pages.
- Cartesia: [2026 changelog](https://docs.cartesia.ai/changelog/2026), [turn WebSocket](https://docs.cartesia.ai/api-reference/stt/turns/websocket), [authentication](https://docs.cartesia.ai/get-started/authenticate-your-client-applications), installed official Python SDK 4.1.0.
- Mistral: [audio overview](https://docs.mistral.ai/studio/audio/overview), [realtime model card](https://huggingface.co/mistralai/Voxtral-Mini-4B-Realtime-2602).
- AssemblyAI: [models](https://www.assemblyai.com/docs/getting-started/models).
- Groq: [speech-to-text](https://console.groq.com/docs/speech-to-text), [Whisper Turbo model](https://console.groq.com/docs/model/whisper-large-v3-turbo).
- Cloudflare: [Whisper V3 Turbo model](https://developers.cloudflare.com/workers-ai/models/whisper-large-v3-turbo/), [REST API](https://developers.cloudflare.com/workers-ai/get-started/rest-api/), [pricing](https://developers.cloudflare.com/workers-ai/platform/pricing/).
- Open historical sources: [WhisperX](https://github.com/m-bain/whisperX), [faster-whisper](https://github.com/SYSTRAN/faster-whisper), [Nemotron ASR](https://huggingface.co/nvidia/nemotron-3.5-asr-streaming-0.6b).

Credentialed raw response receipts contained no keys: DashScope auth probe HTTP 200; Qwen ASR successful events; Cloudflare Workers AI HTTP 200; Cartesia HTTP 402. The repository retains summarized benchmark receipts only, not `/tmp` raw files or secrets.
