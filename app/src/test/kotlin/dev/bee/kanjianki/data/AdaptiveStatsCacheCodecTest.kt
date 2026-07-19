package dev.bee.kanjianki.data

import dev.bee.kanjianki.core.CoreSkill
import dev.bee.kanjianki.core.FailureKind
import dev.bee.kanjianki.core.StudyTaskTypes
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AdaptiveStatsCacheCodecTest {
    @Test
    fun cacheFormatElevenRoundTripsAdaptiveHealthAndLegacyJsonDefaultsEmpty() {
        val metric = StudyStatsStore.AdaptiveHealthMetric(
            coreCounts = mapOf(CoreSkill.RECOGNITION to 2, CoreSkill.CONTEXTUAL_READING to 3),
            activeRepairsByTask = mapOf(StudyTaskTypes.TYPE_READING to 2),
            activeRepairsByFailure = mapOf(FailureKind.WRONG_READING to 2),
            totalAdaptiveItems = 5,
            contextualCompleteCount = 2,
            activeRepairCount = 2,
            revalidationPendingCount = 1,
            recentCoreMissCount = 3,
            escalationRiskCount = 1,
            stuckRepairCount = 1,
            malformedStateCount = 0,
        )
        val stats = StudyStatsStore.KaniOutcomeStats(
            StudyStatsStore.WeakKanjiImprovedMetric.empty(),
            StudyStatsStore.MatureSupportGainedMetric.empty(),
            StudyStatsStore.LadderHealthMetric.empty(),
            metric,
        )

        val encoded = StatsCacheCodec.outcomeToJson(stats)
        val decoded = StatsCacheCodec.outcomeFromJson(encoded).adaptiveHealth

        assertEquals(11, STATS_CACHE_FORMAT_VERSION)
        assertEquals(5, decoded.totalAdaptiveItems)
        assertEquals(2, decoded.countFor(CoreSkill.RECOGNITION))
        assertEquals(3, decoded.countFor(CoreSkill.CONTEXTUAL_READING))
        assertEquals(2, decoded.repairCountFor(StudyTaskTypes.TYPE_READING))
        assertEquals(2, decoded.failureCountFor(FailureKind.WRONG_READING))
        assertEquals(2, decoded.contextualCompleteCount)
        assertEquals(1, decoded.revalidationPendingCount)
        assertEquals(1, decoded.escalationRiskCount)
        assertEquals(1, decoded.stuckRepairCount)

        assertEquals(0, StatsCacheCodec.outcomeFromJson("{}").adaptiveHealth.totalAdaptiveItems)
    }
}
