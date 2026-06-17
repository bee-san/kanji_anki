package dev.bee.kanjianki.core

import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

class ReadingExposureModels private constructor() {
    class KanjiStats(
        kanji: String?,
        totalCount: Int,
        last7DaysCount: Int,
        last14DaysCount: Int,
        last31DaysCount: Int,
        lastSeenAtMillis: Long,
    ) {
        @JvmField val kanji: String = RecordsBase.nullToEmpty(kanji)
        @JvmField val totalCount: Int = totalCount.coerceAtLeast(0)
        @JvmField val last7DaysCount: Int = last7DaysCount.coerceAtLeast(0)
        @JvmField val last14DaysCount: Int = last14DaysCount.coerceAtLeast(0)
        @JvmField val last31DaysCount: Int = last31DaysCount.coerceAtLeast(0)
        @JvmField val lastSeenAtMillis: Long = lastSeenAtMillis.coerceAtLeast(0L)
    }

    class ExposureIndex(stats: List<KanjiStats>?) {
        private val byKanji: Map<String, KanjiStats>

        init {
            val out = HashMap<String, KanjiStats>()
            for (stat in stats.orEmpty()) {
                if (stat.kanji.isNotEmpty()) {
                    out[stat.kanji] = stat
                }
            }
            byKanji = out
        }

        fun statFor(kanji: String?): KanjiStats? = byKanji[RecordsBase.nullToEmpty(kanji)]

        fun priorityBoost(kanji: String?): Double = boostFor(statFor(kanji))

        companion object {
            @JvmField val EMPTY = ExposureIndex(emptyList())

            @JvmStatic
            fun boostFor(stat: KanjiStats?): Double {
                if (stat == null) {
                    return 0.0
                }
                val shortTerm = logScore(stat.last7DaysCount) * 12.0
                val mediumTerm = logScore(max(0, stat.last14DaysCount - stat.last7DaysCount)) * 6.0
                val monthly = logScore(max(0, stat.last31DaysCount - stat.last14DaysCount)) * 3.0
                val lifetime = logScore(stat.totalCount) * 1.5
                return min(80.0, shortTerm + mediumTerm + monthly + lifetime)
            }

            private fun logScore(value: Int): Double {
                if (value <= 0) {
                    return 0.0
                }
                return ln(1.0 + value.toDouble())
            }
        }
    }
}
