package dev.bee.kanjianki.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GamesScreenTest {
    @Test
    fun anAvailableModeStartsAndAnUnavailableOneDoesNot() {
        val available = modeCard(available = true)
        assertEquals(KaniAction.Game.Start(modeId = "meaning_pop"), available.action)
        assertNull(modeCard(available = false).action)
    }

    @Test
    fun aModeNeedsAnId() {
        assertFailsWith<IllegalArgumentException> { modeCard(id = " ") }
        assertFailsWith<IllegalArgumentException> { KaniAction.Game.Start(modeId = "") }
    }

    @Test
    fun aChoiceAnswersWithItsValue() {
        val choice = GamesChoice(value = "take off", label = "take off")
        assertEquals(KaniAction.Game.Answer(answer = "take off"), choice.action)
    }

    @Test
    fun answeringOrChoosingNothingIsNotAnIntent() {
        assertFailsWith<IllegalArgumentException> { GamesChoice(value = " ", label = "x") }
        assertFailsWith<IllegalArgumentException> { KaniAction.Game.Answer(answer = "") }
    }

    @Test
    fun aScreenHoldsWhicheverStateItIsIn() {
        val menu = GamesScreen(
            state = GamesState.MENU,
            menu = GamesMenu(title = "Games", subtitle = "warm up", modes = listOf(modeCard(available = true))),
        )
        assertEquals(GamesState.MENU, menu.state)
        assertEquals(1, menu.menu?.modes?.size)

        val round = GamesScreen(
            state = GamesState.ROUND,
            round = GamesRound(
                roundLabel = "Round",
                roundValue = "1/10",
                scoreLabel = "Score",
                scoreValue = "0",
                streakLabel = "Streak",
                streakValue = "0",
                scoreDescription = "Round 1 of 10.",
                modeLabel = "Meaning Pop",
                prompt = "脱",
                promptDetail = "Choose the meaning.",
                choices = listOf(GamesChoice("take off", "take off")),
                accent = GamesAccent.MEANING,
                choicesAreKanji = false,
            ),
        )
        assertEquals("脱", round.round?.prompt)

        val result = GamesScreen(
            state = GamesState.RESULT,
            result = GamesResult(
                title = "Correct!",
                correct = true,
                finalScore = "40",
                accuracy = "80%",
                primaryLabel = "Play again",
                primaryAction = KaniAction.Game.Continue,
            ),
        )
        assertEquals(KaniAction.Game.Continue, result.result?.primaryAction)
        assertEquals(true, result.result?.correct)
    }

    @Test
    fun everyAccentIsDistinct() {
        assertEquals(GamesAccent.entries.size, GamesAccent.entries.toSet().size)
    }

    private fun modeCard(id: String = "meaning_pop", available: Boolean = true) = GamesModeCard(
        id = id,
        title = "Meaning Pop",
        label = "Kanji → meaning",
        body = "Choose the meaning.",
        accent = GamesAccent.MEANING,
        available = available,
        chipLabel = "Play",
    )
}
