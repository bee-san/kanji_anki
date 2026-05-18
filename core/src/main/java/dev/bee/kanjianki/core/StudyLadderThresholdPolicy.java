package dev.bee.kanjianki.core;

public final class StudyLadderThresholdPolicy {
    public static final String POSITIVE_WHOLE_NUMBER_ERROR = "Use positive whole numbers.";

    private StudyLadderThresholdPolicy() {
    }

    public static SaveResult saveRequest(String promotionDaysText, String failStreakText) {
        int promotionDays;
        int failStreak;
        try {
            promotionDays = parseWholeNumber(promotionDaysText);
            failStreak = parseWholeNumber(failStreakText);
        } catch (NumberFormatException error) {
            return SaveResult.invalid(POSITIVE_WHOLE_NUMBER_ERROR);
        }
        if (promotionDays < 1 || failStreak < 1) {
            return SaveResult.invalid(POSITIVE_WHOLE_NUMBER_ERROR);
        }
        return SaveResult.valid(promotionDays, failStreak);
    }

    private static int parseWholeNumber(String value) {
        return Integer.parseInt(value.trim());
    }

    public static final class SaveResult {
        public final boolean valid;
        public final int promotionDays;
        public final int failStreak;
        public final String message;

        private SaveResult(boolean valid, int promotionDays, int failStreak, String message) {
            this.valid = valid;
            this.promotionDays = promotionDays;
            this.failStreak = failStreak;
            this.message = message;
        }

        static SaveResult valid(int promotionDays, int failStreak) {
            return new SaveResult(true, promotionDays, failStreak, "");
        }

        static SaveResult invalid(String message) {
            return new SaveResult(false, 0, 0, message);
        }
    }
}
