package dev.bee.kanjianki.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.testing.GoldenFixtureResources
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.GZIPInputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SchemaMigrationCorpusTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        deleteDatabase()
    }

    @After
    fun tearDown() {
        deleteDatabase()
    }

    @Test
    fun historicalDatabasesMatchPinnedOldSchemasBeforeMigration() {
        for (fixture in fixtures()) {
            deleteDatabase()
            val compressed = GoldenFixtureResources.bytes(fixture.databaseResource)
            assertEquals(
                "${fixture.id} compressed database digest",
                fixture.databaseSha256,
                GoldenFixtureResources.sha256(compressed),
            )
            assertEquals(
                "${fixture.id} source schema digest",
                fixture.sourceSchemaSha256,
                GoldenFixtureResources.sha256(GoldenFixtureResources.bytes(fixture.sourceSchema)),
            )
            installDatabase(compressed)

            val expectedPath = File(context.cacheDir, "goal178-${fixture.id}-source.db")
            deleteDatabaseFiles(expectedPath)
            try {
                SQLiteDatabase.openDatabase(
                    expectedPath.absolutePath,
                    null,
                    SQLiteDatabase.CREATE_IF_NECESSARY,
                ).use { expected ->
                    SqlSchemaFixtureLoader.load(
                        expected,
                        GoldenFixtureResources.text(fixture.sourceSchema),
                    )
                    SQLiteDatabase.openDatabase(
                        databasePath().absolutePath,
                        null,
                        SQLiteDatabase.OPEN_READONLY,
                    ).use { actual ->
                        assertEquals("${fixture.id} integrity", "ok", scalarText(actual, "PRAGMA integrity_check"))
                        assertEquals("${fixture.id} source version", fixture.schemaVersion, actual.version)
                        assertEquals(
                            "${fixture.id} pinned source schema",
                            schemaObjects(expected),
                            schemaObjects(actual),
                        )
                        assertSourceRows(actual, fixture)
                    }
                }
            } finally {
                deleteDatabaseFiles(expectedPath)
            }
        }
    }

    @Test
    fun historicalDatabasesMigrateAndDowngradeWithRowsIntact() {
        val canonical = executableSchemaSnapshot()
        for (fixture in fixtures()) {
            deleteDatabase()
            installDatabase(GoldenFixtureResources.bytes(fixture.databaseResource))

            LocalStore(context).use { migrated ->
                val database = migrated.writableDatabase
                assertEquals(
                    "${fixture.id} migrated version",
                    LocalStoreSchema.DB_VERSION,
                    database.version,
                )
                assertEquals("${fixture.id} migrated integrity", "ok", scalarText(database, "PRAGMA integrity_check"))
                val snapshot = SchemaFingerprint.capture(database)
                SchemaGoldenVerifier.assertStructurallySchemaEquivalent(
                    canonical,
                    snapshot,
                    "${fixture.id} migrated",
                )
                assertEquals("${fixture.id} tables", canonical.tableNames, snapshot.tableNames)
                assertEquals("${fixture.id} indexes", canonical.indexNames, snapshot.indexNames)
                assertEquals("${fixture.id} triggers", canonical.triggerNames, snapshot.triggerNames)
                assertMigratedRows(database, fixture)
            }

            setDatabaseVersion(LocalStoreSchema.DB_VERSION + 1)
            LocalStore(context).use { downgraded ->
                val database = downgraded.writableDatabase
                assertEquals(
                    "${fixture.id} downgraded version",
                    LocalStoreSchema.DB_VERSION,
                    database.version,
                )
                assertEquals(
                    "${fixture.id} downgrade marker",
                    (LocalStoreSchema.DB_VERSION + 1).toString(),
                    setting(database, LocalStoreBase.SETTING_DOWNGRADED_FROM_VERSION),
                )
                assertTrue(
                    "${fixture.id} downgrade marker timestamp",
                    scalarLong(
                        database,
                        "SELECT updated_at FROM settings WHERE key=?",
                        arrayOf(LocalStoreBase.SETTING_DOWNGRADED_FROM_VERSION),
                    ) > 0L,
                )
                assertMigratedRows(database, fixture)
            }
        }
    }

    private fun assertSourceRows(database: SQLiteDatabase, fixture: Fixture) {
        val label = fixture.label
        assertEquals(
            "${fixture.id} fixture marker",
            label,
            setting(database, FIXTURE_SETTING_KEY),
        )
        assertEquals(
            "${fixture.id} source note",
            label,
            scalarText(database, "SELECT meaning FROM source_notes"),
        )
        assertEquals(
            "${fixture.id} source card",
            23L,
            scalarLong(database, "SELECT interval_days FROM source_cards"),
        )
        assertEquals(
            "${fixture.id} study item",
            "review",
            scalarText(database, "SELECT state FROM study_items"),
        )
        assertEquals(
            "${fixture.id} review token",
            label,
            scalarText(database, "SELECT token FROM review_log"),
        )
        if (fixture.schemaVersion >= 30) {
            assertEquals(
                "${fixture.id} compatibility repair row",
                label,
                scalarText(database, "SELECT choice_signature FROM similar_kanji_repair_queue"),
            )
            assertEquals(
                "${fixture.id} compatibility repeat row",
                label,
                scalarText(database, "SELECT answer_signature FROM learning_repeats"),
            )
        }
        if (fixture.schemaVersion >= 32) {
            assertEquals(
                "${fixture.id} mnemonic row",
                label,
                scalarText(database, "SELECT note FROM kanji_mnemonic_notes"),
            )
        }
        if (fixture.schemaVersion >= 33) {
            assertEquals(
                "${fixture.id} manual source row",
                """["fixture"]""",
                scalarText(database, "SELECT meanings_json FROM manual_kanji_sources"),
            )
            assertEquals(
                "${fixture.id} export receipt row",
                178_000L + fixture.schemaVersion,
                scalarLong(database, "SELECT external_note_id FROM missing_kanji_exports"),
            )
        }
    }

    private fun assertMigratedRows(database: SQLiteDatabase, fixture: Fixture) {
        val label = fixture.label
        assertEquals("${fixture.id} fixture marker", label, setting(database, FIXTURE_SETTING_KEY))
        assertEquals(
            "${fixture.id} v34 source-binding marker",
            SourceBindingMigrationRecord.ELIGIBLE,
            setting(database, SourceBindingMigrationRecord.KEY_ANDROID_LEGACY_MIGRATION),
        )
        assertEquals(
            "${fixture.id} stats source version",
            1L,
            scalarLong(
                database,
                "SELECT value FROM stats_cache_state WHERE key=?",
                arrayOf(LocalStoreBase.STATS_CACHE_SOURCE_VERSION_KEY),
            ),
        )
        assertEquals(
            "${fixture.id} source note meaning",
            label,
            scalarText(database, "SELECT meaning FROM source_notes"),
        )
        assertEquals(
            "${fixture.id} source card interval",
            23L,
            scalarLong(database, "SELECT interval_days FROM source_cards"),
        )
        if (fixture.schemaVersion == 1) {
            assertNull(
                "${fixture.id} added FSRS column default",
                scalarNullableDouble(database, "SELECT fsrs_stability FROM source_cards"),
            )
        } else {
            assertEquals(
                "${fixture.id} FSRS value",
                12.5,
                scalarNullableDouble(database, "SELECT fsrs_stability FROM source_cards")!!,
                0.0,
            )
        }
        assertEquals(
            "${fixture.id} dashboard row",
            label,
            scalarText(database, "SELECT primary_meaning FROM dashboard_rows WHERE kanji='F'"),
        )
        assertEquals(
            "${fixture.id} review token",
            label,
            scalarText(database, "SELECT token FROM review_log"),
        )
        assertEquals(
            "${fixture.id} review migration default",
            "",
            scalarText(database, "SELECT core_skill FROM review_log"),
        )
        assertEquals(
            "${fixture.id} study state",
            "review",
            scalarText(database, "SELECT state FROM study_items WHERE kanji='F'"),
        )
        assertEquals(
            "${fixture.id} study phase",
            "review",
            scalarText(database, "SELECT phase FROM study_items WHERE kanji='F'"),
        )
        assertEquals(
            "${fixture.id} study rung",
            "kanji_meaning",
            scalarText(database, "SELECT rung FROM study_items WHERE kanji='F'"),
        )
        assertEquals(
            "${fixture.id} scheduler revision default",
            0L,
            scalarLong(database, "SELECT scheduler_revision FROM study_items WHERE kanji='F'"),
        )
        assertEquals(
            "${fixture.id} routing version default",
            1L,
            scalarLong(database, "SELECT routing_version FROM study_items WHERE kanji='F'"),
        )
        assertEquals(
            "${fixture.id} archived row",
            label,
            scalarText(database, "SELECT meaning FROM suspended_archive"),
        )

        for (table in COMPATIBILITY_TABLES) {
            assertTrue("${fixture.id} compatibility table $table", tableExists(database, table))
        }
        if (fixture.schemaVersion >= 30) {
            assertEquals(
                "${fixture.id} compatibility repair row",
                label,
                scalarText(database, "SELECT choice_signature FROM similar_kanji_repair_queue"),
            )
            assertEquals(
                "${fixture.id} compatibility repeat row",
                label,
                scalarText(database, "SELECT answer_signature FROM learning_repeats"),
            )
            assertEquals(
                "${fixture.id} stats cache format preserved",
                1L,
                scalarLong(database, "SELECT cache_format_version FROM stats_screen_cache"),
            )
        }
        assertEquals(
            "${fixture.id} mnemonic preservation",
            if (fixture.schemaVersion >= 32) 1L else 0L,
            scalarLong(database, "SELECT COUNT(*) FROM kanji_mnemonic_notes"),
        )
        assertEquals(
            "${fixture.id} manual source preservation",
            if (fixture.schemaVersion >= 33) 1L else 0L,
            scalarLong(database, "SELECT COUNT(*) FROM manual_kanji_sources"),
        )
        assertEquals(
            "${fixture.id} export receipt preservation",
            if (fixture.schemaVersion >= 33) 1L else 0L,
            scalarLong(database, "SELECT COUNT(*) FROM missing_kanji_exports"),
        )
    }

    private fun executableSchemaSnapshot(): SchemaFingerprint.Snapshot {
        val path = File(context.cacheDir, "goal178-canonical-v34-corpus.db")
        deleteDatabaseFiles(path)
        return try {
            SQLiteDatabase.openDatabase(
                path.absolutePath,
                null,
                SQLiteDatabase.CREATE_IF_NECESSARY,
            ).use { database ->
                SqlSchemaFixtureLoader.load(
                    database,
                    GoldenFixtureResources.text(CANONICAL_SCHEMA),
                )
                SchemaFingerprint.capture(database)
            }
        } finally {
            deleteDatabaseFiles(path)
        }
    }

    private fun installDatabase(compressed: ByteArray) {
        val path = databasePath()
        val parent = requireNotNull(path.parentFile)
        check(parent.isDirectory || parent.mkdirs()) { "Unable to create database directory" }
        GZIPInputStream(ByteArrayInputStream(compressed)).use { input ->
            path.outputStream().use { output -> input.copyTo(output) }
        }
    }

    private fun setDatabaseVersion(version: Int) {
        SQLiteDatabase.openDatabase(
            databasePath().absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { database ->
            database.version = version
        }
    }

    private fun fixtures(): List<Fixture> {
        return GoldenFixtureResources.text(HISTORICAL_REGISTRY)
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith('#') }
            .map { line ->
                val fields = line.split('\t')
                check(fields.size == 9) { "Malformed historical database row: $line" }
                Fixture(
                    id = fields[0],
                    schemaVersion = fields[1].toInt(),
                    sourceRef = fields[2],
                    sourceCommit = fields[3],
                    sourceSchema = fields[4],
                    sourceSchemaSha256 = fields[5],
                    databaseResource = fields[6],
                    databaseSha256 = fields[7],
                    contentPolicy = fields[8],
                )
            }
            .toList()
            .also { fixtures ->
                assertEquals(listOf(1, 30, 31, 32, 33), fixtures.map(Fixture::schemaVersion))
                for (fixture in fixtures) {
                    assertTrue("${fixture.id} source ref", fixture.sourceRef.isNotBlank())
                    assertTrue(
                        "${fixture.id} source commit",
                        fixture.sourceCommit.matches(Regex("[0-9a-f]{40}")),
                    )
                    assertEquals(
                        "${fixture.id} content policy",
                        "synthetic-representative-rows-only",
                        fixture.contentPolicy,
                    )
                }
            }
    }

    private fun schemaObjects(database: SQLiteDatabase): List<String> {
        val objects = ArrayList<String>()
        database.rawQuery(
            "SELECT type, name, tbl_name, sql FROM sqlite_master " +
                "WHERE name NOT LIKE 'sqlite_%' AND name <> 'android_metadata' " +
                "AND sql IS NOT NULL ORDER BY type, name",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                objects +=
                    "${cursor.getString(0)}|${cursor.getString(1)}|${cursor.getString(2)}|" +
                    SchemaFingerprint.normalizeSql(cursor.getString(3))
            }
        }
        return objects
    }

    private fun setting(database: SQLiteDatabase, key: String): String =
        scalarText(database, "SELECT value FROM settings WHERE key=?", arrayOf(key))

    private fun scalarText(
        database: SQLiteDatabase,
        sql: String,
        arguments: Array<String>? = null,
    ): String {
        database.rawQuery(sql, arguments).use { cursor ->
            check(cursor.moveToFirst()) { "Query returned no row: $sql" }
            return cursor.getString(0)
        }
    }

    private fun scalarLong(
        database: SQLiteDatabase,
        sql: String,
        arguments: Array<String>? = null,
    ): Long {
        database.rawQuery(sql, arguments).use { cursor ->
            check(cursor.moveToFirst()) { "Query returned no row: $sql" }
            return cursor.getLong(0)
        }
    }

    private fun scalarNullableDouble(database: SQLiteDatabase, sql: String): Double? {
        database.rawQuery(sql, null).use { cursor ->
            check(cursor.moveToFirst()) { "Query returned no row: $sql" }
            return if (cursor.isNull(0)) null else cursor.getDouble(0)
        }
    }

    private fun tableExists(database: SQLiteDatabase, table: String): Boolean =
        scalarLong(
            database,
            "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?",
            arrayOf(table),
        ) == 1L

    private fun databasePath(): File = context.getDatabasePath(LocalStoreSchema.DB_NAME)

    private fun deleteDatabase() {
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        deleteDatabaseFiles(databasePath())
    }

    private fun deleteDatabaseFiles(path: File) {
        path.delete()
        File(path.absolutePath + "-journal").delete()
        File(path.absolutePath + "-wal").delete()
        File(path.absolutePath + "-shm").delete()
    }

    private data class Fixture(
        val id: String,
        val schemaVersion: Int,
        val sourceRef: String,
        val sourceCommit: String,
        val sourceSchema: String,
        val sourceSchemaSha256: String,
        val databaseResource: String,
        val databaseSha256: String,
        val contentPolicy: String,
    ) {
        val label: String
            get() = "goal178-$id"
    }

    private companion object {
        const val RESOURCE_ROOT = "dev/bee/kanjianki/fixtures/goal178"
        const val HISTORICAL_REGISTRY = "$RESOURCE_ROOT/historical-databases.tsv"
        const val CANONICAL_SCHEMA = "$RESOURCE_ROOT/schema-v34.sql"
        const val FIXTURE_SETTING_KEY = "goal178.fixture"

        val COMPATIBILITY_TABLES = listOf(
            "learning_repeats",
            "similar_kanji_choice_state",
            "similar_kanji_repair_queue",
            "similar_kanji_review_log",
        )
    }
}
