package dev.bee.kanjianki.syncdomain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncModelValidatorBridgeCoverageTest {
    @Test
    fun coversStaticBridgeMethods() {
        assertTrue(
            SyncModelValidator.validateModelFields(
                "Kiku",
                listOf("Expression", "Meaning"),
                "Kiku",
                listOf("Expression", "Meaning"),
            ).isEmpty(),
        )
        assertEquals("retryable_provider", SyncModelValidator.classifyProviderFailure(null))
    }
}
