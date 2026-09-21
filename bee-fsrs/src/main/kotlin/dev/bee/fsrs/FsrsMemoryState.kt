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
        require(stability.isFinite() && stability > 0.0) { "stability must be finite and positive" }
        require(
            difficulty.isFinite() &&
                difficulty >= Fsrs.MIN_DIFFICULTY &&
                difficulty <= Fsrs.MAX_DIFFICULTY
        ) {
            "difficulty must be finite and in [1, 10]"
        }
    }

    override fun toString(): String =
        "FsrsMemoryState{stability=$stability, difficulty=$difficulty}"
}
