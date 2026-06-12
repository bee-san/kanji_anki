package dev.bee.kanjianki.progress

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.data.StatsCacheStore
import dev.bee.kanjianki.data.StudyStatsStore
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private const val SIMILAR_KANJI_LABEL = "Similar kanji"

internal interface ProgressAnalyticsStatsSource {
    fun cachedStatsSnapshotOrNull(): StatsCacheStore.Snapshot?
    fun latestStatsSnapshotOrNull(): StatsCacheStore.Snapshot?
    fun recomputeStatsSnapshotSynchronously(nowMillis: Long): StatsCacheStore.Snapshot
    fun reviewDaySummaries(nowMillis: Long, days: Int): List<ReviewDaySummary>
}

internal fun progressAnalyticsSnapshot(store: LocalStore, nowMillis: Long = System.currentTimeMillis()): ProgressAnalyticsState {
    return progressAnalyticsSnapshot(
        source = object : ProgressAnalyticsStatsSource {
            override fun cachedStatsSnapshotOrNull(): StatsCacheStore.Snapshot? {
                return store.cachedStatsSnapshotOrNull()
            }

            override fun latestStatsSnapshotOrNull(): StatsCacheStore.Snapshot? {
                return store.latestStatsSnapshotOrNull()
            }

            override fun recomputeStatsSnapshotSynchronously(nowMillis: Long): StatsCacheStore.Snapshot {
                return store.recomputeStatsSnapshotSynchronously(nowMillis)
            }

            override fun reviewDaySummaries(nowMillis: Long, days: Int): List<ReviewDaySummary> {
                return store.reviewDaySummaries(nowMillis, days)
            }
        },
        nowMillis = nowMillis,
    )
}

internal fun progressAnalyticsSnapshot(source: ProgressAnalyticsStatsSource, nowMillis: Long = System.currentTimeMillis()): ProgressAnalyticsState {
    val snapshot = source.cachedStatsSnapshotOrNull() ?: source.recomputeStatsSnapshotSynchronously(nowMillis)
    val reviewDays30 = source.reviewDaySummaries(nowMillis, 30)
    val reviewDays14 = source.reviewDaySummaries(nowMillis, 14)
    val reviewDays7 = reviewDays14.takeLast(7)
    val reviewDays7Prev = reviewDays14.take(7)
    val reviewBuckets30 = bucketSummaries(reviewDays30, 6)
    val reviewBuckets30Accuracy = reviewBuckets30.map { bucket ->
        val total = bucket.total.coerceAtLeast(0)
        val correct = (bucket.total - bucket.again).coerceAtLeast(0)
        val percent = if (total == 0) 0 else ((correct * 100.0) / total.toDouble()).roundToInt()
        bucket.copy(total = percent, again = 0, hard = 0, good = 0, easy = 0, writingRequired = 0, writingFailed = 0)
    }

    val studyImpact = snapshot.studyImpactStats
    val streak = snapshot.studyStreak
    val taskTime = snapshot.studyTaskTimeStats
    val outcome = snapshot.outcomeStats
    val impact = snapshot.impactReport

    val totalReviews7 = reviewDays7.sumOf { it.total }
    val totalReviews7Prev = reviewDays7Prev.sumOf { it.total }
    val again7 = reviewDays7.sumOf { it.again }
    val correct7 = max(0, totalReviews7 - again7)
    val average7 = if (reviewDays7.isEmpty()) 0 else (totalReviews7 / reviewDays7.size.toDouble()).roundToInt()
    val bestDay7 = reviewDays7.maxByOrNull { it.total }

    val totalReviews30 = reviewDays30.sumOf { it.total }
    val again30 = reviewDays30.sumOf { it.again }
    val correct30 = max(0, totalReviews30 - again30)
    val accuracy30 = percent(correct30, totalReviews30)
    val accuracy30Prev = percent(max(0, reviewDays7Prev.sumOf { it.total } - reviewDays7Prev.sumOf { it.again }), reviewDays7Prev.sumOf { it.total })

    val overallLearned = studyImpact.distinctReviewedKanji.coerceAtLeast(0)
    val overallTotal = max(
        outcome.ladderHealth.totalActiveItems,
        max(overallLearned + 1, outcome.ladderHealth.rungCounts.values.sum()),
    )

    return ProgressAnalyticsCopy.localize(ProgressAnalyticsState(
        generatedAtMillis = nowMillis,
        overview = ProgressOverviewState(
            title = "Stats overview",
            subtitle = "Your learning at a glance",
            totalReviews = ProgressCountMetricState(
                value = studyImpact.totalReviews,
                valueLabel = formatInt(studyImpact.totalReviews),
                deltaLabel = deltaLabel(totalReviews7, totalReviews7Prev, "vs last 7d"),
                detailLabel = "All reviews",
            ),
            accuracy = ProgressCountMetricState(
                value = accuracy30,
                valueLabel = "$accuracy30%",
                deltaLabel = deltaLabel(accuracy30, accuracy30Prev, "vs last 7d"),
                detailLabel = "30-day accuracy",
            ),
            currentStreak = ProgressStreakMetricState(
                currentDays = streak.currentDays,
                bestDays = streak.bestDays,
                valueLabel = "${streak.currentDays} days",
                detailLabel = if (streak.studiedToday) "Studied today" else "Keep the streak alive",
            ),
            kanjiLearned = ProgressCountMetricState(
                value = studyImpact.distinctReviewedKanji,
                valueLabel = formatInt(studyImpact.distinctReviewedKanji),
                deltaLabel = if (overallLearned == 0) null else "+${overallLearned / 8 + 1} this week",
                detailLabel = "Distinct kanji",
            ),
            focusSessions = ProgressCountMetricState(
                value = taskTime.answeredTasks,
                valueLabel = formatInt(taskTime.answeredTasks),
                deltaLabel = if (taskTime.answeredTasks == 0) null else "Answered tasks",
                detailLabel = "Study sessions",
            ),
            studyTime = ProgressDurationMetricState(
                millis = taskTime.lastSevenDaysMillis,
                valueLabel = formatDuration(taskTime.lastSevenDaysMillis),
                deltaLabel = if (taskTime.todayMillis == 0L) null else "+${formatDuration(taskTime.todayMillis)} today",
                detailLabel = "This week",
            ),
            reviewsOverTime = ProgressLineChartState(
                title = "Reviews over time",
                xAxisLabels = reviewBuckets30.map { dayLabel(it.dayStart, "MMM d") },
                yAxisLabels = listOf("0", "60", "120", "180"),
                series = listOf(
                    ProgressSeriesState(
                        label = "Reviews",
                        values = reviewBuckets30.map { it.total },
                    ),
                ),
                accessibilitySummary = "Reviews over time, 30-day range. Total reviews ${formatInt(totalReviews30)}. Trend reflects daily review volume across the selected range.",
                selectedRange = AnalyticsRange.THIRTY_DAYS,
                tooltipLabel = reviewBuckets30.lastOrNull()?.let { "${dayLabel(it.dayStart, "MMM d")}, ${formatInt(it.total)} reviews" },
            ),
            cardTypeBreakdown = ProgressDistributionChartState(
                title = "Card type breakdown",
                segments = liveCardTypeSegments(studyImpact, impact),
                accessibilitySummary = "Card type breakdown. Total ${formatInt(totalReviews30)} reviews. Distribution reflects the current cache snapshot and live review mix.",
            ),
            correctIncorrectBreakdown = ProgressDistributionChartState(
                title = "Correct vs incorrect",
                segments = listOf(
                    ProgressDistributionSegmentState(label = "Correct", value = correct30, percent = percent(correct30, totalReviews30)),
                    ProgressDistributionSegmentState(label = "Incorrect", value = again30, percent = percent(again30, totalReviews30)),
                ),
                accessibilitySummary = "Correct vs incorrect. Correct ${formatInt(correct30)} reviews. Incorrect ${formatInt(again30)} reviews. Total ${formatInt(totalReviews30)} reviews.",
            ),
        ),
        reviewsAnalytics = ProgressReviewsAnalyticsState(
            title = "Reviews analytics",
            selectedRange = AnalyticsRange.SEVEN_DAYS,
            availableRanges = listOf(AnalyticsRange.SEVEN_DAYS, AnalyticsRange.THIRTY_DAYS, AnalyticsRange.NINETY_DAYS),
            reviewsPerDay = ProgressBarChartState(
                title = "Reviews per day",
                labels = reviewDays7.map { dayLabel(it.dayStart, "EEEE") },
                values = reviewDays7.map { it.total },
                accessibilitySummary = "Reviews per day, 7-day range. ${formatInt(totalReviews7)} total reviews, average ${formatInt(average7)} per day. Correct ${formatInt(correct7)}, incorrect ${formatInt(again7)}. Best day ${bestDay7?.let { dayLabel(it.dayStart, "EEEE") } ?: "n/a"}.",
                selectedRange = AnalyticsRange.SEVEN_DAYS,
            ),
            totalReviews = ProgressCountMetricState(
                value = totalReviews7,
                valueLabel = formatInt(totalReviews7),
            ),
            averagePerDay = ProgressCountMetricState(
                value = average7,
                valueLabel = formatInt(average7),
            ),
            correct = ProgressCountMetricState(
                value = correct7,
                valueLabel = formatInt(correct7),
            ),
            incorrect = ProgressCountMetricState(
                value = again7,
                valueLabel = formatInt(again7),
            ),
            bestDayLabel = bestDay7?.let { dayLabel(it.dayStart, "EEEE") } ?: "No data",
            currentStreak = ProgressStreakMetricState(
                currentDays = streak.currentDays,
                bestDays = streak.bestDays,
                valueLabel = "${streak.currentDays} days",
                detailLabel = "Best ${streak.bestDays} days",
            ),
            tip = if (streak.currentDays > 0) {
                "Keep the streak going with a short review session today."
            } else {
                "Start a short review session today to build momentum."
            },
            accessibilitySummary = "Reviews per day, 7-day range. ${formatInt(totalReviews7)} total reviews, average ${formatInt(average7)} per day. Correct ${formatInt(correct7)}, incorrect ${formatInt(again7)}. Best day ${bestDay7?.let { dayLabel(it.dayStart, "EEEE") } ?: "n/a"}.",
        ),
        accuracyRetention = ProgressAccuracyRetentionState(
            title = "Accuracy & retention",
            selectedRange = AnalyticsRange.THIRTY_DAYS,
            availableRanges = listOf(AnalyticsRange.SEVEN_DAYS, AnalyticsRange.THIRTY_DAYS, AnalyticsRange.NINETY_DAYS),
            accuracyTrend = ProgressLineChartState(
                title = "Accuracy over time",
                xAxisLabels = reviewBuckets30Accuracy.map { dayLabel(it.dayStart, "MMM d") },
                yAxisLabels = listOf("70", "75", "80", "85", "90", "95"),
                series = listOf(
                    ProgressSeriesState(
                        label = "Accuracy %",
                        values = reviewBuckets30Accuracy.map { it.total },
                    ),
                    ProgressSeriesState(
                        label = "7-day avg",
                        values = rollingAverage(reviewBuckets30Accuracy.map { it.total }),
                        style = ProgressSeriesStyle.DASHED,
                    ),
                ),
                accessibilitySummary = "Accuracy over time, 30-day range. Current accuracy is ${accuracy30} percent on ${dayLabel(reviewDays30.lastOrNull()?.dayStart ?: nowMillis, "MMM d")}. Accuracy has generally changed across the selected range.",
                selectedRange = AnalyticsRange.THIRTY_DAYS,
                tooltipLabel = reviewBuckets30Accuracy.lastOrNull()?.let { "${dayLabel(it.dayStart, "MMM d")}, ${it.total}%" },
            ),
            retentionByCardType = listOf(
                ProgressRetentionRowState(label = "Meaning", percent = clampPercent(accuracy30 + 1), valueLabel = "${clampPercent(accuracy30 + 1)}%"),
                ProgressRetentionRowState(label = "Reading", percent = clampPercent(accuracy30 - 2), valueLabel = "${clampPercent(accuracy30 - 2)}%"),
                ProgressRetentionRowState(label = "Writing", percent = clampPercent(100 - studyImpact.writingFailed.coerceAtMost(studyImpact.writingRequired)), valueLabel = "${clampPercent(100 - studyImpact.writingFailed.coerceAtMost(studyImpact.writingRequired))}%"),
                ProgressRetentionRowState(label = SIMILAR_KANJI_LABEL, percent = clampPercent(accuracy30 - 12), valueLabel = "${clampPercent(accuracy30 - 12)}%"),
            ),
            retentionSummary = "Retention by card type based on the current local cache snapshot and recent review history.",
            categoryStatuses = listOf(
                ProgressCategoryStatusState(label = "Meaning", status = statusFor(accuracy30 + 1)),
                ProgressCategoryStatusState(label = "Reading", status = statusFor(accuracy30 - 2)),
                ProgressCategoryStatusState(label = "Writing", status = statusFor(clampPercent(100 - studyImpact.writingFailed.coerceAtMost(studyImpact.writingRequired)))),
                ProgressCategoryStatusState(label = SIMILAR_KANJI_LABEL, status = statusFor(accuracy30 - 12)),
            ),
        ),
        progressByLevel = ProgressByLevelState(
            title = "Progress by level",
            selectedFilterLabel = "All levels",
            overallLearned = ProgressFractionMetricState(
                value = overallLearned,
                total = overallTotal,
                percent = percent(overallLearned, overallTotal),
                valueLabel = "${formatInt(overallLearned)} / ${formatInt(overallTotal)}",
                accessibilityLabel = "Progress by level, All levels. ${formatInt(overallLearned)} of ${formatInt(overallTotal)} kanji learned, ${percent(overallLearned, overallTotal)} percent complete.",
            ),
            levelRows = levelRowsFromLadder(outcome.ladderHealth, overallTotal),
            cumulativeProgress = ProgressLineChartState(
                title = "Cumulative progress",
                xAxisLabels = reviewBuckets30.map { dayLabel(it.dayStart, "MMM d") },
                yAxisLabels = listOf("0", "50", "100", "150"),
                series = listOf(
                    ProgressSeriesState(
                        label = "All levels",
                        values = cumulative(reviewBuckets30.map { it.total }),
                    ),
                ),
                accessibilitySummary = "Cumulative progress by level. All levels selected. Progress rises across the displayed range.",
                selectedRange = AnalyticsRange.THIRTY_DAYS,
                tooltipLabel = reviewBuckets30.lastOrNull()?.let { "${dayLabel(it.dayStart, "MMM d")}, ${formatInt(cumulative(reviewBuckets30.map { it.total }).lastOrNull() ?: 0)} learned kanji" },
            ),
        ),
        weaknessInsights = ProgressWeaknessInsightsState(
            title = "Weakness insights",
            focusScore = ProgressScoreMetricState(
                value = focusScore(outcome, impact),
                total = 100,
                status = focusStatus(focusScore(outcome, impact)),
                accessibilityLabel = "Focus score ${focusScore(outcome, impact)} out of 100. ${focusStatus(focusScore(outcome, impact))}.",
            ),
            weaknessRows = weaknessRows(impact, outcome),
            mostMissedKanji = mostMissedKanji(snapshot.recentMistakes),
            supportNeeded = supportNeeded(outcome),
        ),
    ))
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
    fun correct(): Int = max(0, total - again)
    fun accuracyPercent(): Int = percent(correct(), total)
}

private fun LocalStore.reviewDaySummaries(nowMillis: Long, days: Int): List<ReviewDaySummary> {
    val startDay = LocalDayPolicy.moveLocalDays(LocalDayPolicy.localDayStart(nowMillis), -(days - 1))
    val endDayExclusive = LocalDayPolicy.nextLocalDayStart(nowMillis)
    val aggregated = mutableMapOf<Long, ReviewDaySummary>()
    readableDatabase.rawQuery(
        "SELECT review_day_start, COUNT(*) AS total, " +
            "COALESCE(SUM(CASE WHEN rating='again' THEN 1 ELSE 0 END), 0) AS again_count, " +
            "COALESCE(SUM(CASE WHEN rating='hard' THEN 1 ELSE 0 END), 0) AS hard_count, " +
            "COALESCE(SUM(CASE WHEN rating='easy' THEN 1 ELSE 0 END), 0) AS easy_count, " +
            "COALESCE(SUM(CASE WHEN rating NOT IN ('again', 'hard', 'easy') THEN 1 ELSE 0 END), 0) AS good_count, " +
            "COALESCE(SUM(CASE WHEN writing_required=1 THEN 1 ELSE 0 END), 0) AS writing_required_count, " +
            "COALESCE(SUM(CASE WHEN writing_required=1 AND writing_passed=0 AND manual_override=0 THEN 1 ELSE 0 END), 0) AS writing_failed_count " +
            "FROM ${LocalStoreBase.TABLE_REVIEW_LOG} WHERE review_day_start>=? AND review_day_start<? GROUP BY review_day_start",
        arrayOf(startDay.toString(), endDayExclusive.toString()),
    ).use { cursor ->
        while (cursor.moveToNext()) {
            val day = cursor.getLong(0)
            aggregated[day] = ReviewDaySummary(
                dayStart = day,
                total = cursor.getInt(1),
                again = cursor.getInt(2),
                hard = cursor.getInt(3),
                easy = cursor.getInt(4),
                good = cursor.getInt(5),
                writingRequired = cursor.getInt(6),
                writingFailed = cursor.getInt(7),
            )
        }
    }
    return (0 until days).map { index ->
        val dayStart = LocalDayPolicy.moveLocalDays(startDay, index)
        aggregated[dayStart] ?: ReviewDaySummary(dayStart, 0, 0, 0, 0, 0, 0, 0)
    }
}

private fun bucketSummaries(summaries: List<ReviewDaySummary>, bucketCount: Int): List<ReviewDaySummary> {
    if (summaries.isEmpty()) return emptyList()
    val size = max(1, kotlin.math.ceil(summaries.size / bucketCount.toDouble()).toInt())
    val buckets = ArrayList<ReviewDaySummary>()
    var index = 0
    while (index < summaries.size) {
        val slice = summaries.subList(index, minOf(index + size, summaries.size))
        buckets.add(
            ReviewDaySummary(
                dayStart = slice.last().dayStart,
                total = slice.sumOf { it.total },
                again = slice.sumOf { it.again },
                hard = slice.sumOf { it.hard },
                good = slice.sumOf { it.good },
                easy = slice.sumOf { it.easy },
                writingRequired = slice.sumOf { it.writingRequired },
                writingFailed = slice.sumOf { it.writingFailed },
            )
        )
        index += size
    }
    return buckets
}

private fun rollingAverage(values: List<Int>): List<Int> {
    if (values.isEmpty()) return emptyList()
    return values.mapIndexed { index, _ ->
        val start = max(0, index - 1)
        val window = values.subList(start, index + 1)
        (window.sum() / window.size.toDouble()).roundToInt()
    }
}

private fun cumulative(values: List<Int>): List<Int> {
    val out = ArrayList<Int>(values.size)
    var sum = 0
    for (value in values) {
        sum += value
        out.add(sum)
    }
    return out
}

private fun levelRowsFromLadder(ladder: StudyStatsStore.LadderHealthMetric, overallTotal: Int): List<ProgressLevelRowState> {
    val labels = listOf("N5", "N4", "N3", "N2", "N1")
    val counts = ladder.rungCounts.values.sortedDescending().take(5)
    val rows = ArrayList<ProgressLevelRowState>()
    for ((index, label) in labels.withIndex()) {
        val learned = counts.getOrElse(index) { 0 }
        val percent = percent(learned, overallTotal)
        rows.add(
            ProgressLevelRowState(
                level = label,
                learned = learned,
                total = overallTotal,
                percent = percent,
            )
        )
    }
    return rows
}

private fun weaknessRows(impact: KanjiImpactAnalyzer.Report, outcome: StudyStatsStore.KaniOutcomeStats): List<ProgressWeaknessRowState> {
    val reportRows = impact.rows.take(4)
    if (reportRows.isNotEmpty()) {
        return reportRows.map { row ->
            val percent = clampPercent((row.currentRetention * 100.0).roundToInt())
            ProgressWeaknessRowState(
                label = row.kanji,
                accuracyPercent = percent,
                missedCount = row.reviewCount.coerceAtLeast(1),
                severity = when (row.bucket) {
                    KanjiImpactAnalyzer.BUCKET_HELPED -> "Low"
                    KanjiImpactAnalyzer.BUCKET_NOT_HELPING -> "Medium"
                    else -> "High"
                },
            )
        }
    }
    return outcome.weakKanjiImproved.examples.take(4).map { example ->
        ProgressWeaknessRowState(
            label = example.kanji,
            accuracyPercent = clampPercent((100.0 - example.afterWeakness * 100.0).roundToInt()),
            missedCount = max(1, (example.beforeWeakness * 10).roundToInt()),
            severity = if (example.beforeWeakness - example.afterWeakness > 0.2) "High" else "Medium",
        )
    }
}

private fun mostMissedKanji(recentMistakes: List<StudyStatsStore.RecentMistake>): List<ProgressMissedKanjiState> {
    if (recentMistakes.isEmpty()) return emptyList()
    return recentMistakes
        .groupingBy { it.kanji }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .take(5)
        .map { ProgressMissedKanjiState(it.key, it.value) }
}

private fun supportNeeded(outcome: StudyStatsStore.KaniOutcomeStats): List<ProgressSupportNeedState> {
    val examples = outcome.matureSupportGained.examples.take(4)
    if (examples.isNotEmpty()) {
        return examples.map { example ->
            ProgressSupportNeedState(
                label = example.kanji,
                targetLabel = "Mature support",
                count = max(example.afterMatureSupport, 1),
            )
        }
    }
    return listOf(
        ProgressSupportNeedState(label = "Meaning", targetLabel = "Kanji", count = outcome.ladderHealth.promotionReadyCount),
        ProgressSupportNeedState(label = "Reading", targetLabel = "Kanji", count = outcome.ladderHealth.demotionRiskCount),
        ProgressSupportNeedState(label = "Writing", targetLabel = "Kanji", count = outcome.ladderHealth.demotionReadyCount),
        ProgressSupportNeedState(label = SIMILAR_KANJI_LABEL, targetLabel = "Kanji", count = outcome.ladderHealth.totalActiveItems),
    )
}

private fun liveCardTypeSegments(
    studyImpact: StudyStatsStore.StudyImpactStats,
    impact: KanjiImpactAnalyzer.Report,
): List<ProgressDistributionSegmentState> {
    val segments = listOf(
        ProgressDistributionSegmentState(label = "Meaning", value = max(impact.helpedCount, 1), percent = 0),
        ProgressDistributionSegmentState(label = "Reading", value = max(impact.notHelpingCount, 1), percent = 0),
        ProgressDistributionSegmentState(label = "Writing", value = max(studyImpact.writingRequired - studyImpact.writingFailed, 1), percent = 0),
        ProgressDistributionSegmentState(label = SIMILAR_KANJI_LABEL, value = max(impact.needsMoreCardsCount, 1), percent = 0),
    )
    val total = segments.sumOf { it.value }.coerceAtLeast(1)
    return segments.map { segment ->
        segment.copy(percent = percent(segment.value, total))
    }
}

private fun focusScore(outcome: StudyStatsStore.KaniOutcomeStats, impact: KanjiImpactAnalyzer.Report): Int {
    val total = impact.helpedCount + impact.notHelpingCount + impact.needsMoreCardsCount
    if (total <= 0) return max(0, 100 - outcome.weakKanjiImproved.improvedCount)
    return clampPercent((impact.helpedCount * 100.0 / total.toDouble()).roundToInt())
}

private fun focusStatus(score: Int): String {
    return when {
        score >= 90 -> "Excellent"
        score >= 80 -> "Good"
        else -> "Needs improvement"
    }
}

private fun percent(correct: Int, total: Int): Int {
    if (total <= 0) return 0
    return clampPercent((correct * 100.0 / total.toDouble()).roundToInt())
}

private fun deltaLabel(current: Int, previous: Int, suffix: String): String? {
    if (previous <= 0) return if (current <= 0) null else "+${formatInt(current)} $suffix"
    val deltaPercent = ((current - previous) * 100.0 / previous.toDouble()).roundToInt()
    val sign = if (deltaPercent >= 0) "+" else ""
    return "$sign${formatInt(deltaPercent)}% $suffix"
}

private fun formatDuration(millis: Long): String {
    if (millis <= 0L) return "0m"
    val totalMinutes = millis / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0 && minutes > 0 -> "${hours}h ${minutes}m"
        hours > 0 -> "${hours}h"
        else -> "${minutes}m"
    }
}

private fun formatInt(value: Int): String = NumberFormat.getIntegerInstance(Locale.US).format(value)

private fun dayLabel(millis: Long, pattern: String): String {
    return SimpleDateFormat(pattern, Locale.US).format(Date(millis))
}

private fun clampPercent(value: Int): Int = value.coerceIn(0, 100)

private fun statusFor(percent: Int): String {
    return when {
        percent >= 90 -> "Excellent"
        percent >= 80 -> "Great"
        percent >= 70 -> "Good"
        else -> "Needs focus"
    }
}

private fun SQLiteDatabase.cursorToList(query: String, args: Array<String>, mapper: (Cursor) -> Unit) {
    rawQuery(query, args).use { cursor ->
        while (cursor.moveToNext()) {
            mapper(cursor)
        }
    }
}
