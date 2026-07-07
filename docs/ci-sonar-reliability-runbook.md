# CI and Sonar reliability runbook

This runbook is the first stop when Android CI, SonarQube, or CodeQL looks flaky or blocked. It maps the local Gradle gates to the GitHub Actions checks and gives a repeatable triage loop before retrying, merging, or cutting a release.

## Gate map

| Surface | Local command | GitHub check / workflow | Notes |
| --- | --- | --- | --- |
| Deterministic fast gate | `./gradlew ciFast` | `Fast confidence gate` in `.github/workflows/android-ci.yml` | Split in CI into JVM module tests, app unit tests, app lint/androidTest compile, and Python asset tests. |
| Sonar deterministic inputs | `./gradlew ciQuality` | `Build coverage and analyze` in `.github/workflows/sonarqube.yml` | Builds the bytecode and deterministic coverage inputs that Sonar consumes. Advisory on `main`; never blocks releases. This is not a replacement for Android CI. |
| Release confidence | `./gradlew ciRelease` | `.github/workflows/android-release.yml` | Auto path: a successful `Android CI` main-push run triggers the release, which tags, builds, verifies, and publishes with no further gating. Manual tag/dispatch runs the unit-test surface inline first. |
| CodeQL extraction | Forced clean compile in `.github/workflows/codeql.yml` | `Analyze Java/Kotlin` | Advisory security scan on `main`; never blocks releases. Keep the clean, no-build-cache compile after CodeQL init so the extractor sees real compiler work. |
| Live AnkiDroid fixture | workflow-dispatch/nightly Android instrumented workflow | `AnkiDroid provider fixture` | Nightly/dispatch only; deliberately removed from the release path because emulator/provider readiness flakes were the top cause of blocked releases. The local copied-user-collection gate remains the requirement for provider/sync release-risk changes. |

## Triage loop

1. Identify the exact failing check and commit.

   ```sh
   gh run list --repo bee-san/kanji_anki --limit 10
   gh run view RUN_ID --repo bee-san/kanji_anki --log-failed
   ```

   If the GitHub CLI is unavailable or not logged in, use the public Actions page and compare the run's head SHA with the branch or PR head before acting.

2. Classify the failure before retrying.

   - Infrastructure / service: runner setup failed, dependency download timed out, Gradle daemon disappeared, or the run was cancelled by a newer push. Re-run only the stale failed run or push a no-op-free fix commit when the branch head changed.
   - Deterministic project failure: test assertion, lint issue, compile error, Sonar issue, CodeQL extraction error, or missing coverage artifact. Fix locally first.
   - Path-filter skip: expected for docs-only or unrelated files. Do not treat a skipped Sonar/Android run as a substitute for a required check when app, Gradle, workflow, or tool files changed.

3. Reproduce the matching local gate from the repository root.

   ```sh
   # Android SDK location on developer Macs may differ; keep both variables aligned.
   ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk ./gradlew ciFast
   ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk ./gradlew ciQuality
   ```

   On machines where `/tmp/android-sdk` is absent, use the installed SDK path, for example:

   ```sh
   ANDROID_HOME="$HOME/Library/Android/sdk" ANDROID_SDK_ROOT="$HOME/Library/Android/sdk" ./gradlew ciFast
   ```

4. Fix or document the smallest reliable scope.

   - Prefer deterministic JVM/unit/lint fixes before adding connected emulator work to the default path.
   - Keep Sonar focused on correctness, maintainability, bugs, vulnerabilities, and security hotspots. Treat 100% coverage as an optimization target, not a reason to slow every PR.
   - For Sonar test assertions, avoid direct `assertFalse(value.equals(...))`; assign to a boolean first or use the appropriate same-type assertion.

5. Push and watch when workflows changed.

   Workflow edits are service-integration changes. After pushing them, watch the first Actions run to a terminal state instead of relying on local Gradle success alone.

   ```sh
   gh run list --repo bee-san/kanji_anki --branch BRANCH --limit 5
   gh run watch RUN_ID --repo bee-san/kanji_anki --exit-status
   ```

## Release path invariants

The release workflow is deliberately self-contained so releases are fast and
cannot be blocked by flaky or external gates:

- The auto path triggers only off a successful `Android CI` run on a `main`
  push (`workflow_run`), so the deterministic test surface is already green
  for the exact release commit. The release then tags, builds the signed APK,
  verifies signature/identity, and publishes. Target wall time is under ten
  minutes after CI.
- Manual tag pushes and `workflow_dispatch` releases run the deterministic
  unit-test surface inline in the validate job before assembling, because
  those events can target commits no CI run has vouched for.
- The release workflow never polls SonarQube or CodeQL check runs and never
  runs emulator jobs. `tools/test_release_workflows.py` fails if check-run
  polling, `REQUIRED_CHECKS`, or the AnkiDroid fixture reappear in
  `android-release.yml`.
- Live AnkiDroid provider coverage lives in the nightly/dispatch
  `android-instrumented.yml` workflow; a red nightly means investigate before
  the next provider/sync release, enforced by the stricter local
  copied-user-collection gate rather than by the release pipeline.

## Common reliability pitfalls

- Multiple quick pushes to `main` or the same PR branch intentionally cancel older Android CI, SonarQube, and CodeQL runs because the workflows use concurrency groups. Verify the newest run for the SHA you intend to ship.
- CodeQL for this Android Gradle project depends on a forced clean compile after `github/codeql-action/init`. Do not replace it with an up-to-date Gradle compile.
- Sonar workflow-dispatch on a branch tries to discover the matching PR through `gh pr view "$GITHUB_REF_NAME"`. If no PR exists, it falls back to branch analysis arguments.
- Android CI's `Fast confidence gate` is an aggregate check. Inspect the split matrix jobs and uploaded diagnostic artifacts to find the actual failing task.
- The deterministic AnkiDroid fixture can pass with the small sanitized Kiku collection while the local real-collection gate still finds provider/sync scale issues. Use the stricter local gate before release-risk provider/sync changes.
- For remote Android screenshots and the Ralph UI loop, see `docs/ralph-ui-loop/runbook.md`.
