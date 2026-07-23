package dev.bee.kanjianki.anki

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AnkiDroidCollectionInventoryGatewayInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        resetProvider()
    }

    @After
    fun tearDown() {
        if (::context.isInitialized) {
            resetProvider()
        }
    }

    @Test
    fun scansEveryModelAndFieldWithoutFilteringSuspendedNotes() {
        val notes = mutableListOf<AnkiDroidCollectionInventoryGateway.CollectionNote>()
        val progress = mutableListOf<AnkiDroidCollectionInventoryGateway.ScanProgress>()
        val gateway = testGateway()

        val result = gateway.scan(notes::add, progress::add)

        assertEquals(4, result.notesRead)
        assertEquals(0, result.skippedNotes)
        assertEquals(2, result.modelCount)
        assertEquals(
            AnkiDroidCollectionInventoryGateway.QueryMode.DIRECT_SQL_PAGED,
            result.queryMode,
        )
        assertEquals(listOf(1L, 2L, 101L, 102L), notes.map { it.noteId })
        assertEquals(setOf("Kiku", "Custom Japanese"), notes.map { it.modelName }.toSet())
        assertEquals(7, notes.first { it.noteId == 1L }.fields.size)
        assertEquals(6, notes.first { it.noteId == 101L }.fields.size)
        assertTrue(notes.first { it.noteId == 1L }.fields.last().contains("<ruby>"))
        assertTrue(notes.first { it.noteId == 1L }.fields.last().contains("[sound:"))
        assertTrue(notes.any { it.noteId == 2L && it.fields.first() == "笥箱" })
        assertEquals(4, progress.last().notesRead)
    }

    @Test
    fun fallsBackToLegacyAllNotesSearchWhenNotesV2IsUnavailable() {
        providerCall("rejectInventoryNotesV2")
        val notes = mutableListOf<AnkiDroidCollectionInventoryGateway.CollectionNote>()

        val result = testGateway().scan(notes::add)

        assertEquals(4, result.notesRead)
        assertEquals(
            AnkiDroidCollectionInventoryGateway.QueryMode.LEGACY_SEARCH,
            result.queryMode,
        )
        assertEquals(listOf(1L, 2L, 101L, 102L), notes.map { it.noteId })
    }

    @Test
    fun skipsMalformedRowsAndKeepsCompletedInventoryRows() {
        providerCall("inventoryMalformedRow")
        val notes = mutableListOf<AnkiDroidCollectionInventoryGateway.CollectionNote>()

        val result = testGateway().scan(notes::add)

        assertEquals(4, result.notesRead)
        assertEquals(1, result.skippedNotes)
        assertEquals(listOf(1L, 2L, 101L, 102L), notes.map { it.noteId })
    }

    @Test
    fun buildsAggregateKanjiInventoryWithoutConfiguredSync() {
        providerCall("inventoryMalformedRow")
        val progress = mutableListOf<dev.bee.kanjianki.core.AnkiKanjiInventoryProgress>()

        val inventory = AnkiKanjiInventoryReader(testGateway()).read(progress::add)

        assertEquals(setOf("確", "認", "笥", "箱", "見"), inventory.literals)
        assertEquals(4, inventory.notesScanned)
        assertEquals(26, inventory.fieldsScanned)
        assertEquals(1, inventory.skippedNotes)
        assertEquals(2, inventory.modelCount)
        assertTrue(inventory.malformedRowWarning != null)
        assertTrue(progress.first().isIndeterminate)
        assertEquals(5, progress.last().uniqueKanjiCount)
    }

    @Test
    fun cancellationStopsBetweenRowsWithoutRetainingTheRemainingFields() {
        var cancelled = false
        val notes = mutableListOf<AnkiDroidCollectionInventoryGateway.CollectionNote>()
        val gateway = AnkiDroidCollectionInventoryGateway.testProvider(
            context,
            FakeAnkiDroidProvider.AUTHORITY,
            AnkiDroidCollectionInventoryGateway.Cancellation { cancelled },
        )

        try {
            gateway.scan(consumer = {
                notes.add(it)
                if (notes.size == 2) {
                    cancelled = true
                }
            })
            throw AssertionError("Expected the inventory scan to be cancelled.")
        } catch (error: AnkiDroidCollectionInventoryGateway.Failure) {
            assertEquals(AnkiDroidCollectionInventoryGateway.FailureKind.CANCELLED, error.kind)
        }

        assertEquals(listOf(1L, 2L), notes.map { it.noteId })
    }

    @Test
    fun reportsCapabilitiesAndActionableProviderFailures() {
        val status = testGateway().status()

        assertTrue(status.installed)
        assertTrue(status.permissionGranted)
        assertTrue(status.canReadCollection)
        assertTrue(status.canWriteCollection)
        assertEquals(2, status.providerSpecVersion)
        assertEquals(FakeAnkiDroidProvider.AUTHORITY, status.authority)

        providerCall("nullModelsCursor")
        try {
            testGateway().scan(consumer = { })
            throw AssertionError("Expected a provider-unavailable failure.")
        } catch (error: AnkiDroidCollectionInventoryGateway.Failure) {
            assertEquals(
                AnkiDroidCollectionInventoryGateway.FailureKind.PROVIDER_UNAVAILABLE,
                error.kind,
            )
            assertTrue(error.message.orEmpty().contains("note-model cursor"))
        }

        val missing = AnkiDroidCollectionInventoryGateway
            .testProvider(context, "dev.bee.kanjianki.missing.inventory.provider")
            .status()
        assertFalse(missing.installed)
        assertFalse(missing.canReadCollection)
        assertFalse(missing.canWriteCollection)
    }

    private fun testGateway(): AnkiDroidCollectionInventoryGateway {
        return AnkiDroidCollectionInventoryGateway.testProvider(
            context,
            FakeAnkiDroidProvider.AUTHORITY,
        )
    }

    private fun resetProvider() {
        context.contentResolver.call(providerUri(), "reset", null, null)
    }

    private fun providerCall(method: String) {
        context.contentResolver.call(providerUri(), method, null, null)
    }

    private fun providerUri(): Uri = Uri.parse("content://${FakeAnkiDroidProvider.AUTHORITY}")
}
