package dev.bee.kanjianki;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.StudyTaskCopy;
import dev.bee.kanjianki.core.StudyTextCopy;
import dev.bee.kanjianki.core.study.HintState;

final class MainActivityStudyFlashcard {
    private final MainActivityStudy activity;
    private final MainActivityStudyFlashcardInteraction interaction;

    MainActivityStudyFlashcard(MainActivityStudy activity) {
        this.activity = activity;
        this.interaction = new MainActivityStudyFlashcardInteraction(activity);
    }

    void renderFlashcardSession(RecordsSchedulerModels.StudySession session) {
        resetFlashcardSession();

        LinearLayout card = recognitionHeroCard(session);
        activity.flashcardCard = card;
        activity.flashcardGestureArea = card;

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, 0, 1);
        cardLp.setMargins(0, 0, 0, activity.dp(14));
        activity.content.addView(card, cardLp);
        interaction.buildFlashcardActionBar(false);
    }

    void resetFlashcardSession() {
        activity.prepareStudyContent(activity.activeStudyPlan, true);
        activity.activeSimilarWritingRepair = null;
        activity.activeAnalysis = null;
        activity.checkingWriting = false;
        activity.flashcardAnswerRevealed = false;
        activity.flashcardTouchTracking = false;
        activity.typingAnswerInput = null;
        activity.hintsUsed = 0;
        activity.setHintState(HintState.initial());
        activity.drawingPad = null;
        activity.flashcardHeroPanel = null;
        activity.hideStudyActionBar();
    }

    LinearLayout recognitionHeroCard(RecordsSchedulerModels.StudySession session) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(activity.dp(18), activity.dp(18), activity.dp(18), activity.dp(18));
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(activity.panel(Color.WHITE, Color.TRANSPARENT, activity.dp(32)));
        card.setElevation(activity.dp(8));
        card.setClickable(true);
        card.setFocusable(true);

        card.addView(recognitionPill(StudyTaskCopy.studyModeLabel(session)));

        TextView title = activity.text(StudyTaskCopy.flashcardTitle(session), 21, activity.STUDY_HERO_PLUM, true);
        title.setGravity(Gravity.CENTER);
        title.setIncludeFontPadding(false);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, activity.dp(14), 0, 0);
        card.addView(title, titleLp);

        TextView question = activity.text(StudyTextCopy.heroQuestion(session), 27, activity.STUDY_HERO_PLUM, true);
        question.setGravity(Gravity.CENTER);
        question.setIncludeFontPadding(false);
        LinearLayout.LayoutParams questionLp = new LinearLayout.LayoutParams(-1, -2);
        questionLp.setMargins(0, activity.dp(8), 0, 0);
        card.addView(question, questionLp);

        TextView hiddenHint = activity.text("Answer hidden until reveal", 14, activity.STUDY_HERO_MUTED, false);
        hiddenHint.setGravity(Gravity.CENTER);
        hiddenHint.setIncludeFontPadding(false);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1, -2);
        hintLp.setMargins(0, activity.dp(6), 0, 0);
        card.addView(hiddenHint, hintLp);
        activity.addStudyReasonLine(card, session);

        activity.flashcardHeroPanel = heroKanjiPanel(session);
        card.addView(activity.flashcardHeroPanel);

        if (StudyTaskCopy.isTypingMeaningTask(session)) {
            TextView label = activity.text(activity.LABEL_MEANING, 15, activity.STUDY_HERO_MUTED, true);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1, -2);
            labelLp.setMargins(0, activity.dp(14), 0, activity.dp(8));
            card.addView(label, labelLp);
            card.addView(typingAnswerField());
        }

        activity.studyAnswerPanel = flashcardAnswerPanel(session);
        activity.studyAnswerPanel.setVisibility(View.GONE);
        card.addView(activity.studyAnswerPanel);

        return card;
    }

    View recognitionPill(String label) {
        return MainActivityStudyFlashcardContentCompose.recognitionPillView(activity, label);
    }

    View heroKanjiPanel(RecordsSchedulerModels.StudySession session) {
        return MainActivityStudyFlashcardContentCompose.heroKanjiPanelView(
                activity,
                new FlashcardHeroPanelModel(
                        StudyTaskCopy.isWordReadingTask(session) ? StudyTextCopy.wordPrompt(session) : session.item.kanji,
                        StudyTaskCopy.isWordReadingTask(session) ? 44 : 116,
                        StudyTaskCopy.isFontRecognitionTask(session) ? randomFontVariantTypeface() : Typeface.DEFAULT
                )
        );
    }

    Typeface randomFontVariantTypeface() {
        return StudyFontVariants.random(activity);
    }

    View flashcardAnswerPanel(RecordsSchedulerModels.StudySession session) {
        return MainActivityStudyAnswerCompose.flashcardAnswerPanelView(activity, session);
    }

    View typingAnswerField() {
        activity.typingAnswerInput = new EditText(activity);
        activity.typingAnswerInput.setSingleLine(true);
        activity.typingAnswerInput.setTextSize(20);
        activity.typingAnswerInput.setTextColor(activity.STUDY_PLUM);
        activity.typingAnswerInput.setHintTextColor(activity.STUDY_MUTED);
        activity.typingAnswerInput.setHint(activity.LABEL_MEANING);
        activity.typingAnswerInput.setPadding(activity.dp(16), 0, activity.dp(16), 0);
        activity.typingAnswerInput.setBackground(activity.panel(Color.WHITE, activity.STUDY_BORDER, activity.dp(18)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, activity.dp(58));
        lp.setMargins(0, activity.dp(4), 0, activity.dp(4));
        activity.typingAnswerInput.setLayoutParams(lp);
        return activity.typingAnswerInput;
    }

    Typeface fontResource(int fontRes, Typeface fallback) {
        try {
            return activity.getResources().getFont(fontRes);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    void buildFlashcardActionBar(boolean revealed) {
        interaction.buildFlashcardActionBar(revealed);
    }

    void revealFlashcardAnswer() {
        interaction.revealFlashcardAnswer();
    }

    void expandFlashcardForAnswer() {
        interaction.expandFlashcardForAnswer();
    }

    boolean handleFlashcardGesture(MotionEvent event) {
        return interaction.handleFlashcardGesture(event);
    }

    boolean handleFlashcardRelease(MotionEvent event) {
        return interaction.handleFlashcardRelease(event);
    }

    boolean isTouchInsideView(View view, MotionEvent event) {
        return interaction.isTouchInsideView(view, event);
    }
}
