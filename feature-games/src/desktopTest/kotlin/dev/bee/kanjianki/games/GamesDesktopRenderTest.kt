package dev.bee.kanjianki.games

import kotlin.test.Test

/** Runs the shared Games render assertions on the desktop JVM; the Android twin runs the identical list. */
class GamesDesktopRenderTest {
    @Test
    fun startingAModeDispatchesItsId() {
        assertStartingAModeDispatchesItsId()
    }

    @Test
    fun anUnavailableModeDispatchesNothing() {
        assertAnUnavailableModeDispatchesNothing()
    }

    @Test
    fun theEmptyMenuOffersSync() {
        assertTheEmptyMenuOffersSync()
    }

    @Test
    fun pickingAChoiceAnswersTheRound() {
        assertPickingAChoiceAnswersTheRound()
    }

    @Test
    fun theScoreStripIsAnnouncedAsOneSentence() {
        assertTheScoreStripIsAnnouncedAsOneSentence()
    }

    @Test
    fun theResultShowsTheAnswerAndPlaysAgain() {
        assertTheResultShowsTheAnswerAndPlaysAgain()
    }

    @Test
    fun theUnavailableStateNamesItsCause() {
        assertTheUnavailableStateNamesItsCause()
    }

    @Test
    fun theShippedGamesResourcesResolveOnThisHost() {
        assertTheShippedGamesResourcesResolveOnThisHost()
    }

    @Test
    fun theGamesTestTagsAreDistinct() {
        assertTheGamesTestTagsAreDistinct()
    }
}
