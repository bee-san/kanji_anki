package dev.bee.kanjianki.core;

import java.util.Locale;
import java.util.Objects;

public final class HomeTextCopy {
    private HomeTextCopy() {
    }

    public static String sentenceCase(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + value.substring(1);
    }

    public static String focusHeadline(RecordsSchedulerModels.AdaptiveLoadPlan plan) {
        if (plan == null || plan.target <= 0) {
            return "Waiting";
        }
        if (plan.allKanjiMode) {
            return "All current";
        }
        return plan.remaining + " items left / " + plan.target;
    }

    public static String homeSyncValue(Long finishedAtMillis) {
        if (finishedAtMillis == null) {
            return "Never synced";
        }
        return sentenceCase(DateTextPolicy.humanSyncTime(finishedAtMillis));
    }

    public static String recentMistakeTitle(String rowMeaning) {
        if (rowMeaning == null || rowMeaning.isEmpty()) {
            return "Recent review miss";
        }
        return rowMeaning;
    }

    public static String recentMistakeSubtitle(String rating, String dateText) {
        String safeRating = rating == null ? "" : rating;
        String safeDate = dateText == null ? "" : dateText;
        return "Rated " + safeRating + " on " + safeDate;
    }

    public static String streakHeadline(int currentDays) {
        if (currentDays <= 0) {
            return "No streak yet";
        }
        return currentDays + "-day streak";
    }

    public static String streakMetricBody(boolean studiedToday, int bestDays) {
        if (studiedToday) {
            return bestDays > 0 ? "Best: " + streakDayCount(bestDays) : "Done today";
        }
        return "Not done today";
    }

    public static String streakDayCount(int days) {
        return days + " " + (days == 1 ? "day" : "days");
    }

    public static String reviewToast(boolean duplicate, String appliedRating, int currentStreakDays) {
        if (duplicate) {
            return "Already saved.";
        }
        String streakText = currentStreakDays <= 0 ? "" : " " + streakHeadline(currentStreakDays) + ".";
        if (StudyRatings.AGAIN.equals(appliedRating)) {
            return "Saved. This kanji will come back soon." + streakText;
        }
        return "Saved." + streakText;
    }

    public static String browseResultHeading(int size) {
        if (size <= 0) {
            return "No matches";
        }
        if (size >= 300) {
            return "Showing first 300 matches";
        }
        return StudyTextCopy.countText(size, "kanji", "kanji");
    }

    public static String detailDisplayKanji(
            String fallback,
            RecordsImportModels.DashboardRow row,
            RecordsImportModels.KanjiInventoryItem inventory
    ) {
        if (row != null) {
            return row.kanji;
        }
        return inventory == null ? fallback : inventory.kanji;
    }

    public static String inventoryTitle(RecordsImportModels.KanjiInventoryItem inventory) {
        if (inventory == null || inventory.primaryMeaning.isEmpty()) {
            return "Historical recovery";
        }
        return inventory.primaryMeaning;
    }

    public static String detailBrowserSearch(
            RecordsImportModels.DashboardRow row,
            RecordsImportModels.KanjiInventoryItem inventory
    ) {
        if (inventory != null && !inventory.browserSearch.isEmpty()) {
            return inventory.browserSearch;
        }
        if (row != null && !row.browserSearch.isEmpty()) {
            return row.browserSearch;
        }
        return "";
    }

    public static String matureSupportTargetText(int matureSupportCount, int target) {
        return "Mature support " + matureSupportCount + " / target " + target;
    }

    public static String timelineEmptyText() {
        return "Timeline will fill in after the next sync or review.";
    }

    public static String noActiveEvidenceText() {
        return "No active Anki evidence in the latest local sync.";
    }

    public static String exampleSourceLabel(RecordsImportModels.Example example) {
        return Objects.requireNonNull(example, "example").sourceType.toUpperCase(Locale.ROOT);
    }

    public static String exampleExpressionLine(RecordsImportModels.Example example) {
        RecordsImportModels.Example safeExample = Objects.requireNonNull(example, "example");
        if (safeExample.reading.isEmpty()) {
            return safeExample.expression;
        }
        return safeExample.expression + "  " + safeExample.reading;
    }

    public static String exampleMeaningLine(RecordsImportModels.Example example) {
        RecordsImportModels.Example safeExample = Objects.requireNonNull(example, "example");
        if (safeExample.meaning.isEmpty()) {
            return "";
        }
        return StudyTextCopy.cleanLearnerText(safeExample.meaning, safeExample.meaning, 120);
    }
}
