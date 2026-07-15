#!/usr/bin/env python3
"""Classify changed paths for the pull-request Android device gate.

The classifier is intentionally fail-safe. Product code and resources select
the fuller device-risk suite, build/test infrastructure selects the compact
smoke matrix, and only clearly non-runtime documentation changes select no
emulator work. Unknown paths receive the compact smoke lane.
"""

from __future__ import annotations

import argparse
import dataclasses
import sys
from collections.abc import Iterable, Sequence
from typing import Optional


FULL_PRODUCT_PREFIXES = (
    "app/src/main/",
    "core/src/main/",
    "domain/src/main/",
    "sync-domain/src/main/",
    "writing-core/src/main/",
    "dictionary-core/src/main/",
    "update-core/src/main/",
    "fsrs-java/src/main/",
)

FULL_RELEASE_PREFIXES = (
    "app/src/release/",
    "app/src/minifiedSmoke/",
    "app/src/androidTest/java/dev/bee/kanjianki/testing/",
    "build-logic/src/main/",
)

FULL_RELEASE_FILES = frozenset(
    {
        ".github/workflows/android-device-smoke.yml",
        "app/build.gradle.kts",
        "app/proguard-rules.pro",
        "ci/scripts/classify_device_smoke.py",
        "gradle/libs.versions.toml",
    },
)

FULL_ANDROID_TEST_FILES = frozenset(
    {
        "app/src/androidTest/kotlin/dev/bee/kanjianki/BackupAndRestoreInstrumentedTest.kt",
        "app/src/androidTest/kotlin/dev/bee/kanjianki/LadderSchedulerEndToEndTest.kt",
        "app/src/androidTest/kotlin/dev/bee/kanjianki/MainActivityPrimaryRouteSmokeInstrumentedTest.kt",
        "app/src/androidTest/kotlin/dev/bee/kanjianki/MainActivityStudyRouteSmokeInstrumentedTest.kt",
        "app/src/androidTest/kotlin/dev/bee/kanjianki/anki/AnkiDroidGatewayProviderInstrumentedTest.kt",
        "app/src/androidTest/kotlin/dev/bee/kanjianki/backup/DatabaseBackupWorkerInstrumentedTest.kt",
        "app/src/androidTest/kotlin/dev/bee/kanjianki/data/LocalStoreInstrumentedTest.kt",
        "app/src/androidTest/kotlin/dev/bee/kanjianki/sync/ManualSyncEngineInstrumentedTest.kt",
    },
)

SMOKE_PREFIXES = (
    "app/",
    "core/",
    "domain/",
    "sync-domain/",
    "writing-core/",
    "dictionary-core/",
    "update-core/",
    "fsrs-java/",
    "build-logic/",
    "ci/",
    "gradle/",
    ".github/",
)

SMOKE_FILES = {
    "build.gradle",
    "build.gradle.kts",
    "settings.gradle",
    "settings.gradle.kts",
    "gradle.properties",
    "gradlew",
    "gradlew.bat",
}

DOCUMENTATION_PREFIXES = ("docs/",)
DOCUMENTATION_FILES = {"AGENTS.md", "CONTRIBUTING.md", "LICENSE", "README.md"}


@dataclasses.dataclass(frozen=True)
class Classification:
    level: str
    reason: str

    @property
    def run_smoke(self) -> bool:
        return self.level in {"smoke", "full"}

    @property
    def run_full(self) -> bool:
        return self.level == "full"


def classify_paths(paths: Iterable[str]) -> Classification:
    """Return ``none``, ``smoke``, or ``full`` for normalized repo paths."""

    normalized = sorted(
        {
            path.strip()[2:] if path.strip().startswith("./") else path.strip()
            for path in paths
            if path.strip()
        }
    )
    if not normalized:
        return Classification("full", "changed path set was empty; using fail-safe full lane")

    full_reasons: set[str] = set()
    smoke_reasons: set[str] = set()
    documentation_only = True

    for path in normalized:
        if path in FULL_RELEASE_FILES or path.startswith(FULL_RELEASE_PREFIXES):
            full_reasons.add("release build or minified device gate")
            documentation_only = False
            continue

        if path.startswith(FULL_PRODUCT_PREFIXES):
            full_reasons.add("production code or resources")
            documentation_only = False
            continue

        if path in FULL_ANDROID_TEST_FILES:
            full_reasons.add("DeviceRisk-annotated instrumentation")
            documentation_only = False
            continue

        if path in SMOKE_FILES or path.startswith(SMOKE_PREFIXES):
            smoke_reasons.add("Android tests or build infrastructure")
            documentation_only = False
            continue

        if path in DOCUMENTATION_FILES or path.startswith(DOCUMENTATION_PREFIXES):
            continue

        documentation_only = False
        smoke_reasons.add("unclassified path; using fail-safe smoke lane")

    if full_reasons:
        return Classification("full", "; ".join(sorted(full_reasons)))
    if smoke_reasons:
        return Classification("smoke", "; ".join(sorted(smoke_reasons)))
    if documentation_only:
        return Classification("none", "documentation-only change")
    return Classification("smoke", "using fail-safe smoke lane")


def github_output(classification: Classification) -> str:
    return "\n".join(
        (
            f"level={classification.level}",
            f"run_smoke={str(classification.run_smoke).lower()}",
            f"run_full={str(classification.run_full).lower()}",
            f"reason={classification.reason}",
        )
    )


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--null",
        action="store_true",
        help="read NUL-separated paths from stdin (for git diff -z)",
    )
    parser.add_argument(
        "--force",
        choices=("none", "smoke", "full"),
        help="force a lane, used by manually dispatched workflow runs",
    )
    return parser.parse_args(argv)


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv or sys.argv[1:])
    if args.force:
        classification = Classification(args.force, "lane forced by workflow dispatch")
    else:
        data = sys.stdin.buffer.read()
        separator = b"\0" if args.null else b"\n"
        paths = [part.decode("utf-8", errors="surrogateescape") for part in data.split(separator)]
        classification = classify_paths(paths)

    print(github_output(classification))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
