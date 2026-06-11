package dev.bee.kanjianki

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.HomeTextCopy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyLadderRules
import dev.bee.kanjianki.core.StudyRatings
import dev.bee.kanjianki.data.StudyStatsStore
import dev.bee.kanjianki.anki.AnkiDroidGateway
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityHomeFocusQueueCoverageTest {
    @Test
    fun homeFocusQueuePanelModelUsesEmptyCopyWhenRowsAreMissing() {
        val clicked = mutableListOf<String>()

        val model = homeFocusQueuePanelModel(
            rows = emptyList(),
            entries = emptyList(),
            nowMillis = NOW_MILLIS,
            plan = null,
            matureSupportThreshold = 3,
            onCardClick = { clicked += it },
        )

        assertEquals(HomeTextCopy.noKanjiQueuedTitle(), model.emptyTitle)
        assertEquals(HomeTextCopy.focusQueueNoKanjiQueuedBody(), model.emptyBody)
        assertTrue(model.showSyncButton)
        assertTrue(model.cards.isEmpty())
        assertTrue(clicked.isEmpty())
    }

    @Test
    fun homeFocusQueuePanelModelBuildsCardsAndForwardsClickCallbacks() {
        val rows = listOf(sampleRow("字A"), sampleRow("字B"))
        val entries = listOf(
            MainActivityBase.QueueEntry(
                sampleRow("字A"),
                sampleItem("字A", RecordsBase.SchedulerPhase.RELEARNING, totalReviews = 0),
            ),
            MainActivityBase.QueueEntry(
                sampleRow("字B"),
                sampleItem("字B", RecordsBase.SchedulerPhase.NEW_LEARNING, totalReviews = 2),
            ),
        )
        val clicked = mutableListOf<String>()

        val model = homeFocusQueuePanelModel(
            rows = rows,
            entries = entries,
            nowMillis = NOW_MILLIS,
            plan = samplePlan(),
            matureSupportThreshold = 7,
            onCardClick = { clicked += it },
        )

        assertFalse(model.showSyncButton)
        assertNotNull(model.planText)
        assertEquals(2, model.cards.size)
        assertEquals("字A", model.cards[0].kanji)
        assertEquals("字B", model.cards[1].kanji)
        assertTrue(model.cards[0].tags.any { it.label == HomeTextCopy.relearningChipLabel() })
        assertTrue(model.cards[1].tags.any { it.label == HomeTextCopy.deckOverviewLearningLabel() })

        model.cards[0].onClick()
        model.cards[1].onClick()
        assertEquals(listOf("字A", "字B"), clicked)
    }

    @Test
    fun wrapperFunctionsDelegateToHomeModelFactories() {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        try {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val intent = Intent(context, MainActivity::class.java).apply {
                putExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE, MainActivityBase.NAV_HOME_ROUTE)
            }
            val activity = Robolectric.buildActivity(MainActivity::class.java, intent)
                .create()
                .start()
                .resume()
                .get()

            activity.cancelPendingHomeRouteLoads()
            activity.intent.removeExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE)

            val focusCard = homeFocusQueueCardModel(
                home = activity,
                entry = MainActivityBase.QueueEntry(
                    sampleRow("字C"),
                    sampleItem("字C", RecordsBase.SchedulerPhase.REVIEW, totalReviews = 5),
                ),
                nowMillis = NOW_MILLIS,
                matureSupportThreshold = 4,
            )
            assertEquals("字C", focusCard.kanji)
            assertTrue(focusCard.tags.isNotEmpty())
            assertEquals("字C", focusCard.kanji)

            val recentPanel = homeRecentMistakesPanelModel(
                home = activity,
                mistakes = listOf(StudyStatsStore.RecentMistake("字D", StudyRatings.AGAIN, NOW_MILLIS)),
                rowsByKanji = mapOf("字D" to sampleRow("字D")),
            )
            assertEquals(1, recentPanel.cards.size)
            assertEquals("字D", recentPanel.cards.single().kanji)
            assertTrue(recentPanel.cards.single().sourceEvidence?.isNotBlank() == true)
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    private companion object {
        private const val NOW_MILLIS = 1_725_000_000_000L

        fun sampleRow(kanji: String): RecordsImportModels.DashboardRow {
            return RecordsImportModels.DashboardRow(
                kanji,
                1,
                "meaning-$kanji",
                "reading-$kanji",
                "search-$kanji",
                10,
                "reason-$kanji",
                "reason text $kanji",
                2,
                0,
                3,
                listOf(example("example-$kanji")),
            )
        }

        fun sampleItem(
            kanji: String,
            phase: RecordsBase.SchedulerPhase,
            totalReviews: Int,
        ): RecordsStudyModels.StudyItem {
            return RecordsStudyModels.StudyItem(
                kanji,
                StudyLadderRules.STATE_REVIEW,
                NOW_MILLIS,
                1.0,
                2.0,
                1,
                0,
                0,
                0,
                "",
                NOW_MILLIS,
            )
                .copyBuilder()
                .rung(RecordsBase.LadderRung.KANJI_MEANING)
                .phase(phase)
                .totalReviews(totalReviews)
                .activeToken("token-$kanji")
                .build()
        }

        fun samplePlan(): RecordsSchedulerModels.AdaptiveLoadPlan {
            return RecordsSchedulerModels.AdaptiveLoadPlan(
                workloadPercent = 55,
                target = 48,
                remaining = 18,
                focusKanji = listOf("字A", "字B"),
                newAdmissionLimit = 12,
                allKanjiMode = false,
                status = "active",
            )
        }

        fun example(expression: String): RecordsImportModels.Example {
            return RecordsImportModels.Example(
                "active",
                1L,
                2L,
                expression,
                "reading",
                "meaning",
                "",
                false,
                0,
            )
        }

        fun fakeAnkiDroidGateway(): AnkiDroidGateway {
            val constructor = AnkiDroidGateway::class.java.getDeclaredConstructor(
                Context::class.java,
                List::class.java,
            )
            constructor.isAccessible = true
            return constructor.newInstance(
                ApplicationProvider.getApplicationContext<Context>(),
                emptyList<Any>(),
            ) as AnkiDroidGateway
        }
    }
}
