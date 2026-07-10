package dev.bee.kanjianki.data

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.StudyLadderRules
import dev.bee.kanjianki.core.TimelineCopy
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
class LocalStoreRepairedWriteBackTest {
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

    @Test
    fun retiredSourceIsProposedStampedAndSurfacedForHandoff() {
        seedSuspendedSource("徴", 10L, 1L)
        store.saveRows(store.writableDatabase, listOf(row("徴")), NOW)
        store.saveStudyItem(retiredItem("徴"))
        // Successful kani_archived notes are absent from later provider snapshots;
        // the durable suspended archive must still make this proposal possible.
        val snapshot = RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList())

        val proposal = store.repairedWriteBackProposal(snapshot, 2)
        val recorded = store.recordRepairedWriteBack(proposal, setOf(1L), NOW, 7L)

        assertEquals(setOf(1L), proposal.noteIdsToTag)
        assertEquals(listOf("徴"), recorded)
        assertEquals(listOf("徴"), store.pendingRepairedHandoffKanji())
        assertEquals(NOW, restoredAt(10L))
        assertEquals(1, timelineCount("徴", TimelineCopy.EVENT_REPAIR_TAGGED))

        store.dismissRepairedHandoff()
        assertTrue(store.pendingRepairedHandoffKanji().isEmpty())
        assertTrue(store.repairedWriteBackProposal(snapshot, 2).isEmpty())
    }

    @Test
    fun failedProviderNoteIsNotStamped() {
        seedSuspendedSource("徴", 10L, 1L)
        store.saveRows(store.writableDatabase, listOf(row("徴")), NOW)
        store.saveStudyItem(retiredItem("徴"))
        val proposal = store.repairedWriteBackProposal(
            RecordsSyncModels.CollectionSnapshot(emptyList(), listOf(card(10L, 1L, true))),
            2,
        )

        assertTrue(store.recordRepairedWriteBack(proposal, emptySet(), NOW, 7L).isEmpty())
        assertEquals(0L, restoredAt(10L))
        assertTrue(store.pendingRepairedHandoffKanji().isEmpty())
    }

    @Test
    fun fresherLiveActiveCardRejectsHistoricallySuspendedNote() {
        seedSuspendedSource("徴", 10L, 1L)
        store.saveRows(store.writableDatabase, listOf(row("徴")), NOW)
        store.saveStudyItem(retiredItem("徴"))

        val proposal = store.repairedWriteBackProposal(
            RecordsSyncModels.CollectionSnapshot(emptyList(), listOf(card(10L, 1L, false))),
            2,
        )

        assertTrue(proposal.isEmpty())
    }

    private fun seedSuspendedSource(kanji: String, cardId: Long, noteId: Long) {
        val db = store.writableDatabase
        db.insertOrThrow(
            LocalStoreBase.TABLE_SUSPENDED_SOURCES,
            null,
            ContentValues().apply {
                put("kanji", kanji)
                put("card_id", cardId)
                put("note_id", noteId)
                put("expression", kanji)
                put("reading", "ちょう")
                put("meaning", "sign")
                put("sentence", "")
                put("sync_id", 1L)
            },
        )
        db.insertOrThrow(
            LocalStoreBase.TABLE_SUSPENDED_ARCHIVE,
            null,
            ContentValues().apply {
                put("card_id", cardId)
                put("note_id", noteId)
                put("deck_name", "Mining")
                put("model_name", "Kiku")
                put("expression", kanji)
                put("reading", "ちょう")
                put("meaning", "sign")
                put("sentence", "")
                put("fields_json", "{}")
                put("archived_at", 1L)
                put("archived_sync_id", 1L)
            },
        )
    }

    private fun restoredAt(cardId: Long): Long = store.readableDatabase.rawQuery(
        "SELECT restored_at FROM suspended_archive WHERE card_id=?",
        arrayOf(cardId.toString()),
    ).use { cursor ->
        if (!cursor.moveToFirst() || cursor.isNull(0)) 0L else cursor.getLong(0)
    }

    private fun timelineCount(kanji: String, eventType: String): Int = store.readableDatabase.rawQuery(
        "SELECT COUNT(*) FROM kanji_timeline_events WHERE kanji=? AND event_type=?",
        arrayOf(kanji, eventType),
    ).use { cursor -> cursor.moveToFirst(); cursor.getInt(0) }

    private fun row(kanji: String) = RecordsImportModels.DashboardRow(
        kanji, 100, "sign", "ちょう", kanji, 0, "", "", 0, 1, 0,
        emptyList<RecordsImportModels.Example>(),
    )

    private fun retiredItem(kanji: String) = RecordsStudyModels.StudyItem(
        kanji, StudyLadderRules.STATE_RETIRED, NOW, 1.0, 5.0, 1, 0, 0, 0, "token", NOW,
    ).copyBuilder()
        .rung(RecordsBase.LadderRung.KANJI_MEANING)
        .phase(RecordsBase.SchedulerPhase.REVIEW)
        .build()

    private fun card(cardId: Long, noteId: Long, suspended: Boolean) = RecordsSyncModels.Card(
        cardId, noteId, 0, "Mining", if (suspended) -1 else 2, 0, 0, 0, 0, 0, suspended,
    )

    companion object {
        private const val NOW = 1_800_000_000_000L
    }
}
