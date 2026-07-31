package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.syncapi.CollectionAvailability
import dev.bee.kanjianki.syncapi.CollectionCancellation
import dev.bee.kanjianki.syncapi.CollectionCapability
import dev.bee.kanjianki.syncapi.CollectionFailure
import dev.bee.kanjianki.syncapi.CollectionFailureKind
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
        exchange.onResult("getActiveProfile", """"User 1"""")
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
     * `status()` advertises read capabilities only. AnkiConnect reports `addTags`
     * and `addNotes` as available, so it would be easy to advertise the write
     * capabilities off the handshake; the writes do not exist yet (Goal 190) and a
     * declared-but-absent write is worse than an undeclared one.
     */
    @Test
    fun advertisesReadCapabilitiesOnlyWhileWritesAreUnimplemented() {
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
            setOf(CollectionCapability.READ_COLLECTION, CollectionCapability.LIST_NOTE_TYPES),
            status.capabilities,
        )
        assertFalse(status.supports(CollectionCapability.NOTE_TAG_WRITE))
        assertFalse(status.supports(CollectionCapability.MISSING_KANJI_WRITE))
        assertFalse(status.supports(CollectionCapability.COLLECTION_INVENTORY))
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
        val exchange = readyExchange { onResult("getActiveProfile", "null") }

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
     * Archiving is an `addTags` write Kani cannot perform over AnkiConnect yet, so
     * the summary must report zero tagged notes and say so, never a silent success.
     */
    @Test
    fun archivingReportsThatTheWriteIsUnavailableInsteadOfClaimingSuccess() {
        val snapshot = gateway(readyExchange()).readCollection(settings)

        val summary = gateway(readyExchange()).removeArchivedSuspendedCards(snapshot)

        assertEquals(1, summary.sourceCards)
        assertEquals(0, summary.taggedNotes)
        assertEquals(0, summary.deletedNotes)
        assertTrue(summary.message.contains("cannot tag"))
    }

    /**
     * The default `tagRepairedNotes` is the interface's no-op. Pinning it here
     * means adding a real implementation has to change a test, rather than
     * silently changing what callers observe.
     */
    @Test
    fun repairedTaggingIsANoOpUntilWritesExist() {
        val summary = gateway(readyExchange()).tagRepairedNotes(
            setOf(11L),
            CollectionProgressListener.NONE,
        )

        assertTrue(summary.taggedNoteIds.isEmpty())
        assertTrue(summary.requestedNoteIds.isEmpty())
        assertTrue(summary.failedNoteIds.isEmpty())
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
}
