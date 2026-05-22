# Kani Rewrite Continuation

Last updated: 2026-05-22

## Branch and PR

- Rewrite branch: `codex-android-architecture-20260518`
- Pull request: `https://github.com/bee-san/kanji_anki/pull/11`
- Latest confirmed pushed commit before this note: `68bc7e55 Move study learning panel into androidTest`
- Latest confirmed PR state: draft, mergeable, head SHA `2f1cf8e643b23d0bd055a6a440f9f9e82a0388e8`

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

The rewrite branch is intentionally being moved in small, reviewable commits. The app still keeps the original Android package identity and is not ready to merge to `main` yet.

Completed foundations include:

- `fsrs-java` is integrated into the Gradle build with tests and coverage verification.
- FSRS is wired through the domain adapter path used by review transitions.
- CI is split into fast deterministic jobs and currently finishes well under the 15-minute ceiling.
- Several study/writing behaviors have been moved from activity code into core or writing-core policies.
- Kotlin and Compose are now wired into the Android app.
- Real Compose surfaces now include Home header/actions/CTA/metrics, Home recent mistakes and browse-detail timeline, Stats, Settings update/reference/category/automation hero areas, Games question card, Study top bar, flashcard actions, and done actions.
- Recent pushed slices include study flashcard actions, study done actions, and follow-up done-action test coverage.
- Study helper bridges are being pushed into `androidTest`; `learningPanel(session)` no longer lives in `MainActivityStudy`.

## Verification Baseline

The most recent focused local gate passed with:

```bash
./gradlew --no-daemon :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac :app:testDebugUnitTest :app:lintDebug
```

Earlier in this branch, GitHub Actions run `26056399662` passed:

- App unit tests and coverage
- JVM tests and coverage
- Dictionary and asset tests
- App lint and androidTest compile
- Fast confidence gate

Local Gradle may fail inside the Codex sandbox with:

```text
Could not determine a usable wildcard IP for this machine.
```

When that happens, rerun the same Gradle command outside the sandbox via the approved escalation path. Do not treat that as a code failure.

## Review Agent State

Reviewer agents have been used after pushed commits. The latest actionable review covered the study done-actions Compose slice and found two coverage gaps:

```bash
git show --stat --oneline 2f1cf8e6
git show --check 2f1cf8e6
```

Commit `2f1cf8e6` closes those gaps by clicking `Study more new cards` in the helper test and covering both populated and empty extra-new-card states in the Compose test.

## Rewrite Exit Checklist

This is the definitive remaining work. The rewrite is done only when every item in this checklist is complete, reviewed, committed, pushed, and the final verification gates pass. Do not add unrelated Java extraction or helper cleanup unless it directly completes one of these items.

### 1. Finish the direct Compose app shell

- Home, Settings, Stats, Games, Study, Update, and secondary Home screens all enter through `setContent` / `composeRoute` paths.
- No production route renders its primary screen by manually assembling `LinearLayout`, `TextView`, or other legacy View trees.
- `ComposeView` wrappers are allowed only inside Android interop components that still must host a real platform View, such as the handwriting pad or test-only helper code.
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
- Large core Java files are migrated in risk order: record/model containers, scheduler/review logic, copy/text helpers, import selection, game/planner logic, and analyzers.
- Java-to-Kotlin migrations preserve Java-callable APIs where tests or Android code still depend on method-style accessors.

### 7. Finish fsrs-java and scheduler parity

- The in-repo `:fsrs-java` engine is still used through the Kotlin adapter path.
- `BridgeScheduler`, review transitions, ladder movement, Anki-exact learning/relearning behavior, promotion after FSRS schedules beyond the configured threshold, and demotion after configured consecutive failures are covered by focused tests.
- Reference fixtures and existing FSRS tests pass after any adapter or scheduler cleanup.

### 8. Finish data and repository boundaries

- `LocalStore`, `SettingsRepository`, historical sync storage, migration hooks, schema helpers, and activity-owned repository adapters have clear ownership and focused tests.
- UI code does not know SQL details, migration details, or raw storage keys except through explicit repository/model APIs.
- Settings storage fallback behavior, import provenance, sync run history, timeline events, study logs, and stats evidence remain backward compatible with existing installs.

### 9. Finish parity coverage

- Add or update tests before removing any bridge that currently has test coverage.
- Required passing gates:
  - `./gradlew :core:test :fsrs-java:test`
  - `./gradlew :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestJavaWithJavac`
  - `./gradlew :app:testDebugUnitTest`
  - `./gradlew ciFast`
- Manual or emulator smoke coverage must include Home, Settings, Study flashcard, Study writing, Study choice cards, browse/detail, sync/update, and Stats before merging.

### 10. Final merge criteria

- The branch is clean, pushed, and PR CI is green.
- The final PR summary maps every checklist item above to the commits that completed it.
- No stale continuation note says the branch is unfinished.
- No hidden TODO, disabled test, lint suppression, or review-agent finding remains unless it is explicitly documented as out of scope for this rewrite.
- Only after all items pass, merge the rewrite branch to `main`, push `main`, and verify `main` is clean and green.
