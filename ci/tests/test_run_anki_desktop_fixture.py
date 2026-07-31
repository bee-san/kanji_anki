"""Deterministic guards for the live Anki Desktop fixture.

The fixture itself needs a real Anki, an X server, and two downloads, so it
cannot run here. What these tests protect is the part that would be dangerous if
it regressed silently: the isolation from the operator's own Anki, and the
non-interactive first-run seed that keeps the fixture from hanging on a modal
dialog with no error.
"""

from __future__ import annotations

import pickle
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
SCRIPT = REPO_ROOT / "ci" / "scripts" / "run_anki_desktop_fixture.sh"
SEED = REPO_ROOT / "ci" / "scripts" / "seed_anki_desktop_profile.py"
CHECKSUMS = REPO_ROOT / "ci" / "fixtures" / "anki-desktop" / "anki-desktop-26.05.sha256"

sys.path.insert(0, str(REPO_ROOT / "ci" / "scripts"))
import seed_anki_desktop_profile  # noqa: E402


class AnkiDesktopFixtureIsolationTest(unittest.TestCase):
    """The three properties that keep the fixture off the operator's Anki."""

    def test_refuses_the_live_ankiconnect_port(self) -> None:
        # Binding 8765 would send every fixture request -- including the write
        # tests -- to whatever Anki the operator has open.
        result = subprocess.run(
            ["bash", str(SCRIPT)],
            cwd=REPO_ROOT,
            env={"PATH": "/usr/bin:/bin", "KANI_ANKI_DESKTOP_PORT": "8765"},
            text=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=30,
            check=False,
        )

        self.assertEqual(2, result.returncode, result.stdout)
        self.assertIn("live AnkiConnect port", result.stdout)

    def test_defaults_to_an_isolated_port_and_base_directory(self) -> None:
        source = SCRIPT.read_text(encoding="utf-8")

        self.assertIn('port="${KANI_ANKI_DESKTOP_PORT:-18765}"', source)
        self.assertIn('base_dir="${work_dir}/base"', source)
        # Anki is always launched against the fixture's own base directory, so
        # it can neither read nor rewrite the operator's profiles and add-ons.
        self.assertIn('-b "${base_dir}"', source)
        self.assertIn('addon_dir="${base_dir}/addons21/kani_ankiconnect_fixture"', source)

    def test_verifies_pinned_downloads_before_use(self) -> None:
        source = SCRIPT.read_text(encoding="utf-8")

        self.assertIn("verify_sha256", source)
        self.assertIn('curl --fail --location --show-error "${url}" --output "${target}.partial"', source)
        self.assertIn("anki-desktop-${anki_version}.sha256", source)

    def test_checksums_pin_both_downloads(self) -> None:
        checksums = {
            filename: digest
            for digest, filename in (
                line.split()
                for line in CHECKSUMS.read_text(encoding="utf-8").splitlines()
                if line.strip()
            )
        }

        self.assertEqual(
            {
                "anki-26.05-linux-x86_64.tar.zst": (
                    "6223d705563f71ab40ce072a5d96a3919c546d5dde1e4c49dc27975e70067274"
                ),
                "anki-connect-4064fa142785975255457abd6a496015f5b71f38.tar.gz": (
                    "3e5209a66a5f80b7980d82a5825a0c070519cbe66c969544c3d1a83bdd8ee80d"
                ),
            },
            checksums,
        )

    def test_stop_patterns_stay_inside_the_generated_script(self) -> None:
        # `pgrep -f Xvfb` matches the calling shell's own command line. A stop
        # step written inline therefore kills the caller, which cost this fixture
        # three dead shells before the generated-script form.
        source = SCRIPT.read_text(encoding="utf-8")

        # The kill loop is only ever reached by executing the generated script.
        self.assertIn('setsid "${work_dir}/stop.sh"', source)
        for line in source.splitlines():
            stripped = line.strip()
            if stripped.startswith("#") or "pgrep -f" not in stripped:
                continue
            self.assertTrue(stripped.startswith("for p in $(pgrep -f"), stripped)


class SeedAnkiDesktopProfileTest(unittest.TestCase):
    """The seed is what makes an unattended first launch possible at all."""

    def test_seeded_preferences_suppress_the_first_run_dialog(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp) / "base"

            path = seed_anki_desktop_profile.seed(base, ["KaniFixture"])

            rows = dict(
                sqlite3.connect(path).execute("select name, data from profiles")
            )
        meta = pickle.loads(rows["_global"])

        # Both are required: Anki shows the language dialog when firstRun is
        # true *or* defaultLang is unset, and either one hangs a headless boot.
        self.assertFalse(meta["firstRun"])
        self.assertEqual("en_US", meta["defaultLang"])

    def test_the_first_profile_is_the_one_anki_opens(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            path = seed_anki_desktop_profile.seed(
                Path(temp) / "base", ["KaniFixture", "KaniSecond"]
            )

            rows = dict(sqlite3.connect(path).execute("select name, data from profiles"))
        meta = pickle.loads(rows["_global"])

        self.assertEqual({"_global", "KaniFixture", "KaniSecond"}, set(rows))
        self.assertEqual("KaniFixture", meta["last_loaded_profile_name"])

    def test_rows_are_readable_as_anki_writes_them(self) -> None:
        # Anki's ProfileManager._pickle uses protocol 4 and its schema declares
        # `name ... collate nocase`. A row written any other way still loads, but
        # matching keeps a seeded profile indistinguishable from a real one.
        with tempfile.TemporaryDirectory() as temp:
            path = seed_anki_desktop_profile.seed(Path(temp) / "base", ["KaniFixture"])
            schema = (
                sqlite3.connect(path)
                .execute("select sql from sqlite_master where name = 'profiles'")
                .fetchone()[0]
            )
            row = (
                sqlite3.connect(path)
                .execute("select data from profiles where name = 'KaniFixture'")
                .fetchone()[0]
            )

        self.assertIn("collate nocase", schema)
        self.assertEqual(4, seed_anki_desktop_profile.PICKLE_PROTOCOL)
        self.assertIsInstance(pickle.loads(row), dict)

    def test_a_profile_name_is_required(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            with self.assertRaises(ValueError):
                seed_anki_desktop_profile.seed(Path(temp) / "base", [])

    def test_command_line_seeds_the_named_profile(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            base = Path(temp) / "base"

            result = subprocess.run(
                [sys.executable, str(SEED), "--base", str(base), "--profile", "KaniSecond"],
                text=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                timeout=30,
                check=False,
            )

            self.assertEqual(0, result.returncode, result.stdout)
            names = {
                name
                for (name,) in sqlite3.connect(base / "prefs21.db").execute(
                    "select name from profiles"
                )
            }

        self.assertEqual({"_global", "KaniSecond"}, names)


if __name__ == "__main__":
    unittest.main()
