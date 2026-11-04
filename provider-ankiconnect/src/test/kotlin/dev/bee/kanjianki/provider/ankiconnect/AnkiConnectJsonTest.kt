package dev.bee.kanjianki.provider.ankiconnect

import dev.bee.kanjianki.provider.ankiconnect.AnkiConnectJson.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnkiConnectJsonTest {
    @Test
    fun encodesAndReparsesEveryValueShape() {
        val value = AnkiConnectJson.obj(
            "s" to AnkiConnectJson.str("hi\n\"there\"\t\\"),
            "n" to AnkiConnectJson.num(-42),
            "b" to AnkiConnectJson.bool(true),
            "nul" to Json.Null,
            "arr" to AnkiConnectJson.arr(listOf(AnkiConnectJson.num(1), AnkiConnectJson.num(2))),
            "nested" to AnkiConnectJson.obj("k" to AnkiConnectJson.str("v")),
        )
        val text = AnkiConnectJson.encode(value)
        assertEquals(value, AnkiConnectJson.decode(text))
    }

    @Test
    fun decodesControlEscapesAndUnicode() {
        val decoded = AnkiConnectJson.decode("\"line\\nfeed\\u0041\\b\\f\\r\\t\\/\"")
        assertEquals(Json.Str("line\nfeedA\b\u000C\r\t/"), decoded)
    }

    @Test
    fun encodesLowControlCharactersAsUnicodeEscapes() {
        val text = AnkiConnectJson.encode(AnkiConnectJson.str("\u0001"))
        assertEquals("\"\\u0001\"", text)
    }

    /**
     * A fraction decodes to its own type rather than failing the response.
     *
     * Real Anki's `getDeckConfig` carries `"delays": [1.0, 10.0]` and
     * `"ease4": 1.3`, so rejecting fractions outright made every real
     * `getDeckConfig` a protocol error — which the Missing Kanji writer reads as
     * an unprovable deck. The type stays distinct from [Json.Num] so a fraction
     * still cannot pass as an id.
     */
    @Test
    fun decodesFractionalAndExponentNumbersAsFractionsNotIntegers() {
        assertEquals(Json.Frac("1.5"), AnkiConnectJson.decode("1.5"))
        assertEquals(Json.Frac("-0.25"), AnkiConnectJson.decode("-0.25"))
        assertEquals(Json.Frac("2e10"), AnkiConnectJson.decode("2e10"))
        assertEquals(Json.Frac("2E+10"), AnkiConnectJson.decode("2E+10"))
        assertEquals(Json.Frac("1.0e-3"), AnkiConnectJson.decode("1.0e-3"))
    }

    /** An id too large for a `Long` is not silently truncated into one. */
    @Test
    fun decodesOverlargeIntegersAsFractionsRatherThanTruncating() {
        val huge = "9".repeat(25)
        assertEquals(Json.Frac(huge), AnkiConnectJson.decode(huge))
    }

    /** Fractions re-encode verbatim, so a decode/encode round trip is lossless. */
    @Test
    fun reEncodesFractionsAsWritten() {
        val text = """{"delays":[1.0,10.0],"ease4":1.3}"""
        assertEquals(text, AnkiConnectJson.encode(AnkiConnectJson.decode(text)!!))
    }

    @Test
    fun returnsNullForNumbersMissingRequiredDigits() {
        assertNull(AnkiConnectJson.decode("-"))
        assertNull(AnkiConnectJson.decode("1."))
        assertNull(AnkiConnectJson.decode("1e"))
        assertNull(AnkiConnectJson.decode("1e+"))
    }

    /**
     * The real `getDeckConfig` shape: a fraction nested inside the response no
     * longer prevents the integer fields beside it from being read.
     */
    @Test
    fun decodesRealDeckConfigWithFractionalOptions() {
        val decoded = AnkiConnectJson.decode(
            """{"result":{"id":1,"new":{"delays":[1.0,10.0]},"rev":{"ease4":1.3}},"error":null}""",
        )
        val result = ((decoded as Json.Obj).entries["result"] as Json.Obj)
        assertEquals(Json.Num(1), result.entries["id"])
        assertEquals(
            Json.Arr(listOf(Json.Frac("1.0"), Json.Frac("10.0"))),
            (result.entries["new"] as Json.Obj).entries["delays"],
        )
    }

    @Test
    fun parsesTopLevelPrimitivesAndEmptyContainers() {
        assertEquals(Json.Bool(false), AnkiConnectJson.decode("false"))
        assertEquals(Json.Null, AnkiConnectJson.decode("null"))
        assertEquals(Json.Num(7), AnkiConnectJson.decode("  7  "))
        assertEquals(Json.Obj(emptyMap()), AnkiConnectJson.decode("{}"))
        assertEquals(Json.Arr(emptyList()), AnkiConnectJson.decode("[]"))
    }

    @Test
    fun returnsNullForMalformedJson() {
        assertNull(AnkiConnectJson.decode("{"))
        assertNull(AnkiConnectJson.decode("[1,2"))
        assertNull(AnkiConnectJson.decode("{\"a\":}"))
        assertNull(AnkiConnectJson.decode("truthy"))
        assertNull(AnkiConnectJson.decode("nul"))
        assertNull(AnkiConnectJson.decode("\"unterminated"))
        assertNull(AnkiConnectJson.decode("{\"a\":1} trailing"))
        assertNull(AnkiConnectJson.decode(""))
    }

    @Test
    fun returnsNullForBadStringEscape() {
        assertNull(AnkiConnectJson.decode("\"\\x\""))
    }

    @Test
    fun rejectsOverlyDeepNesting() {
        val deep = "[".repeat(AnkiConnectJson.MAX_DEPTH + 2) + "]".repeat(AnkiConnectJson.MAX_DEPTH + 2)
        assertNull(AnkiConnectJson.decode(deep))
    }

    @Test
    fun decodesTypicalAnkiConnectResultArray() {
        val decoded = AnkiConnectJson.decode("""{"result":[1498938915662,1502098034048],"error":null}""")
        assertTrue(decoded is Json.Obj)
        val result = (decoded as Json.Obj).entries["result"]
        assertTrue(result is Json.Arr)
        assertEquals(2, (result as Json.Arr).items.size)
    }
}
