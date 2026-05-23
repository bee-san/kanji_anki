package dev.bee.kanjianki.core

object SimilarKanjiStorageKeys {
    private const val PAIR_DELIMITER = "\u0000"
    private const val CHOICE_DELIMITER = "\u0001"

    @JvmStatic
    fun canonicalPair(first: String?, second: String?): Array<String> {
        val safeFirst = first!!
        val safeSecond = second!!
        if (safeFirst <= safeSecond) {
            return arrayOf(safeFirst, safeSecond)
        }
        return arrayOf(safeSecond, safeFirst)
    }

    @JvmStatic
    fun pairKey(first: String?, second: String?, source: String?): String {
        return first.toString() + PAIR_DELIMITER + second + PAIR_DELIMITER + source
    }

    @JvmStatic
    fun choiceKey(targetKanji: String?, choiceSignature: String?): String {
        return targetKanji.toString() + CHOICE_DELIMITER + (choiceSignature ?: "")
    }

    @JvmStatic
    fun splitChoiceKey(key: String?): Array<String> {
        if (key == null) {
            return emptyArray()
        }
        return key.split(CHOICE_DELIMITER, limit = 2).toTypedArray()
    }
}
