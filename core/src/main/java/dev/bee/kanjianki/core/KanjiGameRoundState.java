package dev.bee.kanjianki.core;

public final class KanjiGameRoundState {
    public final int totalQuestions;
    public final int answered;
    public final int correct;
    public final int streak;

    private KanjiGameRoundState(int totalQuestions, int answered, int correct, int streak) {
        this.totalQuestions = Math.max(1, totalQuestions);
        this.answered = Math.max(0, answered);
        this.correct = Math.max(0, correct);
        this.streak = Math.max(0, streak);
    }

    public static KanjiGameRoundState newRound(int totalQuestions) {
        return new KanjiGameRoundState(totalQuestions, 0, 0, 0);
    }

    public KanjiGameRoundState answer(boolean wasCorrect) {
        return new KanjiGameRoundState(
                totalQuestions,
                answered + 1,
                correct + (wasCorrect ? 1 : 0),
                wasCorrect ? streak + 1 : 0
        );
    }

    public boolean roundComplete() {
        return answered >= totalQuestions;
    }

    public int progress(boolean awaitingAnswer) {
        int nextProgress = answered + (awaitingAnswer ? 1 : 0);
        return Math.min(nextProgress, totalQuestions);
    }

    public int accuracyPercent() {
        return accuracyPercent(correct, answered);
    }

    public static int accuracyPercent(int correct, int answered) {
        if (answered <= 0) {
            return 0;
        }
        return Math.round(correct * 100f / answered);
    }
}
