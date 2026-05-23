package dev.bee.kanjianki.syncdomain

import java.util.Collections
import java.util.Objects

class SyncMirrorPolicy private constructor() {
    @JvmRecord
    data class Card(val cardId: Long, val noteId: Long, val suspended: Boolean)

    @JvmRecord
    data class SelectedSource(val cardId: Long, val suspended: Boolean)

    @JvmRecord
    data class ActiveCardIndex(val noteIds: Set<Long>, val cardIds: Set<Long>, val activeCardCount: Int) {
        init {
            Objects.requireNonNull(noteIds, "noteIds")
            Objects.requireNonNull(cardIds, "cardIds")
        }

        constructor(
            noteIds: Set<Long>?,
            cardIds: Set<Long>?,
            activeCardCount: Int,
            @Suppress("UNUSED_PARAMETER") constructorToken: ConstructorToken,
        ) : this(
            Collections.unmodifiableSet(LinkedHashSet(Objects.requireNonNull(noteIds, "noteIds"))),
            Collections.unmodifiableSet(LinkedHashSet(Objects.requireNonNull(cardIds, "cardIds"))),
            activeCardCount
        )
    }

    class ConstructorToken

    companion object {
        @JvmStatic
        fun activeCardIndex(cards: List<Card>): ActiveCardIndex {
            Objects.requireNonNull(cards, "cards")
            val noteIds = LinkedHashSet<Long>()
            val cardIds = LinkedHashSet<Long>()
            var activeCardCount = 0
            for (card in cards) {
                if (!card.suspended) {
                    activeCardCount++
                    noteIds.add(card.noteId)
                    cardIds.add(card.cardId)
                }
            }
            return ActiveCardIndex(noteIds, cardIds, activeCardCount, ConstructorToken())
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
    }
}
