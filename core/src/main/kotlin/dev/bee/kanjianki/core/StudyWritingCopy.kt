package dev.bee.kanjianki.core

import java.util.Locale

object StudyWritingCopy {
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun title(): String = localizedText("Draw this kanji", "この漢字を書いてください")

    @JvmStatic
    fun referenceInstruction(): String = localizedText(
        "Use the reference, trace, then check.",
        "参考を見てなぞってから確認してください。",
    )

    @JvmStatic
    fun recallPromptLine(clue: String): String = localizedText(
        "Prompt: $clue",
        "書き取りプロンプト: $clue",
    )

    @JvmStatic
    fun readingLine(reading: String): String = localizedText(
        "Reading: $reading",
        "読み: $reading",
    )

    @JvmStatic
    fun promptInstruction(): String = localizedText(
        "Write from the prompt. The answer stays hidden until you check.",
        "問題を見て書いてください。答えは確認するまで隠れています。",
    )

    @JvmStatic
    fun eraseLabel(): String = localizedText("Erase", "消去")

    @JvmStatic
    fun undoLabel(): String = localizedText("Undo", "元に戻す")

    /**
     * Screen-reader copy for the custom handwriting canvas. The requested
     * kanji is intentionally not included: recall cards keep the answer
     * hidden until the learner checks their writing.
     */
    @JvmStatic
    fun drawingPadDescription(): String = localizedText(
        "Handwriting pad. Draw the requested kanji. With TalkBack on, use two fingers or " +
            "TalkBack's pass-through gesture. Use Erase or Undo to edit, then choose Check.",
        "手書きパッド。指定された漢字を書いてください。TalkBack使用中は、2本指または" +
            "TalkBackのパススルージェスチャーを使ってください。修正には「消去」または" +
            "「元に戻す」を使い、最後に「確認」を選んでください。",
    )

    @JvmStatic
    fun drawingPadStrokeState(strokeCount: Int): String {
        val safeStrokeCount = maxOf(0, strokeCount)
        return if (isJapaneseLocale()) {
            if (safeStrokeCount == 0) "まだ線はありません。" else "${safeStrokeCount}画入力済み。"
        } else {
            when (safeStrokeCount) {
                0 -> "No strokes drawn."
                1 -> "1 stroke drawn."
                else -> "$safeStrokeCount strokes drawn."
            }
        }
    }

    @JvmStatic
    fun replayLabel(): String = localizedText("Replay", "再生")

    @JvmStatic
    fun manualOverrideLabel(): String = localizedText("Mark right anyway", "それでも合格にする")

    @JvmStatic
    fun continueAnywayLabel(): String = localizedText("Continue anyway", "このまま続行")

    @JvmStatic
    fun skipLabel(): String = localizedText("Skip", "スキップ")

    @JvmStatic
    fun practiceWithGuideLabel(): String = localizedText("Try again with full guide", "フルガイドで再挑戦")

    @JvmStatic
    fun checkingStatus(): String = localizedText(
        "Checking handwriting...",
        "手書き判定中...",
    )

    @JvmStatic
    fun modelUnavailableStatus(): String = localizedText(
        "The handwriting checker is unavailable on this device.",
        "この端末では自動手書き判定は使えません。",
    )

    @JvmStatic
    fun downloadRequiredStatus(): String = localizedText(
        "Download the handwriting checker before automatic checks.",
        "自動判定を使う前に手書き判定器をダウンロードしてください。",
    )

    @JvmStatic
    fun downloadingStatus(): String = localizedText(
        "Downloading handwriting checker...",
        "手書き判定器をダウンロードしています...",
    )

    @JvmStatic
    fun downloadFailedStatus(errorMessage: String?): String {
        val safeError = errorMessage?.takeIf { it.isNotBlank() } ?: localizedText("an unknown error", "不明なエラー")
        return localizedText(
            "Handwriting checker download failed: $safeError",
            "手書き判定器のダウンロードに失敗しました: $safeError",
        )
    }

    @JvmStatic
    fun readyStatus(): String = localizedText(
        "Handwriting checker ready.",
        "手書き判定器の準備ができました。",
    )

    private fun localizedText(english: String, japanese: String): String {
        return if (isJapaneseLocale()) japanese else english
    }

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
