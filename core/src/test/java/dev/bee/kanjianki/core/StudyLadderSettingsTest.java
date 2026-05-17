package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class StudyLadderSettingsTest {
    @Test
    public void defaultsExposeEditableLadderOrder() {
        RecordsBase.StudyLadderSettings ladder = RecordsBase.StudyLadderSettings.defaults();

        assertEquals(RecordsBase.LadderRung.WRITE_KANJI, ladder.orderedRungs.get(0));
        assertEquals(RecordsBase.LadderRung.SIMILAR_KANJI, ladder.orderedRungs.get(1));
        assertEquals(RecordsBase.LadderRung.TYPE_MEANING, ladder.orderedRungs.get(2));
        assertEquals(RecordsBase.LadderRung.MEANING_KANJI, ladder.orderedRungs.get(3));
        assertTrue(ladder.isEnabled(RecordsBase.LadderRung.MEANING_KANJI));
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, ladder.startingRung(true));
        assertTrue(ladder.isEnabled(RecordsBase.LadderRung.WORD_READING));
    }

    @Test
    public void disabledCurrentRungMapsToNearestEnabledRung() {
        RecordsBase.StudyLadderSettings ladder = RecordsBase.StudyLadderSettings.defaults()
                .withRungEnabled(RecordsBase.LadderRung.KANJI_MEANING, false);

        assertEquals(RecordsBase.LadderRung.MEANING_KANJI, ladder.startingRung(true));
        assertEquals(RecordsBase.LadderRung.MEANING_KANJI, ladder.effectiveRung(RecordsBase.LadderRung.KANJI_MEANING, true));
        assertEquals(RecordsBase.LadderRung.MEANING_KANJI, ladder.effectiveRung(RecordsBase.LadderRung.KANJI_MEANING, false));

        RecordsBase.StudyLadderSettings nearerHigher = ladder
                .withRungEnabled(RecordsBase.LadderRung.TYPE_MEANING, false)
                .withRungEnabled(RecordsBase.LadderRung.MEANING_KANJI, false);
        assertEquals(RecordsBase.LadderRung.FONT_MEANING, nearerHigher.effectiveRung(RecordsBase.LadderRung.KANJI_MEANING, true));
    }

    @Test
    public void keepsOneAlwaysAvailableRungEnabled() {
        RecordsBase.StudyLadderSettings ladder = new RecordsBase.StudyLadderSettings(
                Arrays.asList(RecordsBase.LadderRung.SIMILAR_KANJI, RecordsBase.LadderRung.TYPE_MEANING),
                Arrays.asList(RecordsBase.LadderRung.SIMILAR_KANJI, RecordsBase.LadderRung.TYPE_MEANING)
        );

        RecordsBase.StudyLadderSettings next = ladder.withRungEnabled(RecordsBase.LadderRung.TYPE_MEANING, false);

        assertEquals(ladder.enabledText(), next.enabledText());
        assertTrue(next.isEnabled(RecordsBase.LadderRung.TYPE_MEANING));
    }

    @Test
    public void storedValuesNormalizeUnknownsAndMissingRungs() {
        RecordsBase.StudyLadderSettings ladder = RecordsBase.StudyLadderSettings.fromStored(
                "word_reading unknown kanji_meaning",
                "similar_kanji nope"
        );

        assertEquals(RecordsBase.StudyLadderSettings.defaults().orderText(), ladder.orderText());
        assertTrue(ladder.isEnabled(RecordsBase.LadderRung.WRITE_KANJI));
        assertTrue(ladder.isEnabled(RecordsBase.LadderRung.MEANING_KANJI));
    }

    @Test
    public void oldStoredSixRungLadderInsertsAndEnablesMeaningKanjiAtDefaultSlot() {
        RecordsBase.StudyLadderSettings ladder = RecordsBase.StudyLadderSettings.fromStored(
                "write_kanji,similar_kanji,type_meaning,kanji_meaning,font_meaning,word_reading",
                "write_kanji,type_meaning,kanji_meaning,font_meaning,word_reading"
        );

        assertEquals(RecordsBase.LadderRung.TYPE_MEANING, ladder.orderedRungs.get(2));
        assertEquals(RecordsBase.LadderRung.MEANING_KANJI, ladder.orderedRungs.get(3));
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, ladder.orderedRungs.get(4));
        assertTrue(ladder.isEnabled(RecordsBase.LadderRung.MEANING_KANJI));
    }

    @Test
    public void canDisableAndMoveRungs() {
        RecordsBase.StudyLadderSettings ladder = RecordsBase.StudyLadderSettings.defaults()
                .withRungEnabled(RecordsBase.LadderRung.SIMILAR_KANJI, false)
                .moveRung(RecordsBase.LadderRung.WORD_READING, -6);

        assertFalse(ladder.isEnabled(RecordsBase.LadderRung.SIMILAR_KANJI));
        assertEquals(RecordsBase.LadderRung.WORD_READING, ladder.orderedRungs.get(0));
    }
}
