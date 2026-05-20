package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsImportModels;
import android.view.View;

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
        content.addView(MainActivityGamesCompose.gamesScreenView(this, gamesScreenModel()));
    }

    private void clearGameSession() {
        gameRound = KanjiGameRoundState.newRound(GAME_ROUND_QUESTIONS);
    }

    GamesScreenModel gamesScreenModel() {
        GameData data = gameData();
        List<GamesModeCardModel> cards = new ArrayList<>();
        if (data.hasKanji()) {
            List<KanjiGameEngine.GameMode> available = gameEngine.availableModes(data.rows, data.inventory, data.pairs);
            for (KanjiGameEngine.GameMode mode : KanjiGameEngine.GameMode.values()) {
                boolean modeAvailable = available.contains(mode);
                cards.add(new GamesModeCardModel(
                        mode.title,
                        mode.label,
                        KanjiGameCopy.modeBody(mode, modeAvailable),
                        colorForGameMode(mode),
                        modeAvailable,
                        modeAvailable ? KanjiGameCopy.LABEL_PLAY : KanjiGameCopy.LABEL_LOCKED,
                        modeAvailable ? () -> startGame(mode) : () -> { }
                ));
            }
        }
        return new GamesScreenModel(
                KanjiGameCopy.LABEL_GAMES,
                KanjiGameCopy.GAMES_SUBTITLE,
                data.hasKanji() ? null : KanjiGameCopy.EMPTY_NO_KANJI_TITLE,
                data.hasKanji() ? null : KanjiGameCopy.EMPTY_NO_KANJI_BODY,
                !data.hasKanji(),
                this::confirmSync,
                cards
        );
    }

    void startGame(KanjiGameEngine.GameMode mode) {
        gameRound = KanjiGameRoundState.newRound(GAME_ROUND_QUESTIONS);
        renderGameQuestion(mode);
    }

    private void renderGameQuestion(KanjiGameEngine.GameMode mode) {
        if (gameRound.roundComplete()) {
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
        content.addView(MainActivityGamesCompose.gamesUnavailableView(
                this,
                new GamesUnavailableModel(KanjiGameCopy.GAME_NOT_READY_TITLE, KanjiGameCopy.GAME_NOT_READY_BODY)
        ));
    }

    private View gameScorePanel(boolean awaitingAnswer) {
        int roundProgress = gameRound.progress(awaitingAnswer);
        return MainActivityGamesCompose.gamesScorePanelView(
                this,
                new GamesScoreStripModel(
                        KanjiGameCopy.LABEL_ROUND,
                        roundProgress + "/" + gameRound.totalQuestions,
                        KanjiGameCopy.LABEL_SCORE,
                        gameRound.correct + "/" + gameRound.totalQuestions,
                        KanjiGameCopy.LABEL_STREAK,
                        Integer.toString(gameRound.streak)
                )
        );
    }

    private View gameQuestionCard(KanjiGameEngine.GameQuestion question) {
        return MainActivityGamesCompose.gamesQuestionCardView(
                this,
                question,
                choice -> {
                    answerGameQuestion(question, choice);
                    return kotlin.Unit.INSTANCE;
                }
        );
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

        boolean roundComplete = gameRound.roundComplete();
        int color = gameResultTitleColor(roundComplete, correct);
        content.addView(MainActivityGamesCompose.gamesResultView(
                this,
                new GamesResultModel(
                        KanjiGameCopy.resultTitle(roundComplete, correct),
                        color,
                        roundComplete ? KanjiGameCopy.finalScoreText(gameRound.correct, gameRound.totalQuestions) : null,
                        roundComplete ? KanjiGameCopy.accuracyText(gameRound.correct, gameRound.answered) : null,
                        KanjiGameCopy.answerText(question.correctAnswer),
                        question.isCorrect(selected) ? null : KanjiGameCopy.selectedAnswerText(selected),
                        question.explanation,
                        roundComplete ? KanjiGameCopy.LABEL_NEW_ROUND : KanjiGameCopy.LABEL_NEXT,
                        colorForGameMode(question.mode),
                        roundComplete ? () -> startGame(question.mode) : () -> renderGameQuestion(question.mode),
                        this::renderGames
                )
        ));
    }

    private void renderGameRoundComplete(KanjiGameEngine.GameMode mode) {
        base("home");
        content.addView(homeSectionHeader(mode.title, KanjiGameCopy.LABEL_GAMES, this::renderGames));
        content.addView(gameScorePanel(false));
        content.addView(MainActivityGamesCompose.gamesResultView(
                this,
                new GamesResultModel(
                        KanjiGameCopy.LABEL_ROUND_COMPLETE,
                        BLUE,
                        KanjiGameCopy.finalScoreText(gameRound.correct, gameRound.totalQuestions),
                        KanjiGameCopy.accuracyText(gameRound.correct, gameRound.answered),
                        null,
                        null,
                        null,
                        KanjiGameCopy.LABEL_NEW_ROUND,
                        colorForGameMode(mode),
                        () -> startGame(mode),
                        this::renderGames
                )
        ));
    }

    private int gameResultTitleColor(boolean roundComplete, boolean correct) {
        if (roundComplete) {
            return BLUE;
        }
        return correct ? TEAL : CORAL;
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

    private static final class RunnableClickListener implements View.OnClickListener {
        private final Runnable action;

        RunnableClickListener(Runnable action) {
            this.action = action;
        }

        @Override
        public void onClick(View v) {
            action.run();
        }
    }
}
