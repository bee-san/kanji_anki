package dev.bee.kanjianki.core

import java.util.Locale

object KanjiGameCopy {
    const val LABEL_GAMES = "Games"
    const val LABEL_NEXT = "Next"
    const val LABEL_ROUND_COMPLETE = "Round complete"
    const val LABEL_NEW_ROUND = "New round"
    const val LABEL_SYNC_ANKIDROID = "Sync AnkiDroid"
    const val LABEL_PLAY = "Start"
    const val LABEL_LOCKED = "Needs data"
    const val LABEL_ROUND = "Round"
    const val LABEL_SCORE = "Score"
    const val LABEL_STREAK = "Streak"
    const val GAMES_SUBTITLE = "Practice without changing reviews."
    const val EMPTY_NO_KANJI_TITLE = "No games yet"
    const val EMPTY_NO_KANJI_BODY = "Sync AnkiDroid to build games."
    const val GAME_NOT_READY_TITLE = "Needs more data"
    const val GAME_NOT_READY_BODY = "At least two choices needed."

    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun gamesLabel(): String = localizedText(LABEL_GAMES, "ゲーム")

    @JvmStatic
    fun nextLabel(): String = localizedText(LABEL_NEXT, "次へ")

    @JvmStatic
    fun roundCompleteLabel(): String = localizedText(LABEL_ROUND_COMPLETE, "ラウンド完了")

    @JvmStatic
    fun newRoundLabel(): String = localizedText(LABEL_NEW_ROUND, "新しいラウンド")

    @JvmStatic
    fun syncAnkiDroidLabel(): String = localizedText(LABEL_SYNC_ANKIDROID, "AnkiDroidを同期")

    @JvmStatic
    fun playLabel(): String = localizedText(LABEL_PLAY, "開始")

    @JvmStatic
    fun lockedLabel(): String = localizedText(LABEL_LOCKED, "データ不足")

    @JvmStatic
    fun roundLabel(): String = localizedText(LABEL_ROUND, "ラウンド")

    @JvmStatic
    fun scoreLabel(): String = localizedText(LABEL_SCORE, "スコア")

    @JvmStatic
    fun streakLabel(): String = localizedText(LABEL_STREAK, "連続")

    @JvmStatic
    fun gamesSubtitle(): String = localizedText(GAMES_SUBTITLE, "復習を変更せずに練習できます。")

    @JvmStatic
    fun emptyNoKanjiTitle(): String = localizedText(EMPTY_NO_KANJI_TITLE, "まだゲームはありません")

    @JvmStatic
    fun emptyNoKanjiBody(): String = localizedText(EMPTY_NO_KANJI_BODY, "AnkiDroidを同期してゲームを作成します。")

    @JvmStatic
    fun gameNotReadyTitle(): String = localizedText(GAME_NOT_READY_TITLE, "もっとデータが必要です")

    @JvmStatic
    fun gameNotReadyBody(): String = localizedText(GAME_NOT_READY_BODY, "選択肢が2つ以上必要です。")

    @JvmStatic
    fun modeLabel(mode: KanjiGameEngine.GameMode?): String {
        return when (mode!!) {
            KanjiGameEngine.GameMode.MEANING_POP -> localizedText(mode.label, "漢字→意味")
            KanjiGameEngine.GameMode.READING_RUSH -> localizedText(mode.label, "単語→読み")
            KanjiGameEngine.GameMode.CONFUSABLE_CLASH -> localizedText(mode.label, "意味→漢字")
        }
    }

    @JvmStatic
    fun modeBody(mode: KanjiGameEngine.GameMode?, available: Boolean): String {
        if (!available) {
            return localizedText("Needs more data.", "もっとデータが必要です。")
        }
        return when (mode!!) {
            KanjiGameEngine.GameMode.MEANING_POP -> localizedText(
                "Pick meanings from your focus list.",
                "集中リストから意味を選びます。",
            )
            KanjiGameEngine.GameMode.READING_RUSH -> localizedText(
                "Pick readings from source words.",
                "出典の単語から読みを選びます。",
            )
            KanjiGameEngine.GameMode.CONFUSABLE_CLASH -> localizedText(
                "Choose among similar kanji.",
                "似ている漢字から選びます。",
            )
        }
    }

    @JvmStatic
    fun choiceLabel(question: KanjiGameEngine.GameQuestion?, choice: String?): String? {
        if (question!!.mode == KanjiGameEngine.GameMode.CONFUSABLE_CLASH) {
            return choice
        }
        return StudyCueFormatter.compact(choice, 56)
    }

    @JvmStatic
    fun promptTextSizeSp(question: KanjiGameEngine.GameQuestion?): Int {
        if (question!!.mode == KanjiGameEngine.GameMode.MEANING_POP) {
            return 52
        }
        return if (question.prompt.length <= 6) 38 else 25
    }

    @JvmStatic
    fun choiceTextSizeSp(question: KanjiGameEngine.GameQuestion?): Int {
        return if (choiceUsesKanjiTypography(question)) 32 else 15
    }

    @JvmStatic
    fun choiceUsesKanjiTypography(question: KanjiGameEngine.GameQuestion?): Boolean {
        return question!!.mode == KanjiGameEngine.GameMode.CONFUSABLE_CLASH
    }

    @JvmStatic
    fun resultTitle(roundComplete: Boolean, correct: Boolean): String {
        if (roundComplete) {
            return roundCompleteLabel()
        }
        return if (correct) localizedText("Correct", "正解") else localizedText("Not quite", "惜しい")
    }

    @JvmStatic
    fun answerText(correctAnswer: String?): String = localizedText(
        "Correct answer: $correctAnswer",
        "正解: $correctAnswer",
    )

    @JvmStatic
    fun selectedAnswerText(selectedAnswer: String?): String = localizedText(
        "Your answer: $selectedAnswer",
        "あなたの答え: $selectedAnswer",
    )

    @JvmStatic
    fun finalScoreText(correct: Int, total: Int): String = localizedText(
        "Score: $correct/$total",
        "スコア: $correct/$total",
    )

    @JvmStatic
    fun accuracyText(correct: Int, answered: Int): String {
        val accuracy = KanjiGameRoundState.accuracyPercent(correct, answered)
        return localizedText("Accuracy: $accuracy%", "正答率: $accuracy%")
    }

    private fun localizedText(english: String, japanese: String): String =
        if (isJapaneseLocale()) japanese else english

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE
}
