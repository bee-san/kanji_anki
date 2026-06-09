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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
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
import androidx.compose.ui.platform.ComposeView;

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
import dev.bee.kanjianki.data.LocalStoreBase;
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

import kotlin.Unit;

import java.io.File;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import org.junit.Assert.assertEquals;
import org.junit.Assert.assertFalse;
import org.junit.Assert.assertNotNull;
import org.junit.Assert.assertNull;
import org.junit.Assert.assertNotEquals;
import org.junit.Assert.assertSame;
import org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4::class)
class MainActivityHelperInstrumentedTest {
    private lateinit var context: Context

    @Before
fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("kanji_anki_simple.db");
        MainActivityRuntimeOverrides.setAnkiDroidGateway(AnkiDroidGateway.testProvider(context, "dev.bee.kanjianki.helper_no_anki"));
        MainActivityRuntimeOverrides.setCollectionGateway(null);
        MainActivityRuntimeOverrides.setWritingRecognizer(null);
        MainActivityRuntimeOverrides.setWritingRecognizerFactory(null);
        MainActivityRuntimeOverrides.setInstallPermission(null);
        MainActivityRuntimeOverrides.setRuntimeNotificationPermission(null);
        MainActivityRuntimeOverrides.setNotificationsAllowed(null);
    }

    @After
fun tearDown() {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(null);
        MainActivityRuntimeOverrides.setCollectionGateway(null);
        MainActivityRuntimeOverrides.setWritingRecognizer(null);
        MainActivityRuntimeOverrides.setWritingRecognizerFactory(null);
        MainActivityRuntimeOverrides.setInstallPermission(null);
        MainActivityRuntimeOverrides.setRuntimeNotificationPermission(null);
        MainActivityRuntimeOverrides.setNotificationsAllowed(null);
        context.deleteDatabase("kanji_anki_simple.db");
        deleteRecursively(File(context.getCacheDir(), "updates"));
    }

    @Test
fun launcherHostsHomeInsideComposeShell() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val activityContent = activity.findViewById<ViewGroup>(android.R.id.content)
                assertEquals(1, activityContent.getChildCount());
                assertTrue(activityContent.getChildAt(0) is ComposeView);
                assertHasText(activity, "Kani");
            }
        }
    }

    @Test
fun baseTextHelpersDescribeCountAndCompactText() {
        assertCountAndCompactText();
    }

    @Test
fun baseTextHelpersDescribeStudyModeLabels() {
        assertStudyModeLabels();
    }

    @Test
fun baseTextHelpersDescribeAdaptiveFocusText() {
        assertAdaptiveFocusText();
    }

    @Test
fun baseTextHelpersDescribeWritingGuideText() {
        assertWritingGuideText();
    }

private fun assertCountAndCompactText() {
        assertEquals("1 item", StudyTextCopy.countText(1, "item", "items"));
        assertEquals("2 items", StudyTextCopy.countText(2, "item", "items"));
        assertEquals("", StudyTextCopy.compact(null, 12));
        assertEquals("short", StudyTextCopy.compact("short", 12));
        assertEquals("a very long s...", StudyTextCopy.compact("a very long sentence that should be shortened", 16));
    }

private fun assertStudyModeLabels() {
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
        assertEquals("Repair", StudyTaskCopy.labelForTask("repair_writing"));
        assertEquals("Focused practice", StudyTaskCopy.labelForTask("targeted_writing"));
        assertEquals("New problem kanji", StudyTaskCopy.labelForTask("context_writing"));
        assertEquals("Guided review", StudyTaskCopy.labelForTask("guided_writing"));
        assertEquals("Memory check", StudyTaskCopy.labelForTask("blind_writing"));
        assertEquals("Memory check", StudyTaskCopy.labelForTask("sampled_handwriting"));
        assertEquals("Learn the shape", StudyTaskCopy.labelForTask("confusable_recognition"));
        assertEquals("Study", StudyTaskCopy.labelForTask("unexpected"));
        assertEquals("android.permission.POST_NOTIFICATIONS", MainActivityBase.PERMISSION_POST_NOTIFICATIONS);
    }

private fun assertAdaptiveFocusText() {
        var waiting = RecordsSchedulerModels.AdaptiveLoadPlan(20, 0, 0, emptyList<String>(), 0, false, "")
        var all = RecordsSchedulerModels.AdaptiveLoadPlan(100, 3, 3, listOf("裂", "提", "語"), 0, true, "all")
        var focused = RecordsSchedulerModels.AdaptiveLoadPlan(20, 5, 2, listOf("裂", "提"), 0, false, "focus")
        assertEquals("Adaptive focus is waiting for sync", AdaptiveFocusCopy.adaptiveFocusText(null));
        assertEquals("Adaptive focus is waiting for sync", AdaptiveFocusCopy.adaptiveFocusText(waiting));
        assertEquals("Adaptive focus covers all current problem kanji", AdaptiveFocusCopy.adaptiveFocusText(all));
        assertEquals("Today's adaptive focus: 2 of 5 left", AdaptiveFocusCopy.adaptiveFocusText(focused));
    }

private fun assertWritingGuideText() {
        var emptyGuide = StrokeGuide("裂", emptyList<InkStroke>())
        var guide = guide("裂")
        assertTrue(WritingFeedbackCopy.guideLabel(3, emptyGuide).startsWith("Write from memory"));
        assertTrue(WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(3), emptyGuide).startsWith("Write from memory"));
        assertTrue(WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(0), emptyGuide).startsWith("Draw it"));
        assertEquals("Trace the strokes, then check.", WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(0), guide));
        assertEquals("Copy the faint outline; the current stroke is emphasized.", WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(1), guide));
        assertEquals("Write with only the current stroke hinted, then check.", WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(2), guide));
        assertEquals("Write from memory, then check. Use Hint if you are stuck.", WritingFeedbackCopy.guideLabel(HintState.fromWritingLevel(3), guide));
        assertEquals("Trace", WritingFeedbackCopy.stageLabel(HintLevel.TRACE));
        assertEquals("Blind", WritingFeedbackCopy.stageLabel(HintLevel.BLIND));
        assertEquals("", WritingFeedbackCopy.attemptProgressText(null, null, false));
        assertEquals("", WritingFeedbackCopy.targetRevealText(null, null));
    }

    @Test
fun baseLifecyclePermissionAndProgressHelpersCoverStatefulCallbacks() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val startup = MainActivityStartup(activity)
                assertTrue(startup.shouldRunBackgroundStartupTasks(Intent()))
                assertFalse(startup.shouldRunBackgroundStartupTasks(Intent().putExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE, MainActivityBase.NAV_HOME_ROUTE)))

                activity.handleLaunchIntent(Intent().putExtra(MainActivityBase.EXTRA_OPEN_UPDATE, true));
                assertHasText(activity, "App updates");

                activity.handleLaunchIntent(Intent().putExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE, "update"));
                assertHasText(activity, "App updates");

                activity.handleLaunchIntent(Intent().putExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE, MainActivityBase.NAV_HOME_ROUTE));
                assertHasText(activity, "Browse Kanji");
                activity.handleLaunchIntent(Intent().putExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE, MainActivityBase.NAV_STUDY));
                assertHasText(activity, "Study");
                activity.handleLaunchIntent(Intent().putExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE, MainActivityBase.NAV_STATS_ROUTE));
                assertHasText(activity, "Stats");
                activity.handleLaunchIntent(Intent().putExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE, MainActivityBase.NAV_SETTINGS_ROUTE));
                assertHasText(activity, "Settings");
                activity.handleLaunchIntent(Intent().putExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE, "games"));
                assertHasText(activity, "Games");

                activity.handlePermissionResult(7, intArrayOf(PackageManager.PERMISSION_DENIED));
                assertHasText(activity, "Kani");
                activity.onRequestPermissionsResult(7, emptyArray<String>(), intArrayOf(PackageManager.PERMISSION_DENIED));
                assertHasText(activity, "Kani");

                activity.pendingReminderSettings = LocalStoreBase.ReminderSettings(true, 8, 30);
                activity.handlePostNotificationPermission(intArrayOf(PackageManager.PERMISSION_GRANTED));
                assertTrue(activity.store.reminderSettings().enabled);
                activity.pendingReminderSettings = LocalStoreBase.ReminderSettings(true, 9, 15);
                activity.handlePostNotificationPermission(intArrayOf(PackageManager.PERMISSION_DENIED));
                assertFalse(activity.store.reminderSettings().enabled);
                activity.pendingReminderSettings = LocalStoreBase.ReminderSettings(true, 10, 45);
                activity.handlePermissionResult(MainActivityBase.REQUEST_POST_NOTIFICATIONS, intArrayOf(PackageManager.PERMISSION_GRANTED));
                assertTrue(activity.store.reminderSettings().enabled);
                activity.handlePermissionResult(999, intArrayOf(PackageManager.PERMISSION_DENIED));
                assertTrue(activity.store.reminderSettings().enabled);

                var now = System.currentTimeMillis()
                var reviewDue = studyItem("復", RecordsBase.LadderRung.KANJI_MEANING, "review", now - 1L)
                activity.studyMoreNewCardKanji.add("復");
                val extraPlan = activity.studyMoreNewCardsPlan(
                        listOf(row("復", "review", "フク", emptyList<RecordsImportModels.Example>())),
                        listOf(reviewDue.copyBuilder().totalReviews(1).build()),
                        now
                );
                assertEquals(1, extraPlan.remaining);

                val all = activity.allCurrentProblemKanjiPlan(
                        listOf(row("裂", "split", "レツ", emptyList<RecordsImportModels.Example>()), row("語", "language", "ゴ", emptyList<RecordsImportModels.Example>())),
                        listOf(studyItem("裂", RecordsBase.LadderRung.KANJI_MEANING, "review", now - 1L).copyBuilder().totalReviews(1).build()),
                        now
                );
                assertEquals(2, all.target);
                assertTrue(all.allKanjiMode);

                activity.activeSession = session("裂", BridgeScheduler.TASK_KANJI_MEANING, row("裂", "split", "レツ", emptyList<RecordsImportModels.Example>()));
                activity.studySessionTracker.setTargetCount(2);
                activity.markStudyTaskCompleted("topbar:one");
                activity.markStudyTaskCompleted("topbar:two");
                activity.continueAllKanjiSession = true;
                var clueItem = studyItem("?", RecordsBase.LadderRung.KANJI_MEANING, "review", now)
                assertEquals(
                        "Fallback prompt",
                        StudyTextCopy.sessionClue(activity.currentDictionaryLookup(), RecordsSchedulerModels.StudySession(clueItem, null, "tok", BridgeScheduler.TASK_KANJI_MEANING, false, "fallback prompt"))
                );
                assertEquals("Fallback", StudyTextCopy.canonicalKanjiMeaning(activity.currentDictionaryLookup(), "?", "fallback", 40));
                val cachedRecognizer = FakeWritingRecognizer(
                        CompletableFuture.completedFuture(WritingRecognizer.ModelStatus("ja", "ja-JP", true, "ready")),
                        CompletableFuture.completedFuture(WritingRecognizer.ModelStatus("ja", "ja-JP", true, "ready")),
                        CompletableFuture.completedFuture(WritingRecognizer.RecognitionResult(emptyList<WritingRecognizer.Candidate>()))
                );
                activity.writingRecognizer = cachedRecognizer;
                assertSame(cachedRecognizer, activity.currentWritingRecognizer());

                var timing = StudySessionTracker.ActiveStudyTask(null, null, null, -10L)
                timing.pause(50L);
                timing.resume(60L);
                timing.pause(90L);
                assertEquals(30L, timing.activeElapsedMillis);

            }
        }
    }

    @Test
fun studySessionHelpersPickExamplesPromptsTitlesAndTaskKinds() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var suspended = example("停止語", "テイシゴ", "suspended", MainActivityBase.SOURCE_SUSPENDED)
                var active = example("活動語", "カツドウゴ", "active", MainActivityBase.SOURCE_ACTIVE)
                var fallback = example("予備語", "ヨビゴ", "fallback", "other")
                var row = row("語", "language", "ゴ", listOf(fallback, active, suspended))

                assertEquals(active, activity.firstExample(row));
                assertEquals(suspended, activity.wordReadingExample(row));
                assertEquals(fallback, activity.firstExample(row("語", "language", "ゴ", listOf(fallback))));
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
                assertEquals("活動語", StudyTextCopy.wordPrompt(session("語", BridgeScheduler.TASK_WORD_READING, row("語", "language", "ゴ", listOf(active)))));
                assertEquals("語", StudyTextCopy.wordPrompt(session("語", BridgeScheduler.TASK_WORD_READING, row("語", "language", "ゴ", emptyList<RecordsImportModels.Example>()))));
                assertEquals("active", StudyTextCopy.collectionMeaningForSession(session("語", BridgeScheduler.TASK_WORD_READING, row("語", "language", "ゴ", listOf(active)))));
                assertEquals("active", StudyTextCopy.collectionMeaningForSession(session("語", BridgeScheduler.TASK_KANJI_MEANING, row)));
                assertEquals("", StudyTextCopy.collectionMeaningForSession(null));

                assertTrue(StudyTaskCopy.isWordReadingTask(session("語", BridgeScheduler.TASK_WORD_READING, row)));
                assertTrue(StudyTaskCopy.isTypingMeaningTask(session("語", BridgeScheduler.TASK_TYPE_MEANING, row)));
                assertTrue(StudyTaskCopy.isFontRecognitionTask(session("語", BridgeScheduler.TASK_FONT_MEANING, row)));
                assertTrue(StudyTaskCopy.isRecallTask(session("語", "blind_writing", row)));
                assertFalse(StudyTaskCopy.isRecallTask(null));
            }
        }
    }

    @Test
fun writingAnalysisHelpersExplainDiagnosisAndAllowedActions() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var writing = session("裂", BridgeScheduler.TASK_WRITE_KANJI, row("裂", "split", "レツ", emptyList<RecordsImportModels.Example>()))
                activity.activeSession = writing;
                activity.currentHintState = HintState.fromWritingLevel(1);
                val diagnosis = StrokeDiagnosis.builder()
                        .add(StrokeDiagnosis.Label.WRONG_ORDER, 1)
                        .add(StrokeDiagnosis.Label.WRONG_DIRECTION, 2)
                        .add(StrokeDiagnosis.Label.MISSING_STROKE, 3)
                        .add(StrokeDiagnosis.Label.ROUGH_SHAPE, 4)
                        .add(StrokeDiagnosis.Label.RECOGNIZED_BUT_MESSY, 5)
                        .build();
                val order = StrokeOrderEvaluator
                        .evaluate(guide("裂"), sample())
                        .withDiagnosis(diagnosis);
                var wrong = analysis(WritingAnalysis.Status.WRONG, false, order)
                var close = analysis(WritingAnalysis.Status.CLOSE, true, order)
                val pass = WritingAnalysis(
                        WritingAnalysis.Status.PASS,
                        "good",
                        true,
                        "Clean",
                        emptyList<RecognitionCandidate>(),
                        order
                )

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
                assertTrue(WritingFeedbackCopy.attemptProgressText(close, activity.activeSession?.item?.writingLevel, WritingFeedbackCopy.shouldIncreaseSupportAfterAnalysis(close)).contains("Try cleaner"));
                assertTrue(WritingFeedbackCopy.attemptProgressText(pass, activity.activeSession?.item?.writingLevel, WritingFeedbackCopy.shouldIncreaseSupportAfterAnalysis(pass)).contains("less help"));
                assertTrue(WritingFeedbackCopy.targetRevealText(wrong, activity.activeSession?.item?.kanji).contains("Target: 裂"));
            }
        }
    }

    @Test
fun settingsHelpersSummarizeImportTimingAndWorkloadChoices() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                verifyVersionRankAndRetentionText(activity);
                verifyImportSourceSummaries(activity);
                verifyAutoSyncSummaries(activity);
                verifyWorkloadAndReminderSummaries(activity);
                verifyImportThresholdReader(activity);
                verifyRankAndMaxItemControls(activity);
            }
        }
    }

private fun verifyVersionRankAndRetentionText(activity: MainActivity) {
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

private fun verifyImportSourceSummaries(activity: MainActivity) {
        var validation = MainActivitySettingsAnkiSourceValidation(activity)
        assertTrue(SettingsInputRules.validImportThresholds(7.5, 3, 2));
        assertFalse(SettingsInputRules.validImportThresholds(0.5, 3, 2));
        assertFalse(validation.hasSelectedImportSource(
                checked(activity, false),
                checked(activity, false),
                checked(activity, false),
                checked(activity, false),
                checked(activity, false),
                emptyList<String>(),
                ""
        ));
        assertTrue(validation.hasSelectedImportSource(
                checked(activity, false),
                checked(activity, true),
                checked(activity, false),
                checked(activity, false),
                checked(activity, false),
                emptyList<String>(),
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
                listOf("leeches"),
                ""
        ));
        assertTrue(validation.hasSelectedImportSource(
                checked(activity, false),
                checked(activity, false),
                checked(activity, true),
                checked(activity, false),
                null,
                listOf("leeches"),
                null
        ));
        assertTrue(validation.hasSelectedImportSource(
                checked(activity, false),
                checked(activity, false),
                checked(activity, false),
                checked(activity, false),
                checked(activity, true),
                emptyList<String>(),
                "deck:Kiku"
        ));
        assertEquals("3+ cards per kanji", SettingsTextCopy.matchingCardsSummary(settings(true, true, true, listOf("leeches"), true, true, "deck:Kiku")));
        assertTrue(SettingsTextCopy.settingsImportSummary(settings(true, true, true, listOf("leeches"), true, true, "deck:Kiku")).contains("tagged"));
        assertEquals("Choose an import source", SettingsTextCopy.settingsImportSummary(settings(false, false, false, emptyList<String>(), false, false, "")));
    }

private fun verifyAutoSyncSummaries(activity: MainActivity) {
        var unconfigured = LocalStoreBase.AutoSyncSettings(false, true, 7, 30, 0L, 0L, 0L)
        var enabled = LocalStoreBase.AutoSyncSettings(true, true, 7, 30, 1000L, 2000L, 3000L)
        var disabled = LocalStoreBase.AutoSyncSettings(true, false, 7, 30, 1000L, 0L, 0L)
        var enabledNoHistory = LocalStoreBase.AutoSyncSettings(true, true, 7, 30, 0L, 0L, 0L)
        var disabledNoHistory = LocalStoreBase.AutoSyncSettings(true, false, 7, 30, 0L, 0L, 0L)
        assertEquals("Sync once to start", SettingsTextCopy.settingsAutoSyncSummary(unconfigured.configured, unconfigured.enabled, unconfigured.displayTime()));
        assertEquals("07:30", SettingsTextCopy.settingsAutoSyncSummary(enabled.configured, enabled.enabled, enabled.displayTime()));
        assertEquals("Off", SettingsTextCopy.settingsAutoSyncSummary(disabled.configured, disabled.enabled, disabled.displayTime()));
        assertEquals("Sync once to start", SettingsTextCopy.autoSyncStatus(unconfigured.configured, unconfigured.enabled, unconfigured.displayTime()));
        assertEquals("On around 07:30", SettingsTextCopy.autoSyncStatus(enabled.configured, enabled.enabled, enabled.displayTime()));
        assertEquals("Off", SettingsTextCopy.autoSyncStatus(disabled.configured, disabled.enabled, disabled.displayTime()));
        assertTrue(SettingsTextCopy.autoSyncDetail(
                enabled.configured,
                enabled.enabled,
                DateTextPolicy.shortDateTime(enabled.lastSuccessAt),
                DateTextPolicy.shortDateTime(enabled.lastAttemptAt),
                DateTextPolicy.shortDateTime(enabled.nextRunAt)
        ).contains("Last sync:"));
        assertTrue(SettingsTextCopy.autoSyncDetail(
                disabled.configured,
                disabled.enabled,
                "",
                DateTextPolicy.shortDateTime(disabled.lastAttemptAt),
                ""
        ).contains("Last attempt:"));
        assertTrue(SettingsTextCopy.autoSyncDetail(
                enabledNoHistory.configured,
                enabledNoHistory.enabled,
                "",
                "",
                ""
        ).contains("Runs daily"));
        assertTrue(SettingsTextCopy.autoSyncDetail(
                disabledNoHistory.configured,
                disabledNoHistory.enabled,
                "",
                "",
                ""
        ).contains("paused"));
    }

private fun verifyWorkloadAndReminderSummaries(activity: MainActivity) {
        assertEquals("Focused: up to 5 items", SettingsTextCopy.workloadStatusText(20, 5));
        assertEquals("All kanji: up to 9 items", SettingsTextCopy.workloadStatusText(100, 9));
        assertEquals("Maximum: 1 item", SettingsTextCopy.maxItemsStatusText(1));
        assertEquals("Kani plan: waiting for cards", SettingsTextCopy.autoWorkloadStatusText(null));
        assertEquals(
                "Kani plan: 2 items today",
                SettingsTextCopy.autoWorkloadStatusText(RecordsSchedulerModels.AdaptiveLoadPlan(true, 20, 2, 1, listOf("裂", "語"), 0, false, "auto"))
        );
        assertEquals("Notifications blocked", SettingsTextCopy.reminderStatus(true, true, "21:05"));
        assertEquals("Daily around 21:05", SettingsTextCopy.reminderStatus(true, false, "21:05"));
        assertEquals("Off", SettingsTextCopy.reminderStatus(false, false, "21:05"));
        assertEquals("21:05", SettingsTextCopy.reminderTime(21, 5));
        assertEquals("Reminder time: 21:05", SettingsTextCopy.reminderTimeButtonLabel(21, 5));
        var normalizedMax = AdaptiveLoadPlanner.normalizeMaxItems(0)
        assertEquals("Maximum: " + StudyTextCopy.countText(normalizedMax, "item", "items"), SettingsTextCopy.maxItemsStatusText(0));
    }

private fun appendOptionalSyncSummaryLines(activity: MainActivity, summary: LinearLayout, result: ManualSyncEngine.SyncResult) {
        if (!result.adaptiveSummary.isEmpty()) {
            summary.addView(testText(activity, result.adaptiveSummary, 15, Color.WHITE, false));
        }
        if (result.importedSuspendedKanji > 0) {
            summary.addView(testText(activity, HomeTextCopy.importedSuspendedKanjiText(result.importedSuspendedKanji), 15, Color.WHITE, false));
        }
        if (result.message != null && !result.message.isEmpty()) {
            summary.addView(testText(activity, result.message, 14, Color.WHITE, false));
        }
    }

private fun testText(context: Context, value: String, sp: Int, color: Int, bold: Boolean): TextView {
        var view = TextView(context)
        view.text = value
        view.setTextSize(sp.toFloat())
        view.setTextColor(color);
        view.setIncludeFontPadding(true);
        view.setLineSpacing(0f, 1.05f);
        view.setTypeface(Typeface.DEFAULT, if (bold) Typeface.BOLD else Typeface.NORMAL)
        return view;
    }

private fun verifyImportThresholdReader(activity: MainActivity) {
        var validation = MainActivitySettingsAnkiSourceValidation(activity)
        var difficulty = EditText(activity)
        var lapses = EditText(activity)
        var minMatching = EditText(activity)
        difficulty.setText("not numeric");
        lapses.setText("3");
        minMatching.setText("2");
        assertNull(validation.readImportThresholds(difficulty, lapses, minMatching));
        difficulty.setText("0.5");
        assertNull(validation.readImportThresholds(difficulty, lapses, minMatching));
        difficulty.setText("7.5");
        val thresholds = requireNotNull(validation.readImportThresholds(difficulty, lapses, minMatching))
        assertEquals(7.5, thresholds.difficulty, 0.001);
        assertEquals(3, thresholds.lapseThreshold);
        assertEquals(2, thresholds.minCards);
    }

private fun verifyRankAndMaxItemControls(activity: MainActivity) {
        assertEquals(49, SettingsInputRules.rankSliderProgress(50));
        assertEquals(50, SettingsInputRules.rankFromSliderProgress(49));
        var normalized = SettingsInputRules.normalizedRankRange(300, 20)
        assertEquals(20, normalized.minRank)
        assertEquals(300, normalized.maxRank)
    }

    @Test
fun homeAndDetailHelpersSummarizeQueueStatsAndTimelineState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                verifyHomeSyncFocusAndStreakText(activity);
                verifyStudyTimeRankAndQueueText(activity);
                verifySourceEvidenceAndEmptyQueue(activity);
                var inventory = RecordsImportModels.KanjiInventoryItem("語", "language", "ゴ", "kanji:語", 2, 3, true, 1000L)
                var row = row("裂", "split", "レツ", emptyList<RecordsImportModels.Example>())
                verifyDetailIdentityAndTimeline(activity, inventory, row);
                verifyDetailPanels(activity, inventory, row);
            }
        }
    }

private fun verifyHomeSyncFocusAndStreakText(activity: MainActivity) {
        assertEquals("Never synced", HomeTextCopy.homeSyncValue(null));
        assertEquals("", HomeTextCopy.sentenceCase(""));
        assertEquals("", HomeTextCopy.sentenceCase(null));
        assertEquals("Synced today", HomeTextCopy.sentenceCase("synced today"));

        var waiting = RecordsSchedulerModels.AdaptiveLoadPlan(20, 0, 0, emptyList<String>(), 0, false, "")
        var all = RecordsSchedulerModels.AdaptiveLoadPlan(100, 2, 2, listOf("裂", "語"), 0, true, "all")
        var focused = RecordsSchedulerModels.AdaptiveLoadPlan(20, 4, 1, listOf("裂", "語"), 0, false, "focus")
        assertEquals("Waiting", HomeTextCopy.focusHeadline(null));
        assertEquals("Waiting", HomeTextCopy.focusHeadline(waiting));
        assertEquals("All current", HomeTextCopy.focusHeadline(all));
        assertEquals("1/4 left", HomeTextCopy.focusHeadline(focused));

        var none = StudyStatsStore.StudyStreak(0, 0, false, 0, 0L)
        var doneToday = StudyStatsStore.StudyStreak(2, 5, true, 3, 1000L)
        var doneNoBest = StudyStatsStore.StudyStreak(1, 0, true, 1, 1000L)
        assertEquals("No streak yet", HomeTextCopy.streakHeadline(none.currentDays));
        assertEquals("2-day streak", HomeTextCopy.streakHeadline(doneToday.currentDays));
        assertEquals("Not done today", HomeTextCopy.streakMetricBody(none.studiedToday, none.bestDays));
        assertEquals("Best: 5 days", HomeTextCopy.streakMetricBody(doneToday.studiedToday, doneToday.bestDays));
        assertEquals("Done today", HomeTextCopy.streakMetricBody(doneNoBest.studiedToday, doneNoBest.bestDays));
        assertEquals("1 day", HomeTextCopy.streakDayCount(1));
        assertEquals("3 days", HomeTextCopy.streakDayCount(3));
    }

private fun verifyStudyTimeRankAndQueueText(activity: MainActivity) {
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

        assertEquals("Needs kanji practice.", FocusQueueCopy.queueCardBody(rowWithReason("裂", "", "", "", emptyList<RecordsImportModels.Example>())));
        assertEquals(
                "Shape mix-up; practice writing.",
                FocusQueueCopy.queueCardBody(rowWithReason("裂", "shape", "レツ", "similar-kanji miss", emptyList<RecordsImportModels.Example>()))
        );
        assertEquals("custom evidence", FocusQueueCopy.queueCardBody(rowWithReason("裂", "shape", "レツ", "custom evidence", emptyList<RecordsImportModels.Example>())));

        assertEquals("write kanji", FocusQueueCopy.recognitionStageLabel(studyItem("裂", RecordsBase.LadderRung.WRITE_KANJI, "review", 0L)));
        assertEquals("type meaning", FocusQueueCopy.recognitionStageLabel(studyItem("裂", RecordsBase.LadderRung.TYPE_MEANING, "review", 0L)));
        assertEquals("similar kanji", FocusQueueCopy.recognitionStageLabel(studyItem("裂", RecordsBase.LadderRung.SIMILAR_KANJI, "review", 0L)));
        assertEquals("font -> meaning", FocusQueueCopy.recognitionStageLabel(studyItem("裂", RecordsBase.LadderRung.FONT_MEANING, "review", 0L)));
        assertEquals("word -> reading", FocusQueueCopy.recognitionStageLabel(studyItem("裂", RecordsBase.LadderRung.WORD_READING, "review", 0L)));
        assertEquals("kanji -> meaning", FocusQueueCopy.recognitionStageLabel(studyItem("裂", RecordsBase.LadderRung.KANJI_MEANING, "review", 0L)));
    }

private fun verifySourceEvidenceAndEmptyQueue(activity: MainActivity) {
        var active = example("活動語", "カツドウゴ", "active", MainActivityBase.SOURCE_ACTIVE)
        var suspended = example("停止語", "テイシゴ", "suspended", MainActivityBase.SOURCE_SUSPENDED)
        assertEquals("From 活動語 · missed 停止語", FocusQueueCopy.sourceEvidenceText(row("語", "language", "ゴ", listOf(active, suspended))));
        assertEquals("From 活動語", FocusQueueCopy.sourceEvidenceText(row("語", "language", "ゴ", listOf(active))));
        assertEquals("Missed 停止語", FocusQueueCopy.sourceEvidenceText(row("語", "language", "ゴ", listOf(suspended))));
        assertEquals("From AnkiDroid", FocusQueueCopy.sourceEvidenceText(row("語", "language", "ゴ", emptyList<RecordsImportModels.Example>())));
        seedRows(activity, listOf(row("空", "empty", "クウ", emptyList<RecordsImportModels.Example>())));
        activity.renderFocusQueue();
        waitForText(activity, MainActivityBase.EMPTY_ACTIVE_PRACTICE_TITLE);
    }

private fun verifyDetailIdentityAndTimeline(activity: MainActivity, inventory: RecordsImportModels.KanjiInventoryItem, row: RecordsImportModels.DashboardRow) {
        assertEquals("裂", HomeTextCopy.detailDisplayKanji("fallback", row, inventory));
        assertEquals("語", HomeTextCopy.detailDisplayKanji("fallback", null, inventory));
        assertEquals("fallback", HomeTextCopy.detailDisplayKanji("fallback", null, null));
        assertEquals("Historical recovery", HomeTextCopy.inventoryTitle(null));
        assertEquals("Historical recovery", HomeTextCopy.inventoryTitle(RecordsImportModels.KanjiInventoryItem("語", "", "", "", 0, 0, false, 0L)));
        assertEquals("language", HomeTextCopy.inventoryTitle(inventory));

        var activeTimeline = RecordsStudyModels.KanjiRecoveryTimeline(inventory, row, studyItem("裂", RecordsBase.LadderRung.KANJI_MEANING, "review", 0L), emptyList<RecordsImportModels.KanjiTimelineEvent>())
        var restingTimeline = RecordsStudyModels.KanjiRecoveryTimeline(inventory, row, studyItem("裂", RecordsBase.LadderRung.KANJI_MEANING, "review", System.currentTimeMillis() + 60_000L), emptyList<RecordsImportModels.KanjiTimelineEvent>())
        var retiredTimeline = RecordsStudyModels.KanjiRecoveryTimeline(inventory, null, studyItem("裂", RecordsBase.LadderRung.KANJI_MEANING, "retired", 0L), emptyList<RecordsImportModels.KanjiTimelineEvent>())
        var noRowTimeline = RecordsStudyModels.KanjiRecoveryTimeline(inventory, null, null, emptyList<RecordsImportModels.KanjiTimelineEvent>())
        var browseDetail = MainActivityHomeBrowseDetail(activity)
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

private fun verifyDetailPanels(activity: MainActivity, inventory: RecordsImportModels.KanjiInventoryItem, row: RecordsImportModels.DashboardRow) {
        var browseDetail = MainActivityHomeBrowseDetail(activity)
        var inventoryIdentity = browseDetail.detailIdentityModel(null, inventory, false)
        assertEquals("language", inventoryIdentity.title);
        assertEquals("ゴ", inventoryIdentity.reading);

        val historicalIdentity = browseDetail.detailIdentityModel(
                null,
                RecordsImportModels.KanjiInventoryItem("謎", "", "", "", 0, 0, false, 0L),
                false
        );
        assertEquals("Historical recovery", historicalIdentity.title);
        assertEquals("", historicalIdentity.reading);

        var inventoryReason = browseDetail.detailReasonPanelModel(null, inventory)
        assertTrue(inventoryReason.lines.contains("Inactive; kept in recovery history."));
        assertTrue(inventoryReason.lines.contains("Anki search: kanji:語"));

        var historicalReason = browseDetail.detailReasonPanelModel(null, null)
        assertTrue(historicalReason.lines.contains("Inactive; kept in recovery history."));
        assertFalse(historicalReason.lines.toString().contains("Anki search:"));

        var activeReason = browseDetail.detailReasonPanelModel(row, inventory)
        assertTrue(activeReason.lines.contains("reason text"));
        assertTrue(activeReason.lines.contains("Anki search: 裂"));
    }

    @Test
fun homeNavigationActionButtonsRenderDestinationScreens() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val metricClicked = booleanArrayOf(false)
                val metric = HomeMetricModel(
                        R.drawable.ic_sync_24,
                        MainActivityBase.TEAL,
                        "Sync",
                        "Ready",
                        "Tap to sync",
                        { metricClicked[0] = true }
                );
                requireNotNull(metric.onClick).invoke()
                assertTrue(metricClicked[0]);

                val headerClicked = booleanArrayOf(false)
                var header = homeSectionHeaderTestView(activity, "Focus queue", "View all", Runnable { headerClicked[0] = true })
                performClickableWithText(header, "View all");
                assertTrue(headerClicked[0]);

                performClickableWithText(homeActionRowTestView(activity), "Browse Kanji");
                assertHasText(activity, "Browse Kanji");

                performClickableWithText(homeActionRowTestView(activity), "Recent mistakes");
                assertHasText(activity, "Recent mistakes");
                waitForText(activity, "No mistakes yet");

                performClickableWithText(homeActionRowTestView(activity), "Stats");
                assertHasText(activity, "Stats");

                performClickableWithText(homeActionRowTestView(activity), "Settings");
                assertHasText(activity, "Automation");

                fullWidthHomeButtonTestView(activity).performClick();
                waitForText(activity, "Kani");
            }
        }
    }

    @Test
fun renderHomeUsesSingleComposeScreenForEmptyAndActiveStates() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var activity: MainActivity
            scenario.onActivity { activity = it }

            scenario.onActivity { activity.renderHome() }
            waitForText(activity, HomeTextCopy.noKanjiQueuedTitle())
            waitForText(activity, HomeTextCopy.syncAnkiDroidLabel())

            scenario.onActivity {
                seedRows(activity, listOf(row("裂", "split", "レツ", emptyList<RecordsImportModels.Example>())));
                activity.renderHome();
            }
            waitForText(activity, MainActivityBase.LABEL_STUDY_NOW)
            waitForText(activity, HomeTextCopy.viewAllLabel())
            waitForText(activity, "裂")
            waitForText(activity, "split")
        }
    }

    @Test
fun homeSyncResultRenderersCoverEmptyAndTerminalStates() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.renderFocusQueue();
                waitForText(activity, "No kanji queued");
                activity.renderRecentMistakes();
                waitForText(activity, "No mistakes yet");

                activity.renderSyncResult(syncResult(false, true, 0, 0, "Already syncing.", ""));
                assertHasText(activity, "Sync already running");
                assertHasText(activity, "Already syncing.");
                activity.renderSyncResult(syncResult(false, true, 0, 0, "", ""));
                assertHasText(activity, "Already reading AnkiDroid.");

                activity.renderSyncResult(syncResult(false, false, 0, 0, "Provider unavailable.", ""));
                assertHasText(activity, "AnkiDroid needs attention");
                assertHasText(activity, "Provider unavailable.");
                activity.renderSyncResult(syncResult(false, false, 0, 0, "", ""));
                assertHasText(activity, "Check AnkiDroid permissions, then retry.");

                activity.renderSyncResult(syncResult(true, false, 0, 2, "Cleanup finished.", "Kani plan: 2 items today"));
                assertHasText(activity, "Sync complete");
                assertHasText(activity, "Cleanup finished.");

                var summary = LinearLayout(activity)
                appendOptionalSyncSummaryLines(activity, summary, syncResult(true, false, 1, 2, "Done.", "Focus summary"));
                assertEquals(3, summary.getChildCount());
                assertEquals("fallback", activity.nonEmptyOr("", "fallback"));
                assertEquals("value", activity.nonEmptyOr("value", "fallback"));
            }
        }
    }

    @Test
fun gamesHostPathsRenderComposeResultAndUnavailableStates() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.renderGames();
                assertHasText(activity, "Games");

                activity.startGame(KanjiGameEngine.GameMode.MEANING_POP);
                assertHasText(activity, "Needs more data");

                seedRows(activity, listOf(
                        row("裂", "split", "レツ", emptyList<RecordsImportModels.Example>()),
                        row("語", "language", "ゴ", emptyList<RecordsImportModels.Example>())
                ));
                activity.startGame(KanjiGameEngine.GameMode.MEANING_POP);
                assertHasText(activity, "Pick the meaning");
                performClickableWithText(activity.findViewById(android.R.id.content), "split");
                assertContainsText(activity.findViewById(android.R.id.content), "Answer:");
                performClickableWithText(activity.findViewById(android.R.id.content), "Next");
                assertHasText(activity, "Pick the meaning");

                activity.startGame(KanjiGameEngine.GameMode.MEANING_POP);
                performClickableWithText(activity.findViewById(android.R.id.content), "language");
                assertContainsText(activity.findViewById(android.R.id.content), "Answer:");
                performClickableWithText(activity.findViewById(android.R.id.content), "Games");
                assertHasText(activity, "Games");
            }
        }
    }

    @Test
fun studyRenderAndProgressHelpersCoverTerminalStudyStates() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var dueLater = RecordsSchedulerModels.AdaptiveLoadPlan(20, 3, 2, listOf("裂", "語"), 0, false, "Two left")
                var complete = RecordsSchedulerModels.AdaptiveLoadPlan(20, 3, 0, listOf("裂", "語"), 0, false, "Done")
                var now = System.currentTimeMillis()

                verifyTerminalStudyScreens(activity, dueLater, complete);
                verifyStudyMoreNewCardRequests(activity, now);
                var session = verifyStudyRunProgressTracking(activity, dueLater)
                verifyActiveStudyTaskTracking(activity);
                verifyTargetedStudyHelpers(activity, session, now);
                verifySimilarKanjiChoiceBuilding(activity, now);
            }
        }
    }

private fun verifyTerminalStudyScreens(activity: MainActivity, dueLater: RecordsSchedulerModels.AdaptiveLoadPlan, complete: RecordsSchedulerModels.AdaptiveLoadPlan) {
        activity.renderEmptyStudyQueue();
        assertHasText(activity, "Nothing to study yet");
        activity.renderNoStudySession(dueLater);
        assertHasText(activity, "Nothing due now");
        performClickableWithText(activity.findViewById(android.R.id.content), MainActivityBase.LABEL_BACK_HOME);
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

private fun verifyStudyMoreNewCardRequests(activity: MainActivity, now: Long) {
        assertFalse(activity.startStudyMoreNewCards(2));
        var requested = EditText(activity)
        requested.setText("not a number");
        assertEquals(-1, activity.requestedStudyMoreNewCards(requested));
        requested.setText("0");
        assertEquals(-1, activity.requestedStudyMoreNewCards(requested));
        assertFalse(activity.applyStudyMoreNewCardsRequest(requested));
        requested.setText("3");
        assertEquals(3, activity.requestedStudyMoreNewCards(requested));

        var unavailable = row("余", "extra", "ヨ", emptyList<RecordsImportModels.Example>())
        seedRows(activity, listOf(unavailable));
        activity.store.saveStudyItem(studyItem("余", RecordsBase.LadderRung.KANJI_MEANING, "review", now));
        assertFalse(activity.startStudyMoreNewCards(2));

        seedRows(activity, listOf(row("新", "new", "シン", emptyList<RecordsImportModels.Example>())));
        var extraRequest = EditText(activity)
        extraRequest.setText("3");
        assertTrue(activity.applyStudyMoreNewCardsRequest(extraRequest));
        assertEquals(1, activity.studySessionTracker.targetCount());
        assertTrue(activity.studyMoreNewCardKanji.contains("新"));
    }

private fun verifyStudyRunProgressTracking(activity: MainActivity, dueLater: RecordsSchedulerModels.AdaptiveLoadPlan): RecordsSchedulerModels.StudySession {
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
        var session = session("裂", BridgeScheduler.TASK_KANJI_MEANING, row("裂", "split", "レツ", emptyList<RecordsImportModels.Example>()))
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

private fun verifyActiveStudyTaskTracking(activity: MainActivity) {
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
        var completedBeforeRepair = activity.studySessionTracker.completedCount()
        activity.startActiveStudyTask("repair:active", "裂", BridgeScheduler.TASK_KANJI_MEANING, 3000L);
        assertTrue(activity.studySessionTracker.hasActiveTask());
        activity.completeActiveRepairStudyTask("repair:active", "passed", 4000L);
        assertFalse(activity.studySessionTracker.hasActiveTask());
        assertEquals(completedBeforeRepair, activity.studySessionTracker.completedCount());
        activity.abandonActiveStudyTask();
    }

private fun verifyTargetedStudyHelpers(activity: MainActivity, session: RecordsSchedulerModels.StudySession, now: Long) {
        var targeted = BridgeScheduler().newTargetedStudyItem("謎", 1234L, activity.studyLadderSettings())
        assertEquals("謎", targeted.kanji);
        assertEquals("new", targeted.state);
        assertEquals(1234L, targeted.dueAtMillis);
        var existingTarget = studyItem("裂", RecordsBase.LadderRung.KANJI_MEANING, "review", now)
        assertSame(existingTarget, BridgeScheduler().targetedStudyItem(listOf(existingTarget), requireNotNull(session.item).kanji, now, activity.studyLadderSettings()));
        assertEquals("new", BridgeScheduler().targetedStudyItem(emptyList<RecordsStudyModels.StudyItem>(), "謎", 1234L, activity.studyLadderSettings()).state);
        assertEquals(activity.dp(300), activity.studyPadHeightForScreenDp(699));
        assertEquals(activity.dp(340), activity.studyPadHeightForScreenDp(700));
        assertEquals(activity.dp(390), activity.studyPadHeightForScreenDp(820));
    }

private fun verifySimilarKanjiChoiceBuilding(activity: MainActivity, now: Long) {
        seedRows(activity, listOf(
                row("裂", "split", "レツ", emptyList<RecordsImportModels.Example>()),
                row("列", "row", "レツ", emptyList<RecordsImportModels.Example>()),
                row("烈", "ardent", "レツ", emptyList<RecordsImportModels.Example>()),
                row("劣", "inferior", "レツ", emptyList<RecordsImportModels.Example>()),
                row("例", "example", "レイ", emptyList<RecordsImportModels.Example>()),
                row("戻", "return", "レイ", emptyList<RecordsImportModels.Example>())
        ));
        activity.store.rebuildSimilarKanjiPairs(similarIndex(
                "裂\t列\n裂\t烈\n裂\t劣\n裂\t例\n裂\t戻\n"
        ), now);
        var choices = activity.buildSimilarKanjiChoices("裂")
        assertEquals(4, choices.size)
        assertTrue(choices.contains("裂"));
    }

    @Test
fun studyDoneActionsStudyMoreAndFallbackPanelsExerciseRealUiBranches() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                seedRows(activity, listOf(
                        row("裂", "split", "レツ", emptyList<RecordsImportModels.Example>()),
                        row("語", "language", "ゴ", emptyList<RecordsImportModels.Example>())
                ));
                var complete = RecordsSchedulerModels.AdaptiveLoadPlan(20, 2, 0, listOf("裂", "語"), 0, false, "Done")

                activity.renderFocusDone(complete);
                assertHasText(activity, "Study more new cards");
                performClickableWithText(activity.findViewById(android.R.id.content), "Study more new cards");
                assertHasText(activity, "How many extra new cards?");
                performClickableWithText(activity.findViewById(android.R.id.content), "Cancel");
                performClickableWithText(activity.findViewById(android.R.id.content), MainActivityBase.LABEL_CONTINUE_ALL_KANJI);
                assertTrue(activity.continueAllKanjiSession);
                activity.renderFocusDone(complete);
                performClickableWithText(activity.findViewById(android.R.id.content), MainActivityBase.LABEL_BACK_HOME);
                assertFalse(activity.continueAllKanjiSession);
                assertHasText(activity, "Kani");

                activity.resetStudyRunProgress();
                activity.studySessionTracker.setTargetCount(2);
                activity.markStudyTaskCompleted("continue:one");
                activity.renderStudyRunDone(complete);
                performClickableWithText(activity.findViewById(android.R.id.content), MainActivityBase.LABEL_CONTINUE_ALL_KANJI);
                assertTrue(activity.continueAllKanjiSession);
                activity.renderStudyRunDone(null);
                performClickableWithText(activity.findViewById(android.R.id.content), MainActivityBase.LABEL_BACK_HOME);
                assertFalse(activity.continueAllKanjiSession);

                var available = activity.availableStudyMoreNewCards()
                if (available > 0) {
                    assertTrue(activity.startStudyMoreNewCards(5));
                    assertTrue(activity.studySessionTracker.targetCount() <= 5);
                    assertFalse(activity.studyMoreNewCardKanji.isEmpty());
                }

                val promptOnly = RecordsSchedulerModels.StudySession(
                        studyItem("?", RecordsBase.LadderRung.KANJI_MEANING, "review", 0L),
                        null,
                        "answer-token",
                        BridgeScheduler.TASK_KANJI_MEANING,
                        false,
                        "Prompt fallback"
                );
                var promptAnswerPanel = flashcardAnswerPanelTestView(activity, promptOnly)
                val root = activity.findViewById<ViewGroup>(android.R.id.content)
                root.addView(promptAnswerPanel);
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                assertHasText(activity, "Prompt fallback");
                root.removeView(promptAnswerPanel);
                assertEquals("split", StudyTextCopy.collectionMeaningForSession(session("裂", BridgeScheduler.TASK_KANJI_MEANING, row("裂", "split", "レツ", emptyList<RecordsImportModels.Example>()))));
                assertEquals(Typeface.SERIF, activity.fontResource(0, Typeface.SERIF));
            }
        }
    }

    @Test
fun studyRenderingBranchesCoverFallbacksAndWritingActions() {
        val recognizer = FakeWritingRecognizer(
                CompletableFuture.completedFuture(WritingRecognizer.ModelStatus("ja", "ja-JP", true, "ready")),
                failedFuture(RuntimeException("network unavailable")),
                CompletableFuture.completedFuture(WritingRecognizer.RecognitionResult(listOf(
                        WritingRecognizer.Candidate("裂", 0.9f)
                )))
        );
        MainActivityRuntimeOverrides.setWritingRecognizer(recognizer)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var row = row("裂", "split", "レツ", emptyList<RecordsImportModels.Example>())
                assertNull(activity.firstExample(null));
                assertNull(activity.javaClass.getMethod("wordReadingExample", RecordsImportModels.DashboardRow::class.java).invoke(activity, null));

                val promptOnly = RecordsSchedulerModels.StudySession(
                        studyItem("?", RecordsBase.LadderRung.WRITE_KANJI, "review", 0L),
                        null,
                        "prompt-token",
                        BridgeScheduler.TASK_WRITE_KANJI,
                        true,
                        "Prompt only"
                );
                assertTrue(containsText(learningPanelTestView(activity, promptOnly), "Prompt only"));
                assertTrue(containsText(heroKanjiPanelTestView(activity, session("裂", BridgeScheduler.TASK_FONT_MEANING, row)), "裂"));
                assertNotNull(activity.randomFontVariantTypeface());

                var similarFallback = session("裂", BridgeScheduler.TASK_SIMILAR_KANJI, row)
                activity.activeSession = similarFallback;
                activity.renderSession(similarFallback);
                assertNotNull(activity.flashcardActionBarState);
                assertFalse(activity.flashcardAnswerRevealed);
                activity.store.rebuildSimilarKanjiPairs(similarIndex("裂\t列\n裂\t烈\n"), System.currentTimeMillis());
                activity.renderSession(session("裂", BridgeScheduler.TASK_SIMILAR_KANJI, row));
                assertHasText(activity, MainActivityBase.LABEL_SIMILAR_KANJI);
                assertHasText(activity, "Which kanji means split?");
                assertNull(activity.flashcardGestureBounds);
                assertFalse(activity.flashcardAnswerRevealed);
                seedRows(activity, listOf(
                        row("裂", "split", "レツ", emptyList<RecordsImportModels.Example>()),
                        row("列", "row", "レツ", emptyList<RecordsImportModels.Example>()),
                        row("烈", "ardent", "レツ", emptyList<RecordsImportModels.Example>()),
                        row("劣", "inferior", "レツ", emptyList<RecordsImportModels.Example>())
                ));
                activity.renderSession(session("裂", BridgeScheduler.TASK_MEANING_KANJI, row));
                assertNull(activity.flashcardGestureBounds);
                assertFalse(activity.flashcardAnswerRevealed);
                val root = activity.findViewById<ViewGroup>(android.R.id.content)
                performClickableWithText(root, "裂");
                assertHasText(activity, "Correct");
                assertEquals(0, activity.store.reviewStatsSince(0L).total);
                performClickableWithText(root, "Next");
                assertEquals(1, activity.store.reviewStatsSince(0L).good);

                var meaningFallback = session("返", BridgeScheduler.TASK_MEANING_KANJI, row("返", "return", "ヘン", emptyList<RecordsImportModels.Example>()))
                activity.activeSession = meaningFallback;
                activity.renderSession(meaningFallback);
                assertNotNull(activity.flashcardActionBarState);

                var recall = session("裂", "blind_writing", row)
                activity.activeSession = recall;
                activity.renderSession(recall);
                assertHasText(activity, "Prompt: Split, rend");
                val toolActions = requireNotNull(activity.writingToolActionsView).currentModel()
                val primaryActions = requireNotNull(activity.writingPrimaryActionsView).currentModel()
                val fallbackActions = requireNotNull(activity.writingFallbackActionsView).currentModel()
                assertFalse(toolActions.undoEnabled)
                assertTrue(toolActions.hintVisible)
                assertTrue(primaryActions.checkVisible)
                assertTrue(primaryActions.checkEnabled)
                assertFalse(primaryActions.nextVisible)
                assertFalse(fallbackActions.replayVisible)
                assertTrue(requireNotNull(activity.drawingPad).parent is MainActivityUiSupport.SquarePadFrame)
                assertEquals(View.GONE, requireNotNull(activity.writingResultStatus).getVisibility())
                performClickableWithText(activity.findViewById(android.R.id.content), "Erase");

                activity.activeSession = promptOnly;
                activity.renderSession(promptOnly);
                assertHasText(activity, "Prompt only");

                val nullPromptOnly = RecordsSchedulerModels.StudySession(
                        studyItem("?", RecordsBase.LadderRung.WRITE_KANJI, "review", 0L),
                        null,
                        "null-prompt-token",
                        BridgeScheduler.TASK_WRITE_KANJI,
                        true,
                        null
                );
                activity.activeSession = nullPromptOnly;
                activity.renderSession(nullPromptOnly);
                assertHasText(activity, "Draw this kanji");

                activity.activeSession = null;
                activity.buildComposeWritingActionBarState();
                activity.checkWriting();
                activity.submitReview(MainActivityBase.RATING_GOOD, false);
                activity.showWritingHint();
                activity.startCleanerRetry();
                activity.replayWritingAnalysis();

                activity.activeSession = promptOnly;
                activity.currentHintState = HintState.fromWritingLevel(2);
                activity.studyStatus = WritingStatusState();
                prepareWritingActionViews(activity);
                MainActivityStudyWritingStatus(activity).downloadWritingModel();
            }
            scenario.onActivity { activity -> assertTrue(requireNotNull(activity.studyStatus).getText().toString().contains("download failed")) }
        }
        } finally {
            MainActivityRuntimeOverrides.setWritingRecognizer(null);
        }
    }

    @Test
fun writingUnavailableAndAsyncTokenGuardsLeaveVisibleStateConsistent() {
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var row = row("裂", "split", "レツ", emptyList<RecordsImportModels.Example>())
                var writing = session("裂", BridgeScheduler.TASK_WRITE_KANJI, row)
                activity.activeSession = writing;
                activity.currentHintState = HintState.fromWritingLevel(1);
                activity.studyStatus = WritingStatusState();
                activity.writingResultStatus = WritingResultStatusHandle();
                prepareWritingActionViews(activity);
                activity.studyAnswerPanel = LinearLayout(activity);
                activity.drawingPad = DrawingPadView(activity);
                requireNotNull(activity.drawingPad).setTarget("裂");
                addInk(requireNotNull(activity.drawingPad));
                activity.checkingWriting = true;
                activity.checkWriting();
                assertTrue(activity.checkingWriting);
                activity.checkingWriting = false;

                val staleRecognizer = FakeWritingRecognizer(
                        CompletableFuture.completedFuture(WritingRecognizer.ModelStatus("ja", "ja-JP", false, "missing")),
                        CompletableFuture.completedFuture(WritingRecognizer.ModelStatus("ja", "ja-JP", true, "ready")),
                        CompletableFuture.completedFuture(WritingRecognizer.RecognitionResult(listOf(
                                WritingRecognizer.Candidate("裂", 0.9f)
                        )))
                );
                MainActivityRuntimeOverrides.setWritingRecognizer(staleRecognizer);
                activity.activeSession = sessionWithToken("裂", BridgeScheduler.TASK_WRITE_KANJI, row, "current-token");
                activity.recognizeWriting(staleRecognizer, capturedWriting(), sample(), guide("裂"), "裂", "stale-token");
                MainActivityStudyWritingStatus(activity).downloadWritingModel();
                activity.activeSession = sessionWithToken("裂", BridgeScheduler.TASK_WRITE_KANJI, row, "changed-token");
            }
            scenario.onActivity { activity ->
                assertNull(activity.activeAnalysis);
                val errorRecognizer = FakeWritingRecognizer(
                        CompletableFuture.completedFuture(WritingRecognizer.ModelStatus("ja", "ja-JP", true, "ready")),
                        CompletableFuture.completedFuture(WritingRecognizer.ModelStatus("ja", "ja-JP", true, "ready")),
                        failedFuture(RuntimeException("recognition failed"))
                );
                var row = row("裂", "split", "レツ", emptyList<RecordsImportModels.Example>())
                activity.activeSession = sessionWithToken("裂", BridgeScheduler.TASK_WRITE_KANJI, row, "error-token");
                activity.recognizeWriting(errorRecognizer, capturedWriting(), sample(), guide("裂"), "裂", "error-token");
            }
            scenario.onActivity { activity -> assertEquals(WritingAnalysis.Status.RECOGNITION_ERROR, requireNotNull(activity.activeAnalysis).status) }
        }
        } finally {
            MainActivityRuntimeOverrides.setWritingRecognizer(null);
        }
    }

    @Test
fun writingRecognitionUnavailableAndInvalidCaptureBranchesShowActionableState() {
        val staleStatusRecognizer = FakeWritingRecognizer(
                CompletableFuture.completedFuture(WritingRecognizer.ModelStatus("ja", "ja-JP", false, "missing")),
                CompletableFuture.completedFuture(WritingRecognizer.ModelStatus("ja", "ja-JP", true, "ready")),
                CompletableFuture.completedFuture(WritingRecognizer.RecognitionResult(listOf(
                        WritingRecognizer.Candidate("裂", 0.9f)
                )))
        );
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var row = row("裂", "split", "レツ", emptyList<RecordsImportModels.Example>())
                prepareWritingUi(activity, sessionWithToken("裂", BridgeScheduler.TASK_WRITE_KANJI, row, "invalid-capture"));
                MainActivityRuntimeOverrides.setWritingRecognizer(staleStatusRecognizer);
                activity.drawingPad = DrawingPadView(activity);
                requireNotNull(activity.drawingPad).setTarget("裂");
                addInk(requireNotNull(activity.drawingPad));
                activity.checkWriting();
                assertEquals(WritingAnalysis.Status.NO_INK, requireNotNull(activity.activeAnalysis).status);

                MainActivityRuntimeOverrides.setWritingRecognizer(null);
                MainActivityRuntimeOverrides.setWritingRecognizerFactory { _ -> null }
                prepareWritingUi(activity, sessionWithToken("裂", BridgeScheduler.TASK_WRITE_KANJI, row, "null-recognizer"));
                layoutPad(requireNotNull(activity.drawingPad));
                addInk(requireNotNull(activity.drawingPad));
                activity.checkWriting();
                assertEquals(WritingAnalysis.Status.MODEL_UNAVAILABLE, requireNotNull(activity.activeAnalysis).status);

                activity.activeAnalysis = null;
                MainActivityStudyWritingStatus(activity).refreshWritingModelStatus();
                assertTrue(requireNotNull(activity.studyStatus).getText().toString().contains("Automatic handwriting checks are unavailable"));
                MainActivityStudyWritingStatus(activity).downloadWritingModel();
                assertTrue(requireNotNull(activity.studyStatus).getText().toString().contains("unavailable on this device"));

                MainActivityRuntimeOverrides.setWritingRecognizerFactory { _ ->
                    throw RuntimeException("ml kit unavailable")
                }
                activity.writingRecognizer = null
                assertNull(activity.currentWritingRecognizer());

                MainActivityRuntimeOverrides.setWritingRecognizerFactory(null);
                MainActivityRuntimeOverrides.setWritingRecognizer(staleStatusRecognizer);
                prepareWritingUi(activity, sessionWithToken("裂", BridgeScheduler.TASK_WRITE_KANJI, row, "old-token"));
                layoutPad(requireNotNull(activity.drawingPad));
                addInk(requireNotNull(activity.drawingPad));
                activity.checkWriting();
                activity.activeSession = sessionWithToken("裂", BridgeScheduler.TASK_WRITE_KANJI, row, "new-token");
            }
            scenario.onActivity { activity -> assertNull(activity.activeAnalysis) }
        }
        } finally {
            MainActivityRuntimeOverrides.setWritingRecognizer(null);
            MainActivityRuntimeOverrides.setWritingRecognizerFactory(null);
        }
    }

    @Test
fun schedulerTuningPersistsWhenRecentReviewsJustifyAnAdjustment() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var now = System.currentTimeMillis()
                for (i in 0 until 22) {
                    activity.store.saveReview(
                            RecordsSchedulerModels.ReviewRequest("調" + i, "tune-token-" + i, MainActivityBase.RATING_GOOD, false, false, false, 0),
                            MainActivityBase.RATING_GOOD,
                            now - (i * 1000L)
                    );
                }
                var before = RecordsSchedulerModels.SchedulerParameters.defaults()

                activity.tuneSchedulerIfNeeded(before, now);

                var tuned = activity.store.schedulerParameters()
                assertEquals(now, tuned.lastAdjustedAtMillis);
                assertEquals(22, tuned.lastAdjustmentReviewCount);
                assertTrue(tuned.goodMultiplier > before.goodMultiplier);
            }
        }
    }

    @Test
fun flashcardAndWritingUiStateHelpersCoverInteractiveBranches() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var row = row("裂", "split", "レツ", emptyList<RecordsImportModels.Example>())
                var flashcard = session("裂", BridgeScheduler.TASK_KANJI_MEANING, row)
                var writing = session("裂", BridgeScheduler.TASK_WRITE_KANJI, row)
                activity.activeSession = flashcard;

                verifyTokenCandidatesAndReviewToasts(activity);
                verifyFlashcardActionBarAndGestureBranches(activity, writing);
                prepareWritingControls(activity, writing);
                val order = StrokeOrderEvaluator
                        .evaluate(guide("裂"), sample())
                        .withDiagnosis(StrokeDiagnosis.builder().add(StrokeDiagnosis.Label.WRONG_ORDER, 1).build());
                var wrong = analysis(WritingAnalysis.Status.WRONG, false, order)
                verifyWritingButtonAndModelStatus(activity, wrong);
                verifyTeachingHintAndHelpState(activity, writing, row, order);
            }
        }
    }

private fun verifyTokenCandidatesAndReviewToasts(activity: MainActivity) {
        assertTrue(activity.isActiveToken("tok"));
        assertFalse(activity.isActiveToken("missing"));
        assertEquals("", WritingFeedbackCopy.candidateText(null));
        assertEquals(
                "裂, 列, 烈",
                WritingFeedbackCopy.candidateText(listOf(
                        RecognitionCandidate("裂", 0.9f),
                        RecognitionCandidate("列", 0.5f),
                        RecognitionCandidate("烈", 0.4f),
                        RecognitionCandidate("劣", 0.3f)
                ))
        );
        assertEquals(2, activity.candidates(WritingRecognizer.RecognitionResult(listOf(
                WritingRecognizer.Candidate("裂", 0.9f),
                WritingRecognizer.Candidate("列", null)
        ))).size)
        assertTrue(activity.candidates(null).isEmpty());

        var streak = StudyStatsStore.StudyStreak(2, 2, true, 1, 1000L)
        assertEquals("Already saved.", HomeTextCopy.reviewToast(true, "duplicate", streak.currentDays));
        assertTrue(HomeTextCopy.reviewToast(false, MainActivityBase.RATING_AGAIN, streak.currentDays).contains("2-day streak"));
        assertEquals("Saved. This kanji moved forward.", HomeTextCopy.reviewToast(false, MainActivityBase.RATING_GOOD, 0));
    }

private fun verifyFlashcardActionBarAndGestureBranches(activity: MainActivity, writing: RecordsSchedulerModels.StudySession) {
        activity.buildFlashcardActionBar(false);
        assertNotNull(activity.flashcardActionBarState);
        assertFalse(requireNotNull(activity.flashcardActionBarState).revealed);
        activity.buildFlashcardActionBar(true);
        assertTrue(requireNotNull(activity.flashcardActionBarState).revealed);
        activity.flashcardAnswerRevealed = true;
        activity.revealFlashcardAnswer();
        activity.expandFlashcardForAnswer();

        assertFalse(activity.handleFlashcardGesture(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 1f, 1f, 0)));
        activity.activeSession = writing;
        assertEquals(MainActivityBase.LABEL_PRACTICE, StudyTaskCopy.studyModeLabel(writing));
        assertFalse(activity.handleFlashcardGesture(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 1f, 1f, 0)));
    }

private fun prepareWritingControls(activity: MainActivity, writing: RecordsSchedulerModels.StudySession) {
        activity.activeSession = writing;
        activity.currentHintState = HintState.fromWritingLevel(3);
        activity.studyStatus = WritingStatusState();
        activity.writingResultStatus = WritingResultStatusHandle();
        prepareWritingActionViews(activity);
        activity.studyAnswerPanel = LinearLayout(activity);
    }

private fun verifyWritingButtonAndModelStatus(activity: MainActivity, wrong: WritingAnalysis) {
        var writingStatus = MainActivityStudyWritingStatus(activity)
        activity.checkingWriting = true;
        activity.updateResultActions();
        var primary = requireNotNull(activity.writingPrimaryActionsView).currentModel()
        assertEquals("Checking...", primary.checkText);
        assertFalse(primary.checkEnabled);
        activity.checkingWriting = false;
        activity.activeAnalysis = WritingAnalysis(
                WritingAnalysis.Status.CLOSE,
                MainActivityBase.RATING_HARD,
                true,
                "Messy",
                emptyList<RecognitionCandidate>(),
                wrong.strokeOrder
        )
        activity.updateResultActions();
        primary = requireNotNull(activity.writingPrimaryActionsView).currentModel();
        assertEquals("Try cleaner", primary.checkText);
        assertTrue(primary.checkEnabled);

        activity.writingModelStatusKnown = true;
        activity.writingModelDownloaded = true;
        activity.updateResultActions();
        primary = requireNotNull(activity.writingPrimaryActionsView).currentModel();
        assertFalse(primary.downloadVisible);
        activity.activeAnalysis = wrong;
        activity.updateResultActions();
        primary = requireNotNull(activity.writingPrimaryActionsView).currentModel();
        assertTrue(primary.nextVisible);
        assertEquals("Fail", primary.nextText);

        var fallback = requireNotNull(activity.writingFallbackActionsView).currentModel()
        assertTrue(fallback.manualOverrideVisible);
        assertTrue(fallback.practiceWithGuideVisible);
        activity.showModelUnavailable("checker unavailable");
        assertTrue(requireNotNull(activity.writingResultStatus).getText().toString().contains("checker unavailable"));
        assertEquals(View.VISIBLE, requireNotNull(activity.writingResultStatus).getVisibility());

        writingStatus.setWritingModelStatusMessage(null, RuntimeException("offline"));
        assertTrue(requireNotNull(activity.studyStatus).getText().toString().contains("Unable to read"));
        writingStatus.setWritingModelStatusMessage(WritingRecognizer.ModelStatus("ja", "ja-JP", false, "missing"), null);
        assertTrue(requireNotNull(activity.studyStatus).getText().toString().contains("Download the handwriting checker"));
        writingStatus.setWritingModelStatusMessage(WritingRecognizer.ModelStatus("ja", "ja-JP", true, "ready"), null);
        assertTrue(requireNotNull(activity.studyStatus).getText().toString().contains("Handwriting checker ready"));
    }

private fun verifyTeachingHintAndHelpState(activity: MainActivity, writing: RecordsSchedulerModels.StudySession, row: RecordsImportModels.DashboardRow, order: StrokeOrderEvaluator.StrokeOrderResult) {
        activity.activeAnalysis = null;
        assertTrue(activity.showNoInkWhenNeeded());
        assertEquals(WritingAnalysis.Status.NO_INK, requireNotNull(activity.activeAnalysis).status);
        assertFalse(StudyTaskCopy.isTeachingTask(null));
        assertTrue(StudyTaskCopy.isTeachingTask(session("裂", "context_writing", row)));
        assertTrue(StudyTaskCopy.isTeachingTask(session("裂", "guided_writing", row)));
        assertTrue(StudyTaskCopy.isTeachingTask(session("裂", MainActivityBase.TASK_TARGETED_WRITING, row)));
        assertFalse(StudyTaskCopy.isTeachingTask(RecordsSchedulerModels.StudySession(
                studyItem("裂", RecordsBase.LadderRung.WRITE_KANJI, "review", 0L).copyBuilder().learningStep(3).build(),
                row,
                "tok-target-done",
                MainActivityBase.TASK_TARGETED_WRITING,
                true,
                "split"
        )));
        assertEquals(HintLevel.OUTLINE, activity.initialHintState(session("裂", MainActivityBase.TASK_TARGETED_WRITING, row)).level());
        assertEquals(HintLevel.OUTLINE, activity.initialHintState(RecordsSchedulerModels.StudySession(
                studyItem("裂", RecordsBase.LadderRung.WRITE_KANJI, "review", 0L).copyBuilder().totalReviews(0).writingLevel(3).build(),
                row,
                "tok-new",
                BridgeScheduler.TASK_WRITE_KANJI,
                true,
                "split"
        )).level());
        assertEquals(HintLevel.BLIND, activity.initialHintState(RecordsSchedulerModels.StudySession(
                studyItem("裂", RecordsBase.LadderRung.WRITE_KANJI, "review", 0L).copyBuilder().learningStep(2).writingLevel(3).build(),
                row,
                "tok-mature",
                BridgeScheduler.TASK_WRITE_KANJI,
                true,
                "split"
        )).level());
        var nextHintState = HintState.fromWritingLevel(2)
        activity.currentPracticeLevel = 99;
        activity.setHintState(nextHintState);
        assertEquals(nextHintState, activity.currentHintState);
        assertEquals(nextHintState.level().writingLevel(), activity.currentPracticeLevel);
        activity.setHintState(null);
        assertEquals(HintState.initial(), activity.currentHintState);
        assertEquals(HintState.initial().level().writingLevel(), activity.currentPracticeLevel);
        verifyHelpAndLearningPanelState(activity, writing, row, order);
    }

private fun verifyHelpAndLearningPanelState(activity: MainActivity, writing: RecordsSchedulerModels.StudySession, row: RecordsImportModels.DashboardRow, order: StrokeOrderEvaluator.StrokeOrderResult) {
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
fun flashcardGestureTrackingCoversMissingTypingBoundsCancelAndOutsideRelease() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var row = row("裂", "split", "レツ", emptyList<RecordsImportModels.Example>())
                activity.activeSession = session("裂", BridgeScheduler.TASK_TYPE_MEANING, row);
                var area = LinearLayout(activity)
                activity.findViewById<ViewGroup>(android.R.id.content).addView(area, LinearLayout.LayoutParams(300, 300))
                area.layout(0, 0, 300, 300);
                activity.setFlashcardGestureBounds(0f, 0f, 300f, 300f);
                activity.typingAnswerState = TypingAnswerState();
                assertFalse(requireNotNull(activity.typingAnswerState).containsWindowPoint(40f, 40f));

                assertFalse(activity.handleFlashcardGesture(motion(MotionEvent.ACTION_DOWN, 40f, 40f)));
                assertTrue(activity.flashcardTouchTracking);

                activity.typingAnswerState = null;
                activity.flashcardTouchTracking = false;
                assertFalse(activity.handleFlashcardGesture(motion(MotionEvent.ACTION_UP, 40f, 40f)));
                activity.flashcardTouchTracking = true;
                assertFalse(activity.handleFlashcardGesture(motion(MotionEvent.ACTION_CANCEL, 40f, 40f)));
                assertFalse(activity.flashcardTouchTracking);

                activity.flashcardTouchTracking = true;
                assertFalse(activity.handleFlashcardGesture(motion(MotionEvent.ACTION_UP, 400f, 400f)));

                area.setVisibility(View.GONE);
                assertFalse(activity.isTouchInsideView(area, motion(MotionEvent.ACTION_DOWN, 40f, 40f)));
                assertFalse(activity.isTouchInsideView(View(activity), motion(MotionEvent.ACTION_DOWN, 40f, 40f)));
                activity.flashcardAnswerRevealed = true;
                activity.flashcardTouchStartX = 100f;
                activity.flashcardTouchStartY = 100f;
                assertFalse(activity.handleFlashcardRelease(motion(MotionEvent.ACTION_UP, 102f, 102f)));
            }
        }
    }

    @Test
fun flashcardButtonsAndGesturesPersistPassFailOnlyAfterReveal() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var row = row("裂", "split", "レツ", emptyList<RecordsImportModels.Example>())
                var failSession = sessionWithToken("裂", BridgeScheduler.TASK_KANJI_MEANING, row, "fail-token")
                activity.activeSession = failSession;
                activity.activeStudyPlan = RecordsSchedulerModels.AdaptiveLoadPlan(20, 1, 1, listOf("裂"), 0, false, "One left");
                activity.startActiveStudyTask(activity.sessionTaskKey(failSession), "裂", failSession.taskType, System.currentTimeMillis());
                activity.renderSession(failSession);
                assertNotNull(activity.flashcardActionBarState);
                var root = activity.findViewById<ViewGroup>(android.R.id.content)
                performClickableWithText(root, "Reveal");
                assertTrue(activity.flashcardAnswerRevealed);
                assertTrue(containsText(root, "split"));
                activity.flashcardTouchStartX = 100f;
                activity.flashcardTouchStartY = 100f;
                assertTrue(activity.handleFlashcardRelease(motion(MotionEvent.ACTION_UP, 20f, 100f)));
                var gestureFailStats = activity.store.reviewStatsSince(0L)
                assertEquals(1, gestureFailStats.total);
                assertEquals(1, gestureFailStats.again);

                failSession = sessionWithToken("裂", BridgeScheduler.TASK_KANJI_MEANING, row, "fail-token-button");
                activity.activeSession = failSession;
                activity.startActiveStudyTask(activity.sessionTaskKey(failSession), "裂", failSession.taskType, System.currentTimeMillis());
                activity.renderSession(failSession);
                root = activity.findViewById(android.R.id.content);
                performClickableWithText(root, "Reveal");
                performClickableWithText(root, "Fail");
                var failStats = activity.store.reviewStatsSince(0L)
                assertEquals(2, failStats.total);
                assertEquals(2, failStats.again);

                var passSession = sessionWithToken("語", BridgeScheduler.TASK_KANJI_MEANING, row("語", "language", "ゴ", emptyList<RecordsImportModels.Example>()), "pass-token")
                activity.activeSession = passSession;
                activity.startActiveStudyTask(activity.sessionTaskKey(passSession), "語", passSession.taskType, System.currentTimeMillis());
                activity.renderSession(passSession);
                assertNotNull(activity.flashcardActionBarState);
                root = activity.findViewById(android.R.id.content);
                performClickableWithText(root, "Reveal");
                performClickableWithText(root, "Pass");
                var passStats = activity.store.reviewStatsSince(0L)
                assertEquals(3, passStats.total);
                assertEquals(1, passStats.good);

                var gestureSession = sessionWithToken("提", BridgeScheduler.TASK_KANJI_MEANING, row("提", "carry", "テイ", emptyList<RecordsImportModels.Example>()), "gesture-token")
                activity.activeSession = gestureSession;
                activity.studyAnswerPanel = LinearLayout(activity);
                activity.flashcardHeroPanel = LinearLayout(activity);
                activity.flashcardRevealState = null;
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
                var gestureStats = activity.store.reviewStatsSince(0L)
                assertEquals(4, gestureStats.total);
                assertEquals(3, gestureStats.again);
            }
        }
    }

    @Test
fun homeBrowseDetailStatsAndSyncControlsCoverNonEmptyBranches() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var activeRow = row("裂", "split", "レツ", listOf(example("裂語", "レツゴ", "split word", MainActivityBase.SOURCE_ACTIVE)))
                verifyHomeBrowseRowsAndDetail(activity, activeRow);
                verifyRecentMistakesAndEmptyTimeline(activity);
                verifyStatsVerdictBranches(activity, activeRow);
                verifySyncResultStudyNow(activity);
            }
        }
    }

private fun verifyHomeBrowseRowsAndDetail(activity: MainActivity, activeRow: RecordsImportModels.DashboardRow) {
        var passiveMetric = HomeMetricModel(R.drawable.ic_target_24, MainActivityBase.CORAL, "Focus", "Waiting", null, null)
        assertNull(passiveMetric.onClick);
                assertFalse(containsText(homeSectionHeaderTestView(activity, "Focus queue", null, null), "Focus queue >"));
        val activeExample = MainActivityHomeBrowseDetail(activity)
                .exampleModel(example("裂語", "レツゴ", "split word", MainActivityBase.SOURCE_ACTIVE));
        assertEquals("裂語  レツゴ", activeExample.expression);
        assertEquals("split word", activeExample.meaning);
        val matureSupportThreshold = activity.settings().matureSupportThreshold
        val relearning = studyItem("裂", RecordsBase.LadderRung.KANJI_MEANING, "review", 0L)
                .copyBuilder()
                .phase(RecordsBase.SchedulerPhase.RELEARNING)
                .build();
        var relearningRow = homeFocusQueueCardModel(
                activity,
                MainActivityBase.QueueEntry(activeRow, relearning),
                1000L,
                matureSupportThreshold,
        )
        assertTrue(relearningRow.tags.any { it.label == "relearning" })
        val learning = studyItem("裂", RecordsBase.LadderRung.KANJI_MEANING, "review", 0L)
                .copyBuilder()
                .phase(RecordsBase.SchedulerPhase.NEW_LEARNING)
                .totalReviews(1)
                .build();
        var learningRow = homeFocusQueueCardModel(
                activity,
                MainActivityBase.QueueEntry(activeRow, learning),
                1000L,
                matureSupportThreshold,
        )
        assertTrue(learningRow.tags.any { it.label == "learning" })
        seedRows(activity, listOf(activeRow));
        activity.renderStudyForKanji("裂");
        assertHasText(activity, "Name this kanji");
        activity.store.setKanjiLocallySuspended("裂", true, 1000L);

        activity.renderBrowseKanji("裂");
        assertEquals("裂", activity.activeBrowseQuery);
        assertHasText(activity, "SUSPENDED");
        performClickableWithText(activity.findViewById(android.R.id.content), "split");
        assertHasText(activity, "Back to Browse");
        assertHasText(activity, "Local records");
        performClickableWithText(activity.findViewById(android.R.id.content), "Unsuspend locally");
        assertFalse(activity.store.isKanjiLocallySuspended("裂"));
        activity.renderDetail("missing", false, "");
        assertHasText(activity, "Kanji not found");
    }

private fun verifyRecentMistakesAndEmptyTimeline(activity: MainActivity) {
        activity.renderRecentMistakes();
        waitForText(activity, "No mistakes yet");
        activity.store.saveReview(RecordsSchedulerModels.ReviewRequest("裂", "miss-token", "again", false, false, false, 0), "again", 2000L);
        activity.renderRecentMistakes();
        waitForText(activity, "Rated again");

        val emptyTimeline = MainActivityHomeBrowseDetail(activity)
                .recoveryTimelineModel(RecordsStudyModels.KanjiRecoveryTimeline(null, null, null, emptyList<RecordsImportModels.KanjiTimelineEvent>()));
        assertEquals("No active Anki evidence.", emptyTimeline.supportText);
    }

private fun verifyStatsVerdictBranches(activity: MainActivity, activeRow: RecordsImportModels.DashboardRow) {
        assertEquals("Waiting for evidence", StatsTextCopy.verdictTitle(false));
        val ladderOnly = StudyStatsStore.LadderHealthMetric(
                Collections.singletonMap(RecordsBase.LadderRung.KANJI_MEANING, 1),
                1,
                3,
                0,
                0,
                0
        );
        val ladderStats = StudyStatsStore.KaniOutcomeStats(
                StudyStatsStore.WeakKanjiImprovedMetric.empty(),
                StudyStatsStore.MatureSupportGainedMetric.empty(),
                ladderOnly
        );
        assertTrue(StatsTextCopy.verdictBody(false, false, false, 0, 0, 0, 0, 0).contains("Study and sync to see trends."));
        assertTrue(StatsTextCopy.verdictBody(true, false, true, 0, 0, 1, 3, 1).contains("Tracking 1 active kanji"));
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

private fun verifyWorkingStatsVerdict(activity: MainActivity, activeRow: RecordsImportModels.DashboardRow) {
        val busyLadder = StudyStatsStore.LadderHealthMetric(
                Collections.singletonMap(RecordsBase.LadderRung.KANJI_MEANING, 4),
                4,
                3,
                1,
                2,
                1
        );
        val workingStats = StudyStatsStore.KaniOutcomeStats(
                StudyStatsStore.WeakKanjiImprovedMetric(
                        2,
                        0.7,
                        0.2,
                        listOf(
                                StudyStatsStore.KanjiImprovement("裂", 0.7, 0.2),
                                StudyStatsStore.KanjiImprovement("語", 0.5, 0.1),
                                StudyStatsStore.KanjiImprovement("提", 0.4, 0.2),
                                StudyStatsStore.KanjiImprovement("余", 0.6, 0.3)
                        )
                ),
                StudyStatsStore.MatureSupportGainedMetric(
                        1,
                        3,
                        1,
                        listOf(StudyStatsStore.KanjiSupportGain("語", 0, 3))
                ),
                busyLadder
        );
        val workingBody = StatsTextCopy.verdictBody(
                true,
                true,
                true,
                workingStats.weakKanjiImproved.improvedCount,
                workingStats.matureSupportGained.matureSupportGained,
                busyLadder.promotionReadyCount,
                busyLadder.demotionRiskCount,
                busyLadder.totalActiveItems
        );
        assertTrue(workingBody.contains("weak kanji improved"));
        assertTrue(workingBody.contains("mature card gained"));
        assertTrue(workingBody.contains("review items ready to climb"));
        assertTrue(workingBody.contains("review item with a miss streak"));
        assertTrue(StatsTextCopy.ladderHealthBody(
                busyLadder.totalActiveItems,
                busyLadder.promotionReadyCount,
                busyLadder.demotionRiskCount,
                busyLadder.demotionReadyCount,
                busyLadder.ladderPromotionIntervalDays,
                busyLadder.ladderDemotionFailStreak
        ).contains("fall after 1 misses"));
        assertTrue(activity.notHelpingRows(null).isEmpty());
        assertEquals(3, activity.weaknessImprovementExamples(workingStats.weakKanjiImproved).size)
        assertTrue(activity.supportGainExamples(workingStats.matureSupportGained).get(0).contains("0 -> 3 mature cards"));
        assertTrue(activity.queuedEntries(
                listOf(activeRow),
                listOf(studyItem("裂", RecordsBase.LadderRung.KANJI_MEANING, "review", 0L)),
                System.currentTimeMillis()
        ) != null);
    }

private fun verifySyncResultStudyNow(activity: MainActivity) {
        activity.renderSyncResult(syncResult(true, false, 1, 0, "", ""));
        performClickableWithText(activity.findViewById(android.R.id.content), MainActivityBase.LABEL_STUDY_NOW);
        assertHasText(activity, "Name this kanji");
        assertHasText(activity, "What does this kanji mean?");

        var summary = LinearLayout(activity)
        appendOptionalSyncSummaryLines(activity, summary, syncResult(true, false, 1, 0, "", ""));
        assertEquals(0, summary.getChildCount());
        val emptyTimeline = MainActivityHomeBrowseDetail(activity)
                .recoveryTimelineModel(RecordsStudyModels.KanjiRecoveryTimeline(null, null, null, emptyList<RecordsImportModels.KanjiTimelineEvent>()));
        assertEquals("No timeline events yet.", emptyTimeline.emptyText);
    }

    @Test
fun writingCallbacksResetResultStateAfterInkEditsAndCleanerRetry() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var row = row("裂", "split", "レツ", emptyList<RecordsImportModels.Example>())
                var writing = session("裂", BridgeScheduler.TASK_WRITE_KANJI, row)
                activity.activeSession = writing;
                activity.currentHintState = HintState.fromWritingLevel(2);
                activity.drawingPad = DrawingPadView(activity);
                requireNotNull(activity.drawingPad).setTarget("裂");
                addInk(requireNotNull(activity.drawingPad));
                activity.studyStatus = WritingStatusState();
                activity.writingResultStatus = WritingResultStatusHandle();
                prepareWritingActionViews(activity);
                activity.studyAnswerPanel = LinearLayout(activity);

                val order = StrokeOrderEvaluator
                        .evaluate(guide("裂"), sample())
                        .withDiagnosis(StrokeDiagnosis.builder().add(StrokeDiagnosis.Label.WRONG_DIRECTION, 1).build());
                activity.activeAnalysis = analysis(WritingAnalysis.Status.WRONG, false, order);
                assertTrue(WritingFeedbackCopy.canReplayAnalysis(
                        activity.activeAnalysis,
                        requireNotNull(activity.drawingPad).hasInk(),
                        guide("裂")
                ));
                requireNotNull(activity.writingResultStatus).show("Previous result", MainActivityBase.CORAL);
                activity.handleDrawingEdited();
                assertNull(activity.activeAnalysis);
                assertTrue(requireNotNull(activity.studyStatus).getText().toString().contains("Updated ink"));
                assertEquals(View.GONE, requireNotNull(activity.writingResultStatus).getVisibility());
                assertFalse(WritingFeedbackCopy.canReplayAnalysis(
                        analysis(WritingAnalysis.Status.RECOGNITION_ERROR, false, order),
                        requireNotNull(activity.drawingPad).hasInk(),
                        guide("裂")
                ));
                assertFalse(WritingFeedbackCopy.canReplayAnalysis(
                        activity.activeAnalysis,
                        requireNotNull(activity.drawingPad).hasInk(),
                        guide("裂")
                ));
                assertFalse(WritingFeedbackCopy.canReplayAnalysis(
                        analysis(WritingAnalysis.Status.WRONG, false, order),
                        requireNotNull(activity.drawingPad).hasInk(),
                        null
                ));
                assertFalse(WritingFeedbackCopy.canReplayAnalysis(
                        analysis(WritingAnalysis.Status.WRONG, false, order),
                        requireNotNull(activity.drawingPad).hasInk(),
                        StrokeGuide("裂", emptyList<InkStroke>())
                ));

                activity.activeAnalysis = analysis(WritingAnalysis.Status.CLOSE, true, order);
                requireNotNull(activity.writingResultStatus).show("Messy pass", MainActivityBase.TEAL);
                activity.startCleanerRetry();
                assertNull(activity.activeAnalysis);
                assertTrue(requireNotNull(activity.studyStatus).getText().toString().contains("Try cleaner"));
                assertEquals(View.GONE, requireNotNull(activity.writingResultStatus).getVisibility());

                activity.checkingWriting = true;
                activity.activeAnalysis = analysis(WritingAnalysis.Status.WRONG, false, order);
                requireNotNull(activity.writingResultStatus).show("Still checking", MainActivityBase.CORAL);
                activity.handleDrawingEdited();
                assertNotNull(activity.activeAnalysis);
                assertEquals(View.VISIBLE, requireNotNull(activity.writingResultStatus).getVisibility());

                activity.checkingWriting = false;
                activity.activeSession = writing;
                activity.currentHintState = HintState.fromWritingLevel(1);
                activity.activeAnalysis = analysis(WritingAnalysis.Status.WRONG, false, order);
                addInk(requireNotNull(activity.drawingPad));
                requireNotNull(activity.drawingPad).captureReplaySnapshot();
                activity.replayWritingAnalysis();
                assertTrue(requireNotNull(activity.drawingPad).isReplayOverlayVisible());
            }
        }
    }

    @Test
fun writingResultActionsUseOutcomeSpecificLabels() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var row = row("裂", "split", "レツ", emptyList<RecordsImportModels.Example>())
                prepareWritingUi(activity, session("裂", BridgeScheduler.TASK_WRITE_KANJI, row));
                activity.writingModelStatusKnown = true;
                activity.writingModelDownloaded = true;
                val order = StrokeOrderEvaluator
                        .evaluate(guide("裂"), sample())
                        .withDiagnosis(StrokeDiagnosis.builder().add(StrokeDiagnosis.Label.WRONG_ORDER, 1).build());

                activity.activeAnalysis = analysis(WritingAnalysis.Status.WRONG, false, order);
                activity.updateResultActions();
                var primary = requireNotNull(activity.writingPrimaryActionsView).currentModel()
                var fallback = requireNotNull(activity.writingFallbackActionsView).currentModel()
                assertEquals("Fail", primary.nextText);
                assertTrue(primary.nextVisible);
                assertTrue(fallback.manualOverrideVisible);

                activity.activeAnalysis = analysis(WritingAnalysis.Status.CLOSE, true, order);
                activity.updateResultActions();
                primary = requireNotNull(activity.writingPrimaryActionsView).currentModel();
                fallback = requireNotNull(activity.writingFallbackActionsView).currentModel();
                assertEquals("Try cleaner", primary.checkText);
                assertEquals("Save hard", primary.nextText);
                assertTrue(fallback.manualOverrideVisible);
                primary.onCheck.run();
                assertNull(activity.activeAnalysis);
                assertTrue(requireNotNull(activity.studyStatus).getText().toString().contains("Try cleaner"));

                activity.activeAnalysis = analysis(WritingAnalysis.Status.PASS, true, order);
                activity.updateResultActions();
                primary = requireNotNull(activity.writingPrimaryActionsView).currentModel();
                fallback = requireNotNull(activity.writingFallbackActionsView).currentModel();
                assertEquals("Pass", primary.nextText);
                assertFalse(fallback.manualOverrideVisible);
                primary.onNext.run();
                assertEquals(1, activity.store.reviewStatsSince(0L).good);
            }
        }
    }

    @Test
fun squarePadFrameKeepsDrawingPadSquareUnderRectangularConstraints() {
        var frame = MainActivityUiSupport.SquarePadFrame(context, 390)
        var pad = DrawingPadView(context)
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
fun homeActionGridUsesTwoColumnsWithWrappingHeights() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var homeGrid = homeActionRowTestView(activity)
                measureAtWidth(homeGrid, 320);
                assertTwoColumnGrid(homeGrid, 3);
                assertTrue(containsText(homeGrid, "Recent mistakes"));
                assertTrue(containsText(homeGrid, "Settings"));
            }
        }
    }

    @Test
fun browseAndDetailCopyAvoidMisleadingOrBlankRows() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals("No matches", HomeTextCopy.browseResultHeading(0));
                assertEquals("2 kanji", HomeTextCopy.browseResultHeading(2));
                assertEquals("Showing first 300 matches", HomeTextCopy.browseResultHeading(300));

                val row = RecordsImportModels.DashboardRow(
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
                        emptyList<RecordsImportModels.Example>()
                );
                var browseDetail = MainActivityHomeBrowseDetail(activity)
                var reason = browseDetail.detailReasonPanelModel(row, null)
                assertTrue(reason.lines.contains("Active practice evidence."));
                assertTrue(reason.lines.get(1).contains("Anki search: deck:Japanese"));

                var identity = browseDetail.detailIdentityModel(row, null, false)
                assertEquals("split", identity.title);
                assertEquals("", identity.reading);
            }
        }
    }

    @Test
fun writingRecognizerStatusCallbacksUpdateTheVisibleState() {
        val recognizer = FakeWritingRecognizer(
            CompletableFuture.completedFuture(WritingRecognizer.ModelStatus("ja", "ja-JP", false, "missing")),
            CompletableFuture.completedFuture(WritingRecognizer.ModelStatus("ja", "ja-JP", true, "ready")),
            CompletableFuture.completedFuture(
                WritingRecognizer.RecognitionResult(
                    listOf(WritingRecognizer.Candidate("裂", 0.9f))
                )
            )
        )
        MainActivityRuntimeOverrides.setWritingRecognizer(recognizer)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val row = row("裂", "split", "レツ", emptyList<RecordsImportModels.Example>())
                    activity.studyStatus = WritingStatusState()
                    prepareWritingActionViews(activity)
                    MainActivityStudyWritingStatus(activity).refreshWritingModelStatus()
                    activity.activeSession = session("裂", BridgeScheduler.TASK_WRITE_KANJI, row)
                    activity.currentHintState = HintState.fromWritingLevel(2)
                    MainActivityStudyWritingStatus(activity).refreshWritingModelStatus()
                }
                scenario.onActivity { activity ->
                    assertTrue(activity.writingModelStatusKnown)
                    assertFalse(activity.writingModelDownloaded)
                    assertTrue(
                        requireNotNull(activity.studyStatus).getText().toString().contains("Download the handwriting checker")
                    )
                    val primary = requireNotNull(activity.writingPrimaryActionsView).currentModel()
                    assertTrue(primary.downloadVisible)
                    primary.onDownload.run()
                }
                scenario.onActivity { activity ->
                    assertTrue(activity.writingModelDownloaded)
                    assertTrue(
                        requireNotNull(activity.studyStatus).getText().toString().contains("Handwriting checker ready")
                    )

                    val order = StrokeOrderEvaluator
                        .evaluate(guide("裂"), sample())
                        .withDiagnosis(
                            StrokeDiagnosis.builder()
                                .add(StrokeDiagnosis.Label.WRONG_ORDER, 1)
                                .build()
                        )
                    activity.activeAnalysis = analysis(WritingAnalysis.Status.WRONG, false, order)
                    requireNotNull(activity.studyStatus).setText("Existing analysis message")
                    MainActivityStudyWritingStatus(activity).refreshWritingModelStatus()
                }
                scenario.onActivity { activity ->
                    assertEquals("Existing analysis message", requireNotNull(activity.studyStatus).getText().toString())
                    activity.activeAnalysis = null
                    MainActivityStudyWritingStatus(activity).downloadWritingModel()
                }
                scenario.onActivity { activity ->
                    assertTrue(activity.writingModelDownloaded)
                    assertTrue(
                        requireNotNull(activity.studyStatus).getText().toString().contains("Handwriting checker ready")
                    )

                    activity.activeAnalysis = null
                    activity.recognizeWriting(
                        recognizer,
                        capturedWriting(),
                        sample(),
                        guide("裂"),
                        "裂",
                        requireNotNull(activity.activeSession).token
                    )
                }
                scenario.onActivity { activity ->
                    assertNotNull(activity.activeAnalysis)
                    assertTrue(
                        requireNotNull(activity.activeAnalysis).status == WritingAnalysis.Status.PASS ||
                            requireNotNull(activity.activeAnalysis).status == WritingAnalysis.Status.CLOSE ||
                            requireNotNull(activity.activeAnalysis).status == WritingAnalysis.Status.WRONG
                    )
                }
            }
        } finally {
            MainActivityRuntimeOverrides.setWritingRecognizer(null)
        }
    }

    @Test
fun studyEntryPointsAndWritingGuardsCoverEmptyAndUnavailableStates() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.renderStudy();
                assertHasText(activity, "Nothing to study yet");
                activity.renderStudyForKanji("missing");
                assertHasText(activity, "Kanji not available");
                assertFalse(activity.startStudyMoreNewCards(3));

                activity.checkWriting();

                var row = row("裂", "split", "レツ", emptyList<RecordsImportModels.Example>())
                activity.activeSession = session("裂", BridgeScheduler.TASK_WRITE_KANJI, row);
                activity.currentHintState = HintState.fromWritingLevel(1);
                activity.studyStatus = WritingStatusState();
                activity.writingResultStatus = WritingResultStatusHandle();
                activity.drawingPad = DrawingPadView(activity);
                activity.checkWriting();
                assertEquals(WritingAnalysis.Status.NO_INK, requireNotNull(activity.activeAnalysis).status);
                activity.activeAnalysis = null;
                assertTrue(activity.showNoInkWhenNeeded());
                assertEquals(WritingAnalysis.Status.NO_INK, requireNotNull(activity.activeAnalysis).status);

                activity.showModelUnavailable("The handwriting checker is unavailable on this device.");
                assertEquals(WritingAnalysis.Status.MODEL_UNAVAILABLE, requireNotNull(activity.activeAnalysis).status);
                assertTrue(requireNotNull(activity.writingResultStatus).getText().toString().contains("unavailable"));
                assertEquals(View.VISIBLE, requireNotNull(activity.writingResultStatus).getVisibility());

                activity.replayWritingAnalysis();
                activity.activeSession = null;
                activity.replayWritingAnalysis();
            }
        }
    }

private fun analysis(status: WritingAnalysis.Status, passed: Boolean, order: StrokeOrderEvaluator.StrokeOrderResult): WritingAnalysis {
        return WritingAnalysis(status, if (passed) "good" else "again", passed, status.name, emptyList<RecognitionCandidate>(), order)
    }

private fun session(kanji: String, taskType: String, row: RecordsImportModels.DashboardRow): RecordsSchedulerModels.StudySession {
        return sessionWithToken(kanji, taskType, row, "tok");
    }

private fun sessionWithToken(kanji: String, taskType: String, row: RecordsImportModels.DashboardRow, token: String): RecordsSchedulerModels.StudySession {
        val item = RecordsStudyModels.StudyItem(
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
        val prompt = row.primaryMeaning
        return RecordsSchedulerModels.StudySession(item, row, token, taskType, BridgeScheduler.TASK_WRITE_KANJI.equals(taskType), prompt);
    }

private fun row(kanji: String, meaning: String, reading: String, examples: List<RecordsImportModels.Example>): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(kanji, 1000, meaning, reading, kanji, 10, "reason", "reason text", 1, 0, 0, examples);
    }

private fun rowWithReason(kanji: String, meaning: String, reading: String, reason: String, examples: List<RecordsImportModels.Example>): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(kanji, 1000, meaning, reading, kanji, 10, "reason", reason, 1, 0, 0, examples);
    }

private fun example(expression: String, reading: String, meaning: String, sourceType: String): RecordsImportModels.Example {
        return RecordsImportModels.Example(sourceType, 1L, 2L, expression, reading, meaning, "", false, 0);
    }

private fun guide(kanji: String): StrokeGuide {
        return StrokeGuide(
                kanji,
                listOf(
                        InkStroke(listOf(InkPoint(0f, 0f, 0L), InkPoint(1f, 0f, 1L))),
                        InkStroke(listOf(InkPoint(0f, 1f, 2L), InkPoint(1f, 1f, 3L)))
                )
        );
    }

private fun sample(): WritingSample {
        return WritingSample(
                listOf(InkStroke(listOf(InkPoint(0f, 0f, 0L), InkPoint(1f, 0f, 1L)))),
                1f,
                1f
        );
    }

private fun capturedWriting(): CapturedWriting {
        return CapturedWriting(listOf(CapturedStroke(listOf(
                CapturedStroke.Point(0f, 0f, 0L),
                CapturedStroke.Point(1f, 0f, 1L)
        ))));
    }

private fun <T> failedFuture(error: Throwable): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        future.completeExceptionally(error)
        return future
    }

private fun settings(active: Boolean, suspended: Boolean, tagged: Boolean, tags: List<String>, weak: Boolean, browser: Boolean, query: String): RecordsSyncModels.Settings {
        var defaults = RecordsSyncModels.Settings.kikuDefaults()
        return RecordsSyncModels.Settings(
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

private fun studyItem(kanji: String, rung: RecordsBase.LadderRung, state: String, dueAtMillis: Long): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(
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

private fun event(expression: String, reading: String): RecordsImportModels.KanjiTimelineEvent {
        return RecordsImportModels.KanjiTimelineEvent(
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

private fun syncResult(
        success: Boolean,
        skipped: Boolean,
        dashboardRows: Int,
        importedSuspendedKanji: Int,
        message: String,
        adaptiveSummary: String,
        studyReadyCount: Int = dashboardRows,
        adaptiveFocusText: String = adaptiveSummary.ifEmpty { "Adaptive focus is waiting for sync" },
): ManualSyncEngine.SyncResult {
        try {
            val constructor = ManualSyncEngine.SyncResult::class.java.getDeclaredConstructor(
                    Boolean::class.javaPrimitiveType!!,
                    Boolean::class.javaPrimitiveType!!,
                    Int::class.javaPrimitiveType!!,
                    Int::class.javaPrimitiveType!!,
                    String::class.java,
                    String::class.java
            )
            constructor.setAccessible(true)
            return constructor.newInstance(success, skipped, dashboardRows, importedSuspendedKanji, message, adaptiveSummary).apply {
                this.studyReadyCount = studyReadyCount
                this.adaptiveFocusText = adaptiveFocusText
            }
        } catch (error: ReflectiveOperationException) {
            throw AssertionError(error)
        }
    }

private fun checked(context: Context, checked: Boolean): CheckBox {
        var box = CheckBox(context)
        box.setChecked(checked);
        return box;
    }

private fun seedRows(activity: MainActivity, rows: List<RecordsImportModels.DashboardRow>) {
        val notes = ArrayList<RecordsSyncModels.Note>()
        val cards = ArrayList<RecordsSyncModels.Card>()
        var id = 1L
        for (row in rows) {
            notes.add(note(id, row.kanji + "語", row.reading, row.primaryMeaning, row.kanji + "を見た。"));
            cards.add(RecordsSyncModels.Card(100L + id, id, 0, "Kiku", 2, 2, 0, 1, 0, 0, false));
            id++;
        }
        activity.store.saveSuccessfulSync(
                RecordsSyncModels.CollectionSnapshot(notes, cards),
                emptyList<RecordsImportModels.SuspendedImport>(),
                rows,
                RecordsSyncModels.Settings.kikuDefaults(),
                1000L,
                2000L,
                null
        );
    }

private fun similarIndex(tsv: String): SimilarKanjiIndex {
        try {
            return SimilarKanjiIndex.parseTsv(StringReader(tsv))
        } catch (error: Exception) {
            throw AssertionError(error)
        }
    }

private fun note(id: Long, expression: String, reading: String, meaning: String, sentence: String): RecordsSyncModels.Note {
        val fields = java.util.LinkedHashMap<String, String>()
        fields.put("Expression", expression);
        fields.put("ExpressionReading", reading);
        fields.put("MainDefinition", meaning);
        fields.put("Sentence", sentence);
        fields.put("Frequency", "1000");
        fields.put("FreqSort", "1000");
        return RecordsSyncModels.Note(id, 1001L, "Kiku", fields, emptyList<String>());
    }

private fun addInk(pad: DrawingPadView) {
        pad.onTouchEvent(MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 10f, 10f, 0));
        pad.onTouchEvent(MotionEvent.obtain(0L, 20L, MotionEvent.ACTION_MOVE, 40f, 40f, 0));
        pad.onTouchEvent(MotionEvent.obtain(0L, 40L, MotionEvent.ACTION_UP, 80f, 80f, 0));
    }

private fun layoutPad(pad: DrawingPadView) {
        pad.measure(
                View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY)
        );
        pad.layout(0, 0, 320, 320);
    }

private fun measureAtWidth(view: View, width: Int) {
        view.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1000, View.MeasureSpec.AT_MOST)
        );
        view.layout(0, 0, width, view.getMeasuredHeight());
    }

private fun assertTwoColumnGrid(view: View, expectedRows: Int) {
        assertTrue(view is ViewGroup);
        var grid = view as ViewGroup
        assertEquals(expectedRows, grid.getChildCount());
        for (i in 0 until grid.getChildCount()) {
            assertTrue(grid.getChildAt(i) is ViewGroup);
            var row = grid.getChildAt(i) as ViewGroup
            assertEquals(2, row.getChildCount());
            assertTrue(row.getMeasuredHeight() > 0);
        }
    }

private fun prepareWritingUi(activity: MainActivity, session: RecordsSchedulerModels.StudySession) {
        activity.activeSession = session;
        activity.currentHintState = HintState.fromWritingLevel(1);
        activity.studyStatus = WritingStatusState();
        activity.writingResultStatus = WritingResultStatusHandle();
        prepareWritingActionViews(activity);
        activity.studyAnswerPanel = LinearLayout(activity);
        activity.drawingPad = DrawingPadView(activity);
        requireNotNull(activity.drawingPad).setTarget(requireNotNull(session.item).kanji);
        activity.activeAnalysis = null;
        activity.checkingWriting = false;
        activity.writingModelStatusKnown = false;
        activity.writingModelDownloaded = false;
    }

private fun prepareWritingActionViews(activity: MainActivity) {
        activity.writingToolActionsView = WritingToolActionsView(activity);
        activity.writingPrimaryActionsView = WritingPrimaryActionsView(activity);
        activity.writingFallbackActionsView = WritingFallbackActionsView(activity);
    }

private fun assertHasText(activity: MainActivity, text: String) {
                val root = activity.findViewById<ViewGroup>(android.R.id.content)
        if (!containsText(root, text) && findDeviceTextNow(text) == null) {
            throw AssertionError("Missing text: " + text);
        }
    }

private fun waitForText(activity: MainActivity, text: String, timeoutMillis: Long = 5000L) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (System.currentTimeMillis() < deadline) {
            val root = activity.findViewById<ViewGroup>(android.R.id.content)
            if (containsText(root, text) || findDeviceTextNow(text) != null) {
                return
            }
            Thread.sleep(100L)
        }
        assertHasText(activity, text)
    }

private fun assertContainsText(root: View, text: String) {
        if (!containsTextContaining(root, text)) {
            throw AssertionError("Missing text containing: " + text);
        }
    }

private fun containsText(view: View, expected: String): Boolean {
        if (view is TextView && expected.contentEquals(view.getText())) {
            return true;
        }
        if (view is ComposeView && containsAccessibilityText(view.createAccessibilityNodeInfo(), expected)) {
            return true;
        }
        if (view !is ViewGroup) {
            return false;
        }
        for (i in 0 until view.getChildCount()) {
            if (containsText(view.getChildAt(i), expected)) {
                return true;
            }
        }
        return false;
    }

private fun containsTextContaining(view: View, expected: String): Boolean {
        if (view is TextView && view.getText().toString().contains(expected)) {
            return true;
        }
        if (view is ComposeView && containsAccessibilityTextContaining(view.createAccessibilityNodeInfo(), expected)) {
            return true;
        }
        if (view !is ViewGroup) {
            return false;
        }
        for (i in 0 until view.getChildCount()) {
            if (containsTextContaining(view.getChildAt(i), expected)) {
                return true;
            }
        }
        return false;
    }

private fun performClickableWithText(root: View, label: String) {
        val clickable = findClickableWithText(root, label)
        if (clickable == null) {
            val deviceClickable = findDeviceClickableTextNow(label)
            if (deviceClickable == null) {
                throw AssertionError("Missing clickable text: " + label);
            }
            deviceClickable.click();
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle(2000L);
            return;
        }
        clickable.performClick();
    }

private fun findClickableWithText(view: View, label: String): View? {
        if (view.isClickable() && containsText(view, label)) {
            return view;
        }
        if (view !is ViewGroup) {
            return null;
        }
        for (i in 0 until view.getChildCount()) {
            val found = findClickableWithText(view.getChildAt(i), label)
            if (found != null) {
                return found;
            }
        }
        return null;
    }

private fun findDeviceTextNow(label: String): UiObject2? {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val pkg = appPackage()
        var candidate = firstMatch(device.findObjects(By.pkg(pkg).text(label)))
        if (candidate == null) {
            candidate = firstMatch(device.findObjects(By.pkg(pkg).textContains(label)));
        }
        if (candidate == null) {
            candidate = firstMatch(device.findObjects(By.pkg(pkg).text(label.uppercase(Locale.ROOT))));
        }
        if (candidate == null) {
            candidate = firstMatch(device.findObjects(By.pkg(pkg).textContains(label.uppercase(Locale.ROOT))));
        }
        return candidate;
    }

private fun findDeviceClickableTextNow(label: String): UiObject2? {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val pkg = appPackage()
        var candidate = firstMatch(device.findObjects(By.pkg(pkg).clickable(true).text(label)))
        if (candidate == null) {
            candidate = firstMatch(device.findObjects(By.pkg(pkg).clickable(true).textContains(label)));
        }
        if (candidate == null) {
            candidate = firstMatch(device.findObjects(By.pkg(pkg).clickable(true).text(label.uppercase(Locale.ROOT))));
        }
        if (candidate == null) {
            candidate = firstMatch(device.findObjects(By.pkg(pkg).clickable(true).textContains(label.uppercase(Locale.ROOT))));
        }
        if (candidate == null) {
            candidate = firstMatch(device.findObjects(By.pkg(pkg).clickable(true).desc(label)));
        }
        if (candidate == null) {
            candidate = firstMatch(device.findObjects(By.pkg(pkg).clickable(true).descContains(label)));
        }
        if (candidate != null && !candidate.isClickable()) {
            var parent = candidate.getParent()
            while (parent != null && parent != candidate && !parent.isClickable()) {
                val nextParent = parent.getParent()
                candidate = parent;
                parent = nextParent
            }
            if (parent != null && parent.isClickable()) {
                candidate = parent;
            }
        }
        return if (candidate != null && candidate.isClickable()) candidate else null;
    }

private fun appPackage(): String {
        return InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName();
    }

private fun firstMatch(objects: List<UiObject2>): UiObject2? {
        return objects.firstOrNull();
    }

private fun containsAccessibilityText(node: AccessibilityNodeInfo?, expected: String): Boolean {
        if (node == null) {
            return false;
        }
        val value = node.getText()
        if (value != null && expected.contentEquals(value)) {
            return true;
        }
        val description = node.getContentDescription()
        if (description != null && expected.contentEquals(description)) {
            return true;
        }
        val childCount = node.getChildCount()
        for (i in 0 until childCount) {
            val child = node.getChild(i)
            if (child == null) {
                continue;
            }
            if (containsAccessibilityText(child, expected)) {
                return true;
            }
        }
        return false;
    }

private fun containsAccessibilityTextContaining(node: AccessibilityNodeInfo?, expected: String): Boolean {
        if (node == null) {
            return false;
        }
        val value = node.getText()
        if (value != null && value.toString().contains(expected)) {
            return true;
        }
        val description = node.getContentDescription()
        if (description != null && description.toString().contains(expected)) {
            return true;
        }
        val childCount = node.getChildCount()
        for (i in 0 until childCount) {
            val child = node.getChild(i)
            if (child == null) {
                continue;
            }
            if (containsAccessibilityTextContaining(child, expected)) {
                return true;
            }
        }
        return false;
    }

private fun motion(action: Int, x: Float, y: Float): MotionEvent {
        return MotionEvent.obtain(0L, 0L, action, x, y, 0);
    }

private fun deleteRecursively(file: File) {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
    private class FakeWritingRecognizer(
        private val status: CompletableFuture<WritingRecognizer.ModelStatus>,
        private val download: CompletableFuture<WritingRecognizer.ModelStatus>,
        private val recognition: CompletableFuture<WritingRecognizer.RecognitionResult>
    ) : WritingRecognizer {
        override fun modelStatus(): CompletableFuture<WritingRecognizer.ModelStatus> = status

        override fun downloadModel(): CompletableFuture<WritingRecognizer.ModelStatus> = download

        override fun recognize(writing: CapturedWriting?): CompletableFuture<WritingRecognizer.RecognitionResult> = recognition

        override fun close() {
            // Fake recognizer has no native resources to release.
        }
    }
}
