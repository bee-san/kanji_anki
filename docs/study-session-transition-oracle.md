# Study session transition oracle

This spec documents the explicit transition rows that replaced the old randomized
property test. The goal is to exercise the real study-route completion seam
instead of comparing `StudySessionTracker.atHardCap()` against a copied oracle.

## Scenario matrix

| Seed / label | Visible snapshot | Production seam | Expected | Owner |
| --- | --- | --- | --- | --- |
| `0xC0FFEE`, `0xBEE`, `0x5EED` | visible 5/7 → 6/7 → 7/7 | `MainActivityStudy.renderComposeStudyRoute(...)` + reflected `MainActivityStudyQueueCoordinator.pendingRepairOrDoneRender(...)` | no Done at 5/7 or 6/7; only the 7/7 row is eligible for Done | `StudySessionTransitionModelTest` |
| `0xD00D` | stale target below completed | `recoveredStudyRunTarget(...)` + the same route seam | the persisted target is reconciled up to a coherent N/N snapshot before Done | `StudySessionTransitionModelTest` |
| `0x20260711` | 7/7 visible, but two same-session learning repeats are still due inside the learn-ahead horizon | `dueCompletedLearningRepeatTaskKeys(...)` + `pendingRepairOrDoneRender(...)` | hard-cap alone does not end the run while the repeats remain due | `StudySessionTransitionModelTest` + `StudySessionLearnAheadTest` |
| `0x20260712` | feedback apply / continue / restore | `StudyAnswerFeedbackState` | phase transitions remain explicit and late callbacks are ignored | `StudySessionTransitionModelTest` |

## Ownership boundaries

The new test file owns the completion-oracle rows above and the actual
queue/done branch. Other lifecycle seams stay where they already have focused
coverage:

- `StudySessionRecoveryStoreTest` and `MainActivityStudyRouteInitializationTest`
  own process-restore generation/version ordering, stale callbacks, and
  reset/replace races.
- `StudyAnswerSubmissionCoordinatorTest` owns delayed persistence / apply
  callback sequencing.
- `StudySessionLearnAheadTest` owns the learn-ahead repeat policy itself; this
  oracle test only verifies that the repeats still block Done when the real
  route seam is exercised.

## Why the earlier 2026-07-11 traces missed the real-device report

The old randomized model and the first 00C attempt both stayed at the tracker
level: they asserted `topBarProgress()` / `atHardCap()` behavior, but they never
hit the private `pendingRepairOrDoneRender(...)` branch or rendered the actual
`MainActivityComposeRoute` tree. That meant the exact on-device sequence the
report described — a visible 5/7, then 6/7, then 7/7 snapshot before Done — was
never asserted against the real production decision point.

The preserved seed `0x20260711` now maps to the explicit horizon-mismatch row
above, where two learning repeats remain due within the look-ahead window. That
is the concrete case the random walk missed.

## Notes

- The study-route seam is still private, so the test reaches it reflectively.
  If that branch is ever extracted into a public helper, update the test helper
  to call the extracted seam directly.
- No production behavior changed in this branch; these are characterization and
  specification artifacts only.
