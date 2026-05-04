from __future__ import annotations

import asyncio
import csv
import io
import json
from pathlib import Path
import runpy
import sys
import types
from urllib.error import URLError

import httpx
import pytest
from fastapi import HTTPException

from kanji_leech_dashboard import __version__
from kanji_leech_dashboard import ankiconnect as ankiconnect_module
from kanji_leech_dashboard import api as api_module
from kanji_leech_dashboard import cli as cli_module
from kanji_leech_dashboard import dashboard as dashboard_module
from kanji_leech_dashboard import jiten as jiten_module
from kanji_leech_dashboard import normalization as normalization_module
from kanji_leech_dashboard import state as state_module
from kanji_leech_dashboard import storage as storage_module
from kanji_leech_dashboard.ankiconnect import AnkiConnectClient, AnkiConnectError
from kanji_leech_dashboard.api import create_app
from kanji_leech_dashboard.config import AppSettings, ConfigValidationError, build_default_mature_query, parse_config
from kanji_leech_dashboard.dashboard import FrequencyLookup, KanjiNotFoundError, build_problem_kanji_seeds
from kanji_leech_dashboard.jiten import FrequencyLookup as JitenLookup
from kanji_leech_dashboard.jiten import FrequencyParseError, load_kanji_frequency_lookup, parse_frequency_csv, refresh_kanji_frequency_lookup
from kanji_leech_dashboard.jiten_lists import dropdown_options, frequency_list_ids, get_frequency_list_definition
from kanji_leech_dashboard.service import KanjiCompanionService
from kanji_leech_dashboard.state import APP_DIR_NAME, APP_HOME_ENV
from kanji_leech_dashboard.storage import AppStorage, SyncRunRecord
from kanji_leech_dashboard.study import StudyItemNotFoundError
from kanji_leech_dashboard.study_content import KanjiStudyContent

from .redesign_helpers import FakeContentProvider, FakeSnapshotClient, build_collection_snapshot, prime_app_home


def run(coro):
    return asyncio.run(coro)


class Response:
    def __init__(self, payload: bytes) -> None:
        self._payload = payload

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        return False

    def read(self) -> bytes:
        return self._payload


class RaisingService:
    def __init__(self, *, svg_path: Path | None = None) -> None:
        self._svg_path = svg_path

    def get_health(self):
        return {"ok": True}

    def get_settings(self):
        return {"noteModels": ["Kiku"]}

    def update_settings(self, _payload):
        raise ValueError("bad settings")

    def sync_ankiconnect(self):
        raise AnkiConnectError("offline")

    def get_dashboard(self):
        return {"summary": {}, "rows": [], "warnings": []}

    def get_kanji_detail(self, _kanji):
        raise KanjiNotFoundError("missing")

    def get_study_overview(self):
        return {"dueCount": 0, "newCount": 0, "activeQueueCount": 0, "inactiveCount": 0, "currentProblemSeedCount": 0, "retentionTarget": 0.9, "nextDueAt": None, "queuePreview": [], "attribution": {}}

    def refresh_study_seeds(self):
        return {}

    def create_study_session(self, _payload):
        raise StudyItemNotFoundError("missing item")

    def submit_study_review(self, _payload):
        return {"duplicate": False}

    def get_stroke_order_svg_path(self, _kanji):
        return self._svg_path


def test_config_parsing_and_errors() -> None:
    settings = parse_config(
        {
            "ankiConnectUrl": " http://example.invalid ",
            "noteModels": [" Kiku ", "Core"],
            "expressionField": "Expr",
            "readingField": "Read",
            "meaningField": "Mean",
            "matureDays": "30",
            "kanjiSupportThreshold": "4",
            "jitenCacheTtlHours": "12",
            "jitenRequestTimeoutSeconds": "9",
            "pollingEnabled": 1,
            "pollingIntervalSeconds": "600",
        }
    )
    assert settings.to_dict()["ankiConnectUrl"] == "http://example.invalid"
    assert settings.effective_mature_query == '(note:"Kiku" or note:"Core") prop:ivl>=30 -is:suspended'
    assert build_default_mature_query(tuple(), 10) == "prop:ivl>=10 -is:suspended"
    assert parse_config(None).model_names == ("Kiku",)
    assert parse_config({"modelNames": ["Legacy"]}).model_names == ("Legacy",)
    with pytest.raises(ConfigValidationError):
        parse_config({"noteModels": []})

    with pytest.raises(ConfigValidationError) as error:
        parse_config(
            {
                "ankiConnectUrl": " ",
                "noteModels": "bad",
                "expressionField": " ",
                "readingField": " ",
                "meaningField": " ",
                "matureDays": 0,
                "kanjiSupportThreshold": "bad",
                "jitenCacheTtlHours": 0,
                "jitenRequestTimeoutSeconds": "bad",
                "pollingIntervalSeconds": 0,
            }
        )
    assert "ankiConnectUrl must be a non-empty URL." in error.value.messages
    assert "noteModels must be a list of note type names." in error.value.messages


def test_jiten_lists_and_normalization_helpers(monkeypatch) -> None:
    assert "global" in frequency_list_ids()
    assert get_frequency_list_definition("missing").id == "global"
    assert get_frequency_list_definition("anime").csv_url.startswith("https://api.jiten.moe/api/")
    assert get_frequency_list_definition("anime").dropdown_label.endswith("(media)")
    assert dropdown_options()[0][0] == "global"
    assert normalization_module.normalize_lookup_text(" Ａ  学 ") == "A 学"
    assert normalization_module.extract_kanji_chars("<b>学校学</b>") == ["学", "校"]

    anki_utils = types.ModuleType("anki.utils")
    anki_utils.strip_html = lambda _text: (_ for _ in ()).throw(AttributeError())
    monkeypatch.setitem(sys.modules, "anki.utils", anki_utils)
    assert normalization_module.strip_html_text("<b>x</b>") == "x"
    monkeypatch.delitem(sys.modules, "anki.utils", raising=False)
    assert normalization_module.strip_html_text("<i>y</i>") == "y"


def test_state_paths(monkeypatch, tmp_path: Path) -> None:
    monkeypatch.setenv(APP_HOME_ENV, str(tmp_path))
    assert state_module.app_home_dir() == tmp_path
    assert state_module.ensure_app_home_dir() == tmp_path
    assert state_module.ensure_data_dir() == tmp_path / "data"
    assert state_module.ensure_cache_dir() == tmp_path / "cache"
    assert state_module.database_path() == tmp_path / "app.sqlite3"
    assert state_module.stroke_order_cache_dir() == tmp_path / "data" / "kanjivg"
    assert state_module.kanjidic_cache_path() == tmp_path / "data" / "kanjidic2.xml"
    monkeypatch.delenv(APP_HOME_ENV)
    monkeypatch.setattr(state_module.Path, "home", lambda: tmp_path)
    assert state_module.app_home_dir() == tmp_path / ".local" / "share" / APP_DIR_NAME
    assert state_module.package_dir().name == "kanji_leech_dashboard"
    assert state_module.webapp_dir().name == "webapp"


def test_ankiconnect_low_level_paths(monkeypatch) -> None:
    class StubClient(AnkiConnectClient):
        def __init__(self, responses):
            super().__init__("http://example.invalid")
            self._responses = responses

        def _invoke(self, action: str, **params):
            response = self._responses[action]
            return response(params) if callable(response) else response

    client = StubClient(
        {
            "findNotes": lambda params: ["1", "bad", 2] if params["query"] == 'note:"Kiku"' else [3],
            "notesInfo": [None, {"noteId": 1, "modelName": "Kiku", "fields": {"Expression": {"value": "学校"}}, "tags": ["x"], "cards": ["11", "bad"]}],
            "cardsInfo": [None, {"cardId": 11, "note": 1, "deckName": "Kiku", "queue": -1, "type": 0}],
        }
    )
    snapshot = client.sync_snapshot(AppSettings(model_names=("Kiku", "Core")))
    assert [note.note_id for note in snapshot.notes] == [1]
    assert snapshot.cards[0].is_active is False

    monkeypatch.setattr(ankiconnect_module, "urlopen", lambda *_args, **_kwargs: (_ for _ in ()).throw(URLError("offline")))
    with pytest.raises(AnkiConnectError, match="Could not reach AnkiConnect"):
        AnkiConnectClient("http://offline")._invoke("ping")

    monkeypatch.setattr(ankiconnect_module, "urlopen", lambda *_args, **_kwargs: Response(b"{bad"))
    with pytest.raises(AnkiConnectError, match="invalid JSON"):
        AnkiConnectClient("http://bad")._invoke("ping")

    monkeypatch.setattr(ankiconnect_module, "urlopen", lambda *_args, **_kwargs: Response(b"[]"))
    with pytest.raises(AnkiConnectError, match="invalid payload"):
        AnkiConnectClient("http://bad")._invoke("ping")

    monkeypatch.setattr(ankiconnect_module, "urlopen", lambda *_args, **_kwargs: Response(b'{"error":"boom","result":null}'))
    with pytest.raises(AnkiConnectError, match="boom"):
        AnkiConnectClient("http://bad")._invoke("ping")

    monkeypatch.setattr(ankiconnect_module, "urlopen", lambda *_args, **_kwargs: Response(b'{"error":null,"result":[1]}'))
    assert AnkiConnectClient("http://ok")._invoke("ping") == [1]
    assert list(ankiconnect_module._chunked([1, 2, 3], 2)) == [[1, 2], [3]]
    assert ankiconnect_module._is_int_like("x") is False


def test_dashboard_wrapper_and_helper_paths(monkeypatch) -> None:
    class Note(dict):
        pass

    class Card:
        def __init__(self, note):
            self._note = note

        def note(self):
            return self._note

    class Col:
        def __init__(self):
            self.cards = {
                1: Card(Note(Expression="学校")),
                2: Card(Note(Expression="")),
                3: Card(Note(Expression="学ぶ")),
            }

        def find_cards(self, query):
            mapping = {
                '(note:"Kiku") is:suspended': [1, 2],
                '(note:"Kiku") -is:suspended': [3],
                '(note:"Kiku") prop:ivl>=21 -is:suspended': [3],
                "is:suspended": [1],
                "-is:suspended": [3],
                "prop:ivl>=21 -is:suspended": [3],
            }
            return mapping[query]

        def get_card(self, card_id):
            return self.cards[card_id]

    monkeypatch.setattr(
        dashboard_module,
        "_load_kanji_frequency_lookup",
        lambda _config: FrequencyLookup({"学": 3}, None, ("warn",), "cache"),
    )
    config = AppSettings(model_names=("Kiku",))
    payload = dashboard_module.build_kanji_dashboard(Col(), config)
    detail = dashboard_module.build_kanji_detail(Col(), config, "学")
    assert payload["warnings"][-1].startswith("Skipped 1 suspended cards")
    assert detail["kanji"] == "学"
    assert dashboard_module.build_suspended_query(tuple()) == "is:suspended"
    assert dashboard_module.build_active_query(tuple()) == "-is:suspended"
    assert dashboard_module.build_supported_query(tuple()) == ""
    assert dashboard_module.build_browser_search("学", model_names=tuple(), search_field_name="Expression") == '"Expression:*学*"'
    assert dashboard_module.load_kanji_frequency_lookup(config).source_kind == "cache"
    assert dashboard_module._normalize_kanji_key("学生") == "学生"
    assert dashboard_module._get_note_field({}, "Expression") == ""
    assert dashboard_module._problem_seed_sort_key(build_problem_kanji_seeds(suspended_expressions=["学"], active_expressions=[], mature_expressions=[], threshold=1, kanji_ranks={}, model_names=("Kiku",), search_field_name="Expression")[0])[3][0] == 1


def test_jiten_paths(monkeypatch, tmp_path: Path) -> None:
    monkeypatch.setattr(jiten_module, "ensure_user_files_dir", lambda: tmp_path)
    config = AppSettings()
    assert JitenLookup({"学": 1}, None, tuple()).rank_for("") is None
    assert JitenLookup({"学": 1}, None, tuple()).rank_for("学") == 1
    with pytest.raises(FrequencyParseError):
        parse_frequency_csv("")

    original_sniffer = csv.Sniffer

    class BadSniffer(csv.Sniffer):
        def sniff(self, sample, delimiters=None):
            raise csv.Error("bad")

        def has_header(self, sample):
            return False

    monkeypatch.setattr(jiten_module.csv, "Sniffer", BadSniffer)
    assert parse_frequency_csv("学,1\n校,2\n") == {"学": 1.0, "校": 2.0}
    monkeypatch.setattr(jiten_module.csv, "Sniffer", original_sniffer)
    with pytest.raises(FrequencyParseError):
        parse_frequency_csv("term,rank\n学,\n")

    cache = tmp_path / "jiten_frequency_kanji.csv"
    meta = tmp_path / "jiten_frequency_kanji_meta.json"
    cache.write_text("kanji,rank\n学,3\n", encoding="utf-8")
    meta.write_text(json.dumps({"sourceUrl": "https://source"}), encoding="utf-8")
    fresh = load_kanji_frequency_lookup(config)
    assert fresh.source_kind == "cache"
    cache.write_text("bad", encoding="utf-8")
    invalid = load_kanji_frequency_lookup(config, opener=lambda *_args: (_ for _ in ()).throw(OSError("offline")))
    assert invalid.source_kind == "none"
    cache.write_text("kanji,rank\n学,3\n", encoding="utf-8")
    monkeypatch.setattr(jiten_module, "_is_fresh", lambda *_args: False)
    stale = load_kanji_frequency_lookup(config, opener=lambda *_args: (_ for _ in ()).throw(OSError("offline")))
    assert stale.source_kind == "cache"
    refreshed = refresh_kanji_frequency_lookup(config, opener=lambda *_args: "kanji,rank\n学,2\n")
    assert refreshed.source_kind == "remote"

    monkeypatch.setattr(jiten_module, "urlopen", lambda *_args, **_kwargs: Response("kanji,rank\n学,1\n".encode("utf-8")))
    assert jiten_module._default_fetch_text("https://example.invalid", 5).startswith("kanji")
    warnings = []
    assert jiten_module._fetch_remote_lookup("https://example.invalid", config, warnings, meta, cache, lambda *_args: "kanji,rank\n学,4\n").source_kind == "remote"
    assert jiten_module._fetch_remote_lookup("https://example.invalid", config, warnings, meta, cache, lambda *_args: (_ for _ in ()).throw(RuntimeError("boom"))) is None
    assert warnings
    assert jiten_module._find_header_index(["x"], ("y",), default=7) == 7
    assert jiten_module._find_numeric_candidate_index([], 0) == 1
    assert jiten_module._parse_positive_number("x") is None
    assert jiten_module._parse_positive_number("0") is None
    assert jiten_module._is_fresh(tmp_path / "missing.csv", 1) is False
    assert jiten_module._meta_source_url(None) is None
    assert jiten_module._meta_source_url({"sourceUrl": " "}) is None
    (tmp_path / "bad.json").write_text("[1]", encoding="utf-8")
    assert jiten_module._read_json(tmp_path / "bad.json") is None
    jiten_module._write_json(tmp_path / "out.json", {"a": 1})
    assert json.loads((tmp_path / "out.json").read_text(encoding="utf-8")) == {"a": 1}


def test_storage_service_and_api_paths(monkeypatch, tmp_path: Path) -> None:
    prime_app_home(monkeypatch, tmp_path)
    storage = AppStorage(tmp_path / "app.sqlite3")
    assert storage.load_settings().to_dict()["noteModels"] == ["Kiku"]
    storage.save_settings(parse_config({"noteModels": ["Kiku"]}))
    conn = storage.connect()
    conn.execute("INSERT OR REPLACE INTO app_settings (key, value_json, updated_ts) VALUES (?, ?, ?)", ("bad_json", "{bad", 1))
    conn.execute("INSERT OR REPLACE INTO app_settings (key, value_json, updated_ts) VALUES (?, ?, ?)", ("not_list", json.dumps({"x": 1}), 1))
    conn.commit()
    assert storage._load_json_setting(conn, "missing") is None
    assert storage._load_json_setting(conn, "bad_json") is None
    assert storage._load_json_setting(conn, "not_list") == {"x": 1}
    conn.close()
    assert storage.load_kanji_detail("missing") is None
    assert storage.problem_seed_count() == 0
    assert storage.source_counts() == {"noteCount": 0, "cardCount": 0}
    assert storage.load_problem_seeds() == ()
    assert storage.load_dashboard()["summary"]["totalKanjiCount"] == 0
    assert storage_module._load_json_list("{bad") == []
    assert storage_module._load_json_list(json.dumps({"x": 1})) == []
    assert storage_module._load_json_list(json.dumps(["a", ""])) == ["a"]
    assert SyncRunRecord(1, "ok", "a", None, 2, 3, None).to_dict()["id"] == 1

    storage.save_settings(parse_config({"noteModels": ["Kiku"], "kanjiSupportThreshold": 3}))
    monkeypatch.setattr(
        storage_module,
        "load_kanji_frequency_lookup",
        lambda _settings: types.SimpleNamespace(
            ranks={},
            warnings=tuple(),
            source_kind="none",
        ),
    )
    service = KanjiCompanionService(
        storage=storage,
        content_provider=FakeContentProvider(),
        ankiconnect_factory=lambda *_args: FakeSnapshotClient(build_collection_snapshot()),
    )
    assert service.update_settings({"noteModels": ["Kiku"], "kanjiSupportThreshold": 5})["kanjiSupportThreshold"] == 5
    assert "analysis" in service.rebuild_analysis()
    with pytest.raises(KanjiNotFoundError):
        service.get_kanji_detail("missing")
    with pytest.raises(AnkiConnectError):
        KanjiCompanionService(
            storage=storage,
            content_provider=FakeContentProvider(),
            ankiconnect_factory=lambda *_args: FakeSnapshotClient(AnkiConnectError("offline")),
        ).sync_ankiconnect()

    svg_path = tmp_path / "x.svg"
    svg_path.write_text("<svg></svg>", encoding="utf-8")
    web_dir = tmp_path / "webapp"
    web_dir.mkdir()
    (web_dir / "index.html").write_text("<!doctype html>", encoding="utf-8")
    monkeypatch.setattr(api_module, "webapp_dir", lambda: web_dir)

    async def api_scenario():
        app = create_app(RaisingService(svg_path=svg_path))
        transport = httpx.ASGITransport(app=app)
        async with httpx.AsyncClient(transport=transport, base_url="http://testserver") as client:
            responses = [
                await client.put("/api/settings", json={}),
                await client.post("/api/sync/ankiconnect"),
                await client.get("/api/kanji/%E5%AD%A6"),
                await client.post("/api/study/sessions", json={}),
            ]
        return app, responses

    app, responses = run(api_scenario())
    settings_bad, sync_bad, kanji_missing, session_missing = responses
    stroke_route = next(
        route for route in app.routes if getattr(route, "path", None) == "/api/assets/stroke-order/{kanji}.svg"
    )
    root_route = next(route for route in app.routes if getattr(route, "path", None) == "/")
    svg_ok = run(stroke_route.endpoint("学"))
    root_ok = run(root_route.endpoint())

    assert settings_bad.status_code == 400
    assert sync_bad.status_code == 502
    assert kanji_missing.status_code == 404
    assert session_missing.status_code == 404
    assert svg_ok.status_code == 200
    assert root_ok.status_code == 200

    empty_web = tmp_path / "empty_web"
    empty_web.mkdir()
    monkeypatch.setattr(api_module, "webapp_dir", lambda: empty_web)
    missing_root_app = create_app(RaisingService(svg_path=svg_path))
    missing_root_route = next(
        route for route in missing_root_app.routes if getattr(route, "path", None) == "/"
    )
    with pytest.raises(HTTPException) as error:
        run(missing_root_route.endpoint())
    assert error.value.status_code == 404


def test_cli_and_main_paths(monkeypatch, capsys) -> None:
    class StubService:
        def sync_ankiconnect(self):
            return {"syncRun": {"status": "ok"}}

        def rebuild_analysis(self):
            return {"analysis": {"done": True}}

    monkeypatch.setattr(cli_module, "KanjiCompanionService", lambda: StubService())
    monkeypatch.setattr(cli_module, "create_app", lambda service: {"service": service})
    uvicorn_calls = []
    monkeypatch.setitem(sys.modules, "uvicorn", types.SimpleNamespace(run=lambda app, host, port: uvicorn_calls.append((app, host, port))))
    assert cli_module.main(["run", "--host", "0.0.0.0", "--port", "9999"]) == 0
    assert uvicorn_calls[0][1:] == ("0.0.0.0", 9999)
    assert cli_module.main(["sync-now"]) == 0
    assert cli_module.main(["rebuild-analysis"]) == 0
    out = capsys.readouterr().out
    assert "status" in out and "done" in out

    class FakeParser:
        def parse_args(self, argv):
            return types.SimpleNamespace(command="weird")

        def error(self, message):
            self.message = message

    monkeypatch.setattr(cli_module, "build_parser", lambda: FakeParser())
    monkeypatch.setattr(cli_module, "KanjiCompanionService", lambda: StubService())
    assert cli_module.main([]) == 2

    monkeypatch.setattr("kanji_leech_dashboard.cli.main", lambda: 7)
    with pytest.raises(SystemExit) as error:
        runpy.run_module("kanji_leech_dashboard", run_name="__main__")
    assert error.value.code == 7
