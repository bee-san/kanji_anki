from __future__ import annotations

import os
import sqlite3
import time
from pathlib import Path

import pytest

from kanji_leech_dashboard import jiten as jiten_module
from kanji_leech_dashboard import study_content as study_content_module
from kanji_leech_dashboard.config import AppSettings
from kanji_leech_dashboard.dashboard import ProblemKanjiSeed, build_problem_kanji_seeds
from kanji_leech_dashboard.jiten import load_kanji_frequency_lookup, parse_frequency_csv
from kanji_leech_dashboard.study import StudyService, _coerce_str_list
from kanji_leech_dashboard.study_content import (
    KANJIDIC2_FILE_NAME,
    KANJIVG_DIR_NAME,
    StudyContentProvider,
    _first_text,
    _load_kanjidic_cache,
    _load_kanjivg_entry,
)

from .redesign_helpers import FakeContentProvider


def _seed(kanji: str = "学") -> ProblemKanjiSeed:
    return ProblemKanjiSeed(
        kanji=kanji,
        jiten_rank=3.0,
        collection_expressions=("学校", "学ぶ"),
        affected_suspended_expressions=("学校",),
        active_recurring_expressions=("学ぶ",),
        mature_supporting_expressions=(),
        support_deficit=2,
        browser_search=f'"Expression:*{kanji}*"',
    )


def test_dashboard_seed_builder_skips_fully_supported_suspended_kanji() -> None:
    assert build_problem_kanji_seeds(
        suspended_expressions=["学"],
        active_expressions=[],
        mature_expressions=["学"],
        threshold=1,
        kanji_ranks={},
        model_names=("Kiku",),
        search_field_name="Expression",
    ) == ()


def test_jiten_parse_and_stale_cache_edge_paths(
    monkeypatch: pytest.MonkeyPatch,
    tmp_path: Path,
) -> None:
    with monkeypatch.context() as patch:
        patch.setattr(jiten_module.csv, "reader", lambda *_args, **_kwargs: [])
        assert parse_frequency_csv("x") == {}

    class HeaderSniffer:
        def sniff(self, _sample, delimiters=None):
            return jiten_module.csv.excel

        def has_header(self, _sample):
            return True

    with monkeypatch.context() as patch:
        patch.setattr(jiten_module.csv, "Sniffer", HeaderSniffer)
        assert parse_frequency_csv("expression,rank\nonlyexpr\n ,2\n学,3\n") == {"学": 3.0}

    cache_path = tmp_path / "jiten_frequency_kanji.csv"
    meta_path = tmp_path / "jiten_frequency_kanji_meta.json"
    cache_path.write_text("bad", encoding="utf-8")
    meta_path.write_text('{"sourceUrl":"https://example.invalid/list.csv"}', encoding="utf-8")
    stale_time = time.time() - 7200
    os.utime(cache_path, (stale_time, stale_time))
    monkeypatch.setattr(jiten_module, "ensure_user_files_dir", lambda: tmp_path)

    lookup = load_kanji_frequency_lookup(
        AppSettings(jiten_cache_ttl_hours=1),
        opener=lambda *_args: (_ for _ in ()).throw(RuntimeError("offline")),
    )

    assert lookup.source_kind == "none"
    assert any("Could not read stale Jiten kanji cache" in warning for warning in lookup.warnings)


def test_study_review_token_mismatch_and_invalid_snapshot_payload(tmp_path: Path) -> None:
    now = 1_700_000_000
    service = StudyService(
        db_path=tmp_path / "app.sqlite3",
        content_provider=FakeContentProvider(),
        now_factory=lambda: now,
        token_factory=lambda: "review-token",
    )
    service.sync_problem_kanji("default", [_seed()])
    session = service.next_session("default", mode="new")

    with pytest.raises(ValueError, match="reviewToken does not match"):
        service.review(
            "default",
            kanji="学",
            review_token="wrong-token",
            prompt_type=session["session"]["promptType"],
            rating="good",
            handwriting_result=None,
            hints_used=0,
        )

    conn = sqlite3.connect(tmp_path / "app.sqlite3")
    try:
        conn.execute(
            "UPDATE study_items SET latest_problem_snapshot_json = ? WHERE kanji = ?",
            ("{bad", "学"),
        )
        conn.commit()
    finally:
        conn.close()

    overview = service.overview("default")
    assert overview["queuePreview"][0]["kanji"] == "学"
    assert _coerce_str_list({"x": 1}) == []


def test_study_content_attribution_and_stroke_svg_paths(
    monkeypatch: pytest.MonkeyPatch,
    tmp_path: Path,
) -> None:
    cached_provider = StudyContentProvider(user_files_dir=tmp_path / "cached")
    cached_svg = tmp_path / "cached" / KANJIVG_DIR_NAME / "05b66.svg"
    cached_svg.parent.mkdir(parents=True, exist_ok=True)
    cached_svg.write_text("<svg/>", encoding="utf-8")
    assert cached_provider.attribution()["strokeData"]["name"] == "KanjiVG"
    assert cached_provider.stroke_svg_path("学") == cached_svg

    downloaded_cache_provider = StudyContentProvider(user_files_dir=tmp_path / "downloaded-cache")
    downloaded_cache_svg = tmp_path / "downloaded-cache" / KANJIVG_DIR_NAME / "05b66.svg"

    def create_cached_svg(_kanji: str):
        downloaded_cache_svg.parent.mkdir(parents=True, exist_ok=True)
        downloaded_cache_svg.write_text("<svg/>", encoding="utf-8")
        return {}

    monkeypatch.setattr(downloaded_cache_provider, "_kanjivg_entry", create_cached_svg)
    assert downloaded_cache_provider.stroke_svg_path("学") == downloaded_cache_svg

    packaged_root = tmp_path / "packaged"
    downloaded_packaged_provider = StudyContentProvider(user_files_dir=tmp_path / "downloaded-packaged")
    downloaded_packaged_svg = packaged_root / KANJIVG_DIR_NAME / "05b66.svg"
    monkeypatch.setattr(study_content_module, "packaged_data_dir", lambda: packaged_root)

    def create_packaged_svg(_kanji: str):
        downloaded_packaged_svg.parent.mkdir(parents=True, exist_ok=True)
        downloaded_packaged_svg.write_text("<svg/>", encoding="utf-8")
        return {}

    monkeypatch.setattr(downloaded_packaged_provider, "_kanjivg_entry", create_packaged_svg)
    assert downloaded_packaged_provider.stroke_svg_path("学") == downloaded_packaged_svg


def test_study_content_parser_edge_paths(tmp_path: Path) -> None:
    kanjidic_path = tmp_path / KANJIDIC2_FILE_NAME
    kanjidic_path.write_text(
        """<?xml version="1.0" encoding="UTF-8"?>
<kanjidic2>
  <character>
    <literal>学</literal>
    <misc><stroke_count>8</stroke_count></misc>
    <reading_meaning>
      <rmgroup>
        <reading r_type="ja_on"> </reading>
        <reading r_type="ja_kun">まな.ぶ</reading>
        <meaning>study</meaning>
      </rmgroup>
    </reading_meaning>
  </character>
</kanjidic2>
""",
        encoding="utf-8",
    )
    cache = _load_kanjidic_cache(tmp_path)
    assert cache["学"]["readings"] == ("まな.ぶ",)

    bad_svg = tmp_path / "bad.svg"
    bad_svg.write_text("<svg", encoding="utf-8")
    assert _load_kanjivg_entry(bad_svg, "学") is None
    assert _first_text(None, "", "  ") == ""
