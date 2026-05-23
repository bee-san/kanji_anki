package dev.bee.kanjianki.core.study

import java.util.ArrayList
import java.util.Collections

class InkStroke(points: List<InkPoint>) {
    @JvmField val points: List<InkPoint> = Collections.unmodifiableList(ArrayList(points))

    fun isEmpty(): Boolean = points.isEmpty()

    fun start(): InkPoint? = if (points.isEmpty()) null else points[0]

    fun end(): InkPoint? = if (points.isEmpty()) null else points[points.size - 1]
}
