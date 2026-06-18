package dev.bee.kanjianki

import android.widget.Toast
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.core.StudyLadderThresholdPolicy

internal class MainActivitySettingsLadderThresholdPanel(private val activity: MainActivitySettings) {
    fun ladderThresholdSettingsPanelModel(): SettingsLadderThresholdPanelModel {
        val current = activity.settings()
        return SettingsLadderThresholdPanelModels.create(
            promotionIntervalDays = current.ladderPromotionIntervalDays,
            demotionFailStreak = current.ladderDemotionFailStreak,
            onSave = SettingsLadderThresholdSaveAction { promotionDaysText, failStreakText ->
                saveLadderThresholds(promotionDaysText, failStreakText)
            },
        )
    }

    private fun saveLadderThresholds(promotionDaysText: String, failStreakText: String) {
        val request = StudyLadderThresholdPolicy.saveRequest(promotionDaysText, failStreakText)
        if (!request.valid) {
            Toast.makeText(activity, request.message, Toast.LENGTH_SHORT).show()
            return
        }
        activity.runSettingsWrite(
            traceSection = "kani.settings.ladder-threshold.save",
            write = {
                SettingsWriteActions.saveLadderThresholds(request, activity.store::putIntSetting)
            },
        ) {
            Toast.makeText(activity, SettingsTextCopy.ladderThresholdsSavedToast(), Toast.LENGTH_SHORT).show()
            activity.renderSettingsStudyBehavior(true)
        }
    }
}
