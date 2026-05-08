package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Records {
    public static final int DEFAULT_WRITING_TRIGGER_MISS_DAYS = 3;
    public static final int DEFAULT_SUSPENDED_RANK_MIN = 100;
    public static final int DEFAULT_SUSPENDED_RANK_MAX = 3000;
    public static final String LEARNING_REPEAT_NEW = "new";
    public static final String LEARNING_REPEAT_REVIEW = "review";

    private Records() {
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

        public Settings(
                String modelName,
                String templateName,
                String expressionField,
                String readingField,
                String meaningField,
                String sentenceField,
                String frequencyField,
                String frequencySortField,
                int matureDays,
                int matureSupportThreshold,
                int suspendedRankCutoff,
                int activeQueueCap,
                int newPerDay
        ) {
            this(
                    modelName,
                    templateName,
                    expressionField,
                    readingField,
                    meaningField,
                    sentenceField,
                    frequencyField,
                    frequencySortField,
                    matureDays,
                    matureSupportThreshold,
                    DEFAULT_SUSPENDED_RANK_MIN,
                    suspendedRankCutoff,
                    activeQueueCap,
                    newPerDay,
                    DEFAULT_WRITING_TRIGGER_MISS_DAYS
            );
        }

        public Settings(
                String modelName,
                String templateName,
                String expressionField,
                String readingField,
                String meaningField,
                String sentenceField,
                String frequencyField,
                String frequencySortField,
                int matureDays,
                int matureSupportThreshold,
                int suspendedRankCutoff,
                int activeQueueCap,
                int newPerDay,
                int writingTriggerMissDays
        ) {
            this(
                    modelName,
                    templateName,
                    expressionField,
                    readingField,
                    meaningField,
                    sentenceField,
                    frequencyField,
                    frequencySortField,
                    matureDays,
                    matureSupportThreshold,
                    DEFAULT_SUSPENDED_RANK_MIN,
                    suspendedRankCutoff,
                    activeQueueCap,
                    newPerDay,
                    writingTriggerMissDays
            );
        }

        public Settings(
                String modelName,
                String templateName,
                String expressionField,
                String readingField,
                String meaningField,
                String sentenceField,
                String frequencyField,
                String frequencySortField,
                int matureDays,
                int matureSupportThreshold,
                int suspendedRankMin,
                int suspendedRankMax,
                int activeQueueCap,
                int newPerDay,
                int writingTriggerMissDays
        ) {
            this.modelName = modelName;
            this.templateName = templateName;
            this.expressionField = expressionField;
            this.readingField = readingField;
            this.meaningField = meaningField;
            this.sentenceField = sentenceField;
            this.frequencyField = frequencyField;
            this.frequencySortField = frequencySortField;
            this.matureDays = matureDays;
            this.matureSupportThreshold = matureSupportThreshold;
            int normalizedMin = Math.max(1, Math.min(20000, suspendedRankMin));
            int normalizedMax = Math.max(1, Math.min(20000, suspendedRankMax));
            if (normalizedMin > normalizedMax) {
                int swap = normalizedMin;
                normalizedMin = normalizedMax;
                normalizedMax = swap;
            }
            this.suspendedRankMin = normalizedMin;
            this.suspendedRankMax = normalizedMax;
            this.suspendedRankCutoff = normalizedMax;
            this.activeQueueCap = activeQueueCap;
            this.newPerDay = newPerDay;
            this.writingTriggerMissDays = Math.max(1, writingTriggerMissDays);
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
                    DEFAULT_WRITING_TRIGGER_MISS_DAYS
            );
        }

        public List<String> requiredFields() {
            List<String> fields = new ArrayList<>();
            fields.add(expressionField);
            fields.add(readingField);
            fields.add(meaningField);
            fields.add(sentenceField);
            fields.add(frequencyField);
            fields.add(frequencySortField);
            return fields;
        }
    }

    public static final class Note {
        public final long noteId;
        public final String modelName;
        public final Map<String, String> fields;
        public final List<String> tags;

        public Note(long noteId, String modelName, Map<String, String> fields, List<String> tags) {
            this.noteId = noteId;
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

        public Card(
                long cardId,
                long noteId,
                int ord,
                String deckName,
                int queue,
                int type,
                int due,
                int intervalDays,
                int reps,
                int lapses,
                boolean suspended
        ) {
            this(cardId, noteId, ord, deckName, queue, type, due, intervalDays, reps, lapses, suspended, null, null, null);
        }

        public Card(
                long cardId,
                long noteId,
                int ord,
                String deckName,
                int queue,
                int type,
                int due,
                int intervalDays,
                int reps,
                int lapses,
                boolean suspended,
                Double fsrsStability,
                Double fsrsDifficulty,
                Double fsrsRetrievability
        ) {
            this.cardId = cardId;
            this.noteId = noteId;
            this.ord = ord;
            this.deckName = deckName;
            this.queue = queue;
            this.type = type;
            this.due = due;
            this.intervalDays = intervalDays;
            this.reps = reps;
            this.lapses = lapses;
            this.suspended = suspended;
            this.fsrsStability = fsrsStability;
            this.fsrsDifficulty = fsrsDifficulty;
            this.fsrsRetrievability = fsrsRetrievability;
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

        public Example(String sourceType, long cardId, long noteId, String expression, String reading, String meaning, String sentence, boolean mature, int lapses) {
            this(sourceType, cardId, noteId, expression, reading, meaning, sentence, mature, lapses, 0, 0, null, null, null);
        }

        public Example(
                String sourceType,
                long cardId,
                long noteId,
                String expression,
                String reading,
                String meaning,
                String sentence,
                boolean mature,
                int lapses,
                int intervalDays,
                int reps,
                Double fsrsStability,
                Double fsrsDifficulty,
                Double fsrsRetrievability
        ) {
            this.sourceType = sourceType;
            this.cardId = cardId;
            this.noteId = noteId;
            this.expression = expression;
            this.reading = reading;
            this.meaning = meaning;
            this.sentence = sentence;
            this.mature = mature;
            this.lapses = lapses;
            this.intervalDays = intervalDays;
            this.reps = reps;
            this.fsrsStability = fsrsStability;
            this.fsrsDifficulty = fsrsDifficulty;
            this.fsrsRetrievability = fsrsRetrievability;
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

        public DashboardRow(
                String kanji,
                Integer jitenRank,
                String primaryMeaning,
                String reading,
                String browserSearch,
                int weaknessScore,
                String reasonCode,
                String reasonText,
                int activeExampleCount,
                int suspendedExampleCount,
                int matureSupportCount,
                List<Example> examples
        ) {
            this.kanji = kanji;
            this.jitenRank = jitenRank;
            this.primaryMeaning = primaryMeaning;
            this.reading = reading;
            this.browserSearch = browserSearch;
            this.weaknessScore = weaknessScore;
            this.reasonCode = reasonCode;
            this.reasonText = reasonText;
            this.activeExampleCount = activeExampleCount;
            this.suspendedExampleCount = suspendedExampleCount;
            this.matureSupportCount = matureSupportCount;
            this.examples = Collections.unmodifiableList(new ArrayList<>(examples));
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

        public KanjiInventoryItem(
                String kanji,
                String primaryMeaning,
                String readings,
                String browserSearch,
                int sourceCount,
                int exampleCount,
                boolean suspended,
                long lastSeenAtMillis
        ) {
            this.kanji = kanji == null ? "" : kanji;
            this.primaryMeaning = primaryMeaning == null ? "" : primaryMeaning;
            this.readings = readings == null ? "" : readings;
            this.browserSearch = browserSearch == null ? "" : browserSearch;
            this.sourceCount = Math.max(0, sourceCount);
            this.exampleCount = Math.max(0, exampleCount);
            this.suspended = suspended;
            this.lastSeenAtMillis = Math.max(0L, lastSeenAtMillis);
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
                String choiceSignature
        ) {
            this(targetKanji, primaryMeaning, choices, choiceSignature, 0L, 0L, 0L, 0, 0);
        }

        public SimilarKanjiChoiceCard(
                String targetKanji,
                String primaryMeaning,
                List<String> choices,
                String choiceSignature,
                long dueAtMillis,
                long passedAtMillis,
                long lastReviewedAtMillis,
                int correctCount,
                int wrongCount
        ) {
            this.targetKanji = targetKanji == null ? "" : targetKanji;
            this.primaryMeaning = primaryMeaning == null ? "" : primaryMeaning;
            this.choices = Collections.unmodifiableList(new ArrayList<>(choices == null ? Collections.emptyList() : choices));
            this.choiceSignature = choiceSignature == null ? "" : choiceSignature;
            this.dueAtMillis = Math.max(0L, dueAtMillis);
            this.passedAtMillis = Math.max(0L, passedAtMillis);
            this.lastReviewedAtMillis = Math.max(0L, lastReviewedAtMillis);
            this.correctCount = Math.max(0, correctCount);
            this.wrongCount = Math.max(0, wrongCount);
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
                String status,
                long dueAtMillis,
                String activeToken,
                int attempts,
                long createdAtMillis,
                long updatedAtMillis,
                long completedAtMillis
        ) {
            this.id = Math.max(0L, id);
            this.targetKanji = targetKanji == null ? "" : targetKanji;
            this.repairKanji = repairKanji == null ? "" : repairKanji;
            this.choiceSignature = choiceSignature == null ? "" : choiceSignature;
            this.wrongSelection = wrongSelection == null ? "" : wrongSelection;
            this.promptMeaning = promptMeaning == null ? "" : promptMeaning;
            this.status = status == null || status.isEmpty() ? "pending" : status;
            this.dueAtMillis = Math.max(0L, dueAtMillis);
            this.activeToken = activeToken == null ? "" : activeToken;
            this.attempts = Math.max(0, attempts);
            this.createdAtMillis = Math.max(0L, createdAtMillis);
            this.updatedAtMillis = Math.max(0L, updatedAtMillis);
            this.completedAtMillis = Math.max(0L, completedAtMillis);
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
                String sourceExpression,
                String sourceReading,
                String rating,
                boolean writingRequired,
                boolean writingPassed,
                boolean manualOverride,
                Integer weaknessScore,
                Integer matureSupportCount,
                Long syncId,
                String dedupeKey
        ) {
            this.id = id;
            this.kanji = kanji;
            this.occurredAtMillis = occurredAtMillis;
            this.eventType = eventType;
            this.title = title;
            this.detail = detail;
            this.sourceExpression = sourceExpression == null ? "" : sourceExpression;
            this.sourceReading = sourceReading == null ? "" : sourceReading;
            this.rating = rating == null ? "" : rating;
            this.writingRequired = writingRequired;
            this.writingPassed = writingPassed;
            this.manualOverride = manualOverride;
            this.weaknessScore = weaknessScore;
            this.matureSupportCount = matureSupportCount;
            this.syncId = syncId;
            this.dedupeKey = dedupeKey;
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

        public TaskMemory(
                String state,
                long dueAtMillis,
                double stability,
                double difficulty,
                int totalReviews,
                int lapses,
                int learningStep,
                String lastRating,
                int matureIntervalDays
        ) {
            this.state = state == null || state.isEmpty() ? "new" : state;
            this.dueAtMillis = Math.max(0L, dueAtMillis);
            this.stability = stability;
            this.difficulty = difficulty;
            this.totalReviews = Math.max(0, totalReviews);
            this.lapses = Math.max(0, lapses);
            this.learningStep = Math.max(0, learningStep);
            this.lastRating = lastRating == null ? "" : lastRating;
            this.matureIntervalDays = Math.max(0, matureIntervalDays);
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
                int learningStep,
                int matureIntervalDays
        ) {
            return new TaskMemory(state, dueAtMillis, stability, difficulty, totalReviews, lapses, learningStep, "", matureIntervalDays);
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
                    + matureIntervalDays;
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
                        Integer.parseInt(parts[8])
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
        public final TaskMemory kanjiMeaningMemory;
        public final TaskMemory fontMeaningMemory;
        public final TaskMemory wordReadingMemory;
        public final TaskMemory writingRemediationMemory;

        public StudyItem(
                String kanji,
                String state,
                long dueAtMillis,
                double stability,
                double difficulty,
                int totalReviews,
                int lapses,
                int learningStep,
                int writingLevel,
                String activeToken,
                long createdAtMillis
        ) {
            this(
                    kanji,
                    state,
                    dueAtMillis,
                    stability,
                    difficulty,
                    totalReviews,
                    lapses,
                    learningStep,
                    writingLevel,
                    0,
                    0,
                    0L,
                    false,
                    null,
                    0L,
                    0,
                    "",
                    activeToken,
                    createdAtMillis
            );
        }

        public StudyItem(
                String kanji,
                String state,
                long dueAtMillis,
                double stability,
                double difficulty,
                int totalReviews,
                int lapses,
                int learningStep,
                int writingLevel,
                int recognitionStage,
                int consecutiveFailedRecognitionDays,
                long lastFailedRecognitionDayMillis,
                boolean writingRemediationPending,
                String activeToken,
                long createdAtMillis
        ) {
            this(
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
                    null,
                    0L,
                    0,
                    "",
                    activeToken,
                    createdAtMillis
            );
        }

        public StudyItem(
                String kanji,
                String state,
                long dueAtMillis,
                double stability,
                double difficulty,
                int totalReviews,
                int lapses,
                int learningStep,
                int writingLevel,
                int recognitionStage,
                int consecutiveFailedRecognitionDays,
                long lastFailedRecognitionDayMillis,
                boolean writingRemediationPending,
                String suppressedByTaskType,
                long suppressedAtMillis,
                int matureIntervalDays,
                String answerSignature,
                String activeToken,
                long createdAtMillis
        ) {
            this(
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
                    seedMemoryForStage(0, recognitionStage, state, dueAtMillis, stability, difficulty, totalReviews, lapses, learningStep, matureIntervalDays),
                    seedMemoryForStage(1, recognitionStage, state, dueAtMillis, stability, difficulty, totalReviews, lapses, learningStep, matureIntervalDays),
                    seedMemoryForStage(2, recognitionStage, state, dueAtMillis, stability, difficulty, totalReviews, lapses, learningStep, matureIntervalDays),
                    seedMemoryForWriting(writingRemediationPending, state, dueAtMillis, stability, difficulty, totalReviews, lapses, learningStep, matureIntervalDays)
            );
        }

        public StudyItem(
                String kanji,
                String state,
                long dueAtMillis,
                double stability,
                double difficulty,
                int totalReviews,
                int lapses,
                int learningStep,
                int writingLevel,
                int recognitionStage,
                int consecutiveFailedRecognitionDays,
                long lastFailedRecognitionDayMillis,
                boolean writingRemediationPending,
                String suppressedByTaskType,
                long suppressedAtMillis,
                int matureIntervalDays,
                String answerSignature,
                String activeToken,
                long createdAtMillis,
                TaskMemory kanjiMeaningMemory,
                TaskMemory fontMeaningMemory,
                TaskMemory wordReadingMemory,
                TaskMemory writingRemediationMemory
        ) {
            this.kanji = kanji;
            this.state = state;
            this.dueAtMillis = dueAtMillis;
            this.stability = stability;
            this.difficulty = difficulty;
            this.totalReviews = totalReviews;
            this.lapses = lapses;
            this.learningStep = learningStep;
            this.writingLevel = writingLevel;
            this.recognitionStage = Math.max(0, Math.min(2, recognitionStage));
            this.consecutiveFailedRecognitionDays = Math.max(0, consecutiveFailedRecognitionDays);
            this.lastFailedRecognitionDayMillis = Math.max(0L, lastFailedRecognitionDayMillis);
            this.writingRemediationPending = writingRemediationPending;
            this.suppressedByTaskType = suppressedByTaskType == null ? "" : suppressedByTaskType;
            this.suppressedAtMillis = Math.max(0L, suppressedAtMillis);
            this.matureIntervalDays = Math.max(0, matureIntervalDays);
            this.answerSignature = answerSignature == null ? "" : answerSignature;
            this.activeToken = activeToken;
            this.createdAtMillis = createdAtMillis;
            this.kanjiMeaningMemory = kanjiMeaningMemory == null ? TaskMemory.initial() : kanjiMeaningMemory;
            this.fontMeaningMemory = fontMeaningMemory == null ? TaskMemory.initial() : fontMeaningMemory;
            this.wordReadingMemory = wordReadingMemory == null ? TaskMemory.initial() : wordReadingMemory;
            this.writingRemediationMemory = writingRemediationMemory == null ? TaskMemory.initial() : writingRemediationMemory;
        }

        public StudyItem withToken(String token) {
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
                    token,
                    createdAtMillis,
                    kanjiMeaningMemory,
                    fontMeaningMemory,
                    wordReadingMemory,
                    writingRemediationMemory
            );
        }

        public StudyItem withSuppression(String suppressedByTaskType, long suppressedAtMillis, int matureIntervalDays) {
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
                    kanjiMeaningMemory,
                    fontMeaningMemory,
                    wordReadingMemory,
                    writingRemediationMemory
            );
        }

        public StudyItem withAnswerSignature(String answerSignature) {
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
                    kanjiMeaningMemory,
                    fontMeaningMemory,
                    wordReadingMemory,
                    writingRemediationMemory
            );
        }

        public TaskMemory memoryForTaskType(String taskType) {
            if ("writing_remediation".equals(taskType)) {
                return writingRemediationMemory;
            }
            if ("word_reading".equals(taskType)) {
                return wordReadingMemory;
            }
            if ("font_meaning".equals(taskType)) {
                return fontMeaningMemory;
            }
            return kanjiMeaningMemory;
        }

        public StudyItem withTaskMemory(String taskType, TaskMemory memory) {
            TaskMemory kanjiMemory = kanjiMeaningMemory;
            TaskMemory fontMemory = fontMeaningMemory;
            TaskMemory wordMemory = wordReadingMemory;
            TaskMemory writingMemory = writingRemediationMemory;
            if ("writing_remediation".equals(taskType)) {
                writingMemory = memory;
            } else if ("word_reading".equals(taskType)) {
                wordMemory = memory;
            } else if ("font_meaning".equals(taskType)) {
                fontMemory = memory;
            } else {
                kanjiMemory = memory;
            }
            return withTaskMemories(kanjiMemory, fontMemory, wordMemory, writingMemory);
        }

        public StudyItem withTaskMemories(TaskMemory kanjiMemory, TaskMemory fontMemory, TaskMemory wordMemory, TaskMemory writingMemory) {
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
                    kanjiMemory,
                    fontMemory,
                    wordMemory,
                    writingMemory
            );
        }

        private static TaskMemory seedMemoryForStage(
                int memoryStage,
                int recognitionStage,
                String state,
                long dueAtMillis,
                double stability,
                double difficulty,
                int totalReviews,
                int lapses,
                int learningStep,
                int matureIntervalDays
        ) {
            int safeStage = Math.max(0, Math.min(2, recognitionStage));
            if (safeStage != memoryStage) {
                return TaskMemory.initial();
            }
            return TaskMemory.fromStudyFields(state, dueAtMillis, stability, difficulty, totalReviews, lapses, learningStep, matureIntervalDays);
        }

        private static TaskMemory seedMemoryForWriting(
                boolean writingRemediationPending,
                String state,
                long dueAtMillis,
                double stability,
                double difficulty,
                int totalReviews,
                int lapses,
                int learningStep,
                int matureIntervalDays
        ) {
            if (!writingRemediationPending) {
                return TaskMemory.initial();
            }
            return TaskMemory.fromStudyFields(state, dueAtMillis, stability, difficulty, totalReviews, lapses, learningStep, matureIntervalDays);
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
            return parsed == null ? normalizeSteps(fallback, defaultNewSteps()) : parsed;
        }

        public static List<Integer> tryParseSteps(String value) {
            if (value == null || value.trim().isEmpty()) {
                return null;
            }
            String[] parts = value.trim().split("[,\\s]+");
            List<Integer> parsed = new ArrayList<>();
            for (String part : parts) {
                if (part.isEmpty()) {
                    continue;
                }
                Integer minutes = parseStepMinutes(part);
                if (minutes == null || minutes <= 0) {
                    return null;
                }
                parsed.add(minutes);
            }
            return parsed.isEmpty() ? null : normalizeSteps(parsed, defaultNewSteps());
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

        public LearningRepeat(
                String kanji,
                String answerSignature,
                String taskType,
                String repeatType,
                int stepIndex,
                long dueAtMillis,
                String activeToken,
                long createdAtMillis,
                long updatedAtMillis
        ) {
            this.kanji = kanji == null ? "" : kanji;
            this.answerSignature = answerSignature == null ? "" : answerSignature;
            this.taskType = taskType == null ? "" : taskType;
            this.repeatType = LEARNING_REPEAT_REVIEW.equals(repeatType) ? LEARNING_REPEAT_REVIEW : LEARNING_REPEAT_NEW;
            this.stepIndex = Math.max(0, stepIndex);
            this.dueAtMillis = Math.max(0L, dueAtMillis);
            this.activeToken = activeToken == null ? "" : activeToken;
            this.createdAtMillis = Math.max(0L, createdAtMillis);
            this.updatedAtMillis = Math.max(0L, updatedAtMillis);
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
        public final boolean writingRequired;
        public final boolean writingPassed;
        public final boolean writingClean;
        public final boolean manualOverride;
        public final int hintsUsed;

        public ReviewRequest(String kanji, String token, String rating, boolean writingRequired, boolean writingPassed, boolean manualOverride, int hintsUsed) {
            this(
                    kanji,
                    token,
                    rating,
                    writingRequired,
                    writingPassed,
                    writingPassed && ("good".equals(rating) || "easy".equals(rating)),
                    manualOverride,
                    hintsUsed
            );
        }

        public ReviewRequest(
                String kanji,
                String token,
                String rating,
                boolean writingRequired,
                boolean writingPassed,
                boolean writingClean,
                boolean manualOverride,
                int hintsUsed
        ) {
            this.kanji = kanji;
            this.token = token;
            this.rating = rating;
            this.writingRequired = writingRequired;
            this.writingPassed = writingPassed;
            this.writingClean = writingClean;
            this.manualOverride = manualOverride;
            this.hintsUsed = hintsUsed;
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

        public AdaptiveLoadPlan(
                boolean autoMode,
                int workloadPercent,
                int target,
                int remaining,
                List<String> focusKanji,
                int newAdmissionLimit,
                boolean allKanjiMode,
                String status
        ) {
            this.autoMode = autoMode;
            this.workloadPercent = workloadPercent;
            this.target = target;
            this.remaining = remaining;
            this.focusKanji = Collections.unmodifiableList(new ArrayList<>(focusKanji));
            this.newAdmissionLimit = newAdmissionLimit;
            this.allKanjiMode = allKanjiMode;
            this.status = status == null ? "" : status;
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
