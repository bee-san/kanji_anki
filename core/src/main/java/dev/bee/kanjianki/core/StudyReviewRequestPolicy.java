package dev.bee.kanjianki.core;

public final class StudyReviewRequestPolicy {
    private StudyReviewRequestPolicy() {
    }

    public static MappedReview from(
            RecordsSchedulerModels.StudySession session,
            WritingOutcome writingOutcome,
            int hintsUsed,
            String rating,
            boolean override
    ) {
        String mappedRating = applyRequestedRating(rating, session.writingRequired, writingOutcome, override);
        boolean passed = !session.writingRequired || (writingOutcome != null && writingOutcome.writingPassed);
        boolean cleanWriting = writingOutcome != null && writingOutcome.cleanWriting;
        RecordsSchedulerModels.ReviewRequest request = new RecordsSchedulerModels.ReviewRequest(
                session.item.kanji,
                session.token,
                mappedRating,
                session.writingRequired,
                passed,
                cleanWriting,
                override,
                hintsUsed,
                session.taskType,
                session.item.answerSignature,
                session.prompt
        );
        return new MappedReview(request, mappedRating);
    }

    public static WritingOutcome writingOutcome(boolean writingPassed, boolean cleanWriting, String maxAllowedRating) {
        return new WritingOutcome(writingPassed, cleanWriting, maxAllowedRating);
    }

    private static String applyRequestedRating(
            String requestedRating,
            boolean writingRequired,
            WritingOutcome writingOutcome,
            boolean manualOverride
    ) {
        String requested = StudyRatings.normalize(requestedRating);
        if (!writingRequired || manualOverride) {
            return requested;
        }
        if (writingOutcome == null || !writingOutcome.writingPassed) {
            return StudyRatings.AGAIN;
        }
        return capAt(requested, writingOutcome.maxAllowedRating);
    }

    private static String capAt(String requested, String ceiling) {
        return strength(requested) > strength(ceiling) ? ceiling : requested;
    }

    private static int strength(String rating) {
        return switch (StudyRatings.normalize(rating)) {
            case StudyRatings.HARD -> 1;
            case StudyRatings.GOOD -> 2;
            case StudyRatings.EASY -> 3;
            default -> 0;
        };
    }

    public static final class WritingOutcome {
        private final boolean writingPassed;
        private final boolean cleanWriting;
        private final String maxAllowedRating;

        private WritingOutcome(boolean writingPassed, boolean cleanWriting, String maxAllowedRating) {
            this.writingPassed = writingPassed;
            this.cleanWriting = cleanWriting;
            this.maxAllowedRating = StudyRatings.normalize(maxAllowedRating);
        }
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
