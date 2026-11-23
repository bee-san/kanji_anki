package dev.bee.kanjianki.kani.core

import java.io.BufferedReader

/**
 * The bundled look-alike pairs, ranked by how alike the kanji really look.
 *
 * The pair list is loose: it names 眠 for 退, which share no part. Every pair carries a
 * [StrokeDiff.likeness] score from `res/raw/look_alike_scores.tsv`, so Kani can pick the closest
 * kanji and only say "looks like" about a pair that shares enough to show.
 */
class LookAlikes(private val pairs: (String) -> List<String>, private val scores: Map<String, Float>) {

    /** Kanji that look like [kanji] closely enough to say so, closest first. */
    fun of(kanji: String): List<String> = listed(kanji).filter { likeness(kanji, it) >= MIN_LIKENESS }

    /**
     * Every kanji the pair list names for [kanji], closest first, loose ones included. For
     * questions the list can still answer: whose meaning the user typed, or which kanji share a sound.
     */
    fun listed(kanji: String): List<String> = pairs(kanji).sortedByDescending { likeness(kanji, it) }

    /** 0 for a pair without a score, so an unscored pair is never called a look-alike. */
    fun likeness(a: String, b: String): Float = scores[key(a, b)] ?: 0f

    companion object {
        /** A third of each kanji must be shared. Below that the two mostly differ. */
        const val MIN_LIKENESS = 0.33f

        fun key(a: String, b: String): String = if (a <= b) "$a\t$b" else "$b\t$a"

        /** Reads `kanji_a<TAB>kanji_b<TAB>likeness` lines; `#` lines are comments. */
        fun parseScores(reader: BufferedReader): Map<String, Float> {
            val scores = HashMap<String, Float>(4096)
            reader.forEachLine { line ->
                if (line.isEmpty() || line.startsWith("#")) return@forEachLine
                val cells = line.split('\t')
                val score = cells.getOrNull(2)?.toFloatOrNull() ?: return@forEachLine
                scores[key(cells[0], cells[1])] = score
            }
            return scores
        }
    }
}
