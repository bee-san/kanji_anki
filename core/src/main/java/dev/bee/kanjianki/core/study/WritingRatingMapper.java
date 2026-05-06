package dev.bee.kanjianki.core.study;

public final class WritingRatingMapper {
    public StudyRating applyRequestedRating(
            StudyRating requestedRating,
            boolean writingRequired,
            WritingAnalysis analysis,
            boolean manualOverride
    ) {
        StudyRating requested = requestedRating == null ? StudyRating.AGAIN : requestedRating;
        if (!writingRequired || manualOverride) {
            return requested;
        }
        if (analysis == null || !analysis.passed()) {
            return StudyRating.AGAIN;
        }
        return requested.cappedAt(maxAllowedRating(analysis));
    }

    public StudyRating suggestedRating(WritingAnalysis analysis) {
        if (analysis == null || !analysis.passed()) {
            return StudyRating.AGAIN;
        }
        if (analysis.status == WritingAnalysis.Status.CLOSE) {
            return StudyRating.HARD;
        }
        double confidence = analysis.confidenceScore();
        if (confidence >= 0.92 && analysis.hintLevel() == HintLevel.BLIND && analysis.hintsUsed() == 0) {
            return StudyRating.EASY;
        }
        if (confidence >= 0.72) {
            return StudyRating.GOOD;
        }
        return StudyRating.HARD;
    }

    public StudyRating maxAllowedRating(WritingAnalysis analysis) {
        if (analysis == null || !analysis.passed()) {
            return StudyRating.AGAIN;
        }
        if (analysis.status == WritingAnalysis.Status.CLOSE) {
            return StudyRating.HARD;
        }
        if (analysis.hintsUsed() > 0 || analysis.hintLevel() == HintLevel.TRACE) {
            return StudyRating.HARD;
        }
        if (analysis.confidenceScore() < 0.72) {
            return StudyRating.HARD;
        }
        if (analysis.hintLevel() == HintLevel.OUTLINE) {
            return StudyRating.GOOD;
        }
        return StudyRating.EASY;
    }
}
