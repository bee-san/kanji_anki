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

data class SettingsReminderAntiSpamModel(
    val quietHoursLabel: String,
    val quietHoursBody: String,
    val quietStartLabel: String,
    val quietEndLabel: String,
    val maxPerDayLabel: String,
    val onPickQuietStart: SettingsReminderAction,
    val onPickQuietEnd: SettingsReminderAction,
    val onDecreaseMaxPerDay: SettingsReminderAction,
    val onIncreaseMaxPerDay: SettingsReminderAction,
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
    // Anti-spam controls (quiet hours + max reminders/day). Present only when the
    // reminder is enabled; null keeps the panel unchanged when the reminder is off.
    val antiSpam: SettingsReminderAntiSpamModel? = null,
) : SettingsPanelModel
