package dev.bee.kanjianki.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsSchedulerModels
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalStoreLearningStepSettingsTest {
    private lateinit var context: Context
    private var store: LocalStore? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        store = LocalStore(context)
    }

    @After
    fun tearDown() {
        store?.close()
        store = null
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @Test
    fun learningStepSettingsDefaultToAnkiStyleNewAndRelearningDelays() {
        val settings = store!!.learningStepSettings()

        assertEquals(listOf(1, 10), settings.newStepsMinutes)
        assertEquals(listOf(10), settings.reviewStepsMinutes)
        assertEquals("1m, 10m", settings.newStepsText())
        assertEquals("10m", settings.reviewStepsText())
    }

    @Test
    fun saveLearningStepSettingsPreservesExplicitEmptyRelearningStepsThroughReopen() {
        store!!.saveLearningStepSettings(
            RecordsSchedulerModels.LearningStepSettings(
                listOf(2, 15),
                emptyList<Int>(),
            ),
        )

        assertEquals("2m, 15m", store!!.getStringSetting("new_learning_steps_minutes", "fallback"))
        assertEquals("", store!!.getStringSetting("review_relearning_steps_minutes", "fallback"))

        store!!.close()
        store = LocalStore(context)

        val reloaded = store!!.learningStepSettings()
        assertEquals(listOf(2, 15), reloaded.newStepsMinutes)
        assertEquals(emptyList<Int>(), reloaded.reviewStepsMinutes)
        assertEquals("", reloaded.reviewStepsText())
    }

    @Test
    fun invalidStoredLearningStepSettingsFallbackToDefaults() {
        store!!.putStringSetting("new_learning_steps_minutes", "soon")
        store!!.putStringSetting("review_relearning_steps_minutes", "later")

        val settings = store!!.learningStepSettings()

        assertEquals(listOf(1, 10), settings.newStepsMinutes)
        assertEquals(listOf(10), settings.reviewStepsMinutes)
    }
}
