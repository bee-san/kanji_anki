#!/usr/bin/env python3

from __future__ import annotations

import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ROOT_BUILD_GRADLE = ROOT / "build.gradle.kts"
ANDROID_RELEASE_WORKFLOW = ROOT / ".github/workflows/android-release.yml"
ANDROID_INSTRUMENTED_WORKFLOW = ROOT / ".github/workflows/android-instrumented.yml"
DEBUG_MANIFEST = ROOT / "app/src/debug/AndroidManifest.xml"
FAKE_PROVIDER_DEBUG_SOURCE = (
    ROOT / "app/src/debug/kotlin/dev/bee/kanjianki/anki/FakeAnkiDroidProvider.kt"
)
FAKE_PROVIDER_ANDROID_TEST_SOURCE = (
    ROOT / "app/src/androidTest/kotlin/dev/bee/kanjianki/anki/FakeAnkiDroidProvider.kt"
)


class FastCiTaskWiringTest(unittest.TestCase):
    def setUp(self) -> None:
        self.gradle = ROOT_BUILD_GRADLE.read_text(encoding="utf-8")

    def test_ci_fast_runs_ci_script_python_tests(self) -> None:
        self.assertIn('tasks.register<Exec>("testCiScripts")', self.gradle)
        self.assertIn('"-s", "ci/tests"', self.gradle)
        self.assertIn('"-p", "test_*.py"', self.gradle)
        fast_tasks = self.gradle.split("val fastCiTasks = listOf(", maxsplit=1)[1].split(")", maxsplit=1)[0]
        self.assertIn('"testDictionaryAssets"', fast_tasks)
        self.assertIn('"testCiScripts"', fast_tasks)

    def test_android_ci_asset_job_matches_local_python_test_surface(self) -> None:
        # CI's Fast confidence gate must run the same Python suites as local ciFast:
        # tools/, scripts/tests/, AND ci/tests/. The last one was missing, letting the
        # CI gate silently diverge from local ciFast (which runs it via testCiScripts).
        ci = (ROOT / ".github/workflows/android-ci.yml").read_text(encoding="utf-8")
        for directory in ("tools", "scripts/tests", "ci/tests"):
            with self.subTest(directory=directory):
                self.assertIn(f"-s {directory} -p 'test_*.py'", ci)


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

    def _load(self) -> dict:
        import yaml

        return yaml.safe_load(self.ANDROID_CI.read_text(encoding="utf-8"))

    def _paths(self, trigger: dict) -> set:
        return set(trigger.get("paths", []))

    def test_push_and_pull_request_path_lists_are_identical(self) -> None:
        # PyYAML parses the bare `on:` key as the boolean True.
        on = self._load()[True]
        push_paths = self._paths(on["push"])
        pr_paths = self._paths(on["pull_request"])
        self.assertEqual(
            push_paths,
            pr_paths,
            "android-ci push and pull_request path filters must match exactly",
        )

    def test_every_source_root_is_covered_by_a_filter(self) -> None:
        on = self._load()[True]
        push_paths = self._paths(on["push"])
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

    def test_build_logic_ci_tests_and_github_scripts_are_covered(self) -> None:
        on = self._load()[True]
        push_paths = self._paths(on["push"])
        for entry in ("build-logic/**", "ci/tests/**", ".github/scripts/**"):
            self.assertIn(entry, push_paths)


if __name__ == "__main__":
    unittest.main()
