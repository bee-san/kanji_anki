from __future__ import annotations

import json
import os
from contextlib import contextmanager
from dataclasses import dataclass
from pathlib import Path
from tempfile import TemporaryDirectory
from typing import Any, Iterator, Mapping

from . import __version__
from .config import parse_config
from .service import KanjiCompanionService
from .state import APP_HOME_ENV
from .storage import AppStorage
from .study import StudyService
from .study_content import KanjiStudyContent

JITEN_CACHE_FIXTURE = """kanji,rank
学,3
校,6
年,8
"""

FIXTURE_NOTES: tuple[dict[str, Any], ...] = (
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
        "expression": "学ぶ",
        "reading": "まなぶ",
        "meaning": "study",
        "fields": {
            "Expression": "学ぶ",
            "Reading": "まなぶ",
            "Meaning": "study",
        },
        "tags": ("active",),
    },
    {
        "note_id": 3,
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
)

FIXTURE_CARDS: tuple[dict[str, Any], ...] = (
    {
        "card_id": 11,
        "note_id": 1,
        "deck_name": "Kiku",
        "interval_days": 0,
        "modified_ts": 0,
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
        "interval_days": 10,
        "modified_ts": 0,
        "due": 10,
        "card_ord": 0,
        "queue": 2,
        "card_type": 2,
        "reps": 7,
        "lapses": 1,
        "is_suspended": False,
        "is_active": True,
        "is_mature": False,
    },
    {
        "card_id": 33,
        "note_id": 3,
        "deck_name": "Kiku",
        "interval_days": 30,
        "modified_ts": 0,
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
)


class FixtureContentProvider:
    def attribution(self) -> dict[str, object]:
        return {
            "dictionary": {"name": "KANJIDIC2", "source": "fixture", "license": "fixture"},
            "strokeData": {"name": "KanjiVG", "source": "fixture", "license": "fixture"},
        }

    def get_content(self, kanji: str) -> KanjiStudyContent:
        if kanji == "学":
            return KanjiStudyContent(
                kanji="学",
                keyword="study",
                meanings=("study",),
                readings=("ガク", "まな.ぶ"),
                primary_readings=("ガク",),
                components=("冖", "子"),
                component_hint="roof + child",
                stroke_count=8,
                stroke_paths=("M8 8 L32 8", "M18 18 L18 38"),
                dictionary_source="fixture-dictionary",
                stroke_source="fixture-strokes",
                data_warnings=tuple(),
            )
        return KanjiStudyContent(
            kanji=kanji,
            keyword="fixture",
            meanings=("fixture",),
            readings=(kanji,),
            primary_readings=(kanji,),
            components=tuple(),
            component_hint="",
            stroke_count=1,
            stroke_paths=("M0 0 L1 1",),
            dictionary_source="fixture-dictionary",
            stroke_source="fixture-strokes",
            data_warnings=tuple(),
        )

    def stroke_svg_path(self, kanji: str) -> Path | None:
        return None


@dataclass
class FixtureClock:
    value: int = 1_700_000_000

    def now(self) -> int:
        return self.value

    def advance(self, seconds: int) -> None:
        self.value += seconds


class SequentialTokenFactory:
    def __init__(self) -> None:
        self._issued = 0

    def __call__(self) -> str:
        self._issued += 1
        return f"review-token-{self._issued}"


@dataclass
class FixtureHarness:
    app_home: Path
    clock: FixtureClock
    service: KanjiCompanionService


def build_android_parity_fixture_bundle() -> dict[str, Any]:
    base = _build_base_service()
    try:
        base_bundle = _build_base_bundle(base)
        happy_path = _build_happy_path_scenario()
        handwriting_enforcement = _build_handwriting_enforcement_scenario()
        return {
            "meta": {
                "fixtureVersion": 1,
                "appVersion": __version__,
            },
            **base_bundle,
            "studyScenarios": {
                "happyPath": happy_path,
                "handwritingEnforcement": handwriting_enforcement,
            },
        }
    finally:
        base["tempdir"].cleanup()


def render_android_parity_fixture_bundle() -> str:
    return json.dumps(
        build_android_parity_fixture_bundle(),
        ensure_ascii=False,
        indent=2,
        sort_keys=True,
    )


def write_android_parity_fixture_bundle(path: Path) -> Path:
    output_path = Path(path)
    output_path.parent.mkdir(parents=True, exist_ok=True)
    output_path.write_text(render_android_parity_fixture_bundle() + "\n", encoding="utf-8")
    return output_path


def _build_base_bundle(context: dict[str, Any]) -> dict[str, Any]:
    harness: FixtureHarness = context["harness"]
    service = harness.service
    dashboard = service.get_dashboard()
    detail_map = {
        str(row["kanji"]): service.get_kanji_detail(str(row["kanji"]))
        for row in dashboard["rows"]
    }
    overview_before_refresh = service.get_study_overview()
    refresh = service.refresh_study_seeds()
    overview_after_refresh = service.get_study_overview()
    return _normalize_payload(
        {
            "health": service.get_health(),
            "settings": service.get_settings(),
            "sourceSnapshot": {
                "notes": list(FIXTURE_NOTES),
                "cards": list(FIXTURE_CARDS),
            },
            "dashboard": dashboard,
            "kanjiDetails": detail_map,
            "kanjiDetail": service.get_kanji_detail("学"),
            "studyBaseline": {
                "overviewBeforeRefresh": overview_before_refresh,
                "refreshSeeds": refresh,
                "overviewAfterRefresh": overview_after_refresh,
            },
        },
        app_home=harness.app_home,
    )


def _build_happy_path_scenario() -> dict[str, Any]:
    context = _build_base_service()
    try:
        harness: FixtureHarness = context["harness"]
        service = harness.service
        clock = harness.clock
        refresh = service.refresh_study_seeds()
        session_new = service.create_study_session({"mode": "new"})
        new_session = dict(session_new["session"])
        first_review_request = {
            "kanji": new_session["kanji"],
            "reviewToken": new_session["reviewToken"],
            "promptType": new_session["promptType"],
            "rating": "good",
            "hintsUsed": 0,
            "handwritingResult": {
                "attempted": True,
                "passed": True,
                "score": 0.93,
                "evaluationMode": "guided",
            },
        }
        first_review = service.submit_study_review(first_review_request)

        session_mixed = service.create_study_session({"mode": "mixed"})
        mixed_session = dict(session_mixed["session"])
        second_review_request = {
            "kanji": mixed_session["kanji"],
            "reviewToken": mixed_session["reviewToken"],
            "promptType": mixed_session["promptType"],
            "rating": "good",
            "hintsUsed": 1,
            "handwritingResult": {
                "attempted": True,
                "passed": True,
                "score": 0.97,
                "evaluationMode": "guided",
            },
        }
        second_review = service.submit_study_review(second_review_request)

        clock.advance(10 * 60)
        session_review = service.create_study_session({"mode": "review"})
        review_session = dict(session_review["session"])
        third_review_request = {
            "kanji": review_session["kanji"],
            "reviewToken": review_session["reviewToken"],
            "promptType": review_session["promptType"],
            "rating": "good",
            "hintsUsed": 0,
            "handwritingResult": {
                "attempted": True,
                "passed": True,
                "score": 0.99,
                "evaluationMode": "guided",
            },
        }
        third_review = service.submit_study_review(third_review_request)
        payload = {
            "refreshSeeds": refresh,
            "sessionNew": session_new,
            "firstReview": {
                "request": first_review_request,
                "response": first_review,
            },
            "sessionMixed": session_mixed,
            "secondReview": {
                "request": second_review_request,
                "response": second_review,
            },
            "sessionReview": session_review,
            "thirdReview": {
                "request": third_review_request,
                "response": third_review,
            },
            "overviewAfterPath": service.get_study_overview(),
            "itemAfterPath": service.study_service.get_item_detail("default", "学"),
        }
        return _normalize_payload(payload, app_home=harness.app_home)
    finally:
        context["tempdir"].cleanup()


def _build_handwriting_enforcement_scenario() -> dict[str, Any]:
    context = _build_base_service()
    try:
        harness: FixtureHarness = context["harness"]
        service = harness.service
        service.refresh_study_seeds()
        session_new = service.create_study_session({"mode": "new"})
        session = dict(session_new["session"])
        invalid_review_request = {
            "kanji": session["kanji"],
            "reviewToken": session["reviewToken"],
            "promptType": session["promptType"],
            "rating": "good",
            "hintsUsed": 0,
            "handwritingResult": {
                "attempted": True,
                "passed": False,
                "score": 0.11,
                "evaluationMode": "guided",
            },
        }
        try:
            service.submit_study_review(invalid_review_request)
        except ValueError as error:
            invalid_review_error = str(error)
        else:  # pragma: no cover - defensive
            invalid_review_error = "Expected handwriting enforcement error."

        retry_review_request = {
            "kanji": session["kanji"],
            "reviewToken": session["reviewToken"],
            "promptType": session["promptType"],
            "rating": "again",
            "hintsUsed": 0,
            "handwritingResult": {
                "attempted": True,
                "passed": False,
                "score": 0.11,
                "evaluationMode": "manual-override",
                "selfAssessment": "override-retry",
            },
        }
        retry_review = service.submit_study_review(retry_review_request)
        payload = {
            "sessionNew": session_new,
            "invalidReview": {
                "request": invalid_review_request,
                "error": invalid_review_error,
            },
            "retryReview": {
                "request": retry_review_request,
                "response": retry_review,
            },
            "itemAfterRetry": service.study_service.get_item_detail("default", "学"),
        }
        return _normalize_payload(payload, app_home=harness.app_home)
    finally:
        context["tempdir"].cleanup()


def _build_base_service() -> dict[str, Any]:
    tempdir = TemporaryDirectory()
    app_home = Path(tempdir.name)
    with _temporary_app_home(app_home):
        data_dir = app_home / "data"
        data_dir.mkdir(parents=True, exist_ok=True)
        (data_dir / "jiten_frequency_kanji.csv").write_text(
            JITEN_CACHE_FIXTURE,
            encoding="utf-8",
        )
        db_path = app_home / "app.sqlite3"
        settings = parse_config(
            {
                "noteModels": ["Kiku"],
                "kanjiSupportThreshold": 3,
            }
        )
        content_provider = FixtureContentProvider()
        clock = FixtureClock()
        storage = AppStorage(db_path)
        storage.save_settings(settings)
        storage.replace_source_snapshot(
            notes=FIXTURE_NOTES,
            cards=FIXTURE_CARDS,
            settings=settings,
        )
        study_service = StudyService(
            db_path=db_path,
            content_provider=content_provider,
            now_factory=clock.now,
            token_factory=SequentialTokenFactory(),
        )
        service = KanjiCompanionService(
            storage=storage,
            study_service=study_service,
            content_provider=content_provider,
        )
        return {
            "tempdir": tempdir,
            "harness": FixtureHarness(
                app_home=app_home,
                clock=clock,
                service=service,
            ),
        }


@contextmanager
def _temporary_app_home(app_home: Path) -> Iterator[None]:
    previous = os.environ.get(APP_HOME_ENV)
    os.environ[APP_HOME_ENV] = str(app_home)
    try:
        yield
    finally:
        if previous is None:
            os.environ.pop(APP_HOME_ENV, None)
        else:
            os.environ[APP_HOME_ENV] = previous


def _normalize_payload(payload: Any, *, app_home: Path) -> Any:
    repo_root = Path(__file__).resolve().parent.parent
    replacements = (
        (str(app_home), "<app-home>"),
        (str(repo_root), "<repo-root>"),
    )
    return _normalize_value(payload, replacements)


def _normalize_value(value: Any, replacements: tuple[tuple[str, str], ...]) -> Any:
    if isinstance(value, Mapping):
        return {str(key): _normalize_value(item, replacements) for key, item in value.items()}
    if isinstance(value, list):
        return [_normalize_value(item, replacements) for item in value]
    if isinstance(value, tuple):
        return [_normalize_value(item, replacements) for item in value]
    if isinstance(value, str):
        normalized = value
        for source, target in replacements:
            normalized = normalized.replace(source, target)
        return normalized
    return value
