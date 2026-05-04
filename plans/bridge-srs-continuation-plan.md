# Bridge SRS Continuation Plan

## Goal

Finish and validate the in-progress shift from the old companion review loop to the bridge-deck SRS described in `README.md`, while keeping the server, storage layer, API payloads, browser UI, and tests aligned.

## Current State

- The worktree already contains substantial edits across `study.py`, API/service/storage code, browser assets, docs, and redesign tests.
- The new model appears to introduce:
  - a real `learning` item state,
  - a deterministic introductory packet for new/lapsed kanji,
  - stricter handwriting failure rating limits,
  - bridge-specific retirement and reactivation rules,
  - richer browser payloads for handwriting policy and bridge examples.
- `plans/` did not exist yet, so this file becomes the reference plan for the remainder of the task.
- Initial validation:
  - `.venv/bin/pytest -q tests/test_redesign_study.py` is passing.
  - Broader API/browser validation is still being checked and should drive the next edits.

## Workstreams

### 1. Reconfirm the Intended Bridge-SRS Contract

- Use the current `README.md` changes plus the touched tests as the source of truth for intended behavior.
- Verify the required review packet and review-state transitions are consistent:
  - first exposure with handwriting,
  - immediate confusable/font check,
  - short scheduled bridge follow-up,
  - then FSRS-style review scheduling.
- Confirm retirement requirements and the short-window reactivation rule for recently retired items.

### 2. Stabilize Study/Storage Semantics

- Review the `study.py` transition logic for:
  - `new -> learning -> review`,
  - learning step advancement and reset behavior,
  - duplicate review token protection,
  - writing-required vs writing-optional review selection,
  - mature-bridge retirement eligibility,
  - reactivation when support collapses.
- Confirm schema/migration coverage for new persisted fields such as:
  - `learning_step`,
  - `retired_ts`,
  - `retirement_context_json`.
- Check that storage defaults and row decoding are safe for pre-existing databases.

### 3. Align API and Service Responses

- Verify `service.py` and `api.py` expose the same contract the browser and tests expect.
- Check that session/review payloads consistently include:
  - `requiresWriting`,
  - `handwritingPolicy`,
  - prompt type,
  - scheduler phase,
  - bridge examples / related vocabulary support.
- Make sure failure paths still produce stable API errors.

### 4. Finish Browser Integration

- Validate that `webapp/app.js`, `index.html`, and `styles.css` correctly consume the new session/review payloads.
- Check these browser behaviors in particular:
  - rating restrictions after failed handwriting,
  - guide-mode display and manual override flow,
  - learning/review status presentation,
  - bridge example rendering,
  - any new assets referenced from the untracked `webapp/assets/` directory.
- If the browser tests expose regressions, fix the UI to match the server contract rather than inventing a parallel rule set.

### 5. Verify the Full Change Set

- Run the touched redesign tests from the repo venv.
- Expand to the broader redesign suite once the direct failures are resolved.
- If browser tests are slow or flaky, isolate the failing file first, fix it, then rerun the broader set.
- Keep documentation and tests updated together for any behavior adjustments uncovered during verification.

### 6. Clean Up Task Artifacts Carefully

- Leave unrelated user changes intact.
- Only remove or normalize generated artifacts if they are clearly task-local and safe to touch.
- Pay attention to `.tmp-app-home/` and `webapp/assets/` only after functional verification is complete.

## Immediate Next Steps

1. Finish the pending API/browser test run from the repo venv.
2. Inspect any failing assertions or hangs to identify the exact incomplete surface.
3. Patch the minimum set of files needed to bring server/browser behavior back into alignment.
4. Rerun targeted tests, then widen to the broader redesign suite.
5. Summarize the final state against this plan.

## Verification Target

At minimum, the final state should demonstrate:

- passing study-flow tests,
- passing API/storage contract tests for the touched bridge-SRS fields,
- passing browser tests for the new handwriting/session UI behavior,
- docs that accurately describe the live bridge-deck behavior.
