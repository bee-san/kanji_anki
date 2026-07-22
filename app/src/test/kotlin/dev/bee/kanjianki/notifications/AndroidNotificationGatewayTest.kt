package dev.bee.kanjianki.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidNotificationGatewayTest {
    @Test
    fun runtimePermissionUsesThePlatformGate() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pre33 = RecordingContext(context, PackageManager.PERMISSION_DENIED)
        val denied = RecordingContext(context, PackageManager.PERMISSION_DENIED)
        val granted = RecordingContext(context, PackageManager.PERMISSION_GRANTED)

        assertTrue(AndroidNotificationGateway(pre33, 32).hasRuntimePermission())
        assertEquals(0, pre33.permissionChecks)
        assertFalse(AndroidNotificationGateway(denied, 35).hasRuntimePermission())
        assertTrue(AndroidNotificationGateway(granted, 35).hasRuntimePermission())
        assertEquals(1, denied.permissionChecks)
        assertEquals(1, granted.permissionChecks)
    }

    @Test
    fun missingManagerRejectsEveryNotificationOperation() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val gateway = AndroidNotificationGateway(
            RecordingContext(context, PackageManager.PERMISSION_GRANTED, hideNotificationManager = true),
            35,
        )

        assertFalse(gateway.hasManager())
        assertFalse(gateway.areNotificationsEnabled())
        assertNull(gateway.channelImportance(CHANNEL_ID))
        assertFalse(gateway.ensureChannel(CHANNEL_ID, "Test", "Test notifications", 3))
        assertFalse(gateway.post(1, Notification()))
        assertFalse(gateway.cancel(1))
    }

    @Test
    fun managerBackedOperationsCreatePostAndCancel() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val context = RecordingContext(base, PackageManager.PERMISSION_GRANTED)
        val gateway = AndroidNotificationGateway(context, 35)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        assertTrue(gateway.hasManager())
        assertTrue(gateway.areNotificationsEnabled())
        assertNull(gateway.channelImportance(CHANNEL_ID))
        assertTrue(gateway.ensureChannel(CHANNEL_ID, "Test", "Test notifications", NotificationManager.IMPORTANCE_DEFAULT))
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, gateway.channelImportance(CHANNEL_ID))

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Test")
            .build()
        assertTrue(gateway.post(1, notification))
        assertEquals(1, shadowOf(manager).allNotifications.size)
        assertTrue(gateway.cancel(1))
        assertTrue(shadowOf(manager).allNotifications.isEmpty())
    }

    private class RecordingContext(
        base: Context,
        private val permissionResult: Int,
        private val hideNotificationManager: Boolean = false,
    ) : ContextWrapper(base) {
        var permissionChecks = 0

        override fun getApplicationContext(): Context = this

        override fun checkSelfPermission(permission: String): Int {
            permissionChecks++
            return permissionResult
        }

        override fun getSystemService(name: String): Any? {
            if (hideNotificationManager && name == Context.NOTIFICATION_SERVICE) {
                return null
            }
            return super.getSystemService(name)
        }
    }

    private companion object {
        const val CHANNEL_ID = "gateway-test"
    }
}
