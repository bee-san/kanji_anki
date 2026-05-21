package dev.bee.kanjianki;

import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.RecordsSyncModels;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class StudyMoreNewCardActionsTest {
    @Test
    public void applyAdmissionPersistsAnnotatedItemsAndUpdatesFocusState() {
        BridgeScheduler.ExtraNewCardsResult result = new BridgeScheduler().seedExtraNewCards(
                rows("謎", "示"),
                Collections.emptyList(),
                RecordsSyncModels.Settings.kikuDefaults(),
                1000L,
                0L,
                2
        );
        List<RecordsStudyModels.StudyItem> annotated = new ArrayList<>(result.items);
        RecordingWriter writer = new RecordingWriter(annotated);
        List<String> selected = new ArrayList<>(List.of("old"));
        AtomicBoolean reset = new AtomicBoolean(false);
        AtomicInteger target = new AtomicInteger(-1);

        StudyMoreNewCardActions.AdmissionResult admission = StudyMoreNewCardActions.applyAdmission(
                result,
                writer,
                selected,
                () -> reset.set(true),
                target::set
        );

        assertTrue(admission.admittedAny());
        assertEquals(2, admission.admittedCount());
        assertSame(result.items, writer.annotatedInput);
        assertSame(annotated, writer.replacedInput);
        assertEquals(Arrays.asList("謎", "示"), selected);
        assertTrue(reset.get());
        assertEquals(2, target.get());
    }

    @Test
    public void applyAdmissionDoesNothingWhenNoCardsAdmitted() {
        BridgeScheduler.ExtraNewCardsResult result = new BridgeScheduler().seedExtraNewCards(
                rows("謎"),
                Collections.emptyList(),
                RecordsSyncModels.Settings.kikuDefaults(),
                1000L,
                0L,
                0
        );
        RecordingWriter writer = new RecordingWriter(Collections.emptyList());
        List<String> selected = new ArrayList<>(List.of("old"));
        AtomicBoolean reset = new AtomicBoolean(false);
        AtomicInteger target = new AtomicInteger(-1);

        StudyMoreNewCardActions.AdmissionResult admission = StudyMoreNewCardActions.applyAdmission(
                result,
                writer,
                selected,
                () -> reset.set(true),
                target::set
        );

        assertFalse(admission.admittedAny());
        assertEquals(0, admission.admittedCount());
        assertEquals(null, writer.annotatedInput);
        assertEquals(null, writer.replacedInput);
        assertEquals(List.of("old"), selected);
        assertFalse(reset.get());
        assertEquals(-1, target.get());
    }

    @Test
    public void admissionResultKeepsJavaRecordSemantics() {
        assertTrue(StudyMoreNewCardActions.AdmissionResult.class.isRecord());
        assertEquals(
                new StudyMoreNewCardActions.AdmissionResult(true, 2),
                new StudyMoreNewCardActions.AdmissionResult(true, 2)
        );
    }

    private static List<RecordsImportModels.DashboardRow> rows(String... kanji) {
        List<RecordsImportModels.DashboardRow> rows = new ArrayList<>();
        for (String item : kanji) {
            rows.add(new RecordsImportModels.DashboardRow(
                    item,
                    null,
                    "meaning",
                    "",
                    item,
                    1,
                    "reason",
                    "Needs practice",
                    1,
                    0,
                    0,
                    List.of()
            ));
        }
        return rows;
    }

    private static final class RecordingWriter implements StudyMoreNewCardActions.StudyItemWriter {
        private final List<RecordsStudyModels.StudyItem> annotatedResult;
        private List<RecordsStudyModels.StudyItem> annotatedInput;
        private List<RecordsStudyModels.StudyItem> replacedInput;

        private RecordingWriter(List<RecordsStudyModels.StudyItem> annotatedResult) {
            this.annotatedResult = annotatedResult;
        }

        @Override
        public List<RecordsStudyModels.StudyItem> annotateSimilarKanjiAvailability(List<RecordsStudyModels.StudyItem> items) {
            annotatedInput = items;
            return annotatedResult;
        }

        @Override
        public void replaceStudyItems(List<RecordsStudyModels.StudyItem> items) {
            replacedInput = items;
        }
    }
}
