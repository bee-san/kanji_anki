package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class MeaningKanjiChoicePlannerTest {
    @Test
    public void buildsFourLocalKanjiChoicesIncludingTarget() {
        Records.MeaningKanjiChoiceCard card = new MeaningKanjiChoicePlanner().buildChoiceCard(
                row("裂", "split"),
                Arrays.asList(row("裂", "split"), row("提", "present"), row("浅", "shallow")),
                Arrays.asList(inventory("腕"), inventory("謎")),
                new Random(7)
        );

        assertNotNull(card);
        assertEquals("裂", card.targetKanji);
        assertEquals("split", card.primaryMeaning);
        assertEquals(4, card.choices.size());
        assertTrue(card.choices.contains("裂"));
        assertTrue(card.isCorrect("裂"));
    }

    @Test
    public void returnsNullWhenFewerThanFourLocalKanjiExist() {
        Records.MeaningKanjiChoiceCard card = new MeaningKanjiChoicePlanner().buildChoiceCard(
                row("裂", "split"),
                Arrays.asList(row("裂", "split"), row("提", "present"), row("浅", "shallow")),
                Collections.singletonList(inventory("浅")),
                new Random(7)
        );

        assertNull(card);
    }

    @Test
    public void trimsChoicesAndSkipsNullInventoryKanji() {
        Records.MeaningKanjiChoiceCard card = new MeaningKanjiChoicePlanner().buildChoiceCard(
                row(" 裂 ", " split "),
                Arrays.asList(row("裂", "split"), row("提", "present"), row("浅", "shallow")),
                Arrays.asList(null, inventory(null), inventory(" 腕 ", "arm")),
                new Random(7)
        );

        assertNotNull(card);
        assertEquals("裂", card.targetKanji);
        assertEquals("split", card.primaryMeaning);
        assertEquals(4, card.choices.size());
        assertTrue(card.choices.contains("腕"));
        assertTrue(card.isCorrect(" 裂 "));
    }

    @Test
    public void excludesDecoysWithSamePrimaryMeaning() {
        Records.MeaningKanjiChoiceCard card = new MeaningKanjiChoicePlanner().buildChoiceCard(
                row("裂", "split"),
                Arrays.asList(row("裂", "split"), row("割", " split "), row("提", "present"), row("浅", "shallow")),
                Arrays.asList(inventory("腕", "arm"), inventory("謎", "mystery")),
                new Random(7)
        );

        assertNotNull(card);
        assertFalse(card.choices.contains("割"));
        assertEquals(4, card.choices.size());
    }

    private static Records.DashboardRow row(String kanji, String meaning) {
        return new Records.DashboardRow(kanji, 100, meaning, "reading", "search", 10, "reason", "reason text", 1, 0, 0, new ArrayList<>());
    }

    private static Records.KanjiInventoryItem inventory(String kanji) {
        return inventory(kanji, "meaning");
    }

    private static Records.KanjiInventoryItem inventory(String kanji, String meaning) {
        return new Records.KanjiInventoryItem(kanji, meaning, "reading", "search", 1, 1, false, 0L);
    }
}
