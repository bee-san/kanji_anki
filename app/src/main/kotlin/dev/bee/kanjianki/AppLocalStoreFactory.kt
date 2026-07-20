package dev.bee.kanjianki

import android.content.Context
import dev.bee.kanjianki.data.DiagnosticLogger
import dev.bee.kanjianki.data.LocalStore

internal object AppLocalStoreFactory {
    fun create(context: Context?): LocalStore = LocalStore(context, AppDiagnosticLogger)
}

private object AppDiagnosticLogger : DiagnosticLogger {
    override fun isCapturing(): Boolean = AppDebugLog.isCapturing()

    override fun log(message: String) {
        AppDebugLog.log(message)
    }

    override fun traceStudyLoad(message: String) {
        studyLoadDebug(message)
    }
}
