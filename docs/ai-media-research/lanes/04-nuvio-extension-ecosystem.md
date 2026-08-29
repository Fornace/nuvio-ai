# Lane 04 , Nuvio Extension Ecosystem Archaeology (commit eca648a8)

Scope: Stremio-compatible HTTP addons, Nuvio native JS plugins, CloudStream DEX extensions at commit `eca648a8`. All file references are exact paths/lines at that commit; no source files were modified.

## Executive summary

Nuvio has three parallel source ecosystems, but only two output channels: `Stream`/`AddonStreams` for playback and `Subtitle` for text. **None of the three extension types can, at commit eca648a8, asynchronously request AI dubbing, return an external audio track, or report job progress into playback**; only subtitle URL collection is a first-class output, and even then two paths silently discard collected subtitles (`StreamDto.subtitles`, DEX `SubtitleFile`s). AI subtitles can be achieved **without host changes** today if an AI subtitle addon/provider exposes a ready `.srt/.vtt/.ass` URL as a Stremio `subtitles` resource; AI dubbing cannot.

## 1. Ecosystem topology

### 1.1 Stremio-compatible HTTP addons
- Model: `Addon` (manifest state) at `app/src/main/java/com/nuvio/tv/domain/model/Addon.kt:6-28`.
- Manifest DTO: `AddonManifestDto` fields `id/name/version/catalogs/resources/types/idPrefixes/behaviorHints/stremioAddonsConfig/configVersion/timestamp` at `app/src/main/java/com/nuvio/tv/data/remote/dto/AddonManifestDto.kt:8-24`.
- HTTP client contract: Retrofit `AddonApi` has only `getManifest/getCatalog/getMeta/getStreams/getSubtitles`, all GET with `@Url`, at `app/src/main/java/com/nuvio/tv/data/remote/api/AddonApi.kt:13-28`.
- Resource parsing supports simple strings and `{name,types,idPrefixes}` objects at `app/src/main/java/com/nuvio/tv/data/mapper/AddonMapper.kt:52-75`.

### 1.2 Nuvio native JS plugins (NUVIO_JS)
- Model: `PluginManifest` + `ScraperManifestInfo` at `app/src/main/java/com/nuvio/tv/domain/model/Plugin.kt:39-69`; result contract is `LocalScraperResult` at `app/src/main/java/com/nuvio/tv/domain/model/Plugin.kt:100-117`.
- Repository type enum distinguishes native vs external at `app/src/main/java/com/nuvio/tv/domain/model/Plugin.kt:7-11`.
- Repo add path: `PluginManager.addRepository` auto-detects Nuvio manifest first, then external; Nuvio code downloads to per-profile `.js` files at `app/src/full/java/com/nuvio/tv/core/plugin/PluginManager.kt:297-354`, `397-416`, `964-1061`.
- Runtime: QuickJS wrapper executes exactly one async entry `module.exports.getStreams(tmdbId, mediaType, season, episode)` at `app/src/full/java/com/nuvio/tv/core/plugin/PluginRuntime.kt:129-150`, `214-254`.

### 1.3 CloudStream DEX extensions (EXTERNAL_DEX)
- Repo formats: repo manifest with `pluginLists` or direct plugin array at `app/src/main/java/com/nuvio/tv/domain/model/Plugin.kt:119-146`, `app/src/full/java/com/nuvio/tv/core/plugin/cloudstream/ExternalRepoParser.kt:56-104`.
- `.cs3` downloads to app-private `cs_extensions`, loaded via `DexClassLoader`; max 10 MB at `app/src/full/java/com/nuvio/tv/core/plugin/cloudstream/ExternalExtensionLoader.kt:165-195`, `205-260`, `276-281`.
- Execution bridge has two production paths: a `TmdbProvider` takes a JSON/URL `load()` then `loadLinks()` path, while search-based providers resolve TMDB metadata, run `search()`, match, `load()`, then `loadLinks()`. See `app/src/full/java/com/nuvio/tv/core/plugin/cloudstream/ExternalExtensionRunner.kt:345-457`, `474-610`.

### 1.4 Flavor gates
- Gradle flavors: `full` sets `FEATURE_PLUGINS_ENABLED=true`, `playstore=false` at `app/build.gradle.kts:152-173`; dependencies `quickjs`, `cloudstream` are `fullImplementation` only at `app/build.gradle.kts:504-521`.
- Runtime policy: full enables plugins, playstore disables at `app/src/full/java/com/nuvio/tv/core/build/AppFeaturePolicy.kt:3-10` and `app/src/playstore/java/com/nuvio/tv/core/build/AppFeaturePolicy.kt:3-10`.
- Navigation/UI gate: plugin route is only registered when `AppFeaturePolicy.pluginsEnabled` at `app/src/main/java/com/nuvio/tv/ui/navigation/NuvioNavHost.kt:1278-1284`; Settings row is gated at `app/src/main/java/com/nuvio/tv/ui/screens/settings/SettingsScreen.kt:812-824`.
- Playstore `PluginManager` is a stub returning empty flows / failures at `app/src/playstore/java/com/nuvio/tv/core/plugin/PluginManager.kt:13-74`.

## 2. Install, config, profile storage

### 2.1 Stremio HTTP addons
- Installed URLs live in profile DataStore `addon_preferences`, keys `installed_addon_urls_ordered`, legacy set, user names, enabled states at `app/src/main/java/com/nuvio/tv/data/local/AddonPreferences.kt:28-56`, `66-84`.
- Effective profile uses primary profile 1 when `usesPrimaryAddons` at `app/src/main/java/com/nuvio/tv/data/local/AddonPreferences.kt:30-45`; profile model at `app/src/main/java/com/nuvio/tv/domain/model/UserProfile.kt:3-15`.
- Defaults: Cinemeta + OpenSubtitles at `app/src/main/java/com/nuvio/tv/data/local/AddonPreferences.kt:241-245`.
- Install paths:
  - TV URL paste normalizes `stremio://` → `https://`, strips `/manifest.json` at `app/src/main/java/com/nuvio/tv/ui/screens/addon/AddonManagerViewModel.kt:129-161`, `190-207`.
  - Deep links `nuvio://` and `stremio://` install via `DeepLinkHandler.installAddon` at `app/src/main/java/com/nuvio/tv/core/deeplink/DeepLinkParser.kt:6-40`, `app/src/main/java/com/nuvio/tv/core/deeplink/DeepLinkHandler.kt:13-27`; manifest handler calls `addonRepository.addAddon`.
- Config:
  - URL query configuration is preserved in canonical base URL (query separated from path) at `app/src/main/java/com/nuvio/tv/data/repository/AddonRepositoryImpl.kt:60-75`, `220-226`.
  - Manifest `behaviorHints.configurable/configurationRequired` are parsed at `app/src/main/java/com/nuvio/tv/data/remote/dto/AddonManifestDto.kt:39-41` and mapped to domain at `app/src/main/java/com/nuvio/tv/data/mapper/AddonMapper.kt:128-133`, but grep shows no UI use beyond model/mapper/cache (`app/src/main/java/com/nuvio/tv/domain/model/Addon.kt:68-72`; only hits are DTO/mapper/repository). There is no `/configure` launcher in the addon UI at this commit.
- Manifest cache is app-level SharedPreferences `addon_manifest_cache` with 6h TTL, **not per-profile**, at `app/src/main/java/com/nuvio/tv/data/repository/AddonRepositoryImpl.kt:44-58`, `108-158`.
- Sync pushes URL/name/enabled/order only, no opaque config blob at `app/src/main/java/com/nuvio/tv/core/sync/AddonSyncService.kt:47-79`.

### 2.2 Native JS plugins
- Profile store `plugin_settings` keys `repositories`, `scrapers`, `plugins_enabled`, `group_streams_by_repository`, `scraper_settings` at `app/src/main/java/com/nuvio/tv/data/local/PluginDataStore.kt:36-64`.
- Effective profile uses primary profile 1 when `usesPrimaryPlugins` at `app/src/main/java/com/nuvio/tv/data/local/PluginDataStore.kt:36-55`.
- Per-profile code dir `plugin_code` for profile 1, `plugin_code_p${pid}` for others at `app/src/main/java/com/nuvio/tv/data/local/PluginDataStore.kt:67-77`; code files `${scraperId}.js` at `app/src/main/java/com/nuvio/tv/data/local/PluginDataStore.kt:193-214`.
- **Provider config exists structurally**: `getScraperSettings/setScraperSettings` at `app/src/main/java/com/nuvio/tv/data/local/PluginDataStore.kt:224-257`, injected to JS as `SCRAPER_SETTINGS` at `app/src/full/java/com/nuvio/tv/core/plugin/PluginRuntime.kt:451-452`, `680-681`; execution passes settings from `PluginManager` at `app/src/full/java/com/nuvio/tv/core/plugin/PluginManager.kt:828-845`.
- **No settings UI** writes them: `PluginUiEvent` has no settings/config event at `app/src/main/java/com/nuvio/tv/ui/screens/plugin/PluginUiState.kt:45-63`; `PluginViewModel` only add/remove/refresh/toggle/test/QR at `app/src/main/java/com/nuvio/tv/ui/screens/plugin/PluginViewModel.kt:87-107`.
- Sync pushes repo URL/name/enabled/order/repo_type only; scraper settings are local-only at `app/src/main/java/com/nuvio/tv/core/sync/PluginSyncService.kt:47-77`.

### 2.3 CloudStream DEX extensions
- Repo/plugin state shares `PluginDataStore` as `ScraperInfo.type=EXTERNAL_DEX` at `app/src/full/java/com/nuvio/tv/core/plugin/PluginManager.kt:1068-1119`, `app/src/main/java/com/nuvio/tv/domain/model/Plugin.kt:71-97`.
- Provider config APIs:
  - `DataStore` SharedPreferences `cs3_plugin_preferences` with real `setKey/getKey` persistence at `app/src/full/java/com/lagradost/cloudstream3/utils/DataStore.kt:13-25`, `80-129`.
  - `Plugin.openSettings` exists but comment says **No-op in NuvioTV** and grep shows no invocation at `app/src/full/java/com/lagradost/cloudstream3/plugins/Plugin.kt:24`.
  - `AcraApplication` and `CloudStreamApp` key/value APIs are mostly stubs/defaults at `app/src/full/java/com/lagradost/cloudstream3/AcraApplication.kt:52-79`, `app/src/full/java/com/lagradost/cloudstream3/CloudStreamApp.kt:51-77`.
- No settings UI route exists for DEX extensions (`PluginUiState.kt:45-63`, `PluginViewModel.kt:87-107`).

## 3. Calls, runtime/sandbox APIs, context, outputs

### 3.1 Stremio HTTP addon call graph and context
- Manifest: GET `${basePath}/manifest.json${query}` at `app/src/main/java/com/nuvio/tv/data/repository/AddonRepositoryImpl.kt:220-226`.
- Catalog: GET `/catalog/{type}/{catalogId}[/{extra}].json` with skip support at `app/src/main/java/com/nuvio/tv/data/repository/CatalogRepositoryImpl.kt:24-77`, `84-117`.
- Meta: GET `/meta/{type}/{id}.json` at `app/src/main/java/com/nuvio/tv/data/repository/MetaRepositoryImpl.kt:367-376`, `570-576`.
- Streams: GET `/stream/{type}/{videoId}.json` at `app/src/main/java/com/nuvio/tv/data/repository/StreamRepositoryImpl.kt:563-590`; gating uses `resources.name == "stream"` + type/idPrefix at `app/src/main/java/com/nuvio/tv/data/repository/StreamRepositoryImpl.kt:613-624`.
- Subtitles: GET `/subtitles/{type}/{idOrVideoId}[/videoHash=..&videoSize=..&filename=..].json` at `app/src/main/java/com/nuvio/tv/data/repository/SubtitleRepositoryImpl.kt:156-190`, extras at `220-242`; gating detects `subtitles` or `subtitle` resources at `146-153`.
- Request headers: shared OkHttp adds `User-Agent: Nuvio/<version>` and `Accept-Language` at `app/src/main/java/com/nuvio/tv/core/di/NetworkModule.kt:119-126`, `76-92`.

### 3.2 Stremio HTTP addon outputs
- Streams DTO includes `name/title/description/url/ytId/infoHash/fileIdx/externalUrl/behaviorHints/sources/subtitles/clientResolve` at `app/src/main/java/com/nuvio/tv/data/remote/dto/StreamResponseDto.kt:12-24`.
- **Dropped stream fields**:
  - `StreamDto.subtitles` is parsed at `StreamResponseDto.kt:23` but **never mapped** in `StreamMapper.toDomain` (`app/src/main/java/com/nuvio/tv/data/mapper/StreamMapper.kt:18-34`).
  - Domain `Stream` has no `subtitles` field at `app/src/main/java/com/nuvio/tv/domain/model/Stream.kt:10-30`.
  - Only grep references to `StreamDto.subtitles` outside DTO are absent; no collector consumes it (`app/src/main/java/com/nuvio/tv/data/mapper/StreamMapper.kt:18-34`, `app/src/main/java/com/nuvio/tv/data/repository/StreamRepositoryImpl.kt:588-597`).
- Behavior hints proxy headers map to `ProxyHeaders` at `StreamMapper.kt:97-121`; headers sanitize out `Range` at `109-121`.
- Inline video streams are supported via meta videos at `app/src/main/java/com/nuvio/tv/data/remote/dto/MetaResponseDto.kt:80-90`, `app/src/main/java/com/nuvio/tv/data/mapper/MetaMapper.kt:73`, `app/src/main/java/com/nuvio/tv/data/repository/StreamRepositoryImpl.kt:210-217`, `630-684`.

### 3.3 Native JS runtime APIs and context
- Sandbox is QuickJS, no DOM; host defines console, fetch, URL, cheerio, settings, TMDB key at `app/src/full/java/com/nuvio/tv/core/plugin/PluginRuntime.kt:270-474`, `676-744`.
- Exposed globals:
  - `SCRAPER_ID`, `SCRAPER_SETTINGS`, `TMDB_API_KEY` at `PluginRuntime.kt:676-684`.
  - fetch polyfill calls `__native_fetch` with arbitrary method/headers/body at `PluginRuntime.kt:692-744`.
  - Cheerio/jsoup bridge and `require('cheerio'|'crypto-js')` at `PluginRuntime.kt:269-443`, `1260-1270`.
- Only exported call surface is `getStreams` (no `getSubtitles`, `process`, `dub`, `audio`, or progress hooks) at `PluginRuntime.kt:129-150`.
- Result parser accepts only fields mapping to `LocalScraperResult`: title/name/url/quality/size/language/provider/type/seeders/peers/infoHash/headers at `PluginRuntime.kt:1331-1374`; no subtitle/audio/progress fields are read.
- Timeout/caps: 60s plugin timeout, 1 MiB fetch response/body caps at `PluginRuntime.kt:40-43`, `214-224`; outer 120s scraper timeout at `app/src/full/java/com/nuvio/tv/core/plugin/PluginManager.kt:54-60`, `831-849`.

### 3.4 CloudStream DEX runtime APIs and context
- `Plugin.load()` registers `MainAPI`/`ExtractorApi` to local registries; global APIHolder mapping also attempted at `app/src/full/java/com/lagradost/cloudstream3/plugins/Plugin.kt:33-65`.
- Fallback direct DEX scans instantiate MainAPI/ExtractorApi classes at `app/src/full/java/com/nuvio/tv/core/plugin/cloudstream/ExternalExtensionLoader.kt:330-410`, `575-654`.
- `MainAPI.loadLinks(data, isCasting, subtitleCallback, callback)` is invoked with link + subtitle callbacks at `ExternalExtensionRunner.kt:179-186`, `326-333`, `443-452`, `597-604`.
- Collected links become `LocalScraperResult(title=name, name=source, url, quality, type hls/dash, headers incl. Referer, provider)` at `ExternalExtensionRunner.kt:849-872`.
- **Collected subtitles are discarded** in every path (`subtitles` list exists but never returned): `ExternalExtensionRunner.kt:172-198`, `324-334`, `437-471`, `595-624`.
- `loadExtractor` bridge supports subtitle callbacks from extractors, but again only inside local callback and dropped by runner at `app/src/full/java/com/nuvio/tv/core/plugin/cloudstream/ExternalExtractorRegistry.kt:44-74`.

### 3.5 Active resolved URL + headers (playback-side)
- Host keeps active playback URL/headers in `PlayerRuntimeController.currentStreamUrl/currentHeaders` with getters at `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeController.kt:196-216`.
- HTTP stream switching sets them from `Stream.getStreamUrl()` + `behaviorHints.proxyHeaders.request` at `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerStreams.kt:572-590`, `790-808`, `1335-1361`.
- **Extension code is never invoked after selection** to receive that resolved URL/headers:
  - Stremio stream/subtitle calls happen pre-selection (`StreamRepositoryImpl.kt:563-590`, `SubtitleRepositoryImpl.kt:36-126`).
  - JS `getStreams` inputs are only tmdbId/mediaType/season/episode (`PluginRuntime.kt:129-150`).
  - DEX `loadLinks` inputs are `data` only, not player URL (`ExternalExtensionRunner.kt:179-186`, `326-333`, `443-452`, `597-604`).
- Torrent playback changes `currentStreamUrl` to local TorrServer URL and clears headers at `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerStartup.kt:47-63`, `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerTorrent.kt:174-190`; original addon request headers are not preserved.

## 4. Stream/subtitle collection and UI

### 4.1 Stream collection
- All addon sources are combined in `StreamRepositoryImpl.getStreamsFromAllAddons` at `app/src/main/java/com/nuvio/tv/data/repository/StreamRepositoryImpl.kt:93-127`, `156-315`.
- Plugins stream results as `(ScraperInfo, results)` into same `AddonStreams` list at `StreamRepositoryImpl.kt:429-491`, `app/src/full/java/com/nuvio/tv/core/plugin/PluginManager.kt:698-760`.
- Conversion to playback info carries URL and request headers only at `app/src/main/java/com/nuvio/tv/ui/screens/stream/StreamScreenViewModel.kt:1305-1342`; `StreamPlaybackInfo.headers` at `1818-1847`.
- Player creates media source with `subtitleConfigurations` only; no external audio merge path in main player factory at `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerMediaSourceFactory.kt:82-207`.
- External audio is currently only used for trailer playback via `MergingMediaSource` at `app/src/main/java/com/nuvio/tv/ui/components/TrailerPlayer.kt:112-114`, `188-190` , proving a local precedent but not wired to main playback.

### 4.2 Subtitle collection and UI
- Subtitle repository fans out to all subtitle-capable addons, per-addon 20s timeout, progress callback and emitted snapshot callback at `app/src/main/java/com/nuvio/tv/data/repository/SubtitleRepositoryImpl.kt:33-126`.
- Domain `Subtitle(id,url,lang,addonName,addonLogo)` at `app/src/main/java/com/nuvio/tv/domain/model/Subtitle.kt:6-18`.
- Player UI state exposes `addonSubtitles`, `isLoadingAddonSubtitles`, `selectedAddonSubtitle` at `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerUiState.kt:120-126`.
- Fetch is invoked after playback state via `fetchAddonSubtitles()` at `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerObservers.kt:40-153`; startup prep currently returns empty subtitles (`PlayerRuntimeControllerInitialization.kt:1900-1906`).
- Rendering paths exist:
  - Exo hot sidecar parses downloaded subtitle without buffer reset at `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerSidecarSubtitles.kt:22-52`, `98-180`.
  - Media-source subtitle configurations at `PlayerMediaSourceFactory.kt:82-112`, `188-197`.
  - MPV external subtitle add at `app/src/main/java/com/nuvio/tv/ui/screens/player/PlayerRuntimeControllerTrackSelection.kt:435-462`.
  - External player subtitle intents at `app/src/main/java/com/nuvio/tv/core/player/ExternalPlayerLauncher.kt:59-90`, `app/src/main/java/com/nuvio/tv/core/player/ExternalPlayerResultContract.kt:98-132`.
- Selection overlay accepts `addonSubtitles` at `app/src/main/java/com/nuvio/tv/ui/screens/player/SubtitleSelectionOverlay.kt:94-123`, addon section at `1682-1795`.

## 5. Security model

- App permits cleartext traffic globally and sets network security config allowing cleartext at `app/src/main/AndroidManifest.xml:42-43`, `app/src/main/res/xml/network_security_config.xml:1-8`.
- Main OkHttp client trusts all certs and disables hostname verification at `app/src/main/java/com/nuvio/tv/core/di/NetworkModule.kt:103-116`. This affects addon HTTP calls using shared client.
- JS plugins have unrestricted network egress through `__native_fetch` and can read `TMDB_API_KEY` at `app/src/full/java/com/nuvio/tv/core/plugin/PluginRuntime.kt:286-303`, `451-452`, `676-744`.
- JS download size cap is 5 MB and content is logged by hash, but there is **no signature/pinning/allowlist** verification at `app/src/full/java/com/nuvio/tv/core/plugin/PluginManager.kt:52-61`, `964-1045`.
- DEX is arbitrary Kotlin loaded with `DexClassLoader`; max 10 MB, read-only enforcement, but **no code signing or sandbox boundary** beyond Android app process permissions at `app/src/full/java/com/nuvio/tv/core/plugin/cloudstream/ExternalExtensionLoader.kt:165-195`, `205-260`, `276-327`.
- Cloudstream runtime installs Conscrypt, a trust-all client (`ignoreAllSSLErrors`) and cookie jar at `app/src/full/java/com/nuvio/tv/core/runtime/PluginRuntimeHooks.kt:38-64`.
- Web config servers are local NanoHTTPD with no auth token; changes require TV confirmation but endpoint is LAN-accessible while active at `app/src/main/java/com/nuvio/tv/core/server/AddonConfigServer.kt:29-54`, `137-184`, `app/src/main/java/com/nuvio/tv/core/server/RepositoryConfigServer.kt:29-54`, `75-108`.

## 6. Capability matrix (commit eca648a8)

Legend: ✅ supported, ⚠️ partial/blocked, ❌ not supported.

| Capability | Stremio HTTP addon | Nuvio native JS plugin | CloudStream DEX extension |
|---|---|---|---|
| Install/config/profile storage | ✅ profile-scoped URLs/enabled/order/name (`AddonPreferences.kt:28-56`, `66-84`); query-URL config persisted (`AddonRepositoryImpl.kt:60-75`) | ✅ profile-scoped repos/scrapers/settings (`PluginDataStore.kt:36-64`, `224-257`) | ✅ profile-scoped repo/scraper state (`PluginManager.kt:1068-1119`); extension key/value store partial |
| Manifest/resources declared | ✅ manifest resources/types/idPrefixes (`AddonManifestDto.kt:8-24`, `AddonMapper.kt:52-75`) | ✅ manifest scrapers metadata (`Plugin.kt:39-69`) | ✅ repo manifest + plugin entries (`Plugin.kt:119-146`) |
| Runtime sandbox/API | n/a (server-side HTTP) | QuickJS no DOM; fetch/cheerio/crypto/settings/TMDB key (`PluginRuntime.kt:270-744`) | DEX in-process Kotlin; CS MainAPI/Extractor (`Plugin.kt:33-65`, `ExternalExtensionLoader.kt:276-327`) |
| Content/stream context received | ✅ type/id/videoId + optional videoHash/size/filename (`SubtitleRepositoryImpl.kt:156-242`) | ✅ tmdbId/mediaType/season/episode (`PluginRuntime.kt:129-150`) | ✅ production `TmdbProvider` JSON/load context or search-provider TMDB metadata path (`ExternalExtensionRunner.kt:345-457`, `474-610`); diagnostics-only `TmdbLink` context is separate (`:138-168`) |
| Can output stream URL | ✅ `StreamDto.url` (`StreamResponseDto.kt:16`) | ✅ `LocalScraperResult.url` (`Plugin.kt:100-117`) | ✅ `ExtractorLink.url` → result (`ExternalExtensionRunner.kt:849-872`) |
| Can output request headers | ✅ `behaviorHints.proxyHeaders.request` (`StreamResponseDto.kt:92-106`, `StreamMapper.kt:97-121`) | ✅ `headers` field (`Plugin.kt:100-117`, `PluginRuntime.kt:1344-1369`) | ✅ `ExtractorLink.headers/referer` (`ExternalExtensionRunner.kt:849-872`) |
| Can output subtitles (collected by host) | ✅ via dedicated `/subtitles` resource (`SubtitleRepositoryImpl.kt:36-126`, `196-204`) | ❌ no subtitle output schema (`PluginRuntime.kt:1331-1374`) | ⚠️ provider can emit `SubtitleFile`, but host drops them (`ExternalExtensionRunner.kt:172-198`, `595-624`) |
| Can request async dubbing | ⚠️ only custom URL hack, no host contract/action (no `dub/audio` resource handling in `AddonApi.kt:13-28`) | ⚠️ plugin itself can POST to AI APIs, but cannot hand result to player (`PluginRuntime.kt:129-150`, `1331-1374`) | ⚠️ extension can call AI APIs, but no output channel after `loadLinks` (`ExternalExtensionRunner.kt:179-186`, `849-872`) |
| Access active resolved URL+headers | ❌ addon only knows requested ids (`StreamRepositoryImpl.kt:563-590`) | ❌ no post-selection callback (`PluginRuntime.kt:129-150`) | ❌ no post-selection callback (`ExternalExtensionRunner.kt:179-186`) |
| Return external audio | ❌ no audio field in `Stream`/`StreamDto` (`StreamResponseDto.kt:12-24`, `Stream.kt:10-30`) | ❌ no audio field in `LocalScraperResult` (`Plugin.kt:100-117`) | ❌ `ExtractorLink`→`LocalScraperResult` drops audio/extra fields (`ExternalExtensionRunner.kt:849-872`) |
| Return generated subtitles | ✅ ready subtitle URL via subtitles resource (`SubtitleRepositoryImpl.kt:190-204`) | ❌ unless plugin starts its own HTTP server and returns URL as stream hack (not supported UI) | ⚠️ technically emits `SubtitleFile`, discarded |
| Report progress | ✅ addon-level subtitle fetch progress only (`SubtitleRepositoryImpl.kt:43-44`, `71-105`) | ❌ only logs/console (`PluginRuntime.kt:270-285`) | ❌ diagnostics only for test (`TestDiagnostics.kt:6-18`) |
| Hold provider config | ⚠️ URL query/configured base URL; no in-app `/configure` UI (`AddonManagerViewModel.kt:190-207`) | ✅ `SCRAPER_SETTINGS` storage exists, but no UI writes it (`PluginDataStore.kt:224-257`, `PluginUiState.kt:45-63`) | ⚠️ `DataStore` prefs persist, `openSettings` unused; Acra/CloudStreamApp keys mostly no-op (`DataStore.kt:13-129`, `Plugin.kt:24`) |
| Flavor availability | ✅ both flavors | ✅ full only; playstore stub (`app/build.gradle.kts:152-173`, `app/src/playstore/java/com/nuvio/tv/core/plugin/PluginManager.kt:13-74`) | ✅ full only; playstore stub |

## 7. What works without host changes

1. **AI subtitles as ready URLs** , works today:
   - Build a Stremio addon exposing `resources: ["subtitles"]`; Nuvio will call `/subtitles/...` and collect `{id,url,lang}` (`AddonMapper.kt:52-75`, `SubtitleRepositoryImpl.kt:59-126`, `190-204`).
   - The player will render SRT/VTT/TTML via sidecar without buffer reset (`PlayerSidecarSubtitles.kt:22-52`, `98-180`) and ASS via libass/media path (`PlayerRuntimeControllerTrackSelection.kt:500-570`).
   - Caveat: async generation must complete before returning the subtitle response (20s per-addon timeout) (`SubtitleRepositoryImpl.kt:33-35`, `83-113`). No polling/progress contract exists.
2. **AI provider orchestration inside plugin/extension** , works only if output is a playable URL:
   - JS plugin/DEX can call any AI backend (native fetch / OkHttp), but final output must be a normal stream URL (`PluginRuntime.kt:692-744`, `1331-1374`; `ExternalExtensionRunner.kt:849-872`).
3. **Config for JS plugins** , can be set programmatically (sync or future code) because `setScraperSettings` exists (`PluginDataStore.kt:239-257`), but users cannot edit in UI.

## 8. Minimal host changes for AI subtitles and AI dubbing

### 8.1 Minimal AI subtitles (highest ROI, smallest diff)
1. **Do not drop subtitles already returned in streams**:
   - Add `subtitles: List<Subtitle>?` to domain `Stream` (`Stream.kt:10-30`).
   - Map `StreamDto.subtitles` in `StreamMapper.toDomain` (`StreamResponseDto.kt:23`, `StreamMapper.kt:18-34`).
   - Merge them into `PlayerUiState.addonSubtitles` on selection (`PlayerUiState.kt:120-126`, `StreamScreenViewModel.kt:1305-1342`).
2. **Do not drop DEX subtitles**:
   - Extend `LocalScraperResult` with `subtitles: List<LocalSubtitleResult>` (`Plugin.kt:100-117`).
   - In `ExternalExtensionRunner`, map `SubtitleFile` from `subtitleCallback` into result metadata instead of only logging count (`ExternalExtensionRunner.kt:172-198`, `595-624`).
   - Inject into same player subtitle pool (`PlayerRuntimeControllerObservers.kt:95-153`).
3. **Async subtitle job contract (optional but needed for slow AI)**: add a `subtitlesJob` resource response with `jobId/statusUrl`, host polls until ready; current contract has no async status (`AddonApi.kt:13-28`, `SubtitleRepository.kt:16-26`).

### 8.2 Minimal AI dubbing / external audio
No existing path. Minimal viable host change:
1. Extend stream contract with external audio tracks:
   - `StreamDto` + domain `Stream` add `audioTracks: [{id,url,lang,name?,headers?}]` (`StreamResponseDto.kt:12-24`, `Stream.kt:10-30`).
   - `LocalScraperResult` adds same for plugins/DEX (`Plugin.kt:100-117`, `ExternalExtensionRunner.kt:849-872`).
2. Playback merge:
   - Exo: create audio `MediaSource` per external track and `MergingMediaSource(video, audio)` in `PlayerMediaSourceFactory.createMediaSource` (`PlayerMediaSourceFactory.kt:82-207`), reusing trailer precedent (`TrailerPlayer.kt:112-114`).
   - MPV: use `audio-add`-style external track API in `NuvioMpvSurfaceView`/MPV controller (`NuvioMpvSurfaceView.kt:42-120`, `PlayerRuntimeControllerMpv.kt:20-130`).
   - External player: pass audio URL extras only for players that support it; otherwise reject (`ExternalPlayerLauncher.kt:59-90`).
3. Async dub job:
   - Add a new extension action, e.g. Stremio resource `dub` (`/dub/{type}/{id}.json`) and plugin export `requestDubbing(...)`; return `{jobId,statusUrl}` then final `{audioTracks:[...]}`.
   - Expose progress into existing loading/progress UI (`PlayerUiState.kt:83-88`, `StreamScreenViewModel.kt:228-240`, `1440-1475`).

### 8.3 Minimal post-selection context access
Add one host callback to providers after stream selection:
- Input: `{videoId,type,season,episode,resolvedUrl,requestHeaders,contentId,videoHash,videoSize,filename}`.
- For HTTP addons: optional endpoint `/process/...` or `/dub/...`.
- For JS: exported `processSelectedStream(ctx)`.
- For DEX: optional interface implemented by provider.
Rationale: today no extension receives `currentStreamUrl/currentHeaders` (`PlayerRuntimeController.kt:196-216`, `PlayerRuntimeControllerStreams.kt:572-590`).

## 9. Candidate contract schemas

### 9.1 Async AI job envelope (shared)
```json
{
  "job": {
    "id": "aijob_123",
    "kind": "dub|subtitles",
    "status": "queued|running|ready|failed",
    "progress": { "percent": 42, "stage": "transcribe|translate|tts|mux", "message": "Dubbing audio" },
    "etaSeconds": 90,
    "statusUrl": "https://provider.example/jobs/aijob_123",
    "resultUrl": "https://provider.example/jobs/aijob_123/result.json"
  }
}
```

### 9.2 Generated subtitles result
```json
{
  "subtitles": [
    {
      "id": "ai-es-generated",
      "lang": "es",
      "url": "https://provider.example/subtitles/aijob_123/es.srt",
      "generated": true,
      "sourceLang": "en",
      "headers": { "Authorization": "Bearer ..." }
    }
  ]
}
```
Host mapping target: existing `Subtitle(id,url,lang,addonName,addonLogo)` (`Subtitle.kt:6-18`), optionally headers-aware sidecar (`PlayerRuntimeControllerSubtitleTiming.kt:203-263`).

### 9.3 External audio / dub result
```json
{
  "audioTracks": [
    {
      "id": "dub-es",
      "lang": "es",
      "name": "AI Spanish Dub",
      "url": "https://cdn.example/aijob_123/es.aac",
      "mimeType": "audio/aac",
      "headers": { "Referer": "https://provider.example/" },
      "default": true,
      "generated": true
    }
  ],
  "video": { "muteOriginal": false }
}
```
Host mapping target: new `Stream.audioTracks`, merged in `PlayerMediaSourceFactory` (`PlayerMediaSourceFactory.kt:82-207`).

### 9.4 Selected-stream context
```json
{
  "content": { "type": "series", "id": "tt1234567", "videoId": "tt1234567:1:2", "season": 1, "episode": 2 },
  "stream": {
    "url": "https://resolved.example/video.m3u8",
    "headers": { "User-Agent": "...", "Referer": "..." },
    "videoHash": "abc",
    "videoSize": 734003200,
    "filename": "Show.S01E02.1080p.mkv"
  },
  "request": { "targetLang": "es", "voice": "narrator_female", "mode": "dub|subs" }
}
```

### 9.5 Capability advertisement (manifest-level)
- Stremio manifest extension:
```json
{
  "resources": ["catalog","meta","stream","subtitles", {"name":"dub","types":["movie","series"]}],
  "behaviorHints": {"configurable": true},
  "aiCapabilities": {"dubbing": true, "generatedSubtitles": true, "asyncJobs": true}
}
```
- Nuvio plugin manifest (`PluginManifest.scrapers`) extension:
```json
{ "id":"ai-dubber", "capabilities": ["dubbing","generatedSubtitles","asyncJobs","selectedStreamContext"] }
```
- DEX: optional interface marker, since repo format already lacks capability fields (`ExternalPluginEntry` at `Plugin.kt:130-146`).

## 10. Open questions / gaps

1. **Stremio per-stream `subtitles` field semantics**: Nuvio parses but ignores it (`StreamResponseDto.kt:23`, `StreamMapper.kt:18-34`); need upstream Stremio contract confirmation before mapping into UI.
2. **Addon configure flow**: manifest behaviorHints parsed but no `/configure` UI found; confirm intended product behavior (`Addon.kt:68-72`, `AddonManagerViewModel.kt:129-207`).
3. **Provider config UX**: JS `SCRAPER_SETTINGS` and DEX `DataStore` exist but no settings screen; decide whether to build generic schema-driven settings UI or keep URL-only config (`PluginDataStore.kt:224-257`, `DataStore.kt:13-129`).
4. **Security posture**: current network stack trusts all certs and cleartext globally (`NetworkModule.kt:103-116`, `network_security_config.xml:1-8`); exposing resolved headers to extensions would widen token leakage risk and needs an explicit opt-in permission model.
5. **Torrent path**: active URL becomes local TorrServer with headers cleared (`PlayerRuntimeControllerStartup.kt:47-63`, `PlayerRuntimeControllerTorrent.kt:174-190`); AI audio/subtitle contracts must define whether original addon headers or local playback headers are authoritative.
6. **External players**: subtitle forwarding exists; external audio forwarding is player-specific and not standardized (`ExternalPlayerLauncher.kt:59-90`).

Report written at `docs/ai-media-research/lanes/04-nuvio-extension-ecosystem.md`. No source or git changes made.
