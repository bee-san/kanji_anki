package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncValidatorTest {
    @Test
    fun validatesKikuRequiredFields() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()

        assertTrue(SyncValidator.validateModelFields("Kiku", settings.requiredFields(), settings).isEmpty())
        assertEquals(1, SyncValidator.validateModelFields("Other", settings.requiredFields(), settings).size)
        assertEquals(1, SyncValidator.validateModelFields(null, settings.requiredFields(), settings).size)
        assertTrue(SyncValidator.validateModelFields("Kiku", listOf("Expression"), settings).size > 1)
        assertTrue(
            SyncValidator.validateModelFields("Kiku", listOf("Expression"), settings)
                .first()
                .contains("Configured note type Kiku"),
        )
    }

    @Test
    fun ignoresBlankOptionalCustomFields() {
        val settings = RecordsSyncModels.Settings(
            "Custom Japanese",
            "Mining",
            "Front",
            "",
            "Back",
            "",
            "",
            "",
            21,
            2,
            100,
            3000,
            24,
            3,
            RecordsBase.DEFAULT_WRITING_TRIGGER_MISS_DAYS,
        )

        assertEquals(listOf("Front", "Back"), settings.requiredFields())
        assertTrue(SyncValidator.validateModelFields("Custom Japanese", listOf("Front", "Back"), settings).isEmpty())
    }

    @Test
    fun classifiesProviderFailures() {
        assertEquals("permanent_permission", SyncValidator.classifyProviderFailure(SecurityException("denied")))
        assertEquals("permanent_configuration", SyncValidator.classifyProviderFailure(RuntimeException("missing field Expression")))
        assertEquals("permanent_configuration", SyncValidator.classifyProviderFailure(RuntimeException("wrong model")))
        assertEquals("permanent_configuration", SyncValidator.classifyProviderFailure(RuntimeException("missing note type")))
        assertEquals("permanent_permission", SyncValidator.classifyProviderFailure(RuntimeException("provider permission denied")))
        assertEquals("retryable_provider", SyncValidator.classifyProviderFailure(null))
        assertEquals("retryable_provider", SyncValidator.classifyProviderFailure(RuntimeException()))
        assertEquals("retryable_provider", SyncValidator.classifyProviderFailure(RuntimeException("cursor timed out")))
    }
}
