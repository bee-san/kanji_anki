#!/usr/bin/env python3
"""Architecture contracts for Kani's current, migration, and final module graphs."""

from __future__ import annotations

import re
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
CURRENT_SHARED_JVM_MODULES = (
    "domain",
    "dictionary-core",
    "fsrs-java",
    "sync-domain",
    "writing-core",
    "update-core",
    "core",
)
NEW_SHARED_JVM_MODULES = frozenset(
    {
        "application",
        "backup-core",
        "data-api",
        "data-sql",
        "platform-contracts",
        "reference-assets",
        "sync-api",
        "sync-engine",
    },
)
LEAF_FEATURE_MODULES = frozenset(
    {
        "feature-games",
        "feature-home",
        "feature-missing-kanji",
        "feature-settings",
        "feature-stats",
        "feature-study",
    },
)
SHARED_PRESENTATION_MODULES = frozenset(
    {
        "feature-shell",
        "presentation-api",
        "ui-common",
        *LEAF_FEATURE_MODULES,
    },
)
ANDROID_MODULES = frozenset(
    {
        "app",
        "automation-android",
        "data-android",
        "platform-android",
        "provider-ankidroid",
        "widget",
    },
)
DESKTOP_MODULES = frozenset(
    {
        "data-desktop",
        "desktop-app",
        "platform-desktop",
        "provider-ankiconnect",
    },
)
FINAL_MODULES = frozenset(
    {
        *CURRENT_SHARED_JVM_MODULES,
        *NEW_SHARED_JVM_MODULES,
        *SHARED_PRESENTATION_MODULES,
        *ANDROID_MODULES,
        *DESKTOP_MODULES,
    },
)
EXPECTED_CURRENT_MODULES = frozenset(
    {
        *CURRENT_SHARED_JVM_MODULES,
        "platform-contracts",
        "data-api",
        "sync-api",
        "application",
        "provider-ankidroid",
        "app",
        "desktop-app",
    },
)
MODULE_CLASSES = {
    **dict.fromkeys(CURRENT_SHARED_JVM_MODULES, "shared-jvm-policy"),
    **dict.fromkeys(NEW_SHARED_JVM_MODULES, "shared-jvm-application"),
    "presentation-api": "shared-presentation-api",
    "ui-common": "shared-presentation-ui",
    **dict.fromkeys(LEAF_FEATURE_MODULES, "shared-feature"),
    "feature-shell": "shared-feature-aggregator",
    "data-android": "android-data-adapter",
    "provider-ankidroid": "android-provider-adapter",
    "platform-android": "android-platform-adapter",
    "automation-android": "android-automation-adapter",
    "widget": "android-platform",
    "app": "android-composition-root",
    "data-desktop": "desktop-data-adapter",
    "provider-ankiconnect": "desktop-provider-adapter",
    "platform-desktop": "desktop-platform-adapter",
    "desktop-app": "desktop-composition-root",
}
CURRENT_PROJECT_DEPENDENCIES = {
    "domain": frozenset(),
    "dictionary-core": frozenset(),
    "fsrs-java": frozenset(),
    "sync-domain": frozenset({"domain"}),
    "writing-core": frozenset({"domain"}),
    "update-core": frozenset(),
    "platform-contracts": frozenset(),
    "data-api": frozenset({"core", "sync-domain"}),
    "sync-api": frozenset({"core", "sync-domain"}),
    "application": frozenset({"data-api", "platform-contracts"}),
    "provider-ankidroid": frozenset({"sync-api"}),
    "core": frozenset(
        {"dictionary-core", "domain", "sync-domain", "fsrs-java", "update-core"},
    ),
    "app": frozenset(
        {
            "application",
            "core",
            "data-api",
            "dictionary-core",
            "platform-contracts",
            "provider-ankidroid",
            "sync-api",
            "update-core",
            "writing-core",
        },
    ),
    "desktop-app": frozenset(),
}
FINAL_PROJECT_DEPENDENCIES = {
    "domain": frozenset(),
    "dictionary-core": frozenset(),
    "fsrs-java": frozenset(),
    "sync-domain": frozenset({"domain"}),
    "writing-core": frozenset({"domain"}),
    "update-core": frozenset(),
    "core": frozenset(
        {"dictionary-core", "domain", "sync-domain", "fsrs-java", "update-core"},
    ),
    "data-api": frozenset({"core", "sync-domain"}),
    "sync-api": frozenset({"core", "sync-domain"}),
    "platform-contracts": frozenset(),
    "application": frozenset(
        {
            "core",
            "data-api",
            "platform-contracts",
            "reference-assets",
            "sync-engine",
            "update-core",
        },
    ),
    "sync-engine": frozenset(
        {
            "core",
            "data-api",
            "dictionary-core",
            "platform-contracts",
            "sync-api",
            "sync-domain",
        },
    ),
    "data-sql": frozenset(
        {"core", "data-api", "dictionary-core", "sync-api", "sync-domain"},
    ),
    "backup-core": frozenset({"data-api", "platform-contracts"}),
    "reference-assets": frozenset({"dictionary-core", "writing-core"}),
    "presentation-api": frozenset(),
    "ui-common": frozenset({"presentation-api"}),
    **{
        feature: frozenset({"presentation-api", "ui-common"})
        for feature in LEAF_FEATURE_MODULES
    },
    "feature-shell": frozenset(
        {"presentation-api", "ui-common", *LEAF_FEATURE_MODULES},
    ),
    "data-android": frozenset({"backup-core", "data-sql"}),
    "provider-ankidroid": frozenset({"sync-api"}),
    "platform-android": frozenset({"platform-contracts", "writing-core"}),
    "automation-android": frozenset({"application", "platform-contracts"}),
    "widget": frozenset(
        {"application", "core", "platform-contracts", "presentation-api"},
    ),
    "app": frozenset(
        {
            "application",
            "automation-android",
            "data-android",
            "feature-shell",
            "platform-android",
            "provider-ankidroid",
            "widget",
        },
    ),
    "data-desktop": frozenset({"backup-core", "data-sql"}),
    "provider-ankiconnect": frozenset({"platform-contracts", "sync-api"}),
    "platform-desktop": frozenset({"platform-contracts"}),
    "desktop-app": frozenset(
        {
            "application",
            "data-desktop",
            "feature-shell",
            "platform-desktop",
            "provider-ankiconnect",
        },
    ),
}
MIGRATION_ONLY_PROJECT_DEPENDENCIES = {
    "app": frozenset(
        {
            "backup-core",
            "core",
            "data-api",
            "data-sql",
            "dictionary-core",
            "feature-games",
            "feature-home",
            "feature-missing-kanji",
            "feature-settings",
            "feature-stats",
            "feature-study",
            "platform-contracts",
            "presentation-api",
            "reference-assets",
            "sync-api",
            "sync-engine",
            "ui-common",
            "update-core",
            "writing-core",
        },
    ),
}
MIGRATION_PROJECT_DEPENDENCIES = {
    module: FINAL_PROJECT_DEPENDENCIES[module]
    | MIGRATION_ONLY_PROJECT_DEPENDENCIES.get(module, frozenset())
    for module in FINAL_MODULES
}
EDGE_RATIONALES = {
    ("sync-domain", "domain"): "Sync-domain shares domain value models.",
    ("writing-core", "domain"): "Writing policy consumes domain value models.",
    ("core", "dictionary-core"): "Scheduler policy consumes dictionary models.",
    ("core", "domain"): "Core policy builds on shared domain models.",
    ("core", "sync-domain"): "Core policy consumes pure sync contracts.",
    ("core", "fsrs-java"): "Core delegates memory scheduling to FSRS.",
    ("core", "update-core"): "Core consumes pure update policy.",
    ("data-api", "core"): "Repository contracts use canonical core models.",
    ("data-api", "sync-domain"): "Repository contracts expose sync-domain values.",
    ("sync-api", "core"): "Provider envelopes wrap canonical core snapshots.",
    ("sync-api", "sync-domain"): "Provider contracts reuse sync-domain models.",
    ("application", "core"): "Application state machines invoke scheduler policy.",
    ("application", "data-api"): "Application use cases consume repository ports.",
    ("application", "platform-contracts"): "Application effects use platform ports.",
    ("application", "reference-assets"): "Application use cases consume shared assets.",
    ("application", "sync-engine"): "Application use cases invoke shared sync.",
    ("application", "update-core"): "Application update flows use verified policy.",
    ("sync-engine", "core"): "Sync normalization applies shared admission policy.",
    ("sync-engine", "data-api"): "Sync publishes through repository ports.",
    ("sync-engine", "dictionary-core"): "Sync reads shared dictionary contracts.",
    ("sync-engine", "platform-contracts"): "Sync effects use platform ports.",
    ("sync-engine", "sync-api"): "Sync orchestrates provider-neutral gateways.",
    ("sync-engine", "sync-domain"): "Sync reuses canonical sync value models.",
    ("data-sql", "core"): "Shared SQL persists canonical core state.",
    ("data-sql", "data-api"): "Shared SQL implements repository contracts.",
    ("data-sql", "dictionary-core"): "Shared SQL installs dictionary content.",
    ("data-sql", "sync-api"): "Shared SQL persists opaque source-binding records.",
    ("data-sql", "sync-domain"): "Shared SQL persists sync-domain models.",
    ("backup-core", "data-api"): "Portable backup uses data-owned snapshot contracts.",
    ("backup-core", "platform-contracts"): "Backup durability uses platform ports.",
    ("reference-assets", "dictionary-core"): "Reference assets serve dictionary data.",
    ("reference-assets", "writing-core"): "Reference assets serve writing models.",
    ("ui-common", "presentation-api"): "Shared UI renders portable presentation DTOs.",
    **{
        (feature, "presentation-api"): (
            f":{feature} consumes portable presentation state and actions."
        )
        for feature in LEAF_FEATURE_MODULES
    },
    **{
        (feature, "ui-common"): f":{feature} uses shared Compose UI."
        for feature in LEAF_FEATURE_MODULES
    },
    **{
        ("feature-shell", feature): f"The shell hosts :{feature}."
        for feature in LEAF_FEATURE_MODULES
    },
    ("feature-shell", "presentation-api"): "The shell consumes portable navigation state.",
    ("feature-shell", "ui-common"): "The shell composes shared UI infrastructure.",
    ("data-android", "backup-core"): "Android data implements portable backup.",
    ("data-android", "data-sql"): "Android data supplies the shared SQL driver.",
    ("provider-ankidroid", "sync-api"): "AnkiDroid implements provider contracts.",
    ("platform-android", "platform-contracts"): "Android implements platform ports.",
    ("platform-android", "writing-core"): "Android hosts the ML Kit writing adapter.",
    ("automation-android", "application"): "Android automation invokes shared use cases.",
    (
        "automation-android",
        "platform-contracts",
    ): "Android automation implements background platform ports.",
    ("widget", "application"): "Widgets invoke shared read use cases.",
    ("widget", "core"): "Widgets retain canonical reminder eligibility policy.",
    ("widget", "platform-contracts"): "Widgets consume committed app event contracts.",
    ("widget", "presentation-api"): "Widgets consume portable display snapshots.",
    ("app", "application"): "Android assembles shared application use cases.",
    ("app", "automation-android"): "Android installs automation components.",
    ("app", "data-android"): "Android installs its data driver.",
    ("app", "feature-shell"): "Android hosts the shared product shell.",
    ("app", "platform-android"): "Android installs platform adapters.",
    ("app", "provider-ankidroid"): "Android installs its collection provider.",
    ("app", "widget"): "Android installs widget components.",
    ("data-desktop", "backup-core"): "Desktop data implements portable backup.",
    ("data-desktop", "data-sql"): "Desktop data supplies the shared SQL driver.",
    ("provider-ankiconnect", "sync-api"): "AnkiConnect implements provider contracts.",
    (
        "provider-ankiconnect",
        "platform-contracts",
    ): "AnkiConnect obtains authentication through the SecretStore port.",
    ("platform-desktop", "platform-contracts"): "Desktop implements platform ports.",
    ("desktop-app", "application"): "Desktop assembles shared application use cases.",
    ("desktop-app", "data-desktop"): "Desktop installs its data driver.",
    ("desktop-app", "feature-shell"): "Desktop hosts the shared product shell.",
    ("desktop-app", "platform-desktop"): "Desktop installs platform adapters.",
    ("desktop-app", "provider-ankiconnect"): "Desktop installs its collection provider.",
    **{
        ("app", dependency): (
            f"Migration-only Android host access to :{dependency} is removed "
            "when its final owner is wired."
        )
        for dependency in MIGRATION_ONLY_PROJECT_DEPENDENCIES["app"]
    },
}
PROJECT_CALL = re.compile(r"\bproject\s*\(")
PROJECT_DEPENDENCY = re.compile(
    r"\bproject\s*\(\s*(?:path\s*=\s*)?[\"']:([^\"']+)[\"']\s*\)",
)
TYPE_SAFE_PROJECT_ACCESSOR = re.compile(r"\bprojects\.[A-Za-z0-9_.]+")
ANDROID_IMPORT = re.compile(r"^import\s+(android|androidx)\.", re.MULTILINE)
COMMON_PLATFORM_IMPORT = re.compile(
    r"^import\s+(android|androidx|java\.awt|javax\.swing)\.",
    re.MULTILINE,
)
KANI_REFERENCE = re.compile(
    r"\b(dev\.bee\.kanjianki(?:\.[A-Za-z0-9_*]+)+)",
)
PERSISTENCE_ALLOWED_REFERENCE_PREFIXES = (
    "dev.bee.kanjianki.core",
    "dev.bee.kanjianki.data",
    "dev.bee.kanjianki.syncapi",
    "dev.bee.kanjianki.syncdomain",
    "dev.bee.kanjianki.updatecore",
)
PERSISTENCE_ALLOWED_PLATFORM_CONTRACTS = frozenset(
    {
        "dev.bee.kanjianki.platform.DeviceSettingKey",
        "dev.bee.kanjianki.platform.DeviceSettingKeys",
        "dev.bee.kanjianki.platform.DeviceSettingValueType",
        "dev.bee.kanjianki.platform.DeviceSettingsEditor",
        "dev.bee.kanjianki.platform.DeviceSettingsReader",
        "dev.bee.kanjianki.platform.DeviceSettingsStore",
    },
)
AMBIGUOUS_RECORD_CONSTRUCTION = re.compile(
    r"\b(?:(?:RecordsSchedulerModels\.)?ReviewRequest|"
    r"(?:RecordsStudyModels\.)?TaskMemory)\s*\(|"
    r"\b(?:RecordsStudyModels\.)?TaskMemory\.fromStudyFields\s*\(",
)
RECORD_DEFINITION_FILES = {
    "core/src/main/kotlin/dev/bee/kanjianki/core/RecordsSchedulerModels.kt",
    "core/src/main/kotlin/dev/bee/kanjianki/core/RecordsStudyModels.kt",
}
REPOSITORY_CONTRACTS = {
    "HomeRepository": "data-api/src/main/kotlin/dev/bee/kanjianki/data/HomeRepository.kt",
    "StudyRepository": "data-api/src/main/kotlin/dev/bee/kanjianki/data/StudyRepository.kt",
    "StatsRepository": "data-api/src/main/kotlin/dev/bee/kanjianki/data/StatsRepository.kt",
    "SettingsRepository": "data-api/src/main/kotlin/dev/bee/kanjianki/data/SettingsRepository.kt",
    "SyncRepository": "data-api/src/main/kotlin/dev/bee/kanjianki/data/SyncRepository.kt",
}
REPOSITORY_MODEL_FILES = (
    "data-api/src/main/kotlin/dev/bee/kanjianki/data/RepositorySnapshots.kt",
    "data-api/src/main/kotlin/dev/bee/kanjianki/data/ReviewCommitModels.kt",
    "data-api/src/main/kotlin/dev/bee/kanjianki/data/StoreResult.kt",
)
REPOSITORY_FORBIDDEN_TOKENS = (
    "LocalStore",
    "StatsCacheStore",
    "StudyStatsStore",
    "SQLite",
    "ContentValues",
    "Cursor",
    "Context",
    "Activity",
    "TABLE_",
    "android.",
    "androidx.",
)
TOP_LEVEL_DECLARATION = re.compile(
    r"^(?:(?:data|sealed|enum|fun)\s+)?(?:class|interface|object)\s+"
    r"([A-Za-z][A-Za-z0-9_]*)\b",
    re.MULTILINE,
)
DATA_API_REQUIRED_DECLARATIONS = frozenset(
    {
        *REPOSITORY_CONTRACTS,
        "HomeSnapshot",
        "ReviewCommitCommand",
        "ReviewCommitResult",
        "SettingsSnapshot",
        "StatsSnapshot",
        "StoreResult",
        "StudyQueueSnapshot",
        "SyncPublicationCommand",
    },
)


def parse_project_dependencies(build_script: str, module: str) -> set[str]:
    dependencies = PROJECT_DEPENDENCY.findall(build_script)
    project_calls = PROJECT_CALL.findall(build_script)
    type_safe_accessors = TYPE_SAFE_PROJECT_ACCESSOR.findall(build_script)
    if len(dependencies) != len(project_calls) or type_safe_accessors:
        raise AssertionError(
            f":{module} contains an unparsed project dependency; update the boundary parser",
        )
    return set(dependencies)


def validate_project_dependencies(
    module: str,
    dependencies: set[str] | frozenset[str],
    allowed_graph: dict[str, frozenset[str]],
) -> None:
    if module not in allowed_graph:
        raise AssertionError(f"unknown project module :{module}")
    unknown = set(dependencies) - set(allowed_graph)
    if unknown:
        formatted = ", ".join(f":{dependency}" for dependency in sorted(unknown))
        raise AssertionError(f":{module} has unknown project dependencies: {formatted}")
    forbidden = set(dependencies) - set(allowed_graph[module])
    if forbidden:
        formatted = ", ".join(f":{dependency}" for dependency in sorted(forbidden))
        raise AssertionError(f":{module} has forbidden project dependencies: {formatted}")


def project_dependencies(module: str) -> set[str]:
    build_script = (ROOT / module / "build.gradle.kts").read_text(encoding="utf-8")
    return parse_project_dependencies(build_script, module)


def persistence_reference_allowed(reference: str) -> bool:
    return reference in PERSISTENCE_ALLOWED_PLATFORM_CONTRACTS or any(
        reference == prefix or reference.startswith(f"{prefix}.")
        for prefix in PERSISTENCE_ALLOWED_REFERENCE_PREFIXES
    )


def declaration_body(source: str, declaration: str) -> str:
    start = source.index(f"interface {declaration}")
    opening = source.index("{", start)
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == "{":
            depth += 1
        elif source[index] == "}":
            depth -= 1
            if depth == 0:
                return source[opening + 1 : index]
    raise AssertionError(f"{declaration} has no closing brace")


def delimited_body(
    source: str,
    marker: str,
    opening_delimiter: str,
    closing_delimiter: str,
) -> str:
    start = source.index(marker)
    opening = source.index(opening_delimiter, start + len(marker))
    depth = 0
    for index in range(opening, len(source)):
        if source[index] == opening_delimiter:
            depth += 1
        elif source[index] == closing_delimiter:
            depth -= 1
            if depth == 0:
                return source[opening + 1 : index]
    raise AssertionError(f"{marker} has no closing {closing_delimiter}")


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

    def test_final_modules_have_one_architecture_class(self) -> None:
        self.assertEqual(FINAL_MODULES, frozenset(MODULE_CLASSES))
        self.assertEqual(FINAL_MODULES, frozenset(FINAL_PROJECT_DEPENDENCIES))
        self.assertEqual(FINAL_MODULES, frozenset(MIGRATION_PROJECT_DEPENDENCIES))
        for module, dependencies in FINAL_PROJECT_DEPENDENCIES.items():
            with self.subTest(module=module):
                self.assertLessEqual(dependencies, FINAL_MODULES)

    def test_migration_graph_is_final_graph_plus_reviewed_temporary_edges(self) -> None:
        self.assertTrue(MIGRATION_ONLY_PROJECT_DEPENDENCIES)
        for module, dependencies in MIGRATION_ONLY_PROJECT_DEPENDENCIES.items():
            with self.subTest(module=module):
                self.assertIn(module, FINAL_MODULES)
                self.assertLessEqual(dependencies, FINAL_MODULES)
                self.assertFalse(
                    dependencies & FINAL_PROJECT_DEPENDENCIES[module],
                    "migration-only edges must not be final edges",
                )
        for module in FINAL_MODULES:
            with self.subTest(module=module):
                self.assertEqual(
                    FINAL_PROJECT_DEPENDENCIES[module]
                    | MIGRATION_ONLY_PROJECT_DEPENDENCIES.get(module, frozenset()),
                    MIGRATION_PROJECT_DEPENDENCIES[module],
                )

    def test_settings_contains_the_reviewed_current_module_set(self) -> None:
        settings = (ROOT / "settings.gradle.kts").read_text(encoding="utf-8")
        included = set(re.findall(r'include\(\s*"\:([^\"]+)"\s*\)', settings))
        self.assertEqual(EXPECTED_CURRENT_MODULES, included)
        self.assertLessEqual(included, FINAL_MODULES)

    def test_current_project_dependency_edges_match_the_reviewed_dag(self) -> None:
        for module, expected in CURRENT_PROJECT_DEPENDENCIES.items():
            with self.subTest(module=module):
                actual = project_dependencies(module)
                self.assertEqual(expected, actual)
                validate_project_dependencies(
                    module,
                    actual,
                    MIGRATION_PROJECT_DEPENDENCIES,
                )

    def test_unknown_or_unreviewed_project_edges_fail_closed(self) -> None:
        with self.assertRaisesRegex(AssertionError, "unknown project module"):
            validate_project_dependencies(
                "unreviewed-module",
                set(),
                MIGRATION_PROJECT_DEPENDENCIES,
            )
        with self.assertRaisesRegex(AssertionError, "unknown project dependencies"):
            validate_project_dependencies(
                "domain",
                {"unreviewed-module"},
                MIGRATION_PROJECT_DEPENDENCIES,
            )
        with self.assertRaisesRegex(AssertionError, "forbidden project dependencies"):
            validate_project_dependencies(
                "domain",
                {"core"},
                MIGRATION_PROJECT_DEPENDENCIES,
            )

    def test_every_allowed_dependency_edge_has_a_rationale(self) -> None:
        allowed_edges = {
            (module, dependency)
            for module, dependencies in MIGRATION_PROJECT_DEPENDENCIES.items()
            for dependency in dependencies
        }
        self.assertEqual(allowed_edges, set(EDGE_RATIONALES))
        self.assertTrue(all(reason.strip() for reason in EDGE_RATIONALES.values()))

    def test_leaf_feature_modules_cannot_depend_on_each_other(self) -> None:
        for module in LEAF_FEATURE_MODULES:
            with self.subTest(module=module):
                self.assertFalse(
                    FINAL_PROJECT_DEPENDENCIES[module]
                    & (LEAF_FEATURE_MODULES | {"feature-shell"}),
                    f":{module} may not depend on another feature or the shell",
                )

    def test_shared_and_host_module_directions_are_platform_safe(self) -> None:
        shared_modules = (
            set(CURRENT_SHARED_JVM_MODULES)
            | NEW_SHARED_JVM_MODULES
            | SHARED_PRESENTATION_MODULES
        )
        platform_modules = ANDROID_MODULES | DESKTOP_MODULES
        for module in shared_modules:
            with self.subTest(shared=module):
                self.assertFalse(
                    FINAL_PROJECT_DEPENDENCIES[module] & platform_modules,
                    f":{module} may not depend on a host/platform module",
                )

        jvm_modules = set(CURRENT_SHARED_JVM_MODULES) | NEW_SHARED_JVM_MODULES
        for module in SHARED_PRESENTATION_MODULES:
            with self.subTest(presentation=module):
                self.assertFalse(
                    FINAL_PROJECT_DEPENDENCIES[module] & jvm_modules,
                    f":{module} common presentation may not depend on JVM modules",
                )

        self.assertFalse(FINAL_PROJECT_DEPENDENCIES["app"] & DESKTOP_MODULES)
        self.assertFalse(FINAL_PROJECT_DEPENDENCIES["desktop-app"] & ANDROID_MODULES)

    def test_current_migration_and_final_dependency_graphs_are_acyclic(self) -> None:
        current_graph = {
            module: project_dependencies(module)
            for module in CURRENT_PROJECT_DEPENDENCIES
        }
        self.assert_acyclic(current_graph)
        self.assert_acyclic(MIGRATION_PROJECT_DEPENDENCIES)
        self.assert_acyclic(FINAL_PROJECT_DEPENDENCIES)

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

    def test_included_shared_jvm_sources_do_not_import_android_apis(self) -> None:
        violations = []
        shared_jvm_modules = sorted(
            module
            for module in EXPECTED_CURRENT_MODULES
            if MODULE_CLASSES[module].startswith("shared-jvm-")
        )
        for module in shared_jvm_modules:
            source_root = ROOT / module / "src/main"
            sources = sorted((*source_root.rglob("*.kt"), *source_root.rglob("*.java")))
            for source in sources:
                if ANDROID_IMPORT.search(source.read_text(encoding="utf-8")):
                    violations.append(source.relative_to(ROOT).as_posix())
        self.assertEqual([], violations, "pure JVM modules must not import Android APIs")

    def test_included_common_presentation_sources_do_not_import_platform_apis(
        self,
    ) -> None:
        for forbidden_import in (
            "import android.content.Context",
            "import androidx.compose.ui.platform.LocalContext",
            "import java.awt.Desktop",
            "import javax.swing.JFrame",
        ):
            with self.subTest(forbidden_import=forbidden_import):
                self.assertIsNotNone(COMMON_PLATFORM_IMPORT.search(forbidden_import))
        self.assertIsNone(COMMON_PLATFORM_IMPORT.search("import kotlinx.coroutines.flow.Flow"))

        violations = []
        presentation_modules = sorted(
            EXPECTED_CURRENT_MODULES & SHARED_PRESENTATION_MODULES
        )
        for module in presentation_modules:
            source_root = ROOT / module / "src/commonMain"
            sources = sorted((*source_root.rglob("*.kt"), *source_root.rglob("*.java")))
            for source in sources:
                if COMMON_PLATFORM_IMPORT.search(source.read_text(encoding="utf-8")):
                    violations.append(source.relative_to(ROOT).as_posix())
        self.assertEqual(
            [],
            violations,
            "common presentation must not import Android, AWT, or Swing APIs",
        )

    def test_persistence_reference_policy_rejects_application_layers(self) -> None:
        for forbidden in (
            "dev.bee.kanjianki.MainActivity",
            "dev.bee.kanjianki.feature.home.HomeScreen",
            "dev.bee.kanjianki.automation.ReminderWorker",
            "dev.bee.kanjianki.widget.KaniWidget",
            "dev.bee.kanjianki.sync.ManualSyncEngine",
            "dev.bee.kanjianki.backup.DatabaseBackupWorker",
            "dev.bee.kanjianki.theme.KaniThemePalettes",
        ):
            with self.subTest(forbidden=forbidden):
                self.assertFalse(persistence_reference_allowed(forbidden))
                self.assertIn(
                    forbidden,
                    KANI_REFERENCE.findall(f"val dependency = {forbidden}()"),
                )

    def test_persistence_sources_import_only_data_and_pure_modules(self) -> None:
        violations = []
        source_roots = (
            ROOT / "app/src/main/kotlin/dev/bee/kanjianki/data",
            ROOT / "app/src/main/java/dev/bee/kanjianki/data",
            ROOT / "data-api/src/main",
            ROOT / "data/src/main",
        )
        for source_root in source_roots:
            if not source_root.exists():
                continue
            sources = sorted((*source_root.rglob("*.kt"), *source_root.rglob("*.java")))
            for source in sources:
                content = source.read_text(encoding="utf-8")
                for imported in KANI_REFERENCE.findall(content):
                    if not persistence_reference_allowed(imported):
                        violations.append(
                            f"{source.relative_to(ROOT).as_posix()}: {imported}",
                        )
        self.assertEqual(
            [],
            violations,
            "persistence must not import app, feature, platform, or UI implementations",
        )

    def test_repository_contracts_are_suspend_store_result_boundaries(self) -> None:
        for repository, relative_path in REPOSITORY_CONTRACTS.items():
            with self.subTest(repository=repository):
                source = (ROOT / relative_path).read_text(encoding="utf-8")
                body = declaration_body(source, repository)
                functions = re.findall(
                    r"(?ms)^\s*(suspend\s+)?fun\b.*?(?=^\s*(?:suspend\s+)?fun\b|\Z)",
                    body,
                )
                self.assertTrue(functions, f"{repository} must declare operations")
                self.assertTrue(
                    all(modifier is not None for modifier in functions),
                    f"{repository} operations must be suspend functions",
                )
                self.assertEqual(
                    len(functions),
                    body.count("StoreResult<"),
                    f"{repository} operations must return StoreResult",
                )
        public_surface_files = (*REPOSITORY_CONTRACTS.values(), *REPOSITORY_MODEL_FILES)
        for relative_path in public_surface_files:
            source = (ROOT / relative_path).read_text(encoding="utf-8")
            with self.subTest(public_surface=relative_path):
                for token in REPOSITORY_FORBIDDEN_TOKENS:
                    self.assertNotIn(
                        token,
                        source,
                        f"{relative_path} exposes persistence/platform implementation token {token}",
                    )

        study = declaration_body(
            (ROOT / REPOSITORY_CONTRACTS["StudyRepository"]).read_text(encoding="utf-8"),
            "StudyRepository",
        )
        sync = declaration_body(
            (ROOT / REPOSITORY_CONTRACTS["SyncRepository"]).read_text(encoding="utf-8"),
            "SyncRepository",
        )
        self.assertEqual(1, study.count("commitReview("))
        self.assertEqual(1, sync.count("publish("))
        self.assertTrue(
            (ROOT / "app/src/main/kotlin/dev/bee/kanjianki/data/SqliteSettingsStore.kt").exists(),
        )

    def test_data_api_uniquely_owns_contracts_without_implementation_details(
        self,
    ) -> None:
        expected_files = {
            *REPOSITORY_CONTRACTS.values(),
            *REPOSITORY_MODEL_FILES,
        }
        data_api_root = ROOT / "data-api/src/main"
        data_api_sources = sorted(
            (*data_api_root.rglob("*.kt"), *data_api_root.rglob("*.java")),
        )
        self.assertEqual(
            expected_files,
            {source.relative_to(ROOT).as_posix() for source in data_api_sources},
        )

        owned_declarations = {}
        for source in data_api_sources:
            relative = source.relative_to(ROOT).as_posix()
            content = source.read_text(encoding="utf-8")
            for declaration in TOP_LEVEL_DECLARATION.findall(content):
                self.assertNotIn(
                    declaration,
                    owned_declarations,
                    f":data-api declares {declaration} more than once",
                )
                owned_declarations[declaration] = relative
            for token in REPOSITORY_FORBIDDEN_TOKENS:
                self.assertNotIn(
                    token,
                    content,
                    f"{relative} contains implementation token {token}",
                )
        self.assertLessEqual(DATA_API_REQUIRED_DECLARATIONS, set(owned_declarations))

        duplicate_owners = []
        production_sources = sorted(
            (
                *ROOT.glob("*/src/main/**/*.kt"),
                *ROOT.glob("*/src/main/**/*.java"),
            ),
        )
        for source in production_sources:
            if data_api_root in source.parents:
                continue
            declarations = set(
                TOP_LEVEL_DECLARATION.findall(source.read_text(encoding="utf-8")),
            )
            for duplicate in sorted(declarations & set(owned_declarations)):
                duplicate_owners.append(
                    f"{duplicate}: {source.relative_to(ROOT).as_posix()}",
                )
        self.assertEqual(
            [],
            duplicate_owners,
            ":data-api contracts must not be redefined by another module",
        )

        self.assertFalse(
            (ROOT / "core/src/main/kotlin/dev/bee/kanjianki/core/StoreResult.kt").exists(),
        )
        self.assertFalse(
            (
                ROOT
                / "data-api/src/main/kotlin/dev/bee/kanjianki/data/StaleReviewCommitException.kt"
            ).exists(),
        )
        self.assertTrue(
            (
                ROOT
                / "app/src/main/kotlin/dev/bee/kanjianki/data/StaleReviewCommitException.kt"
            ).exists(),
        )
        self.assertTrue(
            (
                ROOT
                / "data-api/src/testFixtures/kotlin/dev/bee/kanjianki/data/fakes/FakeRepositories.kt"
            ).exists(),
        )
        self.assertFalse(
            (
                ROOT
                / "app/src/test/kotlin/dev/bee/kanjianki/data/fakes/FakeRepositories.kt"
            ).exists(),
        )

    def test_data_api_is_in_shared_gates_and_sonar_inputs(self) -> None:
        root_build = (ROOT / "build.gradle.kts").read_text(encoding="utf-8")
        desktop = delimited_body(root_build, "val desktopCiTasks = listOf", "(", ")")
        fast = delimited_body(root_build, "val fastCiTasks = listOf", "(", ")")
        quality = delimited_body(
            root_build,
            'tasks.register("ciQuality")',
            "{",
            "}",
        )
        sonar_main = delimited_body(
            root_build,
            "val sonarMainBinaries = listOf",
            "(",
            ")",
        )
        sonar_test = delimited_body(
            root_build,
            "val sonarTestBinaries = listOf",
            "(",
            ")",
        )
        sonar_coverage = delimited_body(
            root_build,
            "val sonarCoveragePaths = buildList<String>",
            "{",
            "}",
        )

        self.assertEqual(1, desktop.count('":data-api:check"'))
        for task in (
            ":data-api:test",
            ":data-api:jacocoTestReport",
            ":data-api:jacocoTestCoverageVerification",
        ):
            with self.subTest(fast_task=task):
                self.assertEqual(1, fast.count(f'"{task}"'))
        self.assertEqual(1, quality.count('":data-api:jar"'))
        self.assertEqual(
            1,
            sonar_main.count('rootPath("data-api/build/classes/kotlin/main")'),
        )
        for path in (
            "data-api/build/classes/kotlin/test",
            "data-api/build/classes/java/test",
            "data-api/build/classes/kotlin/testFixtures",
        ):
            with self.subTest(sonar_test_binary=path):
                self.assertEqual(1, sonar_test.count(f'rootPath("{path}")'))
        self.assertEqual(
            1,
            sonar_coverage.count(
                'rootPath("data-api/build/reports/jacoco/test/jacocoTestReport.xml")',
            ),
        )

    def test_repository_adapters_keep_atomic_operations_single_call(self) -> None:
        study_adapter = (
            ROOT / "app/src/main/kotlin/dev/bee/kanjianki/data/SqliteStudyRepository.kt"
        ).read_text(encoding="utf-8")
        sync_adapter = (
            ROOT / "app/src/main/kotlin/dev/bee/kanjianki/data/SqliteSyncRepository.kt"
        ).read_text(encoding="utf-8")
        settings_store = (
            ROOT / "app/src/main/kotlin/dev/bee/kanjianki/data/SqliteSettingsStore.kt"
        ).read_text(encoding="utf-8")
        fakes = (
            ROOT
            / "data-api/src/testFixtures/kotlin/dev/bee/kanjianki/data/fakes/FakeRepositories.kt"
        ).read_text(encoding="utf-8")

        self.assertEqual(1, study_adapter.count("store.commitReview(command)"))
        self.assertNotIn("store.saveReview(", study_adapter)
        self.assertGreaterEqual(
            study_adapter.count("store.readSnapshot {"),
            1,
            "composite study snapshots must use one database transaction",
        )
        self.assertEqual(1, sync_adapter.count("store.publishSyncAtomically"))
        self.assertEqual(1, sync_adapter.count("command.queuePlanner.plan("))
        self.assertIn("internal class SqliteSettingsStore(", settings_store)
        for repository in REPOSITORY_CONTRACTS:
            self.assertIn(f"class Fake{repository}", fakes)

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
