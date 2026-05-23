package dev.bee.kanjianki.updatecore

object AutoUpdateSchedulePolicy {
    const val UNIQUE_WORK_NAME = "kani_daily_auto_updates"
    const val INTERVAL_MILLIS = 86_400_000L
    const val FLEX_MILLIS = 6L * 60L * 60L * 1000L

    @JvmStatic
    fun plan(enabled: Boolean): SchedulePlan {
        return SchedulePlan(enabled, UNIQUE_WORK_NAME, INTERVAL_MILLIS, FLEX_MILLIS, true)
    }

    class SchedulePlan(
        private val enabled: Boolean,
        private val uniqueWorkName: String,
        private val intervalMillis: Long,
        private val flexMillis: Long,
        private val requiresConnectedNetwork: Boolean,
    ) {
        fun enabled(): Boolean = enabled
        fun uniqueWorkName(): String = uniqueWorkName
        fun intervalMillis(): Long = intervalMillis
        fun flexMillis(): Long = flexMillis
        fun requiresConnectedNetwork(): Boolean = requiresConnectedNetwork
    }
}
