package dev.bee.kanjianki.sync

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.anki.CollectionGateway
import dev.bee.kanjianki.anki.RepairedTagSummary
import dev.bee.kanjianki.core.RepairedWriteBackPolicy
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SyncSettings
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
class ManualSyncEngineRepairedWriteBackTest {
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
    fun toggleOffSkipsProposalAndProviderPhase() {
        val gateway = RecordingGateway()
        val engine = engine(gateway)
        var proposalCalls = 0
        engine.repairedProposalProvider = { _, _ ->
            proposalCalls++
            proposal()
        }

        val result = engine.run()

        assertTrue(result.success)
        assertEquals(0, proposalCalls)
        assertEquals(0, gateway.tagCalls)
    }

    @Test
    fun unconfirmedAutomaticSyncCannotRunRepairedWriteBack() {
        store.putIntSetting(SyncSettings.TAG_REPAIRED_CARDS_SETTING_KEY, 1)
        val gateway = RecordingGateway()
        val engine = engine(gateway, repairedWriteBackAuthorized = false)
        var proposalCalls = 0
        engine.repairedProposalProvider = { _, _ ->
            proposalCalls++
            proposal()
        }

        val result = engine.run()

        assertTrue(result.success)
        assertEquals(0, proposalCalls)
        assertEquals(0, gateway.tagCalls)
    }

    @Test
    fun convenienceConstructorDefaultsToNoWriteBackAuthorization() {
        store.putIntSetting(SyncSettings.TAG_REPAIRED_CARDS_SETTING_KEY, 1)
        val gateway = RecordingGateway()
        val engine = ManualSyncEngine(
            context,
            store,
            gateway,
            RecordsSyncModels.Settings.kikuDefaults(),
        ).also {
            it.reminderRescheduler = Runnable { }
            it.widgetRefresher = Runnable { }
        }
        var proposalCalls = 0
        engine.repairedProposalProvider = { _, _ ->
            proposalCalls++
            proposal()
        }

        val result = engine.run()

        assertTrue(result.success)
        assertEquals(0, proposalCalls)
        assertEquals(0, gateway.tagCalls)
    }

    @Test
    fun newlyEligibleProposalIsDeferredWhenItWasNotInConfirmedPreview() {
        store.putIntSetting(SyncSettings.TAG_REPAIRED_CARDS_SETTING_KEY, 1)
        val gateway = RecordingGateway()
        val engine = engine(gateway, confirmedRepairedNoteIds = emptySet())
        engine.repairedProposalProvider = { _, _ -> proposal() }

        val result = engine.run()

        assertTrue(result.success)
        assertEquals(0, gateway.tagCalls)
    }

    @Test
    fun taggingFailureNeverFailsSyncAndPersistsRetryMessage() {
        store.putIntSetting(SyncSettings.TAG_REPAIRED_CARDS_SETTING_KEY, 1)
        val gateway = RecordingGateway(tagFailure = IllegalStateException("provider busy"))
        val stages = mutableListOf<SyncProgress.Stage?>()
        val engine = engine(gateway, progress = { stages += it.stage })
        engine.repairedProposalProvider = { _, _ -> proposal() }
        var recorderCalls = 0
        engine.repairedWriteBackRecorder = { _, tagged, _, _ ->
            recorderCalls++
            assertTrue(tagged.isEmpty())
            emptyList()
        }

        val result = engine.run()

        assertTrue(result.success)
        assertEquals(1, gateway.tagCalls)
        assertEquals(1, recorderCalls)
        assertTrue(result.message!!.contains("retry on the next sync"))
        assertTrue(store.latestSync()!!.removalMessage.contains("retry on the next sync"))
        assertTrue(stages.indexOf(SyncProgress.Stage.TAGGING_REPAIRED) > stages.indexOf(SyncProgress.Stage.ARCHIVING_IMPORTED_CARDS))
    }

    @Test
    fun proposalFailureNeverFailsCommittedSyncAndPersistsRetryMessage() {
        store.putIntSetting(SyncSettings.TAG_REPAIRED_CARDS_SETTING_KEY, 1)
        val gateway = RecordingGateway()
        val engine = engine(gateway)
        engine.repairedProposalProvider = { _, _ -> throw IllegalStateException("local proposal busy") }

        val result = engine.run()

        assertTrue(result.success)
        assertEquals(0, gateway.tagCalls)
        assertTrue(result.message!!.contains("could not be prepared"))
        assertTrue(store.latestSync()!!.removalMessage.contains("could not be prepared"))
    }

    @Test
    fun successfulTaggingRecordsProviderConfirmedNotes() {
        store.putIntSetting(SyncSettings.TAG_REPAIRED_CARDS_SETTING_KEY, 1)
        val gateway = RecordingGateway()
        val engine = engine(gateway)
        engine.repairedProposalProvider = { _, _ -> proposal() }
        var recorded = emptySet<Long>()
        engine.repairedWriteBackRecorder = { _, tagged, _, _ ->
            recorded = tagged
            listOf("徴")
        }

        val result = engine.run()

        assertTrue(result.success)
        assertEquals(setOf(1L), recorded)
        assertTrue(result.message!!.contains("Tagged 1 repaired note"))
        assertFalse(result.message!!.contains("retry"))
    }

    @Test
    fun localConfirmationFailureKeepsSyncSuccessfulAndReportsRetry() {
        store.putIntSetting(SyncSettings.TAG_REPAIRED_CARDS_SETTING_KEY, 1)
        val engine = engine(RecordingGateway())
        engine.repairedProposalProvider = { _, _ -> proposal() }
        engine.repairedWriteBackRecorder = { _, _, _, _ ->
            throw IllegalStateException("local stamp busy")
        }

        val result = engine.run()

        assertTrue(result.success)
        assertTrue(result.message!!.contains("confirmation could not be saved"))
        assertTrue(store.latestSync()!!.removalMessage.contains("confirmation could not be saved"))
    }

    private fun engine(
        gateway: RecordingGateway,
        progress: SyncProgress.Listener = SyncProgress.NONE,
        repairedWriteBackAuthorized: Boolean = true,
        confirmedRepairedNoteIds: Set<Long>? = null,
    ): ManualSyncEngine = ManualSyncEngine(
        context,
        store,
        gateway,
        RecordsSyncModels.Settings.kikuDefaults(),
        progress,
        dev.bee.kanjianki.time.AppClock.systemClock(),
        repairedWriteBackAuthorized,
        confirmedRepairedNoteIds,
    ).also {
        it.reminderRescheduler = Runnable { }
        it.widgetRefresher = Runnable { }
    }

    private fun proposal() = RepairedWriteBackPolicy.Proposal(
        noteIdsToTag = setOf(1L),
        cardIdsByNote = mapOf(1L to setOf(10L)),
        kanjiByNote = mapOf(1L to setOf("徴")),
        repairedKanji = listOf("徴"),
        candidateSourceCount = 1,
        rejectedCardCount = 0,
    )

    private class RecordingGateway(
        private val tagFailure: RuntimeException? = null,
    ) : CollectionGateway {
        var tagCalls = 0

        override fun readCollection(settings: RecordsSyncModels.Settings) =
            RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList())

        override fun removeArchivedSuspendedCards(
            snapshot: RecordsSyncModels.CollectionSnapshot,
            selectedSuspendedImports: List<dev.bee.kanjianki.core.RecordsImportModels.SuspendedImport>?,
            progress: SyncProgress.Listener?,
        ): AnkiDroidGateway.RemovalSummary {
            progress?.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.ARCHIVING_IMPORTED_CARDS))
            return AnkiDroidGateway.RemovalSummary(0, 0, 0, "archive done")
        }

        override fun removeArchivedSuspendedCards(snapshot: RecordsSyncModels.CollectionSnapshot) =
            AnkiDroidGateway.RemovalSummary(0, 0, 0, "archive done")

        override fun tagRepairedNotes(
            noteIds: Set<Long>,
            progress: SyncProgress.Listener?,
        ): RepairedTagSummary {
            tagCalls++
            progress?.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.TAGGING_REPAIRED))
            tagFailure?.let { throw it }
            return RepairedTagSummary(noteIds, noteIds, emptySet(), "Tagged 1 repaired note in AnkiDroid.")
        }
    }
}
