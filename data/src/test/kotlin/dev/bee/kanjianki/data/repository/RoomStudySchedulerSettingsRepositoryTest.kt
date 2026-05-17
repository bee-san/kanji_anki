package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.settings.SettingEntity
import dev.bee.kanjianki.data.settings.SettingsDao
import dev.bee.kanjianki.domain.model.importing.ImportSettings
import dev.bee.kanjianki.domain.model.importing.NewCardSortMode
import dev.bee.kanjianki.domain.model.study.StudyRung
import dev.bee.kanjianki.domain.repository.StudySchedulerSettings
import dev.bee.kanjianki.domain.scheduler.AdaptiveStudyPlanner
import dev.bee.kanjianki.domain.scheduler.AdaptiveWorkloadMode
import dev.bee.kanjianki.domain.scheduler.AdaptiveWorkloadPolicy
import dev.bee.kanjianki.domain.scheduler.StudyLadderSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomStudySchedulerSettingsRepositoryTest {
    @Test
    fun emptySettingsReturnCleanRewriteDefaults() = runBlocking {
        val repository = RoomStudySchedulerSettingsRepository(FakeSettingsDao())

        val settings = repository.get()

        assertEquals(24, settings.activeQueueCap)
        assertEquals(3, settings.newPerDay)
        assertEquals(StudyLadderSettings.defaults, settings.ladderSettings)
        assertEquals(
            AdaptiveWorkloadPolicy.fromSettings(
                AdaptiveStudyPlanner.DEFAULT_WORKLOAD_PERCENT,
                AdaptiveStudyPlanner.DEFAULT_WORKLOAD_MODE,
                AdaptiveStudyPlanner.DEFAULT_MAX_ITEMS,
            ),
            settings.workloadPolicy,
        )
    }

    @Test
    fun persistedSettingsRoundTripWithNormalization() = runBlocking {
        val dao = FakeSettingsDao(
            "study.queue.active_cap" to "7",
            "study.queue.new_per_day" to "2",
            "study_ladder_order" to "font_meaning,kanji_meaning,word_reading",
            "study_ladder_enabled" to "font_meaning word_reading",
            "ladder_promotion_interval_days" to "30",
            "ladder_demotion_fail_streak" to "4",
            AdaptiveStudyPlanner.SETTING_KEY to "37",
            AdaptiveStudyPlanner.MODE_SETTING_KEY to AdaptiveStudyPlanner.MODE_MANUAL,
            AdaptiveStudyPlanner.MAX_ITEMS_SETTING_KEY to "99",
        )
        val repository = RoomStudySchedulerSettingsRepository(dao)

        val settings = repository.get()

        assertEquals(7, settings.activeQueueCap)
        assertEquals(2, settings.newPerDay)
        assertEquals(StudyRung.FONT_MEANING, settings.ladderSettings.orderedRungs.first())
        assertEquals(setOf(StudyRung.FONT_MEANING, StudyRung.WORD_READING), settings.ladderSettings.enabledRungs)
        assertEquals(30, settings.ladderSettings.promotionIntervalDays)
        assertEquals(4, settings.ladderSettings.demotionFailStreak)
        assertEquals(35, settings.workloadPolicy.workloadPercent)
        assertEquals(AdaptiveWorkloadMode.MANUAL, settings.workloadPolicy.mode)
        assertEquals(AdaptiveStudyPlanner.MAX_MAX_ITEMS, settings.workloadPolicy.maxItems)
    }

    @Test
    fun invalidLadderFallsBackWithoutPoisoningOtherSettings() = runBlocking {
        val repository = RoomStudySchedulerSettingsRepository(
            FakeSettingsDao(
                "study.queue.active_cap" to "-7",
                "study.queue.new_per_day" to "-2",
                "study_ladder_enabled" to "similar_kanji",
                "ladder_promotion_interval_days" to "0",
            ),
        )

        val settings = repository.get()

        assertEquals(0, settings.activeQueueCap)
        assertEquals(0, settings.newPerDay)
        assertEquals(StudyLadderSettings.defaults, settings.ladderSettings)
    }

    @Test
    fun savedSettingsUseStableRoomKeys() = runBlocking {
        val dao = FakeSettingsDao()
        val repository = RoomStudySchedulerSettingsRepository(dao)

        repository.save(
            StudySchedulerSettings(
                activeQueueCap = 9,
                newPerDay = 4,
                ladderSettings = StudyLadderSettings(
                    orderedRungs = StudyRung.defaultOrder,
                    enabledRungs = setOf(StudyRung.KANJI_MEANING, StudyRung.WORD_READING),
                    promotionIntervalDays = 28,
                    demotionFailStreak = 5,
                ),
                workloadPolicy = AdaptiveWorkloadPolicy.of(AdaptiveWorkloadMode.MANUAL, 45, 6),
            ),
            updatedAtMillis = 123L,
        )

        assertEquals("9", dao.values.getValue("study.queue.active_cap").value)
        assertEquals("4", dao.values.getValue("study.queue.new_per_day").value)
        assertEquals(
            StudyRung.defaultOrder.joinToString(",") { it.wireName },
            dao.values.getValue("study_ladder_order").value,
        )
        assertEquals("kanji_meaning,word_reading", dao.values.getValue("study_ladder_enabled").value)
        assertEquals("28", dao.values.getValue("ladder_promotion_interval_days").value)
        assertEquals("5", dao.values.getValue("ladder_demotion_fail_streak").value)
        assertEquals("45", dao.values.getValue(AdaptiveStudyPlanner.SETTING_KEY).value)
        assertEquals(AdaptiveStudyPlanner.MODE_MANUAL, dao.values.getValue(AdaptiveStudyPlanner.MODE_SETTING_KEY).value)
        assertEquals("6", dao.values.getValue(AdaptiveStudyPlanner.MAX_ITEMS_SETTING_KEY).value)
        assertTrue(dao.values.values.all { it.updatedAt == 123L })
    }

    @Test
    fun queueSeedSettingsUseImportThresholdAndSortMode() {
        val settings = StudySchedulerSettings(activeQueueCap = 8, newPerDay = 2)

        val seedSettings = settings.queueSeedSettings(
            ImportSettings(
                matureSupportThreshold = 5,
                newCardSortMode = NewCardSortMode.KANI_WEAKNESS,
            ),
        )

        assertEquals(8, seedSettings.activeQueueCap)
        assertEquals(2, seedSettings.newPerDay)
        assertEquals(5, seedSettings.matureSupportThreshold)
        assertEquals(NewCardSortMode.KANI_WEAKNESS, seedSettings.newCardSortMode)
    }

    private class FakeSettingsDao(
        vararg pairs: Pair<String, String>,
    ) : SettingsDao {
        val values = linkedMapOf<String, SettingEntity>()

        init {
            for ((key, value) in pairs) {
                values[key] = SettingEntity(key = key, value = value, updatedAt = 1L)
            }
        }

        override fun observe(key: String): Flow<SettingEntity?> = emptyFlow()

        override suspend fun get(key: String): SettingEntity? = values[key]

        override suspend fun getAll(keys: List<String>): List<SettingEntity> =
            keys.mapNotNull(values::get)

        override suspend fun upsert(setting: SettingEntity) {
            values[setting.key] = setting
        }

        override suspend fun upsertAll(settings: List<SettingEntity>) {
            settings.forEach { values[it.key] = it }
        }
    }
}
