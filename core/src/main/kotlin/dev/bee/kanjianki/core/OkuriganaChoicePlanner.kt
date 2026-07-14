package dev.bee.kanjianki.core

/**
 * Builds the okurigana-choice variant of the `kanji_reading` task (D-P9).
 * Given a target kanji and its kun readings with the KANJIDIC `.` okurigana
 * boundary (e.g. 教 → `おし.える` / `おそ.わる`), when the kanji has ≥ 2
 * distinct okurigana forms, outputs a choice card (prompt = kanji + blanked
 * ending, choices = the distinct kanji+okurigana forms). Null when fewer
 * than 2 okurigana forms exist.
 */
object OkuriganaChoicePlanner {
    const val MIN_CHOICE_COUNT: Int = 2
    const val MAX_CHOICE_COUNT: Int = 4

    @JvmStatic
    fun build(
        targetKanji: String?,
        usageWord: String?,
        usageReading: String?,
        kunReadingsWithDot: List<String>?,
    ): OkuriganaChoiceCard? {
        val kanji = targetKanji?.trim().orEmpty()
        if (kanji.isEmpty()) return null
        val word = usageWord?.trim().orEmpty()
        if (word.isEmpty()) return null
        val reading = usageReading?.trim().orEmpty()
        if (reading.isEmpty()) return null

        val okuriganaForms = kunReadingsWithDot.orEmpty()
            .mapNotNull { parseDotReading(it) }
            .filter { it.okurigana.isNotEmpty() }
            .map { kanji + it.okurigana }
            .distinct()
            .take(MAX_CHOICE_COUNT)

        if (okuriganaForms.size < MIN_CHOICE_COUNT) return null

        val correctForm = findCorrectForm(kanji, word, okuriganaForms)
            ?: return null

        val prompt = "$kanji＿＿"
        return OkuriganaChoiceCard(
            prompt = prompt,
            correctAnswer = correctForm,
            choices = okuriganaForms,
            targetKanji = kanji,
        )
    }

    private fun findCorrectForm(kanji: String, word: String, forms: List<String>): String? {
        for (form in forms) {
            if (word.startsWith(form) || form == word) {
                return form
            }
        }
        if (word.startsWith(kanji) && word.length > kanji.length) {
            val ending = word.substring(kanji.length)
            val withEnding = "$kanji$ending"
            for (form in forms) {
                if (ending.startsWith(form.substring(kanji.length))) {
                    return form
                }
            }
        }
        return null
    }

    private fun parseDotReading(reading: String): ParsedKunReading? {
        val dotIndex = reading.indexOf('.')
        if (dotIndex < 0) return null
        val stem = reading.substring(0, dotIndex)
        val okurigana = reading.substring(dotIndex + 1)
        if (stem.isEmpty() || okurigana.isEmpty()) return null
        return ParsedKunReading(stem, okurigana)
    }

    private data class ParsedKunReading(
        val stem: String,
        val okurigana: String,
    )

    class OkuriganaChoiceCard(
        @JvmField val prompt: String,
        @JvmField val correctAnswer: String,
        @JvmField val choices: List<String>,
        @JvmField val targetKanji: String,
    )
}
