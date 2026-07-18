package dev.bee.kanjianki.core

import java.time.Instant
import java.time.ZoneId

/** Exact local inventory facts selected for one stable local calendar day. */
data class FocusKanjiSelection(
    val kanji: String,
    val primaryMeaning: String,
    val readings: String,
)

object FocusKanjiSelectionPolicy {
    /**
     * Filters committed inventory through canonical Study eligibility, then sorts by normalized
     * glyph, stored meaning, and stored reading. The first duplicate for each glyph wins, and the
     * local epoch day selects from that stable list with floor-mod for pre-1970 dates.
     */
    @JvmStatic
    fun select(
        items: List<RecordsImportModels.KanjiInventoryItem>,
        allowedKanji: Set<String>,
        nowMillis: Long,
        zoneId: ZoneId,
    ): FocusKanjiSelection? {
        val candidates = items.asSequence()
            .mapNotNull(::eligibleSelection)
            .filter { it.kanji in allowedKanji }
            .sortedWith(
                compareBy<FocusKanjiSelection> { it.kanji }
                    .thenBy { it.primaryMeaning }
                    .thenBy { it.readings },
            )
            .distinctBy { it.kanji }
            .toList()
        if (candidates.isEmpty()) return null

        val localEpochDay = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate().toEpochDay()
        val index = Math.floorMod(localEpochDay, candidates.size.toLong()).toInt()
        return candidates[index]
    }

    private fun eligibleSelection(
        item: RecordsImportModels.KanjiInventoryItem,
    ): FocusKanjiSelection? {
        val kanji = TextUtil.normalizeSingleKanji(item.kanji)
        if (
            kanji.isEmpty() ||
            item.sourceCount <= 0 ||
            item.suspended ||
            item.primaryMeaning.isBlank()
        ) {
            return null
        }
        return FocusKanjiSelection(
            kanji = kanji,
            primaryMeaning = item.primaryMeaning,
            readings = item.readings.takeUnless(String::isBlank).orEmpty(),
        )
    }
}
