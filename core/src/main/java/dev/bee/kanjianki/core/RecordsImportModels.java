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

public abstract class RecordsImportModels extends RecordsSyncModels {
    public static final class SuspendedSource {
        public final String kanji;
        public final long cardId;
        public final long noteId;
        public final String expression;
        public final String reading;
        public final String meaning;
        public final String sentence;
        public final String sourceType;
        public final boolean suspended;
        public final boolean forcePractice;
        public final boolean mature;
        public final int lapses;
        public final int intervalDays;
        public final int reps;
        public final Double fsrsStability;
        public final Double fsrsDifficulty;
        public final Double fsrsRetrievability;

        public SuspendedSource(String kanji, long cardId, long noteId, String expression, String reading, String meaning, String sentence) {
            this(
                    kanji,
                    cardId,
                    noteId,
                    expression,
                    reading,
                    meaning,
                    SuspendedSourceDetails.builder(sentence).build()
            );
        }

        public SuspendedSource(
                String kanji,
                long cardId,
                long noteId,
                String expression,
                String reading,
                String meaning,
                SuspendedSourceDetails details
        ) {
            SuspendedSourceDetails sourceDetails = details == null
                    ? SuspendedSourceDetails.builder("").build()
                    : details;
            this.kanji = kanji;
            this.cardId = cardId;
            this.noteId = noteId;
            this.expression = expression;
            this.reading = reading;
            this.meaning = meaning;
            this.sentence = sourceDetails.sentence;
            this.sourceType = normalizeSourceType(sourceDetails.sourceType, sourceDetails.suspended);
            this.suspended = sourceDetails.suspended;
            this.forcePractice = sourceDetails.forcePractice;
            this.mature = sourceDetails.mature;
            this.lapses = Math.max(0, sourceDetails.lapses);
            this.intervalDays = Math.max(0, sourceDetails.intervalDays);
            this.reps = Math.max(0, sourceDetails.reps);
            this.fsrsStability = sourceDetails.fsrsStability;
            this.fsrsDifficulty = sourceDetails.fsrsDifficulty;
            this.fsrsRetrievability = sourceDetails.fsrsRetrievability;
        }

        protected static String normalizeSourceType(String sourceType, boolean suspended) {
            if (sourceType != null && !sourceType.trim().isEmpty()) {
                return sourceType.trim();
            }
            if (suspended) {
                return SOURCE_SUSPENDED;
            }
            return SOURCE_ACTIVE;
        }
    }

    public static final class SuspendedSourceDetails {
        final String sentence;
        final String sourceType;
        final boolean suspended;
        final boolean forcePractice;
        final boolean mature;
        final int lapses;
        final int intervalDays;
        final int reps;
        final Double fsrsStability;
        final Double fsrsDifficulty;
        final Double fsrsRetrievability;

        SuspendedSourceDetails(Builder builder) {
            this.sentence = builder.sentence;
            this.sourceType = builder.sourceType;
            this.suspended = builder.suspended;
            this.forcePractice = builder.forcePractice;
            this.mature = builder.mature;
            this.lapses = builder.lapses;
            this.intervalDays = builder.intervalDays;
            this.reps = builder.reps;
            this.fsrsStability = builder.fsrsStability;
            this.fsrsDifficulty = builder.fsrsDifficulty;
            this.fsrsRetrievability = builder.fsrsRetrievability;
        }

        public static Builder builder(String sentence) {
            return new Builder(sentence);
        }

        public static final class Builder {
            final String sentence;
            String sourceType = SOURCE_SUSPENDED;
            boolean suspended = true;
            boolean forcePractice = true;
            boolean mature;
            int lapses;
            int intervalDays;
            int reps;
            Double fsrsStability;
            Double fsrsDifficulty;
            Double fsrsRetrievability;

            Builder(String sentence) {
                this.sentence = sentence;
            }

            public Builder sourceType(String sourceType) {
                this.sourceType = sourceType;
                return this;
            }

            public Builder suspended(boolean suspended) {
                this.suspended = suspended;
                return this;
            }

            public Builder forcePractice(boolean forcePractice) {
                this.forcePractice = forcePractice;
                return this;
            }

            public Builder mature(boolean mature) {
                this.mature = mature;
                return this;
            }

            public Builder reviewStats(int lapses, int intervalDays, int reps) {
                this.lapses = lapses;
                this.intervalDays = intervalDays;
                this.reps = reps;
                return this;
            }

            public Builder fsrs(Double stability, Double difficulty, Double retrievability) {
                this.fsrsStability = stability;
                this.fsrsDifficulty = difficulty;
                this.fsrsRetrievability = retrievability;
                return this;
            }

            public SuspendedSourceDetails build() {
                return new SuspendedSourceDetails(this);
            }
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

        protected static final class ExampleArgs {
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

        protected static List<Example> examplesArg(Object[] args, int index) {
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
            this.kanji = nullToEmpty(kanji);
            this.primaryMeaning = nullToEmpty(primaryMeaning);
            this.readings = nullToEmpty(readings);
            this.browserSearch = nullToEmpty(browserSearch);
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
            this.kanjiA = nullToEmpty(kanjiA);
            this.kanjiB = nullToEmpty(kanjiB);
            this.source = nullToEmpty(source);
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
            this.targetKanji = nullToEmpty(targetKanji);
            this.primaryMeaning = nullToEmpty(primaryMeaning);
            this.choices = Collections.unmodifiableList(new ArrayList<>(nullToEmptyList(choices)));
            this.choiceSignature = nullToEmpty(choiceSignature);
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
            this.selectedKanji = nullToEmpty(selectedKanji);
            this.correct = correct;
            this.repairKanji = Collections.unmodifiableList(new ArrayList<>(nullToEmptyList(repairKanji)));
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
            this.targetKanji = nullToEmpty(targetKanji);
            this.repairKanji = nullToEmpty(repairKanji);
            this.choiceSignature = nullToEmpty(choiceSignature);
            this.wrongSelection = nullToEmpty(wrongSelection);
            this.promptMeaning = nullToEmpty(promptMeaning);
            String requestedStatus = stringArg(rest, 0, CONTEXT_SIMILAR_KANJI_WRITING_REPAIR);
            this.status = requestedStatus == null || requestedStatus.isEmpty() ? "pending" : requestedStatus;
            this.dueAtMillis = Math.max(0L, longArg(rest, 1, CONTEXT_SIMILAR_KANJI_WRITING_REPAIR));
            String requestedActiveToken = stringArg(rest, 2, CONTEXT_SIMILAR_KANJI_WRITING_REPAIR);
            this.activeToken = nullToEmpty(requestedActiveToken);
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
            this.sourceExpression = nullToEmpty(requestedSourceExpression);
            this.sourceReading = nullToEmpty(requestedSourceReading);
            this.rating = nullToEmpty(requestedRating);
            this.writingRequired = booleanArg(rest, 3, CONTEXT_KANJI_TIMELINE_EVENT);
            this.writingPassed = booleanArg(rest, 4, CONTEXT_KANJI_TIMELINE_EVENT);
            this.manualOverride = booleanArg(rest, 5, CONTEXT_KANJI_TIMELINE_EVENT);
            this.weaknessScore = (Integer) arg(rest, 6, CONTEXT_KANJI_TIMELINE_EVENT);
            this.matureSupportCount = (Integer) arg(rest, 7, CONTEXT_KANJI_TIMELINE_EVENT);
            this.syncId = (Long) arg(rest, 8, CONTEXT_KANJI_TIMELINE_EVENT);
            this.dedupeKey = stringArg(rest, 9, CONTEXT_KANJI_TIMELINE_EVENT);
        }
    }

}
