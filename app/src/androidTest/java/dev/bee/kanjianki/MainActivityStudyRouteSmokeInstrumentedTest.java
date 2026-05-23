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
import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import dev.bee.kanjianki.core.SimilarKanjiIndex;
import dev.bee.kanjianki.data.LocalStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.StringReader;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public final class MainActivityStudyRouteSmokeInstrumentedTest {
    private Context context;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("kanji_anki_simple.db");
        MainActivityRuntimeOverrides.setAnkiDroidGateway(AnkiDroidGateway.testProvider(context, "dev.bee.kanjianki.study_route_no_anki"));
        MainActivityRuntimeOverrides.setCollectionGateway(null);
        MainActivityRuntimeOverrides.setWritingRecognizer(null);
        MainActivityRuntimeOverrides.setWritingRecognizerFactory(null);
        MainActivityRuntimeOverrides.setInstallPermission(null);
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
    public void flashcardAndWritingRoutesRenderProductionComposeScreens() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            RecordsImportModels.DashboardRow row = row("裂", "split", "レツ");
            scenario.onActivity(activity -> {
                RecordsSchedulerModels.StudySession flashcard = session(
                        row,
                        "flashcard-smoke",
                        BridgeScheduler.TASK_KANJI_MEANING,
                        false
                );
                activity.activeStudyPlan = plan("裂");
                activity.activeSession = flashcard;
                activity.startActiveStudyTask(activity.sessionTaskKey(flashcard), "裂", flashcard.taskType, System.currentTimeMillis());
                activity.renderSession(flashcard);
            });

            assertVisible("Recognise");
            assertVisible("Name this kanji");
            clickVisible("Reveal");
            assertVisible("Fail");
            assertVisible(MainActivityBase.LABEL_PASS);
            scenario.onActivity(activity -> assertTrue(activity.flashcardAnswerRevealed));

            scenario.onActivity(activity -> {
                RecordsSchedulerModels.StudySession writing = session(
                        row,
                        "writing-smoke",
                        BridgeScheduler.TASK_WRITE_KANJI,
                        true
                );
                activity.activeStudyPlan = plan("裂");
                activity.activeSession = writing;
                activity.startActiveStudyTask(activity.sessionTaskKey(writing), "裂", writing.taskType, System.currentTimeMillis());
                activity.renderSession(writing);
            });

            assertVisible(MainActivityBase.LABEL_PRACTICE);
            assertVisible("Draw this kanji");
            assertVisible("Writing");
            assertVisible("Check");
            scenario.onActivity(activity -> {
                assertNotNull(activity.drawingPad);
                assertNotNull(activity.studyStatus);
                assertFalse(activity.flashcardAnswerRevealed);
            });

            scenario.onActivity(activity -> {
                seedSimilarChoiceRows(activity);
                RecordsSchedulerModels.StudySession similarChoice = session(
                        row,
                        "choice-smoke",
                        BridgeScheduler.TASK_SIMILAR_KANJI,
                        false
                );
                activity.activeStudyPlan = plan("裂");
                activity.activeSession = similarChoice;
                activity.startActiveStudyTask(activity.sessionTaskKey(similarChoice), "裂", similarChoice.taskType, System.currentTimeMillis());
                activity.renderSession(similarChoice);
            });

            assertVisible("Choose the kanji");
            assertVisible(MainActivityBase.LABEL_SIMILAR_KANJI);
            assertVisible("Which kanji means split?");
            assertVisible("裂");
            assertVisible("列");

            scenario.onActivity(activity -> {
                seedMeaningChoiceRows(activity);
                RecordsSchedulerModels.StudySession meaningChoice = session(
                        row,
                        "meaning-choice-smoke",
                        BridgeScheduler.TASK_MEANING_KANJI,
                        false
                );
                activity.activeStudyPlan = plan("裂");
                activity.activeSession = meaningChoice;
                activity.startActiveStudyTask(activity.sessionTaskKey(meaningChoice), "裂", meaningChoice.taskType, System.currentTimeMillis());
                activity.renderSession(meaningChoice);
            });

            assertVisible("Recall");
            assertVisible("Choose the kanji");
            assertVisible("Meaning -> kanji");
            assertVisible("裂");
            assertVisible("烈");
        }
    }

    private static RecordsSchedulerModels.AdaptiveLoadPlan plan(String kanji) {
        return new RecordsSchedulerModels.AdaptiveLoadPlan(
                20,
                1,
                1,
                Collections.singletonList(kanji),
                0,
                false,
                "One left"
        );
    }

    private static RecordsSchedulerModels.StudySession session(
            RecordsImportModels.DashboardRow row,
            String token,
            String taskType,
            boolean writingRequired
    ) {
        RecordsStudyModels.StudyItem item = new RecordsStudyModels.StudyItem(
                row.kanji,
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
                false,
                "",
                0L,
                0,
                "sig-" + token,
                token,
                0L
        );
        return new RecordsSchedulerModels.StudySession(item, row, token, taskType, writingRequired, row.primaryMeaning);
    }

    private static RecordsImportModels.DashboardRow row(String kanji, String meaning, String reading) {
        return new RecordsImportModels.DashboardRow(
                kanji,
                1000,
                meaning,
                reading,
                kanji,
                10,
                "reason",
                "reason text",
                1,
                0,
                0,
                Collections.emptyList()
        );
    }

    private static void seedSimilarChoiceRows(MainActivity activity) {
        List<RecordsImportModels.DashboardRow> rows = Arrays.asList(
                row("裂", "split", "レツ"),
                row("列", "row", "レツ")
        );
        RecordsSyncModels.CollectionSnapshot snapshot = new RecordsSyncModels.CollectionSnapshot(
                Arrays.asList(
                        TestRecords.kikuNote(1L, "裂語", "レツ", "split", "裂を見た。"),
                        TestRecords.kikuNote(2L, "列語", "レツ", "row", "列を見た。")
                ),
                Arrays.asList(
                        TestRecords.kikuCard(10L, 1L).build(),
                        TestRecords.kikuCard(20L, 2L).build()
                )
        );
        try {
            activity.store.saveSuccessfulSync(
                    snapshot,
                    Collections.emptyList(),
                    rows,
                    RecordsSyncModels.Settings.kikuDefaults(),
                    new LocalStore.SyncTiming(1000L, 2000L),
                    null,
                    SimilarKanjiIndex.parseTsv(new StringReader("裂\t列\tfixture\n"))
            );
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    private static void seedMeaningChoiceRows(MainActivity activity) {
        List<RecordsImportModels.DashboardRow> rows = Arrays.asList(
                row("裂", "split", "レツ"),
                row("列", "row", "レツ"),
                row("烈", "ardent", "レツ"),
                row("劣", "inferior", "レツ")
        );
        RecordsSyncModels.CollectionSnapshot snapshot = new RecordsSyncModels.CollectionSnapshot(
                Arrays.asList(
                        TestRecords.kikuNote(1L, "裂語", "レツ", "split", "裂を見た。"),
                        TestRecords.kikuNote(2L, "列語", "レツ", "row", "列を見た。"),
                        TestRecords.kikuNote(3L, "烈語", "レツ", "ardent", "烈を見た。"),
                        TestRecords.kikuNote(4L, "劣語", "レツ", "inferior", "劣を見た。")
                ),
                Arrays.asList(
                        TestRecords.kikuCard(10L, 1L).build(),
                        TestRecords.kikuCard(20L, 2L).build(),
                        TestRecords.kikuCard(30L, 3L).build(),
                        TestRecords.kikuCard(40L, 4L).build()
                )
        );
        activity.store.saveSuccessfulSync(
                snapshot,
                Collections.emptyList(),
                rows,
                RecordsSyncModels.Settings.kikuDefaults(),
                new LocalStore.SyncTiming(3000L, 4000L),
                null,
                null
        );
    }

    private static void assertVisible(String text) {
        UiObject2 object = waitForText(text);
        assertNotNull("Missing visible text: " + text, object);
    }

    private static void clickVisible(String text) {
        UiObject2 object = waitForText(text);
        assertNotNull("Missing clickable text: " + text, object);
        UiObject2 clickable = object;
        while (clickable != null && !clickable.isClickable()) {
            clickable = clickable.getParent();
        }
        assertNotNull("Visible text is not inside a clickable node: " + text, clickable);
        clickable.click();
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation()).waitForIdle(2_000L);
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
