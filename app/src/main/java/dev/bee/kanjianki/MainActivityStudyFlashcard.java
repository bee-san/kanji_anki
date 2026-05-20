package dev.bee.kanjianki;

import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;

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

        View card = recognitionHeroCard(session);
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
        activity.typingAnswerState = null;
        activity.hintsUsed = 0;
        activity.setHintState(HintState.initial());
        activity.drawingPad = null;
        activity.flashcardHeroPanel = null;
        activity.hideStudyActionBar();
    }

    View recognitionHeroCard(RecordsSchedulerModels.StudySession session) {
        FlashcardRevealState revealState = new FlashcardRevealState(false);
        activity.flashcardRevealState = revealState;
        activity.flashcardHeroPanel = null;
        activity.studyAnswerPanel = null;
        FlashcardHeroPanelModel heroPanel = heroKanjiPanelModel(session);
        TypingAnswerState typingAnswer = null;
        if (StudyTaskCopy.isTypingMeaningTask(session)) {
            typingAnswer = typingAnswerField();
        }

        StudyAnswerPanelModel answerPanel = flashcardAnswerPanelModel(session);

        return MainActivityStudyFlashcardContentCompose.flashcardCardView(
                activity,
                new FlashcardCardModel(
                        flashcardPromptHeaderModel(session),
                        heroPanel,
                        typingAnswer,
                        answerPanel,
                        revealState
                )
        );
    }

    FlashcardPromptHeaderModel flashcardPromptHeaderModel(RecordsSchedulerModels.StudySession session) {
        return new FlashcardPromptHeaderModel(
                StudyTaskCopy.studyModeLabel(session),
                StudyTaskCopy.flashcardTitle(session),
                StudyTextCopy.heroQuestion(session),
                "Answer hidden until reveal",
                activity.studyReasonLine(session)
        );
    }

    View heroKanjiPanel(RecordsSchedulerModels.StudySession session) {
        return MainActivityStudyFlashcardContentCompose.heroKanjiPanelView(
                activity,
                heroKanjiPanelModel(session)
        );
    }

    FlashcardHeroPanelModel heroKanjiPanelModel(RecordsSchedulerModels.StudySession session) {
        return new FlashcardHeroPanelModel(
                StudyTaskCopy.isWordReadingTask(session) ? StudyTextCopy.wordPrompt(session) : session.item.kanji,
                StudyTaskCopy.isWordReadingTask(session) ? 44 : 116,
                StudyTaskCopy.isFontRecognitionTask(session) ? randomFontVariantTypeface() : Typeface.DEFAULT
        );
    }

    Typeface randomFontVariantTypeface() {
        return StudyFontVariants.random(activity);
    }

    View flashcardAnswerPanel(RecordsSchedulerModels.StudySession session) {
        return MainActivityStudyAnswerCompose.flashcardAnswerPanelView(activity, session);
    }

    StudyAnswerPanelModel flashcardAnswerPanelModel(RecordsSchedulerModels.StudySession session) {
        return MainActivityStudyAnswerCompose.flashcardAnswerPanelModel(activity, session);
    }

    TypingAnswerState typingAnswerField() {
        activity.typingAnswerState = new TypingAnswerState();
        return activity.typingAnswerState;
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
