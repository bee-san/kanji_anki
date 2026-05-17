# Android Rewrite Checkpoints

This file tracks concrete evidence for `plans/android_rewrite.md` while the
rewrite is implemented. It is intentionally stricter than "tests pass": every
rewrite step needs an artifact that maps back to the plan.

## Step 1 Characterization Snapshot

Status: partial, based on the current Java/View/SQLite app before the Kotlin
foundation work.

Strong existing coverage:

- Scheduler and ladder behavior: `core/src/test/java/dev/bee/kanjianki/core/LadderSchedulerTest.java`,
  `core/src/test/java/dev/bee/kanjianki/core/BridgeSchedulerTest.java`,
  `core/src/test/java/dev/bee/kanjianki/core/StudyLadderSettingsTest.java`,
  `app/src/androidTest/java/dev/bee/kanjianki/LadderSchedulerEndToEndTest.java`.
- Import defaults and opt-ins: `core/src/test/java/dev/bee/kanjianki/core/KanjiImportSelectorTest.java`,
  `core/src/test/java/dev/bee/kanjianki/core/KanjiImportSelectorBrowserQueryEmptyTest.java`,
  `app/src/test/java/dev/bee/kanjianki/sync/SyncSettingsBehaviorTest.java`.
- Provider classification and sync paths:
  `app/src/test/java/dev/bee/kanjianki/anki/AnkiDroidGatewayTest.java`,
  `app/src/androidTest/java/dev/bee/kanjianki/anki/AnkiDroidGatewayProviderInstrumentedTest.java`,
  `app/src/androidTest/java/dev/bee/kanjianki/sync/ManualSyncEngineInstrumentedTest.java`,
  `app/src/androidTest/java/dev/bee/kanjianki/sync/AutoSyncRunnerInstrumentedTest.java`.
- Writing primitives and UI flows:
  `core/src/test/java/dev/bee/kanjianki/core/study/*Test.java`,
  `app/src/androidTest/java/dev/bee/kanjianki/DrawingPadViewInstrumentedTest.java`,
  `app/src/androidTest/java/dev/bee/kanjianki/MainActivityInstrumentedTest.java`.
- FSRS reference behavior:
  `fsrs-java/src/test/java/dev/bee/fsrs/FsrsEngineReferenceTest.java`,
  `fsrs-java/src/test/java/dev/bee/fsrs/FsrsEngineFixtureTest.java`,
  `core/src/test/java/dev/bee/kanjianki/core/KaniFsrsAdapterTest.java`.

Highest-risk characterization gaps before replacement:

1. Clean Room/DataStore reset path for legacy DBs, with enough empty-state and
   first-sync coverage to prove the app rebuilds from AnkiDroid/source assets.
2. Auto-sync parity with manual sync under customized import filters, adaptive
   planner settings, and note mappings.
3. Study above-fold and progress-truth coverage across every task type and
   compact viewport size, especially typing, word-reading, and similar-choice.
4. Activity-level missing-provider and missing-permission UX, beyond provider
   gateway classification tests.
5. FSRS oracle signoff must include `:fsrs-java:test` in every rewrite gate,
   because the strongest generated oracle tests live outside `core`/`app`.

## Step 2 Kotlin/Gradle Foundation

Target artifact:

- Version catalog in `gradle/libs.versions.toml`.
- Skeleton modules compiling:
  - `:fsrs`
  - `:domain`
  - `:dictionary-core`
  - `:writing-core`
  - `:designsystem`
  - `:data`
  - `:ankidroid`
  - `:dictionary-android`
  - `:writing-android`
- Existing `:app` still builds and keeps package identity.
- `ciFast` includes the new skeleton modules.

Current caveat:

- AGP 9 has built-in Kotlin for Android modules. The Android modules should not
  apply `org.jetbrains.kotlin.android`; Kotlin JVM is used only for pure JVM
  modules.
- KSP currently requires `android.disallowKotlinSourceSets=false` with this
  AGP 9 setup; this is an explicit compatibility flag, not a product decision.
- Kotlin `2.2.10` with Gradle `9.4.1` is locally proven for the current
  skeleton by the commands below.

Verification commands:

```sh
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
  ./gradlew :fsrs:test :domain:test :dictionary-core:test :writing-core:test \
  :designsystem:compileDebugKotlin :data:compileDebugKotlin \
  :ankidroid:compileDebugKotlin :dictionary-android:compileDebugKotlin \
  :writing-android:compileDebugKotlin
```

Result: `BUILD SUCCESSFUL` after adding the initial Room `settings` entity.

```sh
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
  ./gradlew :app:compileDebugJavaWithJavac
```

Result: `BUILD SUCCESSFUL`; the legacy Java app still compiles with the new
module graph present.

```sh
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
  ./gradlew ciFast
```

Result: `BUILD SUCCESSFUL`; `ciFast` now includes the new JVM module tests and
the Android module compile gates.

## Step 3 FSRS Module Decision

Status: Java retained behind a narrow Kotlin API for the first rewrite pass.

Current artifact:

- `:fsrs` depends on the existing `:fsrs-java` engine.
- `JavaBackedKaniFsrsEngine` exposes the app-facing scheduler surface while
  keeping ladder/import policy outside FSRS.
- The Kotlin FSRS wrapper exposes `nextDifficulty` so relearning graduation can
  preserve the current Java review-transition semantics.
- `FsrsSchedulingBounds` publishes the Java FSRS stability, difficulty,
  retention, and maximum-interval bounds for Kotlin scheduler policy code.
- `:fsrs-java:test` remains required in rewrite gates because it owns the
  strongest generated oracle and pinned upstream fixture tests.

Verification command:

```sh
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
  ./gradlew :fsrs:test :fsrs-java:test
```

Result: `:fsrs:test` passed as part of the foundation gate. `:fsrs-java:test`
is still listed as a required gate and must be run before replacing scheduler
call sites.

## Step 4 Domain Model Extraction

Status: partial. The first extracted models are intentionally compatibility
oriented and compile without Android dependencies.

Current artifacts:

- Source mirror models in `:domain`:
  - `SourceNote`
  - `SourceCard`
- Sync models in `:domain`:
  - `SyncRun`
  - `SyncRunStatus`
  - `SyncErrorCode`
- Import contracts in `:domain`:
  - `ImportSettings`
  - `ImportSource`
  - `NoteTypeMapping`
  - `NewCardSortMode`
- Dictionary clue formatting in `:dictionary-core`.
- Writing hint contracts in `:writing-core`.

Behavior locked by unit tests:

- Kiku defaults remain `Kiku` / `Mining` with `Expression`,
  `ExpressionReading`, `MainDefinition`, `Sentence`, `Frequency`, and
  `FreqSort`.
- Suspended import remains enabled by default, while active, tagged, weak, and
  browser-query imports remain opt-in.
- Suspended rank defaults remain `100..3000`.
- Import source and sync status wire names round-trip or fail explicitly.
- Dictionary clues render meaning first, then reading, then `From: ...`, and do
  not reintroduce a `Meaning:` label.
- Writing hint levels preserve `TRACE`, `OUTLINE`, `MINIMAL`, and `BLIND`.

Verification command:

```sh
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
  ./gradlew :domain:test :dictionary-core:test :writing-core:test
```

Result: `BUILD SUCCESSFUL`.

## Step 5 Room Schema And Repositories

Status: partial. The current slices add schema-compatible Room coverage and
repository seams for the key table families. Full production DB migration is
no longer a hard requirement; destructive reset is acceptable when it keeps the
Room schema clean and the app can rebuild through sync.

Current artifacts:

- `SourceNoteEntity` and `SourceNoteDao`.
- `SourceCardEntity` and `SourceCardDao`.
- `SyncRunEntity` and `SyncRunDao`.
- Suspended/import entities and DAOs:
  - `SuspendedArchiveEntity`
  - `SuspendedImportEntity`
  - `SuspendedSourceEntity`
  - `ImportRuleAuditEntity`
  - `ImportDecisionEntity`
- Inventory/dashboard entities and DAOs:
  - `DashboardRowEntity`
  - `KanjiExampleEntity`
  - `KanjiInventoryEntity`
  - `LocalKanjiSuspensionEntity`
- Study/log entities and DAOs:
  - `StudyItemEntity`
  - `LearningRepeatEntity`
  - `ReviewLogEntity`
  - `StudyTaskLogEntity`
- Similar-kanji entities and DAOs:
  - `SimilarKanjiPairEntity`
  - `SimilarChoiceStateEntity`
  - `SimilarRepairQueueEntity`
  - `SimilarReviewLogEntity`
- Timeline and historical sync entities and DAOs:
  - `KanjiTimelineEventEntity`
  - `SyncCardSnapshotEntity`
  - `SyncNoteSnapshotEntity`
  - `SyncKanjiSnapshotEntity`
- `KaniRoomDatabase` now declares `settings`, `source_notes`, `source_cards`,
  `sync_runs`, suspended/import tables, dashboard rows, kanji examples, kanji
  inventory, local kanji suspensions, study/log tables, similar-kanji tables,
  timeline events, and historical sync snapshots.
- Room schema export updated at
  `data/schemas/dev.bee.kanjianki.data.KaniRoomDatabase/20.json`.

Compatibility notes:

- Foreign keys and indexes requested by the ideal schema are intentionally not
  added yet because the current production v20 raw SQLite schema does not have
  them. Adding them belongs in an explicit migration step with fixture tests.
- `sync_runs.id` remains nullable/autogenerated in the Room entity so the
  exported schema matches the existing `INTEGER PRIMARY KEY AUTOINCREMENT`
  shape instead of inventing a new `NOT NULL` constraint.
- `source_cards.due` is an integer to match the existing AnkiDroid/source-card
  mirror contract.
- Legacy indexes already present on `kanji_examples(kanji)` and
  `import_decisions(kanji, sync_id)` are declared in Room.
- Legacy defaults on `kanji_examples.interval_days` and `kanji_examples.reps`
  remain `0`.
- Legacy indexes for study due lookups, stats rollups, similar-kanji due
  queues, kanji inventory search, timeline dedupe, and historical sync
  snapshots are declared in Room.
- Encoded task-memory columns remain embedded in `StudyItemEntity` for this
  parity-first schema pass; extracting typed task memory belongs behind a
  migration fixture and scheduler parity tests.

Verification command:

```sh
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
  ./gradlew :data:kspDebugKotlin :data:compileDebugKotlin --rerun-tasks
```

Result: `BUILD SUCCESSFUL`; schema export regenerated from Room.

The same command also passed after adding the suspended/import and
inventory/dashboard table families.

It also passed after adding the study/log, similar-kanji, timeline, and
historical sync snapshot table families. The exported schema now contains the
current v20 user-data tables:

```text
settings
source_notes
source_cards
sync_runs
suspended_archive
suspended_imports
suspended_sources
import_rule_audits
import_decisions
dashboard_rows
kanji_examples
kanji_inventory
local_kanji_suspensions
study_items
learning_repeats
review_log
study_task_log
similar_kanji_pairs
similar_kanji_choice_state
similar_kanji_repair_queue
similar_kanji_review_log
kanji_timeline_events
sync_card_snapshots
sync_note_snapshots
sync_kanji_snapshots
```

```sh
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
  ./gradlew ciFast
```

Result: `BUILD SUCCESSFUL` after the domain/data slice.

Additional schema guard:

- `data/src/test/kotlin/dev/bee/kanjianki/data/KaniRoomSchemaParityTest.kt`
  checks that the exported Room schema contains the current v20 user-data table
  list and critical legacy defaults/indexes.
- `ciFast` now includes `:data:testDebugUnitTest`.

Repository foundation:

- `SourceMirrorRepository` and `SyncRunRepository` define the first domain
  repository seams.
- `RoomSourceMirrorRepository` writes notes/cards in a Room transaction and
  maps source mirror rows to domain models.
- `RoomSyncRunRepository` maps persisted sync-run wire status values to domain
  `SyncRunStatus` and keeps generated IDs at the repository boundary.
- `RepositoryMappersTest` covers source note/card and sync-run mapper parity.

Verification commands:

```sh
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
  ./gradlew :data:testDebugUnitTest
```

Result: `BUILD SUCCESSFUL`.

```sh
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
  ./gradlew ciFast
```

Result: `BUILD SUCCESSFUL`; the fast gate now runs `259` actionable tasks with
the data schema parity test included.

The same `ciFast` command passed after adding the first repository interfaces,
Room implementations, and mapper tests.

## Step 6 Sync Domain Rewrite

Status: started. This is not yet a manual-sync replacement.

Current artifacts:

- `CollectionGateway` in `:domain` defines a suspendable collection-read
  boundary.
- `RunSourceMirrorSyncUseCase` reads a collection snapshot, inserts a sync-run
  summary, and writes source note/card rows with the generated sync ID.
- `CollectionGatewayException` maps provider failures into permanent vs
  retryable sync-run status.
- `ImportCandidateSelector` in `:domain` selects ranked kanji candidates from a
  source mirror snapshot using active, suspended, tagged, weak-card, and
  browser-query rules without depending on legacy record classes.

Explicit gaps:

- No AnkiDroid module implementation yet.
- Import candidate analysis exists, but the sync use case does not yet call it.
- No suspended archive cleanup, dashboard rebuild, queue seeding,
  similar-kanji rebuild, or historical snapshot write in the new use case yet.
- Manual and background sync still use the legacy Java path.

Verification commands:

```sh
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
  ./gradlew :domain:test
```

Result: `BUILD SUCCESSFUL`; fake gateway tests cover source snapshot write and
failure sync-run mapping.

```sh
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
  ./gradlew ciFast
```

Result: `BUILD SUCCESSFUL`.

## Step 7 Scheduler Rewrite

Status: started. The legacy scheduler remains the app runtime path.

Current artifacts:

- `LearningStepEngine` in `:domain` handles Anki-exact learning/relearning
  step transitions for `Again`, `Hard`, `Good`, and `Easy`.
- `LearningStepSettings` locks current defaults:
  - new learning: `1m`, `10m`
  - relearning: `10m`
- `LearningStepEngineTest` covers:
  - `Again` returns to the first step.
  - `Good` advances and graduates after the final step.
  - `Hard` on the first step uses the delay between Again and Good.
  - `Hard` on later steps repeats the current step.
  - `Easy` graduates immediately.
  - relearning preserves the relearning phase until graduation.
  - empty relearning steps graduate safely.
- `LadderMovementEngine` in `:domain` handles the configurable study-rung
  movement rules around real due reviews.
- `StudyLadderSettings` keeps rung order, enabled rungs, the promotion
  interval threshold, and consecutive-fail demotion threshold configurable.
- `LadderMovementEngineTest` covers:
  - promotion only when FSRS schedules strictly beyond the configured
    interval.
  - custom promotion and demotion thresholds.
  - `Hard`, `Good`, and `Easy` as pass outcomes.
  - `Again` demotion only at the configured consecutive-fail threshold.
  - same due-slot and future-due reviews not advancing streaks.
  - conditional use of the similar-kanji rung.
  - disabled-rung alignment to the nearest lower enabled rung on a tie.
  - rejecting settings where similar-kanji is the only enabled rung.
- `StudyProgressCalculator` in `:domain` tracks immutable study-run progress
  state and produces display snapshots for the Study top bar.
- `StudyProgressCalculatorTest` covers:
  - unique seen/completed task counting.
  - target initialization from remaining count, then target count.
  - stable session and similar-repair task keys.
  - moved-forward and missed-kanji outcome counts.
  - repair failures not marking already-moved kanji as missed.
  - display clamping for done runs, active tasks, and continue-all sessions.
- `StudySessionTracker` in `:app` now delegates study-run progress state,
  task keys, hard-cap checks, outcome counts, and top-bar snapshots to
  `StudyProgressCalculator` while keeping the current Java active-task timing
  and `LocalStore` answer-duration write path.
- `MainActivityBase.studyTopBar` renders from the Kotlin
  `StudyProgressSnapshot` instead of recalculating visible counters in the
  Activity.
- `StudySessionTrackerTest` covers the app bridge for the same visible Study
  top-bar display rules.
- `ReviewTokenGuard` in `:domain` isolates duplicate-review protection.
- `ReviewTokenGuardTest` covers:
  - matching active tokens being accepted and consumed.
  - consumed-token rejection taking precedence.
  - active-token mismatch rejection.
  - empty active tokens not blocking review.
  - null request tokens normalizing to the legacy empty token behavior.
- `ReviewRatingResolver` in `:domain` isolates the request-to-applied-rating
  policy for writing failures and manual overrides.
- `ReviewRatingResolverTest` covers:
  - write-kanji manual override resolving to `hard`.
  - failed required writing resolving to `again`.
  - normal requested rating preservation.
  - clean unguided write-kanji pass detection.
- `ReviewMemoryPolicy` in `:domain` isolates active task-memory selection and
  elapsed-review-day calculation for FSRS inputs.
- `ReviewMemoryPolicyTest` covers:
  - persisted task memory taking precedence when it has reviews.
  - legacy fallback to study item fields when task memory is empty.
  - new items keeping initial task memory.
  - elapsed day calculation from the previous scheduled interval.
- `StudyFsrsScheduler` in `:domain` adapts study ratings and task memory into
  FSRS initial-review and existing-review requests.
- `StudyFsrsSchedulerTest` covers:
  - new-learning graduation using fresh FSRS initial state.
  - relearning graduation preserving current memory with Java-equivalent
    stability, difficulty, retention, and maximum-interval clamps.
  - existing reviews sending clamped state into the Kotlin FSRS API.
  - elapsed-day and interval-result validation.
- `StudyReviewTransitionEngine` in `:domain` composes token validation, rating
  resolution, learning-step transitions, FSRS scheduling, ladder movement,
  writing-level adjustment, and task-memory handoff for a reviewed
  `StudyQueueItem`.
- `StudyReviewTransitionEngineTest` covers:
  - duplicate token rejection without mutating the item.
  - learning-step repeats without entering FSRS.
  - new-learning and relearning graduation through FSRS.
  - review passes using elapsed task-memory days and promotion thresholds.
  - review lapses entering relearning and demoting after the configured fail
    streak.
- `ApplyStudyReviewUseCase` composes the extracted review transition with
  `StudyQueueRepository.updateReviewedItem`.
- `ApplyStudyReviewUseCaseTest` covers:
  - duplicate reviews not being persisted.
  - accepted reviews persisting the transitioned item.
  - missing Room rows reporting an unpersisted result without changing the
    transition outcome.
- `LegacyStudyReviewBridge` in `:app` maps legacy `Records*` review inputs to
  the Kotlin `StudyReviewTransitionEngine`, then maps the transitioned item
  back to `RecordsStudyModels.StudyItem` for the current `LocalStore` write
  path.
- `MainActivityStudy.submitNormalReview` now calculates normal Study review
  transitions through `LegacyStudyReviewBridge` instead of
  `BridgeScheduler.applyReview`.
- `LegacyStudyReviewBridgeTest` compares the bridge against `BridgeScheduler`
  for review pass promotion, review-fail demotion, new-learning repeat, and
  duplicate-token behavior.
- `LegacyStudyMappers` centralizes the current `Records*` to domain study
  mappings used by both legacy runtime bridges.
- `LegacyStudySessionBridge` maps legacy `Records*` queue and dashboard rows
  into the Kotlin `StudySessionSelector`, then maps the selected session back
  to `RecordsSchedulerModels.StudySession` for the current Java/View Study UI.
- `MainActivityStudy.nextActiveSession` now calculates normal Study sessions
  through `LegacyStudySessionBridge` instead of `BridgeScheduler.nextSession`.
- `LegacyStudySessionBridgeTest` compares the bridge against `BridgeScheduler`
  for due-review token reuse, write/relearning priority, new-card sort mode,
  and null-session behavior.
- `LegacyStudySessionBridge` now delegates to `LoadNextStudySessionUseCase`
  through in-memory `StudyQueueRepository` and `StudyDashboardRepository`
  adapters over the rows/items already loaded by the legacy screen. This wires
  the runtime Study session path through the use case without changing the
  current `LocalStore` persistence boundary yet.
- `TaskMemory` and `TaskMemoryBank` in `:domain` preserve per-task memory
  encoding and task/rung memory routing.
- `TaskMemoryTest` covers:
  - initial task-memory defaults and study-field normalization.
  - modern 11-part task-memory encode/decode.
  - legacy 9-part and 10-part decode compatibility.
  - fallback behavior for malformed memory strings.
  - task-type aliases including `typing_meaning` and `writing_remediation`.
  - memory reads by rung and writes by task type.
- `StudyAheadPolicy` in `:domain` isolates the study-ahead horizon clamp.
- `StudyAheadPolicyTest` covers:
  - negative and zero study-ahead windows clamping to zero.
  - positive windows passing through.
  - windows above one day clamping to one day.
  - due-at-horizon inclusion and beyond-horizon exclusion.
- `StudyQueueItem`, `StudyDashboardRow`, `StudyExample`, and
  `StudyItemState` in `:domain` provide the minimal study queue model needed
  by the future session selector and review transition.
- `StudyQueueModelsTest` covers:
  - stable study-item state wire names.
  - answer signatures preferring suspended examples, then active examples, then
    first available examples.
  - row-field fallback and whitespace normalization.
  - family keys built from kanji plus answer signature.
  - retired and suppressed queue item flags.
- `StudyActiveQueueSelector` in `:domain` filters active queue candidates and
  chooses one active item per answer family.
- `StudyActiveQueueSelectorTest` covers:
  - retired, suppressed, disallowed, and missing-row item filtering.
  - blank-signature legacy items matching current rows by kanji.
  - answer-family matching by signature.
  - highest-rung active item selection within a family.
  - horizon-due preference and earliest-due tie breaking.
  - disabled rung alignment before family selection.
  - due counts after active queue filtering.
- `NewCardSortPolicy` in `:domain` preserves new-card row ordering modes.
- `NewCardSortPolicyTest` covers:
  - frequency sorting by Jiten rank with unknown ranks last.
  - FSRS difficulty sorting by highest finite difficulty.
  - retrievability-risk sorting by lowest normalized retrievability.
  - Kani weakness sorting by weakness and suspended support count.
  - null-row fallback ordering.
- `StudySessionSelector` in `:domain` produces the next due `StudySession`
  from active queue items.
- `StudySessionSelectorTest` covers:
  - null sessions when no item is due within the study-ahead horizon.
  - study-ahead pulling horizon-eligible items into a session.
  - existing-token reuse and injectable token generation.
  - task type, writing-required flag, and prompt projection.
  - due-priority, due-time, new-card sort, weakness, and kanji tie-breaking.
  - allowed-kanji and active-queue filtering.
- `LoadNextStudySessionUseCase` in `:domain` composes read-side queue and
  dashboard repositories into the session selector.
- `LoadNextStudySessionUseCaseTest` covers:
  - one-shot dashboard row loading with the legacy 120-row default.
  - active state queue loading without retired items.
  - request options flowing into the session selector.
  - null session behavior when no item is due within the horizon.
- `StudyQueueRepository` in `:domain` defines the read-side queue boundary.
- `RoomStudyQueueRepository` in `:data` reads active `study_items` through
  Room and annotates similar-kanji availability from `similar_kanji_pairs`.
- `StudyQueueRepository.updateReviewedItem` defines the write boundary for a
  reviewed study item.
- `RoomStudyQueueRepository.updateReviewedItem` updates the existing Room row
  while preserving entity-only columns such as `created_at` and suppression
  timestamps.
- `StudyItemMappers` maps `StudyItemEntity` to `StudyQueueItem`, including
  rung/phase/state wire names, real-review evidence fields, and task-memory
  decode, and maps reviewed domain items back onto existing Room rows.
- `StudyDashboardRepository` in `:domain` defines top and active read-side
  dashboard row boundaries and local kanji suspension writes.
- `RoomStudyDashboardRepository` in `:data` reads `dashboard_rows` and
  `kanji_examples` through Room with the legacy eight-example cap, and filters
  active reads through `local_kanji_suspensions`.
- `RoomStudyDashboardRepository.setLocallySuspended` writes and clears
  `local_kanji_suspensions`, normalizing blank input away and clamping negative
  timestamps to zero.
- `DashboardRowMappers` maps dashboard rows and examples to the scheduler
  domain models, including example FSRS difficulty and retrievability.
- `RepositoryMappersTest` covers `StudyItemEntity` to `StudyQueueItem`
  mapping with decoded task memory and dashboard row/example mapping.
- `RoomStudyDashboardRepositoryTest` covers local suspension writes filtering
  active dashboard rows.
- `AdaptiveStudyPlanner` in `:domain` ports the current Pareto/adaptive
  workload planning algorithm, including manual focus sizing, auto drop-off
  detection, due-recovery priority, remaining-count behavior, and FSRS/example
  risk scoring.
- `AdaptiveStudyPlannerTest` covers:
  - default Pareto sizing, very-low workload, and manual all-kanji behavior.
  - recent-review strain and steady-streak target adjustment.
  - due recovery filling or capping admissions.
  - learning cards with future step times still counting as remaining.
  - FSRS retrievability, difficulty, stability, interval, rep, and lapse
    ordering inputs.
  - auto Pareto drop-off detection, composite priority ordering, and max-item
    caps.
  - workload labels, ceilings, and null-plan fallback.
- `LegacyAdaptiveStudyPlannerBridge` maps current legacy dashboard rows, study
  items, recent review stats, focus settings, and sync settings into
  `AdaptiveStudyPlanner`, then maps the result back into
  `RecordsSchedulerModels.AdaptiveLoadPlan`.
- Runtime adaptive-plan calculation now goes through that bridge from:
  - `MainActivityBase.adaptivePlan`
  - `ManualSyncEngine.adaptivePlan`
  - `ReminderScheduler.reminderCopy`
- `LegacyAdaptiveStudyPlannerBridgeTest` compares the bridge against the
  existing Java `AdaptiveLoadPlanner` for manual Pareto planning, auto Pareto
  drop-offs, due recovery max-item caps, null request fallback, null fields
  inside a request, and the explicit settings entrypoint.
- Adaptive workload setting keys, defaults, labels, clamping, mode
  normalization, and target-ceiling helpers now live on `AdaptiveStudyPlanner`
  for Java/Kotlin callers.
- `LocalStoreStudy` and `MainActivitySettings` use the domain adaptive
  workload helpers while preserving the existing setting keys and UI text.

Explicit gaps:

- Kotlin review-transition calculation is wired into normal runtime Study
  reviews through the legacy bridge.
- Kotlin session-selection calculation is wired into normal runtime Study
  sessions through the legacy bridge.
- Study progress is wired into the runtime Study screen through the legacy
  tracker bridge; the future Compose/ViewModel surface is not built yet.
- Runtime Study review persistence still uses the legacy Java `LocalStore`
  write path.
- Runtime local suspension writes remain on the legacy Java `LocalStore` path
  until the detail screen is moved to the Room repository.
- The legacy Java adaptive planner remains as a parity oracle and compatibility
  `PlanRequest` / `WorkloadPolicy` type for reminder tests and bridge inputs
  until settings are moved fully to the domain/DataStore boundary.

Verification commands:

```sh
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
  ./gradlew :domain:test
```

Result: `BUILD SUCCESSFUL`.

The same `:domain:test` command passed after adding `AdaptiveStudyPlanner` and
`AdaptiveStudyPlannerTest`.

```sh
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
  ./gradlew :domain:test :data:testDebugUnitTest
```

Result: `BUILD SUCCESSFUL`.

```sh
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
  ./gradlew :app:testDebugUnitTest
```

Result: `BUILD SUCCESSFUL`.

The same `:app:testDebugUnitTest` command passed after wiring
`LegacyStudySessionBridge` through `LoadNextStudySessionUseCase`.

```sh
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
  ./gradlew :core:test :app:testDebugUnitTest
```

Result: `BUILD SUCCESSFUL` after wiring runtime adaptive-plan calculation
through `LegacyAdaptiveStudyPlannerBridge`.

```sh
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
  ./gradlew :domain:test :app:testDebugUnitTest
```

Result: `BUILD SUCCESSFUL` after moving adaptive workload helpers to the
domain planner for Java settings callers.

```sh
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
  ./gradlew ciFast
```

Result: `BUILD SUCCESSFUL`.

## Current Persistence Facts For Room Migration

Current app database:

- Legacy `SQLiteOpenHelper` name: `kanji_anki_simple.db`.
- Rewrite Room name: `kanji_anki_room.db`.
- Legacy version: `20`.
- Rewrite Room version: `21`.
- Source of truth today: `SQLiteOpenHelper` in
  `app/src/main/java/dev/bee/kanjianki/data/LocalStoreBase.java`.
- Schema constants: `app/src/main/java/dev/bee/kanjianki/data/LocalStoreSchema.java`.
- Room creation point: `data/src/main/java/dev/bee/kanjianki/data/KaniRoomDatabaseFactory.kt`.
  It rejects ambiguous ownership and uses destructive migration for the
  Room-owned file.
- Source mirror parity: Room `source_cards` now stores explicit `suspended`
  and `browser_query_matched` flags. These are required for Room-owned import
  analysis to distinguish suspended, active, weak, tagged, and browser-query
  sources without consulting legacy `RecordsSyncModels.Card`.
- App graph creation point:
  `app/src/main/java/dev/bee/kanjianki/di/KaniDataModule.java`.
  `KaniApplication` is now `@HiltAndroidApp`, and Hilt owns singleton bindings
  for the Room DB plus source mirror, sync run, study queue, study dashboard,
  session-selection, and review-application domain entrypoints.

Runtime DI gap:

- Existing Java screens/services still instantiate their legacy collaborators
  directly. Hilt is available for new ViewModels and for each runtime surface as
  it moves to Room-backed repositories.

Current table families:

- Settings: `settings`.
- Source mirror: `source_notes`, `source_cards`.
- Sync/history: `sync_runs`, `sync_card_snapshots`, `sync_note_snapshots`,
  `sync_kanji_snapshots`.
- Suspended/imports: `suspended_archive`, `suspended_imports`,
  `suspended_sources`, `import_rule_audits`, `import_decisions`.
- Dashboard/inventory/examples: `dashboard_rows`, `kanji_examples`,
  `kanji_inventory`, `local_kanji_suspensions`.
- Study: `study_items`, `learning_repeats`, `review_log`, `study_task_log`.
- Similar kanji: `similar_kanji_pairs`, `similar_kanji_choice_state`,
  `similar_kanji_repair_queue`, `similar_kanji_review_log`.
- Timeline: `kanji_timeline_events`.

Legacy reset/migration hazards:

1. `study_items` uses composite primary key `(kanji, answer_signature)`.
2. Task memories are stored as tab-separated encoded strings, not child rows.
3. Booleans are integer `0/1`; timestamps are integer millis.
4. Several list/search fields are serialized strings.
5. Timeline dedupe depends on unique `dedupe_key` and must be preserved exactly.
6. Similar-kanji availability is derived from `similar_kanji_pairs`, not stored
   on `study_items`.
7. v16 was a scheduler fresh-start rebuild: it recreated `study_items` and
   cleared `learning_repeats`, `similar_kanji_choice_state`, and
   `similar_kanji_repair_queue`. The rewrite may use the same fresh-start
   strategy instead of migrating old scheduler state.
8. Existing backup behavior checkpoints WAL and copies `kanji_anki_simple.db`
   into `filesDir/backups/` with 31 retained backups. A backup/reset UX is
   useful, but it is not a blocker for the clean rewrite schema.

Settings currently live in a string key/value table. Typed Room/DataStore
settings may reset to current defaults or explicitly migrate selected keys from
`app/src/main/java/dev/bee/kanjianki/sync/SyncSettings.java` and
`app/src/main/java/dev/bee/kanjianki/data/LocalStoreStudy.java`.
