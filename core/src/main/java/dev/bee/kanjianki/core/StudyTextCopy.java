package dev.bee.kanjianki.core;

public final class StudyTextCopy {
    private StudyTextCopy() {
    }

    public static String countText(int count, String singular, String plural) {
        return count + " " + (count == 1 ? singular : plural);
    }

    public static String rowMeaning(RecordsImportModels.DashboardRow row) {
        return cleanLearnerText(row == null ? null : row.primaryMeaning, row == null ? null : row.reasonCode, 72);
    }

    public static String sessionClue(DictionaryLookup dictionaryLookup, RecordsSchedulerModels.StudySession session) {
        String raw = sessionClueRawText(session);
        String kanji = session == null || session.item == null ? "" : session.item.kanji;
        return canonicalKanjiMeaning(dictionaryLookup, kanji, raw, 96);
    }

    public static String canonicalKanjiMeaning(DictionaryLookup dictionaryLookup, String kanji, String fallback, int maxChars) {
        DictionaryLookup lookup = dictionaryLookup == null ? DictionaryLookup.empty() : dictionaryLookup;
        DictionaryLookup.KanjiEntry entry = lookup.lookupKanji(kanji);
        if (entry != null) {
            String meaning = StudyCueFormatter.displayGlosses(entry.meanings, 2);
            if (!meaning.isEmpty()) {
                return compact(meaning, maxChars);
            }
        }
        return cleanLearnerText(fallback, "Collection clue", maxChars);
    }

    public static String wordPrompt(RecordsSchedulerModels.StudySession session) {
        RecordsImportModels.Example example = session == null ? null : StudyExampleSelector.wordReadingExample(session.row);
        if (example != null && !example.expression.isEmpty()) {
            return example.expression;
        }
        return session == null || session.item == null ? "" : session.item.kanji;
    }

    public static String cleanLearnerText(String raw, String fallback, int maxChars) {
        return StudyCueFormatter.cleanFallbackMeaning(raw, fallback, maxChars);
    }

    public static String compact(String value, int maxChars) {
        return StudyCueFormatter.compact(value, maxChars);
    }

    private static String sessionClueRawText(RecordsSchedulerModels.StudySession session) {
        if (session == null) {
            return "";
        }
        if (session.row == null || session.row.primaryMeaning.isEmpty()) {
            return session.prompt;
        }
        return session.row.primaryMeaning;
    }
}
