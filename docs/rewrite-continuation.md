# Kani Rewrite Continuation

Last updated: 2026-05-18

## Branch and PR

- Rewrite branch: `codex-android-architecture-20260518`
- Pull request: `https://github.com/bee-san/kanji_anki/pull/11`
- Latest confirmed pushed commit before this note: `e5ccd72c Extract study collection lookup`
- Latest confirmed GitHub Actions run: `26056399662`, passed in about 2.5 minutes

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

## Current Rewrite State

The rewrite branch is intentionally being moved in small, reviewable commits. The app still keeps the original Android package identity and is not ready to merge to `main` yet.

Completed foundations include:

- `fsrs-java` is integrated into the Gradle build with tests and coverage verification.
- FSRS is wired through the domain adapter path used by review transitions.
- CI is split into fast deterministic jobs and currently finishes well under the 15-minute ceiling.
- Several study/writing behaviors have been moved from activity code into core or writing-core policies.
- Recent extracted slices include study answer details, practice message copy, captured writing attempts, writing model availability, writing action presentation, study session routing, and shared study collection lookup.

## Verification Baseline

The most recent focused local gate passed with:

```bash
env ANDROID_HOME=/home/bee/Documents/src/github/thaiwrite/.android-sdk ANDROID_SDK_ROOT=/home/bee/Documents/src/github/thaiwrite/.android-sdk GRADLE_USER_HOME=/tmp/gradle-kanji-review ./gradlew :core:test :core:jacocoTestReport :core:jacocoTestCoverageVerification :app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
```

The most recent pushed commit triggered GitHub Actions run `26056399662`, which passed:

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

Reviewer agents have been used after each pushed commit. The last launched reviewer was for commit `e5ccd72c`; it reported no behavioral parity or compile-risk findings.

```bash
git show --stat --oneline e5ccd72c
git show --check e5ccd72c
```

The reviewer confirmed:

- `StudyCollectionLookup` preserves the previous "first matching kanji" behavior.
- The added null tolerance appears safer rather than regressive.
- `./gradlew :core:test --rerun-tasks` passed.
- `git diff --check e5ccd72c^ e5ccd72c` passed.

The reviewer could not run app compilation because its environment used `/opt/android-sdk`, which lacked accepted Android 36 licenses. This branch has already passed app Java compile and androidTest Java compile locally with the configured SDK, and GitHub CI passed the app compile/lint jobs on run `26056399662`.

## Next Work

Continue with small, behavior-preserving extractions before any broad cleanup. Good next slices:

1. Use Semble to find the next duplicated pure policy or copy/rendering helper.
2. Prefer extractions out of `MainActivityStudy` that can be tested in `core` or `writing-core`.
3. Keep app methods as thin delegates when androidTest or package-visible tests still rely on them.
4. After each slice, run the focused local gate, commit, push, dispatch a reviewer agent, and watch GitHub CI.

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
env ANDROID_HOME=/home/bee/Documents/src/github/thaiwrite/.android-sdk ANDROID_SDK_ROOT=/home/bee/Documents/src/github/thaiwrite/.android-sdk GRADLE_USER_HOME=/tmp/gradle-kanji-review ./gradlew :core:test :app:testDebugUnitTest :app:compileDebugJavaWithJavac :app:compileDebugAndroidTestJavaWithJavac
git diff --check
git add <scoped files>
git commit -m "<focused message>"
git push
gh run list --repo bee-san/kanji_anki --branch codex-android-architecture-20260518 --limit 3
```

Use a broader `./gradlew ciFast` before calling a larger section done.
