package dev.bee.kanjianki

import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.StatsTextCopy
import dev.bee.kanjianki.core.StudyTextCopy
import dev.bee.kanjianki.data.StudyStatsStore
import java.util.Locale
import kotlin.system.measureNanoTime
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityStatsCardsBenchmarkTest {
    @Test
    fun benchmarksStatsExampleLineBuildersAgainstLegacyMapPaths() {
        val weaknessMetric = StudyStatsStore.WeakKanjiImprovedMetric(
            improvedCount = 96,
            averageBeforeWeakness = 81.0,
            averageAfterWeakness = 42.0,
            examples = benchmarkWeaknessExamples(24),
        )
        val supportMetric = StudyStatsStore.MatureSupportGainedMetric(
            gainedSupportCount = 72,
            matureSupportGained = 72,
            firstSupportCount = 28,
            examples = benchmarkSupportGains(32),
        )
        val ladderMetric = StudyStatsStore.LadderHealthMetric(
            benchmarkLadderCounts(),
            totalActiveItems = 192,
            ladderPromotionIntervalDays = 14,
            ladderDemotionFailStreak = 3,
            promotionReadyCount = 18,
            demotionRiskCount = 11,
            demotionReadyCount = 8,
        )

        benchmarkListBuilder(
            label = "stats-weakness-lines",
            iterations = 50_000,
            legacy = { legacyStatsWeaknessImprovementLines(weaknessMetric) },
            optimized = { statsWeaknessImprovementLines(weaknessMetric) },
        )
        benchmarkListBuilder(
            label = "stats-support-lines",
            iterations = 50_000,
            legacy = { legacyStatsSupportGainLines(supportMetric) },
            optimized = { statsSupportGainLines(supportMetric) },
        )
        benchmarkListBuilder(
            label = "stats-ladder-lines",
            iterations = 100_000,
            legacy = { legacyStatsLadderDistributionLines(ladderMetric) },
            optimized = { statsLadderDistributionLines(ladderMetric) },
        )
    }

    @Test
    fun benchmarksStatsStatusLineBuildersAgainstLegacyListAllocations() {
        val nowMillis = 1_725_000_000_000L
        val mistakes = benchmarkRecentMistakes(96)
        val report = benchmarkImpactReport(64)
        val notHelpingRows = KanjiImpactAnalyzer.notHelpingRows(report)

        benchmarkListBuilder(
            label = "stats-recent-mistake-lines",
            iterations = 50_000,
            legacy = { legacyStatsRecentMistakeLines(mistakes, nowMillis) },
            optimized = { statsRecentMistakeLines(mistakes, nowMillis) },
        )
        benchmarkListBuilder(
            label = "stats-not-helping-lines",
            iterations = 50_000,
            legacy = { legacyStatsNotHelpingLines(notHelpingRows, report) },
            optimized = { statsNotHelpingLines(notHelpingRows, report) },
        )
    }

    private inline fun benchmarkListBuilder(
        label: String,
        iterations: Int,
        legacy: () -> List<StatsLineModel>,
        optimized: () -> List<StatsLineModel>,
    ) {
        val legacySample = legacy()
        val optimizedSample = optimized()
        assertEquals(legacySample, optimizedSample)

        var legacyChecksum = 0
        val legacyNanos = measureNanoTime {
            repeat(iterations) {
                legacyChecksum += legacy().checksum()
            }
        }

        var optimizedChecksum = 0
        val optimizedNanos = measureNanoTime {
            repeat(iterations) {
                optimizedChecksum += optimized().checksum()
            }
        }

        assertEquals(legacyChecksum, optimizedChecksum)
        println(
            String.format(
                Locale.ROOT,
                "%s legacy_ms=%.3f legacy_avg_us=%.3f optimized_ms=%.3f optimized_avg_us=%.3f",
                label,
                legacyNanos / 1_000_000.0,
                legacyNanos / iterations.toDouble() / 1_000.0,
                optimizedNanos / 1_000_000.0,
                optimizedNanos / iterations.toDouble() / 1_000.0,
            ),
        )
    }

    private fun List<StatsLineModel>.checksum(): Int {
        return fold(0) { acc, line -> acc + line.text.length + line.sizeSp + if (line.bold) 1 else 0 }
    }

    private fun legacyStatsWeaknessImprovementLines(metric: StudyStatsStore.WeakKanjiImprovedMetric): List<StatsLineModel> {
        return metric.examples.take(3).map { example ->
            StatsLineModel(
                text = StatsTextCopy.weaknessImprovementExample(
                    example.kanji,
                    example.beforeWeakness,
                    example.afterWeakness,
                ),
                color = STATS_INK_COLOR,
                bold = true,
                sizeSp = 17,
            )
        }
    }

    private fun legacyStatsSupportGainLines(metric: StudyStatsStore.MatureSupportGainedMetric): List<StatsLineModel> {
        return metric.examples.map { example ->
            StatsLineModel(
                text = StatsTextCopy.supportGainExample(
                    example.kanji,
                    example.beforeMatureSupport,
                    example.afterMatureSupport,
                ),
                color = STATS_INK_COLOR,
                bold = true,
                sizeSp = 17,
            )
        }
    }

    private fun legacyStatsLadderDistributionLines(metric: StudyStatsStore.LadderHealthMetric): List<StatsLineModel> {
        return RecordsBase.LadderRung.values().map { rung ->
            StatsLineModel(
                text = StatsTextCopy.ladderDistributionRow(rung, metric.countFor(rung)),
                color = STATS_INK_COLOR,
                bold = false,
                sizeSp = 16,
            )
        }
    }

    private fun legacyStatsRecentMistakeLines(
        mistakes: List<StudyStatsStore.RecentMistake>,
        nowMillis: Long,
    ): List<StatsLineModel> {
        return mistakes.take(5).map { mistake ->
            StatsLineModel(
                text = StatsTextCopy.recentMistakeRowText(
                    mistake.kanji,
                    mistake.rating,
                    mistake.reviewedAtMillis,
                    nowMillis,
                ),
                color = STATS_INK_COLOR,
                bold = true,
                sizeSp = 16,
            )
        }
    }

    private fun legacyStatsNotHelpingLines(
        rows: List<KanjiImpactAnalyzer.Row>,
        report: KanjiImpactAnalyzer.Report?,
    ): List<StatsLineModel> {
        val details = buildList {
            rows.take(5).forEach { row ->
                add(
                    StatsLineModel(
                        text = StatsTextCopy.notHelpingRowText(
                            row.kanji,
                            row.reviewCount,
                            row.sameCardCount,
                            row.retentionDelta,
                            row.difficultyDelta,
                        ),
                        color = STATS_INK_COLOR,
                        bold = true,
                        sizeSp = 16,
                    )
                )
            }
            if (rows.isNotEmpty() && report != null && report.needsMoreCardsCount > 0) {
                add(
                    StatsLineModel(
                        text = StudyTextCopy.countText(
                            report.needsMoreCardsCount,
                            "kanji still needs more Anki evidence",
                            "kanji still need more Anki evidence",
                        ) + ".",
                        color = STATS_MUTED_COLOR,
                        bold = false,
                        sizeSp = 15,
                    )
                )
            }
        }
        return details
    }

    private fun benchmarkWeaknessExamples(count: Int): List<StudyStatsStore.KanjiImprovement> {
        return List(count) { index ->
            StudyStatsStore.KanjiImprovement(
                kanji = "字${index + 1}",
                beforeWeakness = 80.0 - index,
                afterWeakness = 42.0 + (index % 7),
            )
        }
    }

    private fun benchmarkSupportGains(count: Int): List<StudyStatsStore.KanjiSupportGain> {
        return List(count) { index ->
            StudyStatsStore.KanjiSupportGain(
                kanji = "字${index + 1}",
                beforeMatureSupport = index,
                afterMatureSupport = index + 3,
            )
        }
    }

    private fun benchmarkLadderCounts(): Map<RecordsBase.LadderRung, Int> {
        return RecordsBase.LadderRung.entries.withIndex().associate { (index, rung) ->
            rung to (index + 1) * 12
        }
    }

    private fun benchmarkRecentMistakes(count: Int): List<StudyStatsStore.RecentMistake> {
        return List(count) { index ->
            StudyStatsStore.RecentMistake(
                kanji = "字${index + 1}",
                rating = if (index % 2 == 0) "again" else "good",
                reviewedAtMillis = 1_725_000_000_000L + index * 60_000L,
            )
        }
    }

    private fun benchmarkImpactReport(count: Int): KanjiImpactAnalyzer.Report {
        val rows = List(count) { index ->
            val bucket = when (index % 3) {
                0 -> KanjiImpactAnalyzer.BUCKET_NOT_HELPING
                1 -> KanjiImpactAnalyzer.BUCKET_HELPED
                else -> KanjiImpactAnalyzer.BUCKET_NEEDS_MORE_CARDS
            }
            KanjiImpactAnalyzer.Row.create(
                kanji = "字${index + 1}",
                bucket = bucket,
                baselineDifficulty = 4.5 + index,
                currentDifficulty = 3.0 + index,
                baselineRetention = 0.35 + (index % 5) * 0.05,
                currentRetention = 0.45 + (index % 5) * 0.05,
                baselineMatureCards = index % 4,
                currentMatureCards = if (bucket == KanjiImpactAnalyzer.BUCKET_NOT_HELPING) index % 4 else index % 4 + 1,
                sameCardCount = 3 + index,
                newCardCount = 2 + index,
                currentCardCount = 5 + index,
                reviewCount = 7 + index,
                advice = "advice-$index",
            )
        }
        return KanjiImpactAnalyzer.Report(
            helpedCount = rows.count { it.bucket == KanjiImpactAnalyzer.BUCKET_HELPED },
            notHelpingCount = rows.count { it.bucket == KanjiImpactAnalyzer.BUCKET_NOT_HELPING },
            needsMoreCardsCount = rows.count { it.bucket == KanjiImpactAnalyzer.BUCKET_NEEDS_MORE_CARDS },
            rows = rows,
        )
    }
}
