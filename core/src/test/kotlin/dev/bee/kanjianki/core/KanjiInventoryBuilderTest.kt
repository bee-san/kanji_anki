package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KanjiInventoryBuilderTest {
    @Test
    fun buildsInventoryFromSnapshotImportsDashboardAndKnownKanji() {
        val builder = KanjiInventoryBuilder(2000L, RecordsSyncModels.Settings.kikuDefaults())
        builder.addSnapshotNote(note("語学", "ごがく", "language study", "語を学ぶ"))
        builder.addSuspendedImport(
            RecordsImportModels.SuspendedImport(
                "外",
                2000,
                true,
                3000,
                listOf(
                    RecordsImportModels.SuspendedSource(
                        "外",
                        10L,
                        20L,
                        "外国",
                        "がいこく",
                        "foreign country",
                        "外へ行く"
                    )
                )
            )
        )
        builder.addDashboardRow(
            RecordsImportModels.DashboardRow(
                "語",
                100,
                "words",
                "ご",
                "browser語",
                10,
                "reason",
                "Needs 語 support",
                1,
                0,
                0,
                listOf(
                    RecordsImportModels.Example(
                        "active",
                        1L,
                        2L,
                        "語彙",
                        "ごい",
                        "vocabulary",
                        "語彙を増やす",
                        false,
                        0
                    )
                )
            )
        )
        builder.addKnownKanji("済")

        val items = builder.build(emptyMap()).associateBy { it.kanji() }
        val language = items.getValue("語")
        assertEquals("language study", language.primaryMeaning())
        assertEquals("browser語", language.browserSearch())
        assertEquals("ごがく / ご / ごい", language.readings())
        assertEquals(3, language.sourceCount())
        assertEquals(1, language.exampleCount())
        assertEquals(2000L, language.firstSeenAtMillis())
        assertEquals(2000L, language.lastSeenAtMillis())
        assertTrue(language.searchText().contains("語彙"))

        assertEquals("foreign country", items.getValue("外").primaryMeaning())
        assertEquals("", items.getValue("済").primaryMeaning())
    }

    @Test
    fun preservesPreviousIdentityWhenCurrentBuildHasOnlyKnownKanji() {
        val builder = KanjiInventoryBuilder(9000L, RecordsSyncModels.Settings.kikuDefaults())
        builder.addKnownKanji("旧")
        val previous = hashMapOf<String, KanjiInventoryBuilder.PreviousItem>(
            "旧" to KanjiInventoryBuilder.PreviousItem(
                "old meaning",
                "きゅう",
                "old search",
                5,
                6,
                1234L,
                5678L
            )
        )

        val item = builder.build(previous).first()

        assertEquals("旧", item.kanji())
        assertEquals("old meaning", item.primaryMeaning())
        assertEquals("きゅう", item.readings())
        assertEquals("old search", item.browserSearch())
        assertEquals(5, item.sourceCount())
        assertEquals(6, item.exampleCount())
        assertEquals(1234L, item.firstSeenAtMillis())
        assertEquals(9000L, item.lastSeenAtMillis())
        assertTrue(item.searchText().contains("old meaning"))
        assertTrue(item.searchText().contains("old search"))
    }

    @Test
    fun capsDisplayedReadingsAndReportsHiddenCount() {
        val builder = KanjiInventoryBuilder(1L, RecordsSyncModels.Settings.kikuDefaults())
        builder.addSourceText(listOf("多"), "one", "a", "多", "")
        builder.addSourceText(listOf("多"), "two", "b", "多", "")
        builder.addSourceText(listOf("多"), "three", "c", "多", "")
        builder.addSourceText(listOf("多"), "four", "d", "多", "")

        assertEquals("a / b / c +1 more", builder.build(emptyMap()).first().readings())
    }

    @Test
    fun itemConstructorsNormalizeNullAndNegativeValuesForJavaCallers() {
        val previous = KanjiInventoryBuilder.PreviousItem(
            null,
            null,
            null,
            -1,
            -2,
            -3L,
            -4L
        )
        val built = KanjiInventoryBuilder.BuiltItem(
            null,
            null,
            null,
            null,
            null,
            -1,
            -2,
            -3L,
            -4L
        )

        assertEquals("", previous.primaryMeaning())
        assertEquals("", previous.readings())
        assertEquals("", previous.browserSearch())
        assertEquals(0, previous.sourceCount())
        assertEquals(0, previous.exampleCount())
        assertEquals(0L, previous.firstSeenAtMillis())
        assertEquals(0L, previous.lastSeenAtMillis())
        assertEquals("", built.kanji())
        assertEquals("", built.searchText())
        assertEquals(0, built.sourceCount())
        assertEquals(0L, built.lastSeenAtMillis())
    }

    private fun note(expression: String, reading: String, meaning: String, sentence: String): RecordsSyncModels.Note {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val fields = hashMapOf(
            settings.expressionField to expression,
            settings.readingField to reading,
            settings.meaningField to meaning,
            settings.sentenceField to sentence
        )
        return RecordsSyncModels.Note(1L, settings.modelName, fields, emptyList())
    }
}
