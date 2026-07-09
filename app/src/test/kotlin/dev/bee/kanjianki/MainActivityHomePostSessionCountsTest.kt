package dev.bee.kanjianki

import dev.bee.kanjianki.core.AdaptiveLoadPlanner
import dev.bee.kanjianki.core.DailyStudyPlanPolicy
import dev.bee.kanjianki.core.DailyStudyPlanRequest
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyLadderRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * PS2 regression at the home-derivation layer: right after a session the focus
 * kanji are all in `studiedToday` and their study items sit in `learning` due a
 * few minutes out. The three home-derived counts — the adaptive plan
 * `remaining` (home CTA / focus metric), the Study nav badge fallback, and the
 * today plan `dueNow` — must all read 0 until the learning step delays elapse,
 * then reappear once `now` passes those due times.
 *
 * These exercise the exact core policies `MainActivityStudyPlanProvider` drives
 * (`AdaptiveLoadPlanner`, `DailyStudyPlanPolicy`) plus `studySessionBadgeCount`.
 */
class MainActivityHomePostSessionCountsTest {
    private val now = 1_725_000_000_000L
    private val minute = 60_000L

    @Test
    fun postSessionLearningCardsReadZeroUntilDueThenReappear() {
        val kanji = listOf("字A", "字B", "字C")
        val rows = kanji.map { row(it) }
        // Every focus kanji was just answered `Again`: in learning, due 1..10 min out.
        val items = kanji.mapIndexed { index, k ->
            learningItem(k, dueAt = now + (index + 1) * minute)
        }
        val studiedToday = kanji.toSet()

        // Immediately after the session (now): all counts read 0.
        assertEquals(0, adaptivePlanRemaining(rows, items, studiedToday, now))
        assertEquals(0, badgeFallback(rows, items, studiedToday, now))
        assertEquals(0, dailyDueNow(items, now))

        // Advance past the latest step delay: the counts reappear.
        val later = now + 11 * minute
        assertEquals(3, adaptivePlanRemaining(rows, items, studiedToday, later))
        assertEquals(3, badgeFallback(rows, items, studiedToday, later))
        assertEquals(3, dailyDueNow(items, later))
    }

    private fun adaptivePlanRemaining(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        studiedToday: Set<String>,
        nowMillis: Long,
    ): Int = plan(rows, items, studiedToday, nowMillis).remaining

    private fun badgeFallback(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        studiedToday: Set<String>,
        nowMillis: Long,
    ): Int {
        // Post-session there is no in-flight tracker (target 0), so the badge
        // falls back to the cached adaptive-plan remaining, exactly as
        // MainActivityHome caches it.
        val cachedPlanRemaining = plan(rows, items, studiedToday, nowMillis).remaining.coerceAtLeast(0)
        return studySessionBadgeCount(
            trackerTargetCount = 0,
            trackerCompletedCount = 0,
            cachedPlanRemaining = cachedPlanRemaining,
        )
    }

    private fun dailyDueNow(items: List<RecordsStudyModels.StudyItem>, nowMillis: Long): Int {
        val plan = DailyStudyPlanPolicy.plan(
            DailyStudyPlanRequest(
                nowMillis = nowMillis,
                dueAtMillis = items.map { it.dueAtMillis },
                studiedToday = true,
                lastSuccessfulSyncAtMillis = nowMillis - minute,
            ),
        )
        return plan.dueNow
    }

    private fun plan(
        rows: List<RecordsImportModels.DashboardRow>,
        items: List<RecordsStudyModels.StudyItem>,
        studiedToday: Set<String>,
        nowMillis: Long,
    ): RecordsSchedulerModels.AdaptiveLoadPlan {
        return AdaptiveLoadPlanner().plan(
            AdaptiveLoadPlanner.PlanRequest.builder(
                rows,
                items,
                RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                1,
                studiedToday,
                AdaptiveLoadPlanner.WorkloadPolicy.manual(100),
                nowMillis,
            ).build(),
        )
    }

    private fun learningItem(kanji: String, dueAt: Long): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(kanji, StudyLadderRules.STATE_LEARNING, dueAt, 1.0, 5.0, 1, 1, 0, 0, "", now)
            .copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.RELEARNING)
            .activeToken("token-$kanji")
            .build()
    }

    private fun row(kanji: String): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji,
            1,
            "meaning-$kanji",
            "reading-$kanji",
            "search-$kanji",
            10,
            "reason-$kanji",
            "reason text $kanji",
            2,
            0,
            3,
            listOf(example(kanji)),
        )
    }

    private fun example(kanji: String): RecordsImportModels.Example {
        return RecordsImportModels.Example("active", 1L, 2L, "expr-$kanji", "reading", "meaning", "", false, 0)
    }
}
