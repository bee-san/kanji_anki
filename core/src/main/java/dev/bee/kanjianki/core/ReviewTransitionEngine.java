package dev.bee.kanjianki.core;

import java.util.List;
import java.util.Objects;
import java.util.Set;

final class ReviewTransitionEngine {
    private final KaniFsrsAdapter fsrsAdapter;

    ReviewTransitionEngine(KaniFsrsAdapter fsrsAdapter) {
        this.fsrsAdapter = Objects.requireNonNull(fsrsAdapter);
    }

    RecordsSchedulerModels.ReviewResult applyReview(
            RecordsStudyModels.StudyItem item,
            RecordsSchedulerModels.ReviewRequest request,
            Set<String> consumedTokens,
            long nowMillis,
            RecordsSchedulerModels.SchedulerParameters parameters,
            RecordsSyncModels.Settings settings,
            RecordsSchedulerModels.LearningStepSettings learningSettings,
            RecordsBase.StudyLadderSettings ladder
    ) {
        RecordsSchedulerModels.SchedulerParameters resolvedParameters = parameters == null ? RecordsSchedulerModels.SchedulerParameters.defaults() : parameters;
        RecordsSyncModels.Settings resolvedSettings = settings == null ? RecordsSyncModels.Settings.kikuDefaults() : settings;
        RecordsSchedulerModels.LearningStepSettings resolvedSteps = learningSettings == null ? RecordsSchedulerModels.LearningStepSettings.defaults() : learningSettings;
        RecordsBase.StudyLadderSettings resolvedLadder = StudyLadderRules.safeLadder(ladder);
        RecordsSchedulerModels.ReviewResult duplicate = duplicateReviewResult(item, request, consumedTokens);
        if (duplicate != null) {
            return duplicate;
        }
        consumedTokens.add(request.token);
        ReviewContext context = ReviewContext.from(item, request, resolvedParameters, resolvedSettings, resolvedSteps, resolvedLadder, nowMillis);
        ReviewState state = ReviewState.from(context);
        applyLadderTransition(context, state);
        updateWritingLevel(context, state);
        return new RecordsSchedulerModels.ReviewResult(updatedStudyItem(context, state), context.rating, false, "Review applied.");
    }

    private RecordsSchedulerModels.ReviewResult duplicateReviewResult(
            RecordsStudyModels.StudyItem item,
            RecordsSchedulerModels.ReviewRequest request,
            Set<String> consumedTokens
    ) {
        if (consumedTokens.contains(request.token)) {
            return new RecordsSchedulerModels.ReviewResult(item, "duplicate", true, "Review token already consumed.");
        }
        if (item.activeToken != null && !item.activeToken.isEmpty() && !item.activeToken.equals(request.token)) {
            return new RecordsSchedulerModels.ReviewResult(item, "duplicate", true, "Review token does not match the active session.");
        }
        return null;
    }

    private void applyLadderTransition(ReviewContext context, ReviewState state) {
        switch (context.phase) {
            case NEW_LEARNING:
                applyLearningTransition(context, state, true);
                break;
            case RELEARNING:
                applyLearningTransition(context, state, false);
                break;
            case REVIEW:
            default:
                applyReviewTransition(context, state);
                break;
        }
    }

    private void applyLearningTransition(ReviewContext context, ReviewState state, boolean isNewLearning) {
        List<Integer> steps = isNewLearning
                ? context.learningSettings.newStepsMinutes
                : context.learningSettings.reviewStepsMinutes;
        switch (context.rating) {
            case StudyRatings.AGAIN:
                state.stepIndex = 0;
                state.due = context.nowMillis + StudyLadderRules.stepDelayMillis(steps.get(0));
                state.phase = isNewLearning ? RecordsBase.SchedulerPhase.NEW_LEARNING : RecordsBase.SchedulerPhase.RELEARNING;
                state.schedulerState = StudyLadderRules.STATE_LEARNING;
                break;
            case StudyRatings.HARD:
                applyLearningHard(context, state, steps, isNewLearning);
                break;
            case StudyRatings.EASY:
                graduateToReview(context, state, isNewLearning);
                break;
            case StudyRatings.GOOD:
            default:
                applyLearningGood(context, state, steps, isNewLearning);
                break;
        }
    }

    private void applyLearningHard(ReviewContext context, ReviewState state, List<Integer> steps, boolean isNewLearning) {
        int idx = Math.max(0, state.stepIndex);
        if (idx == 0 && steps.size() >= 2) {
            long avg = (StudyLadderRules.stepDelayMillis(steps.get(0)) + StudyLadderRules.stepDelayMillis(steps.get(1))) / 2L;
            state.due = context.nowMillis + Math.max(StudyLadderRules.stepDelayMillis(steps.get(0)), avg);
        } else {
            int safeIdx = Math.min(idx, steps.size() - 1);
            state.due = context.nowMillis + StudyLadderRules.stepDelayMillis(steps.get(safeIdx));
        }
        state.stepIndex = idx;
        state.phase = isNewLearning ? RecordsBase.SchedulerPhase.NEW_LEARNING : RecordsBase.SchedulerPhase.RELEARNING;
        state.schedulerState = StudyLadderRules.STATE_LEARNING;
    }

    private void applyLearningGood(ReviewContext context, ReviewState state, List<Integer> steps, boolean isNewLearning) {
        int nextIdx = state.stepIndex + 1;
        if (nextIdx >= steps.size()) {
            graduateToReview(context, state, isNewLearning);
            return;
        }
        state.stepIndex = nextIdx;
        state.due = context.nowMillis + StudyLadderRules.stepDelayMillis(steps.get(nextIdx));
        state.phase = isNewLearning ? RecordsBase.SchedulerPhase.NEW_LEARNING : RecordsBase.SchedulerPhase.RELEARNING;
        state.schedulerState = StudyLadderRules.STATE_LEARNING;
    }

    private void graduateToReview(ReviewContext context, ReviewState state, boolean isNewLearning) {
        state.stepIndex = 0;
        KaniFsrsReviewResult result = fsrsAdapter.initialReview(
                context.rating,
                state.stability,
                state.difficulty,
                context.parameters.targetRetention,
                isNewLearning
        );
        state.stability = result.stability;
        state.difficulty = result.difficulty;
        state.scheduledIntervalDays = result.intervalDays();
        state.due = context.nowMillis + result.intervalMillis;
        state.phase = RecordsBase.SchedulerPhase.REVIEW;
        state.schedulerState = StudyLadderRules.STATE_REVIEW;
    }

    private void applyReviewTransition(ReviewContext context, ReviewState state) {
        switch (context.rating) {
            case StudyRatings.AGAIN:
                applyReviewAgain(context, state);
                break;
            case StudyRatings.HARD:
                applyReviewPass(context, state);
                break;
            case StudyRatings.EASY:
                applyReviewPass(context, state);
                break;
            case StudyRatings.GOOD:
            default:
                applyReviewPass(context, state);
                break;
        }
    }

    private void applyReviewAgain(ReviewContext context, ReviewState state) {
        state.lapses++;
        state.taskLapses++;
        KaniFsrsReviewResult result = fsrsAdapter.review(
                state.stability,
                state.difficulty,
                StudyRatings.AGAIN,
                context.elapsedReviewDays,
                context.parameters.targetRetention
        );
        state.stability = result.stability;
        state.difficulty = result.difficulty;

        List<Integer> relearning = context.learningSettings.reviewStepsMinutes;
        state.phase = RecordsBase.SchedulerPhase.RELEARNING;
        state.stepIndex = 0;
        state.due = context.nowMillis + StudyLadderRules.stepDelayMillis(relearning.get(0));
        state.schedulerState = StudyLadderRules.STATE_LEARNING;
        state.scheduledIntervalDays = 0;

        if (countsAsRealDue(context, state)) {
            state.realPassStreak = 0;
            state.realAgainStreak++;
            state.lastRealReviewDueAtMillis = context.item.dueAtMillis;
            if (state.realAgainStreak >= context.settings.ladderDemotionFailStreak) {
                state.rung = StudyLadderRules.demoteRung(state.rung, context.item.hasSimilarKanji, context.ladder);
                state.realAgainStreak = 0;
                state.realPassStreak = 0;
            }
        }
    }

    private void applyReviewPass(ReviewContext context, ReviewState state) {
        KaniFsrsReviewResult result = fsrsAdapter.review(
                state.stability,
                state.difficulty,
                context.rating,
                context.elapsedReviewDays,
                context.parameters.targetRetention
        );
        state.stability = result.stability;
        state.difficulty = result.difficulty;
        state.scheduledIntervalDays = result.intervalDays();
        state.due = context.nowMillis + result.intervalMillis;
        state.phase = RecordsBase.SchedulerPhase.REVIEW;
        state.schedulerState = StudyLadderRules.STATE_REVIEW;
        state.stepIndex = 0;

        if (countsAsRealDue(context, state)) {
            state.realAgainStreak = 0;
            state.realPassStreak++;
            state.lastRealReviewDueAtMillis = context.item.dueAtMillis;
            if (promotesByFsrsInterval(result, context.settings.ladderPromotionIntervalDays)) {
                state.rung = StudyLadderRules.promoteRung(state.rung, context.item.hasSimilarKanji, context.ladder);
                state.realPassStreak = 0;
                state.realAgainStreak = 0;
            }
        }
    }

    private boolean promotesByFsrsInterval(KaniFsrsReviewResult result, int promotionDays) {
        return result.intervalMillis > Math.max(1, promotionDays) * StudyLadderRules.DAY;
    }

    private boolean countsAsRealDue(ReviewContext context, ReviewState state) {
        long currentDueSlot = context.item.dueAtMillis;
        if (currentDueSlot > context.nowMillis) {
            return false;
        }
        return state.lastRealReviewDueAtMillis == 0L
                || state.lastRealReviewDueAtMillis != currentDueSlot;
    }

    private void updateWritingLevel(ReviewContext context, ReviewState state) {
        if (context.rung != RecordsBase.LadderRung.WRITE_KANJI) {
            return;
        }
        if (context.failedWriting) {
            state.writingLevel = Math.max(0, state.writingLevel - 1);
        } else if (context.cleanWritingPass) {
            state.writingLevel = Math.min(3, state.writingLevel + 1);
        }
    }

    private RecordsStudyModels.StudyItem updatedStudyItem(ReviewContext context, ReviewState state) {
        RecordsStudyModels.TaskMemory updatedMemory = new RecordsStudyModels.TaskMemory(
                state.schedulerState,
                state.due,
                round(state.stability),
                round(state.difficulty),
                state.taskTotal,
                state.taskLapses,
                state.stepIndex,
                context.rating,
                state.scheduledIntervalDays,
                taskMemoryConsecutivePasses(context, state),
                taskMemoryLastPassedDueAt(context, state)
        );
        RecordsStudyModels.StudyItem base = context.item.copyBuilder()
                .state(state.schedulerState)
                .dueAtMillis(state.due)
                .stability(round(state.stability))
                .difficulty(round(state.difficulty))
                .totalReviews(state.total)
                .lapses(state.lapses)
                .learningStep(state.stepIndex)
                .writingLevel(state.writingLevel)
                .recognitionStage(StudyLadderRules.rungToLegacyStage(state.rung))
                .writingRemediationPending(state.rung == RecordsBase.LadderRung.WRITE_KANJI)
                .consecutiveFailedRecognitionDays(state.realAgainStreak)
                .lastFailedRecognitionDayMillis(state.lastRealReviewDueAtMillis)
                .matureIntervalDays(state.scheduledIntervalDays)
                .rung(state.rung)
                .phase(state.phase)
                .realPassStreak(state.realPassStreak)
                .realAgainStreak(state.realAgainStreak)
                .lastRealReviewDueAtMillis(state.lastRealReviewDueAtMillis)
                .activeToken(null)
                .build();
        RecordsStudyModels.StudyItem updated = base.withTaskMemory(context.reviewedTaskType, updatedMemory);
        String activeTaskType = state.rung.wireName();
        if (!activeTaskType.equals(context.reviewedTaskType)) {
            updated = updated.withTaskMemory(activeTaskType, updatedMemory);
        }
        return updated;
    }

    private int taskMemoryConsecutivePasses(ReviewContext context, ReviewState state) {
        return StudyRatings.AGAIN.equals(context.rating) ? 0 : state.realPassStreak;
    }

    private long taskMemoryLastPassedDueAt(ReviewContext context, ReviewState state) {
        return StudyRatings.AGAIN.equals(context.rating) ? 0L : state.lastRealReviewDueAtMillis;
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static final class ReviewContext {
        RecordsStudyModels.StudyItem item;
        RecordsSchedulerModels.ReviewRequest request;
        RecordsSchedulerModels.SchedulerParameters parameters;
        RecordsSyncModels.Settings settings;
        RecordsSchedulerModels.LearningStepSettings learningSettings;
        RecordsBase.StudyLadderSettings ladder;
        RecordsStudyModels.TaskMemory previousTaskMemory;
        RecordsBase.LadderRung rung;
        RecordsBase.SchedulerPhase phase;
        long nowMillis;
        int elapsedReviewDays;
        String rating;
        String reviewedTaskType;
        boolean cleanWritingPass;
        boolean failedWriting;

        static ReviewContext from(
                RecordsStudyModels.StudyItem item,
                RecordsSchedulerModels.ReviewRequest request,
                RecordsSchedulerModels.SchedulerParameters parameters,
                RecordsSyncModels.Settings settings,
                RecordsSchedulerModels.LearningStepSettings learningSettings,
                RecordsBase.StudyLadderSettings ladder,
                long nowMillis
        ) {
            ReviewContext context = new ReviewContext();
            context.item = item;
            context.request = request;
            context.parameters = parameters;
            context.settings = settings;
            context.learningSettings = learningSettings;
            context.ladder = StudyLadderRules.safeLadder(ladder);
            context.nowMillis = nowMillis;
            context.rung = context.ladder.effectiveRung(item.rung, item.hasSimilarKanji);
            context.phase = item.phase;
            context.reviewedTaskType = context.rung.wireName();
            context.previousTaskMemory = context.activeTaskMemory();
            context.elapsedReviewDays = context.elapsedReviewDays();
            context.rating = resolveRating(request, context.rung);
            boolean writingRung = context.rung == RecordsBase.LadderRung.WRITE_KANJI;
            boolean writingReviewCanMoveHelp = writingRung && request.writingRequired && !request.manualOverride;
            context.cleanWritingPass = writingReviewCanMoveHelp
                    && request.writingPassed
                    && request.writingClean
                    && request.hintsUsed <= 0;
            context.failedWriting = writingReviewCanMoveHelp && !request.writingPassed;
            return context;
        }

        private int elapsedReviewDays() {
            long previousIntervalMillis = Math.max(0L, previousTaskMemory.matureIntervalDays) * StudyLadderRules.DAY;
            long lastReviewAtMillis = Math.max(0L, previousTaskMemory.dueAtMillis - previousIntervalMillis);
            long elapsedMillis = Math.max(0L, nowMillis - lastReviewAtMillis);
            return (int) Math.min(Integer.MAX_VALUE, elapsedMillis / StudyLadderRules.DAY);
        }

        private RecordsStudyModels.TaskMemory activeTaskMemory() {
            RecordsStudyModels.TaskMemory memory = item.memoryForRung(rung);
            if (memory.totalReviews > 0 || item.totalReviews <= 0) {
                return memory;
            }
            return RecordsStudyModels.TaskMemory.fromStudyFields(
                    item.state,
                    item.dueAtMillis,
                    item.stability,
                    item.difficulty,
                    item.totalReviews,
                    item.lapses,
                    item.learningStep,
                    item.matureIntervalDays
            );
        }

        private static String resolveRating(RecordsSchedulerModels.ReviewRequest request, RecordsBase.LadderRung rung) {
            if (rung == RecordsBase.LadderRung.WRITE_KANJI) {
                if (request.manualOverride) {
                    return StudyRatings.HARD;
                }
                if (request.writingRequired && !request.writingPassed) {
                    return StudyRatings.AGAIN;
                }
            } else if (request.writingRequired && !request.writingPassed && !request.manualOverride) {
                return StudyRatings.AGAIN;
            }
            return StudyRatings.normalize(request.rating);
        }
    }

    private static final class ReviewState {
        int total;
        int lapses;
        int taskTotal;
        int taskLapses;
        int stepIndex;
        int writingLevel;
        int scheduledIntervalDays;
        int realPassStreak;
        int realAgainStreak;
        long lastRealReviewDueAtMillis;
        long due;
        RecordsBase.LadderRung rung;
        RecordsBase.SchedulerPhase phase;
        double stability;
        double difficulty;
        String schedulerState;

        static ReviewState from(ReviewContext context) {
            ReviewState state = new ReviewState();
            state.total = context.item.totalReviews + 1;
            state.lapses = context.item.lapses;
            state.taskTotal = context.previousTaskMemory.totalReviews + 1;
            state.taskLapses = context.previousTaskMemory.lapses;
            state.stepIndex = context.previousTaskMemory.learningStep;
            state.writingLevel = context.item.writingLevel;
            state.rung = context.rung;
            state.phase = context.phase;
            state.realPassStreak = context.item.realPassStreak;
            state.realAgainStreak = context.item.realAgainStreak;
            state.lastRealReviewDueAtMillis = context.item.lastRealReviewDueAtMillis;
            state.stability = context.previousTaskMemory.stability;
            state.difficulty = context.previousTaskMemory.difficulty;
            state.schedulerState = context.item.state;
            return state;
        }
    }
}
