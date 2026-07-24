package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MissingKanjiExportPlannerTest {
    @Test
    fun buildsStableRankedPayloadWithSourceIds() {
        val supplementary = String(Character.toChars(0x20000))

        val plan = MissingKanjiExportPlanner.plan(
            listOf(
                candidate(supplementary, rank = null),
                candidate("火", rank = 20),
                candidate("水", rank = 10),
            ),
        )

        assertEquals(listOf("水", "火", supplementary), plan.notes.map { it.literal })
        assertEquals(
            listOf("水", "water", "スイ", "みず", "10", "kani-missing:水"),
            plan.notes.first().fields,
        )
        assertEquals(3, plan.requestedCount)
        assertEquals(0, plan.invalidCount)
        assertTrue(plan.invalidLiterals.isEmpty())
    }

    @Test
    fun duplicateCandidatesMergeMetadataAndUseBestRank() {
        val plan = MissingKanjiExportPlanner.plan(
            listOf(
                candidate("水", meanings = listOf("water"), on = emptyList(), rank = 20),
                candidate("水", meanings = listOf("fluid"), on = listOf("スイ"), rank = 10),
            ),
        )

        assertEquals(1, plan.duplicateCount)
        assertEquals(1, plan.notes.size)
        assertEquals("water; fluid", plan.notes.single().meaning)
        assertEquals("スイ", plan.notes.single().onReading)
        assertEquals(10, plan.notes.single().jitenRank)
    }

    @Test
    fun duplicateMergeOrderIsStableAndRepeatedMetadataIsCollapsed() {
        val plan = MissingKanjiExportPlanner.plan(
            listOf(
                candidate(
                    "語",
                    meanings = listOf("language", "word"),
                    on = listOf("ゴ", "ギョ"),
                ),
                candidate(
                    "語",
                    meanings = listOf("word", "speech"),
                    on = listOf("ゴ"),
                ),
            ),
        )

        assertEquals("language; word; speech", plan.notes.single().meaning)
        assertEquals("ゴ; ギョ", plan.notes.single().onReading)
    }

    @Test
    fun fieldTextIsHtmlSafeAndInvalidLiteralsAreReported() {
        val plan = MissingKanjiExportPlanner.plan(
            listOf(
                candidate("語", meanings = listOf("""A&B <word> "quoted" 'single'""")),
                candidate("not-kanji"),
            ),
        )

        assertEquals(
            "A&amp;B &lt;word&gt; &quot;quoted&quot; &#39;single&#39;",
            plan.notes.single().meaning,
        )
        assertEquals(setOf("not-kanji"), plan.invalidLiterals)
        assertEquals(1, plan.invalidCount)
    }

    @Test
    fun repeatedInvalidRequestsRetainTheirPreciseSkippedCount() {
        val plan = MissingKanjiExportPlanner.plan(
            listOf(candidate("invalid"), candidate("invalid")),
        )

        assertEquals(2, plan.invalidCount)
        assertEquals(setOf("invalid"), plan.invalidLiterals)
        assertTrue(plan.notes.isEmpty())
    }

    @Test
    fun leadingSpreadsheetFormulaCharactersAreHtmlEncodedInCanonicalPayload() {
        val plan = MissingKanjiExportPlanner.plan(
            listOf(candidate("式", meanings = listOf("=1+1"))),
        )

        assertEquals("&#61;1+1", plan.notes.single().meaning)
    }

    @Test
    fun sourceIdRejectsAnythingOtherThanOneKanji() {
        assertThrows(IllegalArgumentException::class.java) {
            MissingKanjiExportPlanner.sourceId("water")
        }
    }

    @Test
    fun nonPositiveRanksAreExportedAsUnranked() {
        val plan = MissingKanjiExportPlanner.plan(
            listOf(
                candidate("零", rank = 0),
                candidate("負", rank = -1),
            ),
        )

        assertTrue(plan.notes.all { note -> note.jitenRank == null })
        assertTrue(plan.notes.all { note -> note.fields[4].isEmpty() })
    }

    private fun candidate(
        literal: String,
        meanings: List<String> = listOf("water"),
        on: List<String> = listOf("スイ"),
        kun: List<String> = listOf("みず"),
        rank: Int? = 10,
    ): MissingKanjiCandidate = MissingKanjiCandidate(
        literal = literal,
        meanings = meanings,
        onReadings = on,
        kunReadings = kun,
        jitenRank = rank,
    )
}
