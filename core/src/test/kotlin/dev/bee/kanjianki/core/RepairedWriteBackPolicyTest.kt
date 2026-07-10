package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RepairedWriteBackPolicyTest {
    @Test
    fun retiredRepairProposesFullySuspendedNote() {
        val proposal = plan(
            states = listOf(state("徴", StudyLadderRules.STATE_RETIRED, 0, null, 0.0)),
            sources = listOf(source("徴", 10, 1), source("徴", 11, 1)),
            cards = listOf(card(10, 1, true), card(11, 1, true)),
        )

        assertEquals(setOf(1L), proposal.noteIdsToTag)
        assertEquals(setOf(10L, 11L), proposal.cardIdsByNote[1L])
        assertEquals(setOf("徴"), proposal.kanjiByNote[1L])
        assertEquals(listOf("徴"), proposal.repairedKanji)
        assertEquals(2, proposal.candidateSourceCount)
        assertEquals(0, proposal.rejectedCardCount)
        assertTrue(!proposal.isEmpty())
    }

    @Test
    fun highConfidenceImprovingMatureEvidencePassesButEachGateIsRequired() {
        val states = listOf(
            state("徴", "review", 2, KanjiRepairEvidencePolicy.Status.IMPROVING, 0.75),
            state("微", "review", 1, KanjiRepairEvidencePolicy.Status.IMPROVING, 0.99),
            state("撤", "review", 2, KanjiRepairEvidencePolicy.Status.STABLE, 0.99),
            state("徹", "review", 2, KanjiRepairEvidencePolicy.Status.IMPROVING, 0.749),
            state("澄", "review", 2, KanjiRepairEvidencePolicy.Status.IMPROVING, Double.NaN),
        )
        val sources = states.mapIndexed { index, value -> source(value.kanji, (10 + index).toLong(), (1 + index).toLong()) }
        val cards = sources.map { card(it.cardId, it.noteId, true) }

        val proposal = plan(states, sources, cards)

        assertEquals(setOf(1L), proposal.noteIdsToTag)
        assertEquals(listOf("徴"), proposal.repairedKanji)
    }

    @Test
    fun activeSiblingOrUnrepairedSuspendedSiblingRejectsWholeNote() {
        val states = listOf(state("徴", StudyLadderRules.STATE_RETIRED, 0, null, 0.0))
        val activeSibling = plan(
            states,
            listOf(source("徴", 10, 1)),
            listOf(card(10, 1, true), card(11, 1, false)),
        )
        val unrepairedSibling = plan(
            states,
            listOf(source("徴", 20, 2), source("微", 21, 2)),
            listOf(card(20, 2, true), card(21, 2, true)),
        )

        assertTrue(activeSibling.isEmpty())
        assertEquals(1, activeSibling.rejectedCardCount)
        assertTrue(unrepairedSibling.isEmpty())
        assertEquals(1, unrepairedSibling.rejectedCardCount)
    }

    @Test
    fun restoredStampDeduplicatesProposal() {
        val proposal = plan(
            listOf(state("徴", StudyLadderRules.STATE_RETIRED, 0, null, 0.0)),
            listOf(source("徴", 10, 1, restoredAt = 123L)),
            listOf(card(10, 1, true)),
        )

        assertTrue(proposal.isEmpty())
        assertEquals(0, proposal.candidateSourceCount)
    }

    @Test
    fun reusedCardIdWithDifferentLiveNoteFailsClosed() {
        val proposal = plan(
            listOf(state("徴", StudyLadderRules.STATE_RETIRED, 0, null, 0.0)),
            listOf(source("徴", 10, 1)),
            listOf(card(10, 99, true)),
        )

        assertTrue(proposal.isEmpty())
        assertEquals(1, proposal.candidateSourceCount)
        assertEquals(1, proposal.rejectedCardCount)
        assertTrue(99L !in proposal.noteIdsToTag)
    }

    @Test
    fun ambiguousDuplicateCardIdentityFailsClosed() {
        val proposal = plan(
            listOf(state("徴", StudyLadderRules.STATE_RETIRED, 0, null, 0.0)),
            listOf(source("徴", 10, 1)),
            listOf(card(10, 1, true), card(10, 99, true)),
        )

        assertTrue(proposal.isEmpty())
        assertEquals(1, proposal.rejectedCardCount)
    }

    @Test
    fun emptyInputsAreNoOpAndKanjiOrderingIsDeterministic() {
        assertTrue(RepairedWriteBackPolicy.plan(null, null, null, 2).isEmpty())

        val proposal = plan(
            listOf(
                state("微", StudyLadderRules.STATE_RETIRED, 0, null, 0.0),
                state("徴", StudyLadderRules.STATE_RETIRED, 0, null, 0.0),
            ),
            listOf(source("微", 10, 1), source("徴", 10, 1)),
            listOf(card(10, 1, true)),
        )

        assertEquals(listOf("微", "徴"), proposal.repairedKanji)
    }

    private fun plan(
        states: List<RepairedWriteBackPolicy.RepairState>,
        sources: List<RepairedWriteBackPolicy.Source>,
        cards: List<RepairedWriteBackPolicy.Card>,
    ) = RepairedWriteBackPolicy.plan(states, sources, cards, 2)

    private fun state(
        kanji: String,
        studyState: String,
        mature: Int,
        status: KanjiRepairEvidencePolicy.Status?,
        confidence: Double,
    ) = RepairedWriteBackPolicy.RepairState(kanji, studyState, mature, status, confidence)

    private fun source(kanji: String, cardId: Long, noteId: Long, restoredAt: Long? = null) =
        RepairedWriteBackPolicy.Source(kanji, cardId, noteId, restoredAt)

    private fun card(cardId: Long, noteId: Long, suspended: Boolean) =
        RepairedWriteBackPolicy.Card(cardId, noteId, suspended)
}
