package dev.bee.kanjianki.updatecore

import java.util.Locale

object UpdateTextPolicy {
    const val DEFAULT_PENDING_UPDATE_MESSAGE = "Kani update is ready. Open Kani to install it."
    private const val JAPANESE_LANGUAGE = "ja"

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
    fun notificationTitle(): String = localizedText(
        "Kani update ready to install",
        "Kaniの更新をインストールできます",
    )

    @JvmStatic
    fun notificationChannelName(): String = localizedText("App updates", "アプリの更新")

    @JvmStatic
    fun notificationChannelDescription(): String = localizedText(
        "Friendly Kani update prompts.",
        "Kaniの更新をわかりやすくお知らせします。",
    )

    @JvmStatic
    fun notificationBody(version: String?, message: String?): String {
        if (!version.isNullOrEmpty()) {
            val cleanVersion = version.replaceFirst("^v".toRegex(), "")
            return localizedText(
                "Version $cleanVersion is ready. Open Kani to install it.",
                "バージョン $cleanVersion の準備ができました。Kaniを開いてインストールします。",
            )
        }
        return appendInstallAction(message)
    }

    private fun appendInstallAction(message: String?): String {
        if (message.isNullOrBlank()) {
            return localizedText(
                DEFAULT_PENDING_UPDATE_MESSAGE,
                "Kaniの更新準備ができました。Kaniを開いてインストールします。",
            )
        }
        return "${message.trim()} ${localizedInstallAction()}"
    }

    private fun localizedInstallAction(): String = localizedText(
        "Open Kani to install it.",
        "Kaniを開いてインストールします。",
    )

    private fun localizedText(english: String, japanese: String): String =
        if (Locale.getDefault().language == JAPANESE_LANGUAGE) japanese else english
}
