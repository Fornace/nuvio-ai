# Source Citation Audit: Lanes 04-08

## Severity: HIGH

## Scope and method

- Baseline verified: repository `HEAD` is `eca648a86de8021a47299549d6dedbf0420b7188`, matching the reports.
- Spot-checked **100 repository `path:line` citations** across all five reports, 20 per lane. Checks required the cited range to contain the named symbol and to support the stated behavior, not merely point to the right file.
- Result: **94/100 accurate, 94.0% citation accuracy**. Lane results: 04 `18/20`, 05 `19/20`, 06 `18/20`, 07 `19/20`, 08 `20/20`.
- The 100 checks below are distinct report claims. A semicolon-separated group is one claim when the report used multiple ranges to support one interpretation.

## Issues Found

### 1. Prohibited dash characters occur throughout every report - HIGH

**Location**: `docs/ai-media-research/lanes/04-nuvio-extension-ecosystem.md:1,118,165,169,171`; `docs/ai-media-research/lanes/05-player-audio-integration.md:1,7,37,41,47,51,57,73,87,170,217-222`; `docs/ai-media-research/lanes/06-subtitle-integration-ux.md:9-35,193,271-277,300-301,313,409-415`; `docs/ai-media-research/lanes/07-byok-security-tv-ux.md:1,16,38,60,106,110,232-233,250,256,266,285,401-402,410,425`; `docs/ai-media-research/lanes/08-feasibility-economics.md:1,5,19,24-26,83,87,91,252,279,308,338,353,370,383,433-438`

**Problem**: All five reports violate the prohibition on em/en dashes. Literal scan found **105 em dashes** and **6 en dashes**: lane 04 `5/0`, lane 05 `19/2`, lane 06 `38/1`, lane 07 `22/0`, lane 08 `21/3` (em/en). Affected line counts are 5, 16, 39, 16, and 22 respectively.

**Impact**: The document set fails a stated publishing constraint even where its source claims are correct.

**Fix**: Replace prose em dashes with commas, colons, parentheses, or ` - `. Replace numeric en-dash ranges with `to` or a plain hyphen as required by the documentation standard. Also fix em dashes in headings and Markdown link labels.

### 2. Lane 04 overstates the production DEX execution sequence - MEDIUM

**Location**: `docs/ai-media-research/lanes/04-nuvio-extension-ecosystem.md:20` citing `app/src/full/java/com/nuvio/tv/core/plugin/cloudstream/ExternalExtensionRunner.kt:49-89,361-372`

**Problem**: The cited class comment says `TMDB ID -> title lookup -> search() -> match -> load() -> loadLinks()`, but production dispatch at `ExternalExtensionRunner.kt:345-349` branches `TmdbProvider` into `executeTmdbProvider`, whose path begins with JSON/URL `load()` and then `loadLinks()` (`:352-457`), without `search()`. The `search()` sequence applies only to search-based providers (`:474-610`). The cited `:361-372` is a comment about the TmdbProvider load path, not evidence for a universal search sequence.

**Impact**: Readers may design integration or tests around a mandatory search stage that a supported provider class bypasses.

**Fix**: State: “Execution dispatches either a `TmdbProvider` load/loadLinks path or a TMDB-title search/match/load/loadLinks path.” Cite `ExternalExtensionRunner.kt:345-372,474-610`.

### 3. Lane 04 cites diagnostics-only TMDB-to-IMDb context as general DEX behavior - MEDIUM

**Location**: `docs/ai-media-research/lanes/04-nuvio-extension-ecosystem.md:153` citing `app/src/full/java/com/nuvio/tv/core/plugin/cloudstream/ExternalExtensionRunner.kt:361-432`

**Problem**: The capability row says DEX receives “search/load/data + optional TMDB ids,” but `:361-432` covers the production `TmdbProvider` JSON/load path, not search-based execution, and does not construct the optional IMDb/TMDB `TmdbLink`. That richer `TmdbLink` context appears in the diagnostics path at `:138-168`. Production search-based flow is at `:474-610`.

**Impact**: The matrix blurs production and diagnostics contracts, overstating the exact context consistently delivered to extensions.

**Fix**: Split the row by provider path. Cite `:345-457` for production `TmdbProvider`, `:474-610` for production search-based providers, and label `:138-168` diagnostics-only if mentioning `TmdbLink`/IMDb context.

### 4. Lane 05 says the VOD cache has no assignment without proving a repository-wide negative - MEDIUM

**Location**: `docs/ai-media-research/lanes/05-player-audio-integration.md:93` citing `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerMediaSourceFactory.kt:154-181,314-316,377-392`

**Problem**: The ranges show `sharedSimpleCache` declared and read, but a local range cannot establish “no assignment exists in this tree.” A repository search does support the negative, but the report does not identify that as search evidence. The citation is therefore incomplete for the behavioral conclusion.

**Impact**: A future assignment elsewhere could invalidate the claim while every cited line remains unchanged.

**Fix**: Rephrase to “Within `PlayerMediaSourceFactory`, `sharedSimpleCache` is declared and read, and a baseline-wide symbol search found no write.” Cite declaration/read lines and explicitly record the search scope.

### 5. Lane 06 misreads the OpenSubtitles range-response condition - HIGH

**Location**: `docs/ai-media-research/lanes/06-subtitle-integration-ux.md:34,56,365` citing `app/src/main/java/com/nuvio/tv/core/player/OpenSubtitlesHasher.kt:92-131` / `:113-115`

**Problem**: The report correctly notices that HTTP 200 is accepted, but incorrectly says the code accepts “any successful response” and that `:113-115` should require 206. The exact condition at `OpenSubtitlesHasher.kt:114` is `if (!response.isSuccessful && response.code != 206) return 0L`; because 206 is already successful, the second clause is redundant. More importantly, rejected responses return a zero chunk sum rather than failing the hash. Short/truncated bodies are also accepted as partial sums. No `Content-Range` validation exists.

**Impact**: The proposed fix is directionally right but underspecifies the bug: simply changing success handling can still return a plausible but false hash when reads are short or invalid.

**Fix**: Say: “`readChunkSum` accepts every 2xx response, including 200 to a Range request, does not validate `Content-Range`, and converts rejected/short reads into partial or zero sums rather than failing `compute`.” Require exact 206, matching range/total in `Content-Range`, and exactly 64 KiB read; return failure otherwise.

### 6. Lane 06 says subtitle addons are deliberately denied media access, but the source only shows current omission - MEDIUM

**Location**: `docs/ai-media-research/lanes/06-subtitle-integration-ux.md:17-19` citing `PlayerNavigationArgs.kt:7-38,65-97`, `PlayerRuntimeController.kt:183-216`, `PlayerRuntimeControllerObservers.kt:40-104`

**Problem**: The cited code proves that runtime has URL/headers and sends only ID/hash/size/filename to `SubtitleRepository`. It does not prove intent or a deliberate security policy. No cited comment or policy symbol states that this omission is deliberate.

**Impact**: Architectural accident is presented as an established trust-boundary decision.

**Fix**: Replace “deliberately does not expose” with “does not currently expose.” Keep the recommendation to preserve/formalize the boundary as proposal, not source fact.

### 7. Lane 07 overstates which clients inherit trust-all TLS - HIGH

**Location**: `docs/ai-media-research/lanes/07-byok-security-tv-ux.md:86` citing `app/src/main/java/com/nuvio/tv/core/di/NetworkModule.kt:103-116,127-143,169-184`

**Problem**: The main injected client is trust-all and the Trakt client derives from it, but `directDebrid` is independently built at `:169-184`. The cited `:127-143` is the independently built `customServerAuth` client, not evidence that “addon, metadata, or other derived clients” inherit trust-all. Addon APIs likely use the unqualified client through DI, but that binding should be cited directly; the current range grouping is misleading.

**Impact**: The security conclusion is serious and mostly valid, but inaccurate client attribution can cause remediation to miss actual consumers or modify already-safe clients.

**Fix**: Enumerate verified consumers separately: unqualified client is trust-all; Trakt derives from it; directDebrid/customServerAuth/Simkl are separately constructed. Cite their provider methods and the Retrofit/API bindings that consume the unqualified client.

## Verified spot checks

### Lane 04: 18 accurate, 2 mismatched

1. `Addon.kt:6-28` contains `Addon` manifest state. **Accurate**.
2. `AddonManifestDto.kt:8-24` contains the listed manifest fields. **Accurate**.
3. `AddonApi.kt:13-28` exposes exactly five GET methods. **Accurate**.
4. `AddonMapper.kt:52-75` parses string/object resource forms. **Accurate**.
5. `Plugin.kt:39-69,100-117` contains plugin manifest and result contracts. **Accurate**.
6. `PluginRuntime.kt:129-150,214-254` invokes one `getStreams` export. **Accurate**.
7. `Plugin.kt:119-146; ExternalRepoParser.kt:56-104` supports external repo forms. **Accurate**.
8. `ExternalExtensionLoader.kt:165-195,205-260,276-281` shows 10 MB DEX loading. **Accurate**.
9. `ExternalExtensionRunner.kt:49-89,361-372` universal search sequence. **Mismatch 2**.
10. `app/build.gradle.kts:152-173,504-521` flavor/dependency gates. **Accurate**.
11. `AddonPreferences.kt:28-56,66-84` profile-scoped addon keys/flow. **Accurate**.
12. `AddonRepositoryImpl.kt:44-58,108-158` app-level 6-hour manifest cache. **Accurate**.
13. `PluginDataStore.kt:224-257; PluginRuntime.kt:451-452,680-681; PluginManager.kt:828-845` settings injection. **Accurate**.
14. `StreamResponseDto.kt:23; StreamMapper.kt:18-34; Stream.kt:10-30` inline subtitles dropped. **Accurate**.
15. `PluginRuntime.kt:1331-1374` result parser excludes subtitle/audio/progress. **Accurate**.
16. `ExternalExtensionRunner.kt:172-198,324-334,437-471,595-624` DEX subtitles collected then omitted. **Accurate**.
17. `PlayerMediaSourceFactory.kt:82-207; TrailerPlayer.kt:112-114,188-190` no main merge and trailer merge precedent. **Accurate**.
18. `NetworkModule.kt:103-116` unqualified OkHttp trust-all TLS. **Accurate**.
19. `AddonConfigServer.kt:29-54,137-184` unauthenticated LAN routes with confirmation flow. **Accurate**.
20. `ExternalExtensionRunner.kt:361-432` production DEX context matrix. **Mismatch 3**.

### Lane 05: 19 accurate, 1 mismatched

1. `HeroCarousel.kt:419-425; ClassicFocusGradientBackdrop.kt:96-102` baseline change. **Accurate**.
2. `Stream.kt:10-27,182-190` lacks auxiliary audio and holds proxy headers. **Accurate**.
3. `StreamScreenViewModel.kt:1302-1335,1820-1854` playback boundary carries primary URL/headers. **Accurate**.
4. `Screen.kt:70-133; PlayerNavigationArgs.kt:8-38,50-99` navigation serializes/decodes them. **Accurate**.
5. `PlayerRuntimeController.kt:187-224; PlayerMediaSourceFactory.kt:322-354,674-728` runtime normalization. **Accurate**.
6. `PlayerMediaSourceFactory.kt:80-195,643-655` one source plus audio-delay wrapper. **Accurate**.
7. `PlayerRuntimeControllerInitialization.kt:894-1018,1284-1305` build/set/prepare startup sequence. **Accurate**.
8. `PlayerRuntimeControllerTracks.kt:24-137,291-319` Media3 audio enumeration. **Accurate**.
9. `PlayerRuntimeControllerTrackSelection.kt:44-80` override plus one-millisecond nudge. **Accurate**.
10. `NuvioMpvSurfaceView.kt:35-210,251-267,500-580` MPV load/track/delay/seek/speed surfaces. **Accurate**.
11. `NuvioMpvSurfaceView.kt:444-481` only subtitle add, no audio-add wrapper. **Accurate**.
12. `PlayerRuntimeControllerPlaybackEvents.kt:25-48; AudioSelectionOverlay.kt:371-500` delay bounds/step/UI. **Accurate**.
13. `AudioDelayMediaSource.kt:70-103,145-170` shifts video timestamps live. **Accurate**.
14. `PlayerRuntimeControllerPlaybackEvents.kt:1173-1239,1352-1374` centralized seek/speed. **Accurate**.
15. `PlayerUiState.kt:239-250` flattened `TrackInfo` lacks origin/readiness. **Accurate**.
16. `PlayerRuntimeControllerEngineFailover.kt:10-132,143-258` failover restores loose identity. **Accurate**.
17. `ExternalPlayerLauncher.kt:12-94; PlayerViewModel.kt:264-367` one video URI plus subtitles/headers. **Accurate**.
18. `PlayerMediaSourceFactory.kt:154-181,314-316,377-392` no cache assignment. **Mismatch 4**.
19. `PlayerRuntimeControllerLifecycle.kt:7-78; PlayerRuntimeController.kt:652-667` teardown/onCleared. **Accurate**.
20. `NuvioMpvSurfaceView.kt:631-632` 64 MiB forward/back cache budgets. **Accurate**.

### Lane 06: 18 accurate, 2 mismatched

1. `SubtitleRepositoryImpl.kt:50-68,127-154` addon eligibility. **Accurate**.
2. `SubtitleRepositoryImpl.kt:156-185,220-237` request identity/extras. **Accurate**.
3. `SubtitleResponseDto.kt:7-16` response fields. **Accurate**.
4. `PlayerSidecarSubtitles.kt:98-175` full-body parse then render loop. **Accurate**.
5. `PlayerSidecarSubtitles.kt:178-200` timing, filtering, merge, `setCues`. **Accurate**.
6. `PlayerNavigationArgs.kt:7-38,65-97` media identity/navigation inputs. **Accurate**.
7. `PlayerRuntimeController.kt:183-216; Observers.kt:40-104` “deliberately” excludes access. **Mismatch 6**.
8. `SubtitleSelectionOverlay.kt:225-363,394-436,593-719,997-1120` three-rail focus/loading UX. **Accurate**.
9. `PlayerUiState.kt:121-126; PlayerScreen.kt:1534-1552` error exists but is not passed. **Accurate**.
10. `SubtitleRepositoryImpl.kt:82-116,189-217; Observers.kt:107-151` errors collapse to empty. **Accurate**.
11. `PlayerRuntimeControllerInitialization.kt:805-848,2065-2095` custom audio sink/processor seam. **Accurate**.
12. `GainAudioProcessor.kt:29-61` PCM16/float handling. **Accurate**.
13. `NuvioMpvSurfaceView.kt:590-614,431-479` audio output and no PCM callback. **Accurate**.
14. `PluginRuntime.kt:286-304,48-66` broad fetch and client. **Accurate**.
15. `AddonConfigServer.kt:20-50,156-255; AddonManagerViewModel.kt:361-377,600-646` LAN/confirmation pattern. **Accurate**.
16. `StreamResponseDto.kt:12-24,108-113; StreamMapper.kt:18-32; Stream.kt:9-27` inline subtitle loss. **Accurate**.
17. `OpenSubtitlesHasher.kt:92-131` range validation interpretation. **Mismatch 5**.
18. `SubtitleRepositoryImpl.kt:71-124` concurrent provider discovery. **Accurate**.
19. `PlayerRuntimeControllerSubtitleTiming.kt:196-264` same-host subtitle header forwarding. **Accurate**.
20. `TrackPreferenceDataStore.kt:17-65,125-171; PlayerRuntimeControllerTracks.kt:1087-1151` persisted addon-track identity. **Accurate**.

### Lane 07: 19 accurate, 1 mismatched

1. `AndroidSimklAuthStorage.kt:29-40,79-87,203-284` AES-GCM/Keystore profile storage. **Accurate**.
2. `DebridSettingsDataStore.kt:42-50,62-89,133-160` plaintext API keys. **Accurate**.
3. `MDBListSettingsDataStore.kt:23-42,58-60` plaintext API key. **Accurate**.
4. `TraktAuthDataStore.kt:29-185` plaintext tokens/device codes. **Accurate**.
5. `PluginDataStore.kt:52-71,192-256` arbitrary settings and code storage. **Accurate**.
6. `ProviderCredentialSyncService.kt:38-58,83-243; Models.kt:16-45` raw credential JSON sync. **Accurate**.
7. `SupabaseModule.kt:48-56,100-108` active server and automatic auth persistence. **Accurate**.
8. `AddonPreferences.kt:46-75,98-110; AddonRepositoryImpl.kt:59-72,220-236` query-bearing URLs persist/fetch. **Accurate**.
9. `AddonSyncService.kt:53-79` full addon URLs sync. **Accurate**.
10. `AddonConfigServer.kt:10-50,76-91,160-250` unauthenticated LAN reads/writes. **Accurate**.
11. `LogDiagnostics.kt:3-25` no-op safe-log helpers/raw exception messages. **Accurate**.
12. `SentryInitializer.kt:54-108` partial PII controls and request/user-only scrub. **Accurate**.
13. `SentryNetworkBreadcrumbInterceptor.kt:48-88` raw path/error message, scrubbed query. **Accurate**.
14. `PluginRuntime.kt:280-300,506-600` plugin/request/response logging. **Accurate**.
15. `PluginRuntime.kt:286-305,448-452,506-558,676-714` QuickJS settings/key/fetch exposure. **Accurate**.
16. `DataStore.kt:13-33,39-92; PluginRuntimeHooks.kt:40-62` DEX same-process data/client exposure. **Accurate**.
17. `NetworkModule.kt:103-116,127-143,169-184` client inheritance description. **Mismatch 7**.
18. `ProfileDataStoreFactory.kt:135-219` plaintext shadow copies. **Accurate**.
19. `ProfileDataStoreFactory.kt:105-132; ProfileManager.kt:105-139` `.bak` cleanup gap. **Accurate**.
20. `TrackingModule.kt:38-40` only Simkl bound as profile credential store. **Accurate**.

### Lane 08: 20 accurate, 0 mismatched

1. `Stream.kt:181-189` proxy request/response headers. **Accurate**.
2. `StreamMapper.kt:104-122` header sanitation removes Range. **Accurate**.
3. `PlayerMediaSourceFactory.kt:82-113,197-205` applies headers to source factories. **Accurate**.
4. `PlayerPlaybackNetworking.kt:94-117` cross-host Authorization reattachment. **Accurate**.
5. `PlayerMediaSourceFactory.kt:96-113` no DRM configuration. **Accurate**.
6. `PlayerSidecarSubtitles.kt:26-39,84-200` buffer-preserving sidecar/cancellation. **Accurate**.
7. `AudioDelayMediaSource.kt:1-129` timestamp wrapper, not PCM remix. **Accurate**.
8. `app/build.gradle.kts:452-496` media/decoder/mpv dependencies. **Accurate**.
9. `TorrentService.kt:49-89,120-135; TorrServerApi.kt:145-148` local torrent gateway/deadline. **Accurate**.
10. `AndroidManifest.xml:13-29,73-81` optional mic and no AI worker service. **Accurate**.
11. `app/build.gradle.kts:152-172; playstore PluginManager.kt:12-74` flavor gates/stub. **Accurate**.
12. `StreamLinkCacheDataStore.kt:42-75` raw URL/header persistence. **Accurate**.
13. `PlayerRuntimeControllerLifecycle.kt:11-63` player-owned cancellation/torrent stop. **Accurate**.
14. `Subtitle.kt:6-18` minimal subtitle domain. **Accurate**.
15. `PlayerRuntimeControllerSubtitleTiming.kt:196-263` same-host forwarding boundary. **Accurate**.
16. `SubtitleRepositoryImpl.kt:31-34,77-124` 20-second parallel lookups. **Accurate**.
17. `TorrentService.kt:92-112` torrent stop/drop/reset. **Accurate**.
18. `PlayerPlaybackNetworking.kt:67-140` headers/redirect/range-capable stack. **Accurate**.
19. `PlayerRuntimeControllerTorrent.kt:14-16` TorrServer owns seek/piece behavior. **Accurate**.
20. `Stream.kt:10-100,181-211` repository evidence summary. **Accurate**.

## Summary

Overall assessment: **fix-first**. Repository sourcing is strong at **94.0%**, and most symbols and behavioral interpretations survive literal verification. Publication should still be blocked until the six material citation/interpretation mismatches are corrected and all **111 prohibited dash characters** are removed from lanes 04-08.
