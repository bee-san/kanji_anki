package dev.bee.kanjianki

fun interface SettingsStudyLadderAction {
    fun run()
}

data class SettingsStudyLadderPanelModel(
    val title: String,
    val body: String,
    val rungs: List<SettingsStudyLadderRungModel>,
    val restoreLabel: String,
    val restoreDescription: String,
    val onRestore: SettingsStudyLadderAction,
) : SettingsPanelModel

data class SettingsStudyLadderRungModel(
    val label: String,
    val subtitle: String,
    val toggleLabel: String,
    val moveUpLabel: String,
    val moveDownLabel: String,
    val canMoveUp: Boolean,
    val canMoveDown: Boolean,
    val toggleDescription: String,
    val moveUpDescription: String,
    val moveDownDescription: String,
    val onToggle: SettingsStudyLadderAction,
    val onMoveUp: SettingsStudyLadderAction,
    val onMoveDown: SettingsStudyLadderAction,
)
