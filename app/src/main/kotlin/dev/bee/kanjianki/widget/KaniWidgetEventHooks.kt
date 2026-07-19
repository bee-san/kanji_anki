package dev.bee.kanjianki.widget

import android.content.Context
import dev.bee.kanjianki.backup.StagedRestoreApplier

internal class KaniWidgetEventHooks(
    private val refresh: (Context?) -> Unit = KaniWidgetUpdater::requestUpdate,
) {
    fun themeWriteCompleted(context: Context) {
        refresh(context)
    }

    fun restoreCompleted(context: Context, result: StagedRestoreApplier.Result) {
        if (result == StagedRestoreApplier.Result.APPLIED) refresh(context)
    }

    companion object {
        val DEFAULT = KaniWidgetEventHooks()
    }
}
