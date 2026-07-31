package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectEnvelope.Response
import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectHandshake.Status
import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectTransport.Exchange
import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectTransport.Reason
import dev.bee.kanjianki.provider.ankiconnect.FakeAnkiConnectServer.Reply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The end-to-end failure matrix over the real socket path (fake loopback
 * server): malformed JSON, oversize body, HTTP-200 protocol error, unauthorized
 * key, wrong version, missing required action.
 */
class AnkiConnectFailureMatrixTest {
    private fun grantedPermission() = Reply.Body("""{"result":{"permission":"granted"},"error":null}""")

    @Test
    fun malformedJsonBodyIsAProtocolError() {
        FakeAnkiConnectServer.start().use { server ->
            server.on("version", Reply.Raw("{ this is not json"))
            val exchange = server.transport().post(AnkiConnectEnvelope.request("version"))
            assertTrue(exchange is Exchange.Body)
            assertEquals(Response.ProtocolError, AnkiConnectEnvelope.parse((exchange as Exchange.Body).text))
        }
    }

    @Test
    fun oversizeBodyIsRejectedByTheTransport() {
        FakeAnkiConnectServer.start().use { server ->
            server.on("version", Reply.Oversize(2_000))
            val transport = AnkiConnectTransport(
                server.endpoint,
                JdkHttpExchange(),
                maxResponseBytes = 512,
                addressResolver = { arrayOf(java.net.InetAddress.getByName(it)) },
            )
            val exchange = transport.post(AnkiConnectEnvelope.request("version"))
            assertTrue(exchange is Exchange.Failure && exchange.reason == Reason.RESPONSE_TOO_LARGE)
        }
    }

    @Test
    fun http200WithAnErrorEnvelopeIsAFailedResponse() {
        FakeAnkiConnectServer.start().use { server ->
            server.on("version", Reply.Body("""{"result":null,"error":"collection is not available"}"""))
            val exchange = server.transport().post(AnkiConnectEnvelope.request("version")) as Exchange.Body
            assertEquals(Response.Failed("collection is not available"), AnkiConnectEnvelope.parse(exchange.text))
        }
    }

    @Test
    fun httpErrorStatusIsATransportFailure() {
        FakeAnkiConnectServer.start().use { server ->
            server.on("version", Reply.Body("""{"result":6,"error":null}""", status = 500))
            val exchange = server.transport().post(AnkiConnectEnvelope.request("version"))
            assertTrue(exchange is Exchange.Failure && exchange.reason == Reason.HTTP_ERROR_STATUS)
        }
    }

    @Test
    fun unauthorizedKeyManifestsAsAFailedPermissionEnvelope() {
        FakeAnkiConnectServer.start().use { server ->
            // AnkiConnect reports auth failure as an error envelope.
            server.on("requestPermission", Reply.Body("""{"result":null,"error":"valid api key must be provided"}"""))
            val status = AnkiConnectHandshake(server.transport()).run()
            assertTrue(status is Status.Unavailable)
        }
    }

    @Test
    fun wrongVersionIsReportedByTheHandshake() {
        FakeAnkiConnectServer.start().use { server ->
            server
                .on("requestPermission", grantedPermission())
                .on("version", Reply.Body("""{"result":4,"error":null}"""))
            assertEquals(Status.UnsupportedVersion(4), AnkiConnectHandshake(server.transport()).run())
        }
    }

    @Test
    fun missingRequiredActionIsReportedByTheHandshake() {
        FakeAnkiConnectServer.start().use { server ->
            val partial = AnkiConnectActions.required - setOf("multi")
            val items = partial.joinToString(",") { "\"$it\"" }
            server
                .on("requestPermission", grantedPermission())
                .on("version", Reply.Body("""{"result":6,"error":null}"""))
                .on("apiReflect", Reply.Body("""{"result":{"scopes":["actions"],"actions":[$items]},"error":null}"""))
            val status = AnkiConnectHandshake(server.transport()).run()
            assertTrue(status is Status.MissingRequiredActions)
            assertEquals(setOf("multi"), (status as Status.MissingRequiredActions).actions)
        }
    }

    @Test
    fun healthyServerReachesReadyOverTheRealSocket() {
        FakeAnkiConnectServer.start().use { server ->
            val items = AnkiConnectActions.allowlist.joinToString(",") { "\"$it\"" }
            server
                .on("requestPermission", grantedPermission())
                .on("version", Reply.Body("""{"result":6,"error":null}"""))
                .on("apiReflect", Reply.Body("""{"result":{"scopes":["actions"],"actions":[$items]},"error":null}"""))
                .on("getActiveProfile", Reply.Body("""{"result":"User 1","error":null}"""))
            val status = AnkiConnectHandshake(server.transport()).run()
            assertEquals(Status.Ready(6, "User 1", AnkiConnectActions.optional), status)
        }
    }
}
