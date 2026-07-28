package dev.bee.kanjianki.sync

import dev.bee.kanjianki.data.AndroidSourceBindingStateStore
import dev.bee.kanjianki.data.StoredSyncState
import dev.bee.kanjianki.syncapi.CollectionFailure
import dev.bee.kanjianki.syncapi.CollectionFailureKind
import dev.bee.kanjianki.syncapi.ProviderCollectionSnapshot
import dev.bee.kanjianki.syncapi.SourceBindingAction
import dev.bee.kanjianki.syncapi.SourceBindingDecision
import dev.bee.kanjianki.syncapi.SourceBindingDecisionKind
import dev.bee.kanjianki.syncapi.SourceBindingPolicy
import dev.bee.kanjianki.syncapi.SourceBindingReason
import dev.bee.kanjianki.syncapi.SourceBindingRequest
import dev.bee.kanjianki.syncapi.SourceBindingSalts

internal fun interface SyncSourceBindingGate {
    @Throws(SourceBindingFailure::class)
    fun requireAccess(
        provider: ProviderCollectionSnapshot,
        storedState: StoredSyncState,
        nowMillis: Long,
    )

    companion object {
        @JvmField
        val ALLOW_ALL = SyncSourceBindingGate { _, _, _ -> }
    }
}

internal class AndroidSourceBindingGate(
    private val store: AndroidSourceBindingStateStore,
    private val newSalt: () -> String = { SourceBindingSalts.random() },
) : SyncSourceBindingGate {
    override fun requireAccess(
        provider: ProviderCollectionSnapshot,
        storedState: StoredSyncState,
        nowMillis: Long,
    ) {
        val candidate = provider.sourceIdentity
            ?: throw SourceBindingFailure(
                SourceBindingReason.NO_STABLE_IDS,
                "The collection source did not provide identity evidence.",
            )
        val persisted = try {
            store.load()
        } catch (error: RuntimeException) {
            throw SourceBindingFailure(
                SourceBindingReason.UNKNOWN_ORIGIN,
                "The saved collection binding is invalid and needs recovery.",
                error,
            )
        }
        val decision = if (
            persisted == null &&
            storedState.hasCollectionMirror &&
            store.legacyAndroidMigrationEligible()
        ) {
            migrateExisting(candidate, storedState, nowMillis)
        } else {
            SourceBindingPolicy.evaluate(
                SourceBindingRequest(
                    persisted = persisted,
                    candidate = candidate,
                    databaseIsEmpty = !storedState.hasCollectionMirror,
                    nowMillis = nowMillis,
                ),
            ).also(::persistDecision)
        }
        if (!decision.allowsCollectionAccess) {
            throw SourceBindingFailure(decision.reason, messageFor(decision.reason))
        }
    }

    private fun migrateExisting(
        candidate: dev.bee.kanjianki.syncapi.CollectionSourceIdentity,
        storedState: StoredSyncState,
        nowMillis: Long,
    ): SourceBindingDecision {
        val priorCandidate = candidate.withStableIds(
            storedState.mirrorIdentityEvidence.stableNoteIds,
            storedState.mirrorIdentityEvidence.stableCardIds,
        )
        val seed = SourceBindingPolicy.evaluate(
            SourceBindingRequest(
                persisted = null,
                candidate = priorCandidate,
                databaseIsEmpty = true,
                action = SourceBindingAction.FIRST_BIND,
                replacementSalt = newSalt(),
                nowMillis = nowMillis,
            ),
        )
        val priorBinding = seed.bindingToPersist
            ?: return SourceBindingDecision(
                SourceBindingDecisionKind.REBIND_REQUIRED,
                SourceBindingReason.NO_STABLE_IDS,
            )
        val decision = SourceBindingPolicy.evaluate(
            SourceBindingRequest(
                persisted = priorBinding,
                candidate = candidate,
                databaseIsEmpty = false,
                nowMillis = nowMillis,
            ),
        )
        val migrationResult = decision.bindingToPersist
            ?: priorBinding
        store.saveLegacyMigrationResult(migrationResult)
        return decision
    }

    private fun persistDecision(decision: SourceBindingDecision) {
        decision.bindingToPersist?.let(store::save)
    }

    private fun messageFor(reason: SourceBindingReason): String = when (reason) {
        SourceBindingReason.FIRST_BIND_REQUIRED ->
            "Confirm this local AnkiDroid collection before the first sync."
        SourceBindingReason.UNKNOWN_ORIGIN ->
            "Kani cannot verify which AnkiDroid collection this database came from."
        SourceBindingReason.PROVIDER_KIND_CHANGED,
        SourceBindingReason.SOURCE_KEY_CHANGED,
        -> "The available AnkiDroid source does not match this Kani profile."
        SourceBindingReason.NO_STABLE_IDS,
        SourceBindingReason.INSUFFICIENT_OVERLAP,
        -> "The available AnkiDroid collection does not contain enough matching identity evidence."
        SourceBindingReason.BACKUP_REQUIRED ->
            "Create a durable safety backup before rebinding this Kani profile."
        SourceBindingReason.FRESH_SALT_REQUIRED ->
            "Kani could not create fresh binding evidence."
        SourceBindingReason.UNSUPPORTED_VERSION ->
            "This collection binding version is not supported."
        SourceBindingReason.VALIDATED,
        SourceBindingReason.EXPLICIT_BIND,
        SourceBindingReason.EXPLICIT_REBIND,
        -> "The collection source is validated."
    }
}

internal class SourceBindingFailure(
    @JvmField val reason: SourceBindingReason,
    message: String,
    cause: Throwable? = null,
) : CollectionFailure(
    kind = CollectionFailureKind.INVALID_CONFIGURATION,
    message = message,
    retryable = false,
    cause = cause,
)
