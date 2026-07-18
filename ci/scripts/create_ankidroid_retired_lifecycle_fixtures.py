#!/usr/bin/env python3
"""Create sanitized staged AnkiDroid fixtures for retired-item lifecycle tests."""

from __future__ import annotations

import hashlib
import json
import shutil
import sqlite3
import subprocess
import sys
from pathlib import Path


FIELD_SEPARATOR = "\x1f"
MODEL_ID = 1_700_000_000_000
NOTE_SUSPENDED = 1_700_000_000_001
NOTE_PRIMARY = 1_700_000_000_002
NOTE_SECOND_SUPPORT = 1_700_000_000_003
NOTE_SENTINEL = 1_700_000_000_004
CARD_PRIMARY = 1_700_000_001_002
CARD_SIBLING = 1_700_000_001_102
TARGET_NOTE_IDS = (NOTE_SUSPENDED, NOTE_PRIMARY, NOTE_SECOND_SUPPORT)


def main() -> int:
    if len(sys.argv) != 2:
        print(
            "usage: create_ankidroid_retired_lifecycle_fixtures.py OUTPUT_DIR",
            file=sys.stderr,
        )
        return 2

    output_dir = Path(sys.argv[1]).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    generator = Path(__file__).with_name("create_ankidroid_kiku_fixture.py")
    base = output_dir / "base.anki2"
    subprocess.run([sys.executable, str(generator), str(base)], check=True)

    stage_paths = {
        "weak_below_threshold": build_supported_stage(output_dir, base, "weak_below_threshold", 20),
        "mature_at_threshold": build_supported_stage(output_dir, base, "mature_at_threshold", 21),
        "missing_route": build_missing_route_stage(output_dir, base),
        "invalid_ord1": build_invalid_ord1_stage(output_dir, base),
    }
    manifest = {
        "target_kanji": "橋",
        "mature_days": 21,
        "mature_support_threshold": 2,
        "stages": {
            name: {
                "file": path.name,
                "sha256": sha256(path),
            }
            for name, path in stage_paths.items()
        },
    }
    manifest_path = output_dir / "manifest.json"
    manifest_path.write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    print(manifest_path)
    return 0


def build_supported_stage(
    output_dir: Path,
    base: Path,
    name: str,
    primary_interval: int,
) -> Path:
    target = copy_base(output_dir, base, name)
    with sqlite3.connect(target) as db:
        configure_target_notes(db)
        db.execute(
            "UPDATE cards SET ivl=?, data=? WHERE id=?",
            (
                primary_interval,
                fsrs_payload(float(primary_interval), 5.0, 0.80),
                CARD_PRIMARY,
            ),
        )
        assert_integrity(db, name)
    return target


def build_missing_route_stage(output_dir: Path, base: Path) -> Path:
    name = "missing_route"
    target = copy_base(output_dir, base, name)
    with sqlite3.connect(target) as db:
        placeholders = ",".join("?" for _ in TARGET_NOTE_IDS)
        db.execute(f"DELETE FROM cards WHERE nid IN ({placeholders})", TARGET_NOTE_IDS)
        db.execute(f"DELETE FROM notes WHERE id IN ({placeholders})", TARGET_NOTE_IDS)
        remaining_note_ids = [row[0] for row in db.execute("SELECT id FROM notes ORDER BY id")]
        if remaining_note_ids != [NOTE_SENTINEL]:
            raise RuntimeError(f"{name}: unexpected remaining notes {remaining_note_ids}")
        assert_integrity(db, name)
    return target


def build_invalid_ord1_stage(output_dir: Path, base: Path) -> Path:
    name = "invalid_ord1"
    target = build_supported_stage(output_dir, base, name, 20)
    with sqlite3.connect(target) as db:
        models = json.loads(db.execute("SELECT models FROM col").fetchone()[0])
        model = models[str(MODEL_ID)]
        sibling_template = dict(model["tmpls"][0])
        sibling_template["name"] = "Unsupported sibling"
        sibling_template["ord"] = 1
        model["tmpls"].append(sibling_template)
        db.execute(
            "UPDATE col SET models=?",
            (json.dumps(models, ensure_ascii=False, separators=(",", ":")),),
        )

        card = db.execute(
            "SELECT nid,did,mod,usn,type,queue,due,ivl,factor,reps,lapses,left,odue,odid,flags,data "
            "FROM cards WHERE id=?",
            (CARD_PRIMARY,),
        ).fetchone()
        if card is None:
            raise RuntimeError(f"{name}: primary card missing")
        values = list(card)
        values[6] = 8
        values[7] = 7
        values[9] = 9
        values[10] = 1
        values[15] = fsrs_payload(7.0, 4.0, 0.90)
        db.execute(
            "INSERT INTO cards "
            "(id,nid,did,ord,mod,usn,type,queue,due,ivl,factor,reps,lapses,left,odue,odid,flags,data) "
            "VALUES (?,?,?,1,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            (CARD_SIBLING, *values),
        )
        assert_integrity(db, name)
    return target


def copy_base(output_dir: Path, base: Path, name: str) -> Path:
    target = output_dir / f"{name}.anki2"
    shutil.copy2(base, target)
    return target


def configure_target_notes(db: sqlite3.Connection) -> None:
    update_note(db, NOTE_SUSPENDED, "橋箱", "はしばこ", "bridge box", "橋箱を開けた。", 100)
    update_note(db, NOTE_PRIMARY, "橋", "はし", "bridge", "橋を渡る。", 200)
    update_note(db, NOTE_SECOND_SUPPORT, "橋本", "はしもと", "bridge", "橋本さんに会う。", 300)


def update_note(
    db: sqlite3.Connection,
    note_id: int,
    expression: str,
    reading: str,
    meaning: str,
    sentence: str,
    frequency: int,
) -> None:
    fields = FIELD_SEPARATOR.join(
        (expression, reading, meaning, sentence, str(frequency), str(frequency))
    )
    db.execute(
        "UPDATE notes SET flds=?, sfld=?, csum=? WHERE id=?",
        (fields, expression, anki_checksum(expression), note_id),
    )


def anki_checksum(value: str) -> int:
    return int(hashlib.sha1(value.encode("utf-8")).hexdigest()[:8], 16)


def fsrs_payload(stability: float, difficulty: float, retrievability: float) -> str:
    return json.dumps(
        {"s": stability, "d": difficulty, "r": retrievability},
        separators=(",", ":"),
    )


def assert_integrity(db: sqlite3.Connection, stage: str) -> None:
    result = db.execute("PRAGMA integrity_check").fetchone()[0]
    if result != "ok":
        raise RuntimeError(f"{stage}: integrity_check={result}")


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


if __name__ == "__main__":
    raise SystemExit(main())
