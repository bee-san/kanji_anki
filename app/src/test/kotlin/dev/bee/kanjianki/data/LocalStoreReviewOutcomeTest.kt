package dev.bee.kanjianki.data

import android.content.Context
import androidx.core.database.sqlite.transaction
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Goal 45: the advanced study item and its review-log row must persist in one
 * transaction, so process death can never advance scheduling with no
 * `review_log` row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalStoreReviewOutcomeTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        store = LocalStore(context)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @Test
    fun saveReviewOutcomePersistsAdvancedItemAndReviewLogTogether() {
        val before = studyItem("痛", totalReviews = 1)
        store.saveStudyItem(before)
        val after = before.copyBuilder().totalReviews(2).stability(4.0).build()

        store.saveReviewOutcome(after, reviewRequest("痛", "token-outcome"), "good", 2_000L, before)

        assertEquals(2, store.studyItemsForKanji(listOf("痛")).single().totalReviews)
        assertEquals(1, reviewRowCount("token-outcome"))
    }

    @Test
    fun failureInsideTheOutcomeTransactionRollsBackTheItemAdvance() {
        val before = studyItem("痛", totalReviews = 1)
        store.saveStudyItem(before)
        val after = before.copyBuilder().totalReviews(2).stability(4.0).build()

        // Drive the same combined write inside an enclosing transaction that then
        // aborts (Android SQLite nests on one connection: the inner outcome write
        // only commits when the outermost transaction commits). This models a crash
        // before the commit: neither the item advance nor the review row survives.
        runCatching {
            store.writableDatabase.transaction {
                store.saveReviewOutcome(after, reviewRequest("痛", "token-fail"), "good", 2_000L, before)
                throw IllegalStateException("simulated crash before commit")
            }
        }

        assertEquals("item advance must roll back", 1, store.studyItemsForKanji(listOf("痛")).single().totalReviews)
        assertEquals("no review row may survive the aborted transaction", 0, reviewRowCount("token-fail"))
    }

    private fun reviewRowCount(token: String): Int {
        return store.readableDatabase.query(
            LocalStoreBase.TABLE_REVIEW_LOG,
            arrayOf(LocalStoreBase.COLUMN_TOKEN),
            "${LocalStoreBase.COLUMN_TOKEN} = ?",
            arrayOf(token),
            null,
            null,
            null,
        ).use { it.count }
    }

    private fun reviewRequest(kanji: String, token: String): RecordsSchedulerModels.ReviewRequest {
        return RecordsSchedulerModels.ReviewRequest(kanji, token, "good", false, true, false, 0)
    }

    private fun studyItem(kanji: String, totalReviews: Int): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(kanji, "review", 1_000L, 1.0, 2.0, totalReviews, 0, 0, 0, "", 1_000L)
            .copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .activeToken("token-$kanji")
            .build()
    }
}
