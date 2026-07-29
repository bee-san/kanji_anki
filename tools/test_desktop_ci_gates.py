from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
ROOT_BUILD = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")


def kotlin_block(marker: str) -> str:
    start = ROOT_BUILD.index(marker)
    opening_brace = ROOT_BUILD.index("{", start)
    depth = 0
    for index in range(opening_brace, len(ROOT_BUILD)):
        character = ROOT_BUILD[index]
        if character == "{":
            depth += 1
        elif character == "}":
            depth -= 1
            if depth == 0:
                return ROOT_BUILD[start : index + 1]
    raise AssertionError(f"unterminated Kotlin block: {marker}")


def kotlin_string_list(name: str) -> tuple[str, ...]:
    match = re.search(
        rf"val {re.escape(name)} = listOf\((.*?)\n\)",
        ROOT_BUILD,
        flags=re.DOTALL,
    )
    if match is None:
        raise AssertionError(f"missing Kotlin list: {name}")
    return tuple(re.findall(r'"([^"]+)"', match.group(1)))


class DesktopRootGateContractTest(unittest.TestCase):
    def test_desktop_gate_covers_host_portable_product_and_fixture_checks(self) -> None:
        self.assertEqual(
            (
                "testBuildLogic",
                ":bee-fsrs:check",
                ":core:check",
                ":domain:check",
                ":sync-domain:check",
                ":data-api:check",
                ":application:check",
                ":writing-core:check",
                ":dictionary-core:check",
                ":update-core:check",
                ":platform-contracts:check",
                ":desktop-app:check",
                "testDesktopCiScripts",
                "testDesktopTooling",
            ),
            kotlin_string_list("desktopCiTasks"),
        )
        block = kotlin_block('tasks.register("ciDesktop")')
        self.assertIn("dependsOn(desktopCiTasks)", block)
        self.assertNotIn(":app:", block)

    def test_desktop_package_gate_builds_image_package_and_runs_smoke(self) -> None:
        smoke_block = kotlin_block(
            'tasks.register<Exec>("smokeDesktopInstalledImage")',
        )
        self.assertIn('dependsOn(":desktop-app:createDistributable")', smoke_block)
        self.assertIn('"--image-root"', smoke_block)
        self.assertIn(
            '"tools/run_desktop_installed_image_smoke.py"',
            smoke_block,
        )

        package_block = kotlin_block('tasks.register("ciDesktopPackage")')
        self.assertIn(
            '":desktop-app:packageDistributionForCurrentOS"',
            package_block,
        )
        self.assertIn("smokeDesktopInstalledImage", package_block)

    def test_aggregate_is_explicitly_current_host_without_release_tasks(self) -> None:
        block = kotlin_block('tasks.register("ciAll")')
        for gate in ("ciQuality", "ciDesktop", "ciDesktopPackage"):
            self.assertIn(f'"{gate}"', block)
        self.assertNotIn("ciRelease", block)
        self.assertIn("current-host", block)

    def test_android_fast_and_release_gates_remain_desktop_free(self) -> None:
        fast_tasks = kotlin_string_list("fastCiTasks")
        self.assertFalse(
            any("desktop" in task.lower() for task in fast_tasks),
            fast_tasks,
        )
        release_block = kotlin_block('tasks.register("ciRelease")')
        self.assertNotIn("desktop", release_block.lower())
        self.assertNotIn("ciAll", release_block)


if __name__ == "__main__":
    unittest.main()
