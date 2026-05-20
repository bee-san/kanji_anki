package dev.bee.kanjianki

import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.StatsTextCopy
import dev.bee.kanjianki.data.StudyStatsStore

internal abstract class MainActivityStats : MainActivityGames() {
    override fun renderStats() {
        base("stats")
        content.addView(statsScreenView(this))
    }

    fun notHelpingRows(report: KanjiImpactAnalyzer.Report): List<KanjiImpactAnalyzer.Row> {
        return KanjiImpactAnalyzer.notHelpingRows(report)
    }

    fun ladderDistributionRows(metric: StudyStatsStore.LadderHealthMetric): List<String> {
        return RecordsBase.LadderRung.values().map { rung ->
            StatsTextCopy.ladderDistributionRow(rung, metric.countFor(rung))
        }
    }

    fun weaknessImprovementExamples(metric: StudyStatsStore.WeakKanjiImprovedMetric): List<String> {
        return metric.examples.take(3).map { example ->
            StatsTextCopy.weaknessImprovementExample(
                example.kanji,
                example.beforeWeakness,
                example.afterWeakness
            )
        }
    }

    fun supportGainExamples(metric: StudyStatsStore.MatureSupportGainedMetric): List<String> {
        return metric.examples.map { example ->
            StatsTextCopy.supportGainExample(
                example.kanji,
                example.beforeMatureSupport,
                example.afterMatureSupport
            )
        }
    }
}
