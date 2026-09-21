package dev.bee.fsrs

/**
 * FSRS review ratings in their upstream numeric order.
 */
enum class FsrsRating(
    private val value: Int,
) {
    AGAIN(1),
    HARD(2),
    GOOD(3),
    EASY(4),
    ;

    fun value(): Int = value

    companion object {
        @JvmStatic
        fun fromValue(value: Int): FsrsRating =
            entries.firstOrNull { it.value == value }
                ?: throw IllegalArgumentException("Unknown FSRS rating value: $value")
    }
}
