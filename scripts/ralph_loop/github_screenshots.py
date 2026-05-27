#!/usr/bin/env python3

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import time
from pathlib import Path
from subprocess import CompletedProcess
from typing import Callable, Sequence

Runner = Callable[[list[str], Path | None], CompletedProcess[str]]

PROTECTED_BRANCHES = {"main", "master", "develop", "release", "production"}
EXPECTED_REPO = "bee-san/kanji_anki"


def run_command(args: list[str], cwd: Path | None = None) -> CompletedProcess[str]:
    return subprocess.run(args, cwd=cwd, text=True, capture_output=True, check=False)


def _status(status: str, message: str, **extra: object) -> dict[str, object]:
    result: dict[str, object] = {"status": status, "message": message}
    result.update(extra)
    return result


def _run(runner: Runner, args: Sequence[str], repo_root: Path) -> CompletedProcess[str]:
    return runner(list(args), repo_root)


def _command_text(command: Sequence[str]) -> str:
    return " ".join(command)


def _current_branch(repo_root: Path, runner: Runner) -> tuple[str | None, str | None]:
    process = _run(runner, ["git", "branch", "--show-current"], repo_root)
    if process.returncode != 0:
        return None, process.stderr.strip() or "git branch --show-current failed"
    branch = process.stdout.strip()
    if not branch:
        return None, "Detached HEAD is not safe for remote screenshot dispatch"
    return branch, None


def _current_sha(repo_root: Path, runner: Runner) -> tuple[str | None, str | None]:
    process = _run(runner, ["git", "rev-parse", "HEAD"], repo_root)
    if process.returncode != 0:
        return None, process.stderr.strip() or "git rev-parse HEAD failed"
    sha = process.stdout.strip()
    if not sha:
        return None, "Unable to determine current commit SHA"
    return sha, None


def _default_branch(repo_root: Path, runner: Runner) -> str | None:
    process = _run(runner, ["git", "rev-parse", "--abbrev-ref", "origin/HEAD"], repo_root)
    if process.returncode != 0:
        return None
    ref = process.stdout.strip()
    if not ref:
        return None
    return ref.removeprefix("origin/")


def _is_protected_branch(branch: str, default_branch: str | None = None) -> bool:
    if branch in PROTECTED_BRANCHES or branch == default_branch:
        return True
    return branch.startswith(("release/", "releases/", "release-", "hotfix/release"))


def _gh_ready(repo_root: Path, runner: Runner, require: bool) -> dict[str, object] | None:
    auth = _run(runner, ["gh", "auth", "status"], repo_root)
    if auth.returncode != 0:
        status = "failed" if require else "remote_visual_pending"
        return _status(status, "gh is not authenticated; run `gh auth login` or provide a valid GitHub token.", stderr=auth.stderr.strip())

    repo = _run(runner, ["gh", "repo", "view", "--json", "nameWithOwner", "--jq", ".nameWithOwner"], repo_root)
    if repo.returncode != 0:
        status = "failed" if require else "remote_visual_pending"
        return _status(status, "Unable to verify GitHub repository with `gh repo view`.", stderr=repo.stderr.strip())
    actual = repo.stdout.strip()
    if actual != EXPECTED_REPO:
        return _status("failed", f"Refusing to dispatch screenshots for {actual!r}; expected {EXPECTED_REPO!r}.")
    return None


def _find_run_for_sha(repo_root: Path, runner: Runner, workflow: str, branch: str, sha: str, attempts: int, sleep_seconds: float) -> tuple[int | None, dict[str, object] | None]:
    command = [
        "gh",
        "run",
        "list",
        "--workflow",
        workflow,
        "--branch",
        branch,
        "--json",
        "databaseId,headSha,status,conclusion",
        "--limit",
        "20",
    ]
    last_error = ""
    for _ in range(max(1, attempts)):
        process = _run(runner, command, repo_root)
        if process.returncode != 0:
            last_error = process.stderr.strip() or f"{_command_text(command)} failed"
        else:
            try:
                runs = json.loads(process.stdout or "[]")
            except json.JSONDecodeError as error:
                last_error = f"Unable to parse `gh run list` JSON: {error}"
            else:
                for run in runs:
                    if str(run.get("headSha")) == sha and run.get("databaseId") is not None:
                        return int(run["databaseId"]), run
                last_error = f"No {workflow} run found yet for branch {branch} at {sha}."
        time.sleep(sleep_seconds)
    return None, _status("remote_visual_pending", last_error)


def validate_artifact(out_dir: Path) -> dict[str, object]:
    manifests = sorted(out_dir.rglob("manifest.json"))
    pngs = sorted(out_dir.rglob("*.png"))
    if not manifests:
        return _status("missing_artifact", f"Downloaded artifact under {out_dir} does not contain manifest.json.")
    if not pngs:
        return _status("missing_artifact", f"Downloaded artifact under {out_dir} does not contain any PNG screenshots.", manifest=str(manifests[0]))
    try:
        json.loads(manifests[0].read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as error:
        return _status("missing_artifact", f"Artifact manifest is not valid JSON: {error}", manifest=str(manifests[0]))
    return _status("passed", "Remote screenshot artifact contains a valid manifest and PNG screenshots.", manifest=str(manifests[0]), pngs=[str(path) for path in pngs])


def run_remote_screenshots(
    *,
    repo_root: Path,
    workflow: str,
    artifact: str,
    screenshot_route: str,
    out_dir: Path,
    push_pr_branch: bool = False,
    require_remote_screenshots: bool = False,
    runner: Runner = run_command,
    run_lookup_attempts: int = 12,
    run_lookup_sleep_seconds: float = 5.0,
) -> dict[str, object]:
    repo_root = repo_root.resolve()
    out_dir = out_dir.resolve()

    if os.environ.get("GITHUB_ACTIONS") == "true":
        status = "failed" if require_remote_screenshots else "remote_visual_pending"
        return _status(status, "Refusing to dispatch the screenshot workflow from inside GitHub Actions to avoid self-looping.")

    branch, branch_error = _current_branch(repo_root, runner)
    if branch_error:
        return _status("failed", branch_error)
    assert branch is not None
    default_branch = _default_branch(repo_root, runner)
    if _is_protected_branch(branch, default_branch):
        return _status("failed", f"Refusing to run on protected branch {branch!r}; use a non-main PR branch.")

    sha, sha_error = _current_sha(repo_root, runner)
    if sha_error:
        return _status("failed", sha_error)
    assert sha is not None

    gh_error = _gh_ready(repo_root, runner, require_remote_screenshots)
    if gh_error:
        return gh_error

    if push_pr_branch:
        push = _run(runner, ["git", "push", "-u", "origin", branch], repo_root)
        if push.returncode != 0:
            status = "failed" if require_remote_screenshots else "remote_visual_pending"
            return _status(status, f"Unable to push branch {branch!r}; remote screenshots require the commit to exist on GitHub.", stderr=push.stderr.strip())

    dispatch = _run(runner, ["gh", "workflow", "run", workflow, "--ref", branch, "-f", f"screenshot_route={screenshot_route}"], repo_root)
    if dispatch.returncode != 0:
        status = "failed" if require_remote_screenshots else "remote_visual_pending"
        return _status(status, f"Unable to dispatch workflow {workflow!r}.", stderr=dispatch.stderr.strip())

    run_id, run = _find_run_for_sha(repo_root, runner, workflow, branch, sha, run_lookup_attempts, run_lookup_sleep_seconds)
    if run_id is None:
        assert run is not None
        if require_remote_screenshots:
            run["status"] = "failed"
        return run

    watch = _run(runner, ["gh", "run", "watch", str(run_id), "--exit-status"], repo_root)
    if watch.returncode != 0:
        return _status("failed", f"Screenshot workflow run {run_id} failed or did not complete successfully.", run_id=run_id, stderr=watch.stderr.strip())

    out_dir.mkdir(parents=True, exist_ok=True)
    download = _run(runner, ["gh", "run", "download", str(run_id), "--name", artifact, "--dir", str(out_dir)], repo_root)
    if download.returncode != 0:
        return _status("missing_artifact", f"Unable to download artifact {artifact!r} from run {run_id}.", run_id=run_id, stderr=download.stderr.strip())

    result = validate_artifact(out_dir)
    result["run_id"] = run_id
    result["branch"] = branch
    result["head_sha"] = sha
    return result


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Dispatch and validate Kani Android screenshots from GitHub Actions.")
    parser.add_argument("--repo-root", type=Path, default=Path("."))
    parser.add_argument("--workflow", "--remote-screenshot-workflow", dest="workflow", default="android-screenshots.yml")
    parser.add_argument("--artifact", "--screenshot-artifact", dest="artifact", default="android-screenshots")
    parser.add_argument("--screenshot-route", default="all")
    parser.add_argument("--out", type=Path, default=Path(".ralph-loop/current/remote-screenshots"))
    parser.add_argument("--push-pr-branch", action="store_true")
    parser.add_argument("--require-remote-screenshots", action="store_true")
    parser.add_argument("--run-lookup-attempts", type=int, default=12)
    parser.add_argument("--run-lookup-sleep-seconds", type=float, default=5.0)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    result = run_remote_screenshots(
        repo_root=args.repo_root,
        workflow=args.workflow,
        artifact=args.artifact,
        screenshot_route=args.screenshot_route,
        out_dir=args.out,
        push_pr_branch=args.push_pr_branch,
        require_remote_screenshots=args.require_remote_screenshots,
        run_lookup_attempts=args.run_lookup_attempts,
        run_lookup_sleep_seconds=args.run_lookup_sleep_seconds,
    )
    print(json.dumps(result, indent=2, sort_keys=True))
    if result["status"] in {"passed", "remote_visual_pending"}:
        return 0
    return 1


if __name__ == "__main__":
    sys.exit(main())
