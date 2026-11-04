package dev.bee.kanjianki.provider.ankiconnect

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiConnectCardNormalizationTest {
    @Test
    fun treatsAnyNegativeQueueAsSuspended() {
        assertTrue(AnkiConnectCardNormalization.isSuspended(-1))
        // Broader than AnkiConnect's queue == -1: buried/other negatives count.
        assertTrue(AnkiConnectCardNormalization.isSuspended(-2))
        assertTrue(AnkiConnectCardNormalization.isSuspended(-3))
    }

    @Test
    fun treatsNonNegativeQueueAsNotSuspended() {
        assertFalse(AnkiConnectCardNormalization.isSuspended(0))
        assertFalse(AnkiConnectCardNormalization.isSuspended(2))
    }

    @Test
    fun acceptsOnlyTemplateOrdinalZeroForConfiguredCards() {
        assertTrue(AnkiConnectCardNormalization.isAcceptedConfiguredOrd(0))
        assertFalse(AnkiConnectCardNormalization.isAcceptedConfiguredOrd(1))
        assertFalse(AnkiConnectCardNormalization.isAcceptedConfiguredOrd(5))
    }
}
