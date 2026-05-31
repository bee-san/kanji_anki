package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyLadderSettingsTest {
    @Test
    fun defaultsExposeEditableLadderOrder() {
        val ladder = RecordsBase.StudyLadderSettings.defaults()

        assertEquals(RecordsBase.LadderRung.WRITE_KANJI, ladder.orderedRungs[0])
        assertEquals(RecordsBase.LadderRung.SIMILAR_KANJI, ladder.orderedRungs[1])
        assertEquals(RecordsBase.LadderRung.TYPE_MEANING, ladder.orderedRungs[2])
        assertEquals(RecordsBase.LadderRung.MEANING_KANJI, ladder.orderedRungs[3])
        assertTrue(ladder.isEnabled(RecordsBase.LadderRung.MEANING_KANJI))
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, ladder.startingRung(true))
        assertTrue(ladder.isEnabled(RecordsBase.LadderRung.WORD_READING))
    }

    @Test
    fun disabledCurrentRungMapsToNearestEnabledRung() {
        val ladder = RecordsBase.StudyLadderSettings.defaults()
            .withRungEnabled(RecordsBase.LadderRung.KANJI_MEANING, false)

        assertEquals(RecordsBase.LadderRung.MEANING_KANJI, ladder.startingRung(true))
        assertEquals(RecordsBase.LadderRung.MEANING_KANJI, ladder.effectiveRung(RecordsBase.LadderRung.KANJI_MEANING, true))
        assertEquals(RecordsBase.LadderRung.MEANING_KANJI, ladder.effectiveRung(RecordsBase.LadderRung.KANJI_MEANING, false))

        val nearerHigher = ladder
            .withRungEnabled(RecordsBase.LadderRung.TYPE_MEANING, false)
            .withRungEnabled(RecordsBase.LadderRung.MEANING_KANJI, false)
        assertEquals(RecordsBase.LadderRung.FONT_MEANING, nearerHigher.effectiveRung(RecordsBase.LadderRung.KANJI_MEANING, true))
    }

    @Test
    fun keepsOneAlwaysAvailableRungEnabled() {
        val ladder = RecordsBase.StudyLadderSettings(
            listOf(RecordsBase.LadderRung.SIMILAR_KANJI, RecordsBase.LadderRung.TYPE_MEANING),
            listOf(RecordsBase.LadderRung.SIMILAR_KANJI, RecordsBase.LadderRung.TYPE_MEANING)
        )

        val next = ladder.withRungEnabled(RecordsBase.LadderRung.TYPE_MEANING, false)

        assertEquals(ladder.enabledText(), next.enabledText())
        assertTrue(next.isEnabled(RecordsBase.LadderRung.TYPE_MEANING))
    }

    @Test
    fun constructorIgnoresNullRungsFromJavaCallers() {
        val ladder = RecordsBase.StudyLadderSettings(
            listOf<RecordsBase.LadderRung?>(null, RecordsBase.LadderRung.KANJI_MEANING, null),
            listOf<RecordsBase.LadderRung?>(null, RecordsBase.LadderRung.KANJI_MEANING, null)
        )

        assertFalse(ladder.orderedRungs.contains(null as RecordsBase.LadderRung?))
        assertFalse(ladder.enabledRungs.contains(null as RecordsBase.LadderRung?))
        assertEquals("kanji_meaning", ladder.enabledText())
    }

    @Test
    fun storedValuesNormalizeUnknownsAndMissingRungs() {
        val ladder = RecordsBase.StudyLadderSettings.fromStored(
            "word_reading unknown kanji_meaning",
            "similar_kanji nope"
        )

        assertEquals(RecordsBase.StudyLadderSettings.defaults().orderText(), ladder.orderText())
        assertTrue(ladder.isEnabled(RecordsBase.LadderRung.WRITE_KANJI))
        assertTrue(ladder.isEnabled(RecordsBase.LadderRung.MEANING_KANJI))
    }

    @Test
    fun oldStoredSixRungLadderInsertsAndEnablesMeaningKanjiAtDefaultSlot() {
        val ladder = RecordsBase.StudyLadderSettings.fromStored(
            "write_kanji,similar_kanji,type_meaning,kanji_meaning,font_meaning,word_reading",
            "write_kanji,type_meaning,kanji_meaning,font_meaning,word_reading"
        )

        assertEquals(RecordsBase.LadderRung.TYPE_MEANING, ladder.orderedRungs[2])
        assertEquals(RecordsBase.LadderRung.MEANING_KANJI, ladder.orderedRungs[3])
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, ladder.orderedRungs[4])
        assertTrue(ladder.isEnabled(RecordsBase.LadderRung.MEANING_KANJI))
    }

    @Test
    fun canDisableAndMoveRungs() {
        val ladder = RecordsBase.StudyLadderSettings.defaults()
            .withRungEnabled(RecordsBase.LadderRung.SIMILAR_KANJI, false)
            .moveRung(RecordsBase.LadderRung.WORD_READING, -6)

        assertFalse(ladder.isEnabled(RecordsBase.LadderRung.SIMILAR_KANJI))
        assertEquals(RecordsBase.LadderRung.WORD_READING, ladder.orderedRungs[0])
    }
}
