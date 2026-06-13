#!/usr/bin/env python3

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from typing import cast

from scripts.ralph_loop import ui_manifest, ui_view_matrix


class UiViewMatrixTest(unittest.TestCase):
    def test_build_matrix_maps_routes_to_sources_tests_and_button_rows(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self._write_fixture_repo(root)

            matrix = ui_view_matrix.build_matrix(root)

            self.assertEqual("ui-view-matrix-v1", matrix["schema"])
            summary = cast(dict[str, object], matrix["summary"])
            self.assertEqual(8, summary["view_count"])
            self.assertEqual(["home", "study", "stats", "settings", "games", "narrow", "wide", "update"], summary["routes"])
            self.assertGreaterEqual(cast(int, summary["source_file_count"]), 4)

            views = cast(list[dict[str, object]], matrix["views"])
            home = next(view for view in views if view["view_id"] == "home-default")
            source_files = cast(dict[str, object], home["source_files"])
            primary_source_files = cast(list[str], source_files["primary_source_files"])
            secondary_source_files = cast(list[str], source_files["secondary_source_files"])
            nearest_tests = cast(list[str], source_files["nearest_tests"])
            self.assertEqual(["app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt"], primary_source_files)
            self.assertIn("app/src/main/kotlin/dev/bee/kanjianki/ui/theme/KaniTheme.kt", secondary_source_files)
            self.assertIn("app/src/androidTest/kotlin/dev/bee/kanjianki/HomeScreenComposeTest.kt", nearest_tests)
            self.assertIn("primary_home_cta", cast(list[str], home["interactive_labels"]))
            self.assertGreaterEqual(cast(dict[str, int], home["coverage"])["button_row_count"], 1)
            self.assertTrue(any(row["id"] == "home-study-cta" for row in cast(list[dict[str, object]], home["button_rows"])))

    def test_surfaces_unmapped_button_rows_without_source_files(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self._write_fixture_repo(root)
            contract_path = root / "button-contract.json"
            contract_path.write_text(
                json.dumps(
                    {
                        "schema": "button-contract-v1",
                        "rows": [
                            {
                                "id": "home-study-cta",
                                "title": "Home study CTA",
                                "source_file": "app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt",
                                "composable": "HomeScreen",
                                "labels": ["primary_home_cta"],
                                "interactive_kinds": ["Button"],
                                "existing_tests": ["app/src/androidTest/kotlin/dev/bee/kanjianki/HomeScreenComposeTest.kt"],
                                "missing_tests": [],
                            },
                            {
                                "id": "browse-search-button",
                                "title": "Browse search button",
                                "source_file": "",
                                "composable": "BrowseStudyControls",
                                "labels": ["Search"],
                                "interactive_kinds": ["Button"],
                                "existing_tests": [],
                                "missing_tests": ["click"],
                            },
                        ],
                    },
                    indent=2,
                    sort_keys=True,
                )
                + "\n",
                encoding="utf-8",
            )

            matrix = ui_view_matrix.build_matrix(root, button_contract_path=contract_path)

            summary = cast(dict[str, object], matrix["summary"])
            self.assertEqual(1, summary["unmapped_button_row_count"])
            unmapped_rows = cast(list[dict[str, object]], matrix["unmapped_button_rows"])
            self.assertEqual(["browse-search-button"], [row["id"] for row in unmapped_rows])

    def test_cli_writes_json_and_markdown(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self._write_fixture_repo(root)
            manifest = ui_manifest.build_manifest(root)
            manifest_path = root / ".ralph-loop/current/ui-manifest.json"
            manifest_path.parent.mkdir(parents=True, exist_ok=True)
            manifest_path.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")

            exit_code = ui_view_matrix.main([
                "--repo-root",
                str(root),
                "--manifest",
                str(manifest_path),
                "--out-json",
                ".ralph-loop/current/ui-view-matrix.json",
                "--out-md",
                ".ralph-loop/current/ui-view-matrix.md",
            ])

            self.assertEqual(0, exit_code)
            output = json.loads((root / ".ralph-loop/current/ui-view-matrix.json").read_text(encoding="utf-8"))
            self.assertEqual("ui-view-matrix-v1", output["schema"])
            markdown = (root / ".ralph-loop/current/ui-view-matrix.md").read_text(encoding="utf-8")
            self.assertIn("# Ralph UI View Matrix", markdown)
            self.assertIn("home-default", markdown)

    def _write_fixture_repo(self, root: Path) -> None:
        fixtures = {
            "app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt": """
                package dev.bee.kanjianki
                import androidx.compose.runtime.Composable
                @Composable fun HomeScreen() {
                    Button(onClick = {}, modifier = Modifier.testTag(\"primary_home_cta\")) { Text(\"Study now\") }
                }
            """,
            "app/src/main/kotlin/dev/bee/kanjianki/StudyFlashcardCompose.kt": """
                package dev.bee.kanjianki
                import androidx.compose.runtime.Composable
                @Composable fun StudyFlashcard() { TextField(value = \"\", onValueChange = {}) }
            """,
            "app/src/main/kotlin/dev/bee/kanjianki/SettingsStudyLadderCompose.kt": """
                package dev.bee.kanjianki
                import androidx.compose.runtime.Composable
                @Composable fun SettingsStudyLadderPanel() { Switch(checked = true, onCheckedChange = {}) }
            """,
            "app/src/main/kotlin/dev/bee/kanjianki/ui/theme/KaniTheme.kt": """
                package dev.bee.kanjianki.ui.theme
                import androidx.compose.runtime.Composable
                @Composable fun KaniTheme(content: @Composable () -> Unit) { content() }
            """,
            "app/src/androidTest/kotlin/dev/bee/kanjianki/HomeScreenComposeTest.kt": """
                package dev.bee.kanjianki
                class HomeScreenComposeTest { @Test fun home_button_is_clickable() { compose.onNodeWithText(\"Study now\").performClick() } }
            """,
            "app/src/androidTest/kotlin/dev/bee/kanjianki/StudyFlashcardComposeTest.kt": """
                package dev.bee.kanjianki
                class StudyFlashcardComposeTest { @Test fun study_field_accepts_input() { compose.onNodeWithText(\"Answer\").performTextInput(\"abc\") } }
            """,
        }
        for relative, text in fixtures.items():
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text.strip() + "\n", encoding="utf-8")


if __name__ == "__main__":
    unittest.main()
