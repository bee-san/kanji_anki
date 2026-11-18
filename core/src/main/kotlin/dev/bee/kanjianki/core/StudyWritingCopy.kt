package dev.bee.kanjianki.core

import java.util.Locale

object StudyWritingCopy {
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun title(): String = localizedText("Draw this kanji", "この漢字を書いてください")

    /** Title when the cue contains a blank for the learner to fill in. */
    @JvmStatic
    fun clozeTitle(): String = localizedText("Write the missing kanji", "〇に入る漢字を書いてください")

    /** "〇出 (だっしゅつ) — escape": the blanked word under a sentence cue. */
    @JvmStatic
    fun wordCueLine(word: String, reading: String, meaning: String): String {
        val head = if (reading.isBlank()) word else localizedText("$word ($reading)", "$word（$reading）")
        return joinCue(head, meaning)
    }

    /** "だっしゅつ — escape": the clue under a blanked-word cue. */
    @JvmStatic
    fun readingMeaningLine(reading: String, meaning: String): String = joinCue(reading, meaning)

    private fun joinCue(head: String, meaning: String): String {
        return listOf(head.trim(), meaning.trim()).filter { it.isNotEmpty() }.joinToString(" — ")
    }

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
    fun compareYoursLabel(): String = localizedText("Yours", "あなたの字")

    @JvmStatic
    fun compareModelLabel(): String = localizedText("Model", "お手本")

    @JvmStatic
    fun compareOkLabel(): String = localizedText("Good", "良い")

    @JvmStatic
    fun compareRoughLabel(): String = localizedText("Shape or position off", "形か位置がずれている")

    @JvmStatic
    fun compareWrongDirectionLabel(): String = localizedText("Wrong direction", "書く向きが逆")

    @JvmStatic
    fun compareWrongOrderLabel(): String = localizedText("Wrong order", "書き順が違う")

    @JvmStatic
    fun compareMissingLabel(): String = localizedText("Missing", "足りない画")

    @JvmStatic
    fun compareExtraLabel(): String = localizedText("Extra", "余分な画")

    @JvmStatic
    fun compareOrderOrDirectionLabel(): String = localizedText("Wrong order or direction", "書き順か向きが違う")

    @JvmStatic
    fun compareMissingOrExtraLabel(): String = localizedText("Missing or extra stroke", "画が足りない・多い")

    @JvmStatic
    fun compareStrokeDescription(strokeNumber: Int, verdict: String): String = localizedText(
        "stroke $strokeNumber: $verdict",
        "${strokeNumber}画目: $verdict",
    )

    /** Screen-reader summary of one comparison square. */
    @JvmStatic
    fun compareSquareDescription(title: String, strokeCount: Int, problems: List<String>): String {
        val count = if (isJapaneseLocale()) {
            "${strokeCount}画"
        } else if (strokeCount == 1) {
            "1 stroke"
        } else {
            "$strokeCount strokes"
        }
        val head = localizedText("$title, $count", "$title、$count")
        if (problems.isEmpty()) {
            return "$head."
        }
        return head + localizedText(". ", "。") + problems.joinToString(localizedText("; ", "、")) + localizedText(".", "。")
    }

    @JvmStatic
    fun primerWatchStatus(): String = localizedText(
        "Primer: watch the strokes, then write it from memory.",
        "予習: 筆順を見てから、記憶で書いてみましょう。",
    )

    @JvmStatic
    fun primerWriteStatus(): String = localizedText(
        "Primer: write it from memory, then compare.",
        "予習: 記憶で書いてから、お手本と比べましょう。",
    )

    @JvmStatic
    fun primerNeedsInkStatus(): String = localizedText(
        "Primer: write it first, then compare.",
        "予習: 先に書いてから比べましょう。",
    )

    @JvmStatic
    fun primerCompareStatus(): String = localizedText(
        "Primer: compare your strokes with the model. This is practice and is not graded.",
        "予習: 自分の字をお手本と比べましょう。これは練習で、採点されません。",
    )

    @JvmStatic
    fun primerWriteLabel(): String = localizedText("Write from memory", "記憶で書く")

    @JvmStatic
    fun primerCompareLabel(): String = localizedText("Compare", "比べる")

    @JvmStatic
    fun primerTryAgainLabel(): String = localizedText("Try again", "もう一度")

    @JvmStatic
    fun primerStartLabel(): String = localizedText("Start", "始める")

    @JvmStatic
    fun primerSkipLabel(): String = localizedText("Skip primer", "予習をスキップ")

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

    /** Overrides a graded pass: the learner judged their own writing wrong. */
    @JvmStatic
    fun rejectPassLabel(): String = localizedText("I got it wrong", "間違えた")

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
