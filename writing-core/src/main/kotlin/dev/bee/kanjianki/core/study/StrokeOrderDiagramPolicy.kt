package dev.bee.kanjianki.core.study

import java.util.ArrayList
import java.util.Collections

object StrokeOrderDiagramPolicy {
    const val MAX_PANELS: Int = 24

    @JvmStatic
    fun build(guide: StrokeGuide?): StrokeOrderDiagram {
        if (guide == null || guide.isEmpty()) {
            return StrokeOrderDiagram(Collections.emptyList(), 0)
        }
        val strokeCount = guide.strokeCount()
        val panelCount = strokeCount.coerceAtMost(MAX_PANELS)
        val omitted = strokeCount - panelCount
        val panels = ArrayList<StrokeOrderPanel>(panelCount)
        for (i in 0 until panelCount) {
            val strokeIndex = i
            val strokes = ArrayList<StrokeOrderPanelStroke>(strokeIndex + 1)
            for (j in 0..strokeIndex) {
                val inkStroke = guide.strokes[j]
                strokes.add(StrokeOrderPanelStroke(inkStroke, j == strokeIndex))
            }
            val highlighted = guide.strokes[strokeIndex]
            val startPoint = highlighted.start()
            panels.add(StrokeOrderPanel(Collections.unmodifiableList(strokes), startPoint, strokeIndex + 1))
        }
        return StrokeOrderDiagram(Collections.unmodifiableList(panels), omitted)
    }
}

class StrokeOrderDiagram(
    @JvmField val panels: List<StrokeOrderPanel>,
    @JvmField val omittedStrokeCount: Int,
) {
    fun isEmpty(): Boolean = panels.isEmpty()
}

class StrokeOrderPanel(
    @JvmField val strokes: List<StrokeOrderPanelStroke>,
    @JvmField val startPoint: InkPoint?,
    @JvmField val strokeNumber: Int,
)

class StrokeOrderPanelStroke(
    @JvmField val inkStroke: InkStroke,
    @JvmField val highlighted: Boolean,
)
