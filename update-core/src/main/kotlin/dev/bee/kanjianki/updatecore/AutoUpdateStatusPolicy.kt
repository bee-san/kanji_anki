package dev.bee.kanjianki.updatecore

object AutoUpdateStatusPolicy {
    const val DEFAULT_LAST_RESULT = "No automatic update check has run yet."

    @JvmStatic
    fun normalize(
        enabled: Boolean,
        lastCheckAtMillis: Long,
        lastResult: String?,
        lastVersion: String?,
        pendingApkName: String?,
        pendingMessage: String?,
    ): StatusFields {
        return StatusFields(
            enabled,
            lastCheckAtMillis,
            text(lastResult),
            text(lastVersion),
            text(pendingApkName),
            text(pendingMessage),
        )
    }

    @JvmStatic
    fun hasPendingUpdate(pendingApkName: String?): Boolean {
        return text(pendingApkName).isNotEmpty()
    }

    @JvmStatic
    fun text(value: String?): String {
        return value ?: ""
    }

    class StatusFields(
        private val enabled: Boolean,
        private val lastCheckAtMillis: Long,
        private val lastResult: String,
        private val lastVersion: String,
        private val pendingApkName: String,
        private val pendingMessage: String,
    ) {
        fun enabled(): Boolean = enabled
        fun lastCheckAtMillis(): Long = lastCheckAtMillis
        fun lastResult(): String = lastResult
        fun lastVersion(): String = lastVersion
        fun pendingApkName(): String = pendingApkName
        fun pendingMessage(): String = pendingMessage
    }
}
