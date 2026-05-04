from __future__ import annotations

import json
import sqlite3
from pathlib import Path
import types

import pytest

from kanji_leech_dashboard.dashboard import ProblemKanjiSeed
from kanji_leech_dashboard.study import (
    StudyItemNotFoundError,
    StudyService,
    _choose_prompt_type,
    _coerce_float,
    _coerce_int,
    _coerce_str_list,
    _requires_writing,
    _row_to_snapshot,
    _schedule_after_review,
    _ts_to_iso,
    _update_guide_progression,
    _why_in_queue,
)
from kanji_leech_dashboard.study_content import (
    KANJIDIC2_FILE_NAME,
    KANJIVG_DIR_NAME,
    KanjiStudyContent,
    StudyContentProvider,
    _coerce_int as content_coerce_int,
    _coerce_string_tuple,
    _default_url_fetcher,
    _first_text,
    _inflate_if_gzip,
    _load_kanjidic_cache,
    _load_kanjivg_entry,
    _load_seed_cache,
)

from .redesign_helpers import FakeContentProvider, KANJIVG_FIXTURE, build_collection_snapshot, gzip_kanjidic_fixture


class Clock:
    def __init__(self) -> None:
        self.value = 1_700_000_000

    def now(self) -> int:
        return self.value


def seed(kanji: str = "学") -> ProblemKanjiSeed:
    return ProblemKanjiSeed(
        kanji=kanji,
        jiten_rank=3,
        collection_expressions=("学校", "学ぶ"),
        affected_suspended_expressions=("学校",),
        active_recurring_expressions=("学ぶ",),
        mature_supporting_expressions=(),
        support_deficit=2,
        browser_search=f'"Expression:*{kanji}*"',
    )


def make_service(tmp_path: Path, *, content_provider=None, token_factory=None) -> StudyService:
    return StudyService(
        db_path=tmp_path / "app.sqlite3",
        content_provider=content_provider or FakeContentProvider(),
        now_factory=Clock().now,
        token_factory=token_factory or (lambda: "token"),
    )


def test_study_service_default_provider_path(monkeypatch, tmp_path: Path) -> None:
    captured = {}

    class StubProvider:
        def __init__(self, *, user_files_dir):
            captured["path"] = user_files_dir

        def attribution(self):
            return {}

        def get_content(self, kanji: str):
            return KanjiStudyContent(
                kanji=kanji,
                keyword="x",
                meanings=(),
                readings=(),
                primary_readings=(),
                components=(),
                component_hint="",
                stroke_count=None,
                stroke_paths=(),
                dictionary_source="x",
                stroke_source="x",
            )

    monkeypatch.setattr("kanji_leech_dashboard.study.StudyContentProvider", StubProvider)
    monkeypatch.setattr("kanji_leech_dashboard.study.ensure_user_files_dir", lambda: tmp_path / "data")
    service = StudyService(now_factory=Clock().now, token_factory=lambda: "token")

    assert service.get_attribution() == {}
    assert captured["path"] == tmp_path / "data"


def test_study_service_reactivates_inactive_seed_and_empty_session(tmp_path: Path) -> None:
    service = make_service(tmp_path)
    first = service.sync_problem_kanji("default", [seed("学")])
    assert first["introducedCount"] == 1
    second = service.sync_problem_kanji("default", [])
    third = service.sync_problem_kanji("default", [seed("学")])

    assert second["inactivatedCount"] == 1
    assert third["reactivatedCount"] == 1
    assert service.next_session("default", mode="review")["available"] is False


def test_study_item_detail_and_review_validation_errors(tmp_path: Path) -> None:
    service = make_service(tmp_path)
    service.sync_problem_kanji("default", [seed("学")])
    session = service.next_session("default", mode="new")

    detail = service.get_item_detail("default", "学")
    assert detail["kanji"] == "学"

    with pytest.raises(StudyItemNotFoundError):
        service.get_item_detail("default", "空")
    with pytest.raises(ValueError, match="rating"):
        service.review("default", kanji="学", review_token="token", prompt_type="recognition", rating="bad")
    with pytest.raises(ValueError, match="reviewToken is required"):
        service.review("default", kanji="学", review_token="", prompt_type="recognition", rating="good")
    with pytest.raises(ValueError, match="promptType"):
        service.review("default", kanji="学", review_token="token", prompt_type="bad", rating="good")
    with pytest.raises(ValueError, match="promptType does not match"):
        service.review(
            "default",
            kanji="学",
            review_token=session["session"]["reviewToken"],
            prompt_type="recognition",
            rating="good",
        )


def test_study_helper_branches(tmp_path: Path) -> None:
    service = make_service(tmp_path)
    service.sync_problem_kanji("default", [seed("学")])
    session = service.next_session("default", mode="new")
    service.review(
        "default",
        kanji="学",
        review_token=session["session"]["reviewToken"],
        prompt_type=session["session"]["promptType"],
        rating="again",
        handwriting_result={
            "attempted": True,
            "passed": False,
            "score": "bad",
            "evaluationMode": "manual-override",
            "selfAssessment": "override-retry",
        },
        hints_used="bad",
    )

    conn = sqlite3.connect(tmp_path / "app.sqlite3")
    conn.row_factory = sqlite3.Row
    conn.execute("UPDATE study_items SET latest_problem_snapshot_json = ?, item_status = ?, guide_level = ?, total_reviews = ?, consecutive_writing_failures = ? WHERE profile = ? AND kanji = ?", ("[]", "review", 3, 2, 1, "default", "学"))
    row = conn.execute("SELECT * FROM study_items WHERE profile = ? AND kanji = ?", ("default", "学")).fetchone()
    snapshot = _row_to_snapshot(row)
    conn.close()

    assert snapshot.latest_problem_snapshot == {}
    assert _choose_prompt_type(snapshot) == "production"
    assert _requires_writing(snapshot, "production") is False
    assert _requires_writing(snapshot, "recognition") is False
    assert _update_guide_progression(snapshot, attempted=False, passed=False) == (3, snapshot.consecutive_writing_successes, snapshot.consecutive_writing_failures)
    assert _update_guide_progression(snapshot, attempted=True, passed=False) == (2, 0, 0)
    assert _schedule_after_review(snapshot, rating="hard", now_ts=100)[1] == "review"
    learning_snapshot = snapshot.__class__(**{**snapshot.__dict__, "item_status": "learning"})
    assert _schedule_after_review(learning_snapshot, rating="easy", now_ts=100)[1] == "review"
    assert _why_in_queue(snapshot.__class__(**{**snapshot.__dict__, "is_problem_seed": False}), {}) == "This kanji left the current problem-child seed set, but its bridge history remains active."
    assert _ts_to_iso(None) is None
    assert _coerce_str_list(None) == []
    assert _coerce_str_list("x") == ["x"]
    assert _coerce_str_list(("x", "", "y")) == ["x", "y"]
    assert _coerce_int("bad", None, default=4) == 4
    assert _coerce_float("bad", default=1.5) == 1.5


def test_study_select_next_row_invalid_mode_and_require_item(tmp_path: Path) -> None:
    service = make_service(tmp_path)
    conn = sqlite3.connect(tmp_path / "app.sqlite3")
    conn.row_factory = sqlite3.Row
    with pytest.raises(ValueError, match="mode must be one of mixed, review, or new"):
        service._select_next_row(conn, "default", 0, mode="oops")
    with pytest.raises(StudyItemNotFoundError):
        service._require_item(conn, "default", "空")
    conn.close()


def test_study_ensure_column_adds_missing_column(tmp_path: Path) -> None:
    service = make_service(tmp_path)
    conn = sqlite3.connect(tmp_path / "extra.sqlite3")
    conn.row_factory = sqlite3.Row
    conn.execute("CREATE TABLE demo (id INTEGER PRIMARY KEY)")
    service._ensure_column(conn, "demo", "name", "TEXT")
    names = {row["name"] for row in conn.execute("PRAGMA table_info(demo)").fetchall()}
    conn.close()

    assert "name" in names


def test_study_content_provider_fallbacks_and_paths(monkeypatch, tmp_path: Path) -> None:
    packaged = tmp_path / "packaged"
    (packaged / KANJIVG_DIR_NAME).mkdir(parents=True)
    (packaged / "study_content_seed.json").write_text(
        json.dumps(
            {
                "空": {
                    "keyword": "sky",
                    "meanings": ["sky"],
                    "readings": ["クウ"],
                    "primaryReadings": ["クウ"],
                    "components": ["穴", "工"],
                    "componentHint": "hole + work",
                    "strokeCount": 8,
                    "strokePaths": ["M1 1"],
                    "dictionarySource": "seed",
                    "strokeSource": "seed-svg",
                }
            },
            ensure_ascii=False,
        ),
        encoding="utf-8",
    )
    (packaged / KANJIVG_DIR_NAME / "07a7a.svg").write_bytes(KANJIVG_FIXTURE.replace(b"\xe5\xad\xa6", b"\xe7\xa9\xba"))
    monkeypatch.setattr("kanji_leech_dashboard.study_content.packaged_data_dir", lambda: packaged)
    provider = StudyContentProvider(
        user_files_dir=tmp_path / "user",
        url_fetcher=lambda _url: (_ for _ in ()).throw(OSError("offline")),
        kanjidic_download_urls=("https://example.invalid/kanjidic2.xml.gz",),
        kanjivg_svg_url_template="https://example.invalid/{codepoint}.svg",
    )

    content = provider.get_content("空")
    assert content.keyword == "sky"
    assert content.stroke_paths == ("M1 1",)
    assert provider.stroke_svg_path("空") == packaged / KANJIVG_DIR_NAME / "07a7a.svg"
    assert provider.stroke_svg_path("海") is None


def test_study_content_provider_failure_and_retry_branches(monkeypatch, tmp_path: Path) -> None:
    packaged = tmp_path / "packaged"
    packaged.mkdir()
    monkeypatch.setattr("kanji_leech_dashboard.study_content.packaged_data_dir", lambda: packaged)
    calls = []

    def fetcher(url: str) -> bytes:
        calls.append(url)
        raise OSError("offline")

    provider = StudyContentProvider(
        user_files_dir=tmp_path / "user",
        url_fetcher=fetcher,
        kanjidic_download_urls=("https://example.invalid/one.gz", "https://example.invalid/two.gz"),
        kanjivg_svg_url_template="https://example.invalid/{codepoint}.svg",
    )
    content = provider.get_content("海")

    assert "KANJIDIC2 meaning data could not be loaded" in content.data_warnings[0]
    assert provider._download_and_cache_kanjivg("06d77", tmp_path / "user" / KANJIVG_DIR_NAME / "06d77.svg", "海") is None
    assert provider.get_content("海").stroke_paths == ()
    assert calls.count("https://example.invalid/06d77.svg") == 2


def test_study_content_low_level_helpers(monkeypatch, tmp_path: Path) -> None:
    packaged = tmp_path / "packaged"
    packaged.mkdir()
    monkeypatch.setattr("kanji_leech_dashboard.study_content.packaged_data_dir", lambda: packaged)

    assert _load_seed_cache(tmp_path) == {}
    (tmp_path / "study_content_seed.json").write_text("{bad", encoding="utf-8")
    assert _load_seed_cache(tmp_path) == {}
    (packaged / "study_content_seed.json").write_text(json.dumps({"学": {"keyword": "study"}, 1: []}), encoding="utf-8")
    assert _load_seed_cache(tmp_path)["学"]["keyword"] == "study"

    kanjidic_path = tmp_path / KANJIDIC2_FILE_NAME
    kanjidic_path.write_text(
        """<kanjidic2><character><literal>学</literal><misc><stroke_count>bad</stroke_count><stroke_count>8</stroke_count></misc><reading_meaning><rmgroup><meaning m_lang='fr'>etudier</meaning><meaning>study</meaning><reading r_type='ja_on'>ガク</reading><reading r_type='ja_kun'>まな.ぶ</reading></rmgroup></reading_meaning></character><character><literal></literal></character></kanjidic2>""",
        encoding="utf-8",
    )
    cache = _load_kanjidic_cache(tmp_path)
    assert cache["学"]["stroke_count"] == 8
    kanjidic_path.write_text("<bad", encoding="utf-8")
    assert _load_kanjidic_cache(tmp_path) == {}

    svg_path = tmp_path / "bad.svg"
    svg_path.write_text("<svg></svg>", encoding="utf-8")
    assert _load_kanjivg_entry(svg_path, "学") is None
    svg_path.write_bytes(KANJIVG_FIXTURE)
    assert _load_kanjivg_entry(svg_path, "学")["components"] == ("冖", "子")

    assert _inflate_if_gzip(gzip_kanjidic_fixture(), url="x.gz").startswith(b"<?xml")
    assert _inflate_if_gzip(b"plain", url="plain.txt") == b"plain"

    class Response:
        def __enter__(self):
            return self

        def __exit__(self, exc_type, exc, tb):
            return False

        def read(self):
            return b"payload"

    monkeypatch.setattr("kanji_leech_dashboard.study_content.urlopen", lambda request, timeout=12: Response())
    assert _default_url_fetcher("https://example.invalid") == b"payload"

    assert _coerce_string_tuple(None) == ()
    assert _coerce_string_tuple(" x ") == ("x",)
    assert _coerce_string_tuple(["x", "x", "y"]) == ("x", "y")
    assert _coerce_string_tuple(5) == ("5",)
    assert content_coerce_int(None, "bad", 0, 3) == 3
    assert content_coerce_int(None, "bad", 0) is None
    assert _first_text(None, " ", "ok") == "ok"
