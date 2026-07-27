package dev.bee.kanjianki

import android.os.SystemClock
import android.util.Log
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.reminders.ReminderScheduler
import dev.bee.kanjianki.update.GitHubUpdater
import dev.bee.kanjianki.update.ResumeUpdateInstaller
import java.util.Locale

internal class MainActivityLifecycle(private val activity: MainActivityBase) {
    // Guards the resume re-arm so opening/returning to the app repeatedly does not
    // spam the scheduler; the notification cancel below is cheap and always runs.
    @Volatile
    private var lastResumeRearmAtMillis: Long = 0L
    private val reminderRearmCoordinator by lazy {
        MainActivityReminderRearmCoordinator(
            executor = activity.maintenance,
            rearm = ::runReminderRearm,
            onDispatchError = { error ->
                Log.e(LOG_TAG, "Could not dispatch reminder re-arm", error)
                AppDebugLog.logError("reminder rearm dispatch failed", error)
            },
        )
    }
    private val resumeUpdateInstaller by lazy {
        ResumeUpdateInstaller(
            { canRequestPackageInstalls(activity) },
            { activity.store.autoUpdateStatus() },
            activity.maintenance,
        ) {
            GitHubUpdater(activity).installCachedPendingUpdate(GitHubUpdater.UpdateSource.CACHED)
        }
    }

    fun onPause() {
        activity.pauseActiveStudyTask()
        activity.activityPaused = true
    }

    fun onResume() {
        activity.activityPaused = false
        activity.resumeActiveStudyTask()
        activity.renderDeferredStudyBehaviorPreviewIfNeeded()
        refreshReminderNotificationSettingsIfNeeded()
        installPendingUpdateIfReady()
        onAppOpened()
    }

    private fun refreshReminderNotificationSettingsIfNeeded() {
        if (!activity.reminderNotificationSettingsRefreshPending) {
            return
        }
        activity.reminderNotificationSettingsRefreshPending = false
        ReminderScheduler.ensureNotificationChannel(activity)
        if (
            activity is MainActivitySettings &&
            activity.currentRoute == MainActivityBase.NAV_SETTINGS_AUTOMATION_ROUTE
        ) {
            activity.renderSettingsAutomation(true)
        }
        requestReminderRearm(REASON_NOTIFICATION_SETTINGS)
    }

    /**
     * Opening the app is the ultimate "I got the message": clear any posted
     * reminder (slot 2702) and re-arm the alarm from fresh state so scheduling
     * reflects that the user is here now (D7). The re-arm is throttled to keep
     * resume cheap and off the main thread.
     */
    internal fun onAppOpened() {
        if (storeOrNull() == null) {
            return
        }
        ReminderScheduler.cancelPostedNotification(activity)
        if (!MainActivityStartup.backgroundStartupTasksAllowed(activity.intent)) {
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastResumeRearmAtMillis < RESUME_REARM_THROTTLE_MILLIS) {
            return
        }
        lastResumeRearmAtMillis = now
        requestReminderRearm(REASON_RESUME)
    }

    /**
     * Request a fresh reminder decision without competing with the route the user is waiting for.
     * Calls are coalesced on [MainActivityBase.maintenance] and use the process store so a Home
     * load's warm dashboard cache can be reused across Activity recreation.
     */
    internal fun requestReminderRearm(reason: String) {
        if (storeOrNull() == null || !MainActivityStartup.backgroundStartupTasksAllowed(activity.intent)) {
            return
        }
        if (AppDebugLog.isCapturing()) {
            AppDebugLog.log("reminder rearm requested reason=${traceToken(reason)}")
        }
        reminderRearmCoordinator.request(reason)
    }

    internal fun onAsyncRouteRequested(requestId: Int, route: String) {
        if (!MainActivityStartup.backgroundStartupTasksAllowed(activity.intent)) {
            return
        }
        reminderRearmCoordinator.routeRequested(requestId)
        if (AppDebugLog.isCapturing()) {
            AppDebugLog.log("route requested request_id=$requestId route=${traceToken(route)}")
        }
    }

    internal fun onAsyncRouteCanceled(requestId: Int) {
        if (!MainActivityStartup.backgroundStartupTasksAllowed(activity.intent)) {
            return
        }
        val accepted = reminderRearmCoordinator.routeCanceled(requestId)
        if (AppDebugLog.isCapturing()) {
            AppDebugLog.log("route canceled request_id=$requestId accepted=$accepted")
        }
    }

    internal fun onAsyncRouteSettled(requestId: Int, route: String, succeeded: Boolean) {
        if (!MainActivityStartup.backgroundStartupTasksAllowed(activity.intent)) {
            return
        }
        val accepted = reminderRearmCoordinator.routeSettled(requestId)
        if (AppDebugLog.isCapturing()) {
            AppDebugLog.log(
                "route settled request_id=$requestId route=${traceToken(route)} " +
                    "status=${if (succeeded) "success" else "error"} accepted=$accepted",
            )
        }
    }

    private fun runReminderRearm(reasons: Set<String>) {
        val reason = reasons.joinToString(",") { traceToken(it) }.ifEmpty { "unspecified" }
        val startNanos = monotonicNanos()
        if (AppDebugLog.isCapturing()) {
            AppDebugLog.log("reminder rearm start reason=$reason")
        }
        try {
            withUiTrace("kani.reminder.rearm") {
                ReminderScheduler.schedule(activity, activity.store)
            }
            if (AppDebugLog.isCapturing()) {
                AppDebugLog.log(
                    String.format(
                        Locale.US,
                        "reminder rearm complete reason=%s duration_ms=%.2f",
                        reason,
                        (monotonicNanos() - startNanos) / 1_000_000.0,
                    ),
                )
            }
        } catch (error: RuntimeException) {
            Log.e(LOG_TAG, "Reminder re-arm failed ($reason)", error)
            AppDebugLog.logError(
                String.format(
                    Locale.US,
                    "reminder rearm failed reason=%s duration_ms=%.2f",
                    reason,
                    (monotonicNanos() - startNanos) / 1_000_000.0,
                ),
                error,
            )
        }
    }

    internal fun installPendingUpdateIfReady() {
        if (storeOrNull() == null) {
            return
        }
        if (!MainActivityStartup.backgroundStartupTasksAllowed(activity.intent)) {
            return
        }
        resumeUpdateInstaller.onResume()
    }

    fun onDestroy() {
        val recognizer = activity.writingRecognizer
        if (recognizer != null && recognizer !== MainActivityRuntimeOverrides.writingRecognizer) {
            recognizer.close()
        }
    }

    private fun storeOrNull(): LocalStore? {
        return if (activity.isStoreInitialized()) activity.store else null
    }

    private companion object {
        const val LOG_TAG = "KaniReminder"
        const val REASON_NOTIFICATION_SETTINGS = "notification-settings"
        const val REASON_RESUME = "resume"
        const val RESUME_REARM_THROTTLE_MILLIS = 3L * 60L * 1000L

        fun monotonicNanos(): Long {
            return runCatching { SystemClock.elapsedRealtimeNanos() }.getOrDefault(System.nanoTime())
        }
    }
}
