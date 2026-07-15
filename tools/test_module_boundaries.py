#!/usr/bin/env python3
"""Architecture contracts for Kani's pure-JVM module boundary."""

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
EXPECTED_MODULES = frozenset((*PURE_MODULES, "app"))
ALLOWED_PROJECT_DEPENDENCIES = {
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
        ):
            with self.subTest(unsupported=unsupported):
                with self.assertRaisesRegex(AssertionError, "unparsed"):
                    parse_project_dependencies(unsupported, "fixture")

    def test_settings_contains_the_reviewed_module_set(self) -> None:
        settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
        included = set(re.findall(r'include\(\s*"\:([^\"]+)"\s*\)', settings))
        self.assertEqual(EXPECTED_MODULES, included)

    def test_project_dependency_edges_match_the_reviewed_dag(self) -> None:
        for module, allowed in ALLOWED_PROJECT_DEPENDENCIES.items():
            with self.subTest(module=module):
                self.assertEqual(allowed, project_dependencies(module))

    def test_project_dependency_graph_is_acyclic(self) -> None:
        graph = {
            module: project_dependencies(module)
            for module in ALLOWED_PROJECT_DEPENDENCIES
        }
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
