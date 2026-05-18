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

    public static String appTitle() {
        return "Kani";
    }

    public static String appSubtitle() {
        return "Your AnkiDroid companion app to cure kanji blindness";
    }

    public static String syncAnkiDroidLabel() {
        return "Sync AnkiDroid";
    }

    public static String focusQueueTitle() {
        return "Focus queue";
    }

    public static String viewAllLabel() {
        return "View all";
    }

    public static String noKanjiQueuedTitle() {
        return "No kanji queued yet";
    }

    public static String homeNoKanjiQueuedBody() {
        return "After the first sync, this screen shows the kanji that need focused recall and writing practice.";
    }

    public static String focusQueueNoKanjiQueuedBody() {
        return "Sync AnkiDroid first to build a focus queue.";
    }

    public static String syncMetricLabel() {
        return "Sync";
    }

    public static String syncMetricStatus(boolean upToDate) {
        return upToDate ? "Up to date" : "Tap to sync";
    }

    public static String streakMetricLabel() {
        return "Streak";
    }

    public static String focusMetricLabel() {
        return "Focus";
    }

    public static String studySupportText() {
        return "Start focused practice";
    }

    public static String browseActionLabel() {
        return "Browse Kanji";
    }

    public static String recentMistakesTitle() {
        return "Recent mistakes";
    }

    public static String statsActionLabel() {
        return "Stats";
    }

    public static String gamesActionLabel() {
        return "Games";
    }

    public static String homeLabel() {
        return "Home";
    }

    public static String noRecentMistakesTitle() {
        return "No recent mistakes yet";
    }

    public static String noRecentMistakesBody() {
        return "Missed and hard reviews will show here after you study.";
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

    public static String browseTitle() {
        return "Browse Kanji";
    }

    public static String browseBody() {
        return "Local kanji from synced Kani data and study history.";
    }

    public static String browseSearchHint() {
        return "Search kanji, meaning, reading, or examples";
    }

    public static String browseSearchButtonLabel() {
        return "Search";
    }

    public static String browseEmptyTitle() {
        return "No local kanji found";
    }

    public static String browseEmptyBody() {
        return "Sync AnkiDroid first, or try a different search.";
    }

    public static String kanjiNotFoundTitle() {
        return "Kanji not found";
    }

    public static String kanjiNotFoundBody() {
        return "This row may have disappeared after a sync.";
    }

    public static String browseItemMeaning(RecordsImportModels.KanjiInventoryItem item) {
        RecordsImportModels.KanjiInventoryItem safeItem = Objects.requireNonNull(item, "item");
        return safeItem.primaryMeaning.isEmpty() ? "Meaning not stored yet" : safeItem.primaryMeaning;
    }

    public static String browseInventorySummary(int sourceCount, int exampleCount) {
        return StudyTextCopy.countText(sourceCount, "local source", "local sources")
                + " · "
                + StudyTextCopy.countText(exampleCount, "example", "examples");
    }

    public static String suspendedChipLabel() {
        return "SUSPENDED";
    }

    public static String detailReasonTitle() {
        return "Why it is here";
    }

    public static String historicalReasonText() {
        return "This kanji is no longer in the active Anki evidence set, but Kani kept its local recovery history.";
    }

    public static String activeReasonText(RecordsImportModels.DashboardRow row) {
        RecordsImportModels.DashboardRow safeRow = Objects.requireNonNull(row, "row");
        return safeRow.reasonText.isEmpty() ? "Current local practice evidence from AnkiDroid." : safeRow.reasonText;
    }

    public static String ankiBrowserLine(String browserSearch) {
        return "Anki browser: " + String.valueOf(browserSearch);
    }

    public static String reviewNowLabel() {
        return "Review this now";
    }

    public static String copyAnkiSearchLabel() {
        return "Copy Anki search";
    }

    public static String ankiSearchClipLabel() {
        return "Anki search";
    }

    public static String ankiSearchCopiedToast() {
        return "Search copied";
    }

    public static String localSuspendButtonLabel(boolean currentlySuspended) {
        return currentlySuspended ? "Unsuspend locally" : "Suspend locally";
    }

    public static String localSuspendToast(boolean wasSuspended) {
        return wasSuspended ? "Kanji unsuspended." : "Kanji suspended locally.";
    }

    public static String examplesTitle() {
        return "Examples";
    }

    public static String localInventoryTitle() {
        return "Local inventory";
    }

    public static String localInventorySummary(int sourceCount, int exampleCount) {
        return StudyTextCopy.countText(sourceCount, "source note/card", "source notes/cards")
                + " · "
                + StudyTextCopy.countText(exampleCount, "stored example", "stored examples");
    }

    public static String localInventorySearchLine(String browserSearch) {
        return "Search: " + String.valueOf(browserSearch);
    }

    public static String localInventoryLastSeenLine(long lastSeenAtMillis) {
        return "Last seen locally " + DateTextPolicy.shortDateTime(lastSeenAtMillis);
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

    public static String recoveryTimelineTitle() {
        return "Recovery timeline";
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
