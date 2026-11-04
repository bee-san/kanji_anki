package dev.bee.kanjianki.platform

fun interface PlatformSubscription : AutoCloseable {
    override fun close()
}

enum class AppLifecycleState {
    FOREGROUND,
    BACKGROUND,
    STOPPING,
}

interface AppLifecycle {
    fun currentState(): AppLifecycleState

    fun observe(observer: (AppLifecycleState) -> Unit): PlatformSubscription
}

enum class BackgroundTaskKind {
    AUTO_SYNC,
    REMINDER,
    FSRS_FIT,
    UPDATE_CHECK,
}

data class BackgroundTaskRequest(
    val kind: BackgroundTaskKind,
    val earliestRunAtMillis: Long,
    val repeatIntervalMillis: Long? = null,
    val requiresNetwork: Boolean = false,
) {
    init {
        require(earliestRunAtMillis >= 0L) { "background run time must not be negative" }
        require(repeatIntervalMillis == null || repeatIntervalMillis > 0L) {
            "background repeat interval must be positive"
        }
    }
}

interface BackgroundScheduler {
    fun schedule(request: BackgroundTaskRequest): Boolean

    fun cancel(kind: BackgroundTaskKind): Boolean
}

enum class AppEventType {
    SYNC_COMMITTED,
    STUDY_COMMITTED,
    REMINDER_EVALUATED,
    SETTINGS_CHANGED,
    UPDATE_READY,
}

data class AppEvent(
    val type: AppEventType,
    val occurredAtMillis: Long,
) {
    init {
        require(occurredAtMillis >= 0L) { "event time must not be negative" }
    }
}

interface AppEventBus {
    fun publish(event: AppEvent)

    fun observe(observer: (AppEvent) -> Unit): PlatformSubscription
}
