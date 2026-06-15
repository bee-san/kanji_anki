package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ReminderCopyPolicyTest {
    @Test
    fun forPlanTreatsMissingRowsAsSyncWork() {
        val missingRequest = ReminderCopyPolicy.forPlan(null)
        val missingRows = ReminderCopyPolicy.forPlan(planRequest(
            null,
            emptyList(),
            AdaptiveLoadPlanner.DEFAULT_MAX_ITEMS,
            utc(2026, Calendar.MAY, 15, 8, 0)
        ))

        assertEquals("Sync Kani", missingRequest.title)
        assertEquals("Sync Kani", missingRows.title)
    }

    @Test
    fun japaneseLocaleLocalizesReminderAndChannelCopy() {
        withLocale(Locale.JAPANESE) {
            val now = utc(2026, Calendar.MAY, 15, 8, 0)

            val sync = ReminderCopyPolicy.forPlan(null)
            assertEquals("Kaniを同期", sync.title)
            assertEquals("Kaniを開いて同期してください。", sync.message)

            val focus = ReminderCopyPolicy.forCounts(2, 0)
            assertEquals("Kaniフォーカスの準備ができました", focus.title)
            assertEquals("2件のフォーカス漢字が待っています。Kaniを開いて復習しましょう。", focus.message)

            val review = ReminderCopyPolicy.forCounts(0, 2)
            assertEquals("さらに漢字を復習しましょう", review.title)
            assertEquals("2件の漢字が今復習できます。Kaniを開いて復習しましょう。", review.message)

            val rest = ReminderCopyPolicy.forCounts(0, 0)
            assertEquals("Kaniは今日の分まで完了しています", rest.title)
            assertEquals("今は復習対象の漢字はありません。必要ならKaniで追加練習しましょう。", rest.message)

            val caughtUp = ReminderCopyPolicy.forPlan(planRequest(
                listOf(row("裂", 80)),
                listOf(RecordsStudyModels.StudyItem("裂", "review", now + 7_200_000L, 1.0, 5.0, 2, 0, 2, 1, null, now)),
                1,
                now,
                studiedToday = setOf("裂"),
            ))
            assertEquals("Kaniは今日の分まで完了しています", caughtUp.title)
            assertEquals("今日はもう学習済みです。もっと漢字が戻ってきたらKaniを開いてください。", caughtUp.message)

            val streak = ReminderCopyPolicy.forPlan(planRequest(
                listOf(row("裂", 80)),
                emptyList(),
                1,
                now,
                currentStreakDays = 3,
            ))
            assertEquals("Kaniの連続記録リマインダー", streak.title)
            assertEquals("1件の漢字が待っています。Kaniを開いて3日連続の記録を続けましょう。", streak.message)

            assertEquals("Kaniの学習リマインダー", ReminderCopyPolicy.notificationChannelName())
            assertEquals("Kaniのやさしい学習リマインダー。", ReminderCopyPolicy.notificationChannelDescription())
        }
    }

    @Test
    fun forPlanAsksForSyncBeforeAnyActiveKanjiExist() {
        val copy = ReminderCopyPolicy.forPlan(planRequest(
            emptyList(),
            emptyList(),
            AdaptiveLoadPlanner.DEFAULT_MAX_ITEMS,
            utc(2026, Calendar.MAY, 15, 8, 0)
        ))

        assertEquals("Sync Kani", copy.title)
        assertEquals("Open Kani and tap Sync.", copy.message)
    }

    @Test
    fun forPlanPlansActiveRowsBeforeFormattingMessage() {
        val now = utc(2026, Calendar.MAY, 15, 8, 0)

        val copy = ReminderCopyPolicy.forPlan(planRequest(
            listOf(row("裂", 80), row("提", 70)),
            listOf(
                RecordsStudyModels.StudyItem("裂", "review", now + 3_600_000L, 1.0, 5.0, 2, 0, 2, 1, null, now),
                RecordsStudyModels.StudyItem("提", "review", now + 7_200_000L, 1.0, 5.0, 2, 0, 2, 1, null, now),
            ),
            2,
            now,
            studiedToday = setOf("裂"),
        ))

        assertEquals("Kani focus is ready", copy.title)
        assertEquals("1 focus kanji is waiting. Open Kani to review it.", copy.message)
    }

    @Test
    fun forPlanTreatsMissingStudyItemsAsEmptyQueue() {
        val now = utc(2026, Calendar.MAY, 15, 8, 0)

        val copy = ReminderCopyPolicy.forPlan(planRequest(
            listOf(row("裂", 80)),
            null,
            1,
            now,
        ))

        assertEquals("Kani streak reminder", copy.title)
        assertEquals("1 kanji is waiting. Open Kani to keep your streak alive.", copy.message)
    }

    @Test
    fun forPlanUsesReviewReminderAfterStudy() {
        val now = utc(2026, Calendar.MAY, 15, 8, 0)

        val copy = ReminderCopyPolicy.forPlan(planRequest(
            listOf(row("裂", 80)),
            listOf(RecordsStudyModels.StudyItem("裂", "review", now - 1L, 1.0, 5.0, 2, 0, 2, 1, null, now)),
            1,
            now,
            studiedToday = setOf("裂"),
        ))

        assertEquals("You have more Kanji to review", copy.title)
        assertEquals("1 kanji is ready now. Open Kani to review it.", copy.message)
    }

    @Test
    fun forPlanReturnsCaughtUpCopyWhenNothingElseIsDueAfterStudy() {
        val now = utc(2026, Calendar.MAY, 15, 8, 0)

        val copy = ReminderCopyPolicy.forPlan(planRequest(
            listOf(row("裂", 80)),
            listOf(RecordsStudyModels.StudyItem("裂", "review", now + 7_200_000L, 1.0, 5.0, 2, 0, 2, 1, null, now)),
            1,
            now,
            studiedToday = setOf("裂"),
        ))

        assertEquals("Kani is caught up", copy.title)
        assertEquals("You've already studied today. Open Kani later when more kanji come back.", copy.message)
    }

    @Test
    fun forPlanUsesCurrentStreakLabelWhenWaitingForMoreKanji() {
        val now = utc(2026, Calendar.MAY, 15, 8, 0)

        val copy = ReminderCopyPolicy.forPlan(planRequest(
            listOf(row("裂", 80)),
            emptyList(),
            1,
            now,
            currentStreakDays = 3,
        ))

        assertEquals("Kani streak reminder", copy.title)
        assertEquals("1 kanji is waiting. Open Kani to keep your 3-day streak alive.", copy.message)
    }

    @Test
    fun streakCopyOmitsWaitingTextWhenNothingIsWaiting() {
        val method = ReminderCopyPolicy::class.java.getDeclaredMethod(
            "streakCopy",
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!,
        )
        method.isAccessible = true

        val copy = method.invoke(ReminderCopyPolicy, 0, 3) as ReminderCopyPolicy.ReminderCopy

        assertEquals("Kani streak reminder", copy.title)
        assertEquals("Open Kani to keep your 3-day streak alive.", copy.message)
    }

    @Test
    fun forCountsFormatsFocusRecoveryAndRestMessages() {
        val oneFocus = ReminderCopyPolicy.forCounts(1, 4)
        val manyFocus = ReminderCopyPolicy.forCounts(3, 4)
        val oneDue = ReminderCopyPolicy.forCounts(0, 1)
        val manyDue = ReminderCopyPolicy.forCounts(0, 2)
        val rest = ReminderCopyPolicy.forCounts(0, 0)

        assertEquals("Kani focus is ready", oneFocus.title)
        assertEquals("1 focus kanji is waiting. Open Kani to review it.", oneFocus.message)
        assertEquals("3 focus kanji are waiting. Open Kani to review them.", manyFocus.message)
        assertEquals("Kani recovery is due", oneDue.title)
        assertEquals("1 problem kanji is due. Open Kani to review it now.", oneDue.message)
        assertEquals("2 problem kanji are due. Open Kani to review them now.", manyDue.message)
        assertEquals("Kani is caught up", rest.title)
        assertEquals("No problem kanji are due. Open Kani for extra practice if you want.", rest.message)
    }

    @Test
    fun reviewCopyFormatsReviewBatchCount() {
        val one = ReminderCopyPolicy.reviewCopy(1)
        val many = ReminderCopyPolicy.reviewCopy(3)

        assertEquals("You have more Kanji to review", one.title)
        assertEquals("1 kanji is ready now. Open Kani to review it.", one.message)
        assertEquals("You have more Kanji to review", many.title)
        assertEquals("3 kanji are ready now. Open Kani to review them.", many.message)
    }

    private fun utc(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.set(year, month, day, hour, minute, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private fun planRequest(
        rows: List<RecordsImportModels.DashboardRow>?,
        items: List<RecordsStudyModels.StudyItem>?,
        maxItems: Int,
        now: Long,
        studiedToday: Set<String> = emptySet(),
        currentStreakDays: Int = 0,
    ): AdaptiveLoadPlanner.PlanRequest {
        return AdaptiveLoadPlanner.PlanRequest.builder(
            rows,
            items,
            RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
            currentStreakDays,
            studiedToday,
            AdaptiveLoadPlanner.WorkloadPolicy.fromSettings(
                AdaptiveLoadPlanner.DEFAULT_WORKLOAD_PERCENT,
                AdaptiveLoadPlanner.DEFAULT_WORKLOAD_MODE,
                maxItems
            ),
            now,
        ).build()
    }

    private fun row(kanji: String, score: Int): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji,
            900,
            "meaning",
            "reading",
            "search",
            score,
            "reason",
            "reason text",
            1,
            if (score > 15) 1 else 0,
            0,
            emptyList<RecordsImportModels.Example>()
        )
    }

    private fun withLocale(locale: Locale, block: () -> Unit) {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(locale)
            block()
        } finally {
            Locale.setDefault(original)
        }
    }
}
