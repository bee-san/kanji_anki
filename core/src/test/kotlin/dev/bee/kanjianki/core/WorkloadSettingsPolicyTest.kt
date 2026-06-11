package dev.bee.kanjianki.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class WorkloadSettingsPolicyTest {
    @Test
    fun saveMaximumNormalizesMaxItemsAndPreservesCopy() {
        val request = WorkloadSettingsPolicy.saveMaximum(99)

        assertEquals(null, request.mode)
        assertEquals(null, request.workloadPercent)
        assertEquals(AdaptiveLoadPlanner.MAX_MAX_ITEMS, request.maxItems)
        assertEquals("Max items saved.", request.message)
    }

    @Test
    fun enableManualModePreservesModeAndCopy() {
        val request = WorkloadSettingsPolicy.enableManualMode()

        assertEquals(AdaptiveLoadPlanner.MODE_MANUAL, request.mode)
        assertEquals(null, request.workloadPercent)
        assertEquals(null, request.maxItems)
        assertEquals("Manual study load ready.", request.message)
    }

    @Test
    fun enableAutomaticModePreservesModeAndCopy() {
        val request = WorkloadSettingsPolicy.enableAutomaticMode()

        assertEquals(AdaptiveLoadPlanner.MODE_AUTO, request.mode)
        assertEquals(null, request.workloadPercent)
        assertEquals(null, request.maxItems)
        assertEquals("Kani will pick today's study load.", request.message)
    }

    @Test
    fun saveManualWorkloadNormalizesValuesAndPreservesCopy() {
        val request = WorkloadSettingsPolicy.saveManualWorkload(98, -10)

        assertEquals(AdaptiveLoadPlanner.MODE_MANUAL, request.mode)
        assertEquals(95, request.workloadPercent)
        assertEquals(AdaptiveLoadPlanner.MIN_MAX_ITEMS, request.maxItems)
        assertEquals("Study load saved.", request.message)
    }

    @Test
    fun workloadSaveCopyLocalizesInJapaneseLocale() {
        withDefaultLocale(Locale.JAPANESE) {
            val maximum = WorkloadSettingsPolicy.saveMaximum(99)
            val manual = WorkloadSettingsPolicy.enableManualMode()
            val automatic = WorkloadSettingsPolicy.enableAutomaticMode()
            val workload = WorkloadSettingsPolicy.saveManualWorkload(98, -10)

            assertEquals(null, maximum.mode)
            assertEquals(AdaptiveLoadPlanner.MAX_MAX_ITEMS, maximum.maxItems)
            assertEquals("最大件数を保存しました。", maximum.message)
            assertEquals(AdaptiveLoadPlanner.MODE_MANUAL, manual.mode)
            assertEquals("手動の学習量に切り替えました。", manual.message)
            assertEquals(AdaptiveLoadPlanner.MODE_AUTO, automatic.mode)
            assertEquals("今日の学習量はKaniが選びます。", automatic.message)
            assertEquals(95, workload.workloadPercent)
            assertEquals(AdaptiveLoadPlanner.MIN_MAX_ITEMS, workload.maxItems)
            assertEquals("学習量を保存しました。", workload.message)
        }
    }

    private fun withDefaultLocale(locale: Locale, block: () -> Unit) {
        val previousLocale = Locale.getDefault()
        Locale.setDefault(locale)
        try {
            block()
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
