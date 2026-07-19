package dev.bee.kanjianki.core

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FocusKanjiSelectionPolicyTest {
    @Test
    fun selectsOnlyNormalizedAllowedUsableInventory() {
        val selected = FocusKanjiSelectionPolicy.select(
            items = listOf(
                item(" 学 ", "study", "がく"),
                item("語", "language", "ご"),
                item("習", "practice", "しゅう", sourceCount = 0),
                item("弱", "weak", "じゃく", suspended = true),
                item("裂", "   ", "れつ"),
                item("not-kanji", "invalid", ""),
            ),
            allowedKanji = setOf("学", "習", "弱", "裂"),
            nowMillis = Instant.parse("2026-07-18T12:00:00Z").toEpochMilli(),
            zoneId = ZoneOffset.UTC,
        )

        assertEquals(FocusKanjiSelection("学", "study", "がく"), selected)
    }

    @Test
    fun optionalBlankReadingIsKeptBlankWithoutFabrication() {
        val selected = FocusKanjiSelectionPolicy.select(
            listOf(item("学", "study", "")),
            setOf("学"),
            NOW,
            ZoneOffset.UTC,
        )

        assertEquals(FocusKanjiSelection("学", "study", ""), selected)
    }

    @Test
    fun duplicateGlyphUsesStableMeaningThenReadingTieBreaker() {
        val selected = FocusKanjiSelectionPolicy.select(
            listOf(
                item("学", "study", "まなぶ"),
                item("学", "learn", "がく"),
                item("学", "learn", "まなぶ"),
            ),
            setOf("学"),
            NOW,
            ZoneOffset.UTC,
        )

        assertEquals(FocusKanjiSelection("学", "learn", "がく"), selected)
    }

    @Test
    fun resultIsInputOrderIndependentAndStableForLocalDay() {
        val items = listOf(
            item("学", "study", "がく"),
            item("語", "language", "ご"),
            item("裂", "split", "れつ"),
        )
        val zone = ZoneId.of("Asia/Tokyo")
        val morning = Instant.parse("2026-07-18T00:30:00Z").toEpochMilli()
        val evening = Instant.parse("2026-07-18T14:30:00Z").toEpochMilli()

        val first = FocusKanjiSelectionPolicy.select(items, items.mapTo(linkedSetOf()) { it.kanji }, morning, zone)
        val reordered = FocusKanjiSelectionPolicy.select(items.reversed(), items.mapTo(linkedSetOf()) { it.kanji }, evening, zone)

        assertEquals(first, reordered)
    }

    @Test
    fun nextLocalDayRotatesAcrossSortedCandidates() {
        val items = listOf(item("学", "study", "がく"), item("語", "language", "ご"))
        val allowed = setOf("学", "語")
        val dayOne = LocalDate.of(2026, 12, 31).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val dayTwo = LocalDate.of(2027, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

        val first = FocusKanjiSelectionPolicy.select(items, allowed, dayOne, ZoneOffset.UTC)
        val second = FocusKanjiSelectionPolicy.select(items, allowed, dayTwo, ZoneOffset.UTC)

        assertEquals(false, first == second)
    }

    @Test
    fun negativeEpochUsesFloorModInsteadOfNegativeIndex() {
        val items = listOf(
            item("一", "one", "いち"),
            item("三", "three", "さん"),
            item("二", "two", "に"),
        )
        val selected = FocusKanjiSelectionPolicy.select(
            items,
            items.mapTo(linkedSetOf()) { it.kanji },
            Instant.parse("1969-12-31T12:00:00Z").toEpochMilli(),
            ZoneOffset.UTC,
        )

        assertEquals("二", selected?.kanji)
    }

    @Test
    fun daylightSavingInstantsOnSameLocalDateKeepSelection() {
        val items = listOf(item("夏", "summer", "なつ"), item("冬", "winter", "ふゆ"))
        val allowed = setOf("夏", "冬")
        val zone = ZoneId.of("America/New_York")
        val beforeJump = Instant.parse("2026-03-08T06:30:00Z").toEpochMilli()
        val afterJump = Instant.parse("2026-03-08T07:30:00Z").toEpochMilli()

        assertEquals(
            FocusKanjiSelectionPolicy.select(items, allowed, beforeJump, zone),
            FocusKanjiSelectionPolicy.select(items, allowed, afterJump, zone),
        )
    }

    @Test
    fun emptyOrDisallowedInventoryReturnsNull() {
        assertNull(FocusKanjiSelectionPolicy.select(emptyList(), setOf("学"), NOW, ZoneOffset.UTC))
        assertNull(
            FocusKanjiSelectionPolicy.select(
                listOf(item("学", "study", "がく")),
                setOf("語"),
                NOW,
                ZoneOffset.UTC,
            ),
        )
    }

    private fun item(
        kanji: String,
        meaning: String,
        readings: String,
        sourceCount: Int = 1,
        suspended: Boolean = false,
    ) = RecordsImportModels.KanjiInventoryItem(
        kanji,
        meaning,
        readings,
        "",
        sourceCount,
        0,
        suspended,
        NOW,
    )

    companion object {
        private val NOW = Instant.parse("2026-07-18T12:00:00Z").toEpochMilli()
    }
}
