#!/usr/bin/env python3

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from typing import cast
from unittest import mock

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
            compose.onNodeWithText("Sync AnkiDroid").performClick();
        """

        selectors = button_contract._direct_selectors(text)

        self.assertNotIn(("Study now", 'onNodeWithText("Study now") + performClick'), selectors)
        self.assertIn(("Sync AnkiDroid", 'onNodeWithText("Sync AnkiDroid") + performClick'), selectors)

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

    def test_browse_and_recent_mistake_rows_map_to_literal_selector_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self._write_fixture(
                root,
                {
                    "app/src/main/kotlin/dev/bee/kanjianki/MainActivityHomeBrowseSearchCompose.kt": """
                        package dev.bee.kanjianki
                        @Composable fun BrowseScreen(model: BrowseScreenModel) {
                            HomeFullWidthHomeButton(label = HomeTextCopy.homeLabel(), onClick = model.onHome)
                            Button(onClick = { withButtonTrace("home-search") { model.onSearch() } }) {
                                Text(HomeTextCopy.browseSearchButtonLabel())
                            }
                            Surface(
                                modifier = Modifier
                                    .testTag(browseKanjiRowTestTag("裂"))
                                    .semantics { contentDescription = browseKanjiRowDescription(model) }
                                    .clickable(role = Role.Button, onClick = { withButtonTrace("browse-kanji-裂") { model.onRow() } })
                            ) {
                                Text("Browse kanji row")
                            }
                        }
                    """,
                    "app/src/main/kotlin/dev/bee/kanjianki/HomeRecentMistakesCompose.kt": """
                        package dev.bee.kanjianki
                        @Composable fun HomeRecentMistakesPanel(model: HomeRecentMistakesPanelModel) {
                            Surface(
                                modifier = Modifier
                                    .testTag(homeRecentMistakesCardTestTag("裂"))
                                    .semantics { contentDescription = homeRecentMistakesCardDescription(model) }
                                    .clickable(role = Role.Button, onClick = { withButtonTrace("recent-mistake-裂") { model.onClick() } })
                            ) {
                                Text("Recent mistakes card")
                            }
                        }
                    """,
                    "app/src/androidTest/java/dev/bee/kanjianki/BrowseAndMistakesComposeTest.java": """
                        package dev.bee.kanjianki;
                        class BrowseAndMistakesComposeTest {
                            void clicks_browse_and_recent_mistakes_controls() {
                                compose.onNodeWithText("Home").performClick();
                                compose.onNodeWithText("Search").performClick();
                                compose.onNodeWithTag("browse-kanji-row-裂").performClick();
                                compose.onNodeWithTag("home-recent-mistakes-card-裂").performClick();
                            }
                        }
                    """,
                },
            )
            manifest_path = self._write_manifest(
                root,
                [
                    "app/src/main/kotlin/dev/bee/kanjianki/MainActivityHomeBrowseSearchCompose.kt",
                    "app/src/main/kotlin/dev/bee/kanjianki/HomeRecentMistakesCompose.kt",
                ],
            )

            contract = button_contract.build_contract(root, manifest_path)

        browse_home = self._row(contract, "browse-home-button")
        self.assertEqual(["Home"], cast(list[str], browse_home["labels"]))
        self.assertEqual(
            ['app/src/androidTest/java/dev/bee/kanjianki/BrowseAndMistakesComposeTest.java:onNodeWithText("Home") + performClick'],
            cast(list[str], browse_home["existing_tests"]),
        )
        self.assertEqual([], cast(list[str], browse_home["missing_tests"]))

        browse_search = self._row(contract, "browse-search-button")
        self.assertEqual(["Search"], cast(list[str], browse_search["labels"]))
        self.assertTrue(any("Search" in entry for entry in cast(list[str], browse_search["existing_tests"])))
        self.assertEqual([], cast(list[str], browse_search["missing_tests"]))

        browse_row = self._row(contract, "browse-kanji-row")
        self.assertEqual(["browse-kanji-row-裂"], cast(list[str], browse_row["labels"]))
        self.assertTrue(any("browse-kanji-row-裂" in entry for entry in cast(list[str], browse_row["existing_tests"])))
        self.assertEqual([], cast(list[str], browse_row["missing_tests"]))

        recent = self._row(contract, "home-recent-mistakes-card")
        self.assertEqual(["home-recent-mistakes-card-裂"], cast(list[str], recent["labels"]))
        self.assertTrue(any("home-recent-mistakes-card-裂" in entry for entry in cast(list[str], recent["existing_tests"])))
        self.assertEqual([], cast(list[str], recent["missing_tests"]))

    def test_study_top_bar_actions_map_to_icon_button_clicks(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self._write_fixture(
                root,
                {
                    "app/src/main/kotlin/dev/bee/kanjianki/StudyTopBarCompose.kt": """
                        package dev.bee.kanjianki
                        @Composable
                        fun StudyTopBar() {
                            Button(onClick = {}) { Text("Close study") }
                            Button(onClick = {}) { Text("Settings") }
                        }
                    """,
                    "app/src/androidTest/java/dev/bee/kanjianki/StudyTopBarComposeTest.java": """
                        package dev.bee.kanjianki;
                        class StudyTopBarComposeTest {
                            void clicks_study_top_bar_actions() {
                                compose.onNodeWithContentDescription("Close study").performClick();
                                compose.onNodeWithContentDescription("Settings").performClick();
                            }
                        }
                    """,
                },
            )
            manifest_path = self._write_manifest(root, ["app/src/main/kotlin/dev/bee/kanjianki/StudyTopBarCompose.kt"])

            contract = button_contract.build_contract(root, manifest_path)

        row = self._row(contract, "study-topbar-actions")
        self.assertEqual("app/src/main/kotlin/dev/bee/kanjianki/StudyTopBarCompose.kt", row["source_file"])
        self.assertEqual("StudyTopBar", row["composable"])
        self.assertEqual(["Close study", "Settings"], cast(list[str], row["labels"]))
        self.assertEqual(
            [
                'app/src/androidTest/java/dev/bee/kanjianki/StudyTopBarComposeTest.java:onNodeWithContentDescription("Close study") + performClick',
                'app/src/androidTest/java/dev/bee/kanjianki/StudyTopBarComposeTest.java:onNodeWithContentDescription("Settings") + performClick',
            ],
            row["existing_tests"],
        )
        self.assertEqual([], row["missing_tests"])

    def test_seed_labels_fall_back_to_expected_labels_for_model_driven_ui(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self._write_fixture(
                root,
                {
                    "app/src/main/kotlin/dev/bee/kanjianki/DynamicGamesScreen.kt": """
                        package dev.bee.kanjianki
                        @Composable fun DynamicGamesScreen(model: DynamicGamesScreenModel) {
                            Button(onClick = model.onStart) { Text(model.title) }
                        }
                    """,
                    "app/src/androidTest/java/dev/bee/kanjianki/DynamicGamesScreenTest.kt": """
                        package dev.bee.kanjianki
                        class DynamicGamesScreenTest {
                            fun clicks_start() {
                                compose.onNodeWithText("Start").performClick()
                            }
                        }
                    """,
                },
            )
            manifest_path = self._write_manifest(root, ["app/src/main/kotlin/dev/bee/kanjianki/DynamicGamesScreen.kt"])
            seed = button_contract.Seed(
                "dynamic-start",
                "Dynamic start button",
                ("dynamic", "start"),
                ("DynamicGamesScreen.kt",),
                ("DynamicGamesScreen",),
                ("Start",),
            )

            with mock.patch.object(button_contract, "SEEDS", (seed,)):
                contract = button_contract.build_contract(root, manifest_path)

        row = self._row(contract, "dynamic-start")
        self.assertEqual("app/src/main/kotlin/dev/bee/kanjianki/DynamicGamesScreen.kt", row["source_file"])
        self.assertEqual("DynamicGamesScreen", row["composable"])
        self.assertEqual(["Start"], cast(list[str], row["labels"]))
        self.assertEqual(
            [
                'app/src/androidTest/java/dev/bee/kanjianki/DynamicGamesScreenTest.kt:onNodeWithText("Start") + performClick',
            ],
            row["existing_tests"],
        )
        self.assertNotIn('missing direct selector/click coverage for "Start"', cast(list[str], row["missing_tests"]))

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
        self.assertEqual([], missing_tests)

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
        missing_tests = cast(list[str], row["missing_tests"])
        self.assertNotIn("missing source mapping for dedicated save control", missing_tests)
        self.assertIn("missing direct selector/click coverage for \"On\"", missing_tests)
        self.assertIn("missing direct selector/click coverage for \"Down\"", missing_tests)
        self.assertIn("missing enabled/disabled state coverage", missing_tests)

    def test_settings_new_card_sort_maps_to_sort_panel_controls(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self._write_fixture(
                root,
                {
                    "app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettingsStudySortCompose.kt": """
                        package dev.bee.kanjianki
                        @Composable fun SettingsNewCardSortPanel(model: SettingsNewCardSortPanelModel) {
                            Text("New card sort")
                            OutlinedButton(onClick = { }) { Text("Frequency") }
                            OutlinedButton(onClick = { }) { Text("Balanced priority") }
                            OutlinedButton(onClick = { }) { Text("Anki difficulty") }
                            OutlinedButton(onClick = { }) { Text("Retrievability risk") }
                            OutlinedButton(onClick = { }) { Text("Kani weakness") }
                            Button(onClick = { }) { Text("Save new card sort") }
                        }
                    """,
                    "app/src/androidTest/java/dev/bee/kanjianki/SettingsStudySortComposeTest.java": """
                        package dev.bee.kanjianki;
                        class SettingsStudySortComposeTest {
                            void edits_new_card_sort() {
                                compose.onNodeWithText("Frequency").assertIsEnabled();
                                compose.onNodeWithText("Frequency").performClick();
                                compose.onNodeWithText("Balanced priority").assertIsEnabled();
                                compose.onNodeWithText("Balanced priority").performClick();
                                compose.onNodeWithText("Anki difficulty").assertIsEnabled();
                                compose.onNodeWithText("Anki difficulty").performClick();
                                compose.onNodeWithText("Retrievability risk").assertIsEnabled();
                                compose.onNodeWithText("Retrievability risk").performClick();
                                compose.onNodeWithText("Kani weakness").assertIsEnabled();
                                compose.onNodeWithText("Kani weakness").performClick();
                                compose.onNodeWithText("Save new card sort").assertIsEnabled();
                                compose.onNodeWithText("Save new card sort").performClick();
                            }
                        }
                    """,
                },
            )
            manifest_path = self._write_manifest(
                root,
                ["app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettingsStudySortCompose.kt"],
            )

            contract = button_contract.build_contract(root, manifest_path)

        row = self._row(contract, "settings-new-card-sort")
        labels = cast(list[str], row["labels"])
        existing_tests = cast(list[str], row["existing_tests"])
        missing_tests = cast(list[str], row["missing_tests"])
        self.assertEqual("SettingsNewCardSortPanel", row["composable"])
        self.assertIn("Save new card sort", labels)
        self.assertTrue(any("Save new card sort" in entry for entry in existing_tests))
        self.assertNotIn("missing enabled/disabled state coverage", missing_tests)

    def test_study_settings_toggle_maps_to_category_header(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self._write_fixture(
                root,
                {
                    "app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettingsCategoryCompose.kt": """
                        package dev.bee.kanjianki
                        @Composable fun SettingsCategoryHeader(title: String, onToggle: () -> Unit) {
                            Row(
                                modifier = Modifier.clickable { withButtonTrace(title) { onToggle() } }
                            ) {
                                // Category titles like Study settings reuse this header row.
                                Text(text = title)
                            }
                        }
                    """,
                    "app/src/androidTest/java/dev/bee/kanjianki/SettingsScreenCategoryNavigationComposeTest.kt": """
                        package dev.bee.kanjianki;
                        class SettingsScreenCategoryNavigationComposeTest {
                            void category_header_title_is_clickable() {
                                compose.onNodeWithText("Study settings").assertHasClickAction().performClick();
                                compose.onNodeWithContentDescription("Expand Study settings").assertIsDisplayed();
                            }
                        }
                    """,
                },
            )
            manifest_path = self._write_manifest(
                root,
                ["app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettingsCategoryCompose.kt"],
            )

            contract = button_contract.build_contract(root, manifest_path)

        row = self._row(contract, "study-settings-toggle")
        labels = cast(list[str], row["labels"])
        existing_tests = cast(list[str], row["existing_tests"])
        missing_tests = cast(list[str], row["missing_tests"])
        self.assertEqual("app/src/main/kotlin/dev/bee/kanjianki/MainActivitySettingsCategoryCompose.kt", row["source_file"])
        self.assertIn("Study settings", labels)
        self.assertTrue(any("Study settings" in entry for entry in existing_tests))
        self.assertEqual([], missing_tests)

    def test_study_done_actions_maps_to_exit_and_dialog_controls(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            self._write_fixture(
                root,
                {
                    "app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyDoneActionsCompose.kt": """
                        package dev.bee.kanjianki
                        @Composable fun StudyDoneActions(model: StudyDoneActionsModel) {
                            Text("Study more new cards")
                            Text("Continue all kanji")
                            Text("Back home")
                            TextButton(onClick = { }) { Text("Study") }
                            TextButton(onClick = { }) { Text("Cancel") }
                        }
                    """,
                    "app/src/androidTest/java/dev/bee/kanjianki/MainActivityStudyDoneActionsComposeTest.java": """
                        package dev.bee.kanjianki;
                        class MainActivityStudyDoneActionsComposeTest {
                            void uses_done_actions_controls() {
                                compose.onNodeWithText("Study more new cards").performClick();
                                compose.onNodeWithText("Continue all kanji").performClick();
                                compose.onNodeWithText("Back home").performClick();
                                compose.onNodeWithText("Study").performClick();
                                compose.onNodeWithText("Cancel").performClick();
                            }
                        }
                    """,
                },
            )
            manifest_path = self._write_manifest(
                root,
                ["app/src/main/kotlin/dev/bee/kanjianki/MainActivityStudyDoneActionsCompose.kt"],
            )

            contract = button_contract.build_contract(root, manifest_path)

        row = self._row(contract, "study-done-actions")
        labels = cast(list[str], row["labels"])
        existing_tests = cast(list[str], row["existing_tests"])
        self.assertEqual("StudyDoneActions", row["composable"])
        self.assertIn("Study more new cards", labels)
        self.assertIn("Back home", labels)
        self.assertTrue(any("Study more new cards" in entry for entry in existing_tests))
        self.assertTrue(any("Study" in entry for entry in existing_tests))

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
                            Button(onClick = model.onSync) { Text("Sync AnkiDroid") }
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
                                compose.onNodeWithText("Sync AnkiDroid").performClick();
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
