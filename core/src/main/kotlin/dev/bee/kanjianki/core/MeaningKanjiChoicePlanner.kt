package dev.bee.kanjianki.core

import java.util.Locale
import java.security.SecureRandom
import java.util.Random

class MeaningKanjiChoicePlanner {
    fun buildChoiceCard(
        target: RecordsImportModels.DashboardRow?,
        rows: List<RecordsImportModels.DashboardRow?>?,
        inventory: List<RecordsImportModels.KanjiInventoryItem?>?,
        random: Random?,
    ): RecordsImportModels.MeaningKanjiChoiceCard? {
        return buildChoiceCard(target, rows, inventory, random, emptyMap(), null)
    }

    fun buildChoiceCard(
        target: RecordsImportModels.DashboardRow?,
        rows: List<RecordsImportModels.DashboardRow?>?,
        inventory: List<RecordsImportModels.KanjiInventoryItem?>?,
        random: Random?,
        wrongPickCounts: Map<String, Map<String, Int>>?,
    ): RecordsImportModels.MeaningKanjiChoiceCard? {
        return buildChoiceCard(target, rows, inventory, random, wrongPickCounts, null)
    }

    /**
     * Builds the meaning_kanji choice card. When [dictionaryLookup] is supplied the
     * card is dictionary-gated: because the question now asks for the target kanji's
     * KANJIDIC gloss, a target with no dictionary gloss yields `null` so the UI can
     * degrade to a plain recognition flashcard. Decoys whose own dictionary-first
     * displayed meaning matches the target's displayed gloss are also dropped so the
     * card stays answerable. Passing `null` (the legacy overloads) leaves the planner
     * ungated and preserves the word-meaning behavior for callers without a lookup.
     */
    fun buildChoiceCard(
        target: RecordsImportModels.DashboardRow?,
        rows: List<RecordsImportModels.DashboardRow?>?,
        inventory: List<RecordsImportModels.KanjiInventoryItem?>?,
        random: Random?,
        wrongPickCounts: Map<String, Map<String, Int>>?,
        dictionaryLookup: DictionaryLookup?,
    ): RecordsImportModels.MeaningKanjiChoiceCard? {
        if (target?.kanji == null || target.kanji.javaTrim().isEmpty()) {
            return null
        }
        val targetKanji = target.kanji.javaTrim()
        val meaning = target.primaryMeaning.javaTrim()
        if (meaning.isEmpty()) {
            return null
        }
        // Hard gate: the question asks for the KANJIDIC gloss, so a kanji missing from
        // the dictionary has no answerable clue. Refuse the card (ungated when null).
        val targetGloss = if (dictionaryLookup == null) null else displayedGloss(dictionaryLookup, targetKanji)
        if (dictionaryLookup != null && targetGloss.isNullOrEmpty()) {
            return null
        }
        val normalizedTargetGloss = normalizeMeaning(targetGloss)
        val normalizedTargetMeaning = normalizeMeaning(meaning)
        val eligible = eligibleKanji(rows, inventory)
        eligible[targetKanji] = normalizedTargetMeaning
        eligible.remove("")
        if (eligible.size < CHOICE_COUNT) {
            return null
        }
        val rng = random ?: SecureRandom()
        val decoys = ArrayList(eligible.keys)
        decoys.remove(targetKanji)
        decoys.removeIf { decoy ->
            eligible.getOrDefault(decoy, "").isNotEmpty() &&
                eligible.getOrDefault(decoy, "") == normalizedTargetMeaning
        }
        // Dictionary-aware collision guard: once the displayed clue is the KANJIDIC
        // gloss, drop any decoy whose own dictionary-first displayed meaning (gloss if
        // present, else its word meaning) matches the target's gloss. Applied before
        // confused-seed selection so both seeds and random fill are filtered.
        if (dictionaryLookup != null && normalizedTargetGloss.isNotEmpty()) {
            decoys.removeIf { decoy ->
                displayedMeaningCollides(dictionaryLookup, decoy, eligible, normalizedTargetGloss)
            }
        }
        // Seed the decoy list with the target's confused kanji (wrong-pick
        // count desc) before the random fill; with no confusion history this
        // is a no-op and behavior matches the unweighted path exactly.
        val confusedCounts = wrongPickCounts?.get(targetKanji).orEmpty()
        val confusedSeeds = decoys
            .filter { (confusedCounts[it] ?: 0) > 0 }
            .sortedWith(compareByDescending<String> { confusedCounts[it] ?: 0 }.thenBy { it })
        decoys.shuffle(rng)

        val choices = ArrayList<String>()
        choices.add(targetKanji)
        for (seed in confusedSeeds) {
            if (choices.size >= CHOICE_COUNT) {
                break
            }
            choices.add(seed)
        }
        for (decoy in decoys) {
            if (choices.size >= CHOICE_COUNT) {
                break
            }
            if (!choices.contains(decoy)) {
                choices.add(decoy)
            }
        }
        if (choices.size < CHOICE_COUNT) {
            return null
        }
        choices.shuffle(rng)
        return RecordsImportModels.MeaningKanjiChoiceCard(targetKanji, meaning, target.reading, choices)
    }

    private fun eligibleKanji(
        rows: List<RecordsImportModels.DashboardRow?>?,
        inventory: List<RecordsImportModels.KanjiInventoryItem?>?,
    ): MutableMap<String, String> {
        val out = LinkedHashMap<String, String>()
        addEligibleRows(out, rows)
        addEligibleInventory(out, inventory)
        return out
    }

    private fun addEligibleRows(
        out: MutableMap<String, String>,
        rows: List<RecordsImportModels.DashboardRow?>?,
    ) {
        if (rows != null) {
            for (row in rows) {
                if (row?.kanji != null && row.kanji.javaTrim().isNotEmpty()) {
                    out[row.kanji.javaTrim()] = normalizeMeaning(row.primaryMeaning)
                }
            }
        }
    }

    private fun addEligibleInventory(
        out: MutableMap<String, String>,
        inventory: List<RecordsImportModels.KanjiInventoryItem?>?,
    ) {
        if (inventory != null) {
            for (item in inventory) {
                val kanji = if (item?.kanji == null) "" else item.kanji.javaTrim()
                if (kanji.isNotEmpty()) {
                    out[kanji] = normalizeMeaning(item?.primaryMeaning)
                }
            }
        }
    }

    private fun normalizeMeaning(meaning: String?): String {
        return meaning?.javaTrim()?.replace("\\s+".toRegex(), " ")?.lowercase(Locale.ROOT) ?: ""
    }

    /** The KANJIDIC gloss shown for [kanji] (matching the study copy), or "" when absent. */
    private fun displayedGloss(dictionaryLookup: DictionaryLookup, kanji: String): String {
        val entry = dictionaryLookup.lookupKanji(kanji) ?: return ""
        return StudyCueFormatter.displayGlosses(entry.meanings, DISPLAY_GLOSS_COUNT)
    }

    /**
     * True when [decoy]'s dictionary-first displayed meaning (its gloss if present,
     * else its word meaning) normalizes equal to the target's displayed gloss, which
     * would make the choice card unanswerable.
     */
    private fun displayedMeaningCollides(
        dictionaryLookup: DictionaryLookup,
        decoy: String,
        eligible: Map<String, String>,
        normalizedTargetGloss: String,
    ): Boolean {
        val decoyGloss = displayedGloss(dictionaryLookup, decoy)
        val decoyDisplayed = if (decoyGloss.isNotEmpty()) {
            normalizeMeaning(decoyGloss)
        } else {
            eligible.getOrDefault(decoy, "")
        }
        return decoyDisplayed.isNotEmpty() && decoyDisplayed == normalizedTargetGloss
    }

    private fun String.javaTrim(): String {
        return trim { it <= ' ' }
    }

    companion object {
        private const val CHOICE_COUNT = 4
        private const val DISPLAY_GLOSS_COUNT = 2
    }
}
