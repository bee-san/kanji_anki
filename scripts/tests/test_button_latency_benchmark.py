#!/usr/bin/env python3

from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path
from typing import cast

from scripts.ralph_loop import button_latency_benchmark as benchmark


HOME_VARIANT = benchmark.RouteVariant(
    route="home",
    launch_route="home",
    scroll_position="top",
    scroll_y=0,
    expected_terms=("Kani route home",),
)


class ButtonLatencyBenchmarkTest(unittest.TestCase):
    def test_parse_bounds_rejects_empty_or_invalid_bounds(self) -> None:
        self.assertEqual((1, 2, 30, 40), benchmark.parse_bounds("[1,2][30,40]"))
        self.assertIsNone(benchmark.parse_bounds("[1,2][1,40]"))
        self.assertIsNone(benchmark.parse_bounds(""))

    def test_parse_controls_discovers_enabled_clickable_nodes_and_maps_contract_label(self) -> None:
        xml = """
        <hierarchy>
          <node text="Kani route home" clickable="false" enabled="true" bounds="[0,0][10,10]" />
          <node text="Study now" content-desc="" resource-id="" class="android.widget.Button" clickable="true" enabled="true" bounds="[10,20][210,120]" />
          <node text="Hidden" class="android.widget.Button" clickable="true" enabled="false" bounds="[10,20][210,120]" />
          <node text="Duplicate" class="android.widget.Button" clickable="true" enabled="true" bounds="[10,20][210,120]" />
          <node text="Duplicate" class="android.widget.Button" clickable="true" enabled="true" bounds="[10,20][210,120]" />
        </hierarchy>
        """
        controls = benchmark.parse_controls(
            xml,
            HOME_VARIANT,
            {"study now": {"id": "home-study-cta", "title": "Home study CTA"}},
        )

        self.assertEqual(2, len(controls))
        study = controls[0]
        self.assertEqual("Study now", study.label)
        self.assertEqual("home-study-cta", study.id)
        self.assertEqual("Home study CTA", study.contract_title)
        self.assertEqual((110, 70), study.center)
        self.assertEqual("home|top|Study now|||android.widget.Button|10,20,210,120", study.stable_key)

    def test_parse_controls_uses_descendant_labels_for_compose_click_wrappers(self) -> None:
        xml = """
        <hierarchy>
          <node text="" content-desc="" class="android.view.View" clickable="true" enabled="true" bounds="[10,20][210,120]">
            <node text="Study now" content-desc="" class="android.widget.TextView" clickable="false" enabled="true" bounds="[20,30][200,90]" />
          </node>
        </hierarchy>
        """
        controls = benchmark.parse_controls(
            xml,
            HOME_VARIANT,
            {"study now": {"id": "home-study-cta", "title": "Home study CTA"}},
        )

        self.assertEqual(1, len(controls))
        self.assertEqual("Study now", controls[0].label)
        self.assertEqual("home-study-cta", controls[0].id)

    def test_contract_label_index_loads_first_matching_label(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "button-contract.json"
            path.write_text(
                json.dumps(
                    {
                        "schema": "button-contract-v1",
                        "rows": [
                            {"id": "home-study-cta", "title": "Home study CTA", "labels": ["Study now"]},
                            {"id": "home-study-secondary", "title": "Secondary", "labels": ["Study now"]},
                            {"id": "stats-refresh", "title": "Stats refresh", "labels": ["Refresh stats"]},
                        ],
                    }
                ),
                encoding="utf-8",
            )

            index = benchmark.contract_label_index(path)

        self.assertEqual("home-study-cta", index["study now"]["id"])
        self.assertEqual("stats-refresh", index["refresh stats"]["id"])

    def test_skip_reason_defaults_to_avoiding_external_and_stateful_controls(self) -> None:
        sync = benchmark.UiControl(
            route="home",
            launch_route="home",
            scroll_position="top",
            index=0,
            text="Sync AnkiDroid",
            content_desc="",
            resource_id="",
            class_name="android.widget.Button",
            bounds=(0, 0, 100, 100),
        )
        japanese_sync = benchmark.UiControl(
            route="home",
            launch_route="home",
            scroll_position="top",
            index=2,
            text="AnkiDroidを同期",
            content_desc="同期 まだ同期していません タップして同期",
            resource_id="",
            class_name="android.view.View",
            bounds=(0, 0, 100, 100),
        )
        switch = benchmark.UiControl(
            route="settings",
            launch_route="settings",
            scroll_position="top",
            index=1,
            text="",
            content_desc="Daily reminder",
            resource_id="",
            class_name="android.widget.Switch",
            bounds=(0, 0, 100, 100),
        )

        self.assertEqual("unsafe_or_external_side_effect", benchmark.skip_reason(sync, include_unsafe=False, include_stateful=False))
        self.assertEqual("", benchmark.skip_reason(sync, include_unsafe=True, include_stateful=False))
        self.assertEqual(
            "unsafe_or_external_side_effect",
            benchmark.skip_reason(japanese_sync, include_unsafe=False, include_stateful=False),
        )
        self.assertEqual("stateful_input", benchmark.skip_reason(switch, include_unsafe=True, include_stateful=False))
        self.assertEqual("", benchmark.skip_reason(switch, include_unsafe=True, include_stateful=True))

    def test_expected_route_terms_allow_localized_alternatives(self) -> None:
        self.assertTrue(benchmark._matches_expected_term("設定", "Settings||設定"))
        self.assertTrue(benchmark._matches_expected_term("settings", "Settings||設定"))
        self.assertFalse(benchmark._matches_expected_term("ホーム", "Settings||設定"))

    def test_find_debug_apk_ignores_android_test_outputs(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            test_apk = root / "app" / "build" / "outputs" / "apk" / "androidTest" / "debug" / "app-debug-androidTest.apk"
            app_apk = root / "app" / "build" / "outputs" / "apk" / "debug" / "app-debug.apk"
            test_apk.parent.mkdir(parents=True, exist_ok=True)
            app_apk.parent.mkdir(parents=True, exist_ok=True)
            test_apk.write_text("test", encoding="utf-8")
            app_apk.write_text("app", encoding="utf-8")

            self.assertEqual(app_apk, benchmark.find_debug_apk(root))

    def test_launch_route_args_request_single_top_for_warm_reroutes(self) -> None:
        config = benchmark.RunConfig(
            repo_root=Path("/tmp"),
            adb="missing-adb-for-unit-test",
            aapt="aapt",
            apk_path=Path("/tmp/app.apk"),
            package_name="dev.bee.kanjianki",
            out_dir=Path("/tmp/out"),
            repeat_count=2,
            slow_threshold_ms=1_000,
            settle_timeout_ms=6_000,
            stable_polls=2,
            poll_interval_ms=120,
            include_unsafe=False,
            include_stateful=False,
            max_controls=None,
            routes=("home",),
            scroll_positions=("top",),
            dry_run_inventory=False,
        )

        args = benchmark.launch_route_args(config, HOME_VARIANT)

        self.assertIn("--activity-single-top", args)
        self.assertIn(benchmark.BENCHMARK_ROUTE_EXTRA, args)
        self.assertIn("home", args)

    def test_build_report_and_measurements_json_classify_slow_controls(self) -> None:
        config = benchmark.RunConfig(
            repo_root=Path("/tmp"),
            adb="missing-adb-for-unit-test",
            aapt="aapt",
            apk_path=Path("/tmp/app.apk"),
            package_name="dev.bee.kanjianki",
            out_dir=Path("/tmp/out"),
            repeat_count=2,
            slow_threshold_ms=1_000,
            settle_timeout_ms=6_000,
            stable_polls=2,
            poll_interval_ms=120,
            include_unsafe=False,
            include_stateful=False,
            max_controls=None,
            routes=("home",),
            scroll_positions=("top",),
            dry_run_inventory=False,
        )
        row = benchmark.BenchmarkRow(
            control=benchmark.UiControl(
                route="home",
                launch_route="home",
                scroll_position="top",
                index=0,
                text="Study now",
                content_desc="",
                resource_id="",
                class_name="android.widget.Button",
                bounds=(0, 0, 100, 100),
                contract_id="home-study-cta",
                contract_title="Home study CTA",
            ),
            measurements=[
                benchmark.Measurement(latency_ms=900.0, status="measured", phase="first"),
                benchmark.Measurement(latency_ms=1_200.0, status="measured", phase="warm"),
            ],
        )
        skipped = benchmark.BenchmarkRow(
            control=benchmark.UiControl(
                route="home",
                launch_route="home",
                scroll_position="top",
                index=1,
                text="Sync AnkiDroid",
                content_desc="",
                resource_id="",
                class_name="android.widget.Button",
                bounds=(0, 0, 100, 100),
            ),
            skip_reason="unsafe_or_external_side_effect",
        )

        report = benchmark.build_report(config, [row, skipped])
        measurements = benchmark.build_measurements_json(report)
        markdown = benchmark.render_markdown(report)

        summary = cast(dict[str, object], report["summary"])
        self.assertEqual(2, summary["row_count"])
        self.assertEqual(1, summary["measured_rows"])
        self.assertEqual(1, summary["slow_rows"])
        self.assertEqual(1050.0, cast(list[dict[str, object]], report["rows"])[0]["median_latency_ms"])
        self.assertEqual(900.0, cast(list[dict[str, object]], report["rows"])[0]["first_tap_latency_ms"])
        self.assertEqual(1_200.0, cast(list[dict[str, object]], report["rows"])[0]["warmed_median_latency_ms"])
        self.assertEqual(900.0, summary["first_tap_median_ms"])
        self.assertEqual(1_200.0, summary["warmed_median_ms"])
        self.assertEqual("button-latency-measurements-v1", measurements["schema"])
        self.assertEqual("home-study-cta", cast(list[dict[str, object]], measurements["rows"])[0]["id"])
        self.assertIn("Slow measured controls", markdown)
        self.assertIn("home-study-cta", markdown)


if __name__ == "__main__":
    unittest.main()
