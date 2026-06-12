#!/usr/bin/env python3

from __future__ import annotations

import argparse
import json
import tempfile
from pathlib import Path
from typing import Iterable, Sequence, cast

from scripts.ralph_loop import button_contract
from scripts.ralph_loop import screenshot_fixtures
from scripts.ralph_loop import ui_manifest

SCHEMA = "ui-view-matrix-v1"
DEFAULT_JSON = Path(".ralph-loop/current/ui-view-matrix.json")
DEFAULT_MD = Path(".ralph-loop/current/ui-view-matrix.md")
DEFAULT_MANIFEST = Path(".ralph-loop/current/ui-manifest.json")
DEFAULT_BUTTON_CONTRACT = Path(".ralph-loop/current/button-contract.json")
SECONDARY_SOURCE_BUCKETS = {"shell", "theme", "shared"}


class UiViewMatrixError(ValueError):
    pass


def build_matrix(
    repo_root: Path,
    manifest_path: Path | None = None,
    button_contract_path: Path | None = None,
) -> dict[str, object]:
    root = repo_root.resolve()
    if not root.exists():
        raise UiViewMatrixError(f"repo root does not exist: {root}")

    manifest, source_manifest = _load_or_build_manifest(root, manifest_path)
    contract, source_contract = _load_or_build_contract(root, manifest, manifest_path, button_contract_path)
    files = [cast(dict[str, object], entry) for entry in cast(list[object], manifest.get("files", []))]
    rows = [cast(dict[str, object], row) for row in cast(list[object], contract.get("rows", []))]
    fixtures = _registry_fixtures(manifest)
    views = [_view_entry(fixture, files, rows) for fixture in fixtures]
    view_rows = [cast(dict[str, object], view) for view in views]
    all_source_files = sorted(
        {
            path
            for view in view_rows
            for key in ("primary_source_files", "secondary_source_files")
            for path in cast(list[str], cast(dict[str, object], view["source_files"])[key])
        }
    )
    view_button_row_ids = {
        str(row["id"])
        for view in view_rows
        for row in cast(list[dict[str, object]], view["button_rows"])
    }
    unmapped_button_rows = _unmapped_button_rows(rows, view_button_row_ids, set(all_source_files))
    fixture_registry = manifest.get("screenshot_fixture_registry")
    fixture_set_hash = screenshot_fixtures.fixture_registry()["fixture_set_hash"]
    if isinstance(fixture_registry, dict):
        fixture_set_hash = fixture_registry.get("fixture_set_hash", fixture_set_hash)
    return {
        "schema": SCHEMA,
        "source_manifest": source_manifest,
        "source_button_contract": source_contract,
        "fixture_set_hash": fixture_set_hash,
        "views": views,
        "unmapped_button_rows": unmapped_button_rows,
        "summary": {
            "view_count": len(views),
            "routes": [view["route"] for view in view_rows],
            "source_file_count": len(all_source_files),
            "button_contract_row_count": len(rows),
            "button_row_count": sum(cast(dict[str, int], view["coverage"])["button_row_count"] for view in view_rows),
            "unique_button_row_count": len(view_button_row_ids) + len(unmapped_button_rows),
            "unmapped_button_row_count": len(unmapped_button_rows),
            "views_with_buttons": sum(1 for view in view_rows if cast(dict[str, int], view["coverage"])["button_row_count"] > 0),
            "views_missing_button_coverage": sum(
                1 for view in view_rows if cast(dict[str, int], view["coverage"])["missing_button_rows"] > 0
            ),
        },
    }


def render_markdown(matrix: dict[str, object]) -> str:
    lines = [
        "# Ralph UI View Matrix",
        "",
        f"Schema: `{matrix['schema']}`",
        f"Source manifest: `{matrix['source_manifest']}`",
        f"Source button contract: `{matrix['source_button_contract']}`",
        "",
        "| View | Route | Fixture | Primary source files | Tests | Button rows | Missing button rows |",
        "| --- | --- | --- | --- | --- | --- | --- |",
    ]
    for view in cast(list[dict[str, object]], matrix["views"]):
        source_files = cast(dict[str, object], view["source_files"])
        coverage = cast(dict[str, int], view["coverage"])
        lines.append(
            "| {view} | `{route}` | `{fixture}` | {primary} | {tests} | `{rows}` | `{missing}` |".format(
                view=view["view_id"],
                route=view["route"],
                fixture=view["fixture_id"],
                primary=_markdown_list(cast(list[str], source_files["primary_source_files"])),
                tests=_markdown_list(cast(list[str], source_files["nearest_tests"])),
                rows=coverage["button_row_count"],
                missing=coverage["missing_button_rows"],
            )
        )
    unmapped_rows = cast(list[dict[str, object]], matrix.get("unmapped_button_rows", []))
    if unmapped_rows:
        lines.extend(
            [
                "",
                "## Unmapped button contract rows",
                "",
                "These rows are kept visible because no screenshot fixture source file can own them yet.",
                "",
                "| Row | Source file | Reason | Missing tests |",
                "| --- | --- | --- | --- |",
            ]
        )
        for row in unmapped_rows:
            lines.append(
                "| `{row_id}` | {source_file} | `{reason}` | {missing_tests} |".format(
                    row_id=row["id"],
                    source_file=f"`{row['source_file']}`" if row.get("source_file") else "—",
                    reason=row.get("unmapped_reason", "unmapped"),
                    missing_tests=_markdown_list(cast(list[str], row.get("missing_tests", []))),
                )
            )
    return "\n".join(lines) + "\n"


def write_outputs(matrix: dict[str, object], repo_root: Path, out_json: Path, out_md: Path) -> None:
    json_path = _resolve(repo_root, out_json)
    md_path = _resolve(repo_root, out_md)
    json_path.parent.mkdir(parents=True, exist_ok=True)
    md_path.parent.mkdir(parents=True, exist_ok=True)
    json_path.write_text(json.dumps(matrix, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    md_path.write_text(render_markdown(matrix), encoding="utf-8")


def _view_entry(
    fixture: dict[str, object],
    manifest_files: list[dict[str, object]],
    button_rows: list[dict[str, object]],
) -> dict[str, object]:
    source_entries = _source_entries_for_fixture(fixture, manifest_files)
    primary_buckets = set(cast(list[str], fixture["source_buckets"])) - SECONDARY_SOURCE_BUCKETS
    primary_entries = [entry for entry in source_entries if str(entry.get("bucket")) in primary_buckets]
    secondary_entries = [entry for entry in source_entries if str(entry.get("bucket")) not in primary_buckets]
    source_paths = {str(entry["path"]) for entry in source_entries}
    rows = [_button_row(row) for row in button_rows if str(row.get("source_file", "")) in source_paths]
    tests = _sorted_unique(
        test
        for entry in source_entries
        for test in cast(list[str], entry.get("nearest_tests", []))
    )
    risk_tags = _sorted_unique(
        tag
        for entry in source_entries
        for tag in cast(list[str], entry.get("risk_tags", []))
    )
    fixture_risk_tags = cast(list[str], fixture.get("risk_tags", []))
    labels = _sorted_unique(
        label
        for entry in source_entries
        for label in _entry_labels(entry)
    )
    rows_missing = sum(1 for row in rows if cast(list[str], row["missing_tests"]))
    rows_covered = sum(1 for row in rows if cast(list[str], row["existing_tests"]))
    return {
        "view_id": fixture["view_id"],
        "route": fixture["route"],
        "launch_route": fixture["launch_route"],
        "fixture_id": fixture["fixture_id"],
        "fixture_hash": fixture["fixture_hash"],
        "device_profile": fixture["device_profile"],
        "orientation": fixture["orientation"],
        "screenshot_names": fixture["screenshot_names"],
        "expected_terms": fixture["expected_terms"],
        "known_invariants": fixture["known_invariants"],
        "risk_tags": _sorted_unique([*fixture_risk_tags, *risk_tags]),
        "source_files": {
            "primary_source_files": _entry_paths(primary_entries),
            "secondary_source_files": _entry_paths(secondary_entries),
            "nearest_tests": tests,
        },
        "interactive_labels": labels,
        "button_rows": rows,
        "coverage": {
            "source_file_count": len(source_entries),
            "interactive_file_count": sum(1 for entry in source_entries if entry.get("interactive_markers")),
            "nearest_test_count": len(tests),
            "button_row_count": len(rows),
            "covered_button_rows": rows_covered,
            "missing_button_rows": rows_missing,
        },
    }


def _source_entries_for_fixture(
    fixture: dict[str, object],
    manifest_files: list[dict[str, object]],
) -> list[dict[str, object]]:
    fixture_id = str(fixture["fixture_id"])
    source_buckets = set(cast(list[str], fixture["source_buckets"]))
    entries: list[dict[str, object]] = []
    for entry in manifest_files:
        if str(entry.get("bucket")) == "test":
            continue
        entry_fixture_ids = {
            str(item.get("fixture_id"))
            for item in cast(list[dict[str, object]], entry.get("screenshot_fixtures", []))
        }
        if fixture_id in entry_fixture_ids or str(entry.get("bucket")) in source_buckets:
            entries.append(entry)
    entries.sort(key=lambda item: str(item["path"]))
    return entries


def _entry_labels(entry: dict[str, object]) -> list[str]:
    labels: list[str] = []
    for marker in cast(list[dict[str, object]], entry.get("interactive_markers", [])):
        label = str(marker.get("label") or "")
        if label:
            labels.append(label)
    return labels


def _button_row(row: dict[str, object]) -> dict[str, object]:
    return {
        "id": row["id"],
        "title": row.get("title", ""),
        "source_file": row.get("source_file", ""),
        "composable": row.get("composable", ""),
        "labels": row.get("labels", []),
        "interactive_kinds": row.get("interactive_kinds", []),
        "existing_tests": row.get("existing_tests", []),
        "missing_tests": row.get("missing_tests", []),
    }


def _unmapped_button_rows(
    button_rows: list[dict[str, object]],
    mapped_row_ids: set[str],
    source_files: set[str],
) -> list[dict[str, object]]:
    rows: list[dict[str, object]] = []
    for row in button_rows:
        row_id = str(row["id"])
        if row_id in mapped_row_ids:
            continue
        unmapped = _button_row(row)
        source_file = str(row.get("source_file") or "")
        if not source_file:
            reason = "missing_source_file"
        elif source_file not in source_files:
            reason = "source_file_not_in_fixture_sources"
        else:
            reason = "not_attached_to_any_view"
        unmapped["unmapped_reason"] = reason
        rows.append(unmapped)
    rows.sort(key=lambda item: str(item["id"]))
    return rows


def _registry_fixtures(manifest: dict[str, object]) -> list[dict[str, object]]:
    registry = manifest.get("screenshot_fixture_registry")
    if isinstance(registry, dict):
        raw_fixtures = registry.get("fixtures")
        if isinstance(raw_fixtures, list):
            return [cast(dict[str, object], fixture) for fixture in raw_fixtures]
    return cast(list[dict[str, object]], screenshot_fixtures.fixture_registry()["fixtures"])


def _load_or_build_manifest(root: Path, manifest_path: Path | None) -> tuple[dict[str, object], str]:
    if manifest_path is not None:
        resolved = _resolve(root, manifest_path)
        if resolved.exists():
            return _read_json_object(resolved), _display_path(root, resolved)
    return ui_manifest.build_manifest(root), "generated"


def _load_or_build_contract(
    root: Path,
    manifest: dict[str, object],
    manifest_path: Path | None,
    contract_path: Path | None,
) -> tuple[dict[str, object], str]:
    if contract_path is not None:
        resolved_contract = _resolve(root, contract_path)
        if resolved_contract.exists():
            return _read_json_object(resolved_contract), _display_path(root, resolved_contract)
    if manifest_path is not None:
        resolved_manifest = _resolve(root, manifest_path)
        if resolved_manifest.exists():
            return button_contract.build_contract(root, resolved_manifest), "generated"
    with tempfile.TemporaryDirectory() as temp:
        manifest_file = Path(temp) / "ui-manifest.json"
        manifest_file.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
        return button_contract.build_contract(root, manifest_file), "generated"


def _read_json_object(path: Path) -> dict[str, object]:
    data = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise UiViewMatrixError(f"expected JSON object at {path}")
    return cast(dict[str, object], data)


def _entry_paths(entries: list[dict[str, object]]) -> list[str]:
    return [str(entry["path"]) for entry in entries]


def _sorted_unique(values: Iterable[str]) -> list[str]:
    return sorted({value for value in values if value})


def _markdown_list(values: list[str], max_items: int = 3) -> str:
    if not values:
        return "—"
    shown = [f"`{value}`" for value in values[:max_items]]
    if len(values) > max_items:
        shown.append(f"+{len(values) - max_items} more")
    return "<br>".join(shown)


def _resolve(root: Path, path: Path) -> Path:
    return path if path.is_absolute() else root / path


def _display_path(root: Path, path: Path) -> str:
    return path.relative_to(root).as_posix() if path.is_relative_to(root) else str(path)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Generate a Ralph UI view matrix from screenshot fixtures, UI manifest, and button contract.")
    parser.add_argument("--repo-root", type=Path, default=Path("."))
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--button-contract", type=Path, default=DEFAULT_BUTTON_CONTRACT)
    parser.add_argument("--out-json", type=Path, default=DEFAULT_JSON)
    parser.add_argument("--out-md", type=Path, default=DEFAULT_MD)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    repo_root = args.repo_root.resolve()
    matrix = build_matrix(repo_root, args.manifest, args.button_contract)
    write_outputs(matrix, repo_root, args.out_json, args.out_md)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
