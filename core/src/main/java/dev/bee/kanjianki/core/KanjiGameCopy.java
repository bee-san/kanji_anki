package dev.bee.kanjianki.core;

public final class KanjiGameCopy {
    public static final String LABEL_GAMES = "Games";
    public static final String LABEL_NEXT = "Next";
    public static final String LABEL_ROUND_COMPLETE = "Round complete";
    public static final String LABEL_NEW_ROUND = "New round";

    private KanjiGameCopy() {
    }

    public static String modeBody(KanjiGameEngine.GameMode mode, boolean available) {
        if (!available) {
            return "Needs more local kanji data.";
        }
        KanjiGameEngine.GameMode safeMode = mode == null ? KanjiGameEngine.GameMode.MEANING_POP : mode;
        return switch (safeMode) {
            case MEANING_POP -> "Pick meanings for kanji from your focus list.";
            case READING_RUSH -> "Pick readings from your source words.";
            case CONFUSABLE_CLASH -> "Choose between visually similar kanji.";
        };
    }

    public static String choiceLabel(KanjiGameEngine.GameQuestion question, String choice) {
        if (question != null && question.mode == KanjiGameEngine.GameMode.CONFUSABLE_CLASH) {
            return safe(choice);
        }
        return StudyCueFormatter.compact(choice, 56);
    }

    public static String resultTitle(boolean roundComplete, boolean correct) {
        if (roundComplete) {
            return LABEL_ROUND_COMPLETE;
        }
        return correct ? "Correct" : "Not quite";
    }

    public static String finalScoreText(int correct, int total) {
        return "Final score: " + correct + "/" + total;
    }

    public static String accuracyText(int correct, int answered) {
        return "Accuracy: " + accuracyPercent(correct, answered) + "%";
    }

    public static int accuracyPercent(int correct, int answered) {
        if (answered <= 0) {
            return 0;
        }
        return Math.round(correct * 100f / answered);
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
