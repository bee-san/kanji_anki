#!/usr/bin/env python3

from __future__ import annotations

import ast
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ROOT_BUILD_GRADLE = ROOT / "build.gradle.kts"
WORKFLOW_DIRECTORY = ROOT / ".github/workflows"
GRADLE_WRAPPER_PROPERTIES = ROOT / "gradle/wrapper/gradle-wrapper.properties"
ANDROID_RELEASE_WORKFLOW = ROOT / ".github/workflows/android-release.yml"
ANDROID_INSTRUMENTED_WORKFLOW = ROOT / ".github/workflows/android-instrumented.yml"
ANKIDROID_CHECKSUMS = ROOT / "ci/fixtures/ankidroid/ankidroid-2.24.0.sha256"
ANDROID_DEVICE_SMOKE_WORKFLOW = ROOT / ".github/workflows/android-device-smoke.yml"
DEVICE_RISK_SCRIPT = ROOT / "ci/scripts/run_device_risk_suite.sh"
SONAR_WORKFLOW = ROOT / ".github/workflows/sonarqube.yml"
CODEQL_WORKFLOW = ROOT / ".github/workflows/codeql.yml"
DEBUG_MANIFEST = ROOT / "provider-ankidroid/src/debug/AndroidManifest.xml"
FAKE_PROVIDER_DEBUG_SOURCE = (
    ROOT
    / "provider-ankidroid/src/debug/kotlin/dev/bee/kanjianki/anki/"
    "FakeAnkiDroidProvider.kt"
)
FAKE_PROVIDER_ANDROID_TEST_SOURCE = (
    ROOT
    / "provider-ankidroid/src/androidTest/kotlin/dev/bee/kanjianki/anki/"
    "FakeAnkiDroidProvider.kt"
)


def _yaml_mapping_block(document: str, key: str, indentation: int) -> str:
    """Return one mapping's indented body without requiring a YAML package."""
    header = f"{' ' * indentation}{key}:"
    lines = document.splitlines()
    matches = [index for index, line in enumerate(lines) if line == header]
    if len(matches) != 1:
        raise AssertionError(f"expected one {header!r} mapping, found {len(matches)}")

    body = []
    for line in lines[matches[0] + 1 :]:
        stripped = line.lstrip()
        if stripped and not stripped.startswith("#"):
            current_indentation = len(line) - len(stripped)
            if current_indentation <= indentation:
                break
        body.append(line)
    return "\n".join(body)


def _yaml_string_list(document: str, key: str, indentation: int) -> list[str]:
    """Read the simple quoted/unquoted scalar lists used by workflow filters."""
    body = _yaml_mapping_block(document, key, indentation)
    item_prefix = f"{' ' * (indentation + 2)}- "
    values = []
    for line in body.splitlines():
        if not line.startswith(item_prefix):
            continue
        scalar = line[len(item_prefix) :].strip()
        if scalar.startswith(("'", '"')):
            value = ast.literal_eval(scalar)
        else:
            value = scalar
        if not isinstance(value, str):
            raise AssertionError(f"expected string list item under {key!r}: {scalar!r}")
        values.append(value)
    if not values:
        raise AssertionError(f"expected at least one list item under {key!r}")
    return values


class FastCiTaskWiringTest(unittest.TestCase):
    PYTHON_SUITES = {
        "testDictionaryAssets": "tools",
        "testRalphScripts": "scripts/tests",
        "testCiScripts": "ci/tests",
    }

    def setUp(self) -> None:
        self.gradle = ROOT_BUILD_GRADLE.read_text(encoding="utf-8")

    def test_ci_fast_runs_every_deterministic_python_test_suite(self) -> None:
        fast_tasks = self.gradle.split("val fastCiTasks = listOf(", maxsplit=1)[1].split(")", maxsplit=1)[0]
        for task, directory in self.PYTHON_SUITES.items():
            with self.subTest(task=task, directory=directory):
                task_marker = f'tasks.register<Exec>("{task}") {{'
                self.assertIn(task_marker, self.gradle)
                task_block = self.gradle.split(task_marker, maxsplit=1)[1].split("\n}", maxsplit=1)[0]
                self.assertIn(
                    f'commandLine("python3", "-m", "unittest", "discover", "-s", "{directory}", "-p", "test_*.py")',
                    task_block,
                )
                self.assertIn(f'"{task}"', fast_tasks)

    def test_android_ci_asset_job_matches_local_python_test_surface(self) -> None:
        # CI's Fast confidence gate must run the same Python suites as local ciFast:
        # tools/, scripts/tests/, AND ci/tests/. Pin both sides so neither gate
        # can silently drop a suite while the other remains green.
        ci = (ROOT / ".github/workflows/android-ci.yml").read_text(encoding="utf-8")
        asset_test_job = _yaml_mapping_block(ci, "asset-tests", 2)
        for directory in self.PYTHON_SUITES.values():
            with self.subTest(directory=directory):
                self.assertIn(
                    f"run: python3 -m unittest discover -s {directory} -p 'test_*.py'",
                    asset_test_job,
                )

    def test_android_convention_fixture_runs_locally_and_in_android_ci(self) -> None:
        fast_tasks = self.gradle.split("val fastCiTasks = listOf(", maxsplit=1)[1].split(
            ")",
            maxsplit=1,
        )[0]
        self.assertIn('tasks.register("testBuildLogic")', self.gradle)
        self.assertIn('"testBuildLogic"', fast_tasks)

        ci = (ROOT / ".github/workflows/android-ci.yml").read_text(encoding="utf-8")
        self.assertIn("label: Android convention plugin tests", ci)
        self.assertIn("tasks: 'testBuildLogic'", ci)


class WorkflowSupplyChainTest(unittest.TestCase):
    REMOTE_ACTION = re.compile(r"^\s*uses:\s*[\"']?([^\s\"'#]+)@([^\s\"'#]+)")
    IMMUTABLE_SHA = re.compile(r"^[0-9a-f]{40}$")

    def test_remote_actions_are_pinned_to_full_commit_shas(self) -> None:
        automation_paths = sorted(
            (
                *WORKFLOW_DIRECTORY.glob("*.y*ml"),
                *(ROOT / ".github/actions").rglob("action.y*ml"),
            ),
        )
        for workflow_path in automation_paths:
            for line_number, line in enumerate(
                workflow_path.read_text(encoding="utf-8").splitlines(),
                start=1,
            ):
                match = self.REMOTE_ACTION.match(line)
                if match is None or match.group(1).startswith("./"):
                    continue
                with self.subTest(workflow=workflow_path.name, line=line_number):
                    self.assertRegex(
                        match.group(2),
                        self.IMMUTABLE_SHA,
                        f"remote action must use an immutable 40-character SHA: {line.strip()}",
                    )

    def test_gradle_distribution_has_a_sha256_checksum(self) -> None:
        properties = GRADLE_WRAPPER_PROPERTIES.read_text(encoding="utf-8")
        checksum = re.search(r"^distributionSha256Sum=([0-9a-f]{64})$", properties, re.MULTILINE)
        self.assertIsNotNone(checksum, "Gradle wrapper distribution must have a SHA-256 checksum")

    def test_every_gradle_workflow_job_validates_wrapper_first(self) -> None:
        for workflow_path in sorted(WORKFLOW_DIRECTORY.glob("*.y*ml")):
            lines = workflow_path.read_text(encoding="utf-8").splitlines()
            job_starts = [
                index
                for index, line in enumerate(lines)
                if re.fullmatch(r"  [A-Za-z0-9_-]+:", line)
            ]
            job_starts.append(len(lines))
            for index, start in enumerate(job_starts[:-1]):
                end = job_starts[index + 1]
                block = lines[start:end]
                gradle_indices = [
                    index
                    for index, line in enumerate(block)
                    if (
                        (
                            "./gradlew" in line
                            and "gradle_command:" not in line
                        )
                        or "${{ matrix.gradle_command }}" in line
                    )
                    and not line.lstrip().startswith("#")
                ]
                if not gradle_indices:
                    continue
                wrapper_indices = [
                    index
                    for index, line in enumerate(block)
                    if "gradle/actions/wrapper-validation@" in line
                ]
                with self.subTest(workflow=workflow_path.name, job=lines[start].strip()):
                    self.assertTrue(wrapper_indices, "Gradle workflow job must validate the wrapper")
                    self.assertLess(
                        min(wrapper_indices),
                        min(gradle_indices),
                        "Wrapper validation must run before the first Gradle invocation",
                    )

    def test_workflows_share_the_android_sdk_setup_action(self) -> None:
        workflow_text = "\n".join(
            path.read_text(encoding="utf-8")
            for path in sorted(WORKFLOW_DIRECTORY.glob("*.y*ml"))
        )
        self.assertGreaterEqual(
            workflow_text.count("uses: ./.github/actions/setup-android-sdk"),
            6,
        )
        self.assertNotIn("sdkmanager", workflow_text)


class AndroidDeviceSmokeWorkflowTest(unittest.TestCase):
    def test_emulator_runner_invokes_one_checked_in_bash_script(self) -> None:
        workflow = ANDROID_DEVICE_SMOKE_WORKFLOW.read_text(encoding="utf-8")
        risk_job = _yaml_mapping_block(workflow, "full-risk-suite", 2)
        risk_script = DEVICE_RISK_SCRIPT.read_text(encoding="utf-8")

        # android-emulator-runner executes each YAML script line in a separate
        # /usr/bin/sh process. A single Bash entrypoint preserves strict mode,
        # continuations, and variables for the whole risk lane.
        self.assertIn("script: bash ci/scripts/run_device_risk_suite.sh", risk_job)
        self.assertNotIn("script: |", risk_job)
        self.assertIn("set -euo pipefail", risk_script)
        self.assertIn(":provider-ankidroid:connectedDebugAndroidTest", risk_script)
        self.assertIn(":app:connectedDebugAndroidTest", risk_script)
        self.assertIn(":app:assembleMinifiedSmoke", risk_script)
        self.assertIn("adb logcat -b all -c", risk_script)
        self.assertIn("adb logcat -b all -d -v threadtime", risk_script)
        self.assertIn('dumpsys activity exit-info "${smoke_package}"', risk_script)
        self.assertIn("minified_smoke_activity_is_resumed", risk_script)
        self.assertIn("app/build/reports/minifiedSmoke/**", risk_job)


class WorkflowAnalysisIntegrityTest(unittest.TestCase):
    def test_sonar_always_builds_complete_deterministic_inputs(self) -> None:
        workflow = SONAR_WORKFLOW.read_text(encoding="utf-8")
        root_gradle = ROOT_BUILD_GRADLE.read_text(encoding="utf-8")
        shared_script = (ROOT / ".github/scripts/run-sonar-analysis.sh").read_text(
            encoding="utf-8",
        )
        connected_script = (ROOT / ".github/scripts/sonar-full-coverage.sh").read_text(
            encoding="utf-8",
        )

        self.assertNotIn("github.event.before", workflow)
        self.assertNotIn("changed-areas", workflow)
        self.assertEqual(2, workflow.count("- 'desktop-app/**'"))
        self.assertIn("head.repo.full_name == github.repository", workflow)
        self.assertIn("bash .github/scripts/run-sonar-analysis.sh fast", workflow)
        self.assertIn("robolectric-android-all", workflow)
        self.assertIn("ciQuality", shared_script)
        self.assertIn("run-sonar-analysis.sh full", connected_script)
        self.assertIn('tasks.register("sonarPreflight")', root_gradle)
        self.assertIn("dependsOn(sonarPreflight)", root_gradle)
        self.assertIn("sonarAppMainBinaries", root_gradle)
        self.assertIn('rootPath("core/build/classes/java/test")', root_gradle)
        self.assertIn(
            'rootPath("desktop-app/build/classes/kotlin/main")',
            root_gradle,
        )
        self.assertIn(
            'rootPath("desktop-app/build/classes/kotlin/test")',
            root_gradle,
        )
        self.assertIn(
            'rootPath("desktop-app/build/reports/jacoco/test/jacocoTestReport.xml")',
            root_gradle,
        )
        self.assertIn(
            '"desktop-app/src/main/kotlin/dev/bee/kanjianki/desktop/'
            'DesktopFoundationWindow.kt"',
            root_gradle,
        )
        self.assertIn('":desktop-app:jacocoTestReport"', root_gradle)
        self.assertIn('":desktop-app:jar"', root_gradle)
        self.assertNotIn("existingSonarPaths", root_gradle)
        self.assertNotIn("src/main/java", root_gradle)

    def test_codeql_forces_explicit_kotlin_and_java_compiler_activity(self) -> None:
        workflow = CODEQL_WORKFLOW.read_text(encoding="utf-8")
        analyze_job = _yaml_mapping_block(workflow, "analyze", 2)
        operational = "\n".join(
            line for line in analyze_job.splitlines() if not line.lstrip().startswith("#")
        )

        self.assertNotRegex(operational, r"(?m)^    if:")
        init_index = operational.index("github/codeql-action/init@")
        compile_index = operational.index("Force clean compiler activity")
        self.assertLess(init_index, compile_index)
        for expected in (
            "./gradlew clean",
            ":bee-fsrs:compileKotlin",
            ":core:compileKotlin",
            ":domain:compileKotlin",
            ":sync-domain:compileKotlin",
            ":writing-core:compileKotlin",
            ":dictionary-core:compileKotlin",
            ":update-core:compileKotlin",
            ":app:compileDebugKotlin",
            ":app:compileDebugJavaWithJavac",
            "--parallel",
            "--no-daemon",
            "--no-build-cache",
            "--rerun-tasks",
        ):
            with self.subTest(expected=expected):
                self.assertIn(expected, operational)


class AndroidReleaseWorkflowTest(unittest.TestCase):
    def setUp(self) -> None:
        self.workflow = ANDROID_RELEASE_WORKFLOW.read_text(encoding="utf-8")

    def test_existing_release_assets_fail_closed_by_default(self) -> None:
        self.assertIn("allow_asset_overwrite:", self.workflow)
        self.assertIn("default: false", self.workflow)
        self.assertIn("ALLOW_ASSET_OVERWRITE", self.workflow)
        self.assertIn('if [[ "${ALLOW_ASSET_OVERWRITE}" == "true" ]]; then', self.workflow)
        self.assertIn("upload_args+=(--clobber)", self.workflow)

        unconditional_uploads = re.findall(r"gh release upload [^\n]*--clobber", self.workflow)
        self.assertEqual([], unconditional_uploads)

    def test_release_version_metadata_comes_from_the_tested_script(self) -> None:
        self.assertIn("python3 ci/scripts/kani_version.py next-tag", self.workflow)
        self.assertNotIn("next-tag --beta", self.workflow)
        self.assertIn("python3 ci/scripts/kani_version.py validate-new-tag", self.workflow)
        self.assertIn("metadata_args=(", self.workflow)
        self.assertIn('python3 ci/scripts/kani_version.py "${metadata_args[@]}"', self.workflow)
        self.assertNotIn("* 1000000", self.workflow)
        self.assertNotIn("BASH_REMATCH", self.workflow)

    def test_auto_release_only_fires_on_successful_android_ci_main_push(self) -> None:
        # The workflow_run trigger is the release's test gate: it must only
        # proceed when Android CI succeeded for a push (not PR) event.
        self.assertIn('workflows: ["Android CI"]', self.workflow)
        self.assertIn("branches: [main]", self.workflow)
        self.assertIn(
            "github.event.workflow_run.conclusion == 'success' && github.event.workflow_run.event == 'push'",
            self.workflow,
        )

    def test_automatic_main_releases_use_legacy_compatible_numeric_tags_and_prerelease_metadata(self) -> None:
        self.assertNotIn("next-tag --beta", self.workflow)
        self.assertIn("metadata_args+=(--prerelease)", self.workflow)
        self.assertIn("RELEASE_NAME: ${{ needs.metadata.outputs.release_name }}", self.workflow)
        self.assertIn("RELEASE_IS_PRERELEASE: ${{ needs.metadata.outputs.prerelease }}", self.workflow)
        self.assertIn('if [[ "${RELEASE_IS_PRERELEASE}" == "true" ]]; then', self.workflow)
        self.assertIn("create_args+=(--prerelease)", self.workflow)
        self.assertIn('--title "${RELEASE_NAME}"', self.workflow)
        self.assertNotRegex(self.workflow, r"gh release create[^\n]*--prerelease")

    def test_release_path_has_no_emulator_or_cross_workflow_check_polling(self) -> None:
        # The release path must stay fast and self-contained. The AnkiDroid
        # emulator fixture (nightly/dispatch only) and cross-workflow
        # check-run polling (SonarQube/CodeQL names) were the top causes of
        # blocked and multi-hour releases. Keep them out of this workflow.
        # Comments are stripped so prose explaining the removal doesn't trip
        # the guard; only operational YAML content is checked.
        operational = "\n".join(
            line for line in self.workflow.splitlines() if not line.lstrip().startswith("#")
        )
        for forbidden in (
            "REQUIRED_CHECKS",
            "check-runs",
            "android-instrumented.yml",
            "ankidroid-fixture",
            "android-emulator-runner",
            "quality-status",
        ):
            with self.subTest(forbidden=forbidden):
                self.assertNotIn(forbidden, operational)

    def test_manual_releases_run_deterministic_tests_before_assembling(self) -> None:
        # Tag pushes and workflow_dispatch can target commits no Android CI
        # run has vouched for, so the validate job must run the unit-test
        # surface inline for those events (and only those events).
        self.assertIn("if: github.event_name != 'workflow_run'", self.workflow)
        tests_step = self.workflow.split("Run deterministic tests (manual releases only)", maxsplit=1)[1]
        tests_step = tests_step.split("Build signed release APK", maxsplit=1)[0]
        for task in (
            ":bee-fsrs:test",
            ":core:test",
            ":domain:test",
            ":sync-domain:test",
            ":writing-core:test",
            ":dictionary-core:test",
            ":update-core:test",
            ":app:testDebugUnitTest",
        ):
            with self.subTest(task=task):
                self.assertIn(task, tests_step)

    def test_privileged_release_checkout_selects_only_trusted_main_commits(self) -> None:
        validate_job = _yaml_mapping_block(self.workflow, "validate", 2)

        self.assertNotIn("ref: ${{ needs.metadata.outputs.build_sha }}", validate_job)
        self.assertIn("fetch-depth: 0", validate_job)
        self.assertIn("Select trusted release commit", validate_job)
        self.assertIn("^[0-9a-f]{40}$", validate_job)
        self.assertIn("git merge-base --is-ancestor", validate_job)
        self.assertIn("refs/remotes/origin/main", validate_job)
        self.assertIn('git checkout --detach "${BUILD_SHA}"', validate_job)
        self.assertLess(
            validate_job.index("Select trusted release commit"),
            validate_job.index("Validate Gradle wrapper"),
        )

    def test_publish_requires_metadata_and_validate_jobs(self) -> None:
        publish_job = self.workflow.split("  publish-release:", maxsplit=1)[1]
        needs = publish_job.split("needs:", maxsplit=1)[1].split("steps:", maxsplit=1)[0]
        self.assertIn("- metadata", needs)
        self.assertIn("- validate", needs)

    def test_release_concurrency_and_job_timeouts_fail_closed(self) -> None:
        concurrency = _yaml_mapping_block(self.workflow, "concurrency", 0)
        self.assertIn("group: android-release", concurrency)
        self.assertNotIn("github.event_name", concurrency)
        self.assertIn("cancel-in-progress: false", concurrency)
        for job in ("metadata", "validate", "publish-release"):
            with self.subTest(job=job):
                self.assertIn(
                    "timeout-minutes:",
                    _yaml_mapping_block(self.workflow, job, 2),
                )

    def test_manual_tests_run_before_signing_material_is_decoded(self) -> None:
        validate_job = _yaml_mapping_block(self.workflow, "validate", 2)
        operational = "\n".join(
            line for line in validate_job.splitlines() if not line.lstrip().startswith("#")
        )
        self.assertLess(
            operational.index("Run deterministic tests (manual releases only)"),
            operational.index("Decode signing keystore"),
        )

    def test_release_verifies_apk_signature_and_identity(self) -> None:
        self.assertIn("apksigner\" verify --verbose", self.workflow)
        self.assertIn("package: name='dev.bee.kanjianki'", self.workflow)
        self.assertIn("versionCode='${{ needs.metadata.outputs.version_code }}'", self.workflow)
        self.assertIn("versionName='${{ needs.metadata.outputs.version_name }}'", self.workflow)

    def test_release_validates_gradle_wrapper_before_running_gradle(self) -> None:
        # The validate job runs ./gradlew with signing secrets in env; the
        # wrapper jar must be validated on this push-triggered path (PR-only
        # validation in android-ci.yml does not cover pushes to main).
        self.assertIn("gradle/actions/wrapper-validation@", self.workflow)
        validate_job = self.workflow.split("  validate:", maxsplit=1)[1].split("  publish-release:", maxsplit=1)[0]
        operational = "\n".join(
            line for line in validate_job.splitlines() if not line.lstrip().startswith("#")
        )
        wrapper_index = operational.index("gradle/actions/wrapper-validation@")
        first_gradlew_index = operational.index("./gradlew")
        self.assertLess(
            wrapper_index,
            first_gradlew_index,
            "Wrapper validation must run before any ./gradlew invocation in the validate job",
        )


class AndroidInstrumentedWorkflowTest(unittest.TestCase):
    def setUp(self) -> None:
        self.workflow = ANDROID_INSTRUMENTED_WORKFLOW.read_text(encoding="utf-8")

    def test_live_fixture_runs_nightly_and_on_dispatch_only(self) -> None:
        self.assertIn("workflow_dispatch:", self.workflow)
        self.assertIn("schedule:", self.workflow)
        self.assertIn("17 5 * * *", self.workflow)
        self.assertIn("concurrency:", self.workflow)
        self.assertIn("cancel-in-progress: true", self.workflow)
        # The fixture must never rejoin the release path as a callable gate.
        self.assertNotIn("workflow_call:", self.workflow)

    def test_pinned_ankidroid_download_verifies_exact_x86_64_apk(self) -> None:
        apk_name = "variant-abi-AnkiDroid-2.24.0-x86_64.apk"
        checksums = ANKIDROID_CHECKSUMS.read_text(encoding="utf-8")

        self.assertIn("ankidroid-2.24.0-x86_64-sha256-v1", self.workflow)
        self.assertIn(f"apk_name='{apk_name}'", self.workflow)
        self.assertIn("gh release download v2.24.0", self.workflow)
        self.assertIn('--pattern "${apk_name}"', self.workflow)
        self.assertIn("sha256sum --check", self.workflow)
        self.assertIn("ci/fixtures/ankidroid/ankidroid-2.24.0.sha256", self.workflow)
        self.assertRegex(
            checksums,
            rf"(?m)^b8aaef8c8ed13e96b7bbafbc46e690490684192147ab445db8a193c4ef6989b0  {re.escape(apk_name)}$",
        )

    def test_emulator_fixture_uses_minimum_and_current_api_x86_64_real_runner_scripts(self) -> None:
        self.assertIn("reactivecircus/android-emulator-runner@70f4dee990796918b78d040e3278474bdbd348a7", self.workflow)
        self.assertIn("api-level: [26, 35]", self.workflow)
        self.assertIn("api-level: ${{ matrix.api-level }}", self.workflow)
        self.assertIn("target: google_apis", self.workflow)
        self.assertIn("arch: x86_64", self.workflow)
        self.assertIn("if [ ! -e /dev/kvm ]", self.workflow)
        self.assertIn("sudo chmod 0666 /dev/kvm", self.workflow)
        self.assertIn("test -r /dev/kvm && test -w /dev/kvm", self.workflow)
        self.assertIn("disable-linux-hw-accel: false", self.workflow)
        self.assertIn("disable-animations: true", self.workflow)
        self.assertIn("script: bash ci/scripts/run_ankidroid_fixture.sh", self.workflow)
        self.assertIn("run_ankidroid_retired_lifecycle_fixture.sh", self.workflow)
        self.assertIn("${{ steps.ankidroid.outputs.apk_path }}", self.workflow)
        self.assertIn("${{ steps.fixture.outputs.collection_path }}", self.workflow)
        self.assertIn("${{ steps.fixture.outputs.lifecycle_fixture_dir }}", self.workflow)
        self.assertIn(":provider-ankidroid:assembleDebugAndroidTest", self.workflow)
        self.assertIn(":app:assembleDebugAndroidTest", self.workflow)

    def test_sanitized_fixture_is_generated_in_runner_temp(self) -> None:
        self.assertIn("python3 ci/scripts/create_ankidroid_kiku_fixture.py", self.workflow)
        self.assertIn("python3 ci/scripts/create_ankidroid_retired_lifecycle_fixtures.py", self.workflow)
        self.assertIn("${RUNNER_TEMP}/kiku-provider-fixture.anki2", self.workflow)
        self.assertIn("${RUNNER_TEMP}/retired-lifecycle", self.workflow)
        self.assertIn("collection_path=${collection_path}", self.workflow)
        self.assertIn("lifecycle_fixture_dir=${lifecycle_fixture_dir}", self.workflow)

    def test_evidence_upload_runs_on_success_and_failure(self) -> None:
        diagnostics = self.workflow.split("Upload instrumentation diagnostics", maxsplit=1)[1]
        self.assertIn("if: always()", diagnostics)
        for path in (
            "${{ runner.temp }}/ankidroid-fixture-logcat.txt",
            "${{ runner.temp }}/ankidroid-fixture-provider-probe.txt",
            "${{ runner.temp }}/ankidroid-fixture-instrumentation.txt",
            "${{ runner.temp }}/ankidroid-fixture-provider-instrumentation.txt",
            "${{ runner.temp }}/ankidroid-fixture-app-instrumentation.txt",
            "${{ runner.temp }}/ankidroid-retired-lifecycle-*.txt",
            "app/build/reports/**",
            "app/build/outputs/androidTest-results/**",
            "provider-ankidroid/build/reports/**",
            "provider-ankidroid/build/outputs/androidTest-results/**",
        ):
            with self.subTest(path=path):
                self.assertIn(path, diagnostics)


class FakeAnkiDroidProviderPackagingTest(unittest.TestCase):
    def test_fake_provider_source_lives_in_provider_debug_not_android_test(self) -> None:
        self.assertTrue(FAKE_PROVIDER_DEBUG_SOURCE.exists())
        self.assertFalse(FAKE_PROVIDER_ANDROID_TEST_SOURCE.exists())
        android_test_fake_providers = sorted(
            path.relative_to(ROOT).as_posix()
            for source_root in (
                ROOT / "app/src/androidTest",
                ROOT / "provider-ankidroid/src/androidTest",
            )
            for path in source_root.rglob("FakeAnkiDroidProvider.*")
        )
        self.assertEqual([], android_test_fake_providers)

    def test_debug_manifest_declares_non_exported_fake_provider(self) -> None:
        manifest = ET.parse(DEBUG_MANIFEST)
        namespace = "{http://schemas.android.com/apk/res/android}"
        providers = manifest.findall(".//provider")
        fake_providers = [
            provider
            for provider in providers
            if provider.attrib.get(f"{namespace}name")
            == "dev.bee.kanjianki.anki.FakeAnkiDroidProvider"
        ]
        self.assertEqual(1, len(fake_providers))
        provider = fake_providers[0]
        self.assertEqual("dev.bee.kanjianki.test.ankidroid", provider.attrib.get(f"{namespace}authorities"))
        self.assertEqual("false", provider.attrib.get(f"{namespace}exported"))


class CiPathFilterCoverageTest(unittest.TestCase):
    """Goal 48: source roots must not silently drop out of CI.

    The push and PR path filters in android-ci.yml must be identical sets, and
    every top-level directory that holds build-affecting source (Gradle/Kotlin
    or CI-consumed Python) must be covered by at least one filter entry.
    """

    ANDROID_CI = ROOT / ".github/workflows/android-ci.yml"

    # Directories that must be covered by an android-ci path filter.
    REQUIRED_COVERED_DIRS = (
        "app",
        "core",
        "bee-fsrs",
        "domain",
        "sync-domain",
        "writing-core",
        "dictionary-core",
        "update-core",
        "build-logic",
        "ci",
        "scripts",
        "tools",
        ".github",
    )

    def setUp(self) -> None:
        self.workflow = self.ANDROID_CI.read_text(encoding="utf-8")

    def _paths(self, trigger: str) -> set[str]:
        on = _yaml_mapping_block(self.workflow, "on", 0)
        trigger_block = _yaml_mapping_block(on, trigger, 2)
        return set(_yaml_string_list(trigger_block, "paths", 4))

    def test_push_and_pull_request_path_lists_are_identical(self) -> None:
        push_paths = self._paths("push")
        pr_paths = self._paths("pull_request")
        self.assertEqual(
            push_paths,
            pr_paths,
            "android-ci push and pull_request path filters must match exactly",
        )

    def test_every_source_root_is_covered_by_a_filter(self) -> None:
        push_paths = self._paths("push")
        for directory in self.REQUIRED_COVERED_DIRS:
            covered = any(
                entry == f"{directory}/**"
                or entry.startswith(f"{directory}/")
                or entry == f"{directory}/**/*"
                for entry in push_paths
            )
            self.assertTrue(
                covered,
                f"source root '{directory}/' is not covered by any android-ci path filter",
            )

    def test_build_logic_ci_tests_and_shared_github_automation_are_covered(self) -> None:
        push_paths = self._paths("push")
        for entry in (
            "build-logic/**",
            "ci/tests/**",
            ".github/actions/**",
            ".github/scripts/**",
        ):
            self.assertIn(entry, push_paths)

    def test_robolectric_cache_key_tracks_the_version_catalog(self) -> None:
        self.assertIn("hashFiles('gradle/libs.versions.toml')", self.workflow)
        self.assertNotRegex(self.workflow, r"robolectric-android-all-.*-v1")


if __name__ == "__main__":
    unittest.main()
