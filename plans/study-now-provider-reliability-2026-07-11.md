# Reliability Release: Exact Study Counts and Green Provider Gate

## Summary

Ship the existing Study Now correctness work, repair the failing AnkiDroid
fixture, and release only after both sanitized and real-collection provider
gates pass. Use a fresh worktree from current `origin/main`; leave the existing
uncommitted documentation changes untouched.

## Implementation Changes

- Apply `201543a0` with `cherry-pick --no-commit`, then correct it before
  committing:
  - Derive the Home CTA, idle badge, sync-ready count, and session target
    through the same seeder and selector used by Study.
  - Honor focus/all-kanji mode, study-ahead, admission limits, the active cap,
    retired-item reopening, conditional rungs, and writing repairs.
- Replace session-plan initialization with reconciliation:
  - Preserve completed unique task keys so learning and relearning repeats
    never increase the target.
  - Prune removed tasks, clear stale pending work on an empty plan, preserve
    surviving order, and append genuinely new tasks.
- Make the successful-sync commit boundary absolute:
  - Once `markSyncSucceeded` completes, ready-count, reminder, widget, and
    other post-commit failures are isolated and logged.
  - A summary failure returns a successful sync with a fail-closed count
    instead of adding a contradictory failed-sync row.
  - Stats precompute uses its own short-lived `LocalStore` and safely handles
    closed stores or rejected dispatch.
- Expand the sanitized fixture from two to four notes:
  - Keep suspended `箱`.
  - Make `橋` active-but-weak with three lapses and tag it `kani_query_test`.
  - Add active weak `箸` and `端`, both read `はし`, with mature intervals,
    three lapses, valid FSRS data, and nonblank sentences.
  - Keep the positive `hasReadingKanji` assertion, pinned to `橋・箸・端`; do
    not relax it.
- Add only the focused Study Now contract documentation on the clean branch;
  do not stage unrelated edits from the current dirty worktree.

## Interfaces and Compatibility

- No exported API, wire-format, or database-schema change.
- Internal count APIs gain explicit session-focus context.
- `StudySessionTracker` plan handling changes from initialize-once to ordered
  reconciliation.
- `ManualSyncEngine.SyncResult` remains compatible.

## Test and Delivery Plan

- Add regressions for future-only reviews, admission/cap filtering,
  all-kanji mode, writing repairs, learning-repeat deduplication, empty and
  partial plan replacement, mid-sync review merging, and post-commit summary
  failure.
- Update fixture-generator and browser-query tests for four notes, proving
  `橋・箸・端` share `はし` and are weak-import eligible.
- Run fresh Python fixture tests and
  `./gradlew ciFast --no-daemon --console=plain`.
- Create a WAL-safe SQLite backup of the currently open Anki collection, then
  run the local real-collection AnkiDroid gate without lowering the 7,000-note
  threshold; require `OK (62 tests)`.
- Generate an ephemeral test keystore, run `ciRelease`, and verify the APK
  signature, package/version metadata, and SHA-256 using the SDK from
  `local.properties`.
- Push a new PR, wait for Android CI and SonarCloud on the exact head SHA,
  manually dispatch `android-instrumented.yml`, and require its sanitized
  `OK (62 tests)` result.
- Rebase if `main` advances, revalidate the tested SHA, merge, then watch main
  Android CI, automatic Android Release, SonarQube, and CodeQL through
  completion and verify the published APK/checksum assets.

## Assumptions

- Reliability release was selected because no preference was returned.
- The current dirty documentation file remains user-owned and untouched.
- The PR may be merged and allowed to trigger the automatic patch release only
  after every local and remote gate above passes.
