package dev.bee.kanjianki.kani.core

import java.io.BufferedReader
import kotlin.math.hypot
import kotlin.math.max

/** A stroke as normalised points in a 0..1 box (KanjiVG order). */
data class Pt(val x: Float, val y: Float)

typealias Stroke = List<Pt>

/**
 * KanjiVG stroke data from `res/raw/kanji_strokes.tsv`. Lines are kept as text and parsed on
 * first use, since only a handful of kanji are ever drawn in a session.
 */
class StrokeData(private val raw: Map<String, String>) {
    private val parsed = HashMap<String, List<Stroke>?>()

    @Synchronized
    fun of(kanji: String): List<Stroke>? = parsed.getOrPut(kanji) { raw[kanji]?.let(::parseStrokes) }

    companion object {
        fun parse(reader: BufferedReader): StrokeData {
            val map = HashMap<String, String>(8192)
            reader.forEachLine { line ->
                if (line.isEmpty() || line.startsWith("#")) return@forEachLine
                val tab = line.indexOf('\t')
                if (tab > 0) map[line.substring(0, tab)] = line.substring(tab + 1)
            }
            return StrokeData(map)
        }

        private fun parseStrokes(text: String): List<Stroke>? = text.split('|').mapNotNull { stroke ->
            stroke.split(';').mapNotNull { p ->
                val comma = p.indexOf(',')
                if (comma < 0) null else Pt(p.substring(0, comma).toFloat(), p.substring(comma + 1).toFloat())
            }.takeIf { it.size >= 2 }
        }.ifEmpty { null }
    }
}

/**
 * Finds "the part that decides" between two look-alikes: the strokes of [a] that have no close
 * match in [b]. Shared strokes are what makes them look alike; the rest is what to look at.
 */
object StrokeDiff {
    private const val SAMPLES = 12
    private const val SHARED_DISTANCE = 0.075f

    fun decidingStrokes(a: List<Stroke>, b: List<Stroke>): Set<Int> {
        if (a.isEmpty()) return emptySet()
        if (b.isEmpty()) return a.indices.toSet()
        val sa = a.map { resample(it) }
        val sb = b.map { resample(it) }
        // The shared part can sit in a different place and size (艮 in 退 vs 恨), so align b
        // to a first: pick the scale and offset that make the most strokes match.
        val identity = distancesUnder(sa, sb, 1f, 0f, 0f)
        var best = identity
        // A transform must clearly beat no transform, or it just bends unrelated strokes together.
        var bestShared = identity.count { it <= SHARED_DISTANCE } + maxOf(2, a.size / 3) - 1
        var at = Triple(1f, 0f, 0f)
        fun consider(scale: Float, dx: Float, dy: Float) {
            val d = distancesUnder(sa, sb, scale, dx, dy)
            val shared = d.count { it <= SHARED_DISTANCE }
            if (shared > bestShared || (shared == bestShared && best !== identity && d.sum() < best.sum())) {
                best = d
                bestShared = shared
                at = Triple(scale, dx, dy)
            }
        }
        for (scale in SCALES) for (dx in OFFSETS) for (dy in OFFSETS) consider(scale, dx, dy)
        if (best !== identity) {
            // Refine around the coarse match.
            val (s0, x0, y0) = at
            for (ds in -4..4) for (ix in -4..4) for (iy in -4..4) consider(s0 + ds * 0.02f, x0 + ix * 0.0125f, y0 + iy * 0.0125f)
        }
        val deciding = best.indices.filter { best[it] > SHARED_DISTANCE }.toSet()
        if (deciding.isNotEmpty()) return deciding
        // Near-identical shapes (末/未): the single most different stroke decides.
        return setOf(best.indices.maxBy { best[it] })
    }

    private val SCALES = floatArrayOf(0.7f, 0.8f, 0.9f, 1f, 1.1f, 1.25f, 1.4f)
    private val OFFSETS = FloatArray(13) { -0.3f + it * 0.05f }

    /** For each stroke of [a], its distance to the nearest stroke of [b] scaled about the centre and shifted. */
    private fun distancesUnder(a: List<List<Pt>>, b: List<List<Pt>>, scale: Float, dx: Float, dy: Float): FloatArray {
        val moved = b.map { s -> s.map { Pt((it.x - 0.5f) * scale + 0.5f + dx, (it.y - 0.5f) * scale + 0.5f + dy) } }
        return FloatArray(a.size) { i -> moved.minOf { strokeDistance(a[i], it) } }
    }

    /** Mean distance between two resampled strokes, in either drawing direction. */
    fun strokeDistance(a: List<Pt>, b: List<Pt>): Float {
        var forward = 0f
        var backward = 0f
        for (i in a.indices) {
            forward += dist(a[i], b[i])
            backward += dist(a[i], b[b.size - 1 - i])
        }
        return minOf(forward, backward) / a.size
    }

    fun resample(stroke: List<Pt>, n: Int = SAMPLES): List<Pt> {
        if (stroke.size == 1) return List(n) { stroke[0] }
        val lengths = FloatArray(stroke.size)
        for (i in 1 until stroke.size) lengths[i] = lengths[i - 1] + dist(stroke[i - 1], stroke[i])
        val total = max(lengths.last(), 1e-6f)
        val out = ArrayList<Pt>(n)
        var j = 1
        for (k in 0 until n) {
            val target = total * k / (n - 1)
            while (j < stroke.size - 1 && lengths[j] < target) j++
            val segment = max(lengths[j] - lengths[j - 1], 1e-6f)
            val t = ((target - lengths[j - 1]) / segment).coerceIn(0f, 1f)
            val p0 = stroke[j - 1]
            val p1 = stroke[j]
            out += Pt(p0.x + (p1.x - p0.x) * t, p0.y + (p1.y - p0.y) * t)
        }
        return out
    }

    /**
     * Checks handwritten strokes against the expected deciding strokes: same count, and each
     * drawn stroke lands near a different expected stroke.
     */
    fun matchesDeciding(drawn: List<Stroke>, expected: List<Stroke>, tolerance: Float = 0.16f): Boolean {
        if (drawn.size != expected.size || drawn.isEmpty()) return false
        val remaining = expected.map { resample(it) }.toMutableList()
        for (stroke in drawn) {
            val s = resample(stroke)
            val best = remaining.minByOrNull { strokeDistance(s, it) } ?: return false
            if (strokeDistance(s, best) > tolerance) return false
            remaining.remove(best)
        }
        return true
    }

    private fun dist(a: Pt, b: Pt): Float = hypot(a.x - b.x, a.y - b.y)
}
