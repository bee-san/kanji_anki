package dev.bee.kanjianki.offline

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.BuildConfig
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.update.GitHubUpdater
import dev.bee.kanjianki.updatecore.UpdateTextPolicy
import org.junit.After
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
 * These tests pin the *offline contract* the updater must honour under each
 * transport fault in [NetworkFaultTransport.Fault]. Socket-level failures that
 * already classify correctly (no-route, DNS, connection-refused, black-hole
 * timeout, mid-request disconnect) are locked here as passing regression
 * guards. Two CONFIRMED DEFECTS fail until the child fix card (`t_22aceb86`)
 * corrects them; this audit card changes no production code:
 *
 *  - Defect A (captive portal / transparent proxy): the current code treats a
 *    proxy interstitial (HTTP 200 + HTML) as a clean "you are up to date"
 *    result, hiding the outage and clearing the retry affordance.
 *  - Defect B (TLS handshake failure): `SSLHandshakeException` is not in
 *    `GitHubUpdater.retryableFailure`'s allowlist, so a captive-portal /
 *    intercepting-proxy TLS failure is classified as a permanent (non-retryable)
 *    failure. `recordResult` then clears the update-check-failed flag and Home
 *    offers no retry.
 *
 * Audit artifact: `docs/offline-resilience-audit.md`.
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

    private fun assertRetryableOfflineFailure(fault: NetworkFaultTransport.Fault) {
        val updater = GitHubUpdater(context, NetworkFaultTransport.updateClient(fault))

        val result = updater.checkDownloadAndInstall(GitHubUpdater.UpdateSource.AUTOMATIC)

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
