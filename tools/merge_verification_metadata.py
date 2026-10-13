#!/usr/bin/env python3
"""Safely merge additive Gradle verification metadata from supported hosts.

The temporary desktop bootstrap resolves dependencies independently on Linux,
Windows, and macOS. Each job starts with the same pristine verification
metadata and uploads the complete file that Gradle generated. This tool proves
that every uploaded file preserves that baseline, rejects unsafe or conflicting
changes, and emits one deterministic union for human review.
"""

from __future__ import annotations

import argparse
import difflib
import hashlib
import html
import json
import re
import sys
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Mapping, Sequence


VERIFICATION_NAMESPACE = "https://schema.gradle.org/dependency-verification"
XSI_NAMESPACE = "http://www.w3.org/2001/XMLSchema-instance"
SCHEMA_LOCATION_ATTRIBUTE = f"{{{XSI_NAMESPACE}}}schemaLocation"
NAMESPACED = f"{{{VERIFICATION_NAMESPACE}}}"
EXPECTED_PLATFORM_FILENAMES = {
    "linux": "verification-metadata-linux.xml",
    "windows": "verification-metadata-windows.xml",
    "macos": "verification-metadata-macos.xml",
}
FORBIDDEN_CONFIGURATION_ELEMENTS = {"trusted-artifacts", "ignored-keys"}
SHA256_PATTERN = re.compile(r"[0-9a-f]{64}")


class VerificationMetadataError(ValueError):
    """Raised when verification metadata cannot be merged safely."""


@dataclass(frozen=True, order=True)
class Checksum:
    algorithm: str
    value: str
    origin: str | None = None
    reason: str | None = None
    also_trust: tuple[str, ...] = ()

    @property
    def attributes(self) -> tuple[tuple[str, str], ...]:
        attributes = [("value", self.value)]
        if self.origin is not None:
            attributes.append(("origin", self.origin))
        if self.reason is not None:
            attributes.append(("reason", self.reason))
        return tuple(attributes)


@dataclass(frozen=True)
class Artifact:
    name: str
    checksums: tuple[Checksum, ...]


@dataclass(frozen=True)
class Component:
    group: str
    name: str
    version: str
    artifacts: tuple[Artifact, ...]

    @property
    def key(self) -> tuple[str, str, str]:
        return self.group, self.name, self.version

    @property
    def coordinate(self) -> str:
        return ":".join(self.key)


@dataclass(frozen=True)
class VerificationDocument:
    schema_location: str
    verify_metadata: str
    verify_signatures: str
    components: tuple[Component, ...]


@dataclass(frozen=True)
class Additions:
    components: tuple[tuple[str, str, str], ...]
    artifacts: tuple[tuple[tuple[str, str, str], str], ...]
    checksums: tuple[tuple[tuple[str, str, str], str, Checksum], ...]

    def counts(self) -> dict[str, int]:
        return {
            "components": len(self.components),
            "artifacts": len(self.artifacts),
            "sha256_checksums": sum(
                1 + len(checksum.also_trust)
                for _, _, checksum in self.checksums
            ),
        }


@dataclass(frozen=True)
class MergeResult:
    baseline: VerificationDocument
    merged: VerificationDocument
    platform_documents: Mapping[str, VerificationDocument]
    platform_additions: Mapping[str, Additions]
    merged_additions: Additions


def sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def sha256_text(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def _local_name(tag: str) -> str:
    if not tag.startswith(NAMESPACED):
        raise VerificationMetadataError(
            f"element {tag!r} is outside the Gradle verification namespace"
        )
    return tag[len(NAMESPACED) :]


def _require_whitespace_only(element: ET.Element, *, source: Path) -> None:
    if element.text and element.text.strip():
        raise VerificationMetadataError(
            f"{source}: unexpected text inside <{_local_name(element.tag)}>"
        )
    if element.tail and element.tail.strip():
        raise VerificationMetadataError(
            f"{source}: unexpected trailing text after <{_local_name(element.tag)}>"
        )


def _require_attributes(
    element: ET.Element,
    expected: set[str],
    *,
    source: Path,
) -> None:
    actual = set(element.attrib)
    if actual != expected:
        raise VerificationMetadataError(
            f"{source}: <{_local_name(element.tag)}> attributes must be "
            f"{sorted(expected)}, found {sorted(actual)}"
        )
    for name, value in element.attrib.items():
        if not value:
            raise VerificationMetadataError(
                f"{source}: <{_local_name(element.tag)}> has an empty {name!r} attribute"
            )


def _parse_configuration(
    element: ET.Element,
    *,
    source: Path,
) -> tuple[str, str]:
    _require_attributes(element, set(), source=source)
    _require_whitespace_only(element, source=source)
    children = list(element)
    names = [_local_name(child.tag) for child in children]
    forbidden = FORBIDDEN_CONFIGURATION_ELEMENTS.intersection(names)
    if forbidden:
        raise VerificationMetadataError(
            f"{source}: unsafe verification configuration contains "
            f"{', '.join(sorted(forbidden))}"
        )
    expected_names = ["verify-metadata", "verify-signatures"]
    if names != expected_names:
        raise VerificationMetadataError(
            f"{source}: verification configuration must contain exactly "
            f"{expected_names}, found {names}"
        )

    values: dict[str, str] = {}
    for child in children:
        _require_attributes(child, set(), source=source)
        if list(child):
            raise VerificationMetadataError(
                f"{source}: <{_local_name(child.tag)}> must not contain child elements"
            )
        if child.tail and child.tail.strip():
            raise VerificationMetadataError(
                f"{source}: unexpected text after <{_local_name(child.tag)}>"
            )
        value = (child.text or "").strip()
        if value not in {"true", "false"}:
            raise VerificationMetadataError(
                f"{source}: <{_local_name(child.tag)}> must be true or false"
            )
        values[_local_name(child.tag)] = value

    if values["verify-metadata"] != "true":
        raise VerificationMetadataError(
            f"{source}: verify-metadata must remain true"
        )
    return values["verify-metadata"], values["verify-signatures"]


def _parse_checksum(element: ET.Element, *, source: Path) -> Checksum:
    algorithm = _local_name(element.tag)
    allowed_attributes = {"value", "origin", "reason"}
    actual_attributes = set(element.attrib)
    if "value" not in actual_attributes or not actual_attributes.issubset(
        allowed_attributes
    ):
        raise VerificationMetadataError(
            f"{source}: <{algorithm}> must have value and may only have "
            "origin/reason attributes"
        )
    if any(not value for value in element.attrib.values()):
        raise VerificationMetadataError(
            f"{source}: <{algorithm}> checksum attributes must not be empty"
        )
    _require_whitespace_only(element, source=source)
    value = element.attrib["value"]
    if algorithm == "sha256" and not SHA256_PATTERN.fullmatch(value):
        raise VerificationMetadataError(
            f"{source}: invalid lowercase SHA-256 checksum {value!r}"
        )
    also_trust: list[str] = []
    for child in element:
        if _local_name(child.tag) != "also-trust":
            raise VerificationMetadataError(
                f"{source}: checksum <{algorithm}> has unsupported child "
                f"<{_local_name(child.tag)}>"
            )
        _require_attributes(child, {"value"}, source=source)
        if list(child):
            raise VerificationMetadataError(
                f"{source}: <also-trust> must not contain child elements"
            )
        _require_whitespace_only(child, source=source)
        trusted_value = child.attrib["value"]
        if algorithm == "sha256" and not SHA256_PATTERN.fullmatch(trusted_value):
            raise VerificationMetadataError(
                f"{source}: invalid lowercase also-trust SHA-256 "
                f"{trusted_value!r}"
            )
        also_trust.append(trusted_value)
    if len(also_trust) != len(set(also_trust)):
        raise VerificationMetadataError(
            f"{source}: checksum <{algorithm}> has duplicate also-trust values"
        )
    return Checksum(
        algorithm=algorithm,
        value=value,
        origin=element.attrib.get("origin"),
        reason=element.attrib.get("reason"),
        also_trust=tuple(sorted(also_trust)),
    )


def _checksum_sort_key(
    checksum: Checksum,
) -> tuple[str, str, str, str, tuple[str, ...]]:
    return (
        checksum.algorithm,
        checksum.value,
        checksum.origin or "",
        checksum.reason or "",
        checksum.also_trust,
    )


def _parse_artifact(element: ET.Element, *, source: Path) -> Artifact:
    _require_attributes(element, {"name"}, source=source)
    _require_whitespace_only(element, source=source)
    checksums = tuple(
        sorted(
            (_parse_checksum(child, source=source) for child in element),
            key=_checksum_sort_key,
        )
    )
    if not checksums:
        raise VerificationMetadataError(
            f"{source}: artifact {element.attrib['name']!r} has no checksums"
        )
    checksum_keys = [(checksum.algorithm, checksum.value) for checksum in checksums]
    if len(checksum_keys) != len(set(checksum_keys)):
        raise VerificationMetadataError(
            f"{source}: artifact {element.attrib['name']!r} has duplicate checksums"
        )
    return Artifact(name=element.attrib["name"], checksums=checksums)


def _parse_component(element: ET.Element, *, source: Path) -> Component:
    _require_attributes(element, {"group", "name", "version"}, source=source)
    _require_whitespace_only(element, source=source)
    artifacts = tuple(
        sorted(
            (_parse_artifact(child, source=source) for child in element),
            key=lambda artifact: artifact.name,
        )
    )
    artifact_names = [artifact.name for artifact in artifacts]
    if len(artifact_names) != len(set(artifact_names)):
        coordinate = ":".join(
            (
                element.attrib["group"],
                element.attrib["name"],
                element.attrib["version"],
            )
        )
        raise VerificationMetadataError(
            f"{source}: component {coordinate} has duplicate artifact names"
        )
    return Component(
        group=element.attrib["group"],
        name=element.attrib["name"],
        version=element.attrib["version"],
        artifacts=artifacts,
    )


def parse_verification_metadata(path: Path) -> VerificationDocument:
    try:
        raw = path.read_bytes()
    except OSError as error:
        raise VerificationMetadataError(f"cannot read {path}: {error}") from error
    return _parse_verification_metadata_bytes(raw, source=path)


def _parse_verification_metadata_bytes(
    raw: bytes,
    *,
    source: Path,
) -> VerificationDocument:
    try:
        decoded = raw.decode("utf-8")
    except UnicodeDecodeError as error:
        raise VerificationMetadataError(
            f"{source}: verification metadata must be UTF-8"
        ) from error
    if "<!" in decoded:
        raise VerificationMetadataError(
            f"{source}: DTD, entity, comment, and CDATA declarations are forbidden"
        )
    try:
        root = ET.fromstring(raw)
    except ET.ParseError as error:
        raise VerificationMetadataError(
            f"{source}: malformed XML: {error}"
        ) from error

    if root.tag != f"{NAMESPACED}verification-metadata":
        raise VerificationMetadataError(
            f"{source}: root must use the Gradle dependency-verification namespace"
        )
    _require_attributes(root, {SCHEMA_LOCATION_ATTRIBUTE}, source=source)
    _require_whitespace_only(root, source=source)
    schema_location = root.attrib[SCHEMA_LOCATION_ATTRIBUTE]

    children = list(root)
    child_names = [_local_name(child.tag) for child in children]
    if child_names != ["configuration", "components"]:
        raise VerificationMetadataError(
            f"{source}: root children must be configuration then components, "
            f"found {child_names}"
        )

    verify_metadata, verify_signatures = _parse_configuration(
        children[0],
        source=source,
    )
    components_element = children[1]
    _require_attributes(components_element, set(), source=source)
    _require_whitespace_only(components_element, source=source)
    components = tuple(
        sorted(
            (
                _parse_component(component, source=source)
                for component in components_element
            ),
            key=lambda component: component.key,
        )
    )
    component_keys = [component.key for component in components]
    if len(component_keys) != len(set(component_keys)):
        raise VerificationMetadataError(
            f"{source}: duplicate component coordinates"
        )

    return VerificationDocument(
        schema_location=schema_location,
        verify_metadata=verify_metadata,
        verify_signatures=verify_signatures,
        components=components,
    )


def discover_platform_inputs(
    input_directory: Path,
    *,
    excluded_paths: Iterable[Path] = (),
) -> dict[str, Path]:
    if not input_directory.is_dir():
        raise VerificationMetadataError(
            f"input directory does not exist: {input_directory}"
        )
    excluded = {path.resolve() for path in excluded_paths}
    candidates = [
        path
        for path in input_directory.rglob("verification-metadata-*.xml")
        if path.is_file() and path.resolve() not in excluded
    ]
    expected_names = set(EXPECTED_PLATFORM_FILENAMES.values())
    unexpected = sorted(
        path.relative_to(input_directory).as_posix()
        for path in candidates
        if path.name not in expected_names
    )
    if unexpected:
        raise VerificationMetadataError(
            "unexpected platform verification metadata: " + ", ".join(unexpected)
        )

    discovered: dict[str, Path] = {}
    for platform, filename in EXPECTED_PLATFORM_FILENAMES.items():
        matches = sorted(path for path in candidates if path.name == filename)
        if not matches:
            raise VerificationMetadataError(
                f"missing required platform input {filename}"
            )
        if len(matches) != 1:
            rendered = ", ".join(
                path.relative_to(input_directory).as_posix() for path in matches
            )
            raise VerificationMetadataError(
                f"expected exactly one {filename}, found: {rendered}"
            )
        discovered[platform] = matches[0]
    return discovered


def _components_by_key(
    document: VerificationDocument,
) -> dict[tuple[str, str, str], Component]:
    return {component.key: component for component in document.components}


def _artifacts_by_name(component: Component) -> dict[str, Artifact]:
    return {artifact.name: artifact for artifact in component.artifacts}


def _checksums_by_key(artifact: Artifact) -> dict[tuple[str, str], Checksum]:
    return {
        (checksum.algorithm, checksum.value): checksum
        for checksum in artifact.checksums
    }


def _assert_same_schema_and_configuration(
    baseline: VerificationDocument,
    candidate: VerificationDocument,
    *,
    platform: str,
) -> None:
    if candidate.schema_location != baseline.schema_location:
        raise VerificationMetadataError(
            f"{platform}: schema location differs from the pristine baseline"
        )
    baseline_configuration = (
        baseline.verify_metadata,
        baseline.verify_signatures,
    )
    candidate_configuration = (
        candidate.verify_metadata,
        candidate.verify_signatures,
    )
    if candidate_configuration != baseline_configuration:
        raise VerificationMetadataError(
            f"{platform}: verification configuration differs from the pristine baseline"
        )


def _assert_preserves_baseline(
    baseline: VerificationDocument,
    candidate: VerificationDocument,
    *,
    platform: str,
) -> None:
    _assert_same_schema_and_configuration(
        baseline,
        candidate,
        platform=platform,
    )
    candidate_components = _components_by_key(candidate)
    for baseline_component in baseline.components:
        coordinate = baseline_component.coordinate
        candidate_component = candidate_components.get(baseline_component.key)
        if candidate_component is None:
            raise VerificationMetadataError(
                f"{platform}: baseline component deleted: {coordinate}"
            )
        candidate_artifacts = _artifacts_by_name(candidate_component)
        for baseline_artifact in baseline_component.artifacts:
            candidate_artifact = candidate_artifacts.get(baseline_artifact.name)
            description = f"{coordinate}/{baseline_artifact.name}"
            if candidate_artifact is None:
                raise VerificationMetadataError(
                    f"{platform}: baseline artifact deleted: {description}"
                )
            candidate_checksums = _checksums_by_key(candidate_artifact)
            for baseline_checksum in baseline_artifact.checksums:
                checksum_key = (
                    baseline_checksum.algorithm,
                    baseline_checksum.value,
                )
                candidate_checksum = candidate_checksums.get(checksum_key)
                if candidate_checksum is None:
                    raise VerificationMetadataError(
                        f"{platform}: baseline checksum deleted or changed: "
                        f"{description}/{baseline_checksum.algorithm}="
                        f"{baseline_checksum.value}"
                    )
                if candidate_checksum != baseline_checksum:
                    raise VerificationMetadataError(
                        f"{platform}: baseline checksum attributes changed: "
                        f"{description}/{baseline_checksum.algorithm}="
                        f"{baseline_checksum.value}"
                    )


def document_additions(
    baseline: VerificationDocument,
    candidate: VerificationDocument,
    *,
    platform: str,
) -> Additions:
    _assert_preserves_baseline(baseline, candidate, platform=platform)
    baseline_components = _components_by_key(baseline)
    component_additions: list[tuple[str, str, str]] = []
    artifact_additions: list[tuple[tuple[str, str, str], str]] = []
    checksum_additions: list[
        tuple[tuple[str, str, str], str, Checksum]
    ] = []

    for component in candidate.components:
        baseline_component = baseline_components.get(component.key)
        if baseline_component is None:
            component_additions.append(component.key)
            baseline_artifacts: dict[str, Artifact] = {}
        else:
            baseline_artifacts = _artifacts_by_name(baseline_component)

        for artifact in component.artifacts:
            baseline_artifact = baseline_artifacts.get(artifact.name)
            if baseline_artifact is None:
                artifact_additions.append((component.key, artifact.name))
                baseline_checksums: dict[tuple[str, str], Checksum] = {}
            else:
                baseline_checksums = _checksums_by_key(baseline_artifact)

            for checksum in artifact.checksums:
                checksum_key = (checksum.algorithm, checksum.value)
                if checksum_key in baseline_checksums:
                    continue
                if checksum.algorithm != "sha256":
                    raise VerificationMetadataError(
                        f"{platform}: non-sha256 checksum addition is forbidden: "
                        f"{component.coordinate}/{artifact.name}/"
                        f"{checksum.algorithm}"
                    )
                checksum_additions.append((component.key, artifact.name, checksum))

    return Additions(
        components=tuple(sorted(component_additions)),
        artifacts=tuple(sorted(artifact_additions)),
        checksums=tuple(
            sorted(
                checksum_additions,
                key=lambda addition: (
                    addition[0],
                    addition[1],
                    _checksum_sort_key(addition[2]),
                ),
            )
        ),
    )


def _assert_no_cross_host_checksum_conflicts(
    platform_additions: Mapping[str, Additions],
) -> None:
    additions_by_artifact: dict[
        tuple[tuple[str, str, str], str],
        dict[str, tuple[Checksum, ...]],
    ] = {}
    for platform, additions in platform_additions.items():
        grouped: dict[
            tuple[tuple[str, str, str], str],
            list[Checksum],
        ] = {}
        for component_key, artifact_name, checksum in additions.checksums:
            grouped.setdefault((component_key, artifact_name), []).append(checksum)
        for artifact_key, checksums in grouped.items():
            additions_by_artifact.setdefault(artifact_key, {})[platform] = tuple(
                sorted(checksums, key=_checksum_sort_key)
            )

    for (component_key, artifact_name), platform_values in sorted(
        additions_by_artifact.items()
    ):
        distinct = {checksums for checksums in platform_values.values()}
        if len(distinct) <= 1:
            continue
        coordinate = ":".join(component_key)
        details = "; ".join(
            f"{platform}="
            + ",".join(checksum.value for checksum in checksums)
            for platform, checksums in sorted(platform_values.items())
        )
        raise VerificationMetadataError(
            "conflicting SHA-256 additions for "
            f"{coordinate}/{artifact_name}: {details}"
        )


def _union_documents(
    baseline: VerificationDocument,
    platform_documents: Mapping[str, VerificationDocument],
) -> VerificationDocument:
    components: dict[
        tuple[str, str, str],
        dict[str, dict[tuple[str, str], Checksum]],
    ] = {}

    def add_document(document: VerificationDocument) -> None:
        for component in document.components:
            artifacts = components.setdefault(component.key, {})
            for artifact in component.artifacts:
                checksums = artifacts.setdefault(artifact.name, {})
                for checksum in artifact.checksums:
                    key = checksum.algorithm, checksum.value
                    previous = checksums.get(key)
                    if previous is not None and previous != checksum:
                        raise VerificationMetadataError(
                            "conflicting checksum attributes for "
                            f"{component.coordinate}/{artifact.name}/"
                            f"{checksum.algorithm}={checksum.value}"
                        )
                    checksums[key] = checksum

    add_document(baseline)
    for platform in EXPECTED_PLATFORM_FILENAMES:
        add_document(platform_documents[platform])

    merged_components: list[Component] = []
    for component_key, artifacts in sorted(components.items()):
        merged_artifacts = tuple(
            Artifact(
                name=artifact_name,
                checksums=tuple(
                    sorted(checksums.values(), key=_checksum_sort_key)
                ),
            )
            for artifact_name, checksums in sorted(artifacts.items())
        )
        merged_components.append(
            Component(
                group=component_key[0],
                name=component_key[1],
                version=component_key[2],
                artifacts=merged_artifacts,
            )
        )

    return VerificationDocument(
        schema_location=baseline.schema_location,
        verify_metadata=baseline.verify_metadata,
        verify_signatures=baseline.verify_signatures,
        components=tuple(merged_components),
    )


def merge_documents(
    baseline: VerificationDocument,
    platform_documents: Mapping[str, VerificationDocument],
) -> MergeResult:
    expected_platforms = set(EXPECTED_PLATFORM_FILENAMES)
    actual_platforms = set(platform_documents)
    if actual_platforms != expected_platforms:
        missing = sorted(expected_platforms - actual_platforms)
        unexpected = sorted(actual_platforms - expected_platforms)
        raise VerificationMetadataError(
            f"platform set mismatch; missing={missing}, unexpected={unexpected}"
        )

    platform_additions = {
        platform: document_additions(
            baseline,
            platform_documents[platform],
            platform=platform,
        )
        for platform in EXPECTED_PLATFORM_FILENAMES
    }
    _assert_no_cross_host_checksum_conflicts(platform_additions)
    merged = _union_documents(baseline, platform_documents)
    merged_additions = document_additions(
        baseline,
        merged,
        platform="merged output",
    )
    return MergeResult(
        baseline=baseline,
        merged=merged,
        platform_documents=dict(platform_documents),
        platform_additions=platform_additions,
        merged_additions=merged_additions,
    )


def _escape_attribute(value: str) -> str:
    return html.escape(value, quote=True)


def serialize_verification_metadata(document: VerificationDocument) -> str:
    lines = [
        '<?xml version="1.0" encoding="UTF-8"?>',
        (
            f'<verification-metadata xmlns="{VERIFICATION_NAMESPACE}" '
            f'xmlns:xsi="{XSI_NAMESPACE}" '
            f'xsi:schemaLocation="{_escape_attribute(document.schema_location)}">'
        ),
        "   <configuration>",
        f"      <verify-metadata>{document.verify_metadata}</verify-metadata>",
        f"      <verify-signatures>{document.verify_signatures}</verify-signatures>",
        "   </configuration>",
        "   <components>",
    ]
    for component in sorted(document.components, key=lambda item: item.key):
        lines.append(
            "      <component "
            f'group="{_escape_attribute(component.group)}" '
            f'name="{_escape_attribute(component.name)}" '
            f'version="{_escape_attribute(component.version)}">'
        )
        for artifact in sorted(component.artifacts, key=lambda item: item.name):
            lines.append(
                f'         <artifact name="{_escape_attribute(artifact.name)}">'
            )
            for checksum in sorted(
                artifact.checksums,
                key=_checksum_sort_key,
            ):
                attributes = " ".join(
                    f'{name}="{_escape_attribute(value)}"'
                    for name, value in checksum.attributes
                )
                if checksum.also_trust:
                    lines.append(
                        f"            <{checksum.algorithm} {attributes}>"
                    )
                    for trusted_value in checksum.also_trust:
                        lines.append(
                            "               <also-trust "
                            f'value="{_escape_attribute(trusted_value)}"/>'
                        )
                    lines.append(f"            </{checksum.algorithm}>")
                else:
                    lines.append(
                        f"            <{checksum.algorithm} {attributes}/>"
                    )
            lines.append("         </artifact>")
        lines.append("      </component>")
    lines.extend(
        [
            "   </components>",
            "</verification-metadata>",
            "",
        ]
    )
    return "\n".join(lines)


def _document_counts(document: VerificationDocument) -> dict[str, int]:
    artifacts = sum(len(component.artifacts) for component in document.components)
    checksums = sum(
        sum(1 + len(checksum.also_trust) for checksum in artifact.checksums)
        for component in document.components
        for artifact in component.artifacts
    )
    return {
        "components": len(document.components),
        "artifacts": artifacts,
        "checksums": checksums,
    }


def _coordinate(key: tuple[str, str, str]) -> str:
    return ":".join(key)


def _additions_payload(additions: Additions) -> dict[str, object]:
    return {
        "counts": additions.counts(),
        "components": [_coordinate(key) for key in additions.components],
        "artifacts": [
            {
                "component": _coordinate(component_key),
                "artifact": artifact_name,
            }
            for component_key, artifact_name in additions.artifacts
        ],
        "sha256_checksums": [
            {
                "component": _coordinate(component_key),
                "artifact": artifact_name,
                "sha256": checksum.value,
                **(
                    {"also_trust": list(checksum.also_trust)}
                    if checksum.also_trust
                    else {}
                ),
                **({"origin": checksum.origin} if checksum.origin else {}),
                **({"reason": checksum.reason} if checksum.reason else {}),
            }
            for component_key, artifact_name, checksum in additions.checksums
        ],
    }


def build_manifest(
    result: MergeResult,
    *,
    baseline_path: Path,
    platform_paths: Mapping[str, Path],
    merged_xml: str,
) -> dict[str, object]:
    return {
        "format_version": 1,
        "policy": {
            "merge": "additive-union-only",
            "new_checksum_algorithm": "sha256",
            "platforms": list(EXPECTED_PLATFORM_FILENAMES),
            "verify_metadata": result.baseline.verify_metadata,
            "verify_signatures": result.baseline.verify_signatures,
        },
        "schema_location": result.baseline.schema_location,
        "baseline": {
            "filename": baseline_path.name,
            "sha256": sha256_file(baseline_path),
            "counts": _document_counts(result.baseline),
        },
        "inputs": {
            platform: {
                "filename": EXPECTED_PLATFORM_FILENAMES[platform],
                "sha256": sha256_file(platform_paths[platform]),
                "counts": _document_counts(result.platform_documents[platform]),
                "additions": _additions_payload(
                    result.platform_additions[platform]
                ),
            }
            for platform in EXPECTED_PLATFORM_FILENAMES
        },
        "merged": {
            "sha256": sha256_text(merged_xml),
            "counts": _document_counts(result.merged),
            "additions": _additions_payload(result.merged_additions),
        },
    }


def build_review_summary(result: MergeResult, manifest: Mapping[str, object]) -> str:
    baseline = manifest["baseline"]
    merged = manifest["merged"]
    assert isinstance(baseline, dict)
    assert isinstance(merged, dict)
    lines = [
        "# Gradle verification metadata merge review",
        "",
        "The merge passed the additive-only safety policy. Review every listed",
        "component, artifact, and SHA-256 before committing the merged XML.",
        "",
        "## Provenance",
        "",
        f"- Baseline SHA-256: `{baseline['sha256']}`",
        f"- Merged SHA-256: `{merged['sha256']}`",
        f"- Schema: `{result.baseline.schema_location}`",
        (
            "- Verification flags: "
            f"`verify-metadata={result.baseline.verify_metadata}`, "
            f"`verify-signatures={result.baseline.verify_signatures}`"
        ),
        "",
        "## Platform contributions",
        "",
        "| Platform | Input SHA-256 | Components | Artifacts | SHA-256 checksums |",
        "| --- | --- | ---: | ---: | ---: |",
    ]
    inputs = manifest["inputs"]
    assert isinstance(inputs, dict)
    for platform in EXPECTED_PLATFORM_FILENAMES:
        platform_manifest = inputs[platform]
        assert isinstance(platform_manifest, dict)
        additions = platform_manifest["additions"]
        assert isinstance(additions, dict)
        counts = additions["counts"]
        assert isinstance(counts, dict)
        lines.append(
            f"| {platform} | `{platform_manifest['sha256']}` | "
            f"{counts['components']} | {counts['artifacts']} | "
            f"{counts['sha256_checksums']} |"
        )

    lines.extend(["", "## Additive review diff", ""])
    additions = result.merged_additions
    if not additions.components and not additions.artifacts and not additions.checksums:
        lines.append("- No additions.")
    else:
        for component_key in additions.components:
            lines.append(f"- `+ component {_coordinate(component_key)}`")
        for component_key, artifact_name in additions.artifacts:
            lines.append(
                f"- `+ artifact {_coordinate(component_key)}/{artifact_name}`"
            )
        for component_key, artifact_name, checksum in additions.checksums:
            qualifiers = []
            if checksum.origin:
                qualifiers.append(f"origin={checksum.origin}")
            if checksum.reason:
                qualifiers.append(f"reason={checksum.reason}")
            if checksum.also_trust:
                qualifiers.append(
                    "also-trust=" + ",".join(checksum.also_trust)
                )
            suffix = f" ({', '.join(qualifiers)})" if qualifiers else ""
            lines.append(
                f"- `+ sha256 {_coordinate(component_key)}/{artifact_name} "
                f"{checksum.value}`{suffix}"
            )

    lines.extend(
        [
            "",
            "## Required reviewer checks",
            "",
            "- Confirm every coordinate and artifact belongs to the desktop build.",
            "- Confirm native artifacts match the host and architecture that added them.",
            "- Inspect the unified XML diff; no baseline line may be removed.",
            "- Commit only after the permanent strict matrix passes with no XML diff.",
            "",
        ]
    )
    return "\n".join(lines)


def build_unified_diff(baseline_xml: str, merged_xml: str) -> str:
    return "".join(
        difflib.unified_diff(
            baseline_xml.splitlines(keepends=True),
            merged_xml.splitlines(keepends=True),
            fromfile="baseline/verification-metadata.xml",
            tofile="merged/verification-metadata.xml",
        )
    )


def _validate_output_paths(
    *,
    baseline_path: Path,
    platform_paths: Mapping[str, Path],
    output_path: Path,
    manifest_path: Path,
    review_summary_path: Path,
    diff_path: Path,
) -> None:
    input_paths = [baseline_path, *platform_paths.values()]
    output_paths = [
        output_path,
        manifest_path,
        review_summary_path,
        diff_path,
    ]
    resolved_inputs = {path.resolve() for path in input_paths}
    resolved_outputs = {path.resolve() for path in output_paths}
    if len(resolved_outputs) != len(output_paths):
        raise VerificationMetadataError("all merge output paths must be distinct")
    overlap = resolved_inputs.intersection(resolved_outputs)
    if overlap:
        raise VerificationMetadataError(
            "merge outputs must be separate from the pristine baseline and "
            "platform inputs: " + ", ".join(sorted(str(path) for path in overlap))
        )
    for output_index, output in enumerate(output_paths):
        if not output.exists():
            continue
        for input_path in input_paths:
            if input_path.exists() and output.samefile(input_path):
                raise VerificationMetadataError(
                    "merge outputs must not alias the pristine baseline or "
                    f"platform inputs: {output} and {input_path}"
                )
        for other_output in output_paths[:output_index]:
            if other_output.exists() and output.samefile(other_output):
                raise VerificationMetadataError(
                    f"merge output paths must not alias each other: "
                    f"{output} and {other_output}"
                )


def _write_utf8_lf(path: Path, content: str) -> None:
    path.write_bytes(content.encode("utf-8"))


def merge_files(
    *,
    baseline_path: Path,
    input_directory: Path,
    output_path: Path,
    manifest_path: Path,
    review_summary_path: Path,
    diff_path: Path,
    expected_baseline_sha256: str | None = None,
) -> MergeResult:
    if expected_baseline_sha256 is not None:
        if not SHA256_PATTERN.fullmatch(expected_baseline_sha256):
            raise VerificationMetadataError(
                "--expected-baseline-sha256 must be a lowercase SHA-256"
            )
        actual_baseline_sha256 = sha256_file(baseline_path)
        if actual_baseline_sha256 != expected_baseline_sha256:
            raise VerificationMetadataError(
                "pristine baseline SHA-256 mismatch: expected "
                f"{expected_baseline_sha256}, found {actual_baseline_sha256}"
            )

    platform_paths = discover_platform_inputs(
        input_directory,
        excluded_paths=(output_path,),
    )
    _validate_output_paths(
        baseline_path=baseline_path,
        platform_paths=platform_paths,
        output_path=output_path,
        manifest_path=manifest_path,
        review_summary_path=review_summary_path,
        diff_path=diff_path,
    )
    baseline = parse_verification_metadata(baseline_path)
    platform_documents = {
        platform: parse_verification_metadata(path)
        for platform, path in platform_paths.items()
    }
    result = merge_documents(baseline, platform_documents)
    merged_xml = serialize_verification_metadata(result.merged)
    baseline_source_xml = baseline_path.read_text(encoding="utf-8")
    baseline_canonical_xml = serialize_verification_metadata(baseline)
    if baseline_source_xml != baseline_canonical_xml:
        raise VerificationMetadataError(
            "pristine baseline is not in canonical Gradle serialization; "
            "refusing a merge that would rewrite existing metadata"
        )

    # The serializer is part of the security boundary. Parse its result and
    # prove that it represents exactly the in-memory union before publication.
    reparsed_merged = _parse_verification_metadata_bytes(
        merged_xml.encode("utf-8"),
        source=Path("<merged output>"),
    )
    if reparsed_merged != result.merged:
        raise VerificationMetadataError(
            "internal error: merged XML does not represent the exact union"
        )

    manifest = build_manifest(
        result,
        baseline_path=baseline_path,
        platform_paths=platform_paths,
        merged_xml=merged_xml,
    )
    diff = build_unified_diff(baseline_source_xml, merged_xml)
    review_summary = build_review_summary(result, manifest)

    for path in (output_path, manifest_path, review_summary_path, diff_path):
        path.parent.mkdir(parents=True, exist_ok=True)
    _write_utf8_lf(output_path, merged_xml)
    _write_utf8_lf(
        manifest_path,
        json.dumps(manifest, indent=2, sort_keys=True) + "\n",
    )
    _write_utf8_lf(review_summary_path, review_summary)
    _write_utf8_lf(diff_path, diff)
    merged_manifest = manifest["merged"]
    assert isinstance(merged_manifest, dict)
    if sha256_file(output_path) != merged_manifest["sha256"]:
        raise VerificationMetadataError(
            "internal error: merged output bytes do not match the manifest SHA-256"
        )
    return result


def parse_args(argv: Sequence[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--input-directory", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    parser.add_argument("--review-summary", type=Path, required=True)
    parser.add_argument(
        "--diff",
        type=Path,
        help="unified diff path (default: <output>.diff)",
    )
    parser.add_argument(
        "--expected-baseline-sha256",
        help="optional lowercase SHA-256 pin for the pristine baseline",
    )
    return parser.parse_args(argv)


def main(argv: Sequence[str] | None = None) -> int:
    args = parse_args(argv)
    diff_path = args.diff or Path(f"{args.output}.diff")
    try:
        result = merge_files(
            baseline_path=args.baseline,
            input_directory=args.input_directory,
            output_path=args.output,
            manifest_path=args.manifest,
            review_summary_path=args.review_summary,
            diff_path=diff_path,
            expected_baseline_sha256=args.expected_baseline_sha256,
        )
    except VerificationMetadataError as error:
        print(f"verification metadata merge rejected: {error}", file=sys.stderr)
        return 2

    counts = result.merged_additions.counts()
    print(
        "verification metadata merge accepted: "
        f"{counts['components']} components, "
        f"{counts['artifacts']} artifacts, "
        f"{counts['sha256_checksums']} SHA-256 checksums added"
    )
    print(f"merged XML: {args.output}")
    print(f"manifest: {args.manifest}")
    print(f"review summary: {args.review_summary}")
    print(f"unified diff: {diff_path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
