package dev.bee.kanjianki.update

import android.app.NotificationManager
import java.util.Locale
import org.junit.Assert.assertEquals
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
        assertEquals("channel importance notify", controller.events.toString())
        assertEquals("App updates", controller.channelName)
        assertEquals("Friendly Kani update prompts.", controller.channelDescription)
        assertEquals("Kani update ready to install", controller.title)
        assertEquals("Version 0.4.3 is ready. Open Kani to install it.", controller.body)
    }

    @Test
    fun showPendingUpdateStopsWhenTheUpdateChannelIsBlocked() {
        val controller = Controller().apply {
            runtimePermission = true
            notificationsEnabled = true
            channelImportance = NotificationManager.IMPORTANCE_NONE
        }

        val shown = UpdateNotifier.showPendingUpdate("v0.4.3", "ready", controller)

        assertFalse(shown)
        assertTrue(controller.channelCreated)
        assertFalse(controller.notified)
    }

    @Test
    fun showPendingUpdateReportsARejectedNotificationPost() {
        val controller = Controller().apply {
            runtimePermission = true
            notificationsEnabled = true
            notificationAccepted = false
        }

        val shown = UpdateNotifier.showPendingUpdate("v0.4.3", "ready", controller)

        assertFalse(shown)
        assertTrue(controller.notified)
    }

    @Test
    fun showPendingUpdateUsesJapaneseNotificationCopyWhenLocaleIsJapanese() {
        val controller = Controller().apply {
            runtimePermission = true
            notificationsEnabled = true
        }

        withLocale(Locale.JAPANESE) {
            val shown = UpdateNotifier.showPendingUpdate("v0.4.3", "ignored", controller)

            assertTrue(shown)
            assertEquals("アプリの更新", controller.channelName)
            assertEquals("Kaniの更新をわかりやすくお知らせします。", controller.channelDescription)
            assertEquals("Kaniの更新をインストールできます", controller.title)
            assertEquals("バージョン 0.4.3 の準備ができました。Kaniを開いてインストールします。", controller.body)
        }
    }

    @Test
    fun notificationEnabledHelperRequiresManagerAndEnabledState() {
        val manager: NotificationManager? = null

        assertFalse(UpdateNotifier.notificationsEnabled(manager) { true })
    }

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }

    private class Controller : UpdateNotifier.NotificationController {
        var runtimePermission = false
        var notificationsEnabled = false
        var notificationsQueried = false
        var channelCreated = false
        var notified = false
        var notificationAccepted = true
        var channelImportance = NotificationManager.IMPORTANCE_DEFAULT
        var channelName: String? = null
        var channelDescription: String? = null
        var title: String? = null
        var body: String? = null
        val events = StringBuilder()

        override fun hasRuntimeNotificationPermission(): Boolean = runtimePermission
        override fun areNotificationsEnabled(): Boolean {
            notificationsQueried = true
            return notificationsEnabled
        }

        override fun ensureChannel(name: String, description: String): Boolean {
            channelCreated = true
            channelName = name
            channelDescription = description
            events.append("channel")
            return true
        }

        override fun channelImportance(): Int {
            events.append(" importance")
            return channelImportance
        }

        override fun notifyUpdate(title: String, body: String): Boolean {
            notified = true
            this.title = title
            this.body = body
            events.append(" notify")
            return notificationAccepted
        }
    }
}
