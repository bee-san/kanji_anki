#!/usr/bin/env python3

from __future__ import annotations

import argparse
import json
import re
from dataclasses import dataclass
from pathlib import Path
from typing import Sequence, cast

SCHEMA = "button-contract-v1"
DEFAULT_JSON = Path(".ralph-loop/current/button-contract.json")
DEFAULT_MD = Path(".ralph-loop/current/button-contract.md")
DEFAULT_MANIFEST = Path(".ralph-loop/current/ui-manifest.json")
TEST_PARTS = ("/src/test/", "/src/androidTest/", "Test.kt", "Test.java")
COMPOSABLE_RE = re.compile(r"@Composable\s+(?:private\s+|internal\s+|public\s+)?fun\s+([A-Za-z_][A-Za-z0-9_]*)", re.MULTILINE)
LABEL_RE = re.compile(r"Text\(\s*(?:text\s*=\s*)?\"([^\"]+)\"|(?:label|title|body|subtitle|actionLabel|saveLabel|studyLabel|syncLabel|focusTitle)\s*=\s*\"([^\"]+)\"|contentDescription\s*=\s*\"([^\"]+)\"|testTag\(\s*\"([^\"]+)\"\s*\)")
MODEL_FIELD_RE = re.compile(r"model\.([A-Za-z_][A-Za-z0-9_]*)|([A-Za-z_][A-Za-z0-9_]*)\s*=\s*([A-Za-z_][A-Za-z0-9_]*TextCopy|MainActivityBase|HomeTextCopy)\.")
HANDLER_RE = re.compile(r"on(?:Click|CheckedChange|ValueChange)\s*=\s*(?:\{\s*)?([^,}\n]+)|\.clickable\s*\{\s*([^}\n]+)|\.toggleable\s*\([^)]*onValueChange\s*=\s*([^,)]+)")
INTERACTIVE_RE = re.compile(r"\b(Button|IconButton|TextButton|OutlinedButton|FloatingActionButton|Switch|Checkbox|RadioButton|Slider|TextField|OutlinedTextField)\s*\(|\.(clickable|toggleable|selectable)\b")
SELECTOR_RE = re.compile(r"onNodeWith(Text|Tag|ContentDescription)\(\s*\"([^\"]+)\"\s*\)")
HELPER_CLICK_RE = re.compile(r"performClick(?:able)?WithText\([^;\n]*,\s*\"([^\"]+)\"\s*\)")
PERFORM_CLICK_RE = re.compile(r"\.performClick\s*\(|\.performTouchInput\s*\{|performClick\s*\(")
STATE_COVERAGE_RE = re.compile(r"\bassertIs(?:Not)?Enabled\b|\bisEnabled\s*\(", re.IGNORECASE)
STATE_ASSERT_RE = re.compile(r"\.(assertIs(?:Not)?Enabled)\s*\(", re.IGNORECASE)
TOKEN_RE = re.compile(r"[A-Z]?[a-z]+|[A-Z]+(?=[A-Z]|$)|\d+")


@dataclass(frozen=True)
class Seed:
    id: str
    title: str
    keywords: tuple[str, ...]
    preferred_files: tuple[str, ...]
    preferred_composables: tuple[str, ...]
    expected_labels: tuple[str, ...]


SEEDS = (
    Seed(
        "home-study-cta",
        "Home study CTA",
        ("study", "cta", "home"),
        ("MainActivityHome.kt", "Home", "HomeScreen"),
        ("HomeScreen", "HomePrimaryCta"),
        ("Study now",),
    ),
    Seed(
        "home-sync-cta",
        "Home sync CTA",
        ("sync", "anki", "home"),
        ("MainActivityHome.kt", "Home", "Sync"),
        ("HomeScreen", "SyncResultScreen"),
        ("Sync AnkiDroid", "Sync", "Try sync again"),
    ),
    Seed(
        "home-action-grid",
        "Home action grid",
        ("browse", "mistakes", "stats", "settings", "action"),
        ("HomeAction", "MainActivityHome", "Home"),
        ("HomeAction", "HomeScreen"),
        ("Browse Kanji", "Recent mistakes", "Stats", "Settings"),
    ),
    Seed(
        "home-section-header",
        "Home section header",
        ("focus", "queue", "view", "header"),
        ("Home", "FocusQueue"),
        ("HomeScreen", "HomeSectionHeader"),
        ("Focus queue", "View all >"),
    ),
    Seed(
        "home-sync-metric",
        "Home sync metric",
        ("sync", "metric", "provider"),
        ("HomeMetric", "MainActivityHome", "Stats"),
        ("HomeMetric", "HomeScreen"),
        ("Sync", "AnkiDroid"),
    ),
    Seed(
        "browse-home-button",
        "Browse home button",
        ("browse", "home", "back"),
        ("MainActivityHomeBrowseSearchCompose.kt", "BrowseScreen"),
        ("BrowseScreen", "HomeFullWidthHomeButton"),
        ("Home",),
    ),
    Seed(
        "browse-search-button",
        "Browse search button",
        ("browse", "search", "query"),
        ("MainActivityHomeBrowseSearchCompose.kt", "BrowseScreen"),
        ("BrowseScreen", "browseSearchButtonLabel"),
        ("Search",),
    ),
    Seed(
        "browse-kanji-row",
        "Browse kanji row click",
        ("browse", "kanji", "row"),
        ("MainActivityHomeBrowseSearchCompose.kt", "BrowseScreen"),
        ("BrowseScreen", "BrowseKanjiRow"),
        ("browse-kanji-row-裂",),
    ),
    Seed(
        "home-recent-mistakes-card",
        "Home recent mistakes card click",
        ("home", "recent", "mistakes", "card"),
        ("HomeRecentMistakesCompose.kt",),
        ("HomeRecentMistakesPanel", "HomeRecentMistakesCard"),
        ("home-recent-mistakes-card-裂",),
    ),
    Seed(
        "focus-queue-card",
        "Focus queue card",
        ("focus", "queue", "card", "kanji"),
        ("FocusQueue", "HomeFocusQueue"),
        ("FocusQueue", "HomeFocusQueueCard"),
        ("Study", "Next"),
    ),
    Seed(
        "shared-home-button",
        "Shared home navigation button",
        ("home", "secondary", "navigation"),
        ("HomeChromeCompose.kt", "MainActivityGamesCompose.kt", "MainActivityStatsCompose.kt"),
        ("HomeFullWidthHomeButton",),
        ("Home",),
    ),
    Seed(
        "games-empty-sync-button",
        "Games empty-state sync button",
        ("games", "empty", "sync", "anki"),
        ("MainActivityGamesCompose.kt",),
        ("GamesScreen", "GamesEmptyState"),
        ("Sync AnkiDroid",),
    ),
    Seed(
        "games-result-actions",
        "Games result actions",
        ("games", "result", "round", "next"),
        ("MainActivityGamesResultCompose.kt",),
        ("GamesResultCard",),
        ("Next", "Games", "New round"),
    ),
    Seed(
        "study-pass-fail",
        "Study pass/fail controls",
        ("pass", "fail", "study", "answer"),
        ("Study", "Flashcard", "Writing"),
        ("StudyAnswerPanel", "Flashcard", "Writing"),
        ("Pass", "Fail"),
    ),
    Seed(
        "study-done-actions",
        "Study done actions",
        ("study", "done", "continue", "back", "dialog"),
        ("StudyDoneActions", "StudyMoreNewCardsDialog"),
        ("StudyDoneActions", "StudyMoreNewCardsDialog"),
        ("Study more new cards", "Continue all kanji", "Back home", "Study", "Cancel"),
    ),
    Seed(
        "study-settings-toggle",
        "Study settings category toggle",
        ("category", "header", "toggle"),
        ("MainActivitySettingsCategoryCompose.kt",),
        (),
        ("Study settings",),
    ),
    Seed(
        "settings-save-toggle-reorder",
        "Settings save/toggle/reorder controls",
        ("settings", "ladder", "toggle", "reorder", "move", "save"),
        ("SettingsStudyLadder", "StudyLadder", "SettingsStudySort", "SettingsLearningSteps"),
        ("SettingsStudyLadderPanel", "SettingsNewCardSortPanel", "SettingsLearningStepsPanel"),
        ("On", "Off", "Up", "Down", "Restore defaults"),
    ),
    Seed(
        "settings-new-card-sort",
        "Settings new card sort controls",
        ("settings", "new", "card", "sort", "frequency", "retrievability", "kani"),
        ("SettingsStudySort", "SettingsNewCardSort", "NewCardSort"),
        ("SettingsNewCardSortPanel",),
        (
            "Save new card sort",
            "Frequency",
            "Balanced priority",
            "Anki difficulty",
            "Retrievability risk",
            "Kani weakness",
        ),
    ),
)


class ButtonContractError(ValueError):
    pass


def build_contract(repo_root: Path, manifest_path: Path | None = None) -> dict[str, object]:
    root = repo_root.resolve()
    manifest_file = _resolve(root, manifest_path or DEFAULT_MANIFEST)
    if not manifest_file.exists():
        raise ButtonContractError(f"UI manifest not found: {manifest_file}")
    manifest = json.loads(manifest_file.read_text(encoding="utf-8"))
    source_entries = [entry for entry in manifest.get("files", []) if entry.get("bucket") != "test"]
    sources = [_source_info(root, cast(dict[str, object], entry)) for entry in source_entries]
    tests, state_tests = _test_evidence(root)
    rows = [_row_for_seed(seed, sources, tests, state_tests) for seed in SEEDS]
    return {
        "schema": SCHEMA,
        "source_manifest": manifest_file.relative_to(root).as_posix() if manifest_file.is_relative_to(root) else str(manifest_file),
        "rows": rows,
        "summary": {
            "row_count": len(rows),
            "covered_rows": sum(1 for row in rows if row["existing_tests"]),
            "missing_rows": sum(1 for row in rows if row["missing_tests"]),
        },
    }


def composable_names(text: str) -> list[str]:
    return COMPOSABLE_RE.findall(text)


def _source_info(root: Path, entry: dict[str, object]) -> dict[str, object]:
    relative = str(entry["path"])
    path = root / relative
    text = path.read_text(encoding="utf-8") if path.exists() else ""
    raw_composables = entry.get("composables")
    composables = raw_composables if isinstance(raw_composables, list) else composable_names(text)
    return {
        "path": relative,
        "bucket": str(entry.get("bucket", "")),
        "composables": [str(name) for name in composables],
        "labels": _labels(text),
        "model_fields": _model_fields(text),
        "handlers": _handlers(text),
        "interactive_kinds": _interactive_kinds(text),
        "text": text,
    }


def _labels(text: str) -> list[str]:
    labels: list[str] = []
    for match in LABEL_RE.finditer(text):
        label = next((group for group in match.groups() if group), "")
        if label and label not in labels:
            labels.append(label)
    # Include obvious project constants that are surfaced by model fields.
    constants = {
        "LABEL_STUDY_NOW": "Study now",
        "LABEL_PASS": "Pass",
        "LABEL_FAIL": "Fail",
        "syncAnkiDroidLabel": "Sync with AnkiDroid",
        "viewAllLabel": "View all >",
        "moveUpLabel": "Up",
        "moveDownLabel": "Down",
        "restoreLabel": "Restore defaults",
        "restoreDefaultLadderLabel": "Restore defaults",
    }
    for key, value in constants.items():
        if key in text and value not in labels:
            labels.append(value)
    if "toggleLabel" in text:
        for value in ("On", "Off"):
            if value not in labels:
                labels.append(value)
    return labels


def _model_fields(text: str) -> list[str]:
    fields: list[str] = []
    for match in MODEL_FIELD_RE.finditer(text):
        field = match.group(1) or match.group(2)
        if field and field not in fields:
            fields.append(field)
    return fields


def _handlers(text: str) -> list[str]:
    handlers: list[str] = []
    for match in HANDLER_RE.finditer(text):
        handler = next((group for group in match.groups() if group), "").strip()
        if handler and handler not in handlers:
            handlers.append(handler[:120])
    return handlers


def _interactive_kinds(text: str) -> list[str]:
    kinds: list[str] = []
    for match in INTERACTIVE_RE.finditer(text):
        kind = match.group(1) or match.group(2)
        if kind and kind not in kinds:
            kinds.append(kind)
    return kinds


def _test_evidence(root: Path) -> tuple[
    dict[str, list[dict[str, str]]],
    dict[str, list[dict[str, str]]],
]:
    click_evidence: dict[str, list[dict[str, str]]] = {}
    state_evidence: dict[str, list[dict[str, str]]] = {}
    app_root = root / "app" / "src"
    if not app_root.exists():
        return click_evidence, state_evidence
    for path in sorted(app_root.rglob("*Test.*")):
        relative = path.relative_to(root).as_posix()
        if not any(part in relative for part in TEST_PARTS):
            continue
        text = path.read_text(encoding="utf-8")
        for label, selector in _direct_selectors(text):
            click_evidence.setdefault(label, []).append({"path": relative, "selector": selector})
        for label, selector in _state_selectors(text):
            state_evidence.setdefault(label, []).append({"path": relative, "selector": selector})
    return click_evidence, state_evidence


def _direct_selectors(text: str) -> list[tuple[str, str]]:
    selectors: list[tuple[str, str]] = []
    for match in SELECTOR_RE.finditer(text):
        label = match.group(2)
        trailer = _selector_statement_trailer(text, match)
        if PERFORM_CLICK_RE.search(trailer):
            selectors.append((label, f"onNodeWith{match.group(1)}(\"{label}\") + performClick"))
    for match in HELPER_CLICK_RE.finditer(text):
        label = match.group(1)
        selectors.append((label, f"performClickableWithText(\"{label}\")"))
    return selectors


def _state_selectors(text: str) -> list[tuple[str, str]]:
    selectors: list[tuple[str, str]] = []
    for match in SELECTOR_RE.finditer(text):
        label = match.group(2)
        trailer = _selector_statement_trailer(text, match)
        enabled_match = STATE_ASSERT_RE.search(trailer)
        if enabled_match:
            selectors.append((label, f"onNodeWith{match.group(1)}(\"{label}\") + {enabled_match.group(1)}"))
    return selectors


def _selector_statement_trailer(text: str, match: re.Match[str]) -> str:
    next_selector = SELECTOR_RE.search(text, match.end())
    next_semicolon = text.find(";", match.end())
    next_non_chain_line = re.search(r"\n[ \t]*(?!\.)", text[match.end():])
    stops = [len(text)]
    if next_selector:
        stops.append(next_selector.start())
    if next_semicolon != -1:
        stops.append(next_semicolon)
    if next_non_chain_line:
        stops.append(match.end() + next_non_chain_line.start())
    return text[match.end():min(stops)]


def _row_for_seed(
    seed: Seed,
    sources: list[dict[str, object]],
    tests: dict[str, list[dict[str, str]]],
    state_tests: dict[str, list[dict[str, str]]],
) -> dict[str, object]:
    source = _best_source(seed, sources)
    labels = _seed_labels(seed, source)
    existing = _existing_tests(labels, tests)
    missing = _missing_tests(seed, labels, existing, source, state_tests)
    return {
        "id": seed.id,
        "priority": "high",
        "title": seed.title,
        "source_file": source.get("path", "") if source else "",
        "composable": _best_composable(seed, source),
        "labels": labels,
        "model_fields": source.get("model_fields", []) if source else [],
        "handlers": source.get("handlers", []) if source else [],
        "interactive_kinds": source.get("interactive_kinds", []) if source else [],
        "existing_tests": existing,
        "missing_tests": missing,
    }


def _best_source(seed: Seed, sources: list[dict[str, object]]) -> dict[str, object]:
    scored: list[tuple[int, str, dict[str, object]]] = []
    for source in sources:
        path = str(source["path"])
        text = str(source["text"])
        composables = cast(list[str], source["composables"])
        labels = cast(list[str], source["labels"])
        haystack = " ".join([path, text, *composables, *labels]).lower()
        score = 0
        for preferred in seed.preferred_files:
            if preferred.lower() in path.lower():
                score += 20
        for preferred in seed.preferred_composables:
            if any(preferred.lower() in composable.lower() for composable in composables):
                score += 16
        for label in seed.expected_labels:
            if label.lower() in haystack:
                score += 9
        score += sum(2 for keyword in seed.keywords if keyword.lower() in haystack)
        if seed.id == "settings-save-toggle-reorder" and "importfilters" in path.lower():
            score -= 100
        if seed.id == "settings-save-toggle-reorder" and "studyladder" in path.lower():
            score += 80
        if seed.id == "settings-new-card-sort" and "studysort" in path.lower():
            score += 80
        if seed.id == "study-done-actions" and "studydoneactions" in path.lower():
            score += 80
        if seed.id == "study-pass-fail" and any(label in labels for label in ("Pass", "Fail")):
            score += 40
        if score > 0:
            scored.append((-score, path, source))
    if not scored:
        return {}
    return sorted(scored)[0][2]


def _best_composable(seed: Seed, source: dict[str, object]) -> str:
    composables = cast(list[str], source.get("composables", [])) if source else []
    for preferred in seed.preferred_composables:
        for composable in composables:
            if preferred.lower() in composable.lower():
                return composable
    return composables[0] if composables else ""


def _seed_labels(seed: Seed, source: dict[str, object]) -> list[str]:
    found = cast(list[str], source.get("labels", [])) if source else []
    if seed.expected_labels:
        # Seed labels are the contract's canonical button names; source scans
        # are only used to pick the best file/composable and to backfill rows
        # when a seed intentionally leaves labels unspecified.
        return list(seed.expected_labels)
    return found[:8]


def _label_hint_in_source(label: str, source: dict[str, object]) -> bool:
    text = str(source.get("text", "")).lower() if source else ""
    fields = " ".join(cast(list[str], source.get("model_fields", []))) if source else ""
    tokens = _tokens(label)
    if all(token in text for token in tokens):
        return True
    # Model-backed labels often appear as studyLabel/syncLabel fields while the
    # human text is assembled in the route model rather than the composable.
    return bool(tokens & _tokens(fields)) and "label" in fields.lower()


def _existing_tests(labels: list[str], tests: dict[str, list[dict[str, str]]]) -> list[str]:
    existing: list[str] = []
    seen: set[str] = set()
    for label in labels:
        for item in _matching_test_items(label, tests):
            entry = f"{item['path']}:{item['selector']}"
            if entry not in seen:
                seen.add(entry)
                existing.append(entry)
    return existing


def _matching_test_items(label: str, tests: dict[str, list[dict[str, str]]]) -> list[dict[str, str]]:
    items = list(tests.get(label, []))
    if label not in {"On", "Off", "Up", "Down"}:
        return items
    word = re.compile(rf"\b{re.escape(label)}\b", re.IGNORECASE)
    for selector_label, selector_items in tests.items():
        if selector_label != label and word.search(selector_label):
            items.extend(selector_items)
    return items


def _missing_tests(
    seed: Seed,
    labels: list[str],
    existing: list[str],
    source: dict[str, object],
    state_tests: dict[str, list[dict[str, str]]],
) -> list[str]:
    missing: list[str] = []
    for label in labels:
        if not any(_entry_covers_label(entry, label) for entry in existing):
            missing.append(f"missing direct selector/click coverage for \"{label}\"")
    if not labels:
        missing.append("missing obvious UI label extraction")
    if source and _needs_enabled_disabled(seed, source) and not _has_enabled_disabled_coverage(labels, existing, state_tests):
        missing.append("missing enabled/disabled state coverage")
    if not source:
        missing.append("missing source mapping")
    return missing


def _entry_covers_label(entry: str, label: str) -> bool:
    if f'"{label}"' in entry:
        return True
    if label in {"On", "Off", "Up", "Down"}:
        return bool(re.search(rf"\b{re.escape(label)}\b", entry, re.IGNORECASE))
    return False


def _needs_enabled_disabled(seed: Seed, source: dict[str, object]) -> bool:
    kinds = set(cast(list[str], source.get("interactive_kinds", [])))
    return seed.id.startswith("settings") or bool(kinds & {"Switch", "Checkbox", "RadioButton", "Slider", "TextField", "OutlinedTextField"})


def _has_enabled_disabled_coverage(
    labels: list[str],
    existing: list[str],
    state_tests: dict[str, list[dict[str, str]]],
) -> bool:
    saw_direct_label = False
    for label in labels:
        label_entries = [entry for entry in existing if _entry_covers_label(entry, label)]
        if not label_entries:
            continue
        saw_direct_label = True
        if any(STATE_COVERAGE_RE.search(entry) for entry in label_entries):
            continue
        if _matching_test_items(label, state_tests):
            continue
        return False
    return saw_direct_label


def _tokens(value: str) -> set[str]:
    return {token.lower() for token in TOKEN_RE.findall(value.replace("_", " ").replace("-", " ")) if len(token) > 1}


def render_markdown(contract: dict[str, object]) -> str:
    lines = ["# Ralph Button Contract", "", f"Schema: `{contract['schema']}`", "", "| ID | Source | Composable | Labels | Existing tests | Missing tests |", "| --- | --- | --- | --- | --- | --- |"]
    for row in cast(list[dict[str, object]], contract["rows"]):
        lines.append(
            "| {id} | {source} | {composable} | {labels} | {existing} | {missing} |".format(
                id=row["id"],
                source=row["source_file"] or "—",
                composable=row["composable"] or "—",
                labels="<br>".join(cast(list[str], row["labels"])) or "—",
                existing="<br>".join(cast(list[str], row["existing_tests"])) or "—",
                missing="<br>".join(cast(list[str], row["missing_tests"])) or "—",
            )
        )
    return "\n".join(lines) + "\n"


def write_outputs(contract: dict[str, object], repo_root: Path, out_json: Path, out_md: Path) -> None:
    json_path = _resolve(repo_root, out_json)
    md_path = _resolve(repo_root, out_md)
    json_path.parent.mkdir(parents=True, exist_ok=True)
    md_path.parent.mkdir(parents=True, exist_ok=True)
    json_path.write_text(json.dumps(contract, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    md_path.write_text(render_markdown(contract), encoding="utf-8")


def _resolve(root: Path, path: Path) -> Path:
    return path if path.is_absolute() else root / path


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Generate Ralph button contract JSON and Markdown from the UI manifest and tests.")
    parser.add_argument("--repo-root", type=Path, default=Path("."))
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--out-json", type=Path, default=DEFAULT_JSON)
    parser.add_argument("--out-md", type=Path, default=DEFAULT_MD)
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    repo_root = args.repo_root.resolve()
    contract = build_contract(repo_root, args.manifest)
    write_outputs(contract, repo_root, args.out_json, args.out_md)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
