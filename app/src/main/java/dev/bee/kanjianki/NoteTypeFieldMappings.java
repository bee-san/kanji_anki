package dev.bee.kanjianki;

import android.app.AlertDialog;
import android.os.Handler;
import android.widget.EditText;
import android.widget.Toast;

import dev.bee.kanjianki.anki.AnkiDroidGateway;
import dev.bee.kanjianki.core.Records;

import java.util.List;
import java.util.concurrent.ExecutorService;

final class NoteTypeFieldMappings {
    private NoteTypeFieldMappings() {
    }

    static void choose(
            MainActivity activity,
            AnkiDroidGateway gateway,
            ExecutorService io,
            Handler main,
            Inputs inputs
    ) {
        Toast.makeText(activity, "Reading AnkiDroid note types.", Toast.LENGTH_SHORT).show();
        io.execute(() -> {
            try {
                List<AnkiDroidGateway.NoteType> noteTypes = gateway.noteTypes();
                main.post(() -> showDialog(activity, inputs, noteTypes));
            } catch (Exception error) {
                main.post(() -> Toast.makeText(activity, errorMessage(error), Toast.LENGTH_LONG).show());
            }
        });
    }

    private static void showDialog(MainActivity activity, Inputs inputs, List<AnkiDroidGateway.NoteType> noteTypes) {
        if (noteTypes == null || noteTypes.isEmpty()) {
            Toast.makeText(activity, "No note types found in AnkiDroid.", Toast.LENGTH_LONG).show();
            return;
        }
        String[] labels = new String[noteTypes.size()];
        for (int i = 0; i < noteTypes.size(); i++) {
            AnkiDroidGateway.NoteType noteType = noteTypes.get(i);
            labels[i] = noteType.name + " (" + countText(noteType.fields.size(), "field", "fields") + ")";
        }
        new AlertDialog.Builder(activity)
                .setTitle("Choose note type")
                .setItems(labels, (dialog, which) -> {
                    AnkiDroidGateway.NoteType selected = noteTypes.get(which);
                    inputs.noteType.setText(selected.name);
                    applyFieldGuesses(selected, inputs);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static void applyFieldGuesses(AnkiDroidGateway.NoteType noteType, Inputs inputs) {
        Records.Settings defaults = Records.Settings.kikuDefaults();
        inputs.expression.setText(firstMatchingField(noteType.fields, defaults.expressionField, "Front", "Japanese", "Word", "Vocabulary", "Term"));
        inputs.reading.setText(firstMatchingField(noteType.fields, defaults.readingField, "Reading", "Kana", "Pronunciation"));
        inputs.meaning.setText(firstMatchingField(noteType.fields, defaults.meaningField, "Meaning", "Back", "Definition", "Glossary"));
        inputs.sentence.setText(firstMatchingField(noteType.fields, defaults.sentenceField, "Context", "Example", "ExampleSentence"));
        inputs.frequency.setText(firstMatchingField(noteType.fields, defaults.frequencyField, "Freq"));
        inputs.frequencySort.setText(firstMatchingField(noteType.fields, defaults.frequencySortField, "FrequencySort", defaults.frequencyField));
        if (inputs.expression.getText().toString().trim().isEmpty() && !noteType.fields.isEmpty()) {
            inputs.expression.setText(noteType.fields.get(0));
        }
        if (inputs.meaning.getText().toString().trim().isEmpty() && noteType.fields.size() > 1) {
            inputs.meaning.setText(noteType.fields.get(1));
        }
    }

    private static String firstMatchingField(List<String> fields, String... candidates) {
        for (String candidate : candidates) {
            for (String field : fields) {
                if (field.equalsIgnoreCase(candidate)) {
                    return field;
                }
            }
        }
        return "";
    }

    private static String errorMessage(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "Could not read AnkiDroid note types.";
        }
        return message;
    }

    private static String countText(int count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }

    static final class Inputs {
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
    }
}
