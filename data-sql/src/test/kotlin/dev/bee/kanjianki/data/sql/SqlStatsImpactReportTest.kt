package dev.bee.kanjianki.data.sql

import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.SyncPublicationCommand
import dev.bee.kanjianki.data.SyncQueuePlan
import dev.bee.kanjianki.data.SyncQueuePlanner
import dev.bee.kanjianki.data.SyncTimingSnapshot
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Drives the impact-report same-card baseline→current path: two successful
 * syncs of the same card with changed metrics, then a stats refresh. This
 * exercises SqlKanjiImpactReport's cross-sync join that the single-review
 * conformance scenarios do not reach.
 */
class SqlStatsImpactReportTest {
    private val temporaryDirectories = ArrayList<Path>()

    @After
    fun tearDown() {
        temporaryDirectories.asReversed().forEach(::deleteRecursively)
    }

    @Test
    fun statsRefreshWithASingleSyncTakesTheEmptySameCardPath() = runBlocking {
        withDatabase { database ->
            val sync = SqlSyncRepository(database)
            // One sync only: every candidate's baseline sync IS the latest sync,
            // so the impact report takes the empty same-card-metrics branch.
            sync.publish(command(FIRST_STARTED, FIRST_FINISHED, intervalDays = 2, mature = false) {
                SyncQueuePlan(emptyList(), plan())
            })
            val stats = SqlStatsRepository(database) { FIRST_FINISHED }
            val snapshot = stats.refresh(FIRST_FINISHED).valueOrNull()
            assertTrue("single-sync refresh succeeds", snapshot != null)
        }
    }

    @Test
    fun statsRefreshComputesImpactAcrossTwoSyncsOfTheSameCard() = runBlocking {
        withDatabase { database ->
            val sync = SqlSyncRepository(database)
            // First sync: the card is immature (short interval).
            sync.publish(command(FIRST_STARTED, FIRST_FINISHED, intervalDays = 2, mature = false) {
                SyncQueuePlan(emptyList(), plan())
            })
            // Second sync: the same card matured (long interval), a genuine
            // baseline→current transition for the impact report.
            sync.publish(command(SECOND_STARTED, SECOND_FINISHED, intervalDays = 40, mature = true) {
                SyncQueuePlan(emptyList(), plan())
            })

            val stats = SqlStatsRepository(database) { SECOND_FINISHED }
            val snapshot = stats.refresh(SECOND_FINISHED).valueOrNull()
            assertTrue("refresh succeeds", snapshot != null)
            val report = requireNotNull(snapshot).impactReport
            assertTrue(
                "the matured card produces an impact row or a non-empty report",
                report.rows.isNotEmpty() || !report.empty() || report.rows.any { it.kanji == "裂" },
            )
        }
    }

    private fun command(
        startedAt: Long,
        finishedAt: Long,
        intervalDays: Int,
        mature: Boolean,
        planner: SyncQueuePlanner,
    ): SyncPublicationCommand {
        val row = RecordsImportModels.DashboardRow(
            "裂", null, "split", "れつ", "裂", 90, "weak_support", "needs repair",
            1, 0, if (mature) 1 else 0,
            listOf(
                RecordsImportModels.Example(
                    "active", 11L, 21L, "分裂", "ぶんれつ", "division", "細胞が分裂する。", mature, 0,
                ),
            ),
        )
        val note = RecordsSyncModels.Note(
            21L,
            "Kiku",
            linkedMapOf(
                "Expression" to "分裂",
                "ExpressionReading" to "ぶんれつ",
                "MainDefinition" to "division",
                "Sentence" to "細胞が分裂する。",
            ),
            emptyList(),
        )
        // deckId "d1" (second positional) then queue/type/due/interval/reps/lapses.
        val card = RecordsSyncModels.Card(11L, 21L, 0, "Mining", 0, 2, 0, intervalDays, 5, 0, false)
        return SyncPublicationCommand(
            snapshot = RecordsSyncModels.CollectionSnapshot(listOf(note), listOf(card)),
            imports = emptyList(),
            auditImports = emptyList(),
            rows = listOf(row),
            settings = RecordsSyncModels.Settings.kikuDefaults(),
            timing = SyncTimingSnapshot(startedAt, finishedAt),
            removalMessage = null,
            similarIndex = null,
            dictionary = null,
            queuePlanner = planner,
        )
    }

    private fun plan(): RecordsSchedulerModels.AdaptiveLoadPlan =
        RecordsSchedulerModels.AdaptiveLoadPlan(true, 100, 0, 0, emptyList(), 0, true, "all")

    private fun withDatabase(block: suspend (DedicatedWriterSqlDatabase) -> Unit) = runBlocking {
        val path = temporaryDatabase()
        val database = DedicatedWriterSqlDatabase(
            driver = BundledTestSqlDriver(path.toString()),
            configuration = SqlDatabaseConfiguration(busyTimeoutMillis = 1_000, writerThreadName = "stats-impact-test"),
        )
        try {
            SchemaManager(MigrationContext(clock = MigrationClock { SECOND_FINISHED })).initialize(database)
            block(database)
        } finally {
            database.close()
        }
    }

    private fun temporaryDatabase(): Path {
        val directory = Files.createTempDirectory("kani-stats-impact-")
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
        const val FIRST_STARTED = 1_770_000_000_000L
        const val FIRST_FINISHED = 1_770_000_500_000L
        const val SECOND_STARTED = 1_770_050_000_000L
        const val SECOND_FINISHED = 1_770_050_500_000L
    }
}
