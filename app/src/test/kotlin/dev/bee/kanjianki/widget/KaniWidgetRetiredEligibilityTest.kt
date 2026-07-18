package dev.bee.kanjianki.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreSchema
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class KaniWidgetRetiredEligibilityTest {
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
    fun studyAndFocusUseTheSameCanonicalActiveFamilySet() {
        LocalStore(context).use { store ->
            store.saveSuccessfulSync(
                RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
                emptyList(),
                listOf(row(ACTIVE), row(RETIRED), row(SUSPENDED)),
                RecordsSyncModels.Settings.kikuDefaults(),
                NOW - 2_000L,
                NOW - 1_000L,
                null,
            )
            store.saveStudyItem(item(ACTIVE, "review"))
            store.saveStudyItem(item(RETIRED, "retired"))
            store.saveStudyItem(item(SUSPENDED, "review"))
            store.saveStudyItem(item(MISSING_ROW, "review"))
            store.setKanjiLocallySuspended(SUSPENDED, true, NOW)
        }

        val study = StudyWidgetSnapshotLoader.load(context, NOW)
        var focusAllowed = emptySet<String>()
        FocusKanjiWidgetSnapshotLoader.load(
            context,
            NOW,
            FocusKanjiSelectionResolver { _, allowedKanji, _ ->
                focusAllowed = allowedKanji
                null
            },
        )

        assertEquals(KaniWidgetState.DUE_NOW, study.state)
        assertEquals(1, study.dueCount)
        assertEquals(setOf(ACTIVE), focusAllowed)
    }

    @Test
    fun unavailableWidgetCopyDoesNotMislabelMissingOrSuspendedDataAsRetired() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            val copies = listOf(
                widgetCopy(KaniWidgetSnapshot(KaniWidgetState.NOT_SET_UP), isExpanded = true),
                widgetCopy(KaniWidgetSnapshot(KaniWidgetState.ERROR), isExpanded = true),
                widgetCopy(KaniWidgetSnapshot(KaniWidgetState.NOTHING_DUE), isExpanded = true),
            )

            assertFalse(copies.flatMap { listOf(it.title, it.body, it.action, it.extraLine) }
                .any { it.contains("Retired by Anki support", ignoreCase = true) })
        } finally {
            Locale.setDefault(original)
        }
    }

    private fun row(kanji: String) = RecordsImportModels.DashboardRow(
        kanji,
        100,
        "meaning-$kanji",
        "reading-$kanji",
        kanji,
        0,
        "",
        "",
        0,
        0,
        0,
        emptyList<RecordsImportModels.Example>(),
    )

    private fun item(kanji: String, state: String): RecordsStudyModels.StudyItem =
        RecordsStudyModels.StudyItem(
            kanji,
            state,
            NOW - 1L,
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
        private const val ACTIVE = "裂"
        private const val RETIRED = "包"
        private const val SUSPENDED = "岩"
        private const val MISSING_ROW = "風"
    }
}
