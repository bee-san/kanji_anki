package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayList
import java.util.concurrent.atomic.AtomicReference

class StudyReviewActionsTest {
    @Test
    fun saveAppliedReviewPersistsItemReviewOutcomeAndPassMarkerInOrder() {
        val before = item("語", 1)
        val after = item("語", 2)
        val request = request("語", MainActivityBase.RATING_GOOD)
        val result = RecordsSchedulerModels.ReviewResult(after, MainActivityBase.RATING_GOOD, false, "ok")
        val events = ArrayList<String>()
        val writer = RecordingReviewWriter(events)
        val recorder = RecordingOutcomeRecorder(events)
        val passedKanji = AtomicReference<String>()

        StudyReviewActions.saveAppliedReview(
            request,
            result,
            before,
            123L,
            writer,
            recorder,
        ) {
            events.add("markPassed")
            passedKanji.set(it)
        }

        assertEquals(listOf("saveItem", "saveReview", "recordOutcome", "markPassed"), events)
        assertSame(after, writer.savedItem)
        assertSame(request, writer.savedRequest)
        assertEquals(MainActivityBase.RATING_GOOD, writer.savedRating)
        assertEquals(123L, writer.reviewedAt)
        assertSame(before, writer.beforeReview)
        assertSame(after, writer.afterReview)
        assertEquals("語", recorder.kanji)
        assertEquals(MainActivityBase.RATING_GOOD, recorder.appliedRating)
        assertSame(before, recorder.beforeReview)
        assertSame(after, recorder.afterReview)
        assertEquals("語", passedKanji.get())
    }

    @Test
    fun saveAppliedReviewDoesNotMarkAgainAsPassed() {
        val before = item("語", 1)
        val after = item("語", 2)
        val request = request("語", MainActivityBase.RATING_AGAIN)
        val result = RecordsSchedulerModels.ReviewResult(after, MainActivityBase.RATING_AGAIN, false, "again")
        val events = ArrayList<String>()
        val writer = RecordingReviewWriter(events)
        val recorder = RecordingOutcomeRecorder(events)
        val passedKanji = AtomicReference<String>()

        StudyReviewActions.saveAppliedReview(
            request,
            result,
            before,
            123L,
            writer,
            recorder,
        ) {
            events.add("markPassed")
            passedKanji.set(it)
        }

        assertEquals(listOf("saveItem", "saveReview", "recordOutcome"), events)
        assertNull(passedKanji.get())
    }

    @Test
    fun saveTunedSchedulerWritesOnlyChangedAdjustmentState() {
        val original = RecordsSchedulerModels.SchedulerParameters(0.90, 0.45, 1.2, 2.0, 3.1, 100L, 10)
        val same = RecordsSchedulerModels.SchedulerParameters(0.95, 0.45, 1.2, 2.0, 3.1, 100L, 10)
        val changed = RecordsSchedulerModels.SchedulerParameters(0.95, 0.45, 1.2, 2.0, 3.1, 200L, 11)
        val saved = AtomicReference<RecordsSchedulerModels.SchedulerParameters>()

        StudyReviewActions.saveTunedSchedulerIfChanged(original, same) { saved.set(it) }
        assertNull(saved.get())

        StudyReviewActions.saveTunedSchedulerIfChanged(original, changed) { saved.set(it) }
        assertSame(changed, saved.get())
    }

    @Test
    fun undoLastAppliedReviewRestoresBeforeSnapshotAndDeletesConsumedToken() {
        val before = item("語", 4)
        val after = item("語", 5)
            .copyBuilder()
            .stability(8.0)
            .difficulty(3.5)
            .lapses(1)
            .build()
        val writer = RecordingUndoWriter()

        val undone = StudyReviewActions.undoLastAppliedReview(
            StudyReviewActions.AppliedReviewSnapshot("token-1", before, after),
            after,
            writer,
        )

        assertTrue(undone)
        assertSame(before, writer.savedItem)
        assertEquals("token-1", writer.deletedToken)
    }

    @Test
    fun undoLastAppliedReviewRejectsWhenCurrentSchedulerStateMovedPastAfterSnapshot() {
        val before = item("語", 4)
        val after = item("語", 5)
            .copyBuilder()
            .stability(8.0)
            .difficulty(3.5)
            .build()
        val reviewedAgain = after.copyBuilder()
            .totalReviews(6)
            .stability(9.0)
            .build()
        val writer = RecordingUndoWriter()

        val undone = StudyReviewActions.undoLastAppliedReview(
            StudyReviewActions.AppliedReviewSnapshot("token-1", before, after),
            reviewedAgain,
            writer,
        )

        assertFalse(undone)
        assertNull(writer.savedItem)
        assertNull(writer.deletedToken)
    }

    private companion object {
        fun request(kanji: String, rating: String) =
            RecordsSchedulerModels.ReviewRequest(kanji, "token", rating, false, true, false, 0)

        fun item(kanji: String, totalReviews: Int) =
            RecordsStudyModels.StudyItem(kanji, "review", 1000L, 1.0, 2.0, totalReviews, 0, 0, 0, "", 1000L)
    }

    private class RecordingReviewWriter(private val events: MutableList<String>) : StudyReviewActions.ReviewWriter {
        var savedItem: RecordsStudyModels.StudyItem? = null
        var savedRequest: RecordsSchedulerModels.ReviewRequest? = null
        var savedRating: String? = null
        var reviewedAt: Long = 0
        var beforeReview: RecordsStudyModels.StudyItem? = null
        var afterReview: RecordsStudyModels.StudyItem? = null

        override fun saveStudyItem(item: RecordsStudyModels.StudyItem) {
            events.add("saveItem")
            savedItem = item
        }

        override fun saveReview(
            request: RecordsSchedulerModels.ReviewRequest,
            appliedRating: String?,
            reviewedAt: Long,
            beforeReview: RecordsStudyModels.StudyItem,
            afterReview: RecordsStudyModels.StudyItem,
        ) {
            events.add("saveReview")
            savedRequest = request
            savedRating = appliedRating
            this.reviewedAt = reviewedAt
            this.beforeReview = beforeReview
            this.afterReview = afterReview
        }
    }

    private class RecordingOutcomeRecorder(private val events: MutableList<String>) : StudyReviewActions.ReviewOutcomeRecorder {
        var kanji: String? = null
        var appliedRating: String? = null
        var beforeReview: RecordsStudyModels.StudyItem? = null
        var afterReview: RecordsStudyModels.StudyItem? = null

        override fun recordReviewOutcome(
            kanji: String,
            appliedRating: String?,
            beforeReview: RecordsStudyModels.StudyItem,
            afterReview: RecordsStudyModels.StudyItem,
        ) {
            events.add("recordOutcome")
            this.kanji = kanji
            this.appliedRating = appliedRating
            this.beforeReview = beforeReview
            this.afterReview = afterReview
        }
    }

    private class RecordingUndoWriter : StudyReviewActions.UndoWriter {
        var savedItem: RecordsStudyModels.StudyItem? = null
        var deletedToken: String? = null

        override fun saveStudyItem(item: RecordsStudyModels.StudyItem) {
            savedItem = item
        }

        override fun deleteReviewByToken(token: String) {
            deletedToken = token
        }
    }
}
