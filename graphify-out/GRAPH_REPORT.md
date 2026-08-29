# Graph Report - /tmp/nuvio-graphify-scope  (2026-08-29)

## Corpus Check
- 354 files · ~391,860 words
- Verdict: corpus is large enough that graph structure adds value.

## Summary
- 5488 nodes · 14267 edges · 297 communities (243 shown, 54 thin omitted)
- Extraction: 100% EXTRACTED · 0% INFERRED · 0% AMBIGUOUS · INFERRED: 14 edges (avg confidence: 0.8)
- Token cost: 1,502 input · 1,356 output

## Community Hubs (Navigation)
- Community 0
- Community 1
- Engine Failover Tracks
- Player Settings Storage
- Player UI State
- Community 5
- Community 6
- Community 7
- Community 8
- Community 9
- Audio and Subtitle Preferences
- Community 11
- Community 12
- Stream Mapping and Runtime
- Community 14
- Sidecar Subtitle Rendering
- Community 16
- Community 17
- Community 18
- Community 19
- Community 20
- Community 21
- Community 22
- Subtitle Selection UX
- MPV Runtime
- Playback Audio Controls
- Community 26
- Plugin Management
- Community 28
- Community 29
- Stream Repository
- Community 31
- Community 32
- Audio Selection UX
- Media Source Factory
- Community 35
- Community 36
- Stream Domain Model
- Player Overlay Styling
- Community 39
- Settings Design System
- Community 41
- Subtitle Fetch Runtime
- Plugin Data Storage
- Community 44
- Community 45
- Community 46
- Community 47
- Audio Route Detection
- MPV Surface Bridge
- Player Initialization
- Community 51
- Community 52
- Community 53
- Community 54
- Community 55
- Community 56
- Audio Sink Processing
- Community 58
- Addon Config Server
- Community 60
- Community 61
- Community 62
- Community 63
- Community 64
- Community 65
- Community 66
- Player Runtime Core
- Plugin Settings UX
- Addon Repository
- Community 70
- Community 71
- Community 72
- Community 73
- JavaScript Plugin Runtime
- Community 75
- Community 76
- Community 77
- Community 78
- Community 79
- Community 80
- Community 81
- DEX Extension Loading
- Community 83
- Community 84
- Community 85
- Community 86
- Community 87
- Community 88
- Community 89
- Community 90
- Subtitle Media Integration
- Community 92
- Community 93
- Plugin Screen
- Settings Navigation
- Community 96
- Community 97
- Community 98
- Community 99
- Community 100
- Community 101
- Community 102
- Addon Preferences
- Community 104
- Community 105
- Community 106
- Community 107
- Community 108
- Community 109
- Community 110
- Community 111
- Audio and Subtitle Renderers
- Community 113
- Community 114
- Subtitle Hashing
- Community 116
- Community 117
- Subtitle Repository
- Community 119
- Community 120
- Subtitle Decoding
- Community 122
- Community 123
- Community 124
- Community 125
- Community 126
- Community 127
- Community 128
- Community 129
- Community 130
- Plugin Domain Contract
- Community 132
- Community 133
- Community 134
- Community 135
- Community 136
- Community 137
- Community 138
- Community 139
- Community 140
- Community 141
- PCM Gain Processor
- Subtitle Timing Runtime
- Subtitle Timing UX
- Community 145
- Community 146
- Community 147
- Community 148
- Community 149
- Community 150
- Community 151
- Runtime Track State
- Community 153
- Playback Audio Settings
- Community 155
- Community 156
- Community 157
- Community 158
- Community 159
- Community 160
- Community 161
- Community 162
- Community 163
- Community 164
- Community 165
- Community 166
- Community 167
- Community 168
- Community 169
- Stream Response DTO
- Community 171
- Community 172
- Community 173
- Community 174
- Community 175
- Community 176
- Community 177
- Community 178
- Community 179
- Subtitle Utilities
- Community 181
- Community 182
- Community 183
- Community 184
- Community 185
- Community 186
- Community 187
- Community 188
- Community 189
- Community 190
- Community 191
- Community 192
- Community 193
- Addon Web Configuration
- Community 195
- Community 196
- Community 197
- Addon Domain Model
- Community 199
- Audio Control Focus
- Community 201
- Playback Settings Sections
- Community 203
- Community 204
- Community 205
- Community 206
- Community 207
- Community 208
- Community 209
- Community 210
- Community 211
- Community 212
- Playback Subtitle Settings
- Community 214
- Community 215
- Community 216
- Audio Delay Persistence
- Community 218
- Community 219
- Community 220
- Community 221
- Community 222
- Community 223
- Community 224
- Community 225
- Community 226
- Community 228
- Community 229
- Community 230
- Community 231
- Community 232
- Community 233
- Community 234
- Community 235
- Community 236
- Community 237
- Addon Manifest DTO
- Community 239
- Community 240
- Community 241
- Community 242
- Community 243
- Community 244
- Community 245
- Community 246
- Community 247
- Community 248
- Community 249
- Community 250
- Community 251
- Community 252
- Community 253
- Community 254
- Community 255
- Community 256
- Community 257
- Community 258
- Community 259
- Community 260
- Community 261
- Community 262
- Community 263
- Community 264
- Community 265
- Community 266
- Community 267
- Community 268
- Media Session Metadata
- Player Navigation Arguments
- Community 271
- Community 272
- Subtitle Domain Model
- Community 274
- Community 275
- Community 276
- Subtitle Response DTO
- Community 278
- Community 279
- Community 280
- Community 281
- Community 282
- Community 284
- Community 285
- Community 286
- Community 287
- Community 288
- Community 289
- Community 290
- Community 291
- Community 292

## God Nodes (most connected - your core abstractions)
1. `PlayerSettingsDataStore` - 111 edges
2. `PlaybackSettingsViewModel` - 100 edges
3. `TraktProgressService` - 98 edges
4. `LayoutPreferenceDataStore` - 79 edges
5. `PlayerEvent` - 73 edges
6. `MatroskaExtractor` - 67 edges
7. `LayoutSettingsViewModel` - 62 edges
8. `PlayerRuntimeController` - 60 edges
9. `WatchProgressRepositoryImpl` - 57 edges
10. `FrameRateUtils` - 56 edges

## Surprising Connections (you probably didn't know these)
- `create_session` --calls--> `get_db`  [EXTRACTED]
  src/auth/session.py → src/db/connection.py
- `validate_token` --calls--> `verify_hash`  [EXTRACTED]
  src/auth/session.py → src/utils/crypto.py

## Import Cycles
- None detected.

## Hyperedges (group relationships)
- **Authentication Session Management Flow** — src_auth_session_sessionmanager, src_auth_session_create_session, src_auth_session_validate_token [INFERRED 0.90]
- **Authentication Session Management Flow** — src_auth_session_sessionmanager, src_auth_session_create_session, src_auth_session_validate_token [INFERRED 0.90]

## Communities (297 total, 54 thin omitted)

### Community 0 - "Community 0"
Cohesion: 0.05
Nodes (19): ContentMetadata, EpisodeHistoryAddAttempt, EpisodeMetadata, EpisodeProgressCacheEntry, EpisodeProgressFetchResult, OptimisticProgressEntry, TimedCache, TraktCachedStats (+11 more)

### Community 1 - "Community 1"
Cohesion: 0.03
Nodes (18): ExternalAutoNextPolicy, AppOnboardingDataStore, SentrySettingsDataStore, isPlaceholder(), isPublicServerHost(), ServerCapabilities, ServerConfiguration, TraktCommentReview (+10 more)

### Community 2 - "Engine Failover Tracks"
Cohesion: 0.06
Nodes (86): buildRememberedInternalSubtitleSelectionForEngineSwitch(), captureCurrentAudioSelectionForEngineSwitch(), captureCurrentSubtitleSelectionForEngineSwitch(), describeRememberedSubtitleForLog(), describeRememberedTrackForLog(), findAddonSubtitleByTrackIdOrLanguage(), isStartupPhaseForEngineFailover(), maybeAutoSwitchInternalPlayerOnStartupError() (+78 more)

### Community 3 - "Player Settings Storage"
Cohesion: 0.04
Nodes (4): applyLegacyTimeoutSentinelMigration(), PlayerSettingsDataStore, ResolvedSubtitlePreferredLanguage, LastPlaybackDiagnostics

### Community 4 - "Player UI State"
Cohesion: 0.05
Nodes (80): DisplayModeInfo, OnAdjustSubtitleDelay, OnApplySubtitleAutoSyncCue, OnBackFromEpisodeStreams, OnCaptureSubtitleAutoSyncTime, OnCommitPreviewSeek, OnDisableSubtitles, OnDismissEpisodesPanel (+72 more)

### Community 5 - "Community 5"
Cohesion: 0.06
Nodes (28): CollectionsDataStore, SerializableCatalogSource, SerializableCollection, SerializableFolder, SerializableSource, SerializableTmdbFilters, ValidationResult, LibraryPreferences (+20 more)

### Community 6 - "Community 6"
Cohesion: 0.06
Nodes (20): LibraryRepositoryImpl, PersonalListFetchResult, Snapshot, TraktLibraryService, TraktTrackingLibraryProvider, LibraryEntry, LibraryEntryInput, LibraryListTab (+12 more)

### Community 7 - "Community 7"
Cohesion: 0.04
Nodes (21): DolbyVisionConversionConfig, DolbyVisionConversionStats, NativeOptimizedVideoTrackOutput, parseDvProfile(), parseNalLengthFieldLength(), rewriteDvCodecString(), stripDvCodecString(), skipStep() (+13 more)

### Community 8 - "Community 8"
Cohesion: 0.09
Nodes (33): AnalyticsListener, bufferedPositionMs(), compactTraceValue(), dataTypeName(), decoderReuseResultName(), details(), findHttpStatus(), flushPendingPlaybackRawEventLines() (+25 more)

### Community 9 - "Community 9"
Cohesion: 0.06
Nodes (32): fromDomain(), ProfileDataStore, ProfileJson, ThemeDataStore, Catalog, Custom, ProfileBackgroundSelection, resolveProfileBackgroundSelection() (+24 more)

### Community 10 - "Audio and Subtitle Preferences"
Cohesion: 0.03
Nodes (56): AudioLanguageOption, AudioOutputChannels, CHANNELS_2_0, CHANNELS_2_1, CHANNELS_3_0, CHANNELS_3_1, CHANNELS_4_0, CHANNELS_4_1 (+48 more)

### Community 11 - "Community 11"
Cohesion: 0.06
Nodes (52): LegacyStreamBadgeRules, StreamBadgeSettingsDataStore, LayoutSettingsEvent, LayoutSettingsUiState, ResetCardDepthStyle, ResetPosterCardStyle, SelectLayout, SetBlurContinueWatchingNextUp (+44 more)

### Community 12 - "Community 12"
Cohesion: 0.07
Nodes (12): ContentMetadata, EpisodeMetadata, RemoteProgressWrite, RemoteProgressWriteDeduplicator, RemoteProgressWriteKey, resolveProviderEpisodeProgress(), WatchedItemSyncKey, WatchProgressRepositoryImpl (+4 more)

### Community 13 - "Stream Mapping and Runtime"
Cohesion: 0.08
Nodes (57): parseEpisodeRuntimeMinutes(), toDomain(), sanitizeHeaderMap(), toDomain(), applySelectedStreamState(), applyStreamMetadata(), buildEpisodeRequestKey(), buildSourceRequestKey() (+49 more)

### Community 14 - "Community 14"
Cohesion: 0.06
Nodes (21): toNuvioType(), tvTypeFromString(), toPluginResponseHeaders(), truncateString(), MemberCatalogStorage, shouldRetainTraktLocalProgress(), TraktTrackingProgressProvider, DebridSettings (+13 more)

### Community 15 - "Sidecar Subtitle Rendering"
Cohesion: 0.07
Nodes (30): CueNormalizingTextOutput, activeCueSignature(), bindExoSubtitleView(), canAttachAddonSubtitleViaSidecar(), collectActiveSidecarCues(), isSidecarAddonSubtitleActive(), normalizeSidecarCuePosition(), normalizeTimedCuePositions() (+22 more)

### Community 16 - "Community 16"
Cohesion: 0.05
Nodes (25): Allocator, isRecentCompletedPlaybackSeed(), mergeTrackingNextUpSeeds(), shouldReplaceTrackingSeed(), shouldUseTraktNextUpSeed(), trackingNextUpKey(), traktContinueWatchingCutoffEpochMs(), AudioDelayMediaPeriod (+17 more)

### Community 17 - "Community 17"
Cohesion: 0.09
Nodes (11): ExternalAutoNextEpisode, ExternalAutoNextOverlay, ExternalNextEpisodeSnapshot, ExternalPlaybackMetadata, ExternalPlaybackTracker, resolveExternalNextEpisodeSnapshot(), ExternalPlayerLauncher, SubtitleFileCache (+3 more)

### Community 18 - "Community 18"
Cohesion: 0.06
Nodes (4): CatalogInfo, LayoutSettingsViewModel, CardDepthSurface, StreamBadgeConfigServer

### Community 19 - "Community 19"
Cohesion: 0.07
Nodes (51): DeviceLocalPlayerPreferences, applyExoAspectMode(), applySubtitleStyleIfNeeded(), AspectRatioIndicator(), ControlButton(), DialogButton(), enableComposeSurfaceSyncWorkaroundIfAvailable(), ErrorOverlay() (+43 more)

### Community 20 - "Community 20"
Cohesion: 0.10
Nodes (24): BootstrapCacheEntry, ChunkSession, clearGlobalPool(), DownloadedChunk, enforceSessionCap(), evictFuture(), Factory, freeDirectBuffer() (+16 more)

### Community 21 - "Community 21"
Cohesion: 0.13
Nodes (7): ProgressMapCache, ProgressSnapshot, WatchProgressPreferences, DataStore, JsonElement, WatchProgress, WatchProgressBuckets

### Community 23 - "Subtitle Selection UX"
Cohesion: 0.09
Nodes (46): AddonFilterChips(), StreamItem(), addonSubtitleOptionId(), buildSubtitleLanguageRailItems(), buildSubtitleOptionRailItems(), ColorChip(), ColorChipRow(), CountBadge() (+38 more)

### Community 24 - "MPV Runtime"
Cohesion: 0.06
Nodes (23): applyMpvTrackSnapshot(), applyPendingMpvSeekIfNeeded(), attachMpvView(), currentPlaybackDurationMs(), currentPlaybackPositionMs(), hasActivePlayIntent(), initializeMpvPlayer(), isPlaybackCurrentlyPlaying() (+15 more)

### Community 25 - "Playback Audio Controls"
Cohesion: 0.10
Nodes (41): adjustSubtitleDelay(), applyAudioAmplification(), applyAudioDelay(), applyCenterMixLevel(), buildPlaybackIssuePlaybackSettingsInput(), buildStreamInfoData(), cancelPauseOverlay(), decoderPriorityReportName() (+33 more)

### Community 26 - "Community 26"
Cohesion: 0.11
Nodes (5): MatroskaExtractor, CallSuper, EnsuresNonNull, MatroskaZlibSampleDecompressor, PositionHolder

### Community 27 - "Plugin Management"
Cohesion: 0.12
Nodes (9): PluginManager, CoroutineDispatcher, PluginManifest, PluginRepository, RemotePluginInfo, RepositoryType, Result, ScraperInfo (+1 more)

### Community 28 - "Community 28"
Cohesion: 0.07
Nodes (7): ChunkIndexProvider, DolbyVisionCompatibility, DtsUtil, ThumbnailMetadata, Entry, Nullable, UnstableApi

### Community 29 - "Community 29"
Cohesion: 0.06
Nodes (39): AddonCatalogCollectionSource, Collection, CollectionCatalogSource, CollectionFolder, CollectionSource, normalize(), TmdbCollectionFilters, TmdbCollectionMediaType (+31 more)

### Community 30 - "Stream Repository"
Cohesion: 0.12
Nodes (10): AddonStreams, PluginRequest, StreamAttemptFailure, StreamRepositoryImpl, StreamSourceConfigurationSnapshot, Channel, DebridSettings, MutableList (+2 more)

### Community 31 - "Community 31"
Cohesion: 0.12
Nodes (8): androidx, DebridSettingsDataStore, DebridStreamCodecFilter, DebridStreamFeatureFilter, DebridStreamMinimumQuality, DebridStreamPreferences, DebridStreamResolution, DebridStreamSortMode

### Community 32 - "Community 32"
Cohesion: 0.10
Nodes (32): CardDepthFineTuneDialog(), CardDepthPreview(), CardDepthResetButton(), CardDepthStyleControls(), CatalogChip(), CollapsibleSectionCard(), CompactToggleRow(), ContinueWatchingSortModeDialog() (+24 more)

### Community 33 - "Audio Selection UX"
Cohesion: 0.10
Nodes (28): AnimeSkipSettingsViewModel, AdjustmentSection(), AudioControlsContent(), AudioSelectionOverlay(), AudioTrackCard(), AudioTracksContent(), formatAudioDelay(), StepCard() (+20 more)

### Community 34 - "Media Source Factory"
Cohesion: 0.11
Nodes (33): buildMimeProbeCacheKey(), cacheProbeInfo(), decodeRawUserInfo(), extractUserInfoAuth(), findCause(), getAnySimpleCache(), getProbeInfo(), getReadySimpleCache() (+25 more)

### Community 35 - "Community 35"
Cohesion: 0.12
Nodes (35): DebridApiKeyDialog(), DebridDeviceAuthCodes(), DebridDeviceAuthDialog(), DebridInfoText(), DebridMaxResultsDialog(), DebridMultiChoiceDialog(), DebridPrepareCountDialog(), DebridResolverProviderDialog() (+27 more)

### Community 36 - "Community 36"
Cohesion: 0.13
Nodes (5): DolbyVisionMatroskaTransformer, HevcHdr10PlusStripper, initialValue(), ByteArray, ByteArrayOutputStream

### Community 37 - "Stream Domain Model"
Cohesion: 0.08
Nodes (16): AddonStreams, isMagnetLink(), ProxyHeaders, Stream, StreamBadge, StreamBehaviorHints, StreamClientResolve, StreamClientResolveParsed (+8 more)

### Community 38 - "Player Overlay Styling"
Cohesion: 0.10
Nodes (31): PlayerOverlayScaffold(), SubtitleStyleColorChip(), SubtitleStyleSection(), SubtitleStyleSettingRow(), SubtitleStyleSidePanel(), SubtitleStyleStepperButton(), SubtitleStyleToggleButton(), SubtitleStyleValueDisplay() (+23 more)

### Community 39 - "Community 39"
Cohesion: 0.13
Nodes (10): PlaybackConnectionEventListener, PlaybackConnectionEvents, Call, Connection, EventListener, Handshake, InetAddress, InetSocketAddress (+2 more)

### Community 40 - "Settings Design System"
Cohesion: 0.15
Nodes (30): Alignment, ParentalGuideOverlay(), isFlatSettingsStyle(), rememberRawSvgPainter(), SettingsActionRow(), SettingsBrandPanel(), SettingsChoiceChip(), SettingsDetailHeader() (+22 more)

### Community 41 - "Community 41"
Cohesion: 0.10
Nodes (9): CuePointData, InnerEbmlProcessor, MatroskaSeekMap, ChunkIndex, ChunkIndexProvider, ElementType, Override, SparseArray (+1 more)

### Community 42 - "Subtitle Fetch Runtime"
Cohesion: 0.11
Nodes (26): buildSubtitleFetchRequest(), cancelFirstFrameWatchdog(), cancelStallWatchdog(), clearPendingInitialResumePosition(), fetchAddonSubtitles(), fetchSkipIntervals(), filterToVisibleAddonSubtitles(), loadCloudLibraryResumeProgress() (+18 more)

### Community 43 - "Plugin Data Storage"
Cohesion: 0.10
Nodes (11): AddonBehaviorHints, AddonResource, Any, PluginDataStore, parseCatalogExtras(), parseResources(), toDomain(), coerceStringList() (+3 more)

### Community 44 - "Community 44"
Cohesion: 0.18
Nodes (9): ExternalExtensionRunner, SearchOutcome, Episode, ExtractorLink, LoadResponse, LocalScraperResult, MainAPI, SearchResponse (+1 more)

### Community 45 - "Community 45"
Cohesion: 0.16
Nodes (7): normalizedId(), PlaybackIdentity, PlaybackSnapshot, PostPlayRecommendationController, RatingPreferences, ResolvedCandidate, PostPlayRecommendationUiState

### Community 46 - "Community 46"
Cohesion: 0.12
Nodes (5): DebridSettingsEvent, DebridSettingsUiState, DebridSettingsViewModel, ToggleEnabled, DebridFormatterConfigServer

### Community 47 - "Community 47"
Cohesion: 0.13
Nodes (7): Activity, FrameRateUtils, attachHostActivity(), currentHostActivity(), safeStartupHost(), startInitialPlaybackIfNeeded(), Display

### Community 48 - "Audio Route Detection"
Cohesion: 0.10
Nodes (16): AudioOutputRoute, AudioOutputRouteDetector, applyBluetoothAudioRouteInPlace(), applyMpvBluetoothAudioRouteInPlace(), applyStoredAudioDelayForCurrentRouteIfEnabled(), audioDelayMsToSeconds(), BluetoothRoutePlaybackAction, NONE (+8 more)

### Community 50 - "Player Initialization"
Cohesion: 0.13
Nodes (28): applyDownmixSettings(), applyMapDv7ToHevcIfSupported(), applyStartupSubtitlePreparation(), buildStartupSubtitleConfigurations(), createDolbyVisionFallbackCodecSelector(), describeExtensionRendererMode(), disposeExoPlayerBeforeRebuild(), friendlyVideoCodecName() (+20 more)

### Community 51 - "Community 51"
Cohesion: 0.07
Nodes (29): DebridStreamPicker, EXCLUDED_AUDIO_CHANNELS, EXCLUDED_AUDIO_TAGS, EXCLUDED_ENCODES, EXCLUDED_LANGUAGES, EXCLUDED_QUALITIES, EXCLUDED_RELEASE_GROUPS, EXCLUDED_RESOLUTIONS (+21 more)

### Community 52 - "Community 52"
Cohesion: 0.17
Nodes (28): CenterStatusText(), ContributorCard(), ContributorDetailsDialog(), ContributorRoleBadge(), contributorRoleLabel(), ContributorsTabContent(), ContributorSupportLink, formatSupporterDate() (+20 more)

### Community 53 - "Community 53"
Cohesion: 0.11
Nodes (8): ProfileLockStateDataStore, mergeWatchProgressBuckets(), splitWatchProgressEntries(), WatchProgressBuckets, CatalogRepositoryImpl, CatalogRepository, CatalogRow, Map

### Community 54 - "Community 54"
Cohesion: 0.16
Nodes (4): TraktAuthService, findCause(), T, TraktAuthState

### Community 55 - "Community 55"
Cohesion: 0.17
Nodes (4): WatchedItemsPreferences, shouldRetainTraktLocalWatchedEpisode(), Triple, WatchedItem

### Community 56 - "Community 56"
Cohesion: 0.07
Nodes (27): TraktHiddenItemDto, TraktHistoryAddNotFoundDto, TraktHistoryAddRequestDto, TraktHistoryAddResponseDto, TraktHistoryEpisodeAddDto, TraktHistoryEpisodeRemoveDto, TraktHistoryItemDto, TraktHistoryMovieAddDto (+19 more)

### Community 57 - "Audio Sink Processing"
Cohesion: 0.13
Nodes (8): PlaybackSpeedAwareAudioSink, CompatAssSubtitleParserFactory, AudioOffloadSupport, AudioSink, Format, ForwardingAudioSink, IntArray, PlaybackParameters

### Community 58 - "Community 58"
Cohesion: 0.12
Nodes (22): animeIdPreferenceLabel(), continueWatchingWindowLabel(), librarySourceLabel(), moreLikeThisSourceLabel(), simklConnectionPresentation(), TrackingConnectionPresentation, TrackingSettingsOverview(), TrackingSettingsScreen() (+14 more)

### Community 59 - "Addon Config Server"
Cohesion: 0.15
Nodes (11): AddonConfigServer, startOnAvailablePort(), Response, TmdbSourceMetadataInfo, TmdbSourceMetadataRequest, TmdbSourceSearchRequest, TmdbSourceSearchResultInfo, TraktSourceMetadataInfo (+3 more)

### Community 60 - "Community 60"
Cohesion: 0.08
Nodes (26): AddonChangeStatus, CONFIRMED, PENDING, REJECTED, AddonInfo, AddonWebConfigMode, ADDONS_ONLY, COLLECTIONS_ONLY (+18 more)

### Community 61 - "Community 61"
Cohesion: 0.22
Nodes (4): CachedMeta, MetaAttemptFailure, MetaRepositoryImpl, MetaRepository

### Community 62 - "Community 62"
Cohesion: 0.11
Nodes (16): PlaybackIssueErrorInput, PlaybackIssueLoadingEventInput, PlaybackIssueLoadingInput, PlaybackIssuePlaybackSettingsInput, PlaybackIssueReportInput, PlaybackIssueReportRepository, PlaybackIssueDiagnosticsDto, PlaybackIssueLoadingDto (+8 more)

### Community 63 - "Community 63"
Cohesion: 0.13
Nodes (12): ActivityResultContract, ExternalPlaybackKeepAliveService, start(), stop(), ExternalPlayerInput, ExternalPlayerResult, ExternalPlayerResultContract, SubtitleInput (+4 more)

### Community 64 - "Community 64"
Cohesion: 0.18
Nodes (4): EbmlElement, MatroskaAfrProbe, MatroskaHeadLayout, Vint

### Community 65 - "Community 65"
Cohesion: 0.11
Nodes (7): RepositoryWebPage, NuvioExoPlayerPerformanceHelper, Context, DefaultBandwidthMeter, DefaultLoadControl, okhttp3, ScrubbingModeParameters

### Community 66 - "Community 66"
Cohesion: 0.18
Nodes (25): addExoAspectLayoutChangeListener(), applyAspectMode(), applyAspectScale(), applyExoAspectMode(), AspectMode, CINEMA_ZOOM, FULL_SCREEN, HORIZONTAL_STRETCH (+17 more)

### Community 67 - "Player Runtime Core"
Cohesion: 0.08
Nodes (16): PlayerRuntimeController, ArrayDeque, AudioDeviceCallback, AudioOutputRoute, AutoSkipSegmentType, BitrateAwareLoadControl, CloudLibraryPlaybackContext, DefaultTrackSelector (+8 more)

### Community 68 - "Plugin Settings UX"
Cohesion: 0.16
Nodes (4): PluginViewModel, PluginUiEvent, PluginUiState, RepositoryConfigServer

### Community 69 - "Addon Repository"
Cohesion: 0.20
Nodes (3): Addon, AddonRepository, AddonRepositoryImpl

### Community 71 - "Community 71"
Cohesion: 0.25
Nodes (5): SearchHistoryDataStore, SkipInterval, SkipIntroRepository, ArmEntry, List

### Community 72 - "Community 72"
Cohesion: 0.09
Nodes (21): fromApi(), LibraryEntry, LibraryEntryInput, LibraryListTab, LibrarySourceMode, LOCAL, SIMKL, TRAKT (+13 more)

### Community 73 - "Community 73"
Cohesion: 0.12
Nodes (6): SupportersContributorsTab, Contributors, Sponsors, Supporters, SupportersContributorsUiState, SupportersContributorsViewModel

### Community 74 - "JavaScript Plugin Runtime"
Cohesion: 0.15
Nodes (5): BoundedReadResult, PluginRuntime, com, Gson, InputStream

### Community 75 - "Community 75"
Cohesion: 0.15
Nodes (9): DefaultEbmlReader, MasterElement, EbmlReader, Deprecated, EbmlProcessor, EbmlReader, ExtractorInput, Factory (+1 more)

### Community 76 - "Community 76"
Cohesion: 0.13
Nodes (6): DolbyVisionSampleTransformer, Track, DrmInitData, @MonotonicNonNull CryptoData, TrackOutput, TrueHdSampleRechunker

### Community 77 - "Community 77"
Cohesion: 0.11
Nodes (9): NextToWatch, WatchProgress, DisplayModeOverlay(), formatHz(), LoadingOverlay(), shouldSendPauseScrobble(), shouldSendStopScrobble(), DisplayModeInfo (+1 more)

### Community 78 - "Community 78"
Cohesion: 0.15
Nodes (10): fromStorage(), MoreLikeThisSourcePreference, TMDB, TRAKT, TraktSettingsDataStore, WatchProgressSource, NUVIO_SYNC, SIMKL (+2 more)

### Community 79 - "Community 79"
Cohesion: 0.15
Nodes (13): CacheEntry, MDBListRepository, ProviderType, AUDIENCE, IMDB, LETTERBOXD, MAL, METACRITIC (+5 more)

### Community 80 - "Community 80"
Cohesion: 0.13
Nodes (4): NuvioAssMatroskaExtractor, NuvioAssSubtitleExtractorOutput, NuvioAssTrackOutput, MatroskaExtractor

### Community 81 - "Community 81"
Cohesion: 0.16
Nodes (19): SetLanguage, TmdbSettingsEvent, TmdbSettingsUiState, TmdbSettingsViewModel, ToggleArtwork, ToggleBasicInfo, ToggleCollections, ToggleCredits (+11 more)

### Community 82 - "DEX Extension Loading"
Cohesion: 0.24
Nodes (7): ExternalExtensionLoader, looksLikePlugin(), ReflectivePluginWrapper, DexClassLoader, Error, File, Plugin

### Community 83 - "Community 83"
Cohesion: 0.14
Nodes (14): StreamAutoPlaySelector, AutoPlaySettingsDialogs(), autoPlaySettingsItems(), formatHalfStepValue(), formatReuseCacheDuration(), NextEpisodeThresholdModeDialog(), StreamAutoPlayModeDialog(), StreamAutoPlayProviderSelectionDialog() (+6 more)

### Community 84 - "Community 84"
Cohesion: 0.16
Nodes (16): containsInlineSpoilers(), filterDisplayableComments(), ResolvedCommentsTarget, stripInlineSpoilerMarkup(), TimedCache, toBestCommentsPathId(), toReviewModel(), toTraktPathId() (+8 more)

### Community 85 - "Community 85"
Cohesion: 0.16
Nodes (21): AddRepository, ClearError, ClearSuccess, ClearTestResults, ConfirmPendingRepoChange, ConfirmPendingScraperEnable, DismissPendingScraperEnable, PendingRepoChangeInfo (+13 more)

### Community 86 - "Community 86"
Cohesion: 0.13
Nodes (7): SettingsSnapshot, TraktConnectionMode, AWAITING_APPROVAL, CONNECTED, DISCONNECTED, TraktUiState, TraktViewModel

### Community 87 - "Community 87"
Cohesion: 0.10
Nodes (10): BitrateAwareLoadControl, AudioDelaySampleStream, Array, BooleanArray, DecoderInputBuffer, ExoTrackSelection, FormatHolder, PlayerId (+2 more)

### Community 88 - "Community 88"
Cohesion: 0.17
Nodes (3): DoviBridge, RealtimeConversionProbe, SelfTestResult

### Community 89 - "Community 89"
Cohesion: 0.20
Nodes (10): ElementState, EbmlProcessor, ElementType, Flags, Documented, IntDef, IOException, RequiresNonNull (+2 more)

### Community 90 - "Community 90"
Cohesion: 0.17
Nodes (19): nextEpisodeEndPromptLabel(), NextEpisodeEndPromptOverlay(), applyMetaDetails(), applyRecomputedNextEpisode(), autoSkipKey(), clearNextEpisodeAndCancelPostPlay(), enrichDescriptionFromTmdb(), evaluatePostPlayOverlayVisibility() (+11 more)

### Community 91 - "Subtitle Media Integration"
Cohesion: 0.13
Nodes (12): buildWithAssSupportCompat(), getAssHandlerCompat(), withAssMkvSupportCompat(), toAssRenderType(), PlayerMediaSourceFactory, AssHandler, AssRenderType, ExtractorsFactory (+4 more)

### Community 92 - "Community 92"
Cohesion: 0.23
Nodes (20): cardBrush(), ConnectedTrackingAccountContent(), ConnectedTrackingAccountDialog(), formatTrackingDuration(), SimklAccountDialog(), SimklSyncInfoContent(), TrackingBrandFooterButton(), TrackingBrandMessage() (+12 more)

### Community 93 - "Community 93"
Cohesion: 0.16
Nodes (8): migrate(), ProfileDataStoreFactory, ScopedDataStore, ShadowCopyDataStore, shouldMigrate(), kotlinx, MutableSet, Preferences

### Community 94 - "Plugin Screen"
Cohesion: 0.18
Nodes (19): AddRepositoryInline(), ConfirmRepoChangesDialog(), ConfirmScraperEnableDialog(), EmptyState(), formatDate(), ManageFromPhoneCard(), MessageOverlay(), PluginScreen() (+11 more)

### Community 95 - "Settings Navigation"
Cohesion: 0.17
Nodes (19): AccountSettingsInline(), ContentDiscoverySettingsContent(), EssentialAdvancedSettingsContent(), ExperienceModeLoadState, IntegrationSettingsContent(), IntegrationSettingsSection, AnimeSkip, Debrid (+11 more)

### Community 96 - "Community 96"
Cohesion: 0.17
Nodes (4): MatroskaZlibSampleDecompressor, Sniffer, Inflater, ParsableByteArray

### Community 97 - "Community 97"
Cohesion: 0.25
Nodes (3): startOnAvailablePort(), StreamBadgeConfigServer, IHTTPSession

### Community 99 - "Community 99"
Cohesion: 0.11
Nodes (18): PlaybackIssueAppDto, PlaybackIssueContentDto, PlaybackIssueDeviceDto, PlaybackIssueDiagnosticsDto, PlaybackIssueErrorDto, PlaybackIssueLoadingDto, PlaybackIssueLoadingEventDto, PlaybackIssuePlaybackAnalyticsDto (+10 more)

### Community 101 - "Community 101"
Cohesion: 0.20
Nodes (13): ResolvedRelatedTarget, TimedCache, toBestRelatedPathId(), toMetaPreview(), toMetaPreviewInternal(), toTraktPathId(), TraktRelatedService, TraktRelatedType (+5 more)

### Community 102 - "Community 102"
Cohesion: 0.18
Nodes (16): runAfrPreflightIfEnabled(), AfrCapabilityDisableButton(), AfrCapabilityWarningCard(), frameRateMatchingModeLabel(), FrameRateMatchingModeOptions(), InternalPlayerEngineDialog(), playbackCollapsibleSection(), PlaybackGeneralUi (+8 more)

### Community 105 - "Community 105"
Cohesion: 0.11
Nodes (17): TraktCreateOrUpdateListRequestDto, TraktListIdsDto, TraktListImagesDto, TraktListItemDto, TraktListItemsMutationRequestDto, TraktListItemsMutationResponseDto, TraktListMovieRequestItemDto, TraktListMutationCountDto (+9 more)

### Community 106 - "Community 106"
Cohesion: 0.24
Nodes (17): attemptAutoRetry(), attemptStartupRecovery(), cancelStableProgressReset(), findCauseOfType(), findInvalidResponseCodeException(), findMostRelevantCauseMessage(), isAudioTrackFailure(), isRetryablePlaybackError() (+9 more)

### Community 107 - "Community 107"
Cohesion: 0.19
Nodes (13): MDBListSettingsEvent, MDBListSettingsUiState, MDBListSettingsViewModel, ToggleAudience, ToggleEnabled, ToggleImdb, ToggleLetterboxd, ToggleMal (+5 more)

### Community 108 - "Community 108"
Cohesion: 0.17
Nodes (8): ChangeStatus, CONFIRMED, PENDING, REJECTED, PendingRepoChange, RepositoryConfigServer, RepositoryInfo, startOnAvailablePort()

### Community 109 - "Community 109"
Cohesion: 0.12
Nodes (16): DebridStreamCodecFilter, ANY, AV1, H264, HEVC, DebridStreamFeatureFilter, ANY, EXCLUDE (+8 more)

### Community 110 - "Community 110"
Cohesion: 0.12
Nodes (17): DebridStreamVisualTag, AI, DV, DV_ONLY, H_OU, H_SBS, HDR, HDR10 (+9 more)

### Community 111 - "Community 111"
Cohesion: 0.33
Nodes (5): DebugStat, DebugStatsSampler, PlayerDebugStatsOverlay(), PlayerSnapshot, ProcTimes

### Community 112 - "Audio and Subtitle Renderers"
Cohesion: 0.18
Nodes (11): buildStableAudioCapabilities(), SubtitleOffsetRenderer, SubtitleOffsetRenderersFactory, ArrayList, AudioCapabilities, AudioRendererEventListener, DefaultRenderersFactory, ForwardingRenderer (+3 more)

### Community 113 - "Community 113"
Cohesion: 0.27
Nodes (16): appLicenseItem(), AttributionDetailRow(), AttributionLogo(), AttributionSection(), dataAttributionItems(), Drawable, LicenseAttributionItem, LicenseLogo (+8 more)

### Community 114 - "Community 114"
Cohesion: 0.23
Nodes (16): ActiveSubscriptionContent(), ConnectedMembershipContent(), displayName(), formatMembershipDate(), GrantMembershipContent(), MembershipLoadErrorContent(), MembershipLoadingContent(), MembershipPanelBack() (+8 more)

### Community 115 - "Subtitle Hashing"
Cohesion: 0.20
Nodes (6): OpenSubtitlesHasher, Result, PlayerPlaybackNetworking, HttpURLConnection, OkHttpClient, SSLContext

### Community 116 - "Community 116"
Cohesion: 0.16
Nodes (7): DebugSettingsDataStore, MemberAccessRepository, memberAccessRetryDelayMs(), resolveMemberAccess(), shouldRefreshMemberAccess(), DebugMemberTierCard(), MemberTier

### Community 117 - "Community 117"
Cohesion: 0.12
Nodes (16): DebridStreamLanguage, CS, DE, EN, ES, FR, HI, IT (+8 more)

### Community 118 - "Subtitle Repository"
Cohesion: 0.24
Nodes (6): addonName, SubtitleRepositoryImpl, fetchAddonSubtitlesNow(), completed, SubtitleRepository, total

### Community 119 - "Community 119"
Cohesion: 0.15
Nodes (9): PlayerNextEpisodeRules, findActiveSkipInterval(), isSkipIntroButtonVisible(), isSkipIntroCanFocus(), nextActiveSkipInterval(), skipIntroAutoHideRemainingMs(), Clock, LocalDate (+1 more)

### Community 120 - "Community 120"
Cohesion: 0.20
Nodes (6): DolbyVisionExtractor, DolbyVisionExtractorsFactory, NalFormat, ANNEX_B, LENGTH_DELIMITED, Extractor

### Community 121 - "Subtitle Decoding"
Cohesion: 0.32
Nodes (3): SubtitleCharsetDetector, Character, Charset

### Community 122 - "Community 122"
Cohesion: 0.29
Nodes (3): PersistedTrackPreference, toTrackPreference(), TrackPreferenceDataStore

### Community 123 - "Community 123"
Cohesion: 0.25
Nodes (5): normalizeTraktTokenLifetimeSeconds(), TraktAuthDataStore, TraktAuthState, TraktDeviceCodeResponseDto, TraktTokenResponseDto

### Community 124 - "Community 124"
Cohesion: 0.19
Nodes (13): collectTrailerYtIds(), mapBehaviorHints(), mapReleaseDateCountry(), mapReleaseDates(), mapTrailers(), MetaBehaviorHints, MetaBehaviorHintsDto, MetaReleaseDateCountry (+5 more)

### Community 125 - "Community 125"
Cohesion: 0.14
Nodes (11): TorboxCachedItemDto, TorboxCheckCachedRequestDto, TorboxCloudFileDto, TorboxCloudItemDto, TorboxCreateTorrentDataDto, TorboxDeviceAuthorizationDto, TorboxDeviceTokenDto, TorboxDeviceTokenRequestDto (+3 more)

### Community 126 - "Community 126"
Cohesion: 0.38
Nodes (4): Session, Snapshot, StreamSearchRequestKey, StreamSearchSessionCache

### Community 127 - "Community 127"
Cohesion: 0.14
Nodes (13): AppTheme, AMBER, ARCTIC_BLUE, CRIMSON, EMERALD, GOLD, GRAPHITE, JADE (+5 more)

### Community 128 - "Community 128"
Cohesion: 0.14
Nodes (14): DebridStreamAudioTag, AAC, ATMOS, DD, DD_PLUS, DTS, DTS_ES, DTS_HD (+6 more)

### Community 129 - "Community 129"
Cohesion: 0.14
Nodes (14): DebridStreamQuality, BLURAY, BLURAY_REMUX, CAM, DVDRIP, HD_RIP, HDRIP, HDTV (+6 more)

### Community 130 - "Community 130"
Cohesion: 0.18
Nodes (12): countryToLanguageCode(), Meta, MetaBehaviorHints, MetaCastMember, MetaCompany, MetaLink, MetaReleaseDate, MetaReleaseDateCountry (+4 more)

### Community 131 - "Plugin Domain Contract"
Cohesion: 0.15
Nodes (12): ExternalPluginEntry, ExternalRepoManifest, LocalScraperResult, PluginManifest, PluginRepository, RemotePluginInfo, RepositoryType, EXTERNAL_DEX (+4 more)

### Community 132 - "Community 132"
Cohesion: 0.25
Nodes (13): buildPlaybackIssueLoadingInput(), compactTraceValue(), finishLoadingDiagnostics(), playbackStateName(), PlayerLoadingDiagnosticEvent, rawLoadingEventLine(), recordLoadingDiagnosticEvent(), recordLoadingDiagnosticRawEventLine() (+5 more)

### Community 133 - "Community 133"
Cohesion: 0.26
Nodes (12): AdvancedSettingsViewModel, AdvancedSettingsContent(), ConnectionStatusBadge(), ConnectionType, Ethernet, Offline, WiFi, cwEnrichmentCache() (+4 more)

### Community 134 - "Community 134"
Cohesion: 0.18
Nodes (5): DolbyVisionExtractorOutput, TrackAwareSeekMap, ExtractorOutput, SeekMap, SeekPoints

### Community 135 - "Community 135"
Cohesion: 0.18
Nodes (4): AuthSessionNoticeDataStore, StartupAuthNotice, NUVIO, TRAKT

### Community 137 - "Community 137"
Cohesion: 0.15
Nodes (12): AuthDiagnosticAppDto, AuthDiagnosticDeviceDto, AuthDiagnosticEnvironmentDto, AuthDiagnosticEventDto, AuthDiagnosticExceptionDto, AuthDiagnosticFlowDto, AuthDiagnosticNetworkDto, AuthDiagnosticReportRequestDto (+4 more)

### Community 138 - "Community 138"
Cohesion: 0.15
Nodes (12): AppExtrasCastMemberDto, AppExtrasDto, MetaBehaviorHintsDto, MetaDto, MetaLinkDto, MetaReleaseDateCountryDto, MetaReleaseDateDto, MetaReleaseDatesEnvelopeDto (+4 more)

### Community 139 - "Community 139"
Cohesion: 0.33
Nodes (6): Episode, Movie, ScrobbleStamp, TraktScrobbleItem, TraktScrobbleService, TraktScrobbleRequestDto

### Community 140 - "Community 140"
Cohesion: 0.22
Nodes (9): Decision, CONVERT_TO_DV81, NATIVE_DV7, STRIP_AND_TONEMAP, STRIP_BEST_EFFORT, STRIP_TO_HDR10, DolbyVisionBaseLayerPolicy, DvDecoderProfileSupport (+1 more)

### Community 141 - "Community 141"
Cohesion: 0.19
Nodes (5): DolbyVisionCodecFallback, PlaybackSpeedAwareAudioRenderer, MediaCodecAudioRenderer, MediaCodecInfo, MediaCodecSelector

### Community 142 - "PCM Gain Processor"
Cohesion: 0.23
Nodes (4): GainAudioProcessor, AudioProcessor, BaseAudioProcessor, ByteBuffer

### Community 143 - "Subtitle Timing Runtime"
Cohesion: 0.23
Nodes (10): applySubtitleAutoSyncCue(), autoSyncTrackKey(), downloadSubtitleBody(), executeSubtitleDownload(), formatAutoSyncDelay(), formatAutoSyncTimestamp(), maybeLoadSubtitleAutoSyncCues(), reloadSubtitleAutoSyncCues() (+2 more)

### Community 144 - "Subtitle Timing UX"
Cohesion: 0.31
Nodes (12): CueRow(), CueSelectionPanel(), sanitizeCuePreviewText(), selectAutoSyncVisibleCues(), subtitleCueListItemKey(), SubtitleTimingDialog(), SyncPromptPanel(), SyncStage (+4 more)

### Community 145 - "Community 145"
Cohesion: 0.28
Nodes (10): DebugSettingsEvent, DebugSettingsUiState, DebugSettingsViewModel, GenerateLibraryItems, SelectMemberTier, SignIn, ToggleAccountTab, ToggleBufferLogs (+2 more)

### Community 146 - "Community 146"
Cohesion: 0.15
Nodes (13): SettingsCategory, ABOUT, ACCOUNT, ADVANCED, APPEARANCE, CONTENT_DISCOVERY, DEBUG, EXPERIENCE (+5 more)

### Community 147 - "Community 147"
Cohesion: 0.30
Nodes (4): DebridFormatterConfigServer, DebridFormatterSettings, startOnAvailablePort(), NanoHTTPD

### Community 148 - "Community 148"
Cohesion: 0.35
Nodes (3): CachedInProgressItem, CachedNextUpItem, ContinueWatchingEnrichmentCache

### Community 149 - "Community 149"
Cohesion: 0.23
Nodes (4): ExperienceModeDataStore, ExperienceModeConfirmationDialog(), ExperienceModeSettingsViewModel, ExperienceMode

### Community 150 - "Community 150"
Cohesion: 0.18
Nodes (6): toDomainOrNull(), SavedLibraryItem, resolvePostPlayRecommendation(), MetaPreview, PostPlayRecommendation, TmdbEnrichment

### Community 151 - "Community 151"
Cohesion: 0.17
Nodes (8): DetailImdbRatingsVisibility, HIDE_ALL, HIDE_EPISODES, HIDE_UNWATCHED_EPISODES, SHOW_ALL, HomeImdbRatingsVisibility, HIDE_ALL, SHOW_ALL

### Community 152 - "Runtime Track State"
Cohesion: 0.23
Nodes (11): Addon, beginSwitchTraceSession(), Disabled, ExplicitSubtitleSelectionForEngineSwitch, Internal, logSwitchTrace(), PendingAudioSelection, PendingEngineSwitchTrackPreference (+3 more)

### Community 153 - "Community 153"
Cohesion: 0.29
Nodes (10): AdvancedSettingsEvent, AdvancedSettingsUiState, AdvancedSettingsViewModel, SetComposeHighlighterEnabled, SetFastHorizontalNavigationEnabled, SetPlaybackIssueReportsEnabled, SetPlayerStatsHudEnabled, SetSentryEnabled (+2 more)

### Community 154 - "Playback Audio Settings"
Cohesion: 0.29
Nodes (9): AudioLanguageSelectionDialog(), AudioOutputChannelsDialog(), AudioSettingsDialogs(), DecoderPriorityDialog(), Dv7HandlingModeDialog(), MpvHardwareDecodeModeDialog(), AudioOutputChannels, Dv7HandlingMode (+1 more)

### Community 155 - "Community 155"
Cohesion: 0.24
Nodes (3): SimklSettingsUiState, SimklSettingsViewModel, Job

### Community 157 - "Community 157"
Cohesion: 0.18
Nodes (9): PremiumizeAccountInfoDto, PremiumizeCacheCheckDto, PremiumizeCloudFileDto, PremiumizeDeviceAuthorizationDto, PremiumizeDeviceTokenDto, PremiumizeDirectDownloadDto, PremiumizeDirectDownloadFileDto, PremiumizeItemDetailsDto (+1 more)

### Community 158 - "Community 158"
Cohesion: 0.18
Nodes (10): TraktDeviceCodeRequestDto, TraktDeviceCodeResponseDto, TraktDeviceTokenRequestDto, TraktRefreshTokenRequestDto, TraktRevokeRequestDto, TraktTokenResponseDto, TraktUserDto, TraktUserSettingsResponseDto (+2 more)

### Community 159 - "Community 159"
Cohesion: 0.44
Nodes (3): AuthDiagnosticReportQueue, AuthDiagnosticReportRepository, AuthDiagnosticReportRequestDto

### Community 160 - "Community 160"
Cohesion: 0.29
Nodes (10): extractYear(), hasAnyId(), isTraktCompatibleId(), normalizeContentId(), parseContentIds(), ParsedContentIds, parseIsoToMillis(), resolveEffectiveContentId() (+2 more)

### Community 161 - "Community 161"
Cohesion: 0.20
Nodes (9): CosmeticEntitlement, ARCTIC_BLUE_THEME, GOLD_THEME, GRAPHITE_THEME, JADE_THEME, PROFILE_AVATARS, PROFILE_BACKGROUNDS, ROSE_GOLD_THEME (+1 more)

### Community 162 - "Community 162"
Cohesion: 0.33
Nodes (11): buildScrobbleItem(), currentPlaybackProgressPercent(), emitCompletionScrobbleStop(), emitPauseScrobbleForCurrentProgress(), emitScrobblePause(), emitScrobbleStart(), emitScrobbleStop(), emitStopScrobbleForCurrentProgress() (+3 more)

### Community 163 - "Community 163"
Cohesion: 0.38
Nodes (9): ColdStartPrime, None, PlayerStartupPlaybackPolicy, PostFirstFrameResume, PreFirstFrameResume, ReadyAction, ReadyState, ReadyTransition (+1 more)

### Community 164 - "Community 164"
Cohesion: 0.35
Nodes (10): isSelectKey(), isSelectOrMenuKey(), metadataLine(), PostPlayRecommendationButton(), PostPlayRecommendationNavigationButton(), PostPlayRecommendationOverlay(), PostPlayRecommendationSummary(), rememberPostPlayRecommendationIcon() (+2 more)

### Community 165 - "Community 165"
Cohesion: 0.25
Nodes (10): blocksPostPlayRecommendation(), isPostPlayCandidateWatched(), PostPlayRecommendation, postPlayRecommendationCountdownSeconds(), PostPlayRecommendationUiState, resolvePostPlayContentType(), returnToPlayer(), shouldPrefetchPostPlayRecommendation() (+2 more)

### Community 166 - "Community 166"
Cohesion: 0.24
Nodes (6): DebridDeviceAuthorization, DebridDeviceAuthorizationTokenResult, PremiumizeDeviceTokenDto, retrofit2, TorboxDeviceTokenDto, TorboxEnvelopeDto

### Community 167 - "Community 167"
Cohesion: 0.31
Nodes (7): android, formatMB(), formatSpeed(), launchTorrentSourceStream(), observeTorrentState(), startTorrentStream(), fetchFastComUrls()

### Community 170 - "Stream Response DTO"
Cohesion: 0.20
Nodes (9): BehaviorHintsDto, ProxyHeadersDto, StreamClientResolveDto, StreamClientResolveParsedDto, StreamClientResolveRawDto, StreamClientResolveStreamDto, StreamDto, StreamResponseDto (+1 more)

### Community 171 - "Community 171"
Cohesion: 0.42
Nodes (3): CacheEntry, ImdbEpisodeRatingsRepository, Double

### Community 172 - "Community 172"
Cohesion: 0.42
Nodes (8): AlreadyUsed, Approved, Denied, Expired, Failed, Pending, SlowDown, TraktTokenPollResult

### Community 173 - "Community 173"
Cohesion: 0.24
Nodes (7): toIntExactOrNull(), TraktTrackingScrobbler, trackingActionForNonPlayingState(), TrackingScrobbleAction, TrackingScrobbleEvent, TrackingScrobbler, TraktScrobbleItem

### Community 174 - "Community 174"
Cohesion: 0.27
Nodes (7): CardDepthStyle, CardDepthSurface, CAST, CONTINUE_WATCHING, EPISODE_CARDS, POSTERS, TRAILERS

### Community 175 - "Community 175"
Cohesion: 0.33
Nodes (9): CatalogRow, catalogRowLegacyKey(), catalogRowStableKey(), legacyKey(), mergeCatalogPage(), nextCatalogSkip(), stableItemKey(), stableItemKeys() (+1 more)

### Community 176 - "Community 176"
Cohesion: 0.20
Nodes (10): DebridStreamSortKey, AUDIO_CHANNEL, AUDIO_TAG, ENCODE, LANGUAGE, QUALITY, RELEASE_GROUP, RESOLUTION (+2 more)

### Community 177 - "Community 177"
Cohesion: 0.29
Nodes (5): LoggingDataSource, LoggingDataSourceFactory, recentEvents(), recordEvent(), DataSource

### Community 179 - "Community 179"
Cohesion: 0.38
Nodes (8): Decision, Input, KeepWaiting, PlayerStallWatchdogPolicy, SeekPastBufferedEdge, SkipBufferedNotAhead, SkipTargetNotForward, SkipUnknownDuration

### Community 181 - "Community 181"
Cohesion: 0.38
Nodes (9): formatBitrate(), formatFileSize(), formatResolution(), InfoItem(), SectionLabel(), StreamInfoContent(), StreamInfoHudButton(), StreamInfoOverlay() (+1 more)

### Community 182 - "Community 182"
Cohesion: 0.40
Nodes (9): DebugActionCard(), DebugDialogButton(), DebugGenerateLibraryCard(), DebugProgressIndicatorCard(), DebugProgressIndicatorPreview(), DebugSettingsContent(), DebugSignInCard(), DebugToggleCard() (+1 more)

### Community 184 - "Community 184"
Cohesion: 0.39
Nodes (3): ExternalRepoParser, ExternalRepoParseResult, ExternalPluginEntry

### Community 185 - "Community 185"
Cohesion: 0.36
Nodes (6): DisplayModeSwitchResult, Failed, Found, HttpRangeFetchResult, Mp4MdatWalkResult, NeedHeaderAt

### Community 186 - "Community 186"
Cohesion: 0.33
Nodes (3): ZidooPlaybackResult, ZidooPlayerMonitor, JSONObject

### Community 188 - "Community 188"
Cohesion: 0.42
Nodes (8): mapPeople(), CastChip(), CastDetailView(), PauseMetadataView(), PauseOverlay(), PauseOverlayClock(), AppExtrasCastMemberDto, MetaCastMember

### Community 189 - "Community 189"
Cohesion: 0.22
Nodes (4): SyncRepositoryImpl, ClaimSyncResult, SupabaseLinkedDevice, SyncRepository

### Community 190 - "Community 190"
Cohesion: 0.25
Nodes (7): ContentType, CHANNEL, MOVIE, SERIES, TV, UNKNOWN, fromString()

### Community 191 - "Community 191"
Cohesion: 0.22
Nodes (9): DebridStreamResolution, P1080, P1440, P2160, P360, P480, P576, P720 (+1 more)

### Community 192 - "Community 192"
Cohesion: 0.39
Nodes (7): Input, None, PlayerFirstFrameCodecRecoveryPolicy, RecoveryAction, RetryDv7Mode1, RetryVc1Software, RetryVc1TrackBypass

### Community 193 - "Community 193"
Cohesion: 0.53
Nodes (8): AutoPlayBody(), nextEpisodeDisplayLabel(), NextEpisodeStatusLine(), NextEpisodeThumbnail(), PostPlayOverlay(), PostPlayPillButton(), StillWatchingBody(), PostPlayMode

### Community 194 - "Addon Web Configuration"
Cohesion: 0.25
Nodes (5): AddonWebConfigMode, sanitizePendingAddonChange(), AddonWebPage, PageState, PendingAddonChange

### Community 197 - "Community 197"
Cohesion: 0.36
Nodes (7): Found, MetaFailureKind, MISSING, REQUEST_FAILED, MetaLookupResult, NotFound, SourceSufficient

### Community 198 - "Addon Domain Model"
Cohesion: 0.29
Nodes (7): Addon, AddonBehaviorHints, AddonResource, CatalogDescriptor, CatalogExtra, enabledAddons(), StremioAddonsConfig

### Community 199 - "Community 199"
Cohesion: 0.29
Nodes (7): LibraryDeltaApplyResult, LibraryDeltaEvent, LibrarySnapshotApplyResult, librarySyncIdentity(), LibrarySyncKey, LibrarySyncState, toLibrarySyncKey()

### Community 200 - "Audio Control Focus"
Cohesion: 0.25
Nodes (8): AudioControlFocusTarget, AmpMinus, AmpPlus, CenterMinus, CenterPlus, DelayMinus, DelayPlus, Persist

### Community 201 - "Community 201"
Cohesion: 0.25
Nodes (8): LayoutSettingsSection, CONTINUE_WATCHING, DETAIL_PAGE, FOCUSED_POSTER, HOME_CONTENT, HOME_LAYOUT, POSTER_CARD_STYLE, STREAMS

### Community 202 - "Playback Settings Sections"
Cohesion: 0.25
Nodes (8): PlaybackSection, AUDIO_TRAILER, BUFFER_NETWORK, DIAGNOSTICS, GENERAL, P2P, STREAM_SELECTION, SUBTITLES

### Community 204 - "Community 204"
Cohesion: 0.29
Nodes (6): LibassRenderType, CUES, EFFECTS_CANVAS, EFFECTS_OPEN_GL, OVERLAY_CANVAS, OVERLAY_OPEN_GL

### Community 207 - "Community 207"
Cohesion: 0.29
Nodes (6): TraktEpisodeDto, TraktIdsDto, TraktImagesDto, TraktMovieDto, TraktSeasonDto, TraktShowDto

### Community 208 - "Community 208"
Cohesion: 0.52
Nodes (3): ParentalGuideRepository, ParentalGuideResult, ImdbApiParentsGuideCategory

### Community 209 - "Community 209"
Cohesion: 0.29
Nodes (6): PlaybackIssuePlaybackAnalyticsInput, PlaybackIssuePlaybackEventInput, PlaybackIssuePlaybackFormatInput, PlaybackIssuePlaybackHealthSnapshotInput, PlaybackIssuePlaybackLoadErrorInput, PlaybackIssuePlaybackLoadInput

### Community 210 - "Community 210"
Cohesion: 0.62
Nodes (6): EpisodeMappingEntry, isUsefulEpisodeTitle(), normalizeEpisodeTitle(), remapEpisodeBetweenLists(), remapEpisodeByTitleOrIndex(), reverseRemapEpisodeByTitleOrIndex()

### Community 211 - "Community 211"
Cohesion: 0.29
Nodes (7): DebridStreamEncode, AV1, AVC, DIVX, HEVC, UNKNOWN, XVID

### Community 212 - "Community 212"
Cohesion: 0.33
Nodes (5): fromString(), PosterShape, LANDSCAPE, POSTER, SQUARE

### Community 213 - "Playback Subtitle Settings"
Cohesion: 0.33
Nodes (5): trailerAndAudioSettingsItems(), SubtitleSettingsDialogs(), subtitleSettingsItems(), LazyListScope, PlayerSettings

### Community 214 - "Community 214"
Cohesion: 0.38
Nodes (5): Input, PlayerFirstFrameWatchdogPolicy, RecoveryAction, ForcePlayWhenReady, None

### Community 216 - "Community 216"
Cohesion: 0.29
Nodes (7): TrackingFocusTarget, CONTINUE_WATCHING, LIBRARY, MORE_LIKE_THIS, SIMKL, TRAKT, WATCH_PROGRESS

### Community 220 - "Community 220"
Cohesion: 0.33
Nodes (4): RealDebridAddTorrentDto, RealDebridTorrentFileDto, RealDebridTorrentInfoDto, RealDebridUnrestrictLinkDto

### Community 221 - "Community 221"
Cohesion: 0.33
Nodes (3): MembershipOverviewRepository, AuthState, StateFlow

### Community 222 - "Community 222"
Cohesion: 0.33
Nodes (6): DebridStreamAudioChannel, CH_2_0, CH_5_1, CH_6_1, CH_7_1, UNKNOWN

### Community 223 - "Community 223"
Cohesion: 0.40
Nodes (5): DiscoverLocation, IN_SEARCH, IN_SIDEBAR, OFF, fromLegacySearchDiscoverEnabled()

### Community 224 - "Community 224"
Cohesion: 0.40
Nodes (5): FolderViewMode, FOLLOW_LAYOUT, ROWS, TABBED_GRID, fromString()

### Community 225 - "Community 225"
Cohesion: 0.47
Nodes (5): MemberAccess, MemberTier, SUPPORTER, SUPPORTER_PLUS, preview()

### Community 226 - "Community 226"
Cohesion: 0.40
Nodes (4): CustomDefaultTrackNameProvider, formatNameFromMime(), getChannelLayoutName(), DefaultTrackNameProvider

### Community 228 - "Community 228"
Cohesion: 0.47
Nodes (4): enterStillWatchingPromptMode(), exitFromStillWatching(), onDismissStillWatchingPrompt(), shouldEnterStillWatchingPrompt()

### Community 230 - "Community 230"
Cohesion: 0.40
Nodes (4): AutoPlay, NextEpisodeInfo, PostPlayMode, StillWatching

### Community 231 - "Community 231"
Cohesion: 0.33
Nodes (6): NetworkTestState, Done, Error, Idle, TestingDownload, TestingLatency

### Community 235 - "Community 235"
Cohesion: 0.40
Nodes (4): PlayerPreference, ASK_EVERY_TIME, EXTERNAL, INTERNAL

### Community 236 - "Community 236"
Cohesion: 0.40
Nodes (4): StreamAutoPlayMode, FIRST_STREAM, MANUAL, REGEX_MATCH

### Community 237 - "Community 237"
Cohesion: 0.40
Nodes (4): StreamAutoPlaySource, ALL_SOURCES, ENABLED_PLUGINS_ONLY, INSTALLED_ADDONS_ONLY

### Community 238 - "Addon Manifest DTO"
Cohesion: 0.40
Nodes (4): AddonBehaviorHintsDto, AddonManifestDto, CatalogDescriptorDto, StremioAddonsConfigDto

### Community 239 - "Community 239"
Cohesion: 0.40
Nodes (4): TraktCommentDto, TraktCommentUserDto, TraktCommentUserStatsDto, TraktSearchResultDto

### Community 242 - "Community 242"
Cohesion: 0.40
Nodes (4): AppFont, DM_SANS, INTER, OPEN_SANS

### Community 243 - "Community 243"
Cohesion: 0.70
Nodes (4): AuthState, FullAccount, Loading, SignedOut

### Community 244 - "Community 244"
Cohesion: 0.40
Nodes (4): ContinueWatchingCardStyle, CARD, POSTER, WIDE

### Community 245 - "Community 245"
Cohesion: 0.40
Nodes (4): ContinueWatchingSortMode, DEFAULT, SPLIT_UPCOMING, STREAMING_STYLE

### Community 246 - "Community 246"
Cohesion: 0.40
Nodes (5): DebridStreamMinimumQuality, ANY, P1080, P2160, P720

### Community 247 - "Community 247"
Cohesion: 0.40
Nodes (5): DebridStreamSortMode, DEFAULT, QUALITY_DESC, SIZE_ASC, SIZE_DESC

### Community 248 - "Community 248"
Cohesion: 0.40
Nodes (4): EpisodeOptionsOverlayStyle, ARTWORK, BLUR, NONE

### Community 249 - "Community 249"
Cohesion: 0.40
Nodes (4): HomeLayout, CLASSIC, GRID, MODERN

### Community 250 - "Community 250"
Cohesion: 0.40
Nodes (4): SettingsUiStyle, CLASSIC, HORIZON, ZEN

### Community 251 - "Community 251"
Cohesion: 0.60
Nodes (4): resolveTrackingAttribution(), TrackingAttributedItem, TrackingAttribution, Sequence

### Community 253 - "Community 253"
Cohesion: 0.40
Nodes (5): PlaybackIssueReportStatus, Failed, Idle, Sending, Sent

### Community 254 - "Community 254"
Cohesion: 0.40
Nodes (4): shouldCancelSimklPolling(), shouldRefreshMissingSimklIdentity(), SimklAuthState, SimklConnectionMode

### Community 255 - "Community 255"
Cohesion: 0.40
Nodes (5): create_session, SessionManager, validate_token, get_db, verify_hash

### Community 260 - "Community 260"
Cohesion: 0.67
Nodes (3): DiscoverLocationDialog(), DiscoverLocationRow(), DiscoverLocation

### Community 261 - "Community 261"
Cohesion: 0.50
Nodes (3): GitHubContributorDto, UniqueContributionsResponseDto, UniqueContributorDto

### Community 262 - "Community 262"
Cohesion: 0.50
Nodes (3): MDBListRatingItemDto, MDBListRatingRequestDto, MDBListRatingResponseDto

### Community 263 - "Community 263"
Cohesion: 0.50
Nodes (3): SupportersWallGroupDto, SupportersWallMemberDto, SupportersWallResponseDto

### Community 265 - "Community 265"
Cohesion: 0.50
Nodes (3): ExperienceMode, ADVANCED, ESSENTIAL

### Community 266 - "Community 266"
Cohesion: 0.50
Nodes (3): FocusedPosterTrailerPlaybackTarget, EXPANDED_CARD, HERO_MEDIA

### Community 269 - "Media Session Metadata"
Cohesion: 0.67
Nodes (3): buildMediaSessionMetadata(), updateMediaSessionMetadata(), MediaMetadata

### Community 270 - "Player Navigation Arguments"
Cohesion: 0.67
Nodes (3): from(), PlayerNavigationArgs, SavedStateHandle

### Community 271 - "Community 271"
Cohesion: 0.50
Nodes (4): OverlayFocusRail, LANGUAGE, OPTION, STYLE

### Community 272 - "Community 272"
Cohesion: 0.83
Nodes (3): TmdbSettingsContent(), TmdbSettingsScreen(), TmdbSettingsViewModel

### Community 280 - "Community 280"
Cohesion: 0.67
Nodes (3): StreamFailureKind, MISSING, REQUEST_FAILED

### Community 284 - "Community 284"
Cohesion: 0.67
Nodes (3): FrameRateSource, PROBE, TRACK

### Community 288 - "Community 288"
Cohesion: 0.67
Nodes (3): SettingsSectionDestination, External, Inline

## Knowledge Gaps
- **678 isolated node(s):** `ANNEX_B`, `LENGTH_DELIMITED`, `SubtitleInput`, `FULL`, `COLLECTIONS_ONLY` (+673 more)
  These have ≤1 connection - possible missing edges or undocumented components.
- **54 thin communities (<3 nodes) omitted from report** — run `graphify query` to explore isolated nodes.

## Suggested Questions
_Questions this graph is uniquely positioned to answer:_

- **Why does `DebridSettings` connect `Community 14` to `Community 1`, `JavaScript Plugin Runtime`, `Community 109`, `Community 71`?**
  _High betweenness centrality (0.036) - this node is a cross-community bridge._
- **Why does `SettingsDetailPane()` connect `Settings Navigation` to `Community 1`, `Community 146`, `Community 53`, `Audio Selection UX`?**
  _High betweenness centrality (0.022) - this node is a cross-community bridge._
- **Why does `MatroskaExtractor` connect `Community 26` to `Community 96`, `Community 134`, `Community 41`, `Community 75`, `Community 76`, `PCM Gain Processor`, `Community 120`, `Community 89`, `Subtitle Media Integration`, `Community 28`?**
  _High betweenness centrality (0.022) - this node is a cross-community bridge._
- **What connects `ANNEX_B`, `LENGTH_DELIMITED`, `SubtitleInput` to the rest of the system?**
  _678 weakly-connected nodes found - possible documentation gaps or missing edges._
- **Should `Community 0` be split into smaller, more focused modules?**
  _Cohesion score 0.0489225393127548 - nodes in this community are weakly interconnected._
- **Should `Community 1` be split into smaller, more focused modules?**
  _Cohesion score 0.03306930693069307 - nodes in this community are weakly interconnected._
- **Should `Engine Failover Tracks` be split into smaller, more focused modules?**
  _Cohesion score 0.06077606358111267 - nodes in this community are weakly interconnected._