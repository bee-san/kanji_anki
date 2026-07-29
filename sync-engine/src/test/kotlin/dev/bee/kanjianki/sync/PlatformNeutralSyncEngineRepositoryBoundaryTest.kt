package dev.bee.kanjianki.sync

import dev.bee.kanjianki.syncapi.ArchiveTagSummary
import dev.bee.kanjianki.syncapi.CollectionCancellation
import dev.bee.kanjianki.syncapi.CollectionProgressListener
import dev.bee.kanjianki.syncapi.CollectionGateway
import dev.bee.kanjianki.syncapi.CollectionProviderKind
import dev.bee.kanjianki.syncapi.ProviderCollectionSnapshot
import dev.bee.kanjianki.application.ManualSyncQueuePlanner
import dev.bee.kanjianki.application.SyncUseCases
import dev.bee.kanjianki.core.JitenKanjiRanks
import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.core.ReadingExposureModels
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SimilarKanjiIndex
import dev.bee.kanjianki.data.AdaptiveWorkloadSnapshot
import dev.bee.kanjianki.data.SettingsSnapshot
import dev.bee.kanjianki.data.StoreResult
import dev.bee.kanjianki.data.StoredSyncState
import dev.bee.kanjianki.data.SyncPublicationResult
import dev.bee.kanjianki.data.fakes.FakeSettingsRepository
import dev.bee.kanjianki.data.fakes.FakeStudyRepository
import dev.bee.kanjianki.data.fakes.FakeSyncRepository
import dev.bee.kanjianki.syncapi.SourceBindingReason
import dev.bee.kanjianki.syncapi.RedactedSourceIdentityEvidence
import dev.bee.kanjianki.platform.AppClock
import dev.bee.kanjianki.platform.AppLogger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
class PlatformNeutralSyncEngineRepositoryBoundaryTest {
    @Test
    fun committedPublicationRunsExactlyOnceBeforeEveryEffect() {
        assertFalse(PlatformNeutralSyncEngine.isRunning())
        val events = mutableListOf<String>()
        val repositories = Repositories()
        val gateway = RecordingGateway(events)
        var bindingChecks = 0
        val sourceBindingGate = SyncSourceBindingGate { _, _, _ ->
            bindingChecks += 1
            events += if (bindingChecks == 1) "binding-publication" else "binding-archive"
        }
        repositories.sync.publishHandler = {
            events += "publish"
            assertEquals(listOf("binding-publication", "publish"), events)
            StoreResult.ok(SyncPublicationResult(7L, emptyList(), adaptivePlan()))
        }
        repositories.sync.removalHandler = { syncId, message ->
            assertEquals(7L, syncId)
            assertEquals("archived", message)
            events += "persist-removal"
            StoreResult.ok(Unit)
        }
        val engine = repositories.engine(
            gateway = gateway,
            effects = SyncPostCommitEffects(
                Runnable { events += "reminder" },
                Runnable { events += "widget" },
            ),
            sourceBindingGate = sourceBindingGate,
        )
        engine.committedStudySummaryProvider = { _, _ ->
            events += "summary"
            PlatformNeutralSyncEngine.CommittedStudySummary(0, adaptivePlan())
        }

        val result = engine.run()

        assertTrue(result.success)
        assertFalse(PlatformNeutralSyncEngine.isRunning())
        assertEquals(1, repositories.sync.publications.size)
        assertTrue(repositories.sync.failures.isEmpty())
        assertEquals(1, gateway.archiveCalls)
        assertEquals(
            listOf(
                "binding-publication",
                "publish",
                "reminder",
                "widget",
                "binding-archive",
                "archive",
                "persist-removal",
                "summary",
            ),
            events,
        )
    }

    @Test
    fun processWideRunningGuardRejectsReentrantRunAndResetsAfterCompletion() {
        val repositories = Repositories()
        val gateway = RecordingGateway(mutableListOf())
        repositories.sync.publishHandler = {
            StoreResult.ok(SyncPublicationResult(7L, emptyList(), adaptivePlan()))
        }
        lateinit var engine: PlatformNeutralSyncEngine
        var overlappingResult: PlatformNeutralSyncEngine.SyncResult? = null
        engine = repositories.engine(
            gateway = gateway,
            effects = noOpEffects(),
            sourceBindingGate = SyncSourceBindingGate { _, _, _ ->
                assertTrue(PlatformNeutralSyncEngine.Companion.isRunning())
                if (overlappingResult == null) {
                    overlappingResult = engine.run()
                }
            },
        )
        engine.committedStudySummaryProvider = { _, _ ->
            PlatformNeutralSyncEngine.CommittedStudySummary(0, adaptivePlan())
        }

        val result = engine.run()
        val overlapping = checkNotNull(overlappingResult)

        assertTrue(result.success)
        assertFalse(overlapping.success)
        assertTrue(overlapping.skipped)
        assertTrue(overlapping.retryable)
        assertEquals("Sync already running.", overlapping.message)
        assertFalse(PlatformNeutralSyncEngine.Companion.isRunning())
    }

    @Test
    fun failedPublicationRecordsFailureWithoutRunningAnyEffect() {
        val events = mutableListOf<String>()
        val repositories = Repositories()
        val gateway = RecordingGateway(events)
        repositories.sync.publishHandler = {
            events += "publish"
            StoreResult.transient(IllegalStateException("database locked"))
        }
        val engine = repositories.engine(
            gateway = gateway,
            effects = SyncPostCommitEffects(
                Runnable { events += "reminder" },
                Runnable { events += "widget" },
            ),
        )
        engine.committedStudySummaryProvider = { _, _ ->
            events += "summary"
            throw AssertionError("summary must not run after failed publication")
        }
        engine.removalMessagePersister = { _, _ ->
            events += "persist-removal"
        }

        val result = engine.run()

        assertFalse(result.success)
        assertEquals(1, repositories.sync.publications.size)
        assertEquals(1, repositories.sync.failures.size)
        assertEquals("unexpected", repositories.sync.failures.single().errorCode)
        assertEquals(0, gateway.archiveCalls)
        assertEquals(listOf("publish"), events)
    }

    @Test
    fun bindingFailureStopsBeforePublicationAndReportsRecoveryReason() {
        val events = mutableListOf<String>()
        val repositories = Repositories()
        val gateway = RecordingGateway(events)
        val evidence = SourceBindingEvidence(
            candidate = RedactedSourceIdentityEvidence(
                CollectionProviderKind.ANKIDROID,
                noteIdSampleSize = 20,
                cardIdSampleSize = 21,
            ),
            priorNoteSampleSize = 18,
            priorCardSampleSize = 19,
        )
        val engine = repositories.engine(
            gateway = gateway,
            effects = noOpEffects(),
            sourceBindingGate = SyncSourceBindingGate { _, _, _ ->
                events += "binding"
                throw SourceBindingFailure(
                    SourceBindingReason.UNKNOWN_ORIGIN,
                    "Source recovery is required.",
                    evidence,
                )
            },
        )

        val result = engine.run()

        assertFalse(result.success)
        assertEquals(SourceBindingReason.UNKNOWN_ORIGIN, result.sourceBindingReason)
        assertEquals(evidence, result.sourceBindingEvidence)
        assertTrue(repositories.sync.publications.isEmpty())
        assertEquals(0, gateway.archiveCalls)
        assertEquals(listOf("binding"), events)
        assertEquals(
            "source_binding_unknown_origin",
            repositories.sync.failures.single().errorCode,
        )
    }

    @Test
    fun archiveWriteIsBlockedWhenThePostCommitBindingRecheckFails() {
        val events = mutableListOf<String>()
        val repositories = Repositories()
        val gateway = RecordingGateway(events)
        repositories.sync.publishHandler = {
            events += "publish"
            StoreResult.ok(SyncPublicationResult(7L, emptyList(), adaptivePlan()))
        }
        var checks = 0
        val engine = repositories.engine(
            gateway = gateway,
            effects = noOpEffects(),
            sourceBindingGate = SyncSourceBindingGate { _, _, _ ->
                checks += 1
                events += "binding-$checks"
                if (checks == 2) {
                    throw SourceBindingFailure(
                        SourceBindingReason.SOURCE_KEY_CHANGED,
                        "Source changed before provider write.",
                    )
                }
            },
        )
        engine.committedStudySummaryProvider = { _, _ ->
            PlatformNeutralSyncEngine.CommittedStudySummary(0, adaptivePlan())
        }

        val result = engine.run()

        assertTrue(result.success)
        assertEquals(0, gateway.archiveCalls)
        assertEquals(listOf("binding-1", "publish", "binding-2"), events)
    }

    @Test
    fun preCancelledRunStopsBeforeCallingTheProvider() {
        val repositories = Repositories()
        var providerReads = 0
        val gateway = object : CollectionGateway {
            override fun readCollection(
                settings: RecordsSyncModels.Settings,
            ): RecordsSyncModels.CollectionSnapshot {
                providerReads += 1
                error("pre-cancelled sync must not call the provider")
            }

            override fun removeArchivedSuspendedCards(
                snapshot: RecordsSyncModels.CollectionSnapshot,
            ): ArchiveTagSummary = error("not reached")
        }

        val result = repositories.engine(
            gateway = gateway,
            effects = noOpEffects(),
            cancellation = SyncCancellation { true },
        ).run()

        assertFalse(result.success)
        assertTrue(result.retryable)
        assertEquals(0, providerReads)
        assertTrue(repositories.sync.publications.isEmpty())
        assertEquals("retryable", repositories.sync.failures.single().errorCode)
    }

    @Test
    fun cancellationIsForwardedAndRecheckedAfterTheProviderStage() {
        val repositories = Repositories()
        var stopped = false
        val expectedCancellation = SyncCancellation { stopped }
        val gateway = object : CollectionGateway {
            override fun readCollection(
                settings: RecordsSyncModels.Settings,
            ): RecordsSyncModels.CollectionSnapshot = error("three-argument read required")

            override fun readProviderCollection(
                settings: RecordsSyncModels.Settings,
                progress: CollectionProgressListener,
                cancellation: CollectionCancellation,
            ): ProviderCollectionSnapshot {
                assertSame(expectedCancellation, cancellation)
                stopped = true
                return ProviderCollectionSnapshot(
                    RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
                    emptySet(),
                    null,
                )
            }

            override fun removeArchivedSuspendedCards(
                snapshot: RecordsSyncModels.CollectionSnapshot,
            ): ArchiveTagSummary = error("not reached")
        }

        val result = repositories.engine(
            gateway = gateway,
            effects = noOpEffects(),
            cancellation = expectedCancellation,
        ).run()

        assertFalse(result.success)
        assertTrue(result.retryable)
        assertTrue(repositories.sync.publications.isEmpty())
        assertEquals("retryable", repositories.sync.failures.single().errorCode)
    }

    @Test
    fun loggerFailureCannotChangeACommittedResult() {
        val events = mutableListOf<String>()
        val repositories = Repositories()
        repositories.sync.publishHandler = {
            StoreResult.ok(SyncPublicationResult(7L, emptyList(), adaptivePlan()))
        }
        val engine = repositories.engine(
            gateway = RecordingGateway(events),
            effects = noOpEffects(),
            logger = AppLogger { throw IllegalStateException("logger unavailable") },
        )
        engine.committedStudySummaryProvider = { _, _ ->
            PlatformNeutralSyncEngine.CommittedStudySummary(0, adaptivePlan())
        }

        assertTrue(engine.run().success)
    }

    private class Repositories {
        val sync = FakeSyncRepository().apply {
            storedStateHandler = {
                StoreResult.ok(
                    StoredSyncState(
                        hasCollectionMirror = false,
                        suspendedImports = emptyList(),
                        unrestoredSuspendedArchiveCardIds = emptySet(),
                        studyItems = emptyList(),
                        latestSuccessfulSyncAtMillis = null,
                    ),
                )
            }
        }
        private val study = FakeStudyRepository()
        private val settings = FakeSettingsRepository()

        fun engine(
            gateway: CollectionGateway,
            effects: SyncPostCommitEffects,
            sourceBindingGate: SyncSourceBindingGate = SyncSourceBindingGate.ALLOW_ALL,
            cancellation: SyncCancellation = SyncCancellation.NONE,
            logger: AppLogger = AppLogger.NONE,
        ): PlatformNeutralSyncEngine =
            PlatformNeutralSyncEngine(
                syncUseCases = SyncUseCases(sync, study, settings),
                gateway = gateway,
                settingsSnapshot = settingsSnapshot(),
                progress = SyncProgress.NONE,
                clock = AppClock { NOW },
                assetReaders = EmptyAssets,
                queuePlannerFactory = ::ManualSyncQueuePlanner,
                postCommitEffects = effects,
                repairedWriteBackAuthorized = false,
                confirmedRepairedNoteIds = null,
                sourceBindingGate = sourceBindingGate,
                cancellation = cancellation,
                logger = logger,
            )
    }

    private class RecordingGateway(
        private val events: MutableList<String>,
    ) : CollectionGateway {
        var archiveCalls = 0

        override fun readCollection(
            settings: RecordsSyncModels.Settings,
        ): RecordsSyncModels.CollectionSnapshot =
            RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList())

        override fun removeArchivedSuspendedCards(
            snapshot: RecordsSyncModels.CollectionSnapshot,
        ): ArchiveTagSummary {
            archiveCalls += 1
            events += "archive"
            return ArchiveTagSummary(0, 0, 0, "archived")
        }
    }

    private object EmptyAssets : SyncAssetReaders {
        override fun loadRanks(): JitenKanjiRanks = JitenKanjiRanks.empty()

        override fun loadDictionary() = null

        override fun loadSimilarKanjiIndex(): SimilarKanjiIndex = SimilarKanjiIndex.empty()

        override fun loadReadingExposure(): ReadingExposureModels.ExposureIndex =
            ReadingExposureModels.ExposureIndex.EMPTY
    }

    private companion object {
        const val NOW = 2_000L

        fun adaptivePlan() = RecordsSchedulerModels.AdaptiveLoadPlan(
            100,
            0,
            0,
            emptyList(),
            0,
            true,
            "all",
        )

        fun settingsSnapshot() = SettingsSnapshot(
            sync = RecordsSyncModels.Settings.kikuDefaults(),
            tagRepairedCards = false,
            adaptiveWorkload = AdaptiveWorkloadSnapshot(100, 25, "all"),
            studyAheadMinutes = 0,
            studyLadder = RecordsBase.StudyLadderSettings.defaults(),
            schedulerParameters = RecordsSchedulerModels.SchedulerParameters.defaults(),
            schedulerFsrsWeights = null,
            learningSteps = RecordsSchedulerModels.LearningStepSettings.defaults(),
            themeChoice = KaniThemeChoice.SYSTEM,
            fsrsPersonalizationEnabled = false,
            fsrsFitSummaryJson = "",
        )

        fun noOpEffects() = SyncPostCommitEffects(Runnable { }, Runnable { })
    }
}
