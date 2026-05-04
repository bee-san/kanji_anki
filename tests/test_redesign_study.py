from __future__ import annotations

from dataclasses import replace
from pathlib import Path
import sqlite3

import pytest

from kanji_leech_dashboard.dashboard import ProblemKanjiSeed
from kanji_leech_dashboard.study import StudyService

from .redesign_helpers import FakeContentProvider


class NoGeometryContentProvider(FakeContentProvider):
    def get_content(self, kanji: str):
        return replace(super().get_content(kanji), stroke_paths=())


class Clock:
    def __init__(self) -> None:
        self.value = 1_700_000_000

    def now(self) -> int:
        return self.value

    def advance(self, seconds: int) -> None:
        self.value += seconds


class SequentialTokenFactory:
    def __init__(self) -> None:
        self.issued = 0

    def __call__(self) -> str:
        self.issued += 1
        return f"review-token-{self.issued}"


def _seed(
    kanji: str,
    *,
    suspended: tuple[str, ...],
    active: tuple[str, ...] = tuple(),
    mature: tuple[str, ...] = tuple(),
    deficit: int = 2,
    rank: float | None = 5,
) -> ProblemKanjiSeed:
    return ProblemKanjiSeed(
        kanji=kanji,
        jiten_rank=rank,
        collection_expressions=tuple(sorted(set(suspended + active))),
        affected_suspended_expressions=suspended,
        active_recurring_expressions=active,
        mature_supporting_expressions=mature,
        support_deficit=deficit,
        browser_search=f'"Expression:*{kanji}*"',
    )


def make_service(
    tmp_path: Path,
    clock: Clock,
    token_factory=None,
    content_provider=None,
) -> StudyService:
    return StudyService(
        db_path=tmp_path / "app.sqlite3",
        content_provider=content_provider or FakeContentProvider(),
        now_factory=clock.now,
        token_factory=token_factory or (lambda: "review-token"),
    )


def test_sync_seeds_only_creates_and_updates_problem_child_queue_items(tmp_path: Path) -> None:
    clock = Clock()
    service = make_service(tmp_path, clock)
    first = service.sync_problem_kanji(
        "default",
        [
            _seed("学", suspended=("学校", "学年"), active=("学ぶ",), deficit=2, rank=3),
            _seed("年", suspended=("学年",), deficit=1, rank=6),
        ],
    )
    second = service.sync_problem_kanji(
        "default",
        [_seed("学", suspended=("学校", "学年"), active=("学ぶ",), deficit=2, rank=3)],
    )

    assert first["introducedCount"] == 2
    assert first["overview"]["newCount"] == 2
    assert second["introducedCount"] == 0
    assert second["updatedCount"] == 1
    assert second["inactivatedCount"] == 1


def test_duplicate_review_token_does_not_double_advance_item(tmp_path: Path) -> None:
    clock = Clock()
    service = make_service(tmp_path, clock)
    service.sync_problem_kanji(
        "default",
        [_seed("学", suspended=("学校",), active=("学ぶ",), deficit=2, rank=3)],
    )
    session = service.next_session("default", mode="new")
    first = service.review(
        "default",
        kanji="学",
        review_token=session["session"]["reviewToken"],
        prompt_type=session["session"]["promptType"],
        rating="good",
        handwriting_result={"attempted": True, "passed": True, "score": 0.9},
        hints_used=0,
    )
    duplicate = service.review(
        "default",
        kanji="学",
        review_token=session["session"]["reviewToken"],
        prompt_type=session["session"]["promptType"],
        rating="again",
        handwriting_result={"attempted": True, "passed": False, "score": 0.1},
        hints_used=2,
    )

    assert first["duplicate"] is False
    assert duplicate["duplicate"] is True
    assert duplicate["item"]["reviewCount"] == 1


def test_review_flow_promotes_item_and_writing_guidance_independently(tmp_path: Path) -> None:
    clock = Clock()
    tokens = SequentialTokenFactory()
    service = make_service(tmp_path, clock, token_factory=tokens)
    service.sync_problem_kanji(
        "default",
        [_seed("学", suspended=("学校", "学年"), active=("学ぶ",), deficit=2, rank=3)],
    )
    session_one = service.next_session("default", mode="new")
    review_one = service.review(
        "default",
        kanji="学",
        review_token=session_one["session"]["reviewToken"],
        prompt_type=session_one["session"]["promptType"],
        rating="good",
        handwriting_result={"attempted": True, "passed": True, "score": 0.9},
        hints_used=0,
    )
    session_two = service.next_session("default", mode="mixed")
    review_two = service.review(
        "default",
        kanji="学",
        review_token=session_two["session"]["reviewToken"],
        prompt_type=session_two["session"]["promptType"],
        rating="good",
        handwriting_result={"attempted": True, "passed": True, "score": 0.95},
        hints_used=1,
    )
    clock.advance(10 * 60)
    session_three = service.next_session("default", mode="review")
    review_three = service.review(
        "default",
        kanji="学",
        review_token=session_three["session"]["reviewToken"],
        prompt_type=session_three["session"]["promptType"],
        rating="good",
        handwriting_result={"attempted": True, "passed": True, "score": 0.98},
        hints_used=0,
    )

    assert session_one["available"] is True
    assert review_one["item"]["itemStatus"] == "learning"
    assert session_two["available"] is True
    assert review_two["item"]["itemStatus"] == "learning"
    assert session_three["available"] is True
    assert review_three["item"]["itemStatus"] == "review"
    assert review_three["item"]["guideLevel"] == 1
    assert review_three["overview"]["dueCount"] == 0


def test_next_session_exposes_progressive_handwriting_policy(tmp_path: Path) -> None:
    clock = Clock()
    service = make_service(tmp_path, clock)
    service.sync_problem_kanji(
        "default",
        [_seed("学", suspended=("学校",), active=("学ぶ",), deficit=2, rank=3)],
    )

    session = service.next_session("default", mode="new")["session"]

    assert session["requiresWriting"] is True
    assert session["handwritingPolicy"]["guideMode"] == "trace"
    assert session["handwritingPolicy"]["allowedRatingsOnFailure"] == ["again"]
    assert session["handwritingPolicy"]["guidedEvaluationAvailable"] is True


def test_next_session_marks_manual_only_without_stroke_geometry(tmp_path: Path) -> None:
    clock = Clock()
    service = make_service(
        tmp_path,
        clock,
        content_provider=NoGeometryContentProvider(),
    )
    service.sync_problem_kanji(
        "default",
        [_seed("学", suspended=("学校",), active=("学ぶ",), deficit=2, rank=3)],
    )

    session = service.next_session("default", mode="new")["session"]

    assert session["handwritingPolicy"]["guidedEvaluationAvailable"] is False
    assert session["handwritingPolicy"]["manualOnlyWithoutGeometry"] is True


def test_failed_handwriting_blocks_good_until_override(tmp_path: Path) -> None:
    clock = Clock()
    service = make_service(tmp_path, clock)
    service.sync_problem_kanji(
        "default",
        [_seed("学", suspended=("学校",), active=("学ぶ",), deficit=2, rank=3)],
    )
    session = service.next_session("default", mode="new")["session"]

    with pytest.raises(ValueError, match="handwriting result is required"):
        service.review(
            "default",
            kanji="学",
            review_token=session["reviewToken"],
            prompt_type=session["promptType"],
            rating="good",
            handwriting_result=None,
            hints_used=0,
        )

    with pytest.raises(ValueError, match="only allows again"):
        service.review(
            "default",
            kanji="学",
            review_token=session["reviewToken"],
            prompt_type=session["promptType"],
            rating="good",
            handwriting_result={"attempted": True, "passed": False, "score": 0.1},
            hints_used=0,
        )

    review = service.review(
        "default",
        kanji="学",
        review_token=session["reviewToken"],
        prompt_type=session["promptType"],
        rating="again",
        handwriting_result={
            "attempted": True,
            "passed": False,
            "score": 0.1,
            "evaluationMode": "manual-override",
            "selfAssessment": "override-retry",
        },
        hints_used=0,
    )

    assert review["duplicate"] is False
    assert review["item"]["itemStatus"] == "learning"


def test_sync_retires_mature_srs_item_once_anki_has_three_mature_cards(tmp_path: Path) -> None:
    clock = Clock()
    service = make_service(tmp_path, clock)
    service.sync_problem_kanji(
        "default",
        [_seed("学", suspended=("学校",), active=("学ぶ",), deficit=2, rank=3)],
    )
    conn = sqlite3.connect(tmp_path / "app.sqlite3")
    try:
        conn.execute(
            """
            UPDATE study_items
            SET item_status = ?,
                total_reviews = ?,
                last_reviewed_ts = ?,
                due_ts = ?,
                active_review_token = ?,
                active_prompt_type = ?,
                active_session_issued_ts = ?
            WHERE profile = ? AND kanji = ?
            """,
            (
                "review",
                6,
                clock.now(),
                clock.now() + 30 * 86400,
                "stale-token",
                "recognition",
                clock.now(),
                "default",
                "学",
            ),
        )
        conn.commit()
    finally:
        conn.close()

    result = service.sync_problem_kanji(
        "default",
        [],
        mature_anki_card_counts={"学": 3},
        srs_mature_days=21,
    )
    item = service.get_item_detail("default", "学")

    assert result["inactivatedCount"] == 1
    assert item["itemStatus"] == "inactive"
    assert item["isProblemSeed"] is False
    assert service.next_session("default")["available"] is False


def test_mature_retired_active_seed_is_not_counted_as_available_for_study(tmp_path: Path) -> None:
    clock = Clock()
    service = make_service(tmp_path, clock)
    service.sync_problem_kanji(
        "default",
        [_seed("学", suspended=("学校",), active=("学ぶ",), deficit=2, rank=3)],
    )
    conn = sqlite3.connect(tmp_path / "app.sqlite3")
    try:
        conn.execute(
            """
            UPDATE study_items
            SET item_status = ?,
                total_reviews = ?,
                last_reviewed_ts = ?,
                due_ts = ?,
                active_review_token = ?,
                active_prompt_type = ?,
                active_session_issued_ts = ?
            WHERE profile = ? AND kanji = ?
            """,
            (
                "review",
                6,
                clock.now(),
                clock.now() + 30 * 86400,
                "stale-token",
                "recognition",
                clock.now(),
                "default",
                "学",
            ),
        )
        conn.commit()
    finally:
        conn.close()

    result = service.sync_problem_kanji(
        "default",
        [_seed("学", suspended=("学校",), active=("学ぶ",), deficit=2, rank=3)],
        mature_anki_card_counts={"学": 3},
        srs_mature_days=21,
    )
    item = service.get_item_detail("default", "学")

    assert result["inactivatedCount"] == 1
    assert result["overview"]["currentProblemSeedCount"] == 0
    assert result["overview"]["activeQueueCount"] == 0
    assert item["itemStatus"] == "inactive"
    assert item["isProblemSeed"] is True
    assert service.next_session("default")["available"] is False
