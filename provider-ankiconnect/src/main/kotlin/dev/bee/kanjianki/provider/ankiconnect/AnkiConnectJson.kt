package dev.bee.kanjianki.provider.ankiconnect

/**
 * A tiny dependency-free JSON writer/reader scoped to AnkiConnect envelopes.
 * `:provider-ankiconnect` deliberately depends only on `:platform-contracts` and
 * `:sync-api` (the reviewed module boundary), so it cannot reach the JSON codec
 * in `:core`. This handles exactly the value shapes AnkiConnect uses — objects,
 * arrays, strings, numbers, booleans, and null — with a hard nesting/size bound
 * so a malicious response cannot exhaust memory. Anything malformed decodes to
 * `null`; callers treat that as a protocol error.
 *
 * Integers and fractions are modelled as *separate* types ([Json.Num] and
 * [Json.Frac]) rather than one numeric type, because every value Kani reads out
 * of AnkiConnect is an id or a counter and a fraction there is a protocol error,
 * not a roundable number. Keeping them apart means `as? Json.Num` stays a real
 * check: a fractional deck id still fails, while a fraction merely *present*
 * elsewhere in the same response no longer poisons the whole envelope.
 *
 * That distinction is load-bearing, and the reason it exists is a live finding:
 * rejecting fractions outright made the codec return `null` for real Anki's
 * `getDeckConfig`, whose options group carries `"delays": [1.0, 10.0]` and
 * `"ease4": 1.3`. The `null` surfaced as a protocol error, which
 * [AnkiConnectMissingKanjiWriter.ensureDeck] correctly reads as "cannot prove
 * this deck is ordinary" and reports as a deck collision — so the Missing Kanji
 * export could not complete against any real Anki, while passing against every
 * mock, because the mocks wrote the integers the codec accepted. Do not
 * re-tighten this to integers-only; assert on the value's *type* at the point of
 * use instead.
 */
object AnkiConnectJson {
    /** Maximum nesting depth accepted while parsing (defensive bound). */
    const val MAX_DEPTH = 64

    /** JSON value types this codec models. */
    sealed interface Json {
        data class Obj(val entries: Map<String, Json>) : Json
        data class Arr(val items: List<Json>) : Json
        data class Str(val value: String) : Json
        data class Num(val value: Long) : Json

        /**
         * A fractional or exponent-form number, kept verbatim as it was written.
         *
         * Nothing Kani reads is fractional, so this exists to be *skipped over*
         * rather than consumed: it lets a response that merely contains a
         * fraction somewhere decode, without letting a fraction stand in for an
         * id or a counter. The text is preserved instead of parsed to a double
         * so re-encoding round-trips and no precision decision is made here.
         */
        data class Frac(val text: String) : Json

        data class Bool(val value: Boolean) : Json
        data object Null : Json
    }

    fun obj(vararg entries: Pair<String, Json>): Json.Obj = Json.Obj(linkedMapOf(*entries))

    fun arr(items: List<Json>): Json.Arr = Json.Arr(items)

    fun str(value: String): Json.Str = Json.Str(value)

    fun num(value: Long): Json.Num = Json.Num(value)

    fun bool(value: Boolean): Json.Bool = Json.Bool(value)

    /** Serializes a [Json] value to compact JSON text. */
    fun encode(value: Json): String = buildString { encodeInto(value, this) }

    private fun encodeInto(value: Json, out: StringBuilder) {
        when (value) {
            is Json.Obj -> {
                out.append('{')
                var first = true
                for ((key, entry) in value.entries) {
                    if (!first) out.append(',')
                    first = false
                    encodeString(key, out)
                    out.append(':')
                    encodeInto(entry, out)
                }
                out.append('}')
            }
            is Json.Arr -> {
                out.append('[')
                value.items.forEachIndexed { index, item ->
                    if (index > 0) out.append(',')
                    encodeInto(item, out)
                }
                out.append(']')
            }
            is Json.Str -> encodeString(value.value, out)
            is Json.Num -> out.append(value.value)
            is Json.Frac -> out.append(value.text)
            is Json.Bool -> out.append(if (value.value) "true" else "false")
            Json.Null -> out.append("null")
        }
    }

    private fun encodeString(value: String, out: StringBuilder) {
        out.append('"')
        for (ch in value) {
            when (ch) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (ch < ' ') {
                    out.append("\\u")
                    out.append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    out.append(ch)
                }
            }
        }
        out.append('"')
    }

    /** Parses JSON text, or returns null if malformed or over the depth bound. */
    fun decode(text: String): Json? = try {
        val parser = Parser(text)
        parser.skipWhitespace()
        val value = parser.parseValue(0)
        parser.skipWhitespace()
        if (parser.atEnd()) value else null
    } catch (_: Exception) {
        null
    }

    private class Parser(private val text: String) {
        private var index = 0

        fun atEnd(): Boolean = index >= text.length

        fun skipWhitespace() {
            while (index < text.length && text[index].isWhitespace()) index++
        }

        fun parseValue(depth: Int): Json {
            if (depth > MAX_DEPTH) throw IllegalStateException("json too deep")
            skipWhitespace()
            return when (val ch = peek()) {
                '{' -> parseObject(depth)
                '[' -> parseArray(depth)
                '"' -> Json.Str(parseString())
                't', 'f' -> parseBool()
                'n' -> parseNull()
                else -> if (ch == '-' || ch in '0'..'9') parseNumber() else throw IllegalStateException("unexpected $ch")
            }
        }

        private fun parseObject(depth: Int): Json.Obj {
            expect('{')
            val entries = LinkedHashMap<String, Json>()
            skipWhitespace()
            if (peek() == '}') { index++; return Json.Obj(entries) }
            while (true) {
                skipWhitespace()
                val key = parseString()
                skipWhitespace()
                expect(':')
                entries[key] = parseValue(depth + 1)
                skipWhitespace()
                when (val ch = next()) {
                    ',' -> continue
                    '}' -> return Json.Obj(entries)
                    else -> throw IllegalStateException("unexpected $ch in object")
                }
            }
        }

        private fun parseArray(depth: Int): Json.Arr {
            expect('[')
            val items = ArrayList<Json>()
            skipWhitespace()
            if (peek() == ']') { index++; return Json.Arr(items) }
            while (true) {
                items.add(parseValue(depth + 1))
                skipWhitespace()
                when (val ch = next()) {
                    ',' -> continue
                    ']' -> return Json.Arr(items)
                    else -> throw IllegalStateException("unexpected $ch in array")
                }
            }
        }

        private fun parseString(): String {
            expect('"')
            val out = StringBuilder()
            while (true) {
                when (val ch = next()) {
                    '"' -> return out.toString()
                    '\\' -> when (val esc = next()) {
                        '"' -> out.append('"')
                        '\\' -> out.append('\\')
                        '/' -> out.append('/')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'u' -> {
                            val hex = text.substring(index, index + 4)
                            index += 4
                            out.append(hex.toInt(16).toChar())
                        }
                        else -> throw IllegalStateException("bad escape $esc")
                    }
                    else -> out.append(ch)
                }
            }
        }

        /**
         * Parses one number. A plain integer that fits a `Long` becomes
         * [Json.Num]; anything else — a fraction, an exponent, or an integer too
         * large to hold — becomes [Json.Frac], carrying its original text.
         *
         * Over-large integers land in [Json.Frac] for the same reason fractions
         * do: an id Kani cannot represent must fail where it is *read*, not turn
         * the surrounding response into a protocol error.
         */
        private fun parseNumber(): Json {
            val start = index
            if (peek() == '-') index++
            val digitsStart = index
            skipDigits()
            if (index == digitsStart) throw IllegalStateException("number with no digits")
            var integral = true
            if (index < text.length && text[index] == '.') {
                integral = false
                index++
                val fractionStart = index
                skipDigits()
                if (index == fractionStart) throw IllegalStateException("number with no fraction digits")
            }
            if (index < text.length && (text[index] == 'e' || text[index] == 'E')) {
                integral = false
                index++
                if (index < text.length && (text[index] == '+' || text[index] == '-')) index++
                val exponentStart = index
                skipDigits()
                if (index == exponentStart) throw IllegalStateException("number with no exponent digits")
            }
            val token = text.substring(start, index)
            val whole = if (integral) token.toLongOrNull() else null
            return if (whole == null) Json.Frac(token) else Json.Num(whole)
        }

        private fun skipDigits() {
            while (index < text.length && text[index] in '0'..'9') index++
        }

        private fun parseBool(): Json.Bool =
            if (text.startsWith("true", index)) {
                index += 4; Json.Bool(true)
            } else if (text.startsWith("false", index)) {
                index += 5; Json.Bool(false)
            } else {
                throw IllegalStateException("bad literal")
            }

        private fun parseNull(): Json {
            if (text.startsWith("null", index)) { index += 4; return Json.Null }
            throw IllegalStateException("bad literal")
        }

        private fun peek(): Char =
            if (index < text.length) text[index] else throw IllegalStateException("unexpected end")

        private fun next(): Char =
            if (index < text.length) text[index++] else throw IllegalStateException("unexpected end")

        private fun expect(expected: Char) {
            val ch = next()
            if (ch != expected) throw IllegalStateException("expected $expected but got $ch")
        }
    }
}
