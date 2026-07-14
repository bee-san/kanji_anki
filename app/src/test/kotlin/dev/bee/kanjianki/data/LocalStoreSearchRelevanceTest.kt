package dev.bee.kanjianki.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Browse search relevance. `search_text` embeds the full expressions and sentences of
 * every note a kanji appears in, so a single-glyph search also matches every kanji that
 * co-occurred with it in a word or sentence. These tests pin that the searched kanji is
 * ranked first and survives the 300-row SQL cap instead of being buried or dropped.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalStoreSearchRelevanceTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        store = LocalStore(context)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    private fun row(
        kanji: String,
        meaning: String,
        expression: String,
        reasonText: String = "reason text",
    ): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji, 1, meaning, "reading", "browser", 5, "reason", reasonText, 1, 0, 0,
            listOf(
                RecordsImportModels.Example("word", 1L, 1L, expression, "reading", meaning, "", false, 0),
            ),
        )
    }

    private fun seed(rows: List<RecordsImportModels.DashboardRow>) {
        store.saveSuccessfulSync(
            RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
            emptyList(),
            rows,
            RecordsSyncModels.Settings.kikuDefaults(),
            1_000L,
            2_000L,
            null,
        )
    }

    @Test
    fun singleGlyphSearchRanksTheExactKanjiFirst() {
        // 当 (U+5F53) sorts before 担 (U+62C5) in code-point order, and its example
        // expression 担当 makes it LIKE-match a search for 担 — the exact kanji used to
        // be buried behind every co-occurring match.
        seed(listOf(row("当", "hit", "担当"), row("担", "carry", "担当")))

        val results = store.searchKanjiInventory("担")

        assertEquals(listOf("担", "当"), results.map { it.kanji })
    }

    @Test
    fun singleGlyphSearchRestoresExactKanjiCutByTheRowCap() {
        // 300 co-occurring kanji all sort before 担, so the SQL page cap used to drop
        // the searched kanji from its own results entirely.
        val rows = ArrayList<RecordsImportModels.DashboardRow>()
        for (offset in 0 until 300) {
            val glyph = String(Character.toChars(0x4E00 + offset))
            rows.add(row(glyph, "co-occurring", "担当"))
        }
        rows.add(row("担", "carry", "担当"))
        seed(rows)

        val results = store.searchKanjiInventory("担")

        assertEquals(301, results.size)
        assertEquals("担", results.first().kanji)
    }

    @Test
    fun wordSearchRanksItsOwnKanjiBeforeOtherMatches() {
        // Searching the word 混乱 promotes both of its kanji above an unrelated row
        // that merely mentions the word, keeping code-point order within each group.
        seed(
            listOf(
                row("乱", "chaos", "混乱"),
                row("学", "study", "学校", reasonText = "seen near 混乱"),
                row("混", "mix", "混乱"),
            ),
        )

        val results = store.searchKanjiInventory("混乱")

        assertEquals(listOf("乱", "混", "学"), results.map { it.kanji })
    }
}
