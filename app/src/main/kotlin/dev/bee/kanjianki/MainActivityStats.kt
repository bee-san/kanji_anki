package dev.bee.kanjianki

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.StatsTextCopy
import dev.bee.kanjianki.data.MatureSupportGainedSnapshot
import dev.bee.kanjianki.data.WeakKanjiImprovedSnapshot
import dev.bee.kanjianki.progress.progressAnalyticsSampleSnapshot
import dev.bee.kanjianki.progress.progressAnalyticsSnapshot
import kotlinx.coroutines.runBlocking

internal abstract class MainActivityStats : MainActivityGames() {
    override fun renderStats() {
        if (isScreenshotLaunchRequested()) {
            renderScreenshotStats()
            return
        }
        currentRoute = MainActivityBase.NAV_STATS_ROUTE
        currentHomeRouteRestoration = null
        loadRouteAsync(
            showLoading = {
                composeRoute(MainActivityBase.NAV_STATS_ROUTE) {
                    HomeRouteLoadingScreen(
                        title = HomeTextCopy.statsActionLabel(),
                        homeLabel = HomeTextCopy.homeLabel(),
                        onHome = ::renderHome,
                    )
                }
            },
            traceName = "stats-route",
            load = {
                val now = System.currentTimeMillis()
                val stats = runBlocking { statsUseCases.loadForDisplay(now) }
                val settings = runBlocking { settingsUseCases.load() }
                progressAnalyticsSnapshot(stats, now, settings.studyLadder)
            },
            render = { model ->
                composeRoute(
                    selected = MainActivityBase.NAV_STATS_ROUTE,
                ) {
                    ProgressAnalyticsDashboardScreen(state = model, onBrowseKanji = { renderBrowseKanji(it) })
                }
            },
        )
    }

    private fun renderScreenshotStats() {
        val state = progressAnalyticsSampleSnapshot(0L)
        composeRoute(
            MainActivityBase.NAV_STATS_ROUTE,
            initialScrollY = screenshotScrollY(),
            scrollPositionLabel = screenshotScrollPositionLabel(),
        ) {
            ProgressAnalyticsDashboardScreen(
                state = state,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }

    fun notHelpingRows(report: KanjiImpactAnalyzer.Report?): List<KanjiImpactAnalyzer.Row> {
        return KanjiImpactAnalyzer.notHelpingRows(report)
    }

    fun weaknessImprovementExamples(metric: WeakKanjiImprovedSnapshot): List<String> {
        return metric.examples.take(3).map { example ->
            StatsTextCopy.weaknessImprovementExample(
                example.kanji,
                example.beforeWeakness,
                example.afterWeakness
            )
        }
    }

    fun supportGainExamples(metric: MatureSupportGainedSnapshot): List<String> {
        return metric.examples.map { example ->
            StatsTextCopy.supportGainExample(
                example.kanji,
                example.beforeMatureSupport,
                example.afterMatureSupport
            )
        }
    }
}
