package dev.bee.kanjianki

import dev.bee.kanjianki.core.DictionaryLookup
import dev.bee.kanjianki.core.RecordsImportModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MainActivityStudyAnswerKanjiDetailsModelTest {
    @Test
    fun buildsDictionaryMetadataBreakdownAndStrokeOrderFromDictionaryEntry() {
        val entry = kanjiEntry(
            literal = "抗",
            meanings = listOf("to resist", "to oppose"),
            onReadings = listOf("コウ"),
            kunReadings = listOf("あらが.う"),
            nanoriReadings = listOf("たか"),
            strokeCount = 8,
            grade = 0,
            radical = 64,
            kanjidicFrequency = 100,
            jitenRank = 12,
        )

        val model = studyAnswerKanjiDetailsModel(
            kanji = "抗",
            dictionaryEntry = entry,
            examples = emptyList(),
        )

        assertEquals("抗", model.kanji)

        assertEquals(StudyAnswerSectionContentState.READY, model.details.contentState)
        assertEquals("8 strokes • radical 64", model.details.summary)
        assertEquals(listOf("to resist", "to oppose"), model.details.body!!.meanings)
        assertEquals(listOf("On", "Kun", "Nanori"), model.details.body.readingGroups.map { it.label })
        assertEquals(listOf("コウ"), model.details.body.readingGroups[0].readings)
        assertEquals(8, model.details.body.strokeCount)
        assertEquals(null, model.details.body.grade)
        assertEquals(64, model.details.body.radical)
        assertEquals(100, model.details.body.frequency)
        assertEquals(12, model.details.body.jitenRank)

        assertEquals(StudyAnswerSectionContentState.READY, model.breakdown.contentState)
        assertEquals("Radical only", model.breakdown.summary)
        assertEquals(64, model.breakdown.body!!.radicalNumber)
        assertTrue(model.breakdown.body.componentRows.isEmpty())
        assertEquals("Component breakdown is still molting. Radical data is shown for now.", model.breakdown.body.fallbackCopy)

        assertEquals(StudyAnswerSectionContentState.READY, model.strokeOrder.contentState)
        assertEquals("8 strokes", model.strokeOrder.summary)
        assertEquals(StudyAnswerStrokeOrderAvailability.COUNT_ONLY, model.strokeOrder.body!!.availability)
        assertEquals(8, model.strokeOrder.body.strokeCount)
        assertEquals("Stroke-order animation needs a licensed offline asset before Kani can draw it here.", model.strokeOrder.body.fallbackCopy)
    }

    @Test
    fun usesEmptyStateCopyWhenDictionaryMetadataIsMissing() {
        val model = studyAnswerKanjiDetailsModel(
            kanji = "抗",
            dictionaryEntry = null,
            examples = emptyList(),
        )

        assertEquals(StudyAnswerSectionContentState.EMPTY, model.details.contentState)
        assertEquals("Local dictionary", model.details.summary)
        assertEquals("Kani couldn't find local details for this kanji yet.", model.details.emptyTitle)
        assertEquals("Review still works; this drawer can fill in after dictionary data syncs.", model.details.emptyBody)

        assertEquals(StudyAnswerSectionContentState.EMPTY, model.breakdown.contentState)
        assertEquals("No radical or component data yet.", model.breakdown.summary)
        assertEquals("No radical or component data yet.", model.breakdown.emptyTitle)

        assertEquals(StudyAnswerSectionContentState.UNAVAILABLE, model.strokeOrder.contentState)
        assertEquals("Stroke data is not available for this kanji yet.", model.strokeOrder.summary)
        assertEquals("Stroke data is not available for this kanji yet.", model.strokeOrder.emptyTitle)
        assertEquals("Stroke-order animation needs a licensed offline asset before Kani can draw it here.", model.strokeOrder.emptyBody)
    }

    @Test
    fun treatsContentlessDictionaryEntryAsEmpty() {
        val section = studyAnswerDictionarySection(
            kanjiEntry(
                literal = "抗",
                meanings = emptyList(),
                onReadings = emptyList(),
                kunReadings = emptyList(),
                nanoriReadings = emptyList(),
                strokeCount = 0,
                grade = 0,
                radical = 0,
                kanjidicFrequency = 0,
                jitenRank = null,
            ),
        )

        assertEquals(StudyAnswerSectionContentState.EMPTY, section.contentState)
        assertEquals(null, section.body)
    }

    @Test
    fun buildsUsedInAnkiRowsWithStableOrderingAndShortLabels() {
        val current = example(
            sourceType = "active",
            cardId = 100,
            noteId = 10,
            expression = "抗議",
            reading = "こうぎ",
            meaning = "to protest",
        )
        val first = example(
            sourceType = "suspended",
            cardId = 200,
            noteId = 20,
            expression = "抗体",
            reading = "こうたい",
            meaning = "antibody",
        )
        val second = example(
            sourceType = "suspended",
            cardId = 300,
            noteId = 30,
            expression = "抗体",
            reading = "こうたい",
            meaning = "antibody duplicate",
        )
        val third = example(
            sourceType = "active",
            cardId = 400,
            noteId = 40,
            expression = "抵抗",
            reading = "ていこう",
            meaning = "resistance",
        )

        val rows = studyAnswerUsedInAnkiRows(
            examples = listOf(third, first, second),
            currentExample = current,
            openAnkiDroidSupported = false,
            deckNamesByCardId = mapOf(
                100L to "Core Deck",
                200L to "A very long deck name that should be dropped because it is noisy",
            ),
            modelNamesByNoteId = mapOf(
                10L to "Basic Model",
                20L to "Another very long note model name that should be dropped",
            ),
        )

        assertEquals(listOf("抗議", "抗体", "抗体", "抵抗"), rows.map { it.expression })
        assertTrue(rows[0].isPrimarySource)
        assertEquals(listOf("Active", "Core Deck", "Basic Model"), rows[0].labels)
        assertEquals(listOf("Suspended"), rows[1].labels)
        assertEquals(10L, rows[0].noteId)
        assertEquals(20L, rows[1].noteId)
        assertEquals(30L, rows[2].noteId)
    }

    @Test
    fun truncatesUsedInAnkiRowsAndExposesToggleCopy() {
        val current = example(
            sourceType = "active",
            cardId = 100,
            noteId = 10,
            expression = "抗議",
            reading = "こうぎ",
            meaning = "to protest",
        )
        val second = example(
            sourceType = "suspended",
            cardId = 200,
            noteId = 20,
            expression = "抗体",
            reading = "こうたい",
            meaning = "antibody",
        )
        val third = example(
            sourceType = "suspended",
            cardId = 300,
            noteId = 30,
            expression = "抵抗",
            reading = "ていこう",
            meaning = "resistance",
        )
        val fourth = example(
            sourceType = "active",
            cardId = 400,
            noteId = 40,
            expression = "耐抗",
            reading = "たいこう",
            meaning = "fake",
        )

        val collapsed = studyAnswerUsedInAnkiSection(
            examples = listOf(second, third, fourth),
            currentExample = current,
            showAll = false,
            openAnkiDroidSupported = false,
        )
        assertEquals(StudyAnswerSectionContentState.READY, collapsed.contentState)
        assertEquals("4 synced words", collapsed.summary)
        assertEquals(4, collapsed.body!!.rows.size)
        assertEquals(3, collapsed.body.visibleRows.size)
        assertEquals(1, collapsed.body.hiddenRowCount)
        assertEquals("Show all 4", collapsed.body.toggleLabel)

        val expanded = studyAnswerUsedInAnkiSection(
            examples = listOf(second, third, fourth),
            currentExample = current,
            showAll = true,
            openAnkiDroidSupported = false,
        )
        assertEquals(4, expanded.body!!.visibleRows.size)
        assertEquals(0, expanded.body.hiddenRowCount)
        assertEquals("Show fewer", expanded.body.toggleLabel)
    }

    @Test
    fun usesEmptyCopyForUsedInAnkiWhenThereAreNoRows() {
        val section = studyAnswerUsedInAnkiSection(
            examples = emptyList(),
            currentExample = null,
            showAll = false,
            openAnkiDroidSupported = false,
        )

        assertEquals(StudyAnswerSectionContentState.EMPTY, section.contentState)
        assertEquals("No synced words", section.summary)
        assertEquals("No other synced Anki words yet.", section.emptyTitle)
        assertEquals("Sync more cards and Kani will connect them here.", section.emptyBody)
    }

    @Test
    fun opensAnkiDroidWhenSupportedAndFallsBackToCopyWhenUnavailable() {
        val openAction = studyAnswerAnkiTapAction(
            noteId = 42,
            cardId = 99,
            openAnkiDroidSupported = true,
        )
        assertTrue(openAction is StudyAnswerAnkiTapActionModel.OpenAnkiDroid)
        openAction as StudyAnswerAnkiTapActionModel.OpenAnkiDroid
        assertEquals(42L, openAction.noteId)
        assertEquals(99L, openAction.cardId)

        val noteCopyAction = studyAnswerAnkiTapAction(
            noteId = 42,
            cardId = 99,
            openAnkiDroidSupported = false,
        )
        assertTrue(noteCopyAction is StudyAnswerAnkiTapActionModel.CopyId)
        noteCopyAction as StudyAnswerAnkiTapActionModel.CopyId
        assertEquals(StudyAnswerAnkiCopiedIdKind.NOTE, noteCopyAction.kind)
        assertEquals(42L, noteCopyAction.value)
        assertEquals("Anki link unavailable — copied note ID.", noteCopyAction.toastMessage)

        val cardCopyAction = studyAnswerAnkiTapAction(
            noteId = null,
            cardId = 99,
            openAnkiDroidSupported = false,
        ) as StudyAnswerAnkiTapActionModel.CopyId
        assertEquals(StudyAnswerAnkiCopiedIdKind.CARD, cardCopyAction.kind)
        assertEquals(99L, cardCopyAction.value)
        assertEquals("Anki link unavailable — copied card ID.", cardCopyAction.toastMessage)

        assertSame(
            StudyAnswerAnkiTapActionModel.Unavailable,
            studyAnswerAnkiTapAction(noteId = null, cardId = null, openAnkiDroidSupported = false),
        )
    }

    @Test
    fun buildsWhyThisCardPreviewFromCurrentAndOtherExamples() {
        val current = example(
            sourceType = "active",
            cardId = 100,
            noteId = 10,
            expression = "抗議",
            reading = "こうぎ",
            meaning = "to protest",
        )
        val second = example(
            sourceType = "suspended",
            cardId = 200,
            noteId = 20,
            expression = "抗体",
            reading = "こうたい",
            meaning = "antibody",
        )
        val third = example(
            sourceType = "suspended",
            cardId = 300,
            noteId = 30,
            expression = "抵抗",
            reading = "ていこう",
            meaning = "resistance",
        )
        val fourth = example(
            sourceType = "active",
            cardId = 400,
            noteId = 40,
            expression = "耐抗",
            reading = "たいこう",
            meaning = "fake",
        )

        val section = studyAnswerWhyThisCardSection(
            examples = listOf(second, third, fourth),
            currentExample = current,
        )

        assertEquals(StudyAnswerSectionContentState.READY, section.contentState)
        assertEquals("From: 抗議", section.summary)
        assertEquals("抗議", section.body!!.sourceExpression)
        assertEquals("こうぎ", section.body.sourceReading)
        assertEquals(2, section.body.previewExamples.size)
        assertEquals(listOf("抗体", "抵抗"), section.body.previewExamples.map { it.expression })
        assertEquals(null, section.body.fallbackCopy)

        val fallback = studyAnswerWhyThisCardSection(examples = emptyList(), currentExample = null)
        assertEquals(StudyAnswerSectionContentState.EMPTY, fallback.contentState)
        assertEquals(null, fallback.body)
        assertEquals("This card came from your synced study queue.", fallback.emptyBody)
    }

    @Test
    fun detailsModelsUseLocalizedCopy() {
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.JAPANESE)
            val details = studyAnswerKanjiDetailsModel(
                kanji = "抗",
                dictionaryEntry = null,
                examples = emptyList(),
            )

            assertEquals("詳細", details.details.label)
            assertEquals("ローカル辞書", details.details.summary)
            assertEquals("構成", details.breakdown.label)
            assertEquals("筆順", details.strokeOrder.label)
            assertEquals("Ankiでの使用例", details.usedInAnki.label)
            assertEquals("このカードが出た理由", details.whyThisCard.label)

            val copyAction = studyAnswerAnkiTapAction(
                noteId = 42,
                cardId = null,
                openAnkiDroidSupported = false,
            ) as StudyAnswerAnkiTapActionModel.CopyId
            assertEquals("Ankiリンクを利用できないため、ノートIDをコピーしました。", copyAction.toastMessage)
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    private fun kanjiEntry(
        literal: String,
        meanings: List<String>,
        onReadings: List<String>,
        kunReadings: List<String>,
        nanoriReadings: List<String>,
        strokeCount: Int,
        grade: Int,
        radical: Int,
        kanjidicFrequency: Int,
        jitenRank: Int?,
    ): DictionaryLookup.KanjiEntry {
        return DictionaryLookup.KanjiEntry(
            DictionaryLookup.KanjiEntryFields(
                literal = literal,
                meanings = meanings,
                onReadings = onReadings,
                kunReadings = kunReadings,
                nanoriReadings = nanoriReadings,
                strokeCount = strokeCount,
                grade = grade,
                radical = radical,
                kanjidicFrequency = kanjidicFrequency,
                jitenRank = jitenRank,
            ),
        )
    }

    private fun example(
        sourceType: String,
        cardId: Long,
        noteId: Long,
        expression: String,
        reading: String,
        meaning: String,
    ): RecordsImportModels.Example {
        return RecordsImportModels.Example(
            sourceType,
            cardId,
            noteId,
            expression,
            reading,
            meaning,
            "sentence",
            false,
            0,
        )
    }
}
