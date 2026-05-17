package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsImportModels;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.bee.kanjianki.core.KanjiGameEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

abstract class MainActivityGames extends MainActivityHome {
    private static final int GAME_ROUND_QUESTIONS = 10;
    private static final String LABEL_GAMES = "Games";
    private static final String LABEL_NEXT = "Next";
    private static final String LABEL_ROUND_COMPLETE = "Round complete";
    private static final String LABEL_FINAL_SCORE = "Final score: ";
    private static final String LABEL_NEW_ROUND = "New round";

    private final KanjiGameEngine gameEngine = new KanjiGameEngine();
    private final Random gameRandom = new Random();
    private int gameAnswered;
    private int gameCorrect;
    private int gameStreak;

    void renderGames() {
        clearGameSession();
        base("home");
        content.addView(fullWidthHomeButton());
        content.addView(text(LABEL_GAMES, 34, INK, true));
        content.addView(text("Practice kanji without changing SRS.", 16, MUTED, false));
        addSpace(8);

        GameData data = gameData();
        if (!data.hasKanji()) {
            emptyState("No kanji games yet", "Sync AnkiDroid first so Kani can build practice games from your own cards.");
            Button sync = primaryButton("Sync AnkiDroid", CORAL);
            sync.setOnClickListener(v -> confirmSync());
            content.addView(sync);
            return;
        }

        List<KanjiGameEngine.GameMode> available = gameEngine.availableModes(data.rows, data.inventory, data.pairs);
        for (KanjiGameEngine.GameMode mode : KanjiGameEngine.GameMode.values()) {
            content.addView(gameModeCard(mode, available.contains(mode)));
        }
    }

    private void clearGameSession() {
        gameAnswered = 0;
        gameCorrect = 0;
        gameStreak = 0;
    }

    private View gameModeCard(KanjiGameEngine.GameMode mode, boolean available) {
        int color = available ? colorForGameMode(mode) : Color.rgb(178, 178, 186);
        LinearLayout box = panelBox(Color.WHITE, softened(color));
        box.setPadding(dp(14), dp(13), dp(14), dp(13));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(mode.title, 22, available ? INK : MUTED, true);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(chip(available ? "play" : "locked", color));
        box.addView(top);
        box.addView(text(mode.label, 15, color, true));
        box.addView(text(gameModeBody(mode, available), 14, MUTED, false));
        box.setClickable(available);
        if (available) {
            box.setOnClickListener(v -> startGame(mode));
        }
        return box;
    }

    private String gameModeBody(KanjiGameEngine.GameMode mode, boolean available) {
        if (!available) {
            return "Needs more local kanji data.";
        }
        return switch (mode) {
            case MEANING_POP -> "Pick meanings for kanji from your focus list.";
            case READING_RUSH -> "Pick readings from your source words.";
            case CONFUSABLE_CLASH -> "Choose between visually similar kanji.";
        };
    }

    private void startGame(KanjiGameEngine.GameMode mode) {
        gameAnswered = 0;
        gameCorrect = 0;
        gameStreak = 0;
        renderGameQuestion(mode);
    }

    private void renderGameQuestion(KanjiGameEngine.GameMode mode) {
        if (gameRoundComplete()) {
            renderGameRoundComplete(mode);
            return;
        }
        GameData data = gameData();
        KanjiGameEngine.GameQuestion question = gameEngine.nextQuestion(mode, data.rows, data.inventory, data.pairs, gameRandom);
        if (question == null) {
            renderGameUnavailable(mode);
            return;
        }
        base("home");
        content.addView(homeSectionHeader(mode.title, LABEL_GAMES, this::renderGames));
        content.addView(gameScorePanel(true));
        content.addView(gameQuestionCard(question));
    }

    private void renderGameUnavailable(KanjiGameEngine.GameMode mode) {
        base("home");
        content.addView(homeSectionHeader(mode.title, LABEL_GAMES, this::renderGames));
        emptyState("Game not ready", "This game needs at least two usable choices from your local kanji data.");
    }

    private LinearLayout gameScorePanel(boolean awaitingAnswer) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        int roundProgress = gameRoundProgress(awaitingAnswer);
        row.addView(gameMetric("Round", roundProgress + "/" + GAME_ROUND_QUESTIONS, BLUE));
        row.addView(gameMetric("Score", gameCorrect + "/" + GAME_ROUND_QUESTIONS, CORAL));
        row.addView(gameMetric("Streak", Integer.toString(gameStreak), TEAL));
        return row;
    }

    private View gameMetric(String label, String value, int color) {
        LinearLayout box = panelBox(Color.WHITE, softened(color));
        box.setPadding(dp(12), dp(10), dp(12), dp(10));
        box.addView(text(label, 12, color, true));
        box.addView(text(value, 20, INK, true));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(dp(3), dp(3), dp(3), dp(8));
        box.setLayoutParams(lp);
        return box;
    }

    private LinearLayout gameQuestionCard(KanjiGameEngine.GameQuestion question) {
        int color = colorForGameMode(question.mode);
        LinearLayout card = panelBox(Color.WHITE, softened(color));
        card.addView(modePill(question.mode.label));
        TextView prompt = text(question.prompt, promptSize(question), INK, true);
        prompt.setGravity(Gravity.CENTER);
        prompt.setTypeface(fontResource(R.font.kaisei_tokumin_regular, Typeface.SERIF), Typeface.BOLD);
        prompt.setPadding(0, dp(6), 0, dp(6));
        card.addView(prompt);
        card.addView(text(question.promptDetail, 16, MUTED, false));
        addGameChoices(card, question);
        return card;
    }

    private int promptSize(KanjiGameEngine.GameQuestion question) {
        if (question.mode == KanjiGameEngine.GameMode.MEANING_POP) {
            return 52;
        }
        return question.prompt.length() <= 6 ? 38 : 25;
    }

    private void addGameChoices(LinearLayout card, KanjiGameEngine.GameQuestion question) {
        for (String choice : question.choices) {
            Button button = secondaryButton(choiceLabel(question, choice));
            button.setTextSize(question.mode == KanjiGameEngine.GameMode.CONFUSABLE_CLASH ? 32 : 15);
            button.setMaxLines(2);
            button.setOnClickListener(v -> answerGameQuestion(question, choice));
            if (question.mode == KanjiGameEngine.GameMode.CONFUSABLE_CLASH) {
                button.setTypeface(fontResource(R.font.kaisei_tokumin_regular, Typeface.SERIF), Typeface.BOLD);
                button.setTextColor(INK);
                button.setLayoutParams(buttonLayout(dp(74)));
            }
            card.addView(button);
        }
    }

    private LinearLayout.LayoutParams buttonLayout(int height) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, height);
        lp.setMargins(dp(3), dp(6), dp(3), dp(6));
        return lp;
    }

    private String choiceLabel(KanjiGameEngine.GameQuestion question, String choice) {
        if (question.mode == KanjiGameEngine.GameMode.CONFUSABLE_CLASH) {
            return choice;
        }
        return compact(choice, 56);
    }

    private void answerGameQuestion(KanjiGameEngine.GameQuestion question, String selected) {
        boolean correct = question.isCorrect(selected);
        gameAnswered++;
        if (correct) {
            gameCorrect++;
            gameStreak++;
        } else {
            gameStreak = 0;
        }
        renderGameResult(question, selected, correct);
    }

    private void renderGameResult(KanjiGameEngine.GameQuestion question, String selected, boolean correct) {
        base("home");
        content.addView(homeSectionHeader(question.mode.title, LABEL_GAMES, this::renderGames));
        content.addView(gameScorePanel(false));

        boolean roundComplete = gameRoundComplete();
        int color = gameResultColor(roundComplete, correct);
        LinearLayout result = panelBox(Color.WHITE, softened(color));
        result.addView(text(gameResultTitle(roundComplete, correct), 28, color, true));
        if (roundComplete) {
            addRoundSummary(result);
        }
        result.addView(text("Answer: " + question.correctAnswer, 18, INK, true));
        if (!question.isCorrect(selected)) {
            result.addView(text("You chose: " + selected, 16, MUTED, false));
        }
        result.addView(text(question.explanation, 15, MUTED, false));
        if (roundComplete) {
            addRoundActions(result, question.mode);
        } else {
            Button next = primaryButton(LABEL_NEXT, colorForGameMode(question.mode));
            next.setOnClickListener(v -> renderGameQuestion(question.mode));
            result.addView(next);
        }
        addGamesButton(result);
        content.addView(result);
    }

    private void renderGameRoundComplete(KanjiGameEngine.GameMode mode) {
        base("home");
        content.addView(homeSectionHeader(mode.title, LABEL_GAMES, this::renderGames));
        content.addView(gameScorePanel(false));
        LinearLayout result = panelBox(Color.WHITE, softened(BLUE));
        result.addView(text(LABEL_ROUND_COMPLETE, 28, BLUE, true));
        addRoundSummary(result);
        addRoundActions(result, mode);
        addGamesButton(result);
        content.addView(result);
    }

    private void addRoundSummary(LinearLayout result) {
        result.addView(text(LABEL_FINAL_SCORE + gameCorrect + "/" + GAME_ROUND_QUESTIONS, 20, INK, true));
        result.addView(text("Accuracy: " + roundAccuracyPercent() + "%", 15, MUTED, false));
    }

    private String gameResultTitle(boolean roundComplete, boolean correct) {
        if (roundComplete) {
            return LABEL_ROUND_COMPLETE;
        }
        return correct ? "Correct" : "Not quite";
    }

    private int gameResultColor(boolean roundComplete, boolean correct) {
        if (roundComplete) {
            return BLUE;
        }
        return correct ? TEAL : CORAL;
    }

    private void addRoundActions(LinearLayout result, KanjiGameEngine.GameMode mode) {
        Button newRound = primaryButton(LABEL_NEW_ROUND, colorForGameMode(mode));
        newRound.setOnClickListener(v -> startGame(mode));
        result.addView(newRound);
    }

    private void addGamesButton(LinearLayout result) {
        Button games = secondaryButton(LABEL_GAMES);
        games.setOnClickListener(v -> renderGames());
        result.addView(games);
    }

    private boolean gameRoundComplete() {
        return gameAnswered >= GAME_ROUND_QUESTIONS;
    }

    private int gameRoundProgress(boolean awaitingAnswer) {
        int progress = gameAnswered + (awaitingAnswer ? 1 : 0);
        return Math.min(progress, GAME_ROUND_QUESTIONS);
    }

    private int roundAccuracyPercent() {
        if (gameAnswered == 0) {
            return 0;
        }
        return Math.round(gameCorrect * 100f / gameAnswered);
    }

    private int colorForGameMode(KanjiGameEngine.GameMode mode) {
        return switch (mode) {
            case MEANING_POP -> CORAL;
            case READING_RUSH -> TEAL;
            case CONFUSABLE_CLASH -> BLUE;
        };
    }

    private GameData gameData() {
        List<RecordsImportModels.DashboardRow> rows = store.activeDashboardRows();
        List<RecordsImportModels.KanjiInventoryItem> inventory = store.searchKanjiInventory("");
        List<RecordsImportModels.SimilarKanjiPair> pairs = store.allLocalSimilarPairs();
        return new GameData(rows, inventory, pairs);
    }

    private static final class GameData {
        final List<RecordsImportModels.DashboardRow> rows;
        final List<RecordsImportModels.KanjiInventoryItem> inventory;
        final List<RecordsImportModels.SimilarKanjiPair> pairs;

        GameData(
                List<RecordsImportModels.DashboardRow> rows,
                List<RecordsImportModels.KanjiInventoryItem> inventory,
                List<RecordsImportModels.SimilarKanjiPair> pairs
        ) {
            this.rows = rows == null ? new ArrayList<>() : rows;
            this.inventory = inventory == null ? new ArrayList<>() : inventory;
            this.pairs = pairs == null ? new ArrayList<>() : pairs;
        }

        boolean hasKanji() {
            return !rows.isEmpty() || !inventory.isEmpty();
        }
    }
}
