package dev.bee.kanjianki.games

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Runs the shared Games render assertions on the Android host target. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GamesAndroidRenderTest {
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
