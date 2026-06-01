package dev.bee.kanjianki

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.EditText
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.anki.FakeAnkiDroidProvider
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidHelperInstrumentedTest {
    @Test
    fun noteTypeChooserReadsFakeProviderThroughAndroidEntryPoint() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        val providerTypes = gateway.noteTypes()
        val choices = NoteTypeFieldMappings.choicesFrom(providerTypes)
        assertFalse(choices.isEmpty())

        val direct = DirectExecutorService()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                NoteTypeFieldMappings.choose(
                    activity,
                    gateway,
                    direct,
                    Handler(Looper.getMainLooper()),
                    newInputBundle(activity).inputs
                )
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()

            scenario.onActivity { activity ->
                NoteTypeFieldMappings.choose(
                    activity,
                    AnkiDroidGateway.testProvider(activity, "dev.bee.kanjianki.no_note_type_provider"),
                    direct,
                    Handler(Looper.getMainLooper()),
                    newInputBundle(activity).inputs
                )
            }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }

        assertTrue(direct.executed)
    }

    @Test
    fun noteTypeChooserSelectionAppliesConfiguredFieldGuessesToRealInputs() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val inputBundle = newInputBundle(context)
        val ui = FakeChooserUi()

        NoteTypeFieldMappings.presentNoteTypes(
            listOf(
                NoteTypeFieldMappings.Choice(
                    "Kiku Mining",
                    listOf("Japanese", "Kana", "Definition", "Context", "Freq", "FrequencySort")
                )
            ),
            inputBundle.inputs,
            ui
        )
        ui.select(0)

        assertEquals("Kiku Mining", inputBundle.noteType.text.toString())
        assertEquals("Japanese", inputBundle.expression.text.toString())
        assertEquals("Kana", inputBundle.reading.text.toString())
        assertEquals("Definition", inputBundle.meaning.text.toString())
        assertEquals("Context", inputBundle.sentence.text.toString())
        assertEquals("Freq", inputBundle.frequency.text.toString())
        assertEquals("FrequencySort", inputBundle.frequencySort.text.toString())
    }

    @Test
    fun noteTypeMappingFallsBackToFirstTwoFieldsWhenKnownNamesAreMissing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expression = EditText(context)
        val reading = EditText(context)
        val meaning = EditText(context)
        val inputs = NoteTypeFieldMappings.Inputs(
            EditText(context),
            expression,
            reading,
            meaning,
            EditText(context),
            EditText(context),
            EditText(context)
        )

        NoteTypeFieldMappings.applyFieldGuesses(
            listOf("A", "B"),
            inputs
        )

        assertEquals("A", expression.text.toString())
        assertEquals("B", meaning.text.toString())
        assertEquals("", reading.text.toString())
    }

    @Test
    fun noteTypeMappingLeavesEmptyFieldsWhenThereIsNothingToGuess() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val expression = EditText(context)
        val meaning = EditText(context)
        val inputs = NoteTypeFieldMappings.Inputs(
            EditText(context),
            expression,
            EditText(context),
            meaning,
            EditText(context),
            EditText(context),
            EditText(context)
        )

        NoteTypeFieldMappings.applyFieldGuesses(
            emptyList(),
            inputs
        )

        assertEquals("", expression.text.toString())
        assertEquals("", meaning.text.toString())
    }

    @Test
    fun attributionTextsReadBundledResourcesAndDictionaryManifest() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        val kanjiVg = AttributionTexts.kanjiVg(context)
        val dictionary = AttributionTexts.dictionarySources(context)

        assertFalse(kanjiVg.isBlank())
        assertTrue(kanjiVg.contains("KanjiVG"))
        assertFalse(dictionary.isBlank())
        assertTrue(dictionary.contains("KANJIDIC2") || dictionary.contains("Dictionary"))
        assertEquals(
            "Dictionary manifest is empty.",
            AttributionTexts.dictionarySourcesFromManifestText("{\"sources\":[]}")
        )
        assertEquals(
            "KANJIDIC2 dictionary data from EDRDG, Jiten rank data, and KanjiVG stroke data.",
            AttributionTexts.dictionarySourcesFromManifestText("{}")
        )
        assertEquals(
            "KANJIDIC2 dictionary data from EDRDG, Jiten rank data, and KanjiVG stroke data.",
            AttributionTexts.dictionarySourcesFromManifestText("{\"sources\":\"not an array\"}")
        )
        assertEquals(
            "kanjidic2\nVersion: 2026\nSHA-256: abc",
            AttributionTexts.dictionarySourcesFromManifestText(
                "{\"sources\":[{\"id\":\"kanjidic2\",\"source_sha256\":\"abc\",\"database_version\":\"2026\"}]}")
        )
    }

    private fun newInputBundle(context: Context): InputBundle {
        val noteType = EditText(context)
        val expression = EditText(context)
        val reading = EditText(context)
        val meaning = EditText(context)
        val sentence = EditText(context)
        val frequency = EditText(context)
        val frequencySort = EditText(context)
        return InputBundle(
            noteType,
            expression,
            reading,
            meaning,
            sentence,
            frequency,
            frequencySort,
            NoteTypeFieldMappings.Inputs(
                noteType,
                expression,
                reading,
                meaning,
                sentence,
                frequency,
                frequencySort
            )
        )
    }

    private data class InputBundle(
        val noteType: EditText,
        val expression: EditText,
        val reading: EditText,
        val meaning: EditText,
        val sentence: EditText,
        val frequency: EditText,
        val frequencySort: EditText,
        val inputs: NoteTypeFieldMappings.Inputs
    )

    private class FakeChooserUi : NoteTypeFieldMappings.ChooserUi {
        private var selection: NoteTypeFieldMappings.Selection? = null

        override fun showShortMessage(message: String) {
            // This fake only captures chooser selections.
        }

        override fun showLongMessage(message: String) {
            // This fake only captures chooser selections.
        }

        override fun showNoteTypeChoices(
            title: String,
            labels: Array<String>,
            selection: NoteTypeFieldMappings.Selection
        ) {
            this.selection = selection
        }

        fun select(index: Int) {
            selection?.select(index)
        }
    }

    private class DirectExecutorService : AbstractExecutorService() {
        private var shutdown = false
        private var terminated = false
        var executed = false
            private set

        override fun shutdown() {
            shutdown = true
            terminated = true
        }

        override fun shutdownNow(): MutableList<Runnable> {
            shutdown = true
            terminated = true
            return mutableListOf()
        }

        override fun isShutdown(): Boolean = shutdown

        override fun isTerminated(): Boolean = terminated

        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = terminated

        override fun execute(command: Runnable) {
            executed = true
            command.run()
        }
    }
}
