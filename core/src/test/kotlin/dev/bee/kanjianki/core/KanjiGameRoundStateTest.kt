package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KanjiGameRoundStateTest {
    @Test
    fun newRoundStartsEmptyAndShowsCurrentQuestionProgress() {
        val round = KanjiGameRoundState.newRound(10)

        assertEquals(10, round.totalQuestions)
        assertEquals(0, round.answered)
        assertEquals(0, round.correct)
        assertEquals(0, round.streak)
        assertEquals(0, round.progress(false))
        assertEquals(1, round.progress(true))
        assertEquals(0, round.accuracyPercent())
        assertFalse(round.roundComplete())
    }

    @Test
    fun answersTrackScoreStreakCompletionAndAccuracy() {
        val round = KanjiGameRoundState.newRound(3)
            .answer(true)
            .answer(true)
            .answer(false)

        assertEquals(3, round.answered)
        assertEquals(2, round.correct)
        assertEquals(0, round.streak)
        assertEquals(3, round.progress(false))
        assertEquals(3, round.progress(true))
        assertEquals(67, round.accuracyPercent())
        assertEquals(67, KanjiGameRoundState.accuracyPercent(2, 3))
        assertTrue(round.roundComplete())
    }

    @Test
    fun correctAnswerContinuesStreakAfterAMissResetsIt() {
        val round = KanjiGameRoundState.newRound(4)
            .answer(true)
            .answer(false)
            .answer(true)

        assertEquals(3, round.answered)
        assertEquals(2, round.correct)
        assertEquals(1, round.streak)
        assertEquals(3, round.progress(false))
        assertFalse(round.roundComplete())
    }

    @Test
    fun completedRoundIgnoresExtraAnswersAndAccuracyStaysBounded() {
        val complete = KanjiGameRoundState.newRound(1).answer(true)
        val extra = complete.answer(false)

        assertTrue(complete === extra)
        assertEquals(1, extra.answered)
        assertEquals(1, extra.correct)
        assertEquals(100, KanjiGameRoundState.accuracyPercent(Int.MAX_VALUE, 1))
        assertEquals(0, KanjiGameRoundState.accuracyPercent(Int.MIN_VALUE, 1))
    }
}
