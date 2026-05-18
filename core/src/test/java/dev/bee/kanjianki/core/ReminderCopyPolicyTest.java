package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public final class ReminderCopyPolicyTest {
    @Test
    public void forPlanTreatsMissingRowsAsSyncWork() {
        ReminderCopyPolicy.ReminderCopy missingRequest = ReminderCopyPolicy.forPlan(null);
        ReminderCopyPolicy.ReminderCopy missingRows = ReminderCopyPolicy.forPlan(planRequest(
                null,
                Collections.emptyList(),
                AdaptiveLoadPlanner.DEFAULT_MAX_ITEMS,
                utc(2026, Calendar.MAY, 15, 8, 0)));

        assertEquals("Sync Kani", missingRequest.title);
        assertEquals("Sync Kani", missingRows.title);
    }

    @Test
    public void forPlanAsksForSyncBeforeAnyActiveKanjiExist() {
        ReminderCopyPolicy.ReminderCopy copy = ReminderCopyPolicy.forPlan(planRequest(
                Collections.emptyList(),
                Collections.emptyList(),
                AdaptiveLoadPlanner.DEFAULT_MAX_ITEMS,
                utc(2026, Calendar.MAY, 15, 8, 0)));

        assertEquals("Sync Kani", copy.title);
        assertEquals("Sync AnkiDroid to find the kanji your reviews keep exposing.", copy.message);
    }

    @Test
    public void forPlanPlansActiveRowsBeforeFormattingMessage() {
        long now = utc(2026, Calendar.MAY, 15, 8, 0);

        ReminderCopyPolicy.ReminderCopy copy = ReminderCopyPolicy.forPlan(planRequest(
                Collections.singletonList(row("裂", 80)),
                Collections.singletonList(new RecordsStudyModels.StudyItem("裂", "review", now - 1L, 1.0, 5.0, 2, 0, 2, 1, null, now)),
                1,
                now));

        assertEquals("Kani focus is ready", copy.title);
        assertEquals("1 focus kanji is left today. Draw one now.", copy.message);
    }

    @Test
    public void forPlanTreatsMissingStudyItemsAsEmptyQueue() {
        long now = utc(2026, Calendar.MAY, 15, 8, 0);

        ReminderCopyPolicy.ReminderCopy copy = ReminderCopyPolicy.forPlan(planRequest(
                Collections.singletonList(row("裂", 80)),
                null,
                1,
                now));

        assertEquals("Kani focus is ready", copy.title);
        assertEquals("1 focus kanji is left today. Draw one now.", copy.message);
    }

    @Test
    public void forCountsFormatsFocusRecoveryAndRestMessages() {
        ReminderCopyPolicy.ReminderCopy oneFocus = ReminderCopyPolicy.forCounts(1, 4);
        ReminderCopyPolicy.ReminderCopy manyFocus = ReminderCopyPolicy.forCounts(3, 4);
        ReminderCopyPolicy.ReminderCopy oneDue = ReminderCopyPolicy.forCounts(0, 1);
        ReminderCopyPolicy.ReminderCopy manyDue = ReminderCopyPolicy.forCounts(0, 2);
        ReminderCopyPolicy.ReminderCopy rest = ReminderCopyPolicy.forCounts(0, 0);

        assertEquals("Kani focus is ready", oneFocus.title);
        assertEquals("1 focus kanji is left today. Draw one now.", oneFocus.message);
        assertEquals("3 focus kanji are left today. Draw one now.", manyFocus.message);
        assertEquals("Kani recovery is due", oneDue.title);
        assertEquals("1 problem kanji is ready. Draw one now.", oneDue.message);
        assertEquals("2 problem kanji are ready. Draw one now.", manyDue.message);
        assertEquals("Check Kani", rest.title);
        assertEquals("Your queue can rest today. Open Kani if you want an extra problem kanji rep.", rest.message);
    }

    private static long utc(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
        calendar.set(year, month, day, hour, minute, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static AdaptiveLoadPlanner.PlanRequest planRequest(
            List<RecordsImportModels.DashboardRow> rows,
            List<RecordsStudyModels.StudyItem> items,
            int maxItems,
            long now
    ) {
        return AdaptiveLoadPlanner.PlanRequest.builder(
                        rows,
                        items,
                        new RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                        0,
                        Collections.emptySet(),
                        AdaptiveLoadPlanner.WorkloadPolicy.fromSettings(
                                AdaptiveLoadPlanner.DEFAULT_WORKLOAD_PERCENT,
                                AdaptiveLoadPlanner.DEFAULT_WORKLOAD_MODE,
                                maxItems),
                        now)
                .build();
    }

    private static RecordsImportModels.DashboardRow row(String kanji, int score) {
        return new RecordsImportModels.DashboardRow(
                kanji,
                900,
                "meaning",
                "reading",
                "search",
                score,
                "reason",
                "reason text",
                1,
                score > 15 ? 1 : 0,
                0,
                Collections.emptyList()
        );
    }
}
