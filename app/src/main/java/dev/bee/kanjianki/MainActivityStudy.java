package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import android.Manifest;
import android.app.Activity;
import android.app.TimePickerDialog;
import android.content.res.ColorStateList;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.widget.TextViewCompat;

import dev.bee.kanjianki.backup.DatabaseBackupScheduler;
import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.anki.CollectionGateway;
import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.DictionaryLookup;
import dev.bee.kanjianki.core.FlashcardGesturePolicy;
import dev.bee.kanjianki.core.SchedulerTuner;
import dev.bee.kanjianki.core.StudyExampleSelector;
import dev.bee.kanjianki.core.StudyLayoutPolicy;
import dev.bee.kanjianki.core.StudyMoreNewCardsPolicy;
import dev.bee.kanjianki.core.StudySessionFocusPolicy;
import dev.bee.kanjianki.core.StudySessionRoute;
import dev.bee.kanjianki.core.StudyTextCopy;
import dev.bee.kanjianki.core.TextUtil;
import dev.bee.kanjianki.core.TypingAnswerMatcher;
import dev.bee.kanjianki.core.study.HintProgression;
import dev.bee.kanjianki.core.study.HintState;
import dev.bee.kanjianki.core.study.RecognitionCandidate;
import dev.bee.kanjianki.core.study.StrokeGuide;
import dev.bee.kanjianki.core.study.StrokeGuideGuard;
import dev.bee.kanjianki.core.study.WritingActionPresentation;
import dev.bee.kanjianki.core.study.WritingAnalysis;
import dev.bee.kanjianki.core.study.WritingHintPolicy;
import dev.bee.kanjianki.core.study.WritingSample;
import dev.bee.kanjianki.data.DictionaryAssets;
import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.reminders.ReminderScheduler;
import dev.bee.kanjianki.study.CapturedWriting;
import dev.bee.kanjianki.study.MlKitJapaneseWritingRecognizer;
import dev.bee.kanjianki.study.WritingRecognizer;
import dev.bee.kanjianki.sync.AutoSyncScheduler;
import dev.bee.kanjianki.sync.ManualSyncEngine;
import dev.bee.kanjianki.sync.SyncSettings;
import dev.bee.kanjianki.update.AutoUpdateScheduler;
import dev.bee.kanjianki.update.GitHubUpdater;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

abstract class MainActivityStudy extends MainActivityStats {
    interface KanjiChoiceClickHandler {
        void onClick(String glyph, LinearLayout grid);
    }

    static final class CapturedWritingAttempt {
        final CapturedWriting captured;
        final WritingSample sample;

        CapturedWritingAttempt(CapturedWriting captured, WritingSample sample) {
            this.captured = captured;
            this.sample = sample;
        }
    }

    private final MainActivityStudyFlashcard flashcardUi = new MainActivityStudyFlashcard(this);
    private final MainActivityStudyWritingUi writingUi = new MainActivityStudyWritingUi(this);
    private final MainActivityStudyWritingFlow writingFlow = new MainActivityStudyWritingFlow(this);
    private final MainActivityStudyWritingCheck writingCheck = new MainActivityStudyWritingCheck(this);
    private final MainActivityStudyReviewFlow writingReview = new MainActivityStudyReviewFlow(this);
    private final MainActivityStudyChoiceGrid choiceGrid = new MainActivityStudyChoiceGrid(this);
    private final MainActivityStudyDoneActions doneActions = new MainActivityStudyDoneActions(this);
    private final MainActivityStudyChoiceSessions choiceSessions = new MainActivityStudyChoiceSessions(this);
    private final MainActivityStudyWritingSession writingSession = new MainActivityStudyWritingSession(this);
    private final MainActivityStudyTargetedLaunch targetedLaunch = new MainActivityStudyTargetedLaunch(this);

    View learningPanel(RecordsSchedulerModels.StudySession session) {
        LinearLayout box = softInsetPanel();
        box.addView(text("Reference", 19, STUDY_PLUM, true));
        box.addView(studyAnswerDetailsRow(session, 72));
        box.addView(text("Trace it below, then check.", 13, MUTED, false));
        return box;
    }

    RecordsImportModels.Example firstExample(RecordsImportModels.DashboardRow row) {
        return StudyExampleSelector.firstExample(row);
    }

    RecordsImportModels.Example wordReadingExample(RecordsImportModels.DashboardRow row) {
        return StudyExampleSelector.wordReadingExample(row);
    }

    RecordsImportModels.Example exampleForSession(RecordsSchedulerModels.StudySession session) {
        return StudyExampleSelector.exampleForSession(session);
    }

    void renderStudy() {
        base(NAV_STUDY);
        List<RecordsImportModels.DashboardRow> rows = store.activeDashboardRows();
        long now = System.currentTimeMillis();
        RecordsBase.StudyLadderSettings ladder = studyLadderSettings();
        activeStudyPlan = rows.isEmpty() ? null : studyPlanForMode(rows, store.studyItems(), now);
        if (renderPendingRepairOrDone(activeStudyPlan, now, ladder)) {
            return;
        }
        if (rows.isEmpty()) {
            renderEmptyStudyQueue();
            return;
        }
        List<RecordsStudyModels.StudyItem> beforeSeed = store.studyItems();
        RecordsSchedulerModels.AdaptiveLoadPlan plan = studyPlanForMode(rows, beforeSeed, now);
        List<RecordsStudyModels.StudyItem> seeded = studyQueue(rows, now, true, plan);
        RecordsSchedulerModels.AdaptiveLoadPlan seededPlan = studyPlanForMode(rows, seeded, now);
        activeStudyPlan = seededPlan;
        if (renderPendingRepairOrDone(seededPlan, now, ladder)) {
            return;
        }
        activeSession = new BridgeScheduler().nextSession(
                seeded,
                rows,
                now,
                studyAheadMillis(),
                StudySessionFocusPolicy.allowedKanji(seededPlan, continueAllKanjiSession),
                settings(),
                studyLadderSettings()
        );
        activeSimilarWritingRepair = null;
        if (activeSession == null) {
            renderNoStudySession(seededPlan);
            return;
        }
        StudySessionActions.activateStudySession(
                activeSession,
                now,
                store::saveStudyItem,
                this::registerStudyTaskShown,
                this::startActiveStudyTask
        );
        renderSession(activeSession);
    }

    boolean renderPendingRepairOrDone(
            RecordsSchedulerModels.AdaptiveLoadPlan plan,
            long now,
            RecordsBase.StudyLadderSettings ladder
    ) {
        initializeSessionProgressTarget(plan);
        if (ladder.isEnabled(RecordsBase.LadderRung.WRITE_KANJI)) {
            for (RecordsImportModels.SimilarKanjiWritingRepair repair : store.dueSimilarWritingRepairs(now)) {
                studySessionTracker.includePendingTask(similarRepairProgressKey(repair));
            }
            RecordsImportModels.SimilarKanjiWritingRepair repair = store.nextDueSimilarWritingRepair(now);
            if (repair != null) {
                renderSimilarWritingRepair(repair, plan, now);
                return true;
            }
        }
        if (studySessionTracker.atHardCap(continueAllKanjiSession)) {
            doneActions.renderStudyRunDone(plan);
            return true;
        }
        return false;
    }

    void renderEmptyStudyQueue() {
        doneActions.renderEmptyStudyQueue();
    }

    void renderNoStudySession(RecordsSchedulerModels.AdaptiveLoadPlan seededPlan) {
        doneActions.renderNoStudySession(seededPlan);
    }

    void renderFocusDone(RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        doneActions.renderFocusDone(plan);
    }

    void renderStudyRunDone(RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        doneActions.renderStudyRunDone(plan);
    }

    void addDoneStudyActions(LinearLayout card) {
        doneActions.addDoneStudyActions(card);
    }

    int availableStudyMoreNewCards() {
        return doneActions.availableStudyMoreNewCards();
    }

    void showStudyMoreNewCardsDialog(int availableAtOpen) {
        doneActions.showStudyMoreNewCardsDialog(availableAtOpen);
    }

    boolean applyStudyMoreNewCardsRequest(EditText countInput) {
        return doneActions.applyStudyMoreNewCardsRequest(countInput);
    }

    int requestedStudyMoreNewCards(EditText countInput) {
        StudyMoreNewCardsPolicy.RequestDecision decision = StudyMoreNewCardsPolicy.requestedCount(countInput.getText().toString());
        if (!decision.accepted()) {
            Toast.makeText(this, decision.message(), Toast.LENGTH_SHORT).show();
            return -1;
        }
        return decision.requestedCount();
    }

    boolean startStudyMoreNewCards(int requestedCount) {
        List<RecordsImportModels.DashboardRow> rows = store.activeDashboardRows();
        if (rows.isEmpty()) {
            Toast.makeText(this, StudyMoreNewCardsPolicy.NO_NEW_CARDS_AVAILABLE_MESSAGE, Toast.LENGTH_SHORT).show();
            return false;
        }
        long now = System.currentTimeMillis();
        BridgeScheduler.ExtraNewCardsResult result = new BridgeScheduler().seedExtraNewCards(
                rows,
                store.studyItems(),
                settings(),
                now,
                startOfDay(now),
                requestedCount,
                studyLadderSettings()
        );
        if (!result.admittedAny()) {
            Toast.makeText(this, StudyMoreNewCardsPolicy.NO_NEW_CARDS_AVAILABLE_MESSAGE, Toast.LENGTH_SHORT).show();
            return false;
        }
        StudyMoreNewCardActions.AdmissionResult admission = StudyMoreNewCardActions.applyAdmission(
                result,
                new MainActivityStudyMoreNewCardWriter(this),
                studyMoreNewCardKanji,
                this::resetStudyRunProgress,
                studySessionTracker::setTargetCount
        );
        continueAllKanjiSession = false;
        if (admission.admittedCount() < requestedCount) {
            Toast.makeText(this, StudyMoreNewCardsPolicy.partialAvailabilityMessage(admission.admittedCount()), Toast.LENGTH_SHORT).show();
        }
        renderStudy();
        return true;
    }

    void startFocusedStudy() {
        clearStudyModeOverrides();
        resetStudyRunProgress();
        renderStudy();
    }

    void renderStudyForKanji(String kanji) {
        targetedLaunch.renderStudyForKanji(kanji);
    }

    void renderStudyForKanjiNotAvailable() {
        doneActions.renderStudyForKanjiNotAvailable();
    }

    void renderSession(RecordsSchedulerModels.StudySession session) {
        switch (StudySessionRoute.destination(session)) {
            case WRITING:
                renderWritingSession(session);
                break;
            case SIMILAR_KANJI:
                renderSimilarKanjiSession(session);
                break;
            case MEANING_KANJI:
                renderMeaningKanjiSession(session);
                break;
            case FLASHCARD:
            default:
                renderFlashcardSession(session);
                break;
        }
    }

    void renderMeaningKanjiSession(RecordsSchedulerModels.StudySession session) {
        choiceSessions.renderMeaningKanjiSession(session);
    }

    RecordsImportModels.MeaningKanjiChoiceCard meaningKanjiChoiceCardForSession(RecordsSchedulerModels.StudySession session) {
        return choiceSessions.meaningKanjiChoiceCardForSession(session);
    }

    View meaningKanjiGrid(RecordsImportModels.MeaningKanjiChoiceCard card, View answerPanel) {
        return choiceGrid.meaningKanjiGrid(card, answerPanel);
    }

    View kanjiChoiceGrid(List<String> choices, KanjiChoiceClickHandler clickHandler, boolean balanceLastRow) {
        return choiceGrid.kanjiChoiceGrid(choices, clickHandler, balanceLastRow);
    }

    Button kanjiChoiceButton(String glyph) {
        return choiceGrid.kanjiChoiceButton(glyph);
    }

    LinearLayout.LayoutParams kanjiChoiceLayoutParams() {
        return choiceGrid.kanjiChoiceLayoutParams();
    }

    void addKanjiChoiceSpacer(LinearLayout grid) {
        choiceGrid.addKanjiChoiceSpacer(grid);
    }

    void showMeaningKanjiChoiceResult(RecordsImportModels.MeaningKanjiChoiceCard card, String selectedKanji, View grid, View answerPanel) {
        choiceGrid.showMeaningKanjiChoiceResult(card, selectedKanji, grid, answerPanel);
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

    void renderSimilarKanjiSession(RecordsSchedulerModels.StudySession session) {
        choiceSessions.renderSimilarKanjiSession(session);
    }

    RecordsImportModels.SimilarKanjiChoiceCard similarChoiceCardForSession(RecordsSchedulerModels.StudySession session) {
        return choiceSessions.similarChoiceCardForSession(session);
    }

    List<String> buildSimilarKanjiChoices(String targetKanji) {
        return choiceSessions.buildSimilarKanjiChoices(targetKanji);
    }

    View similarKanjiGrid(List<String> choices, RecordsImportModels.SimilarKanjiChoiceCard card) {
        return choiceGrid.similarKanjiGrid(choices, card);
    }

    View similarKanjiGrid(List<String> choices, String correctKanji) {
        return choiceGrid.similarKanjiGrid(choices, correctKanji);
    }

    void renderFlashcardSession(RecordsSchedulerModels.StudySession session) {
        flashcardUi.renderFlashcardSession(session);
    }

    LinearLayout recognitionHeroCard(RecordsSchedulerModels.StudySession session) {
        return flashcardUi.recognitionHeroCard(session);
    }

    View recognitionPill(String label) {
        return flashcardUi.recognitionPill(label);
    }

    View heroKanjiPanel(RecordsSchedulerModels.StudySession session) {
        return flashcardUi.heroKanjiPanel(session);
    }

    Typeface randomFontVariantTypeface() {
        return flashcardUi.randomFontVariantTypeface();
    }

    void renderWritingSession(RecordsSchedulerModels.StudySession session) {
        writingSession.renderWritingSession(session);
    }

    void resetChoiceSession(boolean resetTouchTracking) {
        prepareStudyContent(activeStudyPlan, true);
        activeSimilarWritingRepair = null;
        activeAnalysis = null;
        checkingWriting = false;
        flashcardAnswerRevealed = false;
        if (resetTouchTracking) {
            flashcardTouchTracking = false;
        }
        flashcardGestureArea = null;
        typingAnswerInput = null;
        drawingPad = null;
        hintsUsed = 0;
        setHintState(HintState.initial());
        hideStudyActionBar();
    }

    void resetFlashcardSession() {
        prepareStudyContent(activeStudyPlan, true);
        activeSimilarWritingRepair = null;
        activeAnalysis = null;
        checkingWriting = false;
        flashcardAnswerRevealed = false;
        flashcardTouchTracking = false;
        typingAnswerInput = null;
        hintsUsed = 0;
        setHintState(HintState.initial());
        drawingPad = null;
        flashcardHeroPanel = null;
        hideStudyActionBar();
    }

    void resetWritingSession(RecordsSchedulerModels.StudySession session) {
        writingSession.resetWritingSession(session);
    }

    void hideStudyActionBar() {
        writingSession.hideStudyActionBar();
    }

    void addStudyReasonLine(LinearLayout card, RecordsSchedulerModels.StudySession session) {
        String reason = StudyTextCopy.studyReasonLine(
                activeSimilarWritingRepair != null,
                session,
                settings().matureSupportThreshold,
                System.currentTimeMillis()
        );
        if (!reason.isEmpty()) {
            card.addView(text(reason, 14, STUDY_MUTED, false));
        }
    }

    void renderSimilarWritingRepair(RecordsImportModels.SimilarKanjiWritingRepair repair, RecordsSchedulerModels.AdaptiveLoadPlan plan, long now) {
        writingSession.renderSimilarWritingRepair(repair, plan, now);
    }

    void resetStudyRunProgress() {
        activeSimilarWritingRepair = null;
        studySessionTracker.resetProgress();
    }

    void clearStudyModeOverrides() {
        continueAllKanjiSession = false;
        studyMoreNewCardKanji.clear();
    }

    void markStudyRunPassed(String kanji) {
        if (activeSession != null) {
            markStudyTaskCompleted(sessionTaskKey(activeSession));
            return;
        }
        if (kanji != null && !kanji.isEmpty()) {
            markStudyTaskCompleted("kanji:" + kanji);
        }
    }

    void initializeSessionProgressTarget(RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        studySessionTracker.initializeTarget(plan);
    }

    void registerStudyTaskShown(String key) {
        studySessionTracker.registerTaskShown(key);
    }

    void markStudyTaskCompleted(String key) {
        studySessionTracker.markTaskCompleted(key);
    }

    String sessionTaskKey(RecordsSchedulerModels.StudySession session) {
        return StudySessionTracker.sessionTaskKey(session);
    }

    String similarRepairProgressKey(RecordsImportModels.SimilarKanjiWritingRepair repair) {
        return StudySessionTracker.similarRepairProgressKey(repair);
    }

    String similarRepairStudyTaskKey(RecordsImportModels.SimilarKanjiWritingRepair repair) {
        return StudySessionTracker.similarRepairStudyTaskKey(repair);
    }

    void startActiveStudyTask(String key, String kanji, String taskType, long startedAt) {
        studySessionTracker.startActiveTask(key, kanji, taskType, startedAt, !activityPaused);
    }

    void completeActiveStudyTask(String key, String outcome, long answeredAt) {
        studySessionTracker.completeActiveTask(store, key, outcome, answeredAt, true);
    }

    void pauseActiveStudyTask() {
        studySessionTracker.pauseActiveTask();
    }

    void resumeActiveStudyTask() {
        studySessionTracker.resumeActiveTask();
    }

    void abandonActiveStudyTask() {
        studySessionTracker.abandonActiveTask();
    }

    View typingAnswerField() {
        return flashcardUi.typingAnswerField();
    }

    Typeface fontResource(int fontRes, Typeface fallback) {
        return flashcardUi.fontResource(fontRes, fallback);
    }

    View flashcardAnswerPanel(RecordsSchedulerModels.StudySession session) {
        return flashcardUi.flashcardAnswerPanel(session);
    }

    LinearLayout studyAnswerDetailsRow(RecordsSchedulerModels.StudySession session, int glyphSize) {
        return flashcardUi.studyAnswerDetailsRow(session, glyphSize);
    }

    void addStudyCueLines(LinearLayout details, RecordsSchedulerModels.StudySession session) {
        flashcardUi.addStudyCueLines(details, session);
    }

    DictionaryLookup currentDictionaryLookup() {
        if (dictionaryLookup == null) {
            dictionaryLookup = DictionaryAssets.load(this);
        }
        return dictionaryLookup;
    }

    void buildFlashcardActionBar(boolean revealed) {
        flashcardUi.buildFlashcardActionBar(revealed);
    }

    void revealFlashcardAnswer() {
        flashcardUi.revealFlashcardAnswer();
    }

    void expandFlashcardForAnswer() {
        flashcardUi.expandFlashcardForAnswer();
    }

    boolean handleFlashcardGesture(MotionEvent event) {
        return flashcardUi.handleFlashcardGesture(event);
    }

    boolean handleFlashcardRelease(MotionEvent event) {
        return flashcardUi.handleFlashcardRelease(event);
    }

    boolean isTouchInsideView(View view, MotionEvent event) {
        return flashcardUi.isTouchInsideView(view, event);
    }

    void buildStudyActionBar() {
        writingUi.buildStudyActionBar();
    }

    void refreshWritingModelStatus() {
        writingUi.refreshWritingModelStatus();
    }

    void eraseWritingPad() {
        writingFlow.eraseWritingPad();
    }

    void startGuidedWritingRetry() {
        writingFlow.startGuidedWritingRetry();
    }

    int studyPadHeight() {
        float density = getResources().getDisplayMetrics().density;
        int screenDp = Math.round(getResources().getDisplayMetrics().heightPixels / density);
        return studyPadHeightForScreenDp(screenDp);
    }

    int studyPadHeightForScreenDp(int screenDp) {
        return dp(StudyLayoutPolicy.writingPadHeightDp(screenDp));
    }

    void checkWriting() {
        writingCheck.checkWriting();
    }

    void submitSimilarKanjiChoice(RecordsImportModels.SimilarKanjiChoiceCard card, String selectedKanji) {
        writingReview.submitSimilarKanjiChoice(card, selectedKanji);
    }

    boolean showNoInkWhenNeeded() {
        return writingFlow.showNoInkWhenNeeded();
    }

    void showModelUnavailable(String message) {
        writingFlow.showModelUnavailable(message);
    }

    void recognizeWriting(WritingRecognizer recognizer, CapturedWriting captured, WritingSample sample, StrokeGuide guide, String target, String token) {
        writingCheck.recognizeWriting(recognizer, captured, sample, guide, target, token);
    }

    void submitReview(String rating, boolean override) {
        writingReview.submitReview(rating, override);
    }

    void completeActiveRepairStudyTask(String key, String outcome, long answeredAt) {
        studySessionTracker.completeActiveTask(store, key, outcome, answeredAt, false);
    }

    void tuneSchedulerIfNeeded(RecordsSchedulerModels.SchedulerParameters parameters, long now) {
        RecordsSchedulerModels.SchedulerParameters tuned = new SchedulerTuner().maybeTune(parameters, store.reviewStatsSince(now - SchedulerTuner.MONTH_MILLIS), now);
        StudyReviewActions.saveTunedSchedulerIfChanged(parameters, tuned, store::saveSchedulerParameters);
    }

    HintState initialHintState(RecordsSchedulerModels.StudySession session) {
        return WritingHintPolicy.initialHintState(
                session.item.writingLevel,
                session.item.totalReviews,
                session.item.learningStep,
                TASK_TARGETED_WRITING.equals(session.taskType)
        );
    }

    void setHintState(HintState state) {
        currentHintState = state == null ? HintState.initial() : state;
        currentPracticeLevel = currentHintState.level().writingLevel();
    }

    void showWritingHint() {
        writingFlow.showWritingHint();
    }

    void showAnalysis(WritingAnalysis analysis) {
        writingFlow.showAnalysis(analysis);
    }

    void updateResultActions() {
        writingUi.updateResultActions();
    }

    WritingActionPresentation writingActionPresentation() {
        return writingUi.writingActionPresentation();
    }

    void updateUndoStrokeButton() {
        writingUi.updateUndoStrokeButton();
    }

    void startCleanerRetry() {
        writingFlow.startCleanerRetry();
    }

    void undoWritingStroke() {
        writingFlow.undoWritingStroke();
    }

    void replayWritingAnalysis() {
        writingFlow.replayWritingAnalysis();
    }

    void handleDrawingEdited() {
        writingFlow.handleDrawingEdited();
    }

    void clearWritingResult() {
        writingFlow.clearWritingResult();
    }

    void handleDrawingBlocked(StrokeGuideGuard.Decision decision) {
        writingFlow.handleDrawingBlocked(decision);
    }

    void setStudyStatus(String value, int color) {
        writingUi.setStudyStatus(value, color);
    }

    void setResultStatus(String value, int color) {
        writingUi.setResultStatus(value, color);
    }

    boolean canRevealMoreHelp() {
        return writingUi.canRevealMoreHelp();
    }

}
