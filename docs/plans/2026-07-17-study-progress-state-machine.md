# Study session progress state machine implementation plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task.

**Goal:** Make Kani Study progress, completion, and feedback all come from one authoritative immutable route snapshot so the app can never show a 5/7 header and a Done screen at the same time.

**Architecture:** Narrow authoritative extraction, not a broad scheduler rewrite. Current main already has a reducer/snapshot pipeline (`StudySessionViewModel`, `StudyAnswerFeedbackState`, `StudySessionProgressTracker.snapshot()`), so the bug is not missing state machinery; it is that the completion decision, the top header, and the done screen still read from different live state at different times. Add one route snapshot/version object and thread it through renderers so header + done + continue/undo all see the same frame.

**Tech Stack:** Kotlin, AndroidX ViewModel/Compose, Robolectric, Gradle (`:core:test`, `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestJavaWithJavac`, `:app:connectedDebugAndroidTest`, `ciFast`, `ciQuality`, `ciRelease`).

---

## Decision

**Decision: narrow authoritative extraction.**

Why this instead of a full state-machine rewrite:

- The app already has explicit lifecycle and feedback state: `StudySessionViewModel.kt` defines `StudySessionEvent`, `StudySessionPhase`, and immutable `StudySessionUiState`; `StudyAnswerFeedbackState.kt` already enforces the explicit Continue boundary; `StudySessionProgressTracker.kt` already exposes a coherent `snapshot()`.
- The current failure mode is a split read path, not a missing reducer. `MainActivityStudyQueueCoordinator.pendingRepairOrDoneRender()` computes done from live tracker state, while `MainActivityStudyRoute.renderComposeStudyRoute()` and the study renderers recompute the header from live tracker state again. `MainActivityStudyDoneActions.renderStudyRunDone()` then caches its own done-screen state. Those are separate reads of the same session.
- A broader rewrite would duplicate the already-tested feedback/review machinery and risk touching scheduler semantics that are not the problem. The smallest fix is to make the existing reducer publish one accepted route snapshot/version and have every renderer consume it.

---

## Reverified evidence against current `origin/main`

Current base commit: `ed5f37a5` (`origin/main`).

No open Study PR is currently relevant: `gh pr list --repo bee-san/kanji_anki --state open --search study` returned `[]`.

Key current-main facts:

- `core/src/main/kotlin/dev/bee/kanjianki/core/StudySessionProgressTracker.kt`
  - `snapshot()` already returns a coherent progress frame.
  - `topBarProgress(activeTask, continueAllKanjiSession)` is still a live recomputation.
  - `atHardCap(...)` is only one half of the done predicate.
- `app/src/main/kotlin/dev/bee/kanjianki/StudySessionViewModel.kt`
  - `StudySessionProgressUiState` is already the immutable progress object published to Compose.
  - `StudySessionEvent` / `StudySessionReducer` already model mounted/feedback/progress/presentation/reset transitions.
- `app/src/main/kotlin/dev/bee/kanjianki/StudyAnswerFeedbackState.kt`
  - `begin(...)`, `markApplied(...)`, `tryContinue()` and `resetForRetry(...)` already encode the explicit Continue boundary.
- `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyQueueCoordinator.kt`
  - `computeStudyRender(...)` and `pendingRepairOrDoneRender(...)` decide route completion.
  - `initializeSessionTarget(...)` can reconcile the target downward.
  - `terminalRender(...)` / `nonRestorableSessionRender(...)` already guard stale recovery, but not a single shared route snapshot.
- `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyRoute.kt`
  - `renderComposeStudyRoute(...)` still reads live `studySessionTracker.topBarProgress(...)`.
- `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyDoneActions.kt`
  - `renderStudyRunDone(...)` shows the done screen after `showComplete()`, but it does not own the same snapshot object as the header.
- `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyReviewFlow.kt`
  - `submitReview(...)` / `markStudyAnswerApplied(...)` already keep answer submission separate from explicit Continue; do not collapse that behavior.

Existing tests already cover most of the contract and should remain green during the refactor:

- `core/src/test/kotlin/dev/bee/kanjianki/core/StudySessionProgressTrackerTest.kt`
  - `snapshotReturnsOneCoherentProgressFrame`
  - `topBarProgressPreservesVisibleCountsAndFractions`
- `app/src/test/kotlin/dev/bee/kanjianki/StudySessionViewModelTest.kt`
  - `mountedFeedbackTransitionsArePublishedAsImmutableSnapshots`
  - `trackerMutationsPublishRealProgress`
- `app/src/test/kotlin/dev/bee/kanjianki/StudyAnswerFeedbackStateTest.kt`
  - explicit Continue / stale-token / restore coverage
- `app/src/test/kotlin/dev/bee/kanjianki/MainActivityStudyReviewFlowSubmitTest.kt`
  - `correctReviewAppliesOnceButDoesNotAdvanceUntilContinue`
  - `wrongReviewAlsoPersistsFeedbackUntilContinue`
- `app/src/test/kotlin/dev/bee/kanjianki/StudySessionLearnAheadTest.kt`
  - learn-ahead repeats block Done until the session really finishes

---

## Proposed state model

Keep the existing reducer and feedback machine, but make the route snapshot authoritative.

### Existing state/event types to keep

- `StudySessionEvent` (`StudySessionViewModel.kt`)
  - `SessionMounted`
  - `FeedbackChanged`
  - `ProgressChanged`
  - `PresentationChanged`
  - `Reset`
- `StudySessionPhase`
  - `IDLE`
  - `LOADING`
  - `ACTIVE`
  - `SUBMITTING`
  - `FEEDBACK`
  - `ADVANCING`
  - `COMPLETE`
- `StudyAnswerFeedbackPhase` (`StudyAnswerFeedbackState.kt`)
  - `UNANSWERED`
  - `SUBMITTING`
  - `APPLIED`
  - `CONTINUED`

### New state types to add

Create `app/src/main/kotlin/dev/bee/kanjianki/StudyRouteSnapshot.kt` (new file) with:

- `StudyRouteVersion` / `StudySessionGeneration` — a monotonic token for stale-callback rejection.
- `StudyRouteCompletionReason` — terminal reason for the current route:
  - `HARD_CAP`
  - `LEARN_AHEAD_REPEAT`
  - `REPAIR`
  - `NO_SESSION`
  - `TARGET_RECONCILIATION`
  - `EXPLICIT_CONTINUE`
  - `UNDO`
  - `RESTORE`
  - `STALE_CALLBACK_DROPPED`
- `StudyRouteSnapshot` — the accepted immutable route frame.

Recommended fields:

- `version: Long`
- `sessionToken: String?`
- `phase: StudySessionPhase`
- `feedback: StudyAnswerFeedbackSnapshot?`
- `progress: StudySessionProgressUiState`
- `displayedCompletedCount: Int`
- `displayedTargetCount: Int`
- `remainingCount: Int`
- `isComplete: Boolean`
- `continueAllKanjiSession: Boolean`
- `completionReason: StudyRouteCompletionReason?`

The important rule is that header progress, done availability, and the done screen all consume the same `StudyRouteSnapshot` instance/version.

---

## State transition table

| Input / event | Preconditions | Authoritative write | Visible result |
|---|---|---|---|
| `AnswerSubmitted` (choice / typed / writing / self-graded) | mounted session token matches feedback token | `StudyAnswerFeedbackState.begin(...)`, `StudySessionEvent.PresentationChanged(SUBMITTING)` | same card, feedback visible, Continue disabled |
| `AnswerApplied` | same session token; async persistence succeeded | feedback phase becomes `APPLIED`; progress snapshot is unchanged | feedback stays visible until explicit Continue |
| `ContinueRequested` | feedback phase is `APPLIED` and `tryContinue()` succeeds | route version increments; `phase = ADVANCING`; next route is loaded from the same snapshot/version | one and only one advance to the next card/route |
| `UndoRequested` / `BackRequested` | a reversible token exists | restore the prior mounted session, restore feedback snapshot, keep progress frame coherent | the original task/card reappears; no phantom extra advancement |
| `LearningStepRepeated` | item is still in learning/relearning | progress may move, but `isComplete` remains false | the run keeps serving the repeat instead of Done |
| `LearnAheadRepeatDue` | hard cap is reached but same-session repeats fall within learn-ahead | keep `completionReason = LEARN_AHEAD_REPEAT` and `isComplete = false` | no Done yet |
| `RepairAvailable` / `Skip` / invalid item pruning | repair or invalid item still counts as work in the current run | include or exclude the task key exactly once in the snapshot; never mutate the displayed header separately | the header and done decision stay aligned |
| `Task-key / rung change` | the planned queue shape changes after reconciliation | publish a new snapshot version; if the target shrinks, first clamp to a consistent `N/N` terminal frame | no hidden 5/7 -> Done jump |
| `ProgressReconciledDownward` | current queue admits fewer items than the earlier frame | first publish the terminal `N/N` snapshot, then allow the done transition on the next render | the UI never silently decrements target while skipping the terminal frame |
| `ProcessRecreated` / restore | persisted session token matches the restored frame | restore `StudyAnswerFeedbackState.snapshot()` and `StudySessionUiState` from the saved snapshot | the same answered card resumes correctly after process death |
| `StaleAsyncCallback` | version/token mismatch | no mutation | callback is dropped silently and cannot mutate current state |

---

## Exact files/functions to change

### 1) Authoritative route snapshot types

- Create: `app/src/main/kotlin/dev/bee/kanjianki/StudyRouteSnapshot.kt`
  - `StudyRouteVersion`
  - `StudyRouteCompletionReason`
  - `StudyRouteSnapshot`
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/StudySessionViewModel.kt`
  - extend `StudySessionProgressUiState` with `remainingCount`, `displayedCompletedCount`, `displayedTargetCount`, `isComplete`, and `version` (or wrap them in `StudyRouteSnapshot` if that is cleaner)
  - make `publishProgress()` build the route snapshot from one `tracker.snapshot()` read
  - keep `StudySessionReducer` intact unless the new snapshot needs one extra `RouteFramePublished` event

### 2) Route completion and header rendering

- Modify: `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyQueueCoordinator.kt`
  - `computeStudyRender(...)`
  - `pendingRepairOrDoneRender(...)`
  - `initializeSessionTarget(...)`
  - `terminalRender(...)`
  - `nonRestorableSessionRender(...)`
  - Ensure this file publishes one accepted snapshot/version before either the active route or the done screen renders.
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyRoute.kt`
  - `renderComposeStudyRoute(...)`
  - Stop calling `studySessionTracker.topBarProgress(...)` directly; read the accepted snapshot/version instead.
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyDoneActions.kt`
  - `renderStudyRunDone(...)`
  - `renderCurrentStudyDone(...)`
  - Use the captured snapshot/version, not a fresh live tracker read.
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyProgress.kt`
  - keep this as the single presenter/helper for snapshot-based progress rendering if a new helper is needed.
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyFlashcard.kt`
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyWritingSession.kt`
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyChoiceSessions.kt`
  - all of these currently call `studySessionTracker.topBarProgress(...)`; switch them to the same snapshot source.

### 3) Answer/continue/restore boundaries

- Modify: `app/src/main/kotlin/dev/bee/kanjianki/StudyAnswerFeedbackState.kt`
  - likely no semantic change; keep the explicit Continue state machine as-is.
  - only touch if the new route snapshot needs to expose its `snapshot()` more directly.
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyReviewFlow.kt`
  - `submitReview(...)`
  - `markStudyAnswerApplied(...)`
  - `continueAfterStudyAnswer(...)`
  - `undoLastRating(...)`
  - Thread the route version/session token through these callbacks so stale callbacks cannot mutate a newer route.
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudy.kt`
  - `prepareSessionRender(...)`
  - `acceptNewActiveStudySession(...)`
  - `acceptRestoredActiveStudySession(...)`
  - `clearAdvancingStudyRecovery(...)`
  - Make these methods accept and validate the same route version token.
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/AsyncHomeRouteLoader.kt`
  - ensure stale loader callbacks cannot mutate a newer accepted route snapshot.
  - keep the generation guard, but make the route version the one source of truth for UI mutation.

### 4) Existing progress tracker seam

- Keep: `core/src/main/kotlin/dev/bee/kanjianki/core/StudySessionProgressTracker.kt`
  - `snapshot()` remains the authoritative live progress read.
  - `topBarProgress(...)` may stay for compatibility/tests, but production renderers should stop depending on it directly.
- Keep: `app/src/main/kotlin/dev/bee/kanjianki/StudySessionTracker.kt`
  - `snapshot()` remains the canonical live read for the app layer.
  - `initializeSessionPlan(...)`, `setTargetCount(...)`, and `atHardCap(...)` stay, but only the route snapshot builder should combine them into visible UI state.

---

## RED-GREEN test sequence

### Task 1: Characterize the current split-brain behavior

Add or extend tests first so the failure is pinned before code moves.

Recommended tests:

- `core/src/test/kotlin/dev/bee/kanjianki/core/StudySessionProgressTrackerTest.kt`
  - add a case that proves one `snapshot()` call yields a coherent frame even when the live target is reconciled downward.
  - add a case that proves `topBarProgress(...)` is only a compatibility helper and does not become the done predicate.
- `app/src/test/kotlin/dev/bee/kanjianki/StudySessionViewModelTest.kt`
  - add a snapshot test that asserts `progress`, `feedback`, and `phase` stay aligned through mount / feedback / complete transitions.
- `app/src/test/kotlin/dev/bee/kanjianki/MainActivityStudyReviewFlowSubmitTest.kt`
  - preserve/extend `correctReviewAppliesOnceButDoesNotAdvanceUntilContinue()` so the explicit Continue boundary stays locked.
- New: `app/src/test/kotlin/dev/bee/kanjianki/StudyRouteSnapshotTest.kt`
  - assert a 5/7 frame cannot become Done without first publishing a consistent terminal `N/N` snapshot.
- New: `app/src/test/kotlin/dev/bee/kanjianki/AsyncHomeRouteLoaderTest.kt`
  - assert a stale generation cannot mutate the current route snapshot.

### Task 2: Introduce the route snapshot model

- Add `StudyRouteSnapshot.kt`.
- Make `StudySessionViewModel.publishProgress()` emit one immutable route snapshot or a frame that contains `StudySessionProgressUiState + isComplete + version`.
- Run the focused tests above and ensure the new snapshot tests fail before the render wiring lands.

### Task 3: Wire the header and done screen to the same snapshot

- Update `MainActivityStudyQueueCoordinator.pendingRepairOrDoneRender(...)` to emit the accepted snapshot/version once.
- Update `MainActivityStudyRoute.renderComposeStudyRoute(...)` and `MainActivityStudyDoneActions.renderStudyRunDone(...)` to read the same accepted snapshot/version.
- Remove the remaining direct `topBarProgress(...)` calls from the renderer path.
- Re-run the focused tests; the 5/7 -> Done regression must now fail if either header or done screen diverges.

### Task 4: Harden stale callbacks and restore semantics

- Thread the route version/token through `MainActivityStudyReviewFlow.kt`, `MainActivityStudy.kt`, and `AsyncHomeRouteLoader.kt`.
- Make stale callbacks a no-op rather than a mutation source.
- Add restore tests for process death / retry / undo so the same answered card can resume without changing the current route version.

### Task 5: Broader verification

Run the same exact commands the repo exposes from `./gradlew tasks --all --console=plain`:

- `./gradlew core:test`
- `./gradlew app:testDebugUnitTest`
- `./gradlew app:compileDebugAndroidTestJavaWithJavac`
- `./gradlew app:connectedDebugAndroidTest`
- `./gradlew ciFast`
- `./gradlew ciQuality`
- `./gradlew ciRelease`

For targeted checks during implementation, use the module tasks with `--tests` filters, for example:

- `./gradlew :core:test --tests dev.bee.kanjianki.core.StudySessionProgressTrackerTest`
- `./gradlew :app:testDebugUnitTest --tests dev.bee.kanjianki.StudySessionViewModelTest --tests dev.bee.kanjianki.StudyAnswerFeedbackStateTest --tests dev.bee.kanjianki.MainActivityStudyReviewFlowSubmitTest --tests dev.bee.kanjianki.StudySessionLearnAheadTest`

---

## Migration strategy

1. Add the new snapshot type and tests first.
2. Keep all existing reducer/feedback code intact while the snapshot is introduced.
3. Switch renderers one by one to the snapshot source; do not flip the whole route at once.
4. Keep `topBarProgress(...)` around only until every caller has moved.
5. Once the header and done screen are both reading the same versioned snapshot, delete the now-redundant live reads.

This should be a narrow refactor, not a rewrite of study scheduling or review semantics.

---

## Rollback plan

If the snapshot extraction regresses anything:

1. Revert the renderer-wiring commit first.
2. Keep the characterization tests that proved the regression; they are the guardrail.
3. If necessary, temporarily fall back to the current `StudySessionTracker.topBarProgress(...)` path while keeping the new snapshot builder behind a feature-local helper.
4. Do not roll back `StudyAnswerFeedbackState` or the explicit Continue contract unless a test shows that specific state machine is broken; it is already separately covered.

---

## Small logical commits

Suggested implementation commit chunks:

1. `test: characterize study route snapshot and 5/7 terminal behavior`
2. `refactor: add authoritative study route snapshot/version`
3. `refactor: thread route snapshot through study renderers`
4. `refactor: harden stale callback and restore guards`
5. `test: lock explicit continue and stale-generation semantics`

Keep each commit small enough to revert independently.

---

## Acceptance criteria

- A single immutable route snapshot owns the visible progress numbers and `isComplete`.
- Header and done screen consume the same snapshot/version in the same render transition.
- The app never silently transitions from visible 5/7 to Done for a target of 7.
- When the target shrinks, the UI first publishes a consistent terminal `N/N` frame.
- Explicit Continue still gates advancement.
- Undo/back, restore, learn-ahead, repairs, skips, and stale async callbacks all have explicit, tested semantics.
- The implementation can be verified with the Gradle commands listed above without needing any new ad-hoc build entrypoints.
