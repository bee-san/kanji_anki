from __future__ import annotations

import logging
import threading
import time
from pathlib import Path
from typing import Any, Callable, Mapping

from . import __version__
from .ankiconnect import AnkiConnectClient, AnkiConnectError
from .config import AppSettings, ConfigValidationError, parse_config
from .dashboard import KanjiNotFoundError
from .state import database_path, webapp_dir
from .storage import DEFAULT_PROFILE, AppStorage
from .study import StudyItemNotFoundError, StudyService
from .study_content import StudyContentProvider

logger = logging.getLogger(__name__)


class KanjiCompanionService:
    def __init__(
        self,
        *,
        db_path: Path | None = None,
        storage: AppStorage | None = None,
        study_service: StudyService | None = None,
        content_provider: StudyContentProvider | None = None,
        ankiconnect_factory: Callable[[str, int], AnkiConnectClient] | None = None,
    ) -> None:
        self.storage = storage or AppStorage(db_path or database_path())
        self.content_provider = content_provider or StudyContentProvider()
        self.study_service = study_service or StudyService(
            db_path=self.storage.db_path,
            content_provider=self.content_provider,
        )
        self._ankiconnect_factory = ankiconnect_factory or (
            lambda base_url, timeout: AnkiConnectClient(
                base_url,
                timeout_seconds=timeout,
            )
        )
        self._storage_operation_lock = threading.Lock()
        self._runtime_lock = threading.Lock()
        self._polling_stop = threading.Event()
        self._polling_wakeup = threading.Event()
        self._polling_thread: threading.Thread | None = None
        self._last_sync_finished_monotonic: float | None = None
        self._force_poll_now = False

    def start_runtime_polling(self) -> None:
        with self._runtime_lock:
            if self._polling_thread is not None and self._polling_thread.is_alive():
                return
            self._polling_stop.clear()
            self._polling_wakeup.clear()
            self._polling_thread = threading.Thread(
                target=self._run_runtime_polling,
                name="kanji-companion-runtime-polling",
                daemon=True,
            )
            self._polling_thread.start()

    def stop_runtime_polling(self) -> None:
        with self._runtime_lock:
            thread = self._polling_thread
            if thread is None:
                return
            self._polling_stop.set()
            self._polling_wakeup.set()
        thread.join()
        with self._runtime_lock:
            if self._polling_thread is thread:
                self._polling_thread = None
            self._polling_stop.clear()
            self._polling_wakeup.clear()
            self._force_poll_now = False

    def get_settings(self) -> dict[str, Any]:
        return self.storage.load_settings().to_dict()

    def update_settings(self, payload: Mapping[str, Any] | None) -> dict[str, Any]:
        settings = parse_config(payload or {})
        with self._storage_operation_lock:
            self.storage.save_settings(settings)
            self.storage.rebuild_analysis(settings)
        self._wake_runtime_loop(force_poll=settings.polling_enabled)
        return settings.to_dict()

    def sync_ankiconnect(self) -> dict[str, Any]:
        with self._storage_operation_lock:
            return self._sync_ankiconnect_locked()

    def _sync_ankiconnect_locked(self) -> dict[str, Any]:
        settings = self.storage.load_settings()
        sync_run_id = self.storage.start_sync_run("ankiconnect")
        try:
            client = self._ankiconnect_factory(
                settings.ankiconnect_url,
                settings.jiten_request_timeout_seconds,
            )
            snapshot = client.sync_snapshot(settings)
            self.storage.replace_source_snapshot(
                notes=(
                    {
                        "note_id": note.note_id,
                        "model_name": note.model_name,
                        "expression": note.expression,
                        "reading": note.reading,
                        "meaning": note.meaning,
                        "fields": note.fields,
                        "tags": note.tags,
                    }
                    for note in snapshot.notes
                ),
                cards=(
                    {
                        "card_id": card.card_id,
                        "note_id": card.note_id,
                        "deck_name": card.deck_name,
                        "interval_days": card.interval_days,
                        "due": card.due,
                        "card_ord": card.card_ord,
                        "queue": card.queue,
                        "card_type": card.card_type,
                        "reps": card.reps,
                        "lapses": card.lapses,
                        "modified_ts": card.modified_ts,
                        "is_suspended": card.is_suspended,
                        "is_active": card.is_active,
                        "is_mature": card.is_active and card.interval_days >= settings.mature_days,
                    }
                    for card in snapshot.cards
                ),
                settings=settings,
            )
            self.storage.finish_sync_run(
                sync_run_id,
                status="success",
                note_count=len(snapshot.notes),
                card_count=len(snapshot.cards),
            )
        except Exception as error:
            self.storage.finish_sync_run(
                sync_run_id,
                status="failed",
                note_count=0,
                card_count=0,
                error_message=str(error),
            )
            raise
        finally:
            self._record_sync_attempt_finished()

        return {
            "settings": settings.to_dict(),
            "syncRun": self.storage.latest_sync_run().to_dict(),
            "dashboard": self.get_dashboard(),
            "sourceCounts": self.storage.source_counts(),
        }

    def rebuild_analysis(self) -> dict[str, Any]:
        settings = self.storage.load_settings()
        with self._storage_operation_lock:
            result = self.storage.rebuild_analysis(settings)
        self._wake_runtime_loop()
        return {
            "settings": settings.to_dict(),
            "dashboard": self.get_dashboard(),
            "analysis": result,
        }

    def get_dashboard(self) -> dict[str, Any]:
        payload = self.storage.load_dashboard()
        payload["settings"] = self.storage.load_settings().to_dict()
        latest_sync = self.storage.latest_sync_run()
        payload["latestSyncRun"] = latest_sync.to_dict() if latest_sync else None
        payload["sourceCounts"] = self.storage.source_counts()
        return payload

    def get_kanji_detail(self, kanji: str) -> dict[str, Any]:
        detail = self.storage.load_kanji_detail(kanji)
        if detail is None:
            raise KanjiNotFoundError(f"Kanji '{kanji}' was not found in the current study scope.")
        content = self.content_provider.get_content(kanji)
        return {
            **detail,
            "classification": {
                "jitenRank": detail.get("jitenRank"),
            },
            "readings": {
                "on": list(content.primary_readings),
                "kun": list(
                    reading for reading in content.readings if reading not in content.primary_readings
                ),
                "nanori": [],
            },
            "meanings": {
                "en": list(content.meanings),
            },
            "structure": {
                "kanjiVgElements": list(content.components),
                "componentHint": content.component_hint,
            },
            "writing": {
                "strokeCount": content.stroke_count,
                "altStrokeCounts": [],
            },
            "collection": {
                "browserSearch": detail.get("browserSearch", ""),
                "counts": {
                    "collectionExpressionCount": detail.get("collectionExpressionCount", 0),
                    "suspendedExpressionCount": detail.get("suspendedExpressionCount", 0),
                    "activeRecurringExpressionCount": detail.get(
                        "activeRecurringExpressionCount",
                        0,
                    ),
                    "matureSupportCount": detail.get("matureSupportCount", 0),
                    "supportDeficit": detail.get("supportDeficit", 0),
                    "isUnknown": detail.get("isUnknown", False),
                },
                "collectionExpressions": detail.get("collectionExpressions", []),
                "suspendedExpressions": detail.get("affectedSuspendedExpressions", []),
                "activeRecurringExpressions": detail.get("activeRecurringExpressions", []),
                "matureSupportingExpressions": detail.get("matureSupportingExpressions", []),
            },
            "relatedVocabulary": {
                "collectionExamples": detail.get("collectionExpressions", []),
                "painExamples": detail.get("affectedSuspendedExpressions", []),
                "bridgeExamples": detail.get("activeRecurringExpressions", []),
                "matureExamples": detail.get("matureSupportingExpressions", []),
            },
            "sources": [
                {
                    "id": "dictionary",
                    "name": "KANJIDIC2",
                    "role": "Dictionary cache",
                    "description": content.dictionary_source,
                },
                {
                    "id": "stroke-order",
                    "name": "KanjiVG",
                    "role": "Stroke order cache",
                    "description": content.stroke_source,
                },
                *(
                    {
                        "id": "warning",
                        "name": "Data warning",
                        "role": "Fallback",
                        "description": warning,
                    }
                    for warning in content.data_warnings
                ),
            ],
        }

    def get_study_overview(self) -> dict[str, Any]:
        return self.study_service.overview(
            DEFAULT_PROFILE,
            current_problem_seed_count=self.storage.problem_seed_count(),
        )

    def refresh_study_seeds(self) -> dict[str, Any]:
        with self._storage_operation_lock:
            settings = self.storage.load_settings()
            support_loader = getattr(self.storage, "load_anki_support_lookup", None)
            return self.study_service.sync_problem_kanji(
                DEFAULT_PROFILE,
                self.storage.load_problem_seeds(),
                mature_anki_card_counts=self.storage.load_mature_anki_card_counts(),
                anki_support_lookup=support_loader() if callable(support_loader) else None,
                srs_mature_days=settings.mature_days,
            )

    def create_study_session(self, payload: Mapping[str, Any] | None) -> dict[str, Any]:
        request = dict(payload or {})
        return self.study_service.next_session(
            DEFAULT_PROFILE,
            mode=str(request.get("mode") or "mixed"),
        )

    def submit_study_review(self, payload: Mapping[str, Any] | None) -> dict[str, Any]:
        request = dict(payload or {})
        return self.study_service.review(
            DEFAULT_PROFILE,
            kanji=str(request.get("kanji") or "").strip(),
            review_token=str(request.get("reviewToken") or "").strip(),
            prompt_type=str(request.get("promptType") or "").strip(),
            rating=str(request.get("rating") or "").strip(),
            handwriting_result=request.get("handwritingResult"),
            hints_used=int(request.get("hintsUsed") or 0),
        )

    def get_health(self) -> dict[str, Any]:
        latest_sync = self.storage.latest_sync_run()
        return {
            "ok": True,
            "version": __version__,
            "webAppPath": str(webapp_dir()),
            "databasePath": str(self.storage.db_path),
            "settings": self.storage.load_settings().to_dict(),
            "sourceCounts": self.storage.source_counts(),
            "latestSyncRun": latest_sync.to_dict() if latest_sync else None,
        }

    def get_stroke_order_svg_path(self, kanji: str) -> Path | None:
        return self.content_provider.stroke_svg_path(kanji)

    def _run_runtime_polling(self) -> None:
        while not self._polling_stop.is_set():
            settings = self.storage.load_settings()
            if not settings.polling_enabled:
                self._wait_for_runtime_wakeup(timeout=None)
                continue

            wait_seconds = self._polling_wait_seconds(settings)
            if self._wait_for_runtime_wakeup(timeout=wait_seconds):
                continue
            if self._polling_stop.is_set():
                break
            if not self._storage_operation_lock.acquire(blocking=False):
                self._wait_for_runtime_wakeup(timeout=None)
                continue
            try:
                self._sync_ankiconnect_locked()
            except Exception:
                logger.exception("Background AnkiConnect sync failed.")
            finally:
                self._storage_operation_lock.release()

    def _polling_wait_seconds(self, settings: AppSettings) -> float:
        with self._runtime_lock:
            if self._force_poll_now:
                return 0.0
            last_sync_finished = self._last_sync_finished_monotonic
        if last_sync_finished is None:
            return 0.0
        return max(
            settings.polling_interval_seconds - (time.monotonic() - last_sync_finished),
            0.0,
        )

    def _wait_for_runtime_wakeup(self, *, timeout: float | None) -> bool:
        woke = self._polling_wakeup.wait(timeout)
        if woke:
            self._polling_wakeup.clear()
        return woke

    def _record_sync_attempt_finished(self) -> None:
        with self._runtime_lock:
            self._last_sync_finished_monotonic = time.monotonic()
            self._force_poll_now = False
        self._polling_wakeup.set()

    def _wake_runtime_loop(self, *, force_poll: bool | None = None) -> None:
        with self._runtime_lock:
            if force_poll is not None:
                self._force_poll_now = force_poll
        self._polling_wakeup.set()
