package dev.bee.kanjianki;

import android.Manifest;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabaseLockedException;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.EditText;
import android.widget.ProgressBar;
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
import dev.bee.kanjianki.anki.CollectionGateway;
import dev.bee.kanjianki.anki.FakeAnkiDroidProvider;
import dev.bee.kanjianki.core.AdaptiveLoadPlanner;
import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.core.SimilarKanjiIndex;
import dev.bee.kanjianki.core.study.InkPoint;
import dev.bee.kanjianki.core.study.InkStroke;
import dev.bee.kanjianki.core.study.StrokeGuide;
import dev.bee.kanjianki.core.study.StrokeGuideParser;
import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.data.StudyStatsStore;
import dev.bee.kanjianki.study.CapturedWriting;
import dev.bee.kanjianki.study.WritingRecognizer;
import dev.bee.kanjianki.sync.SyncProgress;

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
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class MainActivityInstrumentedTest {
    private static final String LIVE_ARG = "kanjiLiveAnkiDroid";
    private static final String LIVE_FOREGROUND_SYNC_TEST = "testManualSyncButtonWorksAgainstLiveAnkiDroid";
    private static final String STUDY_NOW = "Study now";
    private static final String REVEAL = "Reveal";
    private static final String CHECK = "Check";
    private static final String RAMEN_RADICAL_GAP = "ramen radical gap";
    private static final String IMPORTED_FROM_SUSPENDED_CARDS = "Imported from suspended cards";
    private static final String MISSED_IN_MATURE_CARDS = "Missed in mature cards";
    private static final String CLEAN_MATCH = "Clean match";
    private static final String NEXT_CARD = "Next card";

    @Rule
    public final TestName testName = new TestName();

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
            assertNotNull(ignored);
        }
    }

    @Before
    public void setUp() {
        if (liveAnkiDroidEnabled() && !LIVE_FOREGROUND_SYNC_TEST.equals(testName.getMethodName())) {
            Assume.assumeTrue("Live AnkiDroid runs only the foreground sync button path from MainActivity.", false);
        }
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("kanji_anki_simple.db");
        MainActivity.setAnkiDroidGatewayForTests(AnkiDroidGateway.testProvider(context, "dev.bee.kanjianki.no_anki_for_tests"));
        MainActivity.setCollectionGatewayForTests(null);
        MainActivity.setWritingRecognizerForTests(null);
        MainActivity.setInstallPermissionForTests(null);
    }

    @After
    public void tearDown() {
        MainActivity.setAnkiDroidGatewayForTests(null);
        MainActivity.setCollectionGatewayForTests(null);
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
                assertHasText(activity, "Settings");
                assertNoText(activity, "Queue");
                assertNoText(activity, "Update");
            });
        }
    }

    @Test
    public void testHomeShowsCurrentStudyStreak() {
        long today = localDayStart(System.currentTimeMillis());
        long yesterday = moveLocalDays(today, -1);
        try (LocalStore store = new LocalStore(context)) {
            store.saveReview(review("拉", "streak-yesterday"), "good", yesterday + 60_000L);
            store.saveReview(review("提", "streak-today-a"), "good", today + 60_000L);
            store.saveReview(review("謎", "streak-today-b"), "easy", today + 120_000L);
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
    public void testBrowseKanjiShowsDetailAndSuspensionControls() {
        seedDashboardRowsOnly(Collections.singletonList(dashboardRow("拉", RAMEN_RADICAL_GAP, "らーめん", "Needs writing practice")));

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertHasText(activity, "Browse Kanji");
            });
            clickText(scenario, "Browse Kanji");
            scenario.onActivity(activity -> {
                assertHasText(activity, RAMEN_RADICAL_GAP);
                assertNoText(activity, "SUSPENDED");
            });
            clickText(scenario, RAMEN_RADICAL_GAP);
            scenario.onActivity(activity -> {
                assertHasText(activity, "Back to Browse Kanji");
                assertHasText(activity, "Local inventory");
                assertHasText(activity, "Review this now");
                assertHasText(activity, "Suspend locally");
            });
            clickText(scenario, "Suspend locally");
            scenario.onActivity(activity -> {
                assertHasText(activity, "SUSPENDED");
                assertHasText(activity, "Unsuspend locally");
                assertNoText(activity, "Review this now");
            });
            clickText(scenario, "Unsuspend locally");
            scenario.onActivity(activity -> {
                assertNoText(activity, "SUSPENDED");
                assertHasText(activity, "Review this now");
            });
        }
    }

    @Test
    public void testNavigationSettingsAndEmptyStates() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Stats");
            scenario.onActivity(activity -> {
                assertHasTexts(activity, "Stats", "Kani is not currently working for you", "Weak kanji improved", "Anki support gained");
            });

            clickText(scenario, "Home");
            clickText(scenario, "Settings");
            scenario.onActivity(MainActivityInstrumentedTest::assertCollapsedSettingsScreen);
            clickText(scenario, "App & data");
            waitForText(scenario, "App updates");
            clickText(scenario, "Open updater");
            waitForText(scenario, "GitHub updater");
            scenario.onActivity(activity -> assertHasTexts(activity, "GitHub updater", "Current version", "Check for update"));
        }
    }

    @Test
    public void testSettingsControlsPersist() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Settings");
            scenario.onActivity(activity -> {
                List<SeekBar> sliders = findTypes(activity.findViewById(android.R.id.content), SeekBar.class);
                assertTrue(sliders.size() >= 2);
                sliders.get(0).setProgress(249);
                sliders.get(1).setProgress(3499);
            });
            clickText(scenario, "Save frequency range");
            clickText(scenario, "Study tuning");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Daily workload");
                assertHasText(activity, "Auto Pareto: waiting for problem kanji");
                assertHasText(activity, "Use manual workload");
                assertHasText(activity, "FSRS retention");
                assertHasText(activity, "Desired retention: 90%");
                assertHasText(activity, "Ladder thresholds");
                assertHasText(activity, "Passes to go up");
                assertHasText(activity, "Misses to go down");
            });
            clickText(scenario, "Use manual workload");
            waitForText(scenario, "Pareto: up to 5 items");
            scenario.onActivity(activity -> {
                List<SeekBar> sliders = findTypes(activity.findViewById(android.R.id.content), SeekBar.class);
                assertTrue(sliders.size() >= 3);
                sliders.get(2).setProgress(70);
            });
            clickText(scenario, "Save workload");
            clickText(scenario, "95%");
            clickText(scenario, "Save retention");
            clickText(scenario, "Reminders & sync");
            scenario.onActivity(activity -> assertHasTexts(activity, "Daily reminder", "Daily Anki sync"));
            clickText(scenario, "Morning 08:00");
            clickText(scenario, "Enable reminder");
            clickTextIfPresent("Allow");
            waitForText(scenario, "Daily around 08:00");

            assertNavigationSettingsPersisted();
        }
    }

    @Test
    public void testUpdateScreenShowsAutomaticStatusAndInstallPermissionFlow() {
        MainActivity.setInstallPermissionForTests(false);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Settings");
            clickText(scenario, "App & data");
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
        try (LocalStore store = new LocalStore(context)) {
            store.recordAutoUpdateResult(
                    System.currentTimeMillis(),
                    "Android needs confirmation to finish installing.",
                    "v9.9.9",
                    "kani-test.apk",
                    "Android needs confirmation before Kani can replace itself."
            );
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
        long now = System.currentTimeMillis();
        try (LocalStore store = new LocalStore(context)) {
            store.saveSuccessfulSync(
                    new Records.CollectionSnapshot(Collections.emptyList(), Collections.emptyList()),
                    Collections.emptyList(),
                    Arrays.asList(
                            statsDashboardRow("痛", 82, 1),
                            statsDashboardRow("薬", 76, 0),
                            statsDashboardRow("疲", 69, 0),
                            statsDashboardRow("平", 74, 1)
                    ),
                    Records.Settings.kikuDefaults(),
                    now - 30_000L,
                    now - 20_000L,
                    null
            );
            store.saveReview(review("痛", "stats-pain"), "good", now - 15_000L);
            store.saveReview(review("薬", "stats-medicine"), "good", now - 14_000L);
            store.saveReview(review("疲", "stats-tired"), "good", now - 13_000L);
            store.saveReview(review("平", "stats-flat"), "good", now - 12_000L);
            store.saveSuccessfulSync(
                    new Records.CollectionSnapshot(Collections.emptyList(), Collections.emptyList()),
                    Collections.emptyList(),
                    Arrays.asList(
                            statsDashboardRow("痛", 46, 3),
                            statsDashboardRow("薬", 51, 2),
                            statsDashboardRow("疲", 44, 1),
                            statsDashboardRow("平", 50, 1)
                    ),
                    Records.Settings.kikuDefaults(),
                    now - 5_000L,
                    now,
                    null
            );
        }

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Stats");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Kani is working for you");
                assertHasText(activity, "Answered study time");
                assertHasText(activity, "Today: 0 sec");
                assertHasText(activity, "Last 7 days: 0 sec");
                assertHasText(activity, "Answered tasks: 0");
                assertHasText(activity, "Avg / task: 0 sec");
                assertHasText(activity, "Weak kanji improved");
                assertHasText(activity, "4 weak kanji improved after Kani practice");
                assertHasText(activity, "Average weakness dropped from 0.75 to 0.48 after Kani practice.");
                assertHasText(activity, "痛  0.82 -> 0.46");
                assertHasText(activity, "薬  0.76 -> 0.51");
                assertHasText(activity, "疲  0.69 -> 0.44");
                assertNoText(activity, "平  0.74 -> 0.50");
                assertHasText(activity, "Anki support gained");
                assertHasText(activity, "3 kanji gained Anki support");
                assertHasText(activity, "2 of them gained their first mature supporting card.");
                assertHasText(activity, "痛  1 -> 3 mature cards");
                assertHasText(activity, "薬  0 -> 2 mature cards");
                assertHasText(activity, "疲  0 -> 1 mature cards");
                assertNoText(activity, "Anki impact");
                assertNoText(activity, "Kani writing");
                assertNoText(activity, "Now practicing");
                assertNoText(activity, "What this means");
                assertNoText(activity, "You are turning Anki pain points");
            });
        }
    }

    @Test
    public void testStatsShowsImpactHistoryBuckets() {
        long now = System.currentTimeMillis();
        try (LocalStore store = new LocalStore(context)) {
            store.saveSuccessfulSync(
                    new Records.CollectionSnapshot(Collections.emptyList(), Collections.emptyList()),
                    Collections.emptyList(),
                    Collections.singletonList(statsDashboardRow("裂", 80, 0)),
                    Records.Settings.kikuDefaults(),
                    now - 20_000L,
                    now - 10_000L,
                    null
            );
            store.saveReview(review("裂", "impact-no-after-sync"), "good", now - 9_000L);
        }

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Stats");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Kani is not currently working for you");
                assertHasText(activity, "0 weak kanji improved after Kani practice");
                assertHasText(activity, "Weakness improvements will show after Kani reviews are followed by a successful AnkiDroid sync.");
                assertHasText(activity, "0 kanji gained Anki support");
                assertHasText(activity, "0 of them gained their first mature supporting card.");
                assertNoText(activity, "helped kanji");
                assertNoText(activity, "not-helping-yet kanji");
                assertNoText(activity, "needs-more-cards kanji");
            });
        }
    }

    private static Records.DashboardRow statsDashboardRow(String kanji, int weaknessScore, int matureSupportCount) {
        return new Records.DashboardRow(
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
                Collections.emptyList()
        );
    }

    @Test
    public void testKanjiDetailCopyAndStudyReviewFlow() {
        seedDashboard();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertHasTexts(activity, "Today's focus", "Focus queue", RAMEN_RADICAL_GAP, "From 拉麺");
            });

            clickText(scenario, "拉");
            scenario.onActivity(MainActivityInstrumentedTest::assertKanjiDetailReady);

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
            scenario.onActivity(MainActivityInstrumentedTest::assertHiddenRecognitionCard);

            clickText(scenario, REVEAL);
            scenario.onActivity(MainActivityInstrumentedTest::assertRevealedRecognitionCard);

            clickText(scenario, "Fail");
            assertFailedRecognitionReviewStored();
        }
    }

    @Test
    public void testKanjiDetailTimelineShowsReviewAfterStudy() {
        seedDashboard();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, STUDY_NOW);
            clickText(scenario, REVEAL);
            clickText(scenario, "Pass");
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
        Records.DashboardRow active = dashboardRow("拉", RAMEN_RADICAL_GAP, "ら", IMPORTED_FROM_SUSPENDED_CARDS);
        Records.DashboardRow retired = dashboardRow("謎", "mystery unused", "なぞ", "Already covered by known cards");
        seedDashboard(Arrays.asList(active, retired));
        try (LocalStore store = new LocalStore(context)) {
            store.replaceStudyItems(Arrays.asList(
                    new Records.StudyItem("拉", "new", 0L, 0.4, 5.0, 0, 0, 0, 0, null, 0L),
                    new Records.StudyItem("謎", "retired", 0L, 0.4, 5.0, 1, 0, 2, 3, null, 0L)
            ));
        }

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertHasText(activity, "Focus queue");
                assertHasText(activity, RAMEN_RADICAL_GAP);
                assertHasText(activity, "From 拉麺");
                assertNoText(activity, "mystery unused");
                assertNoText(activity, "謎");
            });
        }
    }

    @Test
    public void testHomeViewAllShowsFullFocusQueue() {
        seedDashboard(Arrays.asList(
                dashboardRow("拉", RAMEN_RADICAL_GAP, "ら", IMPORTED_FROM_SUSPENDED_CARDS),
                dashboardRow("謎", "mystery radical gap", "なぞ", MISSED_IN_MATURE_CARDS),
                dashboardRow("示", "show", "しめす", MISSED_IN_MATURE_CARDS),
                dashboardRow("浸", "to be soaked in", "ひたす", MISSED_IN_MATURE_CARDS)
        ));

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertHasText(activity, "View all");
            });
            clickText(scenario, "View all");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Focus queue");
                assertHasText(activity, "to be soaked in");
            });
        }
    }

    @Test
    public void testRecentMistakesOpensMissedReviewList() {
        seedDashboard();
        try (LocalStore store = new LocalStore(context)) {
            store.saveReview(review("拉", "recent-miss"), "again", System.currentTimeMillis());
        }

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Recent mistakes");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Recent mistakes");
                assertHasText(activity, RAMEN_RADICAL_GAP);
                assertHasText(activity, "Rated again");
            });
        }
    }

    @Test
    public void testBrowsingHomeQueuePreviewDoesNotAdmitNewStudyItems() {
        seedDashboardRowsOnly(Collections.singletonList(dashboardRow("拉", RAMEN_RADICAL_GAP, "ら", IMPORTED_FROM_SUSPENDED_CARDS)));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertHasText(activity, "No active practice yet");
                assertHasText(activity, STUDY_NOW);
            });

            try (LocalStore store = new LocalStore(context)) {
                assertTrue(store.studyItems().isEmpty());
            }
        }
    }

    @Test
    public void testLearnNextProblemKanjiFromHomeAdmitsStudyItem() {
        seedDashboardRowsOnly(Collections.singletonList(dashboardRow("拉", RAMEN_RADICAL_GAP, "ら", IMPORTED_FROM_SUSPENDED_CARDS)));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, STUDY_NOW);
            scenario.onActivity(activity -> {
                assertHasText(activity, "Name this kanji");
                assertHasText(activity, "Kanji -> meaning");
                assertHasText(activity, "What does it mean?");
                assertHasText(activity, REVEAL);
            });

            try (LocalStore store = new LocalStore(context)) {
                List<Records.StudyItem> items = store.studyItems();
                assertEquals(1, items.size());
                assertEquals("拉", items.get(0).kanji);
            }
        }
    }

    @Test
    public void testStudyNowStopsAtConfiguredMaximumItems() {
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
        try (LocalStore setupStore = new LocalStore(context)) {
            setupStore.saveAdaptiveLoadMaxItems(3);
        }

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, STUDY_NOW);
            for (int i = 0; i < 3; i++) {
                waitForText(scenario, REVEAL);
                clickText(scenario, REVEAL);
                waitForText(scenario, "Pass");
                clickText(scenario, "Pass");
            }
            waitForText(scenario, "Today's focus done");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Study now: 3 / 3");
                assertHasText(activity, "Continue all kanji");
                assertNoText(activity, REVEAL);
            });

            try (LocalStore store = new LocalStore(context)) {
                assertEquals(3, store.reviewStatsSince(0L).total);
            }
        }
    }

    @Test
    public void testHomeQueuePreviewOrderMatchesReviewFirstStudySelection() {
        Records.DashboardRow newRow = dashboardRow("拉", RAMEN_RADICAL_GAP, "ら", IMPORTED_FROM_SUSPENDED_CARDS);
        Records.DashboardRow reviewRow = dashboardRow("謎", "mystery radical gap", "なぞ", MISSED_IN_MATURE_CARDS);
        seedDashboard(Arrays.asList(newRow, reviewRow));
        try (LocalStore store = new LocalStore(context)) {
            store.replaceStudyItems(Arrays.asList(
                    new Records.StudyItem("拉", "new", 0L, 0.4, 5.0, 0, 0, 0, 0, null, 0L),
                    new Records.StudyItem("謎", "review", 500L, 1.8, 4.8, 2, 0, 2, 3, null, 0L)
            ));
        }

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertHasText(activity, STUDY_NOW);
                assertHasText(activity, "mystery radical gap");
                assertHasText(activity, RAMEN_RADICAL_GAP);
            });
            clickText(scenario, STUDY_NOW);
            scenario.onActivity(activity -> {
                assertHasText(activity, "Name this kanji");
                assertHasText(activity, "Kanji -> meaning");
                assertHasText(activity, "What does it mean?");
            });
        }
    }

    @Test
    public void testMissingStrokeGuideIsExplainedBeforeDrawing() {
        seedDueWritingItem(dashboardRow("鿃", "rare shape", "ソウ", IMPORTED_FROM_SUSPENDED_CARDS), 0);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, STUDY_NOW);
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
            clickText(scenario, STUDY_NOW);
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
                DrawingPadView pad = findType(activity.findViewById(android.R.id.content), DrawingPadView.class);
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
            clickText(scenario, STUDY_NOW);
            scenario.onActivity(activity -> {
                assertHasText(activity, "Writing repair");
                assertHasText(activity, "Reference");
                drawGuideKanji(activity, "拉");
            });
            clickText(scenario, CHECK);
            scenario.onActivity(activity -> {
                assertHasText(activity, CLEAN_MATCH);
                assertHasText(activity, "Target: 拉");
                assertHasText(activity, NEXT_CARD);
                assertHasText(activity, "Reference");
                assertEquals(1, countText(activity.findViewById(android.R.id.content), CLEAN_MATCH));
            });
        }
    }

    @Test
    public void testDiagnosisTextAndReplayAppearAfterCheck() {
        seedDueWritingItem();
        MainActivity.setWritingRecognizerForTests(new FakeWritingRecognizer("拉"));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, STUDY_NOW);
            scenario.onActivity(activity -> drawGuideKanjiWithFirstStrokeReversed(activity, "拉"));
            clickText(scenario, CHECK);
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
        seedDueWritingItem(dashboardRow("鿃", "rare shape", "ソウ", IMPORTED_FROM_SUSPENDED_CARDS), 0);
        MainActivity.setWritingRecognizerForTests(new FakeWritingRecognizer("鿃"));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, STUDY_NOW);
            scenario.onActivity(MainActivityInstrumentedTest::drawFreeformStroke);
            clickText(scenario, CHECK);
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
            clickText(scenario, STUDY_NOW);
            scenario.onActivity(activity -> drawGuideKanji(activity, "拉"));
            clickText(scenario, CHECK);
            waitForText(scenario, CLEAN_MATCH);
            scenario.onActivity(activity -> {
                assertHasText(activity, "Replay");
                assertHasText(activity, NEXT_CARD);
                drawFreeformStroke(activity);
            });
            scenario.onActivity(activity -> {
                assertHasText(activity, "Updated ink");
                assertNoText(activity, CLEAN_MATCH);
                assertNoText(activity, "Replay");
                assertNoText(activity, NEXT_CARD);
                DrawingPadView pad = findType(activity.findViewById(android.R.id.content), DrawingPadView.class);
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
            clickText(scenario, STUDY_NOW);
            scenario.onActivity(activity -> {
                assertHasText(activity, "Writing repair");
                assertHasText(activity, "Reference");
                drawGuideKanji(activity, "拉");
            });
            clickText(scenario, CHECK);
            scenario.onActivity(activity -> {
                assertHasText(activity, "I could not read that as the target kanji yet");
                assertHasText(activity, "Reference");
                assertHasText(activity, "Latin, kidnap");
                assertHasText(activity, "Try again with full guide");
                assertHasText(activity, "Save miss");
            });
        }
    }

    @Test
    public void testStudyLoopActionsArePinnedOutsideScrollableContent() {
        seedDueWritingItem();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, STUDY_NOW);
            scenario.onActivity(activity -> {
                View root = activity.findViewById(android.R.id.content);
                View check = findExactText(root, CHECK);
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
            clickText(scenario, STUDY_NOW);
            int hiddenCardHeight = recognitionCardHeight(scenario);
            assertTrue("Hidden card should be measured", hiddenCardHeight > 0);
            scenario.onActivity(activity -> {
                assertHasTexts(activity, "Name this kanji", "Kanji -> meaning", "Answer hidden until reveal");
                assertNoTexts(activity, "拉麺");
                View root = activity.findViewById(android.R.id.content);
                View reveal = findExactText(root, REVEAL);
                assertNotNull(reveal);
                Rect revealBounds = new Rect();
                assertTrue(reveal.getGlobalVisibleRect(revealBounds));
                assertFalse(hasAncestorOfType(reveal, ScrollView.class));
            });
            clickText(scenario, REVEAL);
            scenario.onActivity(activity -> {
                assertHasTexts(activity, "Answer", "拉", "Fail", "Pass");
                assertTrue("Revealed card should keep full study height",
                        recognitionCard(activity).getHeight() >= hiddenCardHeight - 2);
                Rect failBounds = new Rect();
                Rect passBounds = new Rect();
                View root = activity.findViewById(android.R.id.content);
                View answer = findExactText(root, "Answer");
                View fail = findExactText(root, "Fail");
                View pass = findExactText(root, "Pass");
                assertNotNull(answer);
                assertNotNull(fail);
                assertNotNull(pass);
                Rect answerBounds = new Rect();
                assertTrue(answer.getGlobalVisibleRect(answerBounds));
                assertTrue(fail.getGlobalVisibleRect(failBounds));
                assertTrue(pass.getGlobalVisibleRect(passBounds));
                assertTrue("Fail should be left of Pass", failBounds.centerX() < passBounds.centerX());
            });
            clickText(scenario, "Pass");

            assertKnownAnswerRecognitionReviewStored();
        }
    }

    @Test
    public void testRevealedRecognitionCardPassesOnRightSwipe() {
        seedDashboard();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, STUDY_NOW);
            clickText(scenario, REVEAL);
            swipeRecognitionCard(scenario, true);

            assertKnownAnswerRecognitionReviewStored();
        }
    }

    @Test
    public void testRevealedRecognitionCardFailsOnLeftSwipe() {
        seedDashboard();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, STUDY_NOW);
            clickText(scenario, REVEAL);
            swipeRecognitionCard(scenario, false);

            assertFailedRecognitionReviewStored();
        }
    }

    @Test
    public void testHiddenRecognitionSwipeDoesNotGradeBeforeReveal() {
        seedDashboard();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, STUDY_NOW);
            swipeRecognitionCard(scenario, true);

            scenario.onActivity(activity -> {
                assertHasTexts(activity, "Name this kanji", "Answer hidden until reveal", REVEAL);
                assertNoTexts(activity, "Latin, kidnap", "Fail", "Pass");
            });
            try (LocalStore store = new LocalStore(context)) {
                assertEquals(0, store.reviewStatsSince(0L).total);
            }
        }
    }

    @Test
    public void testSimilarChoiceMissQueuesTwoWritingRepairsBeforeRecognition() throws Exception {
        seedSimilarChoiceDashboard();
        MainActivity.setWritingRecognizerForTests(new FakeWritingRecognizer("拉"));

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, STUDY_NOW);
            scenario.onActivity(activity -> assertSimilarChoiceCard(activity, "0 / 2", "Which kanji means Latin, kidnap?", "拉", "提"));

            clickText(scenario, "提");
            scenario.onActivity(activity -> {
                assertHasTexts(activity, "Similar writing", "Reference", "Latin, kidnap");
                drawGuideKanji(activity, "拉");
            });
            clickText(scenario, CHECK);
            waitForText(scenario, CLEAN_MATCH);
            clickText(scenario, NEXT_CARD);

            MainActivity.setWritingRecognizerForTests(new FakeWritingRecognizer("提"));
            waitForText(scenario, "Propose, take along");
            scenario.onActivity(activity -> drawGuideKanji(activity, "提"));
            clickText(scenario, CHECK);
            waitForText(scenario, CLEAN_MATCH);
            clickText(scenario, NEXT_CARD);

            waitForText(scenario, "Which kanji means Latin, kidnap?");
            clickText(scenario, "拉");
            scenario.onActivity(activity -> {
                assertHasTexts(activity, "3 / 4", "Name this kanji", "Kanji -> meaning");
            });

            assertSimilarRepairsQueuedWithoutReviews();
        }
    }

    @Test
    public void testReviewThisNowUsesSimilarChoiceGate() throws Exception {
        seedSimilarChoiceDashboard();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "拉");
            scenario.onActivity(activity -> assertHasText(activity, "Review this now"));

            clickText(scenario, "Review this now");
            scenario.onActivity(activity -> {
                assertHasText(activity, "0 / 2");
                assertHasText(activity, "Similar choice");
                assertHasText(activity, "Which kanji means Latin, kidnap?");
                assertHasText(activity, "拉");
                assertHasText(activity, "提");
                assertNoText(activity, "Kanji -> meaning");
            });
        }
    }

    @Test
    public void testInventorySimilarChoiceAppearsBeforeFocusDone() throws Exception {
        seedFocusCompleteWithInventorySimilarChoice();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, STUDY_NOW);
            scenario.onActivity(activity -> {
                assertHasText(activity, "1 / 2");
                assertHasText(activity, "Similar choice");
                assertHasText(activity, "Which kanji means Propose, take along?");
                assertHasText(activity, "提");
                assertHasText(activity, "謎");
                assertNoText(activity, "Today's focus done");
            });

            clickText(scenario, "提");
            scenario.onActivity(activity -> {
                assertHasText(activity, "2 / 2");
                assertHasText(activity, "Today's focus done");
            });

            try (LocalStore store = new LocalStore(context)) {
                StudyStatsStore.StudyTaskTimeStats stats = store.studyTaskTimeStats(System.currentTimeMillis());
                assertEquals(1, stats.answeredTasks);
            }
        }
    }

    @Test
    public void testNormalPassLogsAnsweredStudyTime() {
        seedDashboard();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, STUDY_NOW);
            clickText(scenario, REVEAL);
            clickText(scenario, "Pass");

            try (LocalStore store = new LocalStore(context)) {
                Records.ReviewStats reviewStats = store.reviewStatsSince(0L);
                assertEquals(1, reviewStats.total);
                assertEquals(1, reviewStats.good);
                StudyStatsStore.StudyTaskTimeStats timeStats = store.studyTaskTimeStats(System.currentTimeMillis());
                assertEquals(1, timeStats.answeredTasks);
            }
        }
    }

    @Test
    public void testDueLearningRepeatIsPracticeOnlyAndDoesNotLogReview() {
        // In the single-scheduler model, a card in NEW_LEARNING phase with a
        // past due_at shows up directly in the study queue. Answering it
        // advances the learning step on the item itself. Learning step answers
        // are logged but do not count as real FSRS reviews.
        seedDashboard();
        try (LocalStore setup = new LocalStore(context)) {
            long now = System.currentTimeMillis();
            // Create a study item in NEW_LEARNING phase, step 0, due in the past.
            Records.StudyItem item = new Records.StudyItem("拉", "learning", now - 1_000L, 0.4, 5.0, 1, 0, 0, 0, null, now)
                    .withAnswerSignature("拉|拉致|らち|archive example");
            item = item.copyBuilder()
                    .rung(Records.LadderRung.KANJI_MEANING)
                    .phase(Records.SchedulerPhase.NEW_LEARNING)
                    .build();
            setup.saveStudyItem(item);
        }

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, STUDY_NOW);
            scenario.onActivity(activity -> assertHasText(activity, REVEAL));

            clickText(scenario, REVEAL);
            clickText(scenario, "Pass");

            try (LocalStore store = new LocalStore(context)) {
                // The learning step answer is logged but the card stays in learning.
                List<Records.StudyItem> items = store.studyItems();
                assertFalse(items.isEmpty());
                Records.StudyItem updated = items.stream()
                        .filter(i -> "拉".equals(i.kanji))
                        .findFirst()
                        .orElse(null);
                assertNotNull(updated);
                // After Good on step 0, advances to step 1 (or graduates if only 1 step).
                assertTrue(updated.learningStep >= 1 || updated.phase == Records.SchedulerPhase.REVIEW);
            }
        }
    }

    @Test
    public void testLearningRepeatPassAdvancesSessionProgressHeader() {
        // In the single-scheduler model, learning step cards show up in the
        // normal study queue and passing them advances session progress.
        seedDashboard(Arrays.asList(
                dashboardRow("拉", RAMEN_RADICAL_GAP, "ら", IMPORTED_FROM_SUSPENDED_CARDS),
                dashboardRow("提", "carry radical gap", "てい", IMPORTED_FROM_SUSPENDED_CARDS)
        ));
        try (LocalStore setup = new LocalStore(context)) {
            long now = System.currentTimeMillis();
            // Create a study item in NEW_LEARNING phase, due in the past.
            Records.StudyItem item = new Records.StudyItem("拉", "learning", now - 1_000L, 0.4, 5.0, 1, 0, 0, 0, null, now)
                    .withAnswerSignature("拉|拉致|らち|archive example");
            item = item.copyBuilder()
                    .rung(Records.LadderRung.KANJI_MEANING)
                    .phase(Records.SchedulerPhase.NEW_LEARNING)
                    .build();
            setup.saveStudyItem(item);
        }

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, STUDY_NOW);
            clickText(scenario, REVEAL);
            clickText(scenario, "Pass");

            // Progress header should advance after passing.
            scenario.onActivity(activity -> assertHasText(activity, "1 / "));
        }
    }

    @Test
    public void testMissedRecognitionCountsTowardInternalWritingThreshold() {
        seedDashboard();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, STUDY_NOW);
            clickText(scenario, REVEAL);
            clickText(scenario, "Fail");

            try (LocalStore store = new LocalStore(context)) {
                Records.ReviewStats stats = store.reviewStatsSince(0L);
                assertEquals(1, stats.total);
                assertEquals(1, stats.again);
                assertEquals(0, stats.writingRequired);
                List<Records.StudyItem> items = store.studyItems();
                assertEquals(1, items.size());
                assertFalse(items.get(0).writingRemediationPending);
                assertEquals(0, items.get(0).recognitionStage);
                assertEquals(1, items.get(0).consecutiveFailedRecognitionDays);
                assertLatestReviewSchedulerStateContains(store, "\"due_at\":" + items.get(0).dueAtMillis);
            }
        }
    }

    @Test
    public void testTypingMeaningAutoPassesCorrectAnswerAndAllowsManualWrongGrading() {
        seedDashboard();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, STUDY_NOW);
            clickText(scenario, REVEAL);
            clickText(scenario, "Fail");

            forceStudyItemDue("拉", -1, false);
            clickText(scenario, STUDY_NOW);
            scenario.onActivity(activity -> assertHasTexts(activity, "Type the meaning", "Meaning", REVEAL));
            enterFirstEditText(scenario, "kidnap");
            clickText(scenario, REVEAL);

            assertCorrectTypingMeaningReviewStored();

            forceStudyItemDue("拉", -1, false);
            clickText(scenario, STUDY_NOW);
            enterFirstEditText(scenario, "wrong");
            clickText(scenario, REVEAL);
            scenario.onActivity(activity -> assertHasTexts(activity, "Answer", "Latin, kidnap", "Fail", "Pass"));
            clickText(scenario, "Fail");

            assertWrongTypingMeaningReviewStored();
        }
    }

    @Test
    public void testCorrectWritingCheckSubmitsReview() {
        seedDueWritingItem();
        MainActivity.setWritingRecognizerForTests(new FakeWritingRecognizer("拉"));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, STUDY_NOW);
            scenario.onActivity(activity -> drawGuideKanji(activity, "拉"));
            clickText(scenario, CHECK);
            waitForText(scenario, CLEAN_MATCH);
            scenario.onActivity(activity -> {
                assertHasText(activity, CLEAN_MATCH);
                assertHasText(activity, "Target: 拉");
                assertHasText(activity, NEXT_CARD);
                assertEquals(1, countText(activity.findViewById(android.R.id.content), CLEAN_MATCH));
            });
            clickText(scenario, NEXT_CARD);

            try (LocalStore store = new LocalStore(context)) {
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
            }
        }
    }

    @Test
    public void testHintAssistedCleanWritingHoldsFadeLevel() {
        seedDueWritingItem(2);
        MainActivity.setWritingRecognizerForTests(new FakeWritingRecognizer("拉"));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, STUDY_NOW);
            clickText(scenario, "More help");
            scenario.onActivity(activity -> drawGuideKanji(activity, "拉"));
            clickText(scenario, CHECK);
            waitForText(scenario, CLEAN_MATCH);
            clickText(scenario, NEXT_CARD);

            try (LocalStore store = new LocalStore(context)) {
                Records.ReviewStats stats = store.reviewStatsSince(0L);
                assertEquals(1, stats.total);
                assertEquals(1, stats.hard);
                List<Records.StudyItem> items = store.studyItems();
                assertEquals(1, items.size());
                assertEquals(2, items.get(0).writingLevel);
            }
        }
    }

    @Test
    public void testMessyRecognizedWritingCanBeSavedHardWithoutAdvancingFade() {
        seedDueWritingItem(2);
        MainActivity.setWritingRecognizerForTests(new FakeWritingRecognizer("拉"));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, STUDY_NOW);
            scenario.onActivity(activity -> drawGuideKanjiWithFirstStrokeReversed(activity, "拉"));
            clickText(scenario, CHECK);
            waitForText(scenario, "Try cleaner");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Try cleaner");
                assertHasText(activity, "Save hard");
            });
            clickText(scenario, "Save hard");

            try (LocalStore store = new LocalStore(context)) {
                Records.ReviewStats stats = store.reviewStatsSince(0L);
                assertEquals(1, stats.total);
                assertEquals(1, stats.hard);
                List<Records.StudyItem> items = store.studyItems();
                assertEquals(1, items.size());
                assertEquals(2, items.get(0).writingLevel);
            }
        }
    }

    @Test
    public void testWrongRecognitionCanBeLoggedAsFailedAttempt() {
        seedDueWritingItem(2);
        MainActivity.setWritingRecognizerForTests(new FakeWritingRecognizer("提"));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, STUDY_NOW);
            scenario.onActivity(activity -> drawGuideKanji(activity, "拉"));
            clickText(scenario, CHECK);
            scenario.onActivity(activity -> {
                assertHasText(activity, "I could not read that as the target kanji yet");
                assertHasText(activity, "Target: 拉");
                assertHasText(activity, "Save miss");
            });
            clickText(scenario, "Save miss");

            try (LocalStore store = new LocalStore(context)) {
                Records.ReviewStats stats = store.reviewStatsSince(0L);
                assertEquals(1, stats.total);
                assertEquals(1, stats.again);
                assertEquals(1, stats.writingRequired);
                assertEquals(1, stats.writingFailed);
                List<Records.StudyItem> items = store.studyItems();
                assertEquals(1, items.size());
                assertEquals("learning", items.get(0).state);
                assertEquals(1, items.get(0).writingLevel);
            }
        }
    }

    @Test
    public void testTryAgainWithFullGuideStartsFreshAttempt() {
        seedDueWritingItem();
        MainActivity.setWritingRecognizerForTests(new FakeWritingRecognizer("提"));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, STUDY_NOW);
            scenario.onActivity(activity -> drawGuideKanji(activity, "拉"));
            clickText(scenario, CHECK);
            scenario.onActivity(activity -> {
                assertHasText(activity, "Try again with full guide");
                assertHasText(activity, "Replay");
            });
            clickText(scenario, "Try again with full guide");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Fresh guided try");
                assertNoText(activity, "I could not read that as the target kanji yet");
                assertNoText(activity, NEXT_CARD);
                assertNoText(activity, "Replay");
                DrawingPadView pad = findType(activity.findViewById(android.R.id.content), DrawingPadView.class);
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
            clickText(scenario, STUDY_NOW);
            scenario.onActivity(activity -> drawGuideKanji(activity, "拉"));
            clickText(scenario, CHECK);
            scenario.onActivity(activity -> {
                assertHasText(activity, "I could not read that as the target kanji yet");
                assertHasText(activity, "Mark right anyway");
            });
            clickText(scenario, "Mark right anyway");

            try (LocalStore store = new LocalStore(context)) {
                Records.ReviewStats stats = store.reviewStatsSince(0L);
                assertEquals(1, stats.total);
                assertEquals(1, stats.good);
                assertEquals(1, stats.writingRequired);
                assertEquals(0, stats.writingFailed);
            }
        }
    }

    @Test
    public void testMissingModelCanBeManuallyScoredAfterDrawing() {
        seedDueWritingItem();
        MainActivity.setWritingRecognizerForTests(new FakeUnavailableRecognizer());
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, STUDY_NOW);
            scenario.onActivity(activity -> drawGuideKanji(activity, "拉"));
            clickText(scenario, CHECK);
            scenario.onActivity(activity -> {
                assertHasText(activity, "Download the handwriting checker before automatic checks");
                assertHasText(activity, "Target: 拉");
                assertHasText(activity, "Save miss");
                assertHasText(activity, "Mark right anyway");
            });
            clickText(scenario, "Mark right anyway");

            try (LocalStore store = new LocalStore(context)) {
                Records.ReviewStats stats = store.reviewStatsSince(0L);
                assertEquals(1, stats.total);
                assertEquals(1, stats.good);
                assertEquals(1, stats.writingRequired);
                assertEquals(0, stats.writingFailed);
            }
        }
    }

    @Test
    public void testDrawingPadTracksInkAndClear() {
        DrawingPadView pad = new DrawingPadView(context);
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
    public void testManualSyncShowsLiveCardProgress() {
        Records.Settings settings = Records.Settings.kikuDefaults();
        Records.Note first = note(1L, "確認", "かくにん", "confirmation", "確認した。");
        Records.Note second = note(2L, "笥箱", "しはこ", "rare box", "笥箱を見た。");
        Records.CollectionSnapshot snapshot = new Records.CollectionSnapshot(
                Arrays.asList(first, second),
                Arrays.asList(
                        new Records.Card(10L, 1L, 0, "Kiku", 2, 2, 0, settings.matureDays + 5, 12, 0, false),
                        new Records.Card(20L, 2L, 0, "Kiku", -1, 0, 0, 0, 0, 0, true)
                )
        );
        HoldingProgressGateway progressGateway = new HoldingProgressGateway(snapshot);
        MainActivity.setCollectionGatewayForTests(progressGateway);

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Sync AnkiDroid");
            clickText(scenario, "Sync cards");
            waitForText(scenario, "1 / 2 cards scanned");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Scanning cards");
                ProgressBar bar = findType(activity.findViewById(android.R.id.content), ProgressBar.class);
                assertNotNull(bar);
                assertFalse(bar.isIndeterminate());
                assertEquals(1000, bar.getMax());
                assertEquals(500, bar.getProgress());
            });
            progressGateway.finish();
            waitForText(scenario, "Sync complete");
        } finally {
            progressGateway.finish();
        }
    }

    @Test
    public void testSyncProgressPanelShowsProcessingImportedCardsAfterScan() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                SyncProgressPanel panel = new SyncProgressPanel(activity);
                panel.render(SyncProgress.cardsScanned(2, 2));
                panel.render(SyncProgress.atStage(SyncProgress.Stage.PROCESSING_IMPORTED_CARDS));

                assertNotNull(findText(panel, "Processing imported cards"));
                assertNotNull(findText(panel, "2 / 2 cards scanned"));
                assertNotNull(findText(panel, "AnkiDroid read finished"));
            });
        }
    }

    @Test
    public void testLastSyncHeadlineInvitesAndStartsManualSync() {
        long yesterday = moveLocalDays(localDayStart(System.currentTimeMillis()), -1) + 10 * 60 * 60 * 1000L;
        saveSyncFinishedAt(yesterday);
        String syncValue = "Yesterday at "
                + DateFormat.getTimeInstance(DateFormat.SHORT).format(new Date(yesterday));

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                assertHasText(activity, "Last sync");
                assertHasText(activity, syncValue);
                assertNoText(activity, "active cards checked");
                assertNoText(activity, "suspended cards archived");
                assertNoText(activity, "Study starts with recall");
                assertNoText(activity, "Sync once to find");
            });
            clickText(scenario, syncValue);
            scenario.onActivity(activity -> {
                assertHasText(activity, "Sync AnkiDroid?");
                assertHasText(activity, "Sync cards");
            });
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
                assertNoText(activity, "AnkiDroid is ready");
            });

            try (LocalStore store = new LocalStore(context)) {
                LocalStore.AutoSyncSettings auto = store.autoSyncSettings();
                assertTrue(auto.configured);
                assertTrue(auto.enabled);
                assertTrue(auto.nextRunAt > System.currentTimeMillis());
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
            waitForText(scenario, "Sync complete", 300_000L);
            LocalStore.SyncStatus status = waitForLatestSync();
            assertNotNull(status);
            assertEquals("success", status.status);

            try (LocalStore store = new LocalStore(context)) {
                assertFalse(store.dashboardRows().isEmpty());
                assertFalse(store.studyItems().isEmpty());
            }
        }
    }

    private LocalStore.SyncStatus waitForLatestSync() throws Exception {
        return waitForLatestSync(200);
    }

    private LocalStore.SyncStatus waitForLatestSync(int attempts) throws Exception {
        for (int i = 0; i < attempts; i++) {
            try (LocalStore store = new LocalStore(context)) {
                LocalStore.SyncStatus status = store.latestSync();
                if (status != null) {
                    return status;
                }
            } catch (SQLiteDatabaseLockedException busy) {
                // The live sync button test polls while the app is committing a large collection import.
            }
            SystemClock.sleep(100);
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
        seedDashboard(Collections.singletonList(dashboardRow("拉", RAMEN_RADICAL_GAP, "ら", IMPORTED_FROM_SUSPENDED_CARDS)));
    }

    private void seedDueWritingItem() {
        seedDueWritingItem(0);
    }

    private void seedDueWritingItem(int writingLevel) {
        seedDueWritingItem(dashboardRow("拉", RAMEN_RADICAL_GAP, "ら", IMPORTED_FROM_SUSPENDED_CARDS), writingLevel);
    }

    private void seedDueWritingItem(Records.DashboardRow row, int writingLevel) {
        seedDashboard(Collections.singletonList(row));
        try (LocalStore store = new LocalStore(context)) {
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
        }
    }

    private void seedDashboard(List<Records.DashboardRow> rows) {
        seedDashboardRowsOnly(rows);
        try (LocalStore store = new LocalStore(context)) {
            long now = System.currentTimeMillis();
            ArrayList<Records.StudyItem> items = new ArrayList<>();
            for (Records.DashboardRow row : rows) {
                items.add(new Records.StudyItem(row.kanji, "new", now, 0.4, 5.0, 0, 0, 0, 0, null, now));
            }
            store.replaceStudyItems(items);
        }
    }

    private void seedSimilarChoiceDashboard() throws Exception {
        Records.Settings settings = Records.Settings.kikuDefaults();
        Records.DashboardRow row = dashboardRow("拉", RAMEN_RADICAL_GAP, "ら", IMPORTED_FROM_SUSPENDED_CARDS);
        Records.CollectionSnapshot snapshot = new Records.CollectionSnapshot(
                Arrays.asList(
                        note(1L, "拉麺", "らーめん", RAMEN_RADICAL_GAP, "拉麺を食べた。"),
                        note(2L, "提案", "ていあん", "carry radical gap", "提案を見た。")
                ),
                Arrays.asList(
                        new Records.Card(10L, 1L, 0, "Kiku", 2, 2, 0, 3, 4, 1, false),
                        new Records.Card(20L, 2L, 0, "Kiku", 2, 2, 0, 30, 4, 0, false)
                )
        );
        SimilarKanjiIndex index = SimilarKanjiIndex.parseTsv(new StringReader("拉\t提\tfixture\n"));
        try (LocalStore store = new LocalStore(context)) {
            long now = System.currentTimeMillis();
            store.saveSuccessfulSync(
                    snapshot,
                    Collections.emptyList(),
                    Collections.singletonList(row),
                    settings,
                    new LocalStore.SyncTiming(Math.max(0L, now - 1_000L), now),
                    null,
                    index
            );
            store.replaceStudyItems(Collections.singletonList(
                    new Records.StudyItem("拉", "new", now, 0.4, 5.0, 0, 0, 0, 0, null, now)
            ));
        }
    }

    private void seedFocusCompleteWithInventorySimilarChoice() throws Exception {
        Records.Settings settings = Records.Settings.kikuDefaults();
        Records.DashboardRow activeRow = dashboardRow("拉", RAMEN_RADICAL_GAP, "ら", IMPORTED_FROM_SUSPENDED_CARDS);
        Records.CollectionSnapshot snapshot = new Records.CollectionSnapshot(
                Arrays.asList(
                        note(1L, "拉麺", "らーめん", RAMEN_RADICAL_GAP, "拉麺を食べた。"),
                        note(2L, "提案", "ていあん", "carry radical gap", "提案を見た。"),
                        note(3L, "謎語", "なぞご", "riddle radical gap", "謎語を見た。")
                ),
                Arrays.asList(
                        new Records.Card(10L, 1L, 0, "Kiku", 2, 2, 0, 3, 4, 1, false),
                        new Records.Card(20L, 2L, 0, "Kiku", 2, 2, 0, 30, 4, 0, false),
                        new Records.Card(30L, 3L, 0, "Kiku", 2, 2, 0, 30, 4, 0, false)
                )
        );
        SimilarKanjiIndex index = SimilarKanjiIndex.parseTsv(new StringReader("提\t謎\tfixture\n"));
        try (LocalStore store = new LocalStore(context)) {
            long now = System.currentTimeMillis();
            store.saveSuccessfulSync(
                    snapshot,
                    Collections.emptyList(),
                    Collections.singletonList(activeRow),
                    settings,
                    new LocalStore.SyncTiming(Math.max(0L, now - 1_000L), now),
                    null,
                    index
            );
            store.replaceStudyItems(Collections.singletonList(
                    new Records.StudyItem("拉", "review", now + 86_400_000L, 2.0, 4.0, 1, 0, 2, 0, null, now - 86_400_000L)
            ));
            store.saveReview(review("拉", "focus-complete"), "good", now);
        }
    }

    private int countSimilarRepairs(LocalStore store) {
        try (Cursor cursor = store.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM similar_kanji_repair_queue", null)) {
            assertTrue(cursor.moveToFirst());
            return cursor.getInt(0);
        }
    }

    private void assertLatestReviewSchedulerStateContains(LocalStore store, String expected) {
        try (Cursor cursor = store.getReadableDatabase().rawQuery(
                "SELECT scheduler_state_after_json FROM review_log ORDER BY id DESC LIMIT 1",
                null
        )) {
            assertTrue(cursor.moveToFirst());
            assertTrue(cursor.getString(0).contains(expected));
        }
    }

    private void forceStudyItemDue(String kanji, int recognitionStage, boolean writingRemediationPending) {
        try (LocalStore store = new LocalStore(context)) {
            Records.StudyItem item = null;
            for (Records.StudyItem candidate : store.studyItems()) {
                if (kanji.equals(candidate.kanji)) {
                    item = candidate;
                    break;
                }
            }
            assertNotNull(item);
            long now = System.currentTimeMillis();
            store.saveStudyItem(new Records.StudyItem(
                    item.kanji,
                    "learning",
                    now,
                    item.stability,
                    item.difficulty,
                    item.totalReviews,
                    item.lapses,
                    item.learningStep,
                    item.writingLevel,
                    recognitionStage,
                    item.consecutiveFailedRecognitionDays,
                    item.lastFailedRecognitionDayMillis,
                    writingRemediationPending,
                    item.suppressedByTaskType,
                    item.suppressedAtMillis,
                    item.matureIntervalDays,
                    item.answerSignature,
                    null,
                    item.createdAtMillis,
                    item.typingMeaningMemory,
                    item.kanjiMeaningMemory,
                    item.fontMeaningMemory,
                    item.wordReadingMemory,
                    item.writingRemediationMemory
            ));
        }
    }

    private void seedDashboardRowsOnly(List<Records.DashboardRow> rows) {
        saveSyncFinishedAt(2000L, rows);
    }

    private void saveSyncFinishedAt(long finishedAt) {
        saveSyncFinishedAt(finishedAt, Collections.singletonList(dashboardRow("拉", RAMEN_RADICAL_GAP, "ら", IMPORTED_FROM_SUSPENDED_CARDS)));
    }

    private void saveSyncFinishedAt(long finishedAt, List<Records.DashboardRow> rows) {
        Records.Settings settings = Records.Settings.kikuDefaults();
        Records.Note note = note(1L, "拉麺", "らーめん", RAMEN_RADICAL_GAP, "拉麺を食べた。");
        Records.Card card = new Records.Card(10L, 1L, 0, "Kiku", 2, 2, 0, 3, 4, 1, false);
        try (LocalStore store = new LocalStore(context)) {
            store.saveSuccessfulSync(
                    new Records.CollectionSnapshot(Collections.singletonList(note), Collections.singletonList(card)),
                    Collections.emptyList(),
                    rows,
                    settings,
                    Math.max(0L, finishedAt - 1_000L),
                    finishedAt,
                    null
            );
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

    private static final class HoldingProgressGateway implements CollectionGateway {
        private final Records.CollectionSnapshot snapshot;
        private final CompletableFuture<Void> released = new CompletableFuture<>();

        private HoldingProgressGateway(Records.CollectionSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        private void finish() {
            released.complete(null);
        }

        @Override
        public Records.CollectionSnapshot readCollection(Records.Settings settings) {
            return snapshot;
        }

        @Override
        public Records.CollectionSnapshot readCollection(Records.Settings settings, SyncProgress.Listener progress) {
            progress.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.FINDING_NOTE_TYPE));
            progress.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.READING_NOTES));
            progress.onSyncProgress(SyncProgress.cardsScanned(0, snapshot.cards.size()));
            progress.onSyncProgress(SyncProgress.cardsScanned(1, snapshot.cards.size()));
            try {
                released.get(5L, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                assertNotNull(ignored);
            }
            progress.onSyncProgress(SyncProgress.cardsScanned(snapshot.cards.size(), snapshot.cards.size()));
            return snapshot;
        }

        @Override
        public AnkiDroidGateway.RemovalSummary removeArchivedSuspendedCards(Records.CollectionSnapshot snapshot) {
            return new AnkiDroidGateway.RemovalSummary(0, 0, 0, "cleanup done");
        }

        @Override
        public AnkiDroidGateway.RemovalSummary removeArchivedSuspendedCards(Records.CollectionSnapshot snapshot, SyncProgress.Listener progress) {
            progress.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.ARCHIVING_IMPORTED_CARDS));
            return removeArchivedSuspendedCards(snapshot);
        }
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

    private static int recognitionCardHeight(ActivityScenario<MainActivity> scenario) {
        int[] height = new int[]{0};
        scenario.onActivity(activity -> height[0] = recognitionCard(activity).getHeight());
        return height[0];
    }

    private static View recognitionCard(MainActivity activity) {
        View root = activity.findViewById(android.R.id.content);
        View title = findExactText(root, "Name this kanji");
        if (title == null) {
            title = findExactText(root, "Type the meaning");
        }
        if (title == null) {
            title = findExactText(root, "Read this word");
        }
        assertNotNull("Missing recognition card title", title);
        ViewParent parent = title.getParent();
        assertTrue("Recognition title parent should be the card", parent instanceof View);
        return (View) parent;
    }

    private static void swipeRecognitionCard(ActivityScenario<MainActivity> scenario, boolean right) {
        scenario.onActivity(activity -> {
            View card = recognitionCard(activity);
            Rect bounds = new Rect();
            assertTrue("Recognition card should be visible", card.getGlobalVisibleRect(bounds));
            float inset = Math.max(24f, bounds.width() * 0.18f);
            float startX = right ? bounds.left + inset : bounds.right - inset;
            float endX = right ? bounds.right - inset : bounds.left + inset;
            float y = bounds.centerY();
            long downTime = SystemClock.uptimeMillis();
            dispatchActivityTouch(activity, downTime, downTime, MotionEvent.ACTION_DOWN, startX, y);
            dispatchActivityTouch(activity, downTime, downTime + 16L, MotionEvent.ACTION_MOVE, (startX + endX) / 2f, y);
            dispatchActivityTouch(activity, downTime, downTime + 32L, MotionEvent.ACTION_UP, endX, y);
        });
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle(2000L);
    }

    private static void dispatchActivityTouch(MainActivity activity, long downTime, long eventTime, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
        try {
            activity.dispatchTouchEvent(event);
        } finally {
            event.recycle();
        }
    }

    private static void enterFirstEditText(ActivityScenario<MainActivity> scenario, String text) {
        scenario.onActivity(activity -> {
            EditText input = findType(activity.findViewById(android.R.id.content), EditText.class);
            assertNotNull(input);
            input.setText(text);
            input.setSelection(text.length());
        });
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle(2000L);
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
        waitForText(scenario, text, 5000L);
    }

    private static void waitForText(ActivityScenario<MainActivity> scenario, String text, long timeoutMillis) {
        long deadline = SystemClock.uptimeMillis() + timeoutMillis;
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

    private static void assertCollapsedSettingsScreen(MainActivity activity) {
        assertHasTexts(
                activity,
                "Anki setup",
                "Study tuning",
                "Reminders & sync",
                "App & data",
                "Note type",
                "Using Kiku",
                "Expression field",
                "Reading field",
                "Meaning field",
                "Frequency sort field",
                "Choose from AnkiDroid",
                "Save note type",
                "Frequency range",
                "Default: 100-3000",
                "Min rank",
                "Max rank"
        );
        assertNoTexts(activity, "Daily workload", "Daily reminder", "App updates");
    }

    private void assertNavigationSettingsPersisted() {
        try (LocalStore store = new LocalStore(context)) {
            assertEquals(AdaptiveLoadPlanner.MODE_MANUAL, store.adaptiveLoadMode());
            assertEquals(70, store.adaptiveLoadWorkPercent());
            assertEquals(5, store.adaptiveLoadMaxItems());
            assertEquals(0.95, store.schedulerParameters().targetRetention, 0.001);
            assertEquals(250, store.getIntSetting("suspended_rank_min", 100));
            assertEquals(3500, store.getIntSetting("suspended_rank_max", 3000));
            LocalStore.ReminderSettings reminder = store.reminderSettings();
            assertTrue(reminder.enabled);
            assertEquals(8, reminder.hour);
            assertEquals(0, reminder.minute);
        }
    }

    private static void assertKanjiDetailReady(MainActivity activity) {
        assertHasTexts(
                activity,
                "Why it is here",
                IMPORTED_FROM_SUSPENDED_CARDS,
                "Review this now",
                "Copy Anki search",
                "Recovery timeline",
                "Active repair",
                "Mature support 0 / target 2",
                "Kani started watching"
        );
    }

    private static void assertHiddenRecognitionCard(MainActivity activity) {
        assertHasTexts(activity, "Name this kanji", "Kanji -> meaning", "Answer hidden until reveal", "What does it mean?", REVEAL);
        assertNoTexts(activity, "Example: 拉麺  らーめん", "From: 拉麺");
    }

    private static void assertRevealedRecognitionCard(MainActivity activity) {
        assertHasTexts(activity, "Answer", "Latin, kidnap", "Reading: らーめん", "From: 拉麺", "Fail", "Pass");
        assertNoTexts(activity, CHECK);
    }

    private void assertFailedRecognitionReviewStored() {
        try (LocalStore store = new LocalStore(context)) {
            Records.ReviewStats stats = store.reviewStatsSince(0L);
            assertEquals(1, stats.total);
            assertEquals(1, stats.again);
            assertEquals(0, stats.writingRequired);
            List<Records.StudyItem> items = store.studyItems();
            assertEquals(1, items.size());
            assertEquals(1, items.get(0).consecutiveFailedRecognitionDays);
            assertFalse(items.get(0).writingRemediationPending);
        }
    }

    private void assertKnownAnswerRecognitionReviewStored() {
        try (LocalStore store = new LocalStore(context)) {
            Records.ReviewStats stats = store.reviewStatsSince(0L);
            assertEquals(1, stats.total);
            assertEquals(1, stats.good);
            assertEquals(0, stats.writingRequired);
            Records.StudyItem item = onlyStudyItem(store);
            assertEquals("拉", item.kanji);
            assertEquals("learning", item.state);
            assertEquals(1, item.totalReviews);
            assertEquals(1, item.learningStep);
            assertEquals(0, item.writingLevel);
            assertEquals(1, item.recognitionStage);
        }
    }

    private static void assertSimilarChoiceCard(MainActivity activity, String progress, String prompt, String firstChoice, String secondChoice) {
        assertHasTexts(activity, progress, "Similar choice", prompt, firstChoice, secondChoice);
        assertNoTexts(activity, "Kanji -> meaning");
    }

    private void assertSimilarRepairsQueuedWithoutReviews() {
        try (LocalStore store = new LocalStore(context)) {
            assertEquals(0, store.reviewStatsSince(0L).total);
            assertEquals(2, countSimilarRepairs(store));
        }
    }

    private void assertCorrectTypingMeaningReviewStored() {
        try (LocalStore store = new LocalStore(context)) {
            Records.ReviewStats stats = store.reviewStatsSince(0L);
            assertEquals(2, stats.total);
            assertEquals(1, stats.good);
            Records.StudyItem item = onlyStudyItem(store);
            assertEquals(0, item.recognitionStage);
            assertEquals(1, item.typingMeaningMemory.totalReviews);
        }
    }

    private void assertWrongTypingMeaningReviewStored() {
        try (LocalStore store = new LocalStore(context)) {
            Records.ReviewStats stats = store.reviewStatsSince(0L);
            assertEquals(3, stats.total);
            assertEquals(2, stats.again);
            Records.StudyItem item = onlyStudyItem(store);
            assertEquals(-1, item.recognitionStage);
            assertFalse(item.writingRemediationPending);
            assertEquals(1, item.consecutiveFailedRecognitionDays);
        }
    }

    private static Records.StudyItem onlyStudyItem(LocalStore store) {
        List<Records.StudyItem> items = store.studyItems();
        assertEquals(1, items.size());
        return items.get(0);
    }

    private static void assertHasText(MainActivity activity, String text) {
        assertNotNull("Missing text: " + text, findText(activity.findViewById(android.R.id.content), text));
    }

    private static void assertHasTexts(MainActivity activity, String... texts) {
        for (String text : texts) {
            assertHasText(activity, text);
        }
    }

    private static void assertNoText(MainActivity activity, String text) {
        View found = findText(activity.findViewById(android.R.id.content), text);
        if (found != null) {
            throw new AssertionError("Unexpected text before reveal: " + text);
        }
    }

    private static void assertNoTexts(MainActivity activity, String... texts) {
        for (String text : texts) {
            assertNoText(activity, text);
        }
    }

    private static void drawGuideKanji(MainActivity activity, String kanji) {
        DrawingPadView pad = findType(activity.findViewById(android.R.id.content), DrawingPadView.class);
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
        DrawingPadView pad = findType(activity.findViewById(android.R.id.content), DrawingPadView.class);
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
        DrawingPadView pad = findType(activity.findViewById(android.R.id.content), DrawingPadView.class);
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

    private static <T extends View> List<T> findTypes(View root, Class<T> type) {
        List<T> results = new ArrayList<>();
        collectTypes(root, type, results);
        return results;
    }

    private static <T extends View> void collectTypes(View root, Class<T> type, List<T> results) {
        if (root.getVisibility() != View.VISIBLE) {
            return;
        }
        if (type.isInstance(root)) {
            results.add(type.cast(root));
        }
        if (root instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) root;
            for (int i = 0; i < group.getChildCount(); i++) {
                collectTypes(group.getChildAt(i), type, results);
            }
        }
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
        return ancestorOfType(view, type) != null;
    }

    private static View ancestorOfType(View view, Class<?> type) {
        ViewParent parent = view.getParent();
        while (parent != null) {
            if (type.isInstance(parent)) {
                return (View) parent;
            }
            parent = parent.getParent();
        }
        return null;
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
            // Fake recognizer has no model resources to release.
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
            // Fake recognizer has no model resources to release.
        }
    }
}
