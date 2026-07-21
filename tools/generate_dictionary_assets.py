#!/usr/bin/env python3
"""Generate the bundled offline kanji dictionary SQLite database."""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import re
import sqlite3
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import date
from pathlib import Path
from typing import Iterable


LIST_SEPARATOR = "\x1f"
SCHEMA_VERSION = "1"
XML_LANG = "{http://www.w3.org/XML/1998/namespace}lang"
EDRDG_LICENSE = "CC BY-SA 4.0 via EDRDG licence"
KANJIDIC2_URL = "https://www.edrdg.org/kanjidic/kanjidic2.xml.gz"
EDRDG_LICENSE_URL = "https://www.edrdg.org/edrdg/licence.html"
KANJIVG_URL = "https://github.com/KanjiVG/kanjivg"
DB_ASSET_NAME = "kanji_dictionary.db"
DB_SHA256_ASSET_NAME = "kanji_dictionary.db.sha256"
MANIFEST_ASSET_NAME = "dictionary_sources.json"
INT32_MIN = -(2**31)
INT32_MAX = 2**31 - 1
INTEGER_PATTERN = re.compile(r"-?[0-9]+")
KANJI_RANGES = (
    (0x3400, 0x4DBF),
    (0x4E00, 0x9FFF),
    (0xF900, 0xFAFF),
    (0x20000, 0x2FA1F),
    (0x30000, 0x3134F),
)


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
    kanjidic_frequency: int


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
            if "m_lang" not in meaning.attrib and meaning.get(XML_LANG, "eng") in ("", "eng")
        )
        if not is_kanji_literal(literal):
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
    return metadata, sorted(records, key=lambda item: ord(item.literal))


def parse_jiten_ranks(path: Path) -> dict[str, int]:
    ranks: dict[str, int] = {}
    with path.open("r", encoding="utf-8-sig", newline="") as source:
        for line in source:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            cells = [cell.strip() for cell in re.split(r"[,\t]", line)]
            if len(cells) < 2:
                continue
            kanji = ""
            rank = parse_integer(cells[0])
            if rank is not None:
                kanji = cells[1]
            else:
                rank = parse_integer(cells[1])
                kanji = cells[0]
            if rank is not None and is_kanji_literal(kanji):
                ranks[kanji] = rank
    return ranks


def text(elem: ET.Element | None, path: str) -> str:
    if elem is None:
        return ""
    found = elem.find(path)
    return clean(found.text if found is not None else "")


def int_or_zero(value: str) -> int:
    parsed = parse_integer(value)
    return parsed if parsed is not None and parsed >= 0 else 0


def parse_integer(value: str) -> int | None:
    if not INTEGER_PATTERN.fullmatch(value):
        return None
    parsed = int(value)
    return parsed if INT32_MIN <= parsed <= INT32_MAX else None


def is_kanji_literal(value: str) -> bool:
    if len(value) != 1:
        return False
    codepoint = ord(value)
    return any(start <= codepoint <= end for start, end in KANJI_RANGES)


def list_cell(values: Iterable[str]) -> str:
    return LIST_SEPARATOR.join(clean(value) for value in values if clean(value))


def write_database(
    records: list[KanjiRecord],
    jiten_ranks: dict[str, int],
    output: Path,
    fetch_date: str,
    kanjidic_path: Path,
    jiten_path: Path,
    kanjidic_metadata: dict[str, str],
) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists():
        output.unlink()
    kanjidic_sha = sha256(kanjidic_path)
    jiten_sha = sha256(jiten_path)
    connection = sqlite3.connect(output)
    try:
        connection.execute("PRAGMA journal_mode=OFF")
        connection.execute("PRAGMA synchronous=OFF")
        connection.execute("PRAGMA locking_mode=EXCLUSIVE")
        connection.execute("PRAGMA foreign_keys=OFF")
        connection.execute("CREATE TABLE kanji (literal TEXT PRIMARY KEY, meanings TEXT NOT NULL, on_readings TEXT NOT NULL, kun_readings TEXT NOT NULL, nanori_readings TEXT NOT NULL, stroke_count INTEGER NOT NULL, grade INTEGER NOT NULL, radical INTEGER NOT NULL, kanjidic_frequency INTEGER NOT NULL, jiten_rank INTEGER)")
        connection.execute("CREATE TABLE jiten_ranks (literal TEXT PRIMARY KEY, rank INTEGER NOT NULL)")
        connection.execute("CREATE TABLE dictionary_meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        connection.executemany(
            "INSERT INTO kanji (literal, meanings, on_readings, kun_readings, nanori_readings, stroke_count, grade, radical, kanjidic_frequency, jiten_rank) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            [
                (
                    record.literal,
                    list_cell(record.meanings),
                    list_cell(record.on_readings),
                    list_cell(record.kun_readings),
                    list_cell(record.nanori_readings),
                    record.stroke_count,
                    record.grade,
                    record.radical,
                    record.kanjidic_frequency,
                    jiten_ranks.get(record.literal),
                )
                for record in records
            ],
        )
        connection.executemany(
            "INSERT INTO jiten_ranks (literal, rank) VALUES (?, ?)",
            sorted(jiten_ranks.items(), key=lambda item: (item[1], item[0])),
        )
        meta = [
            ("schema_version", SCHEMA_VERSION),
            ("generated_at", fetch_date),
            ("generator", "tools/generate_dictionary_assets.py"),
            ("list_separator", "U+001F"),
            ("kanjidic2_upstream_url", KANJIDIC2_URL),
            ("kanjidic2_source_sha256", kanjidic_sha),
            ("kanjidic2_file_version", kanjidic_metadata.get("file_version", "")),
            ("kanjidic2_database_version", kanjidic_metadata.get("database_version", "")),
            ("kanjidic2_date_of_creation", kanjidic_metadata.get("date_of_creation", "")),
            ("jiten_rank_source_path", str(jiten_path)),
            ("jiten_rank_source_sha256", jiten_sha),
            ("kanji_record_count", str(len(records))),
            ("jiten_rank_count", str(len(jiten_ranks))),
            ("jiten_rank_join_count", str(sum(1 for record in records if record.literal in jiten_ranks))),
            ("skip_codes_imported", "false"),
        ]
        connection.executemany("INSERT INTO dictionary_meta (key, value) VALUES (?, ?)", meta)
        connection.commit()
    finally:
        connection.close()


def write_sha256_file(output: Path, target: Path) -> None:
    output.write_text(f"{sha256(target)}  {target.name}\n", encoding="utf-8")


def write_manifest(
    output: Path,
    fetch_date: str,
    kanjidic_path: Path,
    jiten_path: Path,
    db_asset: Path,
    checksum_asset: Path,
    kanji_count: int,
    jiten_rank_count: int,
    jiten_rank_join_count: int,
    kanjidic_metadata: dict[str, str],
) -> None:
    output.parent.mkdir(parents=True, exist_ok=True)
    manifest = {
        "generated_at": fetch_date,
        "generated_by": "tools/generate_dictionary_assets.py",
        "schema_version": SCHEMA_VERSION,
        "update_package": [
            DB_ASSET_NAME,
            MANIFEST_ASSET_NAME,
            DB_SHA256_ASSET_NAME,
        ],
        "assets": [
            {
                "path": db_asset.name,
                "sha256": sha256(db_asset),
                "records": kanji_count,
                "jiten_rank_records": jiten_rank_count,
                "jiten_rank_join_records": jiten_rank_join_count,
            },
            {
                "path": checksum_asset.name,
                "sha256": sha256(checksum_asset),
            },
        ],
        "sources": [
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
                "fields_imported": ["literal", "English meanings", "on/kun/nanori readings", "stroke count", "grade", "radical", "KANJIDIC frequency"],
            },
            {
                "id": "jiten_kanji_rank",
                "name": "Jiten kanji frequency ranks",
                "source_path": str(jiten_path),
                "fetch_date": fetch_date,
                "source_sha256": sha256(jiten_path),
                "fields_imported": ["literal", "frequency rank"],
            },
            {
                "id": "kanjivg",
                "name": "KanjiVG",
                "upstream_url": KANJIVG_URL,
                "license": "CC BY-SA 3.0",
                "fields_imported": ["stroke path points"],
            },
        ],
        "notes": [
            "SKIP query codes are intentionally excluded because EDRDG documents separate SKIP licensing conditions.",
            "Word-level dictionary data is not bundled. Study From lines come from Anki examples only.",
            "Dictionary updates must provide kanji_dictionary.db, dictionary_sources.json, and kanji_dictionary.db.sha256.",
            "Refresh by downloading the current KANJIDIC2 XML export and rerunning the generator command in documentation/dictionary_sources.md.",
        ],
    }
    output.write_text(json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser()
    parser.add_argument("--kanjidic2", type=Path, required=True)
    parser.add_argument("--jiten-ranks", type=Path, default=Path("tools/data/jiten_kanji_rank.csv"))
    parser.add_argument("--output-dir", type=Path, default=Path("app/src/main/assets/dictionaries"))
    parser.add_argument("--fetch-date", default=date.today().isoformat())
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    kanjidic_metadata, kanji = parse_kanjidic2(args.kanjidic2)
    ranks = parse_jiten_ranks(args.jiten_ranks)
    db_asset = args.output_dir / DB_ASSET_NAME
    checksum_asset = args.output_dir / DB_SHA256_ASSET_NAME
    manifest_asset = args.output_dir / MANIFEST_ASSET_NAME
    write_database(
        kanji,
        ranks,
        db_asset,
        args.fetch_date,
        args.kanjidic2,
        args.jiten_ranks,
        kanjidic_metadata,
    )
    write_sha256_file(checksum_asset, db_asset)
    write_manifest(
        manifest_asset,
        args.fetch_date,
        args.kanjidic2,
        args.jiten_ranks,
        db_asset,
        checksum_asset,
        len(kanji),
        len(ranks),
        sum(1 for record in kanji if record.literal in ranks),
        kanjidic_metadata,
    )
    print(f"Wrote {len(kanji)} KANJIDIC2 kanji rows with {len(ranks)} Jiten ranks into {db_asset}")


if __name__ == "__main__":
    main()
