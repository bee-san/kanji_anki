from __future__ import annotations

from kanji_leech_dashboard.dashboard import (
    KanjiNotFoundError,
    build_dashboard_payload,
    build_kanji_detail_payload,
    build_problem_kanji_seeds,
)


def _payload(
    *,
    analyzed_suspended_card_count: int,
    suspended_expressions: list[str],
    active_expressions: list[str],
    mature_expressions: list[str],
    threshold: int = 3,
    kanji_ranks: dict[str, float] | None = None,
):
    return build_dashboard_payload(
        analyzed_suspended_card_count=analyzed_suspended_card_count,
        suspended_expressions=suspended_expressions,
        active_expressions=active_expressions,
        mature_expressions=mature_expressions,
        threshold=threshold,
        kanji_ranks=kanji_ranks or {},
        model_names=("Kiku",),
        search_field_name="Expression",
    )


def test_collection_rows_include_all_scoped_kanji() -> None:
    payload = _payload(
        analyzed_suspended_card_count=3,
        suspended_expressions=["学校", "学年", "校長", "学校"],
        active_expressions=["学ぶ", "学校", "水田"],
        mature_expressions=[],
    )

    rows = {row.kanji: row for row in payload.rows}
    assert payload.summary.analyzed_suspended_card_count == 3
    assert payload.summary.analyzed_suspended_expression_count == 3
    assert payload.summary.total_kanji_count == 6
    assert set(rows) == {"学", "校", "年", "長", "水", "田"}
    assert rows["学"].suspended_expression_count == 2
    assert rows["学"].active_recurring_expression_count == 2
    assert rows["学"].collection_expression_count == 3


def test_mature_support_counts_use_distinct_mature_expressions() -> None:
    payload = _payload(
        analyzed_suspended_card_count=1,
        suspended_expressions=["学校"],
        active_expressions=["学校", "学年", "校庭"],
        mature_expressions=["学校", "学校", "校庭"],
    )

    rows = {row.kanji: row for row in payload.rows}
    assert rows["学"].mature_support_count == 1
    assert rows["校"].mature_support_count == 2
    assert rows["学"].support_deficit == 2


def test_detail_payload_returns_collection_lists_and_counts() -> None:
    detail = build_kanji_detail_payload(
        kanji="学",
        suspended_expressions=["学校", "学年"],
        active_expressions=["学校", "学ぶ", "火山"],
        mature_expressions=["学年"],
        threshold=3,
        kanji_ranks={"学": 5},
        model_names=("Kiku",),
        search_field_name="Expression",
    )

    assert detail.collection_expressions == ("学ぶ", "学年", "学校")
    assert detail.affected_suspended_expressions == ("学年", "学校")
    assert detail.active_recurring_expressions == ("学ぶ", "学校")
    assert detail.mature_supporting_expressions == ("学年",)
    assert detail.is_unknown is True


def test_detail_payload_raises_for_missing_kanji() -> None:
    try:
        build_kanji_detail_payload(
            kanji="空",
            suspended_expressions=["学校"],
            active_expressions=["学ぶ"],
            mature_expressions=[],
            threshold=3,
            kanji_ranks={},
            model_names=("Kiku",),
            search_field_name="Expression",
        )
    except KanjiNotFoundError:
        pass
    else:  # pragma: no cover - defensive
        raise AssertionError("Expected KanjiNotFoundError")


def test_problem_child_seeds_only_include_under_supported_suspended_kanji() -> None:
    seeds = build_problem_kanji_seeds(
        suspended_expressions=["学校", "学年"],
        active_expressions=["学ぶ", "火山", "校庭"],
        mature_expressions=["校庭"],
        threshold=2,
        kanji_ranks={"学": 3, "校": 6, "年": 8, "火": 1},
        model_names=("Kiku",),
        search_field_name="Expression",
    )

    assert [seed.kanji for seed in seeds] == ["学", "年", "校"]
    assert seeds[0].support_deficit == 2
    assert len(seeds[0].affected_suspended_expressions) == 2
    assert len(seeds[2].mature_supporting_expressions) == 1
