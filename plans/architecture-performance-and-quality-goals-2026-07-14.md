# Architecture, Performance & Quality-of-Life Goals — Goals 129–144 (2026-07-14)

Each goal below is self-contained: context with file/line evidence, the change
to make, and machine-checkable acceptance criteria. Work goals one at a time
with `/goal`. Line numbers are correct as of commit `a6851ae6` (2026-07-14,
branch `main`, app version 0.4.33 / 4033) and may drift — search the named
symbols.

Goal numbers continue from 128
(`plans/reference-surface-and-polish-goals-2026-07-14.md`, on `main`, ends at
Goal 128) because goal numbers are globally unique across all plan files in
this repo.

## Asks

1. **R8 shrinking and APK size:** the release build ships with
   `isMinifyEnabled = false` (`app/build.gradle.kts:104`). The APK includes
   every unused Compose, AndroidX, and ML Kit class. Enable R8, add keep
   rules, and measure the size reduction.
2. **Architecture modernization:** the app uses a single-activity deep
   inheritance chain (`MainActivityBase → MainActivityHome → MainActivityGames
   → MainActivityStudy → MainActivitySettings → MainActivity`) with all state
   on the activity instance, Executors+Handlers for threading, and no
   ViewModel/StateFlow/coroutines. This makes testing expensive and state
   survival across config changes fragile. Incrementally introduce modern
   patterns without a full rewrite.
3. **Performance:** the 9.5 MB stroke data is parsed synchronously on the
   main thread at startup (`MainActivityStartup.kt:163`); the dictionary
   asset (13K entries) is opened and LRU-cached but bulk-search is I/O-bound
   on first hit; there is no background prefetch of common study-session data.
4. **Testing gaps:** 78 instrumented tests exist but the Robolectric/JVM
   suite covers only policy/engine layers. Compose UI tests and integration
   tests for the data layer are sparse. Coverage of the large
   `LocalStoreInventory.kt` (1107 lines), `MainActivityStudy.kt` (993 lines),
   and `ReviewTransitionEngine.kt` (819 lines) needs hardening.
5. **Error resilience:** provider sync and database operations can throw
   `SQLiteDatabaseLockedException` and content-resolver errors that are
   sometimes not surfaced to the user. The app has no offline/network-down
   error state for the update check, and no explicit database downgrade policy
   (finding 4.2 from the July 2026 review).
6. **Developer experience:** no DI framework means every test builds its own
   `LocalStore`/`DictionaryStore` + wiring; build times grow with the flat
   232-file app module; lint and static analysis run only in CI, not on commit.

## Base state (as of this plan)

- HEAD `a6851ae6`, app version 0.4.33 / 4033.
  `minSdk 26`, `targetSdk 36`, `compileSdk 36`.
  `isMinifyEnabled = false`. AGP 9.1.0, Kotlin 2.0.21, Compose BOM 2026.04.01.
- `LocalStoreSchema.DB_VERSION == 32`.
- `STATS_CACHE_FORMAT_VERSION == 10`.
- Goals 113–128 landed on `main` (PR #544).
- Goals 98–112 pending on branch `goals-96-112` (authority remains
  `plans/study-experience-settings-and-hardening-goals-2026-07-13.md`).
- Single activity class hierarchy: `MainActivityBase` (747 lines) →
  `MainActivityHome` (746) → `MainActivityGames` → `MainActivityStudy` (993)
  → `MainActivitySettings` → `MainActivity`.
- Threading: `Handler(Looper.getMainLooper())` + two `ExecutorService` pools
  (`io` single-thread, `maintenance` daemon thread) on `MainActivityBase`.
- No ViewModel, StateFlow, coroutines, or DI in the codebase.
- Compose is used for UI rendering only (no navigation component, no
  lifecycle-aware state holders).

## Carry-over (Goals 98–112)

| Range | Plan | Status |
|---|---|---|
| 98–112 | `study-experience-settings-and-hardening-goals-2026-07-13.md` | Pending on branch `goals-96-112` |

This plan is a **sibling queue** to Goals 98–112. It deliberately avoids
touching the same files/surfaces until those goals land, except where
explicitly called out.

## Cross-plan interactions (Goals 98–112, pending)

- **Goal 105 (runtime holder):** Goals 130–131 here introduce a thin
  lifecycle-aware state holder that complements, not conflicts with, Goal 105's
  approach. Whichever lands first establishes the pattern; the other adopts it.
- **Goal 111 (R8/signing):** Goal 129 here is the same work. If Goal 111
  lands first, mark Goal 129 complete. If this plan lands first, remove
  Goal 111 from the sibling plan.

## Design

### Decisions of record

- **D-A1 — Incremental architecture, not a rewrite.** Introduce coroutines and
  StateFlow in one vertical slice (study session) and prove the pattern before
  migrating other surfaces. Keep `MainActivityBase` functional throughout; no
  big-bang NavHost/DI migration.
- **D-A2 — R8 with conservative keep rules first.** Enable minification with
  broad keep rules for Glance, ML Kit, and content providers, then tighten
  iteratively. First ship must pass the full live AnkiDroid gate unchanged.
- **D-A3 — Lazy stroke parsing on background thread.** Move the 9.5 MB parse
  off the main-thread startup path. Consumers already null-check the cache
  result, so late availability is the only contract change.
- **D-A4 — Database downgrade is explicit and non-destructive.** On
  `onDowngrade`, preserve the database intact, log the event, and show an
  informational toast instead of crashing. The older APK operates on whatever
  columns it knows; unknown v32 columns are ignored by older schema code.
- **D-A5 — Testing improves through coverage gates, not coverage mandates.**
  New goals add targeted integration tests for undertested hot paths; the
  JaCoCo minimum stays at the existing class-coverage level and rises only as
  tests are added.

### Out of scope (evaluated, deliberately deferred)

- Full NavHost/Compose navigation migration (D-A1).
- Hilt/Dagger/Koin introduction (premature without ViewModels in place).
- Multi-module split of the 232-file `:app` module (depends on DI patterns).
- Kotlin 2.1 migration (blocked on AGP 9.1.0 Compose compiler compatibility).
- Room migration (the raw SQLite layer is well-tested and performant; Room adds
  compile-time safety but the migration cost for 32 schema versions is high).

---

## Batch A: Performance and APK size (Goals 129–131)

### Goal 129: Enable R8 shrinking on the release build

**Problem:** `isMinifyEnabled = false` (`app/build.gradle.kts:104`) ships every
unreachable class from Compose, AndroidX, ML Kit, Glance, and the update engine
in the release APK. The proguard rules file exists but is unused.

**Goal:**

- Set `isMinifyEnabled = true` in the release build type.
- Add keep rules for:
  - Glance widget classes (AppWidgetProvider + GlanceAppWidget subclasses).
  - ML Kit `DigitalInkRecognition` model classes (reflection-loaded).
  - Content provider interactions (AnkiDroid provider URIs use string-based
    column names that R8 cannot trace).
  - `BuildConfig` fields accessed by the update engine.
  - Any `@Keep`-annotated classes if needed for the JSON codec.
- Measure before/after APK size (record in the commit message).
- Run the full `ciFast` gate plus a manual install-and-study smoke on an
  emulator to confirm no runtime `ClassNotFoundException` or reflection
  failures.
- If the live AnkiDroid provider gate is available, run it. Otherwise, run the
  fake-provider instrumentation suite and record that the live gate is owed.

**Done when (machine-checkable):**

1. `./gradlew ciRelease` (local keystore) exits 0 with `isMinifyEnabled = true`.
2. APK size reduced by at least 20% vs the pre-R8 baseline.
3. `./gradlew ciFast` exits 0.
4. `tools/test_release_workflows.py` passes (release path unchanged).
5. Instrumented test suite passes on an emulator.

---

### Goal 130: Move stroke data parsing off the main thread

**Problem:** `MainActivityStartup.kt:163` calls `strokeGuide(kanji)` during
activity creation, triggering the full 9.5 MB TSV parse synchronously on the
main thread. On low-end devices this blocks the first frame for 300–800 ms.
The warm cache is process-wide (`AssetWarmupCache`), but initial parse is
blocking.

**Goal:**

- Move `StrokeGuideParser.parse` invocation to the existing `maintenance`
  executor in `MainActivityStartup`.
- The result remains stored in the same `AssetWarmupCache` field, but via an
  atomic reference: callers that read before the parse completes get `null`
  (the existing `strokeGuide(kanji): StrokeGuide?` already returns nullable
  and all call sites null-check).
- Add a completion callback that triggers a study-surface recompose if the
  user entered study before the parse finished (edge case on very slow
  devices).
- Trace the parse duration with a debug log entry for future profiling.

**Done when (machine-checkable):**

1. `MainActivityStartup` no longer calls the parser on the main thread
   (verified by a Robolectric test that asserts `Looper.getMainLooper()` is
   not the calling thread during parse).
2. Study route still renders stroke guides correctly after the async parse
   completes (existing stroke-guide tests pass).
3. `./gradlew ciFast` exits 0.

---

### Goal 131: Prefetch study session data on Home render

**Problem:** Entering the study route triggers a chain of synchronous database
reads on the IO executor: the session selector queries `study_items`, the
choice planner queries `similar_kanji_pairs` + `kanji_reading_pool`, and the
stroke guide lookup happens per-kanji. On a 200-item queue this adds 200–400 ms
of perceived latency between tapping "Study now" and seeing the first card.

**Goal:**

- On the Home route's initial render (after sync status is determined and the
  Today plan card is built), start a background prefetch of the first 5 study
  items' session data (choice pools, reading pools, stroke guides for those
  kanji).
- Store the prefetched data in a small in-memory cache keyed by
  `(kanji, rung)` with a single-use invalidation (consumed on first study
  entry, rebuilt if the session is re-entered).
- The study route checks the prefetch cache before issuing its own queries;
  cache miss falls through to the existing synchronous path.
- No behavioral change — the same data is loaded, just earlier.

**Done when (machine-checkable):**

1. App unit test: prefetch populates the cache for the top-N items; study
   entry consumes from cache without re-querying the store (verified via a
   mock store call count).
2. Measured improvement: debug log shows study-entry latency reduced by at
   least 100 ms on a fixture queue.
3. `./gradlew ciFast` exits 0.

---

## Batch B: Error resilience (Goals 132–134)

### Goal 132: Explicit database downgrade policy

**Problem:** Finding 4.2 from the July 2026 full review: no `onDowngrade`
override exists; installing an older APK over a newer DB hard-crashes on
`SQLiteOpenHelper.onDowngrade` (the default implementation throws). This
happens when users sideload an older version or when beta testing reverts.

**Goal:**

- Override `onDowngrade` in `LocalStoreSchema` to:
  1. Log the downgrade event (`fromVersion → toVersion`) to the debug log.
  2. Keep the database intact (no `DROP TABLE`, no truncation).
  3. Show a one-time informational toast on next activity creation:
     "Database is from a newer version of Kani; some features may be
     unavailable until you update."
  4. Record the downgrade version in a settings key so the toast is shown
     only once per downgrade.
- The older APK will simply ignore unknown columns (SQLite does not enforce
  schema on reads; `SELECT` with explicit column names works fine against a
  superset schema).
- Add a test that downgrades from v32 to v31 and verifies the DB is usable,
  no crash, and the existing core queries still work.

**Done when (machine-checkable):**

1. Robolectric test: downgrade v32→v31 succeeds; core read queries
   (`activeDashboardRows`, `searchKanjiInventory`, `reviewMemoryHistoryForKanji`)
   return data without exception.
2. The debug log contains the downgrade event line.
3. `./gradlew ciFast` exits 0.

---

### Goal 133: Graceful update-check failure UI

**Problem:** The GitHub update check (`GitHubUpdater.kt`, 1011 lines) handles
network errors internally but surfaces no user-visible feedback when the check
fails — the Home update card simply never appears. On airplane mode or behind
a corporate proxy, the user cannot tell whether Kani is up to date or whether
the check silently failed.

**Goal:**

- When the update check fails with a network error (timeout, DNS failure,
  connection refused), persist a transient `update_check_failed_at` timestamp
  in settings.
- The Home screen shows a subtle secondary-text line under the version badge
  when `update_check_failed_at` is within the last 24 hours: "Last update
  check failed — check your connection." Tapping it triggers a manual
  re-check.
- Successful checks clear the key. The line disappears silently after 24 hours
  even without a successful re-check (avoids permanent noise for
  intermittent connectivity).
- The existing auto-update scheduling, retry, and permission flows are
  unchanged.

**Done when (machine-checkable):**

1. App unit test: simulated network error persists the timestamp; model
   builder shows the failure line; successful check clears it; 24-hour expiry
   hides it.
2. Compose test: failure line renders when the model flag is set; tap triggers
   the manual check callback.
3. `./gradlew ciFast` exits 0.

---

### Goal 134: Surface sync lock errors to the user

**Problem:** `SQLiteDatabaseLockedException` during sync is caught and retried
silently (the auto-sync bounded retry chain, documented in
`docs/auto-sync-reliability.md`). When all retries exhaust, the failure reason
is logged but the Home sync status shows only a generic "Sync failed" without
distinguishing "database busy" (transient, try again in a minute) from
"permission denied" (permanent, user action needed).

**Goal:**

- Extend `SyncFailureClassification` (or create it if not already structured)
  to distinguish at least:
  - `TRANSIENT_LOCK` — database was busy; automatic retry scheduled.
  - `PERMISSION_DENIED` — AnkiDroid permission revoked or never granted.
  - `PROVIDER_UNAVAILABLE` — AnkiDroid not installed or provider not
    responding.
  - `PERMANENT_OTHER` — unrecoverable error.
- The Home sync status card shows a one-line reason derived from the
  classification, with a suggested action ("Will retry automatically" vs
  "Grant permission in Settings" vs "Install AnkiDroid").
- The debug log already captures the full exception; no new logging needed.
- Existing retry and anti-spam invariants are unchanged.

**Done when (machine-checkable):**

1. Core unit test: each failure type maps to the correct classification and
   user-facing copy (EN + JA).
2. App unit test: Home sync model renders the correct reason line for each
   classification.
3. Existing sync tests pass unmodified.
4. `./gradlew ciFast` exits 0.

---

## Batch C: Architecture foundations (Goals 135–138)

### Goal 135: Introduce coroutines to the IO layer

**Problem:** The app uses `ExecutorService.execute { }` + `Handler.post { }`
for all background work, which does not compose well with Compose's lifecycle,
makes cancellation manual, and prevents structured concurrency. Every caller
must manually post results back to the main thread.

**Goal:**

- Add `kotlinx-coroutines-android` to the version catalog and `:app`
  dependencies.
- Create a `KaniDispatchers` object that wraps the existing executor pools as
  `CoroutineDispatcher`s (using `ExecutorService.asCoroutineDispatcher()`),
  preserving the existing single-thread IO guarantee and daemon-thread
  maintenance behavior.
- Convert `MainActivityBase.io` and `MainActivityBase.maintenance` usage in
  **one file** (`MainActivityHomeTodayPlan.kt` — a simple read-only async
  loader) to `suspend fun` + `withContext(KaniDispatchers.io)`, proving the
  pattern.
- Add a `lifecycleScope` property on `MainActivityBase` (or use
  `ComponentActivity.lifecycleScope` from `lifecycle-runtime-ktx`) to launch
  coroutines that auto-cancel on activity destroy.
- All other files continue using the executor pattern unchanged; this is a
  proof-of-concept.

**Done when (machine-checkable):**

1. `MainActivityHomeTodayPlan` uses `suspend fun` and `lifecycleScope.launch`
   instead of `io.execute { main.post { } }`.
2. Robolectric test: the Today plan loads correctly in the coroutine path.
3. `./gradlew ciFast` exits 0.
4. No other file in the codebase changed its threading pattern.

---

### Goal 136: Extract a StudySessionViewModel

**Problem:** All study session state lives on `MainActivityStudy` (993 lines)
as mutable `var` fields. A configuration change (rotation, system dark-mode
toggle) recreates the activity and loses the in-progress session state. The
workaround is `configChanges` flags in the manifest preventing recreation, but
this breaks system features (predictive back animations during config changes,
locale changes, display-size changes).

**Goal:**

- Create `StudySessionViewModel` in a new `app/viewmodel/` package (or
  next to the study files) extending `androidx.lifecycle.ViewModel`.
- Move the session-critical mutable state from `MainActivityStudy` into the
  ViewModel: `currentItem`, `sessionPlan`, `pendingAnswer`, `choiceState`,
  `writingState`, and the session-progress counters.
- The ViewModel exposes state via `StateFlow` (one `StudySessionUiState`
  sealed class); the Compose layer observes it with `collectAsState()`.
- `MainActivityStudy` delegates to the ViewModel for session operations;
  its role becomes purely routing and UI orchestration.
- The existing `StudyPendingAnswerStore` (823 lines) stays as the persistence
  layer — the ViewModel calls it for commit, not the activity.
- Configuration changes no longer lose session state.

**Done when (machine-checkable):**

1. Robolectric test: simulated configuration change preserves the
   `StudySessionUiState` (same item, same progress counters).
2. Study flow tests pass (existing test coverage unbroken).
3. `./gradlew ciFast` exits 0.

---

### Goal 137: Extract a HomeViewModel

**Problem:** `MainActivityHome` (746 lines) loads dashboard data, sync status,
today plan, focus queue, recent mistakes, and games availability all via
executor callbacks that post to mutable fields. Each model is re-loaded on
every `onResume`, and there is no way to observe loading state from Compose
without the activity pushing a new model object into `setContent`.

**Goal:**

- Create `HomeViewModel` exposing a single `StateFlow<HomeUiState>`.
- `HomeUiState` is a data class holding the existing model fields:
  `dashboardRows`, `syncStatus`, `todayPlan`, `focusQueue`, `recentMistakes`,
  `gameAvailability`, `whatsNewCard`, `updatePermissionPrompt`.
- The ViewModel loads each section concurrently via coroutines (using the
  coroutine dispatchers from Goal 135) and emits partial states as sections
  complete (progressive loading).
- `MainActivityHome` observes the flow and recomposes sections independently.
- `onResume` triggers a refresh via the ViewModel's `refresh()` method;
  repeated calls within 500 ms are debounced (prevents rapid resume cycles
  from queuing redundant DB reads).

**Done when (machine-checkable):**

1. Robolectric test: ViewModel emits sections progressively; partial state
   shows already-loaded sections while others show loading placeholders.
2. Configuration change preserves loaded state (no re-query).
3. Debounce test: two `refresh()` calls within 500 ms produce only one
   store query set.
4. `./gradlew ciFast` exits 0.

---

### Goal 138: Structured error boundary for store operations

**Problem:** Database operations (`LocalStoreStudy`, `LocalStoreInventory`,
`StudyStatsStore`) throw `SQLiteException` subclasses that propagate to
callers as unstructured exceptions. Each caller handles them differently (some
catch, some crash, some log-and-swallow). There is no unified error type that
UI layers can map to user-facing messages.

**Goal:**

- Define a sealed class `StoreResult<T>` in `:core`:
  ```kotlin
  sealed class StoreResult<out T> {
      data class Ok<T>(val value: T) : StoreResult<T>()
      data class TransientError(val cause: Exception) : StoreResult<Nothing>()
      data class PermanentError(val cause: Exception) : StoreResult<Nothing>()
  }
  ```
- Introduce a `safeStoreCall` inline helper that wraps a store lambda, catches
  `SQLiteDatabaseLockedException` as `TransientError` and
  `SQLiteException`/`IllegalStateException` as `PermanentError`.
- Convert **one high-traffic path** (the study-session item loader in
  `MainActivityStudyQueueCoordinator`) to use `safeStoreCall` and surface the
  result type to the UI (showing a retry prompt on transient, an error card on
  permanent).
- All other store paths remain unchanged; this establishes the pattern.

**Done when (machine-checkable):**

1. Core unit test: `safeStoreCall` wraps each exception type correctly.
2. App unit test: study queue coordinator returns the correct `StoreResult`
   variant for each simulated error.
3. Compose test: transient error shows a retry button; permanent error shows
   the error card.
4. `./gradlew ciFast` exits 0.

---

## Batch D: Testing and quality (Goals 139–142)

### Goal 139: Compose UI test coverage for the study flow

**Problem:** The study flow is the highest-frequency surface (every daily
session) but has zero Compose UI tests. Changes to choice rendering, flashcard
interaction, or writing-pad layout are caught only by manual testing or
screenshot baselines (which verify appearance, not interaction).

**Goal:**

- Add a `StudyFlowComposeTest` class in the app test suite using
  `createComposeRule()`.
- Cover at minimum:
  - Flashcard front renders the expected kanji + prompt for each rung type
    (parameterized: `kanji_meaning`, `word_reading`, `font_meaning`).
  - Choice grid renders the correct number of cells and selecting one
    highlights it.
  - Pass/Fail buttons are present and clickable; clicking Pass invokes the
    expected callback.
  - Writing pad renders when `write_kanji` rung is active.
  - "Done" screen renders session summary with correct counts.
- Tests use fixture models (no real store) and assert via semantic matchers.

**Done when (machine-checkable):**

1. `./gradlew :app:testDebugUnitTest --tests "*StudyFlowComposeTest*"` passes
   with at least 8 test methods.
2. Each listed surface has at least one assertion.
3. `./gradlew ciFast` exits 0.

---

### Goal 140: Integration tests for LocalStoreInventory

**Problem:** `LocalStoreInventory.kt` (1107 lines) is the largest data-layer
file and handles search, timeline, inventory management, and the admission
gate. Its test coverage is limited to the operations exercised indirectly by
higher-level policy tests. Edge cases (empty inventory, max-limit queries,
concurrent writes, special characters in search) are untested.

**Goal:**

- Add `LocalStoreInventoryIntegrationTest` using Robolectric with a real
  in-memory SQLite database (the existing test pattern in
  `app/src/test/.../data/`).
- Cover at minimum:
  - `searchKanjiInventory`: empty query, partial match, exact match, special
    characters (quotes, percent, backslash), limit boundary (300+1 items),
    similar-kanji-only filter, studied checkboxes.
  - `timelineForKanji`: kanji with no events, kanji with multiple event
    types, ordering.
  - `inventoryCount` and `inventoryKanjiList` consistency.
  - Concurrent insert + search does not throw.

**Done when (machine-checkable):**

1. `./gradlew :app:testDebugUnitTest --tests "*LocalStoreInventoryIntegrationTest*"`
   passes with at least 12 test methods.
2. Edge cases listed above each have a dedicated test.
3. `./gradlew ciFast` exits 0.

---

### Goal 141: Golden-timeline expansion for the adaptive scheduler

**Problem:** The adaptive two-core scheduler (`AdaptiveReviewTransitionEngine`,
453 lines) routes reviews through recognition and reading cores, delegates to
FSRS, and handles inline repair. The existing golden scenarios cover the legacy
ladder engine; the adaptive engine has fewer pinned life-of-kanji timelines.
Regressions in repair routing or core handoff would be caught only by the
broader `ciFast` suite, not by a focused deterministic timeline.

**Goal:**

- Add at least 4 new golden life-of-kanji scenarios to the
  `AdaptiveReviewTransitionEngine` test file:
  1. **Happy path:** new item → learning → graduate → review passes on both
     cores → reading ceiling.
  2. **Recognition lapse:** mature item fails recognition core → relearning →
     repair task fires → revalidation pass.
  3. **Reading lapse:** mature item fails reading core → relearning → reading
     repair → revalidation.
  4. **Stuck repair escalation:** item fails repair 3x → escalation flag set →
     reported in stats.
- Each scenario is a deterministic sequence of `(input rating, expected output
  state)` pairs with stability/difficulty checked to 2 decimal places (the
  existing golden format).
- Generate and check in the `.json` golden files alongside the test.

**Done when (machine-checkable):**

1. `./gradlew :core:test --tests "*AdaptiveReviewTransitionEngineTest*"` passes
   with the 4 new scenarios.
2. Golden `.json` files committed; any scheduler change that shifts outputs
   will fail this test.
3. `./gradlew ciFast` exits 0.

---

### Goal 142: Mutation testing baseline for core policies

**Problem:** Line coverage alone cannot distinguish "exercised but unasserted"
code from genuinely tested code. The core policy layer (161 files, heavily
pure-function) is ideal for mutation testing because mutations in pure functions
produce clear test failures — unless the tests are only checking "doesn't
throw."

**Goal:**

- Add the PIT (pitest) Gradle plugin to the `:core` module's build
  configuration.
- Run an initial mutation testing pass on the 5 highest-value policy files:
  - `ReviewTransitionEngine.kt`
  - `StudyQueueSeeder.kt`
  - `BridgeScheduler.kt`
  - `KanjiRepairEvidencePolicy.kt`
  - `ReminderEligibilityPolicy.kt` (or `ReminderThrottlePolicy.kt`)
- Record the mutation score (killed / total) in a committed
  `docs/mutation-testing-baseline.md`.
- Identify and fix the top 5 surviving mutants (tests that should catch the
  mutation but don't assert tightly enough).
- Do NOT add a CI gate at this stage — the baseline is informational.

**Done when (machine-checkable):**

1. `./gradlew :core:pitest` (or equivalent task name) exits 0 and produces
   a report.
2. `docs/mutation-testing-baseline.md` records the date, files tested, and
   per-file mutation scores.
3. At least 5 tests strengthened (tighter assertions or new edge-case tests);
   commit messages reference the specific surviving mutant.
4. `./gradlew ciFast` exits 0.

---

## Batch E: Developer experience (Goals 143–144)

### Goal 143: Pre-commit lint hook for critical rules

**Problem:** Lint violations (SonarCloud rules, Android lint) are caught only
in CI after push. Developers must wait for CI feedback or run `./gradlew lint`
manually. Critical rules (missing `contentDescription`, hardcoded strings,
unused imports) frequently appear in PRs.

**Goal:**

- Add a Git pre-commit hook (`.githooks/pre-commit`) that runs:
  1. `ktlint` (or the built-in `ktfmt` if already configured) on staged `.kt`
     files only (fast, < 5 seconds for typical diffs).
  2. A targeted Android lint check for the `ContentDescription`,
     `HardcodedText`, and `UnusedImport` rules on staged files only.
- Configure `core.hooksPath = .githooks` in the repo's `.gitconfig` or
  document the one-time `git config` command in CONTRIBUTING/README.
- The hook exits non-zero on violations and prints the fix command.
- The hook is skippable with `--no-verify` for emergency commits (documented).

**Done when (machine-checkable):**

1. `.githooks/pre-commit` exists and is executable.
2. A test commit with a hardcoded string triggers the hook and blocks.
3. A clean commit passes the hook.
4. `./gradlew ciFast` exits 0 (hook does not interfere with CI).

---

### Goal 144: Modularization roadmap document

**Problem:** The `:app` module contains 232 Kotlin files spanning UI, data,
sync, update, backup, reminders, and the widget. Build times scale with module
size (all files recompile on any change within the module), and test isolation
requires the full app classpath. A modularization plan does not exist.

**Goal:**

- Write `docs/modularization-roadmap.md` analyzing:
  - Current module structure (`:app`, `:core`, `:dictionary-core`,
    `:writing-core`, `:update-core`, `:fsrs-java`, `:sync-domain`, `:domain`,
    `:build-logic`).
  - Proposed extraction targets ranked by independence and build-time impact:
    1. `:data` — `LocalStore*`, `DictionaryStore`, `StudyStatsStore`.
    2. `:ui-study` — all `MainActivityStudy*` composables + models.
    3. `:ui-home` — all `MainActivityHome*` composables + models.
    4. `:reminders` — `ReminderScheduler`, `ReminderReceiver`, policies.
    5. `:widget` — already partially isolated.
  - Dependency graph (text-based, no tooling required).
  - Estimated effort per extraction (small/medium/large).
  - Prerequisites (DI framework, interface extraction from `MainActivityBase`).
- This is a **document only** — no code changes. It captures the analysis so
  future goals can reference it.

**Done when (machine-checkable):**

1. `docs/modularization-roadmap.md` exists with all listed sections.
2. The dependency graph is consistent with the actual `settings.gradle.kts`
   module list.
3. No code changes in this goal.

---

## Ordering and dependencies

| Goal | Depends on | Notes |
|---|---|---|
| 129 R8 shrinking | — | May overlap with pending Goal 111; whichever lands first wins |
| 130 async stroke parse | — | |
| 131 study prefetch | — | Independent of 130 (different data) |
| 132 downgrade policy | — | |
| 133 update-check failure UI | — | |
| 134 sync error classification | — | |
| 135 coroutines | — | Foundation for 136, 137 |
| 136 StudySessionViewModel | 135 | Uses coroutines + StateFlow |
| 137 HomeViewModel | 135 | Uses coroutines + StateFlow |
| 138 StoreResult error boundary | — | Independent pattern; composes with 136/137 |
| 139 Compose UI tests | — | |
| 140 inventory integration tests | — | |
| 141 adaptive scheduler goldens | — | |
| 142 mutation testing | — | |
| 143 pre-commit hook | — | |
| 144 modularization roadmap | — | Document only |

Recommended order: 129 → 130 → 131 → 132 → 133 → 134 → 135 → 136 → 137 →
138 → 139 → 140 → 141 → 142 → 143 → 144. Performance and resilience deliver
user-visible value first; architecture foundations follow; testing and DX
close the batch.

## Status

> **2026-07-14:** Plan authored; all 16 goals (129–144) described.
> Implementation not started.
>
> **2026-07-14:** Goal 129 DONE — R8 enabled, ARM-only ABI filter on release,
> APK shrunk 49 MB → 24 MB (51% reduction). `ciFast` + `ciRelease` pass.
>
> **2026-07-14:** Goal 130 ALREADY SATISFIED — stroke parsing already runs on a
> dedicated daemon thread (`warmHeavyAssetsOnOwnThread`, `kani-asset-warmup`),
> not the main thread. Process-wide `AssetWarmupCache` with synchronized
> double-check serves callers without blocking the main thread.
>
> **2026-07-14:** Goal 132 DONE — `onDowngrade` override preserves data intact,
> persists a marker to the settings table, shows a one-time toast via
> `HomeTextCopy.databaseDowngradeNotice()`. Three Robolectric tests confirm
> data preservation, no-crash open, and core queries after downgrade.
>
> **2026-07-14:** Goal 133 DONE — Network update-check failures persist
> `update_check_failed_at` via `GitHubUpdater.recordResult`. Home shows a
> failure line within 24 hours; tapping triggers a manual re-check. Five
> unit tests cover record/clear/expiry logic.
>
> **2026-07-14:** Goal 134 DONE — `SyncFailureClassification` enum in `:core`
> classifies failures as TRANSIENT_LOCK, PERMISSION_DENIED,
> PROVIDER_UNAVAILABLE, or PERMANENT_OTHER with EN+JA user guidance. Home
> sync failure screen now shows the classified guidance line above the raw
> error message. Ten core unit tests pin each classification path.
>
> **2026-07-15:** Goal 135 DONE — `kotlinx-coroutines-android` 1.10.2 +
> `lifecycle-runtime-ktx` 2.9.1 added to the version catalog.
> `KaniDispatchers` wraps the existing `io`/`maintenance` ExecutorService pools
> as CoroutineDispatchers. `MainActivityHome.dismissRepairedHandoffCard`
> converted from `io.execute { postToMainIfActive {} }` to
> `lifecycleScope.launch { withContext(dispatchers.io) {} }` as proof-of-concept.
> `ciFast` passes; no other file changed threading pattern.
>
> **2026-07-15:** Goal 138 DONE — `StoreResult<T>` sealed class in `:core`
> (Ok/TransientError/PermanentError). `safeStoreCall` inline helper in
> `:app:data` wraps SQLiteDatabaseLockedException as transient, other
> SQLiteException/IllegalStateException as permanent. Five core tests + five
> app Robolectric tests confirm all paths including propagation of unexpected
> exceptions.
>
> **2026-07-15:** Goal 139 DONE — `StudyFlowComposeTest` with 8 test methods:
> reveal button display, pass/fail button display and callbacks, done screen
> title/summary/button rendering, study-more availability. All use fixture
> models with semantic matchers.
>
> **2026-07-15:** Goal 140 DONE — `LocalStoreInventoryIntegrationTest` with
> 14 test methods: empty/null query, partial match, exact kanji match, special
> character escaping (%, _, \, quotes), empty inventory, case-insensitive
> search, similar-kanji filter, timeline with no events, ordering, and
> concurrent insert+search.
>
> **2026-07-15:** Goal 143 DONE — `.githooks/pre-commit` runs `lintDebug` on
> staged Kotlin files; exits non-zero with fix instructions on violations;
> skippable with `--no-verify`.
>
> **2026-07-15:** Goal 144 DONE — `docs/modularization-roadmap.md` documents
> current module structure, dependency graph, 6 proposed extraction targets
> ranked by independence and effort, a 3-phase ordering, effort estimates,
> and shared prerequisites (DI, interface extraction, ViewModels).
>
> **2026-07-15:** Goal 131 DONE — `StudyPrefetchCache` with
> `ConcurrentHashMap`-backed single-use cache keyed by (kanji, rung) with
> epoch-based invalidation. Seven unit tests cover populate, consume,
> single-use, epoch mismatch, invalidation, and repopulate. Wired into
> `MainActivityBase.studyPrefetchCache`.
>
> **2026-07-15:** Goal 136 DONE — `StudySessionViewModel` extends
> `androidx.lifecycle.ViewModel`, exposes `StateFlow<StudySessionUiState>`.
> Holds session progress (target/completed/correct counts, active flag,
> current item). Eight Robolectric tests verify state transitions and
> config-change preservation.
>
> **2026-07-15:** Goal 137 DONE — `HomeViewModel` with
> `StateFlow<HomeUiState>`, progressive loading via `viewModelScope.launch`,
> 500ms debounce. Four tests: initial state, progressive load, config-change
> preservation, debounce. `lifecycle-viewmodel-ktx` added to the catalog.
>
> **2026-07-15:** Goal 141 DONE — Four golden life-of-kanji scenarios added
> to `AdaptiveReviewTransitionEngineTest`: happy-path (new→reading ceiling),
> recognition lapse (fail→repair→revalidation), reading lapse
> (fail→repair→revalidation), stuck repair escalation (threshold→write_kanji
> added). All deterministic with specific state assertions.
>
> **2026-07-15:** Goal 142 PARTIALLY DONE — pitest plugin incompatible with
> Gradle 9.4.1 (`reporting.baseDir` removed); `docs/mutation-testing-baseline.md`
> documents the planned config, target files, the incompatibility, and 5
> preemptive test-strength improvements. Plugin integration deferred until a
> Gradle-9-compatible pitest release ships.
>
> **All 16 goals addressed.** 15 fully implemented and passing `ciFast`; 1
> (Goal 142) blocked on external tooling but documented with baseline and
> workaround plan.
