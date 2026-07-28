package dev.bee.kanjianki.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.syncapi.CollectionGateway
import dev.bee.kanjianki.application.ManualSyncQueuePlanner
import dev.bee.kanjianki.core.KanjiRepairEvidencePolicy
import dev.bee.kanjianki.core.MissingKanjiCandidate
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.StudyLadderRules
import dev.bee.kanjianki.core.StudyQueueSeeder
import dev.bee.kanjianki.core.TimelineCopy
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.data.LocalStoreSchema
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ManualSyncEngineReminderRescheduleTest {
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
    fun successfulSyncRearmsReminder() {
        val engine = createManualSyncEngine(context, store, EmptyGateway(), RecordsSyncModels.Settings.kikuDefaults())
        var rearms = 0
        engine.reminderRescheduler = Runnable { rearms++ }

        val result = engine.run()

        assertTrue(result.success)
        assertEquals(1, rearms)
    }

    @Test
    fun successfulSyncAdmitsDurableDictionarySourceWithoutAnkiEvidence() {
        store.missingKanjiStore().addManualSources(
            listOf(
                MissingKanjiCandidate(
                    literal = "水",
                    meanings = listOf("water"),
                    kunReadings = listOf("みず"),
                    jitenRank = 12,
                ),
            ),
            nowMillis = 100,
        )
        val engine = createManualSyncEngine(
            context,
            store,
            EmptyGateway(),
            RecordsSyncModels.Settings.kikuDefaults(),
        ).also {
            it.reminderRescheduler = Runnable { }
            it.widgetRefresher = Runnable { }
        }

        val result = engine.run()

        assertTrue(result.success)
        assertEquals("水", store.studyItems().single().kanji)
        assertEquals("水", store.activeDashboardRows().single().kanji)
    }

    @Test
    fun transientEmptyProviderSnapshotPreservesActiveSchedulerStateAndFailsRetryably() {
        val now = 1_725_000_000_000L
        val row = repairRow("痛")
        val memory = RecordsStudyModels.TaskMemory(
            StudyLadderRules.STATE_REVIEW,
            now + 86_400_000L,
            12.5,
            6.25,
            8,
            2,
            0,
            "good",
            30,
            4,
            now - 86_400_000L,
            now - 1_000L,
        )
        val original = reviewItem(row, now + 86_400_000L, 3, now - 86_400_000L)
            .copyBuilder()
            .wordReadingMemory(memory)
            .realPassStreak(4)
            .schedulerRevision(7L)
            .build()
        store.replaceStudyItems(listOf(original))
        store.saveReview(
            RecordsSchedulerModels.ReviewRequest("痛", "surviving-review", "good", false, false, false, 0),
            "good",
            now - 1_000L,
        )
        val engine = createManualSyncEngine(context, store, EmptyGateway(), RecordsSyncModels.Settings.kikuDefaults()).also {
            it.reminderRescheduler = Runnable { }
            it.widgetRefresher = Runnable { }
        }

        val result = engine.run()

        assertFalse(result.success)
        assertTrue(result.retryable)
        val preserved = store.studyItems().single()
        assertEquals("痛", preserved.kanji)
        assertEquals(StudyLadderRules.STATE_REVIEW, preserved.state)
        assertEquals(original.totalReviews, preserved.totalReviews)
        assertEquals(original.stability, preserved.stability, 0.0)
        assertEquals(original.rung, preserved.rung)
        assertEquals(original.realPassStreak, preserved.realPassStreak)
        assertEquals(memory.encode(), preserved.wordReadingMemory.encode())
        assertTrue(store.hasConsumedToken("surviving-review"))
    }

    @Test
    fun transientEmptySnapshotPreservesPriorMirrorWithoutStudyItems() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val prior = completeSnapshot("痛")
        store.saveSuccessfulSync(
            prior,
            emptyList<RecordsImportModels.SuspendedImport>(),
            emptyList<RecordsImportModels.DashboardRow>(),
            settings,
            1_000L,
            2_000L,
            null,
        )
        assertTrue(store.studyItems().isEmpty())
        assertTrue(store.hasPersistedCollectionMirror())

        val result = createManualSyncEngine(context, store, EmptyGateway(), settings).run()

        assertFalse(result.success)
        assertTrue(result.retryable)
        assertTrue(store.hasPersistedCollectionMirror())
    }

    @Test
    fun queueBuildFailureRollsBackMirrorDashboardInventoryAndStudyPublication() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val oldRow = repairRow("旧")
        val oldSnapshot = completeSnapshot("旧")
        val oldItem = reviewItem(oldRow, 9_000L, 6, 1_000L)
        store.saveSuccessfulSync(
            oldSnapshot,
            emptyList<RecordsImportModels.SuspendedImport>(),
            listOf(oldRow),
            settings,
            1_000L,
            2_000L,
            null,
        )
        store.replaceStudyItems(listOf(oldItem))
        val progress = SyncProgress.Listener {
            if (it.stage == SyncProgress.Stage.BUILDING_PRACTICE_QUEUE) {
                throw IllegalStateException("injected queue-build failure")
            }
        }

        val result = createManualSyncEngine(
            context,
            store,
            SnapshotGateway(suspendedSnapshot("新")),
            settings,
            progress,
        ).run()

        assertFalse(result.success)
        assertEquals(listOf("旧"), store.dashboardRows().map { it.kanji })
        assertEquals("旧", store.inventoryItemForKanji("旧")?.kanji)
        assertEquals(null, store.inventoryItemForKanji("新"))
        assertEquals(listOf("旧"), sourceNoteExpressions())
        val preserved = store.studyItems().single()
        assertEquals("旧", preserved.kanji)
        assertEquals(oldItem.totalReviews, preserved.totalReviews)
        assertEquals(listOf("success", "retryable_error"), syncStatuses())
    }

    @Test
    fun failureAfterQueueFinalizationRollsBackTheWholeOuterPublication() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val oldRow = repairRow("旧")
        val oldItem = reviewItem(oldRow, 9_000L, 6, 1_000L)
        store.saveSuccessfulSync(
            completeSnapshot("旧"),
            emptyList<RecordsImportModels.SuspendedImport>(),
            listOf(oldRow),
            settings,
            1_000L,
            2_000L,
            null,
        )
        store.replaceStudyItems(listOf(oldItem))
        val newRow = repairRow("新")
        val newItem = reviewItem(newRow, 10_000L, 2, 2_000L)

        try {
            store.publishSyncAtomically {
                val syncId = store.saveSuccessfulSync(
                    suspendedSnapshot("新"),
                    emptyList<RecordsImportModels.SuspendedImport>(),
                    listOf(newRow),
                    settings,
                    LocalStoreBase.SyncTiming(3_000L, 4_000L),
                    null,
                    null,
                    emptyList<RecordsImportModels.SuspendedImport>(),
                    LocalStoreBase.STATUS_PENDING,
                    null,
                )
                store.commitPendingSyncStudyItems(listOf(newItem), syncId, 4_000L, settings, listOf(oldItem))
                throw IllegalStateException("injected post-finalization failure")
            }
        } catch (expected: IllegalStateException) {
            assertEquals("injected post-finalization failure", expected.message)
        }

        assertEquals(listOf("旧"), store.dashboardRows().map { it.kanji })
        assertEquals(listOf("旧"), sourceNoteExpressions())
        assertEquals("旧", store.studyItems().single().kanji)
        assertEquals(listOf("success"), syncStatuses())
    }

    @Test
    fun notesOnlySnapshotCannotReplacePriorCompleteMirror() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val prior = completeSnapshot("痛")
        store.saveSuccessfulSync(
            prior,
            emptyList<RecordsImportModels.SuspendedImport>(),
            emptyList<RecordsImportModels.DashboardRow>(),
            settings,
            1_000L,
            2_000L,
            null,
        )
        val notesOnly = RecordsSyncModels.CollectionSnapshot(prior.notes, emptyList())

        val result = createManualSyncEngine(context, store, SnapshotGateway(notesOnly), settings).run()

        assertFalse(result.success)
        assertTrue(result.retryable)
        assertTrue(store.hasPersistedCollectionMirror())
    }

    @Test
    fun nonEmptyProviderSnapshotRetiresOmittedItemWithoutDeletingSchedulerOrHistory() {
        val now = 1_725_000_000_000L
        val representedMemory = RecordsStudyModels.TaskMemory(
            StudyLadderRules.STATE_REVIEW,
            now + 86_400_000L,
            12.5,
            6.25,
            8,
            2,
            0,
            "good",
            30,
            4,
            now - 86_400_000L,
            now - 1_000L,
        )
        val omittedMemory = RecordsStudyModels.TaskMemory(
            StudyLadderRules.STATE_REVIEW,
            now + 172_800_000L,
            18.0,
            5.75,
            11,
            3,
            0,
            "hard",
            45,
            2,
            now - 172_800_000L,
            now - 2_000L,
        )
        val represented = reviewItem(repairRow("痛"), now + 86_400_000L, 8, now - 86_400_000L)
            .copyBuilder()
            .wordReadingMemory(representedMemory)
            .realPassStreak(4)
            .schedulerRevision(7L)
            .build()
        val omitted = reviewItem(repairRow("謎"), now + 172_800_000L, 11, now - 172_800_000L)
            .copyBuilder()
            .wordReadingMemory(omittedMemory)
            .realPassStreak(2)
            .schedulerRevision(12L)
            .build()
        store.replaceStudyItems(listOf(represented, omitted))
        store.saveReview(
            RecordsSchedulerModels.ReviewRequest("痛", "represented-review", "good", false, false, false, 0),
            "good",
            now - 1_000L,
        )
        store.saveReview(
            RecordsSchedulerModels.ReviewRequest("謎", "omitted-review", "hard", false, false, false, 0),
            "hard",
            now - 2_000L,
        )
        val engine = createManualSyncEngine(
            context,
            store,
            SnapshotGateway(suspendedSnapshot("痛")),
            RecordsSyncModels.Settings.kikuDefaults(),
            SyncProgress.NONE,
            dev.bee.kanjianki.time.AppClock { now },
        ).also {
            it.reminderRescheduler = Runnable { }
            it.widgetRefresher = Runnable { }
        }

        val result = engine.run()

        assertTrue(result.success)
        assertEquals(1, result.dashboardRows)
        assertEquals(1, result.importedSuspendedKanji)
        val committed = store.studyItems().associateBy { it.kanji }
        assertEquals(setOf("痛", "謎"), committed.keys)

        val retained = committed.getValue("痛")
        assertEquals(StudyLadderRules.STATE_REVIEW, retained.state)
        assertEquals(represented.totalReviews, retained.totalReviews)
        assertEquals(represented.stability, retained.stability, 0.0)
        assertEquals(represented.rung, retained.rung)
        assertEquals(represented.realPassStreak, retained.realPassStreak)
        assertEquals(representedMemory.encode(), retained.wordReadingMemory.encode())

        val retired = committed.getValue("謎")
        assertEquals(StudyLadderRules.STATE_RETIRED, retired.state)
        assertEquals(omitted.totalReviews, retired.totalReviews)
        assertEquals(omitted.stability, retired.stability, 0.0)
        assertEquals(omitted.rung, retired.rung)
        assertEquals(omitted.realPassStreak, retired.realPassStreak)
        assertEquals(omittedMemory.encode(), retired.wordReadingMemory.encode())
        assertEquals(omitted.schedulerRevision + 1L, retired.schedulerRevision)
        assertTrue(store.hasConsumedToken("represented-review"))
        assertTrue(store.hasConsumedToken("omitted-review"))
    }

    @Test
    fun archivedSuspendedImportRemainsAnalyzableWhenProviderHidesTaggedNote() {
        val now = 1_725_000_000_000L
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val firstEngine = createManualSyncEngine(
            context,
            store,
            SnapshotGateway(suspendedSnapshot("痛")),
            settings,
            SyncProgress.NONE,
            dev.bee.kanjianki.time.AppClock { now },
        ).also {
            it.reminderRescheduler = Runnable { }
            it.widgetRefresher = Runnable { }
        }
        assertTrue(firstEngine.run().success)
        assertEquals("痛", store.suspendedImports().single().kanji)
        val painRow = store.dashboardRows().single { it.kanji == "痛" }
        val original = reviewItem(painRow, now + 86_400_000L, 8, now - 86_400_000L)
        store.replaceStudyItems(listOf(original))
        val currentProvider = completeSnapshot("別", noteId = 2L, cardId = 20L)
        assertTrue(currentProvider.cards.none { it.cardId == 10L })
        val secondEngine = createManualSyncEngine(
            context,
            store,
            SnapshotGateway(currentProvider),
            settings,
            SyncProgress.NONE,
            dev.bee.kanjianki.time.AppClock { now + 1_000L },
        ).also {
            it.reminderRescheduler = Runnable { }
            it.widgetRefresher = Runnable { }
        }

        assertTrue(secondEngine.run().success)
        assertTrue(store.dashboardRows().any { it.kanji == "痛" })
        val preserved = store.studyItems().single { it.kanji == "痛" }
        assertEquals(StudyLadderRules.STATE_REVIEW, preserved.state)
        assertEquals(original.totalReviews, preserved.totalReviews)
    }

    @Test
    fun localBrowseSuspensionDoesNotRetireSchedulerStateOrTimeline() {
        val now = 1_725_000_000_000L
        val row = repairRow("痛")
        val original = reviewItem(row, now + 86_400_000L, 8, now - 86_400_000L)
            .copyBuilder()
            .schedulerRevision(7L)
            .build()
        store.replaceStudyItems(listOf(original))
        store.setKanjiLocallySuspended("痛", true, now - 1_000L)
        val engine = createManualSyncEngine(
            context,
            store,
            SnapshotGateway(suspendedSnapshot("痛")),
            RecordsSyncModels.Settings.kikuDefaults(),
            SyncProgress.NONE,
            dev.bee.kanjianki.time.AppClock { now },
        ).also {
            it.reminderRescheduler = Runnable { }
            it.widgetRefresher = Runnable { }
        }

        val result = engine.run()

        assertTrue(result.success)
        assertEquals(listOf("痛"), store.dashboardRows().map { it.kanji })
        assertTrue(store.activeDashboardRows().isEmpty())
        val preserved = store.studyItems().single()
        assertEquals(StudyLadderRules.STATE_REVIEW, preserved.state)
        assertEquals(original.totalReviews, preserved.totalReviews)
        val timeline = store.timelineForKanji("痛")
        assertFalse(timeline.events.any { it.eventType == StudyLadderRules.STATE_RETIRED })
        assertFalse(TimelineCopy.statusText(timeline, now).contains("Retired"))
    }

    @Test
    fun localBrowseSuspensionDoesNotRetireRegressingMatureProviderRow() {
        val now = 1_725_000_000_000L
        val row = repairRow("痛")
        val original = reviewItem(row, now + 86_400_000L, 8, now - 86_400_000L)
        store.replaceStudyItems(listOf(original))
        insertEvidenceSnapshot(90L, now - 10_000L, "痛", weakness = 0, mature = 2)
        listOf("good", "hard", "again").forEachIndexed { index, rating ->
            store.saveReview(
                RecordsSchedulerModels.ReviewRequest(
                    "痛",
                    "regressing-review-$index",
                    rating,
                    false,
                    false,
                    false,
                    0,
                ),
                rating,
                now - 9_000L + index * 1_000L,
            )
        }
        store.setKanjiLocallySuspended("痛", true, now - 1_000L)
        val engine = createManualSyncEngine(
            context,
            store,
            SnapshotGateway(matureProviderSnapshot("痛")),
            RecordsSyncModels.Settings.kikuDefaults(),
            SyncProgress.NONE,
            dev.bee.kanjianki.time.AppClock { now },
        ).also {
            it.reminderRescheduler = Runnable { }
            it.widgetRefresher = Runnable { }
        }

        val result = engine.run()

        assertTrue(result.success)
        val currentRow = store.dashboardRows().single()
        assertEquals(2, currentRow.matureSupportCount)
        assertEquals(
            KanjiRepairEvidencePolicy.Status.REGRESSING,
            ManualSyncQueuePlanner.repairEvidenceStatusByKanji(
                listOf(currentRow),
                store.kanjiRepairEvidenceInputs(),
                now,
            )["痛"],
        )
        assertEquals(StudyLadderRules.STATE_REVIEW, store.studyItems().single().state)
        assertFalse(store.timelineForKanji("痛").events.any { it.eventType == StudyLadderRules.STATE_RETIRED })
    }

    @Test
    fun failedSyncDoesNotRearmReminder() {
        val engine = createManualSyncEngine(
            context,
            store,
            ThrowingGateway(AnkiDroidGateway.SyncFailure.retryable("try later")),
            RecordsSyncModels.Settings.kikuDefaults(),
        )
        var rearms = 0
        engine.reminderRescheduler = Runnable { rearms++ }

        val result = engine.run()

        assertFalse(result.success)
        assertEquals(0, rearms)
    }

    @Test
    fun postCommitSideEffectAndSummaryFailuresRetainSingleSuccessfulSync() {
        val engine = createManualSyncEngine(context, store, EmptyGateway(), RecordsSyncModels.Settings.kikuDefaults())
        var reminderCalls = 0
        var widgetCalls = 0
        var summaryCalls = 0
        engine.reminderRescheduler = Runnable {
            reminderCalls++
            throw IllegalStateException("private reminder failure")
        }
        engine.widgetRefresher = Runnable {
            widgetCalls++
            throw IllegalStateException("private widget failure")
        }
        engine.removalMessagePersister = { _, _ ->
            throw IllegalStateException("private removal-message failure")
        }
        engine.committedStudySummaryProvider = { _, _ ->
            summaryCalls++
            throw IllegalStateException("private summary failure")
        }

        val result = engine.run()

        assertTrue(result.success)
        assertEquals(0, result.studyReadyCount)
        assertFalse(result.message.orEmpty().contains("private"))
        assertEquals(1, reminderCalls)
        assertEquals(1, widgetCalls)
        assertEquals(1, summaryCalls)
        assertEquals(listOf("success"), syncStatuses())
    }

    @Test
    fun successCopyUsesTheEffectiveCommittedFocusPlan() {
        val engine = createManualSyncEngine(context, store, EmptyGateway(), RecordsSyncModels.Settings.kikuDefaults())
        val committedPlan = RecordsSchedulerModels.AdaptiveLoadPlan(
            false,
            20,
            1,
            1,
            listOf("焦"),
            0,
            false,
            "effective committed focus",
        )
        engine.committedStudySummaryProvider = { _, _ ->
            ManualSyncEngine.CommittedStudySummary(1, committedPlan)
        }

        val result = engine.run()

        assertTrue(result.success)
        assertEquals(1, result.studyReadyCount)
        assertEquals(committedPlan.status, result.adaptiveSummary)
    }

    @Test
    fun archiveFailureRetainsSuccessAndDoesNotExposeProviderError() {
        val engine = createManualSyncEngine(
            context,
            store,
            ArchiveThrowingGateway(),
            RecordsSyncModels.Settings.kikuDefaults(),
        ).also {
            it.reminderRescheduler = Runnable { }
            it.widgetRefresher = Runnable { }
        }

        val result = engine.run()

        assertTrue(result.success)
        assertTrue(result.message.orEmpty().contains("Archive tagging could not finish"))
        assertFalse(result.message.orEmpty().contains("private provider detail"))
        assertEquals(listOf("success"), syncStatuses())
        assertTrue(store.latestSync()!!.removalMessage.contains("Archive tagging could not finish"))
    }

    @Test
    fun committedSummaryCountsMergedReviewInsteadOfStaleSeededState() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val engine = createManualSyncEngine(context, store, EmptyGateway(), settings)
        val now = 1_725_000_000_000L
        val row = repairRow("痛")
        val baseline = reviewItem(row, dueAtMillis = now, totalReviews = 3, lastRealReviewDueAt = 100L)
        store.replaceStudyItems(listOf(baseline))

        // A foreground review lands after sync read its baseline but before it
        // writes the stale seeded snapshot.
        val reviewed = reviewItem(
            row,
            dueAtMillis = now + 86_400_000L,
            totalReviews = 4,
            lastRealReviewDueAt = now,
        )
        store.saveStudyItem(reviewed)
        val staleSeeded = reviewItem(row, dueAtMillis = now, totalReviews = 3, lastRealReviewDueAt = 100L)
        store.replaceStudyItems(
            listOf(staleSeeded),
            syncId = 1L,
            occurredAt = now,
            settings = settings,
            baseline = listOf(baseline),
        )
        val summary = engine.committedStudySummaryProvider(listOf(row), now)

        assertEquals(4, store.studyItemsForKanji(listOf(row.kanji)).single().totalReviews)
        assertEquals(0, summary.readyCount)
    }

    @Test
    fun committedSummaryDryRunsAdmissionForMissingPostSyncFocus() {
        val engine = createManualSyncEngine(context, store, EmptyGateway(), RecordsSyncModels.Settings.kikuDefaults())
        val now = 1_725_000_000_000L
        val row = repairRow("未")

        val summary = engine.committedStudySummaryProvider(listOf(row), now)

        assertEquals(1, summary.readyCount)
        assertEquals(listOf(row.kanji), summary.focusPlan?.focusKanji)
    }

    private fun syncStatuses(): List<String> {
        val statuses = mutableListOf<String>()
        store.readableDatabase.rawQuery("SELECT status FROM sync_runs ORDER BY id", null).use { cursor ->
            while (cursor.moveToNext()) {
                statuses += cursor.getString(0)
            }
        }
        return statuses
    }

    private fun sourceNoteExpressions(): List<String> {
        val expressions = mutableListOf<String>()
        store.readableDatabase.rawQuery("SELECT expression FROM source_notes ORDER BY note_id", null).use { cursor ->
            while (cursor.moveToNext()) {
                expressions += cursor.getString(0)
            }
        }
        return expressions
    }

    private fun insertEvidenceSnapshot(
        syncId: Long,
        finishedAt: Long,
        kanji: String,
        weakness: Int,
        mature: Int,
    ) {
        store.writableDatabase.execSQL(
            "INSERT INTO sync_runs " +
                "(id, started_at, finished_at, status, active_notes_count, active_cards_count, " +
                "suspended_cards_archived_count, suspended_kanji_imported_count, deleted_notes_count, deleted_cards_count, " +
                "error_code, error_message, removal_message) VALUES (?, ?, ?, 'success', 0, 0, 0, 0, 0, 0, NULL, NULL, '')",
            arrayOf<Any>(syncId, finishedAt - 1L, finishedAt),
        )
        store.writableDatabase.execSQL(
            "INSERT INTO sync_kanji_snapshots " +
                "(sync_id, finished_at, kanji, active_cards, suspended_cards, mature_support_count, average_interval_days, " +
                "total_lapses, total_reps, fsrs_stability_avg, fsrs_difficulty_avg, fsrs_retrievability_avg, weakness_score, " +
                "reason_code, active_example_count, suspended_example_count) " +
                "VALUES (?, ?, ?, 1, 0, ?, 10.0, 0, 5, NULL, NULL, NULL, ?, '', 1, 0)",
            arrayOf<Any>(syncId, finishedAt, kanji, mature, weakness),
        )
    }

    private fun repairRow(kanji: String): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji,
            900,
            "meaning-$kanji",
            "reading-$kanji",
            "search-$kanji",
            24,
            "suspended_archive",
            "reason text $kanji",
            0,
            1,
            0,
            emptyList<RecordsImportModels.Example>(),
        )
    }

    private fun reviewItem(
        row: RecordsImportModels.DashboardRow,
        dueAtMillis: Long,
        totalReviews: Int,
        lastRealReviewDueAt: Long,
    ): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(
            row.kanji,
            StudyLadderRules.STATE_REVIEW,
            dueAtMillis,
            30.0,
            5.0,
            totalReviews,
            0,
            0,
            0,
            0,
            0,
            0L,
            false,
            null,
            0L,
            30,
            StudyQueueSeeder.answerSignature(row),
            null,
            dueAtMillis - 86_400_000L,
            RecordsStudyModels.TaskMemory.initial(),
            RecordsStudyModels.TaskMemory.initial(),
            RecordsStudyModels.TaskMemory.initial(),
            RecordsStudyModels.TaskMemory.initial(),
            RecordsStudyModels.TaskMemory.initial(),
            RecordsBase.LadderRung.KANJI_MEANING,
            RecordsBase.SchedulerPhase.REVIEW,
            0,
            0,
            lastRealReviewDueAt,
            false,
            RecordsStudyModels.TaskMemory.initial(),
        )
    }

    private fun suspendedSnapshot(
        kanji: String,
        noteId: Long = 1L,
        cardId: Long = 10L,
    ): RecordsSyncModels.CollectionSnapshot {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val fields = linkedMapOf(
            settings.expressionField to kanji,
            settings.readingField to "reading-$kanji",
            settings.meaningField to "meaning-$kanji",
            settings.sentenceField to "$kanji sentence",
            settings.frequencyField to "9999",
            settings.frequencySortField to "9999",
        )
        return RecordsSyncModels.CollectionSnapshot(
            listOf(RecordsSyncModels.Note(noteId, settings.modelName, fields, emptyList())),
            listOf(
                RecordsSyncModels.Card(
                    cardId,
                    noteId,
                    0,
                    "例文マイニング",
                    -1,
                    3,
                    0,
                    0,
                    3,
                    0,
                    true,
                ),
            ),
        )
    }

    private fun matureProviderSnapshot(kanji: String): RecordsSyncModels.CollectionSnapshot {
        val suspended = suspendedSnapshot(kanji)
        return RecordsSyncModels.CollectionSnapshot(
            suspended.notes,
            listOf(
                RecordsSyncModels.Card(10L, 1L, 0, "例文マイニング", 2, 2, 0, 30, 3, 3, false),
                RecordsSyncModels.Card(11L, 1L, 1, "例文マイニング", 2, 2, 0, 30, 3, 3, false),
            ),
        )
    }

    private fun completeSnapshot(
        kanji: String,
        noteId: Long = 1L,
        cardId: Long = 10L,
    ): RecordsSyncModels.CollectionSnapshot {
        val suspended = suspendedSnapshot(kanji, noteId, cardId)
        return RecordsSyncModels.CollectionSnapshot(
            suspended.notes,
            listOf(
                RecordsSyncModels.Card(
                    cardId,
                    noteId,
                    0,
                    "例文マイニング",
                    2,
                    0,
                    30,
                    0,
                    3,
                    0,
                    false,
                ),
            ),
        )
    }

    private class SnapshotGateway(
        private val snapshot: RecordsSyncModels.CollectionSnapshot,
    ) : CollectionGateway {
        override fun readCollection(settings: RecordsSyncModels.Settings): RecordsSyncModels.CollectionSnapshot = snapshot

        override fun removeArchivedSuspendedCards(
            snapshot: RecordsSyncModels.CollectionSnapshot,
        ): AnkiDroidGateway.RemovalSummary {
            return AnkiDroidGateway.RemovalSummary(0, 0, 0, "")
        }
    }

    private class EmptyGateway : CollectionGateway {
        override fun readCollection(settings: RecordsSyncModels.Settings): RecordsSyncModels.CollectionSnapshot {
            return RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList())
        }

        override fun removeArchivedSuspendedCards(
            snapshot: RecordsSyncModels.CollectionSnapshot,
        ): AnkiDroidGateway.RemovalSummary {
            return AnkiDroidGateway.RemovalSummary(0, 0, 0, "")
        }
    }

    private class ThrowingGateway(private val failure: Exception) : CollectionGateway {
        override fun readCollection(settings: RecordsSyncModels.Settings): RecordsSyncModels.CollectionSnapshot {
            throw failure
        }

        override fun removeArchivedSuspendedCards(
            snapshot: RecordsSyncModels.CollectionSnapshot,
        ): AnkiDroidGateway.RemovalSummary {
            throw AssertionError("not used in this test")
        }
    }

    private class ArchiveThrowingGateway : CollectionGateway {
        override fun readCollection(settings: RecordsSyncModels.Settings): RecordsSyncModels.CollectionSnapshot {
            return RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList())
        }

        override fun removeArchivedSuspendedCards(
            snapshot: RecordsSyncModels.CollectionSnapshot,
        ): AnkiDroidGateway.RemovalSummary {
            throw IllegalStateException("private provider detail")
        }
    }
}
