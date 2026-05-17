package dev.bee.kanjianki;

import dev.bee.kanjianki.core.Records;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class TestRecords {
    private TestRecords() {
    }

    public static Records.ReviewRequest review(String kanji, String token) {
        return new Records.ReviewRequest(kanji, token, "good", true, true, false, 0);
    }

    public static Records.Note kikuNote(
            long id,
            String expression,
            String reading,
            String meaning,
            String sentence
    ) {
        return kikuNote(id, 0L, expression, reading, meaning, sentence);
    }

    public static Records.Note sourceKikuNote(
            long id,
            String expression,
            String reading,
            String meaning,
            String sentence
    ) {
        return kikuNote(id, 1001L, expression, reading, meaning, sentence);
    }

    public static Records.Note customMiningNote(
            long id,
            String expression,
            String reading,
            String meaning,
            String sentence
    ) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("Word", expression);
        fields.put("Kana", reading);
        fields.put("Gloss", meaning);
        fields.put("Context", sentence);
        fields.put("Frequency", "1000");
        fields.put("Sort", "1000");
        return new Records.Note(id, 2002L, "Custom Mining", fields, Collections.emptyList());
    }

    public static CardBuilder kikuCard(long cardId, long noteId) {
        return card(cardId, noteId, "Kiku");
    }

    public static CardBuilder card(long cardId, long noteId, String deckId) {
        return new CardBuilder(cardId, noteId, deckId);
    }

    public static CardBuilder card(long cardId, long noteId, String deckId, String deckName) {
        return new CardBuilder(cardId, noteId, deckId).deck(deckId, deckName);
    }

    private static Records.Note kikuNote(
            long id,
            long modelId,
            String expression,
            String reading,
            String meaning,
            String sentence
    ) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("Expression", expression);
        fields.put("ExpressionReading", reading);
        fields.put("MainDefinition", meaning);
        fields.put("Sentence", sentence);
        fields.put("Frequency", "1000");
        fields.put("FreqSort", "1000");
        return new Records.Note(id, modelId, "Kiku", fields, Collections.emptyList());
    }

    public static final class CardBuilder {
        private final long cardId;
        private final long noteId;
        private int ord;
        private String deckId;
        private String deckName;
        private int queue = 2;
        private int type = 2;
        private int due;
        private int intervalDays = 3;
        private int reps = 4;
        private int lapses = 1;
        private boolean suspended;
        private Double fsrsStability;
        private Double fsrsDifficulty;
        private Double fsrsRetrievability;

        private CardBuilder(long cardId, long noteId, String deckId) {
            this.cardId = cardId;
            this.noteId = noteId;
            this.deckId = deckId;
            this.deckName = deckId;
        }

        public CardBuilder deck(String deckId, String deckName) {
            this.deckId = deckId;
            this.deckName = deckName;
            return this;
        }

        public CardBuilder history(int intervalDays, int reps, int lapses) {
            this.intervalDays = intervalDays;
            this.reps = reps;
            this.lapses = lapses;
            return this;
        }

        public CardBuilder suspended() {
            this.queue = -1;
            this.type = 0;
            this.due = 0;
            this.intervalDays = 0;
            this.reps = 0;
            this.lapses = 0;
            this.suspended = true;
            return this;
        }

        public CardBuilder fsrs(Double stability, Double difficulty, Double retrievability) {
            this.fsrsStability = stability;
            this.fsrsDifficulty = difficulty;
            this.fsrsRetrievability = retrievability;
            return this;
        }

        public Records.Card build() {
            if (deckId.equals(deckName)) {
                return new Records.Card(
                        cardId,
                        noteId,
                        ord,
                        deckId,
                        queue,
                        type,
                        due,
                        intervalDays,
                        reps,
                        lapses,
                        suspended,
                        fsrsStability,
                        fsrsDifficulty,
                        fsrsRetrievability
                );
            }
            return new Records.Card(
                    cardId,
                    noteId,
                    ord,
                    deckId,
                    deckName,
                    queue,
                    type,
                    due,
                    intervalDays,
                    reps,
                    lapses,
                    suspended,
                    fsrsStability,
                    fsrsDifficulty,
                    fsrsRetrievability
            );
        }
    }
}
