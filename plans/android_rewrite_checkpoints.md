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

1. Full current-production database fixture migration into the Room schema,
   preserving every current table family and old install defaults.
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

Status: partial. The current slice adds schema-compatible Room coverage for
the source mirror and sync run families only; repositories and full production
DB migration tests are still pending.

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

## Current Persistence Facts For Room Migration

Current app database:

- Name: `kanji_anki_simple.db`.
- Version: `20`.
- Source of truth today: `SQLiteOpenHelper` in
  `app/src/main/java/dev/bee/kanjianki/data/LocalStoreBase.java`.
- Schema constants: `app/src/main/java/dev/bee/kanjianki/data/LocalStoreSchema.java`.

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

Migration hazards:

1. `study_items` uses composite primary key `(kanji, answer_signature)`.
2. Task memories are stored as tab-separated encoded strings, not child rows.
3. Booleans are integer `0/1`; timestamps are integer millis.
4. Several list/search fields are serialized strings.
5. Timeline dedupe depends on unique `dedupe_key` and must be preserved exactly.
6. Similar-kanji availability is derived from `similar_kanji_pairs`, not stored
   on `study_items`.
7. v16 was a scheduler fresh-start rebuild: it recreated `study_items` and
   cleared `learning_repeats`, `similar_kanji_choice_state`, and
   `similar_kanji_repair_queue`. Room migration tests must preserve this
   historical behavior for old DBs.
8. Existing backup behavior checkpoints WAL and copies `kanji_anki_simple.db`
   into `filesDir/backups/` with 31 retained backups. The first Room migration
   path must keep a preflight backup equivalent.

Settings currently live in a string key/value table. Typed Room/DataStore
settings need explicit migrations from keys in
`app/src/main/java/dev/bee/kanjianki/sync/SyncSettings.java` and
`app/src/main/java/dev/bee/kanjianki/data/LocalStoreStudy.java`.
