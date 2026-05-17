package dev.bee.kanjianki.domain.scheduler

import dev.bee.kanjianki.domain.model.study.StudyPhase
import dev.bee.kanjianki.domain.model.study.StudyRating
import dev.bee.kanjianki.domain.model.study.StudyRung
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LadderMovementEngineTest {
    private val engine = LadderMovementEngine()

    @Test
    fun passPromotesOnlyWhenFsrsIntervalIsStrictlyAboveThreshold() {
        assertEquals(
            StudyRung.KANJI_MEANING,
            engine.apply(input(StudyRating.GOOD, intervalDays = 21)).rung,
        )

        val promoted = engine.apply(input(StudyRating.GOOD, intervalMillis = 21 * DAY + 1))

        assertEquals(StudyRung.FONT_MEANING, promoted.rung)
        assertEquals(LadderMovementType.PROMOTED, promoted.movementType)
        assertEquals(0, promoted.realPassStreak)
        assertEquals(0, promoted.realAgainStreak)
    }

    @Test
    fun hardAndEasyCountAsPassesWhenTheyDoNotPromote() {
        val hard = engine.apply(input(StudyRating.HARD, intervalDays = 1))
        val easy = engine.apply(input(StudyRating.EASY, intervalDays = 1))

        assertEquals(1, hard.realPassStreak)
        assertEquals(0, hard.realAgainStreak)
        assertEquals(1, easy.realPassStreak)
        assertEquals(0, easy.realAgainStreak)
    }

    @Test
    fun againDemotesAtConfiguredConsecutiveFailThreshold() {
        val held = engine.apply(input(StudyRating.AGAIN, realAgainStreak = 1))
        val demoted = engine.apply(input(StudyRating.AGAIN, realAgainStreak = 2))

        assertEquals(StudyRung.KANJI_MEANING, held.rung)
        assertEquals(2, held.realAgainStreak)
        assertEquals(StudyRung.TYPE_MEANING, demoted.rung)
        assertEquals(LadderMovementType.DEMOTED, demoted.movementType)
        assertEquals(0, demoted.realAgainStreak)
        assertEquals(0, demoted.realPassStreak)
    }

    @Test
    fun customThresholdsControlPromotionAndDemotion() {
        val settings = StudyLadderSettings(
            promotionIntervalDays = 30,
            demotionFailStreak = 5,
        )

        assertEquals(
            StudyRung.KANJI_MEANING,
            engine.apply(input(StudyRating.GOOD, intervalDays = 30, settings = settings)).rung,
        )
        assertEquals(
            StudyRung.FONT_MEANING,
            engine.apply(input(StudyRating.GOOD, intervalDays = 31, settings = settings)).rung,
        )
        assertEquals(
            StudyRung.KANJI_MEANING,
            engine.apply(input(StudyRating.AGAIN, realAgainStreak = 3, settings = settings)).rung,
        )
        assertEquals(
            StudyRung.TYPE_MEANING,
            engine.apply(input(StudyRating.AGAIN, realAgainStreak = 4, settings = settings)).rung,
        )
    }

    @Test
    fun similarKanjiIsUsedOnlyWhenAvailable() {
        assertEquals(
            StudyRung.SIMILAR_KANJI,
            engine.apply(
                input(
                    StudyRating.GOOD,
                    currentRung = StudyRung.WRITE_KANJI,
                    intervalDays = 22,
                    hasSimilarKanji = true,
                ),
            ).rung,
        )
        assertEquals(
            StudyRung.TYPE_MEANING,
            engine.apply(
                input(
                    StudyRating.GOOD,
                    currentRung = StudyRung.WRITE_KANJI,
                    intervalDays = 22,
                    hasSimilarKanji = false,
                ),
            ).rung,
        )
    }

    @Test
    fun sameDueSlotAndFutureDueReviewsDoNotAdvanceStreaks() {
        val sameSlot = engine.apply(
            input(
                StudyRating.GOOD,
                dueAtMillis = 500,
                lastRealReviewDueAtMillis = 500,
                realPassStreak = 1,
                nowMillis = 1_000,
            ),
        )
        val future = engine.apply(
            input(
                StudyRating.GOOD,
                dueAtMillis = 2_000,
                lastRealReviewDueAtMillis = 0,
                realPassStreak = 1,
                nowMillis = 1_000,
            ),
        )

        assertEquals(1, sameSlot.realPassStreak)
        assertEquals(500, sameSlot.lastRealReviewDueAtMillis)
        assertEquals(1, future.realPassStreak)
        assertEquals(0, future.lastRealReviewDueAtMillis)
    }

    @Test
    fun learningAndRelearningPhasesDoNotMoveLadder() {
        val result = engine.apply(
            input(
                StudyRating.GOOD,
                phase = StudyPhase.RELEARNING,
                intervalDays = 99,
                realAgainStreak = 2,
            ),
        )

        assertEquals(StudyRung.KANJI_MEANING, result.rung)
        assertEquals(2, result.realAgainStreak)
        assertEquals(LadderMovementType.NONE, result.movementType)
    }

    @Test
    fun disabledCurrentRungAlignsToNearestLowerRungOnTie() {
        val settings = StudyLadderSettings(
            enabledRungs = setOf(StudyRung.MEANING_KANJI, StudyRung.FONT_MEANING),
        )

        assertEquals(
            StudyRung.MEANING_KANJI,
            settings.effectiveRung(StudyRung.KANJI_MEANING, hasSimilarKanji = false),
        )
    }

    @Test
    fun similarKanjiCannotBeOnlyEnabledRung() {
        assertThrows(IllegalArgumentException::class.java) {
            StudyLadderSettings(enabledRungs = setOf(StudyRung.SIMILAR_KANJI))
        }
    }

    private fun input(
        rating: StudyRating,
        currentRung: StudyRung = StudyRung.KANJI_MEANING,
        phase: StudyPhase = StudyPhase.REVIEW,
        dueAtMillis: Long = 500L,
        nowMillis: Long = 1_000L,
        lastRealReviewDueAtMillis: Long = 0L,
        realPassStreak: Int = 0,
        realAgainStreak: Int = 0,
        intervalDays: Long = 0L,
        intervalMillis: Long = intervalDays * DAY,
        hasSimilarKanji: Boolean = false,
        settings: StudyLadderSettings = StudyLadderSettings.defaults,
    ): LadderMovementInput = LadderMovementInput(
        currentRung = currentRung,
        phase = phase,
        rating = rating,
        dueAtMillis = dueAtMillis,
        nowMillis = nowMillis,
        lastRealReviewDueAtMillis = lastRealReviewDueAtMillis,
        realPassStreak = realPassStreak,
        realAgainStreak = realAgainStreak,
        fsrsScheduledIntervalMillis = intervalMillis,
        hasSimilarKanji = hasSimilarKanji,
        settings = settings,
    )

    private companion object {
        const val DAY = 86_400_000L
    }
}
