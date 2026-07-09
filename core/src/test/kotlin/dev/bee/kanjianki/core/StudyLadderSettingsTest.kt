package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyLadderSettingsTest {
    @Test
    fun defaultsExposeEditableLadderOrder() {
        val ladder = RecordsBase.StudyLadderSettings.defaults()

        // Goal 65 default order: write_kanji, type_meaning, meaning_kanji,
        // similar_kanji, kanji_meaning, font_meaning, word_reading.
        assertEquals(RecordsBase.LadderRung.WRITE_KANJI, ladder.orderedRungs[0])
        assertEquals(RecordsBase.LadderRung.TYPE_MEANING, ladder.orderedRungs[1])
        assertEquals(RecordsBase.LadderRung.MEANING_KANJI, ladder.orderedRungs[2])
        assertEquals(RecordsBase.LadderRung.SIMILAR_KANJI, ladder.orderedRungs[3])
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, ladder.orderedRungs[4])
        assertEquals(RecordsBase.LadderRung.FONT_MEANING, ladder.orderedRungs[5])
        assertEquals(RecordsBase.LadderRung.WORD_READING, ladder.orderedRungs[6])
        assertTrue(ladder.isEnabled(RecordsBase.LadderRung.MEANING_KANJI))
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, ladder.startingRung(true))
        assertTrue(ladder.isEnabled(RecordsBase.LadderRung.WORD_READING))
    }

    @Test
    fun disabledCurrentRungMapsToNearestEnabledRung() {
        // New default order (Goal 65): kanji_meaning is at index 4, with
        // similar_kanji directly below (index 3) and font_meaning above (5).
        val ladder = RecordsBase.StudyLadderSettings.defaults()
            .withRungEnabled(RecordsBase.LadderRung.KANJI_MEANING, false)

        // With similar-kanji content, the nearest enabled neighbor below is
        // similar_kanji itself; ties prefer the more-scaffolded lower rung.
        assertEquals(RecordsBase.LadderRung.SIMILAR_KANJI, ladder.startingRung(true))
        assertEquals(RecordsBase.LadderRung.SIMILAR_KANJI, ladder.effectiveRung(RecordsBase.LadderRung.KANJI_MEANING, true))
        // Without similar-kanji content the lower neighbor is invalid, so it
        // maps up to font_meaning.
        assertEquals(RecordsBase.LadderRung.FONT_MEANING, ladder.effectiveRung(RecordsBase.LadderRung.KANJI_MEANING, false))

        // Disabling similar_kanji too leaves font_meaning as the nearest rung
        // for the has-content case as well.
        val similarDisabled = ladder
            .withRungEnabled(RecordsBase.LadderRung.SIMILAR_KANJI, false)
        assertEquals(RecordsBase.LadderRung.FONT_MEANING, similarDisabled.effectiveRung(RecordsBase.LadderRung.KANJI_MEANING, true))
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
            listOf(null, RecordsBase.LadderRung.KANJI_MEANING, null),
            listOf(null, RecordsBase.LadderRung.KANJI_MEANING, null)
        )

        val orderedRungs = ladder.orderedRungs.map { it as RecordsBase.LadderRung? }
        val enabledRungs = ladder.enabledRungs.map { it as RecordsBase.LadderRung? }

        assertFalse(orderedRungs.contains(null))
        assertFalse(enabledRungs.contains(null))
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
    fun storedFullOrderRoundTripsUnchanged() {
        // A user's stored order (all seven rungs, e.g. the pre-Goal-65 default)
        // is preserved verbatim; only fresh installs and configs missing rungs
        // are affected by the default reorder.
        val storedOrder = "write_kanji,similar_kanji,type_meaning,meaning_kanji,kanji_meaning,font_meaning,word_reading"
        val ladder = RecordsBase.StudyLadderSettings.fromStored(storedOrder, storedOrder)

        assertEquals(storedOrder, ladder.orderText())
    }

    @Test
    fun storedOrderMissingSimilarKanjiInsertsItBetweenMeaningKanjiAndKanjiMeaning() {
        // Stored config predates similar_kanji. insertMissingRung anchors on the
        // new default neighbors, so it lands between meaning_kanji and
        // kanji_meaning (Goal 65 default slot).
        val ladder = RecordsBase.StudyLadderSettings.fromStored(
            "write_kanji,type_meaning,meaning_kanji,kanji_meaning,font_meaning,word_reading",
            "write_kanji,type_meaning,meaning_kanji,kanji_meaning,font_meaning,word_reading"
        )

        assertEquals(RecordsBase.LadderRung.MEANING_KANJI, ladder.orderedRungs[2])
        assertEquals(RecordsBase.LadderRung.SIMILAR_KANJI, ladder.orderedRungs[3])
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, ladder.orderedRungs[4])
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
