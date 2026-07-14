# Auto-advance after self-graded ratings (flashcards + writing)

Date: 2026-07-14
Status: Implemented
Decisions: hardcoded behavior (no settings toggle); writing rung included.

## Problem

After flipping a flashcard and tapping **Fail**/**Pass**, the app swaps the
action bar to "Incorrect./Correct." plus a **Continue** button that must be
tapped to reach the next card (see user screenshots `me1.jpeg`/`me2.jpeg`).
That is a wasted tap for self-graded cards: the user already saw the answer
("flipside") before rating, so the result feedback tells them nothing new.

The confirmation step is only meaningful when the *app* graded the answer —
multiple-choice cards (similar kanji, etc.) and typed answers — because there
the user answered without seeing the flipside and wants to be 100% sure of the
result.

Desired flow for self-graded cards:

1. Flashcard shown
2. Flip it over (Reveal)
3. Rate it (Pass/Fail)
4. Automatically continues on rate

## Current behavior

Every rating funnels through one gate:
`MainActivityStudy.submitWithAnswerFeedback(...)`
(`app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudy.kt:479`) drives the
per-card state machine `StudyAnswerFeedbackState`
(`app/src/main/kotlin/dev/bee/kanjianki/StudyAnswerFeedbackState.kt`):
`UNANSWERED → SUBMITTING → APPLIED → CONTINUED`.

- Every renderer's action bar swaps to status + Continue as soon as
  `feedbackVisible` (phase != UNANSWERED).
- Continue is disabled until the background review write reports APPLIED
  (`markStudyAnswerApplied`, `MainActivityStudy.kt:513`).
- Only `continueAfterStudyAnswer()` (`MainActivityStudy.kt:528`) advances the
  route, via a one-shot CAS (`tryContinue`) plus a durable recovery-store
  handoff. There is no auto-advance anywhere.
- The "Fail saved / Undo" banner (`StudyUndoState`) is captured *before* the
  applied callback (`MainActivityStudyReviewFlow.kt:537` → `:566`) and already
  persists onto the next card.

## Design

Add a transient `autoContinueOnApply` flag to `StudyAnswerFeedbackState`, set
only by **self-graded** submit call sites. When the background write lands in
`markStudyAnswerApplied`, auto-invoke the existing `continueAfterStudyAnswer()`
— the same one-shot CAS path as the manual button, so all recovery, undo, and
idempotency guarantees are reused unchanged.

### Auto-advances (user rated after seeing the answer)

- Flip flashcards: `kanji_meaning` (Recognise), `font_meaning`,
  `word_reading`, `sentence_reading`, and choice-task cards that fell back to
  the plain flashcard route (too few choices).
- Fail-with-cause path: the recognition failure-cause dialog still shows
  first; auto-advance happens after the cause is picked and the submit
  applies.
- Swipe-to-rate gestures (both the compose action-bar swipe and the view-level
  card gesture).
- Writing (`write_kanji` + writing repair): Pass / Fail / "Save hard" after
  Check, plus Skip and the manual "mark correct" override. The user has seen
  the evaluation + correct kanji before all of these actions.

### Keeps Continue (app graded the answer / user had not seen the flipside)

- Multiple choice: `similar_kanji`, `meaning_kanji`, `kanji_reading`,
  `reading_kanji`.
- Typed answers: `type_meaning`, `type_reading` (grading happens at reveal;
  keeping the result screen also preserves the manual wrong-grade override).
- Process-death restore mid-answer: the flag is transient (not persisted in
  the pending-answer snapshot), so a restored APPLIED state falls back to the
  manual Continue button. Rare and safe.

### Safety properties

- **Undo survives auto-advance.** The undo banner is captured before
  `markStudyAnswerApplied` fires and already rides onto the next card, so a
  mis-tap can still be undone; `undoLastRating` releases the submission-gate
  token and re-renders the restored card exactly as today.
- **Continue CAS failure degrades gracefully.** If the durable continue
  handoff loses CAS, `rollbackContinue()` returns the phase to APPLIED and the
  normal enabled Continue button appears as fallback.
- **No scheduler/rating semantics change.** Ratings, evidence, ladder
  movement, and persistence are untouched; only post-APPLIED navigation
  changes.
- **Race-free.** Auto-continue runs in the same main-thread posted block as
  `markApplied`; `tryContinue()` is one-shot, so a simultaneous manual tap
  cannot double-advance.
- **UI flash accepted.** The feedback bar may render for the few milliseconds
  the local write takes (SUBMITTING → APPLIED). It is kept unchanged because
  it doubles as the fallback UI for slow or failed writes. If the flash proves
  annoying, suppress it in a follow-up.

## Changes

1. `app/src/main/kotlin/dev/bee/kanjianki/StudyAnswerFeedbackState.kt`
   - Add `var autoContinueOnApply: Boolean = false; private set`.
   - New param on `begin(outcome, selectedAnswer, autoContinue: Boolean = false)`
     sets it; `resetForRetry` clears it.
   - Exclude from `StudyAnswerFeedbackSnapshot`/`restore` (transient; keeps
     `StudyPendingAnswerStore` serialization untouched).
   - Update the class doc comment (lines 26–31, "until one explicit Continue
     action is accepted").
2. `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudy.kt`
   - Thread `autoContinue: Boolean = false` through `submitReview` (:463) and
     `submitWithAnswerFeedback` (:479) into `state.begin(...)`.
   - `submitSimilarWritingRepair` (:597) passes `autoContinue = true`.
   - `markStudyAnswerApplied` (:513): inside the posted block, after
     `markApplied(token)` succeeds and the pending answer is re-persisted,
     call `continueAfterStudyAnswer()` when `state.autoContinueOnApply`.
   - Choice wrappers `submitSimilarKanjiChoice` (:412) and
     `submitLoggedChoiceReview` (:424) stay default `false`.
3. Self-graded call sites pass `autoContinue = true`:
   - `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyFlashcard.kt:363`
     (`submitReviewForRoute` — covers Pass/Fail buttons, failure-cause dialog
     submit, and compose swipe `handleReviewAction`).
   - `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyFlashcardInteraction.kt:34,39`
     (fallback action-bar runnables) and `:272` (view-level swipe REVIEW).
   - `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyWritingUi.kt:98`
     (primary Pass/Fail/"Save hard"), `:103` (Skip → Good override), `:116`
     (manual override → Good).
   - Typing call sites (`MainActivityStudyFlashcardInteraction.kt:79,107`)
     stay default `false`.
4. No compose/UI rendering changes.

## Tests

- `app/src/test/kotlin/dev/bee/kanjianki/StudyAnswerFeedbackStateTest.kt`:
  flag set by `begin`, cleared by `resetForRetry`, defaults false after
  snapshot/restore.
- `app/src/test/kotlin/dev/bee/kanjianki/MainActivityStudyReviewFlowSubmitTest.kt`:
  new case — self-graded submit auto-advances exactly once when the io write
  applies; rolls back to APPLIED (manual Continue available) on continue
  failure; retryable drop resets the flag with the phase. The existing
  `correctReviewAppliesOnceButDoesNotAdvanceUntilContinue` pin stays valid for
  default-`false` paths (choice/typing).
- Update full-activity instrumented flows in
  `app/src/androidTest/kotlin/dev/bee/kanjianki/MainActivityInstrumentedTest.kt`
  that tap Continue on flashcard/writing:
  `testKanjiDetailCopyAndStudyReviewFlow` (:964),
  `testKanjiDetailTimelineShowsReviewAfterStudy` (:998), the swipe-fail flow
  (~:1600), and the writing tests (:1878, :1942, :1970) → assert auto-advance
  (next card / review stored / undo banner) instead of a Continue tap.
  Typing (:1822) and choice (:1627) tests stay unchanged as the pins for the
  kept-Continue paths.
- `MainActivityStudyFlashcardComposeTest.gradedFlashcardRequiresExplicitContinueOnDevice`
  stays: it tests the action-bar composable contract, which is unchanged.
- Sweep `app/src/test/kotlin/dev/bee/kanjianki/MainActivityStudyFlashcardGestureTest.kt`
  for "stays until Continue" assertions on plain (non-typing) cards and update.

## Verification

```sh
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk ./gradlew ciFast
```

JVM tests + androidTest compilation + lint. This is a study-UI navigation
change, not a provider/sync change, so the live AnkiDroid emulator gate is not
required. Running the updated instrumented tests on an emulator is optional
extra confidence. Release cuts automatically on push to `main`.
