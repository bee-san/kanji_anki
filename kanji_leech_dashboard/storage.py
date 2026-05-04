from __future__ import annotations

import json
import sqlite3
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterable, Mapping

from .config import AppSettings, parse_config
from .dashboard import (
    KanjiDashboardDetail,
    KanjiDashboardPayload,
    ProblemKanjiSeed,
    build_dashboard_payload,
    build_kanji_detail_payload,
    build_problem_kanji_seeds,
)
from .jiten import load_kanji_frequency_lookup
from .normalization import extract_kanji_chars, normalize_lookup_text, strip_html_text

SETTINGS_KEY = "settings"
DASHBOARD_SUMMARY_KEY = "dashboard_summary"
DEFAULT_PROFILE = "default"


@dataclass(frozen=True)
class SyncRunRecord:
    sync_run_id: int
    status: str
    started_at: str
    finished_at: str | None
    note_count: int
    card_count: int
    error_message: str | None

    def to_dict(self) -> dict[str, Any]:
        return {
            "id": self.sync_run_id,
            "status": self.status,
            "startedAt": self.started_at,
            "finishedAt": self.finished_at,
            "noteCount": self.note_count,
            "cardCount": self.card_count,
            "errorMessage": self.error_message,
        }


class AppStorage:
    def __init__(self, db_path: Path) -> None:
        self._db_path = Path(db_path)
        self._db_path.parent.mkdir(parents=True, exist_ok=True)
        self.initialize()

    @property
    def db_path(self) -> Path:
        return self._db_path

    def initialize(self) -> None:
        conn = self.connect()
        try:
            conn.executescript(
                """
                PRAGMA foreign_keys = ON;

                CREATE TABLE IF NOT EXISTS app_settings (
                  key TEXT PRIMARY KEY,
                  value_json TEXT NOT NULL,
                  updated_ts INTEGER NOT NULL
                );

                CREATE TABLE IF NOT EXISTS sync_runs (
                  id INTEGER PRIMARY KEY AUTOINCREMENT,
                  source TEXT NOT NULL,
                  status TEXT NOT NULL,
                  started_ts INTEGER NOT NULL,
                  finished_ts INTEGER,
                  note_count INTEGER NOT NULL DEFAULT 0,
                  card_count INTEGER NOT NULL DEFAULT 0,
                  error_message TEXT
                );

                CREATE TABLE IF NOT EXISTS source_notes (
                  note_id INTEGER PRIMARY KEY,
                  model_name TEXT NOT NULL,
                  expression TEXT NOT NULL,
                  reading TEXT NOT NULL,
                  meaning TEXT NOT NULL,
                  fields_json TEXT NOT NULL,
                  tags_json TEXT NOT NULL,
                  is_deleted INTEGER NOT NULL DEFAULT 0,
                  first_seen_ts INTEGER NOT NULL,
                  updated_ts INTEGER NOT NULL,
                  synced_ts INTEGER
                );

                CREATE TABLE IF NOT EXISTS source_cards (
                  card_id INTEGER PRIMARY KEY,
                  note_id INTEGER NOT NULL,
                  deck_name TEXT NOT NULL,
                  interval_days INTEGER NOT NULL DEFAULT 0,
                  modified_ts INTEGER NOT NULL DEFAULT 0,
                  due_value INTEGER NOT NULL DEFAULT 0,
                  card_ord INTEGER NOT NULL DEFAULT 0,
                  queue_value INTEGER NOT NULL DEFAULT 0,
                  card_type INTEGER NOT NULL DEFAULT 0,
                  reps INTEGER NOT NULL DEFAULT 0,
                  lapses INTEGER NOT NULL DEFAULT 0,
                  is_suspended INTEGER NOT NULL DEFAULT 0,
                  is_active INTEGER NOT NULL DEFAULT 1,
                  is_mature INTEGER NOT NULL DEFAULT 0,
                  is_deleted INTEGER NOT NULL DEFAULT 0,
                  first_seen_ts INTEGER NOT NULL,
                  updated_ts INTEGER NOT NULL,
                  synced_ts INTEGER,
                  FOREIGN KEY(note_id) REFERENCES source_notes(note_id)
                );

                CREATE TABLE IF NOT EXISTS expression_snapshots (
                  normalized_expression TEXT PRIMARY KEY,
                  expression TEXT NOT NULL,
                  reading TEXT NOT NULL,
                  meaning TEXT NOT NULL,
                  tags_json TEXT NOT NULL,
                  source_note_ids_json TEXT NOT NULL,
                  source_card_ids_json TEXT NOT NULL,
                  suspended_card_count INTEGER NOT NULL DEFAULT 0,
                  active_card_count INTEGER NOT NULL DEFAULT 0,
                  mature_card_count INTEGER NOT NULL DEFAULT 0,
                  updated_ts INTEGER NOT NULL
                );

                CREATE TABLE IF NOT EXISTS problem_kanji_snapshots (
                  kanji TEXT PRIMARY KEY,
                  jiten_rank REAL,
                  collection_expression_count INTEGER NOT NULL,
                  suspended_expression_count INTEGER NOT NULL,
                  active_recurring_expression_count INTEGER NOT NULL,
                  mature_support_count INTEGER NOT NULL,
                  support_deficit INTEGER NOT NULL,
                  is_unknown INTEGER NOT NULL,
                  browser_search TEXT NOT NULL,
                  detail_json TEXT NOT NULL,
                  sort_index INTEGER NOT NULL,
                  updated_ts INTEGER NOT NULL
                );
                """
            )
            self._ensure_column(conn, "source_cards", "modified_ts", "INTEGER NOT NULL DEFAULT 0")
            conn.commit()
        finally:
            conn.close()

    def connect(self) -> sqlite3.Connection:
        conn = sqlite3.connect(self._db_path)
        conn.row_factory = sqlite3.Row
        return conn

    def load_settings(self) -> AppSettings:
        conn = self.connect()
        try:
            row = conn.execute(
                "SELECT value_json FROM app_settings WHERE key = ?",
                (SETTINGS_KEY,),
            ).fetchone()
            if row is None:
                settings = AppSettings()
                self.save_settings(settings, connection=conn)
                conn.commit()
                return settings
            return parse_config(json.loads(str(row["value_json"] or "{}")))
        finally:
            conn.close()

    def save_settings(
        self,
        settings: AppSettings,
        *,
        connection: sqlite3.Connection | None = None,
    ) -> AppSettings:
        own_connection = connection is None
        conn = connection or self.connect()
        try:
            now_ts = _now_ts()
            conn.execute(
                """
                INSERT INTO app_settings (key, value_json, updated_ts)
                VALUES (?, ?, ?)
                ON CONFLICT(key) DO UPDATE SET
                  value_json = excluded.value_json,
                  updated_ts = excluded.updated_ts
                """,
                (SETTINGS_KEY, json.dumps(settings.to_dict(), ensure_ascii=False), now_ts),
            )
            if own_connection:
                conn.commit()
            return settings
        finally:
            if own_connection:
                conn.close()

    def start_sync_run(self, source: str) -> int:
        conn = self.connect()
        try:
            now_ts = _now_ts()
            cursor = conn.execute(
                """
                INSERT INTO sync_runs (source, status, started_ts)
                VALUES (?, 'running', ?)
                """,
                (source, now_ts),
            )
            conn.commit()
            return int(cursor.lastrowid)
        finally:
            conn.close()

    def finish_sync_run(
        self,
        sync_run_id: int,
        *,
        status: str,
        note_count: int,
        card_count: int,
        error_message: str | None = None,
    ) -> None:
        conn = self.connect()
        try:
            conn.execute(
                """
                UPDATE sync_runs
                SET status = ?,
                    finished_ts = ?,
                    note_count = ?,
                    card_count = ?,
                    error_message = ?
                WHERE id = ?
                """,
                (status, _now_ts(), note_count, card_count, error_message, sync_run_id),
            )
            conn.commit()
        finally:
            conn.close()

    def latest_sync_run(self) -> SyncRunRecord | None:
        conn = self.connect()
        try:
            row = conn.execute(
                "SELECT * FROM sync_runs ORDER BY id DESC LIMIT 1"
            ).fetchone()
            if row is None:
                return None
            return _row_to_sync_run(row)
        finally:
            conn.close()

    def replace_source_snapshot(
        self,
        *,
        notes: Iterable[Mapping[str, Any]],
        cards: Iterable[Mapping[str, Any]],
        settings: AppSettings,
    ) -> None:
        conn = self.connect()
        sync_ts = time.time_ns()
        try:
            for note in notes:
                conn.execute(
                    """
                    INSERT INTO source_notes (
                      note_id,
                      model_name,
                      expression,
                      reading,
                      meaning,
                      fields_json,
                      tags_json,
                      is_deleted,
                      first_seen_ts,
                      updated_ts,
                      synced_ts
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?)
                    ON CONFLICT(note_id) DO UPDATE SET
                      model_name = excluded.model_name,
                      expression = excluded.expression,
                      reading = excluded.reading,
                      meaning = excluded.meaning,
                      fields_json = excluded.fields_json,
                      tags_json = excluded.tags_json,
                      is_deleted = 0,
                      updated_ts = excluded.updated_ts,
                      synced_ts = excluded.synced_ts
                    """,
                    (
                        int(note["note_id"]),
                        str(note["model_name"]),
                        str(note["expression"] or ""),
                        str(note["reading"] or ""),
                        str(note["meaning"] or ""),
                        json.dumps(note["fields"], ensure_ascii=False, sort_keys=True),
                        json.dumps(list(note["tags"]), ensure_ascii=False),
                        sync_ts,
                        sync_ts,
                        sync_ts,
                    ),
                )
            for card in cards:
                conn.execute(
                    """
                    INSERT INTO source_cards (
                      card_id,
                      note_id,
                      deck_name,
                      interval_days,
                      modified_ts,
                      due_value,
                      card_ord,
                      queue_value,
                      card_type,
                      reps,
                      lapses,
                      is_suspended,
                      is_active,
                      is_mature,
                      is_deleted,
                      first_seen_ts,
                      updated_ts,
                      synced_ts
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?)
                    ON CONFLICT(card_id) DO UPDATE SET
                      note_id = excluded.note_id,
                      deck_name = excluded.deck_name,
                      interval_days = excluded.interval_days,
                      modified_ts = excluded.modified_ts,
                      due_value = excluded.due_value,
                      card_ord = excluded.card_ord,
                      queue_value = excluded.queue_value,
                      card_type = excluded.card_type,
                      reps = excluded.reps,
                      lapses = excluded.lapses,
                      is_suspended = excluded.is_suspended,
                      is_active = excluded.is_active,
                      is_mature = excluded.is_mature,
                      is_deleted = 0,
                      updated_ts = excluded.updated_ts,
                      synced_ts = excluded.synced_ts
                    """,
                    (
                        int(card["card_id"]),
                        int(card["note_id"]),
                        str(card["deck_name"] or ""),
                        int(card["interval_days"] or 0),
                        int(card.get("modified_ts") or 0),
                        int(card["due"] or 0),
                        int(card["card_ord"] or 0),
                        int(card["queue"] or 0),
                        int(card["card_type"] or 0),
                        int(card["reps"] or 0),
                        int(card["lapses"] or 0),
                        1 if card["is_suspended"] else 0,
                        1 if card["is_active"] else 0,
                        1 if card["is_mature"] else 0,
                        sync_ts,
                        sync_ts,
                        sync_ts,
                    ),
                )

            conn.execute(
                """
                UPDATE source_notes
                SET is_deleted = 1, updated_ts = ?
                WHERE synced_ts IS NULL OR synced_ts != ?
                """,
                (sync_ts, sync_ts),
            )
            conn.execute(
                """
                UPDATE source_cards
                SET is_deleted = 1, updated_ts = ?
                WHERE synced_ts IS NULL OR synced_ts != ?
                """,
                (sync_ts, sync_ts),
            )
            conn.commit()
        finally:
            conn.close()

        self.rebuild_analysis(settings)

    def rebuild_analysis(self, settings: AppSettings) -> dict[str, Any]:
        conn = self.connect()
        now_ts = _now_ts()
        try:
            rows = conn.execute(
                """
                SELECT
                  n.note_id,
                  n.model_name,
                  n.expression,
                  n.reading,
                  n.meaning,
                  n.tags_json,
                  c.card_id,
                  c.is_suspended,
                  c.is_active,
                  c.is_mature
                FROM source_notes n
                JOIN source_cards c ON c.note_id = n.note_id
                WHERE n.is_deleted = 0 AND c.is_deleted = 0
                ORDER BY n.note_id ASC, c.card_id ASC
                """
            ).fetchall()

            expression_entries: dict[str, dict[str, Any]] = {}
            suspended_expressions: list[str] = []
            active_expressions: list[str] = []
            mature_expressions: list[str] = []
            analyzed_suspended_card_count = 0

            for row in rows:
                normalized_expression = _normalize_expression(str(row["expression"] or ""))
                if not normalized_expression:
                    continue

                entry = expression_entries.setdefault(
                    normalized_expression,
                    {
                        "expression": normalized_expression,
                        "reading": str(row["reading"] or ""),
                        "meaning": str(row["meaning"] or ""),
                        "tags": _load_json_list(str(row["tags_json"] or "[]")),
                        "note_ids": set(),
                        "card_ids": set(),
                        "suspended_card_count": 0,
                        "active_card_count": 0,
                        "mature_card_count": 0,
                    },
                )
                entry["note_ids"].add(int(row["note_id"]))
                entry["card_ids"].add(int(row["card_id"]))
                if row["is_suspended"]:
                    entry["suspended_card_count"] += 1
                    suspended_expressions.append(normalized_expression)
                    analyzed_suspended_card_count += 1
                if row["is_active"]:
                    entry["active_card_count"] += 1
                    active_expressions.append(normalized_expression)
                if row["is_mature"]:
                    entry["mature_card_count"] += 1
                    mature_expressions.append(normalized_expression)

            conn.execute("DELETE FROM expression_snapshots")
            for normalized_expression, entry in sorted(expression_entries.items()):
                conn.execute(
                    """
                    INSERT INTO expression_snapshots (
                      normalized_expression,
                      expression,
                      reading,
                      meaning,
                      tags_json,
                      source_note_ids_json,
                      source_card_ids_json,
                      suspended_card_count,
                      active_card_count,
                      mature_card_count,
                      updated_ts
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        normalized_expression,
                        entry["expression"],
                        entry["reading"],
                        entry["meaning"],
                        json.dumps(entry["tags"], ensure_ascii=False),
                        json.dumps(sorted(entry["note_ids"])),
                        json.dumps(sorted(entry["card_ids"])),
                        entry["suspended_card_count"],
                        entry["active_card_count"],
                        entry["mature_card_count"],
                        now_ts,
                    ),
                )

            if expression_entries:
                lookup = load_kanji_frequency_lookup(settings)
                payload = build_dashboard_payload(
                    analyzed_suspended_card_count=analyzed_suspended_card_count,
                    suspended_expressions=suspended_expressions,
                    active_expressions=active_expressions,
                    mature_expressions=mature_expressions,
                    threshold=settings.kanji_dashboard_mature_support_threshold,
                    kanji_ranks=lookup.ranks,
                    model_names=settings.model_names,
                    search_field_name=settings.expression_field,
                    warnings=lookup.warnings,
                    jiten_source_kind=lookup.source_kind,
                )
                seeds = build_problem_kanji_seeds(
                    suspended_expressions=suspended_expressions,
                    active_expressions=active_expressions,
                    mature_expressions=mature_expressions,
                    threshold=settings.kanji_dashboard_mature_support_threshold,
                    kanji_ranks=lookup.ranks,
                    model_names=settings.model_names,
                    search_field_name=settings.expression_field,
                )
            else:
                payload = build_dashboard_payload(
                    analyzed_suspended_card_count=0,
                    suspended_expressions=[],
                    active_expressions=[],
                    mature_expressions=[],
                    threshold=settings.kanji_dashboard_mature_support_threshold,
                    kanji_ranks={},
                    model_names=settings.model_names,
                    search_field_name=settings.expression_field,
                    warnings=(),
                    jiten_source_kind="none",
                )
                seeds = ()

            conn.execute("DELETE FROM problem_kanji_snapshots")
            for index, row in enumerate(payload.rows):
                detail = build_kanji_detail_payload(
                    kanji=row.kanji,
                    suspended_expressions=suspended_expressions,
                    active_expressions=active_expressions,
                    mature_expressions=mature_expressions,
                    threshold=settings.kanji_dashboard_mature_support_threshold,
                    kanji_ranks=lookup.ranks,
                    model_names=settings.model_names,
                    search_field_name=settings.expression_field,
                )
                conn.execute(
                    """
                    INSERT INTO problem_kanji_snapshots (
                      kanji,
                      jiten_rank,
                      collection_expression_count,
                      suspended_expression_count,
                      active_recurring_expression_count,
                      mature_support_count,
                      support_deficit,
                      is_unknown,
                      browser_search,
                      detail_json,
                      sort_index,
                      updated_ts
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    (
                        row.kanji,
                        row.jiten_rank,
                        row.collection_expression_count,
                        row.suspended_expression_count,
                        row.active_recurring_expression_count,
                        row.mature_support_count,
                        row.support_deficit,
                        1 if row.is_unknown else 0,
                        row.browser_search,
                        json.dumps(detail.to_dict(), ensure_ascii=False, sort_keys=True),
                        index,
                        now_ts,
                    ),
                )

            self._save_json_setting(
                conn,
                DASHBOARD_SUMMARY_KEY,
                {
                    **payload.summary.to_dict(),
                    "warnings": list(payload.warnings),
                    "jitenSourceKind": payload.jiten_source_kind,
                    "problemSeedCount": len(seeds),
                },
                now_ts,
            )
            conn.commit()
            return {
                "summary": payload.summary.to_dict(),
                "warnings": list(payload.warnings),
                "jitenSourceKind": payload.jiten_source_kind,
                "problemSeedCount": len(seeds),
            }
        finally:
            conn.close()

    def load_dashboard(self) -> dict[str, Any]:
        conn = self.connect()
        try:
            summary = self._load_json_setting(conn, DASHBOARD_SUMMARY_KEY) or {
                "analyzedSuspendedCardCount": 0,
                "analyzedSuspendedExpressionCount": 0,
                "unknownKanjiCount": 0,
                "averageKanjiRank": None,
                "rankedKanjiCount": 0,
                "totalKanjiCount": 0,
                "matureSupportThreshold": 0,
                "warnings": [],
                "jitenSourceKind": "none",
                "problemSeedCount": 0,
            }
            rows = [
                {
                    "kanji": str(row["kanji"]),
                    "jitenRank": row["jiten_rank"],
                    "collectionExpressionCount": int(row["collection_expression_count"]),
                    "suspendedExpressionCount": int(row["suspended_expression_count"]),
                    "activeRecurringExpressionCount": int(
                        row["active_recurring_expression_count"]
                    ),
                    "matureSupportCount": int(row["mature_support_count"]),
                    "supportDeficit": int(row["support_deficit"]),
                    "isUnknown": bool(row["is_unknown"]),
                    "browserSearch": str(row["browser_search"]),
                }
                for row in conn.execute(
                    "SELECT * FROM problem_kanji_snapshots ORDER BY sort_index ASC"
                ).fetchall()
            ]
            return {
                "summary": {
                    key: summary[key]
                    for key in (
                        "analyzedSuspendedCardCount",
                        "analyzedSuspendedExpressionCount",
                        "unknownKanjiCount",
                        "averageKanjiRank",
                        "rankedKanjiCount",
                        "totalKanjiCount",
                        "matureSupportThreshold",
                    )
                },
                "rows": rows,
                "warnings": list(summary.get("warnings") or []),
                "jitenSourceKind": str(summary.get("jitenSourceKind") or "none"),
                "problemSeedCount": int(summary.get("problemSeedCount") or 0),
            }
        finally:
            conn.close()

    def load_kanji_detail(self, kanji: str) -> dict[str, Any] | None:
        conn = self.connect()
        try:
            row = conn.execute(
                "SELECT detail_json FROM problem_kanji_snapshots WHERE kanji = ?",
                (kanji,),
            ).fetchone()
            if row is None:
                return None
            detail = json.loads(str(row["detail_json"] or "{}"))
            return detail if isinstance(detail, dict) else None
        finally:
            conn.close()

    def load_problem_seeds(self) -> tuple[ProblemKanjiSeed, ...]:
        conn = self.connect()
        try:
            rows = conn.execute(
                """
                SELECT detail_json, jiten_rank, browser_search, support_deficit
                FROM problem_kanji_snapshots
                WHERE suspended_expression_count > 0
                  AND support_deficit > 0
                ORDER BY sort_index ASC
                """
            ).fetchall()
            seeds: list[ProblemKanjiSeed] = []
            for row in rows:
                detail = json.loads(str(row["detail_json"] or "{}"))
                if not isinstance(detail, Mapping):
                    continue
                seeds.append(
                    ProblemKanjiSeed(
                        kanji=str(detail.get("kanji") or ""),
                        jiten_rank=row["jiten_rank"],
                        collection_expressions=tuple(
                            str(value)
                            for value in detail.get("collectionExpressions") or []
                        ),
                        affected_suspended_expressions=tuple(
                            str(value)
                            for value in detail.get("affectedSuspendedExpressions") or []
                        ),
                        active_recurring_expressions=tuple(
                            str(value)
                            for value in detail.get("activeRecurringExpressions") or []
                        ),
                        mature_supporting_expressions=tuple(
                            str(value)
                            for value in detail.get("matureSupportingExpressions") or []
                        ),
                        support_deficit=int(row["support_deficit"]),
                        browser_search=str(row["browser_search"]),
                    )
                )
            return tuple(seeds)
        finally:
            conn.close()

    def load_mature_anki_card_counts(self) -> dict[str, int]:
        return {
            kanji: int(payload["matureCardCount"])
            for kanji, payload in self.load_anki_support_lookup().items()
        }

    def load_anki_support_lookup(self) -> dict[str, dict[str, Any]]:
        conn = self.connect()
        recent_cutoff_ts = _now_ts() - (60 * 86400)
        try:
            rows = conn.execute(
                """
                SELECT n.expression, c.modified_ts, c.reps
                FROM source_cards AS c
                JOIN source_notes AS n
                  ON n.note_id = c.note_id
                WHERE c.is_deleted = 0
                  AND n.is_deleted = 0
                  AND c.is_active = 1
                  AND c.is_mature = 1
                """
            ).fetchall()
            support: dict[str, dict[str, Any]] = {}
            for row in rows:
                expression = _normalize_expression(str(row["expression"] or ""))
                if not expression:
                    continue
                recent_reviewed = (
                    int(row["modified_ts"] or 0) >= recent_cutoff_ts
                    and int(row["reps"] or 0) > 0
                )
                for kanji in extract_kanji_chars(expression):
                    entry = support.setdefault(
                        kanji,
                        {
                            "matureCardCount": 0,
                            "distinctExpressions": set(),
                            "recentReviewedExpressions": set(),
                            "hasRecentReviewEvidence": False,
                        },
                    )
                    entry["matureCardCount"] += 1
                    entry["distinctExpressions"].add(expression)
                    if int(row["modified_ts"] or 0) > 0:
                        entry["hasRecentReviewEvidence"] = True
                    if recent_reviewed:
                        entry["recentReviewedExpressions"].add(expression)
            return {
                kanji: {
                    "matureCardCount": int(entry["matureCardCount"]),
                    "distinctExpressionCount": len(entry["distinctExpressions"]),
                    "supportExpressions": sorted(entry["distinctExpressions"]),
                    "recentReviewedExpressionCount": len(entry["recentReviewedExpressions"]),
                    "recentReviewedExpressions": sorted(entry["recentReviewedExpressions"]),
                    "hasRecentReviewEvidence": bool(entry["hasRecentReviewEvidence"]),
                }
                for kanji, entry in support.items()
            }
        finally:
            conn.close()

    def problem_seed_count(self) -> int:
        conn = self.connect()
        try:
            row = conn.execute(
                """
                SELECT COUNT(*)
                FROM problem_kanji_snapshots
                WHERE suspended_expression_count > 0
                  AND support_deficit > 0
                """
            ).fetchone()
            return int(row[0]) if row is not None else 0
        finally:
            conn.close()

    def source_counts(self) -> dict[str, int]:
        conn = self.connect()
        try:
            notes = conn.execute(
                "SELECT COUNT(*) FROM source_notes WHERE is_deleted = 0"
            ).fetchone()
            cards = conn.execute(
                "SELECT COUNT(*) FROM source_cards WHERE is_deleted = 0"
            ).fetchone()
            return {
                "noteCount": int(notes[0]) if notes is not None else 0,
                "cardCount": int(cards[0]) if cards is not None else 0,
            }
        finally:
            conn.close()

    def _load_json_setting(
        self,
        conn: sqlite3.Connection,
        key: str,
    ) -> dict[str, Any] | None:
        row = conn.execute(
            "SELECT value_json FROM app_settings WHERE key = ?",
            (key,),
        ).fetchone()
        if row is None:
            return None
        try:
            payload = json.loads(str(row["value_json"] or "{}"))
        except ValueError:
            return None
        return payload if isinstance(payload, dict) else None

    def _save_json_setting(
        self,
        conn: sqlite3.Connection,
        key: str,
        payload: Mapping[str, Any],
        updated_ts: int,
    ) -> None:
        conn.execute(
            """
            INSERT INTO app_settings (key, value_json, updated_ts)
            VALUES (?, ?, ?)
            ON CONFLICT(key) DO UPDATE SET
              value_json = excluded.value_json,
              updated_ts = excluded.updated_ts
            """,
            (key, json.dumps(dict(payload), ensure_ascii=False), updated_ts),
        )

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


def _normalize_expression(expression: str) -> str:
    return normalize_lookup_text(strip_html_text(expression or ""))


def _load_json_list(raw: str) -> list[str]:
    try:
        decoded = json.loads(raw)
    except ValueError:
        return []
    if not isinstance(decoded, list):
        return []
    return [str(value) for value in decoded if str(value)]


def _row_to_sync_run(row: sqlite3.Row) -> SyncRunRecord:
    return SyncRunRecord(
        sync_run_id=int(row["id"]),
        status=str(row["status"]),
        started_at=_ts_to_iso(int(row["started_ts"])),
        finished_at=_ts_to_iso(int(row["finished_ts"])) if row["finished_ts"] else None,
        note_count=int(row["note_count"]),
        card_count=int(row["card_count"]),
        error_message=str(row["error_message"]) if row["error_message"] else None,
    )


def _now_ts() -> int:
    return int(time.time())


def _ts_to_iso(timestamp: int) -> str:
    return time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime(timestamp))
