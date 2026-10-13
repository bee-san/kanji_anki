package dev.bee.kanjianki.core

/**
 * A dependency-free JSON reader/writer for the persisted analytics cache
 * (stats format 11). `:core` already carries `CompactJson` for the tiny
 * adaptive-routing payloads, but that codec is private, integer-only, and
 * capped at 256 KiB. The stats cache serializes `Double` metrics and can grow
 * with the inventory, so this facility adds `Double` support and a larger
 * bound while staying pure JVM — the whole point of keeping `:data-sql`
 * free of Android's `org.json`.
 *
 * Values are the standard JSON shapes: `String`, `Boolean`, `Long`, `Double`,
 * `null`, `List<Any?>`, and `Map<String, Any?>`. Integers decode to `Long`;
 * numbers with a `.`/`e` decode to `Double`. Malformed input decodes to null.
 */
object KaniJson {
    private const val MAX_INPUT_CHARS = 8 * 1024 * 1024
    private const val MAX_DEPTH = 32

    fun encode(value: Map<String, Any?>): String = buildString { appendValue(this, value) }

    fun encodeArray(values: List<Any?>): String = buildString { appendValue(this, values) }

    fun decode(encoded: String?): Map<String, Any?>? {
        val root = decodeValue(encoded) ?: return null
        @Suppress("UNCHECKED_CAST")
        return root as? Map<String, Any?>
    }

    fun decodeArray(encoded: String?): List<Any?>? {
        val root = decodeValue(encoded) ?: return null
        return root as? List<Any?>
    }

    private fun decodeValue(encoded: String?): Any? {
        if (encoded.isNullOrBlank() || encoded.length > MAX_INPUT_CHARS) {
            return null
        }
        return try {
            Reader(encoded).readRoot()
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private fun appendValue(target: StringBuilder, value: Any?) {
        when (value) {
            null -> target.append("null")
            is String -> appendString(target, value)
            is Boolean -> target.append(value)
            is Int -> target.append(value)
            is Long -> target.append(value)
            is Double -> appendDouble(target, value)
            is Float -> appendDouble(target, value.toDouble())
            is Map<*, *> -> appendObject(target, value)
            is List<*> -> appendArray(target, value)
            else -> throw IllegalArgumentException("Unsupported JSON value: ${value::class}")
        }
    }

    private fun appendDouble(target: StringBuilder, value: Double) {
        require(value.isFinite()) { "JSON cannot represent a non-finite number" }
        // Emit integral doubles without a trailing ".0" only when they exceed
        // Long range is unnecessary; keep the canonical Double string so the
        // round trip is exact.
        target.append(value.toString())
    }

    private fun appendObject(target: StringBuilder, values: Map<*, *>) {
        target.append('{')
        var first = true
        for ((key, value) in values) {
            if (!first) target.append(',')
            first = false
            appendString(target, key.toString())
            target.append(':')
            appendValue(target, value)
        }
        target.append('}')
    }

    private fun appendArray(target: StringBuilder, values: List<*>) {
        target.append('[')
        values.forEachIndexed { index, value ->
            if (index > 0) target.append(',')
            appendValue(target, value)
        }
        target.append(']')
    }

    private fun appendString(target: StringBuilder, value: String) {
        target.append('"')
        for (character in value) {
            when (character) {
                '"' -> target.append("\\\"")
                '\\' -> target.append("\\\\")
                '\b' -> target.append("\\b")
                '\u000c' -> target.append("\\f")
                '\n' -> target.append("\\n")
                '\r' -> target.append("\\r")
                '\t' -> target.append("\\t")
                else -> if (character.code < 0x20) {
                    target.append("\\u")
                    target.append(character.code.toString(16).padStart(4, '0'))
                } else {
                    target.append(character)
                }
            }
        }
        target.append('"')
    }

    private class Reader(private val source: String) {
        private var index = 0

        fun readRoot(): Any? {
            skipWhitespace()
            val root = readValue(0)
            skipWhitespace()
            require(index == source.length) { "Trailing JSON input" }
            return root
        }

        private fun readValue(depth: Int): Any? {
            require(index < source.length) { "Missing JSON value" }
            return when (source[index]) {
                '"' -> readString()
                '{' -> readObject(depth)
                '[' -> readArray(depth)
                't' -> readLiteral("true", true)
                'f' -> readLiteral("false", false)
                'n' -> readLiteral("null", null)
                '-', in '0'..'9' -> readNumber()
                else -> throw IllegalArgumentException("Invalid JSON value")
            }
        }

        private fun readObject(depth: Int): Map<String, Any?> {
            require(depth < MAX_DEPTH) { "JSON nesting is too deep" }
            expect('{')
            val result = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (consume('}')) return result
            while (true) {
                skipWhitespace()
                val key = readString()
                skipWhitespace()
                expect(':')
                skipWhitespace()
                result[key] = readValue(depth + 1)
                skipWhitespace()
                if (consume('}')) return result
                expect(',')
            }
        }

        private fun readArray(depth: Int): List<Any?> {
            require(depth < MAX_DEPTH) { "JSON nesting is too deep" }
            expect('[')
            val result = ArrayList<Any?>()
            skipWhitespace()
            if (consume(']')) return result
            while (true) {
                skipWhitespace()
                result.add(readValue(depth + 1))
                skipWhitespace()
                if (consume(']')) return result
                expect(',')
            }
        }

        private fun readString(): String {
            expect('"')
            val result = StringBuilder()
            while (index < source.length) {
                val character = source[index++]
                when (character) {
                    '"' -> return result.toString()
                    '\\' -> result.append(readEscape())
                    else -> {
                        require(character.code >= 0x20) { "Control character in JSON string" }
                        result.append(character)
                    }
                }
            }
            throw IllegalArgumentException("Unterminated JSON string")
        }

        private fun readEscape(): Char {
            require(index < source.length) { "Unterminated JSON escape" }
            return when (val escaped = source[index++]) {
                '"', '\\', '/' -> escaped
                'b' -> '\b'
                'f' -> '\u000c'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'u' -> readUnicodeEscape()
                else -> throw IllegalArgumentException("Invalid JSON escape")
            }
        }

        private fun readUnicodeEscape(): Char {
            require(index + 4 <= source.length) { "Truncated unicode escape" }
            val digits = source.substring(index, index + 4)
            index += 4
            return digits.toIntOrNull(16)?.toChar()
                ?: throw IllegalArgumentException("Invalid unicode escape")
        }

        private fun readNumber(): Any {
            val start = index
            if (source[index] == '-') index++
            val firstDigit = index
            while (index < source.length && source[index] in '0'..'9') index++
            require(index > firstDigit) { "Invalid JSON number" }
            var isDouble = false
            if (index < source.length && source[index] == '.') {
                isDouble = true
                index++
                val fractionStart = index
                while (index < source.length && source[index] in '0'..'9') index++
                require(index > fractionStart) { "Invalid JSON fraction" }
            }
            if (index < source.length && (source[index] == 'e' || source[index] == 'E')) {
                isDouble = true
                index++
                if (index < source.length && (source[index] == '+' || source[index] == '-')) index++
                val expStart = index
                while (index < source.length && source[index] in '0'..'9') index++
                require(index > expStart) { "Invalid JSON exponent" }
            }
            val text = source.substring(start, index)
            return if (isDouble) {
                text.toDoubleOrNull() ?: throw IllegalArgumentException("JSON number out of range")
            } else {
                text.toLongOrNull()
                    ?: text.toDoubleOrNull()
                    ?: throw IllegalArgumentException("JSON number out of range")
            }
        }

        private fun readLiteral(expected: String, value: Any?): Any? {
            require(source.regionMatches(index, expected, 0, expected.length)) { "Invalid JSON literal" }
            index += expected.length
            return value
        }

        private fun skipWhitespace() {
            while (index < source.length && source[index].isWhitespace()) index++
        }

        private fun expect(expected: Char) {
            require(index < source.length && source[index] == expected) { "Expected '$expected'" }
            index++
        }

        private fun consume(expected: Char): Boolean {
            if (index < source.length && source[index] == expected) {
                index++
                return true
            }
            return false
        }
    }
}
