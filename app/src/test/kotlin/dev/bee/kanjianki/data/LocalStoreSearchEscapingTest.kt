package dev.bee.kanjianki.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalStoreSearchEscapingTest {
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

    private fun row(kanji: String, meaning: String): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji, 1, meaning, "reading", "browser", 5, "reason", "reason text", 1, 0, 0,
            listOf<RecordsImportModels.Example>(),
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
    fun percentWildcardIsMatchedLiterallyNotAsMatchAll() {
        seed(
            listOf(
                row("率", "50% success"),
                row("学", "plain meaning"),
                row("犬", "another plain meaning"),
            ),
        )

        val results = store.searchKanjiInventory("%")

        // With escaping, "%" matches only the row whose text literally contains "%",
        // not every row (which is what an unescaped LIKE '%%%' would return).
        assertEquals(1, results.size)
        assertEquals("率", results.first().kanji)
    }

    @Test
    fun underscoreWildcardIsMatchedLiterally() {
        seed(
            listOf(
                row("率", "under_score term"),
                row("学", "abc"),
            ),
        )

        val results = store.searchKanjiInventory("_")

        assertTrue(results.all { it.kanji == "率" })
        assertEquals(1, results.size)
    }

    @Test
    fun ordinaryTermStillMatches() {
        seed(listOf(row("学", "plain meaning"), row("犬", "other")))

        val results = store.searchKanjiInventory("plain")

        assertEquals(1, results.size)
        assertEquals("学", results.first().kanji)
    }
}
