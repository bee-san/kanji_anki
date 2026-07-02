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
        return buildChoiceCard(target, rows, inventory, random, emptyMap())
    }

    fun buildChoiceCard(
        target: RecordsImportModels.DashboardRow?,
        rows: List<RecordsImportModels.DashboardRow?>?,
        inventory: List<RecordsImportModels.KanjiInventoryItem?>?,
        random: Random?,
        wrongPickCounts: Map<String, Map<String, Int>>?,
    ): RecordsImportModels.MeaningKanjiChoiceCard? {
        if (target?.kanji == null || target.kanji.javaTrim().isEmpty()) {
            return null
        }
        val targetKanji = target.kanji.javaTrim()
        val meaning = target.primaryMeaning.javaTrim()
        if (meaning.isEmpty()) {
            return null
        }
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

    private fun String.javaTrim(): String {
        return trim { it <= ' ' }
    }

    companion object {
        private const val CHOICE_COUNT = 4
    }
}
