#!/usr/bin/env python3
"""Snapshot and package one host's full Gradle verification metadata."""

from __future__ import annotations

import argparse
import difflib
import hashlib
import json
import shutil
from pathlib import Path
from typing import Optional, Sequence


HOSTS = ("linux", "windows", "macos")


def _sha256(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def _write_text_lf(path: Path, content: str) -> None:
    with path.open("w", encoding="utf-8", newline="\n") as output:
        output.write(content)


def _canonical_diff(baseline: bytes, metadata: bytes, host_id: str) -> str:
    """Return a host-independent LF-only review diff."""

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


def snapshot(metadata: Path, output: Path) -> None:
    if not metadata.is_file():
        raise ValueError(f"verification metadata does not exist: {metadata}")
    output.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(metadata, output)


def capture(
    *,
    baseline: Path,
    metadata: Path,
    host_id: str,
    runner_label: str,
    runner_os: str,
    runner_arch: str,
    event_name: str,
    repository: str,
    ref: str,
    commit_sha: str,
    mode: str,
    output_directory: Path,
) -> tuple[Path, Path, Path]:
    if host_id not in HOSTS:
        raise ValueError(f"unsupported host id: {host_id}")
    if mode not in {"bootstrap-write", "strict"}:
        raise ValueError(f"unsupported verification mode: {mode}")
    if not baseline.is_file():
        raise ValueError(f"baseline metadata does not exist: {baseline}")
    if not metadata.is_file():
        raise ValueError(f"generated metadata does not exist: {metadata}")

    baseline_content = baseline.read_bytes()
    metadata_content = metadata.read_bytes()
    output_directory.mkdir(parents=True, exist_ok=True)

    metadata_name = f"verification-metadata-{host_id}.xml"
    diff_name = f"verification-metadata-{host_id}.diff"
    manifest_name = f"verification-metadata-{host_id}.manifest.json"
    metadata_output = output_directory / metadata_name
    diff_output = output_directory / diff_name
    manifest_output = output_directory / manifest_name

    metadata_output.write_bytes(metadata_content)
    diff_text = _canonical_diff(
        baseline_content,
        metadata_content,
        host_id,
    )
    _write_text_lf(diff_output, diff_text)

    manifest = {
        "baseline_sha256": _sha256(baseline_content),
        "changed": baseline_content != metadata_content,
        "commit_sha": commit_sha,
        "event_name": event_name,
        "host_id": host_id,
        "metadata_file": metadata_name,
        "metadata_sha256": _sha256(metadata_content),
        "mode": mode,
        "ref": ref,
        "repository": repository,
        "runner_arch": runner_arch,
        "runner_label": runner_label,
        "runner_os": runner_os,
        "schema_version": 1,
    }
    _write_text_lf(
        manifest_output,
        json.dumps(manifest, indent=2, sort_keys=True) + "\n",
    )
    return metadata_output, diff_output, manifest_output


def parse_args(argv: Sequence[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    subparsers = parser.add_subparsers(dest="command", required=True)

    snapshot_parser = subparsers.add_parser("snapshot")
    snapshot_parser.add_argument("--metadata", type=Path, required=True)
    snapshot_parser.add_argument("--output", type=Path, required=True)

    capture_parser = subparsers.add_parser("capture")
    capture_parser.add_argument("--baseline", type=Path, required=True)
    capture_parser.add_argument("--metadata", type=Path, required=True)
    capture_parser.add_argument("--host-id", choices=HOSTS, required=True)
    capture_parser.add_argument("--runner-label", required=True)
    capture_parser.add_argument("--runner-os", required=True)
    capture_parser.add_argument("--runner-arch", required=True)
    capture_parser.add_argument("--event-name", required=True)
    capture_parser.add_argument("--repository", required=True)
    capture_parser.add_argument("--ref", required=True)
    capture_parser.add_argument("--commit-sha", required=True)
    capture_parser.add_argument(
        "--mode",
        choices=("bootstrap-write", "strict"),
        required=True,
    )
    capture_parser.add_argument("--output-directory", type=Path, required=True)
    return parser.parse_args(argv)


def main(argv: Optional[Sequence[str]] = None) -> int:
    args = parse_args(argv if argv is not None else [])
    if args.command == "snapshot":
        snapshot(args.metadata, args.output)
    else:
        capture(
            baseline=args.baseline,
            metadata=args.metadata,
            host_id=args.host_id,
            runner_label=args.runner_label,
            runner_os=args.runner_os,
            runner_arch=args.runner_arch,
            event_name=args.event_name,
            repository=args.repository,
            ref=args.ref,
            commit_sha=args.commit_sha,
            mode=args.mode,
            output_directory=args.output_directory,
        )
    return 0


if __name__ == "__main__":
    import sys

    raise SystemExit(main(sys.argv[1:]))
