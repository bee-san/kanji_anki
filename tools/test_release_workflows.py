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
SONAR_WORKFLOW = ROOT / ".github/workflows/sonarqube.yml"
CODEQL_WORKFLOW = ROOT / ".github/workflows/codeql.yml"
DEBUG_MANIFEST = ROOT / "app/src/debug/AndroidManifest.xml"
FAKE_PROVIDER_DEBUG_SOURCE = (
    ROOT / "app/src/debug/kotlin/dev/bee/kanjianki/anki/FakeAnkiDroidProvider.kt"
)
FAKE_PROVIDER_ANDROID_TEST_SOURCE = (
    ROOT / "app/src/androidTest/kotlin/dev/bee/kanjianki/anki/FakeAnkiDroidProvider.kt"
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
            for start, end in zip(job_starts, job_starts[1:]):
                block = lines[start:end]
                gradle_indices = [
                    index
                    for index, line in enumerate(block)
                    if "./gradlew" in line and not line.lstrip().startswith("#")
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
        self.assertIn("head.repo.full_name == github.repository", workflow)
        self.assertIn("bash .github/scripts/run-sonar-analysis.sh fast", workflow)
        self.assertIn("robolectric-android-all", workflow)
        self.assertIn("ciQuality", shared_script)
        self.assertIn("run-sonar-analysis.sh full", connected_script)
        self.assertIn('tasks.register("sonarPreflight")', root_gradle)
        self.assertIn("dependsOn(sonarPreflight)", root_gradle)
        self.assertIn("sonarAppMainBinaries", root_gradle)
        self.assertIn('rootPath("core/build/classes/java/test")', root_gradle)
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
            ":fsrs-java:compileKotlin",
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
        self.assertIn("python3 ci/scripts/kani_version.py metadata", self.workflow)
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
            ":fsrs-java:test",
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

    def test_publish_requires_metadata_and_validate_jobs(self) -> None:
        publish_job = self.workflow.split("  publish-release:", maxsplit=1)[1]
        needs = publish_job.split("needs:", maxsplit=1)[1].split("steps:", maxsplit=1)[0]
        self.assertIn("- metadata", needs)
        self.assertIn("- validate", needs)

    def test_release_concurrency_and_job_timeouts_fail_closed(self) -> None:
        concurrency = _yaml_mapping_block(self.workflow, "concurrency", 0)
        self.assertIn("group: android-release-", concurrency)
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

    def test_pinned_ankidroid_download_prefers_x86_64_apk_then_falls_back(self) -> None:
        self.assertIn("ankidroid-2.24.0-x86_64-v1", self.workflow)
        self.assertIn("gh release download v2.24.0 --repo ankidroid/Anki-Android --pattern '*x86_64*.apk'", self.workflow)
        self.assertIn("gh release download v2.24.0 --repo ankidroid/Anki-Android --pattern '*.apk'", self.workflow)
        self.assertRegex(self.workflow, r"find \"\$\{RUNNER_TEMP\}/ankidroid\" -name '\*x86_64\*\.apk'")
        self.assertRegex(self.workflow, r"find \"\$\{RUNNER_TEMP\}/ankidroid\" -name '\*universal\*\.apk'")
        self.assertIn("::error::No AnkiDroid APK was downloaded.", self.workflow)

    def test_emulator_fixture_uses_atd_x86_64_and_real_runner_script(self) -> None:
        self.assertIn("reactivecircus/android-emulator-runner@70f4dee990796918b78d040e3278474bdbd348a7", self.workflow)
        self.assertIn("api-level: 35", self.workflow)
        self.assertIn("target: aosp_atd", self.workflow)
        self.assertIn("arch: x86_64", self.workflow)
        self.assertIn("disable-animations: true", self.workflow)
        self.assertIn("script: bash ci/scripts/run_ankidroid_fixture.sh", self.workflow)
        self.assertIn("${{ steps.ankidroid.outputs.apk_path }}", self.workflow)
        self.assertIn("${{ steps.fixture.outputs.collection_path }}", self.workflow)

    def test_sanitized_fixture_is_generated_in_runner_temp(self) -> None:
        self.assertIn("python3 ci/scripts/create_ankidroid_kiku_fixture.py", self.workflow)
        self.assertIn("${RUNNER_TEMP}/kiku-provider-fixture.anki2", self.workflow)
        self.assertIn("collection_path=${collection_path}", self.workflow)

    def test_failure_diagnostics_include_logcat_probe_instrumentation_and_reports(self) -> None:
        diagnostics = self.workflow.split("Upload instrumentation diagnostics", maxsplit=1)[1]
        self.assertIn("if: failure()", diagnostics)
        for path in (
            "${{ runner.temp }}/ankidroid-fixture-logcat.txt",
            "${{ runner.temp }}/ankidroid-fixture-provider-probe.txt",
            "${{ runner.temp }}/ankidroid-fixture-instrumentation.txt",
            "app/build/reports/**",
            "app/build/outputs/androidTest-results/**",
        ):
            with self.subTest(path=path):
                self.assertIn(path, diagnostics)


class FakeAnkiDroidProviderPackagingTest(unittest.TestCase):
    def test_fake_provider_source_lives_in_debug_app_not_android_test_apk(self) -> None:
        self.assertTrue(FAKE_PROVIDER_DEBUG_SOURCE.exists())
        self.assertFalse(FAKE_PROVIDER_ANDROID_TEST_SOURCE.exists())
        android_test_fake_providers = sorted(
            path.relative_to(ROOT).as_posix()
            for path in (ROOT / "app/src/androidTest").rglob("FakeAnkiDroidProvider.*")
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
        "fsrs-java",
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
