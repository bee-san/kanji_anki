package dev.bee.kanjianki.data.sql

import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SimilarKanjiIndex
import dev.bee.kanjianki.core.StudyQueueSeeder
import dev.bee.kanjianki.data.SyncPublicationCommand
import dev.bee.kanjianki.data.SyncQueuePlan
import dev.bee.kanjianki.data.SyncQueuePlanner
import dev.bee.kanjianki.data.SyncTimingSnapshot
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Goal 182 atomicity and rich-reload coverage for the shared sync publisher:
 * a planner failure rolls back the whole staged mirror and pending history,
 * and a repeated publish with a suspended import plus a similar index exercises
 * the previous-snapshot reload paths.
 */
class SqlSyncPublicationFaultInjectionTest {
    private val temporaryDirectories = ArrayList<Path>()

    @After
    fun tearDown() {
        temporaryDirectories.asReversed().forEach(::deleteRecursively)
    }

    @Test
    fun plannerFailureRollsBackStagedMirrorAndPendingHistory() = runBlocking {
        withRepository { repository ->
            // A non-storage planning error propagates after the outer transaction
            // rolls back, exactly like the legacy publish path.
            var propagated = false
            try {
                repository.publish(command { throw IllegalArgumentException("queue planning failed") })
            } catch (_: IllegalArgumentException) {
                propagated = true
            }
            assertTrue("a planner failure propagates", propagated)

            val state = repository.loadStoredState().valueOrNull()
            assertFalse("the staged mirror rolled back", state?.hasCollectionMirror ?: true)
            assertTrue("no study items survived", state?.studyItems?.isEmpty() ?: false)
            assertNull("no successful sync was recorded", state?.latestSuccessfulSyncAtMillis)
        }
    }

    @Test
    fun repeatedPublishReloadsSuspendedImportsAndSimilarState() = runBlocking {
        withRepository { repository ->
            // Seed the queue with both similar-pair endpoints so a similar-choice
            // state row is built, and keep an item so repair-evidence inputs run.
            val first = repository.publish(
                command { snapshot -> SyncQueuePlan(seedFromSnapshot(snapshot), plan()) },
            )
            assertTrue("first publish succeeds", first.isOk())

            val afterFirst = repository.loadStoredState().valueOrNull()
            assertEquals(
                "the suspended import round-trips through the reload path",
                listOf("痛"),
                afterFirst?.suspendedImports?.map { it.kanji },
            )
            assertEquals(FIXED_FINISHED_AT, afterFirst?.latestSuccessfulSyncAtMillis)
            assertTrue("the queue seeded a study item", afterFirst?.studyItems?.isNotEmpty() ?: false)

            // A second publish reads the previous suspended-import first-seen time,
            // previous dashboard rows, and previous similar-choice snapshots.
            val second = repository.publish(
                command { snapshot -> SyncQueuePlan(seedFromSnapshot(snapshot), plan()) },
            )
            assertTrue("second publish succeeds", second.isOk())
            assertEquals(
                listOf("痛"),
                repository.loadStoredState().valueOrNull()?.suspendedImports?.map { it.kanji },
            )
        }
    }

    private fun seedFromSnapshot(
        snapshot: dev.bee.kanjianki.data.SyncQueuePlanningSnapshot,
    ): List<RecordsStudyModels.StudyItem> =
        StudyQueueSeeder().seedQueue(
            allRows = snapshot.rows,
            eligibleRows = snapshot.activeRows,
            existing = snapshot.currentItems,
            settings = snapshot.settings,
            nowMillis = snapshot.nowMillis,
            startOfDayMillis = 0L,
            plan = null,
            ladder = snapshot.studyLadder,
        )

    private fun command(planner: SyncQueuePlanner): SyncPublicationCommand {
        val row = RecordsImportModels.DashboardRow(
            "裂", null, "split", "れつ", "裂", 90, "weak_support", "needs repair", 1, 0, 0,
            listOf(
                RecordsImportModels.Example(
                    RepositorySourceActive, 11L, 21L, "分裂", "ぶんれつ", "division", "細胞が分裂する。", false, 0,
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
        val neighborRow = RecordsImportModels.DashboardRow(
            "烈", null, "fierce", "れつ", "烈", 80, "weak_support", "needs repair", 1, 0, 0,
            listOf(
                RecordsImportModels.Example(
                    RepositorySourceActive, 12L, 22L, "熱烈", "ねつれつ", "ardent", "熱烈な歓迎。", false, 0,
                ),
            ),
        )
        val neighborNote = RecordsSyncModels.Note(
            22L,
            "Kiku",
            linkedMapOf(
                "Expression" to "熱烈",
                "ExpressionReading" to "ねつれつ",
                "MainDefinition" to "ardent",
                "Sentence" to "熱烈な歓迎。",
            ),
            emptyList(),
        )
        val card = RecordsSyncModels.Card(11L, 21L, 0, "Mining", 0, 2, 0, 30, 5, 0, false)
        val neighborCard = RecordsSyncModels.Card(12L, 22L, 0, "Mining", 0, 2, 0, 30, 5, 0, false)
        val suspendedImport = RecordsImportModels.SuspendedImport(
            "痛",
            500,
            true,
            600,
            listOf(
                RecordsImportModels.SuspendedSource(
                    "痛", 31L, 41L, "頭痛", "ずつう", "headache", "頭痛がひどい。",
                ),
            ),
        )
        return SyncPublicationCommand(
            snapshot = RecordsSyncModels.CollectionSnapshot(
                listOf(note, neighborNote),
                listOf(card, neighborCard),
            ),
            imports = listOf(suspendedImport),
            auditImports = listOf(suspendedImport),
            rows = listOf(row, neighborRow),
            settings = RecordsSyncModels.Settings.kikuDefaults(),
            timing = SyncTimingSnapshot(FIXED_STARTED_AT, FIXED_FINISHED_AT),
            removalMessage = null,
            similarIndex = SimilarKanjiIndex.parseTsv(StringReader("裂\t烈\tfixture\n")),
            dictionary = null,
            queuePlanner = planner,
        )
    }

    private fun plan(): RecordsSchedulerModels.AdaptiveLoadPlan =
        RecordsSchedulerModels.AdaptiveLoadPlan(true, 100, 0, 0, emptyList(), 0, true, "all")

    private fun withRepository(block: suspend (SqlSyncRepository) -> Unit) = runBlocking {
        val path = temporaryDatabase()
        val database = DedicatedWriterSqlDatabase(
            driver = BundledTestSqlDriver(path.toString()),
            configuration = SqlDatabaseConfiguration(busyTimeoutMillis = 1_000, writerThreadName = "sync-fault-test"),
        )
        try {
            SchemaManager(MigrationContext(clock = MigrationClock { FIXED_FINISHED_AT })).initialize(database)
            block(SqlSyncRepository(database))
        } finally {
            database.close()
        }
    }

    private fun temporaryDatabase(): Path {
        val directory = Files.createTempDirectory("kani-sync-fault-")
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
        const val FIXED_STARTED_AT = 1_770_090_000_000L
        const val FIXED_FINISHED_AT = 1_770_095_000_000L
        const val RepositorySourceActive = "active"
    }
}
