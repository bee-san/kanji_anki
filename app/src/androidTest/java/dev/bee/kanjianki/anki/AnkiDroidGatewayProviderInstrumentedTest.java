package dev.bee.kanjianki.anki;

import dev.bee.kanjianki.core.RecordsBase;
import dev.bee.kanjianki.core.RecordsImportModels;
import dev.bee.kanjianki.core.RecordsSyncModels;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import dev.bee.kanjianki.data.LocalStore;
import dev.bee.kanjianki.sync.ManualSyncEngine;
import dev.bee.kanjianki.sync.SyncProgress;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public final class AnkiDroidGatewayProviderInstrumentedTest {
    private Context context;
    private LocalStore store;

    @BeforeClass
    public static void waitForPackageInstallToSettle() {
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase("kanji_anki_simple.db");
        store = new LocalStore(context);
        resetProvider();
    }

    @After
    public void tearDown() {
        if (store != null) {
            store.close();
        }
        context.deleteDatabase("kanji_anki_simple.db");
        resetProvider();
    }

    @Test
    public void readsKikuCollectionWhenTopLevelCardsUriIsUnsupported() throws Exception {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);

        RecordsSyncModels.CollectionSnapshot snapshot = gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults());

        assertEquals(2, snapshot.notes.size());
        assertEquals(2, snapshot.cards.size());
        assertEquals(2, snapshot.cards.get(0).queue);
        assertEquals(42, snapshot.cards.get(0).intervalDays);
        assertEquals(80, snapshot.cards.get(0).reps);
        assertEquals(3, snapshot.cards.get(0).lapses);
        assertClose(12.5, snapshot.cards.get(0).fsrsStability);
        assertClose(7.0, snapshot.cards.get(0).fsrsDifficulty);
        assertClose(0.42, snapshot.cards.get(0).fsrsRetrievability);
        assertTrue(snapshot.cards.get(1).suspended);
        assertEquals(0, providerInt("topLevelCardsQueries"));
        assertEquals(2, providerInt("perNoteCardsQueries"));
        assertEquals(0, providerInt("explicitIdProjectionQueries"));
    }

    @Test
    public void listsAvailableNoteTypesWithKikuFirst() throws Exception {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);

        List<AnkiDroidGateway.NoteType> noteTypes = gateway.noteTypes();

        assertEquals(2, noteTypes.size());
        assertEquals("Kiku", noteTypes.get(0).name);
        assertEquals("Custom Japanese", noteTypes.get(1).name);
        assertTrue(noteTypes.get(1).fields.contains("Front"));
        assertFalse(noteTypes.get(1).fields.contains("Expression"));
    }

    @Test
    public void permissionDeniedProviderStatusAndReadsFailBeforeProviderQueries() throws Exception {
        AnkiDroidGateway gateway = permissionedGateway("dev.bee.kanjianki.fake.READ_ANKI");

        AnkiDroidGateway.ProviderStatus status = gateway.status();

        assertTrue(status.installed);
        assertFalse(status.permissionGranted);
        assertFalse(status.canSync);
        assertEquals(FakeAnkiDroidProvider.AUTHORITY, status.authority);
        assertEquals("dev.bee.kanjianki.fake.READ_ANKI", status.permission);
        assertTrue(status.message.contains("Allow AnkiDroid access"));
        assertPermissionFailure(gateway::noteTypes);
        assertPermissionFailure(() -> gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults()));
        assertEquals(0, providerInt("perNoteCardsQueries"));
    }

    @Test
    public void emptyPermissionProviderTargetReadsAsGranted() throws Exception {
        AnkiDroidGateway gateway = permissionedGateway("");

        AnkiDroidGateway.ProviderStatus status = gateway.status();
        List<AnkiDroidGateway.NoteType> noteTypes = gateway.noteTypes();

        assertTrue(status.permissionGranted);
        assertTrue(status.canSync);
        assertEquals(2, noteTypes.size());
    }

    @Test
    public void declaredPermissionProviderTargetReadsAsGranted() throws Exception {
        AnkiDroidGateway gateway = permissionedGateway("android.permission.INTERNET");

        AnkiDroidGateway.ProviderStatus status = gateway.status();

        assertTrue(status.permissionGranted);
        assertTrue(status.canSync);
    }

    @Test
    public void providerInstalledHelperUsesModernAndLegacyPackageManagerPaths() {
        assertTrue(AnkiDroidGateway.providerInstalled(context.getPackageManager(), FakeAnkiDroidProvider.AUTHORITY));
        assertTrue(AnkiDroidGateway.providerInstalled(
                context.getPackageManager(),
                FakeAnkiDroidProvider.AUTHORITY,
                Build.VERSION_CODES.S_V2
        ));
        assertTrue(AnkiDroidGateway.providerInstalled(
                context.getPackageManager(),
                FakeAnkiDroidProvider.AUTHORITY,
                Build.VERSION_CODES.TIRAMISU
        ));
        assertTrue(AnkiDroidGateway.providerInstalledBeforeTiramisu(context.getPackageManager(), FakeAnkiDroidProvider.AUTHORITY));
        assertFalse(AnkiDroidGateway.providerInstalledBeforeTiramisu(
                context.getPackageManager(),
                "dev.bee.kanjianki.missing.provider"
        ));
    }

    @Test
    public void nullProgressAndProviderTimeoutMapToRetryableFailure() throws Exception {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        context.getContentResolver().call(providerUri(), "operationCanceledProviderFailure", null, null);

        try {
            gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults(), null);
            fail("Expected timeout failure");
        } catch (AnkiDroidGateway.SyncFailure error) {
            assertFalse(error.permanentFailure);
            assertTrue(error.getMessage().contains("Timed out while reading AnkiDroid"));
        }
    }

    @Test
    public void providerSecurityExceptionMapsToPermanentAccessFailure() throws Exception {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        context.getContentResolver().call(providerUri(), "securityProviderFailure", null, null);

        try {
            gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults());
            fail("Expected security failure");
        } catch (AnkiDroidGateway.SyncFailure error) {
            assertTrue(error.permanentFailure);
            assertTrue(error.getMessage().contains("denied database access"));
        }
    }

    @Test
    public void nullModelCursorIsRetryableAndMissingConfiguredModelIsPermanent() throws Exception {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        context.getContentResolver().call(providerUri(), "nullModelsCursor", null, null);

        try {
            gateway.noteTypes();
            fail("Expected null model cursor failure");
        } catch (AnkiDroidGateway.SyncFailure error) {
            assertFalse(error.permanentFailure);
            assertTrue(error.getMessage().contains("no note model cursor"));
        }

        resetProvider();
        try {
            gateway.readCollection(settingsWithModel("Missing Model"));
            fail("Expected missing model failure");
        } catch (AnkiDroidGateway.SyncFailure error) {
            assertTrue(error.permanentFailure);
            assertTrue(error.getMessage().contains("Missing Model note type was not found"));
        }
    }

    @Test
    public void invalidConfiguredFieldMappingIsPermanent() throws Exception {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);

        try {
            gateway.readCollection(settingsWithExpressionField("MissingExpression"));
            fail("Expected field validation failure");
        } catch (AnkiDroidGateway.SyncFailure error) {
            assertTrue(error.permanentFailure);
            assertTrue(error.getMessage().contains("missing required field MissingExpression"));
        }
    }

    @Test
    public void readsCustomNoteTypeWithMappedFields() throws Exception {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        RecordsSyncModels.Settings settings = customMappedSettings();

        RecordsSyncModels.CollectionSnapshot snapshot = gateway.readCollection(settings);

        assertEquals(2, snapshot.notes.size());
        assertEquals(2, snapshot.cards.size());
        assertEquals("Custom Japanese", snapshot.notes.get(0).modelName);
        assertEquals("確認", snapshot.notes.get(0).expression(settings));
        assertEquals("かくにん", snapshot.notes.get(0).reading(settings));
        assertEquals("confirmation", snapshot.notes.get(0).meaning(settings));
        assertEquals("確認した。", snapshot.notes.get(0).sentence(settings));
    }

    @Test
    public void manualSyncWorksAgainstFakeAnkiDroidProviderContract() {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);

        ManualSyncEngine.SyncResult result = new ManualSyncEngine(context, store, gateway, settings).run();

        assertTrue(result.success);
        assertEquals("success", store.latestSync().status);
        assertFalse(store.dashboardRows().isEmpty());
        List<RecordsImportModels.SuspendedImport> imports = store.suspendedImports();
        assertEquals(1, imports.size());
        assertEquals("箱", imports.get(0).kanji);
        assertTrue(result.message.contains("tagged in AnkiDroid"));
        assertEquals(0, providerInt("topLevelCardsQueries"));
        assertEquals(2, providerInt("perNoteCardsQueries"));
        assertEquals(0, providerInt("explicitIdProjectionQueries"));
    }

    @Test
    public void manualSyncUsesCardQueueWhenAnkiDroidRejectsSuspendedSearch() {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        context.getContentResolver().call(providerUri(), "failSuspendedSearch", null, null);

        ManualSyncEngine.SyncResult result = new ManualSyncEngine(context, store, gateway, settings).run();

        assertTrue(result.success);
        assertEquals("success", store.latestSync().status);
        assertFalse(store.dashboardRows().isEmpty());
        List<RecordsImportModels.SuspendedImport> imports = store.suspendedImports();
        assertEquals(1, imports.size());
        assertEquals("箱", imports.get(0).kanji);
        assertEquals(0, providerInt("topLevelCardsQueries"));
        assertEquals(2, providerInt("perNoteCardsQueries"));
        assertEquals(0, providerInt("explicitIdProjectionQueries"));
    }

    @Test
    public void providerCleanupLeavesExcludedSuspendedCardsUntagged() throws Exception {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        RecordsSyncModels.CollectionSnapshot snapshot = gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults());

        AnkiDroidGateway.RemovalSummary summary = gateway.removeArchivedSuspendedCards(
                snapshot,
                Collections.emptyList(),
                SyncProgress.NONE
        );

        assertEquals(0, summary.sourceCards);
        assertEquals("", FakeAnkiDroidProvider.suspendedTags);
    }

    @Test
    public void providerCleanupNoopsForMissingProviderAndEmptySnapshotOverloads() {
        RecordsSyncModels.CollectionSnapshot empty = new RecordsSyncModels.CollectionSnapshot(Collections.emptyList(), Collections.emptyList());
        AnkiDroidGateway missingProvider = AnkiDroidGateway.testProvider(context, "dev.bee.kanjianki.no_fake_anki");

        AnkiDroidGateway.RemovalSummary missingSummary = missingProvider.removeArchivedSuspendedCards(
                snapshotWithCards(card(9990L, 999L, true))
        );
        AnkiDroidGateway.RemovalSummary emptySummary = AnkiDroidGateway
                .testProvider(context, FakeAnkiDroidProvider.AUTHORITY)
                .removeArchivedSuspendedCards(empty, (SyncProgress.Listener) null);

        assertEquals(0, missingSummary.sourceCards);
        assertEquals("No provider removal attempted.", missingSummary.message);
        assertEquals(0, emptySummary.sourceCards);
        assertEquals("No provider removal attempted.", emptySummary.message);
    }

    @Test
    public void manualSyncFallsBackWhenPerNoteSchedulerProjectionIsUnsupported() {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        context.getContentResolver().call(providerUri(), "rejectSchedulerProjection", null, null);

        ManualSyncEngine.SyncResult result = new ManualSyncEngine(context, store, gateway, settings).run();

        assertTrue(result.success);
        assertEquals("success", store.latestSync().status);
        assertFalse(store.dashboardRows().isEmpty());
        assertEquals(0, providerInt("topLevelCardsQueries"));
        assertEquals(2, providerInt("schedulerProjectionRejects"));
        assertEquals(2, providerInt("perNoteCardsQueries"));
        assertEquals(0, providerInt("explicitIdProjectionQueries"));
    }

    @Test
    public void manualSyncFallsBackWhenPerNoteSchedulerCursorThrowsUnknownQueue() {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        context.getContentResolver().call(providerUri(), "deferSchedulerProjectionFailure", null, null);

        ManualSyncEngine.SyncResult result = new ManualSyncEngine(context, store, gateway, settings).run();

        assertTrue(result.message, result.success);
        assertEquals("success", store.latestSync().status);
        assertFalse(store.dashboardRows().isEmpty());
        assertEquals(0, providerInt("topLevelCardsQueries"));
        assertEquals(2, providerInt("schedulerProjectionRejects"));
        assertEquals(4, providerInt("perNoteCardsQueries"));
        assertEquals(0, providerInt("explicitIdProjectionQueries"));
    }

    @Test
    public void manualSyncFallsBackWhenFsrsColumnsAreUnsupported() {
        RecordsSyncModels.Settings settings = RecordsSyncModels.Settings.kikuDefaults();
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        context.getContentResolver().call(providerUri(), "rejectFsrsProjection", null, null);

        ManualSyncEngine.SyncResult result = new ManualSyncEngine(context, store, gateway, settings).run();

        assertTrue(result.message, result.success);
        assertEquals("success", store.latestSync().status);
        assertFalse(store.dashboardRows().isEmpty());
        assertEquals(1, providerInt("fsrsProjectionRejects"));
        assertEquals(0, providerInt("schedulerProjectionRejects"));
        assertEquals(2, providerInt("perNoteCardsQueries"));
    }

    @Test
    public void providerReadFallsBackToNotesV2WhenConfiguredModelSearchFails() throws Exception {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        context.getContentResolver().call(providerUri(), "failConfiguredSearch", null, null);

        RecordsSyncModels.CollectionSnapshot snapshot = gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults());

        assertEquals(2, snapshot.notes.size());
        assertEquals(2, snapshot.cards.size());
        assertEquals("確認", snapshot.notes.get(0).expression(RecordsSyncModels.Settings.kikuDefaults()));
        assertEquals(2, providerInt("perNoteCardsQueries"));
    }

    @Test
    public void providerReadFallsBackToNotesV2WhenConfiguredModelSearchReturnsNull() throws Exception {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        context.getContentResolver().call(providerUri(), "nullConfiguredSearchCursor", null, null);

        RecordsSyncModels.CollectionSnapshot snapshot = gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults());

        assertEquals(2, snapshot.notes.size());
        assertEquals(2, snapshot.cards.size());
    }

    @Test
    public void configuredModelSearchRowsForOtherModelsAreIgnored() throws Exception {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        context.getContentResolver().call(providerUri(), "configuredSearchIncludesWrongModel", null, null);

        RecordsSyncModels.CollectionSnapshot snapshot = gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults());

        assertEquals(2, snapshot.notes.size());
        assertEquals(2, snapshot.cards.size());
        assertEquals(1L, snapshot.notes.get(0).noteId);
    }

    @Test
    public void notesV2NullAfterSearchFailureIsRetryableWithOriginalFailureSuppressed() throws Exception {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        context.getContentResolver().call(providerUri(), "failConfiguredSearch", null, null);
        context.getContentResolver().call(providerUri(), "nullSqlNotesCursor", null, null);

        try {
            gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults());
            fail("Expected notes_v2 null failure");
        } catch (AnkiDroidGateway.SyncFailure error) {
            assertFalse(error.permanentFailure);
            assertTrue(error.getMessage().contains("no configured note cursor"));
            assertEquals(1, error.getSuppressed().length);
            assertTrue(error.getSuppressed()[0].getMessage().contains("model search failed"));
        }
    }

    @Test
    public void archivedProviderNotesAreSkippedDuringCollectionRead() throws Exception {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        context.getContentResolver().call(providerUri(), "pretagSuspendedArchived", null, null);

        RecordsSyncModels.CollectionSnapshot snapshot = gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults());

        assertEquals(1, snapshot.notes.size());
        assertEquals(1, snapshot.cards.size());
        assertEquals(1L, snapshot.notes.get(0).noteId);
    }

    @Test
    public void nullSuspendedSearchFallsBackToCardQueueState() throws Exception {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        context.getContentResolver().call(providerUri(), "nullSuspendedSearchCursor", null, null);

        RecordsSyncModels.CollectionSnapshot snapshot = gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults());

        assertEquals(2, snapshot.cards.size());
        assertTrue(snapshot.cards.get(1).suspended);
    }

    @Test
    public void browserQueryMarksMatchingActiveCardForImport() {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        RecordsSyncModels.Settings settings = browserQueryOnlySettings();
        context.getContentResolver().call(providerUri(), "browserQueryMatchesActive", null, null);

        ManualSyncEngine.SyncResult result = new ManualSyncEngine(context, store, gateway, settings).run();

        assertTrue(result.message, result.success);
        assertTrue("Browser-query active cards should not be archived as suspended imports.", store.suspendedImports().isEmpty());
        List<RecordsImportModels.DashboardRow> rows = store.dashboardRows();
        assertFalse(rows.isEmpty());
        assertEquals("認", rows.get(0).kanji);
        assertEquals(1, rows.get(0).activeExampleCount);
        assertEquals(0, rows.get(0).suspendedExampleCount);
        assertFalse(store.studyItems().isEmpty());
    }

    @Test
    public void nullBrowserQueryCursorIsPermanentConfigFailure() throws Exception {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        context.getContentResolver().call(providerUri(), "nullBrowserQueryCursor", null, null);

        try {
            gateway.readCollection(browserQueryOnlySettings());
            fail("Expected browser query cursor failure");
        } catch (AnkiDroidGateway.SyncFailure error) {
            assertTrue(error.permanentFailure);
            assertEquals("AnkiDroid could not run the browser query. Check the query in Import filters.", error.getMessage());
        }
    }

    @Test
    public void browserQueryRowsForOtherModelsAreIgnored() throws Exception {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        context.getContentResolver().call(providerUri(), "browserQueryWrongModel", null, null);

        RecordsSyncModels.CollectionSnapshot snapshot = gateway.readCollection(browserQueryOnlySettings());

        assertEquals(2, snapshot.cards.size());
        assertFalse(snapshot.cards.get(0).browserQueryMatched);
    }

    @Test
    public void browserQueryRereadsMissingMatchedNoteBeforeManualImport() {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        RecordsSyncModels.Settings settings = browserQueryOnlySettings();
        context.getContentResolver().call(providerUri(), "browserQueryMatchesMissingNote", null, null);

        ManualSyncEngine.SyncResult result = new ManualSyncEngine(context, store, gateway, settings).run();

        assertTrue(result.message, result.success);
        assertEquals("success", store.latestSync().status);
        assertEquals(2, providerInt("browserQueryQueries"));
        assertEquals(3, providerInt("perNoteCardsQueries"));
        assertTrue(store.suspendedImports().isEmpty());
        RecordsImportModels.DashboardRow row = rowFor(store.dashboardRows(), "認");
        assertEquals(1, row.activeExampleCount);
        assertEquals(0, row.suspendedExampleCount);
        assertFalse(store.studyItems().isEmpty());
    }

    @Test
    public void browserQueryRereadFailureIsPermanentConfigFailure() throws Exception {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        context.getContentResolver().call(providerUri(), "browserQueryMatchesMissingNote", null, null);
        context.getContentResolver().call(providerUri(), "failBrowserQueryReread", null, null);

        try {
            gateway.readCollection(browserQueryOnlySettings());
            fail("Expected browser query re-read failure");
        } catch (AnkiDroidGateway.SyncFailure error) {
            assertTrue(error.permanentFailure);
            assertEquals("AnkiDroid could not run the browser query. Check the query in Import filters.", error.getMessage());
            assertEquals(2, providerInt("browserQueryQueries"));
        }
    }

    @Test
    public void disabledAndBlankBrowserQuerySettingsSkipBrowserQuerySearch() throws Exception {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);

        gateway.readCollection(browserQuerySettings(false, "tag:kani"));
        gateway.readCollection(browserQuerySettings(true, "   "));

        assertEquals(0, providerInt("browserQueryQueries"));
    }

    @Test
    public void browserQueryPermanentErrorIsRecordedAsConfigFailure() {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        RecordsSyncModels.Settings settings = browserQueryOnlySettings();
        context.getContentResolver().call(providerUri(), "failBrowserQuery", null, null);

        ManualSyncEngine.SyncResult result = new ManualSyncEngine(context, store, gateway, settings).run();

        assertFalse(result.success);
        assertTrue(result.message.contains("could not run the browser query"));
        assertEquals("config_error", store.latestSync().status);
        assertEquals(0, store.suspendedImports().size());
    }

    @Test
    public void providerPermanentExceptionIsRecordedAsConfigFailure() {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        context.getContentResolver().call(providerUri(), "permanentProviderFailure", null, null);

        ManualSyncEngine.SyncResult result = new ManualSyncEngine(context, store, gateway, RecordsSyncModels.Settings.kikuDefaults()).run();

        assertFalse(result.success);
        assertTrue(result.message.contains("model metadata cursor failed"));
        assertEquals("config_error", store.latestSync().status);
        assertTrue(store.latestSync().errorMessage.contains("model metadata cursor failed"));
    }

    @Test
    public void providerRetryableExceptionIsRecordedAsRetryableFailure() {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        context.getContentResolver().call(providerUri(), "retryableProviderFailure", null, null);

        ManualSyncEngine.SyncResult result = new ManualSyncEngine(context, store, gateway, RecordsSyncModels.Settings.kikuDefaults()).run();

        assertFalse(result.success);
        assertTrue(result.message.contains("AnkiDroid provider read failed: database locked"));
        assertEquals("retryable_error", store.latestSync().status);
        assertTrue(store.latestSync().errorMessage.contains("database locked"));
    }

    @Test
    public void projectionExhaustionIsRecordedAsTerminalRetryableFailure() {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        context.getContentResolver().call(providerUri(), "rejectAllCardProjections", null, null);

        ManualSyncEngine.SyncResult result = new ManualSyncEngine(context, store, gateway, RecordsSyncModels.Settings.kikuDefaults()).run();

        assertFalse(result.success);
        assertTrue(result.message.contains("AnkiDroid card projection failed"));
        assertEquals("retryable_error", store.latestSync().status);
        assertEquals(3, providerInt("cardProjectionRejects"));
    }

    @Test
    public void nullCardCursorAfterProjectionFallbacksIsRecordedAsTerminalRetryableFailure() {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        context.getContentResolver().call(providerUri(), "nullCardCursor", null, null);

        ManualSyncEngine.SyncResult result = new ManualSyncEngine(context, store, gateway, RecordsSyncModels.Settings.kikuDefaults()).run();

        assertFalse(result.success);
        assertTrue(result.message.contains("AnkiDroid returned no per-note card cursor"));
        assertEquals("retryable_error", store.latestSync().status);
    }

    @Test
    public void firstTemplateOnlyProviderRuleRejectsLaterTemplateCards() throws Exception {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        context.getContentResolver().call(providerUri(), "secondTemplateCard", null, null);

        try {
            gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults());
            fail("Expected second-template card rejection");
        } catch (AnkiDroidGateway.SyncFailure error) {
            assertTrue(error.permanentFailure);
            assertTrue(error.getMessage().contains("supports only the first card template"));
            assertTrue(error.getMessage().contains("ord 1"));
        }
    }

    @Test
    public void providerCleanupPreservesAlreadyArchivedSuspendedNoteTag() throws Exception {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        RecordsSyncModels.CollectionSnapshot snapshot = gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults());
        context.getContentResolver().call(providerUri(), "pretagSuspendedArchived", null, null);

        AnkiDroidGateway.RemovalSummary summary = gateway.removeArchivedSuspendedCards(
                snapshot,
                Collections.singletonList(suspendedImportFor(2000L, 2L, true)),
                SyncProgress.NONE
        );

        assertEquals(1, summary.sourceCards);
        assertEquals(1, summary.taggedNotes);
        assertEquals("leech kani_archived", providerString("suspendedTags"));
    }

    @Test
    public void providerCleanupUsesAllSuspendedCardsWhenNoSelectionIsSupplied() throws Exception {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        RecordsSyncModels.CollectionSnapshot snapshot = gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults());

        AnkiDroidGateway.RemovalSummary summary = gateway.removeArchivedSuspendedCards(snapshot, (SyncProgress.Listener) null);

        assertEquals(1, summary.sourceCards);
        assertEquals(1, summary.taggedNotes);
        assertEquals("kani_archived", providerString("suspendedTags"));
    }

    @Test
    public void providerCleanupCanTagWhenExistingNoteTagsCursorIsNull() throws Exception {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        RecordsSyncModels.CollectionSnapshot snapshot = gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults());
        context.getContentResolver().call(providerUri(), "nullNoteCursor", null, null);

        AnkiDroidGateway.RemovalSummary summary = gateway.removeArchivedSuspendedCards(
                snapshot,
                Collections.singletonList(suspendedImportFor(2000L, 2L, true)),
                SyncProgress.NONE
        );

        assertEquals(1, summary.sourceCards);
        assertEquals(1, summary.taggedNotes);
        assertEquals("kani_archived", providerString("suspendedTags"));
    }

    @Test
    public void providerCleanupKeepsPartiallySuspendedNotesInLocalArchive() throws Exception {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        context.getContentResolver().call(providerUri(), "failSuspendedSearch", null, null);
        context.getContentResolver().call(providerUri(), "partiallySuspendedNote", null, null);
        RecordsSyncModels.CollectionSnapshot snapshot = gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults());

        AnkiDroidGateway.RemovalSummary summary = gateway.removeArchivedSuspendedCards(
                snapshot,
                Collections.singletonList(suspendedImportFor(2000L, 2L, true)),
                SyncProgress.NONE
        );

        assertEquals(1, summary.sourceCards);
        assertEquals(0, summary.taggedNotes);
        assertTrue(summary.message.contains("kept in the local archive"));
        assertEquals("", providerString("suspendedTags"));
    }

    @Test
    public void providerCleanupReportsPartialTagWhenSelectedSuspendedCardsAreMixed() {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        RecordsSyncModels.CollectionSnapshot snapshot = snapshotWithCards(
                card(10L, 1L, true),
                card(11L, 1L, true),
                card(20L, 2L, true)
        );

        AnkiDroidGateway.RemovalSummary summary = gateway.removeArchivedSuspendedCards(
                snapshot,
                Arrays.asList(
                        suspendedImportFor("箱", 20L, 2L, true),
                        new RecordsImportModels.SuspendedImport(
                                "確",
                                100,
                                true,
                                3000,
                                Arrays.asList(
                                        suspendedSource("確", 10L, 1L, true),
                                        suspendedSource("確", 11L, 1L, false)
                                )
                        )
                ),
                null
        );

        assertEquals(2, summary.sourceCards);
        assertEquals(1, summary.taggedNotes);
        assertTrue(summary.message.contains("partly tagged"));
        assertEquals("kani_archived", providerString("suspendedTags"));
    }

    @Test
    public void providerCleanupKeepsLocalArchiveWhenSelectedNoteCannotBeUpdated() {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);

        AnkiDroidGateway.RemovalSummary summary = gateway.removeArchivedSuspendedCards(
                snapshotWithCards(card(9990L, 999L, true)),
                Collections.singletonList(suspendedImportFor("謎", 9990L, 999L, true)),
                SyncProgress.NONE
        );

        assertEquals(1, summary.sourceCards);
        assertEquals(0, summary.taggedNotes);
        assertTrue(summary.message.contains("kept in the local archive"));
        assertEquals("", providerString("suspendedTags"));
    }

    @Test
    public void unparseableFsrsDataDoesNotBlockProviderRead() throws Exception {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        context.getContentResolver().call(providerUri(), "unparseableFsrsData", null, null);

        RecordsSyncModels.CollectionSnapshot snapshot = gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults());

        assertEquals(2, snapshot.cards.size());
        assertNull(snapshot.cards.get(0).fsrsStability);
        assertNull(snapshot.cards.get(0).fsrsDifficulty);
        assertNull(snapshot.cards.get(0).fsrsRetrievability);
    }

    @Test
    public void parseableFsrsDataCanSupplyMemoryState() throws Exception {
        AnkiDroidGateway gateway = AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY);
        context.getContentResolver().call(providerUri(), "dataOnlyFsrs", null, null);

        RecordsSyncModels.CollectionSnapshot snapshot = gateway.readCollection(RecordsSyncModels.Settings.kikuDefaults());

        assertClose(12.5, snapshot.cards.get(0).fsrsStability);
        assertClose(7.0, snapshot.cards.get(0).fsrsDifficulty);
        assertClose(0.42, snapshot.cards.get(0).fsrsRetrievability);
    }

    private void resetProvider() {
        context.getContentResolver().call(providerUri(), "reset", null, null);
    }

    private int providerInt(String method) {
        Bundle result = context.getContentResolver().call(providerUri(), method, null, null);
        return result == null ? -1 : result.getInt("value", -1);
    }

    private String providerString(String method) {
        Bundle result = context.getContentResolver().call(providerUri(), method, null, null);
        return result == null ? null : result.getString("value");
    }

    private AnkiDroidGateway permissionedGateway(String permission) throws Exception {
        Class<?> targetClass = Class.forName(AnkiDroidGateway.class.getName() + "$ProviderTarget");
        Constructor<?> targetConstructor = targetClass.getDeclaredConstructor(String.class, String.class);
        targetConstructor.setAccessible(true);
        Object target = targetConstructor.newInstance(FakeAnkiDroidProvider.AUTHORITY, permission);
        Constructor<AnkiDroidGateway> gatewayConstructor = AnkiDroidGateway.class.getDeclaredConstructor(Context.class, List.class);
        gatewayConstructor.setAccessible(true);
        return gatewayConstructor.newInstance(context, Collections.singletonList(target));
    }

    private void assertPermissionFailure(ThrowingGatewayCall call) throws Exception {
        try {
            call.run();
            fail("Expected permission failure");
        } catch (AnkiDroidGateway.SyncFailure error) {
            assertTrue(error.permanentFailure);
            assertTrue(error.getMessage().contains("permission is missing"));
        }
    }

    private RecordsImportModels.DashboardRow rowFor(List<RecordsImportModels.DashboardRow> rows, String kanji) {
        for (RecordsImportModels.DashboardRow row : rows) {
            if (kanji.equals(row.kanji)) {
                return row;
            }
        }
        fail("Expected dashboard row for " + kanji);
        return null;
    }

    private Uri providerUri() {
        return Uri.parse("content://" + FakeAnkiDroidProvider.AUTHORITY);
    }

    private RecordsImportModels.SuspendedImport suspendedImportFor(long cardId, long noteId, boolean suspended) {
        return suspendedImportFor("箱", cardId, noteId, suspended);
    }

    private RecordsImportModels.SuspendedImport suspendedImportFor(String kanji, long cardId, long noteId, boolean suspended) {
        return new RecordsImportModels.SuspendedImport(kanji, 2500, true, 3000, Collections.singletonList(
                suspendedSource(kanji, cardId, noteId, suspended)
        ));
    }

    private RecordsImportModels.SuspendedSource suspendedSource(String kanji, long cardId, long noteId, boolean suspended) {
        RecordsImportModels.SuspendedSourceDetails.Builder details = RecordsImportModels.SuspendedSourceDetails
                .builder(kanji + "を見た。")
                .suspended(suspended)
                .forcePractice(suspended)
                .sourceType(suspended ? RecordsBase.SOURCE_SUSPENDED : RecordsBase.SOURCE_ACTIVE);
        return new RecordsImportModels.SuspendedSource(
                kanji,
                cardId,
                noteId,
                kanji + "箱",
                "かな",
                "meaning",
                details.build()
        );
    }

    private RecordsSyncModels.CollectionSnapshot snapshotWithCards(RecordsSyncModels.Card... cards) {
        return new RecordsSyncModels.CollectionSnapshot(Collections.emptyList(), Arrays.asList(cards));
    }

    private RecordsSyncModels.Card card(long cardId, long noteId, boolean suspended) {
        return new RecordsSyncModels.Card(cardId, noteId, 0, "Mining", suspended ? -1 : 2, 2, 0, 0, 0, 0, suspended);
    }

    private RecordsSyncModels.Settings customMappedSettings() {
        RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();
        return new RecordsSyncModels.Settings(
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
                defaults.writingTriggerMissDays
        );
    }

    private RecordsSyncModels.Settings settingsWithModel(String modelName) {
        RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();
        return new RecordsSyncModels.Settings(
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
                defaults.writingTriggerMissDays
        );
    }

    private RecordsSyncModels.Settings settingsWithExpressionField(String expressionField) {
        RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();
        return new RecordsSyncModels.Settings(
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
                defaults.writingTriggerMissDays
        );
    }

    private RecordsSyncModels.Settings browserQueryOnlySettings() {
        return browserQuerySettings(true, "tag:kani");
    }

    private RecordsSyncModels.Settings browserQuerySettings(boolean enabled, String query) {
        RecordsSyncModels.Settings defaults = RecordsSyncModels.Settings.kikuDefaults();
        return new RecordsSyncModels.Settings(
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
                Collections.emptyList(),
                false,
                defaults.importWeakFsrsDifficultyThreshold,
                defaults.importWeakLapsesThreshold,
                defaults.importMinMatchingCardsPerKanji,
                enabled,
                query
        );
    }

    private interface ThrowingGatewayCall {
        void run() throws Exception;
    }

    private static void assertClose(double expected, Double actual) {
        assertTrue(actual != null && Math.abs(expected - actual) <= 0.001);
    }
}
