package dev.bee.kanjianki

import dev.bee.kanjianki.core.KanjiGameEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityGamesRoundComposeTest {
    @Test
    fun gameModeColorUsesSharedGameAccentTokens() {
        assertEquals(GamesCoral, gameModeColor(KanjiGameEngine.GameMode.MEANING_POP))
        assertEquals(GamesTeal, gameModeColor(KanjiGameEngine.GameMode.READING_RUSH))
        assertEquals(GamesBlue, gameModeColor(KanjiGameEngine.GameMode.CONFUSABLE_CLASH))
    }
}
