package dev.bee.kanjianki.hostpresentation

import dev.bee.kanjianki.core.KaniThemeChoice
import dev.bee.kanjianki.presentation.GamesScreen
import dev.bee.kanjianki.presentation.GamesState
import dev.bee.kanjianki.presentation.HomeDashboard
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class KaniRouteContentTest {
    @Test
    fun aMinimalRouteDefaultsEveryOptionalSurfaceToEmpty() {
        val content = KaniRouteContent(
            providerMessage = "ready",
            studyItemCount = 3,
            dueCount = 1,
            themeChoice = KaniThemeChoice.GIRLYPOP,
        )

        assertEquals("ready", content.providerMessage)
        assertEquals(3, content.studyItemCount)
        assertEquals(1, content.dueCount)
        assertEquals(KaniThemeChoice.GIRLYPOP, content.themeChoice)
        // The Home models default so a non-Home route needs no dashboard, and every
        // per-route surface is absent until its route fills it.
        assertEquals(HomeDashboard(), content.home)
        assertNull(content.detail)
        assertNull(content.study)
        assertNull(content.stats)
        assertNull(content.games)
        assertNull(content.settings)
        // A default onboarding plan is buildable without a binding of its own.
        assertEquals("", content.onboarding.binding.browserQuery)
        assertEquals(0, content.browse.rows.size)
    }

    @Test
    fun aRouteCarriesWhicheverSurfaceItDraws() {
        val games = GamesScreen(state = GamesState.UNAVAILABLE)
        val content = KaniRouteContent(
            providerMessage = "",
            studyItemCount = 0,
            dueCount = 0,
            themeChoice = KaniThemeChoice.DARK,
            games = games,
        )

        assertEquals(games, content.games)
        assertEquals(KaniThemeChoice.DARK, content.themeChoice)
    }
}
