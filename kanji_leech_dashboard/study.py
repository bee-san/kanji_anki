from __future__ import annotations

from dataclasses import dataclass
import json
from pathlib import Path
import secrets
import sqlite3
import time
from typing import Any, Callable, Iterable, Mapping

from .config import DEFAULT_MATURE_DAYS
from .dashboard import ProblemKanjiSeed
from .normalization import extract_kanji_chars
from .state import ensure_user_files_dir
from .study_content import KanjiStudyContent, StudyContentProvider

RETENTION_TARGET = 0.9
MATURE_ANKI_CARD_RETIREMENT_THRESHOLD = 3
SECONDS_PER_DAY = 86400
FIRST_SCHEDULED_LEARNING_SECONDS = 10 * 60
MAX_ACTIVE_QUEUE_ITEMS = 25
MAX_NEW_ITEMS_PER_DAY = 3
RETIREMENT_INTERVAL_DAYS = 30
RETIREMENT_RECENT_SUPPORT_DAYS = 60
RETIREMENT_REACTIVATION_DAYS = 90
GUIDE_LABELS = {
    0: "Trace",
    1: "Outline",
    2: "Minimal hints",
    3: "Blind recall",
}
GUIDE_MODES = {
    0: "trace",
    1: "outline",
    2: "minimal-hints",
    3: "blind-recall",
}
RATING_ORDER = ("again", "hard", "good", "easy")
FAIL_RATINGS = {"again", "fail"}
PASS_RATINGS = {"hard", "good", "easy", "pass"}
RATINGS = FAIL_RATINGS | PASS_RATINGS
HANDWRITING_FAIL_ALLOWED_RATINGS = ("again",)
REVIEW_STATUSES = {"learning", "review"}
ACTIVE_STATUSES = {"new", "learning", "review"}
YOUNG_REVIEW_CYCLE = (
    "context-production",
    "confusable-recognition",
    "handwriting",
)
MATURE_REVIEW_CYCLE = (
    "context-production-a",
    "context-production-b",
    "confusable-recognition",
    "handwriting",
)
FONT_VARIANTS = {
    "canonical": {
        "label": "Canonical print",
        "family": '"Yu Gothic", "Hiragino Sans", "Noto Sans CJK JP", sans-serif',
    },
    "print-serif": {
        "label": "Mincho print",
        "family": '"Yu Mincho", "Hiragino Mincho ProN", "Noto Serif CJK JP", serif',
    },
    "print-sans": {
        "label": "Gothic print",
        "family": '"Yu Gothic", "Hiragino Sans", "Noto Sans CJK JP", sans-serif',
    },
    "handwritten": {
        "label": "Handwriting-like",
        "family": '"Klee One", "HanziPen SC", "Comic Sans MS", cursive',
    },
}


@dataclass(frozen=True)
class StudyItemSnapshot:
    profile: str
    kanji: str
    item_status: str
    due_ts: int
    is_problem_seed: bool
    guide_level: int
    consecutive_writing_successes: int
    consecutive_writing_failures: int
    stability: float
    difficulty: float
    total_reviews: int
    total_lapses: int
    last_prompt_type: str | None
    latest_problem_snapshot: dict[str, Any]
    priority_suspended_count: int
    priority_support_deficit: int
    priority_active_recurring_count: int
    priority_rank: float | None
    created_ts: int
    updated_ts: int
    last_reviewed_ts: int | None
    active_review_token: str | None
    active_prompt_type: str | None
    active_session_issued_ts: int | None
    learning_step: int
    review_cycle_index: int
    first_introduced_ts: int | None
    inactive_reason: str | None
    retired_ts: int | None
    retirement_context: dict[str, Any]


class StudyItemNotFoundError(LookupError):
    pass


class StudyService:
    def __init__(
        self,
        *,
        db_path: Path | None = None,
        content_provider: StudyContentProvider | None = None,
        now_factory: Callable[[], int] | None = None,
        token_factory: Callable[[], str] | None = None,
    ) -> None:
        if content_provider is None:
            user_files_dir = ensure_user_files_dir()
            provider = StudyContentProvider(user_files_dir=user_files_dir)
        else:
            provider = content_provider
            user_files_dir = db_path.parent if db_path is not None else ensure_user_files_dir()
        self._db_path = db_path or (user_files_dir / "study.sqlite3")
        self._db_path.parent.mkdir(parents=True, exist_ok=True)
        self._content_provider = provider
        self._now_factory = now_factory or (lambda: int(time.time()))
        self._token_factory = token_factory or (lambda: secrets.token_urlsafe(18))
        self._ensure_schema()

    def get_attribution(self) -> dict[str, object]:
        return self._content_provider.attribution()

    def overview(
        self,
        profile: str,
        *,
        current_problem_seed_count: int = 0,
        connection: sqlite3.Connection | None = None,
    ) -> dict[str, Any]:
        now_ts = self._now()
        own_connection = connection is None
        conn = connection or self._connect()
        try:
            due_count = int(
                conn.execute(
                    """
                    SELECT COUNT(*)
                    FROM study_items
                    WHERE profile = ?
                      AND item_status IN ('learning', 'review')
                      AND due_ts <= ?
                    """,
                    (profile, now_ts),
                ).fetchone()[0]
            )
            new_count = int(
                conn.execute(
                    """
                    SELECT COUNT(*)
                    FROM study_items
                    WHERE profile = ?
                      AND item_status = 'new'
                      AND is_problem_seed = 1
                    """,
                    (profile,),
                ).fetchone()[0]
            )
            active_queue_count = int(
                conn.execute(
                    """
                    SELECT COUNT(*)
                    FROM study_items
                    WHERE profile = ?
                      AND item_status IN ('new', 'learning', 'review')
                    """,
                    (profile,),
                ).fetchone()[0]
            )
            current_problem_seed_count = int(
                conn.execute(
                    """
                    SELECT COUNT(*)
                    FROM study_items
                    WHERE profile = ?
                      AND is_problem_seed = 1
                      AND item_status IN ('new', 'learning', 'review')
                    """,
                    (profile,),
                ).fetchone()[0]
            )
            inactive_count = int(
                conn.execute(
                    """
                    SELECT COUNT(*)
                    FROM study_items
                    WHERE profile = ?
                      AND item_status = 'inactive'
                    """,
                    (profile,),
                ).fetchone()[0]
            )
            next_due_row = conn.execute(
                """
                SELECT due_ts
                FROM study_items
                WHERE profile = ?
                  AND item_status IN ('learning', 'review')
                ORDER BY due_ts ASC
                LIMIT 1
                """,
                (profile,),
            ).fetchone()
            preview_rows = conn.execute(
                """
                SELECT *
                FROM study_items
                WHERE profile = ?
                  AND item_status IN ('new', 'learning', 'review')
                ORDER BY
                  CASE
                    WHEN item_status = 'learning' AND due_ts <= ? THEN 0
                    WHEN item_status = 'review' AND due_ts <= ? THEN 1
                    WHEN item_status = 'learning' THEN 2
                    WHEN item_status = 'review' THEN 3
                    ELSE 4
                  END ASC,
                  due_ts ASC,
                  priority_suspended_count DESC,
                  priority_support_deficit DESC,
                  priority_active_recurring_count DESC,
                  CASE WHEN priority_rank IS NULL THEN 1 ELSE 0 END ASC,
                  priority_rank ASC,
                  kanji ASC
                LIMIT 8
                """,
                (profile, now_ts, now_ts),
            ).fetchall()
            return {
                "dueCount": due_count,
                "newCount": new_count,
                "activeQueueCount": active_queue_count,
                "inactiveCount": inactive_count,
                "currentProblemSeedCount": current_problem_seed_count,
                "retentionTarget": RETENTION_TARGET,
                "liveQueueCap": MAX_ACTIVE_QUEUE_ITEMS,
                "dailyNewLimit": MAX_NEW_ITEMS_PER_DAY,
                "nextDueAt": _ts_to_iso(next_due_row["due_ts"]) if next_due_row else None,
                "queuePreview": [
                    self._serialize_queue_preview_row(_row_to_snapshot(row), now_ts)
                    for row in preview_rows
                ],
                "attribution": self.get_attribution(),
            }
        finally:
            if own_connection:
                conn.close()

    def sync_problem_kanji(
        self,
        profile: str,
        seeds: Iterable[ProblemKanjiSeed],
        *,
        mature_anki_card_counts: Mapping[str, int] | None = None,
        anki_support_lookup: Mapping[str, Mapping[str, Any]] | None = None,
        srs_mature_days: int = DEFAULT_MATURE_DAYS,
    ) -> dict[str, Any]:
        now_ts = self._now()
        normalized_seeds = list(seeds)
        support_lookup = _normalize_anki_support_lookup(
            anki_support_lookup=anki_support_lookup,
            mature_anki_card_counts=mature_anki_card_counts,
        )
        mature_days = max(1, int(srs_mature_days or DEFAULT_MATURE_DAYS))
        conn = self._connect()
        introduced_count = 0
        updated_count = 0
        reactivated_count = 0
        inactivated_count = 0
        try:
            existing_rows = {
                row["kanji"]: row
                for row in conn.execute(
                    "SELECT * FROM study_items WHERE profile = ?",
                    (profile,),
                ).fetchall()
            }
            active_count = sum(
                1
                for row in existing_rows.values()
                if str(row["item_status"]) in ACTIVE_STATUSES
            )
            active_seed_kanji = {seed.kanji for seed in normalized_seeds}
            for seed in normalized_seeds:
                snapshot_json = json.dumps(seed.to_dict(), ensure_ascii=False, sort_keys=True)
                support_summary = support_lookup.get(seed.kanji, _empty_support_summary())
                existing = existing_rows.get(seed.kanji)
                if existing is None:
                    item_status = "new" if active_count < MAX_ACTIVE_QUEUE_ITEMS else "inactive"
                    inactive_reason = None if item_status == "new" else "queue-cap"
                    conn.execute(
                        """
                        INSERT INTO study_items (
                          profile,
                          kanji,
                          due_ts,
                          item_status,
                          is_problem_seed,
                          guide_level,
                          consecutive_writing_successes,
                          consecutive_writing_failures,
                          stability,
                          difficulty,
                          total_reviews,
                          total_lapses,
                          last_prompt_type,
                          latest_problem_snapshot_json,
                          priority_suspended_count,
                          priority_support_deficit,
                          priority_active_recurring_count,
                          priority_rank,
                          created_ts,
                          updated_ts,
                          last_reviewed_ts,
                          active_review_token,
                          active_prompt_type,
                          active_session_issued_ts,
                          learning_step,
                          review_cycle_index,
                          first_introduced_ts,
                          inactive_reason,
                          retired_ts,
                          retirement_context_json
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                        (
                            profile,
                            seed.kanji,
                            now_ts,
                            item_status,
                            1,
                            0,
                            0,
                            0,
                            1.2,
                            5.5,
                            0,
                            0,
                            None,
                            snapshot_json,
                            len(seed.affected_suspended_expressions),
                            seed.support_deficit,
                            len(seed.active_recurring_expressions),
                            seed.jiten_rank,
                            now_ts,
                            now_ts,
                            None,
                            None,
                            None,
                            None,
                            0,
                            0,
                            None,
                            inactive_reason,
                            None,
                            "{}",
                        ),
                    )
                    if item_status == "new":
                        active_count += 1
                    introduced_count += 1
                    continue

                snapshot = _row_to_snapshot(existing)
                was_active = snapshot.item_status in ACTIVE_STATUSES
                next_status = snapshot.item_status
                learning_step = snapshot.learning_step
                review_cycle_index = snapshot.review_cycle_index
                inactive_reason = snapshot.inactive_reason
                retired_ts = snapshot.retired_ts
                retirement_context = snapshot.retirement_context
                due_ts = snapshot.due_ts

                if _should_retire_for_mature_anki_support(
                    snapshot,
                    support_summary=support_summary,
                    mature_days=mature_days,
                    now_ts=now_ts,
                ):
                    next_status = "inactive"
                    learning_step = 0
                    review_cycle_index = snapshot.review_cycle_index
                    inactive_reason = "retired"
                    retired_ts = now_ts
                    retirement_context = _retirement_context_payload(support_summary)
                elif _should_reactivate_for_support_collapse(
                    snapshot,
                    support_summary=support_summary,
                    now_ts=now_ts,
                ):
                    if not was_active and active_count < MAX_ACTIVE_QUEUE_ITEMS:
                        next_status = "learning"
                        learning_step = 0
                        review_cycle_index = 0
                        inactive_reason = None
                        retired_ts = None
                        retirement_context = {}
                        due_ts = now_ts
                    elif not was_active:
                        next_status = "inactive"
                        inactive_reason = "queue-cap"
                elif next_status == "inactive":
                    if active_count < MAX_ACTIVE_QUEUE_ITEMS:
                        next_status = "new"
                        learning_step = 0
                        review_cycle_index = snapshot.review_cycle_index
                        inactive_reason = None
                        retired_ts = None
                        retirement_context = {}
                        due_ts = now_ts
                    else:
                        inactive_reason = "queue-cap"
                else:
                    updated_count += 1

                if was_active and next_status == "inactive":
                    active_count -= 1
                    inactivated_count += 1
                elif not was_active and next_status in ACTIVE_STATUSES:
                    active_count += 1
                    reactivated_count += 1

                conn.execute(
                    """
                    UPDATE study_items
                    SET due_ts = ?,
                        item_status = ?,
                        is_problem_seed = 1,
                        latest_problem_snapshot_json = ?,
                        priority_suspended_count = ?,
                        priority_support_deficit = ?,
                        priority_active_recurring_count = ?,
                        priority_rank = ?,
                        learning_step = ?,
                        review_cycle_index = ?,
                        inactive_reason = ?,
                        retired_ts = ?,
                        retirement_context_json = ?,
                        active_review_token = ?,
                        active_prompt_type = ?,
                        active_session_issued_ts = ?,
                        updated_ts = ?
                    WHERE profile = ? AND kanji = ?
                    """,
                    (
                        due_ts,
                        next_status,
                        snapshot_json,
                        len(seed.affected_suspended_expressions),
                        seed.support_deficit,
                        len(seed.active_recurring_expressions),
                        seed.jiten_rank,
                        learning_step,
                        review_cycle_index,
                        inactive_reason,
                        retired_ts,
                        json.dumps(retirement_context, ensure_ascii=False, sort_keys=True),
                        None if next_status == "inactive" else snapshot.active_review_token,
                        None if next_status == "inactive" else snapshot.active_prompt_type,
                        None if next_status == "inactive" else snapshot.active_session_issued_ts,
                        now_ts,
                        profile,
                        seed.kanji,
                    ),
                )

            for kanji, row in existing_rows.items():
                if not row["is_problem_seed"] or kanji in active_seed_kanji:
                    continue
                snapshot = _row_to_snapshot(row)
                support_summary = support_lookup.get(kanji, _empty_support_summary())
                was_active = snapshot.item_status in ACTIVE_STATUSES
                next_status = snapshot.item_status
                learning_step = snapshot.learning_step
                inactive_reason = snapshot.inactive_reason
                retired_ts = snapshot.retired_ts
                retirement_context = snapshot.retirement_context
                due_ts = snapshot.due_ts

                if _should_retire_for_mature_anki_support(
                    snapshot,
                    support_summary=support_summary,
                    mature_days=mature_days,
                    now_ts=now_ts,
                ):
                    next_status = "inactive"
                    learning_step = 0
                    inactive_reason = "retired"
                    retired_ts = now_ts
                    retirement_context = _retirement_context_payload(support_summary)
                elif _should_reactivate_for_support_collapse(
                    snapshot,
                    support_summary=support_summary,
                    now_ts=now_ts,
                ):
                    if not was_active and active_count < MAX_ACTIVE_QUEUE_ITEMS:
                        next_status = "learning"
                        learning_step = 0
                        inactive_reason = None
                        retired_ts = None
                        retirement_context = {}
                        due_ts = now_ts
                    elif not was_active:
                        next_status = "inactive"
                        inactive_reason = "queue-cap"
                elif next_status == "new":
                    next_status = "inactive"
                    inactive_reason = "seed-dropped"
                if was_active and next_status == "inactive":
                    active_count -= 1
                    inactivated_count += 1
                elif not was_active and next_status in ACTIVE_STATUSES:
                    active_count += 1
                    reactivated_count += 1
                conn.execute(
                    """
                    UPDATE study_items
                    SET due_ts = ?,
                        item_status = ?,
                        is_problem_seed = 0,
                        learning_step = ?,
                        inactive_reason = ?,
                        retired_ts = ?,
                        retirement_context_json = ?,
                        active_review_token = ?,
                        active_prompt_type = ?,
                        active_session_issued_ts = ?,
                        updated_ts = ?
                    WHERE profile = ? AND kanji = ?
                    """,
                    (
                        due_ts,
                        next_status,
                        learning_step,
                        inactive_reason,
                        retired_ts,
                        json.dumps(retirement_context, ensure_ascii=False, sort_keys=True),
                        None if next_status == "inactive" else snapshot.active_review_token,
                        None if next_status == "inactive" else snapshot.active_prompt_type,
                        None if next_status == "inactive" else snapshot.active_session_issued_ts,
                        now_ts,
                        profile,
                        kanji,
                    ),
                )

            conn.commit()
            overview = self.overview(
                profile,
                current_problem_seed_count=len(normalized_seeds),
                connection=conn,
            )
            return {
                "introducedCount": introduced_count,
                "updatedCount": updated_count,
                "reactivatedCount": reactivated_count,
                "inactivatedCount": inactivated_count,
                "currentProblemSeedCount": len(normalized_seeds),
                "overview": overview,
            }
        finally:
            conn.close()

    def next_session(self, profile: str, *, mode: str = "mixed") -> dict[str, Any]:
        now_ts = self._now()
        conn = self._connect()
        try:
            row = self._select_active_row(conn, profile)
            if row is None:
                row = self._select_next_row(conn, profile, now_ts, mode=mode)
            if row is None:
                return {
                    "available": False,
                    "message": "No due bridge reviews or new kanji introductions are waiting.",
                }
            snapshot = _row_to_snapshot(row)
            content = self._content_provider.get_content(snapshot.kanji)
            task = self._describe_task(snapshot, content)
            prompt_type = snapshot.active_prompt_type or task["promptType"]
            review_token = snapshot.active_review_token
            if review_token is None or snapshot.active_prompt_type != prompt_type:
                review_token = self._token_factory()
                first_introduced_ts = snapshot.first_introduced_ts
                if snapshot.item_status == "new" and first_introduced_ts is None:
                    first_introduced_ts = now_ts
                conn.execute(
                    """
                    UPDATE study_items
                    SET active_review_token = ?,
                        active_prompt_type = ?,
                        active_session_issued_ts = ?,
                        first_introduced_ts = ?,
                        updated_ts = ?
                    WHERE profile = ? AND kanji = ?
                    """,
                    (
                        review_token,
                        prompt_type,
                        now_ts,
                        first_introduced_ts,
                        now_ts,
                        profile,
                        snapshot.kanji,
                    ),
                )
                conn.commit()
                snapshot = self._require_item(conn, profile, snapshot.kanji)
            requires_writing = bool(task["requiresWriting"])
            item_payload = self._serialize_item(snapshot, now_ts, content=content, task=task)
            session = {
                **item_payload,
                "reviewToken": review_token,
                "promptType": prompt_type,
                "promptLabel": str(task["promptLabel"]),
                "requiresWriting": requires_writing,
                "taskKind": str(task["taskKind"]),
                "schedulerPhase": str(task["schedulerPhase"]),
                "outcomeMode": "binary",
                "allowedOutcomes": ["fail", "pass"],
                "handwritingPolicy": _build_handwriting_policy(
                    snapshot,
                    prompt_type=prompt_type,
                    requires_writing=requires_writing,
                    stroke_geometry_available=bool(
                        item_payload.get("content", {})
                        .get("strokeOrder", {})
                        .get("available")
                    ),
                ),
                "sessionKind": "review" if snapshot.item_status == "review" else "learn",
            }
            return {"available": True, "session": session}
        finally:
            conn.close()

    def get_item_detail(self, profile: str, kanji: str) -> dict[str, Any]:
        conn = self._connect()
        try:
            row = conn.execute(
                "SELECT * FROM study_items WHERE profile = ? AND kanji = ?",
                (profile, kanji),
            ).fetchone()
            if row is None:
                raise StudyItemNotFoundError(f"No study item exists for kanji '{kanji}'.")
            snapshot = _row_to_snapshot(row)
            content = self._content_provider.get_content(snapshot.kanji)
            task = self._describe_task(snapshot, content)
            return self._serialize_item(snapshot, self._now(), content=content, task=task)
        finally:
            conn.close()

    def review(
        self,
        profile: str,
        *,
        kanji: str,
        review_token: str,
        prompt_type: str,
        rating: str,
        handwriting_result: Mapping[str, Any] | None = None,
        hints_used: int = 0,
    ) -> dict[str, Any]:
        normalized_rating = _normalize_rating(rating)
        if normalized_rating not in RATINGS:
            raise ValueError("rating must be one of again, fail, hard, good, easy, or pass.")
        if not review_token:
            raise ValueError("reviewToken is required.")
        normalized_prompt_type = str(prompt_type or "").strip().lower()
        if normalized_prompt_type not in {"recognition", "production"}:
            raise ValueError("promptType must be recognition or production.")

        now_ts = self._now()
        conn = self._connect()
        try:
            existing_log = conn.execute(
                """
                SELECT reviewed_ts
                FROM study_review_log
                WHERE profile = ? AND review_token = ?
                """,
                (profile, review_token),
            ).fetchone()
            if existing_log is not None:
                snapshot = self._require_item(conn, profile, kanji)
                return {
                    "duplicate": True,
                    "reviewedAt": _ts_to_iso(existing_log["reviewed_ts"]),
                    "item": self._serialize_item(snapshot, now_ts),
                    "overview": self.overview(profile, connection=conn),
                }

            snapshot = self._require_item(conn, profile, kanji)
            if snapshot.active_review_token != review_token:
                raise ValueError(
                    "reviewToken does not match the active Study session for this kanji."
                )
            if snapshot.active_prompt_type and snapshot.active_prompt_type != normalized_prompt_type:
                raise ValueError(
                    "promptType does not match the active Study session for this kanji."
                )

            content = self._content_provider.get_content(snapshot.kanji)
            task = self._describe_task(snapshot, content)
            effective_prompt_type = snapshot.active_prompt_type or str(task["promptType"])
            writing = _coerce_mapping(handwriting_result)
            attempted = bool(writing.get("attempted"))
            passed_writing = bool(writing.get("passed"))
            passed_review = _is_pass_rating(normalized_rating)
            _validate_handwriting_review(
                snapshot,
                prompt_type=effective_prompt_type,
                rating=normalized_rating,
                attempted=attempted,
                passed=passed_writing,
            )
            score = _coerce_float(writing.get("score"), default=0.0)
            used_hints = _coerce_int(hints_used, writing.get("hintsUsed"), default=0)
            guide_before = snapshot.guide_level
            guide_after, success_streak, failure_streak = _update_guide_progression(
                snapshot,
                attempted=attempted,
                passed=passed_writing,
            )

            due_ts = snapshot.due_ts
            next_status = snapshot.item_status
            learning_step = snapshot.learning_step
            review_cycle_index = snapshot.review_cycle_index
            stability = snapshot.stability
            difficulty = snapshot.difficulty
            total_lapses = snapshot.total_lapses

            if snapshot.item_status == "new":
                due_ts = now_ts
                next_status = "learning"
                learning_step = 1
                stability = _adjust_packet_stability(snapshot.stability, passed_review)
                difficulty = _adjust_difficulty(snapshot.difficulty, passed_review)
            elif snapshot.item_status == "learning" and snapshot.learning_step == 1:
                stability = _adjust_packet_stability(snapshot.stability, passed_review)
                difficulty = _adjust_difficulty(snapshot.difficulty, passed_review)
                if passed_review:
                    due_ts = now_ts + FIRST_SCHEDULED_LEARNING_SECONDS
                    next_status = "learning"
                    learning_step = 2
                else:
                    due_ts = now_ts
                    next_status = "learning"
                    learning_step = 0
            elif snapshot.item_status == "learning" and snapshot.learning_step == 2:
                due_ts, next_status, stability, difficulty, total_lapses = _schedule_after_review(
                    snapshot,
                    rating="good" if passed_review else "again",
                    now_ts=now_ts,
                )
                if passed_review:
                    learning_step = 0
                    review_cycle_index = 1
                else:
                    due_ts = now_ts
                    next_status = "learning"
                    learning_step = 0
                    review_cycle_index = 0
            else:
                due_ts, next_status, stability, difficulty, total_lapses = _schedule_after_review(
                    snapshot,
                    rating="good" if passed_review else "again",
                    now_ts=now_ts,
                )
                if passed_review:
                    learning_step = 0
                    review_cycle_index = (snapshot.review_cycle_index + 1) % len(
                        _review_cycle(snapshot)
                    )
                else:
                    due_ts = now_ts
                    next_status = "learning"
                    learning_step = 0
                    review_cycle_index = 0

            total_reviews = snapshot.total_reviews + 1
            conn.execute(
                """
                UPDATE study_items
                SET due_ts = ?,
                    item_status = ?,
                    guide_level = ?,
                    consecutive_writing_successes = ?,
                    consecutive_writing_failures = ?,
                    stability = ?,
                    difficulty = ?,
                    total_reviews = ?,
                    total_lapses = ?,
                    last_prompt_type = ?,
                    updated_ts = ?,
                    last_reviewed_ts = ?,
                    active_review_token = ?,
                    active_prompt_type = ?,
                    active_session_issued_ts = ?,
                    learning_step = ?,
                    review_cycle_index = ?,
                    inactive_reason = ?,
                    retired_ts = ?,
                    retirement_context_json = ?
                WHERE profile = ? AND kanji = ?
                """,
                (
                    due_ts,
                    next_status,
                    guide_after,
                    success_streak,
                    failure_streak,
                    stability,
                    difficulty,
                    total_reviews,
                    total_lapses,
                    str(task["taskKind"]),
                    now_ts,
                    now_ts,
                    None,
                    None,
                    None,
                    learning_step,
                    review_cycle_index,
                    None,
                    None,
                    "{}",
                    profile,
                    kanji,
                ),
            )
            conn.execute(
                """
                INSERT INTO study_review_log (
                  profile,
                  kanji,
                  review_token,
                  reviewed_ts,
                  prompt_type,
                  srs_rating,
                  handwriting_passed,
                  handwriting_score,
                  guide_level_before,
                  guide_level_after,
                  hints_used,
                  review_payload_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    profile,
                    kanji,
                    review_token,
                    now_ts,
                    effective_prompt_type,
                    "good" if passed_review else "again",
                    1 if passed_writing else 0,
                    score,
                    guide_before,
                    guide_after,
                    used_hints,
                    json.dumps(
                        {
                            "handwritingResult": writing,
                            "rating": normalized_rating,
                            "promptType": effective_prompt_type,
                            "taskKind": task["taskKind"],
                            "schedulerPhase": task["schedulerPhase"],
                            "binaryOutcome": "pass" if passed_review else "fail",
                        },
                        ensure_ascii=False,
                        sort_keys=True,
                    ),
                ),
            )
            conn.commit()
            updated_snapshot = self._require_item(conn, profile, kanji)
            updated_content = self._content_provider.get_content(updated_snapshot.kanji)
            updated_task = self._describe_task(updated_snapshot, updated_content)
            return {
                "duplicate": False,
                "reviewedAt": _ts_to_iso(now_ts),
                "binaryOutcome": "pass" if passed_review else "fail",
                "item": self._serialize_item(
                    updated_snapshot,
                    now_ts,
                    content=updated_content,
                    task=updated_task,
                ),
                "overview": self.overview(profile, connection=conn),
            }
        finally:
            conn.close()

    def _serialize_queue_preview_row(
        self,
        snapshot: StudyItemSnapshot,
        now_ts: int,
    ) -> dict[str, Any]:
        problem = snapshot.latest_problem_snapshot
        return {
            "kanji": snapshot.kanji,
            "itemStatus": snapshot.item_status,
            "dueAt": _ts_to_iso(snapshot.due_ts),
            "dueNow": snapshot.item_status in REVIEW_STATUSES and snapshot.due_ts <= now_ts,
            "guideLevel": snapshot.guide_level,
            "guideLevelLabel": GUIDE_LABELS.get(snapshot.guide_level, GUIDE_LABELS[3]),
            "supportDeficit": int(problem.get("supportDeficit") or 0),
            "suspendedExpressionCount": int(problem.get("suspendedExpressionCount") or 0),
            "activeRecurringExpressionCount": int(
                problem.get("activeRecurringExpressionCount") or 0
            ),
            "isProblemSeed": snapshot.is_problem_seed,
        }

    def _serialize_item(
        self,
        snapshot: StudyItemSnapshot,
        now_ts: int,
        *,
        content: KanjiStudyContent | None = None,
        task: Mapping[str, Any] | None = None,
    ) -> dict[str, Any]:
        content = content or self._content_provider.get_content(snapshot.kanji)
        task = task or self._describe_task(snapshot, content)
        problem = snapshot.latest_problem_snapshot
        pain_examples = _coerce_str_list(problem.get("affectedSuspendedExpressions"))
        bridge_examples = _coerce_str_list(problem.get("activeRecurringExpressions"))
        mature_examples = _coerce_str_list(problem.get("matureSupportingExpressions"))
        collection_examples = _coerce_str_list(problem.get("collectionExpressions"))
        support_words = _choose_support_words(
            pain_examples=pain_examples,
            bridge_examples=bridge_examples,
            mature_examples=mature_examples,
            collection_examples=collection_examples,
        )
        confusables = _choose_confusables(
            snapshot.kanji,
            collection_examples,
            preferred_examples=support_words,
        )
        current_problem_child = bool(problem.get("suspendedExpressionCount")) and (
            int(problem.get("supportDeficit") or 0) > 0
        )
        font_variant = str(task.get("fontVariant") or "canonical")
        font_payload = FONT_VARIANTS.get(font_variant, FONT_VARIANTS["canonical"])
        recognition_context = _unique_preserve_order(
            [support_words[0], *confusables[:3], support_words[1]]
        )
        production_context = _unique_preserve_order(
            [support_words[0], support_words[1]]
        )
        return {
            "kanji": snapshot.kanji,
            "itemStatus": snapshot.item_status,
            "isProblemSeed": snapshot.is_problem_seed,
            "isCurrentProblemChild": current_problem_child,
            "dueAt": _ts_to_iso(snapshot.due_ts),
            "dueNow": snapshot.item_status in REVIEW_STATUSES and snapshot.due_ts <= now_ts,
            "guideLevel": snapshot.guide_level,
            "guideLevelLabel": GUIDE_LABELS.get(snapshot.guide_level, GUIDE_LABELS[3]),
            "reviewCount": snapshot.total_reviews,
            "lapseCount": snapshot.total_lapses,
            "stability": round(snapshot.stability, 3),
            "difficulty": round(snapshot.difficulty, 3),
            "reviewCycleIndex": snapshot.review_cycle_index,
            "learningStep": snapshot.learning_step,
            "inactiveReason": snapshot.inactive_reason,
            "lastReviewedAt": _ts_to_iso(snapshot.last_reviewed_ts)
            if snapshot.last_reviewed_ts
            else None,
            "content": {
                **content.to_dict(),
                "fontVariant": font_variant,
                "fontVariantLabel": font_payload["label"],
                "fontFamily": font_payload["family"],
                "strokeOrder": {
                    "available": bool(content.stroke_paths),
                    "strokeCount": content.stroke_count,
                    "paths": list(content.stroke_paths),
                    "guideLevel": snapshot.guide_level,
                    "guideLevelLabel": GUIDE_LABELS.get(snapshot.guide_level, GUIDE_LABELS[3]),
                },
            },
            "support": {
                "collectionExpressionCount": int(problem.get("collectionExpressionCount") or 0),
                "suspendedExpressionCount": int(problem.get("suspendedExpressionCount") or 0),
                "activeRecurringExpressionCount": int(
                    problem.get("activeRecurringExpressionCount") or 0
                ),
                "matureSupportCount": int(problem.get("matureSupportCount") or 0),
                "supportDeficit": int(problem.get("supportDeficit") or 0),
                "browserSearch": str(problem.get("browserSearch") or ""),
                "collectionExamples": collection_examples,
                "painExamples": pain_examples,
                "bridgeExamples": bridge_examples,
                "matureExamples": mature_examples,
                "supportWords": [value for value in support_words if value],
                "whyInQueue": _why_in_queue(snapshot, problem),
            },
            "prompts": {
                "recognition": {
                    "kanji": snapshot.kanji,
                    "context": recognition_context,
                    "instruction": str(task.get("recognitionInstruction") or ""),
                },
                "production": {
                    "keyword": str(task.get("productionKeyword") or content.keyword),
                    "context": production_context,
                    "instruction": str(task.get("productionInstruction") or ""),
                },
            },
            "answer": {
                "kanji": snapshot.kanji,
                "keyword": content.keyword,
                "meanings": list(content.meanings),
                "primaryReadings": list(content.primary_readings),
                "readings": list(content.readings),
                "painExample": pain_examples[0] if pain_examples else None,
                "bridgeExample": bridge_examples[0] if bridge_examples else None,
                "matureExample": mature_examples[0] if mature_examples else None,
                "components": list(content.components),
                "componentHint": content.component_hint,
                "confusables": confusables,
                "supportWords": [value for value in support_words if value],
            },
        }

    def _describe_task(
        self,
        snapshot: StudyItemSnapshot,
        content: KanjiStudyContent,
    ) -> dict[str, Any]:
        support_words = _choose_support_words(
            pain_examples=_coerce_str_list(snapshot.latest_problem_snapshot.get("affectedSuspendedExpressions")),
            bridge_examples=_coerce_str_list(snapshot.latest_problem_snapshot.get("activeRecurringExpressions")),
            mature_examples=_coerce_str_list(snapshot.latest_problem_snapshot.get("matureSupportingExpressions")),
            collection_examples=_coerce_str_list(snapshot.latest_problem_snapshot.get("collectionExpressions")),
        )
        task_kind = _task_kind_for_snapshot(snapshot)
        font_variant = _font_variant_for_task(snapshot, task_kind)
        if task_kind == "confusable-recognition":
            return {
                "taskKind": task_kind,
                "schedulerPhase": "acquisition"
                if snapshot.item_status != "review"
                else ("mature-review" if _is_bridge_mature(snapshot) else "young-review"),
                "promptType": "recognition",
                "promptLabel": "Confusable recognition",
                "requiresWriting": False,
                "fontVariant": font_variant,
                "recognitionInstruction": (
                    "Check the kanji in a different print style and distinguish it from nearby forms before you reveal the answer."
                ),
                "productionInstruction": "",
                "productionKeyword": content.keyword,
            }
        if task_kind == "handwriting":
            return {
                "taskKind": task_kind,
                "schedulerPhase": "mature-review" if _is_bridge_mature(snapshot) else "young-review",
                "promptType": "production",
                "promptLabel": "Handwriting review",
                "requiresWriting": True,
                "fontVariant": font_variant,
                "recognitionInstruction": "",
                "productionInstruction": (
                    "Reconstruct the kanji from memory in context. Use handwriting here as the insurance layer, not as brute-force copying."
                ),
                "productionKeyword": content.keyword,
            }
        if snapshot.item_status == "new":
            return {
                "taskKind": task_kind,
                "schedulerPhase": "acquisition",
                "promptType": "production",
                "promptLabel": "Acquisition cue",
                "requiresWriting": True,
                "fontVariant": "canonical",
                "recognitionInstruction": "",
                "productionInstruction": (
                    "Attempt the kanji from the cue first, then write it once from memory before you rate the result."
                ),
                "productionKeyword": content.keyword,
            }
        if snapshot.item_status == "learning":
            return {
                "taskKind": task_kind,
                "schedulerPhase": "scheduled-learning",
                "promptType": "production",
                "promptLabel": "Bridge review",
                "requiresWriting": True,
                "fontVariant": "canonical",
                "recognitionInstruction": "",
                "productionInstruction": (
                    "Rebuild the kanji again after the short delay. A clean pass here hands it to long-term review."
                ),
                "productionKeyword": content.keyword,
            }
        label = "Context production"
        if task_kind == "context-production-b":
            label = "Context production"
        return {
            "taskKind": task_kind,
            "schedulerPhase": "mature-review" if _is_bridge_mature(snapshot) else "young-review",
            "promptType": "production",
            "promptLabel": label,
            "requiresWriting": False,
            "fontVariant": font_variant,
            "recognitionInstruction": "",
            "productionInstruction": (
                "Recall the kanji inside the anchor vocabulary context before you reveal the answer."
            ),
            "productionKeyword": content.keyword,
        }

    def _select_next_row(
        self,
        conn: sqlite3.Connection,
        profile: str,
        now_ts: int,
        *,
        mode: str,
    ) -> sqlite3.Row | None:
        normalized_mode = str(mode or "mixed").strip().lower()
        if normalized_mode not in {"mixed", "review", "new"}:
            raise ValueError("mode must be one of mixed, review, or new.")

        learning_row = conn.execute(
            """
            SELECT *
            FROM study_items
            WHERE profile = ?
              AND item_status = 'learning'
              AND due_ts <= ?
            ORDER BY
              due_ts ASC,
              priority_suspended_count DESC,
              priority_support_deficit DESC,
              priority_active_recurring_count DESC,
              CASE WHEN priority_rank IS NULL THEN 1 ELSE 0 END ASC,
              priority_rank ASC,
              kanji ASC
            LIMIT 1
            """,
            (profile, now_ts),
        ).fetchone()
        if learning_row is not None:
            return learning_row

        if normalized_mode != "new":
            due_review_row = conn.execute(
                """
                SELECT *
                FROM study_items
                WHERE profile = ?
                  AND item_status = 'review'
                  AND due_ts <= ?
                ORDER BY
                  due_ts ASC,
                  priority_suspended_count DESC,
                  priority_support_deficit DESC,
                  priority_active_recurring_count DESC,
                  CASE WHEN priority_rank IS NULL THEN 1 ELSE 0 END ASC,
                  priority_rank ASC,
                  kanji ASC
                LIMIT 1
                """,
                (profile, now_ts),
            ).fetchone()
            if due_review_row is not None:
                return due_review_row
        if normalized_mode == "review":
            return None
        if self._introduced_today_count(conn, profile, now_ts) >= MAX_NEW_ITEMS_PER_DAY:
            return None
        return conn.execute(
            """
            SELECT *
            FROM study_items
            WHERE profile = ?
              AND item_status = 'new'
              AND is_problem_seed = 1
            ORDER BY
              priority_suspended_count DESC,
              priority_support_deficit DESC,
              priority_active_recurring_count DESC,
              CASE WHEN priority_rank IS NULL THEN 1 ELSE 0 END ASC,
              priority_rank ASC,
              kanji ASC
            LIMIT 1
            """,
            (profile,),
        ).fetchone()

    def _select_active_row(
        self,
        conn: sqlite3.Connection,
        profile: str,
    ) -> sqlite3.Row | None:
        return conn.execute(
            """
            SELECT *
            FROM study_items
            WHERE profile = ?
              AND active_review_token IS NOT NULL
            ORDER BY
              CASE
                WHEN active_session_issued_ts IS NULL THEN 1
                ELSE 0
              END ASC,
              active_session_issued_ts ASC,
              updated_ts ASC,
              kanji ASC
            LIMIT 1
            """,
            (profile,),
        ).fetchone()

    def _require_item(
        self,
        conn: sqlite3.Connection,
        profile: str,
        kanji: str,
    ) -> StudyItemSnapshot:
        row = conn.execute(
            "SELECT * FROM study_items WHERE profile = ? AND kanji = ?",
            (profile, kanji),
        ).fetchone()
        if row is None:
            raise StudyItemNotFoundError(f"No study item exists for kanji '{kanji}'.")
        return _row_to_snapshot(row)

    def _introduced_today_count(
        self,
        conn: sqlite3.Connection,
        profile: str,
        now_ts: int,
    ) -> int:
        day_start = now_ts - (now_ts % SECONDS_PER_DAY)
        row = conn.execute(
            """
            SELECT COUNT(*)
            FROM study_items
            WHERE profile = ?
              AND first_introduced_ts IS NOT NULL
              AND first_introduced_ts >= ?
            """,
            (profile, day_start),
        ).fetchone()
        return int(row[0]) if row is not None else 0

    def _ensure_schema(self) -> None:
        conn = self._connect()
        try:
            conn.executescript(
                """
                CREATE TABLE IF NOT EXISTS study_items (
                  profile TEXT NOT NULL,
                  kanji TEXT NOT NULL,
                  due_ts INTEGER NOT NULL,
                  item_status TEXT NOT NULL,
                  is_problem_seed INTEGER NOT NULL DEFAULT 1,
                  guide_level INTEGER NOT NULL DEFAULT 0,
                  consecutive_writing_successes INTEGER NOT NULL DEFAULT 0,
                  consecutive_writing_failures INTEGER NOT NULL DEFAULT 0,
                  stability REAL NOT NULL DEFAULT 1.2,
                  difficulty REAL NOT NULL DEFAULT 5.5,
                  total_reviews INTEGER NOT NULL DEFAULT 0,
                  total_lapses INTEGER NOT NULL DEFAULT 0,
                  last_prompt_type TEXT,
                  latest_problem_snapshot_json TEXT NOT NULL DEFAULT '{}',
                  priority_suspended_count INTEGER NOT NULL DEFAULT 0,
                  priority_support_deficit INTEGER NOT NULL DEFAULT 0,
                  priority_active_recurring_count INTEGER NOT NULL DEFAULT 0,
                  priority_rank REAL,
                  created_ts INTEGER NOT NULL,
                  updated_ts INTEGER NOT NULL,
                  last_reviewed_ts INTEGER,
                  active_review_token TEXT,
                  active_prompt_type TEXT,
                  active_session_issued_ts INTEGER,
                  learning_step INTEGER NOT NULL DEFAULT 0,
                  review_cycle_index INTEGER NOT NULL DEFAULT 0,
                  first_introduced_ts INTEGER,
                  inactive_reason TEXT,
                  retired_ts INTEGER,
                  retirement_context_json TEXT NOT NULL DEFAULT '{}',
                  PRIMARY KEY (profile, kanji)
                );

                CREATE TABLE IF NOT EXISTS study_review_log (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  profile TEXT NOT NULL,
                  kanji TEXT NOT NULL,
                  review_token TEXT NOT NULL,
                  reviewed_ts INTEGER NOT NULL,
                  prompt_type TEXT NOT NULL,
                  srs_rating TEXT NOT NULL,
                  handwriting_passed INTEGER NOT NULL,
                  handwriting_score REAL NOT NULL,
                  guide_level_before INTEGER NOT NULL,
                  guide_level_after INTEGER NOT NULL,
                  hints_used INTEGER NOT NULL DEFAULT 0,
                  review_payload_json TEXT NOT NULL
                );

                CREATE UNIQUE INDEX IF NOT EXISTS idx_study_review_token
                  ON study_review_log (profile, review_token);

                CREATE INDEX IF NOT EXISTS idx_study_due
                  ON study_items (profile, item_status, due_ts);
                """
            )
            self._ensure_column(conn, "study_items", "active_review_token", "TEXT")
            self._ensure_column(conn, "study_items", "active_prompt_type", "TEXT")
            self._ensure_column(conn, "study_items", "active_session_issued_ts", "INTEGER")
            self._ensure_column(conn, "study_items", "learning_step", "INTEGER NOT NULL DEFAULT 0")
            self._ensure_column(
                conn,
                "study_items",
                "review_cycle_index",
                "INTEGER NOT NULL DEFAULT 0",
            )
            self._ensure_column(conn, "study_items", "first_introduced_ts", "INTEGER")
            self._ensure_column(conn, "study_items", "inactive_reason", "TEXT")
            self._ensure_column(conn, "study_items", "retired_ts", "INTEGER")
            self._ensure_column(
                conn,
                "study_items",
                "retirement_context_json",
                "TEXT NOT NULL DEFAULT '{}'",
            )
            conn.commit()
        finally:
            conn.close()

    def _connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self._db_path)
        conn.row_factory = sqlite3.Row
        return conn

    def _now(self) -> int:
        return int(self._now_factory())

    def _ensure_column(
        self,
        conn: sqlite3.Connection,
        table_name: str,
        column_name: str,
        definition: str,
    ) -> None:
        existing_columns = {
            str(row["name"])
            for row in conn.execute(f"PRAGMA table_info({table_name})").fetchall()
        }
        if column_name in existing_columns:
            return
        conn.execute(f"ALTER TABLE {table_name} ADD COLUMN {column_name} {definition}")


def _row_to_snapshot(row: sqlite3.Row) -> StudyItemSnapshot:
    latest_snapshot_raw = str(row["latest_problem_snapshot_json"] or "{}")
    retirement_context_raw = str(row["retirement_context_json"] or "{}")
    try:
        latest_snapshot = json.loads(latest_snapshot_raw)
    except ValueError:
        latest_snapshot = {}
    if not isinstance(latest_snapshot, dict):
        latest_snapshot = {}
    try:
        retirement_context = json.loads(retirement_context_raw)
    except ValueError:
        retirement_context = {}
    if not isinstance(retirement_context, dict):
        retirement_context = {}
    return StudyItemSnapshot(
        profile=str(row["profile"]),
        kanji=str(row["kanji"]),
        item_status=str(row["item_status"]),
        due_ts=int(row["due_ts"]),
        is_problem_seed=bool(row["is_problem_seed"]),
        guide_level=int(row["guide_level"]),
        consecutive_writing_successes=int(row["consecutive_writing_successes"]),
        consecutive_writing_failures=int(row["consecutive_writing_failures"]),
        stability=float(row["stability"]),
        difficulty=float(row["difficulty"]),
        total_reviews=int(row["total_reviews"]),
        total_lapses=int(row["total_lapses"]),
        last_prompt_type=str(row["last_prompt_type"]) if row["last_prompt_type"] else None,
        latest_problem_snapshot=latest_snapshot,
        priority_suspended_count=int(row["priority_suspended_count"]),
        priority_support_deficit=int(row["priority_support_deficit"]),
        priority_active_recurring_count=int(row["priority_active_recurring_count"]),
        priority_rank=float(row["priority_rank"]) if row["priority_rank"] is not None else None,
        created_ts=int(row["created_ts"]),
        updated_ts=int(row["updated_ts"]),
        last_reviewed_ts=int(row["last_reviewed_ts"])
        if row["last_reviewed_ts"] is not None
        else None,
        active_review_token=str(row["active_review_token"])
        if row["active_review_token"] is not None
        else None,
        active_prompt_type=str(row["active_prompt_type"])
        if row["active_prompt_type"] is not None
        else None,
        active_session_issued_ts=int(row["active_session_issued_ts"])
        if row["active_session_issued_ts"] is not None
        else None,
        learning_step=int(row["learning_step"]) if row["learning_step"] is not None else 0,
        review_cycle_index=int(row["review_cycle_index"])
        if row["review_cycle_index"] is not None
        else 0,
        first_introduced_ts=int(row["first_introduced_ts"])
        if row["first_introduced_ts"] is not None
        else None,
        inactive_reason=str(row["inactive_reason"]) if row["inactive_reason"] else None,
        retired_ts=int(row["retired_ts"]) if row["retired_ts"] is not None else None,
        retirement_context=retirement_context,
    )


def _task_kind_for_snapshot(snapshot: StudyItemSnapshot) -> str:
    if snapshot.item_status == "new":
        return "context-production"
    if snapshot.item_status == "learning":
        if snapshot.learning_step == 1:
            return "confusable-recognition"
        return "context-production"
    cycle = _review_cycle(snapshot)
    return cycle[snapshot.review_cycle_index % len(cycle)]


def _choose_prompt_type(snapshot: StudyItemSnapshot) -> str:
    return "recognition" if _task_kind_for_snapshot(snapshot) == "confusable-recognition" else "production"


def _review_cycle(snapshot: StudyItemSnapshot) -> tuple[str, ...]:
    return MATURE_REVIEW_CYCLE if _is_bridge_mature(snapshot) else YOUNG_REVIEW_CYCLE


def _font_variant_for_task(snapshot: StudyItemSnapshot, task_kind: str) -> str:
    if task_kind == "confusable-recognition":
        if snapshot.item_status == "learning":
            return "print-sans"
        return "handwritten" if _is_bridge_mature(snapshot) else "print-serif"
    if task_kind == "handwriting":
        return "canonical"
    return "canonical"


def _requires_writing(snapshot: StudyItemSnapshot, prompt_type: str) -> bool:
    if snapshot.item_status == "new":
        return True
    if snapshot.item_status == "learning" and snapshot.learning_step in {0, 2}:
        return True
    return snapshot.item_status == "review" and _task_kind_for_snapshot(snapshot) == "handwriting"


def _guide_mode(level: int) -> str:
    return GUIDE_MODES.get(int(level), GUIDE_MODES[3])


def _build_handwriting_policy(
    snapshot: StudyItemSnapshot,
    *,
    prompt_type: str,
    requires_writing: bool,
    stroke_geometry_available: bool,
) -> dict[str, Any]:
    allowed_on_failure = (
        list(HANDWRITING_FAIL_ALLOWED_RATINGS) if requires_writing else list(RATING_ORDER)
    )
    blocked_on_failure = [
        rating for rating in RATING_ORDER if rating not in set(allowed_on_failure)
    ]
    return {
        "guideMode": _guide_mode(snapshot.guide_level),
        "guideLevel": snapshot.guide_level,
        "guideLevelLabel": GUIDE_LABELS.get(snapshot.guide_level, GUIDE_LABELS[3]),
        "promptType": prompt_type,
        "required": requires_writing,
        "allowManualOverride": True,
        "guidedEvaluationAvailable": stroke_geometry_available,
        "manualOnlyWithoutGeometry": not stroke_geometry_available,
        "allowedRatingsOnFailure": allowed_on_failure,
        "blockedRatingsOnFailure": blocked_on_failure,
    }


def _validate_handwriting_review(
    snapshot: StudyItemSnapshot,
    *,
    prompt_type: str,
    rating: str,
    attempted: bool,
    passed: bool,
) -> None:
    if not _requires_writing(snapshot, prompt_type):
        return
    if not attempted:
        raise ValueError("A handwriting result is required before submitting this writing review.")
    if not passed and _is_pass_rating(rating):
        raise ValueError(
            "A failed handwriting check only allows again unless you override it to pass."
        )


def _update_guide_progression(
    snapshot: StudyItemSnapshot,
    *,
    attempted: bool,
    passed: bool,
) -> tuple[int, int, int]:
    if not attempted:
        return (
            snapshot.guide_level,
            snapshot.consecutive_writing_successes,
            snapshot.consecutive_writing_failures,
        )
    if passed:
        success_streak = snapshot.consecutive_writing_successes + 1
        guide_level = snapshot.guide_level
        if success_streak >= 2 and guide_level < 3:
            guide_level += 1
            success_streak = 0
        return (guide_level, success_streak, 0)

    failure_streak = snapshot.consecutive_writing_failures + 1
    guide_level = snapshot.guide_level
    if failure_streak >= 2 and guide_level > 0:
        guide_level -= 1
        failure_streak = 0
    return (guide_level, 0, failure_streak)


def _schedule_after_review(
    snapshot: StudyItemSnapshot,
    *,
    rating: str,
    now_ts: int,
) -> tuple[int, str, float, float, int]:
    passed = _is_pass_rating(_normalize_rating(rating))
    difficulty = snapshot.difficulty
    stability = snapshot.stability
    total_lapses = snapshot.total_lapses
    elapsed_days = _elapsed_days(snapshot, now_ts)
    retrievability = _fsrs_retrievability(stability, elapsed_days)

    if not passed:
        total_lapses += 1 if snapshot.item_status == "review" else 0
        difficulty = _adjust_difficulty(difficulty, False)
        stability = _fsrs_failed_stability(stability, difficulty, retrievability)
        return (
            now_ts,
            "learning",
            stability,
            difficulty,
            total_lapses,
        )

    difficulty = _adjust_difficulty(difficulty, True)
    if snapshot.item_status == "review":
        stability = _fsrs_next_stability(stability, difficulty, retrievability)
    else:
        stability = _fsrs_initial_stability(difficulty)
    interval_days = max(1.0, _target_interval_days(stability))
    due_ts = now_ts + int(interval_days * SECONDS_PER_DAY)
    return (due_ts, "review", stability, difficulty, total_lapses)


def _elapsed_days(snapshot: StudyItemSnapshot, now_ts: int) -> float:
    if snapshot.last_reviewed_ts is None:
        return 0.0
    return max(0.0, (now_ts - snapshot.last_reviewed_ts) / SECONDS_PER_DAY)


def _target_interval_days(stability: float) -> float:
    return max(1.0, stability)


def _adjust_difficulty(current: float, passed: bool) -> float:
    mean_reversion = 5.5
    delta = -0.35 if passed else 0.55
    return max(1.0, min(10.0, (current * 0.9) + (mean_reversion * 0.1) + delta))


def _adjust_packet_stability(current: float, passed: bool) -> float:
    if passed:
        return max(0.9, min(4.0, current + 0.25))
    return max(0.6, current * 0.82)


def _fsrs_initial_stability(difficulty: float) -> float:
    return max(1.0, 3.0 - (difficulty * 0.22))


def _fsrs_retrievability(stability: float, elapsed_days: float) -> float:
    stable_days = max(0.1, stability)
    return max(0.01, min(0.999, RETENTION_TARGET ** (elapsed_days / stable_days)))


def _fsrs_next_stability(
    stability: float,
    difficulty: float,
    retrievability: float,
) -> float:
    difficulty_term = 1.0 + ((10.0 - difficulty) / 9.0) * 0.75
    retrievability_term = 1.0 + max(0.0, 1.0 - retrievability) * 2.4
    scale_term = 1.05 + min(0.8, stability / 15.0)
    return max(stability + 0.25, stability * difficulty_term * retrievability_term * scale_term)


def _fsrs_failed_stability(
    stability: float,
    difficulty: float,
    retrievability: float,
) -> float:
    return max(
        0.45,
        min(
            1.6,
            stability * (0.28 + (difficulty / 30.0)) * (0.75 + (retrievability * 0.15)),
        ),
    )


def _is_srs_mature(
    snapshot: StudyItemSnapshot,
    *,
    mature_days: int,
) -> bool:
    if snapshot.item_status != "review" or snapshot.last_reviewed_ts is None:
        return False
    scheduled_interval_seconds = snapshot.due_ts - snapshot.last_reviewed_ts
    if scheduled_interval_seconds <= 0:
        return False
    return scheduled_interval_seconds >= mature_days * SECONDS_PER_DAY


def _is_bridge_mature(snapshot: StudyItemSnapshot) -> bool:
    return _is_srs_mature(snapshot, mature_days=RETIREMENT_INTERVAL_DAYS)


def _should_retire_for_mature_anki_support(
    snapshot: StudyItemSnapshot,
    *,
    support_summary: Mapping[str, Any] | None = None,
    mature_days: int,
    now_ts: int | None = None,
    mature_anki_card_count: int | None = None,
) -> bool:
    if not _is_bridge_mature(snapshot):
        return False
    summary = dict(support_summary or {})
    mature_card_count = int(
        summary.get("matureCardCount")
        or mature_anki_card_count
        or 0
    )
    distinct_expression_count = int(summary.get("distinctExpressionCount") or 0)
    recent_expression_count = int(summary.get("recentReviewedExpressionCount") or 0)
    has_recent_review_evidence = bool(summary.get("hasRecentReviewEvidence"))
    if mature_card_count < MATURE_ANKI_CARD_RETIREMENT_THRESHOLD:
        return False
    if distinct_expression_count and distinct_expression_count < 2:
        return False
    if has_recent_review_evidence and recent_expression_count < 1:
        return False
    return True


def _should_reactivate_for_support_collapse(
    snapshot: StudyItemSnapshot,
    *,
    support_summary: Mapping[str, Any],
    now_ts: int,
) -> bool:
    if snapshot.item_status != "inactive" or snapshot.inactive_reason != "retired":
        return False
    if snapshot.retired_ts is None:
        return False
    if now_ts - snapshot.retired_ts > RETIREMENT_REACTIVATION_DAYS * SECONDS_PER_DAY:
        return False
    retired_support = {
        str(value)
        for value in snapshot.retirement_context.get("supportExpressions") or []
        if str(value)
    }
    if not retired_support:
        return False
    current_support = {
        str(value)
        for value in support_summary.get("supportExpressions") or []
        if str(value)
    }
    lost_support = retired_support - current_support
    return len(lost_support) >= 2


def _retirement_context_payload(support_summary: Mapping[str, Any]) -> dict[str, Any]:
    return {
        "supportExpressions": [
            str(value)
            for value in support_summary.get("supportExpressions") or []
            if str(value)
        ],
        "retiredWithMatureCardCount": int(support_summary.get("matureCardCount") or 0),
        "retiredWithDistinctExpressionCount": int(
            support_summary.get("distinctExpressionCount") or 0
        ),
    }


def _why_in_queue(snapshot: StudyItemSnapshot, problem: dict[str, Any]) -> str:
    suspended_count = int(problem.get("suspendedExpressionCount") or 0)
    support_deficit = int(problem.get("supportDeficit") or 0)
    active_count = int(problem.get("activeRecurringExpressionCount") or 0)
    if snapshot.inactive_reason == "retired":
        return (
            "This kanji retired out of the bridge deck because real vocabulary support took over. "
            "It will reactivate automatically if that support weakens again."
        )
    if snapshot.is_problem_seed:
        return (
            f"{suspended_count} suspended expressions still depend on this kanji, "
            f"with a support deficit of {support_deficit} and {active_count} active bridge examples."
        )
    return "This kanji left the current problem-child seed set, but its bridge history remains active."


def _ts_to_iso(value: int | None) -> str | None:
    if value is None:
        return None
    return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(int(value)))


def _coerce_str_list(value: object) -> list[str]:
    if value is None:
        return []
    if isinstance(value, str):
        return [value] if value else []
    if isinstance(value, list):
        return [str(entry) for entry in value if str(entry)]
    if isinstance(value, tuple):
        return [str(entry) for entry in value if str(entry)]
    return []


def _coerce_mapping(value: object) -> dict[str, Any]:
    if isinstance(value, Mapping):
        return dict(value)
    return {}


def _coerce_int(*values: object, default: int) -> int:
    for value in values:
        try:
            return int(value)  # type: ignore[arg-type]
        except (TypeError, ValueError):
            continue
    return default


def _coerce_float(value: object, *, default: float) -> float:
    try:
        return float(value)  # type: ignore[arg-type]
    except (TypeError, ValueError):
        return default


def _normalize_rating(value: object) -> str:
    return str(value or "").strip().lower()


def _is_pass_rating(value: object) -> bool:
    return _normalize_rating(value) in PASS_RATINGS


def _choose_support_words(
    *,
    pain_examples: list[str],
    bridge_examples: list[str],
    mature_examples: list[str],
    collection_examples: list[str],
) -> tuple[str, str, str]:
    ordered = _unique_preserve_order(
        [
            pain_examples[0] if pain_examples else "",
            bridge_examples[0] if bridge_examples else "",
            mature_examples[0] if mature_examples else "",
            *(pain_examples[1:] if len(pain_examples) > 1 else []),
            *(bridge_examples[1:] if len(bridge_examples) > 1 else []),
            *(mature_examples[1:] if len(mature_examples) > 1 else []),
            *collection_examples,
        ]
    )
    padded = ordered + ["", "", ""]
    return (padded[0], padded[1], padded[2])


def _choose_confusables(
    target_kanji: str,
    collection_examples: list[str],
    *,
    preferred_examples: tuple[str, str, str],
) -> list[str]:
    candidates: list[str] = []
    for expression in [*preferred_examples, *collection_examples]:
        for kanji in extract_kanji_chars(expression):
            if kanji != target_kanji:
                candidates.append(kanji)
    return _unique_preserve_order(candidates)[:3]


def _unique_preserve_order(values: Iterable[str]) -> list[str]:
    seen: set[str] = set()
    ordered: list[str] = []
    for value in values:
        text = str(value or "").strip()
        if not text or text in seen:
            continue
        seen.add(text)
        ordered.append(text)
    return ordered


def _empty_support_summary() -> dict[str, Any]:
    return {
        "matureCardCount": 0,
        "distinctExpressionCount": 0,
        "supportExpressions": [],
        "recentReviewedExpressionCount": 0,
        "recentReviewedExpressions": [],
        "hasRecentReviewEvidence": False,
    }


def _normalize_anki_support_lookup(
    *,
    anki_support_lookup: Mapping[str, Mapping[str, Any]] | None,
    mature_anki_card_counts: Mapping[str, int] | None,
) -> dict[str, dict[str, Any]]:
    support_lookup = {
        str(kanji): {
            **_empty_support_summary(),
            **dict(payload),
        }
        for kanji, payload in dict(anki_support_lookup or {}).items()
    }
    for kanji, count in dict(mature_anki_card_counts or {}).items():
        key = str(kanji)
        payload = support_lookup.setdefault(key, _empty_support_summary())
        payload["matureCardCount"] = max(int(payload.get("matureCardCount") or 0), int(count))
    return support_lookup
