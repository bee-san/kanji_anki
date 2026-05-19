# Kani Rewrite Continuation

Last updated: 2026-05-19

## Branch and PR

- Rewrite branch: `codex-android-architecture-20260518`
- Pull request: `https://github.com/bee-san/kanji_anki/pull/11`
- Latest confirmed pushed commit before this note: `2f1cf8e6 Tighten study done-actions test coverage`
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

## Next Work

Continue with small, behavior-preserving Compose migrations before any broad cleanup. Good next slices:

1. Use Semble before choosing a slice.
2. Finish the Home browse screen migration: move the search header, input, result heading, empty state, and result rows out of legacy Views into `MainActivityHomeBrowseDetailCompose.kt`.
3. Keep `MainActivityHome.browseKanjiRow(...)` as a thin wrapper if tests still call it.
4. Add or extend Compose tests in `MainActivityHomeBrowseDetailComposeTest.kt` for suspended rows, empty rows, and row click callbacks.
5. After each slice, run the focused local gate, commit, push, dispatch a reviewer agent, and watch GitHub CI.

Avoid starting the final merge until a parity audit has passed for:

- AnkiDroid sync and import defaults
- Study ladder behavior and learning/relearning semantics
- FSRS scheduling and reference fixtures
- Writing recognition, guided handwriting, repair actions, and similar-kanji flows
- Stats, settings, update/release behavior, and database migrations
- Existing app identity and release path

## Standard Loop

Use this loop for the next session:

```bash
git status --short --branch
semble search "next duplicated study policy or activity helper" .
./gradlew --no-daemon :app:compileDebugKotlin :app:compileDebugAndroidTestKotlin :app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac :app:testDebugUnitTest :app:lintDebug
git diff --check
git add <scoped files>
git commit -m "<focused message>"
git push
gh run list --repo bee-san/kanji_anki --branch codex-android-architecture-20260518 --limit 3
```

Use a broader `./gradlew ciFast` before calling a larger section done.
