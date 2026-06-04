package dev.bee.kanjianki.core

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteTypeFieldMappingPolicyTest {
    @Test
    fun guessesKnownKikuAndCommonFieldNames() {
        val guesses = NoteTypeFieldMappingPolicy.guessFields(
            listOf("Japanese", "Kana", "Definition", "Context", "Freq", "FrequencySort")
        )

        assertEquals("Japanese", guesses.expression)
        assertEquals("Kana", guesses.reading)
        assertEquals("Definition", guesses.meaning)
        assertEquals("Context", guesses.sentence)
        assertEquals("Freq", guesses.frequency)
        assertEquals("FrequencySort", guesses.frequencySort)
    }

    @Test
    fun fallsBackToFirstTwoFieldsWhenKnownNamesAreMissing() {
        val guesses = NoteTypeFieldMappingPolicy.guessFields(
            listOf("表", "裏")
        )

        assertEquals("表", guesses.expression)
        assertEquals("裏", guesses.meaning)
        assertEquals("", guesses.reading)
        assertEquals("", guesses.sentence)
        assertEquals("", guesses.frequency)
        assertEquals("", guesses.frequencySort)
    }

    @Test
    fun leavesMappingsBlankWhenNoteTypeHasNoFields() {
        val guesses = NoteTypeFieldMappingPolicy.guessFields(emptyList())

        assertEquals("", guesses.expression)
        assertEquals("", guesses.reading)
        assertEquals("", guesses.meaning)
        assertEquals("", guesses.sentence)
        assertEquals("", guesses.frequency)
        assertEquals("", guesses.frequencySort)
    }

    @Test
    fun matchingFieldPrefersConfiguredCandidateCaseInsensitively() {
        val fields = listOf("Front", "sentence", "expression", "Meaning")

        assertEquals(
            "expression",
            NoteTypeFieldMappingPolicy.firstMatchingField(fields, "Expression", "Front", "Japanese")
        )
    }

    @Test
    fun matchingFieldTreatsNullCollectionsAndValuesAsNoMatch() {
        assertEquals("", NoteTypeFieldMappingPolicy.firstMatchingField(null, "Expression", "Meaning"))
        assertEquals("", NoteTypeFieldMappingPolicy.firstMatchingField(listOf<String?>(null, "Front"), null))
        assertEquals("", NoteTypeFieldMappingPolicy.firstMatchingField(listOf("Front", "Back"), "Expression"))
    }

    @Test
    fun noteTypeChoicesNormalizeAndLabelFieldCounts() {
        val blank = NoteTypeFieldMappingPolicy.choice(null, null)
        val basic = NoteTypeFieldMappingPolicy.choice("Basic", listOf("Front", "Back"))
        val choices = listOf(blank, basic)

        assertEquals("", blank.name())
        assertEquals(0, blank.fields().size)
        assertEquals(" (0 fields)", NoteTypeFieldMappingPolicy.label(blank))
        assertEquals("Basic (2 fields)", NoteTypeFieldMappingPolicy.label(basic))
        assertArrayEquals(
            arrayOf(" (0 fields)", "Basic (2 fields)"),
            NoteTypeFieldMappingPolicy.labels(choices)
        )
        assertArrayEquals(arrayOf<String>(), NoteTypeFieldMappingPolicy.labels(null))
    }
}
