package dev.bee.kanjianki.core

internal object CeilingParkingPolicy {
    fun isPastThreshold(intervalDays: Int, settings: RecordsSyncModels.Settings): Boolean {
        val thresholdDays = maxOf(
            settings.matureDays,
            saturatingMultiplyNonNegative(
                settings.ladderPromotionIntervalDays,
                RecordsBase.CEILING_PARK_INTERVAL_MULTIPLIER,
            ),
        )
        return intervalDays > thresholdDays
    }
}
