from __future__ import annotations

import importlib.util
import pathlib
import re
import subprocess
import sys
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "ci/scripts/classify_device_smoke.py"
WORKFLOW = ROOT / ".github/workflows/android-device-smoke.yml"

SPEC = importlib.util.spec_from_file_location("classify_device_smoke", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
CLASSIFIER = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = CLASSIFIER
SPEC.loader.exec_module(CLASSIFIER)


class DeviceSmokeClassifierTest(unittest.TestCase):
    def assert_level(self, expected: str, *paths: str) -> None:
        self.assertEqual(expected, CLASSIFIER.classify_paths(paths).level)

    def test_runtime_provider_scheduler_database_and_ui_paths_are_full(self) -> None:
        full_paths = (
            "app/src/main/kotlin/dev/bee/kanjianki/anki/AnkiDroidGateway.kt",
            "core/src/main/kotlin/dev/bee/kanjianki/core/BridgeScheduler.kt",
            "app/src/main/kotlin/dev/bee/kanjianki/data/LocalStore.kt",
            "app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudy.kt",
            "app/src/main/res/values/strings.xml",
            "sync-domain/src/main/kotlin/dev/bee/kanjianki/sync/SyncPolicy.kt",
        )
        for path in full_paths:
            with self.subTest(path=path):
                self.assert_level("full", path)

    def test_risk_focused_instrumentation_changes_are_full(self) -> None:
        self.assert_level(
            "full",
            "app/src/androidTest/kotlin/dev/bee/kanjianki/anki/AnkiDroidGatewayProviderInstrumentedTest.kt",
        )
        self.assert_level(
            "full",
            "app/src/androidTest/kotlin/dev/bee/kanjianki/LadderSchedulerEndToEndTest.kt",
        )

    def test_unannotated_instrumentation_change_does_not_claim_full_coverage(self) -> None:
        self.assert_level(
            "smoke",
            "app/src/androidTest/kotlin/dev/bee/kanjianki/MainActivityGamesInstrumentedTest.kt",
        )

    def test_every_full_risk_test_is_annotated_for_the_selected_runner_lane(self) -> None:
        android_test_root = ROOT / "app/src/androidTest"
        annotated_files = {
            source.relative_to(ROOT).as_posix()
            for pattern in ("*.kt", "*.java")
            for source in android_test_root.rglob(pattern)
            if re.search(r"@DeviceRisk\b", source.read_text(encoding="utf-8"))
        }
        self.assertEqual(set(CLASSIFIER.FULL_ANDROID_TEST_FILES), annotated_files)

    def test_test_only_and_build_changes_use_compact_smoke(self) -> None:
        self.assert_level("smoke", "app/src/test/kotlin/dev/bee/kanjianki/ExampleTest.kt")
        self.assert_level("smoke", ".github/workflows/android-ci.yml")

    def test_release_and_r8_inputs_run_the_minified_full_lane(self) -> None:
        full_paths = (
            "app/build.gradle.kts",
            "app/proguard-rules.pro",
            "app/src/release/kotlin/dev/bee/kanjianki/MainActivityRuntimeOverrides.kt",
            "app/src/minifiedSmoke/kotlin/dev/bee/kanjianki/SmokeOnly.kt",
            "build-logic/src/main/kotlin/dev/bee/kanjianki/buildlogic/KaniVersioning.kt",
            "gradle/libs.versions.toml",
            ".github/workflows/android-device-smoke.yml",
            "ci/scripts/classify_device_smoke.py",
        )
        for path in full_paths:
            with self.subTest(path=path):
                self.assert_level("full", path)

    def test_documentation_only_change_skips_emulator_explicitly(self) -> None:
        result = CLASSIFIER.classify_paths(("README.md", "docs/testing.md"))
        self.assertEqual("none", result.level)
        self.assertFalse(result.run_smoke)
        self.assertFalse(result.run_full)

    def test_mixed_documentation_and_product_change_uses_full_lane(self) -> None:
        self.assert_level("full", "README.md", "app/src/main/AndroidManifest.xml")

    def test_unknown_and_empty_changes_fail_safe(self) -> None:
        self.assert_level("smoke", "config/unclassified.toml")
        self.assert_level("full")

    def test_cli_accepts_git_null_delimited_paths(self) -> None:
        result = subprocess.run(
            [sys.executable, str(SCRIPT), "--null"],
            input=b"README.md\0app/src/main/res/values/strings.xml\0",
            check=True,
            capture_output=True,
        )
        output = result.stdout.decode("utf-8")
        self.assertIn("level=full\n", output)
        self.assertIn("run_smoke=true\n", output)
        self.assertIn("run_full=true\n", output)


class DeviceSmokeWorkflowContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.workflow = WORKFLOW.read_text(encoding="utf-8")
        self.app_build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")

    def test_workflow_is_pr_or_manual_only_and_not_a_release_dependency(self) -> None:
        self.assertIn("pull_request:", self.workflow)
        self.assertIn("workflow_dispatch:", self.workflow)
        self.assertNotIn("workflow_run:", self.workflow)
        self.assertNotIn("release:", self.workflow)
        self.assertNotIn("kanjiLiveAnkiDroid", self.workflow)

    def test_compact_lane_runs_on_minimum_and_current_api(self) -> None:
        self.assertIn("api-level: 26", self.workflow)
        self.assertGreaterEqual(self.workflow.count("api-level: 35"), 2)
        self.assertIn("DeviceSmoke", self.workflow)
        self.assertIn("DeviceRisk", self.workflow)

    def test_deleted_runtime_paths_are_included_in_classification(self) -> None:
        self.assertIn("--diff-filter=ACDMRT", self.workflow)

    def test_all_remote_actions_are_pinned_and_wrapper_is_validated(self) -> None:
        uses = re.findall(r"^\s*uses:\s*['\"]?([^\s'\"]+)", self.workflow, re.MULTILINE)
        self.assertTrue(uses)
        for action in uses:
            with self.subTest(action=action):
                if not action.startswith("./"):
                    self.assertRegex(action, r"^[^@\s]+@[0-9a-f]{40}$")
        self.assertIn("gradle/actions/wrapper-validation@", self.workflow)

    def test_full_lane_builds_and_launches_a_real_minified_x86_64_apk(self) -> None:
        self.assertIn(":app:assembleMinifiedSmoke", self.workflow)
        self.assertIn("adb install -r", self.workflow)
        self.assertIn("dev.bee.kanjianki.smoke/dev.bee.kanjianki.MainActivity", self.workflow)

        smoke_build = self.app_build.split('create("minifiedSmoke")', maxsplit=1)[1]
        smoke_build = smoke_build.split("\n    sourceSets", maxsplit=1)[0]
        self.assertIn('initWith(getByName("release"))', smoke_build)
        self.assertIn('signingConfigs.getByName("debug")', smoke_build)
        self.assertIn('abiFilters += "x86_64"', smoke_build)
        self.assertNotIn("isDebuggable = true", smoke_build)


if __name__ == "__main__":
    unittest.main()
