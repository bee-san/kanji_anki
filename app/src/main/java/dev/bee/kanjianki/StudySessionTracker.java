package dev.bee.kanjianki;

import android.os.SystemClock;

import dev.bee.kanjianki.core.BridgeScheduler;
import dev.bee.kanjianki.core.Records;
import dev.bee.kanjianki.data.LocalStore;

import java.util.HashSet;
import java.util.Set;

final class StudySessionTracker {
    private int completedCount;
    private int targetCount;
    private ActiveStudyTask activeTask;
    private final Set<String> completedTaskKeys = new HashSet<>();
    private final Set<String> seenTaskKeys = new HashSet<>();
    private final Set<String> movedForwardKanji = new HashSet<>();
    private final Set<String> missedKanji = new HashSet<>();

    int completedCount() {
        return completedCount;
    }

    int targetCount() {
        return targetCount;
    }

    int movedForwardCount() {
        return movedForwardKanji.size();
    }

    int missedCount() {
        return missedKanji.size();
    }

    void resetProgress() {
        completedCount = 0;
        targetCount = 0;
        completedTaskKeys.clear();
        seenTaskKeys.clear();
        movedForwardKanji.clear();
        missedKanji.clear();
    }

    void initializeTarget(Records.AdaptiveLoadPlan plan) {
        if (targetCount <= 0 && plan != null) {
            targetCount = Math.max(0, plan.remaining > 0 ? plan.remaining : plan.target);
        }
    }

    void setTargetCount(int targetCount) {
        this.targetCount = Math.max(0, targetCount);
    }

    boolean includePendingTask(String key) {
        if (isEmpty(key) || seenTaskKeys.contains(key) || completedTaskKeys.contains(key)) {
            return false;
        }
        seenTaskKeys.add(key);
        targetCount++;
        return true;
    }

    boolean atHardCap(boolean continueAllKanjiSession) {
        return !continueAllKanjiSession && targetCount > 0 && completedCount >= targetCount;
    }

    void registerTaskShown(String key) {
        if (isEmpty(key)) {
            return;
        }
        seenTaskKeys.add(key);
        if (targetCount <= 0) {
            targetCount = 1;
        }
    }

    void markTaskCompleted(String key) {
        if (isEmpty(key)) {
            return;
        }
        registerTaskShown(key);
        if (completedTaskKeys.add(key)) {
            completedCount++;
            targetCount = Math.max(targetCount, completedCount);
        }
    }

    static String sessionTaskKey(Records.StudySession session) {
        if (session == null) {
            return "";
        }
        return "session:" + session.taskType + ":" + session.item.kanji + ":" + session.token;
    }

    static String similarRepairProgressKey(Records.SimilarKanjiWritingRepair repair) {
        return repair == null ? "" : "repair:" + repair.id;
    }

    static String similarRepairStudyTaskKey(Records.SimilarKanjiWritingRepair repair) {
        if (repair == null) {
            return "";
        }
        return similarRepairProgressKey(repair) + ":" + repair.activeToken;
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

    void recordReviewOutcome(String kanji, String appliedRating, Records.StudyItem before, Records.StudyItem after) {
        String safeKanji = safeKanji(kanji);
        if (safeKanji.isEmpty()) {
            return;
        }
        boolean moved = !BridgeScheduler.RATING_AGAIN.equals(appliedRating) || locallyImproved(before, after);
        if (moved) {
            movedForwardKanji.add(safeKanji);
            missedKanji.remove(safeKanji);
        } else {
            missedKanji.add(safeKanji);
        }
    }

    void recordRepairOutcome(String kanji, boolean passed) {
        String safeKanji = safeKanji(kanji);
        if (safeKanji.isEmpty()) {
            return;
        }
        if (passed) {
            movedForwardKanji.add(safeKanji);
            missedKanji.remove(safeKanji);
        } else if (!movedForwardKanji.contains(safeKanji)) {
            missedKanji.add(safeKanji);
        }
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

    private static String safeKanji(String kanji) {
        return kanji == null ? "" : kanji.trim();
    }

    private static boolean locallyImproved(Records.StudyItem before, Records.StudyItem after) {
        if (before == null || after == null) {
            return false;
        }
        return after.writingLevel > before.writingLevel
                || after.realPassStreak > before.realPassStreak;
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
