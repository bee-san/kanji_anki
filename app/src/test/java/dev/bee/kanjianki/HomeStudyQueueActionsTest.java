package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.RecordsSyncModels;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public final class HomeStudyQueueActionsTest {
    @Test
    public void nonPersistentQueueReturnsCurrentItemsWithoutSeedingOrWriting() {
        List<RecordsStudyModels.StudyItem> current = new ArrayList<>();
        AtomicBoolean seeded = new AtomicBoolean(false);
        RecordingWriter writer = new RecordingWriter(current);

        List<RecordsStudyModels.StudyItem> result = HomeStudyQueueActions.studyQueue(request(
                false,
                current,
                null,
                (rows, currentItems, settings, nowMillis, startOfDayMillis, plan, ladder) -> {
                    seeded.set(true);
                    return Collections.emptyList();
                },
                writer
        ));

        assertSame(current, result);
        assertFalse(seeded.get());
        assertFalse(writer.annotated);
        assertFalse(writer.replaced);
    }

    @Test
    public void persistentQueueSeedsAnnotatesPersistsAndReturnsAnnotatedItems() {
        List<RecordsStudyModels.StudyItem> current = new ArrayList<>();
        List<RecordsStudyModels.StudyItem> seeded = new ArrayList<>();
        List<RecordsStudyModels.StudyItem> annotated = new ArrayList<>();
        RecordingWriter writer = new RecordingWriter(annotated);
        AtomicLong startOfDay = new AtomicLong();
        AtomicReference<RecordsSchedulerModels.AdaptiveLoadPlan> seenPlan = new AtomicReference<>();

        List<RecordsStudyModels.StudyItem> result = HomeStudyQueueActions.studyQueue(baseRequest(
                true,
                current,
                null,
                (rows, currentItems, settings, nowMillis, startOfDayMillis, plan, ladder) -> {
                    assertSame(current, currentItems);
                    assertEquals(123L, nowMillis);
                    startOfDay.set(startOfDayMillis);
                    seenPlan.set(plan);
                    return seeded;
                },
                writer
        ));

        assertSame(annotated, result);
        assertSame(seeded, writer.annotatedInput);
        assertSame(annotated, writer.replacedInput);
        assertEquals(100L, startOfDay.get());
        assertTrue(seenPlan.get().autoMode);
    }

    @Test
    public void persistentQueueUsesProvidedPlanWithoutRecomputing() {
        List<RecordsStudyModels.StudyItem> current = new ArrayList<>();
        RecordsSchedulerModels.AdaptiveLoadPlan provided = plan(false);
        AtomicBoolean recomputed = new AtomicBoolean(false);
        RecordingWriter writer = new RecordingWriter(current);

        HomeStudyQueueActions.studyQueue(new HomeStudyQueueActions.StudyQueueRequest(
                Collections.emptyList(),
                123L,
                true,
                provided,
                () -> current,
                RecordsSyncModels.Settings::kikuDefaults,
                nowMillis -> 100L,
                RecordsBase.StudyLadderSettings::defaults,
                (rows, currentItems, nowMillis) -> {
                    recomputed.set(true);
                    return plan(true);
                },
                (rows, currentItems, settings, nowMillis, startOfDayMillis, plan, ladder) -> {
                    assertSame(provided, plan);
                    return current;
                },
                writer
        ));

        assertFalse(recomputed.get());
    }

    private static HomeStudyQueueActions.StudyQueueRequest request(
            boolean persist,
            List<RecordsStudyModels.StudyItem> current,
            RecordsSchedulerModels.AdaptiveLoadPlan providedPlan,
            HomeStudyQueueActions.StudyQueueSeeder seeder,
            HomeStudyQueueActions.StudyItemsWriter writer
    ) {
        return baseRequest(persist, current, providedPlan, seeder, writer);
    }

    private static HomeStudyQueueActions.StudyQueueRequest baseRequest(
            boolean persist,
            List<RecordsStudyModels.StudyItem> current,
            RecordsSchedulerModels.AdaptiveLoadPlan providedPlan,
            HomeStudyQueueActions.StudyQueueSeeder seeder,
            HomeStudyQueueActions.StudyItemsWriter writer
    ) {
        return new HomeStudyQueueActions.StudyQueueRequest(
                Collections.emptyList(),
                123L,
                persist,
                providedPlan,
                () -> current,
                RecordsSyncModels.Settings::kikuDefaults,
                nowMillis -> 100L,
                RecordsBase.StudyLadderSettings::defaults,
                (rows, currentItems, nowMillis) -> plan(true),
                seeder,
                writer
        );
    }

    private static RecordsSchedulerModels.AdaptiveLoadPlan plan(boolean enabled) {
        return new RecordsSchedulerModels.AdaptiveLoadPlan(
                enabled,
                20,
                1,
                1,
                Collections.emptyList(),
                1,
                false,
                "status"
        );
    }

    private static final class RecordingWriter implements HomeStudyQueueActions.StudyItemsWriter {
        private final List<RecordsStudyModels.StudyItem> annotatedResult;
        private boolean annotated;
        private boolean replaced;
        private List<RecordsStudyModels.StudyItem> annotatedInput;
        private List<RecordsStudyModels.StudyItem> replacedInput;

        private RecordingWriter(List<RecordsStudyModels.StudyItem> annotatedResult) {
            this.annotatedResult = annotatedResult;
        }

        @Override
        public List<RecordsStudyModels.StudyItem> annotateSimilarKanjiAvailability(List<RecordsStudyModels.StudyItem> items) {
            annotated = true;
            annotatedInput = items;
            return annotatedResult;
        }

        @Override
        public void replaceStudyItems(List<RecordsStudyModels.StudyItem> items) {
            replaced = true;
            replacedInput = items;
        }
    }
}
