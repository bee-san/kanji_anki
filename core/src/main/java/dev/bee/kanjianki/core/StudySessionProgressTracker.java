package dev.bee.kanjianki.core;

import java.util.HashSet;
import java.util.Set;

public final class StudySessionProgressTracker {
    private int completedCount;
    private int targetCount;
    private final Set<String> completedTaskKeys = new HashSet<>();
    private final Set<String> seenTaskKeys = new HashSet<>();
    private final Set<String> movedForwardKanji = new HashSet<>();
    private final Set<String> missedKanji = new HashSet<>();

    public int completedCount() {
        return completedCount;
    }

    public int targetCount() {
        return targetCount;
    }

    public int movedForwardCount() {
        return movedForwardKanji.size();
    }

    public int missedCount() {
        return missedKanji.size();
    }

    public void resetProgress() {
        completedCount = 0;
        targetCount = 0;
        completedTaskKeys.clear();
        seenTaskKeys.clear();
        movedForwardKanji.clear();
        missedKanji.clear();
    }

    public void initializeTarget(RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        if (targetCount <= 0 && plan != null) {
            targetCount = Math.max(0, plan.remaining > 0 ? plan.remaining : plan.target);
        }
    }

    public void setTargetCount(int targetCount) {
        this.targetCount = Math.max(0, targetCount);
    }

    public boolean includePendingTask(String key) {
        if (isEmpty(key) || seenTaskKeys.contains(key) || completedTaskKeys.contains(key)) {
            return false;
        }
        seenTaskKeys.add(key);
        targetCount++;
        return true;
    }

    public boolean atHardCap(boolean continueAllKanjiSession) {
        return !continueAllKanjiSession && targetCount > 0 && completedCount >= targetCount;
    }

    public TopBarProgress topBarProgress(boolean activeTask, boolean continueAllKanjiSession) {
        int completed = completedCount;
        int target = targetCount;
        if (activeTask && target <= completed && continueAllKanjiSession) {
            target = completed + 1;
        }
        if (activeTask) {
            target = Math.max(1, target);
        }
        int visibleCompleted = Math.max(0, Math.min(target, completed));
        float fraction = target <= 0 ? 0f : Math.max(0f, Math.min(1f, completed / (float) target));
        return new TopBarProgress(visibleCompleted, target, fraction);
    }

    public void registerTaskShown(String key) {
        if (isEmpty(key)) {
            return;
        }
        seenTaskKeys.add(key);
        if (targetCount <= 0) {
            targetCount = 1;
        }
    }

    public void markTaskCompleted(String key) {
        if (isEmpty(key)) {
            return;
        }
        registerTaskShown(key);
        if (completedTaskKeys.add(key)) {
            completedCount++;
            targetCount = Math.max(targetCount, completedCount);
        }
    }

    public static String sessionTaskKey(RecordsSchedulerModels.StudySession session) {
        if (session == null) {
            return "";
        }
        return "session:" + session.taskType + ":" + session.item.kanji + ":" + session.token;
    }

    public static String similarRepairProgressKey(RecordsImportModels.SimilarKanjiWritingRepair repair) {
        return repair == null ? "" : "repair:" + repair.id;
    }

    public static String similarRepairStudyTaskKey(RecordsImportModels.SimilarKanjiWritingRepair repair) {
        if (repair == null) {
            return "";
        }
        return similarRepairProgressKey(repair) + ":" + repair.activeToken;
    }

    public void recordReviewOutcome(
            String kanji,
            String appliedRating,
            RecordsStudyModels.StudyItem before,
            RecordsStudyModels.StudyItem after
    ) {
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

    public void recordRepairOutcome(String kanji, boolean passed) {
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

    private static boolean isEmpty(String key) {
        return key == null || key.isEmpty();
    }

    private static String safeKanji(String kanji) {
        return kanji == null ? "" : kanji.trim();
    }

    private static boolean locallyImproved(RecordsStudyModels.StudyItem before, RecordsStudyModels.StudyItem after) {
        if (before == null || after == null) {
            return false;
        }
        return after.writingLevel > before.writingLevel
                || after.realPassStreak > before.realPassStreak;
    }

    public static final class TopBarProgress {
        public final int completed;
        public final int target;
        public final float fraction;

        TopBarProgress(int completed, int target, float fraction) {
            this.completed = completed;
            this.target = target;
            this.fraction = fraction;
        }
    }
}
