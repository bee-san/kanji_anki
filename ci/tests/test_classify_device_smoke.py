from __future__ import annotations

import importlib.util
import os
import pathlib
import re
import subprocess
import sys
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "ci/scripts/classify_device_smoke.py"
WORKFLOW = ROOT / ".github/workflows/android-device-smoke.yml"
RISK_SCRIPT = ROOT / "ci/scripts/run_device_risk_suite.sh"
SMOKE_SCRIPT = ROOT / "ci/scripts/run_device_smoke_suite.sh"
PROGUARD_RULES = ROOT / "app/proguard-rules.pro"

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
            "application/src/main/kotlin/dev/bee/kanjianki/application/StudyUseCase.kt",
            "app/src/main/kotlin/dev/bee/kanjianki/anki/AnkiDroidGateway.kt",
            "data-api/src/main/kotlin/dev/bee/kanjianki/data/StudyRepository.kt",
            "data-sql/src/main/kotlin/dev/bee/kanjianki/data/SqlStudyRepository.kt",
            "core/src/main/kotlin/dev/bee/kanjianki/core/BridgeScheduler.kt",
            "app/src/main/kotlin/dev/bee/kanjianki/data/LocalStore.kt",
            "app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudy.kt",
            "app/src/main/res/values/strings.xml",
            "feature-study/src/commonMain/kotlin/dev/bee/kanjianki/study/StudyRoute.kt",
            "platform-contracts/src/main/kotlin/dev/bee/kanjianki/platform/KaniClock.kt",
            "presentation-api/src/commonMain/kotlin/dev/bee/kanjianki/presentation/StudyState.kt",
            "reference-assets/src/commonMain/composeResources/values/strings.xml",
            "sync-domain/src/main/kotlin/dev/bee/kanjianki/sync/SyncPolicy.kt",
            "sync-engine/src/main/kotlin/dev/bee/kanjianki/sync/SyncEngine.kt",
            "ui-common/src/commonMain/kotlin/dev/bee/kanjianki/ui/KaniTheme.kt",
        )
        for path in full_paths:
            with self.subTest(path=path):
                self.assert_level("full", path)

    def test_risk_focused_instrumentation_changes_are_full(self) -> None:
        self.assert_level(
            "full",
            "app/src/androidTest/kotlin/dev/bee/kanjianki/anki/AnkiDroidSyncProviderIntegrationInstrumentedTest.kt",
        )
        self.assert_level(
            "full",
            "app/src/androidTest/kotlin/dev/bee/kanjianki/LadderSchedulerEndToEndTest.kt",
        )
        self.assert_level(
            "full",
        )
        self.assert_level(
            "full",
        )
        self.assert_level(
            "full",
            "provider-ankidroid/src/androidTest/kotlin/dev/bee/kanjianki/anki/AnkiDroidGatewayProviderInstrumentedTest.kt",
        )
        self.assert_level(
            "full",
            "provider-ankidroid/src/androidTest/kotlin/dev/bee/kanjianki/baseline/Goal165ProviderBaselineInstrumentedTest.kt",
        )

    def test_unannotated_instrumentation_change_does_not_claim_full_coverage(self) -> None:
        # An instrumented file with no @DeviceRisk annotation must not claim the full lane.
        # Named against a surviving suite: the previous example went with the MainActivity
        # chain, and a path that no longer exists would make this pass for the wrong reason.
        self.assert_level(
            "smoke",
            "app/src/androidTest/kotlin/dev/bee/kanjianki/AttributionTextsInstrumentedTest.kt",
        )

    def test_every_full_risk_test_is_annotated_for_the_selected_runner_lane(self) -> None:
        annotated_files = {
            source.relative_to(ROOT).as_posix()
            for android_test_root in (
                ROOT / "app/src/androidTest",
                ROOT / "provider-ankidroid/src/androidTest",
            )
            for pattern in ("*.kt", "*.java")
            for source in android_test_root.rglob(pattern)
            if re.search(r"@DeviceRisk\b", source.read_text(encoding="utf-8"))
        }
        self.assertEqual(set(CLASSIFIER.FULL_ANDROID_TEST_FILES), annotated_files)

    def test_test_only_and_build_changes_use_compact_smoke(self) -> None:
        self.assert_level("smoke", "app/src/test/kotlin/dev/bee/kanjianki/ExampleTest.kt")
        self.assert_level("smoke", ".github/workflows/android-ci.yml")
        self.assert_level("smoke", ".gitattributes")

    def test_desktop_host_only_and_branding_changes_use_compact_smoke(self) -> None:
        smoke_paths = (
            "desktop-app/src/main/kotlin/dev/bee/kanjianki/desktop/Main.kt",
            "data-desktop/src/main/kotlin/dev/bee/kanjianki/data/DesktopDatabase.kt",
            "platform-desktop/src/main/kotlin/dev/bee/kanjianki/platform/DesktopFiles.kt",
            "provider-ankiconnect/src/main/kotlin/dev/bee/kanjianki/anki/AnkiConnect.kt",
            "branding/desktop/kani.svg",
        )
        for path in smoke_paths:
            with self.subTest(path=path):
                self.assert_level("smoke", path)

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
            "ci/scripts/run_device_risk_suite.sh",
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
        self.risk_script = RISK_SCRIPT.read_text(encoding="utf-8")
        self.smoke_script = SMOKE_SCRIPT.read_text(encoding="utf-8")
        self.app_build = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
        self.proguard_rules = PROGUARD_RULES.read_text(encoding="utf-8")

    def test_workflow_is_pr_or_manual_only_and_not_a_release_dependency(self) -> None:
        self.assertIn("pull_request:", self.workflow)
        self.assertIn("workflow_dispatch:", self.workflow)
        self.assertNotIn("workflow_run:", self.workflow)
        self.assertNotIn("release:", self.workflow)
        self.assertNotIn("kanjiLiveAnkiDroid", self.workflow)

    def test_compact_lane_runs_on_minimum_and_current_api(self) -> None:
        self.assertIn("api-level: 26", self.workflow)
        self.assertGreaterEqual(self.workflow.count("api-level: 35"), 2)
        self.assertIn("run_device_smoke_suite.sh", self.workflow)
        self.assertIn("DeviceSmoke", self.smoke_script)
        self.assertIn("DeviceRisk", self.risk_script)

    def test_device_suites_run_provider_and_app_hosts_without_authority_conflicts(self) -> None:
        for script in (self.smoke_script, self.risk_script):
            with self.subTest(script=script):
                provider_index = script.index(":provider-ankidroid:connectedDebugAndroidTest")
                uninstall_index = script.index(
                    'adb uninstall "${provider_test_package}"',
                )
                app_index = script.index(":app:connectedDebugAndroidTest")
                self.assertLess(provider_index, uninstall_index)
                self.assertLess(uninstall_index, app_index)

    def test_every_emulator_lane_fails_closed_without_kvm_access(self) -> None:
        emulator_lane_count = self.workflow.count("reactivecircus/android-emulator-runner@")
        self.assertEqual(2, emulator_lane_count)
        self.assertEqual(emulator_lane_count, self.workflow.count("sudo chmod 0666 /dev/kvm"))
        self.assertEqual(
            emulator_lane_count,
            self.workflow.count("test -r /dev/kvm && test -w /dev/kvm"),
        )
        self.assertEqual(emulator_lane_count, self.workflow.count("/dev/kvm is unavailable"))
        self.assertEqual(
            emulator_lane_count,
            self.workflow.count("disable-linux-hw-accel: false"),
        )

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
        self.assertIn("script: bash ci/scripts/run_device_risk_suite.sh", self.workflow)
        self.assertIn(":app:assembleMinifiedSmoke", self.risk_script)
        self.assertIn("adb install -r", self.risk_script)
        self.assertIn("smoke_package='dev.bee.kanjianki.smoke'", self.risk_script)
        # The launcher activity, which the script starts by explicit component. It has to be
        # the exported one: `am start` from the shell runs as uid 2000 and a Permission
        # Denial is how this failed when the launcher moved and the script did not.
        self.assertIn(
            "smoke_activity='dev.bee.kanjianki.host.KaniHostActivity'",
            self.risk_script,
        )
        self.assertIn('-n "${smoke_package}/${smoke_activity}"', self.risk_script)

        smoke_build = self.app_build.split('create("minifiedSmoke")', maxsplit=1)[1]
        smoke_build = smoke_build.split("\n    sourceSets", maxsplit=1)[0]
        self.assertIn('initWith(getByName("release"))', smoke_build)
        self.assertIn('signingConfigs.getByName("debug")', smoke_build)
        self.assertIn('abiFilters += "x86_64"', smoke_build)
        self.assertNotIn("isDebuggable = true", smoke_build)

    def test_mlkit_component_registrar_constructors_survive_r8_full_mode(self) -> None:
        registrar_rule = self.proguard_rules.split(
            "-keep class * implements com.google.firebase.components.ComponentRegistrar",
            maxsplit=1,
        )[1].split("}", maxsplit=1)[0]
        self.assertIn("void <init>();", registrar_rule)

# `Goal165BaselineRunnerContractTest` was removed with the `MainActivity*` chain. It pinned a
# runner and an instrumented test that rendered the old host's screens into baseline images;
# both retired with those screens. The equivalent coverage is the shared
# `feature-*/src/{commonTest,androidHostTest}` render suites, which exercise the same window
# and font-scale axes on both Android and desktop.


if __name__ == "__main__":
    unittest.main()
