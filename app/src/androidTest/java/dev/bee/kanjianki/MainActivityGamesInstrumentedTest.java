package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.core.KanjiGameEngine;
import android.content.Context;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.data.LocalStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class MainActivityGamesInstrumentedTest {
    private static final String LABEL_GAMES = "Games";
    private static final String LABEL_NEXT = "Next";
    private static final String LABEL_NEW_ROUND = "New round";

    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("kanji_anki_simple.db");
        MainActivity.setAnkiDroidGatewayForTests(AnkiDroidGateway.testProvider(context, "dev.bee.kanjianki.games_no_anki"));
        MainActivity.setCollectionGatewayForTests(null);
        MainActivity.setWritingRecognizerForTests(null);
        MainActivity.setWritingRecognizerFactoryForTests(null);
    }

    @After
    public void tearDown() {
        MainActivity.setAnkiDroidGatewayForTests(null);
        MainActivity.setCollectionGatewayForTests(null);
        MainActivity.setWritingRecognizerForTests(null);
        MainActivity.setWritingRecognizerFactoryForTests(null);
        context.deleteDatabase("kanji_anki_simple.db");
    }

    @Test
    public void homeGamesButtonOpensPracticeOnlyHub() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.renderGames();

                assertTrue(containsText(activity.findViewById(android.R.id.content), "Home"));
                assertNotNull(findComposeView(activity.findViewById(android.R.id.content)));
            });
        }
    }

    @Test
    public void gameRoundEndsAfterTenAnswersWithoutSrsReview() {
        seedGameRows();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(activity -> {
                activity.startGame(KanjiGameEngine.GameMode.MEANING_POP);

                for (int answer = 0; answer < 10; answer++) {
                    Button choice = firstAnswerButton(activity.content);
                    assertNotNull(choice);
                    assertTrue(choice.performClick());
                    if (answer < 9) {
                        Button next = findButton(activity.content, LABEL_NEXT);
                        assertNotNull(next);
                        assertTrue(next.performClick());
                    }
                }

                assertTrue(containsText(activity.content, "Round complete"));
                assertTrue(containsText(activity.content, "Final score:"));
                assertNotNull(findButton(activity.content, LABEL_NEW_ROUND));
                assertNull(findButton(activity.content, LABEL_NEXT));
                assertEquals(0, activity.store.reviewStatsSince(0L).total);
            });
        }
    }

    private void seedGameRows() {
        try (LocalStore store = new LocalStore(context)) {
            store.saveSuccessfulSync(
                    new RecordsSyncModels.CollectionSnapshot(Collections.emptyList(), Collections.emptyList()),
                    Collections.emptyList(),
                    Arrays.asList(
                            dashboardRow("裂", "split", "れつ"),
                            dashboardRow("提", "present", "てい"),
                            dashboardRow("語", "language", "ご")
                    ),
                    RecordsSyncModels.Settings.kikuDefaults(),
                    1L,
                    2L,
                    null
            );
        }
    }

    private static RecordsImportModels.DashboardRow dashboardRow(String kanji, String meaning, String reading) {
        return new RecordsImportModels.DashboardRow(
                kanji,
                100,
                meaning,
                reading,
                kanji,
                5,
                "game_fixture",
                "game fixture",
                1,
                0,
                0,
                Collections.singletonList(new RecordsImportModels.Example("active", 1L, 1L, kanji + "語", reading, meaning, "", false, 1))
        );
    }

    private static boolean containsText(View view, String expected) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            if (textView.getText().toString().contains(expected)) {
                return true;
            }
        }
        if (view instanceof androidx.compose.ui.platform.ComposeView) {
            if (containsAccessibilityText(view.createAccessibilityNodeInfo(), expected)) {
                return true;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsText(group.getChildAt(i), expected)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static View findClickable(View view, String expected) {
        if (view.isClickable() && containsText(view, expected)) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findClickable(group.getChildAt(i), expected);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static View findComposeView(View view) {
        if (view instanceof androidx.compose.ui.platform.ComposeView) {
            return view;
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findComposeView(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static boolean containsAccessibilityText(AccessibilityNodeInfo node, String expected) {
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
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo child = node.getChild(i);
                if (child != null && containsAccessibilityText(child, expected)) {
                    return true;
                }
            }
            return false;
        } finally {
            node.recycle();
        }
    }

    private static Button firstAnswerButton(View view) {
        if (view instanceof Button) {
            Button button = (Button) view;
            String label = button.getText().toString();
            if (!LABEL_NEXT.equals(label) && !LABEL_GAMES.equals(label) && !LABEL_NEW_ROUND.equals(label)) {
                return button;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = firstAnswerButton(group.getChildAt(i));
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static Button findButton(View view, String label) {
        if (view instanceof Button) {
            Button button = (Button) view;
            if (label.equals(button.getText().toString())) {
                return button;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                Button found = findButton(group.getChildAt(i), label);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
