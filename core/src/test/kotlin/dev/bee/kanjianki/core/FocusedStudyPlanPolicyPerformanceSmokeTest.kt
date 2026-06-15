package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.system.measureNanoTime

class FocusedStudyPlanPolicyPerformanceSmokeTest {
    @Test
    fun studyMoreNewCardsPlanMatchesLinearReferenceAndPrintsLookupTimings() {
        val now = 10_000L
        val rows = (0 until 2_000).map { row("字$it") }
        val items = (0 until 2_000).map { review("字$it", now - 1L, 1) }
        val requestedKanji = buildList {
            repeat(1_500) { index ->
                add("字${index * 2}")
                add("missing-$index")
            }
        }

        var reference: RecordsSchedulerModels.AdaptiveLoadPlan? = null
        var optimized: RecordsSchedulerModels.AdaptiveLoadPlan? = null

        val referenceNs = measureNanoTime {
            reference = linearStudyMoreNewCardsPlan(requestedKanji, rows, items, now)
        }
        val optimizedNs = measureNanoTime {
            optimized = FocusedStudyPlanPolicy.studyMoreNewCardsPlan(requestedKanji, rows, items, now)
        }

        val referencePlan = requireNotNull(reference)
        val optimizedPlan = requireNotNull(optimized)
        assertEquals(referencePlan.autoMode, optimizedPlan.autoMode)
        assertEquals(referencePlan.workloadPercent, optimizedPlan.workloadPercent)
        assertEquals(referencePlan.target, optimizedPlan.target)
        assertEquals(referencePlan.remaining, optimizedPlan.remaining)
        assertEquals(referencePlan.focusKanji, optimizedPlan.focusKanji)
        assertEquals(referencePlan.newAdmissionLimit, optimizedPlan.newAdmissionLimit)
        assertEquals(referencePlan.allKanjiMode, optimizedPlan.allKanjiMode)
        assertEquals(referencePlan.status, optimizedPlan.status)
        println(
            "FocusedStudyPlanPolicy.studyMoreNewCardsPlan linear_ms=${referenceNs / 1_000_000} " +
                "optimized_ms=${optimizedNs / 1_000_000}"
        )
    }

    private fun linearStudyMoreNewCardsPlan(
        requestedKanji: List<String>?,
        rows: List<RecordsImportModels.DashboardRow>?,
        items: List<RecordsStudyModels.StudyItem>?,
        nowMillis: Long,
    ): RecordsSchedulerModels.AdaptiveLoadPlan {
        val focus = ArrayList<String>()
        val safeRows = rows.orEmpty()
        for (kanji in requestedKanji.orEmpty()) {
            if (StudyCollectionLookup.dashboardRowByKanji(safeRows, kanji) != null) {
                focus.add(kanji)
            }
        }
        var remaining = 0
        val safeItems = items.orEmpty()
        for (kanji in focus) {
            val item = StudyCollectionLookup.studyItemByKanji(safeItems, kanji)
            if (FocusedStudyPlanPolicy.itemDueForFocus(item, nowMillis)) {
                remaining++
            }
        }
        return RecordsSchedulerModels.AdaptiveLoadPlan(
            false,
            100,
            focus.size,
            remaining,
            focus,
            0,
            false,
            "Custom study: " + StudyTextCopy.countText(focus.size, "extra new card", "extra new cards") + ".",
        )
    }

    private fun row(kanji: String): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji,
            900,
            "meaning",
            "reading",
            "search",
            1,
            "weak_support",
            "reason",
            1,
            0,
            0,
            emptyList<RecordsImportModels.Example>(),
        )
    }

    private fun review(kanji: String, dueAtMillis: Long, totalReviews: Int): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(kanji, StudyLadderRules.STATE_REVIEW, dueAtMillis, 1.0, 5.0, totalReviews, 0, 0, 1, null, 0L)
    }
}
