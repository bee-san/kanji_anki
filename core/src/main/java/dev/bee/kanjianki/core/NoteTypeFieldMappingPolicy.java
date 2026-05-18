package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NoteTypeFieldMappingPolicy {
    private NoteTypeFieldMappingPolicy() {
    }

    public static FieldGuesses guessFields(List<String> fields) {
        List<String> safeFields = fields == null ? Collections.emptyList() : fields;
        RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();
        String expression = firstMatchingField(
                safeFields,
                defaults.expressionField,
                "Front",
                "Japanese",
                "Word",
                "Vocabulary",
                "Term"
        );
        String meaning = firstMatchingField(
                safeFields,
                defaults.meaningField,
                "Meaning",
                "Back",
                "Definition",
                "Glossary"
        );
        if (expression.trim().isEmpty() && !safeFields.isEmpty()) {
            expression = safeFields.get(0);
        }
        if (meaning.trim().isEmpty() && safeFields.size() > 1) {
            meaning = safeFields.get(1);
        }
        return new FieldGuesses(
                expression,
                firstMatchingField(safeFields, defaults.readingField, "Reading", "Kana", "Pronunciation"),
                meaning,
                firstMatchingField(safeFields, defaults.sentenceField, "Context", "Example", "ExampleSentence"),
                firstMatchingField(safeFields, defaults.frequencyField, "Freq"),
                firstMatchingField(safeFields, defaults.frequencySortField, "FrequencySort", defaults.frequencyField)
        );
    }

    public static String firstMatchingField(List<String> fields, String... candidates) {
        List<String> safeFields = fields == null ? Collections.emptyList() : fields;
        for (String candidate : candidates) {
            for (String field : safeFields) {
                if (field != null && candidate != null && field.equalsIgnoreCase(candidate)) {
                    return field;
                }
            }
        }
        return "";
    }

    public static NoteTypeChoice choice(String name, List<String> fields) {
        return new NoteTypeChoice(name, fields);
    }

    public static String label(NoteTypeChoice noteType) {
        NoteTypeChoice safeNoteType = noteType == null ? choice("", Collections.emptyList()) : noteType;
        return safeNoteType.name() + " (" + StudyTextCopy.countText(safeNoteType.fields().size(), "field", "fields") + ")";
    }

    public static String[] labels(List<? extends NoteTypeChoice> noteTypes) {
        List<? extends NoteTypeChoice> safeNoteTypes = noteTypes == null ? Collections.emptyList() : noteTypes;
        String[] labels = new String[safeNoteTypes.size()];
        for (int i = 0; i < safeNoteTypes.size(); i++) {
            labels[i] = label(safeNoteTypes.get(i));
        }
        return labels;
    }

    public static class NoteTypeChoice {
        private final String name;
        private final List<String> fields;

        public NoteTypeChoice(String name, List<String> fields) {
            this.name = name == null ? "" : name;
            this.fields = Collections.unmodifiableList(new ArrayList<>(fields == null ? Collections.emptyList() : fields));
        }

        public String name() {
            return name;
        }

        public List<String> fields() {
            return fields;
        }
    }

    public static final class FieldGuesses {
        public final String expression;
        public final String reading;
        public final String meaning;
        public final String sentence;
        public final String frequency;
        public final String frequencySort;

        private FieldGuesses(
                String expression,
                String reading,
                String meaning,
                String sentence,
                String frequency,
                String frequencySort
        ) {
            this.expression = expression == null ? "" : expression;
            this.reading = reading == null ? "" : reading;
            this.meaning = meaning == null ? "" : meaning;
            this.sentence = sentence == null ? "" : sentence;
            this.frequency = frequency == null ? "" : frequency;
            this.frequencySort = frequencySort == null ? "" : frequencySort;
        }
    }
}
