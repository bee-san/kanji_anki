package dev.bee.kanjianki.core

import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class StudyCollectionLookupTest {
    @Test
    fun dashboardRowByKanjiReturnsMatchingRow() {
        val expected = row("語")

        val result = StudyCollectionLookup.dashboardRowByKanji(
            listOf(row("字"), expected),
            "語",
        )

        assertSame(expected, result)
    }

    @Test
    fun dashboardRowByKanjiReturnsNullForMissingOrUnsafeInputs() {
        assertNull(StudyCollectionLookup.dashboardRowByKanji(listOf(row("語")), "字"))
        assertNull(StudyCollectionLookup.dashboardRowByKanji(null, "語"))
        assertNull(StudyCollectionLookup.dashboardRowByKanji(listOf(row("語")), null))
        assertNull(StudyCollectionLookup.dashboardRowByKanji(listOf<RecordsImportModels.DashboardRow?>(null), "語"))
    }

    @Test
    fun dashboardRowsByKanjiIndexesFirstMatchAndSkipsNulls() {
        val expected = row("語")
        val other = row("字")

        val result = StudyCollectionLookup.dashboardRowsByKanji(
            listOf(null, expected, other, row("語")),
        )

        assertSame(expected, result["語"])
        assertSame(other, result["字"])
        assertNull(result["未"])
    }

    @Test
    fun studyItemByKanjiReturnsMatchingItem() {
        val expected = item("語")

        val result = StudyCollectionLookup.studyItemByKanji(
            listOf(item("字"), expected),
            "語",
        )

        assertSame(expected, result)
    }

    @Test
    fun studyItemByKanjiReturnsNullForMissingOrUnsafeInputs() {
        assertNull(StudyCollectionLookup.studyItemByKanji(listOf(item("語")), "字"))
        assertNull(StudyCollectionLookup.studyItemByKanji(null, "語"))
        assertNull(StudyCollectionLookup.studyItemByKanji(listOf(item("語")), null))
        assertNull(StudyCollectionLookup.studyItemByKanji(listOf<RecordsStudyModels.StudyItem?>(null), "語"))
    }

    @Test
    fun studyItemsByKanjiIndexesFirstMatchAndSkipsNulls() {
        val expected = item("語")
        val other = item("字")

        val result = StudyCollectionLookup.studyItemsByKanji(
            listOf(null, expected, other, item("語")),
        )

        assertSame(expected, result["語"])
        assertSame(other, result["字"])
        assertNull(result["未"])
    }

    private fun row(kanji: String): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji,
            900,
            "meaning",
            "reading",
            "search",
            1,
            "weak_support",
            "reason",
            1,
            0,
            0,
            emptyList<RecordsImportModels.Example>(),
        )
    }

    private fun item(kanji: String): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(kanji, "review", 1000L, 1.0, 2.0, 1, 0, 0, 0, "", 1000L)
    }
}
