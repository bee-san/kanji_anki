package dev.bee.kanjianki.core.study

class WritingRatingMapper {
    fun applyRequestedRating(
        requestedRating: StudyRating?,
        writingRequired: Boolean,
        analysis: WritingAnalysis?,
        manualOverride: Boolean,
    ): StudyRating {
        val requested = requestedRating ?: StudyRating.AGAIN
        if (!writingRequired || manualOverride) {
            return requested
        }
        if (analysis == null || !analysis.passed()) {
            return StudyRating.AGAIN
        }
        return requested.cappedAt(maxAllowedRating(analysis))
    }

    fun suggestedRating(analysis: WritingAnalysis?): StudyRating {
        if (analysis == null || !analysis.passed()) {
            return StudyRating.AGAIN
        }
        if (analysis.status == WritingAnalysis.Status.CLOSE) {
            return StudyRating.HARD
        }
        val confidence = analysis.confidenceScore()
        if (confidence >= 0.92 && analysis.hintLevel() == HintLevel.BLIND && analysis.hintsUsed() == 0) {
            return StudyRating.EASY
        }
        if (confidence >= 0.72) {
            return StudyRating.GOOD
        }
        return StudyRating.HARD
    }

    fun maxAllowedRating(analysis: WritingAnalysis?): StudyRating {
        if (analysis == null || !analysis.passed()) {
            return StudyRating.AGAIN
        }
        if (analysis.status == WritingAnalysis.Status.CLOSE) {
            return StudyRating.HARD
        }
        if (analysis.hintsUsed() > 0 || analysis.hintLevel() == HintLevel.TRACE) {
            return StudyRating.HARD
        }
        if (analysis.confidenceScore() < 0.72) {
            return StudyRating.HARD
        }
        if (analysis.hintLevel() == HintLevel.OUTLINE) {
            return StudyRating.GOOD
        }
        return StudyRating.EASY
    }
}
