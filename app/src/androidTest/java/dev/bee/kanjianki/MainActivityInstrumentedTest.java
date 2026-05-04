package dev.bee.kanjianki;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.data.LocalStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class MainActivityInstrumentedTest {
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("kanji_anki_simple.db");
    }

    @After
    public void tearDown() {
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
                assertHasText(activity, "Sync AnkiDroid now");
            });
        }
    }

    @Test
    public void testNavigationSettingsAndEmptyStates() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Study");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Nothing to study yet");
                assertHasText(activity, "Run a manual sync first");
            });

            clickText(scenario, "Kanji");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Weak kanji");
                assertHasText(activity, "No rows");
            });

            clickText(scenario, "Prefs");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Suspended kanji rank cutoff");
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
                assertHasText(activity, "Weak kanji");
                assertHasText(activity, "ramen radical gap");
            });

            clickText(scenario, "拉");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Why it is here");
                assertHasText(activity, "Imported from suspended cards");
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

            clickText(scenario, "Study");
            scenario.onActivity(activity -> {
                assertHasText(activity, "Context production");
                assertHasText(activity, "Trace: copy the shape deliberately before rating.");
                assertHasText(activity, "good");
            });

            clickText(scenario, "good");
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
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            clickText(scenario, "Sync AnkiDroid now");
            LocalStore.SyncStatus status = waitForLatestSync();
            assertNotNull(status);
            assertEquals("config_error", status.status);
            assertTrue(status.errorMessage.contains("AnkiDroid"));
        }
    }

    private LocalStore.SyncStatus waitForLatestSync() throws Exception {
        for (int i = 0; i < 200; i++) {
            LocalStore store = new LocalStore(context);
            try {
                LocalStore.SyncStatus status = store.latestSync();
                if (status != null) {
                    return status;
                }
            } finally {
                store.close();
            }
            Thread.sleep(100);
        }
        return null;
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
}
