package dev.bee.kanjianki.core

class KanjiGameRoundState private constructor(
    totalQuestions: Int,
    answered: Int,
    correct: Int,
    streak: Int,
) {
    @JvmField val totalQuestions: Int = maxOf(1, totalQuestions)
    @JvmField val answered: Int = maxOf(0, answered)
    @JvmField val correct: Int = maxOf(0, correct)
    @JvmField val streak: Int = maxOf(0, streak)

    fun answer(wasCorrect: Boolean): KanjiGameRoundState {
        if (roundComplete()) {
            return this
        }
        return KanjiGameRoundState(
            totalQuestions,
            answered + 1,
            correct + if (wasCorrect) 1 else 0,
            if (wasCorrect) streak + 1 else 0,
        )
    }

    fun roundComplete(): Boolean = answered >= totalQuestions

    fun progress(awaitingAnswer: Boolean): Int {
        val nextProgress = answered + if (awaitingAnswer) 1 else 0
        return minOf(nextProgress, totalQuestions)
    }

    fun accuracyPercent(): Int = accuracyPercent(correct, answered)

    companion object {
        @JvmStatic
        fun newRound(totalQuestions: Int): KanjiGameRoundState {
            return KanjiGameRoundState(totalQuestions, 0, 0, 0)
        }

        @JvmStatic
        fun accuracyPercent(correct: Int, answered: Int): Int {
            if (answered <= 0) {
                return 0
            }
            val safeCorrect = correct.coerceIn(0, answered)
            return Math.round(safeCorrect.toDouble() * 100.0 / answered).toInt()
        }
    }
}
