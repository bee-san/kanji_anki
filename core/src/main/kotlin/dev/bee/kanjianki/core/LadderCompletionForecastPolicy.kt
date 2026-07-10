package dev.bee.kanjianki.core

import java.util.Calendar
import java.util.TimeZone

/**
 * Runs the production seeder and review engine forward under an all-Good
 * assumption. This forecasts ladder practice only; Anki-side retirement is a
 * separate evidence decision.
 */
object LadderCompletionForecastPolicy {
    data class MonthPoint(
        val monthStartMillis: Long,
        val completedItems: Int,
        val remainingItems: Int,
    )

    data class Forecast(
        val totalItems: Int,
        val burnDown: List<MonthPoint>,
        val projectedCompletionMonthMillis: Long?,
        val beyondHorizon: Boolean,
        val alreadyAtCeiling: Int,
        val alreadyParked: Int,
        val alreadyRetired: Int,
        val assumptionCopyIds: List<String>,
    )

    @Suppress("kotlin:S107", "kotlin:S3776")
    @JvmStatic
    @JvmOverloads
    fun forecast(
        rows: List<RecordsImportModels.DashboardRow>,
        startingItems: List<RecordsStudyModels.StudyItem>,
        settings: RecordsSyncModels.Settings,
        parameters: RecordsSchedulerModels.SchedulerParameters,
        learningSettings: RecordsSchedulerModels.LearningStepSettings,
        ladder: RecordsBase.StudyLadderSettings,
        nowMillis: Long,
        horizonDays: Int = 730,
        weights: DoubleArray? = null,
    ): Forecast {
        val simulator = SchedulerTimelineSimulator(
            BridgeScheduler.withWeights(weights), rows, startingItems, nowMillis, settings, parameters,
            learningSettings, ladder, retainEvents = false,
        )
        val horizon = LocalDayPolicy.moveLocalDays(nowMillis, horizonDays.coerceAtLeast(0), FORECAST_ZONE)
        val initialRetired = startingItems.count { it.state == StudyLadderRules.STATE_RETIRED }
        val initialCeiling = startingItems.count { atCeiling(it, ladder) && it.state != StudyLadderRules.STATE_RETIRED }
        val initialParked = startingItems.count { parked(it, settings, ladder) }
        val targetKanji = linkedSetOf<String>().apply {
            rows.filter { it.matureSupportCount < settings.matureSupportThreshold }.mapTo(this) { it.kanji }
            startingItems.filter { it.state != StudyLadderRules.STATE_RETIRED }.mapTo(this) { it.kanji }
        }
        val remainingTargets = LinkedHashSet(targetKanji)
        val completionAt = linkedMapOf<String, Long>()
        fun recordCompletion(kanji: String, completedAtMillis: Long) {
            completionAt.putIfAbsent(kanji, completedAtMillis)
            remainingTargets.remove(kanji)
        }
        startingItems.filter {
            it.state == StudyLadderRules.STATE_RETIRED || parked(it, settings, ladder) ||
                (atCeiling(it, ladder) && it.phase == RecordsBase.SchedulerPhase.REVIEW && it.realPassStreak > 0)
        }
            .forEach { recordCompletion(it.kanji, nowMillis) }

        simulator.seedQueue()
        var simulatedNow = nowMillis
        var iterations = 0
        while (remainingTargets.isNotEmpty() && simulatedNow <= horizon && iterations < MAX_ITERATIONS) {
            iterations++
            simulator.seedQueue()
            val items = simulator.currentItems()
            var nextDue: Long? = null
            for (item in items) {
                if (item.state == StudyLadderRules.STATE_RETIRED) {
                    recordCompletion(item.kanji, simulatedNow)
                } else {
                    nextDue = nextDue?.let { minOf(it, item.dueAtMillis) } ?: item.dueAtMillis
                }
            }
            if (nextDue == null) {
                simulatedNow = LocalDayPolicy.moveLocalDays(simulatedNow, 1, FORECAST_ZONE)
                simulator.advanceTo(simulatedNow)
                continue
            }
            simulatedNow = maxOf(simulatedNow, nextDue)
            if (simulatedNow > horizon) break
            simulator.advanceTo(simulatedNow)
            val next = simulator.nextSession()
            val selected = next.snapshot
            if (selected == null) {
                simulatedNow = LocalDayPolicy.moveLocalDays(simulatedNow, 1, FORECAST_ZONE)
                simulator.advanceTo(simulatedNow)
                continue
            }
            val answer = if (selected.rung == RecordsBase.LadderRung.WRITE_KANJI) {
                simulator.answerWriting("good", passed = true, clean = true, hintsUsed = 0)
            } else {
                simulator.answer("good")
            }
            val after = answer.snapshot
            if (after != null) {
                val item = simulator.currentItems().firstOrNull {
                    it.kanji == after.kanji && it.rung == after.rung && it.dueAtMillis == after.dueAtMillis
                }
                if (item != null && atCeiling(item, ladder) && item.phase == RecordsBase.SchedulerPhase.REVIEW && item.realPassStreak > 0) {
                    recordCompletion(item.kanji, simulatedNow)
                }
            }
        }

        val total = targetKanji.size
        val completedTarget = completionAt.filterKeys { it in targetKanji }
        val points = monthPoints(nowMillis, horizon, total, completedTarget.values.toList())
        val completed = total == 0 || completedTarget.size == total
        val completionMonth = if (completed && total > 0) monthStart(completedTarget.values.maxOrNull() ?: nowMillis) else null
        return Forecast(
            totalItems = total,
            burnDown = points,
            projectedCompletionMonthMillis = completionMonth,
            beyondHorizon = !completed,
            alreadyAtCeiling = initialCeiling,
            alreadyParked = initialParked,
            alreadyRetired = initialRetired,
            assumptionCopyIds = listOf("all_passes", "anki_retirement_separate"),
        )
    }

    private fun atCeiling(item: RecordsStudyModels.StudyItem, ladder: RecordsBase.StudyLadderSettings): Boolean =
        ladder.isAtCeiling(item.rung, item.rungAvailability())

    private fun parked(
        item: RecordsStudyModels.StudyItem,
        settings: RecordsSyncModels.Settings,
        ladder: RecordsBase.StudyLadderSettings,
    ): Boolean = item.state != StudyLadderRules.STATE_RETIRED &&
        item.phase == RecordsBase.SchedulerPhase.REVIEW && atCeiling(item, ladder) &&
        item.matureIntervalDays > settings.ladderPromotionIntervalDays * RecordsBase.CEILING_PARK_INTERVAL_MULTIPLIER

    private fun monthPoints(start: Long, horizon: Long, total: Int, completions: List<Long>): List<MonthPoint> {
        val points = ArrayList<MonthPoint>()
        var cursor = monthStart(start)
        while (cursor <= horizon) {
            val next = nextMonth(cursor)
            val completed = completions.count { it < next }.coerceAtMost(total)
            points += MonthPoint(cursor, completed, (total - completed).coerceAtLeast(0))
            cursor = next
        }
        return points
    }

    private fun monthStart(millis: Long): Long = Calendar.getInstance(FORECAST_ZONE).run {
        timeInMillis = millis
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        timeInMillis
    }

    private fun nextMonth(monthStart: Long): Long = Calendar.getInstance(FORECAST_ZONE).run {
        timeInMillis = monthStart
        add(Calendar.MONTH, 1)
        timeInMillis
    }

    private const val MAX_ITERATIONS = 100_000
    private val FORECAST_ZONE: TimeZone = TimeZone.getTimeZone("UTC")
}
