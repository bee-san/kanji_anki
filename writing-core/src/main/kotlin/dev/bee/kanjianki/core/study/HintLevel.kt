package dev.bee.kanjianki.core.study

enum class HintLevel(private val writingLevel: Int) {
    TRACE(0),
    OUTLINE(1),
    MINIMAL(2),
    BLIND(3),
    ;

    fun writingLevel(): Int = writingLevel

    fun next(): HintLevel {
        val next = minOf(entries.size - 1, ordinal + 1)
        return entries[next]
    }

    fun previous(): HintLevel {
        val previous = maxOf(0, ordinal - 1)
        return entries[previous]
    }

    companion object {
        @JvmStatic
        fun fromWritingLevel(writingLevel: Int): HintLevel {
            for (level in entries) {
                if (level.writingLevel == writingLevel) {
                    return level
                }
            }
            return if (writingLevel < TRACE.writingLevel) TRACE else BLIND
        }
    }
}
