package dev.bee.kanjianki

fun interface SettingsLadderThresholdSaveAction {
    fun save(promotionDaysText: String, failStreakText: String)
}

data class SettingsLadderThresholdPanelModel(
    val title: String,
    val body: String,
    val promotionDaysLabel: String,
    val initialPromotionDaysText: String,
    val failStreakLabel: String,
    val initialFailStreakText: String,
    val defaultPromotionDaysText: String,
    val defaultFailStreakText: String,
    val defaultsLabel: String,
    val saveLabel: String,
    val onSave: SettingsLadderThresholdSaveAction,
) : SettingsPanelModel
