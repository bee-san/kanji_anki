package dev.bee.kanjianki

import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.reminders.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderNotificationSettingsIntentTest {
    @Test
    fun channelSettingsTargetOnlyABlockedReminderChannelOnModernAndroid() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val channelSpecific = shouldOpenReminderChannelSettings(
            sdkInt = Build.VERSION_CODES.O,
            hasRuntimePermission = true,
            appNotificationsEnabled = true,
            channelImportance = android.app.NotificationManager.IMPORTANCE_NONE,
        )

        val intent = reminderNotificationSettingsIntent(context, channelSpecific, Build.VERSION_CODES.O)

        assertTrue(channelSpecific)
        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        assertEquals(ReminderScheduler.REMINDER_CHANNEL_ID, intent.getStringExtra(Settings.EXTRA_CHANNEL_ID))
    }

    @Test
    fun legacyAndroidFallsBackToAppNotificationSettings() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val intent = reminderNotificationSettingsIntent(context, true, Build.VERSION_CODES.N_MR1)

        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        assertNull(intent.getStringExtra(Settings.EXTRA_CHANNEL_ID))
    }

    @Test
    fun permissionAndAppWideBlocksOpenAppNotificationSettings() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val missingPermission = shouldOpenReminderChannelSettings(
            sdkInt = Build.VERSION_CODES.TIRAMISU,
            hasRuntimePermission = false,
            appNotificationsEnabled = true,
            channelImportance = android.app.NotificationManager.IMPORTANCE_NONE,
        )
        val appWideBlock = shouldOpenReminderChannelSettings(
            sdkInt = Build.VERSION_CODES.TIRAMISU,
            hasRuntimePermission = true,
            appNotificationsEnabled = false,
            channelImportance = android.app.NotificationManager.IMPORTANCE_NONE,
        )

        assertFalse(missingPermission)
        assertFalse(appWideBlock)
        assertEquals(
            Settings.ACTION_APP_NOTIFICATION_SETTINGS,
            reminderNotificationSettingsIntent(context, missingPermission).action,
        )
        assertEquals(
            Settings.ACTION_APP_NOTIFICATION_SETTINGS,
            reminderNotificationSettingsIntent(context, appWideBlock).action,
        )
    }
}
