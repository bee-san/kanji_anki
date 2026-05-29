#!/usr/bin/env python3

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from scripts.ralph_loop import ui_manifest


class UiManifestTest(unittest.TestCase):
    def test_sample_compose_fixtures_are_bucketed_with_composables_markers_tests_and_risks(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self._write_fixture(root)

            manifest = ui_manifest.build_manifest(root)

        files = {entry["path"]: entry for entry in manifest["files"]}
        self.assertEqual("ui-manifest-v1", manifest["schema"])
        self.assertEqual(sorted(files), [entry["path"] for entry in manifest["files"]])
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
            {entry["bucket"] for entry in manifest["files"]},
        )

        home = files["app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt"]
        self.assertEqual("home", home["bucket"])
        self.assertEqual(["HomeScreen", "HomePrimaryCta"], home["composables"])
        self.assertIn("interactive", home["risk_tags"])
        self.assertEqual(["app/src/androidTest/kotlin/dev/bee/kanjianki/HomeScreenComposeTest.kt"], home["nearest_tests"])
        self.assertTrue(any(marker["kind"] == "Button" and marker["label"] == "primary_home_cta" for marker in home["interactive_markers"]))

        settings = files["app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettingsScreenCompose.kt"]
        self.assertIn("stateful_input", settings["risk_tags"])
        self.assertTrue(any(marker["kind"] == "Switch" for marker in settings["interactive_markers"]))

        shell = files["app/src/main/kotlin/dev/bee/kanjianki/MainActivityShell.kt"]
        self.assertEqual("shell", shell["bucket"])
        self.assertIn("shell_entry", shell["risk_tags"])

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
