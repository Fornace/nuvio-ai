# TV interaction & accessibility review — Generated Dialogue Subtitles (pre-change)

Scope: read-only review of the current player subtitle rails, settings integration hub, RTL helpers and Compose test infrastructure against the UX requirements in `docs/ai-media-research/decision.md` (Television UX section) and `docs/agent-briefs/generated-dialogue-subtitles-hammersmith.md` (Milestone 4, TV interaction specification, UX/accessibility acceptance checks). Repo HEAD reviewed: `6e225905`. No files were changed.

## Summary

**Verdict: the feature can fit the current TV rails without focus churn, but only along specific seams.** The **Generate dialogue captions (beta)** entry can be appended to the existing option rail (`SubtitleSelectionOverlay.kt`) with a constant stable id, because the rail already has append-only ordering, LazyColumn key stability, per-language focus memory (`optionFocusMemory`) and a token-based focus-restore machine (`requestOptionFocus` with duplicate suppression) that together satisfy "cue arrivals never change option identity, reorder rails or steal focus". The **provider connection** entry fits the settings Integration hub exactly like the existing Debrid/TMDB/MDBList/Anime-Skip sections (stable item keys, per-section `FocusRequester`, generic `BackHandler` to Hub). The **Exo switch** prompt can reuse the position-preserving manual engine switch (`pendingSeekPosition`).

Three things do **not** fit as-is and must be designed around: (1) a fourth "setup" rail cannot be added horizontally — the three fixed rails already consume 912 dp of the 960 dp available on a 1080p @320dpi TV, leaving 48 dp headroom, so setup/consent must be a dialog window (the `NuvioDialog` + `requestFocusAfterFrames` pattern) or reuse the 280 dp style-rail slot; (2) the overlay's snapshot-at-open pattern (`remember(visible)`) freezes live inputs, so generation status (Preparing/Listening/Live/Failed) needs a dedicated non-snapshotted state channel that never feeds option identity; (3) every new overlay flag must be threaded through at least seven hand-maintained condition lists in `PlayerScreen.kt` / `PlayerRuntimeController*` which have already drifted from each other once — the single biggest structural focus-churn risk. There are also concrete gaps to close: the empty option rail has no focusable Retry/Generate actions, no lookup-error state exists in `PlayerUiState`, the option cards expose no selected-state semantics to TalkBack, RTL key remapping is duplicated inline in seven places, the skip-intro focus-escape gate only covers the subtitle overlay, and there are zero Compose UI tests for any player overlay today. Findings, an exact test matrix, and a deterministic focus state model follow.

---

### F1 — Generate option needs a constant stable id; the addon id pattern embeds mutable data and must not be copied

Finding: The **Generate dialogue captions (beta)** option must be identified by one constant string (e.g. `generate:beta`) for its entire lifecycle. The existing addon option id builder embeds mutable data (`addon:${addonName}:${id}:${url}`), and the option focus-requester map is keyed by the id list, so any id churn rebuilds requesters and disposes the focused LazyColumn node.
Evidence: `app/src/main/java/com/nuvio/tv/ui/screens/player/SubtitleSelectionOverlay.kt:1856` — `addonSubtitleOptionId(subtitle) = "addon:${subtitle.addonName}:${subtitle.id}:${subtitle.url}"`; `:245` — `val optionItemRequesters = rememberFocusRequesterMap(subtitleOptions.map { it.id })`; `:1617-1619` — `remember(keys)` recreates the whole map when ids change; `:729` — `items(items = options, key = { option -> option.id })` makes id the LazyColumn node identity.
Impact: If provider name, status label (Preparing/Listening/Live/Complete/Failed) or cost summary enters the id, every status update rebuilds the requester map and swaps the LazyColumn key: the focused node is disposed, focus falls out of the rail, and the acceptance gates "D-pad focus remains stable under high-frequency updates" and "Cue arrivals never change option identity" fail.
Fix: Append the generate option with one constant id at the end of `buildSubtitleOptionRailItems` output (`internalItems + addonItems + generateItem`), with status/cost/provider text carried only in the card's display fields. Add a unit test pinning id constancy across all status values (see test matrix).
Priority: P1
Confidence: high

### F2 — Snapshot-at-open freezes live state; generation status needs a dedicated non-snapshotted channel

Finding: `SubtitleSelectionOverlay` freezes nearly all inputs at open with `remember(visible)`. A live status that changes while the overlay is open (Listening → Live → Repositioning) cannot flow through those snapshots, and must not be smuggled through `internalTracks`/`addonSubtitles` (F1 churn) either.
Evidence: `SubtitleSelectionOverlay.kt:112-135` — `sessionInternalTracks = remember(visible) { ... }`, `sessionAddonSubtitles = remember(visible, addonSubtitles) { ... }`, plus ~13 more `remember(visible)` session fields; the one deliberately non-snapshotted input is `:126` — `val sessionIsLoadingAddons = isLoadingAddons`, and the loading card at `:714-716` updates live while the overlay is open.
Impact: Either the status is frozen (user opens the overlay during generation and sees a stale "Preparing" forever — violates truthful-state requirement and the stable Preparing/Listening/Repositioning/Paused/Live/Complete/Failed labels), or it flows through track/addon lists and triggers F1 identity churn plus full option-rail recomposition on every cue.
Fix: Expose a dedicated `StateFlow<GenerationStatusUi>` (status, provider name, cost summary, error code) on `PlayerUiState`/ViewModel, read it directly (like `sessionIsLoadingAddons`) only inside the generate status card and the persistent "Audio being transcribed" indicator, and keep it out of every `remember(...)` key and out of `buildSubtitleOptionRailItems` inputs.
Priority: P1
Confidence: high

### F3 — No horizontal budget for a fourth rail on 960 dp TVs; setup/consent must be a dialog or reuse the style slot

Finding: The overlay consumes 912 dp of horizontal space (104 dp scaffold padding + 780 dp fixed rail widths + 28 dp gaps). A setup rail (Audio, Spoken language, Subtitle language, Provider, Cost cap, Start generating — six focusable rows, minimum ~200-280 dp) cannot be appended as a fourth column on 1080p @320dpi TVs (960 dp wide).
Evidence: `SubtitleSelectionOverlay.kt:371` — `contentPadding = PaddingValues(start = 52.dp, end = 52.dp, ...)`; `:628` `RailColumn(width = 200.dp)` (languages); `:707` `RailColumn(width = 300.dp)` (options); `:771` `RailColumn(width = 280.dp)` (style); the Row uses `Arrangement.spacedBy(14.dp)`. Total = 52+200+14+300+14+280+52 = 912 dp; remaining headroom 48 dp.
Impact: A fourth rail overflows the 960 dp surface: the Row does not scroll, so the setup rail (or the style rail, depending on clipping) is cut off and its focusable rows become unreachable or half-rendered — guaranteed focus/navigation failure on common TV hardware.
Fix: Render setup/consent as a `NuvioDialog` window above the overlay (the component already exists with `suppressFirstKeyUp` key-repeat protection and `onDismissRequest`), or — if it must stay in the rail geometry — temporarily swap the 280 dp style-slot content for setup content using the existing `RailFadeIn` + token focus machinery. State the chosen container in the design before implementation.
Priority: P1
Confidence: high

### F4 — Seven hand-maintained overlay-condition lists; they have already drifted — each new flag multiplies omission risk

Finding: Every new overlay boolean (setup dialog, MPV switch prompt, transcription indicator banner) must be added to at least seven separate hand-written condition lists across `PlayerScreen.kt` and the runtime controller. These lists are not derived from one source and have already diverged once.
Evidence: (1) `PlayerScreen.kt:311-309` `handleBackPress` chain; (2) `PlayerScreen.kt:437-467` focus `LaunchedEffect` key list and guard — the guard at `:452-455` excludes panels but omits `showMoreDialog` and `showStreamInfoOverlay`; (3) `PlayerScreen.kt:671-677` `panelOrDialogOpen` in the container `onKeyEvent` — includes `showMoreDialog`; (4) `PlayerRuntimeControllerPlaybackEvents.kt:988-1002` `scheduleHideControls` guard — includes `showMoreDialog` and `showStreamInfoOverlay`; (5) `:1101-1104` `schedulePauseOverlay` `anyPanelOpen`; (6) `PlayerScreen.kt:1189-1215` controls/PostPlay/clock `AnimatedVisibility` conditions; (7) `PlayerScreen.kt:1079-1083` `isSkipIntroCanFocus` gate. Lists (2), (3), (4) already disagree about which overlays block what.
Impact: Forgetting one site produces exactly the churn class the brief forbids: controls auto-hiding under an open dialog, D-pad escaping a dialog onto the controls bar or Skip Intro, Back skipping a dialog level, or the pause overlay appearing over the consent dialog. With three new UI surfaces the omission probability compounds.
Fix: Introduce a single derived helper (e.g. `PlayerUiState.anyModalOverlayOpen: Boolean` and a `PlayerModal` enum/list) computed in one place, and replace all seven lists with it before adding the generation surfaces; alternatively extend a shared `isXxxCanFocus(anyOverlayOpen)` policy function with unit tests (the `SkipIntroVisibilityRulesTest` pattern already proves this style works).
Priority: P1
Confidence: high

### F5 — Empty/error option rail has no focusable actions; lookup failure is invisible to the UI

Finding: When a language has no options, the option rail renders a non-focusable text card. The brief requires the empty state to offer **Retry lookup** and **Generate dialogue captions**, and requires the generation card to remain reachable after lookup failure — none of that exists, and `PlayerUiState` has no subtitle-lookup-error field to drive it.
Evidence: `SubtitleSelectionOverlay.kt:717-719` — `options.isEmpty() -> OverlayEmptyCard(text = stringResource(R.string.subtitle_no_addon))` (plain `Text` in a Box, no focusable node); `PlayerUiState.kt` contains no lookup-error state (only `OnRetry` for playback retry at `:318`); the brief's Milestone 0 explicitly calls the current lookup failures "swallowed".
Impact: On lookup failure the user sees "no addon subtitles" with no way to retry or to reach generation — the primary entry point for the whole feature is unreachable in exactly the scenario it was designed for, and the "provider failure/offline/429/expired key expose recoverable focused actions" gate cannot be met.
Fix: Add a structured lookup-result state (loading/partial/error with error kind) to `PlayerUiState`; when options are empty render two focusable action cards with stable keys (`action:retry_lookup`, `generate:beta`) in fixed order, Retry first; keep initial focus on the language rail (existing rule) so focus never jumps due to an async error arriving.
Priority: P1
Confidence: high

### F6 — Overlay dismissal restores focus only to play/pause; dialogs layered over the overlay have no restore-into-overlay path

Finding: Back from the subtitle overlay deterministically lands on play/pause (250 ms delay). But the new setup/consent dialog opens *on top of* the still-open overlay, and nothing in the current code restores focus back into a specific overlay rail when a layered dialog closes.
Evidence: `PlayerScreen.kt:437-467` — the only post-overlay focus restore is `playPauseFocusRequester.requestFocus()` after `delay(250)`; the one existing restore-flag pattern is `restoreStreamInfoFocus` (`:168`, `:492-496`) for the stream-info overlay only; the subtitle overlay scaffold is non-focusable (`captureKeys = false`, `SubtitleSelectionOverlay.kt:370`), so when a dialog window's focused node is disposed, focus does not fall back inside the overlay.
Impact: Dismissing the consent dialog (Cancel/Back) leaves focus on the player container: the next Back press closes the whole subtitle overlay instead of doing nothing/returning to rails, and D-pad input goes dead or escapes — a direct "Back behavior" gate failure and a focus-churn regression users will hit on every cancelled consent.
Fix: Mirror the `restoreStreamInfoFocus` pattern: when the setup/consent/switch dialog dismisses while `showSubtitleOverlay` is true, set a restore flag and re-request focus on the `generate:beta` card via the overlay's existing `requestOptionFocus` token path (which already suppresses duplicates and verifies the language key).
Priority: P2
Confidence: high

### F7 — Skip-intro focus-escape gate covers only the subtitle overlay; the audio overlay already leaks

Finding: `isSkipIntroCanFocus` takes a single `subtitleOverlayVisible` flag. The audio overlay is not passed, so D-pad down past the last audio card can escape onto the Skip Intro button today; every new overlay surface (generate card rails, setup dialog) must extend this gate or reproduce the same leak.
Evidence: `SkipIntroVisibilityRules.kt:40-43` — `isSkipIntroCanFocus(subtitleOverlayVisible: Boolean): Boolean = !subtitleOverlayVisible`; sole call site `PlayerScreen.kt:1081` passes only `uiState.showSubtitleOverlay`; `AudioSelectionOverlay.kt:153-157` uses the same non-focusable scaffold (`captureKeys = false`); the regression is documented as issue #2874 in the KDoc.
Impact: With the generate flow adding up to three new focusable surfaces at the same z-layer as the skip button, an ungated skip button gives D-pad an escape hatch out of every one of them — "D-pad and RTL traversal are deterministic across all rails/dialogs" fails intermittently (only when a skip interval is active).
Fix: Change the policy function to accept `anyModalOverlayOpen: Boolean` (or the modal list from F4), pass it from `PlayerScreen`, and extend `SkipIntroVisibilityRulesTest` with the audio-overlay and generate-dialog cases. This is a pure policy change with an existing test file.
Priority: P2
Confidence: high

### F8 — TalkBack: selection and status are color-only in the overlay; no live region for generation states; no labeling for the capture indicator

Finding: The option/language cards convey "selected" purely via container color and an unlabeled check icon; there is no `stateDescription`, no `Role.Checkbox` semantics, no live region for status changes, and nothing yet labels the persistent "Audio being transcribed" indicator. The repo already has the right primitives (`cd_selected`, `disabled()` semantics) but the player overlay does not use them.
Evidence: `SubtitleSelectionOverlay.kt:1164` — selected-state `Icon(Icons.Default.Check, contentDescription = null)`; contrast `SettingsDesignSystem.kt` `SettingsSingleChoiceDialog` which uses `contentDescription = stringResource(R.string.cd_selected)`, and `PlayerScreen.kt:3370` / `SubtitleStyleSidePanel.kt:426` which also use `cd_selected`; grep of the overlay file shows zero `semantics {}` blocks and no `liveRegion` anywhere in the player package.
Impact: TalkBack users get no announcement of which subtitle option is active, will hear nothing when generation moves Preparing → Listening → Failed, and cannot discover that program audio is being transcribed — failing "TalkBack never announces secret content and labels program-audio capture accurately" and the stable-state-label requirement (labels must be announced, not just painted).
Fix: Add `stateDescription`/selected semantics to option and language cards (reuse `cd_selected` for the check icon), mark the status text node as `LiveRegionMode.Polite` announcing only the stable label set, give the transcription indicator an accurate non-focusable `contentDescription`, and add a semantics-tree canary assertion that provider keys/transcript text never appear.
Priority: P2
Confidence: high

### F9 — RTL key remapping is duplicated inline in seven controls; the scaffold scrim gradient is not mirrored

Finding: Every focusable control in the overlay re-implements "which physical D-pad key means left/right" inline, instead of a shared helper; `RtlKeyUtils` contains exactly one unrelated helper. The scaffold's horizontal scrim gradient is drawn left-to-right regardless of layout direction.
Evidence: `SubtitleSelectionOverlay.kt` — inline `val moveLeftKey = if (isRtl) KEYCODE_DPAD_RIGHT else KEYCODE_DPAD_LEFT` blocks in `SubtitleStyleRail`, `SubtitleOptionCard`, `SubtitleLanguageCard` (inverted), `StepperButton`, `ToggleChip`, `ColorChip` (seven occurrences total); `RtlKeyUtils.kt:8-14` — only `getClearHistoryDpadKey`; `PlayerOverlayScaffold.kt` `drawWithCache` — `Brush.horizontalGradient(listOf(Black 0.88, Transparent))` with no layout-direction mirroring.
Impact: Each new focusable surface (setup dialog fields, Retry/Stop actions, switch prompt) must re-copy this logic; a single miss means the first D-pad press in RTL exits the dialog or moves focus to the wrong rail — a deterministic-traversal gate failure that only reproduces in RTL locales.
Fix: Extract one shared mapping (e.g. extend `RtlKeyUtils` with `overlayHorizontalMoveKeys(isRtl)`) and use it in all overlay controls plus the new surfaces; mirror the scrim gradient by layout direction. Unit-test the mapping like `RtlKeyUtilsTest` does for the existing helper.
Priority: P2
Confidence: medium

### F10 — 400-line contract and zero player-overlay Compose tests: new UI must land in new files with a test-tag skeleton

Finding: `SubtitleSelectionOverlay.kt` is 1,912 lines and `PlayerScreen.kt` is 3,495 lines; the brief forbids growing oversized files. Meanwhile the only Compose UI test in the repo is `TrackingSettingsOverviewTest` (settings), and it demonstrates the exact test-tag + `FocusRequester` + `assertIsFocused` pattern needed — but no player overlay has any test tags at all.
Evidence: `wc -l` — `SubtitleSelectionOverlay.kt` 1912, `PlayerScreen.kt` 3495; brief contract "All source files remain at or below 400 lines. Existing oversized files must be changed through new focused files/extensions rather than made larger"; test inventory: `app/src/androidTest/.../TrackingSettingsOverviewTest.kt` uses `createComposeRule`, `onNodeWithTag(TrackingSettingsTestTags.*)`, `assertIsFocused` (tags defined at `TrackingSettingsScreen.kt:670-679`); grep shows 0 `testTag` usages in `SubtitleSelectionOverlay.kt` or `PlayerScreen.kt`.
Impact: Without test tags and new files, the required "Compose focus and end-to-end tests" (Milestone 6) cannot be written against the overlay at all, and any attempt to add them by growing the two oversized files violates the size contract.
Fix: Create new focused files (`SubtitleGenerationCard.kt`, `SubtitleGenerationSetupDialog.kt`, `PlayerTranscriptionIndicator.kt`, `PlayerEngineSwitchPromptDialog.kt`), a `SubtitleOverlayTestTags` object mirroring `TrackingSettingsTestTags`, and the androidTest class following `TrackingSettingsOverviewTest`. PlayerScreen wiring stays minimal (parameters + one condition via F4's helper).
Priority: P2
Confidence: high

### F11 — Exo→MPV "Switch player and continue": position-preserving switch exists, but the prompt must own keys, restore focus, and survive auto-failover

Finding: The manual engine switch already preserves position and clears all overlay flags, and a non-focusable switching indicator exists — so a focusable "Switch player and continue" prompt is feasible. It must be its own key-consuming dialog with explicit initial focus and an explicit decline path, and it must dismiss itself when the host force-closes overlays (auto engine failover, stream switch, episode switch).
Evidence: `PlayerRuntimeControllerEngineFailover.kt:64-130` — `switchInternalPlayerEngineManually()` sets `pendingSeekPosition = currentPosition`, clears `showSubtitleOverlay/showAudioOverlay/...` and shows `showPlayerEngineSwitchInfo` (rendered as a non-focusable `PlayerEngineSwitchIndicator`, `PlayerScreen.kt:1246-1252` area); auto-failover also force-closes the overlay (`PlayerRuntimeControllerEngineFailover.kt:101`, `PlayerRuntimeControllerStreams.kt:111/137/1545`).
Impact: If the prompt is an inline composable under the overlay scaffold (non-focusable, `captureKeys=false`), D-pad falls through to the rails/skip button and Back closes the wrong layer; if it is a dialog window that ignores host state, it stays open over a player that already switched engines — both are focus-churn and truthfulness failures the brief calls out ("MPV shows Switch player and continue, never fake live support").
Fix: Implement the prompt as a `NuvioDialog` (window) that observes the overlay/uiState flags and dismisses on force-close; initial focus on **Continue** (single forward action), Back = decline returning focus to `generate:beta` via F6's restore flag; on confirm, delegate to the existing switch path and let the session coordinator restart capture under Exo with a new epoch.
Priority: P1
Confidence: medium

### F12 — Settings Integration hub extension is mechanical and low-risk; the hand-built requester map is the only friction

Finding: "AI media providers" fits the existing hub exactly: a new `IntegrationSettingsSection` enum value, one hub `SettingsActionRow` with a stable LazyColumn key appended after Anime-Skip, one dedicated `FocusRequester`, the generic `BackHandler` back-to-Hub, and a device-auth dialog mirroring `TraktAccountDialog` (code + Cancel + focused Retry already proven by an androidTest).
Evidence: `SettingsScreen.kt:110-116` — `IntegrationSettingsSection { Hub, Debrid, Tmdb, MdbList, AnimeSkip }`; `:960-976` — generic `BackHandler(enabled = selectedSection != Hub)` and per-section focus re-request `LaunchedEffect`; `:997-1030` — hub items with stable keys `integration_hub_debrid` … `integration_hub_animeskip`; `:296-307` — `contentFocusRequesters` hand-built map (note: no entries for TRACKING/DEBUG, silently falling back to `moveFocus`); `TrackingProviderDialogs.kt:580-583` — `backFocusRequester` + delayed `requestFocus()` initial-focus pattern; `TrackingSettingsOverviewTest.kt` — device-auth retry/code/cancel assertions.
Impact: The provider-connection UI can be added with essentially zero focus-churn risk if it follows the section pattern; the risk is only that each new category multiplies the plumbing (enum value, requester var, LaunchedEffect branch, hub item, detail-pane branch) and a missed branch degrades to a silent `moveFocus` fallback rather than a crash — churn-adjacent but recoverable.
Fix: Follow the section pattern verbatim with key `integration_hub_ai_media` and a dedicated `integrationAiMediaFocusRequester`; consider deriving the hub item keys and focus branches from one list so future sections cannot drift.
Priority: P3
Confidence: high

---

## Required test matrix (exact tests to add — no patches in this review)

Stable-key (JVM unit, new `SubtitleSelectionOverlayRailTest`):
1. `buildOptionRailItems appends constant generate id after internal and addon items without reordering existing ids`
2. `generate option id is constant across every GenerationStatus value and provider change`
3. `language rail keys and order are unchanged when generation becomes available`
4. `addonSubtitleOptionId remains url-derived for addon options only` (pins existing behavior)

D-pad (androidTest, `createComposeRule`, mirroring `TrackingSettingsOverviewTest`):
5. `captions key opens overlay with focus on selected option else language rail` (existing `overlay_open` rule)
6. `dpad right from language rail focuses memorized option else generate card when option list is empty`
7. `dpad down past last option card does not escape to skip intro while any modal overlay is open` (F4/F7 policy + UI)
8. `cue storm does not move focus or scroll` — fake provider emitting 10 Hz for 30 s; assert focused tag and `firstVisibleItemIndex` stable at 1 s intervals
9. `setup dialog field traversal is deterministic in listed order audio, spoken, subtitle, provider, cap, start`
10. All D-pad tests repeated under `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl)` with mirrored physical keys

Back:
11. `back from overlay keeps generation session running and restores controls focus to play pause within 250 ms`
12. `back from setup dialog returns focus to generate card and creates no session`
13. `back from consent returns to setup with start focused`
14. `back from switch prompt declines, stays on MPV, and returns focus to generate card`
15. `stacked back from setup closes overlay, not the player`

TalkBack (semantics assertions):
16. `option and language cards expose selected state via stateDescription or cd_selected`
17. `generation status node is a polite live region announcing exactly the stable labels Preparing Listening Repositioning Paused Live Complete Failed`
18. `audio being transcribed indicator is non focusable with accurate contentDescription`
19. `semantics tree contains no provider key or transcript canary text in any generation state`

RTL:
20. `shared overlay move-key helper returns mirrored keys for every rail and dialog control` (extends `RtlKeyUtilsTest` style)
21. `rtl traversal reproduces the ltr path with physical keys mirrored` (test 5-9 under Rtl)

Error/retry:
22. `lookup failure empty state exposes retry lookup and generate actions in stable order with retry first`
23. `provider failed state exposes focused retry and stop actions`
24. `offline, 429 and expired key map to distinct recoverable copy with a focused retry action`
25. `retry recreates the session with a new epoch and no duplicate grant` (fake provider/coordinator)
26. `engine failover or stream switch dismisses dialogs first and ends generation truthfully`

Settings:
27. `ai media row is appended after anime skip with stable key integration hub ai media`
28. `back from ai media section restores hub focus to the ai media row`
29. `provider device auth error state focuses the retry action` (mirrors `TraktAccountDialog` test)

## Focus state model

Focus is a single active target identified by a **stable key**. Rail identity keys: language `lang:<key>`, option `opt:<id>` (generation = `opt:generate:beta`), style `style:<key>`, setup field `setup:<field>`, consent `consent:allow|cancel`, switch prompt `switch:continue|stay`, settings hub `hub:<section>`, provider dialog `prov:<state-action>`. Directions in the table are **logical**; the physical key is mirrored in RTL per F9.

Invariants (hold for every row):
- **I1** Identity keys of rendered options never change while a surface is open; lists are append-only.
- **I2** Cue arrivals, status transitions (Preparing/Listening/Repositioning/Paused/Live/Complete), lookup results and cost updates never move focus or scroll; only user input or explicit dialog open/close does.
- **I3** Back from any generation dialog returns to `opt:generate:beta`; back from the overlay returns to controls; **back never cancels an active session** (Stop/decline are explicit).
- **I4** Every focus move names an explicit stable-key target; no default focus search is permitted to resolve a move.
- **I5** Rail-to-rail moves use the existing token machinery (`requestOptionFocus`/`requestFocusAfterFrames`) with duplicate-pending and already-focused suppression.
- **I6** If a named target is absent (language emptied, options cleared), fall back to `lang:<off>` then the first non-off language — never to a node outside the overlay.

Deterministic transition table:

| # | Current state | Event | Next state | Focus target | Guards / invariants |
|---|---|---|---|---|---|
| 1 | `BG` (playback, controls hidden) | KEYCODE_CAPTIONS or CC control OK | `SUB.*` | selected option if any (`opt:<selectedId>`), else `lang:<selectedLanguage>` | existing `overlay_open` rule; overlay snapshot taken here |
| 2 | `SUB.L(k)` | logical RIGHT (OK on `lang:off` excluded) | `SUB.O` | `opt:<optionFocusMemory[k]>` else first option of k, else `opt:generate:beta` | option rail visible iff k ≠ off; I5 |
| 3 | `SUB.L(k)` | OK | `SUB.L(k)` | unchanged `lang:k` | selection updates rails append-only; if k = off, option+style rails hidden |
| 4 | `SUB.O(id)`, id ≠ generate | OK | `SUB.O(id)` | unchanged | selects track/addon (existing); reveals style rail; I1 |
| 5 | `SUB.O(generate)`, no session | OK | `GEN.SETUP` | `setup:audio` (first field) | dialog window above overlay; overlay stays mounted; NuvioDialog swallows opening KEY_UP |
| 6 | `GEN.SETUP(f)` | BACK or Cancel | `SUB.O` | `opt:generate:beta` (F6 restore flag) | no session created; I3 |
| 7 | `GEN.SETUP(start)` | OK | `GEN.CONSENT` | `consent:allow` | consent copy per brief; no implicit always-allow |
| 8 | `GEN.CONSENT` | BACK | `GEN.SETUP` | `setup:start` | no session; I3 |
| 9 | `GEN.CONSENT` | OK on Allow | `SUB.O` | `opt:generate:beta` | session starts → status Preparing; I2: no focus/scroll change beyond the explicit return |
| 10 | any `SUB.*` / `GEN.*` | cue arrival / status change | unchanged | unchanged | I2; TalkBack polite announcement of stable label only |
| 11 | `SUB.*`, session Failed | (state entry) | unchanged | unchanged unless focused card is `opt:generate:beta` | card content gains Retry/Stop actions, both focusable, stable keys `action:gen_retry`, `action:gen_stop` |
| 12 | Failed, focus `action:gen_retry` | OK | `SUB.O` | unchanged | retry = new epoch, no duplicate grant (test 25) |
| 13 | Failed, focus `action:gen_stop` | OK | `SUB.O` | unchanged | stop is explicit; keep-or-remove finalized cues prompt follows spec |
| 14 | `SUB.L/O/S` | BACK | `CTL` | play/pause after 250 ms | overlay closes; **session continues** (I3); controls auto-hide timer restarted |
| 15 | `SUB.L(k)` | lookup completes | `SUB.L(k)` | unchanged | option list grows append-only; requester map may rebuild but keys stable (I1); scroll preserved |
| 16 | `SUB.L(k)`, lookup failed | (state entry) | `SUB.L(k)` | unchanged | option rail shows `action:retry_lookup` then `opt:generate:beta`; F5 |
| 17 | MPV engine active, `SUB.O(generate)` | OK | `SWITCH.MPV` | `switch:continue` | dialog window; never fake live support |
| 18 | `SWITCH.MPV` | OK on Continue | `BG` (switching) | container; controls after load | existing `switchInternalPlayerEngineManually` path preserves `pendingSeekPosition`; capture restarts under Exo with new epoch |
| 19 | `SWITCH.MPV` | BACK | `SUB.O` | `opt:generate:beta` | decline: stays MPV, generation unavailable, truthful state |
| 20 | any `SUB.*`/`GEN.*`/`SWITCH.*` | stream switch, episode switch, auto engine failover, profile switch | `BG` (loading) | container | host force-closes overlays (existing flags); dialogs observe host state and dismiss first; session coordinator revokes grant (no late renders) |
| 21 | `GEN.*` open, playback paused | (state entry) | unchanged | unchanged | status shows Paused; no frame delivery; I2 |
| 22 | `SET.HUB` (settings rail) | OK on Integrations | `SET.DETAIL(integration)` | `hub:debrid` (first row) | existing section pattern |
| 23 | `SET.DETAIL(integration)` | OK on AI media row (`integration_hub_ai_media`, last) | `SET.AI` | first row of AI section (`ai:providers`) | F12; stable item key |
| 24 | `SET.AI` | BACK | `SET.DETAIL(integration)` | `hub:ai_media` (hub entry restore) | generic BackHandler; I4 |
| 25 | `PROV` device-auth connecting | (state entry) | `PROV.awaiting` | `prov:code` | code + cancel visible; mirrors Trakt dialog |
| 26 | `PROV.awaiting` | auth failed | `PROV.error` | `prov:retry` | recoverable, focused retry (test 29) |
| 27 | `PROV.*` | BACK | `SET.AI` | explicit first-row restore (`ai:providers`) | I4; never default search |

Terminal note: rows 1–27 plus invariants I1–I6 are the acceptance surface for the D-pad/Back/TalkBack/RTL gates in the brief; any implementation edge that cannot be expressed as a row of this table is, by definition, a focus-churn defect.
