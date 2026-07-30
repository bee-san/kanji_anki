package dev.bee.kanjianki.data.sql

import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SchemaManagerBundledTest {
    private lateinit var originalLocale: Locale
    private val temporaryDirectories = ArrayList<Path>()

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
        temporaryDirectories.asReversed().forEach(::deleteRecursively)
    }

    @Test
    fun freshDatabaseUsesTheFrozenCanonicalSchema() = runBlocking {
        val path = temporaryDatabase("fresh")
        openDatabase(path).use { database ->
            val transition = manager().initialize(database)
            assertEquals(
                SchemaTransition(0, 34, SchemaTransitionKind.CREATED),
                transition,
            )
            assertEquals(34L, scalarLong(database, "PRAGMA user_version"))
            assertEquals(
                1L,
                scalarLong(
                    database,
                    "SELECT value FROM stats_cache_state WHERE key='stats_source_version'",
                ),
            )
            assertEquals(canonicalObjectNames("table"), objectNames(database, "table"))
            assertEquals(canonicalObjectNames("index"), objectNames(database, "index"))
        }
    }

    @Test
    fun historicalCorpusMigratesToTheSameSchemaAndRepresentativeRows() =
        runBlocking {
            val canonicalPath = temporaryDatabase("canonical")
            val canonical = openDatabase(canonicalPath)
            val canonicalSchema = try {
                manager().initialize(canonical)
                structuralSchema(canonical)
            } finally {
                canonical.close()
            }

            for (version in FIXTURE_VERSIONS) {
                val path = installFixture(version)
                openDatabase(path).use { database ->
                    val transition = manager().initialize(database)
                    assertEquals(
                        "v$version transition",
                        SchemaTransition(version, 34, SchemaTransitionKind.UPGRADED),
                        transition,
                    )
                    assertEquals("v$version user version", 34L, scalarLong(database, "PRAGMA user_version"))
                    assertEquals("v$version schema", canonicalSchema, structuralSchema(database))
                    assertMigratedRows(database, version)
                }
            }
        }

    @Test
    fun downgradePreservesRowsAndRecordsTheSourceVersion() = runBlocking {
        val path = temporaryDatabase("downgrade")
        openDatabase(path).use { database ->
            manager().initialize(database)
            database.write {
                execute("INSERT INTO settings(key, value, updated_at) VALUES ('probe', 'kept', 1)")
                pragmas.writeLong(SqlPragma.USER_VERSION, 35)
            }

            val transition = manager().initialize(database)

            assertEquals(
                SchemaTransition(35, 34, SchemaTransitionKind.DOWNGRADED),
                transition,
            )
            assertEquals("kept", scalarText(database, "SELECT value FROM settings WHERE key='probe'"))
            assertEquals(
                "35",
                scalarText(
                    database,
                    "SELECT value FROM settings WHERE key='downgraded_from_version'",
                ),
            )
            assertEquals(
                FIXED_NOW,
                scalarLong(
                    database,
                    "SELECT updated_at FROM settings WHERE key='downgraded_from_version'",
                ),
            )
        }
    }

    @Test
    fun v2BackfillRetainsSuspendedImportSourceAndUsesInjectedClock() = runBlocking {
        val path = installFixture(1)
        openDatabase(path).use { database ->
            database.write {
                execute(
                    """
                    INSERT INTO suspended_imports(
                        kanji, jiten_rank, rank_known, cutoff_used,
                        first_imported_at, last_seen_sync_id
                    ) VALUES ('休', 42, 1, 2000, 0, 7)
                    """.trimIndent(),
                )
                execute(
                    """
                    INSERT INTO suspended_sources(
                        kanji, card_id, note_id, expression, reading,
                        meaning, sentence, sync_id
                    ) VALUES ('休', 9001, 9002, '休む', 'やすむ', 'rest', '', 7)
                    """.trimIndent(),
                )
            }

            manager().initialize(database)

            assertEquals(
                FIXED_NOW,
                scalarLong(
                    database,
                    """
                    SELECT occurred_at
                    FROM kanji_timeline_events
                    WHERE dedupe_key='suspended_imported:休'
                    """.trimIndent(),
                ),
            )
            assertEquals(
                "Imported from suspended Anki",
                scalarText(
                    database,
                    """
                    SELECT title
                    FROM kanji_timeline_events
                    WHERE dedupe_key='suspended_imported:休'
                    """.trimIndent(),
                ),
            )
            assertEquals(
                "休む",
                scalarText(
                    database,
                    """
                    SELECT source_expression
                    FROM kanji_timeline_events
                    WHERE dedupe_key='suspended_imported:休'
                    """.trimIndent(),
                ),
            )
            assertEquals(
                "やすむ",
                scalarText(
                    database,
                    """
                    SELECT source_reading
                    FROM kanji_timeline_events
                    WHERE dedupe_key='suspended_imported:休'
                    """.trimIndent(),
                ),
            )
            assertEquals(
                7L,
                scalarLong(
                    database,
                    """
                    SELECT sync_id
                    FROM kanji_timeline_events
                    WHERE dedupe_key='suspended_imported:休'
                    """.trimIndent(),
                ),
            )
        }
    }

    @Test
    fun v11BackfillBuildsDeterministicSimilarKanjiChoices() = runBlocking {
        val path = installFixture(1)
        openDatabase(path).use { database ->
            database.write {
                SchemaMigrations.upgrade(
                    session = this,
                    oldVersion = 1,
                    targetVersion = 10,
                    context = migrationContext(),
                )
                pragmas.writeLong(SqlPragma.USER_VERSION, 10)
                execute(
                    """
                    INSERT OR REPLACE INTO kanji_inventory(
                        kanji, primary_meaning, readings, browser_search,
                        search_text, source_count, example_count,
                        first_seen_at, last_seen_at
                    ) VALUES
                        ('休', 'rest', 'やす', '休', '休 rest やす', 1, 1, 10, 20),
                        ('体', 'body', 'からだ', '体', '体 body からだ', 1, 1, 10, 20)
                    """.trimIndent(),
                )
                execute(
                    """
                    INSERT INTO similar_kanji_pairs(
                        kanji_a, kanji_b, source, first_seen_at, last_seen_at
                    ) VALUES ('休', '体', 'fixture', 10, 20)
                    """.trimIndent(),
                )
            }
            database.write {
                SchemaMigrations.upgrade(
                    session = this,
                    oldVersion = 10,
                    targetVersion = 11,
                    context = migrationContext(),
                )
                pragmas.writeLong(SqlPragma.USER_VERSION, 11)
            }

            assertEquals(11L, scalarLong(database, "PRAGMA user_version"))
            assertEquals(
                2L,
                scalarLong(
                    database,
                    """
                    SELECT COUNT(*)
                    FROM similar_kanji_choice_state
                    WHERE target_kanji IN ('休', '体')
                    """.trimIndent(),
                ),
            )
            for (target in listOf("休", "体")) {
                assertEquals(
                    "休\t体",
                    scalarText(
                        database,
                        """
                        SELECT choice_signature
                        FROM similar_kanji_choice_state
                        WHERE target_kanji='$target'
                        """.trimIndent(),
                    ),
                )
                assertEquals(
                    "休\t体",
                    scalarText(
                        database,
                        """
                        SELECT choices
                        FROM similar_kanji_choice_state
                        WHERE target_kanji='$target'
                        """.trimIndent(),
                    ),
                )
                assertEquals(
                    FIXED_NOW,
                    scalarLong(
                        database,
                        """
                        SELECT first_seen_at
                        FROM similar_kanji_choice_state
                        WHERE target_kanji='$target'
                        """.trimIndent(),
                    ),
                )
                assertEquals(
                    FIXED_NOW,
                    scalarLong(
                        database,
                        """
                        SELECT last_seen_at
                        FROM similar_kanji_choice_state
                        WHERE target_kanji='$target'
                        """.trimIndent(),
                    ),
                )
            }
            assertEquals(
                "rest",
                scalarText(
                    database,
                    """
                    SELECT primary_meaning
                    FROM similar_kanji_choice_state
                    WHERE target_kanji='休'
                    """.trimIndent(),
                ),
            )
            assertEquals(
                "body",
                scalarText(
                    database,
                    """
                    SELECT primary_meaning
                    FROM similar_kanji_choice_state
                    WHERE target_kanji='体'
                    """.trimIndent(),
                ),
            )
        }
    }

    private suspend fun assertMigratedRows(
        database: SqlDatabase,
        sourceVersion: Int,
    ) {
        val label = "goal178-v$sourceVersion"
        assertEquals(label, scalarText(database, settingSql("goal178.fixture")))
        assertEquals("eligible", scalarText(database, settingSql(ANDROID_LEGACY_KEY)))
        assertEquals(1L, scalarLong(database, "SELECT value FROM stats_cache_state WHERE key='stats_source_version'"))
        assertEquals(label, scalarText(database, "SELECT meaning FROM source_notes"))
        assertEquals(23L, scalarLong(database, "SELECT interval_days FROM source_cards"))
        if (sourceVersion == 1) {
            assertNull(scalarNullableDouble(database, "SELECT fsrs_stability FROM source_cards"))
        } else {
            assertEquals(12.5, scalarNullableDouble(database, "SELECT fsrs_stability FROM source_cards")!!, 0.0)
        }
        assertEquals("review", scalarText(database, "SELECT state FROM study_items WHERE kanji='F'"))
        assertEquals("review", scalarText(database, "SELECT phase FROM study_items WHERE kanji='F'"))
        assertEquals("kanji_meaning", scalarText(database, "SELECT rung FROM study_items WHERE kanji='F'"))
        assertEquals(0L, scalarLong(database, "SELECT scheduler_revision FROM study_items WHERE kanji='F'"))
        assertEquals(1L, scalarLong(database, "SELECT routing_version FROM study_items WHERE kanji='F'"))
        assertEquals(label, scalarText(database, "SELECT token FROM review_log"))
        assertEquals("", scalarText(database, "SELECT core_skill FROM review_log"))

        for (table in COMPATIBILITY_TABLES) {
            assertTrue("$table exists after v$sourceVersion migration", tableExists(database, table))
        }
        assertEquals(
            if (sourceVersion >= 32) 1L else 0L,
            scalarLong(database, "SELECT COUNT(*) FROM kanji_mnemonic_notes"),
        )
        assertEquals(
            if (sourceVersion >= 33) 1L else 0L,
            scalarLong(database, "SELECT COUNT(*) FROM manual_kanji_sources"),
        )
        assertEquals(
            if (sourceVersion >= 33) 1L else 0L,
            scalarLong(database, "SELECT COUNT(*) FROM missing_kanji_exports"),
        )

        if (sourceVersion == 1) {
            assertEquals(2L, scalarLong(database, "SELECT source_count FROM kanji_inventory WHERE kanji='F'"))
            assertEquals(1L, scalarLong(database, "SELECT example_count FROM kanji_inventory WHERE kanji='F'"))
            assertEquals(3L, scalarLong(database, "SELECT COUNT(*) FROM kanji_timeline_events WHERE kanji='F'"))
            assertEquals(
                "Review passed",
                scalarText(
                    database,
                    "SELECT title FROM kanji_timeline_events WHERE dedupe_key='review:$label'",
                ),
            )
            assertEquals(1L, scalarLong(database, "SELECT COUNT(*) FROM sync_card_snapshots"))
            assertEquals("Goal178", scalarText(database, "SELECT deck_id FROM sync_card_snapshots"))
            assertEquals(0L, scalarLong(database, "SELECT model_id FROM sync_card_snapshots"))
            assertEquals(1L, scalarLong(database, "SELECT mature FROM sync_card_snapshots"))
            assertEquals(1L, scalarLong(database, "SELECT COUNT(*) FROM sync_note_snapshots"))
            assertEquals("Goal178", scalarText(database, "SELECT deck_names FROM sync_note_snapshots"))
            assertEquals("", scalarText(database, "SELECT extracted_kanji FROM sync_note_snapshots"))
            assertEquals(1L, scalarLong(database, "SELECT COUNT(*) FROM sync_kanji_snapshots"))
            assertEquals(19L, scalarLong(database, "SELECT weakness_score FROM sync_kanji_snapshots WHERE kanji='F'"))
            assertEquals(0.0, scalarDouble(database, "SELECT average_interval_days FROM sync_kanji_snapshots"), 0.0)
        }
    }

    private fun manager(): SchemaManager =
        SchemaManager(
            migrationContext(),
        )

    private fun migrationContext(): MigrationContext =
        MigrationContext(clock = MigrationClock { FIXED_NOW })

    private fun openDatabase(path: Path): DedicatedWriterSqlDatabase =
        DedicatedWriterSqlDatabase(
            driver = BundledTestSqlDriver(path.toString()),
            configuration = SqlDatabaseConfiguration(
                busyTimeoutMillis = 1_000,
                writerThreadName = "schema-manager-test",
            ),
        )

    private fun installFixture(version: Int): Path {
        val path = temporaryDatabase("v$version")
        GZIPInputStream(
            Files.newInputStream(resourceRoot().resolve("historical-v$version.db.gz")),
        ).use { input ->
            Files.newOutputStream(path).use(input::copyTo)
        }
        return path
    }

    private fun temporaryDatabase(label: String): Path {
        val directory = Files.createTempDirectory("kani-schema-$label-")
        temporaryDirectories.add(directory)
        return directory.resolve("kani.db")
    }

    private fun resourceRoot(): Path =
        Path.of(requireNotNull(System.getProperty("kani.goal178.resources")))

    private fun canonicalObjectNames(type: String): List<String> {
        val expected = Regex(
            """^CREATE (?:UNIQUE )?${type.uppercase()} ([^ ]+) """,
            RegexOption.MULTILINE,
        )
        return expected.findAll(
            resourceRoot().resolve("schema-v34.sql").toFile().readText(),
        ).map { it.groupValues[1] }.sorted().toList()
    }

    private suspend fun structuralSchema(database: SqlDatabase): List<String> =
        database.readSnapshot {
            val details = ArrayList<String>()
            val tables = ArrayList<String>()
            forEachMigrationRow(
                """
                SELECT type, name, tbl_name, sql
                FROM sqlite_schema
                WHERE name NOT LIKE 'sqlite_%' AND sql IS NOT NULL
                ORDER BY type, name
                """.trimIndent(),
            ) { row ->
                val type = row.text("type")
                val name = row.text("name")
                if (type == "table") {
                    tables += name
                } else {
                    details +=
                        "schema|$type|$name|${row.text("tbl_name")}|${normalizeSql(row.text("sql"))}"
                }
            }
            for (table in tables) {
                appendTableStructure(details, table)
            }
            details.sorted()
        }

    private fun SqlSession.appendTableStructure(
        details: MutableList<String>,
        table: String,
    ) {
        val quotedTable = quoteSqlString(table)
        forEachMigrationRow(
            """
            SELECT
                name,
                UPPER(type) AS declared_type,
                "notnull" AS not_null,
                COALESCE(dflt_value, '<none>') AS default_value,
                pk,
                COALESCE(hidden, 0) AS hidden
            FROM pragma_table_xinfo($quotedTable)
            """.trimIndent(),
        ) { row ->
            details += buildString {
                append("column|")
                append(table)
                append('|')
                append(row.text("name"))
                append('|')
                append(row.text("declared_type"))
                append("|not_null=")
                append(row.int("not_null"))
                append("|default=")
                append(normalizeSql(row.text("default_value")))
                append("|pk=")
                append(row.int("pk"))
                append("|hidden=")
                append(row.int("hidden"))
            }
        }

        val indexes = ArrayList<StructuralIndex>()
        forEachMigrationRow("PRAGMA index_list($quotedTable)") { row ->
            val name = row.text("name")
            val columns = indexColumns(name)
            val origin = row.text("origin").ifEmpty { "c" }
            val stableName = if (name.startsWith("sqlite_autoindex_")) {
                "auto:$origin:" +
                    columns.filter(StructuralIndexColumn::key)
                        .joinToString(",") { it.name.ifEmpty { "<rowid>" } }
            } else {
                name
            }
            indexes += StructuralIndex(
                stableName = stableName,
                unique = row.int("unique"),
                origin = origin,
                partial = row.int("partial"),
                columns = columns,
            )
        }
        for (index in indexes.sortedBy(StructuralIndex::stableName)) {
            details +=
                "index|$table|${index.stableName}|unique=${index.unique}|" +
                "origin=${index.origin}|partial=${index.partial}"
            for (column in index.columns) {
                details +=
                    "index_column|$table|${index.stableName}|${column.sequence}|" +
                    "name=${column.name.ifEmpty { "<rowid>" }}|" +
                    "desc=${column.descending}|collation=${column.collation}|" +
                    "key=${if (column.key) 1 else 0}"
            }
        }

        forEachMigrationRow("PRAGMA foreign_key_list($quotedTable)") { row ->
            details += buildString {
                append("foreign_key|")
                append(table)
                append('|')
                append(row.int("id"))
                append('|')
                append(row.int("seq"))
                append("|table=")
                append(row.text("table"))
                append("|from=")
                append(row.text("from"))
                append("|to=")
                append(row.text("to").ifEmpty { "<implicit>" })
                append("|on_update=")
                append(row.text("on_update"))
                append("|on_delete=")
                append(row.text("on_delete"))
                append("|match=")
                append(row.text("match"))
            }
        }
    }

    private fun SqlSession.indexColumns(index: String): List<StructuralIndexColumn> {
        val columns = ArrayList<StructuralIndexColumn>()
        forEachMigrationRow("PRAGMA index_xinfo(${quoteSqlString(index)})") { row ->
            columns += StructuralIndexColumn(
                sequence = row.int("seqno"),
                name = row.text("name"),
                descending = row.int("desc"),
                collation = row.text("coll").ifEmpty { "BINARY" },
                key = row.nullableInt("key")?.let { it != 0 } ?: true,
            )
        }
        return columns.sortedBy(StructuralIndexColumn::sequence)
    }

    private suspend fun objectNames(
        database: SqlDatabase,
        type: String,
    ): List<String> =
        database.readSnapshot {
            val names = ArrayList<String>()
            forEachMigrationRow(
                """
                SELECT name
                FROM sqlite_schema
                WHERE type = ? AND name NOT LIKE 'sqlite_%'
                ORDER BY name
                """.trimIndent(),
                bind = { bindText(1, type) },
            ) { names += it.text("name") }
            names
        }

    private suspend fun tableExists(database: SqlDatabase, table: String): Boolean =
        database.readSnapshot {
            firstMigrationRow(
                "SELECT 1 AS present FROM sqlite_schema WHERE type='table' AND name=?",
            ) {
                bindText(1, table)
            } != null
        }

    private suspend fun scalarLong(database: SqlDatabase, sql: String): Long =
        database.readSnapshot {
            requireNotNull(firstMigrationRow(sql)).longAtFirstColumn()
        }

    private suspend fun scalarDouble(database: SqlDatabase, sql: String): Double =
        database.readSnapshot {
            requireNotNull(firstMigrationRow(sql)).doubleAtFirstColumn()
        }

    private suspend fun scalarNullableDouble(database: SqlDatabase, sql: String): Double? =
        database.readSnapshot {
            requireNotNull(firstMigrationRow(sql)).nullableDoubleAtFirstColumn()
        }

    private suspend fun scalarText(database: SqlDatabase, sql: String): String =
        database.readSnapshot {
            requireNotNull(firstMigrationRow(sql)).textAtFirstColumn()
        }

    private fun settingSql(key: String): String =
        "SELECT value FROM settings WHERE key='$key'"

    private fun normalizeSql(sql: String): String =
        sql.trim()
            .replace(Regex("""\s+"""), " ")
            .replace(Regex("""\bIF\s+NOT\s+EXISTS\s+""", RegexOption.IGNORE_CASE), "")
            .lowercase()

    private fun quoteSqlString(value: String): String =
        "'" + value.replace("'", "''") + "'"

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private companion object {
        const val FIXED_NOW = 179_000L
        const val ANDROID_LEGACY_KEY =
            "collection_source_binding.android_legacy_migration"
        val FIXTURE_VERSIONS = listOf(1, 30, 31, 32, 33)
        val COMPATIBILITY_TABLES = listOf(
            "learning_repeats",
            "similar_kanji_choice_state",
            "similar_kanji_repair_queue",
            "similar_kanji_review_log",
            "kanji_reading_usage",
            "kanji_reading_pool",
        )
    }

    private data class StructuralIndex(
        val stableName: String,
        val unique: Int,
        val origin: String,
        val partial: Int,
        val columns: List<StructuralIndexColumn>,
    )

    private data class StructuralIndexColumn(
        val sequence: Int,
        val name: String,
        val descending: Int,
        val collation: String,
        val key: Boolean,
    )
}
