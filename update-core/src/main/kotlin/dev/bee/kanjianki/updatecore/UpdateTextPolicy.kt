package dev.bee.kanjianki.updatecore

object UpdateTextPolicy {
    const val DEFAULT_PENDING_UPDATE_MESSAGE = "Kani update ready. Open Kani to install it."

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
            "Version ${version.replaceFirst("^v".toRegex(), "")} is ready. Open Kani to install it."
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
        return "Kani checked the update. Open Kani to install it."
    }
}
