package dev.bee.kanjianki.core;

public final class WorkloadSettingsPolicy {
    public static final String MAXIMUM_SAVED_MESSAGE = "Pareto maximum saved.";
    public static final String MANUAL_ENABLED_MESSAGE = "Manual workload enabled.";
    public static final String AUTOMATIC_ENABLED_MESSAGE = "Automatic Pareto workload enabled.";
    public static final String WORKLOAD_SAVED_MESSAGE = "Workload saved. Study uses the new adaptive focus.";

    private WorkloadSettingsPolicy() {
    }

    public static SaveRequest saveMaximum(int maxItems) {
        return new SaveRequest(null, null, AdaptiveLoadPlanner.normalizeMaxItems(maxItems), MAXIMUM_SAVED_MESSAGE);
    }

    public static SaveRequest enableManualMode() {
        return new SaveRequest(AdaptiveLoadPlanner.MODE_MANUAL, null, null, MANUAL_ENABLED_MESSAGE);
    }

    public static SaveRequest enableAutomaticMode() {
        return new SaveRequest(AdaptiveLoadPlanner.MODE_AUTO, null, null, AUTOMATIC_ENABLED_MESSAGE);
    }

    public static SaveRequest saveManualWorkload(int workloadPercent, int maxItems) {
        return new SaveRequest(
                AdaptiveLoadPlanner.MODE_MANUAL,
                AdaptiveLoadPlanner.snapWorkloadPercent(workloadPercent),
                AdaptiveLoadPlanner.normalizeMaxItems(maxItems),
                WORKLOAD_SAVED_MESSAGE
        );
    }

    public static final class SaveRequest {
        public final String mode;
        public final Integer workloadPercent;
        public final Integer maxItems;
        public final String message;

        private SaveRequest(String mode, Integer workloadPercent, Integer maxItems, String message) {
            this.mode = mode;
            this.workloadPercent = workloadPercent;
            this.maxItems = maxItems;
            this.message = message;
        }
    }
}
