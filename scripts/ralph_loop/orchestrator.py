#!/usr/bin/env python3

from __future__ import annotations

import argparse
import json
import shlex
import subprocess
import sys
from pathlib import Path
from typing import Sequence, cast

from scripts.ralph_loop import github_screenshots


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
    parser.add_argument("--audit-only", action="store_true", help="Run review/validation steps only; never edit the checkout.")
    parser.add_argument("--iterations", type=positive_int, default=1)
    parser.add_argument("--max-iterations", type=positive_int, default=1)
    parser.add_argument("--file-bucket", default="all")
    parser.add_argument("--max-files", type=positive_int, default=1)
    parser.add_argument("--critic-profile", default="design")
    parser.add_argument("--button-profile", default="uitester")
    parser.add_argument("--critic-cmd", default="hermes -p design chat -Q -t safe -q {prompt}")
    parser.add_argument("--button-cmd", default="hermes -p uitester chat -Q -t safe -q {prompt}")
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
    return parser


def _run_profile_command(template: str, prompt: str, repo_root: Path) -> dict[str, object]:
    if not template:
        return {"status": "skipped", "message": "No profile command configured."}
    command = [part.format(prompt=prompt) for part in shlex.split(template)]
    process = subprocess.run(command, cwd=repo_root, text=True, capture_output=True, check=False)
    status = "passed" if process.returncode == 0 else "failed"
    return {"status": status, "returncode": process.returncode, "stdout": process.stdout, "stderr": process.stderr}


def _remote_visual_prompt(result: dict[str, object]) -> str:
    return (
        "Inspect the Kani remote Android screenshot artifact. Return JSON only with visual/status findings.\n\n"
        + json.dumps(result, indent=2, sort_keys=True)
    )


def run(args: argparse.Namespace) -> dict[str, object]:
    repo_root = args.repo_root.resolve()
    run_dir = (repo_root / args.run_dir).resolve() if not args.run_dir.is_absolute() else args.run_dir.resolve()
    remote_out = run_dir / "remote-screenshots"

    summary: dict[str, object] = {
        "status": "passed",
        "audit_only": args.audit_only,
        "iterations": args.iterations,
        "max_iterations": args.max_iterations,
        "remote_visual": None,
        "profile_reviews": {},
    }

    # Remote screenshots are the default visual renderer for this controller. The
    # helper itself preserves safety: no GitHub Actions self-loop, no protected
    # branch, and no push unless --push-pr-branch is set.
    remote_result = github_screenshots.run_remote_screenshots(
        repo_root=repo_root,
        workflow=args.remote_screenshot_workflow,
        artifact=args.screenshot_artifact,
        screenshot_route=args.screenshot_route,
        out_dir=remote_out,
        push_pr_branch=args.push_pr_branch,
        require_remote_screenshots=args.require_remote_screenshots,
    )
    summary["remote_visual"] = remote_result
    if remote_result["status"] not in {"passed", "remote_visual_pending"}:
        summary["status"] = remote_result["status"]

    context_path = run_dir / "remote-visual-context.json"
    context_path.parent.mkdir(parents=True, exist_ok=True)
    context_path.write_text(json.dumps(remote_result, indent=2, sort_keys=True), encoding="utf-8")
    summary["remote_visual_context"] = str(context_path)

    if remote_result["status"] == "passed":
        prompt = _remote_visual_prompt(remote_result)
        summary["profile_reviews"] = {
            "design": _run_profile_command(args.critic_cmd, prompt, repo_root),
            "button_qa": _run_profile_command(args.button_cmd, prompt, repo_root),
        }

    return summary


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    result = run(args)
    print(json.dumps(result, indent=2, sort_keys=True))
    if result["status"] in {"passed", "remote_visual_pending"}:
        return 0
    return 1


if __name__ == "__main__":
    sys.exit(main())
