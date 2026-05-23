package dev.bee.kanjianki.core

import java.util.TreeMap
import java.util.TreeSet

class SimilarKanjiChoicePlanner {
    fun buildCandidates(
        inventory: List<RecordsImportModels.KanjiInventoryItem?>?,
        pairs: List<RecordsImportModels.SimilarKanjiPair?>?,
    ): List<RecordsImportModels.SimilarKanjiChoiceCard> {
        val inventoryByKanji = inventoryByKanji(inventory)
        if (inventoryByKanji.size < 2) {
            return emptyList()
        }

        val directNeighbors = directNeighbors(pairs, inventoryByKanji)
        val out = ArrayList<RecordsImportModels.SimilarKanjiChoiceCard>()
        for (target in inventoryByKanji.values) {
            val card = choiceCard(target, directNeighbors)
            if (card != null) {
                out.add(card)
            }
        }
        return out
    }

    fun evaluateSelection(
        card: RecordsImportModels.SimilarKanjiChoiceCard?,
        selectedKanji: String?,
    ): RecordsImportModels.SimilarKanjiChoiceResult {
        if (card == null) {
            return RecordsImportModels.SimilarKanjiChoiceResult(null, selectedKanji, false, emptyList())
        }
        val selected = selectedKanji?.trim() ?: ""
        val correct = card.targetKanji == selected
        if (correct) {
            return RecordsImportModels.SimilarKanjiChoiceResult(card, selected, true, emptyList())
        }
        val repairs = LinkedHashSet<String>()
        repairs.add(card.targetKanji)
        if (card.choices.contains(selected)) {
            repairs.add(selected)
        }
        return RecordsImportModels.SimilarKanjiChoiceResult(card, selected, false, ArrayList(repairs))
    }

    companion object {
        const val FALLBACK_CHOICE_LIMIT: Int = 4

        private fun inventoryByKanji(
            inventory: List<RecordsImportModels.KanjiInventoryItem?>?,
        ): Map<String, RecordsImportModels.KanjiInventoryItem> {
            val inventoryByKanji = TreeMap<String, RecordsImportModels.KanjiInventoryItem>()
            if (inventory != null) {
                for (item in inventory) {
                    if (item != null && item.kanji.isNotEmpty()) {
                        inventoryByKanji[item.kanji] = item
                    }
                }
            }
            return inventoryByKanji
        }

        private fun directNeighbors(
            pairs: List<RecordsImportModels.SimilarKanjiPair?>?,
            inventoryByKanji: Map<String, RecordsImportModels.KanjiInventoryItem>,
        ): Map<String, Set<String>> {
            val directNeighbors = TreeMap<String, MutableSet<String>>()
            if (pairs != null) {
                for (pair in pairs) {
                    if (validPair(pair, inventoryByKanji)) {
                        directNeighbors.computeIfAbsent(pair!!.kanjiA) { TreeSet() }.add(pair.kanjiB)
                        directNeighbors.computeIfAbsent(pair.kanjiB) { TreeSet() }.add(pair.kanjiA)
                    }
                }
            }
            return directNeighbors
        }

        private fun validPair(
            pair: RecordsImportModels.SimilarKanjiPair?,
            inventoryByKanji: Map<String, RecordsImportModels.KanjiInventoryItem>,
        ): Boolean {
            return pair != null &&
                pair.kanjiA.isNotEmpty() &&
                pair.kanjiB.isNotEmpty() &&
                pair.kanjiA != pair.kanjiB &&
                inventoryByKanji.containsKey(pair.kanjiA) &&
                inventoryByKanji.containsKey(pair.kanjiB)
        }

        private fun choiceCard(
            target: RecordsImportModels.KanjiInventoryItem,
            directNeighbors: Map<String, Set<String>>,
        ): RecordsImportModels.SimilarKanjiChoiceCard? {
            val meaning = target.primaryMeaning.trim()
            val neighbors = directNeighbors[target.kanji]
            if (meaning.isEmpty() || neighbors == null) {
                return null
            }
            val choices = TreeSet<String>()
            choices.add(target.kanji)
            choices.addAll(neighbors)
            val choiceList = ArrayList(choices)
            return RecordsImportModels.SimilarKanjiChoiceCard(
                target.kanji,
                meaning,
                choiceList,
                choiceSignature(choiceList),
            )
        }

        @JvmStatic
        fun fallbackChoices(
            targetKanji: String,
            pairs: List<RecordsImportModels.SimilarKanjiPair?>?,
        ): List<String> {
            val choices = LinkedHashSet<String>()
            choices.add(targetKanji)
            if (pairs != null) {
                for (pair in pairs) {
                    if (pair == null) {
                        continue
                    }
                    val other = if (pair.kanjiA == targetKanji) pair.kanjiB else pair.kanjiA
                    choices.add(other)
                    if (choices.size >= FALLBACK_CHOICE_LIMIT) {
                        break
                    }
                }
            }
            return ArrayList(choices)
        }

        @JvmStatic
        fun choiceCardForSession(
            stored: RecordsImportModels.SimilarKanjiChoiceCard?,
            targetKanji: String,
            primaryMeaning: String?,
            pairs: List<RecordsImportModels.SimilarKanjiPair?>?,
        ): RecordsImportModels.SimilarKanjiChoiceCard {
            if (stored != null) {
                return stored
            }
            val choices = fallbackChoices(targetKanji, pairs)
            return RecordsImportModels.SimilarKanjiChoiceCard(
                targetKanji,
                primaryMeaning ?: "",
                choices,
                choiceSignature(choices),
            )
        }

        @JvmStatic
        fun choiceSignature(choices: List<String?>?): String {
            val sorted = TreeSet<String>()
            if (choices != null) {
                for (choice in choices) {
                    if (choice != null && choice.trim().isNotEmpty()) {
                        sorted.add(choice.trim())
                    }
                }
            }
            return sorted.joinToString("\t")
        }
    }
}
