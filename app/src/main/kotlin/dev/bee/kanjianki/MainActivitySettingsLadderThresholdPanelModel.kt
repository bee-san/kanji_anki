package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.SettingsTextCopy

internal object SettingsLadderThresholdPanelModels {
    @JvmStatic
    fun create(
        promotionIntervalDays: Int,
        demotionFailStreak: Int,
        onSave: SettingsLadderThresholdSaveAction,
    ): SettingsLadderThresholdPanelModel {
        return SettingsLadderThresholdPanelModel(
            title = SettingsTextCopy.ladderThresholdsTitle(),
            body = SettingsTextCopy.ladderThresholdsBody(),
            promotionDaysLabel = SettingsTextCopy.fsrsDaysToGoUpLabel(),
            initialPromotionDaysText = thresholdText(promotionIntervalDays),
            failStreakLabel = SettingsTextCopy.failsToGoDownLabel(),
            initialFailStreakText = thresholdText(demotionFailStreak),
            defaultPromotionDaysText = RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS.toString(),
            defaultFailStreakText = RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK.toString(),
            defaultsLabel = SettingsTextCopy.useDefaultLadderThresholdsLabel(),
            saveLabel = SettingsTextCopy.saveLadderThresholdsLabel(),
            onSave = onSave,
        )
    }

    private fun thresholdText(value: Int): String = value.coerceAtLeast(1).toString()
}
