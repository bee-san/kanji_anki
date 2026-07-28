package dev.bee.kanjianki.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.testing.GoldenFixtureResources
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SchemaBaselineTest {
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
    fun freshV34MatchesTheCurrentSemanticFingerprint() {
        val expected = GoldenFixtureResources.properties(CURRENT_SCHEMA_GOLDEN)
        val snapshot = freshSnapshot()

        assertEquals("1", expected.required("fingerprint_format"))
        assertEquals(LocalStoreSchema.DB_VERSION, snapshot.userVersion)
        SchemaGoldenVerifier.assertDigest(
            expected.required("schema_sha256"),
            snapshot,
            "fresh v${LocalStoreSchema.DB_VERSION}",
        )
        assertEquals(expected.required("tables").split(','), snapshot.tableNames)
        assertEquals(expected.required("indexes").split(',').filter(String::isNotEmpty), snapshot.indexNames)
        assertEquals(expected.required("triggers").split(',').filter(String::isNotEmpty), snapshot.triggerNames)
        assertEquals(expected.required("settings_rows").toInt(), snapshot.settingsRowCount)
        assertEquals(expected.required("stats_source_version").toLong(), snapshot.statsSourceVersion)
        assertEquals(expected.required("stats_cache_format_version").toInt(), snapshot.statsCacheFormatVersion)

        val schema = snapshot.canonical
        assertTrue(
            "single-row stats cache CHECK constraint must stay frozen",
            schema.contains(
                "schema|table|stats_screen_cache|stats_screen_cache|" +
                    "create table stats_screen_cache(id integer primary key check(id=1)",
            ),
        )
        assertTrue(
            "review idempotency token must remain unique",
            schema.contains("token text not null unique"),
        )
        assertTrue(
            "study-item family identity must remain a composite primary key",
            schema.contains("primary key(kanji,answer_signature)"),
        )
    }

    @Test
    fun retainedHistoricalFreshSchemasMatchV34MigrationFingerprints() {
        val expected = freshSnapshot()
        val expectedDigests =
            GoldenFixtureResources.properties(CURRENT_MIGRATION_DIGESTS)
        val digestMismatches = ArrayList<String>()
        for (fixture in fixtureRegistry()) {
            deleteDatabase()
            createDatabaseFromFixture(fixture)
            LocalStore(context).use { migrated ->
                val actual = SchemaFingerprint.capture(migrated.writableDatabase)
                assertEquals(
                    "${fixture.id} must migrate to user_version ${LocalStoreSchema.DB_VERSION}",
                    LocalStoreSchema.DB_VERSION,
                    actual.userVersion,
                )
                SchemaGoldenVerifier.assertStructurallyEquivalent(expected, actual, fixture.id)
                assertRepresentativeConstraints(actual)
                val expectedDigest = expectedDigests.required(fixture.id)
                if (expectedDigest != actual.sha256) {
                    digestMismatches +=
                        "${fixture.id}: expected $expectedDigest, actual ${actual.sha256}"
                }
            }
        }
        assertEquals("migration fingerprint digest mismatches", emptyList<String>(), digestMismatches)
    }

    @Test
    fun fixtureRegistryPinsSanitizedProvenanceAndContentDigests() {
        val fixtures = fixtureRegistry()

        assertEquals(listOf(1, 30, 31, 32, 33), fixtures.map(Fixture::schemaVersion))
        for (fixture in fixtures) {
            assertTrue("${fixture.id} must pin a source ref", fixture.sourceRef.isNotBlank())
            assertTrue(
                "${fixture.id} must pin a full source commit",
                fixture.sourceCommit.matches(Regex("[0-9a-f]{40}")),
            )
            assertEquals("empty-production-schema-and-built-in-seeds-only", fixture.contentPolicy)
            assertTrue(
                "${fixture.id} must pin its migrated v33 fingerprint",
                fixture.expectedV33Sha256.matches(Regex("[0-9a-f]{64}")),
            )
            val bytes = GoldenFixtureResources.bytes(fixture.resource)
            assertEquals(
                "${fixture.id} resource digest",
                fixture.sha256,
                GoldenFixtureResources.sha256(bytes),
            )
            assertFixtureContainsNoUserRows(fixture, bytes.decodeToString())
        }
    }

    @Test
    fun verifierRejectsAPerturbedProductionSchema() {
        LocalStore(context).use { store ->
            val expected = SchemaFingerprint.capture(store.writableDatabase)
            store.writableDatabase.execSQL("DROP INDEX idx_study_due")
            val perturbed = SchemaFingerprint.capture(store.writableDatabase)

            val failure = assertThrows(AssertionError::class.java) {
                SchemaGoldenVerifier.assertEquivalent(expected, perturbed, "dropped-index probe")
            }
            assertTrue(failure.message.orEmpty().contains("schema fingerprint mismatch"))
            assertTrue(failure.message.orEmpty().contains("idx_study_due"))
        }
    }

    @Test
    fun sqlFixtureSplitterPreservesTriggerBodiesAndQuotedSemicolons() {
        val statements = SqlSchemaFixtureLoader.splitStatements(
            """
            -- a fixture comment
            CREATE TABLE probe(value TEXT);
            CREATE TRIGGER probe_insert AFTER INSERT ON probe BEGIN
              INSERT INTO probe(value) VALUES ('inside;trigger');
            END;
            PRAGMA user_version=7;
            """.trimIndent(),
        )

        assertEquals(3, statements.size)
        assertTrue(statements[1].contains("'inside;trigger'"))
        assertTrue(statements[1].endsWith("END"))
    }

    private fun freshSnapshot(): SchemaFingerprint.Snapshot {
        deleteDatabase()
        return LocalStore(context).use { store ->
            SchemaFingerprint.capture(store.writableDatabase)
        }
    }

    private fun createDatabaseFromFixture(fixture: Fixture) {
        val path = context.getDatabasePath(LocalStoreSchema.DB_NAME)
        val parent = requireNotNull(path.parentFile)
        check(parent.isDirectory || parent.mkdirs()) { "Unable to create database directory" }
        SQLiteDatabase.openDatabase(
            path.absolutePath,
            null,
            SQLiteDatabase.CREATE_IF_NECESSARY,
        ).use { database ->
            SqlSchemaFixtureLoader.load(database, GoldenFixtureResources.text(fixture.resource))
            assertEquals("${fixture.id} source user_version", fixture.schemaVersion, database.version)
        }
    }

    private fun fixtureRegistry(): List<Fixture> {
        return GoldenFixtureResources.text(FIXTURE_REGISTRY)
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith('#') }
            .map { line ->
                val fields = line.split('\t')
                check(fields.size == 8) { "Malformed schema fixture registry row: $line" }
                Fixture(
                    id = fields[0],
                    schemaVersion = fields[1].toInt(),
                    sourceRef = fields[2],
                    sourceCommit = fields[3],
                    resource = fields[4],
                    sha256 = fields[5],
                    expectedV33Sha256 = fields[6],
                    contentPolicy = fields[7],
                )
            }
            .toList()
    }

    private fun assertFixtureContainsNoUserRows(fixture: Fixture, sql: String) {
        val allowedSeedTables = setOf("stats_cache_state", "sqlite_sequence")
        val insertTable = Regex(
            "(?i)^INSERT\\s+INTO\\s+[`\"\\[]?([a-z0-9_]+)",
        )
        for (statement in SqlSchemaFixtureLoader.splitStatements(sql)) {
            val match = insertTable.find(statement.trim()) ?: continue
            assertTrue(
                "${fixture.id} contains a non-built-in data row for ${match.groupValues[1]}",
                match.groupValues[1].lowercase() in allowedSeedTables,
            )
        }
    }

    private fun assertRepresentativeConstraints(snapshot: SchemaFingerprint.Snapshot) {
        val schema = snapshot.canonical
        assertTrue(
            "single-row stats cache CHECK constraint must survive migration",
            schema.contains("check(id=1)"),
        )
        assertTrue("review idempotency token must remain unique", schema.contains("token text not null unique"))
        assertTrue(
            "study-item family identity must remain a composite primary key",
            schema.contains("primary key(kanji,answer_signature)"),
        )
    }

    private fun deleteDatabase() {
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        val database = context.getDatabasePath(LocalStoreSchema.DB_NAME)
        File(database.absolutePath + "-wal").delete()
        File(database.absolutePath + "-shm").delete()
    }

    private fun java.util.Properties.required(key: String): String {
        return requireNotNull(getProperty(key)) { "Missing schema golden property: $key" }
    }

    private data class Fixture(
        val id: String,
        val schemaVersion: Int,
        val sourceRef: String,
        val sourceCommit: String,
        val resource: String,
        val sha256: String,
        val expectedV33Sha256: String,
        val contentPolicy: String,
    )

    private companion object {
        const val RESOURCE_ROOT = "dev/bee/kanjianki/fixtures/goal165"
        const val CURRENT_RESOURCE_ROOT = "dev/bee/kanjianki/fixtures/goal175"
        const val CURRENT_SCHEMA_GOLDEN = "$CURRENT_RESOURCE_ROOT/schema-v34.properties"
        const val CURRENT_MIGRATION_DIGESTS =
            "$CURRENT_RESOURCE_ROOT/schema-v34-migration-digests.properties"
        const val FIXTURE_REGISTRY = "$RESOURCE_ROOT/schema-fixtures.tsv"
    }
}
