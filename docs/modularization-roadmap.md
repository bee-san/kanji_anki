# Modularization Roadmap

Analysis of the current module structure, proposed extraction targets, dependency
graph, and effort estimates for breaking up the monolithic `:app` module.

## Current module structure

| Module | Type | Files | Purpose |
|--------|------|-------|---------|
| `:app` | Android application | 365 Kotlin | UI, data layer, sync, update, backup, reminders, widget, study |
| `:core` | Kotlin JVM library | 170 Kotlin | Domain policies, text copy, scheduler engine, models |
| `:dictionary-core` | Kotlin JVM library | 7 Kotlin | Dictionary lookup interface + asset loader |
| `:writing-core` | Kotlin JVM library | 29 Kotlin | Handwriting analysis, stroke model, recognition types |
| `:update-core` | Kotlin JVM library | 18 Kotlin | Update policies, version parsing, artifact validation |
| `:fsrs-java` | Kotlin JVM library | 9 Kotlin | FSRS-6 implementation; historical module name retained for compatibility |
| `:domain` | Kotlin JVM library | 1 Kotlin | Shared domain model interfaces |
| `:sync-domain` | Kotlin JVM library | 8 Kotlin | Sync-specific domain models |
| `:build-logic` | Gradle convention plugin | — | Shared Kotlin library build conventions |

## Dependency graph (current)

```
app ──┬── core ──┬── dictionary-core
      │          ├── domain
      │          ├── sync-domain ── domain
      │          ├── fsrs-java
      │          └── update-core
      ├── dictionary-core
      ├── update-core
      └── writing-core ── domain
```

The `:app` module directly depends on `:core`, `:dictionary-core`, `:update-core`,
and `:writing-core`. All other modules are pulled transitively through `:core`
(via `api` declarations for `:dictionary-core`, `:domain`, `:sync-domain`).

## Problem statement

The `:app` module contains 365 tracked Kotlin files spanning:
- **Data layer** (65 files in `data/`): `LocalStore*`, settings, migrations, schema
- **Study UI** (~70 files): `MainActivityStudy*`, writing session, choice rendering
- **Home UI** (~50 files): `MainActivityHome*`, dashboard, focus queue, browse
- **Sync** (8 files in `sync/`): auto-sync, manual engine, retry workers
- **Backup** (7 files in `backup/`): WAL-safe snapshots, restore, validation
- **Update** (6 files in `update/`): GitHub updater, workers, notification
- **Widget** (18 files in `widget/`): four Glance widgets, snapshot loaders, configuration, refresh, and receivers
- **Reminders** (4 files in `reminders/`): scheduler, receivers, policies
- **Settings UI** (~30 files): all settings screens and panels
- **Top-level** (~100 files): activity hierarchy, theme, compose utilities, charts

Build times scale with module size (all 365 files can share one app compilation
boundary),
and test isolation requires the full app classpath.

## Proposed extraction targets

Ranked by independence (fewest inbound dependencies) and build-time impact
(file count × change frequency):

### 1. `:data` — LocalStore and persistence layer

**Files:** 65 files from `app/src/main/kotlin/.../data/`
**Contains:** `LocalStoreBase`, `LocalStoreStudy`, `LocalStoreInventory`,
`LocalStoreSync`, `LocalStoreSchema`, `LocalStoreMigrations`, `DictionaryStore`,
`StudyStatsStore`, `SettingsRepository`, `SqliteSettingsStorage`
**Dependencies needed:** `:core` (domain models), Android SQLite
**Effort:** Large — the `LocalStore*` hierarchy uses `abstract` hooks that
reference activity-level concerns (`backfillKanjiInventory`,
`rebuildSimilarKanjiChoiceStates`). Extracting requires interface extraction
from the abstract methods and dependency injection for any callbacks that
currently close over the store itself.
**Prerequisite:** DI framework (at minimum manual constructor injection) to pass
store instances into the activity without the activity needing to be the
`SQLiteOpenHelper` owner.

### 2. `:ui-study` — Study composables and models

**Files:** ~70 files (`MainActivityStudy*`, study composables, session models)
**Contains:** Study session rendering, writing session flow, choice cards,
flashcard rendering, study queue coordinator, review flow
**Dependencies needed:** `:core`, `:data`, `:writing-core`, Compose
**Effort:** Large — still coupled to `MainActivityStudy` (over 1,000 lines) via
mutable activity fields. Goal 136's `StudySessionViewModel` has landed, but the
remaining activity-owned state and host interfaces must move behind it before
module extraction is viable.

### 3. `:ui-home` — Home composables and models

**Files:** ~50 files (`MainActivityHome*`, home composables, browse detail)
**Contains:** Dashboard rendering, sync confirmation, focus queue, today plan,
metrics, browse/detail navigation
**Dependencies needed:** `:core`, `:data`, Compose
**Effort:** Medium — less mutable state than study, but the activity hierarchy
(`MainActivityBase → MainActivityHome → ...`) makes extraction non-trivial.
**Prerequisite:** Goal 137's `HomeViewModel` has landed; interface extraction
from `MainActivityBase` remains.

### 4. `:reminders` — Reminder scheduling and receivers

**Files:** 4 files from `app/src/main/kotlin/.../reminders/`
**Contains:** `ReminderScheduler`, `ReminderReceiver`, `BootReminderReceiver`,
`ReminderEligibilityPolicy` (in `:core`)
**Dependencies needed:** `:core` (for policy), `:data` (for store access),
AndroidX (AlarmManager, BroadcastReceiver)
**Effort:** Small — self-contained, minimal coupling to activity state.
**Prerequisite:** Interface for store access (the scheduler currently opens
its own `LocalStore` instance directly).

### 5. `:widget` — Home-screen widget

**Files:** 18 files from `app/src/main/kotlin/.../widget/`
**Contains:** overview, quick-study, activity, and focus widgets; their three
snapshot loaders; `WidgetLocalStoreReader`; configuration, registry, refresh,
and receiver infrastructure
**Dependencies needed:** `:core` (text copy), `:data` (store for due count),
AndroidX Glance
**Effort:** Medium — the family is isolated from activity state, but
`WidgetLocalStoreReader` directly opens a `LocalStore` and the extraction must
move all widget receivers, configuration, and refresh scheduling together.
**Prerequisite:** Minimal (store interface only).

### 6. `:backup` — Database backup and restore

**Files:** 7 files from `app/src/main/kotlin/.../backup/`
**Contains:** `DatabaseBackupWorker`, `DatabaseBackupScheduler`,
`BackupRestoreValidator`, `StagedRestoreApplier`, `DirectoryDurability`
**Dependencies needed:** `:data` (for `LocalStore.snapshotInto` and
`WalSafeSnapshotOperations`), Android API
**Effort:** Small-Medium — mostly self-contained but `StagedRestoreApplier` is
called from `KaniApplication.onCreate()` which creates a tight coupling.
**Prerequisite:** Restore API extraction (the applier can be called from the
application class via an interface).

## Recommended extraction order

```
Phase 1 (Isolated Android surfaces):
  ├── :widget          (18 files, medium effort, store-interface only)
  └── :reminders       (4 files, small effort, store-interface only)

Phase 2 (ViewModels landed; remaining host boundaries required):
  ├── :data            (65 files, large effort, needs DI)
  └── :backup          (7 files, small-medium, needs restore-API interface)

Phase 3 (After :data extraction):
  ├── :ui-home         (50 files, medium effort)
  └── :ui-study        (70 files, large effort)
```

## Estimated effort per extraction

| Target | Files | Effort | Prerequisites |
|--------|-------|--------|---------------|
| `:widget` | 18 | Medium (3–5 days) | Store read interface |
| `:reminders` | 4 | Small (1–2 days) | Store read interface |
| `:backup` | 7 | Small-Medium (2–3 days) | Restore API interface |
| `:data` | 65 | Large (1–2 weeks) | DI framework, interface extraction |
| `:ui-home` | 50 | Medium (3–5 days) | HomeViewModel, :data extracted |
| `:ui-study` | 70 | Large (1–2 weeks) | StudySessionViewModel, :data extracted |

## Prerequisites (shared)

1. **DI framework or manual injection pattern.** The activity hierarchy currently
   creates stores directly. A factory or constructor-injection pattern is needed
   before stores can live in a separate module.

2. **Interface extraction from `MainActivityBase`.** The 834-line base activity
   exposes `store`, `io`, `main`, `postToMainIfActive`, and theme methods that
   UI modules reference directly. An activity-hosted interface (or ViewModel
   access pattern) must replace direct field access.

3. **Finish the ViewModel boundary.** Goals 136 and 137 have landed
   `StudySessionViewModel` and `HomeViewModel`. Remaining activity-owned state
   and direct host/store access still need to move behind those boundaries
   before the UI modules can be extracted cleanly.

## Enforced boundaries today

`tools/test_module_boundaries.py`, run by `ciFast` through the normal tools
suite, locks the current project dependency DAG and verifies that every
pure-JVM module remains free of `android.*` and `androidx.*` imports. This is
deliberately narrower than the future extraction roadmap: it prevents new
coupling and cycles now without pretending the activity/data interfaces are
ready for a large module move.

The remaining split packages under `dev.bee.kanjianki.core` are known debt.
They should be resolved as part of a deliberate module extraction rather than
through package-only churn that provides no compile-time boundary.

## Notes

- The `:core` module is already well-extracted (163 files, pure JVM, no Android
  dependencies). It should remain the policy/model layer.
- `:fsrs-java` is a standalone Kotlin algorithm library (no Kani dependencies).
- `:dictionary-core` and `:writing-core` are already appropriately scoped.
- The extraction phases remain a roadmap; the current boundary test is the only
  enforcement change included here.
