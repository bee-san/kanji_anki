package dev.bee.kanjianki.syncapi.testing

import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.syncapi.CollectionCancellation
import dev.bee.kanjianki.syncapi.CollectionCapability
import dev.bee.kanjianki.syncapi.CollectionFailure
import dev.bee.kanjianki.syncapi.CollectionFailureKind
import dev.bee.kanjianki.syncapi.CollectionGateway
import dev.bee.kanjianki.syncapi.CollectionProgress
import dev.bee.kanjianki.syncapi.CollectionProgressListener

object CollectionGatewayContractKit {
    data class Observation(
        val noteCount: Int,
        val cardCount: Int,
        val progress: List<CollectionProgress>,
    )

    @JvmStatic
    fun verifyReadContract(
        gateway: CollectionGateway,
        settings: RecordsSyncModels.Settings,
        expectedNoteCount: Int,
        expectedCardCount: Int,
    ): Observation {
        val status = gateway.status()
        check(status.isReady()) { "gateway must be ready for the contract fixture" }
        check(status.supports(CollectionCapability.READ_COLLECTION))
        val noteTypes = gateway.noteTypes()
        check(noteTypes.isNotEmpty()) { "ready gateway must expose a note type fixture" }
        check(noteTypes.all { it.name.isNotBlank() && it.fields.isNotEmpty() })

        val progress = ArrayList<CollectionProgress>()
        val result = gateway.readProviderCollection(
            settings,
            CollectionProgressListener(progress::add),
            CollectionCancellation.NONE,
        )
        check(result.snapshot.notes.size == expectedNoteCount)
        check(result.snapshot.cards.size == expectedCardCount)
        check(result.snapshot.cards.all { card ->
            result.snapshot.notes.any { note -> note.noteId == card.noteId }
        }) {
            "every canonical card must reference a returned note"
        }
        check(result.capabilities.containsAll(status.capabilities))
        if (CollectionCapability.SOURCE_IDENTITY in result.capabilities) {
            checkNotNull(result.sourceIdentity)
        } else {
            check(result.sourceIdentity == null)
        }
        if (CollectionCapability.FSRS_MEMORY_STATE !in result.capabilities) {
            check(result.snapshot.cards.all { card ->
                card.fsrsStability == null &&
                    card.fsrsDifficulty == null &&
                    card.fsrsRetrievability == null
            })
        }
        check(progress.isNotEmpty()) { "provider reads must report progress" }

        val cancelled = try {
            gateway.readProviderCollection(
                settings,
                CollectionProgressListener.NONE,
                CollectionCancellation { true },
            )
            null
        } catch (failure: CollectionFailure) {
            failure
        }
        check(cancelled?.kind == CollectionFailureKind.CANCELLED) {
            "pre-cancelled provider reads must fail as CANCELLED"
        }
        check(cancelled.retryable)

        return Observation(expectedNoteCount, expectedCardCount, progress.toList())
    }
}
