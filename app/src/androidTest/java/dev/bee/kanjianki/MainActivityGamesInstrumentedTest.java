package dev.bee.kanjianki;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import dev.bee.kanjianki.anki.AnkiDroidGateway;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class MainActivityGamesInstrumentedTest {
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
                assertTrue(containsText(activity.content, "Games"));

                activity.renderGames();

                assertTrue(containsText(activity.content, "Practice kanji without changing SRS."));
                assertTrue(containsText(activity.content, "Sync AnkiDroid"));
            });
        }
    }

    private static boolean containsText(View view, String expected) {
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            if (textView.getText().toString().contains(expected)) {
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
}
