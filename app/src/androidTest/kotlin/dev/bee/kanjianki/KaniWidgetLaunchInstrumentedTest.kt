package dev.bee.kanjianki

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.KanjiInventoryBuilder
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.widget.KaniWidgetSnapshot
import dev.bee.kanjianki.widget.KaniWidgetState
import dev.bee.kanjianki.widget.kaniFocusDetailIntent
import dev.bee.kanjianki.widget.kaniWidgetLaunchIntent
import dev.bee.kanjianki.widget.kaniWidgetStatsIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KaniWidgetLaunchInstrumentedTest {
    @Test
    fun openStudyExtraLandsOnStudyRoute() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = coldStartIntent(
            Intent(context, MainActivity::class.java)
                .putExtra(MainActivityBase.EXTRA_OPEN_STUDY, true),
        )

        withScenario(intent) { scenario ->
            val state = awaitState(scenario) { it.route == MainActivityBase.NAV_STUDY }
            assertEquals(MainActivityBase.NAV_STUDY, state.route)
        }
    }

    @Test
    fun dueWidgetTapReusesWarmActivityAndOpensStudyRoute() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        withScenario(coldStartIntent(Intent(context, MainActivity::class.java))) { scenario ->
            val originalActivityId = awaitState(scenario).activityId
            scenario.onActivity { activity ->
                activity.startActivity(
                    kaniWidgetLaunchIntent(
                        activity,
                        KaniWidgetSnapshot(KaniWidgetState.DUE_NOW, dueCount = 1),
                    ),
                )
            }

            val state = awaitState(scenario) { it.route == MainActivityBase.NAV_STUDY }
            assertEquals(MainActivityBase.NAV_STUDY, state.route)
            assertEquals(originalActivityId, state.activityId)

            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            val backState = awaitState(scenario) { it.route == MainActivityBase.NAV_HOME_ROUTE }
            assertEquals(originalActivityId, backState.activityId)
        }
    }

    @Test
    fun coldAndWarmStatsWidgetTapsOpenStatsWithoutDuplicatingActivity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        withScenario(coldStartIntent(kaniWidgetStatsIntent(context))) { scenario ->
            val state = awaitState(scenario) { it.route == MainActivityBase.NAV_STATS_ROUTE }
            assertEquals(MainActivityBase.NAV_STATS_ROUTE, state.route)
        }

        withScenario(coldStartIntent(Intent(context, MainActivity::class.java))) { scenario ->
            val originalId = awaitState(scenario).activityId
            scenario.onActivity { it.startActivity(kaniWidgetStatsIntent(it)) }
            val state = awaitState(scenario) { it.route == MainActivityBase.NAV_STATS_ROUTE }
            assertEquals(MainActivityBase.NAV_STATS_ROUTE, state.route)
            assertEquals(originalId, state.activityId)

            scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
            val backState = awaitState(scenario) { it.route == MainActivityBase.NAV_HOME_ROUTE }
            assertEquals(originalId, backState.activityId)
        }
    }

    @Test
    fun coldAndWarmFocusWidgetTapsOpenExactBrowseDetailWithoutDuplicatingActivity() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val insertedFixture = ensureFocusInventoryFixture(context, "学")
        try {
            withScenario(coldStartIntent(kaniFocusDetailIntent(context, "学"))) { scenario ->
                val state = awaitState(scenario) { it.browseQuery.isEmpty() && it.allKanji }
                assertEquals("", state.browseQuery)
                assertExactKanjiIsVisible("学")
            }

            withScenario(coldStartIntent(Intent(context, MainActivity::class.java))) { scenario ->
                val originalId = awaitState(scenario).activityId
                scenario.onActivity { it.startActivity(kaniFocusDetailIntent(it, "学")) }
                val state = awaitState(scenario) { it.browseQuery.isEmpty() && it.allKanji }
                assertEquals("", state.browseQuery)
                assertExactKanjiIsVisible("学")
                assertEquals(originalId, state.activityId)

                scenario.onActivity { it.onBackPressedDispatcher.onBackPressed() }
                assertTextIsVisible(HomeTextCopy.browseTitle())
                assertEquals(originalId, awaitState(scenario).activityId)
            }
        } finally {
            if (insertedFixture) removeFocusInventoryFixture(context, "学")
        }
    }

    private fun ensureFocusInventoryFixture(context: Context, kanji: String): Boolean {
        LocalStore(context).use { store ->
            if (store.inventoryItemForKanji(kanji) != null) return false
            val inventory = KanjiInventoryBuilder(
                System.currentTimeMillis(),
                RecordsSyncModels.Settings.kikuDefaults(),
            )
            inventory.addSourceText(listOf(kanji), "study", "がく", kanji, "")
            store.writeKanjiInventory(store.writableDatabase, inventory)
        }
        return true
    }

    private fun removeFocusInventoryFixture(context: Context, kanji: String) {
        LocalStore(context).use { store ->
            store.writableDatabase.delete(
                LocalStoreBase.TABLE_KANJI_INVENTORY,
                "${LocalStoreBase.COLUMN_KANJI}=?",
                arrayOf(kanji),
            )
        }
    }

    private fun assertExactKanjiIsVisible(kanji: String) {
        assertTextIsVisible(
            text = kanji,
            message = "Expected exact focus kanji $kanji to be visible",
            timeoutMillis = 15_000L,
        )
    }

    private fun assertTextIsVisible(
        text: String,
        message: String = "Expected $text to be visible",
        timeoutMillis: Long = 5_000L,
    ) {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        assertTrue(
            message,
            device.wait(Until.hasObject(By.text(text)), timeoutMillis),
        )
    }

    private fun coldStartIntent(intent: Intent): Intent = intent.apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }

    private fun withScenario(
        intent: Intent,
        block: (ActivityScenario<MainActivity>) -> Unit,
    ) {
        val scenario = ActivityScenario.launch<MainActivity>(intent)
        try {
            block(scenario)
        } finally {
            // ActivityScenario.close() waits for DESTROYED, but API 35 can leave a singleTop
            // activity reported as PAUSED after its task is removed. Finish the owned task on
            // main; every scenario starts with CLEAR_TASK, so no test inherits stale state.
            runCatching {
                scenario.onActivity { it.finishAndRemoveTask() }
            }
        }
    }

    private fun awaitState(
        scenario: ActivityScenario<MainActivity>,
        ready: (ActivityState) -> Boolean = { true },
    ): ActivityState {
        var state = ActivityState()
        repeat(100) {
            scenario.onActivity { activity ->
                state = ActivityState(
                    route = activity.currentRoute,
                    browseQuery = activity.activeBrowseQuery,
                    allKanji = activity.activeBrowseAllKanji,
                    activityId = System.identityHashCode(activity),
                )
            }
            if (ready(state)) return state
            Thread.sleep(50L)
        }
        return state
    }

    private data class ActivityState(
        val route: String = "",
        val browseQuery: String = "",
        val allKanji: Boolean = false,
        val activityId: Int = 0,
    )
}
