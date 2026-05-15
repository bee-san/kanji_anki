package dev.bee.kanjianki;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.core.SimilarKanjiIndex;
import dev.bee.kanjianki.core.study.HintLevel;
import dev.bee.kanjianki.core.study.HintState;
import dev.bee.kanjianki.core.study.InkPoint;
import dev.bee.kanjianki.core.study.InkStroke;
import dev.bee.kanjianki.core.study.RecognitionCandidate;
import dev.bee.kanjianki.core.study.StrokeDiagnosis;
import dev.bee.kanjianki.core.study.StrokeGuide;
import dev.bee.kanjianki.core.study.StrokeOrderEvaluator;
import dev.bee.kanjianki.core.study.WritingAnalysis;
import dev.bee.kanjianki.core.study.WritingSample;
import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.data.StudyStatsStore;
import dev.bee.kanjianki.study.CapturedStroke;
import dev.bee.kanjianki.study.CapturedWriting;
import dev.bee.kanjianki.study.WritingRecognizer;
import dev.bee.kanjianki.sync.ManualSyncEngine;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class MainActivityHelperInstrumentedTest {
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("kanji_anki_simple.db");
        MainActivity.setAnkiDroidGatewayForTests(AnkiDroidGateway.testProvider(context, "dev.bee.kanjianki.helper_no_anki"));
        MainActivity.setCollectionGatewayForTests(null);
        MainActivity.setWritingRecognizerForTests(null);
        MainActivity.setWritingRecognizerFactoryForTests(null);
        MainActivity.setInstallPermissionForTests(null);
        MainActivity.setRuntimeNotificationPermissionForTests(null);
        MainActivity.setNotificationsAllowedForTests(null);
    }

    @After
    public void tearDown() {
        MainActivity.setAnkiDroidGatewayForTests(null);
        MainActivity.setCollectionGatewayForTests(null);
        MainActivity.setWritingRecognizerForTests(null);
        MainActivity.setWritingRecognizerFactoryForTests(null);
        MainActivity.setInstallPermissionForTests(null);
        MainActivity.setRuntimeNotificationPermissionForTests(null);
        MainActivity.setNotificationsAllowedForTests(null);
        context.deleteDatabase("kanji_anki_simple.db");
        deleteRecursively(new File(context.getCacheDir(), "updates"));
    }

    @Test
    public void baseTextHelpersDescribeStudyModesAndWritingGuides() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals("1 item", activity.countText(1, "item", "items"));
                assertEquals("2 items", activity.countText(2, "item", "items"));
                assertEquals("", activity.compact(null, 12));
                assertEquals("short", activity.compact("short", 12));
                assertEquals("a very long s...", activity.compact("a very long sentence that should be shortened", 16));

                assertEquals("Study", activity.labelForTask(null));
                assertEquals("Focused recall", activity.labelForTask("targeted_flashcard"));
                assertEquals("Kanji -> meaning", activity.labelForTask(BridgeScheduler.TASK_KANJI_MEANING));
                assertEquals("Type the meaning", activity.labelForTask(BridgeScheduler.TASK_TYPE_MEANING));
                assertEquals("Font -> meaning", activity.labelForTask(BridgeScheduler.TASK_FONT_MEANING));
                assertEquals("Word -> reading", activity.labelForTask(BridgeScheduler.TASK_WORD_READING));
                assertEquals("Write kanji", activity.labelForTask(BridgeScheduler.TASK_WRITE_KANJI));
                assertEquals("Similar kanji", activity.labelForTask(BridgeScheduler.TASK_SIMILAR_KANJI));
                assertEquals("Quick recall", activity.labelForTask("meaning_flashcard"));
                assertEquals("Font check", activity.labelForTask("font_recognition"));
                assertEquals("Write to repair", activity.labelForTask("repair_writing"));
                assertEquals("Focused practice", activity.labelForTask("targeted_writing"));
                assertEquals("New problem kanji", activity.labelForTask("context_writing"));
                assertEquals("Guided review", activity.labelForTask("guided_writing"));
                assertEquals("Memory check", activity.labelForTask("blind_writing"));
                assertEquals("Memory check", activity.labelForTask("sampled_handwriting"));
                assertEquals("Learn the shape", activity.labelForTask("confusable_recognition"));
                assertEquals("Study", activity.labelForTask("unexpected"));
                assertEquals("android.permission.POST_NOTIFICATIONS", MainActivityBase.PERMISSION_POST_NOTIFICATIONS);

                Records.AdaptiveLoadPlan waiting = new Records.AdaptiveLoadPlan(20, 0, 0, Collections.emptyList(), 0, false, "");
                Records.AdaptiveLoadPlan all = new Records.AdaptiveLoadPlan(100, 3, 3, Arrays.asList("裂", "提", "語"), 0, true, "all");
                Records.AdaptiveLoadPlan focused = new Records.AdaptiveLoadPlan(20, 5, 2, Arrays.asList("裂", "提"), 0, false, "focus");
                assertEquals("Adaptive focus is waiting for sync", activity.adaptiveFocusText(null));
                assertEquals("Adaptive focus is waiting for sync", activity.adaptiveFocusText(waiting));
                assertEquals("Adaptive focus is set to all current problem kanji", activity.adaptiveFocusText(all));
                assertEquals("Today's adaptive focus: 2 items left / 5", activity.adaptiveFocusText(focused));

                StrokeGuide emptyGuide = new StrokeGuide("裂", Collections.emptyList());
                StrokeGuide guide = guide("裂");
                assertTrue(activity.guideLabel(3, emptyGuide).startsWith("Write from memory"));
                assertTrue(activity.guideLabel(HintState.fromWritingLevel(3), emptyGuide).startsWith("Write from memory"));
                assertTrue(activity.guideLabel(HintState.fromWritingLevel(0), emptyGuide).startsWith("No numbered stroke guide"));
                assertEquals("Trace the numbered strokes, then check. This is a learning attempt.", activity.guideLabel(HintState.fromWritingLevel(0), guide));
                assertEquals("Copy the faint outline; the current stroke is emphasized.", activity.guideLabel(HintState.fromWritingLevel(1), guide));
                assertEquals("Write with only the current stroke hinted, then check.", activity.guideLabel(HintState.fromWritingLevel(2), guide));
                assertEquals("Write from memory, then check. Use Hint if you are stuck.", activity.guideLabel(HintState.fromWritingLevel(3), guide));
                assertEquals("Trace", activity.stageLabel(HintLevel.TRACE));
                assertEquals("Blind", activity.stageLabel(HintLevel.BLIND));
                assertEquals("", activity.attemptProgressText(null));
                assertEquals("", activity.targetRevealText(null));
            });
        }
    }

    @Test
    public void baseLifecyclePermissionAndProgressHelpersCoverStatefulCallbacks() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.handleLaunchIntent(new Intent().putExtra(MainActivity.EXTRA_OPEN_UPDATE, true));
                assertHasText(activity, "GitHub updater");

                activity.handlePermissionResult(7, new int[]{PackageManager.PERMISSION_DENIED});
                assertHasText(activity, "Kani");
                activity.onRequestPermissionsResult(7, new String[0], new int[]{PackageManager.PERMISSION_DENIED});
                assertHasText(activity, "Kani");

                activity.pendingReminderSettings = new LocalStore.ReminderSettings(true, 8, 30);
                activity.handlePostNotificationPermission(new int[]{PackageManager.PERMISSION_GRANTED});
                assertTrue(activity.store.reminderSettings().enabled);
                activity.pendingReminderSettings = new LocalStore.ReminderSettings(true, 9, 15);
                activity.handlePostNotificationPermission(new int[]{PackageManager.PERMISSION_DENIED});
                assertFalse(activity.store.reminderSettings().enabled);
                activity.pendingReminderSettings = new LocalStore.ReminderSettings(true, 10, 45);
                activity.handlePermissionResult(MainActivityBase.REQUEST_POST_NOTIFICATIONS, new int[]{PackageManager.PERMISSION_GRANTED});
                assertTrue(activity.store.reminderSettings().enabled);
                activity.handlePermissionResult(999, new int[]{PackageManager.PERMISSION_DENIED});
                assertTrue(activity.store.reminderSettings().enabled);

                long now = System.currentTimeMillis();
                Records.StudyItem retired = studyItem("退", Records.LadderRung.KANJI_MEANING, "retired", now);
                Records.StudyItem learningDue = studyItem("学", Records.LadderRung.KANJI_MEANING, "learning", now - 1L);
                Records.StudyItem learningFuture = studyItem("未", Records.LadderRung.KANJI_MEANING, "learning", now + 60_000L);
                Records.StudyItem reviewDue = studyItem("復", Records.LadderRung.KANJI_MEANING, "review", now - 1L);
                assertFalse(activity.itemDueForFocus(null, now));
                assertFalse(activity.itemDueForFocus(retired, now));
                assertTrue(activity.itemDueForFocus(learningDue, now));
                assertFalse(activity.itemDueForFocus(learningFuture, now));
                assertFalse(activity.itemDueForFocus(studyItem("新", Records.LadderRung.KANJI_MEANING, "review", now).copyBuilder().totalReviews(0).build(), now));
                assertTrue(activity.itemDueForFocus(reviewDue.copyBuilder().totalReviews(1).build(), now));
                activity.studyMoreNewCardKanji.add("復");
                Records.AdaptiveLoadPlan extraPlan = activity.studyMoreNewCardsPlan(
                        Collections.singletonList(row("復", "review", "フク", Collections.emptyList())),
                        Collections.singletonList(reviewDue.copyBuilder().totalReviews(1).build()),
                        now
                );
                assertEquals(1, extraPlan.remaining);

                Records.AdaptiveLoadPlan all = activity.allCurrentProblemKanjiPlan(
                        Arrays.asList(row("裂", "split", "レツ", Collections.emptyList()), row("語", "language", "ゴ", Collections.emptyList())),
                        Collections.singletonList(studyItem("裂", Records.LadderRung.KANJI_MEANING, "review", now - 1L).copyBuilder().totalReviews(1).build()),
                        now
                );
                assertEquals(2, all.target);
                assertTrue(all.allKanjiMode);

                activity.activeSession = session("裂", BridgeScheduler.TASK_KANJI_MEANING, row("裂", "split", "レツ", Collections.emptyList()));
                activity.sessionProgressCompleted = 2;
                activity.sessionProgressMax = 2;
                activity.continueAllKanjiSession = true;
                assertTrue(activity.studyTopBar(all) instanceof StudyTopBarView);
                Records.StudyItem clueItem = studyItem("?", Records.LadderRung.KANJI_MEANING, "review", now);
                assertEquals(
                        "Fallback prompt",
                        activity.sessionClue(new Records.StudySession(clueItem, null, "tok", BridgeScheduler.TASK_KANJI_MEANING, false, "fallback prompt"))
                );
                assertEquals("Fallback", activity.canonicalKanjiMeaning("?", "fallback", 40));
                FakeWritingRecognizer cachedRecognizer = new FakeWritingRecognizer(
                        CompletableFuture.completedFuture(new WritingRecognizer.ModelStatus("ja", "ja-JP", true, "ready")),
                        CompletableFuture.completedFuture(new WritingRecognizer.ModelStatus("ja", "ja-JP", true, "ready")),
                        CompletableFuture.completedFuture(new WritingRecognizer.RecognitionResult(Collections.emptyList()))
                );
                activity.writingRecognizer = cachedRecognizer;
                assertTrue(activity.writingRecognizer() == cachedRecognizer);

                MainActivityBase.ActiveStudyTask timing = new MainActivityBase.ActiveStudyTask(null, null, null, -10L);
                timing.pause(50L);
                timing.resume(60L);
                timing.pause(90L);
                assertEquals(30L, timing.activeElapsedMillis);

            });
        }
    }

    @Test
    public void baseEqualHeightRowSkipsGoneChildrenAndHonorsExactHeight() {
        MainActivityBase.EqualHeightRow equalRow = new MainActivityBase.EqualHeightRow(context);
        TextView gone = new TextView(context);
        gone.setVisibility(View.GONE);
        equalRow.addView(gone, new LinearLayout.LayoutParams(100, 40));
        equalRow.measure(
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.AT_MOST)
        );
        assertEquals(0, equalRow.getMeasuredHeight());

        TextView visible = new TextView(context);
        visible.setText("Tall");
        LinearLayout.LayoutParams visibleLp = new LinearLayout.LayoutParams(100, 30);
        visibleLp.setMargins(0, 3, 0, 7);
        equalRow.addView(visible, visibleLp);
        equalRow.measure(
                View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(60, View.MeasureSpec.EXACTLY)
        );
        assertEquals(60, equalRow.getMeasuredHeight());
        assertEquals(60, MainActivityBase.EqualHeightRow.measuredOuterHeight(visible));
        MainActivityBase.EqualHeightRow.measureVisibleChild(gone, 20);
        MainActivityBase.EqualHeightRow.measureVisibleChild(visible, 0);
    }

    @Test
    public void studySessionHelpersPickExamplesPromptsTitlesAndTaskKinds() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                Records.Example suspended = example("停止語", "テイシゴ", "suspended", MainActivityBase.SOURCE_SUSPENDED);
                Records.Example active = example("活動語", "カツドウゴ", "active", MainActivityBase.SOURCE_ACTIVE);
                Records.Example fallback = example("予備語", "ヨビゴ", "fallback", "other");
                Records.DashboardRow row = row("語", "language", "ゴ", Arrays.asList(fallback, active, suspended));

                assertEquals(active, activity.firstExample(row));
                assertEquals(suspended, activity.wordReadingExample(row));
                assertEquals(fallback, activity.firstExample(row("語", "language", "ゴ", Collections.singletonList(fallback))));
                assertEquals(suspended, activity.exampleForSession(session("語", BridgeScheduler.TASK_WORD_READING, row)));
                assertEquals(active, activity.exampleForSession(session("語", BridgeScheduler.TASK_KANJI_MEANING, row)));

                assertEquals("What is the reading?", activity.heroQuestion(session("語", BridgeScheduler.TASK_WORD_READING, row)));
                assertEquals("What does this kanji mean?", activity.heroQuestion(session("語", BridgeScheduler.TASK_KANJI_MEANING, row)));
                assertEquals("Read this word", activity.flashcardTitle(session("語", BridgeScheduler.TASK_WORD_READING, row)));
                assertEquals("Type the meaning", activity.flashcardTitle(session("語", BridgeScheduler.TASK_TYPE_MEANING, row)));
                assertEquals("Recognise this kanji", activity.flashcardTitle(session("語", BridgeScheduler.TASK_FONT_MEANING, row)));
                assertEquals("Name this kanji", activity.flashcardTitle(session("語", BridgeScheduler.TASK_KANJI_MEANING, row)));
                assertEquals("Read", activity.studyModeLabel(session("語", BridgeScheduler.TASK_WORD_READING, row)));
                assertEquals("Type", activity.studyModeLabel(session("語", BridgeScheduler.TASK_TYPE_MEANING, row)));
                assertEquals("Recognise", activity.studyModeLabel(session("語", BridgeScheduler.TASK_KANJI_MEANING, row)));
                assertEquals("活動語", activity.wordPrompt(session("語", BridgeScheduler.TASK_WORD_READING, row("語", "language", "ゴ", Collections.singletonList(active)))));
                assertEquals("語", activity.wordPrompt(session("語", BridgeScheduler.TASK_WORD_READING, row("語", "language", "ゴ", Collections.emptyList()))));
                assertEquals("active", activity.collectionMeaningForSession(session("語", BridgeScheduler.TASK_WORD_READING, row("語", "language", "ゴ", Collections.singletonList(active)))));
                assertEquals("active", activity.collectionMeaningForSession(session("語", BridgeScheduler.TASK_KANJI_MEANING, row)));
                assertEquals("", activity.collectionMeaningForSession(null));

                assertTrue(activity.isWordReadingTask(session("語", BridgeScheduler.TASK_WORD_READING, row)));
                assertTrue(activity.isTypingMeaningTask(session("語", BridgeScheduler.TASK_TYPE_MEANING, row)));
                assertTrue(activity.isFontRecognitionTask(session("語", BridgeScheduler.TASK_FONT_MEANING, row)));
                assertTrue(activity.isRecallTask(session("語", "blind_writing", row)));
                assertFalse(activity.isRecallTask(null));
            });
        }
    }

    @Test
    public void writingAnalysisHelpersExplainDiagnosisAndAllowedActions() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                Records.StudySession writing = session("裂", BridgeScheduler.TASK_WRITE_KANJI, row("裂", "split", "レツ", Collections.emptyList()));
                activity.activeSession = writing;
                activity.currentHintState = HintState.fromWritingLevel(1);
                StrokeDiagnosis diagnosis = StrokeDiagnosis.builder()
                        .add(StrokeDiagnosis.Label.WRONG_ORDER, 1)
                        .add(StrokeDiagnosis.Label.WRONG_DIRECTION, 2)
                        .add(StrokeDiagnosis.Label.MISSING_STROKE, 3)
                        .add(StrokeDiagnosis.Label.ROUGH_SHAPE, 4)
                        .add(StrokeDiagnosis.Label.RECOGNIZED_BUT_MESSY, 5)
                        .build();
                StrokeOrderEvaluator.StrokeOrderResult order = StrokeOrderEvaluator
                        .evaluate(guide("裂"), sample())
                        .withDiagnosis(diagnosis);
                WritingAnalysis wrong = analysis(WritingAnalysis.Status.WRONG, false, order);
                WritingAnalysis close = analysis(WritingAnalysis.Status.CLOSE, true, order);
                WritingAnalysis pass = new WritingAnalysis(
                        WritingAnalysis.Status.PASS,
                        "good",
                        true,
                        "Clean",
                        Collections.emptyList(),
                        order,
                        HintLevel.OUTLINE,
                        0
                );

                assertTrue(activity.diagnosisText(wrong).contains("Stroke 1: likely wrong order"));
                assertTrue(activity.diagnosisText(wrong).contains("Recognized, but the stroke path was messy"));
                assertEquals("Stroke 2: likely wrong direction", activity.diagnosisLine(diagnosis.entries.get(1)));
                assertEquals("Stroke 3: may be missing", activity.diagnosisLine(diagnosis.entries.get(2)));
                assertEquals("Stroke 4: shape looks rough", activity.diagnosisLine(diagnosis.entries.get(3)));
                assertTrue(activity.canShowDiagnosis(wrong));
                assertFalse(activity.canShowDiagnosis(analysis(WritingAnalysis.Status.NO_INK, false, order)));

                assertTrue(activity.canSubmitAnalysis(wrong));
                assertTrue(activity.canManualOverride(wrong));
                assertTrue(activity.canPracticeAfterAnalysis(wrong));
                assertFalse(activity.canSubmitAnalysis(null));
                assertFalse(activity.canManualOverride(close));
                assertTrue(activity.shouldIncreaseSupportAfterAnalysis(wrong));
                assertFalse(activity.shouldIncreaseSupportAfterAnalysis(close));
                assertTrue(activity.shouldShowLearningPanel(wrong));
                assertFalse(activity.shouldShowLearningPanel(analysis(WritingAnalysis.Status.NO_INK, false, order)));
                assertTrue(activity.attemptProgressText(close).contains("Try cleaner"));
                assertTrue(activity.attemptProgressText(pass).contains("less help"));
                assertTrue(activity.targetRevealText(wrong).contains("Target: 裂"));
            });
        }
    }

    @Test
    public void settingsHelpersSummarizeImportTimingAndWorkloadChoices() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals("unknown version", activity.versionText(""));
                assertEquals("0.4.33", activity.versionText("v0.4.33"));
                assertEquals(1, MainActivitySettings.boolFlag(true));
                assertEquals(0, MainActivitySettings.boolFlag(false));
                assertEquals(0, activity.rankSliderProgress(-20));
                assertEquals(19999, activity.rankSliderProgress(50_000));
                assertEquals(1, activity.rankFromSliderProgress(-4));
                assertEquals(20000, activity.rankFromSliderProgress(50_000));
                assertEquals("Jiten ranks 10-25", activity.frequencyRangeStatusText(10, 25));
                assertEquals(80, activity.retentionPercent(0.1));
                assertEquals(97, activity.retentionPercent(1.0));
                assertEquals("Desired retention: 90%", activity.retentionStatusText(90));

                assertTrue(activity.validImportThresholds(7.5, 3, 2));
                assertFalse(activity.validImportThresholds(0.5, 3, 2));
                assertFalse(activity.hasSelectedImportSource(
                        checked(activity, false),
                        checked(activity, false),
                        checked(activity, false),
                        checked(activity, false),
                        checked(activity, false),
                        Collections.emptyList(),
                        ""
                ));
                assertTrue(activity.hasSelectedImportSource(
                        checked(activity, false),
                        checked(activity, true),
                        checked(activity, false),
                        checked(activity, false),
                        checked(activity, false),
                        Collections.emptyList(),
                        ""
                ));
                assertTrue(activity.hasSelectedImportSource(
                        checked(activity, false),
                        checked(activity, false),
                        checked(activity, true),
                        checked(activity, false),
                        checked(activity, false),
                        Collections.singletonList("leeches"),
                        ""
                ));
                assertTrue(activity.hasSelectedImportSource(
                        checked(activity, false),
                        checked(activity, false),
                        checked(activity, false),
                        checked(activity, false),
                        checked(activity, true),
                        Collections.emptyList(),
                        "deck:Kiku"
                ));
                assertEquals("3 matching cards per kanji", activity.matchingCardsSummary(settings(true, true, true, Arrays.asList("leeches"), true, true, "deck:Kiku")));
                assertTrue(activity.settingsImportSummary(settings(true, true, true, Arrays.asList("leeches"), true, true, "deck:Kiku")).contains("tagged"));
                assertEquals("No sources", activity.settingsImportSummary(settings(false, false, false, Collections.emptyList(), false, false, "")));

                LocalStore.AutoSyncSettings unconfigured = new LocalStore.AutoSyncSettings(false, true, 7, 30, 0L, 0L, 0L);
                LocalStore.AutoSyncSettings enabled = new LocalStore.AutoSyncSettings(true, true, 7, 30, 1000L, 2000L, 3000L);
                LocalStore.AutoSyncSettings disabled = new LocalStore.AutoSyncSettings(true, false, 7, 30, 1000L, 0L, 0L);
                LocalStore.AutoSyncSettings enabledNoHistory = new LocalStore.AutoSyncSettings(true, true, 7, 30, 0L, 0L, 0L);
                LocalStore.AutoSyncSettings disabledNoHistory = new LocalStore.AutoSyncSettings(true, false, 7, 30, 0L, 0L, 0L);
                assertEquals("After first sync", activity.settingsAutoSyncSummary(unconfigured));
                assertEquals("07:30", activity.settingsAutoSyncSummary(enabled));
                assertEquals("Off", activity.settingsAutoSyncSummary(disabled));
                assertEquals("Starts after first successful sync", activity.autoSyncStatus(unconfigured));
                assertEquals("On around 07:30", activity.autoSyncStatus(enabled));
                assertEquals("Off", activity.autoSyncStatus(disabled));
                assertTrue(activity.autoSyncDetail(enabled).contains("Last auto success"));
                assertTrue(activity.autoSyncDetail(disabled).contains("Last auto attempt"));
                assertTrue(activity.autoSyncDetail(enabledNoHistory).contains("Scheduled once"));
                assertTrue(activity.autoSyncDetail(disabledNoHistory).contains("paused"));

                assertEquals("Pareto: up to 5 items", activity.workloadStatusText(20, 5));
                assertEquals("All kanji: up to 9 items", activity.workloadStatusText(100, 9));
                assertEquals("Maximum: 1 item", activity.maxItemsStatusText(1));
                assertEquals("Auto Pareto: waiting for problem kanji", activity.autoWorkloadStatusText(null));
                assertEquals(
                        "Auto Pareto: 2 items today",
                        activity.autoWorkloadStatusText(new Records.AdaptiveLoadPlan(true, 20, 2, 1, Arrays.asList("裂", "語"), 0, false, "auto"))
                );
                assertEquals("Blocked: notifications off", activity.reminderStatus(new LocalStore.ReminderSettings(true, 21, 5), true));
                assertEquals("Daily around 21:05", activity.reminderStatus(new LocalStore.ReminderSettings(true, 21, 5), false));
                assertEquals("Off", activity.reminderStatus(new LocalStore.ReminderSettings(false, 21, 5), false));
                assertEquals("21:05", activity.reminderTime(21, 5));
                assertEquals("Reminder time: 21:05", activity.reminderTimeButtonLabel(21, 5));
                int normalizedMax = AdaptiveLoadPlanner.normalizeMaxItems(0);
                assertEquals("Maximum: " + activity.countText(normalizedMax, "item", "items"), activity.maxItemsStatusText(0));

                EditText difficulty = new EditText(activity);
                EditText lapses = new EditText(activity);
                EditText minMatching = new EditText(activity);
                difficulty.setText("not numeric");
                lapses.setText("3");
                minMatching.setText("2");
                assertNull(activity.readImportThresholds(difficulty, lapses, minMatching));
                difficulty.setText("0.5");
                assertNull(activity.readImportThresholds(difficulty, lapses, minMatching));
                difficulty.setText("7.5");
                MainActivityBase.ImportThresholds thresholds = activity.readImportThresholds(difficulty, lapses, minMatching);
                assertTrue(thresholds != null);
                assertEquals(7.5, thresholds.difficulty, 0.001);
                assertEquals(3, thresholds.lapseThreshold);
                assertEquals(2, thresholds.minCards);

                int[] selectedRanks = {10, 100};
                TextView rankStatus = new TextView(activity);
                EditText minRank = new EditText(activity);
                EditText maxRank = new EditText(activity);
                SeekBar minSlider = new SeekBar(activity);
                SeekBar maxSlider = new SeekBar(activity);
                activity.bindRankSliders(selectedRanks, rankStatus, minRank, maxRank, minSlider, maxSlider);
                touchSeekBar(minSlider);
                touchSeekBar(maxSlider);
                minSlider.setProgress(activity.rankSliderProgress(50));
                maxSlider.setProgress(activity.rankSliderProgress(80));
                assertEquals(50, selectedRanks[0]);
                assertEquals(80, selectedRanks[1]);
                minSlider.setProgress(activity.rankSliderProgress(90));
                assertEquals(80, selectedRanks[0]);
                maxSlider.setProgress(activity.rankSliderProgress(70));
                assertEquals(80, selectedRanks[1]);
                assertTrue(rankStatus.getText().toString().contains("Jiten ranks"));

                LinearLayout maxOnlyBox = new LinearLayout(activity);
                int[] selectedMaxOnly = {AdaptiveLoadPlanner.MIN_MAX_ITEMS};
                activity.addMaxItemsControl(maxOnlyBox, selectedMaxOnly, null, null);
                SeekBar maxOnlySlider = seekBars(maxOnlyBox).get(0);
                touchSeekBar(maxOnlySlider);
                maxOnlySlider.setProgress(3);
                assertEquals(AdaptiveLoadPlanner.normalizeMaxItems(AdaptiveLoadPlanner.MIN_MAX_ITEMS + 3), selectedMaxOnly[0]);

                LinearLayout linkedMaxBox = new LinearLayout(activity);
                int[] selectedMax = {5};
                int[] selectedWorkload = {20};
                TextView workloadStatus = new TextView(activity);
                activity.addMaxItemsControl(linkedMaxBox, selectedMax, workloadStatus, selectedWorkload);
                SeekBar linkedSlider = seekBars(linkedMaxBox).get(0);
                touchSeekBar(linkedSlider);
                linkedSlider.setProgress(5);
                assertTrue(workloadStatus.getText().toString().contains("Pareto: up to"));
            });
        }
    }

    @Test
    public void settingsCategoriesTogglePanelsAndReferenceNavigation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.renderSettings();
                assertTrue(activity.settingsAnkiExpanded);
                assertFalse(activity.settingsStudyExpanded);
                assertTrue(containsText(activity.content, "Frequency range"));
                assertFalse(containsText(activity.content, "Daily workload"));

                performClickableWithText(activity.content, "Study behavior");
                assertTrue(activity.settingsStudyExpanded);
                assertTrue(containsText(activity.content, "Daily workload"));

                performClickableWithText(activity.content, "Anki source");
                assertFalse(activity.settingsAnkiExpanded);
                assertFalse(containsText(activity.content, "Frequency range"));

                performClickableWithText(activity.content, "Automation");
                assertTrue(activity.settingsSyncExpanded);
                assertTrue(containsText(activity.content, "Daily Anki sync"));

                performClickableWithText(activity.content, "Reference data");
                assertTrue(activity.settingsAppExpanded);
                assertTrue(containsText(activity.content, "Offline data & licenses"));
                performButtonClick(activity.content, "Open data licenses");
                assertHasText(activity, "Data licenses");
                performButtonClick(activity.content, "Back to settings");
                assertHasText(activity, "Settings cockpit");
            });
        }
    }

    @Test
    public void settingsPanelsPersistWorkloadAndLearningStepActions() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.store.saveAdaptiveLoadMode(AdaptiveLoadPlanner.MODE_AUTO);
                LinearLayout autoPanel = activity.workloadSettingsPanel();
                performButtonClick(autoPanel, "Save maximum");
                performButtonClick(autoPanel, "Use manual workload");
                assertEquals(AdaptiveLoadPlanner.MODE_MANUAL, activity.store.adaptiveLoadMode());

                activity.store.saveAdaptiveLoadMode(AdaptiveLoadPlanner.MODE_MANUAL);
                LinearLayout manualPanel = activity.workloadSettingsPanel();
                List<SeekBar> manualSliders = seekBars(manualPanel);
                manualSliders.get(0).setProgress(40);
                touchSeekBar(manualSliders.get(0));
                manualSliders.get(1).setProgress(4);
                touchSeekBar(manualSliders.get(1));
                performButtonClick(manualPanel, "Save workload");
                assertEquals(AdaptiveLoadPlanner.MODE_MANUAL, activity.store.adaptiveLoadMode());
                performButtonClick(manualPanel, "Use automatic Pareto");
                assertEquals(AdaptiveLoadPlanner.MODE_AUTO, activity.store.adaptiveLoadMode());

                LinearLayout stepsPanel = activity.learningStepsSettingsPanel();
                editTexts(stepsPanel).get(0).setText("bad");
                performButtonClick(stepsPanel, "Save learning steps");
                performButtonClick(stepsPanel, "Anki default");
                performButtonClick(stepsPanel, "Both 1m 10m");
                performButtonClick(stepsPanel, "Save learning steps");
                assertEquals("1m, 10m", activity.store.learningStepSettings().reviewStepsText());
            });
        }
    }

    @Test
    public void importFilterAndFrequencyPanelsValidateAndPersistRealSettings() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                LinearLayout importPanel = activity.importFilterSettingsPanel(activity.settings());
                List<CheckBox> boxes = checkBoxes(importPanel);
                List<EditText> inputs = editTexts(importPanel);
                assertEquals(5, boxes.size());
                assertEquals(5, inputs.size());

                boxes.get(4).setChecked(true);
                inputs.get(0).setText("");
                performButtonClick(importPanel, "Save import filters");
                assertFalse(activity.settings().importBrowserQueryCards);

                for (CheckBox box : boxes) {
                    box.setChecked(false);
                }
                performButtonClick(importPanel, "Save import filters");
                assertTrue(activity.settings().importSuspendedCards);

                boxes.get(1).setChecked(true);
                inputs.get(2).setText("not numeric");
                performButtonClick(importPanel, "Save import filters");
                assertTrue(activity.settings().importSuspendedCards);

                boxes.get(0).setChecked(true);
                boxes.get(1).setChecked(false);
                boxes.get(2).setChecked(true);
                boxes.get(3).setChecked(true);
                boxes.get(4).setChecked(true);
                inputs.get(0).setText("deck:Kiku tag:kani");
                inputs.get(1).setText("tagAlpha, tagBeta");
                inputs.get(2).setText("8.5");
                inputs.get(3).setText("4");
                inputs.get(4).setText("2");
                performButtonClick(importPanel, "Save import filters");

                Records.Settings saved = activity.settings();
                assertTrue(saved.importActiveCards);
                assertFalse(saved.importSuspendedCards);
                assertTrue(saved.importTaggedCardsEnabled());
                assertEquals(Arrays.asList("tagAlpha", "tagBeta"), saved.importTags);
                assertTrue(saved.importWeakCards);
                assertEquals(8.5, saved.importWeakFsrsDifficultyThreshold, 0.001);
                assertEquals(4, saved.importWeakLapsesThreshold);
                assertEquals(2, saved.importMinMatchingCardsPerKanji);
                assertTrue(saved.browserQueryImportEnabled());
                assertEquals("deck:Kiku tag:kani", saved.importBrowserQuery);

                LinearLayout frequencyPanel = activity.frequencyRangeSettingsPanel(activity.settings());
                List<EditText> rankInputs = editTexts(frequencyPanel);
                assertEquals(2, rankInputs.size());
                rankInputs.get(0).setText("many");
                performButtonClick(frequencyPanel, "Save frequency range");
                assertEquals(saved.suspendedRankMin, activity.settings().suspendedRankMin);

                rankInputs.get(0).setText("0");
                rankInputs.get(1).setText("25");
                performButtonClick(frequencyPanel, "Save frequency range");
                assertEquals(saved.suspendedRankMin, activity.settings().suspendedRankMin);
                rankInputs.get(0).setText("10");
                rankInputs.get(1).setText("50000");
                performButtonClick(frequencyPanel, "Save frequency range");
                assertEquals(saved.suspendedRankMin, activity.settings().suspendedRankMin);

                rankInputs.get(0).setText("300");
                rankInputs.get(1).setText("20");
                performButtonClick(frequencyPanel, "Save frequency range");
                Records.Settings ranked = activity.settings();
                assertEquals(20, ranked.suspendedRankMin);
                assertEquals(300, ranked.suspendedRankMax);
            });
        }
    }

    @Test
    public void noteTypeInputsWriteRealAndroidFieldsAndFallbackGuesses() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                EditText noteType = new EditText(activity);
                EditText expression = new EditText(activity);
                EditText reading = new EditText(activity);
                EditText meaning = new EditText(activity);
                EditText sentence = new EditText(activity);
                EditText frequency = new EditText(activity);
                EditText frequencySort = new EditText(activity);
                NoteTypeFieldMappings.Inputs inputs = new NoteTypeFieldMappings.Inputs(
                        noteType,
                        expression,
                        reading,
                        meaning,
                        sentence,
                        frequency,
                        frequencySort
                );

                NoteTypeFieldMappings.chooseNoteType(
                        new NoteTypeFieldMappings.Choice("Fallback Model", Arrays.asList("Front", "Back", "Kana")),
                        inputs
                );

                assertEquals("Fallback Model", noteType.getText().toString());
                assertEquals("Front", expression.getText().toString());
                assertEquals("Kana", reading.getText().toString());
                assertEquals("Back", meaning.getText().toString());
                assertEquals("", sentence.getText().toString());
                assertEquals("", frequency.getText().toString());
                assertEquals("", frequencySort.getText().toString());

                LinearLayout panel = activity.noteTypeSettingsPanel(activity.settings());
                List<EditText> panelInputs = editTexts(panel);
                panelInputs.get(0).setText("");
                performButtonClick(panel, "Save note type");
                assertEquals("Kiku", activity.settings().modelName);
                panelInputs.get(0).setText("Custom");
                panelInputs.get(1).setText("");
                performButtonClick(panel, "Save note type");
                assertEquals("Kiku", activity.settings().modelName);
                performButtonClick(panel, "Use Kiku");
                performButtonClick(panel, "Save note type");
                assertEquals("Kiku", activity.settings().modelName);
            });
        }
    }

    @Test
    public void settingsValidationPanelsPersistStudyAheadLadderRetentionAndReminder() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                LinearLayout studyAhead = activity.studyAheadSettingsPanel();
                EditText minutes = editTexts(studyAhead).get(0);
                minutes.setText("later");
                performButtonClick(studyAhead, "Save study ahead");
                minutes.setText("2000");
                performButtonClick(studyAhead, "Save study ahead");
                minutes.setText("45");
                performButtonClick(studyAhead, "Save study ahead");
                assertEquals(45, activity.store.studyAheadMinutes());

                LinearLayout ladder = activity.ladderThresholdSettingsPanel();
                List<EditText> thresholdInputs = editTexts(ladder);
                thresholdInputs.get(0).setText("oops");
                thresholdInputs.get(1).setText("3");
                performButtonClick(ladder, "Save ladder thresholds");
                thresholdInputs.get(0).setText("11");
                thresholdInputs.get(1).setText("3");
                performButtonClick(ladder, "Save ladder thresholds");
                thresholdInputs.get(0).setText("3");
                thresholdInputs.get(1).setText("0");
                performButtonClick(ladder, "Save ladder thresholds");
                performButtonClick(ladder, "Use 3 and 3");
                performButtonClick(ladder, "Save ladder thresholds");
                Records.Settings updated = activity.settings();
                assertEquals(Records.DEFAULT_RECOGNITION_PROMOTION_PASSES, updated.recognitionPromotionPasses);
                assertEquals(Records.DEFAULT_WRITING_TRIGGER_MISS_DAYS, updated.writingTriggerMissDays);

                LinearLayout retention = activity.retentionSettingsPanel();
                SeekBar retentionSlider = seekBars(retention).get(0);
                retentionSlider.setProgress(10);
                touchSeekBar(retentionSlider);
                performButtonClick(retention, "95%");
                performButtonClick(retention, "Save retention");
                assertEquals(0.95, activity.store.schedulerParameters().targetRetention, 0.001);

                activity.store.saveReminderSettings(new LocalStore.ReminderSettings(true, 21, 0));
                LinearLayout reminder = activity.reminderSettingsPanel();
                Button timeButton = findButton(reminder, "Reminder time: 21:00");
                performButtonClick(reminder, "Morning 08:00");
                assertEquals("Reminder time: 08:00", timeButton.getText().toString());
                performButtonClick(reminder, "Turn off reminder");
                assertFalse(activity.store.reminderSettings().enabled);
                assertEquals(MainActivityBase.CORAL, activity.reminderStatusColor(new LocalStore.ReminderSettings(true, 21, 0), true));
                assertEquals(MainActivityBase.TEAL, activity.reminderStatusColor(new LocalStore.ReminderSettings(true, 21, 0), false));
                assertEquals(MainActivityBase.MUTED, activity.reminderStatusColor(new LocalStore.ReminderSettings(false, 21, 0), false));

                int[] selectedHour = {21};
                int[] selectedMinute = {0};
                Button timeButtonDirect = new Button(activity);
                activity.applyReminderTimeSelection(selectedHour, selectedMinute, timeButtonDirect, 6, 5);
                assertEquals(6, selectedHour[0]);
                assertEquals(5, selectedMinute[0]);
                assertEquals("Reminder time: 06:05", timeButtonDirect.getText().toString());
                Intent notificationIntent = activity.notificationSettingsIntent();
                assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, notificationIntent.getAction());
                assertEquals(activity.getPackageName(), notificationIntent.getStringExtra(Settings.EXTRA_APP_PACKAGE));
            });
        }
    }

    @Test
    public void automationPanelsToggleSyncUpdatesAndReminderActions() {
        MainActivity.setInstallPermissionForTests(false);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.store.saveAutoSyncSettings(new LocalStore.AutoSyncSettings(true, true, 6, 45, 1000L, 1000L, 2000L));
                LinearLayout syncOn = activity.autoSyncSettingsPanel();
                performButtonClick(syncOn, "Turn off daily sync");
                assertFalse(activity.store.autoSyncSettings().enabled);

                LinearLayout syncOff = activity.autoSyncSettingsPanel();
                performButtonClick(syncOff, "Turn on daily sync");
                assertTrue(activity.store.autoSyncSettings().enabled);

                activity.store.recordAutoUpdateResult(1234L, "Ready to install.", "v0.5.0", "kani.apk", "");
                LinearLayout missingPermission = activity.updateSettingsPanel();
                assertTrue(containsText(missingPermission, "Install permission: Missing"));
                assertTrue(findButton(missingPermission, "Set up app installs") != null);
                performButtonClick(missingPermission, "Open updater");
                assertHasText(activity, "GitHub updater");

                MainActivity.setInstallPermissionForTests(true);
                LinearLayout readyUpdate = activity.updateSettingsPanel();
                assertTrue(containsText(readyUpdate, "Install permission: Ready"));
                assertTrue(containsText(readyUpdate, "Verified APK ready: 0.5.0"));
                assertTrue(findButton(readyUpdate, "Install verified update") != null);

                performButtonClick(readyUpdate, "Turn off automatic updates");
                assertFalse(activity.store.autoUpdateStatus().enabled);
                LinearLayout updateOff = activity.updateSettingsPanel();
                performButtonClick(updateOff, "Turn on automatic updates");
                assertTrue(activity.store.autoUpdateStatus().enabled);

                activity.store.saveReminderSettings(new LocalStore.ReminderSettings(true, 22, 45));
                activity.saveReminderFromSelection(6, 15, false);
                LocalStore.ReminderSettings reminder = activity.store.reminderSettings();
                assertFalse(reminder.enabled);
                assertEquals(6, reminder.hour);
                assertEquals(15, reminder.minute);
                assertTrue(findButton(activity.reminderSettingsPanel(), "Enable reminder") != null);
            });
        } finally {
            MainActivity.setInstallPermissionForTests(null);
        }
    }

    @Test
    public void reminderSavingCoversPermissionRequestsAndBlockedNotifications() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                MainActivity.setRuntimeNotificationPermissionForTests(false);
                activity.saveReminderFromSelection(7, 45, true);
                assertTrue(activity.pendingReminderSettings.enabled);
                assertEquals(7, activity.pendingReminderSettings.hour);
                assertEquals(45, activity.pendingReminderSettings.minute);

                MainActivity.setRuntimeNotificationPermissionForTests(true);
                MainActivity.setNotificationsAllowedForTests(false);
                activity.saveReminderFromSelection(8, 15, true);
                LocalStore.ReminderSettings saved = activity.store.reminderSettings();
                assertTrue(saved.enabled);
                assertEquals(8, saved.hour);
                assertEquals(15, saved.minute);
                assertContainsText(activity.reminderSettingsPanel(), "Android notifications are off for Kani");

                activity.pendingReminderSettings = new LocalStore.ReminderSettings(true, 9, 30);
                activity.saveGrantedReminderPermission(activity.pendingReminderSettings);
                assertEquals(9, activity.store.reminderSettings().hour);
            });
        } finally {
            MainActivity.setRuntimeNotificationPermissionForTests(null);
            MainActivity.setNotificationsAllowedForTests(null);
        }
    }

    @Test
    public void homeAndDetailHelpersSummarizeQueueStatsAndTimelineState() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals("Never synced", activity.homeSyncValue(null));
                assertEquals("", activity.sentenceCase(""));
                assertEquals("", activity.sentenceCase(null));
                assertEquals("Synced today", activity.sentenceCase("synced today"));

                Records.AdaptiveLoadPlan waiting = new Records.AdaptiveLoadPlan(20, 0, 0, Collections.emptyList(), 0, false, "");
                Records.AdaptiveLoadPlan all = new Records.AdaptiveLoadPlan(100, 2, 2, Arrays.asList("裂", "語"), 0, true, "all");
                Records.AdaptiveLoadPlan focused = new Records.AdaptiveLoadPlan(20, 4, 1, Arrays.asList("裂", "語"), 0, false, "focus");
                assertEquals("Waiting", activity.focusHeadline(null));
                assertEquals("Waiting", activity.focusHeadline(waiting));
                assertEquals("All current", activity.focusHeadline(all));
                assertEquals("1 items left / 4", activity.focusHeadline(focused));

                StudyStatsStore.StudyStreak none = new StudyStatsStore.StudyStreak(0, 0, false, 0, 0L);
                StudyStatsStore.StudyStreak doneToday = new StudyStatsStore.StudyStreak(2, 5, true, 3, 1000L);
                StudyStatsStore.StudyStreak doneNoBest = new StudyStatsStore.StudyStreak(1, 0, true, 1, 1000L);
                assertEquals("No streak yet", activity.streakHeadline(none));
                assertEquals("2-day streak", activity.streakHeadline(doneToday));
                assertEquals("Not done today", activity.streakMetricBody(none));
                assertEquals("Best: 5 days", activity.streakMetricBody(doneToday));
                assertEquals("Done today", activity.streakMetricBody(doneNoBest));
                assertEquals("1 day", activity.streakDayCount(1));
                assertEquals("3 days", activity.streakDayCount(3));

                assertEquals("0 sec", activity.formatStudyTime(-500L));
                assertEquals("59 sec", activity.formatStudyTime(59_000L));
                assertEquals("1 min", activity.formatStudyTime(60_000L));
                assertEquals("1 min 5 sec", activity.formatStudyTime(65_000L));
                assertEquals("1 hr", activity.formatStudyTime(3_600_000L));
                assertEquals("1 hr 1 min", activity.formatStudyTime(3_660_000L));
                assertEquals("0.38", activity.formatWeakness(0.375));

                assertEquals(0, activity.stateRank("learning"));
                assertEquals(1, activity.stateRank("review"));
                assertEquals(2, activity.stateRank("new"));
                assertEquals(3, activity.stateRank("retired"));
                assertEquals(MainActivityBase.CORAL, activity.rowColor(studyItem("裂", Records.LadderRung.KANJI_MEANING, "review", 0L), 1000L));
                assertEquals(MainActivityBase.BLUE, activity.rowColor(studyItem("裂", Records.LadderRung.KANJI_MEANING, "learning", 2000L), 1000L));
                assertTrue(activity.rowColor(studyItem("裂", Records.LadderRung.KANJI_MEANING, "review", 2000L), 1000L) != MainActivityBase.CORAL);

                assertEquals("Needs focused kanji practice.", activity.queueCardBody(rowWithReason("裂", "", "", "", Collections.emptyList())));
                assertEquals(
                        "Shape mix-up made this a writing-practice target.",
                        activity.queueCardBody(rowWithReason("裂", "shape", "レツ", "similar-kanji miss", Collections.emptyList()))
                );
                assertEquals("custom evidence", activity.queueCardBody(rowWithReason("裂", "shape", "レツ", "custom evidence", Collections.emptyList())));

                assertEquals("write kanji", activity.recognitionStageLabel(studyItem("裂", Records.LadderRung.WRITE_KANJI, "review", 0L)));
                assertEquals("type meaning", activity.recognitionStageLabel(studyItem("裂", Records.LadderRung.TYPE_MEANING, "review", 0L)));
                assertEquals("similar kanji", activity.recognitionStageLabel(studyItem("裂", Records.LadderRung.SIMILAR_KANJI, "review", 0L)));
                assertEquals("font -> meaning", activity.recognitionStageLabel(studyItem("裂", Records.LadderRung.FONT_MEANING, "review", 0L)));
                assertEquals("word -> reading", activity.recognitionStageLabel(studyItem("裂", Records.LadderRung.WORD_READING, "review", 0L)));
                assertEquals("kanji -> meaning", activity.recognitionStageLabel(studyItem("裂", Records.LadderRung.KANJI_MEANING, "review", 0L)));

                Records.Example active = example("活動語", "カツドウゴ", "active", MainActivityBase.SOURCE_ACTIVE);
                Records.Example suspended = example("停止語", "テイシゴ", "suspended", MainActivityBase.SOURCE_SUSPENDED);
                assertEquals("From 活動語 · missed 停止語", activity.sourceEvidenceText(row("語", "language", "ゴ", Arrays.asList(active, suspended))));
                assertEquals("From 活動語", activity.sourceEvidenceText(row("語", "language", "ゴ", Collections.singletonList(active))));
                assertEquals("Missed 停止語", activity.sourceEvidenceText(row("語", "language", "ゴ", Collections.singletonList(suspended))));
                assertEquals("From your AnkiDroid sync", activity.sourceEvidenceText(row("語", "language", "ゴ", Collections.emptyList())));
                seedRows(activity, Collections.singletonList(row("空", "empty", "クウ", Collections.emptyList())));
                activity.renderFocusQueue();
                assertHasText(activity, MainActivityBase.EMPTY_ACTIVE_PRACTICE_TITLE);

                Records.KanjiInventoryItem inventory = new Records.KanjiInventoryItem("語", "language", "ゴ", "kanji:語", 2, 3, true, 1000L);
                Records.DashboardRow row = row("裂", "split", "レツ", Collections.emptyList());
                assertEquals("裂", activity.detailDisplayKanji("fallback", row, inventory));
                assertEquals("語", activity.detailDisplayKanji("fallback", null, inventory));
                assertEquals("fallback", activity.detailDisplayKanji("fallback", null, null));
                assertEquals("Historical recovery", activity.inventoryTitle(null));
                assertEquals("Historical recovery", activity.inventoryTitle(new Records.KanjiInventoryItem("語", "", "", "", 0, 0, false, 0L)));
                assertEquals("language", activity.inventoryTitle(inventory));

                Records.KanjiRecoveryTimeline activeTimeline = new Records.KanjiRecoveryTimeline(inventory, row, studyItem("裂", Records.LadderRung.KANJI_MEANING, "review", 0L), Collections.emptyList());
                Records.KanjiRecoveryTimeline restingTimeline = new Records.KanjiRecoveryTimeline(inventory, row, studyItem("裂", Records.LadderRung.KANJI_MEANING, "review", System.currentTimeMillis() + 60_000L), Collections.emptyList());
                Records.KanjiRecoveryTimeline retiredTimeline = new Records.KanjiRecoveryTimeline(inventory, null, studyItem("裂", Records.LadderRung.KANJI_MEANING, "retired", 0L), Collections.emptyList());
                Records.KanjiRecoveryTimeline noRowTimeline = new Records.KanjiRecoveryTimeline(inventory, null, null, Collections.emptyList());
                assertEquals("Active repair", activity.timelineStatusText(activeTimeline));
                assertEquals("Resting until review", activity.timelineStatusText(restingTimeline));
                assertEquals("Retired by Anki support", activity.timelineStatusText(retiredTimeline));
                assertEquals("Retired by Anki support", activity.timelineStatusText(noRowTimeline));
                assertEquals(MainActivityBase.TEAL, activity.timelineStatusColor(retiredTimeline));
                assertEquals(MainActivityBase.BLUE, activity.timelineStatusColor(restingTimeline));
                assertEquals(MainActivityBase.CORAL, activity.timelineStatusColor(activeTimeline));
                assertEquals(MainActivityBase.CORAL, activity.timelineEventColor("review_failed"));
                assertEquals(MainActivityBase.TEAL, activity.timelineEventColor("review_passed"));
                assertEquals(MainActivityBase.BLUE, activity.timelineEventColor("sync"));
                assertEquals("", activity.timelineSourceLine(event("", "")));
                assertEquals("Source: 活動語", activity.timelineSourceLine(event("活動語", "")));
                assertEquals("Source: 活動語  カツドウゴ", activity.timelineSourceLine(event("活動語", "カツドウゴ")));

                activity.content.removeAllViews();
                activity.addDetailIdentity(null, inventory, false);
                assertTrue(containsText(activity.content, "language"));
                assertTrue(containsText(activity.content, "ゴ"));

                activity.content.removeAllViews();
                activity.addDetailIdentity(null, new Records.KanjiInventoryItem("謎", "", "", "", 0, 0, false, 0L), false);
                assertTrue(containsText(activity.content, "Historical recovery"));
                assertFalse(containsText(activity.content, "ゴ"));

                LinearLayout inventoryReason = activity.detailReasonPanel(null, inventory);
                assertTrue(containsText(inventoryReason, "This kanji is no longer in the active Anki evidence set, but Kani kept its local recovery history."));
                assertTrue(containsText(inventoryReason, "Anki browser: kanji:語"));
                LinearLayout fallbackReason = activity.detailReasonPanel(null, null);
                assertTrue(containsText(fallbackReason, "This kanji is no longer in the active Anki evidence set, but Kani kept its local recovery history."));
                assertFalse(containsTextContaining(fallbackReason, "Anki browser:"));
                LinearLayout rowReason = activity.detailReasonPanel(row, inventory);
                assertTrue(containsText(rowReason, "reason text"));
                assertTrue(containsText(rowReason, "Anki browser: 裂"));
            });
        }
    }

    @Test
    public void homeNavigationActionButtonsRenderDestinationScreens() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                boolean[] metricClicked = {false};
                View metric = activity.metricCard(
                        R.drawable.ic_sync_24,
                        MainActivityBase.TEAL,
                        "Sync",
                        "Ready",
                        "Tap to sync",
                        () -> metricClicked[0] = true
                );
                metric.performClick();
                assertTrue(metricClicked[0]);

                boolean[] headerClicked = {false};
                View header = activity.homeSectionHeader("Focus queue", "View all", () -> headerClicked[0] = true);
                performClickableWithText(header, "View all >");
                assertTrue(headerClicked[0]);

                performClickableWithText(activity.homeActionRow(), "Browse Kanji");
                assertHasText(activity, "Browse Kanji");

                performClickableWithText(activity.homeActionRow(), "Recent mistakes");
                assertHasText(activity, "Recent mistakes");
                assertHasText(activity, "No recent mistakes yet");

                performClickableWithText(activity.homeActionRow(), "Stats");
                assertHasText(activity, "Stats");

                performClickableWithText(activity.homeActionRow(), "Settings");
                assertHasText(activity, "Settings cockpit");

                activity.fullWidthHomeButton().performClick();
                assertHasText(activity, "Kani");
            });
        }
    }

    @Test
    public void homeSyncResultRenderersCoverEmptyAndTerminalStates() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.renderFocusQueue();
                assertHasText(activity, "No kanji queued yet");
                activity.renderRecentMistakes();
                assertHasText(activity, "No recent mistakes yet");

                activity.renderSyncResult(syncResult(false, true, 0, 0, "Already syncing.", ""));
                assertHasText(activity, "Sync already running");
                assertHasText(activity, "Already syncing.");

                activity.renderSyncResult(syncResult(false, false, 0, 0, "Provider unavailable.", ""));
                assertHasText(activity, "Sync needs attention");
                assertHasText(activity, "Provider unavailable.");

                activity.renderSyncResult(syncResult(true, false, 0, 2, "Cleanup finished.", "Auto Pareto: 2 items today"));
                assertHasText(activity, "Sync complete");
                assertHasText(activity, "Cleanup finished.");

                LinearLayout summary = new LinearLayout(activity);
                activity.addOptionalSyncSummaryLines(summary, syncResult(true, false, 1, 2, "Done.", "Focus summary"));
                assertEquals(3, summary.getChildCount());
                assertEquals("fallback", activity.nonEmptyOr("", "fallback"));
                assertEquals("value", activity.nonEmptyOr("value", "fallback"));
            });
        }
    }

    @Test
    public void studyRenderAndProgressHelpersCoverTerminalStudyStates() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                Records.AdaptiveLoadPlan dueLater = new Records.AdaptiveLoadPlan(20, 3, 2, Arrays.asList("裂", "語"), 0, false, "Two left");
                Records.AdaptiveLoadPlan complete = new Records.AdaptiveLoadPlan(20, 3, 0, Arrays.asList("裂", "語"), 0, false, "Done");

                activity.renderEmptyStudyQueue();
                assertHasText(activity, "Nothing to study yet");

                activity.renderNoStudySession(dueLater);
                assertHasText(activity, "Nothing due now");

                activity.renderFocusDone(complete);
                assertHasText(activity, "Today's focus done");
                assertHasText(activity, "Today's focus: 0 items left / 3");

                activity.sessionProgressCompleted = 2;
                activity.sessionProgressMax = 3;
                activity.renderStudyRunDone(dueLater);
                assertHasText(activity, "Study now: 2 / 3");

                activity.renderStudyForKanji("謎");
                assertHasText(activity, "Kanji not available");

                assertFalse(activity.startStudyMoreNewCards(2));
                EditText requested = new EditText(activity);
                requested.setText("not a number");
                assertEquals(-1, activity.requestedStudyMoreNewCards(requested));
                requested.setText("0");
                assertEquals(-1, activity.requestedStudyMoreNewCards(requested));
                assertFalse(activity.applyStudyMoreNewCardsRequest(requested));
                requested.setText("3");
                assertEquals(3, activity.requestedStudyMoreNewCards(requested));
                long now = System.currentTimeMillis();
                Records.DashboardRow unavailable = row("余", "extra", "ヨ", Collections.emptyList());
                seedRows(activity, Collections.singletonList(unavailable));
                activity.store.saveStudyItem(studyItem("余", Records.LadderRung.KANJI_MEANING, "review", now));
                assertFalse(activity.startStudyMoreNewCards(2));

                seedRows(activity, Collections.singletonList(row("新", "new", "シン", Collections.emptyList())));
                EditText extraRequest = new EditText(activity);
                extraRequest.setText("3");
                assertTrue(activity.applyStudyMoreNewCardsRequest(extraRequest));
                assertEquals(1, activity.sessionProgressMax);
                assertTrue(activity.studyMoreNewCardKanji.contains("新"));

                activity.resetStudyRunProgress();
                assertEquals(0, activity.sessionProgressCompleted);
                assertEquals(0, activity.sessionProgressMax);
                assertFalse(activity.studyRunAtHardCap());
                activity.initializeSessionProgressTarget(dueLater);
                assertEquals(2, activity.sessionProgressMax);
                activity.sessionProgressCompleted = 2;
                assertTrue(activity.studyRunAtHardCap());
                activity.continueAllKanjiSession = true;
                assertFalse(activity.studyRunAtHardCap());
                activity.clearStudyModeOverrides();
                assertFalse(activity.continueAllKanjiSession);

                activity.resetStudyRunProgress();
                assertEquals("", activity.sessionTaskKey(null));
                Records.StudySession session = session("裂", BridgeScheduler.TASK_KANJI_MEANING, row("裂", "split", "レツ", Collections.emptyList()));
                assertTrue(activity.sessionTaskKey(session).contains("session:kanji_meaning:裂"));
                activity.registerStudyTaskShown("");
                activity.registerStudyTaskShown("task:one");
                assertEquals(1, activity.sessionProgressMax);
                activity.markStudyTaskCompleted("");
                activity.markStudyTaskCompleted("task:one");
                activity.markStudyTaskCompleted("task:one");
                assertEquals(1, activity.sessionProgressCompleted);

                activity.activeSession = null;
                activity.markStudyRunPassed("語");
                assertEquals(2, activity.sessionProgressCompleted);
                activity.activeSession = session;
                activity.markStudyRunPassed("");
                assertEquals(3, activity.sessionProgressCompleted);

                activity.activeStudyTask = null;
                activity.startActiveStudyTask("", "裂", BridgeScheduler.TASK_KANJI_MEANING, 1000L);
                assertNull(activity.activeStudyTask);
                activity.startActiveStudyTask("task:active", "裂", BridgeScheduler.TASK_KANJI_MEANING, 1000L);
                assertTrue(activity.activeStudyTask != null);
                activity.startActiveStudyTask("task:active", "裂", BridgeScheduler.TASK_KANJI_MEANING, 1000L);
                activity.pauseActiveStudyTask();
                activity.resumeActiveStudyTask();
                activity.completeActiveStudyTask("wrong", "missed", 2000L);
                assertTrue(activity.activeStudyTask != null);
                activity.completeActiveStudyTask("task:active", "passed", 2000L);
                assertNull(activity.activeStudyTask);
                activity.abandonActiveStudyTask();

                View grid = activity.similarKanjiGrid(Arrays.asList("裂", "列", "烈"), "裂");
                assertEquals(2, ((ViewGroup) grid).getChildCount());
                Records.StudyItem targeted = activity.newTargetedStudyItem("謎", 1234L);
                assertEquals("謎", targeted.kanji);
                assertEquals("new", targeted.state);
                assertEquals(1234L, targeted.dueAtMillis);
                Records.StudyItem existingTarget = studyItem("裂", Records.LadderRung.KANJI_MEANING, "review", now);
                assertTrue(existingTarget == activity.studyItemForTargetedKanji(Collections.singletonList(existingTarget), "裂", now));
                assertEquals("new", activity.studyItemForTargetedKanji(Collections.emptyList(), "謎", 1234L).state);
                assertEquals(activity.dp(300), activity.studyPadHeightForScreenDp(699));
                assertEquals(activity.dp(340), activity.studyPadHeightForScreenDp(700));
                assertEquals(activity.dp(390), activity.studyPadHeightForScreenDp(820));

                seedRows(activity, Arrays.asList(
                        row("裂", "split", "レツ", Collections.emptyList()),
                        row("列", "row", "レツ", Collections.emptyList()),
                        row("烈", "ardent", "レツ", Collections.emptyList()),
                        row("劣", "inferior", "レツ", Collections.emptyList()),
                        row("例", "example", "レイ", Collections.emptyList()),
                        row("戻", "return", "レイ", Collections.emptyList())
                ));
                activity.store.rebuildSimilarKanjiPairs(similarIndex(
                        "裂\t列\n裂\t烈\n裂\t劣\n裂\t例\n裂\t戻\n"
                ), now);
                List<String> choices = activity.buildSimilarKanjiChoices("裂");
                assertEquals(4, choices.size());
                assertTrue(choices.contains("裂"));
            });
        }
    }

    @Test
    public void studyDoneActionsStudyMoreAndFallbackPanelsExerciseRealUiBranches() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                seedRows(activity, Arrays.asList(
                        row("裂", "split", "レツ", Collections.emptyList()),
                        row("語", "language", "ゴ", Collections.emptyList())
                ));
                Records.AdaptiveLoadPlan complete = new Records.AdaptiveLoadPlan(20, 2, 0, Arrays.asList("裂", "語"), 0, false, "Done");

                activity.renderFocusDone(complete);
                assertTrue(findButton(activity.content, "Study more new cards") != null);
                performButtonClick(activity.content, MainActivityBase.LABEL_CONTINUE_ALL_KANJI);
                assertTrue(activity.continueAllKanjiSession);
                activity.renderFocusDone(complete);
                performButtonClick(activity.content, MainActivityBase.LABEL_BACK_HOME);
                assertFalse(activity.continueAllKanjiSession);
                assertHasText(activity, "Kani");

                activity.sessionProgressCompleted = 1;
                activity.sessionProgressMax = 2;
                activity.renderStudyRunDone(complete);
                performButtonClick(activity.content, MainActivityBase.LABEL_CONTINUE_ALL_KANJI);
                assertTrue(activity.continueAllKanjiSession);
                activity.renderStudyRunDone(null);
                performButtonClick(activity.content, MainActivityBase.LABEL_BACK_HOME);
                assertFalse(activity.continueAllKanjiSession);

                int available = activity.availableStudyMoreNewCards();
                if (available > 0) {
                    assertTrue(activity.startStudyMoreNewCards(5));
                    assertTrue(activity.sessionProgressMax <= 5);
                    assertFalse(activity.studyMoreNewCardKanji.isEmpty());
                }

                Records.StudySession promptOnly = new Records.StudySession(
                        studyItem("?", Records.LadderRung.KANJI_MEANING, "review", 0L),
                        null,
                        "answer-token",
                        BridgeScheduler.TASK_KANJI_MEANING,
                        false,
                        "Prompt fallback"
                );
                assertTrue(containsText(activity.flashcardAnswerPanel(promptOnly), "Prompt fallback"));
                assertEquals("split", activity.collectionMeaningForSession(session("裂", BridgeScheduler.TASK_KANJI_MEANING, row("裂", "split", "レツ", Collections.emptyList()))));
                assertEquals(Typeface.SERIF, activity.fontResource(0, Typeface.SERIF));
            });
        }
    }

    @Test
    public void studyRenderingBranchesCoverFallbacksAndWritingActions() {
        FakeWritingRecognizer recognizer = new FakeWritingRecognizer(
                CompletableFuture.completedFuture(new WritingRecognizer.ModelStatus("ja", "ja-JP", true, "ready")),
                failedFuture(new RuntimeException("network unavailable")),
                CompletableFuture.completedFuture(new WritingRecognizer.RecognitionResult(Collections.singletonList(
                        new WritingRecognizer.Candidate("裂", 0.9f)
                )))
        );
        MainActivity.setWritingRecognizerForTests(recognizer);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                Records.DashboardRow row = row("裂", "split", "レツ", Collections.emptyList());
                assertNull(activity.firstExample(null));
                assertNull(activity.wordReadingExample(null));

                Records.StudySession promptOnly = new Records.StudySession(
                        studyItem("?", Records.LadderRung.WRITE_KANJI, "review", 0L),
                        null,
                        "prompt-token",
                        BridgeScheduler.TASK_WRITE_KANJI,
                        true,
                        "Prompt only"
                );
                assertTrue(containsText(activity.learningPanel(promptOnly), "Prompt only"));
                assertTrue(containsText(activity.heroKanjiPanel(session("裂", BridgeScheduler.TASK_FONT_MEANING, row)), "裂"));
                assertTrue(activity.randomFontVariantTypeface() != null);

                activity.renderSimilarKanjiSession(session("裂", BridgeScheduler.TASK_SIMILAR_KANJI, row));
                assertTrue(activity.flashcardCard != null);
                assertTrue(activity.flashcardGestureArea == activity.flashcardCard);
                assertFalse(activity.flashcardAnswerRevealed);

                Records.StudySession recall = session("裂", "blind_writing", row);
                activity.activeSession = recall;
                activity.renderWritingSession(recall);
                assertHasText(activity, "Prompt: Split, rend");
                performButtonClick(activity.studyActionBar, "Erase");

                activity.activeSession = promptOnly;
                activity.renderWritingSession(promptOnly);
                assertHasText(activity, "Prompt only");

                activity.activeSession = null;
                activity.studyActionBar = null;
                activity.buildStudyActionBar();
                activity.checkWriting();
                activity.submitReview(MainActivityBase.RATING_GOOD, false);
                activity.showWritingHint();
                activity.startCleanerRetry();
                activity.replayWritingAnalysis();

                activity.activeSession = promptOnly;
                activity.currentHintState = HintState.fromWritingLevel(2);
                activity.studyStatus = new TextView(activity);
                activity.downloadModelButton = new Button(activity);
                activity.downloadWritingModel();
            });
            scenario.onActivity(activity -> assertTrue(activity.studyStatus.getText().toString().contains("download failed")));
        } finally {
            MainActivity.setWritingRecognizerForTests(null);
        }
    }

    @Test
    public void writingUnavailableAndAsyncTokenGuardsLeaveVisibleStateConsistent() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                Records.DashboardRow row = row("裂", "split", "レツ", Collections.emptyList());
                Records.StudySession writing = session("裂", BridgeScheduler.TASK_WRITE_KANJI, row);
                activity.activeSession = writing;
                activity.currentHintState = HintState.fromWritingLevel(1);
                activity.studyStatus = new TextView(activity);
                activity.resultStatus = new TextView(activity);
                activity.checkWritingButton = new Button(activity);
                activity.downloadModelButton = new Button(activity);
                activity.nextAfterPassButton = new Button(activity);
                activity.manualOverrideButton = new Button(activity);
                activity.practiceWithGuideButton = new Button(activity);
                activity.replayButton = new Button(activity);
                activity.hintButton = new Button(activity);
                activity.studyAnswerPanel = new LinearLayout(activity);
                activity.drawingPad = new DrawingPadView(activity);
                activity.drawingPad.setTarget("裂");
                addInk(activity.drawingPad);
                activity.checkingWriting = true;
                activity.checkWriting();
                assertTrue(activity.checkingWriting);
                activity.checkingWriting = false;

                FakeWritingRecognizer staleRecognizer = new FakeWritingRecognizer(
                        CompletableFuture.completedFuture(new WritingRecognizer.ModelStatus("ja", "ja-JP", false, "missing")),
                        CompletableFuture.completedFuture(new WritingRecognizer.ModelStatus("ja", "ja-JP", true, "ready")),
                        CompletableFuture.completedFuture(new WritingRecognizer.RecognitionResult(Collections.singletonList(
                                new WritingRecognizer.Candidate("裂", 0.9f)
                        )))
                );
                MainActivity.setWritingRecognizerForTests(staleRecognizer);
                activity.activeSession = sessionWithToken("裂", BridgeScheduler.TASK_WRITE_KANJI, row, "current-token");
                activity.recognizeWriting(staleRecognizer, capturedWriting(), sample(), guide("裂"), "裂", "stale-token");
                activity.downloadWritingModel();
                activity.activeSession = sessionWithToken("裂", BridgeScheduler.TASK_WRITE_KANJI, row, "changed-token");
            });
            scenario.onActivity(activity -> {
                assertNull(activity.activeAnalysis);
                FakeWritingRecognizer errorRecognizer = new FakeWritingRecognizer(
                        CompletableFuture.completedFuture(new WritingRecognizer.ModelStatus("ja", "ja-JP", true, "ready")),
                        CompletableFuture.completedFuture(new WritingRecognizer.ModelStatus("ja", "ja-JP", true, "ready")),
                        failedFuture(new RuntimeException("recognition failed"))
                );
                Records.DashboardRow row = row("裂", "split", "レツ", Collections.emptyList());
                activity.activeSession = sessionWithToken("裂", BridgeScheduler.TASK_WRITE_KANJI, row, "error-token");
                activity.recognizeWriting(errorRecognizer, capturedWriting(), sample(), guide("裂"), "裂", "error-token");
            });
            scenario.onActivity(activity -> assertEquals(WritingAnalysis.Status.RECOGNITION_ERROR, activity.activeAnalysis.status));
        } finally {
            MainActivity.setWritingRecognizerForTests(null);
        }
    }

    @Test
    public void writingRecognitionUnavailableAndInvalidCaptureBranchesShowActionableState() {
        FakeWritingRecognizer staleStatusRecognizer = new FakeWritingRecognizer(
                CompletableFuture.completedFuture(new WritingRecognizer.ModelStatus("ja", "ja-JP", false, "missing")),
                CompletableFuture.completedFuture(new WritingRecognizer.ModelStatus("ja", "ja-JP", true, "ready")),
                CompletableFuture.completedFuture(new WritingRecognizer.RecognitionResult(Collections.singletonList(
                        new WritingRecognizer.Candidate("裂", 0.9f)
                )))
        );
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                Records.DashboardRow row = row("裂", "split", "レツ", Collections.emptyList());
                prepareWritingUi(activity, sessionWithToken("裂", BridgeScheduler.TASK_WRITE_KANJI, row, "invalid-capture"));
                MainActivity.setWritingRecognizerForTests(staleStatusRecognizer);
                activity.drawingPad = new DrawingPadView(activity);
                activity.drawingPad.setTarget("裂");
                addInk(activity.drawingPad);
                activity.checkWriting();
                assertEquals(WritingAnalysis.Status.NO_INK, activity.activeAnalysis.status);

                MainActivity.setWritingRecognizerForTests(null);
                MainActivity.setWritingRecognizerFactoryForTests(executor -> null);
                prepareWritingUi(activity, sessionWithToken("裂", BridgeScheduler.TASK_WRITE_KANJI, row, "null-recognizer"));
                layoutPad(activity.drawingPad);
                addInk(activity.drawingPad);
                activity.checkWriting();
                assertEquals(WritingAnalysis.Status.MODEL_UNAVAILABLE, activity.activeAnalysis.status);

                activity.activeAnalysis = null;
                activity.refreshWritingModelStatus();
                assertTrue(activity.studyStatus.getText().toString().contains("Automatic handwriting checks are unavailable"));
                activity.downloadWritingModel();
                assertTrue(activity.studyStatus.getText().toString().contains("unavailable on this device"));

                MainActivity.setWritingRecognizerFactoryForTests(executor -> {
                    throw new RuntimeException("ml kit unavailable");
                });
                activity.writingRecognizer = null;
                assertNull(activity.writingRecognizer());

                MainActivity.setWritingRecognizerFactoryForTests(null);
                MainActivity.setWritingRecognizerForTests(staleStatusRecognizer);
                prepareWritingUi(activity, sessionWithToken("裂", BridgeScheduler.TASK_WRITE_KANJI, row, "old-token"));
                layoutPad(activity.drawingPad);
                addInk(activity.drawingPad);
                activity.checkWriting();
                activity.activeSession = sessionWithToken("裂", BridgeScheduler.TASK_WRITE_KANJI, row, "new-token");
            });
            scenario.onActivity(activity -> assertNull(activity.activeAnalysis));
        } finally {
            MainActivity.setWritingRecognizerForTests(null);
            MainActivity.setWritingRecognizerFactoryForTests(null);
        }
    }

    @Test
    public void schedulerTuningPersistsWhenRecentReviewsJustifyAnAdjustment() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                long now = System.currentTimeMillis();
                for (int i = 0; i < 22; i++) {
                    activity.store.saveReview(
                            new Records.ReviewRequest("調" + i, "tune-token-" + i, MainActivityBase.RATING_GOOD, false, false, false, 0),
                            MainActivityBase.RATING_GOOD,
                            now - (i * 1000L)
                    );
                }
                Records.SchedulerParameters before = Records.SchedulerParameters.defaults();

                activity.tuneSchedulerIfNeeded(before, now);

                Records.SchedulerParameters tuned = activity.store.schedulerParameters();
                assertEquals(now, tuned.lastAdjustedAtMillis);
                assertEquals(22, tuned.lastAdjustmentReviewCount);
                assertTrue(tuned.goodMultiplier > before.goodMultiplier);
            });
        }
    }

    @Test
    public void flashcardAndWritingUiStateHelpersCoverInteractiveBranches() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                Records.DashboardRow row = row("裂", "split", "レツ", Collections.emptyList());
                Records.StudySession flashcard = session("裂", BridgeScheduler.TASK_KANJI_MEANING, row);
                Records.StudySession writing = session("裂", BridgeScheduler.TASK_WRITE_KANJI, row);
                activity.activeSession = flashcard;

                assertTrue(activity.isActiveToken("tok"));
                assertFalse(activity.isActiveToken("missing"));
                assertEquals("", activity.candidateText(null));
                assertEquals(
                        "裂, 列, 烈",
                        activity.candidateText(Arrays.asList(
                                new RecognitionCandidate("裂", 0.9f),
                                new RecognitionCandidate("列", 0.5f),
                                new RecognitionCandidate("烈", 0.4f),
                                new RecognitionCandidate("劣", 0.3f)
                        ))
                );
                assertTrue(activity.candidates(new WritingRecognizer.RecognitionResult(Arrays.asList(
                        new WritingRecognizer.Candidate("裂", 0.9f),
                        new WritingRecognizer.Candidate("列", null)
                ))).size() == 2);
                assertTrue(activity.candidates(null).isEmpty());

                Records.StudyItem item = studyItem("裂", Records.LadderRung.KANJI_MEANING, "review", 0L);
                StudyStatsStore.StudyStreak streak = new StudyStatsStore.StudyStreak(2, 2, true, 1, 1000L);
                assertEquals("Already saved.", activity.reviewToast(new Records.ReviewResult(item, "duplicate", true, "dup"), streak));
                assertTrue(activity.reviewToast(new Records.ReviewResult(item, MainActivityBase.RATING_AGAIN, false, "again"), streak).contains("2-day streak"));
                assertEquals("Saved.", activity.reviewToast(new Records.ReviewResult(item, MainActivityBase.RATING_GOOD, false, "good"), null));

                activity.studyActionBar = null;
                activity.buildFlashcardActionBar(false);
                activity.studyActionBar = new LinearLayout(activity);
                activity.buildFlashcardActionBar(false);
                assertEquals(2, activity.studyActionBar.getChildCount());
                activity.buildFlashcardActionBar(true);
                assertEquals(2, activity.studyActionBar.getChildCount());
                activity.flashcardAnswerRevealed = true;
                activity.revealFlashcardAnswer();
                activity.flashcardCard = null;
                activity.expandFlashcardForAnswer();

                assertFalse(activity.handleFlashcardGesture(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 1f, 1f, 0)));
                activity.activeSession = writing;
                assertEquals(MainActivityBase.LABEL_PRACTICE, activity.studyModeLabel(writing));
                assertFalse(activity.handleFlashcardGesture(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 1f, 1f, 0)));

                activity.activeSession = writing;
                activity.currentHintState = HintState.fromWritingLevel(3);
                activity.studyStatus = new TextView(activity);
                activity.resultStatus = new TextView(activity);
                activity.checkWritingButton = new Button(activity);
                activity.downloadModelButton = new Button(activity);
                activity.nextAfterPassButton = new Button(activity);
                activity.manualOverrideButton = new Button(activity);
                activity.practiceWithGuideButton = new Button(activity);
                activity.replayButton = new Button(activity);
                activity.hintButton = new Button(activity);
                activity.studyAnswerPanel = new LinearLayout(activity);

                activity.checkingWriting = true;
                assertEquals("Checking...", activity.checkWritingButtonText(false));
                activity.checkingWriting = false;
                assertEquals("Try cleaner", activity.checkWritingButtonText(true));
                activity.updateCheckWritingButton(false, true);
                assertEquals("Try cleaner", activity.checkWritingButton.getText().toString());

                activity.writingModelStatusKnown = true;
                activity.writingModelDownloaded = true;
                activity.updateDownloadModelButton();
                assertEquals(View.GONE, activity.downloadModelButton.getVisibility());
                activity.updateNextAfterPassButton(true);
                assertEquals(View.VISIBLE, activity.nextAfterPassButton.getVisibility());
                assertEquals(MainActivityBase.LABEL_PASS, activity.nextAfterPassButton.getText().toString());

                StrokeOrderEvaluator.StrokeOrderResult order = StrokeOrderEvaluator
                        .evaluate(guide("裂"), sample())
                        .withDiagnosis(StrokeDiagnosis.builder().add(StrokeDiagnosis.Label.WRONG_ORDER, 1).build());
                WritingAnalysis wrong = analysis(WritingAnalysis.Status.WRONG, false, order);
                activity.activeAnalysis = wrong;
                activity.updateFallbackActionButtons(true, false, guide("裂"));
                assertEquals(View.VISIBLE, activity.manualOverrideButton.getVisibility());
                assertEquals(View.VISIBLE, activity.practiceWithGuideButton.getVisibility());
                activity.showModelUnavailable("checker unavailable");
                assertTrue(activity.resultStatus.getText().toString().contains("checker unavailable"));

                activity.setWritingModelStatusMessage(null, new RuntimeException("offline"));
                assertTrue(activity.studyStatus.getText().toString().contains("Unable to read"));
                activity.setWritingModelStatusMessage(new WritingRecognizer.ModelStatus("ja", "ja-JP", false, "missing"), null);
                assertTrue(activity.studyStatus.getText().toString().contains("Download the handwriting checker"));
                activity.setWritingModelStatusMessage(new WritingRecognizer.ModelStatus("ja", "ja-JP", true, "ready"), null);
                assertTrue(activity.studyStatus.getText().toString().contains("Handwriting checker ready"));

                activity.activeAnalysis = null;
                assertTrue(activity.showNoInkWhenNeeded());
                assertTrue(activity.activeAnalysis.status == WritingAnalysis.Status.NO_INK);
                assertFalse(activity.isTeachingTask(null));
                assertTrue(activity.isTeachingTask(session("裂", "context_writing", row)));
                assertTrue(activity.isTeachingTask(session("裂", "guided_writing", row)));
                assertTrue(activity.isTeachingTask(session("裂", MainActivityBase.TASK_TARGETED_WRITING, row)));
                assertFalse(activity.isTeachingTask(new Records.StudySession(
                        studyItem("裂", Records.LadderRung.WRITE_KANJI, "review", 0L).copyBuilder().learningStep(3).build(),
                        row,
                        "tok-target-done",
                        MainActivityBase.TASK_TARGETED_WRITING,
                        true,
                        "split"
                )));
                assertEquals(HintLevel.OUTLINE, activity.initialHintState(session("裂", MainActivityBase.TASK_TARGETED_WRITING, row)).level());
                assertEquals(HintLevel.OUTLINE, activity.initialHintState(new Records.StudySession(
                        studyItem("裂", Records.LadderRung.WRITE_KANJI, "review", 0L).copyBuilder().totalReviews(0).writingLevel(3).build(),
                        row,
                        "tok-new",
                        BridgeScheduler.TASK_WRITE_KANJI,
                        true,
                        "split"
                )).level());
                assertEquals(HintLevel.BLIND, activity.initialHintState(new Records.StudySession(
                        studyItem("裂", Records.LadderRung.WRITE_KANJI, "review", 0L).copyBuilder().learningStep(2).writingLevel(3).build(),
                        row,
                        "tok-mature",
                        BridgeScheduler.TASK_WRITE_KANJI,
                        true,
                        "split"
                )).level());
                activity.activeSession = null;
                assertFalse(activity.canRevealMoreHelp());
                activity.activeSession = writing;
                activity.currentHintState = HintState.fromWritingLevel(0);
                assertFalse(activity.canRevealMoreHelp());
                activity.currentHintState = HintState.fromWritingLevel(1);
                assertTrue(activity.canRevealMoreHelp());
                activity.activeSession = session("裂", "blind_writing", row);
                activity.currentPracticeLevel = 1;
                assertFalse(activity.shouldShowLearningPanel(null));
                activity.activeSession = session("裂", "context_writing", row);
                assertTrue(activity.shouldShowLearningPanel(null));
                activity.currentPracticeLevel = 3;
                assertFalse(activity.shouldShowLearningPanel(null));
                assertTrue(activity.shouldShowLearningPanel(analysis(WritingAnalysis.Status.PASS, true, order)));
                assertFalse(activity.shouldIncreaseSupportAfterAnalysis(null));
                assertFalse(activity.canManualOverride(null));
                assertFalse(activity.canPracticeAfterAnalysis(null));
                assertFalse(activity.canSubmitAnalysis(null));
            });
        }
    }

    @Test
    public void flashcardGestureTrackingCoversTypingBoundsCancelAndOutsideRelease() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                Records.DashboardRow row = row("裂", "split", "レツ", Collections.emptyList());
                activity.activeSession = session("裂", BridgeScheduler.TASK_TYPE_MEANING, row);
                LinearLayout area = new LinearLayout(activity);
                EditText input = new EditText(activity);
                activity.content.addView(area, new LinearLayout.LayoutParams(300, 300));
                activity.content.addView(input, new LinearLayout.LayoutParams(180, 90));
                area.layout(0, 0, 300, 300);
                input.layout(20, 20, 180, 90);
                activity.flashcardGestureArea = area;
                activity.typingAnswerInput = input;

                assertFalse(activity.handleFlashcardGesture(motion(MotionEvent.ACTION_DOWN, 40f, 40f)));
                assertFalse(activity.flashcardTouchTracking);

                activity.typingAnswerInput = null;
                activity.flashcardTouchTracking = false;
                assertFalse(activity.handleFlashcardGesture(motion(MotionEvent.ACTION_UP, 40f, 40f)));
                activity.flashcardTouchTracking = true;
                assertFalse(activity.handleFlashcardGesture(motion(MotionEvent.ACTION_CANCEL, 40f, 40f)));
                assertFalse(activity.flashcardTouchTracking);

                activity.flashcardTouchTracking = true;
                assertFalse(activity.handleFlashcardGesture(motion(MotionEvent.ACTION_UP, 400f, 400f)));

                area.setVisibility(View.GONE);
                assertFalse(activity.isTouchInsideView(area, motion(MotionEvent.ACTION_DOWN, 40f, 40f)));
                assertFalse(activity.isTouchInsideView(new View(activity), motion(MotionEvent.ACTION_DOWN, 40f, 40f)));
                activity.flashcardAnswerRevealed = true;
                activity.flashcardTouchStartX = 100f;
                activity.flashcardTouchStartY = 100f;
                assertFalse(activity.handleFlashcardRelease(motion(MotionEvent.ACTION_UP, 102f, 102f)));
            });
        }
    }

    @Test
    public void flashcardButtonsAndGesturesPersistPassFailOnlyAfterReveal() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                Records.DashboardRow row = row("裂", "split", "レツ", Collections.emptyList());
                Records.StudySession failSession = sessionWithToken("裂", BridgeScheduler.TASK_KANJI_MEANING, row, "fail-token");
                activity.activeSession = failSession;
                activity.activeStudyPlan = new Records.AdaptiveLoadPlan(20, 1, 1, Collections.singletonList("裂"), 0, false, "One left");
                activity.startActiveStudyTask(activity.sessionTaskKey(failSession), "裂", failSession.taskType, System.currentTimeMillis());
                activity.renderFlashcardSession(failSession);
                performButtonClick(activity.studyActionBar, "Reveal");
                assertTrue(activity.flashcardAnswerRevealed);
                assertEquals(View.VISIBLE, activity.studyAnswerPanel.getVisibility());
                performButtonClick(activity.studyActionBar, "Fail");
                Records.ReviewStats failStats = activity.store.reviewStatsSince(0L);
                assertEquals(1, failStats.total);
                assertEquals(1, failStats.again);

                Records.StudySession passSession = sessionWithToken("語", BridgeScheduler.TASK_KANJI_MEANING, row("語", "language", "ゴ", Collections.emptyList()), "pass-token");
                activity.activeSession = passSession;
                activity.startActiveStudyTask(activity.sessionTaskKey(passSession), "語", passSession.taskType, System.currentTimeMillis());
                activity.renderFlashcardSession(passSession);
                performButtonClick(activity.studyActionBar, "Reveal");
                performButtonClick(activity.studyActionBar, MainActivityBase.LABEL_PASS);
                Records.ReviewStats passStats = activity.store.reviewStatsSince(0L);
                assertEquals(2, passStats.total);
                assertEquals(1, passStats.good);

                Records.StudySession gestureSession = sessionWithToken("提", BridgeScheduler.TASK_KANJI_MEANING, row("提", "carry", "テイ", Collections.emptyList()), "gesture-token");
                activity.activeSession = gestureSession;
                activity.studyActionBar = new LinearLayout(activity);
                activity.studyAnswerPanel = new LinearLayout(activity);
                activity.flashcardHeroPanel = new LinearLayout(activity);
                activity.flashcardCard = new LinearLayout(activity);
                activity.flashcardCard.setLayoutParams(new LinearLayout.LayoutParams(-1, 0, 1));
                activity.flashcardAnswerRevealed = false;
                activity.flashcardTouchStartX = 100f;
                activity.flashcardTouchStartY = 100f;
                assertFalse(activity.handleFlashcardRelease(motion(MotionEvent.ACTION_UP, 260f, 100f)));
                assertFalse(activity.flashcardAnswerRevealed);

                activity.flashcardTouchStartX = 100f;
                activity.flashcardTouchStartY = 100f;
                assertTrue(activity.handleFlashcardRelease(motion(MotionEvent.ACTION_UP, 102f, 102f)));
                assertTrue(activity.flashcardAnswerRevealed);

                activity.activeSession = gestureSession;
                activity.startActiveStudyTask(activity.sessionTaskKey(gestureSession), "提", gestureSession.taskType, System.currentTimeMillis());
                activity.flashcardTouchStartX = 380f;
                activity.flashcardTouchStartY = 100f;
                assertTrue(activity.handleFlashcardRelease(motion(MotionEvent.ACTION_UP, 20f, 100f)));
                Records.ReviewStats gestureStats = activity.store.reviewStatsSince(0L);
                assertEquals(3, gestureStats.total);
                assertEquals(2, gestureStats.again);
            });
        }
    }

    @Test
    public void homeBrowseDetailStatsAndSyncControlsCoverNonEmptyBranches() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                Records.DashboardRow activeRow = row("裂", "split", "レツ", Collections.singletonList(example("裂語", "レツゴ", "split word", MainActivityBase.SOURCE_ACTIVE)));
                View passiveMetric = activity.metricCard(R.drawable.ic_target_24, MainActivityBase.CORAL, "Focus", "Waiting", null, null);
                assertFalse(passiveMetric.isClickable());
                assertFalse(containsText(activity.homeSectionHeader("Focus queue", null, null), "Focus queue >"));
                assertTrue(containsText(activity.browseKanjiRow(new Records.KanjiInventoryItem("謎", "", "", "", 0, 1, false, 0L)), "Meaning not stored yet"));
                Records.StudyItem relearning = studyItem("裂", Records.LadderRung.KANJI_MEANING, "review", 0L)
                        .copyBuilder()
                        .phase(Records.SchedulerPhase.RELEARNING)
                        .build();
                assertTrue(containsText(activity.queueRowView(new MainActivityBase.QueueEntry(activeRow, relearning), 1000L), "relearning"));
                Records.StudyItem learning = studyItem("裂", Records.LadderRung.KANJI_MEANING, "review", 0L)
                        .copyBuilder()
                        .phase(Records.SchedulerPhase.NEW_LEARNING)
                        .totalReviews(1)
                        .build();
                assertTrue(containsText(activity.queueRowView(new MainActivityBase.QueueEntry(activeRow, learning), 1000L), "learning"));
                seedRows(activity, Collections.singletonList(activeRow));
                activity.renderStudyForKanji("裂");
                assertHasText(activity, "Name this kanji");
                activity.store.setKanjiLocallySuspended("裂", true, 1000L);

                activity.renderBrowseKanji("裂");
                assertHasText(activity, "SUSPENDED");
                performClickableWithText(activity.content, "split");
                assertHasText(activity, "Back to Browse Kanji");
                assertHasText(activity, "Local inventory");
                performButtonClick(activity.content, "Unsuspend locally");
                assertFalse(activity.store.isKanjiLocallySuspended("裂"));

                activity.renderDetail("missing");
                assertHasText(activity, "Kanji not found");

                activity.renderRecentMistakes();
                assertHasText(activity, "No recent mistakes yet");
                activity.store.saveReview(new Records.ReviewRequest("裂", "miss-token", "again", false, false, false, 0), "again", 2000L);
                activity.renderRecentMistakes();
                assertContainsText(activity.content, "Rated again");

                View emptyStatus = activity.timelineStatusCard(new Records.KanjiRecoveryTimeline(null, null, null, Collections.emptyList()));
                assertTrue(containsText(emptyStatus, "No active Anki evidence in the latest local sync."));

                LinearLayout noEvidence = activity.statsVerdictPanel(StudyStatsStore.KaniOutcomeStats.empty());
                assertTrue(containsText(noEvidence, "Kani is not currently working for you"));
                StudyStatsStore.LadderHealthMetric ladderOnly = new StudyStatsStore.LadderHealthMetric(
                        Collections.singletonMap(Records.LadderRung.KANJI_MEANING, 1),
                        1,
                        3,
                        0,
                        0,
                        0
                );
                StudyStatsStore.KaniOutcomeStats ladderStats = new StudyStatsStore.KaniOutcomeStats(
                        StudyStatsStore.WeakKanjiImprovedMetric.empty(),
                        StudyStatsStore.MatureSupportGainedMetric.empty(),
                        ladderOnly
                );
                assertTrue(activity.statsVerdictBody(null, false, false).contains("No Kani evidence"));
                assertTrue(activity.statsVerdictBody(ladderStats, false, true).contains("Kani is tracking"));
                assertTrue(activity.ladderHealthBody(ladderOnly).contains("Threshold: 3"));
                assertTrue(containsText(activity.statsVerdictPanel(ladderStats), "Kani is not currently working for you"));
                StudyStatsStore.LadderHealthMetric busyLadder = new StudyStatsStore.LadderHealthMetric(
                        Collections.singletonMap(Records.LadderRung.KANJI_MEANING, 4),
                        4,
                        3,
                        1,
                        2,
                        1
                );
                StudyStatsStore.KaniOutcomeStats workingStats = new StudyStatsStore.KaniOutcomeStats(
                        new StudyStatsStore.WeakKanjiImprovedMetric(
                                2,
                                0.7,
                                0.2,
                                Arrays.asList(
                                        new StudyStatsStore.KanjiImprovement("裂", 0.7, 0.2),
                                        new StudyStatsStore.KanjiImprovement("語", 0.5, 0.1),
                                        new StudyStatsStore.KanjiImprovement("提", 0.4, 0.2),
                                        new StudyStatsStore.KanjiImprovement("余", 0.6, 0.3)
                                )
                        ),
                        new StudyStatsStore.MatureSupportGainedMetric(
                                1,
                                3,
                                1,
                                Collections.singletonList(new StudyStatsStore.KanjiSupportGain("語", 0, 3))
                        ),
                        busyLadder
                );
                String workingBody = activity.statsVerdictBody(workingStats, true, true);
                assertTrue(workingBody.contains("weak kanji are burning down"));
                assertTrue(workingBody.contains("mature Anki cards have been gained"));
                assertTrue(workingBody.contains("review-phase item is ready to climb"));
                assertTrue(workingBody.contains("review-phase items with miss streaks"));
                assertTrue(activity.ladderHealthBody(busyLadder).contains("at the demotion threshold"));
                assertEquals(3, activity.weaknessImprovementExamples(workingStats.weakKanjiImproved).size());
                assertTrue(activity.supportGainExamples(workingStats.matureSupportGained).get(0).contains("0 -> 3 mature cards"));
                assertTrue(activity.queuedEntries(
                        Collections.singletonList(activeRow),
                        Collections.singletonList(studyItem("裂", Records.LadderRung.KANJI_MEANING, "review", 0L)),
                        System.currentTimeMillis()
                ) != null);

                activity.renderSyncResult(syncResult(true, false, 1, 0, "", ""));
                performButtonClick(activity.content, MainActivityBase.LABEL_STUDY_NOW);
                assertHasText(activity, "Name this kanji");
                assertHasText(activity, "What does this kanji mean?");

                LinearLayout summary = new LinearLayout(activity);
                activity.addOptionalSyncSummaryLines(summary, syncResult(true, false, 1, 0, "", ""));
                assertEquals(0, summary.getChildCount());
                activity.content.removeAllViews();
                activity.addRecoveryTimeline(new Records.KanjiRecoveryTimeline(null, null, null, Collections.emptyList()));
                assertTrue(containsText(activity.content, "Timeline will fill in after the next sync or review."));
            });
        }
    }

    @Test
    public void writingCallbacksResetResultStateAfterInkEditsAndCleanerRetry() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                Records.DashboardRow row = row("裂", "split", "レツ", Collections.emptyList());
                Records.StudySession writing = session("裂", BridgeScheduler.TASK_WRITE_KANJI, row);
                activity.activeSession = writing;
                activity.currentHintState = HintState.fromWritingLevel(2);
                activity.drawingPad = new DrawingPadView(activity);
                activity.drawingPad.setTarget("裂");
                addInk(activity.drawingPad);
                activity.studyStatus = new TextView(activity);
                activity.resultStatus = new TextView(activity);
                activity.checkWritingButton = new Button(activity);
                activity.downloadModelButton = new Button(activity);
                activity.nextAfterPassButton = new Button(activity);
                activity.manualOverrideButton = new Button(activity);
                activity.practiceWithGuideButton = new Button(activity);
                activity.replayButton = new Button(activity);
                activity.hintButton = new Button(activity);
                activity.studyAnswerPanel = new LinearLayout(activity);

                StrokeOrderEvaluator.StrokeOrderResult order = StrokeOrderEvaluator
                        .evaluate(guide("裂"), sample())
                        .withDiagnosis(StrokeDiagnosis.builder().add(StrokeDiagnosis.Label.WRONG_DIRECTION, 1).build());
                activity.activeAnalysis = analysis(WritingAnalysis.Status.WRONG, false, order);
                assertTrue(activity.canReplayAnalysis(activity.activeAnalysis, guide("裂")));
                activity.resultStatus.setText("Previous result");
                activity.resultStatus.setVisibility(View.VISIBLE);
                activity.handleDrawingEdited();
                assertNull(activity.activeAnalysis);
                assertTrue(activity.studyStatus.getText().toString().contains("Updated ink"));
                assertEquals(View.GONE, activity.resultStatus.getVisibility());
                assertFalse(activity.canReplayAnalysis(analysis(WritingAnalysis.Status.RECOGNITION_ERROR, false, order), guide("裂")));
                assertFalse(activity.canReplayAnalysis(activity.activeAnalysis, guide("裂")));
                assertFalse(activity.canReplayAnalysis(analysis(WritingAnalysis.Status.WRONG, false, order), null));
                assertFalse(activity.canReplayAnalysis(analysis(WritingAnalysis.Status.WRONG, false, order), new StrokeGuide("裂", Collections.emptyList())));

                activity.activeAnalysis = analysis(WritingAnalysis.Status.CLOSE, true, order);
                activity.resultStatus.setText("Messy pass");
                activity.resultStatus.setVisibility(View.VISIBLE);
                activity.startCleanerRetry();
                assertNull(activity.activeAnalysis);
                assertTrue(activity.studyStatus.getText().toString().contains("Try cleaner"));
                assertEquals(View.GONE, activity.resultStatus.getVisibility());

                activity.checkingWriting = true;
                activity.activeAnalysis = analysis(WritingAnalysis.Status.WRONG, false, order);
                activity.resultStatus.setText("Still checking");
                activity.resultStatus.setVisibility(View.VISIBLE);
                activity.handleDrawingEdited();
                assertTrue(activity.activeAnalysis != null);
                assertEquals(View.VISIBLE, activity.resultStatus.getVisibility());

                activity.checkingWriting = false;
                activity.activeSession = writing;
                activity.currentHintState = HintState.fromWritingLevel(1);
                activity.activeAnalysis = analysis(WritingAnalysis.Status.WRONG, false, order);
                addInk(activity.drawingPad);
                activity.drawingPad.captureReplaySnapshot();
                activity.replayWritingAnalysis();
                assertTrue(activity.drawingPad.isReplayOverlayVisibleForTests());
            });
        }
    }

    @Test
    public void writingRecognizerStatusCallbacksUpdateTheVisibleState() {
        FakeWritingRecognizer recognizer = new FakeWritingRecognizer(
                CompletableFuture.completedFuture(new WritingRecognizer.ModelStatus("ja", "ja-JP", false, "missing")),
                CompletableFuture.completedFuture(new WritingRecognizer.ModelStatus("ja", "ja-JP", true, "ready")),
                CompletableFuture.completedFuture(new WritingRecognizer.RecognitionResult(Collections.singletonList(
                        new WritingRecognizer.Candidate("裂", 0.9f)
                )))
        );
        MainActivity.setWritingRecognizerForTests(recognizer);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                Records.DashboardRow row = row("裂", "split", "レツ", Collections.emptyList());
                activity.studyStatus = new TextView(activity);
                activity.downloadModelButton = new Button(activity);
                activity.refreshWritingModelStatus();
                activity.activeSession = session("裂", BridgeScheduler.TASK_WRITE_KANJI, row);
                activity.currentHintState = HintState.fromWritingLevel(2);
                activity.refreshWritingModelStatus();
            });
            scenario.onActivity(activity -> {
                assertTrue(activity.writingModelStatusKnown);
                assertFalse(activity.writingModelDownloaded);
                assertTrue(activity.studyStatus.getText().toString().contains("Download the handwriting checker"));

                StrokeOrderEvaluator.StrokeOrderResult order = StrokeOrderEvaluator
                        .evaluate(guide("裂"), sample())
                        .withDiagnosis(StrokeDiagnosis.builder().add(StrokeDiagnosis.Label.WRONG_ORDER, 1).build());
                activity.activeAnalysis = analysis(WritingAnalysis.Status.WRONG, false, order);
                activity.studyStatus.setText("Existing analysis message");
                activity.refreshWritingModelStatus();
            });
            scenario.onActivity(activity -> {
                assertEquals("Existing analysis message", activity.studyStatus.getText().toString());
                activity.activeAnalysis = null;
                activity.downloadWritingModel();
            });
            scenario.onActivity(activity -> {
                assertTrue(activity.writingModelDownloaded);
                assertTrue(activity.studyStatus.getText().toString().contains("Handwriting checker ready"));

                activity.activeAnalysis = null;
                activity.recognizeWriting(
                        recognizer,
                        capturedWriting(),
                        sample(),
                        guide("裂"),
                        "裂",
                        activity.activeSession.token
                );
            });
            scenario.onActivity(activity -> {
                assertTrue(activity.activeAnalysis != null);
                assertTrue(activity.activeAnalysis.status == WritingAnalysis.Status.PASS
                        || activity.activeAnalysis.status == WritingAnalysis.Status.CLOSE
                        || activity.activeAnalysis.status == WritingAnalysis.Status.WRONG);
            });
        } finally {
            MainActivity.setWritingRecognizerForTests(null);
        }
    }

    @Test
    public void studyEntryPointsAndWritingGuardsCoverEmptyAndUnavailableStates() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.renderStudy();
                assertHasText(activity, "Nothing to study yet");
                activity.renderStudyForKanji("missing");
                assertHasText(activity, "Kanji not available");
                assertFalse(activity.startStudyMoreNewCards(3));

                activity.checkWriting();

                Records.DashboardRow row = row("裂", "split", "レツ", Collections.emptyList());
                activity.activeSession = session("裂", BridgeScheduler.TASK_WRITE_KANJI, row);
                activity.currentHintState = HintState.fromWritingLevel(1);
                activity.studyStatus = new TextView(activity);
                activity.resultStatus = new TextView(activity);
                activity.drawingPad = new DrawingPadView(activity);
                activity.checkWriting();
                assertEquals(WritingAnalysis.Status.NO_INK, activity.activeAnalysis.status);
                activity.activeAnalysis = null;
                assertTrue(activity.showNoInkWhenNeeded());
                assertEquals(WritingAnalysis.Status.NO_INK, activity.activeAnalysis.status);

                activity.showModelUnavailable("The handwriting checker is unavailable on this device.");
                assertEquals(WritingAnalysis.Status.MODEL_UNAVAILABLE, activity.activeAnalysis.status);
                assertTrue(activity.resultStatus.getText().toString().contains("unavailable"));

                activity.replayWritingAnalysis();
                activity.activeSession = null;
                activity.replayWritingAnalysis();
            });
        }
    }

    private static WritingAnalysis analysis(WritingAnalysis.Status status, boolean passed, StrokeOrderEvaluator.StrokeOrderResult order) {
        return new WritingAnalysis(status, passed ? "good" : "again", passed, status.name(), Collections.emptyList(), order, HintLevel.BLIND, 0);
    }

    private static Records.StudySession session(String kanji, String taskType, Records.DashboardRow row) {
        return sessionWithToken(kanji, taskType, row, "tok");
    }

    private static Records.StudySession sessionWithToken(String kanji, String taskType, Records.DashboardRow row, String token) {
        Records.StudyItem item = new Records.StudyItem(
                kanji,
                "review",
                0L,
                1.0,
                5.0,
                1,
                0,
                0,
                1,
                0,
                0,
                0L,
                BridgeScheduler.TASK_WRITE_KANJI.equals(taskType),
                "",
                0L,
                0,
                "sig",
                token,
                0L
        );
        String prompt = row == null ? "" : row.primaryMeaning;
        return new Records.StudySession(item, row, token, taskType, BridgeScheduler.TASK_WRITE_KANJI.equals(taskType), prompt);
    }

    private static Records.DashboardRow row(String kanji, String meaning, String reading, List<Records.Example> examples) {
        return new Records.DashboardRow(kanji, 1000, meaning, reading, kanji, 10, "reason", "reason text", 1, 0, 0, examples);
    }

    private static Records.DashboardRow rowWithReason(String kanji, String meaning, String reading, String reason, List<Records.Example> examples) {
        return new Records.DashboardRow(kanji, 1000, meaning, reading, kanji, 10, "reason", reason, 1, 0, 0, examples);
    }

    private static Records.Example example(String expression, String reading, String meaning, String sourceType) {
        return new Records.Example(sourceType, 1L, 2L, expression, reading, meaning, "", false, 0);
    }

    private static StrokeGuide guide(String kanji) {
        return new StrokeGuide(
                kanji,
                Arrays.asList(
                        new InkStroke(Arrays.asList(new InkPoint(0f, 0f, 0L), new InkPoint(1f, 0f, 1L))),
                        new InkStroke(Arrays.asList(new InkPoint(0f, 1f, 2L), new InkPoint(1f, 1f, 3L)))
                )
        );
    }

    private static WritingSample sample() {
        return new WritingSample(
                Collections.singletonList(new InkStroke(Arrays.asList(new InkPoint(0f, 0f, 0L), new InkPoint(1f, 0f, 1L)))),
                1f,
                1f
        );
    }

    private static CapturedWriting capturedWriting() {
        return new CapturedWriting(Collections.singletonList(new CapturedStroke(Arrays.asList(
                new CapturedStroke.Point(0f, 0f, 0L),
                new CapturedStroke.Point(1f, 0f, 1L)
        ))));
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }

    private static Records.Settings settings(
            boolean active,
            boolean suspended,
            boolean tagged,
            List<String> tags,
            boolean weak,
            boolean browser,
            String query
    ) {
        Records.Settings defaults = Records.Settings.kikuDefaults();
        return new Records.Settings(
                defaults.modelName,
                defaults.templateName,
                defaults.expressionField,
                defaults.readingField,
                defaults.meaningField,
                defaults.sentenceField,
                defaults.frequencyField,
                defaults.frequencySortField,
                defaults.matureDays,
                defaults.matureSupportThreshold,
                defaults.suspendedRankMin,
                defaults.suspendedRankMax,
                defaults.activeQueueCap,
                defaults.newPerDay,
                defaults.writingTriggerMissDays,
                defaults.recognitionPromotionPasses,
                defaults.realDueReviewsToMove,
                active,
                suspended,
                tagged,
                tags,
                weak,
                defaults.importWeakFsrsDifficultyThreshold,
                defaults.importWeakLapsesThreshold,
                3,
                browser,
                query
        );
    }

    private static Records.StudyItem studyItem(String kanji, Records.LadderRung rung, String state, long dueAtMillis) {
        return new Records.StudyItem(
                kanji,
                state,
                dueAtMillis,
                1.0,
                5.0,
                1,
                0,
                0,
                1,
                0,
                0,
                0L,
                false,
                "",
                0L,
                0,
                "sig",
                "tok",
                0L
        ).withRung(rung);
    }

    private static Records.KanjiTimelineEvent event(String expression, String reading) {
        return new Records.KanjiTimelineEvent(
                1L,
                "語",
                1000L,
                "review_passed",
                "Review passed",
                "",
                expression,
                reading,
                "good",
                false,
                true,
                false,
                null,
                null,
                null,
                "event"
        );
    }

    private static ManualSyncEngine.SyncResult syncResult(
            boolean success,
            boolean skipped,
            int dashboardRows,
            int importedSuspendedKanji,
            String message,
            String adaptiveSummary
    ) {
        try {
            Constructor<ManualSyncEngine.SyncResult> constructor = ManualSyncEngine.SyncResult.class.getDeclaredConstructor(
                    boolean.class,
                    boolean.class,
                    int.class,
                    int.class,
                    String.class,
                    String.class
            );
            constructor.setAccessible(true);
            return constructor.newInstance(success, skipped, dashboardRows, importedSuspendedKanji, message, adaptiveSummary);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError(error);
        }
    }

    private static CheckBox checked(Context context, boolean checked) {
        CheckBox box = new CheckBox(context);
        box.setChecked(checked);
        return box;
    }

    private static void seedRows(MainActivity activity, List<Records.DashboardRow> rows) {
        List<Records.Note> notes = new ArrayList<>();
        List<Records.Card> cards = new ArrayList<>();
        long id = 1L;
        for (Records.DashboardRow row : rows) {
            notes.add(note(id, row.kanji + "語", row.reading, row.primaryMeaning, row.kanji + "を見た。"));
            cards.add(new Records.Card(100L + id, id, 0, "Kiku", 2, 2, 0, 1, 0, 0, false));
            id++;
        }
        activity.store.saveSuccessfulSync(
                new Records.CollectionSnapshot(notes, cards),
                Collections.emptyList(),
                rows,
                Records.Settings.kikuDefaults(),
                1000L,
                2000L,
                null
        );
    }

    private static SimilarKanjiIndex similarIndex(String tsv) {
        try {
            return SimilarKanjiIndex.parseTsv(new StringReader(tsv));
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private static Records.Note note(long id, String expression, String reading, String meaning, String sentence) {
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("Expression", expression);
        fields.put("ExpressionReading", reading);
        fields.put("MainDefinition", meaning);
        fields.put("Sentence", sentence);
        fields.put("Frequency", "1000");
        fields.put("FreqSort", "1000");
        return new Records.Note(id, 1001L, "Kiku", fields, Collections.emptyList());
    }

    private static void addInk(DrawingPadView pad) {
        pad.onTouchEvent(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 10f, 10f, 0));
        pad.onTouchEvent(MotionEvent.obtain(0L, 20L, MotionEvent.ACTION_MOVE, 40f, 40f, 0));
        pad.onTouchEvent(MotionEvent.obtain(0L, 40L, MotionEvent.ACTION_UP, 80f, 80f, 0));
    }

    private static void layoutPad(DrawingPadView pad) {
        pad.measure(
                View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY)
        );
        pad.layout(0, 0, 320, 320);
    }

    private static void prepareWritingUi(MainActivity activity, Records.StudySession session) {
        activity.activeSession = session;
        activity.currentHintState = HintState.fromWritingLevel(1);
        activity.studyStatus = new TextView(activity);
        activity.resultStatus = new TextView(activity);
        activity.checkWritingButton = new Button(activity);
        activity.downloadModelButton = new Button(activity);
        activity.nextAfterPassButton = new Button(activity);
        activity.manualOverrideButton = new Button(activity);
        activity.practiceWithGuideButton = new Button(activity);
        activity.replayButton = new Button(activity);
        activity.hintButton = new Button(activity);
        activity.studyAnswerPanel = new LinearLayout(activity);
        activity.drawingPad = new DrawingPadView(activity);
        activity.drawingPad.setTarget(session.item.kanji);
        activity.activeAnalysis = null;
        activity.checkingWriting = false;
        activity.writingModelStatusKnown = false;
        activity.writingModelDownloaded = false;
    }

    private static void assertHasText(MainActivity activity, String text) {
        if (!containsText(activity.findViewById(android.R.id.content), text)) {
            throw new AssertionError("Missing text: " + text);
        }
    }

    private static void assertContainsText(View root, String text) {
        if (!containsTextContaining(root, text)) {
            throw new AssertionError("Missing text containing: " + text);
        }
    }

    private static boolean containsText(View view, String expected) {
        if (view instanceof TextView textView && expected.contentEquals(textView.getText())) {
            return true;
        }
        if (!(view instanceof ViewGroup group)) {
            return false;
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            if (containsText(group.getChildAt(i), expected)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsTextContaining(View view, String expected) {
        if (view instanceof TextView textView && textView.getText().toString().contains(expected)) {
            return true;
        }
        if (!(view instanceof ViewGroup group)) {
            return false;
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            if (containsTextContaining(group.getChildAt(i), expected)) {
                return true;
            }
        }
        return false;
    }

    private static void performButtonClick(View root, String label) {
        Button button = findButton(root, label);
        if (button == null) {
            throw new AssertionError("Missing button: " + label);
        }
        button.performClick();
    }

    private static void performClickableWithText(View root, String label) {
        View clickable = findClickableWithText(root, label);
        if (clickable == null) {
            throw new AssertionError("Missing clickable text: " + label);
        }
        clickable.performClick();
    }

    private static View findClickableWithText(View view, String label) {
        if (view.isClickable() && containsText(view, label)) {
            return view;
        }
        if (!(view instanceof ViewGroup group)) {
            return null;
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = findClickableWithText(group.getChildAt(i), label);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Button findButton(View view, String label) {
        if (view instanceof Button button && label.contentEquals(button.getText())) {
            return button;
        }
        if (!(view instanceof ViewGroup group)) {
            return null;
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            Button found = findButton(group.getChildAt(i), label);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static List<EditText> editTexts(View root) {
        List<EditText> out = new ArrayList<>();
        collectEditTexts(root, out);
        return out;
    }

    private static List<CheckBox> checkBoxes(View root) {
        List<CheckBox> out = new ArrayList<>();
        collectCheckBoxes(root, out);
        return out;
    }

    private static List<SeekBar> seekBars(View root) {
        List<SeekBar> out = new ArrayList<>();
        collectSeekBars(root, out);
        return out;
    }

    private static void collectEditTexts(View view, List<EditText> out) {
        if (view instanceof EditText editText) {
            out.add(editText);
            return;
        }
        if (!(view instanceof ViewGroup group)) {
            return;
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            collectEditTexts(group.getChildAt(i), out);
        }
    }

    private static void collectCheckBoxes(View view, List<CheckBox> out) {
        if (view instanceof CheckBox checkBox) {
            out.add(checkBox);
            return;
        }
        if (!(view instanceof ViewGroup group)) {
            return;
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            collectCheckBoxes(group.getChildAt(i), out);
        }
    }

    private static void collectSeekBars(View view, List<SeekBar> out) {
        if (view instanceof SeekBar seekBar) {
            out.add(seekBar);
            return;
        }
        if (!(view instanceof ViewGroup group)) {
            return;
        }
        for (int i = 0; i < group.getChildCount(); i++) {
            collectSeekBars(group.getChildAt(i), out);
        }
    }

    private static MotionEvent motion(int action, float x, float y) {
        return MotionEvent.obtain(0L, 0L, action, x, y, 0);
    }

    private static void touchSeekBar(SeekBar seekBar) {
        seekBar.measure(
                View.MeasureSpec.makeMeasureSpec(240, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(56, View.MeasureSpec.EXACTLY)
        );
        seekBar.layout(0, 0, 240, 56);
        seekBar.onTouchEvent(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 120f, 28f, 0));
        seekBar.onTouchEvent(MotionEvent.obtain(0L, 20L, MotionEvent.ACTION_UP, 180f, 28f, 0));
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }

    private static final class FakeWritingRecognizer implements WritingRecognizer {
        private final CompletableFuture<ModelStatus> status;
        private final CompletableFuture<ModelStatus> download;
        private final CompletableFuture<RecognitionResult> recognition;

        private FakeWritingRecognizer(
                CompletableFuture<ModelStatus> status,
                CompletableFuture<ModelStatus> download,
                CompletableFuture<RecognitionResult> recognition
        ) {
            this.status = status;
            this.download = download;
            this.recognition = recognition;
        }

        @Override
        public CompletableFuture<ModelStatus> modelStatus() {
            return status;
        }

        @Override
        public CompletableFuture<ModelStatus> downloadModel() {
            return download;
        }

        @Override
        public CompletableFuture<RecognitionResult> recognize(CapturedWriting writing) {
            return recognition;
        }

        @Override
        public void close() {
        }
    }
}
