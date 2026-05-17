package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import android.os.SystemClock;

import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.domain.model.study.StudyRating;
import dev.bee.kanjianki.domain.scheduler.StudyProgressCalculator;
import dev.bee.kanjianki.domain.scheduler.StudyProgressPlan;
import dev.bee.kanjianki.domain.scheduler.StudyProgressSnapshot;
import dev.bee.kanjianki.domain.scheduler.StudyProgressState;
import dev.bee.kanjianki.domain.scheduler.StudyProgressUpdate;
import dev.bee.kanjianki.domain.scheduler.StudyReviewProgressOutcome;

final class StudySessionTracker {
    private static final StudyProgressCalculator KEY_CALCULATOR = new StudyProgressCalculator();
    private final StudyProgressCalculator progressCalculator = new StudyProgressCalculator();
    private StudyProgressState progressState = progressCalculator.reset();
    private ActiveStudyTask activeTask;

    int completedCount() {
        return progressState.getCompletedCount();
    }

    int targetCount() {
        return progressState.getTargetCount();
    }

    int movedForwardCount() {
        return progressSnapshot(false, false).getMovedForwardCount();
    }

    int missedCount() {
        return progressSnapshot(false, false).getMissedCount();
    }

    void resetProgress() {
        progressState = progressCalculator.reset();
    }

    void initializeTarget(RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        progressState = progressCalculator.initializeTarget(
                progressState,
                plan == null ? null : new StudyProgressPlan(plan.target, plan.remaining)
        );
    }

    void setTargetCount(int targetCount) {
        progressState = progressCalculator.setTargetCount(progressState, targetCount);
    }

    boolean includePendingTask(String key) {
        StudyProgressUpdate update = progressCalculator.includePendingTask(progressState, key);
        progressState = update.getState();
        return update.getAccepted();
    }

    boolean atHardCap(boolean continueAllKanjiSession) {
        return progressCalculator.atHardCap(progressState, continueAllKanjiSession);
    }

    void registerTaskShown(String key) {
        progressState = progressCalculator.registerTaskShown(progressState, key);
    }

    void markTaskCompleted(String key) {
        progressState = progressCalculator.markTaskCompleted(progressState, key);
    }

    StudyProgressSnapshot progressSnapshot(boolean activeTask, boolean continueAllKanjiSession) {
        return progressCalculator.snapshot(progressState, activeTask, continueAllKanjiSession);
    }

    static String sessionTaskKey(RecordsSchedulerModels.StudySession session) {
        if (session == null) {
            return "";
        }
        return KEY_CALCULATOR.sessionTaskKey(session.taskType, session.item.kanji, session.token);
    }

    static String similarRepairProgressKey(RecordsImportModels.SimilarKanjiWritingRepair repair) {
        return repair == null ? "" : KEY_CALCULATOR.similarRepairProgressKey(repair.id);
    }

    static String similarRepairStudyTaskKey(RecordsImportModels.SimilarKanjiWritingRepair repair) {
        if (repair == null) {
            return "";
        }
        return KEY_CALCULATOR.similarRepairStudyTaskKey(repair.id, repair.activeToken);
    }

    boolean hasActiveTask() {
        return activeTask != null;
    }

    void startActiveTask(String key, String kanji, String taskType, long startedAt, boolean resumeImmediately) {
        if (key == null || key.isEmpty()) {
            return;
        }
        if (activeTask != null && key.equals(activeTask.taskKey)) {
            return;
        }
        activeTask = new ActiveStudyTask(key, kanji, taskType, startedAt);
        if (resumeImmediately) {
            activeTask.resume(SystemClock.elapsedRealtime());
        }
    }

    void completeActiveTask(LocalStore store, String key, String outcome, long answeredAt, boolean countProgress) {
        if (activeTask == null || key == null || !key.equals(activeTask.taskKey)) {
            return;
        }
        activeTask.pause(SystemClock.elapsedRealtime());
        store.recordStudyTaskAnswered(
                activeTask.taskKey,
                activeTask.kanji,
                activeTask.taskType,
                activeTask.startedAtMillis,
                answeredAt,
                activeTask.activeElapsedMillis,
                outcome
        );
        if (countProgress) {
            markTaskCompleted(key);
        }
        activeTask = null;
    }

    void recordReviewOutcome(String kanji, String appliedRating, RecordsStudyModels.StudyItem before, RecordsStudyModels.StudyItem after) {
        progressState = progressCalculator.recordReviewOutcome(
                progressState,
                new StudyReviewProgressOutcome(
                        kanji,
                        rating(appliedRating),
                        before == null ? 0 : before.writingLevel,
                        after == null ? 0 : after.writingLevel,
                        before == null ? 0 : before.realPassStreak,
                        after == null ? 0 : after.realPassStreak
                )
        );
    }

    void recordRepairOutcome(String kanji, boolean passed) {
        progressState = progressCalculator.recordRepairOutcome(progressState, kanji, passed);
    }

    void pauseActiveTask() {
        if (activeTask != null) {
            activeTask.pause(SystemClock.elapsedRealtime());
        }
    }

    void resumeActiveTask() {
        if (activeTask != null) {
            activeTask.resume(SystemClock.elapsedRealtime());
        }
    }

    void abandonActiveTask() {
        activeTask = null;
    }

    private static StudyRating rating(String rating) {
        if (BridgeScheduler.RATING_HARD.equals(rating)) {
            return StudyRating.HARD;
        }
        if (BridgeScheduler.RATING_GOOD.equals(rating)) {
            return StudyRating.GOOD;
        }
        if (BridgeScheduler.RATING_EASY.equals(rating)) {
            return StudyRating.EASY;
        }
        return StudyRating.AGAIN;
    }

    static final class ActiveStudyTask {
        final String taskKey;
        final String kanji;
        final String taskType;
        final long startedAtMillis;
        long activeElapsedMillis;
        long visibleSinceElapsedMillis;

        ActiveStudyTask(String taskKey, String kanji, String taskType, long startedAtMillis) {
            this.taskKey = taskKey;
            this.kanji = kanji == null ? "" : kanji;
            this.taskType = taskType == null ? "" : taskType;
            this.startedAtMillis = Math.max(0L, startedAtMillis);
        }

        void pause(long nowElapsedMillis) {
            if (visibleSinceElapsedMillis <= 0L) {
                return;
            }
            activeElapsedMillis += Math.max(0L, nowElapsedMillis - visibleSinceElapsedMillis);
            visibleSinceElapsedMillis = 0L;
        }

        void resume(long nowElapsedMillis) {
            if (visibleSinceElapsedMillis <= 0L) {
                visibleSinceElapsedMillis = nowElapsedMillis;
            }
        }
    }
}
