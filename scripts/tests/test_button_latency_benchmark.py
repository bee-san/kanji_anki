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

    def test_find_prelude_control_uses_text_and_selector_fallbacks(self) -> None:
        controls = [
            benchmark.UiControl(
                route="home",
                launch_route="home",
                scroll_position="top",
                index=0,
                text="",
                content_desc="Browse Kanji",
                resource_id="",
                class_name="android.view.View",
                bounds=(10, 20, 210, 120),
            ),
            benchmark.UiControl(
                route="home",
                launch_route="home",
                scroll_position="top",
                index=1,
                text="Close",
                content_desc="",
                resource_id="",
                class_name="android.view.View",
                bounds=(20, 30, 220, 130),
                contract_id="browse-kanji-row-裂",
                contract_title="Browse kanji row",
            ),
        ]

        self.assertIs(controls[0], benchmark._find_prelude_control(controls, benchmark.ScenarioStep("Browse Kanji")))
        self.assertIs(controls[1], benchmark._find_prelude_control(controls, benchmark.ScenarioStep("browse-kanji-row-裂")))

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

    def test_maybe_allow_permission_dialog_taps_allow_button(self) -> None:
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
            dump_timeout_ms=8_000,
            stable_polls=2,
            poll_interval_ms=120,
            include_unsafe=False,
            include_stateful=False,
            max_controls=None,
            routes=("home",),
            scroll_positions=("top",),
            dry_run_inventory=False,
        )
        xml = """
        <hierarchy>
          <node resource-id="com.android.permissioncontroller:id/permission_allow_button" bounds="[100,200][300,400]" />
        </hierarchy>
        """
        calls: list[tuple[str, ...]] = []

        def fake_adb(_config: benchmark.RunConfig, *args: str, **_kwargs: object) -> str:
            calls.append(args)
            return ""

        original_adb = benchmark._adb
        benchmark._adb = fake_adb
        try:
            allowed = benchmark.maybe_allow_permission_dialog(config, xml)
        finally:
            benchmark._adb = original_adb

        self.assertTrue(allowed)
        self.assertEqual(("shell", "input", "tap", "200", "300"), calls[0])

    def test_timed_dump_ui_xml_uses_shell_dump_output_with_status_prefix(self) -> None:
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
            dump_timeout_ms=8_000,
            stable_polls=2,
            poll_interval_ms=120,
            include_unsafe=False,
            include_stateful=False,
            max_controls=None,
            routes=("home",),
            scroll_positions=("top",),
            dry_run_inventory=False,
        )
        calls: list[tuple[str, ...]] = []
        shell_xml = "UI hierchary dumped to: /sdcard/window.xml\n<hierarchy><node text=\"Kani route home\" /></hierarchy>"

        def fake_adb(_config: benchmark.RunConfig, *args: str, **_kwargs: object) -> str:
            calls.append(args)
            if args[:4] == ("shell", "timeout", "-k", "2s"):
                return "UI hierchary dumped to: /sdcard/kani-button-latency.xml"
            if args[:2] == ("shell", "cat"):
                return shell_xml
            if args[:3] == ("shell", "rm", "-f"):
                return ""
            raise AssertionError(args)

        original_adb = benchmark._adb
        benchmark._adb = fake_adb
        try:
            xml, elapsed_ms = benchmark.timed_dump_ui_xml(config)
        finally:
            benchmark._adb = original_adb

        self.assertIn("Kani route home", xml)
        self.assertGreaterEqual(elapsed_ms, 0)
        self.assertTrue(any(call[:4] == ("shell", "timeout", "-k", "2s") for call in calls))
        self.assertTrue(any(call[:2] == ("shell", "cat") for call in calls))
        self.assertFalse(any(call[:3] == ("exec-out", "uiautomator", "dump") for call in calls))

    def test_route_variants_cover_default_matrix_with_route_specific_wait_terms(self) -> None:
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
            routes=benchmark.DEFAULT_ROUTES,
            scroll_positions=benchmark.DEFAULT_SCROLL_POSITIONS,
            dry_run_inventory=False,
        )
        original_logical_screen_height = benchmark.logical_screen_height
        benchmark.logical_screen_height = lambda _config: 2400
        try:
            variants = benchmark.route_variants(config)
        finally:
            benchmark.logical_screen_height = original_logical_screen_height

        by_capture = {variant.capture_id: variant for variant in variants}
        self.assertEqual(len(benchmark.DEFAULT_ROUTES) * len(benchmark.DEFAULT_SCROLL_POSITIONS), len(variants))
        self.assertEqual("settings", by_capture["settings-bottom"].launch_route)
        self.assertEqual(2400 * 2 + benchmark.SETTINGS_BOTTOM_SCROLL_EXTRA, by_capture["settings-bottom"].scroll_y)
        self.assertEqual(("Kani route settings",), by_capture["settings-middle"].expected_terms)
        self.assertEqual(("Games||ゲーム",), by_capture["games-top"].expected_terms)

    def test_route_variants_support_normal_app_scenarios(self) -> None:
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
            routes=("cold-start", "bottom-nav", "study-rating", "browse-detail", "provider-dialog", "settings-targets"),
            scroll_positions=("top", "middle", "bottom"),
            dry_run_inventory=False,
        )
        original_logical_screen_height = benchmark.logical_screen_height
        benchmark.logical_screen_height = lambda _config: 2400
        try:
            variants = benchmark.route_variants(config)
        finally:
            benchmark.logical_screen_height = original_logical_screen_height

        by_capture = {variant.capture_id: variant for variant in variants}
        self.assertEqual(8, len(variants))
        self.assertEqual(benchmark.BENCHMARK_ROUTE_EXTRA, by_capture["cold-start-top"].launch_extra)
        self.assertEqual("home", by_capture["cold-start-top"].launch_route)
        self.assertEqual("bottom-nav", by_capture["bottom-nav-bottom"].route)
        self.assertEqual(2400 * 2, by_capture["bottom-nav-bottom"].scroll_y)
        self.assertEqual("Reveal", by_capture["study-rating-top"].prep_steps[0].label)
        self.assertEqual("Browse Kanji", by_capture["browse-detail-top"].prep_steps[0].label)
        self.assertEqual("Sync", by_capture["provider-dialog-top"].prep_steps[0].label)
        self.assertEqual("settings", by_capture["settings-targets-bottom"].launch_route)
        self.assertEqual(2400 * 2 + benchmark.SETTINGS_BOTTOM_SCROLL_EXTRA, by_capture["settings-targets-bottom"].scroll_y)

    def test_cold_start_suite_expands_to_reproducible_all_button_routes(self) -> None:
        args = benchmark.build_parser().parse_args(
            [
                "--repo-root",
                "/tmp",
                "--apk",
                "/tmp/app.apk",
                "--package",
                "dev.bee.kanjianki",
                "--adb",
                "adb",
                "--aapt",
                "aapt",
                "--suite",
                "cold-start-all-buttons",
                "--routes",
                "home",
            ]
        )

        config = benchmark.config_from_args(args)

        self.assertEqual("cold-start-all-buttons", config.suite)
        self.assertEqual(benchmark.routes_for_suite("cold-start-all-buttons"), config.routes)
        self.assertEqual(len(set(config.routes)), len(config.routes))
        self.assertIn("home", config.routes)
        self.assertIn("cold-start", config.routes)
        self.assertIn("settings-targets", config.routes)

    def test_build_report_includes_reproducible_methodology_for_suite(self) -> None:
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
            routes=benchmark.routes_for_suite("cold-start-all-buttons"),
            scroll_positions=("top", "middle", "bottom"),
            dry_run_inventory=False,
            suite="cold-start-all-buttons",
        )
        row = benchmark.BenchmarkRow(
            control=benchmark.UiControl(
                route="cold-start",
                launch_route="home",
                scroll_position="top",
                index=0,
                text="Browse Kanji",
                content_desc="",
                resource_id="",
                class_name="android.widget.Button",
                bounds=(0, 0, 100, 100),
            ),
            measurements=[benchmark.Measurement(latency_ms=420.0, status="measured", phase="first")],
        )

        report = benchmark.build_report(config, [row])
        methodology = cast(dict[str, object], report["methodology"])
        route_plan = cast(list[dict[str, object]], methodology["route_plan"])
        artifacts = cast(list[dict[str, object]], report["artifacts"])
        markdown = benchmark.render_markdown(report)

        self.assertEqual("cold-start-all-buttons", report["benchmark_suite"])
        self.assertEqual("cold-start-all-buttons", methodology["suite"])
        self.assertGreaterEqual(cast(int, methodology["variant_count"]), 20)
        self.assertTrue(any(route["route"] == "cold-start" for route in route_plan))
        self.assertTrue(any(route["route"] == "home" and route["source"] == "screenshot_fixture" for route in route_plan))
        self.assertTrue(any(artifact["name"] == "measurements_json" for artifact in artifacts))
        self.assertIn("## Methodology", markdown)
        self.assertIn("cold-start-all-buttons", markdown)
        self.assertIn("first repeat force-stops", markdown)
        self.assertIn("button-latency-measurements.json", markdown)

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
        benchmark_variant = benchmark.RouteVariant(
            route="cold-start",
            launch_route="home",
            scroll_position="top",
            scroll_y=0,
            expected_terms=("Kani route home",),
            launch_extra=benchmark.BENCHMARK_ROUTE_EXTRA,
        )
        benchmark_args = benchmark.launch_route_args(config, benchmark_variant)

        self.assertIn("--activity-single-top", args)
        self.assertIn(benchmark.SCREEN_ROUTE_EXTRA, args)
        self.assertIn("home", args)
        self.assertIn(benchmark.BENCHMARK_ROUTE_EXTRA, benchmark_args)
        self.assertIn("home", benchmark_args)

    def test_measure_control_marks_no_visible_change_when_digest_does_not_change(self) -> None:
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
            routes=("cold-start",),
            scroll_positions=("top",),
            dry_run_inventory=False,
        )
        variant = benchmark.RouteVariant(
            route="cold-start",
            launch_route="home",
            scroll_position="top",
            scroll_y=0,
            expected_terms=("Kani route home",),
            launch_extra=benchmark.BENCHMARK_ROUTE_EXTRA,
        )
        control = benchmark.UiControl(
            route="cold-start",
            launch_route="home",
            scroll_position="top",
            index=0,
            text="Browse Kanji",
            content_desc="",
            resource_id="",
            class_name="android.widget.Button",
            bounds=(0, 0, 100, 100),
        )
        xml = "<hierarchy><node text=\"Kani route home\" /></hierarchy>"
        calls: list[tuple[str, ...]] = []

        def fake_launch_route(_config: benchmark.RunConfig, _variant: benchmark.RouteVariant, *, force_stop: bool = True) -> None:
            calls.append(("launch", str(force_stop)))

        def fake_prepare_variant_state(
            _config: benchmark.RunConfig,
            _variant: benchmark.RouteVariant,
            _contract_index: dict[str, dict[str, str]],
        ) -> str:
            calls.append(("prepare",))
            return xml

        def fake_wait_for_stable_ui(_config: benchmark.RunConfig, _started: float) -> tuple[float, float, float, int, str]:
            return 123.0, 130.0, 7.0, 2, benchmark._sha256_text(xml)

        def fake_adb(_config: benchmark.RunConfig, *args: str, **_kwargs: object) -> str:
            calls.append(args)
            return ""

        original_launch_route = benchmark.launch_route
        original_prepare_variant_state = benchmark.prepare_variant_state
        original_wait_for_stable_ui = benchmark.wait_for_stable_ui
        original_adb = benchmark._adb
        benchmark.launch_route = fake_launch_route
        benchmark.prepare_variant_state = fake_prepare_variant_state
        benchmark.wait_for_stable_ui = fake_wait_for_stable_ui
        benchmark._adb = fake_adb
        try:
            measurement = benchmark.measure_control(
                config,
                variant,
                control,
                {},
                phase="first",
                cold_start=True,
            )
        finally:
            benchmark.launch_route = original_launch_route
            benchmark.prepare_variant_state = original_prepare_variant_state
            benchmark.wait_for_stable_ui = original_wait_for_stable_ui
            benchmark._adb = original_adb

        self.assertEqual("measured", measurement.status)
        self.assertEqual("no_visible_change", measurement.transition_kind)
        self.assertTrue(any(call[0] == "launch" for call in calls))
        self.assertTrue(any(call[0] == "prepare" for call in calls))
        self.assertTrue(any(call[:2] == ("shell", "input") for call in calls))

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
        self.assertEqual(0, summary["no_visible_change_rows"])
        self.assertEqual(1, summary["visible_change_rows"])
        self.assertEqual(1050.0, cast(list[dict[str, object]], report["rows"])[0]["median_latency_ms"])
        self.assertEqual(900.0, cast(list[dict[str, object]], report["rows"])[0]["first_tap_latency_ms"])
        self.assertEqual(1_200.0, cast(list[dict[str, object]], report["rows"])[0]["warmed_median_latency_ms"])
        self.assertEqual("visible_change", cast(list[dict[str, object]], report["rows"])[0]["transition_kind"])
        self.assertEqual(900.0, summary["first_tap_median_ms"])
        self.assertEqual(1_200.0, summary["warmed_median_ms"])
        self.assertEqual("button-latency-measurements-v1", measurements["schema"])
        self.assertEqual("home-study-cta", cast(list[dict[str, object]], measurements["rows"])[0]["id"])
        self.assertEqual("visible_change", cast(list[dict[str, object]], measurements["rows"])[0]["transition_kind"])
        self.assertIn("Slow measured controls", markdown)
        self.assertIn("Transition", markdown)
        self.assertIn("visible_change", markdown)
        self.assertIn("home-study-cta", markdown)

    def test_build_report_and_measurements_json_track_no_visible_change_rows(self) -> None:
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
        steady = benchmark.BenchmarkRow(
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
                benchmark.Measurement(
                    latency_ms=420.0,
                    status="measured",
                    phase="first",
                    transition_kind="no_visible_change",
                ),
            ],
        )
        visible = benchmark.BenchmarkRow(
            control=benchmark.UiControl(
                route="home",
                launch_route="home",
                scroll_position="top",
                index=1,
                text="Browse Kanji",
                content_desc="",
                resource_id="",
                class_name="android.widget.Button",
                bounds=(0, 0, 100, 100),
            ),
            measurements=[benchmark.Measurement(latency_ms=920.0, status="measured", phase="first")],
        )

        report = benchmark.build_report(config, [steady, visible])
        measurements = benchmark.build_measurements_json(report)
        markdown = benchmark.render_markdown(report)

        summary = cast(dict[str, object], report["summary"])
        self.assertEqual(2, summary["measured_rows"])
        self.assertEqual(1, summary["no_visible_change_rows"])
        self.assertEqual(1, summary["visible_change_rows"])
        self.assertEqual("no_visible_change", cast(list[dict[str, object]], report["rows"])[0]["transition_kind"])
        self.assertEqual("no_visible_change", cast(list[dict[str, object]], measurements["rows"])[0]["transition_kind"])
        self.assertIn("No visible change rows: 1", markdown)
        self.assertIn("no_visible_change", markdown)


if __name__ == "__main__":
    unittest.main()
