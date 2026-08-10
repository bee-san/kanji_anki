package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.syncapi.CollectionAvailability
import dev.bee.kanjianki.syncapi.CollectionFailureKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mapping table is duplicated nowhere, so this is the only place it is
 * asserted. Every case pins *actionability*: whether the caller should retry, and
 * whether the user has something to fix.
 */
class AnkiConnectStatusMappingTest {
    private val ready = AnkiConnectHandshake.Status.Ready(
        version = 6L,
        profileIdentity = "User 1",
        availableOptionalActions = setOf("guiBrowse"),
    )

    @Test
    fun everyHandshakeOutcomeMapsToAnAvailability() {
        assertEquals(
            CollectionAvailability.READY,
            AnkiConnectStatusMapping.availabilityFor(ready),
        )
        assertEquals(
            CollectionAvailability.AUTH_REQUIRED,
            AnkiConnectStatusMapping.availabilityFor(
                AnkiConnectHandshake.Status.PermissionRequired,
            ),
        )
        assertEquals(
            CollectionAvailability.INVALID_CONFIGURATION,
            AnkiConnectStatusMapping.availabilityFor(
                AnkiConnectHandshake.Status.NoActiveProfile,
            ),
        )
        assertEquals(
            CollectionAvailability.INVALID_CONFIGURATION,
            AnkiConnectStatusMapping.availabilityFor(
                AnkiConnectHandshake.Status.UnsupportedVersion(5L),
            ),
        )
        assertEquals(
            CollectionAvailability.INVALID_CONFIGURATION,
            AnkiConnectStatusMapping.availabilityFor(
                AnkiConnectHandshake.Status.MissingRequiredActions(setOf("notesInfo")),
            ),
        )
        assertEquals(
            CollectionAvailability.NOT_AVAILABLE,
            AnkiConnectStatusMapping.availabilityFor(
                AnkiConnectHandshake.Status.Unavailable("connection refused"),
            ),
        )
    }

    /**
     * A reachable-but-misconfigured Anki must not be retryable: retrying cannot
     * open a collection or upgrade AnkiConnect, and a retry loop hides the fact
     * that the user has something to do.
     */
    @Test
    fun aReachableButMisconfiguredAnkiIsNotRetryable() {
        for (
        status in listOf(
            AnkiConnectHandshake.Status.NoActiveProfile,
            AnkiConnectHandshake.Status.UnsupportedVersion(5L),
            AnkiConnectHandshake.Status.MissingRequiredActions(setOf("notesInfo")),
            AnkiConnectHandshake.Status.PermissionRequired,
        )
        ) {
            val availability = AnkiConnectStatusMapping.availabilityFor(status)
            assertFalse("$status", availability == CollectionAvailability.NOT_AVAILABLE)
            assertFalse("$status", availability == CollectionAvailability.READY)
        }
    }

    /** Each message names what to fix and where to fix it. */
    @Test
    fun messagesNameWhatToFix() {
        assertTrue(AnkiConnectStatusMapping.messageFor(ready).contains("API v6"))
        assertTrue(
            AnkiConnectStatusMapping
                .messageFor(AnkiConnectHandshake.Status.PermissionRequired)
                .contains("Accept the AnkiConnect prompt in Anki"),
        )
        assertTrue(
            AnkiConnectStatusMapping
                .messageFor(AnkiConnectHandshake.Status.NoActiveProfile)
                .contains("no collection is open"),
        )
        val version = AnkiConnectStatusMapping
            .messageFor(AnkiConnectHandshake.Status.UnsupportedVersion(4L))
        assertTrue(version.contains("v4"))
        assertTrue(version.contains("v${AnkiConnectEnvelope.API_VERSION}"))
        // The user-facing copy must NOT carry the internal detail. This previously
        // asserted the opposite, which pinned a real defect as correct: the details are
        // diagnostics like "permission probe failed", and that string reached the
        // desktop onboarding panel where it sent the user looking for a permission
        // setting desktop does not have. Found by screenshotting the running app.
        val unreachable = AnkiConnectStatusMapping
            .messageFor(AnkiConnectHandshake.Status.Unavailable("permission probe failed"))
        assertFalse(unreachable.contains("permission probe failed"))
        assertFalse(unreachable.contains("probe"))
        // It still has to say what to do about it.
        assertTrue(unreachable.contains("Start Anki"))
        assertTrue(unreachable.contains("AnkiConnect"))
    }

    /** Missing actions are listed, sorted, so the copy is stable to read. */
    @Test
    fun missingActionsAreListedInAStableOrder() {
        val message = AnkiConnectStatusMapping.messageFor(
            AnkiConnectHandshake.Status.MissingRequiredActions(
                linkedSetOf("notesInfo", "findNotes", "apiReflect"),
            ),
        )

        assertTrue(message.endsWith("apiReflect, findNotes, notesInfo"))
    }

    @Test
    fun anUnparseableResponseIsTransient() {
        val failure = AnkiConnectStatusMapping.protocolFailure("notesInfo")

        assertEquals(CollectionFailureKind.TRANSIENT, failure.kind)
        assertTrue(failure.retryable)
        assertTrue(failure.message!!.contains("notesInfo"))
    }

    /**
     * AnkiConnect reports a bad or missing API key as a plain error string, so the
     * text is the only signal available — and classifying it as transient is what
     * would turn an auth problem into an endless retry.
     */
    @Test
    fun anApiKeyErrorIsAuthRequiredAndNotRetryable() {
        for (
        message in listOf(
            "valid api key must be provided",
            "Invalid API Key",
            "authentication failed",
        )
        ) {
            val failure = AnkiConnectStatusMapping.failureFor(message)
            assertEquals(message, CollectionFailureKind.AUTH_REQUIRED, failure.kind)
            assertFalse(message, failure.retryable)
        }
    }

    @Test
    fun anyOtherAnkiConnectErrorIsTransientAndCarriesTheOriginalText() {
        val failure = AnkiConnectStatusMapping.failureFor("collection is not available")

        assertEquals(CollectionFailureKind.TRANSIENT, failure.kind)
        assertTrue(failure.message!!.contains("collection is not available"))
    }

    @Test
    fun everyTransportReasonMapsToAFailureKind() {
        assertEquals(
            CollectionFailureKind.CANCELLED,
            transportFailure(AnkiConnectTransport.Reason.CANCELLED).kind,
        )
        assertEquals(
            CollectionFailureKind.NOT_AVAILABLE,
            transportFailure(AnkiConnectTransport.Reason.TIMEOUT).kind,
        )
        assertEquals(
            CollectionFailureKind.NOT_AVAILABLE,
            transportFailure(AnkiConnectTransport.Reason.CONNECTION_FAILED).kind,
        )
        assertEquals(
            CollectionFailureKind.TRANSIENT,
            transportFailure(AnkiConnectTransport.Reason.HTTP_ERROR_STATUS).kind,
        )
        assertEquals(
            CollectionFailureKind.TRANSIENT,
            transportFailure(AnkiConnectTransport.Reason.RESPONSE_TOO_LARGE).kind,
        )
    }

    /**
     * Kani only ever talks to a local Anki, so a name that resolves off-loopback is
     * a configuration error and must never be retried — retrying it would keep
     * sending collection queries at whatever host answered.
     */
    @Test
    fun aNonLoopbackResolutionIsInvalidConfigurationAndNotRetryable() {
        val failure = transportFailure(AnkiConnectTransport.Reason.NON_LOOPBACK_RESOLUTION)

        assertEquals(CollectionFailureKind.INVALID_CONFIGURATION, failure.kind)
        assertFalse(failure.retryable)
    }

    private fun transportFailure(reason: AnkiConnectTransport.Reason) =
        AnkiConnectStatusMapping.transportFailure(
            AnkiConnectTransport.Exchange.Failure(reason, "detail"),
        )
}
