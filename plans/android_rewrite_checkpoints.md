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
