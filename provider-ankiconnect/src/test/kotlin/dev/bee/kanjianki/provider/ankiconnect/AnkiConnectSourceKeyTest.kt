package dev.bee.kanjianki.provider.ankiconnect

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AnkiConnectSourceKeyTest {
    @Test
    fun pairsTheEndpointWithTheActiveProfile() {
        assertEquals(
            "http://127.0.0.1:8765|User 1",
            AnkiConnectSourceKey.of("http://127.0.0.1:8765", "User 1"),
        )
    }

    @Test
    fun distinguishesProfilesOnTheSameEndpoint() {
        // The whole point: one endpoint serves every profile on the machine.
        assertNotEquals(
            AnkiConnectSourceKey.of("http://127.0.0.1:8765", "User 1"),
            AnkiConnectSourceKey.of("http://127.0.0.1:8765", "User 2"),
        )
    }

    @Test
    fun distinguishesEndpointsForTheSameProfileName() {
        assertNotEquals(
            AnkiConnectSourceKey.of("http://127.0.0.1:8765", "User 1"),
            AnkiConnectSourceKey.of("http://127.0.0.1:8766", "User 1"),
        )
    }

    /**
     * The two components stay unambiguous only because the separator cannot occur
     * in the endpoint half. If an endpoint URL could contain `|`, endpoint `a` +
     * profile `b|c` and endpoint `a|b` + profile `c` would compose the same key
     * and two distinct sources would validate as each other. The endpoint parser
     * is what makes that unreachable, so pin it here.
     */
    @Test
    fun theEndpointParserRejectsTheSeparatorCharacter() {
        assertEquals(
            AnkiConnectEndpoint.Result.Invalid(AnkiConnectEndpoint.Rejection.MALFORMED),
            AnkiConnectEndpoint.parse("http://127.0.0.1:8765|evil"),
        )
    }

    @Test
    fun rejectsBlankComponents() {
        assertThrows(IllegalArgumentException::class.java) {
            AnkiConnectSourceKey.of("", "User 1")
        }
        assertThrows(IllegalArgumentException::class.java) {
            AnkiConnectSourceKey.of("http://127.0.0.1:8765", "  ")
        }
    }
}
