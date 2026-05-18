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
import dev.bee.kanjianki.core.MeaningKanjiChoicePlanner;
import dev.bee.kanjianki.core.SchedulerTuner;
import dev.bee.kanjianki.core.SimilarKanjiChoicePlanner;
import dev.bee.kanjianki.core.StudyExampleSelector;
import dev.bee.kanjianki.core.StudyTaskCopy;
import dev.bee.kanjianki.core.StudyTextCopy;
import dev.bee.kanjianki.core.TextUtil;
import dev.bee.kanjianki.core.TypingAnswerMatcher;
import dev.bee.kanjianki.core.study.HintLevel;
import dev.bee.kanjianki.core.study.HintProgression;
import dev.bee.kanjianki.core.study.HintState;
import dev.bee.kanjianki.core.study.RecognitionCandidate;
import dev.bee.kanjianki.core.study.StrokeDiagnosis;
import dev.bee.kanjianki.core.study.StrokeDiagnosisFormatter;
import dev.bee.kanjianki.core.study.StrokeGuide;
import dev.bee.kanjianki.core.study.StrokeGuideGuard;
import dev.bee.kanjianki.core.study.WritingAnalysis;
import dev.bee.kanjianki.core.study.WritingAnalysisEngine;
import dev.bee.kanjianki.core.study.WritingFeedbackCopy;
import dev.bee.kanjianki.core.study.WritingSample;
import dev.bee.kanjianki.data.DictionaryAssets;
import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.data.StudyStatsStore;
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
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

abstract class MainActivityStudy extends MainActivityStats {
    private static final String LABEL_CHOOSE_KANJI = "Choose the kanji";

    private final MeaningKanjiChoicePlanner meaningKanjiChoicePlanner = new MeaningKanjiChoicePlanner();
    private final Random meaningChoiceRandom = new Random();

    View learningPanel(RecordsSchedulerModels.StudySession session) {
        LinearLayout box = softInsetPanel();
        box.addView(text("Reference", 19, STUDY_PLUM, true));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView glyph = text(session.item.kanji, 72, STUDY_PLUM, true);
        glyph.setGravity(Gravity.CENTER);
        row.addView(glyph, new LinearLayout.LayoutParams(dp(118), dp(108)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        if (session.row != null) {
            addStudyCueLines(details, session);
        } else {
            details.addView(text(session.prompt, 15, MUTED, false));
        }
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(row);
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
        initializeSessionProgressTarget(activeStudyPlan);
        includeDueSimilarWritingRepairs(now, ladder);
        RecordsImportModels.SimilarKanjiWritingRepair repair = nextDueSimilarWritingRepair(now, ladder);
        if (repair != null) {
            renderSimilarWritingRepair(repair, activeStudyPlan, now);
            return;
        }
        if (studyRunAtHardCap()) {
            renderStudyRunDone(activeStudyPlan);
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
        initializeSessionProgressTarget(seededPlan);
        includeDueSimilarWritingRepairs(now, ladder);
        repair = nextDueSimilarWritingRepair(now, ladder);
        if (repair != null) {
            renderSimilarWritingRepair(repair, seededPlan, now);
            return;
        }
        if (studyRunAtHardCap()) {
            renderStudyRunDone(seededPlan);
            return;
        }
        activeSession = nextActiveSession(rows, seeded, seededPlan, now);
        activeSimilarWritingRepair = null;
        if (activeSession == null) {
            renderNoStudySession(seededPlan);
            return;
        }
        store.saveStudyItem(activeSession.item);
        String taskKey = sessionTaskKey(activeSession);
        registerStudyTaskShown(taskKey);
        startActiveStudyTask(taskKey, activeSession.item.kanji, activeSession.taskType, now);
        renderSession(activeSession);
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

    RecordsSchedulerModels.StudySession nextActiveSession(List<RecordsImportModels.DashboardRow> rows, List<RecordsStudyModels.StudyItem> seeded, RecordsSchedulerModels.AdaptiveLoadPlan plan, long now) {
        Set<String> focus = continueAllKanjiSession || plan.allKanjiMode ? null : new HashSet<>(plan.focusKanji);
        return new BridgeScheduler().nextSession(seeded, rows, now, studyAheadMillis(), focus, settings(), studyLadderSettings());
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
        card.addView(text("Today's focus done", 32, STUDY_PLUM, true));
        card.addView(text("Kani finished today's adaptive focus. You can stop here, or keep going through all current problem kanji.", 17, STUDY_MUTED, false));
        LinearLayout summary = softInsetPanel();
        summary.addView(text("Today's focus: 0 items left / " + plan.target, 20, STUDY_PLUM, true));
        summary.addView(text(plan.status, 15, STUDY_MUTED, false));
        card.addView(summary);
        boolean canStudyMore = addStudyMoreNewCardsButton(card);
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
        content.addView(card);
    }

    void renderStudyRunDone(RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        prepareStudyContent(plan, false);
        LinearLayout card = softStudyCard();
        card.addView(modePill(LABEL_PRACTICE));
        card.addView(text("Today's focus done", 32, STUDY_PLUM, true));
        card.addView(text("Kani finished the Study now set. You can stop here, or explicitly continue through all current problem kanji.", 17, STUDY_MUTED, false));
        LinearLayout summary = softInsetPanel();
        summary.addView(text(countText(studySessionTracker.movedForwardCount(), "kanji moved forward this session", "kanji moved forward this session"), 20, STUDY_PLUM, true));
        summary.addView(text(countText(studySessionTracker.missedCount(), "missed and will come back", "missed and will come back"), 16, STUDY_MUTED, false));
        summary.addView(text(countText(studySessionTracker.completedCount(), "task completed", "tasks completed"), 16, STUDY_MUTED, false));
        if (plan != null && !plan.status.isEmpty()) {
            summary.addView(text(plan.status, 15, STUDY_MUTED, false));
        }
        card.addView(summary);
        boolean canStudyMore = addStudyMoreNewCardsButton(card);
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
        content.addView(card);
    }

    boolean addStudyMoreNewCardsButton(LinearLayout card) {
        int available = availableStudyMoreNewCards();
        if (available <= 0) {
            return false;
        }
        Button studyMore = pinkPrimaryButton("Study more new cards");
        studyMore.setOnClickListener(v -> showStudyMoreNewCardsDialog(available));
        card.addView(studyMore);
        return true;
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
        int defaultCount = Math.max(1, Math.min(5, availableAtOpen));
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
        int requested;
        try {
            requested = parseThresholdInput(countInput);
        } catch (NumberFormatException error) {
            Toast.makeText(this, "Use a whole number of new cards.", Toast.LENGTH_SHORT).show();
            return -1;
        }
        if (requested <= 0) {
            Toast.makeText(this, "Use at least 1 new card.", Toast.LENGTH_SHORT).show();
            return -1;
        }
        return requested;
    }

    boolean startStudyMoreNewCards(int requestedCount) {
        List<RecordsImportModels.DashboardRow> rows = store.activeDashboardRows();
        if (rows.isEmpty()) {
            Toast.makeText(this, "No new cards are available.", Toast.LENGTH_SHORT).show();
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
            Toast.makeText(this, "No new cards are available.", Toast.LENGTH_SHORT).show();
            return false;
        }
        List<RecordsStudyModels.StudyItem> seeded = store.annotateSimilarKanjiAvailability(result.items);
        store.replaceStudyItems(seeded);
        studyMoreNewCardKanji.clear();
        studyMoreNewCardKanji.addAll(result.admittedKanji);
        continueAllKanjiSession = false;
        resetStudyRunProgress();
        studySessionTracker.setTargetCount(result.admittedCount);
        if (result.admittedCount < requestedCount) {
            Toast.makeText(this, "Only " + countText(result.admittedCount, "new card was", "new cards were") + " available.", Toast.LENGTH_SHORT).show();
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
        List<RecordsStudyModels.StudyItem> seeded = studyQueue(rows, now, true);
        activeStudyPlan = adaptivePlan(rows, seeded, now);
        RecordsStudyModels.StudyItem item = studyItemForTargetedKanji(seeded, kanji, now);
        String token = StudyTokenFactory.studyItem(item.kanji, item.activeToken);
        item = item.withRung(studyLadderSettings().effectiveRung(item.rung, item.hasSimilarKanji));
        String taskType = rungTaskType(item);
        activeSession = new RecordsSchedulerModels.StudySession(
                item.withToken(token),
                row,
                token,
                taskType,
                item.rung == RecordsBase.LadderRung.WRITE_KANJI,
                row.primaryMeaning.isEmpty() ? row.reasonText : row.primaryMeaning
        );
        store.saveStudyItem(activeSession.item);
        String taskKey = sessionTaskKey(activeSession);
        registerStudyTaskShown(taskKey);
        startActiveStudyTask(taskKey, activeSession.item.kanji, activeSession.taskType, now);
        renderSession(activeSession);
    }

    RecordsStudyModels.StudyItem studyItemForTargetedKanji(List<RecordsStudyModels.StudyItem> seeded, String kanji, long now) {
        RecordsStudyModels.StudyItem item = findStudyItem(seeded, kanji);
        return item == null ? newTargetedStudyItem(kanji, now) : item;
    }

    RecordsStudyModels.StudyItem newTargetedStudyItem(String kanji, long now) {
        return new RecordsStudyModels.StudyItem(
                kanji,
                "new",
                now,
                0.4,
                5.0,
                0,
                0,
                0,
                0,
                null,
                now
        ).withRung(studyLadderSettings().startingRung(false));
    }

    String rungTaskType(RecordsStudyModels.StudyItem item) {
        return studyLadderSettings().effectiveRung(item.rung, item.hasSimilarKanji).wireName();
    }

    void renderSession(RecordsSchedulerModels.StudySession session) {
        if (session.writingRequired) {
            renderWritingSession(session);
        } else if (BridgeScheduler.TASK_SIMILAR_KANJI.equals(session.taskType)) {
            renderSimilarKanjiSession(session);
        } else if (BridgeScheduler.TASK_MEANING_KANJI.equals(session.taskType)) {
            renderMeaningKanjiSession(session);
        } else {
            renderFlashcardSession(session);
        }
    }

    void renderMeaningKanjiSession(RecordsSchedulerModels.StudySession session) {
        prepareStudyContent(activeStudyPlan, true);
        activeSimilarWritingRepair = null;
        activeAnalysis = null;
        checkingWriting = false;
        flashcardAnswerRevealed = false;
        flashcardTouchTracking = false;
        flashcardGestureArea = null;
        typingAnswerInput = null;
        drawingPad = null;
        hintsUsed = 0;
        setHintState(HintState.initial());
        if (studyActionBar != null) {
            studyActionBar.removeAllViews();
            studyActionBar.setVisibility(View.GONE);
        }

        RecordsImportModels.MeaningKanjiChoiceCard choiceCard = meaningKanjiChoiceCardForSession(session);
        if (choiceCard == null || choiceCard.choices.size() < 4) {
            renderFlashcardSession(session);
            return;
        }

        LinearLayout cardShell = softStudyCard();
        cardShell.addView(modePill("Recall"));
        cardShell.addView(text(LABEL_CHOOSE_KANJI, 30, STUDY_PLUM, true));
        cardShell.addView(text(labelForTask(session.taskType), 16, STUDY_PINK_DARK, true));
        cardShell.addView(text("Pick the kanji that matches the meaning.", 15, STUDY_MUTED, false));
        addStudyReasonLine(cardShell, session);

        LinearLayout box = softInsetPanel();
        box.addView(text("Which kanji means " + cleanLearnerText(choiceCard.primaryMeaning, session.prompt, 96) + "?", 22, STUDY_PLUM, true));
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
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        LinearLayout row = null;
        for (int i = 0; i < card.choices.size(); i++) {
            if (i % 2 == 0) {
                row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                grid.addView(row);
            }
            String glyph = card.choices.get(i);
            Button button = studySecondaryButton(glyph);
            button.setTextColor(STUDY_PLUM);
            button.setTextSize(34);
            button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            button.setBackground(panel(Color.rgb(255, 245, 250), STUDY_BORDER, dp(20)));
            button.setOnClickListener(v -> showMeaningKanjiChoiceResult(card, glyph, grid, answerPanel));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(82), 1);
            lp.setMargins(dp(4), dp(8), dp(4), 0);
            if (row != null) {
                row.addView(button, lp);
            }
        }
        return grid;
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
        String status = correct
                ? "Correct. " + card.targetKanji + " means " + cleanLearnerText(card.primaryMeaning, prompt, 72) + "."
                : "Answer: " + card.targetKanji + " · " + cleanLearnerText(card.primaryMeaning, prompt, 72);
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
        prepareStudyContent(activeStudyPlan, true);
        activeSimilarWritingRepair = null;
        activeAnalysis = null;
        checkingWriting = false;
        flashcardAnswerRevealed = false;
        flashcardGestureArea = null;
        typingAnswerInput = null;
        drawingPad = null;
        hintsUsed = 0;
        setHintState(HintState.initial());
        if (studyActionBar != null) {
            studyActionBar.removeAllViews();
            studyActionBar.setVisibility(View.GONE);
        }

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
        if (stored != null) {
            return stored;
        }
        List<String> choices = buildSimilarKanjiChoices(session.item.kanji);
        String meaning = session.row == null ? "" : rowMeaning(session.row);
        return new RecordsImportModels.SimilarKanjiChoiceCard(
                session.item.kanji,
                meaning,
                choices,
                SimilarKanjiChoicePlanner.choiceSignature(choices)
        );
    }

    List<String> buildSimilarKanjiChoices(String targetKanji) {
        List<RecordsImportModels.SimilarKanjiPair> pairs = store.similarPairsForKanji(targetKanji);
        Set<String> choices = new LinkedHashSet<>();
        choices.add(targetKanji);
        for (RecordsImportModels.SimilarKanjiPair pair : pairs) {
            String other = pair.kanjiA.equals(targetKanji) ? pair.kanjiB : pair.kanjiA;
            choices.add(other);
            if (choices.size() >= 4) {
                break;
            }
        }
        return new ArrayList<>(choices);
    }

    View similarKanjiGrid(List<String> choices, RecordsImportModels.SimilarKanjiChoiceCard card) {
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
            Button button = studySecondaryButton(glyph);
            button.setTextColor(STUDY_PLUM);
            button.setTextSize(34);
            button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
            button.setBackground(panel(Color.rgb(255, 245, 250), STUDY_BORDER, dp(20)));
            button.setOnClickListener(v -> submitSimilarKanjiChoice(card, glyph));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(82), 1);
            lp.setMargins(dp(4), dp(8), dp(4), 0);
            if (row != null) {
                row.addView(button, lp);
            }
        }
        if (choices.size() % 2 == 1 && grid.getChildCount() > 0) {
            LinearLayout lastRow = (LinearLayout) grid.getChildAt(grid.getChildCount() - 1);
            SpaceView spacer = new SpaceView(this);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(82), 1);
            lp.setMargins(dp(4), dp(8), dp(4), 0);
            lastRow.addView(spacer, lp);
        }
        return grid;
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

        if (studyActionBar != null) {
            studyActionBar.removeAllViews();
            studyActionBar.setVisibility(View.GONE);
        }

        LinearLayout card = recognitionHeroCard(session);
        flashcardCard = card;
        flashcardGestureArea = card;

        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(-1, 0, 1);
        cardLp.setMargins(0, 0, 0, dp(14));
        content.addView(card, cardLp);
        buildFlashcardActionBar(false);
    }

    LinearLayout recognitionHeroCard(RecordsSchedulerModels.StudySession session) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setBackground(panel(Color.WHITE, Color.TRANSPARENT, dp(32)));
        card.setElevation(dp(8));
        card.setClickable(true);
        card.setFocusable(true);

        card.addView(recognitionPill(studyModeLabel(session)));

        TextView title = text(flashcardTitle(session), 21, STUDY_HERO_PLUM, true);
        title.setGravity(Gravity.CENTER);
        title.setIncludeFontPadding(false);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(-1, -2);
        titleLp.setMargins(0, dp(14), 0, 0);
        card.addView(title, titleLp);

        TextView question = text(heroQuestion(session), 27, STUDY_HERO_PLUM, true);
        question.setGravity(Gravity.CENTER);
        question.setIncludeFontPadding(false);
        LinearLayout.LayoutParams questionLp = new LinearLayout.LayoutParams(-1, -2);
        questionLp.setMargins(0, dp(8), 0, 0);
        card.addView(question, questionLp);

        TextView hiddenHint = text("Answer hidden until reveal", 14, STUDY_HERO_MUTED, false);
        hiddenHint.setGravity(Gravity.CENTER);
        hiddenHint.setIncludeFontPadding(false);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(-1, -2);
        hintLp.setMargins(0, dp(6), 0, 0);
        card.addView(hiddenHint, hintLp);
        addStudyReasonLine(card, session);

        flashcardHeroPanel = heroKanjiPanel(session);
        card.addView(flashcardHeroPanel);

        if (isTypingMeaningTask(session)) {
            TextView label = text(LABEL_MEANING, 15, STUDY_HERO_MUTED, true);
            label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams labelLp = new LinearLayout.LayoutParams(-1, -2);
            labelLp.setMargins(0, dp(14), 0, dp(8));
            card.addView(label, labelLp);
            card.addView(typingAnswerField());
        }

        studyAnswerPanel = flashcardAnswerPanel(session);
        studyAnswerPanel.setVisibility(View.GONE);
        card.addView(studyAnswerPanel);

        return card;
    }

    String heroQuestion(RecordsSchedulerModels.StudySession session) {
        return StudyTextCopy.heroQuestion(session);
    }

    View recognitionPill(String label) {
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(18), 0, dp(18), 0);
        pill.setMinimumHeight(dp(44));
        pill.setBackground(panel(Color.rgb(253, 239, 246), Color.TRANSPARENT, dp(24)));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_eye_24);
        icon.setColorFilter(STUDY_HERO_PINK);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(22), dp(22));
        iconLp.setMargins(0, 0, dp(10), 0);
        pill.addView(icon, iconLp);

        TextView text = text(label, 18, STUDY_HERO_PINK, true);
        text.setIncludeFontPadding(false);
        pill.addView(text);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(44));
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        pill.setLayoutParams(lp);
        return pill;
    }

    View heroKanjiPanel(RecordsSchedulerModels.StudySession session) {
        FrameLayout panel = new FrameLayout(this);
        panel.setBackground(panel(STUDY_HERO_PANEL, STUDY_BORDER, dp(28)));
        panel.setPadding(dp(10), dp(10), dp(10), dp(10));

        TextView glyph = text(
                isWordReadingTask(session) ? wordPrompt(session) : session.item.kanji,
                isWordReadingTask(session) ? 44 : 116,
                STUDY_HERO_PLUM,
                true
        );
        if (isFontRecognitionTask(session)) {
            glyph.setTypeface(randomFontVariantTypeface(), Typeface.BOLD);
        } else {
            glyph.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        }
        glyph.setGravity(Gravity.CENTER);
        glyph.setIncludeFontPadding(false);
        panel.addView(glyph, new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(210));
        lp.setMargins(0, dp(16), 0, 0);
        panel.setLayoutParams(lp);
        return panel;
    }

    Typeface randomFontVariantTypeface() {
        return StudyFontVariants.random(this);
    }

    void renderWritingSession(RecordsSchedulerModels.StudySession session) {
        prepareStudyContent(activeStudyPlan, false);
        activeAnalysis = null;
        checkingWriting = false;
        flashcardGestureArea = null;
        flashcardAnswerRevealed = false;
        flashcardTouchTracking = false;
        typingAnswerInput = null;
        hintsUsed = 0;
        setHintState(initialHintState(session));

        LinearLayout card = softStudyCard();
        card.addView(modePill(LABEL_PRACTICE));
        card.addView(text("Draw this kanji", 30, STUDY_PLUM, true));
        card.addView(text(labelForTask(session.taskType), 16, STUDY_PINK_DARK, true));
        addStudyReasonLine(card, session);
        if (session.row != null) {
            if (isRecallTask(session)) {
                card.addView(text("Prompt: " + sessionClue(session), 17, STUDY_PLUM, true));
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
        studyStatus = text(guideLabel(currentHintState, guide), 16, STUDY_MUTED, false);
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

    void addStudyReasonLine(LinearLayout card, RecordsSchedulerModels.StudySession session) {
        String reason = studyReasonLine(session);
        if (!reason.isEmpty()) {
            card.addView(text(reason, 14, STUDY_MUTED, false));
        }
    }

    String studyReasonLine(RecordsSchedulerModels.StudySession session) {
        if (activeSimilarWritingRepair != null) {
            return "Why: similar-kanji miss · writing repair · practice-only";
        }
        if (session == null || session.row == null) {
            return "";
        }
        return focusReasonLine(session.row, session.item, System.currentTimeMillis());
    }

    void renderSimilarWritingRepair(RecordsImportModels.SimilarKanjiWritingRepair repair, RecordsSchedulerModels.AdaptiveLoadPlan plan, long now) {
        String token = StudyTokenFactory.studyItem("repair-" + repair.id, repair.activeToken);
        RecordsImportModels.SimilarKanjiWritingRepair activeRepair = repair.withToken(token, now);
        store.saveSimilarWritingRepair(activeRepair);
        activeSimilarWritingRepair = activeRepair;
        RecordsStudyModels.StudyItem item = newTargetedStudyItem(activeRepair.repairKanji, now);
        activeSession = new RecordsSchedulerModels.StudySession(
                item.withToken(token),
                null,
                token,
                TASK_REPAIR_WRITING,
                true,
                similarRepairPrompt(activeRepair)
        );
        activeStudyPlan = plan;
        String progressKey = similarRepairProgressKey(activeRepair);
        registerStudyTaskShown(progressKey);
        startActiveStudyTask(similarRepairStudyTaskKey(activeRepair), activeRepair.repairKanji, TASK_REPAIR_WRITING, now);
        renderWritingSession(activeSession);
    }

    String similarRepairPrompt(RecordsImportModels.SimilarKanjiWritingRepair repair) {
        return StudyTextCopy.similarRepairPrompt(repair);
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

    RecordsImportModels.SimilarKanjiWritingRepair nextDueSimilarWritingRepair(long nowMillis, RecordsBase.StudyLadderSettings ladder) {
        if (!ladder.isEnabled(RecordsBase.LadderRung.WRITE_KANJI)) {
            return null;
        }
        return store.nextDueSimilarWritingRepair(nowMillis);
    }

    void includeDueSimilarWritingRepairs(long nowMillis, RecordsBase.StudyLadderSettings ladder) {
        if (!ladder.isEnabled(RecordsBase.LadderRung.WRITE_KANJI)) {
            return;
        }
        for (RecordsImportModels.SimilarKanjiWritingRepair repair : store.dueSimilarWritingRepairs(nowMillis)) {
            studySessionTracker.includePendingTask(similarRepairProgressKey(repair));
        }
    }

    boolean studyRunAtHardCap() {
        return studySessionTracker.atHardCap(continueAllKanjiSession);
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

    String flashcardTitle(RecordsSchedulerModels.StudySession session) {
        return StudyTaskCopy.flashcardTitle(session);
    }

    String studyModeLabel(RecordsSchedulerModels.StudySession session) {
        return StudyTaskCopy.studyModeLabel(session);
    }

    View typingAnswerField() {
        typingAnswerInput = new EditText(this);
        typingAnswerInput.setSingleLine(true);
        typingAnswerInput.setTextSize(20);
        typingAnswerInput.setTextColor(STUDY_PLUM);
        typingAnswerInput.setHintTextColor(STUDY_MUTED);
        typingAnswerInput.setHint(LABEL_MEANING);
        typingAnswerInput.setPadding(dp(16), 0, dp(16), 0);
        typingAnswerInput.setBackground(panel(Color.WHITE, STUDY_BORDER, dp(18)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(58));
        lp.setMargins(0, dp(4), 0, dp(4));
        typingAnswerInput.setLayoutParams(lp);
        return typingAnswerInput;
    }

    Typeface fontResource(int fontRes, Typeface fallback) {
        try {
            return getResources().getFont(fontRes);
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    View flashcardAnswerPanel(RecordsSchedulerModels.StudySession session) {
        LinearLayout box = softInsetPanel();
        box.addView(text("Answer", 19, STUDY_PLUM, true));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView glyph = text(session.item.kanji, 76, STUDY_PLUM, true);
        glyph.setGravity(Gravity.CENTER);
        row.addView(glyph, new LinearLayout.LayoutParams(dp(118), dp(108)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        if (session.row != null) {
            addStudyCueLines(details, session);
        } else {
            details.addView(text(session.prompt, 15, MUTED, false));
        }
        row.addView(details, new LinearLayout.LayoutParams(0, -2, 1));
        box.addView(row);
        return box;
    }

    void addStudyCueLines(LinearLayout details, RecordsSchedulerModels.StudySession session) {
        List<String> lines = StudyCueTexts.answerLines(
                currentDictionaryLookup(),
                session,
                exampleForSession(session),
                isWordReadingTask(session)
        );
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int color = line.startsWith("Reading:") ? STUDY_PINK_DARK : STUDY_PLUM;
            details.addView(text(line, i == 0 ? 17 : 15, color, true));
        }
    }

    DictionaryLookup currentDictionaryLookup() {
        if (dictionaryLookup == null) {
            dictionaryLookup = DictionaryAssets.load(this);
        }
        return dictionaryLookup;
    }

    void buildFlashcardActionBar(boolean revealed) {
        if (studyActionBar == null) {
            return;
        }
        styleStudyActionBarShell();
        studyActionBar.removeAllViews();
        studyActionBar.setVisibility(View.VISIBLE);

        resultStatus = text("", 15, STUDY_MUTED, false);
        resultStatus.setVisibility(View.GONE);
        studyActionBar.addView(resultStatus);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        if (!revealed) {
            Button reveal = pinkPrimaryButton("Reveal");
            reveal.setOnClickListener(v -> revealFlashcardAnswer());
            actions.addView(reveal, new LinearLayout.LayoutParams(0, dp(62), 1));
        } else {
            Button fail = studyFailButton("Fail");
            fail.setOnClickListener(v -> submitReview(RATING_AGAIN, false));
            LinearLayout.LayoutParams failParams = new LinearLayout.LayoutParams(0, dp(62), 1);
            failParams.setMargins(0, 0, dp(6), 0);
            actions.addView(fail, failParams);

            Button pass = pinkPrimaryButton(LABEL_PASS);
            pass.setOnClickListener(v -> submitReview(RATING_GOOD, false));
            LinearLayout.LayoutParams passParams = new LinearLayout.LayoutParams(0, dp(62), 1);
            passParams.setMargins(dp(6), 0, 0, 0);
            actions.addView(pass, passParams);
        }
        studyActionBar.addView(actions);
    }

    void revealFlashcardAnswer() {
        if (flashcardAnswerRevealed) {
            return;
        }
        if (isTypingMeaningTask(activeSession)
                && TypingAnswerMatcher.matches(
                currentDictionaryLookup(),
                activeSession.item.kanji,
                typingAnswerInput == null ? "" : typingAnswerInput.getText().toString(),
                collectionMeaningForSession(activeSession))) {
            Toast.makeText(this, "Typing answer accepted.", Toast.LENGTH_SHORT).show();
            submitReview(RATING_GOOD, false);
            return;
        }
        flashcardAnswerRevealed = true;
        if (flashcardHeroPanel != null) {
            flashcardHeroPanel.setVisibility(View.GONE);
        }
        expandFlashcardForAnswer();
        if (studyAnswerPanel != null) {
            studyAnswerPanel.setVisibility(View.VISIBLE);
        }
        buildFlashcardActionBar(true);
    }

    String collectionMeaningForSession(RecordsSchedulerModels.StudySession session) {
        return StudyTextCopy.collectionMeaningForSession(session);
    }

    void expandFlashcardForAnswer() {
        if (flashcardCard == null) {
            return;
        }
        int currentFullHeight = flashcardCard.getHeight();
        if (currentFullHeight > 0) {
            flashcardCard.setMinimumHeight(currentFullHeight);
        }
        ViewGroup.LayoutParams params = flashcardCard.getLayoutParams();
        if (params instanceof LinearLayout.LayoutParams linearParams) {
            linearParams.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            linearParams.weight = 0f;
            flashcardCard.setLayoutParams(linearParams);
            flashcardCard.requestLayout();
        }
    }

    boolean handleFlashcardGesture(MotionEvent event) {
        if (activeSession == null || activeSession.writingRequired || flashcardGestureArea == null) {
            flashcardTouchTracking = false;
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (isTypingMeaningTask(activeSession)
                        && typingAnswerInput != null
                        && isTouchInsideView(typingAnswerInput, event)) {
                    flashcardTouchTracking = false;
                    return false;
                }
                flashcardTouchTracking = isTouchInsideView(flashcardGestureArea, event);
                if (flashcardTouchTracking) {
                    flashcardTouchStartX = event.getRawX();
                    flashcardTouchStartY = event.getRawY();
                }
                return false;
            case MotionEvent.ACTION_UP:
                if (!flashcardTouchTracking) {
                    return false;
                }
                flashcardTouchTracking = false;
                if (!isTouchInsideView(flashcardGestureArea, event)) {
                    return false;
                }
                return handleFlashcardRelease(event);
            case MotionEvent.ACTION_CANCEL:
                flashcardTouchTracking = false;
                return false;
            default:
                return false;
        }
    }

    boolean handleFlashcardRelease(MotionEvent event) {
        float dx = event.getRawX() - flashcardTouchStartX;
        float dy = event.getRawY() - flashcardTouchStartY;
        float absX = Math.abs(dx);
        float absY = Math.abs(dy);
        int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        if (absX <= touchSlop && absY <= touchSlop) {
            if (!flashcardAnswerRevealed) {
                revealFlashcardAnswer();
                return true;
            }
            return false;
        }
        int swipeThreshold = Math.max(dp(72), touchSlop * 6);
        if (absX >= swipeThreshold && absX > absY * 1.25f) {
            if (!flashcardAnswerRevealed) {
                return false;
            }
            submitReview(dx > 0 ? RATING_GOOD : RATING_AGAIN, false);
            return true;
        }
        return false;
    }

    boolean isTouchInsideView(View view, MotionEvent event) {
        if (view == null || !view.isShown()) {
            return false;
        }
        Rect bounds = new Rect();
        if (!view.getGlobalVisibleRect(bounds)) {
            return false;
        }
        return bounds.contains((int) event.getRawX(), (int) event.getRawY());
    }

    void buildStudyActionBar() {
        if (studyActionBar == null) {
            return;
        }
        styleStudyActionBarShell();
        studyActionBar.removeAllViews();
        studyActionBar.setVisibility(View.VISIBLE);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button clear = studySecondaryButton("Erase");
        clear.setOnClickListener(v -> {
            drawingPad.clear();
            activeAnalysis = null;
            setStudyStatus(guideLabel(currentHintState, strokeGuide(activeSession.item.kanji)), MUTED);
            updateResultActions();
        });
        actions.addView(clear, new LinearLayout.LayoutParams(0, dp(58), 1));
        undoStrokeButton = studySecondaryButton("Undo");
        undoStrokeButton.setOnClickListener(v -> undoWritingStroke());
        actions.addView(undoStrokeButton, new LinearLayout.LayoutParams(0, dp(58), 1));
        hintButton = studySecondaryButton("Hint");
        hintButton.setOnClickListener(v -> showWritingHint());
        actions.addView(hintButton, new LinearLayout.LayoutParams(0, dp(58), 1));
        studyActionBar.addView(actions);

        LinearLayout primaryActions = new LinearLayout(this);
        primaryActions.setOrientation(LinearLayout.HORIZONTAL);
        checkWritingButton = pinkPrimaryButton("Check");
        checkWritingButton.setOnClickListener(v -> checkWriting());
        primaryActions.addView(checkWritingButton, new LinearLayout.LayoutParams(0, dp(62), 1));

        downloadModelButton = studySecondaryButton("Download checker");
        downloadModelButton.setOnClickListener(v -> downloadWritingModel());
        primaryActions.addView(downloadModelButton, new LinearLayout.LayoutParams(0, dp(62), 1));

        nextAfterPassButton = pinkPrimaryButton(LABEL_PASS);
        nextAfterPassButton.setOnClickListener(v -> submitReview(writingSubmitRating(activeAnalysis), false));
        primaryActions.addView(nextAfterPassButton, new LinearLayout.LayoutParams(0, dp(62), 1));
        studyActionBar.addView(primaryActions);

        LinearLayout fallbackActions = new LinearLayout(this);
        fallbackActions.setOrientation(LinearLayout.HORIZONTAL);
        replayButton = studySecondaryButton("Replay");
        replayButton.setOnClickListener(v -> replayWritingAnalysis());
        fallbackActions.addView(replayButton, new LinearLayout.LayoutParams(0, dp(56), 1));

        manualOverrideButton = studySecondaryButton("Mark right anyway");
        manualOverrideButton.setOnClickListener(v -> submitReview(RATING_GOOD, true));
        fallbackActions.addView(manualOverrideButton, new LinearLayout.LayoutParams(0, dp(56), 1));

        practiceWithGuideButton = studySecondaryButton("Try again with full guide");
        practiceWithGuideButton.setOnClickListener(v -> {
            setHintState(HintState.initial());
            hintsUsed++;
            activeAnalysis = null;
            drawingPad.clear();
            drawingPad.setGuide(strokeGuide(activeSession.item.kanji), currentHintState, false);
            setStudyStatus(guideLabel(currentHintState, strokeGuide(activeSession.item.kanji)) + "\nFresh guided try. Draw it again, then check.", MUTED);
            updateResultActions();
        });
        fallbackActions.addView(practiceWithGuideButton, new LinearLayout.LayoutParams(0, dp(56), 1));
        studyActionBar.addView(fallbackActions);
    }

    int studyPadHeight() {
        float density = getResources().getDisplayMetrics().density;
        int screenDp = Math.round(getResources().getDisplayMetrics().heightPixels / density);
        return studyPadHeightForScreenDp(screenDp);
    }

    int studyPadHeightForScreenDp(int screenDp) {
        if (screenDp < 700) {
            return dp(300);
        }
        if (screenDp < 820) {
            return dp(340);
        }
        return dp(390);
    }

    void checkWriting() {
        if (activeSession == null) {
            return;
        }
        if (showNoInkWhenNeeded()) {
            return;
        }
        if (checkingWriting) {
            return;
        }
        RecordsSchedulerModels.StudySession session = activeSession;
        String token = session.token;
        String target = session.item.kanji;
        CapturedWriting captured;
        WritingSample sample;
        try {
            captured = drawingPad.capturedWriting();
            sample = drawingPad.writingSample();
        } catch (IllegalArgumentException error) {
            activeAnalysis = WritingAnalysisEngine.noInk(currentHintState.level(), hintsUsed);
            showAnalysis(activeAnalysis);
            return;
        }
        StrokeGuide guide = strokeGuide(target);
        checkingWriting = true;
        checkWritingButton.setEnabled(false);
        updateResultActions();
        setStudyStatus("Checking handwriting...", MUTED);
        WritingRecognizer recognizer = currentWritingRecognizer();
        if (recognizer == null) {
            showModelUnavailable("The handwriting checker is unavailable on this device.");
            return;
        }
        recognizer.modelStatus().whenComplete((status, statusError) -> {
            if (statusError != null || status == null || !status.downloaded) {
                main.post(() -> {
                    if (!isActiveToken(token)) {
                        return;
                    }
                    writingModelDownloaded = false;
                    writingModelStatusKnown = true;
                    showModelUnavailable("Download the handwriting checker before automatic checks.");
                });
                return;
            }
            recognizeWriting(recognizer, captured, sample, guide, target, token);
        });
    }

    void submitSimilarKanjiChoice(RecordsImportModels.SimilarKanjiChoiceCard card, String selectedKanji) {
        long now = System.currentTimeMillis();
        RecordsImportModels.SimilarKanjiChoiceResult result = store.submitSimilarChoice(
                card,
                selectedKanji,
                now,
                studyLadderSettings().isEnabled(RecordsBase.LadderRung.WRITE_KANJI)
        );
        submitReview(result.correct ? RATING_GOOD : RATING_AGAIN, false);
    }

    boolean showNoInkWhenNeeded() {
        if (drawingPad != null && drawingPad.hasInk()) {
            return false;
        }
        activeAnalysis = WritingAnalysisEngine.noInk(currentHintState.level(), hintsUsed);
        showAnalysis(activeAnalysis);
        return true;
    }

    void showModelUnavailable(String message) {
        activeAnalysis = WritingAnalysisEngine.modelUnavailable(message, currentHintState.level(), hintsUsed);
        checkingWriting = false;
        showAnalysis(activeAnalysis);
    }

    void recognizeWriting(WritingRecognizer recognizer, CapturedWriting captured, WritingSample sample, StrokeGuide guide, String target, String token) {
        recognizer.recognize(captured).whenComplete((result, error) -> main.post(() -> {
            if (!isActiveToken(token)) {
                return;
            }
            checkingWriting = false;
            if (error != null) {
                activeAnalysis = WritingAnalysisEngine.recognitionError(currentHintState.level(), hintsUsed);
            } else {
                activeAnalysis = WritingAnalysisEngine.analyze(target, sample, guide, candidates(result), currentHintState.level(), hintsUsed);
            }
            showAnalysis(activeAnalysis);
        }));
    }

    void submitReview(String rating, boolean override) {
        if (activeSession == null) {
            return;
        }
        if (activeSimilarWritingRepair != null) {
            submitSimilarWritingRepair(rating);
            return;
        }
        StudyReviewRequests.MappedReview mappedReview = StudyReviewRequests.from(
                activeSession,
                activeAnalysis,
                hintsUsed,
                rating,
                override
        );
        RecordsSchedulerModels.ReviewRequest request = mappedReview.request();
        submitNormalReview(request);
    }

    void submitSimilarWritingRepair(String rating) {
        RecordsImportModels.SimilarKanjiWritingRepair repair = activeSimilarWritingRepair;
        if (repair == null) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean passed = !RATING_AGAIN.equals(rating);
        completeActiveRepairStudyTask(similarRepairStudyTaskKey(repair), rating, now);
        boolean saved = store.finishSimilarWritingRepair(repair.id, repair.activeToken, passed, now);
        if (saved) {
            studySessionTracker.recordRepairOutcome(repair.repairKanji, passed);
        }
        if (saved && passed) {
            markStudyTaskCompleted(similarRepairProgressKey(repair));
        }
        Toast.makeText(
                this,
                passed ? "Repair saved." : "Saved. Try that repair again.",
                Toast.LENGTH_SHORT
        ).show();
        activeSimilarWritingRepair = null;
        renderStudy();
    }

    void completeActiveRepairStudyTask(String key, String outcome, long answeredAt) {
        studySessionTracker.completeActiveTask(store, key, outcome, answeredAt, false);
    }

    void submitNormalReview(RecordsSchedulerModels.ReviewRequest request) {
        BridgeScheduler scheduler = new BridgeScheduler();
        Set<String> consumed = new HashSet<>(store.consumedTokens());
        long now = System.currentTimeMillis();
        RecordsSchedulerModels.SchedulerParameters parameters = store.schedulerParameters();
        RecordsSchedulerModels.SchedulerParameters effectiveParameters = parameters.withTargetRetention(
                parameters.targetRetentionForRank(activeSession.row.jitenRank)
        );
        RecordsSchedulerModels.ReviewResult result = scheduler.applyReview(activeSession.item, request, consumed, now, effectiveParameters, settings(), studyLadderSettings());
        completeActiveStudyTask(sessionTaskKey(activeSession), result.appliedRating, now);
        StudyStatsStore.StudyStreak streak = null;
        if (!result.duplicate) {
            saveAppliedReview(request, result, now);
            streak = store.studyStreak(now);
            tuneSchedulerIfNeeded(parameters, now);
        }
        Toast.makeText(this, reviewToast(result, streak), Toast.LENGTH_SHORT).show();
        renderStudy();
    }

    void saveAppliedReview(RecordsSchedulerModels.ReviewRequest request, RecordsSchedulerModels.ReviewResult result, long now) {
        store.saveStudyItem(result.item);
        store.saveReview(request, result.appliedRating, now, activeSession.item, result.item);
        studySessionTracker.recordReviewOutcome(request.kanji, result.appliedRating, activeSession.item, result.item);
        if (!RATING_AGAIN.equals(result.appliedRating)) {
            markStudyRunPassed(request.kanji);
        }
    }

    void tuneSchedulerIfNeeded(RecordsSchedulerModels.SchedulerParameters parameters, long now) {
        RecordsSchedulerModels.SchedulerParameters tuned = new SchedulerTuner().maybeTune(parameters, store.reviewStatsSince(now - SchedulerTuner.MONTH_MILLIS), now);
        if (tuned.lastAdjustedAtMillis != parameters.lastAdjustedAtMillis || tuned.lastAdjustmentReviewCount != parameters.lastAdjustmentReviewCount) {
            store.saveSchedulerParameters(tuned);
        }
    }

    HintState initialHintState(RecordsSchedulerModels.StudySession session) {
        int stored = Math.max(0, Math.min(3, session.item.writingLevel));
        if (TASK_TARGETED_WRITING.equals(session.taskType)
                || session.item.totalReviews == 0
                || session.item.learningStep == 0) {
            return HintState.fromWritingLevel(Math.min(stored, 1));
        }
        return HintState.fromWritingLevel(stored);
    }

    void setHintState(HintState state) {
        currentHintState = state == null ? HintState.initial() : state;
        currentPracticeLevel = currentHintState.level().writingLevel();
    }

    String guideStatusPrefix(StrokeGuide guide) {
        return guideLabel(currentHintState, guide);
    }

    void showWritingHint() {
        if (drawingPad == null || activeSession == null) {
            return;
        }
        StrokeGuide guide = strokeGuide(activeSession.item.kanji);
        setHintState(hintProgression.revealNext(currentHintState, guide));
        hintsUsed++;
        activeAnalysis = null;
        drawingPad.setGuide(guide, currentHintState, false);
        setStudyStatus(guideLabel(currentHintState, guide) + "\nHint used. One current stroke hinted; your ink stayed on the canvas.", MUTED);
        updateResultActions();
    }

    void showAnalysis(WritingAnalysis analysis) {
        StrokeGuide guide = activeSession == null ? null : strokeGuide(activeSession.item.kanji);
        if (shouldIncreaseSupportAfterAnalysis(analysis)) {
            setHintState(hintProgression.afterWriting(currentHintState, analysis));
        }
        if (drawingPad != null && activeSession != null) {
            drawingPad.setGuide(guide, currentHintState, true);
            if (canReplayAnalysis(analysis, guide)) {
                drawingPad.captureReplaySnapshot();
                drawingPad.startReplay();
            } else {
                drawingPad.clearReplaySnapshot();
            }
        }
        int color = analysis.writingPassed ? TEAL : CORAL;
        String candidates = candidateText(analysis.candidates);
        String message = analysis.message + attemptProgressText(analysis) + targetRevealText(analysis) + (candidates.isEmpty() ? "" : "\nIt saw: " + candidates);
        String diagnosis = diagnosisText(analysis);
        if (!diagnosis.isEmpty()) {
            message += "\n" + diagnosis;
        }
        setStudyStatus(guideLabel(currentHintState, guide), MUTED);
        setResultStatus(message, color);
        updateResultActions();
    }

    void updateResultActions() {
        boolean hasResult = activeAnalysis != null;
        boolean passed = hasResult && activeAnalysis.writingPassed;
        boolean messyPass = hasResult && activeAnalysis.status == WritingAnalysis.Status.CLOSE;
        boolean submittable = activeAnalysis != null && canSubmitAnalysis(activeAnalysis);
        StrokeGuide guide = activeSession == null ? null : strokeGuide(activeSession.item.kanji);
        updateUndoStrokeButton();
        updateCheckWritingButton(passed, messyPass);
        updateDownloadModelButton();
        updateNextAfterPassButton(submittable);
        updateFallbackActionButtons(hasResult, passed, guide);
        updateHintAndAnswerVisibility(passed);
        if (resultStatus != null && !hasResult) {
            resultStatus.setVisibility(View.GONE);
        }
    }

    void updateCheckWritingButton(boolean passed, boolean messyPass) {
        if (checkWritingButton != null) {
            checkWritingButton.setVisibility(!passed || messyPass ? View.VISIBLE : View.GONE);
            checkWritingButton.setEnabled(!checkingWriting);
            checkWritingButton.setText(checkWritingButtonText(messyPass));
            checkWritingButton.setOnClickListener(messyPass ? v -> startCleanerRetry() : v -> checkWriting());
        }
    }

    String checkWritingButtonText(boolean messyPass) {
        return WritingFeedbackCopy.checkWritingButtonText(checkingWriting, messyPass);
    }

    void updateUndoStrokeButton() {
        if (undoStrokeButton != null) {
            undoStrokeButton.setVisibility(View.VISIBLE);
            undoStrokeButton.setEnabled(!checkingWriting && drawingPad != null && drawingPad.canUndoStroke());
        }
    }

    void updateDownloadModelButton() {
        if (downloadModelButton != null) {
            downloadModelButton.setVisibility(writingModelStatusKnown && writingModelDownloaded ? View.GONE : View.VISIBLE);
        }
    }

    void updateNextAfterPassButton(boolean submittable) {
        if (nextAfterPassButton != null) {
            nextAfterPassButton.setVisibility(submittable ? View.VISIBLE : View.GONE);
            if (submittable) {
                nextAfterPassButton.setText(writingSubmitLabel(activeAnalysis));
                nextAfterPassButton.setOnClickListener(v -> submitReview(writingSubmitRating(activeAnalysis), false));
            }
        }
    }

    String writingSubmitLabel(WritingAnalysis analysis) {
        return WritingFeedbackCopy.submitLabel(analysis);
    }

    String writingSubmitRating(WritingAnalysis analysis) {
        return WritingFeedbackCopy.submitRating(analysis);
    }

    void updateFallbackActionButtons(boolean hasResult, boolean passed, StrokeGuide guide) {
        if (manualOverrideButton != null) {
            manualOverrideButton.setVisibility(hasResult && canManualOverride(activeAnalysis) ? View.VISIBLE : View.GONE);
        }
        if (practiceWithGuideButton != null) {
            practiceWithGuideButton.setVisibility(hasResult && !passed && canPracticeAfterAnalysis(activeAnalysis) ? View.VISIBLE : View.GONE);
        }
        if (replayButton != null) {
            replayButton.setVisibility(hasResult && drawingPad != null && drawingPad.hasReplaySnapshot() && canReplayAnalysis(activeAnalysis, guide) ? View.VISIBLE : View.GONE);
        }
    }

    void updateHintAndAnswerVisibility(boolean passed) {
        if (hintButton != null) {
            hintButton.setVisibility(!passed && canRevealMoreHelp() ? View.VISIBLE : View.GONE);
            hintButton.setText(currentPracticeLevel == 3 ? "Hint" : "More help");
        }
        if (studyAnswerPanel != null) {
            studyAnswerPanel.setVisibility(shouldShowLearningPanel(activeAnalysis) ? View.VISIBLE : View.GONE);
        }
    }

    boolean shouldShowLearningPanel(WritingAnalysis analysis) {
        if (activeSession != null && isRecallTask(activeSession)) {
            return analysis != null && analysis.status != WritingAnalysis.Status.NO_INK && !analysis.writingPassed;
        }
        if (analysis == null || analysis.status == WritingAnalysis.Status.NO_INK) {
            return activeSession != null && isTeachingTask(activeSession) && currentPracticeLevel < 3;
        }
        return true;
    }

    boolean isTeachingTask(RecordsSchedulerModels.StudySession session) {
        if (session == null) {
            return false;
        }
        return "context_writing".equals(session.taskType)
                || "guided_writing".equals(session.taskType)
                || (TASK_TARGETED_WRITING.equals(session.taskType) && session.item.learningStep < 2);
    }

    boolean canRevealMoreHelp() {
        if (activeSession == null || currentHintState == null || currentHintState.level() == HintLevel.TRACE) {
            return false;
        }
        StrokeGuide guide = strokeGuide(activeSession.item.kanji);
        return guide == null || guide.isEmpty() || currentHintState.level() == HintLevel.OUTLINE || currentHintState.revealedStrokeCount() < guide.strokeCount();
    }

    boolean shouldIncreaseSupportAfterAnalysis(WritingAnalysis analysis) {
        return WritingFeedbackCopy.shouldIncreaseSupportAfterAnalysis(analysis);
    }

    void startCleanerRetry() {
        if (drawingPad == null || activeSession == null) {
            return;
        }
        activeAnalysis = null;
        StrokeGuide guide = strokeGuide(activeSession.item.kanji);
        drawingPad.clear();
        drawingPad.setGuide(guide, currentHintState, false);
        setStudyStatus(guideLabel(currentHintState, guide) + "\nTry cleaner. Keep the same help level and draw it carefully once more.", MUTED);
        if (resultStatus != null) {
            resultStatus.setVisibility(View.GONE);
        }
        updateResultActions();
    }

    void undoWritingStroke() {
        if (drawingPad == null || activeSession == null || !drawingPad.undoLastStroke()) {
            updateUndoStrokeButton();
            return;
        }
        activeAnalysis = null;
        setStudyStatus(guideLabel(currentHintState, strokeGuide(activeSession.item.kanji)) + "\nUndid the last stroke.", MUTED);
        if (resultStatus != null) {
            resultStatus.setVisibility(View.GONE);
        }
        updateResultActions();
    }

    void replayWritingAnalysis() {
        if (drawingPad == null || activeSession == null) {
            return;
        }
        StrokeGuide guide = strokeGuide(activeSession.item.kanji);
        if (canReplayAnalysis(activeAnalysis, guide)) {
            drawingPad.setGuide(guide, currentHintState, true);
            drawingPad.startReplay();
        }
    }

    void handleDrawingEdited() {
        updateUndoStrokeButton();
        if (checkingWriting || activeAnalysis == null || activeSession == null || drawingPad == null) {
            return;
        }
        activeAnalysis = null;
        drawingPad.clearReplaySnapshot();
        setStudyStatus(guideLabel(currentHintState, strokeGuide(activeSession.item.kanji)) + "\nUpdated ink. Check again when ready.", MUTED);
        if (resultStatus != null) {
            resultStatus.setVisibility(View.GONE);
        }
        updateResultActions();
    }

    void handleDrawingBlocked(StrokeGuideGuard.Decision decision) {
        if (activeSession == null || drawingPad == null) {
            return;
        }
        String message = decision == null || decision.message.isEmpty()
                ? "Stay close to the guide."
                : decision.message;
        setStudyStatus(guideLabel(currentHintState, strokeGuide(activeSession.item.kanji)) + "\n" + message, MUTED);
        updateUndoStrokeButton();
    }

    boolean canReplayAnalysis(WritingAnalysis analysis, StrokeGuide guide) {
        if (analysis == null
                || drawingPad == null
                || !drawingPad.hasInk()
                || guide == null
                || guide.isEmpty()
                || analysis.strokeOrder == null
                || analysis.strokeOrder.missingGuide) {
            return false;
        }
        switch (analysis.status) {
            case NO_INK, MODEL_UNAVAILABLE, NO_STROKE_DATA, RECOGNITION_ERROR:
                return false;
            default:
                return true;
        }
    }

    String diagnosisText(WritingAnalysis analysis) {
        return StrokeDiagnosisFormatter.text(analysis);
    }

    boolean canShowDiagnosis(WritingAnalysis analysis) {
        return StrokeDiagnosisFormatter.canShow(analysis);
    }

    String diagnosisLine(StrokeDiagnosis.Entry entry) {
        return StrokeDiagnosisFormatter.line(entry);
    }

    String strokeDiagnosisText(StrokeDiagnosis.Entry entry, String label) {
        return StrokeDiagnosisFormatter.strokeLine(entry, label);
    }

    boolean isRecallTask(RecordsSchedulerModels.StudySession session) {
        if (session == null) {
            return false;
        }
        return "blind_writing".equals(session.taskType) || "sampled_handwriting".equals(session.taskType);
    }

    boolean isFontRecognitionTask(RecordsSchedulerModels.StudySession session) {
        return session != null && (TASK_FONT_MEANING.equals(session.taskType) || "font_recognition".equals(session.taskType));
    }

    boolean isTypingMeaningTask(RecordsSchedulerModels.StudySession session) {
        return session != null
                && (TASK_TYPING_MEANING.equals(session.taskType)
                || BridgeScheduler.TASK_TYPE_MEANING.equals(session.taskType));
    }

    boolean isMeaningKanjiTask(RecordsSchedulerModels.StudySession session) {
        return session != null && BridgeScheduler.TASK_MEANING_KANJI.equals(session.taskType);
    }

    boolean isWordReadingTask(RecordsSchedulerModels.StudySession session) {
        return session != null && TASK_WORD_READING.equals(session.taskType);
    }

    boolean canSubmitAnalysis(WritingAnalysis analysis) {
        return WritingFeedbackCopy.canSubmitAnalysis(analysis);
    }

    boolean canManualOverride(WritingAnalysis analysis) {
        return WritingFeedbackCopy.canManualOverride(analysis);
    }

    boolean canPracticeAfterAnalysis(WritingAnalysis analysis) {
        return WritingFeedbackCopy.canPracticeAfterAnalysis(analysis);
    }

    void setStudyStatus(String value, int color) {
        if (studyStatus != null) {
            studyStatus.setText(value);
            studyStatus.setTextColor(color);
        }
        if (resultStatus != null && activeAnalysis == null) {
            resultStatus.setVisibility(View.GONE);
        }
    }

    void setResultStatus(String value, int color) {
        if (resultStatus != null) {
            resultStatus.setText(value);
            resultStatus.setTextColor(color);
            resultStatus.setVisibility(View.VISIBLE);
        }
    }

    void refreshWritingModelStatus() {
        writingModelStatusKnown = false;
        writingModelDownloaded = false;
        updateResultActions();
        String token = activeSession == null ? null : activeSession.token;
        WritingRecognizer recognizer = currentWritingRecognizer();
        if (recognizer == null) {
            writingModelStatusKnown = true;
            setStudyStatus(guideStatusPrefix(strokeGuide(activeSession.item.kanji)) + "\nAutomatic handwriting checks are unavailable on this device.", CORAL);
            updateResultActions();
            return;
        }
        recognizer.modelStatus().whenComplete((status, error) -> main.post(() -> {
            if (token == null || !isActiveToken(token)) {
                return;
            }
            writingModelStatusKnown = true;
            writingModelDownloaded = error == null && status != null && status.downloaded;
            updateResultActions();
            if (activeAnalysis != null || checkingWriting) {
                return;
            }
            setWritingModelStatusMessage(status, error);
        }));
    }

    void setWritingModelStatusMessage(WritingRecognizer.ModelStatus status, Throwable error) {
        String prefix = guideStatusPrefix(strokeGuide(activeSession.item.kanji));
        if (error != null || status == null) {
            setStudyStatus(prefix + "\nUnable to read handwriting checker status.", CORAL);
            return;
        }
        if (!status.downloaded) {
            setStudyStatus(prefix + "\nDownload the handwriting checker before automatic checks.", CORAL);
            return;
        }
        setStudyStatus(prefix + "\nHandwriting checker ready.", MUTED);
    }

    void downloadWritingModel() {
        String token = activeSession == null ? null : activeSession.token;
        WritingRecognizer recognizer = currentWritingRecognizer();
        if (recognizer == null) {
            setStudyStatus("The handwriting checker is unavailable on this device.", CORAL);
            return;
        }
        setStudyStatus("Downloading handwriting checker...", MUTED);
        recognizer.downloadModel().whenComplete((status, error) -> main.post(() -> {
            if (token != null && !isActiveToken(token)) {
                return;
            }
            if (error != null) {
                writingModelStatusKnown = true;
                writingModelDownloaded = false;
                setStudyStatus("Handwriting checker download failed: " + error.getMessage(), CORAL);
            } else {
                writingModelStatusKnown = true;
                writingModelDownloaded = true;
                setStudyStatus("Handwriting checker ready.", TEAL);
            }
            updateResultActions();
        }));
    }
}
