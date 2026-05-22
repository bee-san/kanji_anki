# Kotlin + Compose Rewrite Exit Goal

This branch is done only when the items below are complete, reviewed, committed,
pushed, and verified. Do not keep adding unrelated helper extractions once an
item is satisfied.

## Current State

- Branch: `codex-android-architecture-20260518`
- Last verified commit before this document: `f87a9083`
- `app/src/main` is Kotlin-only.
- `fsrs-java/src/main` is Kotlin-only.
- `core/src/main/java/dev/bee/kanjianki/core` still has 18 Java files.
- `FrequencyRetentionRanges.java` is an intentional compatibility exception
  unless a Kotlin replacement can keep `Rule` truly private to Java reflection.

## Remaining Migration Items

### 1. Finish Direct Compose Routing

- All user-facing routes enter through direct `setContent` / `composeRoute`
  surfaces: Home, Settings, Stats, Games, Study, Update, Browse, Detail, Sync,
  and secondary Home screens.
- No production primary screen is assembled from `LinearLayout`, `TextView`,
  `Button`, `ScrollView`, or other manual View-tree layout code.
- `ComposeView` remains only for Android interop that genuinely needs a View:
  the handwriting pad, small legacy wrappers that are still being retired, or
  androidTest-local helpers.
- `MainActivityBase`, `MainActivityHome`, `MainActivitySettings`, and
  `MainActivityStudy` are coordinators only: route selection, model building,
  and action dispatch are allowed; screen layout code is not.

### 2. Remove Production Test Bridges

- Helpers whose only purpose is instrumentation access move from
  `app/src/main` into `app/src/androidTest`.
- Production `MainActivity*` classes do not expose methods solely because tests
  call them.
- Tests either drive the real Compose route, call pure model builders, or use
  androidTest-local bridge helpers.

### 3. Finish Settings As A Model-Driven Compose Screen

- The Settings route is one screen model plus focused panel composables.
- These settings retain behavior parity: update/release, reference data,
  categories, automation reminder, auto sync, study ladder, ladder thresholds,
  study-ahead, learning steps, retention, workload, study sort, Anki source
  validation, import filters, frequency range, and note type mapping.
- Toasts, dialogs, file pickers, permissions, and note-type selection are behind
  focused action/controller interfaces, not embedded in layout code.
- Scroll preservation and expanded-section state match the current app.

### 4. Finish Study As Model-Driven Compose Routes

- Flashcard, writing, similar-kanji choice, meaning-kanji choice, done, empty,
  and focus-done states render through Compose route surfaces.
- Action bars, top bars, prompt/answer panels, flashcard cards, writing status,
  writing toolbar, writing actions, choice grids, choice results, and typing
  answers are model-driven composables.
- Gesture behavior, reveal-before-grading, above-the-fold controls, writing
  recognition, repair actions, similar-kanji routing, and "study more new cards"
  behavior retain current parity.
- Study stays split into route, model, action, scheduler, writing, and surface
  files. No new god class replaces the old one.

### 5. Finish Home/Browse/Sync Compose Ownership

- Home overview, metrics, action chrome, focus queue, recent mistakes, sync
  result screens, browse search, browse detail, examples, timeline, and empty
  states are Compose-owned.
- Search query preservation, back navigation, "study this kanji", recent
  mistakes navigation, sync CTA behavior, and browse-detail timeline behavior
  retain current parity.
- Home route files remain feature-focused.

### 6. Migrate Remaining Core Java

Migrate or explicitly justify every remaining file:

- `AdaptiveLoadPlanner.java`
- `BridgeScheduler.java`
- `FrequencyRetentionRanges.java`
- `HomeTextCopy.java`
- `KanjiAnalyzer.java`
- `KanjiGameEngine.java`
- `KanjiImpactAnalyzer.java`
- `KanjiImportSelector.java`
- `KanjiInventoryBuilder.java`
- `RecordsBase.java`
- `RecordsImportModels.java`
- `RecordsSchedulerModels.java`
- `RecordsStudyModels.java`
- `RecordsSyncModels.java`
- `ReviewTransitionEngine.java`
- `SettingsTextCopy.java`
- `SimilarKanjiChoicePlanner.java`
- `SimilarKanjiIndex.java`

Migration order should be:

1. Copy/text helpers.
2. Small planners/selectors.
3. Analyzers and game engine.
4. Scheduler/review logic.
5. Record/model containers.
6. Compatibility exceptions with tests and documentation.

Every Java-to-Kotlin migration must preserve Java-callable APIs where Android
code or tests still depend on method-style accessors, Java records, public
fields, constructor visibility, or nullable behavior.

### 7. Data Stack Boundary Check

- Existing SQLite/local-store behavior remains backward-compatible.
- Repository/use-case boundaries are clear enough that UI routes do not issue
  raw SQL or mutate persistence directly.
- Any Room/DataStore/Hilt adoption is either completed for a bounded vertical
  slice or deferred with a written reason. Do not claim the modern stack is done
  if the app still uses the existing local-store implementation.

### 8. Verification And Review Gates

- Each migration slice has a focused commit, pushed to the PR branch.
- Reviewer agents inspect risky commits and their findings are fixed or
  documented.
- Required local gates pass:
  - `./gradlew ciFast`
  - `./gradlew --no-build-cache clean :core:test :app:compileDebugKotlin`
  - `./gradlew :app:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestJavaWithJavac`
- PR checks are green.
- A final smoke pass covers Home, Settings, Study flashcard, Study writing,
  Browse/Detail, Stats, Games, manual sync, and update/settings navigation.

## Paste-Ready `/goal`

Complete PR #11 as a finite Kotlin + Compose rewrite, not an indefinite Java
cleanup branch. Done means: every production route is direct Compose or a
documented Android interop exception; Home, Settings, Study, Browse/Detail,
Stats, Games, Sync, and Update are model-driven Compose surfaces with current
behavior parity; production test-only bridges are removed or moved to
androidTest; `app/src/main` and `fsrs-java/src/main` remain Kotlin-only;
`core/src/main/java/dev/bee/kanjianki/core` is reduced to zero Java files except
for explicitly documented compatibility exceptions with tests; remaining data
and repository boundaries are clean and backward-compatible; no new god classes
or layout dumps are introduced; every slice is committed, reviewed by an agent,
pushed, and verified with focused tests plus `ciFast`; final verification passes
`./gradlew ciFast`, `./gradlew --no-build-cache clean :core:test
:app:compileDebugKotlin`, androidTest compilation, PR CI, and a smoke pass for
Home, Settings, Study flashcard/writing, Browse/Detail, Stats, Games, Sync, and
Update. When all checklist items in `docs/rewrite-exit-goal.md` are complete,
the rewrite is done and the branch may be merged to main.
