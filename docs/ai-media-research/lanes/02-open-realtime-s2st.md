# Open near-real-time speech-to-speech lane

**Research cutoff:** 2026-08-29. **Priority window (inclusive): 2026-07-31-2026-08-29.** ElevenLabs was excluded from the evidence set and comparisons.

## Executive summary

The strongest **in-window translation documentation update** is **Qwen3.5-LiveTranslate-Flash-Realtime**: its official documentation was updated August 26, supports continuous audio/video-to-text/audio interpretation, 60 input/text languages, 29 spoken outputs, voice cloning, hotwords, and reports vendor latency as low as 2.8 s. The stable alias maps to a 2026-05-19 snapshot, so the public product release date is unverified. The strongest new open speech runtime is NVIDIA's August 3 **NemotronLabs VoiceChat 11B**, but it is an English conversational full-duplex S2S agent, not translation; NVIDIA's 26.07 translation offering remains a deployable **ASR → NMT → TTS cascade** whose new Magpie controls improve streaming synthesis but whose full-pipeline end-to-end delay is not published.

For an Android media application, the practical first implementation should be a **remote API sidecar**, ranked **Qwen3.5 LiveTranslate**, **Gemini 3.5 Live Translate**, then **GPT-Realtime-Translate**. If “open” means downloadable weights, test **Hibiki-Zero** (best natural/voice-preserving open Simul-S2ST) and **SimulU** (best new training-free policy for broad language coverage), while treating **Voxtral Realtime, Parakeet/Nemotron ASR, Canary, and Magpie** as components, not standalone S2ST translators.

## Scope and terminology

- **ASR:** source speech → source-language text. Voxtral Realtime, Parakeet, and Nemotron ASR belong here.
- **AST / S2TT:** source speech → translated text. Canary and streaming Canary policies belong here.
- **S2ST / simultaneous interpretation:** source speech → translated speech while source audio is still arriving. Hibiki(-Zero), SeamlessStreaming, StreamSpeech, SimulTron, SimulU, Qwen3.5 LiveTranslate, Gemini 3.5 Live Translate, and GPT-Realtime-Translate belong here.
- **Duplex voice agent:** listens and speaks concurrently but normally *answers* rather than translates. Qwen Omni, JoyAI-Talker, and NemotronLabs VoiceChat belong here unless a dedicated translation checkpoint/session is documented.
- **Voice-preserving translation / live dubbing:** translated speech preserves or adapts source identity/prosody. This is not equivalent to generic TTS with a fixed target voice.

## Priority-window findings: 2026-07-31-2026-08-29

### 1. Qwen3.5-LiveTranslate-Flash-Realtime , the most important in-window finding

**What it is.** A closed, hosted simultaneous audio/video translation model, not released weights. The official page is dated **last updated August 26, 2026** and identifies the stable alias as snapshot `qwen3.5-livetranslate-flash-realtime-2026-05-19` [official model/API page](https://www.alibabacloud.com/help/en/model-studio/qwen3-5-livetranslate-flash-realtime).

**Capability.** Audio is required; video frames are optional. It translates 60 speech-input/text-output languages, with speech output for 29 languages. It supports visual disambiguation, configurable hotwords, automatic intonation/emotion matching, and three voice-clone modes: pre-cloned, clone once, or clone before every response [official product blog](https://www.alibabacloud.com/blog/qwen3-5-livetranslate-from-sound-to-sight-from-word-to-right_603156). This is true translation rather than a prompted voice assistant.

**Streaming and correction behavior.** WebSocket, AOQ, and WebRTC are documented. In default VAD mode, translation typically starts before source speech ends. Text events explicitly contain **confirmed text and tentative predicted text** (`text` plus `stash`), after which a done event returns the full result; translated audio arrives incrementally. That is stronger evidence of a correction-aware incremental interface than the delta-only descriptions published for Gemini/OpenAI [official model/API page](https://www.alibabacloud.com/help/en/model-studio/qwen3-5-livetranslate-flash-realtime).

**Delay.** Alibaba's current model page claims latency as low as **2.8 s**. A vendor blog reports average S2ST per-token latency of 2.8 s and says Readable Unit chunk-wise streaming lowered first-token latency by 3.45 s and per-token latency by 1.88 s versus Qwen3 LiveTranslate. These are vendor-reported metrics and are not the same as end-to-end first-playable audio delay, p95 source-to-playback lag, or average lagging [official model page](https://www.alibabacloud.com/help/en/model-studio/qwen3-5-livetranslate-flash-realtime), [official product blog](https://www.alibabacloud.com/blog/qwen3-5-livetranslate-from-sound-to-sight-from-word-to-right_603156).

**Size/license/hardware.** Parameter count, model-weight license, serving hardware, RTF, and Android-native footprint are **not disclosed**. It is a metered remote Model Studio API. The docs' microphone example sends 16 kHz mono PCM in 100 ms chunks and plays 24 kHz PCM output. Android feasibility is therefore high as a remote WebSocket/WebRTC client, low/unknown on-device.

### 2. NVIDIA 26.07 / August documentation , useful open components, but do not mislabel them

**NemotronLabs VoiceChat 11B (released August 3).** This is an open-weight, English, unified full-duplex conversational S2S model with reported ~450 ms turn-taking latency, an OpenMDW-1.1 license, A100-class support, offline checkpoint inference, and an optimized WebSocket container for interactive streaming [official model card](https://huggingface.co/nvidia/NVIDIA-NemotronLabs-VoiceChat-11B). It is **not a translation model** and does not preserve the input speaker as translated output.

**Speech NIM 26.07.0.** The August 13 release documentation adds/expands:

- Magpie Multilingual and Magpie Zeroshot to 12 languages; Zeroshot performs reference-audio voice cloning.
- **Word-level streaming** for Magpie Multilingual with tunable `flush`, `chunk_len_threshold`, and `max_chunk_threshold`; it flushes at punctuation instead of a hard 400-character boundary.
- End-of-chunk word timestamps (millisecond start/end offsets).
- Expanded Nemotron ASR Streaming profiles and text normalization.

These changes are primary evidence that an NVIDIA cascade can be tuned for timing, but not evidence of a new end-to-end translation model [official 26.07 release notes](https://docs.nvidia.com/nim/speech/latest/about/release-notes.html).

**Actual S2ST architecture.** NVIDIA documents streaming S2ST as **three microservices**: ASR transcribes, a 1.6B NMT service translates, and Magpie synthesizes. The NMT service coordinates remote ASR/TTS gRPC endpoints. It needs enough aggregate GPU memory for all three containers; Android should be a thin remote client, not the host [official S2ST deployment guide](https://docs.nvidia.com/nim/speech/latest/nmt/speech-to-speech-translation.html).

**TTS speed only, not full S2ST delay.** On one stream, official Magpie Multilingual time-to-first-audio is 77.83 ms on A100, 35.12 ms on H100, 32.16 ms on L40S, and 53.03 ms on DGX Spark; throughput is respectively 11.9×, 14.92×, 17.28×, and 9.81× realtime. These exclude ASR, translation, networking, and text accumulation, so they must not be quoted as pipeline E2E latency [official TTS benchmarks](https://docs.nvidia.com/nim/speech/latest/reference/performances/tts/performance.html).

**Window discovery: VoiceChat-TTS (August 13).** NVIDIA's 977M-parameter English continuous TTS consumes streaming LLM text, emits 80 ms audio frames, has one text-token lookahead, supports explicit interruption without resetting KV cache, and conditions on a three-second voice prompt. It is a promising duplex/cascade speech-output component, **not translation** [paper](https://arxiv.org/html/2608.13831v1).

**Window discovery: JoyAI-Talker (August 2).** A 48.9B-total/3.28B-active MoE voice-agent backbone plus a 1.7B duplex controller using 160 ms chunks. It supports streaming expressive response generation and S2TT benchmarks but is a fixed-assistant-voice agent, not documented simultaneous S2ST translation; no public checkpoint/repository was found in fetched primary material [paper](https://arxiv.org/html/2608.01119v1).

### 3. In-window evaluation warning

An August 6 long-form S2ST evaluation paper finds substantial **latency accumulation on continuous speech** and contributes a timestamp/alignment evaluation method. Short clips and “first output” demos are therefore insufficient; benchmark at least 10-20 minute continuous media and report source-to-output timing distributions [paper](https://arxiv.org/html/2606.15059v2).

## Ranked shortlist

Ranking weighs translation fit, continuous streaming, evidence quality, language reach, deployability, and Android/remote feasibility, not merely openness.

| Rank | System | Correct class | Why shortlist / main caveat |
|---:|---|---|---|
| 1 | **Qwen3.5-LiveTranslate-Flash-Realtime** | hosted multimodal Simul-S2ST | 60 text / 29 spoken languages, 2.8 s vendor per-token delay, voice cloning, hotwords, tentative/confirmed text, WebSocket/WebRTC. Closed and opaque hardware/RTF. |
| 2 | **Gemini 3.5 Live Translate** | hosted audio-to-audio Simul-S2ST | 70+ languages, continuous interpretation, source-like intonation/pacing/pitch, 100 ms ingress chunks, WebSocket; Android/iOS product proof. “Few seconds” only; preview and voice replication inconsistencies. |
| 3 | **GPT-Realtime-Translate** | hosted Simul-S2ST | 70+ inputs → 13 outputs, dedicated continuous endpoint, dynamic voice adaptation, 200 ms output chunks, WebRTC/WebSocket and strong browser/call examples. No official E2E latency, prompts/glossaries/voice selection, or timestamps. |
| 4 | **Hibiki-Zero 3B** | open-weight direct Simul-S2ST + S2TT | Best open natural voice-transfer candidate; 4 languages → English, timestamped text, constant 12.5 Hz, 8-12 GB VRAM server. Noncommercial license, one target, no Android path. |
| 5 | **SimulU on SeamlessM4T-medium** | open training-free long-form Simul-S2ST policy | Eight English→European targets, tunable cut-off frame, reported 1-2 s start offset on long talks. ~1B plus 170M T2U/vocoder, research stack, canonical voice. |
| 6 | **StreamSpeech** | open direct Simul-S2ST/S2TT/ASR | Adjustable chunk from 320 ms upward, intermediate text, three languages → English. Research-only ergonomics, fixed voice, poor long-form result in Hibiki evaluation. |
| 7 | **SeamlessStreaming 2.5B** | open-weight Simul-S2ST/S2TT/ASR | 101 speech inputs, 96 text outputs, 36 speech outputs, learned EMMA wait policy. CC-BY-NC, GPU server, older and operationally brittle. |
| 8 | **NVIDIA NIM 26.07 cascade** | streaming ASR→NMT→TTS | Commercially engineered APIs, controllable components, 12-language voice cloning and measured TTS speed. Three heavy GPU services and no full E2E delay. |
| 9 | **SimulTron** | on-device direct Simul-S2ST | Best published Android-native evidence: Pixel 7 Pro, adjustable 1/2/3 s wait-k, 148-259 MB artifacts. No official weights/code found; Spanish→English research prototype. |

## Priority systems and exact controls

### Gemini 3.5 Live Translate

Google describes it as a low-latency audio-to-audio interpreter that continuously balances waiting for context against staying synchronized, remains “a few seconds” behind, automatically detects 70+ languages, and preserves intonation, pacing, and pitch [launch](https://blog.google/innovation-and-ai/models-and-research/gemini-models/gemini-live-3-5-translate/). The Live API accepts 16 kHz mono PCM, emits 24 kHz mono PCM, recommends **100 ms input chunks**, and can return input/output transcripts over a bidirectional WebSocket [developer guide](https://ai.google.dev/gemini-api/docs/live-api/live-translate). No parameter count, weights/license, RTF, transcript timestamps, user-adjustable wait/lookahead, or reproducible algorithmic/E2E latency is published. Voice replication may drift after long pauses, choose the wrong gender, or stick to one voice in fast multi-speaker audio. Android remote feasibility is high: the production model is rolling out in Google Translate Android and Google provides ephemeral client tokens; on-device deployment is unavailable.

### GPT-Realtime-Translate

OpenAI's dedicated `/v1/realtime/translations` session continuously consumes source audio and returns translated audio plus transcript deltas while input is arriving. Browser clients use WebRTC; servers use WebSockets and 24 kHz PCM16 [developer guide](https://developers.openai.com/api/docs/guides/realtime-translation). Official cookbook details: >70 inputs, 13 output languages, dynamic adaptation to source tone/pitch/style, **200 ms output PCM chunks**, separate sessions per direction/speaker, and no custom prompt, glossary, pronunciation guide, or selectable voice [cookbook](https://developers.openai.com/cookbook/examples/voice_solutions/realtime_translation_guide). Size, weights/license, hardware, wait policy, timestamps, correction semantics, and E2E delay are undisclosed. Android should use WebRTC plus a backend-created ephemeral secret. This is the easiest general media sidecar after Gemini, but Qwen now offers better terminology and explicit tentative-text controls.

### Hibiki-Zero

Hibiki-Zero is a 3B hierarchical decoder-only direct S2ST/S2TT model with streaming Mimi audio tokens, constant 12.5 Hz audio/text output, timestamped translation, voice transfer, and adaptive flow. It translates French, Spanish, Portuguese, or German → English, requires NVIDIA GPU (8 GB may work, 12 GB is safe), and is CC-BY-NC-SA-4.0 [repo](https://github.com/kyutai-labs/hibiki-zero), [model card](https://huggingface.co/kyutai/hibiki-zero-3b-pytorch-bf16). Batched H100 inference is reported 3× realtime; training used 48 H100s. No exact algorithmic lag/E2E delay is published in the fetched card. It is server-feasible but not Android-native; model context was trained to 120 s, so long media must be tested for resets/drift.

### SimulU

SimulU is a **training-free policy**, not a new base model. It applies cross-attention stability to SeamlessM4T-medium-v1 (~1B S2T + 170M T2U plus vocoder), retains a ten-word history, and uses a cut-off-frame hyperparameter to trade quality for latency. Across MuST-C English→{Dutch, French, German, Italian, Portuguese, Russian, Romanian, Spanish}, it reports mostly 1-2 s start offset and lower/more stable end offset than the top cascade [paper](https://arxiv.org/html/2603.16924). It emits only stable prefixes rather than repeatedly rewriting display text. No dedicated streaming service, mobile artifact, hardware/RTF disclosure, or source-voice transfer is provided; deployment inherits the complex noncommercial Seamless stack.

## Historical background (published before 2026-07-31)

### Hibiki (2025)

Hibiki is 2.7B, FR→EN, CC-BY weights with MIT/Apache code. It jointly emits audio and timestamped text at 12.5 Hz and uses classifier-free guidance (CFG) to trade source-voice similarity against translation quality. Its paper reports adaptive lag rather than a user wait knob, real-time iPhone 16 Pro operation for the 1.7B Hibiki-M, and H100 batching up to 320 streams [paper](https://arxiv.org/html/2502.03382), [repo](https://github.com/kyutai-labs/hibiki). Its open issue asks about approximately **2.8 s algorithmic latency** but has no maintainer answer, so this should not be treated as confirmed E2E delay [issue](https://github.com/kyutai-labs/hibiki/issues/4).

### SeamlessStreaming (2023)

A 2.5B multilingual streaming model: ASR in 96 languages, 101 speech-input languages, 96 text targets and 36 speech targets, under CC-BY-NC-4.0. It uses learned Efficient Monotonic Multihead Attention rather than fixed wait-k; Meta characterized output around two seconds, but no current hardware E2E benchmark was found. CPU is explicitly discouraged because it adds noticeable delay [model card](https://huggingface.co/facebook/seamless-streaming), [repo](https://github.com/facebookresearch/seamless_communication). A 2025 open issue challenges long-audio streaming behavior, reinforcing the need to validate continuous media rather than demos [issue](https://github.com/facebookresearch/seamless_communication/issues/556).

### StreamSpeech (2024)

A direct multi-task model for German/French/Spanish→English that emits streaming ASR, S2TT, and S2ST. One model handles varied latency through multi-chunk training; `--source-segment-size` directly sets ingress chunk size, with 320 ms demonstrated. On RTX 3090 at 320 ms chunks, reported FR→EN algorithmic average lag is 1.27 s and computation-aware lag 2.20 s; output has a fixed unified voice, not voice transfer [paper](https://arxiv.org/html/2406.03049), [repo](https://github.com/ictnlp/StreamSpeech). Checkpoints are ~862 MB per direction; code is MIT, while the paper is CC-BY-NC-SA. The repo has no packaged mobile or production streaming API.

### SimulTron (2024)

A direct Spanish→English mobile model with causal Conformer, wait-k attention, streaming MelGAN, and TFLite. Each encoded frame is 20 ms: `k=50/100/150` produces 1/2/3 s initial context. On Pixel 7 Pro, configurations occupy 335-562 MB RAM and 148-259 MB storage, with 8-18 ms frame computation and 1.4×-3.1× realtime; MuST-C reports 1.1 s latency at `k=125` and 2.3 s at `k=150` [paper](https://arxiv.org/html/2406.02133). This most closely matches “near-real-time but needed delay adjustment,” but no official checkpoint/code/license was found.

### Qwen2.5/Qwen3 Omni (2025) versus dedicated LiveTranslate

Qwen2.5-Omni (3B/7B) and Qwen3-Omni (30B-A3B) are Apache-code/open-weight general multimodal voice agents with streamed speech **output** and speech-translation capability, but the official Qwen3 repository had an open request for actual continuous real-time audio input. They should not be equated with the dedicated hosted LiveTranslate service [Qwen3 repo](https://github.com/QwenLM/Qwen3-Omni), [issue](https://github.com/QwenLM/Qwen3-Omni/issues/99). Qwen3 supports 19 speech inputs and ten spoken outputs, requires substantial GPU deployment, and uses fixed output voices rather than source-voice preservation.

### Voxtral Realtime (2026-02)

A 4.4B Apache-2.0 **ASR-only** model (970M causal encoder + 3.4B decoder), 13 languages, single ≥16 GB GPU. Its delay is the cleanest exact control found: any **80 ms multiple** from 80-1,200 ms plus 2,400 ms; 480 ms is recommended. It ingests/decodes every 80 ms via resumable full-duplex WebSocket sessions. At 480 ms its FLEURS macro WER is 8.72 versus 5.90 offline; at 960 ms it is 7.70 [paper](https://arxiv.org/html/2602.11298), [model card](https://huggingface.co/mistralai/Voxtral-Mini-4B-Realtime-2602). It does not translate or speak, so it is only an ASR component for an S2ST cascade.

### NVIDIA ASR/AST/TTS components

- **Nemotron 3.5 ASR Streaming 0.6B:** 40 locales (32 usable without tuning), OpenMDW-1.1, exact cache-aware chunk/right-context points 80/160/320/560/1,120 ms, NeMo/Transformers streaming, one H100 sustains 240 streams at 80 ms and 2,400 at 1,120 ms. ASR only; no timestamps documented [model card](https://huggingface.co/nvidia/nemotron-3.5-asr-streaming-0.6b).
- **Parakeet Unified EN 0.6B:** English ASR only, NVIDIA Open Model License; buffered streaming delay is chunk + right context, selectable 160-2,080 ms. Linux/NVIDIA deployment; no Android runtime [model card](https://huggingface.co/nvidia/parakeet-unified-en-0.6b).
- **Canary 1B v2:** CC-BY-4.0 AST/S2TT for English↔24 European languages. New NeMo streaming supports Wait-k or lower-delay AlignAtt; defaults are 2 s chunk, 10 s left context, 2 s right context, and two initial chunks of lag. Word/segment timestamps can be enabled. It emits translated text only [model card](https://huggingface.co/nvidia/canary-1b-v2), [streaming guide](https://docs.nvidia.com/nemo/speech/nightly/asr/streaming_decoding/canary_chunked_and_streaming_decoding.html).
- **Magpie 364M:** open-weight multilingual TTS, not translation. The v2607 checkpoint covers 12 languages but explicitly removed zero-shot cloning for security; NIM offers a separate Zeroshot service with reference-audio cloning. Keep checkpoint and NIM claims separate [model card](https://huggingface.co/nvidia/magpie_tts_multilingual_357m), [release notes](https://docs.nvidia.com/nim/speech/latest/about/release-notes.html).

## Which system was probably remembered as “near-real-time needing adjustment”?

**Most likely: SimulTron**, if the memory involved a phone/on-device demo and an explicit latency setting. The paper literally calls the initial context delay **adjustable**, maps wait-k to 1/2/3 seconds, and demonstrates Pixel 7 Pro deployment [paper](https://arxiv.org/html/2406.02133).

**Second: StreamSpeech**, if the remembered control was a chunk-size command-line option: `--source-segment-size` can be changed and smaller chunks lower latency [repo](https://github.com/ictnlp/StreamSpeech).

**Third: Voxtral Realtime**, if the remembered system was actually transcription rather than translation: its exact `transcription_delay_ms` control is unusually memorable (80-1,200 ms in 80 ms steps, plus 2,400 ms) [model card](https://huggingface.co/mistralai/Voxtral-Mini-4B-Realtime-2602).

Hibiki is a weaker match: it *adapts its own flow* and exposes CFG for voice similarity, but fetched official material does not expose a user-adjustable translation delay. Gemini, GPT, and Qwen LiveTranslate similarly choose timing internally; Qwen's published 2.8 s is a measured outcome, not a delay knob.

## Android / remote deployment recommendation

1. **Prototype a remote provider adapter** with a common interface: 16/24 kHz PCM ingress, translated audio frames, source/final/tentative transcript events, language switching, original-audio ducking, reconnect, and per-event monotonic timestamps.
2. Implement **Qwen3.5** first where its regions/legal terms are acceptable: it uniquely combines hotwords, explicit tentative text, visual context, and voice cloning.
3. Add **Gemini** for widest spoken output reach and demonstrated Android consumer UX; expect voice identity edge cases.
4. Add **OpenAI** for mature WebRTC/browser/call pathways; isolate tracks per speaker and direction.
5. Run an open server bake-off: Hibiki-Zero versus SimulU/StreamSpeech, behind WebRTC. Do not place 1-3B+ PyTorch/NeMo models in the Android process.
6. Treat on-device SimulTron as an architecture lead, not an immediately deployable dependency, until weights/code/license appear.

## Gaps and validation plan

- No comparable public benchmark reports source-audio capture → first translated phoneme, P50/P95 lag, drift over 20 minutes, and network overhead across Qwen/Gemini/OpenAI.
- Qwen's 2.8 s “per-token” metric, Gemini's “few seconds,” OpenAI's absence of delay, and paper AL/LAAL/StartOffset are not interchangeable.
- Transcript correction contracts are underdocumented. Qwen explicitly distinguishes tentative from confirmed text; Gemini/OpenAI expose deltas but fetched docs do not state whether earlier deltas can be retracted.
- Gemini/OpenAI/Qwen do not publish size, serving hardware, RTF, or weights/licenses. Hibiki-Zero lacks a published exact lag. NVIDIA lacks full cascade E2E delay.
- Timestamps: Hibiki/Hibiki-Zero have timestamped target text; Canary can return word/segment timestamps; NVIDIA Magpie NIM returns end-of-chunk word offsets. Dedicated hosted S2ST APIs do not document word timestamps in fetched pages.
- Run identical long-form tests with source/reference timestamps, record arrival/playback times on Android, and measure first-audio delay, AL/LAAL or YAAL, end-offset drift, correction count, skipped/repeated semantic units, speaker similarity, audio discontinuities, and battery/network behavior.

## Fetched-source receipt

All URLs below were actually fetched during this lane; dates identify source publication/update when available.

### In-window primary sources

- Alibaba Cloud, [Qwen3.5 LiveTranslate realtime model/API](https://www.alibabacloud.com/help/en/model-studio/qwen3-5-livetranslate-flash-realtime) , updated 2026-08-26.
- NVIDIA, [NemotronLabs VoiceChat 11B model card](https://huggingface.co/nvidia/NVIDIA-NemotronLabs-VoiceChat-11B) , released 2026-08-03.
- NVIDIA, [Speech NIM 26.07 release notes](https://docs.nvidia.com/nim/speech/latest/about/release-notes.html) , fetched official page surfaced 2026-08-13.
- NVIDIA, [VoiceChat-TTS paper](https://arxiv.org/html/2608.13831v1) , 2026-08-13.
- JD.com, [JoyAI-Talker paper](https://arxiv.org/html/2608.01119v1) , 2026-08-02.
- [Practical Evaluation Method for Long-Form SimulS2ST](https://arxiv.org/html/2606.15059v2) , revision 2026-08-06.
- [PACE full-duplex playback/context paper](https://arxiv.org/html/2608.07631v1) , 2026-08-07 (relevant operational discovery, not translation).

### Current official product/API pages fetched at cutoff

- Google, [Gemini 3.5 Live Translate launch](https://blog.google/innovation-and-ai/models-and-research/gemini-models/gemini-live-3-5-translate/) and [Live Translation developer guide](https://ai.google.dev/gemini-api/docs/live-api/live-translate).
- OpenAI, [Realtime translation guide](https://developers.openai.com/api/docs/guides/realtime-translation), [model card](https://developers.openai.com/api/docs/models/gpt-realtime-translate), and [translation cookbook](https://developers.openai.com/cookbook/examples/voice_solutions/realtime_translation_guide).
- NVIDIA, [streaming S2ST deployment](https://docs.nvidia.com/nim/speech/latest/nmt/speech-to-speech-translation.html), [TTS performance](https://docs.nvidia.com/nim/speech/latest/reference/performances/tts/performance.html), and [Canary streaming policy](https://docs.nvidia.com/nemo/speech/nightly/asr/streaming_decoding/canary_chunked_and_streaming_decoding.html).

### Historical primary sources fetched

- Kyutai, [Hibiki-Zero repo](https://github.com/kyutai-labs/hibiki-zero), [model card](https://huggingface.co/kyutai/hibiki-zero-3b-pytorch-bf16), [Hibiki paper](https://arxiv.org/html/2502.03382), [repo](https://github.com/kyutai-labs/hibiki), and [delay issue](https://github.com/kyutai-labs/hibiki/issues/4).
- Meta, [SeamlessStreaming model card](https://huggingface.co/facebook/seamless-streaming), [repo](https://github.com/facebookresearch/seamless_communication), and [long-input issue](https://github.com/facebookresearch/seamless_communication/issues/556).
- ICTNLP, [StreamSpeech paper](https://arxiv.org/html/2406.03049), [repo](https://github.com/ictnlp/StreamSpeech), and [demo](https://ictnlp.github.io/StreamSpeech-site/).
- Google, [SimulTron paper](https://arxiv.org/html/2406.02133) and [2025 direct S2ST research deployment](https://research.google/blog/real-time-speech-to-speech-translation/).
- FBK, [SimulU paper](https://arxiv.org/html/2603.16924).
- Mistral, [Voxtral Realtime paper](https://arxiv.org/html/2602.11298), [model card](https://huggingface.co/mistralai/Voxtral-Mini-4B-Realtime-2602), and [transcription docs](https://docs.mistral.ai/capabilities/audio_transcription/).
- Qwen, [Qwen3 Omni repo](https://github.com/QwenLM/Qwen3-Omni), [Qwen2.5 Omni repo](https://github.com/QwenLM/Qwen2.5-Omni), and [continuous-input issue](https://github.com/QwenLM/Qwen3-Omni/issues/99).
- NVIDIA model cards: [Nemotron 3.5 ASR](https://huggingface.co/nvidia/nemotron-3.5-asr-streaming-0.6b), [Parakeet Unified](https://huggingface.co/nvidia/parakeet-unified-en-0.6b), [Canary 1B v2](https://huggingface.co/nvidia/canary-1b-v2), [Magpie TTS](https://huggingface.co/nvidia/magpie_tts_multilingual_357m), and [Multitalker Parakeet](https://huggingface.co/nvidia/multitalker-parakeet-streaming-0.6b-v1).

**Search limitation receipt:** Parallel Web Search returned an insufficient-credit error, so discovery fell back to `agent-web`; every decision claim above was then checked against a fetched primary source. No deep-research `interaction_id` exists for this run.
