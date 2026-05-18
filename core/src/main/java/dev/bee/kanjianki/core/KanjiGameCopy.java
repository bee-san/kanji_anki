package dev.bee.kanjianki.core;

public final class KanjiGameCopy {
    public static final String LABEL_GAMES = "Games";
    public static final String LABEL_NEXT = "Next";
    public static final String LABEL_ROUND_COMPLETE = "Round complete";
    public static final String LABEL_NEW_ROUND = "New round";
    public static final String LABEL_SYNC_ANKIDROID = "Sync AnkiDroid";
    public static final String LABEL_PLAY = "play";
    public static final String LABEL_LOCKED = "locked";
    public static final String LABEL_ROUND = "Round";
    public static final String LABEL_SCORE = "Score";
    public static final String LABEL_STREAK = "Streak";
    public static final String GAMES_SUBTITLE = "Practice kanji without changing SRS.";
    public static final String EMPTY_NO_KANJI_TITLE = "No kanji games yet";
    public static final String EMPTY_NO_KANJI_BODY = "Sync AnkiDroid first so Kani can build practice games from your own cards.";
    public static final String GAME_NOT_READY_TITLE = "Game not ready";
    public static final String GAME_NOT_READY_BODY = "This game needs at least two usable choices from your local kanji data.";

    private KanjiGameCopy() {
    }

    public static String modeBody(KanjiGameEngine.GameMode mode, boolean available) {
        if (!available) {
            return "Needs more local kanji data.";
        }
        return switch (mode) {
            case MEANING_POP -> "Pick meanings for kanji from your focus list.";
            case READING_RUSH -> "Pick readings from your source words.";
            case CONFUSABLE_CLASH -> "Choose between visually similar kanji.";
        };
    }

    public static String choiceLabel(KanjiGameEngine.GameQuestion question, String choice) {
        if (question.mode == KanjiGameEngine.GameMode.CONFUSABLE_CLASH) {
            return choice;
        }
        return StudyCueFormatter.compact(choice, 56);
    }

    public static int promptTextSizeSp(KanjiGameEngine.GameQuestion question) {
        if (question.mode == KanjiGameEngine.GameMode.MEANING_POP) {
            return 52;
        }
        return question.prompt.length() <= 6 ? 38 : 25;
    }

    public static int choiceTextSizeSp(KanjiGameEngine.GameQuestion question) {
        return choiceUsesKanjiTypography(question) ? 32 : 15;
    }

    public static boolean choiceUsesKanjiTypography(KanjiGameEngine.GameQuestion question) {
        return question.mode == KanjiGameEngine.GameMode.CONFUSABLE_CLASH;
    }

    public static String resultTitle(boolean roundComplete, boolean correct) {
        if (roundComplete) {
            return LABEL_ROUND_COMPLETE;
        }
        return correct ? "Correct" : "Not quite";
    }

    public static String answerText(String correctAnswer) {
        return "Answer: " + correctAnswer;
    }

    public static String selectedAnswerText(String selectedAnswer) {
        return "You chose: " + selectedAnswer;
    }

    public static String finalScoreText(int correct, int total) {
        return "Final score: " + correct + "/" + total;
    }

    public static String accuracyText(int correct, int answered) {
        return "Accuracy: " + KanjiGameRoundState.accuracyPercent(correct, answered) + "%";
    }

}
