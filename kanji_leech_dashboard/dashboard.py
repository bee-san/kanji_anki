from __future__ import annotations

from dataclasses import dataclass
from itertools import chain
from typing import Any, Iterable, Mapping

from .config import AddonConfig
from .jiten import FrequencyLookup, load_kanji_frequency_lookup as _load_kanji_frequency_lookup
from .normalization import extract_kanji_chars, normalize_lookup_text, strip_html_text


class KanjiNotFoundError(LookupError):
    pass


@dataclass(frozen=True)
class KanjiDashboardRow:
    kanji: str
    jiten_rank: float | None
    collection_expression_count: int
    suspended_expression_count: int
    active_recurring_expression_count: int
    mature_support_count: int
    support_deficit: int
    is_unknown: bool
    browser_search: str

    def to_dict(self) -> dict[str, Any]:
        return {
            "kanji": self.kanji,
            "jitenRank": _serialize_rank(self.jiten_rank),
            "collectionExpressionCount": self.collection_expression_count,
            "suspendedExpressionCount": self.suspended_expression_count,
            "activeRecurringExpressionCount": self.active_recurring_expression_count,
            "matureSupportCount": self.mature_support_count,
            "supportDeficit": self.support_deficit,
            "isUnknown": self.is_unknown,
            "browserSearch": self.browser_search,
        }


@dataclass(frozen=True)
class KanjiDashboardSummary:
    analyzed_suspended_card_count: int
    analyzed_suspended_expression_count: int
    unknown_kanji_count: int
    average_kanji_rank: float | None
    ranked_kanji_count: int
    total_kanji_count: int
    mature_support_threshold: int

    def to_dict(self) -> dict[str, int | float | None]:
        return {
            "analyzedSuspendedCardCount": self.analyzed_suspended_card_count,
            "analyzedSuspendedExpressionCount": self.analyzed_suspended_expression_count,
            "unknownKanjiCount": self.unknown_kanji_count,
            "averageKanjiRank": _serialize_rank(self.average_kanji_rank),
            "rankedKanjiCount": self.ranked_kanji_count,
            "totalKanjiCount": self.total_kanji_count,
            "matureSupportThreshold": self.mature_support_threshold,
        }


@dataclass(frozen=True)
class KanjiDashboardPayload:
    summary: KanjiDashboardSummary
    rows: tuple[KanjiDashboardRow, ...]
    warnings: tuple[str, ...] = tuple()
    jiten_source_kind: str = "none"

    def to_dict(self) -> dict[str, Any]:
        return {
            "summary": self.summary.to_dict(),
            "rows": [row.to_dict() for row in self.rows],
            "warnings": list(self.warnings),
            "jitenSourceKind": self.jiten_source_kind,
        }


@dataclass(frozen=True)
class KanjiDashboardDetail:
    kanji: str
    jiten_rank: float | None
    collection_expressions: tuple[str, ...]
    affected_suspended_expressions: tuple[str, ...]
    active_recurring_expressions: tuple[str, ...]
    mature_supporting_expressions: tuple[str, ...]
    support_deficit: int
    is_unknown: bool
    browser_search: str

    def to_dict(self) -> dict[str, Any]:
        return {
            "kanji": self.kanji,
            "jitenRank": _serialize_rank(self.jiten_rank),
            "collectionExpressionCount": len(self.collection_expressions),
            "suspendedExpressionCount": len(self.affected_suspended_expressions),
            "activeRecurringExpressionCount": len(self.active_recurring_expressions),
            "matureSupportCount": len(self.mature_supporting_expressions),
            "supportDeficit": self.support_deficit,
            "isUnknown": self.is_unknown,
            "collectionExpressions": list(self.collection_expressions),
            "affectedSuspendedExpressions": list(self.affected_suspended_expressions),
            "activeRecurringExpressions": list(self.active_recurring_expressions),
            "matureSupportingExpressions": list(self.mature_supporting_expressions),
            "browserSearch": self.browser_search,
        }


@dataclass(frozen=True)
class ExpressionScan:
    expressions: tuple[str, ...]
    analyzed_card_count: int
    skipped_card_count: int


@dataclass(frozen=True)
class DashboardCollectionScans:
    suspended: ExpressionScan
    active: ExpressionScan
    mature: ExpressionScan


@dataclass(frozen=True)
class KanjiDashboardSnapshot:
    suspended_expressions: tuple[str, ...]
    active_expressions: tuple[str, ...]
    mature_expressions: tuple[str, ...]
    collection_expressions: tuple[str, ...]
    suspended_index: dict[str, tuple[str, ...]]
    active_index: dict[str, tuple[str, ...]]
    mature_index: dict[str, tuple[str, ...]]
    collection_index: dict[str, tuple[str, ...]]
    threshold: int


@dataclass(frozen=True)
class KanjiStats:
    collection_expressions: tuple[str, ...]
    affected_suspended_expressions: tuple[str, ...]
    active_recurring_expressions: tuple[str, ...]
    mature_supporting_expressions: tuple[str, ...]
    support_deficit: int
    is_unknown: bool


@dataclass(frozen=True)
class ProblemKanjiSeed:
    kanji: str
    jiten_rank: float | None
    collection_expressions: tuple[str, ...]
    affected_suspended_expressions: tuple[str, ...]
    active_recurring_expressions: tuple[str, ...]
    mature_supporting_expressions: tuple[str, ...]
    support_deficit: int
    browser_search: str

    def to_dict(self) -> dict[str, Any]:
        return {
            "kanji": self.kanji,
            "jitenRank": _serialize_rank(self.jiten_rank),
            "collectionExpressionCount": len(self.collection_expressions),
            "suspendedExpressionCount": len(self.affected_suspended_expressions),
            "activeRecurringExpressionCount": len(self.active_recurring_expressions),
            "matureSupportCount": len(self.mature_supporting_expressions),
            "supportDeficit": self.support_deficit,
            "collectionExpressions": list(self.collection_expressions),
            "affectedSuspendedExpressions": list(self.affected_suspended_expressions),
            "activeRecurringExpressions": list(self.active_recurring_expressions),
            "matureSupportingExpressions": list(self.mature_supporting_expressions),
            "browserSearch": self.browser_search,
        }


def build_kanji_dashboard(
    col: Any,
    config: AddonConfig,
) -> dict[str, Any]:
    lookup = _load_kanji_frequency_lookup(config)
    scans = scan_dashboard_collection(col, config)
    warnings = list(lookup.warnings)
    if scans.suspended.skipped_card_count:
        warnings.append(
            f"Skipped {scans.suspended.skipped_card_count} suspended cards because the Expression field was empty."
        )
    if scans.active.skipped_card_count:
        warnings.append(
            f"Skipped {scans.active.skipped_card_count} non-suspended supported cards because the Expression field was empty."
        )
    payload = build_dashboard_payload(
        analyzed_suspended_card_count=scans.suspended.analyzed_card_count,
        suspended_expressions=scans.suspended.expressions,
        active_expressions=scans.active.expressions,
        mature_expressions=scans.mature.expressions,
        threshold=config.kanji_dashboard_mature_support_threshold,
        kanji_ranks=lookup.ranks,
        model_names=config.model_names,
        search_field_name=config.expression_field,
        warnings=warnings,
        jiten_source_kind=lookup.source_kind,
    )
    return payload.to_dict()


def build_kanji_detail(
    col: Any,
    config: AddonConfig,
    kanji: str,
) -> dict[str, Any]:
    lookup = _load_kanji_frequency_lookup(config)
    scans = scan_dashboard_collection(col, config)
    payload = build_kanji_detail_payload(
        kanji=kanji,
        suspended_expressions=scans.suspended.expressions,
        active_expressions=scans.active.expressions,
        mature_expressions=scans.mature.expressions,
        threshold=config.kanji_dashboard_mature_support_threshold,
        kanji_ranks=lookup.ranks,
        model_names=config.model_names,
        search_field_name=config.expression_field,
    )
    return payload.to_dict()


def scan_dashboard_collection(
    col: Any,
    config: AddonConfig,
) -> DashboardCollectionScans:
    return DashboardCollectionScans(
        suspended=_scan_expressions_from_card_ids(
            col,
            list(map(int, col.find_cards(build_suspended_query(config.model_names)))),
            config.expression_field,
        ),
        active=_scan_expressions_from_card_ids(
            col,
            list(map(int, col.find_cards(build_active_query(config.model_names)))),
            config.expression_field,
        ),
        mature=_scan_expressions_from_card_ids(
            col,
            list(map(int, col.find_cards(config.effective_mature_query))),
            config.expression_field,
        ),
    )


def build_dashboard_payload(
    *,
    analyzed_suspended_card_count: int,
    suspended_expressions: Iterable[str],
    active_expressions: Iterable[str],
    mature_expressions: Iterable[str],
    threshold: int,
    kanji_ranks: Mapping[str, float],
    model_names: tuple[str, ...],
    search_field_name: str,
    warnings: Iterable[str] = (),
    jiten_source_kind: str = "none",
) -> KanjiDashboardPayload:
    snapshot = _build_dashboard_snapshot(
        suspended_expressions=suspended_expressions,
        active_expressions=active_expressions,
        mature_expressions=mature_expressions,
        threshold=threshold,
    )
    rows = [
        _build_dashboard_row(
            kanji,
            snapshot=snapshot,
            kanji_ranks=kanji_ranks,
            model_names=model_names,
            search_field_name=search_field_name,
        )
        for kanji in snapshot.collection_index
    ]
    rows.sort(key=_overview_row_sort_key)
    ranked_rows = [row for row in rows if row.jiten_rank is not None]
    warnings_tuple = tuple(dict.fromkeys(str(warning) for warning in warnings if str(warning)))
    summary = KanjiDashboardSummary(
        analyzed_suspended_card_count=analyzed_suspended_card_count,
        analyzed_suspended_expression_count=len(snapshot.suspended_expressions),
        unknown_kanji_count=sum(1 for row in rows if row.is_unknown),
        average_kanji_rank=_average_rank([row.jiten_rank for row in ranked_rows]),
        ranked_kanji_count=len(ranked_rows),
        total_kanji_count=len(rows),
        mature_support_threshold=threshold,
    )
    return KanjiDashboardPayload(
        summary=summary,
        rows=tuple(rows),
        warnings=warnings_tuple,
        jiten_source_kind=jiten_source_kind,
    )


def build_kanji_detail_payload(
    *,
    kanji: str,
    suspended_expressions: Iterable[str],
    active_expressions: Iterable[str],
    mature_expressions: Iterable[str],
    threshold: int,
    kanji_ranks: Mapping[str, float],
    model_names: tuple[str, ...],
    search_field_name: str,
) -> KanjiDashboardDetail:
    snapshot = _build_dashboard_snapshot(
        suspended_expressions=suspended_expressions,
        active_expressions=active_expressions,
        mature_expressions=mature_expressions,
        threshold=threshold,
    )
    normalized_kanji = _normalize_kanji_key(kanji)
    if normalized_kanji not in snapshot.collection_index:
        raise KanjiNotFoundError(
            f"Kanji '{normalized_kanji or kanji}' was not found in the current study scope."
        )
    stats = _build_kanji_stats(snapshot, normalized_kanji)
    return KanjiDashboardDetail(
        kanji=normalized_kanji,
        jiten_rank=kanji_ranks.get(normalized_kanji),
        collection_expressions=stats.collection_expressions,
        affected_suspended_expressions=stats.affected_suspended_expressions,
        active_recurring_expressions=stats.active_recurring_expressions,
        mature_supporting_expressions=stats.mature_supporting_expressions,
        support_deficit=stats.support_deficit,
        is_unknown=stats.is_unknown,
        browser_search=build_browser_search(
            normalized_kanji,
            model_names=model_names,
            search_field_name=search_field_name,
        ),
    )


def build_problem_kanji_seeds(
    *,
    suspended_expressions: Iterable[str],
    active_expressions: Iterable[str],
    mature_expressions: Iterable[str],
    threshold: int,
    kanji_ranks: Mapping[str, float],
    model_names: tuple[str, ...],
    search_field_name: str,
) -> tuple[ProblemKanjiSeed, ...]:
    snapshot = _build_dashboard_snapshot(
        suspended_expressions=suspended_expressions,
        active_expressions=active_expressions,
        mature_expressions=mature_expressions,
        threshold=threshold,
    )
    seeds: list[ProblemKanjiSeed] = []
    for kanji in snapshot.suspended_index:
        stats = _build_kanji_stats(snapshot, kanji)
        if not stats.affected_suspended_expressions or stats.support_deficit <= 0:
            continue
        seeds.append(
            ProblemKanjiSeed(
                kanji=kanji,
                jiten_rank=kanji_ranks.get(kanji),
                collection_expressions=stats.collection_expressions,
                affected_suspended_expressions=stats.affected_suspended_expressions,
                active_recurring_expressions=stats.active_recurring_expressions,
                mature_supporting_expressions=stats.mature_supporting_expressions,
                support_deficit=stats.support_deficit,
                browser_search=build_browser_search(
                    kanji,
                    model_names=model_names,
                    search_field_name=search_field_name,
                ),
            )
        )
    seeds.sort(key=_problem_seed_sort_key)
    return tuple(seeds)


def build_suspended_query(model_names: tuple[str, ...]) -> str:
    model_query = build_supported_query(model_names)
    if model_query:
        return f"{model_query} is:suspended"
    return "is:suspended"


def build_active_query(model_names: tuple[str, ...]) -> str:
    model_query = build_supported_query(model_names)
    if model_query:
        return f"{model_query} -is:suspended"
    return "-is:suspended"


def build_supported_query(model_names: tuple[str, ...]) -> str:
    model_query = _build_model_query(model_names)
    if model_query:
        return f"({model_query})"
    return ""


def build_browser_search(
    kanji: str,
    *,
    model_names: tuple[str, ...],
    search_field_name: str,
) -> str:
    model_query = build_supported_query(model_names)
    field_query = f'"{search_field_name}:*{kanji}*"'
    if model_query:
        return f"{model_query} {field_query}"
    return field_query


def load_kanji_frequency_lookup(config: AddonConfig) -> FrequencyLookup:
    return _load_kanji_frequency_lookup(config)


def _build_dashboard_snapshot(
    *,
    suspended_expressions: Iterable[str],
    active_expressions: Iterable[str],
    mature_expressions: Iterable[str],
    threshold: int,
) -> KanjiDashboardSnapshot:
    normalized_suspended_expressions = _unique_expressions(suspended_expressions)
    normalized_active_expressions = _unique_expressions(active_expressions)
    normalized_mature_expressions = _unique_expressions(mature_expressions)
    normalized_collection_expressions = _unique_expressions(
        chain(
            normalized_suspended_expressions,
            normalized_active_expressions,
            normalized_mature_expressions,
        )
    )
    return KanjiDashboardSnapshot(
        suspended_expressions=normalized_suspended_expressions,
        active_expressions=normalized_active_expressions,
        mature_expressions=normalized_mature_expressions,
        collection_expressions=normalized_collection_expressions,
        suspended_index=_build_kanji_expression_index(normalized_suspended_expressions),
        active_index=_build_kanji_expression_index(normalized_active_expressions),
        mature_index=_build_kanji_expression_index(normalized_mature_expressions),
        collection_index=_build_kanji_expression_index(normalized_collection_expressions),
        threshold=threshold,
    )


def _scan_expressions_from_card_ids(
    col: Any,
    card_ids: Iterable[int],
    expression_field: str,
) -> ExpressionScan:
    expressions: list[str] = []
    analyzed_card_count = 0
    skipped_card_count = 0
    for card_id in card_ids:
        card = col.get_card(int(card_id))
        note = card.note()
        normalized_expression = _normalize_expression(_get_note_field(note, expression_field))
        if not normalized_expression:
            skipped_card_count += 1
            continue
        analyzed_card_count += 1
        expressions.append(normalized_expression)
    return ExpressionScan(
        expressions=tuple(expressions),
        analyzed_card_count=analyzed_card_count,
        skipped_card_count=skipped_card_count,
    )


def _build_kanji_expression_index(
    expressions: tuple[str, ...],
) -> dict[str, tuple[str, ...]]:
    index: dict[str, list[str]] = {}
    for expression in expressions:
        for kanji in extract_kanji_chars(expression):
            bucket = index.setdefault(kanji, [])
            bucket.append(expression)
    return {
        kanji: tuple(bucket)
        for kanji, bucket in sorted(index.items(), key=lambda item: item[0])
    }


def _unique_expressions(expressions: Iterable[str]) -> tuple[str, ...]:
    seen: set[str] = set()
    unique: list[str] = []
    for expression in expressions:
        normalized = _normalize_expression(expression)
        if not normalized or normalized in seen:
            continue
        seen.add(normalized)
        unique.append(normalized)
    return tuple(sorted(unique))


def _build_dashboard_row(
    kanji: str,
    *,
    snapshot: KanjiDashboardSnapshot,
    kanji_ranks: Mapping[str, float],
    model_names: tuple[str, ...],
    search_field_name: str,
) -> KanjiDashboardRow:
    stats = _build_kanji_stats(snapshot, kanji)
    return KanjiDashboardRow(
        kanji=kanji,
        jiten_rank=kanji_ranks.get(kanji),
        collection_expression_count=len(stats.collection_expressions),
        suspended_expression_count=len(stats.affected_suspended_expressions),
        active_recurring_expression_count=len(stats.active_recurring_expressions),
        mature_support_count=len(stats.mature_supporting_expressions),
        support_deficit=stats.support_deficit,
        is_unknown=stats.is_unknown,
        browser_search=build_browser_search(
            kanji,
            model_names=model_names,
            search_field_name=search_field_name,
        ),
    )


def _build_kanji_stats(
    snapshot: KanjiDashboardSnapshot,
    kanji: str,
) -> KanjiStats:
    collection_expressions = snapshot.collection_index.get(kanji, ())
    affected_suspended_expressions = snapshot.suspended_index.get(kanji, ())
    mature_supporting_expressions = snapshot.mature_index.get(kanji, ())
    mature_supporting_expression_set = set(mature_supporting_expressions)
    active_recurring_expressions = tuple(
        expression
        for expression in snapshot.active_index.get(kanji, ())
        if expression not in mature_supporting_expression_set
    )
    mature_support_count = len(mature_supporting_expressions)
    return KanjiStats(
        collection_expressions=collection_expressions,
        affected_suspended_expressions=affected_suspended_expressions,
        active_recurring_expressions=active_recurring_expressions,
        mature_supporting_expressions=mature_supporting_expressions,
        support_deficit=max(snapshot.threshold - mature_support_count, 0),
        is_unknown=bool(affected_suspended_expressions)
        and mature_support_count < snapshot.threshold,
    )


def _normalize_expression(expression: str) -> str:
    return normalize_lookup_text(strip_html_text(expression or ""))


def _normalize_kanji_key(value: str) -> str:
    normalized = _normalize_expression(value)
    chars = extract_kanji_chars(normalized)
    if len(chars) == 1:
        return chars[0]
    return normalized


def _get_note_field(note: Any, field_name: str) -> str:
    try:
        return str(note[field_name])
    except KeyError:
        return ""


def _average_rank(ranks: Iterable[float | None]) -> float | None:
    usable = [float(rank) for rank in ranks if rank is not None]
    if not usable:
        return None
    return sum(usable) / len(usable)


def _overview_row_sort_key(row: KanjiDashboardRow) -> tuple[tuple[int, float], str]:
    if row.jiten_rank is None:
        rank_key = (1, float("inf"))
    else:
        rank_key = (0, row.jiten_rank)
    return (rank_key, row.kanji)


def _problem_seed_sort_key(seed: ProblemKanjiSeed) -> tuple[int, int, int, tuple[int, float], str]:
    if seed.jiten_rank is None:
        rank_key = (1, float("inf"))
    else:
        rank_key = (0, seed.jiten_rank)
    return (
        -len(seed.affected_suspended_expressions),
        -seed.support_deficit,
        -len(seed.active_recurring_expressions),
        rank_key,
        seed.kanji,
    )


def _build_model_query(model_names: tuple[str, ...]) -> str:
    return " or ".join(f'note:"{name}"' for name in model_names if name)


def _serialize_rank(value: float | None) -> int | float | None:
    if value is None:
        return None
    if float(value).is_integer():
        return int(value)
    return value
