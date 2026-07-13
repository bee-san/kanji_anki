package dev.bee.kanjianki.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyncCommitWindowTest {
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

    private fun emptySnapshot() = RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList())

    @Test
    fun pendingSyncBeforeStudyItemsCommitDoesNotCountAsSuccessfulSyncSince() {
        val finished = 2_000L
        // Transaction #1 commits as pending (simulates a crash before study items commit).
        store.saveSuccessfulSync(
            emptySnapshot(),
            emptyList(),
            emptyList(),
            RecordsSyncModels.Settings.kikuDefaults(),
            LocalStoreBase.SyncTiming(1_000L, finished),
            null,
            null,
            emptyList(),
            LocalStoreBase.STATUS_PENDING,
        )

        // Auto-sync gating must treat the lingering pending run as not-yet-successful,
        // so it retries instead of skipping for the rest of the day.
        assertFalse(store.hasSuccessfulSyncSince(finished))
    }

    @Test
    fun atomicQueueCommitPromotesPendingRunToSuccess() {
        val finished = 2_000L
        val syncId = store.saveSuccessfulSync(
            emptySnapshot(),
            emptyList(),
            emptyList(),
            RecordsSyncModels.Settings.kikuDefaults(),
            LocalStoreBase.SyncTiming(1_000L, finished),
            null,
            null,
            emptyList(),
            LocalStoreBase.STATUS_PENDING,
        )
        assertFalse(store.hasSuccessfulSyncSince(finished))

        val item = studyItem("痛", 3_000L)
        store.commitPendingSyncStudyItems(
            listOf(item),
            syncId,
            finished,
            RecordsSyncModels.Settings.kikuDefaults(),
            emptyList(),
        )

        assertTrue(store.hasSuccessfulSyncSince(finished))
        assertEquals(listOf("痛"), store.studyItemsForKanji(listOf("痛")).map { it.kanji })
    }

    @Test
    fun failedRunPromotionRollsBackQueueReplacement() {
        val original = studyItem("痛", 1_000L)
        store.replaceStudyItems(listOf(original))

        try {
            store.commitPendingSyncStudyItems(
                emptyList(),
                999_999L,
                2_000L,
                RecordsSyncModels.Settings.kikuDefaults(),
                null,
            )
            throw AssertionError("missing pending sync must fail")
        } catch (_: IllegalStateException) {
            // Expected: the status CAS is in the same transaction as the queue delete.
        }

        val persisted = store.studyItemsForKanji(listOf("痛"))
        assertEquals(1, persisted.size)
        assertEquals(1_000L, persisted.single().dueAtMillis)
    }

    @Test
    fun emptySnapshotPublicationRetainsDormantSchedulerAndReviewHistory() {
        val original = studyItem("痛", 1_000L).copyBuilder()
            .totalReviews(3)
            .schedulerRevision(7L)
            .build()
        store.replaceStudyItems(listOf(original))
        store.saveReview(
            RecordsSchedulerModels.ReviewRequest("痛", "retained-review", "good", false, false, false, 0),
            "good",
            1_500L,
        )
        val syncId = pendingSync(2_000L, 3_000L)

        store.commitPendingSyncStudyItems(
            emptyList(),
            syncId,
            3_000L,
            RecordsSyncModels.Settings.kikuDefaults(),
            emptyList(),
        )

        val retained = store.studyItems().single()
        assertEquals("痛", retained.kanji)
        assertEquals(3, retained.totalReviews)
        assertEquals(7L, retained.schedulerRevision)
        assertEquals(listOf("retained-review"), reviewTokens())
        assertTrue(store.hasSuccessfulSyncSince(3_000L))
    }

    @Test
    fun narrowedCommitPublicationUpdatesPresentKanjiAndRetainsOmittedKanji() {
        val present = studyItem("痛", 1_000L).copyBuilder().schedulerRevision(4L).build()
        val omitted = studyItem("裂", 2_000L).copyBuilder()
            .totalReviews(5)
            .schedulerRevision(9L)
            .build()
        store.replaceStudyItems(listOf(present, omitted))
        store.saveReview(
            RecordsSchedulerModels.ReviewRequest("裂", "omitted-review", "good", false, false, false, 0),
            "good",
            2_500L,
        )
        val syncId = pendingSync(3_000L, 4_000L)
        val reseededPresent = present.copyBuilder().dueAtMillis(5_000L).build()

        store.commitPendingSyncStudyItems(
            listOf(reseededPresent),
            syncId,
            4_000L,
            RecordsSyncModels.Settings.kikuDefaults(),
            listOf(present),
        )

        val persisted = store.studyItems().associateBy { it.kanji }
        assertEquals(setOf("痛", "裂"), persisted.keys)
        assertEquals(5_000L, persisted.getValue("痛").dueAtMillis)
        assertEquals(5L, persisted.getValue("痛").schedulerRevision)
        assertEquals(2_000L, persisted.getValue("裂").dueAtMillis)
        assertEquals(5, persisted.getValue("裂").totalReviews)
        assertEquals(9L, persisted.getValue("裂").schedulerRevision)
        assertEquals(listOf("omitted-review"), reviewTokens())
    }

    @Test
    fun materialSeedChangeAdvancesPersistedSchedulerRevision() {
        val baseline = studyItem("痛", 1_000L).copyBuilder().schedulerRevision(7L).build()
        store.replaceStudyItems(listOf(baseline))
        val syncId = pendingSync(2_000L, 3_000L)
        val seeded = baseline.copyBuilder().dueAtMillis(4_000L).build()

        store.commitPendingSyncStudyItems(
            listOf(seeded),
            syncId,
            3_000L,
            RecordsSyncModels.Settings.kikuDefaults(),
            listOf(baseline),
        )

        val persisted = store.studyItemsForKanji(listOf("痛")).single()
        assertEquals(4_000L, persisted.dueAtMillis)
        assertEquals(8L, persisted.schedulerRevision)
    }

    @Test
    fun midSyncReviewWinnerKeepsItsRevision() {
        val baseline = studyItem("痛", 1_000L).copyBuilder().schedulerRevision(7L).build()
        store.replaceStudyItems(listOf(baseline))
        val reviewed = baseline.copyBuilder()
            .dueAtMillis(9_000L)
            .totalReviews(2)
            .schedulerRevision(8L)
            .build()
        store.saveStudyItem(reviewed)
        val syncId = pendingSync(2_000L, 3_000L)
        val seeded = baseline.copyBuilder().dueAtMillis(4_000L).build()

        store.commitPendingSyncStudyItems(
            listOf(seeded),
            syncId,
            3_000L,
            RecordsSyncModels.Settings.kikuDefaults(),
            listOf(baseline),
        )

        val persisted = store.studyItemsForKanji(listOf("痛")).single()
        assertEquals(9_000L, persisted.dueAtMillis)
        assertEquals(2, persisted.totalReviews)
        assertEquals(8L, persisted.schedulerRevision)
    }

    @Test
    fun sameMeaningSignatureReshufflePreservesMidSyncReviewAndBumpsRevision() {
        val oldSignature = "痛|痛む|いたむ|pain"
        val newSignature = "痛|苦痛|くつう|pain"
        val baseline = studyItem("痛", 1_000L).copyBuilder()
            .answerSignature(oldSignature)
            .schedulerRevision(7L)
            .build()
        store.replaceStudyItems(listOf(baseline))
        val reviewed = baseline.copyBuilder()
            .dueAtMillis(9_000L)
            .totalReviews(2)
            .schedulerRevision(8L)
            .build()
        store.saveStudyItem(reviewed)
        val syncId = pendingSync(2_000L, 3_000L)
        val seeded = baseline.copyBuilder()
            .answerSignature(newSignature)
            .dueAtMillis(4_000L)
            .build()

        store.commitPendingSyncStudyItems(
            listOf(seeded),
            syncId,
            3_000L,
            RecordsSyncModels.Settings.kikuDefaults(),
            listOf(baseline),
        )

        val persisted = store.studyItemsForKanji(listOf("痛")).single()
        assertEquals(newSignature, persisted.answerSignature)
        assertEquals(9_000L, persisted.dueAtMillis)
        assertEquals(2, persisted.totalReviews)
        assertEquals(9L, persisted.schedulerRevision)
    }

    @Test
    fun sameMeaningSignatureChangeWithoutReviewStillBumpsRevision() {
        val baseline = studyItem("痛", 1_000L).copyBuilder()
            .answerSignature("痛|痛む|いたむ|pain")
            .schedulerRevision(7L)
            .build()
        store.replaceStudyItems(listOf(baseline))
        val syncId = pendingSync(2_000L, 3_000L)
        val seeded = baseline.copyBuilder()
            .answerSignature("痛|苦痛|くつう|pain")
            .build()

        store.commitPendingSyncStudyItems(
            listOf(seeded),
            syncId,
            3_000L,
            RecordsSyncModels.Settings.kikuDefaults(),
            listOf(baseline),
        )

        val persisted = store.studyItemsForKanji(listOf("痛")).single()
        assertEquals("痛|苦痛|くつう|pain", persisted.answerSignature)
        assertEquals(8L, persisted.schedulerRevision)
    }

    @Test
    fun successfulFinalizationPurgesOlderPendingAndOrphanSnapshots() {
        val oldPending = pendingSync(1_000L, 2_000L)
        val currentPending = pendingSync(3_000L, 4_000L)
        insertHistoricalRows(oldPending, "古")
        insertHistoricalRows(currentPending, "現")
        insertHistoricalRows(999_999L, "孤")

        store.commitPendingSyncStudyItems(
            emptyList(),
            currentPending,
            4_000L,
            RecordsSyncModels.Settings.kikuDefaults(),
            emptyList(),
        )

        for (table in listOf("sync_card_snapshots", "sync_note_snapshots", "sync_kanji_snapshots")) {
            val ids = mutableListOf<Long>()
            store.readableDatabase.rawQuery("SELECT DISTINCT sync_id FROM $table", null).use { cursor ->
                while (cursor.moveToNext()) ids.add(cursor.getLong(0))
            }
            assertEquals("unexpected rows in $table", listOf(currentPending), ids)
        }
    }

    @Test
    fun pendingTimelineIsHiddenAndCannotPoisonNextSuccessfulDedupeKey() {
        val oldPending = pendingSync(1_000L, 2_000L)
        insertTimeline(oldPending, "first_seen:痛")
        store.clearTimelineCache()
        assertTrue(store.timelineForKanji("痛").events.isEmpty())

        val currentPending = pendingSync(3_000L, 4_000L)
        val oldCount = store.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM kanji_timeline_events WHERE sync_id=?",
            arrayOf(oldPending.toString()),
        ).use { cursor ->
            cursor.moveToFirst()
            cursor.getInt(0)
        }
        assertEquals(0, oldCount)

        insertTimeline(currentPending, "first_seen:痛")
        store.commitPendingSyncStudyItems(
            emptyList(),
            currentPending,
            4_000L,
            RecordsSyncModels.Settings.kikuDefaults(),
            emptyList(),
        )

        assertEquals(
            listOf("first_seen:痛"),
            store.timelineForKanji("痛").events.map { it.dedupeKey },
        )
    }

    private fun pendingSync(startedAt: Long, finishedAt: Long): Long = store.saveSuccessfulSync(
        emptySnapshot(),
        emptyList(),
        emptyList(),
        RecordsSyncModels.Settings.kikuDefaults(),
        LocalStoreBase.SyncTiming(startedAt, finishedAt),
        null,
        null,
        emptyList(),
        LocalStoreBase.STATUS_PENDING,
    )

    private fun studyItem(kanji: String, dueAt: Long): RecordsStudyModels.StudyItem =
        RecordsStudyModels.StudyItem(kanji, "review", dueAt, 1.0, 5.0, 1, 0, 0, 0, null, 1L)

    private fun insertHistoricalRows(syncId: Long, kanji: String) {
        val db = store.writableDatabase
        db.execSQL(
            "INSERT INTO sync_card_snapshots " +
                "(sync_id, started_at, finished_at, card_id, note_id, deck_id, deck_name, model_id, model_name, ord, " +
                "queue, type, due, interval_days, reps, lapses, suspended, mature) " +
                "VALUES (?, 0, 1, ?, ?, '1', 'Deck', 0, 'Model', 0, 2, 2, 0, 1, 1, 0, 0, 0)",
            arrayOf<Any>(syncId, syncId, syncId),
        )
        db.execSQL(
            "INSERT INTO sync_note_snapshots " +
                "(sync_id, finished_at, note_id, model_id, model_name, deck_ids, deck_names, expression, reading, " +
                "meaning, sentence, tags, fields_json, extracted_kanji) " +
                "VALUES (?, 1, ?, 0, 'Model', '1', 'Deck', ?, '', '', '', '', '{}', ?)",
            arrayOf<Any>(syncId, syncId, kanji, kanji),
        )
        db.execSQL(
            "INSERT INTO sync_kanji_snapshots " +
                "(sync_id, finished_at, kanji, active_cards, suspended_cards, mature_support_count, average_interval_days, " +
                "total_lapses, total_reps, weakness_score, reason_code, active_example_count, suspended_example_count) " +
                "VALUES (?, 1, ?, 1, 0, 0, 1.0, 0, 1, 1, '', 1, 0)",
            arrayOf<Any>(syncId, kanji),
        )
    }

    private fun insertTimeline(syncId: Long, dedupeKey: String) {
        store.writableDatabase.execSQL(
            "INSERT INTO kanji_timeline_events " +
                "(kanji, occurred_at, event_type, title, detail, source_expression, source_reading, rating, " +
                "writing_required, writing_passed, manual_override, weakness_score, mature_support_count, sync_id, " +
                "dedupe_key) VALUES ('痛', 1, 'first_seen', '', '', '', '', '', 0, 0, 0, NULL, NULL, ?, ?)",
            arrayOf<Any>(syncId, dedupeKey),
        )
    }

    private fun reviewTokens(): List<String> {
        val tokens = mutableListOf<String>()
        store.readableDatabase.query(
            LocalStoreBase.TABLE_REVIEW_LOG,
            arrayOf(LocalStoreBase.COLUMN_TOKEN),
            null,
            null,
            null,
            null,
            "${LocalStoreBase.COLUMN_TOKEN} ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) tokens.add(cursor.getString(0))
        }
        return tokens
    }
}
