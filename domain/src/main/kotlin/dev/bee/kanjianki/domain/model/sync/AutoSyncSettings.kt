package dev.bee.kanjianki.domain.model.sync

import java.util.Locale

data class AutoSyncSettings(
    val configured: Boolean = false,
    val enabled: Boolean = false,
    val hour: Int = DEFAULT_HOUR,
    val minute: Int = DEFAULT_MINUTE,
    val lastAttemptAtMillis: Long = 0L,
    val lastSuccessAtMillis: Long = 0L,
    val nextRunAtMillis: Long = 0L,
) {
    init {
        require(hour in 0..23) { "hour must be between 0 and 23" }
        require(minute in 0..59) { "minute must be between 0 and 59" }
        require(lastAttemptAtMillis >= 0) { "lastAttemptAtMillis must be non-negative" }
        require(lastSuccessAtMillis >= 0) { "lastSuccessAtMillis must be non-negative" }
        require(nextRunAtMillis >= 0) { "nextRunAtMillis must be non-negative" }
        require(configured || !enabled) { "enabled auto-sync must be configured" }
    }

    fun displayTime(): String =
        String.format(Locale.ROOT, "%02d:%02d", hour, minute)

    companion object {
        const val DEFAULT_HOUR = 19
        const val DEFAULT_MINUTE = 0

        fun fromStored(
            configured: Boolean,
            enabled: Boolean,
            hour: Int,
            minute: Int,
            lastAttemptAtMillis: Long,
            lastSuccessAtMillis: Long,
            nextRunAtMillis: Long,
        ): AutoSyncSettings = AutoSyncSettings(
            configured = configured,
            enabled = configured && enabled,
            hour = hour.coerceIn(0, 23),
            minute = minute.coerceIn(0, 59),
            lastAttemptAtMillis = lastAttemptAtMillis.coerceAtLeast(0L),
            lastSuccessAtMillis = lastSuccessAtMillis.coerceAtLeast(0L),
            nextRunAtMillis = nextRunAtMillis.coerceAtLeast(0L),
        )
    }
}
