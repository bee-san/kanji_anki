package dev.bee.kanjianki;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.database.sqlite.SQLiteDatabaseLockedException;
import android.graphics.Rect;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
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
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
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

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("kanji_anki_simple.db");
        MainActivity.setAnkiDroidGatewayForTests(AnkiDroidGateway.testProvider(context, "dev.bee.kanjianki.no_anki_for_tests"));
        MainActivity.setWritingRecognizerForTests(null);
    }

    @After
    public void tearDown() {
        MainActivity.setAnkiDroidGatewayForTests(null);
        MainActivity.setWritingRecognizerForTests(null);
        context.deleteDatabase("kanji_anki_simple.db");
    }

    @Test
    public void testLaunchesHomeWithoutSeedDataOrProviderCrash() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                View content = activity.findViewById(android.R.id.content);
                assertNotNull(content);
                assertTrue(content.getWidth() >= 0);
                assertTrue(content.getHeight() >= 0);
                assertHasText(activity, "Kanji Anki");
                assertHasText(activity, "Sync AnkiDroid");
            });
        }
    }

    @Test
    public void testNavigationSettingsAndEmptyStates() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Nothing to write yet");
                assertHasText(activity, "Sync from AnkiDroid first");
            });

            clickText(scenario, "Queue");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Practice queue");
                assertHasText(activity, "No queued kanji yet");
            });

            clickText(scenario, "Settings");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Rarity cutoff");
                assertHasText(activity, "Default: 3000");
            });
            clickText(scenario, "4000");
            clickText(scenario, "Save cutoff");

            LocalStore store = new LocalStore(context);
            try {
                assertEquals(4000, store.getIntSetting("suspended_rank_cutoff", 3000));
            } finally {
                store.close();
            }

            clickText(scenario, "Update");
            scenario.onActivity(activity -> {
                assertHasText(activity, "GitHub updater");
                assertHasText(activity, "Current version");
                assertHasText(activity, "Check for update");
            });
        }
    }

    @Test
    public void testKanjiDetailCopyAndStudyReviewFlow() {
        seedDashboard();
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Queue");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Practice queue");
                assertHasText(activity, "1 active kanji");
                assertHasText(activity, "ramen radical gap");
                assertHasText(activity, "From 拉麺");
            });

            clickText(scenario, "拉");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Why it is here");
                assertHasText(activity, "Imported from suspended cards");
                assertHasText(activity, "Review this now");
                assertHasText(activity, "Copy Anki search");
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
                assertHasText(activity, "Draw this kanji");
                assertHasText(activity, "Focused practice");
                assertHasText(activity, "Learn it from the reference");
                assertHasText(activity, "Reference");
                assertHasText(activity, "Meaning: ramen radical gap");
                assertHasText(activity, "Reading: ら");
                assertHasText(activity, "Example: 拉麺  らーめん");
                assertHasText(activity, "Trace the numbered strokes");
                assertHasText(activity, "Check");
                assertHasText(activity, "拉麺");
                assertNoText(activity, "I copied it");
            });

            clickText(scenario, "Check");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Write in the square before checking");
                assertNoText(activity, "Mark right anyway");
                assertNoText(activity, "Next card");
            });

            LocalStore store = new LocalStore(context);
            try {
                Records.ReviewStats stats = store.reviewStatsSince(0L);
                assertEquals(0, stats.total);
            } finally {
                store.close();
            }
        }
    }

    @Test
    public void testQueueShowsActivePracticeKanjiNotEveryCandidate() {
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
            clickText(scenario, "Queue");
            scenario.onActivity(activity -> {
                assertHasText(activity, "1 active kanji");
                assertHasText(activity, "ramen radical gap");
                assertHasText(activity, "From 拉麺");
                assertNoText(activity, "mystery unused");
                assertNoText(activity, "謎");
            });
        }
    }

    @Test
    public void testBrowsingQueueDoesNotAdmitNewStudyItems() {
        seedDashboardRowsOnly(Collections.singletonList(dashboardRow("拉", "ramen radical gap", "ら", "Imported from suspended cards")));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Queue");
            scenario.onActivity(activity -> {
                assertHasText(activity, "0 active kanji");
                assertHasText(activity, "1 candidate waiting to join later");
                assertHasText(activity, "Learn next problem kanji");
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
    public void testLearnNextProblemKanjiFromQueueAdmitsStudyItem() {
        seedDashboardRowsOnly(Collections.singletonList(dashboardRow("拉", "ramen radical gap", "ら", "Imported from suspended cards")));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Queue");
            clickText(scenario, "Learn next problem kanji");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Draw this kanji");
                assertHasText(activity, "New problem kanji");
                assertHasText(activity, "ramen radical gap");
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
    public void testQueueOrderMatchesReviewFirstStudySelection() {
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
            clickText(scenario, "Queue");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Review due now");
                assertTrue(textTop(activity, "mystery radical gap") < textTop(activity, "ramen radical gap"));
            });
            clickText(scenario, "Review due now");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Memory check");
                assertHasText(activity, "Prompt: mystery radical gap");
            });
        }
    }

    @Test
    public void testMissingStrokeGuideIsExplainedBeforeDrawing() {
        seedDashboard(Collections.singletonList(dashboardRow("鿃", "rare shape", "ソウ", "Imported from suspended cards")));
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
    public void testReviewStartsBlindAndHintDoesNotRevealReference() {
        seedDashboard();
        LocalStore store = new LocalStore(context);
        try {
            store.replaceStudyItems(Collections.singletonList(
                    new Records.StudyItem("拉", "review", 0L, 1.8, 4.8, 2, 0, 2, 2, null, 0L)
            ));
        } finally {
            store.close();
        }

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Memory check");
                assertHasText(activity, "Write from memory");
                assertHasText(activity, "Hint");
                assertNoText(activity, "Reference");
                assertNoText(activity, "拉麺");
            });

            scenario.onActivity(activity -> drawGuideKanji(activity, "拉"));
            clickText(scenario, "Hint");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Hint used");
                assertHasText(activity, "ink stayed on the canvas");
                assertHasText(activity, "current stroke hinted");
                MainActivity.DrawingPadView pad = findType(activity.findViewById(android.R.id.content), MainActivity.DrawingPadView.class);
                assertNotNull(pad);
                assertTrue(pad.hasInk());
                assertNoText(activity, "Reference");
                assertNoText(activity, "拉麺");
            });
        }
    }

    @Test
    public void testBlindReviewCheckDoesNotInsertReferenceAboveCanvas() {
        seedDashboard();
        MainActivity.setWritingRecognizerForTests(new FakeWritingRecognizer("拉"));
        LocalStore store = new LocalStore(context);
        try {
            store.replaceStudyItems(Collections.singletonList(
                    new Records.StudyItem("拉", "review", 0L, 1.8, 4.8, 2, 0, 2, 3, null, 0L)
            ));
        } finally {
            store.close();
        }

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Memory check");
                assertNoText(activity, "Reference");
                drawGuideKanji(activity, "拉");
            });
            clickText(scenario, "Check");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Clean match");
                assertHasText(activity, "Target: 拉");
                assertHasText(activity, "Next card");
                assertNoText(activity, "Reference");
                assertEquals(1, countText(activity.findViewById(android.R.id.content), "Clean match"));
            });
        }
    }

    @Test
    public void testStudyLoopActionsArePinnedOutsideScrollableContent() {
        seedDashboard();
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
    public void testCorrectWritingCheckSubmitsReview() {
        seedDashboard();
        MainActivity.setWritingRecognizerForTests(new FakeWritingRecognizer("拉"));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study");
            scenario.onActivity(activity -> drawGuideKanji(activity, "拉"));
            clickText(scenario, "Check");
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
                assertEquals(1, stats.good);
                assertEquals(1, stats.writingRequired);
                assertEquals(0, stats.writingFailed);
            } finally {
                store.close();
            }
        }
    }

    @Test
    public void testWrongRecognitionCanBeLoggedAsFailedAttempt() {
        seedDashboard();
        MainActivity.setWritingRecognizerForTests(new FakeWritingRecognizer("提"));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study");
            scenario.onActivity(activity -> drawGuideKanji(activity, "拉"));
            clickText(scenario, "Check");
            scenario.onActivity(activity -> {
                assertHasText(activity, "I could not read that as the target kanji yet");
                assertHasText(activity, "Target: 拉");
                assertHasText(activity, "Next card");
            });
            clickText(scenario, "Next card");

            LocalStore store = new LocalStore(context);
            try {
                Records.ReviewStats stats = store.reviewStatsSince(0L);
                assertEquals(1, stats.total);
                assertEquals(1, stats.again);
                assertEquals(1, stats.writingRequired);
                assertEquals(1, stats.writingFailed);
            } finally {
                store.close();
            }
        }
    }

    @Test
    public void testTryAgainWithFullGuideStartsFreshAttempt() {
        seedDashboard();
        MainActivity.setWritingRecognizerForTests(new FakeWritingRecognizer("提"));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study");
            scenario.onActivity(activity -> drawGuideKanji(activity, "拉"));
            clickText(scenario, "Check");
            scenario.onActivity(activity -> assertHasText(activity, "Try again with full guide"));
            clickText(scenario, "Try again with full guide");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Fresh guided try");
                assertNoText(activity, "I could not read that as the target kanji yet");
                assertNoText(activity, "Next card");
                MainActivity.DrawingPadView pad = findType(activity.findViewById(android.R.id.content), MainActivity.DrawingPadView.class);
                assertNotNull(pad);
                assertFalse(pad.hasInk());
            });
        }
    }

    @Test
    public void testWrongRecognitionAllowsLoggedManualOverride() {
        seedDashboard();
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
        seedDashboard();
        MainActivity.setWritingRecognizerForTests(new FakeUnavailableRecognizer());
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study");
            scenario.onActivity(activity -> drawGuideKanji(activity, "拉"));
            clickText(scenario, "Check");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Download the handwriting checker before automatic checks");
                assertHasText(activity, "Target: 拉");
                assertHasText(activity, "Next card");
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
            clickText(scenario, "Sync and tag archive");
            LocalStore.SyncStatus status = waitForLatestSync();
            assertNotNull(status);
            assertEquals("config_error", status.status);
            assertTrue(status.errorMessage.contains("AnkiDroid"));
        }
    }

    @Test
    public void testManualSyncButtonWorksAgainstLiveAnkiDroid() throws Exception {
        Assume.assumeTrue("Live AnkiDroid fixture is opt-in.", liveAnkiDroidEnabled());
        MainActivity.setAnkiDroidGatewayForTests(null);
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Sync AnkiDroid");
            clickText(scenario, "Sync and tag archive");
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

    private void seedDashboard() {
        seedDashboard(Collections.singletonList(dashboardRow("拉", "ramen radical gap", "ら", "Imported from suspended cards")));
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
                0,
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
    }

    private static UiObject2 findDeviceText(UiDevice device, String text) {
        UiObject2 object = device.wait(Until.findObject(By.text(text)), 1000L);
        if (object == null) {
            object = device.wait(Until.findObject(By.text(text.toUpperCase(Locale.ROOT))), 1000L);
        }
        if (object == null) {
            object = device.wait(Until.findObject(By.textContains(text)), 1000L);
        }
        if (object == null) {
            object = device.wait(Until.findObject(By.textContains(text.toUpperCase(Locale.ROOT))), 1000L);
        }
        return object;
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
