package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsSyncModels

object TestRecords {
    @JvmStatic
    fun review(kanji: String, token: String): RecordsSchedulerModels.ReviewRequest =
        RecordsSchedulerModels.ReviewRequest(kanji, token, "good", true, true, false, 0)

    @JvmStatic
    fun kikuNote(
        id: Long,
        expression: String,
        reading: String,
        meaning: String,
        sentence: String,
    ): RecordsSyncModels.Note = kikuNote(id, 0L, expression, reading, meaning, sentence)

    @JvmStatic
    fun sourceKikuNote(
        id: Long,
        expression: String,
        reading: String,
        meaning: String,
        sentence: String,
    ): RecordsSyncModels.Note = kikuNote(id, 1001L, expression, reading, meaning, sentence)

    @JvmStatic
    fun customMiningNote(
        id: Long,
        expression: String,
        reading: String,
        meaning: String,
        sentence: String,
    ): RecordsSyncModels.Note {
        val fields = linkedMapOf(
            "Word" to expression,
            "Kana" to reading,
            "Gloss" to meaning,
            "Context" to sentence,
            "Frequency" to "1000",
            "Sort" to "1000",
        )
        return RecordsSyncModels.Note(id, 2002L, "Custom Mining", fields, emptyList())
    }

    @JvmStatic
    fun kikuCard(cardId: Long, noteId: Long): CardBuilder = card(cardId, noteId, "Kiku")

    @JvmStatic
    fun card(cardId: Long, noteId: Long, deckId: String): CardBuilder =
        CardBuilder(cardId, noteId, deckId)

    @JvmStatic
    fun card(cardId: Long, noteId: Long, deckId: String, deckName: String): CardBuilder =
        CardBuilder(cardId, noteId, deckId).deck(deckId, deckName)

    private fun kikuNote(
        id: Long,
        modelId: Long,
        expression: String,
        reading: String,
        meaning: String,
        sentence: String,
    ): RecordsSyncModels.Note {
        val fields = linkedMapOf(
            "Expression" to expression,
            "ExpressionReading" to reading,
            "MainDefinition" to meaning,
            "Sentence" to sentence,
            "Frequency" to "1000",
            "FreqSort" to "1000",
        )
        return RecordsSyncModels.Note(id, modelId, "Kiku", fields, emptyList())
    }

    class CardBuilder(
        private val cardId: Long,
        private val noteId: Long,
        private var deckId: String,
    ) {
        private var ord: Int = 0
        private var deckName: String = deckId
        private var queue: Int = 2
        private var type: Int = 2
        private var due: Int = 0
        private var intervalDays: Int = 3
        private var reps: Int = 4
        private var lapses: Int = 1
        private var suspended: Boolean = false
        private var fsrsStability: Double? = null
        private var fsrsDifficulty: Double? = null
        private var fsrsRetrievability: Double? = null

        fun deck(deckId: String, deckName: String): CardBuilder {
            this.deckId = deckId
            this.deckName = deckName
            return this
        }

        fun history(intervalDays: Int, reps: Int, lapses: Int): CardBuilder {
            this.intervalDays = intervalDays
            this.reps = reps
            this.lapses = lapses
            return this
        }

        fun suspended(): CardBuilder {
            queue = -1
            type = 0
            due = 0
            intervalDays = 0
            reps = 0
            lapses = 0
            suspended = true
            return this
        }

        fun fsrs(stability: Double?, difficulty: Double?, retrievability: Double?): CardBuilder {
            fsrsStability = stability
            fsrsDifficulty = difficulty
            fsrsRetrievability = retrievability
            return this
        }

        fun build(): RecordsSyncModels.Card =
            if (deckId == deckName) {
                RecordsSyncModels.Card(
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
                    fsrsRetrievability,
                )
            } else {
                RecordsSyncModels.Card(
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
                    fsrsRetrievability,
                )
            }
    }
}
