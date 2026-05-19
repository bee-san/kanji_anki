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
import dev.bee.kanjianki.core.MeaningKanjiChoicePlanner;
import dev.bee.kanjianki.core.SchedulerTuner;
import dev.bee.kanjianki.core.SimilarKanjiChoicePlanner;
import dev.bee.kanjianki.core.StudyExampleSelector;
import dev.bee.kanjianki.core.StudyLayoutPolicy;
import dev.bee.kanjianki.core.StudyMoreNewCardsPolicy;
import dev.bee.kanjianki.core.StudyReviewRequestPolicy;
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
import dev.bee.kanjianki.core.study.StrokeDiagnosisFormatter;
import dev.bee.kanjianki.core.study.StrokeGuide;
import dev.bee.kanjianki.core.study.StrokeGuideGuard;
import dev.bee.kanjianki.core.study.WritingActionPresentation;
import dev.bee.kanjianki.core.study.WritingAnalysis;
import dev.bee.kanjianki.core.study.WritingAnalysisEngine;
import dev.bee.kanjianki.core.study.WritingFeedbackCopy;
import dev.bee.kanjianki.core.study.WritingHintPolicy;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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
        activeSession = nextActiveSession(rows, seeded, seededPlan, now);
        activeSimilarWritingRepair = null;
        if (activeSession == null) {
            renderNoStudySession(seededPlan);
            return;
        }
        activateStudySession(activeSession, now);
        renderSession(activeSession);
    }

    boolean renderPendingRepairOrDone(
            RecordsSchedulerModels.AdaptiveLoadPlan plan,
            long now,
            RecordsBase.StudyLadderSettings ladder
    ) {
        initializeSessionProgressTarget(plan);
        includeDueSimilarWritingRepairs(now, ladder);
        RecordsImportModels.SimilarKanjiWritingRepair repair = nextDueSimilarWritingRepair(now, ladder);
        if (repair != null) {
            renderSimilarWritingRepair(repair, plan, now);
            return true;
        }
        if (studyRunAtHardCap()) {
            renderStudyRunDone(plan);
            return true;
        }
        return false;
    }

    void renderEmptyStudyQueue() {
        prepareStudyContent(activeStudyPlan, false);
        content.addView(studyPracticeMessageCard(
                "Nothing to study yet",
                "Sync from AnkiDroid first. Study opens once the app finds problem kanji to repair."
        ));
    }

    RecordsSchedulerModels.StudySession nextActiveSession(List<RecordsImportModels.DashboardRow> rows, List<RecordsStudyModels.StudyItem> seeded, RecordsSchedulerModels.AdaptiveLoadPlan plan, long now) {
        Set<String> focus = StudySessionFocusPolicy.allowedKanji(plan, continueAllKanjiSession);
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
        boolean canStudyMore = addStudyMoreNewCardsButton(card);
        Button keepGoing = canStudyMore ? studySecondaryButton(LABEL_CONTINUE_ALL_KANJI) : pinkPrimaryButton(LABEL_CONTINUE_ALL_KANJI);
        keepGoing.setOnClickListener(v -> continueAllKanjiFromDoneScreen());
        card.addView(keepGoing);
        Button back = studySecondaryButton(LABEL_BACK_HOME);
        back.setOnClickListener(v -> returnHomeFromDoneScreen());
        card.addView(back);
    }

    void continueAllKanjiFromDoneScreen() {
        studyMoreNewCardKanji.clear();
        continueAllKanjiSession = true;
        renderStudy();
    }

    void returnHomeFromDoneScreen() {
        clearStudyModeOverrides();
        renderHome();
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
        BridgeScheduler.ExtraNewCardsResult result = seedExtraNewCards(rows, now, Integer.MAX_VALUE);
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
        BridgeScheduler.ExtraNewCardsResult result = seedExtraNewCards(rows, now, requestedCount);
        if (!result.admittedAny()) {
            Toast.makeText(this, StudyMoreNewCardsPolicy.NO_NEW_CARDS_AVAILABLE_MESSAGE, Toast.LENGTH_SHORT).show();
            return false;
        }
        StudyMoreNewCardActions.AdmissionResult admission = StudyMoreNewCardActions.applyAdmission(
                result,
                studyMoreNewCardWriter(),
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

    BridgeScheduler.ExtraNewCardsResult seedExtraNewCards(List<RecordsImportModels.DashboardRow> rows, long now, int requestedCount) {
        return new BridgeScheduler().seedExtraNewCards(
                rows,
                store.studyItems(),
                settings(),
                now,
                startOfDay(now),
                requestedCount,
                studyLadderSettings()
        );
    }

    StudyMoreNewCardActions.StudyItemWriter studyMoreNewCardWriter() {
        return new StudyMoreNewCardActions.StudyItemWriter() {
            @Override
            public List<RecordsStudyModels.StudyItem> annotateSimilarKanjiAvailability(List<RecordsStudyModels.StudyItem> items) {
                return store.annotateSimilarKanjiAvailability(items);
            }

            @Override
            public void replaceStudyItems(List<RecordsStudyModels.StudyItem> items) {
                store.replaceStudyItems(items);
            }
        };
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
            renderTargetKanjiUnavailable();
            return;
        }
        List<RecordsStudyModels.StudyItem> seeded = studyQueue(rows, now, true);
        activeStudyPlan = adaptivePlan(rows, seeded, now);
        activeSession = new BridgeScheduler().targetedSession(
                seeded,
                row,
                now,
                studyLadderSettings()
        );
        activateStudySession(activeSession, now);
        renderSession(activeSession);
    }

    void renderTargetKanjiUnavailable() {
        prepareStudyContent(activeStudyPlan, false);
        content.addView(studyPracticeMessageCard(
                "Kanji not available",
                "This row may have changed after sync."
        ));
    }

    LinearLayout studyPracticeMessageCard(String title, String body) {
        LinearLayout card = softStudyCard();
        card.addView(modePill(LABEL_PRACTICE));
        card.addView(text("Study practice", 32, STUDY_PLUM, true));
        card.addView(text(title, 22, STUDY_PLUM, true));
        card.addView(text(body, 16, STUDY_MUTED, false));
        return card;
    }

    String activateStudySession(RecordsSchedulerModels.StudySession session, long now) {
        return StudySessionActions.activateStudySession(
                session,
                now,
                store::saveStudyItem,
                this::registerStudyTaskShown,
                this::startActiveStudyTask
        );
    }

    RecordsStudyModels.StudyItem studyItemForTargetedKanji(List<RecordsStudyModels.StudyItem> seeded, String kanji, long now) {
        return new BridgeScheduler().targetedStudyItem(seeded, kanji, now, studyLadderSettings());
    }

    RecordsStudyModels.StudyItem newTargetedStudyItem(String kanji, long now) {
        return new BridgeScheduler().newTargetedStudyItem(kanji, now, studyLadderSettings());
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
        cardShell.addView(text(labelForTask(session.taskType), 16, STUDY_PINK_DARK, true));
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
        String meaning = session.row == null ? "" : rowMeaning(session.row);
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
        resetFlashcardSession();

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
        resetWritingSession(session);

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
        String reason = studyReasonLine(session);
        if (!reason.isEmpty()) {
            card.addView(text(reason, 14, STUDY_MUTED, false));
        }
    }

    String studyReasonLine(RecordsSchedulerModels.StudySession session) {
        return StudyTextCopy.studyReasonLine(
                activeSimilarWritingRepair != null,
                session,
                settings().matureSupportThreshold,
                System.currentTimeMillis()
        );
    }

    void renderSimilarWritingRepair(RecordsImportModels.SimilarKanjiWritingRepair repair, RecordsSchedulerModels.AdaptiveLoadPlan plan, long now) {
        StudyRepairActions.ActiveRepair active = StudyRepairActions.activateSimilarWritingRepair(repair, now, store::saveSimilarWritingRepair);
        RecordsImportModels.SimilarKanjiWritingRepair activeRepair = active.repair();
        activeSimilarWritingRepair = activeRepair;
        RecordsStudyModels.StudyItem item = newTargetedStudyItem(activeRepair.repairKanji, now);
        activeSession = new RecordsSchedulerModels.StudySession(
                item.withToken(active.token()),
                null,
                active.token(),
                TASK_REPAIR_WRITING,
                true,
                similarRepairPrompt(activeRepair)
        );
        activeStudyPlan = plan;
        registerStudyTaskShown(active.progressKey());
        startActiveStudyTask(active.studyTaskKey(), activeRepair.repairKanji, TASK_REPAIR_WRITING, now);
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
        box.addView(studyAnswerDetailsRow(session, 76));
        return box;
    }

    LinearLayout studyAnswerDetailsRow(RecordsSchedulerModels.StudySession session, int glyphSize) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView glyph = text(session.item.kanji, glyphSize, STUDY_PLUM, true);
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
        return row;
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
            Toast.makeText(this, StudyTextCopy.typingAnswerAcceptedToast(), Toast.LENGTH_SHORT).show();
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
        int touchSlop = ViewConfiguration.get(this).getScaledTouchSlop();
        FlashcardGesturePolicy.Decision decision = FlashcardGesturePolicy.release(
                flashcardTouchStartX,
                flashcardTouchStartY,
                event.getRawX(),
                event.getRawY(),
                touchSlop,
                dp(72),
                flashcardAnswerRevealed
        );
        switch (decision.action) {
            case REVEAL:
                revealFlashcardAnswer();
                return true;
            case REVIEW:
                submitReview(decision.rating, false);
                return true;
            default:
                return false;
        }
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

        studyActionBar.addView(writingToolActions());
        studyActionBar.addView(writingPrimaryActions());
        studyActionBar.addView(writingFallbackActions());
    }

    LinearLayout writingToolActions() {
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button clear = studySecondaryButton("Erase");
        clear.setOnClickListener(v -> eraseWritingPad());
        actions.addView(clear, new LinearLayout.LayoutParams(0, dp(58), 1));
        undoStrokeButton = studySecondaryButton("Undo");
        undoStrokeButton.setOnClickListener(v -> undoWritingStroke());
        actions.addView(undoStrokeButton, new LinearLayout.LayoutParams(0, dp(58), 1));
        hintButton = studySecondaryButton("Hint");
        hintButton.setOnClickListener(v -> showWritingHint());
        actions.addView(hintButton, new LinearLayout.LayoutParams(0, dp(58), 1));
        return actions;
    }

    LinearLayout writingPrimaryActions() {
        LinearLayout primaryActions = new LinearLayout(this);
        primaryActions.setOrientation(LinearLayout.HORIZONTAL);
        checkWritingButton = pinkPrimaryButton("Check");
        checkWritingButton.setOnClickListener(v -> checkWriting());
        primaryActions.addView(checkWritingButton, new LinearLayout.LayoutParams(0, dp(62), 1));

        downloadModelButton = studySecondaryButton("Download checker");
        downloadModelButton.setOnClickListener(v -> downloadWritingModel());
        primaryActions.addView(downloadModelButton, new LinearLayout.LayoutParams(0, dp(62), 1));

        nextAfterPassButton = pinkPrimaryButton(LABEL_PASS);
        nextAfterPassButton.setOnClickListener(v -> submitReview(WritingFeedbackCopy.submitRating(activeAnalysis), false));
        primaryActions.addView(nextAfterPassButton, new LinearLayout.LayoutParams(0, dp(62), 1));
        return primaryActions;
    }

    LinearLayout writingFallbackActions() {
        LinearLayout fallbackActions = new LinearLayout(this);
        fallbackActions.setOrientation(LinearLayout.HORIZONTAL);
        replayButton = studySecondaryButton("Replay");
        replayButton.setOnClickListener(v -> replayWritingAnalysis());
        fallbackActions.addView(replayButton, new LinearLayout.LayoutParams(0, dp(56), 1));

        manualOverrideButton = studySecondaryButton("Mark right anyway");
        manualOverrideButton.setOnClickListener(v -> submitReview(RATING_GOOD, true));
        fallbackActions.addView(manualOverrideButton, new LinearLayout.LayoutParams(0, dp(56), 1));

        practiceWithGuideButton = studySecondaryButton("Try again with full guide");
        practiceWithGuideButton.setOnClickListener(v -> startGuidedWritingRetry());
        fallbackActions.addView(practiceWithGuideButton, new LinearLayout.LayoutParams(0, dp(56), 1));
        return fallbackActions;
    }

    void eraseWritingPad() {
        drawingPad.clear();
        activeAnalysis = null;
        setStudyStatus(guideLabel(currentHintState, strokeGuide(activeSession.item.kanji)), MUTED);
        updateResultActions();
    }

    void startGuidedWritingRetry() {
        setHintState(HintState.initial());
        hintsUsed++;
        activeAnalysis = null;
        drawingPad.clear();
        StrokeGuide guide = strokeGuide(activeSession.item.kanji);
        drawingPad.setGuide(guide, currentHintState, false);
        setStudyStatus(WritingFeedbackCopy.freshGuidedTryStatus(guideLabel(currentHintState, guide)), MUTED);
        updateResultActions();
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
        CapturedWritingAttempt attempt;
        try {
            attempt = capturedWritingAttempt();
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
            recognizeWriting(recognizer, attempt.captured, attempt.sample, guide, target, token);
        });
    }

    CapturedWritingAttempt capturedWritingAttempt() {
        return new CapturedWritingAttempt(drawingPad.capturedWriting(), drawingPad.writingSample());
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
        StudyReviewRequestPolicy.MappedReview mappedReview = StudyReviewRequestPolicy.from(
                activeSession,
                StudyReviewWritingOutcome.from(activeAnalysis),
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
        completeActiveRepairStudyTask(similarRepairStudyTaskKey(repair), rating, now);
        StudyRepairActions.RepairCompletion completion = StudyRepairActions.completeSimilarWritingRepair(
                repair,
                rating,
                now,
                store::finishSimilarWritingRepair,
                studySessionTracker::recordRepairOutcome,
                this::markStudyTaskCompleted
        );
        Toast.makeText(
                this,
                StudyTextCopy.similarWritingRepairSavedToast(completion.passed()),
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
        StudyReviewActions.saveAppliedReview(
                request,
                result,
                activeSession.item,
                now,
                reviewWriter(),
                studySessionTracker::recordReviewOutcome,
                this::markStudyRunPassed
        );
    }

    void tuneSchedulerIfNeeded(RecordsSchedulerModels.SchedulerParameters parameters, long now) {
        RecordsSchedulerModels.SchedulerParameters tuned = new SchedulerTuner().maybeTune(parameters, store.reviewStatsSince(now - SchedulerTuner.MONTH_MILLIS), now);
        StudyReviewActions.saveTunedSchedulerIfChanged(parameters, tuned, store::saveSchedulerParameters);
    }

    StudyReviewActions.ReviewWriter reviewWriter() {
        return new StudyReviewActions.ReviewWriter() {
            @Override
            public void saveStudyItem(RecordsStudyModels.StudyItem item) {
                store.saveStudyItem(item);
            }

            @Override
            public void saveReview(
                    RecordsSchedulerModels.ReviewRequest request,
                    String appliedRating,
                    long reviewedAt,
                    RecordsStudyModels.StudyItem beforeReview,
                    RecordsStudyModels.StudyItem afterReview
            ) {
                store.saveReview(request, appliedRating, reviewedAt, beforeReview, afterReview);
            }
        };
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
        setStudyStatus(WritingFeedbackCopy.hintUsedStatus(guideLabel(currentHintState, guide)), MUTED);
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
        String targetKanji = activeSession == null ? null : activeSession.item.kanji;
        Integer activeWritingLevel = activeSession == null ? null : activeSession.item.writingLevel;
        String message = WritingFeedbackCopy.resultMessage(
                analysis,
                targetKanji,
                activeWritingLevel,
                shouldIncreaseSupportAfterAnalysis(analysis),
                diagnosisText(analysis)
        );
        setStudyStatus(guideLabel(currentHintState, guide), MUTED);
        setResultStatus(message, color);
        updateResultActions();
    }

    void updateResultActions() {
        WritingActionPresentation presentation = writingActionPresentation();
        updateUndoStrokeButton(presentation);
        updateCheckWritingButton(presentation);
        updateDownloadModelButton(presentation);
        updateNextAfterPassButton(presentation);
        updateFallbackActionButtons(presentation);
        updateHintAndAnswerVisibility(presentation);
        if (resultStatus != null && !presentation.resultStatusVisible) {
            resultStatus.setVisibility(View.GONE);
        }
    }

    WritingActionPresentation writingActionPresentation() {
        WritingActionPresentation.Input input = new WritingActionPresentation.Input(activeAnalysis);
        input.checkingWriting = checkingWriting;
        input.canUndoStroke = drawingPad != null && drawingPad.canUndoStroke();
        input.writingModelStatusKnown = writingModelStatusKnown;
        input.writingModelDownloaded = writingModelDownloaded;
        input.hasReplaySnapshot = drawingPad != null && drawingPad.hasReplaySnapshot();
        input.hasInk = drawingPad != null && drawingPad.hasInk();
        input.guide = activeSession == null ? null : strokeGuide(activeSession.item.kanji);
        input.canRevealMoreHelp = canRevealMoreHelp();
        input.recallTask = activeSession != null && isRecallTask(activeSession);
        input.teachingTask = activeSession != null && isTeachingTask(activeSession);
        input.currentPracticeLevel = currentPracticeLevel;
        return WritingActionPresentation.from(input);
    }

    void updateCheckWritingButton(WritingActionPresentation presentation) {
        if (checkWritingButton != null) {
            checkWritingButton.setVisibility(presentation.checkVisible ? View.VISIBLE : View.GONE);
            checkWritingButton.setEnabled(presentation.checkEnabled);
            checkWritingButton.setText(presentation.checkText);
            checkWritingButton.setOnClickListener(presentation.messyPass ? v -> startCleanerRetry() : v -> checkWriting());
        }
    }

    void updateUndoStrokeButton() {
        updateUndoStrokeButton(writingActionPresentation());
    }

    void updateUndoStrokeButton(WritingActionPresentation presentation) {
        if (undoStrokeButton != null) {
            undoStrokeButton.setVisibility(View.VISIBLE);
            undoStrokeButton.setEnabled(presentation.undoEnabled);
        }
    }

    void updateDownloadModelButton(WritingActionPresentation presentation) {
        if (downloadModelButton != null) {
            downloadModelButton.setVisibility(presentation.downloadVisible ? View.VISIBLE : View.GONE);
        }
    }

    void updateNextAfterPassButton(WritingActionPresentation presentation) {
        if (nextAfterPassButton != null) {
            nextAfterPassButton.setVisibility(presentation.nextVisible ? View.VISIBLE : View.GONE);
            if (presentation.nextVisible) {
                nextAfterPassButton.setText(presentation.nextLabel);
                nextAfterPassButton.setOnClickListener(v -> submitReview(presentation.nextRating, false));
            }
        }
    }

    void updateFallbackActionButtons(WritingActionPresentation presentation) {
        if (manualOverrideButton != null) {
            manualOverrideButton.setVisibility(presentation.manualOverrideVisible ? View.VISIBLE : View.GONE);
        }
        if (practiceWithGuideButton != null) {
            practiceWithGuideButton.setVisibility(presentation.practiceWithGuideVisible ? View.VISIBLE : View.GONE);
        }
        if (replayButton != null) {
            replayButton.setVisibility(presentation.replayVisible ? View.VISIBLE : View.GONE);
        }
    }

    void updateHintAndAnswerVisibility(WritingActionPresentation presentation) {
        if (hintButton != null) {
            hintButton.setVisibility(presentation.hintVisible ? View.VISIBLE : View.GONE);
            hintButton.setText(presentation.hintText);
        }
        if (studyAnswerPanel != null) {
            studyAnswerPanel.setVisibility(presentation.answerPanelVisible ? View.VISIBLE : View.GONE);
        }
    }

    boolean isTeachingTask(RecordsSchedulerModels.StudySession session) {
        return StudyTaskCopy.isTeachingTask(session);
    }

    boolean shouldShowLearningPanel(WritingAnalysis analysis) {
        return WritingFeedbackCopy.shouldShowLearningPanel(
                analysis,
                activeSession != null && isRecallTask(activeSession),
                activeSession != null && isTeachingTask(activeSession),
                currentPracticeLevel
        );
    }

    boolean canRevealMoreHelp() {
        if (activeSession == null) {
            return false;
        }
        StrokeGuide guide = strokeGuide(activeSession.item.kanji);
        return hintProgression.canRevealMoreHelp(currentHintState, guide);
    }

    boolean shouldIncreaseSupportAfterAnalysis(WritingAnalysis analysis) {
        return WritingFeedbackCopy.shouldIncreaseSupportAfterAnalysis(analysis);
    }

    void startCleanerRetry() {
        if (drawingPad == null || activeSession == null) {
            return;
        }
        clearWritingResult();
        StrokeGuide guide = strokeGuide(activeSession.item.kanji);
        drawingPad.clear();
        drawingPad.setGuide(guide, currentHintState, false);
        setStudyStatus(WritingFeedbackCopy.cleanerRetryStatus(guideLabel(currentHintState, guide)), MUTED);
        updateResultActions();
    }

    void undoWritingStroke() {
        if (drawingPad == null || activeSession == null || !drawingPad.undoLastStroke()) {
            updateUndoStrokeButton();
            return;
        }
        clearWritingResult();
        StrokeGuide guide = strokeGuide(activeSession.item.kanji);
        setStudyStatus(WritingFeedbackCopy.undoStrokeStatus(guideLabel(currentHintState, guide)), MUTED);
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
        clearWritingResult();
        drawingPad.clearReplaySnapshot();
        StrokeGuide guide = strokeGuide(activeSession.item.kanji);
        setStudyStatus(WritingFeedbackCopy.updatedInkStatus(guideLabel(currentHintState, guide)), MUTED);
        updateResultActions();
    }

    void clearWritingResult() {
        activeAnalysis = null;
        if (resultStatus != null) {
            resultStatus.setVisibility(View.GONE);
        }
    }

    void handleDrawingBlocked(StrokeGuideGuard.Decision decision) {
        if (activeSession == null || drawingPad == null) {
            return;
        }
        StrokeGuide guide = strokeGuide(activeSession.item.kanji);
        setStudyStatus(WritingFeedbackCopy.blockedStrokeStatus(guideLabel(currentHintState, guide), decision), MUTED);
        updateUndoStrokeButton();
    }

    boolean canReplayAnalysis(WritingAnalysis analysis, StrokeGuide guide) {
        return WritingFeedbackCopy.canReplayAnalysis(analysis, drawingPad != null && drawingPad.hasInk(), guide);
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
        return StudyTaskCopy.isRecallTask(session);
    }

    boolean isFontRecognitionTask(RecordsSchedulerModels.StudySession session) {
        return StudyTaskCopy.isFontRecognitionTask(session);
    }

    boolean isTypingMeaningTask(RecordsSchedulerModels.StudySession session) {
        return StudyTaskCopy.isTypingMeaningTask(session);
    }

    boolean isMeaningKanjiTask(RecordsSchedulerModels.StudySession session) {
        return StudyTaskCopy.isMeaningKanjiTask(session);
    }

    boolean isWordReadingTask(RecordsSchedulerModels.StudySession session) {
        return StudyTaskCopy.isWordReadingTask(session);
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
            updateWritingModelAvailability(false);
            setStudyStatus(
                    WritingFeedbackCopy.unavailableModelStatusMessage(guideStatusPrefix(strokeGuide(activeSession.item.kanji))),
                    CORAL
            );
            updateResultActions();
            return;
        }
        recognizer.modelStatus().whenComplete((status, error) -> main.post(() -> {
            if (token == null || !isActiveToken(token)) {
                return;
            }
            updateWritingModelAvailability(error == null && status != null && status.downloaded);
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
            setStudyStatus(WritingFeedbackCopy.modelStatusMessage(prefix, status != null, false, error != null), CORAL);
            return;
        }
        if (!status.downloaded) {
            setStudyStatus(WritingFeedbackCopy.modelStatusMessage(prefix, true, false, false), CORAL);
            return;
        }
        setStudyStatus(WritingFeedbackCopy.modelStatusMessage(prefix, true, true, false), MUTED);
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
                updateWritingModelAvailability(false);
                setStudyStatus("Handwriting checker download failed: " + error.getMessage(), CORAL);
            } else {
                updateWritingModelAvailability(true);
                setStudyStatus("Handwriting checker ready.", TEAL);
            }
            updateResultActions();
        }));
    }

    void updateWritingModelAvailability(boolean downloaded) {
        writingModelStatusKnown = true;
        writingModelDownloaded = downloaded;
    }
}
