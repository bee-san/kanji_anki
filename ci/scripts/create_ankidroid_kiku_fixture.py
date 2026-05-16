#!/usr/bin/env python3
"""Create a tiny deterministic Anki collection for CI provider tests."""

from __future__ import annotations

import json
import sqlite3
import sys
import time
import zlib
from pathlib import Path

FIELD_SEPARATOR = "\x1f"
MODEL_ID = 1700000000000
DECK_ID = 1

FIELDS = [
    "Expression",
    "ExpressionReading",
    "MainDefinition",
    "Sentence",
    "Frequency",
    "FreqSort",
]

NOTES = [
    {
        "id": 1700000000001,
        "guid": "kani-ci-hako",
        "fields": ["箱", "はこ", "box", "箱を開けた。", "100", "100"],
        "tags": " kiku_ci ",
        "card": {
            "id": 1700000001001,
            "queue": -1,
            "type": 2,
            "due": 0,
            "ivl": 42,
            "reps": 80,
            "lapses": 3,
            "data": '{"s":12.5,"d":7.0,"r":0.42}',
        },
    },
    {
        "id": 1700000000002,
        "guid": "kani-ci-hashi",
        "fields": ["橋", "はし", "bridge", "橋を渡る。", "200", "200"],
        "tags": " kiku_ci ",
        "card": {
            "id": 1700000001002,
            "queue": 2,
            "type": 2,
            "due": 1,
            "ivl": 7,
            "reps": 9,
            "lapses": 1,
            "data": '{"s":6.0,"d":5.0,"r":0.80}',
        },
    },
]


def main() -> int:
    if len(sys.argv) != 2:
        print("usage: create_ankidroid_kiku_fixture.py OUTPUT.anki2", file=sys.stderr)
        return 2
    output = Path(sys.argv[1])
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists():
        output.unlink()

    now = int(time.time())
    with sqlite3.connect(output) as db:
        create_schema(db)
        insert_collection_metadata(db, now)
        insert_notes_and_cards(db, now)
        db.commit()
    return 0


def create_schema(db: sqlite3.Connection) -> None:
    db.executescript(
        """
        PRAGMA user_version = 11;
        CREATE TABLE col (
            id integer primary key,
            crt integer not null,
            mod integer not null,
            scm integer not null,
            ver integer not null,
            dty integer not null,
            usn integer not null,
            ls integer not null,
            conf text not null,
            models text not null,
            decks text not null,
            dconf text not null,
            tags text not null
        );
        CREATE TABLE notes (
            id integer primary key,
            guid text not null,
            mid integer not null,
            mod integer not null,
            usn integer not null,
            tags text not null,
            flds text not null,
            sfld integer not null,
            csum integer not null,
            flags integer not null,
            data text not null
        );
        CREATE TABLE cards (
            id integer primary key,
            nid integer not null,
            did integer not null,
            ord integer not null,
            mod integer not null,
            usn integer not null,
            type integer not null,
            queue integer not null,
            due integer not null,
            ivl integer not null,
            factor integer not null,
            reps integer not null,
            lapses integer not null,
            left integer not null,
            odue integer not null,
            odid integer not null,
            flags integer not null,
            data text not null
        );
        CREATE TABLE revlog (
            id integer primary key,
            cid integer not null,
            usn integer not null,
            ease integer not null,
            ivl integer not null,
            lastIvl integer not null,
            factor integer not null,
            time integer not null,
            type integer not null
        );
        CREATE TABLE graves (
            usn integer not null,
            oid integer not null,
            type integer not null
        );
        CREATE INDEX ix_notes_usn on notes (usn);
        CREATE INDEX ix_cards_usn on cards (usn);
        CREATE INDEX ix_revlog_usn on revlog (usn);
        CREATE INDEX ix_cards_nid on cards (nid);
        CREATE INDEX ix_cards_sched on cards (did, queue, due);
        CREATE INDEX ix_revlog_cid on revlog (cid);
        CREATE INDEX ix_notes_csum on notes (csum);
        """
    )


def insert_collection_metadata(db: sqlite3.Connection, now: int) -> None:
    model = {
        "id": MODEL_ID,
        "name": "Kiku",
        "type": 0,
        "mod": now,
        "usn": 0,
        "sortf": 0,
        "did": DECK_ID,
        "flds": [
            {
                "name": name,
                "ord": index,
                "sticky": False,
                "rtl": False,
                "font": "Arial",
                "size": 20,
                "description": "",
                "plainText": False,
                "collapsed": False,
                "excludeFromSearch": False,
                "preventDeletion": False,
                "tag": None,
            }
            for index, name in enumerate(FIELDS)
        ],
        "tmpls": [
            {
                "name": "Mining",
                "ord": 0,
                "qfmt": "{{Expression}}",
                "afmt": "{{FrontSide}}<hr id=answer>{{MainDefinition}}",
                "bqfmt": "",
                "bafmt": "",
                "did": None,
                "bfont": "Arial",
                "bsize": 20,
            }
        ],
        "css": ".card { font-family: arial; font-size: 20px; text-align: center; }",
        "latexPre": "\\documentclass[12pt]{article}",
        "latexPost": "\\end{document}",
        "req": [[0, "any", [0]]],
    }
    deck = {
        "id": DECK_ID,
        "name": "Default",
        "mod": now,
        "usn": 0,
        "lrnToday": [0, 0],
        "revToday": [0, 0],
        "newToday": [0, 0],
        "timeToday": [0, 0],
        "collapsed": False,
        "browserCollapsed": False,
        "desc": "CI fixture deck",
        "dyn": 0,
        "conf": 1,
        "extendNew": 0,
        "extendRev": 0,
    }
    dconf = {
        "id": 1,
        "name": "Default",
        "mod": now,
        "usn": 0,
        "maxTaken": 60,
        "autoplay": True,
        "timer": 0,
        "replayq": True,
        "new": {"delays": [1, 10], "ints": [1, 4, 7], "initialFactor": 2500, "perDay": 20, "bury": True},
        "rev": {"perDay": 200, "ease4": 1.3, "fuzz": 0.05, "minSpace": 1, "ivlFct": 1, "maxIvl": 36500, "bury": True},
        "lapse": {"delays": [10], "mult": 0, "minInt": 1, "leechFails": 8, "leechAction": 0},
    }
    conf = {"nextPos": 1, "estTimes": True, "activeDecks": [DECK_ID], "curDeck": DECK_ID}

    db.execute(
        "INSERT INTO col VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        (
            1,
            now,
            now * 1000,
            now * 1000,
            11,
            0,
            0,
            0,
            json.dumps(conf, separators=(",", ":")),
            json.dumps({str(MODEL_ID): model}, separators=(",", ":"), ensure_ascii=False),
            json.dumps({str(DECK_ID): deck}, separators=(",", ":"), ensure_ascii=False),
            json.dumps({"1": dconf}, separators=(",", ":"), ensure_ascii=False),
            json.dumps({}, separators=(",", ":")),
        ),
    )


def insert_notes_and_cards(db: sqlite3.Connection, now: int) -> None:
    for note in NOTES:
        field_text = FIELD_SEPARATOR.join(note["fields"])
        sort_field = note["fields"][0]
        db.execute(
            "INSERT INTO notes VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (
                note["id"],
                note["guid"],
                MODEL_ID,
                now,
                0,
                note["tags"],
                field_text,
                sort_field,
                zlib.crc32(sort_field.encode("utf-8")) & 0xFFFFFFFF,
                0,
                "",
            ),
        )
        card = note["card"]
        db.execute(
            "INSERT INTO cards VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            (
                card["id"],
                note["id"],
                DECK_ID,
                0,
                now,
                0,
                card["type"],
                card["queue"],
                card["due"],
                card["ivl"],
                2500,
                card["reps"],
                card["lapses"],
                0,
                0,
                0,
                0,
                card["data"],
            ),
        )


if __name__ == "__main__":
    raise SystemExit(main())
