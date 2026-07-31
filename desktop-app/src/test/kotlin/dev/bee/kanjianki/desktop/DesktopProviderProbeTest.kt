package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.platform.SecretPersistence
import dev.bee.kanjianki.platform.SecretReference
import dev.bee.kanjianki.platform.SecretStore
import dev.bee.kanjianki.platform.SecretValue
import dev.bee.kanjianki.presentation.PlatformCapability
import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectActions
import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectHandshake
import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectKeyStore
import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectStatusMapping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopProviderProbeTest {
    @Test
    fun aReadyAnkiWithEveryOptionalActionEarnsEveryProviderCapability() {
        val status = probe(ready(AnkiConnectActions.optional)).probe()

        assertTrue(status.isReady)
        assertEquals(
            setOf(
                PlatformCapability.PROVIDER_CONNECTIVITY,
                PlatformCapability.PROVIDER_NOTE_TAG_WRITE,
                PlatformCapability.PROVIDER_BROWSER_HANDOFF,
                PlatformCapability.PROVIDER_MISSING_KANJI_WRITE,
            ),
            status.capabilities,
        )
        // Never granted, whatever the server reports: AnkiConnect does not expose
        // FSRS memory state at all, so admission has to seed from the interval.
        assertFalse(PlatformCapability.PROVIDER_FSRS_MEMORY in status.capabilities)
    }

    @Test
    fun aReadyAnkiWithNoOptionalActionsIsConnectedAndNothingMore() {
        val status = probe(ready(emptySet())).probe()

        assertTrue(status.isReady)
        assertEquals(setOf(PlatformCapability.PROVIDER_CONNECTIVITY), status.capabilities)
    }

    @Test
    fun missingKanjiWriteNeedsAllThreeOfItsActions() {
        // Two of three is not "mostly available": the flow creates a model, creates
        // a deck, and adds notes, and a partial claim would fail halfway through
        // with notes already written.
        val partial = probe(ready(setOf("createModel", "createDeck"))).probe()
        assertFalse(PlatformCapability.PROVIDER_MISSING_KANJI_WRITE in partial.capabilities)

        val complete = probe(ready(setOf("createModel", "createDeck", "addNotes"))).probe()
        assertTrue(PlatformCapability.PROVIDER_MISSING_KANJI_WRITE in complete.capabilities)
    }

    @Test
    fun everyNonReadyStatusGrantsNothingAndExplainsItself() {
        val statuses = listOf(
            AnkiConnectHandshake.Status.PermissionRequired,
            AnkiConnectHandshake.Status.NoActiveProfile,
            AnkiConnectHandshake.Status.MissingRequiredActions(setOf("findNotes")),
            AnkiConnectHandshake.Status.UnsupportedVersion(reported = 4),
            AnkiConnectHandshake.Status.Unavailable(detail = "connection refused"),
        )

        for (status in statuses) {
            val observed = probe(status).probe()
            assertFalse("$status must not be ready", observed.isReady)
            assertTrue("$status granted capabilities", observed.capabilities.isEmpty())
            // The copy is the provider module's, not this host's: one wording of
            // "start Anki" across both hosts is the point of the mapping.
            assertEquals(AnkiConnectStatusMapping.messageFor(status), observed.message)
        }
    }

    @Test
    fun theKeyIsOfferedOnlyAfterTheKeylessProbeAsksForIt() {
        val offered = ArrayList<String?>()
        val probe = DesktopProviderProbe(
            keyStore = AnkiConnectKeyStore(StubSecrets(key = "s3cret")),
            client = object : DesktopProviderProbe.Client {
                override fun handshake(apiKey: String?): AnkiConnectHandshake.Status {
                    offered += apiKey
                    return if (apiKey == null) {
                        AnkiConnectHandshake.Status.PermissionRequired
                    } else {
                        ready(emptySet())
                    }
                }

                override fun browse(query: String, apiKey: String?) = true
            },
        )

        assertTrue(probe.probe().isReady)
        // Keyless first. Sending a stored key up front would hand it to whatever
        // answered on the port even when the server never asked for it.
        assertEquals(listOf(null, "s3cret"), offered)
    }

    @Test
    fun aServerThatNeverAsksForAKeyNeverSeesOne() {
        val offered = ArrayList<String?>()
        val probe = DesktopProviderProbe(
            keyStore = AnkiConnectKeyStore(StubSecrets(key = "s3cret")),
            client = object : DesktopProviderProbe.Client {
                override fun handshake(apiKey: String?): AnkiConnectHandshake.Status {
                    offered += apiKey
                    return ready(emptySet())
                }

                override fun browse(query: String, apiKey: String?) = true
            },
        )

        probe.probe()

        assertEquals(listOf<String?>(null), offered)
    }

    @Test
    fun permissionRequiredWithNoStoredKeyStaysPermissionRequired() {
        val probe = DesktopProviderProbe(
            keyStore = AnkiConnectKeyStore(StubSecrets(key = null)),
            client = object : DesktopProviderProbe.Client {
                override fun handshake(apiKey: String?) =
                    AnkiConnectHandshake.Status.PermissionRequired

                override fun browse(query: String, apiKey: String?) = true
            },
        )

        val status = probe.probe()

        assertFalse(status.isReady)
        assertEquals(
            AnkiConnectStatusMapping.messageFor(AnkiConnectHandshake.Status.PermissionRequired),
            status.message,
        )
    }

    @Test
    fun anUnusableEndpointIsAConfigurationProblemNotAConnectionOne() {
        // A non-loopback endpoint must not be reported as "Anki is not running":
        // starting Anki would not fix it, and the message has to say so.
        val probe = DesktopProviderProbe.forLoopbackEndpoint(
            secrets = StubSecrets(key = null),
            endpointUrl = "http://example.com:8765",
        )

        val status = probe.probe()

        assertFalse(status.isReady)
        assertEquals(DesktopProviderProbe.INVALID_ENDPOINT_MESSAGE, status.message)
        assertTrue(status.capabilities.isEmpty())
        // And browse cannot be attempted at all, rather than throwing.
        assertFalse(probe.browse("tag:kani_repaired"))
    }

    @Test
    fun aThrowingBrowseIsAnAnswerRatherThanACrash() {
        // `AnkiConnectBrowseHandoff` throws when Anki is unreachable or too old for
        // `guiBrowse`; the shell's `openCollectionBrowser` contract is a boolean it
        // falls back from, so the throw has to stop here.
        val probe = DesktopProviderProbe(
            keyStore = AnkiConnectKeyStore(StubSecrets(key = null)),
            client = object : DesktopProviderProbe.Client {
                override fun handshake(apiKey: String?) = ready(emptySet())

                override fun browse(query: String, apiKey: String?): Boolean =
                    throw IllegalStateException("anki went away")
            },
        )

        assertFalse(probe.browse("deck:current"))
    }

    @Test
    fun browseCarriesTheStoredKeyWithoutAWastedKeylessAttempt() {
        val offered = ArrayList<String?>()
        val probe = DesktopProviderProbe(
            keyStore = AnkiConnectKeyStore(StubSecrets(key = "s3cret")),
            client = object : DesktopProviderProbe.Client {
                override fun handshake(apiKey: String?) = ready(emptySet())

                override fun browse(query: String, apiKey: String?): Boolean {
                    offered += apiKey
                    return true
                }
            },
        )

        assertTrue(probe.browse("tag:kani_repaired"))

        // Browse runs only after a Ready handshake, so whether authentication is
        // needed is already settled; a keyless first attempt would be a wasted round
        // trip against an authenticated server.
        assertEquals(listOf("s3cret"), offered)
    }

    @Test
    fun theHostClaimsBackupRestoreAndFollowsTheSecretStoreForPersistence() {
        assertEquals(
            setOf(PlatformCapability.BACKUP_RESTORE),
            desktopHostCapabilities(persistsSecrets = false),
        )
        assertEquals(
            setOf(PlatformCapability.BACKUP_RESTORE, PlatformCapability.SECRET_PERSISTENCE),
            desktopHostCapabilities(persistsSecrets = true),
        )
    }

    private fun probe(status: AnkiConnectHandshake.Status) = DesktopProviderProbe(
        keyStore = AnkiConnectKeyStore(StubSecrets(key = null)),
        client = object : DesktopProviderProbe.Client {
            override fun handshake(apiKey: String?) = status

            override fun browse(query: String, apiKey: String?) = true
        },
    )

    private fun ready(optional: Set<String>) = AnkiConnectHandshake.Status.Ready(
        version = 6,
        profileIdentity = "profile-fingerprint",
        availableOptionalActions = optional,
    )

    /** A session-only store holding at most one key, for the negotiation tests. */
    private class StubSecrets(private val key: String?) : SecretStore {
        override val persistence = SecretPersistence.SESSION_ONLY

        override fun read(reference: SecretReference): SecretValue? =
            key?.let { SecretValue.create(it.toCharArray()) }

        override fun write(reference: SecretReference, value: SecretValue) = false

        override fun delete(reference: SecretReference) = false
    }
}
