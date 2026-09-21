package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectEndpoint.Rejection
import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectEndpoint.Result
import java.net.InetAddress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiConnectEndpointTest {
    private fun reject(url: String): Rejection {
        val result = AnkiConnectEndpoint.parse(url)
        assertTrue("expected Invalid for $url but was $result", result is Result.Invalid)
        return (result as Result.Invalid).reason
    }

    private fun accept(url: String): AnkiConnectEndpoint {
        val result = AnkiConnectEndpoint.parse(url)
        assertTrue("expected Valid for $url but was $result", result is Result.Valid)
        return (result as Result.Valid).endpoint
    }

    @Test
    fun acceptsTheDefaultLoopbackEndpoint() {
        val endpoint = accept(AnkiConnectEndpoint.DEFAULT_URL)
        assertEquals("127.0.0.1", endpoint.host)
        assertEquals(8765, endpoint.port)
    }

    @Test
    fun acceptsLocalhostAndIpv6Loopback() {
        assertEquals("localhost", accept("http://localhost:8765").host)
        assertEquals(9000, accept("http://localhost:9000/").port)
        accept("http://[::1]:8765")
    }

    @Test
    fun rejectsNonHttpSchemes() {
        assertEquals(Rejection.NON_HTTP_SCHEME, reject("ftp://127.0.0.1:8765"))
        assertEquals(Rejection.NON_HTTP_SCHEME, reject("file:///etc/passwd"))
    }

    @Test
    fun rejectsUserinfo() {
        assertEquals(Rejection.HAS_USERINFO, reject("http://user:pass@127.0.0.1:8765"))
    }

    @Test
    fun rejectsNonLoopbackHosts() {
        assertEquals(Rejection.NON_LOOPBACK_HOST, reject("http://192.168.1.5:8765"))
        assertEquals(Rejection.NON_LOOPBACK_HOST, reject("http://anki.example.com:8765"))
        assertEquals(Rejection.NON_LOOPBACK_HOST, reject("http://10.0.0.1:8765"))
    }

    @Test
    fun rejectsAMissingPort() {
        assertEquals(Rejection.MISSING_PORT, reject("http://127.0.0.1"))
    }

    @Test
    fun rejectsUnexpectedPathQueryOrFragment() {
        assertEquals(Rejection.UNEXPECTED_PATH, reject("http://127.0.0.1:8765/admin"))
        assertEquals(Rejection.HAS_QUERY, reject("http://127.0.0.1:8765/?a=1"))
        assertEquals(Rejection.HAS_FRAGMENT, reject("http://127.0.0.1:8765/#x"))
    }

    @Test
    fun rejectsMalformedUrls() {
        assertEquals(Rejection.MALFORMED, reject("http://:::::"))
        assertEquals(Rejection.MALFORMED, reject("not a url at all"))
    }

    @Test
    fun rejectsAHttpUrlWithNoHost() {
        assertEquals(Rejection.MALFORMED, reject("http:///onlypath"))
    }

    @Test
    fun postResolutionLoopbackCheckAcceptsLoopbackAndRejectsPublicIps() {
        assertTrue(AnkiConnectEndpoint.isLoopbackAddress(InetAddress.getByName("127.0.0.1")))
        assertFalse(AnkiConnectEndpoint.isLoopbackAddress(InetAddress.getByName("8.8.8.8")))
    }

    @Test
    fun equalsAndHashCodeFollowTheUri() {
        val a = accept("http://127.0.0.1:8765")
        val b = accept("http://127.0.0.1:8765")
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertTrue(a.toString().contains("127.0.0.1"))
        assertFalse(a == accept("http://127.0.0.1:9000"))
    }
}
