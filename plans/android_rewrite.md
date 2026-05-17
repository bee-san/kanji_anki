# Kani Android Rewrite Plan

Status: planning document  
Target app: Kani Android, `dev.bee.kanjianki`  
Primary goal: rewrite the app into an ideal maintainable Android architecture while preserving the current product behavior exactly.

## 1. Executive Summary

Kani should be rewritten as a Kotlin-first, Compose-first, local-first Android app with clean domain, data, sync, FSRS, and UI boundaries.

The rewrite is not a product reset. The app must behave exactly as the current Android app behaves unless a later product decision explicitly changes behavior. The current app already contains the right product concepts: AnkiDroid provider sync, local Kani study ownership, a single configurable ladder, FSRS scheduling, Pareto focus, handwriting guidance, similar-kanji practice, local dictionary assets, GitHub APK updates, daily background sync, and release verification. The rewrite should preserve those concepts and remove the structural problems around them.

The current implementation is passing and useful, but it is structurally expensive to evolve. The main maintainability issues are:

- The UI is spread across large `MainActivity*` inheritance slices, with Study alone over 2,000 lines and Settings over 1,700 lines.
- Persistence is a large `SQLiteOpenHelper` inheritance chain with raw SQL, schema strings, manual migrations, and too many responsibilities in the `LocalStore*` family.
- Records and DTOs are grouped into broad compatibility namespaces rather than focused model packages.
- Background sync, import selection, queue seeding, and UI state are too tightly coupled in places.
- The current UI is Java/programmatic Views rather than a declarative state-driven architecture.

The rewrite should produce:

- One Android app with no backend, no web runtime, and no Python fixture runtime.
- A single-activity Compose app.
- Kotlin modules with strict dependency direction.
- Room as the source of truth for structured local data.
- DataStore for small user/app settings.
- Hilt for dependency injection.
- Coroutines and Flow for asynchronous data.
- WorkManager for background sync, backup, update checks, and other durable background jobs.
- A pure Kotlin or Java/Kotlin FSRS module owned by us, based on the current in-repo 21-parameter FSRS engine and upstream conformance fixtures.
- Domain logic that is pure JVM/Kotlin wherever possible and extensively unit tested.
- UI screens that render immutable `UiState` and send explicit user actions to ViewModels.
- No god classes, no massive files, no cross-layer shortcuts, and no UI logic hidden in persistence or sync classes.

The end state is not merely "Compose UI." The end state is a maintainable product architecture where an engineer can add a new study rung, import source, stat, setting, or writing-feedback behavior without editing a 2,000-line activity or touching unrelated database code.

## 2. Non-Negotiable Product Contract

The rewritten app must behave exactly like the current app unless a later task explicitly approves a behavior change. Treat every difference as a regression until proven intentional.

### 2.1 App Identity

Preserve:

- Android package/application id: `dev.bee.kanjianki`.
- App name: `Kani`.
- Sideloaded APK distribution through GitHub Releases.
- Signed release APK plus SHA-256 checksum.
- Existing install/update path.
- Existing requirement that Kani is an AnkiDroid companion app.

Do not introduce:

- A hosted backend.
- User accounts.
- Cloud sync.
- Play Store-only distribution.
- A WebView app.
- A Python server, fixture runtime, or polling loop.
- A dependency on desktop Anki runtime.

Database compatibility note:

- Existing local Kani databases may be reset, replaced, or destructively
  migrated when that produces a cleaner Room/DataStore architecture.
- Do not preserve legacy DB shape, key/value settings, or encoded columns just
  to avoid a local reset.
- Product behavior still matters. After a reset, the app must rebuild from
  AnkiDroid/source assets through the intended sync path and must not corrupt
  AnkiDroid data.
- Destructive paths must be explicit in code and user-facing status. Avoid
  silent partial migrations that leave mixed old/new state behind.

### 2.2 AnkiDroid Relationship

Kani is not an AnkiDroid skin and not a generic Anki scheduler.

Preserve this ownership split:

- AnkiDroid is the source collection and provider.
- Kani reads evidence from AnkiDroid through the flashcard provider.
- Kani owns its local study queue, local review state, local FSRS memory, writing hints, repair state, stats, and explanations.
- Kani may archive/tag/remove source cards in AnkiDroid only through existing provider-supported behavior.
- Kani must remain useful offline after local data has been synced.

Provider behavior must remain:

- Manual sync reads AnkiDroid's exported flashcard provider.
- Daily auto sync starts only after the first successful manual sync.
- Daily auto sync uses the same provider sync path as manual sync.
- The provider authority and permission handling must continue supporting real AnkiDroid and debug/test provider targets.
- Provider failures must be classified as permanent or retryable in the same way users currently see.
- Missing provider, missing permission, wrong note type, wrong card template, and provider query failures must produce user-facing status instead of crashes.
- Live-provider regressions must be tested with a real AnkiDroid emulator gate when sync/provider behavior changes.

### 2.3 Kiku Note Contract

Preserve current defaults:

- Note type: `Kiku`.
- Template/card: `Mining`.
- Required fields:
  - `Expression`
  - `ExpressionReading`
  - `MainDefinition`
  - `Sentence`
  - `Frequency`
  - `FreqSort`

Preserve current settings behavior:

- Users can edit note type and field mappings.
- "Use Kiku" resets mappings to Kiku defaults.
- The app can choose the note type from AnkiDroid.
- Settings must survive app upgrades.
- Old installs must retain their persisted settings unless a migration intentionally changes a key.

### 2.4 Import Contract

Preserve current import sources and defaults:

- Suspended cards are imported by default.
- Active-card import is opt-in.
- Tagged-card import is opt-in.
- Weak-card import is opt-in.
- Browser-query import is opt-in.
- Suspended import rank range defaults to Jiten ranks `100` through `3000`.
- Minimum matching cards per kanji defaults to current behavior.
- Kani must never silently flip old persisted installs from suspended-only behavior to broader active-card import.

Preserve current import behavior:

- Suspended cards are archived locally.
- Suspended-card import uses dedicated suspended-kanji logic.
- Active mirror and suspended archive both feed weak-kanji rows/details.
- Kani stores source notes, source cards, suspended archive rows, suspended imports, suspended sources, dashboard rows, kanji examples, kanji inventory, import audit data, timeline events, sync runs, historical sync snapshots, similar-kanji pairs, similar-choice state, similar-writing repair state, study items, learning repeats, review logs, study task logs, settings, and stats.
- Kani must not throw away deliberate dedupe behavior. Existing dedupe in `SuspendedKanjiImporter`, `ManualSyncEngine`, `KanjiAnalyzer`, timeline events, and review-token protection exists for product correctness.
- FSRS fields read from AnkiDroid source cards must remain defensive. Missing or unparseable source FSRS fields are "unavailable", not sync failures.
- Browser-query import must read configured query matches without turning missing note/card projections into hard failures.

### 2.5 Study Contract

Preserve:

- `Study now` is the single main study entry point.
- Kani focuses on "problem-child" kanji.
- Kani uses a small Pareto-style focus set by default.
- Default max focus size is current behavior, currently up to `5` high-impact kanji unless settings say otherwise.
- The user can continue all kanji or study extra new cards when the current focus is done.
- Progress shown in Study must be truthful. If the UI says `N / N`, the current Study workload must actually be done.
- Similar-kanji side work and writing repairs that can still interrupt Study must be included in done/progress truth.
- Study reveal/answer/pass/fail controls must stay above the fold. Scrolling to reveal or grade is a bug.
- Flashcard gestures are allowed only where current behavior allows them; grading gestures should only work after reveal.
- Pass/Fail labels map to core ratings at the UI boundary as before.

### 2.6 Scheduler And Ladder Contract

Preserve the single ladder model:

- The scheduler is one configurable ladder state machine.
- Every persisted study item has exactly one current rung and one phase.
- Do not introduce a parallel "main study item plus side queue" scheduler model.
- `learning_repeats`, similar-choice state, and similar-writing repair state are not independent scheduler queues.
- The scheduler consumes persisted `study_items`.
- `similar_kanji_pairs` is data for `hasSimilarKanji`, not a scheduler queue.

Preserve current default ladder order from low/easy/remedial to high/contextual:

1. `write_kanji`
2. `similar_kanji`
3. `type_meaning`
4. `meaning_kanji`
5. `kanji_meaning`
6. `font_meaning`
7. `word_reading`

Preserve current rung rules:

- `meaning_kanji` exists in editable default order but is off by default.
- Users can turn rungs on/off.
- Users can move rungs up/down.
- New cards start at `kanji_meaning`.
- If `kanji_meaning` is disabled, new cards start at the nearest enabled rung, preferring the lower/easier rung on distance ties.
- `similar_kanji` exists only when valid similar-kanji content exists for the card.
- If `similar_kanji` is unavailable, promotion/demotion crosses over it without pausing.
- Settings must keep at least one always-available rung enabled.
- `similar_kanji` alone is not a valid only-enabled rung because it depends on per-card data.

Preserve phases:

- `new_learning`
- `review`
- `relearning`

Preserve Anki-exact learning/relearning semantics:

- `Again` returns to the first step.
- `Good` advances one step and graduates after the last step.
- `Hard` on the first step uses a delay between Again and Good.
- `Hard` on later steps repeats the current step.
- `Easy` graduates immediately.
- Learning/relearning repeats are practice-only and do not advance promotion, demotion, or long-term scheduler thresholds.
- Only persisted FSRS-due review attempts in the `review` phase count toward ladder movement.
- The "real due" boundary is the task's persisted FSRS due time, not the calendar day and not a learning-repeat queue.

Preserve ladder movement:

- A due-review `Hard`, `Good`, or `Easy` counts as a pass.
- A due-review pass promotes only when the FSRS result schedules the next review strictly more than `ladder_promotion_interval_days` into the future.
- Default promotion threshold is `21` days.
- A due-review `Again` increments consecutive fail streak.
- Demotion happens when consecutive due-review fail streak reaches `ladder_demotion_fail_streak`.
- Default demotion threshold is `3`.
- At `write_kanji`, further `Again`s stay at the floor.
- At `word_reading`, further passes stay at the ceiling.
- `hard`, `good`, and `easy` all count as ladder passes.
- Only `again` counts as ladder failure.

Preserve review ratings:

- Core scheduler keeps `again`, `hard`, `good`, and `easy`.
- UI can present `Pass` and `Fail`.
- UI maps `Pass` to `good`.
- UI maps `Fail` to `again`.
- `write_kanji` shows only Pass/Fail, not Hard/Easy.

### 2.7 FSRS Contract

Current app behavior uses the in-repo `:fsrs-java` 21-parameter engine through `LatestFsrsAdapter`. There is no current runtime switch back to the legacy FSRS-5 engine.

Preserve:

- FSRS memory maths belongs in a narrow `fsrs` module.
- Kani scheduler policy stays outside FSRS.
- FSRS computes memory state, retrievability, difficulty, stability, and next interval.
- Kani decides study item selection, ladder movement, learning/relearning steps, sibling suppression, repair queues, import rules, and UI wording.
- The current 21-parameter FSRS behavior must be the baseline for parity tests.
- Rollback from a future FSRS rewrite is a source-control revert or module replacement, not an in-process legacy FSRS-5 switch unless explicitly reintroduced.

Preserve current FSRS algorithm facts unless upstream update is intentionally chosen:

- Upstream source of truth is `open-spaced-repetition/py-fsrs` tag `v6.3.1`.
- Upstream tag commit is `3abe686e9c058d3f3c00bbeb92e68b71211b2b31`.
- Pinned `fsrs/scheduler.py` blob is `6d42ecb259bbaaa02101f13c5e1b2ec7cdc77eae`.
- Algorithm label is FSRS-6-family 21-parameter snapshot, not FSRS-7 unless upstream explicitly labels it that way.
- Default parameters are the current 21-value set.
- Same-day review path must use short-term stability.
- Relearning graduation must preserve post-lapse stability/difficulty correctly.
- On-time reviews must feed FSRS the full elapsed scheduled interval, not `0` overdue days.
- Ladder movement must update the newly active rung's task memory so promoted rungs do not fall back to empty memory.

If FSRS is rewritten in Kotlin:

- It must match the current Java engine's generated reference fixture before being used by the app.
- It must keep the same public app-facing adapter semantics.
- It must keep the current validation policy or stricter.
- It must include cross-language golden tests from current Java output and pinned `py-fsrs` fixtures.
- It must not smuggle Kani scheduler policy into the FSRS module.

### 2.8 Writing And Handwriting Contract

Preserve:

- Writing uses ML Kit Digital Ink Recognition.
- Stroke-order guide assets come from bundled KanjiVG-derived data.
- Guided handwriting is Ringotan-like: the app gives help first and gradually removes help.
- `HintLevel` concept remains:
  - `TRACE`
  - `OUTLINE`
  - `MINIMAL`
  - `BLIND`
- `HintPolicy` decides stroke visibility.
- `HintProgression` decides how hints advance or fall back.
- Writing diagnosis is tutor-only by default.
- Diagnosis must not directly change grading/scheduler behavior unless explicitly requested.
- Replay stays inside the same pad, not in a separate pane.
- Undo path remains visible.
- Off-guide blocking remains proactive where current behavior has it.
- Far-off active strokes can be discarded without corrupting previous accepted strokes.
- `WritingRatingMapper` caps messy/close outcomes as currently intended.
- A flashcard that was missed and then repaired through writing can pass, but its stored rating must not become a full normal `good`/`easy` when current behavior caps it.

### 2.9 Similar-Kanji Contract

Preserve:

- Similar-kanji data and lookup foundations stay local.
- Similar-kanji practice defaults to local inventory/evidence.
- `similar_kanji` rung is conditional on valid local similar-kanji content.
- Similar-choice tasks and similar-writing repairs are not independent scheduler queues.
- Similar-kanji work can affect Study progress truth.
- Similar-kanji pairs are retained as data source for `hasSimilarKanji`.
- Mature/suspended cards may be used as content sources as current behavior allows, without mutating mature-card review data directly.

### 2.10 Stats Contract

Preserve current user-facing stats behavior unless a later product decision changes it:

- Stats should explain learner impact, not merely technical counters.
- Outcome stats and "Kani is helping / not helping / too few data points" style conclusions should be supported by local evidence.
- Answered-task time should be available for answered task types.
- Study task logs and review logs must remain rich enough for future Stats features.
- Do not drop local evidence during migration just because the current UI only displays a subset.

### 2.11 Dictionary And Assets Contract

Preserve:

- Runtime dictionary scope is kanji-focused.
- Bundled dictionary assets include KANJIDIC2/Jiten-derived data.
- Dictionary clue format is learner-facing:
  - meaning first
  - then reading
  - then `From: ...`
- Do not reintroduce noisy labels such as `Meaning:` in learner-facing clues unless explicitly requested.
- Strip POS/noise as current code expects.
- Visible readings can be capped, but hidden readings remain searchable.
- Dictionary source attribution and data-license surfaces must remain reachable.
- Font attribution and bundled font behavior must be preserved.

### 2.12 Update And Release Contract

Preserve:

- GitHub Releases are the app update source.
- Release assets are APK plus SHA-256 checksum.
- App updater verifies downloads and opens Android package install confirmation.
- Update checks must not continue stale async UI work after navigation away.
- Release builds require signing.
- Release workflow must publish assets for semver tags.
- Release gate includes `ciFast` and signed release build.
- Sync/provider release changes require live AnkiDroid emulator validation as documented in `agents.md`.

## 3. Target Architecture

### 3.1 Architectural Style

Use the standard modern Android layered architecture:

```text
Compose UI
  -> screen ViewModels
    -> domain use cases
      -> repositories
        -> local data sources, AnkiDroid provider, asset readers, ML Kit, GitHub release API
```

Rules:

- UI renders state. UI does not read databases, providers, files, or network directly.
- ViewModels expose immutable `StateFlow<UiState>`.
- UI sends explicit actions to ViewModels.
- ViewModels call use cases or repositories.
- Use cases coordinate business rules.
- Repositories expose data streams and transactional actions.
- Data sources do one IO job each.
- Domain modules contain no Android framework dependency.
- Android framework dependencies live at the edge: app, data-android, ankidroid, writing-android, update-android, workers.
- Dependencies point inward, never outward.

Official Android guidance aligns with this shape: separate UI/data/domain layers, repositories between UI and data sources, UDF, ViewModels, coroutines/Flow, Compose, Room, DataStore, and Hilt.

### 3.2 Target Module Graph

Recommended final modules:

```text
:app
  depends on :feature-home, :feature-study, :feature-settings, :feature-stats,
             :feature-browse, :feature-sync, :feature-update,
             :data, :ankidroid, :writing-android, :dictionary-android,
             :sync-android, :update-android, :backup-android,
             :domain, :designsystem

:designsystem
  depends on AndroidX Compose only

:domain
  depends on :fsrs, Kotlin stdlib/coroutines only

:fsrs
  pure JVM Kotlin or Java/Kotlin, no Android dependency

:data
  Room entities, DAOs, repositories, migrations, DataStore settings
  depends on :domain, :dictionary-core, :fsrs where needed

:ankidroid
  ContentResolver/provider gateway and provider DTO mapping
  depends on Android SDK and :domain

:sync-domain
  import/sync use cases that coordinate snapshots, imports, queue seeding
  depends on :domain

:sync-android
  WorkManager, notification/progress integration, gateway wiring
  depends on :sync-domain, :ankidroid, :data

:writing-core
  pure handwriting models, stroke analysis, hint policy, guide geometry

:writing-android
  ML Kit recognizer, Compose/Android drawing pad integration, asset loading
  depends on :writing-core

:dictionary-core
  dictionary lookup interfaces and formatting

:dictionary-android
  bundled SQLite asset / Room prepackaged asset access and attribution readers

:update-core
  release parsing, version policy, checksum policy

:update-android
  GitHub HTTP client, APK file provider, installer status receiver, WorkManager checks

:backup-android
  local database backup worker/scheduler

:feature-home
:feature-study
:feature-settings
:feature-stats
:feature-browse
:feature-sync
:feature-update
  Compose screens and ViewModels
```

This is the ideal end-state. During implementation, it is acceptable to collapse small feature modules into `:app` temporarily if doing so keeps the rewrite safer, but the final design must preserve the same dependency boundaries by package and tests.

Minimum acceptable module split:

```text
:app
:designsystem
:domain
:data
:ankidroid
:writing-core
:writing-android
:dictionary-core
:dictionary-android
:fsrs
```

Do not ship the rewrite with everything inside `:app`.

### 3.3 Dependency Direction

Allowed:

```text
feature -> domain
feature -> designsystem
feature -> AndroidX lifecycle/navigation/compose
app -> feature modules
app -> concrete Android implementations
data -> domain
data -> Room/DataStore
ankidroid -> domain
sync-android -> sync-domain/data/ankidroid
writing-android -> writing-core
domain -> fsrs
```

Forbidden:

```text
domain -> app
domain -> Android Context
domain -> Room
domain -> ContentResolver
domain -> ML Kit
domain -> Compose
domain -> WorkManager
feature UI -> Room DAO
feature UI -> ContentResolver
feature UI -> SQLiteDatabase
feature UI -> DataStore directly
ViewModel -> Context
ViewModel -> Activity
ViewModel -> Resources
repository -> composable
DAO/entity -> composable
FSRS -> Kani study ladder policy
```

### 3.4 Single Activity

Use one `MainActivity`.

Responsibilities:

- Set content.
- Own the navigation host.
- Apply app theme and edge-to-edge/system bar settings.
- Request top-level permissions when routed through a ViewModel/event contract.
- Host app-level lifecycle wiring.

`MainActivity` must not:

- Query AnkiDroid.
- Read/write Room directly.
- Own Study session state.
- Build individual screen layouts.
- Handle sync logic.
- Hold global mutable state for screens.
- Exceed a strict size limit.

Target size:

- `MainActivity.kt`: under 150 lines.
- If it grows above 200 lines, split responsibilities immediately.

### 3.5 Feature Screen Pattern

Each feature screen follows this pattern:

```text
feature-study/
  StudyRoute.kt
  StudyScreen.kt
  StudyViewModel.kt
  StudyUiState.kt
  StudyAction.kt
  StudyEvent.kt
  components/
    StudyTopBar.kt
    FlashcardPrompt.kt
    WritingPrompt.kt
    SimilarChoicePrompt.kt
    StudyActionBar.kt
```

Route composable:

- Obtains ViewModel.
- Collects `uiState` with lifecycle awareness.
- Connects one-shot effects to navigation, snackbars, permission launchers, install intents, or model downloads.
- Passes state and callbacks to the stateless screen composable.

Screen composable:

- Pure UI.
- No repository calls.
- No database calls.
- No provider calls.
- No threading.
- No long-running logic.

ViewModel:

- Exposes `StateFlow<UiState>`.
- Accepts explicit actions.
- Calls use cases.
- Does not hold Android `Context`, `Activity`, `Resources`, `View`, `ContentResolver`, or `SQLiteDatabase`.
- Uses injected dispatchers/use cases/repositories.
- Converts domain errors to UI state or one-shot events.

Use case:

- Encapsulates a product action.
- Names should be command/query oriented:
  - `RunManualSyncUseCase`
  - `GetHomeSummaryUseCase`
  - `StartStudySessionUseCase`
  - `SubmitStudyReviewUseCase`
  - `RevealFlashcardAnswerUseCase`
  - `CheckWritingUseCase`
  - `UpdateImportFiltersUseCase`
  - `ExportBackupUseCase`

Repository:

- Owns access to one aggregate or data family.
- Exposes `Flow` for observed state.
- Exposes `suspend` functions for writes.
- Owns transactions.

### 3.6 UI Data Flow

Every screen uses unidirectional data flow:

```text
User action
  -> ViewModel.onAction(...)
    -> use case / repository action
      -> repository updates source of truth
        -> repository Flow emits new data
          -> ViewModel maps to UiState
            -> Compose recomposes
```

Avoid:

- Mutating UI widgets imperatively.
- Storing "current screen" state in Activity fields.
- Calling `renderStudy()`-style imperative functions.
- Global mutable singleton state.
- ViewModel one-shot events that are the only source of important state.

One-shot events are only for:

- Navigation.
- Opening Android settings.
- Starting APK installer intent.
- Permission request launch.
- Snackbar/toast-like transient messages.
- File picker launch.

Important state must be represented in the durable `UiState` or source of truth.

## 4. Target Tech Stack

### 4.1 Language

Use Kotlin for all rewritten app, domain, data, and UI code.

Keep Java only where:

- It is temporary during migration.
- Existing FSRS Java code is intentionally retained as a reference.
- Generated code or Android tooling requires it.

Preferred:

- Kotlin data classes for immutable domain models.
- Kotlin value classes for IDs where useful.
- Sealed interfaces/classes for UI states, result types, and domain events.
- Coroutines and Flow for async work.
- KSP for Room/Hilt/code generation.

Do not use:

- Kotlin for a huge single-file `App.kt` rewrite.
- Overly clever DSLs for domain rules.
- Reflection-heavy frameworks in the core/domain path.

### 4.2 UI

Use:

- Jetpack Compose.
- Material 3 primitives where useful.
- A custom Kani design system above Material.
- Compose Navigation or Navigation 3 once stable enough for the repo's toolchain.
- Compose UI tests for screen behavior.

The app should feel like Kani, not generic Material:

- Keep the pink/cutesy visual language.
- Use compact, work-focused layouts where the app is operational.
- Use clear study controls.
- Use strong visual hierarchy for the active study prompt.
- Use icons for tool actions where appropriate.
- Keep buttons readable and non-overlapping on mobile.
- Keep study action controls above the fold.
- Keep handwriting pad dimensions stable.
- Keep accessibility labels/content descriptions for icons and non-text controls.

Design system module should provide:

- `KaniTheme`
- color tokens
- typography tokens
- spacing tokens
- elevations/shapes
- reusable buttons
- cards/panels
- top bars
- status pills
- setting rows
- segmented controls
- sliders/steppers
- empty states
- progress/status surfaces
- study-specific controls

Compose screens should not hardcode raw colors and spacing everywhere. Screen-specific exceptions are allowed only when the design system does not yet provide the needed primitive; add the primitive if it recurs.

### 4.3 Persistence

Use Room for structured app data.

Room should own:

- source notes
- source cards
- suspended archive
- suspended imports
- suspended sources
- sync runs
- import audit rows
- dashboard rows
- kanji examples
- kanji inventory
- similar-kanji pairs
- similar-choice state
- similar-writing repair state
- study items
- task memories
- learning repeats
- review logs
- study task logs
- timeline events
- historical sync snapshots
- historical kanji aggregates
- local kanji suspensions
- stats evidence
- app update/install history if persisted

Use DataStore for:

- small user settings where relational queries are not needed
- theme/settings toggles
- last-opened route if needed
- UI-only preferences

Use Room, not DataStore, for:

- study settings that need transactional coupling with study data
- import settings that participate in sync transactions
- historical evidence
- anything relational
- anything queried by joins/counts
- anything with partial updates or referential integrity

The current `settings` key/value table can be migrated either to:

- Room settings table with typed columns where settings are part of sync/study behavior, or
- Proto DataStore only for non-relational app preferences.

Do not put all settings in one giant Proto if many settings are relationally tied to sync/study transactions.

### 4.4 Database Migrations

Use Room migrations or an explicit destructive reset. The user has approved
breaking existing local Kani databases when that makes the rewrite cleaner.

Rules:

- Prefer a fresh, well-normalized Room/DataStore schema over compatibility
  with legacy `SQLiteOpenHelper` storage.
- Destructive replacement is acceptable for Kani-owned local data.
- Do not build compatibility scaffolding solely for old DB versions.
- If a migration is kept, it must be explicit, tested, and simpler than
  a reset-and-resync path.
- Old install behavior that affects product semantics, such as suspended-only
  import defaults, must be represented by current domain defaults or an
  intentional settings reset, not by preserving legacy storage.
- Migration must be idempotent where re-entry can happen.
- Destructive reset must have clear preflight detection and a single ownership
  point; avoid scattered table drops.
- A backup is optional for developer/debug builds and future UX polish, not a
  blocker for the rewrite architecture.

The legacy SQLite DB is `kanji_anki_simple.db`, current schema version observed
as `20`. The rewrite Room DB is `kanji_anki_room.db`; this intentionally keeps
Room from opening the legacy `SQLiteOpenHelper` file as if it were the new
schema. Room database creation must go through a single factory/reset policy
that uses destructive migration for Kani-owned local data.

### 4.5 Dependency Injection

Use Hilt.

Hilt graph rules:

- `@HiltAndroidApp` application.
- `@AndroidEntryPoint` activity.
- `@HiltViewModel` screen ViewModels.
- Singleton bindings for database, DAOs, repositories, settings stores, app clock, dispatchers, asset providers, FSRS engine, provider gateway factories.
- Qualifiers for dispatchers:
  - `@IoDispatcher`
  - `@DefaultDispatcher`
  - `@MainDispatcher`
- Fakes/test modules for provider, clock, ML Kit recognizer, GitHub release client, and file systems.
- Avoid field injection except where Android framework construction requires it.
- Prefer constructor injection.

Do not use service locator singletons.

### 4.6 Async And Concurrency

Use:

- Kotlin coroutines.
- Flow.
- Structured concurrency.
- WorkManager for durable background work.
- Repository transactions for multi-table writes.

Rules:

- No UI-thread disk IO.
- No UI-thread provider reads.
- No UI-thread network.
- No manually managed unbounded executors in feature code.
- No global atomic RUNNING flags where WorkManager or a repository-level mutex/transaction is a better fit.
- All long-running operations must be cancellable where possible.
- Sync progress should be observable as persistent/flowing state, not only callbacks.
- Race-prone async UI work must use route/viewmodel lifecycle and source-of-truth state rather than Activity run tokens.

### 4.7 Background Work

Use WorkManager for:

- daily auto sync
- periodic update checks
- database backup
- deferred model/download work if appropriate
- retryable provider sync after transient failures if product allows

Rules:

- Manual sync and background sync share the same domain use case.
- Background sync must not use a different planner path or ignore adaptive max-item settings.
- Work state must be observable from Home/Settings/Sync UI.
- Workers should be thin wrappers around use cases.
- Workers should return clear success/retry/failure based on domain results.
- Workers should write sync run rows.
- Workers should not own business logic.

### 4.8 Networking

Keep networking minimal:

- GitHub Releases API for updates.
- Optional no other network by default.

Use:

- Ktor client or OkHttp/Retrofit.
- Explicit timeout policy.
- JSON parsing in update module only.
- SHA-256 checksum verification before install.

Do not introduce:

- analytics
- telemetry
- remote config
- backend sync
- account auth

### 4.9 ML Kit And Writing

Use ML Kit Digital Ink Recognition through an Android-edge module.

Split:

- `writing-core`: pure geometry, stroke order, hint policy, rating mapping, captured stroke models, tutor diagnosis models.
- `writing-android`: ML Kit recognizer, model availability/download, Compose drawing pad or AndroidView wrapper, asset loading.

The UI should depend on abstract writing use cases and UI states, not ML Kit classes directly.

### 4.10 FSRS

Target:

- Module name should be `:fsrs` or keep `:fsrs-java` only if renaming causes needless churn.
- Prefer Kotlin for the rewrite if it improves readability and testing.
- Keep a pure JVM module.
- No Android dependency.
- No JNI.
- No Python runtime.
- No JSON dependency in core engine unless isolated to test/tools.
- Deterministic by default.
- Optional fuzzing only if seedable and disabled by default.

Public API should expose:

- parameters
- rating enum
- memory state
- review input
- review output
- initial state
- retrievability
- next difficulty
- next stability
- next interval

It should not expose:

- Kani study item
- Kani rung
- Kani queue
- Kani settings object
- Kani learning repeat state
- AnkiDroid card state

## 5. Source-Of-Truth Model

### 5.1 Room As Source Of Truth

Room is the source of truth for Kani app state.

Flow of data:

```text
AnkiDroid provider snapshot
  -> import/sync use case
    -> Room transaction updates source mirror, imports, dashboard, inventory, study items, logs
      -> repository Flows emit
        -> ViewModels map to UiState
          -> Compose renders
```

No screen should reconstruct product truth by directly querying AnkiDroid or re-running import analysis during rendering.

Runtime promotion from the legacy store to Room must stay behind an explicit
ownership gate. Room Study reads and Room review writes are allowed only after
the app has either completed the existing-install reset/migration path or is
double-writing legacy and Room state. This prevents Room sync from replacing
study items based only on Room-local seed state while production UI is still
reading or writing legacy `LocalStore` data.

Room study runtime reads and writes share one process-level mutation gate and
the ownership policy above. Sync queue seeding reads the current queue and
builds replacement rows inside the same gated Room transaction that records the
source snapshot, so a foreground review cannot interleave between seed
calculation and `study_items` replacement. Session token claims, direct queue
updates, and review persistence must enter the same gate before their Room
transaction.

### 5.2 Domain Model Families

Replace broad `Records*` namespaces with focused domain packages:

```text
domain/model/source/
  SourceNote
  SourceCard
  CollectionSnapshot
  NoteTypeMapping
  CardTemplate

domain/model/importing/
  ImportSource
  ImportSettings
  SuspendedImport
  SuspendedSource
  ImportAuditEntry

domain/model/kanji/
  KanjiId
  KanjiInventoryItem
  KanjiExample
  KanjiDashboardRow
  KanjiTimelineEvent
  KanjiWeaknessReason

domain/model/study/
  StudyItem
  StudyRung
  StudyPhase
  StudySession
  StudyReviewRequest
  StudyReviewResult
  TaskMemory
  LearningRepeat
  ReviewLogEntry
  StudyTaskLogEntry

domain/model/ladder/
  StudyLadderSettings
  LadderMovementPolicy
  LadderMovementResult

domain/model/similar/
  SimilarKanjiPair
  SimilarChoiceTask
  SimilarWritingRepair

domain/model/writing/
  CapturedWriting
  CapturedStroke
  InkPoint
  StrokeGuide
  HintLevel
  HintState
  WritingAnalysis
  StrokeDiagnosis

domain/model/stats/
  StudyStatsSummary
  OutcomeMetric
  AnsweredTimeMetric
  KaniImpactReport

domain/model/update/
  ReleaseInfo
  UpdateCheckResult
  PackageInstallStatus
```

Domain models should:

- be immutable
- have explicit field names
- avoid `Object... rest`
- avoid compatibility constructors in new code
- have strict validation where invalid state would corrupt scheduling
- represent nullable data deliberately
- encode current wire names for persisted enum/string compatibility

### 5.3 Entity/Domain Separation

Room entities are not domain models.

Use explicit mappers:

```text
StudyItemEntity <-> StudyItem
SourceCardEntity <-> SourceCard
ImportSettingsEntity <-> ImportSettings
```

Reasons:

- Room annotations should not leak into pure domain.
- DB defaults and migrations are not the same as domain invariants.
- Domain tests should not need Room.
- Schema changes should not force UI model changes.

Mappers must be:

- small
- tested for important encoded fields
- located near the repository/data package
- not embedded inside ViewModels

### 5.4 IDs And Keys

Use explicit IDs/value classes where useful:

```kotlin
@JvmInline value class KanjiText(val value: String)
@JvmInline value class CardId(val value: Long)
@JvmInline value class NoteId(val value: Long)
@JvmInline value class SyncRunId(val value: Long)
@JvmInline value class ReviewToken(val value: String)
```

Do not overdo value classes for every string; use them where they prevent common mistakes:

- note id vs card id
- kanji vs expression
- sync id
- review token
- answer signature

### 5.5 Time

Use an injected clock:

```kotlin
interface AppClock {
    fun nowMillis(): Long
}
```

Rules:

- Domain logic accepts explicit timestamps where deterministic tests matter.
- Repositories/workers use injected clock.
- UI does not call `System.currentTimeMillis()` directly.
- Date formatting belongs in UI/domain formatter utilities, not database entities.

## 6. Data Schema Plan

The Room schema should be designed from product aggregates and current behavior,
not copied mechanically from legacy table shape. This section defines required
aggregate groups and key tables. The implementing engineer should translate
each into Room entities, DAOs, relations, indexes, and either clean migrations
or an explicit destructive reset path.

### 6.1 Settings

Split settings by ownership:

```text
StudySettings
  targetRetention
  learningSteps
  relearningSteps
  studyAheadMinutes
  ladderPromotionIntervalDays
  ladderDemotionFailStreak
  ladderOrder
  enabledRungs
  adaptiveLoadMode
  adaptiveLoadWorkPercent
  adaptiveLoadMaxItems
  newCardSortMode
  frequencyRetentionEnabled
  frequencyRetentionRanges

SyncSettings
  noteType
  templateName
  fieldMappings
  matureDays
  matureSupportThreshold
  importActiveCards
  importSuspendedCards
  importTaggedCards
  importTags
  importWeakCards
  importWeakFsrsDifficultyThreshold
  importWeakLapsesThreshold
  importMinMatchingCardsPerKanji
  importBrowserQueryCards
  importBrowserQuery
  suspendedRankMin
  suspendedRankMax

ReminderSettings
  enabled
  hour
  minute

UpdateSettings
  autoCheckEnabled
  lastCheckAt
  ignoredVersion if current behavior has/needs it

UiPreferences
  lightweight app preferences only
```

Implementation choice:

- Persist study/sync settings in Room if they are read inside sync/study transactions.
- Persist UI-only preferences in DataStore.
- Preserve current keys or write migrations from current key/value rows.

### 6.2 Source Mirror

Room entities:

```text
SourceNoteEntity
  noteId primary key
  modelName
  expression
  reading
  meaning
  sentence
  fieldsJson
  tags
  lastSeenSyncId

SourceCardEntity
  cardId primary key
  noteId foreign key
  deckName
  ord
  queue
  type
  due
  intervalDays
  reps
  lapses
  fsrsStability nullable
  fsrsDifficulty nullable
  fsrsRetrievability nullable
  lastSeenSyncId
  matchedImportSource flags/source fields if needed
```

Indexes:

- `SourceCard.noteId`
- `SourceCard.queue`
- `SourceCard.lastSeenSyncId`
- `SourceNote.modelName`
- `SourceNote.lastSeenSyncId`

### 6.3 Sync Runs

Room entities:

```text
SyncRunEntity
  id primary key
  startedAt
  finishedAt nullable
  status
  activeNotesCount
  activeCardsCount
  suspendedCardsArchivedCount
  suspendedKanjiImportedCount
  deletedNotesCount
  deletedCardsCount
  errorCode nullable
  errorMessage nullable
  removalMessage nullable
  planStatus

SyncProgressEntity or in-memory progress state
  runId nullable
  stage
  message
  updatedAt
```

Progress can be in memory for foreground sync, but durable summary belongs in sync runs.

### 6.4 Suspended Archive And Imports

Entities:

```text
SuspendedArchiveEntity
  cardId primary key
  noteId
  deckName
  modelName
  expression
  reading
  meaning
  sentence
  fieldsJson
  archivedAt
  archivedSyncId
  restoredAt nullable

SuspendedImportEntity
  kanji primary key
  jitenRank nullable
  rankKnown
  cutoffUsed
  firstImportedAt
  lastSeenSyncId

SuspendedSourceEntity
  kanji
  cardId
  noteId
  expression
  reading
  meaning
  sentence
  syncId
  primary key (kanji, cardId)
```

Preserve current archive/import semantics exactly.

### 6.5 Dashboard, Inventory, Examples

Entities:

```text
DashboardRowEntity
  kanji primary key
  jitenRank nullable
  primaryMeaning
  reading
  browserSearch
  weaknessScore
  reasonCode
  reasonText
  activeExampleCount
  suspendedExampleCount
  matureSupportCount
  rebuiltAt

KanjiExampleEntity
  id primary key
  kanji
  sourceType
  cardId
  noteId
  expression
  reading
  meaning
  sentence
  mature
  lapses
  intervalDays
  reps
  fsrsStability nullable
  fsrsDifficulty nullable
  fsrsRetrievability nullable

KanjiInventoryEntity
  kanji primary key
  primaryMeaning
  readingsDisplay
  readingsSearch
  browserSearch
  source flags/counts
  rank fields
  suspended flag
  updatedAt
```

Preserve:

- visible readings capped as current behavior dictates
- hidden readings searchable
- browser search text generation
- source counts
- mature support counts

### 6.6 Similar Kanji

Entities:

```text
SimilarKanjiPairEntity
  kanjiA
  kanjiB
  source
  firstSeenAt
  lastSeenAt
  primary key (kanjiA, kanjiB, source)

SimilarChoiceTaskEntity
  id or composite key
  targetKanji
  choiceSignature
  choices
  status
  dueAt
  createdAt
  updatedAt

SimilarWritingRepairEntity
  id or composite key
  targetKanji
  choiceSignature
  repairKanji
  status
  dueAt
  createdAt
  updatedAt
```

Preserve due-count and study-progress truth helpers.

### 6.7 Study Items

Entities should make current encoded task memory explicit enough for safety.

Possible design:

```text
StudyItemEntity
  id primary key
  kanji
  answerSignature
  prompt data fields
  currentRung
  phase
  state
  dueAt
  createdAt
  updatedAt
  totalReviews
  consecutiveFailStreak
  hasSimilarKanji
  source fields needed by prompts
  current/legacy compatibility fields

TaskMemoryEntity
  studyItemId
  taskType
  stability
  difficulty
  dueAt
  scheduledIntervalMillis
  reviewCount
  lapseCount
  lastRating nullable
  lastReviewedAt nullable
  phase-specific fields if needed
  primary key (studyItemId, taskType)
```

Alternative:

- Keep task memories embedded in `StudyItemEntity` columns for performance and migration simplicity.

Decision guidance:

- Use separate `TaskMemoryEntity` if adding future rungs and querying per-task memory will be common.
- Use embedded columns if parity risk is lower and the number of rungs remains small.
- In either case, the domain object must expose typed task memory access rather than raw encoded strings.

Preserve current task memories:

- typing/meaning memory
- kanji/meaning memory
- font/meaning memory
- word/reading memory
- writing memory
- meaning/kanji if currently persisted
- any active rung memory handoff behavior

### 6.8 Learning Repeats

Entity:

```text
LearningRepeatEntity
  kanji
  answerSignature
  taskType
  repeatType new/review
  stepIndex
  dueAt
  token
  createdAt
  updatedAt
```

Preserve:

- learning/relearning repeat semantics
- token behavior
- due handling
- practice-only non-promotion behavior

### 6.9 Review Logs And Study Task Logs

Entities:

```text
ReviewLogEntity
  id
  kanji
  answerSignature
  taskType
  rating
  occurredAt
  phase
  rungBefore
  rungAfter
  stabilityBefore
  difficultyBefore
  stabilityAfter
  difficultyAfter
  intervalBefore
  intervalAfter
  elapsedDays
  learningStep data
  writingClean
  source token

StudyTaskLogEntity
  id
  kanji
  taskType
  startedAt
  answeredAt
  durationMillis
  outcome
  phase
  rung
  prompt metadata
```

These logs are crucial for Stats, debugging, and parity validation. Do not simplify them to "pass/fail count" only.

### 6.10 Timeline And History

Entities:

```text
KanjiTimelineEventEntity
  id
  kanji
  occurredAt
  eventType
  title
  detail
  sourceExpression
  sourceReading
  rating
  writingRequired
  writingPassed
  manualOverride
  weaknessScore nullable
  matureSupportCount nullable
  syncId nullable
  dedupeKey unique

HistoricalSyncSnapshotEntity
  current fields from HistoricalSyncStore

HistoricalKanjiAggregateEntity
  current aggregate fields
```

Preserve dedupe keys exactly enough that migration does not duplicate history.

### 6.11 Dictionary Data

Keep dictionary asset handling separate from user DB.

Options:

1. Keep bundled `kanji_dictionary.db` as an asset and open it read-only.
2. Convert to Room prepackaged database if it improves DAO safety.

Requirements:

- Dictionary asset remains offline.
- SHA-256 verification/metadata remains.
- Attribution remains.
- Jiten ranks stay accessible to import selector.
- Dictionary lookup remains fast enough for sync/import and UI detail pages.

## 7. Domain Layer Plan

### 7.1 Package Layout

```text
domain/
  model/
  repository/
  usecase/
  scheduler/
  importing/
  sync/
  stats/
  writing/
  dictionary/
  update/
  common/
```

### 7.2 Scheduler Domain

Break current scheduler behavior into focused types:

```text
SchedulerFacade
StudyQueueSeeder
StudySessionSelector
ReviewTransitionEngine
LearningStepEngine
LadderMovementEngine
SiblingSuppressionPolicy
StudyProgressCalculator
ReviewTokenGuard
TaskMemoryHandoffPolicy
StudyAheadPolicy
```

Rules:

- No class should exceed 400 lines without explicit justification.
- Most scheduler classes should be under 250 lines.
- Each class should have one reason to change.
- Preserve current public behavior with characterization tests before internals are replaced.

Core scheduler methods:

```kotlin
interface StudyScheduler {
    fun seedQueue(input: SeedQueueInput): SeedQueueResult
    fun seedExtraNewCards(input: SeedExtraNewCardsInput): SeedExtraNewCardsResult
    fun nextSession(input: NextSessionInput): StudySession?
    fun applyReview(input: ApplyReviewInput): ReviewResult
    fun dueCount(input: DueCountInput): Int
    fun progressSnapshot(input: ProgressSnapshotInput): StudyProgressSnapshot
}
```

The facade may exist for compatibility, but internal work must be delegated to focused collaborators.

### 7.3 Learning Step Engine

Responsibilities:

- Parse configured learning/relearning steps.
- Apply Anki-exact learning/relearning transitions.
- Decide repeat step index and due delay.
- Decide graduation.
- Ensure repeats are practice-only.

It must not:

- Decide ladder movement.
- Query database.
- Render UI copy.

### 7.4 Ladder Movement Engine

Responsibilities:

- Determine whether a due review counts as real due.
- Apply promotion/demotion threshold rules.
- Use FSRS interval-days result for promotion threshold.
- Track consecutive fail streak.
- Select nearest valid rung when current rung is disabled/unavailable.
- Skip conditional similar rung when not available.
- Preserve current edge cases at floor/ceiling.

Inputs:

- current study item
- current rung
- rating
- phase
- due/review timing
- FSRS result interval days
- ladder settings
- hasSimilarKanji
- settings thresholds

Output:

- next rung
- movement type
- fail streak
- active task memory handoff command

### 7.5 Study Session Selector

Responsibilities:

- Select next due study item/session.
- Respect adaptive focus set.
- Respect study-ahead window.
- Respect retired/suppressed states.
- Route similar-choice and writing-repair work the same way current behavior does.
- Return prompt data needed by UI without requiring UI to inspect database rows.

It must be deterministic in tests.

### 7.6 Adaptive Pareto Planner

Preserve current behavior:

- `AUTO` and `MANUAL` modes.
- Default workload mode is auto.
- Default max items is current default.
- Cap behavior remains.
- Due recovery can raise the effective target.
- Priority can use:
  - weakness score
  - active/suspended evidence
  - Kani review stats
  - optional FSRS difficulty/stability/retrievability source data
- FSRS is ranking input only, not a replacement scheduler.

Rewrite as:

```text
AdaptiveLoadPlanner
  PlanRequest
  CandidateScorer
  WorkloadPolicy
  FocusSetLimiter
  PlanStatusFormatter
```

Avoid a single 650-line planner in final state.

### 7.7 Import Domain

Focused services:

```text
KanjiImportSelector
SuspendedKanjiImporter
WeakCardImporter
TaggedCardImporter
BrowserQueryImporter
KanjiAnalyzer
KanjiInventoryBuilder
SimilarKanjiPairBuilder
ImportAuditBuilder
```

Preserve:

- Kiku defaults.
- source-specific import flags.
- suspended default.
- rank filters.
- weak-card thresholds.
- browser query fallback behavior.
- active/suspended examples.
- reason codes/text.
- mature support counts.
- local suspension filtering.

### 7.8 Sync Domain

Use case:

```kotlin
class RunSyncUseCase(
    private val collectionGateway: CollectionGateway,
    private val syncRepository: SyncRepository,
    private val importService: ImportService,
    private val studyQueueService: StudyQueueService,
    private val similarKanjiService: SimilarKanjiService,
    private val clock: AppClock,
)
```

Responsibilities:

- Guard concurrent syncs through repository/WorkManager/mutex.
- Read collection from gateway.
- Build import analysis.
- Write source mirror and derived rows in transaction.
- Remove/archive suspended cards through gateway after local state is safe.
- Seed/update study queue using current adaptive plan and ladder settings.
- Write sync run success/failure.
- Emit progress.

Manual and background sync must call this same use case.

### 7.9 Writing Domain

Pure domain/core:

```text
WritingAnalysisEngine
WritingRatingMapper
StrokeOrderEvaluator
StrokeGuideGuard
HintPolicy
HintProgression
StrokeDiagnosisFormatter
CapturedWritingMapper
```

Android edge:

```text
MlKitWritingRecognizer
MlKitModelRepository
StrokeGuideAssetRepository
WritingPadRenderer / ComposeWritingPad
```

Preserve:

- guide levels
- stroke fading
- undo
- off-guide blocking
- same-pad replay
- tutor-only diagnosis
- writing rating cap behavior

### 7.10 Stats Domain

Focused use cases:

```text
GetStatsOverviewUseCase
CalculateKaniImpactUseCase
CalculateAnsweredTimeUseCase
CalculateOutcomeTrendUseCase
GetLadderHealthUseCase
GetStudyHistoryUseCase
```

Stats should consume logs/evidence, not UI state.

### 7.11 Update Domain

Split:

```text
ReleaseParser
VersionComparator
ChecksumVerifier
UpdatePolicy
UpdateRepository
InstallPackageUseCase
```

Android edge handles:

- HTTP
- file storage
- FileProvider
- installer intents
- install status receiver
- notification

Preserve current update UI behavior and navigation cancellation safety by making status durable/viewmodel-owned rather than Activity callback-owned.

## 8. UI Plan

### 8.1 Navigation

Target routes:

```text
Home
Study
Stats
Browse
KanjiDetail(kanji)
Games
Settings
SettingsImportFilters
SettingsAnkiSource
SettingsStudyBehavior
SettingsLadder
SettingsAutomation
SettingsReferenceData
DataSources
Updater
SyncDetails
```

Top-level nav should remain uncluttered:

- Home
- Study
- Stats
- Browse or Practice/Games only if current product requires it
- Settings

Secondary destinations should live under Home or Settings, not cramped bottom nav surfaces.

### 8.2 Home

Responsibilities:

- Show Kani status and current problem-kanji focus.
- Show `Study now` CTA.
- Show sync/provider readiness.
- Show current adaptive focus summary.
- Show recent sync status.
- Show update status only as needed.
- Link to Settings for configuration.
- Link to Browse/Stats where appropriate.

Home ViewModel:

- observes home summary repository flow
- observes provider status
- observes sync status
- observes update status
- exposes `HomeUiState`
- handles Study Now, Sync, Settings, Browse, Stats, Update actions

Home UI should not:

- query database directly
- call AnkiDroid
- compute adaptive plan itself

### 8.3 Study

Study should be the most carefully protected feature.

Routes:

- `StudyRoute` collects UI state and events.
- `StudyScreen` renders current state.

Study states:

```text
Loading
NoData / NeedsSync
NoProblemKanji
SessionReady
AnswerRevealed
WritingChecking
WritingFeedback
SimilarChoiceFeedback
FocusDone
AllDone
ErrorRecoverable
```

Prompt composables:

- `RecognitionPrompt`
- `FontMeaningPrompt`
- `WordReadingPrompt`
- `TypingMeaningPrompt`
- `MeaningKanjiPrompt`
- `SimilarKanjiPrompt`
- `WritingPrompt`

Shared components:

- `StudyTopBar`
- `StudyProgressPill`
- `StudyActionBar`
- `RevealButton`
- `PassFailButtons`
- `HardGoodEasyButtons` if needed outside write rung
- `WritingPad`
- `HintControls`
- `UndoStrokeButton`
- `ReplayButton`
- `DownloadModelButton`

Study invariants:

- Primary action controls stay visible above fold.
- Writing pad has stable aspect ratio and no layout jumps.
- Revealed answer can scroll if content is long, without pushing grading controls below fold.
- Gesture area does not include bottom action/nav controls.
- Swipe grading only after reveal.
- Typing answer input keeps focus and keyboard behavior sane.
- Similar-choice buttons have stable dimensions.
- Pass/fail destructive/confirm buttons have visible spacing.
- Current top count is full workload truth, not only visible focus card count.

Study ViewModel should own:

- current session
- revealed state
- writing check in progress
- current writing capture metadata
- active hint state
- active ML Kit model state
- current feedback
- current progress snapshot
- one-shot navigation/events

Study ViewModel should not own:

- Android `View`
- raw `MotionEvent`
- ML Kit recognizer directly
- database cursor
- content resolver

Writing pad state:

- If using Compose Canvas, keep rendering and pointer handling in a reusable state holder.
- If wrapping a custom View, use `AndroidView` behind a stable `WritingPad` API.
- The rest of Study should not care whether the pad is Canvas or View.

### 8.4 Settings

Settings should be grouped, expandable, and consistent with current pink/cutesy visual language.

Sections:

- Anki source
- Import filters
- Study behavior
- Ladder
- Workload/Pareto
- Learning/relearning
- Automation
- Reference data
- Updates
- Data/backup if exposed

Settings ViewModels:

- Prefer one `SettingsViewModel` for the main screen and small focused ViewModels only if a subsection has complex interactions.
- Do not create one 2,000-line Settings ViewModel.

Settings actions should write through use cases/repositories:

- Save note type.
- Choose note type from AnkiDroid.
- Save field mappings.
- Save import filters.
- Save rank range.
- Save new-card sort.
- Save workload.
- Save learning steps.
- Save study-ahead window.
- Save ladder order/enabled rungs.
- Save ladder thresholds.
- Save target retention.
- Save reminder.
- Toggle daily sync.
- Open data licenses.
- Open updater.

Every setting that changes sync/study behavior must have tests that prove old persisted values migrate and new values take effect in both foreground and background paths.

### 8.5 Stats

Stats should be concise and learner-facing.

Potential UI sections:

- Kani impact summary.
- Answered study time.
- Outcome trend.
- Weakness burn-down.
- Ladder health.
- "Too few data points" state.
- "Kani is not helping" state where evidence supports it.

Stats must not display technical table dumps by default.

### 8.6 Browse And Detail

Browse:

- searchable kanji inventory
- capped visible readings
- hidden readings searchable
- filters for active/suspended/import source if current behavior supports

Detail:

- kanji overview
- meanings/readings
- examples
- support count
- source cards
- timeline
- current study state
- local suspension state
- similar kanji if available
- data source attribution where relevant

### 8.7 Sync UI

Expose:

- provider installed/missing
- permission granted/missing
- configured note type/template readiness
- manual sync button
- current sync progress stages
- last sync result
- removal/archive summary
- retryable/permanent error copy
- daily auto-sync status

Provider permission must remain clear and actionable.

### 8.8 Update UI

Expose:

- current version
- latest release status
- download/checksum/install status
- release notes if current behavior has them
- install confirmation action
- errors

Do not continue update action after navigation away unless durable status says it is still appropriate.

## 9. FSRS Rewrite Plan

The user owns `~/Documents/src/github/fsrs_java`, and Kani already vendors/contains an in-repo `:fsrs-java` module. The rewrite can choose to keep Java, port it to Kotlin, or maintain a standalone FSRS repo and vendor/sync it into Kani. The ideal architecture is to own the engine as a small pure JVM module with conformance tests, regardless of implementation language.

### 9.1 FSRS Goals

FSRS module must:

- implement only FSRS memory maths
- be deterministic
- be pure JVM
- have no Android dependency
- have no JNI/native dependency
- have no Python runtime dependency
- be usable from Android and JVM tests
- have explicit upstream provenance
- reject invalid inputs
- match upstream fixtures
- expose a stable minimal API

FSRS module must not:

- decide Kani review selection
- know about Kani ladder rungs
- know about Kani import settings
- own learning/relearning policy
- own sibling suppression
- own repair queues
- store database state
- include UI wording

### 9.2 Java vs Kotlin Decision

Kotlin is recommended for the rewrite if:

- generated bytecode remains Android-compatible
- public API remains stable and simple
- conformance tests pass exactly
- performance is acceptable
- no Kotlin language feature makes Java interop or Android desugaring risky

Keeping Java is acceptable if:

- it reduces algorithm risk
- a later Kotlin port would distract from app architecture
- current Java is already clear enough

Preferred compromise:

1. Keep current Java engine as reference.
2. Add Kotlin engine implementation side-by-side in tests.
3. Run Java vs Kotlin output comparison over all fixtures.
4. Switch app adapter to Kotlin engine only after parity.
5. Remove Java engine when no longer needed, or keep it as a test fixture only if helpful.

### 9.3 FSRS API Shape

Kotlin API:

```kotlin
enum class FsrsRating(val value: Int) {
    AGAIN(1), HARD(2), GOOD(3), EASY(4)
}

data class FsrsParameters(
    val values: DoubleArray
) {
    companion object {
        const val PARAMETER_COUNT = 21
        fun latestDefault(): FsrsParameters
    }
}

data class FsrsMemoryState(
    val stability: Double,
    val difficulty: Double,
)

data class FsrsReviewInput(
    val previousState: FsrsMemoryState,
    val rating: FsrsRating,
    val elapsedDays: Int,
    val desiredRetention: Double,
    val maximumInterval: Int = 36_500,
)

data class FsrsReviewOutput(
    val nextState: FsrsMemoryState,
    val retrievability: Double,
    val nextIntervalDays: Int,
)

interface FsrsEngine {
    fun initialState(firstRating: FsrsRating): FsrsMemoryState
    fun retrievability(state: FsrsMemoryState, elapsedDays: Int): Double
    fun nextDifficulty(currentDifficulty: Double, rating: FsrsRating): Double
    fun shortTermStability(stability: Double, rating: FsrsRating): Double
    fun nextState(previousState: FsrsMemoryState, rating: FsrsRating, elapsedDays: Int): FsrsMemoryState
    fun nextIntervalDays(stability: Double, desiredRetention: Double, maximumInterval: Int): Int
    fun review(input: FsrsReviewInput): FsrsReviewOutput
}
```

Do not expose mutable parameter arrays. If using `DoubleArray`, defensive-copy at boundaries.

### 9.4 FSRS Validation

Validate:

- parameter count exactly 21
- all parameters finite
- decay magnitude positive
- stability finite and positive
- difficulty finite and within `[1, 10]`
- elapsed days non-negative
- desired retention in `(0, 1)`
- maximum interval at least `1`
- rating non-null

Kani adapter may clamp retention/difficulty where current behavior clamps. The engine itself should reject invalid core inputs.

### 9.5 FSRS Tests

Required tests:

- pinned upstream fixture conformance
- current Java vs Kotlin parity if porting
- all four first-review ratings
- all four review ratings
- elapsed days: `0`, `1`, `2`, `7`, `30`, `365`
- low/medium/high stability
- low/medium/high difficulty
- desired retention: `0.8`, `0.9`, `0.95`
- maximum interval clamping
- stability minimum clamping
- short-term stability for same-day reviews
- forget-stability short-term cap
- invalid parameter count
- invalid retention
- invalid interval
- invalid stability
- invalid difficulty
- no NaN outputs

Kani integration tests:

- first review starts from initial state
- learning graduation uses initial path
- relearning graduation preserves post-lapse stability/difficulty
- on-time review elapsed days is full scheduled interval
- same-day review uses short-term path
- ladder promotion threshold uses FSRS output interval days
- active-rung memory handoff after promotion/demotion

### 9.6 FSRS Upstream Update Process

Keep update workflow from `fsrs_java`:

1. Check latest upstream release.
2. Pin exact upstream tag and source blob.
3. Update algorithm docs and metadata.
4. Regenerate fixtures.
5. Compare against pinned `py-fsrs`.
6. Run FSRS module tests.
7. Run Kani domain/core tests.
8. Generate old-vs-new interval impact report.
9. Review impact before enabling new behavior.

Do not silently update FSRS defaults as part of unrelated architecture work.

## 10. Migration And Rewrite Strategy

This section is an implementation order, not a timeline.

### 10.1 Rewrite Principle

The rewrite must be behavior-preserving.

Use characterization tests to pin behavior before replacing implementation. For every major subsystem:

1. Capture current behavior with tests.
2. Build new architecture behind the same contract.
3. Run old and new in comparison where possible.
4. Switch callers only after parity.
5. Delete old code only when the replacement is covered and shipped internally.

### 10.2 Worktree And Branching

For substantial rewrite work in this repo:

- Use a dedicated writable worktree.
- Keep `main` releasable.
- Make focused commits.
- Avoid sweeping unrelated user changes.
- Prefer small branch slices that can be reviewed.
- Keep CI green for each meaningful merge point.

Suggested branch naming:

```text
rewrite/kotlin-foundation
rewrite/domain-models
rewrite/room-migration
rewrite/fsrs-kotlin
rewrite/compose-shell
rewrite/study-screen
rewrite/sync-provider
```

### 10.3 Start With Characterization

Before replacing code, add or strengthen tests around current behavior:

Domain/scheduler:

- ladder defaults
- enabled/disabled rung behavior
- starting rung selection
- conditional similar rung
- learning/relearning Anki-exact transitions
- ladder promotion by FSRS interval > 21 days
- ladder demotion by 3 consecutive real due fails
- fail streak reset
- floor/ceiling behavior
- task memory handoff
- due counts
- study progress snapshot
- similar-choice and repair due counts
- adaptive planner cap and auto/manual modes
- new card sort modes
- FSRS adapter edge cases

Import/sync:

- Kiku default settings
- field mapping
- suspended-only default
- active import opt-in
- tagged import opt-in
- weak-card import opt-in
- browser query opt-in
- missing FSRS source fields are tolerated
- provider failure classification
- sync success writes all expected tables
- failed sync writes failed run
- background sync uses same planner settings

Data:

- clean Room/DataStore schema initializes from empty state
- destructive reset path is explicit when legacy DB state is incompatible
- no duplicate timeline events after fresh sync/rebuild
- review logs and task memories are preserved only when using a deliberate
  non-destructive migration path
- source mirror, suspended archive, and study items rebuild correctly from sync
- import defaults after reset match current product defaults

UI/instrumented:

- Study reveal/answer/pass/fail above fold
- Study `N / N` done truth
- writing pad undo
- off-guide block
- similar-choice flow
- settings import filters visible
- manual sync button path
- provider permission missing path
- updater navigation cancellation behavior

### 10.4 Build Foundation

Create module and Gradle foundation:

- Kotlin Android plugin.
- Kotlin JVM plugin where needed.
- Compose compiler/BOM.
- Hilt plugin.
- KSP plugin.
- Room dependencies.
- DataStore dependencies.
- WorkManager KTX.
- Lifecycle ViewModel Compose.
- Navigation Compose.
- Coroutines test.
- Turbine or equivalent Flow test helper if accepted.
- Truth/assertk optional, or JUnit assertions if keeping dependency surface small.

Use version catalog if not already present:

```text
gradle/libs.versions.toml
```

Keep dependency versions explicit and centralized.

Update CI:

- Kotlin compile.
- KSP generated sources.
- Room schema export check.
- Compose tests where applicable.
- Detekt/ktlint if adopted.
- Existing Java/JUnit gates.
- Sonar paths include new modules.
- CodeQL paths include Kotlin modules.

### 10.5 Port FSRS

Options:

- Keep Java engine in `:fsrs` and move it into the new module graph.
- Port to Kotlin with Java-vs-Kotlin parity tests.

Recommended:

- Port to Kotlin only if it stays tightly scoped.
- Do it before the full scheduler rewrite so all new domain code depends on final FSRS API.

Completion criteria:

- FSRS fixture tests pass.
- Kani adapter tests pass.
- Impact report generated if behavior changed.
- Public API documented.
- No Android dependency in FSRS module.

### 10.6 Create Domain Models

Create focused domain models while preserving wire names.

Approach:

- Introduce new Kotlin domain models.
- Add mappers from old `Records*` classes if needed during transition.
- Write tests for all enum wire names and default settings.
- Avoid `Object... rest`.
- Avoid giant compatibility constructors.

Do not delete old `Records*` until all users are migrated.

### 10.7 Create Room Schema

Design Room entities/DAOs and the reset/migration policy.

Steps:

1. Export current schema facts from `LocalStoreSchema` only as behavioral input.
2. Design Room entities with indexes.
3. Implement DAOs by aggregate.
4. Choose destructive reset by default unless a migration is genuinely simpler.
5. If migration is retained for a table family, write a representative fixture test.
6. Implement repositories.
7. Add repository contract tests.
8. Add one reset/migration owner that prevents mixed old/new state.

DAO grouping:

```text
SettingsDao
SourceMirrorDao
SyncRunDao
SuspendedArchiveDao
ImportDao
DashboardDao
InventoryDao
StudyDao
LearningRepeatDao
ReviewLogDao
SimilarKanjiDao
TimelineDao
HistoricalSyncDao
StatsDao
```

Repository grouping:

```text
SettingsRepository
SourceCollectionRepository
SyncRepository
ImportRepository
KanjiRepository
StudyRepository
SimilarKanjiRepository
StatsRepository
TimelineRepository
DictionaryRepository
UpdateRepository
```

### 10.8 Rewrite Sync

Build new sync use case with old behavior.

Steps:

1. Keep old provider gateway contract visible.
2. Implement new `CollectionGateway` interface in `:domain` or `:ankidroid-api`.
3. Implement AnkiDroid provider gateway in `:ankidroid`.
4. Build `RunSyncUseCase`.
5. Write fake gateway tests.
6. Write provider instrumentation tests.
7. Make manual sync call new use case.
8. Make background sync call same use case.
9. Remove old `ManualSyncEngine` only after parity.

Parity checks:

- same rows for same collection snapshot
- same selected suspended imports
- same dashboard rows
- same kanji examples
- same queue seed results
- same sync run counts/status
- same removal summary behavior
- same errors for provider failure cases

### 10.9 Rewrite Scheduler

Build Kotlin domain scheduler.

Steps:

1. Port/replace `BridgeScheduler` behind `StudyScheduler` interface.
2. Preserve current facade if app tests depend on it, but delegate to new scheduler.
3. Split collaborators as listed above.
4. Run current scheduler characterization tests.
5. Add random/property-style tests for safe invariants if useful:
   - one active rung per item
   - no invalid similar rung
   - no disabled rung selected
   - no due interval below one day after persisted FSRS review
   - no promotion from learning repeat
   - no demotion from learning repeat
6. Switch repositories/use cases to new scheduler.

Do not rewrite Study UI and scheduler simultaneously.

### 10.10 Rewrite Compose Shell

Build app shell:

- `KaniApplication`
- Hilt graph
- `MainActivity`
- `KaniApp`
- navigation graph
- design system
- top-level scaffold
- route placeholders

Initial screens can show data from existing repositories while deeper features are migrated.

Completion criteria:

- App launches.
- Navigation works.
- Existing app icon/theme/package preserved.
- System bars styled.
- Back navigation works.
- Basic UI tests pass.

### 10.11 Rewrite Home

Build Home first because it is lower risk than Study.

Home completion criteria:

- shows provider readiness
- shows sync status
- shows current focus summary
- shows Study Now
- opens Settings
- starts manual sync through new use case
- navigates to Study/Stats/Browse
- handles empty/no-sync state

### 10.12 Rewrite Settings

Settings is large but lower risk than Study.

Steps:

1. Build Settings screen and sections.
2. Wire to settings repositories.
3. Save each setting through use cases.
4. Verify migrations from old key/value settings.
5. Verify foreground and background sync see changed settings.

Completion criteria:

- all current settings surfaces exist
- grouped expandable/cute style preserved
- import filters visible
- ladder controls work
- thresholds work
- learning/relearning steps work
- reminder/auto sync/update settings work
- data sources/attribution reachable

### 10.13 Rewrite Browse And Detail

Steps:

- Build inventory repository flows.
- Build Browse screen.
- Build Detail screen.
- Preserve search behavior.
- Preserve capped visible readings and hidden searchable readings.
- Preserve timeline.
- Preserve examples and source info.

### 10.14 Rewrite Stats

Steps:

- Build stats repository/use cases.
- Use review logs and study task logs.
- Preserve current Stats content.
- Keep evidence for future stats.

### 10.15 Rewrite Sync UI

After sync use case is stable:

- Build Sync status components.
- Add progress flow.
- Add provider readiness state.
- Add error-specific actions.
- Keep manual sync path tested against fake and live providers.

### 10.16 Rewrite Update UI

Steps:

- Port release parser if not already in domain.
- Implement update repository.
- Implement update ViewModel/screen.
- Preserve checksum/install behavior.
- Preserve stale-navigation protection through durable state.

### 10.17 Rewrite Study Last

Study is highest risk. Do it after:

- Room repository stable.
- Scheduler stable.
- Writing core stable.
- Compose design system stable.
- ViewModel state pattern stable.

Steps:

1. Build `StudyViewModel` against new `StudyRepository` and scheduler.
2. Build non-writing flashcard prompt.
3. Preserve reveal/pass/fail behavior.
4. Add gesture behavior.
5. Add typing prompt.
6. Add similar-choice prompt.
7. Add font prompt.
8. Add word-reading prompt.
9. Add writing prompt with pad wrapper.
10. Add ML Kit model/download state.
11. Add hint controls.
12. Add undo/block/replay behavior.
13. Add truthful progress snapshot.
14. Add focus done/all done states.
15. Run full UI/instrumented tests.

Do not delete old Study UI until the new Study UI passes parity tests.

### 10.18 Delete Old Architecture

Delete old code only when replacement is complete:

- `MainActivity*` Java inheritance chain.
- `MainActivityUiSupport` programmatic widget factory.
- `LocalStore*` raw SQLite helper chain.
- broad `Records*` compatibility namespaces if no longer needed.
- old adapters/mappers after migration.
- old tests that only test implementation details and have equivalent behavior tests.

Before deletion:

- Verify no test coverage gap.
- Verify release gate.
- Verify migration from production DB.
- Verify real provider sync if touched.

## 11. Engineering Best Practices

### 11.1 File And Class Size Limits

Hard rules for new code:

- No god classes.
- No 1,000-line classes.
- No 2,000-line activity/screen files.
- No giant `AppUi.kt`.
- No giant `Records.kt`.
- No giant `Repository.kt` that owns unrelated aggregates.

Targets:

- Screen composable file: under 300 lines.
- ViewModel: under 300 lines.
- Use case: under 200 lines.
- Repository implementation: under 400 lines.
- DAO: under 300 lines unless schema requires more.
- Domain service: under 300 lines.
- Design component file: under 250 lines.
- Test file: under 500 lines unless it is an intentional fixture/golden test.

If a file exceeds target:

- Split by responsibility.
- Extract state holder.
- Extract formatter.
- Extract mapper.
- Extract small domain service.
- Extract reusable composable.
- Extract test fixture builder.

Exceptions require a comment in PR/review explaining why the size is temporary or justified.

### 11.2 Package Ownership

Each package has one ownership area.

Bad:

```text
data/LocalStoreEverything.kt
ui/KaniAppWithAllScreens.kt
domain/Records.kt
sync/SyncAndImportAndQueue.kt
```

Good:

```text
data/study/StudyRepositoryImpl.kt
data/study/StudyDao.kt
domain/study/StudyScheduler.kt
domain/study/LadderMovementEngine.kt
feature/study/StudyViewModel.kt
feature/study/components/WritingPrompt.kt
```

### 11.3 Testability Rules

Every domain rule must be testable without Android.

If code needs Android to test:

- it probably belongs at an Android edge
- isolate it behind an interface
- fake it in domain tests

Required fakeable dependencies:

- clock
- AnkiDroid gateway
- ML Kit recognizer
- file system/APK download target
- GitHub release client
- WorkManager scheduler backend where practical
- notification sender

### 11.4 Error Handling

Use explicit result types for expected failures:

```kotlin
sealed interface SyncResult {
    data class Success(...) : SyncResult
    data class Skipped(...) : SyncResult
    data class Failure(val kind: SyncFailureKind, val message: String, val retryable: Boolean) : SyncResult
}
```

Do not:

- throw generic exceptions through UI layers for expected provider/config problems
- swallow errors silently
- show raw stack traces to users
- convert every failure to "unexpected"

### 11.5 Formatting And Static Analysis

Adopt:

- ktlint or spotless for Kotlin formatting
- detekt for Kotlin complexity if acceptable
- Android lint warnings-as-errors
- Sonar/CodeQL configured for new modules

Maintain:

- no suppression-style workarounds for real code smells
- no broad ignore rules unless explicitly approved
- no test-only logic in production code

### 11.6 Code Review Checklist

For every rewrite PR/slice:

- Behavior parity stated.
- Tests added/updated.
- Migration impact stated.
- File size/responsibility checked.
- No ViewModel has Context/Activity/Resources.
- No composable reads DAO/repository directly.
- No domain class imports Android.
- No background worker owns product logic.
- No sync path bypasses import/adaptive settings.
- No FSRS update without fixture impact.
- No settings change without an explicit reset/default/migration decision.
- No provider change without fake and, when needed, live provider validation.

## 12. Testing Strategy

### 12.1 Test Pyramid

Most tests should be JVM/domain tests.

Layers:

1. FSRS conformance tests.
2. Domain unit tests.
3. Repository tests with in-memory Room.
4. Migration tests.
5. ViewModel tests with fake repositories.
6. Compose UI tests.
7. Android instrumentation tests for Android edges.
8. Live AnkiDroid emulator tests for provider/sync release risk.

### 12.2 Required Test Suites

FSRS:

- `:fsrs:test`
- upstream reference fixture
- Java/Kotlin parity if porting

Domain:

- scheduler
- ladder settings
- learning steps
- adaptive planner
- import selector
- kanji analyzer
- similar kanji
- writing core
- stats calculators
- release parser

Data:

- Room DAO tests
- repository tests
- migration tests from current DB version
- key settings migrations
- transaction tests

App/ViewModel:

- Home ViewModel
- Study ViewModel
- Settings ViewModel
- Sync ViewModel
- Update ViewModel
- Stats ViewModel

Compose:

- Home layout/CTA
- Study controls above fold
- Study reveal behavior
- Writing pad actions
- Settings controls
- Progress truth
- Provider permission states

Instrumentation:

- AnkiDroid fake provider
- real provider live gate when sync/provider changes
- ML Kit model/recognizer integration where feasible
- APK FileProvider/update install flow
- WorkManager scheduling behavior

### 12.3 Golden/Comparison Tests

Before switching subsystems, build comparison harnesses:

- old scheduler vs new scheduler for representative study items
- old import analysis vs new import analysis for fixture snapshots
- legacy DB reset/migration path opens the new app cleanly and rebuilds from sync
- old FSRS Java vs new FSRS Kotlin
- old Study progress snapshot vs new progress calculator

Keep fixtures small enough to maintain, but broad enough to catch the known regressions.

### 12.4 Acceptance Gates

Local deterministic gate:

```sh
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
  ./gradlew ciFast
```

Quality gate:

```sh
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
  ./gradlew ciQuality
```

Release gate:

```sh
ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk \
  ./gradlew ciRelease
```

Additional gates as modules are added:

```sh
./gradlew :fsrs:test
./gradlew :domain:test
./gradlew :data:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest
```

Adjust task names to final module names.

Provider/sync release-risk gate:

- Fake provider instrumentation always for provider code.
- Live AnkiDroid emulator run when sync/provider behavior changes.
- Local real-collection live gate remains stricter than CI fixture gate when release touches provider/sync/local-store/live instrumentation/fixture paths.

## 13. CI/CD Plan

### 13.1 Gradle Tasks

Update root tasks:

```text
ciFast
  :fsrs:test
  :domain:test
  :data:testDebugUnitTest
  :app:testDebugUnitTest
  :app:compileDebugAndroidTestKotlin or Java equivalent
  :app:lintDebug
  dictionary/asset tests

ciQuality
  ciFast
  coverage reports
  bytecode for Sonar

ciRelease
  ciFast
  signed release APK
```

Keep current release signing behavior.

### 13.2 GitHub Actions

Update:

- Android CI workflow for Kotlin modules.
- Instrumented workflow for Compose/Room/provider tests.
- Release workflow for final module names.
- Sonar workflow paths/binaries/coverage.
- CodeQL workflow build step to force clean Kotlin/Java compile.

Do not simplify CodeQL forced compile in a way that lets Gradle mark tasks up-to-date and starve extraction.

### 13.3 Coverage

Priorities:

- Domain correctness coverage.
- FSRS conformance coverage.
- Migration coverage.
- Provider sync behavior coverage.
- Study ViewModel and scheduler coverage.

Do not chase coverage by writing brittle UI tests for everything when a domain test would be better.

### 13.4 Release Verification

Before tagging a release from the rewritten app:

- `ciFast` passes locally.
- Signed release build passes locally.
- Legacy DB reset/migration path tested on emulator.
- App launches after reset/migration on emulator.
- Manual sync works against fake provider.
- Manual sync works against real AnkiDroid if provider/sync touched.
- Study can complete at least one session.
- Update check/install flow still works or is intentionally disabled until verified.
- GitHub Actions release workflow publishes APK and SHA-256.

## 14. Migration From Current App

### 14.1 User Data Preservation

Must preserve:

- settings
- sync runs
- source mirror
- suspended archive
- suspended imports
- dashboard rows
- kanji examples
- kanji inventory
- similar-kanji pairs
- similar-choice state
- similar-writing repair state
- study items
- task memories
- learning repeats
- review logs
- study task logs
- timeline events
- historical sync snapshots
- stats evidence
- local kanji suspensions
- update state if persisted

### 14.2 Reset And Migration Safety Path

Recommended implementation:

1. On first launch of rewritten app, detect legacy DB version/name.
2. If the legacy DB is incompatible with the clean Room schema, move or delete
   it through one reset owner before opening the new database.
3. Initialize Room/DataStore from domain defaults.
4. Show clear status that Kani needs a fresh AnkiDroid sync.
5. Keep AnkiDroid source data untouched.
6. Only run a non-destructive migration for a table family if it is simpler
   than reset-and-resync and has a focused fixture test.

Validation after reset/migration:

- typed settings load from current defaults or intentional migrated values
- app opens to a coherent empty/sync-needed state
- first sync rebuilds source mirror and dashboard data
- suspended archive count
- sync run count
- timeline event count
- no duplicate timeline dedupe keys
- no invalid ladder settings
- no study item with disabled/unavailable current rung after effective-rung repair
- no task memory with invalid stability/difficulty

### 14.3 Behavior-Preserving Repairs

Migrations may repair invalid historical data only where current code already repairs it or where invalid data would crash the app.

Allowed repairs:

- clamp invalid retention/difficulty to current behavior
- fallback invalid ladder order/enabled settings to defaults
- ensure at least one always-available rung
- repair missing conditional similar availability by recomputing from pairs
- preserve old import defaults

Not allowed without explicit product approval:

- reset all FSRS memory
- reset all study items
- broaden import sources
- delete review logs
- delete sync history
- mark all cards new
- collapse similar task state into study item state if behavior changes

## 15. Detailed Implementation Sequence

This sequence is meant to be followed from start to finish. It is not a timeline.

### Step 1: Lock Current Behavior

Deliverables:

- behavior checklist in tests
- scheduler characterization tests
- import/sync fixture tests
- DB migration fixture from current schema
- Study UI key instrumentation tests
- FSRS fixture tests confirmed

Done when:

- current app passes all characterization tests
- tests fail if known product contracts are broken

### Step 2: Build Kotlin/Gradle Foundation

Deliverables:

- Kotlin plugins
- version catalog
- Compose setup
- Hilt setup
- Room setup
- KSP setup
- coroutine test setup
- module skeletons
- CI updated to compile skeletons

Done when:

- empty/skeleton modules compile
- app still builds
- CI path includes new modules

### Step 3: FSRS Module Decision And Port

Deliverables:

- final `:fsrs` API
- Java retained or Kotlin port
- conformance fixtures passing
- Kani adapter compatibility tests
- docs updated with provenance

Done when:

- FSRS output matches current pinned behavior
- scheduler tests pass through adapter

### Step 4: Domain Model Extraction

Deliverables:

- focused Kotlin domain models
- wire-name compatibility tests
- mappers from old records where needed
- no `Object... rest` in new code

Done when:

- scheduler/import/writing core can compile against new models or adapters

### Step 5: Room Schema And Repositories

Deliverables:

- Room entities/DAOs
- reset/migration owner
- repositories
- reset/migration tests where a migration is deliberately kept
- repository tests

Done when:

- legacy DB/reset fixture opens the app cleanly
- repositories expose equivalent data
- clean sync rebuilds critical data

### Step 6: Sync Domain Rewrite

Deliverables:

- collection gateway interface
- AnkiDroid gateway implementation
- sync use case
- import analysis services
- fake provider tests
- sync repository transactions

Done when:

- manual sync fixture produces equivalent rows/queue
- background sync calls same use case
- provider errors map correctly

### Step 7: Scheduler Rewrite

Deliverables:

- Kotlin scheduler facade
- focused scheduler collaborators
- parity tests against old behavior
- adaptive planner split
- progress calculator

Done when:

- all scheduler characterization tests pass
- old scheduler can be removed or adapter-delegated

### Step 8: Compose App Shell

Deliverables:

- `MainActivity.kt`
- `KaniApp`
- navigation graph
- design system
- route placeholders
- Hilt app graph

Done when:

- app launches
- navigation works
- basic UI tests pass

### Step 9: Home And Sync Status

Deliverables:

- Home screen
- Home ViewModel
- sync status/progress components
- provider readiness components

Done when:

- user can see status and start sync
- current behavior is covered

### Step 10: Settings

Deliverables:

- grouped Settings UI
- settings repositories/use cases
- Anki source settings
- import filters
- study behavior
- ladder controls
- automation/update/reference data

Done when:

- all current settings are present
- settings affect sync/study/background paths
- old settings migrate

### Step 11: Browse, Detail, Stats

Deliverables:

- Browse screen
- Kanji detail screen
- Stats screen
- related ViewModels/use cases

Done when:

- current user-facing info exists
- search/readings/timeline/stats behave as before

### Step 12: Update And Backup

Deliverables:

- updater screen
- update repository/use cases
- checksum/install flow
- backup worker/scheduler

Done when:

- current release/update behavior works
- navigation-away stale action bug remains fixed

### Step 13: Writing Infrastructure

Deliverables:

- writing-core module
- ML Kit Android wrapper
- stroke guide repository
- Compose/AndroidView writing pad
- model download state
- writing tests

Done when:

- undo/block/replay/hints/recognition work independently of Study screen

### Step 14: Study Screen Rewrite

Deliverables:

- Study ViewModel
- Study screen states
- all prompt composables
- action bar
- progress truth
- writing flow
- gestures
- full tests

Done when:

- current Study behavior is functionally preserved
- no-scroll control invariants pass
- writing/similar/typing/recognition/font/word prompts all work

### Step 15: Delete Old Code

Deliverables:

- remove old Java UI
- remove old raw SQLite store
- remove compatibility records if no longer needed
- remove dead tests
- update docs

Done when:

- full gate passes
- migration passes
- real provider gate passes where needed
- APK release path still works

## 16. Acceptance Criteria For The Whole Rewrite

The rewrite is complete only when all of these are true:

- App package and install/update identity are preserved.
- Existing user database migrates without data loss.
- Default suspended-only import behavior is preserved.
- Manual sync works.
- Daily sync works after first manual sync.
- Provider permission/config errors are user-visible and non-crashing.
- Kiku defaults and editable mappings work.
- Study now works.
- Study progress is truthful.
- Reveal/answer/pass/fail controls stay above the fold.
- Writing pad supports current hint/undo/block/replay behavior.
- Similar-kanji tasks behave as before.
- Ladder settings behave as before.
- FSRS scheduling matches current pinned behavior unless an explicit FSRS update was approved and impact-reviewed.
- Stats preserve current user-facing meaning.
- Browse/detail preserve inventory/search/timeline behavior.
- Data sources/attribution remain available.
- GitHub update flow works.
- Background workers use same domain paths as foreground actions.
- No domain module imports Android.
- No composable/ViewModel accesses DAO/provider directly.
- No god classes or massive files remain in new architecture.
- `ciFast` passes.
- `ciQuality` passes.
- Signed release build passes.
- Live AnkiDroid gate passes for provider/sync risk.
- GitHub Actions paths are updated and green.

## 17. Explicit Non-Goals

Do not include these unless separately requested:

- Backend service.
- Web/PWA frontend.
- Flutter.
- React Native.
- Desktop app.
- Play Store migration.
- Cloud sync.
- Accounts.
- Telemetry.
- Social/sharing features.
- Generic SRS replacement of Kani's ladder.
- Outsourcing study queue ownership to a third-party scheduler.
- Replacing AnkiDroid with direct Anki collection file mutation.
- Broad JMdict runtime dictionary path.
- Redesigning product behavior while doing architecture rewrite.

## 18. Reference Links

Repo-local references:

- `agents.md`
- `README.md`
- `documentation/srs.md`
- `docs/fsrs-impact-report.md`
- `~/Documents/src/github/fsrs_java/README.md`
- `~/Documents/src/github/fsrs_java/ALGORITHM.md`
- `~/Documents/src/github/fsrs_java/MIGRATION.md`

External references used for architecture direction:

- Android architecture recommendations: https://developer.android.com/topic/architecture/recommendations
- Jetpack Compose overview: https://developer.android.com/compose
- Navigation with Compose: https://developer.android.com/develop/ui/compose/navigation
- Room: https://developer.android.com/room
- DataStore: https://developer.android.com/topic/libraries/architecture/datastore
- Hilt: https://developer.android.com/training/dependency-injection/hilt-android

## 19. Final Goal

At the end of this rewrite, Kani should be the same product from the user's point of view, but with an architecture that makes future work straightforward:

- A new study rung should be added by editing a focused domain model, scheduler policy, repository mapping, and one UI prompt, not by spelunking through a giant activity.
- A sync/import change should be implemented in a sync/import use case and tested against fake/live provider contracts, not hidden in UI code.
- A settings change should have a typed setting, migration, repository method, ViewModel action, and UI row, not a string key scattered through the app.
- An FSRS update should be a pinned algorithm update with fixtures and an impact report, not a risky scheduler rewrite.
- A writing UX improvement should touch writing-core/writing-android/Study UI seams, not scheduler internals unless grading behavior intentionally changes.
- A stats feature should consume durable local evidence, not scrape current UI state.

The rewrite succeeds when the architecture makes the correct thing easy and the risky thing obvious.
