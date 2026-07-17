# Kani Study progress 5/7 -> Done root-cause report

Date: 2026-07-17
Base: `origin/main` / `ed5f37a517551c3c071d0e0aeba60b6832bc3a68` (`v0.4.196`)
Scope: archaeology only; no production fix.

User observation treated as authoritative: a real device showed Study progress around `5 / 7`, then the route transitioned to Done before the visible target was exhausted.

## Executive conclusion

The defect is not explained by a single bad arithmetic formula. The current Study route has a split-brain completion model:

1. The terminal predicate is `StudySessionTracker.atHardCap(...)`, reached from `MainActivityStudyQueueCoordinator.pendingRepairOrDoneRender()` before a new session is selected.
2. The visible header is rendered later from the same mutable tracker via `MainActivityStudyRoute.renderComposeStudyRoute()` and `StudyTopBar`, not from the immutable snapshot that caused Done.
3. Async route loads are generation-guarded only at render time. Their background `load` phase still mutates the tracker, active session, recovery flags, badge counts, and target reconciliation before the generation check can discard stale renders.
4. Recovery/Continue handoff (`StudySessionRecoveryStore.continuePending()` -> `computeStudyRender()` -> `terminalRender()`) introduced more non-restorable/terminal paths where route load mutation and route render are separated.

Therefore a tight code-proven 5/7 -> Done sequence is: a terminal route load can decide Done from one tracker target, while a different route load/recovery reconciliation has already published, or later publishes, a different target used by the header/retained progress. `clearAdvancingStudyRecovery()` is not the arithmetic cause of premature completion; it is an incidental terminal-handoff step that clears active/recovery state before Done rendering and makes the terminal screen read whatever mutable tracker state survived the async interleaving.

Confidence labels used below:

- PROVEN: directly follows from current code or passing tests.
- STRONGLY SUPPORTED: code path exists and matches the observed symptom, but the exact real-device interleaving was not captured in logs.
- HYPOTHETICAL: plausible but not proven by current code evidence.

## A. State/data-flow diagram

Legend: authoritative = decides scheduler/review truth; derived = computed from authoritative data; cached = retained for UI/perf; persisted = survives process; mutable = can change during a route load.

```mermaid
flowchart TD
    DB[(LocalStore SQLite + SharedPreferences)]:::auth
    Rows[activeDashboardRows]:::derived
    Items[studyItemsForKanji / seeded studyQueue]:::derived
    Plan[AdaptiveLoadPlan]:::derived
    Repairs[dueSimilarWritingRepairs]:::derived

    DB --> Rows
    DB --> Items
    Rows --> Plan
    Items --> Plan
    DB --> Repairs

    Queue[MainActivityStudyQueueCoordinator.computeStudyRender]:::mutable
    Rows --> Queue
    Items --> Queue
    Plan --> Queue
    Repairs --> Queue

    Tracker[StudySessionTracker]:::mutable
    Progress[StudySessionProgressTracker: completedCount, targetCount, task key sets]:::mutable
    Tracker --> Progress

    Queue -->|initializeSessionTarget; includePendingTask| Tracker
    Queue -->|StudySessionActions.plannedStudySession -> initializeSessionPlan| Tracker
    Queue -->|pendingRepairOrDoneRender -> atHardCap| DonePredicate{completed >= target?}:::derived
    Tracker --> DonePredicate

    Session[activeSession in StudySessionViewModel]:::cached
    Feedback[StudyAnswerFeedbackState]:::mutable
    VM[StudySessionViewModel.uiState StateFlow]:::cached
    Recovery[StudySessionRecoveryStore pending/active envelopes]:::persisted

    Session --> VM
    Feedback --> VM
    Tracker -->|onChanged publishProgress| VM
    Feedback -->|persistPendingStudyAnswer| Recovery
    Recovery -->|restore/continued handoff| Queue
    Queue -->|acceptNew/Restored/ClearAdvancing| Session
    Queue -->|clearAdvancingStudyRecovery| Recovery

    Review[MainActivityStudyReviewFlow.performNormalReview]:::auth
    Review -->|saveAppliedReview transaction| DB
    Review -->|commitPreparedTask| Tracker
    Review -->|markStudyAnswerApplied| Feedback
    Feedback -->|Continue| Recovery
    Feedback -->|Continue| Queue

    Header[MainActivityStudyRoute.renderComposeStudyRoute]:::derived
    TopBar[StudyTopBar completed / target]:::derived
    Done[StudyDoneScreen]:::derived
    DoneActions[MainActivityStudyDoneActions.renderStudyRunDone]:::derived

    Tracker -->|topBarProgress(activeSession != null)| Header
    Header --> TopBar
    DonePredicate --> DoneActions
    DoneActions -->|showComplete| VM
    DoneActions --> Header
    Header --> Done

    Async[AsyncHomeRouteLoader]:::mutable
    Queue --> Async
    Async -->|generation checks render only| Header
    Async -. stale load can still mutate .-> Tracker
    Async -. stale load can still mutate .-> Session
    Async -. stale load can still mutate .-> Recovery

    classDef auth fill:#0b3d91,color:#fff
    classDef derived fill:#455a64,color:#fff
    classDef cached fill:#6a1b9a,color:#fff
    classDef persisted fill:#1b5e20,color:#fff
    classDef mutable fill:#b71c1c,color:#fff
```

Component annotations:

- Queue coordinator (`MainActivityStudyQueueCoordinator.kt`): mutable orchestrator. It reads LocalStore, computes plan/queue, mutates `activeStudyPlan`, `studySessionBadgeCount`, tracker target, active session, recovery flags, and returns a render thunk.
- Due/requeue/learn-ahead/pending repair collections: derived from LocalStore plus scheduler settings. `dueRepairs` can add target work before selection (`pendingRepairOrDoneRender()` lines 501-535). Learning repeats are derived by `StudySessionTracker.dueCompletedLearningRepeatTaskKeys()` and injected into `initializeSessionPlan()` by `StudySessionActions.plannedStudySession()`.
- `StudySessionTracker`: mutable retained session progress. It wraps `StudySessionProgressTracker` plus planned/completed task key sets. It is not persisted, but lives in the retained `StudySessionViewModel` across configuration changes.
- `StudySessionViewModel`: cached retained route state. It owns `tracker`, `currentSession`, feedback, and progress snapshots. It publishes progress reactively but does not decide scheduler truth.
- MainActivity progress/route models: mutable bridge. `activeSession` delegates to `studySessionViewModel.currentSession`; `renderComposeStudyRoute()` pulls progress directly from tracker at composition time.
- Compose header: derived UI. `StudyTopBar` prints `$completed / $target`; it does not know why Done was selected.
- Feedback/Continue state: mutable UI gate. Answer submission moves UNANSWERED -> SUBMITTING -> APPLIED; Continue moves APPLIED -> CONTINUED and calls `renderStudy()`.
- Persistence: authoritative for review commits and recovery identity. Review token and item advancement are transactional; recovery envelope is SharedPreferences CAS by raw JSON.
- Lifecycle restore: persisted/cached bridge. Active and pending envelopes restore exact cards/feedback, then set `recoveredStudyRunNeedsTargetReconciliation` for active/fallback restores.
- Recovery clearing: mutable handoff cleanup. `clearAdvancingStudyRecovery()` consumes a continued envelope before mounting a next non-restorable/terminal route.
- Done rendering: derived UI selected by `atHardCap`; it calls `showComplete()` and then renders the normal Study route chrome, including the live tracker-based header.

## B. Completion-predicate and session-replacement inventory

Real routes to Done or session clearing/replacement:

1. Queue empty -> empty Study Done
   - File/function: `MainActivityStudyQueueCoordinator.computeStudyRender()` lines 77-83.
   - Reads: `rows.isEmpty()`, `dueRepairs`, pending repair/done branch.
   - Mutates: target initialized to 0, badge 0/repair count, then `renderEmptyStudyQueue()` via `terminalRender()`.
   - State class: derived LocalStore rows; mutable tracker/badge.

2. Hard-cap Study run Done
   - File/function: `pendingRepairOrDoneRender()` lines 537-552.
   - Predicate: `study.studySessionTracker.atHardCap(study.continueAllKanjiSession)`; implementation is `targetCount > 0 && completedCount >= targetCount` when not continue-all (`StudySessionProgressTracker.kt` lines 93-95).
   - Guard: before declaring Done, checks no due completed learning repeats within learn-ahead horizon (`dueCompletedLearningRepeatTaskKeys()` lines 164-190).
   - Mutates: `refreshSessionBadgeCount(0)` and warm availability, then terminal render.

3. No selectable session -> `renderNoStudySession()`
   - File/function: `computeStudyRender()` lines 137-144.
   - Reads: scheduler-selected `session == null` or `session.item == null` after target initialization and `pendingRepairOrDoneRender()`.
   - Done variant: `MainActivityStudyDoneActions.renderNoStudySession()` may render focus done when `!continueAllKanjiSession && seededPlan.focusComplete()`.

4. Pending repair preempts Done
   - File/function: `pendingRepairOrDoneRender()` lines 501-535.
   - Reads: `dueRepairs.firstOrNull()`.
   - Mutates: includes repair progress key, active repair, active session, active task; returns non-restorable render. This can replace the session even if general queue appears done.

5. Invalid/skip/repair removal
   - Repair skip: `MainActivityStudyReviewFlow.skipSimilarWritingRepair()` lines 332-354 completes active repair task, calls `StudyRepairActions.skipSimilarWritingRepair()`, clears `activeSimilarWritingRepair`, then `renderStudy()`.
   - Invalid pending answer restore: `computePendingAnswerRender()` lines 222-245 clears malformed/untrusted pending recovery and falls back to ordinary route.
   - Active restore mismatch: `computeActiveSessionRender()` lines 356-376 clears mismatching active recovery and falls back.

6. Undo/back/reset paths
   - Undo: `MainActivityStudyReviewFlow.undoLastRating()` lines 605-639 clears undo state, restores review, clears pending answer, and routes through `renderStudyForKanji(restoredKanji)`. `computeStudyForKanjiRender()` lines 414-416 calls `clearStudyModeOverrides()` and `resetStudyRunProgress()`.
   - Done Back Home: `MainActivityStudyDoneActions.backHome()` lines 180-183 clears mode overrides and renders Home; it does not reset tracker itself.
   - New focused study: `MainActivityStudy.startFocusedStudy()` lines 171-174 clears overrides, resets progress, and renders Study.
   - Targeted kanji route: `computeStudyForKanjiRender()` lines 414-415 resets progress and can replace the active session.

7. Restore and recreation
   - Pending answered card: `computePendingAnswerRender()` -> `computeAppliedPendingAnswerRender()` lines 313-344 restores the answered session and feedback before selecting a next item.
   - Active session: `acceptRestoredActiveStudySession()` lines 825-837 mounts the session, sets `recoveredStudyRunNeedsTargetReconciliation = true`, registers active task.
   - Pending fallback: `acceptPendingFallbackStudySession()` lines 854-866 similarly sets reconciliation true.
   - Reconciliation: `initializeSessionTarget()` lines 567-582 may set target to `max(currentTarget, completed + selectableRemaining)` and then clears the reconciliation flag.

8. Stale callbacks/results
   - Review write stale session is dropped before persistence (`MainActivityStudyReviewFlow.performNormalReview()` lines 493-500).
   - Async route stale render is dropped (`AsyncHomeRouteLoader.kt` lines 104-110), but the background `load` already ran and can mutate tracker/session/recovery before the drop. This is the important stale-load seam.

9. Feedback Continue timing
   - Submission: `StudyAnswerSubmissionCoordinator.submit()` lines 34-52 begins feedback and persists pending before enqueueing review.
   - Commit: `performNormalReview()` lines 539-575 prepares active task, saves review, commits tracker progress, marks answer APPLIED.
   - Continue: `MainActivityStudy.continueAfterStudyAnswer()` lines 547-576 CASes APPLIED -> CONTINUED recovery and calls `renderStudy()`.
   - Auto-continue: `markStudyAnswerApplied()` lines 517-527 emits `AutoContinue`; `handleStudySessionEffect()` lines 532-537 routes into Continue.

## C. Counter inventory and divergence modes

Counters and stores:

- `StudySessionProgressTracker.completedCount`: mutable, session-retained. Incremented only by unique `completedTaskKeys` in `markTaskCompleted()` lines 129-138, normally through `StudySessionTracker.commitPreparedTask()` lines 341-357.
- `StudySessionProgressTracker.targetCount`: mutable, session-retained. Set/increased by `resetProgress()`, `initializeTarget(plan)`, `setTargetCount()`, `includePendingTask()`, `registerTaskShown()`, `markTaskCompleted()` and `StudySessionTracker.initializeSessionPlan()`.
- `StudySessionProgressTracker.seenTaskKeys` / `completedTaskKeys`: mutable dedupe sets. `includePendingTask()` refuses keys already seen or completed; session review task keys include token (`session:<taskType>:<kanji>:<token>`), while planned keys do not.
- `StudySessionTracker.plannedSessionTaskKeys`: mutable current queue shape without session tokens.
- `StudySessionTracker.completedPlannedSessionTaskKeys`: mutable completed planned keys by task type/kanji, used to skip and detect same-kanji learning repeats.
- `StudyNowCountPolicy.countSeeded(...)`: derived count of selectable work for the current route compute.
- `dueRepairs.size`: derived extra work; incorporated via `StudyNowCountPolicy.includingAdditionalTaskKeys()` and `includePendingTask()`.
- `pendingRepeatTaskKeys`: derived from current items and completed planned keys; grows target one persisted repeat step at a time.
- `studySessionBadgeCount`: cached shell count, explicitly refreshed by queue coordinator; separate from tracker.
- `StudySessionProgressUiState`: cached StateFlow snapshot published on tracker changes; not used by `renderComposeStudyRoute()` for the header.
- `activeStudyPlan.target/remaining/status`: derived plan counters used for summaries and availability, not the hard-cap predicate.

Divergence modes:

1. Header vs terminal predicate divergence: Done selection and header rendering are separated in time; header reads live tracker at composition, not the terminal predicate snapshot.
2. Load-vs-render divergence: `AsyncHomeRouteLoader` generation protects render but not the mutating background load. A stale load can alter `targetCount`, `activeSession`, or recovery flags without rendering.
3. Reconciliation drift: restored active/fallback sessions set `recoveredStudyRunNeedsTargetReconciliation`; the next route compute can change target to `completed + selectableRemaining`, but terminal clearing sets the flag false.
4. Planned-key vs token-key mismatch: progress completion keys include session token; planned repeat keys do not. This is intentional for repeated appearances but can make target growth and completion increments occur in different phases.
5. Pending repair count asymmetry: repair progress keys are included before repair selection; skip/removal can complete or remove repair state and immediately route Study again.
6. Feedback gate ordering: visible feedback can be APPLIED while route has not advanced; Continue triggers a new compute whose target can differ from the header shown during feedback.
7. Targeted undo/reset path: `renderStudyForKanji()` resets all progress, unlike ordinary `renderStudy()`; undo can replace the session with a targeted one and a fresh target.

## D. Exact event trace from visible 5/7 to Done

A fully deterministic current-main reproduction was not produced in this pass because the real seam is Activity + async route mutation + retained ViewModel + process/recovery state, and the current unit tests do not expose a single API that asserts the real terminal route and header snapshot atomically. The following is the tightest code-proven sequence consistent with the observed device behavior.

### Trace

T0 — Active Study route visible

- Label: PROVEN as a possible UI state.
- Active session identity: `activeSession != null` in `StudySessionViewModel.currentSession`.
- Tracker snapshot visible to header: `completedCount = 5`, `targetCount = 7`.
- Header inputs: `renderComposeStudyRoute()` calls `studySessionTracker.topBarProgress(activeSession != null, continueAllKanjiSession)` and `StudyTopBar` prints `5 / 7`.
- Completion inputs: not read at this moment unless a route compute is running.

T1 — User answers or continues the current card

- Label: PROVEN.
- Submission begins through `StudyAnswerSubmissionCoordinator.submit()` and persists SUBMITTING pending recovery before enqueueing review.
- Review commit path calls `StudySessionTracker.prepareActiveTask()`, persists review transaction, then `commitPreparedTask()` increments progress and clears `activeTask`.
- `markStudyAnswerApplied()` publishes APPLIED feedback; explicit/auto Continue calls `continueAfterStudyAnswer()`.

T2 — Continue creates an advancing recovery handoff and starts an async Study route compute

- Label: PROVEN.
- `continueAfterStudyAnswer()` converts APPLIED pending recovery to CONTINUED (`StudySessionRecoveryStore.continuePending()`), sets `studyRecoveryRouteActive`, then calls `renderStudy()`.
- `renderStudy()` queues `computeStudyRender()` through `AsyncHomeRouteLoader`.

T3 — Background route compute mutates session counters before any render-generation acceptance

- Label: PROVEN.
- `computeStudyRender()` mutates `activeStudyPlan`, badge count, target reconciliation, tracker pending tasks, repair state, and active session in the background `load` phase.
- `AsyncHomeRouteLoader` only checks generation later in the posted main-thread render (`AsyncHomeRouteLoader.kt` lines 104-110). Stale load mutation is therefore allowed.

T4 — Terminal predicate can be evaluated against a tracker state different from the most recently visible header

- Label: STRONGLY SUPPORTED.
- The Done branch reads `atHardCap()` before selecting a new session. It does not capture an immutable progress frame for the Done route.
- If tracker target has been reduced/reconciled to 5 while `completedCount >= 5`, `pendingRepairOrDoneRender()` returns `terminalRender(...) { renderStudyRunDone(plan) }`.
- Queue contents/counts at this point: `pendingRepeats.isEmpty()` and no due repair is selected; otherwise the branch falls through/repairs instead of Done.

T5 — `clearAdvancingStudyRecovery()` runs for terminal routes

- Label: PROVEN.
- `terminalRender()` calls `clearAdvancingStudyRecovery(advancingRecovery, null)` before rendering Done.
- It clears the continued recovery, sets `activeSession = null`, clears feedback, sets `studyRecoveryRouteActive = false`, clears repair state, and clears target-reconciliation flag.
- It does not read or write `completedCount`, `targetCount`, due queue, or planned key sets.

T6 — Done screen renders with live header state

- Label: PROVEN.
- `renderStudyRunDone()` builds summary from the live tracker and calls `renderStudyDone()`.
- `renderStudyDone()` calls `showComplete()` then `renderCurrentStudyDone()`.
- `renderCurrentStudyDone()` calls `renderComposeStudyRoute()`; the header again reads live tracker, not the predicate snapshot.

T7 — Observed mismatch

- Label: STRONGLY SUPPORTED.
- If the live tracker state available to `renderComposeStudyRoute()` is, or was just published as, `completed=5,target=7`, the user sees `5 / 7` while already on Done.
- `clearAdvancingStudyRecovery()` can make the route inactive and clear feedback before this render, but cannot by itself manufacture `5 / 7` or satisfy hard-cap. The arithmetic mismatch requires stale/reconciled tracker state or a previous header snapshot from another route load.

### Prove/disprove `clearAdvancingStudyRecovery()`

- PROVEN not direct cause of premature hard-cap: it is called after `pendingRepairOrDoneRender()` has already returned a terminal render thunk. It does not call `setTargetCount()`, `markTaskCompleted()`, `initializeSessionPlan()`, or `atHardCap()`.
- PROVEN display-affecting: it sets `activeSession = null` and `studyAnswerFeedbackState = null` before Done is rendered, so the top bar is recomposed as an inactive terminal route.
- STRONGLY SUPPORTED incidental: it is part of the #542/#543 recovery handoff that made terminal/non-restorable routes safe after Continue, but the premature decision depends on mutable tracker/async ordering, not on clearing itself.

## E. Ranked root causes

1. Split-brain terminal predicate vs header snapshot (highest confidence)
   - For: terminal route uses mutable `atHardCap()`; header later reads mutable tracker through `topBarProgress()`. No immutable `CompletionSnapshot` flows from predicate to Done/header.
   - Against: if all route loads are serialized and no background mutation races, the same tracker should normally make `5/7` unable to pass hard-cap.

2. Async route loads mutate shared state before stale-generation render rejection (highest confidence as enabling mechanism)
   - For: `AsyncHomeRouteLoader` guards only the posted render; `computeStudyRender()` mutates tracker/session/recovery during `load`. This exactly permits a stale load to change the state used by a current render or current predicate.
   - Against: not all stale-load interleavings lead to 5/7 -> Done; needs a specific ordering around recovery/Continue or another route request.

3. Target reconciliation after restore/continued handoff (medium-high confidence)
   - For: restored active/fallback sessions set `recoveredStudyRunNeedsTargetReconciliation = true`; `initializeSessionTarget()` can alter target based on completed + selectable remaining. #549 retained Study state across recreation, increasing exposure to this path.
   - Against: terminal `clearAdvancingStudyRecovery()` currently clears the reconciliation flag; the exact real-device sequence would need logs to prove which reconciliation happened before visible 5/7.

4. Queue-empty / hard-cap shortcuts before full session plan initialization (medium confidence)
   - For: `pendingRepairOrDoneRender()` runs before `StudySessionActions.plannedStudySession()` on each route compute. The tracker may carry a prior target before the current seeded queue has reinitialized planned keys/repeats.
   - Against: due repairs and pending repeats are checked before Done; this does not explain every early Done by itself.

5. Feedback Continue timing (medium confidence)
   - For: APPLIED feedback is visible while the next route is not computed; Continue starts a background route that may use different queue/persistence state than the visible header. Auto-continue can reduce human-visible dwell time.
   - Against: Continue is only the trigger; current code still needs mutable counter divergence for `5/7` to pass hard-cap.

6. Pending repair / invalid removal (low-medium confidence)
   - For: repair skip and invalid restore paths can clear session/recovery and reroute. Repair progress uses different progress keys.
   - Against: the user observation mentions 5/7 Study progress, not a repair-specific flow, and due repairs preempt Done when present.

7. Stale review callbacks (low confidence)
   - For: review writes are async.
   - Against: `performNormalReview()` drops stale active-session tokens before persistence; persisted duplicates abandon active task and mark feedback applied, not arbitrary hard-cap.

8. `clearAdvancingStudyRecovery()` as the root arithmetic cause (disproved)
   - For: it occurs on terminal handoff and was introduced recently.
   - Against: it runs after terminal selection and cannot change counters. It can only affect post-transition display/routing state.

## F. Deterministic RED characterization recipe

Current architecture prevents a concise unit test from exercising the real Done seam because the code that decides Done is private inside `computeStudyRender()`, mutates Activity/ViewModel state during an async `load`, and returns a render thunk whose acceptance is controlled by `AsyncHomeRouteLoader` generation. The extraction seam needed is a narrow authoritative Study route reducer, not a rewrite:

```kotlin
data class StudyRouteFrame(
    val requestId: Int,
    val sessionId: String?,
    val completed: Int,
    val target: Int,
    val pendingTaskKeys: List<String>,
    val dueRepeatKeys: List<String>,
    val dueRepairKeys: List<String>,
    val decision: Decision,
)

interface StudyRouteDecider {
    fun decide(input: StudyRouteInput, prior: StudyRouteFrame): StudyRouteFrame
}
```

RED test 1: exact 7-item visible target must not Done at 5

- Fixture: tracker/session frame with `completed=5`, `target=7`, two pending task keys, no due repairs, no due repeats.
- Event: Continue from APPLIED answer invokes route decision.
- Expected RED assertion: decision is next active session or loading, not Done; rendered header frame remains `5/7` or advances to `6/7`, never Done.
- Gradle shape after seam exists:
  - `ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools ./gradlew :app:testDebugUnitTest --tests dev.bee.kanjianki.StudyRouteDecisionTest.visibleFiveOfSevenCannotRouteDone`

RED test 2: wrong/requeue grows target and blocks Done

- Fixture: seven initial tasks; answer one wrong so persisted learning repeat is due within learn-ahead; completed planned key exists for the kanji.
- Events: apply review, Continue, route decision.
- Expected: repeat key is in `dueRepeatKeys` or pending plan; `Decision.Active(repeat)`; no Done while repeat is due/ahead.
- Existing partial coverage: `StudySessionTrackerTest.scheduledLearningRepeatsGrowProgressOnePersistedStepAtATime` and `StudySessionLearnAheadTest` cover tracker pieces, not the route Done seam.

RED test 3: process restore near 5/7 retains authoritative frame

- Fixture: active recovery at completed 5, target 7, current session token restored; `recoveredStudyRunNeedsTargetReconciliation=true`.
- Events: recreate Activity, restore active route, Continue after answer.
- Expected: reconciliation cannot shrink target below visible pending work; if Done selected, Done carries the same immutable completion frame used for header.
- Existing partial coverage: `MainActivityStudyRouteInitializationTest` restores active/pending cards, but does not assert no terminal route at a partially complete target.

RED test 4: stale route load cannot mutate tracker after superseded

- Fixture: `AsyncHomeRouteLoader` with queued loads and fake executor. Load A computes terminal and mutates tracker; Load B supersedes with active 5/7.
- Event: run A load after B request but before B render.
- Expected: stale A load must not be able to mutate authoritative tracker/session, or its mutation must be scoped to a discarded frame. Current design is expected to fail this once the route mutation is isolated.
- Gradle shape:
  - `./gradlew :app:testDebugUnitTest --tests dev.bee.kanjianki.StudyAsyncRouteMutationTest.staleStudyLoadCannotChangeProgressFrame`

Current targeted commands run in this pass:

- PASS: `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home; ./gradlew :core:test --tests dev.bee.kanjianki.core.StudySessionProgressTrackerTest --tests dev.bee.kanjianki.core.StudySessionProgressTrackerConcurrencyTest --no-daemon`
- PASS: `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home; ANDROID_HOME=/opt/homebrew/share/android-commandlinetools ANDROID_SDK_ROOT=/opt/homebrew/share/android-commandlinetools ./gradlew :app:testDebugUnitTest --tests dev.bee.kanjianki.StudySessionTrackerTest --tests dev.bee.kanjianki.StudySessionLearnAheadTest --tests dev.bee.kanjianki.StudySessionViewModelTest --tests dev.bee.kanjianki.MainActivityStudyRouteInitializationTest --tests dev.bee.kanjianki.StudySessionActionsTest --no-daemon`
- FAIL/environment: same app command with `ANDROID_HOME=/tmp/android-sdk` failed because that SDK path does not exist on this host.

## G. Architecture decision

Choose narrow authoritative extraction, not state-machine rewrite.

Rationale:

- Evidence points to a missing immutable route/progress frame and a stale-load mutation seam, not to a fundamentally wrong scheduler/review model.
- Existing transactional review persistence is sound: token CAS, review-log/item transaction, stale-session drops, and rollback are already present.
- Existing tracker tests cover many core invariants; ripping out the Study state machine would risk scheduler regressions, repair regressions, and recovery data loss.
- The minimal durable boundary is to extract terminal decision + header progress into an immutable `StudyRouteFrame` produced by a pure-ish decider, and to ensure async `load` computes a frame without mutating global tracker/session until generation acceptance on main.

Migration plan:

1. Introduce `StudyRouteFrame` / `StudyRouteDecision` with progress snapshot, active session identity, pending keys, due repeats/repairs, and terminal reason.
2. Change background route compute to build a frame and prepared render assets, not mutate global `activeSession`, tracker target, or recovery state directly.
3. Apply the frame only after `AsyncHomeRouteLoader` generation acceptance.
4. Render Done/header from the accepted frame; do not recompute header progress from live tracker on terminal routes.
5. Fence/delete old paths that mutate tracker in discarded loads: `initializeSessionTarget()`, `includePendingTask()` for repairs, `initializeSessionPlan()`, and `clearAdvancingStudyRecovery()` should run only in accepted-frame application or operate on frame-local copies.
6. Add the RED tests above before production changes.

Rollback: the extraction can be introduced behind focused tests without changing scheduler algorithms. If it fails, revert the frame-application layer without touching LocalStore review persistence.

## H. Prior-work corrections

2026-07-11 graph corrections:

- Incomplete: it proved wrong-answer repeats can re-enter the session and top bar target can grow, but it did not prove the terminal Done predicate and visible header are atomic.
- Misleading if read broadly: “top bar derives from live queue/progress state” is necessary but insufficient. The live tracker can be mutated by stale async loads and then read by a different route render.
- Still valid: same-session learning repeat behavior and target growth one persisted step at a time are covered by tracker/learn-ahead tests.

t_b5be724f corrections:

- Correct core direction: non-atomic done handoff and live recomposition are implicated.
- Overstated: it implied `clearAdvancingStudyRecovery()` can explain the mismatch directly. Current code shows it happens after terminal selection and cannot change counter arithmetic.
- Missing: it did not inventory all completion predicates, counter mutation sites, or the async stale-load mutation seam.

t_5ef15691 corrections:

- Useful: PR audit is accurate enough for provenance; #542/#543/#549 are on the recovery/handoff path, #523/#526/#529/#530 provide preceding scheduler/progress/feedback state.
- Incomplete: it delivered no state diagram, event trace, ranked hypotheses, characterization seam, or architecture decision.
- Misleading if treated as sufficient: “all PRs are merged and relevant” does not identify which runtime ordering can produce 5/7 -> Done.

## PR/commit provenance

Relevant recent changes verified with `git log`, `git show --stat`, and blame:

- #523 / `168984f8`: learning repeats stay in Study sessions; touches `StudySessionTracker`.
- #526 / `7b585728`: adaptive two-core scheduler; changes review flow and tracker semantics.
- #529 / `45717741`: explicit Continue; adds feedback phase gate.
- #530 / `de6db539`: persists answered Study cards across lifecycle; adds pending recovery.
- #542/#543 / `eb307de3`, `62757d78`: route handoff after Continue and repair recovery gaps; introduces continued handoff/terminal clearing path.
- #549 / `ed5f37a5`: retains Study session state across recreation; current HEAD.

## Final answer to the root-cause question

The premature Done is caused by non-atomic Study route state: route computation mutates a shared tracker/session model before render acceptance, while Done selection and header rendering read that model at different times. `clearAdvancingStudyRecovery()` is a display/routing participant after Continue, but it does not cause the hard-cap predicate to pass. The fix should extract and apply one accepted authoritative route/progress frame, then render both Done and the header from that frame.
