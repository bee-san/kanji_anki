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
import android.view.WindowInsets;
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
import dev.bee.kanjianki.core.StudyExampleSelector;
import dev.bee.kanjianki.core.StudyLayoutPolicy;
import dev.bee.kanjianki.core.StudyMoreNewCardsPolicy;
import dev.bee.kanjianki.core.StudySessionFocusPolicy;
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
    final MainActivityStudyDoneActions doneActions = new MainActivityStudyDoneActions(this);
    private final MainActivityStudyChoiceSessions choiceSessions = new MainActivityStudyChoiceSessions(this);
    private final MainActivityStudyProgress studyProgress = new MainActivityStudyProgress(this);
    private final MainActivityStudyScreen studyScreen = new MainActivityStudyScreen(this);
    private final MainActivityStudyMoreNewCards moreNewCards = new MainActivityStudyMoreNewCards(this);
    private final MainActivityStudyState studyState = new MainActivityStudyState(this);
    private final MainActivityStudyWritingSession writingSession = new MainActivityStudyWritingSession(this);
    final MainActivityStudyTargetedLaunch targetedLaunch = new MainActivityStudyTargetedLaunch(this);
    private final MainActivityStudyReasonLine reasonLine = new MainActivityStudyReasonLine(this);
    private final MainActivityStudySessionRouter sessionRouter = new MainActivityStudySessionRouter(this);

    View learningPanel(RecordsSchedulerModels.StudySession session) {
        return MainActivityStudyAnswerCompose.learningPanelView(this, session);
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
        studyScreen.renderStudy();
    }

    boolean renderPendingRepairOrDone(
            RecordsSchedulerModels.AdaptiveLoadPlan plan,
            long now,
            RecordsBase.StudyLadderSettings ladder
    ) {
        return studyScreen.renderPendingRepairOrDone(plan, now, ladder);
    }

    void renderEmptyStudyQueue() {
        studyScreen.renderEmptyStudyQueue();
    }

    void renderNoStudySession(RecordsSchedulerModels.AdaptiveLoadPlan seededPlan) {
        studyScreen.renderNoStudySession(seededPlan);
    }

    void renderFocusDone(RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        studyScreen.renderFocusDone(plan);
    }

    void renderStudyRunDone(RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        studyScreen.renderStudyRunDone(plan);
    }

    int availableStudyMoreNewCards() {
        return moreNewCards.availableStudyMoreNewCards();
    }

    void showStudyMoreNewCardsDialog(int availableAtOpen) {
        moreNewCards.showStudyMoreNewCardsDialog(availableAtOpen);
    }

    boolean applyStudyMoreNewCardsRequest(EditText countInput) {
        return moreNewCards.applyStudyMoreNewCardsRequest(countInput);
    }

    int requestedStudyMoreNewCards(EditText countInput) {
        return moreNewCards.requestedStudyMoreNewCards(countInput);
    }

    boolean startStudyMoreNewCards(int requestedCount) {
        return moreNewCards.startStudyMoreNewCards(requestedCount);
    }

    void startFocusedStudy() {
        clearStudyModeOverrides();
        resetStudyRunProgress();
        renderStudy();
    }

    void renderStudyForKanji(String kanji) {
        studyScreen.renderStudyForKanji(kanji);
    }

    void renderStudyForKanjiNotAvailable() {
        studyScreen.renderStudyForKanjiNotAvailable();
    }

    void renderSession(RecordsSchedulerModels.StudySession session) {
        sessionRouter.renderSession(session);
    }

    void renderMeaningKanjiSession(RecordsSchedulerModels.StudySession session) {
        choiceSessions.renderMeaningKanjiSession(session);
    }

    RecordsImportModels.MeaningKanjiChoiceCard meaningKanjiChoiceCardForSession(RecordsSchedulerModels.StudySession session) {
        return choiceSessions.meaningKanjiChoiceCardForSession(session);
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

    void renderFlashcardSession(RecordsSchedulerModels.StudySession session) {
        flashcardUi.renderFlashcardSession(session);
    }

    View recognitionHeroCard(RecordsSchedulerModels.StudySession session) {
        return flashcardUi.recognitionHeroCard(session);
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

    void resetWritingSession(RecordsSchedulerModels.StudySession session) {
        writingSession.resetWritingSession(session);
    }

    void hideStudyActionBar() {
        writingSession.hideStudyActionBar();
    }

    String studyReasonLine(RecordsSchedulerModels.StudySession session) {
        return reasonLine.studyReasonLine(session);
    }

    void renderSimilarWritingRepair(RecordsImportModels.SimilarKanjiWritingRepair repair, RecordsSchedulerModels.AdaptiveLoadPlan plan, long now) {
        writingSession.renderSimilarWritingRepair(repair, plan, now);
    }

    void resetStudyRunProgress() {
        studyProgress.resetStudyRunProgress();
    }

    void clearStudyModeOverrides() {
        studyProgress.clearStudyModeOverrides();
    }

    void markStudyRunPassed(String kanji) {
        studyProgress.markStudyRunPassed(kanji);
    }

    void initializeSessionProgressTarget(RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        studyProgress.initializeSessionProgressTarget(plan);
    }

    void registerStudyTaskShown(String key) {
        studyProgress.registerStudyTaskShown(key);
    }

    void markStudyTaskCompleted(String key) {
        studyProgress.markStudyTaskCompleted(key);
    }

    String sessionTaskKey(RecordsSchedulerModels.StudySession session) {
        return studyProgress.sessionTaskKey(session);
    }

    String similarRepairProgressKey(RecordsImportModels.SimilarKanjiWritingRepair repair) {
        return studyProgress.similarRepairProgressKey(repair);
    }

    String similarRepairStudyTaskKey(RecordsImportModels.SimilarKanjiWritingRepair repair) {
        return studyProgress.similarRepairStudyTaskKey(repair);
    }

    void startActiveStudyTask(String key, String kanji, String taskType, long startedAt) {
        studyProgress.startActiveStudyTask(key, kanji, taskType, startedAt);
    }

    void completeActiveStudyTask(String key, String outcome, long answeredAt) {
        studyProgress.completeActiveStudyTask(key, outcome, answeredAt);
    }

    void pauseActiveStudyTask() {
        studyProgress.pauseActiveStudyTask();
    }

    void resumeActiveStudyTask() {
        studyProgress.resumeActiveStudyTask();
    }

    void abandonActiveStudyTask() {
        studyProgress.abandonActiveStudyTask();
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
        studyState.completeActiveRepairStudyTask(key, outcome, answeredAt);
    }

    void tuneSchedulerIfNeeded(RecordsSchedulerModels.SchedulerParameters parameters, long now) {
        studyState.tuneSchedulerIfNeeded(parameters, now);
    }

    HintState initialHintState(RecordsSchedulerModels.StudySession session) {
        return studyState.initialHintState(session);
    }

    void setHintState(HintState state) {
        studyState.setHintState(state);
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
