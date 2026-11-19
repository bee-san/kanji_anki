package dev.bee.kanjianki.kani.core

import dev.bee.kanjianki.core.StudyCueFormatter

/** Cleans Anki field text and finds kanji in it. */
object AnkiText {
    private val ENTITIES = listOf("&nbsp;" to " ", "&amp;" to "&", "&lt;" to "<", "&gt;" to ">", "&quot;" to "\"", "&#39;" to "'")
    private val BLOCK_TAGS = listOf("br", "div", "p", "li", "ul", "ol", "tr", "td")

    /** Dictionary tags Yomitan puts before a gloss, e.g. `(Daijirin4 Plain) `. */
    private val LEADING_LABELS = Regex("^(\\([^)]*\\)\\s*|[^\\p{L}\\p{N}(]+)+")

    /**
     * Field HTML → plain text in one pass (no regex: the scan runs this on every note).
     * Drops tags and `[sound:…]`, decodes common entities, collapses whitespace. Block tags
     * become spaces; a tag cut off by truncation is dropped.
     */
    fun plain(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        val out = StringBuilder(raw.length)
        var pendingSpace = false
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            when {
                c == '<' -> {
                    val end = raw.indexOf('>', i)
                    if (end < 0) break
                    if (isBlockTag(raw, i + 1)) pendingSpace = true
                    i = end + 1
                    continue
                }
                c == '[' && raw.startsWith("[sound:", i) -> {
                    val end = raw.indexOf(']', i)
                    if (end < 0) break
                    i = end + 1
                    continue
                }
                c.isWhitespace() -> pendingSpace = true
                else -> {
                    if (pendingSpace && out.isNotEmpty()) out.append(' ')
                    pendingSpace = false
                    val entity = if (c == '&') ENTITIES.firstOrNull { raw.startsWith(it.first, i) } else null
                    if (entity != null) {
                        out.append(entity.second)
                        i += entity.first.length
                        continue
                    }
                    out.append(c)
                }
            }
            i++
        }
        return out.toString().trim()
    }

    private fun isBlockTag(raw: String, start: Int): Boolean {
        var i = start
        if (i < raw.length && raw[i] == '/') i++
        return BLOCK_TAGS.any { tag ->
            raw.regionMatches(i, tag, 0, tag.length, ignoreCase = true) &&
                (i + tag.length >= raw.length || !raw[i + tag.length].isLetterOrDigit())
        }
    }

    /** Removes `[...]` furigana runs. */
    private fun withoutBrackets(s: String): String {
        if ('[' !in s) return s
        val out = StringBuilder(s.length)
        var depth = 0
        for (c in s) {
            when {
                c == '[' -> depth++
                c == ']' && depth > 0 -> depth--
                depth == 0 -> out.append(c)
            }
        }
        return out.toString()
    }

    /** `待[ま]つ` → `待つ`. Anki furigana puts a space before each base; drop those too. */
    fun expression(raw: String?): String {
        return withoutBrackets(plain(raw)).replace(" ", "").trim()
    }

    /** `待[ま]つ` → `まつ`; plain kana passes through. */
    fun reading(raw: String?): String {
        val s = plain(raw)
        if (!s.contains('[')) return s.replace(" ", "")
        val out = StringBuilder()
        // Anki furigana: each "base[reading]" chunk replaces the kanji run before the bracket.
        val chunks = s.split(' ')
        for (chunk in chunks) {
            val open = chunk.indexOf('[')
            val close = chunk.indexOf(']')
            if (open < 0 || close < open) {
                out.append(chunk)
                continue
            }
            val base = chunk.substring(0, open)
            val kanjiRunStart = base.indexOfFirst { isKanji(it) }.let { if (it < 0) base.length else it }
            out.append(base, 0, kanjiRunStart)
            out.append(chunk, open + 1, close)
            out.append(chunk.substring(close + 1))
        }
        return out.toString()
    }

    /** A definition field cleaned of dictionary labels and markup (Yomitan, JMdict, Jitendex). */
    fun meaning(raw: String?): String = LEADING_LABELS.replace(StudyCueFormatter.cleanMeaningText(raw).trim(), "").trim()

    /** First short gloss: `to wait; to await` → `wait`. */
    fun shortMeaning(raw: String?): String {
        val s = meaning(raw)
        if (s.isEmpty()) return ""
        val first = s.split(';', ',', '/', '、').first().trim()
        return first.removePrefix("to ").trim().take(40)
    }

    fun isKanji(c: Char): Boolean = c in '一'..'鿿' || c in '㐀'..'䶿' || c in '豈'..'﫿'

    fun kanjiIn(text: String): List<String> = text.filter(::isKanji).map { it.toString() }.distinct()
}
