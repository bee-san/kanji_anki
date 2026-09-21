package dev.bee.kanjianki.provider.ankiconnect

import java.net.URI

/**
 * A validated AnkiConnect endpoint. AnkiConnect only ever listens on the local
 * loopback interface, so Kani refuses any endpoint that could send a request —
 * and especially an API key — anywhere else. Construction is fail-closed: the
 * literal URL must be `http://` (or `https://`) on a loopback host, carry no
 * userinfo, no path beyond `/`, and no query or fragment. Post-resolution
 * loopback enforcement (checking the resolved IP) happens later in the
 * transport, once DNS is available; this class rejects everything decidable
 * from the literal alone.
 */
class AnkiConnectEndpoint private constructor(
    val uri: URI,
) {
    val host: String get() = uri.host
    val port: Int get() = uri.port

    override fun toString(): String = "AnkiConnectEndpoint($uri)"

    override fun equals(other: Any?): Boolean = other is AnkiConnectEndpoint && uri == other.uri

    override fun hashCode(): Int = uri.hashCode()

    /** Why a candidate endpoint was rejected. */
    enum class Rejection {
        MALFORMED,
        NON_HTTP_SCHEME,
        HAS_USERINFO,
        NON_LOOPBACK_HOST,
        UNEXPECTED_PATH,
        HAS_QUERY,
        HAS_FRAGMENT,
        MISSING_PORT,
    }

    sealed interface Result {
        data class Valid(val endpoint: AnkiConnectEndpoint) : Result

        data class Invalid(val reason: Rejection) : Result
    }

    companion object {
        /** The default AnkiConnect endpoint. */
        const val DEFAULT_URL = "http://127.0.0.1:8765"

        private val LOOPBACK_HOSTS = setOf("127.0.0.1", "localhost", "::1", "[::1]")

        /**
         * Validates [candidateUrl] purely from its literal form. Everything that
         * can be decided without DNS is checked here in a fixed order so the
         * rejection reason is deterministic.
         */
        fun parse(candidateUrl: String): Result {
            val uri = try {
                URI(candidateUrl.trim())
            } catch (_: Exception) {
                return Result.Invalid(Rejection.MALFORMED)
            }
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") {
                return Result.Invalid(Rejection.NON_HTTP_SCHEME)
            }
            if (uri.rawUserInfo != null) {
                return Result.Invalid(Rejection.HAS_USERINFO)
            }
            val host = uri.host ?: return Result.Invalid(Rejection.MALFORMED)
            if (host.lowercase() !in LOOPBACK_HOSTS) {
                return Result.Invalid(Rejection.NON_LOOPBACK_HOST)
            }
            if (uri.port <= 0) {
                return Result.Invalid(Rejection.MISSING_PORT)
            }
            val path = uri.rawPath
            if (!path.isNullOrEmpty() && path != "/") {
                return Result.Invalid(Rejection.UNEXPECTED_PATH)
            }
            if (uri.rawQuery != null) {
                return Result.Invalid(Rejection.HAS_QUERY)
            }
            if (uri.rawFragment != null) {
                return Result.Invalid(Rejection.HAS_FRAGMENT)
            }
            return Result.Valid(AnkiConnectEndpoint(uri))
        }

        /** True when [resolvedIp] is a loopback address (post-resolution check). */
        fun isLoopbackAddress(resolvedIp: java.net.InetAddress): Boolean =
            resolvedIp.isLoopbackAddress
    }
}
