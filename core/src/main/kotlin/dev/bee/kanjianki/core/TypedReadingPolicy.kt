package dev.bee.kanjianki.core

import java.lang.Character.CONNECTOR_PUNCTUATION
import java.lang.Character.DASH_PUNCTUATION
import java.lang.Character.END_PUNCTUATION
import java.lang.Character.FINAL_QUOTE_PUNCTUATION
import java.lang.Character.INITIAL_QUOTE_PUNCTUATION
import java.lang.Character.LINE_SEPARATOR
import java.lang.Character.OTHER_PUNCTUATION
import java.lang.Character.PARAGRAPH_SEPARATOR
import java.lang.Character.SPACE_SEPARATOR
import java.lang.Character.START_PUNCTUATION
import java.text.Normalizer

/** Exact kana comparison for the full-word typed-reading repair. */
object TypedReadingPolicy {
    private const val PROLONGED_SOUND_MARK = 0x30FC

    /**
     * NFKC-normalizes, extracts Anki-style bracket furigana, converts ordinary
     * katakana to hiragana, and removes punctuation and whitespace.
     *
     * This intentionally does not transliterate romaji or apply fuzzy reading
     * equivalences. Small kana, sokuon, combining dakuten, and the prolonged
     * sound mark remain significant.
     */
    @JvmStatic
    fun normalize(value: String?): String {
        if (value.isNullOrEmpty()) {
            return ""
        }
        val compatibilityNormalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
        val readingText = extractBracketFurigana(compatibilityNormalized)
        return normalizeCodePoints(readingText)
    }

    @JvmStatic
    fun matches(typed: String?, expected: String?): Boolean {
        val normalizedTyped = normalize(typed)
        val normalizedExpected = normalize(expected)
        return normalizedTyped.isNotEmpty() && normalizedTyped == normalizedExpected
    }

    private fun extractBracketFurigana(value: String): String {
        val segments = furiganaSegments(value)
        if (segments.isEmpty()) {
            return value
        }
        val output = StringBuilder()
        var segmentIndex = 0
        var index = 0
        while (index < value.length) {
            val segment = segments.getOrNull(segmentIndex)
            if (segment != null && index == segment.start) {
                output.append(segment.reading)
                index = segment.endExclusive
                segmentIndex++
                continue
            }
            val codePoint = value.codePointAt(index)
            if (isKana(codePoint) || codePoint == PROLONGED_SOUND_MARK) {
                output.appendCodePoint(codePoint)
            }
            index += Character.charCount(codePoint)
        }
        return output.toString()
    }

    private fun furiganaSegments(value: String): List<FuriganaSegment> {
        val segments = ArrayList<FuriganaSegment>()
        var index = 0
        while (index < value.length) {
            val close = closeBracket(value[index])
            if (close == null) {
                index++
                continue
            }
            val closeIndex = value.indexOf(close, index + 1)
            if (closeIndex < 0) {
                index++
                continue
            }
            val candidate = value.substring(index + 1, closeIndex)
            if (isKanaReading(candidate)) {
                segments.add(FuriganaSegment(index, closeIndex + 1, candidate))
            }
            index = closeIndex + 1
        }
        return segments
    }

    private fun closeBracket(open: Char): Char? = when (open) {
        '[' -> ']'
        '【' -> '】'
        else -> null
    }

    private fun isKanaReading(value: String): Boolean {
        var hasKana = false
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            when {
                isKana(codePoint) -> hasKana = true
                codePoint == PROLONGED_SOUND_MARK -> Unit
                isIgnored(codePoint) -> Unit
                else -> return false
            }
            index += Character.charCount(codePoint)
        }
        return hasKana
    }

    private fun normalizeCodePoints(value: String): String {
        val result = StringBuilder()
        var index = 0
        while (index < value.length) {
            val codePoint = value.codePointAt(index)
            if (codePoint == PROLONGED_SOUND_MARK) {
                result.appendCodePoint(codePoint)
            } else if (!isIgnored(codePoint)) {
                result.append(katakanaToHiragana(codePoint))
            }
            index += Character.charCount(codePoint)
        }
        return Normalizer.normalize(result, Normalizer.Form.NFC)
    }

    private fun katakanaToHiragana(codePoint: Int): String = when (codePoint) {
        in 0x30A1..0x30F6, in 0x30FD..0x30FE -> String(Character.toChars(codePoint - 0x60))
        0x30F7 -> "わ\u3099"
        0x30F8 -> "ゐ\u3099"
        0x30F9 -> "ゑ\u3099"
        0x30FA -> "を\u3099"
        else -> String(Character.toChars(codePoint))
    }

    private fun isKana(codePoint: Int): Boolean {
        val script = Character.UnicodeScript.of(codePoint)
        return script == Character.UnicodeScript.HIRAGANA || script == Character.UnicodeScript.KATAKANA
    }

    private fun isIgnored(codePoint: Int): Boolean {
        if (Character.isWhitespace(codePoint)) {
            return true
        }
        return when (Character.getType(codePoint)) {
            CONNECTOR_PUNCTUATION.toInt(),
            DASH_PUNCTUATION.toInt(),
            START_PUNCTUATION.toInt(),
            END_PUNCTUATION.toInt(),
            INITIAL_QUOTE_PUNCTUATION.toInt(),
            FINAL_QUOTE_PUNCTUATION.toInt(),
            OTHER_PUNCTUATION.toInt(),
            SPACE_SEPARATOR.toInt(),
            LINE_SEPARATOR.toInt(),
            PARAGRAPH_SEPARATOR.toInt() -> true

            else -> false
        }
    }

    private data class FuriganaSegment(
        val start: Int,
        val endExclusive: Int,
        val reading: String,
    )
}
