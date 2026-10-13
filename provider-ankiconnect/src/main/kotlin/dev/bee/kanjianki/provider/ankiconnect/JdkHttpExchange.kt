package dev.bee.kanjianki.provider.ankiconnect

import java.io.InputStream
import java.net.ProxySelector
import java.net.http.HttpClient
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Duration
import java.util.concurrent.CancellationException

/**
 * Production [AnkiConnectTransport.HttpExchange] over the JDK `HttpClient`.
 * Redirects are disabled (AnkiConnect never redirects, and a redirect could
 * move a request with an attached key off loopback), no proxy is used, and both
 * a connect deadline and a per-request deadline are enforced. The response body
 * is read through a hard byte cap so an oversize or unbounded body cannot
 * exhaust memory. No request or response payload is ever logged.
 */
class JdkHttpExchange(
    private val connectTimeout: Duration = AnkiConnectTransport.DEFAULT_CONNECT_TIMEOUT,
    private val requestTimeout: Duration = AnkiConnectTransport.DEFAULT_REQUEST_TIMEOUT,
    clientFactory: (Duration) -> HttpClient = { connect ->
        HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .proxy(ProxySelector.of(null))
            .connectTimeout(connect)
            .build()
    },
) : AnkiConnectTransport.HttpExchange {
    private val client: HttpClient = clientFactory(connectTimeout)

    override fun post(
        endpoint: AnkiConnectEndpoint,
        body: String,
        maxResponseBytes: Long,
    ): AnkiConnectTransport.HttpExchange.Result {
        val request = HttpRequest.newBuilder()
            .uri(endpoint.uri)
            .timeout(requestTimeout)
            .header("Content-Type", "application/json; charset=utf-8")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        return try {
            val response = client.send(request, boundedBodyHandler(maxResponseBytes))
            when (val payload = response.body()) {
                is BodyResult.TooLarge -> AnkiConnectTransport.HttpExchange.Result.TooLarge
                is BodyResult.Text ->
                    AnkiConnectTransport.HttpExchange.Result.Ok(response.statusCode(), payload.text)
            }
        } catch (_: HttpConnectTimeoutException) {
            AnkiConnectTransport.HttpExchange.Result.Timeout
        } catch (_: HttpTimeoutException) {
            AnkiConnectTransport.HttpExchange.Result.Timeout
        } catch (_: CancellationException) {
            AnkiConnectTransport.HttpExchange.Result.Cancelled
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            AnkiConnectTransport.HttpExchange.Result.Cancelled
        } catch (_: Exception) {
            // Redacted: the message could echo the endpoint or payload.
            AnkiConnectTransport.HttpExchange.Result.ConnectionFailed("io error")
        }
    }

    private sealed interface BodyResult {
        data class Text(val text: String) : BodyResult
        data object TooLarge : BodyResult
    }

    private fun boundedBodyHandler(
        maxResponseBytes: Long,
    ): HttpResponse.BodyHandler<BodyResult> = HttpResponse.BodyHandler {
        HttpResponse.BodySubscribers.mapping(
            HttpResponse.BodySubscribers.ofInputStream(),
        ) { stream -> readBounded(stream, maxResponseBytes) }
    }

    private fun readBounded(stream: InputStream, maxResponseBytes: Long): BodyResult =
        stream.use { input ->
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(DEFAULT_BUFFER_SIZE)
            var total = 0L
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                total += read
                if (total > maxResponseBytes) return BodyResult.TooLarge
                buffer.write(chunk, 0, read)
            }
            BodyResult.Text(buffer.toString(Charsets.UTF_8))
        }
}
