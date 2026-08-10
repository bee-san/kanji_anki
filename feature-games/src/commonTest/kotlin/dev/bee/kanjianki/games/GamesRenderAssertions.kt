package dev.bee.kanjianki.games

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import dev.bee.kanjianki.presentation.GamesScreen
import dev.bee.kanjianki.presentation.GamesState
import dev.bee.kanjianki.presentation.KaniAction
import kotlin.test.assertEquals

/**
 * The games surface's render assertions, run on both hosts.
 *
 * Structure and which action each control dispatches — starting a mode, answering a
 * round, playing again. The engine's scoring is `:core`'s and is tested there.
 */
@OptIn(ExperimentalTestApi::class)
internal fun assertStartingAModeDispatchesItsId() {
    val recorded = mutableListOf<KaniAction>()
    renderGames(
        content = { GamesScreenView(menuScreen(), gamesCopy(), dispatch = { recorded += it }) },
    ) {
        onNodeWithTag(gamesModeTestTag("meaning_pop")).performScrollTo().performClick()
        assertEquals(listOf<KaniAction>(KaniAction.Game.Start(modeId = "meaning_pop")), recorded)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertAnUnavailableModeDispatchesNothing() {
    val recorded = mutableListOf<KaniAction>()
    renderGames(
        content = { GamesScreenView(menuScreen(), gamesCopy(), dispatch = { recorded += it }) },
    ) {
        onNodeWithTag(gamesModeTestTag("miss_sweep")).assertIsNotEnabled()
        onNodeWithTag(gamesModeTestTag("miss_sweep")).performScrollTo().performClick()
        assertEquals(emptyList<KaniAction>(), recorded)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheEmptyMenuOffersSync() {
    val recorded = mutableListOf<KaniAction>()
    renderGames(
        content = { GamesScreenView(menuScreen(needsSync = true, empty = true), gamesCopy(), dispatch = { recorded += it }) },
    ) {
        onNodeWithTag(GAMES_SYNC_TEST_TAG).performScrollTo().performClick()
        assertEquals(listOf<KaniAction>(KaniAction.Provider.RequestSync), recorded)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertPickingAChoiceAnswersTheRound() {
    val recorded = mutableListOf<KaniAction>()
    renderGames(
        content = { GamesScreenView(roundScreen(), gamesCopy(), dispatch = { recorded += it }) },
    ) {
        onNodeWithTag(gamesChoiceTestTag("explain")).performScrollTo().performClick()
        assertEquals(listOf<KaniAction>(KaniAction.Game.Answer(answer = "explain")), recorded)
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheScoreStripIsAnnouncedAsOneSentence() {
    renderGames(
        content = { GamesScreenView(roundScreen(), gamesCopy(), dispatch = {}) },
    ) {
        assertEquals(
            "Round 3 of 10, score 40, streak 3.",
            onNodeWithTag(GAMES_SCORE_TEST_TAG).contentDescriptionOrEmpty(),
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheResultShowsTheAnswerAndPlaysAgain() {
    val recorded = mutableListOf<KaniAction>()
    renderGames(
        content = { GamesScreenView(resultScreen(correct = false), gamesCopy(), dispatch = { recorded += it }) },
    ) {
        onNodeWithTag(GAMES_RESULT_PRIMARY_TEST_TAG).performScrollTo().performClick()
        assertEquals(listOf<KaniAction>(KaniAction.Game.Continue), recorded)
    }

    renderGames(
        content = { GamesScreenView(resultScreen(correct = true), gamesCopy(), dispatch = {}) },
    ) {
        onNodeWithTag(GAMES_RESULT_TEST_TAG).assertIsDisplayed()
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheUnavailableStateNamesItsCause() {
    val copy = gamesCopy()
    renderGames(
        content = { GamesScreenView(GamesScreen(state = GamesState.UNAVAILABLE), copy, dispatch = {}) },
    ) {
        assertEquals(
            "${copy.unavailableTitle}. ${copy.unavailableBody}",
            onNodeWithTag(GAMES_UNAVAILABLE_TEST_TAG).contentDescriptionOrEmpty(),
        )
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheShippedGamesResourcesResolveOnThisHost() {
    var text = ""
    renderGames(
        content = {
            val copy = rememberGamesCopy()
            text = copy.sync + copy.unavailableTitle + copy.unavailableBody
        },
    ) {
        assertEquals(false, text.isBlank())
    }
}

@OptIn(ExperimentalTestApi::class)
internal fun assertTheGamesTestTagsAreDistinct() {
    val tags = listOf(
        GAMES_SCREEN_TEST_TAG,
        GAMES_MENU_TEST_TAG,
        GAMES_SYNC_TEST_TAG,
        GAMES_ROUND_TEST_TAG,
        GAMES_SCORE_TEST_TAG,
        GAMES_RESULT_TEST_TAG,
        GAMES_RESULT_PRIMARY_TEST_TAG,
        GAMES_UNAVAILABLE_TEST_TAG,
    ) + listOf("meaning_pop", "miss_sweep").map(::gamesModeTestTag) +
        listOf("take off", "explain").map(::gamesChoiceTestTag)
    assertEquals(tags.size, tags.distinct().size, "tags must be unique: $tags")
    assertEquals("kani-games-mode-meaning_pop", gamesModeTestTag("meaning_pop"))
}
