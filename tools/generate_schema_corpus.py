#!/usr/bin/env python3
"""Generate the Goal 178 schema manifest and historical SQLite corpus."""

from __future__ import annotations

import argparse
import gzip
import hashlib
import sqlite3
import tempfile
from dataclasses import dataclass
from pathlib import Path


RESOURCE_PREFIX = "dev/bee/kanjianki/fixtures/goal178"
FIXTURE_SETTING_KEY = "goal178.fixture"


@dataclass(frozen=True)
class HistoricalSchema:
    fixture_id: str
    version: int
    source_ref: str
    source_commit: str
    resource: str
    resource_sha256: str


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def parse_properties(path: Path) -> dict[str, str]:
    properties: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if not separator:
            raise ValueError(f"Malformed property in {path}: {raw_line}")
        properties[key] = value
    return properties


def historical_schemas(repo_root: Path) -> list[HistoricalSchema]:
    registry = (
        repo_root
        / "app/src/test/resources/dev/bee/kanjianki/fixtures/goal165/schema-fixtures.tsv"
    )
    fixtures: list[HistoricalSchema] = []
    for raw_line in registry.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        fields = line.split("\t")
        if len(fields) != 8:
            raise ValueError(f"Malformed historical schema row: {raw_line}")
        fixtures.append(
            HistoricalSchema(
                fixture_id=fields[0],
                version=int(fields[1]),
                source_ref=fields[2],
                source_commit=fields[3],
                resource=fields[4],
                resource_sha256=fields[5],
            )
        )
    return fixtures


def resource_path(repo_root: Path, resource: str) -> Path:
    return repo_root / "app/src/test/resources" / resource


def table_columns(connection: sqlite3.Connection, table: str) -> set[str]:
    return {str(row[1]) for row in connection.execute(f'PRAGMA table_info("{table}")')}


def table_exists(connection: sqlite3.Connection, table: str) -> bool:
    row = connection.execute(
        "SELECT 1 FROM sqlite_schema WHERE type='table' AND name=?",
        (table,),
    ).fetchone()
    return row is not None


def insert_available(
    connection: sqlite3.Connection,
    table: str,
    values: dict[str, object],
) -> None:
    if not table_exists(connection, table):
        return
    available = table_columns(connection, table)
    selected = [(column, value) for column, value in values.items() if column in available]
    if not selected:
        raise ValueError(f"No representative columns exist in {table}")
    columns = ", ".join(f'"{column}"' for column, _ in selected)
    placeholders = ", ".join("?" for _ in selected)
    connection.execute(
        f'INSERT INTO "{table}" ({columns}) VALUES ({placeholders})',
        tuple(value for _, value in selected),
    )


def insert_representative_rows(
    connection: sqlite3.Connection,
    fixture: HistoricalSchema,
) -> None:
    version = fixture.version
    note_id = 178_000 + version
    card_id = 278_000 + version
    timestamp = 1_780_000 + version
    label = f"goal178-{fixture.fixture_id}"

    insert_available(
        connection,
        "settings",
        {"key": FIXTURE_SETTING_KEY, "value": label, "updated_at": timestamp},
    )
    insert_available(
        connection,
        "sync_runs",
        {
            "started_at": timestamp,
            "finished_at": timestamp + 10,
            "status": "success",
            "active_notes_count": 1,
            "active_cards_count": 1,
            "suspended_cards_archived_count": 0,
            "suspended_kanji_imported_count": 0,
            "deleted_notes_count": 0,
            "deleted_cards_count": 0,
            "error_code": None,
            "error_message": None,
            "removal_message": "",
        },
    )
    insert_available(
        connection,
        "source_notes",
        {
            "note_id": note_id,
            "model_name": "Goal178",
            "expression": "fixture",
            "reading": "fixture",
            "meaning": label,
            "sentence": "representative historical row",
            "fields_json": '{"fixture":true}',
            "tags": "goal178",
            "last_seen_sync_id": 1,
        },
    )
    insert_available(
        connection,
        "source_cards",
        {
            "card_id": card_id,
            "note_id": note_id,
            "deck_name": "Goal178",
            "ord": 0,
            "queue": 2,
            "type": 2,
            "due": 17,
            "interval_days": 23,
            "reps": 7,
            "lapses": 1,
            "fsrs_stability": 12.5,
            "fsrs_difficulty": 6.5,
            "fsrs_retrievability": 0.91,
            "last_seen_sync_id": 1,
        },
    )
    insert_available(
        connection,
        "dashboard_rows",
        {
            "kanji": "F",
            "jiten_rank": 178,
            "primary_meaning": label,
            "reading": "fixture",
            "browser_search": "goal178",
            "weakness_score": 19,
            "reason_code": "fixture",
            "reason_text": "representative historical row",
            "active_example_count": 1,
            "suspended_example_count": 0,
            "mature_support_count": 0,
            "rebuilt_at": timestamp,
        },
    )
    insert_available(
        connection,
        "kanji_examples",
        {
            "kanji": "F",
            "source_type": "active",
            "card_id": card_id,
            "note_id": note_id,
            "expression": "fixture",
            "reading": "fixture",
            "meaning": label,
            "sentence": "representative historical row",
            "mature": 1,
            "lapses": 1,
            "interval_days": 23,
            "reps": 7,
            "fsrs_stability": 12.5,
            "fsrs_difficulty": 6.5,
            "fsrs_retrievability": 0.91,
        },
    )
    insert_available(
        connection,
        "study_items",
        {
            "kanji": "F",
            "state": "review",
            "due_at": timestamp + 20,
            "stability": 3.5,
            "difficulty": 4.5,
            "total_reviews": 7,
            "lapses": 1,
            "learning_step": 0,
            "writing_level": 2,
            "rung": "kanji_meaning",
            "phase": "review",
            "active_token": label,
            "created_at": timestamp,
        },
    )
    insert_available(
        connection,
        "review_log",
        {
            "kanji": "F",
            "token": label,
            "rating": "good",
            "writing_required": 0,
            "writing_passed": 1,
            "manual_override": 0,
            "reviewed_at": timestamp + 30,
        },
    )
    insert_available(
        connection,
        "suspended_archive",
        {
            "card_id": card_id + 1,
            "note_id": note_id + 1,
            "deck_name": "Goal178",
            "model_name": "Goal178",
            "expression": "archived fixture",
            "reading": "fixture",
            "meaning": label,
            "sentence": "",
            "fields_json": "{}",
            "archived_at": timestamp,
            "archived_sync_id": 1,
            "restored_at": None,
        },
    )
    insert_available(
        connection,
        "learning_repeats",
        {
            "kanji": "L",
            "answer_signature": label,
            "task_type": "kanji_meaning",
            "repeat_type": "learning",
            "step_index": 1,
            "due_at": timestamp + 40,
            "active_token": label,
            "created_at": timestamp,
            "updated_at": timestamp,
        },
    )
    insert_available(
        connection,
        "similar_kanji_repair_queue",
        {
            "target_kanji": "S",
            "repair_kanji": "R",
            "choice_signature": label,
            "wrong_selection": "W",
            "prompt_meaning": label,
            "status": "pending",
            "due_at": timestamp + 50,
            "active_token": label,
            "attempts": 1,
            "created_at": timestamp,
            "updated_at": timestamp,
            "completed_at": 0,
        },
    )
    insert_available(
        connection,
        "stats_screen_cache",
        {
            "id": 1,
            "source_version": 1,
            "generated_at": timestamp,
            "cache_format_version": 1,
            "outcome_json": "{}",
            "impact_report_json": "{}",
        },
    )
    insert_available(
        connection,
        "kanji_mnemonic_notes",
        {"kanji": "M", "note": label, "updated_at": timestamp},
    )
    insert_available(
        connection,
        "manual_kanji_sources",
        {
            "literal": "M",
            "source_type": "manual",
            "jiten_rank": 178,
            "meanings_json": '["fixture"]',
            "on_readings_json": "[]",
            "kun_readings_json": "[]",
            "added_at": timestamp,
            "updated_at": timestamp,
            "active": 1,
        },
    )
    insert_available(
        connection,
        "missing_kanji_exports",
        {
            "literal": "M",
            "destination_key": "goal178",
            "exported_at": timestamp,
            "external_note_id": note_id,
        },
    )


def deterministic_gzip(source: Path, destination: Path) -> None:
    with source.open("rb") as input_stream, destination.open("wb") as output_stream:
        with gzip.GzipFile(
            filename="",
            mode="wb",
            fileobj=output_stream,
            compresslevel=9,
            mtime=0,
        ) as compressed:
            compressed.write(input_stream.read())


def generate_database(
    repo_root: Path,
    output_dir: Path,
    fixture: HistoricalSchema,
) -> tuple[str, str]:
    source_path = resource_path(repo_root, fixture.resource)
    source_bytes = source_path.read_bytes()
    if sha256(source_bytes) != fixture.resource_sha256:
        raise ValueError(f"Historical source digest changed for {fixture.fixture_id}")

    with tempfile.TemporaryDirectory(prefix=f"kani-{fixture.fixture_id}-") as temporary:
        database_path = Path(temporary) / f"{fixture.fixture_id}.db"
        connection = sqlite3.connect(database_path)
        try:
            connection.executescript(source_bytes.decode("utf-8"))
            actual_version = int(connection.execute("PRAGMA user_version").fetchone()[0])
            if actual_version != fixture.version:
                raise ValueError(
                    f"{fixture.fixture_id} source declares v{actual_version}, "
                    f"expected v{fixture.version}"
                )
            insert_representative_rows(connection, fixture)
            connection.commit()
            connection.execute("PRAGMA journal_mode=DELETE")
            connection.execute("VACUUM")
            integrity = connection.execute("PRAGMA integrity_check").fetchone()[0]
            if integrity != "ok":
                raise ValueError(f"{fixture.fixture_id} integrity check failed: {integrity}")
        finally:
            connection.close()

        output_name = f"historical-{fixture.fixture_id}.db.gz"
        output_path = output_dir / output_name
        deterministic_gzip(database_path, output_path)
    return f"{RESOURCE_PREFIX}/{output_name}", sha256(output_path.read_bytes())


def canonical_v34_sql(repo_root: Path) -> str:
    source = resource_path(
        repo_root,
        "dev/bee/kanjianki/fixtures/goal165/schema-v33.sql",
    ).read_text(encoding="utf-8")
    source = source.replace(
        "-- Generated from v0.4.231 b2751c469b290abeb9f80ef75ee3d20a23db0a36",
        "-- Canonical fresh v34 schema; v34 adds only a conditional migration row",
        1,
    )
    source = source.replace("PRAGMA user_version = 33;", "PRAGMA user_version = 34;", 1)
    return source


def write_registry(
    output_dir: Path,
    fixtures: list[HistoricalSchema],
    database_resources: dict[str, tuple[str, str]],
) -> Path:
    registry = output_dir / "historical-databases.tsv"
    lines = [
        "# id\tschema_version\tsource_ref\tsource_commit\tsource_schema"
        "\tsource_schema_sha256\tdatabase_resource\tdatabase_sha256\tcontent_policy"
    ]
    for fixture in fixtures:
        database_resource, database_sha256 = database_resources[fixture.fixture_id]
        lines.append(
            "\t".join(
                (
                    fixture.fixture_id,
                    str(fixture.version),
                    fixture.source_ref,
                    fixture.source_commit,
                    fixture.resource,
                    fixture.resource_sha256,
                    database_resource,
                    database_sha256,
                    "synthetic-representative-rows-only",
                )
            )
        )
    registry.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return registry


def write_schema_properties(
    repo_root: Path,
    output_dir: Path,
    canonical_path: Path,
    registry_path: Path,
    fixtures: list[HistoricalSchema],
    database_resources: dict[str, tuple[str, str]],
) -> None:
    goal175 = parse_properties(
        repo_root
        / "app/src/test/resources/dev/bee/kanjianki/fixtures/goal175/schema-v34.properties"
    )
    canonical_source = next(fixture for fixture in fixtures if fixture.version == 33)
    inventory_path = (
        repo_root
        / "app/src/test/resources/dev/bee/kanjianki/fixtures/goal178"
        / "migration-dependencies.tsv"
    )
    migration_digests_path = (
        repo_root
        / "app/src/test/resources/dev/bee/kanjianki/fixtures/goal175"
        / "schema-v34-migration-digests.properties"
    )
    properties = [
        "manifest_format=1",
        "fingerprint_format=1",
        "database_name=kanji_anki_simple.db",
        "database_version=34",
        "stats_source_version_key=stats_source_version",
        "stats_source_version=1",
        "stats_cache_format_version=11",
        "source_binding_format_version=1",
        "source_binding_key_prefix=collection_source_binding.",
        "downgrade_setting_key=downgraded_from_version",
        f"schema_sha256={goal175['schema_sha256']}",
        f"tables={goal175['tables']}",
        f"indexes={goal175['indexes']}",
        f"triggers={goal175['triggers']}",
        f"settings_rows={goal175['settings_rows']}",
        f"canonical_source_ref={canonical_source.source_ref}",
        f"canonical_source_commit={canonical_source.source_commit}",
        f"canonical_source_sql={canonical_source.resource}|"
        f"{canonical_source.resource_sha256}",
        f"canonical_sql={RESOURCE_PREFIX}/schema-v34.sql|"
        f"{sha256(canonical_path.read_bytes())}",
        f"historical_registry={RESOURCE_PREFIX}/historical-databases.tsv|"
        f"{sha256(registry_path.read_bytes())}",
        f"migration_inventory={RESOURCE_PREFIX}/migration-dependencies.tsv|"
        f"{sha256(inventory_path.read_bytes())}",
        f"migration_digests={RESOURCE_PREFIX}/schema-v34-migration-digests.properties|"
        f"{sha256(migration_digests_path.read_bytes())}",
    ]
    for fixture in fixtures:
        database_resource, database_sha256 = database_resources[fixture.fixture_id]
        properties.append(
            f"historical_{fixture.fixture_id}={database_resource}|{database_sha256}"
        )
    (output_dir / "schema-v34.properties").write_text(
        "\n".join(properties) + "\n",
        encoding="utf-8",
    )


def generate(repo_root: Path, output_dir: Path) -> None:
    output_dir.mkdir(parents=True, exist_ok=True)
    fixtures = historical_schemas(repo_root)

    canonical_path = output_dir / "schema-v34.sql"
    canonical_path.write_text(
        canonical_v34_sql(repo_root),
        encoding="utf-8",
    )

    database_resources = {
        fixture.fixture_id: generate_database(repo_root, output_dir, fixture)
        for fixture in fixtures
    }
    registry_path = write_registry(output_dir, fixtures, database_resources)
    write_schema_properties(
        repo_root,
        output_dir,
        canonical_path,
        registry_path,
        fixtures,
        database_resources,
    )

    migration_digests = (
        repo_root
        / "app/src/test/resources/dev/bee/kanjianki/fixtures/goal175"
        / "schema-v34-migration-digests.properties"
    )
    (output_dir / "schema-v34-migration-digests.properties").write_bytes(
        migration_digests.read_bytes()
    )


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--repo-root",
        type=Path,
        default=Path(__file__).resolve().parents[1],
    )
    parser.add_argument("--output-dir", type=Path)
    arguments = parser.parse_args()
    repo_root = arguments.repo_root.resolve()
    output_dir = arguments.output_dir or (
        repo_root
        / "app/src/test/resources/dev/bee/kanjianki/fixtures/goal178"
    )
    generate(repo_root, output_dir.resolve())


if __name__ == "__main__":
    main()
