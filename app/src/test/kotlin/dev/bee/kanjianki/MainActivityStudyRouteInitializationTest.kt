package dev.bee.kanjianki

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyTaskTypes
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityStudyRouteInitializationTest {
    @Test
    fun flashcardRouteStateIsInitializedAfterRoutePreparation() {
        val activity = createActivity()
        val session = flashcardSession()
        activity.activeSession = session

        activity.renderComposeFlashcardSession(session)
        shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(activity.flashcardRevealState)
        assertNotNull(activity.flashcardActionBarState)
    }

    @Test
    fun writingRouteStateIsInitializedAfterRoutePreparation() {
        val activity = createActivity()
        val session = writingSession()
        activity.activeSession = session

        activity.renderComposeWritingSession(session)
        shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(activity.writingAnswerPanelState)
    }

    private fun createActivity(): MainActivity {
        MainActivityRuntimeOverrides.setAnkiDroidGateway(fakeAnkiDroidGateway())
        return try {
            Robolectric.buildActivity(MainActivity::class.java).create().start().resume().get().also { activity ->
                activity.cancelPendingHomeRouteLoads()
                activity.intent.removeExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE)
            }
        } finally {
            MainActivityRuntimeOverrides.setAnkiDroidGateway(null)
        }
    }

    private fun flashcardSession(): RecordsSchedulerModels.StudySession {
        return RecordsSchedulerModels.StudySession(
            item = studyItem("弱", "flashcard-token"),
            row = null,
            token = "flashcard-token",
            taskType = StudyTaskTypes.KANJI_MEANING,
            writingRequired = false,
            prompt = "prompt text",
        )
    }

    private fun writingSession(): RecordsSchedulerModels.StudySession {
        return RecordsSchedulerModels.StudySession(
            item = studyItem("書", "writing-token"),
            row = null,
            token = "writing-token",
            taskType = StudyTaskTypes.WRITE_KANJI,
            writingRequired = true,
            prompt = "prompt text",
        )
    }

    private fun studyItem(kanji: String, token: String): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(kanji, "review", 1_000L, 1.0, 2.0, 1, 0, 0, 0, "", 1_000L)
            .copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .activeToken(token)
            .build()
    }

    private fun fakeAnkiDroidGateway(): AnkiDroidGateway {
        val constructor = AnkiDroidGateway::class.java.getDeclaredConstructor(Context::class.java, List::class.java)
        constructor.isAccessible = true
        return constructor.newInstance(
            ApplicationProvider.getApplicationContext<Context>(),
            emptyList<Any>(),
        ) as AnkiDroidGateway
    }
}
