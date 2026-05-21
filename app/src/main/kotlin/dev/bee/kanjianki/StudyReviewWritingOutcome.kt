package dev.bee.kanjianki

import dev.bee.kanjianki.core.StudyReviewRequestPolicy
import dev.bee.kanjianki.core.study.WritingAnalysis
import dev.bee.kanjianki.core.study.WritingRatingMapper

internal object StudyReviewWritingOutcome {
    private val writingRatingMapper = WritingRatingMapper()

    @JvmStatic
    fun from(analysis: WritingAnalysis?): StudyReviewRequestPolicy.WritingOutcome? {
        if (analysis == null) {
            return null
        }
        return StudyReviewRequestPolicy.writingOutcome(
            analysis.writingPassed,
            analysis.status == WritingAnalysis.Status.PASS,
            writingRatingMapper.maxAllowedRating(analysis).code()
        )
    }
}
