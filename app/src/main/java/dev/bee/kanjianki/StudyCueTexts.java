package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.DictionaryLookup;
import dev.bee.kanjianki.core.StudyCuePolicy;

import java.util.List;

final class StudyCueTexts {
    private StudyCueTexts() {
    }

    static List<String> answerLines(
            DictionaryLookup dictionaryLookup,
            RecordsSchedulerModels.StudySession session,
            RecordsImportModels.Example example,
            boolean wordReadingTask
    ) {
        return StudyCuePolicy.answerLines(dictionaryLookup, session, example, wordReadingTask);
    }

    static String displayGlosses(List<String> meanings, int maxMeanings) {
        return StudyCuePolicy.displayGlosses(meanings, maxMeanings);
    }

    static String cleanFallbackMeaning(String raw, String fallback, int maxChars) {
        return StudyCuePolicy.cleanFallbackMeaning(raw, fallback, maxChars);
    }
}
