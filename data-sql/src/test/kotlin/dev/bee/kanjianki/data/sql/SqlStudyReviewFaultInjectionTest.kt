package dev.bee.kanjianki.data.sql

import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.data.ReviewCommitCommand
import dev.bee.kanjianki.data.StudyQueueWriteCommand
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Goal 181 atomicity: the token-first review commit persists the review_log
 * row and the study_items revision advance in one transaction. If the item
 * UPDATE fails after the review insert, both must roll back — the token must
 * not appear consumed, and the item revision must be unchanged.
 */
class SqlStudyReviewFaultInjectionTest {
    private val temporaryDirectories = ArrayList<Path>()

    @After
    fun tearDown() {
        temporaryDirectories.asReversed().forEach(::deleteRecursively)
    }

    @Test
    fun aFailureBetweenReviewInsertAndItemUpdateRollsBackBoth() = runBlocking {
        val path = temporaryDatabase()
        // Fail on the first UPDATE of study_items inside the write transaction,
        // which is the CAS advance that follows the review_log insert.
        val driver = FaultDriver(path.toString()) { sql -> sql.trimStart().startsWith("UPDATE study_items") }
        val database = DedicatedWriterSqlDatabase(
            driver = driver,
            configuration = SqlDatabaseConfiguration(busyTimeoutMillis = 1_000, writerThreadName = "study-fault-test"),
        )
        try {
            SchemaManager(MigrationContext(clock = MigrationClock { NOW })).initialize(database)
            val repository = SqlStudyRepository(database)
            val before = studyItem("裂")
            repository.replaceQueue(StudyQueueWriteCommand(listOf(before)))

            // Arm the fault only for the commit under test.
            driver.armed = true
            val result = repository.commitReview(reviewCommit(before))
            driver.armed = false

            // The commit surfaces as a permanent error, not APPLIED.
            assertFalse("a rolled-back commit is not applied", result.isOk())

            // The review_log row was rolled back with the item update.
            assertFalse(
                "the token must not appear consumed after a rolled-back commit",
                repository.reviewTokenStatus(
                    dev.bee.kanjianki.data.ReviewTokenQuery("tok-fault", "裂", "kanji_meaning", ""),
                ).valueOrNull()?.consumed ?: true,
            )
            assertEquals(
                "the study item revision is unchanged after rollback",
                0L,
                repository.loadItems(listOf("裂")).valueOrNull()?.first()?.schedulerRevision,
            )
        } finally {
            database.close()
        }
    }

    private fun reviewCommit(before: RecordsStudyModels.StudyItem): ReviewCommitCommand {
        val request = RecordsSchedulerModels.ReviewRequest.fromFields(
            RecordsSchedulerModels.ReviewRequest.Fields(
                kanji = before.kanji,
                token = "tok-fault",
                rating = "good",
                writingRequired = false,
                writingPassed = false,
                writingClean = false,
                manualOverride = false,
                hintsUsed = 0,
                taskType = "kanji_meaning",
                answerSignature = before.answerSignature,
                prompt = "",
            ),
        )
        return ReviewCommitCommand(
            afterReview = before,
            request = request,
            appliedRating = "good",
            reviewedAtMillis = NOW,
            beforeReview = before,
        )
    }

    private fun studyItem(kanji: String): RecordsStudyModels.StudyItem =
        RecordsStudyModels.StudyItem(
            kanji,
            "review",
            NOW,
            1.0,
            2.0,
            3,
            0,
            0,
            0,
            "",
            NOW,
        ).copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .activeToken("active-$kanji")
            .schedulerRevision(0)
            .build()

    private fun temporaryDatabase(): Path {
        val directory = Files.createTempDirectory("kani-study-fault-")
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
    }

    /** A driver that throws before executing a statement matching [failWhen] once armed. */
    private class FaultDriver(
        path: String,
        private val failWhen: (String) -> Boolean,
    ) : SqlDriver {
        private val delegate = BundledTestSqlDriver(path)

        @Volatile
        var armed: Boolean = false

        override fun openConnection(mode: SqlConnectionMode): SqlConnection =
            FaultConnection(delegate.openConnection(mode), failWhen) { armed }

        override fun close() = delegate.close()
    }

    private class FaultConnection(
        private val delegate: SqlConnection,
        private val failWhen: (String) -> Boolean,
        private val armed: () -> Boolean,
    ) : SqlConnection by delegate {
        override fun prepare(sql: String): SqlStatement =
            FaultStatement(delegate.prepare(sql), sql, failWhen, armed)
    }

    private class FaultStatement(
        private val delegate: SqlStatement,
        private val sql: String,
        private val failWhen: (String) -> Boolean,
        private val armed: () -> Boolean,
    ) : SqlStatement by delegate {
        override fun execute() {
            if (armed() && failWhen(sql)) {
                throw SqlException("Injected study-commit failure at: $sql")
            }
            delegate.execute()
        }
    }
}
