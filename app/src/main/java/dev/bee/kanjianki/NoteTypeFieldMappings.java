package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsSyncModels;
import android.app.AlertDialog;
import android.app.Activity;
import android.os.Handler;
import android.widget.EditText;
import android.widget.Toast;

import dev.bee.kanjianki.anki.AnkiDroidGateway;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;

final class NoteTypeFieldMappings {
    private static final String READING_NOTE_TYPES_MESSAGE = "Reading AnkiDroid note types.";
    private static final String NO_NOTE_TYPES_MESSAGE = "No note types found in AnkiDroid.";
    private static final String CHOOSE_NOTE_TYPE_TITLE = "Choose note type";

    private NoteTypeFieldMappings() {
    }

    static void choose(
            Activity activity,
            AnkiDroidGateway gateway,
            ExecutorService io,
            Handler main,
            Inputs inputs
    ) {
        choose(
                () -> choicesFrom(gateway.noteTypes()),
                io::execute,
                task -> main.post(task),
                inputs,
                new AndroidChooserUi(activity)
        );
    }

    static void choose(
            NoteTypeReader reader,
            Runner io,
            Runner main,
            FieldInputs inputs,
            ChooserUi ui
    ) {
        ui.showShortMessage(READING_NOTE_TYPES_MESSAGE);
        io.run(() -> {
            try {
                List<Choice> noteTypes = reader.noteTypes();
                main.run(() -> presentNoteTypes(noteTypes, inputs, ui));
            } catch (AnkiDroidGateway.SyncFailure | RuntimeException error) {
                main.run(() -> ui.showLongMessage(errorMessage(error)));
            }
        });
    }

    static void presentNoteTypes(List<Choice> noteTypes, FieldInputs inputs, ChooserUi ui) {
        if (noteTypes == null || noteTypes.isEmpty()) {
            ui.showLongMessage(NO_NOTE_TYPES_MESSAGE);
            return;
        }
        ui.showNoteTypeChoices(CHOOSE_NOTE_TYPE_TITLE, labels(noteTypes), which -> chooseNoteType(noteTypes.get(which), inputs));
    }

    static void chooseNoteType(Choice noteType, FieldInputs inputs) {
        inputs.setNoteType(noteType.name);
        applyFieldGuesses(noteType.fields, inputs);
    }

    static void applyFieldGuesses(List<String> fields, FieldInputs inputs) {
        RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();
        String expression = firstMatchingField(fields, defaults.expressionField, "Front", "Japanese", "Word", "Vocabulary", "Term");
        String meaning = firstMatchingField(fields, defaults.meaningField, "Meaning", "Back", "Definition", "Glossary");
        if (expression.trim().isEmpty() && !fields.isEmpty()) {
            expression = fields.get(0);
        }
        if (meaning.trim().isEmpty() && fields.size() > 1) {
            meaning = fields.get(1);
        }
        inputs.setExpression(expression);
        inputs.setReading(firstMatchingField(fields, defaults.readingField, "Reading", "Kana", "Pronunciation"));
        inputs.setMeaning(meaning);
        inputs.setSentence(firstMatchingField(fields, defaults.sentenceField, "Context", "Example", "ExampleSentence"));
        inputs.setFrequency(firstMatchingField(fields, defaults.frequencyField, "Freq"));
        inputs.setFrequencySort(firstMatchingField(fields, defaults.frequencySortField, "FrequencySort", defaults.frequencyField));
    }

    static String[] labels(List<Choice> noteTypes) {
        String[] labels = new String[noteTypes.size()];
        for (int i = 0; i < noteTypes.size(); i++) {
            labels[i] = label(noteTypes.get(i));
        }
        return labels;
    }

    static String label(Choice noteType) {
        return noteType.name + " (" + countText(noteType.fields.size(), "field", "fields") + ")";
    }

    static String firstMatchingField(List<String> fields, String... candidates) {
        for (String candidate : candidates) {
            for (String field : fields) {
                if (field.equalsIgnoreCase(candidate)) {
                    return field;
                }
            }
        }
        return "";
    }

    static String errorMessage(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "Could not read AnkiDroid note types.";
        }
        return message;
    }

    static String countText(int count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }

    static List<Choice> choicesFrom(List<AnkiDroidGateway.NoteType> noteTypes) {
        if (noteTypes == null || noteTypes.isEmpty()) {
            return Collections.emptyList();
        }
        List<Choice> choices = new ArrayList<>(noteTypes.size());
        for (AnkiDroidGateway.NoteType noteType : noteTypes) {
            choices.add(new Choice(noteType.name, noteType.fields));
        }
        return choices;
    }

    @FunctionalInterface
    interface NoteTypeReader {
        List<Choice> noteTypes() throws AnkiDroidGateway.SyncFailure;
    }

    @FunctionalInterface
    interface Runner {
        void run(Runnable task);
    }

    interface ChooserUi {
        void showShortMessage(String message);

        void showLongMessage(String message);

        void showNoteTypeChoices(String title, String[] labels, Selection selection);
    }

    @FunctionalInterface
    interface Selection {
        void select(int index);
    }

    interface FieldInputs {
        void setNoteType(String value);

        void setExpression(String value);

        void setReading(String value);

        void setMeaning(String value);

        void setSentence(String value);

        void setFrequency(String value);

        void setFrequencySort(String value);
    }

    static final class Choice {
        private final String name;
        private final List<String> fields;

        Choice(String name, List<String> fields) {
            this.name = name == null ? "" : name;
            this.fields = Collections.unmodifiableList(new ArrayList<>(fields == null ? Collections.emptyList() : fields));
        }
    }

    static final class AndroidChooserUi implements ChooserUi {
        private final Activity activity;

        AndroidChooserUi(Activity activity) {
            this.activity = activity;
        }

        @Override
        public void showShortMessage(String message) {
            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void showLongMessage(String message) {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        }

        @Override
        public void showNoteTypeChoices(String title, String[] labels, Selection selection) {
            new AlertDialog.Builder(activity)
                    .setTitle(title)
                    .setItems(labels, (dialog, which) -> selection.select(which))
                    .setNegativeButton("Cancel", null)
                    .show();
        }
    }

    static final class Inputs implements FieldInputs {
        private final EditText noteType;
        private final EditText expression;
        private final EditText reading;
        private final EditText meaning;
        private final EditText sentence;
        private final EditText frequency;
        private final EditText frequencySort;

        Inputs(
                EditText noteType,
                EditText expression,
                EditText reading,
                EditText meaning,
                EditText sentence,
                EditText frequency,
                EditText frequencySort
        ) {
            this.noteType = noteType;
            this.expression = expression;
            this.reading = reading;
            this.meaning = meaning;
            this.sentence = sentence;
            this.frequency = frequency;
            this.frequencySort = frequencySort;
        }

        @Override
        public void setNoteType(String value) {
            noteType.setText(value);
        }

        @Override
        public void setExpression(String value) {
            expression.setText(value);
        }

        @Override
        public void setReading(String value) {
            reading.setText(value);
        }

        @Override
        public void setMeaning(String value) {
            meaning.setText(value);
        }

        @Override
        public void setSentence(String value) {
            sentence.setText(value);
        }

        @Override
        public void setFrequency(String value) {
            frequency.setText(value);
        }

        @Override
        public void setFrequencySort(String value) {
            frequencySort.setText(value);
        }
    }
}
