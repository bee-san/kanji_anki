package dev.bee.kanjianki.sync;

import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.data.LocalStore;

public final class SyncSettings {
    public static final String NOTE_TYPE_SETTING_KEY = "note_type_name";
    public static final String EXPRESSION_FIELD_SETTING_KEY = "expression_field";
    public static final String READING_FIELD_SETTING_KEY = "reading_field";
    public static final String MEANING_FIELD_SETTING_KEY = "meaning_field";
    public static final String SENTENCE_FIELD_SETTING_KEY = "sentence_field";
    public static final String FREQUENCY_FIELD_SETTING_KEY = "frequency_field";
    public static final String FREQUENCY_SORT_FIELD_SETTING_KEY = "frequency_sort_field";
    public static final String WRITING_TRIGGER_MISS_DAYS_SETTING_KEY = "writing_trigger_miss_days";
    public static final String RECOGNITION_PROMOTION_PASSES_SETTING_KEY = "recognition_promotion_passes";
    public static final String REAL_DUE_REVIEWS_TO_MOVE_SETTING_KEY = "real_due_reviews_to_move";
    public static final String IMPORT_ACTIVE_CARDS_SETTING_KEY = "import_active_cards";
    public static final String IMPORT_SUSPENDED_CARDS_SETTING_KEY = "import_suspended_cards";
    public static final String IMPORT_TAGGED_CARDS_SETTING_KEY = "import_tagged_cards";
    public static final String IMPORT_TAGS_SETTING_KEY = "import_tags";
    public static final String IMPORT_WEAK_CARDS_SETTING_KEY = "import_weak_cards";
    public static final String IMPORT_WEAK_FSRS_DIFFICULTY_SETTING_KEY = "import_weak_fsrs_difficulty_threshold";
    public static final String IMPORT_WEAK_LAPSES_SETTING_KEY = "import_weak_lapses_threshold";
    public static final String IMPORT_MIN_MATCHING_CARDS_SETTING_KEY = "import_min_matching_cards_per_kanji";

    private SyncSettings() {
    }

    public static Records.Settings fromStore(LocalStore store) {
        Records.Settings defaults = Records.Settings.kikuDefaults();
        String modelName = store == null
                ? defaults.modelName
                : nonBlank(store.getStringSetting(NOTE_TYPE_SETTING_KEY, defaults.modelName), defaults.modelName);
        String expressionField = fieldSetting(store, EXPRESSION_FIELD_SETTING_KEY, defaults.expressionField, true);
        String readingField = fieldSetting(store, READING_FIELD_SETTING_KEY, defaults.readingField, false);
        String meaningField = fieldSetting(store, MEANING_FIELD_SETTING_KEY, defaults.meaningField, false);
        String sentenceField = fieldSetting(store, SENTENCE_FIELD_SETTING_KEY, defaults.sentenceField, false);
        String frequencyField = fieldSetting(store, FREQUENCY_FIELD_SETTING_KEY, defaults.frequencyField, false);
        String frequencySortField = fieldSetting(store, FREQUENCY_SORT_FIELD_SETTING_KEY, defaults.frequencySortField, false);
        int minRank = store == null
                ? defaults.suspendedRankMin
                : store.getIntSetting("suspended_rank_min", defaults.suspendedRankMin);
        int maxRank = store == null
                ? defaults.suspendedRankMax
                : store.getIntSetting(
                        "suspended_rank_max",
                        store.getIntSetting("suspended_rank_cutoff", defaults.suspendedRankMax)
                );
        int writingTriggerMissDays = store == null
                ? defaults.writingTriggerMissDays
                : store.getIntSetting(WRITING_TRIGGER_MISS_DAYS_SETTING_KEY, defaults.writingTriggerMissDays);
        int recognitionPromotionPasses = store == null
                ? defaults.recognitionPromotionPasses
                : store.getIntSetting(RECOGNITION_PROMOTION_PASSES_SETTING_KEY, defaults.recognitionPromotionPasses);
        int realDueReviewsToMove = store == null
                ? defaults.realDueReviewsToMove
                : store.getIntSetting(REAL_DUE_REVIEWS_TO_MOVE_SETTING_KEY, defaults.realDueReviewsToMove);
        boolean importActiveCards = boolSetting(store, IMPORT_ACTIVE_CARDS_SETTING_KEY, defaults.importActiveCards);
        boolean importSuspendedCards = boolSetting(store, IMPORT_SUSPENDED_CARDS_SETTING_KEY, defaults.importSuspendedCards);
        boolean importTaggedCards = boolSetting(store, IMPORT_TAGGED_CARDS_SETTING_KEY, defaults.importTaggedCards);
        String importTags = store == null
                ? defaults.importTagsText()
                : store.getStringSetting(IMPORT_TAGS_SETTING_KEY, defaults.importTagsText());
        boolean importWeakCards = boolSetting(store, IMPORT_WEAK_CARDS_SETTING_KEY, defaults.importWeakCards);
        double importWeakFsrsDifficulty = store == null
                ? defaults.importWeakFsrsDifficultyThreshold
                : store.getDoubleSetting(IMPORT_WEAK_FSRS_DIFFICULTY_SETTING_KEY, defaults.importWeakFsrsDifficultyThreshold);
        int importWeakLapses = store == null
                ? defaults.importWeakLapsesThreshold
                : store.getIntSetting(IMPORT_WEAK_LAPSES_SETTING_KEY, defaults.importWeakLapsesThreshold);
        int importMinMatchingCards = store == null
                ? defaults.importMinMatchingCardsPerKanji
                : store.getIntSetting(IMPORT_MIN_MATCHING_CARDS_SETTING_KEY, defaults.importMinMatchingCardsPerKanji);
        return new Records.Settings(
                modelName,
                defaults.templateName,
                expressionField,
                readingField,
                meaningField,
                sentenceField,
                frequencyField,
                frequencySortField,
                defaults.matureDays,
                defaults.matureSupportThreshold,
                minRank,
                maxRank,
                defaults.activeQueueCap,
                defaults.newPerDay,
                writingTriggerMissDays,
                recognitionPromotionPasses,
                realDueReviewsToMove,
                importActiveCards,
                importSuspendedCards,
                importTaggedCards,
                Records.parseImportTags(importTags),
                importWeakCards,
                importWeakFsrsDifficulty,
                importWeakLapses,
                importMinMatchingCards
        );
    }

    private static boolean boolSetting(LocalStore store, String key, boolean fallback) {
        if (store == null) {
            return fallback;
        }
        return store.getIntSetting(key, fallback ? 1 : 0) == 1;
    }

    private static String fieldSetting(LocalStore store, String key, String fallback, boolean required) {
        if (store == null) {
            return fallback;
        }
        String value = store.getStringSetting(key, null);
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return required && trimmed.isEmpty() ? fallback : trimmed;
    }

    private static String nonBlank(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }
}
