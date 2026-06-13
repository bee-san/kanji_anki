package dev.bee.kanjianki

import dev.bee.kanjianki.core.KanjiGameEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityGamesRoundComposeTest {
    @Test
    fun gameModeColorUsesSharedGameAccentTokens() {
        assertEquals(LightKaniColors.coral, gameModeColor(LightKaniColors, KanjiGameEngine.GameMode.MEANING_POP))
        assertEquals(LightKaniColors.teal, gameModeColor(LightKaniColors, KanjiGameEngine.GameMode.READING_RUSH))
        assertEquals(LightKaniColors.blue, gameModeColor(LightKaniColors, KanjiGameEngine.GameMode.CONFUSABLE_CLASH))
    }

    @Test
    fun gameModeColorFollowsDarkTheme() {
        assertEquals(DarkKaniColors.coral, gameModeColor(DarkKaniColors, KanjiGameEngine.GameMode.MEANING_POP))
    }
}
