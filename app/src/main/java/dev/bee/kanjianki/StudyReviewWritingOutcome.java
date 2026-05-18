package dev.bee.kanjianki;

import dev.bee.kanjianki.core.StudyReviewRequestPolicy;
import dev.bee.kanjianki.core.study.WritingAnalysis;
import dev.bee.kanjianki.core.study.WritingRatingMapper;

final class StudyReviewWritingOutcome {
    private static final WritingRatingMapper WRITING_RATING_MAPPER = new WritingRatingMapper();

    private StudyReviewWritingOutcome() {
    }

    static StudyReviewRequestPolicy.WritingOutcome from(WritingAnalysis analysis) {
        if (analysis == null) {
            return null;
        }
        return StudyReviewRequestPolicy.writingOutcome(
                analysis.writingPassed,
                analysis.status == WritingAnalysis.Status.PASS,
                WRITING_RATING_MAPPER.maxAllowedRating(analysis).code()
        );
    }
}
