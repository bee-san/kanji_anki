from __future__ import annotations

import re
import unicodedata

KANJI_RE = re.compile(r"[\u3400-\u4DBF\u4E00-\u9FFF\uF900-\uFAFF]")
HTML_RE = re.compile(r"<[^>]+>")
WHITESPACE_RE = re.compile(r"\s+")


def normalize_lookup_text(text: str) -> str:
    normalized = unicodedata.normalize("NFKC", text or "")
    normalized = WHITESPACE_RE.sub(" ", normalized)
    return normalized.strip()


def extract_kanji_chars(text: str) -> list[str]:
    seen: set[str] = set()
    chars: list[str] = []
    for char in normalize_lookup_text(strip_html_text(text)):
        if not KANJI_RE.fullmatch(char):
            continue
        if char in seen:
            continue
        seen.add(char)
        chars.append(char)
    return chars


def strip_html_text(text: str) -> str:
    try:
        from anki.utils import strip_html
    except ModuleNotFoundError:
        return HTML_RE.sub("", text or "")
    try:
        return strip_html(text or "")
    except AttributeError:
        return HTML_RE.sub("", text or "")
