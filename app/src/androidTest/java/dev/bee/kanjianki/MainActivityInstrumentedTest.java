package dev.bee.kanjianki;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.database.sqlite.SQLiteDatabaseLockedException;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
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
        MainActivity.setAnkiDroidGatewayForTests(null);
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
                assertHasText(activity, "Sync from AnkiDroid");
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

            clickText(scenario, "Kanji");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Kanji to repair");
                assertHasText(activity, "No kanji yet");
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
            clickText(scenario, "Kanji");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Kanji to repair");
                assertHasText(activity, "ramen radical gap");
            });

            clickText(scenario, "拉");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Why it is here");
                assertHasText(activity, "Imported from suspended cards");
                assertHasText(activity, "Practice this kanji");
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

            clickText(scenario, "Practice this kanji");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Writing practice");
                assertHasText(activity, "Learn this kanji");
                assertHasText(activity, "Meaning cue: ramen radical gap");
                assertHasText(activity, "Meet the kanji");
                assertHasText(activity, "Meaning: ramen radical gap");
                assertHasText(activity, "Reading: ら");
                assertHasText(activity, "Example: 拉麺  らーめん");
                assertHasText(activity, "Step 1 of 4");
                assertHasText(activity, "I copied it");
                assertHasText(activity, "拉麺");
            });

            clickText(scenario, "I copied it");
            clickText(scenario, "Try with less help");
            clickText(scenario, "Start memory check");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Step 4 of 4");
                assertNoText(activity, "Meet the kanji");
                assertNoText(activity, "拉麺");
            });
            clickText(scenario, "Check my writing");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Write in the square before checking");
                assertNoText(activity, "Meet the kanji");
                assertNoText(activity, "拉麺");
                assertNoText(activity, "Mark right anyway");
                assertNoText(activity, "Save miss");
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
    public void testCorrectWritingCheckSubmitsReview() {
        seedDashboard();
        MainActivity.setWritingRecognizerForTests(new FakeWritingRecognizer("拉"));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study");
            clickText(scenario, "I copied it");
            clickText(scenario, "Try with less help");
            clickText(scenario, "Start memory check");
            scenario.onActivity(activity -> drawGuideKanji(activity, "拉"));
            clickText(scenario, "Check my writing");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Clean match");
                assertHasText(activity, "Target: 拉");
                assertHasText(activity, "Save and continue");
            });
            clickText(scenario, "Save and continue");

            LocalStore store = new LocalStore(context);
            try {
                Records.ReviewStats stats = store.reviewStatsSince(0L);
                assertEquals(1, stats.total);
                assertEquals(1, stats.easy);
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
            clickText(scenario, "I copied it");
            clickText(scenario, "Try with less help");
            clickText(scenario, "Start memory check");
            scenario.onActivity(activity -> drawGuideKanji(activity, "拉"));
            clickText(scenario, "Check my writing");
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
            } finally {
                store.close();
            }
        }
    }

    @Test
    public void testWrongRecognitionAllowsLoggedManualOverride() {
        seedDashboard();
        MainActivity.setWritingRecognizerForTests(new FakeWritingRecognizer("提"));
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study");
            clickText(scenario, "I copied it");
            clickText(scenario, "Try with less help");
            clickText(scenario, "Start memory check");
            scenario.onActivity(activity -> drawGuideKanji(activity, "拉"));
            clickText(scenario, "Check my writing");
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
    public void testDrawingPadTracksInkAndClear() {
        MainActivity.DrawingPadView pad = new MainActivity.DrawingPadView(context);
        assertFalse(pad.hasInk());

        long now = System.currentTimeMillis();
        pad.onTouchEvent(MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, 20f, 20f, 0));
        pad.onTouchEvent(MotionEvent.obtain(now, now + 10, MotionEvent.ACTION_MOVE, 80f, 80f, 0));
        pad.onTouchEvent(MotionEvent.obtain(now, now + 20, MotionEvent.ACTION_UP, 120f, 120f, 0));

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
            clickText(scenario, "Sync from AnkiDroid");
            LocalStore.SyncStatus status = waitForLatestSync();
            assertNotNull(status);
            assertEquals("config_error", status.status);
            assertTrue(status.errorMessage.contains("AnkiDroid"));
        }
    }

    @Test
    public void testManualSyncButtonWorksAgainstLiveAnkiDroid() throws Exception {
        Assume.assumeTrue("Live AnkiDroid fixture is opt-in.", liveAnkiDroidEnabled());
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Sync from AnkiDroid");
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
        Records.Settings settings = Records.Settings.kikuDefaults();
        Records.Note note = note(1L, "拉麺", "らーめん", "ramen radical gap", "拉麺を食べた。");
        Records.Card card = new Records.Card(10L, 1L, 0, "Kiku", 2, 2, 0, 3, 4, 1, false);
        Records.Example active = new Records.Example("active", 10L, 1L, "拉麺", "らーめん", "ramen radical gap", "拉麺を食べた。", false, 1);
        Records.Example suspended = new Records.Example("suspended", 20L, 2L, "拉致", "らち", "abduction", "ニュースで拉致を見た。", false, 0);
        Records.DashboardRow row = new Records.DashboardRow(
                "拉",
                3401,
                "ramen radical gap",
                "ら",
                "deck:Kiku 拉",
                88,
                "suspended_archive",
                "Imported from suspended cards",
                1,
                1,
                0,
                Arrays.asList(active, suspended)
        );
        LocalStore store = new LocalStore(context);
        try {
            store.saveSuccessfulSync(
                    new Records.CollectionSnapshot(Collections.singletonList(note), Collections.singletonList(card)),
                    Collections.emptyList(),
                    Collections.singletonList(row),
                    settings,
                    1000L,
                    2000L,
                    null
            );
        } finally {
            store.close();
        }
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
        scenario.onActivity(activity -> {
            View view = findExactText(activity.findViewById(android.R.id.content), text);
            assertNotNull("Missing text: " + text, view);
            View clickable = clickableAncestor(view);
            assertNotNull("Text is not clickable: " + text, clickable);
            assertTrue(clickable.performClick());
        });
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
            pad.onTouchEvent(MotionEvent.obtain(now, now + strokeIndex * 40L, MotionEvent.ACTION_DOWN, first.x * 1000f, first.y * 1000f, 0));
            for (int i = 1; i < stroke.points.size() - 1; i++) {
                InkPoint point = stroke.points.get(i);
                pad.onTouchEvent(MotionEvent.obtain(now, now + strokeIndex * 40L + i, MotionEvent.ACTION_MOVE, point.x * 1000f, point.y * 1000f, 0));
            }
            InkPoint last = stroke.points.get(stroke.points.size() - 1);
            pad.onTouchEvent(MotionEvent.obtain(now, now + strokeIndex * 40L + 30L, MotionEvent.ACTION_UP, last.x * 1000f, last.y * 1000f, 0));
            strokeIndex++;
        }
        assertTrue(pad.hasInk());
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
}
