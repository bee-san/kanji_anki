package dev.bee.kanjianki.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class KanjiGameEngineTest {
    private final KanjiGameEngine engine = new KanjiGameEngine();

    @Test
    public void meaningPopBuildsPracticeOnlyMultipleChoiceQuestion() {
        List<Records.DashboardRow> rows = Arrays.asList(
                row("裂", "split", "れつ"),
                row("提", "present", "てい"),
                row("語", "language", "ご")
        );

        KanjiGameEngine.GameQuestion question = engine.nextQuestion(
                KanjiGameEngine.GameMode.MEANING_POP,
                rows,
                Collections.emptyList(),
                Collections.emptyList(),
                new Random(4L)
        );

        assertNotNull(question);
        assertEquals(KanjiGameEngine.GameMode.MEANING_POP, question.mode);
        assertTrue(question.choices.contains(question.correctAnswer));
        assertTrue(question.choices.size() >= 2);
        assertTrue(question.isCorrect(question.correctAnswer));
        assertFalse(question.isCorrect("definitely wrong"));
    }

    @Test
    public void readingRushTargetsDashboardRowsBeforeInventoryDecoys() {
        List<Records.DashboardRow> rows = Collections.singletonList(rowWithExample(
                "裂",
                "split",
                "れつ",
                example("分裂", "ぶんれつ", "division")
        ));
        List<Records.KanjiInventoryItem> inventory = Arrays.asList(
                inventory("語", "language", "ご"),
                inventory("提", "present", "てい")
        );

        KanjiGameEngine.GameQuestion question = engine.nextQuestion(
                KanjiGameEngine.GameMode.READING_RUSH,
                rows,
                inventory,
                Collections.emptyList(),
                new Random(9L)
        );

        assertNotNull(question);
        assertEquals("裂", question.targetKanji);
        assertEquals("分裂", question.prompt);
        assertEquals("れつ", question.correctAnswer);
        assertTrue(question.choices.contains("ご"));
    }

    @Test
    public void confusableClashUsesSimilarKanjiPairsWithoutStudyState() {
        List<Records.DashboardRow> rows = Arrays.asList(
                row("裂", "split", "れつ"),
                row("提", "present", "てい")
        );
        List<Records.SimilarKanjiPair> pairs = Collections.singletonList(
                new Records.SimilarKanjiPair("裂", "提", "fixture", 1L, 1L)
        );

        KanjiGameEngine.GameQuestion question = engine.nextQuestion(
                KanjiGameEngine.GameMode.CONFUSABLE_CLASH,
                rows,
                Collections.emptyList(),
                pairs,
                new Random(0L)
        );

        assertNotNull(question);
        assertEquals(KanjiGameEngine.GameMode.CONFUSABLE_CLASH, question.mode);
        assertTrue(question.choices.contains(question.targetKanji));
        assertTrue(question.choices.contains("裂") || question.choices.contains("提"));
        assertTrue(question.isCorrect(question.correctAnswer));
    }

    @Test
    public void returnsNullWhenThereAreNotEnoughChoices() {
        KanjiGameEngine.GameQuestion question = engine.nextQuestion(
                KanjiGameEngine.GameMode.MEANING_POP,
                Collections.singletonList(row("裂", "split", "れつ")),
                Collections.emptyList(),
                Collections.emptyList(),
                new Random(0L)
        );

        assertNull(question);
    }

    @Test
    public void availableModesOnlyIncludesBuildableGames() {
        List<Records.DashboardRow> rows = Arrays.asList(
                row("裂", "split", "れつ"),
                row("提", "present", "てい")
        );
        List<Records.SimilarKanjiPair> pairs = Collections.singletonList(
                new Records.SimilarKanjiPair("裂", "提", "fixture", 1L, 1L)
        );

        List<KanjiGameEngine.GameMode> modes = engine.availableModes(rows, Collections.emptyList(), pairs);

        assertTrue(modes.contains(KanjiGameEngine.GameMode.MEANING_POP));
        assertTrue(modes.contains(KanjiGameEngine.GameMode.READING_RUSH));
        assertTrue(modes.contains(KanjiGameEngine.GameMode.CONFUSABLE_CLASH));
    }

    private static Records.DashboardRow row(String kanji, String meaning, String reading) {
        return rowWithExample(kanji, meaning, reading, example(kanji + "語", reading, meaning));
    }

    private static Records.DashboardRow rowWithExample(String kanji, String meaning, String reading, Records.Example example) {
        return new Records.DashboardRow(
                kanji,
                100,
                meaning,
                reading,
                kanji,
                7,
                "reason",
                "reason text",
                1,
                0,
                0,
                Collections.singletonList(example)
        );
    }

    private static Records.Example example(String expression, String reading, String meaning) {
        return new Records.Example("active", 1L, 2L, expression, reading, meaning, "", false, 0);
    }

    private static Records.KanjiInventoryItem inventory(String kanji, String meaning, String reading) {
        return new Records.KanjiInventoryItem(kanji, meaning, reading, kanji, 1, 1, false, 1L);
    }
}
