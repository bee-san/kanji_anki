package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.core.AdaptiveFocusCopy;
import dev.bee.kanjianki.core.DateTextPolicy;
import dev.bee.kanjianki.core.FocusQueueCopy;
import dev.bee.kanjianki.core.HomeTextCopy;
import dev.bee.kanjianki.core.KanjiGameEngine;
import dev.bee.kanjianki.core.SettingsImportPreset;
import dev.bee.kanjianki.core.SettingsInputRules;
import dev.bee.kanjianki.core.SettingsTextCopy;
import dev.bee.kanjianki.core.StudyTextCopy;
import dev.bee.kanjianki.core.StatsTextCopy;
import dev.bee.kanjianki.core.TimelineCopy;
import dev.bee.kanjianki.core.StudyTaskCopy;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;

import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.SimilarKanjiIndex;
import dev.bee.kanjianki.core.study.HintLevel;
import dev.bee.kanjianki.core.study.HintState;
import dev.bee.kanjianki.core.study.InkPoint;
import dev.bee.kanjianki.core.study.InkStroke;
import dev.bee.kanjianki.core.study.RecognitionCandidate;
import dev.bee.kanjianki.core.study.StrokeDiagnosis;
import dev.bee.kanjianki.core.study.StrokeGuide;
import dev.bee.kanjianki.core.study.StrokeOrderEvaluator;
import dev.bee.kanjianki.core.study.StrokeDiagnosisFormatter;
import dev.bee.kanjianki.core.study.WritingAnalysis;
import dev.bee.kanjianki.core.study.WritingFeedbackCopy;
import dev.bee.kanjianki.core.study.WritingSample;
import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.data.StudyStatsStore;
import dev.bee.kanjianki.study.CapturedStroke;
import dev.bee.kanjianki.study.CapturedWriting;
import dev.bee.kanjianki.study.WritingRecognizer;
import dev.bee.kanjianki.sync.ManualSyncEngine;
import dev.bee.kanjianki.MainActivityStudyWritingStatus;

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
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertSame;
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
                assertCountAndCompactText(activity);
                assertStudyModeLabels(activity);
                assertAdaptiveFocusText(activity);
                assertWritingGuideText(activity);
            });
        }
    }

    private static void assertCountAndCompactText(MainActivity activity) {
        assertEquals("1 item", StudyTextCopy.countText(1, "item", "items"));
        assertEquals("2 items", StudyTextCopy.countText(2, "item", "items"));
        assertEquals("", StudyTextCopy.compact(null, 12));
        assertEquals("short", StudyTextCopy.compact("short", 12));
        assertEquals("a very long s...", StudyTextCopy.compact("a very long sentence that should be shortened", 16));
    }

    private static void assertStudyModeLabels(MainActivity activity) {
        assertEquals("Study", StudyTaskCopy.labelForTask(null));
        assertEquals("Focused recall", StudyTaskCopy.labelForTask("targeted_flashcard"));
        assertEquals("Kanji -> meaning", StudyTaskCopy.labelForTask(BridgeScheduler.TASK_KANJI_MEANING));
        assertEquals("Type the meaning", StudyTaskCopy.labelForTask(BridgeScheduler.TASK_TYPE_MEANING));
        assertEquals("Meaning -> kanji", StudyTaskCopy.labelForTask(BridgeScheduler.TASK_MEANING_KANJI));
        assertEquals("Font -> meaning", StudyTaskCopy.labelForTask(BridgeScheduler.TASK_FONT_MEANING));
        assertEquals("Word -> reading", StudyTaskCopy.labelForTask(BridgeScheduler.TASK_WORD_READING));
        assertEquals("Write kanji", StudyTaskCopy.labelForTask(BridgeScheduler.TASK_WRITE_KANJI));
        assertEquals("Similar kanji", StudyTaskCopy.labelForTask(BridgeScheduler.TASK_SIMILAR_KANJI));
        assertEquals("Quick recall", StudyTaskCopy.labelForTask("meaning_flashcard"));
        assertEquals("Font check", StudyTaskCopy.labelForTask("font_recognition"));
        assertEquals("Write to repair", StudyTaskCopy.labelForTask("repair_writing"));
        assertEquals("Focused practice", StudyTaskCopy.labelForTask("targeted_writing"));
        assertEquals("New problem kanji", StudyTaskCopy.labelForTask("context_writing"));
        assertEquals("Guided review", StudyTaskCopy.labelForTask("guided_writing"));
        assertEquals("Memory check", StudyTaskCopy.labelForTask("blind_writing"));
        assertEquals("Memory check", StudyTaskCopy.labelForTask("sampled_handwriting"));
        assertEquals("Learn the shape", StudyTaskCopy.labelForTask("confusable_recognition"));
        assertEquals("Study", StudyTaskCopy.labelForTask("unexpected"));
        assertEquals("android.permission.POST_NOTIFICATIONS", MainActivityBase.PERMISSION_POST_NOTIFICATIONS);
    }

    private static void assertAdaptiveFocusText(MainActivity activity) {
        RecordsSchedulerModels.AdaptiveLoadPlan waiting = new RecordsSchedulerModels.AdaptiveLoadPlan(20, 0, 0, Collections.emptyList(), 0, false, "");
        RecordsSchedulerModels.AdaptiveLoadPlan all = new RecordsSchedulerModels.AdaptiveLoadPlan(100, 3, 3, Arrays.asList("裂", "提", "語"), 0, true, "all");
        RecordsSchedulerModels.AdaptiveLoadPlan focused = new RecordsSchedulerModels.AdaptiveLoadPlan(20, 5, 2, Arrays.asList("裂", "提"), 0, false, "focus");
        assertEquals("Adaptive focus is waiting for sync", AdaptiveFocusCopy.adaptiveFocusText(null));
        assertEquals("Adaptive focus is waiting for sync", AdaptiveFocusCopy.adaptiveFocusText(waiting));
        assertEquals("Adaptive focus is set to all current problem kanji", AdaptiveFocusCopy.adaptiveFocusText(all));
        assertEquals("Today's adaptive focus: 2 items left / 5", AdaptiveFocusCopy.adaptiveFocusText(focused));
    }

    private static void assertWritingGuideText(MainActivity activity) {
        StrokeGuide emptyGuide = new StrokeGuide("裂", Collections.emptyList());
        StrokeGuide guide = guide("裂");
        assertTrue(WritingFeedbackCopy.guideLabel(3, emptyGuide).startsWith("Write from memory"));
        assertTrue(WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(3), emptyGuide).startsWith("Write from memory"));
        assertTrue(WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(0), emptyGuide).startsWith("No numbered stroke guide"));
        assertEquals("Trace the numbered strokes, then check. This is a learning attempt.", WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(0), guide));
        assertEquals("Copy the faint outline; the current stroke is emphasized.", WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(1), guide));
        assertEquals("Write with only the current stroke hinted, then check.", WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(2), guide));
        assertEquals("Write from memory, then check. Use Hint if you are stuck.", WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(3), guide));
        assertEquals("Trace", WritingFeedbackCopy.stageLabel(HintLevel.TRACE));
        assertEquals("Blind", WritingFeedbackCopy.stageLabel(HintLevel.BLIND));
        assertEquals("", WritingFeedbackCopy.attemptProgressText(null, null, false));
        assertEquals("", WritingFeedbackCopy.targetRevealText(null, null));
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
                RecordsStudyModels.StudyItem reviewDue = studyItem("復", RecordsBase.LadderRung.KANJI_MEANING, "review", now - 1L);
                activity.studyMoreNewCardKanji.add("復");
                RecordsSchedulerModels.AdaptiveLoadPlan extraPlan = activity.studyMoreNewCardsPlan(
                        Collections.singletonList(row("復", "review", "フク", Collections.emptyList())),
                        Collections.singletonList(reviewDue.copyBuilder().totalReviews(1).build()),
                        now
                );
                assertEquals(1, extraPlan.remaining);

                RecordsSchedulerModels.AdaptiveLoadPlan all = activity.allCurrentProblemKanjiPlan(
                        Arrays.asList(row("裂", "split", "レツ", Collections.emptyList()), row("語", "language", "ゴ", Collections.emptyList())),
                        Collections.singletonList(studyItem("裂", RecordsBase.LadderRung.KANJI_MEANING, "review", now - 1L).copyBuilder().totalReviews(1).build()),
                        now
                );
                assertEquals(2, all.target);
                assertTrue(all.allKanjiMode);

                activity.activeSession = session("裂", BridgeScheduler.TASK_KANJI_MEANING, row("裂", "split", "レツ", Collections.emptyList()));
                activity.studySessionTracker.setTargetCount(2);
                activity.markStudyTaskCompleted("topbar:one");
                activity.markStudyTaskCompleted("topbar:two");
                activity.continueAllKanjiSession = true;
                assertTrue(activity.studyTopBar(all) instanceof androidx.compose.ui.platform.ComposeView);
                RecordsStudyModels.StudyItem clueItem = studyItem("?", RecordsBase.LadderRung.KANJI_MEANING, "review", now);
                assertEquals(
                        "Fallback prompt",
                        StudyTextCopy.sessionClue(activity.currentDictionaryLookup(), new RecordsSchedulerModels.StudySession(clueItem, null, "tok", BridgeScheduler.TASK_KANJI_MEANING, false, "fallback prompt"))
                );
                assertEquals("Fallback", StudyTextCopy.canonicalKanjiMeaning(activity.currentDictionaryLookup(), "?", "fallback", 40));
                FakeWritingRecognizer cachedRecognizer = new FakeWritingRecognizer(
                        CompletableFuture.completedFuture(new WritingRecognizer.ModelStatus("ja", "ja-JP", true, "ready")),
                        CompletableFuture.completedFuture(new WritingRecognizer.ModelStatus("ja", "ja-JP", true, "ready")),
                        CompletableFuture.completedFuture(new WritingRecognizer.RecognitionResult(Collections.emptyList()))
                );
                activity.writingRecognizer = cachedRecognizer;
                assertSame(cachedRecognizer, activity.currentWritingRecognizer());

                StudySessionTracker.ActiveStudyTask timing = new StudySessionTracker.ActiveStudyTask(null, null, null, -10L);
                timing.pause(50L);
                timing.resume(60L);
                timing.pause(90L);
                assertEquals(30L, timing.activeElapsedMillis);

            });
        }
    }

    @Test
    public void studySessionHelpersPickExamplesPromptsTitlesAndTaskKinds() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                RecordsImportModels.Example suspended = example("停止語", "テイシゴ", "suspended", MainActivityBase.SOURCE_SUSPENDED);
                RecordsImportModels.Example active = example("活動語", "カツドウゴ", "active", MainActivityBase.SOURCE_ACTIVE);
                RecordsImportModels.Example fallback = example("予備語", "ヨビゴ", "fallback", "other");
                RecordsImportModels.DashboardRow row = row("語", "language", "ゴ", Arrays.asList(fallback, active, suspended));

                assertEquals(active, activity.firstExample(row));
                assertEquals(suspended, activity.wordReadingExample(row));
                assertEquals(fallback, activity.firstExample(row("語", "language", "ゴ", Collections.singletonList(fallback))));
                assertEquals(suspended, activity.exampleForSession(session("語", BridgeScheduler.TASK_WORD_READING, row)));
                assertEquals(active, activity.exampleForSession(session("語", BridgeScheduler.TASK_KANJI_MEANING, row)));

                assertEquals("What is the reading?", StudyTextCopy.heroQuestion(session("語", BridgeScheduler.TASK_WORD_READING, row)));
                assertEquals("What does this kanji mean?", StudyTextCopy.heroQuestion(session("語", BridgeScheduler.TASK_KANJI_MEANING, row)));
                assertEquals("Read this word", StudyTaskCopy.flashcardTitle(session("語", BridgeScheduler.TASK_WORD_READING, row)));
                assertEquals("Type the meaning", StudyTaskCopy.flashcardTitle(session("語", BridgeScheduler.TASK_TYPE_MEANING, row)));
                assertEquals("Recognise this kanji", StudyTaskCopy.flashcardTitle(session("語", BridgeScheduler.TASK_FONT_MEANING, row)));
                assertEquals("Name this kanji", StudyTaskCopy.flashcardTitle(session("語", BridgeScheduler.TASK_KANJI_MEANING, row)));
                assertEquals("Read", StudyTaskCopy.studyModeLabel(session("語", BridgeScheduler.TASK_WORD_READING, row)));
                assertEquals("Type", StudyTaskCopy.studyModeLabel(session("語", BridgeScheduler.TASK_TYPE_MEANING, row)));
                assertEquals("Recognise", StudyTaskCopy.studyModeLabel(session("語", BridgeScheduler.TASK_KANJI_MEANING, row)));
                assertEquals("活動語", StudyTextCopy.wordPrompt(session("語", BridgeScheduler.TASK_WORD_READING, row("語", "language", "ゴ", Collections.singletonList(active)))));
                assertEquals("語", StudyTextCopy.wordPrompt(session("語", BridgeScheduler.TASK_WORD_READING, row("語", "language", "ゴ", Collections.emptyList()))));
                assertEquals("active", StudyTextCopy.collectionMeaningForSession(session("語", BridgeScheduler.TASK_WORD_READING, row("語", "language", "ゴ", Collections.singletonList(active)))));
                assertEquals("active", StudyTextCopy.collectionMeaningForSession(session("語", BridgeScheduler.TASK_KANJI_MEANING, row)));
                assertEquals("", StudyTextCopy.collectionMeaningForSession(null));

                assertTrue(StudyTaskCopy.isWordReadingTask(session("語", BridgeScheduler.TASK_WORD_READING, row)));
                assertTrue(StudyTaskCopy.isTypingMeaningTask(session("語", BridgeScheduler.TASK_TYPE_MEANING, row)));
                assertTrue(StudyTaskCopy.isFontRecognitionTask(session("語", BridgeScheduler.TASK_FONT_MEANING, row)));
                assertTrue(StudyTaskCopy.isRecallTask(session("語", "blind_writing", row)));
                assertFalse(StudyTaskCopy.isRecallTask(null));
            });
        }
    }

    @Test
    public void writingAnalysisHelpersExplainDiagnosisAndAllowedActions() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                RecordsSchedulerModels.StudySession writing = session("裂", BridgeScheduler.TASK_WRITE_KANJI, row("裂", "split", "レツ", Collections.emptyList()));
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

                assertTrue(StrokeDiagnosisFormatter.text(wrong).contains("Stroke 1: likely wrong order"));
                assertTrue(StrokeDiagnosisFormatter.text(wrong).contains("Recognized, but the stroke path was messy"));
                assertEquals("Stroke 2: likely wrong direction", StrokeDiagnosisFormatter.line(diagnosis.entries.get(1)));
                assertEquals("Stroke 3: may be missing", StrokeDiagnosisFormatter.line(diagnosis.entries.get(2)));
                assertEquals("Stroke 4: shape looks rough", StrokeDiagnosisFormatter.line(diagnosis.entries.get(3)));
                assertTrue(StrokeDiagnosisFormatter.canShow(wrong));
                assertFalse(StrokeDiagnosisFormatter.canShow(analysis(WritingAnalysis.Status.NO_INK, false, order)));

                assertTrue(WritingFeedbackCopy.canSubmitAnalysis(wrong));
                assertTrue(WritingFeedbackCopy.canManualOverride(wrong));
                assertTrue(WritingFeedbackCopy.canPracticeAfterAnalysis(wrong));
                assertFalse(WritingFeedbackCopy.canSubmitAnalysis(null));
                assertFalse(WritingFeedbackCopy.canManualOverride(close));
                assertTrue(WritingFeedbackCopy.shouldIncreaseSupportAfterAnalysis(wrong));
                assertFalse(WritingFeedbackCopy.shouldIncreaseSupportAfterAnalysis(close));
                assertTrue(WritingFeedbackCopy.shouldShowLearningPanel(wrong, false, false, 1));
                assertFalse(WritingFeedbackCopy.shouldShowLearningPanel(
                        analysis(WritingAnalysis.Status.NO_INK, false, order),
                        false,
                        false,
                        1
                ));
                assertTrue(WritingFeedbackCopy.attemptProgressText(close, activity.activeSession == null ? null : activity.activeSession.item.writingLevel, WritingFeedbackCopy.shouldIncreaseSupportAfterAnalysis(close)).contains("Try cleaner"));
                assertTrue(WritingFeedbackCopy.attemptProgressText(pass, activity.activeSession == null ? null : activity.activeSession.item.writingLevel, WritingFeedbackCopy.shouldIncreaseSupportAfterAnalysis(pass)).contains("less help"));
                assertTrue(WritingFeedbackCopy.targetRevealText(wrong, activity.activeSession == null ? null : activity.activeSession.item.kanji).contains("Target: 裂"));
            });
        }
    }

    @Test
    public void settingsHelpersSummarizeImportTimingAndWorkloadChoices() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                verifyVersionRankAndRetentionText(activity);
                verifyImportSourceSummaries(activity);
                verifyAutoSyncSummaries(activity);
                verifyWorkloadAndReminderSummaries(activity);
                verifyImportThresholdReader(activity);
                verifyRankAndMaxItemControls(activity);
            });
        }
    }

    private static void verifyVersionRankAndRetentionText(MainActivity activity) {
        assertEquals("unknown version", SettingsTextCopy.versionText(""));
        assertEquals("0.4.33", SettingsTextCopy.versionText("v0.4.33"));
        assertEquals(1, SettingsImportPreset.boolFlag(true));
        assertEquals(0, SettingsImportPreset.boolFlag(false));
        assertEquals(0, SettingsInputRules.rankSliderProgress(-20));
        assertEquals(19999, SettingsInputRules.rankSliderProgress(50_000));
        assertEquals(1, SettingsInputRules.rankFromSliderProgress(-4));
        assertEquals(20000, SettingsInputRules.rankFromSliderProgress(50_000));
        assertEquals("Jiten ranks 10-25", SettingsTextCopy.frequencyRangeStatusText(10, 25));
        assertEquals(80, SettingsInputRules.retentionPercent(0.1));
        assertEquals(97, SettingsInputRules.retentionPercent(1.0));
        assertEquals("Desired retention: 90%", SettingsTextCopy.retentionStatusText(90));
    }

    private static void verifyImportSourceSummaries(MainActivity activity) {
        MainActivitySettingsAnkiSourceValidation validation = new MainActivitySettingsAnkiSourceValidation(activity);
        assertTrue(SettingsInputRules.validImportThresholds(7.5, 3, 2));
        assertFalse(SettingsInputRules.validImportThresholds(0.5, 3, 2));
        assertFalse(validation.hasSelectedImportSource(
                checked(activity, false),
                checked(activity, false),
                checked(activity, false),
                checked(activity, false),
                checked(activity, false),
                Collections.emptyList(),
                ""
        ));
        assertTrue(validation.hasSelectedImportSource(
                checked(activity, false),
                checked(activity, true),
                checked(activity, false),
                checked(activity, false),
                checked(activity, false),
                Collections.emptyList(),
                ""
        ));
        assertTrue(validation.hasSelectedImportSource(
                checked(activity, true),
                null,
                null,
                null,
                null,
                null,
                null
        ));
        assertTrue(validation.hasSelectedImportSource(
                checked(activity, false),
                checked(activity, true),
                null,
                null,
                null,
                null,
                null
        ));
        assertTrue(validation.hasSelectedImportSource(
                checked(activity, false),
                checked(activity, false),
                null,
                checked(activity, true),
                null,
                null,
                null
        ));
        assertTrue(validation.hasSelectedImportSource(
                checked(activity, false),
                checked(activity, false),
                checked(activity, true),
                checked(activity, false),
                checked(activity, false),
                Collections.singletonList("leeches"),
                ""
        ));
        assertTrue(validation.hasSelectedImportSource(
                checked(activity, false),
                checked(activity, false),
                checked(activity, true),
                checked(activity, false),
                null,
                Collections.singletonList("leeches"),
                null
        ));
        assertTrue(validation.hasSelectedImportSource(
                checked(activity, false),
                checked(activity, false),
                checked(activity, false),
                checked(activity, false),
                checked(activity, true),
                Collections.emptyList(),
                "deck:Kiku"
        ));
        assertEquals("3 matching cards per kanji", SettingsTextCopy.matchingCardsSummary(settings(true, true, true, Arrays.asList("leeches"), true, true, "deck:Kiku")));
        assertTrue(SettingsTextCopy.settingsImportSummary(settings(true, true, true, Arrays.asList("leeches"), true, true, "deck:Kiku")).contains("tagged"));
        assertEquals("No sources", SettingsTextCopy.settingsImportSummary(settings(false, false, false, Collections.emptyList(), false, false, "")));
    }

    private static void verifyAutoSyncSummaries(MainActivity activity) {
        LocalStore.AutoSyncSettings unconfigured = new LocalStore.AutoSyncSettings(false, true, 7, 30, 0L, 0L, 0L);
        LocalStore.AutoSyncSettings enabled = new LocalStore.AutoSyncSettings(true, true, 7, 30, 1000L, 2000L, 3000L);
        LocalStore.AutoSyncSettings disabled = new LocalStore.AutoSyncSettings(true, false, 7, 30, 1000L, 0L, 0L);
        LocalStore.AutoSyncSettings enabledNoHistory = new LocalStore.AutoSyncSettings(true, true, 7, 30, 0L, 0L, 0L);
        LocalStore.AutoSyncSettings disabledNoHistory = new LocalStore.AutoSyncSettings(true, false, 7, 30, 0L, 0L, 0L);
        assertEquals("After first sync", SettingsTextCopy.settingsAutoSyncSummary(unconfigured.configured, unconfigured.enabled, unconfigured.displayTime()));
        assertEquals("07:30", SettingsTextCopy.settingsAutoSyncSummary(enabled.configured, enabled.enabled, enabled.displayTime()));
        assertEquals("Off", SettingsTextCopy.settingsAutoSyncSummary(disabled.configured, disabled.enabled, disabled.displayTime()));
        assertEquals("Starts after first successful sync", SettingsTextCopy.autoSyncStatus(unconfigured.configured, unconfigured.enabled, unconfigured.displayTime()));
        assertEquals("On around 07:30", SettingsTextCopy.autoSyncStatus(enabled.configured, enabled.enabled, enabled.displayTime()));
        assertEquals("Off", SettingsTextCopy.autoSyncStatus(disabled.configured, disabled.enabled, disabled.displayTime()));
        assertTrue(SettingsTextCopy.autoSyncDetail(
                enabled.configured,
                enabled.enabled,
                DateTextPolicy.shortDateTime(enabled.lastSuccessAt),
                DateTextPolicy.shortDateTime(enabled.lastAttemptAt),
                DateTextPolicy.shortDateTime(enabled.nextRunAt)
        ).contains("Last auto success"));
        assertTrue(SettingsTextCopy.autoSyncDetail(
                disabled.configured,
                disabled.enabled,
                "",
                DateTextPolicy.shortDateTime(disabled.lastAttemptAt),
                ""
        ).contains("Last auto attempt"));
        assertTrue(SettingsTextCopy.autoSyncDetail(
                enabledNoHistory.configured,
                enabledNoHistory.enabled,
                "",
                "",
                ""
        ).contains("Scheduled once"));
        assertTrue(SettingsTextCopy.autoSyncDetail(
                disabledNoHistory.configured,
                disabledNoHistory.enabled,
                "",
                "",
                ""
        ).contains("paused"));
    }

    private static void verifyWorkloadAndReminderSummaries(MainActivity activity) {
        assertEquals("Pareto: up to 5 items", SettingsTextCopy.workloadStatusText(20, 5));
        assertEquals("All kanji: up to 9 items", SettingsTextCopy.workloadStatusText(100, 9));
        assertEquals("Maximum: 1 item", SettingsTextCopy.maxItemsStatusText(1));
        assertEquals("Auto Pareto: waiting for problem kanji", SettingsTextCopy.autoWorkloadStatusText(null));
        assertEquals(
                "Auto Pareto: 2 items today",
                SettingsTextCopy.autoWorkloadStatusText(new RecordsSchedulerModels.AdaptiveLoadPlan(true, 20, 2, 1, Arrays.asList("裂", "語"), 0, false, "auto"))
        );
        assertEquals("Blocked: notifications off", SettingsTextCopy.reminderStatus(true, true, "21:05"));
        assertEquals("Daily around 21:05", SettingsTextCopy.reminderStatus(true, false, "21:05"));
        assertEquals("Off", SettingsTextCopy.reminderStatus(false, false, "21:05"));
        assertEquals("21:05", SettingsTextCopy.reminderTime(21, 5));
        assertEquals("Reminder time: 21:05", SettingsTextCopy.reminderTimeButtonLabel(21, 5));
        int normalizedMax = AdaptiveLoadPlanner.normalizeMaxItems(0);
        assertEquals("Maximum: " + StudyTextCopy.countText(normalizedMax, "item", "items"), SettingsTextCopy.maxItemsStatusText(0));
    }

    private static int reminderStatusColor(boolean enabled, boolean blocked) {
        return blocked ? MainActivityBase.CORAL : (enabled ? MainActivityBase.TEAL : MainActivityBase.MUTED);
    }


    private static void appendOptionalSyncSummaryLines(MainActivity activity, LinearLayout summary, ManualSyncEngine.SyncResult result) {
        if (!result.adaptiveSummary.isEmpty()) {
            summary.addView(activity.text(result.adaptiveSummary, 15, Color.WHITE, false));
        }
        if (result.importedSuspendedKanji > 0) {
            summary.addView(activity.text(HomeTextCopy.importedSuspendedKanjiText(result.importedSuspendedKanji), 15, Color.WHITE, false));
        }
        if (result.message != null && !result.message.isEmpty()) {
            summary.addView(activity.text(result.message, 14, Color.WHITE, false));
        }
    }

    private static void verifyImportThresholdReader(MainActivity activity) {
        MainActivitySettingsAnkiSourceValidation validation = new MainActivitySettingsAnkiSourceValidation(activity);
        EditText difficulty = new EditText(activity);
        EditText lapses = new EditText(activity);
        EditText minMatching = new EditText(activity);
        difficulty.setText("not numeric");
        lapses.setText("3");
        minMatching.setText("2");
        assertNull(validation.readImportThresholds(difficulty, lapses, minMatching));
        difficulty.setText("0.5");
        assertNull(validation.readImportThresholds(difficulty, lapses, minMatching));
        difficulty.setText("7.5");
        MainActivityBase.ImportThresholds thresholds = validation.readImportThresholds(difficulty, lapses, minMatching);
        assertNotNull(thresholds);
        assertEquals(7.5, thresholds.difficulty, 0.001);
        assertEquals(3, thresholds.lapseThreshold);
        assertEquals(2, thresholds.minCards);
    }

    private static void verifyRankAndMaxItemControls(MainActivity activity) {
        assertEquals(49, SettingsInputRules.rankSliderProgress(50));
        assertEquals(50, SettingsInputRules.rankFromSliderProgress(49));
        SettingsInputRules.RankRange normalized = SettingsInputRules.normalizedRankRange(300, 20);
        assertEquals(20, normalized.minRank());
        assertEquals(300, normalized.maxRank());
    }

    @Test
    public void settingsCategoriesTogglePanelsAndReferenceNavigation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.renderSettings();
                assertEquals(1, activity.content.getChildCount());
                assertTrue(activity.content.getChildAt(0) instanceof androidx.compose.ui.platform.ComposeView);
                assertTrue(activity.settingsAnkiExpanded);
                assertFalse(activity.settingsStudyExpanded);
                assertTrue(containsText(activity.content, "Frequency range"));
                assertFalse(containsText(activity.content, "Daily workload"));
                activity.contentScroll.scrollTo(0, 48);
                activity.renderSettings(true);
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                assertEquals(48, activity.contentScroll.getScrollY());

                performClickableWithText(activity.content, "Study behavior");
                assertEquals(1, activity.content.getChildCount());
                assertTrue(activity.content.getChildAt(0) instanceof androidx.compose.ui.platform.ComposeView);
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
                performClickableWithText(activity.content, "Open data licenses");
                assertEquals(1, activity.content.getChildCount());
                assertTrue(activity.content.getChildAt(0) instanceof androidx.compose.ui.platform.ComposeView);
                assertHasText(activity, "Data licenses");
                performClickableWithText(activity.content, "Back to settings");
                assertHasText(activity, "Automation");
            });
        }
    }

    @Test
    public void settingsPanelsPersistWorkloadAndLearningStepActions() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.store.saveAdaptiveLoadMode(AdaptiveLoadPlanner.MODE_AUTO);
                View autoPanel = activity.workloadSettingsPanel();
                assertTrue(autoPanel instanceof androidx.compose.ui.platform.ComposeView);

                activity.store.saveAdaptiveLoadMode(AdaptiveLoadPlanner.MODE_MANUAL);
                View manualPanel = activity.workloadSettingsPanel();
                assertTrue(manualPanel instanceof androidx.compose.ui.platform.ComposeView);
                assertEquals(AdaptiveLoadPlanner.MODE_MANUAL, activity.store.adaptiveLoadMode());

                View stepsPanel = activity.learningStepsSettingsPanel();
                assertTrue(stepsPanel instanceof androidx.compose.ui.platform.ComposeView);
                assertEquals("1m, 10m", activity.store.learningStepSettings().reviewStepsText());
            });
        }
    }

    @Test
    public void importFilterAndFrequencyPanelsUseComposeBridges() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertTrue(activity.importFilterSettingsPanel(activity.settings()) instanceof androidx.compose.ui.platform.ComposeView);
                assertTrue(activity.frequencyRangeSettingsPanel(activity.settings()) instanceof androidx.compose.ui.platform.ComposeView);
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

                View panel = activity.noteTypeSettingsPanel(activity.settings());
                assertTrue(panel instanceof androidx.compose.ui.platform.ComposeView);
            });
        }
    }

    @Test
    public void settingsValidationPanelsPersistStudyAheadLadderRetentionAndReminder() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View studyAhead = activity.studyAheadSettingsPanel();
                assertTrue(studyAhead instanceof androidx.compose.ui.platform.ComposeView);

                View ladder = activity.ladderThresholdSettingsPanel();
                assertTrue(ladder instanceof androidx.compose.ui.platform.ComposeView);

                View ladderOrder = activity.studyLadderSettingsPanel();
                assertTrue(ladderOrder instanceof androidx.compose.ui.platform.ComposeView);
                activity.toggleLadderRung(RecordsBase.LadderRung.SIMILAR_KANJI);
                assertFalse(activity.studyLadderSettings().isEnabled(RecordsBase.LadderRung.SIMILAR_KANJI));
                activity.store.saveStudyLadderSettings(activity.studyLadderSettings().moveRung(RecordsBase.LadderRung.WORD_READING, -6));
                assertEquals(RecordsBase.LadderRung.WORD_READING, activity.studyLadderSettings().orderedRungs.get(0));

                View newCardSort = activity.newCardSortSettingsPanel(activity.settings());
                assertTrue(newCardSort instanceof androidx.compose.ui.platform.ComposeView);

                View retention = activity.retentionSettingsPanel();
                assertTrue(retention instanceof androidx.compose.ui.platform.ComposeView);

                activity.store.saveReminderSettings(new LocalStore.ReminderSettings(true, 21, 0));
                View reminder = activity.reminderSettingsPanel();
                assertTrue(reminder instanceof androidx.compose.ui.platform.ComposeView);
                assertEquals(MainActivityBase.CORAL, reminderStatusColor(true, true));
                assertEquals(MainActivityBase.TEAL, reminderStatusColor(true, false));
                assertEquals(MainActivityBase.MUTED, reminderStatusColor(false, false));

                int[] selectedHour = {21};
                int[] selectedMinute = {0};
                Button timeButtonDirect = new Button(activity);
                selectedHour[0] = 6;
                selectedMinute[0] = 5;
                timeButtonDirect.setText(SettingsTextCopy.reminderTimeButtonLabel(6, 5));
                assertEquals(6, selectedHour[0]);
                assertEquals(5, selectedMinute[0]);
                assertEquals("Reminder time: 06:05", timeButtonDirect.getText().toString());
                Intent notificationIntent = new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, activity.getPackageName());
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
                MainActivitySettingsAutomationReminder reminderHelper = new MainActivitySettingsAutomationReminder(activity);
                activity.store.saveAutoSyncSettings(new LocalStore.AutoSyncSettings(true, true, 6, 45, 1000L, 1000L, 2000L));
                View syncOn = activity.autoSyncSettingsPanel();
                assertTrue(syncOn instanceof androidx.compose.ui.platform.ComposeView);

                activity.store.setAutoSyncEnabled(false);
                View syncOff = activity.autoSyncSettingsPanel();
                assertTrue(syncOff instanceof androidx.compose.ui.platform.ComposeView);

                activity.store.recordAutoUpdateResult(1234L, "Ready to install.", "v0.5.0", "kani.apk", "");
                View missingPermission = activity.updateSettingsPanel();
                assertTrue(missingPermission instanceof androidx.compose.ui.platform.ComposeView);

                MainActivity.setInstallPermissionForTests(true);
                View readyUpdate = activity.updateSettingsPanel();
                assertTrue(readyUpdate instanceof androidx.compose.ui.platform.ComposeView);

                activity.store.saveAutoUpdateEnabled(false);
                View updateOff = activity.updateSettingsPanel();
                assertTrue(updateOff instanceof androidx.compose.ui.platform.ComposeView);

                activity.store.saveReminderSettings(new LocalStore.ReminderSettings(true, 22, 45));
                reminderHelper.saveReminderFromSelection(6, 15, false);
                LocalStore.ReminderSettings reminder = activity.store.reminderSettings();
                assertFalse(reminder.enabled);
                assertEquals(6, reminder.hour);
                assertEquals(15, reminder.minute);
                assertTrue(activity.reminderSettingsPanel() instanceof androidx.compose.ui.platform.ComposeView);
            });
        } finally {
            MainActivity.setInstallPermissionForTests(null);
        }
    }

    @Test
    public void updateUiContinuationStopsAfterNavigationAway() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.base(MainActivityBase.NAV_SETTINGS_ROUTE);
                int firstRun = ++activity.updateUiRunCounter;
                activity.activeUpdateUiRunToken = firstRun;
                assertTrue(firstRun != 0 && activity.activeUpdateUiRunToken == firstRun);

                activity.renderSettings();
                assertFalse(firstRun != 0 && activity.activeUpdateUiRunToken == firstRun);

                int staleRun = ++activity.updateUiRunCounter;
                activity.activeUpdateUiRunToken = staleRun;
                int activeRun = ++activity.updateUiRunCounter;
                activity.activeUpdateUiRunToken = activeRun;
                assertFalse(staleRun != 0 && activity.activeUpdateUiRunToken == staleRun);
                assertTrue(activeRun != 0 && activity.activeUpdateUiRunToken == activeRun);

                activity.renderHome();
                assertFalse(activeRun != 0 && activity.activeUpdateUiRunToken == activeRun);
            });
        }
    }

    @Test
    public void reminderSavingCoversPermissionRequestsAndBlockedNotifications() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                MainActivitySettingsAutomationReminder reminder = new MainActivitySettingsAutomationReminder(activity);
                MainActivity.setRuntimeNotificationPermissionForTests(false);
                reminder.saveReminderFromSelection(7, 45, true);
                assertTrue(activity.pendingReminderSettings.enabled);
                assertEquals(7, activity.pendingReminderSettings.hour);
                assertEquals(45, activity.pendingReminderSettings.minute);

                MainActivity.setRuntimeNotificationPermissionForTests(true);
                MainActivity.setNotificationsAllowedForTests(false);
                reminder.saveReminderFromSelection(8, 15, true);
                LocalStore.ReminderSettings saved = activity.store.reminderSettings();
                assertTrue(saved.enabled);
                assertEquals(8, saved.hour);
                assertEquals(15, saved.minute);
                assertTrue(activity.reminderSettingsPanel() instanceof androidx.compose.ui.platform.ComposeView);

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
                verifyHomeSyncFocusAndStreakText(activity);
                verifyStudyTimeRankAndQueueText(activity);
                verifySourceEvidenceAndEmptyQueue(activity);
                RecordsImportModels.KanjiInventoryItem inventory = new RecordsImportModels.KanjiInventoryItem("語", "language", "ゴ", "kanji:語", 2, 3, true, 1000L);
                RecordsImportModels.DashboardRow row = row("裂", "split", "レツ", Collections.emptyList());
                verifyDetailIdentityAndTimeline(activity, inventory, row);
                verifyDetailPanels(activity, inventory, row);
            });
        }
    }

    private static void verifyHomeSyncFocusAndStreakText(MainActivity activity) {
        assertEquals("Never synced", HomeTextCopy.homeSyncValue(null));
        assertEquals("", HomeTextCopy.sentenceCase(""));
        assertEquals("", HomeTextCopy.sentenceCase(null));
        assertEquals("Synced today", HomeTextCopy.sentenceCase("synced today"));

        RecordsSchedulerModels.AdaptiveLoadPlan waiting = new RecordsSchedulerModels.AdaptiveLoadPlan(20, 0, 0, Collections.emptyList(), 0, false, "");
        RecordsSchedulerModels.AdaptiveLoadPlan all = new RecordsSchedulerModels.AdaptiveLoadPlan(100, 2, 2, Arrays.asList("裂", "語"), 0, true, "all");
        RecordsSchedulerModels.AdaptiveLoadPlan focused = new RecordsSchedulerModels.AdaptiveLoadPlan(20, 4, 1, Arrays.asList("裂", "語"), 0, false, "focus");
        assertEquals("Waiting", HomeTextCopy.focusHeadline(null));
        assertEquals("Waiting", HomeTextCopy.focusHeadline(waiting));
        assertEquals("All current", HomeTextCopy.focusHeadline(all));
        assertEquals("1 items left / 4", HomeTextCopy.focusHeadline(focused));

        StudyStatsStore.StudyStreak none = new StudyStatsStore.StudyStreak(0, 0, false, 0, 0L);
        StudyStatsStore.StudyStreak doneToday = new StudyStatsStore.StudyStreak(2, 5, true, 3, 1000L);
        StudyStatsStore.StudyStreak doneNoBest = new StudyStatsStore.StudyStreak(1, 0, true, 1, 1000L);
        assertEquals("No streak yet", HomeTextCopy.streakHeadline(none.currentDays));
        assertEquals("2-day streak", HomeTextCopy.streakHeadline(doneToday.currentDays));
        assertEquals("Not done today", HomeTextCopy.streakMetricBody(none.studiedToday, none.bestDays));
        assertEquals("Best: 5 days", HomeTextCopy.streakMetricBody(doneToday.studiedToday, doneToday.bestDays));
        assertEquals("Done today", HomeTextCopy.streakMetricBody(doneNoBest.studiedToday, doneNoBest.bestDays));
        assertEquals("1 day", HomeTextCopy.streakDayCount(1));
        assertEquals("3 days", HomeTextCopy.streakDayCount(3));
    }

    private static void verifyStudyTimeRankAndQueueText(MainActivity activity) {
        assertEquals("0 sec", StatsTextCopy.formatStudyTime(-500L));
        assertEquals("59 sec", StatsTextCopy.formatStudyTime(59_000L));
        assertEquals("1 min", StatsTextCopy.formatStudyTime(60_000L));
        assertEquals("1 min 5 sec", StatsTextCopy.formatStudyTime(65_000L));
        assertEquals("1 hr", StatsTextCopy.formatStudyTime(3_600_000L));
        assertEquals("1 hr 1 min", StatsTextCopy.formatStudyTime(3_660_000L));
        assertEquals("0.38", StatsTextCopy.formatWeakness(0.375));

        assertEquals(MainActivityBase.CORAL, activity.rowColor(studyItem("裂", RecordsBase.LadderRung.KANJI_MEANING, "review", 0L), 1000L));
        assertEquals(MainActivityBase.BLUE, activity.rowColor(studyItem("裂", RecordsBase.LadderRung.KANJI_MEANING, "learning", 2000L), 1000L));
        assertNotEquals(MainActivityBase.CORAL, activity.rowColor(studyItem("裂", RecordsBase.LadderRung.KANJI_MEANING, "review", 2000L), 1000L));

        assertEquals("Needs focused kanji practice.", FocusQueueCopy.queueCardBody(rowWithReason("裂", "", "", "", Collections.emptyList())));
        assertEquals(
                "Shape mix-up made this a writing-practice target.",
                FocusQueueCopy.queueCardBody(rowWithReason("裂", "shape", "レツ", "similar-kanji miss", Collections.emptyList()))
        );
        assertEquals("custom evidence", FocusQueueCopy.queueCardBody(rowWithReason("裂", "shape", "レツ", "custom evidence", Collections.emptyList())));

        assertEquals("write kanji", FocusQueueCopy.recognitionStageLabel(studyItem("裂", RecordsBase.LadderRung.WRITE_KANJI, "review", 0L)));
        assertEquals("type meaning", FocusQueueCopy.recognitionStageLabel(studyItem("裂", RecordsBase.LadderRung.TYPE_MEANING, "review", 0L)));
        assertEquals("similar kanji", FocusQueueCopy.recognitionStageLabel(studyItem("裂", RecordsBase.LadderRung.SIMILAR_KANJI, "review", 0L)));
        assertEquals("font -> meaning", FocusQueueCopy.recognitionStageLabel(studyItem("裂", RecordsBase.LadderRung.FONT_MEANING, "review", 0L)));
        assertEquals("word -> reading", FocusQueueCopy.recognitionStageLabel(studyItem("裂", RecordsBase.LadderRung.WORD_READING, "review", 0L)));
        assertEquals("kanji -> meaning", FocusQueueCopy.recognitionStageLabel(studyItem("裂", RecordsBase.LadderRung.KANJI_MEANING, "review", 0L)));
    }

    private static void verifySourceEvidenceAndEmptyQueue(MainActivity activity) {
        RecordsImportModels.Example active = example("活動語", "カツドウゴ", "active", MainActivityBase.SOURCE_ACTIVE);
        RecordsImportModels.Example suspended = example("停止語", "テイシゴ", "suspended", MainActivityBase.SOURCE_SUSPENDED);
        assertEquals("From 活動語 · missed 停止語", FocusQueueCopy.sourceEvidenceText(row("語", "language", "ゴ", Arrays.asList(active, suspended))));
        assertEquals("From 活動語", FocusQueueCopy.sourceEvidenceText(row("語", "language", "ゴ", Collections.singletonList(active))));
        assertEquals("Missed 停止語", FocusQueueCopy.sourceEvidenceText(row("語", "language", "ゴ", Collections.singletonList(suspended))));
        assertEquals("From your AnkiDroid sync", FocusQueueCopy.sourceEvidenceText(row("語", "language", "ゴ", Collections.emptyList())));
        seedRows(activity, Collections.singletonList(row("空", "empty", "クウ", Collections.emptyList())));
        activity.renderFocusQueue();
        assertHasText(activity, MainActivityBase.EMPTY_ACTIVE_PRACTICE_TITLE);
    }

    private static void verifyDetailIdentityAndTimeline(
            MainActivity activity,
            RecordsImportModels.KanjiInventoryItem inventory,
            RecordsImportModels.DashboardRow row
    ) {
        assertEquals("裂", HomeTextCopy.detailDisplayKanji("fallback", row, inventory));
        assertEquals("語", HomeTextCopy.detailDisplayKanji("fallback", null, inventory));
        assertEquals("fallback", HomeTextCopy.detailDisplayKanji("fallback", null, null));
        assertEquals("Historical recovery", HomeTextCopy.inventoryTitle(null));
        assertEquals("Historical recovery", HomeTextCopy.inventoryTitle(new RecordsImportModels.KanjiInventoryItem("語", "", "", "", 0, 0, false, 0L)));
        assertEquals("language", HomeTextCopy.inventoryTitle(inventory));

        RecordsStudyModels.KanjiRecoveryTimeline activeTimeline = new RecordsStudyModels.KanjiRecoveryTimeline(inventory, row, studyItem("裂", RecordsBase.LadderRung.KANJI_MEANING, "review", 0L), Collections.emptyList());
        RecordsStudyModels.KanjiRecoveryTimeline restingTimeline = new RecordsStudyModels.KanjiRecoveryTimeline(inventory, row, studyItem("裂", RecordsBase.LadderRung.KANJI_MEANING, "review", System.currentTimeMillis() + 60_000L), Collections.emptyList());
        RecordsStudyModels.KanjiRecoveryTimeline retiredTimeline = new RecordsStudyModels.KanjiRecoveryTimeline(inventory, null, studyItem("裂", RecordsBase.LadderRung.KANJI_MEANING, "retired", 0L), Collections.emptyList());
        RecordsStudyModels.KanjiRecoveryTimeline noRowTimeline = new RecordsStudyModels.KanjiRecoveryTimeline(inventory, null, null, Collections.emptyList());
        MainActivityHomeBrowseDetail browseDetail = new MainActivityHomeBrowseDetail(activity);
        assertEquals("Active repair", TimelineCopy.statusText(activeTimeline, System.currentTimeMillis()));
        assertEquals("Resting until review", TimelineCopy.statusText(restingTimeline, System.currentTimeMillis()));
        assertEquals("Retired by Anki support", TimelineCopy.statusText(retiredTimeline, System.currentTimeMillis()));
        assertEquals("Retired by Anki support", TimelineCopy.statusText(noRowTimeline, System.currentTimeMillis()));
        assertEquals(MainActivityBase.TEAL, browseDetail.timelineToneColor(TimelineCopy.statusTone(retiredTimeline, System.currentTimeMillis())));
        assertEquals(MainActivityBase.BLUE, browseDetail.timelineToneColor(TimelineCopy.statusTone(restingTimeline, System.currentTimeMillis())));
        assertEquals(MainActivityBase.CORAL, browseDetail.timelineToneColor(TimelineCopy.statusTone(activeTimeline, System.currentTimeMillis())));
        assertEquals(MainActivityBase.CORAL, browseDetail.timelineToneColor(TimelineCopy.eventTone("review_failed")));
        assertEquals(MainActivityBase.TEAL, browseDetail.timelineToneColor(TimelineCopy.eventTone("review_passed")));
        assertEquals(MainActivityBase.BLUE, browseDetail.timelineToneColor(TimelineCopy.eventTone("sync")));
        assertEquals("", TimelineCopy.sourceLine(event("", "")));
        assertEquals("Source: 活動語", TimelineCopy.sourceLine(event("活動語", "")));
        assertEquals("Source: 活動語  カツドウゴ", TimelineCopy.sourceLine(event("活動語", "カツドウゴ")));
    }

    private static void verifyDetailPanels(
            MainActivity activity,
            RecordsImportModels.KanjiInventoryItem inventory,
            RecordsImportModels.DashboardRow row
    ) {
        MainActivityHomeBrowseDetail browseDetail = new MainActivityHomeBrowseDetail(activity);
        BrowseDetailIdentityModel inventoryIdentity = browseDetail.detailIdentityModel(null, inventory, false);
        assertEquals("language", inventoryIdentity.getTitle());
        assertEquals("ゴ", inventoryIdentity.getReading());

        BrowseDetailIdentityModel historicalIdentity = browseDetail.detailIdentityModel(
                null,
                new RecordsImportModels.KanjiInventoryItem("謎", "", "", "", 0, 0, false, 0L),
                false
        );
        assertEquals("Historical recovery", historicalIdentity.getTitle());
        assertEquals("", historicalIdentity.getReading());

        BrowseDetailPanelModel inventoryReason = browseDetail.detailReasonPanelModel(null, inventory);
        assertTrue(inventoryReason.getLines().contains("This kanji is no longer in the active Anki evidence set, but Kani kept its local recovery history."));
        assertTrue(inventoryReason.getLines().contains("Anki browser: kanji:語"));

        BrowseDetailPanelModel historicalReason = browseDetail.detailReasonPanelModel(null, null);
        assertTrue(historicalReason.getLines().contains("This kanji is no longer in the active Anki evidence set, but Kani kept its local recovery history."));
        assertFalse(historicalReason.getLines().toString().contains("Anki browser:"));

        BrowseDetailPanelModel activeReason = browseDetail.detailReasonPanelModel(row, inventory);
        assertTrue(activeReason.getLines().contains("reason text"));
        assertTrue(activeReason.getLines().contains("Anki browser: 裂"));
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
                assertHasText(activity, "Automation");

                activity.fullWidthHomeButton().performClick();
                assertHasText(activity, "Kani");
            });
        }
    }

    @Test
    public void renderHomeUsesSingleComposeScreenForEmptyAndActiveStates() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.renderHome();
                assertEquals(1, activity.content.getChildCount());
                assertTrue(activity.content.getChildAt(0) instanceof androidx.compose.ui.platform.ComposeView);
                assertHasText(activity, HomeTextCopy.noKanjiQueuedTitle());
                assertHasText(activity, HomeTextCopy.syncAnkiDroidLabel());

                seedRows(activity, Collections.singletonList(row("裂", "split", "レツ", Collections.emptyList())));
                activity.renderHome();
                assertEquals(1, activity.content.getChildCount());
                assertTrue(activity.content.getChildAt(0) instanceof androidx.compose.ui.platform.ComposeView);
                assertHasText(activity, MainActivityBase.LABEL_STUDY_NOW);
                assertHasText(activity, HomeTextCopy.viewAllLabel() + " >");
                assertHasText(activity, "裂");
                assertHasText(activity, "split");
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
                assertTrue(activity.content.getChildAt(0) instanceof androidx.compose.ui.platform.ComposeView);
                assertHasText(activity, "Sync already running");
                assertHasText(activity, "Already syncing.");
                activity.renderSyncResult(syncResult(false, true, 0, 0, "", ""));
                assertHasText(activity, "Kani is already reading AnkiDroid.");

                activity.renderSyncResult(syncResult(false, false, 0, 0, "Provider unavailable.", ""));
                assertTrue(activity.content.getChildAt(0) instanceof androidx.compose.ui.platform.ComposeView);
                assertHasText(activity, "Sync needs attention");
                assertHasText(activity, "Provider unavailable.");
                activity.renderSyncResult(syncResult(false, false, 0, 0, "", ""));
                assertHasText(activity, "Try again after checking AnkiDroid permissions.");

                activity.renderSyncResult(syncResult(true, false, 0, 2, "Cleanup finished.", "Auto Pareto: 2 items today"));
                assertTrue(activity.content.getChildAt(0) instanceof androidx.compose.ui.platform.ComposeView);
                assertHasText(activity, "Sync complete");
                assertHasText(activity, "Cleanup finished.");

                LinearLayout summary = new LinearLayout(activity);
                appendOptionalSyncSummaryLines(activity, summary, syncResult(true, false, 1, 2, "Done.", "Focus summary"));
                assertEquals(3, summary.getChildCount());
                assertEquals("fallback", activity.nonEmptyOr("", "fallback"));
                assertEquals("value", activity.nonEmptyOr("value", "fallback"));
            });
        }
    }

    @Test
    public void gamesHostPathsRenderComposeResultAndUnavailableStates() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.renderGames();
                assertEquals(1, activity.content.getChildCount());
                assertTrue(activity.content.getChildAt(0) instanceof androidx.compose.ui.platform.ComposeView);

                activity.startGame(KanjiGameEngine.GameMode.MEANING_POP);
                assertHasText(activity, "Game not ready");
                assertEquals(1, activity.content.getChildCount());
                assertTrue(activity.content.getChildAt(0) instanceof androidx.compose.ui.platform.ComposeView);

                seedRows(activity, Arrays.asList(
                        row("裂", "split", "レツ", Collections.emptyList()),
                        row("語", "language", "ゴ", Collections.emptyList())
                ));
                activity.startGame(KanjiGameEngine.GameMode.MEANING_POP);
                assertEquals(1, activity.content.getChildCount());
                assertTrue(activity.content.getChildAt(0) instanceof androidx.compose.ui.platform.ComposeView);
                assertHasText(activity, "Pick the meaning");
                performClickableWithText(activity.content, "split");
                assertContainsText(activity.content, "Answer:");
                assertEquals(1, activity.content.getChildCount());
                assertTrue(activity.content.getChildAt(0) instanceof androidx.compose.ui.platform.ComposeView);
                performClickableWithText(activity.content, "Next");
                assertHasText(activity, "Pick the meaning");

                activity.startGame(KanjiGameEngine.GameMode.MEANING_POP);
                performClickableWithText(activity.content, "language");
                assertContainsText(activity.content, "Answer:");
                performClickableWithText(activity.content, "Games");
                assertHasText(activity, "Games");
            });
        }
    }

    @Test
    public void studyRenderAndProgressHelpersCoverTerminalStudyStates() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                RecordsSchedulerModels.AdaptiveLoadPlan dueLater = new RecordsSchedulerModels.AdaptiveLoadPlan(20, 3, 2, Arrays.asList("裂", "語"), 0, false, "Two left");
                RecordsSchedulerModels.AdaptiveLoadPlan complete = new RecordsSchedulerModels.AdaptiveLoadPlan(20, 3, 0, Arrays.asList("裂", "語"), 0, false, "Done");
                long now = System.currentTimeMillis();

                verifyTerminalStudyScreens(activity, dueLater, complete);
                verifyStudyMoreNewCardRequests(activity, now);
                RecordsSchedulerModels.StudySession session = verifyStudyRunProgressTracking(activity, dueLater);
                verifyActiveStudyTaskTracking(activity);
                verifyTargetedStudyHelpers(activity, session, now);
                verifySimilarKanjiChoiceBuilding(activity, now);
            });
        }
    }

    private static void verifyTerminalStudyScreens(
            MainActivity activity,
            RecordsSchedulerModels.AdaptiveLoadPlan dueLater,
            RecordsSchedulerModels.AdaptiveLoadPlan complete
    ) {
        activity.renderEmptyStudyQueue();
        assertHasText(activity, "Nothing to study yet");
        activity.renderNoStudySession(dueLater);
        assertHasText(activity, "Nothing due now");
        performClickableWithText(activity.content, MainActivityBase.LABEL_BACK_HOME);
        assertHasText(activity, "Kani");
        activity.renderFocusDone(complete);
        assertHasText(activity, "Today's focus done");
        assertHasText(activity, "Today's focus: 0 items left / 3");
        activity.studySessionTracker.setTargetCount(3);
        activity.markStudyTaskCompleted("done:one");
        activity.markStudyTaskCompleted("done:two");
        activity.renderStudyRunDone(dueLater);
        assertHasText(activity, "Study now: 2 / 3");
        activity.renderStudyForKanji("謎");
        assertHasText(activity, "Kanji not available");
    }

    private static void verifyStudyMoreNewCardRequests(MainActivity activity, long now) {
        assertFalse(activity.startStudyMoreNewCards(2));
        EditText requested = new EditText(activity);
        requested.setText("not a number");
        assertEquals(-1, activity.requestedStudyMoreNewCards(requested));
        requested.setText("0");
        assertEquals(-1, activity.requestedStudyMoreNewCards(requested));
        assertFalse(activity.applyStudyMoreNewCardsRequest(requested));
        requested.setText("3");
        assertEquals(3, activity.requestedStudyMoreNewCards(requested));

        RecordsImportModels.DashboardRow unavailable = row("余", "extra", "ヨ", Collections.emptyList());
        seedRows(activity, Collections.singletonList(unavailable));
        activity.store.saveStudyItem(studyItem("余", RecordsBase.LadderRung.KANJI_MEANING, "review", now));
        assertFalse(activity.startStudyMoreNewCards(2));

        seedRows(activity, Collections.singletonList(row("新", "new", "シン", Collections.emptyList())));
        EditText extraRequest = new EditText(activity);
        extraRequest.setText("3");
        assertTrue(activity.applyStudyMoreNewCardsRequest(extraRequest));
        assertEquals(1, activity.studySessionTracker.targetCount());
        assertTrue(activity.studyMoreNewCardKanji.contains("新"));
    }

    private static RecordsSchedulerModels.StudySession verifyStudyRunProgressTracking(
            MainActivity activity,
            RecordsSchedulerModels.AdaptiveLoadPlan dueLater
    ) {
        activity.resetStudyRunProgress();
        assertEquals(0, activity.studySessionTracker.completedCount());
        assertEquals(0, activity.studySessionTracker.targetCount());
        assertFalse(activity.studySessionTracker.atHardCap(activity.continueAllKanjiSession));
        activity.initializeSessionProgressTarget(dueLater);
        assertEquals(2, activity.studySessionTracker.targetCount());
        activity.markStudyTaskCompleted("cap:one");
        activity.markStudyTaskCompleted("cap:two");
        assertTrue(activity.studySessionTracker.atHardCap(activity.continueAllKanjiSession));
        activity.continueAllKanjiSession = true;
        assertFalse(activity.studySessionTracker.atHardCap(activity.continueAllKanjiSession));
        activity.clearStudyModeOverrides();
        assertFalse(activity.continueAllKanjiSession);

        activity.resetStudyRunProgress();
        assertEquals("", activity.sessionTaskKey(null));
        RecordsSchedulerModels.StudySession session = session("裂", BridgeScheduler.TASK_KANJI_MEANING, row("裂", "split", "レツ", Collections.emptyList()));
        assertTrue(activity.sessionTaskKey(session).contains("session:kanji_meaning:裂"));
        activity.registerStudyTaskShown("");
        activity.registerStudyTaskShown("task:one");
        assertEquals(1, activity.studySessionTracker.targetCount());
        activity.markStudyTaskCompleted("");
        activity.markStudyTaskCompleted("task:one");
        activity.markStudyTaskCompleted("task:one");
        assertEquals(1, activity.studySessionTracker.completedCount());
        activity.activeSession = null;
        activity.markStudyRunPassed("語");
        assertEquals(2, activity.studySessionTracker.completedCount());
        activity.activeSession = session;
        activity.markStudyRunPassed("");
        assertEquals(3, activity.studySessionTracker.completedCount());
        return session;
    }

    private static void verifyActiveStudyTaskTracking(MainActivity activity) {
        activity.abandonActiveStudyTask();
        activity.startActiveStudyTask("", "裂", BridgeScheduler.TASK_KANJI_MEANING, 1000L);
        assertFalse(activity.studySessionTracker.hasActiveTask());
        activity.startActiveStudyTask("task:active", "裂", BridgeScheduler.TASK_KANJI_MEANING, 1000L);
        assertTrue(activity.studySessionTracker.hasActiveTask());
        activity.startActiveStudyTask("task:active", "裂", BridgeScheduler.TASK_KANJI_MEANING, 1000L);
        activity.pauseActiveStudyTask();
        activity.resumeActiveStudyTask();
        activity.completeActiveStudyTask("wrong", "missed", 2000L);
        assertTrue(activity.studySessionTracker.hasActiveTask());
        activity.completeActiveStudyTask("task:active", "passed", 2000L);
        assertFalse(activity.studySessionTracker.hasActiveTask());
        int completedBeforeRepair = activity.studySessionTracker.completedCount();
        activity.startActiveStudyTask("repair:active", "裂", BridgeScheduler.TASK_KANJI_MEANING, 3000L);
        assertTrue(activity.studySessionTracker.hasActiveTask());
        activity.completeActiveRepairStudyTask("repair:active", "passed", 4000L);
        assertFalse(activity.studySessionTracker.hasActiveTask());
        assertEquals(completedBeforeRepair, activity.studySessionTracker.completedCount());
        activity.abandonActiveStudyTask();
    }

    private static void verifyTargetedStudyHelpers(
            MainActivity activity,
            RecordsSchedulerModels.StudySession session,
            long now
    ) {
        RecordsStudyModels.StudyItem targeted = new BridgeScheduler().newTargetedStudyItem("謎", 1234L, activity.studyLadderSettings());
        assertEquals("謎", targeted.kanji);
        assertEquals("new", targeted.state);
        assertEquals(1234L, targeted.dueAtMillis);
        RecordsStudyModels.StudyItem existingTarget = studyItem("裂", RecordsBase.LadderRung.KANJI_MEANING, "review", now);
        assertSame(existingTarget, new BridgeScheduler().targetedStudyItem(Collections.singletonList(existingTarget), session.item.kanji, now, activity.studyLadderSettings()));
        assertEquals("new", new BridgeScheduler().targetedStudyItem(Collections.emptyList(), "謎", 1234L, activity.studyLadderSettings()).state);
        assertEquals(activity.dp(300), activity.studyPadHeightForScreenDp(699));
        assertEquals(activity.dp(340), activity.studyPadHeightForScreenDp(700));
        assertEquals(activity.dp(390), activity.studyPadHeightForScreenDp(820));
    }

    private static void verifySimilarKanjiChoiceBuilding(MainActivity activity, long now) {
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
    }

    @Test
    public void studyDoneActionsStudyMoreAndFallbackPanelsExerciseRealUiBranches() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                seedRows(activity, Arrays.asList(
                        row("裂", "split", "レツ", Collections.emptyList()),
                        row("語", "language", "ゴ", Collections.emptyList())
                ));
                RecordsSchedulerModels.AdaptiveLoadPlan complete = new RecordsSchedulerModels.AdaptiveLoadPlan(20, 2, 0, Arrays.asList("裂", "語"), 0, false, "Done");

                activity.renderFocusDone(complete);
                assertHasText(activity, "Study more new cards");
                performClickableWithText(activity.content, "Study more new cards");
                assertHasText(activity, "How many extra new cards do you want to study now?");
                performClickableWithText(activity.content, "Cancel");
                performClickableWithText(activity.content, MainActivityBase.LABEL_CONTINUE_ALL_KANJI);
                assertTrue(activity.continueAllKanjiSession);
                activity.renderFocusDone(complete);
                performClickableWithText(activity.content, MainActivityBase.LABEL_BACK_HOME);
                assertFalse(activity.continueAllKanjiSession);
                assertHasText(activity, "Kani");

                activity.resetStudyRunProgress();
                activity.studySessionTracker.setTargetCount(2);
                activity.markStudyTaskCompleted("continue:one");
                activity.renderStudyRunDone(complete);
                performClickableWithText(activity.content, MainActivityBase.LABEL_CONTINUE_ALL_KANJI);
                assertTrue(activity.continueAllKanjiSession);
                activity.renderStudyRunDone(null);
                performClickableWithText(activity.content, MainActivityBase.LABEL_BACK_HOME);
                assertFalse(activity.continueAllKanjiSession);

                int available = activity.availableStudyMoreNewCards();
                if (available > 0) {
                    assertTrue(activity.startStudyMoreNewCards(5));
                    assertTrue(activity.studySessionTracker.targetCount() <= 5);
                    assertFalse(activity.studyMoreNewCardKanji.isEmpty());
                }

                RecordsSchedulerModels.StudySession promptOnly = new RecordsSchedulerModels.StudySession(
                        studyItem("?", RecordsBase.LadderRung.KANJI_MEANING, "review", 0L),
                        null,
                        "answer-token",
                        BridgeScheduler.TASK_KANJI_MEANING,
                        false,
                        "Prompt fallback"
                );
                View promptAnswerPanel = activity.flashcardAnswerPanel(promptOnly);
                activity.content.addView(promptAnswerPanel);
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                assertHasText(activity, "Prompt fallback");
                activity.content.removeView(promptAnswerPanel);
                assertEquals("split", StudyTextCopy.collectionMeaningForSession(session("裂", BridgeScheduler.TASK_KANJI_MEANING, row("裂", "split", "レツ", Collections.emptyList()))));
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
                RecordsImportModels.DashboardRow row = row("裂", "split", "レツ", Collections.emptyList());
                assertNull(activity.firstExample(null));
                assertNull(activity.wordReadingExample(null));

                RecordsSchedulerModels.StudySession promptOnly = new RecordsSchedulerModels.StudySession(
                        studyItem("?", RecordsBase.LadderRung.WRITE_KANJI, "review", 0L),
                        null,
                        "prompt-token",
                        BridgeScheduler.TASK_WRITE_KANJI,
                        true,
                        "Prompt only"
                );
                assertTrue(containsText(activity.learningPanel(promptOnly), "Prompt only"));
                assertTrue(containsText(activity.heroKanjiPanel(session("裂", BridgeScheduler.TASK_FONT_MEANING, row)), "裂"));
                assertNotNull(activity.randomFontVariantTypeface());

                activity.renderSimilarKanjiSession(session("裂", BridgeScheduler.TASK_SIMILAR_KANJI, row));
                assertNotNull(activity.flashcardCard);
                assertSame(activity.flashcardCard, activity.flashcardGestureArea);
                assertFalse(activity.flashcardAnswerRevealed);
                activity.store.rebuildSimilarKanjiPairs(similarIndex("裂\t列\n裂\t烈\n"), System.currentTimeMillis());
                activity.renderSimilarKanjiSession(session("裂", BridgeScheduler.TASK_SIMILAR_KANJI, row));
                assertTrue(activity.content.getChildAt(1) instanceof androidx.compose.ui.platform.ComposeView);
                assertNull(activity.flashcardGestureArea);
                assertFalse(activity.flashcardAnswerRevealed);
                seedRows(activity, Arrays.asList(
                        row("裂", "split", "レツ", Collections.emptyList()),
                        row("列", "row", "レツ", Collections.emptyList()),
                        row("烈", "ardent", "レツ", Collections.emptyList()),
                        row("劣", "inferior", "レツ", Collections.emptyList())
                ));
                activity.renderMeaningKanjiSession(session("裂", BridgeScheduler.TASK_MEANING_KANJI, row));
                assertTrue(activity.content.getChildAt(1) instanceof androidx.compose.ui.platform.ComposeView);
                assertNull(activity.flashcardGestureArea);
                assertFalse(activity.flashcardAnswerRevealed);
                performClickableWithText(activity.content, "裂");
                assertHasText(activity, "Correct");
                assertEquals(View.VISIBLE, activity.studyActionBar.getVisibility());
                assertEquals(1, activity.studyActionBar.getChildCount());
                assertTrue(activity.studyActionBar.getChildAt(0) instanceof androidx.compose.ui.platform.ComposeView);
                assertTrue(containsText(activity.studyActionBar, "Next"));
                assertEquals(0, activity.store.reviewStatsSince(0L).total);
                performClickableWithText(activity.studyActionBar, "Next");
                assertEquals(1, activity.store.reviewStatsSince(0L).good);

                activity.renderMeaningKanjiSession(session("返", BridgeScheduler.TASK_MEANING_KANJI, row("返", "return", "ヘン", Collections.emptyList())));
                assertNotNull(activity.flashcardCard);
                assertSame(activity.flashcardCard, activity.flashcardGestureArea);

                RecordsSchedulerModels.StudySession recall = session("裂", "blind_writing", row);
                activity.activeSession = recall;
                activity.renderWritingSession(recall);
                assertTrue(activity.content.getChildAt(1) instanceof androidx.compose.ui.platform.ComposeView);
                assertHasText(activity, "Prompt: Split, rend");
                assertTrue(activity.studyActionBar.getChildAt(0) instanceof WritingToolActionsView);
                assertTrue(activity.studyActionBar.getChildAt(1) instanceof WritingPrimaryActionsView);
                assertTrue(activity.studyActionBar.getChildAt(2) instanceof WritingFallbackActionsView);
                assertTrue(activity.drawingPad.getParent() instanceof MainActivityUiSupport.SquarePadFrame);
                assertTrue(activity.writingResultStatus.view() instanceof WritingStatusView);
                performClickableWithText(activity.studyActionBar, "Erase");

                activity.activeSession = promptOnly;
                activity.renderWritingSession(promptOnly);
                assertHasText(activity, "Prompt only");

                RecordsSchedulerModels.StudySession nullPromptOnly = new RecordsSchedulerModels.StudySession(
                        studyItem("?", RecordsBase.LadderRung.WRITE_KANJI, "review", 0L),
                        null,
                        "null-prompt-token",
                        BridgeScheduler.TASK_WRITE_KANJI,
                        true,
                        null
                );
                activity.activeSession = nullPromptOnly;
                activity.renderWritingSession(nullPromptOnly);
                assertHasText(activity, "Draw this kanji");

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
                activity.studyStatus = new WritingStatusView(activity);
                prepareWritingActionViews(activity);
                new MainActivityStudyWritingStatus(activity).downloadWritingModel();
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
                RecordsImportModels.DashboardRow row = row("裂", "split", "レツ", Collections.emptyList());
                RecordsSchedulerModels.StudySession writing = session("裂", BridgeScheduler.TASK_WRITE_KANJI, row);
                activity.activeSession = writing;
                activity.currentHintState = HintState.fromWritingLevel(1);
                activity.studyStatus = new WritingStatusView(activity);
                activity.writingResultStatus = new WritingResultStatusHandle(activity);
                prepareWritingActionViews(activity);
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
                new MainActivityStudyWritingStatus(activity).downloadWritingModel();
                activity.activeSession = sessionWithToken("裂", BridgeScheduler.TASK_WRITE_KANJI, row, "changed-token");
            });
            scenario.onActivity(activity -> {
                assertNull(activity.activeAnalysis);
                FakeWritingRecognizer errorRecognizer = new FakeWritingRecognizer(
                        CompletableFuture.completedFuture(new WritingRecognizer.ModelStatus("ja", "ja-JP", true, "ready")),
                        CompletableFuture.completedFuture(new WritingRecognizer.ModelStatus("ja", "ja-JP", true, "ready")),
                        failedFuture(new RuntimeException("recognition failed"))
                );
                RecordsImportModels.DashboardRow row = row("裂", "split", "レツ", Collections.emptyList());
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
                RecordsImportModels.DashboardRow row = row("裂", "split", "レツ", Collections.emptyList());
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
                new MainActivityStudyWritingStatus(activity).refreshWritingModelStatus();
                assertTrue(activity.studyStatus.getText().toString().contains("Automatic handwriting checks are unavailable"));
                new MainActivityStudyWritingStatus(activity).downloadWritingModel();
                assertTrue(activity.studyStatus.getText().toString().contains("unavailable on this device"));

                MainActivity.setWritingRecognizerFactoryForTests(executor -> {
                    throw new RuntimeException("ml kit unavailable");
                });
                activity.writingRecognizer = null;
                assertNull(activity.currentWritingRecognizer());

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
                            new RecordsSchedulerModels.ReviewRequest("調" + i, "tune-token-" + i, MainActivityBase.RATING_GOOD, false, false, false, 0),
                            MainActivityBase.RATING_GOOD,
                            now - (i * 1000L)
                    );
                }
                RecordsSchedulerModels.SchedulerParameters before = RecordsSchedulerModels.SchedulerParameters.defaults();

                activity.tuneSchedulerIfNeeded(before, now);

                RecordsSchedulerModels.SchedulerParameters tuned = activity.store.schedulerParameters();
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
                RecordsImportModels.DashboardRow row = row("裂", "split", "レツ", Collections.emptyList());
                RecordsSchedulerModels.StudySession flashcard = session("裂", BridgeScheduler.TASK_KANJI_MEANING, row);
                RecordsSchedulerModels.StudySession writing = session("裂", BridgeScheduler.TASK_WRITE_KANJI, row);
                activity.activeSession = flashcard;

                verifyTokenCandidatesAndReviewToasts(activity);
                verifyFlashcardActionBarAndGestureBranches(activity, writing);
                prepareWritingControls(activity, writing);
                StrokeOrderEvaluator.StrokeOrderResult order = StrokeOrderEvaluator
                        .evaluate(guide("裂"), sample())
                        .withDiagnosis(StrokeDiagnosis.builder().add(StrokeDiagnosis.Label.WRONG_ORDER, 1).build());
                WritingAnalysis wrong = analysis(WritingAnalysis.Status.WRONG, false, order);
                verifyWritingButtonAndModelStatus(activity, wrong);
                verifyTeachingHintAndHelpState(activity, writing, row, order);
            });
        }
    }

    private static void verifyTokenCandidatesAndReviewToasts(MainActivity activity) {
        assertTrue(activity.isActiveToken("tok"));
        assertFalse(activity.isActiveToken("missing"));
        assertEquals("", WritingFeedbackCopy.candidateText(null));
        assertEquals(
                "裂, 列, 烈",
                WritingFeedbackCopy.candidateText(Arrays.asList(
                        new RecognitionCandidate("裂", 0.9f),
                        new RecognitionCandidate("列", 0.5f),
                        new RecognitionCandidate("烈", 0.4f),
                        new RecognitionCandidate("劣", 0.3f)
                ))
        );
        assertEquals(2, activity.candidates(new WritingRecognizer.RecognitionResult(Arrays.asList(
                new WritingRecognizer.Candidate("裂", 0.9f),
                new WritingRecognizer.Candidate("列", null)
        ))).size());
        assertTrue(activity.candidates(null).isEmpty());

        RecordsStudyModels.StudyItem item = studyItem("裂", RecordsBase.LadderRung.KANJI_MEANING, "review", 0L);
        StudyStatsStore.StudyStreak streak = new StudyStatsStore.StudyStreak(2, 2, true, 1, 1000L);
        assertEquals("Already saved.", HomeTextCopy.reviewToast(true, "duplicate", streak.currentDays));
        assertTrue(HomeTextCopy.reviewToast(false, MainActivityBase.RATING_AGAIN, streak.currentDays).contains("2-day streak"));
        assertEquals("Saved.", HomeTextCopy.reviewToast(false, MainActivityBase.RATING_GOOD, 0));
    }

    private static void verifyFlashcardActionBarAndGestureBranches(
            MainActivity activity,
            RecordsSchedulerModels.StudySession writing
    ) {
        activity.studyActionBar = null;
        activity.buildFlashcardActionBar(false);
        activity.studyActionBar = new LinearLayout(activity);
        activity.buildFlashcardActionBar(false);
        assertEquals(1, activity.studyActionBar.getChildCount());
        activity.buildFlashcardActionBar(true);
        assertEquals(1, activity.studyActionBar.getChildCount());
        activity.flashcardAnswerRevealed = true;
        activity.revealFlashcardAnswer();
        activity.flashcardCard = null;
        activity.expandFlashcardForAnswer();

        assertFalse(activity.handleFlashcardGesture(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 1f, 1f, 0)));
        activity.activeSession = writing;
        assertEquals(MainActivityBase.LABEL_PRACTICE, StudyTaskCopy.studyModeLabel(writing));
        assertFalse(activity.handleFlashcardGesture(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 1f, 1f, 0)));
    }

    private static void prepareWritingControls(MainActivity activity, RecordsSchedulerModels.StudySession writing) {
        activity.activeSession = writing;
        activity.currentHintState = HintState.fromWritingLevel(3);
        activity.studyStatus = new WritingStatusView(activity);
        activity.writingResultStatus = new WritingResultStatusHandle(activity);
        prepareWritingActionViews(activity);
        activity.studyAnswerPanel = new LinearLayout(activity);
    }

    private static void verifyWritingButtonAndModelStatus(MainActivity activity, WritingAnalysis wrong) {
        MainActivityStudyWritingStatus writingStatus = new MainActivityStudyWritingStatus(activity);
        activity.checkingWriting = true;
        activity.updateResultActions();
        WritingPrimaryActionsModel primary = activity.writingPrimaryActionsView.currentModelForTests();
        assertEquals("Checking...", primary.getCheckText());
        assertFalse(primary.getCheckEnabled());
        activity.checkingWriting = false;
        activity.activeAnalysis = new WritingAnalysis(
                WritingAnalysis.Status.CLOSE,
                MainActivityBase.RATING_HARD,
                true,
                "Messy",
                Collections.emptyList(),
                wrong.strokeOrder
        );
        activity.updateResultActions();
        primary = activity.writingPrimaryActionsView.currentModelForTests();
        assertEquals("Try cleaner", primary.getCheckText());
        assertTrue(primary.getCheckEnabled());

        activity.writingModelStatusKnown = true;
        activity.writingModelDownloaded = true;
        activity.updateResultActions();
        primary = activity.writingPrimaryActionsView.currentModelForTests();
        assertFalse(primary.getDownloadVisible());
        activity.activeAnalysis = wrong;
        activity.updateResultActions();
        primary = activity.writingPrimaryActionsView.currentModelForTests();
        assertTrue(primary.getNextVisible());
        assertEquals("Fail", primary.getNextText());

        WritingFallbackActionsModel fallback = activity.writingFallbackActionsView.currentModelForTests();
        assertTrue(fallback.getManualOverrideVisible());
        assertTrue(fallback.getPracticeWithGuideVisible());
        activity.showModelUnavailable("checker unavailable");
        assertTrue(activity.writingResultStatus.getText().toString().contains("checker unavailable"));
        assertEquals(View.VISIBLE, activity.writingResultStatus.getVisibility());

        writingStatus.setWritingModelStatusMessage(null, new RuntimeException("offline"));
        assertTrue(activity.studyStatus.getText().toString().contains("Unable to read"));
        writingStatus.setWritingModelStatusMessage(new WritingRecognizer.ModelStatus("ja", "ja-JP", false, "missing"), null);
        assertTrue(activity.studyStatus.getText().toString().contains("Download the handwriting checker"));
        writingStatus.setWritingModelStatusMessage(new WritingRecognizer.ModelStatus("ja", "ja-JP", true, "ready"), null);
        assertTrue(activity.studyStatus.getText().toString().contains("Handwriting checker ready"));
    }

    private static void verifyTeachingHintAndHelpState(
            MainActivity activity,
            RecordsSchedulerModels.StudySession writing,
            RecordsImportModels.DashboardRow row,
            StrokeOrderEvaluator.StrokeOrderResult order
    ) {
        activity.activeAnalysis = null;
        assertTrue(activity.showNoInkWhenNeeded());
        assertEquals(WritingAnalysis.Status.NO_INK, activity.activeAnalysis.status);
        assertFalse(StudyTaskCopy.isTeachingTask(null));
        assertTrue(StudyTaskCopy.isTeachingTask(session("裂", "context_writing", row)));
        assertTrue(StudyTaskCopy.isTeachingTask(session("裂", "guided_writing", row)));
        assertTrue(StudyTaskCopy.isTeachingTask(session("裂", MainActivityBase.TASK_TARGETED_WRITING, row)));
        assertFalse(StudyTaskCopy.isTeachingTask(new RecordsSchedulerModels.StudySession(
                studyItem("裂", RecordsBase.LadderRung.WRITE_KANJI, "review", 0L).copyBuilder().learningStep(3).build(),
                row,
                "tok-target-done",
                MainActivityBase.TASK_TARGETED_WRITING,
                true,
                "split"
        )));
        assertEquals(HintLevel.OUTLINE, activity.initialHintState(session("裂", MainActivityBase.TASK_TARGETED_WRITING, row)).level());
        assertEquals(HintLevel.OUTLINE, activity.initialHintState(new RecordsSchedulerModels.StudySession(
                studyItem("裂", RecordsBase.LadderRung.WRITE_KANJI, "review", 0L).copyBuilder().totalReviews(0).writingLevel(3).build(),
                row,
                "tok-new",
                BridgeScheduler.TASK_WRITE_KANJI,
                true,
                "split"
        )).level());
        assertEquals(HintLevel.BLIND, activity.initialHintState(new RecordsSchedulerModels.StudySession(
                studyItem("裂", RecordsBase.LadderRung.WRITE_KANJI, "review", 0L).copyBuilder().learningStep(2).writingLevel(3).build(),
                row,
                "tok-mature",
                BridgeScheduler.TASK_WRITE_KANJI,
                true,
                "split"
        )).level());
        HintState nextHintState = HintState.fromWritingLevel(2);
        activity.currentPracticeLevel = 99;
        activity.setHintState(nextHintState);
        assertEquals(nextHintState, activity.currentHintState);
        assertEquals(nextHintState.level().writingLevel(), activity.currentPracticeLevel);
        activity.setHintState(null);
        assertEquals(HintState.initial(), activity.currentHintState);
        assertEquals(HintState.initial().level().writingLevel(), activity.currentPracticeLevel);
        verifyHelpAndLearningPanelState(activity, writing, row, order);
    }

    private static void verifyHelpAndLearningPanelState(
            MainActivity activity,
            RecordsSchedulerModels.StudySession writing,
            RecordsImportModels.DashboardRow row,
            StrokeOrderEvaluator.StrokeOrderResult order
    ) {
        activity.activeSession = null;
        assertFalse(activity.canRevealMoreHelp());
        activity.activeSession = writing;
        activity.currentHintState = HintState.fromWritingLevel(0);
        assertFalse(activity.canRevealMoreHelp());
        activity.currentHintState = HintState.fromWritingLevel(1);
        assertTrue(activity.canRevealMoreHelp());
        activity.activeSession = session("裂", "blind_writing", row);
        activity.currentPracticeLevel = 1;
        assertFalse(WritingFeedbackCopy.shouldShowLearningPanel(null, true, false, activity.currentPracticeLevel));
        activity.activeAnalysis = null;
        assertFalse(activity.writingActionPresentation().answerPanelVisible);
        activity.activeSession = session("裂", "context_writing", row);
        assertTrue(WritingFeedbackCopy.shouldShowLearningPanel(null, false, true, activity.currentPracticeLevel));
        assertTrue(activity.writingActionPresentation().answerPanelVisible);
        activity.currentPracticeLevel = 3;
        assertFalse(WritingFeedbackCopy.shouldShowLearningPanel(null, false, true, activity.currentPracticeLevel));
        assertFalse(activity.writingActionPresentation().answerPanelVisible);
        assertTrue(WritingFeedbackCopy.shouldShowLearningPanel(
                analysis(WritingAnalysis.Status.PASS, true, order),
                false,
                true,
                activity.currentPracticeLevel
        ));
        activity.activeAnalysis = analysis(WritingAnalysis.Status.PASS, true, order);
        assertTrue(activity.writingActionPresentation().answerPanelVisible);
        activity.activeAnalysis = null;
        assertFalse(WritingFeedbackCopy.shouldIncreaseSupportAfterAnalysis(null));
        assertFalse(WritingFeedbackCopy.canManualOverride(null));
        assertFalse(WritingFeedbackCopy.canPracticeAfterAnalysis(null));
        assertFalse(WritingFeedbackCopy.canSubmitAnalysis(null));
    }

    @Test
    public void flashcardGestureTrackingCoversTypingBoundsCancelAndOutsideRelease() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                RecordsImportModels.DashboardRow row = row("裂", "split", "レツ", Collections.emptyList());
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
                RecordsImportModels.DashboardRow row = row("裂", "split", "レツ", Collections.emptyList());
                RecordsSchedulerModels.StudySession failSession = sessionWithToken("裂", BridgeScheduler.TASK_KANJI_MEANING, row, "fail-token");
                activity.activeSession = failSession;
                activity.activeStudyPlan = new RecordsSchedulerModels.AdaptiveLoadPlan(20, 1, 1, Collections.singletonList("裂"), 0, false, "One left");
                activity.startActiveStudyTask(activity.sessionTaskKey(failSession), "裂", failSession.taskType, System.currentTimeMillis());
                activity.renderFlashcardSession(failSession);
                assertTrue(activity.flashcardCard instanceof androidx.compose.ui.platform.ComposeView);
                assertEquals(activity.flashcardCard, activity.flashcardGestureArea);
                performClickableWithText(activity.studyActionBar, "Reveal");
                assertTrue(activity.flashcardAnswerRevealed);
                assertTrue(containsText(activity.content, "split"));
                activity.flashcardTouchStartX = 100f;
                activity.flashcardTouchStartY = 100f;
                assertTrue(activity.handleFlashcardRelease(motion(MotionEvent.ACTION_UP, 20f, 100f)));
                RecordsSchedulerModels.ReviewStats gestureFailStats = activity.store.reviewStatsSince(0L);
                assertEquals(1, gestureFailStats.total);
                assertEquals(1, gestureFailStats.again);

                failSession = sessionWithToken("裂", BridgeScheduler.TASK_KANJI_MEANING, row, "fail-token-button");
                activity.activeSession = failSession;
                activity.startActiveStudyTask(activity.sessionTaskKey(failSession), "裂", failSession.taskType, System.currentTimeMillis());
                activity.renderFlashcardSession(failSession);
                performClickableWithText(activity.studyActionBar, "Reveal");
                performClickableWithText(activity.studyActionBar, "Fail");
                RecordsSchedulerModels.ReviewStats failStats = activity.store.reviewStatsSince(0L);
                assertEquals(2, failStats.total);
                assertEquals(2, failStats.again);

                RecordsSchedulerModels.StudySession passSession = sessionWithToken("語", BridgeScheduler.TASK_KANJI_MEANING, row("語", "language", "ゴ", Collections.emptyList()), "pass-token");
                activity.activeSession = passSession;
                activity.startActiveStudyTask(activity.sessionTaskKey(passSession), "語", passSession.taskType, System.currentTimeMillis());
                activity.renderFlashcardSession(passSession);
                assertTrue(activity.flashcardCard instanceof androidx.compose.ui.platform.ComposeView);
                performClickableWithText(activity.studyActionBar, "Reveal");
                performClickableWithText(activity.studyActionBar, MainActivityBase.LABEL_PASS);
                RecordsSchedulerModels.ReviewStats passStats = activity.store.reviewStatsSince(0L);
                assertEquals(3, passStats.total);
                assertEquals(1, passStats.good);

                RecordsSchedulerModels.StudySession gestureSession = sessionWithToken("提", BridgeScheduler.TASK_KANJI_MEANING, row("提", "carry", "テイ", Collections.emptyList()), "gesture-token");
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
                RecordsSchedulerModels.ReviewStats gestureStats = activity.store.reviewStatsSince(0L);
                assertEquals(4, gestureStats.total);
                assertEquals(3, gestureStats.again);
            });
        }
    }

    @Test
    public void homeBrowseDetailStatsAndSyncControlsCoverNonEmptyBranches() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                RecordsImportModels.DashboardRow activeRow = row("裂", "split", "レツ", Collections.singletonList(example("裂語", "レツゴ", "split word", MainActivityBase.SOURCE_ACTIVE)));
                verifyHomeBrowseRowsAndDetail(activity, activeRow);
                verifyRecentMistakesAndEmptyTimeline(activity);
                verifyStatsVerdictBranches(activity, activeRow);
                verifySyncResultStudyNow(activity);
            });
        }
    }

    private static void verifyHomeBrowseRowsAndDetail(
            MainActivity activity,
            RecordsImportModels.DashboardRow activeRow
    ) {
        View passiveMetric = activity.metricCard(R.drawable.ic_target_24, MainActivityBase.CORAL, "Focus", "Waiting", null, null);
        assertFalse(passiveMetric.isClickable());
        assertFalse(containsText(activity.homeSectionHeader("Focus queue", null, null), "Focus queue >"));
        BrowseExampleCardModel activeExample = new MainActivityHomeBrowseDetail(activity)
                .exampleModel(example("裂語", "レツゴ", "split word", MainActivityBase.SOURCE_ACTIVE));
        assertEquals("裂語  レツゴ", activeExample.getExpression());
        assertEquals("split word", activeExample.getMeaning());
        RecordsStudyModels.StudyItem relearning = studyItem("裂", RecordsBase.LadderRung.KANJI_MEANING, "review", 0L)
                .copyBuilder()
                .phase(RecordsBase.SchedulerPhase.RELEARNING)
                .build();
        View relearningRow = activity.queueRowView(new MainActivityBase.QueueEntry(activeRow, relearning), 1000L);
        assertTrue(relearningRow instanceof androidx.compose.ui.platform.ComposeView);
        assertTrue(containsText(relearningRow, "relearning"));
        RecordsStudyModels.StudyItem learning = studyItem("裂", RecordsBase.LadderRung.KANJI_MEANING, "review", 0L)
                .copyBuilder()
                .phase(RecordsBase.SchedulerPhase.NEW_LEARNING)
                .totalReviews(1)
                .build();
        View learningRow = activity.queueRowView(new MainActivityBase.QueueEntry(activeRow, learning), 1000L);
        assertTrue(learningRow instanceof androidx.compose.ui.platform.ComposeView);
        assertTrue(containsText(learningRow, "learning"));
        seedRows(activity, Collections.singletonList(activeRow));
        activity.renderStudyForKanji("裂");
        assertHasText(activity, "Name this kanji");
        activity.store.setKanjiLocallySuspended("裂", true, 1000L);

        activity.renderBrowseKanji("裂");
        assertEquals("裂", activity.activeBrowseQuery);
        assertTrue(activity.content.getChildAt(0) instanceof androidx.compose.ui.platform.ComposeView);
        assertHasText(activity, "SUSPENDED");
        performClickableWithText(activity.content, "split");
        assertEquals(1, activity.content.getChildCount());
        assertTrue(activity.content.getChildAt(0) instanceof androidx.compose.ui.platform.ComposeView);
        assertHasText(activity, "Back to Browse Kanji");
        assertHasText(activity, "Local inventory");
        performClickableWithText(activity.content, "Unsuspend locally");
        assertEquals(1, activity.content.getChildCount());
        assertTrue(activity.content.getChildAt(0) instanceof androidx.compose.ui.platform.ComposeView);
        assertFalse(activity.store.isKanjiLocallySuspended("裂"));
        activity.renderDetail("missing");
        assertEquals(1, activity.content.getChildCount());
        assertTrue(activity.content.getChildAt(0) instanceof androidx.compose.ui.platform.ComposeView);
        assertHasText(activity, "Kanji not found");
    }

    private static void verifyRecentMistakesAndEmptyTimeline(MainActivity activity) {
        activity.renderRecentMistakes();
        assertHasText(activity, "No recent mistakes yet");
        activity.store.saveReview(new RecordsSchedulerModels.ReviewRequest("裂", "miss-token", "again", false, false, false, 0), "again", 2000L);
        activity.renderRecentMistakes();
        assertContainsText(activity.content, "Rated again");

        MainActivityHomeBrowseDetail.BrowseTimelinePanelsModel emptyTimeline = new MainActivityHomeBrowseDetail(activity)
                .recoveryTimelineModel(new RecordsStudyModels.KanjiRecoveryTimeline(null, null, null, Collections.emptyList()));
        assertEquals("No active Anki evidence in the latest local sync.", emptyTimeline.supportText);
    }

    private static void verifyStatsVerdictBranches(
            MainActivity activity,
            RecordsImportModels.DashboardRow activeRow
    ) {
        assertEquals("Kani is not currently working for you", StatsTextCopy.verdictTitle(false));
        StudyStatsStore.LadderHealthMetric ladderOnly = new StudyStatsStore.LadderHealthMetric(
                Collections.singletonMap(RecordsBase.LadderRung.KANJI_MEANING, 1),
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
        assertTrue(StatsTextCopy.verdictBody(false, false, false, 0, 0, 0, 0, 0).contains("No Kani evidence"));
        assertTrue(StatsTextCopy.verdictBody(true, false, true, 0, 0, 1, 3, 1).contains("Kani is tracking"));
        assertTrue(StatsTextCopy.ladderHealthBody(
                ladderOnly.totalActiveItems,
                ladderOnly.promotionReadyCount,
                ladderOnly.demotionRiskCount,
                ladderOnly.demotionReadyCount,
                ladderOnly.ladderPromotionIntervalDays,
                ladderOnly.ladderDemotionFailStreak
        ).contains("more than 21 days"));
        assertFalse(StatsTextCopy.verdictWorking(
                ladderStats.weakKanjiImproved.improvedCount,
                ladderStats.matureSupportGained.matureSupportGained
        ));
        verifyWorkingStatsVerdict(activity, activeRow);
    }

    private static void verifyWorkingStatsVerdict(
            MainActivity activity,
            RecordsImportModels.DashboardRow activeRow
    ) {
        StudyStatsStore.LadderHealthMetric busyLadder = new StudyStatsStore.LadderHealthMetric(
                Collections.singletonMap(RecordsBase.LadderRung.KANJI_MEANING, 4),
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
        String workingBody = StatsTextCopy.verdictBody(
                true,
                true,
                true,
                workingStats.weakKanjiImproved.improvedCount,
                workingStats.matureSupportGained.matureSupportGained,
                busyLadder.promotionReadyCount,
                busyLadder.demotionRiskCount,
                busyLadder.totalActiveItems
        );
        assertTrue(workingBody.contains("weak kanji are burning down"));
        assertTrue(workingBody.contains("mature Anki cards have been gained"));
        assertTrue(workingBody.contains("review-phase item is ready to climb"));
        assertTrue(workingBody.contains("review-phase items with miss streaks"));
        assertTrue(StatsTextCopy.ladderHealthBody(
                busyLadder.totalActiveItems,
                busyLadder.promotionReadyCount,
                busyLadder.demotionRiskCount,
                busyLadder.demotionReadyCount,
                busyLadder.ladderPromotionIntervalDays,
                busyLadder.ladderDemotionFailStreak
        ).contains("at the demotion threshold"));
        assertEquals(3, activity.weaknessImprovementExamples(workingStats.weakKanjiImproved).size());
        assertTrue(activity.supportGainExamples(workingStats.matureSupportGained).get(0).contains("0 -> 3 mature cards"));
        assertTrue(activity.queuedEntries(
                Collections.singletonList(activeRow),
                Collections.singletonList(studyItem("裂", RecordsBase.LadderRung.KANJI_MEANING, "review", 0L)),
                System.currentTimeMillis()
        ) != null);
    }

    private static void verifySyncResultStudyNow(MainActivity activity) {
        activity.renderSyncResult(syncResult(true, false, 1, 0, "", ""));
        performClickableWithText(activity.content, MainActivityBase.LABEL_STUDY_NOW);
        assertHasText(activity, "Name this kanji");
        assertHasText(activity, "What does this kanji mean?");

        LinearLayout summary = new LinearLayout(activity);
        appendOptionalSyncSummaryLines(activity, summary, syncResult(true, false, 1, 0, "", ""));
        assertEquals(0, summary.getChildCount());
        MainActivityHomeBrowseDetail.BrowseTimelinePanelsModel emptyTimeline = new MainActivityHomeBrowseDetail(activity)
                .recoveryTimelineModel(new RecordsStudyModels.KanjiRecoveryTimeline(null, null, null, Collections.emptyList()));
        assertEquals("No timeline events yet.", emptyTimeline.emptyText);
    }

    @Test
    public void writingCallbacksResetResultStateAfterInkEditsAndCleanerRetry() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                RecordsImportModels.DashboardRow row = row("裂", "split", "レツ", Collections.emptyList());
                RecordsSchedulerModels.StudySession writing = session("裂", BridgeScheduler.TASK_WRITE_KANJI, row);
                activity.activeSession = writing;
                activity.currentHintState = HintState.fromWritingLevel(2);
                activity.drawingPad = new DrawingPadView(activity);
                activity.drawingPad.setTarget("裂");
                addInk(activity.drawingPad);
                activity.studyStatus = new WritingStatusView(activity);
                activity.writingResultStatus = new WritingResultStatusHandle(activity);
                prepareWritingActionViews(activity);
                activity.studyAnswerPanel = new LinearLayout(activity);

                StrokeOrderEvaluator.StrokeOrderResult order = StrokeOrderEvaluator
                        .evaluate(guide("裂"), sample())
                        .withDiagnosis(StrokeDiagnosis.builder().add(StrokeDiagnosis.Label.WRONG_DIRECTION, 1).build());
                activity.activeAnalysis = analysis(WritingAnalysis.Status.WRONG, false, order);
                assertTrue(WritingFeedbackCopy.canReplayAnalysis(
                        activity.activeAnalysis,
                        activity.drawingPad != null && activity.drawingPad.hasInk(),
                        guide("裂")
                ));
                activity.writingResultStatus.show("Previous result", activity.CORAL);
                activity.handleDrawingEdited();
                assertNull(activity.activeAnalysis);
                assertTrue(activity.studyStatus.getText().toString().contains("Updated ink"));
                assertEquals(View.GONE, activity.writingResultStatus.getVisibility());
                assertFalse(WritingFeedbackCopy.canReplayAnalysis(
                        analysis(WritingAnalysis.Status.RECOGNITION_ERROR, false, order),
                        activity.drawingPad != null && activity.drawingPad.hasInk(),
                        guide("裂")
                ));
                assertFalse(WritingFeedbackCopy.canReplayAnalysis(
                        activity.activeAnalysis,
                        activity.drawingPad != null && activity.drawingPad.hasInk(),
                        guide("裂")
                ));
                assertFalse(WritingFeedbackCopy.canReplayAnalysis(
                        analysis(WritingAnalysis.Status.WRONG, false, order),
                        activity.drawingPad != null && activity.drawingPad.hasInk(),
                        null
                ));
                assertFalse(WritingFeedbackCopy.canReplayAnalysis(
                        analysis(WritingAnalysis.Status.WRONG, false, order),
                        activity.drawingPad != null && activity.drawingPad.hasInk(),
                        new StrokeGuide("裂", Collections.emptyList())
                ));

                activity.activeAnalysis = analysis(WritingAnalysis.Status.CLOSE, true, order);
                activity.writingResultStatus.show("Messy pass", activity.TEAL);
                activity.startCleanerRetry();
                assertNull(activity.activeAnalysis);
                assertTrue(activity.studyStatus.getText().toString().contains("Try cleaner"));
                assertEquals(View.GONE, activity.writingResultStatus.getVisibility());

                activity.checkingWriting = true;
                activity.activeAnalysis = analysis(WritingAnalysis.Status.WRONG, false, order);
                activity.writingResultStatus.show("Still checking", activity.CORAL);
                activity.handleDrawingEdited();
                assertNotNull(activity.activeAnalysis);
                assertEquals(View.VISIBLE, activity.writingResultStatus.getVisibility());

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
    public void writingResultActionsUseOutcomeSpecificLabels() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                RecordsImportModels.DashboardRow row = row("裂", "split", "レツ", Collections.emptyList());
                prepareWritingUi(activity, session("裂", BridgeScheduler.TASK_WRITE_KANJI, row));
                activity.writingModelStatusKnown = true;
                activity.writingModelDownloaded = true;
                StrokeOrderEvaluator.StrokeOrderResult order = StrokeOrderEvaluator
                        .evaluate(guide("裂"), sample())
                        .withDiagnosis(StrokeDiagnosis.builder().add(StrokeDiagnosis.Label.WRONG_ORDER, 1).build());

                activity.activeAnalysis = analysis(WritingAnalysis.Status.WRONG, false, order);
                activity.updateResultActions();
                WritingPrimaryActionsModel primary = activity.writingPrimaryActionsView.currentModelForTests();
                WritingFallbackActionsModel fallback = activity.writingFallbackActionsView.currentModelForTests();
                assertEquals("Fail", primary.getNextText());
                assertTrue(primary.getNextVisible());
                assertTrue(fallback.getManualOverrideVisible());

                activity.activeAnalysis = analysis(WritingAnalysis.Status.CLOSE, true, order);
                activity.updateResultActions();
                primary = activity.writingPrimaryActionsView.currentModelForTests();
                fallback = activity.writingFallbackActionsView.currentModelForTests();
                assertEquals("Try cleaner", primary.getCheckText());
                assertEquals("Save hard", primary.getNextText());
                assertTrue(fallback.getManualOverrideVisible());
                primary.getOnCheck().run();
                assertNull(activity.activeAnalysis);
                assertTrue(activity.studyStatus.getText().toString().contains("Try cleaner"));

                activity.activeAnalysis = analysis(WritingAnalysis.Status.PASS, true, order);
                activity.updateResultActions();
                primary = activity.writingPrimaryActionsView.currentModelForTests();
                fallback = activity.writingFallbackActionsView.currentModelForTests();
                assertEquals("Pass", primary.getNextText());
                assertFalse(fallback.getManualOverrideVisible());
                primary.getOnNext().run();
                assertEquals(1, activity.store.reviewStatsSince(0L).good);
                assertEquals(View.GONE, activity.studyActionBar.getVisibility());
            });
        }
    }

    @Test
    public void squarePadFrameKeepsDrawingPadSquareUnderRectangularConstraints() {
        MainActivityUiSupport.SquarePadFrame frame = new MainActivityUiSupport.SquarePadFrame(context, 390);
        DrawingPadView pad = new DrawingPadView(context);
        frame.addView(pad);

        frame.measure(
                View.MeasureSpec.makeMeasureSpec(260, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.AT_MOST)
        );
        frame.layout(0, 0, 260, frame.getMeasuredHeight());
        assertEquals(260, pad.getMeasuredWidth());
        assertEquals(260, pad.getMeasuredHeight());

        frame.measure(
                View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.AT_MOST)
        );
        frame.layout(0, 0, 800, frame.getMeasuredHeight());
        assertEquals(390, pad.getMeasuredWidth());
        assertEquals(390, pad.getMeasuredHeight());
        assertEquals(205, pad.getLeft());
    }

    @Test
    public void homeActionGridUsesTwoColumnsWithWrappingHeights() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View homeGrid = activity.homeActionRow();
                measureAtWidth(homeGrid, 320);
                assertTwoColumnGrid(homeGrid, 3);
                assertTrue(containsText(homeGrid, "Recent mistakes"));
                assertTrue(containsText(homeGrid, "Settings"));
                if (BuildConfig.DEBUG) {
                    assertTrue(containsText(homeGrid, "Compose shell"));
                }
            });
        }
    }

    @Test
    public void browseAndDetailCopyAvoidMisleadingOrBlankRows() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertEquals("No matches", HomeTextCopy.browseResultHeading(0));
                assertEquals("2 kanji", HomeTextCopy.browseResultHeading(2));
                assertEquals("Showing first 300 matches", HomeTextCopy.browseResultHeading(300));

                RecordsImportModels.DashboardRow row = new RecordsImportModels.DashboardRow(
                        "裂",
                        1000,
                        "split",
                        "",
                        "deck:Japanese tag:kani prop:due>0 rated:30:1 very-long-browser-query",
                        10,
                        "reason",
                        "",
                        1,
                        0,
                        0,
                        Collections.emptyList()
                );
                MainActivityHomeBrowseDetail browseDetail = new MainActivityHomeBrowseDetail(activity);
                BrowseDetailPanelModel reason = browseDetail.detailReasonPanelModel(row, null);
                assertTrue(reason.getLines().contains("Current local practice evidence from AnkiDroid."));
                assertTrue(reason.getLines().get(1).contains("Anki browser: deck:Japanese"));

                BrowseDetailIdentityModel identity = browseDetail.detailIdentityModel(row, null, false);
                assertEquals("split", identity.getTitle());
                assertEquals("", identity.getReading());
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
                RecordsImportModels.DashboardRow row = row("裂", "split", "レツ", Collections.emptyList());
                activity.studyStatus = new WritingStatusView(activity);
                prepareWritingActionViews(activity);
                new MainActivityStudyWritingStatus(activity).refreshWritingModelStatus();
                activity.activeSession = session("裂", BridgeScheduler.TASK_WRITE_KANJI, row);
                activity.currentHintState = HintState.fromWritingLevel(2);
                new MainActivityStudyWritingStatus(activity).refreshWritingModelStatus();
            });
            scenario.onActivity(activity -> {
                assertTrue(activity.writingModelStatusKnown);
                assertFalse(activity.writingModelDownloaded);
                assertTrue(activity.studyStatus.getText().toString().contains("Download the handwriting checker"));
                WritingPrimaryActionsModel primary = activity.writingPrimaryActionsView.currentModelForTests();
                assertTrue(primary.getDownloadVisible());
                primary.getOnDownload().run();
            });
            scenario.onActivity(activity -> {
                assertTrue(activity.writingModelDownloaded);
                assertTrue(activity.studyStatus.getText().toString().contains("Handwriting checker ready"));

                StrokeOrderEvaluator.StrokeOrderResult order = StrokeOrderEvaluator
                        .evaluate(guide("裂"), sample())
                        .withDiagnosis(StrokeDiagnosis.builder().add(StrokeDiagnosis.Label.WRONG_ORDER, 1).build());
                activity.activeAnalysis = analysis(WritingAnalysis.Status.WRONG, false, order);
                activity.studyStatus.setText("Existing analysis message");
                new MainActivityStudyWritingStatus(activity).refreshWritingModelStatus();
            });
            scenario.onActivity(activity -> {
                assertEquals("Existing analysis message", activity.studyStatus.getText().toString());
                activity.activeAnalysis = null;
                new MainActivityStudyWritingStatus(activity).downloadWritingModel();
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
                assertNotNull(activity.activeAnalysis);
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

                RecordsImportModels.DashboardRow row = row("裂", "split", "レツ", Collections.emptyList());
                activity.activeSession = session("裂", BridgeScheduler.TASK_WRITE_KANJI, row);
                activity.currentHintState = HintState.fromWritingLevel(1);
                activity.studyStatus = new WritingStatusView(activity);
                activity.writingResultStatus = new WritingResultStatusHandle(activity);
                activity.drawingPad = new DrawingPadView(activity);
                activity.checkWriting();
                assertEquals(WritingAnalysis.Status.NO_INK, activity.activeAnalysis.status);
                activity.activeAnalysis = null;
                assertTrue(activity.showNoInkWhenNeeded());
                assertEquals(WritingAnalysis.Status.NO_INK, activity.activeAnalysis.status);

                activity.showModelUnavailable("The handwriting checker is unavailable on this device.");
                assertEquals(WritingAnalysis.Status.MODEL_UNAVAILABLE, activity.activeAnalysis.status);
                assertTrue(activity.writingResultStatus.getText().toString().contains("unavailable"));
                assertEquals(View.VISIBLE, activity.writingResultStatus.getVisibility());

                activity.replayWritingAnalysis();
                activity.activeSession = null;
                activity.replayWritingAnalysis();
            });
        }
    }

    private static WritingAnalysis analysis(WritingAnalysis.Status status, boolean passed, StrokeOrderEvaluator.StrokeOrderResult order) {
        return new WritingAnalysis(status, passed ? "good" : "again", passed, status.name(), Collections.emptyList(), order, HintLevel.BLIND, 0);
    }

    private static RecordsSchedulerModels.StudySession session(String kanji, String taskType, RecordsImportModels.DashboardRow row) {
        return sessionWithToken(kanji, taskType, row, "tok");
    }

    private static RecordsSchedulerModels.StudySession sessionWithToken(String kanji, String taskType, RecordsImportModels.DashboardRow row, String token) {
        RecordsStudyModels.StudyItem item = new RecordsStudyModels.StudyItem(
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
        return new RecordsSchedulerModels.StudySession(item, row, token, taskType, BridgeScheduler.TASK_WRITE_KANJI.equals(taskType), prompt);
    }

    private static RecordsImportModels.DashboardRow row(String kanji, String meaning, String reading, List<RecordsImportModels.Example> examples) {
        return new RecordsImportModels.DashboardRow(kanji, 1000, meaning, reading, kanji, 10, "reason", "reason text", 1, 0, 0, examples);
    }

    private static RecordsImportModels.DashboardRow rowWithReason(String kanji, String meaning, String reading, String reason, List<RecordsImportModels.Example> examples) {
        return new RecordsImportModels.DashboardRow(kanji, 1000, meaning, reading, kanji, 10, "reason", reason, 1, 0, 0, examples);
    }

    private static RecordsImportModels.Example example(String expression, String reading, String meaning, String sourceType) {
        return new RecordsImportModels.Example(sourceType, 1L, 2L, expression, reading, meaning, "", false, 0);
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

    private static RecordsSyncModels.Settings settings(
            boolean active,
            boolean suspended,
            boolean tagged,
            List<String> tags,
            boolean weak,
            boolean browser,
            String query
    ) {
        RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();
        return new RecordsSyncModels.Settings(
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

    private static RecordsStudyModels.StudyItem studyItem(String kanji, RecordsBase.LadderRung rung, String state, long dueAtMillis) {
        return new RecordsStudyModels.StudyItem(
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

    private static RecordsImportModels.KanjiTimelineEvent event(String expression, String reading) {
        return new RecordsImportModels.KanjiTimelineEvent(
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

    private static void seedRows(MainActivity activity, List<RecordsImportModels.DashboardRow> rows) {
        List<RecordsSyncModels.Note> notes = new ArrayList<>();
        List<RecordsSyncModels.Card> cards = new ArrayList<>();
        long id = 1L;
        for (RecordsImportModels.DashboardRow row : rows) {
            notes.add(note(id, row.kanji + "語", row.reading, row.primaryMeaning, row.kanji + "を見た。"));
            cards.add(new RecordsSyncModels.Card(100L + id, id, 0, "Kiku", 2, 2, 0, 1, 0, 0, false));
            id++;
        }
        activity.store.saveSuccessfulSync(
                new RecordsSyncModels.CollectionSnapshot(notes, cards),
                Collections.emptyList(),
                rows,
                RecordsSyncModels.Settings.kikuDefaults(),
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

    private static RecordsSyncModels.Note note(long id, String expression, String reading, String meaning, String sentence) {
        java.util.Map<String, String> fields = new java.util.LinkedHashMap<>();
        fields.put("Expression", expression);
        fields.put("ExpressionReading", reading);
        fields.put("MainDefinition", meaning);
        fields.put("Sentence", sentence);
        fields.put("Frequency", "1000");
        fields.put("FreqSort", "1000");
        return new RecordsSyncModels.Note(id, 1001L, "Kiku", fields, Collections.emptyList());
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

    private static void measureAtWidth(View view, int width) {
        view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST)
        );
        view.layout(0, 0, width, view.getMeasuredHeight());
    }

    private static void assertTwoColumnGrid(View view, int expectedRows) {
        assertTrue(view instanceof ViewGroup);
        ViewGroup grid = (ViewGroup) view;
        assertEquals(expectedRows, grid.getChildCount());
        for (int i = 0; i < grid.getChildCount(); i++) {
            assertTrue(grid.getChildAt(i) instanceof ViewGroup);
            ViewGroup row = (ViewGroup) grid.getChildAt(i);
            assertEquals(2, row.getChildCount());
            assertTrue(row.getMeasuredHeight() > 0);
        }
    }

    private static void prepareWritingUi(MainActivity activity, RecordsSchedulerModels.StudySession session) {
        activity.activeSession = session;
        activity.currentHintState = HintState.fromWritingLevel(1);
        activity.studyStatus = new WritingStatusView(activity);
        activity.writingResultStatus = new WritingResultStatusHandle(activity);
        prepareWritingActionViews(activity);
        activity.studyAnswerPanel = new LinearLayout(activity);
        activity.drawingPad = new DrawingPadView(activity);
        activity.drawingPad.setTarget(session.item.kanji);
        activity.activeAnalysis = null;
        activity.checkingWriting = false;
        activity.writingModelStatusKnown = false;
        activity.writingModelDownloaded = false;
    }

    private static void prepareWritingActionViews(MainActivity activity) {
        activity.writingToolActionsView = new WritingToolActionsView(activity);
        activity.writingPrimaryActionsView = new WritingPrimaryActionsView(activity);
        activity.writingFallbackActionsView = new WritingFallbackActionsView(activity);
    }

    private static void assertHasText(MainActivity activity, String text) {
        View root = activity.findViewById(android.R.id.content);
        if (!containsText(root, text) && findDeviceTextNow(text) == null) {
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
        if (view instanceof androidx.compose.ui.platform.ComposeView composeView && containsAccessibilityText(composeView.createAccessibilityNodeInfo(), expected)) {
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
        if (view instanceof androidx.compose.ui.platform.ComposeView composeView && containsAccessibilityTextContaining(composeView.createAccessibilityNodeInfo(), expected)) {
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
            UiObject2 object = findDeviceClickableTextNow(label);
            if (object == null) {
                throw new AssertionError("Missing clickable text: " + label);
            }
            object.click();
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle(2000L);
            return;
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

    private static UiObject2 findDeviceTextNow(String label) {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        String pkg = appPackage();
        UiObject2 object = firstMatch(device.findObjects(By.pkg(pkg).text(label)));
        if (object == null) {
            object = firstMatch(device.findObjects(By.pkg(pkg).textContains(label)));
        }
        if (object == null) {
            object = firstMatch(device.findObjects(By.pkg(pkg).text(label.toUpperCase(Locale.ROOT))));
        }
        if (object == null) {
            object = firstMatch(device.findObjects(By.pkg(pkg).textContains(label.toUpperCase(Locale.ROOT))));
        }
        return object;
    }

    private static UiObject2 findDeviceClickableTextNow(String label) {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        String pkg = appPackage();
        UiObject2 object = firstMatch(device.findObjects(By.pkg(pkg).clickable(true).text(label)));
        if (object == null) {
            object = firstMatch(device.findObjects(By.pkg(pkg).clickable(true).textContains(label)));
        }
        if (object == null) {
            object = firstMatch(device.findObjects(By.pkg(pkg).clickable(true).text(label.toUpperCase(Locale.ROOT))));
        }
        if (object == null) {
            object = firstMatch(device.findObjects(By.pkg(pkg).clickable(true).textContains(label.toUpperCase(Locale.ROOT))));
        }
        if (object == null) {
            object = firstMatch(device.findObjects(By.pkg(pkg).clickable(true).desc(label)));
        }
        if (object == null) {
            object = firstMatch(device.findObjects(By.pkg(pkg).clickable(true).descContains(label)));
        }
        if (object != null && !object.isClickable()) {
            UiObject2 parent = object.getParent();
            while (parent != null && parent != object && !parent.isClickable()) {
                object = parent;
                parent = object.getParent();
            }
            if (parent != null && parent.isClickable()) {
                object = parent;
            }
        }
        return object != null && object.isClickable() ? object : null;
    }

    private static String appPackage() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName();
    }

    private static UiObject2 firstMatch(List<UiObject2> objects) {
        return objects.isEmpty() ? null : objects.get(0);
    }

    private static boolean containsAccessibilityText(AccessibilityNodeInfo node, String expected) {
        if (node == null) {
            return false;
        }
        try {
            CharSequence value = node.getText();
            if (value != null && expected.contentEquals(value)) {
                return true;
            }
            CharSequence description = node.getContentDescription();
            if (description != null && expected.contentEquals(description)) {
                return true;
            }
            int childCount = node.getChildCount();
            for (int i = 0; i < childCount; i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child == null) {
                    continue;
                }
                try {
                    if (containsAccessibilityText(child, expected)) {
                        return true;
                    }
                } finally {
                    // The recursive call owns the child node lifecycle.
                }
            }
            return false;
        } finally {
            node.recycle();
        }
    }

    private static boolean containsAccessibilityTextContaining(AccessibilityNodeInfo node, String expected) {
        if (node == null) {
            return false;
        }
        try {
            CharSequence value = node.getText();
            if (value != null && value.toString().contains(expected)) {
                return true;
            }
            CharSequence description = node.getContentDescription();
            if (description != null && description.toString().contains(expected)) {
                return true;
            }
            int childCount = node.getChildCount();
            for (int i = 0; i < childCount; i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child == null) {
                    continue;
                }
                try {
                    if (containsAccessibilityTextContaining(child, expected)) {
                        return true;
                    }
                } finally {
                    // The recursive call owns the child node lifecycle.
                }
            }
            return false;
        } finally {
            node.recycle();
        }
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

    private static MotionEvent motion(int action, float x, float y) {
        return MotionEvent.obtain(0L, 0L, action, x, y, 0);
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
            // Fake recognizer has no native resources to release.
        }
    }
}
