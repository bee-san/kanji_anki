package dev.bee.kanjianki.syncdomain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncModelValidatorTest {
    @Test
    fun validatesModelNameAndRequiredFields() {
        assertTrue(
            SyncModelValidator.validateModelFields(
                "Kiku",
                listOf("Expression", "Meaning"),
                "Kiku",
                listOf("Expression", "Meaning"),
            ).isEmpty(),
        )
        assertEquals(
            1,
            SyncModelValidator.validateModelFields(
                "Other",
                listOf("Expression", "Meaning"),
                "Kiku",
                listOf("Expression", "Meaning"),
            ).size,
        )
        assertEquals(
            2,
            SyncModelValidator.validateModelFields(
                "Kiku",
                listOf("Expression"),
                "Kiku",
                listOf("Expression", "Meaning", "Sentence"),
            ).size,
        )
    }

    @Test
    fun classifiesProviderFailures() {
        assertEquals("permanent_permission", SyncModelValidator.classifyProviderFailure(SecurityException("denied")))
        assertEquals("permanent_configuration", SyncModelValidator.classifyProviderFailure(RuntimeException("missing field Expression")))
        assertEquals("permanent_configuration", SyncModelValidator.classifyProviderFailure(RuntimeException("wrong model")))
        assertEquals("permanent_configuration", SyncModelValidator.classifyProviderFailure(RuntimeException("missing note type")))
        assertEquals("permanent_permission", SyncModelValidator.classifyProviderFailure(RuntimeException("provider permission denied")))
        assertEquals("retryable_provider", SyncModelValidator.classifyProviderFailure(null))
        assertEquals("retryable_provider", SyncModelValidator.classifyProviderFailure(RuntimeException()))
        assertEquals("retryable_provider", SyncModelValidator.classifyProviderFailure(RuntimeException("cursor timed out")))
    }
}
