package dev.bee.kanjianki

import dev.bee.kanjianki.application.HomeUseCases
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.data.HomeGameDataSnapshot
import dev.bee.kanjianki.data.StoreResult
import dev.bee.kanjianki.data.fakes.FakeHomeRepository
import dev.bee.kanjianki.data.fakes.FakeStudyRepository
import dev.bee.kanjianki.data.fakes.FakeSettingsRepository
import dev.bee.kanjianki.data.fakes.FakeSyncRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The engine-facing half of the Games surface, driven from seeded game data.
 *
 * A fixed `Random(nowMillis)` seed makes question selection deterministic, so these
 * pin start → answer → advance without touching the scheduler or the collection —
 * games are scored in memory and discarded.
 */
class GamesRuntimeTest {
    @Test
    fun theMenuListsTheModesTheDataCanPlay() = runTest {
        val runtime = GamesRuntime(useCases(gameData(fourKanji())))

        val render = runtime.menu()

        // Meaning Pop needs four distinct-meaning kanji, which the seed provides.
        assertTrue(
            "seeded data unlocks meaning pop: ${render.availableModes.map { it.id }}",
            render.availableModes.any { it.id == "meaning_pop" },
        )
        assertNull("the menu has no active mode", render.mode)
    }

    @Test
    fun startingAModeProducesAFirstQuestion() = runTest {
        val runtime = GamesRuntime(useCases(gameData(fourKanji())))
        runtime.menu()

        val render = runtime.start("meaning_pop", NOW)

        assertNotNull(render.question)
        assertTrue(render.inRound)
        assertEquals(0, render.round.answered)
    }

    @Test
    fun answeringScoresTheRoundAndShowsTheResult() = runTest {
        val runtime = GamesRuntime(useCases(gameData(fourKanji())))
        runtime.menu()
        val started = runtime.start("meaning_pop", NOW)
        val correctAnswer = started.question!!.correctAnswer

        val answered = runtime.answer(correctAnswer, NOW)

        assertTrue(answered.showingResult)
        assertTrue(answered.lastCorrect)
        assertEquals(1, answered.round.answered)
        assertEquals(1, answered.round.correct)
    }

    @Test
    fun aSecondAnswerToTheSameQuestionIsIgnored() = runTest {
        val runtime = GamesRuntime(useCases(gameData(fourKanji())))
        runtime.menu()
        val started = runtime.start("meaning_pop", NOW)

        runtime.answer(started.question!!.correctAnswer, NOW)
        val again = runtime.answer("something else", NOW)

        // The round advanced exactly once — the double-commit guard.
        assertEquals(1, again.round.answered)
    }

    @Test
    fun advancingAfterAnAnswerServesTheNextQuestion() = runTest {
        val runtime = GamesRuntime(useCases(gameData(fourKanji())))
        runtime.menu()
        val started = runtime.start("meaning_pop", NOW)
        runtime.answer(started.question!!.correctAnswer, NOW)

        val next = runtime.advance(NOW)

        assertTrue(next.inRound)
        assertNull("the new question is unanswered", next.lastSelected)
        assertNotNull(next.question)
    }

    @Test
    fun aWrongAnswerRecordsAMissAndResetsTheStreak() = runTest {
        val runtime = GamesRuntime(useCases(gameData(fourKanji())))
        runtime.menu()
        val question = runtime.start("meaning_pop", NOW).question!!
        val wrong = question.choices.first { it != question.correctAnswer }

        val answered = runtime.answer(wrong, NOW)

        assertFalse(answered.lastCorrect)
        assertEquals(0, answered.round.correct)
        assertEquals(0, answered.round.streak)
    }

    @Test
    fun noGameDataMeansNoAvailableModes() = runTest {
        val runtime = GamesRuntime(useCases(HomeGameDataSnapshot(emptyList(), emptyList(), emptyList())))

        val render = runtime.menu()

        assertEquals(emptyList<Any>(), render.availableModes)
    }

    private fun useCases(data: HomeGameDataSnapshot): HomeUseCases {
        val home = FakeHomeRepository().apply { gameDataResult = StoreResult.ok(data) }
        return HomeUseCases(home, FakeStudyRepository(), FakeSettingsRepository(), FakeSyncRepository())
    }

    private fun gameData(rows: List<RecordsImportModels.DashboardRow>) =
        HomeGameDataSnapshot(activeRows = rows, inventory = emptyList(), similarPairs = emptyList())

    private fun fourKanji(): List<RecordsImportModels.DashboardRow> =
        listOf("脱" to "take off", "説" to "explain", "税" to "tax", "鋭" to "sharp").map { (kanji, meaning) ->
            RecordsImportModels.DashboardRow(
                kanji, 900, meaning, "だつ", "deck:current", 50, "reason", "reason text", 1, 1, 0,
                listOf(RecordsImportModels.Example("active", 1L, 1L, kanji, "だつ", meaning, "$kanji する", false, 0)),
            )
        }

    private companion object {
        const val NOW = 1_747_000_000_000L
    }
}
