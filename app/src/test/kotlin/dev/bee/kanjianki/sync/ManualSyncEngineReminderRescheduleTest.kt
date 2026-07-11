package dev.bee.kanjianki.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.anki.CollectionGateway
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.StudyLadderRules
import dev.bee.kanjianki.core.StudyQueueSeeder
import dev.bee.kanjianki.data.LocalStore
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
        val engine = ManualSyncEngine(context, store, EmptyGateway(), RecordsSyncModels.Settings.kikuDefaults())
        var rearms = 0
        engine.reminderRescheduler = Runnable { rearms++ }

        val result = engine.run()

        assertTrue(result.success)
        assertEquals(1, rearms)
    }

    @Test
    fun failedSyncDoesNotRearmReminder() {
        val engine = ManualSyncEngine(
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
        val engine = ManualSyncEngine(context, store, EmptyGateway(), RecordsSyncModels.Settings.kikuDefaults())
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
        val engine = ManualSyncEngine(context, store, EmptyGateway(), RecordsSyncModels.Settings.kikuDefaults())
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
        val engine = ManualSyncEngine(
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
        val engine = ManualSyncEngine(context, store, EmptyGateway(), settings)
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
        val engine = ManualSyncEngine(context, store, EmptyGateway(), RecordsSyncModels.Settings.kikuDefaults())
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
