package dev.bee.kanjianki;

import dev.bee.kanjianki.core.DictionaryLookup;
import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.core.StudyCue;
import dev.bee.kanjianki.core.StudyCueFormatter;

import java.util.List;

final class StudyCueTexts {
    private StudyCueTexts() {
    }

    static List<String> answerLines(
            DictionaryLookup dictionaryLookup,
            Records.StudySession session,
            Records.Example example,
            boolean wordReadingTask
    ) {
        return StudyCueFormatter.answerLines(studyCue(dictionaryLookup, session, example, wordReadingTask));
    }

    static String displayGlosses(List<String> meanings, int maxMeanings) {
        return StudyCueFormatter.displayGlosses(meanings, maxMeanings);
    }

    static String cleanFallbackMeaning(String raw, String fallback, int maxChars) {
        return StudyCueFormatter.cleanFallbackMeaning(raw, fallback, maxChars);
    }

    private static StudyCue studyCue(
            DictionaryLookup dictionaryLookup,
            Records.StudySession session,
            Records.Example example,
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

    private static StudyCue wordReadingCue(Records.StudySession session, Records.Example example) {
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
