# The Kani Study Ladder And SRS System

Status: reference document generated from a deep code review of `main`
(July 2026, DB version 25). Every statement below was verified against the
source files cited. This document supersedes the older design sketch in
`docs/srs.md`, which describes a 3-rung ladder plus a
sibling-suppression design that was never implemented in that form.

Section 14 lists the gaps found during the review, the fixes applied in
the follow-up change set, and the remaining open design decisions.

---

## 1. System Overview

The scheduler is a single ladder state machine layered on top of an FSRS-6
spaced-repetition engine. Every persisted study item has exactly one current
**rung** (which study skill is being drilled) and one **phase** (where the
card is in Anki-style learning/review/relearning semantics).

Module layout:

| Module | Role | Key files |
| --- | --- | --- |
| `fsrs-java` | Pure FSRS-6 memory math (stability, difficulty, retrievability, intervals) | `DefaultFsrsEngine.kt`, `FsrsParameters.kt` |
| `core` | Scheduler: queue seeding, session selection, review transitions, ladder rules, settings models | `BridgeScheduler.kt`, `ReviewTransitionEngine.kt`, `StudyQueueSeeder.kt`, `StudySessionSelector.kt`, `StudyLadderRules.kt`, `RecordsBase.kt` |
| `domain` | Rating wire names | `StudyRatings.kt` |
| `writing-core` | Handwriting analysis and rating mapping for the writing rung | `WritingAnalysisEngine.kt`, `WritingRatingMapper.kt` |
| `app` | Android UI, SQLite persistence, sync, settings screens | `MainActivityStudy*.kt`, `data/LocalStore*.kt` |

Data flow for one review:

```
AnkiDroid sync -> DashboardRows -> StudyQueueSeeder.seedQueue -> study_items
study_items -> StudySessionSelector -> StudySession(token, taskType)
UI renders rung -> user answers -> rating wire string
ReviewTransitionEngine.applyReview -> FSRS -> new StudyItem
LocalStore.saveStudyItem + review_log insert (token = idempotency key)
```

`BridgeScheduler` (`core/.../BridgeScheduler.kt`) is a stateless public
facade over four package-local collaborators: `StudyQueueSeeder`,
`StudySessionSelector`, `TargetedStudySessionPolicy`, and
`ReviewTransitionEngine`. The app constructs a fresh `BridgeScheduler()`
per call site; all state lives in the passed-in items and settings.

---

## 2. Core Data Model

### 2.1 StudyItem

`core/.../RecordsStudyModels.kt:157`. One row per `(kanji, answer_signature)`
— that pair is the SQLite primary key (`app/.../data/LocalStoreBase.kt:565`)
and also the in-memory "family key" (`StudyQueueSeeder.familyKey`,
`StudyQueueSeeder.kt:497`). Fields relevant to scheduling:

- Item-level FSRS mirror: `state` (`new`/`learning`/`review`/`retired`),
  `dueAtMillis`, `stability`, `difficulty`, `totalReviews`, `lapses`,
  `learningStep`.
- Ladder state: `rung` (`LadderRung`), `phase` (`SchedulerPhase`),
  `realPassStreak`, `realAgainStreak`, `lastRealReviewDueAtMillis`.
  `realPassStreak` counts consecutive real-due passes on the current rung and
  gates promotion (`>= ladderPromotionMinPasses`, Goal 63; it also feeds UI
  progress display and repair-evidence reporting). `realAgainStreak` counts
  consecutive real-due fails and drives demotion; at the floor it keeps
  accumulating (see §7) so `LadderHealthPolicy` can report chronic failers.
- Writing: `writingLevel` (0–3), `writingRemediationPending` (legacy flag,
  kept in sync with `rung == WRITE_KANJI`).
- Seven per-rung `TaskMemory` slots (see 2.2).
- `hasSimilarKanji`: **derived, never persisted.** Recomputed at read time
  from the `similar_kanji_pairs` table by `kanjiWithSimilarNeighbors`, which
  (Goal 69) counts a kanji only when it participates in a pair whose **both**
  endpoints are present in the local `kanji_inventory` — the same source-set
  rule the choice planner uses (`SimilarKanjiChoicePlanner.validPair`). This
  guarantees a renderable ≥2-choice card exists, so the predicate and the
  renderer cannot diverge and a plain flashcard exercise is never recorded
  into `similar_kanji_memory`. A pair whose partner is absent from the
  inventory no longer marks the kanji as having similar content.
- `answerSignature`: normalized `kanji|expression|reading|meaning` of the
  preferred source example (`StudyQueueSeeder.answerSignature`,
  `StudyQueueSeeder.kt:507-528`). Changing it materially resets the item
  (see 10.3).
- `activeToken`: current session token; used with the `review_log.token`
  UNIQUE column for duplicate protection (see 9.3).
- Legacy columns (`recognitionStage`, `consecutiveFailedRecognitionDays`,
  `lastFailedRecognitionDayMillis`) are still written for compatibility;
  `rungToLegacyStage` (`StudyLadderRules.kt:94-102`) maps rungs back to the
  legacy stage integers.

### 2.2 TaskMemory — per-rung FSRS state

`RecordsStudyModels.kt:9-155`. Each rung owns an independent FSRS memory:

```
state, dueAtMillis, stability, difficulty, totalReviews, lapses,
learningStep, lastRating, matureIntervalDays, consecutivePasses,
lastPassedDueAtMillis
```

Encoded as a tab-separated string (`encode()`, `:82-94`) into one of seven
`study_items` text columns: `typing_meaning_memory`, `meaning_kanji_memory`,
`kanji_meaning_memory`, `font_meaning_memory`, `word_reading_memory`,
`writing_remediation_memory`, `similar_kanji_memory`. Rung-to-slot routing:
`memoryForRung` (`RecordsStudyModels.kt:371-382`). Initial memory is
`("new", 0, stability 0.4, difficulty 5.0)` (`TaskMemory.initial()`, `:97`).

Note the legacy wire aliases: the `write_kanji` rung stores into
`writing_remediation_memory` (task alias `writing_remediation`) and
`type_meaning` stores into `typing_meaning_memory` (alias `typing_meaning`)
(`StudyTaskTypes.kt:16-18`, `memoryForTaskType` `:356-369`).

### 2.3 Phases

`RecordsBase.SchedulerPhase` (`RecordsBase.kt:340-362`):

- `new_learning` — the card has never graduated; stepping through the
  configured new-learning steps.
- `review` — FSRS owns the schedule; only reviews in this phase, answered at
  or after their due time, move the ladder.
- `relearning` — post-lapse steps after a due-review `Again`.

Unknown wire names decode to `NEW_LEARNING` with a warning (`:349-360`).

### 2.4 Rungs

`RecordsBase.LadderRung`, wire names:
`write_kanji`, `type_meaning`, `similar_kanji`, `meaning_kanji`,
`kanji_meaning`, `font_meaning`, `word_reading`, `kanji_reading`,
`reading_kanji`, `sentence_reading`. Enum order is
storage compatibility only (new constants are appended at the end); the
*user-editable ladder order* is what defines the scaffolding gradient
(most-scaffolded bottom → least-scaffolded top). Unknown wire names decode to
`KANJI_MEANING` with a warning.

---

## 3. Ladder Configuration (`StudyLadderSettings`)

`RecordsBase.kt`. Holds two lists: `orderedRungs` (most-scaffolded bottom →
least-scaffolded top) and `enabledRungs`. The order is a **scaffolding
gradient, not a difficulty gradient** (P9): the bottom rung (`write_kanji`) is
the most *supported* — guided handwriting with hints and stroke guides — and
also the most *demanding* skill, while the top rung (`word_reading`) offers
the least support. Demotion adds scaffolding; promotion removes it. See §13
for P9/P10.

Default order (`defaultsOrder()`), most-scaffolded (bottom) to
least-scaffolded (top):

1. `write_kanji`
2. `type_meaning`
3. `meaning_kanji`
4. `reading_kanji`
5. `similar_kanji`
6. `kanji_meaning`
7. `font_meaning`
8. `kanji_reading`
9. `word_reading`
10. `sentence_reading`

`similar_kanji` sits directly below `kanji_meaning`, the new-card start rung
(Goal 65), so the first demotion reaches discrimination practice in one
demotion step for cards with confusion data (or skips over it for cards
without). `reading_kanji` (Goal 79), the phonetic sibling, sits directly below
`similar_kanji` — not between it and `kanji_meaning` — so the Goal 65 invariant
holds. `kanji_reading` (Goal 78) sits directly
below `word_reading`, so a `word_reading` fail streak demotes straight into
targeted reading discrimination (or skips over it to `font_meaning` for cards
without reading data). `sentence_reading` (Goal 80) is the new ceiling: cards
with a mined sentence promote past `word_reading` into contextual reading, while
cards without sentence data keep `word_reading` as their effective ceiling
(`nextRung` returns the current rung when no higher valid rung exists). Only
fresh installs and stored configs that lack a rung are affected; an existing
stored order round-trips unchanged except that a rung postdating it is spliced
in adjacent to its default neighbors
(`storedFullOrderPreservedModuloNewRungSplice`).

**All rungs are enabled by default** (`defaultsEnabled()`), including
`meaning_kanji`, `kanji_reading`, `reading_kanji`, and `sentence_reading`.

Invariants and behaviors:

- **At least one always-available rung must stay enabled.** Every rung
  except the conditional rungs (`similar_kanji`, `kanji_reading`,
  `reading_kanji` — `StudyLadderSettings.CONDITIONAL_RUNGS`) is "always
  available"
  (`alwaysAvailable`). The constructor force-adds `KANJI_MEANING` if the
  enabled set has none; `withRungEnabled` refuses to disable the last one;
  `fromStored` falls back to full defaults on invalid stored combos.
- **Per-item availability.** `isValidForItem(rung, availability)`: a rung is
  valid if enabled and `availability.isAvailable(rung)` — always-available
  rungs unconditionally, conditional rungs per their `RungAvailability` flag
  (`hasSimilarKanji`, `hasKanjiReading`). The single `hasSimilarKanji: Boolean`
  parameter that used to thread through every movement method was generalized
  into the `RungAvailability` value object (Goal 75).
- **`effectiveRung(current, availability)`**: if the stored rung is invalid
  for the item (disabled, or a conditional rung without content), walk outward
  by distance in the ordered list, checking the lower neighbor before the
  higher one at each distance — i.e. ties prefer the more-scaffolded (lower)
  neighbor. Every read path passes items through this via
  `StudyLadderRules.alignRungToLadder`.
- **`nextRung`/`previousRung`**: scan up/down the ordered list for the next
  *valid-for-this-item* rung; return the current effective rung at the
  ceiling/floor. This is what makes promotion/demotion "cross over" a
  conditional rung without pausing when the card lacks its content; each
  crossed-over conditional rung emits a `<wire>_unavailable` trace reason.
- **New cards start at `kanji_meaning`** (`LadderRung.startingRung()`), mapped
  through `effectiveRung` if disabled. Queue-seeded items are created with
  `startingRung(RungAvailability.none())`, so a new card can never start on a
  conditional rung.
- **Storage**: two comma-joined strings under settings keys
  `study_ladder_order` / `study_ladder_enabled`
  (`app/.../data/LocalStoreStudySettings.kt:73-87`, `:353-354`).
  `fromStored` auto-enables `MEANING_KANJI` for stored orders that predate
  it (`RecordsBase.kt:211-216`); `normalizeOrder` inserts any missing rung
  adjacent to its default neighbors (`:242-282`).
- Settings UI: `MainActivitySettingsStudyLadder*.kt` (reorder + toggles),
  `MainActivitySettingsLadderThreshold*.kt` (thresholds, validated by
  `StudyLadderThresholdPolicy.saveRequest` — positive whole numbers only).

---

## 4. Rung-By-Rung Reference

Session `taskType` is always derived from the item's rung
(`StudySessionSelector.kt:36`), and routing to a UI surface is decided by
`StudySessionRoute.destination` (`core/.../StudySessionRoute.kt:12-17`):
`writingRequired → WRITING`; `similar_kanji → SIMILAR_KANJI`;
`meaning_kanji → MEANING_KANJI`; everything else → `FLASHCARD`. The
four-way switch is `MainActivityStudy.renderSession`
(`app/.../MainActivityStudy.kt:130-137`).

### 4.1 `write_kanji` — handwriting production (most-scaffolded rung / demotion floor)

- **Memory slot**: `writing_remediation_memory`.
- **UI**: `MainActivityStudyWritingSession.kt` builds a `DrawingPadView`
  with stroke guides, hint progression, and a reference answer panel.
- **Evaluation**: ML Kit digital-ink recognition
  (`app/.../study/MlKitJapaneseWritingRecognizer.kt`) feeds
  `WritingAnalysisEngine.analyze` (`writing-core/.../WritingAnalysisEngine.kt:78-154`),
  which produces a status: `NO_INK`/`WRONG`/`NO_STROKE_DATA` → fail;
  `CLOSE` → "messy pass"; `PASS` → clean pass. `writingClean` is true only
  for a full `PASS` (`app/.../StudyReviewWritingOutcome.kt:10-20`).
- **Rating surface**: Pass/Fail only. The submit button's rating comes from
  `WritingFeedbackCopy.submitRating` (`writing-core/.../WritingFeedbackCopy.kt:305-313`):
  fail → `again`, `CLOSE` → `hard` ("Save hard"), else `good`. Hard/Easy
  are never user-selectable. A "Mark right anyway" manual override exists;
  the engine remaps any manual override on this rung to `hard`
  (`ReviewTransitionEngine.resolveRating`, `:513-516`).
- **Engine special-casing**: a required-but-failed writing forces `again`
  (`:517-519`); `StudyReviewRequestPolicy.applyRequestedRating`
  (`core/.../StudyReviewRequestPolicy.kt:37-51`) additionally caps a passing
  rating at `WritingRatingMapper.maxAllowedRating` (confidence < 0.72 or
  hints used → at most `hard`).
- **`writingLevel`** (0–3) rises on clean, hint-free passes and falls on
  fails (`ReviewTransitionEngine.updateWritingLevel`); it seeds
  the initial hint level for future writing sessions
  (`WritingHintPolicy.initialHintState`) **and gates leaving the rung**
  (Goal 67): promotion off `write_kanji` additionally requires
  `writingLevel >= 2`, so a chain of messy `CLOSE`/"Save hard" passes that
  meets the interval and min-pass gates still cannot promote production out
  of the writing rung without at least one clean, hint-free write. The
  blocked non-move records `promotion_blocked_writing_level`.
  `updateWritingLevel` runs before the ladder transition so the gate sees
  the current attempt's effect (behavior-neutral for non-writing rungs).
- **Ladder role**: demotion floor. `previousRung` at index 0 returns the
  same rung; further `Again`s keep the card here. The fail streak **keeps
  accumulating** at the floor (only an actual rung move resets it), so
  chronically-failing floor cards keep reporting to `LadderHealthPolicy`
  (`demotionReady`/`stuck`) instead of silently resetting to zero.
- **Priority**: any item on this rung sorts into the top due-priority
  bucket (`duePriority`, `StudySessionSelector.kt:461-469`).

### 4.2 `similar_kanji` — visual-discrimination multiple choice (conditional rung)

- **Memory slot**: `similar_kanji_memory`.
- **Availability**: exists per-card only while `hasSimilarKanji` is true,
  i.e. the kanji participates in a `similar_kanji_pairs` row whose both
  endpoints are in the local `kanji_inventory` (Goal 69 — a renderable
  ≥2-choice card provably exists; pairs come from index pairs limited to
  local inventory plus confusion pairs mined from wrong picks in
  `similar_kanji_review_log`; `LocalStoreSimilarKanjiMaintenance.kt`). When
  false, promotion and demotion skip this rung without pausing.
- **UI**: `MainActivityStudyChoiceSessions.prepareSimilarKanjiRender`
  (invoked from `renderSimilarKanjiSession`) — a 2-column glyph grid of
  visually similar kanji built by `SimilarKanjiChoicePlanner`, with an
  "Explore the differences" explanation screen. Store/dictionary reads run
  on the background executor; only the returned render thunk touches the
  UI. If fewer than 2 choices can be produced, it falls back to the
  flashcard renderer while keeping the `similar_kanji` task type; with the
  Goal 69 buildability predicate this fallback should be unreachable in
  practice and `SimilarKanjiChoicePlanner.choiceCardForSession` logs a
  warning if it fires. A correct
  tap submits immediately; a wrong tap freezes the grid with red (pressed)
  / green (correct) feedback and waits for an explicit Continue tap before
  submitting (`SimilarChoiceSessionState`,
  `MainActivityStudyChoiceCompose.kt`).
- **Rating surface**: choice correctness → `good`/`again`
  (`MainActivityStudyReviewFlow.submitSimilarKanjiChoice`, `:81-95`). Wrong
  picks are logged to `similar_kanji_review_log` and can enqueue a writing
  repair, but repairs bypass the scheduler entirely.

### 4.3 `type_meaning` — typed recall

- **Memory slot**: `typing_meaning_memory` (legacy alias `typing_meaning`).
- **UI**: flashcard with a pre-reveal typed answer box
  (`MainActivityStudyTypingAnswerCompose.kt`); submit reveals the answer.
  If `TypingAnswerMatcher` matches, the app auto-submits `good` without
  showing buttons (`MainActivityStudyFlashcardInteraction.kt:30-43`);
  otherwise the user picks Again/Good.
- **Legacy mapping**: `recognition_stage = -1`.

### 4.4 `meaning_kanji` — reverse multiple choice (meaning → kanji)

- **Memory slot**: `meaning_kanji_memory` (added in DB v19).
- **UI**: `renderMeaningKanjiSession` (`MainActivityStudyChoiceSessions.kt:57-102`)
  — "Which kanji means …?" with four kanji choices built from the local
  inventory (`MeaningKanjiChoicePlanner`), biased by past wrong-pick
  counts. Falls back to flashcard if four choices cannot be built.
- **Rating surface**: selection + Pass/Fail result bar → `good`/`again`.
- **No legacy source**: unreachable via the v16 migration; reached only by
  ladder movement.

### 4.5 `kanji_meaning` — standard recognition (new-card start)

- **Memory slot**: `kanji_meaning_memory`.
- **UI**: standard flashcard; kanji glyph at 116sp, default typeface;
  reveal, then Again/Good (`MainActivityStudyFlashcard.kt:56-96`).
- **Ladder role**: default starting rung; also the guaranteed fallback rung
  the settings model force-enables.
- **Legacy mapping**: `recognition_stage = 0`.

### 4.6 `font_meaning` — font-varied recognition

- **Memory slot**: `font_meaning_memory`.
- **UI**: same flashcard, but the glyph renders in one of three bundled
  display fonts chosen at random per render (`StudyFontVariants.kt`:
  cinecaption, DotGothic16, Reggae One; `SecureRandom` pick in
  `random()`), to break reliance on one typeface's shapes.
- **Legacy mapping**: `recognition_stage = 1`.

### 4.7 `word_reading` — contextual reading (least-scaffolded rung / promotion ceiling)

- **Memory slot**: `word_reading_memory`.
- **UI**: flashcard whose hero is the whole source word (44sp) with the
  question "What is the reading?" (`StudyTextCopy.wordPrompt`,
  `StudyExampleSelector.wordReadingExample` prefers the suspended/missed
  source word).
- **Ladder role**: promotion ceiling; further passes keep the card here.
- **Legacy mapping**: `recognition_stage = 2`.
- **The meaning→reading seam.** Rungs 1–6 all test *meaning* knowledge in
  some form; `word_reading` switches the tested dimension to *pronunciation*.
  This is deliberate: `word_reading` is the **contextual exit check** — the
  card demonstrates it can be read in a real word before it leaves the
  ladder's active practice. A reading lapse demotes back into
  `font_meaning` (more *meaning* practice), which does not directly
  remediate a reading miss; that is accepted because Kani remediates
  recognition, not readings, and true retirement is Anki-evidence-driven
  (P1: retirement fires on Anki-side `matureSupportCount`, not the ladder
  ceiling). Goal 63's min-pass gate guarantees `word_reading` is validated
  more than once before it is treated as passed. A reading-focused rung or
  failure-dimension tracking is an explicit non-goal (see §14) unless the
  product direction changes.

### 4.8 Rating boundary summary

The UI never exposes Anki's four buttons. All surfaces submit wire strings
to the core:

| Surface | Possible wire ratings |
| --- | --- |
| Flashcard rungs (4.3, 4.5–4.7) | `good` (Good/swipe-right/typed match), `again` (Again/swipe-left) |
| Choice rungs (4.2, 4.4) | `good` (correct), `again` (wrong) |
| `write_kanji` | `again` (fail), `hard` (messy pass "Save hard", or manual override), `good` (pass) |

`easy` is defined in the core (`StudyRatings.kt`) and fully handled by the
engine, but is unreachable from the current UI (open decision D1).

---

## 5. The FSRS Engine (`fsrs-java`)

`DefaultFsrsEngine` implements FSRS with the 21-parameter model
(FSRS-6-style, including the trainable decay in `values[20]`;
`FsrsParameters.kt:60-70` holds the default weights).

Key formulas (`DefaultFsrsEngine.kt`):

- **Retrievability**: `R = (1 + factor * t / S)^decay`, with
  `decay = -w20`, `factor = 0.9^(1/decay) - 1`. This normalization makes
  the **interval equal the stability when desired retention is 0.90**:
  `nextIntervalDays = (S / factor) * (retention^(1/decay) - 1)`, rounded,
  clamped to `[1, 36_500]`.
- **Initial state** from the first rating: `S0 = w[rating-1]`,
  `D0 = w4 - e^(w5 * (rating-1)) + 1`, clamped to `[1, 10]`.
- **Difficulty update**: linear-damped delta plus mean reversion toward the
  Easy initial difficulty.
- **Stability update**: recall branch (with hard penalty `w15` and easy
  bonus `w16`), forget branch (capped by the short-term formula), and a
  **same-day branch**: `elapsedDays == 0` routes to `shortTermStability`
  (`nextState`, `:22-44`).

`LatestFsrsAdapter` (`core/.../LatestFsrsAdapter.kt`) wraps the engine for
the scheduler with clamped inputs (stability ≥ 0.001, difficulty ∈ [1,10],
retention ∈ [0.01, 0.99], elapsed ≥ 0) and two operations:

- `initialReview(rating, stability, difficulty, retention, isNewLearning)` —
  for graduation. New-learning graduation calls `engine.initialState(rating)`
  (fresh memory from the graduating rating); relearning graduation keeps
  the current stability and only advances difficulty, then derives the
  interval from that stability.
- `review(stability, difficulty, rating, elapsedDays, retention)` — the
  normal review-phase update.

Correctness is pinned by golden/parity test resources
(`core/src/test/resources/dev/bee/kanjianki/core/scheduler-goldens/`,
`scheduler-parity/`) and `fsrs-java` unit tests.

### 5.1 Scheduler parameters and retention

`RecordsSchedulerModels.SchedulerParameters` holds the desired retention
(default 0.90) plus the optional frequency-retention override; both are
stored under `scheduler_*` settings keys (`LocalStoreStudySettings.kt`).

- **Target retention** is user-facing and applied per review; an optional
  frequency-based override maps a card's `jitenRank` to a retention range
  (`targetRetentionForRank`; wired in `MainActivityStudyReviewFlow.kt`).
- The former per-rating interval multipliers and the monthly
  `SchedulerTuner` were removed (see resolved Gap G1): they were persisted
  and adjusted but never applied to any interval computation.

### 5.2 Optional personalized weights (Goals 92–93 / D-S7)

`BridgeScheduler.withWeights` is the single production construction seam.
With no stored vector it follows the exact pre-personalization default path;
with a vector it validates all 21 finite values through `FsrsParameters.of`
and constructs the same `LatestFsrsAdapter` around that engine. The setting
`scheduler_fsrs_weights` is a comma-separated, full-precision string (never
`SettingsRepository.putDouble`, which rounds to four decimals). Invalid
stored data produces one sanitized diagnostic and fails open to defaults.

Every scheduler consumer uses that seam: foreground reviews, sync queue
seeding, and ladder forecast simulation. Consequently personalized weights
affect both the ordinary retention-scaled interval and the fixed-0.90
`promotionIntervalDays` memory-strength signal used by ladder promotion.
This is D-S7: personalization changes the FSRS model consistently; it does
not create a second ladder-specific model.

Personalization is opt-in and off by default. `FsrsTrainingDataQueries`
groups `review_log` by `(kanji, answer_signature, task_type)`, drops legacy
rows without rich memory/phase data, and excludes learning/relearning
practice from prediction loss. The first `review` row's exact
`memory_before` is the sequence seed (also the documented approximation for
evidence-seeded items); elapsed whole days mirror
`ReviewContext.elapsedReviewDays`, including zero-day short-term samples.
`FsrsReplayEvaluator` replays ratings through the production FSRS-6 engine
and scores binary recall log-loss. `FsrsWeightFitter` uses bounded,
deterministic finite-difference Adam with an oldest-80% / newest-20%
time-ordered split and early stopping.

The adoption gate requires both at least 400 real review samples in the 80%
training partition (about 500 total samples) and a validation log-loss
improvement of at least 1% over the built-in defaults.
Every run stores counts, losses, reason, adoption state, and timestamp in
`scheduler_fsrs_fit_summary`; only a passing run writes live weights. A
charging + battery-not-low periodic worker runs every seven days while the
toggle is enabled, and Settings can enqueue an expedited one-shot fit or
clear the fitted vector and summary. Turning the toggle off clears the live
weights immediately. Therefore an unset key, or the default-off feature,
leaves all scheduler goldens and parity snapshots byte-identical.

---

## 6. Phase Machine (Anki Semantics)

Implemented in `ReviewTransitionEngine.applyLadderTransition` (`:186-192`)
dispatching on the item's phase.

### 6.1 Learning steps

`LearningStepSettings` (`RecordsSchedulerModels.kt:24-153`). Defaults:
new steps `[1m, 10m]`, relearning steps `[10m]`. Stored as text under
`new_learning_steps_minutes` / `review_relearning_steps_minutes`; the
relearning list may be saved empty, the new list may not (parse falls back
to defaults). Step delays are wall-clock minutes
(`StudyLadderRules.stepDelayMillis`).

### 6.2 `new_learning` and `relearning` transitions

`applyLearningTransition` (`:194-264`):

- `again` → step 0, due in `steps[0]` minutes.
- `hard` → on step 0 with ≥ 2 steps: due in
  `max(steps[0], avg(steps[0], steps[1]))` (the Anki first-step midpoint);
  on step 0 with a single step: 1.5x the step delay; on later steps it
  repeats the current step's delay.
- `good` → next step; past the last step → graduate.
- `easy` → graduate immediately.
- Empty steps (`applyEmptyLearningStepsTransition`):
  new-learning always graduates on any rating; relearning graduates on
  pass, and on `again` goes straight to `review` with a 1-day interval
  (transient edge: only reachable when the relearning list was emptied
  while a card was already mid-relearning).

**Graduation** (`graduateToReview`, `:266-281`) calls
`fsrsAdapter.initialReview(...)` and schedules the FSRS interval; the item
enters `review` phase, step 0, state `review`.

These learning/relearning repeats are practice-only: none of the code paths
above touch `realPassStreak`, `realAgainStreak`, or the rung.

### 6.3 `review` transitions

`applyReviewTransition` (`:283-291`):

- **Pass (`hard`/`good`/`easy`)** → `applyReviewPass` (`:332-358`): FSRS
  `review(...)` with the true elapsed days; new stability/difficulty; due =
  now + FSRS interval; stays in `review`.
- **`again`** → `applyReviewAgain`: increments item and task
  lapse counters; FSRS `review(AGAIN, ...)` updates memory; then:
  - relearning steps configured → phase `relearning`, step 0, due in
    `steps[0]` minutes;
  - relearning steps empty → stays `review` and is rescheduled with the
    FSRS post-lapse interval (Anki-with-FSRS behavior).

Elapsed days are reconstructed from the reviewed task memory:
`lastReviewAt = memory.dueAtMillis - memory.matureIntervalDays * DAY`;
`elapsed = (now - lastReviewAt) / DAY` (`ReviewContext.elapsedReviewDays`,
`:451-456`). Because `due = reviewTime + interval`, this recovers the true
last review time, including overdue gaps.

---

## 7. Ladder Movement Rules

All movement happens inside the two review-phase branches and is gated by
`countsAsRealDue` (`:364-370`):

```
real due  :=  item.dueAtMillis <= now
              AND lastRealReviewDueAtMillis != item.dueAtMillis
```

i.e. the answer must be at/after the persisted FSRS due time, and each due
slot may contribute ladder evidence at most once. Learning repeats,
study-ahead answers, and targeted (not-due) sessions update FSRS memory but
never move the ladder.

- **Promotion** (`applyReviewPass`): on a real-due pass,
  `realAgainStreak = 0`, `realPassStreak++`; the rung moves up only when
  **both** gates are satisfied:
  1. the retention-independent memory-strength interval schedules strictly
     more than `ladderPromotionIntervalDays` (default **21**) into the
     future (`promotesByMemoryStrength`,
     `promotionIntervalMillis > days * DAY`, where `promotionIntervalMillis`
     is computed at a fixed 0.90 retention so progression speed does not
     track the user's retention setting — closed decision D4), and
  2. `realPassStreak >= ladderPromotionMinPasses` (default **2**) — at
     least that many real-due passes have accumulated on the current rung.
  When both hold, the rung moves up via `StudyLadderRules.promoteRung` and
  both streaks reset. The min-pass gate means each rung earns at least two
  due-review credits before the ladder retires its practice, so a mature
  card no longer cascades up two rungs in two reviews after the 7-day
  promotion cap clones above-threshold stability onto the new rung. Setting
  `ladderPromotionMinPasses = 1` reproduces the pre-gate single-pass
  behavior. The gate also resolves the bounce-back idea I5: a freshly
  demoted card restarts its streak at 0 and cannot re-promote on its first
  post-demotion pass. When the rung actually changes, the newly promoted
  rung's first review is capped at `max(1, promotionDays / 3)` days (7 at
  the default 21) — `capPromotedRungFirstReview` — so the new skill is
  validated soon after unlocking. At the ceiling (`word_reading`)
  `nextRung` returns the same rung and no cap applies. When the interval
  qualifies but the min-pass gate blocks the move, the trace records
  `promotion_blocked_min_passes`. On the `write_kanji` rung a third gate
  applies: promotion additionally requires `writingLevel >= 2` (Goal 67),
  so messy `CLOSE`/"Save hard" passes cannot promote production off the
  writing rung without a clean, hint-free write; the blocked non-move
  records `promotion_blocked_writing_level`.
- **Demotion** (`applyReviewAgain`, `:320-329`): on a real-due `again`,
  `realPassStreak = 0`, `realAgainStreak++`; when the streak reaches
  `ladderDemotionFailStreak` (default **3**), the rung moves down via
  `demoteRung` and both streaks reset. The streak reset happens **only when a
  demotion actually moves the rung**. At the floor (`write_kanji`)
  `previousRung` returns the same rung, so the card stays and the streak
  keeps accumulating (it does not reset every N fails), letting chronically-
  failing floor cards keep reporting to `LadderHealthPolicy`.
- **Pass/fail semantics**: `hard`, `good`, `easy` all count as a pass for
  streaks; only `again` is a fail (AGENTS.md contract, implemented by the
  branch structure itself).
- **Settings**: `ladder_promotion_interval_days`,
  `ladder_demotion_fail_streak`, `ladder_promotion_min_passes`
  (`SyncSettings.kt`; defaults in `RecordsBase.kt`; all clamped to ≥ 1).
  The demotion key falls back to the older `real_due_reviews_to_move`
  value for pre-ladder installs; `ladder_promotion_min_passes` defaults
  to **2** and has no settings UI yet.

### 7.1 Task-memory hand-off on movement

`updatedStudyItem` (`:383-424`) writes the updated memory into the
*reviewed* rung's slot, and — if the rung changed — **clones the same
memory into the destination rung's slot** (`:418-423`). Additionally, when
a rung's memory has zero reviews but the item has history, the engine falls
back to the item-level FSRS fields (`activeTaskMemory`, `:458-473`).
Consequences:

- After a promotion, the new rung inherits the promoting review's cloned
  memory, and its first due is capped at one third of the promotion
  threshold (resolved Gap G5).
- After a demotion, the lower rung inherits the failing rung's post-lapse
  memory. With relearning steps configured the relearning due (~10 minutes)
  practices the more-scaffolded skill immediately; with an **empty**
  relearning list the lapse would otherwise reschedule from the FSRS
  post-lapse interval (days out), so `capDemotedRungFirstReview` (Goal 70)
  caps the demoted rung's first review at one day. Either way the
  more-scaffolded skill is practiced soon, no longer depending on the
  relearning-steps setting.
- Per-rung memories are therefore *seeded from* other rungs rather than
  strictly independent (deliberate continuity; see open decision D3).

### 7.2 Observability

- `LadderHealthPolicy.summarize` (`core/.../LadderHealthPolicy.kt`)
  aggregates rung distribution plus `promotionReady`
  (`matureIntervalDays > promotionDays`), `demotionRisk`
  (`realAgainStreak > 0`), `demotionReady` (`streak >= failStreak`), and
  `stuck` (`StuckCardPolicy.isStuck`: a REVIEW-phase card on its per-item
  demotion floor with `realAgainStreak >= 2 * failStreak`, Goal 68) over
  non-retired items. `stuckCount` surfaces on the ladder-health stats card
  and drives the kanji-detail "STUCK" chip.
- `debugTraceApplyReview` / `debugTraceNextSession`
  (`ReviewTransitionEngine.kt:33-51`, `StudySessionSelector.kt:42-91`)
  emit `SchedulerDecisionTrace` records with movement reason codes
  (`fsrs_interval_promotes`, `real_again_streak_threshold`,
  `similar_kanji_unavailable`, `same_family_hidden`, …).
- Every applied review writes before/after task memory and full
  scheduler-state JSON into `review_log`
  (`LocalStoreStudy.insertReview`, `:120-147`).

---

## 8. Session Selection And Queueing

### 8.1 Family collapse

`StudySessionSelector.familyQueueItems` (`:247-276`) groups visible items
by family key, aligns each to the ladder (`alignRungToLadder`), and picks
**one active item per family** via `compareFamilyActivity` (`:516-553`):
due-eligible first, then Anki-style gather order, then due-now, then the
*highest* rung rank, then earliest due. Items are visible when not
`retired` and not suppressed (`isQueueVisible`, `:293-295`), and must have
a current dashboard row/family (`hasCurrentQueueRow`, `:297-304`).

### 8.2 Due ordering and priorities

`duePriority` (`:461-469`): bucket 0 = `write_kanji` rung, `relearning`
phase, or in-progress `new_learning`; bucket 1 = `review`; bucket 2 =
unseen new cards. Within the session plan, buckets are shuffled
(seeded-deterministic when a seed is supplied;
`shuffleDuePriorityBuckets`, `:471-494`), and the app iterates the
resulting task keys (`randomizedSessionTaskKeys` →
`nextSessionForTaskKeys`; `StudySessionActions.kt:28-65`). Ties inside a
bucket break by due time, new-card sort mode, row weakness score, then
kanji.

### 8.3 Study-ahead

`study_ahead_minutes` (default 0, max 1440) extends the due horizon:
`horizon = now + clamp(studyAheadMillis)`
(`StudyLadderRules.clampStudyAheadMillis`; used at
`StudySessionSelector.kt:17` and in `dueCount`). It affects selection and
counts only — answering an ahead-of-due card never counts as real-due
evidence (section 7; the study-ahead settings copy states this — Gap G4).

Learn-ahead (PS1) is a separate, narrower horizon. When the only work left
in an active run is that run's own already-completed learning-step repeats
(`StudySessionTracker.dueCompletedLearningRepeatTaskKeys`), the session keeps
serving them up to `StudyLadderRules.LEARN_AHEAD_MILLIS` (20 minutes,
matching Anki) in the future instead of rendering the done screen
(`MainActivityStudyQueueCoordinator.pendingRepairOrDoneRender`;
`StudySessionActions.plannedStudySession` serves the repeat pass at the
widened horizon `max(studyAheadMillis, LEARN_AHEAD_MILLIS)`). The widened
horizon applies ONLY to same-session completed repeats; ordinary queue
building keeps the user's configured `study_ahead_minutes`, so a fresh
not-in-session learning card due in 5 minutes is not pulled forward. A
re-served repeat keeps its session task key, so completing it again does not
re-increment `completedCount` or the hard cap — repeats stay practice-only.
A learning step longer than the horizon legitimately leaves the session with
a pending learning card; PS2 (section 8.7) keeps the home counts at 0 until
it is due. Pinned by `StudySessionLearnAheadTest` and `StudySessionActionsTest`.

### 8.4 Queue seeding lifecycle (`StudyQueueSeeder`)

`seedQueue` (`:8-54`) reconciles existing items against current dashboard
rows and admits new ones:

- **Admission**: rows sorted by the new-card sort mode
  (`NewCardSortPlanner`; default `frequency`), admitted while
  `activeCount < activeQueueCap` (default 24) and
  `newToday < newPerDay` (default 3) (`SeedQueueState.hasAdmissionRoom`,
  `:470-473`). An `AdaptiveLoadPlan` (workload/focus mode) can replace the
  admission set and limits (`:27-54`); "more new cards" requests bypass
  daily limits explicitly (`seedExtraNewCards`, `:84-118`).
- **New items** start `state=new`, `phase=new_learning`, due immediately,
  rung `startingRung(false)` (`newStudyItem`, `:319-359`).
- **Retirement** (`shouldRetireSeedItem`, `:236-251`): an item retires when
  its source row disappears, or when the row's `matureSupportCount`
  (Anki-side mature cards for that kanji) reaches
  `matureSupportThreshold` (default 2) and the item has been reviewed at
  least once and there is no regressing repair evidence. Retired items are
  kept (state `retired`) and can reopen as fresh items if support drops or
  regression evidence appears (`canReopenRetiredSeedItem`, `:279-291`).
- **Answer-signature reset** (`alignAnswerSignature`, `:361-405`): if the
  source note's expression/reading/meaning changed materially, the item is
  fully reset (FSRS fields, all per-rung memories, streaks, phase
  `new_learning`) and demoted one rung.
- After seeding, the app re-annotates `hasSimilarKanji`
  (`HomeStudyQueueActions.kt`).

### 8.5 Sibling suppression (removed)

An earlier `SiblingSuppressionPolicy` was designed to suppress lower-context
siblings (`word_reading` dominating `font_meaning`/`kanji_meaning`, etc.)
when a dominating sibling was mature. Because the family key equals the
study-item primary key, a family can never contain a second item, so a
dominator could never exist; the layer was removed (resolved Gap G3). The
`suppressed_by_task_type`/`suppressed_at` columns remain for wire/storage
compatibility but are fully inert; DB v25 clears any stale flags left by
older builds. Same-session family hiding in `StudySessionSelector`
(section 8.1) is the only sibling-suppression behavior.

### 8.6 Targeted study

`TargetedStudySessionPolicy` (`core/.../TargetedStudySessionPolicy.kt`)
builds a session for a specific kanji regardless of due state (used by
kanji detail screens), reusing the seeded item when present or creating an
ephemeral new item at the starting rung.

### 8.7 Post-session home counts

The home "to study" count (adaptive plan `remaining`), the Study nav badge
fallback, and the focus metric all derive from
`AdaptiveLoadCandidate.isRecoveryDue`. A mid-`learning` card is recovery-due
only once its step delay has elapsed (`item.dueAtMillis <= now`), not
unconditionally. This means that immediately after finishing a session, the
cards just answered `Again` sit in `learning` due 1–10 minutes out and the
home counts read **0 until those step delays pass**, then reappear as the
steps come due. The today plan's "N due now"
(`DailyStudyPlanPolicy.dueNow`) has always been due-time-gated and agrees.
The learning clause deliberately omits the `totalReviews > 0` guard the
reviewed-card clause uses: a card abandoned mid-learning with no persisted
review yet still counts once it is past due. `FocusedStudyPlanPolicy.itemDueForFocus`
applies the same rule. Pinned by
`AdaptiveLoadRecoveryDueTest`, `AdaptiveLoadPlannerTest`, and
`MainActivityHomePostSessionCountsTest`.

The combined end-to-end promise — finish a session (learn-ahead serves the
session's own repeats to graduation), then the home reads 0 until the
graduated cards' FSRS due times arrive, at which point the counts reappear —
is pinned by `PostSessionZeroStateRegressionTest`, which drives a full
session against a real `LocalStore` + `BridgeScheduler` + `StudySessionTracker`.

---

## 9. Review Application Pipeline

### 9.1 UI → request

All rungs funnel into `MainActivityStudy.submitReview(rating, override)`
(`MainActivityStudy.kt:337-343` →
`MainActivityStudyReviewFlow.kt:18-36`). `StudyReviewRequestPolicy.from`
combines the session, the writing outcome, and `hintsUsed` into a
`ReviewRequest` (kanji, token, rating, writingRequired/Passed/Clean,
manualOverride, hintsUsed, taskType, answerSignature, prompt).

### 9.2 Engine call

`submitNormalReview` (`MainActivityStudyReviewFlow.kt:97-132`) calls the
full `applyReview` overload with: consumed tokens from `review_log`,
`SchedulerParameters` (with per-rank retention override), sync `Settings`
(ladder thresholds, matureDays, …), `LearningStepSettings`, and
`StudyLadderSettings`. `ReviewTransitionEngine.applyReview` (`:8-31`) then:

1. Resolves defaults for any null configuration.
2. Duplicate check (see 9.3); on success consumes the token.
3. Builds `ReviewContext` (effective rung, task memory with item-level
   fallback, elapsed days, resolved rating — including the writing
   force-`again` and manual-override-`hard` rules).
4. Applies the phase transition (section 6) and ladder movement
   (section 7).
5. `updateWritingLevel`, then assembles the updated `StudyItem`
   (`updatedStudyItem`) with `activeToken` cleared and legacy fields kept
   in sync.

### 9.3 Idempotency

Two layers (`duplicateReviewResult`, `:53-65`):

- token already in the consumed set → duplicate;
- item has a non-empty `activeToken` that differs from the request →
  duplicate ("does not match the active session").

The durable consumed-token set is exactly `review_log.token`
(UNIQUE, `CONFLICT_IGNORE` on insert; the submit path checks the single
request token via the indexed `hasConsumedToken()`,
`LocalStoreStudyStatus.kt`). Tokens are minted per session by
`StudyTokenPolicy` (`"$kanji-" + UUID`), persisted onto the item when a
session is activated, and cleared by the engine on application.

### 9.4 Persistence and undo

On a non-duplicate result (`MainActivityStudyReviewFlow.kt`,
`StudyReviewActions.kt:8-23`): save the item, insert the `review_log` row
(request fields + before/after memory + before/after scheduler JSON),
record the task timing row (`study_task_log`), capture an undo snapshot,
maybe tune scheduler parameters, reschedule reminders, re-render. The whole
submit pipeline runs on the background io executor (the Pass/Fail click
handler only captures tap-time state and queues the write); the toast posts
back to main. Undo
deletes the log row by token, deletes the timeline event, and restores the
before-item (`LocalStoreStudy.undoLastAppliedReview`, `:93-106`).

---

## 10. Persistence Summary

DB `kanji_anki_simple.db`, version **25** (`LocalStoreSchema.kt:6-7`).

| Table | Role |
| --- | --- |
| `study_items` | Scheduler source of truth: PK `(kanji, answer_signature)`, item FSRS mirror, rung/phase/streaks, 7 task-memory text columns, `active_token` |
| `review_log` | Durable review evidence, consumed-token set, before/after snapshots |
| `learning_repeats` | Practice-repeat ordering data (not a scheduler queue) |
| `study_task_log` | Per-answer timing/outcome |
| `similar_kanji_pairs` | Data source for `hasSimilarKanji` (index pairs + mined confusion pairs; regenerates from the review log on sync) |
| `similar_kanji_choice_state` / `similar_kanji_repair_queue` / `similar_kanji_review_log` | Choice-card content state, writing repairs, confusion mining (not scheduler queues) |
| `settings` | Key–value store for every knob in section 11 |

**DB v16** (`StudySchedulerMigration.kt`) is the ladder fresh start: it
rebuilt `study_items`, mapped legacy fields to rungs in SQL
(`writing_remediation_pending=1 → write_kanji`; `recognition_stage
-1/0/1/2 → type_meaning/kanji_meaning/font_meaning/word_reading`), mapped
`state='review' → phase review` (else `new_learning`), reset streaks,
`meaning_kanji_memory`, and `similar_kanji_memory`, and wiped
`learning_repeats` plus the similar-kanji practice queues. The same rung
mapping exists in-memory for legacy constructor paths
(`RecordsStudyModels.derivedRung/derivedPhase` — note the
in-memory phase derivation additionally infers `relearning`, unlike the
one-shot SQL; a code comment in `StudySchedulerMigration.kt` records that
this divergence is deliberate).

**DB v25** clears any stale `suppressed_by_task_type`/`suppressed_at`
values left behind by the removed sibling-suppression layer
(`LocalStoreBase.clearStaleSuppressionFlags`).

---

## 11. Settings Reference (scheduler-relevant)

| Setting | Key | Default | Consumed by |
| --- | --- | --- | --- |
| Ladder order / enabled | `study_ladder_order`, `study_ladder_enabled` | all 7 rungs, order §3 | every scheduler entry point |
| Promotion interval | `ladder_promotion_interval_days` | 21 | `applyReviewPass` |
| Demotion fail streak | `ladder_demotion_fail_streak` | 3 (falls back to `real_due_reviews_to_move`) | `applyReviewAgain` |
| Target retention | `scheduler_target_retention` | 0.90 | FSRS interval computation |
| Frequency retention | `scheduler_frequency_retention_*` | off | per-card retention override |
| New learning steps | `new_learning_steps_minutes` | `1m, 10m` | learning transitions |
| Relearning steps | `review_relearning_steps_minutes` | `10m` (may be empty) | lapse handling |
| Study ahead | `study_ahead_minutes` | 0 (max 1440) | due horizon (settings copy notes early answers never move the ladder) |
| New per day | `new_per_day` | 3 | queue admission |
| Active queue cap | (settings arg) | 24 | queue admission |
| Mature days | (settings arg) | 21 | suppression maturity, analytics |
| Mature support threshold | (settings arg) | 2 | retirement/reopening |
| New-card sort | `new_card_sort_mode` | `frequency` | admission order |
| Adaptive load | `adaptive_load_*` | auto, 20%, 5 | `AdaptiveLoadPlanner` seeding |

---

## 12. Deterministic Test Surface

- `core/src/test/.../LadderSchedulerTest.kt`, `StudyLadderSettingsTest.kt`,
  `StudyLadderThresholdPolicyTest.kt`, `LadderHealthPolicyTest.kt` — ladder
  rules and settings.
- `scheduler-goldens/` and `scheduler-parity/` resources — FSRS regression
  pinning; `SchedulerTimelineSimulator` supports timeline-level checks.
- `app/src/androidTest/.../LadderSchedulerEndToEndTest.kt`,
  `SettingsStudyLadderComposeTest.kt`,
  `SettingsLadderThresholdComposeTest.kt` — end-to-end and settings UI.
- Live AnkiDroid provider gates cover sync, not the scheduler (AGENTS.md).

---

## 13. Design Properties Worth Preserving

1. **Single state machine.** No side queues feed the scheduler;
   `learning_repeats`, choice state, and repair queues are content/UX
   tables only.
2. **Evidence-gated movement.** Ladder movement requires persisted FSRS
   due-review evidence with per-due-slot dedupe; practice can never inflate
   progression.
3. **Idempotent reviews.** Token uniqueness is enforced in memory, on the
   item, and by the DB.
4. **Config-safe ladder.** Any stored configuration degrades to a usable
   ladder (always-available guarantee, nearest-rung mapping, unknown wire
   names default safely).
5. **Auditability.** Every review stores full before/after scheduler state;
   decision traces exist for both selection and application.
6. **P9 — The ladder is a scaffolding gradient, not a difficulty gradient.**
   Bottom = maximum support and deliberate practice (guided handwriting with
   hints and stroke guides); top = minimum support and contextual use (raw
   word reading). Demotion adds scaffolding; promotion removes it. Describing
   `write_kanji` as the low or easy end of the ladder is misleading —
   production is the most *demanding* skill; it is the most *supported* rung.
   Prefer
   scaffolding language ("most-scaffolded rung", "the more-scaffolded
   neighbor") over easier/harder throughout code, docs, and settings copy.
7. **P10 — Grading objectivity decreases as trust increases.** Bottom rungs
   grade objectively (ML-Kit ink evaluation, forced-choice correctness,
   typed-answer matching); top rungs are self-graded reveal cards
   (`kanji_meaning`, `font_meaning`, `word_reading`). The ladder hands
   grading back to the learner as the card earns trust; movement rules
   should not undermine this (e.g. the clean-write exit gate, Goal 67,
   keeps objective evidence in charge of leaving `write_kanji`).

---

## 14. Gaps, Issues, And Improvements

The July 2026 review identified the gaps below. Items marked **Fixed**
were resolved by the follow-up change set on this branch; items marked
**Open decision** are deliberate product/design calls recorded here.

### Fixed

- **G1 (High) — Interval multipliers were tuned but never applied.**
  `SchedulerParameters` carried four per-rating interval multipliers,
  `SchedulerTuner` adjusted them monthly after every review, and the app
  persisted them — but no code path ever multiplied an interval by them.
  *Fix:* deleted `SchedulerTuner`, the multiplier/adjustment fields, their
  storage keys, and the post-review tuning hook. `SchedulerParameters` now
  models exactly what the scheduler consumes: target retention plus the
  frequency-retention override. FSRS is the single interval authority.
- **G2 (Medium) — `meaning_kanji` default contradicted AGENTS.md.** Code
  enables all rungs by default and auto-enables `meaning_kanji` for
  stored configs that predate it; AGENTS.md claimed it was off by default.
  *Fix:* corrected AGENTS.md (code behavior was intentional).
- **G3 (Medium) — Sibling suppression was structurally inert.**
  `SiblingSuppressionPolicy` looked for a dominating sibling inside a
  family, but the family key equals the `study_items` primary key, so a
  second family member can never exist. The dead layer still gated queue
  visibility, reminders, and the home "Buried" count through stale flags.
  *Fix:* removed the policy, the visibility gates, and the buried count;
  DB v25 clears stale `suppressed_by_task_type`/`suppressed_at` values.
  Same-session family hiding (8.1) remains the only suppression behavior.
- **G4 (Medium) — Study-ahead silently never moved the ladder.** Answers
  given before the persisted due time update FSRS but contribute no ladder
  evidence, by design (section 7). Nothing surfaced this. *Fix:* the
  study-ahead settings copy now states "Early answers never move the
  ladder." The evidence rule itself is unchanged and remains the AGENTS.md
  contract.
- **G5 (Medium) — Promotion delayed the newly unlocked skill by 21+ days.**
  The promoting review's FSRS due (> promotion threshold) was cloned onto
  the destination rung, so a freshly promoted skill was first tested three
  or more weeks later. *Fix:* `capPromotedRungFirstReview` caps the
  promoted rung's first review at `max(1, promotionDays / 3)` days (7 at
  the default 21). Cloned FSRS memory state is kept intact.
- **G7 (Low) — Learning `hard` with a single step equaled `again`.**
  *Fix:* with one configured learning step, Hard now waits 1.5x the step
  delay (Anki semantics), sitting strictly between Again and Good.
- **G8 (Low) — Empty relearning steps hardcoded a 1-day post-lapse
  interval.** Anki with FSRS reschedules a lapse from the post-lapse
  memory state. *Fix:* `applyReviewAgain` now uses the FSRS post-lapse
  interval when the relearning list is empty; AGENTS.md updated.
- **G10 (Low) — Row-free due counts disagreed with the queue.** The 2/3-arg
  `dueCount` overloads counted orphaned items that could never be selected.
  *Fix:* removed them; all due counts now go through the family-collapsed
  row-aware variant. (`HomeDeckOverviewPolicy` keeps its Anki-style
  New/Learn/Due taxonomy, which is a different, intentional bucketing.)
- **G11 (Low) — `StudyLeechPolicy` was unwired dead code.** No production
  call sites, no UI, no queue behavior. *Fix:* deleted. If leech handling
  becomes a product goal, reintroduce it wired to the study/kanji detail
  UI (tracked in `docs/anki-parity-gap-map.md`).
- **G14 (Doc) — Stale and fragmented documentation.** *Fix:*
  `docs/srs.md` now carries a superseded banner pointing here;
  AGENTS.md scheduler notes were corrected (G2, G7, G8, promotion cap);
  `StudySchedulerMigration.kt` documents why the one-shot v16 SQL phase
  mapping deliberately differs from the in-memory `derivedPhase`.

### Open decisions

- **D1 (was G6) — `easy` (and mostly `hard`) are unreachable from the UI.**
  The two-button Pass/Fail surface is an intentional product
  simplification (`docs/anki-parity-gap-map.md`); the engine keeps full
  four-rating semantics, and the writing rung's "Save hard" is the only
  `hard` producer. Exposing Hard on flashcard rungs would be cheap if the
  product ever wants finer-grained FSRS signal.
- **D2 (was G9) — New-learning graduation seeds FSRS from the graduating
  rating only. (Experiment run — Goal 74; rejected for now.)** A card that
  needed several `again`s in learning graduates with the same initial memory
  as one that passed immediately. The Goal 74 experiment
  (`GraduationHistoryExperimentTest`, write-up in
  `docs/scheduler-fsrs-correctness-lab-report.md`) confirmed the current path
  is struggle-blind (every corpus → 2-day first interval) but found the naive
  FSRS same-day history chain over-corrects (one `Again` collapses graduating
  stability to ~0.25, five to ~0.01), discarding the graduating rating and
  blurring the learning/review boundary. Deferred pending a *tempered*
  variant; production behavior unchanged, harness retained.
- **D3 (was part of G5) — Cross-rung memory cloning.** Movement clones the
  reviewed rung's memory into the destination rung (plus the item-level
  fallback for empty memories). This is deliberate scheduling continuity,
  now paired with the promotion cap; strictly independent per-rung
  evidence remains a possible future direction.
- **D4 (was G12) — Promotion speed is coupled to target retention.
  (Closed by Goal 64.)** Promotion previously fired on the *scheduled
  interval* (> threshold days), which scales with desired retention
  (0.80 ≈ 3.3x stability, 0.95 ≈ 0.4x at the default decay), so tuning
  retention silently tuned ladder progression speed. *Resolution:*
  promotion now keys off a retention-independent memory-strength interval —
  `KaniFsrsReviewResult.promotionIntervalMillis`, computed by the adapter
  at a fixed 0.90 retention (`LatestFsrsAdapter.promotionIntervalDays`) —
  compared in `promotesByMemoryStrength`. At the 0.90 default the decision
  is identical to before, so all goldens are byte-identical; at other
  retentions the promotion decision is unchanged even though due dates
  differ. Pinned by
  `LadderSchedulerTest.promotionDecisionIsIdenticalAcrossTargetRetentions`
  and `lowRetentionDoesNotPromoteWeakMemoryThatNinetyWouldReject`.
- **D5 (was G13) — Positional vararg constructors are a refactor hazard.**
  `StudyItem` accepts 5/9/13/17/18/19/25/26 trailing args with meaning
  decided by count; `Settings` and `ReviewRequest` use the same pattern.
  The builder exists — migrating remaining constructor call sites and
  freezing the shapes is a mechanical but broad refactor, deferred to keep
  this change reviewable.
- **D6 (Goal 68) — Parking chronically-stuck floor cards.** Phase 1 (landed)
  detects stuck cards — `StuckCardPolicy.isStuck`: a `REVIEW`-phase card on
  its per-item demotion floor whose `realAgainStreak` has reached twice the
  demotion threshold — surfaces the count on the ladder-health stats card
  (`stuckCount`) and a "STUCK" chip on the kanji detail screen suggesting
  mnemonic work. Phase 2 (parking) is a **deferred product decision**:
  whether to add a user-initiated "pause this kanji" that sets a new `parked`
  flag excluded from selection and admission counting (schema change, DB
  v26), or to reuse AnkiDroid-side manual suspension. It must **not** silently
  reuse `retired`, which has seeder reopen semantics
  (`StudyQueueSeeder.canReopenRetiredSeedItem`) that user intent must not
  fight. Parking implementation becomes its own follow-up goal with schema
  criteria once the direction is chosen.

### Repair-loop and stats decision log (Goals 82–95)

- **D-S1 — One stats surface.** `ProgressAnalyticsDashboardScreen` is the
  sole production Stats route. The unused `StatsScreen`/`StatsCardModel`
  stack and its second bottom navigation were deleted so cached insights and
  presentation cannot drift between parallel implementations.
- **D-S2 — Truth before decoration.** Stats values, chart geometry, and axes
  must derive from persisted evidence. Unknown data renders an empty state;
  it is never replaced by minimum chart floors, arithmetic offsets, invented
  labels, or an unrelated metric under a friendlier name.
- **D-S3 — Recent confusion means 90 days.** Confusion insights use the same
  bounded `similar_kanji_review_log` window as `ConfusionPairMiner`. No second
  unbounded history is retained, and non-kanji choice values are filtered.
- **D-S4 — Forecast ladder completion, not retirement.** The deterministic
  forecast runs the production scheduler under an explicit all-passes
  assumption until each repair reaches and validates its highest available
  rung. Anki evidence still owns retirement; the forecast never predicts it.
- **D-S5 — Restore by staged whole-file replacement.** A selected backup is
  validated fail-closed, staged privately, and swapped at the next process
  start after a safety snapshot. Row merging and in-process replacement are
  rejected because open-helper quiescence cannot be proven.
- **D-S6 — Widget and reminders share eligibility.** Widget due counts pass
  through `ReminderEligibilityPolicy`, preserving the invariant that a tap on
  advertised work cannot open an empty Study route.
- **D-S7 — One fitted FSRS model everywhere.** Optional custom weights feed
  reviews, seeding, forecasts, and the fixed-0.90 promotion-strength interval.
  This extends closed decision D4 without reintroducing retention coupling:
  the retention preference still does not control promotion, while the
  selected memory model consistently informs every interval calculation.
- **D-S8 — Provider write-back remains tag-only.** Kani may add
  `kani_archived` or opt-in `kani_repaired` note tags, but it never rewrites
  Anki queue, due, interval, ease, deck, or other scheduling state. Repaired
  cards are handed back to the user with an exact AnkiDroid browser search;
  direct unsuspend remains outside this goal unless a future provider contract
  is proven.

### Ladder-completion forecast

The Stats screen's completion forecast is a deterministic simulation of
ladder **practice**, not a prediction that a card will retire in Anki. It runs
the production `StudyQueueSeeder`, `ReviewTransitionEngine`, and FSRS adapter
forward under an explicit all-passes assumption, including queue-cap admission
waves, promotion validation intervals, conditional-rung crossings, and ceiling
parking. The displayed month is therefore the point at which every currently
eligible weak kanji is projected to have reached and validated its highest
enabled valid rung.

Retirement remains evidence-driven and separate: only later AnkiDroid sync
evidence can establish mature support and retire a repair. The forecast never
claims that ladder completion changes Anki scheduling state or proves repair
outcomes (decision D-S4 / Goal 88).

### Non-goals

- **The meaning→reading seam at `word_reading` (Goal 72).** The top rung
  switches the tested dimension from meaning to pronunciation and a reading
  lapse demotes back into meaning practice. This is intentional:
  `word_reading` is the contextual exit check, Kani remediates recognition
  (not readings), and true retirement is Anki-evidence-driven (P1). A
  reading-focused rung or per-dimension failure tracking is an explicit
  non-goal unless the product direction changes. Documented in §4.7 and the
  AGENTS.md ladder notes.

### Improvement ideas (non-defect)

- **I1 — Promotion readiness surfacing.** `LadderHealthPolicy` already
  computes promotion-ready/demotion-risk counts; surfacing per-card "next
  rung at interval > N days" in the kanji detail screen would make the
  ladder legible to users.
- **I3 — FSRS parameter optimization. (Closed by Goal 93.)** The opt-in
  on-device fitter now replays real `review`-phase histories with the
  production FSRS-6 engine, uses bounded finite-difference Adam and a
  time-ordered 80/20 split, and adopts only with at least 400 training samples
  (about 500 total samples) and a ≥1% validation log-loss improvement over
  defaults. The full-precision
  vector is threaded through reviews, sync seeding, forecasts, and the
  fixed-0.90 promotion-strength calculation; default-off behavior remains
  golden-neutral. Importing AnkiDroid-optimized weights remains a separate
  possible enhancement, not a missing plumbing path.
- **I4 — Same-day elapsed granularity.** `elapsedReviewDays` floors to
  whole days, so any same-day second review takes the FSRS short-term
  branch regardless of hour spacing; acceptable, but worth noting if
  sub-day scheduling is ever added.
- **I5 — Demotion bounce-back guard. (Resolved by Goal 63.)** Demotion
  clones the failing rung's post-lapse memory into the lower rung; for a
  formerly mature card this used to let one subsequent real-due pass bounce
  the card straight back up. The `ladderPromotionMinPasses` gate (default
  2) now requires at least that many real-due passes on the current rung
  before promotion, and a demotion resets `realPassStreak` to 0, so the
  first post-demotion pass can no longer re-promote. Pinned by
  `LadderSchedulerTest.demotedMatureCardDoesNotRepromoteOnFirstPass` and
  the `promotionRequiresSecondRealDuePass` golden.

### Reading-aware rungs decision log (Goals 75–80)

Three reading-focused conditional rungs were added on top of the Goal 75
`RungAvailability` generalization and the Goal 76 `KanjiReadingAligner`. Their
decisions of record:

- **D-R1 — Ladder movement stays in-app.** Anki-side lapse counts do NOT feed
  promotion/demotion; only persisted FSRS-due review attempts in the `review`
  phase move the ladder. Anki-side maturity/lapse data is used only inside the
  content planners (`KanjiReadingChoicePlanner`, `ReadingKanjiChoicePlanner`):
  mature attested readings qualify as distractors and lapse-heavy readings are
  preferred as the tested contrast — the same targeting-without-movement pattern
  `similar_kanji` already uses.
- **D-R2 — Canonical readings.** Usage rows and choice options are keyed by
  canonical reading (on-readings → hiragana; kun-readings canonicalized to the
  pre-`.` stem; rendaku/sokuon surface variants mapped back to their base), so
  evidence groups correctly (心配's ぱい counts as はい) and choice lists carry
  no near-duplicates.
- **D-R3 — Jukujikun exclusion is by design.** Words the aligner cannot attribute
  (明日=あした, 大人=おとな, …) produce no usage rows and therefore never gate or
  feed the new rungs. No per-word special cases.
- **D-R4 — Auto-enable on upgrade.** `meaning_kanji`, `kanji_reading`,
  `reading_kanji`, and `sentence_reading` are auto-enabled for stored configs
  that predate them (`StudyLadderSettings.AUTO_ENABLE_RUNGS`, generalizing the
  original single-`MEANING_KANJI` clause). A user's stored order is preserved
  verbatim modulo the documented `insertMissingRung` splice.
- **D-R5 — No parallel queues.** All three rungs are ordinary ladder rungs over
  `study_items`. `kanji_reading_usage` / `kanji_reading_pool` are content/data
  tables like `similar_kanji_pairs`, not scheduler queues.
