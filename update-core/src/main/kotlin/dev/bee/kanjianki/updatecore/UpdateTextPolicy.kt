package dev.bee.kanjianki.updatecore

object UpdateTextPolicy {
    const val DEFAULT_PENDING_UPDATE_MESSAGE = "Open Kani to finish installing the verified update."

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
            message
        } else {
            "Version ${version.replaceFirst("^v".toRegex(), "")} is verified and ready."
        }
        if (body.isNullOrBlank()) {
            body = DEFAULT_PENDING_UPDATE_MESSAGE
        }
        return body
    }
}
