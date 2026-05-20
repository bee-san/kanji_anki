package dev.bee.kanjianki;

import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.SimilarKanjiChoicePlanner;
import dev.bee.kanjianki.core.StudyTextCopy;

import java.util.List;

final class MainActivityStudyChoiceGrid {
    private final MainActivityStudy home;

    MainActivityStudyChoiceGrid(MainActivityStudy home) {
        this.home = home;
    }

    void showMeaningKanjiChoiceResult(RecordsImportModels.MeaningKanjiChoiceCard card, String selectedKanji, View answerPanel) {
        boolean correct = card.isCorrect(selectedKanji);
        answerPanel.setVisibility(View.VISIBLE);
        if (home.studyActionBar == null) {
            home.submitReview(correct ? home.RATING_GOOD : home.RATING_AGAIN, false);
            return;
        }
        home.styleStudyActionBarShell();
        home.studyActionBar.removeAllViews();
        home.studyActionBar.setVisibility(View.VISIBLE);
        String prompt = home.activeSession == null ? "" : home.activeSession.prompt;
        String status = StudyTextCopy.meaningKanjiChoiceResult(card, prompt, correct);
        home.resultStatus = home.text(status, 15, correct ? home.TEAL : home.CORAL, true);
        home.studyActionBar.addView(home.resultStatus);
        Button next = home.pinkPrimaryButton("Next");
        next.setOnClickListener(new RunnableClickListener(() -> home.submitReview(correct ? home.RATING_GOOD : home.RATING_AGAIN, false)));
        home.studyActionBar.addView(next, new LinearLayout.LayoutParams(-1, home.dp(62)));
    }

    View similarKanjiGrid(List<String> choices, RecordsImportModels.SimilarKanjiChoiceCard card) {
        return MainActivityStudyChoiceCompose.similarKanjiGridView(
                home,
                new SimilarChoiceGridModel(
                        choices,
                        true,
                        glyph -> home.submitSimilarKanjiChoice(card, glyph)
                )
        );
    }

    View similarKanjiGrid(List<String> choices, String correctKanji) {
        return similarKanjiGrid(
                choices,
                new RecordsImportModels.SimilarKanjiChoiceCard(
                        correctKanji,
                        "",
                        choices,
                        SimilarKanjiChoicePlanner.choiceSignature(choices)
                )
        );
    }
}
