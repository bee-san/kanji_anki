package dev.bee.kanjianki

import android.content.Context
import dev.bee.kanjianki.domain.common.AppClock
import dev.bee.kanjianki.domain.model.similar.SimilarKanjiIndex
import dev.bee.kanjianki.domain.repository.ImportSettingsRepository
import dev.bee.kanjianki.domain.repository.StudyLocalSuspensionRepository
import dev.bee.kanjianki.domain.repository.StudyReviewStatsRepository
import dev.bee.kanjianki.domain.repository.StudySchedulerSettingsRepository
import dev.bee.kanjianki.domain.sync.RunSourceMirrorSyncRequest
import dev.bee.kanjianki.domain.sync.SyncAdaptivePlanContext
import dev.bee.kanjianki.domain.sync.SyncStudyQueueSeedContext
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Calendar

class RoomSyncRequestFactory(
    private val importSettingsRepository: ImportSettingsRepository,
    private val schedulerSettingsRepository: StudySchedulerSettingsRepository,
    private val localSuspensions: StudyLocalSuspensionRepository,
    private val reviewStats: StudyReviewStatsRepository,
    private val similarKanjiIndexProvider: () -> SimilarKanjiIndex?,
    private val clock: AppClock = SYSTEM_CLOCK,
) {
    constructor(
        context: Context,
        importSettingsRepository: ImportSettingsRepository,
        schedulerSettingsRepository: StudySchedulerSettingsRepository,
        localSuspensions: StudyLocalSuspensionRepository,
        reviewStats: StudyReviewStatsRepository,
        clock: AppClock = SYSTEM_CLOCK,
    ) : this(
        importSettingsRepository = importSettingsRepository,
        schedulerSettingsRepository = schedulerSettingsRepository,
        localSuspensions = localSuspensions,
        reviewStats = reviewStats,
        similarKanjiIndexProvider = { loadSimilarKanjiIndex(context.applicationContext) },
        clock = clock,
    )

    suspend fun request(nowMillis: Long = clock.nowMillis()): RunSourceMirrorSyncRequest {
        val importSettings = importSettingsRepository.get()
        val schedulerSettings = schedulerSettingsRepository.get()
        val startOfDayMillis = startOfDay(nowMillis)
        return RunSourceMirrorSyncRequest(
            importSettings = importSettings,
            queueSeedContext = SyncStudyQueueSeedContext(
                settings = schedulerSettings.queueSeedSettings(importSettings),
                startOfDayMillis = startOfDayMillis,
                ladderSettings = schedulerSettings.ladderSettings,
                locallySuspendedKanji = localSuspensions.listSuspendedKanji(),
                adaptiveContext = SyncAdaptivePlanContext(
                    recentStats = reviewStats.reviewStatsSince(nowMillis - WEEK_MILLIS),
                    currentStreakDays = reviewStats.currentStreakDays(nowMillis),
                    studiedToday = reviewStats.studiedKanjiSince(startOfDayMillis),
                    workloadPolicy = schedulerSettings.workloadPolicy,
                ),
            ),
            similarKanjiIndex = similarKanjiIndexProvider(),
        )
    }

    private companion object {
        const val WEEK_MILLIS = 7 * 86_400_000L
        val SYSTEM_CLOCK = object : AppClock {
            override fun nowMillis(): Long = System.currentTimeMillis()
        }

        fun startOfDay(nowMillis: Long): Long {
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = nowMillis
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar.timeInMillis
        }

        fun loadSimilarKanjiIndex(context: Context): SimilarKanjiIndex =
            context.resources.openRawResource(R.raw.similar_kanji).use { input ->
                InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
                    CoreSimilarKanjiIndexAdapter(
                        dev.bee.kanjianki.core.SimilarKanjiIndex.parseTsv(reader),
                    )
                }
            }
    }
}
