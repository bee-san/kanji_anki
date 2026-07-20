package dev.bee.kanjianki

import dev.bee.kanjianki.core.AppliedReviewSnapshot
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyRatings
import dev.bee.kanjianki.core.StudyTextCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class StudyUndoStateTest {
    @Test
    fun captureReplacesPendingSnapshotAndClearResetsState() {
        val state = StudyUndoState()
        val firstSnapshot = AppliedReviewSnapshot(
            "token-1",
            item("語", 1),
            item("語", 2),
        )
        val secondSnapshot = AppliedReviewSnapshot(
            "token-2",
            item("字", 3),
            item("字", 4),
        )

        state.capture(firstSnapshot, "good", 123L)
        assertSame(firstSnapshot, state.pending?.snapshot)
        assertEquals("good", state.pending?.label)
        assertEquals(123L, state.pending?.createdAtMillis)

        state.capture(secondSnapshot, "again", 456L)
        assertSame(secondSnapshot, state.pending?.snapshot)
        assertEquals("again", state.pending?.label)
        assertEquals(456L, state.pending?.createdAtMillis)

        state.clear()
        assertNull(state.pending)
    }

    @Test
    fun undoMessageOrNullReturnsNullWithoutPendingAndCopyForCapturedReview() {
        val state = StudyUndoState()

        assertNull(state.undoMessageOrNull())

        val snapshot = AppliedReviewSnapshot(
            "token-3",
            item("語", 5),
            item("語", 6),
        )
        state.capture(snapshot, StudyRatings.GOOD, 789L)

        assertEquals(StudyTextCopy.reviewUndoMessage(StudyRatings.GOOD), state.undoMessageOrNull())

        state.clear()
        assertNull(state.undoMessageOrNull())
    }

    private fun item(kanji: String, totalReviews: Int): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(
            kanji,
            "review",
            1_000L,
            1.0,
            2.0,
            totalReviews,
            0,
            0,
            0,
            "",
            1_000L,
        )
    }
}
