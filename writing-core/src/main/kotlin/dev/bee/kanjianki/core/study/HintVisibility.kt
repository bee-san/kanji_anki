package dev.bee.kanjianki.core.study

class HintVisibility(
    level: HintLevel?,
    private val tracePathsVisible: Boolean,
    private val outlineVisible: Boolean,
    private val strokeNumbersVisible: Boolean,
    private val startDotsVisible: Boolean,
    private val strokeCountVisible: Boolean,
    visibleStrokeCount: Int,
) {
    private val level: HintLevel = level ?: HintLevel.TRACE
    private val visibleStrokeCount: Int = maxOf(0, visibleStrokeCount)

    fun level(): HintLevel = level

    fun tracePathsVisible(): Boolean = tracePathsVisible

    fun outlineVisible(): Boolean = outlineVisible

    fun strokeNumbersVisible(): Boolean = strokeNumbersVisible

    fun startDotsVisible(): Boolean = startDotsVisible

    fun strokeCountVisible(): Boolean = strokeCountVisible

    fun visibleStrokeCount(): Int = visibleStrokeCount

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other !is HintVisibility) {
            return false
        }
        return level == other.level &&
            tracePathsVisible == other.tracePathsVisible &&
            outlineVisible == other.outlineVisible &&
            strokeNumbersVisible == other.strokeNumbersVisible &&
            startDotsVisible == other.startDotsVisible &&
            strokeCountVisible == other.strokeCountVisible &&
            visibleStrokeCount == other.visibleStrokeCount
    }

    override fun hashCode(): Int {
        var result = level.hashCode()
        result = 31 * result + tracePathsVisible.hashCode()
        result = 31 * result + outlineVisible.hashCode()
        result = 31 * result + strokeNumbersVisible.hashCode()
        result = 31 * result + startDotsVisible.hashCode()
        result = 31 * result + strokeCountVisible.hashCode()
        result = 31 * result + visibleStrokeCount
        return result
    }

    override fun toString(): String {
        return "HintVisibility[level=$level, tracePathsVisible=$tracePathsVisible, outlineVisible=$outlineVisible, " +
            "strokeNumbersVisible=$strokeNumbersVisible, startDotsVisible=$startDotsVisible, " +
            "strokeCountVisible=$strokeCountVisible, visibleStrokeCount=$visibleStrokeCount]"
    }
}
