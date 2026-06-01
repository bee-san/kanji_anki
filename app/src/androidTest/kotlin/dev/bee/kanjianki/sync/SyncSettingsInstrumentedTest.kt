package dev.bee.kanjianki.sync

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.LocalStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val DATABASE_NAME = "kanji_anki_simple.db"

@RunWith(AndroidJUnit4::class)
class SyncSettingsInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun fromNullStoreUsesDefaultFallbackBranches() {
        val defaults = RecordsSyncModels.Settings.kikuDefaults()

        val settings = SyncSettings.fromStore(null)

        assertEquals(defaults.modelName, settings.modelName)
        assertEquals(defaults.expressionField, settings.expressionField)
        assertEquals(defaults.suspendedRankMin, settings.suspendedRankMin)
        assertEquals(defaults.suspendedRankMax, settings.suspendedRankMax)
        assertFalse(settings.importActiveCards)
        assertTrue(settings.importSuspendedCards)
        assertEquals(defaults.importWeakLapsesThreshold, settings.importWeakLapsesThreshold)
        assertEquals("", settings.importBrowserQuery)
        assertEquals(defaults.newCardSortMode, settings.newCardSortMode)
    }

    @Test
    fun fromStoreTrimsStoredFieldsAndKeepsRequiredExpressionFallback() {
        val defaults = RecordsSyncModels.Settings.kikuDefaults()
        val settings = settingsFromStore { store ->
            store.putStringSetting(SyncSettings.NOTE_TYPE_SETTING_KEY, "  Custom Japanese  ")
            store.putStringSetting(SyncSettings.EXPRESSION_FIELD_SETTING_KEY, "   ")
            store.putStringSetting(SyncSettings.READING_FIELD_SETTING_KEY, "  Kana  ")
            store.putStringSetting(SyncSettings.MEANING_FIELD_SETTING_KEY, "  ")
            store.putIntSetting("suspended_rank_min", 200)
            store.putIntSetting("suspended_rank_max", 2200)
            store.putIntSetting(SyncSettings.WRITING_TRIGGER_MISS_DAYS_SETTING_KEY, 9)
            store.putIntSetting(SyncSettings.RECOGNITION_PROMOTION_PASSES_SETTING_KEY, 4)
            store.putIntSetting(SyncSettings.REAL_DUE_REVIEWS_TO_MOVE_SETTING_KEY, 5)
            store.putIntSetting(SyncSettings.LADDER_PROMOTION_INTERVAL_DAYS_SETTING_KEY, 30)
            store.putIntSetting(SyncSettings.LADDER_DEMOTION_FAIL_STREAK_SETTING_KEY, 6)
            store.putIntSetting(SyncSettings.IMPORT_ACTIVE_CARDS_SETTING_KEY, 1)
            store.putIntSetting(SyncSettings.IMPORT_SUSPENDED_CARDS_SETTING_KEY, 0)
            store.putIntSetting(SyncSettings.IMPORT_TAGGED_CARDS_SETTING_KEY, 1)
            store.putStringSetting(SyncSettings.IMPORT_TAGS_SETTING_KEY, "focus marked")
            store.putIntSetting(SyncSettings.IMPORT_WEAK_CARDS_SETTING_KEY, 1)
            store.putDoubleSetting(SyncSettings.IMPORT_WEAK_FSRS_DIFFICULTY_SETTING_KEY, 8.5)
            store.putIntSetting(SyncSettings.IMPORT_WEAK_LAPSES_SETTING_KEY, 4)
            store.putIntSetting(SyncSettings.IMPORT_MIN_MATCHING_CARDS_SETTING_KEY, 2)
            store.putIntSetting(SyncSettings.IMPORT_BROWSER_QUERY_CARDS_SETTING_KEY, 1)
            store.putStringSetting(SyncSettings.IMPORT_BROWSER_QUERY_SETTING_KEY, "tag:kani")
            store.putStringSetting(
                SyncSettings.NEW_CARD_SORT_MODE_SETTING_KEY,
                RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK,
            )
        }

        assertEquals("Custom Japanese", settings.modelName)
        assertEquals(defaults.expressionField, settings.expressionField)
        assertEquals("Kana", settings.readingField)
        assertEquals("", settings.meaningField)
        assertEquals(defaults.sentenceField, settings.sentenceField)
        assertEquals(200, settings.suspendedRankMin)
        assertEquals(2200, settings.suspendedRankMax)
        assertEquals(9, settings.writingTriggerMissDays)
        assertEquals(4, settings.recognitionPromotionPasses)
        assertEquals(5, settings.realDueReviewsToMove)
        assertEquals(30, settings.ladderPromotionIntervalDays)
        assertEquals(6, settings.ladderDemotionFailStreak)
        assertTrue(settings.importActiveCards)
        assertFalse(settings.importSuspendedCards)
        assertTrue(settings.importTaggedCards)
        assertEquals(2, settings.importTags.size)
        assertTrue(settings.importWeakCards)
        assertEquals(8.5, settings.importWeakFsrsDifficultyThreshold, 0.001)
        assertEquals(4, settings.importWeakLapsesThreshold)
        assertEquals(2, settings.importMinMatchingCardsPerKanji)
        assertTrue(settings.importBrowserQueryCards)
        assertEquals("tag:kani", settings.importBrowserQuery)
        assertEquals(RecordsBase.NEW_CARD_SORT_RETRIEVABILITY_RISK, settings.newCardSortMode)
    }

    @Test
    fun fromStoreFallsBackFromUnknownNewCardSortMode() {
        val settings = settingsFromStore { store ->
            store.putStringSetting(SyncSettings.NEW_CARD_SORT_MODE_SETTING_KEY, "fastest")
        }

        assertEquals(RecordsBase.DEFAULT_NEW_CARD_SORT_MODE, settings.newCardSortMode)
    }

    @Test
    fun oldDefaultRepairTriggersForEachLegacyImportSetting() {
        assertOldDefaultRepair { store -> store.putIntSetting(SyncSettings.IMPORT_ACTIVE_CARDS_SETTING_KEY, 1) }
        assertOldDefaultRepair { store -> store.putIntSetting(SyncSettings.IMPORT_SUSPENDED_CARDS_SETTING_KEY, 1) }
        assertOldDefaultRepair { store -> store.putIntSetting(SyncSettings.IMPORT_TAGGED_CARDS_SETTING_KEY, 0) }
        assertOldDefaultRepair { store -> store.putStringSetting(SyncSettings.IMPORT_TAGS_SETTING_KEY, "") }
        assertOldDefaultRepair { store -> store.putIntSetting(SyncSettings.IMPORT_WEAK_CARDS_SETTING_KEY, 0) }
        assertOldDefaultRepair {
            store -> store.putDoubleSetting(
                SyncSettings.IMPORT_WEAK_FSRS_DIFFICULTY_SETTING_KEY,
                RecordsBase.DEFAULT_IMPORT_WEAK_FSRS_DIFFICULTY,
            )
        }
        assertOldDefaultRepair {
            store -> store.putIntSetting(
                SyncSettings.IMPORT_WEAK_LAPSES_SETTING_KEY,
                RecordsBase.DEFAULT_IMPORT_WEAK_LAPSES,
            )
        }
        assertOldDefaultRepair {
            store -> store.putIntSetting(
                SyncSettings.IMPORT_MIN_MATCHING_CARDS_SETTING_KEY,
                RecordsBase.DEFAULT_IMPORT_MIN_MATCHING_CARDS_PER_KANJI,
            )
        }
    }

    @Test
    fun oldDefaultRepairSkipsEachNonMatchingImportSetting() {
        assertNoOldDefaultRepair { store -> store.putIntSetting(SyncSettings.IMPORT_ACTIVE_CARDS_SETTING_KEY, 0) }
        assertNoOldDefaultRepair { store -> store.putIntSetting(SyncSettings.IMPORT_SUSPENDED_CARDS_SETTING_KEY, 0) }
        assertNoOldDefaultRepair { store -> store.putIntSetting(SyncSettings.IMPORT_TAGGED_CARDS_SETTING_KEY, 1) }
        assertNoOldDefaultRepair { store -> store.putStringSetting(SyncSettings.IMPORT_TAGS_SETTING_KEY, "focus") }
        assertNoOldDefaultRepair { store -> store.putIntSetting(SyncSettings.IMPORT_WEAK_CARDS_SETTING_KEY, 1) }
        assertNoOldDefaultRepair {
            store -> store.putDoubleSetting(
                SyncSettings.IMPORT_WEAK_FSRS_DIFFICULTY_SETTING_KEY,
                RecordsBase.DEFAULT_IMPORT_WEAK_FSRS_DIFFICULTY + 0.5,
            )
        }
        assertNoOldDefaultRepair {
            store -> store.putIntSetting(
                SyncSettings.IMPORT_WEAK_LAPSES_SETTING_KEY,
                RecordsBase.DEFAULT_IMPORT_WEAK_LAPSES + 1,
            )
        }
        assertNoOldDefaultRepair {
            store -> store.putIntSetting(
                SyncSettings.IMPORT_MIN_MATCHING_CARDS_SETTING_KEY,
                RecordsBase.DEFAULT_IMPORT_MIN_MATCHING_CARDS_PER_KANJI + 1,
            )
        }
    }

    @Test
    fun browserQueryOnlySettingDoesNotTriggerOldImportRepair() {
        freshStore().use { store ->
            store.putStringSetting(SyncSettings.IMPORT_BROWSER_QUERY_SETTING_KEY, "tag:kani")

            val settings = SyncSettings.fromStore(store)

            assertEquals("tag:kani", settings.importBrowserQuery)
            assertEquals(-99, store.getIntSetting(SyncSettings.IMPORT_ACTIVE_CARDS_SETTING_KEY, -99))
            assertEquals(-99, store.getIntSetting(SyncSettings.IMPORT_SUSPENDED_CARDS_SETTING_KEY, -99))
        }
    }

    private fun assertOldDefaultRepair(mutation: StoreMutation) {
        freshStore().use { store ->
            mutation(store)

            val settings = SyncSettings.fromStore(store)

            assertFalse(settings.importActiveCards)
            assertTrue(settings.importSuspendedCards)
            assertEquals(0, store.getIntSetting(SyncSettings.IMPORT_ACTIVE_CARDS_SETTING_KEY, -99))
            assertEquals(1, store.getIntSetting(SyncSettings.IMPORT_SUSPENDED_CARDS_SETTING_KEY, -99))
        }
    }

    private fun assertNoOldDefaultRepair(mutation: StoreMutation) {
        freshStore().use { store ->
            mutation(store)

            SyncSettings.fromStore(store)

            val repaired = store.getIntSetting(SyncSettings.IMPORT_ACTIVE_CARDS_SETTING_KEY, -99) == 0 &&
                store.getIntSetting(SyncSettings.IMPORT_SUSPENDED_CARDS_SETTING_KEY, -99) == 1
            assertFalse(repaired)
        }
    }

    private fun settingsFromStore(mutation: StoreMutation): RecordsSyncModels.Settings {
        freshStore().use { store ->
            mutation(store)
            return SyncSettings.fromStore(store)
        }
    }

    private fun freshStore(): LocalStore {
        context.deleteDatabase(DATABASE_NAME)
        return LocalStore(context)
    }

    private fun interface StoreMutation {
        operator fun invoke(store: LocalStore)
    }
}
