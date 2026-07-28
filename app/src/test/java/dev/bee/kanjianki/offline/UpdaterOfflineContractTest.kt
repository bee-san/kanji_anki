package dev.bee.kanjianki.offline

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.BuildConfig
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.update.GitHubUpdater
import dev.bee.kanjianki.updatecore.UpdateTextPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Offline-resilience regression matrix for the in-app updater — the one
 * outbound network touchpoint Kani controls end to end.
 *
 * These tests pin the *offline contract* the updater must honour under every
 * transport fault in [NetworkFaultTransport.Fault], plus the persistence
 * invariants that outlive a single check (online↔offline flapping, and pending
 * update state surviving a process restart). Every case is fully hermetic: no
 * sockets, no Internet, deterministic under Robolectric, safe for `ciFast`.
 *
 * History: the two originally-CONFIRMED defects — captive-portal HTML
 * misreported as "already up to date", and TLS handshake failures classified
 * as permanent — were fixed by child card `t_22aceb86` (commits 58c7fac7 /
 * 387a3dd5) and are now locked here as passing regression guards alongside the
 * socket-level faults. Audit artifact: `docs/offline-resilience-audit.md`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdaterOfflineContractTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        context.deleteDatabase("kanji_anki_simple.db")
    }

    @After
    fun tearDown() {
        context.deleteDatabase("kanji_anki_simple.db")
    }

    // ---------------------------------------------------------------------
    // Passing regression guards: genuine socket-level faults are already
    // classified as retryable connectivity failures, never as "up to date".
    // ---------------------------------------------------------------------

    @Test
    fun noRouteIsRetryableConnectivityFailure() = assertRetryableOfflineFailure(NetworkFaultTransport.Fault.NO_ROUTE)

    @Test
    fun automaticConnectivityFailureDoesNotCreateAHomeScreenNag() {
        val result = GitHubUpdater(context, NetworkFaultTransport.updateClient(NetworkFaultTransport.Fault.NO_ROUTE))
            .checkDownloadAndInstall(GitHubUpdater.UpdateSource.AUTOMATIC)

        assertTrue("Background failure must remain retryable for WorkManager", result.retryable)
        val failedAt = LocalStore(context).use { it.updateCheckFailedAt() }
        assertEquals(
            "Background update checks must fail silently in Kani's offline-first home experience",
            0L,
            failedAt,
        )
    }

    @Test
    fun automaticConnectivityFailurePreservesAnExistingManualRetryAffordance() {
        val manualFailedAt = 1_700_000_000_000L
        LocalStore(context).use { it.recordUpdateCheckFailed(manualFailedAt) }

        GitHubUpdater(context, NetworkFaultTransport.updateClient(NetworkFaultTransport.Fault.NO_ROUTE))
            .checkDownloadAndInstall(GitHubUpdater.UpdateSource.AUTOMATIC)

        val failedAt = LocalStore(context).use { it.updateCheckFailedAt() }
        assertEquals(manualFailedAt, failedAt)
    }

    @Test
    fun dnsFailureIsRetryableConnectivityFailure() =
        assertRetryableOfflineFailure(NetworkFaultTransport.Fault.DNS_FAILURE)

    @Test
    fun connectionRefusedIsRetryableConnectivityFailure() =
        assertRetryableOfflineFailure(NetworkFaultTransport.Fault.CONNECTION_REFUSED)

    @Test
    fun blackHoleTimeoutIsRetryableConnectivityFailure() =
        assertRetryableOfflineFailure(NetworkFaultTransport.Fault.BLACK_HOLE_TIMEOUT)

    @Test
    fun midRequestDisconnectIsRetryableConnectivityFailure() =
        assertRetryableOfflineFailure(NetworkFaultTransport.Fault.MID_REQUEST_DISCONNECT)

    // ---------------------------------------------------------------------
    // CONFIRMED DEFECT B — TLS handshake failure classified as permanent.
    //
    // A captive portal / intercepting proxy with a bad or unexpected
    // certificate makes the TLS negotiation fail with SSLHandshakeException.
    // GitHubUpdater.retryableFailure only walks the cause chain for
    // SocketTimeoutException / ConnectException / UnknownHostException /
    // NoRouteToHostException / SocketException, so SSLHandshakeException falls
    // through to `false`. checkDownloadAndInstall then records a NON-retryable
    // failure, recordResult clears the update-check-failed flag, and Home
    // offers no retry — the outage is silently swallowed.
    //
    // Correct contract: a TLS handshake failure during an update check is a
    // connectivity/interception failure and must be retryable so the flag
    // stays lit. Test fails until the child fix card (`t_22aceb86`) adds the
    // SSL classification.
    // ---------------------------------------------------------------------

    @Test
    fun tlsHandshakeFailureIsRetryableConnectivityFailure() =
        assertRetryableOfflineFailure(NetworkFaultTransport.Fault.TLS_HANDSHAKE_FAILURE)

    // ---------------------------------------------------------------------
    // CONFIRMED DEFECT A — captive portal / transparent proxy.
    //
    // A captive portal answers the releases API with HTTP 200 + an HTML login
    // page. The tolerant JSON parser yields an empty tag_name, ReleaseVersion
    // treats "" as 0.0.0, and checkDownloadAndInstall returns
    // alreadyOnVersionMessage(...) with retryable = false. recordResult then
    // CLEARS the update-check-failed flag, so Home shows no retry affordance and
    // the user believes they are current while actually offline behind a portal.
    //
    // Correct contract: an empty/garbage successful response under an active
    // connectivity intercept must NOT be reported as "already on version" and
    // must remain a retryable connectivity failure that lights the retry flag.
    // ---------------------------------------------------------------------

    @Test
    fun captivePortalHtmlMustNotBeReportedAsAlreadyOnVersion() {
        val updater = GitHubUpdater(
            context,
            NetworkFaultTransport.updateClient(NetworkFaultTransport.Fault.CAPTIVE_PORTAL_HTML),
        )

        val result = updater.checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL)

        // A connectivity intercept must never masquerade as a clean up-to-date.
        assertNotEquals(
            "Captive-portal HTML was misreported as 'already on the latest version'",
            UpdateTextPolicy.alreadyOnVersionMessage(BuildConfig.VERSION_NAME),
            result.message,
        )
    }

    @Test
    fun captivePortalHtmlIsRetryableConnectivityFailure() {
        val updater = GitHubUpdater(
            context,
            NetworkFaultTransport.updateClient(NetworkFaultTransport.Fault.CAPTIVE_PORTAL_HTML),
        )

        val result = updater.checkDownloadAndInstall(GitHubUpdater.UpdateSource.AUTOMATIC)

        assertTrue(
            "Captive-portal interception must be retryable, not a permanent no-update result",
            result.retryable,
        )
    }

    @Test
    fun captivePortalHtmlLightsTheUpdateCheckFailedFlag() {
        val updater = GitHubUpdater(
            context,
            NetworkFaultTransport.updateClient(NetworkFaultTransport.Fault.CAPTIVE_PORTAL_HTML),
        )

        updater.checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL)

        val failedAt = LocalStore(context).use { store -> store.updateCheckFailedAt() }
        assertTrue(
            "Captive-portal interception must record an update-check failure so Home offers a retry",
            failedAt > 0L,
        )
    }

    // ---------------------------------------------------------------------
    // Slow / high-latency response (packet loss, slow-loris). At the socket
    // seam this is a read timeout, which must classify exactly like a black
    // hole: a retryable connectivity failure, never "already up to date".
    // ---------------------------------------------------------------------

    @Test
    fun slowResponseTimeoutIsRetryableConnectivityFailure() =
        assertRetryableOfflineFailure(NetworkFaultTransport.Fault.SLOW_RESPONSE_TIMEOUT)

    // ---------------------------------------------------------------------
    // Malformed / truncated API payload. A partial JSON body carries no usable
    // tag_name; like the captive portal, the HTTP read succeeds but the payload
    // is worthless, so it must be a retryable connectivity failure and must NOT
    // collapse the empty tag to 0.0.0 and report "already on version".
    // ---------------------------------------------------------------------

    @Test
    fun truncatedJsonMustNotBeReportedAsAlreadyOnVersion() {
        val updater = GitHubUpdater(
            context,
            NetworkFaultTransport.updateClient(NetworkFaultTransport.Fault.TRUNCATED_JSON),
        )

        val result = updater.checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL)

        assertNotEquals(
            "Truncated release JSON was misreported as 'already on the latest version'",
            UpdateTextPolicy.alreadyOnVersionMessage(BuildConfig.VERSION_NAME),
            result.message,
        )
    }

    @Test
    fun truncatedJsonIsRetryableConnectivityFailure() =
        assertRetryableOfflineFailure(NetworkFaultTransport.Fault.TRUNCATED_JSON)

    // ---------------------------------------------------------------------
    // Request cancellation (thread interrupt / WorkManager stop / process
    // backgrounded mid-read). Surfaces as InterruptedIOException. This is NOT a
    // broken network: a user/OS cancel must not light a persistent
    // "check failed, tap to retry" affordance, so it is deliberately classified
    // as a NON-retryable failure. It must still not masquerade as "up to date".
    //
    // This guards against a naive "make everything retryable" regression that
    // would add InterruptedIOException to retryableFailure's allowlist and start
    // nagging users with a retry banner every time they navigate away.
    // ---------------------------------------------------------------------

    @Test
    fun cancelledReadIsNotRetryableAndDoesNotLightRetryFlag() {
        val updater = GitHubUpdater(
            context,
            NetworkFaultTransport.updateClient(NetworkFaultTransport.Fault.CANCELLED_READ),
        )

        val result = updater.checkDownloadAndInstall(GitHubUpdater.UpdateSource.AUTOMATIC)

        assertFalse(
            "A cancelled/interrupted read must not be a retryable connectivity failure",
            result.retryable,
        )
        assertNotEquals(
            "A cancelled read must not be misreported as 'already on the latest version'",
            UpdateTextPolicy.alreadyOnVersionMessage(BuildConfig.VERSION_NAME),
            result.message,
        )
        val failedAt = LocalStore(context).use { store -> store.updateCheckFailedAt() }
        assertEquals(
            "A cancelled read is not a connectivity outage; it must not light the retry flag",
            0L,
            failedAt,
        )
    }

    // ---------------------------------------------------------------------
    // Online ↔ offline flapping. As connectivity comes and goes across
    // successive checks the update-check-failed flag must track reality
    // exactly: lit while offline, cleared on a healthy check, re-lit when it
    // drops again. A stale flag would leave a permanent retry banner (false
    // alarm) or hide a live outage (missed update).
    // ---------------------------------------------------------------------

    @Test
    fun flappingConnectivityKeepsRetryFlagInSyncWithReality() {
        val updater = GitHubUpdater(
            context,
            NetworkFaultTransport.flappingClient(
                listOf(
                    NetworkFaultTransport.Fault.DNS_FAILURE, // offline
                    null, // back online, up to date
                    NetworkFaultTransport.Fault.CONNECTION_REFUSED, // offline again
                ),
            ),
        )

        updater.checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL)
        val afterFirstOutage = LocalStore(context).use { it.updateCheckFailedAt() }
        assertTrue("First manual outage must light the retry flag", afterFirstOutage > 0L)

        updater.checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL)
        val afterRecovery = LocalStore(context).use { it.updateCheckFailedAt() }
        assertEquals("A healthy check must clear the retry flag", 0L, afterRecovery)

        updater.checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL)
        val afterSecondOutage = LocalStore(context).use { it.updateCheckFailedAt() }
        assertTrue("A fresh manual outage must re-light the retry flag", afterSecondOutage > 0L)
    }

    // ---------------------------------------------------------------------
    // Process restart with pending work. The update-check-failed flag is
    // persisted through LocalStore, so a fault-induced flag set by one process
    // must still be observable after that store is closed and a brand-new
    // LocalStore is opened — the app was killed and relaunched while offline.
    // ---------------------------------------------------------------------

    @Test
    fun retryFlagSurvivesProcessRestart() {
        // First "process": go offline, which lights the persisted flag.
        GitHubUpdater(context, NetworkFaultTransport.updateClient(NetworkFaultTransport.Fault.NO_ROUTE))
            .checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL)
        val firstProcessFlag = LocalStore(context).use { it.updateCheckFailedAt() }
        assertTrue("Manual offline check must light the retry flag", firstProcessFlag > 0L)

        // Second "process": a fresh LocalStore, as after an app kill/relaunch.
        // The persisted flag must still be there so Home shows the retry banner.
        val secondProcessFlag = LocalStore(context).use { it.updateCheckFailedAt() }
        assertEquals(
            "The persisted retry flag must survive a process restart",
            firstProcessFlag,
            secondProcessFlag,
        )
    }

    private fun assertRetryableOfflineFailure(fault: NetworkFaultTransport.Fault) {
        val updater = GitHubUpdater(context, NetworkFaultTransport.updateClient(fault))

        val result = updater.checkDownloadAndInstall(GitHubUpdater.UpdateSource.MANUAL)

        assertTrue("$fault should be a retryable connectivity failure", result.retryable)
        assertNotEquals(
            "$fault must not be reported as 'already on the latest version'",
            UpdateTextPolicy.alreadyOnVersionMessage(BuildConfig.VERSION_NAME),
            result.message,
        )
        val failedAt = LocalStore(context).use { store -> store.updateCheckFailedAt() }
        assertTrue("$fault should light the update-check-failed flag", failedAt > 0L)
    }
}
