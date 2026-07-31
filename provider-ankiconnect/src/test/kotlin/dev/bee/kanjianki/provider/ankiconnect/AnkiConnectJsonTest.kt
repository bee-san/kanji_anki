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

    @Test
    fun rejectsFractionalAndExponentNumbers() {
        assertNull(AnkiConnectJson.decode("1.5"))
        assertNull(AnkiConnectJson.decode("2e10"))
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
