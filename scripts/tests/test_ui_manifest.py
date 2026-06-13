#!/usr/bin/env python3

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from typing import cast

from scripts.ralph_loop import ui_manifest


class UiManifestTest(unittest.TestCase):
    def test_sample_compose_fixtures_are_bucketed_with_composables_markers_tests_and_risks(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self._write_fixture(root)

            manifest = ui_manifest.build_manifest(root)

        manifest_files = cast(list[dict[str, object]], manifest["files"])
        files: dict[str, dict[str, object]] = {str(entry["path"]): entry for entry in manifest_files}
        summary = cast(dict[str, object], manifest["summary"])
        registry = cast(dict[str, object], manifest["screenshot_fixture_registry"])
        self.assertEqual("ui-manifest-v1", manifest["schema"])
        self.assertEqual("cheap-ralph-screenshot-fixtures-v1", summary["screenshot_fixture_schema"])
        self.assertEqual(8, summary["screenshot_fixture_count"])
        self.assertEqual("cheap-ralph-screenshot-fixtures-v1", registry["schema"])
        self.assertEqual(sorted(files), [entry["path"] for entry in manifest_files])
        self.assertEqual(
            {
                "games",
                "home",
                "settings",
                "shell",
                "stats",
                "study",
                "test",
                "theme",
            },
            {str(entry["bucket"]) for entry in manifest_files},
        )

        home = files["app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt"]
        home_fixtures = cast(list[dict[str, object]], home["screenshot_fixtures"])
        home_markers = cast(list[dict[str, object]], home["interactive_markers"])
        home_risk_tags = cast(list[str], home["risk_tags"])
        home_expected_terms = cast(list[str], home_fixtures[0]["expected_terms"])
        self.assertEqual("home", home["bucket"])
        self.assertEqual(["HomeScreen", "HomePrimaryCta"], home["composables"])
        self.assertIn("interactive", home_risk_tags)
        self.assertEqual(["app/src/androidTest/kotlin/dev/bee/kanjianki/HomeScreenComposeTest.kt"], home["nearest_tests"])
        self.assertTrue(any(marker["kind"] == "Button" and marker["label"] == "primary_home_cta" for marker in home_markers))
        self.assertEqual(["home", "narrow", "wide"], [fixture["route"] for fixture in home_fixtures])
        self.assertEqual("stable-home-default", home_fixtures[0]["fixture_id"])
        self.assertIn("Kani route home", home_expected_terms)

        settings = files["app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettingsScreenCompose.kt"]
        settings_fixtures = cast(list[dict[str, object]], settings["screenshot_fixtures"])
        settings_markers = cast(list[dict[str, object]], settings["interactive_markers"])
        settings_risk_tags = cast(list[str], settings["risk_tags"])
        settings_invariants = cast(list[str], settings_fixtures[0]["known_invariants"])
        self.assertIn("stateful_input", settings_risk_tags)
        self.assertTrue(any(marker["kind"] == "Switch" for marker in settings_markers))
        self.assertEqual(["settings", "update"], [fixture["route"] for fixture in settings_fixtures])
        self.assertIn("Must preserve setting defaults, validation, and persistence semantics.", settings_invariants)

        shell = files["app/src/main/kotlin/dev/bee/kanjianki/MainActivityShell.kt"]
        shell_risk_tags = cast(list[str], shell["risk_tags"])
        self.assertEqual("shell", shell["bucket"])
        self.assertIn("shell_entry", shell_risk_tags)
        self.assertEqual(8, len(cast(list[dict[str, object]], shell["screenshot_fixtures"])))

        theme = files["app/src/main/kotlin/dev/bee/kanjianki/ui/theme/KaniTheme.kt"]
        self.assertEqual(8, len(cast(list[dict[str, object]], theme["screenshot_fixtures"])))

        test = files["app/src/androidTest/kotlin/dev/bee/kanjianki/HomeScreenComposeTest.kt"]
        self.assertEqual([], cast(list[dict[str, object]], test["screenshot_fixtures"]))

    def test_cli_writes_deterministic_json_manifest(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self._write_fixture(root)
            out = root / ".ralph-loop" / "current" / "ui-manifest.json"

            exit_code = ui_manifest.main(["--repo-root", str(root), "--out", str(out)])

            self.assertEqual(0, exit_code)
            loaded = json.loads(out.read_text(encoding="utf-8"))
            self.assertEqual("ui-manifest-v1", loaded["schema"])
            self.assertEqual(8, len(loaded["files"]))
            self.assertEqual(8, loaded["summary"]["screenshot_fixture_count"])

    def _write_fixture(self, root: Path) -> None:
        fixtures = {
            "app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt": """
                package dev.bee.kanjianki
                import androidx.compose.runtime.Composable
                @Composable
                fun HomeScreen() { HomePrimaryCta() }
                @Composable
                private fun HomePrimaryCta() {
                    Button(onClick = {}, modifier = Modifier.testTag(\"primary_home_cta\")) { Text(\"Start\") }
                }
            """,
            "app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyFlashcardCompose.kt": """
                package dev.bee.kanjianki
                @Composable fun StudyFlashcard() { TextField(value = \"\", onValueChange = {}) }
            """,
            "app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettingsScreenCompose.kt": """
                package dev.bee.kanjianki
                @Composable fun SettingsScreen() { Switch(checked = true, onCheckedChange = {}) }
            """,
            "app/src/main/kotlin/dev/bee/kanjianki/MainActivityStatsCompose.kt": """
                package dev.bee.kanjianki
                @Composable fun StatsScreen() { Text(\"Stats\", modifier = Modifier.clickable { }) }
            """,
            "app/src/main/kotlin/dev/bee/kanjianki/MainActivityGamesCompose.kt": """
                package dev.bee.kanjianki
                @Composable fun GamesScreen() { Button(onClick = {}) { Text(\"Play\") } }
            """,
            "app/src/main/kotlin/dev/bee/kanjianki/MainActivityShell.kt": """
                package dev.bee.kanjianki
                @Composable fun AppShell() { NavHost() }
            """,
            "app/src/main/kotlin/dev/bee/kanjianki/ui/theme/KaniTheme.kt": """
                package dev.bee.kanjianki.ui.theme
                @Composable fun KaniTheme(content: @Composable () -> Unit) { content() }
            """,
            "app/src/androidTest/kotlin/dev/bee/kanjianki/HomeScreenComposeTest.kt": """
                package dev.bee.kanjianki
                class HomeScreenComposeTest { @Test fun home_button_is_clickable() {} }
            """,
        }
        for relative, content in fixtures.items():
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content.strip() + "\n", encoding="utf-8")


if __name__ == "__main__":
    unittest.main()
