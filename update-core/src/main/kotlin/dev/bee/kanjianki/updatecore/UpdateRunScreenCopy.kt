package dev.bee.kanjianki.updatecore

import java.util.Locale

object UpdateRunScreenCopy {
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun forRun(cachedPending: Boolean): Copy {
        if (cachedPending) {
            return Copy(
                localizedText("Preparing installer", "インストーラーを準備中"),
                localizedText("Verifying APK", "APKを確認中"),
            )
        }
        return Copy(
            localizedText("Checking for updates", "更新を確認中"),
            localizedText("Checking releases", "リリースを確認中"),
        )
    }

    class Copy(
        private val title: String,
        private val progressLabel: String,
    ) {
        fun title(): String = title
        fun progressLabel(): String = progressLabel
    }

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
