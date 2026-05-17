package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public abstract class RecordsStudyModels extends RecordsImportModels {
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
            this.lastRating = nullToEmpty(requestedLastRating);
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
            String[] parts = TASK_MEMORY_SEPARATOR.split(encoded, -1);
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
        public final TaskMemory meaningKanjiMemory;
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
            this.suppressedByTaskType = nullToEmpty(args.suppressedByTaskType);
            this.suppressedAtMillis = Math.max(0L, args.suppressedAtMillis);
            this.matureIntervalDays = Math.max(0, args.matureIntervalDays);
            this.answerSignature = nullToEmpty(args.answerSignature);
            this.activeToken = args.activeToken;
            this.createdAtMillis = args.createdAtMillis;
            this.typingMeaningMemory = Objects.requireNonNullElseGet(args.typingMeaningMemory, TaskMemory::initial);
            this.meaningKanjiMemory = Objects.requireNonNullElseGet(args.meaningKanjiMemory, TaskMemory::initial);
            this.kanjiMeaningMemory = Objects.requireNonNullElseGet(args.kanjiMeaningMemory, TaskMemory::initial);
            this.fontMeaningMemory = Objects.requireNonNullElseGet(args.fontMeaningMemory, TaskMemory::initial);
            this.wordReadingMemory = Objects.requireNonNullElseGet(args.wordReadingMemory, TaskMemory::initial);
            this.writingRemediationMemory = Objects.requireNonNullElseGet(args.writingRemediationMemory, TaskMemory::initial);
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
            this.similarKanjiMemory = Objects.requireNonNullElseGet(args.similarKanjiMemory, TaskMemory::initial);
        }

        protected static LadderRung derivedRung(boolean writingRemediationPending, int recognitionStage) {
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

        protected static SchedulerPhase derivedPhase(String state, int totalReviews, boolean writingRemediationPending) {
            if (LEARNING_REPEAT_REVIEW.equals(state) || "retired".equals(state)) {
                return SchedulerPhase.REVIEW;
            }
            if (writingRemediationPending || totalReviews > 0) {
                return SchedulerPhase.RELEARNING;
            }
            return SchedulerPhase.NEW_LEARNING;
        }

        protected static final class StudyItemArgs {
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
            TaskMemory meaningKanjiMemory;
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
                requireArgCount(CONTEXT_STUDY_ITEM, rest, 5, 9, 13, 17, 18, 19, 25, 26);
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
                if (rest.length == 25 || rest.length == 26) {
                    int stateStart = rest.length == 25 ? 18 : 19;
                    args.rung = (LadderRung) arg(rest, stateStart, CONTEXT_STUDY_ITEM);
                    args.phase = (SchedulerPhase) arg(rest, stateStart + 1, CONTEXT_STUDY_ITEM);
                    args.realPassStreak = intArg(rest, stateStart + 2, CONTEXT_STUDY_ITEM);
                    args.realAgainStreak = intArg(rest, stateStart + 3, CONTEXT_STUDY_ITEM);
                    args.lastRealReviewDueAtMillis = longArg(rest, stateStart + 4, CONTEXT_STUDY_ITEM);
                    args.hasSimilarKanji = booleanArg(rest, stateStart + 5, CONTEXT_STUDY_ITEM);
                    args.similarKanjiMemory = (TaskMemory) arg(rest, stateStart + 6, CONTEXT_STUDY_ITEM);
                }
                return args;
            }

            void seedMemories(String state, long dueAtMillis, double stability, double difficulty, int totalReviews) {
                typingMeaningMemory = seedMemoryForStage(-1, this, state, dueAtMillis, stability, difficulty, totalReviews);
                meaningKanjiMemory = TaskMemory.initial();
                kanjiMeaningMemory = seedMemoryForStage(0, this, state, dueAtMillis, stability, difficulty, totalReviews);
                fontMeaningMemory = seedMemoryForStage(1, this, state, dueAtMillis, stability, difficulty, totalReviews);
                wordReadingMemory = seedMemoryForStage(2, this, state, dueAtMillis, stability, difficulty, totalReviews);
                writingRemediationMemory = seedMemoryForWriting(this, state, dueAtMillis, stability, difficulty, totalReviews);
                similarKanjiMemory = TaskMemory.initial();
            }

            void applyMemories(Object[] rest, int start) {
                if (rest.length == 17) {
                    kanjiMeaningMemory = (TaskMemory) arg(rest, start, CONTEXT_STUDY_ITEM);
                    fontMeaningMemory = (TaskMemory) arg(rest, start + 1, CONTEXT_STUDY_ITEM);
                    wordReadingMemory = (TaskMemory) arg(rest, start + 2, CONTEXT_STUDY_ITEM);
                    writingRemediationMemory = (TaskMemory) arg(rest, start + 3, CONTEXT_STUDY_ITEM);
                    return;
                }
                typingMeaningMemory = (TaskMemory) arg(rest, start, CONTEXT_STUDY_ITEM);
                if (rest.length == 19 || rest.length == 26) {
                    meaningKanjiMemory = (TaskMemory) arg(rest, start + 1, CONTEXT_STUDY_ITEM);
                    kanjiMeaningMemory = (TaskMemory) arg(rest, start + 2, CONTEXT_STUDY_ITEM);
                    fontMeaningMemory = (TaskMemory) arg(rest, start + 3, CONTEXT_STUDY_ITEM);
                    wordReadingMemory = (TaskMemory) arg(rest, start + 4, CONTEXT_STUDY_ITEM);
                    writingRemediationMemory = (TaskMemory) arg(rest, start + 5, CONTEXT_STUDY_ITEM);
                    return;
                }
                kanjiMeaningMemory = (TaskMemory) arg(rest, start + 1, CONTEXT_STUDY_ITEM);
                fontMeaningMemory = (TaskMemory) arg(rest, start + 2, CONTEXT_STUDY_ITEM);
                wordReadingMemory = (TaskMemory) arg(rest, start + 3, CONTEXT_STUDY_ITEM);
                writingRemediationMemory = (TaskMemory) arg(rest, start + 4, CONTEXT_STUDY_ITEM);
            }

            protected static TaskMemory seedMemoryForStage(
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

            protected static TaskMemory seedMemoryForWriting(
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
            return switch (taskType) {
                case BridgeScheduler.TASK_WRITING_REMEDIATION, BridgeScheduler.TASK_WRITE_KANJI -> writingRemediationMemory;
                case BridgeScheduler.TASK_TYPING_MEANING, BridgeScheduler.TASK_TYPE_MEANING -> typingMeaningMemory;
                case BridgeScheduler.TASK_SIMILAR_KANJI -> similarKanjiMemory;
                case BridgeScheduler.TASK_MEANING_KANJI -> meaningKanjiMemory;
                case BridgeScheduler.TASK_WORD_READING -> wordReadingMemory;
                case BridgeScheduler.TASK_FONT_MEANING -> fontMeaningMemory;
                default -> kanjiMeaningMemory;
            };
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
                case MEANING_KANJI:
                    return meaningKanjiMemory;
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
            return switch (taskType) {
                case BridgeScheduler.TASK_WRITING_REMEDIATION, BridgeScheduler.TASK_WRITE_KANJI ->
                        copyBuilder().writingRemediationMemory(memory).build();
                case BridgeScheduler.TASK_TYPING_MEANING, BridgeScheduler.TASK_TYPE_MEANING ->
                        copyBuilder().typingMeaningMemory(memory).build();
                case BridgeScheduler.TASK_SIMILAR_KANJI -> copyBuilder().similarKanjiMemory(memory).build();
                case BridgeScheduler.TASK_MEANING_KANJI -> copyBuilder().meaningKanjiMemory(memory).build();
                case BridgeScheduler.TASK_WORD_READING -> copyBuilder().wordReadingMemory(memory).build();
                case BridgeScheduler.TASK_FONT_MEANING -> copyBuilder().fontMeaningMemory(memory).build();
                default -> copyBuilder().kanjiMeaningMemory(memory).build();
            };
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
            String kanji;
            String state;
            long dueAtMillis;
            double stability;
            double difficulty;
            int totalReviews;
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
            String answerSignature;
            String activeToken;
            long createdAtMillis;
            TaskMemory typingMeaningMemory;
            TaskMemory meaningKanjiMemory;
            TaskMemory kanjiMeaningMemory;
            TaskMemory fontMeaningMemory;
            TaskMemory wordReadingMemory;
            TaskMemory writingRemediationMemory;
            LadderRung rung;
            SchedulerPhase phase;
            int realPassStreak;
            int realAgainStreak;
            long lastRealReviewDueAtMillis;
            boolean hasSimilarKanji;
            TaskMemory similarKanjiMemory;
            boolean legacyFieldModified;
            boolean rungExplicitlySet;

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
                this.meaningKanjiMemory = src.meaningKanjiMemory;
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
            public StudyItemBuilder recognitionStage(int value) { this.recognitionStage = value; this.legacyFieldModified = true; return this; }
            public StudyItemBuilder consecutiveFailedRecognitionDays(int value) { this.consecutiveFailedRecognitionDays = value; return this; }
            public StudyItemBuilder lastFailedRecognitionDayMillis(long value) { this.lastFailedRecognitionDayMillis = value; return this; }
            public StudyItemBuilder writingRemediationPending(boolean value) { this.writingRemediationPending = value; this.legacyFieldModified = true; return this; }
            public StudyItemBuilder suppressedByTaskType(String value) { this.suppressedByTaskType = value; return this; }
            public StudyItemBuilder suppressedAtMillis(long value) { this.suppressedAtMillis = value; return this; }
            public StudyItemBuilder matureIntervalDays(int value) { this.matureIntervalDays = value; return this; }
            public StudyItemBuilder answerSignature(String value) { this.answerSignature = value; return this; }
            public StudyItemBuilder activeToken(String value) { this.activeToken = value; return this; }
            public StudyItemBuilder createdAtMillis(long value) { this.createdAtMillis = value; return this; }
            public StudyItemBuilder typingMeaningMemory(TaskMemory value) { this.typingMeaningMemory = value; return this; }
            public StudyItemBuilder meaningKanjiMemory(TaskMemory value) { this.meaningKanjiMemory = value; return this; }
            public StudyItemBuilder kanjiMeaningMemory(TaskMemory value) { this.kanjiMeaningMemory = value; return this; }
            public StudyItemBuilder fontMeaningMemory(TaskMemory value) { this.fontMeaningMemory = value; return this; }
            public StudyItemBuilder wordReadingMemory(TaskMemory value) { this.wordReadingMemory = value; return this; }
            public StudyItemBuilder writingRemediationMemory(TaskMemory value) { this.writingRemediationMemory = value; return this; }
            public StudyItemBuilder rung(LadderRung value) { this.rung = value; this.rungExplicitlySet = true; return this; }
            public StudyItemBuilder phase(SchedulerPhase value) { this.phase = value; return this; }
            public StudyItemBuilder realPassStreak(int value) { this.realPassStreak = value; return this; }
            public StudyItemBuilder realAgainStreak(int value) { this.realAgainStreak = value; return this; }
            public StudyItemBuilder lastRealReviewDueAtMillis(long value) { this.lastRealReviewDueAtMillis = value; return this; }
            public StudyItemBuilder hasSimilarKanji(boolean value) { this.hasSimilarKanji = value; return this; }
            public StudyItemBuilder similarKanjiMemory(TaskMemory value) { this.similarKanjiMemory = value; return this; }

            public StudyItem build() {
                // If legacy fields (writingRemediationPending, recognitionStage)
                // were modified without an explicit rung() call, pass null to
                // force re-derivation in the constructor.
                LadderRung effectiveRung = (legacyFieldModified && !rungExplicitlySet) ? null : rung;
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
                        meaningKanjiMemory,
                        kanjiMeaningMemory,
                        fontMeaningMemory,
                        wordReadingMemory,
                        writingRemediationMemory,
                        effectiveRung,
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

}
