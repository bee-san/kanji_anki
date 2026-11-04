package dev.bee.kanjianki.platform

enum class AppLogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR,
}

data class AppLogEvent(
    val level: AppLogLevel,
    val message: String,
    val cause: Throwable? = null,
)

fun interface AppLogger {
    fun log(event: AppLogEvent)

    companion object {
        @JvmField
        val NONE: AppLogger = AppLogger { }
    }
}

fun AppLogger.debug(message: String) {
    log(AppLogEvent(AppLogLevel.DEBUG, message))
}

fun AppLogger.info(message: String) {
    log(AppLogEvent(AppLogLevel.INFO, message))
}

fun AppLogger.warning(message: String, cause: Throwable? = null) {
    log(AppLogEvent(AppLogLevel.WARNING, message, cause))
}

fun AppLogger.error(message: String, cause: Throwable? = null) {
    log(AppLogEvent(AppLogLevel.ERROR, message, cause))
}
