package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.res.ColorStateList;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
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
import dev.bee.kanjianki.core.HomeTextCopy;
import dev.bee.kanjianki.core.MeaningKanjiChoicePlanner;
import dev.bee.kanjianki.core.SchedulerTuner;
import dev.bee.kanjianki.core.SimilarKanjiChoicePlanner;
import dev.bee.kanjianki.core.StudyExampleSelector;
import dev.bee.kanjianki.core.StudyLayoutPolicy;
import dev.bee.kanjianki.core.StudyMoreNewCardsPolicy;
import dev.bee.kanjianki.core.StudySessionFocusPolicy;
import dev.bee.kanjianki.core.StudySessionRoute;
import dev.bee.kanjianki.core.StudyTaskCopy;
import dev.bee.kanjianki.core.StudyTextCopy;
import dev.bee.kanjianki.core.TextUtil;
import dev.bee.kanjianki.core.TypingAnswerMatcher;
import dev.bee.kanjianki.core.study.HintProgression;
import dev.bee.kanjianki.core.study.HintState;
import dev.bee.kanjianki.core.study.RecognitionCandidate;
import dev.bee.kanjianki.core.study.StrokeDiagnosis;
import dev.bee.kanjianki.core.study.StrokeGuide;
import dev.bee.kanjianki.core.study.StrokeGuideGuard;
import dev.bee.kanjianki.core.study.WritingActionPresentation;
import dev.bee.kanjianki.core.study.WritingAnalysis;
import dev.bee.kanjianki.core.study.WritingFeedbackCopy;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

abstract class MainActivityStudy extends MainActivityStats {
    private static final String LABEL_CHOOSE_KANJI = "Choose the kanji";

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

    private final MeaningKanjiChoicePlanner meaningKanjiChoicePlanner = new MeaningKanjiChoicePlanner();
    private final Random meaningChoiceRandom = new Random();

    private MainActivityStudyFlashcard flashcardUi() {
        return new MainActivityStudyFlashcard(this);
    }

    private MainActivityStudyWritingUi writingUi() {
        return new MainActivityStudyWritingUi(this);
    }

    private MainActivityStudyWritingFlow writingFlow() {
        return new MainActivityStudyWritingFlow(this);
    }

    private MainActivityStudyWritingCheck writingCheck() {
        return new MainActivityStudyWritingCheck(this);
    }

    private MainActivityStudyReviewFlow writingReview() {
        return new MainActivityStudyReviewFlow(this);
    }

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
            renderStudyRunDone(plan);
            return true;
        }
        return false;
    }

    void renderEmptyStudyQueue() {
        prepareStudyContent(activeStudyPlan, false);
        LinearLayout card = softStudyCard();
        card.addView(modePill(LABEL_PRACTICE));
        card.addView(text("Study practice", 32, STUDY_PLUM, true));
        card.addView(text("Nothing to study yet", 22, STUDY_PLUM, true));
        card.addView(text("Sync from AnkiDroid first. Study opens once the app finds problem kanji to repair.", 16, STUDY_MUTED, false));
        content.addView(card);
    }

    void renderNoStudySession(RecordsSchedulerModels.AdaptiveLoadPlan seededPlan) {
        if (!continueAllKanjiSession && seededPlan.focusComplete()) {
            renderFocusDone(seededPlan);
            return;
        }
        prepareStudyContent(seededPlan, false);
        LinearLayout card = softStudyCard();
        card.addView(modePill(LABEL_PRACTICE));
        card.addView(text("Nothing due now", 32, STUDY_PLUM, true));
        card.addView(text("Your active kanji are resting. Sync again if Anki has created new problem candidates, or come back when the next review is due.", 17, STUDY_MUTED, false));
        Button back = pinkPrimaryButton(LABEL_BACK_HOME);
        back.setOnClickListener(v -> renderHome());
        card.addView(back);
        content.addView(card);
    }

    void renderFocusDone(RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        prepareStudyContent(plan, false);
        LinearLayout card = softStudyCard();
        card.addView(modePill(LABEL_PRACTICE));
        card.addView(text(StudyTextCopy.studyDoneTitle(), 32, STUDY_PLUM, true));
        card.addView(text(StudyTextCopy.adaptiveFocusDoneBody(), 17, STUDY_MUTED, false));
        LinearLayout summary = softInsetPanel();
        summary.addView(text(StudyTextCopy.adaptiveFocusDoneSummary(plan.target), 20, STUDY_PLUM, true));
        summary.addView(text(plan.status, 15, STUDY_MUTED, false));
        card.addView(summary);
        addDoneStudyActions(card);
        content.addView(card);
    }

    void renderStudyRunDone(RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        prepareStudyContent(plan, false);
        LinearLayout card = softStudyCard();
        card.addView(modePill(LABEL_PRACTICE));
        card.addView(text(StudyTextCopy.studyDoneTitle(), 32, STUDY_PLUM, true));
        card.addView(text(StudyTextCopy.studyRunDoneBody(), 17, STUDY_MUTED, false));
        LinearLayout summary = softInsetPanel();
        summary.addView(text(StudyTextCopy.movedForwardSummary(studySessionTracker.movedForwardCount()), 20, STUDY_PLUM, true));
        summary.addView(text(StudyTextCopy.missedSummary(studySessionTracker.missedCount()), 16, STUDY_MUTED, false));
        summary.addView(text(StudyTextCopy.completedTaskSummary(studySessionTracker.completedCount()), 16, STUDY_MUTED, false));
        if (plan != null && !plan.status.isEmpty()) {
            summary.addView(text(plan.status, 15, STUDY_MUTED, false));
        }
        card.addView(summary);
        addDoneStudyActions(card);
        content.addView(card);
    }

    void addDoneStudyActions(LinearLayout card) {
        int available = availableStudyMoreNewCards();
        boolean canStudyMore = available > 0;
        if (canStudyMore) {
            Button studyMore = pinkPrimaryButton("Study more new cards");
            studyMore.setOnClickListener(v -> showStudyMoreNewCardsDialog(available));
            card.addView(studyMore);
        }
        Button keepGoing = canStudyMore ? studySecondaryButton(LABEL_CONTINUE_ALL_KANJI) : pinkPrimaryButton(LABEL_CONTINUE_ALL_KANJI);
        keepGoing.setOnClickListener(v -> {
            studyMoreNewCardKanji.clear();
            continueAllKanjiSession = true;
            renderStudy();
        });
        card.addView(keepGoing);
        Button back = studySecondaryButton(LABEL_BACK_HOME);
        back.setOnClickListener(v -> {
            clearStudyModeOverrides();
            renderHome();
        });
        card.addView(back);
    }

    int availableStudyMoreNewCards() {
        List<RecordsImportModels.DashboardRow> rows = store.activeDashboardRows();
        if (rows.isEmpty()) {
            return 0;
        }
        long now = System.currentTimeMillis();
        BridgeScheduler.ExtraNewCardsResult result = new BridgeScheduler().seedExtraNewCards(
                rows,
                store.studyItems(),
                settings(),
                now,
                startOfDay(now),
                Integer.MAX_VALUE,
                studyLadderSettings()
        );
        return result.availableCount;
    }

    void showStudyMoreNewCardsDialog(int availableAtOpen) {
        int defaultCount = StudyMoreNewCardsPolicy.defaultRequestCount(availableAtOpen);
        EditText countInput = thresholdInput(defaultCount);
        countInput.setHint(LABEL_NEW_CARDS);
        countInput.setContentDescription(LABEL_NEW_CARDS);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Study more new cards")
                .setMessage("How many extra new cards do you want to study now?")
                .setView(countInput)
                .setPositiveButton(LABEL_STUDY, null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(opened -> dialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (applyStudyMoreNewCardsRequest(countInput)) {
                dialog.dismiss();
            }
        }));
        dialog.show();
        countInput.requestFocus();
    }

    boolean applyStudyMoreNewCardsRequest(EditText countInput) {
        int requested = requestedStudyMoreNewCards(countInput);
        return requested > 0 && startStudyMoreNewCards(requested);
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
                new StudyMoreNewCardActions.StudyItemWriter() {
                    @Override
                    public List<RecordsStudyModels.StudyItem> annotateSimilarKanjiAvailability(List<RecordsStudyModels.StudyItem> items) {
                        return store.annotateSimilarKanjiAvailability(items);
                    }

                    @Override
                    public void replaceStudyItems(List<RecordsStudyModels.StudyItem> items) {
                        store.replaceStudyItems(items);
                    }
                },
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
        clearStudyModeOverrides();
        resetStudyRunProgress();
        base(NAV_STUDY);
        activeSimilarWritingRepair = null;
        List<RecordsImportModels.DashboardRow> rows = store.activeDashboardRows();
        long now = System.currentTimeMillis();
        activeStudyPlan = rows.isEmpty() ? null : adaptivePlan(rows, store.studyItems(), now);
        RecordsImportModels.DashboardRow row = findRow(rows, kanji);
        if (row == null) {
            prepareStudyContent(activeStudyPlan, false);
            LinearLayout card = softStudyCard();
            card.addView(modePill(LABEL_PRACTICE));
            card.addView(text("Study practice", 32, STUDY_PLUM, true));
            card.addView(text("Kanji not available", 22, STUDY_PLUM, true));
            card.addView(text("This row may have changed after sync.", 16, STUDY_MUTED, false));
            content.addView(card);
            return;
        }
        List<RecordsStudyModels.StudyItem> seeded = studyQueue(rows, now, true, activeStudyPlan);
        activeStudyPlan = adaptivePlan(rows, seeded, now);
        activeSession = new BridgeScheduler().targetedSession(
                seeded,
                row,
                now,
                studyLadderSettings()
        );
        StudySessionActions.activateStudySession(
                activeSession,
                now,
                store::saveStudyItem,
                this::registerStudyTaskShown,
                this::startActiveStudyTask
        );
        renderSession(activeSession);
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
        resetChoiceSession(true);

        RecordsImportModels.MeaningKanjiChoiceCard choiceCard = meaningKanjiChoiceCardForSession(session);
        if (choiceCard == null || choiceCard.choices.size() < 4) {
            renderFlashcardSession(session);
            return;
        }

        LinearLayout cardShell = softStudyCard();
        cardShell.addView(modePill("Recall"));
        cardShell.addView(text(LABEL_CHOOSE_KANJI, 30, STUDY_PLUM, true));
        cardShell.addView(text(StudyTaskCopy.labelForTask(session.taskType), 16, STUDY_PINK_DARK, true));
        cardShell.addView(text("Pick the kanji that matches the meaning.", 15, STUDY_MUTED, false));
        addStudyReasonLine(cardShell, session);

        LinearLayout box = softInsetPanel();
        box.addView(text(StudyTextCopy.meaningKanjiChoiceQuestion(choiceCard, session.prompt), 22, STUDY_PLUM, true));
        View answerPanel = flashcardAnswerPanel(session);
        answerPanel.setVisibility(View.GONE);
        box.addView(meaningKanjiGrid(choiceCard, answerPanel));
        box.addView(answerPanel);
        cardShell.addView(box);

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, 0, 1);
        cardLp.setMargins(0, dp(6), 0, dp(12));
        content.addView(cardShell, cardLp);
    }

    RecordsImportModels.MeaningKanjiChoiceCard meaningKanjiChoiceCardForSession(RecordsSchedulerModels.StudySession session) {
        if (session == null || session.row == null) {
            return null;
        }
        return meaningKanjiChoicePlanner.buildChoiceCard(
                session.row,
                store.activeDashboardRows(),
                store.searchKanjiInventory(""),
                meaningChoiceRandom
        );
    }

    View meaningKanjiGrid(RecordsImportModels.MeaningKanjiChoiceCard card, View answerPanel) {
        return kanjiChoiceGrid(
                card.choices,
                (glyph, grid) -> showMeaningKanjiChoiceResult(card, glyph, grid, answerPanel),
                false
        );
    }

    View kanjiChoiceGrid(List<String> choices, KanjiChoiceClickHandler clickHandler, boolean balanceLastRow) {
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = null;
        for (int i = 0; i < choices.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row);
            }
            String glyph = choices.get(i);
            Button button = kanjiChoiceButton(glyph);
            button.setOnClickListener(v -> clickHandler.onClick(glyph, grid));
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
        Button button = studySecondaryButton(glyph);
        button.setTextColor(STUDY_PLUM);
        button.setTextSize(34);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setBackground(panel(Color.rgb(255, 245, 250), STUDY_BORDER, dp(20)));
        return button;
    }

    LinearLayout.LayoutParams kanjiChoiceLayoutParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(82), 1);
        lp.setMargins(dp(4), dp(8), dp(4), 0);
        return lp;
    }

    void addKanjiChoiceSpacer(LinearLayout grid) {
        LinearLayout lastRow = (LinearLayout) grid.getChildAt(grid.getChildCount() - 1);
        SpaceView spacer = new SpaceView(this);
        lastRow.addView(spacer, kanjiChoiceLayoutParams());
    }

    void showMeaningKanjiChoiceResult(RecordsImportModels.MeaningKanjiChoiceCard card, String selectedKanji, View grid, View answerPanel) {
        boolean correct = card.isCorrect(selectedKanji);
        disableChoiceButtons(grid);
        answerPanel.setVisibility(View.VISIBLE);
        if (studyActionBar == null) {
            submitReview(correct ? RATING_GOOD : RATING_AGAIN, false);
            return;
        }
        styleStudyActionBarShell();
        studyActionBar.removeAllViews();
        studyActionBar.setVisibility(View.VISIBLE);
        String prompt = activeSession == null ? "" : activeSession.prompt;
        String status = StudyTextCopy.meaningKanjiChoiceResult(card, prompt, correct);
        resultStatus = text(status, 15, correct ? TEAL : CORAL, true);
        studyActionBar.addView(resultStatus);
        Button next = pinkPrimaryButton("Next");
        next.setOnClickListener(v -> submitReview(correct ? RATING_GOOD : RATING_AGAIN, false));
        studyActionBar.addView(next, new LinearLayout.LayoutParams(-1, dp(62)));
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
        resetChoiceSession(false);

        RecordsImportModels.SimilarKanjiChoiceCard choiceCard = similarChoiceCardForSession(session);
        List<String> choices = new ArrayList<>(choiceCard.choices);
        if (choices.size() < 2) {
            // Not enough similar kanji to show a choice — fall back to
            // standard flashcard for this card.
            renderFlashcardSession(session);
            return;
        }
        Collections.shuffle(choices);

        LinearLayout cardShell = softStudyCard();
        cardShell.addView(modePill("Recognise"));
        cardShell.addView(text(LABEL_CHOOSE_KANJI, 30, STUDY_PLUM, true));
        cardShell.addView(text(LABEL_SIMILAR_KANJI, 16, STUDY_PINK_DARK, true));
        cardShell.addView(text("Pick the kanji that matches the meaning.", 15, STUDY_MUTED, false));
        addStudyReasonLine(cardShell, session);
        LinearLayout box = softInsetPanel();
        String meaning = choiceCard.primaryMeaning;
        box.addView(text("Which kanji means " + meaning + "?", 22, STUDY_PLUM, true));
        box.addView(similarKanjiGrid(choices, choiceCard));
        cardShell.addView(box);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, 0, 1);
        cardLp.setMargins(0, dp(6), 0, dp(12));
        content.addView(cardShell, cardLp);
    }

    RecordsImportModels.SimilarKanjiChoiceCard similarChoiceCardForSession(RecordsSchedulerModels.StudySession session) {
        long now = System.currentTimeMillis();
        RecordsImportModels.SimilarKanjiChoiceCard stored = store.dueSimilarChoiceForActiveTarget(session.item.kanji, now);
        String meaning = session.row == null ? "" : StudyTextCopy.rowMeaning(session.row);
        return SimilarKanjiChoicePlanner.choiceCardForSession(
                stored,
                session.item.kanji,
                meaning,
                store.similarPairsForKanji(session.item.kanji)
        );
    }

    List<String> buildSimilarKanjiChoices(String targetKanji) {
        return SimilarKanjiChoicePlanner.fallbackChoices(
                targetKanji,
                store.similarPairsForKanji(targetKanji)
        );
    }

    View similarKanjiGrid(List<String> choices, RecordsImportModels.SimilarKanjiChoiceCard card) {
        return kanjiChoiceGrid(choices, (glyph, grid) -> submitSimilarKanjiChoice(card, glyph), true);
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

    void renderFlashcardSession(RecordsSchedulerModels.StudySession session) {
        flashcardUi().renderFlashcardSession(session);
    }

    LinearLayout recognitionHeroCard(RecordsSchedulerModels.StudySession session) {
        return flashcardUi().recognitionHeroCard(session);
    }

    View recognitionPill(String label) {
        return flashcardUi().recognitionPill(label);
    }

    View heroKanjiPanel(RecordsSchedulerModels.StudySession session) {
        return flashcardUi().heroKanjiPanel(session);
    }

    Typeface randomFontVariantTypeface() {
        return flashcardUi().randomFontVariantTypeface();
    }

    void renderWritingSession(RecordsSchedulerModels.StudySession session) {
        resetWritingSession(session);

        LinearLayout card = softStudyCard();
        card.addView(modePill(LABEL_PRACTICE));
        card.addView(text("Draw this kanji", 30, STUDY_PLUM, true));
        card.addView(text(StudyTaskCopy.labelForTask(session.taskType), 16, STUDY_PINK_DARK, true));
        addStudyReasonLine(card, session);
        if (session.row != null) {
            if (StudyTaskCopy.isRecallTask(session)) {
                card.addView(text("Prompt: " + StudyTextCopy.sessionClue(currentDictionaryLookup(), session), 17, STUDY_PLUM, true));
                if (!session.row.reading.isEmpty()) {
                    card.addView(text("Reading: " + session.row.reading, 15, STUDY_MUTED, false));
                }
                card.addView(text("Write the kanji from this prompt. The answer stays hidden until you check.", 15, STUDY_MUTED, false));
            } else {
                card.addView(text("Learn it from the reference, trace it, then check.", 15, STUDY_MUTED, false));
            }
        } else {
            card.addView(text(session.prompt, 17, STUDY_MUTED, false));
        }
        studyAnswerPanel = learningPanel(session);
        card.addView(studyAnswerPanel);

        TextView writingTitle = sectionTitle("Writing");
        writingTitle.setTextColor(STUDY_PLUM);
        card.addView(writingTitle);
        StrokeGuide guide = strokeGuide(session.item.kanji);
        studyStatus = text(WritingFeedbackCopy.guideLabel(currentHintState, guide), 16, STUDY_MUTED, false);
        card.addView(studyStatus);
        drawingPad = new DrawingPadView(this);
        drawingPad.setTarget(session.item.kanji);
        drawingPad.setInkEditListener(this::handleDrawingEdited);
        drawingPad.setStrokeBlockedListener(this::handleDrawingBlocked);
        drawingPad.setGuide(guide, currentHintState, false);
        LinearLayout padShell = softInsetPanel();
        padShell.setPadding(dp(8), dp(8), dp(8), dp(8));
        SquarePadFrame squarePad = new SquarePadFrame(this, studyPadHeight());
        squarePad.addView(drawingPad);
        padShell.addView(squarePad, new LinearLayout.LayoutParams(-1, -2));
        card.addView(padShell);
        resultStatus = text("", 16, STUDY_MUTED, false);
        resultStatus.setVisibility(View.GONE);
        card.addView(resultStatus);
        content.addView(card);

        buildStudyActionBar();
        updateResultActions();
        refreshWritingModelStatus();
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
        prepareStudyContent(activeStudyPlan, false);
        activeAnalysis = null;
        checkingWriting = false;
        flashcardGestureArea = null;
        flashcardAnswerRevealed = false;
        flashcardTouchTracking = false;
        typingAnswerInput = null;
        hintsUsed = 0;
        setHintState(initialHintState(session));
    }

    void hideStudyActionBar() {
        if (studyActionBar != null) {
            studyActionBar.removeAllViews();
            studyActionBar.setVisibility(View.GONE);
        }
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
        StudyRepairActions.ActiveRepair active = StudyRepairActions.activateSimilarWritingRepair(repair, now, store::saveSimilarWritingRepair);
        RecordsImportModels.SimilarKanjiWritingRepair activeRepair = active.repair();
        activeSimilarWritingRepair = activeRepair;
        RecordsStudyModels.StudyItem item = new BridgeScheduler().newTargetedStudyItem(activeRepair.repairKanji, now, studyLadderSettings());
        activeSession = new RecordsSchedulerModels.StudySession(
                item.withToken(active.token()),
                null,
                active.token(),
                TASK_REPAIR_WRITING,
                true,
                StudyTextCopy.similarRepairPrompt(activeRepair)
        );
        activeStudyPlan = plan;
        registerStudyTaskShown(active.progressKey());
        startActiveStudyTask(active.studyTaskKey(), activeRepair.repairKanji, TASK_REPAIR_WRITING, now);
        renderWritingSession(activeSession);
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
        return flashcardUi().typingAnswerField();
    }

    Typeface fontResource(int fontRes, Typeface fallback) {
        return flashcardUi().fontResource(fontRes, fallback);
    }

    View flashcardAnswerPanel(RecordsSchedulerModels.StudySession session) {
        return flashcardUi().flashcardAnswerPanel(session);
    }

    LinearLayout studyAnswerDetailsRow(RecordsSchedulerModels.StudySession session, int glyphSize) {
        return flashcardUi().studyAnswerDetailsRow(session, glyphSize);
    }

    void addStudyCueLines(LinearLayout details, RecordsSchedulerModels.StudySession session) {
        flashcardUi().addStudyCueLines(details, session);
    }

    DictionaryLookup currentDictionaryLookup() {
        if (dictionaryLookup == null) {
            dictionaryLookup = DictionaryAssets.load(this);
        }
        return dictionaryLookup;
    }

    void buildFlashcardActionBar(boolean revealed) {
        flashcardUi().buildFlashcardActionBar(revealed);
    }

    void revealFlashcardAnswer() {
        flashcardUi().revealFlashcardAnswer();
    }

    void expandFlashcardForAnswer() {
        flashcardUi().expandFlashcardForAnswer();
    }

    boolean handleFlashcardGesture(MotionEvent event) {
        return flashcardUi().handleFlashcardGesture(event);
    }

    boolean handleFlashcardRelease(MotionEvent event) {
        return flashcardUi().handleFlashcardRelease(event);
    }

    boolean isTouchInsideView(View view, MotionEvent event) {
        return flashcardUi().isTouchInsideView(view, event);
    }

    void buildStudyActionBar() {
        writingUi().buildStudyActionBar();
    }

    void eraseWritingPad() {
        writingFlow().eraseWritingPad();
    }

    void startGuidedWritingRetry() {
        writingFlow().startGuidedWritingRetry();
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
        writingCheck().checkWriting();
    }

    void submitSimilarKanjiChoice(RecordsImportModels.SimilarKanjiChoiceCard card, String selectedKanji) {
        writingReview().submitSimilarKanjiChoice(card, selectedKanji);
    }

    boolean showNoInkWhenNeeded() {
        return writingFlow().showNoInkWhenNeeded();
    }

    void showModelUnavailable(String message) {
        writingFlow().showModelUnavailable(message);
    }

    void recognizeWriting(WritingRecognizer recognizer, CapturedWriting captured, WritingSample sample, StrokeGuide guide, String target, String token) {
        writingCheck().recognizeWriting(recognizer, captured, sample, guide, target, token);
    }

    void submitReview(String rating, boolean override) {
        writingReview().submitReview(rating, override);
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
        writingFlow().showWritingHint();
    }

    void showAnalysis(WritingAnalysis analysis) {
        writingFlow().showAnalysis(analysis);
    }

    void updateResultActions() {
        writingUi().updateResultActions();
    }

    WritingActionPresentation writingActionPresentation() {
        return writingUi().writingActionPresentation();
    }

    void updateUndoStrokeButton() {
        writingUi().updateUndoStrokeButton();
    }

    void startCleanerRetry() {
        writingFlow().startCleanerRetry();
    }

    void undoWritingStroke() {
        writingFlow().undoWritingStroke();
    }

    void replayWritingAnalysis() {
        writingFlow().replayWritingAnalysis();
    }

    void handleDrawingEdited() {
        writingFlow().handleDrawingEdited();
    }

    void clearWritingResult() {
        writingFlow().clearWritingResult();
    }

    void handleDrawingBlocked(StrokeGuideGuard.Decision decision) {
        writingFlow().handleDrawingBlocked(decision);
    }

    String diagnosisText(WritingAnalysis analysis) {
        return writingFlow().diagnosisText(analysis);
    }

    boolean canShowDiagnosis(WritingAnalysis analysis) {
        return writingFlow().canShowDiagnosis(analysis);
    }

    String diagnosisLine(StrokeDiagnosis.Entry entry) {
        return writingFlow().diagnosisLine(entry);
    }

    String strokeDiagnosisText(StrokeDiagnosis.Entry entry, String label) {
        return writingFlow().strokeDiagnosisText(entry, label);
    }

    void setStudyStatus(String value, int color) {
        writingUi().setStudyStatus(value, color);
    }

    void setResultStatus(String value, int color) {
        writingUi().setResultStatus(value, color);
    }

    boolean canRevealMoreHelp() {
        return writingUi().canRevealMoreHelp();
    }

    void refreshWritingModelStatus() {
        writingUi().refreshWritingModelStatus();
    }

    void setWritingModelStatusMessage(WritingRecognizer.ModelStatus status, Throwable error) {
        writingUi().setWritingModelStatusMessage(status, error);
    }

    void downloadWritingModel() {
        writingUi().downloadWritingModel();
    }
}
