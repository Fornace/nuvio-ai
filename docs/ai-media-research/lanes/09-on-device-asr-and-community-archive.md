# Lane 09: On-device ASR state and community track archive

**Date:** 2026-09-01
**Status:** research note, no implementation go yet

## On-device transcription landscape, September 2026

### What Apple uses

- iOS 26 `SpeechAnalyzer` framework: `SpeechTranscriber` (long-form), `DictationTranscriber`
  (short-form), `SpeechDetector` (VAD). One new proprietary Apple model, on-device, free, no
  usage caps, automatic per-locale asset install, streaming partials, word-level timing.
- Measured speed: MacStories measured roughly 2.2x faster than Whisper Large V3 Turbo
  (MacWhisper build) on Apple Silicon; Argmax benchmark (2025-06) put it at mid-tier Whisper
  speed and accuracy on long-form conversational English; a 2026 independent benchmark places it
  above Parakeet in accuracy while staying faster, with Whisper Large still the accuracy leader.
- Limits: SpeechTranscriber covers a small locale set (about 10 at launch vs 99+ for Whisper),
  and the long-form model takes no contextual strings (dictation path takes up to 100 phrases).
- The speed comes from Apple Silicon Neural Engines plus a model co-designed for that hardware.
  Not portable to Android TV. Relevant to us only as the benchmark for what on-device can do.

### Open ASR, late 2026 (GPU-class numbers)

- NVIDIA Canary Qwen 2.5B: tops the HF Open ASR leaderboard at 5.63% WER, SALM architecture
  (FastConformer encoder plus Qwen3-1.7B decoder), 418x RTF, transcription plus analysis modes.
- IBM Granite Speech 3.3 8B: about 5.85% WER.
- Parakeet TDT 1.1B: over 2000 RTFx, English-only, CC-BY-4.0.
- Whisper Large V3 Turbo: 7.75% WER, 216 RTFx, 99+ languages, MIT.
- Voxtral, Nemotron ASR, Cohere Transscribe: same SOTA cluster.

All RTFx figures are GPU-class. Android TV boxes run weak ARM CPUs with no accessible NPU,
two to three orders of magnitude slower, so realistic on-device candidates are quantized
whisper.cpp tiny/base/small and Parakeet-class small models, with a measurable quality loss
against cloud Turbo.

### What Nuvio can use

- Subtitles are already near-free in cloud ($0.06 per movie with Cloudflare Whisper Turbo), so
  on-device ASR buys offline and privacy, not meaningful cost savings.
- Dubbing is the cost center ($3 to $5 per movie) and stays cloud: voice cloning quality cannot
  run on a TV box.
- Proposed free/offline tier: a whisper.cpp engine lane in the subtitles provider (MIT, 99+
  languages, GGUF quantized), measured for RTF and WER on real box hardware before any default
  flip. English-only speed option: Parakeet TDT via ONNX.

## Community track archive: design sketch

User-shared AI dub and subtitle tracks, selected from an in-app archive, with starring so the
best track surfaces and can be auto-selected.

### Identity: content-addressed, never filenames

- `artifactId = sha256(file bytes)`. Same bytes anywhere map to one artifact; re-uploads dedupe
  and stars aggregate. Filename clashes are structurally impossible.
- `titleKey = tmdbId or imdbId + season + episode` for browsing.
- `cutFingerprint` (audio-track hash of first and last 64 KB) binds dubs to the exact cut, since
  a theatrical dub misaligns on an extended edition.
- `variant = kind (dub or sub), source language, target language, engine tag, engine version,
  voice profile hash`.
- Catalog entry key: `titleKey :: variant :: artifactId`.

### Serving

- Cloudflare R2 plus Worker beside the existing registry host. Endpoints: upload (size-capped,
  hash-checked), list by titleKey sorted by stars, star (device-keyed), ranged download.
  Sub tracks are kilobytes; a 2-hour dub is roughly 20 to 60 MB Opus. R2 egress is free.

### Client UX, blended

- The existing track overlays gain a Community group, sorted by stars, showing language, engine
  badge, star count, and cut-match confidence. Selection downloads through the existing
  hash-pinned verifier and mounts like a locally generated track.
- Generation end card offers Share with community, honoring a per-profile setting: share
  automatically.
- Star action available on any played community track, one star per device key per artifact.
  Device keys reuse the install identity already in the credential vault.

### Auto-select

When enabled and no local track matches the profile language, the host queries the archive for
titleKey plus target language and takes the highest-starred artifact above a minimum-star
threshold, falling back to local generation. This is the most-starred dub and autosub path.

### Abuse and legal

- Upload quotas per device, size cap, audio sanity probe (duration within 10% of runtime, codec
  validation), star-weighted ranking buries junk, report flag, takedown path.
- Legal note for Francesco: a translated audio track derived from a copyrighted film is a
  derivative artifact; distribution carries takedown exposure. Subtitles carry similar but
  historically tolerated exposure. Hosting jurisdiction and moderation policy are his call
  before any public launch.
