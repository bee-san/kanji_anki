package dev.bee.kanjianki.syncdomain

import java.util.Locale
import java.util.regex.Pattern

class ProviderCardPolicy private constructor() {
    @JvmRecord
    data class FsrsMemoryState(
        val stability: Double?,
        val difficulty: Double?,
        val retrievability: Double?,
    )

    companion object {
        private const val STABILITY = "stability"
        private const val DIFFICULTY = "difficulty"
        private const val RETRIEVABILITY = "retrievability"
        private val EMPTY = FsrsMemoryState(null, null, null)
        private val FSRS_DATA_VALUE = Pattern.compile(
            "(?:\"|')?(stability|difficulty|retrievability|s|d|r)(?:\"|')?\\s*[:=]\\s*\"?([-+]?[0-9]+(?:\\.[0-9]+)?)\"?",
            Pattern.CASE_INSENSITIVE
        )
        private val FINITE_DOUBLE_VALUE =
            Pattern.compile("[-+]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][-+]?[0-9]+)?")

        @JvmStatic
        fun shouldReportCardProgress(scanned: Int, total: Int): Boolean {
            if (scanned <= 0 || scanned == total || total <= 100) {
                return true
            }
            if (scanned <= 10) {
                return true
            }
            return scanned % (if (total <= 1000) 10 else 50) == 0
        }

        @JvmStatic
        fun fsrsMemoryState(
            fsrsStability: String?,
            legacyStability: String?,
            fsrsDifficulty: String?,
            legacyDifficulty: String?,
            fsrsRetrievability: String?,
            legacyRetrievability: String?,
            serializedData: String?,
        ): FsrsMemoryState {
            val stability = firstFiniteDouble(fsrsStability, legacyStability)
            val difficulty = firstFiniteDouble(fsrsDifficulty, legacyDifficulty)
            val retrievability = firstFiniteDouble(fsrsRetrievability, legacyRetrievability)
            if (stability != null || difficulty != null || retrievability != null) {
                return FsrsMemoryState(stability, difficulty, retrievability)
            }
            return parseFsrsData(serializedData)
        }

        @JvmStatic
        fun parseFsrsData(data: String?): FsrsMemoryState {
            if (data == null || data.trim().isEmpty()) {
                return EMPTY
            }
            var stability: Double? = null
            var difficulty: Double? = null
            var retrievability: Double? = null
            val matcher = FSRS_DATA_VALUE.matcher(data)
            while (matcher.find()) {
                val value = parseDouble(matcher.group(2)) ?: continue
                when (matcher.group(1).lowercase(Locale.ROOT)) {
                    STABILITY, "s" -> stability = value
                    DIFFICULTY, "d" -> difficulty = value
                    else -> retrievability = value
                }
            }
            return FsrsMemoryState(stability, difficulty, retrievability)
        }

        @JvmStatic
        fun parseDouble(value: String?): Double? {
            if (value == null || !FINITE_DOUBLE_VALUE.matcher(value).matches()) {
                return null
            }
            val parsed = value.toDouble()
            return if (parsed.isInfinite()) null else parsed
        }

        private fun firstFiniteDouble(firstValue: String?, fallbackValue: String?): Double? {
            val value = parseDouble(trim(firstValue))
            return value ?: parseDouble(trim(fallbackValue))
        }

        private fun trim(value: String?): String? = value?.trim()
    }
}
