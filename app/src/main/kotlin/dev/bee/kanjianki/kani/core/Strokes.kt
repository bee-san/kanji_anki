package dev.bee.kanjianki.kani.core

import java.io.BufferedReader
import kotlin.math.max
import kotlin.math.sqrt

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
 * The top-level parts of one kanji from KanjiVG (`kanji_parts.tsv`): 借 is 亻 (strokes 1–2) and
 * 昔 (3–10), with 亻 its radical. [partOf] gives each stroke's part, numbered from 0.
 */
class KanjiParts(private val partOf: IntArray, private val radicals: Set<Int>) {
    val strokes: Int get() = partOf.size
    val partCount: Int = (partOf.maxOrNull() ?: -1) + 1

    fun strokesOf(part: Int): List<Int> = partOf.indices.filter { partOf[it] == part }

    /** A radical that is less than half the kanji, like 亻 in 借; not 艮 in 良, which is most of it. */
    fun isSideRadical(part: Int): Boolean = part in radicals && strokesOf(part).size * 2 < strokes

    companion object {
        /** Reads `kanji<TAB>part,part,…<TAB>radical parts` lines; `#` lines are comments. */
        fun parse(reader: BufferedReader): Map<String, KanjiParts> {
            val parts = HashMap<String, KanjiParts>(8192)
            reader.forEachLine { line ->
                if (line.isEmpty() || line.startsWith("#")) return@forEachLine
                val cells = line.split('\t')
                if (cells.size < 2) return@forEachLine
                val partOf = cells[1].split(',').mapNotNull { it.toIntOrNull() }.toIntArray()
                val radicals = cells.getOrNull(2).orEmpty().split(',').mapNotNull { it.toIntOrNull() }.toSet()
                parts[cells[0]] = KanjiParts(partOf, radicals)
            }
            return parts
        }
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

    /**
     * How alike two kanji look, 0..1: the share of its strokes that each kanji has in common with
     * the other, taken from the one that shares less. 待/持 score 0.67 (all of 寺); 退/眠 score 0.2,
     * because only two strokes of 眠 line up with anything in 退.
     *
     * With [partsA] and [partsB] it counts whole parts, not loose strokes, so scraps of two
     * different parts never add up to a look-alike:
     * - a part counts only when at least two thirds of its strokes match. 借 and 供 share 亻 and a
     *   few strokes of 昔 and 共, but 昔 and 共 are different parts.
     * - a shared radical on the side (亻, 口, 女 …) doesn't count, because sharing one is common:
     *   和/如 and 妹/姉 share nothing else. Pairs that match almost entirely (末/未, at
     *   [NEAR_IDENTICAL] or more) keep their score, radical included.
     */
    fun likeness(a: List<Stroke>, b: List<Stroke>, partsA: KanjiParts? = null, partsB: KanjiParts? = null): Float {
        if (a.isEmpty() || b.isEmpty()) return 0f
        val sideA = shared(a.size, decidingStrokes(a, b), partsA?.takeIf { it.strokes == a.size })
        val sideB = shared(b.size, decidingStrokes(b, a), partsB?.takeIf { it.strokes == b.size })
        val whole = minOf(sideA.first, sideB.first)
        return if (whole >= NEAR_IDENTICAL) whole else minOf(sideA.second, sideB.second)
    }

    /** At this likeness two kanji are near copies, so sharing the radical counts. */
    const val NEAR_IDENTICAL = 0.7f

    /**
     * The share of a kanji's [count] strokes in matching parts, with and without its side radical.
     * Without [parts], each stroke is a part of its own.
     */
    private fun shared(count: Int, deciding: Set<Int>, parts: KanjiParts?): Pair<Float, Float> {
        if (parts == null) return (1f - deciding.size.toFloat() / count).let { it to it }
        var whole = 0
        var distinct = 0
        for (part in 0 until parts.partCount) {
            val strokes = parts.strokesOf(part)
            val matched = strokes.count { it !in deciding }
            if (matched * 3 < strokes.size * 2) continue
            whole += matched
            if (!parts.isSideRadical(part)) distinct += matched
        }
        return whole.toFloat() / count to distinct.toFloat() / count
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

    /** Plain sqrt: `hypot` guards against overflow these 0..1 points cannot reach, at ten times the cost. */
    private fun dist(a: Pt, b: Pt): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }
}
