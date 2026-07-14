# Repair Loop, Insights & Stats Rework — Goals 82–95 (2026-07-10)

> **Implementation status (2026-07-10):** Goals 82–94 are landed in the
> isolated `codex/repair-loop-and-stats-goals` worktree. The local portion of
> Goal 95 is complete: `ciFast`, `ciQuality`, and the signed `ciRelease` gate
> pass; the v2-signed local APK is `0.4.33 (4033)`. The committed visual QA
> contains 150 captures and 150 matching UI dumps: the 126-capture full
> six-theme top/middle/bottom matrix, an 18-capture six-theme Japanese
> narrow-phone Stats matrix, and six dedicated heatmap captures. All 300
> artifact hashes and real portrait/landscape dimensions validate. The 9/10
> review covers hierarchy, computed axes, circular donut, compact year
> heatmap, confusion insights, and scheduler-backed forecast.
>
> The mandatory Goal 94 real-collection gate passed against AnkiDroid 2.24.0
> with the default 7,000-note threshold: `OK (62 tests)` in 71.640 seconds.
> The time-boxed, non-destructive card probe attempted to write the card's
> existing queue value and was rejected exactly as expected:
> `updatedRows=-1`, `error=IllegalArgumentException`, `queue=2`; the reread
> queue value was unchanged. Direct unsuspend therefore remains deferred;
> shipped write-back is opt-in, manual-confirmed `kani_repaired` note tagging
> plus the exact AnkiDroid browser hand-off.
>
> Backup and restore were also verified through their production device UI:
> `Export now` opened Android DocumentsUI and produced a standalone gzip whose
> SQLite header and `quick_check` passed. A selected version-28 fixture then
> staged successfully, `Restore and close Kani` ended the process, and a fresh
> ordinary launch created the safety backup, applied the sentinel-bearing DB,
> removed the marker/staged files, and migrated it to schema version 29.
> This verification used API 35. A 2026-07-13 safety audit established that
> stock API 26–29 cannot run `VACUUM INTO`; current behavior fails closed there
> and removes both the checkpoint/main-file-copy snapshot fallback and the
> non-atomic restore replacement fallback.
>
> **External-only pending:** this isolated branch has not been pushed or merged
> to `main`, so the first containing `Android CI` run and the advisory
> SonarCloud quality gate cannot exist yet. No product goal is otherwise
> deferred.

Each goal below is self-contained: context with file/line evidence, the change
to make, and machine-checkable acceptance criteria. Work goals one at a time
with `/goal`. Line numbers are correct as of commit
`834e14f94d1eaf039882493cc02b60dfba237184` (2026-07-10) and may drift — search
the named symbols.

Goal numbers continue from 81 (`plans/reading-rungs-goals-2026-07-09.md` ends
at Goal 81) because goal numbers are globally unique across all plan files in
this repo — commit messages and AGENTS.md reference bare goal numbers, so
"Goal 88" must always mean exactly one thing.

## Asks

1. User-facing backup export/import: delivered through the SAF document picker
   on Android 11+ alongside WAL-safe automatic snapshots. Android 8–10 fail
   closed because their stock SQLite cannot produce the required live snapshot.
2. Glance home-screen widget: due count + streak + one-tap into study.
3. FSRS per-user parameter fitting (open idea I3 in
   `docs/ladder-and-srs-system.md` §14): train the 21 FSRS weights on the
   user's own `review_log`, on-device, in background work.
4. Confusion matrix visualization: "you confuse 徴↔微 (7×)" from the data
   already collected by the choice rungs, with tap-through.
5. Repair-completion forecast: use `SchedulerTimelineSimulator` to project
   "at this pace, done by ~<month year>" on the stats surface.
6. Review heatmap calendar: GitHub-style year grid from `review_log`.
7. Unsuspend write-back: when a kanji is verifiably repaired, offer the user
   a path to unsuspend the original cards in AnkiDroid — close the repair
   loop end-to-end.
8. Rework the Stats tab: "the UI and UX of it is kinda ugly."

## Base state (as of this plan)

- HEAD `834e14f94d1eaf039882493cc02b60dfba237184`, app version 0.4.33 (4033).
- `LocalStoreSchema.DB_VERSION == 29` (`app/.../data/LocalStoreSchema.kt:7`).
- `STATS_CACHE_FORMAT_VERSION == 5`, `STATS_REVIEW_DAY_SUMMARY_LIMIT == 90`
  (`app/.../data/StatsCacheStore.kt:10-11`).
- The live Stats tab renders `ProgressAnalyticsDashboardScreen`
  (`MainActivityStats.kt:15-32`). The `StatsScreenModel`/`StatsCardModel`
  stack (`MainActivityStatsCards.kt`, `MainActivityStatsCompose.kt`,
  `MainActivityStatsModel.kt`) is referenced only by tests and screenshot
  models — it is dead in production.
- The only AnkiDroid provider write in the repo is the archive-tag
  read-modify-write on `content://<auth>/notes/<noteId>`
  (`AnkiDroidArchiveCleanup.kt:73-90`). No card update exists anywhere.
- `suspended_archive.restored_at` exists and is written/read by nothing
  (`LocalStoreSchema.kt:15`).
- `BridgeScheduler`'s public constructor hardcodes default FSRS weights:
  `constructor() : this(LatestFsrsAdapter())` (`core/.../BridgeScheduler.kt:20`).
- No SAF/ActivityResult usage anywhere in `app/src`
  (grep `registerForActivityResult|ActivityResultContracts` → zero hits).
- No appwidget/Glance code anywhere; `androidx.glance` absent from
  `gradle/libs.versions.toml`.
- Goldens on disk: `core/src/test/.../golden/*.timeline.txt` +
  `SchedulerParitySnapshotTest` snapshot — all pinned to default FSRS weights.

## Design

### Decisions of record

- **D-S1 — ProgressAnalytics is the only stats surface.** The legacy
  `StatsScreen`/`StatsCardModel` stack is deleted, not revived. Every new
  insight card (Goals 86–88) targets `ProgressAnalyticsDashboardScreen` and
  the `StatsCacheStore` snapshot path. Reviving the verdict-card route was
  evaluated and rejected: two parallel stats surfaces is how the current mess
  happened.
- **D-S2 — Truthfulness precedes decoration.** No stat may render a value
  that is not computed from real data. Derived-fiction patterns (offsets of
  one number presented as several measurements, `max(x, 1)` chart floors,
  hardcoded axis label arrays disconnected from plotted geometry) are
  defects, not styling choices. Goal 83 lands before any visual polish.
- **D-S3 — Confusion insight reads the existing 90-day window.** The
  `similar_kanji_review_log` is pruned to `ConfusionPairMiner.WINDOW_DAYS`
  (90) on every sync (`LocalStoreSimilarKanjiMaintenance.minedConfusionPairs`
  deletes older rows). The confusion card reads inside that window; no new
  long-horizon store is added (avoids re-introducing the unbounded-growth
  issue fixed after `plans/deep-review-goals-2026-07-08.md:1142`). Persisting
  writing-rung confusion (currently dropped at
  `MainActivityStudyWritingResult.kt:45`) is deferred — see Out of scope.
- **D-S4 — The forecast models ladder completion, not retirement.** True
  retirement is Anki-evidence-driven (`matureSupportCount`), which Kani
  cannot forecast. The forecast simulates each active item to the highest
  enabled rung through the real production engine
  (`SchedulerTimelineSimulator` → `BridgeScheduler` → `ReviewTransitionEngine`)
  with deterministic all-`good` answers, and the card copy states that
  assumption ("assuming passes"). Probabilistic answer models were evaluated
  and rejected: non-deterministic forecasts cannot be golden-tested and the
  optimism bias is easier to explain than a retrievability-sampled model.
- **D-S5 — Restore is whole-file replacement, staged, applied at next
  process start on Android 11+.** There is no in-process "close every store" facility
  (18 independent `LocalStore(...)` construction sites, no registry), and
  `SQLiteOpenHelper` has no `onDowngrade` override — so a newer-schema backup
  would crash on first open. Restore therefore: validates fail-closed
  (SQLite magic, `PRAGMA user_version <= DB_VERSION`, `quick_check`,
  `settings` table present), stages the file, exits the process, and
  `KaniApplication.onCreate` applies the swap with strict atomic renames
  (replace DB, delete `-wal`/`-shm` sidecars, keep a pre-restore safety
  snapshot) before any helper opens. There is no non-atomic copy/move fallback.
  Android 8–10 preserve the live database, staged input, and existing archives
  unchanged. Row-level merge was evaluated and rejected as unverifiable.
- **D-S6 — Widget counts use `ReminderEligibilityPolicy`.** The reminder
  subsystem's invariant D2 ("an 'N reviews ready' notification never opens
  onto an empty study queue", `core/.../ReminderEligibilityPolicy.kt:26-39`)
  extends to the widget: its due count and tap-through use the same
  eligibility filter, so a widget tap never lands on an empty queue.
- **D-S7 — Fitted FSRS weights drive all interval math uniformly; the
  optimizer lives in `:core`.** When custom weights are active they feed
  every engine call including the fixed-0.90 `promotionIntervalDays` (a
  better memory model is better ladder evidence — this deliberately extends,
  not violates, closed decision D4: retention settings still never affect
  ladder pacing; the *model* may). All goldens and parity snapshots remain
  pinned to default weights. `:fsrs-java` stays a pristine upstream snapshot
  (`FsrsAlgorithmInfo` pins py-fsrs v6.3.1); the fitter/evaluator are core
  policies using only the public `FsrsEngine` API. Fitting is opt-in
  (default OFF) and adoption is gated on held-out improvement.
- **D-S8 — Kani still never rewrites Anki scheduling state.** The repair
  write-back is tag-only (`kani_repaired` via the proven note-tags
  read-modify-write), opt-in, confirm-gated, and can never fail a sync.
  Direct card unsuspend through the provider is unproven (the `cards` URI is
  read-only in this codebase; upstream FlashCardsContract documents card
  update only for deck moves and answering) and stays a time-boxed probe on
  the live gate — if the probe ever proves it, direct unsuspend becomes a
  separate future goal. The `kani_archived` tag is never removed: the
  admission gate (`matureSupportCount >= threshold` blocks re-admission)
  makes the repaired note safe to resurface in Anki, but stripping the
  archive tag would churn sync inputs for no benefit.

### Out of scope (evaluated, deliberately deferred)

- Audio/TTS/pitch-accent anywhere (standing non-goal; the app reads no
  media).
- Persisting writing-rung confusion glyphs into `review_log` (schema change;
  revisit if the Goal 87 card proves valuable and choice-rung data feels
  thin).
- Importing FSRS weights from AnkiDroid deck config: the provider surface
  consumed here exposes no deck configuration
  (`docs/anki-manual-parity-checklist.md:84-90`); on-device fitting is the
  chosen path.
- Removing the `kani_archived` tag during write-back (see D-S8).
- Converting the stats route to `LazyColumn` (requires opting the route out
  of the shell's single `verticalScroll` Box, `MainActivityShell.kt:176-183`)
  — optional follow-up if Goal 85's screenshot pass shows jank.
- Cloud backup / cross-device sync of backups (`allowBackup=false` stays;
  the SAF export is the user-controlled escape hatch).
- Wear OS, Bubbles, Quick Settings tile, KMP extraction — separate plans if
  ever.

## Cross-cutting reference — shared touch points

| Touch point | Location | Used by goals |
|---|---|---|
| Stats snapshot cache (`Snapshot`, `STATS_CACHE_FORMAT_VERSION`, codec) | `app/.../data/StatsCacheStore.kt`, `StatsCacheCodec.kt`, `StatsPrecomputeStore.kt` | 83, 86, 87, 88 |
| Live stats data source | `app/.../progress/ProgressAnalyticsLiveDataSource.kt` | 83, 85, 86, 87, 88 |
| Stats composables (1,870-line monolith to be split) | `app/.../ProgressAnalyticsCompose.kt` | 82, 84, 85, 86, 87, 88 |
| Theme screenshot harness + checked-in baselines | `ci/scripts/capture_kani_theme_screenshots.sh`, `screenshots/kani-theme-matrix/*/stats*.png`, `docs/design/kani-theme-screenshot-harness.md` | 84, 85, 86, 87, 88, 95 |
| Settings panel 6-step pattern (model → builder → composable → `SettingsPanel` when-branch → category list + `panelCount` → async render) | `MainActivitySettingsScreenCompose.kt:157-226`, `MainActivitySettingsScreenCoordinator`, exemplar `MainActivitySettingsAutomationDebugLog*.kt` | 89, 90, 93, 94 |
| Ephemeral out-of-Activity store reads (`LocalStore(context).use { }` + DB-exists stat-first check) | `ReminderScheduler.evaluate` (`reminders/ReminderScheduler.kt:434-484`), `AppDebugLog.readEnabledSetting:164-169` | 90, 91, 93 |
| Launch-intent extras precedent | `EXTRA_OPEN_UPDATE` (`MainActivityBase.kt:541`), `MainActivityStartup.handleLaunchIntent:71-96` | 91 |
| WorkManager patterns (unique periodic KEEP; constraints) | `backup/DatabaseBackupScheduler.kt`, `update/AutoUpdateScheduler.kt` | 90, 93 |
| Provider write pattern (per-note isolation, idempotent tag RMW, retry-next-sync, `RemovalSummary` message persistence) | `anki/AnkiDroidArchiveCleanup.kt`, `ManualSyncEngine.runLocked:166-182` | 94 |
| Fake provider + instrumented contract suite | `app/src/debug/.../FakeAnkiDroidProvider.kt`, `AnkiDroidGatewayProviderInstrumentedTest.kt` | 94 |
| Core convention: every new `:core` class needs tests (JaCoCo 100% class coverage wired into `check`) | `build-logic/.../kani.kotlin-library-conventions.gradle.kts` | 86, 87, 88, 91, 92, 93, 94 |

Reserved identifier ranges: widget PendingIntent request codes and any new
notification ids must avoid 2701–2703 (reminders), 2801–2802 (updates), job
id 3801 (auto-sync), and WorkManager's JobScheduler range 10000–11000
(`KaniApplication`).

---

## Batch A: Stats rework (Goals 82–85)

The audit (2026-07-10) found the live stats screen renders fabricated
numbers, keeps a dead duplicate implementation, has no typography system,
and never landed the prior polish plan's P1s
(`docs/design/kani-stats-visual-polish-scroll-direction.md`). Order matters:
delete the dead code, fix the lies, build the components, then restyle.

### Goal 82: Delete the dead legacy stats stack and the dead second bottom nav

**Problem:** Two complete Stats implementations exist. The live route is
`renderStats()` → `ProgressAnalyticsDashboardScreen`
(`MainActivityStats.kt:15-32`). The legacy stack — `StatsScreen` /
`StatsRouteScreen` (`MainActivityStatsCompose.kt`), `StatsScreenModel` /
`StatsCardModel` / `StatsLineModel` (`MainActivityStatsModel.kt`), all card
builders in `MainActivityStatsCards.kt`, and `screenshotStatsScreenModel()`
(`MainActivityScreenshotModels.kt:9-59`) — is referenced only by
`StatsComposeTest`, `MainActivityStatsModelTest`, and
`StatsPrecomputePerformanceSmokeTest`. `ProgressAnalyticsBottomNav`
(`ProgressAnalyticsCompose.kt:175-256`) is the leftover of the old
double-nav bug and is also test-only. Dead code confusables include a
same-named `ProgressLineChartCard` overload (`:947` vs `:1013`) and unused
imports (`:27`, `:49`). Every future stats goal needs exactly one target
surface (D-S1).

**Goal:** Delete the legacy UI layer; keep the data layer.

- Delete `StatsScreen`/`StatsRouteScreen` and the legacy ARGB constants from
  `MainActivityStatsCompose.kt` (the raw ints at `:23-32` and the unmapped
  `0xFFB2B2BA` at `MainActivityStatsCards.kt:140` go with them); delete
  `MainActivityStatsModel.kt` card models; delete the card builders in
  `MainActivityStatsCards.kt`; delete `screenshotStatsScreenModel()`.
- Delete `ProgressAnalyticsBottomNav` and its models; delete the dead
  `ProgressLineChartCard` overload and unused imports.
- KEEP everything the future cards feed on: `StatsCacheStore`,
  `StatsPrecomputeStore`, `StatsCacheCodec`, `StudyStatsStore` (incl.
  `KaniOutcomeStats`, `RepairEvidenceCohortStats`), `StatsPrecomputeScheduler`,
  and core `StatsTextCopy` (its verdict/ladder/repair-evidence copy is reused
  by Goal 88).
- Retarget `StatsPrecomputePerformanceSmokeTest` at
  `StatsPrecomputeStore.refresh` directly (its subject is precompute cost,
  not the dead model builder). Delete `StatsComposeTest` and
  `MainActivityStatsModelTest` or retarget the few assertions that pin
  still-live copy onto `StatsTextCopy` unit tests in core.
- The screenshot harness stats route already renders
  `progressAnalyticsSampleSnapshot` demo data — verify
  `isScreenshotLaunchRequested()` (`MainActivityStats.kt:16-18,34-46`) keeps
  working with no legacy references.

**Done when (machine-checkable):**

1. `grep -rn "StatsScreenModel\|StatsCardModel\|StatsRouteScreen\|ProgressAnalyticsBottomNav\|screenshotStatsScreenModel" app/src core/src` returns zero hits.
2. `StatsPrecomputePerformanceSmokeTest` passes against
   `StatsPrecomputeStore.refresh` via
   `ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk ./gradlew :app:testDebugUnitTest`.
3. `ci/scripts/capture_kani_theme_screenshots.sh` still captures the stats
   route (spot-run one theme locally; manifest written).
4. `./gradlew ciFast` exits 0.

---

### Goal 83: Purge fabricated stats and make every chart axis real

**Problem:** The live screen invents numbers
(`progress/ProgressAnalyticsLiveDataSource.kt`):

- "Kanji learned" delta is `"+${overallLearned / 8 + 1} this week"` —
  arithmetic fiction (`:140`).
- Retention-by-card-type renders one accuracy number four times with offsets
  (`accuracy30 + 1`, `- 2`, `- 12`; `:213-224`) and derives status chips
  from the fakes.
- "Progress by level" pastes ladder-rung counts onto JLPT N5…N1 labels and
  divides by an unrelated total (`:436-453`).
- "Card type breakdown" donut relabels impact-analyzer buckets as card types
  with `max(x, 1)` floors so empty data still draws a donut (`:512-526`).
- "Cumulative progress" counts reviews but is labeled learned kanji
  (`:238-251`).
- Y-axis labels are hardcoded string arrays (`"0","60","120","180"` at
  `:158`; `"70".."95"` at `:365`; `"0","50","100","150"` at `:241`) while the
  Canvas normalizes against the series max
  (`ProgressAnalyticsCompose.kt:1100-1101,1137-1147`) — the axes are
  decorative. Bars have a 14dp minimum so zero days look non-zero (`:1256`).

**Goal:** Every rendered value computes from real data (D-S2).

- **Per-rung accuracy (replaces fake retention rows and the fake donut):**
  new core policy `TaskTypeAccuracyPolicy` grouping `review_log` rows by
  `task_type` over the selected window into honest groups (meaning rungs /
  reading rungs / `write_kanji` / discrimination rungs — group map documented
  in the policy), each with real correct/total (correct = rating ≠ `again`,
  matching `ReviewDaySummary.correct()` semantics). Feed from a new
  `StudyStatsQueries.taskTypeDaySummaries(nowMillis, days)` (indexed by
  `idx_review_log_day_reviewed` + `task_type`), cached in the stats snapshot:
  add `taskTypeDaySummaries` to `StatsCacheStore.Snapshot` + `StatsCacheCodec`,
  bump `STATS_CACHE_FORMAT_VERSION` 5 → 6, compute in
  `StatsPrecomputeStore.refresh`. Groups with zero rows do not render.
- **"Card type breakdown" donut** becomes "Review share by rung group" from
  the same data. Remove every `max(x, 1)` floor; zero data → the section's
  empty state (Goal 85 provides the component; until then hide the chart).
- **"Progress by level"** becomes "Ladder rung distribution" fed by the real
  per-rung counts already in `LadderHealthPolicy` — drop the JLPT labels
  entirely.
- **"Cumulative progress"** becomes cumulative distinct kanji practiced:
  `SELECT MIN(review_day_start) FROM review_log GROUP BY kanji` folded into
  a cumulative day series (same snapshot addition).
- **"Kanji learned" delta**: compute the real 7-day delta from the cumulative
  series above, or render no delta line.
- **Axes:** delete every hardcoded label array. Chart states gain a computed
  `axisMax`/tick model produced by a new core `ChartAxisPolicy`
  (nice-number rounding: 1/2/5 × 10ⁿ ticks covering the true max); the
  Canvas renderers normalize against the same `axisMax` the labels are
  printed from. Remove the 14dp bar floor — zero renders as a baseline tick.
- **Accuracy-over-time series:** verify both series are real (7/30/90-day
  accuracy vs retention target line); make the DASHED style actually dash
  (`PathEffect.dashPathEffect`) instead of alpha 0.45f (`:1156,:1180`), or
  drop the second series.

**Done when (machine-checkable):**

1. `grep -rn "maxOf(.*, 1)\|max(.*, 1)" app/src/main/kotlin/dev/bee/kanjianki/progress/` returns zero chart-floor hits, and the three hardcoded y-label arrays are gone from `ProgressAnalyticsLiveDataSource.kt`.
2. New core tests pass via `./gradlew :core:test --tests "dev.bee.kanjianki.core.TaskTypeAccuracyPolicyTest" --tests "dev.bee.kanjianki.core.ChartAxisPolicyTest"` (rung-group mapping incl. legacy wire names `typing_meaning`/`writing_remediation`; correct/total math; nice-number ticks for 0, 1, 7, 180, 1026).
3. `StatsPrecomputeStoreTest` extended: snapshot round-trips `taskTypeDaySummaries` + cumulative series; `STATS_CACHE_FORMAT_VERSION == 6`; an old-format cache falls back to recompute (existing freshness rule at `StatsCacheStore.kt:59-69`).
4. A UI-model test asserts the rendered axis labels equal `ChartAxisPolicy` output for the same series (no divergence possible).
5. `./gradlew ciFast` exits 0.

---

### Goal 84: Stats design foundation — typography ramp, shared chart components, a11y hygiene

**Problem:** `MaterialTheme` is instantiated with a color scheme only — no
`Typography`, no `Shapes` (`KaniTheme.kt:290`). The stats screen hardcodes
every `fontSize` per call site (9–26sp observed; several with
`fontSize == lineHeight`, e.g. hero 10/12sp at
`ProgressAnalyticsCompose.kt:373-375`), uses ≥5 corner radii
(24/20/18/26/999dp), duplicates the metric-card concept
(`ProgressMetricCard` vs `HomeMetricCard` at `HomeMetricsCompose.kt:108-220`),
hardcodes Canvas stroke widths in raw px (5.5f/4.5f at `:1179-1186`), and
renders accessibility strings as visible body text (bar chart subtitle
`:1216-1221`; focus-score coral body `:1665-1671`). Every chart composable is
`private` to the 1,870-line `ProgressAnalyticsCompose.kt`, so Goals 86–88
would have nothing to reuse.

**Goal:** Build the component layer once.

- Add a `Typography` to `KaniTheme` (ramp per the approved design doc:
  screen title 28–32sp, section title 19–22sp, card title 15–16sp, body
  13–14sp, caption 11–12sp — each with a lineHeight ≥ 1.25×). Stats and
  Home consume `MaterialTheme.typography` roles; no new raw
  `fontSize = N.sp` literals in stats files.
- Standardize radii on the existing tokens: `KaniUiTokens.PanelShape` (24)
  for sections, one leaf-card radius (18), `CircleShape`/pill for chips.
  Add the two missing constants beside `PanelShape`
  (`MainActivityUiTokens.kt:43-55`) rather than a new tokens file.
- Extract shared, non-private components into `app/.../charts/`:
  `KaniLineChart`, `KaniBarChart`, `KaniDonutChart` (moving the Canvas
  renderers out of `ProgressAnalyticsCompose.kt`), each taking the Goal 83
  axis model, converting stroke widths to `Dp.toPx()`, and carrying
  `semantics { contentDescription }` only — never rendering the a11y string
  as visible text. Extract `KaniMetricCard` unifying `ProgressMetricCard`
  and `HomeMetricCard` (icon-in-tinted-circle + label + value + optional
  delta; fix the double-announcement where the icon repeats the visible
  label, `:790`).
- Adopt `HomeSectionHeader` (`HomeChromeCompose.kt:89-141`) as the stats
  section header and `HomeEmptyState` (`HomeEmptyStateCompose.kt:41-75`) as
  the empty-state primitive (used by Goal 85).
- Give the bar chart real semantics (it currently has none — the a11y
  sentence was its visible subtitle); give the 🦀 mascot badge a
  `contentDescription` (`:717-720`).

**Done when (machine-checkable):**

1. `grep -rn "fontSize = [0-9]" app/src/main/kotlin/dev/bee/kanjianki/ProgressAnalyticsCompose.kt app/src/main/kotlin/dev/bee/kanjianki/charts/` returns zero hits (all type via `MaterialTheme.typography`).
2. New chart components have model-level unit tests (axis mapping, segment math) and the extracted files compile with `ProgressAnalyticsCompose.kt` reduced below ~900 lines.
3. `ProgressAnalyticsCompactLayoutTest` still passes; a new compose test asserts the bar chart node exposes a `contentDescription` and the metric card announces its label exactly once.
4. Theme-matrix screenshots re-captured; visual diff reviewed (no baseline update without review).
5. `./gradlew ciFast` exits 0.

---

### Goal 85: Stats visual redesign — hierarchy, empty states, chip layout

**Problem:** The prior polish plan's P1s never landed
(`docs/design/kani-stats-visual-polish-scroll-direction.md`): six equal
metric cards with no hero (`ProgressAnalyticsCompose.kt:281-335`); every leaf
surface is a white box with a 1dp border inside a bordered section (the
"boxy wireframe" — metric `:769-773`, mini `:840-844`, charts `:953-956`,
`:1193-1196`, `:1284-1288`, weakness `:1679-1682`, support `:1792-1795`);
the subtitle renders twice (header `:269→688-695` and hero card line 1
`:371`); range chips crowd the section-header trailing slot on narrow
phones (`:451-457,:533-539`) and a display-only echo of them sits inside the
line-chart card (`:992-998`); four status chips are forced into
quarter-width `weight(1f)` pills with long JP labels (`:556-573`); the
"All levels" chip looks like a filter but has no `onClick` (`:592-597`);
and there are **no empty states anywhere** — zero-data users see walls of
"0"s and headers over empty grids (`:633-639`).

**Goal:** Land the approved direction on top of Goals 83–84.

- **Hero strip:** promote Streak, Accuracy, and Reviews-today into a single
  hero row at the top (real data from Goal 83); demote the remaining metrics
  to a compact `KaniMetricCard` grid. Delete the duplicated subtitle and the
  hero summary card's repetition.
- **Border diet:** leaf cards lose the 1dp border and sit as tinted fills
  (`panelSoft`/accent-alpha) inside the section card; alternate section
  fills for rhythm; keep exactly one border level.
- **Empty states:** per-section `HomeEmptyState` with Kani-flavored copy
  (EN + JA, e.g. "まだこれから🦀") behind a core `StatsEmptyStateCopy` object;
  charts, donuts, grids and their headers do not render when their series is
  empty.
- **Chips:** range chips move to their own row below the section header on
  compact width (the 420dp breakpoint already exists,
  `ProgressAnalyticsCompose.kt:106-134`); delete the non-interactive
  "All levels" chip and the echo chips inside the line-chart card; status
  chips wrap via `FlowRow` instead of `weight(1f)`.
- **Formatting unification:** one number/duration/date formatter set (core,
  locale-aware) replacing the three parallel systems
  (`formatInt`/`SimpleDateFormat` + regex month swap at
  `ProgressAnalyticsLiveDataSource.kt:566-570`,
  `ProgressAnalyticsCopy.kt:315-340`, and `StatsTextCopy`'s `Locale.ROOT`
  formats); localization stops being string-replacement of baked English —
  copy objects take the locale like `HomeTextCopy` does.
- Re-run the full screenshot matrix (girlypop/light/dark/system/autumn ×
  top/middle/bottom scroll, JP locale, narrow phone) and update the
  checked-in baselines deliberately. Design bar from the doc: ≥ 8/10 on real
  narrow-phone JP screenshots.

**Done when (machine-checkable):**

1. Compose/unit tests: hero strip renders the three hero metrics; empty-data
   state renders `HomeEmptyState` and zero chart nodes; `FlowRow` chips wrap
   (compact-width test extends `ProgressAnalyticsCompactLayoutTest`).
2. `grep -n "All levels" app/src/main/kotlin/dev/bee/kanjianki/` returns
   zero non-test hits; the echo-chip composable is deleted.
3. Screenshot matrix regenerated via
   `ci/scripts/capture_kani_theme_screenshots.sh`; new baselines committed in
   the same change.
4. `docs/design/kani-stats-visual-polish-scroll-direction.md` updated to mark
   landed P1s; this plan's status blockquote updated.
5. `./gradlew ciFast` exits 0.

---

## Batch B: New insight cards (Goals 86–88)

All three plug into the Goal 84 component set and the `StatsCacheStore`
snapshot path. Each goal that extends the snapshot bumps
`STATS_CACHE_FORMAT_VERSION` by one in its own commit; the codec keeps
decode-tolerant defaults so old caches fall back to recompute (existing rule
`StatsCacheStore.kt:59-69`).

### Goal 86: Review heatmap calendar (GitHub-style year grid)

**Problem:** The dashboard has no long-horizon consistency view. The data is
already ideal: `review_log.review_day_start` stores local-midnight epoch
millis at write time, indexed by
`idx_review_log_day_reviewed(review_day_start, reviewed_at)`
(`LocalStoreTableCreator.createStatsIndexes:21-28`), and
`StudyStatsQueries.reviewDaySummaries` already groups by day — but the cached
window is 90 days (`STATS_REVIEW_DAY_SUMMARY_LIMIT = 90`,
`StatsCacheStore.kt:11`), too short for a year grid.

**Goal:**

- Raise `STATS_REVIEW_DAY_SUMMARY_LIMIT` to 366 and bump
  `STATS_CACHE_FORMAT_VERSION` (365 tiny summary rows are cheap; the day
  index makes the query linear in days studied). All existing consumers keep
  slicing their own windows.
- New core policy `ReviewHeatmapPolicy` (JVM-pure, 100% coverage):
  input `List<ReviewDaySummary>` + `nowMillis` + zone; output a
  weeks×7 grid model with per-cell intensity bin (0 + quartile bins over
  non-zero days), month labels at week boundaries, weekday labels, and an
  `accessibilitySummary` ("N reviews across M days in the last year; busiest
  day D with K"). Day bucketing uses `review_day_start` verbatim — this
  matches the streak card exactly and inherits its documented write-time-TZ
  caveat (bucketing raw `reviewed_at` would disagree with streaks around
  midnight/DST; rejected).
- New `KaniHeatmapChart` composable in `app/.../charts/` (Canvas `drawRect`
  grid; intensity from the theme accent alpha ramp so all 9 palettes work;
  semantics carry the summary). Card placed at the top of the
  "Reviews analytics" section with the standard empty state.
- Feed from the snapshot in `progressAnalyticsSnapshot`
  (`ProgressAnalyticsLiveDataSource.kt:58-74`); no live query on the render
  path.

**Done when (machine-checkable):**

1. `./gradlew :core:test --tests "dev.bee.kanjianki.core.ReviewHeatmapPolicyTest"` passes: bin thresholds (all-zero, single-day, uniform, skewed), 52/53-week edges, gap filling, month-label placement, DST day handling via `LocalDayPolicy.moveLocalDays`.
2. `StatsPrecomputeStoreTest` updated for the 366-day window + format bump; old-format cache falls back cleanly.
3. Compose test: heatmap renders N week columns for a fixture and exposes the a11y summary; empty data renders the empty state.
4. Screenshot matrix re-captured for the stats route.
5. `./gradlew ciFast` exits 0.

---

### Goal 87: Confusion pairs insight card ("you confuse 徴 ↔ 微")

**Problem:** The choice rungs already record exactly which wrong glyph the
user picked (`similar_kanji_review_log(target_kanji, selected_kanji, correct,
rung, reviewed_at)`, `LocalStoreTableCreator.kt:163-173`), and
`LocalStoreSimilarKanjiData.choiceWrongPickCounts` (`:83-102`) already
aggregates a directional target→selected→count map — but nothing shows it to
the user. Distractor ordering consumes it silently
(`SimilarKanjiChoicePlanner.confusionOrderedChoices`), and
`ConfusionPairMiner` discards the counts when persisting pairs. Two data
caveats: the `kanji_reading` rung writes **kana** strings into
`selected_kanji` (`MainActivityStudyChoiceSessions.kt:192`), and the log is
pruned to 90 days on every sync (D-S3).

**Goal:**

- New core policy `ConfusionInsightPolicy` (JVM-pure): input the directional
  wrong-pick map + `kanji_inventory` items; output top-N unordered pairs,
  each with per-direction counts ("徴→微 ×5, 微→徴 ×2"), both glyphs'
  primary meanings, and a total-count sort. Rows are filtered through
  `TextUtil.normalizeSingleKanji` on **both** glyphs (drops the kana rows and
  multi-codepoint junk) and require total count ≥
  `ConfusionPairMiner.MIN_WRONG_PICKS` (2) so the card agrees with what the
  miner considers a confusion pair.
- Add the windowed wrong-pick map to the stats snapshot (compute in
  `StatsPrecomputeStore.refresh` via the existing
  `choiceWrongPickCounts(nowMillis)` 90-day window; codec + format bump).
- UI card in the "Weakness insights" section: paired-glyph rows using the
  Kaisei Tokumin kanji font precedent (`MainActivityGamesDesign.kt`
  `GamesKanjiFontFamily`), per-direction counts, and tap-through to the
  target kanji's browse detail (the existing browse-detail navigation; the
  detail screen already shows similar-kanji pairs and examples). Include the
  standard empty state ("no recent mix-ups").
- Card subtitle states the window honestly: "last 90 days".

**Done when (machine-checkable):**

1. `./gradlew :core:test --tests "dev.bee.kanjianki.core.ConfusionInsightPolicyTest"` passes: kana rows filtered, direction merge + per-direction counts, min-count gate, meaning join, top-N ordering, tie-break determinism.
2. Snapshot round-trip test for the wrong-pick map; format bump verified.
3. Compose/model test: tap on a row routes to browse detail for the target kanji; empty state renders when the map is empty.
4. Screenshot matrix re-captured.
5. `./gradlew ciFast` exits 0.

---

### Goal 88: Ladder-completion forecast ("done by ~March 2027 at this pace")

**Problem:** `SchedulerTimelineSimulator`
(`core/.../SchedulerTimelineSimulator.kt`, 318 lines) drives the full
production engine — real `StudyQueueSeeder` admission, real
`ReviewTransitionEngine` + FSRS calls, promotion caps, conditional-rung
crossings, ceiling parking — and is used by tests only. Users get no answer
to "when will this be done?", the single most motivating number the app can
compute. The verdict copy exists in core (`StatsTextCopy`) but its card died
with the legacy stack (Goal 82).

**Goal:**

- New core policy `LadderCompletionForecastPolicy` (JVM-pure): inputs
  `rows`, `startingItems`, `Settings`, `SchedulerParameters`,
  `LearningStepSettings`, `StudyLadderSettings`, `nowMillis`,
  `horizonDays = 730`. It constructs a `SchedulerTimelineSimulator`, runs
  `seedQueue()`, then loops: `advanceTo(next due)`, `nextSession()`,
  `answer("good")` (writing rung via `answerWriting("good", passed = true,
  clean = true, hintsUsed = 0)`) until every non-retired item sits at its
  highest enabled *valid* rung in `review` phase with a full first-interval
  pass, or the horizon is reached (D-S4). Because the loop goes through the
  real seeder/selector, it inherently models `activeQueueCap` admission
  waves and ceiling parking — the "current pace" is the app's actual pace,
  not per-item math. Outputs: per-month projected completion counts
  (burn-down series), the projected completion month (or "beyond horizon"),
  items already at ceiling/parked/retired now, and assumption copy ids.
- Determinism: fixed inputs → identical output; add a golden-style pinned
  fixture test (small synthetic deck) asserting the exact burn-down series.
  The simulator must not accumulate render text for this path — add a
  lightweight constructor flag or skip `renderText()`/event retention where
  cheap (measure first; only optimize if the perf smoke fails).
- Compute in `StatsPrecomputeStore.refresh` on the maintenance executor and
  cache in the snapshot (format bump). Perf budget enforced by extending the
  `StatsPrecomputePerformanceSmokeTest` pattern: 200 synthetic active items
  must forecast within a bounded time on the JVM (suggested ≤ 2s; ~2–4k
  simulated reviews expected).
- UI: a forecast hero card at the top of the stats screen (above the Goal 85
  hero strip or merged into it): "On pace to finish practicing all N weak
  kanji by <Month Year>" + a mini burn-down line via `KaniLineChart` +
  the explicit assumption line ("assumes passes; finishing in Anki still
  depends on your Anki reviews"). Copy in core `ForecastTextCopy` (EN + JA).
  Empty/insufficient state when < 1 active item.
- Cross-reference: when Goal 92 lands, the policy must accept the same
  stored FSRS weights the live scheduler uses (constructor already takes the
  simulator's parameter objects; thread weights through the same seam).

**Done when (machine-checkable):**

1. `./gradlew :core:test --tests "dev.bee.kanjianki.core.LadderCompletionForecastPolicyTest"` passes: single-item climb (rungs × min-passes × promotion caps respected), queue-cap admission wave visible in the series, conditional rungs crossed without pausing, horizon cutoff, already-parked items counted as done, deterministic repeat run byte-equal.
2. Perf smoke: 200-item fixture forecast under the budget via `:app:testDebugUnitTest` (or `:core:test` if the fixture lives in core).
3. Snapshot integration + format bump tests; forecast card renders with fixture data and with the empty state.
4. `docs/ladder-and-srs-system.md` gains a §forecast note (explicitly: forecast ≠ retirement, D-S4); AGENTS.md untouched (no scheduler behavior change — `ciFast` goldens must be byte-identical).
5. `./gradlew ciFast` exits 0.

---

## Batch C: Data safety and platform surfaces (Goals 89–91)

### Goal 89: Backup export via SAF + "Backup & restore" settings panel

**Problem (historical; compatibility corrected 2026-07-13):** Daily gzip
snapshots existed (`VACUUM INTO` with an unsafe checkpoint-and-copy fallback,
`LocalStore.snapshotInto`,
`LocalStore.kt:31-77`; tiered 7-daily + 4-weekly retention,
`core/.../DatabaseBackupPolicy.kt:49-75`) but live in app-private
`files/backups/` — if the device dies, every copy dies with it
(`docs/database-backup-restore.md`: "There is no in-app restore path").
Cloud backup is deliberately disabled (`allowBackup=false`,
`res/xml/backup_rules.xml`). No SAF/ActivityResult usage exists anywhere in
the app; the activity chain bottoms out at `ComponentActivity`
(`MainActivityUiSupport.kt:19`) so `registerForActivityResult` is available
but first-use.

**Goal:**

- New settings panel "Backup & restore" in the Automation category following
  the 6-step pattern (model `SettingsBackupPanelModel : SettingsPanelModel`;
  builder reading last-backup time + archive count from
  `DatabaseBackupPolicy.backupDir(filesDir)`; composable with
  `KaniPrimaryButton`/`KaniOutlinedButton`; `SettingsPanel` when-branch +
  `settingsPanelTestTag("settings-panel-backup")`; category list +
  `panelCount` 3 → 4 in `MainActivitySettingsScreenCoordinator:52-70` and
  `MainActivitySettingsScreenSections.kt:110-123`).
- **Export now:** on tap, run on the `io` executor: fresh snapshot via the
  existing `LocalStore.snapshotInto` + `DatabaseBackupWorker.gzipFile`
  building blocks into a temp file, then launch
  `ActivityResultContracts.CreateDocument("application/gzip")` with the
  suggested name from `DatabaseBackupPolicy.backupFile` naming
  (`kanji_anki_simple_yyyyMMdd_HHmmss.db.gz`), and stream the temp file to
  the returned `Uri` via `contentResolver.openOutputStream`. Success/failure
  Toast via the `runSettingsWrite` completion pattern
  (`MainActivitySettings.kt:274-285`).
- Register the launcher once in the activity base (`onCreate`-time
  registration; a small `PendingExportHolder` bridges the settings panel to
  the single-activity callback, mirroring how `onRequestPermissionsResult`
  delegates at `MainActivityBase.kt:348-355`).
- **Share latest:** optional secondary action reusing the existing
  FileProvider (authority `${applicationId}.debuglog` already exposes the
  whole `filesDir` root via `res/xml/debug_log_paths.xml` — reuse verbatim,
  as `AppDebugLog.buildShareIntent` does at `AppDebugLog.kt:144-158`).
- New core policy `BackupExportPolicy` (suggested filename, size/summary
  strings, error copy; EN + JA) so all logic is JVM-tested; the Android
  layer is a thin seam. Add a `UriStreams` fun-interface seam so the copy
  path is unit-testable with fakes.

**Done when (machine-checkable):**

1. `./gradlew :core:test --tests "dev.bee.kanjianki.core.BackupExportPolicyTest"` passes (name suggestion, status lines, error copy).
2. App unit test: export copy path round-trips bytes through a fake `UriStreams` and reports the gzip size; failure path reports without leaving temp files (mirror `backupDatabase`'s finally-delete at `DatabaseBackupWorker.kt:75-113`).
3. Instrumented test: panel renders, "Export now" produces a valid gzip whose decompressed header is `SQLite format 3\0` (write to a test-scoped file Uri).
4. `docs/database-backup-restore.md` updated with the in-app export path (adb path retained).
5. `./gradlew ciFast` exits 0.

---

### Goal 90: Backup restore — validate, stage, swap on next process start

**Problem:** Restore is adb-only and the manual procedure encodes two hard
invariants (`docs/database-backup-restore.md:27-53`): nothing may hold the
DB open during the swap, and stale `-wal`/`-shm` sidecars must be deleted.
In-process restore is unsafe: 18 independent `LocalStore(...)` construction
sites with no registry (workers, JobService, reminder receivers, updater all
open ephemeral stores), and there is no process-restart utility (grep
`exitProcess|killProcess|recreate()` → zero hits). Worse, `SQLiteOpenHelper`
has no `onDowngrade` override anywhere, so restoring a backup produced by a
**newer** app (higher `user_version`) would crash on first open.

**Goal:** Staged restore (D-S5).

- **Pick:** the Backup & restore panel (Goal 89) gains "Restore from
  backup…" launching `ActivityResultContracts.OpenDocument` (mime
  `application/gzip`, `application/octet-stream`, `*/*` fallback).
- **Validate fail-closed** in a new core policy `BackupRestorePolicy` +
  app-side validator: gunzip to a private temp; check the 16-byte magic
  `SQLite format 3\0`; open read-only
  (`SQLiteDatabase.openDatabase(..., OPEN_READONLY)`) and require
  `PRAGMA user_version <= LocalStoreSchema.DB_VERSION` (29 today — an older
  version is fine, migrations re-run on next open per
  `LocalStoreMigrations.upgrade`), `PRAGMA quick_check` == ok, and the
  `settings` table present (sentinel that this is a Kani DB). Each rejection
  maps to precise user copy ("This backup is from a newer version of Kani",
  "Not a Kani backup", "File is corrupted").
- **Confirm** with the dialog-model pattern
  (`HomeSyncConfirmDialogModels` precedent, `MainActivityHome.kt:200-215`):
  "Restore replaces all current data on this device. Kani will close and
  apply the backup on next launch." Blocked while a sync is running
  (`ManualSyncEngine.RUNNING` gate, `ManualSyncEngine.kt:383`).
- **Stage:** strictly atomically rename the validated temp to
  `files/restore/kanji_anki_simple.db.staged` and fsync its directory; defer the
  recovery marker until startup after the safety archive is durable; then
  `finishAffinity()` + `exitProcess(0)`. API 26–29 must not stage a new restore.
- **Apply at next process start:** in `KaniApplication.onCreate` (runs in
  every process entry — receivers, workers, activity — before any component
  opens the helper; the only manifest provider is FileProvider, which opens
  no DB), a new `StagedRestoreApplier`: if the staged file exists → snapshot
  the current DB into `files/backups/` as a pre-restore safety copy (normal
  timestamped name so retention manages it) → publish and fsync a versioned
  `SAFETY_READY` marker → move staged over `databases/kanji_anki_simple.db` and
  fsync both parent directories → delete `-wal`/`-shm` and fsync the database
  directory → delete the marker and fsync the restore directory. All steps are
  idempotent and process/power-loss safe through strict atomic rename and
  durability barriers. Only a versioned ready marker-only state proves that
  replacement committed; ambiguous legacy markers block for manual recovery.
  There is no ordinary move/copy fallback.
- A stray background open between `exitProcess` and relaunch reads the old
  DB once — harmless; the swap applies on the next process start regardless.
- Update `docs/database-backup-restore.md` (in-app restore is now the
  primary path; adb path retained for recovery).

**Done when (machine-checkable):**

1. `./gradlew :core:test --tests "dev.bee.kanjianki.core.BackupRestorePolicyTest"` passes the validation matrix: bad magic, truncated gzip, newer `user_version` (30), missing `settings` table, quick-check failure, happy path — each with distinct copy ids.
2. App unit tests for `StagedRestoreApplier` with temp dirs: full apply, idempotent re-run after simulated crash between each step, no-op when unstaged, sidecars deleted, pre-restore safety snapshot present.
3. Instrumented test: stage a known fixture DB, relaunch via `ActivityScenario`, assert the app opens on the restored data (a settings row planted in the fixture is visible) and migrations ran (`PRAGMA user_version == 29`).
4. Restore is refused while `ManualSyncEngine.RUNNING` is true (unit-testable via the gate).
5. `docs/database-backup-restore.md` updated in the same commit; `./gradlew ciFast` exits 0.

---

### Goal 91: Glance home-screen widget — due count, streak, one tap into study

**Problem:** Zero widget code exists. Everything the widget needs is already
computed out-of-Activity for reminders: `ReminderScheduler.evaluate`
(`reminders/ReminderScheduler.kt:434-484`) reads dashboard rows, filters
eligible items through `ReminderEligibilityPolicy` (invariant D2), reads
`store.studyStreak`, and builds `DailyStudyPlanPolicy.plan(...)` — the exact
data a widget renders. The launch-intent precedent exists
(`EXTRA_OPEN_UPDATE`, `MainActivityBase.kt:541`;
`MainActivityStartup.handleLaunchIntent:71-96`, re-dispatched on
`onNewIntent` at `MainActivityBase.kt:319-323`).

**Goal:**

- Add `androidx.glance:glance-appwidget` to `gradle/libs.versions.toml` +
  `app/build.gradle.kts` (minSdk 26 satisfies Glance's floor; WorkManager is
  already on-demand-init via `KaniApplication : Configuration.Provider`,
  which Glance is compatible with).
- New package `app/.../widget/`:
  - `KaniWidgetReceiver : GlanceAppWidgetReceiver` registered in the
    manifest with `res/xml/kani_widget_info.xml` (resizable horizontal,
    `updatePeriodMillis = 0` — updates are event-driven).
  - `KaniWidgetSnapshotLoader`: stat-first DB-exists check
    (`context.getDatabasePath(LocalStoreSchema.DB_NAME).exists()`, the
    `AppDebugLog.readEnabledSetting:164-169` trick — never create the DB
    from the widget) → `LocalStore(context).use { }` mirroring
    `ReminderScheduler.evaluate`: eligible due count via
    `ReminderEligibilityPolicy.eligibleReminderItems` (D-S6), streak via
    `store.studyStreak(now)`, plan via `DailyStudyPlanPolicy`.
  - `KaniWidget : GlanceAppWidget` rendering three states: not-set-up
    (DB missing → "Open Kani to set up"), nothing-due (streak + next-useful
    time from `plan.nextUsefulReminderAtMillis`), and due-now (count +
    streak + action label). Copy from a new core `WidgetTextCopy` (EN + JA,
    reusing `HomeTextCopy` phrasing conventions).
  - Tap → `PendingIntent` to `MainActivity` with new
    `EXTRA_OPEN_STUDY = "dev.bee.kanjianki.extra.OPEN_STUDY"`; handled in
    `handleLaunchIntent` → `renderStudy()`. Request code outside the
    reserved ranges (see cross-cutting table).
- Refresh triggers via a tiny `KaniWidgetUpdater.requestUpdate(context)`
  (no-op when no widget instances): call sites at (a) study-session
  completion (the done-screen path), (b) `ManualSyncEngine` success and the
  auto-sync runner's success, (c) the daily reminder evaluation (piggyback —
  it already wakes daily), (d) `KaniWidgetReceiver.onUpdate` for
  system-driven refreshes. No new periodic worker.

**Done when (machine-checkable):**

1. `./gradlew :core:test --tests "dev.bee.kanjianki.core.WidgetTextCopyTest"` passes (three states × EN/JA; due-count pluralization).
2. App unit tests: snapshot loader returns not-set-up without creating the DB (temp dir without the file); due count equals `ReminderEligibilityPolicy` output for a seeded store fixture.
3. Instrumented test: launching `MainActivity` with `EXTRA_OPEN_STUDY` lands on the study route (mirror the `EXTRA_OPEN_UPDATE` test pattern).
4. `./gradlew :app:compileDebugAndroidTestJavaWithJavac` and lint pass with the new manifest receiver (lint `abortOnError` is on).
5. `./gradlew ciFast` exits 0.

---

## Batch D: Personalized scheduling (Goals 92–93)

### Goal 92: Custom FSRS weights plumbing (behavior-neutral by default)

**Problem:** The weights seam exists in the engine
(`FsrsParameters.of(DoubleArray)` validates 21 finite values with
`values[20] > 0`; `FsrsEngine.create(parameters)`) but nothing can reach it:
`LatestFsrsAdapter` defaults to `FsrsEngine.latestDefault()`
(`core/.../LatestFsrsAdapter.kt:9-10`) and `BridgeScheduler`'s public
constructor hardcodes it (`BridgeScheduler.kt:20`); a fresh
`BridgeScheduler()` is constructed per review
(`MainActivityStudyReviewFlow.kt:156`) and per sync seeding
(`ManualSyncEngine`), and `SchedulerTimelineSimulator` takes a
`BridgeScheduler`. Storage caution: `SettingsRepository.putDouble` truncates
to 4 decimals (`"%.4f"`) — unusable for weights.

**Goal:**

- `BridgeScheduler` gains a public factory
  `BridgeScheduler.withWeights(weights: DoubleArray?)` → null yields the
  default adapter; non-null yields
  `LatestFsrsAdapter(FsrsEngine.create(FsrsParameters.of(weights)))`.
  Constructor shapes extended once, deliberately (the D5 positional-vararg
  hazard from the ladder plan applies to `Settings`, not here, but follow
  the same discipline).
- New settings key `scheduler_fsrs_weights`: one comma-joined 21-value
  full-precision string via `putStringSetting` (never `putDouble`). Reader
  in `LocalStoreStudySettings` parses + validates through
  `FsrsParameters.of` in a try/catch — any invalid stored value logs one
  sanitized diagnostic line and falls back to defaults (fail-open to
  defaults, never crash a review).
- Thread the stored weights through every production construction site:
  `MainActivityStudyReviewFlow.performNormalReview` (read once per review
  alongside `schedulerParameters()`), `ManualSyncEngine`'s seeding
  `BridgeScheduler()`, and any other `BridgeScheduler()` call in `app/`.
  `SchedulerTimelineSimulator` consumers (Goal 88 forecast) pass the same
  weights.
- D-S7 recorded: weights feed `promotionIntervalDays` too — document in
  AGENTS.md's ladder-movement paragraph and `docs/ladder-and-srs-system.md`
  §14 (close improvement idea I3's plumbing half).
- Behavior-neutral gate: with the key unset, every golden timeline and
  `SchedulerParitySnapshotTest` snapshot must be byte-identical. If any
  golden changes, the plumbing is wrong — fix the code, never the golden.

**Done when (machine-checkable):**

1. `./gradlew :core:test` green with goldens byte-identical (`git diff --exit-code core/src/test/resources` clean after the run).
2. New core test: `BridgeScheduler.withWeights` with a crafted weight vector produces a different `nextIntervalDays` than defaults for the same review (proves the wiring), and null reproduces defaults exactly.
3. App unit tests: settings round-trip preserves full precision (`0.0658` stays `0.0658`); malformed strings (20 values, NaN, `w20 <= 0`) fall back to defaults with one diagnostic line.
4. AGENTS.md + `docs/ladder-and-srs-system.md` updated in the same commit (D-S7).
5. `./gradlew ciFast` exits 0.

---

### Goal 93: On-device FSRS weight fitting (opt-in, evaluation-gated)

**Problem:** Parameters are fixed at the FSRS-6 defaults;
`docs/ladder-and-srs-system.md` §14 I3 already states "review_log stores
everything needed to fit per-user weights offline." Per review, `review_log`
stores the applied rating, `reviewed_at`, the rung, and — critically — the
exact pre-review memory state the engine consumed (`memory_before`,
tab-separated `TaskMemory.encode()` with stability field 3 / difficulty
field 4 / `dueAtMillis` / `matureIntervalDays`) plus the pre-review `phase`
inside `scheduler_state_before_json` (`LocalStoreStudy.insertReview:224-251`,
`studyItemSchedulerJson:260-288`). Known gaps: rows older than the DB v12
rich-column migration have empty blobs; learning/relearning practice rows
must not train review-interval behavior; evidence-seeded items start
mid-flight from Anki-derived state. No optimizer or loss-metric code exists
anywhere (grep `logloss|RMSE|optimizer|gradient` → docs only).

**Goal:**

- **Training extraction** (`app/.../data/FsrsTrainingDataQueries`): per
  `(kanji, answer_signature, task_type)` group, ordered by `reviewed_at`,
  emit sequences of `(elapsedDays, rating, outcome)` where:
  - rows with empty `memory_before` or missing `phase` are dropped (legacy);
  - `phase == "review"` rows are training samples; same-day review-phase
    rows stay as `elapsedDays = 0` short-term samples (upstream FSRS-6 fits
    those; the engine has the short-term branch,
    `DefaultFsrsEngine.kt:32-44`);
  - `new_learning`/`relearning` practice rows are excluded from prediction
    loss but the sequence seed is the first review-phase row's decoded
    `memory_before` (documented approximation that also covers
    evidence-seeded items — their Anki-derived starting S/D is exactly what
    the engine consumed);
  - elapsed derivation mirrors `ReviewContext.elapsedReviewDays()`
    (`ReviewTransitionEngine.kt:626-631`):
    `floor((reviewed_at − (dueAtMillis − matureIntervalDays·DAY)) / DAY)`,
    clamped ≥ 0.
- **Evaluator + fitter in `:core`** (D-S7; `:fsrs-java` stays pristine):
  - `FsrsReplayEvaluator`: given weights, replay each sequence with
    `FsrsEngine.create(...)` — predict
    `R = engine.retrievability(state, elapsed)` before each sample, outcome
    `y = (rating != again)`, accumulate log-loss; forward state with
    `engine.nextState(...)`.
  - `FsrsWeightFitter`: Adam over finite-difference gradients on the 21-dim
    vector, seeded from `FsrsParameters.LATEST_DEFAULT_TEMPLATE`, each
    weight clamped to the upstream FSRS-6 bounds table (embed the table with
    a source comment), early-stopping on validation loss. Time-ordered
    split: oldest 80% train, newest 20% validation.
  - **Adoption gate:** write `scheduler_fsrs_weights` (Goal 92 key) only
    when the training set has ≥ 400 review-phase samples AND validation
    log-loss improves ≥ 1% relative vs the default weights. Otherwise keep
    defaults and record why. Always write a summary JSON to
    `scheduler_fsrs_fit_summary` (sample counts, train/val loss for both
    weight sets, adopted flag, timestamp) — no new table.
- **Background work:** `FsrsFitWorker` (plain `Worker` +
  `LocalStore(context).use { }`, the `DatabaseBackupWorker` template) under
  unique periodic work `kani_fsrs_fit` (7 days, KEEP) with
  `setRequiresCharging(true)` + `setRequiresBatteryNotLow(true)` (the
  `AutoUpdateScheduler` constraints pattern); check `isStopped` between
  epochs. Scheduled only while the feature toggle is on.
- **Settings UI:** extend the Retention panel (Study behavior) or add a
  "Personalized scheduling" panel: opt-in toggle (default OFF), status line
  from the fit summary ("Using your fitted weights — 3.2% better on your
  last 1,840 reviews" / "Using defaults — not enough history yet"),
  "Fit now" (one-shot expedited work), "Reset to defaults" (clears both
  keys). Turning the toggle off clears `scheduler_fsrs_weights`.

**Done when (machine-checkable):**

1. `./gradlew :core:test --tests "dev.bee.kanjianki.core.FsrsReplayEvaluatorTest" --tests "dev.bee.kanjianki.core.FsrsWeightFitterTest"` passes: evaluator log-loss matches a hand-computed fixture; fitter on synthetic data generated from known non-default weights reduces validation loss vs defaults and respects bounds; adoption gate refuses < 400 samples and < 1% improvement; deterministic given a fixed seed.
2. App unit tests: extraction filters (legacy rows, practice rows, kana-free grouping), elapsed derivation equals the `ReviewContext` mirror on crafted rows, fit summary JSON round-trips.
3. Worker test: schedules only when toggled on; cancel path clean; adoption writes the Goal 92 key such that the next constructed `BridgeScheduler` uses it (integration unit test through the settings read).
4. With the toggle OFF (default), goldens and parity snapshots byte-identical — `ciFast` proves it.
5. `docs/ladder-and-srs-system.md` §14: I3 marked closed-by-93 with the adoption-gate description; AGENTS.md scheduler notes updated in the same commit.
6. `./gradlew ciFast` exits 0.

---

## Batch E: Closing the repair loop (Goal 94)

### Goal 94: Repaired-kanji write-back — `kani_repaired` tagging + unsuspend hand-off

**Problem:** The repair loop never closes. Kani imports suspended cards,
repairs the kanji, retires the item on Anki evidence
(`StudyQueueSeeder.shouldRetireSeedItem:244-259`,
`matureSupportCount >= matureSupportThreshold`) — and then nothing tells
Anki. The user has no signal about which suspended cards are safe to
unsuspend. The building blocks all exist: the kanji → original Anki
card/note ids map persists in `suspended_sources(kanji, card_id, note_id)`
(survives archive tagging, unlike the 8-example-capped `kanji_examples`);
the proven idempotent per-note tag write exists
(`AnkiDroidArchiveCleanup.tagNoteArchived:73-90`); the
"all cards of the note" asymmetry rule exists
(`ProviderArchiveCleanupPolicy.plan`); `suspended_archive.restored_at` was
created for exactly this and is used by nothing (`LocalStoreSchema.kt:15`).
Direct card unsuspend through the provider is **unproven**: the `cards` URI
is consumed strictly read-only, and upstream documents card update only for
deck moves and answering (D-S8).

**Goal:** Tag-only write-back plus a guided hand-off.

- **Proposal policy** (`core` or `sync-domain`:
  `RepairedWriteBackPolicy`): input per-kanji repair state (study item
  `state == retired` via `StudyLadderRules.STATE_RETIRED`, or
  `matureSupportCount >= settings.matureSupportThreshold` with
  `KanjiRepairEvidencePolicy.Status.IMPROVING` at confidence ≥ 0.75 — the
  cohort's high band) + `suspended_sources` rows + already-stamped
  `restored_at` values; output note ids to tag `kani_repaired` (constant
  beside `ProviderNotePolicy.ARCHIVED_TAG`), applying the same
  every-card-of-the-note rule as archive cleanup, and the kanji list for the
  hand-off card. Never proposes a note twice (`restored_at` stamp).
- **Sync phase:** in `ManualSyncEngine.runLocked`, immediately after the
  archive-cleanup call (`:166-182`), same envelope — provider writes can
  never fail the sync; failures degrade to a "will retry next sync" message.
  New `SyncProgress.Stage.TAGGING_REPAIRED` + `SyncProgressCopy` mapping
  (the `coreStage` `when` is exhaustive — compiler enforces) + spinner case
  in `SyncProgressPanel.kt:103`. Gateway grows
  `CollectionGateway.tagRepairedNotes(...)` **with a default no-op
  implementation** (≈10 anonymous test fakes implement the interface;
  they must keep compiling). Implementation mirrors
  `AnkiDroidArchiveCleanup` exactly: per-note isolation, idempotent
  read-modify-write on `content://<auth>/notes/<noteId>` `tags`, summary
  message persisted via the `updateSyncRemovalMessage` pattern (sibling
  message or appended). On success, stamp `suspended_archive.restored_at`
  and append a `kanji_timeline_events` row (new event type
  `repair_tagged`, rendered in the browse timeline like `retired`).
- **Gating:** settings toggle `tag_repaired_cards` (default OFF) in the
  Anki-source import-filters panel (`SyncSettings` key + `boolSetting`
  pattern). First-run consent: when the toggle is on and proposals exist,
  the sync confirm dialog body (`HomeImportOnboardingPolicy.plan` →
  `HomeSyncConfirmDialogModels`) states "…and tag N repaired kanji in
  AnkiDroid".
- **Hand-off UX:** after a sync that tagged ≥ 1 note, the Home screen shows
  a "N kanji repaired" card (model + copy in core): body lists the kanji,
  primary action copies the AnkiDroid browser search
  `tag:kani_repaired is:suspended` to the clipboard with a toast
  ("Paste in AnkiDroid's card browser, select all, unsuspend"), secondary
  dismisses. This is the honest unsuspend path until direct card writes are
  proven.
- **Probe (time-boxed, non-blocking):** one manual experiment on the live
  emulator — attempt `resolver.update` of `queue` on a card URI against real
  AnkiDroid 2.24.0; record the observed result (expected: rejected) in this
  plan's status blockquote and in `docs/anki-manual-parity-checklist.md`.
  If it ever succeeds, a *future* goal may add direct unsuspend behind the
  same toggle; this goal does not.
- **Fake provider extension:** teach `FakeAnkiDroidProvider.update` to
  accumulate repaired tags per note + counters (`repairedTags`) + `call`
  accessors + `reset()` entries, mirroring the archive counters.

**Done when (machine-checkable):**

1. Policy tests (`:core` or `:sync-domain` per placement): retirement/evidence gate incl. confidence band, every-card-of-note rule, `restored_at` dedupe, empty-input no-op.
2. Instrumented fake-provider suite mirrors the seven archive-cleanup tests for repaired tagging (already-tagged idempotence, null tags cursor, partial failure isolation, un-updatable note keeps proposal for retry, excluded notes untouched) via `AnkiDroidGatewayProviderInstrumentedTest` additions.
3. `ManualSyncEngine` tests: tagging failure never fails the sync; message persisted; stage sequence includes `TAGGING_REPAIRED` after archive cleanup; toggle OFF → phase skipped entirely.
4. Home card model test: renders kanji list; primary action puts the exact search string on the clipboard.
5. **Live gate (mandatory — provider/sync change):** full local real-collection AnkiDroid emulator run per AGENTS.md (7,000-note threshold) including the targeted suite plus the new tagging tests; result recorded in this plan's status blockquote before any release containing this goal.
6. AGENTS.md sync/write-back section updated in the same commit (Kani's write surface is now: archive tag + repaired tag; still never scheduling state).
7. `./gradlew ciFast` exits 0 and `./gradlew ciRelease` assembles with signing env vars.

---

## Batch F: Consistency and release (Goal 95)

### Goal 95: Consistency pass, doc truth-up, screenshot matrix, release gate

**Problem:** Fourteen goals touch AGENTS.md-documented behavior, the stats
baselines, and the decision log; and the research pass found one existing
doc lie: AGENTS.md describes the sync confirm button as
"Sync and tag archive", but the code has never-matching labels — the live
dialog confirms with "Sync cards"
(`HomeImportOnboardingPolicy` labels; instrumented tests click
`"Sync cards"` at `MainActivityInstrumentedTest.kt:2068-2172`).

**Goal:**

- Fix the stale AGENTS.md "Sync and tag archive" wording to match the real
  dialog labels; re-verify every AGENTS.md claim touched by Goals 82–94
  (backup retention description, write-back surface, widget/eligibility
  invariant D-S6, fitted-weights note D-S7).
- Record D-S1…D-S8 in `docs/ladder-and-srs-system.md` §14 (the repo-wide
  decision log), closing I3 (Goals 92–93) and cross-linking D4 (D-S7).
- Regenerate the full theme screenshot matrix one final time after all
  stats goals; commit baselines; contact sheets reviewed against the ≥ 8/10
  bar from the design doc.
- Full gates: `./gradlew ciFast`; `./gradlew ciQuality` (Sonar inputs
  deterministic — new core policies are 100%-covered by construction);
  `./gradlew ciRelease` with signing vars; SonarCloud quality gate green on
  `main` (zero new smells/bugs/hotspots); and the Goal 94 live AnkiDroid
  gate result recorded here.
- Add a status blockquote to the top of this file summarizing landed /
  partial / deferred goals, following the house convention.

**Done when (machine-checkable):**

1. `grep -n "Sync and tag archive" AGENTS.md` returns zero hits; the replacement quotes the real label(s).
2. `docs/ladder-and-srs-system.md` §14 contains D-S1…D-S8 and marks I3 closed.
3. Screenshot baselines committed; `ci/scripts/capture_kani_theme_screenshots.sh` manifest clean.
4. `./gradlew ciFast` and `./gradlew ciRelease` (with signing env) exit 0; `gh run watch <RUN_ID> --exit-status` green for the first `Android CI` run containing the batch.
5. Status blockquote present at the top of this file.

---

## Suggested sequencing

| Goal | Deliverable | Depends on |
|---|---|---|
| 82 | Legacy stats stack deleted | — |
| 83 | Truthful stats data + real axes (cache v6) | 82 |
| 84 | Typography ramp + shared chart/metric components | 83 |
| 85 | Visual redesign + empty states + screenshot pass | 84 |
| 86 | Review heatmap card (cache bump) | 84 |
| 87 | Confusion pairs card (cache bump) | 84 |
| 88 | Ladder-completion forecast card (cache bump) | 84; respects 92 when present |
| 89 | Backup export panel (SAF) | — |
| 90 | Staged restore | 89 |
| 91 | Glance widget | — |
| 92 | FSRS weights plumbing (behavior-neutral) | — |
| 93 | On-device fitting (opt-in) | 92 |
| 94 | Repaired write-back + hand-off (live gate) | — |
| 95 | Consistency + release gate | all |

Three independent tracks can proceed in parallel after Goal 84:
stats cards (86–88), data safety (89–90), and scheduling (92–93). Goals 91
and 94 are independent. Goals 86–88 each bump
`STATS_CACHE_FORMAT_VERSION` in their own commit — never share a bump across
goals.

## Validation gates (see AGENTS.md for full detail)

- Every goal: `./gradlew ciFast` exits 0 (with the
  `ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk` prefix
  on this machine when `local.properties` is absent).
- Golden rules: Goals 82–91 and 95 must keep all scheduler goldens
  byte-identical. Goal 92 is behavior-neutral by definition (unset key);
  Goal 93 with the toggle OFF likewise. Regenerate goldens only from
  `SchedulerTimelineSimulator` output, never by hand — and for this plan no
  goal should need to.
- New `:core` classes ship with tests (JaCoCo 100% class-coverage
  verification is wired into `check` by the library convention plugin).
- Live AnkiDroid emulator gate is mandatory **only** for Goal 94
  (provider/sync change; 7,000-note local real-collection run). Goals 82–93
  do not touch the provider and must not be blocked on emulator runs.
- Screenshot baselines (`screenshots/kani-theme-matrix/`) change only in
  Goals 84, 85, 86, 87, 88, 95, each with reviewed diffs.
- Workflow files are untouched by this plan; if that changes, watch the
  first GitHub Actions run to completion (`gh run watch RUN_ID
  --exit-status`).

## Appendix A — why not other approaches

Recorded so the next review does not re-litigate.

- **Reviving the legacy verdict-card stats route** instead of extending
  ProgressAnalytics: rejected — two surfaces caused the current drift;
  the verdict/forecast content moves into the live surface (Goal 88).
- **JLPT levels on the progress section:** rejected — no JLPT data exists in
  the bundled dictionary; rung distribution is the honest axis. If JLPT/Jiten
  banding is ever wanted, it needs a data-source goal first.
- **Long-horizon confusion store** (beyond 90 days): rejected for now —
  contradicts the pruning fix and the miner's definition of "recent
  confusion" (D-S3).
- **Per-item analytic forecast math** instead of simulation: rejected — it
  cannot model queue-cap admission waves, promotion caps, or conditional
  rung crossings; the simulator already encodes all of it (D-S4).
- **In-process restore with a "close everything" registry:** rejected — 18
  unregistered store construction sites make quiescence unprovable; staged
  swap-at-startup is smaller and crash-safe (D-S5).
- **Widget periodic refresh worker:** rejected — event-driven updates at the
  three write sites plus the daily reminder wake cover freshness without a
  new periodic job.
- **Optimizer inside `:fsrs-java`:** rejected — the module's value is being
  a pinned upstream snapshot (`FsrsAlgorithmInfo`); the fitter is Kani
  policy, so it lives in `:core` (D-S7).
- **Direct provider unsuspend as the primary Goal 94 path:** rejected until
  proven — the repo's provider knowledge says cards are read-only, and a
  release cannot hang on an unvalidated write; tag + guided hand-off ships
  value regardless of the probe's outcome (D-S8).
- **AnkiDroid deck-config weight import** (the "cheap" I3 variant): rejected
  — the FlashCardsContract surface consumed here exposes no deck
  configuration (`docs/anki-manual-parity-checklist.md:84-90`).
