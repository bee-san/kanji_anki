package dev.bee.kanjianki.progress

import dev.bee.kanjianki.core.CoreSkill
import dev.bee.kanjianki.core.FailureKind
import dev.bee.kanjianki.core.KanjiImpactAnalyzer
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.data.STATS_CACHE_FORMAT_VERSION
import dev.bee.kanjianki.data.StatsCacheStore
import dev.bee.kanjianki.data.StudyStatsStore
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressAdaptiveHealthPresentationTest {
    @Test
    fun adaptiveSnapshotReplacesLegacyRungsWithCoreAndRepairHealth() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            val adaptive = StudyStatsStore.AdaptiveHealthMetric(
                coreCounts = mapOf(CoreSkill.RECOGNITION to 2, CoreSkill.CONTEXTUAL_READING to 3),
                activeRepairsByTask = mapOf("type_reading" to 1),
                activeRepairsByFailure = mapOf(FailureKind.WRONG_READING to 1),
                totalAdaptiveItems = 5,
                contextualCompleteCount = 2,
                activeRepairCount = 1,
                revalidationPendingCount = 1,
                recentCoreMissCount = 2,
                escalationRiskCount = 1,
                stuckRepairCount = 1,
                malformedStateCount = 0,
            )
            val legacyHealth = StudyStatsStore.LadderHealthMetric(
                mapOf(RecordsBase.LadderRung.KANJI_MEANING to 2),
                7,
                3,
                0,
                0,
                0,
            )
            val snapshot = StatsCacheStore.Snapshot(
                outcomeStats = StudyStatsStore.KaniOutcomeStats(
                    StudyStatsStore.WeakKanjiImprovedMetric.empty(),
                    StudyStatsStore.MatureSupportGainedMetric.empty(),
                    legacyHealth,
                    adaptive,
                ),
                impactReport = KanjiImpactAnalyzer.Report(0, 0, 0, emptyList()),
                generatedAtMillis = NOW,
                sourceVersion = 1,
                cacheFormatVersion = STATS_CACHE_FORMAT_VERSION,
            )
            val defaults = RecordsBase.StudyLadderSettings.defaults()
            val customLegacyOrder = RecordsBase.StudyLadderSettings(
                defaults.orderedRungs.reversed(),
                defaults.enabledRungs,
            )
            val state = progressAnalyticsSnapshot(
                FixedSource(snapshot),
                NOW,
                ladderSettings = customLegacyOrder,
            )

            assertEquals("Core skill and repair health", state.progressByLevel.title)
            assertEquals(2, state.progressByLevel.overallLearned.value)
            assertEquals(7, state.progressByLevel.overallLearned.total)
            assertEquals("2 contextually validated", state.progressByLevel.overallLearned.valueLabel)
            assertEquals(
                listOf(
                    "Recognition core",
                    "Contextual reading core",
                    "Active repair",
                    "Revalidation pending",
                    "Escalation risk",
                    "Stuck repair",
                    "Finishing legacy route",
                ),
                state.progressByLevel.levelRows.map { it.level },
            )
            assertEquals(2, state.progressByLevel.levelRows.last().learned)
            assertTrue(state.progressByLevel.selectedFilterLabel.contains("1 stuck repairs"))
        } finally {
            Locale.setDefault(original)
        }
    }

    private class FixedSource(private val snapshot: StatsCacheStore.Snapshot) : ProgressAnalyticsStatsSource {
        override fun cachedStatsSnapshotOrNull(): StatsCacheStore.Snapshot = snapshot
        override fun recomputeStatsSnapshotSynchronously(nowMillis: Long): StatsCacheStore.Snapshot = snapshot
        override fun reviewDaySummaries(nowMillis: Long, days: Int): List<ReviewDaySummary> = emptyList()
    }

    private companion object {
        const val NOW = 1_700_000_000_000L
    }
}
