// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.bee.rwkv

/** A small recursive-descent JSON reader for the test fixtures (no dependencies in this module). */
internal class TestJson private constructor(private val text: String) {
    private var at = 0

    private fun value(): Any? {
        skipWhitespace()
        return when (val c = text[at]) {
            '{' -> obj()
            '[' -> array()
            '"' -> string()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            else -> if (c == '-' || c.isDigit()) number() else error("unexpected '$c' at $at")
        }
    }

    private fun obj(): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        at++
        skipWhitespace()
        if (text[at] == '}') {
            at++
            return out
        }
        while (true) {
            skipWhitespace()
            val key = string()
            skipWhitespace()
            expect(':')
            out[key] = value()
            skipWhitespace()
            if (text[at] == ',') {
                at++
                continue
            }
            expect('}')
            return out
        }
    }

    private fun array(): List<Any?> {
        val out = ArrayList<Any?>()
        at++
        skipWhitespace()
        if (text[at] == ']') {
            at++
            return out
        }
        while (true) {
            out += value()
            skipWhitespace()
            if (text[at] == ',') {
                at++
                continue
            }
            expect(']')
            return out
        }
    }

    private fun string(): String {
        expect('"')
        val out = StringBuilder()
        while (true) {
            val c = text[at++]
            when (c) {
                '"' -> return out.toString()
                '\\' -> {
                    when (val escaped = text[at++]) {
                        'n' -> out.append('\n')
                        't' -> out.append('\t')
                        'r' -> out.append('\r')
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000c')
                        'u' -> {
                            out.append(text.substring(at, at + 4).toInt(16).toChar())
                            at += 4
                        }
                        else -> out.append(escaped)
                    }
                }
                else -> out.append(c)
            }
        }
    }

    private fun number(): Double {
        val start = at
        while (at < text.length && (text[at].isDigit() || text[at] in "+-.eE")) {
            at++
        }
        return text.substring(start, at).toDouble()
    }

    private fun literal(word: String, result: Any?): Any? {
        check(text.startsWith(word, at)) { "expected $word at $at" }
        at += word.length
        return result
    }

    private fun expect(c: Char) {
        check(text[at] == c) { "expected '$c' at $at, found '${text[at]}'" }
        at++
    }

    private fun skipWhitespace() {
        while (at < text.length && text[at].isWhitespace()) {
            at++
        }
    }

    companion object {
        fun parse(text: String): Any? = TestJson(text).value()
    }
}

@Suppress("UNCHECKED_CAST")
internal fun Any?.asObject(): Map<String, Any?> = this as Map<String, Any?>

@Suppress("UNCHECKED_CAST")
internal fun Any?.asList(): List<Any?> = this as List<Any?>

internal fun Any?.asFloats(): FloatArray = asList().map { (it as Double).toFloat() }.toFloatArray()
