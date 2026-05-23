package dev.bee.kanjianki.core

object KanjiGameCopy {
    const val LABEL_GAMES = "Games"
    const val LABEL_NEXT = "Next"
    const val LABEL_ROUND_COMPLETE = "Round complete"
    const val LABEL_NEW_ROUND = "New round"
    const val LABEL_SYNC_ANKIDROID = "Sync AnkiDroid"
    const val LABEL_PLAY = "play"
    const val LABEL_LOCKED = "locked"
    const val LABEL_ROUND = "Round"
    const val LABEL_SCORE = "Score"
    const val LABEL_STREAK = "Streak"
    const val GAMES_SUBTITLE = "Practice kanji without changing SRS."
    const val EMPTY_NO_KANJI_TITLE = "No kanji games yet"
    const val EMPTY_NO_KANJI_BODY = "Sync AnkiDroid first so Kani can build practice games from your own cards."
    const val GAME_NOT_READY_TITLE = "Game not ready"
    const val GAME_NOT_READY_BODY = "This game needs at least two usable choices from your local kanji data."

    @JvmStatic
    fun modeBody(mode: KanjiGameEngine.GameMode?, available: Boolean): String {
        if (!available) {
            return "Needs more local kanji data."
        }
        return when (mode!!) {
            KanjiGameEngine.GameMode.MEANING_POP -> "Pick meanings for kanji from your focus list."
            KanjiGameEngine.GameMode.READING_RUSH -> "Pick readings from your source words."
            KanjiGameEngine.GameMode.CONFUSABLE_CLASH -> "Choose between visually similar kanji."
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
            return LABEL_ROUND_COMPLETE
        }
        return if (correct) "Correct" else "Not quite"
    }

    @JvmStatic
    fun answerText(correctAnswer: String?): String = "Answer: $correctAnswer"

    @JvmStatic
    fun selectedAnswerText(selectedAnswer: String?): String = "You chose: $selectedAnswer"

    @JvmStatic
    fun finalScoreText(correct: Int, total: Int): String = "Final score: $correct/$total"

    @JvmStatic
    fun accuracyText(correct: Int, answered: Int): String {
        return "Accuracy: ${KanjiGameRoundState.accuracyPercent(correct, answered)}%"
    }
}
