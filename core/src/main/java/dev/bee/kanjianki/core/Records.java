package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Records {
    public static final int DEFAULT_WRITING_TRIGGER_MISS_DAYS = 3;
    public static final int DEFAULT_RECOGNITION_PROMOTION_PASSES = 3;
    public static final int DEFAULT_REAL_DUE_REVIEWS_TO_MOVE = 3;
    public static final int DEFAULT_SUSPENDED_RANK_MIN = 100;
    public static final int DEFAULT_SUSPENDED_RANK_MAX = 3000;
    public static final String LEARNING_REPEAT_NEW = "new";
    public static final String LEARNING_REPEAT_REVIEW = "review";

    /**
     * Ladder rungs that a study item can be on, from lowest to highest.
     * New cards start on {@link #KANJI_MEANING}. The {@link #SIMILAR_KANJI}
     * rung is included in the ladder only when {@code hasSimilarKanji} is
     * true for the card; otherwise promotion and demotion skip over it.
     */
    public enum LadderRung {
        WRITE_KANJI("write_kanji"),
        TYPE_MEANING("type_meaning"),
        SIMILAR_KANJI("similar_kanji"),
        KANJI_MEANING("kanji_meaning"),
        FONT_MEANING("font_meaning"),
        WORD_READING("word_reading");

        private final String wireName;

        LadderRung(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static LadderRung startingRung() {
            return KANJI_MEANING;
        }

        public static LadderRung fromWireName(String name) {
            if (name == null) {
                return KANJI_MEANING;
            }
            for (LadderRung rung : values()) {
                if (rung.wireName.equals(name)) {
                    return rung;
                }
            }
            return KANJI_MEANING;
        }
    }

    /**
     * Phase of the card within Anki learning/relearning/review semantics.
     * Learning and relearning repeats are practice-only and must not count
     * toward ladder promotion or demotion; only {@link #REVIEW}-phase
     * answers on a due card advance the ladder streaks.
     */
    public enum SchedulerPhase {
        NEW_LEARNING("new_learning"),
        REVIEW("review"),
        RELEARNING("relearning");

        private final String wireName;

        SchedulerPhase(String wireName) {
            this.wireName = wireName;
        }

        public String wireName() {
            return wireName;
        }

        public static SchedulerPhase fromWireName(String name) {
            if (name == null) {
                return NEW_LEARNING;
            }
            for (SchedulerPhase phase : values()) {
                if (phase.wireName.equals(name)) {
                    return phase;
                }
            }
            return NEW_LEARNING;
        }
    }
    private static final String CONTEXT_SETTINGS = "Settings";
    private static final String CONTEXT_CARD = "Card";
    private static final String CONTEXT_EXAMPLE = "Example";
    private static final String CONTEXT_DASHBOARD_ROW = "DashboardRow";
    private static final String CONTEXT_KANJI_INVENTORY_ITEM = "KanjiInventoryItem";
    private static final String CONTEXT_SIMILAR_KANJI_CHOICE_CARD = "SimilarKanjiChoiceCard";
    private static final String CONTEXT_SIMILAR_KANJI_WRITING_REPAIR = "SimilarKanjiWritingRepair";
    private static final String CONTEXT_KANJI_TIMELINE_EVENT = "KanjiTimelineEvent";
    private static final String CONTEXT_TASK_MEMORY = "TaskMemory";
    private static final String CONTEXT_TASK_MEMORY_FROM_STUDY_FIELDS = "TaskMemory.fromStudyFields";
    private static final String CONTEXT_STUDY_ITEM = "StudyItem";
    private static final String CONTEXT_LEARNING_REPEAT = "LearningRepeat";
    private static final String CONTEXT_REVIEW_REQUEST = "ReviewRequest";
    private static final String CONTEXT_ADAPTIVE_LOAD_PLAN = "AdaptiveLoadPlan";

    private Records() {
    }

    private static Object arg(Object[] args, int index, String context) {
        if (index >= args.length) {
            throw new IllegalArgumentException(context + " expected more arguments");
        }
        return args[index];
    }

    private static void requireArgCount(String context, Object[] args, int... expectedCounts) {
        for (int expected : expectedCounts) {
            if (args.length == expected) {
                return;
            }
        }
        throw new IllegalArgumentException(context + " received " + args.length + " trailing arguments");
    }

    private static String stringArg(Object[] args, int index, String context) {
        return (String) arg(args, index, context);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static int intArg(Object[] args, int index, String context) {
        Object value = arg(args, index, context);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return (Integer) value;
    }

    private static long longArg(Object[] args, int index, String context) {
        Object value = arg(args, index, context);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return (Long) value;
    }

    private static boolean booleanArg(Object[] args, int index, String context) {
        return (Boolean) arg(args, index, context);
    }

    private static Double nullableDoubleArg(Object[] args, int index, String context) {
        Object value = arg(args, index, context);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return (Double) value;
    }

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
        }

        private static final class SettingsArgs {
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

            static SettingsArgs from(Object[] rest) {
                requireArgCount(CONTEXT_SETTINGS, rest, 7, 8, 9, 10, 11);
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
                    if (rest.length == 11) {
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
                return args;
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
                    DEFAULT_REAL_DUE_REVIEWS_TO_MOVE
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

        private static void addRequiredField(List<String> fields, String value) {
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
            return value == null ? "" : value;
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

        public Card(long cardId, long noteId, int ord, String deckId, Object... rest) {
            CardArgs args = CardArgs.from(deckId, rest);
            this.cardId = cardId;
            this.noteId = noteId;
            this.ord = ord;
            this.deckId = args.deckId == null ? "" : args.deckId;
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
        }

        private static final class CardArgs {
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

    public static final class SuspendedSource {
        public final String kanji;
        public final long cardId;
        public final long noteId;
        public final String expression;
        public final String reading;
        public final String meaning;
        public final String sentence;

        public SuspendedSource(String kanji, long cardId, long noteId, String expression, String reading, String meaning, String sentence) {
            this.kanji = kanji;
            this.cardId = cardId;
            this.noteId = noteId;
            this.expression = expression;
            this.reading = reading;
            this.meaning = meaning;
            this.sentence = sentence;
        }
    }

    public static final class SuspendedImport {
        public final String kanji;
        public final Integer jitenRank;
        public final boolean rankKnown;
        public final int cutoffUsed;
        public final List<SuspendedSource> sources;

        public SuspendedImport(String kanji, Integer jitenRank, boolean rankKnown, int cutoffUsed, List<SuspendedSource> sources) {
            this.kanji = kanji;
            this.jitenRank = jitenRank;
            this.rankKnown = rankKnown;
            this.cutoffUsed = cutoffUsed;
            this.sources = Collections.unmodifiableList(new ArrayList<>(sources));
        }
    }

    public static final class Example {
        public final String sourceType;
        public final long cardId;
        public final long noteId;
        public final String expression;
        public final String reading;
        public final String meaning;
        public final String sentence;
        public final boolean mature;
        public final int lapses;
        public final int intervalDays;
        public final int reps;
        public final Double fsrsStability;
        public final Double fsrsDifficulty;
        public final Double fsrsRetrievability;

        public Example(
                String sourceType,
                long cardId,
                long noteId,
                String expression,
                String reading,
                String meaning,
                Object... rest
        ) {
            ExampleArgs args = ExampleArgs.from(rest);
            this.sourceType = sourceType;
            this.cardId = cardId;
            this.noteId = noteId;
            this.expression = expression;
            this.reading = reading;
            this.meaning = meaning;
            this.sentence = args.sentence;
            this.mature = args.mature;
            this.lapses = args.lapses;
            this.intervalDays = args.intervalDays;
            this.reps = args.reps;
            this.fsrsStability = args.fsrsStability;
            this.fsrsDifficulty = args.fsrsDifficulty;
            this.fsrsRetrievability = args.fsrsRetrievability;
        }

        private static final class ExampleArgs {
            String sentence;
            boolean mature;
            int lapses;
            int intervalDays;
            int reps;
            Double fsrsStability;
            Double fsrsDifficulty;
            Double fsrsRetrievability;

            static ExampleArgs from(Object[] rest) {
                requireArgCount(CONTEXT_EXAMPLE, rest, 3, 8);
                ExampleArgs args = new ExampleArgs();
                args.sentence = stringArg(rest, 0, CONTEXT_EXAMPLE);
                args.mature = booleanArg(rest, 1, CONTEXT_EXAMPLE);
                args.lapses = intArg(rest, 2, CONTEXT_EXAMPLE);
                if (rest.length == 8) {
                    args.intervalDays = intArg(rest, 3, CONTEXT_EXAMPLE);
                    args.reps = intArg(rest, 4, CONTEXT_EXAMPLE);
                    args.fsrsStability = nullableDoubleArg(rest, 5, CONTEXT_EXAMPLE);
                    args.fsrsDifficulty = nullableDoubleArg(rest, 6, CONTEXT_EXAMPLE);
                    args.fsrsRetrievability = nullableDoubleArg(rest, 7, CONTEXT_EXAMPLE);
                }
                return args;
            }
        }
    }

    public static final class DashboardRow {
        public final String kanji;
        public final Integer jitenRank;
        public final String primaryMeaning;
        public final String reading;
        public final String browserSearch;
        public final int weaknessScore;
        public final String reasonCode;
        public final String reasonText;
        public final int activeExampleCount;
        public final int suspendedExampleCount;
        public final int matureSupportCount;
        public final List<Example> examples;

        public DashboardRow(String kanji, Integer jitenRank, String primaryMeaning, String reading, String browserSearch, Object... rest) {
            requireArgCount(CONTEXT_DASHBOARD_ROW, rest, 7);
            this.kanji = kanji;
            this.jitenRank = jitenRank;
            this.primaryMeaning = primaryMeaning;
            this.reading = reading;
            this.browserSearch = browserSearch;
            this.weaknessScore = intArg(rest, 0, CONTEXT_DASHBOARD_ROW);
            this.reasonCode = stringArg(rest, 1, CONTEXT_DASHBOARD_ROW);
            this.reasonText = stringArg(rest, 2, CONTEXT_DASHBOARD_ROW);
            this.activeExampleCount = intArg(rest, 3, CONTEXT_DASHBOARD_ROW);
            this.suspendedExampleCount = intArg(rest, 4, CONTEXT_DASHBOARD_ROW);
            this.matureSupportCount = intArg(rest, 5, CONTEXT_DASHBOARD_ROW);
            this.examples = Collections.unmodifiableList(examplesArg(rest, 6));
        }

        private static List<Example> examplesArg(Object[] args, int index) {
            Object value = arg(args, index, CONTEXT_DASHBOARD_ROW);
            List<?> rawExamples = (List<?>) value;
            List<Example> examples = new ArrayList<>();
            for (Object example : rawExamples) {
                examples.add((Example) example);
            }
            return examples;
        }
    }

    public static final class KanjiInventoryItem {
        public final String kanji;
        public final String primaryMeaning;
        public final String readings;
        public final String browserSearch;
        public final int sourceCount;
        public final int exampleCount;
        public final boolean suspended;
        public final long lastSeenAtMillis;

        public KanjiInventoryItem(String kanji, String primaryMeaning, String readings, String browserSearch, Object... rest) {
            requireArgCount(CONTEXT_KANJI_INVENTORY_ITEM, rest, 4);
            this.kanji = kanji == null ? "" : kanji;
            this.primaryMeaning = primaryMeaning == null ? "" : primaryMeaning;
            this.readings = readings == null ? "" : readings;
            this.browserSearch = browserSearch == null ? "" : browserSearch;
            this.sourceCount = Math.max(0, intArg(rest, 0, CONTEXT_KANJI_INVENTORY_ITEM));
            this.exampleCount = Math.max(0, intArg(rest, 1, CONTEXT_KANJI_INVENTORY_ITEM));
            this.suspended = booleanArg(rest, 2, CONTEXT_KANJI_INVENTORY_ITEM);
            this.lastSeenAtMillis = Math.max(0L, longArg(rest, 3, CONTEXT_KANJI_INVENTORY_ITEM));
        }
    }

    public static final class SimilarKanjiPair {
        public final String kanjiA;
        public final String kanjiB;
        public final String source;
        public final long firstSeenAtMillis;
        public final long lastSeenAtMillis;

        public SimilarKanjiPair(String kanjiA, String kanjiB, String source, long firstSeenAtMillis, long lastSeenAtMillis) {
            this.kanjiA = kanjiA == null ? "" : kanjiA;
            this.kanjiB = kanjiB == null ? "" : kanjiB;
            this.source = source == null ? "" : source;
            this.firstSeenAtMillis = Math.max(0L, firstSeenAtMillis);
            this.lastSeenAtMillis = Math.max(0L, lastSeenAtMillis);
        }
    }

    public static final class SimilarKanjiChoiceCard {
        public final String targetKanji;
        public final String primaryMeaning;
        public final List<String> choices;
        public final String choiceSignature;
        public final long dueAtMillis;
        public final long passedAtMillis;
        public final long lastReviewedAtMillis;
        public final int correctCount;
        public final int wrongCount;

        public SimilarKanjiChoiceCard(
                String targetKanji,
                String primaryMeaning,
                List<String> choices,
                String choiceSignature,
                Object... rest
        ) {
            requireArgCount(CONTEXT_SIMILAR_KANJI_CHOICE_CARD, rest, 0, 5);
            this.targetKanji = targetKanji == null ? "" : targetKanji;
            this.primaryMeaning = primaryMeaning == null ? "" : primaryMeaning;
            this.choices = Collections.unmodifiableList(new ArrayList<>(choices == null ? Collections.emptyList() : choices));
            this.choiceSignature = choiceSignature == null ? "" : choiceSignature;
            this.dueAtMillis = rest.length == 0 ? 0L : Math.max(0L, longArg(rest, 0, CONTEXT_SIMILAR_KANJI_CHOICE_CARD));
            this.passedAtMillis = rest.length == 0 ? 0L : Math.max(0L, longArg(rest, 1, CONTEXT_SIMILAR_KANJI_CHOICE_CARD));
            this.lastReviewedAtMillis = rest.length == 0 ? 0L : Math.max(0L, longArg(rest, 2, CONTEXT_SIMILAR_KANJI_CHOICE_CARD));
            this.correctCount = rest.length == 0 ? 0 : Math.max(0, intArg(rest, 3, CONTEXT_SIMILAR_KANJI_CHOICE_CARD));
            this.wrongCount = rest.length == 0 ? 0 : Math.max(0, intArg(rest, 4, CONTEXT_SIMILAR_KANJI_CHOICE_CARD));
        }

        public boolean passed() {
            return passedAtMillis > 0L;
        }
    }

    public static final class SimilarKanjiChoiceResult {
        public final SimilarKanjiChoiceCard card;
        public final String selectedKanji;
        public final boolean correct;
        public final List<String> repairKanji;

        public SimilarKanjiChoiceResult(
                SimilarKanjiChoiceCard card,
                String selectedKanji,
                boolean correct,
                List<String> repairKanji
        ) {
            this.card = card;
            this.selectedKanji = selectedKanji == null ? "" : selectedKanji;
            this.correct = correct;
            this.repairKanji = Collections.unmodifiableList(new ArrayList<>(repairKanji == null ? Collections.emptyList() : repairKanji));
        }
    }

    public static final class SimilarKanjiWritingRepair {
        public final long id;
        public final String targetKanji;
        public final String repairKanji;
        public final String choiceSignature;
        public final String wrongSelection;
        public final String promptMeaning;
        public final String status;
        public final long dueAtMillis;
        public final String activeToken;
        public final int attempts;
        public final long createdAtMillis;
        public final long updatedAtMillis;
        public final long completedAtMillis;

        public SimilarKanjiWritingRepair(
                long id,
                String targetKanji,
                String repairKanji,
                String choiceSignature,
                String wrongSelection,
                String promptMeaning,
                Object... rest
        ) {
            requireArgCount(CONTEXT_SIMILAR_KANJI_WRITING_REPAIR, rest, 7);
            this.id = Math.max(0L, id);
            this.targetKanji = targetKanji == null ? "" : targetKanji;
            this.repairKanji = repairKanji == null ? "" : repairKanji;
            this.choiceSignature = choiceSignature == null ? "" : choiceSignature;
            this.wrongSelection = wrongSelection == null ? "" : wrongSelection;
            this.promptMeaning = promptMeaning == null ? "" : promptMeaning;
            String requestedStatus = stringArg(rest, 0, CONTEXT_SIMILAR_KANJI_WRITING_REPAIR);
            this.status = requestedStatus == null || requestedStatus.isEmpty() ? "pending" : requestedStatus;
            this.dueAtMillis = Math.max(0L, longArg(rest, 1, CONTEXT_SIMILAR_KANJI_WRITING_REPAIR));
            String requestedActiveToken = stringArg(rest, 2, CONTEXT_SIMILAR_KANJI_WRITING_REPAIR);
            this.activeToken = requestedActiveToken == null ? "" : requestedActiveToken;
            this.attempts = Math.max(0, intArg(rest, 3, CONTEXT_SIMILAR_KANJI_WRITING_REPAIR));
            this.createdAtMillis = Math.max(0L, longArg(rest, 4, CONTEXT_SIMILAR_KANJI_WRITING_REPAIR));
            this.updatedAtMillis = Math.max(0L, longArg(rest, 5, CONTEXT_SIMILAR_KANJI_WRITING_REPAIR));
            this.completedAtMillis = Math.max(0L, longArg(rest, 6, CONTEXT_SIMILAR_KANJI_WRITING_REPAIR));
        }

        public SimilarKanjiWritingRepair withToken(String token, long updatedAtMillis) {
            return new SimilarKanjiWritingRepair(
                    id,
                    targetKanji,
                    repairKanji,
                    choiceSignature,
                    wrongSelection,
                    promptMeaning,
                    status,
                    dueAtMillis,
                    token,
                    attempts,
                    createdAtMillis,
                    updatedAtMillis,
                    completedAtMillis
            );
        }
    }

    public static final class KanjiTimelineEvent {
        public final long id;
        public final String kanji;
        public final long occurredAtMillis;
        public final String eventType;
        public final String title;
        public final String detail;
        public final String sourceExpression;
        public final String sourceReading;
        public final String rating;
        public final boolean writingRequired;
        public final boolean writingPassed;
        public final boolean manualOverride;
        public final Integer weaknessScore;
        public final Integer matureSupportCount;
        public final Long syncId;
        public final String dedupeKey;

        public KanjiTimelineEvent(
                long id,
                String kanji,
                long occurredAtMillis,
                String eventType,
                String title,
                String detail,
                Object... rest
        ) {
            requireArgCount(CONTEXT_KANJI_TIMELINE_EVENT, rest, 10);
            this.id = id;
            this.kanji = kanji;
            this.occurredAtMillis = occurredAtMillis;
            this.eventType = eventType;
            this.title = title;
            this.detail = detail;
            String requestedSourceExpression = stringArg(rest, 0, CONTEXT_KANJI_TIMELINE_EVENT);
            String requestedSourceReading = stringArg(rest, 1, CONTEXT_KANJI_TIMELINE_EVENT);
            String requestedRating = stringArg(rest, 2, CONTEXT_KANJI_TIMELINE_EVENT);
            this.sourceExpression = requestedSourceExpression == null ? "" : requestedSourceExpression;
            this.sourceReading = requestedSourceReading == null ? "" : requestedSourceReading;
            this.rating = requestedRating == null ? "" : requestedRating;
            this.writingRequired = booleanArg(rest, 3, CONTEXT_KANJI_TIMELINE_EVENT);
            this.writingPassed = booleanArg(rest, 4, CONTEXT_KANJI_TIMELINE_EVENT);
            this.manualOverride = booleanArg(rest, 5, CONTEXT_KANJI_TIMELINE_EVENT);
            this.weaknessScore = (Integer) arg(rest, 6, CONTEXT_KANJI_TIMELINE_EVENT);
            this.matureSupportCount = (Integer) arg(rest, 7, CONTEXT_KANJI_TIMELINE_EVENT);
            this.syncId = (Long) arg(rest, 8, CONTEXT_KANJI_TIMELINE_EVENT);
            this.dedupeKey = stringArg(rest, 9, CONTEXT_KANJI_TIMELINE_EVENT);
        }
    }

    public static final class KanjiRecoveryTimeline {
        public final KanjiInventoryItem inventoryItem;
        public final DashboardRow currentRow;
        public final StudyItem currentStudyItem;
        public final List<KanjiTimelineEvent> events;

        public KanjiRecoveryTimeline(DashboardRow currentRow, StudyItem currentStudyItem, List<KanjiTimelineEvent> events) {
            this(null, currentRow, currentStudyItem, events);
        }

        public KanjiRecoveryTimeline(KanjiInventoryItem inventoryItem, DashboardRow currentRow, StudyItem currentStudyItem, List<KanjiTimelineEvent> events) {
            this.inventoryItem = inventoryItem;
            this.currentRow = currentRow;
            this.currentStudyItem = currentStudyItem;
            this.events = Collections.unmodifiableList(new ArrayList<>(events));
        }
    }

    public static final class TaskMemory {
        public final String state;
        public final long dueAtMillis;
        public final double stability;
        public final double difficulty;
        public final int totalReviews;
        public final int lapses;
        public final int learningStep;
        public final String lastRating;
        public final int matureIntervalDays;
        public final int consecutivePasses;
        public final long lastPassedDueAtMillis;

        public TaskMemory(
                String state,
                long dueAtMillis,
                double stability,
                double difficulty,
                int totalReviews,
                int lapses,
                Object... rest
        ) {
            requireArgCount(CONTEXT_TASK_MEMORY, rest, 3, 5);
            this.state = state == null || state.isEmpty() ? "new" : state;
            this.dueAtMillis = Math.max(0L, dueAtMillis);
            this.stability = stability;
            this.difficulty = difficulty;
            this.totalReviews = Math.max(0, totalReviews);
            this.lapses = Math.max(0, lapses);
            this.learningStep = Math.max(0, intArg(rest, 0, CONTEXT_TASK_MEMORY));
            String requestedLastRating = stringArg(rest, 1, CONTEXT_TASK_MEMORY);
            this.lastRating = requestedLastRating == null ? "" : requestedLastRating;
            this.matureIntervalDays = Math.max(0, intArg(rest, 2, CONTEXT_TASK_MEMORY));
            this.consecutivePasses = rest.length == 3 ? 0 : Math.max(0, intArg(rest, 3, CONTEXT_TASK_MEMORY));
            this.lastPassedDueAtMillis = rest.length == 3 ? 0L : Math.max(0L, longArg(rest, 4, CONTEXT_TASK_MEMORY));
        }

        public static TaskMemory initial() {
            return new TaskMemory("new", 0L, 0.4, 5.0, 0, 0, 0, "", 0);
        }

        public static TaskMemory fromStudyFields(
                String state,
                long dueAtMillis,
                double stability,
                double difficulty,
                int totalReviews,
                int lapses,
                Object... rest
        ) {
            requireArgCount(CONTEXT_TASK_MEMORY_FROM_STUDY_FIELDS, rest, 2);
            return new TaskMemory(
                    state,
                    dueAtMillis,
                    stability,
                    difficulty,
                    totalReviews,
                    lapses,
                    intArg(rest, 0, CONTEXT_TASK_MEMORY_FROM_STUDY_FIELDS),
                    "",
                    intArg(rest, 1, CONTEXT_TASK_MEMORY_FROM_STUDY_FIELDS)
            );
        }

        public TaskMemory withDueAtMillis(long dueAtMillis) {
            return new TaskMemory(
                    state,
                    dueAtMillis,
                    stability,
                    difficulty,
                    totalReviews,
                    lapses,
                    learningStep,
                    lastRating,
                    matureIntervalDays,
                    consecutivePasses,
                    lastPassedDueAtMillis
            );
        }

        public String encode() {
            return state + "\t"
                    + dueAtMillis + "\t"
                    + stability + "\t"
                    + difficulty + "\t"
                    + totalReviews + "\t"
                    + lapses + "\t"
                    + learningStep + "\t"
                    + lastRating + "\t"
                    + matureIntervalDays + "\t"
                    + consecutivePasses + "\t"
                    + lastPassedDueAtMillis;
        }

        public static TaskMemory decode(String encoded, TaskMemory fallback) {
            TaskMemory safeFallback = fallback == null ? initial() : fallback;
            if (encoded == null || encoded.isEmpty()) {
                return safeFallback;
            }
            String[] parts = encoded.split("\t", -1);
            if (parts.length < 9) {
                return safeFallback;
            }
            try {
                return new TaskMemory(
                        parts[0],
                        Long.parseLong(parts[1]),
                        Double.parseDouble(parts[2]),
                        Double.parseDouble(parts[3]),
                        Integer.parseInt(parts[4]),
                        Integer.parseInt(parts[5]),
                        Integer.parseInt(parts[6]),
                        parts[7],
                        Integer.parseInt(parts[8]),
                        parts.length > 9 ? Integer.parseInt(parts[9]) : 0,
                        parts.length > 10 ? Long.parseLong(parts[10]) : 0L
                );
            } catch (RuntimeException ignored) {
                return safeFallback;
            }
        }
    }

    public static final class StudyItem {
        public final String kanji;
        public final String state;
        public final long dueAtMillis;
        public final double stability;
        public final double difficulty;
        public final int totalReviews;
        public final int lapses;
        public final int learningStep;
        public final int writingLevel;
        public final int recognitionStage;
        public final int consecutiveFailedRecognitionDays;
        public final long lastFailedRecognitionDayMillis;
        public final boolean writingRemediationPending;
        public final String suppressedByTaskType;
        public final long suppressedAtMillis;
        public final int matureIntervalDays;
        public final String answerSignature;
        public final String activeToken;
        public final long createdAtMillis;
        public final TaskMemory typingMeaningMemory;
        public final TaskMemory kanjiMeaningMemory;
        public final TaskMemory fontMeaningMemory;
        public final TaskMemory wordReadingMemory;
        public final TaskMemory writingRemediationMemory;
        public final LadderRung rung;
        public final SchedulerPhase phase;
        public final int realPassStreak;
        public final int realAgainStreak;
        public final long lastRealReviewDueAtMillis;
        public final boolean hasSimilarKanji;
        public final TaskMemory similarKanjiMemory;

        public StudyItem(
                String kanji,
                String state,
                long dueAtMillis,
                double stability,
                double difficulty,
                int totalReviews,
                Object... rest
        ) {
            StudyItemArgs args = StudyItemArgs.from(state, dueAtMillis, stability, difficulty, totalReviews, rest);
            this.kanji = kanji;
            this.state = state;
            this.dueAtMillis = dueAtMillis;
            this.stability = stability;
            this.difficulty = difficulty;
            this.totalReviews = totalReviews;
            this.lapses = args.lapses;
            this.learningStep = args.learningStep;
            this.writingLevel = args.writingLevel;
            this.recognitionStage = Math.max(-1, Math.min(2, args.recognitionStage));
            this.consecutiveFailedRecognitionDays = Math.max(0, args.consecutiveFailedRecognitionDays);
            this.lastFailedRecognitionDayMillis = Math.max(0L, args.lastFailedRecognitionDayMillis);
            this.writingRemediationPending = args.writingRemediationPending;
            this.suppressedByTaskType = args.suppressedByTaskType == null ? "" : args.suppressedByTaskType;
            this.suppressedAtMillis = Math.max(0L, args.suppressedAtMillis);
            this.matureIntervalDays = Math.max(0, args.matureIntervalDays);
            this.answerSignature = args.answerSignature == null ? "" : args.answerSignature;
            this.activeToken = args.activeToken;
            this.createdAtMillis = args.createdAtMillis;
            this.typingMeaningMemory = args.typingMeaningMemory == null ? TaskMemory.initial() : args.typingMeaningMemory;
            this.kanjiMeaningMemory = args.kanjiMeaningMemory == null ? TaskMemory.initial() : args.kanjiMeaningMemory;
            this.fontMeaningMemory = args.fontMeaningMemory == null ? TaskMemory.initial() : args.fontMeaningMemory;
            this.wordReadingMemory = args.wordReadingMemory == null ? TaskMemory.initial() : args.wordReadingMemory;
            this.writingRemediationMemory = args.writingRemediationMemory == null ? TaskMemory.initial() : args.writingRemediationMemory;
            this.rung = args.rung == null
                    ? derivedRung(this.writingRemediationPending, this.recognitionStage)
                    : args.rung;
            this.phase = args.phase == null
                    ? derivedPhase(this.state, this.totalReviews, this.writingRemediationPending)
                    : args.phase;
            this.realPassStreak = Math.max(0, args.realPassStreak);
            this.realAgainStreak = Math.max(0, args.realAgainStreak < 0
                    ? this.consecutiveFailedRecognitionDays
                    : args.realAgainStreak);
            this.lastRealReviewDueAtMillis = Math.max(0L, args.lastRealReviewDueAtMillis);
            this.hasSimilarKanji = args.hasSimilarKanji;
            this.similarKanjiMemory = args.similarKanjiMemory == null ? TaskMemory.initial() : args.similarKanjiMemory;
        }

        private static LadderRung derivedRung(boolean writingRemediationPending, int recognitionStage) {
            if (writingRemediationPending) {
                return LadderRung.WRITE_KANJI;
            }
            switch (Math.max(-1, Math.min(2, recognitionStage))) {
                case -1:
                    return LadderRung.TYPE_MEANING;
                case 1:
                    return LadderRung.FONT_MEANING;
                case 2:
                    return LadderRung.WORD_READING;
                default:
                    return LadderRung.KANJI_MEANING;
            }
        }

        private static SchedulerPhase derivedPhase(String state, int totalReviews, boolean writingRemediationPending) {
            if (LEARNING_REPEAT_REVIEW.equals(state) || "retired".equals(state)) {
                return SchedulerPhase.REVIEW;
            }
            if (writingRemediationPending || totalReviews > 0) {
                return SchedulerPhase.RELEARNING;
            }
            return SchedulerPhase.NEW_LEARNING;
        }

        private static final class StudyItemArgs {
            int lapses;
            int learningStep;
            int writingLevel;
            int recognitionStage;
            int consecutiveFailedRecognitionDays;
            long lastFailedRecognitionDayMillis;
            boolean writingRemediationPending;
            String suppressedByTaskType;
            long suppressedAtMillis;
            int matureIntervalDays;
            String answerSignature = "";
            String activeToken;
            long createdAtMillis;
            TaskMemory typingMeaningMemory;
            TaskMemory kanjiMeaningMemory;
            TaskMemory fontMeaningMemory;
            TaskMemory wordReadingMemory;
            TaskMemory writingRemediationMemory;
            LadderRung rung;
            SchedulerPhase phase;
            int realPassStreak;
            int realAgainStreak = -1;
            long lastRealReviewDueAtMillis;
            boolean hasSimilarKanji;
            TaskMemory similarKanjiMemory;

            static StudyItemArgs from(String state, long dueAtMillis, double stability, double difficulty, int totalReviews, Object[] rest) {
                requireArgCount(CONTEXT_STUDY_ITEM, rest, 5, 9, 13, 17, 18, 25);
                StudyItemArgs args = new StudyItemArgs();
                args.lapses = intArg(rest, 0, CONTEXT_STUDY_ITEM);
                args.learningStep = intArg(rest, 1, CONTEXT_STUDY_ITEM);
                args.writingLevel = intArg(rest, 2, CONTEXT_STUDY_ITEM);
                int memoryStart = -1;
                if (rest.length == 5) {
                    args.activeToken = stringArg(rest, 3, CONTEXT_STUDY_ITEM);
                    args.createdAtMillis = longArg(rest, 4, CONTEXT_STUDY_ITEM);
                } else {
                    args.recognitionStage = intArg(rest, 3, CONTEXT_STUDY_ITEM);
                    args.consecutiveFailedRecognitionDays = intArg(rest, 4, CONTEXT_STUDY_ITEM);
                    args.lastFailedRecognitionDayMillis = longArg(rest, 5, CONTEXT_STUDY_ITEM);
                    args.writingRemediationPending = booleanArg(rest, 6, CONTEXT_STUDY_ITEM);
                    if (rest.length == 9) {
                        args.activeToken = stringArg(rest, 7, CONTEXT_STUDY_ITEM);
                        args.createdAtMillis = longArg(rest, 8, CONTEXT_STUDY_ITEM);
                    } else {
                        args.suppressedByTaskType = stringArg(rest, 7, CONTEXT_STUDY_ITEM);
                        args.suppressedAtMillis = longArg(rest, 8, CONTEXT_STUDY_ITEM);
                        args.matureIntervalDays = intArg(rest, 9, CONTEXT_STUDY_ITEM);
                        args.answerSignature = stringArg(rest, 10, CONTEXT_STUDY_ITEM);
                        args.activeToken = stringArg(rest, 11, CONTEXT_STUDY_ITEM);
                        args.createdAtMillis = longArg(rest, 12, CONTEXT_STUDY_ITEM);
                        if (rest.length > 13) {
                            memoryStart = 13;
                        }
                    }
                }
                args.seedMemories(state, dueAtMillis, stability, difficulty, totalReviews);
                if (memoryStart >= 0) {
                    args.applyMemories(rest, memoryStart);
                }
                if (rest.length == 25) {
                    args.rung = (LadderRung) arg(rest, 18, CONTEXT_STUDY_ITEM);
                    args.phase = (SchedulerPhase) arg(rest, 19, CONTEXT_STUDY_ITEM);
                    args.realPassStreak = intArg(rest, 20, CONTEXT_STUDY_ITEM);
                    args.realAgainStreak = intArg(rest, 21, CONTEXT_STUDY_ITEM);
                    args.lastRealReviewDueAtMillis = longArg(rest, 22, CONTEXT_STUDY_ITEM);
                    args.hasSimilarKanji = booleanArg(rest, 23, CONTEXT_STUDY_ITEM);
                    args.similarKanjiMemory = (TaskMemory) arg(rest, 24, CONTEXT_STUDY_ITEM);
                }
                return args;
            }

            private void seedMemories(String state, long dueAtMillis, double stability, double difficulty, int totalReviews) {
                typingMeaningMemory = seedMemoryForStage(-1, this, state, dueAtMillis, stability, difficulty, totalReviews);
                kanjiMeaningMemory = seedMemoryForStage(0, this, state, dueAtMillis, stability, difficulty, totalReviews);
                fontMeaningMemory = seedMemoryForStage(1, this, state, dueAtMillis, stability, difficulty, totalReviews);
                wordReadingMemory = seedMemoryForStage(2, this, state, dueAtMillis, stability, difficulty, totalReviews);
                writingRemediationMemory = seedMemoryForWriting(this, state, dueAtMillis, stability, difficulty, totalReviews);
                similarKanjiMemory = TaskMemory.initial();
            }

            private void applyMemories(Object[] rest, int start) {
                if (rest.length == 17) {
                    kanjiMeaningMemory = (TaskMemory) arg(rest, start, CONTEXT_STUDY_ITEM);
                    fontMeaningMemory = (TaskMemory) arg(rest, start + 1, CONTEXT_STUDY_ITEM);
                    wordReadingMemory = (TaskMemory) arg(rest, start + 2, CONTEXT_STUDY_ITEM);
                    writingRemediationMemory = (TaskMemory) arg(rest, start + 3, CONTEXT_STUDY_ITEM);
                    return;
                }
                typingMeaningMemory = (TaskMemory) arg(rest, start, CONTEXT_STUDY_ITEM);
                kanjiMeaningMemory = (TaskMemory) arg(rest, start + 1, CONTEXT_STUDY_ITEM);
                fontMeaningMemory = (TaskMemory) arg(rest, start + 2, CONTEXT_STUDY_ITEM);
                wordReadingMemory = (TaskMemory) arg(rest, start + 3, CONTEXT_STUDY_ITEM);
                writingRemediationMemory = (TaskMemory) arg(rest, start + 4, CONTEXT_STUDY_ITEM);
            }

            private static TaskMemory seedMemoryForStage(
                    int memoryStage,
                    StudyItemArgs args,
                    String state,
                    long dueAtMillis,
                    double stability,
                    double difficulty,
                    int totalReviews
            ) {
                int safeStage = Math.max(-1, Math.min(2, args.recognitionStage));
                if (safeStage != memoryStage) {
                    return TaskMemory.initial();
                }
                return TaskMemory.fromStudyFields(
                        state,
                        dueAtMillis,
                        stability,
                        difficulty,
                        totalReviews,
                        args.lapses,
                        args.learningStep,
                        args.matureIntervalDays
                );
            }

            private static TaskMemory seedMemoryForWriting(
                    StudyItemArgs args,
                    String state,
                    long dueAtMillis,
                    double stability,
                    double difficulty,
                    int totalReviews
            ) {
                if (!args.writingRemediationPending) {
                    return TaskMemory.initial();
                }
                return TaskMemory.fromStudyFields(
                        state,
                        dueAtMillis,
                        stability,
                        difficulty,
                        totalReviews,
                        args.lapses,
                        args.learningStep,
                        args.matureIntervalDays
                );
            }
        }

        public StudyItem withToken(String token) {
            return copyBuilder().activeToken(token).build();
        }

        public StudyItem withSuppression(String suppressedByTaskType, long suppressedAtMillis, int matureIntervalDays) {
            return copyBuilder()
                    .suppressedByTaskType(suppressedByTaskType)
                    .suppressedAtMillis(suppressedAtMillis)
                    .matureIntervalDays(matureIntervalDays)
                    .build();
        }

        public StudyItem withAnswerSignature(String answerSignature) {
            return copyBuilder().answerSignature(answerSignature).build();
        }

        public StudyItem withRung(LadderRung rung) {
            return copyBuilder().rung(rung).build();
        }

        public StudyItem withPhase(SchedulerPhase phase) {
            return copyBuilder().phase(phase).build();
        }

        public StudyItem withRungAndPhase(LadderRung rung, SchedulerPhase phase) {
            return copyBuilder().rung(rung).phase(phase).build();
        }

        public StudyItem withLadderProgress(
                LadderRung rung,
                SchedulerPhase phase,
                int stepIndex,
                int realPassStreak,
                int realAgainStreak,
                long lastRealReviewDueAtMillis
        ) {
            return copyBuilder()
                    .rung(rung)
                    .phase(phase)
                    .learningStep(stepIndex)
                    .realPassStreak(realPassStreak)
                    .realAgainStreak(realAgainStreak)
                    .lastRealReviewDueAtMillis(lastRealReviewDueAtMillis)
                    .build();
        }

        public StudyItem withHasSimilarKanji(boolean hasSimilarKanji) {
            return copyBuilder().hasSimilarKanji(hasSimilarKanji).build();
        }

        public StudyItem withSimilarKanjiMemory(TaskMemory memory) {
            return copyBuilder().similarKanjiMemory(memory).build();
        }

        public TaskMemory memoryForTaskType(String taskType) {
            if (taskType == null) {
                return kanjiMeaningMemory;
            }
            switch (taskType) {
                case BridgeScheduler.TASK_WRITING_REMEDIATION:
                case BridgeScheduler.TASK_WRITE_KANJI:
                    return writingRemediationMemory;
                case BridgeScheduler.TASK_TYPING_MEANING:
                case BridgeScheduler.TASK_TYPE_MEANING:
                    return typingMeaningMemory;
                case BridgeScheduler.TASK_SIMILAR_KANJI:
                    return similarKanjiMemory;
                case BridgeScheduler.TASK_WORD_READING:
                    return wordReadingMemory;
                case BridgeScheduler.TASK_FONT_MEANING:
                    return fontMeaningMemory;
                default:
                    return kanjiMeaningMemory;
            }
        }

        public TaskMemory memoryForRung(LadderRung rung) {
            if (rung == null) {
                return kanjiMeaningMemory;
            }
            switch (rung) {
                case WRITE_KANJI:
                    return writingRemediationMemory;
                case TYPE_MEANING:
                    return typingMeaningMemory;
                case SIMILAR_KANJI:
                    return similarKanjiMemory;
                case FONT_MEANING:
                    return fontMeaningMemory;
                case WORD_READING:
                    return wordReadingMemory;
                case KANJI_MEANING:
                default:
                    return kanjiMeaningMemory;
            }
        }

        public StudyItem withTaskMemory(String taskType, TaskMemory memory) {
            if (taskType == null) {
                return copyBuilder().kanjiMeaningMemory(memory).build();
            }
            switch (taskType) {
                case BridgeScheduler.TASK_WRITING_REMEDIATION:
                case BridgeScheduler.TASK_WRITE_KANJI:
                    return copyBuilder().writingRemediationMemory(memory).build();
                case BridgeScheduler.TASK_TYPING_MEANING:
                case BridgeScheduler.TASK_TYPE_MEANING:
                    return copyBuilder().typingMeaningMemory(memory).build();
                case BridgeScheduler.TASK_SIMILAR_KANJI:
                    return copyBuilder().similarKanjiMemory(memory).build();
                case BridgeScheduler.TASK_WORD_READING:
                    return copyBuilder().wordReadingMemory(memory).build();
                case BridgeScheduler.TASK_FONT_MEANING:
                    return copyBuilder().fontMeaningMemory(memory).build();
                default:
                    return copyBuilder().kanjiMeaningMemory(memory).build();
            }
        }

        public StudyItem withTaskMemories(TaskMemory kanjiMemory, TaskMemory fontMemory, TaskMemory wordMemory, TaskMemory writingMemory) {
            return copyBuilder()
                    .kanjiMeaningMemory(kanjiMemory)
                    .fontMeaningMemory(fontMemory)
                    .wordReadingMemory(wordMemory)
                    .writingRemediationMemory(writingMemory)
                    .build();
        }

        public StudyItem withTaskMemories(TaskMemory typingMemory, TaskMemory kanjiMemory, TaskMemory fontMemory, TaskMemory wordMemory, TaskMemory writingMemory) {
            return copyBuilder()
                    .typingMeaningMemory(typingMemory)
                    .kanjiMeaningMemory(kanjiMemory)
                    .fontMeaningMemory(fontMemory)
                    .wordReadingMemory(wordMemory)
                    .writingRemediationMemory(writingMemory)
                    .build();
        }

        public StudyItemBuilder copyBuilder() {
            return new StudyItemBuilder(this);
        }

        public static final class StudyItemBuilder {
            private String kanji;
            private String state;
            private long dueAtMillis;
            private double stability;
            private double difficulty;
            private int totalReviews;
            private int lapses;
            private int learningStep;
            private int writingLevel;
            private int recognitionStage;
            private int consecutiveFailedRecognitionDays;
            private long lastFailedRecognitionDayMillis;
            private boolean writingRemediationPending;
            private String suppressedByTaskType;
            private long suppressedAtMillis;
            private int matureIntervalDays;
            private String answerSignature;
            private String activeToken;
            private long createdAtMillis;
            private TaskMemory typingMeaningMemory;
            private TaskMemory kanjiMeaningMemory;
            private TaskMemory fontMeaningMemory;
            private TaskMemory wordReadingMemory;
            private TaskMemory writingRemediationMemory;
            private LadderRung rung;
            private SchedulerPhase phase;
            private int realPassStreak;
            private int realAgainStreak;
            private long lastRealReviewDueAtMillis;
            private boolean hasSimilarKanji;
            private TaskMemory similarKanjiMemory;

            StudyItemBuilder(StudyItem src) {
                this.kanji = src.kanji;
                this.state = src.state;
                this.dueAtMillis = src.dueAtMillis;
                this.stability = src.stability;
                this.difficulty = src.difficulty;
                this.totalReviews = src.totalReviews;
                this.lapses = src.lapses;
                this.learningStep = src.learningStep;
                this.writingLevel = src.writingLevel;
                this.recognitionStage = src.recognitionStage;
                this.consecutiveFailedRecognitionDays = src.consecutiveFailedRecognitionDays;
                this.lastFailedRecognitionDayMillis = src.lastFailedRecognitionDayMillis;
                this.writingRemediationPending = src.writingRemediationPending;
                this.suppressedByTaskType = src.suppressedByTaskType;
                this.suppressedAtMillis = src.suppressedAtMillis;
                this.matureIntervalDays = src.matureIntervalDays;
                this.answerSignature = src.answerSignature;
                this.activeToken = src.activeToken;
                this.createdAtMillis = src.createdAtMillis;
                this.typingMeaningMemory = src.typingMeaningMemory;
                this.kanjiMeaningMemory = src.kanjiMeaningMemory;
                this.fontMeaningMemory = src.fontMeaningMemory;
                this.wordReadingMemory = src.wordReadingMemory;
                this.writingRemediationMemory = src.writingRemediationMemory;
                this.rung = src.rung;
                this.phase = src.phase;
                this.realPassStreak = src.realPassStreak;
                this.realAgainStreak = src.realAgainStreak;
                this.lastRealReviewDueAtMillis = src.lastRealReviewDueAtMillis;
                this.hasSimilarKanji = src.hasSimilarKanji;
                this.similarKanjiMemory = src.similarKanjiMemory;
            }

            public StudyItemBuilder state(String value) { this.state = value; return this; }
            public StudyItemBuilder dueAtMillis(long value) { this.dueAtMillis = value; return this; }
            public StudyItemBuilder stability(double value) { this.stability = value; return this; }
            public StudyItemBuilder difficulty(double value) { this.difficulty = value; return this; }
            public StudyItemBuilder totalReviews(int value) { this.totalReviews = value; return this; }
            public StudyItemBuilder lapses(int value) { this.lapses = value; return this; }
            public StudyItemBuilder learningStep(int value) { this.learningStep = value; return this; }
            public StudyItemBuilder writingLevel(int value) { this.writingLevel = value; return this; }
            public StudyItemBuilder recognitionStage(int value) { this.recognitionStage = value; return this; }
            public StudyItemBuilder consecutiveFailedRecognitionDays(int value) { this.consecutiveFailedRecognitionDays = value; return this; }
            public StudyItemBuilder lastFailedRecognitionDayMillis(long value) { this.lastFailedRecognitionDayMillis = value; return this; }
            public StudyItemBuilder writingRemediationPending(boolean value) { this.writingRemediationPending = value; return this; }
            public StudyItemBuilder suppressedByTaskType(String value) { this.suppressedByTaskType = value; return this; }
            public StudyItemBuilder suppressedAtMillis(long value) { this.suppressedAtMillis = value; return this; }
            public StudyItemBuilder matureIntervalDays(int value) { this.matureIntervalDays = value; return this; }
            public StudyItemBuilder answerSignature(String value) { this.answerSignature = value; return this; }
            public StudyItemBuilder activeToken(String value) { this.activeToken = value; return this; }
            public StudyItemBuilder createdAtMillis(long value) { this.createdAtMillis = value; return this; }
            public StudyItemBuilder typingMeaningMemory(TaskMemory value) { this.typingMeaningMemory = value; return this; }
            public StudyItemBuilder kanjiMeaningMemory(TaskMemory value) { this.kanjiMeaningMemory = value; return this; }
            public StudyItemBuilder fontMeaningMemory(TaskMemory value) { this.fontMeaningMemory = value; return this; }
            public StudyItemBuilder wordReadingMemory(TaskMemory value) { this.wordReadingMemory = value; return this; }
            public StudyItemBuilder writingRemediationMemory(TaskMemory value) { this.writingRemediationMemory = value; return this; }
            public StudyItemBuilder rung(LadderRung value) { this.rung = value; return this; }
            public StudyItemBuilder phase(SchedulerPhase value) { this.phase = value; return this; }
            public StudyItemBuilder realPassStreak(int value) { this.realPassStreak = value; return this; }
            public StudyItemBuilder realAgainStreak(int value) { this.realAgainStreak = value; return this; }
            public StudyItemBuilder lastRealReviewDueAtMillis(long value) { this.lastRealReviewDueAtMillis = value; return this; }
            public StudyItemBuilder hasSimilarKanji(boolean value) { this.hasSimilarKanji = value; return this; }
            public StudyItemBuilder similarKanjiMemory(TaskMemory value) { this.similarKanjiMemory = value; return this; }

            public StudyItem build() {
                return new StudyItem(
                        kanji,
                        state,
                        dueAtMillis,
                        stability,
                        difficulty,
                        totalReviews,
                        lapses,
                        learningStep,
                        writingLevel,
                        recognitionStage,
                        consecutiveFailedRecognitionDays,
                        lastFailedRecognitionDayMillis,
                        writingRemediationPending,
                        suppressedByTaskType,
                        suppressedAtMillis,
                        matureIntervalDays,
                        answerSignature,
                        activeToken,
                        createdAtMillis,
                        typingMeaningMemory,
                        kanjiMeaningMemory,
                        fontMeaningMemory,
                        wordReadingMemory,
                        writingRemediationMemory,
                        rung,
                        phase,
                        realPassStreak,
                        realAgainStreak,
                        lastRealReviewDueAtMillis,
                        hasSimilarKanji,
                        similarKanjiMemory
                );
            }
        }
    }

    public static final class StudySession {
        public final StudyItem item;
        public final DashboardRow row;
        public final String token;
        public final String taskType;
        public final boolean writingRequired;
        public final String prompt;

        public StudySession(StudyItem item, DashboardRow row, String token, String taskType, boolean writingRequired, String prompt) {
            this.item = item;
            this.row = row;
            this.token = token;
            this.taskType = taskType;
            this.writingRequired = writingRequired;
            this.prompt = prompt;
        }
    }

    public static final class LearningStepSettings {
        public final List<Integer> newStepsMinutes;
        public final List<Integer> reviewStepsMinutes;

        public LearningStepSettings(List<Integer> newStepsMinutes, List<Integer> reviewStepsMinutes) {
            this.newStepsMinutes = Collections.unmodifiableList(normalizeSteps(newStepsMinutes, defaultNewSteps()));
            this.reviewStepsMinutes = Collections.unmodifiableList(normalizeSteps(reviewStepsMinutes, defaultReviewSteps()));
        }

        public static LearningStepSettings defaults() {
            return new LearningStepSettings(defaultNewSteps(), defaultReviewSteps());
        }

        public static List<Integer> parseSteps(String value, List<Integer> fallback) {
            List<Integer> parsed = tryParseSteps(value);
            return parsed.isEmpty() ? normalizeSteps(fallback, defaultNewSteps()) : parsed;
        }

        public static List<Integer> tryParseSteps(String value) {
            if (value == null || value.trim().isEmpty()) {
                return Collections.emptyList();
            }
            String[] parts = value.trim().split("[,\\s]+");
            List<Integer> parsed = new ArrayList<>();
            for (String part : parts) {
                if (part.isEmpty()) {
                    continue;
                }
                Integer minutes = parseStepMinutes(part);
                if (minutes == null || minutes <= 0) {
                    return Collections.emptyList();
                }
                parsed.add(minutes);
            }
            return parsed.isEmpty() ? Collections.emptyList() : normalizeSteps(parsed, defaultNewSteps());
        }

        public static String formatSteps(List<Integer> steps) {
            List<Integer> normalized = normalizeSteps(steps, defaultNewSteps());
            List<String> parts = new ArrayList<>();
            for (int minutes : normalized) {
                if (minutes >= 60 && minutes % 60 == 0) {
                    parts.add((minutes / 60) + "h");
                } else {
                    parts.add(minutes + "m");
                }
            }
            return String.join(", ", parts);
        }

        public String newStepsText() {
            return formatSteps(newStepsMinutes);
        }

        public String reviewStepsText() {
            return formatSteps(reviewStepsMinutes);
        }

        public static List<Integer> defaultNewSteps() {
            List<Integer> out = new ArrayList<>();
            out.add(1);
            out.add(10);
            return out;
        }

        public static List<Integer> defaultReviewSteps() {
            List<Integer> out = new ArrayList<>();
            out.add(10);
            return out;
        }

        private static Integer parseStepMinutes(String raw) {
            String value = raw.trim().toLowerCase();
            int multiplier = 1;
            if (value.endsWith("m")) {
                value = value.substring(0, value.length() - 1);
            } else if (value.endsWith("h")) {
                value = value.substring(0, value.length() - 1);
                multiplier = 60;
            }
            if (value.isEmpty()) {
                return null;
            }
            try {
                return Math.multiplyExact(Integer.parseInt(value), multiplier);
            } catch (ArithmeticException | NumberFormatException ignored) {
                return null;
            }
        }

        private static List<Integer> normalizeSteps(List<Integer> steps, List<Integer> fallback) {
            List<Integer> out = new ArrayList<>();
            if (steps != null) {
                for (Integer step : steps) {
                    if (step == null || step <= 0) {
                        out.clear();
                        break;
                    }
                    out.add(step);
                }
            }
            if (!out.isEmpty()) {
                return out;
            }
            return fallback == null || fallback.isEmpty() ? defaultNewSteps() : new ArrayList<>(fallback);
        }
    }

    public static final class LearningRepeat {
        public final String kanji;
        public final String answerSignature;
        public final String taskType;
        public final String repeatType;
        public final int stepIndex;
        public final long dueAtMillis;
        public final String activeToken;
        public final long createdAtMillis;
        public final long updatedAtMillis;

        public LearningRepeat(String kanji, String answerSignature, String taskType, String repeatType, Object... rest) {
            requireArgCount(CONTEXT_LEARNING_REPEAT, rest, 5);
            this.kanji = kanji == null ? "" : kanji;
            this.answerSignature = answerSignature == null ? "" : answerSignature;
            this.taskType = taskType == null ? "" : taskType;
            this.repeatType = LEARNING_REPEAT_REVIEW.equals(repeatType) ? LEARNING_REPEAT_REVIEW : LEARNING_REPEAT_NEW;
            this.stepIndex = Math.max(0, intArg(rest, 0, CONTEXT_LEARNING_REPEAT));
            this.dueAtMillis = Math.max(0L, longArg(rest, 1, CONTEXT_LEARNING_REPEAT));
            String requestedActiveToken = stringArg(rest, 2, CONTEXT_LEARNING_REPEAT);
            this.activeToken = requestedActiveToken == null ? "" : requestedActiveToken;
            this.createdAtMillis = Math.max(0L, longArg(rest, 3, CONTEXT_LEARNING_REPEAT));
            this.updatedAtMillis = Math.max(0L, longArg(rest, 4, CONTEXT_LEARNING_REPEAT));
        }

        public LearningRepeat withToken(String token, long updatedAtMillis) {
            return new LearningRepeat(kanji, answerSignature, taskType, repeatType, stepIndex, dueAtMillis, token, createdAtMillis, updatedAtMillis);
        }

        public LearningRepeat withStep(int stepIndex, long dueAtMillis, long updatedAtMillis) {
            return new LearningRepeat(kanji, answerSignature, taskType, repeatType, stepIndex, dueAtMillis, "", createdAtMillis, updatedAtMillis);
        }
    }

    public static final class ReviewRequest {
        public final String kanji;
        public final String token;
        public final String rating;
        public final String taskType;
        public final String answerSignature;
        public final String prompt;
        public final boolean writingRequired;
        public final boolean writingPassed;
        public final boolean writingClean;
        public final boolean manualOverride;
        public final int hintsUsed;

        public ReviewRequest(
                String kanji,
                String token,
                String rating,
                boolean writingRequired,
                boolean writingPassed,
                Object... rest
        ) {
            requireArgCount(CONTEXT_REVIEW_REQUEST, rest, 2, 3, 6);
            this.kanji = kanji;
            this.token = token;
            this.rating = rating;
            this.writingRequired = writingRequired;
            this.writingPassed = writingPassed;
            if (rest.length == 2) {
                this.writingClean = writingPassed && ("good".equals(rating) || "easy".equals(rating));
                this.manualOverride = booleanArg(rest, 0, CONTEXT_REVIEW_REQUEST);
                this.hintsUsed = intArg(rest, 1, CONTEXT_REVIEW_REQUEST);
                this.taskType = "";
                this.answerSignature = "";
                this.prompt = "";
            } else {
                this.writingClean = booleanArg(rest, 0, CONTEXT_REVIEW_REQUEST);
                this.manualOverride = booleanArg(rest, 1, CONTEXT_REVIEW_REQUEST);
                this.hintsUsed = intArg(rest, 2, CONTEXT_REVIEW_REQUEST);
                this.taskType = rest.length == 3 ? "" : nullToEmpty(stringArg(rest, 3, CONTEXT_REVIEW_REQUEST));
                this.answerSignature = rest.length == 3 ? "" : nullToEmpty(stringArg(rest, 4, CONTEXT_REVIEW_REQUEST));
                this.prompt = rest.length == 3 ? "" : nullToEmpty(stringArg(rest, 5, CONTEXT_REVIEW_REQUEST));
            }
        }
    }

    public static final class SchedulerParameters {
        public final double targetRetention;
        public final double againMultiplier;
        public final double hardMultiplier;
        public final double goodMultiplier;
        public final double easyMultiplier;
        public final long lastAdjustedAtMillis;
        public final int lastAdjustmentReviewCount;

        public SchedulerParameters(
                double targetRetention,
                double againMultiplier,
                double hardMultiplier,
                double goodMultiplier,
                double easyMultiplier,
                long lastAdjustedAtMillis,
                int lastAdjustmentReviewCount
        ) {
            this.targetRetention = targetRetention;
            this.againMultiplier = againMultiplier;
            this.hardMultiplier = hardMultiplier;
            this.goodMultiplier = goodMultiplier;
            this.easyMultiplier = easyMultiplier;
            this.lastAdjustedAtMillis = lastAdjustedAtMillis;
            this.lastAdjustmentReviewCount = lastAdjustmentReviewCount;
        }

        public static SchedulerParameters defaults() {
            return new SchedulerParameters(0.90, 0.45, 1.20, 2.00, 3.10, 0L, 0);
        }

        public SchedulerParameters withAdjustment(double again, double hard, double good, double easy, long adjustedAtMillis, int reviewCount) {
            return new SchedulerParameters(
                    targetRetention,
                    clamp(again, 0.25, 0.75),
                    clamp(hard, 1.05, 1.80),
                    clamp(good, 1.35, 3.20),
                    clamp(easy, 2.00, 4.80),
                    adjustedAtMillis,
                    reviewCount
            );
        }

        private static double clamp(double value, double min, double max) {
            return Math.max(min, Math.min(max, value));
        }
    }

    public static final class ReviewStats {
        public final int total;
        public final int again;
        public final int hard;
        public final int good;
        public final int easy;
        public final int writingRequired;
        public final int writingFailed;

        public ReviewStats(int total, int again, int hard, int good, int easy, int writingRequired, int writingFailed) {
            this.total = total;
            this.again = again;
            this.hard = hard;
            this.good = good;
            this.easy = easy;
            this.writingRequired = writingRequired;
            this.writingFailed = writingFailed;
        }

        public double retentionProxy() {
            if (total == 0) {
                return 1.0;
            }
            return (hard + good + easy) / (double) total;
        }

        public double writingFailureRate() {
            if (writingRequired == 0) {
                return 0.0;
            }
            return writingFailed / (double) writingRequired;
        }
    }

    public static final class ReviewResult {
        public final StudyItem item;
        public final String appliedRating;
        public final boolean duplicate;
        public final String message;

        public ReviewResult(StudyItem item, String appliedRating, boolean duplicate, String message) {
            this.item = item;
            this.appliedRating = appliedRating;
            this.duplicate = duplicate;
            this.message = message;
        }
    }

    public static final class AdaptiveLoadPlan {
        public final boolean autoMode;
        public final int workloadPercent;
        public final int target;
        public final int remaining;
        public final List<String> focusKanji;
        public final int newAdmissionLimit;
        public final boolean allKanjiMode;
        public final String status;

        public AdaptiveLoadPlan(
                int workloadPercent,
                int target,
                int remaining,
                List<String> focusKanji,
                int newAdmissionLimit,
                boolean allKanjiMode,
                String status
        ) {
            this(false, workloadPercent, target, remaining, focusKanji, newAdmissionLimit, allKanjiMode, status);
        }

        public AdaptiveLoadPlan(boolean autoMode, int workloadPercent, int target, int remaining, List<String> focusKanji, Object... rest) {
            requireArgCount(CONTEXT_ADAPTIVE_LOAD_PLAN, rest, 3);
            this.autoMode = autoMode;
            this.workloadPercent = workloadPercent;
            this.target = target;
            this.remaining = remaining;
            this.focusKanji = Collections.unmodifiableList(new ArrayList<>(focusKanji));
            this.newAdmissionLimit = intArg(rest, 0, CONTEXT_ADAPTIVE_LOAD_PLAN);
            this.allKanjiMode = booleanArg(rest, 1, CONTEXT_ADAPTIVE_LOAD_PLAN);
            this.status = nullToEmpty(stringArg(rest, 2, CONTEXT_ADAPTIVE_LOAD_PLAN));
        }

        public boolean focusComplete() {
            return !allKanjiMode && target > 0 && remaining <= 0;
        }
    }

    public static final class ReleaseAsset {
        public final String name;
        public final String downloadUrl;

        public ReleaseAsset(String name, String downloadUrl) {
            this.name = name;
            this.downloadUrl = downloadUrl;
        }
    }

    public static final class ReleaseInfo {
        public final String tagName;
        public final String htmlUrl;
        public final List<ReleaseAsset> assets;

        public ReleaseInfo(String tagName, String htmlUrl, List<ReleaseAsset> assets) {
            this.tagName = tagName;
            this.htmlUrl = htmlUrl;
            this.assets = Collections.unmodifiableList(new ArrayList<>(assets));
        }

        public ReleaseAsset apkAsset() {
            for (ReleaseAsset asset : assets) {
                if (asset.name.endsWith(".apk")) {
                    return asset;
                }
            }
            return null;
        }

        public ReleaseAsset checksumAssetFor(String apkName) {
            for (ReleaseAsset asset : assets) {
                if (Objects.equals(asset.name, apkName + ".sha256")) {
                    return asset;
                }
            }
            return null;
        }
    }
}
