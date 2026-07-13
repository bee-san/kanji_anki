package dev.bee.kanjianki

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StudyPendingAnswerStoreTest {
    @Test
    fun pendingAppliedAnswerSurvivesStoreRecreationUntilExplicitClear() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = context.getSharedPreferences("pending-answer-test", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        val snapshot = StudyPendingAnswerSnapshot(
            feedback = StudyAnswerFeedbackSnapshot(
                sessionToken = "token-裂",
                phase = StudyAnswerFeedbackPhase.APPLIED,
                outcome = StudyAnswerOutcome.INCORRECT,
                selectedAnswer = "列",
            ),
            kanji = "裂",
            taskType = "meaning_kanji",
            writingRequired = false,
            prompt = "Which kanji means split?",
        )

        StudyPendingAnswerStore(preferences).save(snapshot)

        val recreatedStore = StudyPendingAnswerStore(preferences)
        assertEquals(snapshot, recreatedStore.read())
        recreatedStore.clear()
        assertNull(recreatedStore.read())
    }
}
