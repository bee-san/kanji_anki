package dev.bee.kanjianki;

import android.view.View;
import android.widget.LinearLayout;

import dev.bee.kanjianki.core.MeaningKanjiChoicePlanner;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.SimilarKanjiChoicePlanner;
import dev.bee.kanjianki.core.StudyTaskCopy;
import dev.bee.kanjianki.core.StudyTextCopy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

final class MainActivityStudyChoiceSessions {
    private static final String LABEL_CHOOSE_KANJI = "Choose the kanji";
    private final MainActivityStudy home;
    private final MeaningKanjiChoicePlanner meaningKanjiChoicePlanner = new MeaningKanjiChoicePlanner();
    private final Random meaningChoiceRandom = new Random();

    MainActivityStudyChoiceSessions(MainActivityStudy home) {
        this.home = home;
    }

    void renderMeaningKanjiSession(RecordsSchedulerModels.StudySession session) {
        home.resetChoiceSession(true);

        RecordsImportModels.MeaningKanjiChoiceCard choiceCard = meaningKanjiChoiceCardForSession(session);
        if (choiceCard == null || choiceCard.choices.size() < 4) {
            home.renderFlashcardSession(session);
            return;
        }

        LinearLayout cardShell = home.softStudyCard();
        cardShell.addView(home.modePill("Recall"));
        cardShell.addView(home.text(LABEL_CHOOSE_KANJI, 30, home.STUDY_PLUM, true));
        cardShell.addView(home.text(StudyTaskCopy.labelForTask(session.taskType), 16, home.STUDY_PINK_DARK, true));
        cardShell.addView(home.text("Pick the kanji that matches the meaning.", 15, home.STUDY_MUTED, false));
        home.addStudyReasonLine(cardShell, session);

        LinearLayout box = home.softInsetPanel();
        box.addView(home.text(StudyTextCopy.meaningKanjiChoiceQuestion(choiceCard, session.prompt), 22, home.STUDY_PLUM, true));
        View answerPanel = home.flashcardAnswerPanel(session);
        answerPanel.setVisibility(View.GONE);
        box.addView(home.meaningKanjiGrid(choiceCard, answerPanel));
        box.addView(answerPanel);
        cardShell.addView(box);

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

    void renderSimilarKanjiSession(RecordsSchedulerModels.StudySession session) {
        home.resetChoiceSession(false);

        RecordsImportModels.SimilarKanjiChoiceCard choiceCard = similarChoiceCardForSession(session);
        List<String> choices = new ArrayList<>(choiceCard.choices);
        if (choices.size() < 2) {
            home.renderFlashcardSession(session);
            return;
        }
        Collections.shuffle(choices);

        LinearLayout cardShell = home.softStudyCard();
        cardShell.addView(home.modePill("Recognise"));
        cardShell.addView(home.text(LABEL_CHOOSE_KANJI, 30, home.STUDY_PLUM, true));
        cardShell.addView(home.text(MainActivityBase.LABEL_SIMILAR_KANJI, 16, home.STUDY_PINK_DARK, true));
        cardShell.addView(home.text("Pick the kanji that matches the meaning.", 15, home.STUDY_MUTED, false));
        home.addStudyReasonLine(cardShell, session);
        LinearLayout box = home.softInsetPanel();
        String meaning = choiceCard.primaryMeaning;
        box.addView(home.text("Which kanji means " + meaning + "?", 22, home.STUDY_PLUM, true));
        box.addView(home.similarKanjiGrid(choices, choiceCard));
        cardShell.addView(box);
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
}
