package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
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
        assertSame(suspended, StudyExampleSelector.exampleForSession(session(StudyTaskTypes.TYPE_READING, row)))
        assertSame(active, StudyExampleSelector.exampleForSession(session(StudyTaskTypes.KANJI_MEANING, row)))
        assertNull(StudyExampleSelector.exampleForSession(null))
    }

    @Test
    fun typeReadingUsesPersistedFailedExpressionAndFullReading() {
        val generic = exampleWith("suspended", "一般", sentence = "一般の文。", reading = "いっぱん")
        val row = row(generic)
        val base = session(StudyTaskTypes.TYPE_READING, row)
        val route = AdaptiveRouteState(
            activeCore = CoreSkill.CONTEXTUAL_READING,
            activeRepairTasks = listOf(StudyTaskTypes.TYPE_READING),
            answerEvidence = AnswerEvidence(
                coreSkill = CoreSkill.CONTEXTUAL_READING,
                failureKind = FailureKind.WRONG_READING,
                renderedExpression = "脱出する",
                renderedReading = "だっしゅつする",
            ),
        )
        val item = base.item!!.copyBuilder()
            .routingVersion(AdaptiveStudyItemPolicy.ROUTING_VERSION)
            .adaptiveRouteStateJson(AdaptiveRouteStateCodec.encode(route))
            .build()
        val exactSession = RecordsSchedulerModels.StudySession(
            item,
            row,
            base.token,
            StudyTaskTypes.TYPE_READING,
            false,
            base.prompt,
        )

        val selected = StudyExampleSelector.exampleForSession(exactSession)

        assertEquals("脱出する", selected?.expression)
        assertEquals("だっしゅつする", selected?.reading)
        assertEquals("", selected?.meaning)
        assertEquals("", selected?.sentence)
        assertEquals("脱出する", StudyTextCopy.wordPrompt(exactSession))
        assertEquals("だっしゅつする", StudyTextCopy.collectionReadingForSession(exactSession))
    }

    @Test
    fun sentenceReadingExamplePrefersBothFieldsSuspendedThenActive() {
        // Only examples with BOTH a sentence and a reading qualify.
        val active = exampleWith("active", "有効", sentence = "有効な文。", reading = "ゆうこう")
        val suspended = exampleWith("suspended", "休止", sentence = "休止の文。", reading = "きゅうし")
        val noSentence = exampleWith("active", "無文", sentence = "", reading = "むぶん")
        val noReading = exampleWith("suspended", "無読", sentence = "文だけ。", reading = "")

        assertSame(suspended, StudyExampleSelector.sentenceReadingExample(row(noSentence, active, suspended)))
        assertSame(active, StudyExampleSelector.sentenceReadingExample(row(noSentence, active)))
        // No example carries both fields → null (rung unavailable for the card).
        assertNull(StudyExampleSelector.sentenceReadingExample(row(noSentence, noReading)))
        assertNull(StudyExampleSelector.sentenceReadingExample(null))
        assertNull(StudyExampleSelector.sentenceReadingExample(row()))
    }

    @Test
    fun exampleForSessionUsesSentenceReadingSelectionForSentenceReadingTasks() {
        val active = exampleWith("active", "有効", sentence = "有効な文。", reading = "ゆうこう")
        val suspended = exampleWith("suspended", "休止", sentence = "休止の文。", reading = "きゅうし")
        val row = row(active, suspended)

        assertSame(suspended, StudyExampleSelector.exampleForSession(session(StudyTaskTypes.SENTENCE_READING, row)))
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
        return exampleWith(sourceType, expression, "sentence", "reading")
    }

    private fun exampleWith(
        sourceType: String,
        expression: String,
        sentence: String,
        reading: String,
    ): RecordsImportModels.Example {
        return RecordsImportModels.Example(
            sourceType,
            1L,
            2L,
            expression,
            reading,
            "meaning",
            sentence,
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
