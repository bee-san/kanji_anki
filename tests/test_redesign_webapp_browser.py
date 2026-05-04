from __future__ import annotations

import threading
import time
from dataclasses import dataclass
from pathlib import Path
from urllib.parse import urlparse

import pytest

uvicorn = pytest.importorskip("uvicorn")
pytest.importorskip("playwright.sync_api")
from playwright.sync_api import Browser, Page, expect, sync_playwright

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

STROKE_ORDER_SVG = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 109 109">
  <path d="M8 8 L32 8" stroke="#1f6b57" stroke-width="6" fill="none" stroke-linecap="round"/>
  <path d="M18 18 L18 38" stroke="#1f6b57" stroke-width="6" fill="none" stroke-linecap="round"/>
</svg>
"""


class BrowserTestContentProvider(FakeContentProvider):
    def __init__(self, stroke_svg_path: Path) -> None:
        self._stroke_svg_path = stroke_svg_path

    def stroke_svg_path(self, kanji: str) -> Path | None:
        return self._stroke_svg_path if kanji else None


@dataclass
class LiveServer:
    base_url: str
    service: KanjiCompanionService
    server: object
    thread: threading.Thread


def build_browser_service(tmp_path: Path, monkeypatch: pytest.MonkeyPatch) -> KanjiCompanionService:
    data_dir = prime_app_home(monkeypatch, tmp_path)
    stroke_svg_path = data_dir / "kanjivg" / "stroke-order.svg"
    stroke_svg_path.parent.mkdir(parents=True, exist_ok=True)
    stroke_svg_path.write_text(STROKE_ORDER_SVG, encoding="utf-8")

    storage = AppStorage(tmp_path / "app.sqlite3")
    storage.save_settings(parse_config({"noteModels": ["Kiku"]}))
    storage.rebuild_analysis(storage.load_settings())
    return KanjiCompanionService(
        storage=storage,
        content_provider=BrowserTestContentProvider(stroke_svg_path),
        ankiconnect_factory=lambda *_args: FakeSnapshotClient(build_collection_snapshot()),
    )


def wait_for_server_start(server: object, thread: threading.Thread, timeout_seconds: float = 10.0) -> None:
    deadline = time.monotonic() + timeout_seconds
    while time.monotonic() < deadline:
        if getattr(server, "started", False):
            return
        if not thread.is_alive():
            raise RuntimeError("Browser test server exited before startup completed.")
        time.sleep(0.05)
    raise TimeoutError("Timed out waiting for the browser test server to start.")


@pytest.fixture(scope="session")
def browser() -> Browser:
    with sync_playwright() as playwright:
        try:
            browser = playwright.chromium.launch(
                executable_path="/usr/bin/chromium",
                args=["--no-sandbox"],
            )
        except Exception as error:  # pragma: no cover - environment dependent
            pytest.skip(f"Chromium launch is unavailable in this environment: {error}")
        yield browser
        browser.close()


@pytest.fixture
def page(browser: Browser) -> Page:
    context = browser.new_context(viewport={"width": 1360, "height": 960})
    page = context.new_page()
    yield page
    context.close()


@pytest.fixture
def live_server(
    tmp_path: Path,
    monkeypatch: pytest.MonkeyPatch,
    free_tcp_port_factory,
) -> LiveServer:
    service = build_browser_service(tmp_path, monkeypatch)
    port = free_tcp_port_factory()
    server = uvicorn.Server(
        uvicorn.Config(
            create_app(service),
            host="127.0.0.1",
            port=port,
            log_level="warning",
            access_log=False,
            lifespan="on",
        )
    )
    thread = threading.Thread(target=server.run, daemon=True)
    thread.start()
    wait_for_server_start(server, thread)
    try:
        yield LiveServer(
            base_url=f"http://127.0.0.1:{port}",
            service=service,
            server=server,
            thread=thread,
        )
    finally:
        server.should_exit = True
        thread.join(timeout=5)
        if thread.is_alive():
            server.force_exit = True
            thread.join(timeout=1)


def draw_guided_strokes(page: Page) -> None:
    strokes = [
        [{"x": 0.14, "y": 0.14}, {"x": 0.26, "y": 0.14}, {"x": 0.38, "y": 0.14}, {"x": 0.56, "y": 0.14}],
        [{"x": 0.34, "y": 0.34}, {"x": 0.34, "y": 0.48}, {"x": 0.34, "y": 0.62}, {"x": 0.34, "y": 0.78}],
    ]
    canvas = page.locator("#handwriting-canvas")
    box = canvas.bounding_box()
    assert box is not None
    for pointer_id, stroke in enumerate(strokes, start=1):
        start = stroke[0]
        canvas.dispatch_event(
            "pointerdown",
            {
                "pointerId": pointer_id,
                "pointerType": "mouse",
                "buttons": 1,
                "clientX": box["x"] + (box["width"] * start["x"]),
                "clientY": box["y"] + (box["height"] * start["y"]),
            },
        )
        for point in stroke[1:]:
            canvas.dispatch_event(
                "pointermove",
                {
                    "pointerId": pointer_id,
                    "pointerType": "mouse",
                    "buttons": 1,
                    "clientX": box["x"] + (box["width"] * point["x"]),
                    "clientY": box["y"] + (box["height"] * point["y"]),
                },
            )
        end = stroke[-1]
        canvas.dispatch_event(
            "pointerup",
            {
                "pointerId": pointer_id,
                "pointerType": "mouse",
                "buttons": 0,
                "clientX": box["x"] + (box["width"] * end["x"]),
                "clientY": box["y"] + (box["height"] * end["y"]),
            },
        )


def set_handwriting_strokes(page: Page, strokes) -> None:
    page.evaluate(
        """(strokes) => {
          if (!window.__kanjiCompanionTestHooks) {
            throw new Error("Missing kanji companion test hooks.");
          }
          const applied = window.__kanjiCompanionTestHooks.setHandwritingStrokes(strokes);
          if (!applied) {
            throw new Error("Handwriting session UI is not active.");
          }
        }""",
        strokes,
    )


def handwriting_snapshot(page: Page) -> dict:
    return page.evaluate(
        """() => {
          if (!window.__kanjiCompanionTestHooks) {
            throw new Error("Missing kanji companion test hooks.");
          }
          return window.__kanjiCompanionTestHooks.getHandwritingSnapshot();
        }"""
    )


def test_same_origin_webapp_flow_drives_dashboard_detail_study_and_handwriting(
    page: Page,
    live_server: LiveServer,
) -> None:
    request_urls: list[str] = []
    page.on("request", lambda request: request_urls.append(request.url) if "/api/" in request.url else None)

    page.goto(f"{live_server.base_url}/?testHooks=1", wait_until="networkidle")

    expect(page.locator("#dashboard-empty")).to_be_visible()
    expect(page.locator("#health-pill")).to_contain_text("Healthy")
    expect(page.locator("#status-sync-chip")).to_contain_text("Manual sync only")

    page.get_by_role("button", name="Manual sync").click()
    expect(page.locator("#metric-total-kanji")).to_have_text("3")
    expect(page.locator("#dashboard-tbody [data-kanji-link='学']")).to_be_visible()

    latest_sync = live_server.service.storage.latest_sync_run()
    assert latest_sync is not None
    assert latest_sync.status == "success"

    page.locator("#dashboard-tbody [data-kanji-link='学']").click()
    expect(page.locator("#detail-headline")).to_have_text("study")
    expect(page.locator("#detail-browser-search")).to_contain_text('"Expression:*学*"')
    expect(page.locator("#detail-note-list")).to_contain_text("学校")

    page.locator("#detail-back").click()
    expect(page.locator("#view-dashboard")).to_be_visible()

    page.locator("#nav-study").click()
    expect(page.locator("#study-empty")).to_be_visible()

    page.locator("#study-refresh-seeds").click()
    expect(page.locator("#study-preview .queue-item")).to_have_count(2)
    expect(page.locator("#study-next")).to_contain_text("2 current problem-child seeds")
    expect(page.locator("#status-seeds-chip")).to_contain_text("2 seeds ready")

    preview_kanji = {text.strip() for text in page.locator("#study-preview .queue-item .link-button").all_text_contents()}
    assert preview_kanji == {"学", "校"}

    page.locator("#start-new").click()
    expect(page.locator("#session-title")).to_contain_text("new introduction")
    first_session_title = page.locator("#session-title").text_content() or ""
    first_kanji = first_session_title.split(" · ", 1)[0]
    assert first_kanji in preview_kanji
    remaining_kanji = (preview_kanji - {first_kanji}).pop()

    page.locator("#session-reveal").click()
    expect(page.locator("#session-answer-panel")).to_be_visible()

    draw_guided_strokes(page)
    expect(page.locator("#handwriting-metrics")).to_contain_text("Captured strokes")
    expect(page.locator("#handwriting-evaluate")).to_be_enabled()
    page.locator("#handwriting-evaluate").click()
    expect(page.locator("#handwriting-status")).to_contain_text("Guide")
    if page.locator(".rating-button[data-rating='good']").is_disabled():
        page.locator("#handwriting-pass").click()
        expect(page.locator(".rating-button[data-rating='good']")).to_be_enabled()

    page.locator(".rating-button[data-rating='good']").click()
    expect(page.locator("#study-new")).to_have_text("1")
    expect(page.locator("#session-title")).to_contain_text(remaining_kanji)
    expect(page.locator("#session-label")).to_contain_text("Confusable recognition")
    recognition_font = page.locator("#session-prompt-kanji").evaluate(
        "(node) => node.style.fontFamily"
    )
    assert recognition_font

    api_paths = {urlparse(url).path for url in request_urls if "/api/" in url}
    assert api_paths >= {
        "/api/health",
        "/api/settings",
        "/api/dashboard",
        "/api/sync/ankiconnect",
        "/api/kanji/%E5%AD%A6",
        "/api/study/overview",
        "/api/study/seeds/refresh",
        "/api/study/sessions",
        "/api/study/reviews",
    }
    assert any(path.startswith("/api/assets/stroke-order/") for path in api_paths)
    assert {urlparse(url).netloc for url in request_urls if "/api/" in url} == {
        urlparse(live_server.base_url).netloc
    }


def test_failed_handwriting_caps_high_ratings_until_override(
    page: Page,
    live_server: LiveServer,
) -> None:
    page.goto(f"{live_server.base_url}/?testHooks=1", wait_until="networkidle")
    page.get_by_role("button", name="Manual sync").click()
    page.locator("#nav-study").click()
    page.locator("#study-refresh-seeds").click()
    page.locator("#start-new").click()
    page.locator("#session-reveal").click()

    set_handwriting_strokes(
        page,
        [[{"x": 0.15, "y": 0.15}, {"x": 0.85, "y": 0.85}]],
    )
    page.locator("#handwriting-evaluate").click()
    expect(page.locator("#handwriting-status")).to_contain_text("Guide retry")
    expect(page.locator(".rating-button[data-rating='again']")).to_be_enabled()
    expect(page.locator(".rating-button[data-rating='hard']")).to_be_hidden()
    expect(page.locator(".rating-button[data-rating='good']")).to_be_disabled()
    expect(page.locator(".rating-button[data-rating='easy']")).to_be_hidden()

    snapshot = handwriting_snapshot(page)
    assert snapshot["allowedRatings"] == ["again"]

    page.locator("#handwriting-pass").click()
    expect(page.locator(".rating-button[data-rating='good']")).to_be_enabled()
    expect(page.locator(".rating-button[data-rating='easy']")).to_be_hidden()


def test_handwriting_undo_clears_live_state(
    page: Page,
    live_server: LiveServer,
) -> None:
    page.goto(f"{live_server.base_url}/?testHooks=1", wait_until="networkidle")
    page.get_by_role("button", name="Manual sync").click()
    page.locator("#nav-study").click()
    page.locator("#study-refresh-seeds").click()
    page.locator("#start-new").click()
    page.locator("#session-reveal").click()

    set_handwriting_strokes(
        page,
        [[{"x": 0.14, "y": 0.14}, {"x": 0.56, "y": 0.14}]],
    )
    expect(page.locator("#handwriting-evaluate")).to_be_enabled()
    assert handwriting_snapshot(page)["strokeCount"] == 1

    page.locator("#canvas-undo").click()
    snapshot = handwriting_snapshot(page)
    assert snapshot["strokeCount"] == 0
    assert snapshot["phase"] == "prewrite"
    expect(page.locator("#handwriting-evaluate")).to_be_disabled()


def test_mobile_layout_stacks_nav_and_dashboard_cards_without_overflow(
    browser: Browser,
    live_server: LiveServer,
) -> None:
    context = browser.new_context(viewport={"width": 412, "height": 915}, is_mobile=True)
    page = context.new_page()
    try:
        page.goto(f"{live_server.base_url}/", wait_until="networkidle")

        dashboard_box = page.locator("#nav-dashboard").bounding_box()
        study_box = page.locator("#nav-study").bounding_box()
        assert dashboard_box is not None
        assert study_box is not None
        assert study_box["y"] > dashboard_box["y"] + 1

        page.get_by_role("button", name="Manual sync").click()
        expect(page.locator("#dashboard-tbody [data-kanji-link='学']")).to_be_visible()

        layout = page.evaluate(
            """() => {
              const firstRow = document.querySelector("#dashboard-tbody tr");
              const firstCell = document.querySelector("#dashboard-tbody td");
              return {
                rowDisplay: firstRow ? getComputedStyle(firstRow).display : "",
                cellDisplay: firstCell ? getComputedStyle(firstCell).display : "",
                firstLabel: firstCell ? firstCell.getAttribute("data-label") : "",
                innerWidth: window.innerWidth,
                scrollWidth: document.documentElement.scrollWidth
              };
            }"""
        )
        assert layout["rowDisplay"] == "block"
        assert layout["cellDisplay"] == "grid"
        assert layout["firstLabel"] == "Kanji"
        assert layout["scrollWidth"] <= layout["innerWidth"] + 1
    finally:
        context.close()
