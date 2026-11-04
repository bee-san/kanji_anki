#!/usr/bin/env python3
"""Canonical release-tag parsing and Android version metadata for CI."""

from __future__ import annotations

import argparse
import hashlib
import re
import subprocess
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Sequence


ANDROID_MAX_VERSION_CODE = 2_100_000_000
VERSION_COMPONENT_MAX = 999
CANONICAL_COMPONENT = r"(0|[1-9][0-9]*)"
TAG_PATTERN = re.compile(
    rf"^v{CANONICAL_COMPONENT}\.{CANONICAL_COMPONENT}\.{CANONICAL_COMPONENT}(?:-(beta))?$",
)

# Windows Installer packs major and minor into one byte each and the build (our
# patch) into two. It ignores the high bits rather than failing, so a version above
# these bounds would install as a different, lower version and MSI would then decline
# the upgrade. Fail the release build instead (Goal 202).
MSI_MAJOR_MINOR_MAX = 255
MSI_PATCH_MAX = 65_535

# The packaging revision of a given upstream version. Kani builds each upstream
# version once, so this is pinned; a repackage of unchanged upstream bytes bumps it.
DEBIAN_REVISION = "1"

# The desktop targets Kani publishes native packages for. Each entry is
# (os token, arch token, extensions in preference order); the canonical asset name is
# kani-desktop-<os>-<arch>-<version><extension>, which is also what
# :update-core's DesktopReleaseAssetSelector matches against by exact name.
DESKTOP_TARGETS: tuple[tuple[str, str, tuple[str, ...]], ...] = (
    ("windows", "x64", (".msi",)),
    ("macos", "arm64", (".dmg",)),
    ("linux", "x64", (".deb", ".tar.gz")),
)

DESKTOP_ASSET_PREFIX = "kani-desktop"
MANIFEST_NAME = "release-manifest-v1.json"
MANIFEST_SIGNATURE_NAME = f"{MANIFEST_NAME}.sig"
CHECKSUMS_NAME = "SHA256SUMS.txt"


@dataclass(frozen=True)
class Version:
    major: int
    minor: int
    patch: int
    beta: bool = False

    @property
    def base_name(self) -> str:
        return f"{self.major}.{self.minor}.{self.patch}"

    @property
    def name(self) -> str:
        suffix = "-beta" if self.beta else ""
        return f"{self.base_name}{suffix}"

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

    @property
    def msi_version(self) -> str:
        """The MSI ProductVersion, failing closed above the installer's bounds."""
        for name, value, maximum in (
            ("major", self.major, MSI_MAJOR_MINOR_MAX),
            ("minor", self.minor, MSI_MAJOR_MINOR_MAX),
            ("patch", self.patch, MSI_PATCH_MAX),
        ):
            if value > maximum:
                raise ValueError(
                    f"MSI {name} component {value} exceeds the installer "
                    f"maximum {maximum}",
                )
        return self.name

    @property
    def macos_short_version(self) -> str:
        """CFBundleShortVersionString: jpackage rejects a zero leading component."""
        return f"{self.major + 1}.{self.minor}.{self.patch}"

    @property
    def macos_build_version(self) -> str:
        """CFBundleVersion: the monotonic Kani version code."""
        return str(self.code)

    @property
    def deb_version(self) -> str:
        """The Debian package version: semantic version plus explicit revision."""
        return f"{self.name}-{DEBIAN_REVISION}"

    def desktop_asset_names(self) -> list[str]:
        """Every canonical desktop asset name for this version, in target order."""
        return [
            desktop_asset_name(os_token, arch_token, extension, self)
            for os_token, arch_token, extensions in DESKTOP_TARGETS
            for extension in extensions
        ]


def desktop_asset_name(
    os_token: str,
    arch_token: str,
    extension: str,
    version: Version,
) -> str:
    """The canonical desktop asset name; the same grammar :update-core matches."""
    return (
        f"{DESKTOP_ASSET_PREFIX}-{os_token}-{arch_token}-{version.name}{extension}"
    )


def parse_tag(tag: str) -> Version:
    match = TAG_PATTERN.fullmatch(tag.strip())
    if match is None:
        raise ValueError("release tag must match vMAJOR.MINOR.PATCH or vMAJOR.MINOR.PATCH-beta")
    components = tuple(int(value) for value in match.groups()[:3])
    names = ("major", "minor", "patch")
    for index, name in enumerate(names):
        value = components[index]
        if value > VERSION_COMPONENT_MAX:
            suffix = "; bump minor before releasing again" if name == "patch" else ""
            raise ValueError(
                f"release {name} component {value} exceeds "
                f"{VERSION_COMPONENT_MAX}{suffix}",
            )
    version = Version(*components, beta=match.group(4) == "beta")
    _ = version.code
    return version


def next_patch_tag(tags: Iterable[str], *, beta: bool = False) -> str:
    versions = []
    for tag in tags:
        try:
            versions.append(parse_tag(tag))
        except ValueError:
            continue
    if not versions:
        raise ValueError("no existing vMAJOR.MINOR.PATCH tag found to bump from")
    latest = max(versions, key=lambda version: (version.major, version.minor, version.patch))
    if latest.patch >= VERSION_COMPONENT_MAX:
        raise ValueError(
            f"latest release {latest.tag} already uses patch {latest.patch}; "
            "bump minor before releasing again",
        )
    return Version(latest.major, latest.minor, latest.patch + 1, beta=beta).tag


def validate_new_tag(
    tag: str,
    tags: Iterable[str],
    *,
    ignore_existing_exact: bool = False,
) -> None:
    candidate = parse_tag(tag)
    existing_versions = []
    for existing_tag in tags:
        try:
            existing = parse_tag(existing_tag)
        except ValueError:
            continue
        if existing.tag == candidate.tag:
            if ignore_existing_exact:
                continue
            raise ValueError(f"release tag {candidate.tag} already exists")
        existing_versions.append(existing)

    if not existing_versions:
        return
    latest = max(
        existing_versions,
        key=lambda version: (version.major, version.minor, version.patch),
    )
    candidate_core = (candidate.major, candidate.minor, candidate.patch)
    latest_core = (latest.major, latest.minor, latest.patch)
    if candidate_core <= latest_core:
        raise ValueError(
            f"release {candidate.tag} must use a numeric core newer than {latest.tag}",
        )


def git_release_tags(repository: Path) -> list[str]:
    completed = subprocess.run(
        ["git", "tag", "--list", "v*"],
        cwd=repository,
        check=True,
        text=True,
        stdout=subprocess.PIPE,
    )
    return completed.stdout.splitlines()


def metadata_lines(tag: str, build_sha: str, *, prerelease: bool = False) -> list[str]:
    version = parse_tag(tag)
    apk_name = f"kani-android-{version.name}.apk"
    return [
        f"release_tag={version.tag}",
        f"release_name={version.tag}",
        f"prerelease={str(version.beta or prerelease).lower()}",
        f"build_sha={build_sha}",
        f"version_name={version.name}",
        f"version_code={version.code}",
        f"apk_name={apk_name}",
        f"checksum_name={apk_name}.sha256",
    ]


def desktop_metadata_lines(tag: str) -> list[str]:
    """The desktop packaging contract for a release tag.

    Every per-OS installer version and every canonical asset name derives from the
    one tag here, so a workflow cannot name an asset the updater will not match.
    """
    version = parse_tag(tag)
    return [
        f"release_tag={version.tag}",
        f"version_name={version.name}",
        f"msi_version={version.msi_version}",
        f"macos_short_version={version.macos_short_version}",
        f"macos_build_version={version.macos_build_version}",
        f"deb_version={version.deb_version}",
        f"manifest_name={MANIFEST_NAME}",
        f"manifest_signature_name={MANIFEST_SIGNATURE_NAME}",
        f"checksums_name={CHECKSUMS_NAME}",
        f"desktop_assets={' '.join(version.desktop_asset_names())}",
    ]


def sha256_of(path: Path) -> str:
    """The SHA-256 of a file, read in chunks so a large installer is not buffered."""
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def checksums_text(paths: Iterable[Path]) -> str:
    """The conventional `SHA256SUMS.txt` body, sorted by filename.

    Sorted so the file is byte-identical whatever order the release job collected
    the assets in — an unsorted checksum file would differ between two runs over
    the same bytes and could not be compared or reproduced. Basenames only: the
    checksums are verified next to the downloaded asset, not at a build path (which
    would also leak the runner's directory layout).
    """
    entries = sorted(
        ((path.name, sha256_of(path)) for path in paths),
        key=lambda entry: entry[0],
    )
    names = [name for name, _ in entries]
    duplicates = {name for name in names if names.count(name) > 1}
    if duplicates:
        raise ValueError(
            f"duplicate asset filename in checksums: {sorted(duplicates)}",
        )
    # Two spaces is coreutils' binary-mode separator, so `sha256sum --check` reads
    # this file directly.
    return "".join(f"{digest}  {name}\n" for name, digest in entries)


def _parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    subparsers = parser.add_subparsers(dest="command", required=True)

    next_tag = subparsers.add_parser("next-tag", help="print the next patch tag")
    next_tag.add_argument("--repository", type=Path, default=Path.cwd())
    next_tag.add_argument("--beta", action="store_true", help="append the canonical -beta prerelease suffix")

    validate_tag = subparsers.add_parser(
        "validate-new-tag",
        help="fail unless a tag has a numeric core newer than every existing release tag",
    )
    validate_tag.add_argument("--tag", required=True)
    validate_tag.add_argument("--repository", type=Path, default=Path.cwd())
    validate_tag.add_argument(
        "--ignore-existing-exact",
        action="store_true",
        help="ignore the event's already-created tag while comparing it with all other tags",
    )

    metadata = subparsers.add_parser("metadata", help="print GitHub-output metadata lines")
    metadata.add_argument("--tag", required=True)
    metadata.add_argument("--build-sha", required=True)
    metadata.add_argument(
        "--prerelease",
        action="store_true",
        help="publish a numeric compatibility tag as a GitHub prerelease",
    )

    desktop = subparsers.add_parser(
        "desktop-metadata",
        help="print the desktop packaging contract for a tag",
    )
    desktop.add_argument("--tag", required=True)

    checksums = subparsers.add_parser(
        "checksums",
        help=f"print a sorted {CHECKSUMS_NAME} body for the given assets",
    )
    checksums.add_argument("assets", nargs="+", type=Path)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = _parser().parse_args(argv)
    try:
        if args.command == "next-tag":
            print(next_patch_tag(git_release_tags(args.repository), beta=args.beta))
        elif args.command == "validate-new-tag":
            validate_new_tag(
                args.tag,
                git_release_tags(args.repository),
                ignore_existing_exact=args.ignore_existing_exact,
            )
        elif args.command == "desktop-metadata":
            print("\n".join(desktop_metadata_lines(args.tag)))
        elif args.command == "checksums":
            print(checksums_text(args.assets), end="")
        else:
            print("\n".join(metadata_lines(args.tag, args.build_sha, prerelease=args.prerelease)))
    except (OSError, subprocess.CalledProcessError, ValueError) as error:
        raise SystemExit(f"release version error: {error}") from error
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
