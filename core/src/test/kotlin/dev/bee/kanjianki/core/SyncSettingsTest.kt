package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncSettingsTest {
    @Test
    fun missingStoreUsesKikuDefaults() {
        val defaults = RecordsSyncModels.Settings.kikuDefaults()

        val actual = SyncSettings.fromStore(null)

        assertEquals(defaults.modelName, actual.modelName)
        assertEquals(defaults.expressionField, actual.expressionField)
        assertEquals(defaults.newPerDay, actual.newPerDay)
        assertFalse(actual.importActiveCards)
        assertTrue(actual.importSuspendedCards)
        assertFalse(SyncSettings.tagRepairedCards(null))
    }

    @Test
    fun storedValuesAreNormalizedWithoutDependingOnPersistenceTypes() {
        val store = FakeStore(
            SyncSettings.NOTE_TYPE_SETTING_KEY to "  Custom  ",
            SyncSettings.TEMPLATE_SETTING_KEY to "  Alternate  ",
            SyncSettings.EXPRESSION_FIELD_SETTING_KEY to "  ",
            SyncSettings.READING_FIELD_SETTING_KEY to " Kana ",
            SyncSettings.MATURE_DAYS_SETTING_KEY to "35",
            SyncSettings.MATURE_SUPPORT_THRESHOLD_SETTING_KEY to "4",
            SyncSettings.NEW_PER_DAY_SETTING_KEY to "37",
            SyncSettings.ACTIVE_QUEUE_CAP_SETTING_KEY to "42",
            SyncSettings.IMPORT_BROWSER_QUERY_CARDS_SETTING_KEY to "1",
            SyncSettings.IMPORT_BROWSER_QUERY_SETTING_KEY to " tag:kani ",
            SyncSettings.TAG_REPAIRED_CARDS_SETTING_KEY to "1",
        )

        val actual = SyncSettings.fromStore(store)

        assertEquals("Custom", actual.modelName)
        assertEquals("Alternate", actual.templateName)
        assertEquals(RecordsSyncModels.Settings.kikuDefaults().expressionField, actual.expressionField)
        assertEquals("Kana", actual.readingField)
        assertEquals(35, actual.matureDays)
        assertEquals(4, actual.matureSupportThreshold)
        assertEquals(37, actual.newPerDay)
        assertEquals(42, actual.activeQueueCap)
        assertTrue(actual.importBrowserQueryCards)
        assertEquals("tag:kani", actual.normalizedBrowserQuery())
        assertTrue(SyncSettings.tagRepairedCards(store))
    }

    @Test
    fun legacyImportDefaultsAreRepairedThroughTheCorePort() {
        val store = FakeStore(
            SyncSettings.IMPORT_ACTIVE_CARDS_SETTING_KEY to "1",
            SyncSettings.IMPORT_SUSPENDED_CARDS_SETTING_KEY to "1",
            SyncSettings.IMPORT_TAGGED_CARDS_SETTING_KEY to "0",
            SyncSettings.IMPORT_TAGS_SETTING_KEY to "",
            SyncSettings.IMPORT_WEAK_CARDS_SETTING_KEY to "0",
            SyncSettings.IMPORT_WEAK_FSRS_DIFFICULTY_SETTING_KEY to
                RecordsBase.LEGACY_IMPORT_WEAK_FSRS_DIFFICULTY.toString(),
            SyncSettings.IMPORT_WEAK_LAPSES_SETTING_KEY to RecordsBase.LEGACY_IMPORT_WEAK_LAPSES.toString(),
            SyncSettings.IMPORT_MIN_MATCHING_CARDS_SETTING_KEY to
                RecordsBase.LEGACY_IMPORT_MIN_MATCHING_CARDS_PER_KANJI.toString(),
        )

        val actual = SyncSettings.fromStore(store)

        assertFalse(actual.importActiveCards)
        assertTrue(actual.importSuspendedCards)
        assertEquals("0", store.values[SyncSettings.IMPORT_ACTIVE_CARDS_SETTING_KEY])
        assertEquals("1", store.values[SyncSettings.IMPORT_SUSPENDED_CARDS_SETTING_KEY])
    }

    @Test
    fun nonPositiveMaturitySettingsFallBackToDefaults() {
        val defaults = RecordsSyncModels.Settings.kikuDefaults()
        val store = FakeStore(
            SyncSettings.MATURE_DAYS_SETTING_KEY to "0",
            SyncSettings.MATURE_SUPPORT_THRESHOLD_SETTING_KEY to "-1",
        )

        val actual = SyncSettings.fromStore(store)

        assertEquals(defaults.matureDays, actual.matureDays)
        assertEquals(defaults.matureSupportThreshold, actual.matureSupportThreshold)
    }

    private class FakeStore(vararg entries: Pair<String, String>) : SyncSettingsStore {
        val values = entries.toMap().toMutableMap()

        override fun getIntSetting(key: String, fallback: Int): Int =
            values[key]?.toIntOrNull() ?: fallback

        override fun getStringSetting(key: String, fallback: String?): String? = values[key] ?: fallback

        override fun getDoubleSetting(key: String, fallback: Double): Double =
            values[key]?.toDoubleOrNull() ?: fallback

        override fun putIntSetting(key: String, value: Int) {
            values[key] = value.toString()
        }
    }
}
