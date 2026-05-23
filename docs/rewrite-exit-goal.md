# Kotlin + Compose Rewrite Exit Goal

This branch is done only when the items below are complete, reviewed, committed,
pushed, and verified. Do not keep adding unrelated helper extractions once an
item is satisfied.

## Current State

- Branch: `codex-android-architecture-20260518`
- Last verified code commit before this refresh: `44df402e`
- `app/src/main` is Kotlin-only.
- `fsrs-java/src/main` is Kotlin-only.
- `writing-core/src/main` is Kotlin-only.
- `sync-domain/src/main` is Kotlin-only.
- `update-core/src/main` is Kotlin-only.
- `core/src/main/java/dev/bee/kanjianki/core` has 1 Java file.
- `FrequencyRetentionRanges.java` is the intentional compatibility exception:
  the Kotlin replacement attempt emitted a public synthetic
  `DefaultConstructorMarker` constructor for nested `Rule`, while
  `FrequencyRetentionRangesTest#ruleConstructorStaysPrivateForJavaInterop`
  requires exactly one private Java-visible constructor.
- Compose is wired through direct `setContent` route surfaces. The only live
  production `AndroidView` bridge is the handwriting pad, which must host the
  real `DrawingPadView`.
- Production `ForTests` APIs have been removed from app main sources; test
  dependency overrides now go through the debug-only mutable
  `MainActivityRuntimeOverrides`.
- Latest local verification on 2026-05-23 passed `./gradlew ciFast`.
- Latest clean compile verification on 2026-05-23 passed
  `./gradlew --no-build-cache clean :core:test :app:compileDebugKotlin`.
- Latest androidTest compilation verification on 2026-05-23 passed
  `./gradlew :app:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestJavaWithJavac`.
- Latest targeted emulator smoke on 2026-05-23 passed
  `MainActivityStudyRouteSmokeInstrumentedTest`, covering production Study
  flashcard, writing, similar-kanji choice, and meaning-kanji choice route
  rendering.
- Latest targeted primary-route emulator smoke on 2026-05-23 passed
  `MainActivityPrimaryRouteSmokeInstrumentedTest`, covering production Home,
  Settings, Browse, Detail, Stats, Games, and Update route rendering.
- Latest targeted emulator sync progress test on 2026-05-23 passed
  `ManualSyncEngineInstrumentedTest#manualSyncReceivesOrderedProgressEvents`.
- GitHub Actions run `26331843021` passed for commit
  `44df402eb6e2b80bb240e29492676cfc1e375639`.
- SonarQube workflow run `26331843026` passed for the same branch head, and the
  SonarCloud Code Analysis check completed successfully.

## Final Prompt-To-Artifact Audit

This audit was refreshed on 2026-05-23 against commit
`44df402eb6e2b80bb240e29492676cfc1e375639`.

| Requirement | Evidence |
| --- | --- |
| Kotlin-first production code with only the documented Java exception | `find app/src/main core/src/main domain/src/main dictionary-core/src/main writing-core/src/main sync-domain/src/main fsrs-java/src/main update-core/src/main -name '*.java' -print \| sort` returns only `core/src/main/java/dev/bee/kanjianki/core/FrequencyRetentionRanges.java`. |
| Direct Compose user-facing routes, with handwriting pad as the only documented `AndroidView` exception | `rg -n "AndroidView\|ComposeView\|setContent\|composeRoute\|LinearLayout\|ScrollView\|TextView\|Button\\(" app/src/main/kotlin app/src/main/java` shows `MainActivityShellHost.setContent`, route `composeRoute` entry points, and one production `AndroidView` in `MainActivityStudyWritingPadCompose`; no production `ComposeView` remains. |
| Production test bridges removed | `rg -n "ForTests\|forTests\|set.*ForTests\|@VisibleForTesting" app/src/main/kotlin app/src/main/java core/src/main domain/src/main dictionary-core/src/main writing-core/src/main sync-domain/src/main fsrs-java/src/main update-core/src/main` returns no matches. |
| Home, Settings, Browse, Detail, Stats, Games, and Update route parity smoke | `env ANDROID_HOME=/home/bee/Documents/src/github/thaiwrite/.android-sdk ANDROID_SDK_ROOT=/home/bee/Documents/src/github/thaiwrite/.android-sdk ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.bee.kanjianki.MainActivityPrimaryRouteSmokeInstrumentedTest` passed on the current head. |
| Study flashcard, writing, similar-kanji choice, and meaning-kanji choice route parity smoke | `env ANDROID_HOME=/home/bee/Documents/src/github/thaiwrite/.android-sdk ANDROID_SDK_ROOT=/home/bee/Documents/src/github/thaiwrite/.android-sdk ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.bee.kanjianki.MainActivityStudyRouteSmokeInstrumentedTest` passed on the current head. |
| Manual sync progress/parity smoke | `env ANDROID_HOME=/home/bee/Documents/src/github/thaiwrite/.android-sdk ANDROID_SDK_ROOT=/home/bee/Documents/src/github/thaiwrite/.android-sdk ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.bee.kanjianki.sync.ManualSyncEngineInstrumentedTest#manualSyncReceivesOrderedProgressEvents` passed on the current head. |
| Clean compile gate | `./gradlew --no-build-cache clean :core:test :app:compileDebugKotlin` passed on the current head. Local Kotlin daemon cache warnings recovered through Gradle's fallback compiler and the command returned `BUILD SUCCESSFUL`. |
| androidTest compilation gate | `./gradlew :app:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestJavaWithJavac` passed on the current head. |
| Deterministic local fast gate | `./gradlew ciFast` passed on the current head. |
| PR CI and SonarQube green | PR #11 check rollup is green for `44df402e`: Android CI run `26331843021`, SonarQube run `26331843026`, and SonarCloud Code Analysis all succeeded. |
| Branch state | `git status --short --branch` reports `codex-android-architecture-20260518...origin/codex-android-architecture-20260518` with no local changes before this evidence-doc update. |

## Completion Checklist

### 1. Finish Direct Compose Routing

- All user-facing routes enter through direct `setContent` / `composeRoute`
  surfaces: Home, Settings, Stats, Games, Study, Update, Browse, Detail, Sync,
  and secondary Home screens.
- No production primary screen is assembled from `LinearLayout`, `TextView`,
  `Button`, `ScrollView`, or other manual View-tree layout code.
- `ComposeView` remains only in test-local helpers. Production Android interop
  is limited to the handwriting pad `AndroidView` bridge.
- `MainActivityBase`, `MainActivityHome`, `MainActivitySettings`, and
  `MainActivityStudy` are coordinators only: route selection, model building,
  and action dispatch are allowed; screen layout code is not.

### 2. Remove Production Test Bridges

- Helpers whose only purpose is instrumentation access have moved from
  `app/src/main` into `app/src/androidTest`.
- Production `MainActivity*` classes must not expose methods solely because tests
  call them.
- Tests either drive the real Compose route, call pure model builders, or use
  androidTest-local bridge helpers.
- Current hard inventory: `rg "ForTests|forTests|set.*ForTests|@VisibleForTesting"
  app/src/main/kotlin app/src/main/java` returns no matches.

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

Migrate or explicitly justify every remaining file. The finite remaining list is:

- `FrequencyRetentionRanges.java`

The only accepted exception is `FrequencyRetentionRanges.java`, because its
Java-reflection privacy contract is documented and tested. A Kotlin migration
attempt was rejected by the existing reflection test after emitting an
additional public synthetic constructor.

Every Java-to-Kotlin migration must preserve Java-callable APIs where Android
code or tests still depend on method-style accessors, Java records, public
fields, constructor visibility, or nullable behavior.

### 7. Remove Legacy Route Mirrors And Test Bridges

- `MainActivityShellHost` no longer creates legacy `LinearLayout`,
  `ScrollView`, or placeholder `ComposeView` mirrors unless a production
  Android interop need is documented.
- Production methods that exist only for instrumentation are moved to
  `app/src/androidTest` helpers.
- Android tests use real Compose routes or explicit test-only bridges.

### 8. Data Stack Boundary Check

- Existing SQLite/local-store behavior remains backward-compatible.
- Repository/use-case boundaries are clear enough that UI routes do not issue
  raw SQL or mutate persistence directly.
- Any Room/DataStore/Hilt adoption is either completed for a bounded vertical
  slice or deferred with a written reason. Do not claim the modern stack is done
  if the app still uses the existing local-store implementation.
- Current state: Room, DataStore, and Hilt are not adopted in production code;
  final completion must either implement bounded slices or leave an explicit
  deferral note tied to the existing repository boundaries.
- Current deferral: this PR does not adopt Room, DataStore, or Hilt. It keeps
  `LocalStore`/`SQLiteOpenHelper` for backward-compatible migrations and runtime
  data, while narrowing persistence behind focused classes such as
  `SettingsRepository`, `SettingsStorage`, `SyncRunRepository`, and the
  `LocalStore*` feature stores. A future Room/DataStore/Hilt migration should be
  a separate persistence PR with schema fixtures and upgrade tests, not a hidden
  requirement of this Compose route rewrite.

### 9. Verification And Review Gates

- Each migration slice has a focused commit, pushed to the PR branch.
- Reviewer agents inspect risky commits and their findings are fixed or
  documented.
- Required local gates pass:
  - `./gradlew ciFast`
  - `./gradlew --no-build-cache clean :core:test :app:compileDebugKotlin`
  - `./gradlew :app:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestJavaWithJavac`
- PR checks are green.
- SonarQube PR-safe analysis is green.
- A final smoke pass covers Home, Settings, Study flashcard, Study writing,
  Study choice, Browse/Detail, Stats, Games, manual sync, and update/settings
  navigation.
- Current smoke evidence: Home, Settings, Browse, Detail, Stats, Games, and
  Update are covered by
  `MainActivityPrimaryRouteSmokeInstrumentedTest`. Manual sync is covered by
  `ManualSyncEngineInstrumentedTest#manualSyncReceivesOrderedProgressEvents`.
  Study flashcard, Study writing, Study similar-kanji choice, and Study
  meaning-kanji choice are covered by
  `MainActivityStudyRouteSmokeInstrumentedTest`.

## Paste-Ready `/goal`

Complete PR #11 as a finite Kotlin + Compose rewrite, not an indefinite Java
cleanup branch. Done means: every production route is direct Compose or a
documented Android interop exception; Home, Settings, Study, Browse/Detail,
Stats, Games, Sync, and Update are model-driven Compose surfaces with current
behavior parity; production test-only bridges are removed or moved to
androidTest; `app/src/main` and `fsrs-java/src/main` remain Kotlin-only;
the only remaining Java file under main sources is the explicitly documented
and tested `FrequencyRetentionRanges.java` compatibility exception, kept only
because its nested `Rule` constructor must remain truly private to Java
reflection; `core/src/main/java/dev/bee/kanjianki/core` is otherwise zero Java
files;
production legacy route mirrors and test-only bridges are removed or moved to androidTest; remaining data and repository
boundaries are clean and backward-compatible; no new god classes or layout dumps
are introduced; every slice is committed, reviewed by an agent, pushed, and
verified with focused tests plus `ciFast`; final verification passes
`./gradlew ciFast`, `./gradlew --no-build-cache clean :core:test
:app:compileDebugKotlin`, androidTest compilation, PR CI, and a smoke pass for
Home, Settings, Study flashcard/writing, Browse/Detail, Stats, Games, Sync, and
Update. When every item in this checklist is complete, the rewrite is done and
the branch may be merged to main.
