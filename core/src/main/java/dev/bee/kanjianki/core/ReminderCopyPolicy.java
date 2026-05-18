package dev.bee.kanjianki.core;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class ReminderCopyPolicy {
    private ReminderCopyPolicy() {
    }

    public static ReminderCopy forPlan(AdaptiveLoadPlanner.PlanRequest request) {
        List<RecordsImportModels.DashboardRow> rows = request == null ? Collections.emptyList() : safeRows(request);
        if (rows.isEmpty()) {
            return new ReminderCopy("Sync Kani", "Sync AnkiDroid to find the kanji your reviews keep exposing.");
        }
        RecordsSchedulerModels.AdaptiveLoadPlan plan = new AdaptiveLoadPlanner().plan(request);
        return forCounts(plan.remaining, currentDueCount(rows, safeItems(request), request.nowMillis()));
    }

    public static ReminderCopy forCounts(int focusRemaining, int due) {
        if (focusRemaining > 0) {
            return new ReminderCopy(
                    "Kani focus is ready",
                    String.format(Locale.ROOT, "%d focus kanji %s left today. Draw one now.", focusRemaining, focusRemaining == 1 ? "is" : "are")
            );
        }
        if (due > 0) {
            return new ReminderCopy(
                    "Kani recovery is due",
                    String.format(Locale.ROOT, "%d problem kanji %s ready. Draw one now.", due, due == 1 ? "is" : "are")
            );
        }
        return new ReminderCopy("Check Kani", "Your queue can rest today. Open Kani if you want an extra problem kanji rep.");
    }

    private static int currentDueCount(
            List<RecordsImportModels.DashboardRow> rows,
            List<RecordsStudyModels.StudyItem> items,
            long now
    ) {
        return new BridgeScheduler().dueCount(items, rows, now);
    }

    private static List<RecordsImportModels.DashboardRow> safeRows(AdaptiveLoadPlanner.PlanRequest request) {
        return request.rows() == null ? Collections.emptyList() : request.rows();
    }

    private static List<RecordsStudyModels.StudyItem> safeItems(AdaptiveLoadPlanner.PlanRequest request) {
        return request.items() == null ? Collections.emptyList() : request.items();
    }

    public static final class ReminderCopy {
        public final String title;
        public final String message;

        public ReminderCopy(String title, String message) {
            this.title = title;
            this.message = message;
        }
    }
}
