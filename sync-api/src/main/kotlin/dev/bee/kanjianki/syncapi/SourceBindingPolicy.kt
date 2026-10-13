package dev.bee.kanjianki.syncapi

object SourceBindingPolicy {
    private const val LARGE_SAMPLE_MINIMUM = 16
    private const val REQUIRED_OVERLAP_PERCENT = 90

    @JvmStatic
    fun evaluate(request: SourceBindingRequest): SourceBindingDecision {
        val persisted = request.persisted
        if (persisted != null && persisted.version != PersistedSourceBinding.CURRENT_VERSION) {
            return SourceBindingDecision(
                SourceBindingDecisionKind.REJECT,
                SourceBindingReason.UNSUPPORTED_VERSION,
            )
        }
        return when (request.action) {
            SourceBindingAction.FIRST_BIND -> firstBind(request)
            SourceBindingAction.REBIND -> rebind(request)
            SourceBindingAction.VALIDATE -> validate(request)
        }
    }

    private fun firstBind(request: SourceBindingRequest): SourceBindingDecision {
        if (request.persisted != null || !request.databaseIsEmpty) {
            return SourceBindingDecision(
                SourceBindingDecisionKind.REBIND_REQUIRED,
                SourceBindingReason.UNKNOWN_ORIGIN,
                request.persisted?.requiresRevalidation(),
            )
        }
        if (!request.candidate.hasStableIds()) {
            return SourceBindingDecision(
                SourceBindingDecisionKind.FIRST_BIND_REQUIRED,
                SourceBindingReason.NO_STABLE_IDS,
            )
        }
        val salt = request.replacementSalt?.takeIf(String::isNotBlank)
            ?: return SourceBindingDecision(
                SourceBindingDecisionKind.FIRST_BIND_REQUIRED,
                SourceBindingReason.FIRST_BIND_REQUIRED,
            )
        return SourceBindingDecision(
            SourceBindingDecisionKind.ALLOW,
            SourceBindingReason.EXPLICIT_BIND,
            request.candidate.toBinding(salt, request.nowMillis),
        )
    }

    private fun validate(request: SourceBindingRequest): SourceBindingDecision {
        val persisted = request.persisted
            ?: return SourceBindingDecision(
                if (request.databaseIsEmpty) {
                    SourceBindingDecisionKind.FIRST_BIND_REQUIRED
                } else {
                    SourceBindingDecisionKind.REBIND_REQUIRED
                },
                if (request.databaseIsEmpty) {
                    SourceBindingReason.FIRST_BIND_REQUIRED
                } else {
                    SourceBindingReason.UNKNOWN_ORIGIN
                },
            )
        val evidence = request.candidate.opaqueEvidence(persisted.bindingSalt)
        if (evidence.providerKindDigest != persisted.providerKindDigest) {
            return revalidationRequired(persisted, SourceBindingReason.PROVIDER_KIND_CHANGED)
        }
        if (evidence.sourceKeyDigest != persisted.sourceKeyDigest) {
            return revalidationRequired(persisted, SourceBindingReason.SOURCE_KEY_CHANGED)
        }
        val overlap = overlap(persisted, evidence)
        if (overlap.priorCount == 0 || overlap.candidateCount == 0) {
            return revalidationRequired(persisted, SourceBindingReason.NO_STABLE_IDS)
        }
        if (!overlap.qualifies()) {
            return revalidationRequired(persisted, SourceBindingReason.INSUFFICIENT_OVERLAP)
        }
        return SourceBindingDecision(
            SourceBindingDecisionKind.ALLOW,
            SourceBindingReason.VALIDATED,
            evidence.toBinding(persisted.bindingSalt, request.nowMillis),
        )
    }

    private fun rebind(request: SourceBindingRequest): SourceBindingDecision {
        val persisted = request.persisted
            ?: return SourceBindingDecision(
                SourceBindingDecisionKind.REBIND_REQUIRED,
                SourceBindingReason.UNKNOWN_ORIGIN,
            )
        if (!request.backupConfirmed) {
            return SourceBindingDecision(
                SourceBindingDecisionKind.REBIND_REQUIRED,
                SourceBindingReason.BACKUP_REQUIRED,
                persisted.requiresRevalidation(),
            )
        }
        val evidenceWithPriorSalt = request.candidate.opaqueEvidence(persisted.bindingSalt)
        val overlap = overlap(persisted, evidenceWithPriorSalt)
        if (overlap.priorCount == 0 || overlap.candidateCount == 0) {
            return SourceBindingDecision(
                SourceBindingDecisionKind.REBIND_REQUIRED,
                SourceBindingReason.NO_STABLE_IDS,
                persisted.requiresRevalidation(),
            )
        }
        if (!overlap.qualifies()) {
            return SourceBindingDecision(
                SourceBindingDecisionKind.REBIND_REQUIRED,
                SourceBindingReason.INSUFFICIENT_OVERLAP,
                persisted.requiresRevalidation(),
            )
        }
        val replacementSalt = request.replacementSalt?.takeIf {
            it.isNotBlank() && it != persisted.bindingSalt
        }
            ?: return SourceBindingDecision(
                SourceBindingDecisionKind.REBIND_REQUIRED,
                SourceBindingReason.FRESH_SALT_REQUIRED,
                persisted.requiresRevalidation(),
            )
        return SourceBindingDecision(
            SourceBindingDecisionKind.ALLOW,
            SourceBindingReason.EXPLICIT_REBIND,
            request.candidate.toBinding(replacementSalt, request.nowMillis),
            SourceBindingResetScope.PROVIDER_PROJECTIONS_AND_WRITE_RECEIPTS,
        )
    }

    private fun revalidationRequired(
        persisted: PersistedSourceBinding,
        reason: SourceBindingReason,
    ): SourceBindingDecision =
        SourceBindingDecision(
            SourceBindingDecisionKind.REBIND_REQUIRED,
            reason,
            persisted.requiresRevalidation(),
        )

    private fun overlap(
        persisted: PersistedSourceBinding,
        evidence: OpaqueSourceEvidence,
    ): Overlap {
        val prior = persisted.noteIdDigests.toSet() + persisted.cardIdDigests
        val candidate = evidence.noteIdDigests.toSet() + evidence.cardIdDigests
        return Overlap(
            priorCount = prior.size,
            candidateCount = candidate.size,
            matchCount = prior.intersect(candidate).size,
        )
    }

    private data class Overlap(
        val priorCount: Int,
        val candidateCount: Int,
        val matchCount: Int,
    ) {
        fun qualifies(): Boolean =
            if (priorCount < LARGE_SAMPLE_MINIMUM) {
                matchCount == priorCount
            } else {
                matchCount >= LARGE_SAMPLE_MINIMUM &&
                    matchCount * 100 >= priorCount * REQUIRED_OVERLAP_PERCENT
            }
    }

    private fun CollectionSourceIdentity.toBinding(
        salt: String,
        nowMillis: Long,
    ): PersistedSourceBinding = opaqueEvidence(salt).toBinding(salt, nowMillis)

    private fun OpaqueSourceEvidence.toBinding(
        salt: String,
        nowMillis: Long,
    ): PersistedSourceBinding =
        PersistedSourceBinding(
            version = PersistedSourceBinding.CURRENT_VERSION,
            providerKindDigest = providerKindDigest,
            sourceKeyDigest = sourceKeyDigest,
            bindingSalt = salt,
            noteIdDigests = noteIdDigests,
            cardIdDigests = cardIdDigests,
            validationState = SourceBindingValidationState.VALIDATED,
            lastValidatedAtMillis = nowMillis,
        )

    private fun PersistedSourceBinding.requiresRevalidation(): PersistedSourceBinding =
        PersistedSourceBinding(
            version = version,
            providerKindDigest = providerKindDigest,
            sourceKeyDigest = sourceKeyDigest,
            bindingSalt = bindingSalt,
            noteIdDigests = noteIdDigests,
            cardIdDigests = cardIdDigests,
            validationState = SourceBindingValidationState.REVALIDATION_REQUIRED,
            lastValidatedAtMillis = lastValidatedAtMillis,
        )
}
