package dev.bee.kanjianki;

import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.RecordsSyncModels;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class StudySessionActionsTest {
    @Test
    public void activateStudySessionSavesRegistersAndStartsInOrder() {
        List<String> events = new ArrayList<>();
        RecordsStudyModels.StudyItem item = item("語").withToken("token-1");
        RecordsSchedulerModels.StudySession session = new RecordsSchedulerModels.StudySession(
                item,
                row("語"),
                "token-1",
                "kanji_meaning",
                false,
                "language"
        );
        RecordingWriter writer = new RecordingWriter(events);
        RecordingRegistrar registrar = new RecordingRegistrar(events);
        RecordingStarter starter = new RecordingStarter(events);

        String taskKey = StudySessionActions.activateStudySession(session, 1234L, writer, registrar, starter);

        assertEquals("session:kanji_meaning:語:token-1", taskKey);
        assertEquals(List.of("saveItem", "register", "start"), events);
        assertSame(item, writer.item);
        assertEquals(taskKey, registrar.taskKey);
        assertEquals(taskKey, starter.taskKey);
        assertEquals("語", starter.kanji);
        assertEquals("kanji_meaning", starter.taskType);
        assertEquals(1234L, starter.nowMillis);
    }

    @Test
    public void activateStudySessionRejectsNullItemSessions() {
        RecordsSchedulerModels.StudySession session = new RecordsSchedulerModels.StudySession(
                null,
                row("語"),
                "token-1",
                "kanji_meaning",
                false,
                "language"
        );

        assertThrows(
                NullPointerException.class,
                () -> StudySessionActions.activateStudySession(
                        session,
                        1234L,
                        item -> { },
                        taskKey -> { },
                        (taskKey, kanji, taskType, nowMillis) -> { }
                )
        );
    }

    @Test
    public void plannedStudySessionInitializesPlanBeforeChoosingSession() {
        StudySessionTracker tracker = new StudySessionTracker();
        List<RecordsStudyModels.StudyItem> items = List.of(item("語"), item("謎"));
        List<RecordsImportModels.DashboardRow> rows = List.of(row("語"), row("謎"));

        RecordsSchedulerModels.StudySession session = StudySessionActions.plannedStudySession(
                new BridgeScheduler(),
                tracker,
                items,
                rows,
                2_000L,
                0L,
                null,
                RecordsSyncModels.Settings.kikuDefaults(),
                RecordsBase.StudyLadderSettings.defaults()
        );

        assertNotNull(session);
        assertTrue(tracker.pendingPlannedSessionTaskKeys().contains(session.taskType + ":" + session.item.kanji));
        assertEquals(2, tracker.pendingPlannedSessionTaskKeys().size());
    }

    private static RecordsStudyModels.StudyItem item(String kanji) {
        return new RecordsStudyModels.StudyItem(kanji, "review", 1000L, 1.0, 2.0, 1, 0, 0, 0, "", 1000L);
    }

    private static RecordsImportModels.DashboardRow row(String kanji) {
        return new RecordsImportModels.DashboardRow(
                kanji,
                null,
                "meaning",
                "",
                kanji,
                1,
                "reason",
                "Needs practice",
                1,
                0,
                0,
                List.of()
        );
    }

    private static final class RecordingWriter implements StudySessionActions.StudyItemWriter {
        private final List<String> events;
        private RecordsStudyModels.StudyItem item;

        private RecordingWriter(List<String> events) {
            this.events = events;
        }

        @Override
        public void saveStudyItem(RecordsStudyModels.StudyItem item) {
            events.add("saveItem");
            this.item = item;
        }
    }

    private static final class RecordingRegistrar implements StudySessionActions.TaskRegistrar {
        private final List<String> events;
        private String taskKey;

        private RecordingRegistrar(List<String> events) {
            this.events = events;
        }

        @Override
        public void registerStudyTaskShown(String taskKey) {
            events.add("register");
            this.taskKey = taskKey;
        }
    }

    private static final class RecordingStarter implements StudySessionActions.ActiveTaskStarter {
        private final List<String> events;
        private String taskKey;
        private String kanji;
        private String taskType;
        private long nowMillis;

        private RecordingStarter(List<String> events) {
            this.events = events;
        }

        @Override
        public void startActiveStudyTask(String taskKey, String kanji, String taskType, long nowMillis) {
            events.add("start");
            this.taskKey = taskKey;
            this.kanji = kanji;
            this.taskType = taskType;
            this.nowMillis = nowMillis;
        }
    }
}
