package dev.bee.kanjianki.domain.importing

import java.text.Normalizer

internal object JapaneseText {
    fun normalize(value: String?): String {
        if (value.isNullOrEmpty()) {
            return ""
        }
        return Normalizer.normalize(stripHtml(value), Normalizer.Form.NFKC)
            .replace('\u3000', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun firstMeaningLine(value: String?): String {
        val stripped = stripHtml(value ?: "")
        if (stripped.isEmpty()) {
            return ""
        }
        val cut = listOf("|", ";", "\n", "。")
            .mapNotNull { separator -> stripped.indexOf(separator).takeIf { it >= 0 } }
            .minOrNull()
            ?: stripped.length
        val result = stripped.substring(0, cut).trim()
        return if (result.length > MAX_MEANING_LENGTH) {
            result.substring(0, MAX_MEANING_LENGTH - ELLIPSIS.length).trim() + ELLIPSIS
        } else {
            result
        }
    }

    fun extractKanji(value: String): List<String> {
        val out = linkedSetOf<String>()
        var index = 0
        val normalized = normalize(value)
        while (index < normalized.length) {
            val codePoint = normalized.codePointAt(index)
            if (isKanji(codePoint)) {
                out += String(Character.toChars(codePoint))
            }
            index += Character.charCount(codePoint)
        }
        return out.toList()
    }

    private fun stripHtml(value: String): String {
        if (value.isEmpty()) {
            return ""
        }
        val withoutTags = value.replace(Regex("<rt\\b[^>]*>.*?</rt>", RegexOption.IGNORE_CASE), "")
            .replace(Regex("<(script|style)\\b[^>]*>.*?</\\1>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)), " ")
            .replace(Regex("<[^>]+>"), " ")
        return htmlEntities(withoutTags).replace(Regex("\\s+"), " ").trim()
    }

    private fun htmlEntities(value: String): String = value
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&lt;", "<")
        .replace("&gt;", ">")

    private fun isKanji(codePoint: Int): Boolean =
        codePoint in 0x3400..0x4DBF ||
            codePoint in 0x4E00..0x9FFF ||
            codePoint in 0xF900..0xFAFF ||
            codePoint in 0x20000..0x2EBEF

    private const val MAX_MEANING_LENGTH = 96
    private const val ELLIPSIS = "..."
}
