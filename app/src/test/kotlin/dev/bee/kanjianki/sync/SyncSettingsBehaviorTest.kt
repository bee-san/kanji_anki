package dev.bee.kanjianki.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.lang.reflect.Method
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncSettingsBehaviorTest {
    @Test
    fun fromStoreWithNullUsesKikuDefaultsAndSuspendedOnlyImports() {
        val defaults = RecordsSyncModels.Settings.kikuDefaults()
        val settings = SyncSettings.fromStore(null)

        assertDefaultFieldSettings(defaults, settings)
        assertDefaultLearningSettings(defaults, settings)
        assertDefaultImportSettings(defaults, settings)
    }

    @Test
    fun freshLocalStoreUsesSuspendedOnlyDefaultsWithBrowserQueryOff() {
        val store = freshStore()
        try {
            val settings = SyncSettings.fromStore(store)

            assertFalse(settings.importActiveCards)
            assertTrue(settings.importSuspendedCards)
            assertFalse(settings.importBrowserQueryCards)
            assertFalse(SyncSettings.tagRepairedCards(store))
            assertEquals("", settings.importBrowserQuery)
            assertFalse(settings.browserQueryImportEnabled())
            assertTrue(settings.hasImportSourceEnabled())
        } finally {
            store.close()
        }
    }

    @Test
    fun customizedImportFiltersRoundTripThroughLocalStoreIncludingBrowserQuery() {
        val store = freshStore()
        try {
            store.putIntSetting(SyncSettings.IMPORT_ACTIVE_CARDS_SETTING_KEY, 1)
            store.putIntSetting(SyncSettings.IMPORT_SUSPENDED_CARDS_SETTING_KEY, 0)
            store.putIntSetting(SyncSettings.IMPORT_TAGGED_CARDS_SETTING_KEY, 1)
            store.putStringSetting(SyncSettings.IMPORT_TAGS_SETTING_KEY, "kani leech")
            store.putIntSetting(SyncSettings.IMPORT_WEAK_CARDS_SETTING_KEY, 1)
            store.putDoubleSetting(SyncSettings.IMPORT_WEAK_FSRS_DIFFICULTY_SETTING_KEY, 8.5)
            store.putIntSetting(SyncSettings.IMPORT_WEAK_LAPSES_SETTING_KEY, 4)
            store.putIntSetting(SyncSettings.IMPORT_MIN_MATCHING_CARDS_SETTING_KEY, 2)
            store.putIntSetting(SyncSettings.IMPORT_BROWSER_QUERY_CARDS_SETTING_KEY, 1)
            store.putStringSetting(SyncSettings.IMPORT_BROWSER_QUERY_SETTING_KEY, " rated:30:1 ")
            store.putIntSetting(SyncSettings.TAG_REPAIRED_CARDS_SETTING_KEY, 1)

            val settings = SyncSettings.fromStore(store)

            assertTrue(settings.importActiveCards)
            assertFalse(settings.importSuspendedCards)
            assertTrue(settings.importTaggedCardsEnabled())
            assertEquals("kani leech", settings.importTagsText())
            assertTrue(settings.importWeakCards)
            assertEquals(8.5, settings.importWeakFsrsDifficultyThreshold, 0.001)
            assertEquals(4, settings.importWeakLapsesThreshold)
            assertEquals(2, settings.importMinMatchingCardsPerKanji)
            assertTrue(settings.importBrowserQueryCards)
            assertEquals("rated:30:1", settings.normalizedBrowserQuery())
            assertTrue(settings.browserQueryImportEnabled())
            assertTrue(SyncSettings.tagRepairedCards(store))
        } finally {
            store.close()
        }
    }

    @Test
    fun cachedSettingsSnapshotRefreshesAfterAnotherLocalStoreWrites() {
        val first = freshStore()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val second = LocalStore(context)
        try {
            assertEquals(RecordsSyncModels.Settings.kikuDefaults().newPerDay, SyncSettings.fromStore(first).newPerDay)

            second.putIntSetting(SyncSettings.NEW_PER_DAY_SETTING_KEY, 37)

            assertEquals(37, SyncSettings.fromStore(first).newPerDay)
        } finally {
            second.close()
            first.close()
        }
    }

    @Test
    fun multiSettingTransactionPublishesCommittedValuesToOtherStoreSnapshot() {
        val first = freshStore()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val second = LocalStore(context)
        try {
            assertFalse(first.reminderSettings().enabled)

            second.saveReminderSettings(LocalStoreBase.ReminderSettings(true, 17, 42))

            val refreshed = first.reminderSettings()
            assertTrue(refreshed.enabled)
            assertEquals(17, refreshed.hour)
            assertEquals(42, refreshed.minute)
        } finally {
            second.close()
            first.close()
        }
    }

    @Test
    fun oldDefaultRepairDoesNotEnableBrowserQueryImport() {
        val store = freshStore()
        try {
            store.putIntSetting(SyncSettings.IMPORT_ACTIVE_CARDS_SETTING_KEY, 1)
            store.putIntSetting(SyncSettings.IMPORT_SUSPENDED_CARDS_SETTING_KEY, 1)

            val settings = SyncSettings.fromStore(store)

            assertFalse(settings.importActiveCards)
            assertTrue(settings.importSuspendedCards)
            assertFalse(settings.importBrowserQueryCards)
            assertEquals("", settings.importBrowserQuery)
            assertFalse(settings.browserQueryImportEnabled())
            assertTrue(settings.hasImportSourceEnabled())
        } finally {
            store.close()
        }
    }

    @Test
    fun stringFallbackHelpersHandleNullBlankAndPresentValues() {
        assertEquals("fallback", invokeStringHelper("nonBlank", null, "fallback"))
        assertEquals("fallback", invokeStringHelper("nonBlank", "   ", "fallback"))
        assertEquals("Kiku", invokeStringHelper("nonBlank", "  Kiku  ", "fallback"))
        assertEquals("", invokeStringHelper("nullToEmpty", null))
        assertEquals("tag:kani", invokeStringHelper("nullToEmpty", "tag:kani"))
        assertEquals(
            "Expression",
            invokePrivateStatic(
                "fieldSetting",
                arrayOf(
                    LocalStore::class.java,
                    String::class.java,
                    String::class.java,
                    Boolean::class.javaPrimitiveType!!,
                ),
                null,
                SyncSettings.EXPRESSION_FIELD_SETTING_KEY,
                "Expression",
                true,
            ),
        )
    }

    @Test
    fun autoSyncSchedulerNextTriggerUsesTodayTomorrowAndAlreadySyncedBranches() {
        val settings = LocalStoreBase.AutoSyncSettings(
            true,
            true,
            19,
            30,
            0L,
            0L,
            0L,
        )
        val morning = millis(2026, Calendar.MAY, 15, 9, 0)
        val evening = millis(2026, Calendar.MAY, 15, 20, 0)

        assertEquals(
            millis(2026, Calendar.MAY, 15, 19, 30),
            AutoSyncScheduler.nextTriggerMillis(settings, morning),
        )
        assertEquals(
            millis(2026, Calendar.MAY, 16, 19, 30),
            AutoSyncScheduler.nextTriggerMillis(settings, evening),
        )
        assertEquals(
            millis(2026, Calendar.MAY, 16, 19, 30),
            AutoSyncScheduler.nextTriggerMillis(settings, morning, true),
        )
    }

    @Test
    fun autoSyncSettingsDisplayTimeKeepsZeroPadding() {
        val early = LocalStoreBase.AutoSyncSettings(
            true,
            true,
            7,
            5,
            0L,
            0L,
            0L,
        )
        val late = LocalStoreBase.AutoSyncSettings(
            true,
            true,
            23,
            59,
            0L,
            0L,
            0L,
        )

        assertEquals("07:05", early.displayTime())
        assertEquals("23:59", late.displayTime())
    }

    @Test
    fun autoSyncSettingsNormalizeClampsInvalidStoredValues() {
        val raw = LocalStoreBase.AutoSyncSettings(
            false,
            true,
            99,
            -4,
            -100L,
            -200L,
            -300L,
        )

        val normalized = normalize(raw)

        assertFalse(normalized.configured)
        assertFalse(normalized.enabled)
        assertEquals(23, normalized.hour)
        assertEquals(0, normalized.minute)
        assertEquals(0L, normalized.lastAttemptAt)
        assertEquals(0L, normalized.lastSuccessAt)
        assertEquals(0L, normalized.nextRunAt)
    }

    @Test
    fun autoSyncSettingsNormalizePreservesConfiguredEnabledSchedule() {
        val raw = LocalStoreBase.AutoSyncSettings(
            true,
            true,
            -2,
            90,
            10L,
            20L,
            30L,
        )

        val normalized = normalize(raw)

        assertTrue(normalized.configured)
        assertTrue(normalized.enabled)
        assertEquals(0, normalized.hour)
        assertEquals(59, normalized.minute)
        assertEquals(10L, normalized.lastAttemptAt)
        assertEquals(20L, normalized.lastSuccessAt)
        assertEquals(30L, normalized.nextRunAt)
    }

    private fun assertDefaultFieldSettings(
        defaults: RecordsSyncModels.Settings,
        settings: RecordsSyncModels.Settings,
    ) {
        assertEquals(defaults.modelName, settings.modelName)
        assertEquals(defaults.expressionField, settings.expressionField)
        assertEquals(defaults.readingField, settings.readingField)
        assertEquals(defaults.meaningField, settings.meaningField)
        assertEquals(defaults.sentenceField, settings.sentenceField)
        assertEquals(defaults.frequencyField, settings.frequencyField)
        assertEquals(defaults.frequencySortField, settings.frequencySortField)
    }

    private fun assertDefaultLearningSettings(
        defaults: RecordsSyncModels.Settings,
        settings: RecordsSyncModels.Settings,
    ) {
        assertEquals(defaults.suspendedRankMin, settings.suspendedRankMin)
        assertEquals(defaults.suspendedRankMax, settings.suspendedRankMax)
        assertEquals(defaults.writingTriggerMissDays, settings.writingTriggerMissDays)
        assertEquals(defaults.recognitionPromotionPasses, settings.recognitionPromotionPasses)
        assertEquals(defaults.realDueReviewsToMove, settings.realDueReviewsToMove)
        assertEquals(defaults.ladderPromotionIntervalDays, settings.ladderPromotionIntervalDays)
        assertEquals(defaults.ladderDemotionFailStreak, settings.ladderDemotionFailStreak)
        assertEquals(defaults.ladderPromotionMinPasses, settings.ladderPromotionMinPasses)
    }

    private fun assertDefaultImportSettings(
        defaults: RecordsSyncModels.Settings,
        settings: RecordsSyncModels.Settings,
    ) {
        assertFalse(settings.importActiveCards)
        assertTrue(settings.importSuspendedCards)
        assertFalse(settings.importTaggedCardsEnabled())
        assertEquals(defaults.importTags, settings.importTags)
        assertEquals(defaults.importTagsText(), settings.importTagsText())
        assertTrue(settings.importWeakCards)
        assertEquals(defaults.importWeakFsrsDifficultyThreshold, settings.importWeakFsrsDifficultyThreshold, 0.001)
        assertEquals(defaults.importWeakLapsesThreshold, settings.importWeakLapsesThreshold)
        assertEquals(defaults.importMinMatchingCardsPerKanji, settings.importMinMatchingCardsPerKanji)
        assertFalse(settings.browserQueryImportEnabled())
        assertEquals("", settings.normalizedBrowserQuery())
        assertTrue(settings.hasImportSourceEnabled())
    }

    private fun millis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance()
        calendar.clear()
        calendar.set(Calendar.YEAR, year)
        calendar.set(Calendar.MONTH, month)
        calendar.set(Calendar.DAY_OF_MONTH, day)
        calendar.set(Calendar.HOUR_OF_DAY, hour)
        calendar.set(Calendar.MINUTE, minute)
        return calendar.timeInMillis
    }

    private fun normalize(settings: LocalStoreBase.AutoSyncSettings): LocalStoreBase.AutoSyncSettings {
        val method: Method = LocalStoreBase.AutoSyncSettings::class.java.getDeclaredMethod("normalized")
        method.isAccessible = true
        return method.invoke(settings) as LocalStoreBase.AutoSyncSettings
    }

    private fun freshStore(): LocalStore {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase("kanji_anki_simple.db")
        return LocalStore(context)
    }

    private fun invokeStringHelper(name: String, value: String?, fallback: String): String {
        return invokePrivateStatic(
            name,
            arrayOf(String::class.java, String::class.java),
            value,
            fallback,
        ) as String
    }

    private fun invokeStringHelper(name: String, value: String?): String {
        return invokePrivateStatic(
            name,
            arrayOf(String::class.java),
            value,
        ) as String
    }

    private fun invokePrivateStatic(name: String, parameterTypes: Array<Class<*>>, vararg args: Any?): Any? {
        val method = SyncSettings::class.java.getDeclaredMethod(name, *parameterTypes)
        method.isAccessible = true
        return method.invoke(null, *args)
    }
}
