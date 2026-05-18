package dev.bee.kanjianki.core;

public final class StudyTextCopy {
    public static final String SIMILAR_REPAIR_REASON = "Why: similar-kanji miss \u00b7 writing repair \u00b7 practice-only";

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

    public static String heroQuestion(RecordsSchedulerModels.StudySession session) {
        if (session != null && StudyTaskTypes.WORD_READING.equals(session.taskType)) {
            return "What is the reading?";
        }
        return "What does this kanji mean?";
    }

    public static String collectionMeaningForSession(RecordsSchedulerModels.StudySession session) {
        if (session == null || session.row == null) {
            return "";
        }
        RecordsImportModels.Example example = StudyExampleSelector.exampleForSession(session);
        if (example != null && !example.meaning.isEmpty()) {
            return example.meaning;
        }
        return session.row.primaryMeaning;
    }

    public static String similarRepairPrompt(RecordsImportModels.SimilarKanjiWritingRepair repair) {
        StringBuilder prompt = new StringBuilder("Repair the shape mix-up");
        if (!repair.promptMeaning.isEmpty()) {
            prompt.append(" for ").append(repair.promptMeaning);
        }
        if (!repair.wrongSelection.isEmpty()) {
            prompt.append(". You picked ").append(repair.wrongSelection).append("; write ").append(repair.repairKanji).append(".");
        } else {
            prompt.append(". Write ").append(repair.repairKanji).append(".");
        }
        return prompt.toString();
    }

    public static String studyReasonLine(
            boolean similarRepairActive,
            RecordsSchedulerModels.StudySession session,
            int matureSupportThreshold,
            long nowMillis
    ) {
        if (similarRepairActive) {
            return SIMILAR_REPAIR_REASON;
        }
        if (session == null || session.row == null) {
            return "";
        }
        return FocusQueueCopy.focusReasonLine(session.row, session.item, nowMillis, matureSupportThreshold);
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
