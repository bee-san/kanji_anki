# Deep Review Goals

Source: full-codebase deep review (architecture, scheduler/FSRS, UI, sync/data, CI/testing).
Each goal below is self-contained: it has context, the files involved, and acceptance
criteria. Work them one at a time with `/goal`. Ordering within each section is by
priority. The suggested batch order is:

1. Data safety (Goals 1-3)
2. Sync integrity (Goals 4-7)
3. UX / main-thread (Goals 8-12)
4. Scheduler correctness (Goals 13-17)
5. Update & release security (Goals 18-21)
6. Structural / build (Goals 22-26)
7. CI & test hygiene (Goals 27-31)
8. Compose & misc polish (Goals 32-38)

Always validate with `./gradlew ciFast` (see AGENTS.md). Goals touching provider/sync
behavior additionally require the live AnkiDroid emulator gate before release.

---

## Batch 1: Data safety

### Goal 1: Add retention/pruning to historical sync snapshot tables

**Problem:** `HistoricalSyncStore.appendHistoricalSyncSnapshots`
(`app/src/main/kotlin/dev/bee/kanjianki/data/HistoricalSyncStore.kt:13-102`) inserts a
full copy of every note and card (including `fields_json`) into `sync_card_snapshots`
and `sync_note_snapshots` on every successful sync. There is no retention or pruning
anywhere. With a 7,000+ note collection and daily auto-sync this is ~2.5M+ rows/year of
full note text — unbounded storage growth.

**Goal:** Add a retention policy so snapshot tables stop growing without bound.
- Determine which sync_ids the impact report actually needs
  (`KanjiImpactReportStore.kt:273-280` self-joins card snapshots — it mostly needs
  baseline + latest).
- Keep per-kanji aggregates (`sync_kanji_snapshots`) long-term; prune note/card
  snapshots for superseded sync_ids (or keep last N syncs / N days) inside
  `saveSuccessfulSync`.
- Add unit tests proving pruning fires and the impact report still works after pruning.

**Done when:** repeated syncs no longer grow `sync_card_snapshots`/`sync_note_snapshots`
beyond the retention window; impact-report tests pass; `ciFast` green.

### Goal 2: Enable SQLite WAL mode and make backups WAL-safe

**Problem:** No `setWriteAheadLoggingEnabled` anywhere. In rollback-journal mode, the
large sync write transaction blocks all readers for its duration → jank/ANR during
background sync. Meanwhile `DatabaseBackupWorker`
(`app/src/main/kotlin/dev/bee/kanjianki/backup/DatabaseBackupWorker.kt`) does a plain
file copy and a `wal_checkpoint(TRUNCATE)` that is currently a no-op; if WAL is enabled
without fixing the backup, backups can be torn/stale (copy ignores `-wal`/`-shm`).

**Goal:** Do these together:
- Enable WAL in `LocalStoreBase` init
  (`app/src/main/kotlin/dev/bee/kanjianki/data/LocalStoreBase.kt:17-22`).
- Replace the backup's checkpoint+file-copy with `VACUUM INTO ?` through the existing
  helper connection (API 27+/SQLite 3.27, minSdk is 26 — guard or use a copy-under-
  exclusive-transaction fallback for API 26). Remove the second raw `SQLiteDatabase`
  connection the worker opens (`DatabaseBackupWorker.kt:163-168`).
- Consider `yieldIfContendedSafely` in the historical-snapshot insert loop.
- Add/adjust instrumentation tests for backup integrity (open the backup copy and run
  an integrity check / row count).

**Done when:** WAL is on, backups are produced via VACUUM INTO (or safe fallback),
backup instrumented tests pass, `ciFast` green.

### Goal 3: Reduce backup storage footprint

**Problem:** 31 daily full-file DB copies (`core` `DatabaseBackupPolicy.MAX_BACKUPS = 31`)
of a growing database. Combined with Goal 1's growth this dominates app storage. There
is also no in-app restore path, so most copies are dead weight.

**Goal:**
- Compress backups (gzip; SQLite DBs compress ~4-10x) in `DatabaseBackupWorker`.
- Change retention to a tiered scheme (e.g., 7 daily + 4 weekly) in
  `DatabaseBackupPolicy` with unit tests for the pruning selection.
- (Optional stretch) add a debug-only restore path or document manual restore in docs/.

**Done when:** backups are compressed, tiered retention has unit tests, worker
instrumented tests updated, `ciFast` green.

---

## Batch 2: Sync integrity

### Goal 4: Fix lost-update race between auto-sync and active study

**Problem:** `ManualSyncEngine.runLocked`
(`app/src/main/kotlin/dev/bee/kanjianki/sync/ManualSyncEngine.kt:122-136`) reads
`store.studyItemsForKanji(...)`, computes seeding in memory, then `replaceStudyItems`
does delete-all + reinsert in a separate transaction
(`app/src/main/kotlin/dev/bee/kanjianki/data/LocalStoreStudy.kt:36-47`). A review the
user saves between the read and the replace is silently overwritten with pre-review
scheduler state (auto-sync can fire while the app is foregrounded; only manual sync
blocks the UI). The `review_log` row survives but FSRS memory/rung state regresses.

**Goal:** Eliminate the window. Options (pick the cleanest):
- Hold one transaction across read → seed → write; or
- Re-read inside the write transaction and merge items whose
  `last_real_review_due_at`/`total_reviews` changed since the initial read; or
- Block study submission while `ManualSyncEngine.RUNNING` is set (it is already a
  static `AtomicBoolean` — expose it) and vice versa.
Add a unit/Robolectric test that simulates a review landing mid-sync and asserts the
review is not lost.

**Done when:** the mid-sync review survives in the test; `ciFast` green. Provider/sync
change ⇒ run the live AnkiDroid gate before any release.

### Goal 5: Close the two-transaction commit window in sync persistence

**Problem:** `saveSuccessfulSync` (mirror + sync_run row, transaction #1) and
`replaceStudyItems` (transaction #2) are separated by heavy in-memory work
(`ManualSyncEngine.kt:108-136`). A crash between them leaves a committed "success"
sync with stale study items, and `hasSuccessfulSyncSince` then makes auto-sync skip
for the rest of the day (`app/src/main/kotlin/dev/bee/kanjianki/sync/AutoSyncRunner.kt:32`),
so the inconsistency persists.

**Goal:** Either wrap both phases in one transaction, or record the sync run as
`pending` in txn #1 and flip to `success` only after study items commit (treat
lingering `pending` as failed for `hasSuccessfulSyncSince`). Add a test simulating a
crash between phases and asserting auto-sync retries.

**Done when:** the crash-window test passes; `ciFast` green.

### Goal 6: Log sync failures with stack traces and stop swallowing Errors

**Problem:** `ManualSyncEngine.runLocked` catches `SyncFailure` and blanket `Throwable`
(`ManualSyncEngine.kt:176-190`) and only persists `error.message` into `sync_runs` —
no `Log.e`, no stack trace anywhere. `catch (Throwable)` also converts
`OutOfMemoryError`/`StackOverflowError` into a "retryable_error" row. If
`saveFailedSync` itself throws (disk full), the original error is masked.

**Goal:**
- `Log.e` the full stack before persisting failures.
- Narrow `Throwable` to `Exception` (rethrow `Error`s).
- Guard `saveFailedSync` with its own try/catch and `addSuppressed` the original.
- Optionally persist a truncated stack string in `sync_runs` for on-device diagnosis.
Unit tests for the classification behavior.

**Done when:** failure paths log stacks, Errors propagate, tests pass, `ciFast` green.

### Goal 7: Fix AutoSyncJobService stopped-flag capture and add cooperative cancellation

**Problem:** `AutoSyncJobService`
(`app/src/main/kotlin/dev/bee/kanjianki/sync/AutoSyncJobService.kt:54-56`) evaluates
`stopped` when the task starts, so `jobFinished(params, needsReschedule=stopped)` uses
the pre-sync value; a job stopped mid-sync never requests reschedule with fresh state.
There is no cooperative cancellation: `onStopJob` sets a flag nobody reads mid-loop,
and `io.shutdownNow()` interrupts a thread inside SQLite/provider calls that ignore
interrupts.

**Goal:**
- Pass a `stopped: () -> Boolean` supplier down through `AutoSyncRunner` into
  `ManualSyncEngine` and check it between note batches in
  `AnkiDroidCardReader.queryCardsByNote`
  (`app/src/main/kotlin/dev/bee/kanjianki/anki/AnkiDroidCardReader.kt:35`), aborting
  with a retryable failure.
- Evaluate `stopped` at completion time for the `jobFinished` reschedule flag; clean up
  the contradictory `onStopJob`-returns-true + later-`jobFinished` pattern.
Instrumented/unit tests for the cancellation path.

**Done when:** mid-sync stop aborts between batches with a retryable failure and the
reschedule flag reflects reality; `ciFast` green.

---

## Batch 3: UX / main-thread correctness

> DEFERRED (deep-review pass 2026-07-03): Goal 8 is the most architecturally
> invasive item in the plan (activity lifecycle, executor ownership, retained
> session state) and its acceptance is gated on an `activity.recreate()`
> instrumented test that requires a live emulator. Defer until it can be
> validated on-device rather than land it blind. All other Batch-3 goals
> (9-12) are complete.

### Goal 8: Survive configuration changes (rotation, dark mode, locale)

**Problem:** No `onSaveInstanceState`, no ViewModel, no retained state. Every rotation
recreates the activity and `MainActivityStartup.handleLaunchIntent`
(`app/src/main/kotlin/dev/bee/kanjianki/MainActivityStartup.kt:31-56`) always renders
Home. Mid-study state (ink strokes in `DrawingPadView`, `TypingAnswerState`, session
position in `StudySessionTracker`) is unrecoverable. Also
`MainActivityLifecycle.onDestroy` calls `io.shutdownNow()`
(`app/src/main/kotlin/dev/bee/kanjianki/MainActivityLifecycle.kt:41`), which can
interrupt an in-flight sync or `runSettingsWrite`
(`MainActivitySettings.kt:274-285`) mid-run.

**Goal:**
- Move `store`, `gateway`, the `io` executor, and session state (`activeSession`,
  `StudySessionTracker`, current route) into a retained holder that survives
  configuration changes (a ViewModel-scoped holder or an application-scoped singleton —
  match the codebase's no-ViewModel style if preferred, but it must be retained).
- Restore `currentRoute` (and study session position) after recreation instead of
  forcing Home.
- Never `shutdownNow()` an executor that may hold a sync; tie its lifetime to the
  retained holder / process, not the activity.
- Add an instrumented test: start a study card, trigger recreation
  (`activity.recreate()`), assert the study route and session state survive.

**Done when:** recreation test passes, no work is killed on rotation, `ciFast` green.

### Goal 9: Move the Study route's DB work off the main thread

**Problem:** `MainActivityStudyQueueCoordinator.renderStudyInternal`
(`app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyQueueCoordinator.kt:14-73`)
runs `activeDashboardRows()`, `studyItemsForKanji()`, two plan computations,
`dueSimilarWritingRepairs()`, and `saveStudyItem()` synchronously on the Study-button
tap. Home (#486), settings (#489), and browse were already moved to
`AsyncHomeRouteLoader`; the highest-traffic route was not. Existing probe scaffolding
(`withStudyLoadProbe`, `StudyLoadDebugLog`) shows this is a known hot spot.

**Goal:** Load the Study route through the same async route-loading pattern
(`AsyncHomeRouteLoader.loadRouteAsync`), preserving the loading-screen delay behavior.
Keep review submission ordering intact. Update/keep the study load probe assertions.

**Done when:** no LocalStore reads/writes execute on the main thread when tapping
Study (verify via existing probe or StrictMode in a test), UI tests pass, `ciFast`
green.

### Goal 10: Warm heavy assets during startup instead of first use

**Problem:**
- `MainActivityBase.strokeGuide()`
  (`app/src/main/kotlin/dev/bee/kanjianki/MainActivityBase.kt:401-406`) parses the
  9.5 MB `res/raw/kanji_strokes.tsv` synchronously the first time a writing card
  renders (`MainActivityStudyWritingSession.kt:62`).
- `MainActivityDictionaryLookupProvider`
  (`MainActivityDictionaryLookupProvider.kt:8-11`) triggers `DictionaryStore.open`,
  which on first run copies and SHA-256-hashes a 1.3 MB asset DB
  (`app/src/main/kotlin/dev/bee/kanjianki/data/DictionaryStore.kt:250-286`) — called
  from the flashcard reveal tap.

**Goal:** Warm both on the `io` executor during startup — `MainActivityStartup.start`
already has the hook (lines 20-27). First use must still work if warmup hasn't
finished (block-or-await semantics, thread-safe single initialization).

**Done when:** first writing card and first flashcard reveal do no main-thread asset
parsing after warmup; thread-safety of lazy init covered by a test; `ciFast` green.

### Goal 11: Move BroadcastReceiver work off the main thread

**Problem:** Receivers do heavy DB work on the main thread:
- `ReminderReceiver.onReceive` → `ReminderScheduler.showReminderNotification` reads the
  full dashboard + all study items + streaks
  (`app/src/main/kotlin/dev/bee/kanjianki/reminders/ReminderScheduler.kt:349-423`).
- `BootReminderReceiver` reschedules four subsystems, each opening its own `LocalStore`
  (`BootReminderReceiver.kt:33-39`).
- `PackageInstallStatusReceiver.onReceive` does a `LocalStore` write plus file deletion
  (`app/src/main/kotlin/dev/bee/kanjianki/update/PackageInstallStatusReceiver.kt:26-35`).

**Goal:** Use `goAsync()` + a background thread (or delegate to WorkManager, already a
dependency) for each receiver. Keep the existing instrumented receiver tests passing;
add coverage for the async completion.

**Done when:** no receiver touches LocalStore on the main thread; instrumented tests
pass; `ciFast` green.

### Goal 12: Fix cross-thread visibility races in the app layer

**Problem:**
- `AsyncHomeRouteLoader.generation` is a plain `Int` mutated on main and read on
  background threads (`app/src/main/kotlin/dev/bee/kanjianki/AsyncHomeRouteLoader.kt:22,36,53,58,79`)
  — a cancelled load may still render. The file already uses `AtomicBoolean` for
  `finished`, so the pattern is half-applied.
- `studyDueBadgeCount` written on a background thread
  (`MainActivityHome.kt:101`) and read on main (`MainActivityShellHost.kt:70`) with no
  synchronization; it also goes stale after completing reviews until the next Home
  visit.
- `activeBrowseQuery` written on main, read from the io `load` lambda
  (`MainActivityHomeBrowseDetail.kt:26-27`).
- `LocalStoreInventory` holds ~10 plain mutable cache fields incl. a non-synchronized
  `LinkedHashMap` mutated at `LocalStoreInventory.kt:251`, read from UI and cleared/
  repopulated from sync threads (`clearStudyItemsCache` inside the write transaction,
  `LocalStoreStudy.kt:46`).
- Un-guarded `main.post` after activity destruction: `MainActivitySettings.runUpdate`
  (`:368-383`) and `ManualSyncCoordinator` post `setContent`/`startActivity`/toasts
  against a possibly-destroyed activity; token checks guard route changes, not
  destruction.

**Goal:**
- `AtomicInteger` for `generation`.
- `@Volatile` (or main-thread-confined recompute) for the badge/browse fields; refresh
  the study badge when reviews complete.
- Make `LocalStoreInventory` caches thread-safe: `@Volatile` immutable snapshots,
  `ConcurrentHashMap`, or funnel all store access through one executor.
- Guard posted UI work with a destroyed-check (e.g., `lifecycle.currentState`).

**Done when:** all listed fields are safely published; a stress/unit test covers the
loader generation race; `ciFast` green.

---

## Batch 4: Scheduler correctness (core / fsrs)

> STATUS (deep-review pass 2026-07-03): Goal 15 (stop rounding) and Goal 16
> (token-on-success) are DONE. Goal 17 is PARTIAL (see its note). Goal 14 has
> its additive foundation landed — `LocalDayPolicy` now takes an injectable
> `TimeZone` and exposes `localDaysBetween` with DST spring-forward/fall-back +
> timezone tests. The remaining output-changing cores of Goals 13 and 14
> (persisting `lastReviewedAtMillis` + a DB column/migration; routing FSRS
> elapsed days through `localDaysBetween` in `ReviewTransitionEngine` and
> plumbing an injectable clock) are DEFERRED and should land together as one
> reviewed change: both alter FSRS stability/retrievability outputs and require
> deliberate golden-timeline regeneration plus the live AnkiDroid gate. They are
> not safe to land blind in this environment.

### Goal 13: Persist lastReviewedAtMillis instead of reconstructing elapsed time

**Problem:** `ReviewTransitionEngine`
(`core/src/main/kotlin/dev/bee/kanjianki/core/ReviewTransitionEngine.kt:479-484`)
derives `elapsedReviewDays` as `dueAtMillis − matureIntervalDays·DAY`. This is only
correct while nothing else edits due/interval — but `StudyItem.withSuppression`
(`core/src/main/kotlin/dev/bee/kanjianki/core/RecordsStudyModels.kt:306-312`) already
mutates `matureIntervalDays` independently, and the promotion-cap path clones capped
memory into both rungs (`ReviewTransitionEngine.kt:446-450`), so promote→demote cycles
under-estimate elapsed time. Corrupted elapsed days corrupt FSRS retrievability and
stability updates.

**Goal:** Add `lastReviewedAtMillis` to `TaskMemory`, persist it on every review, and
compute elapsed time from it (fall back to the current reconstruction for legacy rows).
Requires a DB column + migration in `LocalStore` and wire-format handling. Add tests
covering suppression and promote→demote cycles producing correct elapsed days.

**Done when:** elapsed time is derived from the persisted timestamp; migration is
idempotent; golden timelines updated if affected; `ciFast` green.

### Goal 14: Compute FSRS elapsed days with day boundaries, not a 24-hour floor

**Problem:** `ReviewTransitionEngine.kt:483` floors `elapsedMillis / DAY`; a review
23h59m after the previous one gets `elapsedDays = 0` and is routed through
*short-term* stability (`fsrs-java/.../DefaultFsrsEngine.kt:32-44`) instead of the
long-term curve. Anki computes elapsed in collection-day units, so slightly-early
reviews of 1-day-cycle cards are systematically mis-classified.

**Goal:** Compute elapsed days using local-day boundaries (extend `LocalDayPolicy`,
`core/src/main/kotlin/dev/bee/kanjianki/core/LocalDayPolicy.kt`), and make
`LocalDayPolicy` accept an injectable `TimeZone`/clock (it currently hardcodes
`Calendar.getInstance()` with the default TZ at lines 8, 16, 30-33 — this also feeds
new-card admission counting in `StudyQueueSeeder.kt:460-468`). Add DST spring-forward/
fall-back and timezone-change tests (current `LocalDayPolicyTest` pins UTC only).

**Done when:** day-boundary elapsed computation is in place with DST tests; golden
timelines regenerated deliberately if outputs change; `ciFast` green.

### Goal 15: Stop rounding FSRS stability/difficulty on persistence

**Problem:** `roundScore` (2 decimal places, `ReviewTransitionEngine.kt:603`) is applied
to stability/difficulty on every review persist (`:415-416, :428-429`). For small
stabilities (initial Again S≈0.212) that is ~2% relative error per persist,
compounding across reviews.

**Goal:** Store full doubles; round only for display. Audit any tests/goldens that
assert rounded persisted values and update them deliberately. Add a soak test that
repeated reviews never produce non-finite stability/difficulty (exercises the `safe*`
clamps in `LatestFsrsAdapter.kt:69-90`, currently untested against round-tripping).

**Done when:** persistence keeps full precision, soak test passes, `ciFast` green.

### Goal 16: Consume idempotency tokens only on success

**Problem:** `ReviewTransitionEngine.kt:17` adds `request.token` to `consumedTokens`
before any FSRS computation. If a downstream `require` throws, the retry with the same
token is rejected as "duplicate" while the item was never updated.

**Goal:** Move token consumption to after the review successfully applies. Add a test:
force a failure mid-apply, retry with the same token, assert the retry succeeds.
Also review `MainActivityStudyReviewFlow.kt:104` which copies `consumedTokens()` into
a fresh `HashSet` per submit (idempotency currently depends entirely on persistence) —
document or tighten as part of this goal.

**Done when:** failed reviews are retryable with the same token; test proves it;
`ciFast` green.

> PARTIAL (deep-review pass 2026-07-03): Fixed the demotion-floor streak reset,
> the Hard-on-first-step descending-steps midpoint, the StudySessionSelector
> `row!!` crash, and swapped cosmetic `SecureRandom` shuffling for `Random`;
> added tests for those plus the single-step 1.5x Hard branch, similar_kanji
> availability-flip demotion, and the clock-moved-backwards clamp; documented
> the intentional learning-step-history-invisible-at-graduation behavior in
> AGENTS.md. STILL OPEN, both needing more than a test addition: (1) the
> `countsAsRealDue` due-slot aliasing fix wants a persisted monotonic review
> sequence number (schema + migration + golden-timeline work, on par with Goal
> 13); (2) "rung disabled mid-relearning keeps step/memory continuity" — a
> practice Good on a card whose current rung was just disabled currently
> graduates to review (effectiveRung remaps the disabled rung), which is a real
> behavioral decision to make deliberately, not a drive-by test.

### Goal 17: Fix minor scheduler defects and close test gaps

**Problem (each small, batch them):**
- Demotion streak resets at the floor (`ReviewTransitionEngine.kt:333-337`): at
  `WRITE_KANJI` the streak is zeroed even though no demotion occurred, so
  chronically-failing floor cards under-report in `LadderHealthPolicy`.
- `countsAsRealDue` due-slot aliasing (`ReviewTransitionEngine.kt:392-398`): dedupe by
  `lastRealReviewDueAtMillis != currentDueSlot` misfires on repeated-millisecond due
  slots and overloads `0L` as "never reviewed". Use a monotonically increasing review
  sequence number.
- Hard-on-first-step uses `max(step0, midpoint)` (`ReviewTransitionEngine.kt:241-243`);
  with descending steps like `[10, 5]` Hard equals the Again delay, contradicting the
  documented "between Again and Good".
- `SecureRandom` used for cosmetic queue shuffling
  (`StudySessionSelector.kt:452-475`, suppression at `:93`) — use seeded `Random`.
- `row!!` in session construction (`StudySessionSelector.kt:38`) — return null / skip
  instead.
- Test gaps: item resting on `SIMILAR_KANJI` when `hasSimilarKanji` flips false;
  clock-moved-backwards (`max(0, …)` clamp at `ReviewTransitionEngine.kt:482`); Hard
  with a single learning step (1.5× branch at `:244-247`); rung disabled mid-relearning
  keeps step/memory continuity.

**Goal:** Fix each defect and add each missing test. If learning-step history being
memory-invisible at graduation (`ReviewTransitionEngine.kt:273-288` calls
`engine.initialState(graduationRating)` regardless of intermediate failures) is
intentional, document it in AGENTS.md; otherwise route learning answers through
short-term stability like Anki.

**Done when:** all fixes have tests, AGENTS.md updated where behavior is intentional,
golden timelines regenerated deliberately if outputs change, `ciFast` green.

---

## Batch 5: Update & release security

### Goal 18: Verify APK signing certificate before committing an update install

**Problem:** The auto-updater verifies SHA-256 against the release asset
(`app/src/main/kotlin/dev/bee/kanjianki/update/GitHubUpdater.kt:72-87`) but never
verifies the downloaded APK's signing certificate against the running app's cert. If
the GitHub account/release pipeline is compromised, both the APK and its `.sha256` are
attacker-controlled. Android rejects mismatched signatures at commit time, but failing
fast avoids keeping a hostile APK in cache.

**Goal:** Before `session.commit` (`GitHubUpdater.kt:699-734`), compare the archive's
signing certs (`PackageManager.getPackageArchiveInfo(..., GET_SIGNING_CERTIFICATES)`)
against the running package's certs; on mismatch, delete the cached APK and record a
permanent failure. Also replace the reflection call to `getPackageArchiveInfo`
(`GitHubUpdater.kt:753-775`) with a direct API call. Add unit tests with mismatched
certs and verify the failure path clears `pendingApkName` so `installCachedPendingUpdate`
does not retry a hostile/broken APK on every `onResume`.

**Done when:** cert check gates install, reflection removed, failure clears pending
state, tests pass, `ciFast` green.

### Goal 19: Harden the update download path and remove dead attack surface

**Problem:**
- No download size cap in `download()` (`GitHubUpdater.kt:582-599`); a malformed
  release asset can fill the cache partition.
- `ApkContentProvider` (`app/src/main/kotlin/dev/bee/kanjianki/update/ApkContentProvider.kt`)
  appears to be dead code in main — `uriFor` is referenced only by instrumentation
  tests; the installer streams into the session directly. Manifest entry at
  `app/src/main/AndroidManifest.xml:41-45`.

**Goal:** Add a sane max-size cap to `download()` (e.g., derived from the release
asset's declared size plus margin) with a test. Remove `ApkContentProvider`, its
manifest entry, and its instrumented test (or keep the test asserting it no longer
exists/exports).

**Done when:** oversized downloads abort cleanly, provider removed, `ciFast` green.

> PARTIAL (deep-review pass 2026-07-03): Landed the locally-verifiable parts:
> (1) a meta-test in tools/test_release_workflows.py asserting every
> REQUIRED_CHECKS name is a real job `name:` in .github/workflows/*.yml (catches
> the rename-drift the goal warns about); (2) shell-injection hygiene — the
> android-instrumented fixture step now passes step outputs via `env:` instead
> of an inline expression; (3) scoped the release fixture call down from
> `secrets: inherit` to just GRADLE_ENCRYPTION_KEY (declared on the reusable
> workflow's workflow_call). STILL OPEN and requiring a live Actions run to
> validate (per AGENTS.md, workflow behavior changes must be watched to
> completion): making quality-status treat a *missing* required check as a
> failure unless the diff provably misses that workflow's path filters (+ grace
> period), and running gradle/actions/wrapper-validation on push. Deferred — not
> safe to land blind here.

### Goal 20: Make the release quality gate fail closed and validate the wrapper on push

**Problem (workflow security/reliability):**
- `quality-status` in `.github/workflows/android-release.yml:317-321,346-347` treats a
  *missing* check-run as skipped ⇒ success. A renamed check or a race right after push
  means releases can publish without SonarQube/CodeQL actually gating.
- Gradle wrapper validation only runs on PRs (`.github/workflows/android-ci.yml:166-184`),
  but the release path is push-to-main → auto-release, and the release `validate` job
  runs `./gradlew` with signing secrets in env (`android-release.yml:217-238`). A
  tampered wrapper pushed to main executes with signing secrets unvalidated.

**Goal:**
- In `quality-status`: require each expected check to exist unless the release diff
  provably misses that workflow's path filters; add a minimum grace period before
  accepting absence. Add a meta-test in `tools/test_release_workflows.py` asserting the
  `REQUIRED_CHECKS` names (`android-release.yml:293`) appear as job `name:` values in
  their source workflows.
- Run `gradle/actions/wrapper-validation` on push (or inside the release `validate`
  job).
- Scope `secrets: inherit` on the fixture call (`android-release.yml:386`) down to
  `GRADLE_ENCRYPTION_KEY`.
- Pass `steps.ankidroid.outputs.apk_path` via `env:` instead of inline expression in
  `.github/workflows/android-instrumented.yml:122` (shell-injection hygiene; the
  screenshot workflows already do this correctly).
Push and watch the first Actions run to completion per AGENTS.md.

**Done when:** fail-closed gate + wrapper validation on push are live and a real
Actions run passes; meta-tests updated; `ciFast` green.

> DEFERRED (deep-review pass 2026-07-03): Both changes require validation this
> environment cannot provide — enforcing signing inside the release variant and
> enabling R8 must be verified with the full release gate (`ciRelease` + a temp
> keystore), apksigner verify, and an emulator smoke test (ML Kit digital ink +
> Compose keep-rule audit). Landing R8 without that smoke test risks shipping a
> broken minified APK to every user via the self-updater. Defer to an on-device
> pass.

### Goal 21: Close the unsigned-release escape hatch and enable R8

**Problem:**
- The signing guard string-matches `gradle.startParameter.taskNames` for
  `assembleRelease`/`bundleRelease` (`app/build.gradle.kts:41-48`). `./gradlew build`,
  camel-case abbreviations (`aR`), and IDE task paths bypass it and silently produce an
  *unsigned* release APK. Reading `startParameter` at configuration time also causes
  configuration-cache misses.
- `isMinifyEnabled = false` (`app/build.gradle.kts:101`) — release ships un-minified
  despite an effectively empty, "reflection-free" `app/proguard-rules.pro`. For a
  self-updating app, APK size costs every user on every update.

**Goal:**
- Enforce signing inside the release variant itself (e.g., a `doFirst` check on the
  package/validate task or `onVariants` failure when no signing config), removing the
  task-name string match.
- Enable R8 (`isMinifyEnabled = true`, `isShrinkResources = true`) for release. Run the
  full release gate (`ciRelease` with the temp keystore per AGENTS.md), verify the APK,
  and smoke-test the release build in an emulator (ML Kit digital ink + Compose need a
  keep-rule audit). Add any required keep rules with comments.

**Done when:** `./gradlew build` cannot produce an unsigned release artifact silently;
R8 release APK passes `ciRelease`, apksigner verify, and an emulator smoke test.

---

## Batch 6: Structural / build system

### Goal 22: Introduce a Gradle version catalog

**Problem:** No `gradle/libs.versions.toml` (CI path filters at
`.github/workflows/android-ci.yml:27,52` already reference it; `docs/dependency-updates.md`
anticipates it). Duplication: JaCoCo `"0.8.14"` declared 9 times across module build
files; JUnit version smuggled through `gradle.properties:6` and read via
`providers.gradleProperty("junitVersion")` in 8 build files; app version `0.4.33`/`4033`
hardcoded in both root `build.gradle.kts:16` and `app/build.gradle.kts:30-31`.

**Goal:** Create `gradle/libs.versions.toml` covering all plugin and library versions;
migrate every module; collapse the JaCoCo/JUnit duplication; single-source the app
version fallback (derive from latest git tag or one property). Keep dependency
verification metadata (`gradle/verification-metadata.xml`) in sync.

**Done when:** no version literals duplicated across build files, Renovate-compatible
catalog in place, `ciFast` + `ciQuality` green.

### Goal 23: Extract a convention plugin for JVM modules

**Problem:** The seven JVM module build files (core, domain, sync-domain,
dictionary-core, writing-core, update-core, fsrs-java) are ~95% identical (toolchain
17, JUnit4, JaCoCo report + 100% class-coverage verification wired into `check`);
drift already exists (`writing-core/build.gradle.kts:25-32` excludes `*WhenMappings*`).

**Goal:** Create `build-logic/` with a `kani.kotlin-library-conventions` plugin
encapsulating the shared config (with an extension point for coverage excludes); apply
it in all seven modules and delete the duplication. Depends on Goal 22 (catalog) being
done first or done together.

**Done when:** module build files shrink to plugin application + module-specific deps;
`ciFast` green.

> STATUS (deep-review pass 2026-07-03): Documented the compiler-version coupling
> in AGENTS.md ("Kotlin compiler version coupling") per the goal's "or an
> explicit documented pairing" option — the catalog shares one `kotlin` ref for
> kotlin-jvm + kotlin-compose, and :app tracks AGP's embedded kotlinc. Applying
> KGP to :app to fully unify compilers, and verifying Renovate/AGP-embedded
> behavior on a live run, is DEFERRED (needs an actual AGP bump + CI run to
> validate rather than a blind change).

### Goal 24: Align Kotlin compiler versions between library modules and :app

**Problem:** Root `build.gradle.kts:5-6` pins `org.jetbrains.kotlin.jvm` and
`kotlin.plugin.compose` at 2.0.21 (Oct 2024), while `:app` applies no Kotlin plugin
and uses AGP 9.1.0's built-in kotlinc (see `built_in_kotlinc` intermediates paths at
root `build.gradle.kts:29,40-41`). Library modules and the app compile with different
Kotlin compilers, and the Compose compiler plugin version is decoupled from the
compiler actually building the app — a latent break on AGP bumps.

**Goal:** Determine AGP 9.1's embedded Kotlin version, align/upgrade KGP and the
Compose compiler plugin to match (or apply KGP to :app explicitly), and document the
coupling. Verify Renovate will keep them moving together.

**Done when:** one Kotlin compiler version across modules (or an explicit documented
pairing), full build + `ciQuality` green.

> DEFERRED (deep-review pass 2026-07-03): This renames `fsrs-java`→`fsrs`, folds
> `domain`, demotes core's `api` deps, and re-points CodeQL to compileKotlin
> targets. Module-path renames ripple through .github workflows, Sonar
> java.binaries/coverage paths, and CodeQL's compile target list, and the goal's
> "Done when" explicitly requires watching the first CodeQL Actions run green
> after the change. That live validation isn't available in this environment;
> landing a module rename blind risks silently breaking CodeQL/Sonar extraction.
> Defer to an on-CI pass.

### Goal 25: Clean up module boundaries and split packages

**Problem:**
- Split package `dev.bee.kanjianki.core` spans three modules (`core`, `domain`,
  `dictionary-core`); `writing-core` owns `dev.bee.kanjianki.core.study`.
- `domain` module contains one production file (`StudyRatings.kt`).
- `core/build.gradle.kts:51-53` exposes `dictionary-core`, `domain`, `sync-domain` as
  `api`; `:app` consumes `syncdomain` types without declaring the dependency, and
  `app/build.gradle.kts:180` declares `dictionary-core` both directly and via core's api.
- `fsrs-java` is 100% Kotlin (misleading name); the CodeQL workflow invokes
  `compileJava` on all-Kotlin modules (`.github/workflows/codeql.yml:70-81`) — works
  only transitively.

**Goal:** Give each module its own package; fold `domain` into `sync-domain` (or a real
shared module); demote core's `api` deps to `implementation` and declare direct deps in
app; rename `fsrs-java` → `fsrs` and switch CodeQL to uniform `compileKotlin` targets
with a comment. Update Sonar/CI path references. Watch the first CodeQL Actions run
after the change per AGENTS.md.

**Done when:** no split packages, enforceable layering, CodeQL run green.

### Goal 26: Remove repo cruft and consolidate docs

**Problem:** `.sisyphus/run-continuation/ses_*.json` (an AI session dump) is tracked in
a public repo; planning docs are scattered across `new/`, `plans/`, `suspended.md`,
root, plus two doc roots (`docs/` and `documentation/`). `.github` path filters
reference a nonexistent `dependabot.yml` (`android-ci.yml:29`), and the push vs PR
path-filter lists are asymmetric (README.md only in one).

**Goal:** Review `.sisyphus/run-continuation/` contents for sensitive data, remove and
gitignore `.sisyphus/`; consolidate `new/`, `plans/`, `suspended.md` under `docs/plans/`;
merge `documentation/` into `docs/`; fix the stale/asymmetric path filters. Update any
references in AGENTS.md/workflows.

**Done when:** repo root has one docs tree, no AI session state tracked, path filters
accurate, CI green.

---

## Batch 7: CI & test hygiene

> PARTIAL (deep-review pass 2026-07-03): Added the missing `ci/tests` unittest
> step to android-ci.yml's asset-tests job so CI's Fast confidence gate covers
> the same Python surface as local ciFast (which ran it via testCiScripts), and
> added a meta-test asserting all three suites (tools/, scripts/tests/,
> ci/tests/) run in CI. DEFERRED: collapsing the module/task inventory
> duplicated across ≥5 places into aggregate Gradle tasks (ciFastJvm/
> ciFastAppUnit) that all workflows call — that refactor's payoff is validated by
> watching a live Actions run, and touches multiple workflows at once.

### Goal 27: Run ci/tests Python suite in CI and single-source the CI task inventory

**Problem:** `android-ci.yml:149-164` runs `tools/` and `scripts/tests/` but never
`ci/tests/` (`test_run_ankidroid_fixture.py` etc.), so CI's "Fast confidence gate" is
not the same surface as local `ciFast` (which runs them via `testCiScripts`,
root `build.gradle.kts:119-123,154`) — contradicting AGENTS.md. The module/task list is
also duplicated in ≥5 places: root `build.gradle.kts:125-155`, `android-ci.yml:75`,
`sonarqube.yml:224-245`, `.github/scripts/sonar-full-coverage.sh:23-46`,
`codeql.yml:70-81`.

**Goal:** Add the `ci/tests` unittest step to the `asset-tests` job; define aggregate
Gradle tasks (e.g., `ciFastJvm`, `ciFastAppUnit`) and have all workflows call the
aggregates instead of hand-listing tasks; extend `tools/test_release_workflows.py` to
assert the workflows call the aggregates. Watch the first Actions run.

**Done when:** CI surface == local `ciFast` surface, one source of truth for the task
inventory, meta-test enforces it, Actions green.

> DEFERRED (deep-review pass 2026-07-03): Goals 28-31 all gate on validation this
> environment cannot provide — each requires watching a live GitHub Actions run
> (and Goals 28/31 an emulator connected/fixture run) to confirm green, per
> AGENTS.md's rule that workflow changes must be watched to completion. Goal 30's
> SHA-pinning also needs authoritative tag→commit resolution for every action.
> Goal 31's method move out of the 3,489-line instrumented monolith is only
> "done" when the fixture workflow passes on dispatch. These are best done in an
> on-CI pass; the locally-verifiable Goal 27 core (ci/tests parity + meta-test)
> landed above.

### Goal 28: Give the instrumentation suite a scheduled run

**Problem:** ~575 instrumentation tests are compile-only on every normal path; the
nightly/release fixture runs just 3 classes
(`ci/scripts/run_ankidroid_fixture.sh:140`); the full connected suite runs only on
manual Sonar `full_coverage` dispatch. The 80+ Compose UI test files can silently rot.

**Goal:** Add a scheduled (nightly or weekly) `connectedDebugAndroidTest` workflow job
reusing the AVD-snapshot caching from `android-instrumented.yml:90-110`, with the same
transient-failure retry pattern, uploading the test report artifact. Also emit a
`::warning::` annotation in `run_ankidroid_fixture.sh` when a pass required retries so
flake frequency is visible.

**Done when:** scheduled connected run exists and its first run passes (or produces an
actionable report); retry visibility annotation in place.

### Goal 29: Make Sonar main-branch coverage deterministic

**Problem:** `sonarqube.yml:75-138` selects tests from `git diff ${{ github.event.before }}`
with a shallow fetch that can fail (fallback compares sha to itself, line 243-245), and
`existingSonarPaths` (root `build.gradle.kts:43-45`) silently drops missing report
paths — main-branch coverage jitters with push shape and can hollow out invisibly. The
exclusion list at `build.gradle.kts:61-86` also references nonexistent
`app/src/main/java/**` paths, and hardcoded AGP intermediates paths break silently
across AGP upgrades.

**Goal:** For pushes to `main`, run the full deterministic task set instead of the
changed-area subset; add an assertion that at least the app-module class dirs resolved
(fail loudly instead of silently dropping); prune dead exclusion patterns; dedupe the
Sonar PR-argument logic between `sonarqube.yml:204-223` and
`.github/scripts/sonar-full-coverage.sh:4-21` into one script with a mode flag. Add the
Robolectric android-all cache to the Sonar workflow (currently only in the `app-unit`
CI leg, `android-ci.yml:131-136`). Watch the first Sonar Actions run.

**Done when:** main-branch Sonar coverage stable across pushes, missing-path failures
loud, duplication removed, Sonar run green.

### Goal 30: Deduplicate workflow boilerplate

**Problem:** The "Install Android SDK packages" step is copy-pasted 7 times;
`android-screenshots.yml` and `android-theme-screenshots.yml` are near-identical;
AnkiDroid 2.24.0 APK is re-downloaded from GitHub every fixture run
(`android-instrumented.yml:53-74`); AVD snapshot cache key is static
(`avd-api-35-aosp-atd-x86_64-pixel2-v1`, line 97) and silently pins a stale image if
inputs change; `codeql.yml:35` has a dead fork-PR guard with no `pull_request` trigger;
CodeQL's forced clean compile doesn't pass `--parallel`; action pinning is mixed
(some SHA-pinned, most mutable tags) in a repo with signing secrets.

**Goal:** Extract a composite action for SDK install; merge the two screenshot
workflows behind an input; `actions/cache` the pinned AnkiDroid APK; derive the AVD
cache key from api-level/target inputs; remove the dead CodeQL condition; add
`--parallel` to the CodeQL compile; SHA-pin all actions. Watch first runs of each
changed workflow.

**Done when:** each duplication removed, all changed workflows green on Actions.

### Goal 31: Split the release-gate live test out of the 3,489-line instrumented monolith

**Problem:** `MainActivityInstrumentedTest.kt` mixes fake-provider UI flows and the
live-provider release gate in one 3,489-line class; the release path selects it by
method-name string in `ci/scripts/run_ankidroid_fixture.sh:140`, coupling the release
gate to unrelated churn.

**Goal:** Move `testManualSyncButtonWorksAgainstLiveAnkiDroid` (and any live-only
helpers) into a dedicated live test class; update `run_ankidroid_fixture.sh`, the
release fixture-gate path list (`android-release.yml:145-172`), AGENTS.md's documented
command, and `tools/test_release_workflows.py` / `ci/tests` accordingly.

**Done when:** release gate references the dedicated class, fixture workflow passes on
dispatch, docs updated, `ciFast` green.

---

## Batch 8: Compose & misc polish

### Goal 32: Consolidate the flashcard swipe into one gesture system

**Problem:** Two parallel gesture systems feed `FlashcardGesturePolicy.release`:
activity-level `dispatchTouchEvent` interception (`MainActivityBase.kt:343-348` →
`MainActivityStudyFlashcardInteraction.kt:65-139`) and a Compose `pointerInput` handler
(`MainActivityStudyFlashcardCompose.kt:145-191`). A swipe crossing card → action bar
can be evaluated by both; the activity layer can swallow `ACTION_UP` from Compose
(buttons left visually pressed). Also a state write during composition:
`swipeFeedback?.thresholdPx = ...` in the composable body
(`MainActivityStudyFlashcardCompose.kt:152`).

**Goal:** Remove the activity-level interception and keep the Compose handler as the
single source; move the threshold write into `SideEffect`/`pointerInput`. Keep the
existing swipe Compose tests passing and add one for the card→action-bar crossing case.

**Done when:** one gesture path, no composition-time state writes, UI tests green.

### Goal 33: Stop re-rendering routes where recomposition suffices

**Problem:** Dialogs (`pendingHomeSyncDialog`, `pendingUpdatePermissionDialog`) are
shown by calling `rerenderLatestHomeRoute()` (`MainActivityHome.kt:196-262`) — a full
`setContent` teardown. The theme is also read from SQLite on the main thread on *every*
navigation (`MainActivityShellHost.kt:12,47`).

**Goal:** Drive dialog visibility and the study badge via `mutableStateOf` (the pattern
already exists in `SyncProgressPanel.state`) instead of route re-render; cache
`appThemeChoice()` in memory and invalidate on save.

**Done when:** dialogs appear without recomposing the whole route, no main-thread
theme DB read on navigation, UI tests green.

### Goal 34: Replace uninitialized-null hacks with lateinit

**Problem:** `uninitialized(): T = null as T` for `store`/`gateway`
(`MainActivityBase.kt:471-473`) plus catching `NullPointerException` as control flow in
`MainActivityLifecycle.storeOrNull` (`MainActivityLifecycle.kt:51-56`).

**Goal:** Use `lateinit var` + `::store.isInitialized`. Remove the NPE-catch. (If Goal
8 moved these into a retained holder, do this there.)

**Done when:** no `null as T` casts or NPE control flow remain; `ciFast` green.

### Goal 35: Fix TalkBack container pollution and edge-to-edge deprecations

**Problem:**
- `MainActivityShellFrame` sets `contentDescription = "Kani shell <route>"` on the root
  `Box` (`MainActivityShell.kt:92-94`) and per-route on scroll columns (lines 162-164);
  TalkBack announces these container descriptions. `testTag` alone suffices for tests.
- `styleSystemBars` sets `window.statusBarColor`/`navigationBarColor`
  (`MainActivityUiSupport.kt:25-33`); with targetSdk 36 these are deprecated no-ops
  under enforced edge-to-edge on Android 15+.

**Goal:** Replace container contentDescriptions with `testTag` (update any tests that
matched by description); migrate bar styling to drawing behind bars +
`isAppearanceLight*` via `WindowInsetsControllerCompat`.

**Done when:** TalkBack no longer announces shell containers, no deprecated bar-color
calls, UI tests green.

### Goal 36: Move untranslated UI strings into the localized copy layer

**Problem:** Localization bypasses Android resources by design (`*TextCopy` objects
branching on `Locale.getDefault()`), but stragglers are hardcoded English-only:
`MainActivityBase.kt:511-535` (`LABEL_BACK_HOME` etc.) and mixed inline strings in
`SyncProgressPanel.kt:113-119`.

**Goal:** Migrate the `MainActivityBase` `LABEL_*` constants and `SyncProgressPanel`
inline strings into the `core` `*TextCopy` layer with Japanese translations, following
the existing pattern (e.g., `HomeTextCopy`). Update tests that assert the literals.

**Done when:** no user-visible English-only literals in the app layer for these
surfaces; `ciFast` green.

> STATUS (deep-review pass 2026-07-03): Done — parseThresholdInput uses
> toIntOrNull, searchKanjiInventory escapes LIKE wildcards (ESCAPE '\'),
> StudyFontVariants/MainActivityGames use java.util.Random, the
> queryCardsForNotes cursor map is built inside use{}, tagNoteArchived catches
> per-note failures, and the synthetic cardId + RemovalSummary.deletedNotes=0
> are documented. NOT actionable: dropping the manifest
> fullBackupContent/dataExtractionRules attributes — Android lint MANDATES both
> at minSdk 26 (dataExtractionRules for API 31+, fullBackupContent for pre-31),
> so removing either fails lint. Left as-is (allowBackup=false already disables
> backup; the rule files exclude everything).

### Goal 37: Small defect sweep (crash guards, escaping, randomness, cursor leak)

**Problem (batch of small fixes):**
- `MainActivitySettings.parseThresholdInput` (`MainActivitySettings.kt:337-339`) calls
  `.toInt()` on raw user text — `NumberFormatException` on empty input.
- LIKE wildcard leakage: `searchKanjiInventory`
  (`LocalStoreInventory.kt:216-220`) doesn't escape `%`/`_` — add `ESCAPE '\'`.
- `SecureRandom` for font-variant selection (`StudyFontVariants.kt:8`) and game
  shuffling (`MainActivityGames.kt:13`) — use `Random` (entropy blocking risk).
- Cursor leak on exception in `AnkiDroidCardReader.queryCardsForNotes`
  (`AnkiDroidCardReader.kt:156-175`): map construction between `resolver.query` and
  `cursor.use` — move it inside the `use`.
- `RemovalSummary.deletedNotes` always 0 (`AnkiDroidArchiveCleanup.kt:40`) — remove or
  wire up.
- Per-note catch in `AnkiDroidArchiveCleanup.tagNoteArchived` loop
  (`AnkiDroidArchiveCleanup.kt:64-81`) so one provider failure doesn't abort tagging
  the remaining notes.
- Add a comment at the synthetic `cardId = noteId*1000+ord` site
  (`AnkiDroidCardReader.kt:219`) explaining the `_id is unknown` rationale (currently
  only in AGENTS.md).
- `AndroidManifest.xml:21-23`: `allowBackup="false"` renders
  `fullBackupContent`/`dataExtractionRules` inert — drop the rules or enable scoped
  backup deliberately.

**Goal:** Fix each item with a matching unit test where behavior changes.

**Done when:** all items addressed, tests added, `ciFast` green.

### Goal 38: Replace vararg positional constructors with typed builders/data classes

**Problem:** `StudyItem` takes `vararg rest: Any?` and dispatches on argument count
(8 accepted shapes: 5/9/13/17/18/19/25/26 args) with `as?` casts that silently convert
type errors into defaults (`core/src/main/kotlin/dev/bee/kanjianki/core/RecordsStudyModels.kt:157-165,694-748`).
The same pattern exists in `ReviewRequest`
(`RecordsSchedulerModels.kt:193-228`), `TaskMemory` (`RecordsStudyModels.kt:9-64`),
`LearningRepeat`, `AdaptiveLoadPlan`, and `Settings`
(`RecordsSyncModels.kt:11-53`). A silently mis-ordered argument becomes wrong runtime
state. This is the biggest latent-bug generator in core. Also related structural debt:
the `RecordsSchedulerModels : RecordsStudyModels : ... : RecordsBase` inheritance chain
is namespacing-by-inheritance, and `BridgeScheduler` has 6 `applyReview` / 6
`nextSession` / 4 `seedQueue` telescoping overloads
(`core/src/main/kotlin/dev/bee/kanjianki/core/BridgeScheduler.kt:137-326`) even though
`ReviewApplication` already exists.

**Goal (large; can be split per type):**
- Replace each vararg constructor with named-parameter constructors / builders (the
  existing `StudyItemBuilder` shows the target pattern); delete the arg-count dispatch.
- Migrate all call sites (app + tests).
- Deprecate the telescoping `BridgeScheduler` overloads in favor of
  `ReviewApplication`; migrate callers.
- Optionally flatten the Records* inheritance chain into top-level files/packages.

**Done when:** no `vararg rest: Any?` constructors remain in core, all call sites use
named parameters, `ciFast` green, golden timelines unchanged.
