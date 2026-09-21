package dev.bee.kanjianki.data.conformance

import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.StudyQueueSeeder
import dev.bee.kanjianki.data.RecordRepairedWriteBackCommand
import dev.bee.kanjianki.data.RecordSyncFailureCommand
import dev.bee.kanjianki.data.StoreResult
import dev.bee.kanjianki.data.SyncPublicationCommand
import dev.bee.kanjianki.data.SyncQueuePlan
import dev.bee.kanjianki.data.SyncQueuePlanner
import dev.bee.kanjianki.data.SyncTimingSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/**
 * The Goal 182 cross-implementation contract for sync publication and history:
 * the legacy Android `LocalStore` SyncRepository and the shared `:data-sql`
 * SyncRepository must be indistinguishable, including the atomic
 * mirror/queue/history publication, planner-failure rollback,
 * successful-run-only history, and the post-commit write-back/handoff surface.
 */
class SyncRepositoryConformanceSuite(
    private val host: RepositoryConformanceHost,
) {
    suspend fun runAll() {
        emptyStoredStateIsStable()
        publishSeedsQueueAndFinalizesOneSuccessfulRun()
        publishRunsThePlannerExactlyOnce()
        recordFailureAndRemovalMessageRoundTrip()
        repairedWriteBackAndHandoffOnEmptyStore()
    }

    private suspend fun emptyStoredStateIsStable() {
        host.reset()
        val state = host.sync.loadStoredState().expect("loadStoredState on empty store")
        assertFalse("no mirror exists yet", state.hasCollectionMirror)
        assertTrue(state.suspendedImports.isEmpty())
        assertTrue(state.unrestoredSuspendedArchiveCardIds.isEmpty())
        assertTrue(state.studyItems.isEmpty())
        assertNull(state.latestSuccessfulSyncAtMillis)
        assertTrue("an untouched store is an empty profile", state.databaseIsEmpty)
    }

    private suspend fun publishSeedsQueueAndFinalizesOneSuccessfulRun() {
        host.reset()
        val result = host.sync.publish(
            publicationCommand { snapshot ->
                val seeded = StudyQueueSeeder().seedQueue(
                    allRows = snapshot.rows,
                    eligibleRows = snapshot.activeRows,
                    existing = snapshot.currentItems,
                    settings = snapshot.settings,
                    nowMillis = snapshot.nowMillis,
                    startOfDayMillis = 0L,
                    plan = null,
                    ladder = snapshot.studyLadder,
                )
                SyncQueuePlan(seeded, emptyAdaptivePlan())
            },
        ).expect("publish with a seeded queue")

        assertEquals("裂", result.activeRows.single().kanji)

        val state = host.sync.loadStoredState().expect("loadStoredState after publish")
        assertTrue("a committed sync leaves a mirror", state.hasCollectionMirror)
        assertEquals(listOf("裂"), state.studyItems.map { it.kanji })
        assertEquals(FIXED_FINISHED_AT, state.latestSuccessfulSyncAtMillis)

        // The dashboard now reflects the published row on both implementations.
        val home = host.home.loadHome(FIXED_FINISHED_AT).expect("loadHome after publish")
        assertEquals(listOf("裂"), home.activeRows.map { it.kanji })
    }

    private suspend fun publishRunsThePlannerExactlyOnce() {
        host.reset()
        var plannerCalls = 0
        val plan = emptyAdaptivePlan()
        val result = host.sync.publish(
            publicationCommand {
                plannerCalls += 1
                SyncQueuePlan(emptyList(), plan)
            },
        ).expect("publish with empty plan")
        assertEquals("the planner runs exactly once per publish", 1, plannerCalls)
        assertEquals(plan, result.adaptiveLoadPlan)
        assertEquals(
            FIXED_FINISHED_AT,
            host.sync.loadStoredState().expect("state after empty publish").latestSuccessfulSyncAtMillis,
        )
    }

    private suspend fun recordFailureAndRemovalMessageRoundTrip() {
        host.reset()
        assertTrue(
            host.sync.recordFailure(
                RecordSyncFailureCommand(
                    startedAtMillis = FIXED_STARTED_AT,
                    finishedAtMillis = FIXED_FINISHED_AT,
                    status = "provider_error",
                    errorCode = "provider_unavailable",
                    errorMessage = "provider unavailable",
                ),
            ).isOk(),
        )
        // A failed run is not a successful sync.
        assertNull(
            "a failed run does not advance the successful-sync marker",
            host.sync.loadStoredState().expect("state after failure").latestSuccessfulSyncAtMillis,
        )
        val latest = host.home.loadHome(FIXED_FINISHED_AT).expect("home after failure").latestSync
        assertEquals("provider_error", latest?.status)
        assertEquals("provider unavailable", latest?.errorMessage)

        assertTrue(host.sync.updateRemovalMessage(1L, "nothing removed").isOk())
        assertEquals(
            "nothing removed",
            host.home.loadHome(FIXED_FINISHED_AT).expect("home after removal update").latestSync?.removalMessage,
        )
    }

    private suspend fun repairedWriteBackAndHandoffOnEmptyStore() {
        host.reset()
        val snapshot = RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList())
        val proposal = host.sync.repairedWriteBackProposal(snapshot, 2).expect("write-back proposal")
        assertTrue("no repaired notes without archive rows", proposal.isEmpty())
        assertTrue(host.sync.repairedWriteBackPreview(2).expect("write-back preview").isEmpty())
        assertTrue(
            host.sync.recordRepairedWriteBack(
                RecordRepairedWriteBackCommand(
                    proposal = proposal,
                    taggedNoteIds = emptySet(),
                    occurredAtMillis = FIXED_FINISHED_AT,
                    syncId = 1L,
                ),
            ).expect("record empty write-back").isEmpty(),
        )
        assertTrue(host.sync.loadRepairedHandoff().expect("handoff on empty store").isEmpty())
        assertTrue(host.sync.dismissRepairedHandoff().isOk())
    }

    private fun publicationCommand(planner: SyncQueuePlanner): SyncPublicationCommand {
        val row = RecordsImportModels.DashboardRow(
            "裂",
            null,
            "split",
            "れつ",
            "裂",
            90,
            "weak_support",
            "needs repair",
            1,
            0,
            0,
            listOf(
                RecordsImportModels.Example(
                    RepositoryConformanceFixture.SOURCE_ACTIVE,
                    11L,
                    21L,
                    "分裂",
                    "ぶんれつ",
                    "division",
                    "細胞が分裂する。",
                    false,
                    0,
                ),
            ),
        )
        val note = RecordsSyncModels.Note(
            21L,
            "Kiku",
            linkedMapOf(
                "Expression" to "分裂",
                "ExpressionReading" to "ぶんれつ",
                "MainDefinition" to "division",
                "Sentence" to "細胞が分裂する。",
            ),
            emptyList(),
        )
        val card = RecordsSyncModels.Card(11L, 21L, 0, "Mining", 0, 2, 0, 30, 5, 0, false)
        return SyncPublicationCommand(
            snapshot = RecordsSyncModels.CollectionSnapshot(listOf(note), listOf(card)),
            imports = emptyList(),
            auditImports = emptyList(),
            rows = listOf(row),
            settings = RecordsSyncModels.Settings.kikuDefaults(),
            timing = SyncTimingSnapshot(FIXED_STARTED_AT, FIXED_FINISHED_AT),
            removalMessage = null,
            similarIndex = null,
            dictionary = null,
            queuePlanner = planner,
        )
    }

    private fun emptyAdaptivePlan(): RecordsSchedulerModels.AdaptiveLoadPlan =
        RecordsSchedulerModels.AdaptiveLoadPlan(
            true,
            100,
            0,
            0,
            emptyList(),
            0,
            true,
            "all",
        )

    private fun <T> StoreResult<T>.expect(label: String): T {
        assertTrue("$label must succeed, got $this", isOk())
        if (this is StoreResult.Ok) {
            return value
        }
        throw AssertionError("$label was not Ok: $this")
    }

    private companion object {
        const val FIXED_STARTED_AT = 1_770_090_000_000L
        const val FIXED_FINISHED_AT = 1_770_095_000_000L
    }
}
