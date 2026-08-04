# Beta Update Channel Implementation Plan

> **For Hermes:** Implement this plan task-by-task with strict RED-GREEN-REFACTOR cycles.

**Goal:** Add an opt-in Kani beta update channel that downloads GitHub prereleases while stable users continue receiving only stable releases, and make every successful `main` push publish a prerelease beta build.

**Architecture:** Persist a new `beta_updates_enabled` setting (default `false`). Centralize GitHub release endpoint and response-shape selection in `update-core`: stable uses GitHub's `/releases/latest` object endpoint, while beta selects the newest prerelease from the release list. Automatic successful-main releases use an explicit `vMAJOR.MINOR.PATCH-beta` tag and matching APK `versionName`; deliberate stable releases use the next unused numeric core without the suffix.

**Tech Stack:** Kotlin/JVM, Android/Robolectric, Jetpack Compose, SQLite settings, Python `unittest`, GitHub Actions, GitHub CLI.

---

### Task 1: Specify update-channel selection

**Objective:** Lock stable-vs-beta GitHub API behavior behind a pure, unit-tested policy.

**Files:**
- Create: `update-core/src/test/kotlin/dev/bee/kanjianki/updatecore/GitHubReleaseChannelPolicyTest.kt`
- Create: `update-core/src/main/kotlin/dev/bee/kanjianki/updatecore/GitHubReleaseChannelPolicy.kt`
- Modify: `update-core/src/main/kotlin/dev/bee/kanjianki/updatecore/GitHubReleaseMetadataParser.kt`

**Steps:**
1. Write tests proving stable uses `/releases/latest`, beta uses `/releases?per_page=1`, stable parses an object, and beta parses the first object in a release array.
2. Run `./gradlew :update-core:test --tests '*GitHubReleaseChannelPolicyTest'` and confirm failure because the policy is absent.
3. Implement the smallest policy/parser API that passes.
4. Re-run the focused test and then `./gradlew :update-core:test`.

### Task 2: Persist beta opt-in safely

**Objective:** Store beta opt-in with a default of `false` so existing users remain stable.

**Files:**
- Modify: `app/src/test/kotlin/dev/bee/kanjianki/data/LocalStoreLearningStepSettingsTest.kt` or add a focused store test under the same package
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/data/LocalStoreBase.kt`
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/data/LocalStoreStudySettings.kt`
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/data/LocalStoreStudy.kt`

**Steps:**
1. Write a Robolectric test proving the setting defaults off and survives save/reopen.
2. Run the focused app test and confirm failure.
3. Add the key plus read/write methods without a schema migration (settings are key/value rows).
4. Re-run the focused test.

### Task 3: Wire updater to the selected channel

**Objective:** Make update checks query and parse the configured stable or beta release feed.

**Files:**
- Modify: `app/src/test/java/dev/bee/kanjianki/update/GitHubUpdaterTest.kt`
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/update/GitHubUpdater.kt`

**Steps:**
1. Add tests with a recording `UpdateClient`: default setting requests `/releases/latest`; beta setting requests `/releases?per_page=1` and accepts the array response.
2. Run the focused tests and confirm they fail on the current hard-coded stable endpoint.
3. Read `betaUpdatesEnabled()` from `LocalStore`, ask `GitHubReleaseChannelPolicy` for the endpoint and metadata, and keep all checksum/signature/version validation unchanged.
4. Re-run updater tests.

### Task 4: Expose the opt-in setting in Update settings

**Objective:** Give users an explicit beta-build toggle with clear copy and persisted behavior.

**Files:**
- Modify: `core/src/test/kotlin/dev/bee/kanjianki/core/SettingsAutomationTextCopyTest.kt`
- Modify: `core/src/main/kotlin/dev/bee/kanjianki/core/SettingsAutomationTextCopy.kt`
- Modify: `core/src/main/kotlin/dev/bee/kanjianki/core/SettingsTextCopy.kt`
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettingsUpdatePanelModel.kt`
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettingsUpdatePanelCompose.kt`
- Modify: `app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettingsUpdatePageCompose.kt`
- Modify affected model-construction tests such as `app/src/test/kotlin/dev/bee/kanjianki/ComposeScreenModelsTest.kt`

**Steps:**
1. Add copy/model tests for opt-in wording and enabled state.
2. Run focused tests and confirm failure.
3. Add a switch row/button showing `Beta builds: On/Off`, explain that betas may be unstable, and persist/re-render on toggle.
4. Re-run focused core and app tests.

### Task 5: Publish automatic main builds as prereleases

**Objective:** Keep automatic build availability for testers without changing GitHub's stable `latest` release.

**Files:**
- Modify: `tools/test_release_workflows.py`
- Modify: `.github/workflows/android-release.yml`
- Modify: `AGENTS.md` release documentation if its stable-release description becomes inaccurate

**Steps:**
1. Add workflow tests proving `workflow_run` generates a `-beta` tag, while the tag suffix controls `--prerelease` for every publication path.
2. Run `python3 -m unittest tools.test_release_workflows.AndroidReleaseWorkflowTest` and confirm failure.
3. Compute the next explicit `vMAJOR.MINOR.PATCH-beta` tag for automatic `workflow_run` publication, derive `--prerelease` from that tag, and retain existing signed-APK verification.
4. Update release-path documentation.
5. Re-run the workflow tests.

### Task 6: Verify and deliver on main

**Objective:** Prove behavior, build integrity, and publish the implementation to `origin/main`.

**Steps:**
1. Run focused suites: `./gradlew :update-core:test :app:testDebugUnitTest` and `python3 -m unittest tools.test_release_workflows`.
2. Run the repository confidence gate: `ANDROID_HOME=/tmp/android-sdk ANDROID_SDK_ROOT=/tmp/android-sdk ./gradlew ciFast --parallel -Dorg.gradle.parallel=true`.
3. Review `git diff --check`, `git diff`, and workflow security invariants.
4. Commit with a conventional commit message.
5. Push directly to `origin/main` as requested.
6. Verify local `main` equals `origin/main`, then inspect the triggered GitHub Actions runs and confirm the release run is queued/running (or report any external CI failure precisely).

## Risks and tradeoffs

- GitHub's stable `/releases/latest` deliberately excludes prereleases; this is what protects stable users.
- GitHub's releases list includes prereleases and drafts; unauthenticated app requests cannot see drafts, so the first visible entry is the newest published release.
- Every automatic beta consumes the next numeric patch and uses SemVer's explicit `-beta` suffix (for example `v0.5.6-beta`). A deliberate stable release uses a later numeric core so Android versionCode ordering stays monotonic.
- The beta toggle defaults off and does not weaken APK checksum, package identity, or signing-certificate checks.
- One-time migration: the legacy `v0.5.11` updater accepts numeric-only tags, so the first `v0.5.12-beta` APK must be installed manually. Its suffix-aware updater restores automatic beta updates for every later build.
