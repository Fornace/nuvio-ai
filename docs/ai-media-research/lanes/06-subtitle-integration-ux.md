# Subtitle Integration UX and Missing-Subtitle ASR Plugin

> **Repository baseline:** `eca648a86de8021a47299549d6dedbf0420b7188` (`eca648a8`). `git show --stat eca648a8` changes only gradient compositing in `HeroCarousel.kt` and `ClassicFocusGradientBackdrop.kt`; this report treats that commit as the fixed baseline. No application source changes are proposed as already implemented.

Nuvio already has a capable static-subtitle pipeline: concurrent Stremio-addon discovery, media fingerprint hints, charset repair, ExoPlayer hot sidecar rendering, MPV loading, styling, delay, persistence, and a mature three-rail TV selector. The missing-subtitle feature should not pretend that live ASR is an ordinary Stremio subtitle URL: batch results can use the standard addon contract, but secure decoded-audio access, progressive cue revisions, cancellation, seek epochs, and episode lifecycle require an explicit Nuvio host capability. The most implementable first release is ExoPlayer-only, with a host-owned PCM tap and mutable cue store feeding a generalized version of the existing sidecar renderer, plus an honest “Switch to ExoPlayer” state on MPV.

## Key Findings

1. **What — The standard addon request path is suitable for lookup and completed batch subtitles, not a live host-coupled transcription session.**
   **Evidence —** Nuvio selects enabled addons advertising `subtitles`, matching type and ID prefix (`app/src/main/java/com/nuvio/tv/data/repository/SubtitleRepositoryImpl.kt:50-68`, `:127-154`), then calls a REST resource containing only type, video/content ID, and optional hash/size/filename (`:156-185`, `:220-237`). The accepted response object has only `id`, `url`, and `lang`/`language` (`app/src/main/java/com/nuvio/tv/data/remote/dto/SubtitleResponseDto.kt:7-16`). This matches the official Stremio subtitle resource and response documentation: [protocol](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/protocol.md), [subtitle response](https://github.com/Stremio/stremio-addon-sdk/blob/master/docs/api/responses/subtitles.md).
   **So what —** A normal addon may run server-side batch ASR if it can independently acquire the media, cache the completed VTT/SRT, and return its URL. It receives neither Nuvio’s selected stream URL/headers nor decoded audio, seek, cancel, grant, or episode-transition events; those must remain Nuvio extensions.

2. **What — ExoPlayer’s current sidecar is the right rendering foundation, but its source is immutable and one-shot.**
   **Evidence —** The hot path downloads the entire body, parses it, stores one `List<CuesWithTiming>`, and only then begins a render loop (`app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerSidecarSubtitles.kt:98-175`). Rendering already applies current playback position and audio/subtitle delay, sanitization, SDH filtering, overlap merging, and direct `SubtitleView.setCues` without replacing the video media source (`:178-200`). The stated purpose is to preserve the progressive/VOD buffer (`:26-38`).
   **So what —** Generalize this into a `TimedCueSource`: static addon files populate an immutable source, while ASR populates a host-owned mutable source. Do not repeatedly download a growing VTT file or call `setMediaSource` for every partial result.

3. **What — Nuvio currently carries useful media identity but deliberately does not expose media access to subtitle addons.**
   **Evidence —** Player navigation includes stream URL, serialized headers, filename, video hash, and size (`app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerNavigationArgs.kt:7-38`, `:65-97`). Runtime normalizes and retains current URL/headers (`app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeController.kt:183-216`) and lazily computes an OpenSubtitles hash before querying addons (`app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerObservers.kt:40-104`). Only filename/hash/size are sent to `SubtitleRepository`; URL, headers, and PCM are not (`:95-104`).
   **So what —** Preserve that boundary. Grant an ASR plugin expiring access to selected decoded PCM, never a debrid URL, cookie, authorization header, torrent session URL, or unrestricted playback data source.

4. **What — The existing TV selector supplies the interaction model, but it lacks actionable fetch errors and a generated-track kind.**
   **Evidence —** It is a language → option → style layout with dedicated focus requesters and scroll restoration (`app/src/main/java/com/nuvio/tv/ui/screens/player/SubtitleSelectionOverlay.kt:225-363`, `:394-436`, `:593-705`), explicit D-pad rail transitions (`:997-1044`, `:1069-1120`), and loading/empty cards (`:707-719`). `PlayerUiState` contains `addonSubtitlesError` (`app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerUiState.kt:121-126`), but `PlayerScreen` passes only loading state into the overlay (`app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerScreen.kt:1534-1552`). Repository failures are generally converted to empty lists (`SubtitleRepositoryImpl.kt:82-116`, `:189-217`), so the controller’s exception-to-error branch is seldom reached (`PlayerRuntimeControllerObservers.kt:107-151`).
   **So what —** Add a real error/retry model before ASR. Generated tracks need stable IDs and explicit queued/listening/transcribing/paused/complete/failed states so cue updates do not move focus or rebuild rails.

5. **What — A decoded-PCM hook is practical in ExoPlayer, while MPV has no equivalent Kotlin seam in this baseline.**
   **Evidence —** ExoPlayer is already built with a custom renderers factory and injected `GainAudioProcessor` (`app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerInitialization.kt:805-848`); its `DefaultAudioSink` currently receives that processor (`:2065-2095`). The gain processor demonstrates PCM-16/float handling (`app/src/main/java/com/nuvio/tv/ui/screens/player/GainAudioProcessor.kt:29-61`). MPV is configured to output through `audiotrack,opensles` (`app/src/main/java/com/nuvio/tv/ui/screens/player/NuvioMpvSurfaceView.kt:590-614`) and exposes subtitle commands, but no decoded-audio callback (`:431-479`).
   **So what —** Ship the first generation path on ExoPlayer. MPV requires native/libmpv audio-frame integration; Android Audio Playback Capture is a user-consented `MediaProjection` fallback with platform eligibility constraints, not a silent substitute ([Android playback capture](https://developer.android.com/media/platform/av-capture)).

6. **What — Existing plugin and QR infrastructure are precedents, not secure media-capability transports.**
   **Evidence —** The full-build JavaScript runtime has a broad native fetch bridge (`app/src/full/java/com/nuvio/tv/core/plugin/PluginRuntime.kt:286-304`) and a general network client (`:48-66`), so decoded audio should not simply be injected into that environment. The LAN configuration server listens on ports beginning at 8080 and exposes unauthenticated state and mutation routes (`app/src/main/java/com/nuvio/tv/core/server/AddonConfigServer.kt:20-50`, `:156-210`, `:221-255`). Its QR contains only `http://<LAN-IP>:<port>` (`app/src/main/java/com/nuvio/tv/ui/screens/addon/AddonManagerViewModel.kt:361-377`), although mutation does require TV-side confirmation (`:600-646`).
   **So what —** Reuse the visible QR + TV confirmation pattern only for plugin account/configuration. Add short-lived authentication and never expose stream URLs, headers, PCM, transcripts, or grant handles over the LAN server.

7. **What — There are two important correctness gaps to close before layering ASR on top.**
   **Evidence —** `StreamDto` accepts inline `subtitles` (`app/src/main/java/com/nuvio/tv/data/remote/dto/StreamResponseDto.kt:12-24`, `:108-113`), but `StreamMapper.toDomain()` omits the field and the domain `Stream` has no subtitle property (`app/src/main/java/com/nuvio/tv/data/mapper/StreamMapper.kt:18-32`; `app/src/main/java/com/nuvio/tv/domain/model/Stream.kt:9-27`). Also, the hasher accepts any successful response for a range read, including `200` from a server that ignored `Range` (`app/src/main/java/com/nuvio/tv/core/player/OpenSubtitlesHasher.kt:92-131`), which can yield a false hash when no cached range-capability probe was available.
   **So what —** Preserve stream-provided subtitles and harden fingerprinting first; otherwise Nuvio may offer unnecessary generation or cache a transcript against the wrong media.

## Data / Evidence

### 1. Existing end-to-end subtitle pipeline

#### 1.1 Discovery and addon request

1. The player constructs a subtitle request from current content/episode identity and, if necessary, computes a media hash (`PlayerRuntimeControllerObservers.kt:40-104`).
2. The repository reads installed addons and limits work to enabled addons advertising `subtitle`/`subtitles`, a compatible canonical type (`tv` becomes `series`), and a matching resource or manifest ID prefix (`SubtitleRepositoryImpl.kt:50-68`, `:127-154`).
3. Eligible requests run concurrently under `supervisorScope`, each with a 20-second timeout. Progress is counted independently and non-empty snapshots are emitted to the main thread as each addon completes (`SubtitleRepositoryImpl.kt:71-124`). This is progressive **provider discovery**, not progressive cue delivery.
4. For a series, the episode `videoId` is preferred. The URL is `/subtitles/{type}/{actualId}/{videoHash=…&videoSize=…&filename=…}.json`, retaining any query already present in the addon base URL (`SubtitleRepositoryImpl.kt:156-185`, `:220-237`).
5. DTOs without a URL are discarded; missing language becomes `und`; missing ID is synthesized from language and URL hash (`SubtitleRepositoryImpl.kt:189-206`). The domain identity is consequently `id + url + lang + addon name/logo` (`app/src/main/java/com/nuvio/tv/domain/model/Subtitle.kt:7-18`).

**Protocol boundary:** The standard Stremio contract can advertise the `subtitles` resource and return completed subtitle objects. It does not standardize a host permission prompt, selected-stream credentials, PCM channel, ASR job lifecycle, cue upserts/retractions, or cancellation. Nuvio-specific fields should therefore be namespaced and negotiated outside the standard manifest/resource contract, not smuggled into `extra` and assumed portable.

#### 1.2 Active media identity and hashing

- Runtime starts with navigation hash/size/filename and derives a filename from the URL path if needed (`PlayerRuntimeController.kt:183-212`). Stream switching replaces hash, size, filename, addon metadata, and the normalized URL/headers (`app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerStreams.kt:572-606`).
- The OpenSubtitles algorithm adds content length and little-endian 64-bit words from the first and last 64 KiB (`OpenSubtitlesHasher.kt:11-39`). Files below 128 KiB, missing length, cached “no ranges,” or exceptions return `null` (`:23-42`, `:45-89`).
- Range requests preserve playback headers except caller-supplied `Range` and add a default user agent (`:92-110`). The response validation at `:113-115` should require `206` plus a matching `Content-Range`; accepting `200` can hash the first chunk twice.
- Hash failure is intentionally silent. ASR identity therefore needs an explicit quality marker (`EXACT_FILE_HASH`, `TORRENT_FILE`, `WEAK_METADATA`, `SESSION_ONLY`) rather than treating every available key as equally durable.

#### 1.3 Fetch, decode, parse, and render

**ExoPlayer static sidecar (preferred):**

- Selection prefers the sidecar for parser-supported formats so video buffering survives (`app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerTrackSelection.kt:485-525`). ASS/SSA stays on the media-source/libass path when libass is requested (`PlayerSidecarSubtitles.kt:43-67`).
- Download uses up to three attempts and full `ResponseBody.bytes()`. Active playback headers are forwarded only when subtitle and media hosts match, excluding hop-by-hop/range/host headers (`app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerSubtitleTiming.kt:196-264`). Charset detection handles BOMs, valid UTF-8, language-guided legacy encodings, and double-encoding repair (`app/src/main/java/com/nuvio/tv/core/player/SubtitleCharsetDetector.kt:38-83`, `:85-149`).
- Content sniffing covers VTT, ASS/SSA, TTML, and SRT rather than trusting the URL extension (`app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerSubtitleUtils.kt:136-211`). Media3 parsing is followed by lenient SRT/VTT fallback (`PlayerSidecarSubtitles.kt:220-255`).
- The full cue list is assigned once at `PlayerSidecarSubtitles.kt:145`; the ticker reads that in-memory list at `:153-155` and renders current cues at `:178-200`. There is no growing-file parser, mutable cue revision, or push channel today.

**ExoPlayer media-source fallback:** Unsupported/higher-fidelity tracks are attached through `MediaItem.SubtitleConfiguration`; if not already attached, the player resets its media source at the current position and prepares again (`PlayerRuntimeControllerTrackSelection.kt:413-429`, `:572-625`). This is unsuitable for frequent ASR revisions.

**MPV:** Static addon text is downloaded, decoded/sanitized, written to cache, then loaded via `sub-add`; download failure falls back to the remote URL (`PlayerRuntimeControllerTrackSelection.kt:431-481`). MPV applies delay through `sub-delay` (`NuvioMpvSurfaceView.kt:250-256`) and style through MPV properties (`:353-391`). A changing subtitle file would require unsafe reload/poll behavior and still would not give the plugin audio.

**External players:** Nuvio downloads all forwarded addon subtitles into cache, rewrites them to `content://` URIs, and silently drops failed files (`app/src/main/java/com/nuvio/tv/core/player/SubtitleFileCache.kt:16-57`, `:62-95`). The cache is cleared before each batch (`:38-40`, `:97-105`), and external launch waits up to ten seconds for best-effort preparation (`app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerViewModel.kt:324-361`). Progressive generated subtitles cannot be promised after control leaves Nuvio; only a completed snapshot should be forwarded.

#### 1.4 Selection, preferences, timing, and styling

- Global style contains primary/secondary language, forced-only behavior, preferred-only filtering, SDH stripping, size, vertical offset, bold, text/background color, and outline (`app/src/main/java/com/nuvio/tv/data/local/PlayerSettingsDataStore.kt:136-154`). The same basic style maps to ExoPlayer `SubtitleView` (`PlayerScreen.kt:1817-1856`) and MPV (`NuvioMpvSurfaceView.kt:353-391`).
- Addon track preference persists ID, URL, language, and addon name (`app/src/main/java/com/nuvio/tv/data/local/TrackPreferenceDataStore.kt:17-65`, `:125-171`). Exact addon name + ID is required for normal restoration (`app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerTracks.kt:1087-1151`). A generated track must not persist a signed URL; it needs a stable generated-track identity.
- Subtitle delay is deliberately keyed by video/episode rather than series content ID (`TrackPreferenceDataStore.kt:30-35`, `:95-111`). The timing dialog captures the current video time, lets the user select a matching subtitle cue, calculates delay, persists it, and refreshes rendering (`PlayerRuntimeControllerSubtitleTiming.kt:35-96`, `:117-193`). Generated tracks should use the same delay controls, but never carry one episode’s delay to the next.
- `subtitleOrganizationMode` is persisted in player settings (`PlayerSettingsDataStore.kt:282-286`) and reported in diagnostics (`app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerPlaybackEvents.kt:519`), but the baseline search found no selection/render consumer. It should not be assumed to organize ASR tracks without a separate implementation.

### 2. Proposed capability split

| Capability | Standard Stremio addon | Nuvio host extension required |
|---|---:|---:|
| Advertise `subtitles` and filter by type/ID prefix | Yes | No |
| Receive type, content/episode ID, hash, size, filename | Yes | No |
| Return completed VTT/SRT via `{id,url,lang}` | Yes | No |
| Server-side batch ASR when the addon already owns/can fetch media | Yes | No |
| Receive Nuvio’s selected protected stream URL or headers | No | **Do not add**; use PCM capability instead |
| Receive selected decoded audio frames | No | Yes |
| Know selected audio track and track changes | No | Yes |
| Push provisional/final cue upserts and revisions | No | Yes |
| Seek epoch, pause/resume, cancel, episode transition | No | Yes |
| Host consent, expiry, revocation, visible privacy state | No | Yes |
| Completed generated-file export to an external player | Via a normal URL once complete | Host snapshot/export needed |

A standard addon may expose an asynchronous batch service operationally, but the standard response has no job token/status schema and Nuvio times each addon request out after 20 seconds (`SubtitleRepositoryImpl.kt:31-34`, `:82-115`). Portable behavior is therefore: return an already-completed file now, or return no result and make it available on a later ordinary lookup. Nuvio-specific async status must be declared as an extension.

### 3. Proposed host architecture

#### 3.1 Components and trust boundary

```text
PlayerRuntimeController
  ├─ MediaIdentityResolver
  ├─ SubtitleGenerationCoordinator (state machine; owns grants)
  ├─ AudioFrameBroker
  │    └─ Exo: PcmTapAudioProcessor + timing/discontinuity adapter
  ├─ AsrPluginConnection (Binder/isolated service or signed in-app provider)
  ├─ MutableCueStore
  ├─ TimedCueSource adapter
  │    └─ existing sidecar render ticker → SubtitleView
  └─ GeneratedSubtitleCache (final cues only; profile-scoped)
```

**Do not reuse the unrestricted JavaScript scraper runtime as the media boundary.** It is designed for network scraping and exposes native fetch (`PluginRuntime.kt:286-304`); a media capability should have a smaller typed surface and a verifiable plugin identity.

Recommended plugin classes:

- **On-device engine:** signed component in a dedicated process. Prefer `isolatedProcess` where model/runtime constraints permit; no internet permission. Frames cross a bounded IPC queue or shared memory owned by the host.
- **Cloud connector:** still receives only downmixed/resampled audio frames. Network egress goes through a host broker restricted to manifest-declared HTTPS origins, request size/rate limits, and redacted logs. The connector never receives playback headers.
- **Remote Stremio addon:** remains a normal URL-based subtitle provider and cannot request `media.audioFrames.v1`.

#### 3.2 Host capability and session contract

The following is a proposed Nuvio contract, not an existing runtime API:

```kotlin
data class AudioGrant(
    val opaqueGrantId: String,
    val pluginIdentity: PluginIdentity,       // package/module + signing digest
    val playbackSessionId: String,
    val contentVideoId: String,
    val episodeKey: String?,
    val selectedAudioTrackFingerprint: String,
    val spokenLanguage: String?,              // null = auto-detect
    val outputLanguage: String,
    val sampleFormat: GrantedPcmFormat,        // e.g. 16 kHz mono PCM16
    val expiresAtElapsedRealtimeMs: Long,
    val allowCloudEgress: Boolean
)

sealed interface HostToAsr {
    data class Start(val grant: AudioGrant, val model: ModelRef) : HostToAsr
    data class AudioFrame(
        val epoch: Long,
        val sequence: Long,
        val mediaTimeUs: Long,
        val pcm: ByteBuffer
    ) : HostToAsr
    data class Discontinuity(val newEpoch: Long, val mediaTimeUs: Long) : HostToAsr
    data object Pause : HostToAsr
    data object Resume : HostToAsr
    data class End(val reason: EndReason) : HostToAsr
}

data class CueUpsert(
    val trackKey: String,
    val epoch: Long,
    val cueId: String,
    val revision: Long,
    val startUs: Long,
    val endUs: Long,
    val text: String,
    val isFinal: Boolean
)
```

Security invariants:

1. The opaque grant is unforgeable, non-serializable outside the bound IPC connection, scoped to one plugin identity, playback session, episode, selected audio track, format, and expiry.
2. Changing plugin, episode, audio track, engine, or playback session revokes the grant. Process death and player release revoke it synchronously.
3. The broker provides normalized PCM only. It must not provide URL, headers, cookies, DRM/session data, torrent endpoints, file descriptors to the original media, or arbitrary range reads.
4. A plain `AudioProcessor.queueInput` receives bytes but not authoritative media timestamps. The tap must pair a monotonically counted sample clock with host timing/discontinuity anchors; it must not invent PTS from callback wall time. Seek always starts a new epoch.
5. Audio is copied to a bounded non-blocking queue. ASR backpressure drops/marks frames or reduces recognition quality; it never blocks the playback audio thread.
6. PCM taps require decoded PCM. While a generation grant is active, passthrough may need to be disabled/forced to PCM. Nuvio already has custom sink/force-PCM machinery (`PlayerRuntimeControllerInitialization.kt:2065-2095`), but this behavioral and quality impact must be measured.
7. Resampling/downmix occurs on the capture branch and must not alter audible playback. Tap before user gain so amplification settings do not distort recognition.

#### 3.3 Progressive cue store and sidecar integration

Refactor the renderer around this small seam:

```kotlin
interface TimedCueSource {
    val trackKey: String
    fun snapshot(): List<CuesWithTiming>
}
```

- `StaticFileCueSource` wraps today’s fully parsed list.
- `GeneratedCueSource` snapshots `MutableCueStore`.
- The existing render loop continues to apply delay, sanitization, SDH stripping, overlap merging, and `SubtitleView.setCues` (`PlayerSidecarSubtitles.kt:178-200`).
- The cue store accepts an upsert only when `(epoch, cueId)` matches the current namespace and `revision` is newer. Final cues are immutable; a plugin attempting to revise a final cue is rejected and recorded as a protocol fault.
- Provisional cues may change text/timing, but update presentation is coalesced (for example, at 5–10 Hz) to avoid Compose or `SubtitleView` churn. Cue-list changes must not mutate the option card’s stable ID.
- On a seek, provisional cues from the old epoch are removed immediately. Final cues outside a configurable overlap window may remain available, but only if their media identity and timeline revision still match.
- Do not use an ever-growing VTT response as the primary extension. HLS defines segmented WebVTT delivery ([RFC 8216 §3.5](https://www.rfc-editor.org/rfc/rfc8216#section-3.5)), but that solves segment transport, not host grants, cue revision semantics, seek cancellation, or this sidecar’s full-body parsing behavior.

#### 3.4 Stable identity and cache

Use two related keys:

```text
MediaGenerationIdentity =
  profile scope
  + content type / content ID / exact videoId (episode)
  + media fingerprint + fingerprint quality
  + selected audio-track fingerprint
  + spoken language + output language
  + ASR engine / model / model version
  + audio normalization version
  + subtitle normalization version

InFlightTrackIdentity = MediaGenerationIdentity + playbackSession + timingEpoch
```

Media fingerprint preference:

1. verified OpenSubtitles file hash + size;
2. torrent info hash + file index + file size, stored as a profile-scoped HMAC;
3. profile-scoped HMAC of normalized non-secret media identity plus size/filename;
4. session-only random identity if confidence is insufficient.

Never put a signed URL, query token, authorization header, cookie, LAN torrent URL, or cloud-session token in a durable key. Persist only finalized cues, engine metadata, identity quality, and completion ranges. Raw PCM and provisional text remain memory-only by default. Completed generated subtitles may be exported as a normal VTT/SRT and then enter the existing static/external-player paths.

### 4. Implementable TV flow

The flow deliberately keeps the established language → option → right rail geometry and focus conventions.

#### 4.1 Entry and discovery

1. User opens **Subtitles**. Existing addon and embedded tracks appear normally while addon loading is visible.
2. For each selected language, append a stable option card:
   - **Generate subtitles…** when no viable track exists; or
   - **Generate another version…** under a small “Tools” divider when tracks exist.
3. The action remains available after a lookup error. Replace today’s generic empty card with:
   - “Couldn’t load subtitle providers”
   - focused **Retry lookup**
   - focused **Generate subtitles**
   Errors should be concise and should not stop playback.
4. Do not auto-start audio capture solely because the subtitle list is empty. Generation always requires a user action and a visible consent step.

#### 4.2 Configuration in the right rail

When **Generate subtitles…** is focused, the right rail changes from Style to **Generation setup** while preserving the same left/right navigation:

- **Audio:** currently selected audio track; other tracks are explicit choices.
- **Spoken language:** track language, Auto detect, or explicit language.
- **Subtitle language:** inherited from the language rail; if different from spoken language, label the operation “Transcribe + translate.”
- **Engine:** installed provider name.
- **Model/profile:** Fast, Balanced, Accurate; show estimated latency and whether downloaded.
- **Processing:** On this TV / Cloud. Cloud displays the provider and retention statement before Start.
- focused primary action **Start generating**; secondary **Back**.

If the selected engine is MPV, the right rail shows **Generation requires ExoPlayer** with a focused **Switch player and continue** action. Do not silently use microphone capture or Android `MediaProjection`.

#### 4.3 Consent

First use per plugin/privacy mode shows a TV-safe confirmation:

> “Nuvio will share the selected program audio with **Provider** to create **English** subtitles. Video links and account headers are never shared.”
>
> On-device: “Audio stays on this TV.”
>
> Cloud: “Audio clips are sent to `provider.example`; retention: …”

Actions: **Allow for this episode**, optionally **Always allow this signed on-device provider** in settings, and **Cancel**. Cloud consent should not have an implicit “always” choice unless policy and retention are independently reviewed.

#### 4.4 Active generation

Use one stable option ID for the generated track. Its label changes without replacing the focused item:

- `Preparing model…` — cancellable download/setup.
- `Listening · 00:42 transcribed` — live and selected automatically only after first renderable cue, unless user chose “generate without selecting.”
- `Paused` — playback paused; no frames leave the broker.
- `Repositioning…` — seek epoch changed, queued audio discarded.
- `Subtitles live` — partial coverage; small progress/coverage value.
- `Complete` — durable result available.
- `Couldn’t continue` — focused **Resume** and **Details** actions.

The right rail switches to **Generation status** with focused **Pause generation**, **Stop & keep captions**, and **Cancel & remove**. Once a generated track is selected, normal Style and Timing remain reachable. Cue arrivals must never steal focus, scroll the rails, dismiss the overlay, or pause playback.

A small persistent player indicator should say **Audio being transcribed** with provider/on-device status. It remains visible while a grant can emit frames, including when the subtitle overlay is closed; it disappears immediately on pause/stop/revocation. Avoid a microphone icon because Nuvio is capturing program playback, not room audio.

#### 4.5 Back and focus behavior

- Left/right retain the existing explicit rail transitions (`SubtitleSelectionOverlay.kt:997-1044`, `:1069-1120`).
- Back from a field returns to the generated option; Back from the overlay dismisses it without cancelling generation.
- Back on a destructive active-session action opens confirmation and initially focuses **Keep generating**.
- Reopening the overlay restores focus to the same generated option ID. Current player code already reacquires play/pause or container focus when transient overlays close (`PlayerScreen.kt:436-481`); preserve that behavior.
- RTL must invert physical D-pad directions exactly as current cards do (`SubtitleSelectionOverlay.kt:1005-1008`, `:1084-1086`).

### 5. Lifecycle policy

| Event | Required host behavior | User-visible result |
|---|---|---|
| Start | Create identity, obtain consent, bind plugin, issue expiring grant, start PCM only after successful bind | Preparing → Listening; playback continues |
| Pause playback | Stop delivering frames; preserve grant/session for a short idle TTL | `Paused`; existing cues remain visible |
| Resume | Resume only if grant, plugin identity, audio track, and media identity still match | `Listening` resumes |
| Seek | Flush queued audio, increment timing epoch, send discontinuity, remove old provisional cues, restart at target | Brief `Repositioning…`; finalized compatible cues retained |
| Scrub repeatedly | Debounce restarts; only the final target opens an epoch | No flood of model restarts |
| Audio track change | Revoke old track grant and create a new identity; require confirmation if consent was track-specific | “Audio changed — continue generation?” |
| Switch to MPV | Revoke Exo PCM grant. Keep finalized cue cache but stop live updates | “Generation paused — switch to ExoPlayer to resume” |
| Switch back to ExoPlayer | Resume only exact compatible finalized cache; create a new in-flight epoch/grant | Resume action, never silent capture |
| Stop & keep captions | Revoke audio immediately; finalize valid cues and retain selected snapshot | Captions remain, marked incomplete |
| Cancel & remove | Revoke, zero/drain frame buffers, delete provisional cues and session files | Return focus to Generate card |
| Player release/process background | Revoke and clean memory synchronously; cloud request cancellation is best-effort but no new frames | Privacy indicator clears |
| Episode transition | End old session, revoke grant, create a new video identity, never carry provisional cues or delay blindly | New episode starts with normal subtitle lookup/generate choice |
| Exact episode replay | Offer compatible finalized cache; never resume a stale plugin process automatically | “Use generated subtitles” or “Continue generation” |

Existing episode switching already resets stream hash/size/filename and selected track state (`PlayerRuntimeControllerStreams.kt:1344-1409`) and refreshes subtitles on a new torrent episode (`:1461-1525`). The generation coordinator must be called from every direct, torrent, cloud, autoplay, and manual switch path rather than relying on one UI event.

### 6. Privacy and security requirements

1. **Explicit purpose and visibility:** Capture begins only after Start + consent. Keep a persistent indicator while frames can flow and an audit entry containing provider, episode identity, start/end time, and disposition—but no audio or transcript text.
2. **Least privilege:** PCM16 mono at the model-required sample rate, selected audio track only, bounded time window, no raw media access. Grant expiry should be measured with monotonic time and renewed only while playback/session state is valid.
3. **Identity verification:** Bind grants to Android package signing certificate or signed module digest. An addon URL or display name is not an identity.
4. **Isolation:** Prefer an isolated on-device service. For cloud engines, use host-mediated allowlisted HTTPS egress. Rate-limit frame bytes, cue events, cue length, revision frequency, and total transcript size.
5. **Output validation:** Reject negative/overflow timings, excessive cue durations, overlapping revision attacks, bidi/control-character abuse, and markup outside the supported subtitle subset. Run existing text sanitization before render (`SubtitleMojibakeSanitizer.kt:7-78`) but do not treat mojibake replacement as a security sanitizer.
6. **Data minimization:** No PCM on disk by default. Provisional cues are memory-only. Final transcript caching is profile-scoped, user-clearable, and included in “clear playback data.” Use app-private storage and profile-bound encryption for durable generated text.
7. **Secrets:** Redact URL query strings and headers from generation logs/crash reports. Track/cache identity uses HMACs, not raw identifiers. Never send playback headers to a plugin or cloud ASR endpoint.
8. **Failure closure:** Binder death, timeout, model crash, engine switch, episode change, player release, grant expiry, or TV sleep closes audio transport before updating UI.
9. **Passthrough disclosure:** If generation forces PCM and changes Dolby/bitstream output, show a one-time nonblocking notice and restore the prior audio path immediately when the grant ends.
10. **No microphone substitution:** `RECORD_AUDIO` already exists for voice search (`app/src/main/AndroidManifest.xml:13-29`), but it does not authorize program-audio capture. Never infer ASR consent from that permission.

#### QR/LAN pairing hardening

The existing server’s unauthenticated routes and plain IP URL are acceptable only as evidence of a UX pattern, not as the media capability design (`AddonConfigServer.kt:34-50`; `AddonManagerViewModel.kt:367-374`). For ASR provider setup:

- QR URL carries a random 128-bit, short-lived, single-use pairing secret, preferably in the URL fragment so the browser app explicitly places it in an `Authorization` header.
- Require TV approval showing provider name, origin, and requested configuration scope.
- Enforce token expiry, one pending approval, client/IP and network binding where practical, origin checks, request/body limits, and rate limiting.
- Stop the server on overlay close/background, as the existing ViewModel stops its server (`AddonManagerViewModel.kt:380-389`, `:413-416`).
- Do not use cookies as the capability, and do not expose any `/audio`, `/stream`, `/headers`, `/transcript`, or grant endpoint over LAN.
- Plain LAN HTTP remains observable. If configuration contains provider credentials, use an external device-code/OAuth flow or an authenticated encrypted channel rather than posting secrets to NanoHTTPD.

### 7. Failure matrix

| Failure | Current/static implication | Proposed TV state and recovery | Test seam |
|---|---|---|---|
| No eligible subtitle addon | Repository returns empty (`SubtitleRepositoryImpl.kt:58-69`) | Empty state + focused Generate | Fake addon manifest set |
| One addon times out | Partial results survive; timeout looks like empty (`:77-124`) | Keep available tracks; “1 provider unavailable”; Retry | Virtual clock + per-addon fake |
| All addons fail | Errors are swallowed to empty (`:189-217`) | Distinguish failed from truly empty; Retry + Generate | Structured repository result |
| Inline stream subtitles exist | Dropped by mapper | Preserve/show before offering generation | Stream mapper unit test |
| Hash cannot be computed | Silent `null` | Session/weak identity; no unsafe durable reuse | MockWebServer HEAD/range cases |
| Server ignores Range | Potential false hash | Reject non-206/mismatched `Content-Range` | 200-to-Range regression test |
| Subtitle is malformed or wrong extension | Robust parser/fallback may still yield no cues | Track-level “Couldn’t read”; Retry/other track | Parser fixture corpus |
| Charset/mojibake | Detector and sanitizer repair common cases | No ASR-specific divergence | Charset + sanitizer fixtures |
| ASR model unavailable/download fails | New | Stable Failed card; Retry or choose model | Fake model manager |
| Plugin crashes/Binder dies | New | Revoke grant first; “Provider stopped”; Resume | Kill fake service |
| Cloud offline/429/5xx | New | Backoff countdown; switch on-device; Stop | Fake egress broker |
| PCM queue overload | New | Playback unaffected; reduce quality and expose diagnostics, not a blocking spinner | Bounded queue stress test |
| Passthrough has no PCM | New | Ask to temporarily switch audio path or cancel | Fake sink capability |
| Out-of-order cue revision | New | Ignore stale revision; never regress final text | Cue-store property tests |
| Seek while provisional text exists | New | New epoch, flush provisional, brief Repositioning | Coordinator reducer test |
| Multiple rapid seeks | New | Debounce and start only final epoch | Virtual clock reducer test |
| Audio track changes | New | Revoke/reconfirm or explicit continue | Track fingerprint test |
| Episode auto-advances | New | Revoke old grant; exact new identity; no cue/delay leakage | Episode transition integration test |
| MPV active | No PCM seam | Switch-to-Exo action; no fake parity claim | Engine capability test |
| External player launch | Only completed cached files can be passed | Export finalized snapshot; label incomplete if partial | File export/URI test |
| Grant expires while playing | New | Close frames, clear indicator, focused Resume | Fake monotonic clock |
| QR token guessed/replayed | Existing server has no token | 401/expiry/single-use + TV approval | HTTP authentication tests |
| Player/process is released | Sidecar jobs are cancellable; generated transport does not yet exist | Revoke, zero buffers, cancel cloud, cleanup provisional files | Lifecycle/instrumentation test |

### 8. Test seams and acceptance plan

#### 8.1 Extract seams before implementation

- `SubtitleAddonEligibility` and `SubtitleRequestUrlBuilder` from private repository functions, with canonical type and percent-encoding tests.
- `MediaFingerprintResolver` returning both value and confidence/quality.
- `GenerationStateReducer` as a pure state machine driven by Start/Pause/Seek/TrackChange/EpisodeChange/Cancel/Failure.
- `AudioFrameSource`, `AudioGrantStore`, `AsrPluginConnection`, `CueEventSink`, `TimedCueSource`, `GeneratedSubtitleCache`, `MonotonicClock`, and `GenerationEgressBroker` interfaces.
- A sidecar renderer seam that accepts snapshots independently of downloading/parsing. This is the critical progressive-update seam.

#### 8.2 Unit tests

1. **Repository:** enabled/resource/type/prefix matrix; `tv`/`series`; movie versus episode ID; exact base-query and filename encoding; one/all timeout; callback ordering; structured partial failure.
2. **Stream mapping:** inline subtitles survive DTO → domain → player startup.
3. **Hashing:** correct 206 first/last bytes; forwarded auth headers without caller Range; absent/invalid length; <128 KiB; cached no-range; ignored Range (`200`); bad `Content-Range`; truncated final block; cancellation.
4. **Decode/parse:** UTF BOMs, Windows code pages, CJK, RTL, malformed SRT/VTT, extensionless URLs, empty bodies, duplicate/overlap, SDH stripping, malicious control characters.
5. **Cue store:** out-of-order sequence, duplicate revision, final immutability, epoch isolation, overlapping cue merge, coalescing, snapshot determinism, maximum-size enforcement.
6. **Identity/cache:** episode/audio/model/language/normalization changes invalidate; signed query tokens and auth headers never appear in keys; weak fingerprints cannot cross sessions; partial versus complete coverage.
7. **Capability:** wrong signer/session/episode/track, expired grant, revoke-before-close ordering, binder death, quotas, cloud egress allowlist.
8. **Reducer:** pause/resume, single and repeated seek, cancel modes, audio change, Exo↔MPV, process release, exact and different episode replay.
9. **QR:** missing/expired/replayed token, wrong origin, body limit, rate limit, one pending approval, server shutdown.

#### 8.3 TV/Compose tests

- No subtitles → Generate card is reachable and initially focusable without trapping focus in an empty option rail.
- Loading → partial tracks → complete and loading → error transitions preserve the active language and option focus.
- Hundreds of cue upserts do not recompose/rekey the option rail or steal D-pad focus.
- Left/right/Back behavior and focus restoration across language, option, generation setup/status, and style rails, including RTL.
- Start consent defaults to Cancel/least privilege where policy requires; destructive stop defaults to Keep generating.
- Provider error exposes focused Retry and still permits Back/dismiss/playback controls.
- Closing/reopening the overlay returns to the same generated option while generation continues.

#### 8.4 Instrumentation and performance tests

- Feed synthetic PCM through ExoPlayer’s custom sink path and verify deterministic cue timing without changing audible samples.
- Verify pause emits no frames, seek creates a new epoch before the next frame, and release emits no later frames.
- Stress a slow plugin: audio rendering must not underrun and the bounded broker must not grow memory without limit.
- Verify passthrough-to-PCM transition and restoration on representative Android TV/Fire TV hardware.
- Kill the plugin and Nuvio processes; verify privacy indicator, Binder handles, shared memory, temporary PCM, and provisional transcript cleanup.
- Confirm MPV shows the explicit unsupported/switch flow and never claims generation is active.
- Export a finalized and a partial generated VTT to an external player and verify URI lifetime/cleanup.

**Existing test inventory relevant to reuse:** charset, MIME sniffing, mojibake, subtitle race behavior, and server sanitization tests exist under `app/src/test/java/com/nuvio/tv/core/player/SubtitleCharsetDetectorTest.kt`, `app/src/test/java/com/nuvio/tv/ui/screens/player/PlayerSubtitleUtilsMimeTest.kt`, `SubtitleMojibakeSanitizerTest.kt`, `SubtitleRaceConditionUpgradeTest.kt`, and `app/src/test/java/com/nuvio/tv/core/server/AddonConfigServerTest.kt`. Server tests currently cover configuration-mode sanitization rather than authentication (`AddonConfigServerTest.kt:8-121`). The focused Gradle test run attempted during research could not start because this environment has no Java Runtime, so no runtime test result is claimed.

## Recommendations

1. **Phase 0 — Correct the static foundation.** Preserve `StreamDto.subtitles`; return structured subtitle lookup outcomes instead of swallowing all failures; pass error/retry state into `SubtitleSelectionOverlay`; require valid `206 Content-Range` for hashing; add repository/hasher regression tests.
2. **Phase 1 — Generalize sidecar rendering.** Extract `TimedCueSource` and `MutableCueStore` while keeping static addon behavior byte-for-byte compatible. Prove that cue-source snapshots update `SubtitleView` without `setMediaSource`, buffer loss, or focus recomposition.
3. **Phase 2 — Build the host coordinator and fake ASR plugin.** Implement the pure lifecycle reducer, identity/cache model, grants, revocation, bounded audio broker, and synthetic cue producer before integrating a real model.
4. **Phase 3 — ExoPlayer on-device MVP.** Add a PCM tap to the existing custom audio sink path, with authoritative timing anchors and seek epochs. Ship one signed on-device model, explicit consent, persistent indicator, Generate/setup/status TV cards, cancellation, and finalized VTT export.
5. **Phase 4 — Cloud provider connector.** Add host-brokered allowlisted egress, provider-specific retention disclosure, rate/size limits, cancellation, and encrypted profile-scoped final transcript cache. Do not send stream credentials.
6. **Phase 5 — Harden configuration pairing.** If QR setup is needed, add single-use auth, TV approval, origin/rate/body controls, rapid shutdown, and OAuth/device-code handling for secrets. Keep media capabilities off LAN.
7. **Phase 6 — MPV parity only after a native design.** Evaluate a libmpv/native decoded-audio callback and timestamp contract. Until it is implemented and tested, offer an explicit ExoPlayer switch rather than Android playback-capture prompts or periodically reloaded subtitle files.
8. **Release gates:** no playback-thread blocking; no post-revocation frames; no raw PCM on disk; no stream secrets in plugin IPC/log/cache; stable D-pad focus under cue updates; correct seek/episode isolation; exact Exo/MPV capability messaging; user-clearable generated data.

## Open Questions

1. **Plugin packaging:** Will ASR engines be first-party modules, Android packages, or downloadable signed artifacts? The choice determines signer verification, process isolation, model updates, and revocation.
2. **Model/device budget:** Which target TVs, ABIs, RAM/thermal limits, languages, and latency goals define Fast/Balanced/Accurate? Hardware profiling is needed before promising on-device real time.
3. **Audio timing API:** The existing custom `AudioSink` wrapper is promising, but the precise Media3 integration for presentation-time anchors and discontinuities must be prototyped; `AudioProcessor.queueInput` alone is not timestamped.
4. **Passthrough policy:** Is temporarily forcing PCM acceptable, and how should Nuvio communicate loss of bitstream formats? Device-specific audio tests will resolve this.
5. **Translation:** Is output-language translation in the same plugin capability or a separately consented transcript-to-translation stage? Separating them improves purpose limitation and cache provenance.
6. **Caching default:** Should finalized generated subtitles persist automatically, per profile, or only after “Keep captions”? Product/privacy policy must define retention and clearing semantics.
7. **Cloud legal policy:** Provider region, retention, training use, age/profile restrictions, and deletion API are not determinable from this codebase; each provider needs a reviewed declaration before inclusion.
8. **DRM/protected content:** Even decoded in-process audio may be contractually restricted. Content/provider policy and Android secure-decoder behavior require legal and device validation.
9. **MPV native feasibility:** No decoded-audio callback is visible in the inspected Kotlin layer. A native/libmpv review is required to determine whether equivalent timing and capture can be provided without Android `MediaProjection`.
10. **Test execution:** This research environment lacks a Java Runtime, so the proposed and existing JVM tests must be run in the project’s configured Android/JDK CI environment.
