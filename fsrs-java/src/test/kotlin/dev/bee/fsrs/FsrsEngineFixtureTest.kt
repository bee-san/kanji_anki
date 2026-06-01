@file:Suppress("DEPRECATION")

package dev.bee.fsrs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

import java.nio.file.Files
import java.nio.file.Path
import java.util.ArrayList
import java.util.LinkedHashMap

private const val TOLERANCE = 1.0e-9

class FsrsEngineFixtureTest {
    @Test
    fun generatedReferenceFixtureIsTheEngineOracle() {
        val root = asObject(Json.parse(Files.readString(referenceCasesPath())))
        val cases = asList(root["cases"])

        assertEquals(38, cases.size)
        for (rawCase in cases) {
            val testCase = asObject(rawCase)
            val name = text(testCase, "name")
            when (text(testCase, "kind")) {
                "initial" -> assertInitialCase(name, testCase)
                "review" -> assertReviewCase(name, testCase)
                "interval" -> assertIntervalCase(name, testCase)
                "shortTerm" -> assertShortTermCase(name, testCase)
                "validation" -> assertValidationCase(name, testCase)
                else -> fail("Unknown fixture case kind for $name")
            }
        }
    }

    private fun assertInitialCase(name: String, testCase: Map<String, Any?>) {
        val state = engine(testCase).initialState(rating(testCase))
        assertState(name, state, asObject(testCase["expectedInitialState"]))
    }

    private fun assertReviewCase(name: String, testCase: Map<String, Any?>) {
        val previous = asObject(testCase["previousState"])
        val output = requireNotNull(
            engine(testCase).review(
                FsrsReviewInput(
                    FsrsMemoryState(number(previous, "stability"), number(previous, "difficulty")),
                    rating(testCase),
                    integer(testCase, "elapsedDays"),
                    number(testCase, "desiredRetention"),
                    integer(testCase, "maximumInterval"),
                )
            )
        )

        assertState(name, output.nextState, asObject(testCase["expectedNextState"]))
        assertEquals(name + " retrievability", number(testCase, "expectedRetrievability"), output.retrievability, TOLERANCE)
        assertEquals(name + " interval", integer(testCase, "expectedNextIntervalDays"), output.nextIntervalDays)
    }

    private fun assertIntervalCase(name: String, testCase: Map<String, Any?>) {
        val interval = engine(testCase).nextIntervalDays(
            number(testCase, "stability"),
            number(testCase, "desiredRetention"),
            integer(testCase, "maximumInterval"),
        )

        assertEquals(name + " interval", integer(testCase, "expectedNextIntervalDays"), interval)
    }

    private fun assertShortTermCase(name: String, testCase: Map<String, Any?>) {
        val stability = engine(testCase).shortTermStability(number(testCase, "stability"), rating(testCase))

        assertEquals(name + " short-term stability", number(testCase, "expectedShortTermStability"), stability, TOLERANCE)
    }

    private fun assertValidationCase(name: String, testCase: Map<String, Any?>) {
        val payload = asObject(testCase["payload"])
        expectIllegalArgument {
            when (text(testCase, "target")) {
                "FsrsParameters.of" -> FsrsParameters.of(doubleArray(asList(payload["parameters"])))
                "FsrsEngine.nextState" -> {
                    val previous = asObject(payload["previousState"])
                    engine(testCase).nextState(
                        FsrsMemoryState(number(previous, "stability"), number(previous, "difficulty")),
                        rating(payload),
                        integer(payload, "elapsedDays"),
                    )
                }
                "FsrsMemoryState" -> FsrsMemoryState(number(payload, "stability"), number(payload, "difficulty"))
                "FsrsEngine.nextIntervalDays" -> engine(testCase).nextIntervalDays(
                    number(payload, "stability"),
                    number(payload, "desiredRetention"),
                    integer(payload, "maximumInterval"),
                )
                else -> fail("Unknown validation target for $name")
            }
        }
    }

    private fun engine(testCase: Map<String, Any?>): FsrsEngine {
        return FsrsEngine.create(FsrsParameters.of(doubleArray(asList(testCase["parameters"]))))
    }

    private fun rating(values: Map<String, Any?>): FsrsRating {
        return FsrsRating.valueOf(text(values, "rating"))
    }

    private fun assertState(name: String, state: FsrsMemoryState?, expected: Map<String, Any?>) {
        val actual = requireNotNull(state)
        assertEquals(name + " stability", number(expected, "stability"), actual.stability, TOLERANCE)
        assertEquals(name + " difficulty", number(expected, "difficulty"), actual.difficulty, TOLERANCE)
    }

    private fun referenceCasesPath(): Path {
        val modulePath = Path.of("testdata", "upstream-reference-cases.json")
        if (Files.exists(modulePath)) {
            return modulePath
        }
        return Path.of("fsrs-java", "testdata", "upstream-reference-cases.json")
    }

    private fun doubleArray(values: List<Any?>): DoubleArray {
        val out = DoubleArray(values.size)
        for (i in values.indices) {
            out[i] = number(values[i])
        }
        return out
    }

    @Suppress("UNCHECKED_CAST")
    private fun asObject(value: Any?): Map<String, Any?> {
        return value as Map<String, Any?>
    }

    @Suppress("UNCHECKED_CAST")
    private fun asList(value: Any?): List<Any?> {
        return value as List<Any?>
    }

    private fun text(values: Map<String, Any?>, key: String): String {
        return values[key] as String
    }

    private fun integer(values: Map<String, Any?>, key: String): Int {
        return Math.round(number(values, key)).toInt()
    }

    private fun number(values: Map<String, Any?>, key: String): Double {
        return number(values[key])
    }

    private fun number(value: Any?): Double {
        if (value is Number) {
            return value.toDouble()
        }
        if (value == "NaN") {
            return Double.NaN
        }
        throw IllegalArgumentException("Expected number but got $value")
    }

    private fun expectIllegalArgument(block: () -> Unit) {
        try {
            block()
            fail("Expected IllegalArgumentException")
        } catch (expected: IllegalArgumentException) {
            assertNotNull(expected.message)
        }
    }

    private object Json {
        fun parse(text: String): Any? {
            return Parser(text).parse()
        }

        private class Parser(private val text: String) {
            private var index = 0

            fun parse(): Any? {
                val value = parseValue()
                skipWhitespace()
                if (index != text.length) {
                    throw IllegalArgumentException("Unexpected trailing JSON at $index")
                }
                return value
            }

            private fun parseValue(): Any? {
                skipWhitespace()
                val c = text[index]
                return when (c) {
                    '{' -> parseObject()
                    '[' -> parseArray()
                    '"' -> parseString()
                    't' -> {
                        consume("true")
                        true
                    }
                    'f' -> {
                        consume("false")
                        false
                    }
                    'n' -> {
                        consume("null")
                        null
                    }
                    else -> parseNumber()
                }
            }

            private fun parseObject(): Map<String, Any?> {
                val out = LinkedHashMap<String, Any?>()
                index++
                skipWhitespace()
                if (peek('}')) {
                    index++
                    return out
                }
                while (true) {
                    val key = parseString()
                    skipWhitespace()
                    consume(":")
                    out[key] = parseValue()
                    skipWhitespace()
                    if (peek('}')) {
                        index++
                        return out
                    }
                    consume(",")
                    skipWhitespace()
                }
            }

            private fun parseArray(): List<Any?> {
                val out = ArrayList<Any?>()
                index++
                skipWhitespace()
                if (peek(']')) {
                    index++
                    return out
                }
                while (true) {
                    out.add(parseValue())
                    skipWhitespace()
                    if (peek(']')) {
                        index++
                        return out
                    }
                    consume(",")
                }
            }

            private fun parseString(): String {
                consume("\"")
                val out = StringBuilder()
                while (index < text.length) {
                    val c = text[index++]
                    if (c == '"') {
                        return out.toString()
                    }
                    if (c == '\\') {
                        out.append(parseEscape())
                    } else {
                        out.append(c)
                    }
                }
                throw IllegalArgumentException("Unterminated JSON string")
            }

            private fun parseEscape(): Char {
                val escaped = text[index++]
                return when (escaped) {
                    '"', '\\', '/' -> escaped
                    'b' -> '\b'
                    'f' -> '\u000C'
                    'n' -> '\n'
                    'r' -> '\r'
                    't' -> '\t'
                    'u' -> {
                        val codePoint = text.substring(index, index + 4).toInt(16)
                        index += 4
                        codePoint.toChar()
                    }
                    else -> throw IllegalArgumentException("Unknown JSON escape: $escaped")
                }
            }

            private fun parseNumber(): Double {
                val start = index
                while (index < text.length) {
                    val c = text[index]
                    if ((c in '0'..'9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                        index++
                    } else {
                        break
                    }
                }
                return text.substring(start, index).toDouble()
            }

            private fun consume(expected: String) {
                if (!text.startsWith(expected, index)) {
                    throw IllegalArgumentException("Expected $expected at $index")
                }
                index += expected.length
            }

            private fun peek(expected: Char): Boolean {
                return index < text.length && text[index] == expected
            }

            private fun skipWhitespace() {
                while (index < text.length) {
                    val c = text[index]
                    if (c == ' ' || c == '\n' || c == '\r' || c == '\t') {
                        index++
                    } else {
                        return
                    }
                }
            }
        }
    }
}