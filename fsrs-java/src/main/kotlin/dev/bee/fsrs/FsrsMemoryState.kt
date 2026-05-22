package dev.bee.fsrs

/**
 * Immutable FSRS memory state for a reviewed item.
 */
@JvmRecord
data class FsrsMemoryState(
    val stability: Double,
    val difficulty: Double,
) {
    init {
        if (!stability.isFinite() || stability <= 0.0) {
            throw IllegalArgumentException("stability must be finite and positive")
        }
        if (!difficulty.isFinite() || difficulty < Fsrs.MIN_DIFFICULTY || difficulty > Fsrs.MAX_DIFFICULTY) {
            throw IllegalArgumentException("difficulty must be finite and in [1, 10]")
        }
    }

    override fun toString(): String =
        "FsrsMemoryState{stability=$stability, difficulty=$difficulty}"
}
