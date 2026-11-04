package dev.bee.kanjianki.provider.ankiconnect

import java.io.IOException
import java.net.InetAddress
import java.time.Duration

/**
 * The bounded, loopback-only transport for AnkiConnect. It owns the security
 * envelope around every HTTP exchange: the endpoint was already literal-validated
 * ([AnkiConnectEndpoint]); here the resolved address is re-checked to be loopback
 * before a byte is sent, the response body is bounded, deadlines are enforced,
 * and every diagnostic is redacted so an API key or collection content can never
 * leak into a log or exception message.
 *
 * The actual socket work is behind [HttpExchange] so the protocol logic is unit
 * testable with a deterministic in-process fake; [JdkHttpExchange] is the
 * production adapter over the JDK `HttpClient`.
 */
class AnkiConnectTransport(
    private val endpoint: AnkiConnectEndpoint,
    private val exchange: HttpExchange,
    private val maxResponseBytes: Long = DEFAULT_MAX_RESPONSE_BYTES,
    private val addressResolver: (String) -> Array<InetAddress> = { host ->
        InetAddress.getAllByName(host)
    },
) {
    /** The outcome of a single POST, before envelope parsing. */
    sealed interface Exchange {
        data class Body(val text: String) : Exchange

        /** A transport-level failure with a redacted, safe-to-log reason. */
        data class Failure(val reason: Reason, val detail: String) : Exchange
    }

    enum class Reason {
        NON_LOOPBACK_RESOLUTION,
        TIMEOUT,
        CONNECTION_FAILED,
        HTTP_ERROR_STATUS,
        RESPONSE_TOO_LARGE,
        CANCELLED,
    }

    /**
     * The validated endpoint URL. Used as the source-identity key for the
     * collection behind this transport; [dev.bee.kanjianki.syncapi.CollectionSourceIdentity]
     * digests it and never persists or logs it raw.
     */
    fun endpointUrl(): String = endpoint.uri.toString()

    /** POSTs an already-built [AnkiConnectEnvelope.Request] and returns the raw body. */
    fun post(request: AnkiConnectEnvelope.Request): Exchange {
        val resolution = try {
            addressResolver(endpoint.host)
        } catch (failure: IOException) {
            return Exchange.Failure(Reason.CONNECTION_FAILED, "address resolution failed")
        }
        if (resolution.isEmpty() || resolution.any { !AnkiConnectEndpoint.isLoopbackAddress(it) }) {
            // Post-resolution enforcement: refuse if the name resolved to anything
            // that is not loopback (DNS-rebinding defense). No address is logged.
            return Exchange.Failure(Reason.NON_LOOPBACK_RESOLUTION, "endpoint did not resolve to loopback")
        }
        return try {
            when (val result = exchange.post(endpoint, request.json, maxResponseBytes)) {
                is HttpExchange.Result.Ok ->
                    if (result.status in 200..299) {
                        Exchange.Body(result.body)
                    } else {
                        Exchange.Failure(Reason.HTTP_ERROR_STATUS, "http status ${result.status}")
                    }
                HttpExchange.Result.TooLarge ->
                    Exchange.Failure(Reason.RESPONSE_TOO_LARGE, "response exceeded $maxResponseBytes bytes")
                HttpExchange.Result.Timeout ->
                    Exchange.Failure(Reason.TIMEOUT, "request timed out")
                HttpExchange.Result.Cancelled ->
                    Exchange.Failure(Reason.CANCELLED, "request cancelled")
                is HttpExchange.Result.ConnectionFailed ->
                    Exchange.Failure(Reason.CONNECTION_FAILED, "connection failed")
            }
        } catch (_: Exception) {
            Exchange.Failure(Reason.CONNECTION_FAILED, "transport error")
        }
    }

    /** The socket seam. Implementations must not log request/response payloads. */
    interface HttpExchange {
        fun post(endpoint: AnkiConnectEndpoint, body: String, maxResponseBytes: Long): Result

        sealed interface Result {
            data class Ok(val status: Int, val body: String) : Result
            data object TooLarge : Result
            data object Timeout : Result
            data object Cancelled : Result
            data class ConnectionFailed(val safeDetail: String) : Result
        }
    }

    companion object {
        const val DEFAULT_MAX_RESPONSE_BYTES: Long = 32L * 1024L * 1024L
        val DEFAULT_CONNECT_TIMEOUT: Duration = Duration.ofSeconds(3)
        val DEFAULT_REQUEST_TIMEOUT: Duration = Duration.ofSeconds(15)
    }
}
