package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

public final class StudyExampleSelectorTest {
    @Test
    public void firstExamplePrefersActiveThenFallsBackToFirstExample() {
        RecordsImportModels.Example fallback = example("other", "fallback");
        RecordsImportModels.Example active = example("active", "active");
        RecordsImportModels.Example suspended = example("suspended", "suspended");

        assertSame(active, StudyExampleSelector.firstExample(row(fallback, suspended, active)));
        assertSame(fallback, StudyExampleSelector.firstExample(row(fallback, suspended)));
        assertNull(StudyExampleSelector.firstExample(null));
        assertNull(StudyExampleSelector.firstExample(row()));
    }

    @Test
    public void wordReadingExamplePrefersSuspendedThenActiveThenFirstExample() {
        RecordsImportModels.Example fallback = example("other", "fallback");
        RecordsImportModels.Example active = example("active", "active");
        RecordsImportModels.Example suspended = example("suspended", "suspended");

        assertSame(suspended, StudyExampleSelector.wordReadingExample(row(fallback, active, suspended)));
        assertSame(active, StudyExampleSelector.wordReadingExample(row(fallback, active)));
        assertSame(fallback, StudyExampleSelector.wordReadingExample(row(fallback)));
        assertNull(StudyExampleSelector.wordReadingExample(null));
        assertNull(StudyExampleSelector.wordReadingExample(row()));
    }

    @Test
    public void exampleForSessionUsesWordReadingSelectionOnlyForWordReadingTasks() {
        RecordsImportModels.Example active = example("active", "active");
        RecordsImportModels.Example suspended = example("suspended", "suspended");
        RecordsImportModels.DashboardRow row = row(active, suspended);

        assertSame(suspended, StudyExampleSelector.exampleForSession(session(StudyTaskTypes.WORD_READING, row)));
        assertSame(active, StudyExampleSelector.exampleForSession(session(StudyTaskTypes.KANJI_MEANING, row)));
        assertNull(StudyExampleSelector.exampleForSession(null));
    }

    private static RecordsSchedulerModels.StudySession session(String taskType, RecordsImportModels.DashboardRow row) {
        return new RecordsSchedulerModels.StudySession(
                new RecordsStudyModels.StudyItem("x", "review", 0L, 1.0, 5.0, 1, 0, 0, 1, null, 0L),
                row,
                "token",
                taskType,
                false,
                "prompt"
        );
    }

    private static RecordsImportModels.DashboardRow row(RecordsImportModels.Example... examples) {
        return new RecordsImportModels.DashboardRow(
                "x",
                900,
                "meaning",
                "reading",
                "search",
                1,
                "reason",
                "reason text",
                1,
                0,
                1,
                examples.length == 0 ? Collections.emptyList() : Arrays.asList(examples)
        );
    }

    private static RecordsImportModels.Example example(String sourceType, String expression) {
        return new RecordsImportModels.Example(
                sourceType,
                1L,
                2L,
                expression,
                "reading",
                "meaning",
                "sentence",
                false,
                0,
                0,
                0,
                null,
                null,
                null
        );
    }
}
