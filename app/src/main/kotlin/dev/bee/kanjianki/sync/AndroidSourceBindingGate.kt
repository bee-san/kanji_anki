package dev.bee.kanjianki.sync

import dev.bee.kanjianki.data.AndroidSourceBindingStateStore
import dev.bee.kanjianki.data.StoredSyncState
import dev.bee.kanjianki.syncapi.CollectionFailure
import dev.bee.kanjianki.syncapi.CollectionFailureKind
import dev.bee.kanjianki.syncapi.ProviderCollectionSnapshot
import dev.bee.kanjianki.syncapi.RedactedSourceIdentityEvidence
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
                null,
            )
        val persisted = try {
            store.load()
        } catch (error: RuntimeException) {
            throw SourceBindingFailure(
                SourceBindingReason.UNKNOWN_ORIGIN,
                "The saved collection binding is invalid and needs recovery.",
                SourceBindingEvidence(candidate.redactedEvidence(), 0, 0),
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
                    databaseIsEmpty = storedState.databaseIsEmpty,
                    nowMillis = nowMillis,
                ),
            ).also(::persistDecision)
        }
        if (!decision.allowsCollectionAccess) {
            val priorNoteSampleSize = persisted?.noteIdDigests?.size
                ?: storedState.mirrorIdentityEvidence.stableNoteIds.size
            val priorCardSampleSize = persisted?.cardIdDigests?.size
                ?: storedState.mirrorIdentityEvidence.stableCardIds.size
            throw SourceBindingFailure(
                decision.reason,
                messageFor(decision.reason),
                SourceBindingEvidence(
                    candidate.redactedEvidence(),
                    priorNoteSampleSize,
                    priorCardSampleSize,
                ),
            )
        }
    }

    fun recover(
        provider: ProviderCollectionSnapshot,
        storedState: StoredSyncState,
        action: SourceBindingAction,
        backupConfirmed: Boolean,
        nowMillis: Long,
    ): SourceBindingDecision {
        require(action != SourceBindingAction.VALIDATE) {
            "Recovery requires an explicit bind or rebind action"
        }
        val candidate = provider.sourceIdentity
            ?: throw SourceBindingFailure(
                SourceBindingReason.NO_STABLE_IDS,
                "The collection source did not provide identity evidence.",
                null,
            )
        val persisted = try {
            store.load()
        } catch (error: RuntimeException) {
            throw SourceBindingFailure(
                SourceBindingReason.UNKNOWN_ORIGIN,
                "The saved collection binding is invalid and needs recovery.",
                SourceBindingEvidence(candidate.redactedEvidence(), 0, 0),
                error,
            )
        }
        val evidence = SourceBindingEvidence(
            candidate = candidate.redactedEvidence(),
            priorNoteSampleSize = persisted?.noteIdDigests?.size ?: 0,
            priorCardSampleSize = persisted?.cardIdDigests?.size ?: 0,
        )
        val decision = SourceBindingPolicy.evaluate(
            SourceBindingRequest(
                persisted = persisted,
                candidate = candidate,
                databaseIsEmpty = storedState.databaseIsEmpty,
                action = action,
                backupConfirmed = backupConfirmed,
                replacementSalt = when (action) {
                    SourceBindingAction.FIRST_BIND -> newSalt()
                    SourceBindingAction.REBIND -> if (backupConfirmed) newSalt() else null
                    SourceBindingAction.VALIDATE -> null
                },
                nowMillis = nowMillis,
            ),
        )
        val binding = decision.bindingToPersist
        if (decision.allowsCollectionAccess && binding != null) {
            store.saveExplicitRecoveryResult(binding, decision.resetScope)
        } else {
            persistDecision(decision)
        }
        if (!decision.allowsCollectionAccess) {
            throw SourceBindingFailure(
                decision.reason,
                messageFor(decision.reason),
                evidence,
            )
        }
        return decision
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
    @JvmField val evidence: SourceBindingEvidence? = null,
    cause: Throwable? = null,
) : CollectionFailure(
    kind = CollectionFailureKind.INVALID_CONFIGURATION,
    message = message,
    retryable = false,
    cause = cause,
)

internal data class SourceBindingEvidence(
    val candidate: RedactedSourceIdentityEvidence,
    val priorNoteSampleSize: Int,
    val priorCardSampleSize: Int,
) {
    init {
        require(priorNoteSampleSize >= 0) { "prior note sample size must not be negative" }
        require(priorCardSampleSize >= 0) { "prior card sample size must not be negative" }
    }
}
