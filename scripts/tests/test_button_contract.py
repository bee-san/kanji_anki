#!/usr/bin/env python3

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from typing import cast

from scripts.ralph_loop import button_contract


class ButtonContractTest(unittest.TestCase):
    def test_contract_uses_direct_selector_evidence_not_broad_keyword_fallback(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self._write_fixture(
                root,
                {
                    "app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt": """
                        package dev.bee.kanjianki
                        @Composable
                        fun HomeScreen(model: HomeScreenModel) {
                            Button(onClick = model.onStudy) { Text(model.studyLabel) }
                        }
                    """,
                    "app/src/androidTest/java/dev/bee/kanjianki/HomeScreenInstrumentedTest.java": """
                        package dev.bee.kanjianki;
                        class HomeScreenInstrumentedTest {
                            void unrelated_home_assertion() {
                                assertThat("Study now").isNotEmpty();
                            }
                        }
                    """,
                    "app/src/androidTest/java/dev/bee/kanjianki/HomeCtaClickInstrumentedTest.java": """
                        package dev.bee.kanjianki;
                        class HomeCtaClickInstrumentedTest {
                            void study_click() {
                                compose.onNodeWithText("Study now").performClick();
                            }
                        }
                    """,
                },
            )
            manifest_path = self._write_manifest(root, ["app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt"])

            contract = button_contract.build_contract(root, manifest_path)

        row = self._row(contract, "home-study-cta")
        self.assertEqual(["Study now"], row["labels"])
        self.assertEqual(
            ["app/src/androidTest/java/dev/bee/kanjianki/HomeCtaClickInstrumentedTest.java:onNodeWithText(\"Study now\") + performClick"],
            row["existing_tests"],
        )
        self.assertTrue(all("unrelated_home_assertion" not in entry for entry in row["existing_tests"]))
        self.assertNotIn("selector_coverage", row["missing_tests"])

    def test_selector_click_evidence_stays_on_same_selector_statement(self) -> None:
        text = """
            compose.onNodeWithText("Study now").assertExists();
            compose.onNodeWithText("Sync with AnkiDroid").performClick();
        """

        selectors = button_contract._direct_selectors(text)

        self.assertNotIn(("Study now", 'onNodeWithText("Study now") + performClick'), selectors)
        self.assertIn(("Sync with AnkiDroid", 'onNodeWithText("Sync with AnkiDroid") + performClick'), selectors)

    def test_selector_click_evidence_stops_at_kotlin_statement_without_semicolon(self) -> None:
        text = """
            compose.onNodeWithText("Study now").assertExists()
            unrelated.performClick()
        """

        selectors = button_contract._direct_selectors(text)

        self.assertEqual([], selectors)

    def test_absent_or_weak_evidence_stays_in_missing_tests(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self._write_fixture(
                root,
                {
                    "app/src/main/kotlin/dev/bee/kanjianki/HomeActionsCompose.kt": """
                        package dev.bee.kanjianki
                        @Composable fun HomeActionGrid(model: HomeScreenModel) {
                            Text("Browse Kanji", Modifier.clickable { model.onBrowse() })
                        }
                    """,
                    "app/src/androidTest/java/dev/bee/kanjianki/HomeActionsInstrumentedTest.java": """
                        package dev.bee.kanjianki;
                        class HomeActionsInstrumentedTest {
                            void mentions_label_without_selector_or_click() {
                                String label = "Browse Kanji";
                            }
                        }
                    """,
                },
            )
            manifest_path = self._write_manifest(root, ["app/src/main/kotlin/dev/bee/kanjianki/HomeActionsCompose.kt"])

            contract = button_contract.build_contract(root, manifest_path)

        row = self._row(contract, "home-action-grid")
        self.assertEqual([], row["existing_tests"])
        self.assertIn('missing direct selector/click coverage for "Browse Kanji"', row["missing_tests"])

    def test_state_assertions_count_toward_state_coverage(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self._write_fixture(
                root,
                {
                    "app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettingsStudyLadderCompose.kt": """
                        package dev.bee.kanjianki
                        @Composable fun SettingsStudyLadderPanel(model: SettingsStudyLadderPanelModel) {
                            Text("On")
                            Text("Off")
                            Text("Up")
                            Text("Down")
                            Text("Restore defaults")
                        }
                    """,
                    "app/src/androidTest/java/dev/bee/kanjianki/MainActivitySettingsInstrumentedTest.kt": """
                        package dev.bee.kanjianki;
                        class MainActivitySettingsInstrumentedTest {
                            void updates_study_ladder() {
                                compose.onNodeWithText("On").assertIsEnabled().performClick();
                                compose.onNodeWithText("Off").assertIsNotEnabled().performClick();
                                compose.onNodeWithText("Up").assertIsEnabled().performClick();
                                compose.onNodeWithText("Down").assertIsNotEnabled().performClick();
                                compose.onNodeWithText("Restore defaults").assertIsEnabled().performClick();
                            }
                        }
                    """,
                },
            )
            manifest_path = self._write_manifest(
                root,
                ["app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettingsStudyLadderCompose.kt"],
            )

            contract = button_contract.build_contract(root, manifest_path)

        row = self._row(contract, "settings-save-toggle-reorder")
        missing_tests = cast(list[str], row["missing_tests"])
        self.assertIn("missing source mapping for dedicated save control", missing_tests)
        self.assertNotIn("missing enabled/disabled state coverage", missing_tests)

    def test_state_coverage_only_counts_actual_state_assertions(self) -> None:
        self.assertFalse(
            button_contract._has_enabled_disabled_coverage(
                ["Enabled mode"],
                [
                    'app/src/androidTest/java/dev/bee/kanjianki/MainActivitySettingsInstrumentedTest.kt:onNodeWithText("Enabled mode").performClick()',
                ],
                {},
            )
        )

        self.assertTrue(
            button_contract._has_enabled_disabled_coverage(
                ["Study now"],
                [
                    'app/src/androidTest/java/dev/bee/kanjianki/MainActivitySettingsInstrumentedTest.kt:onNodeWithText("Study now").isEnabled()',
                ],
                {},
            )
        )

    def test_state_coverage_detects_is_enabled_expression(self) -> None:
        self.assertTrue(
            button_contract._has_enabled_disabled_coverage(
                ["Study now"],
                [
                    'app/src/androidTest/java/dev/bee/kanjianki/MainActivitySettingsInstrumentedTest.kt:onNodeWithText("Study now").performClick()',
                ],
                {
                    "Study now": [
                        {
                            "path": "app/src/androidTest/java/dev/bee/kanjianki/MainActivitySettingsInstrumentedTest.kt",
                            "selector": 'onNodeWithText("Study now") + assertIsEnabled',
                        }
                    ]
                },
            )
        )

    def test_settings_save_toggle_reorder_maps_to_ladder_settings_controls(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self._write_fixture(
                root,
                {
                    "app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettingsAnkiSourceImportFiltersCompose.kt": """
                        package dev.bee.kanjianki
                        @Composable fun SettingsImportFiltersPanel(model: SettingsImportFiltersPanelModel) {
                            Button(onClick = model.onSave) { Text("Save import filters") }
                        }
                    """,
                    "app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettingsStudyLadderCompose.kt": """
                        package dev.bee.kanjianki
                        @Composable fun SettingsStudyLadderPanel(model: SettingsStudyLadderPanelModel) {
                            toggleLabel = "On",
                            moveUpLabel = "Up",
                            moveDownLabel = "Down",
                            restoreLabel = "Restore defaults",
                            toggleDescription = "Turn off Recognition",
                            moveUpDescription = "Move up Recognition",
                            moveDownDescription = "Move down Recognition",
                            restoreDescription = "Restore defaults",
                            onToggle = model.onToggle,
                            onMoveUp = model.onMoveUp,
                            onMoveDown = model.onMoveDown,
                            onRestore = model.onRestore,
                            Switch(checked = true, onCheckedChange = { model.onToggle.run("write_kanji", it) })
                            Button(onClick = { model.onMoveUp.run("write_kanji") }) { Text("Up") }
                            Button(onClick = { model.onRestore.run() }) { Text("Restore defaults") }
                        }
                    """,
                    "app/src/androidTest/java/dev/bee/kanjianki/MainActivitySettingsInstrumentedTest.java": """
                        package dev.bee.kanjianki;
                        class MainActivitySettingsInstrumentedTest {
                            void edits_study_ladder() {
                                compose.onNodeWithContentDescription("Turn off Recognition").performClick();
                                compose.onNodeWithText("Up").performClick();
                                compose.onNodeWithText("Restore defaults").performClick();
                            }
                        }
                    """,
                },
            )
            manifest_path = self._write_manifest(
                root,
                [
                    "app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettingsAnkiSourceImportFiltersCompose.kt",
                    "app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettingsStudyLadderCompose.kt",
                ],
            )

            contract = button_contract.build_contract(root, manifest_path)

        row = self._row(contract, "settings-save-toggle-reorder")
        self.assertEqual("SettingsStudyLadderPanel", row["composable"])
        self.assertEqual("app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettingsStudyLadderCompose.kt", row["source_file"])
        self.assertIn("On", row["labels"])
        self.assertIn("Off", row["labels"])
        self.assertIn("Up", row["labels"])
        self.assertIn("Down", row["labels"])
        self.assertIn("Restore defaults", row["labels"])
        self.assertNotIn("Save study ladder", row["labels"])
        self.assertNotEqual("SettingsImportFiltersPanel", row["composable"])
        self.assertTrue(any("Turn off Recognition" in entry for entry in row["existing_tests"]))
        self.assertIn("missing source mapping for dedicated save control", row["missing_tests"])

    def test_state_assertions_do_not_mask_missing_state_for_sibling_labels(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self._write_fixture(
                root,
                {
                    "app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettingsStudyLadderCompose.kt": """
                        package dev.bee.kanjianki
                        @Composable fun SettingsStudyLadderPanel(model: SettingsStudyLadderPanelModel) {
                            toggleLabel = "On",
                            moveUpLabel = "Up",
                            moveDownLabel = "Down",
                            restoreLabel = "Restore default ladder",
                            toggleDescription = "Turn off Recognition",
                            moveUpDescription = "Move up Recognition",
                            moveDownDescription = "Move down Recognition",
                            restoreDescription = "Restore default ladder",
                            onToggle = model.onToggle,
                            onMoveUp = model.onMoveUp,
                            onMoveDown = model.onMoveDown,
                            onRestore = model.onRestore,
                            Switch(checked = true, onCheckedChange = { model.onToggle.run("write_kanji", it) })
                            Button(onClick = { model.onToggle.run("write_kanji", true) }) { Text("On") }
                            Button(onClick = { model.onToggle.run("write_kanji", false) }) { Text("Off") }
                            Button(onClick = { model.onMoveUp.run("write_kanji") }) { Text("Up") }
                            Button(onClick = { model.onMoveDown.run("write_kanji") }) { Text("Down") }
                            Button(onClick = { model.onRestore.run() }) { Text("Restore default ladder") }
                        }
                    """,
                    "app/src/androidTest/java/dev/bee/kanjianki/MainActivitySettingsStateCoverageInstrumentedTest.java": """
                        package dev.bee.kanjianki;
                        class MainActivitySettingsStateCoverageInstrumentedTest {
                            void edits_study_ladder() {
                                compose.onNodeWithText("On").assertIsEnabled();
                                compose.onNodeWithText("On").performClick();
                                compose.onNodeWithText("Off").performClick();
                                compose.onNodeWithText("Up").performClick();
                                compose.onNodeWithText("Down").performClick();
                                compose.onNodeWithText("Restore default ladder").performClick();
                            }
                        }
                    """,
                },
            )
            manifest_path = self._write_manifest(
                root,
                ["app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettingsStudyLadderCompose.kt"],
            )

            contract = button_contract.build_contract(root, manifest_path)

        row = self._row(contract, "settings-save-toggle-reorder")
        self.assertIn("missing enabled/disabled state coverage", cast(list[str], row["missing_tests"]))

    def test_every_row_has_existing_or_missing_tests_and_cli_writes_json_and_markdown(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self._write_fixture(
                root,
                {
                    "app/src/main/kotlin/dev/bee/kanjianki/MainActivityHome.kt": """
                        package dev.bee.kanjianki
                        @Composable fun HomeScreen(model: HomeScreenModel) {
                            Button(onClick = model.onStudy) { Text("Study now") }
                            Button(onClick = model.onSync) { Text("Sync with AnkiDroid") }
                            Text("Browse Kanji", Modifier.clickable { model.onBrowse() })
                            Text("Focus queue")
                        }
                    """,
                    "app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyFlashcardCompose.kt": """
                        package dev.bee.kanjianki
                        @Composable fun StudyAnswerPanel(model: StudyAnswerPanelModel) {
                            Button(onClick = model.onPass) { Text("Pass") }
                            Button(onClick = model.onFail) { Text("Fail") }
                        }
                    """,
                    "app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettingsStudyLadderCompose.kt": """
                        package dev.bee.kanjianki
                        @Composable fun SettingsStudyLadderPanel(model: SettingsStudyLadderPanelModel) {
                            toggleLabel = "On",
                            moveUpLabel = "Up",
                            moveDownLabel = "Down",
                            restoreLabel = "Restore defaults",
                            Switch(checked = true, onCheckedChange = {})
                            Button(onClick = {}) { Text("Up") }
                            Button(onClick = {}) { Text("Restore defaults") }
                        }
                    """,
                    "app/src/androidTest/java/dev/bee/kanjianki/MainActivityHelperInstrumentedTest.java": """
                        package dev.bee.kanjianki;
                        class MainActivityHelperInstrumentedTest {
                            void clicks_core_controls() {
                                compose.onNodeWithText("Study now").performClick();
                                compose.onNodeWithText("Sync with AnkiDroid").performClick();
                                compose.onNodeWithText("Pass").performClick();
                                compose.onNodeWithText("Fail").performClick();
                            }
                        }
                    """,
                },
            )
            manifest_path = self._write_manifest(
                root,
                [
                    "app/src/main/kotlin/dev/bee/kanjianki/MainActivityHome.kt",
                    "app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyFlashcardCompose.kt",
                    "app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettingsStudyLadderCompose.kt",
                ],
            )
            out_json = root / ".ralph-loop/current/button-contract.json"
            out_md = root / ".ralph-loop/current/button-contract.md"

            exit_code = button_contract.main([
                "--repo-root", str(root),
                "--manifest", str(manifest_path),
                "--out-json", str(out_json),
                "--out-md", str(out_md),
            ])

            self.assertEqual(0, exit_code)
            loaded = json.loads(out_json.read_text(encoding="utf-8"))
            self.assertEqual("button-contract-v1", loaded["schema"])
            self.assertTrue(out_md.read_text(encoding="utf-8").startswith("# Ralph Button Contract"))
            self.assertGreaterEqual(len(loaded["rows"]), 8)
            for row in loaded["rows"]:
                self.assertTrue(row["existing_tests"] or row["missing_tests"], row["id"])

    def _write_manifest(self, root: Path, source_paths: list[str]) -> Path:
        files = []
        for path in source_paths:
            text = (root / path).read_text(encoding="utf-8")
            files.append({
                "path": path,
                "bucket": "settings" if "Settings" in path else "study" if "Study" in path else "home",
                "composables": button_contract.composable_names(text),
                "interactive_markers": [],
                "nearest_tests": [],
                "risk_tags": [],
            })
        manifest = {"schema": "ui-manifest-v1", "files": files, "summary": {"file_count": len(files), "buckets": []}}
        path = root / ".ralph-loop/current/ui-manifest.json"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(manifest), encoding="utf-8")
        return path

    def _write_fixture(self, root: Path, files: dict[str, str]) -> None:
        for relative, content in files.items():
            path = root / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content.strip() + "\n", encoding="utf-8")

    def _row(self, contract: dict[str, object], row_id: str) -> dict[str, object]:
        rows = {row["id"]: row for row in contract["rows"]}
        return rows[row_id]


if __name__ == "__main__":
    unittest.main()
