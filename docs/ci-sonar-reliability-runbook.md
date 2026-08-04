# CI and Sonar reliability runbook

This runbook is the first stop when Android CI, SonarQube, or CodeQL looks flaky or blocked. It maps the local Gradle gates to the GitHub Actions checks and gives a repeatable triage loop before retrying, merging, or cutting a release.

## Gate map

| Surface | Local command | GitHub check / workflow | Notes |
| --- | --- | --- | --- |
| Deterministic fast gate | `./gradlew ciFast` | `Fast confidence gate` in `.github/workflows/android-ci.yml` | Split in CI into JVM module tests, app unit tests, app lint/androidTest compile, and Python asset tests. |
| Connected device smoke | targeted `connectedDebugAndroidTest` annotation run | `.github/workflows/android-device-smoke.yml` | PR/manual-only. Runs a compact API 26/35 matrix and adds an API 35 risk suite for product/provider/scheduler/database/UI changes. Documentation-only changes explicitly skip emulators. |
| Sonar deterministic inputs | `./gradlew ciQuality` | `Build coverage and analyze` in `.github/workflows/sonarqube.yml` | Builds the complete bytecode and deterministic coverage set on every analysis. `sonarPreflight` fails closed if any expected class/report input is absent. Advisory on `main`; never blocks releases. |
| Release confidence | `./gradlew ciRelease` | `.github/workflows/android-release.yml` | Auto path: a successful `Android CI` main-push run creates a legacy-compatible numeric `vMAJOR.MINOR.PATCH` tag/APK, verifies it, and publishes it as a beta prerelease with no further gating. Manual tag/dispatch publishes deliberate releases after running the unit-test surface inline. |
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
   - If Sonar reports a missing deterministic input, run `ciQuality` first. To validate the fail-closed check itself, override the app path with `-PsonarAppMainBinaries=/definitely/missing` and run `sonarPreflight`.

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
  verifies signature/identity, and publishes a GitHub prerelease for opt-in beta
  users. Automatic beta tags, APK `versionName`s, and asset filenames use the
  numeric `vMAJOR.MINOR.PATCH` form so legacy Kani 0.5.10/0.5.11 updaters can
  discover them. Because GitHub prereleases are excluded from the `latest`
  endpoint, stable users are unaffected. The parser also accepts explicit
  `-beta` tags. Target wall time is under ten minutes after CI.
- Manual tag pushes and `workflow_dispatch` releases run the deterministic
  unit-test surface inline in the validate job before assembling, because
  those events can target commits no CI run has vouched for.
- Compatibility note (August 2026): Kani 0.5.10/0.5.11 cannot parse suffixed
  beta tags, so automatic beta releases retain numeric tags and use GitHub's
  prerelease flag to identify the channel. This allows those versions to
  self-update without a one-time manual installation.
- The release workflow never polls SonarQube or CodeQL check runs and never
  runs emulator jobs. `tools/test_release_workflows.py` fails if check-run
  polling, `REQUIRED_CHECKS`, or the AnkiDroid fixture reappear in
  `android-release.yml`.
- Live AnkiDroid provider coverage lives in the nightly/dispatch
  `android-instrumented.yml` workflow; a red nightly means investigate before
  the next provider/sync release, enforced by the stricter local
  copied-user-collection gate rather than by the release pipeline.
- Pull-request device smoke is also independent of release publication. It
  exercises fake-provider and UI contracts without moving the real-provider
  fixture or any emulator into `android-release.yml`.

## Supply-chain and version integrity

- Every remote workflow action is pinned to a full commit SHA. Renovate may
  update the digest and its human-readable version comment together.
- Every Gradle workflow job validates the wrapper before the first Gradle
  invocation, and `gradle-wrapper.properties` pins the distribution SHA-256.
- Android SDK package installation is shared by
  `.github/actions/setup-android-sdk`; keep inline `sdkmanager` copies out of
  workflow files.
- Release tags and Android version codes are validated by
  `ci/scripts/kani_version.py`. It accepts canonical stable tags and the exact
  `-beta` prerelease suffix; a patch component above 999 fails with an
  instruction to bump the minor version, preventing versionCode collisions.
  A stable release must use a numeric core newer than the preceding beta because
  `vX.Y.Z` and `vX.Y.Z-beta` intentionally map to the same Android versionCode.
- Release packaging validates signing from the selected Gradle task graph, so
  aggregate entry points such as `ciRelease` cannot silently emit an unsigned
  artifact.

## Common reliability pitfalls

- Multiple quick pushes to `main` or the same PR branch intentionally cancel older Android CI, SonarQube, and CodeQL runs because the workflows use concurrency groups. Verify the newest run for the SHA you intend to ship.
- CodeQL for this Android Gradle project depends on a forced clean compile after `github/codeql-action/init`. Do not replace it with an up-to-date Gradle compile.
- CodeQL explicitly requests Kotlin compiler tasks as well as the app Java task. Keep `clean`, `--no-daemon`, `--no-build-cache`, `--rerun-tasks`, and the explicit Kotlin targets together.
- Sonar workflow-dispatch on a branch tries to discover the matching PR through `gh pr view "$GITHUB_REF_NAME"`. If no PR exists, it falls back to branch analysis arguments.
- Android CI's `Fast confidence gate` is an aggregate check. Inspect the split matrix jobs and uploaded diagnostic artifacts to find the actual failing task.
- The deterministic AnkiDroid fixture can pass with the small sanitized Kiku collection while the local real-collection gate still finds provider/sync scale issues. Use the stricter local gate before release-risk provider/sync changes.
- For remote Android screenshots and the Ralph UI loop, see `docs/ralph-ui-loop/runbook.md`.
