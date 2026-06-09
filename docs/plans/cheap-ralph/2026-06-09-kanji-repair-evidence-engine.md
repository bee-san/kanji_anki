# Kanji Repair Evidence Engine Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Build a kanji-level evidence/explainability layer that tells the user whether each repaired kanji is **Improving**, **Stable**, **Regressing**, or has **InsufficientEvidence**, with concrete before/after AnkiDroid evidence, Kani review evidence, writing failures, ladder state, and careful non-causal attribution language.

**Architecture:** Add a pure Kotlin core policy first, then wire it through app data queries, store/cache APIs, per-kanji UI cards, and cohort dashboards. Keep the core decision logic Android-free and testable. Use existing outcome evidence (`StudyStatsQueries`/`StudyStatsStore`), ladder evidence (`LadderHealthPolicy` and `study_items`), Kani review logs (`review_log`), sync snapshots (`sync_kanji_snapshots`), dashboard rows/examples (`dashboard_rows`, `kanji_examples`), and timeline data (`kanji_timeline_events`) rather than inventing a parallel evidence store prematurely.

**Tech Stack:** Kotlin/JVM in `:core` for policies, Android/Kotlin in `:app` for SQLite data access and Compose/View models, Gradle tests via `./gradlew :core:test` and `./gradlew :app:testDebugUnitTest`, JUnit + Robolectric for app data/UI model tests, existing SQLite schema and stats cache infrastructure.

## Current repo anchors verified before writing this plan

Use these existing files/classes as the implementation base:

- `core/src/main/kotlin/dev/bee/kanjianki/core/KaniOutcomePolicy.kt`
  - Existing outcome summarizer: `KaniOutcomePolicy.summarize(...)` produces `WeakKanjiImprovedMetric`, `MatureSupportGainedMetric`, and carries `LadderHealthPolicy.Metric`.
  - Existing per-kanji values: `OutcomeEvidence(kanji, before, after)` and `OutcomeSnapshot(weaknessScore, matureSupportCount)`.
  - Existing improvement threshold: weakness drop must be at least 5 raw points before it is counted as an improvement.
- `app/src/main/kotlin/dev/bee/kanjianki/data/StudyStatsQueries.kt`
  - Existing `kaniOutcomeStats()` pulls `outcomeEvidence(db())` and `ladderItems(db())`.
  - `outcomeEvidence(...)` compares `sync_kanji_snapshots` before first Kani review and after last Kani review.
  - Existing review evidence queries include `recentMistakes(limit)`, `studyImpactStats()`, `reviewStatsSince(...)`, and `studiedKanjiSince(...)`.
- `app/src/main/kotlin/dev/bee/kanjianki/data/StudyStatsStore.kt`
  - Existing app-facing metrics: `KaniOutcomeStats`, `WeakKanjiImprovedMetric`, `MatureSupportGainedMetric`, `LadderHealthMetric`, `RecentMistake`, `StudyImpactStats`, `OutcomeEvidence`, `LadderItemEvidence`.
  - Existing bridge methods convert app evidence into `KaniOutcomePolicy` and `LadderHealthPolicy` core models.
- `core/src/main/kotlin/dev/bee/kanjianki/core/LadderHealthPolicy.kt`
  - Existing ladder evidence model: `ItemEvidence(state, rung, phase, realPassStreak, realAgainStreak, matureIntervalDays)`.
  - Existing metrics: `totalActiveItems`, rung counts, `promotionReadyCount`, `demotionRiskCount`, `demotionReadyCount`.
- `core/src/main/kotlin/dev/bee/kanjianki/core/KanjiImpactAnalyzer.kt`
  - Existing correlation-style impact analyzer with buckets `helped`, `not_helping_yet`, `needs_more_cards`.
  - Existing caution threshold: `MIN_REVIEWS_TO_JUDGE = 3`.
  - Existing non-causal advice language is already close to the desired tone: “appears to be helping”, “not moving the needle yet”, and “before judging Kani”.
- `app/src/main/kotlin/dev/bee/kanjianki/data/KanjiImpactReportStore.kt`
  - Existing app data adapter for historical sync snapshots, same-card comparisons, review counts, and latest successful sync.
  - Useful patterns for querying `sync_runs`, `sync_kanji_snapshots`, `sync_card_snapshots`, `sync_note_snapshots`, `study_items`, and `suspended_imports`.
- `core/src/main/kotlin/dev/bee/kanjianki/core/StatsTextCopy.kt` and `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStatsCards.kt`
  - Existing stats copy/cards for “Kani is working”, “Waiting for evidence”, weak kanji trend, Anki support, study impact, needs attention, ladder status, recent mistakes, and study time.
- `app/src/main/kotlin/dev/bee/kanjianki/data/LocalStoreTableCreator.kt`
  - Existing historical sync schema includes `sync_kanji_snapshots` with `finished_at`, `kanji`, `mature_support_count`, `weakness_score`, `reason_code`, `active_example_count`, and `suspended_example_count`.
  - Existing review/stats indexes include `idx_review_log_kanji_reviewed` and `idx_sync_kanji_snapshots_kanji_finished`.
- `app/src/main/kotlin/dev/bee/kanjianki/data/LocalStoreBase.kt`
  - Existing `review_log` has `kanji`, `rating`, `writing_required`, `writing_passed`, `manual_override`, `reviewed_at`, `task_type`, `answer_signature`, `prompt`, and `hints_used`.
  - Existing `study_items` has ladder state/rung/phase, pass/miss streak fields, mature interval days, answer signature, and writing remediation fields.
- `core/src/main/kotlin/dev/bee/kanjianki/core/RecordsImportModels.kt`, `core/src/main/kotlin/dev/bee/kanjianki/core/FocusQueueCopy.kt`, and `app/src/main/kotlin/dev/bee/kanjianki/data/LocalStoreInventory.kt`
  - Existing `DashboardRow` carries `weaknessScore`, `reasonCode`, `reasonText`, `activeExampleCount`, `suspendedExampleCount`, `matureSupportCount`, and `examples`.
  - Existing `Example` carries source type, expression, reading, meaning, sentence, maturity, lapses, interval, reps, and FSRS values.
  - `FocusQueueCopy.sourceEvidenceText(...)` already surfaces “From <active> · missed <suspended>”.
- Existing tests to preserve and extend:
  - `core/src/test/kotlin/dev/bee/kanjianki/core/KaniOutcomePolicyTest.kt`
  - `core/src/test/kotlin/dev/bee/kanjianki/core/LadderHealthPolicyTest.kt`
  - `core/src/test/kotlin/dev/bee/kanjianki/core/KanjiImpactAnalyzerTest.kt`
  - `core/src/test/kotlin/dev/bee/kanjianki/core/StatsTextCopyTest.kt` explicitly preserves “Kani is working” and “Waiting for evidence” verdict copy.
  - `app/src/test/kotlin/dev/bee/kanjianki/data/StudyStatsStoreTest.kt`
  - `app/src/test/kotlin/dev/bee/kanjianki/data/StudyStatsStoreOutcomeMetricsTest.kt`
  - `app/src/test/kotlin/dev/bee/kanjianki/data/StudyStatsStoreCoverageTest.kt`
  - `app/src/test/kotlin/dev/bee/kanjianki/data/StatsCacheStoreTest.kt`
  - `app/src/test/kotlin/dev/bee/kanjianki/data/LocalStoreTimelineCacheTest.kt`
  - `app/src/test/kotlin/dev/bee/kanjianki/ComposeScreenModelsTest.kt`

## Non-goals and language rules

- Do **not** claim “Kani caused this improvement.” Use correlation language: “After Kani reviews…”, “Kani reviews are associated with…”, “AnkiDroid evidence improved after study…”, “not enough evidence yet.”
- Do **not** replace existing outcome stats. This project adds a per-kanji evidence/explanation layer under the existing aggregate metrics.
- Do **not** add new SQLite tables in the first PR. Start with existing snapshots/logs; add caching only after the query and UI shape settle.
- Do **not** make “Waiting for evidence” look like failure. Missing post-review syncs, too few post-review samples, or sparse cards are expected states.

## Phase 1 — Core domain model and policy

### Proposed new core file

Create a new core-only policy next to existing pure policies:

- Proposed: `core/src/main/kotlin/dev/bee/kanjianki/core/KanjiRepairEvidencePolicy.kt`
- Proposed tests: `core/src/test/kotlin/dev/bee/kanjianki/core/KanjiRepairEvidencePolicyTest.kt`

### Domain model

Keep this Android-free. Recommended core model shape:

```kotlin
object KanjiRepairEvidencePolicy {
    enum class Status { IMPROVING, STABLE, REGRESSING, INSUFFICIENT_EVIDENCE }

    class Evidence(
        val kanji: String,
        val status: Status,
        val reason: String,
        val explanation: String,
        val beforeWeakness: Int?,
        val afterWeakness: Int?,
        val beforeMatureSupport: Int?,
        val afterMatureSupport: Int?,
        val kaniReviews: Int,
        val writingFailures: Int,
        val lastMistakeAtMillis: Long,
        val lastSyncAtMillis: Long,
        val confidence: Double,
        val confidenceReason: String,
    )
}
```

Model notes:

- Preserve requested status labels in UI as `Improving`, `Stable`, `Regressing`, `InsufficientEvidence` even if Kotlin enum names are uppercase.
- `confidence` should be a clamped `0.0..1.0`, not a pseudo-statistical probability.
- `reason` should be machine-readable enough for UI/filtering, e.g. `improved_after_reviews`, `more_mature_support`, `no_post_review_sync`, `too_few_post_review_samples`, `still_failing_after_reviews`, `stable_after_reviews`.
- `explanation` should be human-readable and anti-overclaiming.
- Store raw weakness/support as nullable integers to distinguish “zero” from “not sampled”. The aggregate metrics currently normalize weakness to `0.0..1.0`; per-kanji explainability should show source raw scores where available.

### Policy inputs

Use a single input object so later app wiring stays simple:

```kotlin
class Input(
    val kanji: String?,
    val before: Snapshot?,
    val after: Snapshot?,
    val kaniReviews: Int,
    val postReviewSamples: Int,
    val writingFailures: Int,
    val lastMistakeAtMillis: Long,
    val firstReviewAtMillis: Long,
    val lastReviewAtMillis: Long,
    val lastSyncAtMillis: Long,
    val ladder: Ladder?,
)
```

Suggested support classes:

- `Snapshot(weaknessScore: Int, matureSupportCount: Int, sampledAtMillis: Long, activeExampleCount: Int, suspendedExampleCount: Int, reasonCode: String?)`
- `Ladder(rung: RecordsBase.LadderRung?, phase: RecordsBase.SchedulerPhase?, realPassStreak: Int, realAgainStreak: Int, matureIntervalDays: Int)`

### Required status/confidence behavior

Cover these cases first; this is the best first issue.

| Case | Expected status | Confidence posture | Explanation rule |
|---|---:|---:|---|
| No Kani reviews | `InsufficientEvidence` | very low | “No Kani reviews recorded for this kanji yet.” |
| No before snapshot | `InsufficientEvidence` | low | “Need a baseline AnkiDroid sync before judging this kanji.” |
| No after snapshot | `InsufficientEvidence` | low | “Study recorded; waiting for a later AnkiDroid sync.” |
| `lastSyncAtMillis <= lastReviewAtMillis` | `InsufficientEvidence` | low | “No sync has landed since the latest Kani review.” |
| Fewer than 3 Kani reviews or fewer than 2 post-review samples | `InsufficientEvidence` unless there is a large support/weakness change | low/medium | “Too few post-review samples to judge confidently.” |
| Weakness drops by at least 5 raw points after reviews | `Improving` | medium/high if post-review sync exists and sample count is adequate | “After Kani reviews, AnkiDroid weakness moved X → Y.” |
| Mature support increases | `Improving` | medium/high based on sample count | “After Kani reviews, mature AnkiDroid support moved A → B.” |
| Weakness rises by at least 5 raw points or mature support drops, with recent mistakes/writing failures | `Regressing` | medium if sampled after reviews | “Evidence is worse after study; keep reviewing before attributing.” |
| Small changes with adequate samples | `Stable` | medium | “Evidence is about unchanged after reviews.” |
| Still failing despite reviews: recent `again`/`hard` or writing failures after reviews, without improved sync evidence | `Regressing` or `Stable` depending on snapshot deltas | low/medium | “Still seeing mistakes/writing failures after Kani reviews.” |

Use thresholds intentionally aligned with existing code:

- Weakness improvement threshold: 5 raw points, matching `KaniOutcomePolicy`.
- Minimum reviews to judge: 3, matching `KanjiImpactAnalyzer`.
- Support gain threshold: any positive increase in `matureSupportCount`.

### First PR acceptance criteria

- `KanjiRepairEvidencePolicy` exists in `:core` only.
- Tests cover all anti-overclaiming cases above.
- Policy returns a status and an explanation string for every non-null/blank kanji input.
- Null/negative unsafe input is clamped/defaulted consistently with `KaniOutcomePolicy`, `LadderHealthPolicy`, and `StudyStatsStore` public models.
- No Android imports, no database, no UI.

## Phase 2 — App data access and per-kanji repair evidence card

### App data adapter

After the core policy lands, add an app adapter that reads existing SQLite evidence and returns app-facing evidence models.

Prefer extending existing seams rather than creating a new persistence table immediately:

- Extend `StudyStatsQueries` with a query method that emits policy input rows per kanji.
- Extend `StudyStatsStore` with public/app-facing wrappers, following existing nested-model style used by `KaniOutcomeStats`, `OutcomeEvidence`, and `LadderItemEvidence`.
- Consider a small `KanjiRepairEvidenceStore` only if `StudyStatsStore` becomes too broad; if created, keep it as an app adapter around the core policy, not a second policy.

Required data sources:

- `review_log`
  - `COUNT(*) AS kaniReviews`
  - `MIN(reviewed_at) AS firstReviewAtMillis`
  - `MAX(reviewed_at) AS lastReviewAtMillis`
  - `MAX(CASE WHEN rating IN ('again','hard') THEN reviewed_at ELSE 0 END) AS lastMistakeAtMillis`
  - `SUM(CASE WHEN writing_required=1 AND writing_passed=0 AND manual_override=0 THEN 1 ELSE 0 END) AS writingFailures`
  - Optional task breakdown by `task_type`, `answer_signature`, `prompt`, and `hints_used` for richer explanations later.
- `sync_kanji_snapshots`
  - Before snapshot: latest row before first review, same pattern already used by `StudyStatsQueries.outcomeEvidence(...)`.
  - After snapshot: latest row after last review, same pattern already used by `StudyStatsQueries.outcomeEvidence(...)`.
  - Fields: `finished_at`, `weakness_score`, `mature_support_count`, `reason_code`, `active_example_count`, `suspended_example_count`.
- `study_items`
  - Current ladder rung, phase, `real_pass_streak`, `real_again_streak`, `mature_interval_days`.
- `dashboard_rows` / `kanji_examples`
  - Why selected: `reasonCode`, `reasonText`, `weaknessScore`, support counts.
  - Failed source words: examples with `sourceType == "suspended"`, high `lapses`, or weak FSRS values.
  - Active/source words: examples with `sourceType == "active"`.
- `import_decisions` can be used later for historical “why imported/selected” if the dashboard row has already changed.
- `kanji_timeline_events` can enrich the card, but should not be mandatory for the first app wiring.

### Per-kanji Repair Evidence card

Add a per-kanji model that can be rendered wherever kanji detail/timeline is shown. Existing candidate surfaces:

- `LocalStoreInventory.timelineForKanji(...)` returns `RecordsStudyModels.KanjiRecoveryTimeline` and already combines inventory item, dashboard row, study item, and timeline events.
- Home/focus queue models already use `DashboardRow`, `StudyItem`, and `FocusQueueCopy`.

Card fields required by Bee:

1. **Why selected**
   - Use `DashboardRow.reasonText`, `reasonCode`, `weaknessScore`, `matureSupportCount`, and `FocusQueueCopy.queueCardBody(...)` where appropriate.
2. **Failed source words**
   - Use `DashboardRow.examples` / `kanji_examples`; prioritize suspended examples, high-lapse examples, and examples whose source type maps to missed/suspended evidence.
3. **Suspected cause**
   - Start heuristic-only: similar-kanji reason text → “shape mix-up”; writing failures → “writing recall”; low mature support → “not enough mature Anki support”; recent `again`/`hard` → “recent recall failures”.
4. **Kani reviews**
   - Show count, last review age, recent mistake status, and writing failure count.
5. **Before/after AnkiDroid evidence**
   - Show raw weakness `before → after`, mature support `before → after`, last sync timestamp, and sample caveat.
6. **Ladder rung**
   - Use `RecordsBase.LadderRung` + `StatsTextCopy.ladderRungLabel(...)` or a shared copy helper.
7. **Next action**
   - Derive from status:
     - Improving: “Keep normal reviews; sync after AnkiDroid study.”
     - Stable: “Review again, then sync to collect more evidence.”
     - Regressing: “Practice the failed source words / writing rung; do not claim success yet.”
     - InsufficientEvidence: “Study and sync after more samples.”

Create UI copy in a focused copy helper, not inline string soup. Existing patterns: `StatsTextCopy`, `FocusQueueCopy`, `TimelineCopy`.

## Phase 3 — Cohort dashboards

Extend the stats/cohort layer after per-kanji evidence exists.

### Dashboard requirements

- Counts by status: Improving, Stable, Regressing, InsufficientEvidence.
- Confidence distribution: high/medium/low or average confidence per status.
- Top improving kanji: weakness drop and support gained.
- Top regressing/still failing kanji: recent mistakes, writing failures, or worse snapshots.
- “Waiting for evidence” cohort: no post-review sync, too few post-review samples, no baseline.
- Ladder overlay: status by rung and counts for promotion/demotion risk.

### Existing surfaces to extend

- `MainActivityStatsCards.kt` currently builds stats screen cards from `StudyStatsStore.KaniOutcomeStats`, `KanjiImpactAnalyzer.Report`, `StudyImpactStats`, `StudyStreak`, and `RecentMistake`.
- `StatsCacheStore.kt` and `StatsPrecomputeStore.kt` already cache/precompute stats snapshots. Add repair evidence summaries there only after app data tests prove the live query shape.
- `StatsTextCopy.kt` should own cohort copy and keep non-causal wording.

### Suggested cohort model

```kotlin
class RepairEvidenceCohortStats(
    val improvingCount: Int,
    val stableCount: Int,
    val regressingCount: Int,
    val insufficientEvidenceCount: Int,
    val highConfidenceCount: Int,
    val lowConfidenceCount: Int,
    val examples: List<KanjiRepairEvidence>
)
```

Keep examples limited and sorted deterministically:

1. Regressing high-confidence first.
2. Improving high-confidence next.
3. Insufficient evidence grouped by actionable reason.
4. Tie-break by kanji string.

## Phase 4 — Repair attribution and anti-causality copy audit

Add attribution language only after cards and cohort summaries are backed by real evidence.

Required wording principles:

- Say “after Kani reviews” or “following Kani study”, not “because of Kani”.
- Say “associated with improved AnkiDroid evidence”, not “Kani fixed it”.
- When evidence is weak, lead with uncertainty: “Too few post-review samples”, “waiting for sync”, “not enough same-card evidence”.
- For regressions, avoid blame: “Evidence is worse after study; keep collecting data.”
- For mature support gains, say “more mature AnkiDroid support is now present” rather than “learned”.

Add tests that grep-like assert no new stats/evidence copy contains banned phrases such as:

- “caused”
- “proves”
- “fixed by Kani”
- “Kani made you learn”

Use exact copy tests rather than runtime scanning if that matches existing test style better.

## TDD task plan by Cheap Ralph slice

| Slice | Scope | Main files | Tests | Acceptance |
|---|---|---|---|---|
| 1. Best first issue: core policy | Add `KanjiRepairEvidencePolicy` with status/confidence/explanation rules. | Proposed new core policy file next to `KaniOutcomePolicy.kt`. | New `KanjiRepairEvidencePolicyTest`. Run `./gradlew :core:test --tests dev.bee.kanjianki.core.KanjiRepairEvidencePolicyTest`. | All required anti-overclaiming cases pass; no Android/db code. |
| 2. App wrapper models | Add app-facing model wrappers and conversion helpers, mirroring `StudyStatsStore.OutcomeEvidence` style. | `StudyStatsStore.kt`; maybe a small app adapter if needed. | Extend `StudyStatsStoreTest` or add `StudyStatsStoreRepairEvidenceTest`. | Public app model clamps unsafe values and maps core statuses/copy. |
| 3. Evidence SQL query | Query per-kanji review counts, writing failures, last mistake, before/after snapshots, ladder item. | `StudyStatsQueries.kt`; reuse query patterns from `outcomeEvidence(...)` and `ladderItems(...)`. | Robolectric data test using `LocalStore`, `saveSuccessfulSync(...)`, `replaceStudyItems(...)`, and `saveReview(...)` patterns from existing tests. | Query returns correct before/after sync around review times and low-confidence no-post-sync cases. |
| 4. Failed source words and suspected cause | Add a pure mapping from `DashboardRow.examples` + review evidence to source-word/cause lines. | Core copy/helper or app model helper; existing `FocusQueueCopy.kt` as pattern. | Core copy tests near `FocusQueueCopyTest` or app model tests. | Suspended/high-lapse examples appear as failed source words; similar-kanji/writing/support causes are deterministic. |
| 5. Per-kanji evidence card model | Create the card model with why selected, failed words, suspected cause, Kani reviews, before/after evidence, ladder rung, next action. | Existing UI model area near `HomeFocusQueueModel.kt`, timeline/detail models, or stats model files. | `ComposeScreenModelsTest` plus focused model tests. | Card is renderable with all required fields and correct empty states. |
| 6. Per-kanji UI integration | Render card on kanji detail/timeline or focus queue surface without blocking existing cards. | Existing timeline/focus Compose files; `LocalStoreInventory.timelineForKanji(...)` if using timeline. | Compose/model tests; light instrumentation only if needed. | User can inspect a kanji and see evidence/explanation. |
| 7. Cohort summary policy | Aggregate per-kanji evidence into counts/examples. | Core policy/helper and app store summary method. | Core tests for sorting/tallying; app tests for store integration. | Dashboard can show status counts and top examples. |
| 8. Stats dashboard card | Add cohort dashboard cards to stats screen. | `MainActivityStatsCards.kt`, `StatsTextCopy.kt`, stats screen model tests. | `ComposeScreenModelsTest`, `StatsTextCopy` tests. | Cohort card preserves “Kani is working”/“Waiting for evidence” behavior and adds per-status explanation. |
| 9. Cache/precompute integration | Cache repair cohort summary if live query is too expensive. | `StatsCacheStore.kt`, `StatsCacheCodec.kt`, `StatsPrecomputeStore.kt`. | Existing stats cache tests plus legacy-cache fallback tests. | Legacy cache still decodes; source-version invalidation works. |
| 10. Attribution copy audit | Enforce non-causal language and docs/release notes. | Copy helpers and tests. | Copy tests. Run full relevant module tests. | No overclaiming copy ships. |

## Best first issue details

Implement only this first:

1. Add `KanjiRepairEvidencePolicy` in `:core`.
2. Add `KanjiRepairEvidencePolicyTest` with fixture builders for snapshots, ladder evidence, and review/sync timestamps.
3. Use existing thresholds:
   - weakness drop/gain threshold: `5` raw points.
   - reviews needed to judge: `3`.
4. Return a deterministic explanation string for every status.
5. Do not touch app data/UI/cache in this first PR.

Minimum first-issue tests:

- `improvingWhenWeaknessDropsAfterPostReviewSync()`
- `improvingWhenMatureSupportIncreasesAfterPostReviewSync()`
- `insufficientEvidenceWhenNoKaniReviews()`
- `insufficientEvidenceWhenNoSyncSinceStudying()`
- `insufficientEvidenceWhenTooFewPostReviewSamples()`
- `regressingWhenWeaknessWorsensAndMistakesContinue()`
- `stableWhenEnoughEvidenceButNoMeaningfulDelta()`
- `stillFailingDespiteReviewsUsesLowConfidenceAntiOverclaimingExplanation()`
- `unsafeInputsAreClampedAndBlankKanjiDoesNotCrash()`

## Test commands

Run targeted tests as each slice lands:

```bash
./gradlew :core:test --tests dev.bee.kanjianki.core.KanjiRepairEvidencePolicyTest
./gradlew :core:test --tests dev.bee.kanjianki.core.KaniOutcomePolicyTest --tests dev.bee.kanjianki.core.LadderHealthPolicyTest --tests dev.bee.kanjianki.core.KanjiImpactAnalyzerTest
./gradlew :app:testDebugUnitTest --tests dev.bee.kanjianki.data.StudyStatsStoreRepairEvidenceTest
./gradlew :app:testDebugUnitTest --tests dev.bee.kanjianki.ComposeScreenModelsTest
```

Before merging any slice that touches app data/cache/UI, run:

```bash
./gradlew :core:test :app:testDebugUnitTest
```

If a slice changes stats cache serialization, also run the existing stats cache tests explicitly:

```bash
./gradlew :app:testDebugUnitTest --tests dev.bee.kanjianki.data.StatsCacheStoreTest --tests dev.bee.kanjianki.data.StatsCacheCodecTest --tests dev.bee.kanjianki.data.StatsCacheInvalidationTest
```

## End-to-end acceptance criteria

- Every active/reviewed kanji with available data can produce a `KanjiRepairEvidence` object containing:
  - status `Improving|Stable|Regressing|InsufficientEvidence`
  - reason and explanation string
  - before/after weakness
  - before/after mature support
  - Kani review count
  - writing failure count
  - last mistake timestamp
  - last sync timestamp
  - confidence
- The per-kanji repair evidence card shows:
  - why selected
  - failed source words
  - suspected cause
  - Kani reviews
  - before/after AnkiDroid evidence
  - ladder rung
  - next action
- The cohort dashboard shows counts/examples by status and confidence.
- Aggregate “Kani is working”/“Waiting for evidence” behavior remains compatible with existing stats cards.
- No UI copy claims causality.
- Insufficient-evidence states are explicit for:
  - too few post-review samples
  - no sync since studying
  - no baseline sync
  - no Kani reviews
- Regressing/still-failing states remain cautious and actionable.
- App data tests prove before/after snapshots are selected around Kani review times, not arbitrary syncs.
- Cache changes, if any, are backward-compatible with legacy stats cache rows.

## Implementation guardrails

- Keep policy code pure and deterministic.
- Prefer app adapters over putting SQL-specific fields into core models.
- Do not duplicate `KaniOutcomePolicy` aggregate logic; share thresholds/semantics where practical.
- Preserve existing `KanjiImpactAnalyzer` language style and thresholds unless tests justify a change.
- Add indexes only with a measured need; current useful indexes already include review log by kanji/reviewed time and sync snapshots by kanji/finished time.
- Keep PRs small enough that each can be reviewed independently and reverted without taking the whole evidence engine down.
