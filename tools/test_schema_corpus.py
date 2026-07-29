import gzip
import hashlib
import re
import sqlite3
import tempfile
import unittest
from pathlib import Path

from tools.generate_schema_corpus import generate


ROOT = Path(__file__).resolve().parents[1]
RESOURCE_ROOT = (
    ROOT / "app/src/test/resources/dev/bee/kanjianki/fixtures/goal178"
)
GOAL165_ROOT = (
    ROOT / "app/src/test/resources/dev/bee/kanjianki/fixtures/goal165"
)


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def properties(path: Path) -> dict[str, str]:
    parsed: dict[str, str] = {}
    for raw_line in path.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        key, separator, value = line.partition("=")
        if not separator:
            raise AssertionError(f"Malformed property: {raw_line}")
        parsed[key] = value
    return parsed


def normalized_sql(sql: str) -> str:
    normalized = re.sub(r"\s+", " ", sql.strip())
    normalized = re.sub(
        r"\bIF\s+NOT\s+EXISTS\s+",
        "",
        normalized,
        flags=re.IGNORECASE,
    )
    return normalized.lower()


def schema_objects(connection: sqlite3.Connection) -> dict[tuple[str, str], str]:
    rows = connection.execute(
        """
        SELECT type, name, sql
        FROM sqlite_schema
        WHERE name NOT LIKE 'sqlite_%' AND sql IS NOT NULL
        ORDER BY type, name
        """
    )
    return {
        (str(object_type), str(name)): normalized_sql(str(sql))
        for object_type, name, sql in rows
    }


def historical_rows() -> list[list[str]]:
    rows: list[list[str]] = []
    registry = RESOURCE_ROOT / "historical-databases.tsv"
    for raw_line in registry.read_text(encoding="utf-8").splitlines():
        line = raw_line.strip()
        if not line or line.startswith("#"):
            continue
        fields = line.split("\t")
        if len(fields) != 9:
            raise AssertionError(f"Malformed historical database row: {raw_line}")
        rows.append(fields)
    return rows


class SchemaCorpusTest(unittest.TestCase):
    def test_manifest_hashes_every_executable_resource(self) -> None:
        manifest = properties(RESOURCE_ROOT / "schema-v34.properties")
        self.assertEqual("1", manifest["manifest_format"])
        self.assertEqual("34", manifest["database_version"])
        self.assertEqual("11", manifest["stats_cache_format_version"])
        self.assertEqual("1", manifest["source_binding_format_version"])

        for key in (
            "canonical_sql",
            "historical_registry",
            "migration_inventory",
            "migration_digests",
            "historical_v1",
            "historical_v30",
            "historical_v31",
            "historical_v32",
            "historical_v33",
        ):
            resource, expected_hash = manifest[key].split("|", 1)
            path = ROOT / "app/src/test/resources" / resource
            self.assertTrue(path.is_file(), f"{key} resource is missing")
            self.assertEqual(expected_hash, sha256(path), key)

        source_resource, source_hash = manifest["canonical_source_sql"].split("|", 1)
        source_row = next(row for row in historical_rows() if row[4] == source_resource)
        self.assertEqual(manifest["canonical_source_ref"], source_row[2])
        self.assertEqual(manifest["canonical_source_commit"], source_row[3])
        self.assertEqual(source_hash, sha256(ROOT / "app/src/test/resources" / source_resource))

    def test_generated_corpus_is_byte_reproducible(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "goal178"
            generate(ROOT, output)
            generated_names = sorted(path.name for path in output.iterdir())
            self.assertEqual(
                [
                    "historical-databases.tsv",
                    "historical-v1.db.gz",
                    "historical-v30.db.gz",
                    "historical-v31.db.gz",
                    "historical-v32.db.gz",
                    "historical-v33.db.gz",
                    "schema-v34-migration-digests.properties",
                    "schema-v34.properties",
                    "schema-v34.sql",
                ],
                generated_names,
            )
            for name in generated_names:
                self.assertEqual(
                    (RESOURCE_ROOT / name).read_bytes(),
                    (output / name).read_bytes(),
                    name,
                )

    def test_historical_databases_have_real_old_shapes_and_fixture_rows(self) -> None:
        current_sql = (RESOURCE_ROOT / "schema-v34.sql").read_text(encoding="utf-8")
        current = sqlite3.connect(":memory:")
        self.addCleanup(current.close)
        current.executescript(current_sql)
        current_objects = schema_objects(current)

        rows = historical_rows()
        self.assertEqual([1, 30, 31, 32, 33], [int(row[1]) for row in rows])
        for row in rows:
            (
                fixture_id,
                version_text,
                _source_ref,
                source_commit,
                source_resource,
                source_hash,
                database_resource,
                database_hash,
                content_policy,
            ) = row
            version = int(version_text)
            self.assertRegex(source_commit, r"^[0-9a-f]{40}$")
            self.assertEqual("synthetic-representative-rows-only", content_policy)

            source_path = ROOT / "app/src/test/resources" / source_resource
            database_path = ROOT / "app/src/test/resources" / database_resource
            self.assertEqual(source_hash, sha256(source_path))
            self.assertEqual(database_hash, sha256(database_path))

            expected = sqlite3.connect(":memory:")
            self.addCleanup(expected.close)
            expected.executescript(source_path.read_text(encoding="utf-8"))

            with tempfile.TemporaryDirectory() as temporary:
                unpacked = Path(temporary) / f"{fixture_id}.db"
                unpacked.write_bytes(gzip.decompress(database_path.read_bytes()))
                actual = sqlite3.connect(unpacked)
                try:
                    self.assertEqual("ok", actual.execute("PRAGMA integrity_check").fetchone()[0])
                    self.assertEqual(version, actual.execute("PRAGMA user_version").fetchone()[0])
                    self.assertEqual(schema_objects(expected), schema_objects(actual))
                    self.assertEqual(
                        f"goal178-{fixture_id}",
                        actual.execute(
                            "SELECT value FROM settings WHERE key='goal178.fixture'"
                        ).fetchone()[0],
                    )
                    self.assertEqual(
                        "Goal178",
                        actual.execute("SELECT model_name FROM source_notes").fetchone()[0],
                    )
                    self.assertEqual(
                        "review",
                        actual.execute("SELECT state FROM study_items").fetchone()[0],
                    )
                    self.assertEqual(
                        f"goal178-{fixture_id}",
                        actual.execute("SELECT token FROM review_log").fetchone()[0],
                    )
                    if version >= 30:
                        self.assertEqual(
                            "pending",
                            actual.execute(
                                "SELECT status FROM similar_kanji_repair_queue"
                            ).fetchone()[0],
                        )
                finally:
                    actual.close()

            if version < 33:
                self.assertNotEqual(
                    current_objects,
                    schema_objects(expected),
                    f"{fixture_id} must not be a v34 database with a changed user_version",
                )

    def test_canonical_schema_and_production_sql_features_run_on_desktop_sqlite(
        self,
    ) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            directory = Path(temporary)
            database = directory / "canonical.db"
            snapshot = directory / "snapshot.db"
            connection = sqlite3.connect(database)
            try:
                connection.executescript(
                    (RESOURCE_ROOT / "schema-v34.sql").read_text(encoding="utf-8")
                )
                self.assertEqual(34, connection.execute("PRAGMA user_version").fetchone()[0])

                connection.execute("BEGIN IMMEDIATE")
                connection.execute(
                    "INSERT INTO settings(key, value, updated_at) VALUES (?, ?, ?)",
                    ("rollback-probe", "value", 1),
                )
                connection.rollback()
                self.assertEqual(
                    0,
                    connection.execute(
                        "SELECT COUNT(*) FROM settings WHERE key='rollback-probe'"
                    ).fetchone()[0],
                )

                connection.execute("CREATE TABLE feature_probe(id INTEGER PRIMARY KEY)")
                connection.execute(
                    "ALTER TABLE feature_probe "
                    "ADD COLUMN label TEXT NOT NULL DEFAULT ''"
                )
                connection.execute("INSERT INTO feature_probe(id) VALUES (1)")
                connection.execute(
                    """
                    INSERT OR REPLACE INTO settings(key, value, updated_at)
                    SELECT ?, ?, 0
                    WHERE EXISTS (SELECT 1 FROM feature_probe WHERE id=?)
                    """,
                    ("exists-probe", "present", 1),
                )
                self.assertEqual(
                    "present",
                    connection.execute(
                        "SELECT value FROM settings WHERE key='exists-probe'"
                    ).fetchone()[0],
                )

                connection.execute(
                    "INSERT INTO review_log("
                    "kanji, token, rating, writing_required, writing_passed, "
                    "manual_override, reviewed_at"
                    ") VALUES ('F', 'token', 'good', 0, 1, 0, 1)"
                )
                connection.execute(
                    "INSERT OR IGNORE INTO review_log("
                    "kanji, token, rating, writing_required, writing_passed, "
                    "manual_override, reviewed_at"
                    ") VALUES ('F', 'token', 'again', 0, 0, 0, 2)"
                )
                self.assertEqual(
                    1,
                    connection.execute(
                        "SELECT COUNT(*) FROM review_log WHERE token='token'"
                    ).fetchone()[0],
                )

                connection.execute("ALTER TABLE feature_probe RENAME TO feature_probe_old")
                connection.execute("DROP TABLE feature_probe_old")
                connection.commit()
                connection.execute("VACUUM INTO ?", (str(snapshot),))
            finally:
                connection.close()

            copied = sqlite3.connect(snapshot)
            try:
                self.assertEqual("ok", copied.execute("PRAGMA quick_check").fetchone()[0])
                self.assertEqual(34, copied.execute("PRAGMA user_version").fetchone()[0])
                self.assertEqual(
                    "present",
                    copied.execute(
                        "SELECT value FROM settings WHERE key='exists-probe'"
                    ).fetchone()[0],
                )
            finally:
                copied.close()

    def test_migration_dependency_inventory_tracks_every_production_step(self) -> None:
        inventory_path = RESOURCE_ROOT / "migration-dependencies.tsv"
        rows: dict[str, list[str]] = {}
        for raw_line in inventory_path.read_text(encoding="utf-8").splitlines():
            line = raw_line.strip()
            if not line or line.startswith("#"):
                continue
            fields = line.split("\t")
            self.assertEqual(7, len(fields), raw_line)
            self.assertNotIn(fields[0], rows)
            rows[fields[0]] = fields

        expected_scopes = [f"v{version}" for version in range(2, 35)] + ["downgrade"]
        self.assertEqual(expected_scopes, list(rows))
        self.assertEqual("system wall clock", rows["v9"][2])
        self.assertEqual("system wall clock", rows["v11"][2])
        self.assertIn("wall clock", rows["v2"][2])
        self.assertEqual("system wall clock", rows["downgrade"][2])

        migrations = (
            ROOT
            / "app/src/main/kotlin/dev/bee/kanjianki/data/LocalStoreMigrations.kt"
        ).read_text(encoding="utf-8")
        implemented_versions = [
            int(version)
            for version in re.findall(
                r"shouldRun\(oldVersion,\s*targetVersion,\s*(\d+)\)",
                migrations,
            )
        ]
        self.assertEqual(list(range(2, 35)), implemented_versions)
        self.assertEqual(2, migrations.count("System.currentTimeMillis()"))
        self.assertIn(
            "System.currentTimeMillis()",
            (
                ROOT
                / "app/src/main/kotlin/dev/bee/kanjianki/data/LocalStoreTimeline.kt"
            ).read_text(encoding="utf-8"),
        )

    def test_canonical_object_names_match_manifest(self) -> None:
        manifest = properties(RESOURCE_ROOT / "schema-v34.properties")
        connection = sqlite3.connect(":memory:")
        self.addCleanup(connection.close)
        connection.executescript(
            (RESOURCE_ROOT / "schema-v34.sql").read_text(encoding="utf-8")
        )
        objects = schema_objects(connection)
        tables = sorted(name for object_type, name in objects if object_type == "table")
        indexes = sorted(name for object_type, name in objects if object_type == "index")
        triggers = sorted(name for object_type, name in objects if object_type == "trigger")
        self.assertEqual(manifest["tables"].split(","), tables)
        self.assertEqual(manifest["indexes"].split(","), indexes)
        self.assertEqual(
            [name for name in manifest["triggers"].split(",") if name],
            triggers,
        )


if __name__ == "__main__":
    unittest.main()
