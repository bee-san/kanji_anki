package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectHandshake.Status
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiConnectHandshakeTest {
    private val endpoint =
        (AnkiConnectEndpoint.parse(AnkiConnectEndpoint.DEFAULT_URL) as AnkiConnectEndpoint.Result.Valid).endpoint

    /** A fake exchange that replies per action name from a script. */
    private class ScriptedExchange(
        private val script: Map<String, AnkiConnectTransport.HttpExchange.Result>,
    ) : AnkiConnectTransport.HttpExchange {
        val sentBodies = mutableListOf<String>()
        override fun post(
            endpoint: AnkiConnectEndpoint,
            body: String,
            maxResponseBytes: Long,
        ): AnkiConnectTransport.HttpExchange.Result {
            sentBodies += body
            val action = AnkiConnectJson.decode(body)
                ?.let { it as AnkiConnectJson.Json.Obj }
                ?.entries?.get("action")
                ?.let { (it as AnkiConnectJson.Json.Str).value }
            return script[action] ?: AnkiConnectTransport.HttpExchange.Result.Ok(200, """{"result":null,"error":"unscripted"}""")
        }
    }

    private fun ok(body: String) = AnkiConnectTransport.HttpExchange.Result.Ok(200, body)

    private fun handshake(vararg script: Pair<String, AnkiConnectTransport.HttpExchange.Result>): Pair<AnkiConnectHandshake, ScriptedExchange> {
        val exchange = ScriptedExchange(script.toMap())
        val transport = AnkiConnectTransport(
            endpoint,
            exchange,
            addressResolver = { arrayOf(InetAddress.getByName("127.0.0.1")) },
        )
        return AnkiConnectHandshake(transport) to exchange
    }

    private fun reflectBody(actions: Collection<String>): String {
        val items = actions.joinToString(",") { "\"$it\"" }
        return """{"result":{"scopes":["actions"],"actions":[$items]},"error":null}"""
    }

    @Test
    fun reachesReadyWhenEverythingIsHealthy() {
        val (handshake, exchange) = handshake(
            "requestPermission" to ok("""{"result":{"permission":"granted"},"error":null}"""),
            "version" to ok("""{"result":6,"error":null}"""),
            "apiReflect" to ok(reflectBody(AnkiConnectActions.allowlist)),
            "getActiveProfile" to ok("""{"result":"User 1","error":null}"""),
        )
        val status = handshake.run()
        assertTrue(status is Status.Ready)
        status as Status.Ready
        assertEquals(6, status.version)
        assertEquals("User 1", status.activeProfile)
        assertEquals(AnkiConnectActions.optional, status.availableOptionalActions)
        // The permission probe was sent without a key.
        val firstBody = AnkiConnectJson.decode(exchange.sentBodies.first()) as AnkiConnectJson.Json.Obj
        assertTrue(!firstBody.entries.containsKey("key"))
    }

    @Test
    fun reportsPermissionRequiredWhenDenied() {
        val (handshake, _) = handshake(
            "requestPermission" to ok("""{"result":{"permission":"denied"},"error":null}"""),
        )
        assertEquals(Status.PermissionRequired, handshake.run())
    }

    @Test
    fun reportsUnsupportedVersion() {
        val (handshake, _) = handshake(
            "requestPermission" to ok("""{"result":{"permission":"granted"},"error":null}"""),
            "version" to ok("""{"result":5,"error":null}"""),
        )
        assertEquals(Status.UnsupportedVersion(5), handshake.run())
    }

    @Test
    fun reportsMissingRequiredActions() {
        val partial = AnkiConnectActions.required - setOf("findCards")
        val (handshake, _) = handshake(
            "requestPermission" to ok("""{"result":{"permission":"granted"},"error":null}"""),
            "version" to ok("""{"result":6,"error":null}"""),
            "apiReflect" to ok(reflectBody(partial)),
        )
        val status = handshake.run()
        assertTrue(status is Status.MissingRequiredActions)
        assertEquals(setOf("findCards"), (status as Status.MissingRequiredActions).actions)
    }

    @Test
    fun reportsNoActiveProfileWhenProfileIsNull() {
        val (handshake, _) = handshake(
            "requestPermission" to ok("""{"result":{"permission":"granted"},"error":null}"""),
            "version" to ok("""{"result":6,"error":null}"""),
            "apiReflect" to ok(reflectBody(AnkiConnectActions.allowlist)),
            "getActiveProfile" to ok("""{"result":null,"error":null}"""),
        )
        assertEquals(Status.NoActiveProfile, handshake.run())
    }

    @Test
    fun reportsUnavailableWhenTheProbeTransportFails() {
        val (handshake, _) = handshake(
            "requestPermission" to AnkiConnectTransport.HttpExchange.Result.Timeout,
        )
        assertTrue(handshake.run() is Status.Unavailable)
    }

    @Test
    fun reportsUnavailableOnAProtocolErrorPermissionBody() {
        val (handshake, _) = handshake(
            "requestPermission" to ok("""{"result":{"permission":"maybe"},"error":null}"""),
        )
        assertTrue(handshake.run() is Status.Unavailable)
    }

    @Test
    fun reportsUnavailableWhenVersionApiReflectOrProfileFail() {
        val base = mutableMapOf(
            "requestPermission" to ok("""{"result":{"permission":"granted"},"error":null}"""),
        )
        // version returns a non-number
        var (h, _) = handshake(*(base + ("version" to ok("""{"result":"six","error":null}"""))).toList().toTypedArray())
        assertTrue(h.run() is Status.Unavailable)

        // apiReflect malformed
        base["version"] = ok("""{"result":6,"error":null}""")
        val (h2, _) = handshake(*(base + ("apiReflect" to ok("""{"result":{},"error":null}"""))).toList().toTypedArray())
        assertTrue(h2.run() is Status.Unavailable)

        // getActiveProfile error envelope
        base["apiReflect"] = ok(reflectBody(AnkiConnectActions.allowlist))
        val (h3, _) = handshake(*(base + ("getActiveProfile" to ok("""{"result":7,"error":null}"""))).toList().toTypedArray())
        assertTrue(h3.run() is Status.Unavailable)
    }

    @Test
    fun attachesTheKeyToPostPermissionRequests() {
        val (handshake, exchange) = handshake(
            "requestPermission" to ok("""{"result":{"permission":"granted"},"error":null}"""),
            "version" to ok("""{"result":6,"error":null}"""),
            "apiReflect" to ok(reflectBody(AnkiConnectActions.allowlist)),
            "getActiveProfile" to ok("""{"result":"User 1","error":null}"""),
        )
        handshake.run(apiKey = "topsecret")
        // The version request (second sent) carries the key.
        val versionBody = AnkiConnectJson.decode(exchange.sentBodies[1]) as AnkiConnectJson.Json.Obj
        assertEquals(AnkiConnectJson.Json.Str("topsecret"), versionBody.entries["key"])
    }
}
