package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectTransport.Exchange
import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectTransport.HttpExchange
import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectTransport.Reason
import java.io.IOException
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiConnectTransportTest {
    private val endpoint =
        (AnkiConnectEndpoint.parse(AnkiConnectEndpoint.DEFAULT_URL) as AnkiConnectEndpoint.Result.Valid).endpoint
    private val request = AnkiConnectEnvelope.request("version")

    private fun fixedResult(result: HttpExchange.Result) = object : HttpExchange {
        var calls = 0
        override fun post(endpoint: AnkiConnectEndpoint, body: String, maxResponseBytes: Long): HttpExchange.Result {
            calls++
            return result
        }
    }

    private fun loopbackResolver(): (String) -> Array<InetAddress> =
        { arrayOf(InetAddress.getByName("127.0.0.1")) }

    @Test
    fun returnsTheBodyForA200Response() {
        val transport = AnkiConnectTransport(
            endpoint,
            fixedResult(HttpExchange.Result.Ok(200, """{"result":6,"error":null}""")),
            addressResolver = loopbackResolver(),
        )
        val exchange = transport.post(request)
        assertEquals(Exchange.Body("""{"result":6,"error":null}"""), exchange)
    }

    @Test
    fun refusesToSendWhenTheHostResolvesOffLoopback() {
        val exchangeSpy = fixedResult(HttpExchange.Result.Ok(200, "{}"))
        val transport = AnkiConnectTransport(
            endpoint,
            exchangeSpy,
            addressResolver = { arrayOf(InetAddress.getByName("8.8.8.8")) },
        )
        val result = transport.post(request)
        assertTrue(result is Exchange.Failure && result.reason == Reason.NON_LOOPBACK_RESOLUTION)
        // Critically, the socket was never touched.
        assertEquals(0, exchangeSpy.calls)
    }

    @Test
    fun refusesWhenResolutionReturnsNoAddresses() {
        val transport = AnkiConnectTransport(
            endpoint,
            fixedResult(HttpExchange.Result.Ok(200, "{}")),
            addressResolver = { emptyArray() },
        )
        val result = transport.post(request)
        assertTrue(result is Exchange.Failure && result.reason == Reason.NON_LOOPBACK_RESOLUTION)
    }

    @Test
    fun reportsConnectionFailedWhenResolutionThrows() {
        val transport = AnkiConnectTransport(
            endpoint,
            fixedResult(HttpExchange.Result.Ok(200, "{}")),
            addressResolver = { throw IOException("no such host") },
        )
        val result = transport.post(request)
        assertTrue(result is Exchange.Failure && result.reason == Reason.CONNECTION_FAILED)
    }

    @Test
    fun mapsHttpErrorStatusToFailure() {
        val transport = AnkiConnectTransport(
            endpoint,
            fixedResult(HttpExchange.Result.Ok(500, "boom")),
            addressResolver = loopbackResolver(),
        )
        val result = transport.post(request)
        assertTrue(result is Exchange.Failure && result.reason == Reason.HTTP_ERROR_STATUS)
    }

    @Test
    fun mapsEachTransportResultToItsReason() {
        val cases = mapOf(
            HttpExchange.Result.TooLarge to Reason.RESPONSE_TOO_LARGE,
            HttpExchange.Result.Timeout to Reason.TIMEOUT,
            HttpExchange.Result.Cancelled to Reason.CANCELLED,
            HttpExchange.Result.ConnectionFailed("x") to Reason.CONNECTION_FAILED,
        )
        for ((httpResult, expected) in cases) {
            val transport = AnkiConnectTransport(endpoint, fixedResult(httpResult), addressResolver = loopbackResolver())
            val result = transport.post(request)
            assertTrue("$httpResult -> $result", result is Exchange.Failure && result.reason == expected)
        }
    }

    @Test
    fun mapsAnUnexpectedExchangeExceptionToConnectionFailed() {
        val throwing = object : HttpExchange {
            override fun post(endpoint: AnkiConnectEndpoint, body: String, maxResponseBytes: Long): HttpExchange.Result {
                throw RuntimeException("secret-bearing detail should not leak")
            }
        }
        val transport = AnkiConnectTransport(endpoint, throwing, addressResolver = loopbackResolver())
        val result = transport.post(request)
        assertTrue(result is Exchange.Failure && result.reason == Reason.CONNECTION_FAILED)
        // The redacted detail must not echo the exception message.
        assertEquals("transport error", (result as Exchange.Failure).detail)
    }
}
