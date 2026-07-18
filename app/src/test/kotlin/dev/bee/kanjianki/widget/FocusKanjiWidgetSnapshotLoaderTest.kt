package dev.bee.kanjianki.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreSchema
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FocusKanjiWidgetSnapshotLoaderTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        context.getDatabasePath(LocalStoreSchema.DB_NAME).deleteRecursively()
    }

    @Test
    fun validStoreWithoutResolvedEligibleContentReturnsHonestEmpty() {
        LocalStore(context).use { store -> store.writableDatabase }

        val snapshot = FocusKanjiWidgetSnapshotLoader.load(context, NOW)

        assertEquals(FocusKanjiWidgetState.EMPTY, snapshot.state)
        assertEquals("", snapshot.kanji)
        assertEquals("", snapshot.primaryMeaning)
        assertEquals("", snapshot.readings)
    }

    @Test
    fun resolverReceivesCommittedInventoryAndCanonicalStudyEligibleGlyphs() {
        LocalStore(context).use { store ->
            store.saveSuccessfulSync(
                RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
                emptyList(),
                listOf(dashboardRow("裂")),
                RecordsSyncModels.Settings.kikuDefaults(),
                NOW - 2_000L,
                NOW - 1_000L,
                null,
            )
            store.saveStudyItem(studyItem("裂", NOW - 1L))
        }
        var seenInventory = emptyList<String>()
        var seenAllowed = emptySet<String>()

        val snapshot = FocusKanjiWidgetSnapshotLoader.load(
            context,
            NOW,
            FocusKanjiSelectionResolver { inventory, allowedKanji, _ ->
                seenInventory = inventory.map { it.kanji }
                seenAllowed = allowedKanji
                FocusKanjiWidgetSelection("裂", "split", "れつ")
            },
        )

        assertEquals(listOf("裂"), seenInventory)
        assertEquals(setOf("裂"), seenAllowed)
        assertEquals(FocusKanjiWidgetState.READY, snapshot.state)
        assertEquals("裂", snapshot.kanji)
        assertEquals("split", snapshot.primaryMeaning)
        assertEquals("れつ", snapshot.readings)
        assertEquals(true, snapshot.isDueNow)
    }

    private fun dashboardRow(kanji: String) = RecordsImportModels.DashboardRow(
        kanji,
        100,
        "split",
        "れつ",
        kanji,
        0,
        "",
        "",
        0,
        0,
        0,
        emptyList<RecordsImportModels.Example>(),
    )

    private fun studyItem(kanji: String, dueAtMillis: Long): RecordsStudyModels.StudyItem =
        RecordsStudyModels.StudyItem(
            kanji,
            "review",
            dueAtMillis,
            1.0,
            5.0,
            1,
            0,
            0,
            0,
            "token-$kanji",
            NOW - 10_000L,
        ).copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .build()

    companion object {
        private const val NOW = 1_800_000_000_000L
    }
}
