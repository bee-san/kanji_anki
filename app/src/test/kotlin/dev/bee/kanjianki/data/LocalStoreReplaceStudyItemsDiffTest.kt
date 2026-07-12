package dev.bee.kanjianki.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsStudyModels.StudyItem
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The per-review (syncId == null, no baseline) replaceStudyItems path writes a diff
 * (upsert changed rows, delete stale rows) instead of delete-all + reinsert. These
 * tests pin the observable semantics: the table always ends up exactly equal to the
 * requested list.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalStoreReplaceStudyItemsDiffTest {
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

    private fun item(kanji: String, totalReviews: Int, dueAt: Long): StudyItem {
        return StudyItem(kanji, "review", dueAt, 1.0, 5.0, totalReviews, 0, 0, 0, null, 1L)
    }

    @Test
    fun replaceUpdatesChangedRowsAddsNewRowsAndDeletesStaleRows() {
        store.replaceStudyItems(
            listOf(
                item("痛", totalReviews = 3, dueAt = 100L),
                item("裂", totalReviews = 1, dueAt = 200L),
            )
        )

        store.replaceStudyItems(
            listOf(
                item("痛", totalReviews = 4, dueAt = 900L),
                item("弱", totalReviews = 0, dueAt = 300L),
            )
        )

        val byKanji = store.studyItems().associateBy { it.kanji }
        assertEquals(setOf("痛", "弱"), byKanji.keys)
        assertEquals(4, byKanji.getValue("痛").totalReviews)
        assertEquals(900L, byKanji.getValue("痛").dueAtMillis)
        assertEquals(300L, byKanji.getValue("弱").dueAtMillis)
        assertNull(byKanji["裂"])
    }

    @Test
    fun replaceWithIdenticalItemsKeepsTableEqual() {
        val items = listOf(
            item("痛", totalReviews = 3, dueAt = 100L),
            item("裂", totalReviews = 1, dueAt = 200L),
        )
        store.replaceStudyItems(items)
        store.replaceStudyItems(items)

        val byKanji = store.studyItems().associateBy { it.kanji }
        assertEquals(setOf("痛", "裂"), byKanji.keys)
        assertEquals(3, byKanji.getValue("痛").totalReviews)
        assertEquals(1, byKanji.getValue("裂").totalReviews)
    }

    @Test
    fun replaceWithEmptyListClearsTable() {
        store.replaceStudyItems(listOf(item("痛", totalReviews = 3, dueAt = 100L)))
        store.replaceStudyItems(emptyList())

        assertEquals(0, store.studyItems().size)
    }

    @Test
    fun sameMeaningSignatureMoveAdvancesRevision() {
        val original = item("痛", totalReviews = 3, dueAt = 100L).copyBuilder()
            .answerSignature("痛|痛む|いたむ|pain")
            .schedulerRevision(7L)
            .build()
        store.replaceStudyItems(listOf(original))

        store.replaceStudyItems(
            listOf(
                original.copyBuilder()
                    .answerSignature("痛|苦痛|くつう|pain")
                    .build(),
            ),
        )

        val persisted = store.studyItems().single()
        assertEquals("痛|苦痛|くつう|pain", persisted.answerSignature)
        assertEquals(8L, persisted.schedulerRevision)
    }
}
