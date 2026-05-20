package dev.bee.kanjianki;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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

    View meaningKanjiGrid(RecordsImportModels.MeaningKanjiChoiceCard card, View answerPanel) {
        return kanjiChoiceGrid(
                card.choices,
                (glyph, grid) -> home.showMeaningKanjiChoiceResult(card, glyph, grid, answerPanel),
                false
        );
    }

    View kanjiChoiceGrid(List<String> choices, MainActivityStudy.KanjiChoiceClickHandler clickHandler, boolean balanceLastRow) {
        LinearLayout grid = new LinearLayout(home);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = null;
        for (int i = 0; i < choices.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(home);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row);
            }
            String glyph = choices.get(i);
            Button button = kanjiChoiceButton(glyph);
            button.setOnClickListener(new ViewClickListener(v -> clickHandler.onClick(glyph, grid)));
            if (row != null) {
                row.addView(button, kanjiChoiceLayoutParams());
            }
        }
        if (balanceLastRow && choices.size() % 2 == 1 && grid.getChildCount() > 0) {
            addKanjiChoiceSpacer(grid);
        }
        return grid;
    }

    Button kanjiChoiceButton(String glyph) {
        Button button = home.studySecondaryButton(glyph);
        button.setTextColor(home.STUDY_PLUM);
        button.setTextSize(34);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(home.panel(Color.rgb(255, 245, 250), home.STUDY_BORDER, home.dp(20)));
        return button;
    }

    LinearLayout.LayoutParams kanjiChoiceLayoutParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, home.dp(82), 1);
        lp.setMargins(home.dp(4), home.dp(8), home.dp(4), 0);
        return lp;
    }

    void addKanjiChoiceSpacer(LinearLayout grid) {
        LinearLayout lastRow = (LinearLayout) grid.getChildAt(grid.getChildCount() - 1);
        MainActivityUiSupport.SpaceView spacer = new MainActivityUiSupport.SpaceView(home);
        lastRow.addView(spacer, kanjiChoiceLayoutParams());
    }

    void showMeaningKanjiChoiceResult(RecordsImportModels.MeaningKanjiChoiceCard card, String selectedKanji, View grid, View answerPanel) {
        boolean correct = card.isCorrect(selectedKanji);
        disableChoiceButtons(grid);
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

    void disableChoiceButtons(View view) {
        if (view instanceof Button button) {
            button.setEnabled(false);
            return;
        }
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                disableChoiceButtons(group.getChildAt(i));
            }
        }
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
