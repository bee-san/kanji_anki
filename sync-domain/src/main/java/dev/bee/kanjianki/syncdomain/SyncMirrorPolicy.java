package dev.bee.kanjianki.syncdomain;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class SyncMirrorPolicy {
    private SyncMirrorPolicy() {
    }

    public static ActiveCardIndex activeCardIndex(List<Card> cards) {
        Objects.requireNonNull(cards, "cards");
        Set<Long> noteIds = new LinkedHashSet<>();
        Set<Long> cardIds = new LinkedHashSet<>();
        int activeCardCount = 0;
        for (Card card : cards) {
            if (!card.suspended()) {
                activeCardCount++;
                noteIds.add(card.noteId());
                cardIds.add(card.cardId());
            }
        }
        return new ActiveCardIndex(noteIds, cardIds, activeCardCount);
    }

    public static Set<Long> selectedSuspendedCardIds(List<SelectedSource> sources) {
        if (sources == null) {
            return null;
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (SelectedSource source : sources) {
            if (source.suspended()) {
                ids.add(source.cardId());
            }
        }
        return ids;
    }

    public record Card(long cardId, long noteId, boolean suspended) {
    }

    public record SelectedSource(long cardId, boolean suspended) {
    }

    public record ActiveCardIndex(Set<Long> noteIds, Set<Long> cardIds, int activeCardCount) {
        public ActiveCardIndex {
            noteIds = Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNull(noteIds, "noteIds")));
            cardIds = Collections.unmodifiableSet(new LinkedHashSet<>(Objects.requireNonNull(cardIds, "cardIds")));
        }
    }
}
