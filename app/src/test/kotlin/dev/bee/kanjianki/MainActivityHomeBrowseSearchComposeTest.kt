package dev.bee.kanjianki

import dev.bee.kanjianki.core.RecordsImportModels
import java.util.Locale
import kotlin.system.measureNanoTime
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityHomeBrowseSearchComposeTest {
    @Test
    fun browseScreenDataKeepsKanjiOrderAndStudiedCount() {
        val items = listOf(
            inventoryItem("字A", suspended = false),
            inventoryItem("字B", suspended = true),
            inventoryItem("字C", suspended = false),
        )

        val data = buildBrowseScreenData(items) { item -> browseRow(item) }

        assertEquals(listOf("字A", "字B", "字C"), data.kanjiList)
        assertEquals(2, data.studiedCount)
        assertEquals(3, data.rows.size)
        assertEquals(listOf(true, false, true), data.rows.map { it.studied })
    }

    @Test
    fun browseKanjiRowDescriptionIsActionOrientedAndConcise() {
        assertEquals(
            "Open details for 裂, split, レツ, 2 local sources · 1 example, SUSPENDED, not selected",
            browseKanjiRowDescription(
                kanji = "裂",
                meaning = "split",
                readings = "レツ",
                summary = "2 local sources · 1 example",
                studied = false,
                suspended = true,
            )
        )
    }

    @Test
    fun benchmarksSinglePassBrowseSelectionStateAgainstLegacyTwoPassPath() {
        val items = List(512) { index ->
            inventoryItem(
                kanji = "字$index",
                suspended = index % 3 == 0,
            )
        }
        val iterations = 10_000

        var legacyChecksum = 0
        val legacyNanos = measureNanoTime {
            repeat(iterations) {
                val data = legacyBrowseScreenData(items)
                legacyChecksum += data.rows.size + data.kanjiList.size + data.studiedCount
            }
        }

        var singlePassChecksum = 0
        val singlePassNanos = measureNanoTime {
            repeat(iterations) {
                val data = buildBrowseScreenData(items) { item -> browseRow(item) }
                singlePassChecksum += data.rows.size + data.kanjiList.size + data.studiedCount
            }
        }

        assertEquals(legacyChecksum, singlePassChecksum)
        println(
            String.format(
                Locale.ROOT,
                "browse-screen-data legacy_ms=%.3f legacy_avg_us=%.3f single_pass_ms=%.3f single_pass_avg_us=%.3f",
                legacyNanos / 1_000_000.0,
                legacyNanos / iterations.toDouble() / 1_000.0,
                singlePassNanos / 1_000_000.0,
                singlePassNanos / iterations.toDouble() / 1_000.0,
            ),
        )
    }

    private fun legacyBrowseScreenData(
        items: List<RecordsImportModels.KanjiInventoryItem>,
    ): BrowseScreenData {
        val rows = items.map { item -> browseRow(item) }
        return BrowseScreenData(
            rows = rows,
            kanjiList = items.map { item -> item.kanji },
            studiedCount = rows.count { it.studied },
        )
    }

    private fun browseRow(item: RecordsImportModels.KanjiInventoryItem): BrowseKanjiRowModel {
        val meaning = item.primaryMeaning
        val readings = item.readings
        return BrowseKanjiRowModel(
            kanji = item.kanji,
            meaning = meaning,
            readings = readings,
            summary = "summary-${item.kanji}",
            contentDescription = "${item.kanji}, $meaning, $readings",
            suspended = item.suspended,
            studied = !item.suspended,
            onClick = {},
        )
    }

    private fun inventoryItem(kanji: String, suspended: Boolean): RecordsImportModels.KanjiInventoryItem {
        return RecordsImportModels.KanjiInventoryItem(
            kanji,
            "meaning-$kanji",
            "reading-$kanji",
            "search-$kanji",
            1,
            1,
            suspended,
            1_000L,
        )
    }
}
