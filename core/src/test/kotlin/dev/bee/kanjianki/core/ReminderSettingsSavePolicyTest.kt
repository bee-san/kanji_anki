package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ReminderSettingsSavePolicyTest {
    @Test
    fun fieldsNormalizeReminderTimeBounds() {
        val fields = ReminderSettingsSavePolicy.fields(true, 30, -4)

        assertTrue(fields.enabled)
        assertEquals(23, fields.hour)
        assertEquals(0, fields.minute)
        assertTrue(ReminderSettingsSavePolicy.ReminderFields::class.java.isRecord)
        assertEquals("ReminderFields[enabled=true, hour=23, minute=0]", fields.toString())
    }

    @Test
    fun fieldsPreserveDisabledState() {
        val fields = ReminderSettingsSavePolicy.fields(false, 8, 30)

        assertFalse(fields.enabled)
        assertEquals(8, fields.hour)
        assertEquals(30, fields.minute)
    }

    @Test
    fun savedMessagePreservesNotificationAwareCopy() {
        assertEquals("Reminder saved for around 08:05.", ReminderSettingsSavePolicy.savedMessage(8, 5, true))
        assertEquals(
            "Reminder saved, but Android notifications are off.",
            ReminderSettingsSavePolicy.savedMessage(8, 5, false),
        )
    }

    @Test
    fun savedMessageAndPermissionCopyTranslateToJapaneseLocale() {
        withLocale(Locale.JAPANESE) {
            assertEquals("08:05頃にリマインダーを保存しました。", ReminderSettingsSavePolicy.savedMessage(8, 5, true))
            assertEquals(
                "リマインダーを保存しましたが、Androidの通知がオフです。",
                ReminderSettingsSavePolicy.savedMessage(8, 5, false),
            )
            assertEquals("リマインダーをオフにしました。", ReminderSettingsSavePolicy.disabledMessage())
            assertEquals(
                "リマインダーを保存しました。受け取るにはAndroidの設定で通知を許可してください。",
                ReminderSettingsSavePolicy.permissionDeniedMessage(),
            )
        }
    }

    @Test
    fun disabledAndDeniedCopyIsCentralized() {
        assertEquals("Reminder turned off.", ReminderSettingsSavePolicy.DISABLED_MESSAGE)
        assertEquals("Reminder turned off.", ReminderSettingsSavePolicy.disabledMessage())
        assertEquals(
            "Reminder saved. Allow notifications in Android settings to receive it.",
            ReminderSettingsSavePolicy.PERMISSION_DENIED_MESSAGE,
        )
        assertEquals(
            "Reminder saved. Allow notifications in Android settings to receive it.",
            ReminderSettingsSavePolicy.permissionDeniedMessage(),
        )
    }

    private inline fun <T> withLocale(locale: Locale, block: () -> T): T {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        return try {
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }
}
