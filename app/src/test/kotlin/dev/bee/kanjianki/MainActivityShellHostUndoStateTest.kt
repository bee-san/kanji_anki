package dev.bee.kanjianki

import android.os.Bundle
import dev.bee.kanjianki.core.RecordsStudyModels
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityShellHostUndoStateTest {
    @Test
    fun studyRoutePreparationKeepsUndoStateButHomePreparationClearsIt() {
        val activity = Robolectric.buildActivity(ShellHostTestActivity::class.java)
            .create()
            .get()
        val host = MainActivityShellHost(activity)
        val firstSnapshot = snapshot("語", 1, 2)
        val secondSnapshot = snapshot("字", 3, 4)

        activity.studyUndoState.capture(firstSnapshot, "good", 123L)
        invokePrepareRoute(host, MainActivityBase.NAV_STUDY)
        assertSame(firstSnapshot, activity.studyUndoState.pending?.snapshot)

        activity.studyUndoState.capture(secondSnapshot, "again", 456L)
        invokePrepareRoute(host, MainActivityBase.NAV_HOME_ROUTE)
        assertNull(activity.studyUndoState.pending)
    }

    private fun invokePrepareRoute(host: MainActivityShellHost, selected: String) {
        val method = MainActivityShellHost::class.java.getDeclaredMethod("prepareRoute", String::class.java)
        method.isAccessible = true
        method.invoke(host, selected)
    }

    private fun snapshot(kanji: String, beforeReviews: Int, afterReviews: Int): StudyReviewActions.AppliedReviewSnapshot {
        val beforeReview = item(kanji, beforeReviews)
        val afterReview = item(kanji, afterReviews)
        return StudyReviewActions.AppliedReviewSnapshot(
            "token-$kanji",
            beforeReview,
            afterReview,
        )
    }

    private fun item(kanji: String, totalReviews: Int): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(
            kanji,
            "review",
            1_000L,
            1.0,
            2.0,
            totalReviews,
            0,
            0,
            0,
            "",
            1_000L,
        )
    }

    private class ShellHostTestActivity : MainActivity() {
        override fun onCreate(savedInstanceState: Bundle?) {
            // Skip the real activity startup; this test only needs the host state transitions.
        }

        override fun abandonActiveStudyTask() {
            // No-op: the undo-route test only cares that leaving study clears undo state.
        }
    }
}
