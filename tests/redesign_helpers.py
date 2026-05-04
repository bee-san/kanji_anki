from __future__ import annotations

from dataclasses import dataclass
import gzip
from pathlib import Path

from kanji_leech_dashboard.ankiconnect import CardSnapshot, CollectionSnapshot, NoteSnapshot
from kanji_leech_dashboard.state import APP_HOME_ENV
from kanji_leech_dashboard.study_content import KanjiStudyContent

KANJIDIC_FIXTURE = """<?xml version="1.0" encoding="UTF-8"?>
<kanjidic2>
  <character>
    <literal>学</literal>
    <misc>
      <stroke_count>8</stroke_count>
    </misc>
    <reading_meaning>
      <rmgroup>
        <reading r_type="ja_on">ガク</reading>
        <reading r_type="ja_kun">まな.ぶ</reading>
        <meaning>study</meaning>
      </rmgroup>
    </reading_meaning>
  </character>
</kanjidic2>
""".encode("utf-8")

KANJIVG_FIXTURE = """<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" xmlns:kvg="http://kanjivg.tagaini.net" viewBox="0 0 109 109">
  <g id="kvg:StrokePaths_05b66" kvg:element="学">
    <g kvg:element="冖">
      <path id="kvg:05b66-s1" d="M8 8 L32 8" />
    </g>
    <g kvg:element="子">
      <path id="kvg:05b66-s2" d="M18 18 L18 38" />
    </g>
  </g>
</svg>
""".encode("utf-8")

JITEN_CACHE_FIXTURE = """kanji,rank
学,3
校,6
年,8
火,1
山,2
水,4
田,5
漢,7
字,9
"""


class FakeContentProvider:
    def attribution(self) -> dict[str, object]:
        return {
            "dictionary": {"name": "KANJIDIC2", "source": "test", "license": "test"},
            "strokeData": {"name": "KanjiVG", "source": "test", "license": "test"},
        }

    def get_content(self, kanji: str) -> KanjiStudyContent:
        return KanjiStudyContent(
            kanji=kanji,
            keyword="study",
            meanings=("study",),
            readings=("ガク", "まな.ぶ"),
            primary_readings=("ガク",),
            components=("冖", "子"),
            component_hint="roof + child",
            stroke_count=8,
            stroke_paths=("M8 8 L32 8", "M18 18 L18 38"),
            dictionary_source="test-dict",
            stroke_source="test-strokes",
            data_warnings=tuple(),
        )

    def stroke_svg_path(self, kanji: str) -> Path | None:
        return None


class FakeSnapshotClient:
    def __init__(self, snapshot: CollectionSnapshot | Exception) -> None:
        self._snapshot = snapshot

    def sync_snapshot(self, _settings) -> CollectionSnapshot:
        if isinstance(self._snapshot, Exception):
            raise self._snapshot
        return self._snapshot


def prime_app_home(monkeypatch, tmp_path: Path) -> Path:
    monkeypatch.setenv(APP_HOME_ENV, str(tmp_path))
    data_dir = tmp_path / "data"
    data_dir.mkdir(parents=True, exist_ok=True)
    (data_dir / "jiten_frequency_kanji.csv").write_text(
        JITEN_CACHE_FIXTURE,
        encoding="utf-8",
    )
    return data_dir


def build_collection_snapshot(
    *,
    suspended_expression: str = "学校",
    active_expression: str = "学ぶ",
    mature_expression: str = "学年",
    suspended_queue: int = -1,
    active_interval: int = 10,
    mature_interval: int = 30,
) -> CollectionSnapshot:
    notes = (
        NoteSnapshot(
            note_id=1,
            model_name="Kiku",
            expression=suspended_expression,
            reading="がっこう",
            meaning="school",
            fields={"Expression": suspended_expression, "Reading": "がっこう", "Meaning": "school"},
            tags=("blocked",),
            card_ids=(11,),
        ),
        NoteSnapshot(
            note_id=2,
            model_name="Kiku",
            expression=active_expression,
            reading="まなぶ",
            meaning="study",
            fields={"Expression": active_expression, "Reading": "まなぶ", "Meaning": "study"},
            tags=("active",),
            card_ids=(22,),
        ),
        NoteSnapshot(
            note_id=3,
            model_name="Kiku",
            expression=mature_expression,
            reading="がくねん",
            meaning="school year",
            fields={"Expression": mature_expression, "Reading": "がくねん", "Meaning": "school year"},
            tags=("mature",),
            card_ids=(33,),
        ),
    )
    cards = (
        CardSnapshot(
            card_id=11,
            note_id=1,
            deck_name="Kiku",
            interval_days=0,
            due=0,
            card_ord=0,
            queue=suspended_queue,
            card_type=0,
            reps=0,
            lapses=0,
        ),
        CardSnapshot(
            card_id=22,
            note_id=2,
            deck_name="Kiku",
            interval_days=active_interval,
            due=12,
            card_ord=0,
            queue=2,
            card_type=2,
            reps=7,
            lapses=1,
        ),
        CardSnapshot(
            card_id=33,
            note_id=3,
            deck_name="Kiku",
            interval_days=mature_interval,
            due=30,
            card_ord=0,
            queue=2,
            card_type=2,
            reps=18,
            lapses=0,
        ),
    )
    return CollectionSnapshot(notes=notes, cards=cards)


def gzip_kanjidic_fixture() -> bytes:
    return gzip.compress(KANJIDIC_FIXTURE)
