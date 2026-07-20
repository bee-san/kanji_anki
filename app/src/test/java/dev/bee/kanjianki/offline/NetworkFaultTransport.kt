package dev.bee.kanjianki.offline

import dev.bee.kanjianki.update.GitHubUpdater
import dev.bee.kanjianki.updatecore.SigningCertificateInfo
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.atomic.AtomicInteger
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
         * A high-latency / slow-loris style response: the far end trickles or
         * stalls and the socket read eventually gives up. At the transport seam
         * this is indistinguishable from a black hole (both surface as a read
         * timeout), but it is catalogued separately so a slow-network
         * regression names its own scenario in logs and CI artifacts.
         */
        SLOW_RESPONSE_TIMEOUT,

        /**
         * The in-flight read is interrupted because the caller / OS cancelled
         * the request (thread interrupt, WorkManager stop, process going to the
         * background). Surfaces as [InterruptedIOException]. This is a
         * *cancellation*, not a broken network: the contract is that it must not
         * masquerade as "already up to date", but it is deliberately NOT a
         * retryable connectivity failure (a user/OS cancel must not light a
         * persistent "check failed, tap to retry" affordance).
         */
        CANCELLED_READ,

        /**
         * Malformed / truncated API payload: the releases endpoint answers with
         * a partial or corrupt JSON body that carries no usable `tag_name`.
         * The read *succeeds* at the HTTP layer, so — like [CAPTIVE_PORTAL_HTML]
         * — this must be classified as a connectivity/interception failure, not
         * an "already on version" result.
         */
        TRUNCATED_JSON,

        /**
         * Captive-portal / transparent-proxy interception: the request returns
         * HTTP 200 with a login/interstitial HTML page instead of the expected
         * JSON or checksum text. This is the insidious case because the read
         * *succeeds* at the HTTP layer while carrying no usable payload.
         */
        CAPTIVE_PORTAL_HTML,
    }

    /** A valid `tag_name` JSON body for a release NEWER than the running app. */
    const val NEWER_RELEASE_TAG: String = "v999.0.0"

    /** Well-formed releases JSON advertising [NEWER_RELEASE_TAG]. */
    const val NEWER_RELEASE_JSON: String =
        "{\"tag_name\":\"$NEWER_RELEASE_TAG\",\"assets\":[]}"

    /**
     * A truncated JSON body: the response was cut off mid-object, so the
     * tolerant parser cannot recover a `tag_name`. Deterministic and local.
     */
    const val TRUNCATED_JSON_BODY: String = "{\"tag_name\":\"v9"

    /** Canonical HTML body a captive portal / proxy returns on an HTTP 200. */
    const val CAPTIVE_PORTAL_BODY: String =
        "<!DOCTYPE html><html><head><title>Sign in to Wi-Fi</title></head>" +
            "<body><h1>Login required</h1><form action=\"/login\"></form></body></html>"

    /**
     * Faults whose HTTP read *succeeds* but returns an unusable body (no valid
     * release metadata). These have no exception form; the harness feeds the
     * garbage payload to the parser and Kani's classification is what must
     * reject it.
     */
    private val SUCCESSFUL_BUT_UNUSABLE: Set<Fault> =
        setOf(Fault.CAPTIVE_PORTAL_HTML, Fault.TRUNCATED_JSON)

    /** The unusable body a [SUCCESSFUL_BUT_UNUSABLE] fault returns from `getText`. */
    private fun unusableBody(fault: Fault): String = when (fault) {
        Fault.CAPTIVE_PORTAL_HTML -> CAPTIVE_PORTAL_BODY
        Fault.TRUNCATED_JSON -> TRUNCATED_JSON_BODY
        else -> error("$fault is not a successful-but-unusable fault")
    }

    /** Builds the exception a given transport-level [Fault] raises on read. */
    @JvmStatic
    fun exceptionFor(fault: Fault): IOException = when (fault) {
        Fault.NO_ROUTE -> NoRouteToHostException("No route to host")
        Fault.DNS_FAILURE -> UnknownHostException("api.github.com")
        Fault.CONNECTION_REFUSED -> ConnectException("Connection refused")
        Fault.BLACK_HOLE_TIMEOUT -> SocketTimeoutException("Read timed out")
        // A slow/high-latency response that the socket read gives up on
        // surfaces, at this seam, as the same read timeout as a black hole.
        Fault.SLOW_RESPONSE_TIMEOUT -> SocketTimeoutException("Read timed out (slow response)")
        Fault.TLS_HANDSHAKE_FAILURE -> SSLHandshakeException("handshake_failure")
        Fault.MID_REQUEST_DISCONNECT -> SocketException("Connection reset")
        // A cancelled/interrupted read is an InterruptedIOException, NOT a
        // socket subclass, so production must classify it as non-retryable.
        Fault.CANCELLED_READ -> InterruptedIOException("thread interrupted")
        Fault.CAPTIVE_PORTAL_HTML, Fault.TRUNCATED_JSON ->
            throw IllegalArgumentException(
                "$fault is a successful HTTP read, not an exception; use updateClient()",
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
            if (fault in SUCCESSFUL_BUT_UNUSABLE) {
                // The read succeeds (HTTP 200) but the body carries no usable
                // release metadata; classification is what must reject it.
                return unusableBody(fault)
            }
            throw exceptionFor(fault)
        }

        override fun download(url: String, file: File) {
            if (fault in SUCCESSFUL_BUT_UNUSABLE) {
                // A portal/proxy would stream its garbage into the APK file.
                // Model it as a successful write so downstream checksum/APK
                // validation is what rejects it.
                file.writeText(unusableBody(fault))
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

    /**
     * A client that scripts a fixed sequence of behaviours across successive
     * `getText` calls, for online↔offline *flapping* regressions. Each element
     * of [script] is either a [Fault] (raise/return that fault) or `null`
     * (respond healthily with an up-to-date release, so the check reports
     * "already on version" and clears the failure flag). The client walks the
     * script one entry per `getText` call and clamps to the last entry.
     *
     * `download` always throws, because every scripted step here resolves at
     * the first `getText` (either a fault, or an up-to-date no-download result).
     */
    @JvmStatic
    fun flappingClient(script: List<Fault?>): GitHubUpdater.UpdateClient {
        require(script.isNotEmpty()) { "flapping script must not be empty" }
        val step = AtomicInteger(0)
        return object : GitHubUpdater.UpdateClient {
            override fun getText(url: String): String {
                val index = minOf(step.getAndIncrement(), script.size - 1)
                val fault = script[index]
                if (fault == null) {
                    // Healthy: report the running version so the check is a clean
                    // "already up to date" that clears the update-check-failed flag.
                    return "{\"tag_name\":\"${dev.bee.kanjianki.BuildConfig.VERSION_NAME}\"}"
                }
                if (fault in SUCCESSFUL_BUT_UNUSABLE) {
                    return unusableBody(fault)
                }
                throw exceptionFor(fault)
            }

            override fun download(url: String, file: File) =
                error("download must not be reached in a flapping script step")

            override fun inspectApk(apkFile: File): GitHubUpdater.ApkMetadata =
                error("inspectApk must not be reached in a flapping script step")

            override fun installedSigningCertificates(packageName: String): SigningCertificateInfo =
                error("installedSigningCertificates must not be reached in a flapping script step")

            override fun canRequestPackageInstalls(): Boolean =
                error("canRequestPackageInstalls must not be reached in a flapping script step")

            override fun startPackageInstaller(
                apkFile: File,
                version: String,
                source: GitHubUpdater.UpdateSource,
                targetSdkVersion: Int,
            ) = error("startPackageInstaller must not be reached in a flapping script step")

            override fun showPendingUpdate(version: String, message: String): Boolean =
                error("showPendingUpdate must not be reached in a flapping script step")
        }
    }
}
