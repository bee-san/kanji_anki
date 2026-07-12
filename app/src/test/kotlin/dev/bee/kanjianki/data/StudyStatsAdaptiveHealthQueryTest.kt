package dev.bee.kanjianki.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.AdaptiveRouteState
import dev.bee.kanjianki.core.AdaptiveRouteStateCodec
import dev.bee.kanjianki.core.AdaptiveStudyHealthPolicy
import dev.bee.kanjianki.core.AdaptiveStudyItemPolicy
import dev.bee.kanjianki.core.CoreSkill
import dev.bee.kanjianki.core.FailureKind
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.StudyTaskTypes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StudyStatsAdaptiveHealthQueryTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore
    private lateinit var db: SQLiteDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        store = LocalStore(context)
        db = store.writableDatabase
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @Test
    fun outcomeStatsReadsAdaptiveHealthDirectlyFromPersistedStudyItems() {
        insertAdaptive(
            "認",
            RecordsBase.SchedulerPhase.REVIEW,
            AdaptiveRouteState(activeCore = CoreSkill.RECOGNITION),
            RecordsStudyModels.TaskMemory.initial(),
        )
        insertAdaptive(
            "読",
            RecordsBase.SchedulerPhase.REVIEW,
            AdaptiveRouteState(
                activeCore = CoreSkill.CONTEXTUAL_READING,
                contextualReadingReviewCount = 2,
            ),
            contextualMemory(consecutivePasses = 2),
        )
        insertAdaptive(
            "修",
            RecordsBase.SchedulerPhase.RELEARNING,
            AdaptiveRouteState(
                activeCore = CoreSkill.CONTEXTUAL_READING,
                contextualReadingReviewCount = 1,
                activeRepairTasks = listOf(StudyTaskTypes.TYPE_READING),
                repairStepMinutes = listOf(10),
                recurringFailure = FailureKind.WRONG_READING,
                recurringFailureCount = 2,
                repairAttemptCount = AdaptiveStudyHealthPolicy.STUCK_REPAIR_ATTEMPTS,
            ),
            contextualMemory(consecutivePasses = 1),
        )

        val health = StudyStatsStore(store).kaniOutcomeStats().adaptiveHealth

        assertEquals(3, health.totalAdaptiveItems)
        assertEquals(1, health.countFor(CoreSkill.RECOGNITION))
        assertEquals(2, health.countFor(CoreSkill.CONTEXTUAL_READING))
        assertEquals(1, health.contextualCompleteCount)
        assertEquals(1, health.activeRepairCount)
        assertEquals(1, health.repairCountFor(StudyTaskTypes.TYPE_READING))
        assertEquals(1, health.failureCountFor(FailureKind.WRONG_READING))
        assertEquals(1, health.escalationRiskCount)
        assertEquals(1, health.stuckRepairCount)
    }

    private fun insertAdaptive(
        kanji: String,
        phase: RecordsBase.SchedulerPhase,
        route: AdaptiveRouteState,
        contextualMemory: RecordsStudyModels.TaskMemory,
    ) {
        db.execSQL(
            "INSERT INTO study_items " +
                "(kanji, state, due_at, stability, difficulty, total_reviews, lapses, learning_step, writing_level, " +
                "rung, phase, word_reading_memory, routing_version, adaptive_route_state_json, created_at) " +
                "VALUES (?, 'review', 0, 1.0, 5.0, 1, 0, 0, 0, ?, ?, ?, ?, ?, 1)",
            arrayOf<Any>(
                kanji,
                if (route.activeCore == CoreSkill.RECOGNITION) {
                    RecordsBase.LadderRung.KANJI_MEANING.wireName()
                } else {
                    RecordsBase.LadderRung.WORD_READING.wireName()
                },
                phase.wireName(),
                contextualMemory.encode(),
                AdaptiveStudyItemPolicy.ROUTING_VERSION,
                AdaptiveRouteStateCodec.encode(route),
            ),
        )
    }

    private fun contextualMemory(consecutivePasses: Int) = RecordsStudyModels.TaskMemory(
        "review",
        1_700_000_000_000L,
        4.0,
        5.0,
        2,
        0,
        0,
        "good",
        30,
        consecutivePasses,
        1_699_000_000_000L,
    )
}
