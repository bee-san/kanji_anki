package dev.bee.kanjianki.presentation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeDashboardTest {
    @Test
    fun aFocusCardOpensItsOwnKanjiAndNothingElse() {
        // Derived rather than passed, because the Android model took an `onClick`
        // lambda that every call site built by hand — and one built with the wrong
        // kanji would have compiled and opened the wrong card.
        val card = FocusCard(kanji = "脱", meaning = UiText.Literal("take off"))

        assertEquals(
            KaniAction.Navigation.Open(KaniDestination.Detail(kanji = "脱")),
            card.action,
        )
    }

    @Test
    fun aFocusCardAboutNoKanjiCannotBeConstructed() {
        assertFailsWith<IllegalArgumentException> {
            FocusCard(kanji = " ", meaning = UiText.EMPTY)
        }
    }

    @Test
    fun aFocusCardsBadgesCarryMeaningRatherThanAColour() {
        // The Android model carried a packed ARGB per badge, so the model had already
        // chosen a hue from a palette the active theme might not use. These say what
        // the badge is about and let the theme decide how to draw it.
        val card = FocusCard(
            kanji = "脱",
            meaning = UiText.Literal("take off"),
            tags = listOf(
                FocusTag(UiText.Literal("recognition")),
                FocusTag(UiText.Literal("relearning"), accent = HomeAccent.LEARNING),
            ),
            accent = HomeAccent.DUE,
        )

        assertEquals(
            listOf(HomeAccent.NEUTRAL, HomeAccent.LEARNING),
            card.tags.map { it.accent },
        )
        assertEquals(HomeAccent.DUE, card.accent)
    }

    @Test
    fun anEmptyQueueOnAnEmptyCollectionAsksForASyncAndOnAFullOneDoesNot() {
        // The distinction the Android code rebuilt at four separate call sites by
        // comparing two different list emptiness checks.
        val neverSynced = FocusQueue(hasImportedKanji = false)
        val nothingActive = FocusQueue(hasImportedKanji = true)

        assertEquals(FocusEmptyReason.NOTHING_IMPORTED, neverSynced.emptyReason)
        assertEquals(FocusEmptyReason.NOTHING_ACTIVE, nothingActive.emptyReason)
        assertFalse(neverSynced.showsViewAll)
        assertFalse(nothingActive.showsViewAll)
    }

    @Test
    fun aQueueWithCardsHasNoEmptyReasonAndOffersTheFullList() {
        val queue = FocusQueue(cards = listOf(FocusCard("脱", UiText.Literal("take off"))))

        assertNull(queue.emptyReason)
        assertTrue(queue.showsViewAll)
        assertTrue(queue.hasImportedKanji, "cards on screen are imported kanji by definition")
        assertEquals(
            KaniAction.Navigation.Open(KaniDestination.FocusQueue),
            queue.viewAllAction,
        )
    }

    @Test
    fun everyRecommendationMapsToTheOneActionThatWouldHelp() {
        // Exhaustive, because the failure this prevents is a Today card whose button
        // says "Study now" and starts a sync. Android built these as three separate
        // lambdas chosen by a `when`, so nothing checked the pairing.
        assertEquals(
            listOf<KaniAction?>(
                KaniAction.Navigation.Open(KaniDestination.Study),
                KaniAction.Provider.RequestSync,
                null,
                null,
            ),
            HomeRecommendation.entries.map { it.action },
        )
    }

    @Test
    fun theTodayCardRecommendsAConfirmationRatherThanStartingASync() {
        // Every sync in Kani is user-confirmed; a Home button that started one
        // directly would bypass the repaired-tag confirmation entirely.
        assertEquals(KaniAction.Provider.RequestSync, HomeRecommendation.SYNC_FIRST.action)
    }

    @Test
    fun aTodayCardWithNothingToSayReportsItselfEmptyRatherThanRenderingABlankBox() {
        assertTrue(TodayPlan(HomeRecommendation.NOTHING_USEFUL_NOW).isEmpty)
        assertTrue(TodayPlan(HomeRecommendation.WAIT_UNTIL_LATER).isEmpty)
    }

    @Test
    fun aTodayCardIsNotEmptyWhenItHasAnyOfSummaryDetailsOrAnAction() {
        val recommendation = HomeRecommendation.NOTHING_USEFUL_NOW
        assertFalse(
            TodayPlan(recommendation, summary = UiText.Literal("all caught up")).isEmpty,
        )
        assertFalse(
            TodayPlan(recommendation, details = listOf(UiText.Literal("12 resting"))).isEmpty,
        )
        assertFalse(
            TodayPlan(HomeRecommendation.STUDY_NOW).isEmpty,
            "a card with a button is never empty, whatever else it lacks",
        )
    }

    @Test
    fun homeAsksForASyncUntilSomethingHasBeenImportedAndThenOffersStudy() {
        val fresh = HomeDashboard()
        val imported = HomeDashboard(focus = FocusQueue(hasImportedKanji = true))

        assertTrue(fresh.needsFirstSync)
        assertEquals(KaniAction.Provider.RequestSync, fresh.primaryAction)
        assertFalse(imported.needsFirstSync)
        assertEquals(
            KaniAction.Navigation.Open(KaniDestination.Study),
            imported.primaryAction,
        )
    }

    @Test
    fun aFreshHomeAssumesNothingAboutTheCollection() {
        val home = HomeDashboard()

        assertEquals(ProviderReadiness.ABSENT, home.readiness)
        assertEquals(emptyList(), home.metrics)
        assertNull(home.todayPlan)
        assertFalse(home.syncing)
        assertEquals(0, home.repairedKanjiCount)
    }

    @Test
    fun negativeCountsAreRejectedAtConstructionRatherThanRenderedAsMinusOne() {
        assertFailsWith<IllegalArgumentException> { HomeDashboard(repairedKanjiCount = -1) }
        assertFailsWith<IllegalArgumentException> { HomeDashboard(studyRemainingCount = -1) }
    }

    @Test
    fun onlyTheTilesThatDoSomethingCarryAnAction() {
        // Per-tile rather than "the first one is a button", so a screen reader is told
        // which tiles are actionable and this test can prove the streak tile is not.
        val sync = HomeMetric(
            kind = HomeMetricKind.SYNC,
            value = UiText.Literal("Tap to sync"),
            accent = HomeAccent.DUE,
            action = KaniAction.Provider.RequestSync,
        )
        val streak = HomeMetric(kind = HomeMetricKind.STREAK, value = UiText.Literal("4"))

        assertEquals(KaniAction.Provider.RequestSync, sync.action)
        assertNull(streak.action)
    }
}
