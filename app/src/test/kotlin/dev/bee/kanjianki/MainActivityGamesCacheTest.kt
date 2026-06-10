package dev.bee.kanjianki

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.KanjiGameEngine
import dev.bee.kanjianki.core.RecordsImportModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityGamesCacheTest {
    @Test
    fun startGameReusesMenuSnapshotWithoutReloadingGameData() {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        try {
            val activity = Robolectric.buildActivity(CountingGamesActivity::class.java)
                .create()
                .start()
                .resume()
                .get()

            val before = activity.gameDataCalls
            val model = activity.gamesScreenModel()
            assertTrue(model.modeCards.isNotEmpty())
            assertEquals(before + 1, activity.gameDataCalls)

            activity.startGame(KanjiGameEngine.GameMode.MEANING_POP)

            assertEquals(before + 1, activity.gameDataCalls)
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    @Test
    fun gamesScreenModelUsesJapaneseModeTitles() {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        try {
            withLocale(Locale.JAPAN) {
                val activity = Robolectric.buildActivity(CountingGamesActivity::class.java)
                    .create()
                    .start()
                    .resume()
                    .get()

                val model = activity.gamesScreenModel()

                assertEquals("意味ポップ", model.modeCards[0].title)
                assertEquals("読みラッシュ", model.modeCards[1].title)
                assertEquals("似た漢字バトル", model.modeCards[2].title)
            }
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    private inline fun <T> withLocale(locale: Locale, block: () -> T): T {
        val original = Locale.getDefault()
        Locale.setDefault(locale)
        return try {
            block()
        } finally {
            Locale.setDefault(original)
        }
    }

    private class CountingGamesActivity : MainActivity() {
        var gameDataCalls = 0

        override fun gameData(): MainActivityGames.GameData {
            gameDataCalls++
            return MainActivityGames.GameData(
                rows = listOf(
                    dashboardRow("裂", "split", "れつ"),
                    dashboardRow("提", "present", "てい"),
                ),
                inventory = emptyList(),
                pairs = emptyList(),
            )
        }
    }
}

private fun dashboardRow(kanji: String, meaning: String, reading: String): RecordsImportModels.DashboardRow {
    return RecordsImportModels.DashboardRow(
        kanji,
        100,
        meaning,
        reading,
        kanji,
        7,
        "reason",
        "reason text",
        1,
        0,
        0,
        listOf(example(kanji + "語", reading, meaning)),
    )
}

private fun example(expression: String, reading: String, meaning: String): RecordsImportModels.Example {
    return RecordsImportModels.Example("active", 1L, 2L, expression, reading, meaning, "", false, 0)
}

private fun fakeAnkiDroidGateway(): AnkiDroidGateway {
    val constructor = AnkiDroidGateway::class.java.getDeclaredConstructor(Context::class.java, List::class.java)
    constructor.isAccessible = true
    return constructor.newInstance(
        ApplicationProvider.getApplicationContext<Context>(),
        emptyList<Any>(),
    ) as AnkiDroidGateway
}
