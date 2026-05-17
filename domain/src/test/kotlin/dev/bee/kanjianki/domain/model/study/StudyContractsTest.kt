package dev.bee.kanjianki.domain.model.study

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyContractsTest {
    @Test
    fun defaultLadderMatchesProductContract() {
        assertEquals(
            listOf(
                StudyRung.WRITE_KANJI,
                StudyRung.SIMILAR_KANJI,
                StudyRung.TYPE_MEANING,
                StudyRung.MEANING_KANJI,
                StudyRung.KANJI_MEANING,
                StudyRung.FONT_MEANING,
                StudyRung.WORD_READING,
            ),
            StudyRung.defaultOrder,
        )
        assertFalse(StudyRung.defaultEnabled.contains(StudyRung.MEANING_KANJI))
        assertTrue(StudyRung.defaultEnabled.contains(StudyRung.KANJI_MEANING))
    }

    @Test
    fun wireNamesStayStable() {
        assertEquals(StudyRung.WRITE_KANJI, StudyRung.fromWireName("write_kanji"))
        assertEquals(StudyRung.SIMILAR_KANJI, StudyRung.fromWireName("similar_kanji"))
        assertEquals(StudyPhase.NEW_LEARNING, StudyPhase.fromWireName("new_learning"))
        assertEquals(StudyPhase.RELEARNING, StudyPhase.fromWireName("relearning"))
        assertEquals(StudyRating.AGAIN, StudyRating.fromWireName("again"))
        assertEquals(StudyRating.EASY, StudyRating.fromWireName("easy"))
    }

    @Test
    fun onlyAgainCountsAsLadderFailure() {
        assertFalse(StudyRating.AGAIN.countsAsLadderPass)
        assertTrue(StudyRating.HARD.countsAsLadderPass)
        assertTrue(StudyRating.GOOD.countsAsLadderPass)
        assertTrue(StudyRating.EASY.countsAsLadderPass)
    }
}
