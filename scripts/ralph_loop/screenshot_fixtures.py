#!/usr/bin/env python3

from __future__ import annotations

import hashlib
import json
from dataclasses import asdict, dataclass
from typing import Iterable, cast

SCHEMA = "cheap-ralph-screenshot-fixtures-v1"
DEFAULT_DEVICE_PROFILE = "pixel-6-api-35"


@dataclass(frozen=True)
class ScreenshotFixture:
    view_id: str
    route: str
    launch_route: str
    fixture_id: str
    orientation: str
    screenshot_names: tuple[str, ...]
    expected_terms: tuple[str, ...]
    source_buckets: tuple[str, ...]
    risk_tags: tuple[str, ...]
    known_invariants: tuple[str, ...]
    device_profile: str = DEFAULT_DEVICE_PROFILE

    def to_dict(self) -> dict[str, object]:
        data = asdict(self)
        for key, value in list(data.items()):
            if isinstance(value, tuple):
                data[key] = list(value)
        data["fixture_hash"] = fixture_hash(data)
        return data


FIXTURES: tuple[ScreenshotFixture, ...] = (
    ScreenshotFixture(
        view_id="home-default",
        route="home",
        launch_route="home",
        fixture_id="stable-home-default",
        orientation="portrait",
        screenshot_names=("home.png",),
        expected_terms=("Kani route home",),
        source_buckets=("home", "shell", "theme", "shared"),
        risk_tags=("navigation", "study-entry", "summary-state"),
        known_invariants=(
            "Must not start a study session without an explicit user action.",
            "Must not perform sync/provider work during screenshot-only startup.",
        ),
    ),
    ScreenshotFixture(
        view_id="study-default",
        route="study",
        launch_route="study",
        fixture_id="stable-study-default",
        orientation="portrait",
        screenshot_names=("study.png",),
        expected_terms=("Kani route study", "Study"),
        source_buckets=("study", "shell", "theme", "shared"),
        risk_tags=("learning-flow", "review-buttons", "scheduler-sensitive"),
        known_invariants=(
            "Must preserve ladder movement, learning/relearning step semantics, and review rating behavior.",
            "Must keep review buttons and answer controls accessible and testable.",
        ),
    ),
    ScreenshotFixture(
        view_id="stats-default",
        route="stats",
        launch_route="stats",
        fixture_id="stable-stats-default",
        orientation="portrait",
        screenshot_names=("stats.png",),
        expected_terms=("Kani route stats", "Stats"),
        source_buckets=("stats", "shell", "theme", "shared"),
        risk_tags=("analytics", "read-only"),
        known_invariants=(
            "Must not change progress analytics calculations while doing visual-only UX work.",
            "Must keep empty/loading states deterministic for screenshot evidence.",
        ),
    ),
    ScreenshotFixture(
        view_id="settings-default",
        route="settings",
        launch_route="settings",
        fixture_id="stable-settings-default",
        orientation="portrait",
        screenshot_names=("settings.png",),
        expected_terms=("Kani route settings", "Settings"),
        source_buckets=("settings", "shell", "theme", "shared"),
        risk_tags=("settings", "stateful-input", "scheduler-sensitive"),
        known_invariants=(
            "Must preserve setting defaults, validation, and persistence semantics.",
            "Must not remove safety guidance without a shorter equivalent.",
        ),
    ),
    ScreenshotFixture(
        view_id="games-default",
        route="games",
        launch_route="games",
        fixture_id="stable-games-default",
        orientation="portrait",
        screenshot_names=("games.png",),
        expected_terms=("Games",),
        source_buckets=("games", "shell", "theme", "shared"),
        risk_tags=("practice-games", "navigation"),
        known_invariants=(
            "Must keep game entry points optional and separate from core review scheduling.",
            "Must preserve accessible labels for game actions.",
        ),
    ),
    ScreenshotFixture(
        view_id="home-narrow",
        route="narrow",
        launch_route="home",
        fixture_id="stable-home-narrow-portrait",
        orientation="portrait",
        screenshot_names=("narrow.png",),
        expected_terms=("Kani route home",),
        source_buckets=("home", "shell", "theme", "shared"),
        risk_tags=("responsive-layout", "narrow-viewport"),
        known_invariants=(
            "Must keep the primary study action reachable on narrow screens.",
            "Must not depend on random or current-time data for the screenshot state.",
        ),
    ),
    ScreenshotFixture(
        view_id="home-wide",
        route="wide",
        launch_route="home",
        fixture_id="stable-home-wide-landscape",
        orientation="landscape",
        screenshot_names=("wide.png",),
        expected_terms=("Kani route home",),
        source_buckets=("home", "shell", "theme", "shared"),
        risk_tags=("responsive-layout", "landscape"),
        known_invariants=(
            "Must keep navigation and the primary study action visible in landscape.",
            "Must use the same seeded home fixture as the default home capture.",
        ),
    ),
    ScreenshotFixture(
        view_id="update-settings",
        route="update",
        launch_route="update",
        fixture_id="stable-settings-update-default",
        orientation="portrait",
        screenshot_names=("update.png",),
        expected_terms=("Kani route settings", "GitHub updater"),
        source_buckets=("settings", "shell", "theme", "shared"),
        risk_tags=("settings", "update-flow", "network-risk"),
        known_invariants=(
            "Must not change release, signing, download, or install behavior during visual-only UX work.",
            "Must preserve update warning and permission copy when shortening text.",
        ),
    ),
)


ALL_ROUTE_ORDER = ("home", "study", "stats", "settings", "games", "narrow", "wide")
SUPPORTED_REQUESTED_ROUTES = (
    "all",
    "launcher-home",
    "home",
    "study",
    "stats",
    "settings",
    "games",
    "narrow",
    "wide",
    "update",
)
REQUESTED_ROUTE_ALIASES = {"launcher-home": "home"}


def fixture_hash(data: dict[str, object]) -> str:
    stable = {key: value for key, value in data.items() if key != "fixture_hash"}
    encoded = json.dumps(stable, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def fixture_set_hash(fixtures: Iterable[dict[str, object]]) -> str:
    encoded = json.dumps(list(fixtures), sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def fixture_registry() -> dict[str, object]:
    fixtures = [fixture.to_dict() for fixture in FIXTURES]
    return {
        "schema": SCHEMA,
        "fixture_set_hash": fixture_set_hash(fixtures),
        "all_route_order": list(ALL_ROUTE_ORDER),
        "supported_requested_routes": list(SUPPORTED_REQUESTED_ROUTES),
        "requested_route_aliases": dict(REQUESTED_ROUTE_ALIASES),
        "fixtures": fixtures,
        "summary": {
            "fixture_count": len(fixtures),
            "routes": [str(fixture["route"]) for fixture in fixtures],
            "default_device_profile": DEFAULT_DEVICE_PROFILE,
        },
    }


def fixtures_for_bucket(bucket: str) -> list[dict[str, object]]:
    fixtures = cast(list[dict[str, object]], fixture_registry()["fixtures"])
    result: list[dict[str, object]] = []
    for fixture in fixtures:
        source_buckets = cast(list[str], fixture.get("source_buckets", []))
        if bucket in source_buckets:
            result.append(fixture)
    return result
