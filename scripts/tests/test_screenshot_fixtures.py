#!/usr/bin/env python3

from __future__ import annotations

import unittest
from typing import cast

from scripts.ralph_loop import screenshot_fixtures


class ScreenshotFixturesTest(unittest.TestCase):
    def test_registry_is_deterministic_and_matches_capture_route_order(self) -> None:
        registry = screenshot_fixtures.fixture_registry()
        again = screenshot_fixtures.fixture_registry()

        self.assertEqual(again, registry)
        self.assertEqual("cheap-ralph-screenshot-fixtures-v1", registry["schema"])
        self.assertEqual(
            ["home", "study", "stats", "settings", "games", "missing-kanji", "narrow", "wide"],
            registry["all_route_order"],
        )
        self.assertEqual({"launcher-home": "home"}, registry["requested_route_aliases"])
        self.assertEqual(
            [
                "all",
                "launcher-home",
                "home",
                "study",
                "stats",
                "settings",
                "games",
                "missing-kanji",
                "narrow",
                "wide",
                "update",
            ],
            registry["supported_requested_routes"],
        )

        fixtures = cast(list[dict[str, object]], registry["fixtures"])
        self.assertEqual(
            ["home", "study", "stats", "settings", "games", "missing-kanji", "narrow", "wide", "update"],
            [fixture["route"] for fixture in fixtures],
        )
        self.assertEqual(9, cast(dict[str, object], registry["summary"])["fixture_count"])
        for fixture in fixtures:
            self.assertEqual(screenshot_fixtures.fixture_hash(fixture), fixture["fixture_hash"])
            self.assertTrue(fixture["view_id"])
            self.assertTrue(fixture["fixture_id"])
            self.assertTrue(cast(list[str], fixture["screenshot_names"]))
            self.assertTrue(cast(list[str], fixture["expected_terms"]))
            self.assertTrue(cast(list[str], fixture["known_invariants"]))
            route = str(fixture["route"])
            expected_names = (
                [f"{route}-top.png"]
                if route == "missing-kanji"
                else [f"{route}-top.png", f"{route}-middle.png", f"{route}-bottom.png"]
            )
            self.assertEqual(expected_names, fixture["screenshot_names"])

    def test_bucket_mapping_returns_expected_view_fixtures(self) -> None:
        self.assertEqual(
            ["home", "missing-kanji", "narrow", "wide"],
            [fixture["route"] for fixture in screenshot_fixtures.fixtures_for_bucket("home")],
        )
        self.assertEqual(
            ["settings", "update"],
            [fixture["route"] for fixture in screenshot_fixtures.fixtures_for_bucket("settings")],
        )
        self.assertEqual(9, len(screenshot_fixtures.fixtures_for_bucket("shell")))
        self.assertEqual(9, len(screenshot_fixtures.fixtures_for_bucket("theme")))
        self.assertEqual(9, len(screenshot_fixtures.fixtures_for_bucket("shared")))
        self.assertEqual([], screenshot_fixtures.fixtures_for_bucket("test"))


if __name__ == "__main__":
    unittest.main()
