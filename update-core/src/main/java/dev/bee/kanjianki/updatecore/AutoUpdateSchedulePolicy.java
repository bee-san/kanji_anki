package dev.bee.kanjianki.updatecore;

public final class AutoUpdateSchedulePolicy {
    public static final String UNIQUE_WORK_NAME = "kani_daily_auto_updates";
    public static final long INTERVAL_MILLIS = 86_400_000L;
    public static final long FLEX_MILLIS = 6L * 60L * 60L * 1000L;

    private AutoUpdateSchedulePolicy() {
    }

    public static SchedulePlan plan(boolean enabled) {
        return new SchedulePlan(enabled, UNIQUE_WORK_NAME, INTERVAL_MILLIS, FLEX_MILLIS, true);
    }

    public record SchedulePlan(
            boolean enabled,
            String uniqueWorkName,
            long intervalMillis,
            long flexMillis,
            boolean requiresConnectedNetwork
    ) {
    }
}
