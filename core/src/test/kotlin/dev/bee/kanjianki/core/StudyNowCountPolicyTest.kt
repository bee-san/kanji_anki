package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class StudyNowCountPolicyTest {
    private val now = 1_725_000_000_000L
    private val day = 86_400_000L
    private val ladder = RecordsBase.StudyLadderSettings.defaults()

    @Test
    fun additionalRouteTasksAreDeduplicatedAndBlankSafe() {
        assertEquals(
            4,
            StudyNowCountPolicy.includingAdditionalTaskKeys(
                2,
                listOf("repair:1", "", "repair:1", null, "repair:2"),
            ),
        )
    }

    @Test
    fun freshDayFocusWithFourFutureReviewsHasNoStudyNowTasks() {
        val kanji = listOf("裂", "復", "習", "待")
        val rows = kanji.map(::repairRow)
        val items = rows.map { row -> reviewItem(row, dueAtMillis = now + day) }
        val plan = focusPlan(kanji, remaining = 4, newAdmissionLimit = 4)

        assertEquals(4, plan.remaining)
        assertEquals(0, count(rows, items, plan))
    }

    @Test
    fun missingFocusRowsCountAfterDryRunAdmissionMakesThemDueNow() {
        val kanji = listOf("裂", "復")
        val rows = kanji.map(::repairRow)
        val plan = focusPlan(kanji, remaining = 2, newAdmissionLimit = 2)

        assertEquals(2, count(rows, emptyList(), plan))
    }

    @Test
    fun dueRowsOutsideTheAdaptiveFocusAreNotCounted() {
        val focused = repairRow("焦")
        val outside = repairRow("外")
        val plan = focusPlan(listOf("焦"), remaining = 1, newAdmissionLimit = 0)

        assertEquals(
            1,
            count(
                rows = listOf(focused, outside),
                items = listOf(
                    reviewItem(focused, dueAtMillis = now),
                    reviewItem(outside, dueAtMillis = now),
                ),
                plan = plan,
            ),
        )
    }

    @Test
    fun continueAllKanjiCountsSelectableTasksOutsideTheAdaptiveFocus() {
        val focused = repairRow("焦")
        val outside = repairRow("外")
        val rows = listOf(focused, outside)
        val items = rows.map { reviewItem(it, dueAtMillis = now) }
        val plan = focusPlan(listOf("焦"), remaining = 1, newAdmissionLimit = 0)

        assertEquals(1, count(rows, items, plan, continueAllKanjiSession = false))
        assertEquals(2, count(rows, items, plan, continueAllKanjiSession = true))
    }

    @Test
    fun allKanjiPlanCountsEverySelectableTaskWithoutASeparateModeFlag() {
        val rows = listOf(repairRow("全"), repairRow("部"))
        val items = rows.map { reviewItem(it, dueAtMillis = now) }
        val plan = RecordsSchedulerModels.AdaptiveLoadPlan(
            false,
            100,
            rows.size,
            rows.size,
            listOf("全"),
            0,
            true,
            "all current problem kanji",
        )

        assertEquals(2, count(rows, items, plan))
    }

    @Test
    fun activeQueueCapBlocksMissingFocusRowFromStudyNowCount() {
        val occupied = repairRow("既")
        val missing = repairRow("新")
        val settings = settingsWithQueue(activeQueueCap = 1, newPerDay = 5)
        val plan = focusPlan(listOf("新"), remaining = 1, newAdmissionLimit = 1)

        assertEquals(
            0,
            count(
                rows = listOf(occupied, missing),
                items = listOf(reviewItem(occupied, dueAtMillis = now + day)),
                plan = plan,
                settings = settings,
            ),
        )
    }

    @Test
    fun exhaustedDailyNewLimitBlocksMissingFocusRowFromStudyNowCount() {
        val admittedToday = repairRow("今")
        val missing = repairRow("次")
        val createdToday = reviewItem(admittedToday, dueAtMillis = now + day)
            .copyBuilder()
            .createdAtMillis(now - 1_000L)
            .build()
        val settings = settingsWithQueue(activeQueueCap = 5, newPerDay = 1)
        val plan = focusPlan(listOf("次"), remaining = 1, newAdmissionLimit = 1)

        assertEquals(
            0,
            count(
                rows = listOf(admittedToday, missing),
                items = listOf(createdToday),
                plan = plan,
                settings = settings,
            ),
        )
    }

    @Test
    fun alreadyRepairedMissingRowIsNotCounted() {
        val repaired = repairedRow("済")
        val plan = focusPlan(listOf("済"), remaining = 1, newAdmissionLimit = 1)

        assertEquals(0, count(listOf(repaired), emptyList(), plan))
    }

    @Test
    fun retiredFocusRowReopensAndCountsWhenRepairIsNeeded() {
        val row = repairRow("戻")
        val retired = reviewItem(row, dueAtMillis = now + day)
            .copyBuilder()
            .state(StudyLadderRules.STATE_RETIRED)
            .activeToken(null)
            .build()
        val plan = focusPlan(listOf("戻"), remaining = 1, newAdmissionLimit = 1)

        assertEquals(1, count(listOf(row), listOf(retired), plan))
    }

    @Test
    fun retiredFocusRowWithMatureSupportDoesNotReopenOrCount() {
        val row = repairedRow("済")
        val retired = reviewItem(row, dueAtMillis = now + day)
            .copyBuilder()
            .state(StudyLadderRules.STATE_RETIRED)
            .activeToken(null)
            .build()
        val plan = focusPlan(listOf("済"), remaining = 1, newAdmissionLimit = 1)

        assertEquals(0, count(listOf(row), listOf(retired), plan))
    }

    @Test
    fun duplicateSelectorTaskKeysCountOnceLikeSessionTracker() {
        val firstRow = repairRowWithMeaning("重", "first")
        val secondRow = repairRowWithMeaning("重", "second")
        val items = listOf(
            reviewItem(firstRow, dueAtMillis = now),
            reviewItem(secondRow, dueAtMillis = now),
        )
        val plan = focusPlan(listOf("重"), remaining = 1, newAdmissionLimit = 0)

        assertEquals(1, count(listOf(firstRow, secondRow), items, plan))
    }

    @Test
    fun configuredStudyAheadControlsFutureReviewCount() {
        val row = repairRow("近")
        val future = reviewItem(row, dueAtMillis = now + 5 * 60_000L)
        val plan = focusPlan(listOf("近"), remaining = 1, newAdmissionLimit = 0)

        assertEquals(0, count(listOf(row), listOf(future), plan, studyAheadMillis = 0L))
        assertEquals(1, count(listOf(row), listOf(future), plan, studyAheadMillis = 10 * 60_000L))
    }

    private fun count(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        plan: RecordsSchedulerModels.AdaptiveLoadPlan,
        settings: RecordsSyncModels.Settings = RecordsSyncModels.Settings.kikuDefaults(),
        studyAheadMillis: Long = 0L,
        continueAllKanjiSession: Boolean = false,
    ): Int {
        return StudyNowCountPolicy.count(
            rows,
            items,
            settings,
            now,
            now - 12 * 60 * 60_000L,
            studyAheadMillis,
            plan,
            continueAllKanjiSession,
            ladder,
        )
    }

    private fun focusPlan(
        kanji: List<String>,
        remaining: Int,
        newAdmissionLimit: Int,
    ): RecordsSchedulerModels.AdaptiveLoadPlan {
        return RecordsSchedulerModels.AdaptiveLoadPlan(
            false,
            20,
            kanji.size,
            remaining,
            kanji,
            newAdmissionLimit,
            false,
            "test focus",
        )
    }

    private fun repairRow(kanji: String): RecordsImportModels.DashboardRow {
        return repairRowWithMeaning(kanji, "meaning-$kanji")
    }

    private fun repairRowWithMeaning(
        kanji: String,
        meaning: String,
    ): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji,
            900,
            meaning,
            "reading-$kanji",
            "search-$kanji",
            24,
            "suspended_archive",
            "reason text $kanji",
            0,
            1,
            0,
            emptyList<RecordsImportModels.Example>(),
        )
    }

    private fun repairedRow(kanji: String): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji,
            900,
            "meaning-$kanji",
            "reading-$kanji",
            "search-$kanji",
            5,
            "mature_support",
            "reason text $kanji",
            2,
            0,
            2,
            emptyList<RecordsImportModels.Example>(),
        )
    }

    private fun reviewItem(
        row: RecordsImportModels.DashboardRow,
        dueAtMillis: Long,
    ): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(
            row.kanji,
            StudyLadderRules.STATE_REVIEW,
            dueAtMillis,
            30.0,
            5.0,
            4,
            0,
            0,
            0,
            0,
            0,
            0L,
            false,
            null,
            0L,
            30,
            StudyQueueSeeder.answerSignature(row),
            null,
            now - day,
            RecordsStudyModels.TaskMemory.initial(),
            RecordsStudyModels.TaskMemory.initial(),
            RecordsStudyModels.TaskMemory.initial(),
            RecordsStudyModels.TaskMemory.initial(),
            RecordsStudyModels.TaskMemory.initial(),
            RecordsBase.LadderRung.KANJI_MEANING,
            RecordsBase.SchedulerPhase.REVIEW,
            0,
            0,
            0L,
            false,
            RecordsStudyModels.TaskMemory.initial(),
        )
    }

    private fun settingsWithQueue(activeQueueCap: Int, newPerDay: Int): RecordsSyncModels.Settings {
        val defaults = RecordsSyncModels.Settings.kikuDefaults()
        return RecordsSyncModels.Settings(
            defaults.modelName,
            defaults.templateName,
            defaults.expressionField,
            defaults.readingField,
            defaults.meaningField,
            defaults.sentenceField,
            defaults.frequencyField,
            defaults.frequencySortField,
            defaults.matureDays,
            defaults.matureSupportThreshold,
            defaults.suspendedRankMin,
            defaults.suspendedRankMax,
            activeQueueCap,
            newPerDay,
            defaults.writingTriggerMissDays,
            defaults.recognitionPromotionPasses,
            defaults.realDueReviewsToMove,
        )
    }
}
