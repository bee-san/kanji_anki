from __future__ import annotations

from contextlib import asynccontextmanager
from pathlib import Path
from typing import Any

from fastapi import Body, FastAPI, HTTPException
from fastapi.responses import FileResponse, JSONResponse
from fastapi.staticfiles import StaticFiles

from .ankiconnect import AnkiConnectError
from .config import ConfigValidationError
from .dashboard import KanjiNotFoundError
from .service import KanjiCompanionService
from .study import StudyItemNotFoundError
from .state import webapp_dir


def create_app(service: KanjiCompanionService | None = None) -> FastAPI:
    service = service or KanjiCompanionService()

    @asynccontextmanager
    async def lifespan(_app: FastAPI):
        start_runtime_polling = getattr(service, "start_runtime_polling", None)
        stop_runtime_polling = getattr(service, "stop_runtime_polling", None)
        if callable(start_runtime_polling):
            start_runtime_polling()
        try:
            yield
        finally:
            if callable(stop_runtime_polling):
                stop_runtime_polling()

    app = FastAPI(
        title="Kanji Companion Server",
        version="1.0",
        lifespan=lifespan,
    )
    web_dir = webapp_dir()

    @app.exception_handler(ConfigValidationError)
    async def handle_config_error(
        _request: Any,
        error: ConfigValidationError,
    ) -> JSONResponse:
        return JSONResponse(
            status_code=400,
            content={"detail": error.messages},
        )

    @app.exception_handler(ValueError)
    async def handle_value_error(_request: Any, error: ValueError) -> JSONResponse:
        return JSONResponse(status_code=400, content={"detail": str(error)})

    @app.exception_handler(AnkiConnectError)
    async def handle_ankiconnect_error(
        _request: Any,
        error: AnkiConnectError,
    ) -> JSONResponse:
        return JSONResponse(status_code=502, content={"detail": str(error)})

    @app.exception_handler(KanjiNotFoundError)
    @app.exception_handler(StudyItemNotFoundError)
    async def handle_not_found(_request: Any, error: Exception) -> JSONResponse:
        return JSONResponse(status_code=404, content={"detail": str(error)})

    @app.get("/api/health")
    def get_health() -> dict[str, Any]:
        return service.get_health()

    @app.get("/api/settings")
    def get_settings() -> dict[str, Any]:
        return service.get_settings()

    @app.put("/api/settings")
    def put_settings(payload: dict[str, Any] = Body(default={})) -> dict[str, Any]:
        return service.update_settings(payload)

    @app.post("/api/sync/ankiconnect")
    def post_sync_ankiconnect() -> dict[str, Any]:
        return service.sync_ankiconnect()

    @app.get("/api/dashboard")
    def get_dashboard() -> dict[str, Any]:
        return service.get_dashboard()

    @app.get("/api/kanji/{kanji}")
    def get_kanji_detail(kanji: str) -> dict[str, Any]:
        return service.get_kanji_detail(kanji)

    @app.get("/api/study/overview")
    def get_study_overview() -> dict[str, Any]:
        return service.get_study_overview()

    @app.post("/api/study/seeds/refresh")
    def post_refresh_study_seeds() -> dict[str, Any]:
        return service.refresh_study_seeds()

    @app.post("/api/study/sessions")
    def post_study_sessions(
        payload: dict[str, Any] = Body(default={}),
    ) -> dict[str, Any]:
        return service.create_study_session(payload)

    @app.post("/api/study/reviews")
    def post_study_reviews(
        payload: dict[str, Any] = Body(default={}),
    ) -> dict[str, Any]:
        return service.submit_study_review(payload)

    @app.get("/api/assets/stroke-order/{kanji}.svg")
    def get_stroke_order_svg(kanji: str) -> FileResponse:
        svg_path = service.get_stroke_order_svg_path(kanji)
        if svg_path is None or not svg_path.exists():
            raise HTTPException(status_code=404, detail=f"No stroke-order SVG was found for '{kanji}'.")
        return FileResponse(svg_path, media_type="image/svg+xml")

    @app.get("/", include_in_schema=False)
    def get_webapp_index() -> FileResponse:
        index_path = web_dir / "index.html"
        if not index_path.exists():
            raise HTTPException(status_code=404, detail="The hosted web app is not available.")
        return FileResponse(index_path, media_type="text/html")

    app.mount("/", StaticFiles(directory=web_dir, html=False), name="webapp")
    return app
