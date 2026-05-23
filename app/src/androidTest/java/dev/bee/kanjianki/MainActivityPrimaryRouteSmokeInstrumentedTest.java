package dev.bee.kanjianki;

import android.content.Context;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.uiautomator.By;
import androidx.test.uiautomator.UiDevice;
import androidx.test.uiautomator.UiObject2;
import androidx.test.uiautomator.Until;

import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.data.LocalStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertNotNull;

@RunWith(AndroidJUnit4.class)
public final class MainActivityPrimaryRouteSmokeInstrumentedTest {
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("kanji_anki_simple.db");
        MainActivityRuntimeOverrides.setAnkiDroidGateway(AnkiDroidGateway.testProvider(context, "dev.bee.kanjianki.primary_route_no_anki"));
        MainActivityRuntimeOverrides.setCollectionGateway(null);
        MainActivityRuntimeOverrides.setWritingRecognizer(null);
        MainActivityRuntimeOverrides.setWritingRecognizerFactory(null);
        MainActivityRuntimeOverrides.setInstallPermission(false);
        MainActivityRuntimeOverrides.setRuntimeNotificationPermission(null);
        MainActivityRuntimeOverrides.setNotificationsAllowed(null);
    }

    @After
    public void tearDown() {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(null);
        MainActivityRuntimeOverrides.setCollectionGateway(null);
        MainActivityRuntimeOverrides.setWritingRecognizer(null);
        MainActivityRuntimeOverrides.setWritingRecognizerFactory(null);
        MainActivityRuntimeOverrides.setInstallPermission(null);
        MainActivityRuntimeOverrides.setRuntimeNotificationPermission(null);
        MainActivityRuntimeOverrides.setNotificationsAllowed(null);
        context.deleteDatabase("kanji_anki_simple.db");
    }

    @Test
    public void primaryRoutesRenderProductionComposeScreens() {
        seedRows();

        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.onActivity(MainActivity::renderHome);
            assertVisible("Browse Kanji");
            assertVisible("Stats");
            assertVisible("Games");

            scenario.onActivity(MainActivity::renderSettings);
            assertVisible(MainActivityBase.NAV_SETTINGS);
            assertVisible("Settings cockpit");
            assertVisible("Note type");

            scenario.onActivity(activity -> activity.renderBrowseKanji("裂"));
            assertVisible("Browse Kanji");
            assertVisible("split");
            assertVisible("local source");

            scenario.onActivity(activity -> activity.renderDetail("裂", true, "裂"));
            assertVisible("Back to Browse Kanji");
            assertVisible("裂");
            assertVisible("Why it is here");

            scenario.onActivity(MainActivity::renderStats);
            assertVisible("Stats");
            assertVisible("Kani is not currently working for you");

            scenario.onActivity(MainActivity::renderGames);
            assertVisible("Games");
            assertVisible("Meaning Pop");

            scenario.onActivity(MainActivity::renderUpdate);
            assertVisible("GitHub updater");
            assertVisible("Check for update");
        }
    }

    private void seedRows() {
        try (LocalStore store = new LocalStore(context)) {
            store.saveSuccessfulSync(
                    new RecordsSyncModels.CollectionSnapshot(
                            Arrays.asList(
                                    TestRecords.kikuNote(1L, "裂語", "レツ", "split", "裂を見た。"),
                                    TestRecords.kikuNote(2L, "列語", "レツ", "row", "列を見た。"),
                                    TestRecords.kikuNote(3L, "語学", "ゴ", "language", "語を見た。")
                            ),
                            Arrays.asList(
                                    TestRecords.kikuCard(10L, 1L).build(),
                                    TestRecords.kikuCard(20L, 2L).build(),
                                    TestRecords.kikuCard(30L, 3L).build()
                            )
                    ),
                    Collections.emptyList(),
                    Arrays.asList(
                            row("裂", "split", "レツ"),
                            row("列", "row", "レツ"),
                            row("語", "language", "ゴ")
                    ),
                    RecordsSyncModels.Settings.kikuDefaults(),
                    new LocalStore.SyncTiming(1000L, 2000L),
                    null,
                    null
            );
        }
    }

    private static RecordsImportModels.DashboardRow row(String kanji, String meaning, String reading) {
        return new RecordsImportModels.DashboardRow(
                kanji,
                1000,
                meaning,
                reading,
                kanji,
                10,
                "route_smoke",
                "route smoke",
                1,
                0,
                0,
                Collections.emptyList()
        );
    }

    private static void assertVisible(String text) {
        UiObject2 object = waitForText(text);
        assertNotNull("Missing visible text: " + text, object);
    }

    private static UiObject2 waitForText(String text) {
        UiDevice device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation());
        String pkg = InstrumentationRegistry.getInstrumentation().getTargetContext().getPackageName();
        UiObject2 exact = device.wait(Until.findObject(By.pkg(pkg).text(text)), 3_000L);
        if (exact != null) {
            return exact;
        }
        return device.wait(Until.findObject(By.pkg(pkg).textContains(text)), 3_000L);
    }
}
