package dev.bee.kanjianki.notifications

object NotificationDeliveryPolicy {
    @JvmStatic
    fun attempt(action: NotificationAction): Boolean {
        return try {
            action.run()
            true
        } catch (_: SecurityException) {
            false
        }
    }

    fun interface NotificationAction {
        fun run()
    }
}
