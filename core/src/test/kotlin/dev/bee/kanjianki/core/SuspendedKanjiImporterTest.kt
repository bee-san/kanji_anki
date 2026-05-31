package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.StringReader
import java.util.LinkedHashMap

class SuspendedKanjiImporterTest {
    @Test
    fun importsOnlyKnownRanksInsideConfiguredRange() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        assertEquals(100, settings.suspendedRankMin)
        assertEquals(3000, settings.suspendedRankMax)
        assertEquals(3000, settings.suspendedRankCutoff)
        val ranks = JitenKanjiRanks.parseCsv(StringReader("Kanji,Rank\n日,1\n提,99\n示,100\n裂,3000\n遅,3001\n"))
        val snapshot = RecordsSyncModels.CollectionSnapshot(
            listOf(
                note(1, "提示", "ていじ"),
                note(2, "裂ける謎", "さける"),
                note(3, "遅い", "おそい")
            ),
            listOf(
                card(10, 1, true),
                card(20, 2, true),
                card(30, 3, true)
            )
        )

        val imports = SuspendedKanjiImporter(ranks, settings.suspendedRankMin, settings.suspendedRankMax).importFrom(snapshot, settings)

        assertEquals(2, imports.size)
        assertEquals("示", imports[0].kanji)
        assertEquals(100, imports[0].jitenRank)
        assertEquals("裂", imports[1].kanji)
        assertEquals(3000, imports[1].jitenRank)
        assertFalse(imports.any { it.kanji == "謎" })
        assertEquals(20L, imports[1].sources[0].cardId)
    }

    @Test
    fun deduplicatesKanjiButKeepsMultipleSourceCards() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val ranks = JitenKanjiRanks.parseCsv(StringReader("裂,2900\n傷,2900\n"))
        val snapshot = RecordsSyncModels.CollectionSnapshot(
            listOf(note(1, "裂ける", "さける"), note(2, "裂傷", "れっしょう")),
            listOf(card(10, 1, true), card(20, 2, true))
        )

        val imports = SuspendedKanjiImporter(ranks, settings.suspendedRankMin, settings.suspendedRankMax).importFrom(snapshot, settings)

        assertEquals("傷", imports[0].kanji)
        assertEquals("裂", imports[1].kanji)
        assertEquals(2, imports[1].sources.size)
    }

    @Test
    fun cutoffConstructorAndSwappedRangeStillImportConfiguredRanks() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val ranks = JitenKanjiRanks.parseCsv(StringReader("示,100\n裂,3000\n"))
        val snapshot = RecordsSyncModels.CollectionSnapshot(
            listOf(note(1, "提示", "ていじ"), note(2, "裂ける", "さける")),
            listOf(card(10, 1, true), card(20, 2, true))
        )

        val cutoffImports = SuspendedKanjiImporter(ranks, 3000).importFrom(snapshot, settings)
        val swappedRangeImports = SuspendedKanjiImporter(ranks, 3000, 100).importFrom(snapshot, settings)

        assertEquals(listOf("示", "裂"), kanjiList(cutoffImports))
        assertEquals(listOf("示", "裂"), kanjiList(swappedRangeImports))
    }

    @Test
    fun ignoresActiveCardsAndSuspendedCardsWithoutNotes() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val ranks = JitenKanjiRanks.parseCsv(StringReader("裂,2900\n"))
        val snapshot = RecordsSyncModels.CollectionSnapshot(
            listOf(note(1, "裂ける", "さける")),
            listOf(card(10, 1, false), card(20, 999, true))
        )

        assertEquals(0, SuspendedKanjiImporter(ranks, 3000).importFrom(snapshot, settings).size)
    }

    private fun kanjiList(imports: List<RecordsImportModels.SuspendedImport>): List<String> {
        val out = ArrayList<String>()
        for (item in imports) {
            out.add(item.kanji)
        }
        return out
    }

    companion object {
        @JvmStatic
        fun note(id: Long, expression: String, reading: String): RecordsSyncModels.Note {
            val fields = LinkedHashMap<String, String>()
            val settings = RecordsSyncModels.Settings.kikuDefaults()
            fields[settings.expressionField] = expression
            fields[settings.readingField] = reading
            fields[settings.meaningField] = "<b>meaning</b>"
            fields[settings.sentenceField] = expression + " sentence"
            fields[settings.frequencyField] = "9999"
            fields[settings.frequencySortField] = "9999"
            return RecordsSyncModels.Note(id, "Kiku", fields, emptyList())
        }

        @JvmStatic
        fun card(cardId: Long, noteId: Long, suspended: Boolean): RecordsSyncModels.Card {
            return RecordsSyncModels.Card(cardId, noteId, 0, "例文マイニング", if (suspended) -1 else 2, if (suspended) 3 else 2, 0, if (suspended) 0 else 30, 3, 0, suspended)
        }
    }
}
