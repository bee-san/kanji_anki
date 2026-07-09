package dev.bee.kanjianki

import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.reminders.ReminderScheduler
import dev.bee.kanjianki.update.GitHubUpdater
import dev.bee.kanjianki.update.ResumeUpdateInstaller

internal class MainActivityLifecycle(private val activity: MainActivityBase) {
    // Guards the resume re-arm so opening/returning to the app repeatedly does not
    // spam the scheduler; the notification cancel below is cheap and always runs.
    @Volatile
    private var lastResumeRearmAtMillis: Long = 0L
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
        installPendingUpdateIfReady()
        onAppOpened()
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
        val now = System.currentTimeMillis()
        if (now - lastResumeRearmAtMillis < RESUME_REARM_THROTTLE_MILLIS) {
            return
        }
        lastResumeRearmAtMillis = now
        activity.maintenance.execute {
            ReminderScheduler.schedule(activity)
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
        activity.io.shutdownNow()
        activity.maintenance.shutdownNow()
        val recognizer = activity.writingRecognizer
        if (recognizer != null && recognizer !== MainActivityRuntimeOverrides.writingRecognizer) {
            recognizer.close()
        }
        if (storeOrNull() != null) {
            activity.store.close()
        }
    }

    private fun storeOrNull(): LocalStore? {
        return if (activity.isStoreInitialized()) activity.store else null
    }

    private companion object {
        const val RESUME_REARM_THROTTLE_MILLIS = 3L * 60L * 1000L
    }
}
