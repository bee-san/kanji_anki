package dev.bee.kanjianki.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.AppliedReviewSnapshot
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.testing.DatabaseRowGolden
import dev.bee.kanjianki.testing.GoldenFixtureResources
import java.util.TimeZone
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalStoreTransactionGoldenTest {
    private lateinit var context: Context
    private lateinit var originalTimeZone: TimeZone

    @Before
    fun setUp() {
        originalTimeZone = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        TimeZone.setDefault(originalTimeZone)
    }

    @Test
    fun productionTransactionsMatchFrozenRowStates() {
        val actual = buildString {
            append(captureReviewCommitDispositions())
            append(captureUndo())
            append(captureMidSyncReview())
            append(captureSuccessfulHistoryOnly())
        }

        assertEquals(GoldenFixtureResources.text(TRANSACTION_GOLDEN), actual)
    }

    private fun captureReviewCommitDispositions(): String = withFreshStore { store ->
        val before = studyItem("甲", totalReviews = 1, dueAt = 1_000L)
        store.saveStudyItem(before)
        val after = before.copyBuilder()
            .totalReviews(2)
            .dueAtMillis(5_000L)
            .stability(4.0)
            .build()
        val applied = store.saveReviewOutcome(
            after,
            reviewRequest("甲", "fixture-review-applied"),
            "good",
            2_000L,
            before,
        )
        val duplicate = store.saveReviewOutcome(
            before.copyBuilder().totalReviews(99).build(),
            reviewRequest("甲", "fixture-review-applied"),
            "good",
            3_000L,
            before,
        )
        val stale = store.saveReviewOutcome(
            before.copyBuilder().totalReviews(3).build(),
            reviewRequest("甲", "fixture-review-stale"),
            "good",
            4_000L,
            before,
        )

        buildString {
            appendLine("[review-commit-dispositions]")
            append("results|")
                .append(applied.disposition)
                .append('|')
                .append(duplicate.disposition)
                .append('|')
                .appendLine(stale.disposition)
            append(
                DatabaseRowGolden.capture(
                    store.readableDatabase,
                    LocalStoreBase.TABLE_STUDY_ITEMS,
                    "kanji=?",
                    arrayOf("甲"),
                    "kanji, answer_signature",
                ),
            )
            append(
                DatabaseRowGolden.capture(
                    store.readableDatabase,
                    LocalStoreBase.TABLE_REVIEW_LOG,
                    "kanji=?",
                    arrayOf("甲"),
                    "id",
                ),
            )
        }
    }

    private fun captureUndo(): String = withFreshStore { store ->
        val before = studyItem("乙", totalReviews = 7, dueAt = 7_000L)
        store.saveStudyItem(before)
        val committed = store.commitReview(
            ReviewCommitCommand(
                afterReview = before.copyBuilder()
                    .totalReviews(8)
                    .dueAtMillis(8_000L)
                    .stability(8.0)
                    .build(),
                request = reviewRequest("乙", "fixture-review-undo"),
                appliedRating = "good",
                reviewedAtMillis = 7_500L,
                beforeReview = before,
                taskTiming = ReviewTaskTiming(
                    "fixture-task-undo",
                    "乙",
                    "kanji_meaning",
                    7_100L,
                    7_500L,
                    350L,
                    "good",
                ),
            ),
        )
        val undone = store.undoLastAppliedReview(
            AppliedReviewSnapshot(
                "fixture-review-undo",
                before,
                requireNotNull(committed.item),
            ),
        )

        buildString {
            appendLine("[undo]")
            appendLine("result|$undone")
            append(
                DatabaseRowGolden.capture(
                    store.readableDatabase,
                    LocalStoreBase.TABLE_STUDY_ITEMS,
                    "kanji=?",
                    arrayOf("乙"),
                    "kanji, answer_signature",
                ),
            )
            append(
                DatabaseRowGolden.capture(
                    store.readableDatabase,
                    LocalStoreBase.TABLE_REVIEW_LOG,
                    "kanji=?",
                    arrayOf("乙"),
                    "id",
                ),
            )
            append(
                DatabaseRowGolden.capture(
                    store.readableDatabase,
                    LocalStoreBase.TABLE_STUDY_TASK_LOG,
                    "kanji=?",
                    arrayOf("乙"),
                    "id",
                ),
            )
        }
    }

    private fun captureMidSyncReview(): String = withFreshStore { store ->
        val baseline = studyItem("丙", totalReviews = 3, dueAt = 100L)
            .copyBuilder()
            .lastRealReviewDueAtMillis(100L)
            .build()
        store.replaceStudyItems(listOf(baseline))

        val reviewed = baseline.copyBuilder()
            .totalReviews(4)
            .lastRealReviewDueAtMillis(9_000L)
            .dueAtMillis(9_000L)
            .build()
        store.saveStudyItem(reviewed)

        val staleSeed = baseline.copyBuilder().dueAtMillis(200L).build()
        store.replaceStudyItems(
            listOf(staleSeed),
            syncId = 1L,
            occurredAt = 10_000L,
            settings = null,
            baseline = listOf(baseline),
        )

        buildString {
            appendLine("[mid-sync-review]")
            append(
                DatabaseRowGolden.capture(
                    store.readableDatabase,
                    LocalStoreBase.TABLE_STUDY_ITEMS,
                    "kanji=?",
                    arrayOf("丙"),
                    "kanji, answer_signature",
                ),
            )
        }
    }

    private fun captureSuccessfulHistoryOnly(): String = withFreshStore { store ->
        val storage = SqliteSyncRunStorage(store)
        storage.insert(syncRun(100L, "failed"))
        storage.insert(syncRun(200L, LocalStoreBase.STATUS_SUCCESS))
        storage.insert(syncRun(300L, "failed"))
        storage.insert(syncRun(400L, "failed"))

        buildString {
            appendLine("[successful-history-only]")
            appendLine("latest_success_finished_at|${store.latestSuccessfulSyncFinishedAt()}")
            appendLine("latest_status|${store.latestSync()?.status}")
            appendLine("consecutive_failures|${store.consecutiveFailedSyncCount()}")
            append(
                DatabaseRowGolden.capture(
                    store.readableDatabase,
                    LocalStoreBase.TABLE_SYNC_RUNS,
                    orderBy = "id",
                ),
            )
        }
    }

    private fun studyItem(
        kanji: String,
        totalReviews: Int,
        dueAt: Long,
    ): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(
            kanji,
            "review",
            dueAt,
            1.0,
            2.0,
            totalReviews,
            0,
            0,
            0,
            "",
            1_000L,
        ).copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .activeToken("fixture-active-$kanji")
            .build()
    }

    private fun reviewRequest(
        kanji: String,
        token: String,
    ): RecordsSchedulerModels.ReviewRequest {
        return RecordsSchedulerModels.ReviewRequest(
            kanji,
            token,
            "good",
            false,
            true,
            false,
            0,
        )
    }

    private fun syncRun(
        finishedAt: Long,
        status: String,
    ): SyncRunRecord {
        return SyncRunRecord(
            startedAt = finishedAt - 10L,
            finishedAt = finishedAt,
            status = status,
            activeNotesCount = if (status == LocalStoreBase.STATUS_SUCCESS) 2 else 0,
            activeCardsCount = if (status == LocalStoreBase.STATUS_SUCCESS) 3 else 0,
            archivedSuspendedCardCount = 0,
            importedSuspendedKanjiCount = 0,
            deletedNotesCount = 0,
            deletedCardsCount = 0,
            errorCode = if (status == LocalStoreBase.STATUS_SUCCESS) null else "fixture_failure",
            errorMessage = if (status == LocalStoreBase.STATUS_SUCCESS) null else "deterministic failure",
            removalMessage = "",
        )
    }

    private fun <T> withFreshStore(block: (LocalStore) -> T): T {
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        return LocalStore(context).use(block)
    }

    private companion object {
        const val TRANSACTION_GOLDEN =
            "dev/bee/kanjianki/fixtures/goal165/transaction-row-states.txt"
    }
}
