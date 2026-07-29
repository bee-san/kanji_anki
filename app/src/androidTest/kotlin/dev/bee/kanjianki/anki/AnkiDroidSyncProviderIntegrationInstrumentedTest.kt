package dev.bee.kanjianki.anki

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.sync.createManualSyncEngine
import dev.bee.kanjianki.testing.DeviceRisk
import dev.bee.kanjianki.testing.DeviceSmoke
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@DeviceRisk
class AnkiDroidSyncProviderIntegrationInstrumentedTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.deleteDatabase(DATABASE_NAME)
        store = LocalStore(context)
        resetProvider()
    }

    @After
    fun tearDown() {
        if (::store.isInitialized) {
            store.close()
        }
        if (::context.isInitialized) {
            context.deleteDatabase(DATABASE_NAME)
            resetProvider()
        }
    }

    @Test
    @DeviceSmoke
    fun manualSyncWorksAgainstFakeAnkiDroidProviderContract() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val gateway = gateway()

        val result = createManualSyncEngine(context, store, gateway, settings).run()

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
        providerCall("failSuspendedSearch")

        val result = createManualSyncEngine(context, store, gateway(), settings).run()

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
    fun manualSyncFallsBackWhenBulkSchedulerProjectionIsUnsupported() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        providerCall("rejectSchedulerProjection")

        val result = createManualSyncEngine(context, store, gateway(), settings).run()

        assertTrue(result.success)
        assertEquals("success", store.latestSync()?.status)
        assertFalse(store.dashboardRows().isEmpty())
        assertEquals(4, providerInt("topLevelCardsQueries"))
        assertEquals(3, providerInt("schedulerProjectionRejects"))
        assertEquals(0, providerInt("perNoteCardsQueries"))
        assertEquals(0, providerInt("explicitIdProjectionQueries"))
    }

    @Test
    @DeviceSmoke
    fun manualSyncFallsBackWhenBulkSchedulerCursorThrowsUnknownQueue() {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        providerCall("deferSchedulerProjectionFailure")

        val result = createManualSyncEngine(context, store, gateway(), settings).run()

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
        providerCall("legacyTopLevelCardsUnsupported")

        val result = createManualSyncEngine(context, store, gateway(), settings).run()

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
        providerCall("rejectFsrsProjection")

        val result = createManualSyncEngine(context, store, gateway(), settings).run()

        assertTrue(result.message, result.success)
        assertEquals("success", store.latestSync()?.status)
        assertFalse(store.dashboardRows().isEmpty())
        assertEquals(2, providerInt("fsrsProjectionRejects"))
        assertEquals(0, providerInt("schedulerProjectionRejects"))
        assertEquals(3, providerInt("topLevelCardsQueries"))
        assertEquals(0, providerInt("perNoteCardsQueries"))
    }

    @Test
    fun browserQueryMarksMatchingActiveCardForImport() {
        providerCall("browserQueryMatchesActive")

        val result = createManualSyncEngine(
            context,
            store,
            gateway(),
            browserQueryOnlySettings(),
        ).run()

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
    fun browserQueryRereadsMissingMatchedNoteBeforeManualImport() {
        providerCall("browserQueryMatchesMissingNote")

        val result = createManualSyncEngine(
            context,
            store,
            gateway(),
            browserQueryOnlySettings(),
        ).run()

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
    fun browserQueryPermanentErrorIsRecordedAsConfigFailure() {
        providerCall("failBrowserQuery")

        val result = createManualSyncEngine(
            context,
            store,
            gateway(),
            browserQueryOnlySettings(),
        ).run()

        assertFalse(result.success)
        assertTrue(result.message?.contains("could not run the browser query") == true)
        assertEquals("config_error", store.latestSync()?.status)
        assertEquals(0, store.suspendedImports().size)
    }

    @Test
    fun providerPermanentExceptionIsRecordedAsConfigFailure() {
        providerCall("permanentProviderFailure")

        val result = createManualSyncEngine(
            context,
            store,
            gateway(),
            RecordsSyncModels.Settings.kikuDefaults(),
        ).run()

        assertFalse(result.success)
        assertTrue(result.message?.contains("model metadata cursor failed") == true)
        assertEquals("config_error", store.latestSync()?.status)
        assertTrue(store.latestSync()?.errorMessage?.contains("model metadata cursor failed") == true)
    }

    @Test
    fun providerRetryableExceptionIsRecordedAsRetryableFailure() {
        providerCall("retryableProviderFailure")

        val result = createManualSyncEngine(
            context,
            store,
            gateway(),
            RecordsSyncModels.Settings.kikuDefaults(),
        ).run()

        assertFalse(result.success)
        assertTrue(result.message?.contains("AnkiDroid provider read failed: database locked") == true)
        assertEquals("retryable_error", store.latestSync()?.status)
        assertTrue(store.latestSync()?.errorMessage?.contains("database locked") == true)
    }

    @Test
    fun projectionExhaustionIsRecordedAsTerminalRetryableFailure() {
        providerCall("rejectAllCardProjections")

        val result = createManualSyncEngine(
            context,
            store,
            gateway(),
            RecordsSyncModels.Settings.kikuDefaults(),
        ).run()

        assertFalse(result.success)
        assertTrue(result.message?.contains("AnkiDroid card projection failed") == true)
        assertEquals("retryable_error", store.latestSync()?.status)
        assertEquals(4, providerInt("cardProjectionRejects"))
        assertEquals(4, providerInt("topLevelCardsQueries"))
    }

    @Test
    fun nullCardCursorAfterProjectionFallbacksIsRecordedAsTerminalRetryableFailure() {
        providerCall("nullCardCursor")

        val result = createManualSyncEngine(
            context,
            store,
            gateway(),
            RecordsSyncModels.Settings.kikuDefaults(),
        ).run()

        assertFalse(result.success)
        assertTrue(result.message?.contains("AnkiDroid returned no bulk card cursor") == true)
        assertEquals("retryable_error", store.latestSync()?.status)
    }

    private fun gateway(): AnkiDroidGateway =
        AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)

    private fun resetProvider() {
        providerCall("reset")
    }

    private fun providerCall(method: String) {
        context.contentResolver.call(providerUri(), method, null, null)
    }

    private fun providerInt(method: String): Int =
        context.contentResolver.call(providerUri(), method, null, null)
            ?.getInt("value", -1) ?: -1

    private fun providerUri(): Uri =
        Uri.parse("content://${FakeAnkiDroidProvider.AUTHORITY}")

    private fun rowFor(
        rows: List<RecordsImportModels.DashboardRow>,
        kanji: String,
    ): RecordsImportModels.DashboardRow =
        rows.firstOrNull { it.kanji == kanji }
            ?: throw AssertionError("Expected dashboard row for $kanji")

    private fun browserQueryOnlySettings(): RecordsSyncModels.Settings {
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
            true,
            "tag:kani",
        )
    }

    private companion object {
        private const val DATABASE_NAME = "kanji_anki_simple.db"
    }
}
