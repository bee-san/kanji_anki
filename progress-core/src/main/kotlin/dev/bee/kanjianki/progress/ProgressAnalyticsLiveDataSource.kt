package dev.bee.kanjianki.progress

import dev.bee.kanjianki.core.ChartAxisPolicy
import dev.bee.kanjianki.core.ConfusionInsightPolicy
import dev.bee.kanjianki.core.CoreSkill
import dev.bee.kanjianki.core.ForecastTextCopy
import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.ReviewHeatmapPolicy
import dev.bee.kanjianki.core.StatsValueFormatter
import dev.bee.kanjianki.core.StatsDashboardCopy
import dev.bee.kanjianki.core.TaskTypeAccuracyPolicy
import dev.bee.kanjianki.data.AdaptiveHealthSnapshot
import dev.bee.kanjianki.data.CumulativeKanjiSnapshot
import dev.bee.kanjianki.data.KaniOutcomeSnapshot
import dev.bee.kanjianki.data.LadderHealthSnapshot
import dev.bee.kanjianki.data.RecentMistakeSnapshot
import dev.bee.kanjianki.data.ReviewDaySummarySnapshot
import dev.bee.kanjianki.data.StatsSnapshot
import java.text.NumberFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.ceil
import kotlin.math.roundToInt

interface ProgressAnalyticsStatsSource {
    fun cachedStatsSnapshotOrNull(nowMillis: Long): StatsSnapshot?
    fun recomputeStatsSnapshotSynchronously(nowMillis: Long): StatsSnapshot
    fun reviewDaySummaries(nowMillis: Long, days: Int): List<ReviewDaySummary>
}

private const val CACHED_REVIEW_DAY_COUNT = 366
private val FORECAST_TIME_ZONE: TimeZone = TimeZone.getTimeZone("UTC")

fun progressAnalyticsSnapshot(
    snapshot: StatsSnapshot,
    nowMillis: Long = System.currentTimeMillis(),
    ladderSettings: RecordsBase.StudyLadderSettings,
): ProgressAnalyticsState = progressAnalyticsSnapshot(
    source = object : ProgressAnalyticsStatsSource {
        override fun cachedStatsSnapshotOrNull(nowMillis: Long): StatsSnapshot = snapshot
        override fun recomputeStatsSnapshotSynchronously(nowMillis: Long): StatsSnapshot = snapshot
        override fun reviewDaySummaries(nowMillis: Long, days: Int): List<ReviewDaySummary> =
            emptyList()
    },
    nowMillis = nowMillis,
    ladderSettings = ladderSettings,
)

fun progressAnalyticsSnapshot(
    source: ProgressAnalyticsStatsSource,
    nowMillis: Long = System.currentTimeMillis(),
    scheduleRefresh: (() -> Unit)? = null,
    ladderSettings: RecordsBase.StudyLadderSettings,
): ProgressAnalyticsState {
    val locale = Locale.getDefault()
    val timeZone = TimeZone.getDefault()
    val fresh = source.cachedStatsSnapshotOrNull(nowMillis)
    val snapshot = if (fresh != null) {
        fresh
    } else {
        // A current-format cache can still be stale after sync or study mutations. Rendering
        // it would leave Stats disagreeing with live Home/Focus state until another refresh.
        source.recomputeStatsSnapshotSynchronously(nowMillis)
    }
    val copy = StatsDashboardCopy.forLocale(locale)
    if (fresh != null && snapshot.reviewDaySummaries.isEmpty()) scheduleRefresh?.invoke()

    val reviewDaysYear = canonicalReviewDays(snapshot.reviewDaySummaries, nowMillis, timeZone)
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
    val reviewRangeData = rangeDays.mapValues { (range, days) ->
        reviewsRangeData(range, days, copy, locale, timeZone)
    }
    val accuracyRangeData = rangeDays.mapValues { (range, days) ->
        accuracyTrendChart(range, days, nowMillis, copy, locale, timeZone)
    }

    val impactStats = snapshot.studyImpactStats
    val outcome = snapshot.outcomeStats
    val impact = snapshot.impactReport
    val streak = snapshot.studyStreak
    val taskTime = snapshot.studyTaskTimeStats
    val total30 = reviewDays30.saturatingLongCountSum { it.total }
    val correct30 = reviewDays30.saturatingLongCountSum { it.correct() }
    val accuracy30 = percent(correct30, total30)
    val previousTotal30 = reviewDays30Previous.saturatingLongCountSum { it.total }
    val previousAccuracy = percent(
        reviewDays30Previous.saturatingLongCountSum { it.correct() },
        previousTotal30,
    )
    val accuracyDelta = if (previousTotal30 > 0) {
        accuracyDeltaLabel(accuracy30, previousAccuracy, copy, locale)
    } else null
    val taskRangeStart = reviewDays30.first().dayStart
    val taskRangeEnd = LocalDayPolicy.nextLocalDayStart(nowMillis, timeZone)
    val taskRows30 = snapshot.taskTypeDaySummaries.filter {
        it.dayStartMillis >= taskRangeStart && it.dayStartMillis < taskRangeEnd
    }
    val groupedAccuracy = TaskTypeAccuracyPolicy.summarize(taskRows30.map {
        TaskTypeAccuracyPolicy.Summary(it.taskType, it.correct, it.total)
    })
    val reviewShare = distributionFromAccuracy(groupedAccuracy, copy)
    val cumulative = canonicalCumulative(snapshot.cumulativeKanjiPracticed, nowMillis, timeZone)
    val cumulativeChart = cumulativeChart(cumulative, nowMillis, copy, locale, timeZone)
    val practicedDelta = cumulativeSevenDayDelta(cumulative, nowMillis, timeZone)
    val adaptiveHealth = outcome.adaptiveHealth
    val adaptiveItemCount = adaptiveHealth.totalAdaptiveItems.coerceAtLeast(0)
    val legacyActiveTotal = outcome.ladderHealth.totalActiveItems.coerceAtLeast(0)
    val usesAdaptiveHealth = adaptiveItemCount > 0
    val adaptiveProgressTotal = maxOf(adaptiveItemCount, legacyActiveTotal)
    val legacyTransitionCount = adaptiveProgressTotal - adaptiveItemCount
    val progressRows = if (usesAdaptiveHealth) {
        adaptiveRows(adaptiveHealth, adaptiveProgressTotal, legacyTransitionCount, copy)
    } else {
        ladderRows(outcome.ladderHealth, ladderSettings, copy)
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
        reviewDaysYear.map { ReviewHeatmapPolicy.DaySummary(it.dayStart, it.total.toDisplayCount()) },
        nowMillis,
        timeZone,
        locale,
    )
    val currentStreakDays = streak.currentDays.coerceAtLeast(0)
    val bestStreakDays = streak.bestDays.coerceAtLeast(currentStreakDays)
    val reviewsToday = streak.reviewsToday.coerceAtLeast(0)
    val legacyProgressCount = outcome.ladderHealth.rungCounts.values.saturatingCountSum()
    val contextualCompleteCount = adaptiveHealth.contextualCompleteCount.coerceIn(0, adaptiveProgressTotal)
    val focusScore = focusScore(impact)
    val focusScoreAvailable = impactCount(impact) > 0
    val totalReviews = impactStats.totalReviews.coerceAtLeast(0)
    val distinctReviewedKanji = impactStats.distinctReviewedKanji.coerceAtLeast(0)
    val learnedKanji = cumulative.lastOrNull()?.cumulativeCount ?: distinctReviewedKanji
    val answeredTasks = taskTime.answeredTasks.coerceAtLeast(0)
    val lastSevenDaysMillis = taskTime.lastSevenDaysMillis.coerceAtLeast(0L)
    val todayMillis = taskTime.todayMillis.coerceAtLeast(0L)

    val state = ProgressAnalyticsState(
        generatedAtMillis = snapshot.generatedAtMillis,
        overview = ProgressOverviewState(
            title = copy.statsOverview,
            subtitle = copy.overviewSubtitle,
            totalReviews = ProgressCountMetricState(
                totalReviews, formatInt(totalReviews, locale),
                deltaLabel(
                    reviewDays7.saturatingLongCountSum { it.total },
                    reviewDays7Previous.saturatingLongCountSum { it.total },
                    copy,
                    locale,
                ),
                copy.allReviews,
            ),
            accuracy = ProgressCountMetricState(
                accuracy30, "$accuracy30%", accuracyDelta, copy.thirtyDayAccuracy,
            ),
            currentStreak = ProgressStreakMetricState(
                currentStreakDays, bestStreakDays, copy.days(currentStreakDays),
                if (streak.studiedToday) copy.studiedToday else copy.keepStreakAlive,
            ),
            kanjiLearned = ProgressCountMetricState(
                learnedKanji,
                formatInt(learnedKanji, locale),
                practicedDelta.takeIf { it > 0 }?.let { copy.thisWeekDelta(formatInt(it, locale)) },
                copy.distinctKanjiPracticed,
            ),
            focusSessions = ProgressCountMetricState(
                answeredTasks,
                formatInt(answeredTasks, locale),
                detailLabel = copy.lastSevenDays,
            ),
            studyTime = ProgressDurationMetricState(
                lastSevenDaysMillis,
                StatsValueFormatter.duration(lastSevenDaysMillis, locale),
                todayMillis.takeIf { it > 0 }?.let { copy.todayDelta(StatsValueFormatter.duration(it, locale)) },
                copy.thisWeek,
            ),
            reviewsToday = ProgressCountMetricState(reviewsToday, formatInt(reviewsToday, locale)),
            reviewsOverTime = volumeChart(reviewDays30, copy, locale, timeZone),
            cardTypeBreakdown = ProgressDistributionChartState(
                copy.reviewShare, reviewShare,
                distributionSummary(copy.reviewShare, reviewShare, copy, locale),
            ),
            correctIncorrectBreakdown = distribution(
                copy.correctVsIncorrect,
                listOf(copy.correct to correct30, copy.incorrect to (total30 - correct30).coerceAtLeast(0L)),
                copy,
                locale,
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
            currentStreak = ProgressStreakMetricState(
                currentStreakDays,
                bestStreakDays,
                copy.days(currentStreakDays),
                copy.bestDays(bestStreakDays),
            ),
            tip = when {
                streak.studiedToday -> copy.streakSafeTip
                currentStreakDays > 0 -> copy.keepStreakTip
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
            retentionSummary = groupedAccuracy.joinToString(localizedListSeparator(locale)) {
                "${copy.group(it.group)} ${it.correct}/${it.total}"
            },
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
                if (usesAdaptiveHealth) contextualCompleteCount else legacyProgressCount,
                if (usesAdaptiveHealth) adaptiveProgressTotal else legacyActiveTotal,
                if (usesAdaptiveHealth) {
                    percent(contextualCompleteCount, adaptiveProgressTotal)
                } else {
                    percent(legacyProgressCount, legacyActiveTotal)
                },
                if (usesAdaptiveHealth) {
                    copy.contextualComplete(contextualCompleteCount)
                } else {
                    copy.activeItems(legacyActiveTotal)
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
                    copy.activeItemsSummary(legacyActiveTotal)
                },
            ),
            levelRows = progressRows,
            cumulativeProgress = cumulativeChart,
        ),
        weaknessInsights = ProgressWeaknessInsightsState(
            title = copy.weaknessInsights,
            focusScore = ProgressScoreMetricState(
                focusScore, 100, copy.focusStatus(focusScore),
                "$focusScore / 100 · ${copy.focusStatus(focusScore)}",
            ),
            weaknessRows = weaknessRows(impact, copy),
            mostMissedKanji = mostMissedKanji(snapshot.recentMistakes),
            supportNeeded = supportNeeded(outcome, copy),
            confusionPairs = confusionPairs,
            focusScoreAvailable = focusScoreAvailable,
        ),
        forecast = forecastState(snapshot, copy, locale),
    )
    return state
}

data class ReviewDaySummary(
    val dayStart: Long,
    val total: Long,
    val again: Long,
    val hard: Long,
    val good: Long,
    val easy: Long,
    val writingRequired: Long,
    val writingFailed: Long,
) {
    fun correct(): Long {
        val safeTotal = total.coerceAtLeast(0L)
        return safeTotal - again.coerceIn(0, safeTotal)
    }

    fun accuracyPercent(): Int = percent(correct(), total.coerceAtLeast(0L))
}

private fun ReviewDaySummarySnapshot.toReviewDaySummary(
    normalizedDayStart: Long = dayStartMillis,
): ReviewDaySummary {
    val safeTotal = total.coerceAtLeast(0)
    val safeWritingRequired = writingRequired.coerceIn(0, safeTotal)
    return ReviewDaySummary(
        normalizedDayStart,
        safeTotal.toLong(),
        again.coerceIn(0, safeTotal).toLong(),
        hard.coerceIn(0, safeTotal).toLong(),
        good.coerceIn(0, safeTotal).toLong(),
        easy.coerceIn(0, safeTotal).toLong(),
        safeWritingRequired.toLong(),
        writingFailed.coerceIn(0, safeWritingRequired).toLong(),
    )
}

private fun canonicalReviewDays(
    snapshots: List<ReviewDaySummarySnapshot>,
    nowMillis: Long,
    timeZone: TimeZone,
): List<ReviewDaySummary> {
    val today = LocalDayPolicy.localDayStart(nowMillis, timeZone)
    val firstDay = LocalDayPolicy.moveLocalDays(today, -(CACHED_REVIEW_DAY_COUNT - 1), timeZone)
    val endExclusive = LocalDayPolicy.nextLocalDayStart(nowMillis, timeZone)
    val byDay = HashMap<Long, ReviewDaySummary>()
    snapshots.forEach { snapshot ->
        if (snapshot.dayStartMillis < firstDay || snapshot.dayStartMillis >= endExclusive) return@forEach
        val dayStart = LocalDayPolicy.localDayStart(snapshot.dayStartMillis, timeZone)
        val normalized = snapshot.toReviewDaySummary(dayStart)
        byDay[dayStart] = byDay[dayStart]?.merge(normalized) ?: normalized
    }
    return (0 until CACHED_REVIEW_DAY_COUNT).map { offset ->
        val dayStart = LocalDayPolicy.moveLocalDays(firstDay, offset, timeZone)
        byDay[dayStart] ?: ReviewDaySummary(dayStart, 0L, 0L, 0L, 0L, 0L, 0L, 0L)
    }
}

private fun ReviewDaySummary.merge(other: ReviewDaySummary): ReviewDaySummary = ReviewDaySummary(
    dayStart = dayStart,
    total = saturatingLongAdd(total, other.total),
    again = saturatingLongAdd(again, other.again),
    hard = saturatingLongAdd(hard, other.hard),
    good = saturatingLongAdd(good, other.good),
    easy = saturatingLongAdd(easy, other.easy),
    writingRequired = saturatingLongAdd(writingRequired, other.writingRequired),
    writingFailed = saturatingLongAdd(writingFailed, other.writingFailed),
)

private fun reviewsRangeData(
    range: AnalyticsRange,
    days: List<ReviewDaySummary>,
    copy: StatsDashboardCopy,
    locale: Locale,
    timeZone: TimeZone,
): ProgressReviewsRangeData {
    val total = days.saturatingLongCountSum { it.total }
    val correct = days.saturatingLongCountSum { it.correct() }
    val incorrect = (total - correct).coerceAtLeast(0L)
    val average = if (days.isEmpty()) 0 else (total / days.size.toDouble()).roundToInt().coerceAtLeast(0)
    val labelPattern = if (range == AnalyticsRange.SEVEN_DAYS) "EEE" else monthDayPattern(locale)
    val chartDays = if (range == AnalyticsRange.SEVEN_DAYS) days else bucketSummaries(days, 10)
    val best = days.maxWithOrNull(compareBy<ReviewDaySummary> { it.total }.thenByDescending { it.dayStart })
    val summary = copy.reviewSummary(
        range.days,
        formatCount(total, locale),
        formatInt(average, locale),
        formatCount(correct, locale),
        formatCount(incorrect, locale),
    )
    return ProgressReviewsRangeData(
        reviewsPerDay = ProgressBarChartState(
            copy.reviewsPerDay,
            chartDays.map { dayLabel(it.dayStart, labelPattern, locale, timeZone) },
            chartDays.map { it.total.toDisplayCount() },
            summary,
            range,
        ),
        totalReviews = ProgressCountMetricState(total.toDisplayCount(), formatCount(total, locale)),
        averagePerDay = ProgressCountMetricState(average, formatInt(average, locale)),
        correct = ProgressCountMetricState(correct.toDisplayCount(), formatCount(correct, locale)),
        incorrect = ProgressCountMetricState(incorrect.toDisplayCount(), formatCount(incorrect, locale)),
        bestDayLabel = best?.takeIf { it.total > 0 }
            ?.let { dayLabel(it.dayStart, labelPattern, locale, timeZone) }
            ?: copy.noData,
        accessibilitySummary = summary,
    )
}

private fun accuracyTrendChart(
    range: AnalyticsRange,
    days: List<ReviewDaySummary>,
    nowMillis: Long,
    copy: StatsDashboardCopy,
    locale: Locale,
    timeZone: TimeZone,
): ProgressLineChartState {
    val buckets = bucketSummaries(days, if (range == AnalyticsRange.SEVEN_DAYS) 7 else 6)
    val values = buckets.map { it.accuracyPercent() }
    val total = days.saturatingLongCountSum { it.total }
    val accuracy = percent(days.saturatingLongCountSum { it.correct() }, total)
    val pattern = monthDayPattern(locale)
    return lineChart(
        title = copy.accuracyOverTime,
        labels = buckets.map { dayLabel(it.dayStart, pattern, locale, timeZone) },
        series = if (total == 0L) emptyList() else listOf(ProgressSeriesState(copy.accuracyPercent, values)),
        summary = copy.accuracySummary(
            range.days,
            accuracy,
            dayLabel(days.lastOrNull()?.dayStart ?: nowMillis, pattern, locale, timeZone),
        ),
        range = range,
        tooltip = buckets.lastOrNull()?.let {
            accuracyTooltip(dayLabel(it.dayStart, pattern, locale, timeZone), it.accuracyPercent(), locale)
        },
    )
}

private fun volumeChart(
    days: List<ReviewDaySummary>,
    copy: StatsDashboardCopy,
    locale: Locale,
    timeZone: TimeZone,
): ProgressLineChartState {
    val buckets = bucketSummaries(days, 6)
    val pattern = monthDayPattern(locale)
    return lineChart(
        copy.reviewsOverTime, buckets.map { dayLabel(it.dayStart, pattern, locale, timeZone) },
        if (buckets.saturatingLongCountSum { it.total } == 0L) {
            emptyList()
        } else {
            listOf(ProgressSeriesState(copy.reviews, buckets.map { it.total.toDisplayCount() }))
        },
        copy.volumeSummary(), AnalyticsRange.THIRTY_DAYS,
        buckets.lastOrNull()?.let {
            copy.reviewsTooltip(
                dayLabel(it.dayStart, pattern, locale, timeZone),
                it.total.toDisplayCount(),
            )
        },
    )
}

private fun cumulativeChart(
    points: List<CumulativeKanjiSnapshot>,
    nowMillis: Long,
    copy: StatsDashboardCopy,
    locale: Locale,
    timeZone: TimeZone,
): ProgressLineChartState {
    val rangeStart = LocalDayPolicy.moveLocalDays(
        LocalDayPolicy.localDayStart(nowMillis, timeZone),
        -(AnalyticsRange.NINETY_DAYS.days - 1),
        timeZone,
    )
    val shown = sampleCumulativeLocalDays(
        points = points,
        rangeStart = rangeStart,
        dayCount = AnalyticsRange.NINETY_DAYS.days,
        maximumSize = 12,
        timeZone = timeZone,
    )
    val pattern = monthDayPattern(locale)
    return lineChart(
        copy.cumulativePracticed,
        shown.map { dayLabel(it.dayStartMillis, pattern, locale, timeZone) },
        if (shown.isEmpty()) emptyList() else listOf(ProgressSeriesState(copy.practicedKanji, shown.map { it.cumulativeCount })),
        copy.cumulativeSummary(),
        AnalyticsRange.NINETY_DAYS,
        shown.lastOrNull()?.let {
            copy.practicedTooltip(dayLabel(it.dayStartMillis, pattern, locale, timeZone), it.cumulativeCount)
        },
    )
}

private fun sampleCumulativeLocalDays(
    points: List<CumulativeKanjiSnapshot>,
    rangeStart: Long,
    dayCount: Int,
    maximumSize: Int,
    timeZone: TimeZone,
): List<CumulativeKanjiSnapshot> {
    if (maximumSize <= 0 || dayCount <= 0 || points.isEmpty()) return emptyList()
    val sampleCount = minOf(maximumSize, dayCount)
    val lastDayOffset = dayCount - 1L
    var pointIndex = 0
    var cumulativeCount = 0
    return List(sampleCount) { sampleIndex ->
        val dayOffset = if (sampleCount == 1) {
            lastDayOffset
        } else {
            sampleIndex.toLong() * lastDayOffset / (sampleCount - 1L)
        }
        val sampleDay = LocalDayPolicy.moveLocalDays(rangeStart, dayOffset.toInt(), timeZone)
        while (pointIndex < points.size && points[pointIndex].dayStartMillis <= sampleDay) {
            cumulativeCount = points[pointIndex].cumulativeCount
            pointIndex += 1
        }
        CumulativeKanjiSnapshot(sampleDay, cumulativeCount)
    }
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
            slice.last().dayStart,
            slice.saturatingLongCountSum { it.total },
            slice.saturatingLongCountSum { it.again },
            slice.saturatingLongCountSum { it.hard },
            slice.saturatingLongCountSum { it.good },
            slice.saturatingLongCountSum { it.easy },
            slice.saturatingLongCountSum { it.writingRequired },
            slice.saturatingLongCountSum { it.writingFailed },
        )
    }
}

private fun distributionFromAccuracy(groups: List<TaskTypeAccuracyPolicy.Accuracy>, copy: StatsDashboardCopy): List<ProgressDistributionSegmentState> {
    val total = groups.saturatingLongCountSum { it.total.toLong() }
    if (total == 0L) return emptyList()
    return groups.map {
        val count = it.total.coerceAtLeast(0)
        ProgressDistributionSegmentState(copy.group(it.group), count, percent(count.toLong(), total))
    }
}

private fun distribution(
    title: String,
    values: List<Pair<String, Long>>,
    copy: StatsDashboardCopy,
    locale: Locale,
): ProgressDistributionChartState {
    val total = values.saturatingLongCountSum { it.second }
    val segments = if (total == 0L) emptyList() else values.filter { it.second > 0L }.map {
        ProgressDistributionSegmentState(it.first, it.second.toDisplayCount(), percent(it.second, total))
    }
    return ProgressDistributionChartState(title, segments, distributionSummary(title, segments, copy, locale))
}

private fun distributionSummary(
    title: String,
    segments: List<ProgressDistributionSegmentState>,
    copy: StatsDashboardCopy,
    locale: Locale,
): String {
    if (segments.isEmpty()) return if (isJapanese(locale)) "$title。" else "$title."
    return if (isJapanese(locale)) {
        "$title。" + segments.joinToString("、") {
            "${it.label} ${formatInt(it.value, locale)}、${it.percent}${copy.percentWord}"
        }
    } else {
        "$title. " + segments.joinToString {
            "${it.label} ${formatInt(it.value, locale)}, ${it.percent} ${copy.percentWord}"
        }
    }
}

private fun ladderRows(
    metric: LadderHealthSnapshot,
    ladderSettings: RecordsBase.StudyLadderSettings,
    copy: StatsDashboardCopy,
): List<ProgressLevelRowState> {
    val total = metric.rungCounts.values.saturatingCountSum()
    return ladderSettings.orderedRungs.map { rung ->
        val count = metric.countFor(rung).coerceIn(0, total)
        ProgressLevelRowState(copy.rung(rung.wireName()), count, total, percent(count, total))
    }
}

private fun adaptiveRows(
    metric: AdaptiveHealthSnapshot,
    total: Int,
    legacyTransitionCount: Int,
    copy: StatsDashboardCopy,
): List<ProgressLevelRowState> {
    val recognitionCount = metric.countFor(CoreSkill.RECOGNITION).coerceIn(0, total)
    val contextualCount = metric.countFor(CoreSkill.CONTEXTUAL_READING).coerceIn(0, total)
    val rows = mutableListOf(
        ProgressLevelRowState(
            copy.coreSkill(CoreSkill.RECOGNITION),
            recognitionCount,
            total,
            percent(recognitionCount, total),
        ),
        ProgressLevelRowState(
            copy.coreSkill(CoreSkill.CONTEXTUAL_READING),
            contextualCount,
            total,
            percent(contextualCount, total),
        ),
    )
    listOf(
        "active_repair" to metric.activeRepairCount,
        "revalidation" to metric.revalidationPendingCount,
        "escalation_risk" to metric.escalationRiskCount,
        "stuck_repair" to metric.stuckRepairCount,
        "legacy_transition" to legacyTransitionCount,
    ).filter { it.second > 0 }.forEach { (status, count) ->
        val normalized = count.coerceIn(0, total)
        rows += ProgressLevelRowState(copy.adaptiveStatus(status), normalized, total, percent(normalized, total))
    }
    return rows
}

private fun canonicalCumulative(
    points: List<CumulativeKanjiSnapshot>,
    nowMillis: Long,
    timeZone: TimeZone,
): List<CumulativeKanjiSnapshot> {
    val endExclusive = LocalDayPolicy.nextLocalDayStart(nowMillis, timeZone)
    val byDay = HashMap<Long, Int>()
    points.forEach { point ->
        if (point.dayStartMillis >= endExclusive) return@forEach
        val dayStart = LocalDayPolicy.localDayStart(point.dayStartMillis, timeZone)
        byDay[dayStart] = maxOf(byDay[dayStart] ?: 0, point.cumulativeCount.coerceAtLeast(0))
    }
    var runningMaximum = 0
    return byDay.entries.sortedBy { it.key }.map { (dayStart, count) ->
        runningMaximum = maxOf(runningMaximum, count)
        CumulativeKanjiSnapshot(dayStart, runningMaximum)
    }
}

private fun cumulativeSevenDayDelta(
    points: List<CumulativeKanjiSnapshot>,
    nowMillis: Long,
    timeZone: TimeZone,
): Int {
    if (points.isEmpty()) return 0
    val cutoff = LocalDayPolicy.moveLocalDays(
        LocalDayPolicy.localDayStart(nowMillis, timeZone),
        -(AnalyticsRange.SEVEN_DAYS.days - 1),
        timeZone,
    )
    val current = points.last().cumulativeCount
    val before = points.lastOrNull { it.dayStartMillis < cutoff }?.cumulativeCount ?: 0
    return (current.toLong() - before.toLong()).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
}

private fun forecastState(
    snapshot: StatsSnapshot,
    dashboardCopy: StatsDashboardCopy,
    locale: Locale,
): ProgressForecastState? {
    val forecast = snapshot.ladderForecast ?: return null
    if (forecast.totalItems < 1) return null
    val copy = ForecastTextCopy.forLocale(locale)
    val completion = forecast.projectedCompletionMonthMillis?.let {
        StatsValueFormatter.date(it, forecastCompletionPattern(locale), locale, FORECAST_TIME_ZONE)
    } ?: copy.beyondHorizon
    val shown = forecast.burnDown.map {
        it.copy(
            completedItems = it.completedItems.coerceIn(0, forecast.totalItems),
            remainingItems = it.remainingItems.coerceIn(0, forecast.totalItems),
        )
    }
    return ProgressForecastState(
        totalItems = forecast.totalItems,
        headline = String.format(locale, copy.headline, forecast.totalItems, completion),
        assumption = copy.assumption,
        burnDown = lineChart(
            dashboardCopy.itemsRemaining,
            shown.map {
                StatsValueFormatter.date(it.monthStartMillis, forecastMonthPattern(locale), locale, FORECAST_TIME_ZONE)
            },
            if (shown.isEmpty()) {
                emptyList()
            } else {
                listOf(ProgressSeriesState(dashboardCopy.remaining, shown.map { it.remainingItems }))
            },
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
        val accuracy = ((row.currentRetention.takeIf { it.isFinite() } ?: 0.0) * 100)
            .roundToInt()
            .coerceIn(0, 100)
        ProgressWeaknessRowState(
            row.kanji,
            accuracy,
            row.reviewCount.coerceAtLeast(0),
            copy.impactSeverity(row.bucket),
        )
    }
    return emptyList()
}

private fun mostMissedKanji(mistakes: List<RecentMistakeSnapshot>): List<ProgressMissedKanjiState> = mistakes
    .filter { it.kanji.isNotBlank() }.groupingBy { it.kanji }.eachCount().entries
    .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).take(5)
    .map { ProgressMissedKanjiState(it.key, it.value) }

private fun supportNeeded(
    outcome: KaniOutcomeSnapshot,
    copy: StatsDashboardCopy,
): List<ProgressSupportNeedState> =
    outcome.matureSupportGained.examples.take(4).map {
        ProgressSupportNeedState(it.kanji, copy.matureSupport, it.afterMatureSupport.coerceAtLeast(0))
    }

private fun impactCount(impact: KanjiImpactAnalyzer.Report): Int = listOf(
    impact.helpedCount,
    impact.notHelpingCount,
    impact.needsMoreCardsCount,
).saturatingCountSum()

private fun focusScore(impact: KanjiImpactAnalyzer.Report): Int {
    val total = impactCount(impact)
    return if (total == 0) 0 else percent(impact.helpedCount, total)
}

private fun percent(value: Int, total: Int): Int =
    if (total <= 0) 0 else (value * 100.0 / total).roundToInt().coerceIn(0, 100)

private fun percent(value: Long, total: Long): Int {
    if (total <= 0L) return 0
    val normalized = value.coerceIn(0L, total)
    return (normalized.toDouble() * 100.0 / total.toDouble()).roundToInt().coerceIn(0, 100)
}

private fun accuracyDeltaLabel(
    current: Int,
    previous: Int,
    copy: StatsDashboardCopy,
    locale: Locale,
): String {
    val delta = current - previous
    val value = "${if (delta >= 0) "+" else ""}${formatInt(delta, locale)}%"
    return copy.deltaVsPreviousThirty(value)
}

private fun deltaLabel(
    current: Long,
    previous: Long,
    copy: StatsDashboardCopy,
    locale: Locale,
): String? {
    if (current == 0L && previous == 0L) return null
    if (previous == 0L) return copy.deltaVsPreviousSeven("+${formatCount(current, locale)}")
    val delta = ((current.toDouble() - previous.toDouble()) * 100.0 / previous.toDouble()).roundToInt()
    val value = "${if (delta >= 0) "+" else ""}${formatInt(delta, locale)}%"
    return copy.deltaVsPreviousSeven(value)
}

private fun saturatingLongAdd(left: Long, right: Long): Long {
    val safeLeft = left.coerceAtLeast(0L)
    val safeRight = right.coerceAtLeast(0L)
    return if (safeLeft > Long.MAX_VALUE - safeRight) Long.MAX_VALUE else safeLeft + safeRight
}

private inline fun <T> Iterable<T>.saturatingLongCountSum(selector: (T) -> Long): Long {
    var sum = 0L
    for (item in this) {
        sum = saturatingLongAdd(sum, selector(item))
        if (sum == Long.MAX_VALUE) break
    }
    return sum
}

private fun saturatingCountAdd(left: Int, right: Int): Int =
    (left.coerceAtLeast(0).toLong() + right.coerceAtLeast(0).toLong())
        .coerceAtMost(Int.MAX_VALUE.toLong())
        .toInt()

private inline fun <T> Iterable<T>.saturatingCountSum(selector: (T) -> Int): Int {
    var sum = 0
    for (item in this) {
        sum = saturatingCountAdd(sum, selector(item))
        if (sum == Int.MAX_VALUE) break
    }
    return sum
}

private fun Iterable<Int>.saturatingCountSum(): Int = saturatingCountSum { it }

private fun Long.toDisplayCount(): Int = coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()

private fun formatInt(value: Int, locale: Locale): String = StatsValueFormatter.integer(value, locale)

private fun formatCount(value: Long, locale: Locale): String =
    NumberFormat.getIntegerInstance(locale).format(value.coerceAtLeast(0L))

private fun dayLabel(millis: Long, pattern: String, locale: Locale, timeZone: TimeZone): String =
    StatsValueFormatter.date(millis, pattern, locale, timeZone)

private fun monthDayPattern(locale: Locale): String = if (isJapanese(locale)) "M月d日" else "MMM d"

private fun forecastCompletionPattern(locale: Locale): String = if (isJapanese(locale)) "yyyy年M月" else "MMMM yyyy"

private fun forecastMonthPattern(locale: Locale): String = if (isJapanese(locale)) "M月" else "MMM"

private fun accuracyTooltip(date: String, accuracy: Int, locale: Locale): String =
    if (isJapanese(locale)) "$date、$accuracy%" else "$date, $accuracy%"

private fun localizedListSeparator(locale: Locale): String = if (isJapanese(locale)) "、" else ", "

private fun isJapanese(locale: Locale): Boolean = locale.language == Locale.JAPANESE.language
