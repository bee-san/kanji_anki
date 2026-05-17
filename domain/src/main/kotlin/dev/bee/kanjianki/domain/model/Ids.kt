package dev.bee.kanjianki.domain.model

@JvmInline
value class KanjiText(val value: String) {
    init {
        require(value.isNotBlank()) { "kanji must not be blank" }
    }
}

@JvmInline
value class CardId(val value: Long)

@JvmInline
value class NoteId(val value: Long)

@JvmInline
value class SyncRunId(val value: Long)

@JvmInline
value class ReviewToken(val value: String) {
    init {
        require(value.isNotBlank()) { "review token must not be blank" }
    }
}
