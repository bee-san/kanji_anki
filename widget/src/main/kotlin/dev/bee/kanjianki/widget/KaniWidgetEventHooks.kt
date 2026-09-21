package dev.bee.kanjianki.widget

import android.content.Context

class KaniWidgetEventHooks(
    private val refresh: (Context?) -> Unit = KaniWidgetUpdater::requestUpdate,
) {
    fun themeWriteCompleted(context: Context) {
        refresh(context)
    }

    /**
     * Refreshes after a restore, when one was actually applied.
     *
     * Takes a Boolean rather than the applier's result enum: that enum is the composition
     * root's, and the only thing this needed from it was `== APPLIED`. Narrowing it is what
     * lets this module stop depending on `:app` — and it reads better at the call site, which
     * is the one place that knows what "applied" means.
     */
    fun restoreCompleted(context: Context, applied: Boolean) {
        if (applied) refresh(context)
    }

    companion object {
        val DEFAULT = KaniWidgetEventHooks()
    }
}
