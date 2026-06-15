package dev.bee.kanjianki.core

import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale

data class SimilarKanjiExplanation(
    val targetKanji: String,
    val confusedWith: List<String>,
    val sharedComponents: List<String>,
    val differingComponents: List<String>,
    val meaningClues: List<String>,
    val readingClues: List<String>,
    val failedSourceWords: List<String>,
    val watchThisPart: String,
    val confidence: ExplanationConfidence,
)

enum class ExplanationConfidence {
    LOW,
    MEDIUM,
    HIGH,
}

object SimilarKanjiExplanationPolicy {
    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun explain(
        targetKanji: String?,
        inventory: List<RecordsImportModels.KanjiInventoryItem?>?,
        pairs: List<RecordsImportModels.SimilarKanjiPair?>?,
        failedSourceWords: List<String?>? = null,
    ): SimilarKanjiExplanation {
        val normalizedTarget = TextUtil.normalizeSingleKanji(targetKanji)
        val inventoryByKanji = inventoryByKanji(inventory)
        val confusedWith = confusedWith(normalizedTarget, pairs)
        val targetItem = inventoryByKanji[normalizedTarget]
        val neighborItems = neighborItems(confusedWith, inventoryByKanji)
        val failedWords = normalizeWords(failedSourceWords)
        val meaningClues = clueLines(normalizedTarget, targetItem, neighborItems, { it.primaryMeaning })
        val readingClues = clueLines(normalizedTarget, targetItem, neighborItems, { it.readings })
        val watchThisPart = watchThisPart(normalizedTarget, confusedWith)

        return SimilarKanjiExplanation(
            targetKanji = normalizedTarget,
            confusedWith = confusedWith,
            sharedComponents = emptyList(),
            differingComponents = emptyList(),
            meaningClues = meaningClues,
            readingClues = readingClues,
            failedSourceWords = failedWords,
            watchThisPart = watchThisPart,
            confidence = confidenceFor(confusedWith, meaningClues, readingClues, failedWords),
        )
    }

    private fun inventoryByKanji(
        inventory: List<RecordsImportModels.KanjiInventoryItem?>?,
    ): Map<String, RecordsImportModels.KanjiInventoryItem> {
        val out = LinkedHashMap<String, RecordsImportModels.KanjiInventoryItem>()
        if (inventory != null) {
            for (item in inventory) {
                val normalizedKanji = TextUtil.normalizeSingleKanji(item?.kanji)
                if (normalizedKanji.isNotEmpty() && item != null) {
                    out[normalizedKanji] = item
                }
            }
        }
        return out
    }

    private fun confusedWith(
        targetKanji: String,
        pairs: List<RecordsImportModels.SimilarKanjiPair?>?,
    ): List<String> {
        if (targetKanji.isEmpty() || pairs.isNullOrEmpty()) {
            return emptyList()
        }
        val out = LinkedHashSet<String>()
        for (pair in pairs) {
            val left = TextUtil.normalizeSingleKanji(pair?.kanjiA)
            val right = TextUtil.normalizeSingleKanji(pair?.kanjiB)
            if (left.isEmpty() || right.isEmpty() || left == right) {
                continue
            }
            if (left == targetKanji) {
                out.add(right)
            } else if (right == targetKanji) {
                out.add(left)
            }
        }
        return ArrayList(out)
    }

    private fun neighborItems(
        confusedWith: List<String>,
        inventoryByKanji: Map<String, RecordsImportModels.KanjiInventoryItem>,
    ): List<RecordsImportModels.KanjiInventoryItem> {
        if (confusedWith.isEmpty()) {
            return emptyList()
        }
        val out = ArrayList<RecordsImportModels.KanjiInventoryItem>()
        for (kanji in confusedWith) {
            val item = inventoryByKanji[kanji]
            if (item != null) {
                out.add(item)
            }
        }
        return out
    }

    private fun clueLines(
        targetKanji: String,
        targetItem: RecordsImportModels.KanjiInventoryItem?,
        neighborItems: List<RecordsImportModels.KanjiInventoryItem>,
        value: (RecordsImportModels.KanjiInventoryItem) -> String,
    ): List<String> {
        val out = LinkedHashSet<String>()
        addClue(out, targetKanji, targetItem, value)
        for (item in neighborItems) {
            addClue(out, item.kanji, item, value)
        }
        return ArrayList(out)
    }

    private fun addClue(
        out: MutableSet<String>,
        kanji: String,
        item: RecordsImportModels.KanjiInventoryItem?,
        value: (RecordsImportModels.KanjiInventoryItem) -> String,
    ) {
        if (item == null || kanji.isEmpty()) {
            return
        }
        val clue = value(item).trim()
        if (clue.isNotEmpty()) {
            out.add("$kanji: $clue")
        }
    }

    private fun normalizeWords(sourceWords: List<String?>?): List<String> {
        if (sourceWords.isNullOrEmpty()) {
            return emptyList()
        }
        val out = LinkedHashSet<String>()
        for (word in sourceWords) {
            val normalized = TextUtil.normalizeJapanese(word)
            if (normalized.isNotEmpty()) {
                out.add(normalized)
            }
        }
        return ArrayList(out)
    }

    private fun watchThisPart(
        targetKanji: String,
        confusedWith: List<String>,
    ): String {
        if (isJapaneseLocale()) {
            return japaneseWatchThisPart(targetKanji, confusedWith)
        }
        if (targetKanji.isEmpty()) {
            return "Compare these kanji closely."
        }
        if (confusedWith.isEmpty()) {
            return "Look closely at this kanji."
        }
        return if (confusedWith.size == 1) {
            "Compare $targetKanji with ${confusedWith.first()}."
        } else {
            "Compare $targetKanji with ${joinWithAnd(confusedWith)}."
        }
    }

    private fun japaneseWatchThisPart(
        targetKanji: String,
        confusedWith: List<String>,
    ): String {
        if (targetKanji.isEmpty()) {
            return "この漢字をよく見比べましょう。"
        }
        if (confusedWith.isEmpty()) {
            return "この漢字をよく見ましょう。"
        }
        return if (confusedWith.size == 1) {
            "${targetKanji}と${confusedWith.first()}の違いを見比べましょう。"
        } else {
            "${targetKanji}と${joinWithJapaneseSeparator(confusedWith)}の違いを見比べましょう。"
        }
    }

    private fun joinWithJapaneseSeparator(values: List<String>): String = values.joinToString("・")

    private fun isJapaneseLocale(): Boolean = Locale.getDefault().language == JAPANESE_LANGUAGE

    private fun joinWithAnd(values: List<String>): String {
        return when (values.size) {
            0 -> ""
            1 -> values.first()
            2 -> "${values[0]} and ${values[1]}"
            else -> values.dropLast(1).joinToString(", ") + ", and ${values.last()}"
        }
    }

    private fun confidenceFor(
        confusedWith: List<String>,
        meaningClues: List<String>,
        readingClues: List<String>,
        failedSourceWords: List<String>,
    ): ExplanationConfidence {
        if (confusedWith.isEmpty()) {
            return ExplanationConfidence.LOW
        }
        if (failedSourceWords.isNotEmpty() && (meaningClues.isNotEmpty() || readingClues.isNotEmpty())) {
            return ExplanationConfidence.HIGH
        }
        if (failedSourceWords.isNotEmpty() || meaningClues.isNotEmpty() || readingClues.isNotEmpty()) {
            return ExplanationConfidence.MEDIUM
        }
        return ExplanationConfidence.LOW
    }
}
