#!/usr/bin/env python3
"""Reject Gradle bootstrap mutations outside verification-metadata.xml."""

from __future__ import annotations

import argparse
import subprocess
from pathlib import Path
from typing import Optional, Sequence


ALLOWED_TRACKED_CHANGES = frozenset({"gradle/verification-metadata.xml"})


def unexpected_paths(paths: Sequence[str]) -> list[str]:
    return sorted(set(paths) - ALLOWED_TRACKED_CHANGES)


def tracked_changes(repository: Path) -> list[str]:
    result = subprocess.run(
        [
            "git",
            "-C",
            str(repository),
            "diff",
            "--name-only",
            "--diff-filter=ACDMRT",
            "-z",
            "HEAD",
            "--",
        ],
        check=True,
        capture_output=True,
    )
    return [
        item.decode("utf-8", errors="surrogateescape")
        for item in result.stdout.split(b"\0")
        if item
    ]


def untracked_non_ignored_changes(repository: Path) -> list[str]:
    result = subprocess.run(
        [
            "git",
            "-C",
            str(repository),
            "ls-files",
            "--others",
            "--exclude-standard",
            "-z",
        ],
        check=True,
        capture_output=True,
    )
    return [
        item.decode("utf-8", errors="surrogateescape")
        for item in result.stdout.split(b"\0")
        if item
    ]


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repository", type=Path, default=Path("."))
    return parser.parse_args(argv)


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv if argv is not None else [])
    changes = sorted(
        set(tracked_changes(args.repository))
        | set(untracked_non_ignored_changes(args.repository))
    )
    unexpected = unexpected_paths(changes)
    if unexpected:
        joined = "\n".join(f"  {path}" for path in unexpected)
        raise SystemExit(
            "desktop verification changed tracked files outside "
            f"{sorted(ALLOWED_TRACKED_CHANGES)}:\n{joined}"
        )
    print(
        "non-ignored change scope is valid: "
        + (", ".join(changes) if changes else "no tracked or untracked changes")
    )
    return 0


if __name__ == "__main__":
    import sys

    raise SystemExit(main(sys.argv[1:]))
