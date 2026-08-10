#!/usr/bin/env python3
"""Verify Kani's installed desktop image against its pinned identity and runtime.

Goal 204's closing requirement is to "verify package identity/version/runtime
provenance". This checks that against the built artifact rather than against the
build configuration that produced it, which is the only way the three defects
found in this area would have been caught: a missing `java.net.http`, a missing
`jdk.accessibility`, and a runtime shipped from the wrong JDK. Each of those left
the Gradle build green and the packaged image wrong.

What this gate can and cannot establish, stated because the difference matters:

  - It **can** verify the launcher, the app identity, the shipped `JAVA_VERSION`,
    the exact `MODULES` set, and that no class is shadowed across the bundled
    jars, because the `jlink` image's `release` file records the first three and
    the jars themselves answer the last.
  - It **cannot** verify the runtime's *vendor*. The packaged `release` file
    contains only `JAVA_VERSION` and `MODULES` -- no `IMPLEMENTOR` -- and nothing
    under `conf/` or `legal/` names a distribution either. Vendor provenance is
    established at build time by `KaniPackagingJdk.verify` against the building
    JDK's own `release` file, and this gate deliberately does not claim
    otherwise. See `docs/desktop-packaging-jdk.md`.

Bundling the vendor claim in here anyway -- by, say, asserting a `legal/` file
count -- would produce a check that passes for the wrong reason and reads in a
release log as though provenance had been verified end to end.
"""

from __future__ import annotations

import argparse
import json
import sys
import zipfile
from collections.abc import Mapping, Sequence
from pathlib import Path

from tools.run_desktop_installed_image_smoke import (
    APPLICATION_NAME,
    DesktopInstalledImageSmokeError,
    installed_image_launcher,
    normalized_platform,
)


# Pinned to match `KaniPackagingJdk` and `KaniDesktopRuntimeModules`. Duplicated
# here rather than parsed out of Kotlin because this gate must be runnable against
# a downloaded artifact with no repository present -- but
# `test_verify_desktop_package` reads both Kotlin sources and fails when they
# disagree, so the copies cannot drift unnoticed.
EXPECTED_JAVA_VERSION = "17.0.19"

# The exact module set the image must contain: jpackage's default plus the four
# `KaniDesktopRuntimeModules.REQUIRED` entries. Pinned exactly, in both
# directions. A missing module is a crash or a mute screen reader in the installed
# app; an unexpected extra one means the module set moved without anyone deciding
# it should, which is how a stale scan silently becomes the contract.
EXPECTED_MODULES = (
    "java.base",
    "java.datatransfer",
    "java.desktop",
    "java.instrument",
    "java.logging",
    "java.net.http",
    "java.prefs",
    "java.xml",
    "jdk.accessibility",
    "jdk.crypto.ec",
    "jdk.unsupported",
)


class DesktopPackageVerificationError(RuntimeError):
    """Raised when the installed image does not match its pinned contract."""


# Where jpackage puts the runtime image's `release` file, per host. All three shapes
# differ, and the differences are jpackage's, not a choice available here:
#
#   linux    Kani/bin/Kani        Kani/lib/app/    Kani/lib/runtime/
#   windows  Kani/Kani.exe        Kani/app/        Kani/runtime/
#   macos    Kani.app/Contents/MacOS/Kani          Kani.app/Contents/runtime/Contents/Home/
#
# The Linux path is verified against a built image on the development host. The Windows
# and macOS paths follow jpackage's documented app-image layout and are exercised by
# their own CI runners, which is acceptable because a wrong path here fails the gate
# loudly with "no release file" rather than passing without checking anything.
RUNTIME_RELEASE_PATHS = {
    "linux": Path(APPLICATION_NAME, "lib", "runtime", "release"),
    "windows": Path(APPLICATION_NAME, "runtime", "release"),
    "macos": Path(
        f"{APPLICATION_NAME}.app",
        "Contents",
        "runtime",
        "Contents",
        "Home",
        "release",
    ),
}


def runtime_release_file(image_root: Path, platform: str) -> Path:
    """The packaged runtime's `release` file for this host's image layout."""
    return image_root.resolve() / RUNTIME_RELEASE_PATHS[normalized_platform(platform)]


# Where jpackage puts the bundled application jars, per host. Same layout split as
# the runtime paths above.
APPLICATION_JAR_PATHS = {
    "linux": Path(APPLICATION_NAME, "lib", "app"),
    "windows": Path(APPLICATION_NAME, "app"),
    "macos": Path(f"{APPLICATION_NAME}.app", "Contents", "app"),
}

# Multi-release module descriptors legitimately repeat across jars: they live under
# `META-INF/versions/`, and a flat classpath launch never loads them at all (the JVM
# reads them only when the jar is on the module path). Every *other* repeated class
# is a real shadowing hazard.
IGNORED_DUPLICATE_CLASS_PREFIX = "META-INF/versions/"


def application_jar_directory(image_root: Path, platform: str) -> Path:
    """The directory holding the bundled application jars for this host."""
    return image_root.resolve() / APPLICATION_JAR_PATHS[normalized_platform(platform)]


def find_shadowed_classes(jar_directory: Path) -> dict[str, list[str]]:
    """Finds classes present in more than one bundled jar, with their jars.

    jpackage puts every jar in one directory and the launcher builds a flat
    classpath from it, so when two jars carry the same class the JVM silently loads
    whichever the classpath reaches first. Nothing reports this: the build passes,
    the app starts, and the wrong implementation is live.

    Kani's image really does bundle four artifacts at two versions each --
    `runtime-desktop` 1.11.1 and 1.11.2, and three more like it -- because Compose
    1.11 moved these artifacts from `org.jetbrains.compose` to `androidx.compose`
    and publishes the old coordinates as empty redirect stubs. That is harmless,
    but only because the stubs contain no classes, and a file listing cannot tell
    an empty stub apart from a genuine duplicate. This measures the classes.
    """
    if not jar_directory.is_dir():
        raise DesktopPackageVerificationError(
            f"the image has no bundled application jars: {jar_directory}",
        )
    owners: dict[str, list[str]] = {}
    for jar in sorted(jar_directory.glob("*.jar")):
        with zipfile.ZipFile(jar) as archive:
            for entry in archive.namelist():
                if not entry.endswith(".class"):
                    continue
                if entry.startswith(IGNORED_DUPLICATE_CLASS_PREFIX):
                    continue
                owners.setdefault(entry, []).append(jar.name)
    return {
        entry: jars for entry, jars in owners.items() if len(jars) > 1
    }


def read_release_properties(release_file: Path) -> dict[str, str]:
    """Parses a JDK `release` file, stripping the quotes it uses inconsistently."""
    if not release_file.is_file():
        raise DesktopPackageVerificationError(
            f"the packaged runtime has no release file: {release_file}",
        )
    properties: dict[str, str] = {}
    for line in release_file.read_text(encoding="utf-8").splitlines():
        separator = line.find("=")
        if separator <= 0:
            continue
        key = line[:separator].strip()
        value = line[separator + 1:].strip().strip('"')
        properties[key] = value
    return properties


def verify_installed_package(
    image_root: Path,
    *,
    platform: str = sys.platform,
    expected_java_version: str = EXPECTED_JAVA_VERSION,
    expected_modules: Sequence[str] = EXPECTED_MODULES,
) -> dict[str, object]:
    """Verifies the image and returns what it found, for the release record.

    Collects every problem before failing. A wrong runtime version and a missing
    module are usually one mistake -- the image was built from the wrong JDK -- and
    reporting them one build at a time is a worse way to learn that.
    """
    host = normalized_platform(platform)
    problems: list[str] = []

    # The launcher check comes from the smoke runner, so "is this a real installed
    # image" is answered the same way in both gates.
    launcher = installed_image_launcher(image_root, platform)

    properties = read_release_properties(runtime_release_file(image_root, platform))

    java_version = properties.get("JAVA_VERSION")
    if java_version != expected_java_version:
        problems.append(
            f"the shipped runtime is JAVA_VERSION {java_version or 'absent'}, "
            f"expected {expected_java_version}; this JVM runs on the user's "
            "machine, so it is release evidence",
        )

    modules = tuple(sorted(properties.get("MODULES", "").split()))
    expected_sorted = tuple(sorted(expected_modules))
    if modules != expected_sorted:
        missing = sorted(set(expected_sorted) - set(modules))
        unexpected = sorted(set(modules) - set(expected_sorted))
        if missing:
            problems.append(
                "the packaged runtime is missing required modules: "
                f"{', '.join(missing)}; a missing module does not fail the build, "
                "it fails in the installed app on the user's machine",
            )
        if unexpected:
            problems.append(
                "the packaged runtime carries unpinned modules: "
                f"{', '.join(unexpected)}; update KaniDesktopRuntimeModules and "
                "this gate together, deliberately",
            )

    jar_directory = application_jar_directory(image_root, platform)
    shadowed = find_shadowed_classes(jar_directory)
    if shadowed:
        sample = sorted(shadowed)[:3]
        detail = "; ".join(
            f"{entry} in {', '.join(shadowed[entry])}" for entry in sample
        )
        problems.append(
            f"{len(shadowed)} class(es) are present in more than one bundled jar, "
            "so the flat classpath decides which one loads: "
            f"{detail}",
        )

    bundled_jars = sorted(jar.name for jar in jar_directory.glob("*.jar"))

    if problems:
        raise DesktopPackageVerificationError("; ".join(problems))

    return {
        "host": host,
        "launcher": str(launcher),
        "java_version": java_version,
        "modules": list(modules),
        "bundled_jar_count": len(bundled_jars),
        # Recorded as an explicit null rather than omitted, so a release record
        # shows that vendor was not checked here instead of leaving it ambiguous.
        "runtime_vendor": None,
        "runtime_vendor_note": (
            "the packaged jlink image records no IMPLEMENTOR; vendor provenance is "
            "verified at build time by KaniPackagingJdk"
        ),
    }


def format_report(verification: Mapping[str, object]) -> str:
    return "\n".join(
        (
            f"host={verification['host']}",
            f"java_version={verification['java_version']}",
            f"modules={' '.join(verification['modules'])}",  # type: ignore[arg-type]
            f"bundled_jars={verification['bundled_jar_count']} "
            "(no class shadowed across them)",
            f"runtime_vendor=unverifiable-from-image "
            f"({verification['runtime_vendor_note']})",
        ),
    )


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--image-root",
        required=True,
        type=Path,
        help="Compose installed-image root containing Kani or Kani.app",
    )
    parser.add_argument(
        "--json-out",
        type=Path,
        help="Write the verification result here for the release record",
    )
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    try:
        verification = verify_installed_package(args.image_root)
    except (
        DesktopPackageVerificationError,
        DesktopInstalledImageSmokeError,
    ) as error:
        print(f"desktop package verification failed: {error}", file=sys.stderr)
        return 1
    print(format_report(verification))
    if args.json_out is not None:
        args.json_out.write_text(
            json.dumps(verification, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
