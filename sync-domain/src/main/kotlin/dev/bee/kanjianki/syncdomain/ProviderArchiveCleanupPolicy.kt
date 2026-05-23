package dev.bee.kanjianki.syncdomain

class ProviderArchiveCleanupPolicy private constructor() {
    @JvmRecord
    data class Card(val cardId: Long, val noteId: Long, val suspended: Boolean)

    @JvmRecord
    data class SelectedSource(val cardId: Long, val suspended: Boolean)

    @JvmRecord
    data class CleanupPlan(val sourceCards: Int, val notesToTag: Set<Long>, val alreadyFailedCards: Int) {
        fun hasSuspendedCards(): Boolean = sourceCards > 0
    }

    private class CleanupIndex private constructor(
        private val cardsByNote: Map<Long, Int>,
        private val suspendedByNote: Map<Long, Int>,
        private val selectedSuspendedByNote: Map<Long, Int>,
        val suspendedCards: List<Card>,
    ) {
        fun notesFullySuspended(): Set<Long> {
            val notes = LinkedHashSet<Long>()
            for (card in suspendedCards) {
                if (fullySelectedSuspendedNote(card.noteId)) {
                    notes.add(card.noteId)
                }
            }
            return notes
        }

        fun partiallySuspendedCardCount(): Int {
            var failed = 0
            for (card in suspendedCards) {
                if (!fullySelectedSuspendedNote(card.noteId)) {
                    failed++
                }
            }
            return failed
        }

        private fun fullySelectedSuspendedNote(noteId: Long): Boolean {
            return cardsByNote[noteId] == suspendedByNote[noteId] &&
                suspendedByNote[noteId] == selectedSuspendedByNote[noteId]
        }

        companion object {
            fun from(cards: List<Card>, selectedSuspendedCardIds: Set<Long>?): CleanupIndex {
                val cardsByNote = LinkedHashMap<Long, Int>()
                val suspendedByNote = LinkedHashMap<Long, Int>()
                val selectedSuspendedByNote = LinkedHashMap<Long, Int>()
                val suspendedCards = ArrayList<Card>()
                for (card in cards) {
                    cardsByNote[card.noteId] = cardsByNote.getOrDefault(card.noteId, 0) + 1
                    if (card.suspended) {
                        suspendedByNote[card.noteId] = suspendedByNote.getOrDefault(card.noteId, 0) + 1
                        if (selectedSuspendedCardIds == null || selectedSuspendedCardIds.contains(card.cardId)) {
                            suspendedCards.add(card)
                            selectedSuspendedByNote[card.noteId] = selectedSuspendedByNote.getOrDefault(card.noteId, 0) + 1
                        }
                    }
                }
                return CleanupIndex(cardsByNote, suspendedByNote, selectedSuspendedByNote, suspendedCards)
            }
        }
    }

    companion object {
        @JvmStatic
        fun plan(cards: List<Card>, selectedSuspendedCardIds: Set<Long>?): CleanupPlan {
            val index = CleanupIndex.from(cards, selectedSuspendedCardIds)
            return CleanupPlan(
                index.suspendedCards.size,
                index.notesFullySuspended(),
                index.partiallySuspendedCardCount()
            )
        }

        @JvmStatic
        fun selectedSuspendedCardIds(sources: List<SelectedSource>?): Set<Long>? {
            if (sources == null) {
                return null
            }
            val ids = LinkedHashSet<Long>()
            for (source in sources) {
                if (source.suspended) {
                    ids.add(source.cardId)
                }
            }
            return ids
        }

        @JvmStatic
        fun removalMessage(tagged: Int, failed: Int): String {
            if (tagged > 0 && failed == 0) {
                return "Archived suspended notes were tagged in AnkiDroid and hidden from future syncs."
            }
            if (tagged > 0) {
                return "Archived suspended notes were partly tagged in AnkiDroid; any leftovers stay in the local archive."
            }
            return "Archived suspended cards were kept in the local archive; AnkiDroid did not allow provider tagging."
        }
    }
}
