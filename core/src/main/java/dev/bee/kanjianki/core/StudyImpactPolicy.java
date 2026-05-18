package dev.bee.kanjianki.core;

public final class StudyImpactPolicy {
    private StudyImpactPolicy() {
    }

    public static Impact summarize(
            int totalReviews,
            int distinctReviewedKanji,
            int writingRequired,
            int writingPassed,
            int writingFailed,
            int manualOverrides
    ) {
        return new Impact(
                totalReviews,
                distinctReviewedKanji,
                writingRequired,
                writingPassed,
                writingFailed,
                manualOverrides
        );
    }

    public record Impact(
            int totalReviews,
            int distinctReviewedKanji,
            int writingRequired,
            int writingPassed,
            int writingFailed,
            int manualOverrides
    ) {
    }
}
