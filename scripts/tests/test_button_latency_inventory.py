#!/usr/bin/env python3

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from typing import cast

from scripts.ralph_loop import button_latency_inventory


class ButtonLatencyInventoryTest(unittest.TestCase):
    def test_inventory_maps_button_rows_to_manifest_risks_and_pending_timing_slots(self) -> None:
        root = Path("/")
        inventory = button_latency_inventory.build_inventory(
            root,
            self._manifest(),
            self._button_contract(),
        )

        self.assertEqual("button-latency-inventory-v1", inventory["schema"])
        self.assertEqual("pending_real_device_timings", inventory["measurement_status"])
        self.assertEqual(
            {
                "row_count": 3,
                "measured_rows": 0,
                "pending_timing_rows": 3,
                "high_risk_rows": 1,
                "missing_click_coverage_rows": 1,
            },
            inventory["summary"],
        )

        rows = {row["id"]: row for row in cast(list[dict[str, object]], inventory["rows"])}
        study = rows["home-study-cta"]
        self.assertEqual("app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt", study["source_file"])
        self.assertEqual("home", study["bucket"])
        self.assertEqual(["Study now"], study["labels"])
        self.assertEqual("kani.button.home-study-cta", study["trace_name"])
        self.assertEqual(1_000, study["target_budget_ms"])
        self.assertIsNone(study["baseline_ms"])
        self.assertIsNone(study["after_ms"])
        self.assertEqual("pending_manual_timing", study["timing_status"])
        self.assertEqual("low", study["latency_risk_level"])
        study_reasons = cast(list[str], study["latency_risk_reasons"])
        self.assertIn("interactive control needs click-to-idle timing", study_reasons)

        sync = rows["home-sync-cta"]
        self.assertEqual("high", sync["latency_risk_level"])
        self.assertGreaterEqual(cast(int, sync["latency_risk_score"]), 70)
        self.assertIn("no_nearest_test", cast(list[str], sync["source_risk_tags"]))
        sync_reasons = cast(list[str], sync["latency_risk_reasons"])
        self.assertIn("missing direct click/selector coverage before timing can be trusted", sync_reasons)
        self.assertIn("sync/provider path may perform database or import work", sync_reasons)

        topbar = rows["study-topbar-actions"]
        self.assertEqual("app/src/main/kotlin/dev/bee/kanjianki/StudyTopBarCompose.kt", topbar["source_file"])
        self.assertEqual("study", topbar["bucket"])
        self.assertEqual(["Close study", "Settings"], topbar["labels"])
        self.assertEqual("kani.button.study-topbar-actions", topbar["trace_name"])
        self.assertEqual(1_000, topbar["target_budget_ms"])
        self.assertIsNone(topbar["baseline_ms"])
        self.assertIsNone(topbar["after_ms"])
        self.assertEqual("pending_manual_timing", topbar["timing_status"])
        self.assertEqual("medium", topbar["latency_risk_level"])
        topbar_reasons = cast(list[str], topbar["latency_risk_reasons"])
        self.assertIn("interactive control needs click-to-idle timing", topbar_reasons)
        self.assertNotIn("missing direct click/selector coverage before timing can be trusted", topbar_reasons)
        self.assertEqual(["interactive"], topbar["source_risk_tags"])

    def test_inventory_applies_manual_timings(self) -> None:
        inventory = button_latency_inventory.build_inventory(
            Path("/"),
            self._manifest(),
            self._button_contract(),
            {
                "schema": "button-latency-measurements-v1",
                "rows": [
                    {
                        "id": "home-study-cta",
                        "baseline_ms": 850,
                        "after_ms": 610,
                    }
                ],
            },
        )

        self.assertEqual("partial_manual_timings", inventory["measurement_status"])
        rows = {row["id"]: row for row in cast(list[dict[str, object]], inventory["rows"])}
        study = rows["home-study-cta"]
        self.assertEqual(850, study["baseline_ms"])
        self.assertEqual(610, study["after_ms"])
        self.assertEqual(-240, study["timing_delta_ms"])
        self.assertEqual("measured", study["timing_status"])
        self.assertIn("timing is within the target budget", cast(list[str], study["latency_risk_reasons"]))
        self.assertLess(cast(int, study["timing_delta_ms"]), 0)
        self.assertLessEqual(cast(int, study["latency_risk_score"]), 40)

        sync = rows["home-sync-cta"]
        self.assertEqual("missing_timing_fields", sync["timing_status"])
        self.assertEqual("partial_manual_timings", inventory["measurement_status"])

    def test_cli_writes_json_and_markdown_inventory(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            run_dir = root / ".ralph-loop" / "current"
            run_dir.mkdir(parents=True, exist_ok=True)
            manifest_path = run_dir / "ui-manifest.json"
            contract_path = run_dir / "button-contract.json"
            timings_path = run_dir / "button-latency-measurements.json"
            manifest_path.write_text(json.dumps(self._manifest()), encoding="utf-8")
            contract_path.write_text(json.dumps(self._button_contract()), encoding="utf-8")
            timings_path.write_text(
                json.dumps(
                    {
                        "schema": "button-latency-measurements-v1",
                        "rows": [
                            {
                                "id": "home-study-cta",
                                "baseline_ms": 620,
                                "after_ms": 590,
                            }
                        ],
                    },
                    sort_keys=True,
                ),
                encoding="utf-8",
            )
            out_json = run_dir / "button-latency-inventory.json"
            out_md = run_dir / "button-latency-inventory.md"

            exit_code = button_latency_inventory.main(
                [
                    "--repo-root",
                    str(root),
                    "--manifest",
                    str(manifest_path),
                    "--button-contract",
                    str(contract_path),
                    "--timings",
                    str(timings_path),
                    "--out-json",
                    str(out_json),
                    "--out-md",
                    str(out_md),
                    "--target-budget-ms",
                    "750",
                ]
            )

            self.assertEqual(0, exit_code)
            loaded = json.loads(out_json.read_text(encoding="utf-8"))
            self.assertEqual("button-latency-inventory-v1", loaded["schema"])
            self.assertEqual("partial_manual_timings", loaded["measurement_status"])
            self.assertEqual(750, loaded["target_budget_ms"])
            self.assertEqual(750, loaded["rows"][0]["target_budget_ms"])
            self.assertEqual("measured", loaded["rows"][0]["timing_status"])
            md = out_md.read_text(encoding="utf-8")
            self.assertTrue(md.startswith("# Ralph Button Latency Inventory"))
            self.assertIn("620 -> 590 ms", md)
            self.assertIn("Measurement status: `partial_manual_timings`", md)

    def test_risk_score_includes_manifest_source_keywords(self) -> None:
        inventory = button_latency_inventory.build_inventory(
            Path("/"),
            {
                "schema": "ui-manifest-v1",
                "files": [
                    {
                        "path": "app/src/main/kotlin/dev/bee/kanjianki/FeatureCompose.kt",
                        "bucket": "settings",
                        "risk_tags": ["interactive"],
                    }
                ],
            },
            {
                "schema": "button-contract-v1",
                "rows": [
                    {
                        "id": "primary-cta",
                        "title": "Primary CTA",
                        "source_file": "app/src/main/kotlin/dev/bee/kanjianki/FeatureCompose.kt",
                        "composable": "FeatureScreen",
                        "labels": ["Continue"],
                        "existing_tests": ["FeatureComposeTest.kt:performClick"],
                        "missing_tests": [],
                    }
                ],
            },
        )

        row = cast(list[dict[str, object]], inventory["rows"])[0]
        self.assertEqual("settings", row["bucket"])
        self.assertEqual("medium", row["latency_risk_level"])
        self.assertGreaterEqual(cast(int, row["latency_risk_score"]), 40)

    def _manifest(self) -> dict[str, object]:
        return {
            "schema": "ui-manifest-v1",
            "files": [
                {
                    "path": "app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt",
                    "bucket": "home",
                    "composables": ["HomeScreen"],
                    "interactive_markers": [{"kind": "Button", "line": 10, "label": "Study now", "snippet": "Button(...)"}],
                    "nearest_tests": ["app/src/androidTest/kotlin/dev/bee/kanjianki/HomeScreenComposeTest.kt"],
                    "risk_tags": ["interactive"],
                },
                {
                    "path": "app/src/main/kotlin/dev/bee/kanjianki/MainActivityHomeSyncCompose.kt",
                    "bucket": "home",
                    "composables": ["SyncResultScreen"],
                    "interactive_markers": [{"kind": "Button", "line": 20, "label": "Sync", "snippet": "Button(...)"}],
                    "nearest_tests": [],
                    "risk_tags": ["interactive", "no_nearest_test"],
                },
                {
                    "path": "app/src/main/kotlin/dev/bee/kanjianki/StudyTopBarCompose.kt",
                    "bucket": "study",
                    "composables": ["StudyTopBar"],
                    "interactive_markers": [
                        {"kind": "Button", "line": 10, "label": "Close study", "snippet": "Button(...)"},
                        {"kind": "Button", "line": 11, "label": "Settings", "snippet": "Button(...)"},
                    ],
                    "nearest_tests": ["app/src/androidTest/kotlin/dev/bee/kanjianki/StudyTopBarComposeTest.kt"],
                    "risk_tags": ["interactive"],
                },
            ],
            "summary": {"file_count": 3, "buckets": ["home", "study"]},
        }

    def _button_contract(self) -> dict[str, object]:
        return {
            "schema": "button-contract-v1",
            "source_manifest": ".ralph-loop/current/ui-manifest.json",
            "rows": [
                {
                    "id": "home-study-cta",
                    "title": "Home study CTA",
                    "source_file": "app/src/main/kotlin/dev/bee/kanjianki/HomeScreenCompose.kt",
                    "composable": "HomeScreen",
                    "labels": ["Study now"],
                    "existing_tests": ["app/src/androidTest/kotlin/dev/bee/kanjianki/HomeScreenComposeTest.kt:onNodeWithText(\"Study now\") + performClick"],
                    "missing_tests": [],
                },
                {
                    "id": "home-sync-cta",
                    "title": "Home sync CTA",
                    "source_file": "app/src/main/kotlin/dev/bee/kanjianki/MainActivityHomeSyncCompose.kt",
                    "composable": "SyncResultScreen",
                    "labels": ["Sync AnkiDroid"],
                    "existing_tests": [],
                    "missing_tests": ["missing direct selector/click coverage for \"Sync AnkiDroid\""],
                },
                {
                    "id": "study-topbar-actions",
                    "title": "Study top bar actions",
                    "source_file": "app/src/main/kotlin/dev/bee/kanjianki/StudyTopBarCompose.kt",
                    "composable": "StudyTopBar",
                    "labels": ["Close study", "Settings"],
                    "existing_tests": [
                        'app/src/androidTest/kotlin/dev/bee/kanjianki/StudyTopBarComposeTest.kt:onNodeWithContentDescription("Close study") + performClick',
                        'app/src/androidTest/kotlin/dev/bee/kanjianki/StudyTopBarComposeTest.kt:onNodeWithContentDescription("Settings") + performClick',
                    ],
                    "missing_tests": [],
                },
            ],
            "summary": {"row_count": 3, "covered_rows": 2, "missing_rows": 1},
        }


if __name__ == "__main__":
    unittest.main()
