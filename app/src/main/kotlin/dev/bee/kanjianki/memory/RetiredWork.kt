package dev.bee.kanjianki.memory

import android.content.Context
import androidx.work.WorkManager
import dev.bee.kanjianki.AppDebugLog

/**
 * Background work earlier versions enqueued that no longer exists. The FSRS
 * weight fitter's worker class was removed with FSRS, so its unique periodic
 * and one-off requests are cancelled at startup instead of failing to start.
 */
internal object RetiredWork {
    /** The unique work names of the removed FSRS fitter (do not reuse). */
    val UNIQUE_WORK_NAMES: List<String> = listOf("kani_fsrs_fit", "kani_fsrs_fit_now")

    fun cancel(context: Context) {
        runCatching {
            val workManager = WorkManager.getInstance(context.applicationContext)
            UNIQUE_WORK_NAMES.forEach(workManager::cancelUniqueWork)
        }.onFailure { AppDebugLog.logError("cancelling retired FSRS fit work failed", it) }
    }
}
