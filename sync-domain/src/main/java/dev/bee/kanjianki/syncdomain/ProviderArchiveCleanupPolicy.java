package dev.bee.kanjianki.syncdomain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProviderArchiveCleanupPolicy {
    private ProviderArchiveCleanupPolicy() {
    }

    public static CleanupPlan plan(List<Card> cards, Set<Long> selectedSuspendedCardIds) {
        CleanupIndex index = CleanupIndex.from(cards, selectedSuspendedCardIds);
        return new CleanupPlan(
                index.suspendedCards.size(),
                index.notesFullySuspended(),
                index.partiallySuspendedCardCount()
        );
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

    public static String removalMessage(int tagged, int failed) {
        if (tagged > 0 && failed == 0) {
            return "Archived suspended notes were tagged in AnkiDroid and hidden from future syncs.";
        }
        if (tagged > 0) {
            return "Archived suspended notes were partly tagged in AnkiDroid; any leftovers stay in the local archive.";
        }
        return "Archived suspended cards were kept in the local archive; AnkiDroid did not allow provider tagging.";
    }

    public record Card(long cardId, long noteId, boolean suspended) {
    }

    public record SelectedSource(long cardId, boolean suspended) {
    }

    public record CleanupPlan(int sourceCards, Set<Long> notesToTag, int alreadyFailedCards) {
        public boolean hasSuspendedCards() {
            return sourceCards > 0;
        }
    }

    private static final class CleanupIndex {
        private final Map<Long, Integer> cardsByNote;
        private final Map<Long, Integer> suspendedByNote;
        private final Map<Long, Integer> selectedSuspendedByNote;
        private final List<Card> suspendedCards;

        private CleanupIndex(
                Map<Long, Integer> cardsByNote,
                Map<Long, Integer> suspendedByNote,
                Map<Long, Integer> selectedSuspendedByNote,
                List<Card> suspendedCards
        ) {
            this.cardsByNote = cardsByNote;
            this.suspendedByNote = suspendedByNote;
            this.selectedSuspendedByNote = selectedSuspendedByNote;
            this.suspendedCards = suspendedCards;
        }

        private static CleanupIndex from(List<Card> cards, Set<Long> selectedSuspendedCardIds) {
            Map<Long, Integer> cardsByNote = new LinkedHashMap<>();
            Map<Long, Integer> suspendedByNote = new LinkedHashMap<>();
            Map<Long, Integer> selectedSuspendedByNote = new LinkedHashMap<>();
            List<Card> suspendedCards = new ArrayList<>();
            for (Card card : cards) {
                cardsByNote.put(card.noteId(), cardsByNote.getOrDefault(card.noteId(), 0) + 1);
                if (card.suspended()) {
                    suspendedByNote.put(card.noteId(), suspendedByNote.getOrDefault(card.noteId(), 0) + 1);
                    if (selectedSuspendedCardIds == null || selectedSuspendedCardIds.contains(card.cardId())) {
                        suspendedCards.add(card);
                        selectedSuspendedByNote.put(card.noteId(), selectedSuspendedByNote.getOrDefault(card.noteId(), 0) + 1);
                    }
                }
            }
            return new CleanupIndex(cardsByNote, suspendedByNote, selectedSuspendedByNote, suspendedCards);
        }

        private Set<Long> notesFullySuspended() {
            Set<Long> notes = new LinkedHashSet<>();
            for (Card card : suspendedCards) {
                if (fullySelectedSuspendedNote(card.noteId())) {
                    notes.add(card.noteId());
                }
            }
            return notes;
        }

        private int partiallySuspendedCardCount() {
            int failed = 0;
            for (Card card : suspendedCards) {
                if (!fullySelectedSuspendedNote(card.noteId())) {
                    failed++;
                }
            }
            return failed;
        }

        private boolean fullySelectedSuspendedNote(long noteId) {
            return cardsByNote.get(noteId).equals(suspendedByNote.get(noteId))
                    && suspendedByNote.get(noteId).equals(selectedSuspendedByNote.get(noteId));
        }
    }
}
