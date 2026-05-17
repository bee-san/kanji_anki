package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StudyLadderSettingsTest {
    @Test
    public void defaultsExposeEditableLadderOrder() {
        Records.StudyLadderSettings ladder = Records.StudyLadderSettings.defaults();

        assertEquals(Records.LadderRung.WRITE_KANJI, ladder.orderedRungs.get(0));
        assertEquals(Records.LadderRung.SIMILAR_KANJI, ladder.orderedRungs.get(1));
        assertEquals(Records.LadderRung.TYPE_MEANING, ladder.orderedRungs.get(2));
        assertEquals(Records.LadderRung.MEANING_KANJI, ladder.orderedRungs.get(3));
        assertTrue(ladder.isEnabled(Records.LadderRung.MEANING_KANJI));
        assertEquals(Records.LadderRung.KANJI_MEANING, ladder.startingRung(true));
        assertTrue(ladder.isEnabled(Records.LadderRung.WORD_READING));
    }

    @Test
    public void disabledCurrentRungMapsToNearestEnabledRung() {
        Records.StudyLadderSettings ladder = Records.StudyLadderSettings.defaults()
                .withRungEnabled(Records.LadderRung.KANJI_MEANING, false);

        assertEquals(Records.LadderRung.MEANING_KANJI, ladder.startingRung(true));
        assertEquals(Records.LadderRung.MEANING_KANJI, ladder.effectiveRung(Records.LadderRung.KANJI_MEANING, true));
        assertEquals(Records.LadderRung.MEANING_KANJI, ladder.effectiveRung(Records.LadderRung.KANJI_MEANING, false));

        Records.StudyLadderSettings nearerHigher = ladder
                .withRungEnabled(Records.LadderRung.TYPE_MEANING, false)
                .withRungEnabled(Records.LadderRung.MEANING_KANJI, false);
        assertEquals(Records.LadderRung.FONT_MEANING, nearerHigher.effectiveRung(Records.LadderRung.KANJI_MEANING, true));
    }

    @Test
    public void keepsOneAlwaysAvailableRungEnabled() {
        Records.StudyLadderSettings ladder = new Records.StudyLadderSettings(
                Arrays.asList(Records.LadderRung.SIMILAR_KANJI, Records.LadderRung.TYPE_MEANING),
                Arrays.asList(Records.LadderRung.SIMILAR_KANJI, Records.LadderRung.TYPE_MEANING)
        );

        Records.StudyLadderSettings next = ladder.withRungEnabled(Records.LadderRung.TYPE_MEANING, false);

        assertEquals(ladder.enabledText(), next.enabledText());
        assertTrue(next.isEnabled(Records.LadderRung.TYPE_MEANING));
    }

    @Test
    public void storedValuesNormalizeUnknownsAndMissingRungs() {
        Records.StudyLadderSettings ladder = Records.StudyLadderSettings.fromStored(
                "word_reading unknown kanji_meaning",
                "similar_kanji nope"
        );

        assertEquals(Records.StudyLadderSettings.defaults().orderText(), ladder.orderText());
        assertTrue(ladder.isEnabled(Records.LadderRung.WRITE_KANJI));
        assertTrue(ladder.isEnabled(Records.LadderRung.MEANING_KANJI));
    }

    @Test
    public void oldStoredSixRungLadderInsertsAndEnablesMeaningKanjiAtDefaultSlot() {
        Records.StudyLadderSettings ladder = Records.StudyLadderSettings.fromStored(
                "write_kanji,similar_kanji,type_meaning,kanji_meaning,font_meaning,word_reading",
                "write_kanji,type_meaning,kanji_meaning,font_meaning,word_reading"
        );

        assertEquals(Records.LadderRung.TYPE_MEANING, ladder.orderedRungs.get(2));
        assertEquals(Records.LadderRung.MEANING_KANJI, ladder.orderedRungs.get(3));
        assertEquals(Records.LadderRung.KANJI_MEANING, ladder.orderedRungs.get(4));
        assertTrue(ladder.isEnabled(Records.LadderRung.MEANING_KANJI));
    }

    @Test
    public void canDisableAndMoveRungs() {
        Records.StudyLadderSettings ladder = Records.StudyLadderSettings.defaults()
                .withRungEnabled(Records.LadderRung.SIMILAR_KANJI, false)
                .moveRung(Records.LadderRung.WORD_READING, -6);

        assertFalse(ladder.isEnabled(Records.LadderRung.SIMILAR_KANJI));
        assertEquals(Records.LadderRung.WORD_READING, ladder.orderedRungs.get(0));
    }
}
