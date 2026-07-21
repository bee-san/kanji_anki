package dev.bee.kanjianki

import android.app.Activity
import android.app.AlertDialog
import android.os.Handler
import android.widget.EditText
import android.widget.Toast
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.NoteTypeFieldMappingPolicy
import java.util.concurrent.ExecutorService

internal object NoteTypeFieldMappings {
    private const val READING_NOTE_TYPES_MESSAGE = "Reading AnkiDroid note types."
    private const val NO_NOTE_TYPES_MESSAGE = "No note types found in AnkiDroid."
    private const val CHOOSE_NOTE_TYPE_TITLE = "Choose note type"

    @JvmStatic
    fun choose(
        activity: Activity,
        gateway: AnkiDroidGateway,
        io: ExecutorService,
        main: Handler,
        inputs: FieldInputs,
    ) {
        choose(
            NoteTypeReader { choicesFrom(gateway.noteTypes()) },
            Runner { task -> io.execute(task) },
            Runner { task -> main.post(task) },
            inputs,
            AndroidChooserUi(activity),
        )
    }

    @JvmStatic
    fun choose(
        reader: NoteTypeReader,
        io: Runner,
        main: Runner,
        inputs: FieldInputs,
        ui: ChooserUi,
    ) {
        ui.showShortMessage(READING_NOTE_TYPES_MESSAGE)
        io.run(
            Runnable {
                try {
                    val noteTypes = reader.noteTypes()
                    main.run(Runnable { presentNoteTypes(noteTypes, inputs, ui) })
                } catch (error: AnkiDroidGateway.SyncFailure) {
                    main.run(Runnable { ui.showLongMessage(errorMessage(error)) })
                } catch (error: RuntimeException) {
                    main.run(Runnable { ui.showLongMessage(errorMessage(error)) })
                }
            }
        )
    }

    @JvmStatic
    fun presentNoteTypes(noteTypes: List<Choice>?, inputs: FieldInputs, ui: ChooserUi) {
        if (noteTypes.isNullOrEmpty()) {
            ui.showLongMessage(NO_NOTE_TYPES_MESSAGE)
            return
        }
        ui.showNoteTypeChoices(CHOOSE_NOTE_TYPE_TITLE, labels(noteTypes)) { which ->
            if (which in noteTypes.indices) {
                chooseNoteType(noteTypes[which], inputs)
            }
        }
    }

    @JvmStatic
    fun chooseNoteType(noteType: Choice, inputs: FieldInputs) {
        inputs.setNoteType(noteType.name())
        applyFieldGuesses(noteType.fields(), inputs)
    }

    @JvmStatic
    fun applyFieldGuesses(fields: List<String>?, inputs: FieldInputs) {
        val guesses = NoteTypeFieldMappingPolicy.guessFields(fields)
        inputs.setExpression(guesses.expression)
        inputs.setReading(guesses.reading)
        inputs.setMeaning(guesses.meaning)
        inputs.setSentence(guesses.sentence)
        inputs.setFrequency(guesses.frequency)
        inputs.setFrequencySort(guesses.frequencySort)
    }

    @JvmStatic
    fun labels(noteTypes: List<Choice>?): Array<String> {
        val choices = noteTypes.orEmpty().map { noteType -> noteType?.coreChoice }
        return NoteTypeFieldMappingPolicy.labels(choices)
    }

    @JvmStatic
    fun label(noteType: Choice?): String {
        return NoteTypeFieldMappingPolicy.label(noteType?.coreChoice)
    }

    @JvmStatic
    fun firstMatchingField(fields: List<String>?, vararg candidates: String?): String {
        return NoteTypeFieldMappingPolicy.firstMatchingField(fields, *candidates)
    }

    @JvmStatic
    fun errorMessage(error: Exception): String {
        val message = error.message
        return if (message.isNullOrBlank()) {
            "Could not read AnkiDroid note types."
        } else {
            message
        }
    }

    @JvmStatic
    fun choicesFrom(noteTypes: List<AnkiDroidGateway.NoteType>?): List<Choice> {
        if (noteTypes.isNullOrEmpty()) {
            return emptyList()
        }
        return noteTypes.map { noteType -> Choice(noteType.name, noteType.fields) }
    }

    fun interface NoteTypeReader {
        @Throws(AnkiDroidGateway.SyncFailure::class)
        fun noteTypes(): List<Choice>?
    }

    fun interface Runner {
        fun run(task: Runnable)
    }

    interface ChooserUi {
        fun showShortMessage(message: String)

        fun showLongMessage(message: String)

        fun showNoteTypeChoices(title: String, labels: Array<String>, selection: Selection)
    }

    fun interface Selection {
        fun select(index: Int)
    }

    interface FieldInputs {
        fun setNoteType(value: String?)

        fun setExpression(value: String?)

        fun setReading(value: String?)

        fun setMeaning(value: String?)

        fun setSentence(value: String?)

        fun setFrequency(value: String?)

        fun setFrequencySort(value: String?)
    }

    class Choice(name: String?, fields: List<String>?) {
        internal val coreChoice: NoteTypeFieldMappingPolicy.NoteTypeChoice =
            NoteTypeFieldMappingPolicy.choice(name, fields)

        fun name(): String = coreChoice.name()

        fun fields(): List<String> = coreChoice.fields()
    }

    class AndroidChooserUi(private val activity: Activity) : ChooserUi {
        override fun showShortMessage(message: String) {
            if (!activityCanShowUi()) return
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
        }

        override fun showLongMessage(message: String) {
            if (!activityCanShowUi()) return
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
        }

        override fun showNoteTypeChoices(title: String, labels: Array<String>, selection: Selection) {
            if (!activityCanShowUi()) return
            AlertDialog.Builder(activity)
                .setTitle(title)
                .setItems(labels) { _, which -> selection.select(which) }
                .setNegativeButton("Cancel", null)
                .show()
        }

        private fun activityCanShowUi(): Boolean = !activity.isFinishing && !activity.isDestroyed
    }

    class Inputs(
        private val noteType: EditText,
        private val expression: EditText,
        private val reading: EditText,
        private val meaning: EditText,
        private val sentence: EditText,
        private val frequency: EditText,
        private val frequencySort: EditText,
    ) : FieldInputs {
        override fun setNoteType(value: String?) {
            noteType.setText(value)
        }

        override fun setExpression(value: String?) {
            expression.setText(value)
        }

        override fun setReading(value: String?) {
            reading.setText(value)
        }

        override fun setMeaning(value: String?) {
            meaning.setText(value)
        }

        override fun setSentence(value: String?) {
            sentence.setText(value)
        }

        override fun setFrequency(value: String?) {
            frequency.setText(value)
        }

        override fun setFrequencySort(value: String?) {
            frequencySort.setText(value)
        }
    }
}
