package dev.bee.kanjianki.core

import java.util.Locale

object ReminderSettingsSavePolicy {
    const val DISABLED_MESSAGE: String = "Reminder turned off."
    const val NOTIFICATIONS_BLOCKED_MESSAGE: String = "Reminder saved, but Android notifications are off."
    const val PERMISSION_DENIED_MESSAGE: String =
        "Reminder saved. Allow notifications in Android settings to receive it."
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun disabledMessage(): String = localizedText(DISABLED_MESSAGE, "リマインダーをオフにしました。")

    @JvmStatic
    fun permissionDeniedMessage(): String = localizedText(
        PERMISSION_DENIED_MESSAGE,
        "リマインダーを保存しました。受け取るにはAndroidの設定で通知を許可してください。",
    )

    private fun notificationsBlockedMessage(): String = localizedText(
        NOTIFICATIONS_BLOCKED_MESSAGE,
        "リマインダーを保存しましたが、Androidの通知がオフです。",
    )

    private fun localizedText(english: String, japanese: String): String =
        if (Locale.getDefault().language == JAPANESE_LANGUAGE) japanese else english

    @JvmStatic
    fun fields(enabled: Boolean, hour: Int, minute: Int): ReminderFields {
        val normalized = TimeOfDaySettingsPolicy.normalizeReminder(enabled, hour, minute)
        return ReminderFields(normalized.enabled, normalized.hour, normalized.minute)
    }

    @JvmStatic
    fun savedMessage(hour: Int, minute: Int, notificationsAllowed: Boolean): String {
        if (!notificationsAllowed) {
            return notificationsBlockedMessage()
        }
        val timeText = TimeOfDaySettingsPolicy.displayTime(hour, minute)
        return localizedText(
            "Reminder saved for around $timeText.",
            "${timeText}頃にリマインダーを保存しました。",
        )
    }

    @JvmRecord
    data class ReminderFields(val enabled: Boolean, val hour: Int, val minute: Int) {
        override fun toString(): String {
            return "ReminderFields[enabled=$enabled, hour=$hour, minute=$minute]"
        }
    }
}
