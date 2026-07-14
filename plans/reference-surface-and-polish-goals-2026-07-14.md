# Reference Surface, Daily Polish & Platform Goals — Goals 113–128 (2026-07-14)

Each goal below is self-contained: context with file/line evidence, the change
to make, and machine-checkable acceptance criteria. Work goals one at a time
with `/goal`. Line numbers are correct as of commit
`63f40462` (2026-07-14, branch `merge-pr-542`, app version 0.4.33 / 4033) and
may drift — search the named symbols.

Goal numbers continue from 112
(`plans/study-experience-settings-and-hardening-goals-2026-07-13.md`, on the
`goals-96-112` branch, ends at Goal 112) because goal numbers are globally
unique across all plan files in this repo — commit messages and AGENTS.md
reference bare goal numbers, so "Goal 120" must always mean exactly one thing.
Numbers 96–112 stay reserved for that plan even though it is not fully merged.

## Asks

1. Make the kanji detail page a real reference surface: the KanjiVG stroke
   data (`res/raw/kanji_strokes.tsv`, ~9.5 MB parsed) is bundled and parsed
   by `writing-core` but rendered only inside the writing pad
   (`DrawingPadView.kt:424-459`); the detail page shows no stroke order, and
   no "confused with" links even though `similar_kanji_pairs` and directional
   wrong-pick counts exist and Goal 87's stats card already renders them.
2. Let the user look up kanji they do not own: Browse searches only
   `kanji_inventory` (`LocalStoreInventory.searchKanjiInventory:297-354`),
   while the bundled dictionary ships 13,108 kanji with meanings, readings,
   grade, radical, and stroke count that are reachable only via exact-glyph
   `lookupKanji`.
3. Daily-touch polish: the reminder notification has no action buttons (plain
   `Notification.Builder`, `ReminderScheduler.kt:278-291`); the self-updater
   discards GitHub release notes (`GitHubReleaseMetadataParser.kt:11-26`) so
   the user never learns what an update changed; and the app's deep
   two-core/ladder/repair model has no in-app explanation anywhere.
4. Practice and insight: recent mistakes are listed on Home but there is no
   zero-stakes way to drill them (games are practice-only and ideal for
   this); `review_log` stores full before/after memory state per review
   (24 columns, `LocalStoreBase.kt:658`) but no surface plots a kanji's
   memory strength over time.
5. Scheduler-adjacent extensions recorded as future work: the
   okurigana-choice variant of `kanji_reading`
   (`plans/reading-rungs-goals-2026-07-09.md:282-283` — "natural future
   extension … KANJIDIC `.`-split already provides the data") and the
   tempered D2 graduation-history variant
   (`docs/ladder-and-srs-system.md:1052-1062` — "Deferred pending a
   *tempered* variant; production behavior unchanged, harness retained").
6. Widget and platform: the Glance widget is one fixed layout with default
   `SizeMode.Single`; the manifest has no predictive-back opt-in; no Baseline
   Profile exists anywhere; study choice/done/game composables carry zero
   semantics (`MainActivityStudyChoiceCompose.kt` — 0 contentDescription /
   0 semantics across 11 interactive references); and nothing anywhere uses
   `WindowSizeClass` (zero grep hits) despite `resizeableActivity="true"`.

## Base state (as of this plan)

- HEAD `63f40462`, app version 0.4.33 / 4033 (`gradle/libs.versions.toml:9-10`,
  applied at `app/build.gradle.kts:60-61`). `minSdk 26`, `targetSdk 36`
  (`app/build.gradle.kts:58-59`). `isMinifyEnabled = false`
  (`app/build.gradle.kts:103` — Goal 111 pending on the sibling branch).
- `LocalStoreSchema.DB_VERSION == 32` (`app/.../data/LocalStoreSchema.kt:7`).
  **No goal in this plan needs a schema migration** — new persistence is
  settings-KV only.
- `STATS_CACHE_FORMAT_VERSION == 10` (`app/.../data/StatsCacheStore.kt:11`).
  No goal in this plan extends the stats snapshot.
- Goals 82–95 (`plans/repair-loop-and-stats-goals-2026-07-10.md`) are landed
  on `main`: `ConfusionInsightPolicy` exists
  (`core/.../ConfusionInsightPolicy.kt:17` `topPairs`), the heatmap chart and
  policy exist (`charts/KaniCharts.kt:143-158` `KaniHeatmapChart`;
  `core/.../ReviewHeatmapPolicy.kt:20-27` `build`), the Glance widget exists,
  and `scheduler_fsrs_weights` is live.
- Goals 96–97 (mnemonics) and 103 (settings hero deletion) are on `main`;
  Goals 98–102 and 105–112 are pending on branch `goals-96-112` (see
  Carry-over below).
- **Browse detail** (`MainActivityHomeBrowseDetailCompose.kt:41-72` renders
  hero → identity/badges → reason panel → local inventory panel → mnemonic
  editor → actions → recovery timeline → examples): no stroke-order section,
  no similar-kanji section, and nothing on the page navigates to another
  kanji. Kanji-to-kanji navigation exists only from search rows
  (`MainActivityHomeBrowseSearchCompose.kt:145`
  `activity.renderDetail(item.kanji, true, browseQuery)`). Model:
  `BrowseDetailScreenModel` (`MainActivityHomeBrowseDetailModel.kt:51-61`);
  loader `MainActivityHomeBrowseDetail.renderDetail:55-115` is already an
  async route (`store.timelineForKanji` at `:60`, mnemonic at `:64`).
- **Browse search** queries only `kanji_inventory.search_text LIKE`
  (`LocalStoreInventory.kt:317`, limit 300 at `:337-338`), with the
  similar-kanji-only `EXISTS` filter (`:321-327`) and studied checkboxes.
  Query parsing: `KanjiInventorySearchQuery.parse`
  (`core/.../KanjiInventorySearchQuery.kt:28-33`).
- **Dictionary**: `DictionaryLookup` exposes `lookupKanji(literal)`,
  `kanjiCount()`, `jitenRanks()` only (`dictionary-core/.../DictionaryLookup.kt:10-14`)
  — no search API. `KanjiEntry` carries meanings, on/kun/nanori readings,
  `strokeCount`, `grade`, `radical`, `kanjidicFrequency`, `jitenRank`
  (`:66-118`). App-side `DictionaryStore` copies the asset with SHA-256 +
  atomic replace (`data/DictionaryStore.kt:300-332`), opens one shared
  read-only connection (`:161-169`), and LRU-caches 512 entries (`:230`).
  Kun readings retain the KANJIDIC `.` okurigana split (verified:
  教 → `おし.える`/`おそ.わる`). Process-wide warm cache:
  `AssetWarmupCache.dictionaryLookup` (`AssetWarmupCache.kt:50-58`).
- **Stroke guides**: `StrokeGuideParser.parse(Reader): Map<String, StrokeGuide>`
  (`writing-core/.../StrokeGuideParser.kt:18-36`); `StrokeGuide.strokes:
  List<InkStroke>`, points are normalized 0..1 floats
  (`InkPoint.kt:5-10`; renderer scales by view size,
  `DrawingPadView.kt:449-452`). App cache: `MainActivityBase.strokeGuide(kanji)`
  (`MainActivityBase.kt:563-565`; ~9.5 MB parse warning at `:567-572`),
  warmed at startup (`MainActivityStartup.kt:163`) and on study entry
  (`MainActivityStudy.kt:192`). The only renderer is the writing pad
  (`DrawingPadView.kt:424-459`, numbered start markers `:524-539`).
- **Reminders**: notification built with `android.app.Notification.Builder`
  (`ReminderScheduler.kt:278-291`), channel `kani_study_reminders` (`:39-40`),
  content intent = plain `MainActivity` launch with **no extras**
  (`:270-277`, request code 2701), swipe-dismiss intent (`:302-312`, request
  code 2703), single slot `NOTIFICATION_ID = 2702` (`:43-45`; replace-in-place
  comments `:190-194`, `:285-288`). Zero action buttons. Deep-link machinery
  exists and is consumed: `EXTRA_OPEN_STUDY` (`MainActivityBase.kt:661`) →
  study route (`MainActivityStartup.kt:105-110`); the widget already uses it
  (`widget/KaniWidget.kt:105`). Anti-spam invariants: 90-minute min gap +
  never re-notify an unchanged due-set signature
  (`core/.../ReminderThrottlePolicy.kt:11-16,30`), hard per-day fuse
  `MAX_NOTIFICATIONS_PER_DAY = 2` (`core/.../ReminderReviewBatchPolicy.kt:10-11`),
  quiet hours default 22:00–08:00 (`core/.../ReminderAntiSpamPolicy.kt:15-16`).
- **Updater**: `GitHubReleaseMetadata(tagName, htmlUrl, assets)` has **no
  release-notes field**; the parser extracts only `tag_name`, `html_url`,
  asset name/url and drops `body` (`update-core/.../GitHubReleaseMetadataParser.kt:11-26`).
  Update state lives in settings keys `auto_update_*`
  (`data/LocalStoreBase.kt:675-680`; `AutoUpdateStatus` `:455-488`). Current
  version read from `BuildConfig.VERSION_NAME` (`update/GitHubUpdater.kt:56`).
- **Games**: modes `MEANING_POP`, `READING_RUSH`, `CONFUSABLE_CLASH`
  (`core/.../KanjiGameEngine.kt:9-18`); `nextQuestion(mode, rows, inventory,
  pairs, random)` (`:55-70`); a mode is available iff a question can be built
  (`availableModes`, `:41-53`); max 4 choices (`:141`). Data:
  `MainActivityGames.gameData():251-257` = `activeDashboardRows()` +
  `searchKanjiInventory("")` + `allLocalSimilarPairs()`; round size 10
  (`MainActivityGames.kt:268`). Games write nothing to `review_log` or the
  scheduler.
- **Recent mistakes**: `RecentMistakePolicy.mistakeRatings() == [AGAIN, HARD]`
  (`core/.../RecentMistakePolicy.kt:9-12`); store query over `review_log`
  ordered `reviewed_at DESC` (`data/StudyStatsQueries.kt:46-70`), exposed as
  `store.recentMistakes(limit)` (`data/LocalStoreStudy.kt:783-785`).
- **review_log**: 24 columns including `memory_before`, `memory_after`,
  `scheduler_state_before_json`, `scheduler_state_after_json`, `task_type`,
  `core_skill`, `failure_cause` (`LocalStoreBase.kt:658`), indexed
  `idx_review_log_kanji_reviewed(kanji, reviewed_at)`
  (`data/LocalStoreTableCreator.kt:24`). **No store method returns raw rows
  for one kanji** — per-kanji access today is aggregate-only
  (`StudyStatsQueries.repairEvidenceSummary:455-481`,
  `KanjiImpactReportStore.kt:157-161`). The detail timeline reads
  `kanji_timeline_events`, not `review_log`
  (`LocalStoreInventory.timelineForKanji:480-515`).
- **Charts**: `KaniLineChart(chart: ProgressLineChartState, colors, modifier)`
  (`charts/KaniCharts.kt:68-80`), `KaniDonutChart` (`:113-141`),
  `KaniHeatmapChart(grid, accent, modifier)` (`:143-158`).
- **Widget**: `KaniWidget` (Glance) with default `SizeMode.Single` (no
  override), snapshot = `state / dueCount / streakDays / nextUsefulAtMillis`
  (`widget/KaniWidgetSnapshotLoader.kt:11-22`), loader applies
  `ReminderEligibilityPolicy` (decision D-S6) and fails soft to `NOT_SET_UP`
  (`:29-73`). Provider XML: `minWidth 250dp`, `minHeight 72dp`,
  `resizeMode="horizontal"`, hourly fallback `updatePeriodMillis=3600000`
  (`res/xml/kani_widget_info.xml:1-9`). Event-driven refreshes after sync
  (`sync/ManualSyncEngine.kt:65`), study commit
  (`MainActivityStudyReviewFlow.kt:71`), done screen
  (`MainActivityStudyDoneActions.kt:122`), and daily reminder evaluation
  (`reminders/ReminderReceiverDailyActions.kt:11`). No configuration
  activity exists.
- **Manifest / back**: single activity `.MainActivity`
  (`AndroidManifest.xml:32-44`), `resizeableActivity="true"` (`:28`),
  `tools:targetApi="tiramisu"` (`:31`), **no
  `android:enableOnBackInvokedCallback`**, no `screenOrientation` locks.
  Back handling already uses the modern dispatcher — a single
  `OnBackPressedCallback` registered at `MainActivityBase.kt:262-266,363`;
  no deprecated `onBackPressed()` override exists.
- **Shell**: every route renders inside one `verticalScroll` Box
  (`MainActivityShell.kt:184-189`); bottom nav rendered at `:208-212`,
  hidden during IME/study conditions (`:206-207`). Zero
  `WindowSizeClass`/`calculateWindowSizeClass` usage in the repo.
- **Static settings page precedent**: licenses route
  (`NAV_SETTINGS_LICENSES_ROUTE = "settings/display-data/licenses"`,
  `MainActivityBase.kt:686`), wired in
  `MainActivitySettings.kt:196-206` via
  `MainActivitySettingsReferenceData.referenceDataScreenModel():63-77`;
  route constants live at `MainActivityBase.kt:675-686` with parent-route
  back logic at `:688-700`. Copy is centralized EN+JA via
  `localizedText(english, japanese)` (e.g.
  `core/.../SettingsSectionTextCopy.kt:36`).
- **Accessibility**: zero `contentDescription` and zero `semantics` in
  `MainActivityStudyChoiceCompose.kt` (11 interactive references),
  `MainActivityStudyDoneActionsCompose.kt` (20),
  `MainActivityStudyActionButtonCompose.kt` (8),
  `MainActivityGamesRoundCompose.kt` / `MainActivityGamesResultCompose.kt`
  (4 each). `MainActivityStudyChoiceGridCompose.kt` has `stateDescription`
  only.
- **D2 experiment state**: graduation seeds FSRS at
  `ReviewTransitionEngine.graduateToReview`
  (`core/.../ReviewTransitionEngine.kt:384-399`) →
  `LatestFsrsAdapter.initialReview` (`core/.../LatestFsrsAdapter.kt:12-34`;
  new-learning branch `:20-27`). The Goal 74 naive experiment is REJECTED
  with harness retained (`GraduationHistoryExperimentTest`; write-up
  `docs/scheduler-fsrs-correctness-lab-report.md:115-137`; open decision D2
  `docs/ladder-and-srs-system.md:1052-1062`).
- **Reading-rung data**: `KanjiReadingChoicePlanner` (Goal 78 rung,
  `MIN_CHOICE_COUNT=2`, `MAX_CHOICE_COUNT=4`,
  `core/.../KanjiReadingChoicePlanner.kt:21-23`) builds from
  `kanji_reading_usage` (PK `(kanji, reading, note_id)`,
  `data/LocalStoreTableCreator.kt:114-125`) and `kanji_reading_pool`
  (`:138-144`), rebuilt by `LocalStoreKanjiReadingMaintenance` from the
  dictionary (`data/LocalStoreKanjiReadingMaintenance.kt:16-19`).

## Carry-over (Goals 82–112)

| Range | Plan | Status |
|---|---|---|
| 82–95 | `plans/repair-loop-and-stats-goals-2026-07-10.md` | Landed on `main`; Goal 94 live gate `OK (62 tests)` re-validated 2026-07-10 |
| 96–97, 103 | `plans/study-experience-settings-and-hardening-goals-2026-07-13.md` | Landed on `main` via PRs #536/#539 and commit `3eac3c17` |
| 98–99 (session recap), 100–101 (parking), 102 (vocabulary unlock), 104 (settings declutter), 105–106 (runtime/recreation), 107–109 (vararg freeze), 110–111 (signing/R8), 112 (consistency pass) | same plan, branch `goals-96-112` | **Pending** — that plan remains the authority for those goals; this plan does not renumber or restate them |

This plan is a **sibling queue** to the pending Goals 98–112 and is written to
be workable in any interleaving with them; every point of contact is called
out below.

## Cross-plan interactions (Goals 98–112, pending)

- **Browse detail screen:** Goals 113, 114, 115, and 120 here add sections to
  `BrowseDetailScreenModel`/`BrowseDetailScreen`; Goal 101 (parking chip +
  action) touches the same screen. All additions are independent optional
  model fields rendered as separate sections — land in any order; each goal
  adds its own field with a `null` = absent contract, never reordering
  existing sections.
- **Done screen:** Goal 119's result CTA and Goals 98–99's recap both touch
  the done/games surfaces but different files (`MainActivityGames*` vs
  `MainActivityStudyDoneActions*`). No shared edit.
- **Stats surface:** nothing in this plan edits
  `ProgressAnalyticsDashboardScreen` or the stats snapshot — no
  `STATS_CACHE_FORMAT_VERSION` bump anywhere here, so any interleaving with
  Goal 102 composes.
- **R8 (Goal 111):** the Baseline Profile (Goal 125) is generated from the
  release variant. If Goal 111 lands first, regenerate the profile against
  the minified build; if after, regenerate as part of Goal 111's smoke. The
  checked-in profile file is the contract either way.
- **Runtime holder (Goal 105):** Goal 115's dictionary search reuses the
  existing `AssetWarmupCache.dictionaryLookup` process cache
  (`AssetWarmupCache.kt:50-58`), which is already process-scoped — no new
  per-activity state is introduced, so Goal 105 can absorb it mechanically.
- **Vararg freeze (Goals 107–109):** new core models introduced here
  (`StrokeOrderDiagramPolicy`, `ReminderSnoozePolicy`, `WhatsNewCardPolicy`,
  `KanjiMemoryHistoryPolicy`, okurigana planner models) must use named-field
  data classes with no positional vararg telescoping from day one, so the
  freeze goals never have to touch them.

## Design

### Decisions of record

- **D-P1 — Browse detail is the reference surface; one stroke dataset.** The
  stroke-order diagram reuses the `writing-core` `StrokeGuide` data verbatim
  (normalized 0..1 polylines) — no second stroke dataset, no SVG re-import.
  The diagram is a **static cumulative panel grid** (stroke N highlighted,
  strokes 1..N-1 dimmed, start-dot on the current stroke), not an animation:
  deterministic output is screenshot-testable and needs no clock, and the
  writing pad already covers the "watch it drawn" need via hints.
- **D-P2 — Dictionary-wide search is read-only and additive.** Inventory
  search stays the default scope; the "All kanji" scope never creates study
  items, never touches the scheduler or seeder, and renders non-inventory
  kanji on a reduced read-only detail page. `DictionaryLookup` gains an
  `open fun searchKanji(...)` with a default empty implementation so
  `dictionary-core` remains backward compatible for every existing subclass
  and fake.
- **D-P3 — Notification actions preserve every anti-spam invariant.**
  "Study now" reuses the proven `EXTRA_OPEN_STUDY` deep link. "Snooze 1h"
  cancels the slot and re-arms through the normal evaluation path: the
  re-post re-runs full eligibility (never re-shows a stale count), counts
  against `MAX_NOTIFICATIONS_PER_DAY`, and respects quiet hours. The single
  notification slot 2702 is unchanged. New PendingIntent request codes are
  2704 (study action) and 2705 (snooze action) — the reserved-range table
  gains them.
- **D-P4 — What's-new is updater-captured and fail-open.** Release-notes
  capture happens during the normal update check/download and may never
  block, delay, or fail an update. The Home card shows once per installed
  version, only when notes for exactly `BuildConfig.VERSION_NAME` were
  captured; dismissing stamps a seen-version settings key. Sideloaded
  updates simply show no card (no network fetch at render time).
- **D-P5 — Games stay practice-only.** Miss Sweep (Goal 119) reads recent
  mistakes but writes nothing: no `review_log` rows, no scheduler mutation,
  no learning repeats, no second queue. This re-states the AGENTS.md
  invariant for the one new surface that could tempt violation.
- **D-P6 — The memory chart plots only persisted evidence.** Goal 120
  decodes the same `scheduler_state_after_json` the review commit writes —
  it never re-simulates, extrapolates, or fabricates points (inherits D-S2
  truthfulness). Fewer than 2 decodable points renders the standard empty
  state.
- **D-P7 — Baseline Profile generation never enters the release path.**
  Profile generation requires an emulator, and the release path is
  deliberately emulator-free (`tools/test_release_workflows.py` invariants).
  The profile is generated in the nightly/dispatch instrumented lane or
  locally, **checked in** at `app/src/main/baseline-prof.txt`, and consumed
  at build time. A stale profile is a performance issue, never a correctness
  gate.
- **D-P8 — Large-screen work is layout-adaptive only.** Goal 127 adds
  window-size-class plumbing, a navigation rail at expanded width, and
  content width caps. No NavHost rewrite, no two-pane redesign, no
  tablet-specific screens (matches the sibling plan's out-of-scope).
- **D-P9 — Okurigana is a deterministic variant of the existing
  `kanji_reading` repair task.** No new rung, no new FSRS memory, no ladder
  settings churn, no change to `hasKanjiReading` gating. Given the same
  (kanji, usage word, pool) inputs the card is deterministic; when the
  okurigana form cannot be built the existing reading-choice card renders
  unchanged.
- **D-P10 — D2 stays experiment-first.** Goal 122 extends the retained
  harness with tempered variants and produces a corpus table + lab-report
  write-up. Production behavior changes **only** if a variant is adopted,
  and adoption requires regenerated goldens, the pinned
  `RelearningGraduationDifficultyTest` double-update staying intentional,
  and an AGENTS.md update — otherwise the goal closes with D2 re-deferred
  and the evidence recorded.

### Out of scope (evaluated, deliberately deferred)

- **Stats CSV/JSON export** — evaluated for this plan and deliberately
  excluded by ask; the SAF whole-DB export remains the data escape hatch.
- Stroke-order **animation** with playback controls (D-P1 chooses static
  panels; revisit only if user feedback asks for motion).
- Radical/handwriting **input** for dictionary lookup (text search only;
  the writing pad is a study surface, not a lookup IME).
- Widget **configuration activity** / per-widget options — responsive size
  buckets (Goal 123) cover the need without config-state plumbing.
- Audio/TTS/pitch-accent anywhere (standing non-goal).
- Full tablet two-pane redesign, NavHost migration (D-P8).
- Persisting a recap archive, writing-rung confusion persistence, direct
  provider unsuspend (D-S8), cloud sync — all previously recorded
  deferrals stand.
- New locales beyond EN+JA (copy objects keep the two-locale pattern).

## Cross-cutting reference — shared touch points

| Touch point | Location | Used by goals |
|---|---|---|
| Browse detail model/loader/screen | `MainActivityHomeBrowseDetailModel.kt:51-61`, `MainActivityHomeBrowseDetail.kt:55-140`, `MainActivityHomeBrowseDetailCompose.kt:41-72` | 113, 114, 115, 120 |
| Async route render pattern | `MainActivityHomeBrowseDetail.renderDetail:55-115`, `MainActivitySettings.renderSettingsRouteAsync:222+` | 113–115, 117, 118, 120 |
| Route constants + settings back logic | `MainActivityBase.kt:675-700` | 115, 118 |
| Centralized EN+JA copy (`localizedText`) | `core/.../SettingsSectionTextCopy.kt:36` exemplar; per-surface `*Copy` objects | 113–121, 123, 126 |
| Core convention: every new `:core`/library class needs 100% JaCoCo class coverage wired into `check` | `build-logic/.../kani.kotlin-library-conventions.gradle.kts` | 113–123 |
| Theme screenshot harness + checked-in baselines | `ci/scripts/capture_kani_theme_screenshots.sh`, `screenshots/`, `docs/design/kani-theme-screenshot-harness.md` | 113–120, 123, 126, 127, 128 |
| Games engine + surface | `core/.../KanjiGameEngine.kt`, `MainActivityGames*.kt` | 119 |
| Reminder policies + scheduler | `core/.../Reminder*Policy.kt`, `reminders/ReminderScheduler.kt` | 116 |
| Widget snapshot loader + updater | `widget/KaniWidgetSnapshotLoader.kt`, `widget/KaniWidgetUpdater.kt` | 123 |
| Charts | `app/.../charts/KaniCharts.kt` | 120, 123 |
| Kanji glyph font precedent | `GamesKanjiFontFamily` (`MainActivityGamesDesign.kt:25`, Kaisei Tokumin) | 113, 114, 115 |

Reserved identifier ranges (updated by Goal 116): notification/PendingIntent
request codes 2701–2705 (reminders; 2704–2705 new), 2801–2802 (updates), job
id 3801 (auto-sync), WorkManager JobScheduler range 10000–11000
(`KaniApplication`).

---

## Batch A: The kanji reference surface (Goals 113–115)

Order matters: 113 builds the stroke diagram the read-only detail page (115)
reuses; 114 establishes detail→detail navigation that 115's results list also
relies on.

### Goal 113: Stroke-order diagram on the kanji detail page

**Problem:** KanjiVG stroke data is bundled (`res/raw/kanji_strokes.tsv`) and
parsed into normalized polylines (`StrokeGuideParser.parse`,
`writing-core/.../StrokeGuideParser.kt:18-36`; 0..1 coords per
`InkPoint.kt:5-10`), but the only renderer is the writing pad's guide layer
(`DrawingPadView.kt:424-459`). The detail page — the natural "look this kanji
up" surface — shows nothing about how the character is written, even though
the process-wide guide cache is one call away
(`MainActivityBase.strokeGuide(kanji)`, `MainActivityBase.kt:563-565`).

**Goal:**

- New pure policy `StrokeOrderDiagramPolicy` in `writing-core`
  (next to `StrokeGuide`): input a `StrokeGuide?`; output a
  `Diagram(panels: List<Panel>)` model where panel N carries the polylines
  for strokes 1..N with the Nth flagged `highlighted` and its start point
  exposed for the start-dot marker. Cap panels at
  `MAX_PANELS = 24` with an `omittedStrokeCount` overflow field (rare
  30+-stroke glyphs must not build unbounded grids); null/empty guide →
  empty diagram.
- New composable `KaniStrokeOrderDiagram` in `app` (Canvas per panel:
  dimmed strokes in `onSurface` low alpha, highlighted stroke in the theme
  accent, start dot, panel index label). Panels flow in a wrapping grid of
  fixed-size cells so narrow phones wrap naturally inside the shell's
  vertical scroll.
- Detail integration: add `strokeOrder: BrowseStrokeOrderModel?` to
  `BrowseDetailScreenModel` (`MainActivityHomeBrowseDetailModel.kt:51-61`),
  built on the existing async load path
  (`MainActivityHomeBrowseDetail.renderDetail:55-115`) from
  `home.strokeGuide(displayKanji)`; render as a titled section between the
  identity block and the reason panel. `null` guide → section absent (no
  empty-state card; absence is the correct state for kana/unknown glyphs).
- Copy: EN+JA section title + overflow line ("+N more strokes") in the
  browse-detail copy object, following the `localizedText` pattern.

**Done when (machine-checkable):**

1. `./gradlew :writing-core:test --tests "*StrokeOrderDiagramPolicyTest"`
   passes: empty/null guide, 1-stroke, cumulative flags, start-point
   exposure, 24-panel cap + overflow count, determinism.
2. Compose test: fixture guide renders N panels with the expected test tags;
   absent guide renders no stroke-order section.
3. Screenshot matrix re-captured for the browse-detail route.
4. `./gradlew ciFast` exits 0.

---

### Goal 114: "Confused with" panel + detail-to-detail navigation

**Problem:** The detail page renders no similar-kanji information and nothing
on it navigates to another kanji (verified: kanji→kanji navigation exists
only from search rows, `MainActivityHomeBrowseSearchCompose.kt:145`). Yet the
data is live: `similar_kanji_pairs` via `allLocalSimilarPairs()`
(`data/LocalStoreSimilarKanji.kt:27`), directional 90-day wrong-pick counts
via `choiceWrongPickCounts` (`data/LocalStoreSimilarKanjiData.kt:83-102`),
and `ConfusionInsightPolicy.topPairs` (`core/.../ConfusionInsightPolicy.kt:17`)
already joins them for the stats card (Goal 87) — which tap-throughs INTO
this page while the page itself is a dead end.

**Goal:**

- New core policy `KanjiNeighborPanelPolicy` (JVM-pure): input the target
  kanji, its similar pairs, the directional wrong-pick map, and inventory
  meanings; output ordered neighbor rows (glyph, primary meaning,
  optional per-direction miss counts "you picked it ×N / it stole ×M"),
  confusion-evidenced neighbors first, then remaining visual pairs,
  deterministic tie-break. Reuse `ConfusionInsightPolicy`'s normalization
  rules (kana rows filtered) rather than duplicating them.
- Detail integration: add `neighbors: BrowseNeighborPanelModel?` to
  `BrowseDetailScreenModel`; rows render the glyph in `GamesKanjiFontFamily`
  (`MainActivityGamesDesign.kt:25`) and are tappable →
  `activity.renderDetail(neighborKanji, …)` (the existing search-row
  navigation call shape). Panel placed after the reason panel. Kanji with no
  pairs render no panel.
- Back behavior: detail→detail pushes must return to the previous detail
  (reuse the existing browse back-stack behavior the search entry path uses;
  add coverage).
- Copy: EN+JA panel title + per-direction count lines.

**Done when (machine-checkable):**

1. `./gradlew :core:test --tests "dev.bee.kanjianki.core.KanjiNeighborPanelPolicyTest"`
   passes: ordering (evidenced first), per-direction counts, kana filtering,
   no-pairs → empty, determinism.
2. Compose/model test: neighbor row tap navigates to the neighbor's detail;
   back returns to the origin detail.
3. Screenshot matrix re-captured for the browse-detail route.
4. `./gradlew ciFast` exits 0.

---

### Goal 115: Dictionary-wide lookup — "All kanji" search scope + read-only detail

**Problem:** The bundled dictionary ships 13,108 kanji (meanings, on/kun/
nanori readings, stroke count, grade, radical, Jiten rank —
`DictionaryLookup.KanjiEntry`, `dictionary-core/.../DictionaryLookup.kt:66-118`)
but is reachable only by exact glyph (`lookupKanji`, `:10`). Browse search
matches only `kanji_inventory.search_text`
(`LocalStoreInventory.searchKanjiInventory:297-354`), so a user who meets an
unknown kanji in the wild cannot look it up in Kani at all.

**Goal:**

- `dictionary-core`: add
  `open fun searchKanji(query: String?, limit: Int): List<KanjiEntry>`
  to `DictionaryLookup` with a default empty-list implementation (D-P2 —
  no existing subclass breaks).
- `DictionaryStore`: implement it with a read-only SQL query over the
  `kanji` table — exact-literal match ranked first, then `LIKE` over
  meanings and readings (normalize the query with the same
  `TextUtil.normalizeJapanese` + lowercase rules
  `KanjiInventorySearchQuery.parse` uses, `KanjiInventorySearchQuery.kt:28-33`),
  ordered by `jiten_rank` nulls-last then literal, bounded `limit` (300,
  matching the inventory cap). No schema change — the bundled DB already has
  the columns.
- Browse search UI: add an "All kanji" scope toggle beside the existing
  similar-kanji filter (`MainActivityHomeBrowseSearchCompose.kt:220-238`
  precedent). Inventory scope stays the default. In all-kanji scope, rows
  show glyph + primary meaning + first reading + an "In your deck" marker
  when the glyph exists in inventory; studied checkboxes are hidden (they
  are inventory semantics).
- Read-only detail: for a non-inventory kanji, `renderDetail` builds a
  reduced `BrowseDetailScreenModel` — hero, identity from the dictionary
  entry, a dictionary panel (meanings / on / kun / stroke count / grade /
  Jiten rank), and the Goal 113 stroke-order section. No timeline, no
  actions, no mnemonic editor, no neighbor panel (those are inventory
  surfaces). A single copy line states the kanji is not in the user's deck.
  The scheduler, seeder, and `study_items` are untouched (D-P2).
- Copy: EN+JA scope-toggle label, "in your deck" marker, dictionary panel
  titles, not-in-deck line.

**Done when (machine-checkable):**

1. `./gradlew :dictionary-core:test` passes with new coverage for the
   default `searchKanji` (empty) and any shared normalization helper.
2. App unit test (Robolectric) for `DictionaryStore.searchKanji`:
   exact-glyph first, meaning and reading matches, rank ordering, limit,
   blank query → empty.
3. Compose/model tests: scope toggle switches result source; non-inventory
   row opens the read-only detail; inventory row still opens the full
   detail; read-only detail renders no action buttons.
4. Grep-check: no new call sites into `StudyQueueSeeder`/`study_items`
   writes from the browse/dictionary paths.
5. Screenshot matrix re-captured for browse search + read-only detail.
6. `./gradlew ciFast` exits 0.

---

## Batch B: Daily-touch polish (Goals 116–118)

### Goal 116: Reminder notification actions — "Study now" + "Snooze 1h"

**Problem:** The reminder notification is tap-or-dismiss only: content intent
launches `MainActivity` with **no extras** (`ReminderScheduler.kt:270-277`),
so a tap does not even land on Study, and there is no snooze despite the
whole anti-spam stack existing in core. Zero `addAction` calls exist in the
app. The deep-link extra is already consumed at
`MainActivityStartup.kt:105-110` and proven by the widget
(`widget/KaniWidget.kt:105`).

**Goal:**

- New core policy `ReminderSnoozePolicy` (JVM-pure): input snooze-tap time,
  quiet hours, posts-today count, and the throttle state; output the re-arm
  time (tap + 60 min, shifted past quiet hours) and whether a re-post is
  even allowed (per-day fuse `MAX_NOTIFICATIONS_PER_DAY` respected — a
  snooze never mints extra budget, D-P3).
- `ReminderScheduler`: add two actions to the existing builder
  (`ReminderScheduler.kt:278-291`):
  - **Study now** — PendingIntent (request code 2704) to `MainActivity`
    with `EXTRA_OPEN_STUDY`, mirroring the widget intent.
  - **Snooze 1h** — PendingIntent (request code 2705) to `ReminderReceiver`
    with new action `ACTION_REMINDER_SNOOZED`: cancel slot 2702, persist the
    snooze re-arm via the existing alarm plumbing, and at re-arm time run
    the **normal evaluation path** (fresh eligibility + throttle) instead of
    re-showing the stale payload.
- The one-slot invariant (2702), unchanged-signature suppression
  (`ReminderThrottlePolicy.kt:11-16`), and quiet hours all keep applying to
  the snoozed re-post; a snoozed re-post that fails eligibility silently
  posts nothing.
- Copy: EN+JA action labels in the reminder copy object.
- Update the reserved-identifier table in this plan and the sibling plans'
  successor docs: 2704–2705 now taken.

**Done when (machine-checkable):**

1. `./gradlew :core:test --tests "dev.bee.kanjianki.core.ReminderSnoozePolicyTest"`
   passes: +60 min re-arm, quiet-hour shift, per-day fuse denial, no
   double-count of the original post.
2. App unit tests (Robolectric): notification carries exactly two actions;
   study action intent contains `EXTRA_OPEN_STUDY`; snooze broadcast cancels
   the notification and schedules the re-arm; re-arm path calls the normal
   evaluate entry point.
3. Existing reminder tests still pass unmodified (invariants untouched).
4. `./gradlew ciFast` exits 0.

---

### Goal 117: Post-update "What's new" card on Home

**Problem:** The app self-updates but never tells the user what changed: the
GitHub release `body` is parsed and dropped
(`GitHubReleaseMetadataParser.parseLatest`,
`update-core/.../GitHubReleaseMetadataParser.kt:11-26`;
`GitHubReleaseMetadata` has no notes field, `GitHubReleaseMetadata.kt:6-23`).
After an auto-update the user just finds a new version string.

**Goal:**

- `update-core`: `GitHubReleaseMetadata` gains `body: String?`; the parser
  retains it, trimmed and capped at 4,000 chars (fail-open: absent/huge body
  → null, never a parse failure — D-P4).
- App: when the updater records a completed check/download for version V
  (the `recordAutoUpdateResult` path,
  `data/LocalStoreStudySettings.kt:361-378`), also persist settings keys
  `whats_new_version` = V and `whats_new_notes` = body (empty clears both).
  No schema change (settings KV).
- New core policy `WhatsNewCardPolicy` (JVM-pure): input
  `currentVersion` (`BuildConfig.VERSION_NAME`), `storedNotesVersion`,
  `notesBlank`, `seenVersion`; output show/hide. Shows only when
  `storedNotesVersion == currentVersion`, notes non-blank, and
  `seenVersion != currentVersion`.
- Home: dismissible card ("What's new in vX.Y.Z" + the first N lines of the
  notes + a "View release" line using the stored `htmlUrl` precedent);
  dismiss writes `whats_new_seen_version = currentVersion`. Card slots into
  `HomeScreenModel` beside the existing update-permission prompt
  (`HomeScreenModel.kt:24-25` precedent).
- Copy: EN+JA title/dismiss labels.

**Done when (machine-checkable):**

1. `./gradlew :update-core:test` passes: body retained, cap applied,
   missing body → null, malformed JSON still fail-open.
2. `./gradlew :core:test --tests "dev.bee.kanjianki.core.WhatsNewCardPolicyTest"`
   passes: version-match matrix, blank notes, seen stamping.
3. App unit test: dismiss persists the seen version; card absent on next
   model build.
4. Screenshot capture of the Home card state added to the matrix.
5. `./gradlew ciFast` exits 0.

---

### Goal 118: "How Kani works" in-app explainer page

**Problem:** The scheduler model (two cores, ladder rungs, conditional rungs,
repair vs review, promotion/demotion, tag-only write-back) is deep and fully
documented for developers — but in-app, nowhere. Onboarding covers only the
AnkiDroid permission flow. The licenses page proves the static-page pattern
costs little (`MainActivitySettings.kt:196-206`,
`MainActivitySettingsReferenceData.referenceDataScreenModel:63-77`).

**Goal:**

- New route `NAV_SETTINGS_HOW_IT_WORKS_ROUTE = "settings/display-data/how-kani-works"`
  (constant at `MainActivityBase.kt:675-686`; parent-route back logic
  `:688-700` gains the branch), rendered async like the licenses page.
- New core copy object `HowKaniWorksCopy` (EN+JA, `localizedText` pattern)
  with short plain-language sections: what Kani reads from AnkiDroid and the
  tag-only write surface; the two core checks (recognition, contextual
  reading); variants (font/sentence); repair tasks and why they appear;
  pass/fail and what promotes or demotes; parking/suspension distinctions
  (one line, forward-compatible with Goal 101); backups. Each section is a
  title + ≤ 3 short paragraphs — this is an explainer, not the design doc.
- Entry points: a link panel on the Display & data hub
  (`MainActivitySettingsReferenceData.kt:9-18` precedent) and a "How Kani
  works" text link on the Home import-onboarding empty state.
- No behavior toggles on this page; it is static copy only.

**Done when (machine-checkable):**

1. `./gradlew :core:test` passes with `HowKaniWorksCopyTest`: every section
   non-blank in both locales; no section exceeds the agreed length budget.
2. App test: route renders from the hub link; back returns to Display &
   data (parent-route logic covered).
3. Screenshot matrix re-captured for the new page (EN + JA).
4. `./gradlew ciFast` exits 0.

---

## Batch C: Practice and insight (Goals 119–120)

### Goal 119: "Miss Sweep" game mode — drill today's misses, practice-only

**Problem:** Home lists recent mistakes (`store.recentMistakes(limit)`,
`data/StudyStatsQueries.kt:46-70`) but offers no zero-stakes way to drill
them; the games engine already builds multiple-choice questions from
inventory data (`KanjiGameEngine.nextQuestion:55-70`) and games write nothing
to the scheduler — the exact safe surface for this (AGENTS.md forbids any
second scheduler queue; D-P5).

**Goal:**

- `KanjiGameEngine`: new mode
  `MISS_SWEEP("miss_sweep", "Miss Sweep", "Recent misses", …)` in the
  `GameMode` enum (`KanjiGameEngine.kt:9-18`). `nextQuestion` gains a
  null-tolerant `recentMissKanji: List<String?>` input (same defensive style
  as the existing list params): MISS_SWEEP builds MEANING_POP-shaped
  questions (kanji → meaning) whose **targets** are restricted to the
  recent-miss set (distractors come from the full candidate pool);
  unavailable when fewer than 2 miss targets can build valid questions
  (`availableModes` then locks the card exactly like CONFUSABLE_CLASH
  today).
- `MainActivityGames.gameData()` (`MainActivityGames.kt:251-257`) adds
  `store.recentMistakes(20)` mapped to kanji strings; cached with the
  existing per-session `cachedGameData`.
- Games menu card copy explains the source ("kanji you missed recently");
  locked label reuses the existing lockedLabel pattern. Result screen for
  MISS_SWEEP gains one extra line when due items exist, pointing at Study
  now (no new navigation plumbing beyond the existing route render).
- Explicit invariant checks in tests: playing a full MISS_SWEEP round
  writes zero `review_log` rows and zero `study_items` mutations.

**Done when (machine-checkable):**

1. `./gradlew :core:test --tests "dev.bee.kanjianki.core.KanjiGameEngineTest"`
   (extended) passes: MISS_SWEEP targets ⊆ miss set, distractor pool
   unrestricted, lock threshold, null-tolerance, determinism under seeded
   `Random`.
2. App unit test: `gameData` feeds recent misses; menu shows the locked and
   unlocked states.
3. Practice-only test: after a simulated round, `review_log` count and
   `study_items` snapshot are unchanged.
4. Screenshot matrix re-captured for the games menu.
5. `./gradlew ciFast` exits 0.

---

### Goal 120: Per-kanji memory-strength chart on the detail page

**Problem:** Every persisted review stores full before/after scheduler state
(`scheduler_state_after_json`, `memory_after` — `LocalStoreBase.kt:658`) and
the `(kanji, reviewed_at)` index exists (`LocalStoreTableCreator.kt:24`), but
no store method returns raw per-kanji rows and no surface plots them; the
detail timeline renders only `kanji_timeline_events` prose cards
(`LocalStoreInventory.timelineForKanji:480-515`). "Is this kanji actually
getting stronger?" is unanswerable in-app.

**Goal:**

- New store query `reviewMemoryHistoryForKanji(kanji, limit)` (in
  `StudyStatsQueries`): projection `(reviewed_at, rating,
  scheduler_state_after_json)`, ordered ascending, bounded (default 120
  rows).
- New core policy `KanjiMemoryHistoryPolicy` (JVM-pure): input decoded rows
  (reviewedAt, rating, stability) — decoding uses the **same** scheduler-
  state JSON codec the review commit writes (locate the writer of
  `scheduler_state_after_json` and reuse its codec symbol; never a second
  parser — D-P6); output a `ProgressLineChartState`-compatible single-series
  model (stability over time, day-bucketed x labels) plus a fail-count
  caption ("N reviews, M misses"). Rows whose JSON fails to decode are
  skipped; < 2 usable points → empty output.
- Detail integration: add `memoryHistory: BrowseMemoryHistoryModel?` to
  `BrowseDetailScreenModel`; render a compact `KaniLineChart`
  (`charts/KaniCharts.kt:68-80`) panel titled from EN+JA copy, placed above
  the recovery timeline. Loaded on the existing async detail path. Empty
  output → panel absent.
- Read-only dictionary details (Goal 115) never query this (no inventory,
  no history).

**Done when (machine-checkable):**

1. `./gradlew :core:test --tests "dev.bee.kanjianki.core.KanjiMemoryHistoryPolicyTest"`
   passes: decode-skip, < 2 points → empty, day bucketing, caption counts,
   determinism.
2. App unit test for the store query: ordering, limit, kanji filter, and a
   round-trip against a row written by the real review-commit path (codec
   compatibility pinned).
3. Compose/model test: panel renders for a fixture history; absent for a
   kanji with one review.
4. Screenshot matrix re-captured for the browse-detail route.
5. `./gradlew ciFast` exits 0.

---

## Batch D: Scheduler-adjacent extensions (Goals 121–122)

These two goals touch scheduler-adjacent code and carry the strictest
invariants. Read `docs/adaptive-two-core-scheduler.md` first; neither goal
may create a new queue, a new FSRS memory, or change ladder movement
semantics without the explicit adoption gate in D-P10.

### Goal 121: Okurigana-choice variant of the `kanji_reading` task

**Problem:** Recorded as a deliberate future extension, twice:
"Okurigana-choice variant (教える vs 教わる) — natural future extension of
`kanji_reading`; KANJIDIC `.`-split already provides the data"
(`plans/reading-rungs-goals-2026-07-09.md:282-283`; reaffirmed at
`:1165-1166`). The data is confirmed present: the bundled dictionary keeps
kun readings with the `.` okurigana boundary (教 → `おし.える` /
`おそ.わる`), and `KanjiReadingChoicePlanner` already builds reading-choice
cards from `kanji_reading_usage`/`kanji_reading_pool`
(`core/.../KanjiReadingChoicePlanner.kt:44-60`;
`data/LocalStoreTableCreator.kt:114-148`).

**Goal:**

- New core planner `OkuriganaChoicePlanner` (JVM-pure): input the target
  kanji, the usage word/reading (from `kanji_reading_usage`), and the
  dictionary kun readings with `.` splits; when the usage word is a
  kun-reading inflection whose stem matches ≥ 2 distinct okurigana endings
  for the same kanji (教える vs 教わる), output a choice card — prompt shows
  the kanji + shared stem with the ending blanked, choices are the distinct
  full forms (2–4, same bounds as `KanjiReadingChoicePlanner.MIN/MAX_CHOICE_COUNT`,
  `KanjiReadingChoicePlanner.kt:21-23`), correct = the usage word's form.
  Deterministic for fixed inputs (D-P9). Null when the shape does not hold.
- Task integration: inside the existing `kanji_reading` task build path
  (the choice-session builder that already falls back to a plain flashcard
  when fewer than two choices exist), attempt the okurigana card **first**
  when the usage word contains okurigana, falling back to the standard
  reading-choice card unchanged. No new rung, no ladder settings entry, no
  `hasKanjiReading` change, no new FSRS memory — the variant only changes
  the rendered card face for the same repair task (D-P9).
- Answer logging keeps the existing choice-log semantics (kana selections
  already drop at the store boundary,
  `LocalStoreSimilarKanji.kt:220-224` behavior unchanged).
- Copy: EN+JA prompt line for the blanked-ending question.

**Done when (machine-checkable):**

1. `./gradlew :core:test --tests "dev.bee.kanjianki.core.OkuriganaChoicePlannerTest"`
   passes: stem matching, ≥ 2 endings requirement, bounds, non-inflected
   words → null, determinism, mixed on/kun words → null.
2. Task-build test: okurigana-eligible usage renders the variant; ineligible
   usage renders the standard card byte-identically to before.
3. Golden timelines and parity snapshots unchanged
   (`./gradlew :core:test` full suite green with zero golden regeneration —
   proving no scheduler semantics moved).
4. Screenshot capture of the variant card added to the matrix.
5. `./gradlew ciFast` exits 0.

---

### Goal 122: Run the tempered D2 experiment — graduation state from capped/blended learning history

**Problem:** Open decision D2 (`docs/ladder-and-srs-system.md:1052-1062`):
new-learning graduation seeds FSRS from the graduating rating alone
(`LatestFsrsAdapter.initialReview`, new-learning branch
`core/.../LatestFsrsAdapter.kt:20-27`, called from
`ReviewTransitionEngine.graduateToReview:384-399`), which the Goal 74
experiment proved struggle-blind — but the naive same-day history chain
over-corrects (one `Again` collapses graduating stability to ~0.25, five to
~0.01) and was REJECTED with the harness retained
(`docs/scheduler-fsrs-correctness-lab-report.md:115-137`). The rejection
explicitly names the follow-up: "a *tempered* variant (e.g. cap the number
of learning answers fed into the chain, or blend
`initialState(graduatingRating)` with the history stability)".

**Goal:**

- Extend the retained `GraduationHistoryExperimentTest` harness with the two
  named tempered variants, each behind an experiment-only adapter (never
  wired to production in this goal):
  - **Cap-N:** feed at most N (sweep N ∈ {1, 2}) most-recent learning
    answers into the same-day chain before the graduating rating.
  - **Blend-α:** `stability = α · initialState(graduatingRating).stability +
    (1-α) · historyChain.stability` (sweep α ∈ {0.5, 0.7, 0.9});
    difficulty from the graduating rating alone.
- Re-run the Goal 74 corpus (clean graduation, 1-again, 5-again, hard-path)
  for both variants; produce the comparison table (graduating stability,
  first-interval days) and append a dated section to
  `docs/scheduler-fsrs-correctness-lab-report.md`.
- Decision gate (D-P10): adopt a variant **only if** it preserves a sane
  ordering (struggling cards graduate weaker, but never below a floor that
  collapses the learning/review boundary — concretely: 5-again stability
  stays ≥ 25% of clean-graduation stability) and the team accepts the
  golden churn. Adoption is its own follow-up work item: production wiring,
  regenerated goldens, `RelearningGraduationDifficultyTest` re-pinned
  intentionally, AGENTS.md "Do not change either path" paragraph updated.
  If no variant qualifies, close by updating D2 in
  `docs/ladder-and-srs-system.md` with the tempered results and keep
  production unchanged.

**Done when (machine-checkable):**

1. `./gradlew :core:test --tests "*GraduationHistoryExperimentTest*"` passes
   with the tempered variants included and asserted against the recorded
   corpus numbers.
2. `docs/scheduler-fsrs-correctness-lab-report.md` gains the dated tempered
   section with the table; `docs/ladder-and-srs-system.md` D2 entry updated
   with the outcome (adopted-pending-follow-up or re-deferred with
   evidence).
3. Production behavior byte-identical unless adoption was decided: full
   `:core` suite green with zero golden changes in this goal.
4. `./gradlew ciFast` exits 0.

---

## Batch E: Widget and platform (Goals 123–125)

### Goal 123: Responsive widget sizes + 7-day activity strip

**Problem:** The widget is one fixed layout with Glance's default
`SizeMode.Single` (no override in `widget/KaniWidget.kt`), resizable only
horizontally (`res/xml/kani_widget_info.xml:1-9`) — resizing shows the same
three text lines. The snapshot model carries no recent-activity data
(`KaniWidgetSnapshotLoader.kt:11-22`), yet `reviewDaySummaries(nowMillis, days)`
(`data/StudyStatsQueries.kt:90`) already answers it in one query.

**Goal:**

- `KaniWidget` adopts `SizeMode.Responsive` with two buckets: **compact**
  (current layout, unchanged) and **expanded** (taller/wider bucket) which
  adds a 7-cell "last 7 days" activity strip — one small rounded Box per
  day, filled with the theme-accent alpha ramp by review count (reuse the
  heatmap intensity binning idea; cells carry a contentDescription with the
  day + count). Seven cells stays well inside RemoteViews child limits — a
  full heatmap grid was evaluated and rejected for the widget surface.
- `KaniWidgetSnapshot` gains `last7DayCounts: List<Int>` (empty = strip
  hidden); loader fills it via `reviewDaySummaries(now, 7)` inside the
  existing fail-soft `LocalStore` read (`KaniWidgetSnapshotLoader.kt:29-73`).
  The D-S6 eligibility invariant for due counts is untouched.
- Provider XML: `minResizeHeight`/`resizeMode` extended to vertical so the
  expanded bucket is reachable; `initialLayout` unchanged.
- Refresh triggers unchanged (event-driven set + hourly fallback).

**Done when (machine-checkable):**

1. Unit test: snapshot loader fills `last7DayCounts` from fixture rows,
   pads missing days with 0, fails soft to empty on store errors.
2. Bucket-mapping test for the size→layout decision (pure function over
   DpSize).
3. Manual smoke recorded in the goal commit message: compact and expanded
   render on an API 34+ emulator/launcher, tap-through still opens Study
   when `DUE_NOW`.
4. `./gradlew ciFast` exits 0.

---

### Goal 124: Predictive back opt-in

**Problem:** The manifest has no `android:enableOnBackInvokedCallback`
(`AndroidManifest.xml:19-31`), so Android 13+ predictive-back animations are
disabled app-wide. The prerequisite work is already done: back handling uses
a single modern `OnBackPressedCallback` registered on the dispatcher
(`MainActivityBase.kt:262-266,363`) and no deprecated `onBackPressed()`
override exists.

**Goal:**

- Add `android:enableOnBackInvokedCallback="true"` to `<application>`.
- Audit the single back callback: it must be **disabled** (not just
  falling through) whenever the app has nothing to handle (Home root), so
  the system predictive animation can run; verify the enable/disable state
  tracks the route stack (root → disabled, any pushed route/session →
  enabled). Fix the state toggling if it is currently always-enabled.
- Verify the study-session "close" confirmation still intercepts back while
  a session is active (session survival interplay is Goal 106's concern;
  this goal only preserves current semantics under the new flag).

**Done when (machine-checkable):**

1. Robolectric test: callback `isEnabled` is false on the Home root and
   true after navigating to a sub-route; back from a settings sub-route
   lands on its parent (existing `settingsParentRoute` logic covered).
2. Manifest lint/`ciFast` green with the flag present.
3. Manual smoke recorded in the commit message: predictive-back preview
   gesture on an API 34+ emulator shows the home-exit animation from the
   root and normal in-app back elsewhere.

---

### Goal 125: Baseline Profile — generated in the nightly lane, checked in, consumed at build

**Problem:** No Baseline Profile exists anywhere (zero `baselineprofile`/
`baseline-prof` hits), so every cold start JIT-compiles the hot path.
Constraint: profile generation needs an emulator, and the release path is
deliberately emulator-free (`tools/test_release_workflows.py` locks this) —
so generation must never gate a release (D-P7).

**Goal:**

- Add `androidx.profileinstaller` to `:app` and a new `:baselineprofile`
  test module (AGP `androidx.baselineprofile` plugin) with one journey:
  cold start → Home render → open Study route → open Stats. The journey
  uses the fake-provider debug fixture (no AnkiDroid dependency).
- Generate locally against the release variant and **check in**
  `app/src/main/baseline-prof.txt`. The release build consumes the
  checked-in file; no generation task runs in any release workflow.
- Wire an optional generation job into the existing nightly/dispatch
  instrumented workflow (`android-instrumented.yml`) that uploads a fresh
  profile as an artifact for manual refresh — it never commits and never
  blocks.
- Document the refresh procedure (one paragraph) in
  `docs/ci-sonar-reliability-runbook.md`.

**Done when (machine-checkable):**

1. `app/src/main/baseline-prof.txt` exists, non-empty, and referenced by a
   passing release assembly (`ciRelease` with local keystore).
2. `tools/test_release_workflows.py` still passes unmodified — proving the
   release path gained no emulator step.
3. The nightly workflow job runs green once via `workflow_dispatch`
   (run ID recorded in the goal commit message).
4. `./gradlew ciFast` exits 0.

---

## Batch F: Accessibility and large screens (Goals 126–127)

### Goal 126: TalkBack semantics + font-scale pass on the study and games surfaces

**Problem:** The highest-frequency interactive surfaces ship with zero
accessibility semantics: `MainActivityStudyChoiceCompose.kt` (0/0 across 11
interactive references), `MainActivityStudyDoneActionsCompose.kt` (0/0, 20),
`MainActivityStudyActionButtonCompose.kt` (0/0, 8),
`MainActivityGamesRoundCompose.kt` and `MainActivityGamesResultCompose.kt`
(0/0, 4 each). Only the choice grid has partial `stateDescription`
(`MainActivityStudyChoiceGridCompose.kt`).

**Goal:**

- Semantics pass over exactly those five files plus the choice grid:
  - Choice cells: `Role.Button`, merged descendants, a contentDescription
    of "choice text, K of N", selection `stateDescription` kept.
  - Pass/Fail and primary action buttons: explicit role + label semantics
    (the visible label is the description — no redundant text).
  - Done-screen summary block: `heading()` on the title, merged summary
    lines.
  - Games question card + result card: merged, with the prompt as the
    description; score strip exposes "X correct of Y".
- Font-scale audit at 2.0x on the same surfaces: replace any fixed-height
  row that clips at 2.0x with min-height + wrap. Record before/after in the
  goal commit message.
- Copy additions (the "K of N" template) go through the EN+JA copy objects.

**Done when (machine-checkable):**

1. Compose semantics tests: each listed surface exposes the specified
   roles/descriptions (assert via `onNodeWithContentDescription` /
   `SemanticsMatcher` on role and state).
2. A font-scale 2.0x screenshot pass of study choice + done + games routes
   is captured and committed alongside the standard matrix.
3. `./gradlew ciFast` exits 0.

---

### Goal 127: Window-size-class foundations — navigation rail + content width caps

**Problem:** The app declares `resizeableActivity="true"`
(`AndroidManifest.xml:28`) but adapts nothing: zero `WindowSizeClass` usage
in the repo, one bottom bar always at the bottom
(`MainActivityShell.kt:208-212`), and every route stretches full-bleed inside
the single `verticalScroll` Box (`MainActivityShell.kt:184-189`) — on a
tablet or desktop window, 100%-width cards and a 4-tab bottom bar look
broken.

**Goal (foundations only — D-P8):**

- Add `androidx.compose.material3:material3-window-size-class`; compute the
  size class once in the shell host.
- **Expanded width:** replace the bottom bar with a `NavigationRail`
  carrying the same four destinations + study badge (the existing
  `KaniBottomNavCompose` model feeds both); keep the bottom bar for
  compact/medium. The IME/study-session hiding rules
  (`MainActivityShell.kt:206-207`) apply to the rail identically.
- **Content width cap:** inside the scroll Box, cap route content at 640dp
  and center it at expanded width (one modifier at the shell level — no
  per-screen edits).
- Study surfaces sanity pass: the writing pad and choice grid must remain
  usable at expanded width under the cap (no oversize pad; grid keeps its
  cell sizing).
- No route, model, or navigation logic changes — layout only.

**Done when (machine-checkable):**

1. Robolectric/Compose tests: expanded size class renders the rail (bottom
   bar absent) and applies the width cap; compact renders exactly today's
   layout (pin with an unchanged-baseline screenshot).
2. The existing landscape screenshot harness lane re-captured; add one
   tablet-size capture (e.g. 1280dp-wide) for Home, Study, Stats, Settings.
3. `./gradlew ciFast` exits 0.

---

## Goal 128: Consistency, documentation, and release pass

**Problem:** Sixteen goals across six surfaces will drift copy, docs,
screenshots, and reserved-identifier tables unless a closing pass pins them.

**Goal:**

- Docs: update `README.md` (reference surface, dictionary lookup, Miss
  Sweep, widget sizes, what's-new), AGENTS.md only where invariants gained
  members (reserved request codes 2704–2705; okurigana variant noted under
  the study-UI rung rendering list; D2 outcome), and
  `docs/ladder-and-srs-system.md`/lab report per Goal 122's outcome.
- Full screenshot matrix re-capture (all themes) for every touched route;
  contact sheets reviewed.
- Copy audit: every new string exists in EN and JA; copy tests green.
- `./gradlew ciFast`, `./gradlew ciQuality`, and the signed local
  `./gradlew ciRelease` gate all pass; SonarCloud quality gate green on the
  first `main` push.
- Live AnkiDroid gate: **not required** for this plan unless a goal ended up
  touching provider/sync behavior (none is designed to — Goals 113–128 are
  UI, core-policy, reminder, widget, and build-infra work). If any
  implementation detail leaked into sync/provider paths, run the standard
  live gate per AGENTS.md before release.

**Done when (machine-checkable):**

1. `./gradlew ciRelease` (local keystore) exits 0; APK version bumped.
2. `tools/test_release_workflows.py` passes.
3. Screenshot matrix committed with matching manifests; hash validation
   passes.
4. README/AGENTS/docs diffs reviewed and merged with the final goal.
5. First `Android CI` run on `main` green (auto-release publishes the
   patch tag per the standard flow).

---

## Ordering and dependencies

| Goal | Depends on | Notes |
|---|---|---|
| 113 stroke-order panel | — | unlocks 115's read-only detail |
| 114 confused-with panel | — | independent of 113 |
| 115 dictionary lookup | 113 | reuses the stroke section on read-only detail |
| 116 notification actions | — | |
| 117 what's-new card | — | |
| 118 how-it-works page | — | copy can cite 113–115 features if landed |
| 119 Miss Sweep | — | |
| 120 memory chart | — | shares detail-model plumbing with 113/114 (rebase-trivial) |
| 121 okurigana variant | — | scheduler-adjacent; read D-P9 first |
| 122 tempered D2 | — | experiment-first; adoption is follow-up work |
| 123 widget sizes | — | |
| 124 predictive back | — | |
| 125 baseline profile | interacts with pending Goal 111 (regenerate after R8) | |
| 126 a11y pass | after 113–115, 119 land (audits their final surfaces) | |
| 127 size-class foundations | — | shell-level; rebases over everything |
| 128 consistency/release | all above | closing gate |

Recommended order: 113 → 114 → 115 → 116 → 117 → 118 → 119 → 120 → 123 →
124 → 121 → 122 → 125 → 126 → 127 → 128. The Batch A/B/C goals are the
user-visible payoff and land first; scheduler-adjacent and platform work
follows once the reference surface is stable.

## Status

> **2026-07-14:** Plan authored. No goal started. Base-state line numbers
> pinned to `63f40462` (branch `merge-pr-542`); re-verify symbols before
> each goal. Goals 98–112 remain owned by
> `plans/study-experience-settings-and-hardening-goals-2026-07-13.md`.
