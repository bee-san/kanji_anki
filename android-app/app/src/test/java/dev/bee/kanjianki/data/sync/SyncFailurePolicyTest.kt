package dev.bee.kanjianki.data.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncFailurePolicyTest {
    @Test
    fun `detects permanent failures through nested causes`() {
        val error = IllegalStateException(
            "top level",
            PermanentCollectionSyncException("permission missing"),
        )

        assertTrue(error.isPermanentCollectionSyncFailure())
    }

    @Test
    fun `does not mark transient failures as permanent`() {
        val error = IllegalStateException(
            "top level",
            TransientCollectionSyncException("provider timeout"),
        )

        assertFalse(error.isPermanentCollectionSyncFailure())
    }
}
