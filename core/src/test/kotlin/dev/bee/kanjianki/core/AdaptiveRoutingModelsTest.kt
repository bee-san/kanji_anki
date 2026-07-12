package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AdaptiveRoutingModelsTest {
    @Test
    fun wireNamesRoundTripWithoutInventingUnknownValues() {
        CoreSkill.entries.forEach { assertEquals(it, CoreSkill.fromWireName(it.wireName())) }
        FailureKind.entries.forEach { assertEquals(it, FailureKind.fromWireName(it.wireName())) }
        EvidenceSource.entries.forEach { assertEquals(it, EvidenceSource.fromWireName(it.wireName())) }
        PresentationVariant.entries.forEach {
            assertEquals(it, PresentationVariant.fromWireName(it.wireName()))
        }

        assertNull(CoreSkill.fromWireName("future_skill"))
        assertNull(FailureKind.fromWireName(""))
        assertNull(EvidenceSource.fromWireName(null))
        assertEquals(EvidenceSource.OBJECTIVE_CHOICE, EvidenceSource.fromWireName("objective"))
        assertNull(PresentationVariant.fromWireName("standard"))
    }

    @Test
    fun legacyTasksMapToExactlyTwoMemoryOwners() {
        val recognitionTasks = listOf(
            StudyTaskTypes.WRITE_KANJI,
            StudyTaskTypes.TYPE_MEANING,
            StudyTaskTypes.SIMILAR_KANJI,
            StudyTaskTypes.MEANING_KANJI,
            StudyTaskTypes.KANJI_MEANING,
            StudyTaskTypes.FONT_MEANING,
        )
        val readingTasks = listOf(
            StudyTaskTypes.KANJI_READING,
            StudyTaskTypes.READING_KANJI,
            StudyTaskTypes.WORD_READING,
            StudyTaskTypes.SENTENCE_READING,
            StudyTaskTypes.TYPE_READING,
        )

        recognitionTasks.forEach {
            assertEquals(CoreSkill.RECOGNITION, AdaptiveCorePolicy.coreForTaskType(it))
        }
        readingTasks.forEach {
            assertEquals(CoreSkill.CONTEXTUAL_READING, AdaptiveCorePolicy.coreForTaskType(it))
        }
        assertNull(AdaptiveCorePolicy.coreForTaskType("future_task"))
        assertEquals(StudyTaskTypes.KANJI_MEANING, AdaptiveCorePolicy.memoryOwnerTaskType(CoreSkill.RECOGNITION))
        assertEquals(StudyTaskTypes.WORD_READING, AdaptiveCorePolicy.memoryOwnerTaskType(CoreSkill.CONTEXTUAL_READING))
        assertEquals(StudyTaskTypes.TYPE_READING, BridgeScheduler.TASK_TYPE_READING)
    }

    @Test
    fun presentationAlternatesDeterministicallyAndBeginsWithStandardForm() {
        assertEquals(
            PresentationVariant.STANDARD_GLYPH,
            AdaptivePresentationPolicy.variant(CoreSkill.RECOGNITION, 0, true, true),
        )
        assertEquals(
            PresentationVariant.FONT_GLYPH,
            AdaptivePresentationPolicy.variant(CoreSkill.RECOGNITION, 1, true, true),
        )
        assertEquals(
            PresentationVariant.STANDARD_GLYPH,
            AdaptivePresentationPolicy.variant(CoreSkill.RECOGNITION, 2, true, true),
        )
        assertEquals(
            PresentationVariant.PLAIN_WORD,
            AdaptivePresentationPolicy.variant(CoreSkill.CONTEXTUAL_READING, 1, false, true),
        )
        assertEquals(
            PresentationVariant.PLAIN_WORD,
            AdaptivePresentationPolicy.variant(CoreSkill.CONTEXTUAL_READING, 1, true, false),
        )
        assertEquals(
            PresentationVariant.SENTENCE_CONTEXT,
            AdaptivePresentationPolicy.variant(CoreSkill.CONTEXTUAL_READING, 1, true, true),
        )
    }
}
