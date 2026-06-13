#!/usr/bin/env python3

from __future__ import annotations

import argparse
import fnmatch
import json
import math
import subprocess
from collections import Counter
from pathlib import Path
from typing import Any, Iterable, Sequence

SCHEMA = "ralph-validation-v1"
DEFAULT_REVIEWER_MODEL = "gpt5.4-codex-mini"
MAX_CHANGED_FILES = 5
MAX_DIFF_LINES = 500
MAX_UNPUSHED_COMMITS = 1
MIN_DESIGN_SCORE_DELTA = 0.10
VISUAL_ACCEPTANCE_MODES = {"dry-run", "apply-accepted", "commit-accepted"}
PROTECTED_BRANCHES = {"main", "master", "develop", "release", "production"}
FORBIDDEN_GLOBS = (
    ".github/workflows/**",
    "build.gradle.kts",
    "settings.gradle.kts",
    "gradle/**",
    "ci/**",
)
INTERACTIVE_TAGS = {"interactive", "stateful_input"}
ANDROID_CODE_PREFIXES = (
    "app/src/main/",
    "app/src/androidTest/",
    "app/src/test/",
    "domain/",
    "sync-domain/",
    "dictionary-core/",
    "fsrs-java/",
    "writing-core/",
    "update-core/",
)
SONAR_FILE_SUFFIXES = {".kt", ".kts", ".java", ".xml"}
CODE_FAST_SUFFIXES = {".kt", ".kts", ".java", ".xml"}
GENERATED_PREFIXES = (".ralph-loop/",)
STATUS_ALIASES = {
    "ok": "passed",
    "pass": "passed",
    "success": "passed",
    "passed": "passed",
    "failed": "failed",
    "failure": "failed",
    "error": "failed",
    "skipped": "skipped",
    "skip": "skipped",
    "pending": "pending",
    "queued": "pending",
    "running": "pending",
    "in_progress": "pending",
    "in-progress": "pending",
    "missing_artifact": "pending",
    "remote_visual_pending": "pending",
    "needs-host": "needs_host",
    "needs_host": "needs_host",
}


def _gate(gate_id: str, status: str, message: str, **details: object) -> dict[str, object]:
    return {
        "id": gate_id,
        "status": status,
        "message": message,
        "details": {key: value for key, value in details.items() if value is not None},
    }


def _as_list(value: object) -> list[object]:
    if value is None:
        return []
    if isinstance(value, list):
        return value
    if isinstance(value, tuple):
        return list(value)
    if isinstance(value, set):
        return list(value)
    return [value]


def _string_list(value: object) -> list[str]:
    result: list[str] = []
    for item in _as_list(value):
        if item is None:
            continue
        text = str(item).strip()
        if text and text not in result:
            result.append(text)
    return result


def _maybe_load_json(value: object) -> object:
    if isinstance(value, Path):
        if value.exists() and value.is_file():
            return json.loads(value.read_text(encoding="utf-8"))
        return value.as_posix()
    if isinstance(value, str):
        path = Path(value)
        if path.exists() and path.is_file():
            return json.loads(path.read_text(encoding="utf-8"))
    return value


def _get_result(state: dict[str, Any], *keys: str) -> object:
    for key in keys:
        if key in state and state[key] is not None:
            return _maybe_load_json(state[key])
    return None


def _normalize_status(result: object, default: str | None = None) -> str | None:
    if result is None:
        return default
    if isinstance(result, dict):
        status = result.get("status")
        if status is not None:
            text = str(status).strip().lower().replace("-", "_")
            return STATUS_ALIASES.get(text, text)
        if "passed" in result:
            return "passed" if bool(result.get("passed")) else "failed"
        if "returncode" in result:
            try:
                return "passed" if int(result["returncode"]) == 0 else "failed"
            except (TypeError, ValueError):
                return default
        if "exit_code" in result:
            try:
                return "passed" if int(result["exit_code"]) == 0 else "failed"
            except (TypeError, ValueError):
                return default
        if "conclusion" in result:
            conclusion = str(result["conclusion"]).strip().lower().replace("-", "_")
            return STATUS_ALIASES.get(conclusion, conclusion)
    if isinstance(result, bool):
        return "passed" if result else "failed"
    if isinstance(result, int):
        return "passed" if result == 0 else "failed"
    text = str(result).strip().lower().replace("-", "_")
    return STATUS_ALIASES.get(text, text or default)


def _result_model(result: object) -> str | None:
    if isinstance(result, dict):
        for key in ("model", "reviewer_model", "provider_model"):
            value = result.get(key)
            if value:
                return str(value)
    return None


def _numeric(value: object, default: int = 0) -> int:
    if value is None:
        return default
    try:
        return int(value)
    except (TypeError, ValueError):
        return default


def _finite_float_or_none(value: object) -> float | None:
    if isinstance(value, bool) or not isinstance(value, (int, float, str)):
        return None
    try:
        parsed = float(value)
    except ValueError:
        return None
    if not math.isfinite(parsed):
        return None
    return parsed


def _normalized_score_or_none(value: object) -> float | None:
    parsed = _finite_float_or_none(value)
    if parsed is None or parsed < 0.0 or parsed > 1.0:
        return None
    return parsed


def _numeric_float(value: object, default: float = 0.0) -> float:
    if value is None:
        return default
    parsed = _finite_float_or_none(value)
    return default if parsed is None else parsed


def _non_negative_float(value: str) -> float:
    try:
        parsed = float(value)
    except ValueError as exc:
        raise argparse.ArgumentTypeError("must be a finite number >= 0") from exc
    if not math.isfinite(parsed):
        raise argparse.ArgumentTypeError("must be a finite number >= 0")
    if parsed < 0:
        raise argparse.ArgumentTypeError("must be >= 0")
    return parsed


def _read_json_file(path: Path) -> object | None:
    if not path.exists() or not path.is_file():
        return None
    return json.loads(path.read_text(encoding="utf-8"))


def _run_git(repo_root: Path, args: list[str]) -> subprocess.CompletedProcess[str]:
    return subprocess.run(args, cwd=repo_root, text=True, capture_output=True, check=False)


def _git_output(repo_root: Path, args: list[str]) -> str | None:
    process = _run_git(repo_root, args)
    if process.returncode != 0:
        return None
    text = process.stdout.strip()
    return text or None


def _git_lines(repo_root: Path, args: list[str]) -> list[str]:
    process = _run_git(repo_root, args)
    if process.returncode != 0:
        return []
    lines = []
    for line in process.stdout.splitlines():
        text = line.strip()
        if text:
            lines.append(text)
    return lines


def _git_numstat_lines(repo_root: Path) -> int | None:
    process = _run_git(repo_root, ["git", "diff", "--numstat", "HEAD"])
    if process.returncode != 0:
        return None
    total = 0
    for line in process.stdout.splitlines():
        parts = line.split("\t")
        if len(parts) < 3:
            continue
        added, removed = parts[0], parts[1]
        try:
            total += int(added) + int(removed)
        except ValueError:
            continue
    return total


def _git_unpushed_commits(repo_root: Path) -> int | None:
    process = _run_git(repo_root, ["git", "rev-list", "--count", "@{u}..HEAD"])
    if process.returncode != 0:
        return None
    try:
        return int(process.stdout.strip())
    except ValueError:
        return None


def _parse_status_paths(repo_root: Path) -> list[str]:
    lines = _git_lines(repo_root, ["git", "status", "--porcelain=v1", "--untracked-files=all"])
    paths: list[str] = []
    for line in lines:
        if len(line) < 4:
            continue
        path = line[3:]
        if " -> " in path:
            path = path.split(" -> ", 1)[1]
        if path and path not in paths:
            paths.append(path)
    return paths


def collect_repo_state(repo_root: Path, run_dir: Path | None = None) -> dict[str, object]:
    repo_root = repo_root.resolve()
    run_dir = (repo_root / run_dir).resolve() if run_dir is not None and not run_dir.is_absolute() else (run_dir.resolve() if run_dir is not None else None)

    changed_files = _git_lines(repo_root, ["git", "diff", "--name-only", "HEAD"])
    dirty_paths = _parse_status_paths(repo_root)
    for path in dirty_paths:
        if path not in changed_files:
            changed_files.append(path)
    changed_files = sorted(dict.fromkeys(changed_files))
    diff_lines = _git_numstat_lines(repo_root)
    branch = _git_output(repo_root, ["git", "branch", "--show-current"])
    default_branch = _git_output(repo_root, ["git", "rev-parse", "--abbrev-ref", "origin/HEAD"])
    if default_branch:
        default_branch = default_branch.removeprefix("origin/")
    commits_ahead = _git_unpushed_commits(repo_root)

    state: dict[str, object] = {
        "repo_root": repo_root.as_posix(),
        "branch": branch,
        "default_branch": default_branch,
        "changed_files": changed_files,
        "dirty_paths": dirty_paths,
        "focus_paths": changed_files,
        "diff_lines": diff_lines if diff_lines is not None else 0,
        "commits_ahead": commits_ahead,
    }

    if run_dir is not None:
        state["run_dir"] = run_dir.as_posix()
        for relative in (
            "ui-manifest.json",
            "button-contract.json",
            "remote-visual-context.json",
            "profile-reviews.json",
            "targeted-compose-tests.json",
            "ci-fast-result.json",
            "ci-quality-result.json",
            "remote-ci-result.json",
            "reviewer-result.json",
            "validation-state.json",
        ):
            path = run_dir / relative
            data = _read_json_file(path)
            if data is not None:
                key = relative.removesuffix(".json").replace("-", "_")
                state[key] = data

    return state


def build_validation_report(state: dict[str, Any]) -> dict[str, object]:
    normalized = _normalize_state(state)
    changed_files = _string_list(normalized.get("changed_files"))
    dirty_paths = _string_list(normalized.get("dirty_paths"))
    focus_paths = _string_list(normalized.get("focus_paths")) or changed_files
    run_dir = _string(normalized.get("run_dir"))
    branch = _string(normalized.get("branch"))
    default_branch = _string(normalized.get("default_branch"))
    reviewer_model = _string(normalized.get("reviewer_model")) or DEFAULT_REVIEWER_MODEL
    require_remote_green = bool(normalized.get("require_remote_green", False))
    mode = _string(normalized.get("mode"))
    max_changed_files = _numeric(normalized.get("max_changed_files"), MAX_CHANGED_FILES)
    max_diff_lines = _numeric(normalized.get("max_diff_lines"), MAX_DIFF_LINES)
    max_unpushed_commits = _numeric(normalized.get("max_unpushed_commits"), MAX_UNPUSHED_COMMITS)
    min_design_score_delta = _numeric_float(normalized.get("min_design_score_delta"), MIN_DESIGN_SCORE_DELTA)
    diff_lines = _numeric(normalized.get("diff_lines"), 0)
    commits_ahead = normalized.get("commits_ahead")
    commits_ahead_value = None if commits_ahead is None else _numeric(commits_ahead, 0)
    manifest = _get_result(normalized, "manifest")
    button_contract = _get_result(normalized, "button_contract")
    screenshot_result = _get_result(normalized, "screenshot_result", "remote_visual", "remote_visual_context")
    design_review = _get_result(normalized, "design_review")
    button_review = _get_result(normalized, "button_review", "button_qa_review")
    ci_fast_result = _get_result(normalized, "ci_fast_result", "ci_fast")
    ci_quality_result = _get_result(normalized, "ci_quality_result", "ci_quality")
    remote_ci_result = _get_result(normalized, "remote_ci_result", "remote_ci")
    reviewer_result = _get_result(normalized, "reviewer_result", "independent_review")
    profile_reviews = _get_result(normalized, "profile_reviews")
    if isinstance(profile_reviews, dict):
        design_review = (
            design_review
            or profile_reviews.get("design")
            or profile_reviews.get("design_comparison")
            or profile_reviews.get("design-comparison")
        )
        button_review = button_review or profile_reviews.get("button_qa") or profile_reviews.get("button")
        reviewer_result = reviewer_result or profile_reviews.get("independent") or profile_reviews.get("reviewer")

    manifest_entries = _manifest_entry_map(manifest)
    interactive_changed_files = [path for path in changed_files if _is_interactive_changed_file(path, manifest_entries)]
    sonar_inputs_matter = normalized.get("sonar_inputs_matter")
    if sonar_inputs_matter is None:
        sonar_inputs_matter = _has_sonar_inputs(changed_files)
    else:
        sonar_inputs_matter = bool(sonar_inputs_matter)
    ui_change_present = bool(interactive_changed_files) or _looks_like_ui_path_any(changed_files)

    gates: list[dict[str, object]] = []
    gates.append(_branch_guard(branch, default_branch, changed_files, dirty_paths))
    gates.append(_dirty_work_guard(dirty_paths, focus_paths, run_dir))
    gates.append(_forbidden_file_guard(changed_files))
    gates.append(_diff_size_guard(changed_files, diff_lines, max_changed_files, max_diff_lines))
    gates.append(
        _button_contract_delta_guard(
            changed_files,
            manifest,
            button_contract,
            interactive_changed_files,
            ui_change_present,
        )
    )
    gates.append(_targeted_compose_tests_gate(interactive_changed_files, normalized.get("targeted_compose_tests")))
    gates.append(_ci_fast_gate(changed_files, ci_fast_result))
    gates.append(_ci_quality_gate(changed_files, sonar_inputs_matter, ci_quality_result))
    gates.append(_screenshot_availability_gate(interactive_changed_files, screenshot_result, mode))
    gates.append(_design_comparison_gate(interactive_changed_files, design_review, screenshot_result, min_design_score_delta))
    gates.append(_button_qa_review_gate(interactive_changed_files, button_review, button_contract))
    gates.append(_commit_push_frequency_gate(changed_files, commits_ahead_value, max_unpushed_commits))
    gates.append(_remote_ci_sonar_gate(require_remote_green, remote_ci_result))
    gates.append(_independent_review_gate(changed_files, reviewer_result, reviewer_model))

    status = _overall_status(gates)
    counts = Counter(gate["status"] for gate in gates)
    summary = _summary_text(counts)
    return {
        "schema": SCHEMA,
        "status": status,
        "summary": summary,
        "gates": gates,
        "counts": dict(counts),
        "inputs": {
            "branch": branch,
            "default_branch": default_branch,
            "mode": mode,
            "changed_files": changed_files,
            "dirty_paths": dirty_paths,
            "focus_paths": focus_paths,
            "interactive_changed_files": interactive_changed_files,
            "diff_lines": diff_lines,
            "commits_ahead": commits_ahead_value,
            "require_remote_green": require_remote_green,
            "min_design_score_delta": min_design_score_delta,
            "sonar_inputs_matter": bool(sonar_inputs_matter),
            "reviewer_model": reviewer_model,
            "run_dir": run_dir,
        },
    }


# Public aliases kept small so call sites can choose their preferred entrypoint.
def evaluate(state: dict[str, Any]) -> dict[str, object]:
    return build_validation_report(state)


def validate(state: dict[str, Any]) -> dict[str, object]:
    return build_validation_report(state)


def _normalize_state(state: dict[str, Any]) -> dict[str, Any]:
    normalized = dict(state)
    for key in (
        "manifest",
        "button_contract",
        "screenshot_result",
        "remote_visual",
        "remote_visual_context",
        "design_review",
        "button_review",
        "button_qa_review",
        "ci_fast_result",
        "ci_fast",
        "ci_quality_result",
        "ci_quality",
        "remote_ci_result",
        "remote_ci",
        "reviewer_result",
        "independent_review",
        "profile_reviews",
        "targeted_compose_tests",
    ):
        if key in normalized:
            normalized[key] = _maybe_load_json(normalized[key])
    if "profile_reviews" in normalized and isinstance(normalized["profile_reviews"], dict):
        reviews = normalized["profile_reviews"]
        normalized.setdefault(
            "design_review",
            reviews.get("design") or reviews.get("design_comparison") or reviews.get("design-comparison"),
        )
        normalized.setdefault("button_review", reviews.get("button_qa") or reviews.get("button"))
        normalized.setdefault("reviewer_result", reviews.get("independent") or reviews.get("reviewer"))
    if "focus_paths" not in normalized or normalized.get("focus_paths") is None:
        normalized["focus_paths"] = normalized.get("changed_files", [])
    if "changed_files" not in normalized or normalized.get("changed_files") is None:
        normalized["changed_files"] = []
    if "dirty_paths" not in normalized or normalized.get("dirty_paths") is None:
        normalized["dirty_paths"] = []
    return normalized


def _summary_text(counts: Counter[str]) -> str:
    pieces = []
    for key in ("failed", "needs_host", "pending", "passed", "skipped"):
        pieces.append(f"{counts.get(key, 0)} {key}")
    return ", ".join(pieces)


def _overall_status(gates: list[dict[str, object]]) -> str:
    priorities = {"failed": 4, "needs_host": 3, "pending": 2, "passed": 1, "skipped": 0}
    worst = "passed"
    worst_priority = 1
    for gate in gates:
        status = str(gate["status"])
        priority = priorities.get(status, 1)
        if priority > worst_priority:
            worst = status
            worst_priority = priority
        if status == "failed":
            return "failed"
    if worst in {"needs_host", "pending"}:
        return worst
    return "passed"


def _branch_guard(branch: str | None, default_branch: str | None, changed_files: list[str], dirty_paths: list[str]) -> dict[str, object]:
    if not branch:
        if changed_files or dirty_paths:
            return _gate(
                "branch_guard",
                "needs_host",
                "Need a checked-out PR branch before validating changes.",
                branch=branch,
                default_branch=default_branch,
            )
        return _gate("branch_guard", "skipped", "No branch information available.")

    branch_name = branch.strip()
    if not branch_name:
        return _gate("branch_guard", "needs_host", "Need a checked-out PR branch before validating changes.")
    if branch_name in PROTECTED_BRANCHES or branch_name == default_branch or branch_name.startswith(("release/", "releases/", "release-", "hotfix/release")):
        return _gate(
            "branch_guard",
            "failed",
            f"Validation requires a non-main PR branch; current branch is {branch_name!r}.",
            branch=branch_name,
            default_branch=default_branch,
        )
    return _gate("branch_guard", "passed", f"Branch {branch_name!r} is safe for PR validation.", branch=branch_name, default_branch=default_branch)


def _dirty_work_guard(dirty_paths: list[str], focus_paths: list[str], run_dir: str | None) -> dict[str, object]:
    if not dirty_paths:
        return _gate("dirty_work_guard", "passed", "Working tree is clean.")

    allowed = set(focus_paths)
    ignored: list[str] = []
    violations: list[str] = []
    for path in dirty_paths:
        if _is_generated_path(path, run_dir):
            ignored.append(path)
            continue
        if path not in allowed:
            violations.append(path)
    if violations:
        return _gate(
            "dirty_work_guard",
            "failed",
            f"Found unrelated dirty work: {', '.join(violations)}.",
            dirty_paths=dirty_paths,
            focus_paths=focus_paths,
            ignored_dirty_paths=ignored,
        )
    return _gate(
        "dirty_work_guard",
        "passed",
        "Dirty paths are limited to the focused slice or generated artifacts.",
        dirty_paths=dirty_paths,
        focus_paths=focus_paths,
        ignored_dirty_paths=ignored,
    )


def _forbidden_file_guard(changed_files: list[str]) -> dict[str, object]:
    if not changed_files:
        return _gate("forbidden_file_guard", "skipped", "No changed files to inspect.")
    forbidden = [path for path in changed_files if any(fnmatch.fnmatch(path, pattern) for pattern in FORBIDDEN_GLOBS)]
    if forbidden:
        return _gate(
            "forbidden_file_guard",
            "failed",
            f"Forbidden files changed: {', '.join(forbidden)}.",
            forbidden_files=forbidden,
        )
    return _gate("forbidden_file_guard", "passed", "No forbidden files were changed.")


def _diff_size_guard(changed_files: list[str], diff_lines: int, max_changed_files: int, max_diff_lines: int) -> dict[str, object]:
    if not changed_files and diff_lines == 0:
        return _gate("diff_size_guard", "skipped", "No diff to size-check.")
    if len(changed_files) > max_changed_files or diff_lines > max_diff_lines:
        return _gate(
            "diff_size_guard",
            "failed",
            f"Diff is too large: {len(changed_files)} changed files and {diff_lines} diff lines (limits: {max_changed_files} files / {max_diff_lines} lines).",
            changed_files=changed_files,
            diff_lines=diff_lines,
            max_changed_files=max_changed_files,
            max_diff_lines=max_diff_lines,
        )
    return _gate(
        "diff_size_guard",
        "passed",
        f"Diff size is within the {max_changed_files}-file / {max_diff_lines}-line guard.",
        changed_files=changed_files,
        diff_lines=diff_lines,
    )


def _button_contract_delta_guard(
    changed_files: list[str],
    manifest: object,
    button_contract: object,
    interactive_changed_files: list[str],
    ui_change_present: bool,
) -> dict[str, object]:
    if not interactive_changed_files:
        if ui_change_present and (manifest is None or button_contract is None):
            return _gate(
                "button_contract_delta_guard",
                "needs_host",
                "Need UI manifest and button contract artifacts before evaluating interactive files.",
                changed_files=changed_files,
            )
        return _gate("button_contract_delta_guard", "skipped", "No touched interactive files were detected.")

    if manifest is None or button_contract is None:
        return _gate(
            "button_contract_delta_guard",
            "needs_host",
            "Need UI manifest and button contract artifacts before evaluating interactive files.",
            interactive_changed_files=interactive_changed_files,
        )

    contract_rows = _contract_rows(button_contract)
    missing: list[str] = []
    checked_rows: list[str] = []

    for path in interactive_changed_files:
        rows = [row for row in contract_rows if str(row.get("source_file", "")) == path]
        if not rows:
            missing.append(f"missing contract rows for {path}")
            continue
        covered_rows = [row for row in rows if _string_list(row.get("existing_tests"))]
        if not covered_rows:
            missing.append(f"missing direct selector/click coverage for {path}")
            continue
        for row in covered_rows:
            checked_rows.append(str(row.get("id", "")))
            row_missing = _string_list(row.get("missing_tests"))
            if row_missing:
                missing.extend(f"{path}: {item}" for item in row_missing)

    if missing:
        return _gate(
            "button_contract_delta_guard",
            "failed",
            "Touched interactive files still have missing button-test coverage.",
            interactive_changed_files=interactive_changed_files,
            checked_rows=checked_rows,
            missing_tests=missing,
        )

    return _gate(
        "button_contract_delta_guard",
        "passed",
        "Touched interactive files have zero missing button-test coverage.",
        interactive_changed_files=interactive_changed_files,
        checked_rows=checked_rows,
    )


def _targeted_compose_tests_gate(interactive_changed_files: list[str], targeted_tests: object) -> dict[str, object]:
    tests = _as_list(targeted_tests)
    if not interactive_changed_files and not tests:
        return _gate("targeted_compose_tests", "skipped", "No interactive UI change requires targeted Compose tests.")
    if interactive_changed_files and not tests:
        return _gate(
            "targeted_compose_tests",
            "needs_host",
            "Need targeted Compose tests for touched interactive files.",
            interactive_changed_files=interactive_changed_files,
        )
    statuses = [_normalize_status(test, default="needs_host") for test in tests]
    if any(status == "failed" for status in statuses):
        return _gate("targeted_compose_tests", "failed", "At least one targeted Compose test failed.", results=tests)
    if any(status in {"pending", "needs_host"} for status in statuses):
        status = "pending" if any(status == "pending" for status in statuses) else "needs_host"
        return _gate("targeted_compose_tests", status, "Targeted Compose test evidence is not complete yet.", results=tests)
    return _gate("targeted_compose_tests", "passed", "Targeted Compose tests passed.", results=tests)


def _ci_fast_gate(changed_files: list[str], ci_fast_result: object) -> dict[str, object]:
    if not _requires_ci_fast(changed_files):
        return _gate("ci_fast_gate", "skipped", "No Kotlin/Java/Android compile surface changed.")
    status = _normalize_status(ci_fast_result)
    if status is None:
        return _gate("ci_fast_gate", "needs_host", "Need a ciFast/compile result for the touched Android sources.")
    if status == "passed":
        return _gate("ci_fast_gate", "passed", "ciFast/compile gate passed.", result=ci_fast_result)
    return _gate("ci_fast_gate", status, "ciFast/compile gate is not passing yet.", result=ci_fast_result)


def _ci_quality_gate(changed_files: list[str], sonar_inputs_matter: bool, ci_quality_result: object) -> dict[str, object]:
    if not sonar_inputs_matter:
        return _gate("ci_quality_gate", "skipped", "No Sonar inputs were touched.")
    status = _normalize_status(ci_quality_result)
    if status is None:
        return _gate("ci_quality_gate", "needs_host", "Need a ciQuality/Sonar result for the touched sources.")
    if status == "passed":
        return _gate("ci_quality_gate", "passed", "ciQuality/Sonar gate passed.", result=ci_quality_result)
    return _gate("ci_quality_gate", status, "ciQuality/Sonar gate is not passing yet.", result=ci_quality_result)


def _visual_result_identity(result: object) -> dict[str, object]:
    if not isinstance(result, dict):
        return {}
    identity: dict[str, object] = {}
    for key in ("requested_route", "view_id", "fixture_id", "device_profile", "fixture_set_hash"):
        value = result.get(key)
        if value is not None:
            identity[key] = value
    routes = result.get("routes")
    if isinstance(routes, list):
        identity["routes"] = [str(route) for route in routes]
    return identity


def _visual_pair_results(screenshot_result: object) -> tuple[dict[str, object], dict[str, object]] | None:
    if not isinstance(screenshot_result, dict):
        return None
    before = screenshot_result.get("before")
    after = screenshot_result.get("after")
    if isinstance(before, dict) and isinstance(after, dict):
        return before, after
    return None


def _screenshot_availability_gate(interactive_changed_files: list[str], screenshot_result: object, mode: str | None) -> dict[str, object]:
    if not interactive_changed_files:
        return _gate("screenshot_availability", "skipped", "No interactive UI change requires screenshots.")
    pair = _visual_pair_results(screenshot_result)
    if mode in VISUAL_ACCEPTANCE_MODES:
        if pair is None:
            return _gate(
                "screenshot_availability",
                "needs_host",
                "Need before and after screenshot evidence for the touched interactive files.",
                result=screenshot_result,
            )
        before, after = pair
        before_status = _normalize_status(before)
        after_status = _normalize_status(after)
        if before_status is None or after_status is None:
            return _gate(
                "screenshot_availability",
                "needs_host",
                "Need before and after screenshot validation results for the touched interactive files.",
                result=screenshot_result,
            )
        if before_status in {"pending", "needs_host"} or after_status in {"pending", "needs_host"}:
            status = "pending" if "pending" in {before_status, after_status} else "needs_host"
            return _gate("screenshot_availability", status, "Screenshot evidence is not ready yet.", result=screenshot_result)
        if before_status != "passed" or after_status != "passed":
            return _gate("screenshot_availability", "failed", "Before or after screenshot evidence failed.", result=screenshot_result)

        missing_identity_fields: list[str] = []
        before_identity = _visual_result_identity(before)
        after_identity = _visual_result_identity(after)
        for field in ("requested_route", "view_id", "fixture_id", "device_profile", "fixture_set_hash"):
            if field not in before_identity or field not in after_identity:
                missing_identity_fields.append(field)
        if missing_identity_fields:
            return _gate(
                "screenshot_availability",
                "needs_host",
                "Before/after screenshot evidence is missing route/view/fixture identity fields.",
                result=screenshot_result,
                missing_identity_fields=sorted(set(missing_identity_fields)),
            )

        mismatched_identity_fields = [
            field
            for field in sorted(set(before_identity).intersection(after_identity))
            if before_identity[field] != after_identity[field]
        ]
        if mismatched_identity_fields:
            return _gate(
                "screenshot_availability",
                "failed",
                "Before and after screenshot evidence must describe the same route/view/fixture/device profile.",
                result=screenshot_result,
                mismatched_identity_fields=mismatched_identity_fields,
                before_identity=before_identity,
                after_identity=after_identity,
            )
        return _gate("screenshot_availability", "passed", "Screenshot evidence is available.", result=screenshot_result)

    status = _normalize_status(screenshot_result)
    if status is None:
        return _gate("screenshot_availability", "needs_host", "Need screenshot evidence for the touched interactive files.")
    if status == "passed":
        return _gate("screenshot_availability", "passed", "Screenshot evidence is available.", result=screenshot_result)
    if status in {"pending", "needs_host"}:
        return _gate("screenshot_availability", status, "Screenshot evidence is not ready yet.", result=screenshot_result)
    return _gate("screenshot_availability", "failed", "Screenshot evidence failed.", result=screenshot_result)


def _comparison_payload(design_review: object) -> dict[str, object]:
    if not isinstance(design_review, dict):
        return {}
    parsed = design_review.get("parsed")
    if isinstance(parsed, dict):
        return parsed
    return design_review


def _design_comparison_gate(
    interactive_changed_files: list[str],
    design_review: object,
    screenshot_result: object,
    min_score_delta: float,
) -> dict[str, object]:
    if not interactive_changed_files:
        return _gate("design_comparison", "skipped", "No interactive UI change requires design comparison.")
    status = _normalize_status(design_review)
    if status is None:
        return _gate("design_comparison", "needs_host", "Need a design review result for the touched interactive files.")
    if status in {"pending", "needs_host"}:
        return _gate("design_comparison", status, "Design comparison is not ready yet.", result=design_review)
    if status != "passed":
        return _gate("design_comparison", "failed", "Design comparison rejected the change.", result=design_review)

    comparison = _comparison_payload(design_review)
    missing_fields: list[str] = []
    if not isinstance(comparison.get("after_better"), bool):
        missing_fields.append("after_better boolean")

    def score_field(name: str) -> float | None:
        if name in {"score_before", "score_after"}:
            parsed = _normalized_score_or_none(comparison.get(name))
            if parsed is None:
                missing_fields.append(f"{name} finite 0.0..1.0 score")
            return parsed

        parsed = _finite_float_or_none(comparison.get(name))
        if parsed is None:
            missing_fields.append(f"{name} finite number")
        return parsed

    score_before = score_field("score_before")
    score_after = score_field("score_after")
    score_delta = score_field("score_delta")
    if missing_fields:
        return _gate(
            "design_comparison",
            "failed",
            "Design comparison must include explicit after-better, score-before, score-after, and score-delta evidence.",
            missing_fields=missing_fields,
            result=design_review,
            screenshot_result=screenshot_result,
            min_score_delta=min_score_delta,
        )

    assert score_before is not None
    assert score_after is not None
    assert score_delta is not None
    expected_score_delta = score_after - score_before
    if not math.isclose(score_delta, expected_score_delta, rel_tol=1e-9, abs_tol=1e-6):
        return _gate(
            "design_comparison",
            "failed",
            "Design comparison score delta must match score_after - score_before.",
            result=design_review,
            screenshot_result=screenshot_result,
            score_before=score_before,
            score_after=score_after,
            score_delta=score_delta,
            expected_score_delta=expected_score_delta,
            min_score_delta=min_score_delta,
        )

    if comparison.get("after_better") is not True:
        return _gate(
            "design_comparison",
            "failed",
            "Design comparison did not confirm the after screenshot is better.",
            result=design_review,
            screenshot_result=screenshot_result,
            min_score_delta=min_score_delta,
        )
    if score_delta is None or score_delta < min_score_delta:
        return _gate(
            "design_comparison",
            "failed",
            "Design comparison score delta is below the configured threshold.",
            result=design_review,
            screenshot_result=screenshot_result,
            score_delta=score_delta,
            min_score_delta=min_score_delta,
        )
    if comparison.get("issue_resolved") is False:
        return _gate(
            "design_comparison",
            "failed",
            "Design comparison says the accepted issue was not resolved.",
            result=design_review,
            screenshot_result=screenshot_result,
            score_delta=score_delta,
            min_score_delta=min_score_delta,
        )
    if comparison.get("learning_correctness_risk") is True:
        return _gate(
            "design_comparison",
            "failed",
            "Design comparison flagged a learning-correctness risk.",
            result=design_review,
            screenshot_result=screenshot_result,
            score_delta=score_delta,
            min_score_delta=min_score_delta,
        )
    regressions = comparison.get("new_regressions")
    if (isinstance(regressions, list) and regressions) or (regressions is not None and not isinstance(regressions, list)):
        return _gate(
            "design_comparison",
            "failed",
            "Design comparison reported new visual or interaction regressions.",
            result=design_review,
            screenshot_result=screenshot_result,
            score_delta=score_delta,
            min_score_delta=min_score_delta,
        )
    return _gate(
        "design_comparison",
        "passed",
        "Design comparison confirms the after screenshot improved enough.",
        result=design_review,
        screenshot_result=screenshot_result,
        score_delta=score_delta,
        min_score_delta=min_score_delta,
    )


def _button_qa_review_gate(interactive_changed_files: list[str], button_review: object, button_contract: object) -> dict[str, object]:
    if not interactive_changed_files:
        return _gate("button_qa_review", "skipped", "No interactive UI change requires button QA review.")
    status = _normalize_status(button_review)
    if status is None:
        return _gate("button_qa_review", "needs_host", "Need a button QA review result for the touched interactive files.")
    if status == "passed":
        return _gate("button_qa_review", "passed", "Button QA review passed.", result=button_review, button_contract=button_contract)
    if status in {"pending", "needs_host"}:
        return _gate("button_qa_review", status, "Button QA review is not ready yet.", result=button_review)
    return _gate("button_qa_review", "failed", "Button QA review rejected the change.", result=button_review)


def _commit_push_frequency_gate(changed_files: list[str], commits_ahead: int | None, max_unpushed_commits: int) -> dict[str, object]:
    if not changed_files:
        return _gate("commit_push_frequency", "skipped", "No changes to monitor for push frequency.")
    if commits_ahead is None:
        return _gate("commit_push_frequency", "needs_host", "Need upstream tracking information to check push frequency.")
    if commits_ahead > max_unpushed_commits:
        return _gate(
            "commit_push_frequency",
            "failed",
            f"Branch is {commits_ahead} commits ahead of upstream; push smaller slices more often.",
            commits_ahead=commits_ahead,
            max_unpushed_commits=max_unpushed_commits,
        )
    return _gate(
        "commit_push_frequency",
        "passed",
        "Branch push frequency is within the guard.",
        commits_ahead=commits_ahead,
        max_unpushed_commits=max_unpushed_commits,
    )


def _remote_ci_sonar_gate(require_remote_green: bool, remote_ci_result: object) -> dict[str, object]:
    if not require_remote_green:
        return _gate("remote_ci_sonar_gate", "skipped", "Remote CI/Sonar were not required for this change.")
    if remote_ci_result is None:
        return _gate("remote_ci_sonar_gate", "needs_host", "Need remote CI/Sonar evidence because --require-remote-green is set.")
    if isinstance(remote_ci_result, dict):
        nested = []
        for key in ("ci_fast_result", "ci_quality_result", "fast_gate", "sonar_gate", "ciFast", "ciQuality"):
            if key in remote_ci_result:
                nested.append(_normalize_status(remote_ci_result.get(key), default="needs_host"))
        if nested:
            if any(status == "failed" for status in nested):
                return _gate("remote_ci_sonar_gate", "failed", "Remote CI/Sonar evidence shows a failure.", result=remote_ci_result)
            if any(status in {"pending", "needs_host"} for status in nested):
                status = "pending" if any(status == "pending" for status in nested) else "needs_host"
                return _gate("remote_ci_sonar_gate", status, "Remote CI/Sonar evidence is not complete yet.", result=remote_ci_result)
            return _gate("remote_ci_sonar_gate", "passed", "Remote CI/Sonar evidence passed.", result=remote_ci_result)
    status = _normalize_status(remote_ci_result)
    if status is None:
        return _gate("remote_ci_sonar_gate", "needs_host", "Need remote CI/Sonar evidence because --require-remote-green is set.")
    if status == "passed":
        return _gate("remote_ci_sonar_gate", "passed", "Remote CI/Sonar evidence passed.", result=remote_ci_result)
    if status in {"pending", "needs_host"}:
        return _gate("remote_ci_sonar_gate", status, "Remote CI/Sonar evidence is not complete yet.", result=remote_ci_result)
    return _gate("remote_ci_sonar_gate", "failed", "Remote CI/Sonar evidence failed.", result=remote_ci_result)


def _independent_review_gate(changed_files: list[str], reviewer_result: object, reviewer_model: str) -> dict[str, object]:
    if not changed_files:
        return _gate("independent_review_gate", "skipped", "No changes require an independent review.")
    status = _normalize_status(reviewer_result)
    if status is None:
        return _gate(
            "independent_review_gate",
            "needs_host",
            f"Need a {DEFAULT_REVIEWER_MODEL} independent review before proceeding.",
            reviewer_model=reviewer_model,
        )
    actual_model = _result_model(reviewer_result)
    if actual_model and actual_model != DEFAULT_REVIEWER_MODEL:
        return _gate(
            "independent_review_gate",
            "failed",
            f"Independent review must come from {DEFAULT_REVIEWER_MODEL!r}; got {actual_model!r}.",
            reviewer_model=actual_model,
        )
    if reviewer_model and reviewer_model != DEFAULT_REVIEWER_MODEL:
        return _gate(
            "independent_review_gate",
            "failed",
            f"Independent review must use {DEFAULT_REVIEWER_MODEL!r}; got {reviewer_model!r}.",
            reviewer_model=reviewer_model,
        )
    if status == "passed":
        return _gate(
            "independent_review_gate",
            "passed",
            "Independent review passed.",
            result=reviewer_result,
            reviewer_model=actual_model or reviewer_model,
        )
    if status in {"pending", "needs_host"}:
        return _gate(
            "independent_review_gate",
            status,
            "Independent review is not ready yet.",
            result=reviewer_result,
            reviewer_model=actual_model or reviewer_model,
        )
    return _gate(
        "independent_review_gate",
        "failed",
        "Independent review rejected the change.",
        result=reviewer_result,
        reviewer_model=actual_model or reviewer_model,
    )


def _manifest_entry_map(manifest: object) -> dict[str, dict[str, object]]:
    if not isinstance(manifest, dict):
        return {}
    entries = manifest.get("files")
    if not isinstance(entries, list):
        return {}
    result: dict[str, dict[str, object]] = {}
    for entry in entries:
        if isinstance(entry, dict):
            path = entry.get("path")
            if path:
                result[str(path)] = entry
    return result


def _contract_rows(contract: object) -> list[dict[str, object]]:
    if not isinstance(contract, dict):
        return []
    rows = contract.get("rows")
    if not isinstance(rows, list):
        return []
    return [row for row in rows if isinstance(row, dict)]


def _entry_labels(entry: dict[str, object]) -> list[str]:
    labels: list[str] = []
    markers = entry.get("interactive_markers")
    if isinstance(markers, list):
        for marker in markers:
            if not isinstance(marker, dict):
                continue
            label = str(marker.get("label", "")).strip()
            if label and label not in labels:
                labels.append(label)
    if not labels:
        for label in _string_list(entry.get("composables")):
            if label not in labels:
                labels.append(label)
    return labels


def _entry_kinds(entry: dict[str, object]) -> list[str]:
    kinds: list[str] = []
    markers = entry.get("interactive_markers")
    if isinstance(markers, list):
        for marker in markers:
            if not isinstance(marker, dict):
                continue
            kind = str(marker.get("kind", "")).strip()
            if kind and kind not in kinds:
                kinds.append(kind)
    return kinds


def _row_relevant(row: dict[str, object], labels: list[str], kinds: list[str], composables: list[str]) -> bool:
    row_labels = {item.lower() for item in _string_list(row.get("labels"))}
    if labels and row_labels.intersection({label.lower() for label in labels}):
        return True
    # If a file has no extracted labels, fall back to its interactive kinds or composable name
    # so sliders/switches without visible text still get coverage.
    if not labels:
        row_kinds = {item.lower() for item in _string_list(row.get("interactive_kinds"))}
        row_composable = str(row.get("composable", "")).lower()
        if kinds and row_kinds.intersection({kind.lower() for kind in kinds}):
            return True
        if composables and row_composable:
            return any(composable.lower() in row_composable for composable in composables)
    return False


def _is_interactive_changed_file(path: str, manifest_entries: dict[str, dict[str, object]]) -> bool:
    entry = manifest_entries.get(path)
    if not entry:
        return False
    return bool(_entry_labels(entry) or _entry_kinds(entry) or _has_risk_tag(entry, INTERACTIVE_TAGS))


def _has_risk_tag(entry: dict[str, object], tags: set[str]) -> bool:
    current = {item.lower() for item in _string_list(entry.get("risk_tags"))}
    return bool(current.intersection(tags))


def _looks_like_ui_path_any(paths: Iterable[str]) -> bool:
    for path in paths:
        if path.startswith("app/src/") and Path(path).suffix in CODE_FAST_SUFFIXES:
            return True
    return False


def _has_sonar_inputs(paths: Iterable[str]) -> bool:
    for path in paths:
        if Path(path).suffix not in SONAR_FILE_SUFFIXES:
            continue
        if path.startswith(ANDROID_CODE_PREFIXES):
            return True
        if path in {"build.gradle.kts", "settings.gradle.kts"}:
            return True
        if path.startswith("app/src/"):
            return True
    return False


def _requires_ci_fast(paths: Iterable[str]) -> bool:
    for path in paths:
        if Path(path).suffix not in CODE_FAST_SUFFIXES:
            continue
        if path.startswith(ANDROID_CODE_PREFIXES):
            return True
        if path in {"build.gradle.kts", "settings.gradle.kts"}:
            return True
        if path.startswith("app/src/"):
            return True
    return False


def _is_generated_path(path: str, run_dir: str | None) -> bool:
    if path.startswith(GENERATED_PREFIXES):
        return True
    if run_dir:
        run_prefix = Path(run_dir).as_posix().rstrip("/") + "/"
        if path.startswith(run_prefix):
            return True
    return False


def _string(value: object) -> str | None:
    if value is None:
        return None
    text = str(value).strip()
    return text or None


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Validate the Ralph UI change gates.")
    parser.add_argument("--repo-root", type=Path, default=Path("."))
    parser.add_argument("--run-dir", type=Path, default=Path(".ralph-loop/current"))
    parser.add_argument("--state-json", type=Path, default=None)
    parser.add_argument("--out", type=Path, default=None)
    parser.add_argument("--require-remote-green", action="store_true")
    parser.add_argument("--reviewer-model", default=DEFAULT_REVIEWER_MODEL)
    parser.add_argument("--max-changed-files", type=int, default=MAX_CHANGED_FILES)
    parser.add_argument("--max-diff-lines", type=int, default=MAX_DIFF_LINES)
    parser.add_argument("--max-unpushed-commits", type=int, default=MAX_UNPUSHED_COMMITS)
    parser.add_argument("--min-design-score-delta", type=_non_negative_float, default=MIN_DESIGN_SCORE_DELTA)
    return parser


def run(args: argparse.Namespace) -> dict[str, object]:
    repo_root = args.repo_root.resolve()
    run_dir = (repo_root / args.run_dir).resolve() if not args.run_dir.is_absolute() else args.run_dir.resolve()
    state = collect_repo_state(repo_root, run_dir)
    if args.state_json is not None:
        overlay = json.loads(args.state_json.read_text(encoding="utf-8"))
        if not isinstance(overlay, dict):
            raise ValueError("--state-json must contain a JSON object")
        state.update(overlay)
    state["require_remote_green"] = bool(args.require_remote_green)
    state["reviewer_model"] = args.reviewer_model
    state["max_changed_files"] = args.max_changed_files
    state["max_diff_lines"] = args.max_diff_lines
    state["max_unpushed_commits"] = args.max_unpushed_commits
    state["min_design_score_delta"] = args.min_design_score_delta
    report = build_validation_report(state)
    if args.out is not None:
        out_path = args.out if args.out.is_absolute() else run_dir / args.out
    else:
        out_path = run_dir / "validation.json"
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    report["output_path"] = out_path.as_posix()
    return report


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    report = run(args)
    print(json.dumps(report, indent=2, sort_keys=True))
    return 0 if report["status"] == "passed" else 1


if __name__ == "__main__":
    raise SystemExit(main())
