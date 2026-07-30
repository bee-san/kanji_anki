package dev.bee.kanjianki.data.sql

import dev.bee.kanjianki.core.KanjiInventoryBuilder
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.data.HomeRepository
import dev.bee.kanjianki.data.SettingsRepository
import dev.bee.kanjianki.data.conformance.RepositoryConformanceFixture
import dev.bee.kanjianki.data.conformance.RepositoryConformanceHost
import dev.bee.kanjianki.data.conformance.RepositoryConformanceSuite
import java.nio.file.Files
import java.nio.file.Path
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Drives the shared repository conformance suite against the driver-neutral
 * `:data-sql` implementation, backed by the bundled SQLite driver.
 */
class SqlRepositoryConformanceTest {
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
    fun sqlRepositoriesPassSharedContract() = runBlocking {
        val host = SqlConformanceHost()
        try {
            RepositoryConformanceSuite(host).runAll()
        } finally {
            host.close()
        }
    }

    private inner class SqlConformanceHost : RepositoryConformanceHost {
        private var database: DedicatedWriterSqlDatabase = freshDatabase()
        private val invalidation = SqlProjectionInvalidation()

        override val home: HomeRepository
            get() = SqlHomeRepository(database, invalidation)

        override val settings: SettingsRepository
            get() = SqlSettingsRepository(database) { FIXED_CLOCK }

        override suspend fun reset() {
            database.close()
            database = freshDatabase()
        }

        override suspend fun putRawSetting(key: String, value: String) {
            database.write {
                executeBound(
                    """
                    INSERT INTO settings(key, value, updated_at)
                    VALUES (?, ?, ?)
                    ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at
                    """.trimIndent(),
                ) {
                    bindText(1, key)
                    bindText(2, value)
                    bindLong(3, FIXED_CLOCK)
                }
            }
        }

        override suspend fun rawSetting(key: String): String? =
            database.readSnapshot {
                queryOneOrNull(
                    "SELECT value FROM settings WHERE key = ? LIMIT 1",
                    bind = { bindText(1, key) },
                ) { row -> row.text(0) }
            }

        override suspend fun statsSourceVersion(): Long =
            database.readSnapshot {
                queryOneOrNull(
                    "SELECT value FROM stats_cache_state WHERE key = ? LIMIT 1",
                    bind = { bindText(1, "stats_source_version") },
                ) { row -> row.long(0) } ?: 0L
            }

        override suspend fun seedFixture(fixture: RepositoryConformanceFixture) {
            database.write {
                fixture.kanji.forEach { entry -> seedEntry(entry) }
                fixture.similarPairs.forEach { pair -> seedSimilarPair(pair) }
                seedSuccessfulSync(fixture.syncFinishedAtMillis)
            }
            seedInventory(fixture)
        }

        private fun SqlTransactionScope.seedEntry(entry: RepositoryConformanceFixture.Entry) {
            executeBound(
                """
                INSERT INTO dashboard_rows(
                    kanji, jiten_rank, primary_meaning, reading, browser_search,
                    weakness_score, reason_code, reason_text,
                    active_example_count, suspended_example_count, mature_support_count, rebuilt_at
                ) VALUES (?, NULL, ?, ?, ?, ?, '', '', ?, ?, ?, ?)
                """.trimIndent(),
            ) {
                bindText(1, entry.kanji)
                bindText(2, entry.primaryMeaning)
                bindText(3, entry.reading)
                bindText(4, entry.kanji)
                bindLong(5, entry.weaknessScore.toLong())
                bindLong(6, entry.activeExampleCount.toLong())
                bindLong(7, entry.suspendedExampleCount.toLong())
                bindLong(8, entry.matureSupportCount.toLong())
                bindLong(9, FIXED_CLOCK)
            }
            entry.examples.forEach { example ->
                executeBound(
                    """
                    INSERT INTO kanji_examples(
                        kanji, source_type, card_id, note_id, expression, reading, meaning,
                        sentence, mature, lapses, interval_days, reps,
                        fsrs_stability, fsrs_difficulty, fsrs_retrievability
                    ) VALUES (?, ?, 0, 0, ?, ?, ?, ?, ?, 0, 0, 0, NULL, NULL, NULL)
                    """.trimIndent(),
                ) {
                    bindText(1, entry.kanji)
                    bindText(2, example.sourceType)
                    bindText(3, example.expression)
                    bindText(4, example.reading)
                    bindText(5, example.meaning)
                    bindText(6, example.sentence)
                    bindLong(7, if (example.mature) 1L else 0L)
                }
            }
        }

        private fun SqlTransactionScope.seedSimilarPair(
            pair: RepositoryConformanceFixture.SimilarPair,
        ) {
            executeBound(
                """
                INSERT INTO similar_kanji_pairs(kanji_a, kanji_b, source, first_seen_at, last_seen_at)
                VALUES (?, ?, ?, ?, ?)
                """.trimIndent(),
            ) {
                bindText(1, pair.kanjiA)
                bindText(2, pair.kanjiB)
                bindText(3, pair.source)
                bindLong(4, FIXED_CLOCK)
                bindLong(5, FIXED_CLOCK)
            }
        }

        private fun SqlTransactionScope.seedSuccessfulSync(finishedAtMillis: Long) {
            executeBound(
                """
                INSERT INTO sync_runs(
                    started_at, finished_at, status,
                    active_notes_count, active_cards_count, suspended_cards_archived_count,
                    suspended_kanji_imported_count, deleted_notes_count, deleted_cards_count,
                    error_code, error_message, removal_message
                ) VALUES (?, ?, 'success', 0, 0, 0, 0, 0, 0, '', '', '')
                """.trimIndent(),
            ) {
                bindLong(1, finishedAtMillis)
                bindLong(2, finishedAtMillis)
            }
        }

        private suspend fun seedInventory(fixture: RepositoryConformanceFixture) {
            // Derive kanji_inventory (search_text included) the same way the app
            // host does: run KanjiInventoryBuilder over the seeded dashboard rows.
            val builder = KanjiInventoryBuilder(FIXED_CLOCK, null)
            fixture.kanji.forEach { entry -> builder.addDashboardRow(dashboardRow(entry)) }
            database.write {
                builder.build(emptyMap()).forEach { item ->
                    executeBound(
                        """
                        INSERT INTO kanji_inventory(
                            kanji, primary_meaning, readings, browser_search, search_text,
                            source_count, example_count, first_seen_at, last_seen_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT(kanji) DO UPDATE SET
                            primary_meaning = excluded.primary_meaning,
                            readings = excluded.readings,
                            browser_search = excluded.browser_search,
                            search_text = excluded.search_text,
                            source_count = excluded.source_count,
                            example_count = excluded.example_count,
                            last_seen_at = excluded.last_seen_at
                        """.trimIndent(),
                    ) {
                        bindText(1, item.kanji())
                        bindText(2, item.primaryMeaning())
                        bindText(3, item.readings())
                        bindText(4, item.browserSearch())
                        bindText(5, item.searchText())
                        bindLong(6, item.sourceCount().toLong())
                        bindLong(7, item.exampleCount().toLong())
                        bindLong(8, item.firstSeenAtMillis())
                        bindLong(9, item.lastSeenAtMillis())
                    }
                }
            }
        }

        private fun dashboardRow(
            entry: RepositoryConformanceFixture.Entry,
        ): RecordsImportModels.DashboardRow =
            RecordsImportModels.DashboardRow(
                entry.kanji,
                null,
                entry.primaryMeaning,
                entry.reading,
                entry.kanji,
                entry.weaknessScore,
                "",
                "",
                entry.activeExampleCount,
                entry.suspendedExampleCount,
                entry.matureSupportCount,
                entry.examples.map { example ->
                    RecordsImportModels.Example(
                        example.sourceType,
                        0L,
                        0L,
                        example.expression,
                        example.reading,
                        example.meaning,
                        example.sentence,
                        example.mature,
                        0,
                    )
                },
            )

        fun close() {
            database.close()
        }

        private fun freshDatabase(): DedicatedWriterSqlDatabase {
            val path = temporaryDatabase()
            val database = DedicatedWriterSqlDatabase(
                driver = BundledTestSqlDriver(path.toString()),
                configuration = SqlDatabaseConfiguration(
                    busyTimeoutMillis = 1_000,
                    writerThreadName = "sql-conformance-test",
                ),
            )
            runBlocking {
                SchemaManager(MigrationContext(clock = MigrationClock { FIXED_CLOCK }))
                    .initialize(database)
            }
            return database
        }
    }

    private fun temporaryDatabase(): Path {
        val directory = Files.createTempDirectory("kani-repo-conformance-")
        temporaryDirectories.add(directory)
        return directory.resolve("kani.db")
    }

    private fun deleteRecursively(path: Path) {
        if (!Files.exists(path)) return
        Files.walk(path).use { paths ->
            paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
        }
    }

    private companion object {
        const val FIXED_CLOCK = 1_770_050_000_000L
    }
}
