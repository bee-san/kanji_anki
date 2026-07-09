package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SettingsTextCopy

internal object SettingsDeckLimitsPanelModels {
    @JvmStatic
    fun create(
        current: RecordsSyncModels.Settings,
        onSave: SettingsDeckLimitsSaveAction,
    ): SettingsDeckLimitsPanelModel {
        return SettingsDeckLimitsPanelModel(
            title = SettingsTextCopy.deckLimitsTitle(),
            body = SettingsTextCopy.deckLimitsBody(),
            newPerDayLabel = SettingsTextCopy.newCardsPerDayLabel(),
            initialNewPerDayText = current.newPerDay.toString(),
            activeQueueCapLabel = SettingsTextCopy.activeQueueCapLabel(),
            initialActiveQueueCapText = current.activeQueueCap.toString(),
            saveLabel = SettingsTextCopy.saveDeckLimitsLabel(),
            onSave = onSave,
        )
    }
}
