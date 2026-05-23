package dev.bee.kanjianki

import dev.bee.kanjianki.data.LocalStore

internal class MainActivityLifecycle(private val activity: MainActivityBase) {
    fun onPause() {
        activity.pauseActiveStudyTask()
        activity.activityPaused = true
    }

    fun onResume() {
        activity.activityPaused = false
        activity.resumeActiveStudyTask()
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
        return try {
            activity.store
        } catch (_: NullPointerException) {
            null
        }
    }
}
