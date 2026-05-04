from __future__ import annotations

import argparse
from typing import Sequence

from .api import create_app
from .service import KanjiCompanionService


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="kanji-companion")
    subparsers = parser.add_subparsers(dest="command", required=True)

    run_parser = subparsers.add_parser("run", help="Run the FastAPI server with uvicorn.")
    run_parser.add_argument("--host", default="127.0.0.1")
    run_parser.add_argument("--port", type=int, default=8768)

    subparsers.add_parser("sync-now", help="Pull the current Anki collection state from AnkiConnect.")
    subparsers.add_parser("rebuild-analysis", help="Rebuild derived dashboard and problem-kanji snapshots from the local database.")
    return parser


def main(argv: Sequence[str] | None = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)
    service = KanjiCompanionService()

    if args.command == "run":
        try:
            import uvicorn
        except ModuleNotFoundError as error:  # pragma: no cover - env dependent
            raise SystemExit(
                "uvicorn is not installed. Install project dependencies before running the server."
            ) from error
        uvicorn.run(create_app(service), host=args.host, port=args.port)
        return 0

    if args.command == "sync-now":
        result = service.sync_ankiconnect()
        print(result["syncRun"])
        return 0

    if args.command == "rebuild-analysis":
        result = service.rebuild_analysis()
        print(result["analysis"])
        return 0

    parser.error(f"Unknown command: {args.command}")
    return 2
