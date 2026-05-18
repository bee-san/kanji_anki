package dev.bee.kanjianki.core.study;

import dev.bee.kanjianki.core.RecordsSchedulerModels;

public final class StudyReviewRequestPolicy {
    private static final WritingRatingMapper RATING_MAPPER = new WritingRatingMapper();

    private StudyReviewRequestPolicy() {
    }

    public static MappedReview from(
            RecordsSchedulerModels.StudySession session,
            WritingAnalysis analysis,
            int hintsUsed,
            String rating,
            boolean override
    ) {
        StudyRating requestedRating = StudyRating.fromCode(rating);
        StudyRating mappedRating = RATING_MAPPER.applyRequestedRating(requestedRating, session.writingRequired, analysis, override);
        boolean passed = !session.writingRequired || (analysis != null && analysis.writingPassed);
        boolean cleanWriting = analysis != null && analysis.status == WritingAnalysis.Status.PASS;
        RecordsSchedulerModels.ReviewRequest request = new RecordsSchedulerModels.ReviewRequest(
                session.item.kanji,
                session.token,
                mappedRating.code(),
                session.writingRequired,
                passed,
                cleanWriting,
                override,
                hintsUsed,
                session.taskType,
                session.item.answerSignature,
                session.prompt
        );
        return new MappedReview(request, mappedRating.code());
    }

    public static final class MappedReview {
        private final RecordsSchedulerModels.ReviewRequest request;
        private final String ratingCode;

        private MappedReview(RecordsSchedulerModels.ReviewRequest request, String ratingCode) {
            this.request = request;
            this.ratingCode = ratingCode;
        }

        public RecordsSchedulerModels.ReviewRequest request() {
            return request;
        }

        public String ratingCode() {
            return ratingCode;
        }
    }
}
