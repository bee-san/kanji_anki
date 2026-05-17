package dev.bee.kanjianki

import dev.bee.kanjianki.domain.common.AppClock
import dev.bee.kanjianki.domain.model.importing.ImportSettings
import dev.bee.kanjianki.domain.model.importing.NewCardSortMode
import dev.bee.kanjianki.domain.model.similar.SimilarKanjiIndex
import dev.bee.kanjianki.domain.model.similar.SimilarKanjiPair
import dev.bee.kanjianki.domain.repository.ImportSettingsRepository
import dev.bee.kanjianki.domain.repository.StudyLocalSuspensionRepository
import dev.bee.kanjianki.domain.repository.StudyReviewStatsRepository
import dev.bee.kanjianki.domain.repository.StudySchedulerSettings
import dev.bee.kanjianki.domain.repository.StudySchedulerSettingsRepository
import dev.bee.kanjianki.domain.repository.StudyStreak
import dev.bee.kanjianki.domain.scheduler.AdaptiveReviewStats
import dev.bee.kanjianki.domain.scheduler.AdaptiveStudyPlanner
import dev.bee.kanjianki.domain.scheduler.AdaptiveWorkloadPolicy
import dev.bee.kanjianki.domain.scheduler.StudyLadderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import java.util.Calendar

class RoomSyncRequestFactoryTest {
    @Test
    fun requestComposesRoomSettingsStatsSuspensionsAndSimilarIndex() = runBlocking {
        val now = 1_700_000_000_000L
        val importSettings = ImportSettings(
            matureSupportThreshold = 2,
            newCardSortMode = NewCardSortMode.RETRIEVABILITY_RISK,
        )
        val schedulerSettings = StudySchedulerSettings(
            activeQueueCap = 5,
            newPerDay = 7,
            ladderSettings = StudyLadderSettings(
                orderedRungs = StudyLadderSettings.defaults.orderedRungs,
                enabledRungs = StudyLadderSettings.defaults.enabledRungs,
                promotionIntervalDays = 30,
                demotionFailStreak = 6,
            ),
            workloadPolicy = AdaptiveWorkloadPolicy.fromSettings(
                20,
                AdaptiveStudyPlanner.MODE_MANUAL,
                12,
            ),
        )
        val similarIndex = FakeSimilarKanjiIndex
        val factory = RoomSyncRequestFactory(
            importSettingsRepository = FakeImportSettingsRepository(importSettings),
            schedulerSettingsRepository = FakeStudySchedulerSettingsRepository(schedulerSettings),
            localSuspensions = FakeStudyLocalSuspensionRepository(setOf("本")),
            reviewStats = FakeStudyReviewStatsRepository(
                stats = AdaptiveReviewStats(
                    total = 8,
                    again = 1,
                    hard = 2,
                    good = 3,
                    easy = 2,
                    writingRequired = 4,
                    writingFailed = 1,
                ),
                streak = StudyStreak(
                    currentDays = 3,
                    bestDays = 5,
                    studiedToday = true,
                    reviewsToday = 9,
                    lastStudyAtMillis = now - 1_000L,
                ),
                studiedKanji = setOf("日"),
            ),
            similarKanjiIndexProvider = { similarIndex },
            clock = object : AppClock {
                override fun nowMillis(): Long = now
            },
        )

        val request = factory.request()
        val queueContext = request.queueSeedContext!!
        val adaptiveContext = queueContext.adaptiveContext!!

        assertEquals(importSettings, request.importSettings)
        assertEquals(5, queueContext.settings.activeQueueCap)
        assertEquals(7, queueContext.settings.newPerDay)
        assertEquals(2, queueContext.settings.matureSupportThreshold)
        assertEquals(NewCardSortMode.RETRIEVABILITY_RISK, queueContext.settings.newCardSortMode)
        assertEquals(localDayStart(now), queueContext.startOfDayMillis)
        assertEquals(30, queueContext.ladderSettings.promotionIntervalDays)
        assertEquals(6, queueContext.ladderSettings.demotionFailStreak)
        assertEquals(setOf("本"), queueContext.locallySuspendedKanji)
        assertEquals(8, adaptiveContext.recentStats.total)
        assertEquals(1, adaptiveContext.recentStats.again)
        assertEquals(3, adaptiveContext.currentStreakDays)
        assertEquals(setOf("日"), adaptiveContext.studiedToday)
        assertEquals(20, adaptiveContext.workloadPolicy.workloadPercent)
        assertEquals(12, adaptiveContext.workloadPolicy.maxItems)
        assertSame(similarIndex, request.similarKanjiIndex)
    }

    private class FakeImportSettingsRepository(
        private val settings: ImportSettings,
    ) : ImportSettingsRepository {
        override suspend fun get(): ImportSettings = settings

        override suspend fun save(
            settings: ImportSettings,
            updatedAtMillis: Long,
        ) = Unit
    }

    private class FakeStudySchedulerSettingsRepository(
        private val settings: StudySchedulerSettings,
    ) : StudySchedulerSettingsRepository {
        override suspend fun get(): StudySchedulerSettings = settings

        override suspend fun save(
            settings: StudySchedulerSettings,
            updatedAtMillis: Long,
        ) = Unit
    }

    private class FakeStudyLocalSuspensionRepository(
        private val suspendedKanji: Set<String>,
    ) : StudyLocalSuspensionRepository {
        override fun observeSuspendedKanji(): Flow<Set<String>> = flowOf(suspendedKanji)

        override suspend fun listSuspendedKanji(): Set<String> = suspendedKanji
    }

    private class FakeStudyReviewStatsRepository(
        private val stats: AdaptiveReviewStats,
        private val streak: StudyStreak,
        private val studiedKanji: Set<String>,
    ) : StudyReviewStatsRepository {
        override suspend fun reviewStatsSince(sinceMillis: Long): AdaptiveReviewStats = stats

        override suspend fun studiedKanjiSince(sinceMillis: Long): Set<String> = studiedKanji

        override suspend fun studyStreak(nowMillis: Long): StudyStreak = streak
    }

    private object FakeSimilarKanjiIndex : SimilarKanjiIndex {
        override fun pairsWithin(kanji: Collection<String>): List<SimilarKanjiPair> = emptyList()
    }

    private companion object {
        fun localDayStart(millis: Long): Long {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = millis
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar.timeInMillis
        }
    }
}
