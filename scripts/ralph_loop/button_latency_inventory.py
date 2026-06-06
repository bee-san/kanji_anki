#!/usr/bin/env python3

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Mapping, Sequence, cast

SCHEMA = "button-latency-inventory-v1"
TIMINGS_SCHEMA = "button-latency-measurements-v1"
DEFAULT_MANIFEST = Path(".ralph-loop/current/ui-manifest.json")
DEFAULT_BUTTON_CONTRACT = Path(".ralph-loop/current/button-contract.json")
DEFAULT_JSON = Path(".ralph-loop/current/button-latency-inventory.json")
DEFAULT_MD = Path(".ralph-loop/current/button-latency-inventory.md")
DEFAULT_TARGET_BUDGET_MS = 1_000
RISK_WEIGHTS = {
    "interactive": 20,
    "stateful_input": 20,
    "shell_entry": 15,
    "no_nearest_test": 15,
}
KEYWORD_WEIGHTS = {
    "sync": 25,
    "anki": 20,
    "settings": 15,
    "ladder": 15,
    "stats": 15,
    "focus": 10,
    "queue": 10,
}
SLUG_RE = re.compile(r"[^a-z0-9]+")


class LatencyInventoryError(ValueError):
    pass


def build_inventory(
    repo_root: Path,
    manifest: Mapping[str, object] | Path,
    button_contract: Mapping[str, object] | Path,
    timings: Mapping[str, object] | Path | None = None,
    *,
    target_budget_ms: int = DEFAULT_TARGET_BUDGET_MS,
) -> dict[str, object]:
    root = repo_root.resolve()
    manifest_data, manifest_source = _load_json_or_mapping(root, manifest)
    contract_data, contract_source = _load_json_or_mapping(root, button_contract)
    timing_data, timing_source = _load_timings(root, timings)
    if manifest_data.get("schema") != "ui-manifest-v1":
        raise LatencyInventoryError("manifest must use schema ui-manifest-v1")
    if contract_data.get("schema") != "button-contract-v1":
        raise LatencyInventoryError("button contract must use schema button-contract-v1")

    manifest_files = cast(list[dict[str, object]], manifest_data.get("files", []))
    manifest_by_path = {str(entry.get("path", "")): entry for entry in manifest_files}
    contract_rows = cast(list[dict[str, object]], contract_data.get("rows", []))
    rows = [
        _inventory_row(
            row,
            manifest_by_path,
            timing_data.get(str(row.get("id", "")), {}),
            target_budget_ms,
            source_timings=timing_source,
        )
        for row in contract_rows
    ]
    return {
        "schema": SCHEMA,
        "source_manifest": manifest_source,
        "source_button_contract": contract_source,
        "source_timings": timing_source,
        "target_budget_ms": target_budget_ms,
        "measurement_status": _measurement_status(rows),
        "measurement_notes": _measurement_notes(rows, timing_source),
        "summary": _summary(rows),
        "rows": rows,
    }


def _measurement_status(rows: list[dict[str, object]]) -> str:
    measured_rows = sum(1 for row in rows if row["timing_status"] == "measured")
    if measured_rows == 0:
        return "pending_real_device_timings"
    if measured_rows == len(rows):
        return "manual_timings_complete"
    return "partial_manual_timings"


def _measurement_notes(rows: list[dict[str, object]], source_timings: str) -> list[str]:
    notes = [
        "Rows are the deterministic per-button inventory to time on an emulator or device.",
        "Fill baseline_ms and after_ms from Android Studio profiler, Perfetto, or instrumentation logs before using this as pass/fail evidence.",
        "The default budget is 1000 ms because the README queue treats waits over about one second as needing justification.",
    ]
    if source_timings != "not-provided":
        notes.append(f"Applied measurement inputs from {source_timings}.")
    measured_rows = sum(1 for row in rows if row["timing_status"] == "measured")
    if measured_rows:
        notes.append(f"{measured_rows}/{len(rows)} buttons currently have measured baseline+after timing.")
    if any(row["timing_status"] in {"measured", "pending_after_ms"} for row in rows):
        notes.append("Timing rows should use baseline_ms and after_ms in milliseconds.")
    return notes


def _load_json_or_mapping(root: Path, source: Mapping[str, object] | Path) -> tuple[dict[str, object], str]:
    if isinstance(source, Path):
        path = source if source.is_absolute() else root / source
        if not path.exists():
            raise LatencyInventoryError(f"input JSON not found: {path}")
        data = json.loads(path.read_text(encoding="utf-8"))
        label = path.relative_to(root).as_posix() if path.is_relative_to(root) else str(path)
    else:
        data = dict(source)
        label = "inline"
    if not isinstance(data, dict):
        raise LatencyInventoryError("input JSON must be an object")
    return data, label


def _load_timings(root: Path, source: Mapping[str, object] | Path | None) -> tuple[dict[str, dict[str, object]], str]:
    if source is None:
        return {}, "not-provided"
    timing_data, _ = _load_json_or_mapping(root, source)
    if timing_data.get("schema") != TIMINGS_SCHEMA:
        raise LatencyInventoryError(f"timing data must use schema {TIMINGS_SCHEMA}")
    rows = cast(list[dict[str, object]], timing_data.get("rows", []))
    return {
        str(row.get("id", "")): cast(dict[str, object], row)
        for row in rows
        if isinstance(row, Mapping) and str(row.get("id", ""))
    }, _timing_label(root, source)


def _timing_label(root: Path, source: Mapping[str, object] | Path | None) -> str:
    if source is None:
        return "not-provided"
    if isinstance(source, Path):
        path = source if source.is_absolute() else root / source
        return path.relative_to(root).as_posix() if path.is_relative_to(root) else str(path)
    return "inline"


def _inventory_row(
    row: dict[str, object],
    manifest_by_path: dict[str, dict[str, object]],
    timing: Mapping[str, object],
    target_budget_ms: int,
    *,
    source_timings: str = "not-provided",
) -> dict[str, object]:
    source_path = str(row.get("source_file", ""))
    source = manifest_by_path.get(source_path, {})
    labels = _strings(row.get("labels", []))
    missing_tests = _strings(row.get("missing_tests", []))
    existing_tests = _strings(row.get("existing_tests", []))
    risk_tags = sorted(set(_strings(source.get("risk_tags", [])))) if source else []
    row_id = str(row.get("id", ""))
    baseline_ms = _int_or_none(timing.get("baseline_ms"))
    after_ms = _int_or_none(timing.get("after_ms"))
    timing_status = _timing_status(source_timings, baseline_ms, after_ms)
    timing_delta = (
        after_ms - baseline_ms if isinstance(baseline_ms, int) and isinstance(after_ms, int) else None
    )
    reasons = _risk_reasons(
        row,
        source,
        risk_tags,
        missing_tests,
        existing_tests,
        baseline_ms,
        after_ms,
        target_budget_ms,
    )
    score = _risk_score(
        row,
        source,
        risk_tags,
        missing_tests,
        existing_tests,
        labels,
        timing_status,
        baseline_ms,
        after_ms,
        target_budget_ms,
    )
    return {
        "id": row_id,
        "title": str(row.get("title", "")),
        "source_file": source_path,
        "bucket": str(source.get("bucket", "")) if source else "",
        "composable": str(row.get("composable", "")),
        "labels": labels,
        "trace_name": f"kani.button.{_slug(str(row.get('id', 'button')))}",
        "target_budget_ms": target_budget_ms,
        "baseline_ms": baseline_ms,
        "after_ms": after_ms,
        "timing_status": timing_status,
        "timing_delta_ms": timing_delta,
        "timing_source": source_timings,
        "latency_risk_score": score,
        "latency_risk_level": _risk_level(score),
        "latency_risk_reasons": reasons,
        "source_risk_tags": risk_tags,
        "existing_tests": existing_tests,
        "missing_tests": missing_tests,
    }


def _timing_status(source_timings: str, baseline_ms: int | None, after_ms: int | None) -> str:
    if source_timings == "not-provided":
        return "pending_manual_timing"
    if baseline_ms is not None and after_ms is not None:
        return "measured"
    if baseline_ms is not None:
        return "pending_after_ms"
    if after_ms is not None:
        return "pending_baseline_ms"
    return "missing_timing_fields"


def _strings(value: object) -> list[str]:
    if not isinstance(value, list):
        return []
    return [str(item) for item in value]


def _int_or_none(value: object) -> int | None:
    if value is None:
        return None
    if isinstance(value, int):
        return value
    if isinstance(value, float):
        return int(value)
    if isinstance(value, str) and value.strip():
        try:
            return int(value.strip())
        except ValueError:
            return None
    return None


def _risk_reasons(
    row: dict[str, object],
    source: dict[str, object],
    risk_tags: list[str],
    missing_tests: list[str],
    existing_tests: list[str],
    baseline_ms: int | None,
    after_ms: int | None,
    target_budget_ms: int,
) -> list[str]:
    reasons: list[str] = []
    if missing_tests:
        reasons.append("missing direct click/selector coverage before timing can be trusted")
    if not existing_tests:
        reasons.append("no existing button click test evidence")
    if "interactive" in risk_tags:
        reasons.append("interactive control needs click-to-idle timing")
    if "stateful_input" in risk_tags:
        reasons.append("stateful control can trigger recomposition or storage writes")
    if "shell_entry" in risk_tags:
        reasons.append("shell/navigation entry point can hide whole-screen refresh work")
    if "no_nearest_test" in risk_tags:
        reasons.append("source has no nearby test in the UI manifest")
    haystack = _row_keyword_haystack(row, source)
    if "sync" in haystack or "anki" in haystack:
        reasons.append("sync/provider path may perform database or import work")
    if "stats" in haystack:
        reasons.append("stats path must stay on cached/precomputed reads where possible")
    if "settings" in haystack or "ladder" in haystack:
        reasons.append("settings/ladder changes often persist state and refresh previews")
    if baseline_ms is not None and after_ms is not None:
        if after_ms <= target_budget_ms:
            reasons.append("timing is within the target budget")
        if after_ms > baseline_ms:
            reasons.append("regressed versus baseline timing")
        else:
            reasons.append("post-fix timing is not slower than baseline")
    if not reasons:
        reasons.append("low static risk; still needs real timing evidence")
    return reasons


def _risk_score(
    row: dict[str, object],
    source: dict[str, object],
    risk_tags: list[str],
    missing_tests: list[str],
    existing_tests: list[str],
    labels: list[str],
    timing_status: str = "pending_manual_timing",
    baseline_ms: int | None = None,
    after_ms: int | None = None,
    target_budget_ms: int = DEFAULT_TARGET_BUDGET_MS,
) -> int:
    score = 10 + min(len(labels) * 3, 15)
    for tag in risk_tags:
        score += RISK_WEIGHTS.get(tag, 0)
    score += min(len(missing_tests) * 12, 30)
    if not existing_tests:
        score += 10
    haystack = _row_keyword_haystack(row, source)
    for keyword, weight in KEYWORD_WEIGHTS.items():
        if keyword in haystack:
            score += weight
    if baseline_ms is not None and after_ms is not None:
        if after_ms <= target_budget_ms:
            score = max(10, score - 18)
        else:
            score = min(100, score + 16)
    elif timing_status in {"measured", "pending_after_ms", "pending_baseline_ms"}:
        score = max(10, score - 10)
    return min(score, 100)


def _row_keyword_haystack(row: dict[str, object], source: dict[str, object]) -> str:
    source_name = Path(str(row.get("source_file", ""))).name
    parts = [
        str(row.get("id", "")),
        str(row.get("title", "")),
        source_name,
        str(row.get("composable", "")),
        str(source.get("bucket", "")),
        " ".join(_strings(row.get("labels", []))),
        " ".join(_strings(source.get("risk_tags", []))),
    ]
    return " ".join(parts).lower()


def _risk_level(score: int) -> str:
    if score >= 70:
        return "high"
    if score >= 40:
        return "medium"
    return "low"


def _summary(rows: list[dict[str, object]]) -> dict[str, object]:
    return {
        "row_count": len(rows),
        "measured_rows": sum(1 for row in rows if row["timing_status"] == "measured"),
        "pending_timing_rows": sum(1 for row in rows if row["timing_status"] != "measured"),
        "high_risk_rows": sum(1 for row in rows if row["latency_risk_level"] == "high"),
        "missing_click_coverage_rows": sum(1 for row in rows if row["missing_tests"]),
    }


def _slug(value: str) -> str:
    return SLUG_RE.sub("-", value.lower()).strip("-") or "button"


def _render_timing_cell(row: dict[str, object]) -> str:
    baseline_ms = row.get("baseline_ms")
    after_ms = row.get("after_ms")
    if row["timing_status"] == "measured":
        return f"{baseline_ms} -> {after_ms} ms"
    if baseline_ms is None and after_ms is None:
        return str(row["timing_status"])
    if baseline_ms is None:
        return f"after only {after_ms} ms"
    return f"baseline only {baseline_ms} ms"


def render_markdown(inventory: dict[str, object]) -> str:
    lines = [
        "# Ralph Button Latency Inventory",
        "",
        f"Schema: `{inventory['schema']}`",
        f"Default target budget: `{inventory['target_budget_ms']} ms`",
        f"Measurement status: `{inventory['measurement_status']}`",
        "",
        "| ID | Source | Labels | Trace | Budget | Timing | Risk | Gaps |",
        "| --- | --- | --- | --- | --- | --- | --- | --- |",
    ]
    for row in cast(list[dict[str, object]], inventory["rows"]):
        lines.append(
            "| {id} | {source} | {labels} | `{trace}` | {budget} ms | {timing} | {risk} ({score}) | {gaps} |".format(
                id=row["id"],
                source=row["source_file"] or "—",
                labels="<br>".join(cast(list[str], row["labels"])) or "—",
                trace=row["trace_name"],
                budget=row["target_budget_ms"],
                timing=_render_timing_cell(row),
                risk=row["latency_risk_level"],
                score=row["latency_risk_score"],
                gaps="<br>".join(cast(list[str], row["missing_tests"])) or "—",
            )
        )
    lines.extend(
        [
            "",
            "## Measurement notes",
            "",
            *[f"- {note}" for note in cast(list[str], inventory.get("measurement_notes", []))],
        ]
    )
    return "\n".join(lines) + "\n"


def write_outputs(inventory: dict[str, object], repo_root: Path, out_json: Path, out_md: Path) -> None:
    json_path = out_json if out_json.is_absolute() else repo_root / out_json
    md_path = out_md if out_md.is_absolute() else repo_root / out_md
    json_path.parent.mkdir(parents=True, exist_ok=True)
    md_path.parent.mkdir(parents=True, exist_ok=True)
    json_path.write_text(json.dumps(inventory, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    md_path.write_text(render_markdown(inventory), encoding="utf-8")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Generate a deterministic per-button latency inventory from Ralph UI artifacts.")
    parser.add_argument("--repo-root", type=Path, default=Path("."))
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--button-contract", type=Path, default=DEFAULT_BUTTON_CONTRACT)
    parser.add_argument("--timings", type=Path, default=None, help="Optional button latency measurements JSON")
    parser.add_argument("--out-json", type=Path, default=DEFAULT_JSON)
    parser.add_argument("--out-md", type=Path, default=DEFAULT_MD)
    parser.add_argument("--target-budget-ms", type=int, default=DEFAULT_TARGET_BUDGET_MS)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    if args.target_budget_ms < 1:
        raise LatencyInventoryError("--target-budget-ms must be >= 1")
    repo_root = args.repo_root.resolve()
    inventory = build_inventory(
        repo_root,
        args.manifest,
        args.button_contract,
        args.timings,
        target_budget_ms=args.target_budget_ms,
    )
    write_outputs(inventory, repo_root, args.out_json, args.out_md)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
