package dev.bee.fsrs

/**
 * Immutable FSRS memory state for a reviewed item.
 */
class FsrsMemoryState(
    private val stability: Double,
    private val difficulty: Double,
) {
    init {
        if (!stability.isFinite() || stability <= 0.0) {
            throw IllegalArgumentException("stability must be finite and positive")
        }
        if (!difficulty.isFinite() || difficulty < Fsrs.MIN_DIFFICULTY || difficulty > Fsrs.MAX_DIFFICULTY) {
            throw IllegalArgumentException("difficulty must be finite and in [1, 10]")
        }
    }

    fun stability(): Double = stability

    fun difficulty(): Double = difficulty

    override fun equals(other: Any?): Boolean =
        this === other ||
            other is FsrsMemoryState &&
            stability == other.stability &&
            difficulty == other.difficulty

    override fun hashCode(): Int {
        var result = stability.hashCode()
        result = 31 * result + difficulty.hashCode()
        return result
    }

    override fun toString(): String =
        "FsrsMemoryState{stability=$stability, difficulty=$difficulty}"
}
