package dev.bee.kanjianki.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Collections
import java.util.Random

class KanjiGameEngineTest {
    private val engine = KanjiGameEngine()

    @Test
    fun meaningPopBuildsPracticeOnlyMultipleChoiceQuestion() {
        val rows = listOf(
            row("裂", "split", "れつ"),
            row("提", "present", "てい"),
            row("語", "language", "ご")
        )

        val question = engine.nextQuestion(
            KanjiGameEngine.GameMode.MEANING_POP,
            rows,
            emptyList(),
            emptyList(),
            Random(4L)
        )

        assertNotNull(question)
        assertEquals(KanjiGameEngine.GameMode.MEANING_POP, question!!.mode)
        assertTrue(question.choices.contains(question.correctAnswer))
        assertTrue(question.choices.size >= 2)
        assertTrue(question.isCorrect(question.correctAnswer))
        assertFalse(question.isCorrect("definitely wrong"))
    }

    @Test
    fun readingRushTargetsDashboardRowsBeforeInventoryDecoys() {
        val rows = listOf(
            rowWithExample(
                "裂",
                "split",
                "れつ",
                example("分裂", "ぶんれつ", "division")
            )
        )
        val inventory = listOf(
            inventory("語", "language", "ご"),
            inventory("提", "present", "てい")
        )

        val question = engine.nextQuestion(
            KanjiGameEngine.GameMode.READING_RUSH,
            rows,
            inventory,
            emptyList(),
            Random(9L)
        )

        assertNotNull(question)
        assertEquals("裂", question!!.targetKanji)
        assertEquals("分裂", question.prompt)
        assertEquals("ぶんれつ", question.correctAnswer)
        assertTrue(question.choices.contains("ご"))
    }

    @Test
    fun readingRushKeepsExpressionAndReadingFromTheSameExample() {
        val rows = listOf(
            rowWithExample(
                "裂",
                "split",
                "れつ",
                example("分割", "", "division"),
                example("分裂", "ぶんれつ", "fission"),
            ),
        )
        val inventory = listOf(
            inventory("語", "language", "ご"),
            inventory("提", "present", "てい"),
        )

        val question = engine.nextQuestion(
            KanjiGameEngine.GameMode.READING_RUSH,
            rows,
            inventory,
            emptyList(),
            Random(9L),
        )

        assertNotNull(question)
        assertEquals("分裂", question!!.prompt)
        assertEquals("ぶんれつ", question.correctAnswer)
    }

    @Test
    fun confusableClashUsesSimilarKanjiPairsWithoutStudyState() {
        val rows = listOf(
            row("裂", "split", "れつ"),
            row("提", "present", "てい")
        )
        val pairs = listOf(
            RecordsImportModels.SimilarKanjiPair("裂", "提", "fixture", 1L, 1L)
        )

        val question = engine.nextQuestion(
            KanjiGameEngine.GameMode.CONFUSABLE_CLASH,
            rows,
            emptyList(),
            pairs,
            Random(0L)
        )

        assertNotNull(question)
        assertEquals(KanjiGameEngine.GameMode.CONFUSABLE_CLASH, question!!.mode)
        assertTrue(question.choices.contains(question.targetKanji))
        assertTrue(question.choices.contains("裂") || question.choices.contains("提"))
        assertTrue(question.isCorrect(question.correctAnswer))
    }

    @Test
    fun returnsNullWhenThereAreNotEnoughChoices() {
        val question = engine.nextQuestion(
            KanjiGameEngine.GameMode.MEANING_POP,
            listOf(row("裂", "split", "れつ")),
            emptyList(),
            emptyList(),
            Random(0L)
        )

        assertNull(question)
    }

    @Test
    fun availableModesOnlyIncludesBuildableGames() {
        val rows = listOf(
            row("裂", "split", "れつ"),
            row("提", "present", "てい")
        )
        val pairs = listOf(
            RecordsImportModels.SimilarKanjiPair("裂", "提", "fixture", 1L, 1L)
        )

        val modes = engine.availableModes(rows, emptyList(), pairs)

        assertTrue(modes.contains(KanjiGameEngine.GameMode.MEANING_POP))
        assertTrue(modes.contains(KanjiGameEngine.GameMode.READING_RUSH))
        assertTrue(modes.contains(KanjiGameEngine.GameMode.CONFUSABLE_CLASH))
    }

    @Test
    fun missSweepTargetsRecentMissKanjiWithMeaningChoices() {
        val rows = listOf(
            row("裂", "split", "れつ"),
            row("提", "present", "てい"),
            row("語", "language", "ご")
        )

        val question = engine.nextQuestion(
            KanjiGameEngine.GameMode.MISS_SWEEP,
            rows,
            emptyList(),
            emptyList(),
            Random(4L),
            listOf("裂", "提")
        )

        assertNotNull(question)
        assertEquals(KanjiGameEngine.GameMode.MISS_SWEEP, question!!.mode)
        assertTrue(question.targetKanji == "裂" || question.targetKanji == "提")
        assertTrue(question.choices.size >= 2)
        assertTrue(question.choices.contains(question.correctAnswer))
        assertTrue(question.isCorrect(question.correctAnswer))
    }

    @Test
    fun missSweepReturnsNullWithoutEnoughRecentMisses() {
        val rows = listOf(
            row("裂", "split", "れつ"),
            row("提", "present", "てい")
        )

        assertNull(
            engine.nextQuestion(
                KanjiGameEngine.GameMode.MISS_SWEEP,
                rows,
                emptyList(),
                emptyList(),
                Random(1L),
                listOf("裂")
            )
        )
        assertNull(
            engine.nextQuestion(
                KanjiGameEngine.GameMode.MISS_SWEEP,
                rows,
                emptyList(),
                emptyList(),
                Random(1L),
                emptyList()
            )
        )
        assertNull(
            engine.nextQuestion(
                KanjiGameEngine.GameMode.MISS_SWEEP,
                rows,
                emptyList(),
                emptyList(),
                Random(1L),
                null
            )
        )
    }

    @Test
    fun missSweepReturnsNullWhenChoicesCollapseToOneAnswer() {
        val rows = listOf(
            row("裂", "same", "れつ"),
            row("提", "same", "てい")
        )

        assertNull(
            engine.nextQuestion(
                KanjiGameEngine.GameMode.MISS_SWEEP,
                rows,
                emptyList(),
                emptyList(),
                Random(1L),
                listOf("裂", "提")
            )
        )
    }

    @Test
    fun availableModesIncludesMissSweepWhenRecentMissesExist() {
        val rows = listOf(
            row("裂", "split", "れつ"),
            row("提", "present", "てい"),
            row("語", "language", "ご")
        )

        val modes = engine.availableModes(rows, emptyList(), emptyList(), listOf("裂", "提"))

        assertTrue(modes.contains(KanjiGameEngine.GameMode.MISS_SWEEP))
    }

    private fun row(kanji: String, meaning: String, reading: String): RecordsImportModels.DashboardRow {
        return rowWithExample(kanji, meaning, reading, example(kanji + "語", reading, meaning))
    }

    private fun rowWithExample(
        kanji: String,
        meaning: String,
        reading: String,
        vararg examples: RecordsImportModels.Example,
    ): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
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
            examples.toList()
        )
    }

    private fun example(expression: String, reading: String, meaning: String): RecordsImportModels.Example {
        return RecordsImportModels.Example("active", 1L, 2L, expression, reading, meaning, "", false, 0)
    }

    private fun inventory(kanji: String, meaning: String, reading: String): RecordsImportModels.KanjiInventoryItem {
        return RecordsImportModels.KanjiInventoryItem(kanji, meaning, reading, kanji, 1, 1, false, 1L)
    }
}
