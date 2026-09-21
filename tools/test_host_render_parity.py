#!/usr/bin/env python3
"""The Android/desktop render-test parity invariant, checked across every module.

Each Compose multiplatform module keeps its render assertions in `commonTest` and
registers them twice: once in a `*DesktopRenderTest` and once in a
`*AndroidRenderTest` under Robolectric. That pairing is the whole Android/desktop
parity proof — an assertion registered on one host only is an assertion that half
the product does not run.

Nothing in the Kotlin build can check this. Each registry compiles independently
into its own source set, so a test method dropped from one of them is not a
compile error, not a test failure, and not visible in either module's coverage
report. It shows up as a green build with one fewer test, which is precisely the
shape of regression a gate is supposed to catch.

So this reads both registries as text and compares the registered method names.
The one legitimate divergence is declared by name in `HOST_ONLY_TESTS` with its
reason, and is itself checked to still exist — a stale exception would silently
re-open the gap it documents.

Written in Python rather than Kotlin for the same reason as
`test_shared_string_locales`: the invariant spans modules and source sets, so no
single compilation unit can see all of it.
"""

from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]

# Each module and the package its render tests live in. Named rather than
# discovered, so a module whose registries both vanished is a failure here
# instead of one fewer silent subTest.
RENDER_TEST_MODULES = {
    "ui-common": ("KaniTheme", "ui"),
    "feature-home": ("Home", "home"),
    "feature-shell": ("Shell", "shell"),
    "feature-study": ("Study", "study"),
    "feature-stats": ("Stats", "stats"),
    "feature-games": ("Games", "games"),
    "feature-missing-kanji": ("MissingKanji", "missing"),
    "feature-settings": ("Settings", "settings"),
}

# Tests deliberately registered on one host only, with the reason. Every entry is
# a documented host capability gap, not a convenience.
HOST_ONLY_TESTS = {
    # Robolectric's text measurement ignores `Density.fontScale`: the study
    # progress line measures the same height at 1x and at 2x, so the witness that
    # the font-scale matrix actually varied cannot witness anything there. The
    # scaled-layout assertions it guards do run on both hosts.
    ("feature-study", "eachFontScaleReallyReachesTheRenderedText"): "desktop",
}

TEST_METHOD = re.compile(r"@Test\s+fun\s+([A-Za-z0-9_]+)\s*\(")


def registry_path(module: str, host: str) -> Path:
    prefix, package = RENDER_TEST_MODULES[module]
    source_set = "desktopTest" if host == "desktop" else "androidHostTest"
    name = f"{prefix}{'Desktop' if host == 'desktop' else 'Android'}RenderTest.kt"
    return ROOT / module / "src" / source_set / "kotlin/dev/bee/kanjianki" / package / name


def registered_tests(module: str, host: str) -> set[str]:
    text = registry_path(module, host).read_text(encoding="utf-8")
    return set(TEST_METHOD.findall(text))


class HostRenderRegistryParityTest(unittest.TestCase):
    def test_every_module_registers_render_tests_on_both_hosts(self) -> None:
        for module in sorted(RENDER_TEST_MODULES):
            for host in ("desktop", "android"):
                with self.subTest(module=module, host=host):
                    path = registry_path(module, host)
                    self.assertTrue(path.is_file(), f"{path} is missing")
                    self.assertTrue(
                        registered_tests(module, host),
                        f"{path} registers no tests",
                    )

    def test_both_registries_run_the_same_assertions(self) -> None:
        for module in sorted(RENDER_TEST_MODULES):
            with self.subTest(module=module):
                desktop = registered_tests(module, "desktop")
                android = registered_tests(module, "android")
                allowed_desktop = {
                    name
                    for (owner, name), host in HOST_ONLY_TESTS.items()
                    if owner == module and host == "desktop"
                }
                allowed_android = {
                    name
                    for (owner, name), host in HOST_ONLY_TESTS.items()
                    if owner == module and host == "android"
                }
                self.assertEqual(
                    allowed_desktop,
                    desktop - android,
                    f"{module} registers desktop-only tests that are not declared",
                )
                self.assertEqual(
                    allowed_android,
                    android - desktop,
                    f"{module} registers Android-only tests that are not declared",
                )

    def test_every_declared_host_only_test_is_real_and_still_one_sided(self) -> None:
        # A renamed or re-added test would leave a stale entry here, and a stale
        # entry silently permits a future one-sided registration under the same
        # name — the exact gap the exception documents.
        for (module, name), host in sorted(HOST_ONLY_TESTS.items()):
            with self.subTest(module=module, name=name):
                self.assertIn(module, RENDER_TEST_MODULES)
                present = registered_tests(module, host)
                absent = registered_tests(module, "android" if host == "desktop" else "desktop")
                self.assertIn(name, present, f"{name} is no longer registered on {host}")
                self.assertNotIn(
                    name,
                    absent,
                    f"{name} runs on both hosts now; drop the exception",
                )

    def test_the_one_sided_registration_is_explained_where_it_is_missing(self) -> None:
        # The reason has to be readable at the file that lacks the test, because that
        # is where the next person will notice the difference. Either name counts —
        # a registry method and the `commonTest` assertion it calls are the same
        # thing named at two layers, and the KDoc may reasonably cite either.
        for (module, name), host in sorted(HOST_ONLY_TESTS.items()):
            other = "android" if host == "desktop" else "desktop"
            assertion = f"assert{name[0].upper()}{name[1:]}"
            with self.subTest(module=module, name=name):
                text = registry_path(module, other).read_text(encoding="utf-8")
                self.assertTrue(
                    name in text or assertion in text,
                    f"{registry_path(module, other)} must name {name} and say why it is absent",
                )


if __name__ == "__main__":
    unittest.main()
