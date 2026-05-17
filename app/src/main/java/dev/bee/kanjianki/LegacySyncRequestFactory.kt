package dev.bee.kanjianki

import android.content.Context
import dev.bee.kanjianki.R
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SimilarKanjiIndex
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.domain.common.AppClock
import dev.bee.kanjianki.domain.sync.RunSourceMirrorSyncRequest
import dev.bee.kanjianki.sync.SyncSettings
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Calendar

class LegacySyncRequestFactory(
    context: Context,
    private val store: LocalStore,
    private val clock: AppClock = object : AppClock {
        override fun nowMillis(): Long = System.currentTimeMillis()
    },
) {
    private val appContext = context.applicationContext

    fun request(
        settings: RecordsSyncModels.Settings = SyncSettings.fromStore(store),
        nowMillis: Long = clock.nowMillis(),
    ): RunSourceMirrorSyncRequest {
        val startOfDayMillis = startOfDay(nowMillis)
        return RunSourceMirrorSyncRequest(
            importSettings = LegacySyncMappers.toImportSettings(settings),
            queueSeedContext = LegacySyncMappers.toQueueSeedContext(
                settings = settings,
                ladderSettings = store.studyLadderSettings(),
                locallySuspendedKanji = store.locallySuspendedKanji(),
                startOfDayMillis = startOfDayMillis,
                recentStats = store.reviewStatsSince(nowMillis - WEEK_MILLIS),
                currentStreakDays = store.studyStreak(nowMillis).currentDays,
                studiedToday = store.studiedKanjiSince(startOfDayMillis),
                workloadPercent = store.adaptiveLoadWorkPercent(),
                workloadMode = store.adaptiveLoadMode(),
                maxItems = store.adaptiveLoadMaxItems(),
            ),
            similarKanjiIndex = loadSimilarKanjiIndex(),
        )
    }

    fun loadSimilarKanjiIndex(): dev.bee.kanjianki.domain.model.similar.SimilarKanjiIndex =
        appContext.resources.openRawResource(R.raw.similar_kanji).use { input ->
            InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
                CoreSimilarKanjiIndexAdapter(SimilarKanjiIndex.parseTsv(reader))
            }
        }

    private fun startOfDay(nowMillis: Long): Long {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = nowMillis
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        return calendar.timeInMillis
    }

    private companion object {
        const val WEEK_MILLIS = 7 * 86_400_000L
    }
}
