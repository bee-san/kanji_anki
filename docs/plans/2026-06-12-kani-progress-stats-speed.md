# Kani Progress Stats Speed Implementation Plan

> **For Hermes:** Use subagent-driven-development skill to implement this plan task-by-task, or execute the Kanban graph recorded at the bottom of this file.

**Goal:** Make Kani's Stats / Progress route load and interact at normal app speed by ensuring the route uses precomputed domain snapshots and bounded cached aggregates instead of doing heavy stats recomputation or review-log aggregation on page load.

**Architecture:** Reuse the existing Stats cache pipeline that shipped in PR #76, but extend it to cover the newer Progress Analytics dashboard introduced by PR #356 / PR #383. Cache domain data, not rendered copy: add review-day aggregate snapshots to `StatsCacheStore.Snapshot`, refresh them in `StatsPrecomputeStore`, and make `ProgressAnalyticsLiveDataSource` prefer fresh or latest cached data while scheduling background refresh when stale. Keep first-run compatibility, but normal route load must avoid synchronous `StatsPrecomputeStore.refresh(...)` and avoid live `review_log` scans.

**Tech Stack:** Kotlin/Android, SQLite via `LocalStore`, Robolectric/JUnit, Gradle tasks in `:app`, existing `StatsCacheStore` / `StatsPrecomputeStore` / `ProgressAnalyticsLiveDataSource` code, existing Kani GitHub PR + CI/Sonar flow.

**Created:** 2026-06-12 17:28 BST  
**Plan worktree:** `/Users/autumnskerritt/kanji_anki_worktrees/kani-stats-speed-20260612`  
**Plan branch:** `perf/kani-progress-stats-speed`  
**Observed base:** `origin/main` at `ced498af` (`ci: speed up CI and release pipeline`)

---

## 0. Context verified before planning

This is **not** a duplicate of the already-shipped old Stats cache campaign.

Already completed work:

- PR #76 (`perf: precompute Kani Stats cache`) merged to main on 2026-05-31.
- Existing Kanban tenant `precompute-kani-stats-review-log` is done.
- Existing cache includes expensive legacy Stats values in `StatsCacheStore.Snapshot`:
  - `outcomeStats`
  - `impactReport`
  - `studyImpactStats`
  - `recentMistakes`
  - `studyStreak`
  - `studyTaskTimeStats`
- Existing progress/mockup parity work is done:
  - `kani-progress-analytics-20260610`
  - `kani-stats-mockup-parity-20260611`

New target for this plan:

- `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStats.kt`
  - `renderStats()` currently loads `progressAnalyticsSnapshot(store)` inside `renderAsyncHomeRoute(...)`.
- `app/src/main/kotlin/dev/bee/kanjianki/progress/ProgressAnalyticsLiveDataSource.kt`
  - `progressAnalyticsSnapshot(...)` uses `store.cachedStatsSnapshotOrNull() ?: store.recomputeStatsSnapshotSynchronously(nowMillis)`.
  - It still runs live `reviewDaySummaries(nowMillis, 30)` and `reviewDaySummaries(nowMillis, 14)` raw SQL queries against `review_log` at route load.
- `app/src/main/kotlin/dev/bee/kanjianki/data/LocalStoreStudy.kt`
  - `latestStatsSnapshotOrNull()` currently calls `StatsCacheStore.readFresh(...)`, not `readLatest(...)`, so stale-cache fallback is effectively unavailable.
  - `undoLastRating()` in `MainActivityStudyReviewFlow.kt` still calls `activity.store.recomputeStatsSnapshotSynchronously(now)` after rendering restored study state.
- `app/src/main/kotlin/dev/bee/kanjianki/data/LocalStoreTableCreator.kt`
  - There is already an index on `(review_day_start, reviewed_at)`, so the first optimization should be precompute/cache integration, not a speculative new index.
- `app/src/test/kotlin/dev/bee/kanjianki/progress/ProgressAnalyticsLiveDataSourceTest.kt`
  - Existing test proves a fresh cache can combine with live review buckets, but it does not prove the cached path avoids live range queries or synchronous recompute.

## 1. Success criteria

User-facing acceptance:

- Opening Stats / Progress should feel normal: no visible multi-second stall for ordinary local datasets.
- Navigating inside the Progress dashboard should not trigger new heavyweight DB work for each section.
- Stale-but-safe cached Stats are acceptable during background refresh; the UI should not crash or block simply because the cache is stale.
- First install / no-cache compatibility remains: the app still produces a Stats screen, but it schedules cache refresh and does not regress data safety.
- The visual/copy output of the five Progress surfaces remains equivalent unless the implementation intentionally changes only loading/freshness copy.

Engineering acceptance:

- Cached Progress route path does **not** call `StatsPrecomputeStore.refresh(...)` synchronously.
- Cached Progress route path does **not** call live `reviewDaySummaries(...)` / raw `review_log` range aggregate SQL.
- Stale latest cache is used as a fallback when format-compatible, and refresh is scheduled out-of-band.
- Direct synchronous compute is limited to true no-cache/invalid-cache compatibility, and a task body must document if that remains necessary.
- The performance guard is behavioral and deterministic: assert which expensive methods were or were not called instead of relying only on brittle millisecond thresholds.
- Cache format changes are backward-compatible: legacy cache rows decode safely, missing new fields default to empty review ranges, corrupt JSON returns null/fallback, and no existing tables are rewritten/dropped.

Non-goals:

- Do not redesign the Progress UI or change the pink/mockup layout.
- Do not chase micro-optimizations once normal page-load/interaction speed is reached.
- Do not cache localized strings or rendered Compose models; cache domain data only.
- Do not add a DB migration unless a real schema/index change is necessary. A JSON cache-format bump inside the existing `stats_screen_cache` table should be enough for the first implementation.
- Do not use live Android device/emulator validation by default unless a worker finds a UI issue that cannot be covered by existing Compose/Robolectric tests.

## 2. Root-cause hypothesis to verify first

The likely slow path is route-load aggregation, not Compose rendering.

Current route-load shape:

1. `MainActivityStats.renderStats()` calls `progressAnalyticsSnapshot(store)` in a background route loader.
2. `progressAnalyticsSnapshot(...)` falls back to `store.recomputeStatsSnapshotSynchronously(nowMillis)` whenever `cachedStatsSnapshotOrNull()` is not fresh.
3. `latestStatsSnapshotOrNull()` exists in legacy Stats code but is currently implemented as `readFresh(...)`, so stale latest fallback is missing.
4. Even with a fresh cache, Progress still executes at least two `review_log` aggregate queries:
   - 30-day summaries
   - 14-day summaries for current/previous 7-day comparisons
5. `undoLastRating()` has a direct synchronous recompute after restoring a study item.

The plan starts with characterization because performance work without proving the hot path is how we get fragile cache changes.

## 3. Target architecture

### 3.1 Add domain cache data for Progress ranges

Add a cacheable domain model for review-day summaries.

Recommended shape in `StatsCacheStore.kt`:

```kotlin
internal data class ReviewDaySummarySnapshot(
    val dayStartMillis: Long,
    val total: Int,
    val again: Int,
    val hard: Int,
    val good: Int,
    val easy: Int,
    val writingRequired: Int,
    val writingFailed: Int,
)
```

Add to `StatsCacheStore.Snapshot`:

```kotlin
val reviewDaySummaries: List<ReviewDaySummarySnapshot> = emptyList()
```

Cache 90 days by default so the Progress dashboard can support 7, 30, and 90 day ranges without more SQL.

Rules:

- Store local-day starts, not formatted labels.
- Store raw counts, not percentages.
- Store only bounded recent days, not the whole review history.
- Missing field in old cache JSON means empty list and `cacheFormatVersion < newVersion`.
- Corrupt JSON keeps existing null/fallback behavior.

### 3.2 Fix latest-cache fallback

`LocalStoreStudy.latestStatsSnapshotOrNull()` should call `StatsCacheStore.readLatest(...)`, not `readFresh(...)`.

Then route loaders can choose:

1. `readFresh(...)` for exact current data.
2. `readLatest(...)` for stale-but-safe data with background refresh scheduled.
3. Direct recompute only when no usable snapshot exists.

### 3.3 Give Progress a testable source facade

Create a source interface so tests can prove the fast path by call counts.

Recommended file: `app/src/main/kotlin/dev/bee/kanjianki/progress/ProgressAnalyticsLiveDataSource.kt`.

Possible shape:

```kotlin
internal interface ProgressAnalyticsStatsSource {
    fun freshStatsSnapshot(nowMillis: Long): StatsCacheStore.Snapshot?
    fun latestStatsSnapshot(): StatsCacheStore.Snapshot?
    fun recomputeStatsSnapshotSynchronously(nowMillis: Long): StatsCacheStore.Snapshot
    fun reviewDaySummaries(nowMillis: Long, days: Int): List<StatsCacheStore.ReviewDaySummarySnapshot>
    fun scheduleStatsRefreshIfStale()
}
```

Then `progressAnalyticsSnapshot(...)` delegates to a pure helper:

```kotlin
internal fun progressAnalyticsSnapshot(
    source: ProgressAnalyticsStatsSource,
    nowMillis: Long,
): ProgressAnalyticsState
```

The `LocalStore` overload can build the real source.

### 3.4 Use cached review summaries first

Progress range selection should use:

1. Fresh snapshot with `reviewDaySummaries` covering the requested window.
2. Latest compatible snapshot with `reviewDaySummaries`, plus background refresh.
3. Live bounded SQL query only when no compatible snapshot exists.

Do not silently accept old cache rows as fully fresh if they lack review-day summaries. For old rows:

- Use existing legacy Stats data for legacy Stats cards if needed.
- For Progress range charts, either run the bounded live query once or direct recompute a new snapshot.
- Schedule refresh so old rows are upgraded.

### 3.5 Make refresh asynchronous at route boundaries

Add or expose a small method on the activity layer so Stats route can schedule refresh after rendering a stale/latest snapshot.

Candidate changes:

- `MainActivityHome.scheduleStatsPrecomputeIfStaleAsync()` is currently private in `MainActivityHome`; make it `internal`/protected enough for `MainActivityStats` to call, or move it to a shared base/utility.
- In `MainActivityStats.renderStats()`, after rendering the Progress dashboard, call `scheduleStatsPrecomputeIfStaleAsync()`.
- In `MainActivityStudyReviewFlow.undoLastRating()`, replace direct `activity.store.recomputeStatsSnapshotSynchronously(now)` with scheduling/dirty behavior unless a test proves a synchronous recompute is required.

### 3.6 Keep existing dirty-version invalidation

Existing dirty markers are probably enough for first implementation:

- Review writes mark dirty.
- Undo review marks dirty.
- Study item/source writes and sync hooks were already wired in the PR #76 campaign.

The worker must verify every relevant write path still marks dirty after adding Progress ranges.

## 4. Detailed implementation tasks

### Task 1: Characterize current Progress route hot path

**Objective:** Add evidence-only tests / probes that prove which expensive paths currently run.

**Files:**

- Modify: `app/src/test/kotlin/dev/bee/kanjianki/progress/ProgressAnalyticsLiveDataSourceTest.kt`
- Possibly create: `app/src/test/kotlin/dev/bee/kanjianki/progress/ProgressAnalyticsPerformancePathTest.kt`
- Read only: `app/src/main/kotlin/dev/bee/kanjianki/progress/ProgressAnalyticsLiveDataSource.kt`
- Read only: `app/src/main/kotlin/dev/bee/kanjianki/data/LocalStoreStudy.kt`

**Steps:**

1. Write failing characterization tests before production changes:
   - Fresh cache path should not need direct recompute.
   - Stale latest cache should be usable for display while refresh is scheduled.
   - Cached range path should eventually avoid live review-log aggregation.
2. If the current code is not injectable enough, add tests that document the missing seam first; do not refactor production code before the RED test.
3. Record current SQL/call path in the task handoff.

**Suggested command:**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
./gradlew :app:testDebugUnitTest --tests dev.bee.kanjianki.progress.ProgressAnalyticsLiveDataSourceTest --no-daemon
```

**Acceptance:**

- Tests initially fail for the expected reason or are marked as characterization evidence if they pass because main already changed.
- Handoff names exact slow calls that still happen on Stats route load.

### Task 2: Fix latest Stats cache fallback

**Objective:** Make stale-but-compatible cache snapshots actually available.

**Files:**

- Modify: `app/src/main/kotlin/dev/bee/kanjianki/data/LocalStoreStudy.kt`
- Modify: `app/src/test/kotlin/dev/bee/kanjianki/data/StatsCacheStoreTest.kt` or `MainActivityStatsModelTest.kt`
- Possibly modify: `app/src/test/kotlin/dev/bee/kanjianki/MainActivityStatsModelTest.kt`

**Steps:**

1. RED: Add a test where `StatsCacheStore.write(...)` writes a snapshot with an old `generatedAtMillis` but matching source version and current cache format.
2. Assert `latestStatsSnapshotOrNull()` returns it while `cachedStatsSnapshotOrNull()` returns null.
3. GREEN: Change `latestStatsSnapshotOrNull()` to `StatsCacheStore(...).readLatest(...)`.
4. Add a test that `buildStatsScreenModel(...)` uses latest cache before direct recompute.

**Acceptance:**

- Legacy Stats model still uses latest stale cache before recompute.
- No direct synchronous recompute is needed when latest compatible cache exists.

### Task 3: Add review-day aggregate snapshot model and codec

**Objective:** Extend cache serialization with bounded Progress review-day summaries.

**Files:**

- Modify: `app/src/main/kotlin/dev/bee/kanjianki/data/StatsCacheStore.kt`
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/data/StatsCacheCodec.kt`
- Modify: `app/src/test/kotlin/dev/bee/kanjianki/data/StatsCacheCodecTest.kt`
- Modify: `app/src/test/kotlin/dev/bee/kanjianki/data/StatsCacheStoreTest.kt`

**Steps:**

1. RED: Codec round-trip test for `reviewDaySummaries` with at least two days and all rating/writing fields.
2. RED: Legacy JSON without `reviewDaySummaries` decodes safely to empty list and old format is not treated as the newest cache format.
3. GREEN: Add `ReviewDaySummarySnapshot` and JSON array field.
4. Bump `STATS_CACHE_FORMAT_VERSION` only after tests prove old rows are safe.
5. Keep JSON names stable and boring: `reviewDaySummaries`, `dayStartMillis`, `total`, `again`, `hard`, `good`, `easy`, `writingRequired`, `writingFailed`.

**Acceptance:**

- New snapshots round-trip all fields.
- Old snapshots do not crash.
- Corrupt summary entries are ignored or make snapshot decode null consistently with existing cache corruption policy.

### Task 4: Precompute review-day summaries during Stats refresh

**Objective:** Populate the new Progress range data in background refresh.

**Files:**

- Modify: `app/src/main/kotlin/dev/bee/kanjianki/data/StatsPrecomputeStore.kt`
- Possibly modify: `app/src/main/kotlin/dev/bee/kanjianki/data/StudyStatsQueries.kt`
- Modify: `app/src/test/kotlin/dev/bee/kanjianki/data/StatsPrecomputeStoreTest.kt`

**Steps:**

1. RED: Extend `StatsPrecomputeStoreTest.refreshWritesFreshCacheMatchingDirectQueries()` to assert review-day summary data is present and matches seeded `review_log` rows.
2. Add a bounded query helper that fetches 90 local days of review summary data in one SQL query.
3. Use existing `(review_day_start, reviewed_at)` index; do not add a new index unless `EXPLAIN QUERY PLAN` or tests show the existing one is not used.
4. Fill missing local days in memory with zero-count rows so chart code never has to query for holes.

**Acceptance:**

- `StatsPrecomputeStore.refresh(...)` writes 90-day review summary snapshots.
- Data parity with direct query is tested.
- No unrelated cache fields regress.

### Task 5: Wire Progress Analytics to cached domain snapshots

**Objective:** Make `progressAnalyticsSnapshot(...)` fast when cache exists.

**Files:**

- Modify: `app/src/main/kotlin/dev/bee/kanjianki/progress/ProgressAnalyticsLiveDataSource.kt`
- Modify: `app/src/test/kotlin/dev/bee/kanjianki/progress/ProgressAnalyticsLiveDataSourceTest.kt`
- Possibly create: `app/src/test/kotlin/dev/bee/kanjianki/progress/ProgressAnalyticsPerformancePathTest.kt`

**Steps:**

1. RED: Add a fake `ProgressAnalyticsStatsSource` test where fresh cache includes review-day summaries and the fake throws if direct recompute or live review-day query is called.
2. Extract pure helper `progressAnalyticsSnapshot(source, nowMillis)`.
3. Convert `StatsCacheStore.ReviewDaySummarySnapshot` to the local `ReviewDaySummary` used by chart builders.
4. Use fresh cache first.
5. Use latest cache second and schedule refresh.
6. Use direct fallback only when no snapshot exists or snapshot cannot provide required range data.
7. Preserve current labels/series/copy values from existing tests.

**Acceptance:**

- Cached path produces the same Progress state shape while making zero expensive calls.
- Stale latest path displays and requests background refresh.
- No-cache path still works.

### Task 6: Replace route-load synchronous refresh with background scheduling

**Objective:** Avoid heavy refresh in user-facing route flows when stale data can display.

**Files:**

- Modify: `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStats.kt`
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/MainActivityHome.kt`
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyReviewFlow.kt`
- Modify: `app/src/test/kotlin/dev/bee/kanjianki/StatsPrecomputeSchedulerTest.kt`
- Possibly modify: route/model tests for Stats rendering.

**Steps:**

1. RED: Test that Stats route render schedules refresh when stale latest cache is used.
2. RED: Test that undo-last-rating marks dirty and schedules refresh instead of synchronously recomputing.
3. Make scheduling method available to Stats route without exposing a broad public API.
4. Ensure `StatsPrecomputeScheduler` still suppresses duplicate concurrent refreshes and respects the 60-second minimum interval.
5. Keep first-run/no-cache direct behavior only if tests show no renderable fallback exists.

**Acceptance:**

- Route render returns promptly from cached/latest data.
- Background scheduler refreshes stale data.
- Undo flow no longer blocks on direct stats recompute.

### Task 7: Add deterministic performance guard

**Objective:** Prevent regressions where a future stats UI change reintroduces heavy route-load queries.

**Files:**

- Create or modify: `app/src/test/kotlin/dev/bee/kanjianki/progress/ProgressAnalyticsPerformancePathTest.kt`
- Modify: `app/src/test/kotlin/dev/bee/kanjianki/data/StatsPrecomputePerformanceSmokeTest.kt`

**Steps:**

1. Add a fake-source test with counters:
   - `freshSnapshotCalls == 1`
   - `latestSnapshotCalls == 0` on fresh path
   - `directRecomputeCalls == 0` on fresh/latest path
   - `liveReviewSummaryCalls == 0` on fresh/latest path with cached range data
2. Add a stale path test:
   - latest snapshot used
   - refresh scheduled exactly once
   - no direct recompute
3. Keep any timing smoke threshold broad and secondary, if included at all.

**Acceptance:**

- Regression tests fail if Progress route starts doing direct recompute or review-log aggregation with usable cache.

### Task 8: UI/copy parity and accessibility check

**Objective:** Prove speed changes did not break the Progress dashboard output.

**Files:**

- Modify: `app/src/test/kotlin/dev/bee/kanjianki/progress/ProgressAnalyticsLiveDataSourceTest.kt`
- Modify: `app/src/androidTest/kotlin/dev/bee/kanjianki/ProgressAnalyticsComposeTest.kt` only if needed
- Modify: `app/src/test/kotlin/dev/bee/kanjianki/ProgressAnalyticsLocaleComposeTest.kt` only if localized copy changes

**Steps:**

1. Run existing Progress Analytics unit tests after implementation.
2. If freshness/loading copy is added, update tests in the copy layer.
3. Verify bottom nav, five sections, chart labels, and key accessibility summaries remain present.

**Commands:**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
./gradlew :app:testDebugUnitTest --tests dev.bee.kanjianki.progress.ProgressAnalyticsLiveDataSourceTest \
  --tests dev.bee.kanjianki.progress.ProgressAnalyticsCopyTest \
  --tests dev.bee.kanjianki.ProgressAnalyticsLocaleComposeTest --no-daemon

JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
./gradlew :app:compileDebugAndroidTestKotlin --no-daemon
```

**Acceptance:**

- Existing visual/model contracts still pass.
- No UI section disappears.
- No untranslated/loading placeholder leaks into normal cached path.

### Task 9: QA, security review, and ship

**Objective:** Merge the speed-up safely through the normal Kani PR flow.

**Files:**

- Review entire diff.
- No secrets/credentials.

**Required validation bundle before PR merge:**

```bash
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
./gradlew :app:testDebugUnitTest --tests 'dev.bee.kanjianki.data.*Stats*Test' \
  --tests 'dev.bee.kanjianki.progress.*' \
  --tests dev.bee.kanjianki.MainActivityStatsModelTest \
  --tests dev.bee.kanjianki.StatsPrecomputeSchedulerTest --no-daemon

JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home \
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:lintDebug --no-daemon
```

Then open/update PR:

- Title: `perf: speed up Kani Progress stats loading`
- Body includes:
  - what was cached
  - behavioral performance guard
  - stale-cache behavior
  - validation commands
  - note that live-device/emulator validation was not run unless it actually was

Acceptance:

- PR checks green.
- Sonar/SonarCloud green or documented not applicable.
- Squash merge to main only after QA + security pass.
- Final handoff includes PR URL, merge SHA, and test evidence.

## 5. Safety / data correctness checklist

Before merge, reviewers must answer:

- Does fresh cache still require matching source version and same local day?
- Does stale latest cache display only as fallback and trigger refresh?
- Does old cache JSON decode without crashing?
- Does corrupt cache JSON fall back safely?
- Are review-day summaries bounded to recent days?
- Are review writes, undo writes, sync/history writes, and settings writes still dirtying the cache?
- Does route-load cached path avoid direct recompute and live review-log aggregate SQL?
- Are all cached fields domain values rather than rendered/localized text?

## 6. Kanban task graph

This section is patched after cards are created.

Board: `kani`  
Tenant: `kani-progress-stats-speed-20260612`  
Implementation worktree: `/Users/autumnskerritt/kanji_anki_worktrees/kani-stats-speed-20260612`  
Implementation branch: `perf/kani-progress-stats-speed`

<!-- KANBAN_GRAPH_START -->
Created on board `kani` under tenant `kani-progress-stats-speed-20260612`.

Task graph:

1. `t_44745742` — **audit: characterize Kani Progress stats load performance path**
   - Assignee: `coding`
   - Parents: none
   - Role: root characterization / evidence card
2. `t_22f40b99` — **fix: Kani Stats latest-cache fallback and Progress source seam**
   - Assignee: `coding`
   - Parents: `t_44745742`
3. `t_161d3d9d` — **implement: cache Kani Progress review-day summary snapshots**
   - Assignee: `coding`
   - Parents: `t_22f40b99`
4. `t_2c978347` — **implement: precompute Kani Progress review-day summaries**
   - Assignee: `coding`
   - Parents: `t_161d3d9d`
5. `t_72cc7b30` — **implement: load Kani Progress Analytics from cached ranges**
   - Assignee: `coding`
   - Parents: `t_2c978347`
6. `t_9136661d` — **implement: async refresh scheduling for Kani Stats route and undo**
   - Assignee: `coding`
   - Parents: `t_72cc7b30`
7. `t_8afa880a` — **test: add Kani Progress stats performance regression guard**
   - Assignee: `coding`
   - Parents: `t_9136661d`
8. `t_11d570b9` — **review: Kani Progress stats cache data-safety**
   - Assignee: `security`
   - Parents: `t_8afa880a`
9. `t_93d5d710` — **uitest: verify Kani Progress dashboard after stats speedup**
   - Assignee: `uitester`
   - Parents: `t_8afa880a`
10. `t_19293ebf` — **qa: review CI/Sonar and merge Kani Progress stats speedup**
    - Assignee: `qa`
    - Parents: `t_8afa880a`, `t_11d570b9`, `t_93d5d710`
11. `t_d8bb1fa1` — **notify: Kani Progress stats speedup complete**
    - Assignee: `qa`
    - Parents: `t_19293ebf`

Dependency diagram:

```text
t_44745742 characterize
  -> t_22f40b99 stale-cache fallback/source seam
    -> t_161d3d9d cache review-day summaries
      -> t_2c978347 precompute review-day summaries
        -> t_72cc7b30 Progress reads cached ranges
          -> t_9136661d async refresh scheduling
            -> t_8afa880a performance regression guard
              -> t_11d570b9 security/data-safety review
              -> t_93d5d710 UI verification
              -> t_19293ebf QA/CI/Sonar/merge (also waits for security + UI)
                -> t_d8bb1fa1 finalizer
```

Execution defaults encoded in card bodies:

- Use the shared clean worktree `/Users/autumnskerritt/kanji_anki_worktrees/kani-stats-speed-20260612` on branch `perf/kani-progress-stats-speed`.
- Do not touch Bee's dirty original checkout.
- Strict TDD: RED test before production change, then GREEN, then refactor.
- Preserve the normal Kani PR flow: commit, push, PR, GitHub CI/Fast confidence gate, Sonar/SonarCloud, QA/security review, merge to `main` only when green/safe.
- Live Android device/emulator testing is not required by default because this is a local cache/performance slice, not provider/sync behavior.
- No secrets, no release/signing, no destructive operations.
<!-- KANBAN_GRAPH_END -->
