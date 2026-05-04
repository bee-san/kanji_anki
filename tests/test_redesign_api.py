from __future__ import annotations

import asyncio
from pathlib import Path

import httpx

from kanji_leech_dashboard.api import create_app
from kanji_leech_dashboard.config import parse_config
from kanji_leech_dashboard.service import KanjiCompanionService
from kanji_leech_dashboard.storage import AppStorage

from .redesign_helpers import (
    FakeContentProvider,
    FakeSnapshotClient,
    build_collection_snapshot,
    prime_app_home,
)


def make_client(tmp_path: Path, monkeypatch, snapshot=None) -> tuple[httpx.ASGITransport, KanjiCompanionService]:
    prime_app_home(monkeypatch, tmp_path)
    storage = AppStorage(tmp_path / "app.sqlite3")
    storage.save_settings(parse_config({"noteModels": ["Kiku"]}))
    storage.rebuild_analysis(storage.load_settings())
    service = KanjiCompanionService(
        storage=storage,
        content_provider=FakeContentProvider(),
        ankiconnect_factory=lambda *_args: FakeSnapshotClient(
            snapshot if snapshot is not None else build_collection_snapshot()
        ),
    )
    return httpx.ASGITransport(app=create_app(service)), service


def run(coro):
    return asyncio.run(coro)


def test_health_and_settings_routes(tmp_path: Path, monkeypatch) -> None:
    transport, _service = make_client(tmp_path, monkeypatch)

    async def scenario():
        async with httpx.AsyncClient(transport=transport, base_url="http://testserver") as client:
            return (
                await client.get("/api/health"),
                await client.get("/api/settings"),
                await client.put("/api/settings", json={"noteModels": []}),
            )

    health, settings, invalid = run(scenario())

    assert health.status_code == 200
    assert settings.json()["noteModels"] == ["Kiku"]
    assert invalid.status_code == 400


def test_sync_dashboard_and_kanji_routes(tmp_path: Path, monkeypatch) -> None:
    transport, _service = make_client(tmp_path, monkeypatch, snapshot=build_collection_snapshot())

    async def scenario():
        async with httpx.AsyncClient(transport=transport, base_url="http://testserver") as client:
            return (
                await client.post("/api/sync/ankiconnect"),
                await client.get("/api/dashboard"),
                await client.get("/api/kanji/%E5%AD%A6"),
            )

    sync_response, dashboard, detail = run(scenario())

    assert sync_response.status_code == 200
    assert sync_response.json()["syncRun"]["status"] == "success"
    assert dashboard.json()["summary"]["totalKanjiCount"] >= 1
    assert detail.json()["kanji"] == "学"


def test_empty_sync_and_study_overview_have_no_problem_kanji(tmp_path: Path, monkeypatch) -> None:
    empty_snapshot = build_collection_snapshot(
        suspended_expression="",
        active_expression="",
        mature_expression="",
    )
    transport, _service = make_client(tmp_path, monkeypatch, snapshot=empty_snapshot)

    async def scenario():
        async with httpx.AsyncClient(transport=transport, base_url="http://testserver") as client:
            await client.post("/api/sync/ankiconnect")
            return (
                await client.get("/api/dashboard"),
                await client.get("/api/study/overview"),
            )

    dashboard, overview = run(scenario())

    assert dashboard.json()["rows"] == []
    assert overview.json()["currentProblemSeedCount"] == 0


def test_duplicate_review_submission_returns_duplicate_true(tmp_path: Path, monkeypatch) -> None:
    transport, _service = make_client(tmp_path, monkeypatch, snapshot=build_collection_snapshot())

    async def scenario():
        async with httpx.AsyncClient(transport=transport, base_url="http://testserver") as client:
            await client.post("/api/sync/ankiconnect")
            seed_refresh = await client.post("/api/study/seeds/refresh")
            session = (await client.post("/api/study/sessions", json={"mode": "new"})).json()
            payload = {
                "kanji": session["session"]["kanji"],
                "reviewToken": session["session"]["reviewToken"],
                "promptType": session["session"]["promptType"],
                "rating": "good",
                "handwritingResult": {"attempted": True, "passed": True, "score": 0.95},
                "hintsUsed": 0,
            }
            return (
                seed_refresh,
                await client.post("/api/study/reviews", json=payload),
                await client.post("/api/study/reviews", json=payload),
            )

    seed_refresh, first, duplicate = run(scenario())

    assert seed_refresh.status_code == 200
    assert first.json()["duplicate"] is False
    assert duplicate.json()["duplicate"] is True


def test_study_session_exposes_handwriting_policy_and_failed_write_rejects_good(
    tmp_path: Path,
    monkeypatch,
) -> None:
    transport, _service = make_client(tmp_path, monkeypatch, snapshot=build_collection_snapshot())

    async def scenario():
        async with httpx.AsyncClient(transport=transport, base_url="http://testserver") as client:
            await client.post("/api/sync/ankiconnect")
            await client.post("/api/study/seeds/refresh")
            session = await client.post("/api/study/sessions", json={"mode": "new"})
            payload = session.json()["session"]
            rejected = await client.post(
                "/api/study/reviews",
                json={
                    "kanji": payload["kanji"],
                    "reviewToken": payload["reviewToken"],
                    "promptType": payload["promptType"],
                    "rating": "good",
                    "handwritingResult": {
                        "attempted": True,
                        "passed": False,
                        "score": 0.1,
                        "evaluationMode": "guided",
                        "selfAssessment": "guided-retry",
                    },
                    "hintsUsed": 0,
                },
            )
            return session, rejected

    session, rejected = run(scenario())

    assert session.status_code == 200
    assert session.json()["session"]["handwritingPolicy"]["guideMode"] == "trace"
    assert session.json()["session"]["handwritingPolicy"]["allowedRatingsOnFailure"] == ["again"]
    assert rejected.status_code == 400
    assert "only allows again" in rejected.json()["detail"]


def test_missing_stroke_asset_and_legacy_routes_are_not_served(tmp_path: Path, monkeypatch) -> None:
    transport, service = make_client(tmp_path, monkeypatch)

    async def scenario():
        async with httpx.AsyncClient(transport=transport, base_url="http://testserver") as client:
            return await client.get("/api/assets/stroke-order/%E7%A9%BA.svg")

    missing_asset = run(scenario())
    route_paths = {getattr(route, "path", "") for route in create_app(service).router.routes}

    assert missing_asset.status_code == 404
    assert "/sort" not in route_paths
    assert "/dashboard/data" not in route_paths
