package dev.bee.kanjianki

import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.update.GitHubUpdater
import dev.bee.kanjianki.update.ResumeUpdateInstaller

internal class MainActivityLifecycle(private val activity: MainActivityBase) {
    private val resumeUpdateInstaller by lazy {
        ResumeUpdateInstaller(
            { canRequestPackageInstalls(activity) },
            { activity.store.autoUpdateStatus() },
            activity.io,
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
}
