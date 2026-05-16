package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public abstract class RecordsSyncModels extends RecordsBase {
    public static final class Settings {
        public final String modelName;
        public final String templateName;
        public final String expressionField;
        public final String readingField;
        public final String meaningField;
        public final String sentenceField;
        public final String frequencyField;
        public final String frequencySortField;
        public final int matureDays;
        public final int matureSupportThreshold;
        public final int suspendedRankMin;
        public final int suspendedRankMax;
        public final int suspendedRankCutoff;
        public final int activeQueueCap;
        public final int newPerDay;
        public final int writingTriggerMissDays;
        public final int recognitionPromotionPasses;
        public final int realDueReviewsToMove;
        public final int ladderPromotionIntervalDays;
        public final int ladderDemotionFailStreak;
        public final boolean importActiveCards;
        public final boolean importSuspendedCards;
        public final boolean importTaggedCards;
        public final List<String> importTags;
        public final boolean importWeakCards;
        public final double importWeakFsrsDifficultyThreshold;
        public final int importWeakLapsesThreshold;
        public final int importMinMatchingCardsPerKanji;
        public final boolean importBrowserQueryCards;
        public final String importBrowserQuery;
        public final String newCardSortMode;

        public Settings(
                String modelName,
                String templateName,
                String expressionField,
                String readingField,
                String meaningField,
                String sentenceField,
                Object... rest
        ) {
            SettingsArgs args = SettingsArgs.from(rest);
            this.modelName = modelName;
            this.templateName = templateName;
            this.expressionField = expressionField;
            this.readingField = readingField;
            this.meaningField = meaningField;
            this.sentenceField = sentenceField;
            this.frequencyField = args.frequencyField;
            this.frequencySortField = args.frequencySortField;
            this.matureDays = args.matureDays;
            this.matureSupportThreshold = args.matureSupportThreshold;
            int normalizedMin = Math.max(1, Math.min(20000, args.suspendedRankMin));
            int normalizedMax = Math.max(1, Math.min(20000, args.suspendedRankMax));
            if (normalizedMin > normalizedMax) {
                int swap = normalizedMin;
                normalizedMin = normalizedMax;
                normalizedMax = swap;
            }
            this.suspendedRankMin = normalizedMin;
            this.suspendedRankMax = normalizedMax;
            this.suspendedRankCutoff = normalizedMax;
            this.activeQueueCap = args.activeQueueCap;
            this.newPerDay = args.newPerDay;
            this.writingTriggerMissDays = Math.max(1, args.writingTriggerMissDays);
            this.recognitionPromotionPasses = Math.max(1, args.recognitionPromotionPasses);
            this.realDueReviewsToMove = Math.max(1, args.realDueReviewsToMove);
            this.ladderPromotionIntervalDays = Math.max(1, args.ladderPromotionIntervalDays);
            this.ladderDemotionFailStreak = Math.max(1, args.ladderDemotionFailStreak);
            this.importActiveCards = args.importActiveCards;
            this.importSuspendedCards = args.importSuspendedCards;
            this.importTags = Collections.unmodifiableList(normalizeImportTags(args.importTags));
            this.importTaggedCards = args.importTaggedCards && !this.importTags.isEmpty();
            this.importWeakCards = args.importWeakCards;
            this.importWeakFsrsDifficultyThreshold = finitePositive(args.importWeakFsrsDifficultyThreshold)
                    ? Math.max(1.0, Math.min(10.0, args.importWeakFsrsDifficultyThreshold))
                    : DEFAULT_IMPORT_WEAK_FSRS_DIFFICULTY;
            this.importWeakLapsesThreshold = Math.max(1, Math.min(100, args.importWeakLapsesThreshold));
            this.importMinMatchingCardsPerKanji = Math.max(1, Math.min(1000, args.importMinMatchingCardsPerKanji));
            this.importBrowserQueryCards = args.importBrowserQueryCards;
            this.importBrowserQuery = nullToEmpty(args.importBrowserQuery);
            this.newCardSortMode = normalizeNewCardSortMode(args.newCardSortMode);
        }

        public boolean importTaggedCardsEnabled() {
            return importTaggedCards;
        }

        public boolean hasImportSourceEnabled() {
            return importActiveCards || importSuspendedCards || importTaggedCardsEnabled() || importWeakCards || browserQueryImportEnabled();
        }

        public boolean browserQueryImportEnabled() {
            return importBrowserQueryCards && !normalizedBrowserQuery().isEmpty();
        }

        public String normalizedBrowserQuery() {
            return importBrowserQuery.trim();
        }

        public String importTagsText() {
            return String.join(" ", importTags);
        }

        public static String normalizeNewCardSortMode(String value) {
            if (NEW_CARD_SORT_FSRS_DIFFICULTY.equals(value)
                    || NEW_CARD_SORT_RETRIEVABILITY_RISK.equals(value)
                    || NEW_CARD_SORT_KANI_WEAKNESS.equals(value)) {
                return value;
            }
            return DEFAULT_NEW_CARD_SORT_MODE;
        }

        protected static boolean finitePositive(double value) {
            return !Double.isNaN(value) && !Double.isInfinite(value) && value > 0.0;
        }

        protected static List<String> normalizeImportTags(List<String> rawTags) {
            if (rawTags.isEmpty()) {
                return Collections.emptyList();
            }
            Set<String> parsed = new LinkedHashSet<>();
            for (String tag : rawTags) {
                String trimmed = tag.trim();
                if (!trimmed.isEmpty()) {
                    parsed.add(trimmed);
                }
            }
            return new ArrayList<>(parsed);
        }

        protected static final class SettingsArgs {
            String frequencyField;
            String frequencySortField;
            int matureDays;
            int matureSupportThreshold;
            int suspendedRankMin = DEFAULT_SUSPENDED_RANK_MIN;
            int suspendedRankMax;
            int activeQueueCap;
            int newPerDay;
            int writingTriggerMissDays = DEFAULT_WRITING_TRIGGER_MISS_DAYS;
            int recognitionPromotionPasses = DEFAULT_RECOGNITION_PROMOTION_PASSES;
            int realDueReviewsToMove = DEFAULT_REAL_DUE_REVIEWS_TO_MOVE;
            int ladderPromotionIntervalDays = DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS;
            int ladderDemotionFailStreak = DEFAULT_LADDER_DEMOTION_FAIL_STREAK;
            boolean importActiveCards = DEFAULT_IMPORT_ACTIVE_CARDS;
            boolean importSuspendedCards = DEFAULT_IMPORT_SUSPENDED_CARDS;
            boolean importTaggedCards = DEFAULT_IMPORT_TAGGED_CARDS;
            List<String> importTags = Collections.emptyList();
            boolean importWeakCards = DEFAULT_IMPORT_WEAK_CARDS;
            double importWeakFsrsDifficultyThreshold = DEFAULT_IMPORT_WEAK_FSRS_DIFFICULTY;
            int importWeakLapsesThreshold = DEFAULT_IMPORT_WEAK_LAPSES;
            int importMinMatchingCardsPerKanji = DEFAULT_IMPORT_MIN_MATCHING_CARDS_PER_KANJI;
            boolean importBrowserQueryCards = DEFAULT_IMPORT_BROWSER_QUERY_CARDS;
            String importBrowserQuery = DEFAULT_IMPORT_BROWSER_QUERY;
            String newCardSortMode = DEFAULT_NEW_CARD_SORT_MODE;

            static SettingsArgs from(Object[] rest) {
                requireArgCount(CONTEXT_SETTINGS, rest, 7, 8, 9, 10, 11, 19, 21, 22, 24);
                SettingsArgs args = new SettingsArgs();
                args.frequencyField = stringArg(rest, 0, CONTEXT_SETTINGS);
                args.frequencySortField = stringArg(rest, 1, CONTEXT_SETTINGS);
                args.matureDays = intArg(rest, 2, CONTEXT_SETTINGS);
                args.matureSupportThreshold = intArg(rest, 3, CONTEXT_SETTINGS);
                if (rest.length <= 8) {
                    args.suspendedRankMax = intArg(rest, 4, CONTEXT_SETTINGS);
                    args.activeQueueCap = intArg(rest, 5, CONTEXT_SETTINGS);
                    args.newPerDay = intArg(rest, 6, CONTEXT_SETTINGS);
                    if (rest.length == 8) {
                        args.writingTriggerMissDays = intArg(rest, 7, CONTEXT_SETTINGS);
                    }
                } else {
                    args.suspendedRankMin = intArg(rest, 4, CONTEXT_SETTINGS);
                    args.suspendedRankMax = intArg(rest, 5, CONTEXT_SETTINGS);
                    args.activeQueueCap = intArg(rest, 6, CONTEXT_SETTINGS);
                    args.newPerDay = intArg(rest, 7, CONTEXT_SETTINGS);
                    args.writingTriggerMissDays = intArg(rest, 8, CONTEXT_SETTINGS);
                    if (rest.length >= 10) {
                        args.recognitionPromotionPasses = intArg(rest, 9, CONTEXT_SETTINGS);
                    }
                    if (rest.length >= 11) {
                        args.realDueReviewsToMove = intArg(rest, 10, CONTEXT_SETTINGS);
                    } else if (rest.length >= 10) {
                        // If only the two legacy promotion knobs are provided, use their
                        // stricter value as the ladder-move threshold so behaviour stays
                        // consistent until callers migrate to the new setting.
                        args.realDueReviewsToMove = Math.max(
                                args.writingTriggerMissDays,
                                args.recognitionPromotionPasses
                        );
                    }
                }
                if (rest.length >= 19) {
                    args.importActiveCards = booleanArg(rest, 11, CONTEXT_SETTINGS);
                    args.importSuspendedCards = booleanArg(rest, 12, CONTEXT_SETTINGS);
                    args.importTaggedCards = booleanArg(rest, 13, CONTEXT_SETTINGS);
                    args.importTags = stringListArg(rest, 14, CONTEXT_SETTINGS);
                    args.importWeakCards = booleanArg(rest, 15, CONTEXT_SETTINGS);
                    args.importWeakFsrsDifficultyThreshold = doubleArg(rest, 16, CONTEXT_SETTINGS);
                    args.importWeakLapsesThreshold = intArg(rest, 17, CONTEXT_SETTINGS);
                    args.importMinMatchingCardsPerKanji = intArg(rest, 18, CONTEXT_SETTINGS);
                }
                if (rest.length >= 21) {
                    args.importBrowserQueryCards = booleanArg(rest, 19, CONTEXT_SETTINGS);
                    args.importBrowserQuery = stringArg(rest, 20, CONTEXT_SETTINGS);
                }
                if (rest.length >= 22) {
                    args.newCardSortMode = stringArg(rest, 21, CONTEXT_SETTINGS);
                }
                if (rest.length >= 24) {
                    args.ladderPromotionIntervalDays = intArg(rest, 22, CONTEXT_SETTINGS);
                    args.ladderDemotionFailStreak = intArg(rest, 23, CONTEXT_SETTINGS);
                } else {
                    args.ladderDemotionFailStreak = args.realDueReviewsToMove;
                }
                return args;
            }

            protected static double doubleArg(Object[] args, int index, String context) {
                Object value = arg(args, index, context);
                return ((Number) value).doubleValue();
            }

            protected static List<String> stringListArg(Object[] args, int index, String context) {
                Object value = arg(args, index, context);
                if (value == null) {
                    return Collections.emptyList();
                }
                if (value instanceof List<?> raw) {
                    List<String> out = new ArrayList<>();
                    for (Object item : raw) {
                        if (item != null) {
                            out.add(item.toString());
                        }
                    }
                    return out;
                }
                return parseImportTags(value.toString());
            }
        }

        public static Settings kikuDefaults() {
            return new Settings(
                    "Kiku",
                    "Mining",
                    "Expression",
                    "ExpressionReading",
                    "MainDefinition",
                    "Sentence",
                    "Frequency",
                    "FreqSort",
                    21,
                    2,
                    DEFAULT_SUSPENDED_RANK_MIN,
                    DEFAULT_SUSPENDED_RANK_MAX,
                    24,
                    3,
                    DEFAULT_WRITING_TRIGGER_MISS_DAYS,
                    DEFAULT_RECOGNITION_PROMOTION_PASSES,
                    DEFAULT_REAL_DUE_REVIEWS_TO_MOVE,
                    DEFAULT_IMPORT_ACTIVE_CARDS,
                    DEFAULT_IMPORT_SUSPENDED_CARDS,
                    DEFAULT_IMPORT_TAGGED_CARDS,
                    Collections.emptyList(),
                    DEFAULT_IMPORT_WEAK_CARDS,
                    DEFAULT_IMPORT_WEAK_FSRS_DIFFICULTY,
                    DEFAULT_IMPORT_WEAK_LAPSES,
                    DEFAULT_IMPORT_MIN_MATCHING_CARDS_PER_KANJI,
                    DEFAULT_IMPORT_BROWSER_QUERY_CARDS,
                    DEFAULT_IMPORT_BROWSER_QUERY,
                    DEFAULT_NEW_CARD_SORT_MODE,
                    DEFAULT_LADDER_PROMOTION_INTERVAL_DAYS,
                    DEFAULT_LADDER_DEMOTION_FAIL_STREAK
            );
        }

        public List<String> requiredFields() {
            List<String> fields = new ArrayList<>();
            addRequiredField(fields, expressionField);
            addRequiredField(fields, readingField);
            addRequiredField(fields, meaningField);
            addRequiredField(fields, sentenceField);
            addRequiredField(fields, frequencyField);
            addRequiredField(fields, frequencySortField);
            return fields;
        }

        protected static void addRequiredField(List<String> fields, String value) {
            if (value != null && !value.trim().isEmpty() && !fields.contains(value.trim())) {
                fields.add(value.trim());
            }
        }
    }

    public static final class Note {
        public final long noteId;
        public final long modelId;
        public final String modelName;
        public final Map<String, String> fields;
        public final List<String> tags;

        public Note(long noteId, String modelName, Map<String, String> fields, List<String> tags) {
            this(noteId, 0L, modelName, fields, tags);
        }

        public Note(long noteId, long modelId, String modelName, Map<String, String> fields, List<String> tags) {
            this.noteId = noteId;
            this.modelId = modelId;
            this.modelName = modelName;
            this.fields = Collections.unmodifiableMap(new LinkedHashMap<>(fields));
            this.tags = Collections.unmodifiableList(new ArrayList<>(tags));
        }

        public String field(String name) {
            String value = fields.get(name);
            return nullToEmpty(value);
        }

        public String expression(Settings settings) {
            return field(settings.expressionField);
        }

        public String reading(Settings settings) {
            return field(settings.readingField);
        }

        public String meaning(Settings settings) {
            return field(settings.meaningField);
        }

        public String sentence(Settings settings) {
            return field(settings.sentenceField);
        }
    }

    public static final class Card {
        public final long cardId;
        public final long noteId;
        public final int ord;
        public final String deckId;
        public final String deckName;
        public final int queue;
        public final int type;
        public final int due;
        public final int intervalDays;
        public final int reps;
        public final int lapses;
        public final boolean suspended;
        public final Double fsrsStability;
        public final Double fsrsDifficulty;
        public final Double fsrsRetrievability;
        public final boolean browserQueryMatched;

        public Card(long cardId, long noteId, int ord, String deckId, Object... rest) {
            this(cardId, noteId, ord, false, CardArgs.from(deckId, rest));
        }

        Card(long cardId, long noteId, int ord, boolean browserQueryMatched, CardArgs args) {
            this.cardId = cardId;
            this.noteId = noteId;
            this.ord = ord;
            this.deckId = nullToEmpty(args.deckId);
            this.deckName = args.deckName;
            this.queue = args.queue;
            this.type = args.type;
            this.due = args.due;
            this.intervalDays = args.intervalDays;
            this.reps = args.reps;
            this.lapses = args.lapses;
            this.suspended = args.suspended;
            this.fsrsStability = args.fsrsStability;
            this.fsrsDifficulty = args.fsrsDifficulty;
            this.fsrsRetrievability = args.fsrsRetrievability;
            this.browserQueryMatched = browserQueryMatched;
        }

        public Card withBrowserQueryMatched(boolean matched) {
            if (matched == this.browserQueryMatched) {
                return this;
            }
            CardArgs args = new CardArgs();
            args.deckId = this.deckId;
            args.deckName = this.deckName;
            args.queue = this.queue;
            args.type = this.type;
            args.due = this.due;
            args.intervalDays = this.intervalDays;
            args.reps = this.reps;
            args.lapses = this.lapses;
            args.suspended = this.suspended;
            args.fsrsStability = this.fsrsStability;
            args.fsrsDifficulty = this.fsrsDifficulty;
            args.fsrsRetrievability = this.fsrsRetrievability;
            return new Card(cardId, noteId, ord, matched, args);
        }

        protected static final class CardArgs {
            String deckId;
            String deckName;
            int queue;
            int type;
            int due;
            int intervalDays;
            int reps;
            int lapses;
            boolean suspended;
            Double fsrsStability;
            Double fsrsDifficulty;
            Double fsrsRetrievability;

            static CardArgs from(String firstDeckValue, Object[] rest) {
                requireArgCount(CONTEXT_CARD, rest, 7, 10, 11);
                CardArgs args = new CardArgs();
                int offset = 0;
                args.deckId = firstDeckValue;
                args.deckName = firstDeckValue;
                if (rest.length == 11) {
                    args.deckName = stringArg(rest, 0, CONTEXT_CARD);
                    offset = 1;
                }
                args.queue = intArg(rest, offset, CONTEXT_CARD);
                args.type = intArg(rest, offset + 1, CONTEXT_CARD);
                args.due = intArg(rest, offset + 2, CONTEXT_CARD);
                args.intervalDays = intArg(rest, offset + 3, CONTEXT_CARD);
                args.reps = intArg(rest, offset + 4, CONTEXT_CARD);
                args.lapses = intArg(rest, offset + 5, CONTEXT_CARD);
                args.suspended = booleanArg(rest, offset + 6, CONTEXT_CARD);
                if (rest.length - offset == 10) {
                    args.fsrsStability = nullableDoubleArg(rest, offset + 7, CONTEXT_CARD);
                    args.fsrsDifficulty = nullableDoubleArg(rest, offset + 8, CONTEXT_CARD);
                    args.fsrsRetrievability = nullableDoubleArg(rest, offset + 9, CONTEXT_CARD);
                }
                return args;
            }
        }

        public boolean mature(int matureDays) {
            return !suspended && intervalDays >= matureDays;
        }

        public boolean active() {
            return !suspended;
        }
    }

    public static final class CollectionSnapshot {
        public final List<Note> notes;
        public final List<Card> cards;

        public CollectionSnapshot(List<Note> notes, List<Card> cards) {
            this.notes = Collections.unmodifiableList(new ArrayList<>(notes));
            this.cards = Collections.unmodifiableList(new ArrayList<>(cards));
        }

        public Map<Long, Note> notesById() {
            Map<Long, Note> map = new LinkedHashMap<>();
            for (Note note : notes) {
                map.put(note.noteId, note);
            }
            return map;
        }
    }
}
