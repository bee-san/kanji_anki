package dev.bee.kanjianki;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;

import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class MainActivityStudyTypingInstrumentedTest {
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("kanji_anki_simple.db");
        MainActivity.setAnkiDroidGatewayForTests(AnkiDroidGateway.testProvider(context, "dev.bee.kanjianki.typing_no_anki"));
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
    }

    @Test
    public void routedTypingMeaningInputAutoPassesAndPreservesGestureExclusion() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                RecordsImportModels.DashboardRow row = row("裂", "split", "レツ");
                RecordsSchedulerModels.StudySession correct = sessionWithToken("裂", row, "typing-correct");
                activity.activeStudyPlan = new RecordsSchedulerModels.AdaptiveLoadPlan(20, 1, 1, Collections.singletonList("裂"), 0, false, "One left");
                activity.activeSession = correct;
                activity.startActiveStudyTask(activity.sessionTaskKey(correct), "裂", correct.taskType, System.currentTimeMillis());
                activity.renderSession(correct);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                assertNotNull(activity.typingAnswerInput);
                assertTrue(hasComposeViewAncestor(activity.typingAnswerInput));
                activity.typingAnswerInput.setText("split");
                performClickableWithText(activity.studyActionBar, "Reveal");
                RecordsSchedulerModels.ReviewStats stats = activity.store.reviewStatsSince(0L);
                assertEquals(1, stats.total);
                assertEquals(1, stats.good);

                RecordsImportModels.DashboardRow row = row("裂", "split", "レツ");
                RecordsSchedulerModels.StudySession wrong = sessionWithToken("裂", row, "typing-wrong");
                activity.activeSession = wrong;
                activity.startActiveStudyTask(activity.sessionTaskKey(wrong), "裂", wrong.taskType, System.currentTimeMillis());
                activity.renderSession(wrong);
            });
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            scenario.onActivity(activity -> {
                assertNotNull(activity.typingAnswerInput);
                assertTrue(hasComposeViewAncestor(activity.typingAnswerInput));
                activity.typingAnswerInput.setText("wrong");
                int[] inputLocation = new int[2];
                activity.typingAnswerInput.getLocationOnScreen(inputLocation);
                float inputCenterX = inputLocation[0] + activity.typingAnswerInput.getWidth() / 2f;
                float inputCenterY = inputLocation[1] + activity.typingAnswerInput.getHeight() / 2f;
                assertTrue(activity.isTouchInsideView(activity.typingAnswerInput, motion(MotionEvent.ACTION_DOWN, inputCenterX, inputCenterY)));
                assertFalse(activity.handleFlashcardGesture(motion(MotionEvent.ACTION_DOWN, inputCenterX, inputCenterY)));
                assertFalse(activity.flashcardTouchTracking);
                assertFalse(activity.handleFlashcardGesture(motion(MotionEvent.ACTION_UP, inputCenterX, inputCenterY)));
                assertFalse(activity.flashcardTouchTracking);

                performClickableWithText(activity.studyActionBar, "Reveal");
                assertTrue(activity.flashcardAnswerRevealed);
                assertEquals(View.VISIBLE, activity.studyAnswerPanel.getVisibility());
                assertTrue(containsText(activity.studyActionBar, "Fail"));
                assertTrue(containsText(activity.studyActionBar, MainActivityBase.LABEL_PASS));
                RecordsSchedulerModels.ReviewStats stats = activity.store.reviewStatsSince(0L);
                assertEquals(1, stats.total);
                assertEquals(1, stats.good);
            });
        }
    }

    private static RecordsSchedulerModels.StudySession sessionWithToken(String kanji, RecordsImportModels.DashboardRow row, String token) {
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
                0,
                0L,
                false,
                "",
                0L,
                0,
                "sig",
                token,
                0L
        );
        return new RecordsSchedulerModels.StudySession(item, row, token, BridgeScheduler.TASK_TYPE_MEANING, false, row.primaryMeaning);
    }

    private static RecordsImportModels.DashboardRow row(String kanji, String meaning, String reading) {
        return new RecordsImportModels.DashboardRow(kanji, 1000, meaning, reading, kanji, 10, "reason", "reason text", 1, 0, 0, Collections.emptyList());
    }

    private static boolean hasComposeViewAncestor(View view) {
        ViewParent parent = view.getParent();
        while (parent != null) {
            if (parent instanceof androidx.compose.ui.platform.ComposeView) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    private static MotionEvent motion(int action, float x, float y) {
        return MotionEvent.obtain(0L, 0L, action, x, y, 0);
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

    private static UiObject2 findDeviceClickableTextNow(String label) {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        String pkg = InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName();
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
            for (int i = 0; i < node.getChildCount(); i++) {
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
}
