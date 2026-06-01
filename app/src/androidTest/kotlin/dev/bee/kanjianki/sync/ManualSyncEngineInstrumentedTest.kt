package dev.bee.kanjianki.sync

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.anki.CollectionGateway
import dev.bee.kanjianki.core.AdaptiveLoadPlanner
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.LocalStore
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

private const val DATABASE_NAME = "kanji_anki_simple.db"

@RunWith(AndroidJUnit4::class)
class ManualSyncEngineInstrumentedTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DATABASE_NAME)
        store = LocalStore(context)
    }

    @After
    fun tearDown() {
        if (::store.isInitialized) {
            store.close()
        }
        if (::context.isInitialized) {
            context.deleteDatabase(DATABASE_NAME)
        }
    }

    @Test
    fun successfulSyncArchivesSuspendedCardsBuildsRowsAndSeedsStudy() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val result = ManualSyncEngine(
            context,
            store,
            FakeGateway(snapshot(settings), AnkiDroidGateway.RemovalSummary(1, 0, 0, "cleanup done")),
            settings,
        ).run()

        assertTrue(result.success)
        assertEquals("cleanup done", result.message)
        val rows = store.dashboardRows()
        val items = store.studyItems()
        assertTrue(rows.isNotEmpty())
        assertTrue(store.suspendedImports().isNotEmpty())
        assertTrue(items.isNotEmpty())
        assertEquals("success", store.latestSync()!!.status)

        val rowByKanji = mutableMapOf<String, RecordsImportModels.DashboardRow>()
        for (row in rows) {
            rowByKanji[row.kanji] = row
        }
        var activeStudyItem = false
        for (item in items) {
            if (item.state == "retired") {
                continue
            }
            activeStudyItem = true
            assertTrue(
                "Active study item must still have current Anki evidence: ${item.kanji}",
                rowByKanji.containsKey(item.kanji),
            )
        }
        assertTrue(activeStudyItem)
        assertTrue("The fake suspended problem card should create at least one suspended-evidence row.", hasSuspendedEvidence(rows))
    }

    @Test
    fun nullProgressListenerUsesNoopProgressAndStillSyncs() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()

        val result = ManualSyncEngine(
            context,
            store,
            FakeGateway(snapshot(settings), AnkiDroidGateway.RemovalSummary(0, 0, 0, "cleanup done")),
            settings,
            null,
        ).run()

        assertTrue(result.success)
        assertEquals("success", store.latestSync()!!.status)
    }

    @Test
    fun successfulSyncUsesAdaptiveWorkloadForNewAdmissions() {
        val settings = importSettings(true, false, false, "", false, 1)
        store.saveAdaptiveLoadMode(AdaptiveLoadPlanner.MODE_MANUAL)
        store.saveAdaptiveLoadWorkPercent(0)

        val result = ManualSyncEngine(
            context,
            store,
            FakeGateway(manyProblemSnapshot(), AnkiDroidGateway.RemovalSummary(0, 0, 0, "cleanup done")),
            settings,
        ).run()

        assertTrue(result.success)
        assertTrue(result.adaptiveSummary.contains("Very little"))
        assertEquals(1, activeStudyItemCount(store.studyItems()))
    }

    @Test
    fun browserQueryActiveCardsCreateRowsWithoutSuspendedArchive() {
        val settings = importSettings(false, false, false, "", false, 1, true, "deck:Mining")
        val matched = note(1L, "裂ける", "さける", "split", "裂ける音。")
        val unmatched = note(2L, "謎", "なぞ", "mystery", "謎を見た。")
        val snapshot = RecordsSyncModels.CollectionSnapshot(
            listOf(matched, unmatched),
            listOf(
                browserQueryCard(10L, 1L, false),
                RecordsSyncModels.Card(20L, 2L, 0, "Kiku", 2, 2, 0, 3, 12, 0, false),
            ),
        )
        val gateway = RecordingGateway(snapshot, AnkiDroidGateway.RemovalSummary(0, 0, 0, "cleanup done"))

        val result = ManualSyncEngine(context, store, gateway, settings).run()

        assertTrue(result.success)
        assertTrue(store.suspendedImports().isEmpty())
        assertEquals(0, store.latestSync()!!.suspendedCards)
        assertTrue(gateway.selectedSuspendedImports.isEmpty())
        val rows = store.dashboardRows()
        assertEquals(1, rows.size)
        val row = rows[0]
        assertEquals("裂", row.kanji)
        assertEquals(1, row.activeExampleCount)
        assertEquals(0, row.suspendedExampleCount)
        assertEquals("browser_query", row.examples[0].sourceType)
    }

    @Test
    fun browserQuerySuspendedCardsArchiveWhenSuspendedFilterOff() {
        val settings = importSettings(false, false, false, "", false, 1, true, "is:suspended deck:Mining")
        val matched = note(1L, "謎", "なぞ", "mystery", "謎を見た。")
        val snapshot = RecordsSyncModels.CollectionSnapshot(
            listOf(matched),
            listOf(browserQueryCard(10L, 1L, true)),
        )
        val gateway = RecordingGateway(snapshot, AnkiDroidGateway.RemovalSummary(1, 0, 0, "cleanup done"))

        val result = ManualSyncEngine(context, store, gateway, settings).run()

        assertTrue(result.success)
        assertEquals(1, store.latestSync()!!.suspendedCards)
        assertEquals(1, store.suspendedImports().size)
        assertEquals(1, gateway.selectedSuspendedImports.size)
        assertEquals("謎", gateway.selectedSuspendedImports[0].kanji)
        val rows = store.dashboardRows()
        assertEquals(1, rows.size)
        assertEquals(1, rows[0].suspendedExampleCount)
        assertEquals("suspended", rows[0].examples[0].sourceType)
    }

    @Test
    fun suspendedSourceAndBrowserQuerySourceDedupeSameCard() {
        val settings = importSettings(false, true, false, "", false, 1, true, "is:suspended deck:Mining")
        val matched = note(1L, "謎", "なぞ", "mystery", "謎を見た。")
        val snapshot = RecordsSyncModels.CollectionSnapshot(
            listOf(matched),
            listOf(browserQueryCard(10L, 1L, true)),
        )
        val gateway = RecordingGateway(snapshot, AnkiDroidGateway.RemovalSummary(1, 0, 0, "cleanup done"))

        val result = ManualSyncEngine(context, store, gateway, settings).run()

        assertTrue(result.success)
        assertEquals(1, gateway.selectedSuspendedImports.size)
        assertEquals(1, gateway.selectedSuspendedImports[0].sources.size)
        val source = gateway.selectedSuspendedImports[0].sources[0]
        assertEquals(10L, source.cardId)
        assertEquals("suspended", source.sourceType)
        assertTrue(source.ruleTypes.contains("suspended"))
        assertTrue(source.ruleTypes.contains("browser_query"))
        val rows = store.dashboardRows()
        assertEquals(1, rows.size)
        assertEquals(1, rows[0].examples.size)
    }

    @Test
    fun browserQueryRowsStillUseAdaptiveWorkload() {
        val settings = importSettings(false, false, false, "", false, 1, true, "deck:Mining")
        store.saveAdaptiveLoadMode(AdaptiveLoadPlanner.MODE_MANUAL)
        store.saveAdaptiveLoadWorkPercent(0)
        val snapshot = RecordsSyncModels.CollectionSnapshot(
            listOf(
                note(1L, "拉麺", "らーめん", "ramen", "拉麺を食べた。"),
                note(2L, "謎", "なぞ", "mystery", "謎を見た。"),
                note(3L, "裂ける", "さける", "split", "裂ける音。"),
            ),
            listOf(
                browserQueryCard(10L, 1L, false),
                browserQueryCard(20L, 2L, false),
                browserQueryCard(30L, 3L, false),
            ),
        )

        val result = ManualSyncEngine(
            context,
            store,
            FakeGateway(snapshot, AnkiDroidGateway.RemovalSummary(0, 0, 0, "cleanup done")),
            settings,
        ).run()

        assertTrue(result.success)
        assertTrue(result.adaptiveSummary.contains("Very little"))
        assertEquals(1, activeStudyItemCount(store.studyItems()))
    }

    @Test
    fun importFiltersCanCreateRowsFromTaggedActiveCardsWithoutArchivingExcludedSuspendedCards() {
        val settings = importSettings(false, false, true, "focus", false, 1)
        val taggedActive = note(1L, "裂ける", "さける", "split", "裂ける音。", "focus")
        val excludedSuspended = note(2L, "謎", "なぞ", "mystery", "謎を見た。")
        val snapshot = RecordsSyncModels.CollectionSnapshot(
            listOf(taggedActive, excludedSuspended),
            listOf(
                RecordsSyncModels.Card(10L, 1L, 0, "Kiku", 2, 2, 0, 30, 12, 0, false),
                RecordsSyncModels.Card(20L, 2L, 0, "Kiku", -1, 0, 0, 0, 0, 0, true),
            ),
        )
        val gateway = RecordingGateway(snapshot, AnkiDroidGateway.RemovalSummary(0, 0, 0, "cleanup done"))

        val result = ManualSyncEngine(context, store, gateway, settings).run()

        assertTrue(result.success)
        assertTrue(store.suspendedImports().isEmpty())
        assertEquals(0, store.latestSync()!!.suspendedCards)
        assertTrue(gateway.selectedSuspendedImports.isEmpty())
        val rows = store.dashboardRows()
        assertEquals(1, rows.size)
        assertEquals("裂", rows[0].kanji)
    }

    @Test
    fun failedSyncPersistsConfigError() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val result = ManualSyncEngine(
            context,
            store,
            FailingGateway(),
            settings,
        ).run()

        assertFalse(result.success)
        assertEquals("Kiku note type was not found in AnkiDroid.", result.message)
        val status = store.latestSync()!!
        assertEquals("config_error", status.status)
        assertEquals("Kiku note type was not found in AnkiDroid.", status.errorMessage)
    }

    @Test
    fun retryableSyncFailurePersistsRetryableError() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val result = ManualSyncEngine(
            context,
            store,
            RetryableGateway(),
            settings,
        ).run()

        assertFalse(result.success)
        assertEquals("AnkiDroid returned no configured note cursor.", result.message)
        val status = store.latestSync()!!
        assertEquals("retryable_error", status.status)
        assertEquals("AnkiDroid returned no configured note cursor.", status.errorMessage)
    }

    @Test
    fun unexpectedRuntimeExceptionPersistsRetryableError() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val result = ManualSyncEngine(
            context,
            store,
            RuntimeFailingGateway(),
            settings,
        ).run()

        assertFalse(result.success)
        assertEquals("sync crashed", result.message)
        val status = store.latestSync()!!
        assertEquals("retryable_error", status.status)
        assertEquals("sync crashed", status.errorMessage)
    }

    @Test
    fun concurrentManualSyncSkipsWithoutRecordingSync() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val blockingGateway = BlockingGateway(
            snapshot(settings),
            AnkiDroidGateway.RemovalSummary(0, 0, 0, "cleanup done"),
        )
        val firstResult = AtomicReference<ManualSyncEngine.SyncResult?>(null)
        val threadFailure = AtomicReference<Throwable?>(null)
        val firstSync = Thread {
            try {
                firstResult.set(ManualSyncEngine(context, store, blockingGateway, settings).run())
            } catch (error: Throwable) {
                threadFailure.set(error)
            }
        }

        firstSync.start()
        assertTrue(blockingGateway.awaitStarted())

        val skipped = ManualSyncEngine(
            context,
            store,
            FakeGateway(snapshot(settings), AnkiDroidGateway.RemovalSummary(0, 0, 0, "cleanup done")),
            settings,
        ).run()

        assertTrue(skipped.skipped)
        assertFalse(skipped.success)
        assertEquals("Sync already running.", skipped.message)
        assertNull(store.latestSync())

        blockingGateway.release()
        firstSync.join(10_000L)
        assertFalse(firstSync.isAlive)
        assertNull(threadFailure.get())
        assertTrue(firstResult.get()!!.success)
    }

    @Test
    fun manualSyncReceivesOrderedProgressEvents() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val events = mutableListOf<String>()

        val result = ManualSyncEngine(
            context,
            store,
            ProgressGateway(snapshot(settings), AnkiDroidGateway.RemovalSummary(1, 0, 0, "cleanup done")),
            settings,
            { progress -> events.add("${progress.stage?.name}:${progress.scannedCards}/${progress.totalCards}") },
        ).run()

        assertTrue(result.success)
        assertEquals(
            listOf(
                "FINDING_NOTE_TYPE:0/-1",
                "READING_NOTES:0/-1",
                "SCANNING_CARDS:0/2",
                "SCANNING_CARDS:1/2",
                "SCANNING_CARDS:2/2",
                "PROCESSING_IMPORTED_CARDS:0/-1",
                "SAVING_LOCAL_DATA:0/-1",
                "ARCHIVING_IMPORTED_CARDS:0/-1",
                "BUILDING_PRACTICE_QUEUE:0/-1",
            ),
            events,
        )
    }

    private fun snapshot(settings: RecordsSyncModels.Settings): RecordsSyncModels.CollectionSnapshot {
        val active = note(1L, "確認", "かくにん", "confirmation", "確認した。")
        val suspended = note(2L, "笥箱", "しはこ", "rare box", "笥箱を見た。")
        val activeCard = RecordsSyncModels.Card(10L, 1L, 0, "Kiku", 2, 2, 0, settings.matureDays + 5, 12, 0, false)
        val suspendedCard = RecordsSyncModels.Card(20L, 2L, 0, "Kiku", -1, 0, 0, 0, 0, 0, true)
        return RecordsSyncModels.CollectionSnapshot(
            listOf(active, suspended),
            listOf(activeCard, suspendedCard),
        )
    }

    private fun manyProblemSnapshot(): RecordsSyncModels.CollectionSnapshot {
        val first = note(1L, "拉麺", "らーめん", "ramen", "拉麺を食べた。")
        val second = note(2L, "謎", "なぞ", "mystery", "謎を見た。")
        val third = note(3L, "裂ける", "さける", "split", "裂ける音。")
        return RecordsSyncModels.CollectionSnapshot(
            listOf(first, second, third),
            listOf(
                RecordsSyncModels.Card(10L, 1L, 0, "Kiku", 2, 2, 0, 3, 12, 2, false),
                RecordsSyncModels.Card(20L, 2L, 0, "Kiku", 2, 2, 0, 3, 12, 1, false),
                RecordsSyncModels.Card(30L, 3L, 0, "Kiku", 2, 2, 0, 3, 12, 0, false),
            ),
        )
    }

    private fun browserQueryCard(cardId: Long, noteId: Long, suspended: Boolean): RecordsSyncModels.Card {
        return RecordsSyncModels.Card(
            cardId,
            noteId,
            0,
            "Kiku",
            if (suspended) -1 else 2,
            if (suspended) 0 else 2,
            0,
            if (suspended) 0 else 3,
            12,
            0,
            suspended,
        ).withBrowserQueryMatched(true)
    }

    private fun activeStudyItemCount(items: List<RecordsStudyModels.StudyItem>): Int {
        var count = 0
        for (item in items) {
            if (item.state != "retired") {
                count++
            }
        }
        return count
    }

    private fun hasSuspendedEvidence(rows: List<RecordsImportModels.DashboardRow>): Boolean {
        for (row in rows) {
            if (row.suspendedExampleCount > 0) {
                return true
            }
        }
        return false
    }

    private fun note(
        id: Long,
        expression: String,
        reading: String,
        meaning: String,
        sentence: String,
        vararg tags: String,
    ): RecordsSyncModels.Note {
        val fields = linkedMapOf(
            "Expression" to expression,
            "ExpressionReading" to reading,
            "MainDefinition" to meaning,
            "Sentence" to sentence,
            "Frequency" to "1000",
            "FreqSort" to "1000",
        )
        return RecordsSyncModels.Note(id, "Kiku", fields, tags.toList())
    }

    private fun importSettings(
        active: Boolean,
        suspended: Boolean,
        tagged: Boolean,
        tags: String,
        weak: Boolean,
        minMatching: Int,
        browserQueryCards: Boolean = false,
        browserQuery: String = "",
    ): RecordsSyncModels.Settings {
        val defaults = RecordsSyncModels.Settings.kikuDefaults()
        return RecordsSyncModels.Settings(
            defaults.modelName,
            defaults.templateName,
            defaults.expressionField,
            defaults.readingField,
            defaults.meaningField,
            defaults.sentenceField,
            defaults.frequencyField,
            defaults.frequencySortField,
            defaults.matureDays,
            defaults.matureSupportThreshold,
            defaults.suspendedRankMin,
            defaults.suspendedRankMax,
            defaults.activeQueueCap,
            defaults.newPerDay,
            defaults.writingTriggerMissDays,
            defaults.recognitionPromotionPasses,
            defaults.realDueReviewsToMove,
            active,
            suspended,
            tagged,
            RecordsBase.parseImportTags(tags),
            weak,
            defaults.importWeakFsrsDifficultyThreshold,
            defaults.importWeakLapsesThreshold,
            minMatching,
            browserQueryCards,
            browserQuery,
        )
    }

    private class FakeGateway(
        private val snapshot: RecordsSyncModels.CollectionSnapshot,
        private val removal: AnkiDroidGateway.RemovalSummary,
    ) : CollectionGateway {
        override fun readCollection(settings: RecordsSyncModels.Settings): RecordsSyncModels.CollectionSnapshot {
            return snapshot
        }

        override fun removeArchivedSuspendedCards(snapshot: RecordsSyncModels.CollectionSnapshot): AnkiDroidGateway.RemovalSummary {
            return removal
        }
    }

    private class FailingGateway : CollectionGateway {
        override fun readCollection(settings: RecordsSyncModels.Settings): RecordsSyncModels.CollectionSnapshot {
            throw AnkiDroidGateway.SyncFailure.permanent("Kiku note type was not found in AnkiDroid.")
        }

        override fun removeArchivedSuspendedCards(snapshot: RecordsSyncModels.CollectionSnapshot): AnkiDroidGateway.RemovalSummary {
            return AnkiDroidGateway.RemovalSummary(0, 0, 0, "")
        }
    }

    private class RetryableGateway : CollectionGateway {
        override fun readCollection(settings: RecordsSyncModels.Settings): RecordsSyncModels.CollectionSnapshot {
            throw AnkiDroidGateway.SyncFailure.retryable("AnkiDroid returned no configured note cursor.")
        }

        override fun removeArchivedSuspendedCards(snapshot: RecordsSyncModels.CollectionSnapshot): AnkiDroidGateway.RemovalSummary {
            return AnkiDroidGateway.RemovalSummary(0, 0, 0, "")
        }
    }

    private class RuntimeFailingGateway : CollectionGateway {
        override fun readCollection(settings: RecordsSyncModels.Settings): RecordsSyncModels.CollectionSnapshot {
            throw IllegalStateException("sync crashed")
        }

        override fun removeArchivedSuspendedCards(snapshot: RecordsSyncModels.CollectionSnapshot): AnkiDroidGateway.RemovalSummary {
            return AnkiDroidGateway.RemovalSummary(0, 0, 0, "")
        }
    }

    private class BlockingGateway(
        private val snapshot: RecordsSyncModels.CollectionSnapshot,
        private val removal: AnkiDroidGateway.RemovalSummary,
    ) : CollectionGateway {
        private val started = CountDownLatch(1)
        private val releaseLatch = CountDownLatch(1)

        override fun readCollection(settings: RecordsSyncModels.Settings): RecordsSyncModels.CollectionSnapshot {
            started.countDown()
            try {
                if (!releaseLatch.await(10L, TimeUnit.SECONDS)) {
                    throw AnkiDroidGateway.SyncFailure.retryable("Timed out waiting for test release.")
                }
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt()
                throw AnkiDroidGateway.SyncFailure.retryable("Interrupted while waiting for test release.", error)
            }
            return snapshot
        }

        override fun removeArchivedSuspendedCards(snapshot: RecordsSyncModels.CollectionSnapshot): AnkiDroidGateway.RemovalSummary {
            return removal
        }

        fun awaitStarted(): Boolean {
            return started.await(10L, TimeUnit.SECONDS)
        }

        fun release() {
            releaseLatch.countDown()
        }
    }

    private class ProgressGateway(
        private val snapshot: RecordsSyncModels.CollectionSnapshot,
        private val removal: AnkiDroidGateway.RemovalSummary,
    ) : CollectionGateway {
        override fun readCollection(settings: RecordsSyncModels.Settings): RecordsSyncModels.CollectionSnapshot {
            return snapshot
        }

        override fun readCollection(
            settings: RecordsSyncModels.Settings,
            progress: SyncProgress.Listener?,
        ): RecordsSyncModels.CollectionSnapshot {
            val listener = progress ?: SyncProgress.NONE
            listener.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.FINDING_NOTE_TYPE))
            listener.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.READING_NOTES))
            listener.onSyncProgress(SyncProgress.cardsScanned(0, snapshot.cards.size))
            for (i in snapshot.cards.indices) {
                listener.onSyncProgress(SyncProgress.cardsScanned(i + 1, snapshot.cards.size))
            }
            return snapshot
        }

        override fun removeArchivedSuspendedCards(snapshot: RecordsSyncModels.CollectionSnapshot): AnkiDroidGateway.RemovalSummary {
            return removal
        }

        override fun removeArchivedSuspendedCards(
            snapshot: RecordsSyncModels.CollectionSnapshot,
            progress: SyncProgress.Listener?,
        ): AnkiDroidGateway.RemovalSummary {
            val listener = progress ?: SyncProgress.NONE
            listener.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.ARCHIVING_IMPORTED_CARDS))
            return removal
        }
    }

    private class RecordingGateway(
        private val snapshot: RecordsSyncModels.CollectionSnapshot,
        private val removal: AnkiDroidGateway.RemovalSummary,
    ) : CollectionGateway {
        var selectedSuspendedImports: List<RecordsImportModels.SuspendedImport> = emptyList()

        override fun readCollection(settings: RecordsSyncModels.Settings): RecordsSyncModels.CollectionSnapshot {
            return snapshot
        }

        override fun removeArchivedSuspendedCards(snapshot: RecordsSyncModels.CollectionSnapshot): AnkiDroidGateway.RemovalSummary {
            return removal
        }

        override fun removeArchivedSuspendedCards(
            snapshot: RecordsSyncModels.CollectionSnapshot,
            selectedSuspendedImports: List<RecordsImportModels.SuspendedImport>?,
            progress: SyncProgress.Listener?,
        ): AnkiDroidGateway.RemovalSummary {
            this.selectedSuspendedImports = selectedSuspendedImports ?: emptyList()
            val listener = progress ?: SyncProgress.NONE
            listener.onSyncProgress(SyncProgress.atStage(SyncProgress.Stage.ARCHIVING_IMPORTED_CARDS))
            return removal
        }
    }
}
