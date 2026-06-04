package dev.bee.kanjianki.core

import java.util.ArrayList
import java.util.Collections
import java.util.Locale

object FrequencyRetentionRanges {
    const val MIN_RETENTION = 0.10
    const val MAX_RETENTION = 0.99
    const val MIN_RANK = 1
    const val MAX_RANK = 20000
    const val EXAMPLE_TEXT = "1-500=95%\n501-2000=90%\n2001-20000=85%"

    @JvmStatic
    fun exampleText(): String = EXAMPLE_TEXT

    @JvmStatic
    fun retentionForRank(text: String?, rank: Int?): Double? {
        if (rank == null || rank < MIN_RANK || rank > MAX_RANK) {
            return null
        }
        for (rule in parse(text)) {
            if (rule.contains(rank)) {
                return rule.retention
            }
        }
        return null
    }

    @JvmStatic
    fun parse(text: String?): List<Rule> {
        val raw = text ?: ""
        val rules = ArrayList<Rule>()
        val lines = raw.split(Regex("\\R"))
        for ((index, originalLine) in lines.withIndex()) {
            val line = stripComment(originalLine).trim()
            if (line.isNotEmpty()) {
                rules.add(parseLine(line, index + 1))
            }
        }
        rules.sortBy { it.minRank }
        validateNoOverlaps(rules)
        return Collections.unmodifiableList(rules)
    }

    private fun stripComment(line: String): String {
        val comment = line.indexOf('#')
        return if (comment < 0) line else line.substring(0, comment)
    }

    private fun parseLine(line: String, lineNumber: Int): Rule {
        val pieces = line.split("=", limit = 2)
        if (pieces.size != 2) {
            throw IllegalArgumentException(errorPrefix(lineNumber) + "Use rank-range=retention.")
        }
        val range = parseRange(pieces[0].trim(), lineNumber)
        val retention = parseRetention(pieces[1].trim(), lineNumber)
        return newRule(range[0], range[1], retention)
    }

    private fun parseRange(value: String, lineNumber: Int): IntArray {
        if (value.isEmpty()) {
            throw IllegalArgumentException(errorPrefix(lineNumber) + "Rank range is empty.")
        }
        val normalized = value.replace("..", "-")
        val pieces = normalized.split("-", limit = 3)
        val min: Int
        val max: Int
        try {
            when (pieces.size) {
                1 -> {
                    min = pieces[0].trim().toInt()
                    max = min
                }
                2 -> {
                    min = pieces[0].trim().toInt()
                    max = pieces[1].trim().toInt()
                }
                else -> throw NumberFormatException("too many range separators")
            }
        } catch (error: NumberFormatException) {
            throw IllegalArgumentException(errorPrefix(lineNumber) + "Use numeric Jiten ranks.", error)
        }
        if (min < MIN_RANK || max > MAX_RANK || min > max) {
            throw IllegalArgumentException(errorPrefix(lineNumber) + "Use ranks 1-20000 in ascending order.")
        }
        return intArrayOf(min, max)
    }

    private fun parseRetention(value: String, lineNumber: Int): Double {
        if (value.isEmpty()) {
            throw IllegalArgumentException(errorPrefix(lineNumber) + "Retention is empty.")
        }
        val percent = value.endsWith("%")
        val numeric = if (percent) value.substring(0, value.length - 1).trim() else value
        val parsed = try {
            numeric.toDouble()
        } catch (error: NumberFormatException) {
            throw IllegalArgumentException(errorPrefix(lineNumber) + "Use numeric retention.", error)
        }
        val retention = if (percent || parsed > 1.0) parsed / 100.0 else parsed
        if (!retention.isFinite() || retention < MIN_RETENTION || retention > MAX_RETENTION) {
            throw IllegalArgumentException(errorPrefix(lineNumber) + "Use retention from 10% to 99%.")
        }
        return retention
    }

    private fun validateNoOverlaps(rules: List<Rule>) {
        var previousMax = 0
        for (rule in rules) {
            if (rule.minRank <= previousMax) {
                throw IllegalArgumentException(
                    String.format(
                        Locale.ROOT,
                        "Rank range %d-%d overlaps an earlier range.",
                        rule.minRank,
                        rule.maxRank
                    )
                )
            }
            previousMax = rule.maxRank
        }
    }

    private fun errorPrefix(lineNumber: Int): String {
        return "Line $lineNumber: "
    }

    private fun newRule(minRank: Int, maxRank: Int, retention: Double): Rule {
        val constructor = Rule::class.java.getDeclaredConstructor(
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Double::class.javaPrimitiveType,
        )
        constructor.isAccessible = true
        return constructor.newInstance(minRank, maxRank, retention)
    }

    class Rule private constructor(
        val minRank: Int,
        val maxRank: Int,
        val retention: Double,
    ) {
        fun contains(rank: Int): Boolean = rank >= minRank && rank <= maxRank
    }
}
