#!/usr/bin/env python3

from __future__ import annotations

import argparse
import json
import math
import re
import shlex
import subprocess
import sys
from pathlib import Path
from typing import Sequence, cast

from scripts.ralph_loop import button_contract
from scripts.ralph_loop import button_latency_inventory
from scripts.ralph_loop import github_screenshots
from scripts.ralph_loop import prompts
from scripts.ralph_loop import ui_manifest
from scripts.ralph_loop import ui_view_matrix

FILE_BUCKET_CHOICES = ("home", "study", "settings", "stats", "games", "shell", "theme", "shared", "all")
BUCKET_PRIORITY = {
    "home": 0,
    "study": 1,
    "settings": 2,
    "stats": 3,
    "games": 4,
    "shell": 5,
    "theme": 6,
    "shared": 7,
}
RISK_WEIGHTS = {
    "interactive": 40,
    "stateful_input": 20,
    "shell_entry": 15,
    "no_nearest_test": 10,
    "visual_theme": 5,
}


def positive_int(value: str) -> int:
    parsed = int(value)
    if parsed < 1:
        raise argparse.ArgumentTypeError("must be >= 1")
    return parsed


class IterationCapParser(argparse.ArgumentParser):
    def parse_args(self, args=None, namespace=None):  # type: ignore[override]
        parsed = cast(argparse.Namespace, super().parse_args(args, namespace))
        if parsed.iterations > parsed.max_iterations:
            self.error("--iterations must be <= --max-iterations")
        return parsed


def build_parser() -> argparse.ArgumentParser:
    parser = IterationCapParser(description="Kani Ralph UI loop controller.")
    parser.add_argument("--repo-root", type=Path, default=Path("."))
    parser.add_argument("--run-dir", type=Path, default=Path(".ralph-loop/current"))
    parser.add_argument(
        "--audit-only",
        action="store_true",
        help="Run validation only; never edit the checkout.",
    )
    parser.add_argument("--iterations", type=positive_int, default=1)
    parser.add_argument("--max-iterations", type=positive_int, default=1)
    parser.add_argument("--file-bucket", default="all", choices=FILE_BUCKET_CHOICES)
    parser.add_argument("--max-files", type=positive_int, default=1)
    parser.add_argument("--critic-profile", default="design")
    parser.add_argument("--button-profile", default="uitester")
    parser.add_argument(
        "--critic-cmd",
        default="hermes -p {profile} chat -Q -t safe -q {prompt}",
    )
    parser.add_argument(
        "--button-cmd",
        default="hermes -p {profile} chat -Q -t safe -q {prompt}",
    )
    parser.add_argument("--agent-cmd", default="")
    parser.add_argument("--reviewer-model", default="gpt5.4-codex-mini")
    parser.add_argument("--reviewer-cmd", default="")
    parser.add_argument("--pr-branch", default="")
    parser.add_argument("--push-pr-branch", action="store_true")
    parser.add_argument("--require-remote-green", action="store_true")
    parser.add_argument("--remote-screenshot-workflow", default="android-screenshots.yml")
    parser.add_argument("--screenshot-artifact", default="android-screenshots")
    parser.add_argument("--screenshot-route", default="all")
    parser.add_argument("--require-remote-screenshots", action="store_true")
    parser.add_argument(
        "--latency-measurements",
        type=Path,
        default=Path(".ralph-loop/current/button-latency-measurements.json"),
        help="Optional button-latency-measurements JSON used for latency inventory evidence.",
    )
    return parser


def _json_text(data: object) -> str:
    return json.dumps(data, indent=2, sort_keys=True, default=str)


def _write_json(path: Path, data: object) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(_json_text(data) + "\n", encoding="utf-8")


def _slugify_path(value: str) -> str:
    slug = re.sub(r"[^A-Za-z0-9]+", "-", value).strip("-")
    return slug.lower() or "file"


def _format_command(template: str, *, prompt: str, profile: str) -> list[str]:
    return [part.format(prompt=prompt, profile=profile) for part in shlex.split(template)]


def _finite_float_or_none(value: object) -> float | None:
    if isinstance(value, bool):
        return None
    if isinstance(value, (int, float)):
        parsed = float(value)
        if math.isfinite(parsed):
            return parsed
    return None


def _normalized_score_or_none(value: object) -> float | None:
    parsed = _finite_float_or_none(value)
    if parsed is None or parsed < 0.0 or parsed > 1.0:
        return None
    return parsed


def _non_empty_string(value: object) -> bool:
    return isinstance(value, str) and bool(value.strip())


def _string_list(value: object) -> bool:
    return isinstance(value, list) and all(_non_empty_string(item) for item in value)


def _non_empty_string_list(value: object) -> bool:
    return _string_list(value) and bool(value)


def _design_critic_schema_errors(parsed: dict[str, object]) -> list[str]:
    errors: list[str] = []
    if parsed.get("schema") != "cheap-ralph-design-critic-v1":
        errors.append("design critic JSON must include schema 'cheap-ralph-design-critic-v1'")
    if "accepted_issues" in parsed:
        errors.append("design critic JSON must use single 'accepted_issue', not list 'accepted_issues'")
    if "highest_priority_issue" in parsed:
        errors.append("design critic JSON must not include legacy 'highest_priority_issue'")
    if not isinstance(parsed.get("passed"), bool):
        errors.append("design critic JSON must include boolean 'passed'")
    if not _non_empty_string(parsed.get("view_id")):
        errors.append("design critic JSON must include non-empty string 'view_id'")
    if not _non_empty_string(parsed.get("before_screenshot_sha256")):
        errors.append("design critic JSON must include non-empty string 'before_screenshot_sha256'")
    if _normalized_score_or_none(parsed.get("score_before")) is None:
        errors.append("design critic JSON must include finite 0.0..1.0 score 'score_before'")
    if not isinstance(parsed.get("rejected_issues"), list):
        errors.append("design critic JSON must include list 'rejected_issues'")
    if not _non_empty_string_list(parsed.get("do_not_touch")):
        errors.append("design critic JSON must include non-empty string list 'do_not_touch'")

    accepted_issue = parsed.get("accepted_issue")
    target_view_spec = parsed.get("target_view_spec")
    if accepted_issue is None:
        if parsed.get("passed") is False:
            errors.append("design critic JSON with passed=false must include one 'accepted_issue'")
        if target_view_spec is not None:
            errors.append("design critic JSON without accepted_issue must set 'target_view_spec' to null")
        return errors

    if parsed.get("passed") is True:
        errors.append("design critic JSON with accepted_issue must set passed=false")
    if not isinstance(accepted_issue, dict):
        errors.append("design critic JSON 'accepted_issue' must be an object or null")
    else:
        for field in ("id", "title", "evidence", "primary_file", "expected_fix"):
            if not _non_empty_string(accepted_issue.get(field)):
                errors.append(f"design critic accepted_issue must include non-empty string '{field}'")
        if str(accepted_issue.get("severity", "")).lower() not in {"low", "medium", "high"}:
            errors.append("design critic accepted_issue severity must be low, medium, or high")
        for field in ("acceptance_criteria", "do_not_touch"):
            if not _non_empty_string_list(accepted_issue.get(field)):
                errors.append(f"design critic accepted_issue must include non-empty string list '{field}'")
    if not isinstance(target_view_spec, dict):
        errors.append("design critic JSON with accepted_issue must include object 'target_view_spec'")
    else:
        if not _non_empty_string(target_view_spec.get("summary")):
            errors.append("design critic target_view_spec must include non-empty string 'summary'")
        for field in ("hierarchy", "copy_changes", "spacing_touch_targets", "accessibility", "material_expectations"):
            if not _non_empty_string_list(target_view_spec.get(field)):
                errors.append(f"design critic target_view_spec must include non-empty string list '{field}'")
    target_screenshot = parsed.get("target_screenshot")
    if target_screenshot is not None and not _non_empty_string(target_screenshot):
        errors.append("design critic target_screenshot must be a non-empty string or null")
    if target_screenshot is None and not _non_empty_string(parsed.get("target_screenshot_unavailable_reason")):
        errors.append("design critic JSON with null target_screenshot must include non-empty 'target_screenshot_unavailable_reason'")
    return errors


def _review_schema_errors(label: str, parsed: object) -> list[str]:
    if not isinstance(parsed, dict):
        return ["reviewer stdout must be a JSON object"]
    if label.startswith("button") and not isinstance(parsed.get("passed"), bool):
        return ["button review JSON must include boolean 'passed'"]
    if label.startswith("design-comparison"):
        errors: list[str] = []
        if parsed.get("schema") != "cheap-ralph-design-comparison-v1":
            errors.append("design comparison JSON must include schema 'cheap-ralph-design-comparison-v1'")
        for field in ("passed", "after_better", "issue_resolved", "learning_correctness_risk"):
            if not isinstance(parsed.get(field), bool):
                errors.append(f"design comparison JSON must include boolean '{field}'")
        for field in ("score_before", "score_after"):
            if _normalized_score_or_none(parsed.get(field)) is None:
                errors.append(f"design comparison JSON must include finite 0.0..1.0 score '{field}'")
        if _finite_float_or_none(parsed.get("score_delta")) is None:
            errors.append("design comparison JSON must include finite number 'score_delta'")
        if not isinstance(parsed.get("new_regressions"), list):
            errors.append("design comparison JSON must include list 'new_regressions'")
        if not isinstance(parsed.get("rationale"), str) or not parsed.get("rationale", "").strip():
            errors.append("design comparison JSON must include non-empty string 'rationale'")
        return errors
    if (
        label.startswith("design-critic")
        or parsed.get("schema") == "cheap-ralph-design-critic-v1"
        or "accepted_issue" in parsed
        or "target_view_spec" in parsed
        or "accepted_issues" in parsed
        or "highest_priority_issue" in parsed
    ):
        return _design_critic_schema_errors(parsed)
    if label.startswith("design"):
        has_file_audit_schema = "visual_problems" in parsed and "interaction_a11y_problems" in parsed
        if not has_file_audit_schema:
            return ["design review JSON is missing expected file-auditor fields"]
    return []


def _run_profile_command(
    template: str,
    prompt: str,
    repo_root: Path,
    *,
    out_dir: Path,
    label: str,
    profile: str,
) -> dict[str, object]:
    out_dir.mkdir(parents=True, exist_ok=True)
    prompt_path = out_dir / f"{label}.prompt.txt"
    result_path = out_dir / f"{label}.result.json"
    prompt_path.write_text(prompt, encoding="utf-8")

    result: dict[str, object] = {
        "label": label,
        "profile": profile,
        "prompt_path": str(prompt_path),
        "result_path": str(result_path),
    }

    if not template:
        result.update(
            {
                "status": "skipped",
                "returncode": None,
                "command": [],
                "command_text": "",
                "stdout": "",
                "stderr": "No profile command configured.",
            }
        )
        _write_json(result_path, result)
        return result

    command = _format_command(template, prompt=prompt, profile=profile)
    process = subprocess.run(command, cwd=repo_root, text=True, capture_output=True, check=False)
    status = "passed" if process.returncode == 0 else "failed"
    result.update(
        {
            "status": status,
            "returncode": process.returncode,
            "command": command,
            "command_text": shlex.join(command),
            "stdout": process.stdout,
            "stderr": process.stderr,
        }
    )
    stdout = process.stdout.strip()
    if stdout:
        try:
            parsed = json.loads(stdout)
            result["parsed"] = parsed
            schema_errors = _review_schema_errors(label, parsed)
            if schema_errors:
                result["status"] = "failed"
                result["schema_errors"] = schema_errors
            elif isinstance(parsed, dict) and parsed.get("passed") is False:
                result["status"] = "failed"
        except json.JSONDecodeError as exc:
            result["status"] = "failed"
            result["schema_errors"] = [f"reviewer stdout was not valid JSON: {exc.msg}"]
    elif result["status"] == "passed":
        result["status"] = "failed"
        result["schema_errors"] = ["reviewer stdout was empty"]
    _write_json(result_path, result)
    return result


def _is_interactive(entry: dict[str, object]) -> bool:
    risk_tags = {str(tag) for tag in entry.get("risk_tags", [])}
    interactive_markers = entry.get("interactive_markers", [])
    return bool(interactive_markers) or "interactive" in risk_tags


def _selection_score(entry: dict[str, object]) -> tuple[int, int, str]:
    bucket = str(entry.get("bucket", ""))
    bucket_rank = BUCKET_PRIORITY.get(bucket, 99)
    risk_tags = {str(tag) for tag in entry.get("risk_tags", [])}
    risk_score = sum(RISK_WEIGHTS.get(tag, 0) for tag in risk_tags)
    path = str(entry.get("path", ""))
    return bucket_rank, -risk_score, path


def _selected_entries(
    manifest_files: list[dict[str, object]],
    bucket: str,
    max_files: int,
) -> list[dict[str, object]]:
    entries = [dict(entry) for entry in manifest_files if str(entry.get("bucket")) != "test"]
    if bucket != "all":
        entries = [entry for entry in entries if str(entry.get("bucket")) == bucket]
    entries.sort(key=_selection_score)
    selected: list[dict[str, object]] = []
    for entry in entries[:max_files]:
        entry["selection_score"] = _selection_score(entry)
        selected.append(entry)
    return selected


def _manifest_slice(manifest: dict[str, object], entry: dict[str, object]) -> dict[str, object]:
    selected = dict(entry)
    return {
        "schema": manifest.get("schema", "ui-manifest-v1"),
        "summary": {
            "file_count": 1,
            "selected_file": str(entry.get("path", "")),
            "bucket": str(entry.get("bucket", "")),
            "selection_score": selected.get("selection_score"),
        },
        "files": [selected],
    }


def _button_contract_slice(
    contract: dict[str, object],
    selected_file: str,
) -> dict[str, object]:
    rows = [
        dict(row)
        for row in contract.get("rows", [])
        if str(row.get("source_file", "")) == selected_file
    ]
    return {
        "schema": contract.get("schema", "button-contract-v1"),
        "source_manifest": contract.get("source_manifest"),
        "rows": rows,
        "summary": {
            "row_count": len(rows),
            "selected_file": selected_file,
            "covered_rows": sum(1 for row in rows if row.get("existing_tests")),
            "missing_rows": sum(1 for row in rows if row.get("missing_tests")),
        },
    }


def _review_with_qa_retry(
    template: str,
    prompt: str,
    repo_root: Path,
    *,
    review_dir: Path,
    initial_profile: str,
) -> dict[str, object]:
    attempts: list[dict[str, object]] = []
    attempts.append(
        _run_profile_command(
            template,
            prompt,
            repo_root,
            out_dir=review_dir,
            label="button-attempt-1",
            profile=initial_profile,
        )
    )
    if attempts[0]["status"] != "passed" and initial_profile != "qa":
        attempts.append(
            _run_profile_command(
                template,
                prompt,
                repo_root,
                out_dir=review_dir,
                label="button-attempt-2",
                profile="qa",
            )
        )
    final_attempt = attempts[-1]
    return {
        "profile": initial_profile,
        "final_profile": final_attempt["profile"],
        "status": final_attempt["status"],
        "used_qa_retry": len(attempts) > 1,
        "attempts": attempts,
        "prompt_path": attempts[0]["prompt_path"],
        "result_path": final_attempt["result_path"],
        "parsed": final_attempt.get("parsed"),
    }


def _severity_rank(value: object | None) -> int:
    severity = str(value or "medium").lower()
    if severity in {"critical", "high"}:
        return 0
    if severity == "medium":
        return 1
    if severity == "low":
        return 2
    return 1


def _design_backlog_items(review: dict[str, object]) -> list[dict[str, object]]:
    raw_design = review["design"]
    design = raw_design if isinstance(raw_design, dict) else {}
    parsed = design.get("parsed")
    parsed = parsed if isinstance(parsed, dict) else {}
    schema_errors = design.get("schema_errors")
    if not isinstance(schema_errors, list):
        schema_errors = _review_schema_errors("design", parsed) if parsed else []
    if schema_errors:
        return [
            {
                "priority": 1,
                "kind": "design-command-failure",
                "file": str(review["file"]),
                "title": "Design review failed schema validation",
                "reason": "; ".join(str(error) for error in schema_errors),
                "source": "design",
                "prompt_path": design.get("prompt_path"),
                "result_path": design.get("result_path"),
            }
        ]
    items: list[dict[str, object]] = []
    accepted_issue = parsed.get("accepted_issue")
    if isinstance(accepted_issue, dict):
        target_spec = parsed.get("target_view_spec")
        target_summary = ""
        if isinstance(target_spec, dict):
            target_summary = str(target_spec.get("summary") or "")
        reason_parts = [str(accepted_issue.get("expected_fix") or accepted_issue.get("evidence") or "")]
        if target_summary:
            reason_parts.append(f"Target view: {target_summary}")
        items.append(
            {
                "priority": _severity_rank(accepted_issue.get("severity")),
                "kind": "design-accepted-issue",
                "file": str(accepted_issue.get("primary_file") or accepted_issue.get("file") or review["file"]),
                "title": str(accepted_issue.get("title") or accepted_issue.get("summary") or "Accepted UI issue"),
                "reason": " ".join(part for part in reason_parts if part),
                "source": "design",
                "prompt_path": design.get("prompt_path"),
                "result_path": design.get("result_path"),
            }
        )
    for field_name, kind, default_title in (
        ("visual_problems", "design-visual-problem", "Accepted visual problem"),
        ("interaction_a11y_problems", "design-interaction-a11y-problem", "Accepted interaction/accessibility problem"),
    ):
        issues = parsed.get(field_name, [])
        if not isinstance(issues, list):
            continue
        for issue in issues:
            if not isinstance(issue, dict):
                continue
            component = str(issue.get("component") or "").strip()
            problem = str(issue.get("problem") or issue.get("title") or issue.get("summary") or default_title)
            title = f"{component}: {problem}" if component else problem
            items.append(
                {
                    "priority": _severity_rank(issue.get("severity")),
                    "kind": kind,
                    "file": str(issue.get("file") or parsed.get("file") or review["file"]),
                    "title": title,
                    "reason": str(issue.get("evidence") or issue.get("expected_fix") or ""),
                    "source": "design",
                    "prompt_path": design.get("prompt_path"),
                    "result_path": design.get("result_path"),
                }
            )
    if not items and design.get("status") != "passed":
        highest = parsed.get("highest_priority_issue")
        if isinstance(highest, dict):
            items.append(
                {
                    "priority": _severity_rank(highest.get("severity")),
                    "kind": "design-command-failure",
                    "file": str(highest.get("file") or review["file"]),
                    "title": str(highest.get("title") or highest.get("summary") or "Design review failed"),
                    "reason": str(highest.get("reason") or highest.get("evidence") or design.get("stderr") or design.get("stdout") or ""),
                    "source": "design",
                    "prompt_path": design.get("prompt_path"),
                    "result_path": design.get("result_path"),
                }
            )
        elif highest:
            items.append(
                {
                    "priority": 1,
                    "kind": "design-command-failure",
                    "file": str(review["file"]),
                    "title": str(highest),
                    "reason": str(design.get("stderr") or design.get("stdout") or ""),
                    "source": "design",
                    "prompt_path": design.get("prompt_path"),
                    "result_path": design.get("result_path"),
                }
            )
        else:
            items.append(
                {
                    "priority": 1,
                    "kind": "design-command-failure",
                    "file": str(review["file"]),
                    "title": "Design review failed",
                    "reason": str(design.get("stderr") or design.get("stdout") or ""),
                    "source": "design",
                    "prompt_path": design.get("prompt_path"),
                    "result_path": design.get("result_path"),
                }
            )
    return items


def _button_backlog_items(review: dict[str, object]) -> list[dict[str, object]]:
    button = review["button"]
    if not isinstance(button, dict) or button.get("status") == "skipped":
        return []
    parsed = button.get("parsed") if isinstance(button.get("parsed"), dict) else {}
    items: list[dict[str, object]] = []
    source_prompt_path = button.get("prompt_path")
    source_result_path = button.get("result_path")
    file_name = str(review["file"])

    missing_contract_rows = parsed.get("missing_contract_rows", [])
    if isinstance(missing_contract_rows, list):
        for row in missing_contract_rows:
            items.append(
                {
                    "priority": 0,
                    "kind": "button-missing-contract-row",
                    "file": file_name,
                    "title": f"Add missing contract row {row}",
                    "reason": f"Missing button contract row for {row}",
                    "source": "button",
                    "prompt_path": source_prompt_path,
                    "result_path": source_result_path,
                }
            )

    missing_click_tests = parsed.get("missing_click_tests", [])
    if isinstance(missing_click_tests, list):
        for row in missing_click_tests:
            items.append(
                {
                    "priority": 1,
                    "kind": "button-missing-click-test",
                    "file": file_name,
                    "title": f"Add click test for {row}",
                    "reason": f"Missing click test coverage for {row}",
                    "source": "button",
                    "prompt_path": source_prompt_path,
                    "result_path": source_result_path,
                }
            )

    missing_disabled = parsed.get("missing_disabled_state_tests", [])
    if isinstance(missing_disabled, list):
        for row in missing_disabled:
            items.append(
                {
                    "priority": 1,
                    "kind": "button-missing-disabled-state-test",
                    "file": file_name,
                    "title": f"Add disabled/loading state test for {row}",
                    "reason": f"Missing disabled/loading coverage for {row}",
                    "source": "button",
                    "prompt_path": source_prompt_path,
                    "result_path": source_result_path,
                }
            )

    a11y_gaps = parsed.get("a11y_gaps", [])
    if isinstance(a11y_gaps, list):
        for gap in a11y_gaps:
            if isinstance(gap, dict):
                gap_text = str(gap.get("gap") or gap.get("reason") or gap.get("summary") or gap.get("row") or gap)
                row = str(gap.get("row") or file_name)
            else:
                gap_text = str(gap)
                row = file_name
            items.append(
                {
                    "priority": 2,
                    "kind": "button-a11y-gap",
                    "file": file_name,
                    "title": f"Fix accessibility gap for {row}",
                    "reason": gap_text,
                    "source": "button",
                    "prompt_path": source_prompt_path,
                    "result_path": source_result_path,
                }
            )

    if not items and button.get("status") != "passed":
        fix = parsed.get("highest_priority_fix")
        if isinstance(fix, dict):
            items.append(
                {
                    "priority": 0,
                    "kind": "button-command-failure",
                    "file": file_name,
                    "title": str(fix.get("row") or fix.get("title") or "Button review failed"),
                    "reason": str(fix.get("reason") or fix.get("summary") or button.get("stderr") or button.get("stdout") or ""),
                    "source": "button",
                    "prompt_path": source_prompt_path,
                    "result_path": source_result_path,
                }
            )
        else:
            items.append(
                {
                    "priority": 0,
                    "kind": "button-command-failure",
                    "file": file_name,
                    "title": "Button review failed",
                    "reason": str(button.get("stderr") or button.get("stdout") or ""),
                    "source": "button",
                    "prompt_path": source_prompt_path,
                    "result_path": source_result_path,
                }
            )
    return items


def _render_audit_report_markdown(report: dict[str, object]) -> str:
    artifacts = cast(dict[str, object], report["artifacts"])
    lines = [
        "# Ralph audit report",
        "",
        f"Status: `{report['status']}`",
        f"Repository: `{report['repo_root']}`",
        f"Run dir: `{report['run_dir']}`",
        f"File bucket: `{report['selection']['file_bucket']}`",
        f"Selected files: `{report['summary']['selected_files']}`",
        f"Backlog items: `{report['summary']['backlog_items']}`",
        "",
        "## Artifacts",
        "",
        f"- Manifest: `{artifacts['manifest_json']}`",
        f"- UI view matrix JSON: `{artifacts['ui_view_matrix_json']}`",
        f"- UI view matrix Markdown: `{artifacts['ui_view_matrix_markdown']}`",
        f"- Button contract JSON: `{artifacts['button_contract_json']}`",
        f"- Button contract Markdown: `{artifacts['button_contract_markdown']}`",
        f"- Audit report JSON: `{artifacts['audit_report_json']}`",
        f"- Audit report Markdown: `{artifacts['audit_report_markdown']}`",
        "",
        "## File reviews",
        "",
        "| File | Bucket | Design | Button | QA retry | Design prompt | Button prompt |",
        "| --- | --- | --- | --- | --- | --- | --- |",
    ]
    for review in report["file_reviews"]:
        design = review["design"]
        button = review["button"]
        button_prompt = button["attempts"][0]["prompt_path"] if button.get("attempts") else "-"
        lines.append(
            f"| `{review['file']}` | `{review['bucket']}` | `{design['status']}` | `{button['status']}` | `{button.get('used_qa_retry', False)}` | `{design['prompt_path']}` | `{button_prompt}` |"
        )
    lines.extend([
        "",
        "## Prioritized backlog",
        "",
    ])
    if report["backlog"]:
        lines.extend([
            "| Priority | Kind | File | Title | Reason |",
            "| --- | --- | --- | --- | --- |",
        ])
        for item in report["backlog"]:
            lines.append(
                f"| `{item['priority']}` | `{item['kind']}` | `{item['file']}` | {item['title']} | {item['reason']} |"
            )
    else:
        lines.append("No backlog items.")
    lines.append("")
    return "\n".join(lines)


def _build_audit_report(
    *,
    args: argparse.Namespace,
    repo_root: Path,
    run_dir: Path,
    manifest: dict[str, object],
    contract: dict[str, object],
    view_matrix: dict[str, object],
    file_reviews: list[dict[str, object]],
    backlog: list[dict[str, object]],
) -> dict[str, object]:
    summary = {
        "manifest_files": len(manifest.get("files", [])),
        "selected_files": len(file_reviews),
        "interactive_files": sum(1 for review in file_reviews if review["interactive"]),
        "design_passed": sum(1 for review in file_reviews if review["design"].get("status") == "passed"),
        "design_failed": sum(1 for review in file_reviews if review["design"].get("status") != "passed"),
        "button_passed": sum(
            1 for review in file_reviews if review["interactive"] and review["button"].get("status") == "passed"
        ),
        "button_failed": sum(
            1 for review in file_reviews if review["interactive"] and review["button"].get("status") != "passed"
        ),
        "qa_retries": sum(1 for review in file_reviews if review["interactive"] and review["button"].get("used_qa_retry")),
        "backlog_items": len(backlog),
    }
    status = "passed"
    if summary["backlog_items"]:
        status = "failed"
    if any(review["design"].get("status") != "passed" for review in file_reviews):
        status = "failed"
    if any(review["interactive"] and review["button"].get("status") != "passed" for review in file_reviews):
        status = "failed"

    report: dict[str, object] = {
        "schema": "ralph-audit-report-v1",
        "status": status,
        "audit_only": True,
        "repo_root": str(repo_root),
        "run_dir": str(run_dir),
        "selection": {
            "file_bucket": args.file_bucket,
            "max_files": args.max_files,
            "selected_files": len(file_reviews),
        },
        "config": {
            "critic_profile": args.critic_profile,
            "button_profile": args.button_profile,
            "critic_cmd": args.critic_cmd,
            "button_cmd": args.button_cmd,
            "agent_cmd": args.agent_cmd,
            "reviewer_model": args.reviewer_model,
            "reviewer_cmd": args.reviewer_cmd,
            "pr_branch": args.pr_branch,
            "push_pr_branch": args.push_pr_branch,
            "require_remote_green": args.require_remote_green,
        },
        "manifest_summary": manifest.get("summary", {}),
        "ui_view_matrix_summary": view_matrix.get("summary", {}),
        "button_contract_summary": contract.get("summary", {}),
        "summary": summary,
        "file_reviews": file_reviews,
        "backlog": backlog,
        "artifacts": {
            "manifest_json": str(run_dir / "ui-manifest.json"),
            "ui_view_matrix_json": str(run_dir / "ui-view-matrix.json"),
            "ui_view_matrix_markdown": str(run_dir / "ui-view-matrix.md"),
            "button_contract_json": str(run_dir / "button-contract.json"),
            "button_contract_markdown": str(run_dir / "button-contract.md"),
            "button_latency_inventory_json": str(run_dir / "button-latency-inventory.json"),
            "button_latency_inventory_markdown": str(run_dir / "button-latency-inventory.md"),
        },
    }
    report["artifacts"]["audit_report_json"] = str(run_dir / "audit-report.json")
    report["artifacts"]["audit_report_markdown"] = str(run_dir / "audit-report.md")
    return report


def _run_audit_only(args: argparse.Namespace, repo_root: Path, run_dir: Path) -> dict[str, object]:
    run_dir.mkdir(parents=True, exist_ok=True)
    audit_dir = run_dir / "audit"
    audit_dir.mkdir(parents=True, exist_ok=True)

    manifest = ui_manifest.build_manifest(repo_root)
    manifest_path = run_dir / "ui-manifest.json"
    _write_json(manifest_path, manifest)

    contract = button_contract.build_contract(repo_root, manifest_path)
    contract_path = run_dir / "button-contract.json"
    button_contract.write_outputs(contract, repo_root, contract_path, run_dir / "button-contract.md")

    view_matrix = ui_view_matrix.build_matrix(repo_root, manifest_path, contract_path)
    ui_view_matrix.write_outputs(view_matrix, repo_root, run_dir / "ui-view-matrix.json", run_dir / "ui-view-matrix.md")

    latency_measurements = args.latency_measurements
    if latency_measurements and not latency_measurements.is_absolute():
        latency_measurements = repo_root / latency_measurements

    latency_inventory = button_latency_inventory.build_inventory(
        repo_root,
        manifest_path,
        contract_path,
        latency_measurements if latency_measurements and latency_measurements.exists() else None,
    )
    button_latency_inventory.write_outputs(
        latency_inventory,
        repo_root,
        run_dir / "button-latency-inventory.json",
        run_dir / "button-latency-inventory.md",
    )

    manifest_files = cast(list[dict[str, object]], manifest.get("files", []))
    selected_entries = _selected_entries(manifest_files, args.file_bucket, args.max_files)
    file_reviews: list[dict[str, object]] = []
    backlog: list[dict[str, object]] = []
    for entry in selected_entries:
        review_dir = audit_dir / _slugify_path(str(entry.get("path", "file")))
        review_dir.mkdir(parents=True, exist_ok=True)

        manifest_slice = _manifest_slice(manifest, entry)
        design_prompt = prompts.load_project_prompt(repo_root, "ralph_design_file_auditor.md").render(
            file=str(entry.get("path", "")),
            manifest_json=_json_text(manifest_slice),
        )
        design_result = _run_profile_command(
            args.critic_cmd,
            design_prompt,
            repo_root,
            out_dir=review_dir,
            label="design",
            profile=args.critic_profile,
        )

        interactive = _is_interactive(entry)
        if interactive:
            contract_slice = _button_contract_slice(contract, str(entry.get("path", "")))
            button_prompt = prompts.load_project_prompt(repo_root, "ralph_button_contract_reviewer.md").render(
                manifest_json=_json_text(manifest_slice),
                button_contract_json=_json_text(contract_slice),
            )
            button_result = _review_with_qa_retry(
                args.button_cmd,
                button_prompt,
                repo_root,
                review_dir=review_dir,
                initial_profile=args.button_profile,
            )
        else:
            button_result = {
                "profile": args.button_profile,
                "final_profile": args.button_profile,
                "status": "skipped",
                "used_qa_retry": False,
                "attempts": [],
                "prompt_path": None,
                "result_path": None,
                "parsed": None,
            }

        review = {
            "file": str(entry.get("path", "")),
            "bucket": str(entry.get("bucket", "")),
            "interactive": interactive,
            "selection_score": entry.get("selection_score"),
            "manifest_entry": entry,
            "design": design_result,
            "button": button_result,
        }
        file_reviews.append(review)
        backlog.extend(_design_backlog_items(review))
        backlog.extend(_button_backlog_items(review))

    backlog.sort(key=lambda item: (item["priority"], item["file"], item["kind"], item["title"]))
    report = _build_audit_report(
        args=args,
        repo_root=repo_root,
        run_dir=run_dir,
        manifest=manifest,
        contract=contract,
        view_matrix=view_matrix,
        file_reviews=file_reviews,
        backlog=backlog,
    )

    report_path = run_dir / "audit-report.json"
    report_md_path = run_dir / "audit-report.md"
    _write_json(report_path, report)
    report_md_path.write_text(_render_audit_report_markdown(report), encoding="utf-8")
    return report


def _read_json_file_or_default(path: Path | None, default: dict[str, object]) -> str:
    if path is None or not path.exists():
        return _json_text(default)
    return path.read_text(encoding="utf-8")


def _path_from_result(result: dict[str, object], key: str) -> Path | None:
    value = result.get(key)
    if isinstance(value, str) and value:
        return Path(value)
    return None


def _remote_visual_prompt(result: dict[str, object], repo_root: Path) -> str:
    manifest_json = _read_json_file_or_default(
        _path_from_result(result, "manifest"),
        {"schema": "ui-manifest-v1", "files": [], "status": "not_found"},
    )
    return prompts.load_project_prompt(repo_root, "ralph_design_comparison.md").render(
        screenshots_json=_json_text(result),
        manifest_json=manifest_json,
    )


def _button_contract_prompt(result: dict[str, object], repo_root: Path, run_dir: Path) -> str:
    manifest_json = _read_json_file_or_default(
        _path_from_result(result, "manifest"),
        {"schema": "ui-manifest-v1", "files": [], "status": "not_found"},
    )
    button_contract_json = _read_json_file_or_default(
        run_dir / "button-contract.json",
        {"schema": "button-contract-v1", "rows": [], "status": "not_found"},
    )
    return prompts.load_project_prompt(repo_root, "ralph_button_contract_reviewer.md").render(
        manifest_json=manifest_json,
        button_contract_json=button_contract_json,
    )


def _run_remote_visual_mode(args: argparse.Namespace, repo_root: Path, run_dir: Path) -> dict[str, object]:
    remote_out = run_dir / "remote-screenshots"
    remote_result = github_screenshots.run_remote_screenshots(
        repo_root=repo_root,
        workflow=args.remote_screenshot_workflow,
        artifact=args.screenshot_artifact,
        screenshot_route=args.screenshot_route,
        out_dir=remote_out,
        push_pr_branch=args.push_pr_branch,
        require_remote_screenshots=args.require_remote_screenshots,
    )

    context_path = run_dir / "remote-visual-context.json"
    context_path.parent.mkdir(parents=True, exist_ok=True)
    context_path.write_text(_json_text(remote_result), encoding="utf-8")

    summary: dict[str, object] = {
        "schema": "ralph-remote-visual-v1",
        "status": remote_result["status"],
        "audit_only": False,
        "repo_root": str(repo_root),
        "run_dir": str(run_dir),
        "remote_visual": remote_result,
        "remote_visual_context": str(context_path),
        "profile_reviews": {},
    }

    if remote_result["status"] == "passed":
        design_review = _run_profile_command(
            args.critic_cmd,
            _remote_visual_prompt(remote_result, repo_root),
            repo_root,
            out_dir=run_dir / "remote-visual" / "reviews",
            label="design-comparison",
            profile=args.critic_profile,
        )
        button_review = _run_profile_command(
            args.button_cmd,
            _button_contract_prompt(remote_result, repo_root, run_dir),
            repo_root,
            out_dir=run_dir / "remote-visual" / "reviews",
            label="button-qa",
            profile=args.button_profile,
        )
        summary["profile_reviews"] = {
            "design": design_review,
            "button_qa": button_review,
        }
        if design_review["status"] != "passed" or button_review["status"] != "passed":
            summary["status"] = "failed"

    return summary


def run(args: argparse.Namespace) -> dict[str, object]:
    repo_root = args.repo_root.resolve()
    run_dir = (repo_root / args.run_dir).resolve() if not args.run_dir.is_absolute() else args.run_dir.resolve()
    if args.audit_only:
        return _run_audit_only(args, repo_root, run_dir)
    return _run_remote_visual_mode(args, repo_root, run_dir)


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    result = run(args)
    print(_json_text(result))
    return 0 if result["status"] == "passed" else 1


if __name__ == "__main__":
    sys.exit(main())
