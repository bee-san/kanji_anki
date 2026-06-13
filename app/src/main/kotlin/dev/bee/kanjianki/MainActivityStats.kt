package dev.bee.kanjianki

import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.StatsTextCopy
import dev.bee.kanjianki.data.StudyStatsStore
import dev.bee.kanjianki.progress.progressAnalyticsSnapshot

internal abstract class MainActivityStats : MainActivityGames() {
    override fun renderStats() {
        if (isScreenshotLaunchRequested()) {
            renderScreenshotStats()
            return
        }
        renderAsyncHomeRoute(
            loadingTitle = HomeTextCopy.statsActionLabel(),
            load = { progressAnalyticsSnapshot(store, scheduleRefresh = this::scheduleStatsPrecomputeIfStaleAsync) },
            render = { model ->
                composeRouteWithActionBar(
                    selected = MainActivityBase.NAV_STATS_ROUTE,
                    content = {
                        ProgressAnalyticsDashboardScreen(state = model)
                    },
                    actionBar = {
                        ProgressAnalyticsBottomNav(
                            selectedTab = ProgressAnalyticsBottomNavTab.Progress,
                            onHome = this::renderHome,
                            onStudy = this::renderStudy,
                            onProgress = this::renderStats,
                            onProfile = this::renderSettings,
                        )
                    },
                )
            },
        )
    }

    private fun renderScreenshotStats() {
        val model = screenshotStatsScreenModel()
        composeRoute(
            MainActivityBase.NAV_STATS_ROUTE,
            initialScrollY = screenshotScrollY(),
            scrollPositionLabel = screenshotScrollPositionLabel(),
        ) {
            StatsRouteScreen(model = model, onHome = this::renderHome)
        }
    }

    fun notHelpingRows(report: KanjiImpactAnalyzer.Report?): List<KanjiImpactAnalyzer.Row> {
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
