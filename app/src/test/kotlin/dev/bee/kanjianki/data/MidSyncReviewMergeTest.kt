package dev.bee.kanjianki.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsStudyModels.StudyItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MidSyncReviewMergeTest {
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

    private fun item(
        kanji: String,
        totalReviews: Int,
        lastRealReviewDueAt: Long,
        dueAt: Long,
    ): StudyItem {
        return StudyItem(kanji, "review", dueAt, 1.0, 5.0, totalReviews, 0, 0, 0, null, 1L)
            .copyBuilder()
            .lastRealReviewDueAtMillis(lastRealReviewDueAt)
            .build()
    }

    @Test
    fun reviewSavedMidSyncSurvivesReplaceStudyItems() {
        // Pre-sync state the sync reads as its baseline.
        val baseline = listOf(item("痛", totalReviews = 3, lastRealReviewDueAt = 100L, dueAt = 100L))
        store.replaceStudyItems(baseline)

        // The user completes a review mid-sync: persisted state advances.
        val reviewed = item("痛", totalReviews = 4, lastRealReviewDueAt = 9_000L, dueAt = 9_000L)
        store.saveStudyItem(reviewed)

        // The sync computed pre-review seeded state and now writes with the baseline.
        val seeded = listOf(item("痛", totalReviews = 3, lastRealReviewDueAt = 100L, dueAt = 200L))
        store.replaceStudyItems(seeded, syncId = 1L, occurredAt = 10_000L, settings = null, baseline = baseline)

        val persisted = store.studyItemsForKanji(listOf("痛"))
        assertEquals(1, persisted.size)
        assertEquals("mid-sync review must not be overwritten", 4, persisted[0].totalReviews)
        assertEquals(9_000L, persisted[0].dueAtMillis)
    }

    @Test
    fun seededStateWinsWhenNoReviewLandedMidSync() {
        val baseline = listOf(item("痛", totalReviews = 3, lastRealReviewDueAt = 100L, dueAt = 100L))
        store.replaceStudyItems(baseline)

        val seeded = listOf(item("痛", totalReviews = 3, lastRealReviewDueAt = 100L, dueAt = 555L))
        store.replaceStudyItems(seeded, syncId = 1L, occurredAt = 10_000L, settings = null, baseline = baseline)

        val persisted = store.studyItemsForKanji(listOf("痛"))
        assertEquals(1, persisted.size)
        assertEquals(555L, persisted[0].dueAtMillis)
    }

    @Test
    fun scopedRefreshRetainsReviewOfOmittedKanjiSavedAfterBaselineRead() {
        val active = item("痛", totalReviews = 3, lastRealReviewDueAt = 100L, dueAt = 100L)
        val omitted = item("裂", totalReviews = 2, lastRealReviewDueAt = 90L, dueAt = 90L)
        store.replaceStudyItems(listOf(active, omitted))

        val reviewedOmitted = item("裂", totalReviews = 3, lastRealReviewDueAt = 9_000L, dueAt = 9_000L)
        store.saveStudyItem(reviewedOmitted)

        val seededActive = item("痛", totalReviews = 3, lastRealReviewDueAt = 100L, dueAt = 555L)
        store.replaceStudyItems(
            listOf(seededActive),
            syncId = null,
            occurredAt = 0L,
            settings = null,
            baseline = listOf(active),
        )

        val persisted = store.studyItems().associateBy { it.kanji }
        assertEquals(555L, persisted.getValue("痛").dueAtMillis)
        assertEquals(3, persisted.getValue("裂").totalReviews)
        assertEquals(9_000L, persisted.getValue("裂").dueAtMillis)
        assertEquals(9_000L, persisted.getValue("裂").lastRealReviewDueAtMillis)
    }
}
