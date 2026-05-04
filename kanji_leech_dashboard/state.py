from __future__ import annotations

import os
from pathlib import Path

APP_HOME_ENV = "KANJI_LEECH_DASHBOARD_HOME"
APP_DIR_NAME = "kanji_leech_dashboard"
DATABASE_FILE_NAME = "app.sqlite3"
KANJIDIC2_FILE_NAME = "kanjidic2.xml"
KANJIVG_DIR_NAME = "kanjivg"


def package_dir() -> Path:
    return Path(__file__).resolve().parent


def packaged_data_dir() -> Path:
    path = package_dir() / "data"
    path.mkdir(parents=True, exist_ok=True)
    return path


def webapp_dir() -> Path:
    return package_dir() / "webapp"


def app_home_dir() -> Path:
    raw = os.environ.get(APP_HOME_ENV)
    if raw:
        return Path(raw).expanduser()
    return Path.home() / ".local" / "share" / APP_DIR_NAME


def ensure_app_home_dir() -> Path:
    path = app_home_dir()
    path.mkdir(parents=True, exist_ok=True)
    return path


def ensure_data_dir() -> Path:
    path = ensure_app_home_dir() / "data"
    path.mkdir(parents=True, exist_ok=True)
    return path


def ensure_user_files_dir() -> Path:
    return ensure_data_dir()


def ensure_cache_dir() -> Path:
    path = ensure_app_home_dir() / "cache"
    path.mkdir(parents=True, exist_ok=True)
    return path


def database_path() -> Path:
    return ensure_app_home_dir() / DATABASE_FILE_NAME


def stroke_order_cache_dir() -> Path:
    path = ensure_data_dir() / KANJIVG_DIR_NAME
    path.mkdir(parents=True, exist_ok=True)
    return path


def kanjidic_cache_path() -> Path:
    return ensure_data_dir() / KANJIDIC2_FILE_NAME
