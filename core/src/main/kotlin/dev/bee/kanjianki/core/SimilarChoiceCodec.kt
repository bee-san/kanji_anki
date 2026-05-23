package dev.bee.kanjianki.core

object SimilarChoiceCodec {
    private const val SEPARATOR = "\t"
    private val tabSeparator = Regex("\\t")

    @JvmStatic
    fun serializeChoices(choices: List<String>?): String {
        return choices.orEmpty().joinToString(SEPARATOR)
    }

    @JvmStatic
    fun deserializeChoices(encoded: String?): List<String> {
        if (encoded.isNullOrEmpty()) {
            return emptyList()
        }
        return tabSeparator.split(encoded)
            .filter { it.isNotEmpty() }
    }
}
