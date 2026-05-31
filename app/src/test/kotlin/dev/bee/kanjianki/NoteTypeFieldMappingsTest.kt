package dev.bee.kanjianki

import dev.bee.kanjianki.anki.AnkiDroidGateway
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Constructor
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections

class NoteTypeFieldMappingsTest {
    @Test
    fun chooseShowsNoNoteTypesMessageWithoutOpeningDialog() {
        val emptyUi = FakeUi()
        NoteTypeFieldMappings.choose({ Collections.emptyList() }, Runnable::run, Runnable::run, FakeInputs(), emptyUi)

        assertEquals(listOf("Reading AnkiDroid note types."), emptyUi.shortMessages)
        assertEquals(listOf("No note types found in AnkiDroid."), emptyUi.longMessages)
        assertFalse(emptyUi.dialogShown())

        val nullUi = FakeUi()
        NoteTypeFieldMappings.choose({ null }, Runnable::run, Runnable::run, FakeInputs(), nullUi)

        assertEquals(listOf("No note types found in AnkiDroid."), nullUi.longMessages)
        assertFalse(nullUi.dialogShown())
    }

    @Test
    fun choicesFromTreatsMissingProviderModelsAsEmpty() {
        assertTrue(NoteTypeFieldMappings.choicesFrom(null).isEmpty())
        assertTrue(NoteTypeFieldMappings.choicesFrom(Collections.emptyList()).isEmpty())
    }

    @Test
    fun choicesFromCopiesProviderModelsAndFields() {
        val choices = NoteTypeFieldMappings.choicesFrom(
            listOf(providerNoteType("Mining", listOf("Front", "Back")))
        )
        val inputs = FakeInputs()

        assertEquals(1, choices.size)
        assertEquals("Mining (2 fields)", NoteTypeFieldMappings.label(choices[0]))
        NoteTypeFieldMappings.chooseNoteType(choices[0], inputs)
        assertEquals("Mining", inputs.noteTypeValue)
        assertEquals("Front", inputs.expressionValue)
        assertEquals("Back", inputs.meaningValue)
    }

    @Test
    fun presentNoteTypesBuildsChooserLabelsFromNamesAndFieldCounts() {
        val ui = FakeUi()

        NoteTypeFieldMappings.presentNoteTypes(
            listOf(
                choiceWithNullFields("Empty"),
                choice("Kiku", "Expression"),
                choice("Basic", "Front", "Back")
            ),
            FakeInputs(),
            ui
        )

        assertEquals("Choose note type", ui.title)
        assertArrayEquals(arrayOf("Empty (0 fields)", "Kiku (1 field)", "Basic (2 fields)"), ui.labels)
        assertTrue(ui.longMessages.isEmpty())
    }

    @Test
    fun choiceNormalizesNullNamesAndFieldsForLabels() {
        val blank = NoteTypeFieldMappings.Choice(null, null)

        assertEquals(" (0 fields)", NoteTypeFieldMappings.label(blank))
        assertArrayEquals(arrayOf(" (0 fields)"), NoteTypeFieldMappings.labels(listOf(blank)))
    }

    @Test
    fun selectedNoteTypeFillsModelNameAndAppliesFieldGuesses() {
        val ui = FakeUi()
        val inputs = FakeInputs()
        NoteTypeFieldMappings.presentNoteTypes(
            listOf(
                choice("Wrong", "Front", "Back"),
                choice("Kiku Mining", "Japanese", "Kana", "Definition", "Context", "Freq", "FrequencySort")
            ),
            inputs,
            ui
        )

        ui.select(1)

        assertEquals("Kiku Mining", inputs.noteTypeValue)
        assertEquals("Japanese", inputs.expressionValue)
        assertEquals("Kana", inputs.readingValue)
        assertEquals("Definition", inputs.meaningValue)
        assertEquals("Context", inputs.sentenceValue)
        assertEquals("Freq", inputs.frequencyValue)
        assertEquals("FrequencySort", inputs.frequencySortValue)
    }

    @Test
    fun fieldGuessesFallBackToFirstTwoFieldsWhenKnownNamesAreMissing() {
        val inputs = FakeInputs()

        NoteTypeFieldMappings.applyFieldGuesses(listOf("表", "裏"), inputs)

        assertEquals("表", inputs.expressionValue)
        assertEquals("裏", inputs.meaningValue)
        assertEquals("", inputs.readingValue)
        assertEquals("", inputs.sentenceValue)
        assertEquals("", inputs.frequencyValue)
        assertEquals("", inputs.frequencySortValue)
    }

    @Test
    fun fieldGuessesLeaveMappingsBlankWhenNoteTypeHasNoFields() {
        val inputs = FakeInputs()

        NoteTypeFieldMappings.applyFieldGuesses(Collections.emptyList(), inputs)

        assertEquals("", inputs.expressionValue)
        assertEquals("", inputs.meaningValue)
        assertEquals("", inputs.readingValue)
        assertEquals("", inputs.sentenceValue)
        assertEquals("", inputs.frequencyValue)
        assertEquals("", inputs.frequencySortValue)
    }

    @Test
    fun chooseShowsGatewayExceptionMessageWithoutOpeningDialog() {
        val ui = FakeUi()

        NoteTypeFieldMappings.choose(
            { throw AnkiDroidGateway.SyncFailure.retryable("Provider unavailable") },
            Runnable::run,
            Runnable::run,
            FakeInputs(),
            ui
        )

        assertEquals(listOf("Reading AnkiDroid note types."), ui.shortMessages)
        assertEquals(listOf("Provider unavailable"), ui.longMessages)
        assertFalse(ui.dialogShown())
    }

    @Test
    fun firstMatchingFieldPrefersConfiguredCandidateWithCaseInsensitiveMatch() {
        val fields = listOf("Front", "sentence", "expression", "Meaning")

        val match = NoteTypeFieldMappings.firstMatchingField(fields, "Expression", "Front", "Japanese")

        assertEquals("expression", match)
    }

    @Test
    fun firstMatchingFieldReturnsEmptyWhenNoCandidateMatches() {
        val fields = listOf("Front", "Back", "Reading")

        val match = NoteTypeFieldMappings.firstMatchingField(fields, "Expression", "Meaning")

        assertEquals("", match)
    }

    @Test
    fun errorMessageFallsBackForNullAndBlankExceptionMessages() {
        val expected = "Could not read AnkiDroid note types."

        assertEquals(expected, NoteTypeFieldMappings.errorMessage(Exception()))
        assertEquals(expected, NoteTypeFieldMappings.errorMessage(Exception("   ")))
    }

    @Test
    fun errorMessageUsesNonBlankExceptionMessage() {
        assertEquals("Provider unavailable", NoteTypeFieldMappings.errorMessage(Exception("Provider unavailable")))
    }

    private fun choice(name: String, vararg fields: String): NoteTypeFieldMappings.Choice {
        return NoteTypeFieldMappings.Choice(name, listOf(*fields))
    }

    private fun choiceWithNullFields(name: String): NoteTypeFieldMappings.Choice {
        return NoteTypeFieldMappings.Choice(name, null)
    }

    private fun providerNoteType(name: String, fields: List<String>): AnkiDroidGateway.NoteType {
        val constructor: Constructor<AnkiDroidGateway.NoteType> =
            AnkiDroidGateway.NoteType::class.java.getDeclaredConstructor(Long::class.javaPrimitiveType, String::class.java, List::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(42L, name, fields)
    }

    private class FakeUi : NoteTypeFieldMappings.ChooserUi {
        val shortMessages = ArrayList<String>()
        val longMessages = ArrayList<String>()
        var title: String? = null
        var labels: Array<String>? = null
        private var selection: NoteTypeFieldMappings.Selection? = null

        override fun showShortMessage(message: String) {
            shortMessages.add(message)
        }

        override fun showLongMessage(message: String) {
            longMessages.add(message)
        }

        override fun showNoteTypeChoices(title: String, labels: Array<String>, selection: NoteTypeFieldMappings.Selection) {
            this.title = title
            this.labels = labels
            this.selection = selection
        }

        fun dialogShown(): Boolean = labels != null

        fun select(index: Int) {
            selection!!.select(index)
        }
    }

    private class FakeInputs : NoteTypeFieldMappings.FieldInputs {
        var noteTypeValue = ""
        var expressionValue = ""
        var readingValue = ""
        var meaningValue = ""
        var sentenceValue = ""
        var frequencyValue = ""
        var frequencySortValue = ""

        override fun setNoteType(value: String?) {
            noteTypeValue = value.orEmpty()
        }

        override fun setExpression(value: String?) {
            expressionValue = value.orEmpty()
        }

        override fun setReading(value: String?) {
            readingValue = value.orEmpty()
        }

        override fun setMeaning(value: String?) {
            meaningValue = value.orEmpty()
        }

        override fun setSentence(value: String?) {
            sentenceValue = value.orEmpty()
        }

        override fun setFrequency(value: String?) {
            frequencyValue = value.orEmpty()
        }

        override fun setFrequencySort(value: String?) {
            frequencySortValue = value.orEmpty()
        }
    }
}
