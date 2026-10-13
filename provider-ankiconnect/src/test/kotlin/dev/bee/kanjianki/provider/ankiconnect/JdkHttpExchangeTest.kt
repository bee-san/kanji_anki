package dev.bee.kanjianki.provider.ankiconnect

import com.sun.net.httpserver.HttpServer
import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectTransport.HttpExchange
import java.net.InetAddress
import java.net.InetSocketAddress
import java.time.Duration
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises the production JDK adapter against a real in-process loopback HTTP
 * server, so the socket path, bounded-body reader, and status mapping are all
 * proven end-to-end without touching a real Anki.
 */
class JdkHttpExchangeTest {
    private lateinit var server: HttpServer
    private lateinit var endpoint: AnkiConnectEndpoint

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
        server.start()
        val url = "http://127.0.0.1:${server.address.port}"
        endpoint = (AnkiConnectEndpoint.parse(url) as AnkiConnectEndpoint.Result.Valid).endpoint
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    private fun respondWith(status: Int, body: ByteArray) {
        server.createContext("/") { exchange ->
            exchange.requestBody.readBytes()
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
    }

    @Test
    fun postsAndReadsA200Body() {
        respondWith(200, """{"result":6,"error":null}""".toByteArray())
        val exchange = JdkHttpExchange()
        val result = exchange.post(endpoint, AnkiConnectEnvelope.request("version").json, 1_024)
        assertTrue(result is HttpExchange.Result.Ok)
        result as HttpExchange.Result.Ok
        assertEquals(200, result.status)
        assertEquals("""{"result":6,"error":null}""", result.body)
    }

    @Test
    fun reportsErrorStatusBodyThrough() {
        respondWith(500, "internal".toByteArray())
        val result = JdkHttpExchange().post(endpoint, AnkiConnectEnvelope.request("version").json, 1_024)
        assertTrue(result is HttpExchange.Result.Ok)
        assertEquals(500, (result as HttpExchange.Result.Ok).status)
    }

    @Test
    fun enforcesTheResponseByteCap() {
        respondWith(200, ByteArray(4_096) { 'a'.code.toByte() })
        val result = JdkHttpExchange().post(endpoint, AnkiConnectEnvelope.request("version").json, 512)
        assertEquals(HttpExchange.Result.TooLarge, result)
    }

    @Test
    fun reportsConnectionFailedForADeadEndpoint() {
        val deadPort = server.address.port
        server.stop(0)
        val dead = (AnkiConnectEndpoint.parse("http://127.0.0.1:$deadPort") as AnkiConnectEndpoint.Result.Valid).endpoint
        val exchange = JdkHttpExchange(
            connectTimeout = Duration.ofMillis(200),
            requestTimeout = Duration.ofMillis(500),
        )
        val result = exchange.post(dead, AnkiConnectEnvelope.request("version").json, 1_024)
        assertTrue(
            "expected a transport failure but was $result",
            result is HttpExchange.Result.ConnectionFailed || result is HttpExchange.Result.Timeout,
        )
    }
}
