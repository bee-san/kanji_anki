package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.syncapi.CollectionAvailability
import dev.bee.kanjianki.syncapi.CollectionFailure
import dev.bee.kanjianki.syncapi.CollectionFailureKind

/**
 * The one place AnkiConnect outcomes become shared-contract outcomes.
 *
 * Two gateways ([AnkiConnectGateway] and [AnkiConnectInventoryGateway]) and the
 * collection reader all have to answer the same question — "what should the
 * caller do about this?" — and they must answer it identically, because the
 * caller's retry policy keys off the answer. A second hand-written copy of this
 * mapping would drift: it is exactly the kind of table where one site classifies
 * a stale API key as retryable and another does not, and nothing fails until a
 * user is stuck in a retry loop on a problem retrying cannot fix.
 *
 * The classification rule throughout is *actionability*, not severity. A
 * reachable Anki with the wrong wire version, no open collection, or a missing
 * required action is `INVALID_CONFIGURATION` and not retryable, because the user
 * has to change something. Only genuine reachability and rate/size problems are
 * retryable.
 */
object AnkiConnectStatusMapping {
    /** The availability a non-ready handshake outcome maps to. */
    fun availabilityFor(status: AnkiConnectHandshake.Status): CollectionAvailability =
        when (status) {
            is AnkiConnectHandshake.Status.Ready -> CollectionAvailability.READY
            AnkiConnectHandshake.Status.PermissionRequired -> CollectionAvailability.AUTH_REQUIRED
            AnkiConnectHandshake.Status.NoActiveProfile,
            is AnkiConnectHandshake.Status.UnsupportedVersion,
            is AnkiConnectHandshake.Status.MissingRequiredActions,
            -> CollectionAvailability.INVALID_CONFIGURATION
            is AnkiConnectHandshake.Status.Unavailable -> CollectionAvailability.NOT_AVAILABLE
        }

    /** User-facing copy for a handshake outcome. Names what to fix, where. */
    fun messageFor(status: AnkiConnectHandshake.Status): String = when (status) {
        is AnkiConnectHandshake.Status.Ready ->
            "Connected to Anki (API v${status.version})."
        AnkiConnectHandshake.Status.PermissionRequired ->
            "Anki has not granted Kani access yet. Accept the AnkiConnect prompt in Anki."
        AnkiConnectHandshake.Status.NoActiveProfile ->
            "Anki is running but no collection is open."
        is AnkiConnectHandshake.Status.UnsupportedVersion ->
            "Anki reported AnkiConnect API v${status.reported}; Kani needs " +
                "v${AnkiConnectEnvelope.API_VERSION}."
        is AnkiConnectHandshake.Status.MissingRequiredActions ->
            "This AnkiConnect is missing actions Kani needs: " +
                status.actions.sorted().joinToString(", ")
        is AnkiConnectHandshake.Status.Unavailable ->
            "Anki is not reachable (${status.detail})."
    }

    /** A response Kani could not parse as the action's documented shape. */
    fun protocolFailure(action: String): CollectionFailure = CollectionFailure(
        CollectionFailureKind.TRANSIENT,
        "AnkiConnect returned an unexpected $action response.",
    )

    /**
     * An AnkiConnect-level `error` string. AnkiConnect reports a missing or
     * incorrect API key as a plain error message rather than a status code or a
     * typed field, so the text is the only available signal — and getting this
     * wrong is what turns an auth problem into an infinite retry.
     */
    fun failureFor(message: String): CollectionFailure {
        val lowered = message.lowercase()
        val kind = if (lowered.contains("api key") || lowered.contains("authentication")) {
            CollectionFailureKind.AUTH_REQUIRED
        } else {
            CollectionFailureKind.TRANSIENT
        }
        return CollectionFailure(kind, "AnkiConnect rejected the request: $message")
    }

    /** A transport-level failure, below the AnkiConnect protocol. */
    fun transportFailure(
        failure: AnkiConnectTransport.Exchange.Failure,
    ): CollectionFailure = when (failure.reason) {
        AnkiConnectTransport.Reason.CANCELLED -> CollectionFailure.cancelled()
        // A non-loopback resolution is a configuration error, never retried:
        // Kani only ever talks to a local Anki.
        AnkiConnectTransport.Reason.NON_LOOPBACK_RESOLUTION ->
            CollectionFailure(
                CollectionFailureKind.INVALID_CONFIGURATION,
                "AnkiConnect endpoint did not resolve to loopback.",
            )
        AnkiConnectTransport.Reason.TIMEOUT,
        AnkiConnectTransport.Reason.CONNECTION_FAILED,
        ->
            CollectionFailure(
                CollectionFailureKind.NOT_AVAILABLE,
                "Anki is not reachable: ${failure.detail}",
            )
        AnkiConnectTransport.Reason.HTTP_ERROR_STATUS,
        AnkiConnectTransport.Reason.RESPONSE_TOO_LARGE,
        ->
            CollectionFailure(
                CollectionFailureKind.TRANSIENT,
                "AnkiConnect request failed: ${failure.detail}",
            )
    }
}
