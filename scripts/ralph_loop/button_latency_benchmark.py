#!/usr/bin/env python3

from __future__ import annotations

import argparse
from collections import Counter
import csv
import hashlib
import json
import os
import re
import statistics
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable, Mapping, Sequence, TextIO, cast

from scripts.ralph_loop import screenshot_fixtures

SCHEMA = "button-latency-benchmark-v1"
TIMINGS_SCHEMA = "button-latency-measurements-v1"
DEFAULT_OUT_DIR = Path(".ralph-loop/current/button-latency-benchmark")
DEFAULT_MEASUREMENTS_JSON = Path(".ralph-loop/current/button-latency-measurements.json")
DEFAULT_INVENTORY_JSON = Path(".ralph-loop/current/button-latency-benchmark.json")
DEFAULT_INVENTORY_CSV = Path(".ralph-loop/current/button-latency-benchmark.csv")
DEFAULT_INVENTORY_MD = Path(".ralph-loop/current/button-latency-benchmark.md")
DEFAULT_BUTTON_CONTRACT = Path(".ralph-loop/current/button-contract.json")
DEFAULT_ROUTES = ("home", "study", "stats", "settings", "games")
DEFAULT_SCROLL_POSITIONS = ("top", "middle", "bottom")
DEFAULT_REPEAT_COUNT = 3
DEFAULT_SLOW_THRESHOLD_MS = 1_000
DEFAULT_SETTLE_TIMEOUT_MS = 15_000
DEFAULT_DUMP_TIMEOUT_MS = 8_000
DEFAULT_STABLE_POLLS = 2
DEFAULT_POLL_INTERVAL_MS = 120
BENCHMARK_BROWSE_DETAIL_ROW = "browse-kanji-row-認"
LOCALIZED_ROUTE_TERMS = {
    "study": ("Study||学習",),
    "stats": ("Stats||統計",),
    "settings": ("Settings||設定",),
    "games": ("Games||ゲーム",),
}
SCREEN_ROUTE_EXTRA = "dev.bee.kanjianki.extra.SCREENSHOT_ROUTE"
BENCHMARK_ROUTE_EXTRA = "dev.bee.kanjianki.extra.BENCHMARK_ROUTE"
SCREEN_SCROLL_POSITION_EXTRA = "dev.bee.kanjianki.extra.SCREENSHOT_SCROLL_POSITION"
SCREEN_SCROLL_Y_EXTRA = "dev.bee.kanjianki.extra.SCREENSHOT_SCROLL_Y"
SETTINGS_BOTTOM_SCROLL_EXTRA = 288
ANR_MARKERS = ("isn't responding", "is not responding", "aerr_")
UNSAFE_LABEL_RE = re.compile(
    r"sync ankidroid|ankidroid\s*sync|ankidroidを同期|タップして同期|同期 まだ同期|sync needed|check for updates|"
    r"github updater|install update|open notification settings|notification settings|delete|reset|"
    r"restore defaults|turn off|save ",
    re.IGNORECASE,
)
STATEFUL_CLASS_RE = re.compile(r"switch|checkbox|seekbar|spinner|edittext|radiobutton", re.IGNORECASE)
SLUG_RE = re.compile(r"[^a-z0-9]+")


class ButtonLatencyBenchmarkError(ValueError):
    pass


@dataclass(frozen=True)
class ScenarioStep:
    label: str
    expected_terms: tuple[str, ...] = ()


@dataclass(frozen=True)
class RouteVariant:
    route: str
    launch_route: str
    scroll_position: str
    scroll_y: int
    expected_terms: tuple[str, ...]
    launch_extra: str = SCREEN_ROUTE_EXTRA
    prep_steps: tuple[ScenarioStep, ...] = ()

    @property
    def capture_id(self) -> str:
        return f"{self.route}-{self.scroll_position}"


@dataclass(frozen=True)
class ScenarioSpec:
    launch_route: str
    expected_terms: tuple[str, ...]
    scroll_positions: tuple[str, ...] | None = None
    prep_steps: tuple[ScenarioStep, ...] = ()
    launch_extra: str = BENCHMARK_ROUTE_EXTRA


@dataclass(frozen=True)
class UiControl:
    route: str
    launch_route: str
    scroll_position: str
    index: int
    text: str
    content_desc: str
    resource_id: str
    class_name: str
    bounds: tuple[int, int, int, int]
    enabled: bool = True
    clickable: bool = True
    contract_id: str = ""
    contract_title: str = ""

    @property
    def label(self) -> str:
        return self.text or self.content_desc or _resource_tail(self.resource_id) or self.class_name or "control"

    @property
    def center(self) -> tuple[int, int]:
        left, top, right, bottom = self.bounds
        return (left + right) // 2, (top + bottom) // 2

    @property
    def stable_key(self) -> str:
        return "|".join(
            [
                self.route,
                self.scroll_position,
                self.text,
                self.content_desc,
                self.resource_id,
                self.class_name,
                ",".join(str(value) for value in self.bounds),
            ]
        )

    @property
    def id(self) -> str:
        if self.contract_id:
            return self.contract_id
        return "-".join(
            [
                _slug(self.route),
                _slug(self.scroll_position),
                _slug(self.label),
                str(self.index),
            ]
        )


@dataclass
class Measurement:
    latency_ms: float | None
    status: str
    error: str = ""
    xml_settle_polls: int = 0
    final_xml_sha256: str = ""
    phase: str = "measurement"
    raw_latency_ms: float | None = None
    dump_overhead_ms: float = 0.0
    transition_kind: str = ""


@dataclass
class BenchmarkRow:
    control: UiControl
    measurements: list[Measurement] = field(default_factory=list)
    skip_reason: str = ""

    @property
    def measured_latencies(self) -> list[float]:
        return [cast(float, item.latency_ms) for item in self.measurements if item.latency_ms is not None]

    @property
    def measured_raw_latencies(self) -> list[float]:
        return [
            cast(float, item.raw_latency_ms if item.raw_latency_ms is not None else item.latency_ms)
            for item in self.measurements
            if item.latency_ms is not None
        ]

    @property
    def median_latency_ms(self) -> float | None:
        values = self.measured_latencies
        if not values:
            return None
        return float(statistics.median(values))

    @property
    def first_tap_latency_ms(self) -> float | None:
        for item in self.measurements:
            if item.phase == "first" and item.latency_ms is not None:
                return item.latency_ms
        return self.measured_latencies[0] if self.measured_latencies else None

    @property
    def first_tap_raw_latency_ms(self) -> float | None:
        for item in self.measurements:
            if item.phase == "first" and item.latency_ms is not None:
                return item.raw_latency_ms if item.raw_latency_ms is not None else item.latency_ms
        return self.measured_raw_latencies[0] if self.measured_raw_latencies else None

    @property
    def warmed_latencies(self) -> list[float]:
        return [
            cast(float, item.latency_ms)
            for item in self.measurements
            if item.phase == "warm" and item.latency_ms is not None
        ]

    @property
    def warmed_median_latency_ms(self) -> float | None:
        values = self.warmed_latencies
        return float(statistics.median(values)) if values else None

    @property
    def warmed_raw_median_latency_ms(self) -> float | None:
        values = [
            cast(float, item.raw_latency_ms if item.raw_latency_ms is not None else item.latency_ms)
            for item in self.measurements
            if item.phase == "warm" and item.latency_ms is not None
        ]
        return float(statistics.median(values)) if values else None

    @property
    def median_raw_latency_ms(self) -> float | None:
        values = self.measured_raw_latencies
        return float(statistics.median(values)) if values else None

    @property
    def median_dump_overhead_ms(self) -> float | None:
        values = [item.dump_overhead_ms for item in self.measurements if item.latency_ms is not None]
        return float(statistics.median(values)) if values else None

    @property
    def min_latency_ms(self) -> float | None:
        values = self.measured_latencies
        return min(values) if values else None

    @property
    def max_latency_ms(self) -> float | None:
        values = self.measured_latencies
        return max(values) if values else None

    @property
    def transition_kind(self) -> str:
        kinds = [item.transition_kind or "visible_change" for item in self.measurements if item.latency_ms is not None]
        if not kinds:
            return ""
        counts = Counter(kinds)
        if len(counts) == 1:
            return kinds[0]
        return "mixed"

    @property
    def status(self) -> str:
        if self.skip_reason:
            return "skipped"
        if self.measured_latencies:
            return "measured"
        if self.measurements:
            return self.measurements[-1].status
        return "pending"


@dataclass(frozen=True)
class RunConfig:
    repo_root: Path
    adb: str
    aapt: str
    apk_path: Path
    package_name: str
    out_dir: Path
    repeat_count: int
    slow_threshold_ms: int
    settle_timeout_ms: int
    stable_polls: int
    poll_interval_ms: int
    include_unsafe: bool
    include_stateful: bool
    max_controls: int | None
    routes: tuple[str, ...]
    scroll_positions: tuple[str, ...]
    dry_run_inventory: bool
    dump_timeout_ms: int = DEFAULT_DUMP_TIMEOUT_MS
    suite: str = ""


NORMAL_APP_SCENARIOS: dict[str, ScenarioSpec] = {
    "cold-start": ScenarioSpec(
        launch_route="home",
        expected_terms=("Kani route home",),
        scroll_positions=("top",),
    ),
    "bottom-nav": ScenarioSpec(
        launch_route="home",
        expected_terms=("Kani route home",),
        scroll_positions=("bottom",),
    ),
    "study-reveal": ScenarioSpec(
        launch_route="study",
        expected_terms=("Study||学習", "Reveal"),
        scroll_positions=("top",),
    ),
    "study-rating": ScenarioSpec(
        launch_route="study",
        expected_terms=("Study||学習", "Reveal"),
        scroll_positions=("top",),
        prep_steps=(ScenarioStep("Reveal", ("Again||Good",)),),
    ),
    "study-close": ScenarioSpec(
        launch_route="study",
        expected_terms=("Study||学習",),
        scroll_positions=("top",),
    ),
    "browse-detail": ScenarioSpec(
        launch_route="home",
        expected_terms=("Kani route home",),
        scroll_positions=("top",),
        prep_steps=(ScenarioStep("Browse Kanji", ("Search",)),),
    ),
    "browse-back": ScenarioSpec(
        launch_route="home",
        expected_terms=("Kani route home",),
        scroll_positions=("top",),
        prep_steps=(
            ScenarioStep("Browse Kanji", ("Search",)),
            ScenarioStep(BENCHMARK_BROWSE_DETAIL_ROW, ("Back to study||Review now",)),
        ),
    ),
    "provider-dialog": ScenarioSpec(
        launch_route="home",
        expected_terms=("Kani route home",),
        scroll_positions=("top",),
        prep_steps=(
            ScenarioStep(
                "Sync",
                (
                    "Install AnkiDroid||Grant permission||Review import settings||Fix permission||Try sync again||Sync again||Sync cards||Syncing AnkiDroid",
                ),
            ),
        ),
    ),
    "settings-targets": ScenarioSpec(
        launch_route="settings",
        expected_terms=("Kani route settings",),
        scroll_positions=("top", "middle", "bottom"),
    ),
}

DEFAULT_SUITE = "screenshot-all-buttons"
COLD_START_ALL_BUTTONS_SUITE = "cold-start-all-buttons"
BENCHMARK_SUITES: dict[str, tuple[str, ...]] = {
    DEFAULT_SUITE: DEFAULT_ROUTES,
    COLD_START_ALL_BUTTONS_SUITE: (*DEFAULT_ROUTES, *tuple(NORMAL_APP_SCENARIOS)),
}
SUITE_DESCRIPTIONS = {
    DEFAULT_SUITE: "screenshot fixture routes across top/middle/bottom scroll positions",
    COLD_START_ALL_BUTTONS_SUITE: (
        "screenshot fixture routes plus normal-app cold-start, navigation, study, browse, provider, and settings scenarios"
    ),
}


def _slug(value: str) -> str:
    return SLUG_RE.sub("-", value.lower()).strip("-") or "control"


def _resource_tail(value: str) -> str:
    if not value:
        return ""
    return value.rsplit("/", 1)[-1]


def _run(command: Sequence[str], *, timeout_seconds: float = 120, cwd: Path | None = None) -> str:
    result = subprocess.run(command, check=False, text=True, capture_output=True, timeout=timeout_seconds, cwd=cwd)
    if result.returncode != 0:
        details = (result.stderr or result.stdout or "").strip()
        raise ButtonLatencyBenchmarkError(f"command failed ({result.returncode}): {' '.join(command)}\n{details}")
    return result.stdout.strip()


def _adb(config: RunConfig, *args: str, timeout_seconds: float = 120) -> str:
    return _run([config.adb, *args], timeout_seconds=timeout_seconds)


def find_aapt(sdk_root: Path | None = None) -> str:
    configured = os.environ.get("AAPT")
    if configured:
        return configured
    for candidate in _path_candidates("aapt"):
        return candidate
    sdk = sdk_root or Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or "")
    if sdk and sdk.exists():
        candidates = sorted(sdk.glob("build-tools/*/aapt"))
        if candidates:
            return str(candidates[-1])
    raise ButtonLatencyBenchmarkError("aapt not found; install Android build-tools or set AAPT")


def find_adb() -> str:
    configured = os.environ.get("ADB")
    if configured:
        return configured
    for candidate in _path_candidates("adb"):
        return candidate
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or "")
    candidate = sdk / "platform-tools" / "adb"
    if candidate.exists():
        return str(candidate)
    raise ButtonLatencyBenchmarkError("adb not found; put platform-tools on PATH or set ADB")


def _path_candidates(name: str) -> Iterable[str]:
    for directory in os.environ.get("PATH", "").split(os.pathsep):
        if not directory:
            continue
        candidate = Path(directory) / name
        if candidate.exists() and os.access(candidate, os.X_OK):
            yield str(candidate)


def find_debug_apk(repo_root: Path) -> Path:
    apk_root = repo_root / "app" / "build" / "outputs" / "apk"
    candidates = sorted(
        path
        for path in apk_root.rglob("*debug*.apk")
        if "androidTest" not in path.name and "androidTest" not in path.parts
    )
    if not candidates:
        raise ButtonLatencyBenchmarkError(
            "No debug APK found under app/build/outputs/apk; run ./gradlew :app:assembleDebug first"
        )
    return candidates[0]


def package_name_from_apk(aapt: str, apk_path: Path) -> str:
    output = _run([aapt, "dump", "badging", str(apk_path)], timeout_seconds=120)
    match = re.search(r"package: name='([^']+)'", output)
    if not match:
        raise ButtonLatencyBenchmarkError(f"Could not derive package name from {apk_path}")
    return match.group(1)


def route_variants(config: RunConfig) -> list[RouteVariant]:
    fixtures = _fixtures_by_route()
    variants: list[RouteVariant] = []
    for route in config.routes:
        if route in fixtures:
            fixture = fixtures[route]
            launch_route = str(fixture["launch_route"])
            launch_extra = SCREEN_ROUTE_EXTRA
            prep_steps: tuple[ScenarioStep, ...] = ()
            expected_terms = tuple(str(term) for term in cast(list[object], fixture.get("expected_terms", [])))
            route_markers = tuple(term for term in expected_terms if term.startswith("Kani route "))
            route_expected_terms = LOCALIZED_ROUTE_TERMS.get(route)
            scroll_positions = config.scroll_positions
            row_expected_terms = route_markers or route_expected_terms or expected_terms or (route,)
        elif route in NORMAL_APP_SCENARIOS:
            scenario = NORMAL_APP_SCENARIOS[route]
            launch_route = scenario.launch_route
            launch_extra = scenario.launch_extra
            prep_steps = scenario.prep_steps
            scroll_positions = scenario.scroll_positions or config.scroll_positions
            row_expected_terms = scenario.expected_terms or (route,)
        else:
            known_routes = ", ".join(sorted(fixtures))
            known_scenarios = ", ".join(sorted(NORMAL_APP_SCENARIOS))
            raise ButtonLatencyBenchmarkError(
                f"Unsupported route '{route}'. Known screenshot routes: {known_routes}; normal-app scenarios: {known_scenarios}"
            )
        for scroll_position in scroll_positions:
            variants.append(
                RouteVariant(
                    route=route,
                    launch_route=launch_route,
                    scroll_position=scroll_position,
                    scroll_y=scroll_y_for_position(config, launch_route, scroll_position),
                    expected_terms=row_expected_terms,
                    launch_extra=launch_extra,
                    prep_steps=prep_steps,
                )
            )
    return variants


def _fixtures_by_route() -> dict[str, dict[str, object]]:
    registry = screenshot_fixtures.fixture_registry()
    return {str(fixture["route"]): fixture for fixture in cast(list[dict[str, object]], registry["fixtures"])}


def routes_for_suite(suite: str) -> tuple[str, ...]:
    try:
        return BENCHMARK_SUITES[suite]
    except KeyError as exc:
        known = ", ".join(sorted(BENCHMARK_SUITES))
        raise ButtonLatencyBenchmarkError(f"Unsupported benchmark suite '{suite}'. Known suites: {known}") from exc


def route_plan_from_config(config: RunConfig) -> list[dict[str, object]]:
    fixtures = _fixtures_by_route()
    plan: list[dict[str, object]] = []
    for route in config.routes:
        if route in fixtures:
            fixture = fixtures[route]
            expected_terms = tuple(str(term) for term in cast(list[object], fixture.get("expected_terms", [])))
            route_markers = tuple(term for term in expected_terms if term.startswith("Kani route "))
            row_expected_terms = route_markers or LOCALIZED_ROUTE_TERMS.get(route) or expected_terms or (route,)
            plan.append(
                {
                    "route": route,
                    "source": "screenshot_fixture",
                    "launch_route": str(fixture["launch_route"]),
                    "launch_extra": SCREEN_ROUTE_EXTRA,
                    "scroll_positions": list(config.scroll_positions),
                    "expected_terms": list(row_expected_terms),
                    "prep_steps": [],
                }
            )
        elif route in NORMAL_APP_SCENARIOS:
            scenario = NORMAL_APP_SCENARIOS[route]
            plan.append(
                {
                    "route": route,
                    "source": "normal_app_scenario",
                    "launch_route": scenario.launch_route,
                    "launch_extra": scenario.launch_extra,
                    "scroll_positions": list(scenario.scroll_positions or config.scroll_positions),
                    "expected_terms": list(scenario.expected_terms),
                    "prep_steps": [
                        {"label": step.label, "expected_terms": list(step.expected_terms)} for step in scenario.prep_steps
                    ],
                }
            )
        else:
            known_routes = ", ".join(sorted(fixtures))
            known_scenarios = ", ".join(sorted(NORMAL_APP_SCENARIOS))
            raise ButtonLatencyBenchmarkError(
                f"Unsupported route '{route}'. Known screenshot routes: {known_routes}; normal-app scenarios: {known_scenarios}"
            )
    return plan


def build_artifact_plan(config: RunConfig, artifacts: Mapping[str, Path | str] | None = None) -> list[dict[str, str]]:
    paths: dict[str, Path | str] = {
        "benchmark_json": DEFAULT_INVENTORY_JSON,
        "benchmark_csv": DEFAULT_INVENTORY_CSV,
        "benchmark_markdown": DEFAULT_INVENTORY_MD,
        "measurements_json": DEFAULT_MEASUREMENTS_JSON,
    }
    if artifacts:
        paths.update(artifacts)
    schemas = {
        "benchmark_json": SCHEMA,
        "benchmark_csv": "csv",
        "benchmark_markdown": "markdown",
        "measurements_json": TIMINGS_SCHEMA,
    }
    purposes = {
        "benchmark_json": "full per-control benchmark report with raw and adjusted settle timings",
        "benchmark_csv": "spreadsheet-friendly per-control benchmark rows",
        "benchmark_markdown": "human-readable benchmark summary and slow-control table",
        "measurements_json": "portable timing input for button_latency_inventory.py",
    }
    return [
        {
            "name": name,
            "path": _artifact_path_label(config.repo_root, path),
            "schema": schemas[name],
            "purpose": purposes[name],
        }
        for name, path in paths.items()
    ]


def _artifact_path_label(repo_root: Path, path: Path | str) -> str:
    artifact_path = path if isinstance(path, Path) else Path(path)
    return _relative_or_abs(repo_root, artifact_path if artifact_path.is_absolute() else repo_root / artifact_path)


def build_methodology(config: RunConfig, artifacts: list[dict[str, str]]) -> dict[str, object]:
    route_plan = route_plan_from_config(config)
    return {
        "suite": config.suite or "custom",
        "suite_description": SUITE_DESCRIPTIONS.get(config.suite, "custom route list supplied by --routes"),
        "route_plan": route_plan,
        "variant_count": sum(len(cast(list[object], item.get("scroll_positions", []))) for item in route_plan),
        "cold_start_policy": {
            "first_tap_phase": "force-stop app, relaunch target route, prepare state, then tap once",
            "warm_phase": "reuse the installed app with --activity-single-top reroutes before repeated taps",
            "reported_latency": "adb input tap to XML-stable settle time minus measured uiautomator dump overhead",
        },
        "device_prep": [
            "install the debug APK unless --skip-install is passed",
            "wait for the package to be visible",
            "disable system window/transition/animator animation scales",
            "grant POST_NOTIFICATIONS when available so permission prompts do not pollute timings",
        ],
        "inventory_pipeline": [
            "Run ui_manifest.py and button_contract.py to create .ralph-loop/current/ui-manifest.json and button-contract.json.",
            "Run button_latency_benchmark.py with this suite to create the benchmark and measurements artifacts.",
            "Run button_latency_inventory.py with --timings .ralph-loop/current/button-latency-measurements.json to rank every contracted button by risk and timing evidence.",
        ],
        "artifacts": artifacts,
    }


def scroll_y_for_position(config: RunConfig, route: str, position: str) -> int:
    if re.fullmatch(r"-?\d+", position):
        return int(position)
    height = logical_screen_height(config)
    if position == "top":
        value = 0
    elif position == "middle":
        value = height
    elif position == "bottom":
        value = height * 2
    else:
        raise ButtonLatencyBenchmarkError(f"Unsupported scroll position '{position}'")
    if route == "settings" and position == "bottom":
        value += SETTINGS_BOTTOM_SCROLL_EXTRA
    return value


def logical_screen_height(config: RunConfig) -> int:
    output = _adb(config, "shell", "wm", "size")
    match = re.search(r"Physical size:\s*(\d+)x(\d+)", output)
    if not match:
        return 2400
    return int(match.group(2))


def install_apk(config: RunConfig) -> None:
    _adb(config, "wait-for-device", timeout_seconds=180)
    _adb(config, "install", "-r", "-d", str(config.apk_path), timeout_seconds=240)
    wait_for_package(config)
    prepare_benchmark_device(config)


def prepare_benchmark_device(config: RunConfig) -> None:
    wait_for_package(config)
    disable_animations(config)
    grant_benchmark_permissions(config)


def grant_benchmark_permissions(config: RunConfig) -> None:
    for permission in ("android.permission.POST_NOTIFICATIONS",):
        try:
            _adb(config, "shell", "pm", "grant", config.package_name, permission, timeout_seconds=10)
        except ButtonLatencyBenchmarkError:
            pass


def wait_for_package(config: RunConfig) -> None:
    deadline = time.perf_counter() + 20.0
    last_error = ""
    while time.perf_counter() < deadline:
        try:
            path = _adb(config, "shell", "pm", "path", config.package_name, timeout_seconds=30).strip()
            if path.startswith("package:"):
                return
        except ButtonLatencyBenchmarkError as exc:
            last_error = str(exc)
        time.sleep(0.5)
    raise ButtonLatencyBenchmarkError(f"package {config.package_name} was not visible after install: {last_error}")


def disable_animations(config: RunConfig) -> None:
    for key in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        try:
            _adb(config, "shell", "settings", "put", "global", key, "0")
        except ButtonLatencyBenchmarkError:
            pass


def wait_for_process_exit(config: RunConfig) -> None:
    deadline = time.perf_counter() + 5.0
    while time.perf_counter() < deadline:
        try:
            output = _adb(config, "shell", "pidof", config.package_name, timeout_seconds=30)
        except ButtonLatencyBenchmarkError:
            return
        if not output.strip():
            return
        time.sleep(0.2)


def launch_route(config: RunConfig, variant: RouteVariant, *, force_stop: bool = True) -> None:
    if force_stop:
        _adb(config, "shell", "am", "force-stop", config.package_name)
        wait_for_process_exit(config)
    _adb(config, *launch_route_args(config, variant), timeout_seconds=120)


def launch_route_args(config: RunConfig, variant: RouteVariant) -> tuple[str, ...]:
    return (
        "shell",
        "am",
        "start",
        "--activity-single-top",
        "-n",
        f"{config.package_name}/.MainActivity",
        "--es",
        variant.launch_extra,
        variant.launch_route,
        "--es",
        SCREEN_SCROLL_POSITION_EXTRA,
        variant.scroll_position,
        "--ei",
        SCREEN_SCROLL_Y_EXTRA,
        str(variant.scroll_y),
    )


def timed_dump_ui_xml(config: RunConfig) -> tuple[str, float]:
    return timed_dump_ui_xml_via_shell(config)


def timed_dump_ui_xml_via_shell(config: RunConfig, *, prior_error: str = "") -> tuple[str, float]:
    dump_timeout_seconds = max(1.0, config.dump_timeout_ms / 1_000.0)
    device_path = f"/sdcard/kani-button-latency-{os.getpid()}.xml"
    started = time.perf_counter()
    try:
        timeout_value = f"{max(1, int(dump_timeout_seconds))}s"
        dump_output = _adb(
            config,
            "shell",
            "timeout",
            "-k",
            "2s",
            timeout_value,
            "uiautomator",
            "dump",
            device_path,
            timeout_seconds=dump_timeout_seconds + 5.0,
        )
        xml = _adb(config, "shell", "cat", device_path, timeout_seconds=dump_timeout_seconds)
        elapsed_ms = (time.perf_counter() - started) * 1_000.0
        parsed = _extract_hierarchy_xml(xml)
        if parsed:
            return parsed, elapsed_ms
        last_error = f"uiautomator shell dump returned no XML: {dump_output[:120]} {xml[:120]}".strip()
    except (ButtonLatencyBenchmarkError, subprocess.TimeoutExpired) as exc:
        last_error = str(exc)
    finally:
        try:
            _adb(config, "shell", "rm", "-f", device_path, timeout_seconds=2)
        except (ButtonLatencyBenchmarkError, subprocess.TimeoutExpired):
            pass
    if prior_error:
        last_error = f"{prior_error}; fallback: {last_error}"
    raise ButtonLatencyBenchmarkError(f"uiautomator dump failed after retries: {last_error}")


def _extract_hierarchy_xml(xml: str) -> str:
    xml_start = xml.find("<?xml")
    if xml_start < 0:
        xml_start = xml.find("<hierarchy")
    if xml_start < 0:
        return ""
    xml_end = xml.rfind("</hierarchy>")
    return xml[xml_start : xml_end + len("</hierarchy>")] if xml_end >= 0 else xml[xml_start:]


def dump_ui_xml(config: RunConfig) -> str:
    xml, _elapsed_ms = timed_dump_ui_xml(config)
    return xml


def wait_for_terms(config: RunConfig, expected_terms: Sequence[str], context: str) -> str:
    deadline = time.perf_counter() + (config.settle_timeout_ms / 1_000.0)
    last_xml = ""
    last_error = ""
    while time.perf_counter() < deadline:
        try:
            xml = dump_ui_xml(config)
        except ButtonLatencyBenchmarkError as exc:
            last_error = str(exc)
            time.sleep(config.poll_interval_ms / 1_000.0)
            continue
        last_xml = xml
        if maybe_allow_permission_dialog(config, xml):
            last_error = "dismissed permission prompt"
            time.sleep(0.5)
            continue
        _raise_on_anr(xml, context)
        lower = xml.lower()
        if all(_matches_expected_term(lower, term) for term in expected_terms):
            return xml
        time.sleep(config.poll_interval_ms / 1_000.0)
    raise ButtonLatencyBenchmarkError(
        f"Timed out waiting for {context}; expected terms: {', '.join(expected_terms)}; "
        f"last UI digest: {_sha256_text(last_xml)}; last dump error: {last_error}"
    )


def wait_for_route(config: RunConfig, variant: RouteVariant) -> str:
    return wait_for_terms(config, variant.expected_terms, variant.capture_id)


def prepare_variant_state(
    config: RunConfig,
    variant: RouteVariant,
    contract_index: Mapping[str, dict[str, str]],
) -> str:
    xml = wait_for_route(config, variant)
    if not variant.prep_steps:
        return xml
    return apply_prelude_steps(config, variant, contract_index, xml)


def apply_prelude_steps(
    config: RunConfig,
    variant: RouteVariant,
    contract_index: Mapping[str, dict[str, str]],
    xml: str,
) -> str:
    current_xml = xml
    for step in variant.prep_steps:
        controls = parse_controls(current_xml, variant, contract_index)
        control = _find_prelude_control(controls, step)
        x, y = control.center
        _adb(config, "shell", "input", "tap", str(x), str(y), timeout_seconds=30)
        step_terms = step.expected_terms or variant.expected_terms
        current_xml = wait_for_terms(config, step_terms, f"{variant.capture_id}:{step.label}")
    return current_xml


def _find_prelude_control(controls: Sequence[UiControl], step: ScenarioStep) -> UiControl:
    normalized_step = _normalize_label(step.label)
    scored: list[tuple[int, UiControl]] = []
    for control in controls:
        candidates = (
            control.label,
            control.text,
            control.content_desc,
            control.contract_id,
            control.contract_title,
            _resource_tail(control.resource_id),
        )
        normalized_candidates = [_normalize_label(candidate) for candidate in candidates if candidate]
        if normalized_step in normalized_candidates:
            return control
        if any(normalized_step in candidate or candidate in normalized_step for candidate in normalized_candidates):
            scored.append((len(control.label), control))
    if scored:
        scored.sort(key=lambda item: item[0])
        return scored[0][1]
    raise ButtonLatencyBenchmarkError(f"Could not find control for prelude step '{step.label}'")


def _matches_expected_term(xml_lower: str, term: str) -> bool:
    return any(option.strip().lower() in xml_lower for option in term.split("||") if option.strip())


def maybe_allow_permission_dialog(config: RunConfig, xml: str) -> bool:
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        return False
    for node in root.iter("node"):
        resource_id = node.attrib.get("resource-id", "")
        if not resource_id.endswith("permission_allow_button"):
            continue
        bounds = parse_bounds(node.attrib.get("bounds", ""))
        if not bounds:
            return False
        left, top, right, bottom = bounds
        _adb(config, "shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2), timeout_seconds=10)
        return True
    return False


def _raise_on_anr(xml: str, context: str) -> None:
    lower = xml.lower()
    if any(marker in lower for marker in ANR_MARKERS):
        raise ButtonLatencyBenchmarkError(f"Android ANR/dialog detected while waiting for {context}")


def parse_bounds(value: str) -> tuple[int, int, int, int] | None:
    match = re.fullmatch(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]", value.strip())
    if not match:
        return None
    left, top, right, bottom = (int(group) for group in match.groups())
    if right <= left or bottom <= top:
        return None
    return left, top, right, bottom


def parse_controls(xml: str, variant: RouteVariant, contract_index: Mapping[str, dict[str, str]] | None = None) -> list[UiControl]:
    contract_index = contract_index or {}
    controls: list[UiControl] = []
    seen: set[str] = set()
    root = ET.fromstring(xml)
    for node in root.iter("node"):
        clickable = node.attrib.get("clickable") == "true"
        enabled = node.attrib.get("enabled", "true") == "true"
        if not clickable or not enabled:
            continue
        bounds = parse_bounds(node.attrib.get("bounds", ""))
        if bounds is None:
            continue
        control = UiControl(
            route=variant.route,
            launch_route=variant.launch_route,
            scroll_position=variant.scroll_position,
            index=len(controls),
            text=_node_label(node, "text"),
            content_desc=_node_label(node, "content-desc"),
            resource_id=node.attrib.get("resource-id", ""),
            class_name=node.attrib.get("class", ""),
            bounds=bounds,
        )
        key = control.stable_key
        if key in seen:
            continue
        seen.add(key)
        controls.append(_with_contract(control, contract_index))
    return controls


def _node_label(node: ET.Element, attribute: str) -> str:
    own = node.attrib.get(attribute, "").strip()
    if own:
        return own
    values: list[str] = []
    for child in node.iter("node"):
        if child is node:
            continue
        value = child.attrib.get(attribute, "").strip()
        if value and value not in values:
            values.append(value)
        if len(values) >= 3:
            break
    return " ".join(values)


def _with_contract(control: UiControl, contract_index: Mapping[str, dict[str, str]]) -> UiControl:
    for candidate in (control.text, control.content_desc, _resource_tail(control.resource_id)):
        normalized = _normalize_label(candidate)
        if normalized and normalized in contract_index:
            row = contract_index[normalized]
            return UiControl(
                route=control.route,
                launch_route=control.launch_route,
                scroll_position=control.scroll_position,
                index=control.index,
                text=control.text,
                content_desc=control.content_desc,
                resource_id=control.resource_id,
                class_name=control.class_name,
                bounds=control.bounds,
                enabled=control.enabled,
                clickable=control.clickable,
                contract_id=row.get("id", ""),
                contract_title=row.get("title", ""),
            )
    return control


def _normalize_label(value: str) -> str:
    return re.sub(r"\s+", " ", value.strip()).lower()


def contract_label_index(path: Path | None) -> dict[str, dict[str, str]]:
    if path is None or not path.exists():
        return {}
    data = json.loads(path.read_text(encoding="utf-8"))
    if data.get("schema") != "button-contract-v1":
        raise ButtonLatencyBenchmarkError(f"button contract must use schema button-contract-v1: {path}")
    index: dict[str, dict[str, str]] = {}
    for row in cast(list[dict[str, object]], data.get("rows", [])):
        row_info = {"id": str(row.get("id", "")), "title": str(row.get("title", ""))}
        for label in cast(list[object], row.get("labels", [])):
            normalized = _normalize_label(str(label))
            if normalized and normalized not in index:
                index[normalized] = row_info
    return index


def skip_reason(control: UiControl, *, include_unsafe: bool, include_stateful: bool) -> str:
    haystack = " ".join([control.text, control.content_desc, control.resource_id, control.class_name])
    if not include_unsafe and UNSAFE_LABEL_RE.search(haystack):
        return "unsafe_or_external_side_effect"
    if not include_stateful and STATEFUL_CLASS_RE.search(control.class_name):
        return "stateful_input"
    return ""


def discover_controls(config: RunConfig, contract_index: Mapping[str, dict[str, str]]) -> list[BenchmarkRow]:
    rows: list[BenchmarkRow] = []
    for variant in route_variants(config):
        _progress(f"discover {variant.capture_id}")
        launch_route(config, variant)
        xml = prepare_variant_state(config, variant, contract_index)
        controls = parse_controls(xml, variant, contract_index)
        for control in controls:
            rows.append(
                BenchmarkRow(
                    control=control,
                    skip_reason=skip_reason(
                        control,
                        include_unsafe=config.include_unsafe,
                        include_stateful=config.include_stateful,
                    ),
                )
            )
    return rows[: config.max_controls] if config.max_controls else rows


def measure_rows(config: RunConfig, rows: list[BenchmarkRow], contract_index: Mapping[str, dict[str, str]] | None = None) -> None:
    contract_index = contract_index or {}
    variants = {variant.capture_id: variant for variant in route_variants(config)}
    measureable = [row for row in rows if not row.skip_reason and not config.dry_run_inventory]
    for measured_index, row in enumerate(measureable, start=1):
        _progress(f"measure {measured_index}/{len(measureable)} {row.control.id}")
        variant = variants[f"{row.control.route}-{row.control.scroll_position}"]
        for attempt in range(config.repeat_count):
            phase = "first" if attempt == 0 else "warm"
            row.measurements.append(
                measure_control(config, variant, row.control, contract_index, phase=phase, cold_start=attempt == 0)
            )


def measure_control(
    config: RunConfig,
    variant: RouteVariant,
    control: UiControl,
    contract_index: Mapping[str, dict[str, str]] | None = None,
    *,
    phase: str,
    cold_start: bool,
) -> Measurement:
    try:
        contract_index = contract_index or {}
        launch_route(config, variant, force_stop=cold_start)
        xml = prepare_variant_state(config, variant, contract_index)
        pre_tap_digest = _sha256_text(xml)
        x, y = control.center
        started = time.perf_counter()
        _adb(config, "shell", "input", "tap", str(x), str(y), timeout_seconds=30)
        latency_ms, raw_latency_ms, dump_overhead_ms, polls, xml_sha = wait_for_stable_ui(config, started)
        transition_kind = "no_visible_change" if xml_sha == pre_tap_digest else "visible_change"
        return Measurement(
            latency_ms=latency_ms,
            status="measured",
            xml_settle_polls=polls,
            final_xml_sha256=xml_sha,
            phase=phase,
            raw_latency_ms=raw_latency_ms,
            dump_overhead_ms=dump_overhead_ms,
            transition_kind=transition_kind,
        )
    except Exception as exc:  # pragma: no cover - exercised on real devices.
        return Measurement(latency_ms=None, status="error", error=str(exc), phase=phase)


def wait_for_stable_ui(config: RunConfig, started: float) -> tuple[float, float, float, int, str]:
    deadline = started + (config.settle_timeout_ms / 1_000.0)
    last_digest = ""
    stable_count = 0
    polls = 0
    last_xml = ""
    dump_overhead_ms = 0.0
    while time.perf_counter() < deadline:
        try:
            xml, dump_elapsed_ms = timed_dump_ui_xml(config)
            dump_overhead_ms += dump_elapsed_ms
        except ButtonLatencyBenchmarkError:
            time.sleep(config.poll_interval_ms / 1_000.0)
            continue
        polls += 1
        last_xml = xml
        _raise_on_anr(xml, "button click")
        digest = _stable_ui_digest(xml)
        if digest == last_digest:
            stable_count += 1
        else:
            stable_count = 1
            last_digest = digest
        if stable_count >= config.stable_polls:
            raw_latency_ms = (time.perf_counter() - started) * 1_000.0
            adjusted_latency_ms = max(0.0, raw_latency_ms - dump_overhead_ms)
            return adjusted_latency_ms, raw_latency_ms, dump_overhead_ms, polls, _sha256_text(xml)
        time.sleep(config.poll_interval_ms / 1_000.0)
    raw_latency_ms = (time.perf_counter() - started) * 1_000.0
    adjusted_latency_ms = max(0.0, raw_latency_ms - dump_overhead_ms)
    return adjusted_latency_ms, raw_latency_ms, dump_overhead_ms, polls, _sha256_text(last_xml)


def _stable_ui_digest(xml: str) -> str:
    # Bounds and selected/focused state are useful settle signals; strip only whitespace between attrs.
    return hashlib.sha256(re.sub(r"\s+", " ", xml).encode("utf-8", errors="ignore")).hexdigest()


def _progress(message: str) -> None:
    print(f"[button-latency] {message}", file=sys.stderr, flush=True)


def _sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8", errors="ignore")).hexdigest()


def build_report(
    config: RunConfig,
    rows: list[BenchmarkRow],
    artifacts: Mapping[str, Path | str] | None = None,
) -> dict[str, object]:
    measured = [row for row in rows if row.median_latency_ms is not None]
    latencies = [cast(float, row.median_latency_ms) for row in measured]
    slow_rows = [row for row in measured if cast(float, row.median_latency_ms) > config.slow_threshold_ms]
    transition_kinds = [row.transition_kind or "visible_change" for row in measured]
    artifact_plan = build_artifact_plan(config, artifacts)
    return {
        "schema": SCHEMA,
        "generated_at": datetime.now(timezone.utc).isoformat(),
        "repo_head": _git_output(config.repo_root, "rev-parse", "HEAD"),
        "repo_branch": _git_output(config.repo_root, "branch", "--show-current"),
        "apk_path": _relative_or_abs(config.repo_root, config.apk_path),
        "package_name": config.package_name,
        "device": _device_info(config),
        "benchmark_suite": config.suite or "custom",
        "routes": list(config.routes),
        "scroll_positions": list(config.scroll_positions),
        "repeat_count": config.repeat_count,
        "slow_threshold_ms": config.slow_threshold_ms,
        "settle_timeout_ms": config.settle_timeout_ms,
        "dump_timeout_ms": config.dump_timeout_ms,
        "measurement_model": "adb input tap to repeated uiautomator XML-stable settle; row latency is raw settle time minus measured uiautomator dump overhead, with raw fields retained for audit; coarse emulator timing, not frame-perfect tracing",
        "methodology": build_methodology(config, artifact_plan),
        "artifacts": artifact_plan,
        "summary": {
            "row_count": len(rows),
            "measured_rows": len(measured),
            "skipped_rows": sum(1 for row in rows if row.skip_reason),
            "error_rows": sum(1 for row in rows if row.status == "error"),
            "slow_rows": len(slow_rows),
            "no_visible_change_rows": sum(1 for kind in transition_kinds if kind == "no_visible_change"),
            "visible_change_rows": sum(1 for kind in transition_kinds if kind == "visible_change"),
            "median_ms": _median_or_none(latencies),
            "first_tap_median_ms": _median_or_none(
                [cast(float, row.first_tap_latency_ms) for row in measured if row.first_tap_latency_ms is not None]
            ),
            "warmed_median_ms": _median_or_none(
                [cast(float, row.warmed_median_latency_ms) for row in measured if row.warmed_median_latency_ms is not None]
            ),
            "p95_ms": _percentile_or_none(latencies, 95),
            "max_ms": max(latencies) if latencies else None,
        },
        "rows": [row_to_dict(row, config) for row in rows],
    }


def _git_output(repo_root: Path, *args: str) -> str:
    try:
        return _run(["git", *args], timeout_seconds=30, cwd=repo_root) if repo_root.exists() else ""
    except Exception:
        return ""


def _device_info(config: RunConfig) -> dict[str, str]:
    def shell_getprop(name: str) -> str:
        try:
            return _adb(config, "shell", "getprop", name, timeout_seconds=30).strip()
        except Exception:
            return ""

    try:
        adb_serials = _run([config.adb, "devices"], timeout_seconds=30)
    except Exception:
        adb_serials = ""
    return {
        "adb_serials": adb_serials,
        "model": shell_getprop("ro.product.model"),
        "sdk": shell_getprop("ro.build.version.sdk"),
        "fingerprint": shell_getprop("ro.build.fingerprint"),
    }


def _relative_or_abs(root: Path, path: Path) -> str:
    try:
        return path.resolve().relative_to(root.resolve()).as_posix()
    except ValueError:
        return str(path)


def _median_or_none(values: list[float]) -> float | None:
    return float(statistics.median(values)) if values else None


def _percentile_or_none(values: list[float], percentile: int) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    index = max(0, min(len(ordered) - 1, round((percentile / 100.0) * (len(ordered) - 1))))
    return ordered[index]


def row_to_dict(row: BenchmarkRow, config: RunConfig) -> dict[str, object]:
    control = row.control
    median = row.median_latency_ms
    return {
        "id": control.id,
        "contract_id": control.contract_id,
        "contract_title": control.contract_title,
        "route": control.route,
        "launch_route": control.launch_route,
        "scroll_position": control.scroll_position,
        "label": control.label,
        "text": control.text,
        "content_desc": control.content_desc,
        "resource_id": control.resource_id,
        "class_name": control.class_name,
        "bounds": list(control.bounds),
        "center": list(control.center),
        "status": row.status,
        "skip_reason": row.skip_reason,
        "first_tap_latency_ms": row.first_tap_latency_ms,
        "first_tap_raw_latency_ms": row.first_tap_raw_latency_ms,
        "warmed_median_latency_ms": row.warmed_median_latency_ms,
        "warmed_raw_median_latency_ms": row.warmed_raw_median_latency_ms,
        "blank_spinner_frozen_duration_ms": None,
        "median_latency_ms": median,
        "median_raw_latency_ms": row.median_raw_latency_ms,
        "median_dump_overhead_ms": row.median_dump_overhead_ms,
        "min_latency_ms": row.min_latency_ms,
        "max_latency_ms": row.max_latency_ms,
        "transition_kind": row.transition_kind,
        "slow": median is not None and median > config.slow_threshold_ms,
        "measurements": [measurement_to_dict(item) for item in row.measurements],
    }


def measurement_to_dict(item: Measurement) -> dict[str, object]:
    return {
        "latency_ms": item.latency_ms,
        "raw_latency_ms": item.raw_latency_ms,
        "dump_overhead_ms": item.dump_overhead_ms,
        "status": item.status,
        "error": item.error,
        "xml_settle_polls": item.xml_settle_polls,
        "final_xml_sha256": item.final_xml_sha256,
        "phase": item.phase,
        "transition_kind": item.transition_kind or ("visible_change" if item.latency_ms is not None else ""),
    }


def build_measurements_json(report: Mapping[str, object]) -> dict[str, object]:
    rows = []
    for row in cast(list[dict[str, object]], report.get("rows", [])):
        median = row.get("median_latency_ms")
        if not isinstance(median, (int, float)):
            continue
        rows.append(
            {
                "id": row["id"],
                "baseline_ms": median,
                "route": row["route"],
                "scroll_position": row["scroll_position"],
                "label": row["label"],
                "transition_kind": row.get("transition_kind", "visible_change"),
                "source": "button_latency_benchmark.py",
            }
        )
    return {
        "schema": TIMINGS_SCHEMA,
        "source_schema": report.get("schema"),
        "generated_at": report.get("generated_at"),
        "measurement_model": report.get("measurement_model"),
        "rows": rows,
    }


def write_outputs(
    report: Mapping[str, object],
    measurements: Mapping[str, object],
    out_json: Path,
    out_csv: Path,
    out_md: Path,
    measurements_json: Path,
) -> None:
    for path in (out_json, out_csv, out_md, measurements_json):
        path.parent.mkdir(parents=True, exist_ok=True)
    out_json.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    measurements_json.write_text(json.dumps(measurements, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    write_csv(report, out_csv)
    out_md.write_text(render_markdown(report), encoding="utf-8")


def write_csv(report: Mapping[str, object], path: Path) -> None:
    fieldnames = [
        "id",
        "contract_id",
        "route",
        "scroll_position",
        "label",
        "class_name",
        "status",
        "transition_kind",
        "skip_reason",
        "first_tap_latency_ms",
        "first_tap_raw_latency_ms",
        "warmed_median_latency_ms",
        "warmed_raw_median_latency_ms",
        "blank_spinner_frozen_duration_ms",
        "median_latency_ms",
        "median_raw_latency_ms",
        "median_dump_overhead_ms",
        "min_latency_ms",
        "max_latency_ms",
        "slow",
    ]
    with path.open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        for row in cast(list[dict[str, object]], report.get("rows", [])):
            writer.writerow({key: row.get(key, "") for key in fieldnames})


def render_markdown(report: Mapping[str, object]) -> str:
    summary = cast(dict[str, object], report.get("summary", {}))
    rows = cast(list[dict[str, object]], report.get("rows", []))
    slow_rows = [row for row in rows if row.get("slow")]
    skipped_rows = [row for row in rows if row.get("skip_reason")]
    methodology = cast(dict[str, object], report.get("methodology", {}))
    route_plan = cast(list[dict[str, object]], methodology.get("route_plan", []))
    artifact_rows = cast(list[dict[str, object]], report.get("artifacts", methodology.get("artifacts", [])))
    lines = [
        "# Ralph Button Latency Benchmark",
        "",
        f"Schema: `{report.get('schema', '')}`",
        f"Generated at: `{report.get('generated_at', '')}`",
        f"Repo head: `{report.get('repo_head', '')}`",
        f"Package: `{report.get('package_name', '')}`",
        f"Measurement model: {report.get('measurement_model', '')}",
        "",
        "## Summary",
        "",
        f"- Rows discovered: {summary.get('row_count', 0)}",
        f"- Rows measured: {summary.get('measured_rows', 0)}",
        f"- Rows skipped: {summary.get('skipped_rows', 0)}",
        f"- Slow rows over {report.get('slow_threshold_ms', DEFAULT_SLOW_THRESHOLD_MS)} ms: {summary.get('slow_rows', 0)}",
        f"- Median latency: {_format_ms(summary.get('median_ms'))}",
        f"- First-tap median latency: {_format_ms(summary.get('first_tap_median_ms'))}",
        f"- Warmed median latency: {_format_ms(summary.get('warmed_median_ms'))}",
        f"- P95 latency: {_format_ms(summary.get('p95_ms'))}",
        f"- Max latency: {_format_ms(summary.get('max_ms'))}",
        f"- No visible change rows: {summary.get('no_visible_change_rows', 0)}",
        f"- Visible change rows: {summary.get('visible_change_rows', 0)}",
        "",
        "## Methodology",
        "",
        f"- Suite: `{methodology.get('suite', report.get('benchmark_suite', 'custom'))}` — {methodology.get('suite_description', '')}",
        f"- Route variants: {methodology.get('variant_count', 0)}",
        "- Cold-start policy: first repeat force-stops the app before launch; warm repeats use single-top reroutes before tapping.",
        "- Inventory pipeline: feed `button-latency-measurements.json` into `button_latency_inventory.py` with the current UI manifest and button contract.",
        "",
        "### Route plan",
        "",
        "| Route | Source | Launch route | Scroll positions | Prep steps |",
        "| --- | --- | --- | --- | --- |",
    ]
    for route in route_plan:
        prep_steps = cast(list[dict[str, object]], route.get("prep_steps", []))
        lines.append(
            f"| `{route.get('route', '')}` | `{route.get('source', '')}` | `{route.get('launch_route', '')}` | "
            f"{', '.join(cast(list[str], route.get('scroll_positions', [])))} | "
            f"{', '.join(str(step.get('label', '')) for step in prep_steps) or '—'} |"
        )
    lines.extend([
        "",
        "### Generated artifacts",
        "",
        "| Name | Path | Schema | Purpose |",
        "| --- | --- | --- | --- |",
    ])
    for artifact in artifact_rows:
        lines.append(
            f"| `{artifact.get('name', '')}` | `{artifact.get('path', '')}` | `{artifact.get('schema', '')}` | {artifact.get('purpose', '')} |"
        )
    lines.extend([
        "",
        "## Slow measured controls",
        "",
    ])
    if slow_rows:
        lines.extend([
            "| ID | Route | Label | Transition | First tap | Warmed | Max |",
            "| --- | --- | --- | --- | --- | --- | --- |",
        ])
        for row in sorted(slow_rows, key=lambda item: cast(float, item.get("median_latency_ms") or 0), reverse=True):
            lines.append(
                f"| `{row.get('id', '')}` | `{row.get('route', '')}`/{row.get('scroll_position', '')} | "
                f"{row.get('label', '') or '—'} | {row.get('transition_kind', 'visible_change') or 'visible_change'} | {_format_ms(row.get('first_tap_latency_ms'))} | "
                f"{_format_ms(row.get('warmed_median_latency_ms'))} | "
                f"{_format_ms(row.get('max_latency_ms'))} |"
            )
    else:
        lines.append("No measured controls exceeded the configured slow threshold.")
    lines.extend([
        "",
        "## All measured controls",
        "",
        "| ID | Route | Label | Status | Transition | First tap | Warmed | Median | Raw median | Dump overhead | Max |",
        "| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |",
    ])
    for row in rows:
        if row.get("status") != "measured":
            continue
        lines.append(
            f"| `{row.get('id', '')}` | `{row.get('route', '')}`/{row.get('scroll_position', '')} | "
            f"{row.get('label', '') or '—'} | `{row.get('status', '')}` | "
            f"{row.get('transition_kind', 'visible_change') or 'visible_change'} | "
            f"{_format_ms(row.get('first_tap_latency_ms'))} | "
            f"{_format_ms(row.get('warmed_median_latency_ms'))} | "
            f"{_format_ms(row.get('median_latency_ms'))} | "
            f"{_format_ms(row.get('median_raw_latency_ms'))} | "
            f"{_format_ms(row.get('median_dump_overhead_ms'))} | "
            f"{_format_ms(row.get('max_latency_ms'))} |"
        )
    lines.extend([
        "",
        "## Skipped controls",
        "",
    ])
    if skipped_rows:
        lines.extend([
            "| ID | Route | Label | Reason |",
            "| --- | --- | --- | --- |",
        ])
        for row in skipped_rows:
            lines.append(
                f"| `{row.get('id', '')}` | `{row.get('route', '')}`/{row.get('scroll_position', '')} | "
                f"{row.get('label', '') or '—'} | `{row.get('skip_reason', '')}` |"
            )
    else:
        lines.append("No controls were skipped by the benchmark filters.")
    return "\n".join(lines) + "\n"


def _format_ms(value: object) -> str:
    if isinstance(value, (int, float)):
        return f"{float(value):.1f} ms"
    return "—"


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Discover and time visible Kani buttons/controls on a booted emulator via adb + uiautomator."
    )
    parser.add_argument("--repo-root", type=Path, default=Path("."))
    parser.add_argument("--apk", type=Path, default=None, help="Debug APK to install; defaults to first app debug APK")
    parser.add_argument("--package", default="", help="Package name; defaults to aapt dump badging from APK")
    parser.add_argument("--adb", default="", help="adb binary; defaults to PATH/ANDROID_HOME")
    parser.add_argument("--aapt", default="", help="aapt binary; defaults to PATH/ANDROID_HOME")
    parser.add_argument("--button-contract", type=Path, default=DEFAULT_BUTTON_CONTRACT)
    suite_help = "; ".join(f"{name}: {description}" for name, description in sorted(SUITE_DESCRIPTIONS.items()))
    parser.add_argument(
        "--suite",
        choices=sorted(BENCHMARK_SUITES),
        default="",
        help=f"Named reproducible route suite. When set, this overrides --routes. Suites: {suite_help}",
    )
    parser.add_argument(
        "--routes",
        default=",".join(DEFAULT_ROUTES),
        help="Comma-separated screenshot routes or normal-app scenarios; ignored when --suite is set",
    )
    parser.add_argument("--scroll-positions", default=",".join(DEFAULT_SCROLL_POSITIONS), help="Comma-separated scroll positions")
    parser.add_argument("--repeat-count", type=int, default=DEFAULT_REPEAT_COUNT)
    parser.add_argument("--slow-threshold-ms", type=int, default=DEFAULT_SLOW_THRESHOLD_MS)
    parser.add_argument("--settle-timeout-ms", type=int, default=DEFAULT_SETTLE_TIMEOUT_MS)
    parser.add_argument("--dump-timeout-ms", type=int, default=DEFAULT_DUMP_TIMEOUT_MS)
    parser.add_argument("--stable-polls", type=int, default=DEFAULT_STABLE_POLLS)
    parser.add_argument("--poll-interval-ms", type=int, default=DEFAULT_POLL_INTERVAL_MS)
    parser.add_argument("--max-controls", type=int, default=None, help="Limit measured/discovered rows for smoke runs")
    parser.add_argument("--include-unsafe", action="store_true", help="Measure controls that may open external/provider/destructive paths")
    parser.add_argument("--include-stateful", action="store_true", help="Measure toggles/sliders/text inputs that may mutate settings")
    parser.add_argument("--skip-install", action="store_true", help="Do not adb install the APK before running")
    parser.add_argument("--dry-run-inventory", action="store_true", help="Only discover controls; do not tap them")
    parser.add_argument("--out-dir", type=Path, default=DEFAULT_OUT_DIR)
    parser.add_argument("--out-json", type=Path, default=DEFAULT_INVENTORY_JSON)
    parser.add_argument("--out-csv", type=Path, default=DEFAULT_INVENTORY_CSV)
    parser.add_argument("--out-md", type=Path, default=DEFAULT_INVENTORY_MD)
    parser.add_argument("--measurements-json", type=Path, default=DEFAULT_MEASUREMENTS_JSON)
    return parser


def _split_csv(value: str) -> tuple[str, ...]:
    return tuple(item.strip() for item in value.split(",") if item.strip())


def config_from_args(args: argparse.Namespace) -> RunConfig:
    repo_root = args.repo_root.resolve()
    adb = args.adb or find_adb()
    aapt = args.aapt or find_aapt()
    apk_path = (args.apk if args.apk else find_debug_apk(repo_root)).resolve()
    package_name = args.package or package_name_from_apk(aapt, apk_path)
    if args.repeat_count < 1:
        raise ButtonLatencyBenchmarkError("--repeat-count must be >= 1")
    if args.slow_threshold_ms < 1:
        raise ButtonLatencyBenchmarkError("--slow-threshold-ms must be >= 1")
    if args.settle_timeout_ms < 1:
        raise ButtonLatencyBenchmarkError("--settle-timeout-ms must be >= 1")
    if args.dump_timeout_ms < 1:
        raise ButtonLatencyBenchmarkError("--dump-timeout-ms must be >= 1")
    routes = routes_for_suite(args.suite) if args.suite else _split_csv(args.routes)
    if not routes:
        raise ButtonLatencyBenchmarkError("at least one route or suite route is required")
    return RunConfig(
        repo_root=repo_root,
        adb=adb,
        aapt=aapt,
        apk_path=apk_path,
        package_name=package_name,
        out_dir=(args.out_dir if args.out_dir.is_absolute() else repo_root / args.out_dir),
        repeat_count=args.repeat_count,
        slow_threshold_ms=args.slow_threshold_ms,
        settle_timeout_ms=args.settle_timeout_ms,
        dump_timeout_ms=args.dump_timeout_ms,
        stable_polls=args.stable_polls,
        poll_interval_ms=args.poll_interval_ms,
        include_unsafe=args.include_unsafe,
        include_stateful=args.include_stateful,
        max_controls=args.max_controls,
        routes=routes,
        scroll_positions=_split_csv(args.scroll_positions),
        dry_run_inventory=args.dry_run_inventory,
        suite=args.suite,
    )


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    config = config_from_args(args)
    contract_path = args.button_contract if args.button_contract.is_absolute() else config.repo_root / args.button_contract
    contract_index = contract_label_index(contract_path if contract_path.exists() else None)
    if not args.skip_install:
        install_apk(config)
    else:
        prepare_benchmark_device(config)
    rows = discover_controls(config, contract_index)
    measure_rows(config, rows, contract_index)
    out_json = args.out_json if args.out_json.is_absolute() else config.repo_root / args.out_json
    out_csv = args.out_csv if args.out_csv.is_absolute() else config.repo_root / args.out_csv
    out_md = args.out_md if args.out_md.is_absolute() else config.repo_root / args.out_md
    measurements_json = (
        args.measurements_json if args.measurements_json.is_absolute() else config.repo_root / args.measurements_json
    )
    report = build_report(
        config,
        rows,
        {
            "benchmark_json": out_json,
            "benchmark_csv": out_csv,
            "benchmark_markdown": out_md,
            "measurements_json": measurements_json,
        },
    )
    measurements = build_measurements_json(report)
    write_outputs(
        report,
        measurements,
        out_json,
        out_csv,
        out_md,
        measurements_json,
    )
    print(json.dumps(report["summary"], sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
