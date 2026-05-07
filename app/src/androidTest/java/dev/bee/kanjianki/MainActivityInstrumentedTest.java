package dev.bee.kanjianki;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.sqlite.SQLiteDatabaseLockedException;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.SeekBar;
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
import dev.bee.kanjianki.anki.FakeAnkiDroidProvider;
import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.core.study.InkPoint;
import dev.bee.kanjianki.core.study.InkStroke;
import dev.bee.kanjianki.core.study.StrokeGuide;
import dev.bee.kanjianki.core.study.StrokeGuideParser;
import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.study.CapturedWriting;
import dev.bee.kanjianki.study.WritingRecognizer;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class MainActivityInstrumentedTest {
    private static final String LIVE_ARG = "kanjiLiveAnkiDroid";

    private Context context;

    @BeforeClass
    public static void grantSuiteNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) {
            return;
        }
        Context target = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try {
            InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .grantRuntimePermission(target.getPackageName(), Manifest.permission.POST_NOTIFICATIONS);
        } catch (SecurityException ignored) {
            // Some runners pre-grant or disallow shell permission grants; the test can still use the dialog.
        }
    }

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("kanji_anki_simple.db");
        MainActivity.setAnkiDroidGatewayForTests(AnkiDroidGateway.testProvider(context, "dev.bee.kanjianki.no_anki_for_tests"));
        MainActivity.setWritingRecognizerForTests(null);
        MainActivity.setInstallPermissionForTests(null);
    }

    @After
    public void tearDown() {
        MainActivity.setAnkiDroidGatewayForTests(null);
        MainActivity.setWritingRecognizerForTests(null);
        MainActivity.setInstallPermissionForTests(null);
        context.deleteDatabase("kanji_anki_simple.db");
        deleteRecursively(new File(context.getCacheDir(), "updates"));
    }

    @Test
    public void testLaunchesHomeWithoutSeedDataOrProviderCrash() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View content = activity.findViewById(android.R.id.content);
                assertNotNull(content);
                assertTrue(content.getWidth() >= 0);
                assertTrue(content.getHeight() >= 0);
                assertHasText(activity, "Kani");
                assertHasText(activity, "Sync AnkiDroid");
                assertHasText(activity, "Study streak");
                assertHasText(activity, "No streak yet");
                assertNoText(activity, "Queue");
                assertNoText(activity, "Update");
            });
        }
    }

    @Test
    public void testHomeShowsCurrentStudyStreak() {
        long today = localDayStart(System.currentTimeMillis());
        long yesterday = moveLocalDays(today, -1);
        LocalStore store = new LocalStore(context);
        try {
            store.saveReview(review("拉", "streak-yesterday"), "good", yesterday + 60_000L);
            store.saveReview(review("提", "streak-today-a"), "good", today + 60_000L);
            store.saveReview(review("謎", "streak-today-b"), "easy", today + 120_000L);
        } finally {
            store.close();
        }

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertHasText(activity, "Study streak");
                assertHasText(activity, "2-day streak");
                assertHasText(activity, "Streak logged today");
                assertHasText(activity, "2 writing reviews today");
                assertHasText(activity, "Best: 2 days");
            });
        }
    }

    @Test
    public void testNavigationSettingsAndEmptyStates() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Nothing to study yet");
                assertHasText(activity, "Sync from AnkiDroid first");
            });

            clickText(scenario, "Stats");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Stats");
                assertHasText(activity, "Anki impact");
                assertHasText(activity, "Sync AnkiDroid to connect Kani stats to your Kiku cards");
            });

            clickText(scenario, "Settings");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Rarity cutoff");
                assertHasText(activity, "Default: 3000");
                assertHasText(activity, "Daily workload");
                assertHasText(activity, "Auto Pareto: waiting for problem kanji");
                assertHasText(activity, "Use manual workload");
                assertHasText(activity, "FSRS retention");
                assertHasText(activity, "Desired retention: 90%");
                assertHasText(activity, "Daily reminder");
                assertHasText(activity, "App updates");
                assertHasText(activity, "Off");
            });
            clickText(scenario, "Use manual workload");
            waitForText(scenario, "Pareto: up to 5 kanji");
            scenario.onActivity(activity -> {
                SeekBar slider = findType(activity.findViewById(android.R.id.content), SeekBar.class);
                assertNotNull(slider);
                slider.setProgress(70);
            });
            clickText(scenario, "Save workload");
            clickText(scenario, "95%");
            clickText(scenario, "Save retention");
            clickText(scenario, "4000");
            clickText(scenario, "Save cutoff");
            clickText(scenario, "Morning 08:00");
            clickText(scenario, "Enable reminder");
            clickTextIfPresent("Allow");
            waitForText(scenario, "Daily around 08:00");

            LocalStore store = new LocalStore(context);
            try {
                assertEquals(AdaptiveLoadPlanner.MODE_MANUAL, store.adaptiveLoadMode());
                assertEquals(70, store.adaptiveLoadWorkPercent());
                assertEquals(0.95, store.schedulerParameters().targetRetention, 0.001);
                assertEquals(4000, store.getIntSetting("suspended_rank_cutoff", 3000));
                LocalStore.ReminderSettings reminder = store.reminderSettings();
                assertTrue(reminder.enabled);
                assertEquals(8, reminder.hour);
                assertEquals(0, reminder.minute);
            } finally {
                store.close();
            }

            clickText(scenario, "Open updater");
            waitForText(scenario, "GitHub updater");
            scenario.onActivity(activity -> {
                assertHasText(activity, "GitHub updater");
                assertHasText(activity, "Current version");
                assertHasText(activity, "Check for update");
            });
        }
    }

    @Test
    public void testUpdateScreenShowsAutomaticStatusAndInstallPermissionFlow() {
        MainActivity.setInstallPermissionForTests(false);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Settings");
            waitForText(scenario, "App updates");
            scenario.onActivity(activity -> {
                assertHasText(activity, "On: checks about once a day");
                assertHasText(activity, "Last check: not yet");
                assertHasText(activity, "Install permission: Missing");
                assertHasText(activity, "Set up app installs");
                assertHasText(activity, "Turn off automatic updates");
            });
        }
    }

    @Test
    public void testUpdateScreenSurfacesCachedPendingUpdate() throws Exception {
        File updatesDir = new File(context.getCacheDir(), "updates");
        assertTrue(updatesDir.mkdirs() || updatesDir.isDirectory());
        try (FileOutputStream output = new FileOutputStream(new File(updatesDir, "kani-test.apk"))) {
            output.write(new byte[]{1, 2, 3});
        }
        LocalStore store = new LocalStore(context);
        try {
            store.recordAutoUpdateResult(
                    System.currentTimeMillis(),
                    "Android needs confirmation to finish installing.",
                    "v9.9.9",
                    "kani-test.apk",
                    "Android needs confirmation before Kani can replace itself."
            );
        } finally {
            store.close();
        }
        MainActivity.setInstallPermissionForTests(true);

        Intent openUpdate = new Intent(context, MainActivity.class)
                .putExtra(MainActivity.EXTRA_OPEN_UPDATE, true);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(openUpdate)) {
            waitForText(scenario, "Verified APK ready: 9.9.9");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Install permission: Ready");
                assertHasText(activity, "Install verified update");
                assertHasText(activity, "Android needs confirmation before Kani can replace itself.");
            });
        }
    }

    @Test
    public void testStatsConnectsKaniPracticeToAnkiImpact() {
        Records.DashboardRow active = dashboardRow("拉", "ramen radical gap", "ら", "Imported from suspended cards");
        Records.DashboardRow supported = dashboardRow("謎", "mystery radical gap", "なぞ", "Enough mature Anki cards now support it", 2);
        seedDashboard(Arrays.asList(active, supported));
        long now = System.currentTimeMillis();
        LocalStore store = new LocalStore(context);
        try {
            store.replaceStudyItems(Arrays.asList(
                    new Records.StudyItem("拉", "learning", 0L, 0.9, 5.0, 1, 0, 1, 1, null, now),
                    new Records.StudyItem("謎", "retired", now + 86_400_000L, 2.5, 4.0, 3, 0, 2, 3, null, now - 86_400_000L)
            ));
            store.saveReview(review("拉", "stats-good"), "good", now - 7_200_000L);
            store.saveReview(new Records.ReviewRequest("謎", "stats-again", "again", true, false, false, 0), "again", now - 3_600_000L);
        } finally {
            store.close();
        }

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Stats");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Anki impact");
                assertHasText(activity, "2 problem kanji found from AnkiDroid");
                assertHasText(activity, "2 active Anki example links");
                assertHasText(activity, "2 suspended miss links");
                assertHasText(activity, "Kani writing");
                assertHasText(activity, "2 writing reviews");
                assertHasText(activity, "2 kanji studied");
                assertHasText(activity, "50% automatic pass rate");
                assertHasText(activity, "1 miss caught");
                assertHasText(activity, "Now practicing");
                assertHasText(activity, "1 active kanji");
                assertHasText(activity, "1 due now");
                assertHasText(activity, "2 mature Anki support links");
                assertHasText(activity, "1 kanji resting in Kani");
            });
        }
    }

    @Test
    public void testKanjiDetailCopyAndStudyReviewFlow() {
        seedDashboard();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertHasText(activity, "Todayy's Focus");
                assertHasText(activity, "Adaptive focus queue");
                assertHasText(activity, "ramen radical gap");
                assertHasText(activity, "From 拉麺");
            });

            clickText(scenario, "拉");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Why it is here");
                assertHasText(activity, "Imported from suspended cards");
                assertHasText(activity, "Review this now");
                assertHasText(activity, "Copy Anki search");
                assertHasText(activity, "Recovery timeline");
                assertHasText(activity, "Active repair");
                assertHasText(activity, "Mature support 0 / target 2");
                assertHasText(activity, "Kani started watching");
            });

            clickText(scenario, "Copy Anki search");
            scenario.onActivity(activity -> {
                ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = clipboard.getPrimaryClip();
                if (clip != null) {
                    assertEquals("deck:Kiku 拉", clip.getItemAt(0).coerceToText(activity).toString());
                }
                assertHasText(activity, "Copied Anki search");
            });

            clickText(scenario, "Review this now");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Name this kanji");
                assertHasText(activity, "Kanji -> meaning");
                assertHasText(activity, "Answer hidden until reveal");
                assertHasText(activity, "What does it mean?");
                assertHasText(activity, "Reveal");
                assertNoText(activity, "Example: 拉麺  らーめん");
                assertNoText(activity, "From: 拉麺");
            });

            clickText(scenario, "Reveal");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Answer");
                assertHasText(activity, "Meaning: ramen radical gap");
                assertHasText(activity, "Reading: ら");
                assertHasText(activity, "From: 拉麺  らーめん");
                assertHasText(activity, "I knew it");
                assertHasText(activity, "I missed it");
                assertNoText(activity, "Check");
            });

            clickText(scenario, "I missed it");
            LocalStore store = new LocalStore(context);
            try {
                Records.ReviewStats stats = store.reviewStatsSince(0L);
                assertEquals(1, stats.total);
                assertEquals(1, stats.again);
                assertEquals(0, stats.writingRequired);
                List<Records.StudyItem> items = store.studyItems();
                assertEquals(1, items.size());
                assertEquals(1, items.get(0).consecutiveFailedRecognitionDays);
                assertFalse(items.get(0).writingRemediationPending);
            } finally {
                store.close();
            }
        }
    }

    @Test
    public void testKanjiDetailTimelineShowsReviewAfterStudy() {
        seedDashboard();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study");
            clickText(scenario, "Reveal");
            clickText(scenario, "I knew it");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Today's focus done");
                assertHasText(activity, "Continue all kanji");
            });
            clickText(scenario, "Home");
            clickText(scenario, "拉");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Recovery timeline");
                assertHasText(activity, "Resting until review");
                assertHasText(activity, "Review passed");
                assertHasText(activity, "Recall review was rated good.");
            });
        }
    }

    @Test
    public void testHomeQueuePreviewShowsActivePracticeKanjiNotEveryCandidate() {
        Records.DashboardRow active = dashboardRow("拉", "ramen radical gap", "ら", "Imported from suspended cards");
        Records.DashboardRow retired = dashboardRow("謎", "mystery unused", "なぞ", "Already covered by known cards");
        seedDashboard(Arrays.asList(active, retired));
        LocalStore store = new LocalStore(context);
        try {
            store.replaceStudyItems(Arrays.asList(
                    new Records.StudyItem("拉", "new", 0L, 0.4, 5.0, 0, 0, 0, 0, null, 0L),
                    new Records.StudyItem("謎", "retired", 0L, 0.4, 5.0, 1, 0, 2, 3, null, 0L)
            ));
        } finally {
            store.close();
        }

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertHasText(activity, "Adaptive focus queue");
                assertHasText(activity, "ramen radical gap");
                assertHasText(activity, "From 拉麺");
                assertNoText(activity, "mystery unused");
                assertNoText(activity, "謎");
            });
        }
    }

    @Test
    public void testBrowsingHomeQueuePreviewDoesNotAdmitNewStudyItems() {
        seedDashboardRowsOnly(Collections.singletonList(dashboardRow("拉", "ramen radical gap", "ら", "Imported from suspended cards")));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertHasText(activity, "No active practice yet");
                assertHasText(activity, "Study now");
            });

            LocalStore store = new LocalStore(context);
            try {
                assertTrue(store.studyItems().isEmpty());
            } finally {
                store.close();
            }
        }
    }

    @Test
    public void testLearnNextProblemKanjiFromHomeAdmitsStudyItem() {
        seedDashboardRowsOnly(Collections.singletonList(dashboardRow("拉", "ramen radical gap", "ら", "Imported from suspended cards")));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study now");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Name this kanji");
                assertHasText(activity, "Kanji -> meaning");
                assertHasText(activity, "What does it mean?");
                assertHasText(activity, "Reveal");
            });

            LocalStore store = new LocalStore(context);
            try {
                List<Records.StudyItem> items = store.studyItems();
                assertEquals(1, items.size());
                assertEquals("拉", items.get(0).kanji);
            } finally {
                store.close();
            }
        }
    }

    @Test
    public void testHomeQueuePreviewOrderMatchesReviewFirstStudySelection() {
        Records.DashboardRow newRow = dashboardRow("拉", "ramen radical gap", "ら", "Imported from suspended cards");
        Records.DashboardRow reviewRow = dashboardRow("謎", "mystery radical gap", "なぞ", "Missed in mature cards");
        seedDashboard(Arrays.asList(newRow, reviewRow));
        LocalStore store = new LocalStore(context);
        try {
            store.replaceStudyItems(Arrays.asList(
                    new Records.StudyItem("拉", "new", 0L, 0.4, 5.0, 0, 0, 0, 0, null, 0L),
                    new Records.StudyItem("謎", "review", 500L, 1.8, 4.8, 2, 0, 2, 3, null, 0L)
            ));
        } finally {
            store.close();
        }

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertHasText(activity, "Study now");
                assertHasText(activity, "mystery radical gap");
                assertHasText(activity, "ramen radical gap");
            });
            clickText(scenario, "Study now");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Name this kanji");
                assertHasText(activity, "Kanji -> meaning");
                assertHasText(activity, "What does it mean?");
            });
        }
    }

    @Test
    public void testMissingStrokeGuideIsExplainedBeforeDrawing() {
        seedDueWritingItem(dashboardRow("鿃", "rare shape", "ソウ", "Imported from suspended cards"), 0);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study");
            scenario.onActivity(activity -> {
                assertHasText(activity, "No numbered stroke guide is bundled");
                assertHasText(activity, "Stroke-order feedback will be limited");
                assertNoText(activity, "Trace the numbered strokes");
            });
        }
    }

    @Test
    public void testWritingRepairHintPreservesInkAndReference() {
        seedDueWritingItem(3);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Writing repair");
                assertHasText(activity, "Recognition has missed on multiple days");
                assertHasText(activity, "Reference");
                assertHasText(activity, "拉麺");
                assertHasText(activity, "More help");
            });

            scenario.onActivity(activity -> drawGuideKanji(activity, "拉"));
            clickText(scenario, "More help");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Hint used");
                assertHasText(activity, "ink stayed on the canvas");
                assertHasText(activity, "current stroke hinted");
                MainActivity.DrawingPadView pad = findType(activity.findViewById(android.R.id.content), MainActivity.DrawingPadView.class);
                assertNotNull(pad);
                assertTrue(pad.hasInk());
                assertHasText(activity, "Reference");
                assertHasText(activity, "拉麺");
            });
        }
    }

    @Test
    public void testWritingRepairCheckKeepsSingleCleanMatchMessage() {
        seedDueWritingItem(3);
        MainActivity.setWritingRecognizerForTests(new FakeWritingRecognizer("拉"));

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Writing repair");
                assertHasText(activity, "Reference");
                drawGuideKanji(activity, "拉");
            });
            clickText(scenario, "Check");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Clean match");
                assertHasText(activity, "Target: 拉");
                assertHasText(activity, "Next card");
                assertHasText(activity, "Reference");
                assertEquals(1, countText(activity.findViewById(android.R.id.content), "Clean match"));
            });
        }
    }

    @Test
    public void testDiagnosisTextAndReplayAppearAfterCheck() {
        seedDueWritingItem();
        MainActivity.setWritingRecognizerForTests(new FakeWritingRecognizer("拉"));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study");
            scenario.onActivity(activity -> drawGuideKanjiWithFirstStrokeReversed(activity, "拉"));
            clickText(scenario, "Check");
            waitForText(scenario, "likely wrong direction");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Stroke 1: likely wrong direction");
                assertHasText(activity, "Recognized, but the stroke path was messy");
                assertHasText(activity, "Try cleaner");
                assertHasText(activity, "Save hard");
                assertHasText(activity, "Replay");
            });
        }
    }

    @Test
    public void testReplayHiddenWhenStrokeGuideMissing() {
        seedDueWritingItem(dashboardRow("鿃", "rare shape", "ソウ", "Imported from suspended cards"), 0);
        MainActivity.setWritingRecognizerForTests(new FakeWritingRecognizer("鿃"));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study");
            scenario.onActivity(MainActivityInstrumentedTest::drawFreeformStroke);
            clickText(scenario, "Check");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Stroke order could not be checked");
                assertNoText(activity, "Replay");
            });
        }
    }

    @Test
    public void testDrawingAfterCheckClearsPriorReplayState() {
        seedDueWritingItem();
        MainActivity.setWritingRecognizerForTests(new FakeWritingRecognizer("拉"));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study");
            scenario.onActivity(activity -> drawGuideKanji(activity, "拉"));
            clickText(scenario, "Check");
            waitForText(scenario, "Clean match");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Replay");
                assertHasText(activity, "Next card");
                drawFreeformStroke(activity);
            });
            scenario.onActivity(activity -> {
                assertHasText(activity, "Updated ink");
                assertNoText(activity, "Clean match");
                assertNoText(activity, "Replay");
                assertNoText(activity, "Next card");
                MainActivity.DrawingPadView pad = findType(activity.findViewById(android.R.id.content), MainActivity.DrawingPadView.class);
                assertNotNull(pad);
                assertFalse(pad.hasReplaySnapshot());
            });
        }
    }

    @Test
    public void testWritingRepairMissKeepsRepairReference() {
        seedDueWritingItem(3);
        MainActivity.setWritingRecognizerForTests(new FakeWritingRecognizer("提"));

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Writing repair");
                assertHasText(activity, "Reference");
                drawGuideKanji(activity, "拉");
            });
            clickText(scenario, "Check");
            scenario.onActivity(activity -> {
                assertHasText(activity, "I could not read that as the target kanji yet");
                assertHasText(activity, "Reference");
                assertHasText(activity, "Meaning: ramen radical gap");
                assertHasText(activity, "Try again with full guide");
                assertHasText(activity, "Save miss");
            });
        }
    }

    @Test
    public void testStudyLoopActionsArePinnedOutsideScrollableContent() {
        seedDueWritingItem();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study");
            scenario.onActivity(activity -> {
                View root = activity.findViewById(android.R.id.content);
                View check = findExactText(root, "Check");
                View erase = findExactText(root, "Erase");
                assertNotNull(check);
                assertNotNull(erase);
                assertFalse(hasAncestorOfType(check, ScrollView.class));
                assertFalse(hasAncestorOfType(erase, ScrollView.class));
            });
        }
    }

    @Test
    public void testNewKanjiStartsAsHiddenFlashcardAndKnownAnswerLogsRecognitionReview() {
        seedDashboard();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Name this kanji");
                assertHasText(activity, "Kanji -> meaning");
                assertHasText(activity, "Answer hidden until reveal");
                assertNoText(activity, "拉麺");
            });
            clickText(scenario, "Reveal");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Answer");
                assertHasText(activity, "拉");
                assertHasText(activity, "I knew it");
                assertHasText(activity, "I missed it");
            });
            clickText(scenario, "I knew it");

            LocalStore store = new LocalStore(context);
            try {
                Records.ReviewStats stats = store.reviewStatsSince(0L);
                assertEquals(1, stats.total);
                assertEquals(1, stats.good);
                assertEquals(0, stats.writingRequired);
                List<Records.StudyItem> items = store.studyItems();
                assertEquals(1, items.size());
                Records.StudyItem item = items.get(0);
                assertEquals("拉", item.kanji);
                assertEquals("learning", item.state);
                assertEquals(1, item.totalReviews);
                assertEquals(1, item.learningStep);
                assertEquals(0, item.writingLevel);
                assertEquals(1, item.recognitionStage);
            } finally {
                store.close();
            }
        }
    }

    @Test
    public void testDueLearningRepeatIsPracticeOnlyAndDoesNotLogReview() {
        seedDashboard();
        LocalStore setup = new LocalStore(context);
        try {
            long now = System.currentTimeMillis();
            Records.StudyItem repeatItem = new Records.StudyItem("拉", "learning", now + 86_400_000L, 0.4, 5.0, 1, 1, 0, 0, null, now)
                    .withAnswerSignature("拉|拉致|らち|archive example");
            setup.enqueueLearningRepeat(repeatItem, "kanji_meaning", Records.LEARNING_REPEAT_NEW, 0, now - 1_000L, now - 2_000L);
        } finally {
            setup.close();
        }

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Learning step 1 / 2. Practice only.");
                assertHasText(activity, "Reveal");
            });

            clickText(scenario, "Reveal");
            clickText(scenario, "I knew it");

            LocalStore store = new LocalStore(context);
            try {
                assertEquals(0, store.reviewStatsSince(0L).total);
                List<Records.LearningRepeat> repeats = store.dueLearningRepeats(System.currentTimeMillis() + 11 * 60_000L);
                assertEquals(1, repeats.size());
                assertEquals(1, repeats.get(0).stepIndex);
            } finally {
                store.close();
            }
        }
    }

    @Test
    public void testMissedRecognitionUsesInternalWritingThreshold() {
        seedDashboard();
        LocalStore initialStore = new LocalStore(context);
        try {
            initialStore.putIntSetting("writing_trigger_miss_days", 1);
        } finally {
            initialStore.close();
        }
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study");
            clickText(scenario, "Reveal");
            clickText(scenario, "I missed it");

            LocalStore store = new LocalStore(context);
            try {
                Records.ReviewStats stats = store.reviewStatsSince(0L);
                assertEquals(1, stats.total);
                assertEquals(1, stats.again);
                assertEquals(0, stats.writingRequired);
                List<Records.StudyItem> items = store.studyItems();
                assertEquals(1, items.size());
                assertFalse(items.get(0).writingRemediationPending);
                assertEquals(0, items.get(0).recognitionStage);
                assertEquals(1, items.get(0).consecutiveFailedRecognitionDays);
            } finally {
                store.close();
            }
        }
    }

    @Test
    public void testCorrectWritingCheckSubmitsReview() {
        seedDueWritingItem();
        MainActivity.setWritingRecognizerForTests(new FakeWritingRecognizer("拉"));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study");
            scenario.onActivity(activity -> drawGuideKanji(activity, "拉"));
            clickText(scenario, "Check");
            waitForText(scenario, "Clean match");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Clean match");
                assertHasText(activity, "Target: 拉");
                assertHasText(activity, "Next card");
                assertEquals(1, countText(activity.findViewById(android.R.id.content), "Clean match"));
            });
            clickText(scenario, "Next card");

            LocalStore store = new LocalStore(context);
            try {
                Records.ReviewStats stats = store.reviewStatsSince(0L);
                assertEquals(1, stats.total);
                assertEquals(0, stats.good);
                assertEquals(1, stats.hard);
                assertEquals(1, stats.writingRequired);
                assertEquals(0, stats.writingFailed);
                List<Records.StudyItem> items = store.studyItems();
                assertEquals(1, items.size());
                Records.StudyItem item = items.get(0);
                assertEquals("拉", item.kanji);
                assertEquals("review", item.state);
                assertEquals(2, item.totalReviews);
                assertEquals(1, item.learningStep);
                assertEquals(1, item.writingLevel);
                assertTrue(item.activeToken == null || item.activeToken.isEmpty());
            } finally {
                store.close();
            }
        }
    }

    @Test
    public void testHintAssistedCleanWritingHoldsFadeLevel() {
        seedDueWritingItem(2);
        MainActivity.setWritingRecognizerForTests(new FakeWritingRecognizer("拉"));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study");
            clickText(scenario, "More help");
            scenario.onActivity(activity -> drawGuideKanji(activity, "拉"));
            clickText(scenario, "Check");
            waitForText(scenario, "Clean match");
            clickText(scenario, "Next card");

            LocalStore store = new LocalStore(context);
            try {
                Records.ReviewStats stats = store.reviewStatsSince(0L);
                assertEquals(1, stats.total);
                assertEquals(1, stats.hard);
                List<Records.StudyItem> items = store.studyItems();
                assertEquals(1, items.size());
                assertEquals(2, items.get(0).writingLevel);
            } finally {
                store.close();
            }
        }
    }

    @Test
    public void testMessyRecognizedWritingCanBeSavedHardWithoutAdvancingFade() {
        seedDueWritingItem(2);
        MainActivity.setWritingRecognizerForTests(new FakeWritingRecognizer("拉"));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study");
            scenario.onActivity(activity -> drawGuideKanjiWithFirstStrokeReversed(activity, "拉"));
            clickText(scenario, "Check");
            waitForText(scenario, "Try cleaner");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Try cleaner");
                assertHasText(activity, "Save hard");
            });
            clickText(scenario, "Save hard");

            LocalStore store = new LocalStore(context);
            try {
                Records.ReviewStats stats = store.reviewStatsSince(0L);
                assertEquals(1, stats.total);
                assertEquals(1, stats.hard);
                List<Records.StudyItem> items = store.studyItems();
                assertEquals(1, items.size());
                assertEquals(2, items.get(0).writingLevel);
            } finally {
                store.close();
            }
        }
    }

    @Test
    public void testWrongRecognitionCanBeLoggedAsFailedAttempt() {
        seedDueWritingItem(2);
        MainActivity.setWritingRecognizerForTests(new FakeWritingRecognizer("提"));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study");
            scenario.onActivity(activity -> drawGuideKanji(activity, "拉"));
            clickText(scenario, "Check");
            scenario.onActivity(activity -> {
                assertHasText(activity, "I could not read that as the target kanji yet");
                assertHasText(activity, "Target: 拉");
                assertHasText(activity, "Save miss");
            });
            clickText(scenario, "Save miss");

            LocalStore store = new LocalStore(context);
            try {
                Records.ReviewStats stats = store.reviewStatsSince(0L);
                assertEquals(1, stats.total);
                assertEquals(1, stats.again);
                assertEquals(1, stats.writingRequired);
                assertEquals(1, stats.writingFailed);
                List<Records.StudyItem> items = store.studyItems();
                assertEquals(1, items.size());
                assertEquals("learning", items.get(0).state);
                assertEquals(1, items.get(0).writingLevel);
            } finally {
                store.close();
            }
        }
    }

    @Test
    public void testTryAgainWithFullGuideStartsFreshAttempt() {
        seedDueWritingItem();
        MainActivity.setWritingRecognizerForTests(new FakeWritingRecognizer("提"));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study");
            scenario.onActivity(activity -> drawGuideKanji(activity, "拉"));
            clickText(scenario, "Check");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Try again with full guide");
                assertHasText(activity, "Replay");
            });
            clickText(scenario, "Try again with full guide");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Fresh guided try");
                assertNoText(activity, "I could not read that as the target kanji yet");
                assertNoText(activity, "Next card");
                assertNoText(activity, "Replay");
                MainActivity.DrawingPadView pad = findType(activity.findViewById(android.R.id.content), MainActivity.DrawingPadView.class);
                assertNotNull(pad);
                assertFalse(pad.hasInk());
                assertFalse(pad.isReplayOverlayVisibleForTests());
            });
        }
    }

    @Test
    public void testWrongRecognitionAllowsLoggedManualOverride() {
        seedDueWritingItem();
        MainActivity.setWritingRecognizerForTests(new FakeWritingRecognizer("提"));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study");
            scenario.onActivity(activity -> drawGuideKanji(activity, "拉"));
            clickText(scenario, "Check");
            scenario.onActivity(activity -> {
                assertHasText(activity, "I could not read that as the target kanji yet");
                assertHasText(activity, "Mark right anyway");
            });
            clickText(scenario, "Mark right anyway");

            LocalStore store = new LocalStore(context);
            try {
                Records.ReviewStats stats = store.reviewStatsSince(0L);
                assertEquals(1, stats.total);
                assertEquals(1, stats.good);
                assertEquals(1, stats.writingRequired);
                assertEquals(0, stats.writingFailed);
            } finally {
                store.close();
            }
        }
    }

    @Test
    public void testMissingModelCanBeManuallyScoredAfterDrawing() {
        seedDueWritingItem();
        MainActivity.setWritingRecognizerForTests(new FakeUnavailableRecognizer());
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study");
            scenario.onActivity(activity -> drawGuideKanji(activity, "拉"));
            clickText(scenario, "Check");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Download the handwriting checker before automatic checks");
                assertHasText(activity, "Target: 拉");
                assertHasText(activity, "Save miss");
                assertHasText(activity, "Mark right anyway");
            });
            clickText(scenario, "Mark right anyway");

            LocalStore store = new LocalStore(context);
            try {
                Records.ReviewStats stats = store.reviewStatsSince(0L);
                assertEquals(1, stats.total);
                assertEquals(1, stats.good);
                assertEquals(1, stats.writingRequired);
                assertEquals(0, stats.writingFailed);
            } finally {
                store.close();
            }
        }
    }

    @Test
    public void testDrawingPadTracksInkAndClear() {
        MainActivity.DrawingPadView pad = new MainActivity.DrawingPadView(context);
        assertFalse(pad.hasInk());

        long now = System.currentTimeMillis();
        sendTouch(pad, now, now, MotionEvent.ACTION_DOWN, 20f, 20f);
        sendTouch(pad, now, now + 10, MotionEvent.ACTION_MOVE, 80f, 80f);
        sendTouch(pad, now, now + 20, MotionEvent.ACTION_UP, 120f, 120f);

        assertTrue(pad.hasInk());
        pad.clear();
        assertFalse(pad.hasInk());
    }

    @Test
    public void testManualSyncButtonRecordsProviderFailureWithoutCrash() throws Exception {
        Assume.assumeFalse(
                "The opt-in live AnkiDroid run exercises the successful sync button path instead.",
                liveAnkiDroidEnabled()
        );
        MainActivity.setAnkiDroidGatewayForTests(AnkiDroidGateway.testProvider(context, "dev.bee.kanjianki.missing_anki"));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Sync AnkiDroid");
            clickText(scenario, "Sync cards");
            LocalStore.SyncStatus status = waitForLatestSync();
            assertNotNull(status);
            assertEquals("config_error", status.status);
            assertTrue(status.errorMessage.contains("AnkiDroid"));
        }
    }

    @Test
    public void testManualSyncButtonEnablesDailyAutoSyncAfterSuccess() throws Exception {
        MainActivity.setAnkiDroidGatewayForTests(AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY));
        context.getContentResolver().call(Uri.parse("content://" + FakeAnkiDroidProvider.AUTHORITY), "reset", null, null);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Sync AnkiDroid");
            clickText(scenario, "Sync cards");
            LocalStore.SyncStatus status = waitForLatestSync();
            assertNotNull(status);
            assertEquals("success", status.status);
            waitForText(scenario, "Sync complete");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Today's adaptive focus");
                assertNoText(activity, "new per day");
            });

            LocalStore store = new LocalStore(context);
            try {
                LocalStore.AutoSyncSettings auto = store.autoSyncSettings();
                assertTrue(auto.configured);
                assertTrue(auto.enabled);
                assertTrue(auto.nextRunAt > System.currentTimeMillis());
            } finally {
                store.close();
            }
        }
    }

    @Test
    public void testManualSyncButtonWorksAgainstLiveAnkiDroid() throws Exception {
        Assume.assumeTrue("Live AnkiDroid fixture is opt-in.", liveAnkiDroidEnabled());
        MainActivity.setAnkiDroidGatewayForTests(null);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Sync AnkiDroid");
            clickText(scenario, "Sync cards");
            LocalStore.SyncStatus status = waitForLatestSync(2400);
            assertNotNull(status);
            assertEquals("success", status.status);

            LocalStore store = new LocalStore(context);
            try {
                assertFalse(store.dashboardRows().isEmpty());
                assertFalse(store.studyItems().isEmpty());
            } finally {
                store.close();
            }
        }
    }

    private LocalStore.SyncStatus waitForLatestSync() throws Exception {
        return waitForLatestSync(200);
    }

    private LocalStore.SyncStatus waitForLatestSync(int attempts) throws Exception {
        for (int i = 0; i < attempts; i++) {
            LocalStore store = new LocalStore(context);
            try {
                LocalStore.SyncStatus status = store.latestSync();
                if (status != null) {
                    return status;
                }
            } catch (SQLiteDatabaseLockedException busy) {
                // The live sync button test polls while the app is committing a large collection import.
            } finally {
                store.close();
            }
            Thread.sleep(100);
        }
        return null;
    }

    private boolean liveAnkiDroidEnabled() {
        return "true".equals(InstrumentationRegistry.getArguments().getString(LIVE_ARG));
    }

    private static Records.ReviewRequest review(String kanji, String token) {
        return new Records.ReviewRequest(kanji, token, "good", true, true, false, 0);
    }

    private static long localDayStart(long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static long moveLocalDays(long localDayStart, int days) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(localDayStart);
        calendar.add(Calendar.DAY_OF_YEAR, days);
        return calendar.getTimeInMillis();
    }

    private void seedDashboard() {
        seedDashboard(Collections.singletonList(dashboardRow("拉", "ramen radical gap", "ら", "Imported from suspended cards")));
    }

    private void seedDueWritingItem() {
        seedDueWritingItem(0);
    }

    private void seedDueWritingItem(int writingLevel) {
        seedDueWritingItem(dashboardRow("拉", "ramen radical gap", "ら", "Imported from suspended cards"), writingLevel);
    }

    private void seedDueWritingItem(Records.DashboardRow row, int writingLevel) {
        seedDashboard(Collections.singletonList(row));
        LocalStore store = new LocalStore(context);
        try {
            store.replaceStudyItems(Collections.singletonList(
                    new Records.StudyItem(
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
                            0L
                    )
            ));
        } finally {
            store.close();
        }
    }

    private void seedDashboard(List<Records.DashboardRow> rows) {
        seedDashboardRowsOnly(rows);
        LocalStore store = new LocalStore(context);
        try {
            long now = System.currentTimeMillis();
            ArrayList<Records.StudyItem> items = new ArrayList<>();
            for (Records.DashboardRow row : rows) {
                items.add(new Records.StudyItem(row.kanji, "new", now, 0.4, 5.0, 0, 0, 0, 0, null, now));
            }
            store.replaceStudyItems(items);
        } finally {
            store.close();
        }
    }

    private void seedDashboardRowsOnly(List<Records.DashboardRow> rows) {
        Records.Settings settings = Records.Settings.kikuDefaults();
        Records.Note note = note(1L, "拉麺", "らーめん", "ramen radical gap", "拉麺を食べた。");
        Records.Card card = new Records.Card(10L, 1L, 0, "Kiku", 2, 2, 0, 3, 4, 1, false);
        LocalStore store = new LocalStore(context);
        try {
            store.saveSuccessfulSync(
                    new Records.CollectionSnapshot(Collections.singletonList(note), Collections.singletonList(card)),
                    Collections.emptyList(),
                    rows,
                    settings,
                    1000L,
                    2000L,
                    null
            );
        } finally {
            store.close();
        }
    }

    private Records.DashboardRow dashboardRow(String kanji, String meaning, String reading, String reasonText) {
        return dashboardRow(kanji, meaning, reading, reasonText, 0);
    }

    private Records.DashboardRow dashboardRow(String kanji, String meaning, String reading, String reasonText, int matureSupportCount) {
        Records.Example active = new Records.Example("active", 10L, 1L, kanji.equals("拉") ? "拉麺" : kanji + "語", kanji.equals("拉") ? "らーめん" : reading, meaning, kanji + "を見た。", false, 1);
        Records.Example suspended = new Records.Example("suspended", 20L, 2L, kanji.equals("拉") ? "拉致" : kanji + "例", kanji.equals("拉") ? "らち" : reading, "archive example", kanji + "を練習した。", false, 0);
        return new Records.DashboardRow(
                kanji,
                3401,
                meaning,
                reading,
                "deck:Kiku " + kanji,
                kanji.equals("拉") ? 88 : 42,
                "suspended_archive",
                reasonText,
                1,
                1,
                matureSupportCount,
                Arrays.asList(active, suspended)
        );
    }

    private Records.Note note(long id, String expression, String reading, String meaning, String sentence) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("Expression", expression);
        fields.put("ExpressionReading", reading);
        fields.put("MainDefinition", meaning);
        fields.put("Sentence", sentence);
        fields.put("Frequency", "1000");
        fields.put("FreqSort", "1000");
        return new Records.Note(id, "Kiku", fields, Collections.emptyList());
    }

    private static void clickText(ActivityScenario<MainActivity> scenario, String text) {
        boolean[] clicked = new boolean[]{false};
        scenario.onActivity(activity -> {
            View view = findExactText(activity.findViewById(android.R.id.content), text);
            if (view == null) {
                return;
            }
            View clickable = clickableAncestor(view);
            assertNotNull("Text is not clickable: " + text, clickable);
            assertTrue(clickable.performClick());
            clicked[0] = true;
        });
        if (clicked[0]) {
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle(2000L);
            return;
        }
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        UiObject2 object = findDeviceText(device, text);
        if (object == null && !"Allow".equals(text)) {
            UiObject2 allow = device.wait(Until.findObject(By.res("com.android.permissioncontroller:id/permission_allow_button")), 1000L);
            if (allow == null) {
                allow = device.wait(Until.findObject(By.text("Allow")), 1000L);
            }
            if (allow != null) {
                allow.click();
                device.waitForIdle(2000L);
                object = findDeviceText(device, text);
            }
        }
        assertNotNull("Missing text: " + text, object);
        object.click();
        device.waitForIdle(2000L);
    }

    private static void clickTextIfPresent(String text) {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        UiObject2 object = findDeviceText(device, text);
        if (object != null) {
            object.click();
            device.waitForIdle(2000L);
        }
    }

    private static UiObject2 findDeviceText(UiDevice device, String text) {
        UiObject2 object = device.wait(Until.findObject(By.text(text)), 3000L);
        if (object == null) {
            object = device.wait(Until.findObject(By.text(text.toUpperCase(Locale.ROOT))), 3000L);
        }
        if (object == null) {
            object = device.wait(Until.findObject(By.textContains(text)), 3000L);
        }
        if (object == null) {
            object = device.wait(Until.findObject(By.textContains(text.toUpperCase(Locale.ROOT))), 3000L);
        }
        return object;
    }

    private static void waitForText(ActivityScenario<MainActivity> scenario, String text) {
        long deadline = SystemClock.uptimeMillis() + 5000L;
        boolean[] found = new boolean[]{false};
        while (SystemClock.uptimeMillis() < deadline) {
            scenario.onActivity(activity -> found[0] = findText(activity.findViewById(android.R.id.content), text) != null);
            if (found[0]) {
                return;
            }
            SystemClock.sleep(100L);
        }
        scenario.onActivity(activity -> assertHasText(activity, text));
    }

    private static void assertHasText(MainActivity activity, String text) {
        assertNotNull("Missing text: " + text, findText(activity.findViewById(android.R.id.content), text));
    }

    private static void assertNoText(MainActivity activity, String text) {
        View found = findText(activity.findViewById(android.R.id.content), text);
        if (found != null) {
            throw new AssertionError("Unexpected text before reveal: " + text);
        }
    }

    private static int textTop(MainActivity activity, String text) {
        View view = findText(activity.findViewById(android.R.id.content), text);
        assertNotNull("Missing text: " + text, view);
        Rect bounds = new Rect();
        assertTrue(view.getGlobalVisibleRect(bounds));
        return bounds.top;
    }

    private static void drawGuideKanji(MainActivity activity, String kanji) {
        MainActivity.DrawingPadView pad = findType(activity.findViewById(android.R.id.content), MainActivity.DrawingPadView.class);
        assertNotNull(pad);
        pad.layout(0, 0, 1000, 1000);
        StrokeGuide guide = strokeGuide(activity, kanji);
        assertNotNull(guide);
        long now = System.currentTimeMillis();
        int strokeIndex = 0;
        for (InkStroke stroke : guide.strokes) {
            if (stroke.points.size() < 2) {
                continue;
            }
            InkPoint first = stroke.points.get(0);
            sendTouch(pad, now, now + strokeIndex * 40L, MotionEvent.ACTION_DOWN, first.x * 1000f, first.y * 1000f);
            for (int i = 1; i < stroke.points.size() - 1; i++) {
                InkPoint point = stroke.points.get(i);
                sendTouch(pad, now, now + strokeIndex * 40L + i, MotionEvent.ACTION_MOVE, point.x * 1000f, point.y * 1000f);
            }
            InkPoint last = stroke.points.get(stroke.points.size() - 1);
            sendTouch(pad, now, now + strokeIndex * 40L + 30L, MotionEvent.ACTION_UP, last.x * 1000f, last.y * 1000f);
            strokeIndex++;
        }
        assertTrue(pad.hasInk());
    }

    private static void drawGuideKanjiWithFirstStrokeReversed(MainActivity activity, String kanji) {
        MainActivity.DrawingPadView pad = findType(activity.findViewById(android.R.id.content), MainActivity.DrawingPadView.class);
        assertNotNull(pad);
        pad.layout(0, 0, 1000, 1000);
        StrokeGuide guide = strokeGuide(activity, kanji);
        assertNotNull(guide);
        long now = System.currentTimeMillis();
        int strokeIndex = 0;
        for (InkStroke stroke : guide.strokes) {
            if (stroke.points.size() < 2) {
                continue;
            }
            if (strokeIndex == 0) {
                InkPoint last = stroke.points.get(stroke.points.size() - 1);
                sendTouch(pad, now, now, MotionEvent.ACTION_DOWN, last.x * 1000f, last.y * 1000f);
                for (int i = stroke.points.size() - 2; i > 0; i--) {
                    InkPoint point = stroke.points.get(i);
                    sendTouch(pad, now, now + (stroke.points.size() - i), MotionEvent.ACTION_MOVE, point.x * 1000f, point.y * 1000f);
                }
                InkPoint first = stroke.points.get(0);
                sendTouch(pad, now, now + 30L, MotionEvent.ACTION_UP, first.x * 1000f, first.y * 1000f);
            } else {
                InkPoint first = stroke.points.get(0);
                sendTouch(pad, now, now + strokeIndex * 40L, MotionEvent.ACTION_DOWN, first.x * 1000f, first.y * 1000f);
                for (int i = 1; i < stroke.points.size() - 1; i++) {
                    InkPoint point = stroke.points.get(i);
                    sendTouch(pad, now, now + strokeIndex * 40L + i, MotionEvent.ACTION_MOVE, point.x * 1000f, point.y * 1000f);
                }
                InkPoint last = stroke.points.get(stroke.points.size() - 1);
                sendTouch(pad, now, now + strokeIndex * 40L + 30L, MotionEvent.ACTION_UP, last.x * 1000f, last.y * 1000f);
            }
            strokeIndex++;
        }
        assertTrue(pad.hasInk());
    }

    private static void drawFreeformStroke(MainActivity activity) {
        MainActivity.DrawingPadView pad = findType(activity.findViewById(android.R.id.content), MainActivity.DrawingPadView.class);
        assertNotNull(pad);
        pad.layout(0, 0, 1000, 1000);
        long now = System.currentTimeMillis();
        sendTouch(pad, now, now, MotionEvent.ACTION_DOWN, 240f, 240f);
        sendTouch(pad, now, now + 16L, MotionEvent.ACTION_MOVE, 520f, 460f);
        sendTouch(pad, now, now + 32L, MotionEvent.ACTION_UP, 760f, 640f);
        assertTrue(pad.hasInk());
    }

    private static void sendTouch(View view, long downTime, long eventTime, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
        try {
            view.onTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    private static StrokeGuide strokeGuide(MainActivity activity, String kanji) {
        try (InputStream in = activity.getResources().openRawResource(R.raw.kanji_strokes);
             InputStreamReader reader = new InputStreamReader(in)) {
            return StrokeGuideParser.parse(reader).get(kanji);
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private static <T extends View> T findType(View root, Class<T> type) {
        if (root.getVisibility() != View.VISIBLE) {
            return null;
        }
        if (type.isInstance(root)) {
            return type.cast(root);
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                T found = findType(group.getChildAt(i), type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static View clickableAncestor(View view) {
        View current = view;
        while (current != null) {
            if (current.isClickable()) {
                return current;
            }
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return null;
    }

    private static boolean hasAncestorOfType(View view, Class<?> type) {
        ViewParent parent = view.getParent();
        while (parent != null) {
            if (type.isInstance(parent)) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    private static View findText(View root, String text) {
        if (root.getVisibility() != View.VISIBLE) {
            return null;
        }
        if (root instanceof TextView) {
            CharSequence value = ((TextView) root).getText();
            if (value != null && value.toString().contains(text)) {
                return root;
            }
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findText(group.getChildAt(i), text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static int countText(View root, String text) {
        if (root.getVisibility() != View.VISIBLE) {
            return 0;
        }
        int count = 0;
        if (root instanceof TextView) {
            CharSequence value = ((TextView) root).getText();
            if (value != null && value.toString().contains(text)) {
                count++;
            }
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                count += countText(group.getChildAt(i), text);
            }
        }
        return count;
    }

    private static View findExactText(View root, String text) {
        if (root.getVisibility() != View.VISIBLE) {
            return null;
        }
        if (root instanceof TextView) {
            CharSequence value = ((TextView) root).getText();
            if (value != null && value.toString().equals(text)) {
                return root;
            }
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findExactText(group.getChildAt(i), text);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
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
        private final String candidate;

        private FakeWritingRecognizer(String candidate) {
            this.candidate = candidate;
        }

        @Override
        public CompletableFuture<ModelStatus> modelStatus() {
            return CompletableFuture.completedFuture(new ModelStatus("JA", "ja", true, "ready"));
        }

        @Override
        public CompletableFuture<ModelStatus> downloadModel() {
            return modelStatus();
        }

        @Override
        public CompletableFuture<RecognitionResult> recognize(CapturedWriting writing) {
            return CompletableFuture.completedFuture(new RecognitionResult(
                    Collections.singletonList(new Candidate(candidate, 0.99f))
            ));
        }

        @Override
        public void close() {
        }
    }

    private static final class FakeUnavailableRecognizer implements WritingRecognizer {
        @Override
        public CompletableFuture<ModelStatus> modelStatus() {
            return CompletableFuture.completedFuture(new ModelStatus("JA", "ja", false, "missing"));
        }

        @Override
        public CompletableFuture<ModelStatus> downloadModel() {
            return modelStatus();
        }

        @Override
        public CompletableFuture<RecognitionResult> recognize(CapturedWriting writing) {
            return CompletableFuture.completedFuture(new RecognitionResult(Collections.emptyList()));
        }

        @Override
        public void close() {
        }
    }
}
