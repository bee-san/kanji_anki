#!/usr/bin/env python3

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Sequence, cast

SCHEMA = "ui-manifest-v1"
UI_ROOTS = ("app/src/main", "app/src/debug", "app/src/androidTest", "app/src/test")
TEST_PARTS = ("/src/test/", "/src/androidTest/", "Test.kt")
INTERACTIVE_PATTERNS = (
    "Button",
    "IconButton",
    "TextButton",
    "FloatingActionButton",
    "TextField",
    "OutlinedTextField",
    "Slider",
    "Switch",
    "Checkbox",
    "RadioButton",
    "DropdownMenu",
    "clickable",
    "selectable",
    "toggleable",
)
STATEFUL_MARKERS = {"TextField", "OutlinedTextField", "Slider", "Switch", "Checkbox", "RadioButton"}
COMPOSABLE_RE = re.compile(r"@Composable\s+(?:private\s+|internal\s+|public\s+)?fun\s+([A-Za-z_][A-Za-z0-9_]*)", re.MULTILINE)
LINE_COMPOSABLE_RE = re.compile(r"@Composable\s+(?:private\s+|internal\s+|public\s+)?fun\s+([A-Za-z_][A-Za-z0-9_]*)")
TEST_TAG_RE = re.compile(r"testTag\(\s*\"([^\"]+)\"\s*\)")
CONTENT_DESCRIPTION_RE = re.compile(r"contentDescription\s*=\s*\"([^\"]+)\"")
TOKEN_RE = re.compile(r"[A-Z]?[a-z]+|[A-Z]+(?=[A-Z]|$)|\d+")


class ManifestError(ValueError):
    pass


def build_manifest(repo_root: Path) -> dict[str, object]:
    root = repo_root.resolve()
    if not root.exists():
        raise ManifestError(f"repo root does not exist: {root}")

    ui_files = [_file_entry(root, path) for path in _iter_candidate_files(root)]
    tests = [entry for entry in ui_files if entry["bucket"] == "test"]
    for entry in ui_files:
        entry["nearest_tests"] = _nearest_tests(entry, tests)
        entry["risk_tags"] = _risk_tags(entry)

    ui_files.sort(key=lambda entry: str(entry["path"]))
    buckets = sorted({str(entry["bucket"]) for entry in ui_files})
    return {"schema": SCHEMA, "files": ui_files, "summary": {"file_count": len(ui_files), "buckets": buckets}}


def _iter_candidate_files(root: Path) -> list[Path]:
    candidates: list[Path] = []
    app_root = root / "app" / "src"
    if not app_root.exists():
        return candidates
    for path in sorted(app_root.rglob("*.kt")):
        relative = path.relative_to(root).as_posix()
        if not relative.startswith(UI_ROOTS):
            continue
        text = path.read_text(encoding="utf-8")
        if _is_test(relative) or _is_ui_source(relative, text):
            candidates.append(path)
    return candidates


def _is_ui_source(relative: str, text: str) -> bool:
    name = Path(relative).name
    if "@Composable" in text:
        return True
    if "ui/theme" in relative or "Theme" in name or "Colors" in name or "Typography" in name or "UiSupport" in name:
        return True
    if "Color.rgb" in text or "MaterialTheme" in text:
        return True
    return any(f"{marker}(" in text or f".{marker}" in text for marker in INTERACTIVE_PATTERNS)


def _file_entry(root: Path, path: Path) -> dict[str, object]:
    relative = path.relative_to(root).as_posix()
    text = path.read_text(encoding="utf-8")
    return {
        "path": relative,
        "bucket": _bucket(relative),
        "composables": _composables(text),
        "interactive_markers": _interactive_markers(text),
        "nearest_tests": [],
        "risk_tags": [],
    }


def _bucket(relative: str) -> str:
    lower = relative.lower()
    name = Path(relative).name.lower()
    if _is_test(relative):
        return "test"
    if "/ui/theme/" in lower or "theme" in name or "color" in name or "typography" in name or "uisupport" in name:
        return "theme"
    if "home" in name:
        return "home"
    if "study" in name or "flashcard" in name or "writing" in name:
        return "study"
    if "settings" in name:
        return "settings"
    if "stats" in name or "metric" in name:
        return "stats"
    if "games" in name or "game" in name:
        return "games"
    if any(token in name for token in ("mainactivity", "shell", "route", "navigation", "screenshotroute")):
        return "shell"
    return "shared"


def _is_test(relative: str) -> bool:
    return any(part in relative for part in TEST_PARTS)


def _composables(text: str) -> list[str]:
    names = COMPOSABLE_RE.findall(text)
    if names:
        return names
    fallback: list[str] = []
    for line in text.splitlines():
        match = LINE_COMPOSABLE_RE.search(line)
        if match:
            fallback.append(match.group(1))
    return fallback


def _interactive_markers(text: str) -> list[dict[str, object]]:
    markers: list[dict[str, object]] = []
    for number, line in enumerate(text.splitlines(), start=1):
        kind = _line_marker_kind(line)
        if kind is None:
            continue
        markers.append({"kind": kind, "line": number, "label": _line_label(line), "snippet": line.strip()[:160]})
    return markers


def _line_marker_kind(line: str) -> str | None:
    for marker in INTERACTIVE_PATTERNS:
        if f"{marker}(" in line or f".{marker}" in line:
            return marker
    if "onClick" in line or "onCheckedChange" in line or "onValueChange" in line:
        return "callback"
    return None


def _line_label(line: str) -> str:
    for regex in (TEST_TAG_RE, CONTENT_DESCRIPTION_RE):
        match = regex.search(line)
        if match:
            return match.group(1)
    quoted = re.search(r"Text\(\s*\"([^\"]+)\"", line)
    if quoted:
        return quoted.group(1)
    return ""


def _nearest_tests(entry: dict[str, object], tests: list[dict[str, object]]) -> list[str]:
    if entry["bucket"] == "test":
        return []
    composables = cast(list[str], entry["composables"])
    entry_tokens = _tokens(str(entry["path"])) | {str(entry["bucket"])} | {token for name in composables for token in _tokens(name)}
    scored: list[tuple[int, str]] = []
    for test in tests:
        test_path = str(test["path"])
        test_tokens = _tokens(test_path)
        score = len(entry_tokens & test_tokens)
        if str(entry["bucket"]) in test_tokens:
            score += 2
        if score > 0:
            scored.append((-score, test_path))
    return [path for _, path in sorted(scored)[:3]]


def _tokens(value: str) -> set[str]:
    return {token.lower() for token in TOKEN_RE.findall(value.replace("_", " ").replace("-", " ")) if len(token) > 1}


def _risk_tags(entry: dict[str, object]) -> list[str]:
    tags: set[str] = set()
    markers = cast(list[dict[str, object]], entry["interactive_markers"])
    if markers:
        tags.add("interactive")
    if any(marker["kind"] in STATEFUL_MARKERS for marker in markers):
        tags.add("stateful_input")
    if entry["bucket"] == "shell":
        tags.add("shell_entry")
    if entry["bucket"] == "theme":
        tags.add("visual_theme")
    if not entry["nearest_tests"] and entry["bucket"] != "test":
        tags.add("no_nearest_test")
    return sorted(tags)


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Generate a deterministic Ralph UI file manifest.")
    parser.add_argument("--repo-root", type=Path, default=Path("."))
    parser.add_argument("--out", type=Path, required=True)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    repo_root = args.repo_root.resolve()
    out = args.out if args.out.is_absolute() else repo_root / args.out
    manifest = build_manifest(repo_root)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
