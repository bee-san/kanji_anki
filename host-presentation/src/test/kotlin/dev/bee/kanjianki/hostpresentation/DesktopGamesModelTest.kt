package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.GamesRender
import dev.bee.kanjianki.core.KanjiGameEngine
import dev.bee.kanjianki.core.KanjiGameRoundState
import dev.bee.kanjianki.data.HomeGameDataSnapshot
import dev.bee.kanjianki.presentation.GamesState
import dev.bee.kanjianki.presentation.KaniAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The desktop half of the Games parity: the mapping renders the engine state as the
 * portable screen. The surface's layout is proven by `:feature-games`'s render tests
 * on both hosts; this checks the state selection and per-mode accent the mapping owns.
 */
class DesktopGamesModelTest {
    @Test
    fun noDataMapsToUnavailable() {
        val screen = DesktopGamesModel.screen(render(data = null))
        assertEquals(GamesState.UNAVAILABLE, screen.state)
    }

    @Test
    fun loadedDataWithNoModeMapsToTheMenuWithEveryModeShown() {
        val screen = DesktopGamesModel.screen(
            render(data = emptyData(), available = listOf(KanjiGameEngine.GameMode.MEANING_POP)),
        )

        assertEquals(GamesState.MENU, screen.state)
        // All four modes appear; only the available one starts.
        assertEquals(4, screen.menu?.modes?.size)
        val pop = screen.menu?.modes?.first { it.id == "meaning_pop" }
        assertEquals(KaniAction.Game.Start(modeId = "meaning_pop"), pop?.action)
        val locked = screen.menu?.modes?.first { it.id == "miss_sweep" }
        assertNull("an unavailable mode does not start", locked?.action)
    }

    @Test
    fun anActiveUnansweredQuestionMapsToTheRound() {
        val screen = DesktopGamesModel.screen(
            render(data = emptyData(), mode = KanjiGameEngine.GameMode.MEANING_POP, question = question()),
        )

        assertEquals(GamesState.ROUND, screen.state)
        assertEquals(listOf("take off", "explain"), screen.round?.choices?.map { it.value })
        assertEquals("脱", screen.round?.prompt)
    }

    @Test
    fun anansweredQuestionMapsToTheResult() {
        val screen = DesktopGamesModel.screen(
            render(
                data = emptyData(),
                mode = KanjiGameEngine.GameMode.MEANING_POP,
                question = question(),
                lastSelected = "explain",
                lastCorrect = false,
            ),
        )

        assertEquals(GamesState.RESULT, screen.state)
        assertEquals(false, screen.result?.correct)
        // A wrong answer shows the correct answer; play-again advances the runtime.
        assertTrue(screen.result?.answer?.isNotBlank() == true)
        assertEquals(KaniAction.Game.Continue, screen.result?.primaryAction)
    }

    private fun render(
        data: HomeGameDataSnapshot?,
        available: List<KanjiGameEngine.GameMode> = emptyList(),
        mode: KanjiGameEngine.GameMode? = null,
        question: KanjiGameEngine.GameQuestion? = null,
        lastSelected: String? = null,
        lastCorrect: Boolean = false,
    ) = GamesRender(
        data = data,
        availableModes = available,
        mode = mode,
        round = KanjiGameRoundState.newRound(10),
        question = question,
        lastCorrect = lastCorrect,
        lastSelected = lastSelected,
    )

    private fun emptyData() = HomeGameDataSnapshot(emptyList(), emptyList(), emptyList())

    private fun question() = KanjiGameEngine.GameQuestion(
        KanjiGameEngine.GameMode.MEANING_POP,
        "脱",
        "脱",
        "Choose the meaning.",
        "take off",
        listOf("take off", "explain"),
        "脱 means take off.",
    )
}
