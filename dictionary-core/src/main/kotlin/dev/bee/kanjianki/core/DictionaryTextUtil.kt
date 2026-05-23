package dev.bee.kanjianki.core

import java.util.Locale
import java.util.regex.Pattern

internal object DictionaryTextUtil {
    private val MULTI_WHITESPACE: Pattern = Pattern.compile("\\s+")

    @JvmStatic
    fun stripHtml(value: String?): String {
        if (value.isNullOrEmpty()) {
            return ""
        }
        return MULTI_WHITESPACE.matcher(htmlEntities(stripHtmlTags(value))).replaceAll(" ").trim()
    }

    @JvmStatic
    fun isKanji(cp: Int): Boolean {
        return (cp in 0x3400..0x4DBF) ||
            (cp in 0x4E00..0x9FFF) ||
            (cp in 0xF900..0xFAFF) ||
            (cp in 0x20000..0x2EBEF)
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
        if ("rt" == tagName || "style" == tagName || "script" == tagName) {
            return closingTagEnd(value, fromIndex, tagName)
        }
        return -1
    }

    private fun appendSkipReplacement(out: StringBuilder, tagName: String) {
        if ("rt" != tagName) {
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

    private fun isTagNameChar(value: Char): Boolean = Character.isLetterOrDigit(value)

    private fun closingTagEnd(value: String, fromIndex: Int, tagName: String): Int {
        val closingTag = "</$tagName>"
        val maxStart = value.length - closingTag.length
        for (index in fromIndex..maxStart) {
            if (value.regionMatches(index, closingTag, 0, closingTag.length, true)) {
                return index + closingTag.length
            }
        }
        return -1
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

    private class TagBounds(val start: Int, val end: Int) {
        fun missingStart(): Boolean = start < 0

        fun missingEnd(): Boolean = end < 0

        fun empty(): Boolean = end == start + 1

        fun afterEnd(): Int = end + 1
    }
}
