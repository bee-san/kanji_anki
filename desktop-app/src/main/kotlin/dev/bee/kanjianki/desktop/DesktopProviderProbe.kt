package dev.bee.kanjianki.desktop

import dev.bee.kanjianki.platform.SecretStore
import dev.bee.kanjianki.presentation.PlatformCapability
import dev.bee.kanjianki.presentation.ProviderReadiness
import dev.bee.kanjianki.syncapi.CollectionAvailability
import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectBrowseHandoff
import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectEndpoint
import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectHandshake
import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectKeyStore
import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectStatusMapping
import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectTransport
import dev.bee.kanjianki.provider.ankiconnect.JdkHttpExchange

/** What the desktop host knows about its collection provider right now. */
internal data class DesktopProviderStatus(
    /** User-facing copy naming what to fix, from [AnkiConnectStatusMapping]. */
    val message: String,
    /**
     * Which of the four provider states this is, from [AnkiConnectStatusMapping].
     *
     * Carried rather than reduced to [isReady] because onboarding needs the
     * distinction a boolean destroys: a `PermissionRequired` handshake means "grant
     * Kani access" and an `Unavailable` one means "start Anki", and both are
     * `isReady == false`. [ProviderReadiness] is the presentation-side shape of the
     * same three-way question, and [readiness] is where the two meet.
     */
    val availability: CollectionAvailability,
    /** The provider-derived capabilities this connection actually supports. */
    val capabilities: Set<PlatformCapability>,
) {
    /** True only for a completed handshake against a reachable, authorized profile. */
    val isReady: Boolean
        get() = availability == CollectionAvailability.READY

    /**
     * The onboarding step's view of [availability].
     *
     * [CollectionAvailability.INVALID_CONFIGURATION] folds into
     * [ProviderReadiness.ABSENT] rather than into [ProviderReadiness.UNAUTHORIZED]:
     * an AnkiConnect too old for Kani, or a profile that is not open, is not
     * something granting access fixes, and the onboarding copy for `ABSENT` is the
     * host-supplied [message] which already says which of the two it is.
     */
    val readiness: ProviderReadiness
        get() = when (availability) {
            CollectionAvailability.READY -> ProviderReadiness.READY
            CollectionAvailability.AUTH_REQUIRED -> ProviderReadiness.UNAUTHORIZED
            CollectionAvailability.NOT_AVAILABLE,
            CollectionAvailability.INVALID_CONFIGURATION,
            -> ProviderReadiness.ABSENT
        }
}

/**
 * The desktop host's view of Anki: one handshake, and the browser handoff that
 * uses the same connection.
 *
 * Both live here because both need the same endpoint, transport, and API key, and
 * because the browse capability is only claimed when the handshake reported
 * `guiBrowse`. Splitting them would mean two places deciding whether the key is
 * needed, which is one more than can be kept correct.
 *
 * Capabilities are derived from what the server *reported*, never assumed. An
 * AnkiConnect that does not list `guiBrowse` gets no browser handoff and one that
 * does not list `addTags` gets no note-tag write, because the alternative is a
 * Settings toggle that fails at the tap — the exact failure [PlatformCapability]
 * exists to prevent. `PROVIDER_FSRS_MEMORY` is never granted: AnkiConnect does
 * not expose FSRS memory state at all.
 */
internal class DesktopProviderProbe(
    private val keyStore: AnkiConnectKeyStore,
    /**
     * `null` when the configured endpoint is not a usable loopback URL, which is a
     * configuration problem rather than a connection one and must not be reported
     * as "Anki is not running".
     */
    private val client: Client?,
) {
    /**
     * The two calls the host makes over one AnkiConnect connection.
     *
     * An interface rather than the concrete transport so a test can drive every
     * handshake outcome, and the refusal path, without a socket.
     */
    internal interface Client {
        fun handshake(apiKey: String?): AnkiConnectHandshake.Status

        fun browse(query: String, apiKey: String?): Boolean
    }

    fun probe(): DesktopProviderStatus {
        val connection = client ?: return DesktopProviderStatus(
            message = INVALID_ENDPOINT_MESSAGE,
            availability = CollectionAvailability.INVALID_CONFIGURATION,
            capabilities = emptySet(),
        )
        // The keyless probe first, then one retry with the stored key. This order is
        // the handshake's own contract: `requestPermission` is probed without a key,
        // and a key is only meaningful once that probe says one is needed. Sending a
        // stored key up front would hand it to whatever answered on the port even
        // when the server never asked for authentication.
        val keyless = connection.handshake(null)
        val status = if (keyless == AnkiConnectHandshake.Status.PermissionRequired) {
            keyStore.withKey { key -> if (key == null) keyless else connection.handshake(key) }
        } else {
            keyless
        }
        return DesktopProviderStatus(
            message = AnkiConnectStatusMapping.messageFor(status),
            availability = AnkiConnectStatusMapping.availabilityFor(status),
            capabilities = capabilitiesFor(status),
        )
    }

    /**
     * Shows [query] in Anki's own browser, returning whether Anki accepted it.
     *
     * Failures are answers, not exceptions: [AnkiConnectBrowseHandoff] throws when
     * Anki is unreachable or too old for `guiBrowse`, and the shell's contract for
     * `openCollectionBrowser` is a boolean it falls back from. Turning the throw
     * into `false` here is what lets the caller show the query for the user to
     * copy instead of surfacing a stack trace.
     */
    fun browse(query: String): Boolean {
        val connection = client ?: return false
        // The key is offered up front here, unlike the handshake. Browse runs only
        // after a Ready handshake, so whether authentication is required is already
        // known; a keyless first attempt would just be a wasted round trip against
        // an authenticated server. A store with no key yields null and the
        // unauthenticated request, which is the correct call for the common case.
        return runCatching {
            keyStore.withKey { key -> connection.browse(query, key) }
        }.getOrDefault(false)
    }

    private fun capabilitiesFor(
        status: AnkiConnectHandshake.Status,
    ): Set<PlatformCapability> {
        if (status !is AnkiConnectHandshake.Status.Ready) return emptySet()
        val optional = status.availableOptionalActions
        return buildSet {
            add(PlatformCapability.PROVIDER_CONNECTIVITY)
            if ("addTags" in optional) add(PlatformCapability.PROVIDER_NOTE_TAG_WRITE)
            if ("guiBrowse" in optional) add(PlatformCapability.PROVIDER_BROWSER_HANDOFF)
            // The additive Missing Kanji flow needs all three: a model to write
            // into, a deck to put the notes in, and the note write itself. With any
            // one missing the flow stays complete through CSV export, which is why
            // this is a capability and not an error.
            if (MISSING_KANJI_ACTIONS.all { it in optional }) {
                add(PlatformCapability.PROVIDER_MISSING_KANJI_WRITE)
            }
        }
    }

    companion object {
        internal const val INVALID_ENDPOINT_MESSAGE =
            "Kani's AnkiConnect address is not a valid loopback address."

        private val MISSING_KANJI_ACTIONS = setOf("createModel", "createDeck", "addNotes")

        /**
         * A probe against the loopback AnkiConnect endpoint at [endpointUrl].
         *
         * The API key is read through [AnkiConnectKeyStore] over the host's
         * [SecretStore], so a session-only store simply has no key to offer and the
         * user is asked again next launch. Nothing here can write the key to disk in
         * the clear.
         */
        fun forLoopbackEndpoint(
            secrets: SecretStore,
            endpointUrl: String = AnkiConnectEndpoint.DEFAULT_URL,
            exchangeFactory: () -> AnkiConnectTransport.HttpExchange = { JdkHttpExchange() },
        ): DesktopProviderProbe = DesktopProviderProbe(
            keyStore = AnkiConnectKeyStore(secrets),
            client = (AnkiConnectEndpoint.parse(endpointUrl) as? AnkiConnectEndpoint.Result.Valid)
                ?.let { valid -> transportClient(AnkiConnectTransport(valid.endpoint, exchangeFactory())) },
        )

        private fun transportClient(transport: AnkiConnectTransport): Client =
            object : Client {
                private val handshake = AnkiConnectHandshake(transport)

                override fun handshake(apiKey: String?) = handshake.run(apiKey)

                override fun browse(query: String, apiKey: String?): Boolean =
                    AnkiConnectBrowseHandoff(transport) { apiKey }.browse(query)
            }
    }
}

/**
 * The capabilities the desktop host itself has, independent of any provider.
 *
 * Deliberately conservative, and each absence is a fact about what is wired today
 * rather than a permanent property of the platform:
 *
 *  - `SECRET_PERSISTENCE` follows the real [SecretStore], so it is absent until a
 *    qualified OS vault adapter exists. Claiming it would offer "remember this
 *    key" as a promise the host cannot keep.
 *  - `BACKUP_RESTORE` is present: desktop has live `VACUUM INTO` snapshots and the
 *    staged whole-file restore the startup gate applies.
 *  - `TRAY_PRESENCE`, `NOTIFICATIONS`, `CLOSED_APP_SCHEDULING`,
 *    `WRITING_RECOGNITION`, and `UPDATE_DELIVERY` are absent because nothing
 *    implements them yet. They arrive with their own goals; until then the shell
 *    renders visibly reduced, which is the honest state.
 */
internal fun desktopHostCapabilities(persistsSecrets: Boolean): Set<PlatformCapability> =
    buildSet {
        add(PlatformCapability.BACKUP_RESTORE)
        if (persistsSecrets) add(PlatformCapability.SECRET_PERSISTENCE)
    }
