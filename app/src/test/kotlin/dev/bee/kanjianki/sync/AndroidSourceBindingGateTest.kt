package dev.bee.kanjianki.sync

import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.AndroidSourceBindingStateStore
import dev.bee.kanjianki.data.CollectionMirrorIdentityEvidence
import dev.bee.kanjianki.data.StoredSyncState
import dev.bee.kanjianki.syncapi.CollectionCapability
import dev.bee.kanjianki.syncapi.CollectionProviderKind
import dev.bee.kanjianki.syncapi.CollectionSourceIdentity
import dev.bee.kanjianki.syncapi.PersistedSourceBinding
import dev.bee.kanjianki.syncapi.ProviderCollectionSnapshot
import dev.bee.kanjianki.syncapi.SourceBindingReason
import dev.bee.kanjianki.syncapi.SourceBindingValidationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class AndroidSourceBindingGateTest {
    @Test
    fun qualifyingLegacyMirrorMigratesAndValidatesWithoutUserAction() {
        val store = RecordingStore(legacyEligible = true)
        val gate = AndroidSourceBindingGate(store) { "migration-salt" }
        val noteIds = (1L..20L).toList()
        val cardIds = (101L..120L).toList()

        gate.requireAccess(
            provider(noteIds = noteIds + 21L, cardIds = cardIds + 121L),
            storedState(
                hasMirror = true,
                noteIds = noteIds,
                cardIds = cardIds,
            ),
            NOW,
        )

        val migrated = requireNotNull(store.persisted)
        assertEquals(SourceBindingValidationState.VALIDATED, migrated.validationState)
        assertEquals(NOW, migrated.lastValidatedAtMillis)
        assertEquals(1, store.legacyMigrationSaves)
        assertFalse(store.legacyEligible)

        gate.requireAccess(
            provider(noteIds = noteIds + 21L, cardIds = cardIds + 121L),
            storedState(hasMirror = true, noteIds = noteIds, cardIds = cardIds),
            NOW + 1,
        )
        assertEquals(SourceBindingValidationState.VALIDATED, store.persisted?.validationState)
        assertEquals(NOW + 1, store.persisted?.lastValidatedAtMillis)
    }

    @Test
    fun insufficientLegacyOverlapPersistsRevalidationAndConsumesMarker() {
        val store = RecordingStore(legacyEligible = true)
        val gate = AndroidSourceBindingGate(store) { "migration-salt" }

        val failure = assertThrows(SourceBindingFailure::class.java) {
            gate.requireAccess(
                provider(
                    noteIds = (101L..120L).toList(),
                    cardIds = (201L..220L).toList(),
                ),
                storedState(
                    hasMirror = true,
                    noteIds = (1L..20L).toList(),
                    cardIds = (21L..40L).toList(),
                ),
                NOW,
            )
        }

        assertEquals(SourceBindingReason.INSUFFICIENT_OVERLAP, failure.reason)
        assertEquals(
            SourceBindingValidationState.REVALIDATION_REQUIRED,
            store.persisted?.validationState,
        )
        assertEquals(1, store.legacyMigrationSaves)
        assertFalse(store.legacyEligible)
    }

    @Test
    fun nonemptyDatabaseWithoutMigrationMarkerFailsAsUnknownOrigin() {
        val store = RecordingStore(legacyEligible = false)

        val failure = assertThrows(SourceBindingFailure::class.java) {
            AndroidSourceBindingGate(store).requireAccess(
                provider(),
                storedState(hasMirror = true),
                NOW,
            )
        }

        assertEquals(SourceBindingReason.UNKNOWN_ORIGIN, failure.reason)
        assertNull(store.persisted)
        assertEquals(0, store.legacyMigrationSaves)
    }

    @Test
    fun emptyDatabaseRequiresExplicitFirstBind() {
        val store = RecordingStore(legacyEligible = false)

        val failure = assertThrows(SourceBindingFailure::class.java) {
            AndroidSourceBindingGate(store).requireAccess(
                provider(),
                storedState(hasMirror = false),
                NOW,
            )
        }

        assertEquals(SourceBindingReason.FIRST_BIND_REQUIRED, failure.reason)
        assertNull(store.persisted)
    }

    @Test
    fun missingProviderIdentityFailsClosed() {
        val store = RecordingStore(legacyEligible = true)
        val provider = ProviderCollectionSnapshot(
            snapshot = RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
            capabilities = setOf(CollectionCapability.READ_COLLECTION),
            sourceIdentity = null,
        )

        val failure = assertThrows(SourceBindingFailure::class.java) {
            AndroidSourceBindingGate(store).requireAccess(
                provider,
                storedState(hasMirror = true),
                NOW,
            )
        }

        assertEquals(SourceBindingReason.NO_STABLE_IDS, failure.reason)
        assertNull(store.persisted)
        assertEquals(0, store.legacyMigrationSaves)
    }

    private fun provider(
        noteIds: List<Long> = (1L..20L).toList(),
        cardIds: List<Long> = (21L..40L).toList(),
    ): ProviderCollectionSnapshot =
        ProviderCollectionSnapshot(
            snapshot = RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
            capabilities = setOf(
                CollectionCapability.READ_COLLECTION,
                CollectionCapability.SOURCE_IDENTITY,
            ),
            sourceIdentity = CollectionSourceIdentity.create(
                providerKind = CollectionProviderKind.ANKIDROID,
                sourceKey = "com.ichi2.anki.flashcards",
                stableNoteIds = noteIds,
                stableCardIds = cardIds,
            ),
        )

    private fun storedState(
        hasMirror: Boolean,
        noteIds: List<Long> = emptyList(),
        cardIds: List<Long> = emptyList(),
    ): StoredSyncState =
        StoredSyncState(
            hasCollectionMirror = hasMirror,
            suspendedImports = emptyList(),
            unrestoredSuspendedArchiveCardIds = emptySet(),
            studyItems = emptyList(),
            latestSuccessfulSyncAtMillis = if (hasMirror) NOW - 1 else null,
            mirrorIdentityEvidence = CollectionMirrorIdentityEvidence(noteIds, cardIds),
        )

    private class RecordingStore(
        var legacyEligible: Boolean,
    ) : AndroidSourceBindingStateStore {
        var persisted: PersistedSourceBinding? = null
        var legacyMigrationSaves: Int = 0

        override fun load(): PersistedSourceBinding? = persisted

        override fun save(binding: PersistedSourceBinding) {
            persisted = binding
        }

        override fun clear() {
            persisted = null
        }

        override fun legacyAndroidMigrationEligible(): Boolean = legacyEligible

        override fun saveLegacyMigrationResult(binding: PersistedSourceBinding) {
            legacyMigrationSaves += 1
            persisted = binding
            legacyEligible = false
        }
    }

    private companion object {
        const val NOW = 1_000L
    }
}
