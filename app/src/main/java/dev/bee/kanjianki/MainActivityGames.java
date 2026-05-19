package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsImportModels;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.bee.kanjianki.core.KanjiGameCopy;
import dev.bee.kanjianki.core.KanjiGameEngine;
import dev.bee.kanjianki.core.KanjiGameRoundState;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

abstract class MainActivityGames extends MainActivityHome {
    private static final int GAME_ROUND_QUESTIONS = 10;

    private final KanjiGameEngine gameEngine = new KanjiGameEngine();
    private final Random gameRandom = new Random();
    private KanjiGameRoundState gameRound = KanjiGameRoundState.newRound(GAME_ROUND_QUESTIONS);

    void renderGames() {
        clearGameSession();
        base("home");
        content.addView(fullWidthHomeButton());
        content.addView(text(KanjiGameCopy.LABEL_GAMES, 34, INK, true));
        content.addView(text(KanjiGameCopy.GAMES_SUBTITLE, 16, MUTED, false));
        addSpace(8);

        GameData data = gameData();
        if (!data.hasKanji()) {
            emptyState(KanjiGameCopy.EMPTY_NO_KANJI_TITLE, KanjiGameCopy.EMPTY_NO_KANJI_BODY);
            Button sync = primaryButton(KanjiGameCopy.LABEL_SYNC_ANKIDROID, CORAL);
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
        gameRound = KanjiGameRoundState.newRound(GAME_ROUND_QUESTIONS);
    }

    private View gameModeCard(KanjiGameEngine.GameMode mode, boolean available) {
        int color = available ? colorForGameMode(mode) : Color.rgb(178, 178, 186);
        LinearLayout box = panelBox(Color.WHITE, softened(color));
        box.setPadding(dp(14), dp(13), dp(14), dp(13));
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(mode.title, 22, available ? INK : MUTED, true);
        top.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(chip(available ? KanjiGameCopy.LABEL_PLAY : KanjiGameCopy.LABEL_LOCKED, color));
        box.addView(top);
        box.addView(text(mode.label, 15, color, true));
        box.addView(text(KanjiGameCopy.modeBody(mode, available), 14, MUTED, false));
        box.setClickable(available);
        if (available) {
            box.setOnClickListener(v -> startGame(mode));
        }
        return box;
    }

    private void startGame(KanjiGameEngine.GameMode mode) {
        gameRound = KanjiGameRoundState.newRound(GAME_ROUND_QUESTIONS);
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
        content.addView(homeSectionHeader(mode.title, KanjiGameCopy.LABEL_GAMES, this::renderGames));
        content.addView(gameScorePanel(true));
        content.addView(gameQuestionCard(question));
    }

    private void renderGameUnavailable(KanjiGameEngine.GameMode mode) {
        base("home");
        content.addView(homeSectionHeader(mode.title, KanjiGameCopy.LABEL_GAMES, this::renderGames));
        emptyState(KanjiGameCopy.GAME_NOT_READY_TITLE, KanjiGameCopy.GAME_NOT_READY_BODY);
    }

    private LinearLayout gameScorePanel(boolean awaitingAnswer) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        int roundProgress = gameRound.progress(awaitingAnswer);
        row.addView(gameMetric(KanjiGameCopy.LABEL_ROUND, roundProgress + "/" + gameRound.totalQuestions, BLUE));
        row.addView(gameMetric(KanjiGameCopy.LABEL_SCORE, gameRound.correct + "/" + gameRound.totalQuestions, CORAL));
        row.addView(gameMetric(KanjiGameCopy.LABEL_STREAK, Integer.toString(gameRound.streak), TEAL));
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
        TextView prompt = text(question.prompt, KanjiGameCopy.promptTextSizeSp(question), INK, true);
        prompt.setGravity(Gravity.CENTER);
        prompt.setTypeface(fontResource(R.font.kaisei_tokumin_regular, Typeface.SERIF), Typeface.BOLD);
        prompt.setPadding(0, dp(6), 0, dp(6));
        card.addView(prompt);
        card.addView(text(question.promptDetail, 16, MUTED, false));
        addGameChoices(card, question);
        return card;
    }

    private void addGameChoices(LinearLayout card, KanjiGameEngine.GameQuestion question) {
        for (String choice : question.choices) {
            Button button = secondaryButton(KanjiGameCopy.choiceLabel(question, choice));
            button.setTextSize(KanjiGameCopy.choiceTextSizeSp(question));
            button.setMaxLines(2);
            button.setOnClickListener(v -> answerGameQuestion(question, choice));
            if (KanjiGameCopy.choiceUsesKanjiTypography(question)) {
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

    private void answerGameQuestion(KanjiGameEngine.GameQuestion question, String selected) {
        boolean correct = question.isCorrect(selected);
        gameRound = gameRound.answer(correct);
        renderGameResult(question, selected, correct);
    }

    private void renderGameResult(KanjiGameEngine.GameQuestion question, String selected, boolean correct) {
        base("home");
        content.addView(homeSectionHeader(question.mode.title, KanjiGameCopy.LABEL_GAMES, this::renderGames));
        content.addView(gameScorePanel(false));

        boolean roundComplete = gameRoundComplete();
        int color = gameResultColor(roundComplete, correct);
        LinearLayout result = panelBox(Color.WHITE, softened(color));
        result.addView(text(KanjiGameCopy.resultTitle(roundComplete, correct), 28, color, true));
        if (roundComplete) {
            addRoundSummary(result);
        }
        result.addView(text(KanjiGameCopy.answerText(question.correctAnswer), 18, INK, true));
        if (!question.isCorrect(selected)) {
            result.addView(text(KanjiGameCopy.selectedAnswerText(selected), 16, MUTED, false));
        }
        result.addView(text(question.explanation, 15, MUTED, false));
        if (roundComplete) {
            Button newRound = primaryButton(KanjiGameCopy.LABEL_NEW_ROUND, colorForGameMode(question.mode));
            newRound.setOnClickListener(v -> startGame(question.mode));
            result.addView(newRound);
        } else {
            Button next = primaryButton(KanjiGameCopy.LABEL_NEXT, colorForGameMode(question.mode));
            next.setOnClickListener(v -> renderGameQuestion(question.mode));
            result.addView(next);
        }
        Button games = secondaryButton(KanjiGameCopy.LABEL_GAMES);
        games.setOnClickListener(v -> renderGames());
        result.addView(games);
        content.addView(result);
    }

    private void renderGameRoundComplete(KanjiGameEngine.GameMode mode) {
        base("home");
        content.addView(homeSectionHeader(mode.title, KanjiGameCopy.LABEL_GAMES, this::renderGames));
        content.addView(gameScorePanel(false));
        LinearLayout result = panelBox(Color.WHITE, softened(BLUE));
        result.addView(text(KanjiGameCopy.LABEL_ROUND_COMPLETE, 28, BLUE, true));
        addRoundSummary(result);
        Button newRound = primaryButton(KanjiGameCopy.LABEL_NEW_ROUND, colorForGameMode(mode));
        newRound.setOnClickListener(v -> startGame(mode));
        result.addView(newRound);
        Button games = secondaryButton(KanjiGameCopy.LABEL_GAMES);
        games.setOnClickListener(v -> renderGames());
        result.addView(games);
        content.addView(result);
    }

    private void addRoundSummary(LinearLayout result) {
        result.addView(text(KanjiGameCopy.finalScoreText(gameRound.correct, gameRound.totalQuestions), 20, INK, true));
        result.addView(text(KanjiGameCopy.accuracyText(gameRound.correct, gameRound.answered), 15, MUTED, false));
    }

    private int gameResultColor(boolean roundComplete, boolean correct) {
        if (roundComplete) {
            return BLUE;
        }
        return correct ? TEAL : CORAL;
    }

    private boolean gameRoundComplete() {
        return gameRound.roundComplete();
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
