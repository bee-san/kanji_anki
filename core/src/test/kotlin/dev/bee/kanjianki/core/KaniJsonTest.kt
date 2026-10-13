package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KaniJsonTest {
    @Test
    fun objectRoundTripsEveryValueShape() {
        val value = linkedMapOf<String, Any?>(
            "text" to "hello",
            "flag" to true,
            "count" to 42L,
            "ratio" to 0.3125,
            "missing" to null,
            "list" to listOf(1L, 2L, 3L),
            "nested" to linkedMapOf<String, Any?>("inner" to "x"),
        )
        val decoded = KaniJson.decode(KaniJson.encode(value))!!
        assertEquals("hello", decoded["text"])
        assertEquals(true, decoded["flag"])
        assertEquals(42L, decoded["count"])
        assertEquals(0.3125, decoded["ratio"] as Double, 0.0)
        assertNull(decoded["missing"])
        assertEquals(listOf(1L, 2L, 3L), decoded["list"])
        @Suppress("UNCHECKED_CAST")
        assertEquals("x", (decoded["nested"] as Map<String, Any?>)["inner"])
    }

    @Test
    fun doublesRoundTripExactly() {
        val values = listOf(0.0, 1.5, -2.25, 3.141592653589793, 1.0e-9, 123456.789)
        values.forEach { d ->
            val decoded = KaniJson.decode(KaniJson.encode(linkedMapOf("d" to d)))!!
            assertEquals(d, decoded["d"] as Double, 0.0)
        }
    }

    @Test
    fun integersDecodeAsLongAndFractionsAsDouble() {
        val decoded = KaniJson.decode("""{"i":7,"d":7.0,"e":1e3}""")!!
        assertTrue(decoded["i"] is Long)
        assertTrue(decoded["d"] is Double)
        assertEquals(1000.0, decoded["e"] as Double, 0.0)
    }

    @Test
    fun stringsEscapeControlAndQuotes() {
        val tricky = "line1\nline2\t\"quoted\"\\slash\u0001"
        val decoded = KaniJson.decode(KaniJson.encode(linkedMapOf("s" to tricky)))!!
        assertEquals(tricky, decoded["s"])
    }

    @Test
    fun arrayRoundTrips() {
        val encoded = KaniJson.encodeArray(listOf("a", 1L, true, null))
        assertEquals(listOf("a", 1L, true, null), KaniJson.decodeArray(encoded))
    }

    @Test
    fun malformedInputDecodesToNull() {
        assertNull(KaniJson.decode(null))
        assertNull(KaniJson.decode(""))
        assertNull(KaniJson.decode("{not json"))
        assertNull(KaniJson.decode("""{"a":1} trailing"""))
        assertNull(KaniJson.decodeArray("[1,2"))
        // A JSON array is not an object and vice versa.
        assertNull(KaniJson.decode("[1,2,3]"))
        assertNull(KaniJson.decodeArray("""{"a":1}"""))
    }

    @Test
    fun unicodeEscapesDecode() {
        assertEquals("\u65e5", KaniJson.decode("""{"s":"\u65e5"}""")!!["s"])
    }
}
