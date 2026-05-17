package dev.bee.kanjianki.domain.scheduler

import dev.bee.kanjianki.domain.model.study.StudyRating
import dev.bee.kanjianki.domain.model.study.StudyRung
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewRatingResolverTest {
    private val resolver = ReviewRatingResolver()

    @Test
    fun writeKanjiManualOverrideResolvesToHard() {
        val resolved = resolver.resolve(
            request = request(
                rating = StudyRating.GOOD,
                writingRequired = true,
                writingPassed = false,
                manualOverride = true,
            ),
            rung = StudyRung.WRITE_KANJI,
        )

        assertEquals(StudyRating.HARD, resolved.rating)
        assertFalse(resolved.failedWriting)
        assertFalse(resolved.cleanWritingPass)
    }

    @Test
    fun failedRequiredWritingResolvesToAgain() {
        val writeKanji = resolver.resolve(
            request = request(
                rating = StudyRating.EASY,
                writingRequired = true,
                writingPassed = false,
            ),
            rung = StudyRung.WRITE_KANJI,
        )
        val recognitionWithRequiredWriting = resolver.resolve(
            request = request(
                rating = StudyRating.GOOD,
                writingRequired = true,
                writingPassed = false,
            ),
            rung = StudyRung.KANJI_MEANING,
        )

        assertEquals(StudyRating.AGAIN, writeKanji.rating)
        assertTrue(writeKanji.failedWriting)
        assertEquals(StudyRating.AGAIN, recognitionWithRequiredWriting.rating)
        assertFalse(recognitionWithRequiredWriting.failedWriting)
    }

    @Test
    fun requestedRatingIsPreservedWhenWritingDoesNotForceFailure() {
        val resolved = resolver.resolve(
            request = request(
                rating = StudyRating.EASY,
                writingRequired = true,
                writingPassed = true,
            ),
            rung = StudyRung.KANJI_MEANING,
        )

        assertEquals(StudyRating.EASY, resolved.rating)
        assertFalse(resolved.failedWriting)
        assertFalse(resolved.cleanWritingPass)
    }

    @Test
    fun cleanWritingPassRequiresCleanUnguidedWriteKanjiPass() {
        assertTrue(
            resolver.resolve(
                request = request(
                    writingRequired = true,
                    writingPassed = true,
                    writingClean = true,
                    hintsUsed = 0,
                ),
                rung = StudyRung.WRITE_KANJI,
            ).cleanWritingPass,
        )
        assertFalse(
            resolver.resolve(
                request = request(
                    writingRequired = true,
                    writingPassed = true,
                    writingClean = true,
                    hintsUsed = 1,
                ),
                rung = StudyRung.WRITE_KANJI,
            ).cleanWritingPass,
        )
        assertFalse(
            resolver.resolve(
                request = request(
                    writingRequired = true,
                    writingPassed = true,
                    writingClean = true,
                ),
                rung = StudyRung.KANJI_MEANING,
            ).cleanWritingPass,
        )
    }

    private fun request(
        rating: StudyRating = StudyRating.GOOD,
        writingRequired: Boolean = false,
        writingPassed: Boolean = true,
        writingClean: Boolean = false,
        hintsUsed: Int = 0,
        manualOverride: Boolean = false,
    ): StudyReviewRequest = StudyReviewRequest(
        kanji = "裂",
        rating = rating,
        token = "token",
        writingRequired = writingRequired,
        writingPassed = writingPassed,
        writingClean = writingClean,
        hintsUsed = hintsUsed,
        manualOverride = manualOverride,
    )
}
