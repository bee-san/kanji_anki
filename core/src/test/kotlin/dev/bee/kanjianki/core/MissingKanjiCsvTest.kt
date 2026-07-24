package dev.bee.kanjianki.core

import java.io.StringReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MissingKanjiCsvTest {
    @Test
    fun writesDeterministicAnkiReadyCsvWithCanonicalFields() {
        val supplementary = String(Character.toChars(0x20000))
        val output = StringBuilder()

        val result = MissingKanjiCsv.write(
            listOf(
                candidate(supplementary, "supplementary", null),
                candidate("火", "fire", 20),
                candidate("水", "water", 10),
            ),
            output,
        )

        assertEquals(3, result.exportedCount)
        assertEquals(
            listOf(
                MissingKanjiExportPlanner.FIELD_NAMES,
                listOf("水", "water", "スイ", "みず", "10", "kani-missing:水"),
                listOf("火", "fire", "スイ", "みず", "20", "kani-missing:火"),
                listOf(
                    supplementary,
                    "supplementary",
                    "スイ",
                    "みず",
                    "",
                    "kani-missing:$supplementary",
                ),
            ),
            parse(output.toString()),
        )
        assertTrue(output.lines().dropLast(1).all { line -> line.startsWith('"') })
        assertTrue(output.toString().contains("\r\n"))
    }

    @Test
    fun quotesCommasQuotesNewlinesAndJapaneseTextRoundTrip() {
        val output = StringBuilder()
        MissingKanjiCsv.write(
            listOf(
                candidate(
                    literal = "語",
                    meaning = "language, \"word\"\n日本語",
                    rank = 301,
                ),
            ),
            output,
        )

        val row = parse(output.toString())[1]
        assertEquals("language, &quot;word&quot; 日本語", row[1])
        assertEquals("語", row[0])
    }

    @Test
    fun preventsSpreadsheetFormulaEvaluation() {
        val output = StringBuilder()
        MissingKanjiCsv.write(
            listOf(
                MissingKanjiCandidate(
                    literal = "式",
                    meanings = listOf("=1+1", "+SUM(A1:A2)", "-2", "@cmd"),
                    onReadings = listOf("ショク"),
                    jitenRank = 500,
                ),
            ),
            output,
        )

        assertEquals(
            "&#61;1+1; +SUM(A1:A2); -2; @cmd",
            parse(output.toString())[1][1],
        )
    }

    @Test
    fun reportsInvalidAndDuplicateCandidatesWithoutWritingExtraRows() {
        val output = StringBuilder()

        val result = MissingKanjiCsv.write(
            listOf(
                candidate("水", "water", 10),
                candidate("水", "fluid", 11),
                candidate("invalid", "ignored", 12),
            ),
            output,
        )

        assertEquals(3, result.requestedCount)
        assertEquals(1, result.exportedCount)
        assertEquals(1, result.invalidCount)
        assertEquals(1, result.duplicateCount)
        assertEquals(2, result.skippedCount)
        assertEquals(setOf("invalid"), result.invalidLiterals)
        assertEquals(2, parse(output.toString()).size)
    }

    @Test
    fun handlesFiveThousandRowsWithoutChangingRankOrder() {
        val output = StringBuilder()
        val candidates = (5_000 downTo 1).map { rank ->
            candidate(
                literal = String(Character.toChars(0x4E00 + rank)),
                meaning = "meaning $rank",
                rank = rank,
            )
        }

        val result = MissingKanjiCsv.write(candidates, output)
        val rows = parse(output.toString())

        assertEquals(5_000, result.exportedCount)
        assertEquals(5_001, rows.size)
        assertEquals("1", rows[1][4])
        assertEquals("5000", rows.last()[4])
    }

    private fun candidate(
        literal: String,
        meaning: String,
        rank: Int?,
    ): MissingKanjiCandidate = MissingKanjiCandidate(
        literal = literal,
        meanings = listOf(meaning),
        onReadings = listOf("スイ"),
        kunReadings = listOf("みず"),
        jitenRank = rank,
    )

    private fun parse(csv: String): List<List<String>> {
        val reader = StringReader(csv)
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        var cell = StringBuilder()
        var quoted = false
        while (true) {
            val current = reader.read()
            if (current < 0) {
                break
            }
            val character = current.toChar()
            if (quoted) {
                if (character == '"') {
                    reader.mark(1)
                    if (reader.read() == '"'.code) {
                        cell.append('"')
                    } else {
                        reader.reset()
                        quoted = false
                    }
                } else {
                    cell.append(character)
                }
            } else {
                when (character) {
                    '"' -> quoted = true
                    ',' -> {
                        row.add(cell.toString())
                        cell = StringBuilder()
                    }
                    '\r' -> Unit
                    '\n' -> {
                        row.add(cell.toString())
                        rows.add(row)
                        row = ArrayList()
                        cell = StringBuilder()
                    }
                    else -> cell.append(character)
                }
            }
        }
        return rows
    }
}
