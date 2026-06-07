package dev.bee.kanjianki.core

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

    @JvmStatic
    fun modeBody(mode: KanjiGameEngine.GameMode?, available: Boolean): String {
        if (!available) {
            return "Needs more data."
        }
        return when (mode!!) {
            KanjiGameEngine.GameMode.MEANING_POP -> "Pick meanings from your focus list."
            KanjiGameEngine.GameMode.READING_RUSH -> "Pick readings from source words."
            KanjiGameEngine.GameMode.CONFUSABLE_CLASH -> "Choose among similar kanji."
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
    fun answerText(correctAnswer: String?): String = "Correct answer: $correctAnswer"

    @JvmStatic
    fun selectedAnswerText(selectedAnswer: String?): String = "Your answer: $selectedAnswer"

    @JvmStatic
    fun finalScoreText(correct: Int, total: Int): String = "Score: $correct/$total"

    @JvmStatic
    fun accuracyText(correct: Int, answered: Int): String {
        return "Accuracy: ${KanjiGameRoundState.accuracyPercent(correct, answered)}%"
    }
}
