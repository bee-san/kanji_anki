package dev.bee.kanjianki.core

import java.util.TreeMap
import java.util.TreeSet
import java.util.logging.Logger

class SimilarKanjiChoicePlanner {
    fun buildCandidates(
        inventory: List<RecordsImportModels.KanjiInventoryItem?>?,
        pairs: List<RecordsImportModels.SimilarKanjiPair?>?,
    ): List<RecordsImportModels.SimilarKanjiChoiceCard> {
        return buildCandidates(inventory, pairs, emptyMap())
    }

    fun buildCandidates(
        inventory: List<RecordsImportModels.KanjiInventoryItem?>?,
        pairs: List<RecordsImportModels.SimilarKanjiPair?>?,
        wrongPickCounts: Map<String, Map<String, Int>>?,
    ): List<RecordsImportModels.SimilarKanjiChoiceCard> {
        val inventoryByKanji = inventoryByKanji(inventory)
        if (inventoryByKanji.size < 2) {
            return emptyList()
        }

        val directNeighbors = directNeighbors(pairs, inventoryByKanji)
        val out = ArrayList<RecordsImportModels.SimilarKanjiChoiceCard>()
        for (target in inventoryByKanji.values) {
            val card = choiceCard(target, directNeighbors, wrongPickCounts?.get(target.kanji).orEmpty())
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
            targetWrongPicks: Map<String, Int>,
        ): RecordsImportModels.SimilarKanjiChoiceCard? {
            val meaning = target.primaryMeaning.trim()
            val neighbors = directNeighbors[target.kanji]
            if (meaning.isEmpty() || neighbors == null) {
                return null
            }
            val choiceList = if (targetWrongPicks.isEmpty()) {
                val choices = TreeSet<String>()
                choices.add(target.kanji)
                choices.addAll(neighbors)
                ArrayList(choices)
            } else {
                confusionOrderedChoices(target.kanji, neighbors, targetWrongPicks)
            }
            return RecordsImportModels.SimilarKanjiChoiceCard(
                target.kanji,
                meaning,
                choiceList,
                choiceSignature(choiceList),
            )
        }

        /**
         * Orders the neighbor distractors by how often the learner actually
         * confused them with the target (wrong-pick count desc, then the
         * existing deterministic lexicographic order) so the most-confused
         * neighbors survive the choice-limit truncation.
         */
        private fun confusionOrderedChoices(
            targetKanji: String,
            neighbors: Set<String>,
            targetWrongPicks: Map<String, Int>,
        ): ArrayList<String> {
            val orderedNeighbors = neighbors
                .filter { it != targetKanji }
                .sortedWith(compareByDescending<String> { targetWrongPicks[it] ?: 0 }.thenBy { it })
            val choiceList = ArrayList<String>()
            choiceList.add(targetKanji)
            for (neighbor in orderedNeighbors) {
                if (choiceList.size >= FALLBACK_CHOICE_LIMIT) {
                    break
                }
                choiceList.add(neighbor)
            }
            return choiceList
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

        @JvmField
        val LOGGER: Logger = Logger.getLogger(SimilarKanjiChoicePlanner::class.java.name)

        @JvmStatic
        fun choiceCardForSession(
            stored: RecordsImportModels.SimilarKanjiChoiceCard?,
            targetKanji: String,
            primaryMeaning: String?,
            pairs: List<RecordsImportModels.SimilarKanjiPair?>?,
        ): RecordsImportModels.SimilarKanjiChoiceCard = choiceCardForSession(
            stored,
            targetKanji,
            primaryMeaning,
            pairs,
            null,
        )

        @JvmStatic
        fun choiceCardForSession(
            stored: RecordsImportModels.SimilarKanjiChoiceCard?,
            targetKanji: String,
            primaryMeaning: String?,
            pairs: List<RecordsImportModels.SimilarKanjiPair?>?,
            preferredConfusion: String?,
        ): RecordsImportModels.SimilarKanjiChoiceCard {
            val base = if (stored != null) {
                stored
            } else {
                // Goal 69 safety net: with the strengthened hasSimilarKanji predicate
                // (both pair endpoints in the local inventory), a card that reaches
                // the similar_kanji rung should always have a pre-built choice state,
                // so this fallback should be unreachable in practice. Warn if it
                // fires so a divergence between predicate and planner is caught.
                LOGGER.warning {
                    "SimilarKanjiChoicePlanner.choiceCardForSession fell back to on-the-fly " +
                        "choices for '$targetKanji'; expected a pre-built similar-kanji choice state."
                }
                val choices = fallbackChoices(targetKanji, pairs)
                RecordsImportModels.SimilarKanjiChoiceCard(
                    targetKanji,
                    primaryMeaning ?: "",
                    choices,
                    choiceSignature(choices),
                )
            }
            return withPreferredConfusion(base, preferredConfusion)
        }

        /** Keeps the captured wrong glyph in the bounded choice set first. */
        @JvmStatic
        fun withPreferredConfusion(
            card: RecordsImportModels.SimilarKanjiChoiceCard,
            preferredConfusion: String?,
        ): RecordsImportModels.SimilarKanjiChoiceCard {
            val confusion = preferredConfusion?.trim().orEmpty()
            if (confusion.isEmpty() || confusion == card.targetKanji) {
                return card
            }
            val limit = card.choices.size.coerceAtLeast(2).coerceAtMost(FALLBACK_CHOICE_LIMIT)
            val ordered = LinkedHashSet<String>()
            ordered.add(card.targetKanji)
            ordered.add(confusion)
            for (choice in card.choices) {
                if (ordered.size >= limit) break
                val normalized = choice.trim()
                if (normalized.isNotEmpty()) ordered.add(normalized)
            }
            val choices = ordered.toList()
            return RecordsImportModels.SimilarKanjiChoiceCard(
                card.targetKanji,
                card.primaryMeaning,
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
