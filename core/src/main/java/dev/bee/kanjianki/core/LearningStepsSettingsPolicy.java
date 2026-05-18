package dev.bee.kanjianki.core;

import java.util.List;

public final class LearningStepsSettingsPolicy {
    public static final String STEP_FORMAT_ERROR = "Use steps like 1m, 10m, or 1h.";

    private LearningStepsSettingsPolicy() {
    }

    public static SaveResult saveRequest(String newStepsText, String reviewStepsText) {
        List<Integer> parsedNew = RecordsSchedulerModels.LearningStepSettings.tryParseSteps(newStepsText);
        List<Integer> parsedReview = RecordsSchedulerModels.LearningStepSettings.tryParseSteps(reviewStepsText);
        if (parsedNew.isEmpty() || parsedReview.isEmpty()) {
            return SaveResult.invalid(STEP_FORMAT_ERROR);
        }
        return SaveResult.valid(new RecordsSchedulerModels.LearningStepSettings(parsedNew, parsedReview));
    }

    public static final class SaveResult {
        public final boolean valid;
        public final RecordsSchedulerModels.LearningStepSettings settings;
        public final String message;

        private SaveResult(boolean valid, RecordsSchedulerModels.LearningStepSettings settings, String message) {
            this.valid = valid;
            this.settings = settings;
            this.message = message;
        }

        static SaveResult valid(RecordsSchedulerModels.LearningStepSettings settings) {
            return new SaveResult(true, settings, "");
        }

        static SaveResult invalid(String message) {
            return new SaveResult(false, null, message);
        }
    }
}
