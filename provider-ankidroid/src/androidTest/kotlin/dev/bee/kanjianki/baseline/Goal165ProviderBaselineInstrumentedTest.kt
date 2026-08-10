package dev.bee.kanjianki.baseline

import android.content.Context
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.anki.AnkiDroidCollectionInventoryGateway
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.anki.AnkiMissingKanjiWriter
import dev.bee.kanjianki.anki.FakeAnkiDroidProvider
import dev.bee.kanjianki.core.MissingKanjiCandidate
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.syncapi.CollectionProgressListener
import dev.bee.kanjianki.testing.DeviceRisk
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Sanitized provider fixture shared by later Android/desktop sync parity work.
 *
 * This deliberately exercises the debug fake provider instead of a copied user
 * collection. Large HTML/glossary fields and generated provider row ids are not
 * rendered, so the checked-in contract is deterministic and safe to publish.
 */
@RunWith(AndroidJUnit4::class)
@DeviceRisk
class Goal165ProviderBaselineInstrumentedTest {
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
    fun sanitizedFakeProviderContractMatchesGoldenSnapshot() {
        val actual = actualSnapshot()
        if (
            InstrumentationRegistry.getArguments()
                .getString(RECORD_ARGUMENT)
                ?.toBooleanStrictOrNull() == true
        ) {
            val output = File(requireNotNull(context.getExternalFilesDir(null)), "goal165-provider.snapshot.txt")
            output.writeText("$actual\n", StandardCharsets.UTF_8)
            return
        }
        assertEquals(expectedSnapshot(), actual)
    }

    private fun actualSnapshot(): String = buildString {
        appendLine("goal165 provider baseline v1")
        appendLine("personal-data=none")

        resetProvider()
        val inventoryGateway = inventoryGateway()
        val capability = inventoryGateway.status()
        val noteTypes = gateway().noteTypes()
        appendLine()
        appendLine("[capability]")
        appendLine(
            "status installed=${capability.installed} permission=${capability.permissionGranted} " +
                "read=${capability.canReadCollection} write=${capability.canWriteCollection} " +
                "spec=${capability.providerSpecVersion} authority=${capability.authority}",
        )
        appendLine(
            "note-types=" + noteTypes.joinToString(";") { type ->
                "${type.modelId}:${type.name}:${type.fields.joinToString(",")}"
            },
        )

        resetProvider()
        appendCollection("configured-kiku", gateway().readCollection(RecordsSyncModels.Settings.kikuDefaults()))

        resetProvider()
        val custom = customMappedSettings()
        appendCollection("configured-custom", gateway().readCollection(custom), custom)

        resetProvider()
        val active = gateway().readCollection(browserQuerySettings("tag:kani_contract_active"))
        appendLine()
        appendLine("[browser-query]")
        appendLine("active-matches=${matchedCardIds(active)} queries=${providerInt("browserQueryQueries")}")
        resetProvider()
        val suspended = gateway().readCollection(browserQuerySettings("tag:kani_contract_suspended"))
        appendLine(
            "suspended-matches=${matchedCardIds(suspended)} suspended=${suspended.cards.filter { it.suspended }.map { it.cardId }} " +
                "queries=${providerInt("browserQueryQueries")}",
        )

        appendFsrsFallbacks()
        appendTagWriteBack()
        appendInventory()
        appendMissingKanjiExport()
        appendCancellationOutcomes()
    }.trimEnd()

    private fun StringBuilder.appendCollection(
        name: String,
        snapshot: RecordsSyncModels.CollectionSnapshot,
        settings: RecordsSyncModels.Settings = RecordsSyncModels.Settings.kikuDefaults(),
    ) {
        appendLine()
        appendLine("[$name]")
        snapshot.notes.sortedBy { it.noteId }.forEach { note ->
            appendLine(
                "note id=${note.noteId} model=${note.modelId}:${note.modelName} " +
                    "expression=${note.expression(settings)} reading=${note.reading(settings)} " +
                    "meaning=${note.meaning(settings)} sentence=${note.sentence(settings)} " +
                    "tags=${note.tags.sorted()}",
            )
        }
        snapshot.cards.sortedBy { it.cardId }.forEach { card ->
            appendLine(
                "card id=${card.cardId} note=${card.noteId} ord=${card.ord} " +
                    "deck=${card.deckId}/${card.deckName} queue=${card.queue} type=${card.type} " +
                    "due=${card.due} interval=${card.intervalDays} reps=${card.reps} lapses=${card.lapses} " +
                    "suspended=${card.suspended} fsrs=${nullable(card.fsrsStability)}," +
                    "${nullable(card.fsrsDifficulty)},${nullable(card.fsrsRetrievability)} " +
                    "browser=${card.browserQueryMatched}",
            )
        }
        appendLine(
            "queries bulk=${providerInt("topLevelCardsQueries")} per-note=${providerInt("perNoteCardsQueries")} " +
                "explicit-id=${providerInt("explicitIdProjectionQueries")}",
        )
    }

    private fun StringBuilder.appendFsrsFallbacks() {
        appendLine()
        appendLine("[fsrs-fallbacks]")
        resetProvider()
        providerCall("rejectFsrsProjection")
        val nullable = gateway().readCollection(RecordsSyncModels.Settings.kikuDefaults())
        appendLine(
            "nullable=${fsrs(nullable)} rejects=${providerInt("fsrsProjectionRejects")} " +
                "bulk=${providerInt("topLevelCardsQueries")}",
        )

        resetProvider()
        providerCall("dataOnlyFsrs")
        appendLine("data-only=${fsrs(gateway().readCollection(RecordsSyncModels.Settings.kikuDefaults()))}")

        resetProvider()
        providerCall("unparseableFsrsData")
        appendLine("malformed=${fsrs(gateway().readCollection(RecordsSyncModels.Settings.kikuDefaults()))}")
    }

    private fun StringBuilder.appendTagWriteBack() {
        appendLine()
        appendLine("[note-tag-write-back]")
        resetProvider()
        val archiveGateway = gateway()
        val archiveSnapshot = archiveGateway.readCollection(RecordsSyncModels.Settings.kikuDefaults())
        val archive = archiveGateway.removeArchivedSuspendedCards(
            archiveSnapshot,
            CollectionProgressListener.NONE,
        )
        appendLine(
            "archive source=${archive.sourceCards} deleted=${archive.deletedNotes} tagged=${archive.taggedNotes} " +
                "final-tags=${providerString("suspendedTags")}",
        )

        resetProvider()
        val repaired = gateway().tagRepairedNotes(
            setOf(2L, 1L),
            CollectionProgressListener.NONE,
        )
        appendLine(
            "repaired requested=${repaired.requestedNoteIds.sorted()} tagged=${repaired.taggedNoteIds.sorted()} " +
                "failed=${repaired.failedNoteIds.sorted()} note1=${providerString("repairedTagsForNote", "1")} " +
                "note2=${providerString("repairedTagsForNote", "2")} updates=${providerInt("repairedTagUpdates")}",
        )
    }

    private fun StringBuilder.appendInventory() {
        appendLine()
        appendLine("[collection-inventory]")
        resetProvider()
        val notes = mutableListOf<AnkiDroidCollectionInventoryGateway.CollectionNote>()
        val complete = inventoryGateway().scan(notes::add)
        appendLine(
            "complete notes=${complete.notesRead} skipped=${complete.skippedNotes} models=${complete.modelCount} " +
                "mode=${complete.queryMode} ids=${notes.map { it.noteId }} " +
                "field-counts=${notes.map { it.fields.size }}",
        )

        resetProvider()
        providerCall("inventoryMalformedRow")
        val malformedNotes = mutableListOf<AnkiDroidCollectionInventoryGateway.CollectionNote>()
        val malformed = inventoryGateway().scan(malformedNotes::add)
        appendLine(
            "malformed notes=${malformed.notesRead} skipped=${malformed.skippedNotes} " +
                "ids=${malformedNotes.map { it.noteId }}",
        )
    }

    private fun StringBuilder.appendMissingKanjiExport() {
        appendLine()
        appendLine("[missing-kanji-export]")
        resetProvider()
        val receipts = mutableListOf<AnkiMissingKanjiWriter.ConfirmedNote>()
        val result = writer().export(
            candidates = listOf(
                candidate("火", 20),
                candidate("水", 10),
                candidate("水", 11),
                candidate("invalid", 12),
            ),
            receiptSink = AnkiMissingKanjiWriter.ReceiptSink { _, confirmed ->
                receipts += confirmed
                true
            },
        )
        appendLine(
            "result completed=${result.completed} requested=${result.requestedCount} valid=${result.validCount} " +
                "created=${result.createdCount} present=${result.alreadyPresentCount} invalid=${result.invalidCount} " +
                "duplicates=${result.duplicateRequestCount} unfinished=${result.unfinishedLiterals.sorted()}",
        )
        appendLine(
            "created-literals=${result.createdNotes.keys.sorted()} receipt-literals=${receipts.map { it.literal }.sorted()} " +
                "source-ids=${providerString("exportedSourceIds").split('|').filter(String::isNotBlank).sorted()} " +
                "bulk-inserts=${providerInt("exportBulkInsertCalls")}",
        )
    }

    private fun StringBuilder.appendCancellationOutcomes() {
        appendLine()
        appendLine("[cancellation]")
        resetProvider()
        providerCall("deferBrowserQueryCancellation")
        val browserFailure = try {
            gateway().readCollection(browserQuerySettings("tag:kani"))
            "none"
        } catch (error: AnkiDroidGateway.SyncFailure) {
            "permanent=${error.permanentFailure} message=${error.message}"
        }
        appendLine("browser=$browserFailure")

        resetProvider()
        var cancelInventory = false
        val partialNotes = mutableListOf<Long>()
        val cancellableInventory = AnkiDroidCollectionInventoryGateway.testProvider(
            context,
            FakeAnkiDroidProvider.AUTHORITY,
            AnkiDroidCollectionInventoryGateway.Cancellation { cancelInventory },
        )
        val inventoryFailure = try {
            cancellableInventory.scan(consumer = { note ->
                partialNotes += note.noteId
                if (partialNotes.size == 2) cancelInventory = true
            })
            "none"
        } catch (error: AnkiDroidCollectionInventoryGateway.Failure) {
            error.kind.name
        }
        appendLine("inventory kind=$inventoryFailure partial-note-ids=$partialNotes")

        resetProvider()
        val cancelled = AtomicBoolean(false)
        val export = AnkiMissingKanjiWriter.testProvider(
            context,
            FakeAnkiDroidProvider.AUTHORITY,
            cancellation = AnkiMissingKanjiWriter.Cancellation(cancelled::get),
        ).export(
            candidates = candidates(101),
            progress = AnkiMissingKanjiWriter.ProgressListener { progress ->
                if (progress.processedCount >= 100) cancelled.set(true)
            },
        )
        appendLine(
            "export kind=${export.failureKind} created=${export.createdCount} " +
                "unfinished=${export.unfinishedLiterals.size} bulk-inserts=${providerInt("exportBulkInsertCalls")}",
        )
    }

    private fun expectedSnapshot(): String =
        InstrumentationRegistry.getInstrumentation().context.assets
            .open("goal165/provider.snapshot.txt")
            .use { input -> String(input.readBytes(), StandardCharsets.UTF_8).trimEnd() }

    private fun gateway(): AnkiDroidGateway =
        AnkiDroidGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)

    private fun inventoryGateway(): AnkiDroidCollectionInventoryGateway =
        AnkiDroidCollectionInventoryGateway.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)

    private fun writer(): AnkiMissingKanjiWriter =
        AnkiMissingKanjiWriter.testProvider(context, FakeAnkiDroidProvider.AUTHORITY)

    private fun matchedCardIds(snapshot: RecordsSyncModels.CollectionSnapshot): List<Long> =
        snapshot.cards.filter { it.browserQueryMatched }.map { it.cardId }.sorted()

    private fun fsrs(snapshot: RecordsSyncModels.CollectionSnapshot): String =
        snapshot.cards.sortedBy { it.cardId }.joinToString(";") { card ->
            "${card.cardId}:${nullable(card.fsrsStability)},${nullable(card.fsrsDifficulty)}," +
                nullable(card.fsrsRetrievability)
        }

    private fun nullable(value: Double?): String = value?.toString() ?: "-"

    private fun nullable(value: Int?): String = value?.toString() ?: "-"

    private fun resetProvider() = providerCall("reset")

    private fun providerCall(method: String, argument: String? = null) {
        context.contentResolver.call(providerUri(), method, argument, null)
    }

    private fun providerInt(method: String): Int =
        context.contentResolver.call(providerUri(), method, null, null)?.getInt("value", -1) ?: -1

    private fun providerString(method: String, argument: String? = null): String =
        context.contentResolver.call(providerUri(), method, argument, null)?.getString("value").orEmpty()

    private fun providerUri(): Uri = Uri.parse("content://${FakeAnkiDroidProvider.AUTHORITY}")

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

    private fun browserQuerySettings(query: String): RecordsSyncModels.Settings {
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
            query,
        )
    }

    private fun candidates(count: Int): List<MissingKanjiCandidate> =
        (1..count).map { index -> candidate(String(Character.toChars(0x4E00 + index)), index) }

    private fun candidate(literal: String, rank: Int): MissingKanjiCandidate =
        MissingKanjiCandidate(
            literal = literal,
            meanings = listOf("meaning $rank"),
            onReadings = listOf("オン"),
            kunReadings = listOf("くん"),
            jitenRank = rank,
        )

    private companion object {
        private const val RECORD_ARGUMENT = "goal165RecordProviderBaseline"
    }
}
