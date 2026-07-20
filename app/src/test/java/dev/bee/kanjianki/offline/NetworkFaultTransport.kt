package dev.bee.kanjianki.offline

import dev.bee.kanjianki.update.GitHubUpdater
import dev.bee.kanjianki.updatecore.SigningCertificateInfo
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException

/**
 * Reusable, dependency-injected network-fault transport for offline-resilience
 * regression tests.
 *
 * Kani has exactly two outbound network touchpoints (see
 * `docs/offline-resilience-audit.md`):
 *
 *  1. The in-app updater (GitHub releases API + APK/checksum download), reached
 *     through [GitHubUpdater.UpdateClient]. This is the only touchpoint under
 *     Kani's own transport control and is therefore the surface this harness
 *     drives directly.
 *  2. The ML Kit handwriting model download, which is owned by Google Play
 *     Services and is exercised through the `WritingRecognizer` fakes in the
 *     study tests, not here.
 *
 * This class centralises the fault catalogue named in the audit card so later
 * cards can add a defect's fake transport by choosing a [Fault] instead of
 * hand-rolling an anonymous `UpdateClient` each time. Every fault is produced
 * locally; no test built on this harness may touch the public Internet.
 *
 * The harness is deliberately transport-only: it decides *how the wire behaves*
 * and leaves the assertions about how Kani classifies the outcome
 * (retryable vs. permanent, "already on version" vs. "offline") to the test.
 */
object NetworkFaultTransport {
    /**
     * The offline fault catalogue. Each entry maps a real-world connectivity
     * failure to the deterministic effect it has on an HTTP read. The comments
     * record the transport-layer symptom each fault reproduces.
     */
    enum class Fault {
        /** Airplane mode / no route to any host. */
        NO_ROUTE,

        /** DNS lookup fails (name cannot be resolved). */
        DNS_FAILURE,

        /** TCP connection actively refused. */
        CONNECTION_REFUSED,

        /** Black-holed connection: the socket read never returns and times out. */
        BLACK_HOLE_TIMEOUT,

        /** TLS negotiation fails (e.g. an intercepting proxy with a bad cert). */
        TLS_HANDSHAKE_FAILURE,

        /** The socket drops in the middle of a response body. */
        MID_REQUEST_DISCONNECT,

        /**
         * Captive-portal / transparent-proxy interception: the request returns
         * HTTP 200 with a login/interstitial HTML page instead of the expected
         * JSON or checksum text. This is the insidious case because the read
         * *succeeds* at the HTTP layer while carrying no usable payload.
         */
        CAPTIVE_PORTAL_HTML,
    }

    /** Canonical HTML body a captive portal / proxy returns on an HTTP 200. */
    const val CAPTIVE_PORTAL_BODY: String =
        "<!DOCTYPE html><html><head><title>Sign in to Wi-Fi</title></head>" +
            "<body><h1>Login required</h1><form action=\"/login\"></form></body></html>"

    /** Builds the exception a given transport-level [Fault] raises on read. */
    @JvmStatic
    fun exceptionFor(fault: Fault): IOException = when (fault) {
        Fault.NO_ROUTE -> NoRouteToHostException("No route to host")
        Fault.DNS_FAILURE -> UnknownHostException("api.github.com")
        Fault.CONNECTION_REFUSED -> ConnectException("Connection refused")
        Fault.BLACK_HOLE_TIMEOUT -> SocketTimeoutException("Read timed out")
        Fault.TLS_HANDSHAKE_FAILURE -> SSLHandshakeException("handshake_failure")
        Fault.MID_REQUEST_DISCONNECT -> SocketException("Connection reset")
        Fault.CAPTIVE_PORTAL_HTML ->
            throw IllegalArgumentException(
                "CAPTIVE_PORTAL_HTML is a successful HTTP read, not an exception; use updateClient()",
            )
    }

    /**
     * Produces an [GitHubUpdater.UpdateClient] whose `getText`/`download` react
     * to the chosen [fault]. All other client operations throw, so a test
     * that reaches installer/signing code under an "offline" transport fails
     * loudly instead of silently passing.
     */
    @JvmStatic
    fun updateClient(fault: Fault): GitHubUpdater.UpdateClient = object : GitHubUpdater.UpdateClient {
        override fun getText(url: String): String {
            if (fault == Fault.CAPTIVE_PORTAL_HTML) {
                // A captive portal answers *every* GET with its interstitial,
                // HTTP 200. The transport succeeds; the payload is worthless.
                return CAPTIVE_PORTAL_BODY
            }
            throw exceptionFor(fault)
        }

        override fun download(url: String, file: File) {
            if (fault == Fault.CAPTIVE_PORTAL_HTML) {
                // A captive portal would stream HTML into the APK file. Model it
                // as a successful write of the interstitial bytes so downstream
                // checksum/APK validation is what rejects it.
                file.writeText(CAPTIVE_PORTAL_BODY)
                return
            }
            throw exceptionFor(fault)
        }

        override fun inspectApk(apkFile: File): GitHubUpdater.ApkMetadata =
            error("inspectApk must not be reached under an offline transport")

        override fun installedSigningCertificates(packageName: String): SigningCertificateInfo =
            error("installedSigningCertificates must not be reached under an offline transport")

        override fun canRequestPackageInstalls(): Boolean =
            error("canRequestPackageInstalls must not be reached under an offline transport")

        override fun startPackageInstaller(
            apkFile: File,
            version: String,
            source: GitHubUpdater.UpdateSource,
            targetSdkVersion: Int,
        ) = error("startPackageInstaller must not be reached under an offline transport")

        override fun showPendingUpdate(version: String, message: String): Boolean =
            error("showPendingUpdate must not be reached under an offline transport")
    }
}
