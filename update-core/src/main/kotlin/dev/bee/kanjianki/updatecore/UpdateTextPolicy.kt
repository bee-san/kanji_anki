package dev.bee.kanjianki.updatecore

object UpdateTextPolicy {
    const val DEFAULT_PENDING_UPDATE_MESSAGE = "Kani update is ready. Open Kani to install it."

    @JvmStatic
    fun readableMessage(error: Throwable?): String {
        if (error == null) {
            return "unknown error"
        }
        val message = error.message
        if (!message.isNullOrBlank()) {
            return message
        }
        return error::class.java.simpleName
    }

    @JvmStatic
    fun notificationBody(version: String?, message: String?): String {
        if (!version.isNullOrEmpty()) {
            return "Version ${version.replaceFirst("^v".toRegex(), "")} is ready. Open Kani to install it."
        }
        return appendInstallAction(message)
    }

    private fun appendInstallAction(message: String?): String {
        if (message.isNullOrBlank()) {
            return DEFAULT_PENDING_UPDATE_MESSAGE
        }
        return "${message.trim()} Open Kani to install it."
    }
}
