package dev.bee.kanjianki.core

class SchedulerTuner {
    fun maybeTune(
        current: RecordsSchedulerModels.SchedulerParameters?,
        stats: RecordsSchedulerModels.ReviewStats?,
        nowMillis: Long,
    ): RecordsSchedulerModels.SchedulerParameters {
        val schedulerParameters = current ?: RecordsSchedulerModels.SchedulerParameters.defaults()
        if (stats == null || stats.total < MIN_REVIEWS) {
            return schedulerParameters
        }
        if (
            schedulerParameters.lastAdjustedAtMillis > 0 &&
            nowMillis - schedulerParameters.lastAdjustedAtMillis < MONTH_MILLIS
        ) {
            return schedulerParameters
        }
        if (stats.total <= schedulerParameters.lastAdjustmentReviewCount) {
            return schedulerParameters
        }

        val retention = stats.retentionProxy()
        val error = schedulerParameters.targetRetention - retention
        val spacingFactor = if (Math.abs(error) < 0.03) {
            1.0
        } else if (error > 0) {
            if (error > 0.10) 0.84 else 0.92
        } else {
            if (retention >= schedulerParameters.targetRetention + 0.10) 1.12 else 1.06
        }

        val writingPenalty = if (stats.writingFailureRate() > 0.25) 0.94 else 1.0
        return schedulerParameters.withAdjustment(
            schedulerParameters.againMultiplier * if (error > 0) 0.92 else 1.02,
            schedulerParameters.hardMultiplier * spacingFactor,
            schedulerParameters.goodMultiplier * spacingFactor * writingPenalty,
            schedulerParameters.easyMultiplier * spacingFactor * writingPenalty,
            nowMillis,
            stats.total,
        )
    }

    companion object {
        const val MONTH_MILLIS: Long = 30L * 86_400_000L
        private const val MIN_REVIEWS = 20
    }
}
