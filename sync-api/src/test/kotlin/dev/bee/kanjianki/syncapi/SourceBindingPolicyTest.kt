package dev.bee.kanjianki.syncapi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceBindingPolicyTest {
    @Test
    fun emptyDatabaseRequiresExplicitFirstBindThenPersistsOnlyOpaqueEvidence() {
        val candidate = identity(noteIds = 1L..20L, cardIds = 101L..120L)

        val pending = evaluate(candidate = candidate, databaseIsEmpty = true)
        val bound = evaluate(
            candidate = candidate,
            databaseIsEmpty = true,
            action = SourceBindingAction.FIRST_BIND,
            replacementSalt = SALT_A,
        )

        assertEquals(SourceBindingDecisionKind.FIRST_BIND_REQUIRED, pending.kind)
        assertEquals(SourceBindingDecisionKind.ALLOW, bound.kind)
        val persisted = requireNotNull(bound.bindingToPersist)
        assertEquals(SourceBindingValidationState.VALIDATED, persisted.validationState)
        assertFalse(persisted.toString().contains(PROFILE_A))
        assertFalse(persisted.toString().contains("101"))
        assertFalse(candidate.toString().contains(PROFILE_A))
        assertTrue(persisted.providerKindDigest.matches(PersistedSourceBinding.DIGEST))
        assertTrue(persisted.sourceKeyDigest.matches(PersistedSourceBinding.DIGEST))
    }

    @Test
    fun unchangedSourceValidatesAndRefreshesItsBoundedSamples() {
        val candidate = identity(noteIds = 1L..80L, cardIds = 101L..180L)
        val persisted = bind(candidate)

        val decision = evaluate(candidate = candidate, persisted = persisted, nowMillis = 200L)

        assertEquals(SourceBindingDecisionKind.ALLOW, decision.kind)
        assertEquals(SourceBindingReason.VALIDATED, decision.reason)
        assertEquals(64, decision.bindingToPersist?.noteIdDigests?.size)
        assertEquals(64, decision.bindingToPersist?.cardIdDigests?.size)
        assertEquals(200L, decision.bindingToPersist?.lastValidatedAtMillis)
    }

    @Test
    fun renamedProfileNeverAutoBindsEvenWithPerfectIdOverlap() {
        val original = identity(sourceKey = PROFILE_A)
        val persisted = bind(original)
        val renamed = identity(sourceKey = "Renamed profile")

        val decision = evaluate(candidate = renamed, persisted = persisted)

        assertEquals(SourceBindingDecisionKind.REBIND_REQUIRED, decision.kind)
        assertEquals(SourceBindingReason.SOURCE_KEY_CHANGED, decision.reason)
        assertEquals(
            SourceBindingValidationState.REVALIDATION_REQUIRED,
            decision.bindingToPersist?.validationState,
        )
    }

    @Test
    fun providerKindChangeNeverAutoBindsEvenWithPerfectIdOverlap() {
        val persisted = bind(identity())
        val desktop = identity(providerKind = CollectionProviderKind.ANKI_CONNECT)

        val decision = evaluate(candidate = desktop, persisted = persisted)

        assertEquals(SourceBindingDecisionKind.REBIND_REQUIRED, decision.kind)
        assertEquals(SourceBindingReason.PROVIDER_KIND_CHANGED, decision.reason)
    }

    @Test
    fun oneToFifteenPriorIdsRequireEveryIdToMatch() {
        val original = identity(noteIds = 1L..10L, cardIds = LongRange.EMPTY)
        val persisted = bind(original)
        val all = evaluate(candidate = original, persisted = persisted)
        val missingOne = evaluate(
            candidate = identity(noteIds = 1L..9L, cardIds = LongRange.EMPTY),
            persisted = persisted,
        )

        assertEquals(SourceBindingDecisionKind.ALLOW, all.kind)
        assertEquals(SourceBindingReason.INSUFFICIENT_OVERLAP, missingOne.reason)
    }

    @Test
    fun largerSamplesRequireSixteenMatchesAndNinetyPercentOverlap() {
        val persisted = bind(identity(noteIds = 1L..20L, cardIds = LongRange.EMPTY))
        val eighteenMatches = evaluate(
            candidate = identity(noteIds = 1L..18L, cardIds = LongRange.EMPTY),
            persisted = persisted,
        )
        val seventeenMatches = evaluate(
            candidate = identity(noteIds = 1L..17L, cardIds = LongRange.EMPTY),
            persisted = persisted,
        )

        assertEquals(SourceBindingDecisionKind.ALLOW, eighteenMatches.kind)
        assertEquals(SourceBindingReason.INSUFFICIENT_OVERLAP, seventeenMatches.reason)
    }

    @Test
    fun noIdsAndUnknownOriginFailClosed() {
        val noIds = identity(noteIds = LongRange.EMPTY, cardIds = LongRange.EMPTY)
        val noIdDecision = evaluate(
            candidate = noIds,
            persisted = bind(identity()),
        )
        val restoredDecision = evaluate(
            candidate = identity(),
            databaseIsEmpty = false,
        )

        assertEquals(SourceBindingReason.NO_STABLE_IDS, noIdDecision.reason)
        assertEquals(SourceBindingDecisionKind.REBIND_REQUIRED, restoredDecision.kind)
        assertEquals(SourceBindingReason.UNKNOWN_ORIGIN, restoredDecision.reason)
    }

    @Test
    fun explicitRebindRequiresBackupOverlapAndFreshSalt() {
        val original = identity()
        val persisted = bind(original)
        val changedKind = identity(providerKind = CollectionProviderKind.ANKI_CONNECT)

        val withoutBackup = evaluate(
            candidate = changedKind,
            persisted = persisted,
            action = SourceBindingAction.REBIND,
            replacementSalt = SALT_B,
        )
        val insufficient = evaluate(
            candidate = identity(
                providerKind = CollectionProviderKind.ANKI_CONNECT,
                noteIds = 1L..10L,
                cardIds = 101L..110L,
            ),
            persisted = persisted,
            action = SourceBindingAction.REBIND,
            backupConfirmed = true,
            replacementSalt = SALT_B,
        )
        val reusedSalt = evaluate(
            candidate = changedKind,
            persisted = persisted,
            action = SourceBindingAction.REBIND,
            backupConfirmed = true,
            replacementSalt = SALT_A,
        )
        val rebound = evaluate(
            candidate = changedKind,
            persisted = persisted,
            action = SourceBindingAction.REBIND,
            backupConfirmed = true,
            replacementSalt = SALT_B,
        )

        assertEquals(SourceBindingReason.BACKUP_REQUIRED, withoutBackup.reason)
        assertEquals(SourceBindingReason.INSUFFICIENT_OVERLAP, insufficient.reason)
        assertEquals(SourceBindingReason.FRESH_SALT_REQUIRED, reusedSalt.reason)
        assertEquals(SourceBindingDecisionKind.ALLOW, rebound.kind)
        assertEquals(SourceBindingReason.EXPLICIT_REBIND, rebound.reason)
        assertEquals(SALT_B, rebound.bindingToPersist?.bindingSalt)
        assertNotEquals(persisted.providerKindDigest, rebound.bindingToPersist?.providerKindDigest)
    }

    @Test
    fun unsignedOrderingFreezesTheLowestSixtyFourIdsPerKind() {
        val ids = (1L..70L).toMutableList().apply {
            add(-1L)
            reverse()
        }
        val first = bind(identity(noteIds = ids, cardIds = ids))
        val reordered = bind(identity(noteIds = ids.shuffled(), cardIds = ids.shuffled()))

        assertEquals(first.noteIdDigests, reordered.noteIdDigests)
        assertEquals(first.cardIdDigests, reordered.cardIdDigests)
        assertEquals(64, first.noteIdDigests.size)
    }

    @Test
    fun unsupportedPersistedVersionIsRejectedBeforeIdentityComparison() {
        val current = bind(identity())
        val unsupported = PersistedSourceBinding(
            version = PersistedSourceBinding.CURRENT_VERSION + 1,
            providerKindDigest = current.providerKindDigest,
            sourceKeyDigest = current.sourceKeyDigest,
            bindingSalt = current.bindingSalt,
            noteIdDigests = current.noteIdDigests,
            cardIdDigests = current.cardIdDigests,
            validationState = current.validationState,
            lastValidatedAtMillis = current.lastValidatedAtMillis,
        )

        val decision = evaluate(candidate = identity(), persisted = unsupported)

        assertEquals(SourceBindingDecisionKind.REJECT, decision.kind)
        assertEquals(SourceBindingReason.UNSUPPORTED_VERSION, decision.reason)
    }

    private fun evaluate(
        candidate: CollectionSourceIdentity,
        persisted: PersistedSourceBinding? = null,
        databaseIsEmpty: Boolean = false,
        action: SourceBindingAction = SourceBindingAction.VALIDATE,
        backupConfirmed: Boolean = false,
        replacementSalt: String? = null,
        nowMillis: Long = 100L,
    ): SourceBindingDecision =
        SourceBindingPolicy.evaluate(
            SourceBindingRequest(
                persisted = persisted,
                candidate = candidate,
                databaseIsEmpty = databaseIsEmpty,
                action = action,
                backupConfirmed = backupConfirmed,
                replacementSalt = replacementSalt,
                nowMillis = nowMillis,
            ),
        )

    private fun bind(candidate: CollectionSourceIdentity): PersistedSourceBinding =
        requireNotNull(
            evaluate(
                candidate = candidate,
                databaseIsEmpty = true,
                action = SourceBindingAction.FIRST_BIND,
                replacementSalt = SALT_A,
            ).bindingToPersist,
        )

    private fun identity(
        providerKind: CollectionProviderKind = CollectionProviderKind.ANKIDROID,
        sourceKey: String = PROFILE_A,
        noteIds: Iterable<Long> = 1L..40L,
        cardIds: Iterable<Long> = 101L..140L,
    ): CollectionSourceIdentity =
        CollectionSourceIdentity.create(
            providerKind,
            sourceKey,
            noteIds.toList(),
            cardIds.toList(),
        )

    companion object {
        private const val PROFILE_A = "Private profile name"
        private const val SALT_A = "database-local-random-salt-a"
        private const val SALT_B = "database-local-random-salt-b"
    }
}
