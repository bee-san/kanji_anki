package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class Records {
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
        public final int suspendedRankCutoff;
        public final int activeQueueCap;
        public final int newPerDay;

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
            this.suspendedRankCutoff = suspendedRankCutoff;
            this.activeQueueCap = activeQueueCap;
            this.newPerDay = newPerDay;
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
                    3000,
                    24,
                    3
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
        public final DashboardRow currentRow;
        public final StudyItem currentStudyItem;
        public final List<KanjiTimelineEvent> events;

        public KanjiRecoveryTimeline(DashboardRow currentRow, StudyItem currentStudyItem, List<KanjiTimelineEvent> events) {
            this.currentRow = currentRow;
            this.currentStudyItem = currentStudyItem;
            this.events = Collections.unmodifiableList(new ArrayList<>(events));
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
        public final String activeToken;
        public final long createdAtMillis;

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
            this.kanji = kanji;
            this.state = state;
            this.dueAtMillis = dueAtMillis;
            this.stability = stability;
            this.difficulty = difficulty;
            this.totalReviews = totalReviews;
            this.lapses = lapses;
            this.learningStep = learningStep;
            this.writingLevel = writingLevel;
            this.activeToken = activeToken;
            this.createdAtMillis = createdAtMillis;
        }

        public StudyItem withToken(String token) {
            return new StudyItem(kanji, state, dueAtMillis, stability, difficulty, totalReviews, lapses, learningStep, writingLevel, token, createdAtMillis);
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
