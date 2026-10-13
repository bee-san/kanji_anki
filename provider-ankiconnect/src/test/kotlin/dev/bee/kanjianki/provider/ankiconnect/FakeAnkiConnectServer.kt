package dev.bee.kanjianki.provider.ankiconnect

import com.sun.net.httpserver.HttpServer
import java.io.Closeable
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * A deterministic in-process AnkiConnect server for the failure/handshake test
 * matrix. It answers each request from a per-action script and can inject
 * transport-level faults (oversize body, malformed JSON, HTTP error status).
 * Loopback-only, so it also exercises the transport's post-resolution loopback
 * acceptance.
 */
class FakeAnkiConnectServer private constructor(
    private val server: HttpServer,
) : Closeable {
    /** How the server should respond to a given action. */
    sealed interface Reply {
        data class Body(val json: String, val status: Int = 200) : Reply
        data class Raw(val text: String, val status: Int = 200) : Reply
        data class Oversize(val bytes: Int) : Reply
    }

    private val replies = HashMap<String, Reply>()

    /** Bodies received, in order, so tests can assert what was sent. */
    val received = mutableListOf<String>()

    val endpoint: AnkiConnectEndpoint
        get() = (AnkiConnectEndpoint.parse("http://127.0.0.1:${server.address.port}") as AnkiConnectEndpoint.Result.Valid).endpoint

    fun on(action: String, reply: Reply): FakeAnkiConnectServer {
        replies[action] = reply
        return this
    }

    fun transport(): AnkiConnectTransport =
        AnkiConnectTransport(endpoint, JdkHttpExchange(), addressResolver = { host ->
            arrayOf(InetAddress.getByName(host))
        })

    override fun close() {
        server.stop(0)
    }

    companion object {
        fun start(): FakeAnkiConnectServer {
            val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
            val fake = FakeAnkiConnectServer(server)
            server.createContext("/") { exchange ->
                val requestBody = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
                fake.received += requestBody
                val action = AnkiConnectJson.decode(requestBody)
                    ?.let { it as? AnkiConnectJson.Json.Obj }
                    ?.entries?.get("action")
                    ?.let { (it as? AnkiConnectJson.Json.Str)?.value }
                when (val reply = fake.replies[action]) {
                    is Reply.Body -> respond(exchange, reply.status, reply.json.toByteArray())
                    is Reply.Raw -> respond(exchange, reply.status, reply.text.toByteArray())
                    is Reply.Oversize -> respond(exchange, 200, ByteArray(reply.bytes) { 'a'.code.toByte() })
                    null -> respond(exchange, 404, "no reply".toByteArray())
                }
            }
            server.start()
            return fake
        }

        private fun respond(exchange: com.sun.net.httpserver.HttpExchange, status: Int, body: ByteArray) {
            exchange.sendResponseHeaders(status, body.size.toLong())
            exchange.responseBody.use { it.write(body) }
        }
    }
}
