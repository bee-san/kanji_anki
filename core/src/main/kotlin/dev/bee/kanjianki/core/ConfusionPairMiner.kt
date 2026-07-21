package dev.bee.kanjianki.core

/**
 * Mines the similar-kanji choice review log for personalized confusion
 * pairs: unordered kanji pairs the learner has mixed up repeatedly in
 * recent multiple-choice reviews. Mined pairs feed the same
 * similar_kanji_pairs table as the static visually-similar dataset, under
 * the dedicated [SOURCE_USER_CONFUSION] source.
 */
class ConfusionPairMiner {
    class WrongPickRow(
        targetKanji: String?,
        selectedKanji: String?,
        @JvmField val correct: Boolean,
        reviewedAtMillis: Long,
    ) {
        @JvmField val targetKanji: String = TextUtil.normalizeSingleKanji(targetKanji)
        @JvmField val selectedKanji: String = TextUtil.normalizeSingleKanji(selectedKanji)
        @JvmField val reviewedAtMillis: Long = reviewedAtMillis.coerceAtLeast(0L)
    }

    fun minePairs(rows: List<WrongPickRow?>?, nowMillis: Long): List<RecordsImportModels.SimilarKanjiPair> {
        val windowStartMillis = windowStartMillis(nowMillis)
        val wrongPickCounts = LinkedHashMap<String, Int>()
        val firstSeenByPair = HashMap<String, Long>()
        val lastSeenByPair = HashMap<String, Long>()
        for (row in rows.orEmpty()) {
            if (row == null || !isMinableWrongPick(row, windowStartMillis, nowMillis)) {
                continue
            }
            val key = canonicalKey(row.targetKanji, row.selectedKanji)
            wrongPickCounts[key] = saturatingAddNonNegative(wrongPickCounts[key] ?: 0, 1)
            firstSeenByPair.merge(key, row.reviewedAtMillis) { a, b -> minOf(a, b) }
            lastSeenByPair.merge(key, row.reviewedAtMillis) { a, b -> maxOf(a, b) }
        }
        val out = ArrayList<RecordsImportModels.SimilarKanjiPair>()
        for ((key, count) in wrongPickCounts) {
            if (count < MIN_WRONG_PICKS) {
                continue
            }
            val glyphs = key.split(KEY_SEPARATOR)
            out.add(
                RecordsImportModels.SimilarKanjiPair(
                    glyphs[0],
                    glyphs[1],
                    SOURCE_USER_CONFUSION,
                    firstSeenByPair[key] ?: 0L,
                    lastSeenByPair[key] ?: 0L,
                ),
            )
        }
        out.sortWith(compareBy({ it.kanjiA }, { it.kanjiB }))
        return out
    }

    private fun isMinableWrongPick(row: WrongPickRow, windowStartMillis: Long, nowMillis: Long): Boolean {
        return !row.correct &&
            row.targetKanji.isNotEmpty() &&
            row.selectedKanji.isNotEmpty() &&
            row.targetKanji != row.selectedKanji &&
            row.reviewedAtMillis >= windowStartMillis &&
            row.reviewedAtMillis <= nowMillis
    }

    private fun canonicalKey(first: String, second: String): String {
        return if (first <= second) {
            first + KEY_SEPARATOR + second
        } else {
            second + KEY_SEPARATOR + first
        }
    }

    companion object {
        const val SOURCE_USER_CONFUSION: String = "user:confusion"
        const val MIN_WRONG_PICKS: Int = 2
        const val WINDOW_DAYS: Long = 90L
        private const val DAY_MILLIS: Long = 24L * 60L * 60L * 1000L
        private const val KEY_SEPARATOR: String = "\u0000"

        @JvmStatic
        fun windowStartMillis(nowMillis: Long): Long {
            return saturatingSubtract(nowMillis, WINDOW_DAYS * DAY_MILLIS)
        }
    }
}
