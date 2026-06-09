# World-Class Similar Kanji + Writing Study Experience Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Make Kani's most distinctive study loop feel like a calm kanji tutor: users can undo mistakes, understand why visually similar kanji are confusing, write with progressively less help, and receive feedback that guides without over-penalizing.

**Architecture:** Ship the first value slice through the existing study-state seams (`StudyReviewActions.undoLastAppliedReview`, `review_log` before/after snapshots, and Compose study screens), then expand the local core policies for similar-kanji explanations and writing tutor feedback. Keep scheduler grading conservative: diagnosis and explanation improve UI guidance, while `WritingRatingMapper`, `ReviewTransitionEngine`, and ladder movement remain the source of scheduling truth unless a later PR explicitly changes that contract.

**Tech Stack:** Kotlin/JVM core modules (`:core`, `:writing-core`), Android/Kotlin app module (`:app`), Compose study UI, ML Kit digital ink integration in app, KanjiVG-derived stroke-guide data through writing-core models, SQLite local store/review log, Gradle tests via `./gradlew :writing-core:test :core:test :app:testDebugUnitTest` and focused Compose/JVM tests.

## Current repo anchors verified before writing this plan

- `app/src/main/kotlin/dev/bee/kanjianki/StudyReviewActions.kt`
  - Already has `AppliedReviewSnapshot(token, beforeReview, afterReview)`.
  - `undoLastAppliedReview(snapshot, currentItem, writer)` restores the before item and deletes the review token only when the current item still exactly matches the after-review boundary.
- `app/src/test/kotlin/dev/bee/kanjianki/StudyReviewActionsTest.kt`
  - Already covers successful undo and stale-boundary rejection.
- `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyReviewFlow.kt`
  - Calls `saveAppliedReview(request, result, now)` after non-duplicate review application, refreshes streaks, schedules reminders, and re-renders study.
- `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyWriting*.kt`
  - Study writing UI is split into prompt, pad, status, primary actions, fallback actions, tool actions, chrome, toolbar, and session/flow helpers.
- `app/src/main/kotlin/dev/bee/kanjianki/study/MlKitJapaneseWritingRecognizer.kt`
  - App-side ML Kit digital ink boundary.
- `writing-core/src/main/kotlin/dev/bee/kanjianki/core/study/WritingAnalysis.kt`
  - Models `PASS`, `CLOSE`, `WRONG`, `NO_INK`, `MODEL_UNAVAILABLE`, `NO_STROKE_DATA`, and `RECOGNITION_ERROR`, plus confidence from recognition and stroke-order evidence.
- `writing-core/src/main/kotlin/dev/bee/kanjianki/core/study/StrokeDiagnosis.kt`
  - Existing diagnosis labels include `WRONG_ORDER`, `WRONG_DIRECTION`, `MISSING_STROKE`, `ROUGH_SHAPE`, and `RECOGNIZED_BUT_MESSY`.
- `writing-core/src/main/kotlin/dev/bee/kanjianki/core/study/WritingHintPolicy.kt`
  - Existing hint levels map from stored writing level and targeted/learning context.
- `writing-core/src/main/kotlin/dev/bee/kanjianki/core/study/WritingRatingMapper.kt`
  - Existing rating cap boundary for writing outcomes; do not bypass it from UI diagnosis.
- `core/src/main/kotlin/dev/bee/kanjianki/core/SimilarKanjiChoicePlanner.kt`, `SimilarKanjiChoiceReviewPolicy.kt`, `SimilarKanjiRepairPolicy.kt`, `SimilarKanjiIndex.kt`, `SimilarChoiceCodec.kt`, and matching tests.
  - Similar-kanji work already has local data/planning/review-policy foundations.
- `app/src/main/kotlin/dev/bee/kanjianki/data/LocalStoreSimilarKanji*.kt`
  - Local similar-kanji data/cache/maintenance boundary.
- `agents.md` and `plans/android_rewrite.md`
  - Explicit contract: `similar_kanji` is conditional on local content; `similar_kanji_choice_state` and repair queues are not independent scheduler queues; writing diagnosis is tutor-only unless a later PR explicitly changes grading behavior.

## Non-goals and safety boundaries

- Do not make writing pixel-perfect or punitive. Learners need confidence; diagnostics should guide, not block progress.
- Do not let tutor diagnosis directly change scheduler rating, FSRS memory, ladder movement, or review intervals in this project.
- Do not create a separate similar-kanji scheduler queue. Keep `study_items` as the scheduler source and use similar data as local content/evidence.
- Do not require live-device/emulator validation for every slice. Use JVM/app unit tests and Android CI by default; use screenshot/instrumentation artifacts only for UI slices that need it.
- Do not hide or remove manual grading/undo paths. Users must stay in control.

## Phase plan

### Phase 1 — Undo last rating UI flow (best first issue)

User-facing behavior:

- After marking a card Pass/Fail/Hard/Easy, show a snackbar or equivalent transient action: `Marked Pass — Undo`.
- Undo is available only for the immediate previous applied review.
- Undo disappears/invalidates after the next review, after leaving the study flow, or when the current item no longer matches the saved after-review boundary.
- Stale undo rejection should be quiet and safe: no partial restore, no review-log delete, and the UI refreshes to current truth.

Implementation shape:

1. Add a small app-level state holder for pending undo, probably near study state rather than core scheduling:
   - suggested model: `StudyUndoState(pending: AppliedReviewSnapshot?, label: String, createdAtMillis: Long)` or equivalent.
   - store it on the active `MainActivityStudy`/study-flow owner, not in durable DB.
2. In `MainActivityStudyReviewFlow.saveAppliedReview`, capture `AppliedReviewSnapshot(request.token, beforeReview = activeSession.item, afterReview = result.item)` after successful persistence.
3. Add a UI action in the study scaffold/snackbar host to call a new `undoLastRating()` method.
4. `undoLastRating()` should:
   - read the pending snapshot,
   - load the current persisted item for the same kanji,
   - call `StudyReviewActions.undoLastAppliedReview`,
   - clear pending undo regardless of success,
   - reset active session/tracker state enough to render the restored item truthfully,
   - reschedule reminders/stats refresh only after successful undo.
5. Add copy/test tags in the same style as existing study copy/UI model files.

### Phase 2 — Similar-kanji explanation cards

Create a local explanation model that can power study cards and browse/detail cards:

```kotlin
data class SimilarKanjiExplanation(
    val targetKanji: String,
    val confusedWith: List<String>,
    val sharedComponents: List<String>,
    val differingComponents: List<String>,
    val meaningClues: List<String>,
    val readingClues: List<String>,
    val failedSourceWords: List<String>,
    val watchThisPart: String,
    val confidence: ExplanationConfidence,
)
```

First implementation can be conservative:

- Use local similar-kanji pairs and available dictionary/import evidence.
- If component metadata is missing, show pair/source-word explanations and a `watch this pair` visual emphasis instead of inventing components.
- Add one-tap state labels only if they do not mutate scheduler truth directly: `I know this pair now` can dismiss local explanation/help; `Still confusing` can record tutor-only evidence or a repair hint for future selection, not force a scheduler lapse.

### Phase 3 — Writing tutor feedback categories

Extend `StrokeDiagnosis`/copy/UI to cover the categories Bee wants:

- missing stroke,
- extra stroke,
- wrong stroke order,
- wrong component proportion / rough shape,
- stroke too far from guide,
- confused with visually similar kanji,
- good enough but messy.

Map these to UI feedback strings, not scheduler ratings. Example: `Nice — shape recognized. Try making the left component narrower.`

### Phase 4 — Adaptive hint progression visibility

Make hint progression visible and explainable:

- TRACE for unknown/targeted repair,
- OUTLINE after clean pass,
- MINIMAL after repeated clean pass,
- BLIND when ready,
- regress only on meaningful failure, not ML weirdness or model-unavailable states.

The current scheduler already adjusts `writingLevel` on writing pass/fail. This phase should expose why help was removed/restored and add tests around edge cases.

### Phase 5 — Study UX polish and session summary

Small, reviewable UI slices:

- answer controls always above fold,
- reveal/grade flow cannot be accidentally skipped,
- visible undo,
- fast one-handed mode affordances,
- optional haptics/audio settings only if small and non-invasive,
- `Why this prompt?` explanation using scheduler/evidence models,
- better empty/done states,
- session summary like `3 writing checks, 2 similar-kanji repairs, 1 word-reading review`.

## Task breakdown

### Task 1: Behavior-lock undo backend and identify UI insertion point

**Objective:** Ensure the existing undo seam is safe before adding UI.

**Files:**
- Modify/test: `app/src/test/kotlin/dev/bee/kanjianki/StudyReviewActionsTest.kt`
- Read: `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyReviewFlow.kt`
- Read: `app/src/main/kotlin/dev/bee/kanjianki/StudyReviewActions.kt`

**Steps:**
1. Add missing tests if needed:
   - null snapshot rejects,
   - empty token rejects,
   - wrong kanji rejects,
   - successful undo deletes exactly the consumed review token.
2. Run:
   - `./gradlew :app:testDebugUnitTest --tests dev.bee.kanjianki.StudyReviewActionsTest`
3. Commit:
   - `test(study): lock undo review boundary behavior`

### Task 2: Add pending undo UI state after review persistence

**Objective:** Capture exactly one pending undo snapshot after a non-duplicate applied review.

**Files:**
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyReviewFlow.kt`
- Possibly create/modify: `app/src/main/kotlin/dev/bee/kanjianki/StudyUndoState.kt`
- Test: `app/src/test/kotlin/dev/bee/kanjianki/MainActivityStudyReviewFlowTest.kt` or the closest existing flow/action test.

**Steps:**
1. Write a failing test proving a successful non-duplicate review stores one pending snapshot.
2. Write a failing test proving duplicate-token review does not overwrite pending undo.
3. Implement the minimal state capture.
4. Run targeted app tests.
5. Commit:
   - `feat(study): remember immediate review undo snapshot`

### Task 3: Add snackbar/action UI for Undo

**Objective:** Show `Marked Pass — Undo` (or matching copy for the applied rating) and route action clicks to the undo method.

**Files:**
- Modify: study Compose host/scaffold files discovered around `MainActivityStudy*.kt`.
- Modify/create: study copy/test-tag source if present.
- Test: focused Compose unit/instrumentation tests near `MainActivityStudyFlashcardComposeUnitTest.kt` and writing/study action tests.

**Steps:**
1. Add a model-only test for visible label/action state.
2. Add UI test asserting the action appears after review and disappears after next review.
3. Implement snackbar/action.
4. Run:
   - `./gradlew :app:testDebugUnitTest --tests '*Study*Undo*'`
   - fallback: `./gradlew :app:testDebugUnitTest`
5. Commit:
   - `feat(study): offer undo for the last rating`

### Task 4: Wire undo execution and stale rejection refresh

**Objective:** Restore the previous study item and delete the review log row when safe, otherwise clear the UI action without corrupting state.

**Files:**
- Modify: `MainActivityStudyReviewFlow.kt` or study action owner.
- Modify: local-store writer adapter that can `saveStudyItem` and `deleteReviewByToken`.
- Test: app unit tests for success and stale rejection.

**Steps:**
1. Write a test where current item matches after snapshot and undo restores the previous item.
2. Write a test where current item has advanced and undo rejects without DB mutation.
3. Implement writer adapter and UI refresh.
4. Run targeted tests and `./gradlew :app:testDebugUnitTest`.
5. Commit:
   - `feat(study): restore previous item from undo action`

### Task 5: Similar-kanji explanation MVP model

**Objective:** Add a pure core model/policy that can explain a confused pair without UI redesign.

**Files:**
- Create: `core/src/main/kotlin/dev/bee/kanjianki/core/SimilarKanjiExplanationPolicy.kt`
- Test: `core/src/test/kotlin/dev/bee/kanjianki/core/SimilarKanjiExplanationPolicyTest.kt`
- Read/extend: `SimilarKanjiChoicePlanner.kt`, `SimilarKanjiIndex.kt`, `SimilarKanjiRepairPolicy.kt`.

**Steps:**
1. Write tests for explanation with pair + failed source words.
2. Write tests for missing component metadata using conservative fallback copy.
3. Implement policy and models.
4. Run:
   - `./gradlew :core:test --tests dev.bee.kanjianki.core.SimilarKanjiExplanationPolicyTest`
5. Commit:
   - `feat(similar): explain confused kanji pairs locally`

### Task 6: Similar explanation card UI slice

**Objective:** Show side-by-side glyphs, source words, and `watch this part` guidance on the similar-kanji study prompt/detail surface.

**Files:**
- Modify: similar-kanji study prompt/choice Compose files discovered in `MainActivityStudy*.kt`.
- Modify: browse/detail model if exposing in kanji detail.
- Test: focused Compose/model tests.

**Steps:**
1. Add model tests for explanation rows.
2. Add UI test for side-by-side pair rendering and content descriptions.
3. Implement minimal UI card.
4. Run app tests.
5. Commit:
   - `feat(similar): show why kanji pairs are confusing`

### Task 7: Writing tutor feedback copy categories

**Objective:** Expand tutor-only writing feedback while preserving rating boundaries.

**Files:**
- Modify: `writing-core/src/main/kotlin/dev/bee/kanjianki/core/study/StrokeDiagnosis.kt`
- Modify: `writing-core/src/main/kotlin/dev/bee/kanjianki/core/study/WritingFeedbackCopy.kt`
- Test: `WritingFeedbackCopyTest.kt`, `WritingAnalysisEngineTest.kt`, `WritingRatingMapperTest.kt`

**Steps:**
1. Write tests for missing/extra/order/proportion/far/confusion/messy copy.
2. Add labels conservatively.
3. Ensure `WritingRatingMapperTest` still proves tutor diagnosis does not over-grade or over-penalize.
4. Run:
   - `./gradlew :writing-core:test`
5. Commit:
   - `feat(writing): add tutor feedback categories`

### Task 8: Adaptive hint progress UI

**Objective:** Make hint removal/restoration visible without making ML noise punitive.

**Files:**
- Modify: `WritingHintPolicy.kt` and tests only if policy gaps exist.
- Modify: writing UI prompt/status files in `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyWriting*.kt`.
- Test: `WritingHintPolicyTest.kt`, writing Compose/model tests.

**Steps:**
1. Add tests for clean pass moving toward less help and meaningful failure restoring help.
2. Add UI copy like `Help removed: outline only` / `Help restored: trace guide`.
3. Keep model-unavailable/recognition-error from regressing hint level.
4. Run writing-core and app targeted tests.
5. Commit:
   - `feat(writing): show adaptive hint progress`

## Acceptance criteria

- Undo works immediately after the previous rating and rejects stale state safely.
- Undo never deletes the wrong review-log token.
- Similar-kanji explanations are local and conservative; missing data produces honest fallback copy.
- Writing feedback is tutor-only and does not bypass `WritingRatingMapper`/scheduler boundaries.
- Hint progression is visible and not punitive on ML/model failures.
- Study controls remain above the fold; no flow requires scrolling to grade.
- Targeted tests pass plus a reasonable app/core/writing smoke set.

## Suggested Cheap Ralph slicing

1. PR 1: undo backend/UI state and snackbar action.
2. PR 2: undo execution/stale refresh tests.
3. PR 3: similar-kanji explanation policy/model.
4. PR 4: similar explanation UI card.
5. PR 5: tutor feedback categories.
6. PR 6: adaptive hint progress visibility.
7. PR 7: session summary/one-handed polish if the previous PRs stay small and green.

Each PR should be small, pushed early, and not tick the README queue item until the whole selected objective is proven complete with PR/CI evidence.
