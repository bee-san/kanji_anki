package dev.bee.kanjianki.core;

public final class SettingsNoteTypeTextCopy {
    private SettingsNoteTypeTextCopy() {
    }

    public static String noteTypeFieldsTitle() {
        return "Note type & clue fields";
    }

    public static String noteTypeUsingText(String modelName) {
        return "Using " + String.valueOf(modelName);
    }

    public static String noteTypeFieldsBody() {
        return "Default: Kiku. This single card owns the note type and all field mapping so clue configuration is not repeated elsewhere.";
    }

    public static String requiredFieldsTitle() {
        return "Required fields";
    }

    public static String requiredFieldsBody() {
        return "Expression = kanji source, ExpressionReading = reading, MainDefinition = meaning, Sentence = context, Frequency/FreqSort = metadata.";
    }

    public static String expressionFieldLabel() {
        return "Expression field";
    }

    public static String readingFieldLabel() {
        return "Reading field";
    }

    public static String meaningFieldLabel() {
        return "Meaning field";
    }

    public static String sentenceFieldLabel() {
        return "Sentence field";
    }

    public static String frequencyFieldLabel() {
        return "Frequency field";
    }

    public static String frequencySortFieldLabel() {
        return "Frequency sort field";
    }

    public static String chooseFromAnkiDroidLabel() {
        return "Choose from AnkiDroid";
    }

    public static String useKikuLabel() {
        return "Use Kiku";
    }

    public static String saveNoteTypeLabel() {
        return "Save note type";
    }

    public static String noteTypeRequiredToast() {
        return "Enter a note type name.";
    }

    public static String expressionFieldRequiredToast() {
        return "Choose the field that contains kanji.";
    }

    public static String noteTypeSavedToast() {
        return "Note type saved. Sync again to rebuild practice.";
    }
}
