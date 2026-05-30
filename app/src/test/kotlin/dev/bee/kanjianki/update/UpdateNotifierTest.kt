package dev.bee.kanjianki.update

import android.app.NotificationManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateNotifierTest {
    @Test
    fun showPendingUpdateStopsBeforeChannelWhenRuntimePermissionIsMissing() {
        val controller = Controller().apply {
            runtimePermission = false
            notificationsEnabled = true
        }

        val shown = UpdateNotifier.showPendingUpdate("v0.4.3", "ready", controller)

        assertFalse(shown)
        assertFalse(controller.notificationsQueried)
        assertFalse(controller.channelCreated)
        assertFalse(controller.notified)
    }

    @Test
    fun showPendingUpdateStopsBeforeChannelWhenNotificationsAreDisabled() {
        val controller = Controller().apply {
            runtimePermission = true
            notificationsEnabled = false
        }

        val shown = UpdateNotifier.showPendingUpdate("v0.4.3", "ready", controller)

        assertFalse(shown)
        assertTrue(controller.notificationsQueried)
        assertFalse(controller.channelCreated)
        assertFalse(controller.notified)
    }

    @Test
    fun showPendingUpdateCreatesChannelBeforePostingResolvedNotificationCopy() {
        val controller = Controller().apply {
            runtimePermission = true
            notificationsEnabled = true
        }

        val shown = UpdateNotifier.showPendingUpdate("v0.4.3", "ignored", controller)

        assertTrue(shown)
        assertTrue(controller.notificationsQueried)
        assertTrue(controller.channelCreated)
        assertTrue(controller.notified)
        assertTrue(controller.events.toString() == "channel notify")
        assertTrue(controller.title == "Kani update ready to install")
        assertTrue(controller.body == "Version 0.4.3 is ready. Open Kani to install it.")
    }

    @Test
    fun notificationEnabledHelperRequiresManagerAndEnabledState() {
        val manager: NotificationManager? = null

        assertFalse(UpdateNotifier.notificationsEnabled(manager) { true })
    }

    private class Controller : UpdateNotifier.NotificationController {
        var runtimePermission = false
        var notificationsEnabled = false
        var notificationsQueried = false
        var channelCreated = false
        var notified = false
        var title: String? = null
        var body: String? = null
        val events = StringBuilder()

        override fun hasRuntimeNotificationPermission(): Boolean = runtimePermission

        override fun areNotificationsEnabled(): Boolean {
            notificationsQueried = true
            return notificationsEnabled
        }

        override fun ensureChannel() {
            channelCreated = true
            events.append("channel")
        }

        override fun notifyUpdate(title: String, body: String) {
            notified = true
            this.title = title
            this.body = body
            events.append(" notify")
        }
    }
}
