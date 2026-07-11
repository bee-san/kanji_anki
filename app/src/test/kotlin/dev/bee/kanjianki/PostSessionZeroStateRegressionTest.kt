package dev.bee.kanjianki

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.AdaptiveLoadPlanner
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.DailyStudyPlanPolicy
import dev.bee.kanjianki.core.DailyStudyPlanRequest
import dev.bee.kanjianki.core.LocalDayPolicy
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.StudyLadderRules
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreSchema
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * PS3: end-to-end pin on the combined PS1 + PS2 behavior — "finish a session,
 * home reads 0 for a bit, then reappears once cards come due."
 *
 * Drives a complete session against a real [LocalStore] + [BridgeScheduler] +
 * [StudySessionTracker]: seed N focus cards, fail each once, then keep serving
 * the same-session learning repeats (PS1 learn-ahead) through their steps to
 * graduation. At the graduation instant the timing-derived counts (adaptive plan
 * `remaining`, today plan `dueNow`, Study badge fallback) all read 0 (PS2). Advancing
 * the clock past the graduated card's FSRS due time makes the counts non-zero
 * again, proving the zero-state is a timing behavior, not a suppressed count.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PostSessionZeroStateRegressionTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore

    // Midday so startOfDay(now) is stable across the short simulation window.
    private val startNow = LocalDayPolicy.localDayStart(1_725_000_000_000L) + 12L * 60L * 60L * 1000L
    // A single focus card keeps the combined loop deterministic: the scheduler
    // finishes an in-progress learning card before starting new ones, so a
    // multi-card model would hinge on gather order rather than the PS1/PS2
    // behavior this regression pins.
    private val kanji = listOf("裂")
    private val settings = RecordsSyncModels.Settings.kikuDefaults()
    private val ladder = RecordsBase.StudyLadderSettings.defaults()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        store = LocalStore(context)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @Test
    fun postSessionHomeReadsZeroUntilGraduatedCardsComeDue() {
        seedStore()

        val tracker = StudySessionTracker()
        val scheduler = BridgeScheduler()
        val consumed = HashSet<String>()
        val plan = adaptivePlan(startNow)
        tracker.initializeTarget(plan)

        // Run the full session the way the coordinator does: keep serving the
        // planned session (learn-ahead includes same-session repeats) until the
        // hard cap is reached AND no learn-ahead repeat remains. Fail the card
        // once, then pass its learning-step repeats to graduation.
        var now = startNow
        var guard = 0
        var answeredAtLeastOnce = false
        while (guard++ < 200) {
            val items = store.studyItemsForKanji(kanji)
            if (doneReached(tracker, items, now)) {
                break
            }
            val session = StudySessionActions.plannedStudySession(
                scheduler,
                tracker,
                items,
                store.activeDashboardRows(),
                now,
                0L,
                null,
                settings,
                ladder,
            ) ?: break

            // Fail the very first answer (drops the card into learning), then
            // pass every subsequent repeat until it graduates.
            val rating = if (!answeredAtLeastOnce) "again" else "good"
            answeredAtLeastOnce = true
            answer(scheduler, tracker, consumed, session, rating, now)

            // Advance the clock the way real wall-clock would between answers:
            // to the earliest future due time among cards still in a learning
            // phase, so the next step becomes serveable. Never jump to a
            // graduated review's (days-away) due time.
            val nextLearningDue = store.studyItemsForKanji(kanji)
                .filter { it.state == "learning" && it.dueAtMillis > now }
                .minOfOrNull { it.dueAtMillis }
            now = maxOf(now + 1_000L, nextLearningDue ?: (now + 1_000L))
        }

        // The run finished: hard cap reached and no same-session repeat pending.
        val graduatedItems = store.studyItemsForKanji(kanji)
        assertTrue("hard cap reached", tracker.atHardCap(false))
        assertTrue(
            "no same-session learning repeat pending",
            tracker.dueCompletedLearningRepeatTaskKeys(
                graduatedItems,
                now + StudyLadderRules.LEARN_AHEAD_MILLIS,
            ).isEmpty(),
        )
        assertTrue(
            "the card graduated to review; states=" +
                graduatedItems.joinToString { "${it.kanji}:${it.state}:${it.phase}" },
            graduatedItems.all { it.state == "review" },
        )

        // --- Home reads 0 immediately after the session (PS2) ---
        val doneNow = now
        val donePlan = adaptivePlan(doneNow)
        assertEquals("adaptive remaining is 0", 0, donePlan.remaining)
        assertEquals("Study badge is 0", 0, badgeFallback(donePlan))
        assertEquals("today dueNow is 0", 0, dailyDueNow(doneNow))

        // --- Counts reappear once the graduated cards come due ---
        val laterDue = store.studyItemsForKanji(kanji).minOf { it.dueAtMillis }
        val later = laterDue + 1_000L
        val laterPlan = adaptivePlan(later)
        assertTrue("adaptive remaining reappears", laterPlan.remaining > 0)
        assertTrue("today dueNow reappears", dailyDueNow(later) > 0)
    }

    private fun doneReached(
        tracker: StudySessionTracker,
        items: List<RecordsStudyModels.StudyItem>,
        now: Long,
    ): Boolean {
        if (!tracker.atHardCap(false)) {
            return false
        }
        return tracker.dueCompletedLearningRepeatTaskKeys(
            items,
            now + StudyLadderRules.LEARN_AHEAD_MILLIS,
        ).isEmpty()
    }

    private fun answer(
        scheduler: BridgeScheduler,
        tracker: StudySessionTracker,
        consumed: MutableSet<String>,
        session: RecordsSchedulerModels.StudySession,
        rating: String,
        now: Long,
    ) {
        val before = session.item!!
        val request = RecordsSchedulerModels.ReviewRequest(before.kanji, session.token, rating, false, true, false, 0)
        val result = scheduler.applyReview(before, request, consumed, now, null, settings, ladder)
        store.saveReviewOutcome(result.item, request, rating, now, before)

        val taskKey = StudySessionTracker.sessionTaskKey(session)
        tracker.registerTaskShown(taskKey)
        tracker.markTaskCompleted(taskKey)
        tracker.markPlannedSessionTaskCompleted(session.taskType, before.kanji)
    }

    private fun adaptivePlan(now: Long): RecordsSchedulerModels.AdaptiveLoadPlan {
        val rows = store.activeDashboardRows()
        val items = store.studyItemsForKanji(rows.map { it.kanji })
        val studiedToday = store.studiedKanjiSince(LocalDayPolicy.localDayStart(now))
        return AdaptiveLoadPlanner().plan(
            AdaptiveLoadPlanner.PlanRequest.builder(
                rows,
                items,
                RecordsSchedulerModels.ReviewStats(0, 0, 0, 0, 0, 0, 0),
                store.studyStreak(now).currentDays,
                studiedToday,
                AdaptiveLoadPlanner.WorkloadPolicy.manual(100),
                now,
            ).settings(settings).build(),
        )
    }

    private fun badgeFallback(plan: RecordsSchedulerModels.AdaptiveLoadPlan): Int {
        return studySessionBadgeCount(
            studySessionActive = false,
            trackerTargetCount = 0,
            trackerCompletedCount = 0,
            cachedStudyNowCount = plan.remaining.coerceAtLeast(0),
        )
    }

    private fun dailyDueNow(now: Long): Int {
        val items = store.studyItemsForKanji(kanji)
        val streak = store.studyStreak(now)
        return DailyStudyPlanPolicy.plan(
            DailyStudyPlanRequest(
                nowMillis = now,
                dueAtMillis = items.map { it.dueAtMillis },
                studiedToday = streak.studiedToday,
                lastSuccessfulSyncAtMillis = startNow - 60_000L,
            ),
        ).dueNow
    }

    private fun seedStore() {
        store.saveSuccessfulSync(
            RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
            emptyList(),
            kanji.map { row(it) },
            settings,
            startNow - 2_000L,
            startNow - 1_000L,
            null,
        )
        // New focus cards, due now at the starting rung.
        kanji.forEach { store.saveStudyItem(newCard(it)) }
    }

    private fun newCard(kanji: String): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(kanji, "new", startNow, 0.4, 5.0, 0, 0, 0, 0, "", startNow)
            .copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.NEW_LEARNING)
            .activeToken("token-$kanji")
            .build()
    }

    private fun row(kanji: String): RecordsImportModels.DashboardRow {
        return RecordsImportModels.DashboardRow(
            kanji,
            1,
            "meaning-$kanji",
            "reading-$kanji",
            kanji,
            10,
            "reason-$kanji",
            "Needs practice",
            1,
            0,
            0,
            emptyList<RecordsImportModels.Example>(),
        )
    }
}
