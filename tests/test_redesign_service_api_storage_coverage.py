from __future__ import annotations

import asyncio
import json
from pathlib import Path
import threading
from types import SimpleNamespace
from urllib.error import URLError

import httpx
import pytest

from kanji_leech_dashboard import ankiconnect as ankiconnect_module
from kanji_leech_dashboard import api as api_module
from kanji_leech_dashboard import dashboard as dashboard_module
from kanji_leech_dashboard.ankiconnect import AnkiConnectClient, AnkiConnectError
from kanji_leech_dashboard.api import create_app
from kanji_leech_dashboard.config import AppSettings, ConfigValidationError, parse_config
from kanji_leech_dashboard.dashboard import (
    KanjiDashboardDetail,
    KanjiDashboardRow,
    KanjiDashboardSummary,
    KanjiNotFoundError,
    ProblemKanjiSeed,
)
from kanji_leech_dashboard.service import KanjiCompanionService
from kanji_leech_dashboard.storage import AppStorage, DASHBOARD_SUMMARY_KEY
from kanji_leech_dashboard.study import StudyItemNotFoundError

from .redesign_helpers import FakeContentProvider, prime_app_home


def run(coro):
    return asyncio.run(coro)


class HandlerService:
    def get_health(self) -> dict[str, bool]:
        return {"ok": True}

    def get_settings(self) -> dict[str, object]:
        return {"noteModels": ["Kiku"]}

    def update_settings(self, _payload) -> dict[str, object]:
        raise ConfigValidationError(["bad settings"])

    def sync_ankiconnect(self) -> dict[str, object]:
        raise AnkiConnectError("bridge offline")

    def get_dashboard(self) -> dict[str, object]:
        raise ValueError("bad dashboard")

    def get_kanji_detail(self, _kanji: str) -> dict[str, object]:
        raise KanjiNotFoundError("Kanji '空' was not found in the current study scope.")

    def get_study_overview(self) -> dict[str, object]:
        raise StudyItemNotFoundError("study item missing")

    def refresh_study_seeds(self) -> dict[str, object]:
        return {"ok": True}

    def create_study_session(self, _payload) -> dict[str, object]:
        return {"ok": True}

    def submit_study_review(self, _payload) -> dict[str, object]:
        return {"ok": True}

    def get_stroke_order_svg_path(self, _kanji: str) -> Path | None:
        return None


class FailingSnapshotClient:
    def sync_snapshot(self, _settings) -> object:
        raise RuntimeError("sync exploded")


class RefreshLockStudyService:
    def __init__(self) -> None:
        self.calls = 0

    def sync_problem_kanji(self, *_args, **_kwargs) -> dict[str, object]:
        self.calls += 1
        return {"ok": True}


class RefreshLockStorage:
    def __init__(self, settings: AppSettings) -> None:
        self.settings = settings
        self.load_settings_called = False

    def load_settings(self) -> AppSettings:
        self.load_settings_called = True
        return self.settings

    def load_problem_seeds(self) -> tuple[ProblemKanjiSeed, ...]:
        return ()

    def load_mature_anki_card_counts(self) -> dict[str, int]:
        return {}


class FakeHTTPResponse:
    def __init__(self, raw: bytes) -> None:
        self._raw = raw

    def __enter__(self) -> FakeHTTPResponse:
        return self

    def __exit__(self, exc_type, exc, tb) -> bool:
        return False

    def read(self) -> bytes:
        return self._raw


class DashboardCard:
    def __init__(self, fields: dict[str, str]) -> None:
        self._fields = fields

    def note(self) -> dict[str, str]:
        return self._fields


class DashboardCollection:
    def __init__(
        self,
        *,
        query_results: dict[str, list[int]],
        notes_by_card_id: dict[int, dict[str, str]],
    ) -> None:
        self._query_results = query_results
        self._notes_by_card_id = notes_by_card_id

    def find_cards(self, query: str) -> list[int]:
        return list(self._query_results[query])

    def get_card(self, card_id: int) -> DashboardCard:
        return DashboardCard(self._notes_by_card_id[card_id])


class ParsingStubAnkiConnectClient(AnkiConnectClient):
    def __init__(self, responses: dict[str, object]) -> None:
        super().__init__("http://example.invalid")
        self._responses = responses

    def _invoke(self, action: str, **params):
        response = self._responses[action]
        return response(params) if callable(response) else response


def _insert_problem_snapshot(
    storage: AppStorage,
    *,
    kanji: str,
    jiten_rank: float | None,
    suspended_expression_count: int,
    support_deficit: int,
    browser_search: str,
    detail_json: str,
    sort_index: int,
) -> None:
    conn = storage.connect()
    try:
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
                kanji,
                jiten_rank,
                1,
                suspended_expression_count,
                0,
                0,
                support_deficit,
                1,
                browser_search,
                detail_json,
                sort_index,
                0,
            ),
        )
        conn.commit()
    finally:
        conn.close()


def test_api_routes_use_registered_exception_handlers(tmp_path: Path, monkeypatch) -> None:
    web_dir = tmp_path / "webapp"
    web_dir.mkdir()
    monkeypatch.setattr(api_module, "webapp_dir", lambda: web_dir)

    transport = httpx.ASGITransport(app=create_app(HandlerService()))

    async def scenario():
        async with httpx.AsyncClient(transport=transport, base_url="http://testserver") as client:
            return (
                await client.put("/api/settings", json={}),
                await client.post("/api/sync/ankiconnect"),
                await client.get("/api/dashboard"),
                await client.get("/api/kanji/%E7%A9%BA"),
                await client.get("/api/study/overview"),
                await client.get("/"),
            )

    invalid_settings, sync_failure, dashboard_failure, missing_detail, missing_study_item, index = run(
        scenario()
    )

    assert invalid_settings.status_code == 400
    assert invalid_settings.json() == {"detail": ["bad settings"]}
    assert sync_failure.status_code == 502
    assert sync_failure.json() == {"detail": "bridge offline"}
    assert dashboard_failure.status_code == 400
    assert dashboard_failure.json() == {"detail": "bad dashboard"}
    assert missing_detail.status_code == 404
    assert missing_detail.json() == {
        "detail": "Kanji '空' was not found in the current study scope."
    }
    assert missing_study_item.status_code == 404
    assert missing_study_item.json() == {"detail": "study item missing"}
    assert index.status_code == 404
    assert index.json() == {"detail": "The hosted web app is not available."}


def test_service_sync_failure_marks_latest_run_failed(tmp_path: Path, monkeypatch) -> None:
    prime_app_home(monkeypatch, tmp_path)
    storage = AppStorage(tmp_path / "app.sqlite3")
    storage.save_settings(parse_config({"noteModels": ["Kiku"]}))
    service = KanjiCompanionService(
        storage=storage,
        content_provider=FakeContentProvider(),
        ankiconnect_factory=lambda *_args: FailingSnapshotClient(),
    )

    with pytest.raises(RuntimeError, match="sync exploded"):
        service.sync_ankiconnect()

    latest = storage.latest_sync_run()
    assert latest is not None
    assert latest.status == "failed"
    assert latest.note_count == 0
    assert latest.card_count == 0
    assert latest.error_message == "sync exploded"
    assert latest.finished_at is not None

    health = service.get_health()
    assert health["latestSyncRun"]["status"] == "failed"
    assert health["sourceCounts"] == {"noteCount": 0, "cardCount": 0}


def test_service_get_kanji_detail_raises_when_storage_has_no_match(
    tmp_path: Path,
    monkeypatch,
) -> None:
    prime_app_home(monkeypatch, tmp_path)
    service = KanjiCompanionService(
        storage=AppStorage(tmp_path / "app.sqlite3"),
        content_provider=FakeContentProvider(),
    )

    with pytest.raises(
        KanjiNotFoundError,
        match="Kanji '空' was not found in the current study scope.",
    ):
        service.get_kanji_detail("空")


def test_refresh_study_seeds_respects_storage_operation_lock() -> None:
    storage = RefreshLockStorage(parse_config({"noteModels": ["Kiku"]}))
    study_service = RefreshLockStudyService()
    service = KanjiCompanionService(
        storage=storage,
        study_service=study_service,
        content_provider=FakeContentProvider(),
    )
    ready = threading.Event()
    finished = threading.Event()

    def run_refresh() -> None:
        ready.set()
        service.refresh_study_seeds()
        finished.set()

    service._storage_operation_lock.acquire()
    try:
        worker = threading.Thread(target=run_refresh, daemon=True)
        worker.start()
        assert ready.wait(timeout=1.0)
        assert not finished.wait(timeout=0.2)
        assert storage.load_settings_called is False
        assert study_service.calls == 0
    finally:
        service._storage_operation_lock.release()

    assert finished.wait(timeout=1.0)
    worker.join(timeout=1.0)
    assert storage.load_settings_called is True
    assert study_service.calls == 1


def test_storage_dashboard_summary_falls_back_for_invalid_saved_json(tmp_path: Path) -> None:
    storage = AppStorage(tmp_path / "app.sqlite3")
    conn = storage.connect()
    try:
        conn.execute(
            """
            INSERT INTO app_settings (key, value_json, updated_ts)
            VALUES (?, ?, ?)
            """,
            (DASHBOARD_SUMMARY_KEY, "not-json", 0),
        )
        assert storage._load_json_setting(conn, DASHBOARD_SUMMARY_KEY) is None
        conn.execute(
            "UPDATE app_settings SET value_json = ? WHERE key = ?",
            ("[]", DASHBOARD_SUMMARY_KEY),
        )
        assert storage._load_json_setting(conn, DASHBOARD_SUMMARY_KEY) is None
        conn.commit()
    finally:
        conn.close()

    assert storage.load_dashboard() == {
        "summary": {
            "analyzedSuspendedCardCount": 0,
            "analyzedSuspendedExpressionCount": 0,
            "unknownKanjiCount": 0,
            "averageKanjiRank": None,
            "rankedKanjiCount": 0,
            "totalKanjiCount": 0,
            "matureSupportThreshold": 0,
        },
        "rows": [],
        "warnings": [],
        "jitenSourceKind": "none",
        "problemSeedCount": 0,
    }


def test_storage_skips_non_mapping_problem_detail_payloads(tmp_path: Path) -> None:
    storage = AppStorage(tmp_path / "app.sqlite3")
    _insert_problem_snapshot(
        storage,
        kanji="学",
        jiten_rank=3.0,
        suspended_expression_count=1,
        support_deficit=2,
        browser_search='"Expression:*学*"',
        detail_json="[]",
        sort_index=0,
    )
    _insert_problem_snapshot(
        storage,
        kanji="校",
        jiten_rank=6.0,
        suspended_expression_count=1,
        support_deficit=1,
        browser_search='"Expression:*校*"',
        detail_json=json.dumps(
            {
                "kanji": "校",
                "collectionExpressions": ["学校"],
                "affectedSuspendedExpressions": ["学校"],
                "activeRecurringExpressions": ["学校"],
                "matureSupportingExpressions": [],
            },
            ensure_ascii=False,
            sort_keys=True,
        ),
        sort_index=1,
    )

    assert storage.load_kanji_detail("学") is None

    seeds = storage.load_problem_seeds()
    assert [seed.kanji for seed in seeds] == ["校"]
    assert seeds[0].browser_search == '"Expression:*校*"'
    assert seeds[0].support_deficit == 1


def test_service_refresh_study_seeds_uses_mature_anki_card_counts_to_retire_items(
    tmp_path: Path,
    monkeypatch,
) -> None:
    prime_app_home(monkeypatch, tmp_path)
    storage = AppStorage(tmp_path / "app.sqlite3")
    settings = parse_config({"noteModels": ["Kiku"], "kanjiSupportThreshold": 3})
    storage.save_settings(settings)
    storage.replace_source_snapshot(
        notes=(
            {
                "note_id": 1,
                "model_name": "Kiku",
                "expression": "学",
                "reading": "がく",
                "meaning": "study",
                "fields": {
                    "Expression": "学",
                    "Reading": "がく",
                    "Meaning": "study",
                },
                "tags": ("blocked",),
            },
            {
                "note_id": 2,
                "model_name": "Kiku",
                "expression": "学校",
                "reading": "がっこう",
                "meaning": "school",
                "fields": {
                    "Expression": "学校",
                    "Reading": "がっこう",
                    "Meaning": "school",
                },
                "tags": ("mature",),
            },
            {
                "note_id": 3,
                "model_name": "Kiku",
                "expression": "学校",
                "reading": "がっこう",
                "meaning": "school",
                "fields": {
                    "Expression": "学校",
                    "Reading": "がっこう",
                    "Meaning": "school",
                },
                "tags": ("mature",),
            },
            {
                "note_id": 4,
                "model_name": "Kiku",
                "expression": "学年",
                "reading": "がくねん",
                "meaning": "school year",
                "fields": {
                    "Expression": "学年",
                    "Reading": "がくねん",
                    "Meaning": "school year",
                },
                "tags": ("mature",),
            },
        ),
        cards=(
            {
                "card_id": 11,
                "note_id": 1,
                "deck_name": "Kiku",
                "interval_days": 0,
                "due": 0,
                "card_ord": 0,
                "queue": -1,
                "card_type": 0,
                "reps": 0,
                "lapses": 0,
                "is_suspended": True,
                "is_active": False,
                "is_mature": False,
            },
            {
                "card_id": 22,
                "note_id": 2,
                "deck_name": "Kiku",
                "interval_days": 30,
                "due": 30,
                "card_ord": 0,
                "queue": 2,
                "card_type": 2,
                "reps": 18,
                "lapses": 0,
                "is_suspended": False,
                "is_active": True,
                "is_mature": True,
            },
            {
                "card_id": 33,
                "note_id": 3,
                "deck_name": "Kiku",
                "interval_days": 35,
                "due": 35,
                "card_ord": 0,
                "queue": 2,
                "card_type": 2,
                "reps": 20,
                "lapses": 0,
                "is_suspended": False,
                "is_active": True,
                "is_mature": True,
            },
            {
                "card_id": 44,
                "note_id": 4,
                "deck_name": "Kiku",
                "interval_days": 40,
                "due": 40,
                "card_ord": 0,
                "queue": 2,
                "card_type": 2,
                "reps": 22,
                "lapses": 0,
                "is_suspended": False,
                "is_active": True,
                "is_mature": True,
            },
        ),
        settings=settings,
    )
    service = KanjiCompanionService(
        storage=storage,
        content_provider=FakeContentProvider(),
    )

    initial = service.refresh_study_seeds()
    assert initial["introducedCount"] == 1

    conn = storage.connect()
    try:
        conn.execute(
            """
            UPDATE study_items
            SET item_status = ?,
                total_reviews = ?,
                last_reviewed_ts = ?,
                due_ts = ?
            WHERE profile = ? AND kanji = ?
            """,
            ("review", 6, 100, 100 + 30 * 86400, "default", "学"),
        )
        conn.commit()
    finally:
        conn.close()

    refreshed = service.refresh_study_seeds()
    item = service.study_service.get_item_detail("default", "学")
    overview = service.get_study_overview()

    assert refreshed["inactivatedCount"] == 1
    assert refreshed["overview"]["currentProblemSeedCount"] == 0
    assert overview["currentProblemSeedCount"] == 0
    assert item["itemStatus"] == "inactive"
    assert item["isProblemSeed"] is True


def test_dashboard_wrapper_functions_cover_skipped_cards_and_field_only_search(
    monkeypatch,
) -> None:
    lookup = SimpleNamespace(
        ranks={"学": 3.0, "年": 8.5},
        warnings=("fixture warning",),
        source_kind="fixture",
    )
    monkeypatch.setattr(dashboard_module, "_load_kanji_frequency_lookup", lambda _config: lookup)

    config = AppSettings(model_names=(), kanji_dashboard_mature_support_threshold=2)
    collection = DashboardCollection(
        query_results={
            "is:suspended": [1, 2],
            "-is:suspended": [3],
            "prop:ivl>=21 -is:suspended": [4],
        },
        notes_by_card_id={
            1: {"Expression": "学校"},
            2: {"Expression": ""},
            3: {"Reading": "まなぶ"},
            4: {"Expression": "学年"},
        },
    )

    assert dashboard_module.load_kanji_frequency_lookup(config) is lookup

    payload = dashboard_module.build_kanji_dashboard(collection, config)
    detail = dashboard_module.build_kanji_detail(collection, config, " 学 ")
    rows = {row["kanji"]: row for row in payload["rows"]}

    assert payload["warnings"] == [
        "fixture warning",
        "Skipped 1 suspended cards because the Expression field was empty.",
        "Skipped 1 non-suspended supported cards because the Expression field was empty.",
    ]
    assert payload["jitenSourceKind"] == "fixture"
    assert rows["学"]["browserSearch"] == '"Expression:*学*"'
    assert detail["kanji"] == "学"
    assert detail["browserSearch"] == '"Expression:*学*"'
    assert detail["matureSupportCount"] == 1


def test_dashboard_public_serializers_cover_none_integer_and_float_ranks() -> None:
    row = KanjiDashboardRow(
        kanji="学",
        jiten_rank=3.0,
        collection_expression_count=1,
        suspended_expression_count=1,
        active_recurring_expression_count=0,
        mature_support_count=0,
        support_deficit=2,
        is_unknown=True,
        browser_search='"Expression:*学*"',
    ).to_dict()
    summary = KanjiDashboardSummary(
        analyzed_suspended_card_count=1,
        analyzed_suspended_expression_count=1,
        unknown_kanji_count=1,
        average_kanji_rank=2.5,
        ranked_kanji_count=2,
        total_kanji_count=3,
        mature_support_threshold=3,
    ).to_dict()
    detail = KanjiDashboardDetail(
        kanji="空",
        jiten_rank=None,
        collection_expressions=("空",),
        affected_suspended_expressions=("空",),
        active_recurring_expressions=(),
        mature_supporting_expressions=(),
        support_deficit=3,
        is_unknown=True,
        browser_search='"Expression:*空*"',
    ).to_dict()
    seed = ProblemKanjiSeed(
        kanji="校",
        jiten_rank=4.0,
        collection_expressions=("学校",),
        affected_suspended_expressions=("学校",),
        active_recurring_expressions=(),
        mature_supporting_expressions=(),
        support_deficit=1,
        browser_search='"Expression:*校*"',
    ).to_dict()

    assert row["jitenRank"] == 3
    assert summary["averageKanjiRank"] == 2.5
    assert detail["jitenRank"] is None
    assert seed["jitenRank"] == 4


def test_ankiconnect_sync_snapshot_filters_invalid_ids_and_payloads() -> None:
    client = ParsingStubAnkiConnectClient(
        {
            "findNotes": lambda params: [1, "2", None, "bad"]
            if params["query"] == 'note:"Kiku"'
            else [],
            "notesInfo": [
                "ignore me",
                {
                    "note": 1,
                    "modelName": "Kiku",
                    "fields": {
                        "Expression": {"value": "学校"},
                        "Reading": {"value": "がっこう"},
                        "Meaning": {"value": "school"},
                        "Ignored": "not-a-mapping",
                    },
                    "tags": ["tagged", ""],
                    "cards": [11, "12", "bad", None],
                },
                {
                    "noteId": 0,
                    "modelName": "Kiku",
                    "fields": {"Expression": {"value": "skip"}},
                    "cards": [13],
                },
            ],
            "cardsInfo": [
                "ignore me",
                {
                    "cardId": 11,
                    "note": 1,
                    "deckName": "Kiku",
                    "ivl": 5,
                    "queue": 2,
                    "type": 2,
                    "reps": 1,
                    "lapses": 0,
                },
                {
                    "cardId": 12,
                    "note": 0,
                    "deckName": "Kiku",
                },
                {
                    "cardId": 0,
                    "note": 1,
                    "deckName": "Kiku",
                },
            ],
        }
    )

    snapshot = client.sync_snapshot(parse_config({"noteModels": ["Kiku"]}))

    assert len(snapshot.notes) == 1
    assert snapshot.notes[0].note_id == 1
    assert snapshot.notes[0].card_ids == (11, 12)
    assert snapshot.notes[0].tags == ("tagged",)
    assert len(snapshot.cards) == 1
    assert snapshot.cards[0].card_id == 11
    assert snapshot.cards[0].interval_days == 5


def test_ankiconnect_invoke_surfaces_transport_and_response_errors(monkeypatch) -> None:
    client = AnkiConnectClient("http://127.0.0.1:8765")

    def raise_urlerror(*_args, **_kwargs):
        raise URLError("down")

    monkeypatch.setattr(ankiconnect_module, "urlopen", raise_urlerror)
    with pytest.raises(
        AnkiConnectError,
        match="Could not reach AnkiConnect at http://127.0.0.1:8765",
    ):
        client._invoke("findNotes", query='note:"Kiku"')

    monkeypatch.setattr(
        ankiconnect_module,
        "urlopen",
        lambda *_args, **_kwargs: FakeHTTPResponse(b"{not json}"),
    )
    with pytest.raises(
        AnkiConnectError,
        match="AnkiConnect returned invalid JSON for action findNotes.",
    ):
        client._invoke("findNotes")

    monkeypatch.setattr(
        ankiconnect_module,
        "urlopen",
        lambda *_args, **_kwargs: FakeHTTPResponse(b"[]"),
    )
    with pytest.raises(
        AnkiConnectError,
        match="AnkiConnect returned an invalid payload for action findNotes.",
    ):
        client._invoke("findNotes")

    monkeypatch.setattr(
        ankiconnect_module,
        "urlopen",
        lambda *_args, **_kwargs: FakeHTTPResponse(
            json.dumps({"result": None, "error": "bridge failure"}).encode("utf-8")
        ),
    )
    with pytest.raises(AnkiConnectError, match="bridge failure"):
        client._invoke("findNotes")
