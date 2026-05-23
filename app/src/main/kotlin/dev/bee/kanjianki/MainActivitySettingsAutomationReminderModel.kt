package dev.bee.kanjianki

fun interface SettingsReminderAction {
    fun run()
}

fun interface SettingsReminderSelectedTimeAction {
    fun select(hour: Int, minute: Int)
}

fun interface SettingsReminderTimePickerAction {
    fun pick(hour: Int, minute: Int, onSelected: SettingsReminderSelectedTimeAction)
}

data class SettingsReminderPresetModel(
    val label: String,
    val hour: Int,
    val minute: Int,
)

data class SettingsReminderPanelModel(
    val title: String,
    val status: String,
    val statusColor: Int,
    val body: String,
    val selectedHour: IntArray,
    val selectedMinute: IntArray,
    val presets: List<SettingsReminderPresetModel>,
    val saveLabel: String,
    val turnOffLabel: String?,
    val warning: String?,
    val notificationSettingsLabel: String?,
    val onPickTime: SettingsReminderTimePickerAction,
    val onSave: SettingsReminderAction,
    val onTurnOff: SettingsReminderAction?,
    val onOpenNotificationSettings: SettingsReminderAction?,
) : SettingsPanelModel
