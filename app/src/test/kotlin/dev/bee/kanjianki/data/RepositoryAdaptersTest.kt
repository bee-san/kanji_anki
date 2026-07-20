package dev.bee.kanjianki.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsSyncModels
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RepositoryAdaptersTest {
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
    fun typedSettingsCommandsRoundTripWithoutRawKeys() = runTest {
        val repository = SqliteSettingsRepository(store)

        assertTrue(
            repository.save(
                SettingsSaveCommand.AdaptiveWorkload(
                    AdaptiveWorkloadSnapshot(workPercent = 65, maxItems = 17, mode = "manual"),
                ),
            ).isOk(),
        )
        assertTrue(
            repository.save(
                SettingsSaveCommand.Sync(
                    RecordsSyncModels.Settings.kikuDefaults(),
                    tagRepairedCards = true,
                ),
            ).isOk(),
        )

        val snapshot = repository.load().valueOrNull()
        assertEquals(65, snapshot?.adaptiveWorkload?.workPercent)
        assertEquals(17, snapshot?.adaptiveWorkload?.maxItems)
        assertEquals("manual", snapshot?.adaptiveWorkload?.mode)
        assertTrue(snapshot?.tagRepairedCards == true)
    }

    @Test
    fun featureReadAdaptersReturnStableSnapshotsFromAnEmptyStore() = runTest {
        val home = SqliteHomeRepository(store)
        val study = SqliteStudyRepository(store)
        val stats = SqliteStatsRepository(store)

        val homeSnapshot = home.loadHome(FINISHED_AT).valueOrNull()
        assertTrue(homeSnapshot?.activeRows?.isEmpty() == true)
        assertNull(homeSnapshot?.latestSync)
        assertEquals(0, homeSnapshot?.studyStreak?.currentDays)

        assertTrue(
            home.saveMnemonic(SaveMnemonicCommand("痛", "water memory", FINISHED_AT)).isOk(),
        )
        assertEquals(
            "water memory",
            home.loadKanjiDetail("痛", FINISHED_AT).valueOrNull()?.mnemonic,
        )

        val queue = study.loadQueue(FINISHED_AT).valueOrNull()
        assertTrue(queue?.studyItems?.isEmpty() == true)
        assertTrue(study.loadChoiceData("痛", FINISHED_AT).valueOrNull()?.inventory?.isEmpty() == true)

        val analytics = stats.refresh(FINISHED_AT).valueOrNull()
        assertEquals(0, analytics?.outcomeStats?.weakKanjiImproved?.improvedCount)
        assertEquals(0, analytics?.studyImpactStats?.totalReviews)
    }

    @Test
    fun syncPublicationInvokesPlannerOnceAndFinalizesOneSuccessfulRun() = runTest {
        val repository = SqliteSyncRepository(store)
        var plannerCalls = 0
        val plan = emptyAdaptivePlan()

        val result = repository.publish(
            emptyPublicationCommand {
                plannerCalls += 1
                SyncQueuePlan(emptyList(), plan)
            },
        )

        assertTrue(result.isOk())
        assertEquals(1, plannerCalls)
        assertEquals(plan, result.valueOrNull()?.adaptiveLoadPlan)
        assertTrue(store.hasSuccessfulSyncSince(FINISHED_AT))
    }

    @Test
    fun plannerFailureRollsBackStagedMirrorAndPendingHistory() = runTest {
        val repository = SqliteSyncRepository(store)

        try {
            repository.publish(
                emptyPublicationCommand {
                    throw IllegalArgumentException("queue planning failed")
                },
            )
            throw AssertionError("planner failure should propagate")
        } catch (_: IllegalArgumentException) {
            // A non-storage planning error propagates after the outer transaction rolls back.
        }

        assertNull(store.latestSync())
        assertTrue(store.studyItems().isEmpty())
        assertFalse(store.hasPersistedCollectionMirror())
    }

    private fun emptyPublicationCommand(planner: SyncQueuePlanner): SyncPublicationCommand =
        SyncPublicationCommand(
            snapshot = RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
            imports = emptyList(),
            auditImports = emptyList(),
            rows = emptyList(),
            settings = RecordsSyncModels.Settings.kikuDefaults(),
            timing = SyncTimingSnapshot(STARTED_AT, FINISHED_AT),
            removalMessage = null,
            similarIndex = null,
            dictionary = null,
            queuePlanner = planner,
        )

    private fun emptyAdaptivePlan(): RecordsSchedulerModels.AdaptiveLoadPlan =
        RecordsSchedulerModels.AdaptiveLoadPlan(
            100,
            0,
            0,
            emptyList(),
            0,
            true,
            "all",
        )

    private companion object {
        const val STARTED_AT = 1_000L
        const val FINISHED_AT = 2_000L
    }
}
