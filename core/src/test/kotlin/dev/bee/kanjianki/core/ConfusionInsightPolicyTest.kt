package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Test

class ConfusionInsightPolicyTest {
    @Test fun filtersKanaMergesDirectionsJoinsMeaningsAndSortsDeterministically() {
        val result = ConfusionInsightPolicy.topPairs(
            mapOf(
                "徴" to mapOf("微" to 5, "び" to 20, "二字" to 20),
                "微" to mapOf("徴" to 2),
                "待" to mapOf("持" to 2),
                "持" to mapOf("待" to 2),
                "己" to mapOf("已" to 1),
            ),
            listOf(
                inventory("徴", "sign"), inventory("微", "minute"),
                inventory("待", "wait"), inventory("持", "hold"),
            ),
            limit = 2,
        )
        assertEquals(2, result.size)
        assertEquals(ConfusionInsightPolicy.Pair("徴", "微", 5, 2, "sign", "minute"), result[0])
        assertEquals(4, result[1].total)
        assertEquals(setOf("待", "持"), setOf(result[1].firstKanji, result[1].secondKanji))
        assertEquals(emptyList<ConfusionInsightPolicy.Pair>(), ConfusionInsightPolicy.topPairs(null, null, -1))
    }

    private fun inventory(kanji: String, meaning: String) =
        RecordsImportModels.KanjiInventoryItem(kanji, meaning, "", "", 1, 1, false, 1L)
}
