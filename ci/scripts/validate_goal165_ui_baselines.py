#!/usr/bin/env python3
"""Validate the exact checked-in Goal 165 Android UI golden set."""

from __future__ import annotations

import argparse
import hashlib
import pathlib
import struct
from collections import defaultdict
from collections.abc import Iterable


EXPECTED_IMAGE_ALIASES = {
    frozenset(("study-active-fs100", "study-data-fs100")),
}
EXPECTED_SEMANTICS_ALIASES = {
    frozenset(("study-active-fs100", "study-data-fs100")),
    frozenset(("recent-mistakes-data-fs100", "recent-mistakes-data-fs200")),
}
EXPECTED_VIEWPORT = (360, 640)


def parse_contract(path: pathlib.Path) -> tuple[list[str], list[str], int]:
    durable: list[str] = []
    states: list[str] = []
    declared_total: int | None = None
    section = ""
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if line.startswith("[") and line.endswith("]"):
            section = line
            continue
        if not line:
            continue
        if section == "[durable-routes]":
            durable.append(line.split(maxsplit=1)[0])
        elif section == "[representative-states]":
            states.append(line.split(maxsplit=1)[0])
        elif section == "[capture-counts]" and line.startswith("total="):
            declared_total = int(line.removeprefix("total="))
    if not durable or not states or declared_total is None:
        raise AssertionError(f"incomplete route-state contract: {path}")
    return durable, states, declared_total


def expected_capture_ids(contract: pathlib.Path) -> tuple[list[str], list[str]]:
    durable, states, declared_total = parse_contract(contract)
    capture_ids = [
        capture_id
        for route in durable
        for capture_id in (f"{route}-data-fs100", f"{route}-data-fs200")
    ]
    capture_ids.extend(f"{state}-fs100" for state in states)
    if len(capture_ids) != declared_total:
        raise AssertionError(
            f"contract declares {declared_total} captures but names {len(capture_ids)}"
        )
    if declared_total != 63:
        raise AssertionError(f"Goal 165 contract must contain exactly 63 captures: {declared_total}")
    return capture_ids, durable


def assert_exact_files(root: pathlib.Path, capture_ids: Iterable[str], kind: str, suffix: str) -> None:
    expected = {f"{capture_id}{suffix}" for capture_id in capture_ids}
    directory = root / kind
    actual = {
        path.relative_to(directory).as_posix()
        for path in directory.rglob("*")
        if path.is_file()
    }
    if actual != expected:
        missing = sorted(expected - actual)
        unexpected = sorted(actual - expected)
        raise AssertionError(
            f"{kind} asset set mismatch; missing={missing}, unexpected={unexpected}"
        )


def png_dimensions(path: pathlib.Path) -> tuple[int, int]:
    header = path.read_bytes()[:24]
    if len(header) != 24 or header[:8] != b"\x89PNG\r\n\x1a\n" or header[12:16] != b"IHDR":
        raise AssertionError(f"not a PNG with an IHDR header: {path}")
    return struct.unpack(">II", header[16:24])


def duplicate_groups(paths_by_id: dict[str, pathlib.Path]) -> set[frozenset[str]]:
    by_digest: defaultdict[bytes, list[str]] = defaultdict(list)
    for capture_id, path in paths_by_id.items():
        data = path.read_bytes()
        if not data:
            raise AssertionError(f"empty baseline asset: {path}")
        by_digest[hashlib.sha256(data).digest()].append(capture_id)
    return {
        frozenset(capture_ids)
        for capture_ids in by_digest.values()
        if len(capture_ids) > 1
    }


def validate(root: pathlib.Path, contract: pathlib.Path) -> None:
    capture_ids, durable = expected_capture_ids(contract)
    assert_exact_files(root, capture_ids, "images", ".png")
    assert_exact_files(root, capture_ids, "semantics", ".txt")

    images = {
        capture_id: root / "images" / f"{capture_id}.png"
        for capture_id in capture_ids
    }
    semantics = {
        capture_id: root / "semantics" / f"{capture_id}.txt"
        for capture_id in capture_ids
    }
    invalid_dimensions = {
        capture_id: png_dimensions(path)
        for capture_id, path in images.items()
        if png_dimensions(path) != EXPECTED_VIEWPORT
    }
    if invalid_dimensions:
        raise AssertionError(f"invalid PNG dimensions: {invalid_dimensions}")
    if duplicate_groups(images) != EXPECTED_IMAGE_ALIASES:
        raise AssertionError(
            f"unexpected image aliases: {sorted(map(sorted, duplicate_groups(images)))}"
        )
    if duplicate_groups(semantics) != EXPECTED_SEMANTICS_ALIASES:
        raise AssertionError(
            "unexpected semantics aliases: "
            f"{sorted(map(sorted, duplicate_groups(semantics)))}"
        )
    for route in durable:
        normal = images[f"{route}-data-fs100"].read_bytes()
        accessible = images[f"{route}-data-fs200"].read_bytes()
        if normal == accessible:
            raise AssertionError(f"{route} 1.0x and 2.0x image goldens are identical")


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=pathlib.Path, required=True)
    parser.add_argument("--contract", type=pathlib.Path, required=True)
    args = parser.parse_args()
    validate(args.root, args.contract)
    print("Goal 165 UI assets valid: 63 PNGs and 63 semantics snapshots.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
