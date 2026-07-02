package dev.bee.kanjianki.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetentionSettingsPolicyTest {
    @Test
    fun saveRequestStoresRetentionPercent() {
        val latest = parameters()

        val result = RetentionSettingsPolicy.saveRequest(95, false, " 1-500=95% ", latest)
        val parameters = requireNotNull(result.parameters)

        assertTrue(result.valid)
        assertEquals(0.95, parameters.targetRetention, 0.001)
        assertFalse(parameters.frequencyRetentionEnabled)
        assertEquals("1-500=95%", parameters.frequencyRetentionRanges)
        assertEquals("Review retention saved.", result.message)
        assertEquals("Review retention saved.", RetentionSettingsPolicy.savedMessage())
    }

    @Test
    fun saveRequestValidatesEnabledFrequencyRanges() {
        val result = RetentionSettingsPolicy.saveRequest(
            90,
            true,
            "1-500=95%\n501-20000=85%",
            parameters(),
        )
        val parameters = requireNotNull(result.parameters)

        assertTrue(result.valid)
        assertTrue(parameters.frequencyRetentionEnabled)
        assertEquals("1-500=95%\n501-20000=85%", parameters.frequencyRetentionRanges)
    }

    @Test
    fun saveRequestRejectsInvalidEnabledFrequencyRanges() {
        val result = RetentionSettingsPolicy.saveRequest(90, true, "500-1=90%", parameters())

        assertFalse(result.valid)
        assertNull(result.parameters)
        assertEquals("Line 1: Use ranks 1-20000 in ascending order.", result.message)
    }

    @Test
    fun saveRequestIgnoresInvalidDisabledFrequencyRangesLikePreviousUi() {
        val result = RetentionSettingsPolicy.saveRequest(90, false, "500-1=90%", parameters())
        val parameters = requireNotNull(result.parameters)

        assertTrue(result.valid)
        assertFalse(parameters.frequencyRetentionEnabled)
        assertEquals("500-1=90%", parameters.frequencyRetentionRanges)
    }

    @Test
    fun saveRequestLocalizesSuccessMessageInJapaneseLocale() {
        withDefaultLocale(Locale.JAPANESE) {
            val result = RetentionSettingsPolicy.saveRequest(95, false, " 1-500=95% ", parameters())
            val parameters = requireNotNull(result.parameters)

            assertTrue(result.valid)
            assertEquals(0.95, parameters.targetRetention, 0.001)
            assertEquals("レビュー保持率を保存しました。", result.message)
            assertEquals("レビュー保持率を保存しました。", RetentionSettingsPolicy.savedMessage())
        }
    }

    private fun parameters(): RecordsSchedulerModels.SchedulerParameters {
        return RecordsSchedulerModels.SchedulerParameters(0.88)
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
