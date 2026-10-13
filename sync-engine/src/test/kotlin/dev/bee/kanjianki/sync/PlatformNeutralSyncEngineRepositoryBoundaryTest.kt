package dev.bee.kanjianki.sync

import dev.bee.kanjianki.syncapi.ArchiveTagSummary
import dev.bee.kanjianki.syncapi.CollectionCancellation
import dev.bee.kanjianki.syncapi.CollectionCapability
import dev.bee.kanjianki.syncapi.CollectionFailure
import dev.bee.kanjianki.syncapi.CollectionFailureKind
import dev.bee.kanjianki.syncapi.CollectionProgressListener
import dev.bee.kanjianki.syncapi.CollectionGateway
import dev.bee.kanjianki.syncapi.CollectionProviderKind
import dev.bee.kanjianki.syncapi.ProviderCollectionSnapshot
import dev.bee.kanjianki.syncapi.RepairedTagSummary
import dev.bee.kanjianki.application.ManualSyncQueuePlanner
import dev.bee.kanjianki.application.SyncUseCases
import dev.bee.kanjianki.core.AdmissionEvidencePolicy
import dev.bee.kanjianki.core.JitenKanjiRanks
import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.core.ReadingExposureModels
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.RepairedWriteBackPolicy
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
import org.junit.Assert.assertNull
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
    fun cancellationAtEveryInjectedPreCommitBoundaryPreventsPublication() {
        val boundaries = listOf("binding", "ranks", "similar", "dictionary", "reading", "progress")
        for (boundary in boundaries) {
            var stopped = false
            val repositories = Repositories()
            val assets = CancellableAssets(boundary) { stopped = true }
            val result = repositories.engine(
                gateway = RecordingGateway(mutableListOf()),
                effects = noOpEffects(),
                sourceBindingGate = SyncSourceBindingGate { _, _, _ ->
                    if (boundary == "binding") stopped = true
                },
                cancellation = SyncCancellation { stopped },
                assets = assets,
                progress = SyncProgress.Listener {
                    if (
                        boundary == "progress" &&
                        it.stage == SyncProgress.Stage.BUILDING_PRACTICE_QUEUE
                    ) {
                        stopped = true
                    }
                },
            ).run()

            assertFalse("boundary=$boundary", result.success)
            assertTrue("boundary=$boundary", result.retryable)
            assertTrue("boundary=$boundary", repositories.sync.publications.isEmpty())
            assertEquals("boundary=$boundary", "retryable", repositories.sync.failures.single().errorCode)
        }
    }

    @Test
    fun cancellationAfterPublicationRetainsCommittedSuccess() {
        var stopped = false
        val repositories = Repositories()
        val gateway = RecordingGateway(mutableListOf())
        repositories.sync.publishHandler = {
            stopped = true
            StoreResult.ok(SyncPublicationResult(7L, emptyList(), adaptivePlan()))
        }
        val engine = repositories.engine(
            gateway = gateway,
            effects = noOpEffects(),
            cancellation = SyncCancellation { stopped },
        )
        engine.committedStudySummaryProvider = { _, _ ->
            PlatformNeutralSyncEngine.CommittedStudySummary(0, adaptivePlan())
        }

        val result = engine.run()

        assertTrue(result.success)
        assertEquals(0, gateway.archiveCalls)
        assertTrue(repositories.sync.failures.isEmpty())
        assertTrue(result.message.orEmpty().contains("retry on the next sync"))
    }

    @Test
    fun providerFailureRetryabilityAndPersistenceClassificationRemainExplicit() {
        val cases = listOf(
            Triple(
                CollectionFailure(
                    CollectionFailureKind.INVALID_CONFIGURATION,
                    "bad configuration",
                ),
                false,
                "config_error" to "permanent",
            ),
            Triple(
                CollectionFailure(
                    CollectionFailureKind.TRANSIENT,
                    "provider busy",
                ),
                true,
                "retryable_error" to "retryable",
            ),
        )

        for ((failure, retryable, expectedPersistence) in cases) {
            val repositories = Repositories()
            val result = repositories.engine(
                gateway = ThrowingGateway(failure),
                effects = noOpEffects(),
            ).run()

            assertFalse(result.success)
            assertEquals(retryable, result.retryable)
            val persisted = repositories.sync.failures.single()
            assertEquals(expectedPersistence.first, persisted.status)
            assertEquals(expectedPersistence.second, persisted.errorCode)
        }
    }

    @Test
    fun unexpectedExceptionStaysTerminalWhileRetainingLegacyHistoryLabel() {
        val repositories = Repositories()

        val result = repositories.engine(
            gateway = ThrowingGateway(IllegalStateException("private failure")),
            effects = noOpEffects(),
        ).run()

        assertFalse(result.success)
        assertFalse(result.retryable)
        val persisted = repositories.sync.failures.single()
        assertEquals("retryable_error", persisted.status)
        assertEquals("unexpected", persisted.errorCode)
    }

    @Test
    fun errorsPropagateWithoutFailureHistoryAndReleaseTheRunningGuard() {
        val repositories = Repositories()
        try {
            repositories.engine(
                gateway = ThrowingGateway(OutOfMemoryError("heap")),
                effects = noOpEffects(),
            ).run()
            throw AssertionError("Error must propagate")
        } catch (expected: OutOfMemoryError) {
            assertEquals("heap", expected.message)
        }

        assertTrue(repositories.sync.failures.isEmpty())
        assertFalse(PlatformNeutralSyncEngine.isRunning())
    }

    @Test
    fun failureHistoryPersistenceCannotMaskTheOriginalFailure() {
        val repositories = Repositories()
        repositories.sync.failureHandler = {
            throw IllegalStateException("history unavailable")
        }

        val result = repositories.engine(
            gateway = ThrowingGateway(
                CollectionFailure(CollectionFailureKind.TRANSIENT, "provider unavailable"),
            ),
            effects = noOpEffects(),
        ).run()

        assertFalse(result.success)
        assertTrue(result.retryable)
        assertEquals("provider unavailable", result.message)
        assertEquals(1, repositories.sync.failures.size)
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

    @Test
    fun everyPostCommitFailureRetainsTheSingleSuccessfulPublication() {
        val repositories = Repositories()
        repositories.sync.publishHandler = {
            StoreResult.ok(SyncPublicationResult(7L, emptyList(), adaptivePlan()))
        }
        val engine = repositories.engine(
            gateway = ThrowingArchiveGateway(),
            effects = SyncPostCommitEffects(
                Runnable { throw IllegalStateException("private reminder failure") },
                Runnable { throw IllegalStateException("private widget failure") },
            ),
            logger = AppLogger { throw IllegalStateException("private logger failure") },
        )
        engine.removalMessagePersister = { _, _ ->
            throw IllegalStateException("private persistence failure")
        }
        engine.committedStudySummaryProvider = { _, _ ->
            throw IllegalStateException("private summary failure")
        }

        val result = engine.run()

        assertTrue(result.success)
        assertEquals(1, repositories.sync.publications.size)
        assertTrue(repositories.sync.failures.isEmpty())
        assertEquals(0, result.studyReadyCount)
        assertTrue(result.message.orEmpty().contains("retry on the next sync"))
        assertFalse(result.message.orEmpty().contains("private"))
    }

    @Test
    fun repairedWriteIsBlockedByTheSamePostPublicationBindingGate() {
        val events = mutableListOf<String>()
        val repositories = Repositories()
        val gateway = RecordingRepairedGateway(events)
        repositories.sync.publishHandler = {
            events += "publish"
            StoreResult.ok(SyncPublicationResult(7L, emptyList(), adaptivePlan()))
        }
        var bindingChecks = 0
        val engine = repositories.engine(
            gateway = gateway,
            effects = noOpEffects(),
            sourceBindingGate = SyncSourceBindingGate { _, _, _ ->
                bindingChecks += 1
                events += "binding-$bindingChecks"
                if (bindingChecks == 3) {
                    throw SourceBindingFailure(
                        SourceBindingReason.SOURCE_KEY_CHANGED,
                        "Source changed before repaired-note tagging.",
                    )
                }
            },
            snapshot = settingsSnapshot(tagRepairedCards = true),
            repairedWriteBackAuthorized = true,
        )
        engine.repairedProposalProvider = { _, _ ->
            RepairedWriteBackPolicy.Proposal(
                noteIdsToTag = setOf(1L),
                cardIdsByNote = mapOf(1L to setOf(10L)),
                kanjiByNote = mapOf(1L to setOf("徴")),
                repairedKanji = listOf("徴"),
                candidateSourceCount = 1,
                rejectedCardCount = 0,
            )
        }
        engine.repairedWriteBackRecorder = { _, tagged, _, _ ->
            assertTrue(tagged.isEmpty())
            emptyList()
        }
        engine.committedStudySummaryProvider = { _, _ ->
            PlatformNeutralSyncEngine.CommittedStudySummary(0, adaptivePlan())
        }

        val result = engine.run()

        assertTrue(result.success)
        assertEquals(0, gateway.tagCalls)
        assertEquals(
            listOf("binding-1", "publish", "binding-2", "archive", "binding-3"),
            events,
        )
        assertTrue(result.message.orEmpty().contains("retry on the next sync"))
        assertTrue(repositories.sync.failures.isEmpty())
    }

    @Test
    fun fsrsCapabilityUsesProviderMemoryForAdmissionSeed() {
        val publication = runCapabilityFixture(
            setOf(
                CollectionCapability.READ_COLLECTION,
                CollectionCapability.FSRS_MEMORY_STATE,
            ),
        )

        val card = publication.snapshot.cards.single()
        val example = publication.rows.single().examples.single()
        val seed = AdmissionEvidencePolicy.seedFor(
            publication.rows.single(),
            RecordsBase.StudyLadderSettings.defaults(),
            publication.settings,
        )
        assertEquals(80.0, card.fsrsStability!!, 0.0)
        assertEquals(9.0, example.fsrsDifficulty!!, 0.0)
        assertEquals(30, example.intervalDays)
        assertEquals(3, example.lapses)
        assertEquals(80.0, seed.stability, 0.0)
        assertEquals(9.0, seed.difficulty, 0.0)
    }

    @Test
    fun absentFsrsCapabilityUsesIntervalAndLapseAdmissionFallback() {
        val publication = runCapabilityFixture(setOf(CollectionCapability.READ_COLLECTION))

        val card = publication.snapshot.cards.single()
        val example = publication.rows.single().examples.single()
        val seed = AdmissionEvidencePolicy.seedFor(
            publication.rows.single(),
            RecordsBase.StudyLadderSettings.defaults(),
            publication.settings,
        )
        assertNull(card.fsrsStability)
        assertNull(card.fsrsDifficulty)
        assertNull(card.fsrsRetrievability)
        assertNull(example.fsrsStability)
        assertNull(example.fsrsDifficulty)
        assertNull(example.fsrsRetrievability)
        assertEquals(30, example.intervalDays)
        assertEquals(3, example.lapses)
        assertEquals(30.0, seed.stability, 0.0)
        assertEquals(8.0, seed.difficulty, 0.0)
    }

    private fun runCapabilityFixture(
        capabilities: Set<CollectionCapability>,
    ): dev.bee.kanjianki.data.SyncPublicationCommand {
        val repositories = Repositories()
        repositories.sync.publishHandler = {
            StoreResult.ok(SyncPublicationResult(7L, it.rows, adaptivePlan()))
        }
        val engine = repositories.engine(
            gateway = CapabilityGateway(capabilities),
            effects = noOpEffects(),
        )
        engine.committedStudySummaryProvider = { _, _ ->
            PlatformNeutralSyncEngine.CommittedStudySummary(0, adaptivePlan())
        }

        assertTrue(engine.run().success)
        return repositories.sync.publications.single()
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
            assets: SyncAssetReaders = EmptyAssets,
            progress: SyncProgress.Listener = SyncProgress.NONE,
            snapshot: SettingsSnapshot = settingsSnapshot(),
            repairedWriteBackAuthorized: Boolean = false,
        ): PlatformNeutralSyncEngine =
            PlatformNeutralSyncEngine(
                syncUseCases = SyncUseCases(sync, study, settings),
                gateway = gateway,
                settingsSnapshot = snapshot,
                progress = progress,
                clock = AppClock { NOW },
                assetReaders = assets,
                queuePlannerFactory = ::ManualSyncQueuePlanner,
                postCommitEffects = effects,
                repairedWriteBackAuthorized = repairedWriteBackAuthorized,
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

    private class ThrowingGateway(
        private val failure: Throwable,
    ) : CollectionGateway {
        override fun readCollection(
            settings: RecordsSyncModels.Settings,
        ): RecordsSyncModels.CollectionSnapshot = throw failure

        override fun removeArchivedSuspendedCards(
            snapshot: RecordsSyncModels.CollectionSnapshot,
        ): ArchiveTagSummary = error("not reached")
    }

    private class ThrowingArchiveGateway : CollectionGateway {
        override fun readCollection(
            settings: RecordsSyncModels.Settings,
        ): RecordsSyncModels.CollectionSnapshot =
            RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList())

        override fun removeArchivedSuspendedCards(
            snapshot: RecordsSyncModels.CollectionSnapshot,
        ): ArchiveTagSummary = throw IllegalStateException("private archive failure")
    }

    private class RecordingRepairedGateway(
        private val events: MutableList<String>,
    ) : CollectionGateway {
        var tagCalls = 0

        override fun readCollection(
            settings: RecordsSyncModels.Settings,
        ): RecordsSyncModels.CollectionSnapshot =
            RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList())

        override fun removeArchivedSuspendedCards(
            snapshot: RecordsSyncModels.CollectionSnapshot,
        ): ArchiveTagSummary {
            events += "archive"
            return ArchiveTagSummary(0, 0, 0, "")
        }

        override fun tagRepairedNotes(
            noteIds: Set<Long>,
            progress: CollectionProgressListener,
        ): RepairedTagSummary {
            tagCalls += 1
            events += "tag"
            return RepairedTagSummary(noteIds, noteIds, emptySet(), "")
        }
    }

    private class CapabilityGateway(
        private val capabilities: Set<CollectionCapability>,
    ) : CollectionGateway {
        override fun readCollection(
            settings: RecordsSyncModels.Settings,
        ): RecordsSyncModels.CollectionSnapshot = CAPABILITY_SNAPSHOT

        override fun readProviderCollection(
            settings: RecordsSyncModels.Settings,
            progress: CollectionProgressListener,
            cancellation: CollectionCancellation,
        ): ProviderCollectionSnapshot =
            ProviderCollectionSnapshot(CAPABILITY_SNAPSHOT, capabilities, null)

        override fun removeArchivedSuspendedCards(
            snapshot: RecordsSyncModels.CollectionSnapshot,
        ): ArchiveTagSummary = ArchiveTagSummary(0, 0, 0, "")
    }

    private class CancellableAssets(
        private val boundary: String,
        private val cancel: () -> Unit,
    ) : SyncAssetReaders {
        override fun loadRanks(): JitenKanjiRanks {
            if (boundary == "ranks") cancel()
            return JitenKanjiRanks.empty()
        }

        override fun loadDictionary(): Nothing? {
            if (boundary == "dictionary") cancel()
            return null
        }

        override fun loadSimilarKanjiIndex(): SimilarKanjiIndex {
            if (boundary == "similar") cancel()
            return SimilarKanjiIndex.empty()
        }

        override fun loadReadingExposure(): ReadingExposureModels.ExposureIndex {
            if (boundary == "reading") cancel()
            return ReadingExposureModels.ExposureIndex.EMPTY
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

        val CAPABILITY_SNAPSHOT = RecordsSyncModels.CollectionSnapshot(
            listOf(
                RecordsSyncModels.Note(
                    101L,
                    7L,
                    "Kiku",
                    mapOf(
                        "Expression" to "橋",
                        "ExpressionReading" to "はし",
                        "MainDefinition" to "bridge",
                        "Sentence" to "橋を渡る。",
                    ),
                    emptyList(),
                ),
            ),
            listOf(
                RecordsSyncModels.Card(
                    201L,
                    101L,
                    0,
                    "Deck",
                    2,
                    2,
                    0,
                    30,
                    10,
                    3,
                    false,
                    80.0,
                    9.0,
                    0.2,
                ),
            ),
        )

        fun adaptivePlan() = RecordsSchedulerModels.AdaptiveLoadPlan(
            100,
            0,
            0,
            emptyList(),
            0,
            true,
            "all",
        )

        fun settingsSnapshot(tagRepairedCards: Boolean = false) = SettingsSnapshot(
            sync = RecordsSyncModels.Settings.kikuDefaults(),
            tagRepairedCards = tagRepairedCards,
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
