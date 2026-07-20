# Architecture Modernization Goals (2026-07-20)

Each goal in this plan is an independently reviewable, release-ready slice.
Goal numbers continue from Goal 144 in
`plans/architecture-performance-and-quality-goals-2026-07-14.md`.

## Overall goal

Turn Kani's Android application layer into a pragmatic modular architecture
with explicit state, persistence, navigation, and Android-platform ownership.
The finished app keeps its current SQLite implementation and product behavior,
uses manual dependency injection, and has one `MainActivity` hosting a
Navigation Compose graph.

## Current state

This plan was prepared from `fix/offline-resilience` at `0e1f6bca`. The
architecture work must start from a clean, current `main` after that reliability
work is integrated rather than being added to the feature branch.

Current constraints that drive the plan:

- `:app` contains about 60,000 production lines and 352 Kotlin files.
- About 124 production files refer to the `MainActivity` hierarchy.
- `MainActivityBase` owns the database, executors, navigation, permission
  launchers, and mutable state shared by every feature.
- `HomeViewModel` is a proof of concept and is not wired into production Home.
- `StudySessionViewModel` owns authoritative route progress but substantial
  choice, writing, reveal, plan, and recovery state still lives on the Activity.
- `LocalStore` exposes a broad inheritance chain from schema through sync and
  is directly constructed by activities, workers, receivers, schedulers,
  updater code, backup code, and widgets.
- WAL and cross-instance cache invalidation already exist and must be retained.
- `tools/test_module_boundaries.py` currently validates the existing pure-JVM
  DAG and passes.

## Decisions

1. Keep `SQLiteOpenHelper`; do not migrate to Room.
2. Use manual dependency injection; do not add Hilt, Dagger, or Koin.
3. Pin `androidx.navigation:navigation-compose` to stable version `2.9.8`.
4. Use typed Kotlin destination objects with one centralized string/URI codec.
   Do not add Kotlin serialization solely for navigation.
5. Keep Study as one navigation destination. Card, feedback, repair, and Done
   phases remain scheduler/ViewModel state rather than back-stack entries.
6. Keep every goal releasable. Do not use a long-lived migration branch where
   the app is knowingly broken between goals.
7. Preserve current intent actions and extra keys so existing widget,
   notification, shortcut, and cached update PendingIntents remain valid.
8. Do not change scheduler rules, provider behavior, database schema, backup
   format, user-facing design, or app package identity as part of this plan.

## Target modules

```text
:app
  -> :feature-home
  -> :feature-study
  -> :feature-stats
  -> :feature-settings
  -> :sync-android
  -> :automation
  -> :widget
  -> :ui-common
  -> :data

:feature-* -> :ui-common, :data, :core
:sync-android -> :data, :core, :sync-domain, :dictionary-core
:automation -> :data, :core, :update-core
:widget -> :data, :core, :ui-common
:ui-common -> :core
:data -> :core, :dictionary-core, :sync-domain, :update-core
```

Additional feature-specific dependencies are allowed only where required:

- `:feature-study` may depend on `:writing-core` and `:dictionary-core`.
- `:feature-home` may depend on the public sync action contract from
  `:sync-android`.
- `:feature-settings` may depend on public automation action contracts.
- No feature module may depend on another feature module.
- No extracted module may import an application-module class.

## Shared contracts

The following are the intended cross-module APIs:

- `KaniContainer`: process-owned manual DI container.
- `KaniDependencyOwner`: implemented by `KaniApplication` for Android-created
  workers, services, receivers, and activities.
- `KaniDispatchers`: process-owned user-I/O and maintenance dispatchers.
- `HomeRepository`, `StudyRepository`, `StatsRepository`,
  `SettingsRepository`, and `SyncRepository`: suspend-based persistence ports.
- `KaniDestination`: sealed destination model.
- `KaniRouteCodec`: the only destination-to-route-string conversion point.
- `KaniLaunchContract`: stable action, extra, and MainActivity launch contract.
- `AppEventSink`: injected publication boundary for committed sync, study,
  restore, reminder, and widget-refresh events.

Repositories return immutable data snapshots or existing typed commit results.
Expected storage failures use `StoreResult`; raw SQLite objects and
`LocalStore` types never cross the `:data` boundary.

---

## Part A: Build and dependency foundations

### Goal 145: Add Android module conventions and architecture guardrails

**Outcome:** New Android modules can be added consistently without duplicating
SDK, Kotlin, lint, Compose, JaCoCo, or test configuration.

**Changes:**

- Add the Android library plugin to the version catalog.
- Add `kani.android-library-conventions` and
  `kani.android-compose-library-conventions` in `:build-logic`.
- Centralize compile SDK 36, min SDK 26, Java 17, warnings-as-errors lint,
  Compose setup, Robolectric resources, and Android-library coverage tasks.
- Pin Navigation Compose `2.9.8` in the version catalog.
- Extend `tools/test_module_boundaries.py` to model the target module classes,
  reject feature-to-feature dependencies, and fail closed on unparsed Gradle
  dependency declarations.
- Record clean and one-feature incremental compile timings before extraction.

**Done when:**

- A small fixture Android library can compile, lint, test, and publish coverage
  using only the convention plugin.
- Existing modules and `ciFast` remain green.
- Boundary tests explain every allowed dependency edge.

**Completion evidence (2026-07-20):**

- Work started from `origin/main` at `612afd4d`, after offline-resilience PR
  `#561` merged, on `architecture/goal-145-build-foundations`.
- `kani.android-compose-library-conventions` is exercised by a build script
  containing only that plugin. Its TestKit fixture compiles Compose plus a Java
  17 record, lints an API-26 call, runs a resource-backed Robolectric test, and
  passes both JaCoCo XML reporting and 100% class-coverage verification.
- The boundary test defines the current and final graphs separately, classifies
  every target module, rejects feature-to-feature edges, fails on unsupported
  Gradle project dependency syntax, and requires a rationale for every allowed
  migration or final edge.
- Pre-extraction compile baseline on this workstation, using no daemon and no
  build cache: clean `:app:compileDebugKotlin` was `46.14s`; recompiling after a
  temporary comment-only change to `MainActivityStats.kt` was `14.43s`. The
  timing probe was removed immediately after measurement.
- `./gradlew testBuildLogic --no-daemon --no-build-cache` passed 11 tests,
  including the isolated Android convention fixture. The repository tools
  suite passed 58 tests.
- `./gradlew ciFast ciQuality` with the prepared Android SDK environment
  completed successfully in `3m 7s` (110 tasks). After dependency-verification
  hardening, the final rerun also passed all 110 tasks (`4` executed, `106`
  up-to-date).
- Decisions are unchanged. Rollback is a source-only revert of the convention
  plugins, catalog/toolchain aliases, boundary policy, fixture, and CI task.
  No schema, persisted state, package identity, or runtime behavior changed.

### Goal 146: Remove app-layer dependencies from persistence contracts

**Outcome:** The persistence code can move to `:data` without depending on UI,
Activity, backup, or sync implementation classes.

**Changes:**

- Move study-item comparators and persisted review snapshot types to `:core`.
- Move the stored theme preference enum to a pure model in `:core`; keep palette
  and Compose theme resolution in the UI layer.
- Move pure sync-settings mapping out of `app/.../sync` into `:sync-domain` or
  `:core`, according to whether the type is sync-only or app-wide.
- Replace direct `AppDebugLog` imports in data code with an injected
  `DiagnosticLogger` interface whose production adapter remains in `:app`.
- Move WAL snapshot execution behind a data-owned `DatabaseSnapshotter`
  contract so persistence does not import backup implementation code.
- Add a source-boundary test that rejects imports from root app, feature,
  automation, widget, and sync implementation packages inside persistence.

**Done when:**

- The current `data/` package imports only Android database APIs, existing pure
  modules, and its own contracts.
- Existing schema, migration, review, sync, and backup tests pass unchanged.

---

## Part B: Persistence ownership and manual injection

### Goal 147: Define feature-oriented repository APIs

**Outcome:** UI and platform code depend on narrow storage capabilities rather
than the `LocalStore` implementation.

**Changes:**

- Create `HomeRepository` for dashboard, browse, detail, mnemonic, and local
  suspension data.
- Create `StudyRepository` for queue loading, review CAS commit, undo, repair,
  token, and recovery-related persistence.
- Create `StatsRepository` for cached and live analytics snapshots.
- Create `SettingsRepository` for typed settings snapshots and save commands;
  rename the existing key/value implementation to `SqliteSettingsStore`.
- Create `SyncRepository` for atomic mirror, queue, history, and write-back
  persistence.
- Add focused fake implementations for ViewModel and coordinator tests.

**Done when:**

- New UI or platform code can perform its work without accepting `LocalStore`.
- Repository APIs expose no `SQLiteDatabase`, cursor, table name, or Activity.
- Review and sync atomic operations remain one repository call each.

### Goal 148: Introduce the process-owned `KaniContainer`

**Outcome:** Object creation and lifetime are explicit and no longer owned by
the Activity hierarchy.

**Changes:**

- Create `KaniContainer` after `StagedRestoreApplier` permits startup.
- Make the container own repository implementations, `AppClock`,
  `KaniDispatchers`, provider clients, and app event publication.
- Move user-I/O and maintenance executors from `MainActivityBase` to the
  process container.
- Add `KaniDependencyOwner` to `KaniApplication`.
- Add a custom WorkManager `WorkerFactory` that resolves dependencies lazily
  after the restore gate.
- Let services and receivers resolve only their narrow dependency interfaces
  through `KaniDependencyOwner`.
- Add a test container override instead of adding new global runtime override
  objects.

**Done when:**

- Activity recreation does not close the database or executors.
- Workers, services, receivers, and activities use the same dependency graph.
- The restore gate still runs before any database-capable dependency opens.

### Goal 149: Extract `:data` with the existing facade intact

**Outcome:** Persistence has a compile-time module boundary before its internal
implementation is refactored.

**Changes:**

- Move schema, migrations, `LocalStore*`, dictionary installation, settings,
  stats queries, and repository adapters into `:data`.
- Keep `LocalStore` and the inheritance chain internal as a temporary
  compatibility facade.
- Move data tests and Android database instrumentation tests with the module.
- Keep end-to-end migration and backup/restore instrumentation in `:app` where
  they exercise application startup.
- Make every non-data consumer use repository interfaces.

**Done when:**

- No production source outside `:data` imports or constructs `LocalStore`.
- `:app` contains no raw SQL or Android database imports.
- Existing DB v32 migration, downgrade, WAL, cache, and snapshot tests pass.

### Goal 150: Replace `LocalStore` inheritance with focused stores

**Outcome:** Persistence is internally composed by capability rather than
inherited through one broad object.

**Changes:**

- Introduce internal `KaniDatabase`, `DatabaseSession`, and transaction helpers.
- Create composed `StudyStore`, `InventoryStore`, `StatsStore`, `SyncStore`,
  `SettingsStore`, and `BackupStore` implementations.
- Replace abstract migration callbacks with one explicit `MigrationHooks`
  implementation assembled inside `:data`.
- Pass transaction-local database sessions into operations that must publish
  review or sync state atomically.
- Migrate one capability at a time behind unchanged repository interfaces.
- Delete the `LocalStoreBase -> History -> Study -> SimilarKanji -> Inventory
  -> Sync -> LocalStore` inheritance chain after the last adapter is removed.

**Done when:**

- No persistence implementation uses `this as LocalStore`.
- Repository contract tests pass against the composed implementation.
- Review CAS, sync atomic publication, cache invalidation, and snapshot
  consistency retain their existing golden behavior.

---

## Part C: Feature state ownership

### Goal 151: Make Home, Browse, and Games state lifecycle-aware

**Outcome:** Home-family screens are driven by ViewModel state rather than
Activity fields and render callbacks.

**Changes:**

- Replace the Home proof-of-concept loader with production `HomeViewModel`.
- Add route-scoped `BrowseViewModel` and `GamesViewModel`.
- Model each screen as `Loading`, `Content`, or `Error` immutable UI state.
- Move browse query/scope, focus queue, detail origin, game round, sync result,
  and scroll restoration state out of `MainActivityHome`/`Games`.
- Emit effects for permission requests, sync confirmation, external URLs, and
  transient messages; keep Android execution in route adapters.
- Keep model construction off the main thread through injected dispatchers.

**Done when:**

- Home-family composables accept state and callbacks, never an Activity.
- Rotation preserves the active Home subroute state and query.
- Existing Home, Browse, Games, sync result, screenshot, and latency tests pass.

### Goal 152: Make Stats state lifecycle-aware

**Outcome:** Stats loading and presentation are independently testable and no
longer owned by `MainActivityStats`.

**Changes:**

- Add `StatsViewModel` using `StatsRepository`.
- Represent cache hit, background refresh, empty, content, and retryable error
  states explicitly.
- Move progress analytics model construction and refresh generation tracking
  into the ViewModel.
- Keep chart composables pure and retain existing cache format contracts.

**Done when:**

- Stats renders from immutable state with fake-repository tests.
- Re-entry and rotation do not restart an accepted refresh unnecessarily.
- Existing analytics, cache, screenshot, and route tests pass.

### Goal 153: Make Settings state and effects lifecycle-aware

**Outcome:** Settings no longer stores navigation, pending save, permission, or
document-picker state on the Activity.

**Changes:**

- Add `SettingsViewModel` with typed settings snapshots and save commands.
- Keep each settings submenu as a route state, not a recreated whole screen.
- Model notification permission, install permission, time picker, backup
  export, restore selection, share sheet, and app restart as one-shot effects.
- Preserve current Activity Result launcher registration order while legacy
  saved results may still exist.
- Keep settings scroll state keyed by destination.

**Done when:**

- Settings composables have no Activity or direct repository dependency.
- Pending permission and document-picker flows survive recreation.
- Existing Settings defaults, async loading, category, locale, and backup tests
  pass.

### Goal 154: Complete Study state ownership

**Outcome:** `StudySessionViewModel` is the only in-memory authority for the
current Study route while the database remains the durable review authority.

**Changes:**

- Move active plan, reveal, typed draft, choice, writing, hints, submission,
  feedback, undo presentation, and route completion state into the ViewModel.
- Retain token-first revision-CAS persistence and advance UI only for APPLIED
  commits.
- Keep the existing durable recovery envelope and SharedPreferences store;
  Navigation state must not serialize scheduler items or review commands.
- Inject `StudyRepository`, writing recognizer, dictionary provider, clock,
  event sink, and dispatchers behind interfaces.
- Keep `DrawingPadView` and Activity Result execution as Android adapters fed by
  ViewModel state.
- Remove Study state fields and route mutation methods from the Activity.

**Done when:**

- Study composables accept immutable route state and event callbacks only.
- Rotation/process recreation, pending feedback, explicit Continue, undo,
  learn-ahead, and stale callback tests remain green.
- No navigation back-stack entry is created per card or feedback phase.

---

## Part D: Shared UI and Navigation Compose

### Goal 155: Extract `:ui-common` and define navigation contracts

**Outcome:** Shared Compose UI and navigation types no longer depend on the
application Activity.

**Changes:**

- Move theme, shell, bottom navigation, shared loading/error surfaces, common
  controls, and UI tokens into `:ui-common`.
- Add sealed `KaniDestination` types for top-level, Home subroutes, Settings
  subroutes, Study, Stats, update, and test harness destinations.
- Add `KaniRouteCodec` with URI-safe encoding for browse queries and explicit
  validation for kanji detail arguments.
- Add `KaniLaunchContract` using the current action and extra strings.
- Keep the stable MainActivity class name in one tested launch-intent factory
  so extracted Android modules do not import `:app`.
- Add route codec and legacy-intent mapping tests.

**Done when:**

- Shared UI has no `MainActivity*` imports.
- Raw route strings exist only inside `KaniRouteCodec` and NavHost registration.
- Existing widget/notification/shortcut intents decode to the same destination.

### Goal 156: Replace custom routing with Navigation Compose

**Outcome:** One `MainActivity` hosts one `KaniApp` composition and one NavHost.

**Changes:**

- Install Navigation Compose `2.9.8` and register the complete route graph.
- Use `popUpTo(Home)`, `launchSingleTop`, saved state, and restored state for
  top-level navigation.
- Preserve current back rules:
  - Home root exits.
  - Study, Stats, and root Settings return Home.
  - Settings subroutes return their parent.
  - Browse detail returns Browse.
  - Focus-widget detail returns all-kanji Browse.
  - Game rounds/results return Games.
- On startup, resolve destination precedence as: new explicit intent, current
  study recreation marker/recovery, restored NavController state, then Home.
- Handle `singleTop` warm intents through the same destination mapper.
- Flush/abandon Study before leaving its route.
- Delete `MainActivityShellHost`, `currentRoute`, custom `backAction`, repeated
  render methods, and the deep Activity inheritance chain.

**Done when:**

- `MainActivity` contains only lifecycle setup, Activity Result adapters,
  intent forwarding, and `setContent { KaniApp(...) }`.
- Route, back, warm-intent, widget, shortcut, rotation, and study recovery
  instrumentation tests pass on API 26 and API 35.
- The screenshot and button-latency harnesses address routes through
  `KaniLaunchContract`.

---

## Part E: Feature module extraction

### Goal 157: Extract `:feature-stats`

**Outcome:** The smallest completed feature proves the feature-module pattern.

**Changes:**

- Move Stats ViewModel, UI state, route composables, charts, and unit tests.
- Export one route composable and one ViewModel factory.
- Keep all other implementation symbols module-internal.

**Done when:**

- A Stats-only source change does not recompile Home, Settings, or Study.
- Stats module tests and app route instrumentation pass.

### Goal 158: Extract `:feature-settings`

**Outcome:** Settings UI depends only on typed persistence and automation
contracts.

**Changes:**

- Move Settings ViewModel, subroutes, models, composables, and tests.
- Keep SAF, permission, share, restart, and external-settings execution in the
  app adapter through effects.
- Depend on `:automation` interfaces rather than updater/backup implementations.

**Done when:**

- Settings has no app-module or concrete automation imports.
- Every submenu compiles and tests independently.

### Goal 159: Extract `:feature-home`

**Outcome:** Home, Browse, Focus Queue, Recent Mistakes, Kanji Detail, Games,
and sync-result presentation form one cohesive feature module.

**Changes:**

- Move the Home-family ViewModels, UI states, models, composables, and tests.
- Use the public sync action contract for manual sync.
- Keep Android permission and external AnkiDroid install actions as effects.

**Done when:**

- Home contains no direct provider, SQLite, Activity, widget, or updater access.
- Home/Browse/Games tests and primary route instrumentation pass.

### Goal 160: Extract `:feature-study`

**Outcome:** Study becomes an independently compiled feature with scheduler
behavior supplied by pure modules and persistence supplied by repositories.

**Changes:**

- Move Study ViewModel, route state, recovery models, card composables, choice
  flows, writing flows, answer details, and tests.
- Keep ML Kit implementation and Android drawing adapter behind injected
  writing interfaces.
- Keep all scheduler decisions in `:core`; do not fork or duplicate policy in
  the feature.

**Done when:**

- Study has no app-module imports and no direct SQLite/provider access.
- All study route, lifecycle, feedback, writing, choice, and golden scheduler
  tests pass.
- A Study-only change does not recompile Home, Settings, or Stats.

---

## Part F: Android platform module extraction

### Goal 161: Extract `:sync-android`

**Outcome:** AnkiDroid provider integration and sync orchestration have one
Android module and one public action boundary.

**Changes:**

- Move AnkiDroid gateway/readers/tagging, manual sync, auto-sync scheduling,
  JobService, retry workers, cancellation, and sync progress.
- Expose `SyncActions` for manual sync and a narrow scheduler contract for
  automation.
- Publish committed-sync events through `AppEventSink` instead of importing
  widget or reminder implementations.
- Move service manifest entries and consumer R8 rules with the module.

**Done when:**

- Sync imports no Activity, feature, widget, or automation implementation.
- Fake-provider contract tests, API 26/35 instrumentation, and the live
  AnkiDroid provider suite pass.
- Before release, run the strict local copied-collection gate with the default
  7,000-note minimum.

### Goal 162: Extract `:automation`

**Outcome:** Backup/restore, reminders, updater, FSRS fitting, and background
schedule ownership are separated from UI and sync implementation.

**Changes:**

- Move backup workers and restore staging, reminder receivers/schedulers,
  Android update workers/install handling, FSRS fit worker/scheduler, and
  receiver async helpers.
- Expose typed action interfaces consumed by Settings and app startup.
- Resolve sync rescheduling through an injected scheduler contract.
- Keep staged restore callable before `KaniContainer` creation.
- Move manifest entries, resources, and consumer R8 rules with the module.

**Done when:**

- Automation imports no Activity or feature implementation.
- Restore still blocks database-capable startup on marker-bearing failure.
- Backup, reminder, update offline, worker, and minified-smoke tests pass.

### Goal 163: Extract `:widget` and complete the architecture gate

**Outcome:** All Glance widgets are independently owned and `:app` is only the
composition root.

**Changes:**

- Move the four widget families, snapshot loaders, configuration Activity,
  receivers, alarms, resources, previews, and tests.
- Use repositories for widget snapshots and `KaniLaunchContract` for taps.
- Consume committed app events for refresh rather than accepting imports from
  sync, automation, or feature modules.
- Update root Sonar binary/coverage paths, `ciFast`, `ciQuality`, Android CI
  matrices/path filters, CodeQL compile coverage, release workflow tests, and
  module boundary expectations for every final module.
- Replace `docs/modularization-roadmap.md` with the achieved graph, ownership
  rules, and instructions for adding a feature.
- Remove compatibility facades, dead route adapters, stale architecture
  comments, and obsolete direct-store test helpers.

**Done when:**

- No production code outside `:data` constructs or imports `LocalStore`.
- No feature composable accepts an Activity.
- No ViewModel depends on `Context`, `Activity`, SQLite, or a concrete Android
  platform implementation.
- No legacy `MainActivityBase`, `MainActivityHome`, `MainActivityStudy`,
  `MainActivityStats`, `MainActivityGames`, or `MainActivitySettings` remains.
- The enforced module graph matches the target graph and is acyclic.
- A source-only change in one feature leaves unrelated feature compile tasks
  up-to-date.
- `ciFast`, `ciQuality`, lint, minified smoke, API 26/35 device smoke, device
  risk, screenshots, and the required live provider gate all pass.

## Per-goal delivery rules

Every goal must:

1. Start from current `main` and use a focused branch/PR.
2. Preserve a green build before moving to the next goal.
3. Move tests with code and add boundary tests before deleting compatibility
   paths.
4. Update this file with completion evidence and any deliberately revised
   decision.
5. Avoid opportunistic scheduler, schema, provider, copy, or visual changes.
6. Include a rollback path that restores the previous adapter without database
   migration or user-data transformation.

## Standard validation

Run for every goal:

```sh
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
  ./gradlew ciFast ciQuality
```

Also run when relevant:

- Navigation or feature route changes: API 26/35 route instrumentation and the
  screenshot matrix.
- Manifest, R8, worker, receiver, or widget changes: minified device-risk suite.
- Backup/data lifetime changes: backup/restore instrumentation and process
  recreation tests.
- Sync/provider changes or module moves: fake-provider contracts, real
  AnkiDroid fixture, and the strict local release gate required by `AGENTS.md`.
