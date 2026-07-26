#!/usr/bin/env python3
"""Classify changed paths for the cross-platform desktop confidence gate.

Only changes that are proven to be documentation-only or Android-host-only may
skip the expensive three-host matrix. Unknown paths fail safe to running it so
new shared or desktop modules cannot silently escape cross-platform coverage.
"""

from __future__ import annotations

import argparse
import dataclasses
import sys
from collections.abc import Iterable, Sequence
from typing import Optional


DESKTOP_AND_SHARED_PREFIXES = (
    "application/",
    "backup-core/",
    "branding/",
    "core/",
    "data-api/",
    "data-desktop/",
    "data-sql/",
    "desktop-app/",
    "dictionary-core/",
    "domain/",
    "feature-",
    "fsrs-java/",
    "platform-contracts/",
    "platform-desktop/",
    "presentation-api/",
    "provider-ankiconnect/",
    "reference-assets/",
    "sync-api/",
    "sync-domain/",
    "sync-engine/",
    "ui-common/",
    "update-core/",
    "writing-core/",
)

BUILD_AND_CI_PREFIXES = (
    ".github/actions/",
    ".github/scripts/",
    ".github/workflows/",
    "build-logic/",
    "ci/",
    "gradle/",
    "scripts/",
    "tools/",
)

DESKTOP_AND_SHARED_FILES = frozenset(
    {
        ".gitattributes",
        "build.gradle",
        "build.gradle.kts",
        "docs/dependency-updates.md",
        "gradle.properties",
        "gradlew",
        "gradlew.bat",
        "renovate.json",
        "settings.gradle",
        "settings.gradle.kts",
    }
)

ANDROID_HOST_ONLY_PREFIXES = (
    "app/",
    "automation-android/",
    "data-android/",
    "platform-android/",
    "provider-ankidroid/",
    "widget/",
)

DOCUMENTATION_PREFIXES = ("docs/", "plans/")
DOCUMENTATION_FILES = frozenset(
    {
        "AGENTS.md",
        "CONTRIBUTING.md",
        "LICENSE",
        "README.md",
    }
)


@dataclasses.dataclass(frozen=True)
class Classification:
    run_desktop: bool
    reason: str


def _normalize(paths: Iterable[str]) -> list[str]:
    return sorted(
        {
            path.strip()[2:] if path.strip().startswith("./") else path.strip()
            for path in paths
            if path.strip()
        }
    )


def classify_paths(paths: Iterable[str]) -> Classification:
    """Return whether the three-host desktop matrix must run."""

    normalized = _normalize(paths)
    if not normalized:
        return Classification(
            True,
            "changed path set was empty; using fail-safe desktop matrix",
        )

    run_reasons: set[str] = set()
    skipped_reasons: set[str] = set()

    for path in normalized:
        if path in DESKTOP_AND_SHARED_FILES:
            run_reasons.add("desktop or shared root input")
            continue
        if path.startswith(DESKTOP_AND_SHARED_PREFIXES):
            run_reasons.add("desktop or shared module")
            continue
        if path.startswith(BUILD_AND_CI_PREFIXES):
            run_reasons.add("build or CI input")
            continue
        if path.startswith(ANDROID_HOST_ONLY_PREFIXES):
            skipped_reasons.add("Android-host-only input")
            continue
        if path in DOCUMENTATION_FILES or path.startswith(DOCUMENTATION_PREFIXES):
            skipped_reasons.add("documentation-only input")
            continue

        run_reasons.add("unclassified path; using fail-safe desktop matrix")

    if run_reasons:
        return Classification(True, "; ".join(sorted(run_reasons)))
    return Classification(False, "; ".join(sorted(skipped_reasons)))


def github_output(classification: Classification) -> str:
    return "\n".join(
        (
            f"run_desktop={str(classification.run_desktop).lower()}",
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
        choices=("run", "skip"),
        help="force a decision, used for manual and initial-branch runs",
    )
    return parser.parse_args(argv)


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv if argv is not None else sys.argv[1:])
    if args.force is not None:
        classification = Classification(
            args.force == "run",
            f"desktop matrix explicitly forced to {args.force}",
        )
    else:
        raw = sys.stdin.buffer.read()
        separator = b"\0" if args.null else b"\n"
        paths = [
            value.decode("utf-8", errors="surrogateescape")
            for value in raw.split(separator)
            if value
        ]
        classification = classify_paths(paths)
    print(github_output(classification))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
