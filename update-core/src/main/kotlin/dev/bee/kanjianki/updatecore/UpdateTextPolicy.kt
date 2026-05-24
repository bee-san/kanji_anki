package dev.bee.kanjianki.updatecore

object UpdateTextPolicy {
    const val DEFAULT_PENDING_UPDATE_MESSAGE = "A verified Kani update is waiting. Open Kani to confirm installation and keep the app current."

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
        var body = if (version.isNullOrEmpty()) {
            appendInstallAction(message)
        } else {
            "Version ${version.replaceFirst("^v".toRegex(), "")} is verified. Open Kani to confirm installation and keep the app current."
        }
        if (body.isNullOrBlank()) {
            body = DEFAULT_PENDING_UPDATE_MESSAGE
        }
        return body
    }

    private fun appendInstallAction(message: String?): String? {
        if (message.isNullOrBlank()) {
            return message
        }
        return "$message Open Kani to confirm installation and keep the app current."
    }
}
