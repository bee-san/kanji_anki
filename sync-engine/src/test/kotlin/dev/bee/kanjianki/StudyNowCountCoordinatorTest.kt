package dev.bee.kanjianki

import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.StudyItemComparators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyNowCountCoordinatorTest {
    private val now = 1_725_000_000_000L
    private val settings = RecordsSyncModels.Settings.kikuDefaults()
    private val ladder = RecordsBase.StudyLadderSettings.defaults()

    @Test
    fun selectorUsesPostSeedFocusInsteadOfStaleInitialFocus() {
        val seededRow = row("種")
        val newlyFocusedRow = row("新")
        val initialPlan = plan(listOf(seededRow.kanji), newAdmissionLimit = 1)
        val effectivePlan = plan(listOf(newlyFocusedRow.kanji), newAdmissionLimit = 1)
        var replans = 0

        val result = count(
            rows = listOf(seededRow, newlyFocusedRow),
            currentItems = emptyList(),
            initialPlan = initialPlan,
        ) { seeded ->
            replans++
            assertEquals(listOf(seededRow.kanji), seeded.map { it.kanji })
            effectivePlan
        }

        assertEquals(1, replans)
        assertEquals(0, result.studyItemCount)
        assertSame(effectivePlan, result.effectivePlan)
    }

    @Test
    fun committedMissingFocusItemIsDryRunAdmittedBeforeCounting() {
        val focused = row("未")
        val plan = plan(listOf(focused.kanji), newAdmissionLimit = 1)

        val result = count(
            rows = listOf(focused),
            currentItems = emptyList(),
            initialPlan = plan,
        ) { plan }

        assertEquals(1, result.studyItemCount)
        assertSame(plan, result.effectivePlan)
    }

    @Test
    fun queueComparisonIncludesEveryConditionalAvailabilityAndMemory() {
        val base = studyItem("条")
        val changedMemory = RecordsStudyModels.TaskMemory.initial().withDueAtMillis(1L)
        val variants = listOf(
            base.withHasSimilarKanji(true),
            base.withSimilarKanjiMemory(changedMemory),
            base.withHasKanjiReading(true),
            base.withKanjiReadingMemory(changedMemory),
            base.withHasReadingKanji(true),
            base.withReadingKanjiMemory(changedMemory),
            base.withHasSentenceReading(true),
            base.withSentenceReadingMemory(changedMemory),
        )

        assertTrue(StudyItemComparators.sameStudyQueue(listOf(base), listOf(base)))
        for (variant in variants) {
            assertFalse(StudyItemComparators.sameStudyQueue(listOf(base), listOf(variant)))
        }
    }

    private fun count(
        rows: List<RecordsImportModels.DashboardRow>,
        currentItems: List<RecordsStudyModels.StudyItem>,
        initialPlan: RecordsSchedulerModels.AdaptiveLoadPlan,
        replanner: StudyNowCountCoordinator.SeededPlanProvider,
    ): StudyNowCountCoordinator.Result {
        return StudyNowCountCoordinator.count(
            StudyNowCountCoordinator.Request(
                queue = StudyNowCountCoordinator.QueueInput(rows, currentItems, settings, ladder),
                timing = StudyNowCountCoordinator.Timing(now, now - 12 * 60 * 60_000L, 0L),
                mode = StudyNowCountCoordinator.Mode(initialPlan, false),
                pipeline = StudyNowCountCoordinator.Pipeline(BridgeScheduler(), { it }, replanner),
            ),
        )
    }

    private fun plan(
        focusKanji: List<String>,
        newAdmissionLimit: Int,
    ): RecordsSchedulerModels.AdaptiveLoadPlan {
        return RecordsSchedulerModels.AdaptiveLoadPlan(
            false,
            20,
            focusKanji.size,
            focusKanji.size,
            focusKanji,
            newAdmissionLimit,
            false,
            "test focus",
        )
    }

    private fun row(kanji: String): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji,
            900,
            "meaning-$kanji",
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

    private fun studyItem(kanji: String): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(
            kanji,
            "review",
            now,
            1.0,
            2.0,
            1,
            0,
            0,
            0,
            "signature",
            now,
        )
    }
}
