package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyLadderSettingsTest {
    @Test
    fun defaultsExposeEditableLadderOrder() {
        val ladder = RecordsBase.StudyLadderSettings.defaults()

        // Goal 79 default order: write_kanji, type_meaning, meaning_kanji,
        // reading_kanji, similar_kanji, kanji_meaning, font_meaning,
        // kanji_reading, word_reading.
        assertEquals(RecordsBase.LadderRung.WRITE_KANJI, ladder.orderedRungs[0])
        assertEquals(RecordsBase.LadderRung.TYPE_MEANING, ladder.orderedRungs[1])
        assertEquals(RecordsBase.LadderRung.MEANING_KANJI, ladder.orderedRungs[2])
        assertEquals(RecordsBase.LadderRung.READING_KANJI, ladder.orderedRungs[3])
        assertEquals(RecordsBase.LadderRung.SIMILAR_KANJI, ladder.orderedRungs[4])
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, ladder.orderedRungs[5])
        assertEquals(RecordsBase.LadderRung.FONT_MEANING, ladder.orderedRungs[6])
        assertEquals(RecordsBase.LadderRung.KANJI_READING, ladder.orderedRungs[7])
        assertEquals(RecordsBase.LadderRung.WORD_READING, ladder.orderedRungs[8])
        assertTrue(ladder.isEnabled(RecordsBase.LadderRung.MEANING_KANJI))
        assertTrue(ladder.isEnabled(RecordsBase.LadderRung.KANJI_READING))
        assertTrue(ladder.isEnabled(RecordsBase.LadderRung.READING_KANJI))
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, ladder.startingRung(RecordsBase.RungAvailability.of(true)))
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
        assertEquals(RecordsBase.LadderRung.SIMILAR_KANJI, ladder.startingRung(RecordsBase.RungAvailability.of(true)))
        assertEquals(RecordsBase.LadderRung.SIMILAR_KANJI, ladder.effectiveRung(RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.RungAvailability.of(true)))
        // Without similar-kanji content the lower neighbor is invalid, so it
        // maps up to font_meaning.
        assertEquals(RecordsBase.LadderRung.FONT_MEANING, ladder.effectiveRung(RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.RungAvailability.none()))

        // Disabling similar_kanji too leaves font_meaning as the nearest rung
        // for the has-content case as well.
        val similarDisabled = ladder
            .withRungEnabled(RecordsBase.LadderRung.SIMILAR_KANJI, false)
        assertEquals(RecordsBase.LadderRung.FONT_MEANING, similarDisabled.effectiveRung(RecordsBase.LadderRung.KANJI_MEANING, RecordsBase.RungAvailability.of(true)))
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
        assertEquals(RecordsBase.LadderRung.READING_KANJI, ladder.orderedRungs[4])
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, ladder.orderedRungs[5])
        assertTrue(ladder.isEnabled(RecordsBase.LadderRung.MEANING_KANJI))
    }

    @Test
    fun storedFullOrderPreservedModuloNewRungSplice() {
        // A user's stored order is preserved verbatim except that a rung
        // postdating it (kanji_reading, Goal 78) is spliced in adjacent to its
        // default neighbors (before word_reading). Every stored rung keeps its
        // relative position.
        val storedOrder = "write_kanji,similar_kanji,type_meaning,meaning_kanji,kanji_meaning,font_meaning,word_reading"
        val ladder = RecordsBase.StudyLadderSettings.fromStored(storedOrder, storedOrder)

        assertEquals(
            "write_kanji,similar_kanji,type_meaning,meaning_kanji,reading_kanji,kanji_meaning,font_meaning,kanji_reading,word_reading",
            ladder.orderText(),
        )
    }

    @Test
    fun storedOrderAlreadyContainingNewRungsIsPreservedVerbatim() {
        // A config that already lists every rung (incl. reading_kanji and
        // kanji_reading) is left exactly as stored.
        val storedOrder = "kanji_reading,reading_kanji,write_kanji,similar_kanji,type_meaning,meaning_kanji,kanji_meaning,font_meaning,word_reading"
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
        assertEquals(RecordsBase.LadderRung.READING_KANJI, ladder.orderedRungs[3])
        assertEquals(RecordsBase.LadderRung.SIMILAR_KANJI, ladder.orderedRungs[4])
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, ladder.orderedRungs[5])
    }

    @Test
    fun canDisableAndMoveRungs() {
        val ladder = RecordsBase.StudyLadderSettings.defaults()
            .withRungEnabled(RecordsBase.LadderRung.SIMILAR_KANJI, false)
            .moveRung(RecordsBase.LadderRung.WORD_READING, -8)

        assertFalse(ladder.isEnabled(RecordsBase.LadderRung.SIMILAR_KANJI))
        assertEquals(RecordsBase.LadderRung.WORD_READING, ladder.orderedRungs[0])
    }

    // ---- Goal 75: RungAvailability ----

    @Test
    fun rungAvailabilityWithoutSimilarReproducesSkipBehavior() {
        // A RungAvailability carrying hasSimilarKanji=false reproduces the
        // pre-refactor behavior: movements crossing SIMILAR_KANJI skip it.
        val ladder = RecordsBase.StudyLadderSettings.defaults()
        val none = RecordsBase.RungAvailability.none()
        assertEquals(
            RecordsBase.LadderRung.MEANING_KANJI,
            ladder.previousRung(RecordsBase.LadderRung.KANJI_MEANING, none),
        )
        // Promotion from MEANING_KANJI skips the unavailable SIMILAR_KANJI and
        // lands on KANJI_MEANING.
        assertEquals(
            RecordsBase.LadderRung.KANJI_MEANING,
            ladder.nextRung(RecordsBase.LadderRung.MEANING_KANJI, none),
        )
    }

    @Test
    fun rungAvailabilityWithSimilarIncludesConditionalRung() {
        val ladder = RecordsBase.StudyLadderSettings.defaults()
        val withSimilar = RecordsBase.RungAvailability.of(true)
        assertEquals(
            RecordsBase.LadderRung.SIMILAR_KANJI,
            ladder.previousRung(RecordsBase.LadderRung.KANJI_MEANING, withSimilar),
        )
    }

    @Test
    fun alwaysAvailableRungsAreAvailableRegardlessOfFlags() {
        val none = RecordsBase.RungAvailability.none()
        // Every non-conditional rung is available even with no conditional data.
        for (rung in RecordsBase.LadderRung.values()) {
            val expected = RecordsBase.StudyLadderSettings.alwaysAvailable(rung)
            assertEquals(rung.wireName(), expected, none.isAvailable(rung))
        }
        // The conditional SIMILAR_KANJI rung follows its flag.
        assertFalse(none.isAvailable(RecordsBase.LadderRung.SIMILAR_KANJI))
        assertTrue(RecordsBase.RungAvailability.of(true).isAvailable(RecordsBase.LadderRung.SIMILAR_KANJI))
    }
}
