#!/usr/bin/env python3
"""Validate three host metadata artifacts before deterministic aggregation."""

from __future__ import annotations

import argparse
import difflib
import hashlib
import json
import re
from pathlib import Path
from typing import Any, Optional, Sequence


HOST_CONTRACT = {
    "linux": ("ubuntu-24.04", "X64", "Linux"),
    "windows": ("windows-2025", "X64", "Windows"),
    "macos": ("macos-15", "ARM64", "macOS"),
}
SHA_40 = re.compile(r"^[0-9a-f]{40}$")
SHA_256 = re.compile(r"^[0-9a-f]{64}$")


def _sha256(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def _one(root: Path, name: str) -> Path:
    matches = sorted(root.rglob(name))
    if len(matches) != 1:
        raise ValueError(f"expected exactly one {name}, found {len(matches)}")
    return matches[0]


def _expected_diff(baseline: bytes, metadata: bytes, host_id: str) -> str:
    def canonical_lines(content: bytes) -> list[str]:
        text = content.decode("utf-8")
        return text.replace("\r\n", "\n").replace("\r", "\n").splitlines(
            keepends=True,
        )

    return "".join(
        difflib.unified_diff(
            canonical_lines(baseline),
            canonical_lines(metadata),
            fromfile="a/gradle/verification-metadata.xml",
            tofile=f"b/verification-metadata-{host_id}.xml",
            lineterm="\n",
        )
    )


def validate_artifacts(
    *,
    input_directory: Path,
    baseline: Path,
    expected_commit_sha: str,
    expected_repository: str,
    expected_ref: str,
    expected_event_name: str,
    expected_mode: str,
) -> dict[str, dict[str, Any]]:
    if not input_directory.is_dir():
        raise ValueError(f"artifact directory does not exist: {input_directory}")
    if not baseline.is_file():
        raise ValueError(f"baseline metadata does not exist: {baseline}")
    if not SHA_40.fullmatch(expected_commit_sha):
        raise ValueError("expected commit SHA must contain 40 lowercase hex characters")

    baseline_content = baseline.read_bytes()
    baseline_sha256 = _sha256(baseline_content)
    manifests: dict[str, dict[str, Any]] = {}

    expected_xml_names = {
        f"verification-metadata-{host_id}.xml" for host_id in HOST_CONTRACT
    }
    found_xml_names = {
        path.name for path in input_directory.rglob("verification-metadata-*.xml")
    }
    if found_xml_names != expected_xml_names:
        raise ValueError(
            "host XML set mismatch: "
            f"expected {sorted(expected_xml_names)}, found {sorted(found_xml_names)}"
        )

    for host_id, (runner_label, runner_arch, runner_os) in HOST_CONTRACT.items():
        metadata_name = f"verification-metadata-{host_id}.xml"
        metadata_path = _one(input_directory, metadata_name)
        diff_path = _one(
            input_directory,
            f"verification-metadata-{host_id}.diff",
        )
        manifest_path = _one(
            input_directory,
            f"verification-metadata-{host_id}.manifest.json",
        )
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
        if not isinstance(manifest, dict):
            raise ValueError(f"{manifest_path} must contain a JSON object")

        metadata_content = metadata_path.read_bytes()
        expected_fields = {
            "baseline_sha256": baseline_sha256,
            "changed": baseline_content != metadata_content,
            "commit_sha": expected_commit_sha,
            "event_name": expected_event_name,
            "host_id": host_id,
            "metadata_file": metadata_name,
            "metadata_sha256": _sha256(metadata_content),
            "mode": expected_mode,
            "ref": expected_ref,
            "repository": expected_repository,
            "runner_arch": runner_arch,
            "runner_label": runner_label,
            "runner_os": runner_os,
            "schema_version": 1,
        }
        for field, expected in expected_fields.items():
            actual = manifest.get(field)
            if actual != expected:
                raise ValueError(
                    f"{manifest_path}: {field} must be {expected!r}, got {actual!r}"
                )
        if not SHA_256.fullmatch(str(manifest["metadata_sha256"])):
            raise ValueError(f"{manifest_path}: invalid metadata SHA-256")

        expected_diff = _expected_diff(baseline_content, metadata_content, host_id)
        actual_diff = diff_path.read_bytes()
        if actual_diff != expected_diff.encode("utf-8"):
            raise ValueError(f"{diff_path}: diff does not match baseline and XML")
        manifests[host_id] = manifest

    agreement_fields = (
        "baseline_sha256",
        "commit_sha",
        "event_name",
        "mode",
        "ref",
        "repository",
    )
    for field in agreement_fields:
        values = {manifest[field] for manifest in manifests.values()}
        if len(values) != 1:
            raise ValueError(f"host manifests disagree on {field}: {sorted(values)}")
    return manifests


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input-directory", type=Path, required=True)
    parser.add_argument("--baseline", type=Path, required=True)
    parser.add_argument("--expected-commit-sha", required=True)
    parser.add_argument("--expected-repository", required=True)
    parser.add_argument("--expected-ref", required=True)
    parser.add_argument("--expected-event-name", required=True)
    parser.add_argument(
        "--expected-mode",
        choices=("bootstrap-write", "strict"),
        required=True,
    )
    return parser.parse_args(argv)


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv if argv is not None else [])
    manifests = validate_artifacts(
        input_directory=args.input_directory,
        baseline=args.baseline,
        expected_commit_sha=args.expected_commit_sha,
        expected_repository=args.expected_repository,
        expected_ref=args.expected_ref,
        expected_event_name=args.expected_event_name,
        expected_mode=args.expected_mode,
    )
    print(
        "validated host verification metadata: "
        + ", ".join(sorted(manifests))
    )
    return 0


if __name__ == "__main__":
    import sys

    raise SystemExit(main(sys.argv[1:]))
