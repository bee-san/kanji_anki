from __future__ import annotations

from dataclasses import dataclass
import gzip
from pathlib import Path
import json
from typing import Callable
from urllib.request import Request, urlopen
import xml.etree.ElementTree as ET

from .state import ensure_user_files_dir, packaged_data_dir

KANJIDIC2_FILE_NAME = "kanjidic2.xml"
KANJIVG_DIR_NAME = "kanjivg"
KANJIDIC2_DOWNLOAD_URLS = (
    "https://ftp.monash.edu/pub/nihongo/kanjidic2.xml.gz",
    "ftp://ftp.monash.edu/pub/nihongo/kanjidic2.xml.gz",
    "http://www.csse.monash.edu.au/~jwb/kanjidic2.xml.gz",
)
KANJIVG_SVG_URL_TEMPLATE = (
    "https://raw.githubusercontent.com/KanjiVG/kanjivg/master/kanji/{codepoint}.svg"
)
DOWNLOAD_USER_AGENT = "kanji-anki-study/1.0"
SVG_NS = "http://www.w3.org/2000/svg"
KVG_NS = "http://kanjivg.tagaini.net"
NS = {"svg": SVG_NS, "kvg": KVG_NS}


@dataclass(frozen=True)
class KanjiStudyContent:
    kanji: str
    keyword: str
    meanings: tuple[str, ...]
    readings: tuple[str, ...]
    primary_readings: tuple[str, ...]
    components: tuple[str, ...]
    component_hint: str
    stroke_count: int | None
    stroke_paths: tuple[str, ...]
    dictionary_source: str
    stroke_source: str
    data_warnings: tuple[str, ...] = tuple()

    def to_dict(self) -> dict[str, object]:
        return {
            "kanji": self.kanji,
            "keyword": self.keyword,
            "meanings": list(self.meanings),
            "readings": list(self.readings),
            "primaryReadings": list(self.primary_readings),
            "components": list(self.components),
            "componentHint": self.component_hint,
            "strokeCount": self.stroke_count,
            "strokePaths": list(self.stroke_paths),
            "dictionarySource": self.dictionary_source,
            "strokeSource": self.stroke_source,
            "dataWarnings": list(self.data_warnings),
        }


class StudyContentProvider:
    def __init__(
        self,
        *,
        user_files_dir: Path | None = None,
        url_fetcher: Callable[[str], bytes] | None = None,
        kanjidic_download_urls: tuple[str, ...] = KANJIDIC2_DOWNLOAD_URLS,
        kanjivg_svg_url_template: str = KANJIVG_SVG_URL_TEMPLATE,
    ) -> None:
        self._user_files_dir = user_files_dir or ensure_user_files_dir()
        self._kanjidic_cache: dict[str, dict[str, object]] | None = None
        self._seed_cache: dict[str, dict[str, object]] | None = None
        self._url_fetcher = url_fetcher or _default_url_fetcher
        self._kanjidic_download_urls = kanjidic_download_urls
        self._kanjivg_svg_url_template = kanjivg_svg_url_template
        self._kanjidic_download_attempted = False
        self._kanjivg_download_attempts: set[str] = set()

    def get_content(self, kanji: str) -> KanjiStudyContent:
        seed_entry = self._seed_entry(kanji)
        dictionary_entry = self._kanjidic_entry(kanji)
        stroke_entry = self._kanjivg_entry(kanji)

        meanings = _coerce_string_tuple(
            (dictionary_entry or {}).get("meanings") or (seed_entry or {}).get("meanings")
        )
        keyword = _first_text(
            (dictionary_entry or {}).get("keyword"),
            meanings[0] if meanings else "",
            (seed_entry or {}).get("keyword"),
            "Collection context",
        )
        readings = _coerce_string_tuple(
            (dictionary_entry or {}).get("readings") or (seed_entry or {}).get("readings")
        )
        primary_readings = _coerce_string_tuple(
            (dictionary_entry or {}).get("primary_readings")
            or (seed_entry or {}).get("primaryReadings")
            or readings[:2]
        )
        components = _coerce_string_tuple(
            (stroke_entry or {}).get("components") or (seed_entry or {}).get("components")
        )
        component_hint = _first_text(
            (seed_entry or {}).get("componentHint"),
            " + ".join(components),
            "No cached component breakdown yet.",
        )
        stroke_paths = _coerce_string_tuple(
            (stroke_entry or {}).get("stroke_paths") or (seed_entry or {}).get("strokePaths")
        )
        stroke_count = _coerce_int(
            (dictionary_entry or {}).get("stroke_count"),
            (seed_entry or {}).get("strokeCount"),
            len(stroke_paths) if stroke_paths else None,
        )
        warnings: list[str] = []
        dictionary_source = _first_text(
            (dictionary_entry or {}).get("source"),
            (seed_entry or {}).get("dictionarySource"),
            "fallback",
        )
        stroke_source = _first_text(
            (stroke_entry or {}).get("source"),
            (seed_entry or {}).get("strokeSource"),
            "fallback",
        )
        if not meanings:
            warnings.append("KANJIDIC2 meaning data could not be loaded for this kanji.")
        if not readings:
            warnings.append("Reading data could not be loaded for this kanji.")
        if not stroke_paths:
            warnings.append("KanjiVG stroke data could not be loaded for this kanji.")

        return KanjiStudyContent(
            kanji=kanji,
            keyword=keyword,
            meanings=meanings or ("Collection context",),
            readings=readings,
            primary_readings=primary_readings,
            components=components,
            component_hint=component_hint,
            stroke_count=stroke_count,
            stroke_paths=stroke_paths,
            dictionary_source=dictionary_source,
            stroke_source=stroke_source,
            data_warnings=tuple(warnings),
        )

    def attribution(self) -> dict[str, object]:
        return {
            "dictionary": {
                "name": "KANJIDIC2",
                "source": "server data cache/kanjidic2.xml (auto-cached when absent)",
                "upstream": list(self._kanjidic_download_urls),
                "license": "EDRDG licence required for redistributed data.",
            },
            "strokeData": {
                "name": "KanjiVG",
                "source": "server data cache/kanjivg/ (auto-cached per kanji when absent)",
                "upstream": self._kanjivg_svg_url_template,
                "license": "KanjiVG attribution required for redistributed stroke data.",
            },
        }

    def stroke_svg_path(self, kanji: str) -> Path | None:
        codepoint = format(ord(kanji), "05x")
        cached_path = self._user_files_dir / KANJIVG_DIR_NAME / f"{codepoint}.svg"
        if cached_path.exists():
            return cached_path
        packaged_path = packaged_data_dir() / KANJIVG_DIR_NAME / f"{codepoint}.svg"
        if packaged_path.exists():
            return packaged_path
        self._kanjivg_entry(kanji)
        if cached_path.exists():
            return cached_path
        if packaged_path.exists():
            return packaged_path
        return None

    def _seed_entry(self, kanji: str) -> dict[str, object] | None:
        if self._seed_cache is None:
            self._seed_cache = _load_seed_cache(self._user_files_dir)
        return self._seed_cache.get(kanji)

    def _kanjidic_entry(self, kanji: str) -> dict[str, object] | None:
        if self._kanjidic_cache is None:
            self._kanjidic_cache = _load_kanjidic_cache(self._user_files_dir)
        if not self._kanjidic_cache and not self._kanjidic_download_attempted:
            self._kanjidic_cache = self._download_and_cache_kanjidic()
        return self._kanjidic_cache.get(kanji)

    def _kanjivg_entry(self, kanji: str) -> dict[str, object] | None:
        codepoint = format(ord(kanji), "05x")
        kanjivg_dir = self._user_files_dir / KANJIVG_DIR_NAME
        svg_path = kanjivg_dir / f"{codepoint}.svg"
        entry = _load_kanjivg_entry(svg_path, kanji)
        if entry is not None:
            return entry
        if codepoint in self._kanjivg_download_attempts:
            return None
        self._kanjivg_download_attempts.add(codepoint)
        return self._download_and_cache_kanjivg(codepoint, svg_path, kanji)

    def _download_and_cache_kanjidic(self) -> dict[str, dict[str, object]]:
        self._kanjidic_download_attempted = True
        target_path = self._user_files_dir / KANJIDIC2_FILE_NAME
        for url in self._kanjidic_download_urls:
            try:
                payload = self._url_fetcher(url)
                xml_bytes = _inflate_if_gzip(payload, url=url)
                _write_bytes_atomically(target_path, xml_bytes)
            except (OSError, ValueError):
                continue
            cache = _load_kanjidic_cache(self._user_files_dir)
            if cache:
                return cache
        return self._kanjidic_cache or {}

    def _download_and_cache_kanjivg(
        self,
        codepoint: str,
        svg_path: Path,
        kanji: str,
    ) -> dict[str, object] | None:
        try:
            payload = self._url_fetcher(
                self._kanjivg_svg_url_template.format(codepoint=codepoint)
            )
            svg_path.parent.mkdir(parents=True, exist_ok=True)
            _write_bytes_atomically(svg_path, payload)
        except (OSError, ValueError):
            return None
        return _load_kanjivg_entry(svg_path, kanji)


def _load_seed_cache(user_files_dir: Path) -> dict[str, dict[str, object]]:
    candidates = (
        user_files_dir / "study_content_seed.json",
        packaged_data_dir() / "study_content_seed.json",
    )
    for candidate in candidates:
        try:
            payload = json.loads(candidate.read_text(encoding="utf-8"))
        except (OSError, ValueError):
            continue
        if isinstance(payload, dict):
            return {
                str(key): value
                for key, value in payload.items()
                if isinstance(key, str) and isinstance(value, dict)
            }
    return {}


def _load_kanjidic_cache(user_files_dir: Path) -> dict[str, dict[str, object]]:
    path = user_files_dir / KANJIDIC2_FILE_NAME
    if not path.exists():
        return {}
    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError):
        return {}

    cache: dict[str, dict[str, object]] = {}
    for character in root.findall("character"):
        literal = character.findtext("literal", default="").strip()
        if not literal:
            continue
        meanings: list[str] = []
        on_readings: list[str] = []
        kun_readings: list[str] = []
        for group in character.findall("./reading_meaning/rmgroup"):
            for meaning in group.findall("meaning"):
                if meaning.get("m_lang"):
                    continue
                text = (meaning.text or "").strip()
                if text and text not in meanings:
                    meanings.append(text)
            for reading in group.findall("reading"):
                text = (reading.text or "").strip()
                if not text:
                    continue
                reading_type = reading.get("r_type")
                if reading_type == "ja_on" and text not in on_readings:
                    on_readings.append(text)
                elif reading_type == "ja_kun" and text not in kun_readings:
                    kun_readings.append(text)
        stroke_count = None
        for stroke_text in character.findall("./misc/stroke_count"):
            try:
                stroke_count = int((stroke_text.text or "").strip())
                break
            except ValueError:
                continue
        readings = tuple(on_readings + kun_readings)
        cache[literal] = {
            "keyword": meanings[0] if meanings else "",
            "meanings": tuple(meanings),
            "readings": readings,
            "primary_readings": tuple(readings[:2]),
            "stroke_count": stroke_count,
            "source": path.name,
        }
    return cache


def _load_kanjivg_entry(svg_path: Path, kanji: str) -> dict[str, object] | None:
    if not svg_path.exists():
        return None
    try:
        root = ET.parse(svg_path).getroot()
    except (OSError, ET.ParseError):
        return None

    stroke_paths = tuple(
        path.get("d", "").strip()
        for path in root.findall(".//svg:path", NS)
        if path.get("d")
    )
    components: list[str] = []
    for group in root.findall(".//svg:g", NS):
        element = group.get(f"{{{KVG_NS}}}element")
        if not element or element == kanji or element in components:
            continue
        components.append(element)
    if not stroke_paths and not components:
        return None
    return {
        "stroke_paths": stroke_paths,
        "components": tuple(components),
        "source": str(svg_path.name),
    }


def _write_bytes_atomically(path: Path, payload: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    temp_path = path.with_name(f".{path.name}.tmp")
    temp_path.write_bytes(payload)
    temp_path.replace(path)


def _inflate_if_gzip(payload: bytes, *, url: str) -> bytes:
    if url.endswith(".gz") or payload[:2] == b"\x1f\x8b":
        return gzip.decompress(payload)
    return payload


def _default_url_fetcher(url: str) -> bytes:
    request = Request(url, headers={"User-Agent": DOWNLOAD_USER_AGENT})
    with urlopen(request, timeout=12) as response:
        return response.read()


def _coerce_string_tuple(value: object) -> tuple[str, ...]:
    if value is None:
        return tuple()
    if isinstance(value, str):
        cleaned = value.strip()
        return (cleaned,) if cleaned else tuple()
    if isinstance(value, (list, tuple)):
        items: list[str] = []
        for entry in value:
            cleaned = str(entry).strip()
            if cleaned and cleaned not in items:
                items.append(cleaned)
        return tuple(items)
    cleaned = str(value).strip()
    return (cleaned,) if cleaned else tuple()


def _coerce_int(*values: object) -> int | None:
    for value in values:
        if value is None:
            continue
        try:
            parsed = int(value)
        except (TypeError, ValueError):
            continue
        if parsed > 0:
            return parsed
    return None


def _first_text(*values: object) -> str:
    for value in values:
        if value is None:
            continue
        text = str(value).strip()
        if text:
            return text
    return ""
