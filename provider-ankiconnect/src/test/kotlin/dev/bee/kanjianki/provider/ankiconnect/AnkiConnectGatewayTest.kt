package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.syncapi.CollectionAvailability
import dev.bee.kanjianki.syncapi.CollectionCancellation
import dev.bee.kanjianki.syncapi.CollectionCapability
import dev.bee.kanjianki.syncapi.CollectionFailure
import dev.bee.kanjianki.syncapi.CollectionFailureKind
import dev.bee.kanjianki.syncapi.CollectionProgress
import dev.bee.kanjianki.syncapi.CollectionProgressListener
import dev.bee.kanjianki.syncapi.testing.CollectionGatewayContractKit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiConnectGatewayTest {
    private val settings = RecordsSyncModels.Settings.kikuDefaults()
    private val model = settings.modelName

    /**
     * A scripted AnkiConnect that completes the handshake and serves one note with
     * one card. [handshakeOverrides] lets a test break exactly one handshake step
     * while leaving the rest healthy, which is how the availability mapping is
     * pinned per-step rather than by an all-or-nothing "unreachable" case.
     */
    private fun readyExchange(
        handshakeOverrides: ScriptedAnkiConnectExchange.() -> Unit = {},
    ): ScriptedAnkiConnectExchange {
        val exchange = ScriptedAnkiConnectExchange()
        exchange.onResult("requestPermission", """{"permission":"granted","requireApikey":false}""")
        exchange.onResult("version", "6")
        exchange.onResult(
            "apiReflect",
            """{"scopes":["actions"],"actions":${
                AnkiConnectActions.required.joinToString(",", "[", "]") { """"$it"""" }
            }}""",
        )
        exchange.onResult("getMediaDirPath", """"User 1"""")
        exchange.onResult("modelNamesAndIds", """{"$model":42}""")
        exchange.onResult("modelFieldNames", """["Expression","Reading","Meaning","Sentence"]""")
        exchange.onResult("findNotes", "[11]")
        exchange.onResult(
            "notesInfo",
            """[${
                ScriptedAnkiConnectExchange.noteRow(
                    11L,
                    model,
                    listOf(
                        settings.expressionField to "橋",
                        settings.readingField to "はし",
                        settings.meaningField to "bridge",
                        settings.sentenceField to "橋を渡る。",
                        settings.frequencyField to "1",
                        settings.frequencySortField to "1",
                    ),
                    tags = listOf("fixture"),
                )
            }]""",
        )
        exchange.onResult("findCards", "[110]")
        exchange.onResult(
            "cardsInfo",
            """[${ScriptedAnkiConnectExchange.cardRow(110L, 11L, model)}]""",
        )
        exchange.handshakeOverrides()
        return exchange
    }

    private fun gateway(exchange: ScriptedAnkiConnectExchange) =
        AnkiConnectGateway(exchange.transport())

    /**
     * The Goal 188 done-when condition: the AnkiConnect adapter satisfies the same
     * read contract as AnkiDroid. The kit checks note/card referential integrity,
     * capability/identity coherence, progress reporting, and pre-cancellation.
     */
    @Test
    fun satisfiesTheSharedCollectionReadContract() {
        val observation = CollectionGatewayContractKit.verifyReadContract(
            gateway(readyExchange()),
            settings,
            expectedNoteCount = 1,
            expectedCardCount = 1,
        )
        assertEquals(1, observation.noteCount)
        assertTrue(observation.progress.isNotEmpty())
    }

    /**
     * The documented capability difference. AnkiConnect exposes no FSRS memory
     * state, so the capability is absent and the fields stay null — the contract
     * kit checks the latter follows from the former, and this pins that it is
     * AnkiConnect taking that branch rather than accidentally claiming parity.
     */
    @Test
    fun neverClaimsFsrsMemoryStateAndNeverFabricatesIt() {
        val result = gateway(readyExchange()).readProviderCollection(settings)

        assertFalse(CollectionCapability.FSRS_MEMORY_STATE in result.capabilities)
        assertTrue(
            result.snapshot.cards.all {
                it.fsrsStability == null && it.fsrsDifficulty == null && it.fsrsRetrievability == null
            },
        )
        assertNotNull(result.sourceIdentity)
    }

    /**
     * The tag-write capability is advertised off what *this* Anki reported, not off
     * "AnkiConnect generally supports `addTags`". Missing Kanji stays unadvertised
     * here because that flow is a separate writer with its own gate.
     */
    @Test
    fun advertisesTagWriteOnlyWhenThisAnkiReportedTheAction() {
        val exchange = readyExchange()
        exchange.onResult(
            "apiReflect",
            """{"scopes":["actions"],"actions":${
                (AnkiConnectActions.required + AnkiConnectActions.optional)
                    .joinToString(",", "[", "]") { """"$it"""" }
            }}""",
        )

        val status = gateway(exchange).status()

        assertTrue(status.isReady())
        assertEquals(
            setOf(
                CollectionCapability.READ_COLLECTION,
                CollectionCapability.LIST_NOTE_TYPES,
                CollectionCapability.NOTE_TAG_WRITE,
            ),
            status.capabilities,
        )
        assertFalse(status.supports(CollectionCapability.MISSING_KANJI_WRITE))
        assertFalse(status.supports(CollectionCapability.COLLECTION_INVENTORY))
    }

    /**
     * An AnkiConnect that withholds `addTags` gets the read capabilities alone. A
     * declared-but-absent write is worse than an undeclared one: the caller would
     * offer the user archive/repaired write-back that could never succeed.
     */
    @Test
    fun withholdsTagWriteWhenTheActionIsUnavailable() {
        val status = gateway(readyExchange()).status()

        assertTrue(status.isReady())
        assertEquals(AnkiConnectGateway.READ_CAPABILITIES, status.capabilities)
        assertFalse(status.supports(CollectionCapability.NOTE_TAG_WRITE))
    }

    @Test
    fun anUngrantedPermissionPromptIsAuthRequiredNotUnavailable() {
        val exchange = readyExchange {
            onResult("requestPermission", """{"permission":"denied"}""")
        }

        val status = gateway(exchange).status()

        assertEquals(CollectionAvailability.AUTH_REQUIRED, status.availability)
        assertTrue(status.capabilities.isEmpty())
        assertTrue(status.message.contains("prompt"))
    }

    /**
     * A running Anki with no collection open is a configuration problem the user
     * can fix, not an unreachable provider — retrying would never help.
     */
    @Test
    fun noOpenCollectionIsInvalidConfiguration() {
        val exchange = readyExchange { onResult("getMediaDirPath", "null") }

        val status = gateway(exchange).status()

        assertEquals(CollectionAvailability.INVALID_CONFIGURATION, status.availability)
        assertTrue(status.message.contains("no collection is open"))
    }

    @Test
    fun anUnsupportedWireVersionNamesBothVersions() {
        val exchange = readyExchange { onResult("version", "5") }

        val status = gateway(exchange).status()

        assertEquals(CollectionAvailability.INVALID_CONFIGURATION, status.availability)
        assertTrue(status.message.contains("v5"))
        assertTrue(status.message.contains("v6"))
    }

    /** The message names the missing actions, so the user can act on it. */
    @Test
    fun aMissingRequiredActionIsReportedByName() {
        val exchange = readyExchange {
            onResult(
                "apiReflect",
                """{"scopes":["actions"],"actions":${
                    AnkiConnectActions.required
                        .filterNot { it == "notesInfo" || it == "cardsInfo" }
                        .joinToString(",", "[", "]") { """"$it"""" }
                }}""",
            )
        }

        val status = gateway(exchange).status()

        assertEquals(CollectionAvailability.INVALID_CONFIGURATION, status.availability)
        assertTrue(status.message.contains("cardsInfo, notesInfo"))
    }

    @Test
    fun anUnreachableAnkiIsNotAvailable() {
        val exchange = ScriptedAnkiConnectExchange()
        exchange.onRaw("requestPermission") {
            AnkiConnectTransport.HttpExchange.Result.ConnectionFailed("connection refused")
        }

        val status = gateway(exchange).status()

        assertEquals(CollectionAvailability.NOT_AVAILABLE, status.availability)
        assertTrue(status.capabilities.isEmpty())
    }

    @Test
    fun exposesNoteTypesAndBothPlainReadOverloads() {
        val gateway = gateway(readyExchange())

        assertEquals(listOf(model), gateway.noteTypes().map { it.name })
        assertEquals(1, gateway.readCollection(settings).notes.size)

        var progressSeen = 0
        val withProgress = gateway.readCollection(settings, CollectionProgressListener { progressSeen++ })
        assertEquals(1, withProgress.notes.size)
        assertTrue(progressSeen > 0)
    }

    /**
     * A snapshot whose single note is fully suspended, so it is eligible for the
     * `kani_archived` tag. [suspendedQueue] lets a test make it ineligible again.
     */
    private fun suspendedExchange(suspendedQueue: Long = -1L): ScriptedAnkiConnectExchange =
        readyExchange {
            onResult(
                "cardsInfo",
                """[${
                    ScriptedAnkiConnectExchange.cardRow(110L, 11L, model, queue = suspendedQueue)
                }]""",
            )
            onResult("addTags", "null")
        }

    /**
     * Archiving tags the fully-suspended note in Anki, so a later sync skips it.
     * The tag written is Kani's own `kani_archived`, and it goes out as a one-note
     * `addTags` action.
     */
    @Test
    fun archivingTagsFullySuspendedNotes() {
        val exchange = suspendedExchange()
        val gateway = gateway(exchange)
        val snapshot = gateway.readCollection(settings)

        val summary = gateway.removeArchivedSuspendedCards(snapshot)

        assertEquals(1, summary.sourceCards)
        assertEquals(1, summary.taggedNotes)
        // Kani never deletes a note; archiving is a tag write only.
        assertEquals(0, summary.deletedNotes)
        assertTrue(summary.message, summary.message.contains("tagged in Anki"))
        val sent = exchange.anyBodiesFor("addTags").single()
        assertTrue(sent, sent.contains("kani_archived"))
        assertEquals(listOf(11L), requestedNoteIds(sent))
    }

    /**
     * A collection with nothing suspended must not send a write at all. Anki is the
     * user's data; "no work" has to mean "no request", not "an empty request".
     */
    @Test
    fun archivingSendsNoWriteWhenNothingIsSuspended() {
        val exchange = suspendedExchange(suspendedQueue = 2L)
        val gateway = gateway(exchange)

        val summary = gateway.removeArchivedSuspendedCards(gateway.readCollection(settings))

        assertEquals(0, summary.sourceCards)
        assertEquals(0, summary.taggedNotes)
        assertTrue(exchange.received.none { it.contains("addTags") })
    }

    /**
     * The sync is already committed by the time archiving runs, so a refused write
     * is reported, never thrown — and the copy has to say the local archive kept
     * the leftovers, because that is what actually happened.
     */
    @Test
    fun aRefusedArchiveWriteIsReportedRatherThanThrown() {
        val exchange = suspendedExchange()
        exchange.onError("addTags", "collection is not open")
        val gateway = gateway(exchange)
        val snapshot = gateway.readCollection(settings)

        val summary = gateway.removeArchivedSuspendedCards(snapshot)

        assertEquals(1, summary.sourceCards)
        assertEquals(0, summary.taggedNotes)
        assertTrue(summary.message, summary.message.contains("local archive"))
    }

    /** Repaired tagging writes `kani_repaired` and reports the notes it reached. */
    @Test
    fun repairedTaggingTagsTheRequestedNotes() {
        val exchange = readyExchange { onResult("addTags", "null") }

        val summary = gateway(exchange).tagRepairedNotes(
            setOf(11L, 12L),
            CollectionProgressListener.NONE,
        )

        assertEquals(setOf(11L, 12L), summary.taggedNoteIds)
        assertEquals(setOf(11L, 12L), summary.requestedNoteIds)
        assertTrue(summary.failedNoteIds.isEmpty())
        assertTrue(summary.message, summary.message.contains("Tagged 2 repaired notes in Anki"))
        val sent = exchange.anyBodiesFor("addTags")
        assertEquals(2, sent.size)
        assertTrue(sent.all { it.contains("kani_repaired") })
    }

    /** No requested notes means no request and the shared no-op summary. */
    @Test
    fun repairedTaggingWithNoNotesSendsNothing() {
        val exchange = readyExchange { onResult("addTags", "null") }

        val summary = gateway(exchange).tagRepairedNotes(emptySet(), CollectionProgressListener.NONE)

        assertTrue(summary.requestedNoteIds.isEmpty())
        assertTrue(exchange.received.isEmpty())
    }

    /**
     * An unreachable Anki fails the repaired write without failing the caller, and
     * the copy promises the retry the caller will actually perform.
     */
    @Test
    fun anUnreachableAnkiFailsTheRepairedWriteWithoutThrowing() {
        val exchange = readyExchange()
        // The write leaves as a `multi`, so the transport loss has to be scripted
        // there; failing the nested action instead would exercise a different path.
        exchange.onRaw("multi") {
            AnkiConnectTransport.HttpExchange.Result.ConnectionFailed("refused")
        }

        val summary = gateway(exchange).tagRepairedNotes(
            setOf(11L),
            CollectionProgressListener.NONE,
        )

        assertEquals(setOf(11L), summary.failedNoteIds)
        assertTrue(summary.taggedNoteIds.isEmpty())
        assertTrue(summary.message, summary.message.contains("retry"))
    }

    /**
     * A partially-failed repaired write reports exactly which notes to retry, and
     * says so — the caller retries the failures on the next sync, and the user is
     * told a number that matches what actually happened.
     */
    @Test
    fun aPartiallyFailedRepairedWriteNamesTheNotesToRetry() {
        val exchange = readyExchange()
        exchange.onRaw("addTags") { body ->
            val failed = requestedNoteIds(body) == listOf(12L)
            AnkiConnectTransport.HttpExchange.Result.Ok(
                200,
                if (failed) {
                    """{"result":null,"error":"note was not found: 12"}"""
                } else {
                    """{"result":null,"error":null}"""
                },
            )
        }

        val summary = gateway(exchange).tagRepairedNotes(
            setOf(11L, 12L),
            CollectionProgressListener.NONE,
        )

        assertEquals(setOf(11L), summary.taggedNoteIds)
        assertEquals(setOf(12L), summary.failedNoteIds)
        assertTrue(summary.message, summary.message.contains("1 will retry next sync"))
    }

    /** Both write paths report their stage, so the sync UI can name the step. */
    @Test
    fun bothWritePathsReportTheirProgressStage() {
        val stages = mutableListOf<CollectionProgress.Stage>()
        val listener = CollectionProgressListener { stages += it.stage }
        val exchange = suspendedExchange()
        val gateway = gateway(exchange)
        val snapshot = gateway.readCollection(settings)

        gateway.removeArchivedSuspendedCards(snapshot, listener)
        gateway.tagRepairedNotes(setOf(11L), listener)

        assertTrue(stages.toString(), CollectionProgress.Stage.ARCHIVING_IMPORTED_CARDS in stages)
        assertTrue(stages.toString(), CollectionProgress.Stage.TAGGING_REPAIRED in stages)
    }

    /**
     * The archive selection honors what the user chose to import: a suspended card
     * the user did not select leaves its note ineligible, exactly as on AnkiDroid,
     * because the shared policy makes that call for both providers.
     */
    @Test
    fun archivingRespectsTheUsersSuspendedImportSelection() {
        val exchange = suspendedExchange()
        val gateway = gateway(exchange)
        val snapshot = gateway.readCollection(settings)

        val summary = gateway.removeArchivedSuspendedCards(
            snapshot,
            listOf(
                RecordsImportModels.SuspendedImport(
                    "橋",
                    1,
                    true,
                    1,
                    // A different card than the snapshot's 110: the suspended card
                    // Kani read was not among the ones the user chose to import.
                    listOf(
                        RecordsImportModels.SuspendedSource(
                            "橋",
                            999L,
                            11L,
                            "橋",
                            "はし",
                            "bridge",
                            "橋を渡る。",
                        ),
                    ),
                ),
            ),
            CollectionProgressListener.NONE,
        )

        assertEquals(0, summary.taggedNotes)
        assertTrue(exchange.received.none { it.contains("addTags") })
    }

    /** The diagnostics accessor surfaces the reader's malformed-row warning. */
    @Test
    fun readWithDiagnosticsReportsMalformedRows() {
        val clean = gateway(readyExchange()).readWithDiagnostics(settings)
        assertEquals(0, clean.skipped.total)
        assertNull(clean.malformedRowWarning)

        val noisy = readyExchange {
            onResult("findNotes", "[11,12]")
            onResult(
                "notesInfo",
                """[${
                    ScriptedAnkiConnectExchange.noteRow(
                        11L,
                        model,
                        listOf(settings.expressionField to "橋"),
                    )
                },{"noteId":null}]""",
            )
        }

        val result = gateway(noisy).readWithDiagnostics(settings)

        assertEquals(1, result.skipped.notes)
        assertNotNull(result.malformedRowWarning)
    }

    @Test
    fun cancellationBeforeTheReadFailsAsCancelled() {
        val failure = assertThrows(CollectionFailure::class.java) {
            gateway(readyExchange()).readProviderCollection(
                settings,
                CollectionProgressListener.NONE,
            ) { true }
        }

        assertEquals(CollectionFailureKind.CANCELLED, failure.kind)
        assertTrue(failure.retryable)
    }

    @Test
    fun forwardsTheApiKeyToEveryRequest() {
        val exchange = readyExchange()
        AnkiConnectGateway(exchange.transport()) { "s3cret" }
            .readProviderCollection(settings, CollectionProgressListener.NONE, CollectionCancellation.NONE)

        // requestPermission is deliberately keyless; every read carries the key.
        assertTrue(exchange.bodiesFor("notesInfo").all { it.contains("s3cret") })
        assertTrue(exchange.bodiesFor("cardsInfo").all { it.contains("s3cret") })
    }

    /** The note ids an `addTags` request body named. */
    private fun requestedNoteIds(body: String): List<Long> {
        val params = (AnkiConnectJson.decode(body) as? AnkiConnectJson.Json.Obj)
            ?.entries?.get("params") as? AnkiConnectJson.Json.Obj
            ?: return emptyList()
        val notes = params.entries["notes"] as? AnkiConnectJson.Json.Arr ?: return emptyList()
        return notes.items.mapNotNull { (it as? AnkiConnectJson.Json.Num)?.value }
    }
}
