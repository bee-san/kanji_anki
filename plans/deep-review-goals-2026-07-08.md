# Deep Review Goals — 2026-07-08 pass

Source: fresh full-codebase deep review performed at commit `438a3ccf`
(branch `feat/pareto-planner-overhaul`, 2026-07-08), covering the app layer
(threading, lifecycle, data stores, sync), the core scheduler/FSRS plus the new
Pareto planner, the updater and debug-log features shipped since 2026-07-03,
the CI/release workflows after the #505 rework, and the Gradle build system.

This document continues `plans/deep-review-goals.md` (2026-07-03). Goal numbers
continue from 38 so cross-references stay unambiguous: "Goal 21" always means
the 2026-07-03 document, "Goal 39+" always means this one. Carry-over items
from the old document are indexed in Batch F rather than re-written.

Each goal is self-contained: context with file/line evidence, the change to
make, and machine-checkable acceptance criteria. Work goals one at a time with
`/goal`. Line numbers are correct as of `438a3ccf` and may drift — search the
named symbols.

Validation gates (see AGENTS.md for full detail):

- `./gradlew ciFast` — required for every goal. On machines without
  `local.properties`, prefix Android tasks with
  `ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk`.
- Python suites: `python3 -m unittest discover -s tools -p 'test_*.py'`,
  same for `ci/tests` and `scripts/tests`.
- Live AnkiDroid gate (required for provider/sync-behavior goals before any
  release): the AGENTS.md emulator run must print `OK (10 tests)`.
- Workflow goals: push and watch the first Actions run to completion —
  `gh run watch RUN_ID --repo bee-san/kanji_anki --exit-status` must exit 0.
- Golden-sensitive scheduler goals: regenerate goldens with the documented
  generator; never hand-edit.

Suggested batch order:

1. Batch A: Sync data safety (Goals 39-42) — land 39+40 together, live gate
2. Batch B: Activity lifecycle & review-write integrity (Goals 43-47)
3. Batch C: CI & build system (Goals 48-54)
4. Batch D: Updater & debug log (Goals 55-57)
5. Batch E: Planner & scheduler polish (Goals 58-61)
6. Batch F: Hygiene sweep + carry-over index (Goal 62 + table)

> IMPLEMENTATION PASS (2026-07-08, locally-verifiable subset):
> Landed with tests + `ciFast` green: Goals 41 (NUL byte + hygiene suite),
> 45 (atomic saveReviewOutcome), 46 (thread-safe session trackers), 48 (CI
> path-filter blind spots + meta-test), 51 (lazy convention coverageExcludes),
> 58 (capped all-kanji backlog honesty), 59 (Lorenz-head edge cases + honest
> scoring docs), 60 (relearning-graduation rule documented + pinned, option b),
> 61 (scheduler small-defect sweep), 62 (docs consolidation + AGENTS.md
> casing), plus a partial on 47 (the duplicate reminder-schedule). The rest
> (39, 40, 42, 43, 44, 49-50, 52-57 and carry-overs) remain gated on live
> AnkiDroid emulator runs, live GitHub Actions runs, on-device UI/TalkBack
> validation, R8 emulator smoke tests, or large golden-regenerating refactors
> — not safe to land blind in this environment. Each goal's own criteria note
> what still needs live validation.

---

## Verified sound in this pass (do not re-litigate without new evidence)

The 2026-07-03 pass and #494-#507 landed correctly in these areas; this pass
re-verified them at `438a3ccf`:

- WAL mode enabled once (`LocalStoreBase.kt:27`); backups via `VACUUM INTO`
  with checkpoint+copy fallback, gzip+fsync, tiered pruning
  (`LocalStore.kt:31-77`, `DatabaseBackupWorker.kt:54-58`).
- Historical snapshot pruning fires on every append (`HistoricalSyncStore.kt:103-120`).
- Sync run committed as `pending`, flipped to `success` only after study items
  commit; `hasSuccessfulSyncSince` filters `status=success`
  (`ManualSyncEngine.kt:116-150`).
- Mid-sync review merge re-reads persisted rows inside the write transaction;
  every applied review increments `totalReviews`, so the
  `MidSyncReviewMergePolicy` detector cannot miss a mid-sync review
  (modulo the input starvation fixed by Goal 39).
- Review idempotency anchored in persistence: UNIQUE token column +
  `CONFLICT_IGNORE` insert (`LocalStoreStudyStatus.kt:12-25`,
  `LocalStoreStudy.kt:225`); tokens consumed only after successful apply
  (`ReviewTransitionEngine.kt:29-33`).
- `AutoSyncJobService` cooperative cancellation with completion-time
  reschedule flag; concurrent syncs blocked by the process-wide `RUNNING`
  atomic (`ManualSyncEngine.kt:74-82`).
- Receivers do DB work via `goAsync()` + background pool (`ReceiverAsyncWork.kt`).
- Updater: signing-cert verification before every session commit on both
  fresh and cached paths, fails closed and clears pending state
  (`GitHubUpdater.kt:100-104, 161-165`); 200 MB APK download cap with
  mid-stream abort (`:529, 625-652`); `ApkContentProvider` removed; manifest
  export surface minimal; `usesCleartextTraffic="false"`; no auth tokens.
- fsrs-java matches pinned py-fsrs v6.3.1 via a 38-case upstream fixture
  oracle (`fsrs-java/testdata/upstream-reference-cases.json`); persisted
  stability/difficulty keep full precision (`FsrsPrecisionSoakTest`).
- Promotion cap, demotion-floor streak accumulation, learning-step semantics,
  and new-learning graduation all match AGENTS.md.
- Release path: auto-release fires only off a successful `Android CI` main-push
  run for the exact commit; auto runs serialized by the `main-auto` concurrency
  group with `cancel-in-progress: false`; tag computation fails closed;
  wrapper validation runs before any `./gradlew` in the release validate job;
  no emulator or Sonar/CodeQL polling in the release path.
- Cold-boot ANR fixes hold: theme reads are cache-only on main
  (`appThemeChoiceNonBlocking`), asset warmup is process-wide with correct
  double-checked locking (`AssetWarmupCache.kt`), maintenance runs off the
  route-load executor.

---

## Batch A: Sync data safety

> Goals 39 and 40 are two halves of the same lost-data class (DB-level input
> starvation and cache-level staleness). Land them together in one reviewed
> change with the live AnkiDroid gate before any release.

### Goal 39: Stop sync from hard-deleting study items whose kanji left the analyzer rows

> **FIXED (2026-07-13):** Sync now seeds from all durable study items while
> keeping planning scoped to active rows, so absent kanji rows pass through the
> seeder's explicit retirement path with FSRS/task memories intact. Atomic sync
> publication also retains any kanji omitted by a narrowed caller. Foreground
> Study reseeding preserves kanji rows outside its capped dashboard input rather
> than treating that UI cap as authoritative deletion/retirement evidence.
> Focused core, commit-window, full-engine Robolectric, and foreground queue
> regressions cover empty and subset inputs. `review_log` was never deleted by
> this path; its survival is now pinned as an invariant.

**Problem:** `ManualSyncEngine.runLocked`
(`app/src/main/kotlin/dev/bee/kanjianki/sync/ManualSyncEngine.kt:131`) reads
only `store.studyItemsForKanji(activeRows.map { it.kanji })`. Any persisted
study item whose kanji is *not* in the current analyzer rows — because the
weakness score decayed to zero, the Anki cards were deleted, the import
selection narrowed, or the kanji was locally suspended
(`SuspendedImportPolicy.activeRows` filters those out one line earlier) —
never reaches the seeder at all. `StudyQueueSeeder.shouldRetireSeedItem`'s
`row == null → retire` branch
(`core/src/main/kotlin/dev/bee/kanjianki/core/StudyQueueSeeder.kt:245-247`)
is therefore starved: it can only see items whose rows still exist.
`replaceStudyItems` on the sync path (`syncId != null`) then does
delete-all + reinsert (`app/src/main/kotlin/dev/bee/kanjianki/data/LocalStoreStudy.kt:69-74`),
and `MidSyncReviewMergePolicy.merge` iterates the *seeded* list only — so the
starved rows, including previously `retired` markers, are permanently deleted:
FSRS memory, rung, streaks, task memories, all gone. If the kanji later
returns it is admitted as a brand-new item.

The home-refresh path is inconsistent with this: it passes the *unfiltered*
`store::studyItems` into seeding
(`app/src/main/kotlin/dev/bee/kanjianki/MainActivityHomeFocusQueue.kt:147-181`),
so the same items get **retired** there — and the next sync then deletes the
row home just retired.

**Goal:** Make retire-vs-delete an explicit seeder decision on the full item
set:

- Feed all persisted study items (or the union of filtered + persisted-only)
  into `seedQueue` on the sync path so `shouldRetireSeedItem` sees every item.
- Ensure the seeded output (and therefore `replaceStudyItems`) carries
  persisted-only families forward as `retired` instead of dropping them.
- Reconcile the sync path and the home path so both produce the same
  retire/keep decisions for identical inputs.
- Keep the existing deliberate-resurrection semantics
  (`StudyQueueSeeder.kt:279-310`) unchanged.

**Done when (machine-checkable):**

1. New core test class (suggested
   `core/src/test/kotlin/dev/bee/kanjianki/core/SyncRetirementRegressionTest.kt`)
   passes:
   `./gradlew :core:test --tests "dev.bee.kanjianki.core.SyncRetirementRegressionTest"`
   exits 0, covering at minimum:
   - item exists for kanji X with reviews/memories; X absent from rows →
     after seeding, the item is present with `STATE_RETIRED` and its
     `TaskMemory` fields byte-identical to before;
   - an already-`retired` item whose kanji is absent from rows survives
     seeding unchanged;
   - a locally-suspended kanji's item survives as retired, not deleted.
2. New app-level test (suggested
   `app/src/test/kotlin/dev/bee/kanjianki/sync/ManualSyncEngineRetirementTest.kt`)
   passes: run a full engine sync where a previously-studied kanji is missing
   from the analyzer rows; assert `store.studyItems()` still contains that
   family with `STATE_RETIRED`.
   `./gradlew :app:testDebugUnitTest --tests "dev.bee.kanjianki.sync.ManualSyncEngineRetirementTest"`
   exits 0.
3. Path-consistency test passes: for identical rows/items/settings inputs, the
   sync-path seed and the home-path seed
   (`HomeStudyQueueActions.studyQueue`) produce equal retire/keep decision
   sets (compare kanji→state maps).
4. `./gradlew ciFast` exits 0.
5. Before any release containing this change: live AnkiDroid gate prints
   `OK (10 tests)` per the AGENTS.md instrumentation command.

### Goal 40: Close the cross-instance cache-coherence lost-update (auto-sync vs foreground activity)

**Problem:** `AutoSyncJobService` syncs through its own private store —
`val store = LocalStore(context)`
(`app/src/main/kotlin/dev/bee/kanjianki/sync/AutoSyncJobService.kt:112`).
All cache invalidation in the sync commit path
(`app/src/main/kotlin/dev/bee/kanjianki/data/LocalStoreSync.kt:159-161`)
clears only *that instance's* caches. The foreground activity's store keeps
its `@Volatile` per-instance snapshots
(`app/src/main/kotlin/dev/bee/kanjianki/data/LocalStoreInventory.kt:20-40`:
`cachedDashboardRows`, `cachedStudyItems`, `cachedStudyItemsByKanji`, …) with
no cross-connection freshness check. `ManualSyncEngine.kt:147`'s own comment
states auto-sync can run while the app is foregrounded and studyable.

Consequences after a foreground-overlapping auto-sync:

- (a) Home/study render pre-sync data until the activity is recreated.
- (b) Worse — silent data deletion: the next study-route load reseeds from the
  stale cached rows/items (`HomeStudyQueueActions.studyQueue`,
  `app/src/main/kotlin/dev/bee/kanjianki/HomeStudyQueueActions.kt:15-41`),
  detects a difference, and calls `replaceStudyItems(annotated)`;
  `applyStudyItemsDiff` (`LocalStoreStudy.kt:93-115`) deletes persisted rows
  absent from the stale-derived set — erasing study items the auto-sync just
  imported while its sync_run row says success. The #498 DB-level mid-sync
  merge closed the write/write race; this is the cache/write race it did not
  cover.

**Goal:** Give the store family a cross-connection freshness check. Options,
cleanest first:

- Check SQLite `PRAGMA data_version` (cheap; changes whenever *another*
  connection commits) on each cached read in `LocalStoreInventory`; clear all
  caches when it moved.
- Or a process-wide write epoch (`AtomicLong` in a `LocalStoreBase`
  companion) bumped by every write transaction in every instance; cached
  reads validate their captured epoch.
- Or share one process-wide `LocalStore` (largest change; aligns with Goal 43
  — acceptable to do there instead, but the epoch check is still cheap
  insurance for the job-service store).

Whichever mechanism: the study-route persist path must never destructively
diff against stale inputs — either the caches are provably fresh at read
time, or `HomeStudyQueueActions.studyQueue` passes a baseline and merges like
the sync path does.

**Done when (machine-checkable):**

1. New app test (suggested
   `app/src/test/kotlin/dev/bee/kanjianki/data/LocalStoreCrossInstanceCoherenceTest.kt`)
   passes: open two `LocalStore` instances on one DB file; warm instance A's
   caches (`studyItems()`, `activeDashboardRows()`); commit a study-item +
   sync write through instance B; assert instance A's next `studyItems()` /
   `activeDashboardRows()` reflect B's committed data.
   `./gradlew :app:testDebugUnitTest --tests "dev.bee.kanjianki.data.LocalStoreCrossInstanceCoherenceTest"`
   exits 0.
2. New end-to-end regression test passes: warm activity-store caches; run a
   sync through a second store instance that imports a new kanji K; then run
   `HomeStudyQueueActions.studyQueue(persist = true)` against the first
   store; assert K's study item still exists in the DB afterwards (the
   stale-cache reseed did not delete it).
3. Existing cache unit tests still pass (`./gradlew :app:testDebugUnitTest`
   exits 0).
4. `./gradlew ciFast` exits 0.
5. Live AnkiDroid gate prints `OK (10 tests)` before any release (sync-adjacent).

### Goal 41: Replace the literal NUL byte in MidSyncReviewMergePolicy.kt and add a control-byte hygiene test

**Problem:**
`core/src/main/kotlin/dev/bee/kanjianki/core/MidSyncReviewMergePolicy.kt`
contains a literal U+0000 byte inside the `key()` string concatenation
(`item.kanji + "<NUL>" + item.answerSignature`, byte offset ~3427). `file`
classifies the source file as `data`; `git diff` renders "Binary files
differ"; `grep`/`rg` and file-reading tools skip it. A correctness-critical
merge policy is invisible to every text tool including code review — this
review's own automated searches missed it until a byte-level scan.
`StudyQueueSeeder.familyKey` (`StudyQueueSeeder.kt:531`) already does the
same thing correctly with the `"\u0000"` escape.

**Goal:** Replace the raw byte with the `"\u0000"` escape (behavior
identical), and add a repo hygiene test so no tracked Kotlin/Gradle source can
regress to containing raw control bytes.

**Done when (machine-checkable):**

1. Byte check exits 0:
   `python3 -c "import pathlib,sys;sys.exit(1 if b'\x00' in pathlib.Path('core/src/main/kotlin/dev/bee/kanjianki/core/MidSyncReviewMergePolicy.kt').read_bytes() else 0)"`
2. `file core/src/main/kotlin/dev/bee/kanjianki/core/MidSyncReviewMergePolicy.kt`
   output contains `text` (not `data`).
3. New hygiene suite `tools/test_source_hygiene.py` exists and passes,
   asserting no file matched by `git ls-files -- '*.kt' '*.kts'` contains
   bytes in `0x00-0x08, 0x0B, 0x0C, 0x0E-0x1F`:
   `python3 -m unittest discover -s tools -p 'test_*.py'` exits 0. (It runs in
   CI automatically via the `asset-tests` job and locally via `testCiScripts`.)
4. `git diff HEAD~1 -- core/src/main/kotlin/dev/bee/kanjianki/core/MidSyncReviewMergePolicy.kt`
   renders a textual diff: `git diff --numstat HEAD~1 -- <file>` does not
   print the binary marker `-<TAB>-`.
5. `./gradlew :core:test` exits 0 (merge behavior unchanged).

### Goal 42: Make answerSignature stable against remote suspension churn (and dedupe legacy alignment)

**Problem:** `answerSignature` prefers the first **suspended** example when
choosing its source (`core/src/main/kotlin/dev/bee/kanjianki/core/StudyQueueSeeder.kt:507-528`).
When a *different* card in the kanji's family gets suspended or unsuspended in
Anki — a routine desktop action — the selected signature source can change
even though the studied content did not. `alignAnswerSignature`
(`StudyQueueSeeder.kt:361-405`) treats any signature change as "the answer
changed" and rebuilds the item: state→learning, stability 0.4,
`totalReviews` 0, all task memories wiped, rung demoted one step. Months of
ladder/FSRS state can be destroyed by a suspension flip.

Related, pre-existing: during legacy signature alignment
(`StudyQueueSeeder.kt:196-205`) two existing items with the same kanji but
different stale signatures both align to the current signature —
`state.byFamily` keeps only the last but `state.items` keeps both, so
duplicate family rows get persisted (they diverge over time and inflate
`replaceStudyItems` diffs).

**Goal:**

- Derive the signature from stable answer content, independent of
  suspension-ordered example selection — or align without reset when the
  actual studied content (meaning/reading fields) is unchanged even though
  the signature source moved.
- A genuine content edit must still reset (current deliberate behavior).
- Deduplicate families during alignment so at most one row per
  (kanji, current signature) survives.
- Consider migration for already-persisted signatures so the fix does not
  itself trigger a mass reset (that would be the exact bug being fixed).

**Done when (machine-checkable):**

1. Core tests pass (extend `StudyQueueSeederTest` or add
   `AnswerSignatureStabilityTest`):
   - family with examples A (unsuspended) + B; remotely suspend B; re-seed →
     item's state, `totalReviews`, task memories, and rung are unchanged;
   - the reverse unsuspend flip likewise changes nothing;
   - a real content edit (changed meaning field) still resets exactly as
     today (existing reset tests keep passing);
   - two legacy items with different stale signatures aligning to one current
     signature → seeded output contains exactly one row for that family.
   `./gradlew :core:test --tests "dev.bee.kanjianki.core.*Signature*"` exits 0.
2. The fix does not reset existing users: a migration/round-trip test seeds
   with old-scheme signatures, re-seeds with the new scheme, and asserts zero
   items entered `learning` state.
3. `./gradlew ciFast` exits 0.
4. Live AnkiDroid gate prints `OK (10 tests)` before any release.

---

## Batch B: Activity lifecycle & review-write integrity

> Goal 43 is the structural fix (old Goal 8 done properly). Goals 44-47 are
> independently landable hardening; if Goal 43 lands first, re-scope 44
> against the retained holder.

### Goal 43: Retain the runtime (store, executors, session) across configuration changes

**Problem:** Old Goal 8, now with concrete crash evidence (Goal 44). Every
runtime object is a plain per-activity field: `io = Executors.newSingleThreadExecutor()`
(`app/src/main/kotlin/dev/bee/kanjianki/MainActivityBase.kt:59`), `maintenance`
(`:70`), `lateinit var store/gateway` (`:80-82`), `activeSession` (`:97`).
`MainActivityLifecycle.onDestroy` (`MainActivityLifecycle.kt:40-49`) calls
`io.shutdownNow()`, `maintenance.shutdownNow()`, `store.close()`.
`MainActivityStartup.handleLaunchIntent` (`MainActivityStartup.kt:71-96`)
always renders Home. The manifest declares no `configChanges`, so every
rotation/dark-mode/locale change destroys the activity: mid-study ink,
session position, and route are lost; in-flight writes are killed.
`KaniApplication` (`KaniApplication.kt:6`) already exists but only configures
WorkManager and debug logs — a natural home for a process-scoped holder.
`AssetWarmupCache` proves the process-scoped pattern works here.

**Goal:** Move `store`, `gateway`, the `io` + `maintenance` executors, and
session state (`activeSession`, `StudySessionTracker`, current route) into a
process-scoped holder (application-scoped singleton to match the repo's
no-ViewModel style, or a retained ViewModel — either, but it must survive
recreation). The activity binds on create and unbinds on destroy without
shutting anything down; executor/store lifetime is the process. Restore the
current route and study-session position after recreation instead of forcing
Home.

**Done when (machine-checkable):**

1. New instrumented test (suggested
   `app/src/androidTest/kotlin/dev/bee/kanjianki/RecreationSurvivalInstrumentedTest.kt`)
   passes locally and compiles in CI: start a study card, call
   `activity.recreate()`, assert the study route and session position
   survive. Local run:
   `adb shell am instrument -w -e class dev.bee.kanjianki.RecreationSurvivalInstrumentedTest dev.bee.kanjianki.test/androidx.test.runner.AndroidJUnitRunner`
   output contains `OK`.
2. Robolectric unit test passes: drive
   `ActivityController.recreate()`; assert the holder returns the *same*
   executor and store instances (identity equality) across recreation, and
   that no `ExecutorService.isShutdown` becomes true.
3. Static check: `rg -n "shutdownNow" app/src/main/kotlin/dev/bee/kanjianki/MainActivityLifecycle.kt`
   exits 1 (no matches).
4. Static check: `rg -n "store.close\(\)" app/src/main/kotlin/dev/bee/kanjianki/MainActivityLifecycle.kt`
   exits 1 (store lifetime is no longer the activity's).
5. `./gradlew :app:compileDebugAndroidTestJavaWithJavac` and
   `./gradlew ciFast` exit 0.

### Goal 44: Eliminate destroy-race crashes on the review-write and route-load paths

**Problem:** Three concrete crash/corruption paths introduced or exposed by
#502/#506 (all reachable by rotating during normal use, since Goal 43 is not
landed yet):

- Nested submit to a dead executor: the #502 review-write task runs on `io`
  and finishes by re-rendering the study route
  (`app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyReviewFlow.kt:192`,
  also `:86, :108, :214` →
  `MainActivityStudyQueueCoordinator.kt:14` → `AsyncHomeRouteLoader.kt:82`),
  which calls `execute` on the *same* `io` executor. If `onDestroy` ran
  meanwhile, `shutdownNow()` has been called; the running task survives
  (SQLite ignores interrupts) and the nested `execute` throws
  `RejectedExecutionException` — uncaught on the io thread → process death.
- `store.close()` races running tasks: `MainActivityLifecycle.kt:40-49` has
  no `awaitTermination` between `shutdownNow()` and `store.close()`; a
  mid-transaction review write or maintenance read gets the DB closed under
  it → background `IllegalStateException` crash and a silently rolled-back
  review.
- Loading-guard fires after destroy: `AsyncHomeRouteLoader`'s 120 ms guard
  runs on a **process-wide** scheduler
  (`app/src/main/kotlin/dev/bee/kanjianki/AsyncHomeRouteLoader.kt:115`) that
  outlives the activity; it posts via the *unguarded*
  `postToMain = { task -> main.post(task) }` wiring
  (`MainActivityHome.kt:36`). `onDestroy` never calls
  `cancelPendingHomeRouteLoads()` and never bumps the loader generation, so
  a queued load discarded by `shutdownNow()` leaves `finished == false` and
  the guard calls `showLoading()` → `setContent` on a destroyed activity.

**Goal:** (Subsumed by Goal 43 if landed together; otherwise:) ordered
shutdown — `shutdown()` not `shutdownNow()`, bounded `awaitTermination`, then
`store.close()`; make post-write re-render tolerate a shut-down executor
(check state or catch `RejectedExecutionException` and abort quietly); cancel
pending loads and bump the loader generation in `onDestroy`; route the
loading-guard's UI posts through the existing `postToMainIfActive` guard.

**Done when (machine-checkable):**

1. Unit test passes: run the review-write completion path against an executor
   stub that rejects new tasks; assert no exception escapes and no render is
   attempted.
2. Robolectric test passes: destroy the activity with a route load pending,
   advance the loading-guard scheduler past 120 ms; assert `showLoading`/
   `setContent` was not invoked after destroy (probe/spy hook) and the
   generation no longer matches.
3. Robolectric test passes: submit a review write, immediately destroy the
   activity; assert the review row is committed (ordered shutdown waited) and
   no exception was thrown.
4. Static check: `rg -n "postToMain = \{ task -> main.post\(task\) \}" app/src/main/kotlin`
   exits 1 (loader posts are guarded).
5. `./gradlew ciFast` exits 0.

### Goal 45: Persist the review item and the review log in one transaction

**Problem:** `StudyReviewActions`
(`app/src/main/kotlin/dev/bee/kanjianki/StudyReviewActions.kt:17-18`) calls
`writer.saveStudyItem(...)` then `writer.saveReview(...)` — two separate
transactions (`LocalStoreStudy.kt:144-150` and `:156-170`). Process death
between the commits leaves scheduling advanced with **no** `review_log` row:
streaks, stats, undo, and the timeline lose the review, and the idempotency
rationale at `MainActivityStudyReviewFlow.kt:157-162` ("a review that failed
mid-apply left no review_log row, so its token is retryable") is wrong for
this window — retrying the token would re-apply FSRS to the
already-advanced item (double advance).

**Goal:** Add a combined `LocalStore` write (suggested
`saveReviewOutcome(item, review, …)`) that performs both writes inside one
`transaction { }`; route `StudyReviewActions` through it; update the
idempotency comment to describe the now-true invariant.

**Done when (machine-checkable):**

1. New app test passes: inject a failure between the item write and the
   review insert (fault-injection seam or a constraint violation on the
   review row); assert the study item was **not** advanced (full rollback,
   both-or-nothing).
   `./gradlew :app:testDebugUnitTest --tests "dev.bee.kanjianki.data.*ReviewOutcome*"`
   exits 0.
2. Static check: `rg -n "saveStudyItem" app/src/main/kotlin/dev/bee/kanjianki/StudyReviewActions.kt`
   exits 1 (the two-call pattern is gone from the review path).
3. Existing idempotency tests still pass; the stale comment sentence is gone:
   `rg -n "left no review_log row" app/src/main/kotlin` exits 1 or the
   comment text matches the new invariant.
4. `./gradlew ciFast` exits 0.

### Goal 46: Make the study session trackers thread-safe (the badge made them load-bearing)

**Problem:** `StudySessionTracker`
(`app/src/main/kotlin/dev/bee/kanjianki/StudySessionTracker.kt:13-16,152-222`)
and `StudySessionProgressTracker`
(`core/src/main/kotlin/dev/bee/kanjianki/core/StudySessionProgressTracker.kt:7-12`)
are plain mutable state written on the io thread
(`completeActiveTask`/`recordReviewOutcome`/`includePendingTask`/`registerTaskShown`
— `MainActivityStudyReviewFlow.kt:183-189`,
`MainActivityStudyQueueCoordinator.kt:163-199`) and read/mutated on main
(#507 badge `MainActivityShellHost.kt:93-100`; `abandonActiveStudyTask` +
`studyUndoState.clear()` in `prepareRoute` `:114-117`; `pause`/`resume`
`MainActivityLifecycle.kt:18-27`). No locks, no volatiles. Failure modes:
torn/stale badge counts; concurrent `pause` (main) vs `completeActiveTask`
(io) corrupting `activeElapsedMillis` persisted into `study_task_log`;
`completedTaskBreakdown` iterating `completedTaskKeys` on main
(`MainActivityStudyDoneActions.kt:67-69`) can throw
`ConcurrentModificationException` if a straggler io write lands mid-iteration.
Related: `StudyUndoState.pending` is Compose snapshot state written from io
(`StudyUndoState.kt:15-28`; writes at `MainActivityStudyReviewFlow.kt:250,
203, 208`) — legal but the io-side compare-and-clear can act on a stale
snapshot read.

**Goal:** Make both trackers thread-safe: either synchronize all mutating and
reading methods (they are tiny; contention is nil) or confine mutation to one
thread and export immutable snapshots for main-thread reads. Decide and
document `StudyUndoState`'s threading (move writes to main via the guarded
post, or keep snapshot semantics with a comment explaining the tolerated
staleness).

**Done when (machine-checkable):**

1. New race test passes deterministically: N=1000 iterations of
   io-thread `completeActiveTask`/`recordReviewOutcome` racing main-thread
   `pause`/`resume`/`completedTaskBreakdown` reads; asserts no exception and
   the invariant `completed ≤ shown ≤ total` holds at every read.
   `./gradlew :app:testDebugUnitTest --tests "dev.bee.kanjianki.StudySessionTrackerConcurrencyTest"`
   exits 0.
2. Core tracker gets the same treatment/test in
   `./gradlew :core:test --tests "dev.bee.kanjianki.core.StudySessionProgressTrackerConcurrencyTest"`.
3. Existing badge tests (`#507` suite) still pass under `ciFast`.
4. `./gradlew ciFast` exits 0.

### Goal 47: Sweep the remaining unguarded posts, swallowed interrupts, and duplicated side effects

**Problem (batch of small, individually-testable defects):**

- `NoteTypeFieldMappings` posts an `AlertDialog` via plain `main.post` after a
  slow cross-process AnkiDroid read
  (`app/src/main/kotlin/dev/bee/kanjianki/NoteTypeFieldMappings.kt:28,47`) —
  `BadTokenException` if the user left/rotated meanwhile.
- `MainActivitySettings.runSettingsWrite` posts `onComplete` (typically a
  `setContent` re-render) unguarded (`MainActivitySettings.kt:283`);
  `MainActivityHomeBrowseDetail` posts Toast + `renderDetail` unguarded
  (`MainActivityHomeBrowseDetail.kt:204-207`). `postToMainIfActive` exists
  for exactly this (`MainActivityBase.kt:49`).
- `MainActivityStartup.start`'s maintenance block swallows the shutdown
  interrupt — `runCatching { migrationReady.await(...) }`
  (`MainActivityStartup.kt:62`) converts interruption into a normal return
  and proceeds to schedule four subsystems against a destroyed activity.
- ML Kit callbacks guard only `isActiveToken`, never destruction, and
  `activeSession` is not cleared on destroy
  (`MainActivityStudyWritingCheck.kt:40-47,70-89`,
  `MainActivityStudyWritingStatus.kt:28-38,64-83`) — zombie UI mutation after
  `recognizer.close()`.
- A generation-cancelled study compute still mutates session state and
  persists an `active_token` (`MainActivityStudyQueueCoordinator.kt:39-93`)
  — the generation check guards only the render, not the side effects.
- Every answered review schedules reminders **twice**, each opening a
  throwaway `LocalStore` reading the full dashboard
  (`MainActivityStudyReviewFlow.kt:188` and the tail of `saveAppliedReview`
  at `:255`; `ReminderScheduler.kt:46-53`).

**Goal:** Fix each: guard the three post sites with `postToMainIfActive` (or
lifecycle checks), rethrow/abort on interrupt in the startup maintenance
block, add a destruction check to the ML Kit callback guard and clear
`activeSession` on destroy, check the generation before side effects in the
study compute, and schedule reminders once per answer.

**Done when (machine-checkable):**

1. Robolectric test: destroyed activity + completed provider read →
   `NoteTypeFieldMappings` shows nothing and does not throw.
2. Static checks exit 1 (patterns gone):
   `rg -n "main\.post\(" app/src/main/kotlin/dev/bee/kanjianki/NoteTypeFieldMappings.kt app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettings.kt app/src/main/kotlin/dev/bee/kanjianki/MainActivityHomeBrowseDetail.kt`
3. Unit test: interrupting the maintenance startup latch aborts the block —
   assert the four schedulers were not invoked.
4. Unit test: a compute whose generation was superseded performs zero
   `saveStudyItem` calls and zero tracker mutations (spy store/tracker).
5. Unit test: exactly one `ReminderScheduler.schedule` per answered review
   (counting fake), and at most one `LocalStore` open per schedule call.
6. `./gradlew ciFast` exits 0.

---

## Batch C: CI & build system

### Goal 48: Close the CI path-filter blind spots (build-logic, ci/tests, .github/scripts)

**Problem:** `.github/workflows/android-ci.yml:8-53` (both `push` and
`pull_request` lists) omits `build-logic/**`, `ci/tests/**`, and
`.github/scripts/**`. `sonarqube.yml` and `codeql.yml` path filters omit
`build-logic/**` too. `build-logic` defines the convention plugin providing
toolchains, JaCoCo wiring, and the 100%-class-coverage gate for all seven JVM
modules — a change touching only it triggers **zero** CI: a PR merges
untested, and a main push produces no `workflow_run`, so no auto-release
fires for that commit and the untested build logic ships silently inside the
next unrelated release. `ci/tests/**` contains the release-fixture unit tests
that the `asset-tests` job runs — test-only regressions land unexecuted.
`tools/test_release_workflows.py` has no assertions about filter coverage, so
nothing locks this.

**Goal:** Add the missing paths to all filter lists (push + PR symmetric),
and add a meta-test locking filter coverage so future source roots cannot
silently drop out of CI.

**Done when (machine-checkable):**

1. `grep -c 'build-logic/\*\*' .github/workflows/android-ci.yml` prints `2`
   (push + PR); `grep -c 'ci/tests/\*\*' .github/workflows/android-ci.yml`
   prints `2`; `grep -c '.github/scripts/\*\*' .github/workflows/android-ci.yml`
   prints `2`; `grep -c 'build-logic/\*\*' .github/workflows/sonarqube.yml`
   and `.github/workflows/codeql.yml` each print ≥ 1.
2. New meta-test in `tools/test_release_workflows.py` (or a sibling module)
   passes, asserting: (a) the push and PR path lists in `android-ci.yml` are
   identical sets; (b) every top-level directory in the repo containing
   `*.gradle.kts`, `*.kt`, or CI-consumed `*.py` files (currently: the 8
   modules, `build-logic`, `ci`, `scripts`, `tools`, `.github`) is covered by
   at least one filter entry.
   `python3 -m unittest discover -s tools -p 'test_*.py'` exits 0.
3. Live validation: a branch commit touching only
   `build-logic/src/main/kotlin/` triggers Android CI —
   `gh run list --repo bee-san/kanji_anki --workflow android-ci.yml --branch <branch> --limit 1`
   returns a run for that head SHA, and
   `gh run watch RUN_ID --repo bee-san/kanji_anki --exit-status` exits 0.

### Goal 49: Enforce release signing inside the variant and settle the R8 question (old Goal 21, now concrete)

**Problem:** The signing guard string-matches
`gradle.startParameter.taskNames` for `assembleRelease`/`bundleRelease`
(`app/build.gradle.kts:43-50`). It does not fire for `./gradlew ciRelease` —
the repo's own documented release gate, which `dependsOn(":app:assembleRelease")`
(root `build.gradle.kts:180-187`) — nor for `./gradlew build` or abbreviated
invocations, all of which silently produce an **unsigned** release APK.
Reading `startParameter` at configuration time is also the repo's one
configuration-cache red flag. Separately `isMinifyEnabled = false` while
`proguardFiles(...optimize...)` is still declared against a 1-line stub
(`app/build.gradle.kts:102-108`) — dead config implying protection that does
not exist; for a self-updating app, APK size costs every user on every
update.

**Goal:** Two sub-changes:

- (a) Enforce signing inside the release variant itself — fail the
  package/validate task (via `onVariants` or a `doFirst` on
  `packageRelease`) whenever the release variant has no signing config,
  regardless of how the task was requested. Delete the task-name string
  match.
- (b) Decide R8: either enable `isMinifyEnabled = true` +
  `isShrinkResources = true` with a keep-rule audit (ML Kit digital ink,
  Compose) validated through `ciRelease` + an emulator smoke test, **or**
  explicitly document non-minified release as deliberate and delete the dead
  `proguardFiles` lines. (a) is safe to land alone; (b) requires on-device
  validation.

**Done when (machine-checkable):**

1. Without signing properties, the release build fails loudly:
   `./gradlew :app:assembleRelease` exits **non-zero** and its output matches
   `grep -q "signing"`; same for `./gradlew ciRelease` without properties.
2. With the temp keystore (AGENTS.md `ciRelease` invocation), the build exits
   0 and `apksigner verify --verbose app/build/outputs/apk/release/app-release.apk`
   exits 0.
3. `rg -n "startParameter" app/build.gradle.kts` exits 1.
4. If R8 enabled: `test -f app/build/outputs/mapping/release/mapping.txt`
   exits 0 after `ciRelease`, and the live AnkiDroid gate passes against the
   minified build. If R8 declined:
   `rg -n "proguardFiles" app/build.gradle.kts` exits 1 and
   `rg -n "deliberately unminified" app/build.gradle.kts` exits 0 (decision
   comment present).
5. `./gradlew ciFast` exits 0.

### Goal 50: Fix release-version drift and the versionCode overflow

**Problem:** Two related version-integrity defects:

- Stale fallback: `gradle/libs.versions.toml:9-10` pins
  `appVersionName = "0.4.33"` / `appVersionCode = "4033"` while the latest
  tag is `v0.4.158`. The auto-release computes versions from tags and never
  writes back, so every build not injecting `KANI_VERSION_*` (local
  `ciRelease`, IDE installs, the Sonar fallback at root
  `build.gradle.kts:11-16`) is stamped 125 patches stale — installing one
  **downgrades** versionCode below the fleet and breaks the in-app updater's
  downgrade protection assumptions.
- Overflow: `android-release.yml:113` computes
  `versionCode = major*1000000 + minor*1000 + patch`. The auto-release
  increments patch unboundedly on every main push (already 158); at patch
  1000, `v0.4.1000` → 5000 — colliding with `v0.5.0`, and `v0.4.1001` makes
  upgrading to `v0.5.0` impossible on-device. Nothing validates
  `patch < 1000`.

**Goal:**

- Derive the fallback version from the latest reachable `v*` git tag at
  configuration time (catalog literal only as last resort when git is
  unavailable), or add a release-workflow step that commits the catalog bump
  back to main after publishing.
- Extract the tag→versionCode computation into a testable script under
  `ci/scripts/` (called by the workflow), add a hard `patch < 1000` guard
  that fails the release run with an actionable message ("bump minor"), and
  unit-test the function including the guard and cross-boundary monotonicity.
- Extend `tools/test_release_workflows.py` to assert the workflow calls the
  script (no inline duplicate formula).

**Done when (machine-checkable):**

1. Version-fallback check: on a checkout with tags fetched,
   `./gradlew -q :app:printKaniVersion` (new tiny task) prints a versionName
   equal to `git describe --tags --abbrev=0 | sed 's/^v//'` (script-diff
   check exits 0) and a versionCode ≥ the code computed from that tag.
2. New Python unit tests pass for the extracted computation:
   `python3 -m unittest discover -s ci/tests -p 'test_*.py'` exits 0,
   covering: normal computation, `patch == 999` ok, `patch == 1000` fails
   with the guard message, and `code(v0.4.999) < code(v0.5.0)`.
3. Meta-test passes asserting `android-release.yml` invokes the script and
   contains no inline `* 1000000` arithmetic:
   `python3 -m unittest discover -s tools -p 'test_*.py'` exits 0.
4. First auto-release after merge is green:
   `gh run watch RUN_ID --repo bee-san/kanji_anki --exit-status` exits 0, and
   `aapt dump badging` on the published APK shows the expected
   versionName/versionCode pair.
5. `./gradlew ciFast` exits 0.

### Goal 51: Make the convention plugin's coverageExcludes actually work (it is dead config)

**Problem:**
`build-logic/src/main/kotlin/kani.kotlin-library-conventions.gradle.kts:37-44`
reads `conventionExtension.coverageExcludes.orNull` **eagerly** inside the
`fileTree(dir) { ... }` configuration action at plugin-apply time — before
any module's build script runs. `writing-core/build.gradle.kts:5-8` adds
`"**/*WhenMappings*"` afterwards; the already-configured trees never see it.
The exclusion has been a silent no-op since the #498 extraction; the
100%-class gate stays green only because current `WhenMappings` synthetics
happen to be executed by tests. The next uncovered `when`-mapping class fails
`check` with no working escape hatch.

**Goal:** Wire the excludes lazily — e.g.
`classDirectories.setFrom(project.provider { fileTree(...) { exclude(ext.coverageExcludes.get()) } })`
or resolve inside `doFirst` — so property values added anywhere in the module
script take effect. Add a functional test so the mechanism cannot silently
regress again.

**Done when (machine-checkable):**

1. New build-logic functional test (Gradle TestKit, suggested
   `build-logic/src/test/kotlin/CoverageExcludesFunctionalTest.kt`) passes:
   a synthetic module applying the convention, containing one deliberately
   uncovered class matching a configured exclude, passes
   `jacocoTestCoverageVerification`; the same module **without** the exclude
   fails it. `./gradlew :build-logic:test` exits 0.
2. Effective-excludes probe: `./gradlew -q :writing-core:printCoverageExcludes`
   (new debug task) prints `**/*WhenMappings*`.
3. `./gradlew :writing-core:check` and `./gradlew ciFast` exit 0.

### Goal 52: Unbreak Renovate — automate dependency-verification metadata regeneration

**Problem:** `gradle/verification-metadata.xml` is enforced
(`verify-metadata=true`), and Renovate bumps `gradle/libs.versions.toml` —
but nothing regenerates the metadata, so **every** Renovate dependency PR
fails all Gradle jobs until a human runs `--write-verification-metadata`
locally. `docs/dependency-updates.md` does not mention the requirement. This
trains maintainers to regenerate hashes mechanically (weakening the control)
and contradicts the documented "dependency PRs must pass CI" flow.

**Goal:** Add a workflow that, for `renovate/**` branches (or on demand),
runs the documented regeneration command against the CI-resolved task set and
pushes the updated `verification-metadata.xml` back to the PR branch; document
the exact manual command in `docs/dependency-updates.md` as the fallback.

**Done when (machine-checkable):**

1. Workflow file exists and is locked by a meta-test asserting: trigger
   covers `renovate/**` branches, the regeneration step contains
   `--write-verification-metadata sha256`, and the push step is gated to
   same-repo branches. `python3 -m unittest discover -s tools -p 'test_*.py'`
   exits 0.
2. `grep -q -- '--write-verification-metadata sha256' docs/dependency-updates.md`
   exits 0.
3. Live validation on the next Renovate PR:
   `gh pr checks <PR-number> --repo bee-san/kanji_anki --watch` exits 0 with
   no human having run the regeneration locally (verify the metadata commit
   author is the workflow).

### Goal 53: Workflow hygiene batch (cache keys, fork guards, timeouts, ordering, meta-test gaps)

**Problem (each small; batch into one or two workflow PRs):**

- Robolectric android-all cache key is static
  (`robolectric-android-all-${{ runner.os }}-v1`,
  `android-ci.yml:128-133`) — after the first save it never refreshes, so a
  Robolectric bump re-downloads jars on every run forever while the cache
  reports hits.
- `sonarqube.yml` runs on fork PRs without `SONAR_TOKEN` → guaranteed red
  advisory check (`sonarqube.yml:4-24, 198-207`); meanwhile `codeql.yml:35`
  carries a dead fork-PR guard with no `pull_request` trigger.
- CodeQL's forced compile lists only NO-SOURCE `compileJava` tasks
  (`codeql.yml:68-81`; every module is pure Kotlin) — extraction works only
  via implicit task dependencies; one AGP wiring change away from "didn't
  build any of it".
- `metadata` and `publish-release` jobs have no `timeout-minutes`
  (`android-release.yml:37-50, 241-249`); a hung `gh` call pins the
  `main-auto` concurrency group for the 6-hour default and blocks every
  subsequent auto-release.
- Manual-path keystore is decoded **before** the inline test step runs
  (`android-release.yml:163-190`) — a compromised test on a manually
  released branch can read the keystore file; reordering is free.
- `tools/test_release_workflows.py` gaps: does not assert the `concurrency`
  group / `cancel-in-progress: false` (deleting it reintroduces the
  same-tag race undetected); most assertions are raw substring checks that
  do not strip YAML comments; its docstring claims local `ciFast` runs
  `scripts/tests` (it does not — root `build.gradle.kts:119-123` runs only
  `tools` and `ci/tests`).
- The "Install Android SDK packages" step is copy-pasted 7×; the pinned
  AnkiDroid 2.24.0 APK is re-downloaded from GitHub every fixture run
  (`android-instrumented.yml:49-70`); `gradle-wrapper.properties` lacks
  `distributionSha256Sum`; `sonarqube.yml:174` hardcodes
  `gradle-version: "9.4.1"` which drifts from the wrapper on Renovate bumps.

**Goal:** Fix each: version-keyed Robolectric cache (hash of
`gradle/libs.versions.toml`); same-repo guard on Sonar, delete CodeQL's dead
guard; add explicit `compileKotlin`/`:app:compileDebugKotlin` targets to the
CodeQL compile (keeping `clean --no-daemon --no-build-cache` per AGENTS.md);
`timeout-minutes` on every release job; decode keystore after tests;
strengthen the meta-tests (concurrency assertion, comment-stripped matching,
corrected docstring); extract a composite SDK-install action; cache the
AnkiDroid APK; pin the wrapper checksum; derive/assert the Sonar
gradle-version.

**Done when (machine-checkable):**

1. `grep -q "hashFiles('gradle/libs.versions.toml')" .github/workflows/android-ci.yml`
   exits 0 (Robolectric cache key), and the static `-v1` literal is gone
   (`rg -n "robolectric-android-all-.*-v1" .github/workflows` exits 1).
2. Meta-tests pass asserting: Sonar has a same-repo fork guard; CodeQL has no
   dead fork condition; every job in `android-release.yml` declares
   `timeout-minutes`; the keystore-decode step index is greater than the
   test step index on the manual path (comment-stripped parse); the release
   workflow declares `concurrency.group` with `cancel-in-progress: false`.
   `python3 -m unittest discover -s tools -p 'test_*.py'` exits 0.
3. `grep -q ":app:compileDebugKotlin" .github/workflows/codeql.yml` exits 0
   and `grep -q "clean" .github/workflows/codeql.yml` exits 0.
4. `grep -c "uses: ./.github/actions/setup-android-sdk" .github/workflows/*.yml`
   totals ≥ 6 and `rg -n "sdkmanager" .github/workflows` exits 1 (inline
   copies gone).
5. `grep -q "distributionSha256Sum" gradle/wrapper/gradle-wrapper.properties`
   exits 0.
6. `rg -n "scripts/tests" tools/test_release_workflows.py` output no longer
   claims ciFast runs it (docstring corrected; meta-test suite green).
7. First runs of every changed workflow are green:
   `gh run watch RUN_ID --repo bee-san/kanji_anki --exit-status` exits 0 for
   android-ci, sonarqube, codeql, android-instrumented, and the next
   auto-release.

### Goal 54: Make Sonar main-branch coverage deterministic (old Goal 29, unchanged and still open)

**Problem:** All parts verified still present at `438a3ccf`:
`sonarqube.yml:87-138` selects tasks from `git diff ${{ github.event.before }}`
on main pushes with a self-compare fallback (`BASE_SHA="${{ github.sha }}"`,
lines 96-101), so main-branch coverage jitters with push shape;
`existingSonarPaths` silently drops missing report paths (root
`build.gradle.kts:43-45`); dead `app/src/main/java/**` exclusion patterns
(`:61-87`; the app is 100% Kotlin) plus hardcoded AGP intermediates paths
break silently across AGP upgrades; the Sonar PR-argument logic is duplicated
between `sonarqube.yml:210-226` and `.github/scripts/sonar-full-coverage.sh:4-21`;
the Sonar workflow lacks the Robolectric android-all cache the `app-unit` CI
leg has.

**Goal:** On main pushes run the full deterministic task set (keep
changed-area selection for PRs only); add a preflight that fails loudly when
the app-module class dirs did not resolve; prune dead exclusion patterns;
single-source the Sonar argument assembly into one script with a mode flag;
add the Robolectric cache.

**Done when (machine-checkable):**

1. `rg -n "github.event.before" .github/workflows/sonarqube.yml` exits 1, or
   every remaining use is provably inside a PR-only conditional (meta-test
   parses and asserts this).
2. New Gradle preflight fails loudly: with an intentionally wrong class-dir
   path injected via `-P` override, `./gradlew sonarPreflight` exits
   non-zero; unmodified, it exits 0 and CI runs it before `sonar`.
3. `rg -n "src/main/java" build.gradle.kts` exits 1.
4. `rg -n "sonar-args" .github/workflows/sonarqube.yml .github/scripts/sonar-full-coverage.sh`
   shows both call the same shared script (grep exit 0 in both files); the
   duplicated inline blocks are gone.
5. `grep -q "robolectric-android-all" .github/workflows/sonarqube.yml` exits 0.
6. First Sonar Actions run after the change is green:
   `gh run watch RUN_ID --repo bee-san/kanji_anki --exit-status` exits 0.

---

## Batch D: Updater & debug log

### Goal 55: Auto-update lifecycle guards — never restart mid-study, never wedge on denied permission

**Problem:** Four liveness/UX defects in the #497/#508 auto-install flow:

- Silent installs can restart the app while it is in active use:
  `sessionParams` sets `USER_ACTION_NOT_REQUIRED` when allowed
  (`app/src/main/kotlin/dev/bee/kanjianki/update/GitHubUpdater.kt:791-800`),
  and commits are triggered from the daily worker (which can fire while
  foregrounded, `AutoUpdateWorker.kt:45-49`) and from every `onResume`
  (`MainActivityLifecycle.kt:23-38` → `ResumeUpdateInstaller`). No
  foreground/mid-study guard exists before `session.commit`.
- A never-granted install permission permanently disables update *checking*:
  `AutoUpdateRunPolicy.shouldRun` returns false while a pending update exists
  (`update-core/.../AutoUpdateRunPolicy.kt:5-7`), `ResumeInstallPolicy`
  refuses without `canRequestPackageInstalls`, the stale-APK sweep skips the
  pending name, and `InstallPermissionPromptPolicy` never re-prompts because
  the pending version never changes (`InstallPermissionPromptPolicy.kt:27-33`)
  — only a manual check recovers.
- Dismissing the system confirmation creates a nag loop: `pendingApkName`
  stays set, so every `onResume` re-commits a fresh installer session and
  re-launches the confirmation over whatever the user was doing
  (`ResumeUpdateInstaller.kt:21-46`,
  `PackageInstallStatusReceiver.kt:104-112`), accumulating unresolved
  sessions.
- Any failed or no-op check wipes a verified pending update's state: all
  catch-paths call `recordResult(..., pendingApkName = "")`
  (`GitHubUpdater.kt:50-64,107-123`), orphaning a fully verified cached APK
  after a transient 403/network blip.
- For MANUAL/CACHED sources, a blocked `startActivity(confirmation)` (API
  29+ background-launch restrictions) is silently swallowed with no
  notification fallback (`PackageInstallStatusReceiver.kt:106-110`).

**Goal:** All decisions live in `update-core` policies (pure, unit-testable):

- Gate worker-path silent commits on "app not foregrounded / no active study
  session"; defer resume-installs while a study session is active.
- Let update checks run when a pending update exists but permission is
  denied (newer releases supersede the pending one); define a bounded
  re-prompt policy tied to explicit user actions.
- Bound resume re-commit attempts per pending version after a user dismissal.
- Preserve `pendingApkName` across failed/no-op checks; clear it only on
  success, supersession, or validation failure.
- Post the existing update notification as a fallback when the confirmation
  activity cannot be launched.

**Done when (machine-checkable):**

1. `update-core` policy tests pass covering each rule:
   `./gradlew :update-core:test` exits 0, including named tests for —
   check-allowed-despite-pending-when-permission-denied; commit-deferred
   while foreground/study-active; re-commit attempts bounded after dismissal;
   pending preserved across transient failure; pending cleared on
   supersession.
2. App-level test: a transient network failure during a check leaves
   `hasPendingUpdate()` true and the cached APK on disk.
3. App-level receiver test: MANUAL/CACHED confirmation with a blocked
   activity launch posts the fallback notification.
4. `rg -n "recordResult" app/src/main/kotlin/dev/bee/kanjianki/update/GitHubUpdater.kt`
   inspection is enforced by test, not grep: the "wipe on failure" unit test
   in (2) is the guard.
5. `./gradlew ciFast` exits 0.

### Goal 56: Debug-log privacy hardening (provider scope, clear action, honest copy)

**Problem:** The share FileProvider is scoped to the **entire** internal
files directory — `<files-path path="."/>` in
`app/src/main/res/xml/debug_log_paths.xml:7-9` — which includes the gzipped
full database backups (`backups/kani-backup-*.gz`,
`DatabaseBackupPolicy.kt:11,31`, `DatabaseBackupWorker.kt:85-94`) and the
dictionary store. Only log files are passed to `getUriForFile` today, but any
future call-path bug or intent-redirection issue can mint a URI for the
user's whole study database. Additional gaps: no in-app "clear log" (the file
persists after toggle-off, tail-trimmed at 2 MB,
`AppDebugLog.kt:33-34,133-158`); error stacks can embed deck/model names
(`ManualSyncEngine.kt:86,196-199` → `AppDebugLog.kt:121-131`) which the share
copy does not mention; `setEnabled(false)` enqueues its footer after the
state flip so a racing `log()` can land after the "disabled" marker, and a
toggle-on/off with no captures leaves a marker-only file making `hasLog()`
true (`AppDebugLog.kt:81-92`).

**Goal:** Move the log files into a dedicated `logs/` subdirectory and scope
the provider path to it; add a "Clear log" action beside toggle/share in the
Automation settings panel; extend the share-dialog copy
(`DebugLogTextCopy`, EN/JA) to state that deck/model names may appear in
error entries; fix the disable-footer ordering and make `hasLog()` false for
marker-only files.

**Done when (machine-checkable):**

1. Provider scope test passes: Robolectric/instrumented test asserts
   `FileProvider.getUriForFile` succeeds for a file under `files/logs/` and
   **throws `IllegalArgumentException`** for `files/backups/probe.gz`.
2. `rg -n 'path="\."' app/src/main/res/xml/debug_log_paths.xml` exits 1.
3. Clear-action tests pass: policy unit test + Compose test assert the button
   exists in the Automation panel and that after clearing, `hasLog()` is
   false and the file is gone.
4. Copy test passes: `DebugLogTextCopy` unit test asserts EN and JA share
   copy contain the deck/model-name disclosure.
5. `AppDebugLog` unit tests pass: toggle on→off with zero captures →
   `hasLog()` false; no log line ever appears after the disabled footer
   (ordering test with a racing writer).
6. `./gradlew ciFast` exits 0.

### Goal 57: Updater robustness smalls (fetch caps, cert-rotation policy, dead retry flag, main-thread reads)

**Problem (batch):**

- `getText()` reads responses fully with no byte cap
  (`GitHubUpdater.kt:608-620,673-680`); the `.sha256` asset URL is
  release-controlled, so a compromised release can OOM the process with a
  multi-hundred-MB "checksum" before the signing gate is ever reached. The
  200 MB APK cap does not cover these paths.
- The signing check compares full cert-history **sets** for exact equality
  (`update-core/.../UpdateArtifactValidator.kt:68-72`,
  `GitHubUpdater.kt:829-840`): a legitimate future key rotation makes the
  sets differ and permanently hard-blocks auto-update for every existing
  install (fails closed — safe, but strands the fleet on manual sideload).
- The worker retry machinery is dead code: every production `UpdateResult`
  passes `retryable = false` (`GitHubUpdater.kt:213,220,269-271`), so
  `workerOutcome` can never return `RETRY` (`AutoUpdateRunPolicy.kt:10-12`)
  — transient failures always wait a full day while the flag misleads
  readers.
- `maybeShowUpdatePermissionPrompt` performs three settings-table reads plus
  a `PackageManager` IPC on the UI thread on **every** home render
  (`MainActivityHomeUpdatePermissionPrompt.kt:24-33`, called from
  `MainActivityHome.kt:73`) — this repo's cold-boot ANR class.

**Goal:** Cap `getText` (e.g. 64 KiB for checksum assets, 1 MiB for API
JSON) with clean abort + readable failure; decide and implement the
key-rotation acceptance rule (suggested: accept when the archive's current
signer matches any cert in the installed lineage, or vice versa) and document
it; either wire `retryable=true` for transient network failures (WorkManager
backoff) or delete the flag and `RETRY` branch; move the permission-prompt
reads into the async route-load so home renders consume a preloaded model.

**Done when (machine-checkable):**

1. Fetch-cap unit tests pass: oversized checksum stream and oversized JSON
   stream both abort with a bounded readable failure and no OOM-scale
   allocation (assert bytes-read ≤ cap).
2. Cert-rotation policy tests pass in `update-core`: rotated-lineage archive
   accepted per the chosen rule; fully disjoint certs rejected.
   `./gradlew :update-core:test` exits 0.
3. Retry flag resolved, one of: (a) worker test asserts `Result.retry()` for
   a transient network failure, or (b)
   `rg -n "retryable" app/src/main/kotlin/dev/bee/kanjianki/update update-core/src/main/kotlin`
   exits 1.
4. Static check: `rg -n "settingValue|packageManager" app/src/main/kotlin/dev/bee/kanjianki/MainActivityHomeUpdatePermissionPrompt.kt`
   exits 1 (the compose/render path consumes a preloaded model; reads happen
   on the loader thread), plus a unit test asserting the prompt decision is
   computed from the route-load payload.
5. `./gradlew ciFast` exits 0.

---

## Batch E: Planner & scheduler polish

### Goal 58: Route the capped all-kanji plan through the backlog-honesty status

**Problem:** `allKanjiPlan`
(`core/src/main/kotlin/dev/bee/kanjianki/core/AdaptiveLoadPlanner.kt:283-308`)
never computes `overflowDue`. When manual-100% ("All kanji") is capped by Max
items with more due kanji than the cap, the user sees the generic "All kanji
mode is capped to today's maximum" instead of the
`AdaptiveLoadStatusFormatter` overflow message ("N due kanji wait beyond
today's cap…") — violating the formatter's own documented contract
("backlog honesty… in both modes",
`AdaptiveLoadStatusFormatter.kt:7-11`). It also passes `focus.size` as
`newAdmissionLimit`, counting due items as admissions — diverging from the
main path's `focusKanji.size - cappedRecoveryDue` (currently benign because
the seeder treats capped all-kanji plans as an upper bound, but it is an
unstated invariant).

**Goal:** Compute due overflow on the capped all-kanji path and emit the
formatter's overflow status; align `newAdmissionLimit` with the main path's
semantics (or, if the divergence is deliberate, encode it in a named
function with a comment and a pinning test).

**Done when (machine-checkable):**

1. Core test passes: manual mode, workload 100%, `itemCap` < number of due
   kanji → `plan.status` contains `"due kanji wait beyond today's cap"`.
2. Core test passes pinning `newAdmissionLimit` on the capped all-kanji path
   to the chosen semantics (aligned value, or documented divergent value).
3. `./gradlew :core:test --tests "dev.bee.kanjianki.core.AdaptiveLoadPlannerTest"`
   exits 0; `./gradlew ciFast` exits 0.

### Goal 59: Fix Lorenz-head edge cases and make the candidate-scoring docs honest

**Problem:** Three follow-ups to the `438a3ccf` planner overhaul:

- `concentratedHeadSize` iterates `0 until ranked.size - 1`
  (`core/src/main/kotlin/dev/bee/kanjianki/core/AdaptiveLoadFocusPolicy.kt:81`),
  so a **single** non-due candidate can never be classified concentrated —
  target is still correct (1) but the status reads "Today's priority is
  spread evenly", which is wrong copy for one dominant kanji.
- The code selects the gap-**maximizing** prefix while the commit message
  describes "the smallest head … meaningfully more loaded"; for gap profiles
  like `[0.26, 0.30]` the code picks head 2 where the description implies
  head 1. Decide which is intended and align docs/comments (max-gap is
  defensible; say so).
- The dominance hierarchy documented on `AdaptiveLoadCandidate`
  (`AdaptiveLoadCandidate.kt:10-28`: fsrsRisk > exposureBoost >
  weaknessScore > frequencyValue) is not enforced: `weaknessScore`'s
  suspended term is uncapped (`KanjiAnalyzer.kt:66-70`, `suspended * 12`), so
  10 suspended examples yield 120+ and outrank most `fsrsRisk` values.
  Either cap the term where the doc claims the ceiling, or rewrite the doc to
  describe additive contribution without a strict hierarchy.

**Goal:** Special-case `ranked.size == 1` with positive priority as
concentrated (copy-only change to selection outcome); align the
smallest-head-vs-max-gap description with the implemented rule; make the
scoring KDoc truthful (cap or re-document).

**Done when (machine-checkable):**

1. Core test passes: auto mode, exactly one non-due candidate with positive
   priority → `plan.status` contains `"concentrated"`.
2. Core test pins the exact-0.25 gap boundary (inclusive) and the multi-gap
   tie choice, matching whatever the aligned documentation states.
3. One of: (a) core test asserts the suspended term is capped such that
   `weaknessScore ≤` the documented bound, or (b)
   `rg -n "strongest first" core/src/main/kotlin/dev/bee/kanjianki/core/AdaptiveLoadCandidate.kt`
   exits 1 (the strict-hierarchy claim removed and replaced with accurate
   prose).
4. `./gradlew :core:test` and `./gradlew ciFast` exit 0.

### Goal 60: Decide the relearning-graduation difficulty rule (golden-gated parity call)

**Problem:** On a lapse, `applyReviewAgain` already runs
`fsrsAdapter.review(AGAIN, …)`, updating stability **and** difficulty. When
the card later graduates from relearning steps,
`graduateToReview → initialReview(isNewLearning = false)`
(`core/src/main/kotlin/dev/bee/kanjianki/core/ReviewTransitionEngine.kt:281-296`,
`LatestFsrsAdapter.kt:20-27`) keeps post-lapse stability but applies
`nextDifficulty(graduationRating)` a **second** time. That matches neither
Anki-without-short-term (no memory change on relearning-step answers) nor
AGENTS.md, which claims graduation uses `engine.initialState(graduationRating)`
alone — true only for `NEW_LEARNING`. Net effect: repeated lapses drift
difficulty downward faster than the pinned reference behavior.

**Goal:** Make a deliberate decision:

- (a) Parity: relearning graduation applies **no** additional memory change
  (schedule from post-lapse memory; drop the second `nextDifficulty`), with
  deliberate golden-timeline regeneration; or
- (b) Keep: document the intentional deviation in AGENTS.md next to the
  existing graduation note, with a pinning test.

**Done when (machine-checkable):**

1. If (a): core test asserts difficulty after relearning graduation equals
   the post-lapse difficulty (no second update); golden timelines regenerated
   via the documented generator — `git diff --stat` on
   `core/src/test/resources/**/scheduler-goldens/` is non-empty in the same
   commit, and `./gradlew :core:test` exits 0.
   If (b): AGENTS.md contains the new documented rule
   (`grep -q "relearning graduation" AGENTS.md` exits 0) and a pinning test
   asserts the current double-update value.
2. `FsrsEngineReferenceTest` (upstream oracle) still passes unchanged.
3. `./gradlew ciFast` exits 0.

### Goal 61: Scheduler small-defect sweep

**Problem (batch, each with evidence):**

- `lastFailedRecognitionDayMillis` is set to `lastRealReviewDueAtMillis` on
  **every** real-due review including passes
  (`ReviewTransitionEngine.kt:451-452`) — anything reading the legacy
  "last failed day" column sees the last *review* day.
- `StudySessionSelector.nextSession`'s missing-row guard returns `null` for
  the **whole session** instead of skipping the item, contradicting its own
  comment (`StudySessionSelector.kt:34-36`; currently unreachable dead code,
  but wrong fallback if the invariant ever breaks).
- Dead expression: `secureRandom ?: SecureRandom()` can never take the right
  branch (`StudySessionSelector.kt:470`; `secureRandom` is non-null exactly
  when `seed == null`).
- `similar_kanji_review_log` grows forever and is fully materialized on
  every sync: the SQL filters `correct = 0` but not
  `reviewed_at >= windowStart`
  (`app/src/main/kotlin/dev/bee/kanjianki/data/LocalStoreSimilarKanjiMaintenance.kt:70-93`),
  and the table has inserts only — no deletion path. `ConfusionPairMiner`
  discards >90-day rows *after* they are loaded into memory.

**Goal:** Only-set the legacy failure mirror on fails (or rename the column
mapping with a migration note); make the missing-row guard skip the item and
continue; delete the dead `?:` branch; push the 90-day window into the SQL
`WHERE` clause and prune rows older than the window during similar-kanji
maintenance.

**Done when (machine-checkable):**

1. Core test: a real-due **pass** leaves `lastFailedRecognitionDayMillis`
   unchanged; a real-due fail updates it.
2. Core test: with one candidate lacking a queue row and one healthy due
   candidate, `nextSession` returns a session containing the healthy item.
3. `rg -n "secureRandom \?: SecureRandom\(\)" core/src/main/kotlin` exits 1.
4. App test: wrong-pick rows older than 90 days are (a) excluded by the
   query (assert via a seeded old row not reaching the miner) and (b) deleted
   by maintenance (assert row count shrinks after the maintenance run).
5. `./gradlew :core:test`, `./gradlew :app:testDebugUnitTest`, and
   `./gradlew ciFast` exit 0.

---

## Batch F: Hygiene sweep + carry-over index

### Goal 62: Repo hygiene sweep

**Problem (small items):**

- `CLAUDE.md` is a symlink to `AGENTS.md`, but the tracked file is
  lowercase `agents.md`. On case-insensitive macOS this resolves; on any
  case-sensitive checkout (Linux CI, codespaces) the symlink dangles.
- Planning docs remain scattered (old Goal 26 remainder): `new/`, `plans/`,
  `suspended.md` at root, plus two doc roots (`docs/` and `documentation/`).
- The control-byte hygiene test from Goal 41 should also cover workflow YAML
  and Python sources once it exists (cheap extension).

**Goal:** Normalize the AGENTS filename casing (suggest `git mv agents.md
AGENTS.md` and keep the `CLAUDE.md` symlink); consolidate `new/` and
`suspended.md` under `docs/plans/`; merge `documentation/` into `docs/`
(updating referencing links in AGENTS.md and README); extend the hygiene test.

**Done when (machine-checkable):**

1. Symlink resolves everywhere:
   `python3 -c "import os,sys;sys.exit(0 if os.path.exists(os.path.realpath('CLAUDE.md')) else 1)"`
   exits 0, and `git ls-files | grep -x "AGENTS.md"` exits 0 (tracked name
   matches the symlink target exactly, case-sensitively).
2. `test ! -d new && test ! -f suspended.md && test ! -d documentation`
   exits 0; `git ls-files new/ suspended.md documentation/ | wc -l` prints 0.
3. No dangling references: `rg -n "documentation/|suspended.md|(^|[^a-z])new/" AGENTS.md README.md docs/ --glob '!docs/plans/**'`
   exits 1 (excluding the consolidated plan files themselves).
4. Hygiene suite extended and green:
   `python3 -m unittest discover -s tools -p 'test_*.py'` exits 0.
5. `./gradlew ciFast` exits 0 (path filters unaffected or updated in the
   same change).

### Carry-over index (2026-07-03 goals still open at 438a3ccf)

Verified statuses from this pass; work these from the old document, which
retains their full context:

| Old goal | Status at 438a3ccf | Gate |
|---|---|---|
| 8 (config-change survival) | Superseded by **Goal 43** here | instrumented |
| 13 (persist `lastReviewedAtMillis`) | OPEN — `TaskMemory` has no field; engine still reconstructs (`ReviewTransitionEngine.kt:494-499`) | goldens + live gate; land with 14 |
| 14-core (day-boundary elapsed days) | OPEN — `localDaysBetween` exists with DST tests but has zero production callers | goldens + live gate; land with 13 |
| 17-remainder (due-slot sentinel; rung-disabled mid-relearning) | OPEN — `0L` sentinel intact (`ReviewTransitionEngine.kt:407-413`); no monotonic sequence; no mid-relearning-disable test | goldens (sentinel fix is schema-level) |
| 21 (signing + R8) | Superseded by **Goal 49** here | ciRelease + emulator |
| 25 (module boundaries, split packages, `fsrs-java` name) | OPEN — unchanged; CodeQL still targets `compileJava` on all-Kotlin modules | watch CodeQL run |
| 26-remainder (docs sprawl) | Folded into **Goal 62** here | ciFast |
| 28 (scheduled full `connectedDebugAndroidTest`) | OPEN — nightly runs only the 3-class fixture; no `::warning::` retry annotation | watch Actions |
| 29 (Sonar determinism) | Superseded by **Goal 54** here | watch Sonar run |
| 30-remainder (workflow boilerplate) | Mostly folded into **Goal 53** here (screenshot-workflow merge still optional) | watch Actions |
| 31 (split live test from the 3,513-line monolith) | OPEN — method still at `MainActivityInstrumentedTest.kt:2169`, selected by string in `run_ankidroid_fixture.sh:218` | fixture dispatch run |
| 32 (single gesture system; composition-time state write) | OPEN — both systems + `swipeFeedback?.thresholdPx` write intact (`MainActivityStudyFlashcardCompose.kt:152`) | UI tests on device |
| 33-remainder (dialog re-render) | PARTIAL — theme cache + badge fixed; `pendingHomeSyncDialog`/`pendingUpdatePermissionDialog` still full re-renders (`MainActivityHome.kt:48-49,211-260`) | UI tests |
| 35 (TalkBack containers; deprecated bar colors) | OPEN — `contentDescription = "Kani shell …"` (`MainActivityShell.kt:91-94`); `window.statusBarColor` (`MainActivityUiSupport.kt:27-28`) | on-device TalkBack |
| 36-remainder (`LABEL_*` constants; SyncProgressPanel inline pair) | PARTIAL — study buttons localized; stragglers at `MainActivityBase.kt:566-590`, `SyncProgressPanel.kt:113-119` | ciFast |
| 38 (vararg positional constructors; BridgeScheduler telescoping) | OPEN — all sites unchanged (`RecordsStudyModels.kt:157-165,704`; `RecordsSyncModels.kt:18,219`; `BridgeScheduler.kt:29-328`). Schedule as its own golden-gated effort; still the biggest latent-bug generator in core | goldens |
