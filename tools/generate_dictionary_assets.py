#!/usr/bin/env python3
"""Generate compact offline dictionary assets from JMdict_e and KANJIDIC2 XML."""

from __future__ import annotations

import argparse
import gzip
import hashlib
import io
import json
import re
import xml.etree.ElementTree as ET
from contextlib import contextmanager
from dataclasses import dataclass
from datetime import date
from pathlib import Path
from typing import Iterable


LIST_SEPARATOR = "\x1f"
XML_LANG = "{http://www.w3.org/XML/1998/namespace}lang"
EDRDG_LICENSE = "CC BY-SA 4.0 via EDRDG licence"
JMDICT_URL = "http://ftp.edrdg.org/pub/Nihongo/JMdict_e.gz"
KANJIDIC2_URL = "https://www.edrdg.org/kanjidic/kanjidic2.xml.gz"
EDRDG_LICENSE_URL = "https://www.edrdg.org/edrdg/licence.html"


@dataclass(frozen=True)
class WordRecord:
    expression: str
    reading: str
    glosses: tuple[str, ...]
    pos: tuple[str, ...]
    priority: tuple[str, ...]
    commonness: int


@dataclass(frozen=True)
class KanjiRecord:
    literal: str
    meanings: tuple[str, ...]
    on_readings: tuple[str, ...]
    kun_readings: tuple[str, ...]
    nanori_readings: tuple[str, ...]
    stroke_count: int
    grade: int
    radical: int
    frequency: int


def source_opener(path: Path):
    return gzip.open if path.suffix == ".gz" else open


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def clean(value: str | None) -> str:
    return re.sub(r"\s+", " ", (value or "").replace("\t", " ").replace(LIST_SEPARATOR, " ")).strip()


def unique(values: Iterable[str]) -> tuple[str, ...]:
    out: list[str] = []
    for value in values:
        cleaned = clean(value)
        if cleaned and cleaned not in out:
            out.append(cleaned)
    return tuple(out)


def commonness(priorities: Iterable[str]) -> int:
    score = 999
    for priority in priorities:
        if priority.startswith(("ichi", "news", "spec", "gai")):
            score = min(score, 1)
        elif priority.startswith("nf"):
            try:
                score = min(score, int(priority[2:]))
            except ValueError:
                score = min(score, 50)
    return score


def better_word(left: WordRecord, right: WordRecord) -> WordRecord:
    left_key = (left.commonness, -len(left.glosses), left.expression, left.reading)
    right_key = (right.commonness, -len(right.glosses), right.expression, right.reading)
    return left if left_key <= right_key else right


def parse_jmdict(path: Path) -> list[WordRecord]:
    records: dict[tuple[str, str], WordRecord] = {}
    opener = source_opener(path)
    with opener(path, "rb") as source:
        for _event, elem in ET.iterparse(source, events=("end",)):
            if elem.tag != "entry":
                continue
            kanji_forms = [clean(node.text) for node in elem.findall("k_ele/keb")]
            kanji_priorities = [clean(node.text) for node in elem.findall("k_ele/ke_pri")]
            readings = [clean(node.text) for node in elem.findall("r_ele/reb")]
            reading_priorities = [clean(node.text) for node in elem.findall("r_ele/re_pri")]
            first_sense = elem.find("sense")
            if first_sense is None:
                elem.clear()
                continue
            glosses = unique(
                gloss.text
                for gloss in first_sense.findall("gloss")
                if gloss.get(XML_LANG, "eng") in ("", "eng")
            )
            if not glosses:
                elem.clear()
                continue
            pos = unique(node.text for node in first_sense.findall("pos"))
            priorities = unique(kanji_priorities + reading_priorities)
            expressions = unique(kanji_forms if kanji_forms else readings)
            for expression in expressions:
                for reading in unique(readings):
                    if not expression or not reading:
                        continue
                    record = WordRecord(
                        expression,
                        reading,
                        glosses,
                        pos,
                        priorities,
                        commonness(priorities),
                    )
                    key = (expression, reading)
                    previous = records.get(key)
                    records[key] = record if previous is None else better_word(previous, record)
            elem.clear()
    return sorted(records.values(), key=lambda item: (item.expression, item.reading, item.commonness))


def parse_kanjidic2(path: Path) -> tuple[dict[str, str], list[KanjiRecord]]:
    opener = source_opener(path)
    with opener(path, "rb") as source:
        root = ET.parse(source).getroot()
    header = root.find("header")
    metadata = {
        "file_version": text(header, "file_version"),
        "database_version": text(header, "database_version"),
        "date_of_creation": text(header, "date_of_creation"),
    }
    records: list[KanjiRecord] = []
    for character in root.findall("character"):
        literal = text(character, "literal")
        meanings = unique(
            meaning.text
            for meaning in character.findall("reading_meaning/rmgroup/meaning")
            if "m_lang" not in meaning.attrib
        )
        if not literal:
            continue
        records.append(
            KanjiRecord(
                literal,
                meanings,
                unique(node.text for node in character.findall("reading_meaning/rmgroup/reading[@r_type='ja_on']")),
                unique(node.text for node in character.findall("reading_meaning/rmgroup/reading[@r_type='ja_kun']")),
                unique(node.text for node in character.findall("reading_meaning/nanori")),
                int_or_zero(text(character, "misc/stroke_count")),
                int_or_zero(text(character, "misc/grade")),
                int_or_zero(text(character, "radical/rad_value[@rad_type='classical']")),
                int_or_zero(text(character, "misc/freq")),
            )
        )
    return metadata, sorted(records, key=lambda item: ord(item.literal[0]))


def text(elem: ET.Element | None, path: str) -> str:
    if elem is None:
        return ""
    found = elem.find(path)
    return clean(found.text if found is not None else "")


def int_or_zero(value: str) -> int:
    try:
        return int(value)
    except ValueError:
        return 0


def list_cell(values: Iterable[str]) -> str:
    return LIST_SEPARATOR.join(clean(value) for value in values if clean(value))


@contextmanager
def gzip_text_writer(output: Path):
    with output.open("wb") as raw:
        with gzip.GzipFile(filename="", mode="wb", fileobj=raw, mtime=0) as gz:
            with io.TextIOWrapper(gz, encoding="utf-8", newline="\n") as target:
                yield target


def write_words(records: list[WordRecord], output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    with gzip_text_writer(output) as target:
        target.write("# Generated by tools/generate_dictionary_assets.py\n")
        target.write("expression\treading\tglosses\tpos\tpriority\tcommonness\n")
        for record in records:
            target.write(
                "\t".join(
                    [
                        record.expression,
                        record.reading,
                        list_cell(record.glosses),
                        list_cell(record.pos),
                        list_cell(record.priority),
                        str(record.commonness),
                    ]
                )
                + "\n"
            )


def write_kanji(records: list[KanjiRecord], output: Path) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    with gzip_text_writer(output) as target:
        target.write("# Generated by tools/generate_dictionary_assets.py\n")
        target.write("literal\tmeanings\ton_readings\tkun_readings\tnanori_readings\tstroke_count\tgrade\tradical\tfrequency\n")
        for record in records:
            target.write(
                "\t".join(
                    [
                        record.literal,
                        list_cell(record.meanings),
                        list_cell(record.on_readings),
                        list_cell(record.kun_readings),
                        list_cell(record.nanori_readings),
                        str(record.stroke_count),
                        str(record.grade),
                        str(record.radical),
                        str(record.frequency),
                    ]
                )
                + "\n"
            )


def write_manifest(
    output: Path,
    fetch_date: str,
    jmdict_path: Path,
    kanjidic_path: Path,
    words_asset: Path,
    kanji_asset: Path,
    word_count: int,
    kanji_count: int,
    kanjidic_metadata: dict[str, str],
) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    manifest = {
        "generated_at": fetch_date,
        "generated_by": "tools/generate_dictionary_assets.py",
        "assets": [
            {
                "path": words_asset.name,
                "sha256": sha256(words_asset),
                "records": word_count,
            },
            {
                "path": kanji_asset.name,
                "sha256": sha256(kanji_asset),
                "records": kanji_count,
            },
        ],
        "sources": [
            {
                "id": "jmdict_e",
                "name": "JMdict_e",
                "upstream_url": JMDICT_URL,
                "fetch_date": fetch_date,
                "source_sha256": sha256(jmdict_path),
                "version": "daily generated JMdict_e XML export",
                "license": EDRDG_LICENSE,
                "license_url": EDRDG_LICENSE_URL,
                "fields_imported": ["expression", "reading", "English glosses", "part-of-speech tags", "priority/commonness"],
            },
            {
                "id": "kanjidic2",
                "name": "KANJIDIC2",
                "upstream_url": KANJIDIC2_URL,
                "fetch_date": fetch_date,
                "source_sha256": sha256(kanjidic_path),
                "file_version": kanjidic_metadata.get("file_version", ""),
                "database_version": kanjidic_metadata.get("database_version", ""),
                "date_of_creation": kanjidic_metadata.get("date_of_creation", ""),
                "license": EDRDG_LICENSE,
                "license_url": EDRDG_LICENSE_URL,
                "fields_imported": ["literal", "English meanings", "on/kun/nanori readings", "stroke count", "grade", "radical", "frequency"],
            },
            {
                "id": "kanjivg",
                "name": "KanjiVG",
                "upstream_url": "https://github.com/KanjiVG/kanjivg",
                "license": "CC BY-SA 3.0",
                "fields_imported": ["stroke path points"],
            },
        ],
        "notes": [
            "SKIP query codes are intentionally excluded because EDRDG documents separate SKIP licensing conditions.",
            "Refresh by downloading the current source XML exports and rerunning the generator command in documentation/dictionary_sources.md.",
        ],
    }
    output.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--jmdict", type=Path, required=True)
    parser.add_argument("--kanjidic2", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, default=Path("app/src/main/assets/dictionaries"))
    parser.add_argument("--fetch-date", default=date.today().isoformat())
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    words = parse_jmdict(args.jmdict)
    kanjidic_metadata, kanji = parse_kanjidic2(args.kanjidic2)
    words_asset = args.output_dir / "jmdict_e_words.tsv.gz"
    kanji_asset = args.output_dir / "kanjidic2_kanji.tsv.gz"
    manifest_asset = args.output_dir / "dictionary_sources.json"
    write_words(words, words_asset)
    write_kanji(kanji, kanji_asset)
    write_manifest(
        manifest_asset,
        args.fetch_date,
        args.jmdict,
        args.kanjidic2,
        words_asset,
        kanji_asset,
        len(words),
        len(kanji),
        kanjidic_metadata,
    )
    print(f"Wrote {len(words)} JMdict_e word rows and {len(kanji)} KANJIDIC2 kanji rows to {args.output_dir}")


if __name__ == "__main__":
    main()
