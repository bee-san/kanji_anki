package dev.bee.kanjianki.core;

public final class SchedulerTuner {
    public static final long MONTH_MILLIS = 30L * 86_400_000L;
    private static final int MIN_REVIEWS = 20;

    public RecordsSchedulerModels.SchedulerParameters maybeTune(
            RecordsSchedulerModels.SchedulerParameters current,
            RecordsSchedulerModels.ReviewStats stats,
            long nowMillis
    ) {
        if (current == null) {
            current = RecordsSchedulerModels.SchedulerParameters.defaults();
        }
        if (stats == null || stats.total < MIN_REVIEWS) {
            return current;
        }
        if (current.lastAdjustedAtMillis > 0 && nowMillis - current.lastAdjustedAtMillis < MONTH_MILLIS) {
            return current;
        }
        if (stats.total <= current.lastAdjustmentReviewCount) {
            return current;
        }

        double retention = stats.retentionProxy();
        double error = current.targetRetention - retention;
        double spacingFactor;
        if (Math.abs(error) < 0.03) {
            spacingFactor = 1.0;
        } else if (error > 0) {
            spacingFactor = error > 0.10 ? 0.84 : 0.92;
        } else {
            spacingFactor = retention >= current.targetRetention + 0.10 ? 1.12 : 1.06;
        }

        double writingPenalty = stats.writingFailureRate() > 0.25 ? 0.94 : 1.0;
        return current.withAdjustment(
                current.againMultiplier * (error > 0 ? 0.92 : 1.02),
                current.hardMultiplier * spacingFactor,
                current.goodMultiplier * spacingFactor * writingPenalty,
                current.easyMultiplier * spacingFactor * writingPenalty,
                nowMillis,
                stats.total
        );
    }
}
