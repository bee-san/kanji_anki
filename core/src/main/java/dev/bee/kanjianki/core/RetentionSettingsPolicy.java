package dev.bee.kanjianki.core;

public final class RetentionSettingsPolicy {
    public static final String SAVED_MESSAGE = "FSRS retention saved.";

    private RetentionSettingsPolicy() {
    }

    public static SaveResult saveRequest(
            int retentionPercent,
            boolean frequencyRetentionEnabled,
            String frequencyRetentionRanges,
            RecordsSchedulerModels.SchedulerParameters latest
    ) {
        String ranges = frequencyRetentionRanges == null ? "" : frequencyRetentionRanges.trim();
        if (frequencyRetentionEnabled) {
            try {
                FrequencyRetentionRanges.parse(ranges);
            } catch (IllegalArgumentException error) {
                return SaveResult.invalid(error.getMessage());
            }
        }
        RecordsSchedulerModels.SchedulerParameters safeLatest = latest == null
                ? RecordsSchedulerModels.SchedulerParameters.defaults()
                : latest;
        RecordsSchedulerModels.SchedulerParameters parameters = new RecordsSchedulerModels.SchedulerParameters(
                SettingsInputRules.retentionPercent(retentionPercent / 100.0) / 100.0,
                safeLatest.againMultiplier,
                safeLatest.hardMultiplier,
                safeLatest.goodMultiplier,
                safeLatest.easyMultiplier,
                safeLatest.lastAdjustedAtMillis,
                safeLatest.lastAdjustmentReviewCount
        ).withFrequencyRetention(frequencyRetentionEnabled, ranges);
        return SaveResult.valid(parameters);
    }

    public static final class SaveResult {
        public final boolean valid;
        public final RecordsSchedulerModels.SchedulerParameters parameters;
        public final String message;

        private SaveResult(boolean valid, RecordsSchedulerModels.SchedulerParameters parameters, String message) {
            this.valid = valid;
            this.parameters = parameters;
            this.message = message;
        }

        static SaveResult valid(RecordsSchedulerModels.SchedulerParameters parameters) {
            return new SaveResult(true, parameters, SAVED_MESSAGE);
        }

        static SaveResult invalid(String message) {
            return new SaveResult(false, null, message);
        }
    }
}
