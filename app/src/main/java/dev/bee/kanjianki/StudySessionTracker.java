package dev.bee.kanjianki;

import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSchedulerModels;
import dev.bee.kanjianki.core.RecordsStudyModels;
import dev.bee.kanjianki.core.StudySessionProgressTracker;
import dev.bee.kanjianki.core.StudyTaskTimingPolicy;
import android.os.SystemClock;

import dev.bee.kanjianki.data.LocalStore;

final class StudySessionTracker {
    private ActiveStudyTask activeTask;
    private final StudySessionProgressTracker progressTracker = new StudySessionProgressTracker();

    int completedCount() {
        return progressTracker.completedCount();
    }

    int targetCount() {
        return progressTracker.targetCount();
    }

    int movedForwardCount() {
        return progressTracker.movedForwardCount();
    }

    int missedCount() {
        return progressTracker.missedCount();
    }

    void resetProgress() {
        progressTracker.resetProgress();
    }

    void initializeTarget(RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        progressTracker.initializeTarget(plan);
    }

    void setTargetCount(int targetCount) {
        progressTracker.setTargetCount(targetCount);
    }

    boolean includePendingTask(String key) {
        return progressTracker.includePendingTask(key);
    }

    boolean atHardCap(boolean continueAllKanjiSession) {
        return progressTracker.atHardCap(continueAllKanjiSession);
    }

    StudySessionProgressTracker.TopBarProgress topBarProgress(boolean activeTask, boolean continueAllKanjiSession) {
        return progressTracker.topBarProgress(activeTask, continueAllKanjiSession);
    }

    void registerTaskShown(String key) {
        progressTracker.registerTaskShown(key);
    }

    void markTaskCompleted(String key) {
        progressTracker.markTaskCompleted(key);
    }

    static String sessionTaskKey(RecordsSchedulerModels.StudySession session) {
        return StudySessionProgressTracker.sessionTaskKey(session);
    }

    static String similarRepairProgressKey(RecordsImportModels.SimilarKanjiWritingRepair repair) {
        return StudySessionProgressTracker.similarRepairProgressKey(repair);
    }

    static String similarRepairStudyTaskKey(RecordsImportModels.SimilarKanjiWritingRepair repair) {
        return StudySessionProgressTracker.similarRepairStudyTaskKey(repair);
    }

    boolean hasActiveTask() {
        return activeTask != null;
    }

    void startActiveTask(String key, String kanji, String taskType, long startedAt, boolean resumeImmediately) {
        if (isEmpty(key)) {
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
        progressTracker.recordReviewOutcome(kanji, appliedRating, before, after);
    }

    void recordRepairOutcome(String kanji, boolean passed) {
        progressTracker.recordRepairOutcome(kanji, passed);
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

    private static boolean isEmpty(String key) {
        return key == null || key.isEmpty();
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
            activeElapsedMillis = StudyTaskTimingPolicy.elapsedAfterPause(
                    activeElapsedMillis,
                    visibleSinceElapsedMillis,
                    nowElapsedMillis
            );
            visibleSinceElapsedMillis = 0L;
        }

        void resume(long nowElapsedMillis) {
            visibleSinceElapsedMillis = StudyTaskTimingPolicy.visibleSinceAfterResume(
                    visibleSinceElapsedMillis,
                    nowElapsedMillis
            );
        }
    }
}
