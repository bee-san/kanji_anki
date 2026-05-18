package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsImportModels;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class StudyRepairActionsTest {
    @Test
    public void activateSimilarWritingRepairStoresActiveTokenAndProgressKeys() {
        RecordsImportModels.SimilarKanjiWritingRepair repair = repair("");
        AtomicReference<RecordsImportModels.SimilarKanjiWritingRepair> saved = new AtomicReference<>();

        StudyRepairActions.ActiveRepair active = StudyRepairActions.activateSimilarWritingRepair(repair, 1234L, saved::set);

        assertSame(saved.get(), active.repair());
        assertEquals(active.token(), active.repair().activeToken);
        assertEquals(1234L, active.repair().updatedAtMillis);
        assertTrue(active.token().startsWith("repair-42-"));
        assertEquals("repair:42", active.progressKey());
        assertEquals("repair:42:" + active.token(), active.studyTaskKey());
    }

    @Test
    public void activateSimilarWritingRepairKeepsExistingActiveToken() {
        RecordsImportModels.SimilarKanjiWritingRepair repair = repair("existing-token");

        StudyRepairActions.ActiveRepair active = StudyRepairActions.activateSimilarWritingRepair(repair, 1234L, ignored -> {
        });

        assertEquals("existing-token", active.token());
        assertEquals("existing-token", active.repair().activeToken);
        assertEquals("repair:42:existing-token", active.studyTaskKey());
    }

    @Test
    public void completeSimilarWritingRepairRecordsAndMarksSavedPass() {
        RecordsImportModels.SimilarKanjiWritingRepair repair = repair("active-token");
        List<String> events = new ArrayList<>();
        RecordingFinisher finisher = new RecordingFinisher(events, true);
        RecordingOutcomeRecorder recorder = new RecordingOutcomeRecorder(events);
        RecordingMarker marker = new RecordingMarker(events);

        StudyRepairActions.RepairCompletion completion = StudyRepairActions.completeSimilarWritingRepair(
                repair,
                MainActivityBase.RATING_GOOD,
                2222L,
                finisher,
                recorder,
                marker
        );

        assertTrue(completion.saved());
        assertTrue(completion.passed());
        assertEquals(List.of("finish", "record", "mark"), events);
        assertEquals(42L, finisher.repairId);
        assertEquals("active-token", finisher.activeToken);
        assertTrue(finisher.passed);
        assertEquals(2222L, finisher.nowMillis);
        assertEquals("未", recorder.kanji);
        assertTrue(recorder.passed);
        assertEquals("repair:42", marker.taskKey);
    }

    @Test
    public void completeSimilarWritingRepairRecordsSavedFailureWithoutMarkingComplete() {
        RecordsImportModels.SimilarKanjiWritingRepair repair = repair("active-token");
        List<String> events = new ArrayList<>();
        RecordingFinisher finisher = new RecordingFinisher(events, true);
        RecordingOutcomeRecorder recorder = new RecordingOutcomeRecorder(events);
        RecordingMarker marker = new RecordingMarker(events);

        StudyRepairActions.RepairCompletion completion = StudyRepairActions.completeSimilarWritingRepair(
                repair,
                MainActivityBase.RATING_AGAIN,
                2222L,
                finisher,
                recorder,
                marker
        );

        assertTrue(completion.saved());
        assertFalse(completion.passed());
        assertEquals(List.of("finish", "record"), events);
        assertFalse(finisher.passed);
        assertFalse(recorder.passed);
        assertEquals(null, marker.taskKey);
    }

    @Test
    public void completeSimilarWritingRepairSkipsOutcomeAndMarkerWhenStoreRejects() {
        RecordsImportModels.SimilarKanjiWritingRepair repair = repair("active-token");
        List<String> events = new ArrayList<>();
        RecordingFinisher finisher = new RecordingFinisher(events, false);
        RecordingOutcomeRecorder recorder = new RecordingOutcomeRecorder(events);
        RecordingMarker marker = new RecordingMarker(events);

        StudyRepairActions.RepairCompletion completion = StudyRepairActions.completeSimilarWritingRepair(
                repair,
                MainActivityBase.RATING_GOOD,
                2222L,
                finisher,
                recorder,
                marker
        );

        assertFalse(completion.saved());
        assertTrue(completion.passed());
        assertEquals(List.of("finish"), events);
        assertEquals(null, recorder.kanji);
        assertEquals(null, marker.taskKey);
    }

    private static RecordsImportModels.SimilarKanjiWritingRepair repair(String activeToken) {
        return new RecordsImportModels.SimilarKanjiWritingRepair(
                42L,
                "末",
                "未",
                "末|未",
                "末",
                "not yet",
                "pending",
                1000L,
                activeToken,
                0,
                900L,
                901L,
                0L
        );
    }

    private static final class RecordingFinisher implements StudyRepairActions.SimilarWritingRepairFinisher {
        private final List<String> events;
        private final boolean saved;
        private long repairId;
        private String activeToken;
        private boolean passed;
        private long nowMillis;

        private RecordingFinisher(List<String> events, boolean saved) {
            this.events = events;
            this.saved = saved;
        }

        @Override
        public boolean finishSimilarWritingRepair(long repairId, String activeToken, boolean passed, long nowMillis) {
            events.add("finish");
            this.repairId = repairId;
            this.activeToken = activeToken;
            this.passed = passed;
            this.nowMillis = nowMillis;
            return saved;
        }
    }

    private static final class RecordingOutcomeRecorder implements StudyRepairActions.RepairOutcomeRecorder {
        private final List<String> events;
        private String kanji;
        private boolean passed;

        private RecordingOutcomeRecorder(List<String> events) {
            this.events = events;
        }

        @Override
        public void recordRepairOutcome(String kanji, boolean passed) {
            events.add("record");
            this.kanji = kanji;
            this.passed = passed;
        }
    }

    private static final class RecordingMarker implements StudyRepairActions.RepairTaskMarker {
        private final List<String> events;
        private String taskKey;

        private RecordingMarker(List<String> events) {
            this.events = events;
        }

        @Override
        public void markStudyTaskCompleted(String taskKey) {
            events.add("mark");
            this.taskKey = taskKey;
        }
    }
}
