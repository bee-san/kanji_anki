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
                "tools.test_verify_desktop_package",
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
        self.assertIn("dependsOn(createDesktopDistributable)", smoke_block)
        self.assertIn('"--image-root"', smoke_block)
        self.assertIn(
            '"tools/run_desktop_installed_image_smoke.py"',
            smoke_block,
        )

        package_block = kotlin_block('tasks.register("ciDesktopPackage")')
        self.assertIn("packageDesktopCurrentOs", package_block)
        self.assertIn("smokeDesktopInstalledImage", package_block)
        self.assertIn("verifyDesktopPackage", package_block)

    def test_compose_packaging_task_names_are_reached_only_through_the_wrappers(
        self,
    ) -> None:
        # Goal 204 asks for stable task names so CI does not depend on plugin task-name
        # churn. That only holds if the plugin's names appear in exactly one place each:
        # the wrapper that delegates to them. Anything else referring to
        # `:desktop-app:createDistributable` directly reintroduces the coupling the
        # wrappers exist to remove, and does it silently.
        create_block = kotlin_block('tasks.register("createDesktopDistributable")')
        self.assertIn('dependsOn(":desktop-app:createDistributable")', create_block)
        package_block = kotlin_block('tasks.register("packageDesktopCurrentOs")')
        self.assertIn(
            'dependsOn(":desktop-app:packageDistributionForCurrentOS")',
            package_block,
        )

        self.assertEqual(1, ROOT_BUILD.count(':desktop-app:createDistributable"'))
        self.assertEqual(
            1,
            ROOT_BUILD.count(':desktop-app:packageDistributionForCurrentOS"'),
        )

        # The non-minified variants, deliberately: Kani does not ProGuard the desktop
        # distribution, and the gates must exercise the image users install. Matched as
        # quoted task paths rather than as bare names, so the comment above that explains
        # the choice does not count as making it.
        for release_variant in (
            '":desktop-app:createReleaseDistributable"',
            '":desktop-app:packageReleaseDistributionForCurrentOS"',
        ):
            with self.subTest(release_variant=release_variant):
                self.assertNotIn(release_variant, ROOT_BUILD)

        # CI names the wrappers, not the plugin.
        workflow = (ROOT / ".github/workflows/desktop-ci.yml").read_text(
            encoding="utf-8",
        )
        self.assertNotIn("createDistributable", workflow)
        self.assertNotIn("packageDistributionForCurrentOS", workflow)

    def test_package_gate_verifies_the_shipped_runtime_from_the_built_image(self) -> None:
        # Three defects in this area were each invisible to a green build and visible in
        # the installed image: a missing `java.net.http`, a missing `jdk.accessibility`,
        # and a runtime taken from whichever JDK ran the daemon. This gate reads the
        # artifact, which is the only place any of them showed.
        block = kotlin_block('tasks.register<Exec>("verifyDesktopPackage")')
        self.assertIn("dependsOn(createDesktopDistributable)", block)
        self.assertIn("mustRunAfter(packageDesktopCurrentOs)", block)
        self.assertIn('"-m"', block)
        self.assertIn('"tools.verify_desktop_package"', block)
        self.assertIn('"--image-root"', block)

    def test_package_gate_measures_startup_and_memory_after_the_smoke_gate(self) -> None:
        # The performance budget rides the same installed image as the smoke gate,
        # and runs after it: timing an image that does not render would report the
        # fastest startup the app has ever had for a build that shows nothing.
        budget_block = kotlin_block(
            'tasks.register<Exec>("measureDesktopStartupBudget")',
        )
        self.assertIn("dependsOn(createDesktopDistributable)", budget_block)
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
