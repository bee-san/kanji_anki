package dev.bee.kanjianki.syncapi

import dev.bee.kanjianki.core.RecordsSyncModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionContractsTest {
    @Test
    fun providerSnapshotRequiresCapabilityAndIdentityEvidenceTogether() {
        val identity = CollectionSourceIdentity.create(
            CollectionProviderKind.TEST,
            "fixture-source",
            listOf(1L),
            listOf(2L),
        )

        assertThrows(IllegalArgumentException::class.java) {
            ProviderCollectionSnapshot(
                emptySnapshot(),
                setOf(CollectionCapability.READ_COLLECTION),
                identity,
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            ProviderCollectionSnapshot(
                emptySnapshot(),
                setOf(
                    CollectionCapability.READ_COLLECTION,
                    CollectionCapability.SOURCE_IDENTITY,
                ),
                null,
            )
        }
    }

    @Test
    fun providerSnapshotDefensivelyCopiesCapabilities() {
        val capabilities = mutableSetOf(CollectionCapability.READ_COLLECTION)
        val snapshot = ProviderCollectionSnapshot(emptySnapshot(), capabilities, null)

        capabilities.clear()

        assertEquals(setOf(CollectionCapability.READ_COLLECTION), snapshot.capabilities)
        assertThrows(UnsupportedOperationException::class.java) {
            (snapshot.capabilities as MutableSet).clear()
        }
    }

    @Test
    fun sourceIdentityProducesOnlyRedactedBoundedEvidenceForPresentation() {
        val identity = CollectionSourceIdentity.create(
            CollectionProviderKind.ANKIDROID,
            "private.provider.authority",
            listOf(123_456_789L, 222L),
            listOf(987_654_321L),
        )

        val evidence = identity.redactedEvidence()

        assertEquals(CollectionProviderKind.ANKIDROID, evidence.providerKind)
        assertEquals(2, evidence.noteIdSampleSize)
        assertEquals(1, evidence.cardIdSampleSize)
        assertFalse(evidence.toString().contains("private.provider.authority"))
        assertFalse(evidence.toString().contains("123456789"))
        assertFalse(evidence.toString().contains("987654321"))
    }

    @Test
    fun failureKindsExposeStableDefaultRetryability() {
        assertTrue(CollectionFailure(CollectionFailureKind.NOT_AVAILABLE, null).retryable)
        assertFalse(CollectionFailure(CollectionFailureKind.AUTH_REQUIRED, null).retryable)
        assertFalse(CollectionFailure(CollectionFailureKind.INVALID_CONFIGURATION, null).retryable)
        assertFalse(CollectionFailure(CollectionFailureKind.UNSUPPORTED_CAPABILITY, null).retryable)
        assertTrue(CollectionFailure(CollectionFailureKind.TRANSIENT, null).retryable)

        val cancelled = CollectionFailure.cancelled()
        assertEquals(CollectionFailureKind.CANCELLED, cancelled.kind)
        assertTrue(cancelled.retryable)
    }

    private fun emptySnapshot(): RecordsSyncModels.CollectionSnapshot =
        RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList())
}
