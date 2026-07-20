# Study feedback freeze — reproduction & root-cause evidence

Card: t_89db57a1 (branch `fix/study-feedback-freeze`, based on current `origin/main`).
Scope: reproduce + root-cause only. **No production fix is made in this card.**

## Reported symptom

Portrait Android study screen, progress 8/12, after grading recognition kanji
脱 (from 脱出). The screen shows "Fail saved", "Incorrect.", and a Continue
button that looks enabled but does not advance — the card is frozen. The Fail
was persisted (the review committed), yet the UI never leaves the answered card.

## Tight deterministic RED loop

JVM/Robolectric, no emulator required (the whole answer-gate + review-commit
pipeline is exercised through the existing `withReviewActivity` harness with a
controllable background executor `reviewIo`).

Two RED regression tests were added to
`app/src/test/kotlin/dev/bee/kanjianki/MainActivityStudyReviewFlowSubmitTest.kt`:

1. `appliedCallbackDroppedByFinishingActivityLeavesConsumedCardStuckAtSubmitting`
   — grades Fail (SUBMITTING), calls `activity.finish()` so the Activity is
   finishing, then runs the queued review commit and drains the main looper. The
   review token is consumed ("Fail saved") but the feedback gate never reaches
   `APPLIED`. Asserts the answered card must be `APPLIED` and continuable.
2. `retainedConsumedButUnappliedCardMustRecoverContinue`
   — same setup (retained in-memory holder, no process death); asserts that a
   consumed-token answered card must expose a working Continue
   (`continueEnabled == true`, `continueAfterStudyAnswer() == true`).

### Exact commands

```sh
cd /home/skerraut/work/kani-study-feedback-freeze
export JAVA_HOME=/home/skerraut/.local/share/mise/installs/java/temurin-17
./gradlew :app:testDebugUnitTest \
  --tests "dev.bee.kanjianki.MainActivityStudyReviewFlowSubmitTest" --console=plain
```

### Observed failure (RED)

```
MainActivityStudyReviewFlowSubmitTest > appliedCallbackDroppedByFinishingActivityLeavesConsumedCardStuckAtSubmitting FAILED
    java.lang.AssertionError: Consumed-token answered card must reach APPLIED, not stay stuck at SUBMITTING expected:<APPLIED> but was:<SUBMITTING>

MainActivityStudyReviewFlowSubmitTest > retainedConsumedButUnappliedCardMustRecoverContinue FAILED
    java.lang.AssertionError: Re-rendered consumed answered card must expose a working Continue

24 tests completed, 2 failed
```

`store.hasConsumedToken(session.token)` is `true` in both tests at the point of
failure — the review committed, but the UI gate is stuck at `SUBMITTING`, so
`StudyAnswerFeedbackState.continueEnabled` is `false` and
`continueAfterStudyAnswer()` returns `false`. That is the freeze. The other 22
tests in the class pass, so the loop is specific to this bug.

## Data flow of the answer gate

`StudyAnswerFeedbackState` phases: `UNANSWERED → SUBMITTING → APPLIED →
CONTINUED`. `continueEnabled` is true **only** in `APPLIED`; the Continue button
maps to it (`MainActivityStudyFlashcardCompose.kt:359`,
`MainActivityStudyChoiceCompose.kt:312`).

Grading path (`MainActivityStudy.submitReview` →
`StudyAnswerSubmissionCoordinator.submit`):

1. `state.begin(...)` → `SUBMITTING`; `persistPending` writes a SUBMITTING
   pending envelope; the review is enqueued on the background `io` executor.
2. On IO completion, `MainActivityStudyReviewFlow` (lines 327 / 576 / 602) calls
   `activity.markStudyAnswerApplied(token)`.
3. `MainActivityStudy.markStudyAnswerApplied` (lines 515–522) hops to the main
   thread via `MainActivityBase.postToMainIfActive` and only there calls
   `state.markApplied(token)` + persists the APPLIED envelope.

`postToMainIfActive` (`MainActivityBase.kt:55–62`) **drops** the runnable when
`isDestroyed || isFinishing`.

## Ranked hypotheses (Phase 3)

1. **(CONFIRMED) Dropped applied-callback leaves the gate at SUBMITTING with no
   in-memory reconciliation.** If the Activity is finishing/destroyed in the
   window between the review commit and the posted `markStudyAnswerApplied`
   runnable (config change, teardown, backgrounding), `postToMainIfActive` drops
   the runnable. The scheduler token is consumed and the row advances, but the
   feedback gate stays `SUBMITTING`. Prediction: with `isFinishing == true` at
   commit time, `hasConsumedToken` is true yet phase stays `SUBMITTING` and
   Continue never works. **Both RED tests confirm this exactly.**

2. (Rejected as the whole story) Pure process-death recovery is broken. The
   durable process-death path *does* reconcile: `MainActivityStudyQueueCoordinator
   .computePendingAnswerRender` promotes a SUBMITTING pending envelope to APPLIED
   when `study.store.hasConsumedToken(...)` is true (lines 293, 388–392), and
   `StudySessionRestorationPolicy.restorePendingItem` requires `tokenConsumed`
   and `schedulerRevision == revision + 1`. So a full process death that re-reads
   the durable envelope self-heals. This hypothesis does not explain the freeze
   on its own.

3. (Rejected) The scheduler double-applies / token idempotency is broken. The
   token is consumed exactly once; re-submitting is correctly rejected as a
   duplicate. The bug is purely the UI gate, not the scheduler.

## Root cause

The APPLIED transition is delivered only through a main-thread post that
`postToMainIfActive` discards when the Activity is finishing/destroyed. When that
happens **and the process survives** (config change / retained ViewModel holder),
the in-memory `StudyAnswerFeedbackState` is left permanently at `SUBMITTING` even
though the review token was consumed and the row advanced. Unlike the durable
process-death recovery path
(`MainActivityStudyQueueCoordinator.computePendingAnswerRender`, which promotes
SUBMITTING→APPLIED against `hasConsumedToken`), the retained-holder re-render
path (`MainActivityStudyQueueCoordinator.renderStudy`, the in-place re-render of
an active SUBMITTING/APPLIED card) has **no reconciliation of the in-memory gate
against the consumed token**. Result: "Fail saved" + "Incorrect." + a Continue
button that maps to `continueEnabled == false`, so `continueAfterStudyAnswer()`
(→ `StudyAnswerFeedbackState.tryContinue`, which requires `APPLIED`) returns
false forever. The card is frozen.

Regression #12f7e6f6 ("fix(study): require explicit continue after answers")
removed the `autoContinueOnApply` / effect-channel auto-continue that previously
provided an alternate advancement route; the explicit-Continue-only model makes
the dropped-APPLIED window terminal for the mounted card.

## Where a production fix should land (next card — not this one)

Reconcile the in-memory answer gate against a consumed token on the
retained-holder path: when `hasConsumedToken(token)` is true but the in-memory
`StudyAnswerFeedbackState` is still `SUBMITTING`, promote it to `APPLIED` (the
same rule the durable process-death path already applies), so the mounted
Continue button works. Alternatively, make the APPLIED transition durable across
a dropped `postToMainIfActive` (e.g. drive it from lifecycle resume /
`renderStudy` rather than only a fire-and-forget main-thread post).

## Files

- Added RED tests:
  `app/src/test/kotlin/dev/bee/kanjianki/MainActivityStudyReviewFlowSubmitTest.kt`
- Evidence note: `docs/study-feedback-freeze-evidence.md` (this file).
