package dev.bee.kanjianki.data.sql

import dev.bee.kanjianki.core.StringListJsonCodec
import dev.bee.kanjianki.data.HomeRepository
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Exercises the read paths the shared conformance fixture does not seed:
 * mapping a persisted `study_items` row (SqlStudyItemMapper), the study-streak
 * aggregate (StudyDay), and an admitted manual dictionary source (ManualSource).
 */
class SqlHomeReadPathCoverageTest {
    private val temporaryDirectories = ArrayList<Path>()

    @After
    fun tearDown() {
        temporaryDirectories.asReversed().forEach(::deleteRecursively)
    }

    @Test
    fun loadHomeMapsStudyItemsStreakAndManualSources() = runBlocking {
        withDatabase { database ->
            database.write {
                insertDashboardRow("裂", 90)
                insertInventory("裂")
                insertStudyItem("裂")
                insertReviewDay("裂", REVIEW_DAY_START, REVIEWED_AT)
                insertManualSource("裂")
            }

            val home: HomeRepository = SqlHomeRepository(database)
            val snapshot = home.loadHome(NOW).valueOrNull()
            assertTrue("loadHome must succeed", snapshot != null)
            val loaded = requireNotNull(snapshot)

            assertEquals(listOf("裂"), loaded.activeRows.map { it.kanji })
            assertEquals(listOf("裂"), loaded.studyItems.map { it.kanji })
            assertEquals("review", loaded.studyItems.first().state)
            assertTrue("the streak aggregate is computed", loaded.studyStreak.bestDays >= 1)
        }
    }

    private fun SqlTransactionScope.insertDashboardRow(kanji: String, weakness: Int) {
        executeBound(
            """
            INSERT INTO dashboard_rows(
                kanji, jiten_rank, primary_meaning, reading, browser_search,
                weakness_score, reason_code, reason_text,
                active_example_count, suspended_example_count, mature_support_count, rebuilt_at
            ) VALUES (?, NULL, 'split', 'れつ', ?, ?, '', '', 0, 0, 0, ?)
            """.trimIndent(),
        ) {
            bindText(1, kanji)
            bindText(2, kanji)
            bindLong(3, weakness.toLong())
            bindLong(4, NOW)
        }
    }

    private fun SqlTransactionScope.insertInventory(kanji: String) {
        executeBound(
            """
            INSERT INTO kanji_inventory(
                kanji, primary_meaning, readings, browser_search, search_text,
                source_count, example_count, first_seen_at, last_seen_at
            ) VALUES (?, 'split', 'れつ', ?, ?, 1, 0, ?, ?)
            """.trimIndent(),
        ) {
            bindText(1, kanji)
            bindText(2, kanji)
            bindText(3, kanji)
            bindLong(4, NOW)
            bindLong(5, NOW)
        }
    }

    private fun SqlTransactionScope.insertStudyItem(kanji: String) {
        executeBound(
            """
            INSERT INTO study_items(
                kanji, state, due_at, stability, difficulty, total_reviews, lapses,
                learning_step, writing_level, recognition_stage,
                consecutive_failed_recognition_days, last_failed_recognition_day,
                writing_remediation_pending, suppressed_by_task_type, suppressed_at,
                mature_interval_days, answer_signature, rung, phase,
                real_pass_streak, real_again_streak, last_real_review_due_at,
                scheduler_revision, routing_version, adaptive_route_state_json,
                active_token, created_at
            ) VALUES (?, 'review', ?, 5.0, 5.0, 3, 0, 0, 0, 0, 0, 0, 0, '', 0, 12, '',
                      'kanji_meaning', 'review', 2, 0, ?, 1, 1, '', NULL, ?)
            """.trimIndent(),
        ) {
            bindText(1, kanji)
            bindLong(2, NOW)
            bindLong(3, NOW)
            bindLong(4, NOW)
        }
    }

    private fun SqlTransactionScope.insertReviewDay(kanji: String, dayStart: Long, reviewedAt: Long) {
        executeBound(
            """
            INSERT INTO review_log(
                kanji, token, rating, writing_required, writing_passed, manual_override,
                reviewed_at, review_day_start
            ) VALUES (?, ?, 'good', 0, 0, 0, ?, ?)
            """.trimIndent(),
        ) {
            bindText(1, kanji)
            bindText(2, "token-$kanji")
            bindLong(3, reviewedAt)
            bindLong(4, dayStart)
        }
    }

    private fun SqlTransactionScope.insertManualSource(kanji: String) {
        val json = StringListJsonCodec.encode(listOf("split"))
        executeBound(
            """
            INSERT INTO manual_kanji_sources(
                literal, source_type, jiten_rank, meanings_json,
                on_readings_json, kun_readings_json, added_at, updated_at, active
            ) VALUES (?, 'dictionary', 100, ?, ?, ?, ?, ?, 1)
            """.trimIndent(),
        ) {
            bindText(1, kanji)
            bindText(2, json)
            bindText(3, StringListJsonCodec.encode(emptyList()))
            bindText(4, StringListJsonCodec.encode(emptyList()))
            bindLong(5, NOW)
            bindLong(6, NOW)
        }
    }

    private suspend fun <T> withDatabase(block: suspend (DedicatedWriterSqlDatabase) -> T): T {
        val path = temporaryDatabase()
        val database = DedicatedWriterSqlDatabase(
            driver = BundledTestSqlDriver(path.toString()),
            configuration = SqlDatabaseConfiguration(
                busyTimeoutMillis = 1_000,
                writerThreadName = "sql-read-path-test",
            ),
        )
        return try {
            runBlocking {
                SchemaManager(MigrationContext(clock = MigrationClock { NOW })).initialize(database)
            }
            block(database)
        } finally {
            database.close()
        }
    }

    private fun temporaryDatabase(): Path {
        val directory = Files.createTempDirectory("kani-read-path-")
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
        const val NOW = 1_770_100_000_000L
        const val REVIEW_DAY_START = 1_770_000_000_000L
        const val REVIEWED_AT = 1_770_090_000_000L
    }
}
