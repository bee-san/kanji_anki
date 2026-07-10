package dev.bee.kanjianki.syncdomain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderNotePolicyTest {
    @Test
    fun selectRequiredFieldsDropsBulkyUnusedFields() {
        val largeGlossary = repeatText("media-glossary-entry", 4000)
        val required = listOf(
            "Expression",
            "ExpressionReading",
            "MainDefinition",
            "Sentence",
            "Frequency",
            "FreqSort",
        )

        val fields = ProviderNotePolicy.selectRequiredFields(
            listOf(
                "Expression",
                "ExpressionReading",
                "MainDefinition",
                "Sentence",
                "Frequency",
                "FreqSort",
                "Glossary",
                "PitchGraph",
                "Audio",
            ),
            listOf(
                "確認",
                "かくにん",
                "confirmation",
                "確認した。",
                "123",
                "123",
                largeGlossary,
                repeatText("pitch", 3000),
                "[sound:large.mp3]",
            ),
            required,
        )

        assertEquals(required.size, fields.size)
        assertEquals("確認", fields["Expression"])
        assertEquals("かくにん", fields["ExpressionReading"])
        assertEquals("confirmation", fields["MainDefinition"])
        assertEquals("確認した。", fields["Sentence"])
        assertEquals("123", fields["Frequency"])
        assertEquals("123", fields["FreqSort"])
        assertFalse(fields.containsKey("Glossary"))
        assertFalse(fields.containsKey("PitchGraph"))
        assertFalse(fields.containsKey("Audio"))
    }

    @Test
    fun selectRequiredFieldsUsesEmptyStringsForMissingAndShortFieldRows() {
        val fields = ProviderNotePolicy.selectRequiredFields(
            listOf("Expression", "ExpressionReading"),
            listOf("確認"),
            listOf("Expression", "ExpressionReading", "MainDefinition", "Sentence"),
        )

        assertEquals("確認", fields["Expression"])
        assertEquals("", fields["ExpressionReading"])
        assertEquals("", fields["MainDefinition"])
        assertEquals("", fields["Sentence"])
    }

    @Test
    fun selectRequiredFieldsSkipsBlankOptionalCustomMappings() {
        val fields = ProviderNotePolicy.selectRequiredFields(
            listOf("Front", "Reading", "Back", "Example", "Frequency", "FrequencySort"),
            listOf("確認", "かくにん", "confirmation", "確認した。", "123", "123"),
            listOf("Front", "Back"),
        )

        assertEquals(2, fields.size)
        assertEquals("確認", fields["Front"])
        assertEquals("confirmation", fields["Back"])
        assertFalse(fields.containsKey(""))
        assertFalse(fields.containsKey("Reading"))
    }

    @Test
    fun fieldMappingPreservesEmptyAnkiFieldSlots() {
        val fields = ProviderNotePolicy.selectRequiredFields(
            listOf("Front", "Reading", "Back", "Example"),
            listOf("確認", "", "confirmation", ""),
            listOf("Front", "Reading", "Back"),
        )

        assertEquals("確認", fields["Front"])
        assertEquals("", fields["Reading"])
        assertEquals("confirmation", fields["Back"])
    }

    @Test
    fun currentAndLegacyArchiveTagsAreRecognized() {
        assertTrue(ProviderNotePolicy.isArchivedTagPresent(listOf("leech", "kani_archived")))
        assertTrue(ProviderNotePolicy.isArchivedTagPresent(listOf("marked", "kanji_anki_archived")))
        assertTrue(ProviderNotePolicy.isArchivedTagPresent(listOf("kani_archived", "kanji_anki_archived")))
        assertFalse(ProviderNotePolicy.isArchivedTagPresent(listOf("marked")))
        assertFalse(ProviderNotePolicy.isArchivedTagPresent(null))
        assertTrue(ProviderNotePolicy.isRepairedTagPresent(listOf("leech", ProviderNotePolicy.REPAIRED_TAG)))
        assertFalse(ProviderNotePolicy.isRepairedTagPresent(listOf("kani_archived")))
        assertFalse(ProviderNotePolicy.isRepairedTagPresent(null))
    }

    @Test
    fun browserQuerySearchUsesRawAnkiSyntax() {
        val search = ProviderNotePolicy.browserQuerySearch("tag:Kani marked:1")

        assertEquals("tag:Kani marked:1", search)
        assertEquals("note:\"Kiku\"", ProviderNotePolicy.modelSearch("Kiku"))
    }

    @Test
    fun modelSearchEscapesQuotesAndBackslashesInModelNames() {
        assertEquals(
            "note:\"Kiku \\\"Main\\\" \\\\ One\"",
            ProviderNotePolicy.modelSearch("Kiku \"Main\" \\ One"),
        )
    }

    @Test
    fun jvmStaticBridgeIsInvocableFromJavaReflection() {
        val method = ProviderNotePolicy::class.java.getDeclaredMethod(
            "isArchivedTagPresent",
            List::class.java,
        )

        assertNotNull(method.invoke(null, listOf("kani_archived")))
        assertTrue(method.invoke(null, listOf("kani_archived")) as Boolean)
    }

    private fun repeatText(value: String, count: Int): String = buildString(value.length * count) {
        repeat(count) {
            append(value)
        }
    }
}
