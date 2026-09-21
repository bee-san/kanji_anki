#!/usr/bin/env python3
"""Pre-seeds an Anki Desktop base directory so first launch is non-interactive.

A fresh base directory has no `prefs21.db`. Anki then shows a modal language
dialog and waits for a click before loading the main window -- and AnkiConnect
starts from the main window, so on a headless host the fixture hangs with its
port closed and no error. Automating the click would need `xdotool`, which is
not packaged for Amazon Linux 2023, and would be brittle regardless.

This writes the preferences Anki would have written itself: a `_global` row with
`firstRun` already false and `defaultLang` set, plus one row per profile. The
defaults come from Anki's own `aqt.profiles` module, read out of the bundled
interpreter, so this does not carry a hand-copied schema that silently drifts
when upstream adds a key.

Run under the *bundled* interpreter, not the system one:

    cd anki-linux
    PYTHONHOME="$PWD/python" ./python/bin/python3 \\
      ci/scripts/seed_anki_desktop_profile.py --base /tmp/fixture/base \\
      --profile KaniFixture

Only `--defaults-from-anki` needs the bundled interpreter. Without it the
built-in fallback defaults are used and any Python 3.11+ will do, which is what
the unit tests exercise.
"""

from __future__ import annotations

import argparse
import pickle
import sqlite3
import sys
from pathlib import Path

# Anki pickles profile rows with protocol 4 (`ProfileManager._pickle`). Writing
# a different protocol would still load, but matching it keeps a seeded row
# byte-comparable with one Anki wrote.
PICKLE_PROTOCOL = 4

# Used when `--defaults-from-anki` is not passed. Kept deliberately minimal:
# Anki fills in anything absent on load, and the only keys that must be right
# are the two that suppress the first-run dialog.
FALLBACK_META = {
    "ver": 0,
    "updates": True,
    "created": 1_785_000_000,
    "id": 1,
    "lastMsg": 0,
    "suppressUpdate": False,
    "firstRun": True,
    "defaultLang": None,
}

FALLBACK_PROFILE = {
    "activeDecks": [1],
    "curDeck": 1,
    "newSpread": 0,
    "collapseTime": 1200,
    "timeLim": 0,
    "curModel": None,
    "numBackups": 50,
    "lastOptimize": 0,
}


def anki_defaults() -> tuple[dict, dict]:
    """Anki's own `metaConf`/`profileConf` defaults.

    Importable only from the bundled interpreter with `app_packages` on the
    path. Note that `anki-linux/app` must *not* be added: it holds a stub `anki`
    package that shadows the real one, and the import then fails with
    `No module named 'anki.collection'`.
    """
    import site

    site.addsitedir(str(Path.cwd() / "app_packages"))
    import aqt.profiles as profiles

    return dict(profiles.metaConf), dict(profiles.profileConf)


def seed(
    base: Path,
    profiles: list[str],
    *,
    language: str = "en_US",
    defaults_from_anki: bool = False,
) -> Path:
    """Writes `<base>/prefs21.db` with [profiles] present and first run done.

    The first entry of [profiles] is recorded as the last-loaded one, which is
    what Anki opens when `-p` is not passed.
    """
    if not profiles:
        raise ValueError("at least one profile name is required")

    meta, profile_defaults = (
        anki_defaults() if defaults_from_anki else (dict(FALLBACK_META), dict(FALLBACK_PROFILE))
    )
    meta["firstRun"] = False
    meta["defaultLang"] = language
    meta["last_loaded_profile_name"] = profiles[0]

    base.mkdir(parents=True, exist_ok=True)
    path = base / "prefs21.db"
    connection = sqlite3.connect(path)
    try:
        connection.execute(
            "create table if not exists profiles"
            " (name text primary key collate nocase, data blob not null)"
        )
        rows = [("_global", meta)] + [(name, dict(profile_defaults)) for name in profiles]
        connection.executemany(
            "insert or replace into profiles (name, data) values (?, ?)",
            [(name, pickle.dumps(data, protocol=PICKLE_PROTOCOL)) for name, data in rows],
        )
        connection.commit()
    finally:
        connection.close()
    return path


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base", required=True, type=Path, help="Anki base directory")
    parser.add_argument(
        "--profile",
        action="append",
        default=None,
        dest="profiles",
        help="profile to create; repeatable. The first is opened by default.",
    )
    parser.add_argument("--language", default="en_US")
    parser.add_argument(
        "--defaults-from-anki",
        action="store_true",
        help="read defaults from aqt.profiles; requires the bundled interpreter.",
    )
    args = parser.parse_args(argv)

    path = seed(
        args.base,
        args.profiles or ["KaniFixture"],
        language=args.language,
        defaults_from_anki=args.defaults_from_anki,
    )
    print(path)
    return 0


if __name__ == "__main__":
    sys.exit(main())
