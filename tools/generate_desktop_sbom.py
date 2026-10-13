#!/usr/bin/env python3
"""Generate a CycloneDX SBOM and third-party notices for the desktop image.

Goal 205 asks for an SBOM "from the exact resolved release dependency graph". This
generates it from the **installed image** rather than from `gradle dependencies`,
and the distinction is the point: the image is what users receive. A resolution
listing can include artifacts the packager dropped and can omit ones jpackage
added, so an SBOM built from it describes a build that was never shipped — which
is worse than no SBOM, because it will be believed.

Determinism is a hard requirement, not a nicety. The document is release evidence,
so two runs over the same image must produce byte-identical output: components are
sorted, the JSON is written with fixed separators and sorted keys, and no
timestamp, hostname, or random value appears anywhere. `serialNumber` is derived
from the content hash, so it identifies *this* component set rather than this run.

What this deliberately does not do: guess licenses. A jar's license is not
reliably recoverable from its filename, and a notices file that states the wrong
license is a legal claim, not a formatting error. Coordinates come from the
version catalog where they can be matched, and anything unmatched is reported as
`UNKNOWN` and listed in the notices as requiring review. An honest gap is
auditable; a plausible guess is not.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from collections.abc import Sequence
from pathlib import Path

# jpackage renames each jar to `<name>-<version>-<hash>.jar`. The trailing hash is
# jpackage's, not the artifact's, so it is stripped before matching.
JPACKAGE_JAR = re.compile(r"^(?P<stem>.+?)-(?P<version>\d[\w.+-]*?)-(?P<hash>[0-9a-f]{20,})\.jar$")
PLAIN_JAR = re.compile(r"^(?P<stem>.+?)-(?P<version>\d[\w.+-]*)\.jar$")
# jpackage's rename hash on a jar that carries no version of its own.
HASH_SUFFIX = re.compile(r"-[0-9a-f]{20,}$")

SPDX_BY_GROUP_PREFIX = (
    # Only prefixes whose licensing is unambiguous and stable. Anything else is
    # UNKNOWN by design — see the module docstring.
    ("androidx.", "Apache-2.0"),
    ("org.jetbrains.compose", "Apache-2.0"),
    ("org.jetbrains.kotlin", "Apache-2.0"),
    ("org.jetbrains.kotlinx", "Apache-2.0"),
    ("org.jetbrains.skiko", "Apache-2.0"),
    ("org.jetbrains:annotations", "Apache-2.0"),
    ("com.squareup.okio", "Apache-2.0"),
    ("com.squareup.okhttp3", "Apache-2.0"),
    # JetBrains' multiplatform forks of the androidx libraries, published under their
    # own groups. Same license as the upstream they fork.
    ("org.jetbrains.androidx", "Apache-2.0"),
    ("org.jetbrains.runtime", "Apache-2.0"),
    ("org.jspecify", "Apache-2.0"),
)

# The bundled JDK's own jars, which are not Maven artifacts at all: they arrive inside
# the jlink runtime image, so they have no group, no version, and no entry in the
# dependency-verification metadata. Attributing them as libraries would be wrong; they
# are covered by the runtime's license, recorded in `docs/desktop-packaging-jdk.md`.
RUNTIME_IMAGE_JARS = frozenset({"jrt-fs"})


# Kani's own modules, which ship inside the image but are not third-party
# components: they have no external license and no upstream to attribute. Read from
# settings.gradle.kts so a new module cannot silently be reported as a dependency.
FIRST_PARTY_FALLBACK = frozenset({"bee-fsrs"})


class SbomError(RuntimeError):
    """Raised when the image cannot be described."""


def first_party_modules(settings_file: Path) -> frozenset[str]:
    """Kani's own Gradle modules, named from settings.gradle.kts.

    Derived rather than hardcoded because the list changes: `:widget` was extracted
    during this same body of work, and a hardcoded set would have reported it as a
    third-party library on the next release.
    """
    if not settings_file.is_file():
        return FIRST_PARTY_FALLBACK
    text = settings_file.read_text(encoding="utf-8")
    modules = set(re.findall(r'include\("?:([\w-]+)"?\)', text))
    return frozenset(modules | FIRST_PARTY_FALLBACK) if modules else FIRST_PARTY_FALLBACK


def packaged_jars(image_root: Path) -> list[Path]:
    """Every jar inside the installed image, sorted by name."""
    candidates = sorted(image_root.rglob("*.jar"), key=lambda path: path.name)
    if not candidates:
        raise SbomError(f"no jars found under {image_root}; is this an installed image?")
    return candidates


def verified_coordinates(metadata_file: Path) -> dict[str, tuple[str, str, str]]:
    """Maps an artifact's sha256 to its (group, name, version).

    This is the authoritative source and the reason attribution here is not guesswork.
    Gradle's dependency-verification metadata records the coordinates *and* the digest
    of every artifact the build resolved, so hashing a shipped jar identifies it
    exactly — no filename parsing, no version inference, and no chance of attributing
    one library's license to another that happens to share a name prefix.

    Parsed with a regex rather than an XML library only because the shape is fixed and
    machine-generated; a malformed file yields an empty map and every component falls
    back to UNKNOWN, which is the honest outcome.
    """
    if not metadata_file.is_file():
        return {}
    text = metadata_file.read_text(encoding="utf-8")
    by_digest: dict[str, tuple[str, str, str]] = {}
    component = re.compile(
        r'<component group="([^"]+)" name="([^"]+)" version="([^"]+)"',
    )
    digest = re.compile(r'<sha256 value="([0-9a-f]{64})"')

    current: tuple[str, str, str] | None = None
    for line in text.splitlines():
        found = component.search(line)
        if found:
            current = (found.group(1), found.group(2), found.group(3))
            continue
        hashed = digest.search(line)
        if hashed and current is not None:
            # First writer wins: the same bytes cannot belong to two coordinates, and a
            # later duplicate would be a metadata bug rather than new information.
            by_digest.setdefault(hashed.group(1), current)
    return by_digest


def catalog_coordinates(catalog: Path) -> dict[tuple[str, str], str]:
    """Maps (artifact, version) to its group, read from the version catalog.

    The catalog is the only place in the repo that states a group for an artifact,
    and jpackage's filenames drop it. Parsed with a regex rather than a TOML
    library because this must run on a stock Python with no site packages, the
    same constraint every other tool in `tools/` works under.
    """
    if not catalog.is_file():
        return {}
    coordinates: dict[tuple[str, str], str] = {}
    versions: dict[str, str] = {}
    text = catalog.read_text(encoding="utf-8")

    section = ""
    for raw_line in text.splitlines():
        line = raw_line.strip()
        if line.startswith("[") and line.endswith("]"):
            section = line[1:-1]
            continue
        if not line or line.startswith("#") or "=" not in line:
            continue
        if section == "versions":
            key, _, value = line.partition("=")
            literal = value.strip().strip('"')
            if literal:
                versions[key.strip()] = literal
            continue
        if section != "libraries":
            continue
        module = re.search(r'module\s*=\s*"([^"]+)"', line)
        group_attr = re.search(r'group\s*=\s*"([^"]+)"', line)
        name_attr = re.search(r'name\s*=\s*"([^"]+)"', line)
        if module:
            group, _, artifact = module.group(1).partition(":")
        elif group_attr and name_attr:
            group, artifact = group_attr.group(1), name_attr.group(1)
        else:
            continue

        version = ""
        literal_version = re.search(r'version\s*=\s*"([^"]+)"', line)
        version_ref = re.search(r'version\.ref\s*=\s*"([^"]+)"', line)
        if literal_version:
            version = literal_version.group(1)
        elif version_ref:
            version = versions.get(version_ref.group(1), "")
        if group and artifact:
            coordinates[(artifact, version)] = group
            # Also key on artifact alone, so a transitive whose version differs
            # from the catalog's still resolves its group.
            coordinates.setdefault((artifact, ""), group)
    return coordinates


def spdx_for(group: str, artifact: str) -> str:
    qualified = f"{group}:{artifact}"
    for prefix, spdx in SPDX_BY_GROUP_PREFIX:
        if qualified.startswith(prefix) or group.startswith(prefix):
            return spdx
    return "UNKNOWN"


def describe(
    jar: Path,
    coordinates: dict[tuple[str, str], str],
    first_party: frozenset[str] = frozenset(),
    by_digest: dict[str, tuple[str, str, str]] | None = None,
) -> dict[str, object]:
    """One CycloneDX component for [jar], with its real sha256."""
    name = jar.name
    match = JPACKAGE_JAR.match(name) or PLAIN_JAR.match(name)
    if match:
        stem = match.group("stem")
        version = match.group("version")
    else:
        # An unversioned jar still carries jpackage's rename hash, so `data-api` arrives
        # as `data-api-f095bff0...`. Strip it, or every first-party module gets a
        # different component name on every build and the SBOM stops being comparable.
        stem = HASH_SUFFIX.sub("", jar.stem)
        version = "UNKNOWN"

    group = (
        coordinates.get((stem, version))
        or coordinates.get((stem, ""))
        # A KMP artifact is published as `<name>-desktop` / `-jvm`; try the base.
        or coordinates.get((re.sub(r"-(desktop|jvm)$", "", stem), ""))
        # Skiko publishes one artifact per target (`skiko-awt-runtime-linux-x64`); the
        # catalog names only `skiko`, so match on the base before the platform suffix.
        or coordinates.get((re.sub(r"-awt-runtime-.*$", "", stem), ""))
        or "UNKNOWN"
    )
    digest_early = hashlib.sha256(jar.read_bytes()).hexdigest()
    # Identify by the shipped bytes first. Only fall back to the filename when the
    # digest is unknown, which happens for Kani's own jars — they are built here, so
    # they are not in the verification metadata at all.
    verified = (by_digest or {}).get(digest_early)
    if verified:
        group, stem, version = verified

    base = re.sub(r"-(desktop|jvm)$", "", stem)
    is_first_party = stem in first_party or base in first_party
    is_runtime = stem in RUNTIME_IMAGE_JARS
    if is_runtime:
        # Part of the bundled Temurin runtime, not a dependency Kani declares. Named so
        # the SBOM is a complete inventory of the image, but attributed to the JDK whose
        # vendor and version `KaniPackagingJdk` pins.
        group = "net.adoptium.temurin"
    if is_first_party:
        # Kani's own code: named as an internal component so a reader can see what the
        # image contains, but never presented as a third-party dependency needing
        # attribution.
        group = "dev.bee.kanjianki"

    digest = digest_early
    license_id = (
        "LicenseRef-Kani-first-party"
        if is_first_party
        else "GPL-2.0-with-classpath-exception"
        if is_runtime
        else spdx_for(group, stem)
    )
    component: dict[str, object] = {
        "type": "library",
        "name": stem,
        "version": version,
        "group": group,
        # The hash of the shipped bytes, which is the whole value of building this
        # from the image: it lets someone verify a component rather than trust it.
        "hashes": [{"alg": "SHA-256", "content": digest}],
        "licenses": [{"license": {"id": license_id}}],
    }
    if is_first_party:
        component["properties"] = [{"name": "kani:firstParty", "value": "true"}]
    if is_runtime:
        component["properties"] = [{"name": "kani:bundledRuntime", "value": "true"}]
    if group != "UNKNOWN":
        component["purl"] = f"pkg:maven/{group}/{stem}@{version}"
    return component


def build_sbom(components: Sequence[dict[str, object]]) -> dict[str, object]:
    ordered = sorted(
        components,
        key=lambda component: (component["group"], component["name"], component["version"]),
    )
    # Serial number from the content, not the clock: rebuilding the same image must
    # produce the same document, and a run-scoped UUID would break that.
    fingerprint = hashlib.sha256(
        json.dumps(ordered, sort_keys=True, separators=(",", ":")).encode("utf-8"),
    ).hexdigest()
    return {
        "bomFormat": "CycloneDX",
        "specVersion": "1.5",
        "version": 1,
        "serialNumber": f"urn:uuid:{fingerprint[:8]}-{fingerprint[8:12]}-"
        f"{fingerprint[12:16]}-{fingerprint[16:20]}-{fingerprint[20:32]}",
        "metadata": {
            # No `timestamp`: it is the one CycloneDX field that would make two
            # builds of identical bytes produce different documents.
            "component": {
                "type": "application",
                "name": "Kani",
                "group": "dev.bee.kanjianki",
            },
        },
        "components": ordered,
    }


def build_notices(components: Sequence[dict[str, object]]) -> str:
    """A deterministic third-party notices file."""
    lines = [
        "Kani desktop — third-party notices",
        "",
        "Generated by tools/generate_desktop_sbom.py from the installed image, so this",
        "lists what the distribution actually contains rather than what a dependency",
        "resolution reported.",
        "",
    ]
    def license_of(component: dict[str, object]) -> str:
        return component["licenses"][0]["license"]["id"]

    # First-party modules are excluded: Kani does not attribute itself, and listing 20
    # of its own modules would bury the entries a reviewer actually has to check.
    third_party = [c for c in components if license_of(c) != "LicenseRef-Kani-first-party"]
    known = [c for c in third_party if license_of(c) != "UNKNOWN"]
    unknown = [c for c in third_party if license_of(c) == "UNKNOWN"]

    for component in sorted(known, key=lambda c: (c["group"], c["name"])):
        lines.append(f"{component['group']}:{component['name']}:{component['version']}")
        lines.append(f"    License: {component['licenses'][0]['license']['id']}")
    if unknown:
        lines.extend(
            [
                "",
                "REQUIRES REVIEW — license not determined",
                "",
                "These are listed rather than guessed. A notices file that states the wrong",
                "license is a legal claim, not a formatting error, and a jar's license is not",
                "reliably recoverable from its filename.",
                "",
            ],
        )
        for component in sorted(unknown, key=lambda c: c["name"]):
            lines.append(f"{component['name']}:{component['version']}")
    lines.append("")
    return "\n".join(lines)


def write_outputs(image_root: Path, sbom_path: Path, notices_path: Path, catalog: Path) -> int:
    repo_root = catalog.parent.parent
    coordinates = catalog_coordinates(catalog)
    first_party = first_party_modules(repo_root / "settings.gradle.kts")
    by_digest = verified_coordinates(repo_root / "gradle/verification-metadata.xml")
    components = [
        describe(jar, coordinates, first_party, by_digest)
        for jar in packaged_jars(image_root)
    ]
    sbom = build_sbom(components)

    sbom_path.parent.mkdir(parents=True, exist_ok=True)
    # Trailing newline and fixed separators so the file is stable under diff and
    # a git checkout does not report it modified.
    sbom_path.write_text(
        json.dumps(sbom, indent=2, sort_keys=True, ensure_ascii=False) + "\n",
        encoding="utf-8",
    )
    notices_path.write_text(build_notices(sbom["components"]), encoding="utf-8")
    return len(components)


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--image-root", required=True, type=Path)
    parser.add_argument("--sbom-out", required=True, type=Path)
    parser.add_argument("--notices-out", required=True, type=Path)
    parser.add_argument(
        "--version-catalog",
        default=Path("gradle/libs.versions.toml"),
        type=Path,
    )
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    arguments = parse_args(argv)
    try:
        count = write_outputs(
            arguments.image_root,
            arguments.sbom_out,
            arguments.notices_out,
            arguments.version_catalog,
        )
    except (SbomError, OSError) as error:
        print(f"desktop SBOM generation failed: {error}", file=sys.stderr)
        return 1
    print(f"KANI_DESKTOP_SBOM components={count}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
