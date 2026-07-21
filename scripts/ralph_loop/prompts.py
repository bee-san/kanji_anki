#!/usr/bin/env python3

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from pathlib import Path

_PROMPT_DIR = Path("scripts/prompts")
_PLACEHOLDER_RE = re.compile(r"{{\s*([a-zA-Z_][a-zA-Z0-9_]*)\s*}}")
_FRONT_MATTER_RE = re.compile(r"\A---\n(?P<header>.*?)\n---\n(?P<body>.*)\Z", re.DOTALL)


@dataclass(frozen=True)
class ProjectPrompt:
    name: str
    template: str
    output_schema: dict[str, object]

    @property
    def placeholders(self) -> set[str]:
        return set(_PLACEHOLDER_RE.findall(self.template))

    def render(self, **context: str) -> str:
        if not self.output_schema:
            raise ValueError(f"{self.name} is missing output_schema_json front matter")
        placeholders = self.placeholders
        missing = placeholders.difference(context)
        if missing:
            missing_list = ", ".join(sorted(missing))
            raise KeyError(f"{self.name} missing template values: {missing_list}")
        unknown = set(context).difference(placeholders)
        if unknown:
            unknown_list = ", ".join(sorted(unknown))
            raise KeyError(f"{self.name} received unused template values: {unknown_list}")

        def replace(match: re.Match[str]) -> str:
            return context[match.group(1)]

        return _PLACEHOLDER_RE.sub(replace, self.template)


def load_project_prompt(repo_root: Path, prompt_name: str) -> ProjectPrompt:
    return load_prompt(repo_root / _PROMPT_DIR / prompt_name)


def load_prompt(path: Path) -> ProjectPrompt:
    text = path.read_text(encoding="utf-8")
    match = _FRONT_MATTER_RE.match(text)
    if not match:
        raise ValueError(f"{path.name} is missing YAML-style front matter")
    header = _parse_front_matter(match.group("header"))
    schema_text = header.get("output_schema_json")
    if schema_text is None:
        raise ValueError(f"{path.name} is missing output_schema_json front matter")
    try:
        schema = json.loads(schema_text)
    except json.JSONDecodeError as exc:
        raise ValueError(f"{path.name} output_schema_json is not valid JSON") from exc
    if not isinstance(schema, dict):
        raise ValueError(f"{path.name} output_schema_json must be a JSON object")
    return ProjectPrompt(path.name, match.group("body"), schema)


def _parse_front_matter(header: str) -> dict[str, str]:
    parsed: dict[str, str] = {}
    for raw_line in header.splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        if ":" not in line:
            raise ValueError(f"invalid front matter line: {raw_line}")
        key, value = line.split(":", 1)
        parsed[key.strip()] = _unquote(value.strip())
    return parsed


def _unquote(value: str) -> str:
    if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
        return value[1:-1]
    return value
