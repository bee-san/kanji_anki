package dev.bee.kanjianki.platform.desktop

import dev.bee.kanjianki.platform.NotificationCategory
import dev.bee.kanjianki.platform.NotificationRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopNotificationServiceTest {
    @Test
    fun aSupportedTrayPostsAndCancels() {
        val notifier = RecordingNotifier(supported = true, displayAccepts = true, cancelAccepts = true)
        val service = DesktopNotificationService(notifier)

        assertTrue(service.isAvailable())
        assertTrue(service.post(reminder()))
        assertEquals(listOf("kani-reminder"), notifier.displayed)
        assertTrue(service.cancel("kani-reminder"))
        assertEquals(listOf("kani-reminder"), notifier.cancelled)
    }

    @Test
    fun anUnavailableTrayRefusesToPostSoTheCallerCanFallBack() {
        val notifier = RecordingNotifier(supported = false)
        val service = DesktopNotificationService(notifier)

        assertFalse(service.isAvailable())
        assertFalse("a post to no tray is refused, not silently dropped", service.post(reminder()))
        assertTrue("nothing reached the tray", notifier.displayed.isEmpty())
    }

    @Test
    fun aBlankCancelIdIsRefusedWithoutTouchingTheTray() {
        val notifier = RecordingNotifier(supported = true)
        val service = DesktopNotificationService(notifier)

        assertFalse(service.cancel("  "))
        assertTrue(notifier.cancelled.isEmpty())
    }

    @Test
    fun aThrowingTrayBecomesFalseRatherThanCrashing() {
        val service = DesktopNotificationService(object : DesktopNotificationService.TrayNotifier {
            override fun supported(): Boolean = true
            override fun display(request: NotificationRequest): Boolean = throw IllegalStateException("no display")
            override fun cancel(id: String): Boolean = throw IllegalStateException("no display")
        })

        assertFalse(service.post(reminder()))
        assertFalse(service.cancel("kani-reminder"))
    }

    private fun reminder() = NotificationRequest(
        id = "kani-reminder",
        category = NotificationCategory.REMINDER,
        title = "Time to study",
        body = "5 kanji are due.",
    )

    private class RecordingNotifier(
        private val supported: Boolean,
        private val displayAccepts: Boolean = true,
        private val cancelAccepts: Boolean = true,
    ) : DesktopNotificationService.TrayNotifier {
        val displayed = mutableListOf<String>()
        val cancelled = mutableListOf<String>()

        override fun supported(): Boolean = supported

        override fun display(request: NotificationRequest): Boolean {
            displayed += request.id
            return displayAccepts
        }

        override fun cancel(id: String): Boolean {
            cancelled += id
            return cancelAccepts
        }
    }
}
