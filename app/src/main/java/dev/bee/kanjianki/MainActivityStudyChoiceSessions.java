package dev.bee.kanjianki;

import android.view.View;
import android.widget.LinearLayout;

import dev.bee.kanjianki.core.MeaningKanjiChoicePlanner;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.SimilarKanjiChoicePlanner;
import dev.bee.kanjianki.core.StudyTaskCopy;
import dev.bee.kanjianki.core.StudyTextCopy;
import dev.bee.kanjianki.core.study.HintState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static dev.bee.kanjianki.MainActivityStudyChoiceResultCompose.meaningKanjiChoiceResultActionBarView;

final class MainActivityStudyChoiceSessions {
    private static final String LABEL_CHOOSE_KANJI = "Choose the kanji";
    private final MainActivityStudy home;
    private final MeaningKanjiChoicePlanner meaningKanjiChoicePlanner = new MeaningKanjiChoicePlanner();
    private final Random meaningChoiceRandom = new Random();

    MainActivityStudyChoiceSessions(MainActivityStudy home) {
        this.home = home;
    }

    void renderMeaningKanjiSession(RecordsSchedulerModels.StudySession session) {
        resetChoiceSession(true);

        RecordsImportModels.MeaningKanjiChoiceCard choiceCard = meaningKanjiChoiceCardForSession(session);
        if (choiceCard == null || choiceCard.choices.size() < 4) {
            home.renderFlashcardSession(session);
            return;
        }

        StudyAnswerPanelModel answerPanel = home.flashcardAnswerPanelModel(session);
        String reason = StudyTextCopy.studyReasonLine(
                home.activeSimilarWritingRepair != null,
                session,
                home.settings().matureSupportThreshold,
                System.currentTimeMillis()
        );
        View cardShell = MainActivityStudyChoiceCompose.meaningKanjiSessionView(
                home,
                new MeaningChoiceSessionModel(
                        "Recall",
                        LABEL_CHOOSE_KANJI,
                        StudyTaskCopy.labelForTask(session.taskType),
                        "Pick the kanji that matches the meaning.",
                        reason,
                        StudyTextCopy.meaningKanjiChoiceQuestion(choiceCard, session.prompt),
                        choiceCard.choices,
                        answerPanel,
                        glyph -> showMeaningKanjiChoiceResult(choiceCard, glyph)
                )
        );

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, 0, 1);
        cardLp.setMargins(0, home.dp(6), 0, home.dp(12));
        home.content.addView(cardShell, cardLp);
    }

    RecordsImportModels.MeaningKanjiChoiceCard meaningKanjiChoiceCardForSession(RecordsSchedulerModels.StudySession session) {
        if (session == null || session.row == null) {
            return null;
        }
        return meaningKanjiChoicePlanner.buildChoiceCard(
                session.row,
                home.store.activeDashboardRows(),
                home.store.searchKanjiInventory(""),
                meaningChoiceRandom
        );
    }

    void showMeaningKanjiChoiceResult(RecordsImportModels.MeaningKanjiChoiceCard card, String selectedKanji) {
        boolean correct = card.isCorrect(selectedKanji);
        if (home.studyActionBar == null) {
            home.submitReview(correct ? home.RATING_GOOD : home.RATING_AGAIN, false);
            return;
        }
        home.styleStudyActionBarShell();
        home.studyActionBar.removeAllViews();
        home.studyActionBar.setVisibility(View.VISIBLE);
        String prompt = home.activeSession == null ? "" : home.activeSession.prompt;
        String status = StudyTextCopy.meaningKanjiChoiceResult(card, prompt, correct);
        home.studyActionBar.addView(meaningKanjiChoiceResultActionBarView(
                home,
                status,
                correct ? home.TEAL : home.CORAL,
                () -> home.submitReview(correct ? home.RATING_GOOD : home.RATING_AGAIN, false)
        ));
    }

    void renderSimilarKanjiSession(RecordsSchedulerModels.StudySession session) {
        resetChoiceSession(false);

        RecordsImportModels.SimilarKanjiChoiceCard choiceCard = similarChoiceCardForSession(session);
        List<String> choices = new ArrayList<>(choiceCard.choices);
        if (choices.size() < 2) {
            home.renderFlashcardSession(session);
            return;
        }
        Collections.shuffle(choices);

        String meaning = choiceCard.primaryMeaning;
        String reason = StudyTextCopy.studyReasonLine(
                home.activeSimilarWritingRepair != null,
                session,
                home.settings().matureSupportThreshold,
                System.currentTimeMillis()
        );
        View cardShell = MainActivityStudyChoiceCompose.similarKanjiSessionView(
                home,
                new SimilarChoiceSessionModel(
                        "Recognise",
                        LABEL_CHOOSE_KANJI,
                        MainActivityBase.LABEL_SIMILAR_KANJI,
                        "Pick the kanji that matches the meaning.",
                        reason,
                        "Which kanji means " + meaning + "?",
                        new SimilarChoiceGridModel(
                                choices,
                                true,
                                glyph -> home.submitSimilarKanjiChoice(choiceCard, glyph)
                        )
                )
        );
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, 0, 1);
        cardLp.setMargins(0, home.dp(6), 0, home.dp(12));
        home.content.addView(cardShell, cardLp);
    }

    RecordsImportModels.SimilarKanjiChoiceCard similarChoiceCardForSession(RecordsSchedulerModels.StudySession session) {
        long now = System.currentTimeMillis();
        RecordsImportModels.SimilarKanjiChoiceCard stored = home.store.dueSimilarChoiceForActiveTarget(session.item.kanji, now);
        String meaning = session.row == null ? "" : StudyTextCopy.rowMeaning(session.row);
        return SimilarKanjiChoicePlanner.choiceCardForSession(
                stored,
                session.item.kanji,
                meaning,
                home.store.similarPairsForKanji(session.item.kanji)
        );
    }

    List<String> buildSimilarKanjiChoices(String targetKanji) {
        return SimilarKanjiChoicePlanner.fallbackChoices(
                targetKanji,
                home.store.similarPairsForKanji(targetKanji)
        );
    }

    void resetChoiceSession(boolean resetTouchTracking) {
        home.prepareStudyContent(home.activeStudyPlan, true);
        home.activeSimilarWritingRepair = null;
        home.activeAnalysis = null;
        home.checkingWriting = false;
        home.flashcardAnswerRevealed = false;
        if (resetTouchTracking) {
            home.flashcardTouchTracking = false;
        }
        home.flashcardGestureArea = null;
        home.typingAnswerInput = null;
        home.drawingPad = null;
        home.hintsUsed = 0;
        home.setHintState(HintState.initial());
        home.hideStudyActionBar();
    }
}
