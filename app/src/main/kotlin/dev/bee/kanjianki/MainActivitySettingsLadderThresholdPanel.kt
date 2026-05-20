package dev.bee.kanjianki

import android.view.View
import android.widget.Toast
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.SettingsTextCopy
import dev.bee.kanjianki.core.StudyLadderThresholdPolicy

internal class MainActivitySettingsLadderThresholdPanel(private val activity: MainActivitySettings) {
    fun ladderThresholdSettingsPanel(): View {
        return ladderThresholdSettingsPanelView(activity, ladderThresholdSettingsPanelModel())
    }

    fun ladderThresholdSettingsPanelModel(): SettingsLadderThresholdPanelModel {
        val current = activity.settings()
        return SettingsLadderThresholdPanelModel(
            title = SettingsTextCopy.ladderThresholdsTitle(),
            body = SettingsTextCopy.ladderThresholdsBody(),
            promotionDaysLabel = SettingsTextCopy.fsrsDaysToGoUpLabel(),
            initialPromotionDaysText = thresholdText(current.ladderPromotionIntervalDays),
            failStreakLabel = SettingsTextCopy.failsToGoDownLabel(),
            initialFailStreakText = thresholdText(current.ladderDemotionFailStreak),
            defaultPromotionDaysText = RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS.toString(),
            defaultFailStreakText = RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK.toString(),
            defaultsLabel = SettingsTextCopy.useDefaultLadderThresholdsLabel(),
            saveLabel = SettingsTextCopy.saveLadderThresholdsLabel(),
            onSave = SettingsLadderThresholdSaveAction { promotionDaysText, failStreakText ->
                saveLadderThresholds(promotionDaysText, failStreakText)
            }
        )
    }

    private fun saveLadderThresholds(promotionDaysText: String, failStreakText: String) {
        val request = StudyLadderThresholdPolicy.saveRequest(promotionDaysText, failStreakText)
        if (!request.valid) {
            Toast.makeText(activity, request.message, Toast.LENGTH_SHORT).show()
            return
        }
        SettingsWriteActions.saveLadderThresholds(request, activity.store::putIntSetting)
        Toast.makeText(activity, SettingsTextCopy.ladderThresholdsSavedToast(), Toast.LENGTH_SHORT).show()
        activity.renderSettings()
    }

    private companion object {
        fun thresholdText(value: Int): String = value.coerceAtLeast(1).toString()
    }
}
