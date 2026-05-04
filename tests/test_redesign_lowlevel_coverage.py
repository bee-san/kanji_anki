from __future__ import annotations

import argparse
import builtins
import json
import os
import runpy
import sys
import time
import types
from pathlib import Path

import pytest

import kanji_leech_dashboard.cli as cli
import kanji_leech_dashboard.config as config
import kanji_leech_dashboard.jiten as jiten
import kanji_leech_dashboard.jiten_lists as jiten_lists
import kanji_leech_dashboard.normalization as normalization
import kanji_leech_dashboard.state as state


def _write_jiten_cache(
    tmp_path: Path,
    csv_text: str,
    *,
    meta_text: str | None = None,
) -> tuple[Path, Path]:
    cache_path = tmp_path / "jiten_frequency_kanji.csv"
    meta_path = tmp_path / "jiten_frequency_kanji_meta.json"
    cache_path.write_text(csv_text, encoding="utf-8")
    if meta_text is not None:
        meta_path.write_text(meta_text, encoding="utf-8")
    return cache_path, meta_path


def test_parse_config_uses_defaults_for_empty_payload() -> None:
    settings = config.parse_config(None)

    assert settings == config.AppSettings()
    assert settings.to_dict()["noteModels"] == list(config.DEFAULT_MODEL_NAMES)
    assert (
        settings.effective_mature_query
        == '(note:"Kiku") prop:ivl>=21 -is:suspended'
    )


def test_parse_config_coerces_aliases_and_builds_queries() -> None:
    settings = config.parse_config(
        {
            "modelNames": [" Core ", "", "Extra"],
            "readingField": " Reading ",
            "meaningField": 42,
            "ankiconnectUrl": " http://anki.example ",
            "matureDays": "30",
            "kanjiDashboardMatureSupportThreshold": "5",
            "jitenCacheTtlHours": "12",
            "jitenRequestTimeoutSeconds": "7",
            "pollingEnabled": 1,
            "pollingIntervalSeconds": "600",
        }
    )

    assert settings.model_names == ("Core", "Extra")
    assert settings.expression_field == config.DEFAULT_EXPRESSION_FIELD
    assert settings.reading_field == "Reading"
    assert settings.meaning_field == "42"
    assert settings.ankiconnect_url == "http://anki.example"
    assert settings.mature_days == 30
    assert settings.kanji_dashboard_mature_support_threshold == 5
    assert settings.jiten_cache_ttl_hours == 12
    assert settings.jiten_request_timeout_seconds == 7
    assert settings.polling_enabled is True
    assert settings.polling_interval_seconds == 600
    assert (
        settings.effective_mature_query
        == '(note:"Core" or note:"Extra") prop:ivl>=30 -is:suspended'
    )
    assert config.build_default_mature_query((), 7) == "prop:ivl>=7 -is:suspended"


def test_parse_config_reports_invalid_models_and_fields() -> None:
    with pytest.raises(config.ConfigValidationError) as error:
        config.parse_config(
            {
                "noteModels": [],
                "ankiConnectUrl": "   ",
                "expressionField": " ",
                "readingField": "",
                "meaningField": " ",
                "matureDays": "0",
                "kanjiSupportThreshold": "x",
                "jitenCacheTtlHours": -1,
                "jitenRequestTimeoutSeconds": [],
                "pollingIntervalSeconds": "0",
            }
        )

    assert "noteModels must include at least one note type name." in error.value.messages
    assert "ankiConnectUrl must be a non-empty URL." in error.value.messages
    assert "expressionField must be a non-empty string." in error.value.messages
    assert "readingField must be a non-empty string." in error.value.messages
    assert "meaningField must be a non-empty string." in error.value.messages
    assert "matureDays must be a positive integer." in error.value.messages
    assert "kanjiSupportThreshold must be a positive integer." in error.value.messages
    assert "jitenCacheTtlHours must be a positive integer." in error.value.messages
    assert (
        "jitenRequestTimeoutSeconds must be a positive integer."
        in error.value.messages
    )
    assert "pollingIntervalSeconds must be a positive integer." in error.value.messages

    with pytest.raises(config.ConfigValidationError) as type_error:
        config.parse_config({"noteModels": "Kiku"})

    assert type_error.value.messages == [
        "noteModels must be a list of note type names."
    ]


def test_jiten_list_helpers_cover_dropdowns_and_default_lookup() -> None:
    anime = jiten_lists.get_frequency_list_definition("anime")

    assert anime.csv_url.endswith("mediaType=1")
    assert anime.dropdown_label == "Anime (media)"
    assert (
        jiten_lists.get_frequency_list_definition("missing").id
        == jiten_lists.DEFAULT_JITEN_FREQUENCY_LIST_ID
    )
    assert "kanji" in jiten_lists.frequency_list_ids()
    assert ("global", "Global") in jiten_lists.dropdown_options()


def test_normalization_helpers_cover_fallback_imports_and_deduping(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    assert normalization.normalize_lookup_text(" Ａ　B\nC ") == "A B C"

    with monkeypatch.context() as patch:
        real_import = builtins.__import__
        patch.delitem(sys.modules, "anki", raising=False)
        patch.delitem(sys.modules, "anki.utils", raising=False)

        def missing_import(name: str, *args: object, **kwargs: object) -> object:
            if name == "anki.utils":
                raise ModuleNotFoundError("anki is unavailable")
            return real_import(name, *args, **kwargs)

        patch.setattr(builtins, "__import__", missing_import)
        assert normalization.strip_html_text("<b>学</b>") == "学"

    with monkeypatch.context() as patch:
        anki_module = types.ModuleType("anki")
        utils_module = types.ModuleType("anki.utils")

        def broken_strip_html(_text: str) -> str:
            raise AttributeError("strip_html is unavailable")

        utils_module.strip_html = broken_strip_html
        anki_module.utils = utils_module
        patch.setitem(sys.modules, "anki", anki_module)
        patch.setitem(sys.modules, "anki.utils", utils_module)

        assert normalization.strip_html_text("<i>火</i>") == "火"

    assert normalization.extract_kanji_chars("<b>森</b> 森 林 A") == ["森", "林"]


def test_state_app_home_defaults_to_user_home(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv(state.APP_HOME_ENV, raising=False)

    assert state.app_home_dir() == Path.home() / ".local" / "share" / state.APP_DIR_NAME


def test_state_creates_configured_directories(
    monkeypatch: pytest.MonkeyPatch,
    tmp_path: Path,
) -> None:
    app_home = tmp_path / "app-home"
    package_root = tmp_path / "package-root"
    monkeypatch.setenv(state.APP_HOME_ENV, str(app_home))
    monkeypatch.setattr(state, "package_dir", lambda: package_root)

    assert state.packaged_data_dir() == package_root / "data"
    assert state.webapp_dir() == package_root / "webapp"
    assert state.ensure_app_home_dir() == app_home
    assert state.ensure_data_dir() == app_home / "data"
    assert state.ensure_user_files_dir() == app_home / "data"
    assert state.ensure_cache_dir() == app_home / "cache"
    assert state.stroke_order_cache_dir() == app_home / "data" / state.KANJIVG_DIR_NAME
    assert state.database_path() == app_home / state.DATABASE_FILE_NAME
    assert (
        state.kanjidic_cache_path()
        == app_home / "data" / state.KANJIDIC2_FILE_NAME
    )
    assert (package_root / "data").is_dir()
    assert (app_home / "data").is_dir()
    assert (app_home / "cache").is_dir()


def test_parse_frequency_csv_handles_headers_duplicates_and_errors() -> None:
    ranks = jiten.parse_frequency_csv(
        "\ufeffsurface,count,notes\n"
        "森,10,first\n"
        "林,3,second\n"
        "森,2,better\n"
    )

    assert ranks == {"森": 2.0, "林": 3.0}

    with pytest.raises(jiten.FrequencyParseError, match="empty"):
        jiten.parse_frequency_csv(" \n ")

    with pytest.raises(
        jiten.FrequencyParseError,
        match="No usable frequency rows",
    ):
        jiten.parse_frequency_csv("expression,rank\n森,n/a\n")


def test_parse_frequency_csv_handles_sniffer_fallback_without_header(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class FakeSniffer:
        def sniff(self, _sample: str) -> object:
            raise jiten.csv.Error("bad dialect")

        def has_header(self, _sample: str) -> bool:
            return False

    monkeypatch.setattr(jiten.csv, "Sniffer", FakeSniffer)

    assert jiten.parse_frequency_csv("森,9\n林,7\n") == {"森": 9.0, "林": 7.0}


def test_load_kanji_frequency_lookup_prefers_fresh_cache(
    monkeypatch: pytest.MonkeyPatch,
    tmp_path: Path,
) -> None:
    _write_jiten_cache(
        tmp_path,
        "expression,rank\n森,4\n",
        meta_text='{"sourceUrl":" https://cache.example/list.csv "}',
    )
    monkeypatch.setattr(jiten, "ensure_user_files_dir", lambda: tmp_path)

    def opener(_url: str, _timeout: int) -> str:
        raise AssertionError("fresh cache should avoid remote refresh")

    lookup = jiten.load_kanji_frequency_lookup(
        config.AppSettings(jiten_cache_ttl_hours=24),
        opener=opener,
    )

    assert lookup.source_kind == "cache"
    assert lookup.source_url == "https://cache.example/list.csv"
    assert lookup.warnings == ()
    assert lookup.rank_for(" 森 ") == 4.0
    assert lookup.rank_for("  ") is None


def test_load_kanji_frequency_lookup_uses_stale_cache_when_refresh_fails(
    monkeypatch: pytest.MonkeyPatch,
    tmp_path: Path,
) -> None:
    cache_path, _meta_path = _write_jiten_cache(
        tmp_path,
        "kanji,frequency\n森,8\n",
        meta_text='{"sourceUrl":" https://stale.example/list.csv "}',
    )
    stale_time = time.time() - 7200
    os.utime(cache_path, (stale_time, stale_time))
    monkeypatch.setattr(jiten, "ensure_user_files_dir", lambda: tmp_path)
    seen: dict[str, object] = {}

    def opener(url: str, timeout: int) -> str:
        seen["url"] = url
        seen["timeout"] = timeout
        raise RuntimeError("network down")

    lookup = jiten.load_kanji_frequency_lookup(
        config.AppSettings(
            jiten_cache_ttl_hours=1,
            jiten_request_timeout_seconds=17,
        ),
        opener=opener,
    )

    assert seen["url"] == jiten_lists.get_frequency_list_definition("kanji").csv_url
    assert seen["timeout"] == 17
    assert lookup.source_kind == "cache"
    assert lookup.source_url == "https://stale.example/list.csv"
    assert lookup.rank_for("森") == 8.0
    assert "Using a stale cached Jiten Kanji CSV." in lookup.warnings
    assert any(
        "Could not refresh the Jiten Kanji CSV" in warning
        for warning in lookup.warnings
    )


def test_load_kanji_frequency_lookup_skips_stale_fallback_after_invalid_fresh_cache(
    monkeypatch: pytest.MonkeyPatch,
    tmp_path: Path,
) -> None:
    _write_jiten_cache(
        tmp_path,
        "",
        meta_text="{not valid json",
    )
    monkeypatch.setattr(jiten, "ensure_user_files_dir", lambda: tmp_path)

    def opener(_url: str, _timeout: int) -> str:
        raise RuntimeError("network down")

    lookup = jiten.load_kanji_frequency_lookup(
        config.AppSettings(jiten_cache_ttl_hours=24),
        opener=opener,
    )

    assert lookup.source_kind == "none"
    assert lookup.ranks == {}
    assert lookup.source_url is None
    assert any(
        "Ignoring an invalid cached Jiten kanji list" in warning
        for warning in lookup.warnings
    )
    assert any(
        "Could not refresh the Jiten Kanji CSV" in warning
        for warning in lookup.warnings
    )
    assert lookup.warnings[-1] == "Jiten Kanji CSV is unavailable."


def test_refresh_kanji_frequency_lookup_fetches_and_persists_remote_cache(
    monkeypatch: pytest.MonkeyPatch,
    tmp_path: Path,
) -> None:
    monkeypatch.setattr(jiten, "ensure_user_files_dir", lambda: tmp_path)
    seen: dict[str, object] = {}

    def opener(url: str, timeout: int) -> str:
        seen["url"] = url
        seen["timeout"] = timeout
        return "expression,rank\n森,5\n"

    lookup = jiten.refresh_kanji_frequency_lookup(
        config.AppSettings(jiten_request_timeout_seconds=11),
        opener=opener,
    )

    assert seen["url"] == jiten_lists.get_frequency_list_definition("kanji").csv_url
    assert seen["timeout"] == 11
    assert lookup.source_kind == "remote"
    assert lookup.source_url == seen["url"]
    assert lookup.warnings == ()
    assert lookup.rank_for(" 森 ") == 5.0
    assert (
        (tmp_path / "jiten_frequency_kanji.csv").read_text(encoding="utf-8")
        == "expression,rank\n森,5\n"
    )

    meta = json.loads(
        (tmp_path / "jiten_frequency_kanji_meta.json").read_text(encoding="utf-8")
    )
    assert meta["sourceUrl"] == seen["url"]
    assert isinstance(meta["fetchedAt"], float)


def test_cli_main_requires_a_command(capsys: pytest.CaptureFixture[str]) -> None:
    with pytest.raises(SystemExit) as error:
        cli.main([])

    assert error.value.code == 2
    assert "the following arguments are required: command" in capsys.readouterr().err


def test_cli_run_command_invokes_uvicorn(monkeypatch: pytest.MonkeyPatch) -> None:
    service = object()
    calls: dict[str, object] = {}
    monkeypatch.setattr(cli, "KanjiCompanionService", lambda: service)
    monkeypatch.setattr(cli, "create_app", lambda current_service: {"service": current_service})
    monkeypatch.setitem(
        sys.modules,
        "uvicorn",
        types.SimpleNamespace(
            run=lambda app, host, port: calls.update(
                {"app": app, "host": host, "port": port}
            )
        ),
    )

    assert cli.main(["run", "--host", "0.0.0.0", "--port", "9000"]) == 0
    assert calls == {
        "app": {"service": service},
        "host": "0.0.0.0",
        "port": 9000,
    }


def test_cli_run_command_reports_missing_uvicorn(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    real_import = builtins.__import__
    monkeypatch.delitem(sys.modules, "uvicorn", raising=False)
    monkeypatch.setattr(cli, "KanjiCompanionService", lambda: object())

    def missing_import(name: str, *args: object, **kwargs: object) -> object:
        if name == "uvicorn":
            raise ModuleNotFoundError("No module named 'uvicorn'")
        return real_import(name, *args, **kwargs)

    monkeypatch.setattr(builtins, "__import__", missing_import)

    with pytest.raises(SystemExit) as error:
        cli.main(["run"])

    assert "uvicorn is not installed" in str(error.value)


def test_cli_sync_and_rebuild_commands_print_results(
    monkeypatch: pytest.MonkeyPatch,
    capsys: pytest.CaptureFixture[str],
) -> None:
    class FakeService:
        def sync_ankiconnect(self) -> dict[str, str]:
            return {"syncRun": "synced"}

        def rebuild_analysis(self) -> dict[str, str]:
            return {"analysis": "rebuilt"}

    monkeypatch.setattr(cli, "KanjiCompanionService", FakeService)

    assert cli.main(["sync-now"]) == 0
    assert capsys.readouterr().out == "synced\n"
    assert cli.main(["rebuild-analysis"]) == 0
    assert capsys.readouterr().out == "rebuilt\n"


def test_cli_unknown_command_path_uses_parser_error(
    monkeypatch: pytest.MonkeyPatch,
) -> None:
    class FakeParser:
        def parse_args(self, _argv: object) -> argparse.Namespace:
            return argparse.Namespace(command="mystery")

        def error(self, message: str) -> None:
            raise RuntimeError(message)

    monkeypatch.setattr(cli, "build_parser", lambda: FakeParser())
    monkeypatch.setattr(cli, "KanjiCompanionService", lambda: object())

    with pytest.raises(RuntimeError, match="Unknown command: mystery"):
        cli.main(["mystery"])


def test_main_module_exits_with_cli_status(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setattr(cli, "main", lambda: 7)

    with pytest.raises(SystemExit) as error:
        runpy.run_module("kanji_leech_dashboard.__main__", run_name="__main__")

    assert error.value.code == 7
