package dev.bee.kanjianki.progress

import dev.bee.kanjianki.core.ChartAxisPolicy
import dev.bee.kanjianki.core.ConfusionInsightPolicy
import dev.bee.kanjianki.core.CoreSkill
import dev.bee.kanjianki.core.ForecastTextCopy
import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.ReviewHeatmapPolicy
import dev.bee.kanjianki.core.StatsValueFormatter
import dev.bee.kanjianki.core.StatsDashboardCopy
import dev.bee.kanjianki.core.TaskTypeAccuracyPolicy
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.STATS_CACHE_FORMAT_VERSION
import dev.bee.kanjianki.data.StatsCacheStore
import dev.bee.kanjianki.data.StudyStatsStore
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.roundToInt

internal interface ProgressAnalyticsStatsSource {
    fun cachedStatsSnapshotOrNull(): StatsCacheStore.Snapshot?
    fun latestStatsSnapshotOrNull(): StatsCacheStore.Snapshot?
    fun recomputeStatsSnapshotSynchronously(nowMillis: Long): StatsCacheStore.Snapshot
    fun reviewDaySummaries(nowMillis: Long, days: Int): List<ReviewDaySummary>
}

internal fun progressAnalyticsSnapshot(
    store: LocalStore,
    nowMillis: Long = System.currentTimeMillis(),
    scheduleRefresh: (() -> Unit)? = null,
): ProgressAnalyticsState = progressAnalyticsSnapshot(
    source = object : ProgressAnalyticsStatsSource {
        override fun cachedStatsSnapshotOrNull() = store.cachedStatsSnapshotOrNull()
        override fun latestStatsSnapshotOrNull() = store.latestStatsSnapshotOrNull()
        override fun recomputeStatsSnapshotSynchronously(nowMillis: Long) = store.recomputeStatsSnapshotSynchronously(nowMillis)
        override fun reviewDaySummaries(nowMillis: Long, days: Int) =
            dev.bee.kanjianki.data.StudyStatsQueries(store).reviewDaySummaries(nowMillis, days).map { it.toReviewDaySummary() }
    },
    nowMillis = nowMillis,
    scheduleRefresh = scheduleRefresh,
)

internal fun progressAnalyticsSnapshot(
    source: ProgressAnalyticsStatsSource,
    nowMillis: Long = System.currentTimeMillis(),
    scheduleRefresh: (() -> Unit)? = null,
): ProgressAnalyticsState {
    val fresh = source.cachedStatsSnapshotOrNull()
    val latest = if (fresh == null) source.latestStatsSnapshotOrNull() else null
    val currentLatest = latest?.takeIf { it.cacheFormatVersion == STATS_CACHE_FORMAT_VERSION }
    val snapshot = fresh ?: currentLatest ?: source.recomputeStatsSnapshotSynchronously(nowMillis)
    val locale = Locale.getDefault()
    val copy = StatsDashboardCopy.forLocale(locale)
    if (currentLatest != null || (fresh != null && snapshot.reviewDaySummaries.isEmpty())) scheduleRefresh?.invoke()

    val reviewDaysYear = if (snapshot.reviewDaySummaries.isNotEmpty()) {
        snapshot.reviewDaySummaries.map { it.toReviewDaySummary() }.takeLast(366)
    } else emptyList()
    val reviewDays90 = reviewDaysYear.takeLast(90)
    val reviewDays30 = reviewDays90.takeLast(30)
    val reviewDays14 = reviewDays30.takeLast(14)
    val reviewDays7 = reviewDays14.takeLast(7)
    val reviewDays7Previous = reviewDays14.take(7)
    val reviewDays30Previous = if (reviewDays90.size >= 60) {
        reviewDays90.takeLast(60).take(30)
    } else {
        emptyList()
    }
    val ranges = listOf(AnalyticsRange.SEVEN_DAYS, AnalyticsRange.THIRTY_DAYS, AnalyticsRange.NINETY_DAYS)
    val rangeDays = mapOf(
        AnalyticsRange.SEVEN_DAYS to reviewDays7,
        AnalyticsRange.THIRTY_DAYS to reviewDays30,
        AnalyticsRange.NINETY_DAYS to reviewDays90,
    )
    val reviewRangeData = rangeDays.mapValues { (range, days) -> reviewsRangeData(range, days, copy) }
    val accuracyRangeData = rangeDays.mapValues { (range, days) -> accuracyTrendChart(range, days, nowMillis, copy) }

    val impactStats = snapshot.studyImpactStats
    val outcome = snapshot.outcomeStats
    val impact = snapshot.impactReport
    val streak = snapshot.studyStreak
    val taskTime = snapshot.studyTaskTimeStats
    val total30 = reviewDays30.sumOf { it.total }
    val correct30 = reviewDays30.sumOf { it.correct() }
    val accuracy30 = percent(correct30, total30)
    val previousAccuracy = percent(reviewDays30Previous.sumOf { it.correct() }, reviewDays30Previous.sumOf { it.total })
    val accuracyDelta = if (reviewDays30Previous.sumOf { it.total } > 0) {
        deltaLabel(accuracy30, previousAccuracy, copy, thirtyDayWindow = true)
    } else null
    val taskRows30 = snapshot.taskTypeDaySummaries.filter { it.dayStartMillis >= reviewDays30.firstOrNull()?.dayStart.orZero() }
    val groupedAccuracy = TaskTypeAccuracyPolicy.summarize(taskRows30.map {
        TaskTypeAccuracyPolicy.Summary(it.taskType, it.correct, it.total)
    })
    val reviewShare = distributionFromAccuracy(groupedAccuracy, copy)
    val cumulative = snapshot.cumulativeKanjiPracticed
    val cumulativeChart = cumulativeChart(cumulative, copy)
    val practicedDelta = cumulativeSevenDayDelta(cumulative, nowMillis)
    val adaptiveHealth = outcome.adaptiveHealth
    val usesAdaptiveHealth = adaptiveHealth.totalAdaptiveItems > 0
    val adaptiveProgressTotal = maxOf(adaptiveHealth.totalAdaptiveItems, outcome.ladderHealth.totalActiveItems)
    val legacyTransitionCount = (adaptiveProgressTotal - adaptiveHealth.totalAdaptiveItems).coerceAtLeast(0)
    val progressRows = if (usesAdaptiveHealth) {
        adaptiveRows(adaptiveHealth, adaptiveProgressTotal, legacyTransitionCount, copy)
    } else {
        ladderRows(outcome.ladderHealth, copy)
    }
    val range7 = reviewRangeData.getValue(AnalyticsRange.SEVEN_DAYS)
    val inventory = snapshot.confusionMeanings.map { (kanji, meaning) ->
        RecordsImportModels.KanjiInventoryItem(kanji, meaning, "", "", 0, 0, false, 0L)
    }
    val confusionPairs = ConfusionInsightPolicy.topPairs(snapshot.wrongPickCounts, inventory).map {
        ProgressConfusionPairState(
            it.firstKanji, it.secondKanji, it.firstMeaning, it.secondMeaning,
            it.firstToSecond, it.secondToFirst,
        )
    }
    val heatmap = ReviewHeatmapPolicy.build(
        reviewDaysYear.map { ReviewHeatmapPolicy.DaySummary(it.dayStart, it.total) },
        nowMillis,
    )

    val state = ProgressAnalyticsState(
        generatedAtMillis = snapshot.generatedAtMillis,
        overview = ProgressOverviewState(
            title = copy.statsOverview,
            subtitle = copy.overviewSubtitle,
            totalReviews = ProgressCountMetricState(
                impactStats.totalReviews, formatInt(impactStats.totalReviews),
                deltaLabel(reviewDays7.sumOf { it.total }, reviewDays7Previous.sumOf { it.total }, copy),
                copy.allReviews,
            ),
            accuracy = ProgressCountMetricState(
                accuracy30, "$accuracy30%", accuracyDelta, copy.thirtyDayAccuracy,
            ),
            currentStreak = ProgressStreakMetricState(
                streak.currentDays, streak.bestDays, copy.days(streak.currentDays),
                if (streak.studiedToday) copy.studiedToday else copy.keepStreakAlive,
            ),
            kanjiLearned = ProgressCountMetricState(
                cumulative.lastOrNull()?.cumulativeCount ?: impactStats.distinctReviewedKanji,
                formatInt(cumulative.lastOrNull()?.cumulativeCount ?: impactStats.distinctReviewedKanji),
                practicedDelta.takeIf { it > 0 }?.let { copy.thisWeekDelta(formatInt(it)) },
                copy.distinctKanjiPracticed,
            ),
            focusSessions = ProgressCountMetricState(taskTime.answeredTasks, formatInt(taskTime.answeredTasks), detailLabel = copy.lastSevenDays),
            studyTime = ProgressDurationMetricState(
                taskTime.lastSevenDaysMillis, StatsValueFormatter.duration(taskTime.lastSevenDaysMillis),
                taskTime.todayMillis.takeIf { it > 0 }?.let { copy.todayDelta(StatsValueFormatter.duration(it, locale)) }, copy.thisWeek,
            ),
            reviewsToday = ProgressCountMetricState(streak.reviewsToday, formatInt(streak.reviewsToday)),
            reviewsOverTime = volumeChart(reviewDays30, copy),
            cardTypeBreakdown = ProgressDistributionChartState(
                copy.reviewShare, reviewShare,
                distributionSummary(copy.reviewShare, reviewShare, copy),
            ),
            correctIncorrectBreakdown = distribution(
                copy.correctVsIncorrect,
                listOf(copy.correct to correct30, copy.incorrect to (total30 - correct30).coerceAtLeast(0)),
                copy,
            ),
        ),
        reviewsAnalytics = ProgressReviewsAnalyticsState(
            title = copy.reviewsAnalytics,
            selectedRange = AnalyticsRange.SEVEN_DAYS,
            availableRanges = ranges,
            reviewsPerDay = range7.reviewsPerDay,
            totalReviews = range7.totalReviews,
            averagePerDay = range7.averagePerDay,
            correct = range7.correct,
            incorrect = range7.incorrect,
            bestDayLabel = range7.bestDayLabel,
            currentStreak = ProgressStreakMetricState(streak.currentDays, streak.bestDays, copy.days(streak.currentDays), copy.bestDays(streak.bestDays)),
            tip = when {
                streak.studiedToday -> copy.streakSafeTip
                streak.currentDays > 0 -> copy.keepStreakTip
                else -> copy.startMomentumTip
            },
            accessibilitySummary = range7.accessibilitySummary,
            rangeData = reviewRangeData,
            heatmap = heatmap,
        ),
        accuracyRetention = ProgressAccuracyRetentionState(
            title = copy.accuracyByGroup,
            selectedRange = AnalyticsRange.THIRTY_DAYS,
            availableRanges = ranges,
            accuracyTrend = accuracyRangeData.getValue(AnalyticsRange.THIRTY_DAYS),
            retentionByCardType = groupedAccuracy.map {
                ProgressRetentionRowState(copy.group(it.group), it.percent, "${it.correct}/${it.total} · ${it.percent}%")
            },
            retentionSummary = groupedAccuracy.joinToString { "${copy.group(it.group)} ${it.correct}/${it.total}" },
            categoryStatuses = groupedAccuracy.map { ProgressCategoryStatusState(copy.group(it.group), copy.status(it.percent)) },
            rangeData = accuracyRangeData,
        ),
        progressByLevel = ProgressByLevelState(
            title = if (usesAdaptiveHealth) copy.coreSkillHealth else copy.ladderDistribution,
            selectedFilterLabel = if (usesAdaptiveHealth) {
                copy.adaptiveHealthSummary(
                    adaptiveProgressTotal,
                    adaptiveHealth.activeRepairCount,
                    adaptiveHealth.revalidationPendingCount,
                    adaptiveHealth.escalationRiskCount,
                    adaptiveHealth.stuckRepairCount,
                )
            } else "",
            overallLearned = ProgressFractionMetricState(
                if (usesAdaptiveHealth) adaptiveHealth.contextualCompleteCount else outcome.ladderHealth.rungCounts.values.sum(),
                if (usesAdaptiveHealth) adaptiveProgressTotal else outcome.ladderHealth.totalActiveItems,
                if (usesAdaptiveHealth) {
                    percent(adaptiveHealth.contextualCompleteCount, adaptiveProgressTotal)
                } else {
                    percent(outcome.ladderHealth.rungCounts.values.sum(), outcome.ladderHealth.totalActiveItems)
                },
                if (usesAdaptiveHealth) {
                    copy.contextualComplete(adaptiveHealth.contextualCompleteCount)
                } else {
                    copy.activeItems(outcome.ladderHealth.totalActiveItems)
                },
                if (usesAdaptiveHealth) {
                    copy.adaptiveHealthSummary(
                        adaptiveProgressTotal,
                        adaptiveHealth.activeRepairCount,
                        adaptiveHealth.revalidationPendingCount,
                        adaptiveHealth.escalationRiskCount,
                        adaptiveHealth.stuckRepairCount,
                    )
                } else {
                    copy.activeItemsSummary(outcome.ladderHealth.totalActiveItems)
                },
            ),
            levelRows = progressRows,
            cumulativeProgress = cumulativeChart,
        ),
        weaknessInsights = ProgressWeaknessInsightsState(
            title = copy.weaknessInsights,
            focusScore = ProgressScoreMetricState(
                focusScore(impact), 100, copy.focusStatus(focusScore(impact)),
                "${focusScore(impact)} / 100 · ${copy.focusStatus(focusScore(impact))}",
            ),
            weaknessRows = weaknessRows(impact, copy),
            mostMissedKanji = mostMissedKanji(snapshot.recentMistakes),
            supportNeeded = supportNeeded(outcome, copy),
            confusionPairs = confusionPairs,
            focusScoreAvailable = impact.helpedCount + impact.notHelpingCount + impact.needsMoreCardsCount > 0,
        ),
        forecast = forecastState(snapshot, copy),
    )
    return state
}

internal data class ReviewDaySummary(
    val dayStart: Long,
    val total: Int,
    val again: Int,
    val hard: Int,
    val good: Int,
    val easy: Int,
    val writingRequired: Int,
    val writingFailed: Int,
) {
    fun correct(): Int = (total - again).coerceAtLeast(0)
    fun accuracyPercent(): Int = percent(correct(), total)
}

private fun StatsCacheStore.ReviewDaySummarySnapshot.toReviewDaySummary() = ReviewDaySummary(
    dayStartMillis, total, again, hard, good, easy, writingRequired, writingFailed,
)

private fun Long?.orZero(): Long = this ?: 0L

private fun reviewsRangeData(range: AnalyticsRange, days: List<ReviewDaySummary>, copy: StatsDashboardCopy): ProgressReviewsRangeData {
    val total = days.sumOf { it.total }
    val correct = days.sumOf { it.correct() }
    val incorrect = (total - correct).coerceAtLeast(0)
    val average = if (days.isEmpty()) 0 else (total / days.size.toDouble()).roundToInt()
    val labelPattern = if (range == AnalyticsRange.SEVEN_DAYS) "EEE" else "MMM d"
    val chartDays = if (range == AnalyticsRange.SEVEN_DAYS) days else bucketSummaries(days, 10)
    val best = days.maxWithOrNull(compareBy<ReviewDaySummary> { it.total }.thenByDescending { it.dayStart })
    val summary = copy.reviewSummary(range.days, formatInt(total), formatInt(average), formatInt(correct), formatInt(incorrect))
    return ProgressReviewsRangeData(
        reviewsPerDay = ProgressBarChartState(copy.reviewsPerDay, chartDays.map { dayLabel(it.dayStart, labelPattern) }, chartDays.map { it.total }, summary, range),
        totalReviews = ProgressCountMetricState(total, formatInt(total)),
        averagePerDay = ProgressCountMetricState(average, formatInt(average)),
        correct = ProgressCountMetricState(correct, formatInt(correct)),
        incorrect = ProgressCountMetricState(incorrect, formatInt(incorrect)),
        bestDayLabel = best?.takeIf { it.total > 0 }?.let { dayLabel(it.dayStart, labelPattern) } ?: copy.noData,
        accessibilitySummary = summary,
    )
}

private fun accuracyTrendChart(range: AnalyticsRange, days: List<ReviewDaySummary>, nowMillis: Long, copy: StatsDashboardCopy): ProgressLineChartState {
    val buckets = bucketSummaries(days, if (range == AnalyticsRange.SEVEN_DAYS) 7 else 6)
    val values = buckets.map { it.accuracyPercent() }
    val total = days.sumOf { it.total }
    val accuracy = percent(days.sumOf { it.correct() }, total)
    return lineChart(
        title = copy.accuracyOverTime,
        labels = buckets.map { dayLabel(it.dayStart, "MMM d") },
        series = if (total == 0) emptyList() else listOf(ProgressSeriesState(copy.accuracyPercent, values)),
        summary = copy.accuracySummary(range.days, accuracy, dayLabel(days.lastOrNull()?.dayStart ?: nowMillis, "MMM d")),
        range = range,
        tooltip = buckets.lastOrNull()?.let { "${dayLabel(it.dayStart, "MMM d")}, ${it.accuracyPercent()}%" },
    )
}

private fun volumeChart(days: List<ReviewDaySummary>, copy: StatsDashboardCopy): ProgressLineChartState {
    val buckets = bucketSummaries(days, 6)
    return lineChart(
        copy.reviewsOverTime, buckets.map { dayLabel(it.dayStart, "MMM d") },
        if (buckets.sumOf { it.total } == 0) emptyList() else listOf(ProgressSeriesState(copy.reviews, buckets.map { it.total })),
        copy.volumeSummary(), AnalyticsRange.THIRTY_DAYS,
        buckets.lastOrNull()?.let { copy.reviewsTooltip(dayLabel(it.dayStart, "MMM d"), it.total) },
    )
}

private fun cumulativeChart(points: List<StatsCacheStore.CumulativeKanjiSnapshot>, copy: StatsDashboardCopy): ProgressLineChartState {
    val shown = points.takeLast(12)
    return lineChart(
        copy.cumulativePracticed,
        shown.map { dayLabel(it.dayStartMillis, "MMM d") },
        if (shown.isEmpty()) emptyList() else listOf(ProgressSeriesState(copy.practicedKanji, shown.map { it.cumulativeCount })),
        copy.cumulativeSummary(),
        AnalyticsRange.NINETY_DAYS,
        shown.lastOrNull()?.let { copy.practicedTooltip(dayLabel(it.dayStartMillis, "MMM d"), it.cumulativeCount) },
    )
}

private fun lineChart(
    title: String,
    labels: List<String>,
    series: List<ProgressSeriesState>,
    summary: String,
    range: AnalyticsRange? = null,
    tooltip: String? = null,
): ProgressLineChartState {
    val axis = ChartAxisPolicy.forValues(series.flatMap { it.values })
    return ProgressLineChartState(title, labels, series, summary, range, tooltip, axis)
}

private fun bucketSummaries(days: List<ReviewDaySummary>, bucketCount: Int): List<ReviewDaySummary> {
    if (days.isEmpty()) return emptyList()
    val size = ceil(days.size / bucketCount.coerceAtLeast(1).toDouble()).toInt().coerceAtLeast(1)
    return days.chunked(size).map { slice ->
        ReviewDaySummary(
            slice.last().dayStart, slice.sumOf { it.total }, slice.sumOf { it.again },
            slice.sumOf { it.hard }, slice.sumOf { it.good }, slice.sumOf { it.easy },
            slice.sumOf { it.writingRequired }, slice.sumOf { it.writingFailed },
        )
    }
}

private fun distributionFromAccuracy(groups: List<TaskTypeAccuracyPolicy.Accuracy>, copy: StatsDashboardCopy): List<ProgressDistributionSegmentState> {
    val total = groups.sumOf { it.total }
    if (total == 0) return emptyList()
    return groups.map { ProgressDistributionSegmentState(copy.group(it.group), it.total, percent(it.total, total)) }
}

private fun distribution(
    title: String,
    values: List<Pair<String, Int>>,
    copy: StatsDashboardCopy,
): ProgressDistributionChartState {
    val total = values.sumOf { it.second.coerceAtLeast(0) }
    val segments = if (total == 0) emptyList() else values.filter { it.second > 0 }.map {
        ProgressDistributionSegmentState(it.first, it.second, percent(it.second, total))
    }
    return ProgressDistributionChartState(title, segments, distributionSummary(title, segments, copy))
}

private fun distributionSummary(
    title: String,
    segments: List<ProgressDistributionSegmentState>,
    copy: StatsDashboardCopy,
): String = "$title. " + segments.joinToString { "${it.label} ${it.value}, ${it.percent} ${copy.percentWord}" }

private fun ladderRows(metric: StudyStatsStore.LadderHealthMetric, copy: StatsDashboardCopy): List<ProgressLevelRowState> {
    val total = metric.rungCounts.values.sum()
    return metric.rungCounts.entries.sortedBy { it.key.ordinal }.map { (rung, count) ->
        ProgressLevelRowState(copy.rung(rung.wireName()), count, total, percent(count, total))
    }
}

private fun adaptiveRows(
    metric: StudyStatsStore.AdaptiveHealthMetric,
    total: Int,
    legacyTransitionCount: Int,
    copy: StatsDashboardCopy,
): List<ProgressLevelRowState> {
    val rows = mutableListOf(
        ProgressLevelRowState(
            copy.coreSkill(CoreSkill.RECOGNITION),
            metric.countFor(CoreSkill.RECOGNITION),
            total,
            percent(metric.countFor(CoreSkill.RECOGNITION), total),
        ),
        ProgressLevelRowState(
            copy.coreSkill(CoreSkill.CONTEXTUAL_READING),
            metric.countFor(CoreSkill.CONTEXTUAL_READING),
            total,
            percent(metric.countFor(CoreSkill.CONTEXTUAL_READING), total),
        ),
    )
    listOf(
        "active_repair" to metric.activeRepairCount,
        "revalidation" to metric.revalidationPendingCount,
        "escalation_risk" to metric.escalationRiskCount,
        "stuck_repair" to metric.stuckRepairCount,
        "legacy_transition" to legacyTransitionCount,
    ).filter { it.second > 0 }.forEach { (status, count) ->
        rows += ProgressLevelRowState(copy.adaptiveStatus(status), count, total, percent(count, total))
    }
    return rows
}

private fun cumulativeSevenDayDelta(points: List<StatsCacheStore.CumulativeKanjiSnapshot>, nowMillis: Long): Int {
    if (points.isEmpty()) return 0
    val cutoff = LocalDayPolicy.moveLocalDays(LocalDayPolicy.localDayStart(nowMillis), -7)
    val current = points.last().cumulativeCount
    val before = points.lastOrNull { it.dayStartMillis < cutoff }?.cumulativeCount ?: 0
    return (current - before).coerceAtLeast(0)
}

private fun forecastState(
    snapshot: StatsCacheStore.Snapshot,
    dashboardCopy: StatsDashboardCopy,
): ProgressForecastState? {
    val forecast = snapshot.ladderForecast ?: return null
    if (forecast.totalItems < 1) return null
    val copy = ForecastTextCopy.forLocale()
    val completion = forecast.projectedCompletionMonthMillis?.let { StatsValueFormatter.date(it, "MMMM yyyy") } ?: copy.beyondHorizon
    val shown = forecast.burnDown
    return ProgressForecastState(
        totalItems = forecast.totalItems,
        headline = copy.headline.format(forecast.totalItems, completion),
        assumption = copy.assumption,
        burnDown = lineChart(
            dashboardCopy.itemsRemaining,
            shown.map { StatsValueFormatter.date(it.monthStartMillis, "MMM") },
            listOf(ProgressSeriesState(dashboardCopy.remaining, shown.map { it.remainingItems })),
            dashboardCopy.forecastSummary(
                forecast.totalItems,
                shown.lastOrNull()?.remainingItems ?: forecast.totalItems,
            ),
        ),
    )
}

private fun weaknessRows(
    impact: KanjiImpactAnalyzer.Report,
    copy: StatsDashboardCopy,
): List<ProgressWeaknessRowState> {
    if (impact.rows.isNotEmpty()) return impact.rows.take(4).map { row ->
        val accuracy = (row.currentRetention * 100).roundToInt().coerceIn(0, 100)
        ProgressWeaknessRowState(
            row.kanji,
            accuracy,
            row.reviewCount.coerceAtLeast(0),
            copy.impactSeverity(row.bucket),
        )
    }
    return emptyList()
}

private fun mostMissedKanji(mistakes: List<StudyStatsStore.RecentMistake>): List<ProgressMissedKanjiState> = mistakes
    .filter { it.kanji.isNotBlank() }.groupingBy { it.kanji }.eachCount().entries
    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(5)
    .map { ProgressMissedKanjiState(it.key, it.value) }

private fun supportNeeded(
    outcome: StudyStatsStore.KaniOutcomeStats,
    copy: StatsDashboardCopy,
): List<ProgressSupportNeedState> =
    outcome.matureSupportGained.examples.take(4).map {
        ProgressSupportNeedState(it.kanji, copy.matureSupport, it.afterMatureSupport.coerceAtLeast(0))
    }

private fun focusScore(impact: KanjiImpactAnalyzer.Report): Int {
    val total = impact.helpedCount + impact.notHelpingCount + impact.needsMoreCardsCount
    return if (total == 0) 0 else percent(impact.helpedCount, total)
}

private fun percent(value: Int, total: Int): Int =
    if (total <= 0) 0 else (value * 100.0 / total).roundToInt().coerceIn(0, 100)

private fun deltaLabel(current: Int, previous: Int, copy: StatsDashboardCopy, thirtyDayWindow: Boolean = false): String? {
    if (current == 0 && previous == 0) return null
    if (previous == 0) return if (thirtyDayWindow) copy.deltaVsPreviousThirty("+${formatInt(current)}") else copy.deltaVsPreviousSeven("+${formatInt(current)}")
    val delta = ((current - previous) * 100.0 / previous).roundToInt()
    val value = "${if (delta >= 0) "+" else ""}${formatInt(delta)}%"
    return if (thirtyDayWindow) copy.deltaVsPreviousThirty(value) else copy.deltaVsPreviousSeven(value)
}

private fun formatInt(value: Int): String = StatsValueFormatter.integer(value, Locale.getDefault())
private fun dayLabel(millis: Long, pattern: String): String = StatsValueFormatter.date(millis, pattern, Locale.getDefault())
