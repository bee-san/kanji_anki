#!/usr/bin/env python3
"""Canonical release-tag parsing and Android version metadata for CI."""

from __future__ import annotations

import argparse
import re
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Sequence


ANDROID_MAX_VERSION_CODE = 2_100_000_000
VERSION_COMPONENT_MAX = 999
CANONICAL_COMPONENT = r"(0|[1-9][0-9]*)"
TAG_PATTERN = re.compile(
    rf"^v{CANONICAL_COMPONENT}\.{CANONICAL_COMPONENT}\.{CANONICAL_COMPONENT}$",
)


@dataclass(frozen=True, order=True)
class Version:
    major: int
    minor: int
    patch: int

    @property
    def name(self) -> str:
        return f"{self.major}.{self.minor}.{self.patch}"

    @property
    def tag(self) -> str:
        return f"v{self.name}"

    @property
    def code(self) -> int:
        code = self.major * 1_000_000 + self.minor * 1_000 + self.patch
        if not 1 <= code <= ANDROID_MAX_VERSION_CODE:
            raise ValueError(
                f"versionCode {code} is outside Android's supported range "
                f"1..{ANDROID_MAX_VERSION_CODE}",
            )
        return code


def parse_tag(tag: str) -> Version:
    match = TAG_PATTERN.fullmatch(tag.strip())
    if match is None:
        raise ValueError("release tag must match vMAJOR.MINOR.PATCH")
    components = tuple(int(value) for value in match.groups())
    names = ("major", "minor", "patch")
    for name, value in zip(names, components):
        if value > VERSION_COMPONENT_MAX:
            suffix = "; bump minor before releasing again" if name == "patch" else ""
            raise ValueError(
                f"release {name} component {value} exceeds "
                f"{VERSION_COMPONENT_MAX}{suffix}",
            )
    version = Version(*components)
    _ = version.code
    return version


def next_patch_tag(tags: Iterable[str]) -> str:
    versions = []
    for tag in tags:
        try:
            versions.append(parse_tag(tag))
        except ValueError:
            continue
    if not versions:
        raise ValueError("no existing vMAJOR.MINOR.PATCH tag found to bump from")
    latest = max(versions)
    if latest.patch >= VERSION_COMPONENT_MAX:
        raise ValueError(
            f"latest release {latest.tag} already uses patch {latest.patch}; "
            "bump minor before releasing again",
        )
    return Version(latest.major, latest.minor, latest.patch + 1).tag


def git_release_tags(repository: Path) -> list[str]:
    completed = subprocess.run(
        ["git", "tag", "--list", "v[0-9]*.[0-9]*.[0-9]*"],
        cwd=repository,
        check=True,
        text=True,
        stdout=subprocess.PIPE,
    )
    return completed.stdout.splitlines()


def metadata_lines(tag: str, build_sha: str) -> list[str]:
    version = parse_tag(tag)
    apk_name = f"kani-android-{version.name}.apk"
    return [
        f"release_tag={version.tag}",
        f"build_sha={build_sha}",
        f"version_name={version.name}",
        f"version_code={version.code}",
        f"apk_name={apk_name}",
        f"checksum_name={apk_name}.sha256",
    ]


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    next_tag = subparsers.add_parser("next-tag", help="print the next patch tag")
    next_tag.add_argument("--repository", type=Path, default=Path.cwd())

    metadata = subparsers.add_parser("metadata", help="print GitHub-output metadata lines")
    metadata.add_argument("--tag", required=True)
    metadata.add_argument("--build-sha", required=True)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        if args.command == "next-tag":
            print(next_patch_tag(git_release_tags(args.repository)))
        else:
            print("\n".join(metadata_lines(args.tag, args.build_sha)))
    except (OSError, subprocess.CalledProcessError, ValueError) as error:
        raise SystemExit(f"release version error: {error}") from error
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
