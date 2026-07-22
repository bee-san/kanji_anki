package dev.bee.kanjianki.update

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.MainActivityBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdateNotifierAndroidTest {
    @Test
    fun updateNotificationOpensTheUpdaterInTheExistingTask() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val intent = UpdateNotifier.updateOpenIntent(context)

        assertTrue(intent.getBooleanExtra(MainActivityBase.EXTRA_OPEN_UPDATE, false))
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(intent.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }

    @Test
    fun postedUpdateUsesStatusMetadataAndCanBeCancelled() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val controller = UpdateNotifier.AndroidNotificationController(context, 35)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        assertTrue(controller.ensureChannel("App updates", "Friendly Kani update prompts."))
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, controller.channelImportance())
        assertTrue(controller.notifyUpdate("Kani update ready", "Ready"))

        val notification = shadowOf(manager).allNotifications.single()
        assertEquals(Notification.CATEGORY_STATUS, notification.category)
        assertTrue(UpdateNotifier.cancelPendingUpdate(context))
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }
}
