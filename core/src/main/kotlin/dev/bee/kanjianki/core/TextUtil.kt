package dev.bee.kanjianki.core

import java.text.Normalizer
import java.util.Locale
import java.util.regex.Pattern

object TextUtil {
    private val multiWhitespace: Pattern = Pattern.compile("\\s+")
    private val htmlEntityRegex: Pattern = Pattern.compile("[A-Za-z0-9_\\-]+")

    @JvmStatic
    fun normalizeJapanese(value: String?): String {
        if (value == null) {
            return ""
        }
        val noHtml = stripHtml(value)
        val normalized = Normalizer.normalize(noHtml, Normalizer.Form.NFKC)
        val withSpaces = normalized.replace('\u3000', ' ')
        return multiWhitespace.matcher(withSpaces).replaceAll(" ").trim()
    }

    @JvmStatic
    fun normalizeSingleKanji(value: String?): String {
        val normalized = normalizeJapanese(value)
        if (normalized.codePointCount(0, normalized.length) != 1) {
            return ""
        }
        return if (isKanji(normalized.codePointAt(0))) normalized else ""
    }

    @JvmStatic
    fun stripHtml(value: String?): String {
        if (value.isNullOrEmpty()) {
            return ""
        }
        return multiWhitespace.matcher(htmlEntities(stripHtmlTags(value))).replaceAll(" ").trim()
    }

    private fun stripHtmlTags(value: String): String {
        val out = StringBuilder(value.length)
        var index = 0
        var done = false
        while (!done && index < value.length) {
            val tag = nextTag(value, index)
            if (tag.missingStart()) {
                out.append(value, index, value.length)
                done = true
            } else {
                out.append(value, index, tag.start)
                if (tag.missingEnd()) {
                    out.append(value, tag.start, value.length)
                    done = true
                } else {
                    index = appendTagReplacement(value, out, tag)
                }
            }
        }
        return out.toString()
    }

    private fun nextTag(value: String, index: Int): TagBounds {
        val tagStart = value.indexOf('<', index)
        val tagEnd = if (tagStart < 0) -1 else value.indexOf('>', tagStart + 1)
        return TagBounds(tagStart, tagEnd)
    }

    private fun appendTagReplacement(value: String, out: StringBuilder, tag: TagBounds): Int {
        if (tag.empty()) {
            out.append(value, tag.start, tag.afterEnd())
            return tag.afterEnd()
        }
        val tagName = openingTagName(value, tag.start + 1, tag.end)
        val skippedContentEnd = skippableContentEnd(value, tag.afterEnd(), tagName)
        if (skippedContentEnd >= 0) {
            appendSkipReplacement(out, tagName)
            return skippedContentEnd
        }
        out.append(' ')
        return tag.afterEnd()
    }

    private fun skippableContentEnd(value: String, fromIndex: Int, tagName: String): Int {
        if (tagName == "rt" || tagName == "style" || tagName == "script") {
            return closingTagEnd(value, fromIndex, tagName)
        }
        return -1
    }

    private fun appendSkipReplacement(out: StringBuilder, tagName: String) {
        if (tagName != "rt") {
            out.append(' ')
        }
    }

    private fun openingTagName(value: String, index: Int, tagEnd: Int): String {
        val first = value[index]
        if (!Character.isLetter(first)) {
            return ""
        }
        var nameEnd = index + 1
        while (nameEnd < tagEnd && isTagNameChar(value[nameEnd])) {
            nameEnd++
        }
        return value.substring(index, nameEnd).lowercase(Locale.ROOT)
    }

    private fun isTagNameChar(value: Char): Boolean {
        return Character.isLetterOrDigit(value)
    }

    private fun closingTagEnd(value: String, fromIndex: Int, tagName: String): Int {
        val closingTag = "</$tagName>"
        val maxStart = value.length - closingTag.length
        var index = fromIndex
        while (index <= maxStart) {
            if (value.regionMatches(index, closingTag, 0, closingTag.length, ignoreCase = true)) {
                return index + closingTag.length
            }
            index++
        }
        return -1
    }

    @JvmStatic
    fun firstMeaningLine(value: String?): String {
        val stripped = stripHtml(value)
        if (stripped.isEmpty()) {
            return ""
        }
        val separators = arrayOf("|", ";", "\n", "。")
        var cut = stripped.length
        for (separator in separators) {
            val index = stripped.indexOf(separator)
            if (index >= 0) {
                cut = minOf(cut, index)
            }
        }
        var result = StudyCueFormatter.cleanMeaningText(stripped.substring(0, cut).trim())
        if (result.length > 96) {
            var truncationEnd = 93
            if (Character.isHighSurrogate(result[truncationEnd - 1]) &&
                Character.isLowSurrogate(result[truncationEnd])
            ) {
                truncationEnd--
            }
            result = result.substring(0, truncationEnd).trim() + "..."
        }
        return result
    }

    @JvmStatic
    fun extractKanji(value: String?): List<String> {
        val normalized = normalizeJapanese(value)
        val out = LinkedHashSet<String>()
        var index = 0
        while (index < normalized.length) {
            val cp = normalized.codePointAt(index)
            if (isKanji(cp)) {
                out.add(String(Character.toChars(cp)))
            }
            index += Character.charCount(cp)
        }
        return ArrayList(out)
    }

    @JvmStatic
    fun isKanji(cp: Int): Boolean {
        return (cp in 0x3400..0x4DBF) ||
            (cp in 0x4E00..0x9FFF) ||
            (cp in 0xF900..0xFAFF) ||
            (cp in 0x20000..0x2A6DF) ||
            (cp in 0x2A700..0x2B73F) ||
            (cp in 0x2B740..0x2B81F) ||
            (cp in 0x2B820..0x2CEAF) ||
            (cp in 0x2CEB0..0x2EBEF) ||
            (cp in 0x2EBF0..0x2EE5F) ||
            (cp in 0x2F800..0x2FA1F) ||
            (cp in 0x30000..0x3134F) ||
            (cp in 0x31350..0x323AF) ||
            (cp in 0x323B0..0x3347F)
    }

    @JvmStatic
    fun browserSearchForKanji(kanji: String?, settings: RecordsSyncModels.Settings): String {
        return String.format(
            Locale.ROOT,
            "note:%s %s:*%s*",
            ankiSearchToken(settings.modelName),
            ankiSearchToken(settings.expressionField),
            ankiSearchValue(kanji),
        )
    }

    private fun ankiSearchToken(value: String?): String {
        val safe = ankiSearchValue(value?.trim() ?: "")
        if (htmlEntityRegex.matcher(safe).matches()) {
            return safe
        }
        return "\"$safe\""
    }

    private fun ankiSearchValue(value: String?): String {
        return (value ?: "")
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
    }

    @JvmStatic
    fun jsonQuote(value: String?): String {
        if (value == null) {
            return "null"
        }
        val out = StringBuilder(value.length + 2)
        out.append('"')
        for (char in value) {
            appendJsonQuotedChar(out, char)
        }
        out.append('"')
        return out.toString()
    }

    private fun appendJsonQuotedChar(out: StringBuilder, char: Char) {
        when (char) {
            '"' -> out.append("\\\"")
            '\\' -> out.append("\\\\")
            '\n' -> out.append("\\n")
            '\r' -> out.append("\\r")
            '\t' -> out.append("\\t")
            else -> appendJsonDefaultChar(out, char)
        }
    }

    private fun appendJsonDefaultChar(out: StringBuilder, char: Char) {
        if (char.code < 0x20) {
            out.append(String.format(Locale.ROOT, "\\u%04x", char.code))
        } else {
            out.append(char)
        }
    }

    private fun htmlEntities(value: String): String {
        return value
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    private data class TagBounds(val start: Int, val end: Int) {
        fun missingStart(): Boolean {
            return start < 0
        }

        fun missingEnd(): Boolean {
            return end < 0
        }

        fun empty(): Boolean {
            return end == start + 1
        }

        fun afterEnd(): Int {
            return end + 1
        }
    }
}
