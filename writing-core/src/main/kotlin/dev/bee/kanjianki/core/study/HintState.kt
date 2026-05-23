package dev.bee.kanjianki.core.study

class HintState(
    level: HintLevel?,
    revealedStrokeCount: Int,
    consecutivePasses: Int,
) {
    private val level: HintLevel = level ?: HintLevel.TRACE
    private val revealedStrokeCount: Int = maxOf(0, revealedStrokeCount)
    private val consecutivePasses: Int = maxOf(0, consecutivePasses)

    fun level(): HintLevel = level

    fun revealedStrokeCount(): Int = revealedStrokeCount

    fun consecutivePasses(): Int = consecutivePasses

    fun withLevel(nextLevel: HintLevel?): HintState = HintState(nextLevel, 0, 0)

    fun withRevealCount(nextRevealCount: Int): HintState = HintState(level, nextRevealCount, consecutivePasses)

    fun withConsecutivePasses(nextConsecutivePasses: Int): HintState =
        HintState(level, revealedStrokeCount, nextConsecutivePasses)

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is HintState) {
            return false
        }
        return level == other.level &&
            revealedStrokeCount == other.revealedStrokeCount &&
            consecutivePasses == other.consecutivePasses
    }

    override fun hashCode(): Int {
        var result = level.hashCode()
        result = 31 * result + revealedStrokeCount
        result = 31 * result + consecutivePasses
        return result
    }

    override fun toString(): String {
        return "HintState[level=$level, revealedStrokeCount=$revealedStrokeCount, consecutivePasses=$consecutivePasses]"
    }

    companion object {
        @JvmStatic
        fun initial(): HintState = HintState(HintLevel.TRACE, 0, 0)

        @JvmStatic
        fun fromWritingLevel(writingLevel: Int): HintState = HintState(HintLevel.fromWritingLevel(writingLevel), 0, 0)
    }
}
