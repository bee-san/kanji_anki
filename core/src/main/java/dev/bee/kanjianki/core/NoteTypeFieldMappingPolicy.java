package dev.bee.kanjianki.core;

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
