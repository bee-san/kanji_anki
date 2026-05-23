package dev.bee.kanjianki.core

import java.util.Locale
import java.util.regex.Pattern

class StudyCueFormatter private constructor() {
    companion object {
        private val DATE_METADATA_PATTERN: Pattern = Pattern.compile("\\[\\d{4}-\\d{2}-\\d{2}\\]")
        private val MEANING_LABEL_PATTERN: Pattern = Pattern.compile("(?i)^\\s*meaning\\s*:\\s*")
        private val JM_DICT_PATTERN: Pattern = Pattern.compile("(?i)\\bJMdict(?:\\s*\\[[^\\]]*\\])?\\s*")
        private val JITENDEX_PATTERN: Pattern = Pattern.compile("(?i)(?<!\\()\\bJitendex(?:\\.org)?\\s*")
        private val NUMBERED_PREFIX_PATTERN: Pattern = Pattern.compile("^\\d+\\.\\s*")
        private val GODAN_PATTERN: Pattern = Pattern.compile("(?i)^(5-dan|godan)\\s+(intransitive|transitive)\\s+")
        private val ADJECTIVE_VERB_PATTERN: Pattern = Pattern.compile("(?i)^(ichidan|suru|na-adjective|i-adjective|no-adjective)\\s+")
        private val TRAILING_JAPANESE_EXAMPLE_PATTERN: Pattern = Pattern.compile("(?i)(?<=[a-z])\\s+[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}].*$")
        private val LEADING_METADATA_SEPARATOR_PATTERN: Pattern = Pattern.compile("\\s+")
        private val NON_ALPHA_NUMERIC_PATTERN: Pattern = Pattern.compile("[^a-z0-9-]")
        private val MULTI_WHITESPACE_PATTERN: Pattern = Pattern.compile("\\s+")

        private val LEADING_METADATA: Set<String> = hashSetOf(
            "noun",
            "nouns",
            "suru",
            "intransitive",
            "transitive",
            "ichidan",
            "godan",
            "adverb",
            "adverbial",
            "taru",
            "to-adverb",
            "auxiliary",
            "counter",
            "expression",
            "interjection",
            "prefix",
            "suffix",
            "pronoun",
            "conjunction",
            "particle",
        )

        @JvmStatic
        fun answerLines(cue: StudyCue?): List<String> {
            val lines = ArrayList<String>()
            val safe = cue ?: StudyCue("", "", "", "")
            if (safe.meaning.isNotEmpty()) {
                lines.add(safe.meaning)
            }
            if (safe.reading.isNotEmpty()) {
                lines.add("Reading: " + hiraganaReading(safe.reading))
            }
            if (safe.fromExpression.isNotEmpty()) {
                lines.add("From: " + safe.fromExpression)
            }
            if (lines.isEmpty()) {
                lines.add("Collection clue")
            }
            return lines
        }

        @JvmStatic
        fun displayGlosses(glosses: List<String?>?, maxGlosses: Int): String {
            val cleaned = ArrayList<String>()
            if (glosses != null) {
                for (gloss in glosses) {
                    val value = cleanInline(gloss)
                    if (value.isNotEmpty() && !cleaned.contains(value)) {
                        cleaned.add(value)
                    }
                    if (cleaned.size >= maxOf(1, maxGlosses)) {
                        break
                    }
                }
            }
            if (cleaned.isEmpty()) {
                return ""
            }
            return capitalize(cleaned.joinToString(", "))
        }

        @JvmStatic
        fun cleanFallbackMeaning(raw: String?, fallback: String?, maxChars: Int): String {
            var value = cleanMeaningText(raw)
            if (value.isEmpty()) {
                value = cleanMeaningText(fallback)
            }
            if (value.isEmpty()) {
                value = "Collection clue"
            }
            return compact(capitalize(value), maxChars)
        }

        @JvmStatic
        fun cleanCollectionMeaning(raw: String?, maxChars: Int): String {
            return compact(cleanMeaningText(raw), maxChars)
        }

        @JvmStatic
        fun cleanMeaningText(raw: String?): String {
            var value = DictionaryTextUtil.stripHtml(raw)
            value = MEANING_LABEL_PATTERN.matcher(value).replaceAll(" ")
            value = DATE_METADATA_PATTERN.matcher(value).replaceAll(" ")
            value = JM_DICT_PATTERN.matcher(value).replaceAll(" ")
            value = JITENDEX_PATTERN.matcher(value).replaceAll(" ")
            value = MEANING_LABEL_PATTERN.matcher(value).replaceAll(" ")
            value = value.replace('\n', ' ').replace('\r', ' ').trim()
            var changed = true
            while (changed && value.startsWith("(")) {
                changed = false
                val end = value.indexOf(')')
                if (end > 0 && end < 180) {
                    val metadata = value.substring(1, end).lowercase(Locale.ROOT)
                    if (metadata.contains("jitendex") ||
                        metadata.contains("★") ||
                        metadata.contains("priority") ||
                        metadata.contains("form") ||
                        metadata.contains("noun") ||
                        metadata.contains("adjective") ||
                        metadata.contains("verb") ||
                        metadata.contains("transitive") ||
                        metadata.contains("suru")
                    ) {
                        value = value.substring(end + 1).trim()
                        changed = true
                    }
                }
            }
            value = NUMBERED_PREFIX_PATTERN.matcher(value).replaceAll("")
            value = GODAN_PATTERN.matcher(value).replaceAll("")
            value = ADJECTIVE_VERB_PATTERN.matcher(value).replaceAll("")
            value = stripLeadingMetadataWords(value)
            value = TRAILING_JAPANESE_EXAMPLE_PATTERN.matcher(value).replaceAll("")
            return cleanInline(value)
        }

        @JvmStatic
        fun hiraganaReading(reading: String?): String {
            if (reading.isNullOrEmpty()) {
                return ""
            }
            val converted = StringBuilder(reading.length)
            for (index in reading.indices) {
                val c = reading[index]
                if (c in 'ァ'..'ヶ') {
                    converted.append((c.code - 0x60).toChar())
                } else {
                    converted.append(c)
                }
            }
            return converted.toString()
        }

        @JvmStatic
        fun compact(value: String?, maxChars: Int): String {
            if (value == null) {
                return ""
            }
            if (value.length <= maxChars) {
                return value
            }
            var cut = value.lastIndexOf(' ', maxChars - 3)
            if (cut < 32) {
                cut = maxChars - 3
            }
            return value.substring(0, cut).trim() + "..."
        }

        private fun stripLeadingMetadataWords(value: String): String {
            val words = LEADING_METADATA_SEPARATOR_PATTERN.split(value.trim())
            var firstMeaningWord = 0
            while (firstMeaningWord < words.size && isLeadingMetadataWord(words[firstMeaningWord])) {
                firstMeaningWord++
            }
            if (firstMeaningWord == 0) {
                return value
            }
            return words.copyOfRange(firstMeaningWord, words.size).joinToString(" ")
        }

        private fun isLeadingMetadataWord(word: String): Boolean {
            val normalized = NON_ALPHA_NUMERIC_PATTERN.matcher(word.lowercase(Locale.ROOT)).replaceAll("")
            return normalized == "5-dan" ||
                normalized == "na-adjective" ||
                normalized == "i-adjective" ||
                normalized == "no-adjective" ||
                LEADING_METADATA.contains(normalized)
        }

        private fun cleanInline(value: String?): String {
            if (value == null) {
                return ""
            }
            val normalized = value
                .replace('\t', ' ')
                .replace('\n', ' ')
                .replace('\r', ' ')
            return MULTI_WHITESPACE_PATTERN.matcher(normalized).replaceAll(" ").trim()
        }

        private fun capitalize(value: String): String {
            val first = value[0]
            if (!Character.isLowerCase(first)) {
                return value
            }
            return Character.toUpperCase(first) + value.substring(1)
        }
    }
}
