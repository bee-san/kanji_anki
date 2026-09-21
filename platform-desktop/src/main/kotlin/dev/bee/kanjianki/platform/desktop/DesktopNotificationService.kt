package dev.bee.kanjianki.platform.desktop

import dev.bee.kanjianki.platform.NotificationRequest
import dev.bee.kanjianki.platform.NotificationService

/**
 * Desktop's [NotificationService], over the AWT system tray.
 *
 * The tray operations are injected rather than reached for directly, the same reason
 * [DesktopClipboardService] injects its clipboard write: `SystemTray.getSystemTray()`
 * and `TrayIcon.displayMessage` need a display and throw on a headless CI runner, which
 * is every host in this repo's desktop gate. The composition root supplies a
 * [TrayNotifier] backed by a real tray icon where one is supported; a test supplies a
 * recording fake.
 *
 * Availability is honest and load-bearing (Goal 201): where no tray exists — a Linux
 * session without a system tray, a headless run — [isAvailable] is false and the caller
 * falls back to the in-app reminder surface rather than dropping the notification. A
 * post to an unavailable service is refused, not silently swallowed, so a caller cannot
 * mistake "no tray" for "notified".
 */
class DesktopNotificationService(
    private val notifier: TrayNotifier,
) : NotificationService {
    /**
     * The tray operations this service needs, as a seam.
     *
     * [supported] is the tray's own availability (`SystemTray.isSupported()` plus a
     * successfully added icon); [display] shows a balloon message and reports whether
     * the tray accepted it.
     */
    interface TrayNotifier {
        fun supported(): Boolean

        fun display(request: NotificationRequest): Boolean

        fun cancel(id: String): Boolean
    }

    override fun isAvailable(): Boolean = runCatching { notifier.supported() }.getOrDefault(false)

    override fun post(request: NotificationRequest): Boolean {
        if (!isAvailable()) return false
        return runCatching { notifier.display(request) }.getOrDefault(false)
    }

    override fun cancel(id: String): Boolean {
        if (id.isBlank()) return false
        return runCatching { notifier.cancel(id) }.getOrDefault(false)
    }
}
