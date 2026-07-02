package dev.bee.kanjianki.anki

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.sync.ManualSyncEngine
import dev.bee.kanjianki.sync.SyncProgress
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import java.lang.reflect.Constructor
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class AnkiDroidGatewayProviderInstrumentedTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore

    companion object {
        @JvmStatic
        @BeforeClass
        fun waitForPackageInstallToSettle() {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        }
    }

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase("kanji_anki_simple.db")
        store = LocalStore(context)
        resetProvider()
    }

    @After
    fun tearDown() {
        if (::store.isInitialized) {
            store.close()
        }
        if (::context.isInitialized) {
            context.deleteDatabase("kanji_anki_simple.db")
            resetProvider()
        }
    }

    @Test
    fun readsKikuCollectionWithBulkCardsQuery() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)

        val snapshot = gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults())

        assertEquals(2, snapshot.notes.size)
        assertEquals(2, snapshot.cards.size)
        assertEquals(2, snapshot.cards[0].queue)
        assertEquals(42, snapshot.cards[0].intervalDays)
        assertEquals(80, snapshot.cards[0].reps)
        assertEquals(3, snapshot.cards[0].lapses)
        assertClose(12.5, snapshot.cards[0].fsrsStability)
        assertClose(7.0, snapshot.cards[0].fsrsDifficulty)
        assertClose(0.42, snapshot.cards[0].fsrsRetrievability)
        assertTrue(snapshot.cards[1].suspended)
        assertEquals(1, providerInt("topLevelCardsQueries"))
        assertEquals(0, providerInt("perNoteCardsQueries"))
        assertEquals(0, providerInt("explicitIdProjectionQueries"))
    }

    @Test
    fun bulkCardReaderPagesLargeNoteSetsWithoutPerNoteQueries() {
        val reader = AnkiDroidCardReader(context.contentResolver)
        val noteIds = linkedSetOf<Long>().apply {
            for (noteId in 1L..7000L) {
                add(noteId)
            }
        }

        val cards = reader.queryCardsByNote(
            FakeAnkiDroidProvider.AUTHORITY,
            RecordsSyncModels.Settings.kikuDefaults(),
            noteIds,
            SyncProgress.NONE,
        )

        assertEquals(7000, cards.size)
        assertEquals(1L, cards.first().noteId)
        assertEquals(7000L, cards.last().noteId)
        assertEquals(0, providerInt("perNoteCardsQueries"))
        assertTrue(providerInt("topLevelCardsQueries") < 100)
    }

    @Test
    fun listsAvailableNoteTypesWithKikuFirst() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)

        val noteTypes = gateway.noteTypes()

        assertEquals(2, noteTypes.size)
        assertEquals("Kiku", noteTypes[0].name)
        assertEquals("Custom Japanese", noteTypes[1].name)
        assertTrue(noteTypes[1].fields.contains("Front"))
        assertFalse(noteTypes[1].fields.contains("Expression"))
    }

    @Test
    fun permissionDeniedProviderStatusAndReadsFailBeforeProviderQueries() {
        val gateway = permissionedGateway("dev.bee.kanjianki.fake.READ_ANKI")

        val status = gateway.status()

        assertTrue(status.installed)
        assertFalse(status.permissionGranted)
        assertFalse(status.canSync)
        assertEquals(FakeAnkiDroidProvider.AUTHORITY, status.authority)
        assertEquals("dev.bee.kanjianki.fake.READ_ANKI", status.permission)
        assertTrue(status.message.contains("Allow AnkiDroid access"))
        assertPermissionFailure(gateway::noteTypes)
        assertPermissionFailure { gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults()) }
        assertEquals(0, providerInt("perNoteCardsQueries"))
    }

    @Test
    fun emptyPermissionProviderTargetReadsAsGranted() {
        val gateway = permissionedGateway("")

        val status = gateway.status()
        val noteTypes = gateway.noteTypes()

        assertTrue(status.permissionGranted)
        assertTrue(status.canSync)
        assertEquals(2, noteTypes.size)
    }

    @Test
    fun declaredPermissionProviderTargetReadsAsGranted() {
        val gateway = permissionedGateway("android.permission.INTERNET")

        val status = gateway.status()

        assertTrue(status.permissionGranted)
        assertTrue(status.canSync)
    }

    @Test
    fun providerInstalledHelperUsesModernAndLegacyPackageManagerPaths() {
        assertTrue(AnkiDroidGateway.providerInstalled(context.packageManager, FakeAnkiDroidProvider.AUTHORITY))
        assertTrue(
            AnkiDroidGateway.providerInstalled(
                context.packageManager,
                FakeAnkiDroidProvider.AUTHORITY,
                Build.VERSION_CODES.S_V2,
            )
        )
        assertTrue(
            AnkiDroidGateway.providerInstalled(
                context.packageManager,
                FakeAnkiDroidProvider.AUTHORITY,
                Build.VERSION_CODES.TIRAMISU,
            )
        )
        assertTrue(AnkiDroidGateway.providerInstalledBeforeTiramisu(context.packageManager, FakeAnkiDroidProvider.AUTHORITY))
        assertFalse(
            AnkiDroidGateway.providerInstalledBeforeTiramisu(
                context.packageManager,
                "dev.bee.kanjianki.missing.provider",
            )
        )
    }

    @Test
    fun nullProgressAndProviderTimeoutMapToRetryableFailure() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        context.contentResolver.call(providerUri(), "operationCanceledProviderFailure", null, null)

        try {
            gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults(), null)
            assertTrue("Expected timeout failure", false)
        } catch (error: AnkiDroidGateway.SyncFailure) {
            assertFalse(error.permanentFailure)
            assertTrue(error.message?.contains("Timed out while reading AnkiDroid") == true)
        }
    }

    @Test
    fun providerSecurityExceptionMapsToPermanentAccessFailure() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        context.contentResolver.call(providerUri(), "securityProviderFailure", null, null)

        try {
            gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults())
            assertTrue("Expected security failure", false)
        } catch (error: AnkiDroidGateway.SyncFailure) {
            assertTrue(error.permanentFailure)
            assertTrue(error.message?.contains("denied database access") == true)
        }
    }

    @Test
    fun nullModelCursorIsRetryableAndMissingConfiguredModelIsPermanent() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        context.contentResolver.call(providerUri(), "nullModelsCursor", null, null)

        try {
            gateway.noteTypes()
            assertTrue("Expected null model cursor failure", false)
        } catch (error: AnkiDroidGateway.SyncFailure) {
            assertFalse(error.permanentFailure)
            assertTrue(error.message?.contains("no note model cursor") == true)
        }

        resetProvider()
        try {
            gateway.readCollection(settingsWithModel("Missing Model"))
            assertTrue("Expected missing model failure", false)
        } catch (error: AnkiDroidGateway.SyncFailure) {
            assertTrue(error.permanentFailure)
            assertTrue(error.message?.contains("Missing Model note type was not found") == true)
        }
    }

    @Test
    fun invalidConfiguredFieldMappingIsPermanent() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)

        try {
            gateway.readCollection(settingsWithExpressionField("MissingExpression"))
            assertTrue("Expected field validation failure", false)
        } catch (error: AnkiDroidGateway.SyncFailure) {
            assertTrue(error.permanentFailure)
            assertTrue(error.message?.contains("missing required field MissingExpression") == true)
        }
    }

    @Test
    fun readsCustomNoteTypeWithMappedFields() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        val settings = customMappedSettings()

        val snapshot = gateway.readCollection(settings)

        assertEquals(2, snapshot.notes.size)
        assertEquals(2, snapshot.cards.size)
        assertEquals("Custom Japanese", snapshot.notes[0].modelName)
        assertEquals("確認", snapshot.notes[0].expression(settings))
        assertEquals("かくにん", snapshot.notes[0].reading(settings))
        assertEquals("confirmation", snapshot.notes[0].meaning(settings))
        assertEquals("確認した。", snapshot.notes[0].sentence(settings))
    }

    @Test
    fun manualSyncWorksAgainstFakeAnkiDroidProviderContract() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)

        val result = ManualSyncEngine(context, store, gateway, settings).run()

        assertTrue(result.success)
        assertEquals("success", store.latestSync()?.status)
        assertFalse(store.dashboardRows().isEmpty())
        val imports = store.suspendedImports()
        assertEquals(1, imports.size)
        assertEquals("箱", imports[0].kanji)
        assertTrue(result.message?.contains("tagged in AnkiDroid") == true)
        assertEquals(1, providerInt("topLevelCardsQueries"))
        assertEquals(0, providerInt("perNoteCardsQueries"))
        assertEquals(0, providerInt("explicitIdProjectionQueries"))
    }

    @Test
    fun manualSyncUsesCardQueueWhenAnkiDroidRejectsSuspendedSearch() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        context.contentResolver.call(providerUri(), "failSuspendedSearch", null, null)

        val result = ManualSyncEngine(context, store, gateway, settings).run()

        assertTrue(result.success)
        assertEquals("success", store.latestSync()?.status)
        assertFalse(store.dashboardRows().isEmpty())
        val imports = store.suspendedImports()
        assertEquals(1, imports.size)
        assertEquals("箱", imports[0].kanji)
        assertEquals(1, providerInt("topLevelCardsQueries"))
        assertEquals(0, providerInt("perNoteCardsQueries"))
        assertEquals(0, providerInt("explicitIdProjectionQueries"))
    }

    @Test
    fun providerCleanupLeavesExcludedSuspendedCardsUntagged() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        val snapshot = gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults())

        val summary = gateway.removeArchivedSuspendedCards(
            snapshot,
            emptyList<RecordsImportModels.SuspendedImport>(),
            SyncProgress.NONE,
        )

        assertEquals(0, summary.sourceCards)
        assertEquals("", FakeAnkiDroidProvider.suspendedTags)
    }

    @Test
    fun providerCleanupNoopsForMissingProviderAndEmptySnapshotOverloads() {
        val empty = RecordsSyncModels.CollectionSnapshot(emptyList<RecordsSyncModels.Note>(), emptyList<RecordsSyncModels.Card>())
        val missingProvider = AnkiDroidGateway.testProvider(context, "dev.bee.kanjianki.no_fake_anki")

        val missingSummary = missingProvider.removeArchivedSuspendedCards(
            snapshotWithCards(card(9990L, 999L, true)),
        )
        val emptySummary = AnkiDroidGateway
            .testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
            .removeArchivedSuspendedCards(empty, null as SyncProgress.Listener?)

        assertEquals(0, missingSummary.sourceCards)
        assertEquals("No provider removal attempted.", missingSummary.message)
        assertEquals(0, emptySummary.sourceCards)
        assertEquals("No provider removal attempted.", emptySummary.message)
    }

    @Test
    fun manualSyncFallsBackWhenBulkSchedulerProjectionIsUnsupported() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        context.contentResolver.call(providerUri(), "rejectSchedulerProjection", null, null)

        val result = ManualSyncEngine(context, store, gateway, settings).run()

        assertTrue(result.success)
        assertEquals("success", store.latestSync()?.status)
        assertFalse(store.dashboardRows().isEmpty())
        assertEquals(3, providerInt("topLevelCardsQueries"))
        assertEquals(2, providerInt("schedulerProjectionRejects"))
        assertEquals(0, providerInt("perNoteCardsQueries"))
        assertEquals(0, providerInt("explicitIdProjectionQueries"))
    }

    @Test
    fun manualSyncFallsBackWhenBulkSchedulerCursorThrowsUnknownQueue() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        context.contentResolver.call(providerUri(), "deferSchedulerProjectionFailure", null, null)

        val result = ManualSyncEngine(context, store, gateway, settings).run()

        assertTrue(result.message, result.success)
        assertEquals("success", store.latestSync()?.status)
        assertFalse(store.dashboardRows().isEmpty())
        assertEquals(4, providerInt("topLevelCardsQueries"))
        assertEquals(3, providerInt("schedulerProjectionRejects"))
        assertEquals(0, providerInt("perNoteCardsQueries"))
        assertEquals(0, providerInt("explicitIdProjectionQueries"))
    }

    @Test
    fun manualSyncFallsBackToPerNoteCardsWhenBulkCardsUriIsUnsupported() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        context.contentResolver.call(providerUri(), "legacyTopLevelCardsUnsupported", null, null)

        val result = ManualSyncEngine(context, store, gateway, settings).run()

        assertTrue(result.message, result.success)
        assertEquals("success", store.latestSync()?.status)
        assertFalse(store.dashboardRows().isEmpty())
        assertEquals(1, providerInt("topLevelCardsQueries"))
        assertTrue(providerInt("perNoteCardsQueries") > 0)
        assertEquals(0, providerInt("explicitIdProjectionQueries"))
    }

    @Test
    fun manualSyncFallsBackWhenFsrsColumnsAreUnsupported() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        context.contentResolver.call(providerUri(), "rejectFsrsProjection", null, null)

        val result = ManualSyncEngine(context, store, gateway, settings).run()

        assertTrue(result.message, result.success)
        assertEquals("success", store.latestSync()?.status)
        assertFalse(store.dashboardRows().isEmpty())
        assertEquals(2, providerInt("fsrsProjectionRejects"))
        assertEquals(0, providerInt("schedulerProjectionRejects"))
        assertEquals(3, providerInt("topLevelCardsQueries"))
        assertEquals(0, providerInt("perNoteCardsQueries"))
    }

    @Test
    fun providerReadFallsBackToNotesV2WhenConfiguredModelSearchFails() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        context.contentResolver.call(providerUri(), "failConfiguredSearch", null, null)

        val snapshot = gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults())

        assertEquals(2, snapshot.notes.size)
        assertEquals(2, snapshot.cards.size)
        assertEquals("確認", snapshot.notes[0].expression(RecordsSyncModels.Settings.kikuDefaults()))
        assertEquals(1, providerInt("topLevelCardsQueries"))
        assertEquals(0, providerInt("perNoteCardsQueries"))
        assertEquals(0, providerInt("browserQueryQueries"))
    }

    @Test
    fun providerReadFallsBackToNotesV2WhenConfiguredModelSearchReturnsNull() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        context.contentResolver.call(providerUri(), "nullConfiguredSearchCursor", null, null)

        val snapshot = gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults())

        assertEquals(2, snapshot.notes.size)
        assertEquals(2, snapshot.cards.size)
    }

    @Test
    fun configuredModelSearchRowsForOtherModelsAreIgnored() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        context.contentResolver.call(providerUri(), "configuredSearchIncludesWrongModel", null, null)

        val snapshot = gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults())

        assertEquals(2, snapshot.notes.size)
        assertEquals(2, snapshot.cards.size)
        assertEquals(1L, snapshot.notes[0].noteId)
    }

    @Test
    fun notesV2NullAfterSearchFailureIsRetryableWithOriginalFailureSuppressed() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        context.contentResolver.call(providerUri(), "failConfiguredSearch", null, null)
        context.contentResolver.call(providerUri(), "nullSqlNotesCursor", null, null)

        try {
            gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults())
            assertTrue("Expected notes_v2 null failure", false)
        } catch (error: AnkiDroidGateway.SyncFailure) {
            assertFalse(error.permanentFailure)
            assertTrue(error.message?.contains("no configured note cursor") == true)
            assertEquals(1, error.suppressed.size)
            assertTrue(error.suppressed[0].message?.contains("model search failed") == true)
        }
    }

    @Test
    fun archivedProviderNotesAreSkippedDuringCollectionRead() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        context.contentResolver.call(providerUri(), "pretagSuspendedArchived", null, null)

        val snapshot = gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults())

        assertEquals(1, snapshot.notes.size)
        assertEquals(1, snapshot.cards.size)
        assertEquals(1L, snapshot.notes[0].noteId)
    }

    @Test
    fun nullSuspendedSearchFallsBackToCardQueueState() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        context.contentResolver.call(providerUri(), "nullSuspendedSearchCursor", null, null)

        val snapshot = gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults())

        assertEquals(2, snapshot.cards.size)
        assertTrue(snapshot.cards[1].suspended)
    }

    @Test
    fun browserQueryProviderContractMatchesActiveNoteWithRawQuery() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)

        val snapshot = gateway.readCollection(browserQuerySettings(true, "tag:kani_contract_active"))

        assertTrue(cardFor(snapshot, 1L).browserQueryMatched)
        assertFalse(cardFor(snapshot, 2L).browserQueryMatched)
        assertEquals(1, providerInt("browserQueryQueries"))
    }

    @Test
    fun browserQueryProviderContractMatchesSuspendedNoteWithRawQuery() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)

        val snapshot = gateway.readCollection(browserQuerySettings(true, "tag:kani_contract_suspended"))

        assertFalse(cardFor(snapshot, 1L).browserQueryMatched)
        val suspended = cardFor(snapshot, 2L)
        assertTrue(suspended.suspended)
        assertTrue(suspended.browserQueryMatched)
        assertEquals(1, providerInt("browserQueryQueries"))
    }

    @Test
    fun browserQueryProviderContractLeavesUnmatchedNotesUnmarked() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)

        val snapshot = gateway.readCollection(browserQuerySettings(true, "tag:kani_contract_unmatched"))

        assertFalse(cardFor(snapshot, 1L).browserQueryMatched)
        assertFalse(cardFor(snapshot, 2L).browserQueryMatched)
        assertEquals(1, providerInt("browserQueryQueries"))
    }

    @Test
    fun browserQueryProviderContractFiltersArchivedMatchesOut() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)

        val snapshot = gateway.readCollection(browserQuerySettings(true, "tag:kani_contract_archived"))

        assertNull(cardOrNull(snapshot, 3L))
        assertFalse(cardFor(snapshot, 1L).browserQueryMatched)
        assertFalse(cardFor(snapshot, 2L).browserQueryMatched)
        assertEquals(2, providerInt("browserQueryQueries"))
    }

    @Test
    fun browserQueryProviderContractIgnoresOtherNoteTypes() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)

        val snapshot = gateway.readCollection(browserQuerySettings(true, "tag:kani_contract_other_type"))

        assertFalse(cardFor(snapshot, 1L).browserQueryMatched)
        assertFalse(cardFor(snapshot, 2L).browserQueryMatched)
        assertEquals(1, providerInt("browserQueryQueries"))
    }

    @Test
    fun browserQueryProviderContractMapsInvalidQueryToConfigError() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)

        try {
            gateway.readCollection(browserQuerySettings(true, "tag:kani_contract_invalid"))
            assertTrue("Expected invalid browser query failure", false)
        } catch (error: AnkiDroidGateway.SyncFailure) {
            assertTrue(error.permanentFailure)
            assertEquals(
                "AnkiDroid could not run the browser query. Check the query in Import filters.",
                error.message,
            )
        }
        assertEquals(1, providerInt("browserQueryQueries"))
    }

    @Test
    fun browserQueryMarksMatchingActiveCardForImport() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        val settings = browserQueryOnlySettings()
        context.contentResolver.call(providerUri(), "browserQueryMatchesActive", null, null)

        val result = ManualSyncEngine(context, store, gateway, settings).run()

        assertTrue(result.message, result.success)
        assertTrue(
            "Browser-query active cards should not be archived as suspended imports.",
            store.suspendedImports().isEmpty(),
        )
        val rows = store.dashboardRows()
        assertFalse(rows.isEmpty())
        assertEquals("認", rows[0].kanji)
        assertEquals(1, rows[0].activeExampleCount)
        assertEquals(0, rows[0].suspendedExampleCount)
        assertFalse(store.studyItems().isEmpty())
    }

    @Test
    fun nullBrowserQueryCursorIsTreatedAsZeroMatches() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        context.contentResolver.call(providerUri(), "nullBrowserQueryCursor", null, null)

        // Real AnkiDroid returns a null notes cursor when a valid browser
        // query matches zero notes. That must not fail the whole sync.
        val snapshot = gateway.readCollection(browserQueryOnlySettings())

        assertFalse(snapshot.cards.any { it.browserQueryMatched })
    }

    @Test
    fun browserQueryRowsForOtherModelsAreIgnored() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        context.contentResolver.call(providerUri(), "browserQueryWrongModel", null, null)

        val snapshot = gateway.readCollection(browserQueryOnlySettings())

        assertEquals(2, snapshot.cards.size)
        assertFalse(snapshot.cards[0].browserQueryMatched)
    }

    @Test
    fun browserQueryRereadsMissingMatchedNoteBeforeManualImport() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        val settings = browserQueryOnlySettings()
        context.contentResolver.call(providerUri(), "browserQueryMatchesMissingNote", null, null)

        val result = ManualSyncEngine(context, store, gateway, settings).run()

        assertTrue(result.message, result.success)
        assertEquals("success", store.latestSync()?.status)
        assertEquals(2, providerInt("browserQueryQueries"))
        assertEquals(1, providerInt("topLevelCardsQueries"))
        assertEquals(0, providerInt("perNoteCardsQueries"))
        assertTrue(store.suspendedImports().isEmpty())
        val row = rowFor(store.dashboardRows(), "認")
        assertEquals(1, row.activeExampleCount)
        assertEquals(0, row.suspendedExampleCount)
        assertFalse(store.studyItems().isEmpty())
    }

    @Test
    fun browserQueryRereadFailureIsPermanentConfigFailure() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        context.contentResolver.call(providerUri(), "browserQueryMatchesMissingNote", null, null)
        context.contentResolver.call(providerUri(), "failBrowserQueryReread", null, null)

        try {
            gateway.readCollection(browserQueryOnlySettings())
            assertTrue("Expected browser query re-read failure", false)
        } catch (error: AnkiDroidGateway.SyncFailure) {
            assertTrue(error.permanentFailure)
            assertEquals(
                "AnkiDroid could not run the browser query. Check the query in Import filters.",
                error.message,
            )
            assertEquals(2, providerInt("browserQueryQueries"))
        }
    }

    @Test
    fun disabledAndBlankBrowserQuerySettingsSkipBrowserQuerySearch() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)

        gateway.readCollection(browserQuerySettings(false, "tag:kani"))
        gateway.readCollection(browserQuerySettings(true, "   "))

        assertEquals(0, providerInt("browserQueryQueries"))
    }

    @Test
    fun browserQueryPermanentErrorIsRecordedAsConfigFailure() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        val settings = browserQueryOnlySettings()
        context.contentResolver.call(providerUri(), "failBrowserQuery", null, null)

        val result = ManualSyncEngine(context, store, gateway, settings).run()

        assertFalse(result.success)
        assertTrue(result.message?.contains("could not run the browser query") == true)
        assertEquals("config_error", store.latestSync()?.status)
        assertEquals(0, store.suspendedImports().size)
    }

    @Test
    fun providerPermanentExceptionIsRecordedAsConfigFailure() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        context.contentResolver.call(providerUri(), "permanentProviderFailure", null, null)

        val result = ManualSyncEngine(context, store, gateway, RecordsSyncModels.Settings.kikuDefaults()).run()

        assertFalse(result.success)
        assertTrue(result.message?.contains("model metadata cursor failed") == true)
        assertEquals("config_error", store.latestSync()?.status)
        assertTrue(store.latestSync()?.errorMessage?.contains("model metadata cursor failed") == true)
    }

    @Test
    fun providerRetryableExceptionIsRecordedAsRetryableFailure() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        context.contentResolver.call(providerUri(), "retryableProviderFailure", null, null)

        val result = ManualSyncEngine(context, store, gateway, RecordsSyncModels.Settings.kikuDefaults()).run()

        assertFalse(result.success)
        assertTrue(result.message?.contains("AnkiDroid provider read failed: database locked") == true)
        assertEquals("retryable_error", store.latestSync()?.status)
        assertTrue(store.latestSync()?.errorMessage?.contains("database locked") == true)
    }

    @Test
    fun projectionExhaustionIsRecordedAsTerminalRetryableFailure() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        context.contentResolver.call(providerUri(), "rejectAllCardProjections", null, null)

        val result = ManualSyncEngine(context, store, gateway, RecordsSyncModels.Settings.kikuDefaults()).run()

        assertFalse(result.success)
        assertTrue(result.message?.contains("AnkiDroid card projection failed") == true)
        assertEquals("retryable_error", store.latestSync()?.status)
        assertEquals(4, providerInt("cardProjectionRejects"))
        assertEquals(4, providerInt("topLevelCardsQueries"))
    }

    @Test
    fun nullCardCursorAfterProjectionFallbacksIsRecordedAsTerminalRetryableFailure() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        context.contentResolver.call(providerUri(), "nullCardCursor", null, null)

        val result = ManualSyncEngine(context, store, gateway, RecordsSyncModels.Settings.kikuDefaults()).run()

        assertFalse(result.success)
        assertTrue(result.message?.contains("AnkiDroid returned no bulk card cursor") == true)
        assertEquals("retryable_error", store.latestSync()?.status)
    }

    @Test
    fun firstTemplateOnlyProviderRuleRejectsLaterTemplateCards() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        context.contentResolver.call(providerUri(), "secondTemplateCard", null, null)

        try {
            gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults())
            assertTrue("Expected second-template card rejection", false)
        } catch (error: AnkiDroidGateway.SyncFailure) {
            assertTrue(error.permanentFailure)
            assertTrue(error.message?.contains("supports only the first card template") == true)
            assertTrue(error.message?.contains("ord 1") == true)
        }
    }

    @Test
    fun providerCleanupPreservesAlreadyArchivedSuspendedNoteTag() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        val snapshot = gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults())
        context.contentResolver.call(providerUri(), "pretagSuspendedArchived", null, null)

        val summary = gateway.removeArchivedSuspendedCards(
            snapshot,
            listOf(suspendedImportFor(2000L, 2L, true)),
            SyncProgress.NONE,
        )

        assertEquals(1, summary.sourceCards)
        assertEquals(1, summary.taggedNotes)
        assertEquals("leech kani_archived", providerString("suspendedTags"))
    }

    @Test
    fun providerCleanupUsesAllSuspendedCardsWhenNoSelectionIsSupplied() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        val snapshot = gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults())

        val summary = gateway.removeArchivedSuspendedCards(snapshot, null as SyncProgress.Listener?)

        assertEquals(1, summary.sourceCards)
        assertEquals(1, summary.taggedNotes)
        assertEquals("kani_archived", providerString("suspendedTags"))
    }

    @Test
    fun providerCleanupCanTagWhenExistingNoteTagsCursorIsNull() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        val snapshot = gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults())
        context.contentResolver.call(providerUri(), "nullNoteCursor", null, null)

        val summary = gateway.removeArchivedSuspendedCards(
            snapshot,
            listOf(suspendedImportFor(2000L, 2L, true)),
            SyncProgress.NONE,
        )

        assertEquals(1, summary.sourceCards)
        assertEquals(1, summary.taggedNotes)
        assertEquals("kani_archived", providerString("suspendedTags"))
    }

    @Test
    fun providerCleanupKeepsPartiallySuspendedNotesInLocalArchive() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        context.contentResolver.call(providerUri(), "failSuspendedSearch", null, null)
        context.contentResolver.call(providerUri(), "partiallySuspendedNote", null, null)
        val snapshot = gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults())

        val summary = gateway.removeArchivedSuspendedCards(
            snapshot,
            listOf(suspendedImportFor(2000L, 2L, true)),
            SyncProgress.NONE,
        )

        assertEquals(1, summary.sourceCards)
        assertEquals(0, summary.taggedNotes)
        assertTrue(summary.message?.contains("kept in the local archive") == true)
        assertEquals("", providerString("suspendedTags"))
    }

    @Test
    fun providerCleanupReportsPartialTagWhenSelectedSuspendedCardsAreMixed() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        val snapshot = snapshotWithCards(
            card(10L, 1L, true),
            card(11L, 1L, true),
            card(20L, 2L, true),
        )

        val summary = gateway.removeArchivedSuspendedCards(
            snapshot,
            listOf(
                suspendedImportFor("箱", 20L, 2L, true),
                RecordsImportModels.SuspendedImport(
                    "確",
                    100,
                    true,
                    3000,
                    listOf(
                        suspendedSource("確", 10L, 1L, true),
                        suspendedSource("確", 11L, 1L, false),
                    ),
                ),
            ),
            null as SyncProgress.Listener?,
        )

        assertEquals(2, summary.sourceCards)
        assertEquals(1, summary.taggedNotes)
        assertTrue(summary.message?.contains("partly tagged") == true)
        assertEquals("kani_archived", providerString("suspendedTags"))
    }

    @Test
    fun providerCleanupKeepsLocalArchiveWhenSelectedNoteCannotBeUpdated() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)

        val summary = gateway.removeArchivedSuspendedCards(
            snapshotWithCards(card(9990L, 999L, true)),
            listOf(suspendedImportFor("謎", 9990L, 999L, true)),
            SyncProgress.NONE,
        )

        assertEquals(1, summary.sourceCards)
        assertEquals(0, summary.taggedNotes)
        assertTrue(summary.message?.contains("kept in the local archive") == true)
        assertEquals("", providerString("suspendedTags"))
    }

    @Test
    fun unparseableFsrsDataDoesNotBlockProviderRead() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        context.contentResolver.call(providerUri(), "unparseableFsrsData", null, null)

        val snapshot = gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults())

        assertEquals(2, snapshot.cards.size)
        assertNull(snapshot.cards[0].fsrsStability)
        assertNull(snapshot.cards[0].fsrsDifficulty)
        assertNull(snapshot.cards[0].fsrsRetrievability)
    }

    @Test
    fun parseableFsrsDataCanSupplyMemoryState() {
        val gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
        context.contentResolver.call(providerUri(), "dataOnlyFsrs", null, null)

        val snapshot = gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults())

        assertClose(12.5, snapshot.cards[0].fsrsStability)
        assertClose(7.0, snapshot.cards[0].fsrsDifficulty)
        assertClose(0.42, snapshot.cards[0].fsrsRetrievability)
    }

    private fun resetProvider() {
        context.contentResolver.call(providerUri(), "reset", null, null)
    }

    private fun providerInt(method: String): Int {
        val result = context.contentResolver.call(providerUri(), method, null, null)
        return result?.getInt("value", -1) ?: -1
    }

    private fun providerString(method: String): String? {
        val result = context.contentResolver.call(providerUri(), method, null, null)
        return result?.getString("value")
    }

    private fun permissionedGateway(permission: String): AnkiDroidGateway {
        val providerTargetClass = Class.forName("${AnkiDroidGateway::class.java.name}\$ProviderTarget")
        val targetConstructor: Constructor<*> = providerTargetClass.getDeclaredConstructor(String::class.java, String::class.java)
        targetConstructor.setAccessible(true)
        val target = targetConstructor.newInstance(FakeAnkiDroidProvider.AUTHORITY, permission)
        val gatewayConstructor: Constructor<AnkiDroidGateway> = AnkiDroidGateway::class.java.getDeclaredConstructor(Context::class.java, List::class.java)
        gatewayConstructor.setAccessible(true)
        @Suppress("UNCHECKED_CAST")
        return gatewayConstructor.newInstance(context, listOf(target)) as AnkiDroidGateway
    }

    private fun <T> assertPermissionFailure(call: () -> T) {
        try {
            call()
            assertTrue("Expected permission failure", false)
        } catch (error: AnkiDroidGateway.SyncFailure) {
            assertTrue(error.permanentFailure)
            assertTrue(error.message?.contains("permission is missing") == true)
        }
    }

    private fun rowFor(rows: List<RecordsImportModels.DashboardRow>, kanji: String): RecordsImportModels.DashboardRow {
        for (row in rows) {
            if (kanji == row.kanji) {
                return row
            }
        }
        throw AssertionError("Expected dashboard row for $kanji")
    }

    private fun cardFor(snapshot: RecordsSyncModels.CollectionSnapshot, noteId: Long): RecordsSyncModels.Card {
        return cardOrNull(snapshot, noteId) ?: throw AssertionError("Expected card for note $noteId")
    }

    private fun cardOrNull(snapshot: RecordsSyncModels.CollectionSnapshot, noteId: Long): RecordsSyncModels.Card? {
        for (card in snapshot.cards) {
            if (card.noteId == noteId) {
                return card
            }
        }
        return null
    }

    private fun providerUri(): Uri {
        return Uri.parse("content://${FakeAnkiDroidProvider.AUTHORITY}")
    }

    private fun suspendedImportFor(cardId: Long, noteId: Long, suspended: Boolean): RecordsImportModels.SuspendedImport {
        return suspendedImportFor("箱", cardId, noteId, suspended)
    }

    private fun suspendedImportFor(kanji: String, cardId: Long, noteId: Long, suspended: Boolean): RecordsImportModels.SuspendedImport {
        return RecordsImportModels.SuspendedImport(
            kanji,
            2500,
            true,
            3000,
            listOf(suspendedSource(kanji, cardId, noteId, suspended)),
        )
    }

    private fun suspendedSource(kanji: String, cardId: Long, noteId: Long, suspended: Boolean): RecordsImportModels.SuspendedSource {
        val details = RecordsImportModels.SuspendedSourceDetails
            .builder("${kanji}を見た。")
            .suspended(suspended)
            .forcePractice(suspended)
            .sourceType(if (suspended) RecordsBase.SOURCE_SUSPENDED else RecordsBase.SOURCE_ACTIVE)
        return RecordsImportModels.SuspendedSource(
            kanji,
            cardId,
            noteId,
            "${kanji}箱",
            "かな",
            "meaning",
            details.build(),
        )
    }

    private fun snapshotWithCards(vararg cards: RecordsSyncModels.Card): RecordsSyncModels.CollectionSnapshot {
        return RecordsSyncModels.CollectionSnapshot(emptyList<RecordsSyncModels.Note>(), cards.toList())
    }

    private fun card(cardId: Long, noteId: Long, suspended: Boolean): RecordsSyncModels.Card {
        return RecordsSyncModels.Card(cardId, noteId, 0, "Mining", if (suspended) -1 else 2, 2, 0, 0, 0, 0, suspended)
    }

    private fun customMappedSettings(): RecordsSyncModels.Settings {
        val defaults = RecordsSyncModels.Settings.kikuDefaults()
        return RecordsSyncModels.Settings(
            "Custom Japanese",
            defaults.templateName,
            "Front",
            "Reading",
            "Back",
            "Example",
            "Frequency",
            "FrequencySort",
            defaults.matureDays,
            defaults.matureSupportThreshold,
            defaults.suspendedRankMin,
            defaults.suspendedRankMax,
            defaults.activeQueueCap,
            defaults.newPerDay,
            defaults.writingTriggerMissDays,
        )
    }

    private fun settingsWithModel(modelName: String): RecordsSyncModels.Settings {
        val defaults = RecordsSyncModels.Settings.kikuDefaults()
        return RecordsSyncModels.Settings(
            modelName,
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
        )
    }

    private fun settingsWithExpressionField(expressionField: String): RecordsSyncModels.Settings {
        val defaults = RecordsSyncModels.Settings.kikuDefaults()
        return RecordsSyncModels.Settings(
            defaults.modelName,
            defaults.templateName,
            expressionField,
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
        )
    }

    private fun browserQueryOnlySettings(): RecordsSyncModels.Settings {
        return browserQuerySettings(true, "tag:kani")
    }

    private fun browserQuerySettings(enabled: Boolean, query: String): RecordsSyncModels.Settings {
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
            false,
            false,
            false,
            emptyList<String>(),
            false,
            defaults.importWeakFsrsDifficultyThreshold,
            defaults.importWeakLapsesThreshold,
            defaults.importMinMatchingCardsPerKanji,
            enabled,
            query,
        )
    }

    private fun assertClose(expected: Double, actual: Double?) {
        assertTrue(actual != null && abs(expected - actual) <= 0.001)
    }
}
