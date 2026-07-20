package dev.bee.kanjianki.sync

import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SyncSettings
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import org.junit.Test
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Calendar
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue

class SyncSettingsCoverageTest {
    @Test
    fun fromStoreWithNullUsesKikuDefaultsAndSuspendedOnlyImports() {
        val defaults = RecordsSyncModels.Settings.kikuDefaults()
        val settings = SyncSettings.fromStore(null)

        assertDefaultFieldSettings(defaults, settings)
        assertDefaultLearningSettings(defaults, settings)
        assertDefaultImportSettings(defaults, settings)
    }

    @Test
    fun localStoreIsFinalSoJvmTestsCannotSubclassFakeStoreBranches() {
        assertTrue(Modifier.isFinal(LocalStore::class.java.modifiers))
    }

    @Test
    fun syncProgressNormalizesStageAndScanCounts() {
        for (stage in SyncProgress.Stage.values()) {
            val progress = SyncProgress.atStage(stage)

            assertSame(stage, progress.stage)
            assertEquals(0, progress.scannedCards)
            assertEquals(-1, progress.totalCards)
            assertFalse(progress.totalKnown())
            SyncProgress.NONE.onSyncProgress(progress)
        }

        val negative = SyncProgress.cardsScanned(-3, -5)
        assertSame(SyncProgress.Stage.SCANNING_CARDS, negative.stage)
        assertEquals(0, negative.scannedCards)
        assertEquals(0, negative.totalCards)
        assertTrue(negative.totalKnown())

        val known = SyncProgress.cardsScanned(7, 12)
        assertEquals(7, known.scannedCards)
        assertEquals(12, known.totalCards)
        assertTrue(known.totalKnown())
    }

    @Test
    fun autoSyncRunnerResultsExposeOutcomeFlags() {
        val success = autoSyncResult("success", "Sync complete.")
        assertTrue(success.ran)
        assertTrue(success.success)
        assertFalse(success.retryable)
        assertEquals("Sync complete.", success.message)

        val failed = autoSyncResult("failed", "Provider missing.")
        assertTrue(failed.ran)
        assertFalse(failed.success)
        assertFalse(failed.retryable)
        assertEquals("Provider missing.", failed.message)

        val skipped = autoSyncResult("skipped", "Daily sync is off.")
        assertFalse(skipped.ran)
        assertFalse(skipped.success)
        assertFalse(skipped.retryable)
        assertEquals("Daily sync is off.", skipped.message)

        val retryable = autoSyncResult("retryableFailure", "Provider locked.")
        assertTrue(retryable.ran)
        assertFalse(retryable.success)
        assertTrue(retryable.retryable)

        val deferred = autoSyncResult("deferred", "Sync already running.")
        assertFalse(deferred.ran)
        assertFalse(deferred.success)
        assertTrue(deferred.retryable)
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
        assertClose(defaults.importWeakFsrsDifficultyThreshold, settings.importWeakFsrsDifficultyThreshold)
        assertEquals(defaults.importWeakLapsesThreshold, settings.importWeakLapsesThreshold)
        assertEquals(defaults.importMinMatchingCardsPerKanji, settings.importMinMatchingCardsPerKanji)
        assertFalse(settings.browserQueryImportEnabled())
        assertEquals("", settings.normalizedBrowserQuery())
        assertTrue(settings.hasImportSourceEnabled())
    }

    private fun autoSyncResult(methodName: String, message: String): AutoSyncRunner.Result {
        val method = AutoSyncRunner.Result::class.java.getDeclaredMethod(methodName, String::class.java)
        method.isAccessible = true
        return method.invoke(null, message) as AutoSyncRunner.Result
    }

    private fun assertClose(expected: Double, actual: Double) {
        assertTrue(abs(expected - actual) <= 0.001)
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
}
