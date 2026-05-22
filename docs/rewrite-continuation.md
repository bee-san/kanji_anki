# Kani Rewrite Continuation

Last updated: 2026-05-22

## Branch and PR

- Rewrite branch: `codex-android-architecture-20260518`
- Pull request: `https://github.com/bee-san/kanji_anki/pull/11`
- Latest confirmed pushed commit before this note: `48601d6e Inline study flashcard route model`
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

## Migration Backlog

This is the working list for the remaining rewrite. Keep the branch as the active migration branch, keep product behavior as the parity contract, and cut one lane at a time.

### 1. Home shell

- Finish the Home browse-detail migration in `MainActivityHomeBrowseDetailCompose.kt`.
- Move the search header, query input, result heading, empty state, and result rows out of legacy Views.
- Keep `MainActivityHome.browseKanjiRow(...)` only if tests still need it as a bridge.
- Decide whether the remaining Home helper views stay as test-backed bridges or move into the Compose host:
  - `MainActivityHome.homeActionRow()`
  - `MainActivityHome.homeSectionHeader(...)`
  - `MainActivityHome.fullWidthHomeButton()`
- Continue the Home overview, metrics, recent mistakes, focus queue, and sync surfaces toward Compose-only rendering.

### 2. Settings shell

- Keep collapsing the remaining settings panels into the Compose files.
- Remaining settings lanes include:
  - update/release
  - reference data
  - category sections
  - automation hero/reminder/autosync
  - study ladder and threshold settings
  - learning steps
  - retention
  - workload
  - study sort
  - Anki source import filters, frequency range, and note type
- Keep `MainActivitySettings*` wrappers only while androidTest still depends on them.

### 3. Study shell

- Keep inlining the remaining study bridges into the study route and Compose files.
- Preserve test-backed wrappers until the helper tests are moved, especially `learningPanel(session)`.
- Continue the study write and flashcard lanes:
  - writing session card
  - writing toolbar
  - primary and fallback writing actions
  - writing pad
  - study prompt and answer panels
  - flashcard card and action bar
  - choice grid/session/result screens
  - done-actions and empty/focus-done states
- Keep the Study shell split into route, model, and surface pieces instead of a new god class.

### 4. Data and repository boundary

- Keep pushing view logic out of the data layer.
- Remaining boundary work includes:
  - `LocalStore`
  - `SettingsRepository`
  - `HistoricalSyncStore`
  - migration hooks and schema helpers
  - any remaining activity-owned repository adapters
- Keep `LocalStoreMigrations`, `LocalStoreSchema`, and `LocalStoreMigrationHooks` as the active migration boundary until the rewrite is done.

### 5. `fsrs_java` integration

- Keep the in-repo `:fsrs-java` engine covered by `core` and `fsrs-java` tests.
- Keep the adapter path through `BridgeScheduler` and the review transition code under coverage.
- Verify any remaining ladder or memory-handoff behavior against the reference fixtures before removing related shims.

### 6. Parity tests and review

- Add or extend Compose tests before removing a bridge.
- Keep androidTest helper coverage for any legacy surface that is still asserted.
- After every small batch:
  - run the narrow compile or focused test gate first
  - run `./gradlew ciFast` before push
  - commit
  - push
  - dispatch a reviewer agent on the commit
- Use Semble first when picking the next slice or checking for duplicate work.

### 7. Final parity audit before merge

Do not call the rewrite complete until a real audit passes for:

- AnkiDroid sync and import defaults
- study ladder behavior and learning/relearning semantics
- FSRS scheduling and reference fixtures
- writing recognition, guided handwriting, repair actions, and similar-kanji flows
- Stats, settings, update/release behavior, and database migrations
- app identity, packaging, and the release path
