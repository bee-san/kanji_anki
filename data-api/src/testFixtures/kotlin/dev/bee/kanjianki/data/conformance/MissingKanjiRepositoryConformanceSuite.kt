package dev.bee.kanjianki.data.conformance

import dev.bee.kanjianki.core.AnkiKanjiInventory
import dev.bee.kanjianki.core.MissingKanjiCandidate
import dev.bee.kanjianki.core.MissingKanjiExportReceipt
import dev.bee.kanjianki.core.MissingKanjiFrequencyRange
import dev.bee.kanjianki.core.MissingKanjiPreferences
import dev.bee.kanjianki.core.MissingKanjiScanStatus
import dev.bee.kanjianki.data.AddManualKanjiSourcesCommand
import dev.bee.kanjianki.data.DeactivateManualKanjiSourcesCommand
import dev.bee.kanjianki.data.PublishMissingKanjiInventoryCommand
import dev.bee.kanjianki.data.RecordMissingKanjiScanCommand
import dev.bee.kanjianki.data.RemoveManualKanjiSourcesCommand
import dev.bee.kanjianki.data.StoreResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue

/**
 * The Goal 183 cross-implementation contract for Missing Kanji persistence: the
 * legacy Android `LocalStore` MissingKanjiStore adapter and the shared
 * `:data-sql` MissingKanjiRepository must be indistinguishable across scan
 * publication, preferences, manual sources, and export receipts.
 */
class MissingKanjiRepositoryConformanceSuite(
    private val host: RepositoryConformanceHost,
) {
    suspend fun runAll() {
        emptyStateIsStable()
        inventoryPublicationAndFailedScans()
        preferencesRoundTrip()
        manualSourceLifecycle()
        exportReceiptsRoundTrip()
    }

    private suspend fun emptyStateIsStable() {
        host.reset()
        val state = host.missingKanji.inventoryState().expect("empty inventory state")
        assertNull("no scan published yet", state.published)
        assertNull(state.latestAttempt)
        assertFalse(state.isStale)
        assertTrue(host.missingKanji.manualSources(activeOnly = true).expect("empty manual sources").isEmpty())
        assertTrue(host.missingKanji.admittedManualSources().expect("empty admitted").isEmpty())
        assertTrue(host.missingKanji.removableManualSourceLiterals().expect("empty removable").isEmpty())
        assertNull(host.missingKanji.manualSource("裂").expect("no manual source"))
        assertEquals(
            MissingKanjiPreferences(),
            host.missingKanji.loadPreferences().expect("default preferences"),
        )
    }

    private suspend fun inventoryPublicationAndFailedScans() {
        host.reset()
        val published = host.missingKanji.publishInventory(
            PublishMissingKanjiInventoryCommand(
                inventory = AnkiKanjiInventory(
                    literals = linkedSetOf("裂", "脱", "痛"),
                    notesScanned = 120,
                    fieldsScanned = 240,
                    skippedNotes = 3,
                    modelCount = 2,
                    malformedRowWarning = null,
                ),
                startedAtMillis = NOW,
                completedAtMillis = NOW + 5_000,
                providerFingerprint = "authority=com.ichi2.anki.flashcards;spec=1",
            ),
        ).expect("publish inventory")
        assertEquals(MissingKanjiScanStatus.SUCCESS, published.status)
        assertEquals(3, published.uniqueKanjiCount)

        val state = host.missingKanji.inventoryState().expect("state after publish")
        assertNotNull(state.published)
        assertEquals(setOf("裂", "脱", "痛"), state.published?.literals)
        assertFalse("a fresh publish is not stale", state.isStale)

        // A later failed scan becomes the latest attempt and marks the state stale.
        host.missingKanji.recordUnsuccessfulScan(
            RecordMissingKanjiScanCommand(
                status = MissingKanjiScanStatus.FAILED,
                startedAtMillis = NOW + 10_000,
                completedAtMillis = NOW + 11_000,
                notesScanned = 0,
                fieldsScanned = 0,
                uniqueKanjiCount = 0,
                skippedNotes = 0,
                modelCount = 0,
                providerFingerprint = "authority=com.ichi2.anki.flashcards;spec=1",
                failureCode = "provider_unavailable",
            ),
        ).expect("record failed scan")
        val stale = host.missingKanji.inventoryState().expect("state after failed scan")
        assertEquals(MissingKanjiScanStatus.FAILED, stale.latestAttempt?.status)
        assertEquals("the published inventory is retained", setOf("裂", "脱", "痛"), stale.published?.literals)
        assertTrue("a newer failed scan makes the published inventory stale", stale.isStale)
    }

    private suspend fun preferencesRoundTrip() {
        host.reset()
        val custom = MissingKanjiPreferences(
            preset = MissingKanjiPreferences.PRESET_CUSTOM,
            range = MissingKanjiFrequencyRange(minimumRank = 500, maximumRank = 3_500, includeUnranked = true),
            searchQuery = "  radical  ",
        )
        assertTrue(host.missingKanji.savePreferences(custom).isOk())
        val stored = host.missingKanji.loadPreferences().expect("stored preferences")
        assertEquals(MissingKanjiPreferences.PRESET_CUSTOM, stored.preset)
        assertEquals(500, stored.range.minimumRank)
        assertEquals(3_500, stored.range.maximumRank)
        assertTrue(stored.range.includeUnranked)
        assertEquals("the search query is trimmed", "radical", stored.searchQuery)
    }

    private suspend fun manualSourceLifecycle() {
        host.reset()
        val write = host.missingKanji.addManualSources(
            AddManualKanjiSourcesCommand(
                candidates = listOf(
                    MissingKanjiCandidate("裂", listOf("split"), emptyList(), listOf("さ.く"), 1_200),
                    MissingKanjiCandidate("烈", listOf("fierce"), listOf("レツ"), emptyList(), 1_500),
                    // Missing meaning: rejected by admission policy.
                    MissingKanjiCandidate("鬱", emptyList(), emptyList(), emptyList(), 9_000),
                ),
                nowMillis = NOW,
            ),
        ).expect("add manual sources")
        assertEquals(setOf("裂", "烈"), write.addedLiterals)
        assertTrue("the meaning-less candidate is refused", write.missingMeaningLiterals.contains("鬱"))

        val active = host.missingKanji.manualSources(activeOnly = true).expect("active manual sources")
        assertEquals(listOf("裂", "烈"), active.map { it.candidate.literal })
        assertTrue(active.all { it.active })

        assertEquals("裂", host.missingKanji.manualSource("裂").expect("single manual source")?.candidate?.literal)

        // Both are unreviewed, so both are removable.
        assertEquals(setOf("裂", "烈"), host.missingKanji.removableManualSourceLiterals().expect("removable"))
        val removal = host.missingKanji.removeUnreviewedManualSources(
            RemoveManualKanjiSourcesCommand(listOf("裂"), NOW + 1),
        ).expect("remove unreviewed")
        assertEquals(setOf("裂"), removal.removedLiterals)
        assertEquals(
            listOf("烈"),
            host.missingKanji.manualSources(activeOnly = true).expect("after removal").map { it.candidate.literal },
        )

        // Deactivating the rest empties the active set.
        assertEquals(1, host.missingKanji.deactivateManualSources(
            DeactivateManualKanjiSourcesCommand(listOf("烈"), NOW + 2),
        ).expect("deactivate"))
        assertTrue(host.missingKanji.manualSources(activeOnly = true).expect("after deactivate").isEmpty())
        assertEquals(
            "deactivated sources still exist as inactive rows",
            setOf("裂", "烈"),
            host.missingKanji.manualSources(activeOnly = false).expect("all sources").map { it.candidate.literal }.toSet(),
        )
    }

    private suspend fun exportReceiptsRoundTrip() {
        host.reset()
        assertTrue(host.missingKanji.exportReceipts("csv").expect("empty receipts").isEmpty())
        val written = host.missingKanji.recordExportReceipts(
            listOf(
                MissingKanjiExportReceipt("裂", "csv", NOW, externalNoteId = null),
                MissingKanjiExportReceipt("脱", "csv", NOW + 1, externalNoteId = 42L),
                MissingKanjiExportReceipt("痛", "anki", NOW + 2, externalNoteId = 7L),
            ),
        ).expect("record receipts")
        assertEquals(3, written)
        val csv = host.missingKanji.exportReceipts("csv").expect("csv receipts")
        assertEquals(setOf("裂", "脱"), csv.keys)
        assertNull("a null external note id round-trips", csv["裂"]?.externalNoteId)
        assertEquals(42L, csv["脱"]?.externalNoteId)
        assertEquals(
            setOf("痛"),
            host.missingKanji.exportReceipts("anki").expect("anki receipts").keys,
        )
    }

    private fun <T> StoreResult<T>.expect(label: String): T {
        assertTrue("$label must succeed, got $this", isOk())
        if (this is StoreResult.Ok) {
            return value
        }
        throw AssertionError("$label was not Ok: $this")
    }

    private companion object {
        const val NOW = 1_770_100_000_000L
    }
}
