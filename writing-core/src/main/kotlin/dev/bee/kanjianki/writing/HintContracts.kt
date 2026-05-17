package dev.bee.kanjianki.writing

enum class HintLevel(val writingLevel: Int) {
    TRACE(0),
    OUTLINE(1),
    MINIMAL(2),
    BLIND(3);

    fun next(): HintLevel = entries[minOf(entries.lastIndex, ordinal + 1)]

    fun previous(): HintLevel = entries[maxOf(0, ordinal - 1)]

    companion object {
        fun fromWritingLevel(writingLevel: Int): HintLevel =
            entries.firstOrNull { it.writingLevel == writingLevel }
                ?: if (writingLevel < TRACE.writingLevel) TRACE else BLIND
    }
}

data class HintState(
    val level: HintLevel,
    val acceptedStrokeCount: Int,
) {
    init {
        require(acceptedStrokeCount >= 0) { "acceptedStrokeCount must be non-negative" }
    }
}
