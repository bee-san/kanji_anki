package dev.bee.kanjianki.core

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class StudyExampleSelectorTest {
    @Test
    fun firstExamplePrefersActiveThenFallsBackToFirstExample() {
        val fallback = example("other", "fallback")
        val active = example("active", "active")
        val suspended = example("suspended", "suspended")

        assertSame(active, StudyExampleSelector.firstExample(row(fallback, suspended, active)))
        assertSame(fallback, StudyExampleSelector.firstExample(row(fallback, suspended)))
        assertNull(StudyExampleSelector.firstExample(null))
        assertNull(StudyExampleSelector.firstExample(row()))
    }

    @Test
    fun wordReadingExamplePrefersSuspendedThenActiveThenFirstExample() {
        val fallback = example("other", "fallback")
        val active = example("active", "active")
        val suspended = example("suspended", "suspended")

        assertSame(suspended, StudyExampleSelector.wordReadingExample(row(fallback, active, suspended)))
        assertSame(active, StudyExampleSelector.wordReadingExample(row(fallback, active)))
        assertSame(fallback, StudyExampleSelector.wordReadingExample(row(fallback)))
        assertNull(StudyExampleSelector.wordReadingExample(null))
        assertNull(StudyExampleSelector.wordReadingExample(row()))
    }

    @Test
    fun exampleForSessionUsesWordReadingSelectionOnlyForWordReadingTasks() {
        val active = example("active", "active")
        val suspended = example("suspended", "suspended")
        val row = row(active, suspended)

        assertSame(suspended, StudyExampleSelector.exampleForSession(session(StudyTaskTypes.WORD_READING, row)))
        assertSame(active, StudyExampleSelector.exampleForSession(session(StudyTaskTypes.KANJI_MEANING, row)))
        assertNull(StudyExampleSelector.exampleForSession(null))
    }

    private fun session(taskType: String, row: RecordsImportModels.DashboardRow?): RecordsSchedulerModels.StudySession {
        return RecordsSchedulerModels.StudySession(
            RecordsStudyModels.StudyItem("x", "review", 0L, 1.0, 5.0, 1, 0, 0, 1, null, 0L),
            row,
            "token",
            taskType,
            false,
            "prompt"
        )
    }

    private fun row(vararg examples: RecordsImportModels.Example): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
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
            examples.toList()
        )
    }

    private fun example(sourceType: String, expression: String): RecordsImportModels.Example {
        return RecordsImportModels.Example(
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
        )
    }
}
