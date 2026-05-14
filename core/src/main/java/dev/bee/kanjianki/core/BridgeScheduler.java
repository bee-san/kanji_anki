package dev.bee.kanjianki.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * The ladder scheduler. Every persisted study item has one {@link Records.LadderRung}
 * (the task shape the learner sees) and one {@link Records.SchedulerPhase}
 * (new_learning / review / relearning). Learning and relearning follow Anki
 * semantics: {@code Again} resets to step 0, {@code Good} advances one step
 * and graduates past the last step, {@code Hard} on step 0 uses a delay
 * between Again and Good, {@code Hard} on later steps repeats the current
 * step, and {@code Easy} graduates immediately.
 * <p>
 * Only persisted due-review attempts in the {@code REVIEW} phase advance the
 * ladder streaks. Reaching {@code realDueReviewsToMove} passes in a row
 * promotes the rung; reaching that many {@code Again}s in a row demotes it.
 * The {@link Records.LadderRung#SIMILAR_KANJI} rung is only part of the
 * ladder when {@link Records.StudyItem#hasSimilarKanji} is true for the
 * card; otherwise promotion and demotion skip over it without pausing.
 */
public final class BridgeScheduler {
    private static final Pattern MULTI_WHITESPACE = Pattern.compile("\\s+");

    private static final long MINUTE = 60_000L;
    private static final long DAY = 86_400_000L;
    private static final int MIN_RECOGNITION_STAGE = -1;
    private static final int MAX_RECOGNITION_STAGE = 2;
    private static final String STATE_NEW = "new";
    private static final String STATE_LEARNING = "learning";
    private static final String STATE_REVIEW = "review";
    private static final String STATE_RETIRED = "retired";
    static final String RATING_AGAIN = "again";
    static final String RATING_HARD = "hard";
    static final String RATING_GOOD = "good";
    static final String RATING_EASY = "easy";

    public static final String TASK_WRITE_KANJI = "write_kanji";
    public static final String TASK_TYPE_MEANING = "type_meaning";
    public static final String TASK_SIMILAR_KANJI = "similar_kanji";
    public static final String TASK_KANJI_MEANING = "kanji_meaning";
    public static final String TASK_FONT_MEANING = "font_meaning";
    public static final String TASK_WORD_READING = "word_reading";

    // Legacy wire-format aliases retained for callers that still persist or
    // read these task-type strings (review_log, task memory lookup).
    public static final String TASK_TYPING_MEANING = "typing_meaning";
    public static final String TASK_WRITING_REMEDIATION = "writing_remediation";

    public List<Records.StudyItem> seedQueue(
            List<Records.DashboardRow> rows,
            List<Records.StudyItem> existing,
            Records.Settings settings,
            long nowMillis,
            long startOfDayMillis
    ) {
        return seedQueueInternal(new SeedQueueRequest(
                rows,
                rows,
                existing,
                settings,
                nowMillis,
                startOfDayMillis,
                new SeedQueueLimits(settings.newPerDay, false)
        ));
    }

    public List<Records.StudyItem> seedQueue(
            List<Records.DashboardRow> rows,
            List<Records.StudyItem> existing,
            Records.Settings settings,
            long nowMillis,
            long startOfDayMillis,
            Records.AdaptiveLoadPlan plan
    ) {
        if (plan == null) {
            return seedQueue(rows, existing, settings, nowMillis, startOfDayMillis);
        }
        List<Records.DashboardRow> admissionRows = plan.allKanjiMode ? rows : rowsForFocus(rows, plan.focusKanji);
        int cappedAdmission = plan.allKanjiMode
                ? plan.newAdmissionLimit
                : Math.min(plan.newAdmissionLimit, settings.newPerDay);
        return seedQueueInternal(new SeedQueueRequest(
                rows,
                admissionRows,
                existing,
                settings,
                nowMillis,
                startOfDayMillis,
                new SeedQueueLimits(cappedAdmission, plan.allKanjiMode)
        ));
    }

    public ExtraNewCardsResult seedExtraNewCards(
            List<Records.DashboardRow> rows,
            List<Records.StudyItem> existing,
            Records.Settings settings,
            long nowMillis,
            long startOfDayMillis,
            int requestedCount
    ) {
        int requested = Math.max(0, requestedCount);
        SeedQueueRequest request = new SeedQueueRequest(
                rows,
                rows,
                existing,
                settings,
                nowMillis,
                startOfDayMillis,
                new SeedQueueLimits(Integer.MAX_VALUE, true)
        );
        SeedRowIndex rowIndex = indexSeedRows(request.allRows);
        SeedQueueState state = reconcileExistingItems(request, rowIndex);
        List<String> admittedKanji = new ArrayList<>();
        int available = 0;
        for (Records.DashboardRow row : request.admissionRows) {
            String rowKey = rowFamilyKey(row);
            Records.StudyItem current = state.byFamily.get(rowKey);
            boolean eligible = current == null || canReopenRetiredExtraSeedItem(request.settings, row, current);
            if (!eligible) {
                continue;
            }
            available++;
            if (admittedKanji.size() >= requested) {
                continue;
            }
            admitExtraSeedRow(request, state, row, rowKey, current);
            admittedKanji.add(row.kanji);
        }
        state.items.sort(Comparator
                .comparing((Records.StudyItem item) -> item.state.equals(STATE_RETIRED))
                .thenComparingLong(item -> item.dueAtMillis)
                .thenComparing(item -> item.kanji));
        return new ExtraNewCardsResult(state.items, admittedKanji, available);
    }

    private boolean canReopenRetiredExtraSeedItem(
            Records.Settings settings,
            Records.DashboardRow row,
            Records.StudyItem current
    ) {
        return STATE_RETIRED.equals(current.state)
                && row.matureSupportCount < settings.matureSupportThreshold;
    }

    private void admitExtraSeedRow(
            SeedQueueRequest request,
            SeedQueueState state,
            Records.DashboardRow row,
            String rowKey,
            Records.StudyItem current
    ) {
        Records.StudyItem admitted = newStudyItem(row.kanji, request.nowMillis, answerSignature(row));
        if (current != null) {
            state.items.remove(current);
        }
        state.items.add(admitted);
        state.byFamily.put(rowKey, admitted);
        state.activeCount++;
        state.newToday++;
    }

    private List<Records.StudyItem> seedQueueInternal(SeedQueueRequest request) {
        SeedRowIndex rowIndex = indexSeedRows(request.allRows);
        SeedQueueState state = reconcileExistingItems(request, rowIndex);
        for (Records.DashboardRow row : request.admissionRows) {
            admitSeedRow(request, state, row);
        }
        state.items.sort(Comparator
                .comparing((Records.StudyItem item) -> item.state.equals(STATE_RETIRED))
                .thenComparingLong(item -> item.dueAtMillis)
                .thenComparing(item -> item.kanji));
        return state.items;
    }

    public Records.StudySession nextSession(List<Records.StudyItem> items, List<Records.DashboardRow> rows, long nowMillis) {
        return nextSession(items, rows, nowMillis, null);
    }

    public Records.StudySession nextSession(
            List<Records.StudyItem> items,
            List<Records.DashboardRow> rows,
            long nowMillis,
            Set<String> allowedKanji
    ) {
        Map<String, Records.DashboardRow> rowByKanji = new HashMap<>();
        for (Records.DashboardRow row : rows) {
            rowByKanji.put(row.kanji, row);
        }
        Records.StudyItem best = null;
        for (Records.StudyItem item : activeQueueItems(items, rows, nowMillis, allowedKanji)) {
            if (item.dueAtMillis > nowMillis) {
                continue;
            }
            if (best == null || compareDueItems(item, best, rowByKanji) < 0) {
                best = item;
            }
        }
        if (best == null) {
            return null;
        }
        Records.DashboardRow row = rowByKanji.get(best.kanji);
        String token = best.activeToken == null || best.activeToken.isEmpty()
                ? best.kanji + "-" + UUID.randomUUID()
                : best.activeToken;
        String taskType = rungTaskType(best.rung);
        boolean writingRequired = best.rung == Records.LadderRung.WRITE_KANJI;
        String prompt = row.reasonText;
        return new Records.StudySession(best.withToken(token), row, token, taskType, writingRequired, prompt);
    }

    private List<Records.DashboardRow> rowsForFocus(List<Records.DashboardRow> rows, List<String> focusKanji) {
        Map<String, Records.DashboardRow> byKanji = new HashMap<>();
        for (Records.DashboardRow row : rows) {
            byKanji.put(row.kanji, row);
        }
        List<Records.DashboardRow> out = new ArrayList<>();
        for (String kanji : focusKanji) {
            Records.DashboardRow row = byKanji.get(kanji);
            if (row != null) {
                out.add(row);
            }
        }
        return out;
    }

    private SeedRowIndex indexSeedRows(List<Records.DashboardRow> rows) {
        SeedRowIndex index = new SeedRowIndex();
        for (Records.DashboardRow row : rows) {
            index.rowByFamily.put(rowFamilyKey(row), row);
            List<Records.DashboardRow> familyRows = index.rowsByKanji.get(row.kanji);
            if (familyRows == null) {
                familyRows = new ArrayList<>();
                index.rowsByKanji.put(row.kanji, familyRows);
            }
            familyRows.add(row);
        }
        return index;
    }

    private SeedQueueState reconcileExistingItems(SeedQueueRequest request, SeedRowIndex rowIndex) {
        SeedQueueState state = new SeedQueueState();
        for (Records.StudyItem item : request.existing) {
            Records.StudyItem current = alignOrRetireSeedItem(request, rowIndex, item);
            state.byFamily.put(familyKey(current), current);
            state.items.add(current);
            state.trackActiveItem(current, request.startOfDayMillis);
        }
        return state;
    }

    private Records.StudyItem alignOrRetireSeedItem(
            SeedQueueRequest request,
            SeedRowIndex rowIndex,
            Records.StudyItem item
    ) {
        Records.DashboardRow row = seedRowForItem(rowIndex, item);
        Records.StudyItem current = row == null ? item : alignAnswerSignature(item, row, request.nowMillis);
        if (shouldRetireSeedItem(request.settings, row, item, current)) {
            return retiredCopy(current);
        }
        return current;
    }

    private Records.DashboardRow seedRowForItem(SeedRowIndex rowIndex, Records.StudyItem item) {
        Records.DashboardRow row = rowIndex.rowByFamily.get(familyKey(item));
        List<Records.DashboardRow> familyRows = rowIndex.rowsByKanji.get(item.kanji);
        if (row != null || familyRows == null || (!item.answerSignature.isEmpty() && familyRows.size() != 1)) {
            return row;
        }
        return familyRows.get(0);
    }

    private boolean shouldRetireSeedItem(
            Records.Settings settings,
            Records.DashboardRow row,
            Records.StudyItem original,
            Records.StudyItem current
    ) {
        return !STATE_RETIRED.equals(original.state)
                && (row == null || (row.matureSupportCount >= settings.matureSupportThreshold && current.totalReviews > 0));
    }

    private void admitSeedRow(SeedQueueRequest request, SeedQueueState state, Records.DashboardRow row) {
        String rowKey = rowFamilyKey(row);
        Records.StudyItem current = state.byFamily.get(rowKey);
        if (current == null) {
            addNewSeedItemIfRoom(request, state, row, rowKey);
        } else if (canReopenRetiredSeedItem(request, state, row, current)) {
            reopenSeedItem(request, state, row, rowKey, current);
        }
    }

    private void addNewSeedItemIfRoom(
            SeedQueueRequest request,
            SeedQueueState state,
            Records.DashboardRow row,
            String rowKey
    ) {
        if (!state.hasAdmissionRoom(request)) {
            return;
        }
        Records.StudyItem item = newStudyItem(row.kanji, request.nowMillis, answerSignature(row));
        state.items.add(item);
        state.byFamily.put(rowKey, item);
        state.activeCount++;
        state.newToday++;
    }

    private boolean canReopenRetiredSeedItem(
            SeedQueueRequest request,
            SeedQueueState state,
            Records.DashboardRow row,
            Records.StudyItem current
    ) {
        return STATE_RETIRED.equals(current.state)
                && row.matureSupportCount < request.settings.matureSupportThreshold
                && state.hasAdmissionRoom(request);
    }

    private void reopenSeedItem(
            SeedQueueRequest request,
            SeedQueueState state,
            Records.DashboardRow row,
            String rowKey,
            Records.StudyItem current
    ) {
        Records.StudyItem reopened = newStudyItem(row.kanji, request.nowMillis, answerSignature(row));
        state.items.remove(current);
        state.items.add(reopened);
        state.byFamily.put(rowKey, reopened);
        state.activeCount++;
        state.newToday++;
    }

    private Records.StudyItem retiredCopy(Records.StudyItem item) {
        return item.copyBuilder()
                .state(STATE_RETIRED)
                .activeToken(null)
                .build();
    }

    private Records.StudyItem newStudyItem(String kanji, long nowMillis, String answerSignature) {
        return new Records.StudyItem(
                kanji,
                STATE_NEW,
                nowMillis,
                0.4,
                5.0,
                0,
                0,
                0,
                0,
                0,
                0,
                0L,
                false,
                null,
                0L,
                0,
                answerSignature,
                null,
                nowMillis,
                Records.TaskMemory.initial(),
                Records.TaskMemory.initial(),
                Records.TaskMemory.initial(),
                Records.TaskMemory.initial(),
                Records.TaskMemory.initial(),
                Records.LadderRung.startingRung(),
                Records.SchedulerPhase.NEW_LEARNING,
                0,
                0,
                0L,
                false,
                Records.TaskMemory.initial()
        );
    }

    private Records.StudyItem alignAnswerSignature(Records.StudyItem item, Records.DashboardRow row, long nowMillis) {
        String signature = answerSignature(row);
        if (item.answerSignature.isEmpty() || signature.equals(item.answerSignature)) {
            return item.withAnswerSignature(signature);
        }
        boolean retired = STATE_RETIRED.equals(item.state);
        if (retired) {
            return item.copyBuilder().answerSignature(signature).build();
        }
        // The answer signature under the card changed; reset short-term state
        // and demote one rung so the learner re-proves competence on the new
        // material.
        Records.LadderRung fallbackRung = demoteRung(item.rung, item.hasSimilarKanji);
        return item.copyBuilder()
                .state(STATE_LEARNING)
                .dueAtMillis(nowMillis)
                .stability(0.4)
                .difficulty(5.0)
                .totalReviews(0)
                .lapses(0)
                .learningStep(0)
                .consecutiveFailedRecognitionDays(0)
                .lastFailedRecognitionDayMillis(0L)
                .writingRemediationPending(false)
                .suppressedByTaskType(null)
                .suppressedAtMillis(0L)
                .matureIntervalDays(0)
                .answerSignature(signature)
                .activeToken(null)
                .typingMeaningMemory(Records.TaskMemory.initial())
                .kanjiMeaningMemory(Records.TaskMemory.initial())
                .fontMeaningMemory(Records.TaskMemory.initial())
                .wordReadingMemory(Records.TaskMemory.initial())
                .writingRemediationMemory(Records.TaskMemory.initial())
                .similarKanjiMemory(Records.TaskMemory.initial())
                .rung(fallbackRung)
                .phase(Records.SchedulerPhase.NEW_LEARNING)
                .realPassStreak(0)
                .realAgainStreak(0)
                .lastRealReviewDueAtMillis(0L)
                .build();
    }

    private static int compareDueItems(
            Records.StudyItem left,
            Records.StudyItem right,
            Map<String, Records.DashboardRow> rowByKanji
    ) {
        int priority = Integer.compare(duePriority(left), duePriority(right));
        if (priority != 0) {
            return priority;
        }
        int due = Long.compare(left.dueAtMillis, right.dueAtMillis);
        if (due != 0) {
            return due;
        }
        int weakness = Integer.compare(rowWeakness(right, rowByKanji), rowWeakness(left, rowByKanji));
        if (weakness != 0) {
            return weakness;
        }
        return left.kanji.compareTo(right.kanji);
    }

    private static int duePriority(Records.StudyItem item) {
        if (item.rung == Records.LadderRung.WRITE_KANJI || item.phase == Records.SchedulerPhase.RELEARNING) {
            return 0;
        }
        if (item.phase == Records.SchedulerPhase.NEW_LEARNING) {
            return item.totalReviews > 0 ? 0 : 2;
        }
        return 1;
    }

    private static int rowWeakness(Records.StudyItem item, Map<String, Records.DashboardRow> rowByKanji) {
        return rowByKanji.get(item.kanji).weaknessScore;
    }

    public Records.ReviewResult applyReview(
            Records.StudyItem item,
            Records.ReviewRequest request,
            Set<String> consumedTokens,
            long nowMillis
    ) {
        return applyReview(item, request, consumedTokens, nowMillis, Records.SchedulerParameters.defaults(), Records.Settings.kikuDefaults());
    }

    public Records.ReviewResult applyReview(
            Records.StudyItem item,
            Records.ReviewRequest request,
            Set<String> consumedTokens,
            long nowMillis,
            Records.SchedulerParameters parameters
    ) {
        return applyReview(item, request, consumedTokens, nowMillis, parameters, Records.Settings.kikuDefaults());
    }

    public Records.ReviewResult applyReview(
            Records.StudyItem item,
            Records.ReviewRequest request,
            Set<String> consumedTokens,
            long nowMillis,
            Records.SchedulerParameters parameters,
            Records.Settings settings
    ) {
        return applyReview(item, request, consumedTokens, nowMillis, parameters, settings, Records.LearningStepSettings.defaults());
    }

    public Records.ReviewResult applyReview(
            Records.StudyItem item,
            Records.ReviewRequest request,
            Set<String> consumedTokens,
            long nowMillis,
            Records.SchedulerParameters parameters,
            Records.Settings settings,
            Records.LearningStepSettings learningSettings
    ) {
        Records.SchedulerParameters resolvedParameters = parameters == null ? Records.SchedulerParameters.defaults() : parameters;
        Records.Settings resolvedSettings = settings == null ? Records.Settings.kikuDefaults() : settings;
        Records.LearningStepSettings resolvedSteps = learningSettings == null ? Records.LearningStepSettings.defaults() : learningSettings;
        Records.ReviewResult duplicate = duplicateReviewResult(item, request, consumedTokens);
        if (duplicate != null) {
            return duplicate;
        }
        consumedTokens.add(request.token);
        ReviewContext context = ReviewContext.from(item, request, resolvedParameters, resolvedSettings, resolvedSteps, nowMillis);
        ReviewState state = ReviewState.from(context);
        applyLadderTransition(context, state);
        updateWritingLevel(context, state);
        return new Records.ReviewResult(updatedStudyItem(context, state), context.rating, false, "Review applied.");
    }

    private Records.ReviewResult duplicateReviewResult(
            Records.StudyItem item,
            Records.ReviewRequest request,
            Set<String> consumedTokens
    ) {
        if (consumedTokens.contains(request.token)) {
            return new Records.ReviewResult(item, "duplicate", true, "Review token already consumed.");
        }
        if (item.activeToken != null && !item.activeToken.isEmpty() && !item.activeToken.equals(request.token)) {
            return new Records.ReviewResult(item, "duplicate", true, "Review token does not match the active session.");
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
            case RATING_AGAIN:
                state.stepIndex = 0;
                state.due = context.nowMillis + stepDelayMillis(steps.get(0));
                state.phase = isNewLearning ? Records.SchedulerPhase.NEW_LEARNING : Records.SchedulerPhase.RELEARNING;
                state.schedulerState = STATE_LEARNING;
                break;
            case RATING_HARD:
                applyLearningHard(context, state, steps, isNewLearning);
                break;
            case RATING_EASY:
                graduateToReview(context, state);
                break;
            case RATING_GOOD:
            default:
                applyLearningGood(context, state, steps, isNewLearning);
                break;
        }
        // Learning / relearning repeats are practice-only; ladder streaks and
        // rung move markers must not advance here.
    }

    private void applyLearningHard(ReviewContext context, ReviewState state, List<Integer> steps, boolean isNewLearning) {
        int idx = Math.max(0, state.stepIndex);
        if (idx == 0 && steps.size() >= 2) {
            // Anki "Hard on first step" uses a delay half-way between Again
            // and Good delays. Again delay is step 0, Good delay is step 1.
            long avg = (stepDelayMillis(steps.get(0)) + stepDelayMillis(steps.get(1))) / 2L;
            state.due = context.nowMillis + Math.max(stepDelayMillis(steps.get(0)), avg);
        } else {
            // On later steps, Hard repeats the current step.
            int safeIdx = Math.min(idx, steps.size() - 1);
            state.due = context.nowMillis + stepDelayMillis(steps.get(safeIdx));
        }
        state.stepIndex = idx;
        state.phase = isNewLearning ? Records.SchedulerPhase.NEW_LEARNING : Records.SchedulerPhase.RELEARNING;
        state.schedulerState = STATE_LEARNING;
    }

    private void applyLearningGood(ReviewContext context, ReviewState state, List<Integer> steps, boolean isNewLearning) {
        int nextIdx = state.stepIndex + 1;
        if (nextIdx >= steps.size()) {
            graduateToReview(context, state);
            return;
        }
        state.stepIndex = nextIdx;
        state.due = context.nowMillis + stepDelayMillis(steps.get(nextIdx));
        state.phase = isNewLearning ? Records.SchedulerPhase.NEW_LEARNING : Records.SchedulerPhase.RELEARNING;
        state.schedulerState = STATE_LEARNING;
    }

    private void graduateToReview(ReviewContext context, ReviewState state) {
        state.stepIndex = 0;
        int fsrsRating = Fsrs5Engine.ratingToInt(context.rating);
        Fsrs5Engine engine = new Fsrs5Engine(null, context.parameters.targetRetention);
        state.stability = engine.initialStability(fsrsRating == Fsrs5Engine.ratingToInt("again") ? 3 : fsrsRating);
        state.difficulty = engine.updateDifficulty(state.difficulty, fsrsRating);
        long interval = engine.nextIntervalMillis(state.stability);
        state.scheduledIntervalDays = intervalDays(interval);
        state.due = context.nowMillis + interval;
        state.phase = Records.SchedulerPhase.REVIEW;
        state.schedulerState = STATE_REVIEW;
    }

    private void applyReviewTransition(ReviewContext context, ReviewState state) {
        switch (context.rating) {
            case RATING_AGAIN:
                applyReviewAgain(context, state);
                break;
            case RATING_HARD:
                applyReviewPass(context, state, context.parameters.hardMultiplier, 0.2);
                break;
            case RATING_EASY:
                applyReviewPass(context, state, context.parameters.easyMultiplier, -0.35);
                break;
            case RATING_GOOD:
            default:
                applyReviewPass(context, state, context.parameters.goodMultiplier, -0.1);
                break;
        }
    }

    private void applyReviewAgain(ReviewContext context, ReviewState state) {
        state.lapses++;
        state.taskLapses++;
        Fsrs5Engine engine = new Fsrs5Engine(null, context.parameters.targetRetention);
        state.difficulty = engine.updateDifficulty(state.difficulty, Fsrs5Engine.ratingToInt("again"));
        double elapsedDays = Fsrs5Engine.elapsedDays(context.item.dueAtMillis, context.nowMillis);
        double retrievability = engine.retrievability(elapsedDays, state.stability);
        state.stability = engine.stabilityAfterForgetting(state.stability, state.difficulty, retrievability);

        List<Integer> relearning = context.learningSettings.reviewStepsMinutes;
        state.phase = Records.SchedulerPhase.RELEARNING;
        state.stepIndex = 0;
        state.due = context.nowMillis + stepDelayMillis(relearning.get(0));
        state.schedulerState = STATE_LEARNING;
        state.scheduledIntervalDays = 0;

        if (countsAsRealDue(context, state)) {
            state.realPassStreak = 0;
            state.realAgainStreak++;
            state.lastRealReviewDueAtMillis = context.item.dueAtMillis;
            if (state.realAgainStreak >= context.settings.realDueReviewsToMove) {
                state.rung = demoteRung(state.rung, context.item.hasSimilarKanji);
                state.realAgainStreak = 0;
                state.realPassStreak = 0;
            }
        }
    }

    private void applyReviewPass(ReviewContext context, ReviewState state, double multiplier, double difficultyDelta) {
        Fsrs5Engine engine = new Fsrs5Engine(null, context.parameters.targetRetention);
        int fsrsRating = Fsrs5Engine.ratingToInt(context.rating);
        state.difficulty = engine.updateDifficulty(state.difficulty, fsrsRating);
        double elapsedDays = Fsrs5Engine.elapsedDays(context.item.dueAtMillis, context.nowMillis);
        double retrievability = engine.retrievability(elapsedDays, state.stability);
        state.stability = engine.stabilityAfterRecall(state.stability, state.difficulty, retrievability, fsrsRating);
        long interval = engine.nextIntervalMillis(state.stability);
        state.scheduledIntervalDays = intervalDays(interval);
        state.due = context.nowMillis + interval;
        state.phase = Records.SchedulerPhase.REVIEW;
        state.schedulerState = STATE_REVIEW;
        state.stepIndex = 0;

        if (countsAsRealDue(context, state)) {
            state.realAgainStreak = 0;
            state.realPassStreak++;
            state.lastRealReviewDueAtMillis = context.item.dueAtMillis;
            if (state.realPassStreak >= context.settings.realDueReviewsToMove) {
                state.rung = promoteRung(state.rung, context.item.hasSimilarKanji);
                state.realPassStreak = 0;
                state.realAgainStreak = 0;
            }
        }
    }

    /**
     * True when the answer being graded is a real due review in the REVIEW
     * phase: the card's current FSRS due slot has already elapsed, and that
     * slot has not already been counted toward the ladder streak.
     * <p>
     * This method is only called from {@code applyReviewTransition} which is
     * dispatched exclusively for REVIEW-phase items, so the phase guard is
     * omitted.
     */
    private boolean countsAsRealDue(ReviewContext context, ReviewState state) {
        long currentDueSlot = context.item.dueAtMillis;
        if (currentDueSlot > context.nowMillis) {
            return false;
        }
        return state.lastRealReviewDueAtMillis == 0L
                || state.lastRealReviewDueAtMillis != currentDueSlot;
    }

    static Records.LadderRung promoteRung(Records.LadderRung current, boolean hasSimilarKanji) {
        Records.LadderRung[] order = Records.LadderRung.values();
        for (int i = current.ordinal() + 1; i < order.length; i++) {
            Records.LadderRung candidate = order[i];
            if (candidate == Records.LadderRung.SIMILAR_KANJI && !hasSimilarKanji) {
                continue;
            }
            return candidate;
        }
        return current;
    }

    static Records.LadderRung demoteRung(Records.LadderRung current, boolean hasSimilarKanji) {
        Records.LadderRung[] order = Records.LadderRung.values();
        for (int i = current.ordinal() - 1; i >= 0; i--) {
            Records.LadderRung candidate = order[i];
            if (candidate == Records.LadderRung.SIMILAR_KANJI && !hasSimilarKanji) {
                continue;
            }
            return candidate;
        }
        return current;
    }

    private void updateWritingLevel(ReviewContext context, ReviewState state) {
        if (context.rung != Records.LadderRung.WRITE_KANJI) {
            return;
        }
        if (context.failedWriting) {
            state.writingLevel = Math.max(0, state.writingLevel - 1);
        } else if (context.cleanWritingPass) {
            state.writingLevel = Math.min(3, state.writingLevel + 1);
        }
    }

    /**
     * Builds the updated study item after a review. The item-level stability
     * and difficulty fields mirror the active rung's TaskMemory values — they
     * are per-rung, not global. This is intentional: FSRS parameters are
     * rung-specific, and external code reading item.stability sees the most
     * recent rung's state for display and persistence purposes.
     */
    private Records.StudyItem updatedStudyItem(ReviewContext context, ReviewState state) {
        Records.TaskMemory updatedMemory = new Records.TaskMemory(
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
        Records.StudyItem base = context.item.copyBuilder()
                .state(state.schedulerState)
                .dueAtMillis(state.due)
                .stability(round(state.stability))
                .difficulty(round(state.difficulty))
                .totalReviews(state.total)
                .lapses(state.lapses)
                .learningStep(state.stepIndex)
                .writingLevel(state.writingLevel)
                .recognitionStage(rungToLegacyStage(state.rung))
                .writingRemediationPending(state.rung == Records.LadderRung.WRITE_KANJI)
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
        return base.withTaskMemory(context.reviewedTaskType, updatedMemory);
    }

    private int taskMemoryConsecutivePasses(ReviewContext context, ReviewState state) {
        return RATING_AGAIN.equals(context.rating) ? 0 : state.realPassStreak;
    }

    private long taskMemoryLastPassedDueAt(ReviewContext context, ReviewState state) {
        return RATING_AGAIN.equals(context.rating) ? 0L : state.lastRealReviewDueAtMillis;
    }

    public int dueCount(List<Records.StudyItem> items, long nowMillis) {
        int count = 0;
        for (Records.StudyItem item : items) {
            if (!STATE_RETIRED.equals(item.state) && item.dueAtMillis <= nowMillis) {
                count++;
            }
        }
        return count;
    }

    public int dueCount(List<Records.StudyItem> items, List<Records.DashboardRow> rows, long nowMillis) {
        int count = 0;
        for (Records.StudyItem item : activeQueueItems(items, rows, nowMillis, null)) {
            if (item.dueAtMillis <= nowMillis) {
                count++;
            }
        }
        return count;
    }

    public List<Records.StudyItem> activeQueueItems(
            List<Records.StudyItem> items,
            List<Records.DashboardRow> rows,
            long nowMillis,
            Set<String> allowedKanji
    ) {
        Set<String> currentRows = new HashSet<>();
        Set<String> currentFamilies = new HashSet<>();
        for (Records.DashboardRow row : rows) {
            currentRows.add(row.kanji);
            currentFamilies.add(rowFamilyKey(row));
        }
        Map<String, List<Records.StudyItem>> byFamily = new HashMap<>();
        for (Records.StudyItem item : items) {
            if (isActiveQueueCandidate(item, currentRows, currentFamilies, allowedKanji)) {
                addFamilyItem(byFamily, item);
            }
        }
        List<Records.StudyItem> out = new ArrayList<>();
        for (List<Records.StudyItem> family : byFamily.values()) {
            out.add(activeFamilyItem(family, nowMillis));
        }
        return out;
    }

    private boolean isActiveQueueCandidate(
            Records.StudyItem item,
            Set<String> currentRows,
            Set<String> currentFamilies,
            Set<String> allowedKanji
    ) {
        return !STATE_RETIRED.equals(item.state)
                && (item.suppressedByTaskType == null || item.suppressedByTaskType.isEmpty())
                && (allowedKanji == null || allowedKanji.contains(item.kanji))
                && hasCurrentQueueRow(item, currentRows, currentFamilies);
    }

    private boolean hasCurrentQueueRow(
            Records.StudyItem item,
            Set<String> currentRows,
            Set<String> currentFamilies
    ) {
        return currentFamilies.contains(familyKey(item))
                || (item.answerSignature.isEmpty() && currentRows.contains(item.kanji));
    }

    private void addFamilyItem(Map<String, List<Records.StudyItem>> byFamily, Records.StudyItem item) {
        String itemFamilyKey = familyKey(item);
        byFamily.computeIfAbsent(itemFamilyKey, ignored -> new ArrayList<>()).add(item);
    }

    /**
     * Creates a mutable token set from the given list of previously consumed
     * tokens. The returned set is <strong>not thread-safe</strong>; callers must
     * synchronize externally if the set will be shared across threads.
     */
    public Set<String> tokenSet(List<String> tokens) {
        return new HashSet<>(tokens);
    }

    private static final int MATURE_DAYS_THRESHOLD = 21;

    public List<Records.StudyItem> applySuppression(List<Records.StudyItem> items, Records.Settings settings) {
        Map<String, List<Records.StudyItem>> byKanji = new HashMap<>();
        for (Records.StudyItem item : items) {
            byKanji.computeIfAbsent(item.kanji, k -> new ArrayList<>()).add(item);
        }
        List<Records.StudyItem> result = new ArrayList<>(items.size());
        for (Records.StudyItem item : items) {
            List<Records.StudyItem> siblings = byKanji.get(item.kanji);
            Records.StudyItem updated = evaluateSuppression(item, siblings);
            result.add(updated);
        }
        return result;
    }

    private Records.StudyItem evaluateSuppression(Records.StudyItem item, List<Records.StudyItem> siblings) {
        if (STATE_RETIRED.equals(item.state)) {
            return item;
        }
        String dominator = findDominatingMatureSibling(item, siblings);
        boolean currentlySuppressed = item.suppressedByTaskType != null && !item.suppressedByTaskType.isEmpty();
        if (dominator != null && !currentlySuppressed) {
            return item.copyBuilder()
                    .suppressedByTaskType(dominator)
                    .suppressedAtMillis(System.currentTimeMillis())
                    .build();
        }
        if (dominator == null && currentlySuppressed) {
            return item.copyBuilder()
                    .suppressedByTaskType(null)
                    .suppressedAtMillis(0L)
                    .build();
        }
        return item;
    }

    private String findDominatingMatureSibling(Records.StudyItem item, List<Records.StudyItem> siblings) {
        Records.LadderRung itemRung = item.rung;
        for (Records.StudyItem sibling : siblings) {
            if (sibling == item || STATE_RETIRED.equals(sibling.state)) {
                continue;
            }
            if (!dominates(sibling.rung, itemRung)) {
                continue;
            }
            if (isMature(sibling)) {
                return sibling.rung.wireName();
            }
        }
        return null;
    }

    private static boolean dominates(Records.LadderRung higher, Records.LadderRung lower) {
        if (higher == Records.LadderRung.WORD_READING) {
            return lower == Records.LadderRung.FONT_MEANING || lower == Records.LadderRung.KANJI_MEANING;
        }
        if (higher == Records.LadderRung.FONT_MEANING) {
            return lower == Records.LadderRung.KANJI_MEANING;
        }
        return false;
    }

    private static boolean isMature(Records.StudyItem item) {
        return item.matureIntervalDays >= MATURE_DAYS_THRESHOLD
                && item.totalReviews > 0
                && item.phase == Records.SchedulerPhase.REVIEW;
    }

    public static final class ExtraNewCardsResult {
        public final List<Records.StudyItem> items;
        public final List<String> admittedKanji;
        public final int availableCount;
        public final int admittedCount;

        private ExtraNewCardsResult(
                List<Records.StudyItem> items,
                List<String> admittedKanji,
                int availableCount
        ) {
            this.items = Collections.unmodifiableList(new ArrayList<>(items));
            this.admittedKanji = Collections.unmodifiableList(new ArrayList<>(admittedKanji));
            this.availableCount = availableCount;
            this.admittedCount = admittedKanji.size();
        }

        public boolean admittedAny() {
            return admittedCount > 0;
        }
    }

    private static double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static long stepDelayMillis(int minutes) {
        return Math.max(1L, Math.max(1, minutes)) * MINUTE;
    }

    private static String rungTaskType(Records.LadderRung rung) {
        return rung.wireName();
    }

    private static int rungToLegacyStage(Records.LadderRung rung) {
        return switch (rung) {
            case TYPE_MEANING -> MIN_RECOGNITION_STAGE;
            case FONT_MEANING -> 1;
            case WORD_READING -> MAX_RECOGNITION_STAGE;
            default -> 0;
        };
    }

    private static Records.StudyItem activeFamilyItem(List<Records.StudyItem> family, long nowMillis) {
        Records.StudyItem best = null;
        for (Records.StudyItem item : family) {
            if (best == null || compareFamilyActivity(item, best, nowMillis) < 0) {
                best = item;
            }
        }
        return best;
    }

    private static int compareFamilyActivity(Records.StudyItem left, Records.StudyItem right, long nowMillis) {
        int rank = Integer.compare(-left.rung.ordinal(), -right.rung.ordinal());
        if (rank != 0) {
            return rank;
        }
        int due = Integer.compare(left.dueAtMillis <= nowMillis ? 0 : 1, right.dueAtMillis <= nowMillis ? 0 : 1);
        if (due != 0) {
            return due;
        }
        return Long.compare(left.dueAtMillis, right.dueAtMillis);
    }

    private static String familyKey(Records.StudyItem item) {
        return familyKey(item.kanji, item.answerSignature);
    }

    private static String rowFamilyKey(Records.DashboardRow row) {
        return familyKey(row.kanji, answerSignature(row));
    }

    private static String familyKey(String kanji, String answerSignature) {
        return kanji + "\u0000" + Objects.requireNonNullElse(answerSignature, "");
    }

    private static int intervalDays(long intervalMillis) {
        return Math.max(0, (int) Math.round((double) intervalMillis / DAY));
    }

    private static String answerSignature(Records.DashboardRow row) {
        Records.Example example = null;
        for (Records.Example candidate : row.examples) {
            if ("suspended".equals(candidate.sourceType)) {
                example = candidate;
                break;
            }
            if (example == null && "active".equals(candidate.sourceType)) {
                example = candidate;
            }
        }
        if (example == null && !row.examples.isEmpty()) {
            example = row.examples.get(0);
        }
        String expression = example == null ? "" : example.expression;
        String reading = example == null ? row.reading : example.reading;
        String meaning = example == null ? row.primaryMeaning : example.meaning;
        return normalizeSignature(row.kanji) + "|"
                + normalizeSignature(expression) + "|"
                + normalizeSignature(reading) + "|"
                + normalizeSignature(meaning);
    }

    private static String normalizeSignature(String value) {
        return MULTI_WHITESPACE.matcher(Objects.requireNonNullElse(value, "").trim()).replaceAll(" ");
    }

    private static final class SeedQueueLimits {
        final int newAdmissionLimit;
        final boolean allKanjiMode;

        SeedQueueLimits(int newAdmissionLimit, boolean allKanjiMode) {
            this.newAdmissionLimit = newAdmissionLimit;
            this.allKanjiMode = allKanjiMode;
        }

        int activeQueueCap(Records.Settings settings) {
            return allKanjiMode ? Integer.MAX_VALUE : settings.activeQueueCap;
        }

        int admissionLimit() {
            return allKanjiMode ? Integer.MAX_VALUE : Math.max(0, newAdmissionLimit);
        }
    }

    private static final class SeedQueueRequest {
        final List<Records.DashboardRow> allRows;
        final List<Records.DashboardRow> admissionRows;
        final List<Records.StudyItem> existing;
        final Records.Settings settings;
        final long nowMillis;
        final long startOfDayMillis;
        final SeedQueueLimits limits;

        SeedQueueRequest(
                List<Records.DashboardRow> allRows,
                List<Records.DashboardRow> admissionRows,
                List<Records.StudyItem> existing,
                Records.Settings settings,
                long nowMillis,
                long startOfDayMillis,
                SeedQueueLimits limits
        ) {
            this.allRows = allRows;
            this.admissionRows = admissionRows;
            this.existing = existing;
            this.settings = settings;
            this.nowMillis = nowMillis;
            this.startOfDayMillis = startOfDayMillis;
            this.limits = limits;
        }
    }

    private static final class SeedRowIndex {
        final Map<String, Records.DashboardRow> rowByFamily = new HashMap<>();
        final Map<String, List<Records.DashboardRow>> rowsByKanji = new HashMap<>();
    }

    private static final class SeedQueueState {
        final Map<String, Records.StudyItem> byFamily = new HashMap<>();
        final List<Records.StudyItem> items = new ArrayList<>();
        int activeCount;
        int newToday;

        void trackActiveItem(Records.StudyItem item, long startOfDayMillis) {
            if (STATE_RETIRED.equals(item.state)) {
                return;
            }
            activeCount++;
            if (item.createdAtMillis >= startOfDayMillis) {
                newToday++;
            }
        }

        boolean hasAdmissionRoom(SeedQueueRequest request) {
            return activeCount < request.limits.activeQueueCap(request.settings)
                    && newToday < request.limits.admissionLimit();
        }
    }

    private static final class ReviewContext {
        Records.StudyItem item;
        Records.ReviewRequest request;
        Records.SchedulerParameters parameters;
        Records.Settings settings;
        Records.LearningStepSettings learningSettings;
        Records.TaskMemory previousTaskMemory;
        Records.LadderRung rung;
        Records.SchedulerPhase phase;
        long nowMillis;
        String rating;
        String reviewedTaskType;
        boolean cleanWritingPass;
        boolean failedWriting;

        static ReviewContext from(
                Records.StudyItem item,
                Records.ReviewRequest request,
                Records.SchedulerParameters parameters,
                Records.Settings settings,
                Records.LearningStepSettings learningSettings,
                long nowMillis
        ) {
            ReviewContext context = new ReviewContext();
            context.item = item;
            context.request = request;
            context.parameters = parameters;
            context.settings = settings;
            context.learningSettings = learningSettings;
            context.nowMillis = nowMillis;
            context.rung = item.rung;
            context.phase = item.phase;
            context.reviewedTaskType = context.rung.wireName();
            context.previousTaskMemory = item.memoryForRung(context.rung);
            context.rating = resolveRating(request, context.rung);
            boolean writingRung = context.rung == Records.LadderRung.WRITE_KANJI;
            boolean writingReviewCanMoveHelp = writingRung && request.writingRequired && !request.manualOverride;
            context.cleanWritingPass = writingReviewCanMoveHelp
                    && request.writingPassed
                    && request.writingClean
                    && request.hintsUsed <= 0;
            context.failedWriting = writingReviewCanMoveHelp && !request.writingPassed;
            return context;
        }

        private static String resolveRating(Records.ReviewRequest request, Records.LadderRung rung) {
            if (rung == Records.LadderRung.WRITE_KANJI) {
                if (request.manualOverride) {
                    return RATING_HARD;
                }
                if (request.writingRequired && !request.writingPassed) {
                    return RATING_AGAIN;
                }
            } else if (request.writingRequired && !request.writingPassed && !request.manualOverride) {
                return RATING_AGAIN;
            }
            return normalizeRating(request.rating);
        }

        private static String normalizeRating(String rating) {
            if (rating == null) {
                return RATING_AGAIN;
            }
            return switch (rating) {
                case RATING_AGAIN, RATING_HARD, RATING_GOOD, RATING_EASY -> rating;
                default -> RATING_AGAIN;
            };
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
        Records.LadderRung rung;
        Records.SchedulerPhase phase;
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

    // -------- Non-public helpers kept package-private for testing. --------

    static List<Records.LadderRung> rungsForItem(Records.StudyItem item) {
        List<Records.LadderRung> out = new ArrayList<>();
        for (Records.LadderRung rung : Records.LadderRung.values()) {
            if (rung == Records.LadderRung.SIMILAR_KANJI && !item.hasSimilarKanji) {
                continue;
            }
            out.add(rung);
        }
        return Collections.unmodifiableList(out);
    }
}
