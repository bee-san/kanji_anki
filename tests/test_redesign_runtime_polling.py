from __future__ import annotations

import asyncio
import threading
from pathlib import Path

from kanji_leech_dashboard.api import create_app
from kanji_leech_dashboard.config import parse_config
from kanji_leech_dashboard.service import KanjiCompanionService
from kanji_leech_dashboard.storage import AppStorage

from .redesign_helpers import (
    FakeContentProvider,
    build_collection_snapshot,
    prime_app_home,
)


def run(coro):
    return asyncio.run(coro)


class RecordingSnapshotClient:
    def __init__(
        self,
        *,
        snapshot,
        sync_event: threading.Event,
        sync_count: list[int],
        sync_count_lock: threading.Lock,
    ) -> None:
        self._snapshot = snapshot
        self._sync_event = sync_event
        self._sync_count = sync_count
        self._sync_count_lock = sync_count_lock

    def sync_snapshot(self, _settings):
        with self._sync_count_lock:
            self._sync_count[0] += 1
        self._sync_event.set()
        return self._snapshot


class LifecycleService:
    def __init__(self) -> None:
        self.started = 0
        self.stopped = 0

    def start_runtime_polling(self) -> None:
        self.started += 1

    def stop_runtime_polling(self) -> None:
        self.stopped += 1

    def get_health(self):
        return {"ok": True}

    def get_settings(self):
        return {"noteModels": ["Kiku"]}

    def update_settings(self, _payload):
        return {"noteModels": ["Kiku"]}

    def sync_ankiconnect(self):
        return {"syncRun": {"status": "success"}}

    def get_dashboard(self):
        return {"summary": {}, "rows": [], "warnings": []}

    def get_kanji_detail(self, _kanji):
        return {"kanji": "学"}

    def get_study_overview(self):
        return {"dueCount": 0}

    def refresh_study_seeds(self):
        return {"ok": True}

    def create_study_session(self, _payload):
        return {"ok": True}

    def submit_study_review(self, _payload):
        return {"duplicate": False}

    def get_stroke_order_svg_path(self, _kanji) -> Path | None:
        return None


def test_runtime_polling_syncs_immediately_when_enabled(
    tmp_path: Path,
    monkeypatch,
) -> None:
    prime_app_home(monkeypatch, tmp_path)
    storage = AppStorage(tmp_path / "app.sqlite3")
    storage.save_settings(
        parse_config(
            {
                "noteModels": ["Kiku"],
                "pollingEnabled": True,
                "pollingIntervalSeconds": 600,
            }
        )
    )
    sync_event = threading.Event()
    sync_count = [0]
    sync_count_lock = threading.Lock()
    service = KanjiCompanionService(
        storage=storage,
        content_provider=FakeContentProvider(),
        ankiconnect_factory=lambda *_args: RecordingSnapshotClient(
            snapshot=build_collection_snapshot(),
            sync_event=sync_event,
            sync_count=sync_count,
            sync_count_lock=sync_count_lock,
        ),
    )

    service.start_runtime_polling()
    try:
        assert sync_event.wait(timeout=2.0)
    finally:
        service.stop_runtime_polling()

    assert sync_count[0] == 1
    latest = storage.latest_sync_run()
    assert latest is not None
    assert latest.status == "success"
    assert storage.source_counts() == {"noteCount": 3, "cardCount": 3}


def test_runtime_polling_wakes_when_settings_enable_polling(
    tmp_path: Path,
    monkeypatch,
) -> None:
    prime_app_home(monkeypatch, tmp_path)
    storage = AppStorage(tmp_path / "app.sqlite3")
    storage.save_settings(
        parse_config(
            {
                "noteModels": ["Kiku"],
                "pollingEnabled": False,
                "pollingIntervalSeconds": 600,
            }
        )
    )
    sync_event = threading.Event()
    sync_count = [0]
    sync_count_lock = threading.Lock()
    service = KanjiCompanionService(
        storage=storage,
        content_provider=FakeContentProvider(),
        ankiconnect_factory=lambda *_args: RecordingSnapshotClient(
            snapshot=build_collection_snapshot(),
            sync_event=sync_event,
            sync_count=sync_count,
            sync_count_lock=sync_count_lock,
        ),
    )

    service.start_runtime_polling()
    try:
        assert not sync_event.wait(timeout=0.2)
        service.update_settings(
            {
                "noteModels": ["Kiku"],
                "pollingEnabled": True,
                "pollingIntervalSeconds": 600,
            }
        )
        assert sync_event.wait(timeout=2.0)
    finally:
        service.stop_runtime_polling()

    assert sync_count[0] == 1


def test_create_app_lifespan_starts_and_stops_runtime_polling() -> None:
    service = LifecycleService()
    app = create_app(service)

    async def scenario() -> None:
        async with app.router.lifespan_context(app):
            assert service.started == 1
            assert service.stopped == 0
        assert service.stopped == 1

    run(scenario())
