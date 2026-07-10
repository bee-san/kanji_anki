package dev.bee.kanjianki.core

import dev.bee.kanjianki.syncdomain.ProviderArchiveCleanupPolicy

/**
 * Proposes conservative note-level `kani_repaired` writes. A note is eligible only
 * when every card belonging to it is suspended and every suspended card is backed by
 * an unstamped source for a kanji whose repair evidence passes the gate.
 */
object RepairedWriteBackPolicy {
    const val MIN_EVIDENCE_CONFIDENCE: Double = 0.75

    @JvmRecord
    data class RepairState(
        val kanji: String,
        val studyState: String,
        val matureSupportCount: Int,
        val evidenceStatus: KanjiRepairEvidencePolicy.Status?,
        val evidenceConfidence: Double,
    )

    @JvmRecord
    data class Source(
        val kanji: String,
        val cardId: Long,
        val noteId: Long,
        val restoredAtMillis: Long?,
    )

    @JvmRecord
    data class Card(val cardId: Long, val noteId: Long, val suspended: Boolean)

    data class Proposal(
        val noteIdsToTag: Set<Long>,
        val cardIdsByNote: Map<Long, Set<Long>>,
        val kanjiByNote: Map<Long, Set<String>>,
        val repairedKanji: List<String>,
        val candidateSourceCount: Int,
        val rejectedCardCount: Int,
    ) {
        fun isEmpty(): Boolean = noteIdsToTag.isEmpty()
    }

    @JvmStatic
    fun plan(
        repairStates: List<RepairState>?,
        sources: List<Source>?,
        cards: List<Card>?,
        matureSupportThreshold: Int,
    ): Proposal {
        val safeCards = cards.orEmpty()
        if (repairStates.isNullOrEmpty() || sources.isNullOrEmpty() || safeCards.isEmpty()) {
            return emptyProposal()
        }
        val threshold = matureSupportThreshold.coerceAtLeast(1)
        val eligibleKanji = repairStates
            .asSequence()
            .filter { isEligible(it, threshold) }
            .map { it.kanji }
            .filter { it.isNotBlank() }
            .toSet()
        if (eligibleKanji.isEmpty()) {
            return emptyProposal()
        }

        val unstampedSources = sources.filter {
            it.kanji in eligibleKanji && (it.restoredAtMillis ?: 0L) <= 0L
        }
        if (unstampedSources.isEmpty()) {
            return emptyProposal()
        }

        // A source row is historical identity, not merely a card-id selector. Anki
        // collection restores can reuse a card id for a different note; treating that
        // collision as a match would tag an unrelated note. Ambiguous duplicate card
        // rows also fail closed instead of letting list order choose an identity.
        val trustedCardsById = safeCards
            .groupBy { it.cardId }
            .mapNotNull { (cardId, matches) ->
                val first = matches.first()
                if (matches.all { it.noteId == first.noteId && it.suspended == first.suspended }) {
                    cardId to first
                } else {
                    null
                }
            }
            .toMap()
        val candidateSources = unstampedSources.filter { source ->
            trustedCardsById[source.cardId]?.noteId == source.noteId
        }
        val identityRejected = unstampedSources.size - candidateSources.size
        if (candidateSources.isEmpty()) {
            return Proposal(
                emptySet(),
                emptyMap(),
                emptyMap(),
                emptyList(),
                unstampedSources.size,
                identityRejected,
            )
        }
        val selectedCardIds = candidateSources.mapTo(linkedSetOf()) { it.cardId }
        val cleanup = ProviderArchiveCleanupPolicy.plan(
            trustedCardsById.values.map {
                ProviderArchiveCleanupPolicy.Card(it.cardId, it.noteId, it.suspended)
            },
            selectedCardIds,
        )
        val sourceNoteIds = candidateSources.mapTo(linkedSetOf()) { it.noteId }
        val noteIds = cleanup.notesToTag.intersect(sourceNoteIds)
        if (noteIds.isEmpty()) {
            return Proposal(
                emptySet(),
                emptyMap(),
                emptyMap(),
                emptyList(),
                unstampedSources.size,
                cleanup.alreadyFailedCards + identityRejected,
            )
        }

        val cardIdsByNote = linkedMapOf<Long, MutableSet<Long>>()
        val kanjiByNote = linkedMapOf<Long, MutableSet<String>>()
        for (source in candidateSources) {
            if (source.noteId !in noteIds) continue
            cardIdsByNote.getOrPut(source.noteId) { linkedSetOf() }.add(source.cardId)
            kanjiByNote.getOrPut(source.noteId) { linkedSetOf() }.add(source.kanji)
        }
        val immutableCards = cardIdsByNote.mapValues { (_, values) -> values.toSet() }
        val immutableKanji = kanjiByNote.mapValues { (_, values) -> values.toSet() }
        return Proposal(
            noteIdsToTag = noteIds.toSet(),
            cardIdsByNote = immutableCards,
            kanjiByNote = immutableKanji,
            repairedKanji = immutableKanji.values.flatten().distinct().sorted(),
            candidateSourceCount = unstampedSources.size,
            rejectedCardCount = cleanup.alreadyFailedCards + identityRejected,
        )
    }

    private fun isEligible(state: RepairState, matureSupportThreshold: Int): Boolean {
        if (state.studyState == StudyLadderRules.STATE_RETIRED) {
            return true
        }
        return state.matureSupportCount >= matureSupportThreshold &&
            state.evidenceStatus == KanjiRepairEvidencePolicy.Status.IMPROVING &&
            state.evidenceConfidence.isFinite() &&
            state.evidenceConfidence >= MIN_EVIDENCE_CONFIDENCE
    }

    private fun emptyProposal(): Proposal = Proposal(
        emptySet(),
        emptyMap(),
        emptyMap(),
        emptyList(),
        0,
        0,
    )
}
