package dev.bee.kanjianki.core;

public final class SimilarKanjiChoiceReviewPolicy {
    private SimilarKanjiChoiceReviewPolicy() {
    }

    public static ReviewUpdate reviewUpdate(
            RecordsImportModels.SimilarKanjiChoiceCard card,
            RecordsImportModels.SimilarKanjiChoiceResult result,
            long nowMillis
    ) {
        boolean correct = result != null && result.correct;
        int correctCount = card == null ? 0 : card.correctCount;
        int wrongCount = card == null ? 0 : card.wrongCount;
        if (correct) {
            return new ReviewUpdate(nowMillis, nowMillis, null, correctCount + 1, null);
        }
        return new ReviewUpdate(nowMillis, 0L, nowMillis, null, wrongCount + 1);
    }

    public record ReviewUpdate(
            long lastReviewedAtMillis,
            long passedAtMillis,
            Long dueAtMillis,
            Integer correctCount,
            Integer wrongCount
    ) {
    }
}
