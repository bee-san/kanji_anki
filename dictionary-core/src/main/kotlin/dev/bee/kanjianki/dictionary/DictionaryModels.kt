package dev.bee.kanjianki.dictionary

data class DictionaryCue(
    val meaning: String,
    val reading: String,
    val fromExpression: String,
    val source: String,
)

object DictionaryClueFormatter {
    fun answerLines(cue: DictionaryCue): List<String> = buildList {
        val meaning = cue.meaning.trim()
        if (meaning.isNotEmpty()) {
            add(meaning)
        }
        val reading = cue.reading.trim()
        if (reading.isNotEmpty()) {
            add("Reading: $reading")
        }
        val fromExpression = cue.fromExpression.trim()
        if (fromExpression.isNotEmpty()) {
            add("From: $fromExpression")
        }
    }
}
