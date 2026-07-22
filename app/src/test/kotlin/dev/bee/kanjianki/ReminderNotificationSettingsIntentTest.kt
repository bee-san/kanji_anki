package dev.bee.kanjianki

import android.os.Build
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.reminders.ReminderScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ReminderNotificationSettingsIntentTest {
    @Test
    fun channelSettingsTargetOnlyReminderNotificationsOnModernAndroid() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val intent = reminderNotificationSettingsIntent(context, Build.VERSION_CODES.O)

        assertEquals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        assertEquals(ReminderScheduler.REMINDER_CHANNEL_ID, intent.getStringExtra(Settings.EXTRA_CHANNEL_ID))
    }

    @Test
    fun legacyAndroidFallsBackToAppNotificationSettings() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        val intent = reminderNotificationSettingsIntent(context, Build.VERSION_CODES.N_MR1)

        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
        assertNull(intent.getStringExtra(Settings.EXTRA_CHANNEL_ID))
    }
}
