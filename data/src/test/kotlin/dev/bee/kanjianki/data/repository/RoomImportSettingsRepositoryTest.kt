package dev.bee.kanjianki.data.repository

import dev.bee.kanjianki.data.settings.SettingEntity
import dev.bee.kanjianki.data.settings.SettingsDao
import dev.bee.kanjianki.domain.model.importing.ImportSettings
import dev.bee.kanjianki.domain.model.importing.NewCardSortMode
import dev.bee.kanjianki.domain.model.importing.NoteTypeMapping
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomImportSettingsRepositoryTest {
    @Test
    fun emptyRoomSettingsLoadCurrentImportDefaultsAfterReset() = runBlocking {
        val repository = RoomImportSettingsRepository(FakeSettingsDao())

        assertEquals(ImportSettings(), repository.get())
    }

    @Test
    fun savesAndLoadsCustomizedImportSettings() = runBlocking {
        val dao = FakeSettingsDao()
        val repository = RoomImportSettingsRepository(dao)
        val expected = ImportSettings(
            noteMapping = NoteTypeMapping(
                noteTypeName = "Custom Japanese",
                templateName = "Front",
                expressionField = "Expression",
                readingField = "Reading",
                meaningField = "Meaning",
                sentenceField = "Sentence",
                frequencyField = "Frequency",
                frequencySortField = "FreqSort",
            ),
            matureDays = 45,
            matureSupportThreshold = 4,
            importActiveCards = true,
            importSuspendedCards = false,
            importTaggedCards = true,
            importTags = listOf("focus", "marked"),
            importWeakCards = true,
            importWeakFsrsDifficultyThreshold = 8.5,
            importWeakLapsesThreshold = 4,
            importMinMatchingCardsPerKanji = 2,
            importBrowserQueryCards = true,
            importBrowserQuery = "deck:Mining tag:kani",
            suspendedRankMin = 200,
            suspendedRankMax = 2500,
            newCardSortMode = NewCardSortMode.RETRIEVABILITY_RISK,
        )

        repository.save(expected, updatedAtMillis = 123L)

        assertEquals(expected, repository.get())
        assertTrue(dao.values.values.all { it.updatedAt == 123L })
    }

    @Test
    fun invalidPersistedImportSettingsFallBackToCurrentDefaults() = runBlocking {
        val dao = FakeSettingsDao(
            mapOf(
                "sync.import.suspended_rank_min" to "5000",
                "sync.import.suspended_rank_max" to "100",
                "sync.import.active_cards" to "true",
            ),
        )
        val repository = RoomImportSettingsRepository(dao)

        assertEquals(ImportSettings(), repository.get())
    }

    @Test
    fun loadsImportSettingsFromSingleDaoSnapshot() = runBlocking {
        val dao = FakeSettingsDao(
            initialValues = mapOf(
                "sync.import.active_cards" to "true",
                "sync.import.suspended_cards" to "false",
            ),
            failSingleKeyReads = true,
        )
        val repository = RoomImportSettingsRepository(dao)

        val loaded = repository.get()

        assertEquals(1, dao.getAllCalls)
        assertEquals(true, loaded.importActiveCards)
        assertEquals(false, loaded.importSuspendedCards)
    }

    private class FakeSettingsDao(
        initialValues: Map<String, String> = emptyMap(),
        private val failSingleKeyReads: Boolean = false,
    ) : SettingsDao {
        val values = initialValues.mapValues { (key, value) ->
            SettingEntity(key = key, value = value, updatedAt = 1L)
        }.toMutableMap()
        var getAllCalls = 0
            private set

        override fun observe(key: String): Flow<SettingEntity?> =
            flowOf(values[key])

        override suspend fun get(key: String): SettingEntity? {
            check(!failSingleKeyReads) { "Import settings must load through getAll." }
            return values[key]
        }

        override suspend fun getAll(keys: List<String>): List<SettingEntity> {
            getAllCalls += 1
            return keys.mapNotNull { key -> values[key] }
        }

        override suspend fun upsert(setting: SettingEntity) {
            values[setting.key] = setting
        }

        override suspend fun upsertAll(settings: List<SettingEntity>) {
            settings.forEach { setting ->
                values[setting.key] = setting
            }
        }
    }
}
