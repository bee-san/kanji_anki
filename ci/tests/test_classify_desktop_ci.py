from __future__ import annotations

import importlib.util
import pathlib
import subprocess
import sys
import unittest


ROOT = pathlib.Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "ci/scripts/classify_desktop_ci.py"
SPEC = importlib.util.spec_from_file_location("classify_desktop_ci", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
CLASSIFIER = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = CLASSIFIER
SPEC.loader.exec_module(CLASSIFIER)


class DesktopCiClassifierTest(unittest.TestCase):
    def assert_runs(self, expected: bool, *paths: str) -> None:
        self.assertEqual(expected, CLASSIFIER.classify_paths(paths).run_desktop)

    def test_current_desktop_and_shared_modules_run(self) -> None:
        paths = (
            "desktop-app/src/main/kotlin/DesktopMain.kt",
            "core/src/main/java/CorePolicy.java",
            "domain/src/main/kotlin/DomainModel.kt",
            "sync-domain/src/main/kotlin/SyncModel.kt",
            "writing-core/src/main/java/WritingPolicy.java",
            "dictionary-core/src/main/java/Dictionary.java",
            "update-core/src/main/java/UpdatePolicy.java",
            "fsrs-java/src/main/java/FSRS.java",
        )
        for path in paths:
            with self.subTest(path=path):
                self.assert_runs(True, path)

    def test_planned_shared_and_desktop_modules_run_before_they_exist(self) -> None:
        paths = (
            "data-api/src/main/kotlin/Repository.kt",
            "data-sql/src/main/kotlin/SqlStore.kt",
            "application/src/main/kotlin/UseCase.kt",
            "sync-engine/src/main/kotlin/SyncEngine.kt",
            "platform-contracts/src/main/kotlin/Clock.kt",
            "presentation-api/src/commonMain/kotlin/HomeState.kt",
            "ui-common/src/commonMain/composeResources/values/strings.xml",
            "feature-study/src/commonMain/kotlin/StudyRoute.kt",
            "provider-ankiconnect/src/main/kotlin/AnkiConnect.kt",
            "platform-desktop/src/main/kotlin/DesktopFiles.kt",
            "data-desktop/src/main/kotlin/DesktopDatabase.kt",
        )
        for path in paths:
            with self.subTest(path=path):
                self.assert_runs(True, path)

    def test_build_workflow_wrapper_catalog_resource_and_release_inputs_run(self) -> None:
        paths = (
            ".gitattributes",
            "build-logic/src/main/kotlin/kani.desktop-application-conventions.gradle.kts",
            ".github/workflows/desktop-ci.yml",
            "ci/scripts/classify_desktop_ci.py",
            "tools/run_desktop_installed_image_smoke.py",
            "gradle/libs.versions.toml",
            "gradle/wrapper/gradle-wrapper.properties",
            "gradlew.bat",
            "settings.gradle.kts",
            "branding/desktop/kani.svg",
            "docs/dependency-updates.md",
        )
        for path in paths:
            with self.subTest(path=path):
                self.assert_runs(True, path)

    def test_documentation_and_android_host_only_changes_may_skip(self) -> None:
        self.assert_runs(False, "README.md", "docs/testing/local.md")
        self.assert_runs(
            False,
            "app/src/main/kotlin/dev/bee/kanjianki/MainActivity.kt",
            "provider-ankidroid/src/main/kotlin/Provider.kt",
        )

    def test_mixed_skip_and_shared_change_runs(self) -> None:
        self.assert_runs(
            True,
            "README.md",
            "app/src/main/kotlin/dev/bee/kanjianki/MainActivity.kt",
            "feature-home/src/commonMain/kotlin/Home.kt",
        )

    def test_unknown_and_empty_changes_fail_safe(self) -> None:
        self.assert_runs(True, "config/new-desktop-input.toml")
        self.assert_runs(True)

    def test_cli_accepts_null_delimited_paths_and_force(self) -> None:
        classified = subprocess.run(
            [sys.executable, str(SCRIPT), "--null"],
            input=b"README.md\0desktop-app/build.gradle.kts\0",
            check=True,
            capture_output=True,
        ).stdout.decode("utf-8")
        self.assertIn("run_desktop=true\n", classified)

        forced = subprocess.run(
            [sys.executable, str(SCRIPT), "--force", "run"],
            check=True,
            capture_output=True,
            text=True,
        ).stdout
        self.assertEqual(
            "run_desktop=true\nreason=desktop matrix explicitly forced to run\n",
            forced,
        )


if __name__ == "__main__":
    unittest.main()
