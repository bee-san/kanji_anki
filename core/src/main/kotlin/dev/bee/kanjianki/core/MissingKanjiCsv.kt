package dev.bee.kanjianki.core

import java.io.IOException

/**
 * Writes the canonical Missing Kanji payload as UTF-8-ready RFC 4180 CSV.
 *
 * The canonical planner HTML-encodes formula-sensitive leading characters in
 * user-facing fields, and this writer adds a final guard for any future field.
 */
object MissingKanjiCsv {
    const val MIME_TYPE: String = "text/csv"

    data class Result(
        val requestedCount: Int,
        val exportedCount: Int,
        val invalidLiterals: Set<String>,
        val invalidCount: Int,
        val duplicateCount: Int,
    ) {
        val skippedCount: Int
            get() = invalidCount + duplicateCount
    }

    @JvmStatic
    @Throws(IOException::class)
    fun write(
        candidates: Iterable<MissingKanjiCandidate>,
        output: Appendable,
    ): Result {
        val plan = MissingKanjiExportPlanner.plan(candidates)
        writeRow(output, MissingKanjiExportPlanner.FIELD_NAMES)
        for (note in plan.notes) {
            writeRow(output, note.fields)
        }
        return Result(
            requestedCount = plan.requestedCount,
            exportedCount = plan.notes.size,
            invalidLiterals = plan.invalidLiterals,
            invalidCount = plan.invalidCount,
            duplicateCount = plan.duplicateCount,
        )
    }

    private fun writeRow(output: Appendable, values: List<String>) {
        values.forEachIndexed { index, value ->
            if (index > 0) {
                output.append(',')
            }
            appendCell(output, protectFormula(value))
        }
        output.append("\r\n")
    }

    private fun appendCell(output: Appendable, value: String) {
        output.append('"')
        for (character in value) {
            if (character == '"') {
                output.append("\"\"")
            } else {
                output.append(character)
            }
        }
        output.append('"')
    }

    private fun protectFormula(value: String): String {
        val first = value.firstOrNull() ?: return value
        return if (first in FORMULA_PREFIXES) "'$value" else value
    }

    private val FORMULA_PREFIXES = setOf('=', '+', '-', '@', '\t', '\r')
}
