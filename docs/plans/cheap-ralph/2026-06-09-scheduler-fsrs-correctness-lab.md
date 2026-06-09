# Scheduler / FSRS Correctness Lab Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Make Kani scheduler decisions transparent, simulatable, and regression-proof so Bee can explain and verify why a kanji was selected, skipped, promoted, demoted, lapsed, suppressed, or scheduled at a given interval.

**Architecture:** Keep `BridgeScheduler` as the facade and Kani policy owner. Add passive trace/inspection models around the existing queue, session-selection, sibling-suppression, review-transition, and FSRS-adapter calls. Build a deterministic timeline simulator on top of the same facade so tests, parity reports, and a hidden playground all exercise the real scheduler path instead of a parallel scheduler.

**Tech Stack:** Kotlin/JVM core module, JUnit 4 golden tests, in-repo `:fsrs-java` 21-parameter FSRS engine via `LatestFsrsAdapter`, Android app/Compose for any hidden debug screen, Gradle commands from the repo root.

## Non-negotiable safety rule

FSRS computes memory state and intervals only. Kani policy owns queue admission, session priority, ladder rungs, learning/relearning steps, sibling suppression, writing-level changes, duplicate-token handling, and all user/developer wording. Do not move Kani policy into `:fsrs-java`, `LatestFsrsAdapter`, or FSRS model classes.

## Repo facts found during inspection

Repo/worktree inspected: `/Users/autumnskerritt/kanji_anki_worktrees/cheap-ralph-longterm-queue-2026-06-09`.

| Area | Existing file/class | Relevant behavior found |
| --- | --- | --- |
| Scheduler facade | `core/src/main/kotlin/dev/bee/kanjianki/core/BridgeScheduler.kt` / `BridgeScheduler` | Public compatibility facade. Owns `seedQueue`, `nextSession`, `applyReview`, `activeQueueItems`, `focusQueueItems`, `randomizedSessionTaskKeys`, `nextSessionForTaskKeys`, `applySuppression`, and nested `ReviewApplication`. Constructs `StudyQueueSeeder`, `StudySessionSelector`, `TargetedStudySessionPolicy`, `ReviewTransitionEngine`, and `SiblingSuppressionPolicy`. Default constructor routes through `LatestFsrsAdapter`. |
| Session selection | `core/src/main/kotlin/dev/bee/kanjianki/core/StudySessionSelector.kt` / `StudySessionSelector` | `nextSession()` builds row lookup, filters family queue items, compares due candidates by `duePriority`, due time, new-card sort, row weakness, and kanji. `duePriority()` currently orders write/relearning and practiced learning before reviews, then unseen new cards. Same-family candidate selection happens in `familyQueueItems()` / `compareFamilyActivity()`. |
| Review transitions | `core/src/main/kotlin/dev/bee/kanjianki/core/ReviewTransitionEngine.kt` / `ReviewTransitionEngine` | Handles duplicate tokens, learning/relearning step transitions, FSRS graduation, review `Again` lapses/relearning, review pass FSRS intervals, ladder promotion/demotion, writing-level changes, and updated task memory. `promotesByFsrsInterval()` requires FSRS interval `>` configured promotion days (`21` days by default). |
| FSRS boundary | `core/src/main/kotlin/dev/bee/kanjianki/core/KaniFsrsAdapter.kt`, `LatestFsrsAdapter.kt`, `KaniFsrsReviewResult.kt` | Adapter API exposes `initialReview()` and `review()` returning stability, difficulty, and interval millis. `LatestFsrsAdapter` calls `dev.bee.fsrs.FsrsEngine.latestDefault()` and clamps inputs locally. |
| FSRS engine | `fsrs-java/src/main/kotlin/dev/bee/fsrs/*` | In-repo 21-param engine with `FsrsEngine`, `DefaultFsrsEngine`, `FsrsParameters`, `FsrsReviewInput`, `FsrsReviewOutput`, and reference tests. |
| Queue seeding | `core/src/main/kotlin/dev/bee/kanjianki/core/StudyQueueSeeder.kt` / `StudyQueueSeeder` | Seeds/reopens/retire-aligns items, applies daily new and active caps, assigns starting rung, answer signature, initial phase/state, and sorted admission rows. |
| Sibling suppression | `core/src/main/kotlin/dev/bee/kanjianki/core/SiblingSuppressionPolicy.kt` / `SiblingSuppressionPolicy` | Groups by answer-signature family. Mature higher rungs or active writing remediation can suppress lower siblings. Suppression clears when no dominator remains. |
| Ladder rules | `core/src/main/kotlin/dev/bee/kanjianki/core/StudyLadderRules.kt` and `RecordsBase.kt` / `StudyLadderSettings`, `LadderRung`, `SchedulerPhase` | Default constants include `DEFAULT_REAL_DUE_REVIEWS_TO_MOVE = 3`, `DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS = 21`, `DEFAULT_LADDER_DEMOTION_FAIL_STREAK = 3`. `SIMILAR_KANJI` is valid only when `StudyItem.hasSimilarKanji` is true. |
| Study models | `core/src/main/kotlin/dev/bee/kanjianki/core/RecordsStudyModels.kt`, `RecordsSchedulerModels.kt`, `RecordsSyncModels.kt` | `StudyItem` stores rung, phase, memory fields, task memories, active token, suppression fields, writing state, and `hasSimilarKanji`. `ReviewRequest`, `ReviewResult`, `StudySession`, scheduler settings, learning steps, and scheduler parameters live here. |
| Existing tests | `core/src/test/kotlin/dev/bee/kanjianki/core/BridgeSchedulerTest.kt` | Covers queue seeding, token handling, next-session priority, sibling suppression, study-ahead, learning/relearning sibling order, focus queue, and many scheduler edge cases. |
| Existing ladder tests | `core/src/test/kotlin/dev/bee/kanjianki/core/LadderSchedulerTest.kt` | Covers Anki-style learning/relearning, FSRS promotion after `>21` days, no promotion at `<=21`, three due-review `Again`s demoting, similar-kanji inclusion/skipping, custom thresholds, and uses a private `FixedIntervalFsrsAdapter`. |
| Existing docs | `docs/anki-manual-parity-checklist.md`, `docs/fsrs-impact-report.md`, `README.md` | Document current Anki parity boundaries: Kani uses local FSRS scheduling, does not rewrite Anki deck schedule, does not import Anki optimizer/deck-preset parameters, and treats sibling suppression/learning order explicitly. |
| Build/test setup | `settings.gradle.kts`, `core/build.gradle.kts`, `README.md` | Modules include `:fsrs-java`, `:core`, `:app`, etc. Core is Kotlin/JVM with JUnit. README smoke command is `./gradlew :core:test :app:assembleDebug`. |

## Design principles

1. **Trace by wrapping real behavior, not reimplementing it.** Existing `nextSession()` and `applyReview()` should share the same internal path as trace APIs.
2. **No release behavior change in Phase 1.** Existing public return types remain stable; trace APIs are additive and side-effect-free except for the existing review path when explicitly applying a review.
3. **Make reasons stable and machine-checkable.** Use short reason codes (`due_now`, `relearning_priority`, `same_family_hidden`, `fsrs_interval_promotes`, `similar_kanji_unavailable`) plus human text generated by formatter functions.
4. **Keep user and developer explanations separate.** User explanation should be short and non-technical. Developer explanation should include exact rung, phase, due, comparator inputs, skipped/suppressed candidates, FSRS inputs/output, and ladder movement reason.
5. **Goldens should check decisions, not incidental timestamps.** Use deterministic clocks and fixed FSRS adapters when testing policy. Normalize timestamps as offsets from scenario start where possible.
6. **No database migration for the lab.** The trace/simulator is an inspection tool. Persisting traces can be considered later only after the passive lab is accepted.

## Planned files/classes

These are implementation targets, not yet present unless noted as existing.

| Planned file | Planned classes/functions | Purpose |
| --- | --- | --- |
| `core/src/main/kotlin/dev/bee/kanjianki/core/SchedulerDecisionTrace.kt` | `SchedulerDecisionTrace`, `SchedulerTraceCandidate`, `SchedulerTraceReason`, `SchedulerTraceSuppression`, `SchedulerTraceFsrsCall`, `SchedulerTraceTransition`, `SchedulerTraceResult<T>` | Stable data model for selection and review traces. Keep it Kotlin/JVM, immutable, and serialization-friendly without adding dependencies. |
| `core/src/main/kotlin/dev/bee/kanjianki/core/SchedulerTraceFormatter.kt` | `SchedulerTraceFormatter.userExplanation(trace)`, `developerExplanation(trace)` | Generate user-safe and developer-detailed explanations from trace codes. |
| `core/src/main/kotlin/dev/bee/kanjianki/core/SchedulerTimelineSimulator.kt` | `SchedulerTimelineSimulator`, `SchedulerTimelineScenario`, `SchedulerTimelineEvent`, `SchedulerTimelineSnapshot` | Deterministic life-of-kanji simulator built on `BridgeScheduler`. Used by tests, reports, CLI/screen. |
| `core/src/test/kotlin/dev/bee/kanjianki/core/SchedulerDecisionTraceTest.kt` | Trace API unit tests | Best-first-issue tests for `nextSession` and `applyReview` traces. |
| `core/src/test/kotlin/dev/bee/kanjianki/core/SchedulerTimelineSimulatorTest.kt` | Golden timeline tests | Five required life-of-kanji scenario tests. |
| `core/src/test/resources/dev/bee/kanjianki/core/scheduler-goldens/*.txt` | Stable text snapshots | Golden outputs for simulator timelines. Use text over JSON unless a JSON dependency already exists. |
| `core/src/test/kotlin/dev/bee/kanjianki/core/SchedulerParitySnapshotTest.kt` | Anki parity report assertions | Snapshot/report checks derived from existing docs and simulator outputs. |
| `docs/scheduler-fsrs-correctness-lab-report.md` | Generated or manually refreshed report | Human-readable report tying traces/goldens to Anki/Kani boundaries. Create only in the implementation phase that owns docs updates. |
| `app/src/debug/kotlin/dev/bee/kanjianki/SchedulerLabScreen.kt` or equivalent debug-only route | Hidden Compose playground | Debug-only UI for inspecting traces from current local data. Must not exist in release source set unless fully gated. |

## Best first issue

Add `SchedulerDecisionTrace` to `BridgeScheduler.nextSession()` and `BridgeScheduler.applyReview()` behind explicit debug/test-only APIs, then write golden tests for these five scenarios:

1. New kanji enters `kanji_meaning`.
2. Review pass promotes after an FSRS interval greater than `21` days.
3. Three due-review `Again`s demote one rung.
4. `similar_kanji` is skipped when no similar-kanji content is available (`hasSimilarKanji == false`).
5. A relearning item beats a same-family review sibling.

## Phase 1 — Passive `SchedulerDecisionTrace` for selection and review

### 1.1 TDD: trace model shape first

Red tests to add in `SchedulerDecisionTraceTest`:

- `traceNextSessionForNewKanjiExplainsSelectedKanjiMeaning()`
  - Seed one new row/item.
  - Call debug/test trace API.
  - Assert selected `kanji`, `taskType == kanji_meaning`, `rung == KANJI_MEANING`, `phase == NEW_LEARNING`, `dueAtMillis == now`, and reason codes include `new_learning_unseen` and `selected_best_candidate`.
- `traceNextSessionExplainsRelearningBeatsSameFamilyReviewSibling()`
  - Build same-family relearning `KANJI_MEANING` due now plus review `FONT_MEANING` due now.
  - Assert selected item is relearning and the review sibling appears under hidden/skipped candidates with reason `same_family_lower_priority` or equivalent.
- `traceApplyReviewCapturesFsrsPromotionAfterLongInterval()`
  - Use an injectable/fixed FSRS adapter returning `22 * BridgeScheduler.DAY` for review.
  - Assert trace contains FSRS call input (`rating=good`, elapsed days, target retention, prior stability/difficulty), output interval days `22`, and ladder movement `KANJI_MEANING -> FONT_MEANING` with reason `fsrs_interval_promotes`.
- `traceApplyReviewCapturesThreeDueAgainsDemotion()`
  - Drive three real due `Again`s with deterministic due slots.
  - Assert final trace contains `review_again_lapse`, `real_again_streak_threshold`, and movement `KANJI_MEANING -> MEANING_KANJI`.
- `traceApplyReviewDuplicateTokenDoesNotCallFsrs()`
  - Existing duplicate-token behavior should return a trace with reason `duplicate_token` or `active_token_mismatch` and no FSRS call.

### 1.2 Add data model

Implement immutable model classes with only primitives, strings, enums, and lists.

Minimum fields for `SchedulerDecisionTrace`:

- `traceId` stable-ish string for logs/tests, e.g. `nextSession:<nowMillis>` or `applyReview:<token>`.
- `operation`: `next_session` or `apply_review`.
- `nowMillis`, `studyAheadMillis`, `horizonMillis` where applicable.
- `selected`: selected task summary or `null`.
- `candidates`: all considered task summaries with ordered reason codes.
- `skipped`: candidates not selectable and why (`retired`, `suppressed_by_task_type`, `outside_allowed_kanji`, `missing_row_or_family`, `beyond_horizon`, `same_family_hidden`, etc.).
- `suppressedSiblings`: suppression source/target summaries when known.
- `fsrsCalls`: zero or more calls with type (`initial_review`, `review`), raw Kani input, target retention, elapsed days, rating, and output stability/difficulty/interval.
- `transition`: for reviews, before/after state/rung/phase/due/memory plus ladder movement reason.
- `policyOwner`: fixed string or enum values making the boundary explicit (`FSRS_MEMORY_INTERVAL_ONLY`, `KANI_QUEUE_LADDER_SUPPRESSION_STEPS_WORDING`).

Use exact field names that are boring and stable. Do not include raw prompt text or private deck/query strings in trace unless a later debug screen redacts them.

### 1.3 Add debug/test-only API without changing existing callers

Preferred API shape:

```kotlin
class BridgeScheduler {
    fun debugTraceNextSession(...same args as richest nextSession overload...): SchedulerDecisionTrace
    fun debugTraceApplyReview(application: ReviewApplication): SchedulerTraceResult<RecordsSchedulerModels.ReviewResult>
}
```

Rules:

- Existing `nextSession()` delegates to a shared private `nextSessionWithTrace(...).result` or equivalent.
- Existing `applyReview()` delegates to a shared private `applyReviewWithTrace(...).result` or equivalent.
- Trace APIs must be opt-in by name (`debugTrace...`) and not wired to production UI in Phase 1.
- If the team prefers stricter Kotlin opt-in, add a small `@RequiresOptIn` annotation such as `@SchedulerDebugApi`, but do not make Java tests painful.

### 1.4 Instrument `StudySessionSelector` without duplicating priority logic

Refactor carefully:

- Extract candidate-summary creation near `dueQueueItems()` / `familyQueueItems()`.
- Keep `compareDueItems()` and `compareFamilyActivity()` as the source of ordering truth.
- Add reason derivation around existing comparator factors:
  - `due_priority_write_or_relearning`
  - `due_priority_practiced_learning`
  - `due_priority_review`
  - `due_priority_unseen_new`
  - `earliest_due_at`
  - `new_card_sort_<mode>`
  - `row_weakness_tiebreak`
  - `kanji_tiebreak`
  - `study_ahead_horizon`
- Track same-family hiding separately from persistent `suppressedByTaskType` suppression.

Acceptance for this step:

- `BridgeScheduler.nextSession()` returns identical sessions before/after in existing tests.
- Trace test can identify the selected candidate and at least one skipped/same-family candidate when relevant.

### 1.5 Instrument `ReviewTransitionEngine` and FSRS calls

Refactor `ReviewTransitionEngine.applyReview()` into a shared internal path that can optionally collect trace facts.

Capture:

- Duplicate-token path before mutating `consumedTokens`.
- Resolved parameters/settings/learning settings/ladder.
- Previous task memory and active rung memory source.
- Rating resolution, including writing failure/manual override mapping.
- Phase transition reason:
  - `learning_again_reset_first_step`
  - `learning_good_next_step`
  - `learning_graduated_fsrs_initial`
  - `review_again_lapse_relearning`
  - `review_again_lapse_empty_relearning_steps_one_day`
  - `review_pass_fsrs_interval`
- FSRS call inputs and outputs around `fsrsAdapter.initialReview()` / `fsrsAdapter.review()`.
- Real-due counting reason (`counts_real_due`, `future_due_practice_only`, `same_due_slot_already_counted`).
- Ladder movement reason (`fsrs_interval_promotes`, `again_streak_demotes`, `floor`, `ceiling`, `similar_kanji_unavailable`, `disabled_rung_skipped`).
- Writing level reason (`clean_writing_pass`, `failed_writing`, `not_write_rung`, `hinted_or_messy_no_change`).

Do not change `KaniFsrsAdapter` semantics unless necessary. If adapter-level clamped inputs are needed later, add an additive trace method or wrapper, not policy logic.

### 1.6 Formatter

Add `SchedulerTraceFormatter` with two output variants:

- User explanation examples:
  - `裂 is due now on Kanji → meaning. It was picked before new cards because it is in relearning.`
  - `裂 moved from Kanji → meaning to Font → meaning because the FSRS interval was 22 days, above your 21-day promotion threshold.`
- Developer explanation examples:
  - Include candidate table order, due priority values, due offsets, family key redacted/hash, suppression source, FSRS input/output, and before/after state.

Acceptance:

- User explanation avoids raw internals unless needed.
- Developer explanation includes enough fields to debug a wrong decision without stepping through code.

## Phase 2 — Golden timeline simulator

### 2.1 Build simulator core

Add `SchedulerTimelineSimulator` as a pure core module class:

- Inputs: rows, starting items, scheduler settings, ladder settings, learning settings, scheduler parameters, clock start, optional test FSRS adapter.
- Actions:
  - `seed()`
  - `nextSession()`
  - `answer(rating, writing fields...)`
  - `advanceBy(millis)` / `advanceTo(millis)`
  - `applySuppression()`
- Outputs:
  - Ordered events with `SchedulerDecisionTrace`.
  - Normalized snapshots containing kanji, task type, rung, phase, state, due offset, interval days, lapses, pass/again streaks, suppression, and FSRS interval.

Keep simulator as thin as possible: it should call `BridgeScheduler`, not duplicate rules.

### 2.2 Golden format

Use deterministic text snapshots unless the repo already has a JSON serializer dependency.

Example line format:

```text
T+00:00 seed admitted=裂 task=kanji_meaning rung=KANJI_MEANING phase=NEW_LEARNING due=T+00:00 reasons=[new_admitted]
T+00:00 next selected=裂 task=kanji_meaning reasons=[due_priority_unseen_new,selected_best_candidate]
T+00:00 answer rating=good phase=NEW_LEARNING->NEW_LEARNING step=0->1 due=T+00:01 fsrs=[]
```

Snapshot rules:

- Use `T+<duration>` offsets instead of absolute epoch millis.
- Sort maps/lists deterministically.
- Redact answer signatures/family keys if they contain imported card text; show a stable short hash only if needed.

### 2.3 Required golden scenarios

Create one golden test per scenario:

1. `newKanjiEntersKanjiMeaning.timeline.txt`
   - Seed one row, assert starting rung `KANJI_MEANING`, phase `NEW_LEARNING`, task `kanji_meaning`.
2. `reviewPassPromotesAfterLongFsrsInterval.timeline.txt`
   - Use fixed FSRS review interval `22` days.
   - Assert `KANJI_MEANING -> FONT_MEANING`, reason `fsrs_interval_promotes`, threshold `21`.
3. `threeDueReviewAgainsDemote.timeline.txt`
   - Use real due slots for three `Again`s.
   - Assert two hold decisions then final demotion to `MEANING_KANJI`, streak reset, lapse count increments.
4. `similarKanjiSkippedWithoutContent.timeline.txt`
   - Item starts near/around `TYPE_MEANING`, `hasSimilarKanji=false`.
   - Assert movement skips `SIMILAR_KANJI` and trace records `similar_kanji_unavailable`.
5. `relearningBeatsSameFamilyReviewSibling.timeline.txt`
   - Same-family lower-rung relearning and higher-rung review both due.
   - Assert relearning wins session selection; review sibling is shown as hidden/skipped due to Anki gather/family order.

### 2.4 Regression expectations

For every golden scenario, tests must assert both:

- The human-readable snapshot string matches.
- Critical machine fields in traces match explicit assertions, so formatting-only changes do not hide logic regressions.

## Phase 3 — Anki parity snapshots/report from docs

Use `docs/anki-manual-parity-checklist.md` and `docs/fsrs-impact-report.md` as local parity anchors.

Tasks:

1. Add `SchedulerParitySnapshotTest` that renders a compact parity matrix from simulator scenarios:
   - Learning/relearning steps: Anki-like where intentionally implemented.
   - Bury/sibling order: Kani same-family selection and persistent mature-sibling suppression boundaries.
   - FSRS: local Kani FSRS scheduling, not Anki deck-preset/optimizer parity.
   - Lapses/leeches: Kani tracks lapses but does not implement Anki leech tag/suspend unless future product work changes that.
2. Add/update `docs/scheduler-fsrs-correctness-lab-report.md` with:
   - Current version/date.
   - Source docs referenced.
   - Golden scenario summary.
   - FSRS/Kani policy boundary.
   - Known intentional differences from Anki.
3. Keep docs factual and generated-from-tests where practical. If manual report text is maintained, each statement should point to a test name or source file.

Acceptance:

- Report does not claim full Anki FSRS parity.
- Report says Kani keeps local due dates and never rewrites Anki deck schedules.
- Report separates same-session family hiding from persistent mature-sibling suppression.

## Phase 4 — Hidden playground CLI/screen

Implement only after Phase 1-3 tests are stable.

### Option A: Debug-only screen

- Add debug source-set UI, e.g. `app/src/debug/kotlin/dev/bee/kanjianki/SchedulerLabScreen.kt`.
- Gate entry with `BuildConfig.DEBUG` and a hidden gesture or debug-only settings row.
- Screen inputs:
  - Current local queue item by kanji.
  - Synthetic scenario picker for the five goldens.
  - Rating/action buttons that do not mutate real data unless explicitly marked as simulation.
- Screen outputs:
  - User explanation.
  - Developer trace dump.
  - Timeline list with before/after rung/phase/due/FSRS interval.

### Option B: CLI-like Gradle task

If a CLI is preferred over UI, add a test-runtime JavaExec task such as `:core:schedulerLabDump` that runs a small Kotlin main against `SchedulerTimelineSimulator` and prints a selected scenario. Keep it deterministic and read-only.

Acceptance:

- Hidden lab cannot accidentally apply reviews to real user data.
- Release build has no visible lab entry point.
- `./gradlew :app:assembleDebug` still succeeds.

## Commands for implementers

Run from `/Users/autumnskerritt/kanji_anki_worktrees/cheap-ralph-longterm-queue-2026-06-09`.

Narrow TDD commands:

```bash
./gradlew :core:test --tests dev.bee.kanjianki.core.SchedulerDecisionTraceTest
./gradlew :core:test --tests dev.bee.kanjianki.core.SchedulerTimelineSimulatorTest
./gradlew :core:test --tests dev.bee.kanjianki.core.SchedulerParitySnapshotTest
```

Existing regression commands:

```bash
./gradlew :core:test --tests dev.bee.kanjianki.core.BridgeSchedulerTest
./gradlew :core:test --tests dev.bee.kanjianki.core.LadderSchedulerTest
./gradlew :fsrs-java:test
```

Full local smoke:

```bash
./gradlew :fsrs-java:test :core:test :app:assembleDebug
```

Shared-machine Gradle isolation retry:

```bash
GRADLE_USER_HOME="$PWD/.gradle-task" ./gradlew :fsrs-java:test :core:test :app:assembleDebug
```

Pre-commit sanity:

```bash
git status --short
git diff --check
```

## Acceptance criteria

Phase 1 is done when:

- `BridgeScheduler.nextSession()` and all `applyReview()` overloads behave exactly as before in existing tests.
- Debug/test trace APIs expose selected task, rung, phase, due time, priority reasons, suppressed siblings/skipped reasons, FSRS inputs/output, and ladder movement reason.
- Trace formatter provides user and developer variants.
- Duplicate review tokens produce a trace and do not call FSRS.

Phase 2 is done when:

- The five required golden timeline tests exist and pass.
- Timelines are deterministic across machines/time zones because they use fixed clocks and normalized offsets.
- Golden tests cover both snapshot text and machine trace fields.

Phase 3 is done when:

- A parity snapshot/report ties scheduler behavior to existing docs without overstating Anki parity.
- The report makes the FSRS boundary explicit: FSRS memory/interval only; Kani queue/ladder/suppression/steps/UI policy.

Phase 4 is done when:

- A hidden debug-only playground can inspect/simulate scheduler decisions without mutating real study data.
- Release build has no visible playground entry point.
- Full smoke command passes.

Overall project is done when:

- `./gradlew :fsrs-java:test :core:test :app:assembleDebug` passes.
- A developer can answer “why did Kani pick/skip/move this item?” from a trace without reading scheduler code.
- A user-facing explanation can be shown without exposing private Anki card text or implementation jargon.
- FSRS remains an interval/memory engine and does not own Kani scheduler policy.

## Implementation notes and pitfalls

- `BridgeScheduler.applyReview()` mutates `consumedTokens` as part of real application. Trace-only review APIs should be explicit about whether they simulate or apply. Prefer returning `SchedulerTraceResult<ReviewResult>` for apply paths and a separate simulator method for non-mutating dry runs.
- `SiblingSuppressionPolicy` currently stamps `suppressedAtMillis` with `System.currentTimeMillis()`. Golden tests should avoid asserting absolute suppression timestamps or should isolate suppression decisions before persistence.
- `StudySessionSelector` has both session selection and focus/active queue paths. Trace Phase 1 should start with `nextSession`; Phase 2 can extend trace coverage to `activeQueueItems`/`focusQueueItems` only if a scenario needs it.
- Existing `LadderSchedulerTest` has a private `FixedIntervalFsrsAdapter`. Move or duplicate a tiny test helper if needed; do not make production FSRS fake logic.
- `LatestFsrsAdapter` clamps stability, difficulty, retention, and elapsed days. If developer traces need clamped values, add a clearly adapter-level trace field; do not let adapter decide ladder movement.
- Avoid adding heavyweight serialization dependencies just for goldens. Stable text snapshots are enough.
- Do not include private Anki browser query text, example sentence text, or raw answer signatures in debug exports unless explicitly redacted.
