package dev.bee.fsrs

import java.util.ArrayList
import java.util.LinkedHashMap

/**
 * A minimal JSON reader for the reference fixtures.
 *
 * Hand-written because the engine is deliberately dependency-free, and that rule
 * covers the test classpath too: adding a JSON library for the fixtures would make
 * the build's dependency graph disagree with the package's central claim.
 *
 * Shared by the FSRS-6 and FSRS-7 fixture tests so both oracles are read by the
 * same parser. Two copies could come to disagree about number parsing, which would
 * be a silent difference in what the fixtures actually assert.
 */
internal object Json {
    fun parse(text: String): Any? = Parser(text).parse()

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
            return when (text[index]) {
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
                'f' -> ''
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

        private fun peek(expected: Char): Boolean = index < text.length && text[index] == expected

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
