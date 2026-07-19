package dev.bee.kanjianki

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyLadderRules
import dev.bee.kanjianki.core.StudyQueueSeeder
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityStudyPlanRetiredProjectionTest {
    @Test
    fun retiredDueRelearningItemIsExcludedAndReopeningRestoresIt() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivityBase.EXTRA_SCREENSHOT_ROUTE, MainActivityBase.NAV_HOME_ROUTE)
        }
        val controller = Robolectric.buildActivity(MainActivity::class.java, intent).create()
        val activity = controller.get()
        val now = System.currentTimeMillis()
        val row = dashboardRow("済")
        val retired = relearningItem(row, now - 1L, StudyLadderRules.STATE_RETIRED)

        try {
            val afterRetirement = activity.dailyStudyPlan(listOf(row), listOf(retired), now)
            val afterReopening = activity.dailyStudyPlan(
                listOf(row),
                listOf(retired.copyBuilder().state(StudyLadderRules.STATE_LEARNING).build()),
                now,
            )

            assertEquals(0, afterRetirement.dueNow)
            assertEquals(0, afterRetirement.newProblemKanjiAvailable)
            assertEquals(1, afterReopening.dueNow)
        } finally {
            controller.destroy()
        }
    }

    private fun relearningItem(
        row: RecordsImportModels.DashboardRow,
        dueAtMillis: Long,
        state: String,
    ): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(
            row.kanji,
            state,
            dueAtMillis,
            1.0,
            5.0,
            3,
            1,
            0,
            0,
            "",
            dueAtMillis - 1_000L,
        ).copyBuilder()
            .phase(RecordsBase.SchedulerPhase.RELEARNING)
            .answerSignature(StudyQueueSeeder.answerSignature(row))
            .build()
    }

    private fun dashboardRow(kanji: String): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji,
            1,
            "meaning-$kanji",
            "reading-$kanji",
            "search-$kanji",
            10,
            "weak_support",
            "reason-$kanji",
            1,
            0,
            0,
            listOf(
                RecordsImportModels.Example(
                    "active",
                    1L,
                    2L,
                    "expr-$kanji",
                    "reading-$kanji",
                    "meaning-$kanji",
                    "",
                    false,
                    0,
                ),
            ),
        )
    }
}
