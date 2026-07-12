# Kani Rewrite Continuation

Last updated: 2026-05-23

> Archived rewrite-branch handoff. "Current" and scheduler/settings claims in
> this file describe the May 2026 rewrite snapshot, not DB31 production
> behavior. For the live scheduler contract, see
> [`adaptive-two-core-scheduler.md`](adaptive-two-core-scheduler.md).

## Branch and PR

- Rewrite branch: `codex-android-architecture-20260518`
- Pull request: `https://github.com/bee-san/kanji_anki/pull/11`
- Latest verified commit before this note: `d7ab9ba9 Fix remaining Sonar code smells`
- Latest confirmed PR state before this note: draft branch `codex-android-architecture-20260518`, mergeable with green PR checks

If `/tmp` has been wiped, resume from a normal checkout:

```bash
cd /home/bee/Documents/src/github/kanji_anki
git fetch origin
git switch codex-android-architecture-20260518
git pull --ff-only
```

If the branch does not exist locally:

```bash
git switch --track origin/codex-android-architecture-20260518
```

On 2026-05-19, the original checkout at `/home/bee/Documents/src/github/kanji_anki`
had a read-only `.git` mount. Its working tree content matched the pushed branch,
but `git reset --mixed origin/codex-android-architecture-20260518` could not create
`.git/index.lock`. If that persists, either remount/fix `.git` first or continue
from the writable clone/worktree and push from there.

## Current Rewrite State

The rewrite branch is intentionally being moved in small, reviewable commits. The app still keeps the original Android package identity, and the final proof pass for the current code head has now completed.

Completed foundations include:

- `fsrs-java` is integrated into the Gradle build with tests and coverage verification.
- FSRS is wired through the domain adapter path used by review transitions.
- CI is split into fast deterministic jobs and currently finishes well under the 15-minute ceiling.
- Several study/writing behaviors have been moved from activity code into core or writing-core policies.
- Kotlin and Compose are now wired into the Android app.
- Real Compose surfaces now include Home header/actions/CTA/metrics, Home recent mistakes and browse-detail timeline, Stats, Settings update/reference/category/automation hero areas, Games question card, Study top bar, flashcard card/actions, writing action bars, and done actions.
- Recent pushed slices removed the legacy shell/content/scroll mirrors, removed production `ComposeView` and `TextView` helper wrappers, rendered flashcards directly in Compose, and migrated `update-core` production code to Kotlin.
- Production View interop is now limited to the handwriting pad `AndroidView`, which hosts the real `DrawingPadView`.
- `writing-core/src/main` and `sync-domain/src/main` are Kotlin-only.
- Production `ForTests` APIs have been removed from app main sources. Instrumentation dependency overrides now go through the debug-only mutable `MainActivityRuntimeOverrides`; the release variant exposes null-only overrides, and the drawing-pad replay state query is production-neutral.
- Study helper bridges have been pushed into `androidTest`; `learningPanel(session)` no longer lives in `MainActivityStudy`.
- Room, DataStore, and Hilt are explicitly deferred from this PR. The live
  production data stack is still `LocalStore`/`SQLiteOpenHelper` with focused
  repository/storage seams such as `SettingsRepository`, `SettingsStorage`,
  `SyncRunRepository`, and feature-specific `LocalStore*` classes. Do not claim
  those libraries are migrated unless Gradle dependencies and production code
  are added in a later persistence PR.
- Manual sync progress now has separate local-save and practice-queue stages.
  The live emulator smoke reached `Sync complete`; the local save stage is still
  a relatively long persistence window on the emulator dataset, but it is no
  longer mislabeled as practice-queue work.
- The final Sonar cleanup removed the last three new-code smells from PR #11.
  SonarCloud now reports zero open PR issues, zero new code smells, and zero new
  duplication on the live PR analysis.
- Study flashcard, Study writing, similar-kanji choice, and meaning-kanji choice
  have targeted production-route emulator smoke coverage in
  `MainActivityStudyRouteSmokeInstrumentedTest`.
- Home, Settings, Browse, Detail, Stats, Games, and Update have targeted
  production-route emulator smoke coverage in
  `MainActivityPrimaryRouteSmokeInstrumentedTest`.

## Verification Baseline

The final proof pass on 2026-05-23 verified code head
`d7ab9ba9e686b8b5173137a75148bceddaff2cef`.

The focused local gates passed with:

```bash
./gradlew :core:test :app:testDebugUnitTest
./gradlew --no-build-cache clean :core:test :app:compileDebugKotlin
./gradlew :app:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestJavaWithJavac
./gradlew ciFast
```

The clean compile gate passed with Kotlin daemon cache warnings followed by
Gradle's non-daemon fallback compile; the command returned `BUILD SUCCESSFUL`.

The targeted emulator gates passed with:

```bash
env ANDROID_HOME=/home/bee/Documents/src/github/thaiwrite/.android-sdk ANDROID_SDK_ROOT=/home/bee/Documents/src/github/thaiwrite/.android-sdk ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.bee.kanjianki.MainActivityPrimaryRouteSmokeInstrumentedTest
env ANDROID_HOME=/home/bee/Documents/src/github/thaiwrite/.android-sdk ANDROID_SDK_ROOT=/home/bee/Documents/src/github/thaiwrite/.android-sdk ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.bee.kanjianki.MainActivityStudyRouteSmokeInstrumentedTest
env ANDROID_HOME=/home/bee/Documents/src/github/thaiwrite/.android-sdk ANDROID_SDK_ROOT=/home/bee/Documents/src/github/thaiwrite/.android-sdk ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=dev.bee.kanjianki.sync.ManualSyncEngineInstrumentedTest#manualSyncReceivesOrderedProgressEvents
```

The inventory and route-shape audit passed with:

```bash
find app/src/main core/src/main domain/src/main dictionary-core/src/main writing-core/src/main sync-domain/src/main fsrs-java/src/main update-core/src/main -name '*.java' -print | sort
rg -n "AndroidView|ComposeView|setContent|composeRoute|LinearLayout|ScrollView|TextView|Button\(" app/src/main/kotlin app/src/main/java
rg -n "ForTests|forTests|set.*ForTests|@VisibleForTesting" app/src/main/kotlin app/src/main/java core/src/main domain/src/main dictionary-core/src/main writing-core/src/main sync-domain/src/main fsrs-java/src/main update-core/src/main
```

The Java inventory command returns only
`core/src/main/java/dev/bee/kanjianki/core/FrequencyRetentionRanges.java`.
The route-shape search shows production `setContent`/`composeRoute` entry
points and one production `AndroidView` in `MainActivityStudyWritingPadCompose`;
there is no production `ComposeView`. The production test-bridge search returns
no matches.

GitHub Actions run `26332463828` passed for commit
`d7ab9ba9e686b8b5173137a75148bceddaff2cef`:

- App unit tests and coverage
- JVM tests and coverage
- Dictionary and asset tests
- App lint and androidTest compile
- Fast confidence gate

SonarQube workflow run `26332463839` passed for the same commit. The live
SonarCloud PR API reports quality gate `OK`, zero open issues, zero new code
smells, zero bugs, zero vulnerabilities, zero security hotspots, 100.0% reviewed
security hotspots, 84.5303867403315% new coverage, and 0.0% new duplication.

Local Gradle may fail inside the Codex sandbox with:

```text
Could not determine a usable wildcard IP for this machine.
```

When that happens, rerun the same Gradle command outside the sandbox via the approved escalation path. Do not treat that as a code failure.

## Review Agent State

Reviewer agents have been used after pushed commits. The latest actionable
review covered Sonar/code-smell and CI risks after the Robolectric coverage
slice. Commits `57a053dc` and `44df402e` close the reported lint/workflow and
androidTest assertion-style findings.

## Rewrite Exit Checklist

This checklist is now the completion audit baseline. The rewrite is done only
when every item in this checklist maps to the evidence above, the evidence note
is committed and pushed, and the final pushed branch remains clean with green
PR checks.

### 1. Finish the direct Compose app shell

- Home, Settings, Stats, Games, Study, Update, and secondary Home screens all enter through `setContent` / `composeRoute` paths.
- No production route renders its primary screen by manually assembling `LinearLayout`, `TextView`, or other legacy View trees.
- `ComposeView` wrappers are allowed only in test-only helper code. Production Android interop is limited to components that still must host a real platform View, currently the handwriting pad.
- `MainActivityBase`, `MainActivityHome`, `MainActivitySettings`, and `MainActivityStudy` are route coordinators only. They may build models and dispatch actions, but they must not contain screen layout code.

### 2. Remove production test bridges

- Any helper whose only purpose is to expose a screen fragment to instrumentation tests lives under `app/src/androidTest`, not `app/src/main`.
- Production `MainActivity*` classes do not keep wrapper methods solely because tests call them.
- Existing helper tests either assert the full Compose route, call model builders directly, or use androidTest-local bridge helpers.

### 3. Finish Settings migration

- The Settings route is one Compose screen model plus focused panel composables.
- Remaining settings behavior is migrated with parity for update/release, reference data, categories, automation reminder/autosync, study ladder, ladder thresholds, study-ahead, learning steps, retention, workload, study sort, Anki source validation, import filters, frequency range, and note type mapping.
- Toasts, dialogs, file pickers, and note-type selection stay behind small action interfaces or focused controller classes, not embedded in composable layout code.
- Scroll preservation and expanded-section state still behave as they do on this branch.

### 4. Finish Study migration

- Flashcard, writing, similar-kanji choice, meaning-kanji choice, done, empty, and focus-done states all render through Compose route surfaces.
- Remaining Study action bars, top bars, prompt/answer panels, flashcard cards, writing status, writing toolbar, writing actions, and choice result screens are model-driven composables.
- Gesture behavior, reveal-before-grading, above-the-fold controls, writing recognition, repair actions, similar-kanji routing, and "study more new cards" behavior keep current parity.
- Study stays split into route, model, action, scheduler, writing, and surface files. No new giant Study class replaces the old one.

### 5. Finish Home migration

- Home overview, metrics, action chrome, focus queue, recent mistakes, sync result screens, browse search, browse detail, examples, timeline, and empty states are Compose-owned.
- Search query preservation, back navigation, "study this kanji", recent mistakes navigation, sync CTA behavior, and browse-detail timeline behavior keep current parity.
- Home route files remain focused by feature; no single Home Compose file becomes the new dump for every surface.

### 6. Finish core Kotlin migration

- `app/src/main` remains Kotlin-only.
- `core/src/main/java/dev/bee/kanjianki/core` is reduced to zero Java files, or every remaining Java file has a written reason that it is intentionally left Java for compatibility.
- `update-core/src/main` remains Kotlin-only.
- Large core Java files are migrated in risk order: record/model containers, scheduler/review logic, copy/text helpers, import selection, game/planner logic, and analyzers.
- Java-to-Kotlin migrations preserve Java-callable APIs where tests or Android code still depend on method-style accessors.
- Current intentional Java exception: `FrequencyRetentionRanges.java` stays Java until this compatibility contract is no longer needed, because its nested `Rule` constructor must remain genuinely private to Java reflection. A Kotlin replacement attempt emitted a public synthetic `DefaultConstructorMarker` constructor and failed `FrequencyRetentionRangesTest#ruleConstructorStaysPrivateForJavaInterop`.
- Current hard Java inventory: only `core/src/main/java/dev/bee/kanjianki/core/FrequencyRetentionRanges.java` remains under main sources for app/core/domain/dictionary-core/writing-core/sync-domain/fsrs-java/update-core.

### 7. Finish fsrs-java and scheduler parity

- The in-repo `:fsrs-java` engine is still used through the Kotlin adapter path.
- `BridgeScheduler`, review transitions, ladder movement, Anki-exact learning/relearning behavior, promotion after FSRS schedules beyond the configured threshold, and demotion after configured consecutive failures are covered by focused tests.
- Reference fixtures and existing FSRS tests pass after any adapter or scheduler cleanup.

### 8. Finish data and repository boundaries

- `LocalStore`, `SettingsRepository`, historical sync storage, migration hooks, schema helpers, and activity-owned repository adapters have clear ownership and focused tests.
- UI code does not know SQL details, migration details, or raw storage keys except through explicit repository/model APIs.
- Settings storage fallback behavior, import provenance, sync run history, timeline events, study logs, and stats evidence remain backward compatible with existing installs.
- Room/DataStore/Hilt are deferred for a separate persistence PR; this rewrite
  is not complete by claiming those libraries are present.

### 9. Finish parity coverage

- Add or update tests before removing any bridge that currently has test coverage.
- Required passing gates:
  - `./gradlew :core:test :fsrs-java:test`
  - `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestJavaWithJavac`
  - `./gradlew :app:testDebugUnitTest`
  - `./gradlew ciFast`
- Manual or emulator smoke coverage must include Home, Settings, Study flashcard, Study writing, Study choice cards, browse/detail, sync/update, and Stats before merging. The current targeted route smokes cover Study flashcard, writing, similar-kanji choice, meaning-kanji choice, plus Home, Settings, Browse, Detail, Stats, Games, and Update.

### 10. Final merge criteria

- The branch is clean, pushed, and PR CI is green.
- The final PR summary maps every checklist item above to the commits that completed it.
- No stale continuation note says the branch is unfinished.
- No hidden TODO, disabled test, lint suppression, or review-agent finding remains unless it is explicitly documented as out of scope for this rewrite.
- Only after all items pass, merge the rewrite branch to `main`, push `main`, and verify `main` is clean and green.
