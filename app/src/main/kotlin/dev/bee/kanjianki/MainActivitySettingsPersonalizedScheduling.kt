package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.core.FsrsPersonalizationTextCopy
import dev.bee.kanjianki.data.FsrsFitSummaryCodec
import dev.bee.kanjianki.fsrs.FsrsFitScheduler

internal class MainActivitySettingsPersonalizedScheduling(private val activity: MainActivitySettings) {
    fun panelModel(): SettingsPersonalizedSchedulingPanelModel {
        val enabled = activity.store.fsrsPersonalizationEnabled()
        val summary = FsrsFitSummaryCodec.decode(activity.store.fsrsFitSummaryJson())
        val hasLiveWeights = activity.store.schedulerFsrsWeights() != null
        return SettingsPersonalizedSchedulingPanelModel(
            title = FsrsPersonalizationTextCopy.title(),
            body = FsrsPersonalizationTextCopy.body(),
            status = FsrsPersonalizationTextCopy.status(
                enabled = enabled,
                adopted = hasLiveWeights,
                sampleCount = summary?.sampleCount ?: 0,
                relativeImprovement = summary?.takeIf { it.adopted }?.relativeImprovement(),
                // Turning the toggle off intentionally clears the live vector but
                // keeps the historical summary. If it is turned on again, do not
                // claim that cleared weights are active while the next fit is pending.
                reason = summary?.reason?.takeUnless { summary.adopted && !hasLiveWeights },
            ),
            state = SettingsPersonalizedSchedulingState(enabled),
            toggleLabel = FsrsPersonalizationTextCopy.toggleLabel(),
            fitNowLabel = FsrsPersonalizationTextCopy.fitNowLabel(),
            resetLabel = FsrsPersonalizationTextCopy.resetLabel(),
            onToggle = SettingsPersonalizedSchedulingToggleAction(::setEnabled),
            onFitNow = Runnable(::fitNow),
            onReset = Runnable(::reset),
        )
    }

    private fun setEnabled(enabled: Boolean) {
        activity.runSettingsWrite(
            traceSection = "kani.settings.fsrs-personalization.toggle",
            write = {
                activity.store.saveFsrsPersonalizationEnabled(enabled)
                FsrsFitScheduler.schedule(activity)
            },
        ) {
            Toast.makeText(
                activity,
                if (enabled) FsrsPersonalizationTextCopy.enabledToast() else FsrsPersonalizationTextCopy.disabledToast(),
                Toast.LENGTH_SHORT,
            ).show()
            activity.renderSettingsStudyBehavior(true)
        }
    }

    private fun fitNow() {
        if (!activity.store.fsrsPersonalizationEnabled()) {
            Toast.makeText(activity, FsrsPersonalizationTextCopy.turnOnFirstToast(), Toast.LENGTH_SHORT).show()
            return
        }
        activity.runSettingsWrite(
            traceSection = "kani.settings.fsrs-personalization.fit-now",
            write = { FsrsFitScheduler.fitNow(activity) },
        ) {
            Toast.makeText(activity, FsrsPersonalizationTextCopy.fitQueuedToast(), Toast.LENGTH_SHORT).show()
        }
    }

    private fun reset() {
        activity.runSettingsWrite(
            traceSection = "kani.settings.fsrs-personalization.reset",
            write = { activity.store.resetFsrsPersonalization() },
        ) {
            Toast.makeText(activity, FsrsPersonalizationTextCopy.resetToast(), Toast.LENGTH_SHORT).show()
            activity.renderSettingsStudyBehavior(true)
        }
    }
}
