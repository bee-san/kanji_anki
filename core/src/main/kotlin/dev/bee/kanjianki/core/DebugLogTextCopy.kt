package dev.bee.kanjianki.core

import java.util.Locale

/**
 * Bilingual copy for the Settings > Automation debug-log panel. The panel offers a
 * runtime on/off switch for the diagnostic log plus a share action, so all labels
 * are state-dependent and live here where they stay JVM-testable.
 */
object DebugLogTextCopy {
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun debugLogTitle(): String = localizedText("Debug log", "デバッグログ")

    @JvmStatic
    fun debugLogStatus(enabled: Boolean): String {
        return if (enabled) localizedText("Recording", "記録中") else localizedText("Off", "オフ")
    }

    @JvmStatic
    fun debugLogDetail(enabled: Boolean): String {
        return if (enabled) {
            localizedText(
                "Recording timestamped app activity (screens, sync, errors) to a private local file. " +
                    "Turn it off once you have captured the problem.",
                "アプリの動作（画面、同期、エラー）をタイムスタンプ付きでローカルファイルに記録中です。" +
                    "問題を記録できたらオフにしてください。",
            )
        } else {
            localizedText(
                "Records timestamped app activity (screens, sync, errors) to a private local file " +
                    "you can share when reporting a problem. Off by default so it never slows the app.",
                "問題を報告するときに共有できるよう、アプリの動作（画面、同期、エラー）を" +
                    "タイムスタンプ付きでローカルファイルに記録します。アプリを遅くしないよう、通常はオフです。",
            )
        }
    }

    @JvmStatic
    fun debugLogToggleLabel(enabled: Boolean): String {
        return if (enabled) {
            localizedText("Turn off debug log", "デバッグログをオフにする")
        } else {
            localizedText("Turn on debug log", "デバッグログをオンにする")
        }
    }

    @JvmStatic
    fun shareDebugLogLabel(): String = localizedText("Share debug log", "デバッグログを共有")

    @JvmStatic
    fun debugLogEmptyToast(): String {
        return localizedText(
            "No debug log captured yet. Turn it on, reproduce the problem, then share.",
            "デバッグログはまだありません。オンにして問題を再現してから共有してください。",
        )
    }

    @JvmStatic
    fun shareDebugLogChooserTitle(): String = localizedText("Share debug log", "デバッグログを共有")

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
