# Model/API research and benchmark audit

## Severity: HIGH

## Issues Found

### 1. The commercial recommendation omits Qwen and therefore produces contradictory winners , HIGH
**Location**: `docs/ai-media-research/lanes/01-commercial-realtime-dubbing.md:9,72-79`; `docs/ai-media-research/lanes/02-open-realtime-s2st.md:7-9,58-66`; `docs/ai-media-research/benchmarks/2026-08-29-qwen-live-translate.json:24-67`

**Problem**: Lane 01 calls CAMB the best commercial live-dubbing fit and recommends a CAMB/OpenAI/Gemini bake-off, but does not evaluate Qwen3.5 LiveTranslate at all. Qwen is a closed, hosted commercial API and belongs in that lane. Lane 02 instead ranks Qwen first, and the repository's only successful direct S2ST probe is Qwen. This is not a harmless difference between “commercial” and “open”: lane 02 explicitly labels Qwen closed.

The products also solve different jobs. CAMB's differentiator is managed SRT ingest, packaged SRT/RTMP/HLS outputs, and a separate batch API. Qwen's differentiator is a direct player/media-worker sidecar with tentative/confirmed text, hotwords, visual context, voice cloning, public usage rates, and one local transport probe. A single global rank hides that distinction.

Lane 01 also says OpenAI's language list is unknown (`:51,77`), while the fetched official OpenAI cookbook used by lane 02 states over 70 input and 13 output languages (`lane 02:66,82`). The cookbook list may change and still needs runtime validation, but “unknown” and “70+/13” cannot both be the decision record.

**Impact**: NuvioTV could choose an unmeasured, cost-opaque CAMB socket for a player-side feature while ignoring the only probed direct API, or choose Qwen for a managed broadcast job it does not package. Language reach is also compared inconsistently.

**Fix**: Split the decision into (a) player-side/direct S2ST, (b) managed broadcast packaging, and (c) asynchronous dubbing. Include Qwen in every applicable commercial comparison. Record OpenAI as “official cookbook says >70 input/13 output; exact pair acceptance must be probed,” not unknown.

Primary checks: [Qwen LiveTranslate](https://www.alibabacloud.com/help/en/model-studio/qwen3-5-livetranslate-flash-realtime), [CAMB realtime](https://docs.camb.ai/api-reference/websockets/realtime), [CAMB streaming](https://docs.camb.ai/api-reference/endpoint/streaming/overview), [OpenAI cookbook source](https://raw.githubusercontent.com/openai/openai-cookbook/main/examples/voice_solutions/realtime_translation_guide.mdx).

### 2. “New in-window release” is inferred from mutable documentation dates , HIGH
**Location**: `docs/ai-media-research/lanes/02-open-realtime-s2st.md:7,21-23,157-160`; `docs/ai-media-research/lanes/03-fast-subtitle-generation.md:7,15-17`; `docs/ai-media-research/lanes/01-commercial-realtime-dubbing.md:153-160`

**Problem**: The inclusive window is correctly stated as 2026-07-31 through 2026-08-29, but two freshness claims are not proven:

- The Qwen page was updated 2026-08-26, yet it says the stable alias maps to snapshot `...-2026-05-19`. That proves an in-window documentation/capability-page update, not by itself a new in-window model release. No dated launch changelog or preserved announcement establishes the public release date.
- Lane 03 says Gemini 3.5 Transcribe was “introduced August 13.” The currently fetched official Gemini changelog's August 13 section contains Gemini 3.7 Flash, not Transcribe. The August 26 GA entry is authentic and sufficient to place Transcribe in-window, but the August 13 introduction is unsupported without an archived receipt.

Lane 01 handles the same issue more correctly for Gemini Live Translate and OpenAI by labeling their older launches as historical/current-product background. CAMB realtime's official `dateModified` is 2026-08-27 and Deepdub Live's is 2026-08-13, but these likewise prove documentation changes unless tied to a release or commit describing the product change.

**Impact**: The recommendation overweights apparent recency and could claim window compliance that cannot survive review after vendor pages mutate.

**Fix**: Use three explicit date fields: `product_release_date`, `artifact_or_snapshot_date`, and `page_last_modified`. Mark Qwen “in-window documentation update; public launch date unverified.” Mark Gemini Transcribe “GA 2026-08-26; earlier introduction date unverified.” Preserve changelog excerpts or immutable commit URLs for every freshness claim.

Primary checks: [Qwen model page](https://www.alibabacloud.com/help/en/model-studio/qwen3-5-livetranslate-flash-realtime), [Gemini changelog](https://ai.google.dev/gemini-api/docs/changelog), [Gemini Transcribe model](https://ai.google.dev/gemini-api/docs/models/gemini-3.5-transcribe).

### 3. The Qwen dubbing receipt is not a controlled latency or voice-clone experiment , HIGH
**Location**: `docs/ai-media-research/benchmarks/2026-08-29-qwen-live-translate.json:24-67,96-106`; `docs/ai-media-research/lanes/02-open-realtime-s2st.md:138-150`

**Problem**: The two successful runs change at least three variables: fixed `Tina` versus `default`, clone disabled versus clone-once, and source ASR disabled versus enabled. The receipt nevertheless attributes the roughly 2.15-second first-audio difference to clone-once. That attribution is confounded.

The timer interpretation is also too favorable:

- Fixed voice: first audio was 2.5572 s after probe start and 1.2087 s after recorded `speech_started`, not merely “1.3 seconds after session configuration.”
- Clone-once: first audio was 4.5778 s after probe start and 3.3600 s after `speech_started`.
- The 9.814 s source produced 14.96 s and 15.36 s of output: 1.524× and 1.565× source duration. Last packet arrival is not playback completion, and no mapping from source media time to playable target samples exists.

The receipt has no source-audio artifact/hash, probe code, exact request payloads, raw event log, provider request IDs, response hashes, monotonic-clock definition, or usage/invoice record. The raw data was not retained. It authenticates neither the run nor its derivation independently; it is a useful self-reported smoke-test summary only.

**Impact**: Product could mistake packet arrival for synchronized dubbing, claim an unsupported clone penalty, and discover after implementation that translated audio overruns video by more than 50%.

**Fix**: Repeat a factorial test holding voice, ASR, source, endpoint, and session options constant. Measure from each source sample's media timestamp to target audio becoming playable at the DAC/buffer boundary. Save redacted raw events, code, dependency lock, source SHA-256, provider request IDs, and billed usage. Report packet arrival, first playable audio, steady-state lag, playback completion, and duration ratio separately.

### 4. Metrics are correctly caveated in prose but still drive an incomparable rank , MEDIUM
**Location**: `docs/ai-media-research/lanes/01-commercial-realtime-dubbing.md:11,48-59,103-107`; `docs/ai-media-research/lanes/02-open-realtime-s2st.md:29,48,56,64-71,146-150`; `docs/ai-media-research/lanes/03-fast-subtitle-generation.md:9-11,64-84`

**Problem**: The reports acknowledge that Qwen's vendor “2.8 seconds,” Gemini's “few seconds,” OpenAI's missing E2E number, CAMB/Cartesia/NVIDIA TTS TTFB, paper AL/StartOffset, and local first-event times are not interchangeable. The ranking still presents an ordered latency-sensitive recommendation without a common metric.

The direct Qwen page currently describes 2.8 seconds as “as low as,” while lane 02 relies on an Alibaba blog for “average per-token latency.” That is vendor-controlled marketing evidence, not a reproducible percentile. Similarly, the subtitle receipt compares real-time-fed Qwen to batch Cloudflare and explicitly says the transports are different, yet the shortlist promotes winners across untested systems.

**Impact**: Readers will remember rank order and headline numbers, not the caveats, and may treat first packet, first provisional text, TTS TTFB, average token lag, and full completion as equivalent.

**Fix**: Do not publish a latency rank until every candidate has the same source clock and corpus. Maintain distinct columns for setup, first provisional text, first stable text, first playable audio, p50/p95 source-to-playback lag, end flush, long-form drift, output/source duration, RTF, and failure rate. Vendor numbers belong in a separate non-comparable claims appendix.

### 5. Pricing evidence is incomplete and contains correctable omissions , HIGH
**Location**: `docs/ai-media-research/lanes/01-commercial-realtime-dubbing.md:50-59,76-82,103-107`; `docs/ai-media-research/lanes/02-open-realtime-s2st.md:23-31,64-66,146-149`; `docs/ai-media-research/lanes/03-fast-subtitle-generation.md:7-9,37-45,141-150`

**Problem**:

- Qwen LiveTranslate public pricing was omitted. International rates are $7.50/M input-audio tokens and $30/M output-audio tokens; the model uses 7 input and 12.5 output tokens/second. With equal input/output duration, that is about $0.00315 + $0.02250 = **$0.02565/min**, before optional image and output-text charges. Applied to the receipt's expanded audio, the fixed and clone runs are approximately **$0.0374 and $0.0384 per source minute**, before output text. Cost is therefore output-duration-sensitive and directly material to Qwen versus OpenAI ($0.034/min estimated) and Gemini (~$0.0368/min).
- Lane 03 says Qwen ASR pricing needs a token-to-minute conversion. The official list price is already duration based: $0.00009/second, or **$0.0054/min**, with output free. Invoice reconciliation is still mandatory, but list-price conversion is not unknown.
- The official Cloudflare pricing page currently says **$0.0005/audio minute**, not the report's $0.00051. A dated page capture is needed if $0.00051 was the cutoff value.
- CAMB realtime/broadcast cost remains unknown, so it cannot be called a value winner. Palabra's public prices conflict, and DeepL/Deepdub/Azure live economics remain unresolved.

**Impact**: The economic comparison understates Qwen's known billing mechanics, misses duration-expansion cost, and treats known ASR list pricing as unknown while ranking cost-sensitive choices.

**Fix**: Add normalized cost scenarios for 1:1 and observed output/source duration, source-ASR text, silence, fan-out, reconnect/retry, and taxes/region. Keep “list-price estimate” separate from “invoice observed.” Do not award value rank to quote/credit-only products.

Primary checks: [Alibaba model pricing](https://www.alibabacloud.com/help/en/model-studio/model-pricing), [OpenAI pricing](https://developers.openai.com/api/docs/pricing), [Gemini pricing](https://ai.google.dev/gemini-api/docs/pricing), [Cloudflare Workers AI pricing](https://developers.cloudflare.com/workers-ai/platform/pricing/).

### 6. The subtitle recommendation overinterprets a smoke test and misses a material same-vendor batch candidate , HIGH
**Location**: `docs/ai-media-research/lanes/03-fast-subtitle-generation.md:7-9,33-50,64-84,137-150`; `docs/ai-media-research/benchmarks/2026-08-29-subtitle-api-probes.json:14-68`

**Problem**: The Qwen streaming run proves API mechanics, exact clean synthetic text, and returned word/sentence fields. It does not prove production subtitle suitability. The first event at 1.704 s may be provisional; the final event arrived at 8.711 s for a 7.898 s clip. There is no timestamp ground truth, cue segmentation score, WER corpus, code-switching, music, overlap, long-session, seek, reconnection, or Android capture result. The receipt's derived statement that Qwen is “immediately suitable” is stronger than its evidence.

The opening claim that the fastest useful design is not whole-title batch also conflicts with the report's own ahead-of-playback mode and Cloudflare RTF smoke test. Live and ahead-of-playback are separate product modes, not one winner.

A material candidate is missing from the hosted batch ranking: **Qwen-Audio-3.0-ASR-Flash-Filetrans**. Official docs list asynchronous files up to 12 hours/2 GB, sentence/word timestamps, optional diarization, and $0.000035/second (**$0.0021/min**) internationally. It is more expensive than Cloudflare/Groq list prices but is relevant for long titles, speaker labels, provider reuse, and avoiding live-session rotation. OpenAI's current `gpt-live-transcribe` ($0.017/min) and `gpt-transcribe` ($0.0045/min) are also absent as explicit controls, even if timestamp limitations ultimately keep them below the shortlist.

**Impact**: NuvioTV could promote a one-clip winner to production, accept unstable cue timing, or build avoidable live-session machinery for titles that could be processed asynchronously.

**Fix**: Label Qwen streaming “trial leader, no production winner.” Maintain separate live-caption and ahead-generation rankings. Add Qwen Filetrans to the batch bake-off and OpenAI transcription as a completeness control; reject or retain them only after identical timestamp/cost tests.

Primary checks: [Alibaba ASR selection](https://www.alibabacloud.com/help/en/model-studio/asr-model), [Alibaba non-realtime ASR](https://www.alibabacloud.com/help/en/model-studio/non-realtime-speech-recognition-user-guide), [Alibaba pricing](https://www.alibabacloud.com/help/en/model-studio/model-pricing), [OpenAI pricing](https://developers.openai.com/api/docs/pricing).

### 7. Source domains are authentic, but evidence is not archived strongly enough for a cutoff audit , MEDIUM
**Location**: `docs/ai-media-research/lanes/01-commercial-realtime-dubbing.md:162-235`; `docs/ai-media-research/lanes/02-open-realtime-s2st.md:153-184`; `docs/ai-media-research/lanes/03-fast-subtitle-generation.md:154-168`; both benchmark JSON files

**Problem**: No counterfeit or obvious aggregator source was found in the decision-critical citations checked. Alibaba, Google, OpenAI, CAMB, Cloudflare, vendor-owned GitHub organizations, Hugging Face publisher organizations, and arXiv papers are authentic primary/vendor sources. Authenticity does not make vendor latency comparable or independently verified.

Most web evidence is only a prose assertion that a mutable page was fetched. There are no retrieved-at timestamps per URL, HTTP status/final URL, content hashes, archived extracts, or response snapshots. The Alibaba marketing blog used for the detailed 2.8-second comparison was not reliably refetchable without anti-bot handling, making a preserved excerpt especially important. The benchmark JSON similarly lacks cryptographic links to raw evidence.

**Impact**: A later page edit can change dates, prices, limits, and wording with no way to prove what existed at the 2026-08-29 cutoff.

**Fix**: For each decision claim, retain a small redacted evidence manifest containing retrieval UTC, final URL, HTTP status, SHA-256, quoted excerpt/table row, and immutable commit where available. Preserve raw benchmark responses with secrets removed and hashes referenced by the summary receipt.

## Corrected Ranked Decision

### Confidence legend
- **GREEN**: representative, reproducible, cross-candidate corpus plus invoice/SLA validation. **No candidate currently qualifies.**
- **YELLOW**: authentic primary mechanics and pricing, and/or a limited smoke test; mandatory bake-off remains.
- **RED**: material pricing, protocol, quality, or production behavior remains unverified.

### A. Direct player/media-worker live translated audio
1. **Qwen3.5 LiveTranslate , YELLOW.** First pilot because it is the only direct S2ST API locally smoke-tested and has explicit tentative text, hotwords, 29 spoken outputs, clone modes, and calculable public pricing. It is not selected for launch until duration expansion, long-form drift, region/legal fit, and voice similarity pass.
2. **OpenAI GPT-Realtime-Translate , YELLOW.** Best integration fallback: dedicated translation endpoint, WebRTC/WS, short-lived client secret, current $0.034/min estimate, and official >70-input/13-output claim. No local latency/quality test; dynamic style adaptation is not proven identity preservation.
3. **Gemini 3.5 Live Translate Preview , YELLOW.** Best coverage candidate at 70+ languages with constrained ephemeral tokens and transparent price. Preview status and documented pause/gender/multi-speaker voice failures place it behind OpenAI for launch confidence.
4. **CAMB realtime S2S , RED.** Valid direct socket and cloned voice selection, but only 14 listed realtime languages, no comparable test, no public unit economics, and no verified ephemeral client credential. Promote only if its measured quality or account voice workflow wins.
5. **Palabra / DeepL Voice , RED.** Keep as specialist controls. Palabra has voice/broadcast features but unresolved price presentation and no reproducible benchmark; DeepL has strong token/reconnect semantics but unknown price and no verified source-voice preservation.

### B. Managed live broadcast packaging
1. **CAMB Streaming , YELLOW.** Correct leader only for native managed SRT ingest plus SRT/RTMP/HLS delivery, subtitle/audio assets, and automatic source-speaker cloning. Pricing, long-run stability, and actual output latency remain gates.
2. **Deepdub Live , RED.** Native broadcast challenger, but early access, quote-only, and unmeasured.
3. **Palabra Broadcast , RED.** Plausible protocol/voice specialist; price, wire behavior, and SLA need direct validation.

Qwen, OpenAI, and Gemini can feed an app-owned mux/packager but should not be ranked as managed broadcast services.

### C. Live generated dialogue captions
1. **Qwen-Audio-3.0-ASR-Flash-Streaming , YELLOW.** Trial leader because word/sentence timestamps were returned in one successful probe, sessions are documented as unlimited, and list price is $0.0054/min. Not a production selection.
2. **Deepgram Nova-3 , YELLOW.** Strong production control because it supports streaming words/timing/diarization and media-oriented operation; it needs the same paid corpus and exact invoice test.
3. **Gemini 3.5 Transcribe Live , YELLOW.** Coverage/code-switching control, but 10-minute sessions and no live word timestamps or diarization are significant TV-caption penalties.
4. **AssemblyAI Universal-3.5 Pro Streaming , RED.** Keep in bake-off; sub-300 ms is a vendor claim, not a comparable result.

### D. Ahead-of-playback/final subtitle generation
1. **Cloudflare Whisper Large V3 Turbo , YELLOW.** Cheapest measured baseline and one successful RTF smoke test; no long-form/media-quality proof.
2. **Groq Whisper Large V3 Turbo , YELLOW.** Public low-cost throughput control; chunking, file limits, and timestamp quality need identical tests.
3. **Qwen-Audio-3.0-ASR-Flash-Filetrans , YELLOW.** Add as the long-title/provider-reuse control: 12-hour files, timestamps, optional diarization, and $0.0021/min list price.
4. **Gemini 3.5 Transcribe batch , YELLOW.** Broad-language/code-switching control with word timestamps and diarization, but those features reduce request duration to 30 minutes.
5. **WhisperX/faster-whisper , RED.** Self-hosted post-pass controls until deployment hardware, alignment error, throughput, and operating cost are measured.

## Mandatory Follow-up Probes

1. **Common direct-S2ST bake-off:** CAMB, Qwen, Gemini, and OpenAI on identical PCM, region-normalized where possible. Include at least EN↔ES, EN↔FR, EN→IT, priority non-English pairs, code-switching, noise/music, overlap, and single/multi-speaker clips.
2. **Long-form gate:** Three 22-minute episodes and one 100-minute title per finalist. Record p50/p95 first-playable and steady-state source-to-DAC lag, end offset, drift/20 min, gaps/repeats, reconnect loss, and output/source duration ratio.
3. **Controlled Qwen clone ablation:** Same voice and payload with clone off/once/always crossed independently with source ASR off/on. Score speaker similarity and bilingual human naturalness; do not infer clone cost from the existing confounded pair.
4. **Translation quality:** Preserve source and target transcripts; use bilingual human error annotation for omissions, additions, named entities, profanity, and speaker attribution. Automated scores may supplement, not replace, review.
5. **Broadcast test:** Run CAMB SRT ingest for at least two hours, consume each advertised output, inspect A/V sync and subtitle cue timestamps, inject source interruption, and verify recovery, fan-out, and actual invoice/credits.
6. **Live-caption bake-off:** Qwen, Deepgram, Gemini, and AssemblyAI on the same owned film/TV corpus. Measure first provisional and first stable cue separately, WER/CER, partial churn, word timestamp median/p95 error, cue onset/end error, CPS/line violations, diarization DER where available, seek recovery, and old-epoch contamination.
7. **Batch-caption bake-off:** Cloudflare, Groq, Qwen Filetrans, Gemini batch, and a self-hosted Whisper control on identical 22/100-minute assets. Separate upload, queue, inference, alignment, retry, and full wall time; verify resumability and file limits.
8. **Session-boundary test:** Rotate Gemini before 10 minutes with overlap; quantify duplicate/missing words and timestamp discontinuity. Force reconnects for every live provider at 5, 30, and 60 minutes.
9. **Invoice probe:** Reconcile Qwen input/output/text/image tokens and duration expansion; OpenAI billed duration; Gemini audio tokens; CAMB credits; silence, fan-out, cancellation, retry, and failed-session charges. Require ±10% local-meter agreement.
10. **Catalog probe:** Query or attempt every launch source/target pair. Store exact accepted matrices, target speech availability, region, concurrency, and session duration; never substitute homepage language totals.
11. **Receipt hardening:** Retain source SHA-256, probe code/commit, SDK/runtime lock, redacted exact payload, raw event log, monotonic clock origin, region, request IDs, response hashes, and usage record. A summarized JSON without these is not a benchmark receipt.
12. **Security/legal gate:** Validate ephemeral credential constraints, key-broker behavior, retention/training/data residency, voice-clone consent, redistribution rights, and deletion. Do not ship source-speaker cloning on documentation claims alone.

## Summary
Overall assessment: **fix-first**. The reports contain substantial authentic primary-source work and generally recognize taxonomy and metric caveats, but the combined recommendation is internally inconsistent: the commercial lane omits Qwen, recency is overstated, pricing is incomplete, and one synthetic smoke test is carrying too much decision weight. Treat the corrected ranks as pilot order only; no live dubbing or subtitle provider has GREEN launch confidence until the mandatory common-corpus, invoice, long-form, and failure probes pass.
