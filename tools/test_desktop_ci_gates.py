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
                ":data-sql:check",
                ":data-desktop:check",
                ":provider-ankiconnect:check",
                ":reference-assets:check",
                ":backup-core:check",
                ":sync-engine:check",
                ":application:check",
                ":writing-core:check",
                ":dictionary-core:check",
                ":update-core:check",
                ":platform-contracts:check",
                ":platform-desktop:check",
                ":presentation-api:check",
                ":host-presentation:check",
                ":ui-common:check",
                ":feature-shell:check",
                ":feature-home:check",
                ":feature-study:check",
                ":feature-stats:check",
                ":feature-games:check",
                ":feature-missing-kanji:check",
                ":feature-settings:check",
                ":desktop-app:check",
                "testDesktopCiScripts",
                "testDesktopTooling",
            ),
            kotlin_string_list("desktopCiTasks"),
        )
        block = kotlin_block('tasks.register("ciDesktop")')
        self.assertIn("dependsOn(desktopCiTasks)", block)
        self.assertNotIn(":app:", block)

        sonar_block = kotlin_block('tasks.register("sonarPreflight")')
        self.assertIn('mustRunAfter("ciQuality")', sonar_block)

    def test_desktop_tooling_gate_names_every_module_it_is_meant_to_run(self) -> None:
        # `testDesktopTooling` enumerates its modules instead of discovering them,
        # because most of `tools/` needs the generated dictionary assets and belongs
        # to the Android gate. The cost of enumerating is that a new desktop tooling
        # test silently never runs, so the list is pinned here and each entry is
        # checked to be a real module — a rename shows up as a failing name rather
        # than as a gate that quietly got smaller.
        block = kotlin_block('tasks.register<Exec>("testDesktopTooling")')
        modules = tuple(re.findall(r'"(tools\.test_[a-z_]+)"', block))
        self.assertEqual(
            (
                "tools.test_desktop_ci_gates",
                "tools.test_desktop_ci_workflow",
                "tools.test_generate_desktop_icons",
                "tools.test_host_render_parity",
                "tools.test_measure_desktop_startup_budget",
                "tools.test_merge_verification_metadata",
                "tools.test_module_boundaries",
                "tools.test_run_desktop_installed_image_smoke",
                "tools.test_shared_string_locales",
            ),
            modules,
        )
        for module in modules:
            with self.subTest(module=module):
                self.assertTrue(
                    (ROOT / f"{module.replace('.', '/')}.py").is_file(),
                    f"{module} is enumerated but does not exist",
                )

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

    def test_package_gate_measures_startup_and_memory_after_the_smoke_gate(self) -> None:
        # The performance budget rides the same installed image as the smoke gate,
        # and runs after it: timing an image that does not render would report the
        # fastest startup the app has ever had for a build that shows nothing.
        budget_block = kotlin_block(
            'tasks.register<Exec>("measureDesktopStartupBudget")',
        )
        self.assertIn('dependsOn(":desktop-app:createDistributable")', budget_block)
        self.assertIn("mustRunAfter(smokeDesktopInstalledImage", budget_block)
        # Invoked as a module, because this gate imports the smoke runner instead
        # of duplicating it and a path invocation cannot resolve that import.
        self.assertIn('"tools.measure_desktop_startup_budget"', budget_block)
        self.assertIn('"-m"', budget_block)
        self.assertIn('"--image-root"', budget_block)

        package_block = kotlin_block('tasks.register("ciDesktopPackage")')
        self.assertIn("measureDesktopStartupBudget", package_block)

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
