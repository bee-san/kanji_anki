package dev.bee.kanjianki.core;

import java.util.List;

public final class StudyCuePolicy {
    private StudyCuePolicy() {
    }

    public static List<String> answerLines(
            DictionaryLookup dictionaryLookup,
            RecordsSchedulerModels.StudySession session,
            RecordsImportModels.Example example,
            boolean wordReadingTask
    ) {
        return StudyCueFormatter.answerLines(studyCue(dictionaryLookup, session, example, wordReadingTask));
    }

    public static String displayGlosses(List<String> meanings, int maxMeanings) {
        return StudyCueFormatter.displayGlosses(meanings, maxMeanings);
    }

    public static String cleanFallbackMeaning(String raw, String fallback, int maxChars) {
        return StudyCueFormatter.cleanFallbackMeaning(raw, fallback, maxChars);
    }

    public static StudyCue studyCue(
            DictionaryLookup dictionaryLookup,
            RecordsSchedulerModels.StudySession session,
            RecordsImportModels.Example example,
            boolean wordReadingTask
    ) {
        if (session == null || session.row == null) {
            return new StudyCue("", "", "", "");
        }
        if (wordReadingTask) {
            return wordReadingCue(session, example);
        }
        String sourceExpression = example == null ? "" : example.expression;
        String sourceReading = example == null ? session.row.reading : example.reading;
        String ankiMeaning = example != null && !example.meaning.isEmpty()
                ? example.meaning
                : session.row.primaryMeaning;
        return dictionaryLookup.studyCue(
                session.item.kanji,
                ankiMeaning,
                session.row.reading,
                sourceExpression,
                sourceReading
        );
    }

    private static StudyCue wordReadingCue(RecordsSchedulerModels.StudySession session, RecordsImportModels.Example example) {
        String sourceExpression = example == null ? "" : example.expression;
        String sourceReading = example == null ? session.row.reading : example.reading;
        String cueReading = firstNonEmpty(sourceReading, session.row.reading);
        return new StudyCue("", cueReading, firstNonEmpty(sourceExpression), DictionaryLookup.SOURCE_ANKI);
    }

    private static String firstNonEmpty(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }
}
