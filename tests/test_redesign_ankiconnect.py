from __future__ import annotations

from pathlib import Path

import pytest

from kanji_leech_dashboard.ankiconnect import AnkiConnectClient, AnkiConnectError
from kanji_leech_dashboard.config import parse_config
from kanji_leech_dashboard.service import KanjiCompanionService
from kanji_leech_dashboard.storage import AppStorage

from .redesign_helpers import FakeContentProvider, FakeSnapshotClient, build_collection_snapshot, prime_app_home


class StubAnkiConnectClient(AnkiConnectClient):
    def __init__(self, responses):
        super().__init__("http://example.invalid")
        self._responses = responses

    def _invoke(self, action: str, **params):
        response = self._responses[action]
        return response(params) if callable(response) else response


def test_adapter_builds_notes_and_cards_from_canned_responses() -> None:
    client = StubAnkiConnectClient(
        {
            "findNotes": lambda params: [1, 2] if params["query"] == 'note:"Kiku"' else [],
            "notesInfo": [
                {
                    "noteId": 1,
                    "modelName": "Kiku",
                    "fields": {
                        "Expression": {"value": "学校"},
                        "Reading": {"value": "がっこう"},
                        "Meaning": {"value": "school"},
                    },
                    "tags": ["blocked"],
                    "cards": [11],
                },
                {
                    "noteId": 2,
                    "modelName": "Kiku",
                    "fields": {
                        "Expression": {"value": "学ぶ"},
                        "Reading": {"value": "まなぶ"},
                        "Meaning": {"value": "study"},
                    },
                    "tags": ["active"],
                    "cards": [22],
                },
            ],
            "cardsInfo": [
                {
                    "cardId": 11,
                    "note": 1,
                    "deckName": "Kiku",
                    "interval": 0,
                    "due": 0,
                    "ord": 0,
                    "queue": -1,
                    "type": 0,
                    "reps": 0,
                    "lapses": 0,
                },
                {
                    "cardId": 22,
                    "note": 2,
                    "deckName": "Kiku",
                    "interval": 14,
                    "due": 7,
                    "ord": 0,
                    "queue": 2,
                    "type": 2,
                    "reps": 4,
                    "lapses": 0,
                },
            ],
        }
    )

    snapshot = client.sync_snapshot(parse_config({"noteModels": ["Kiku"]}))
    assert len(snapshot.notes) == 2
    assert snapshot.notes[0].expression == "学校"
    assert snapshot.cards[0].is_suspended is True
    assert snapshot.cards[1].interval_days == 14


def test_adapter_tolerates_partial_note_payloads() -> None:
    client = StubAnkiConnectClient(
        {
            "findNotes": lambda _params: [1],
            "notesInfo": [
                {
                    "noteId": 1,
                    "modelName": "Kiku",
                    "fields": {"Expression": {"value": "学校"}},
                    "tags": [],
                    "cards": [11],
                }
            ],
            "cardsInfo": [
                {
                    "cardId": 11,
                    "note": 1,
                    "deckName": "Kiku",
                    "queue": 2,
                    "type": 2,
                }
            ],
        }
    )

    snapshot = client.sync_snapshot(parse_config({"noteModels": ["Kiku"]}))
    assert snapshot.notes[0].reading == ""
    assert snapshot.notes[0].meaning == ""
    assert snapshot.cards[0].interval_days == 0


def test_service_sync_updates_changed_cards_and_tombstones_deleted_rows(
    tmp_path: Path,
    monkeypatch,
) -> None:
    prime_app_home(monkeypatch, tmp_path)
    storage = AppStorage(tmp_path / "app.sqlite3")
    settings = parse_config({"noteModels": ["Kiku"]})
    storage.save_settings(settings)

    first_snapshot = build_collection_snapshot()
    changed_snapshot = build_collection_snapshot(
        suspended_expression="学校",
        active_expression="学ぶ",
        mature_expression="",
        suspended_queue=2,
        active_interval=30,
        mature_interval=0,
    )
    second_snapshot = type(changed_snapshot)(
        notes=changed_snapshot.notes[:2],
        cards=changed_snapshot.cards[:2],
    )
    snapshots = [first_snapshot, second_snapshot]
    service = KanjiCompanionService(
        storage=storage,
        content_provider=FakeContentProvider(),
        ankiconnect_factory=lambda *_args: FakeSnapshotClient(snapshots.pop(0)),
    )

    first = service.sync_ankiconnect()
    second = service.sync_ankiconnect()

    assert first["sourceCounts"] == {"noteCount": 3, "cardCount": 3}
    assert second["sourceCounts"] == {"noteCount": 2, "cardCount": 2}
    dashboard = service.get_dashboard()
    assert dashboard["summary"]["analyzedSuspendedCardCount"] == 0
    assert all(row["kanji"] != "年" for row in dashboard["rows"] if row["suspendedExpressionCount"] > 0)


def test_service_sync_surfaces_unreachable_bridge(tmp_path: Path, monkeypatch) -> None:
    prime_app_home(monkeypatch, tmp_path)
    storage = AppStorage(tmp_path / "app.sqlite3")
    storage.save_settings(parse_config({"noteModels": ["Kiku"]}))
    service = KanjiCompanionService(
        storage=storage,
        content_provider=FakeContentProvider(),
        ankiconnect_factory=lambda *_args: FakeSnapshotClient(
            AnkiConnectError("bridge offline")
        ),
    )

    with pytest.raises(AnkiConnectError):
        service.sync_ankiconnect()
