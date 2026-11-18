package dev.bee.kanjianki.data

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.AdaptiveRouteState
import dev.bee.kanjianki.core.AdaptiveRouteStateCodec
import dev.bee.kanjianki.core.AdaptiveStudyItemPolicy
import dev.bee.kanjianki.core.AppliedReviewSnapshot
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.CoreSkill
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RwkvHistoryBackfill
import dev.bee.kanjianki.core.RwkvStreamKeys
import dev.bee.kanjianki.core.StudyLadderRules
import dev.bee.kanjianki.core.StudyTaskTypes
import dev.bee.kanjianki.memory.RwkvReviewBinding
import dev.bee.kanjianki.memory.RwkvRuntime
import dev.bee.kanjianki.memory.RwkvStateRebuilder
import dev.bee.rwkv.RwkvAlgorithmInfo
import dev.bee.rwkv.RwkvCollectionState
import dev.bee.rwkv.RwkvEngine
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RwkvStateStoreTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore
    private lateinit var engine: RwkvEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        store = LocalStore(context)
        RwkvRuntime.resetForTests()
        engine = RwkvRuntime.engine(context)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        RwkvRuntime.resetForTests()
    }

    @Test
    fun aFreshDatabaseHasACompleteEmptyState() {
        assertTrue(store.rwkvStateIsReady())
        val meta = store.rwkvMeta()
        assertEquals(RwkvAlgorithmInfo.stateIdentity(), meta.identity)
        assertEquals(0L, meta.stateVersion)
        assertFalse(meta.needsRebuild)
        assertTrue(RwkvRuntime.readyNow(context, store))
    }

    @Test
    fun aReviewPersistsItsModelInputsAndStateInItsTransaction() {
        val item = persist(legacyReviewItem("痛"))
        val applied = answer(item, "good", NOW, "t1")
        assertTrue(applied.commit.applied())

        val row = reviewRow("t1")
        val ids = RwkvStreamKeys.idsFor("痛", CoreSkill.RECOGNITION, item.answerSignature)
        assertEquals(1L, row.getAsLong(RwkvStateStore.COLUMN_MM_SEQ))
        assertEquals(ids.card, row.getAsLong(RwkvStateStore.COLUMN_MM_CARD))
        assertEquals(3, row.getAsInteger(RwkvStateStore.COLUMN_MM_RATING))
        assertEquals(4_000L, row.getAsLong(RwkvStateStore.COLUMN_MM_DURATION))
        assertEquals(RwkvRuntime.dayOffsetOf(NOW), row.getAsLong(RwkvStateStore.COLUMN_MM_DAY))

        val meta = store.rwkvMeta()
        assertEquals(1L, meta.stateVersion)
        assertEquals(2L, meta.nextSeq)
        val state = store.loadRwkvCollection()
        assertNotNull(state.histories[ids.card])
        assertEquals(1, count(RwkvStateStore.TABLE_JOURNAL))
        // The stored item carries the model's S90 as its stability.
        val persisted = store.studyItemsForKanji(listOf("痛")).single()
        assertTrue(persisted.stability > 0.0 && persisted.dueAtMillis > NOW)
    }

    @Test
    fun undoingTheLatestReviewRestoresTheStateExactly() {
        val first = persist(legacyReviewItem("痛"))
        val afterFirst = answer(first, "good", NOW, "t1").persisted
        val stateAfterFirst = store.loadRwkvCollection()

        val second = answer(afterFirst, "again", NOW + 20 * DAY, "t2")
        assertTrue(store.undoLastAppliedReview(AppliedReviewSnapshot("t2", afterFirst, second.persisted)))

        assertStatesEqual(stateAfterFirst, store.loadRwkvCollection())
        assertFalse(store.rwkvMeta().needsRebuild)
        assertTrue(store.rwkvStateIsReady())
        assertEquals(1, count(RwkvStateStore.TABLE_JOURNAL))
    }

    @Test
    fun undoingAnOlderReviewFallsBackToARebuildFromHistory() {
        val firstItem = persist(legacyReviewItem("痛"))
        val other = persist(legacyReviewItem("酷"))
        val first = answer(firstItem, "good", NOW, "t1")
        answer(other, "good", NOW + 60_000L, "t2")

        assertTrue(store.undoLastAppliedReview(AppliedReviewSnapshot("t1", firstItem, first.persisted)))
        assertTrue(store.rwkvMeta().needsRebuild)
        assertFalse(store.rwkvStateIsReady())

        RwkvStateRebuilder(store, engine).rebuild { _, _ -> }
        assertTrue(store.rwkvStateIsReady())
        // Only the remaining review is in the rebuilt state.
        val expected = RwkvCollectionState()
        store.forEachRwkvHistoryEvent { event ->
            val review = event as RwkvStateStore.HistoryEvent.Review
            engine.replay(expected, review.ids, review.answer)
        }
        assertEquals(1, expected.histories.size)
        assertStatesEqual(expected, store.loadRwkvCollection())
    }

    @Test
    fun aSnapshotLoadedBeforeAnotherWriteCannotCommit() {
        val a = persist(legacyReviewItem("痛"))
        val b = persist(legacyReviewItem("酷"))
        val bindingA = bind(a, NOW)
        val bindingB = bind(b, NOW)
        assertTrue(commit(a, bindingA, "good", NOW, "ta").commit.applied())
        val stale = commit(b, bindingB, "good", NOW, "tb")
        assertEquals(ReviewCommitDisposition.STALE, stale.commit.disposition)
        assertEquals(0, count(LocalStoreBase.TABLE_REVIEW_LOG, "token='tb'"))
        assertEquals(1L, store.rwkvMeta().stateVersion)
    }

    @Test
    fun anEvidenceSeedIsWarmedOnItsFirstAnswerAndUndoRemovesTheBurst() {
        val seeded = persist(seededContextualItem("脱", stability = 60.0))
        val applied = answer(seeded, "good", NOW, "seed", StudyTaskTypes.WORD_READING)
        assertTrue(applied.commit.applied())
        assertEquals(1, count(RwkvStateStore.TABLE_BURST))
        assertEquals(2L, reviewRow("seed").getAsLong(RwkvStateStore.COLUMN_MM_SEQ))
        val ids = RwkvStreamKeys.idsFor("脱", CoreSkill.CONTEXTUAL_READING, seeded.answerSignature)
        val history = store.loadRwkvCollection().histories.getValue(ids.card)
        assertTrue("burst plus the answer", history.answers > 2)
        // A warmed mature seed schedules weeks out, not like a brand-new card.
        val due = store.studyItemsForKanji(listOf("脱")).single().dueAtMillis
        assertTrue("due ${(due - NOW) / DAY} days out", due - NOW > 14 * DAY)

        assertTrue(store.undoLastAppliedReview(AppliedReviewSnapshot("seed", seeded, applied.persisted)))
        assertEquals(0, count(RwkvStateStore.TABLE_BURST))
        assertTrue(store.loadRwkvCollection().histories.isEmpty())
    }

    @Test
    fun aRebuildClassifiesPreMigrationHistoryAndReplaysItDeterministically() {
        val learning = legacyJson("new_learning", "kanji_meaning")
        val review = legacyJson("review", "kanji_meaning")
        val repairRoute = AdaptiveRouteStateCodec.encode(
            AdaptiveRouteState(activeCore = CoreSkill.RECOGNITION, activeRepairTasks = listOf(StudyTaskTypes.MEANING_KANJI)),
        )
        insertHistoricalRow("h1", "痛", "good", NOW - 30 * DAY, StudyTaskTypes.KANJI_MEANING, learning)
        insertHistoricalRow("h2", "痛", "good", NOW - 29 * DAY, StudyTaskTypes.KANJI_MEANING, review)
        insertHistoricalRow(
            "h3", "痛", "good", NOW - 28 * DAY, StudyTaskTypes.MEANING_KANJI,
            "{\"phase\":\"relearning\",\"routing_version\":2,\"adaptive_route_state_json\":${org.json.JSONObject.quote(repairRoute)}}",
        )
        insertHistoricalRow("h4", "酷", "again", NOW - 27 * DAY, StudyTaskTypes.WORD_READING, legacyJson("review", "word_reading"))
        store.writableDatabase.update(
            RwkvStateStore.TABLE_META,
            ContentValues().apply { put("value", "1") },
            "key=?",
            arrayOf(RwkvStateStore.META_NEEDS_REBUILD),
        )
        assertEquals(4, store.rwkvUnclassifiedRowCount())

        RwkvStateRebuilder(store, engine).rebuild { _, _ -> }

        assertTrue(store.rwkvStateIsReady())
        assertEquals(0L, reviewRow("h3").getAsLong(RwkvStateStore.COLUMN_MM_SEQ))
        val seqs = listOf("h1", "h2", "h4").map { reviewRow(it).getAsLong(RwkvStateStore.COLUMN_MM_SEQ) }
        assertTrue(seqs.all { it > 0 } && seqs == seqs.sorted())
        assertEquals(3, store.rwkvHistoryEventCount())

        // Replaying the classified rows independently gives the same state.
        val expected = RwkvCollectionState()
        val backfill = RwkvHistoryBackfill()
        for (token in listOf("h1", "h2", "h4")) {
            val row = reviewRow(token)
            val fed = backfill.classify(
                RwkvHistoryBackfill.Row(
                    kanji = row.getAsString("kanji"),
                    answerSignature = "",
                    taskType = row.getAsString("task_type"),
                    rating = row.getAsString("rating"),
                    reviewedAtMillis = row.getAsLong("reviewed_at"),
                    reviewDayStartMillis = row.getAsLong("review_day_start"),
                    coreSkill = "",
                    schedulerStateBeforeJson = row.getAsString("scheduler_state_before_json"),
                    memoryBefore = "",
                    activeElapsedMillis = null,
                ),
            )!!
            engine.replay(expected, fed.ids, fed.answer)
        }
        assertStatesEqual(expected, store.loadRwkvCollection())

        // A second rebuild from the classified history is a no-op on the state.
        store.markRwkvStateForRebuild()
        RwkvStateRebuilder(store, engine).rebuild { _, _ -> }
        assertStatesEqual(expected, store.loadRwkvCollection())
    }

    // --- fixtures -----------------------------------------------------------------

    private class Applied(val commit: ReviewCommitResult, val persisted: RecordsStudyModels.StudyItem)

    private fun bind(item: RecordsStudyModels.StudyItem, now: Long): RwkvReviewBinding =
        RwkvReviewBinding.forItem(engine, store, item, now, 4_000L, RwkvRuntime::dayOffsetOf)

    private fun answer(
        item: RecordsStudyModels.StudyItem,
        rating: String,
        now: Long,
        token: String,
        taskType: String = StudyTaskTypes.KANJI_MEANING,
    ): Applied = commit(item, bind(item, now), rating, now, token, taskType)

    private fun commit(
        item: RecordsStudyModels.StudyItem,
        binding: RwkvReviewBinding,
        rating: String,
        now: Long,
        token: String,
        taskType: String = StudyTaskTypes.KANJI_MEANING,
    ): Applied {
        val request = RecordsSchedulerModels.ReviewRequest(
            item.kanji, token, rating, false, false, false, false, 0, taskType, item.answerSignature, "",
        )
        val result = BridgeScheduler.withMemoryModel(binding.model).applyReview(
            BridgeScheduler.ReviewApplication.builder(item.withToken(token), request, HashSet(), now).build(),
        )
        val commit = store.commitReview(
            ReviewCommitCommand(
                afterReview = result.item,
                request = request,
                appliedRating = result.appliedRating,
                reviewedAtMillis = now,
                beforeReview = item,
                memory = binding.commit(),
            ),
        )
        return Applied(commit, commit.item ?: item)
    }

    private fun persist(item: RecordsStudyModels.StudyItem): RecordsStudyModels.StudyItem {
        store.saveStudyItem(item)
        return store.studyItemsForKanji(listOf(item.kanji)).single()
    }

    private fun legacyReviewItem(kanji: String): RecordsStudyModels.StudyItem =
        RecordsStudyModels.StudyItem(kanji, StudyLadderRules.STATE_REVIEW, NOW - 1L, 3.0, 5.0, 3, 0, 0, 0, 0, 0, 0L, false, null, 3)
            .copyBuilder()
            .answerSignature("$kanji|$kanji|reading|meaning")
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .build()

    private fun seededContextualItem(kanji: String, stability: Double): RecordsStudyModels.StudyItem {
        val memory = RecordsStudyModels.TaskMemory(StudyLadderRules.STATE_REVIEW, NOW - 1L, stability, 4.0, 0, 0, 0, "", 0)
        return RecordsStudyModels.StudyItem(kanji, StudyLadderRules.STATE_REVIEW, NOW - 1L, stability, 4.0, 0, 0, 0, 0, 0, 0, 0L, false, null, 0)
            .copyBuilder()
            .answerSignature("$kanji|脱出|だっしゅつ|escape")
            .rung(RecordsBase.LadderRung.WORD_READING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .routingVersion(AdaptiveStudyItemPolicy.ROUTING_VERSION)
            .adaptiveRouteStateJson(AdaptiveRouteStateCodec.encode(AdaptiveRouteState(activeCore = CoreSkill.CONTEXTUAL_READING)))
            .build()
            .withTaskMemory(StudyTaskTypes.WORD_READING, memory)
    }

    private fun legacyJson(phase: String, rung: String): String =
        "{\"phase\":\"$phase\",\"rung\":\"$rung\",\"routing_version\":1,\"adaptive_route_state_json\":\"\"}"

    private fun insertHistoricalRow(token: String, kanji: String, rating: String, at: Long, taskType: String, before: String) {
        val values = ContentValues().apply {
            put("kanji", kanji)
            put("token", token)
            put("rating", rating)
            put("writing_required", 0)
            put("writing_passed", 0)
            put("manual_override", 0)
            put("reviewed_at", at)
            put("review_day_start", 0)
            put("task_type", taskType)
            put("scheduler_state_before_json", before)
            putNull(RwkvStateStore.COLUMN_MM_SEQ)
        }
        store.writableDatabase.insertOrThrow(LocalStoreBase.TABLE_REVIEW_LOG, null, values)
    }

    private fun reviewRow(token: String): ContentValues {
        store.readableDatabase.rawQuery("SELECT * FROM ${LocalStoreBase.TABLE_REVIEW_LOG} WHERE token=?", arrayOf(token)).use { cursor ->
            assertTrue("missing review $token", cursor.moveToFirst())
            val values = ContentValues()
            android.database.DatabaseUtils.cursorRowToContentValues(cursor, values)
            return values
        }
    }

    private fun count(table: String, where: String? = null): Int =
        store.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM $table" + (where?.let { " WHERE $it" } ?: ""),
            null,
        ).use { if (it.moveToFirst()) it.getInt(0) else 0 }

    private fun assertStatesEqual(expected: RwkvCollectionState, actual: RwkvCollectionState) {
        assertArrayEquals(expected.globals.encode(), actual.globals.encode())
        for (stream in expected.streams.indices) {
            assertEquals("stream $stream ids", expected.streams[stream].keys, actual.streams[stream].keys)
            for ((id, state) in expected.streams[stream]) {
                val precision = RwkvStateStore.defaultPrecision(stream)
                assertArrayEquals(
                    "stream $stream id $id",
                    dev.bee.rwkv.RwkvStateCodec.encode(state, precision),
                    dev.bee.rwkv.RwkvStateCodec.encode(actual.streams[stream].getValue(id), precision),
                )
            }
        }
        assertEquals(expected.cards, actual.cards)
        assertEquals(expected.encodings.keys, actual.encodings.keys)
        assertEquals(expected.histories.keys, actual.histories.keys)
        for ((id, history) in expected.histories) {
            assertArrayEquals(history.encode(), actual.histories.getValue(id).encode())
        }
        assertNull(actual.histories[-1L])
    }

    private companion object {
        const val DAY = 24L * 60L * 60L * 1000L
        const val NOW = 1_760_000_000_000L
    }
}
