#!/usr/bin/env python3
"""Architecture contracts for Kani's current and target module graphs."""

from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
PURE_MODULES = (
    "domain",
    "dictionary-core",
    "fsrs-java",
    "sync-domain",
    "writing-core",
    "update-core",
    "core",
)
FEATURE_MODULES = frozenset(
    {
        "feature-home",
        "feature-study",
        "feature-stats",
        "feature-settings",
    },
)
TARGET_ANDROID_MODULES = frozenset(
    {
        "app",
        "automation",
        "data",
        "sync-android",
        "ui-common",
        "widget",
        *FEATURE_MODULES,
    },
)
TARGET_MODULES = frozenset({*PURE_MODULES, *TARGET_ANDROID_MODULES})
EXPECTED_CURRENT_MODULES = frozenset({*PURE_MODULES, "app"})
MODULE_CLASSES = {
    **dict.fromkeys(PURE_MODULES, "pure-jvm"),
    "data": "persistence",
    "ui-common": "shared-ui",
    **dict.fromkeys(FEATURE_MODULES, "feature"),
    "sync-android": "android-platform",
    "automation": "android-platform",
    "widget": "android-platform",
    "app": "composition-root",
}
CURRENT_PROJECT_DEPENDENCIES = {
    "domain": frozenset(),
    "dictionary-core": frozenset(),
    "fsrs-java": frozenset(),
    "sync-domain": frozenset({"domain"}),
    "writing-core": frozenset({"domain"}),
    "update-core": frozenset(),
    "core": frozenset(
        {"dictionary-core", "domain", "sync-domain", "fsrs-java", "update-core"},
    ),
    "app": frozenset({"core", "dictionary-core", "update-core", "writing-core"}),
}
TARGET_PROJECT_DEPENDENCIES = {
    "domain": frozenset(),
    "dictionary-core": frozenset(),
    "fsrs-java": frozenset(),
    "sync-domain": frozenset({"domain"}),
    "writing-core": frozenset({"domain"}),
    "update-core": frozenset(),
    "core": frozenset(
        {"dictionary-core", "domain", "sync-domain", "fsrs-java", "update-core"},
    ),
    "data": frozenset(
        {"core", "dictionary-core", "sync-domain", "update-core"},
    ),
    "ui-common": frozenset({"core"}),
    "feature-home": frozenset(
        {"core", "data", "sync-android", "ui-common"},
    ),
    "feature-study": frozenset(
        {"core", "data", "dictionary-core", "ui-common", "writing-core"},
    ),
    "feature-stats": frozenset({"core", "data", "ui-common"}),
    "feature-settings": frozenset(
        {"automation", "core", "data", "ui-common"},
    ),
    "sync-android": frozenset(
        {"core", "data", "dictionary-core", "sync-domain"},
    ),
    "automation": frozenset({"core", "data", "update-core"}),
    "widget": frozenset({"core", "data", "ui-common"}),
    "app": frozenset(
        {
            "automation",
            "data",
            "feature-home",
            "feature-settings",
            "feature-stats",
            "feature-study",
            "sync-android",
            "ui-common",
            "widget",
        },
    ),
}
ALLOWED_PROJECT_DEPENDENCIES = {
    module: CURRENT_PROJECT_DEPENDENCIES.get(module, frozenset())
    | TARGET_PROJECT_DEPENDENCIES.get(module, frozenset())
    for module in TARGET_MODULES
}
EDGE_RATIONALES = {
    ("sync-domain", "domain"): "Sync-domain shares domain value models.",
    ("writing-core", "domain"): "Writing policy consumes domain value models.",
    ("core", "dictionary-core"): "Scheduler policy consumes dictionary models.",
    ("core", "domain"): "Core policy builds on shared domain models.",
    ("core", "sync-domain"): "Core policy consumes pure sync contracts.",
    ("core", "fsrs-java"): "Core delegates memory scheduling to FSRS.",
    ("core", "update-core"): "Core consumes pure update policy.",
    ("data", "core"): "Persistence implements core-owned state contracts.",
    ("data", "dictionary-core"): "Persistence installs dictionary content.",
    ("data", "sync-domain"): "Persistence stores pure sync-domain models.",
    ("data", "update-core"): "Persistence stores update metadata.",
    ("ui-common", "core"): "Shared UI renders core value models.",
    ("feature-home", "core"): "Home renders core dashboard models.",
    ("feature-home", "data"): "Home consumes its repository contracts.",
    ("feature-home", "sync-android"): "Home invokes the public sync action contract.",
    ("feature-home", "ui-common"): "Home uses the shared shell and controls.",
    ("feature-study", "core"): "Study delegates all scheduler decisions to core.",
    ("feature-study", "data"): "Study consumes its repository contract.",
    ("feature-study", "dictionary-core"): "Study consumes dictionary interfaces.",
    ("feature-study", "ui-common"): "Study uses shared controls and tokens.",
    ("feature-study", "writing-core"): "Study uses pure writing evaluation models.",
    ("feature-stats", "core"): "Stats renders core analytics models.",
    ("feature-stats", "data"): "Stats consumes its repository contract.",
    ("feature-stats", "ui-common"): "Stats uses shared charts and surfaces.",
    ("feature-settings", "automation"): "Settings invokes public automation actions.",
    ("feature-settings", "core"): "Settings edits core preference models.",
    ("feature-settings", "data"): "Settings consumes its repository contract.",
    ("feature-settings", "ui-common"): "Settings uses shared controls and tokens.",
    ("sync-android", "core"): "Android sync applies core admission policy.",
    ("sync-android", "data"): "Android sync commits through sync repositories.",
    ("sync-android", "dictionary-core"): "Provider mapping uses dictionary models.",
    ("sync-android", "sync-domain"): "Android sync implements pure sync contracts.",
    ("automation", "core"): "Automation evaluates core reminder and fit policy.",
    ("automation", "data"): "Automation persists through repositories.",
    ("automation", "update-core"): "Updater workers execute pure update policy.",
    ("widget", "core"): "Widgets render core eligibility models.",
    ("widget", "data"): "Widgets load immutable repository snapshots.",
    ("widget", "ui-common"): "Widgets share launch and presentation contracts.",
    ("app", "automation"): "The composition root installs automation adapters.",
    ("app", "data"): "The composition root constructs repository implementations.",
    ("app", "feature-home"): "The navigation graph hosts Home.",
    ("app", "feature-settings"): "The navigation graph hosts Settings.",
    ("app", "feature-stats"): "The navigation graph hosts Stats.",
    ("app", "feature-study"): "The navigation graph hosts Study.",
    ("app", "sync-android"): "The composition root installs sync adapters.",
    ("app", "ui-common"): "The composition root hosts the shared app shell.",
    ("app", "widget"): "The application manifest installs widget components.",
    ("app", "core"): "Migration-only app code still consumes core directly.",
    (
        "app",
        "dictionary-core",
    ): "Migration-only app code still constructs dictionary adapters.",
    ("app", "update-core"): "Migration-only app code still executes update policy.",
    ("app", "writing-core"): "Migration-only app code still hosts writing adapters.",
}
PROJECT_CALL = re.compile(r"\bproject\s*\(")
PROJECT_DEPENDENCY = re.compile(
    r"\bproject\s*\(\s*(?:path\s*=\s*)?[\"']:([^\"']+)[\"']\s*\)",
)
TYPE_SAFE_PROJECT_ACCESSOR = re.compile(r"\bprojects\.[A-Za-z0-9_.]+")
ANDROID_IMPORT = re.compile(r"^import\s+(android|androidx)\.", re.MULTILINE)
AMBIGUOUS_RECORD_CONSTRUCTION = re.compile(
    r"\b(?:(?:RecordsSchedulerModels\.)?ReviewRequest|"
    r"(?:RecordsStudyModels\.)?TaskMemory)\s*\(|"
    r"\b(?:RecordsStudyModels\.)?TaskMemory\.fromStudyFields\s*\(",
)
RECORD_DEFINITION_FILES = {
    "core/src/main/kotlin/dev/bee/kanjianki/core/RecordsSchedulerModels.kt",
    "core/src/main/kotlin/dev/bee/kanjianki/core/RecordsStudyModels.kt",
}


def parse_project_dependencies(build_script: str, module: str) -> set[str]:
    dependencies = PROJECT_DEPENDENCY.findall(build_script)
    project_calls = PROJECT_CALL.findall(build_script)
    type_safe_accessors = TYPE_SAFE_PROJECT_ACCESSOR.findall(build_script)
    if len(dependencies) != len(project_calls) or type_safe_accessors:
        raise AssertionError(
            f":{module} contains an unparsed project dependency; update the boundary parser",
        )
    return set(dependencies)


def project_dependencies(module: str) -> set[str]:
    build_script = (ROOT / module / "build.gradle.kts").read_text(encoding="utf-8")
    return parse_project_dependencies(build_script, module)


class ModuleBoundaryTest(unittest.TestCase):
    def test_dependency_parser_accepts_named_path_and_fails_closed(self) -> None:
        self.assertEqual(
            {"domain", "core"},
            parse_project_dependencies(
                'implementation(project(":core"))\napi(project(path = ":domain"))',
                "fixture",
            ),
        )
        for unsupported in (
            'implementation(project(mapOf("path" to ":core")))',
            "implementation(projects.core)",
            'implementation(project(path = providers.gradleProperty("module")))',
            'implementation(project(":core", configuration = "default"))',
        ):
            with self.subTest(unsupported=unsupported):
                with self.assertRaisesRegex(AssertionError, "unparsed"):
                    parse_project_dependencies(unsupported, "fixture")

    def test_target_modules_have_one_architecture_class(self) -> None:
        self.assertEqual(TARGET_MODULES, frozenset(MODULE_CLASSES))
        self.assertEqual(TARGET_MODULES, frozenset(TARGET_PROJECT_DEPENDENCIES))
        for module, dependencies in TARGET_PROJECT_DEPENDENCIES.items():
            with self.subTest(module=module):
                self.assertLessEqual(dependencies, TARGET_MODULES)

    def test_settings_contains_the_reviewed_current_module_set(self) -> None:
        settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
        included = set(re.findall(r'include\(\s*"\:([^\"]+)"\s*\)', settings))
        self.assertEqual(EXPECTED_CURRENT_MODULES, included)
        self.assertLessEqual(included, TARGET_MODULES)

    def test_current_project_dependency_edges_match_the_reviewed_dag(self) -> None:
        for module, expected in CURRENT_PROJECT_DEPENDENCIES.items():
            with self.subTest(module=module):
                actual = project_dependencies(module)
                self.assertEqual(expected, actual)
                self.assertLessEqual(actual, ALLOWED_PROJECT_DEPENDENCIES[module])

    def test_every_allowed_dependency_edge_has_a_rationale(self) -> None:
        allowed_edges = {
            (module, dependency)
            for module, dependencies in ALLOWED_PROJECT_DEPENDENCIES.items()
            for dependency in dependencies
        }
        self.assertEqual(allowed_edges, set(EDGE_RATIONALES))
        self.assertTrue(all(reason.strip() for reason in EDGE_RATIONALES.values()))

    def test_feature_modules_cannot_depend_on_each_other(self) -> None:
        for module in FEATURE_MODULES:
            with self.subTest(module=module):
                other_features = FEATURE_MODULES - {module}
                self.assertFalse(
                    ALLOWED_PROJECT_DEPENDENCIES[module] & other_features,
                    f":{module} may not depend on another feature module",
                )

    def test_current_and_target_dependency_graphs_are_acyclic(self) -> None:
        current_graph = {
            module: project_dependencies(module)
            for module in CURRENT_PROJECT_DEPENDENCIES
        }
        self.assert_acyclic(current_graph)
        self.assert_acyclic(TARGET_PROJECT_DEPENDENCIES)

    def assert_acyclic(self, graph: dict[str, frozenset[str] | set[str]]) -> None:
        visiting: set[str] = set()
        visited: set[str] = set()

        def visit(module: str) -> None:
            if module in visiting:
                self.fail(f"project dependency cycle includes :{module}")
            if module in visited:
                return
            visiting.add(module)
            for dependency in graph[module]:
                visit(dependency)
            visiting.remove(module)
            visited.add(module)

        for module in graph:
            visit(module)

    def test_pure_jvm_sources_do_not_import_android_apis(self) -> None:
        violations = []
        for module in PURE_MODULES:
            source_root = ROOT / module / "src/main"
            sources = sorted((*source_root.rglob("*.kt"), *source_root.rglob("*.java")))
            for source in sources:
                if ANDROID_IMPORT.search(source.read_text(encoding="utf-8")):
                    violations.append(source.relative_to(ROOT).as_posix())
        self.assertEqual([], violations, "pure JVM modules must not import Android APIs")

    def test_production_sources_use_typed_record_factories(self) -> None:
        violations = []
        sources = sorted(
            (
                *ROOT.glob("*/src/main/**/*.kt"),
                *ROOT.glob("*/src/main/**/*.java"),
            ),
        )
        for source in sources:
            relative = source.relative_to(ROOT).as_posix()
            if relative in RECORD_DEFINITION_FILES:
                continue
            if AMBIGUOUS_RECORD_CONSTRUCTION.search(source.read_text(encoding="utf-8")):
                violations.append(relative)
        self.assertEqual(
            [],
            violations,
            "production ReviewRequest and TaskMemory creation must use typed field factories",
        )


if __name__ == "__main__":
    unittest.main()
