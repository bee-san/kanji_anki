package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabaseLockedException;
import android.graphics.Point;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.anki.CollectionGateway;
import dev.bee.kanjianki.anki.FakeAnkiDroidProvider;
import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.FrequencyRetentionRanges;
import dev.bee.kanjianki.core.SettingsInputRules;
import dev.bee.kanjianki.core.SettingsTextCopy;
import dev.bee.kanjianki.core.SimilarKanjiIndex;
import dev.bee.kanjianki.core.study.InkPoint;
import dev.bee.kanjianki.core.study.InkStroke;
import dev.bee.kanjianki.core.study.StrokeGuide;
import dev.bee.kanjianki.core.study.StrokeGuideParser;
import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.data.LocalStoreBase;
import dev.bee.kanjianki.data.StudyStatsStore;
import dev.bee.kanjianki.study.CapturedWriting;
import dev.bee.kanjianki.study.WritingRecognizer;
import dev.bee.kanjianki.sync.SyncProgress;
import dev.bee.kanjianki.sync.SyncSettings;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.rules.TestName;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import dev.bee.kanjianki.TestDates.localDayStart;
import dev.bee.kanjianki.TestDates.moveLocalDays;
import dev.bee.kanjianki.TestRecords.kikuCard;
import dev.bee.kanjianki.TestRecords.kikuNote;
import dev.bee.kanjianki.TestRecords.review;
import org.junit.Assert.assertEquals;
import org.junit.Assert.assertFalse;
import org.junit.Assert.assertNotNull;
import org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4::class)
class MainActivityInstrumentedTest {
    companion object {
    var LIVE_ARG = "kanjiLiveAnkiDroid"
    var LIVE_FOREGROUND_SYNC_TEST = "testManualSyncButtonWorksAgainstLiveAnkiDroid"
    var STUDY_NOW = "Study now"
    var REVEAL = "Reveal"
    var CHECK = "Check"
    var RAMEN_RADICAL_GAP = "ramen radical gap"
    var IMPORTED_FROM_SUSPENDED_CARDS = "Imported from suspended cards"
    var MISSED_IN_MATURE_CARDS = "Missed in mature cards"
    var CLEAN_MATCH = "Clean match"
    var PASS_AFTER_WRITING = "Pass"
    var RECOGNITION_QUESTION = "What does this kanji mean?"
    var RECOGNISE = "Recognise"
    var SIMILAR_KANJI = "Similar kanji"

    @get:Rule
    val testName = TestName()

    private lateinit var context: Context

    @BeforeClass
@JvmStatic fun grantSuiteNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) {
            return;
        }
        var target = InstrumentationRegistry.getInstrumentation().getTargetContext()
        try {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .grantRuntimePermission(target.getPackageName(), Manifest.permission.POST_NOTIFICATIONS);
        } catch (ignored: SecurityException) {
            // Some devices do not allow the shell identity to grant this permission.
        }
    }
}

    @Before
fun setUp() {
      context = InstrumentationRegistry.getInstrumentation().getTargetContext()
        if (liveAnkiDroidEnabled() && !LIVE_FOREGROUND_SYNC_TEST.equals(testName.getMethodName())) {
            Assume.assumeTrue("Live AnkiDroid runs only the foreground sync button path from MainActivity.", false);
        }
        context.deleteDatabase("kanji_anki_simple.db");
        MainActivityRuntimeOverrides.setAnkiDroidGateway(AnkiDroidGateway.testProvider(context, "dev.bee.kanjianki.no_anki_for_tests"));
        MainActivityRuntimeOverrides.setCollectionGateway(null);
        MainActivityRuntimeOverrides.setWritingRecognizer(null);
        MainActivityRuntimeOverrides.setInstallPermission(null);
    }

    @After
fun tearDown() {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(null);
        MainActivityRuntimeOverrides.setCollectionGateway(null);
        MainActivityRuntimeOverrides.setWritingRecognizer(null);
        MainActivityRuntimeOverrides.setInstallPermission(null);
        context.deleteDatabase("kanji_anki_simple.db");
        deleteRecursively(File(context.getCacheDir(), "updates"));
    }

    @Test
fun testLaunchesHomeWithoutSeedDataOrProviderCrash() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                var content = activity.findViewById<View>(android.R.id.content)
                assertNotNull(content);
                assertTrue(content.getWidth() >= 0);
                assertTrue(content.getHeight() >= 0);
                assertHasText(activity, "Kani");
                assertHasText(activity, "Sync AnkiDroid");
                assertHasText(activity, "Streak");
                assertHasText(activity, "No streak yet");
                assertHasText(activity, "Settings");
                assertNoText(activity, "Queue");
                assertNoText(activity, "Update");
            }
        }
    }

    @Test
fun testHomeShowsCurrentStudyStreak() {
        var today = localDayStart(System.currentTimeMillis())
        var yesterday = moveLocalDays(today, -1)
        LocalStore(context).use { store ->
            store.saveReview(review("拉", "streak-yesterday"), "good", yesterday + 60_000L);
            store.saveReview(review("提", "streak-today-a"), "good", today + 60_000L);
            store.saveReview(review("謎", "streak-today-b"), "easy", today + 120_000L);
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertHasText(activity, "Streak");
                assertHasText(activity, "2-day streak");
                assertHasText(activity, "Best: 2 days");
            }
        }
    }

    @Test
fun testBrowseKanjiShowsDetailAndSuspensionControls() {
        seedDashboardRowsOnly(Collections.singletonList(dashboardRow("拉", RAMEN_RADICAL_GAP, "らーめん", "Needs writing practice")));

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertHasText(activity, "Browse Kanji");
            }
            clickText(scenario, "Browse Kanji");
            scenario.onActivity { activity ->
                assertHasText(activity, RAMEN_RADICAL_GAP);
                activity.renderBrowseKanji("拉");
                assertEquals("拉", activity.activeBrowseQuery);
                assertHasText(activity, "拉");
            }
            clickText(scenario, RAMEN_RADICAL_GAP);
            scenario.onActivity { activity ->
                assertHasText(activity, "Back to Browse Kanji");
                assertHasText(activity, "Local inventory");
                assertHasText(activity, "Review this now");
                assertHasText(activity, "Suspend locally");
            }
            clickText(scenario, "Suspend locally");
            scenario.onActivity { activity ->
                assertHasText(activity, "SUSPENDED");
                assertHasText(activity, "Unsuspend locally");
                assertNoText(activity, "Review this now");
            }
            clickText(scenario, "Unsuspend locally");
            scenario.onActivity { activity ->
                assertHasText(activity, "Review this now");
                assertHasText(activity, "Suspend locally");
            }
            clickText(scenario, "Back to Browse Kanji");
            scenario.onActivity { activity ->
                assertEquals("拉", activity.activeBrowseQuery);
                assertHasText(activity, "拉");
            }
        }
    }

    @Test
fun testNavigationSettingsAndEmptyStates() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, "Stats");
            scenario.onActivity { activity ->
                assertHasTexts(activity, "Stats", "Kani is not currently working for you", "Weakness Burn-Down", "Anki Support Conversion", "Ladder Health");
            }

            clickText(scenario, "Home");
            clickText(scenario, "Settings");
            scenario.onActivity { activity -> assertCollapsedSettingsScreen(activity) }
            clickText(scenario, "Reminders & updates");
            waitForText(scenario, "App updates");
            clickText(scenario, "Open updater");
            waitForText(scenario, "GitHub updater");
            scenario.onActivity { activity -> assertHasTexts(activity, "GitHub updater", "Back to settings", "Home", "Current version", "Check for update") }
        }
    }

    @Test
fun testSettingsControlsPersistFiltersAndLearning() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, "Settings");
            setFrequencyRangeInputs("250", "3500");
            clickText(scenario, "Save frequency range");
            clickText(scenario, "Save import filters");
            clickText(scenario, "Deck options");
            verifyStudyBehaviorPanel(scenario);
            clickText(scenario, SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK));
            waitForText(scenario, SettingsTextCopy.newCardSortStatusText(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK));
            clickText(scenario, SettingsTextCopy.saveNewCardSortLabel());
            verifyLearningStepValidationAndPresets(scenario);
        }
    }

    @Test
fun testSettingsControlsPersistStudyAheadLadderAndWorkload() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, "Settings");
            setStudyAheadMinutes("later");
            clickText(scenario, "Save study ahead");
            assertStudyAheadMinutes(scenario, SettingsInputRules.DEFAULT_STUDY_AHEAD_MINUTES);
            setStudyAheadMinutes("2000");
            clickText(scenario, "Save study ahead");
            assertStudyAheadMinutes(scenario, SettingsInputRules.DEFAULT_STUDY_AHEAD_MINUTES);
            setStudyAheadMinutes("45");
            clickText(scenario, "Save study ahead");
            verifyLadderThresholdValidationAndDefaults(scenario);
            setLadderThresholdText();
            clickText(scenario, "Save movement rules");
            configureManualWorkload(scenario);
            verifyWorkloadAutoActions(scenario);
        }
    }

    @Test
fun testSettingsControlsPersistRetentionReminderAndStoredValues() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, "Settings");
            clickText(scenario, "Deck options");
            clickText(scenario, "95%");
            verifyRetentionValidationAndRanges(scenario);
            clickText(scenario, "Save retention");
            enableMorningReminder(scenario);
        }
    }

    @Test
fun testSettingsControlsPersistStoredNavigationValuesAcrossPanels() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, "Settings");
            setFrequencyRangeInputs("250", "3500");
            clickText(scenario, "Save frequency range");
            clickText(scenario, "Save import filters");
            clickText(scenario, "Deck options");
            clickText(scenario, SettingsTextCopy.newCardSortLabel(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK));
            waitForText(scenario, SettingsTextCopy.newCardSortStatusText(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK));
            clickText(scenario, SettingsTextCopy.saveNewCardSortLabel());
            setLearningStepText();
            clickText(scenario, "Save learning steps");
            setStudyAheadMinutes();
            clickText(scenario, "Save study ahead");
            setLadderThresholdText();
            clickText(scenario, "Save movement rules");
            setNavigationWorkloadControls(scenario);
            setNavigationRetentionAndReminder(scenario);
            assertNavigationSettingsPersisted();
        }
    }

private fun setNavigationWorkloadControls(scenario: ActivityScenario<MainActivity>) {
        clickText(scenario, SettingsTextCopy.manualWorkloadLabel());
        waitForText(scenario, SettingsTextCopy.workloadStatusText(
                AdaptiveLoadPlanner.DEFAULT_WORKLOAD_PERCENT,
                AdaptiveLoadPlanner.DEFAULT_MAX_ITEMS
        ));
        setComposeSliderToEnd(SettingsWorkloadControlDescriptions.WORKLOAD_PERCENT_SLIDER);
        waitForText(scenario, SettingsTextCopy.workloadStatusText(100, AdaptiveLoadPlanner.DEFAULT_MAX_ITEMS));
        clickText(scenario, "Save workload");
        clickText(scenario, SettingsTextCopy.automaticParetoLabel());
        waitForText(scenario, SettingsTextCopy.saveMaximumLabel());
        setComposeSliderToEnd(SettingsWorkloadControlDescriptions.MAX_ITEMS_SLIDER);
        waitForText(scenario, SettingsTextCopy.maxItemsStatusText(AdaptiveLoadPlanner.MAX_MAX_ITEMS));
        clickText(scenario, SettingsTextCopy.saveMaximumLabel());
        clickText(scenario, SettingsTextCopy.manualWorkloadLabel());
        waitForText(scenario, SettingsTextCopy.saveWorkloadLabel());
        clickText(scenario, SettingsTextCopy.saveWorkloadLabel());
    }

private fun setNavigationRetentionAndReminder(scenario: ActivityScenario<MainActivity>) {
        clickText(scenario, "95%");
        verifyRetentionValidationAndRanges(scenario);
        clickText(scenario, "Save retention");
        clickText(scenario, "Reminders & updates");
        clickText(scenario, "Morning 08:00");
        clickText(scenario, "Enable reminder");
        clickTextIfPresent("Allow");
        waitForText(scenario, "Daily around 08:00");
    }

fun setFrequencyRangeInputs(minRank: String, maxRank: String) {
        setComposeTextField(SettingsTextCopy.minRankLabel(), minRank);
        setComposeTextField(SettingsTextCopy.maxRankLabel(), maxRank);
    }

private fun verifyStudyBehaviorPanel(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { activity ->
            assertHasText(activity, "Daily workload");
            assertHasText(activity, SettingsTextCopy.autoWorkloadStatusText(null));
            assertHasText(activity, SettingsTextCopy.manualWorkloadLabel());
            assertHasText(activity, "Review retention");
            assertHasText(activity, "Desired retention: 90%");
            assertHasText(activity, "Ladder movement");
            assertHasText(activity, "Days to move up");
            assertHasText(activity, "Fails to move down");
        }
    }

private fun verifyRetentionValidationAndRanges(scenario: ActivityScenario<MainActivity>) {
        setRankRetentionEnabled(true);
        setRetentionRanges("not a range");
        clickText(scenario, SettingsTextCopy.saveRetentionLabel());
        assertFrequencyRetentionDisabled(scenario);

        clickText(scenario, SettingsTextCopy.useExampleRangesLabel());
        assertRetentionRanges(FrequencyRetentionRanges.exampleText());
        setRetentionRanges("1-500=95%\n501-20000=85%");
    }

fun setRankRetentionEnabled(enabled: Boolean) {
        setComposeCheckBox(SettingsRetentionControlDescriptions.RANK_RETENTION_CHECKBOX, enabled);
    }

fun setRetentionRanges(ranges: String) {
        setComposeTextField(SettingsRetentionControlDescriptions.RANK_RANGES_INPUT, ranges);
    }

fun assertRetentionRanges(ranges: String) {
        assertComposeTextFieldValue(SettingsRetentionControlDescriptions.RANK_RANGES_INPUT, ranges);
    }

private fun assertFrequencyRetentionDisabled(scenario: ActivityScenario<MainActivity>) {
        scenario.onActivity { activity -> assertFalse(activity.store.schedulerParameters().frequencyRetentionEnabled) }
    }

private fun verifyLearningStepValidationAndPresets(scenario: ActivityScenario<MainActivity>) {
        var defaults = RecordsSchedulerModels.LearningStepSettings.defaults()
        setLearningStepText("bad", "5m 20m");
        clickText(scenario, SettingsTextCopy.saveLearningStepsLabel());
        assertLearningStepSettings(scenario, defaults);

        setLearningStepText("2m 15m", "5m 20m");
        clickText(scenario, SettingsTextCopy.ankiDefaultLabel());
        assertLearningStepFields(scenario, defaults.newStepsText(), defaults.reviewStepsText());
        clickText(scenario, SettingsTextCopy.sameLearningStepsLabel());
        assertLearningStepFields(scenario, defaults.newStepsText(), defaults.newStepsText());
    }

fun setLearningStepText() {
        setLearningStepText("2m 15m", "5m 20m");
    }

fun setLearningStepText(newSteps: String, reviewSteps: String) {
        setComposeTextField(MainActivityBase.LABEL_NEW_CARDS, newSteps);
        setComposeTextField(SettingsTextCopy.reviewMissesLabel(), reviewSteps);
    }

private fun assertLearningStepFields(scenario: ActivityScenario<MainActivity>, newSteps: String, reviewSteps: String) {
        assertComposeTextFieldValue(MainActivityBase.LABEL_NEW_CARDS, newSteps);
        assertComposeTextFieldValue(SettingsTextCopy.reviewMissesLabel(), reviewSteps);
    }

private fun assertLearningStepSettings(scenario: ActivityScenario<MainActivity>, expected: RecordsSchedulerModels.LearningStepSettings) {
        scenario.onActivity { activity ->
            var actual = activity.store.learningStepSettings()
            assertEquals(expected.newStepsMinutes, actual.newStepsMinutes);
            assertEquals(expected.reviewStepsMinutes, actual.reviewStepsMinutes);
        }
    }

fun setStudyAheadMinutes() {
        setStudyAheadMinutes("45");
    }

fun setStudyAheadMinutes(minutes: String) {
        setComposeTextField(SettingsTextCopy.studyAheadMinutesLabel(), minutes);
    }

private fun assertStudyAheadMinutes(scenario: ActivityScenario<MainActivity>, expected: Int) {
        scenario.onActivity { activity -> assertEquals(expected, activity.store.studyAheadMinutes()) }
    }

private fun verifyLadderThresholdValidationAndDefaults(scenario: ActivityScenario<MainActivity>) {
        setLadderThresholdText("later", "0");
        clickText(scenario, SettingsTextCopy.saveLadderThresholdsLabel());
        assertLadderThresholdSettings(
                scenario,
                RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS,
                RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK
        );

        setLadderThresholdText("99", "7");
        clickText(scenario, SettingsTextCopy.useDefaultLadderThresholdsLabel());
        assertLadderThresholdFields(
                scenario,
                RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS,
                RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK
        );
    }

fun setLadderThresholdText() {
        setLadderThresholdText("30", "2");
    }

fun setLadderThresholdText(promotionDays: String, failStreak: String) {
        setComposeTextField(SettingsTextCopy.fsrsDaysToGoUpLabel(), promotionDays);
        setComposeTextField(SettingsTextCopy.failsToGoDownLabel(), failStreak);
    }

private fun assertLadderThresholdFields(scenario: ActivityScenario<MainActivity>, promotionDays: Int, failStreak: Int) {
        var device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assertNotNull(device.wait(Until.findObject(By.text(Integer.toString(promotionDays))), 3000L));
        assertNotNull(device.wait(Until.findObject(By.text(Integer.toString(failStreak))), 3000L));
    }

private fun assertLadderThresholdSettings(scenario: ActivityScenario<MainActivity>, promotionDays: Int, failStreak: Int) {
        scenario.onActivity { activity ->
            assertEquals(promotionDays, activity.store.getIntSetting(
                    SyncSettings.LADDER_PROMOTION_INTERVAL_DAYS_SETTING_KEY,
                    RecordsBase.DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS
            ));
            assertEquals(failStreak, activity.store.getIntSetting(
                    SyncSettings.LADDER_DEMOTION_FAIL_STREAK_SETTING_KEY,
                    RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK
            ));
        }
    }

private fun configureManualWorkload(scenario: ActivityScenario<MainActivity>) {
        clickText(scenario, SettingsTextCopy.manualWorkloadLabel());
        waitForText(scenario, SettingsTextCopy.workloadStatusText(
                AdaptiveLoadPlanner.DEFAULT_WORKLOAD_PERCENT,
                AdaptiveLoadPlanner.DEFAULT_MAX_ITEMS
        ));
        setComposeSliderToEnd(SettingsWorkloadControlDescriptions.WORKLOAD_PERCENT_SLIDER);
        waitForText(scenario, SettingsTextCopy.workloadStatusText(100, AdaptiveLoadPlanner.DEFAULT_MAX_ITEMS));
        clickText(scenario, "Save workload");
    }

private fun verifyWorkloadAutoActions(scenario: ActivityScenario<MainActivity>) {
        clickText(scenario, SettingsTextCopy.automaticParetoLabel());
        waitForText(scenario, SettingsTextCopy.saveMaximumLabel());
        scenario.onActivity { activity -> assertEquals(AdaptiveLoadPlanner.MODE_AUTO, activity.store.adaptiveLoadMode()) }
        setComposeSliderToEnd(SettingsWorkloadControlDescriptions.MAX_ITEMS_SLIDER);
        waitForText(scenario, SettingsTextCopy.maxItemsStatusText(AdaptiveLoadPlanner.MAX_MAX_ITEMS));
        clickText(scenario, SettingsTextCopy.saveMaximumLabel());
        scenario.onActivity { activity -> assertEquals(
                AdaptiveLoadPlanner.MAX_MAX_ITEMS,
                activity.store.adaptiveLoadMaxItems()
        ) }
        clickText(scenario, SettingsTextCopy.manualWorkloadLabel());
        waitForText(scenario, SettingsTextCopy.saveWorkloadLabel());
        scenario.onActivity { activity -> assertEquals(AdaptiveLoadPlanner.MODE_MANUAL, activity.store.adaptiveLoadMode()) }
    }

private fun enableMorningReminder(scenario: ActivityScenario<MainActivity>) {
        clickText(scenario, "Reminders & updates");
        scenario.onActivity { activity -> assertHasTexts(activity, "Daily reminder", "Daily sync") }
        clickText(scenario, "Morning 08:00");
        clickText(scenario, "Enable reminder");
        clickTextIfPresent("Allow");
        waitForText(scenario, "Daily around 08:00");
    }

    @Test
fun testConfiguredDailySyncSettingsScreenCanPauseAndResume() {
        var now = System.currentTimeMillis()
        LocalStore(context).use { store ->
            store.saveAutoSyncSettings(LocalStoreBase.AutoSyncSettings(
                    true,
                    true,
                    6,
                    30,
                    now - 3_600_000L,
                    now - 3_600_000L,
                    now + 86_400_000L
            ));
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, "Settings");
            clickText(scenario, "Reminders & updates");
            waitForText(scenario, "Daily sync");
            scenario.onActivity { activity ->
                assertHasText(activity, "On around 06:30");
                assertHasText(activity, "Last successful sync");
                assertHasText(activity, "Next sync");
                assertHasText(activity, "Turn off daily sync");
            }

            clickText(scenario, "Turn off daily sync");
            waitForText(scenario, "Off");
            assertDailySyncEnabled(false);

            clickText(scenario, "Turn on daily sync");
            waitForText(scenario, "On around 06:30");
            assertDailySyncEnabled(true);
        }
    }

    @Test
fun testReminderSettingsPanelCanEnableAndTurnOffReminder() {
        MainActivityRuntimeOverrides.setRuntimeNotificationPermission(true)
        MainActivityRuntimeOverrides.setNotificationsAllowed(true)
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        try {
            clickText(scenario, "Settings")
            clickText(scenario, "Reminders & updates")
            waitForText(scenario, "Daily reminder")
            clickText(scenario, "Morning 08:00")
            waitForText(scenario, "Reminder time: 08:00")
            clickText(scenario, "Enable reminder")
            waitForText(scenario, "Daily around 08:00")
            assertReminderSettings(true, 8, 0)

            clickText(scenario, "Turn off reminder")
            waitForText(scenario, "Off")
            assertReminderSettings(false, 8, 0)
        } finally {
            scenario.close()
            MainActivityRuntimeOverrides.setRuntimeNotificationPermission(null)
            MainActivityRuntimeOverrides.setNotificationsAllowed(null)
        }
    }

    @Test
fun testImportFilterValidationBlocksEmptySourcesAndEmptyBrowserQuery() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, "Settings");
            setImportFilterChecked(SettingsTextCopy.activeCardsLabel(), false);
            setImportFilterChecked(SettingsTextCopy.suspendedCardsLabel(), false);
            setImportFilterChecked(SettingsTextCopy.taggedCardsLabel(), false);
            setImportFilterChecked(SettingsTextCopy.weakCardsLabel(), false);
            setImportFilterChecked(SettingsTextCopy.browserQueryLabel(), false);
            setComposeTextField(SettingsTextCopy.ankiNoteTagsLabel(), "");
            setComposeTextField(SettingsTextCopy.ankiBrowserQueryLabel(), "");
            clickText(scenario, "Save import filters");
            assertDefaultImportSettingsStillStored();

            setImportFilterChecked(SettingsTextCopy.browserQueryLabel(), true);
            clickText(scenario, "Save import filters");
            assertDefaultImportSettingsStillStored();

            setComposeTextField(SettingsTextCopy.ankiBrowserQueryLabel(), "deck:Japanese tag:kani");
            clickText(scenario, "Save import filters");
            waitForText(scenario, "Import filters");
            LocalStore(context).use { store ->
                var saved = SyncSettings.fromStore(store)
                assertFalse(saved.importActiveCards);
                assertFalse(saved.importSuspendedCards);
                assertFalse(saved.importTaggedCards);
                assertFalse(saved.importWeakCards);
                assertTrue(saved.importBrowserQueryCards);
                assertEquals("deck:Japanese tag:kani", saved.importBrowserQuery);
            }
        }
    }

    @Test
fun testImportFilterFieldsAndPresetsPersistThroughAttachedComposePanel() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, "Settings");
            setImportFilterChecked(SettingsTextCopy.suspendedCardsLabel(), true);
            setComposeTextField(SettingsTextCopy.fsrsDifficultyLabel(), "not numeric");
            clickText(scenario, "Save import filters");
            assertDefaultImportSettingsStillStored();

            setImportFilterChecked(SettingsTextCopy.activeCardsLabel(), true);
            setImportFilterChecked(SettingsTextCopy.suspendedCardsLabel(), false);
            setImportFilterChecked(SettingsTextCopy.taggedCardsLabel(), true);
            setImportFilterChecked(SettingsTextCopy.weakCardsLabel(), true);
            setImportFilterChecked(SettingsTextCopy.browserQueryLabel(), true);
            setComposeTextField(SettingsTextCopy.ankiBrowserQueryLabel(), "deck:Kiku tag:kani");
            setComposeTextField(SettingsTextCopy.ankiNoteTagsLabel(), "tagAlpha, tagBeta");
            setComposeTextField(SettingsTextCopy.fsrsDifficultyLabel(), "8.5");
            setComposeTextField(SettingsTextCopy.lapsesLabel(), "4");
            setComposeTextField(SettingsTextCopy.minimumMatchingCardsLabel(), "2");
            clickText(scenario, "Save import filters");
            waitForText(scenario, "Import filters");
            assertCustomImportSettingsStored();

            clickText(scenario, "Leech tag");
            waitForText(scenario, "Import filters");
            assertLeechImportPresetStored();

            clickText(scenario, "Mining deck");
            waitForText(scenario, "Import filters");
            assertMiningDeckPresetStored();
        }
    }

    @Test
fun testFrequencyRangeValidationRunsThroughAttachedComposePanel() {
        var defaults = RecordsSyncModels.Settings.kikuDefaults()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, "Settings");
            setFrequencyRangeInputs("many", Integer.toString(defaults.suspendedRankMax));
            clickText(scenario, SettingsTextCopy.saveFrequencyRangeLabel());
            assertFrequencyRangeStored(defaults.suspendedRankMin, defaults.suspendedRankMax);

            setFrequencyRangeInputs("0", "25");
            clickText(scenario, SettingsTextCopy.saveFrequencyRangeLabel());
            assertFrequencyRangeStored(defaults.suspendedRankMin, defaults.suspendedRankMax);

            setFrequencyRangeInputs("10", "50000");
            clickText(scenario, SettingsTextCopy.saveFrequencyRangeLabel());
            assertFrequencyRangeStored(defaults.suspendedRankMin, defaults.suspendedRankMax);

            setFrequencyRangeInputs("300", "20");
            clickText(scenario, SettingsTextCopy.saveFrequencyRangeLabel());
            assertFrequencyRangeStored(20, 300);
        }
    }

    @Test
fun testNoteTypeSettingsValidateCustomSaveAndReset() {
        var defaults = RecordsSyncModels.Settings.kikuDefaults()

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, "Settings");
            scenario.onActivity { activity ->
                assertHasText(activity, "Using " + defaults.modelName);
            }
            setComposeTextField(SettingsTextCopy.noteTypeStatusLabel(), "");
            clickText(scenario, "Save note type");
            assertNoteTypeSettings(defaults);

            setComposeTextField(SettingsTextCopy.noteTypeStatusLabel(), "Custom Mining");
            setComposeTextField(SettingsTextCopy.expressionFieldLabel(), "");
            clickText(scenario, "Save note type");
            assertNoteTypeSettings(defaults);

            setComposeTextField(SettingsTextCopy.noteTypeStatusLabel(), "Custom Mining");
            setComposeTextField(SettingsTextCopy.expressionFieldLabel(), "Word");
            setComposeTextField(SettingsTextCopy.readingFieldLabel(), "WordReading");
            setComposeTextField(SettingsTextCopy.meaningFieldLabel(), "Gloss");
            setComposeTextField(SettingsTextCopy.sentenceFieldLabel(), "Context");
            setComposeTextField(SettingsTextCopy.frequencyFieldLabel(), "Freq");
            setComposeTextField(SettingsTextCopy.frequencySortFieldLabel(), "SortKey");
            clickText(scenario, "Save note type");
            waitForText(scenario, "Using Custom Mining");
            assertNoteTypeSettings(RecordsSyncModels.Settings(
                    "Custom Mining",
                    defaults.templateName,
                    "Word",
                    "WordReading",
                    "Gloss",
                    "Context",
                    "Freq",
                    "SortKey",
                    defaults.matureDays,
                    defaults.matureSupportThreshold,
                    defaults.suspendedRankMin,
                    defaults.suspendedRankMax,
                    defaults.activeQueueCap,
                    defaults.newPerDay,
                    defaults.writingTriggerMissDays,
                    defaults.recognitionPromotionPasses,
                    defaults.realDueReviewsToMove,
                    defaults.importActiveCards,
                    defaults.importSuspendedCards,
                    defaults.importTaggedCards,
                    defaults.importTags,
                    defaults.importWeakCards,
                    defaults.importWeakFsrsDifficultyThreshold,
                    defaults.importWeakLapsesThreshold,
                    defaults.importMinMatchingCardsPerKanji,
                    defaults.importBrowserQueryCards,
                    defaults.importBrowserQuery
            ));

            clickText(scenario, "Use Kiku");
            clickText(scenario, "Save note type");
            waitForText(scenario, "Using " + defaults.modelName);
            assertNoteTypeSettings(defaults);
        }
    }

    @Test
fun testReferenceDataLicensesRoundTripFromSettings() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, "Settings");
            clickText(scenario, "Display & data");
            waitForText(scenario, "Open data licenses");
            clickText(scenario, "Open data licenses");
            waitForText(scenario, "Data licenses");
            scenario.onActivity { activity ->
                assertHasText(activity, "Dictionary data");
                assertHasText(activity, "Stroke data");
                assertHasText(activity, "Fonts");
                assertHasText(activity, "Back to settings");
            }
            clickText(scenario, "Back to settings");
            waitForText(scenario, "Display & data");
        }
    }

    @Test
fun testUpdateScreenShowsAutomaticStatusAndInstallPermissionFlow() {
        MainActivityRuntimeOverrides.setInstallPermission(false);

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, "Settings");
            clickText(scenario, "Reminders & updates");
            waitForText(scenario, "App updates");
            scenario.onActivity { activity ->
                assertHasText(activity, "On: checks about once a day");
                assertHasText(activity, "Last check: not yet");
                assertHasText(activity, "Install permission: Missing");
                assertHasText(activity, "Set up app installs");
                assertHasText(activity, "Turn off automatic updates");
            }
            clickText(scenario, "Turn off automatic updates");
            waitForText(scenario, "Off");
            clickText(scenario, "Turn on automatic updates");
            waitForText(scenario, "On: checks about once a day");
        }
    }

    @Test
fun testUpdateScreenSurfacesCachedPendingUpdate() {
        var updatesDir = File(context.getCacheDir(), "updates")
        assertTrue(updatesDir.mkdirs() || updatesDir.isDirectory());
        FileOutputStream(File(updatesDir, "kani-test.apk")).use { output ->
            output.write(byteArrayOf(1, 2, 3))
        }
        LocalStore(context).use { store ->
            store.recordAutoUpdateResult(
                    System.currentTimeMillis(),
                    "Android needs confirmation to finish installing.",
                    "v9.9.9",
                    "kani-test.apk",
                    "Android needs confirmation before Kani can replace itself."
            );
        }
        MainActivityRuntimeOverrides.setInstallPermission(true);

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, "Settings");
            clickText(scenario, "Reminders & updates");
            waitForText(scenario, "Verified APK ready: 9.9.9");
            scenario.onActivity { activity ->
                assertHasText(activity, "Install permission: Ready");
                assertHasText(activity, "Install verified update");
                assertHasText(activity, "Android needs confirmation before Kani can replace itself.");
            }
        }

        val openUpdate = Intent(context, MainActivity::class.java)
            .putExtra(MainActivityBase.EXTRA_OPEN_UPDATE, true)
        val scenario = ActivityScenario.launch<MainActivity>(openUpdate)
        try {
            waitForText(scenario, "Verified APK ready: 9.9.9")
            scenario.onActivity { activity ->
                assertHasText(activity, "Install permission: Ready")
                assertHasText(activity, "Install verified update")
                assertHasText(activity, "Android needs confirmation before Kani can replace itself.")
            }
            clickText(scenario, "Install verified update")
            waitForText(scenario, "Last result: APK metadata could not be read. Install blocked.")
            scenario.onActivity { activity -> assertNoText(activity, "Install verified update") }
        } finally {
            scenario.close()
        }
    }

    @Test
fun testStatsConnectsKaniPracticeToAnkiImpact() {
        var now = System.currentTimeMillis()
        LocalStore(context).use { store ->
            store.saveSuccessfulSync(
                    RecordsSyncModels.CollectionSnapshot(Collections.emptyList(), Collections.emptyList()),
                    Collections.emptyList(),
                    Arrays.asList(
                            statsDashboardRow("痛", 82, 1),
                            statsDashboardRow("薬", 76, 0),
                            statsDashboardRow("疲", 69, 0),
                            statsDashboardRow("平", 74, 1)
                    ),
                    RecordsSyncModels.Settings.kikuDefaults(),
                    now - 30_000L,
                    now - 20_000L,
                    null
            );
            store.saveReview(review("痛", "stats-pain"), "good", now - 15_000L);
            store.saveReview(review("薬", "stats-medicine"), "good", now - 14_000L);
            store.saveReview(review("疲", "stats-tired"), "good", now - 13_000L);
            store.saveReview(review("平", "stats-flat"), "good", now - 12_000L);
            store.saveSuccessfulSync(
                    RecordsSyncModels.CollectionSnapshot(Collections.emptyList(), Collections.emptyList()),
                    Collections.emptyList(),
                    Arrays.asList(
                            statsDashboardRow("痛", 46, 3),
                            statsDashboardRow("薬", 51, 2),
                            statsDashboardRow("疲", 44, 1),
                            statsDashboardRow("平", 50, 1)
                    ),
                    RecordsSyncModels.Settings.kikuDefaults(),
                    now - 5_000L,
                    now,
                    null
            );
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, "Stats");
            scenario.onActivity { activity ->
                assertStatsTimePanel(activity);
                assertWeaknessBurnDownPanel(activity);
                assertSupportConversionPanel(activity);
                assertStatsScreenOmitsLegacyCopy(activity);
            }
        }
    }

private fun assertStatsTimePanel(activity: MainActivity) {
        assertHasText(activity, "Kani is working for you");
        assertHasText(activity, "Answered study time");
        assertHasText(activity, "Today: 0 sec");
        assertHasText(activity, "Last 7 days: 0 sec");
        assertHasText(activity, "Answered tasks: 0");
        assertHasText(activity, "Avg / task: 0 sec");
    }

private fun assertWeaknessBurnDownPanel(activity: MainActivity) {
        assertHasText(activity, "Weakness Burn-Down");
        assertHasText(activity, "4 weak kanji improved");
        assertHasText(activity, "Average weakness: 0.75 -> 0.48 after Kani practice.");
        assertHasText(activity, "痛  0.82 -> 0.46");
        assertHasText(activity, "薬  0.76 -> 0.51");
        assertHasText(activity, "疲  0.69 -> 0.44");
        assertNoText(activity, "平  0.74 -> 0.50");
    }

private fun assertSupportConversionPanel(activity: MainActivity) {
        assertHasText(activity, "Anki Support Conversion");
        assertHasText(activity, "5 mature cards gained");
        assertHasText(activity, "2 kanji gained first mature support.");
        assertHasText(activity, "痛  1 -> 3 mature cards");
        assertHasText(activity, "薬  0 -> 2 mature cards");
        assertHasText(activity, "疲  0 -> 1 mature cards");
        assertHasText(activity, "Ladder Health");
        assertHasText(activity, "0 active kanji on the ladder");
    }

private fun assertStatsScreenOmitsLegacyCopy(activity: MainActivity) {
        assertNoText(activity, "Anki impact");
        assertNoText(activity, "Kani writing");
        assertNoText(activity, "Now practicing");
        assertNoText(activity, "What this means");
        assertNoText(activity, "You are turning Anki pain points");
    }

    @Test
fun testStatsShowsImpactHistoryBuckets() {
        var now = System.currentTimeMillis()
        LocalStore(context).use { store ->
            store.saveSuccessfulSync(
                    RecordsSyncModels.CollectionSnapshot(Collections.emptyList(), Collections.emptyList()),
                    Collections.emptyList(),
                    Collections.singletonList(statsDashboardRow("裂", 80, 0)),
                    RecordsSyncModels.Settings.kikuDefaults(),
                    now - 20_000L,
                    now - 10_000L,
                    null
            );
            store.saveReview(review("裂", "impact-no-after-sync"), "good", now - 9_000L);
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, "Stats");
            scenario.onActivity { activity ->
                assertHasText(activity, "Kani is not currently working for you");
                assertHasText(activity, "0 weak kanji improved");
                assertHasText(activity, "Weakness improvements will show after Kani reviews are followed by a successful AnkiDroid sync.");
                assertHasText(activity, "0 mature cards gained");
                assertHasText(activity, "0 kanji gained first mature support.");
                assertHasText(activity, "0 active kanji on the ladder");
                assertNoText(activity, "helped kanji");
                assertNoText(activity, "not-helping-yet kanji");
                assertNoText(activity, "needs-more-cards kanji");
            }
        }
    }

fun statsDashboardRow(kanji: String, weaknessScore: Int, matureSupportCount: Int): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
                kanji,
                null,
                "",
                "",
                kanji,
                weaknessScore,
                "suspended_archive",
                "Stats fixture",
                1,
                1,
                matureSupportCount,
                Collections.emptyList<RecordsImportModels.Example>()
        );
    }

    @Test
fun testKanjiDetailCopyAndStudyReviewFlow() {
        seedDashboard();
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertHasTexts(activity, "Focus", "Focus queue", "Ramen radical gap", "From 拉麺");
            }

            clickText(scenario, "拉");
            scenario.onActivity { activity -> assertKanjiDetailReady(activity) }

            clickText(scenario, "Copy Anki search");
            scenario.onActivity { activity ->
                var clipboard = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                var clip = clipboard.getPrimaryClip()
                if (clip != null) {
                    assertEquals("deck:Kiku 拉", clip.getItemAt(0).coerceToText(activity).toString());
                }
                assertHasText(activity, "Copied Anki search");
            }

            clickText(scenario, "Review this now");
            scenario.onActivity { activity -> assertHiddenRecognitionCard(activity) }

            clickText(scenario, REVEAL);
            scenario.onActivity { activity -> assertRevealedRecognitionCard(activity) }

            clickText(scenario, "Fail");
            assertFailedRecognitionReviewStored();
        }
    }

    @Test
fun testKanjiDetailTimelineShowsReviewAfterStudy() {
        seedDashboard();
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            clickText(scenario, REVEAL);
            clickText(scenario, "Pass");
            scenario.onActivity { activity ->
                assertHasText(activity, "Today's focus done");
                assertHasText(activity, "Continue all kanji");
            }
            clickText(scenario, "Back home");
            clickText(scenario, "拉");
            scenario.onActivity { activity ->
                assertHasText(activity, "Recovery timeline");
                assertHasText(activity, "Resting until review");
                assertHasText(activity, "Review passed");
                assertHasText(activity, "Recall review was rated good.");
            }
        }
    }

    @Test
fun testHomeQueuePreviewShowsActivePracticeKanjiNotEveryCandidate() {
        var active = dashboardRow("拉", RAMEN_RADICAL_GAP, "ら", IMPORTED_FROM_SUSPENDED_CARDS)
        var retired = dashboardRow("謎", "mystery unused", "なぞ", "Already covered by known cards")
        seedDashboard(Arrays.asList(active, retired));
        LocalStore(context).use { store ->
            store.replaceStudyItems(Arrays.asList(
                    RecordsStudyModels.StudyItem("拉", "new", 0L, 0.4, 5.0, 0, 0, 0, 0, null, 0L),
                    RecordsStudyModels.StudyItem("謎", "retired", 0L, 0.4, 5.0, 1, 0, 2, 3, null, 0L)
            ));
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertHasText(activity, "Focus queue");
                assertHasText(activity, "Ramen radical gap");
                assertHasText(activity, "From 拉麺");
                assertNoText(activity, "mystery unused");
                assertNoText(activity, "謎");
            }
        }
    }

    @Test
fun testHomeViewAllShowsFullFocusQueue() {
        seedDashboard(Arrays.asList(
                dashboardRow("拉", RAMEN_RADICAL_GAP, "ら", IMPORTED_FROM_SUSPENDED_CARDS),
                dashboardRow("謎", "mystery radical gap", "なぞ", MISSED_IN_MATURE_CARDS),
                dashboardRow("示", "show", "しめす", MISSED_IN_MATURE_CARDS),
                dashboardRow("浸", "to be soaked in", "ひたす", MISSED_IN_MATURE_CARDS)
        ));

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertHasText(activity, "View all");
            }
            clickText(scenario, "View all");
            scenario.onActivity { activity ->
                assertHasText(activity, "Focus queue");
                assertHasText(activity, "To be soaked in");
            }
        }
    }

    @Test
fun testRecentMistakesOpensMissedReviewList() {
        seedDashboard();
        LocalStore(context).use { store ->
            store.saveReview(review("拉", "recent-miss"), "again", System.currentTimeMillis());
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, "Recent mistakes");
            scenario.onActivity { activity ->
                assertHasText(activity, "Recent mistakes");
                assertHasText(activity, "Ramen radical gap");
                assertHasText(activity, "Rated again");
            }
        }
    }

    @Test
fun testBrowsingHomeQueuePreviewDoesNotAdmitNewStudyItems() {
        seedDashboardRowsOnly(Collections.singletonList(dashboardRow("拉", RAMEN_RADICAL_GAP, "ら", IMPORTED_FROM_SUSPENDED_CARDS)));
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertHasText(activity, "No active practice yet");
                assertHasText(activity, STUDY_NOW);
            }

            LocalStore(context).use { store ->
                assertTrue(store.studyItems().isEmpty());
            }
        }
    }

    @Test
fun testLearnNextProblemKanjiFromHomeAdmitsStudyItem() {
        seedDashboardRowsOnly(Collections.singletonList(dashboardRow("拉", RAMEN_RADICAL_GAP, "ら", IMPORTED_FROM_SUSPENDED_CARDS)));
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            scenario.onActivity { activity ->
                assertHasText(activity, "Name this kanji");
                assertHasText(activity, RECOGNISE);
                assertHasText(activity, RECOGNITION_QUESTION);
                assertHasText(activity, REVEAL);
            }

            LocalStore(context).use { store ->
                var items = store.studyItems()
                assertEquals(1, items.size);
                assertEquals("拉", items.get(0).kanji);
            }
        }
    }

    @Test
fun testStudyNowStopsAtConfiguredMaximumItems() {
        seedDashboardRowsOnly(Arrays.asList(
                dashboardRow("拉", RAMEN_RADICAL_GAP, "ら", IMPORTED_FROM_SUSPENDED_CARDS),
                dashboardRow("謎", "mystery radical gap", "なぞ", MISSED_IN_MATURE_CARDS),
                dashboardRow("示", "show radical gap", "しめす", MISSED_IN_MATURE_CARDS),
                dashboardRow("浸", "soak radical gap", "ひたす", MISSED_IN_MATURE_CARDS),
                dashboardRow("確", "certain radical gap", "たし", MISSED_IN_MATURE_CARDS),
                dashboardRow("曜", "weekday radical gap", "よう", MISSED_IN_MATURE_CARDS),
                dashboardRow("麺", "noodle radical gap", "めん", MISSED_IN_MATURE_CARDS),
                dashboardRow("提", "present radical gap", "てい", MISSED_IN_MATURE_CARDS)
        ));
        LocalStore(context).use { setupStore ->
            setupStore.saveAdaptiveLoadMode(AdaptiveLoadPlanner.MODE_MANUAL);
            setupStore.saveAdaptiveLoadWorkPercent(100);
            setupStore.saveAdaptiveLoadMaxItems(3);
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            for (i in 0 until 3) {
                waitForText(scenario, REVEAL);
                clickText(scenario, REVEAL);
                waitForText(scenario, "Pass");
                clickText(scenario, "Pass");
            }
            waitForText(scenario, "Today's focus done");
            scenario.onActivity { activity ->
                assertHasText(activity, "Study now: 3 / 3");
                assertHasText(activity, "Study more new cards");
                assertHasText(activity, "Continue all kanji");
                assertNoText(activity, REVEAL);
            }

            LocalStore(context).use { store ->
                assertEquals(3, store.reviewStatsSince(0L).total);
            }
        }
    }

    @Test
fun testStudyMoreNewCardsStartsOneTimeExtraSetWithoutChangingWorkload() {
        seedDashboardRowsOnly(Arrays.asList(
                dashboardRow("拉", RAMEN_RADICAL_GAP, "ら", IMPORTED_FROM_SUSPENDED_CARDS),
                dashboardRow("謎", "mystery radical gap", "なぞ", MISSED_IN_MATURE_CARDS),
                dashboardRow("示", "show radical gap", "しめす", MISSED_IN_MATURE_CARDS),
                dashboardRow("浸", "soak radical gap", "ひたす", MISSED_IN_MATURE_CARDS),
                dashboardRow("確", "certain radical gap", "たし", MISSED_IN_MATURE_CARDS),
                dashboardRow("曜", "weekday radical gap", "よう", MISSED_IN_MATURE_CARDS),
                dashboardRow("麺", "noodle radical gap", "めん", MISSED_IN_MATURE_CARDS),
                dashboardRow("提", "present radical gap", "てい", MISSED_IN_MATURE_CARDS)
        ));
        LocalStore(context).use { setupStore ->
            setupStore.saveAdaptiveLoadMode(AdaptiveLoadPlanner.MODE_MANUAL);
            setupStore.saveAdaptiveLoadWorkPercent(100);
            setupStore.saveAdaptiveLoadMaxItems(3);
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            for (i in 0 until 3) {
                waitForText(scenario, REVEAL);
                clickText(scenario, REVEAL);
                waitForText(scenario, "Pass");
                clickText(scenario, "Pass");
            }
            waitForText(scenario, "Study more new cards");

            clickText(scenario, "Study more new cards");
            enterDialogEditText("2");
            clickText(scenario, "Study");
            waitForText(scenario, REVEAL);
            scenario.onActivity { activity -> assertHasText(activity, "0 / 2") }
            for (i in 0 until 2) {
                waitForText(scenario, REVEAL);
                clickText(scenario, REVEAL);
                waitForText(scenario, "Pass");
                clickText(scenario, "Pass");
            }
            waitForText(scenario, "Today's focus done");
            scenario.onActivity { activity ->
                assertHasText(activity, "Study now: 2 / 2");
                assertHasText(activity, "Study more new cards");
                assertNoText(activity, REVEAL);
            }

            LocalStore(context).use { store ->
                assertEquals(5, store.reviewStatsSince(0L).total);
                assertEquals(3, store.adaptiveLoadMaxItems());
            }
        }
    }

    @Test
fun testHomeQueuePreviewOrderMatchesReviewFirstStudySelection() {
        var newRow = dashboardRow("拉", RAMEN_RADICAL_GAP, "ら", IMPORTED_FROM_SUSPENDED_CARDS)
        var reviewRow = dashboardRow("謎", "mystery radical gap", "なぞ", MISSED_IN_MATURE_CARDS)
        seedDashboard(Arrays.asList(newRow, reviewRow));
        LocalStore(context).use { store ->
            store.replaceStudyItems(Arrays.asList(
                    RecordsStudyModels.StudyItem("拉", "new", 0L, 0.4, 5.0, 0, 0, 0, 0, null, 0L),
                    RecordsStudyModels.StudyItem("謎", "review", 500L, 1.8, 4.8, 2, 0, 2, 3, null, 0L)
            ));
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertHasText(activity, STUDY_NOW);
                assertHasText(activity, "Mystery radical gap");
                assertHasText(activity, "Ramen radical gap");
            }
            clickText(scenario, STUDY_NOW);
            scenario.onActivity { activity ->
                assertHasText(activity, "Name this kanji");
                assertHasText(activity, RECOGNISE);
                assertHasText(activity, RECOGNITION_QUESTION);
            }
        }
    }

    @Test
fun testRestingActiveKanjiShowsNothingDueNow() {
        var now = System.currentTimeMillis()
        seedDashboardRowsOnly(Collections.singletonList(dashboardRow("拉", RAMEN_RADICAL_GAP, "ら", IMPORTED_FROM_SUSPENDED_CARDS)));
        LocalStore(context).use { store ->
            store.replaceStudyItems(Collections.singletonList(
                    RecordsStudyModels.StudyItem("拉", "review", now + 86_400_000L, 1.2, 4.8, 1, 0, 1, 0, null, now - 86_400_000L)
            ));
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            scenario.onActivity { activity ->
                assertHasText(activity, "Nothing due now");
                assertHasText(activity, "Your active kanji are resting");
                assertHasText(activity, "Back home");
                assertNoText(activity, REVEAL);
            }
            LocalStore(context).use { store ->
                assertEquals(0, store.reviewStatsSince(0L).total);
                var item = onlyStudyItem(store)
                assertEquals("拉", item.kanji);
                assertTrue(item.dueAtMillis > now);
            }
        }
    }

    @Test
fun testMissingStrokeGuideIsExplainedBeforeDrawing() {
        seedDueWritingItem(dashboardRow("鿃", "rare shape", "ソウ", IMPORTED_FROM_SUSPENDED_CARDS), 0);
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            scenario.onActivity { activity ->
                assertHasText(activity, "Draw it, then check");
                assertHasText(activity, "Stroke-order feedback will be limited");
                assertNoText(activity, "Trace the strokes");
            }
        }
    }

    @Test
fun testWritingHintPreservesInkWhileIncreasingSupport() {
        seedDueWritingItem(3);

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            scenario.onActivity { activity ->
                assertHasText(activity, "Practice");
                assertHasText(activity, "Draw this kanji");
                assertHasText(activity, "Write kanji");
                assertHasText(activity, "More help");
            }

            scenario.onActivity { activity -> drawGuideKanji(activity, "拉") }
            clickText(scenario, "More help");
            scenario.onActivity { activity ->
                assertHasText(activity, "Hint used");
                assertHasText(activity, "ink stayed on the canvas");
                assertHasText(activity, "current stroke hinted");
                val pad: DrawingPadView = requireNotNull(findType(activity.findViewById<View>(android.R.id.content), DrawingPadView::class.java))
                assertNotNull(pad);
                assertTrue(pad.hasInk());
            }
        }
    }

    @Test
fun testWritingModeBlocksFarOffGuideStrokeAndShowsUndo() {
        seedDueWritingItem(3);

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            scenario.onActivity { activity ->
                assertHasText(activity, "Undo");
                drawFarOffGuideStroke(activity);
            }
            scenario.onActivity { activity ->
                assertHasText(activity, "Stay close to stroke 1");
                val pad: DrawingPadView = requireNotNull(findType(activity.findViewById<View>(android.R.id.content), DrawingPadView::class.java))
                assertNotNull(pad);
                assertFalse(pad.hasInk());
                assertFalse(pad.canUndoStroke());
            }
        }
    }

    @Test
fun testTryCleanerStartsFreshWritingAttemptAtSameHelpLevel() {
        seedDueWritingItem(2);
        MainActivityRuntimeOverrides.setWritingRecognizer(FakeWritingRecognizer("拉"));
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            scenario.onActivity { activity -> drawGuideKanjiWithFirstStrokeReversed(activity, "拉") }
            clickText(scenario, CHECK);
            waitForText(scenario, "Try cleaner");

            clickText(scenario, "Try cleaner");
            scenario.onActivity { activity ->
                assertHasText(activity, "Try cleaner. Keep the same help level");
                assertHasText(activity, CHECK);
                assertNoText(activity, "Recognized, but the stroke path was messy");
                assertNoText(activity, "Replay");
                assertNoText(activity, PASS_AFTER_WRITING);
                val pad: DrawingPadView = requireNotNull(findType(activity.findViewById<View>(android.R.id.content), DrawingPadView::class.java))
                assertNotNull(pad);
                assertFalse(pad.hasInk());
                assertFalse(pad.hasReplaySnapshot());
            }
        }
    }

    @Test
fun testWritingRepairCheckKeepsSingleCleanMatchMessage() {
        seedDueWritingItem(3);
        MainActivityRuntimeOverrides.setWritingRecognizer(FakeWritingRecognizer("拉"));

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            scenario.onActivity { activity ->
                assertHasText(activity, "Practice");
                assertHasText(activity, "Write kanji");
                drawGuideKanji(activity, "拉");
            }
            clickText(scenario, CHECK);
            scenario.onActivity { activity ->
                assertHasText(activity, CLEAN_MATCH);
                assertHasText(activity, "Target: 拉");
                assertHasText(activity, PASS_AFTER_WRITING);
                assertHasText(activity, "Reference");
                assertEquals(1, countText(activity.findViewById<View>(android.R.id.content), CLEAN_MATCH));
            }
        }
    }

    @Test
fun testDiagnosisTextAndReplayAppearAfterCheck() {
        seedDueWritingItem();
        MainActivityRuntimeOverrides.setWritingRecognizer(FakeWritingRecognizer("拉"));
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            scenario.onActivity { activity -> drawGuideKanjiWithFirstStrokeReversed(activity, "拉") }
            clickText(scenario, CHECK);
            waitForText(scenario, "likely wrong direction");
            scenario.onActivity { activity ->
                assertHasText(activity, "Stroke 1: likely wrong direction");
                assertHasText(activity, "Recognized, but the stroke path was messy");
                assertHasText(activity, "Try cleaner");
                assertHasText(activity, "Save hard");
                assertHasText(activity, PASS_AFTER_WRITING);
                assertHasText(activity, "Replay");
            }
        }
    }

    @Test
fun testReplayHiddenWhenStrokeGuideMissing() {
        seedDueWritingItem(dashboardRow("鿃", "rare shape", "ソウ", IMPORTED_FROM_SUSPENDED_CARDS), 0);
        MainActivityRuntimeOverrides.setWritingRecognizer(FakeWritingRecognizer("鿃"));
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            scenario.onActivity { activity -> drawFreeformStroke(activity) }
            clickText(scenario, CHECK);
            scenario.onActivity { activity ->
                assertHasText(activity, "Stroke order could not be checked");
                assertNoText(activity, "Replay");
            }
        }
    }

    @Test
fun testUndoAfterCheckClearsPriorReplayState() {
        seedDueWritingItem();
        MainActivityRuntimeOverrides.setWritingRecognizer(FakeWritingRecognizer("拉"));
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            scenario.onActivity { activity -> drawGuideKanji(activity, "拉") }
            clickText(scenario, CHECK);
            waitForText(scenario, CLEAN_MATCH);
            scenario.onActivity { activity ->
                assertHasText(activity, "Replay");
                assertHasText(activity, PASS_AFTER_WRITING);
            }
            clickText(scenario, "Undo");
            scenario.onActivity { activity ->
                assertHasText(activity, "Undid the last stroke");
                assertNoText(activity, CLEAN_MATCH);
                assertNoText(activity, "Replay");
                assertNoText(activity, PASS_AFTER_WRITING);
                val pad: DrawingPadView = requireNotNull(findType(activity.findViewById<View>(android.R.id.content), DrawingPadView::class.java))
                assertNotNull(pad);
                assertFalse(pad.hasReplaySnapshot());
            }
        }
    }

    @Test
fun testWritingRepairMissKeepsRepairReference() {
        seedDueWritingItem(3);
        MainActivityRuntimeOverrides.setWritingRecognizer(FakeWritingRecognizer("提"));

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            scenario.onActivity { activity ->
                assertHasText(activity, "Practice");
                assertHasText(activity, "Write kanji");
                drawGuideKanji(activity, "拉");
            }
            clickText(scenario, CHECK);
            scenario.onActivity { activity ->
                assertHasText(activity, "I could not read that as the target kanji yet");
                assertHasText(activity, "Reference");
                assertHasText(activity, "Latin, kidnap");
                assertHasText(activity, "Try again with full guide");
                assertHasText(activity, PASS_AFTER_WRITING);
            }
        }
    }

    @Test
fun testStudyLoopActionsArePinnedOutsideScrollableContent() {
        seedDueWritingItem();
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            scenario.onActivity { activity ->
                var root = activity.findViewById<View>(android.R.id.content)
                var check = findExactText(root, CHECK)
                var erase = findExactText(root, "Erase")
                val checkView = requireNotNull(check)
                val eraseView = requireNotNull(erase)
                assertFalse(hasAncestorOfType(checkView, ScrollView::class.java))
                assertFalse(hasAncestorOfType(eraseView, ScrollView::class.java))
            }
        }
    }

    @Test
fun testNewKanjiStartsAsHiddenFlashcardAndKnownAnswerLogsRecognitionReview() {
        seedDashboard();
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            var hiddenCardHeight = recognitionCardHeight(scenario)
            assertTrue("Hidden card should be measured", hiddenCardHeight > 0);
            scenario.onActivity { activity ->
                assertHasTexts(activity, "Name this kanji", RECOGNISE, "Answer hidden until reveal");
                assertNoTexts(activity, "拉麺");
                var root = activity.findViewById<View>(android.R.id.content)
                var reveal = findExactText(root, REVEAL)
                val revealView = requireNotNull(reveal)
                val revealBounds = Rect()
                assertTrue(revealView.getGlobalVisibleRect(revealBounds))
                assertFalse(hasAncestorOfType(revealView, ScrollView::class.java))
            }
            clickText(scenario, REVEAL);
            scenario.onActivity { activity ->
                assertHasTexts(activity, "Answer", "拉", "Fail", "Pass");
                assertTrue("Revealed card should keep full study height",
                        flashcardBounds(activity).height() >= hiddenCardHeight - 2);
                var failBounds = Rect()
                var passBounds = Rect()
                var root = activity.findViewById<View>(android.R.id.content)
                var answer = findExactText(root, "Answer")
                var fail = findExactText(root, "Fail")
                var pass = findExactText(root, "Pass")
                val answerView = requireNotNull(answer)
                val failView = requireNotNull(fail)
                val passView = requireNotNull(pass)
                val answerBounds = Rect()
                assertTrue(answerView.getGlobalVisibleRect(answerBounds))
                assertTrue(failView.getGlobalVisibleRect(failBounds))
                assertTrue(passView.getGlobalVisibleRect(passBounds))
                assertTrue("Fail should be left of Pass", failBounds.centerX() < passBounds.centerX());
            }
            clickText(scenario, "Pass");

            assertKnownAnswerRecognitionReviewStored();
        }
    }

    @Test
fun testRevealedRecognitionCardPassesOnRightSwipe() {
        seedDashboard();
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            clickText(scenario, REVEAL);
            swipeRecognitionCard(scenario, true);

            assertKnownAnswerRecognitionReviewStored();
        }
    }

    @Test
fun testRevealedRecognitionCardFailsOnLeftSwipe() {
        seedDashboard();
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            clickText(scenario, REVEAL);
            swipeRecognitionCard(scenario, false);

            assertFailedRecognitionReviewStored();
        }
    }

    @Test
fun testHiddenRecognitionSwipeDoesNotGradeBeforeReveal() {
        seedDashboard();
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            swipeRecognitionCard(scenario, true);

            scenario.onActivity { activity ->
                assertHasTexts(activity, "Name this kanji", "Answer hidden until reveal", REVEAL);
                assertNoTexts(activity, "Latin, kidnap", "Fail", "Pass");
            }
            LocalStore(context).use { store ->
                assertEquals(0, store.reviewStatsSince(0L).total);
            }
        }
    }

    @Test
fun testRevealedRecognitionCardSwipesFromAnswerPanelAdvanceQueueBothDirections() {
        seedDashboard(Arrays.asList(
                dashboardRow("拉", RAMEN_RADICAL_GAP, "ら", IMPORTED_FROM_SUSPENDED_CARDS),
                dashboardRow("謎", "mystery radical gap", "なぞ", MISSED_IN_MATURE_CARDS),
                dashboardRow("示", "show radical gap", "しめす", MISSED_IN_MATURE_CARDS)
        ));
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            clickText(scenario, REVEAL);
            waitForRevealedAnswerPanel(scenario)
            swipeRecognitionCardFromAnswerPanel(scenario, true);
            scenario.onActivity { activity ->
                assertFalse(activity.flashcardAnswerRevealed)
                assertHasText(activity, REVEAL)
                assertNoTexts(activity, "Fail", "Pass")
            }
            LocalStore(context).use { store ->
                val stats = store.reviewStatsSince(0L)
                assertEquals(1, stats.total)
                assertEquals(1, stats.good)
                assertEquals(0, stats.again)
                assertEquals(0, stats.writingRequired)
                val items = store.studyItems()
                assertEquals(3, items.size)
                val item = items.first { it.kanji == "拉" }
                assertEquals("learning", item.state)
                assertEquals(1, item.totalReviews)
                assertEquals(1, item.learningStep)
                assertEquals(0, item.writingLevel)
                assertEquals(0, item.recognitionStage)
                assertEquals(1, item.kanjiMeaningMemory.totalReviews)
                assertEquals("good", item.kanjiMeaningMemory.lastRating)
                assertEquals(0, item.realPassStreak)
            }
        }

        context.deleteDatabase("kanji_anki_simple.db");
        seedDashboard(Arrays.asList(
                dashboardRow("拉", RAMEN_RADICAL_GAP, "ら", IMPORTED_FROM_SUSPENDED_CARDS),
                dashboardRow("謎", "mystery radical gap", "なぞ", MISSED_IN_MATURE_CARDS),
                dashboardRow("示", "show radical gap", "しめす", MISSED_IN_MATURE_CARDS)
        ));
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            clickText(scenario, REVEAL);
            waitForRevealedAnswerPanel(scenario)
            swipeRecognitionCardFromAnswerPanel(scenario, false);
            scenario.onActivity { activity ->
                assertFalse(activity.flashcardAnswerRevealed)
                assertHasText(activity, REVEAL)
                assertNoTexts(activity, "Fail", "Pass")
            }
            LocalStore(context).use { store ->
                val stats = store.reviewStatsSince(0L)
                assertEquals(1, stats.total)
                assertEquals(1, stats.again)
                assertEquals(0, stats.good)
                assertEquals(0, stats.writingRequired)
                val items = store.studyItems()
                assertEquals(3, items.size)
                val item = items.first { it.kanji == "拉" }
                assertEquals(1, item.kanjiMeaningMemory.totalReviews)
                assertEquals("again", item.kanjiMeaningMemory.lastRating)
                assertEquals(0, item.consecutiveFailedRecognitionDays)
                assertEquals(0, item.realAgainStreak)
                assertFalse(item.writingRemediationPending)
            }
        }
    }

    @Test
fun testSimilarChoiceMissLogsReviewAndShowsWritingRepairQueue() {
        seedSimilarChoiceDashboard();

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            scenario.onActivity { activity -> assertSimilarChoiceCard(activity, "0 / 1", "Which kanji means ramen radical gap?", "拉", "提") }

            clickText(scenario, "提");
            scenario.onActivity { activity ->
                assertHasText(activity, "1 / 3");
                assertHasText(activity, "Repair");
                assertHasText(activity, "You picked 提 — write 拉.");
                assertNoText(activity, "Write to repair");
                assertNoText(activity, "Repair the shape mix-up");
                assertNoText(activity, "similar-kanji miss · writing repair · practice-only");
            }

            assertSimilarChoiceReviewStored("again");
        }
    }

    @Test
fun testReviewThisNowUsesSimilarChoiceGate() {
        seedSimilarChoiceDashboard();

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, "拉");
            scenario.onActivity { activity -> assertHasText(activity, "Review this now") }

            clickText(scenario, "Review this now");
            scenario.onActivity { activity ->
                assertHasText(activity, "0 / 1");
                assertHasText(activity, SIMILAR_KANJI);
                assertHasText(activity, "Which kanji means ramen radical gap?");
                assertHasText(activity, "拉");
                assertHasText(activity, "提");
                assertNoText(activity, "Name this kanji");
            }
        }
    }

    @Test
fun testFocusDoneScreenAppearsWhenSimilarChoiceIsOutsideTodaysFocus() {
        seedFocusCompleteWithInventorySimilarChoice();

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            scenario.onActivity { activity ->
                assertHasText(activity, "0 / 1");
                assertHasText(activity, "Practice");
                assertHasText(activity, "Today's focus done");
                assertHasText(activity, "Today's focus: 0 of 1 left");
                assertHasText(activity, "Continue all kanji");
                assertNoText(activity, SIMILAR_KANJI);
            }

            LocalStore(context).use { store ->
                var stats = store.studyTaskTimeStats(System.currentTimeMillis())
                assertEquals(0, stats.answeredTasks);
            }
        }
    }

    @Test
fun testNormalPassLogsAnsweredStudyTime() {
        seedDashboard();

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            clickText(scenario, REVEAL);
            clickText(scenario, "Pass");

            LocalStore(context).use { store ->
                var reviewStats = store.reviewStatsSince(0L)
                assertEquals(1, reviewStats.total);
                assertEquals(1, reviewStats.good);
                var timeStats = store.studyTaskTimeStats(System.currentTimeMillis())
                assertEquals(1, timeStats.answeredTasks);
            }
        }
    }

    @Test
fun testDueLearningRepeatIsPracticeOnlyAndDoesNotLogReview() {
        // In the single-scheduler model, a card in NEW_LEARNING phase with a
        // past due_at shows up directly in the study queue. Answering it
        // advances the learning step on the item itself. Learning step answers
        // are logged but do not count as real FSRS reviews.
        seedDashboard();
        LocalStore(context).use { setup ->
            var now = System.currentTimeMillis()
            // Create a study item in NEW_LEARNING phase, step 0, due in the past.
            val item = RecordsStudyModels.StudyItem("拉", "learning", now - 1_000L, 0.4, 5.0, 1, 0, 0, 0, null, now)
                .copyBuilder()
                .answerSignature("拉|拉致|らち|archive example")
                .rung(RecordsBase.LadderRung.KANJI_MEANING)
                .phase(RecordsBase.SchedulerPhase.NEW_LEARNING)
                .build()
            setup.saveStudyItem(item)
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            scenario.onActivity { activity -> assertHasText(activity, REVEAL) }

            clickText(scenario, REVEAL);
            clickText(scenario, "Pass");

            LocalStore(context).use { store ->
                // The learning step answer is logged but the card stays in learning.
                var items = store.studyItems()
                assertFalse(items.isEmpty());
                var updated = items.firstOrNull { it.kanji == "拉" }
                val updatedItem = requireNotNull(updated)
                // After Good on step 0, advances to step 1 (or graduates if only 1 step).
                assertTrue(updatedItem.learningStep >= 1 || updatedItem.phase == RecordsBase.SchedulerPhase.REVIEW)
            }
        }
    }

    @Test
fun testLearningRepeatPassAdvancesSessionProgressHeader() {
        // In the single-scheduler model, learning step cards show up in the
        // normal study queue and passing them advances session progress.
        seedDashboard(Arrays.asList(
                dashboardRow("拉", RAMEN_RADICAL_GAP, "ら", IMPORTED_FROM_SUSPENDED_CARDS),
                dashboardRow("提", "carry radical gap", "てい", IMPORTED_FROM_SUSPENDED_CARDS)
        ));
        LocalStore(context).use { setup ->
            var now = System.currentTimeMillis()
            // Create a study item in NEW_LEARNING phase, due in the past.
            val item = RecordsStudyModels.StudyItem("拉", "learning", now - 1_000L, 0.4, 5.0, 1, 0, 0, 0, null, now)
                .copyBuilder()
                .answerSignature("拉|拉致|らち|archive example")
                .rung(RecordsBase.LadderRung.KANJI_MEANING)
                .phase(RecordsBase.SchedulerPhase.NEW_LEARNING)
                .build()
            setup.saveStudyItem(item)
        }

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            clickText(scenario, REVEAL);
            clickText(scenario, "Pass");

            // Progress header should advance after passing.
            scenario.onActivity { activity -> assertHasText(activity, "1 / ") }
        }
    }

    @Test
fun testMissedRecognitionCountsTowardInternalWritingThreshold() {
        seedDashboard();
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            clickText(scenario, REVEAL);
            clickText(scenario, "Fail");

            LocalStore(context).use { store ->
                var stats = store.reviewStatsSince(0L)
                assertEquals(1, stats.total);
                assertEquals(1, stats.again);
                assertEquals(0, stats.writingRequired);
                var items = store.studyItems()
                assertEquals(1, items.size);
                assertFalse(items.get(0).writingRemediationPending);
                assertEquals(0, items.get(0).recognitionStage);
                assertEquals(0, items.get(0).consecutiveFailedRecognitionDays);
                assertEquals("again", items.get(0).kanjiMeaningMemory.lastRating);
                assertLatestReviewSchedulerStateContains(store, "\"due_at\":" + items.get(0).dueAtMillis);
            }
        }
    }

    @Test
fun testTypingMeaningAutoPassesCorrectAnswerAndAllowsManualWrongGrading() {
        seedDashboard();
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            clickText(scenario, REVEAL);
            clickText(scenario, "Fail");

            forceStudyItemDue("拉", -1, false);
            clickText(scenario, "Back home");
            clickText(scenario, STUDY_NOW);
            scenario.onActivity { activity -> assertHasTexts(activity, "Type the meaning", "Meaning", REVEAL) }
            enterFirstEditText(scenario, "kidnap");
            clickText(scenario, REVEAL);

            assertCorrectTypingMeaningReviewStored();

            forceStudyItemDue("拉", -1, false);
            clickText(scenario, "Back home");
            clickText(scenario, STUDY_NOW);
            enterFirstEditText(scenario, "wrong");
            clickText(scenario, REVEAL);
            scenario.onActivity { activity -> assertHasTexts(activity, "Answer", "Latin, kidnap", "Fail", "Pass") }
            clickText(scenario, "Fail");

            assertWrongTypingMeaningReviewStored();
        }
    }

    @Test
fun testWordReadingRungPromptsRevealsAndLogsTaskType() {
        seedDashboard();
        forceStudyItemDue("拉", 2, false);

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            scenario.onActivity { activity ->
                assertHasTexts(activity, "Read", "Read this word", "What is the reading?", "拉致", REVEAL);
                assertNoTexts(activity, "Reading: らち", "From: 拉致");
            }

            clickText(scenario, REVEAL);
            scenario.onActivity { activity ->
                assertHasTexts(activity, "Answer", "Reading: らち", "From: 拉致", "Fail", "Pass");
                assertNoText(activity, "Latin, kidnap");
            }
            clickText(scenario, "Pass");

            assertWordReadingReviewStored();
        }
    }

    @Test
fun testCorrectWritingCheckSubmitsReview() {
        seedDueWritingItem();
        MainActivityRuntimeOverrides.setWritingRecognizer(FakeWritingRecognizer("拉"));
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            scenario.onActivity { activity -> drawGuideKanji(activity, "拉") }
            clickText(scenario, CHECK);
            waitForText(scenario, CLEAN_MATCH);
            scenario.onActivity { activity ->
                assertHasText(activity, CLEAN_MATCH);
                assertHasText(activity, "Target: 拉");
                assertHasText(activity, PASS_AFTER_WRITING);
                assertNoText(activity, "Fail");
                assertEquals(1, countText(activity.findViewById<View>(android.R.id.content), CLEAN_MATCH));
            }
            clickText(scenario, PASS_AFTER_WRITING);

            LocalStore(context).use { store ->
                var stats = store.reviewStatsSince(0L)
                assertEquals(1, stats.total);
                assertEquals(0, stats.good);
                assertEquals(1, stats.hard);
                assertEquals(1, stats.writingRequired);
                assertEquals(0, stats.writingFailed);
                var items = store.studyItems()
                assertEquals(1, items.size);
                var item = items.get(0)
                assertEquals("拉", item.kanji);
                assertEquals("learning", item.state);
                assertEquals(4, item.totalReviews);
                assertEquals(0, item.learningStep);
                assertEquals(1, item.writingLevel);
                val token = item.activeToken
                assertTrue(token == null || token.isEmpty())
            }
        }
    }

    @Test
fun testHintAssistedCleanWritingHoldsFadeLevel() {
        seedDueWritingItem(2);
        MainActivityRuntimeOverrides.setWritingRecognizer(FakeWritingRecognizer("拉"));
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            clickText(scenario, "More help");
            scenario.onActivity { activity -> drawGuideKanji(activity, "拉") }
            clickText(scenario, CHECK);
            waitForText(scenario, CLEAN_MATCH);
            clickText(scenario, PASS_AFTER_WRITING);

            LocalStore(context).use { store ->
                var stats = store.reviewStatsSince(0L)
                assertEquals(1, stats.total);
                assertEquals(1, stats.hard);
                var items = store.studyItems()
                assertEquals(1, items.size);
                assertEquals(2, items.get(0).writingLevel);
            }
        }
    }

    @Test
fun testMessyRecognizedWritingCanBeSavedHardWithoutAdvancingFade() {
        seedDueWritingItem(2);
        MainActivityRuntimeOverrides.setWritingRecognizer(FakeWritingRecognizer("拉"));
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            scenario.onActivity { activity -> drawGuideKanjiWithFirstStrokeReversed(activity, "拉") }
            clickText(scenario, CHECK);
            waitForText(scenario, "Try cleaner");
            scenario.onActivity { activity ->
                assertHasText(activity, "Try cleaner");
                assertHasText(activity, "Save hard");
                assertHasText(activity, "Mark right anyway");
                assertNoText(activity, PASS_AFTER_WRITING);
            }
            clickText(scenario, "Save hard");

            LocalStore(context).use { store ->
                var stats = store.reviewStatsSince(0L)
                assertEquals(1, stats.total);
                assertEquals(1, stats.hard);
                var items = store.studyItems()
                assertEquals(1, items.size);
                assertEquals(2, items.get(0).writingLevel);
            }
        }
    }

    @Test
fun testWrongRecognitionCanBeLoggedAsFailedAttempt() {
        seedDueWritingItem(2);
        MainActivityRuntimeOverrides.setWritingRecognizer(FakeWritingRecognizer("提"));
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            scenario.onActivity { activity -> drawGuideKanji(activity, "拉") }
            clickText(scenario, CHECK);
            scenario.onActivity { activity ->
                assertHasText(activity, "I could not read that as the target kanji yet");
                assertHasText(activity, "Target: 拉");
                assertHasText(activity, "Fail");
                assertNoText(activity, PASS_AFTER_WRITING);
            }
            clickText(scenario, "Fail");

            LocalStore(context).use { store ->
                var stats = store.reviewStatsSince(0L)
                assertEquals(1, stats.total);
                assertEquals(1, stats.again);
                assertEquals(1, stats.writingRequired);
                assertEquals(1, stats.writingFailed);
                var items = store.studyItems()
                assertEquals(1, items.size);
                assertEquals("learning", items.get(0).state);
                assertEquals(1, items.get(0).writingLevel);
            }
        }
    }

    @Test
fun testTryAgainWithFullGuideStartsFreshAttempt() {
        seedDueWritingItem();
        MainActivityRuntimeOverrides.setWritingRecognizer(FakeWritingRecognizer("提"));
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            scenario.onActivity { activity -> drawGuideKanji(activity, "拉") }
            clickText(scenario, CHECK);
            scenario.onActivity { activity ->
                assertHasText(activity, "Try again with full guide");
                assertHasText(activity, "Replay");
            }
            clickText(scenario, "Try again with full guide");
            scenario.onActivity { activity ->
                assertHasText(activity, "Fresh guided try");
                assertNoText(activity, "I could not read that as the target kanji yet");
                assertNoText(activity, PASS_AFTER_WRITING);
                assertNoText(activity, "Replay");
                val pad: DrawingPadView = requireNotNull(findType(activity.findViewById<View>(android.R.id.content), DrawingPadView::class.java))
                assertNotNull(pad);
                assertFalse(pad.hasInk());
                assertFalse(pad.isReplayOverlayVisible());
            }
        }
    }

    @Test
fun testWrongRecognitionAllowsLoggedManualOverride() {
        seedDueWritingItem();
        MainActivityRuntimeOverrides.setWritingRecognizer(FakeWritingRecognizer("提"));
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            scenario.onActivity { activity -> drawGuideKanji(activity, "拉") }
            clickText(scenario, CHECK);
            scenario.onActivity { activity ->
                assertHasText(activity, "I could not read that as the target kanji yet");
                assertHasText(activity, "Mark right anyway");
            }
            clickText(scenario, "Mark right anyway");

            LocalStore(context).use { store ->
                var stats = store.reviewStatsSince(0L)
                assertEquals(1, stats.total);
                assertEquals(1, stats.hard);
                assertEquals(1, stats.writingRequired);
                assertEquals(0, stats.writingFailed);
            }
        }
    }

    @Test
fun testMissingModelCanBeManuallyScoredAfterDrawing() {
        seedDueWritingItem();
        MainActivityRuntimeOverrides.setWritingRecognizer(FakeUnavailableRecognizer());
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickText(scenario, STUDY_NOW);
            scenario.onActivity { activity -> drawGuideKanji(activity, "拉") }
            clickText(scenario, CHECK);
            scenario.onActivity { activity ->
                assertHasText(activity, "Download the handwriting checker before automatic checks");
                assertHasText(activity, "Target: 拉");
                assertHasText(activity, "Fail");
                assertHasText(activity, "Mark right anyway");
            }
            clickText(scenario, "Mark right anyway");

            LocalStore(context).use { store ->
                var stats = store.reviewStatsSince(0L)
                assertEquals(1, stats.total);
                assertEquals(1, stats.hard);
                assertEquals(1, stats.writingRequired);
                assertEquals(0, stats.writingFailed);
            }
        }
    }

    @Test
fun testDrawingPadTracksInkAndClear() {
        var pad = DrawingPadView(context)
        assertFalse(pad.hasInk());

        var now = System.currentTimeMillis()
        sendTouch(pad, now, now, MotionEvent.ACTION_DOWN, 20f, 20f);
        sendTouch(pad, now, now + 10, MotionEvent.ACTION_MOVE, 80f, 80f);
        sendTouch(pad, now, now + 20, MotionEvent.ACTION_UP, 120f, 120f);

        assertTrue(pad.hasInk());
        pad.clear();
        assertFalse(pad.hasInk());
    }

    @Test
fun testManualSyncButtonRecordsProviderFailureWithoutCrash() {
        Assume.assumeFalse(
                "The opt-in live AnkiDroid run exercises the successful sync button path instead.",
                liveAnkiDroidEnabled()
        );
        MainActivityRuntimeOverrides.setAnkiDroidGateway(AnkiDroidGateway.testProvider(context, "dev.bee.kanjianki.missing_anki"));
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickHomeSyncEntryPoint(scenario);
            clickText(scenario, "Sync cards");
            val status = requireNotNull(waitForLatestSync())
            assertEquals("config_error", status.status)
            assertTrue(status.errorMessage.contains("AnkiDroid"))
        }
    }

    @Test
fun testManualSyncShowsLiveCardProgress() {
    val settings = RecordsSyncModels.Settings.kikuDefaults()
    val first = kikuNote(1L, "確認", "かくにん", "confirmation", "確認した。")
    val second = kikuNote(2L, "笥箱", "しはこ", "rare box", "笥箱を見た。")
    val snapshot = RecordsSyncModels.CollectionSnapshot(
        Arrays.asList(first, second),
        Arrays.asList(
            kikuCard(10L, 1L).history(settings.matureDays + 5, 12, 0).build(),
            kikuCard(20L, 2L).suspended().build(),
        ),
    )
    val progressGateway = HoldingProgressGateway(snapshot)
    MainActivityRuntimeOverrides.setCollectionGateway(progressGateway)

    try {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickHomeSyncEntryPoint(scenario)
            clickText(scenario, "Sync cards")
            waitForText(scenario, "1 / 2 cards scanned")
            scenario.onActivity { activity ->
                assertHasText(activity, "Scanning cards")
                assertHasText(activity, "1 / 2 cards scanned")
            }
            progressGateway.finish()
            waitForText(scenario, "Sync complete")
        }
    } finally {
        progressGateway.finish()
    }
}

    @Test
fun testLastSyncHeadlineInvitesAndStartsManualSync() {
        val note = kikuNote(2L, "同期", "どうき", "sync", "同期する。")
        val snapshot = RecordsSyncModels.CollectionSnapshot(
            Collections.singletonList(note),
            Collections.singletonList(kikuCard(20L, 2L).suspended().build())
        )
        val progressGateway = HoldingProgressGateway(snapshot)
        progressGateway.finish()
        MainActivityRuntimeOverrides.setCollectionGateway(progressGateway)
        val yesterday = moveLocalDays(localDayStart(System.currentTimeMillis()), -1) + 10 * 60 * 60 * 1000L
        saveSyncFinishedAt(yesterday)
        val syncValue = "Yesterday at " + DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(yesterday))

        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertHasText(activity, "Sync");
                assertHasText(activity, syncValue);
                assertNoText(activity, "active cards checked");
                assertNoText(activity, "suspended cards archived");
                assertNoText(activity, "Study starts with recall");
                assertNoText(activity, "Sync once to find");
            }
            var syncStartedAt = System.currentTimeMillis() - 1L
            clickText(scenario, "Sync");
            clickText(scenario, "Sync cards");
            val status = requireNotNull(waitForLatestSyncAfter(syncStartedAt))
            assertEquals("success", status.status)
            assertTrue(status.finishedAt >= syncStartedAt)
            waitForText(scenario, "Sync complete");
        }
    }

    @Test
fun testManualSyncButtonEnablesDailyAutoSyncAfterSuccess() {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY));
        context.getContentResolver().call(Uri.parse("content://" + FakeAnkiDroidProvider.AUTHORITY), "reset", null, null);
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickHomeSyncEntryPoint(scenario);
            clickText(scenario, "Sync cards");
            val status = requireNotNull(waitForLatestSync())
            assertEquals("success", status.status)

            waitForText(scenario, "Sync complete");
            scenario.onActivity { activity ->
                assertHasText(activity, "Today's adaptive focus");
                assertNoText(activity, "new per day");
                assertNoText(activity, "AnkiDroid is ready");
            }

            LocalStore(context).use { store ->
                var auto = store.autoSyncSettings()
                assertTrue(auto.configured);
                assertTrue(auto.enabled);
                assertTrue(auto.nextRunAt > System.currentTimeMillis());
            }
        }
    }

    @Test
fun testManualSyncButtonWorksAgainstLiveAnkiDroid() {
        Assume.assumeTrue("Live AnkiDroid fixture is opt-in.", liveAnkiDroidEnabled());
        MainActivityRuntimeOverrides.setAnkiDroidGateway(null);
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            clickHomeSyncEntryPoint(scenario);
            clickText(scenario, "Sync cards");
            waitForText(scenario, "Sync complete", 300_000L);
            val status = requireNotNull(waitForLatestSync())
            assertEquals("success", status.status)


            LocalStore(context).use { store ->
                assertFalse(store.dashboardRows().isEmpty());
                assertFalse(store.studyItems().isEmpty());
            }
        }
    }

fun waitForLatestSync(): LocalStoreBase.SyncStatus? {
    return waitForLatestSync(200)
}

private fun waitForLatestSyncAfter(finishedAt: Long): LocalStoreBase.SyncStatus? {
    for (i in 0 until 200) {
        try {
            LocalStore(context).use { store ->
                val status = store.latestSync()
                if (status != null && status.finishedAt >= finishedAt) {
                    return status
                }
            }
        } catch (busy: SQLiteDatabaseLockedException) {
            // The sync button tests poll while the app is committing a collection import.
        }
        SystemClock.sleep(100)
    }
    return null
}

private fun waitForLatestSync(attempts: Int): LocalStoreBase.SyncStatus? {
    for (i in 0 until attempts) {
        try {
            LocalStore(context).use { store ->
                val status = store.latestSync()
                if (status != null) {
                    return status
                }
            }
        } catch (busy: SQLiteDatabaseLockedException) {
            // The live sync button test polls while the app is committing a large collection import.
        }
        SystemClock.sleep(100)
    }
    return null
}

fun liveAnkiDroidEnabled(): Boolean {
    return "true" == InstrumentationRegistry.getArguments().getString(LIVE_ARG)
}

fun seedDashboard() {
    seedDashboard(listOf(dashboardRow("拉", RAMEN_RADICAL_GAP, "ら", IMPORTED_FROM_SUSPENDED_CARDS)))
}

fun seedDueWritingItem() {
    seedDueWritingItem(0)
}

fun seedDueWritingItem(writingLevel: Int) {
    seedDueWritingItem(dashboardRow("拉", RAMEN_RADICAL_GAP, "ら", IMPORTED_FROM_SUSPENDED_CARDS), writingLevel)
}

fun seedDueWritingItem(row: RecordsImportModels.DashboardRow, writingLevel: Int) {
    seedDashboard(listOf(row))
    LocalStore(context).use { store ->
        store.replaceStudyItems(
            listOf(
                RecordsStudyModels.StudyItem(
                    row.kanji,
                    "learning",
                    0L,
                    0.9,
                    5.0,
                    3,
                    2,
                    0,
                    writingLevel,
                    0,
                    3,
                    localDayStart(System.currentTimeMillis()),
                    true,
                    null,
                    0L,
                ),
            ),
        )
    }
}

fun seedDashboard(rows: List<RecordsImportModels.DashboardRow>) {
    seedDashboardRowsOnly(rows)
    LocalStore(context).use { store ->
        val now = System.currentTimeMillis()
        val items = arrayListOf<RecordsStudyModels.StudyItem>()
        for (row in rows) {
            items.add(RecordsStudyModels.StudyItem(row.kanji, "new", now, 0.4, 5.0, 0, 0, 0, 0, null, now))
        }
        store.replaceStudyItems(items)
    }
}

fun seedSimilarChoiceDashboard() {
    val settings = RecordsSyncModels.Settings.kikuDefaults()
    val row = dashboardRow("拉", RAMEN_RADICAL_GAP, "ら", IMPORTED_FROM_SUSPENDED_CARDS)
    val snapshot = RecordsSyncModels.CollectionSnapshot(
        listOf(
            kikuNote(1L, "拉麺", "らーめん", RAMEN_RADICAL_GAP, "拉麺を食べた。"),
            kikuNote(2L, "提案", "ていあん", "carry radical gap", "提案を見た。"),
        ),
        listOf(
            kikuCard(10L, 1L).build(),
            kikuCard(20L, 2L).history(30, 4, 0).build(),
        ),
    )
    val index = SimilarKanjiIndex.parseTsv(StringReader("拉\t提\tfixture\n"))
    LocalStore(context).use { store ->
        val now = System.currentTimeMillis()
        store.saveSuccessfulSync(
            snapshot,
            Collections.emptyList(),
            listOf(row),
            settings,
            LocalStoreBase.SyncTiming(maxOf(0L, now - 1_000L), now),
            null,
            index,
        )
        store.replaceStudyItems(
            listOf(
                RecordsStudyModels.StudyItem("拉", "new", now, 0.4, 5.0, 0, 0, 0, 0, null, now)
                    .withRungAndPhase(RecordsBase.LadderRung.SIMILAR_KANJI, RecordsBase.SchedulerPhase.NEW_LEARNING)
                    .withHasSimilarKanji(true),
            ),
        )
    }
}

fun seedFocusCompleteWithInventorySimilarChoice() {
    val settings = RecordsSyncModels.Settings.kikuDefaults()
    val activeRow = dashboardRow("拉", RAMEN_RADICAL_GAP, "ら", IMPORTED_FROM_SUSPENDED_CARDS)
    val snapshot = RecordsSyncModels.CollectionSnapshot(
        listOf(
            kikuNote(1L, "拉麺", "らーめん", RAMEN_RADICAL_GAP, "拉麺を食べた。"),
            kikuNote(2L, "提案", "ていあん", "carry radical gap", "提案を見た。"),
            kikuNote(3L, "謎語", "なぞご", "riddle radical gap", "謎語を見た。"),
        ),
        listOf(
            kikuCard(10L, 1L).build(),
            kikuCard(20L, 2L).history(30, 4, 0).build(),
            kikuCard(30L, 3L).history(30, 4, 0).build(),
        ),
    )
    val index = SimilarKanjiIndex.parseTsv(StringReader("提\t謎\tfixture\n"))
    LocalStore(context).use { store ->
        val now = System.currentTimeMillis()
        store.saveSuccessfulSync(
            snapshot,
            Collections.emptyList(),
            listOf(activeRow),
            settings,
            LocalStoreBase.SyncTiming(maxOf(0L, now - 1_000L), now),
            null,
            index,
        )
        store.replaceStudyItems(
            listOf(
                RecordsStudyModels.StudyItem("拉", "review", now + 86_400_000L, 2.0, 4.0, 1, 0, 2, 0, null, now - 86_400_000L),
            ),
        )
        store.saveReview(review("拉", "focus-complete"), "good", now)
    }
}

private fun countSimilarRepairs(store: LocalStore): Int {
    store.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM similar_kanji_repair_queue", null).use { cursor ->
        assertTrue(cursor.moveToFirst())
        return cursor.getInt(0)
    }
}

private fun assertLatestReviewSchedulerStateContains(store: LocalStore, expected: String) {
    store.getReadableDatabase().rawQuery(
        "SELECT scheduler_state_after_json FROM review_log ORDER BY id DESC LIMIT 1",
        null,
    ).use { cursor ->
        assertTrue(cursor.moveToFirst())
        assertTrue(cursor.getString(0).contains(expected))
    }
}

fun forceStudyItemDue(kanji: String, recognitionStage: Int, writingRemediationPending: Boolean) {
    LocalStore(context).use { store ->
        var item: RecordsStudyModels.StudyItem? = null
        for (candidate in store.studyItems()) {
            if (kanji == candidate.kanji) {
                item = candidate
                break
            }
        }
        val studyItem = requireNotNull(item)
        val rung = rungForLegacyStage(recognitionStage, writingRemediationPending)
        val dueTaskMemory = studyItem.memoryForRung(rung).withDueAtMillis(0L)
        val builder = studyItem.copyBuilder()
            .state("review")
            .dueAtMillis(0L)
            .recognitionStage(recognitionStage)
            .writingRemediationPending(writingRemediationPending)
            .rung(rung)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .answerSignature("")
            .activeToken(null)
        when (rung) {
            RecordsBase.LadderRung.WRITE_KANJI -> builder.writingRemediationMemory(dueTaskMemory)
            RecordsBase.LadderRung.TYPE_MEANING -> builder.typingMeaningMemory(dueTaskMemory)
            RecordsBase.LadderRung.FONT_MEANING -> builder.fontMeaningMemory(dueTaskMemory)
            RecordsBase.LadderRung.WORD_READING -> builder.wordReadingMemory(dueTaskMemory)
            RecordsBase.LadderRung.KANJI_MEANING,
            RecordsBase.LadderRung.SIMILAR_KANJI,
            RecordsBase.LadderRung.MEANING_KANJI -> builder.kanjiMeaningMemory(dueTaskMemory)
        }
        store.replaceStudyItems(listOf(builder.build()))
    }
}

fun rungForLegacyStage(recognitionStage: Int, writingRemediationPending: Boolean): RecordsBase.LadderRung {
    if (writingRemediationPending) {
        return RecordsBase.LadderRung.WRITE_KANJI
    }
    return when (recognitionStage.coerceIn(-1, 2)) {
        -1 -> RecordsBase.LadderRung.TYPE_MEANING
        1 -> RecordsBase.LadderRung.FONT_MEANING
        2 -> RecordsBase.LadderRung.WORD_READING
        else -> RecordsBase.LadderRung.KANJI_MEANING
    }
}

fun seedDashboardRowsOnly(rows: List<RecordsImportModels.DashboardRow>) {
    saveSyncFinishedAt(2000L, rows)
}

fun saveSyncFinishedAt(finishedAt: Long) {
    saveSyncFinishedAt(finishedAt, listOf(dashboardRow("拉", RAMEN_RADICAL_GAP, "ら", IMPORTED_FROM_SUSPENDED_CARDS)))
}

fun saveSyncFinishedAt(finishedAt: Long, rows: List<RecordsImportModels.DashboardRow>) {
    val settings = RecordsSyncModels.Settings.kikuDefaults()
    val note = kikuNote(1L, "拉麺", "らーめん", RAMEN_RADICAL_GAP, "拉麺を食べた。")
    val card = kikuCard(10L, 1L).build()
    LocalStore(context).use { store ->
        store.saveSuccessfulSync(
            RecordsSyncModels.CollectionSnapshot(Collections.singletonList(note), Collections.singletonList(card)),
            Collections.emptyList(),
            ArrayList(rows),
            settings,
            Math.max(0L, finishedAt - 1_000L),
            finishedAt,
            null,
        )
    }
}


fun dashboardRow(kanji: String, meaning: String, reading: String, reasonText: String): RecordsImportModels.DashboardRow {
        return dashboardRow(kanji, meaning, reading, reasonText, 0);
    }

fun dashboardRow(kanji: String, meaning: String, reading: String, reasonText: String, matureSupportCount: Int): RecordsImportModels.DashboardRow {
    val active = RecordsImportModels.Example(
        "active",
        10L,
        1L,
        if (kanji == "拉") "拉麺" else kanji + "語",
        if (kanji == "拉") "らーめん" else reading,
        meaning,
        kanji + "を見た。",
        false,
        1,
    )
    val suspended = RecordsImportModels.Example(
        "suspended",
        20L,
        2L,
        if (kanji == "拉") "拉致" else kanji + "例",
        if (kanji == "拉") "らち" else reading,
        "archive example",
        kanji + "を練習した。",
        false,
        0,
    )
    return RecordsImportModels.DashboardRow(
        kanji,
        3401,
        meaning,
        reading,
        "deck:Kiku $kanji",
        if (kanji == "拉") 88 else 42,
        "suspended_archive",
        reasonText,
        1,
        1,
        matureSupportCount,
        Arrays.asList(active, suspended),
    )
}

class HoldingProgressGateway(
    private val snapshot: RecordsSyncModels.CollectionSnapshot,
) : CollectionGateway {
    private val released = CompletableFuture<Void>()

    fun finish() {
        released.complete(null)
    }

    override fun readCollection(settings: RecordsSyncModels.Settings): RecordsSyncModels.CollectionSnapshot {
        return snapshot
    }

    override fun readCollection(
        settings: RecordsSyncModels.Settings,
        progress: SyncProgress.Listener?,
    ): RecordsSyncModels.CollectionSnapshot {
        progress?.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.FINDING_NOTE_TYPE))
        progress?.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.READING_NOTES))
        progress?.onSyncProgress(SyncProgress.cardsScanned(0, snapshot.cards.size))
        progress?.onSyncProgress(SyncProgress.cardsScanned(1, snapshot.cards.size))
        try {
            released.get(5L, TimeUnit.SECONDS)
        } catch (_: Exception) {
            // Continue when the UI release signal is not needed for this fake gateway path.
        }
        progress?.onSyncProgress(SyncProgress.cardsScanned(snapshot.cards.size, snapshot.cards.size))
        return snapshot
    }

    override fun removeArchivedSuspendedCards(snapshot: RecordsSyncModels.CollectionSnapshot): AnkiDroidGateway.RemovalSummary {
        return AnkiDroidGateway.RemovalSummary(0, 0, 0, "cleanup done")
    }

    override fun removeArchivedSuspendedCards(
        snapshot: RecordsSyncModels.CollectionSnapshot,
        progress: SyncProgress.Listener?,
    ): AnkiDroidGateway.RemovalSummary {
        progress?.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.ARCHIVING_IMPORTED_CARDS))
        return removeArchivedSuspendedCards(snapshot)
    }
}

private fun clickHomeSyncEntryPoint(scenario: ActivityScenario<MainActivity>) {
    if (clickTextInActivityIfPresent(scenario, "Sync AnkiDroid")) {
        return
    }
    clickText(scenario, "Sync")
}

private fun clickTextInActivityIfPresent(scenario: ActivityScenario<MainActivity>, text: String): Boolean {
    var clicked = false
    scenario.onActivity { activity ->
        val view = findExactText(activity.findViewById<View>(android.R.id.content), text)
        if (view == null) {
            return@onActivity
        }
        val clickable = requireNotNull(clickableAncestor(view)) { "Text is not clickable: $text" }
        assertTrue(clickable.performClick())
        clicked = true
    }
    if (clicked) {
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle(2000L)
    }
    return clicked
}

private fun clickText(scenario: ActivityScenario<MainActivity>, text: String) {
    if (clickTextInActivityIfPresent(scenario, text)) {
        return
    }
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    var object2 = findDeviceText(device, text)
    if (object2 == null && text != "Allow") {
        var allow = device.wait(Until.findObject(By.res("com.android.permissioncontroller:id/permission_allow_button")), 1000L)
        if (allow == null) {
            allow = device.wait(Until.findObject(By.text("Allow")), 1000L)
        }
        if (allow != null) {
            allow.click()
            device.waitForIdle(2000L)
            object2 = findDeviceText(device, text)
        }
    }
    val found = requireNotNull(object2) { "Missing text: $text\nDevice text: ${deviceVisibleText(device)}" }
    found.click()
    device.waitForIdle(2000L)
}

fun clickTextIfPresent(text: String) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val object2 = findDeviceText(device, text)
    if (object2 != null) {
        object2.click()
        device.waitForIdle(2000L)
    }
}

private fun recognitionCardHeight(scenario: ActivityScenario<MainActivity>): Int {
    return waitForFlashcardBounds(scenario).height()
}

private fun flashcardBounds(activity: MainActivity): Rect {
    return requireNotNull(activity.flashcardGestureBounds) { "Missing flashcard bounds" }.let { Rect(it) }
}

private fun waitForFlashcardBounds(scenario: ActivityScenario<MainActivity>, timeoutMillis: Long = 5000L): Rect {
    val deadline = SystemClock.uptimeMillis() + timeoutMillis
    var bounds: Rect? = null
    while (SystemClock.uptimeMillis() < deadline) {
        scenario.onActivity { activity ->
            bounds = activity.flashcardGestureBounds?.let { Rect(it) }
        }
        if (bounds != null) {
            return requireNotNull(bounds)
        }
        SystemClock.sleep(100L)
    }
    scenario.onActivity { activity ->
        bounds = activity.flashcardGestureBounds?.let { Rect(it) }
    }
    return requireNotNull(bounds) { "Missing flashcard bounds\nActivity may not have rendered the study card yet" }
}

private fun recognitionCard(activity: MainActivity): View {
    val root = activity.findViewById<View>(android.R.id.content)
    var title = findExactText(root, "Name this kanji")
    if (title == null) {
        title = findExactText(root, "Type the meaning")
    }
    if (title == null) {
        title = findExactText(root, "Read this word")
    }
    val nonNullTitle = requireNotNull(title) { "Missing recognition card title" }
    val parent = requireNotNull(nonNullTitle.parent as? View) { "Recognition title parent should be the card" }
    return parent
}

private fun swipeRecognitionCard(scenario: ActivityScenario<MainActivity>, right: Boolean) {
    val bounds = waitForFlashcardBounds(scenario)
    scenario.onActivity { activity ->
        val inset = maxOf(24f, bounds.width() * 0.18f)
        val startX = if (right) bounds.left + inset else bounds.right - inset
        val endX = if (right) bounds.right - inset else bounds.left + inset
        val y = bounds.centerY().toFloat()
        activity.flashcardTouchStartX = startX
        activity.flashcardTouchStartY = y
        val downTime = SystemClock.uptimeMillis()
        assertTrue(activity.handleFlashcardRelease(MotionEvent.obtain(downTime, downTime + 32L, MotionEvent.ACTION_UP, endX, y, 0)))
    }
    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle(2000L)
}

private fun swipeRecognitionCardFromAnswerPanel(scenario: ActivityScenario<MainActivity>, right: Boolean) {
    val cardBounds = waitForFlashcardBounds(scenario)
    assertTrue("Recognition card should be visible", cardBounds.width() > 0 && cardBounds.height() > 0)
    scenario.onActivity { activity ->
        val answerBounds = revealedAnswerPanel(activity)
        val startInset = maxOf(24f, answerBounds.width() * 0.18f)
        val startX = if (right) answerBounds.left + startInset else answerBounds.right - startInset
        val endInset = maxOf(48f, cardBounds.width() * 0.18f)
        val endX = if (right) cardBounds.right + endInset else cardBounds.left - endInset
        val y = answerBounds.centerY().toFloat()
        assertTrue("Swipe should start inside the answer panel", answerBounds.contains(startX.toInt(), y.toInt()))
        assertTrue("Answer panel should be inside the flashcard bounds", cardBounds.contains(answerBounds.centerX(), answerBounds.centerY()))
        assertFalse("Swipe should finish outside the answer panel", answerBounds.contains(endX.toInt(), y.toInt()))

        activity.flashcardTouchStartX = startX
        activity.flashcardTouchStartY = y
        val downTime = SystemClock.uptimeMillis()
        assertTrue(activity.handleFlashcardRelease(MotionEvent.obtain(downTime, downTime + 32L, MotionEvent.ACTION_UP, endX, y, 0)))
    }
    UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle(2000L)
}

private fun revealedAnswerPanel(activity: MainActivity): Rect {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val answer = findDeviceTextNow(device, "Answer") ?: findDeviceTextNow(device, "Reference")
    val answerObject = requireNotNull(answer) { "Missing revealed answer panel\nDevice text: ${deviceVisibleText(device)}" }
    return answerObject.getVisibleBounds()
}

private fun waitForRevealedAnswerPanel(scenario: ActivityScenario<MainActivity>, timeoutMillis: Long = 5000L): Rect {
    val deadline = SystemClock.uptimeMillis() + timeoutMillis
    var panel: Rect? = null
    while (SystemClock.uptimeMillis() < deadline) {
        scenario.onActivity { activity ->
            panel = runCatching { revealedAnswerPanel(activity) }.getOrNull()
        }
        if (panel != null) {
            return requireNotNull(panel)
        }
        SystemClock.sleep(100L)
    }
    scenario.onActivity { activity ->
        panel = revealedAnswerPanel(activity)
    }
    return requireNotNull(panel)
}

private fun enterFirstEditText(scenario: ActivityScenario<MainActivity>, text: String) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val composeInput = device.wait(Until.findObject(By.clazz(EditText::class.java.name)), 1000L)
    if (composeInput != null) {
        composeInput.setText(text)
        device.waitForIdle(2000L)
        return
    }
    scenario.onActivity { activity ->
        val input = requireNotNull(findType(activity.findViewById<View>(android.R.id.content), EditText::class.java))
        input.setText(text)
        input.setSelection(text.length)
    }
    device.waitForIdle(2000L)
}

fun enterDialogEditText(text: String) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val input = requireNotNull(device.wait(Until.findObject(By.clazz(EditText::class.java.name)), 3000L))
    input.setText(text)
    device.waitForIdle(2000L)
}

fun findDeviceText(device: UiDevice, text: String): UiObject2? {
    var object2 = device.wait(Until.findObject(By.text(text)), 3000L)
    if (object2 == null) {
        object2 = device.wait(Until.findObject(By.text(text.uppercase(Locale.ROOT))), 3000L)
    }
    if (object2 == null) {
        object2 = device.wait(Until.findObject(By.textContains(text)), 3000L)
    }
    if (object2 == null) {
        object2 = device.wait(Until.findObject(By.textContains(text.uppercase(Locale.ROOT))), 3000L)
    }
    return object2
}

fun findDeviceTextNow(device: UiDevice, text: String): UiObject2? {
    val pkg = appPackage()
    var object2 = device.findObjects(By.pkg(pkg).text(text)).firstOrNull()
    if (object2 == null) {
        object2 = device.findObjects(By.pkg(pkg).text(text.uppercase(Locale.ROOT))).firstOrNull()
    }
    if (object2 == null) {
        object2 = device.findObjects(By.pkg(pkg).textContains(text)).firstOrNull()
    }
    if (object2 == null) {
        object2 = device.findObjects(By.pkg(pkg).textContains(text.uppercase(Locale.ROOT))).firstOrNull()
    }
    return object2
}

fun appPackage(): String {
    return InstrumentationRegistry.getInstrumentation().targetContext.packageName
}

fun firstMatch(objects: List<UiObject2>): UiObject2? {
    return if (objects.isEmpty()) null else objects[0]
}

fun deviceVisibleText(device: UiDevice): String {
    val texts = ArrayList<String>()
    for (object2 in device.findObjects(By.clazz(TextView::class.java.name))) {
        val text = object2.text
        if (text != null && text.isNotBlank()) {
            texts.add(text)
        }
    }
    return texts.toString()
}

private fun waitForText(scenario: ActivityScenario<MainActivity>, text: String) {
    waitForText(scenario, text, 5000L)
}

private fun waitForText(scenario: ActivityScenario<MainActivity>, text: String, timeoutMillis: Long) {
    val deadline = SystemClock.uptimeMillis() + timeoutMillis
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    while (SystemClock.uptimeMillis() < deadline) {
        var found = false
        scenario.onActivity { activity -> found = hasText(activity, text, device) }
        if (found) {
            return
        }
        SystemClock.sleep(100L)
    }
    scenario.onActivity { activity -> assertHasText(activity, text) }
}
private fun assertCollapsedSettingsScreen(activity: MainActivity) {
        assertHasTexts(
                activity,
                "Import & sync",
                "Deck options",
                "Reminders & updates",
                "Display & data",
                "Note type",
                "Using Kiku",
                "Expression field",
                "Reading field",
                "Meaning field",
                "Frequency sort field",
                SettingsTextCopy.chooseFromAnkiDroidLabel(),
                "Save note type",
                "Import filters",
                "Active cards",
                "Suspended cards",
                "Tagged cards",
                "Weak cards",
                "Minimum matching cards per kanji",
                "Suspended card range",
                "Default: 100-3000",
                "Min rank",
                "Max rank"
        );
        assertImportFilterDefaultState();
        assertNoTexts(activity, "Daily workload", "Daily reminder", "App updates");
    }

fun assertImportFilterDefaultState() {
        assertComposeCheckBox(SettingsTextCopy.activeCardsLabel(), false);
        assertComposeCheckBox(SettingsTextCopy.suspendedCardsLabel(), true);
        assertComposeCheckBox(SettingsTextCopy.taggedCardsLabel(), false);
        assertComposeCheckBox(SettingsTextCopy.weakCardsLabel(), false);
        assertComposeCheckBox(SettingsTextCopy.browserQueryLabel(), false);
    }

fun assertNavigationSettingsPersisted() {
        LocalStore(context).use { store ->
            assertEquals(AdaptiveLoadPlanner.MODE_MANUAL, store.adaptiveLoadMode());
            assertEquals(100, store.adaptiveLoadWorkPercent());
            assertEquals(AdaptiveLoadPlanner.MAX_MAX_ITEMS, store.adaptiveLoadMaxItems());
            assertEquals(0.95, store.schedulerParameters().targetRetention, 0.001);
            assertTrue(store.schedulerParameters().frequencyRetentionEnabled);
            assertEquals("1-500=95%\n501-20000=85%", store.schedulerParameters().frequencyRetentionRanges);
            assertEquals(Arrays.asList(2, 15), store.learningStepSettings().newStepsMinutes);
            assertEquals(Arrays.asList(5, 20), store.learningStepSettings().reviewStepsMinutes);
            assertEquals(45, store.studyAheadMinutes());
            assertEquals(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK, store.getStringSetting(
                    SyncSettings.NEW_CARD_SORT_MODE_SETTING_KEY,
                    RecordsBase.NEW_CARD_SORT_FREQUENCY
            ));
            assertEquals(30, store.getIntSetting(SyncSettings.LADDER_PROMOTION_INTERVAL_DAYS_SETTING_KEY, 0));
            assertEquals(2, store.getIntSetting(SyncSettings.LADDER_DEMOTION_FAIL_STREAK_SETTING_KEY, 0));
            assertEquals(2, store.getIntSetting(SyncSettings.WRITING_TRIGGER_MISS_DAYS_SETTING_KEY, 0));
            assertEquals(2, store.getIntSetting(SyncSettings.REAL_DUE_REVIEWS_TO_MOVE_SETTING_KEY, 0));
            assertEquals(250, store.getIntSetting("suspended_rank_min", 100));
            assertEquals(3500, store.getIntSetting("suspended_rank_max", 3000));
            assertEquals(0, store.getIntSetting(SyncSettings.IMPORT_ACTIVE_CARDS_SETTING_KEY, 1));
            assertEquals(1, store.getIntSetting(SyncSettings.IMPORT_SUSPENDED_CARDS_SETTING_KEY, 0));
            assertEquals(1, store.getIntSetting(SyncSettings.IMPORT_MIN_MATCHING_CARDS_SETTING_KEY, 0));
            var reminder = store.reminderSettings()
            assertTrue(reminder.enabled);
            assertEquals(8, reminder.hour);
            assertEquals(0, reminder.minute);
        }
    }

fun assertDailySyncEnabled(expectedEnabled: Boolean) {
        LocalStore(context).use { store ->
            var auto = store.autoSyncSettings()
            assertTrue(auto.configured);
            assertEquals(expectedEnabled, auto.enabled);
        }
    }

fun assertReminderSettings(enabled: Boolean, hour: Int, minute: Int) {
        LocalStore(context).use { store ->
            var reminder = store.reminderSettings()
            assertEquals(enabled, reminder.enabled);
            assertEquals(hour, reminder.hour);
            assertEquals(minute, reminder.minute);
        }
    }

fun assertDefaultImportSettingsStillStored() {
        LocalStore(context).use { store ->
            var saved = SyncSettings.fromStore(store)
            var defaults = RecordsSyncModels.Settings.kikuDefaults()
            assertEquals(defaults.importActiveCards, saved.importActiveCards);
            assertEquals(defaults.importSuspendedCards, saved.importSuspendedCards);
            assertEquals(defaults.importTaggedCards, saved.importTaggedCards);
            assertEquals(defaults.importWeakCards, saved.importWeakCards);
            assertEquals(defaults.importBrowserQueryCards, saved.importBrowserQueryCards);
            assertEquals(defaults.importTags, saved.importTags);
            assertEquals(defaults.importWeakFsrsDifficultyThreshold, saved.importWeakFsrsDifficultyThreshold, 0.001);
            assertEquals(defaults.importWeakLapsesThreshold, saved.importWeakLapsesThreshold);
            assertEquals(defaults.importMinMatchingCardsPerKanji, saved.importMinMatchingCardsPerKanji);
            assertEquals(defaults.importBrowserQuery, saved.importBrowserQuery);
        }
    }

fun assertCustomImportSettingsStored() {
        var saved = storedSettings()
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
    }

fun assertLeechImportPresetStored() {
        var saved = storedSettings()
        assertFalse(saved.importActiveCards);
        assertFalse(saved.importSuspendedCards);
        assertTrue(saved.importTaggedCardsEnabled());
        assertEquals(Collections.singletonList("leech"), saved.importTags);
        assertFalse(saved.browserQueryImportEnabled());
    }

fun assertMiningDeckPresetStored() {
        var saved = storedSettings()
        assertFalse(saved.importActiveCards);
        assertFalse(saved.importSuspendedCards);
        assertFalse(saved.importTaggedCardsEnabled());
        assertTrue(saved.browserQueryImportEnabled());
        assertEquals("deck:Mining", saved.importBrowserQuery);
    }

fun assertFrequencyRangeStored(minRank: Int, maxRank: Int) {
        var saved = storedSettings()
        assertEquals(minRank, saved.suspendedRankMin);
        assertEquals(maxRank, saved.suspendedRankMax);
    }

fun storedSettings(): RecordsSyncModels.Settings {
        LocalStore(context).use { store ->
            return SyncSettings.fromStore(store);
        }
    }

private fun assertKanjiDetailReady(activity: MainActivity) {
        assertHasTexts(
                activity,
                IMPORTED_FROM_SUSPENDED_CARDS,
                "Review this now",
                "Copy Anki search",
                "Recovery timeline",
                "Active repair",
                "Mature support 0 / target 2",
                "Kani started watching"
        );
    }

private fun assertHiddenRecognitionCard(activity: MainActivity) {
        assertHasTexts(activity, "Name this kanji", RECOGNISE, "Answer hidden until reveal", RECOGNITION_QUESTION, REVEAL);
        assertNoTexts(activity, "Example: 拉麺  らーめん", "From: 拉麺");
    }

private fun assertRevealedRecognitionCard(activity: MainActivity) {
        assertHasTexts(activity, "Answer", "Latin, kidnap", "Reading: らーめん", "From: 拉麺", "Fail", "Pass");
        assertNoTexts(activity, CHECK);
    }

fun assertFailedRecognitionReviewStored() {
        LocalStore(context).use { store ->
            var stats = store.reviewStatsSince(0L)
            assertEquals(1, stats.total);
            assertEquals(1, stats.again);
            assertEquals(0, stats.writingRequired);
            var items = store.studyItems()
            assertEquals(1, items.size);
            var item = items.get(0)
            assertEquals(1, item.kanjiMeaningMemory.totalReviews);
            assertEquals("again", item.kanjiMeaningMemory.lastRating);
            assertEquals(0, item.consecutiveFailedRecognitionDays);
            assertEquals(0, item.realAgainStreak);
            assertFalse(item.writingRemediationPending);
        }
    }

fun assertKnownAnswerRecognitionReviewStored() {
        LocalStore(context).use { store ->
            var stats = store.reviewStatsSince(0L)
            assertEquals(1, stats.total);
            assertEquals(1, stats.good);
            assertEquals(0, stats.writingRequired);
            var item = onlyStudyItem(store)
            assertEquals("拉", item.kanji);
            assertEquals("learning", item.state);
            assertEquals(1, item.totalReviews);
            assertEquals(1, item.learningStep);
            assertEquals(0, item.writingLevel);
            assertEquals(0, item.recognitionStage);
            assertEquals(1, item.kanjiMeaningMemory.totalReviews);
            assertEquals("good", item.kanjiMeaningMemory.lastRating);
            assertEquals(0, item.realPassStreak);
        }
    }

fun assertWordReadingReviewStored() {
        LocalStore(context).use { store ->
            var stats = store.reviewStatsSince(0L)
            assertEquals(1, stats.total);
            assertEquals(1, stats.good);
            assertEquals(0, stats.writingRequired);
            var item = onlyStudyItem(store)
            assertEquals("拉", item.kanji);
            assertEquals(RecordsBase.LadderRung.WORD_READING, item.rung);
            assertEquals(1, item.wordReadingMemory.totalReviews);
            assertEquals("good", item.wordReadingMemory.lastRating);
            assertLatestLoggedTaskTypes(store, BridgeScheduler.TASK_WORD_READING);
        }
    }

private fun assertSimilarChoiceCard(activity: MainActivity, progress: String, prompt: String, firstChoice: String, secondChoice: String) {
        assertHasTexts(activity, progress, SIMILAR_KANJI, prompt, firstChoice, secondChoice);
        assertNoTexts(activity, "Kanji -> meaning");
    }

fun assertSimilarChoiceReviewStored(expectedRating: String) {
        LocalStore(context).use { store ->
            var stats = store.reviewStatsSince(0L)
            assertEquals(1, stats.total);
            if ("again".equals(expectedRating)) {
                assertEquals(1, stats.again);
            } else {
                assertEquals(1, stats.good);
            }
            assertEquals(if ("again" == expectedRating) 2 else 0, countSimilarRepairs(store))
            var item = onlyStudyItem(store)
            assertEquals(1, item.similarKanjiMemory.totalReviews);
        }
    }

fun assertCorrectTypingMeaningReviewStored() {
        LocalStore(context).use { store ->
            var stats = store.reviewStatsSince(0L)
            assertEquals(2, stats.total);
            assertEquals(1, stats.good);
            var item = onlyStudyItem(store)
            assertEquals(-1, item.recognitionStage);
            assertEquals(RecordsBase.LadderRung.TYPE_MEANING, item.rung);
            assertEquals(1, item.typingMeaningMemory.totalReviews);
            assertEquals("good", item.typingMeaningMemory.lastRating);
            assertLatestLoggedTaskTypes(store, BridgeScheduler.TASK_TYPE_MEANING);
        }
    }

fun assertWrongTypingMeaningReviewStored() {
        LocalStore(context).use { store ->
            var stats = store.reviewStatsSince(0L)
            assertEquals(3, stats.total);
            assertEquals(2, stats.again);
            var item = onlyStudyItem(store)
            assertEquals(-1, item.recognitionStage);
            assertFalse(item.writingRemediationPending);
            assertEquals(1, item.consecutiveFailedRecognitionDays);
            assertLatestLoggedTaskTypes(store, BridgeScheduler.TASK_TYPE_MEANING);
        }
    }

private fun assertLatestLoggedTaskTypes(store: LocalStore, expectedTaskType: String) {
        assertEquals(
                expectedTaskType,
                scalarString(store, "SELECT task_type FROM review_log ORDER BY id DESC LIMIT 1")
        );
        assertEquals(
                expectedTaskType,
                scalarString(store, "SELECT task_type FROM study_task_log ORDER BY id DESC LIMIT 1")
        );
    }

private fun scalarString(store: LocalStore, sql: String): String {
        store.getReadableDatabase().rawQuery(sql, null).use { cursor ->
            assertTrue(cursor.moveToFirst());
            return cursor.getString(0);
        }
    }

private fun onlyStudyItem(store: LocalStore): RecordsStudyModels.StudyItem {
        var items = store.studyItems()
        assertEquals(1, items.size);
        return items.get(0);
    }

private fun assertHasText(activity: MainActivity, text: String) {
        var root = activity.findViewById<View>(android.R.id.content)
        var device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assertTrue("Missing text: " + text + "\nVisible text: " + visibleText(root), hasText(activity, text, device));
    }

private fun assertHasTexts(activity: MainActivity, vararg texts: String) {
        for (text in texts) {
            assertHasText(activity, text);
        }
    }

private fun assertNoText(activity: MainActivity, text: String) {
        var device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        if (hasText(activity, text, device)) {
            throw AssertionError("Unexpected text before reveal: " + text);
        }
    }

private fun hasText(activity: MainActivity, text: String, device: UiDevice): Boolean {
        var root = activity.findViewById<View>(android.R.id.content)
        return findText(root, text) != null || findDeviceTextNow(device, text) != null;
    }

fun visibleText(root: View): String {
    val texts = ArrayList<String>()
    collectVisibleText(root, texts)
    return texts.toString()
}

fun collectVisibleText(root: View, texts: MutableList<String>) {
    if (root.getVisibility() != View.VISIBLE) {
        return
    }
    if (root is TextView) {
        val value = root.text
        if (value != null && !value.toString().isBlank()) {
            texts.add(value.toString())
        }
    }
    if (root is android.view.ViewGroup) {
        val group = root as android.view.ViewGroup
        for (i in 0 until group.getChildCount()) {
            collectVisibleText(group.getChildAt(i), texts)
        }
    }
}

private fun assertNoTexts(activity: MainActivity, vararg texts: String) {
        for (text in texts) {
            assertNoText(activity, text);
        }
    }

fun assertNoteTypeSettings(expected: RecordsSyncModels.Settings) {
        LocalStore(context).use { store ->
            var actual = SyncSettings.fromStore(store)
            assertEquals(expected.modelName, actual.modelName);
            assertEquals(expected.expressionField, actual.expressionField);
            assertEquals(expected.readingField, actual.readingField);
            assertEquals(expected.meaningField, actual.meaningField);
            assertEquals(expected.sentenceField, actual.sentenceField);
            assertEquals(expected.frequencyField, actual.frequencyField);
            assertEquals(expected.frequencySortField, actual.frequencySortField);
        }
    }

private fun drawGuideKanji(activity: MainActivity, kanji: String) {
    val pad: DrawingPadView = requireNotNull(findType(activity.findViewById<View>(android.R.id.content), DrawingPadView::class.java))
    assertNotNull(pad)
    pad.layout(0, 0, 1000, 1000)
    val guide: StrokeGuide = strokeGuide(activity, kanji)
    assertNotNull(guide)
    val now = System.currentTimeMillis()
    var strokeIndex = 0
    for (stroke in guide.strokes) {
        if (stroke.points.size < 2) {
            continue
        }
        val first: InkPoint = stroke.points.get(0)
        sendTouch(pad, now, now + strokeIndex * 40L, MotionEvent.ACTION_DOWN, first.x * 1000f, first.y * 1000f)
        for (i in 1 until (stroke.points.size - 1)) {
            val point: InkPoint = stroke.points.get(i)
            sendTouch(pad, now, now + strokeIndex * 40L + i.toLong(), MotionEvent.ACTION_MOVE, point.x * 1000f, point.y * 1000f)
        }
        val last = stroke.points.get(stroke.points.size - 1)
        sendTouch(pad, now, now + strokeIndex * 40L + 30L, MotionEvent.ACTION_UP, last.x * 1000f, last.y * 1000f)
        strokeIndex++
    }
    assertTrue(pad.hasInk())
}

private fun drawGuideKanjiWithFirstStrokeReversed(activity: MainActivity, kanji: String) {
    val pad: DrawingPadView = requireNotNull(findType(activity.findViewById<View>(android.R.id.content), DrawingPadView::class.java))
    assertNotNull(pad)
    pad.layout(0, 0, 1000, 1000)
    val guide: StrokeGuide = strokeGuide(activity, kanji)
    assertNotNull(guide)
    val now = System.currentTimeMillis()
    var strokeIndex = 0
    for (stroke in guide.strokes) {
        if (stroke.points.size < 2) {
            continue
        }
        if (strokeIndex == 0) {
            val last: InkPoint = stroke.points.get(stroke.points.size - 1)
            sendTouch(pad, now, now, MotionEvent.ACTION_DOWN, last.x * 1000f, last.y * 1000f)
            for (i in (stroke.points.size - 2) downTo 1) {
                val point: InkPoint = stroke.points.get(i)
                sendTouch(pad, now, now + (stroke.points.size - i).toLong(), MotionEvent.ACTION_MOVE, point.x * 1000f, point.y * 1000f)
            }
            val first: InkPoint = stroke.points.get(0)
            sendTouch(pad, now, now + 30L, MotionEvent.ACTION_UP, first.x * 1000f, first.y * 1000f)
        } else {
            val first: InkPoint = stroke.points.get(0)
            sendTouch(pad, now, now + strokeIndex * 40L, MotionEvent.ACTION_DOWN, first.x * 1000f, first.y * 1000f)
            for (i in 1 until (stroke.points.size - 1)) {
                val point: InkPoint = stroke.points.get(i)
                sendTouch(pad, now, now + strokeIndex * 40L + i.toLong(), MotionEvent.ACTION_MOVE, point.x * 1000f, point.y * 1000f)
            }
            val last: InkPoint = stroke.points.get(stroke.points.size - 1)
            sendTouch(pad, now, now + strokeIndex * 40L + 30L, MotionEvent.ACTION_UP, last.x * 1000f, last.y * 1000f)
        }
        strokeIndex++
    }
    assertTrue(pad.hasInk())
}

private fun drawFreeformStroke(activity: MainActivity) {
    val pad: DrawingPadView = requireNotNull(findType(activity.findViewById<View>(android.R.id.content), DrawingPadView::class.java))
    assertNotNull(pad)
    pad.layout(0, 0, 1000, 1000)
    val now = System.currentTimeMillis()
    sendTouch(pad, now, now, MotionEvent.ACTION_DOWN, 240f, 240f)
    sendTouch(pad, now, now + 16L, MotionEvent.ACTION_MOVE, 520f, 460f)
    sendTouch(pad, now, now + 32L, MotionEvent.ACTION_UP, 760f, 640f)
    assertTrue(pad.hasInk())
}

private fun drawFarOffGuideStroke(activity: MainActivity) {
    val pad: DrawingPadView = requireNotNull(findType(activity.findViewById<View>(android.R.id.content), DrawingPadView::class.java))
    assertNotNull(pad)
    pad.layout(0, 0, 1000, 1000)
    val now = System.currentTimeMillis()
    sendTouch(pad, now, now, MotionEvent.ACTION_DOWN, 950f, 950f)
    sendTouch(pad, now, now + 16L, MotionEvent.ACTION_MOVE, 970f, 970f)
    sendTouch(pad, now, now + 32L, MotionEvent.ACTION_UP, 980f, 980f)
}

fun sendTouch(view: View, downTime: Long, eventTime: Long, action: Int, x: Float, y: Float) {
    val event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0)
    view.onTouchEvent(event)
}

private fun strokeGuide(activity: MainActivity, kanji: String): StrokeGuide {
    activity.resources.openRawResource(R.raw.kanji_strokes).bufferedReader().use { reader ->
        return requireNotNull(StrokeGuideParser.parse(reader).get(kanji))
    }
}

fun <T : View> findType(root: View, type: Class<T>): T? {
    if (root.getVisibility() != View.VISIBLE) {
        return null
    }
    if (type.isInstance(root)) {
        return type.cast(root)
    }
    if (root is android.view.ViewGroup) {
        val group = root as android.view.ViewGroup
        for (i in 0 until group.getChildCount()) {
            val found = findType(group.getChildAt(i), type)
            if (found != null) {
                return found
            }
        }
    }
    return null
}

fun <T : View> collectTypes(root: View, type: Class<T>, results: MutableList<T>) {
    if (root.getVisibility() != View.VISIBLE) {
        return
    }
    if (type.isInstance(root)) {
        results.add(type.cast(root))
    }
    if (root is android.view.ViewGroup) {
        val group = root as android.view.ViewGroup
        for (i in 0 until group.getChildCount()) {
            collectTypes(group.getChildAt(i), type, results)
        }
    }
}

fun clickableAncestor(view: View): View? {
    var current: View? = view
    while (current != null) {
        if (current.isClickable) {
            return current
        }
        current = current.getParent() as? View
    }
    return null
}

fun hasAncestorOfType(view: View, type: Class<*>): Boolean {
    return ancestorOfType(view, type) != null
}

fun ancestorOfType(view: View, type: Class<*>): View? {
    var parent: ViewParent? = view.getParent()
    while (parent != null) {
        if (type.isInstance(parent)) {
            return parent as View
        }
        parent = parent.getParent()
    }
    return null
}

fun findText(root: View, text: String): View? {
    if (root.getVisibility() != View.VISIBLE) {
        return null
    }
    if (root is TextView) {
        val value = (root as TextView).text
        if (value != null && value.toString().contains(text)) {
            return root
        }
    }
    if (root is android.view.ViewGroup) {
        val group = root as android.view.ViewGroup
        for (i in 0 until group.getChildCount()) {
            val found = findText(group.getChildAt(i), text)
            if (found != null) {
                return found
            }
        }
    }
    return null
}

fun countText(root: View, text: String): Int {
    if (root.getVisibility() != View.VISIBLE) {
        return 0
    }
    var count = 0
    if (root is TextView) {
        val value = (root as TextView).text
        if (value != null && value.toString().contains(text)) {
            count++
        }
    }
    if (root is android.view.ViewGroup) {
        val group = root as android.view.ViewGroup
        for (i in 0 until group.getChildCount()) {
            count += countText(group.getChildAt(i), text)
        }
    }
    return count
}

fun findExactText(root: View, text: String): View? {
    if (root.getVisibility() != View.VISIBLE) {
        return null
    }
    if (root is TextView) {
        val value = (root as TextView).text
        if (value != null && value.toString().equals(text)) {
            return root
        }
    }
    if (root is android.view.ViewGroup) {
        val group = root as android.view.ViewGroup
        for (i in 0 until group.getChildCount()) {
            val found = findExactText(group.getChildAt(i), text)
            if (found != null) {
                return found
            }
        }
    }
    return null
}

fun setComposeSliderToEnd(description: String) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val slider = requireNotNull(device.wait(Until.findObject(By.pkg(appPackage()).desc(description)), 3000L)) {
        "Missing slider: $description\nDevice text: ${deviceVisibleText(device)}"
    }
    assertTrue("Slider is not enabled: $description", slider.isEnabled())
    val bounds = slider.getVisibleBounds()
    slider.click(Point(bounds.right - 2, bounds.centerY()))
    device.waitForIdle(2000L)
}

fun setComposeTextField(description: String, text: String) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val input = requireNotNull(device.wait(Until.findObject(By.pkg(appPackage()).desc(description)), 3000L)) {
        "Missing text field: $description\nDevice text: ${deviceVisibleText(device)}"
    }
    input.setText(text)
    device.waitForIdle(2000L)
}

fun assertComposeTextFieldValue(description: String, expected: String) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val input = requireNotNull(device.wait(Until.findObject(By.pkg(appPackage()).desc(description)), 3000L)) {
        "Missing text field: $description\nDevice text: ${deviceVisibleText(device)}"
    }
    assertEquals(expected, input.getText())
}

fun setImportFilterChecked(description: String, checked: Boolean) {
    setComposeCheckBox(description, checked)
}

fun setComposeCheckBox(description: String, checked: Boolean) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val checkbox = composeCheckBox(device, description)
    if (checkbox.isChecked() != checked) {
        checkbox.click()
        device.waitForIdle(2000L)
    }
    assertComposeCheckBox(description, checked)
}

fun assertComposeCheckBox(description: String, checked: Boolean) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val checkbox = composeCheckBox(device, description)
    assertEquals("Unexpected checkbox state: $description", checked, checkbox.isChecked())
}

fun composeCheckBox(device: UiDevice, description: String): UiObject2 {
    return requireNotNull(device.wait(Until.findObject(By.pkg(appPackage()).desc(description)), 3000L)) {
        "Missing checkbox: $description\nDevice text: ${deviceVisibleText(device)}"
    }
}

fun collectViews(root: View, views: MutableList<View>) {
    if (root.getVisibility() != View.VISIBLE) {
        return
    }
    views.add(root)
    if (root is android.view.ViewGroup) {
        val group = root as android.view.ViewGroup
        for (i in 0 until group.getChildCount()) {
            collectViews(group.getChildAt(i), views)
        }
    }
}

fun deleteRecursively(file: File) {
    if (!file.exists()) {
        return
    }
    if (file.isDirectory()) {
        val children = file.listFiles()
        if (children != null) {
            for (child in children) {
                deleteRecursively(child)
            }
        }
    }
    file.delete()
}

class FakeWritingRecognizer(private val candidate: String) : WritingRecognizer {
    override fun modelStatus(): CompletableFuture<WritingRecognizer.ModelStatus> {
        return CompletableFuture.completedFuture(WritingRecognizer.ModelStatus("JA", "ja", true, "ready"))
    }

    override fun downloadModel(): CompletableFuture<WritingRecognizer.ModelStatus> {
        return modelStatus()
    }

    override fun recognize(writing: CapturedWriting?): CompletableFuture<WritingRecognizer.RecognitionResult> {
        return CompletableFuture.completedFuture(
            WritingRecognizer.RecognitionResult(
                listOf(WritingRecognizer.Candidate(candidate, 0.99f))
            )
        )
    }

    override fun close() {
        // Fake recognizer has no model resources to release.
    }
}

class FakeUnavailableRecognizer : WritingRecognizer {
    override fun modelStatus(): CompletableFuture<WritingRecognizer.ModelStatus> {
        return CompletableFuture.completedFuture(WritingRecognizer.ModelStatus("JA", "ja", false, "missing"))
    }

    override fun downloadModel(): CompletableFuture<WritingRecognizer.ModelStatus> {
        return modelStatus()
    }

    override fun recognize(writing: CapturedWriting?): CompletableFuture<WritingRecognizer.RecognitionResult> {
        return CompletableFuture.completedFuture(WritingRecognizer.RecognitionResult(emptyList()))
    }

    override fun close() {
        // Fake recognizer has no model resources to release.
    }
}
}
