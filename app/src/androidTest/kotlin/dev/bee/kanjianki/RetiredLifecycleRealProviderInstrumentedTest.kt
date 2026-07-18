package dev.bee.kanjianki

import android.content.ContentValues
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.ReminderEligibilityPolicy
import dev.bee.kanjianki.core.StudyLadderRules
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.sync.ManualSyncEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Stateful, sanitized real-ContentProvider lifecycle probe driven by the CI fixture runner. */
@RunWith(AndroidJUnit4::class)
class RetiredLifecycleRealProviderInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val arguments = InstrumentationRegistry.getArguments()
    private val preferences by lazy {
        context.getSharedPreferences("retired_lifecycle_fixture", Context.MODE_PRIVATE)
    }

    @Before
    fun requireExplicitLifecycleFixture() {
        assumeTrue(
            "Retired lifecycle provider fixture is opt-in.",
            arguments.getString("kanjiRetiredLifecycle") == "true",
        )
    }

    @Test
    fun lifecycleStage() {
        when (val stage = arguments.getString("lifecycleStage")) {
            "weak_initial" -> weakInitial()
            "mature_retire" -> matureRetire()
            "mature_repeat" -> matureRepeat()
            "weak_reopen" -> weakReopen("active_revision")
            "missing_route_retire" -> missingRouteRetire()
            "weak_reopen_after_missing" -> weakReopen("active_after_missing_revision")
            "invalid_ord1_fail_closed" -> invalidOrdOneFailsClosed()
            else -> throw AssertionError("Unknown lifecycleStage=$stage")
        }
    }

    private fun weakInitial() {
        context.deleteDatabase(DATABASE_NAME)
        preferences.edit().clear().commit()
        LocalStore(context).use { store ->
            assertSuccessfulSync(store)
            assertProviderSupport(store, expectedMature = 1)
            val seeded = targetItem(store)
            assertNotEquals(RETIRED, seeded.state)

            val memory = RecordsStudyModels.TaskMemory(
                StudyLadderRules.STATE_REVIEW,
                0L,
                12.5,
                6.25,
                7,
                3,
                0,
                "good",
                20,
            )
            val durable = seeded.copyBuilder()
                .state(StudyLadderRules.STATE_REVIEW)
                .dueAtMillis(0L)
                .stability(12.5)
                .difficulty(6.25)
                .totalReviews(7)
                .lapses(3)
                .kanjiMeaningMemory(memory)
                .rung(RecordsBase.LadderRung.KANJI_MEANING)
                .phase(RecordsBase.SchedulerPhase.REVIEW)
                .realPassStreak(4)
                .realAgainStreak(2)
                .lastRealReviewDueAtMillis(123_456L)
                .schedulerRevision(seeded.schedulerRevision + 10L)
                .activeToken(null)
                .build()
            store.saveStudyItem(durable)
            rememberDurable(durable)
            assertTargetReminderEligible(store, expected = true)
        }
    }

    private fun matureRetire() {
        LocalStore(context).use { store ->
            assertSuccessfulSync(store)
            assertProviderSupport(store, expectedMature = 2)
            val retired = targetItem(store)
            assertEquals(RETIRED, retired.state)
            assertDurablePreserved(retired)
            assertEquals(preferences.getLong("baseline_revision", -1L) + 1L, retired.schedulerRevision)
            preferences.edit().putLong("retired_revision", retired.schedulerRevision).commit()
            assertTransitionCounts(store, retired = 1, reopened = 0)
            assertTargetReminderEligible(store, expected = false)
        }
    }

    private fun matureRepeat() {
        LocalStore(context).use { store ->
            assertSuccessfulSync(store)
            assertProviderSupport(store, expectedMature = 2)
            val retired = targetItem(store)
            assertEquals(RETIRED, retired.state)
            assertDurablePreserved(retired)
            assertEquals(preferences.getLong("retired_revision", -1L), retired.schedulerRevision)
            assertTransitionCounts(store, retired = 1, reopened = 0)
            assertTargetReminderEligible(store, expected = false)
        }
    }

    private fun weakReopen(revisionKey: String) {
        LocalStore(context).use { store ->
            val priorRevision = targetItem(store).schedulerRevision
            assertSuccessfulSync(store)
            assertProviderSupport(store, expectedMature = 1)
            val reopened = targetItem(store)
            assertNotEquals(RETIRED, reopened.state)
            assertDurablePreserved(reopened)
            assertEquals(priorRevision + 1L, reopened.schedulerRevision)
            preferences.edit().putLong(revisionKey, reopened.schedulerRevision).commit()
            val expectedTransitions = if (revisionKey == "active_revision") 1 else 2
            assertTransitionCounts(store, retired = expectedTransitions, reopened = expectedTransitions)
            assertTargetReminderEligible(store, expected = true)
        }
    }

    private fun missingRouteRetire() {
        LocalStore(context).use { store ->
            val priorRevision = targetItem(store).schedulerRevision
            markArchivedRouteRestored(store)
            assertSuccessfulSync(store)
            assertTrue(store.dashboardRows().none { it.kanji == TARGET_KANJI })
            val retired = targetItem(store)
            assertEquals(RETIRED, retired.state)
            assertDurablePreserved(retired)
            assertEquals(priorRevision + 1L, retired.schedulerRevision)
            assertTransitionCounts(store, retired = 2, reopened = 1)
            assertTargetReminderEligible(store, expected = false)
        }
    }

    private fun markArchivedRouteRestored(store: LocalStore) {
        // Archived suspended sources deliberately remain analyzable after AnkiDroid
        // hides kani_archived notes. Mark the fixture archive restored so this stage
        // proves the distinct no-live-or-durable-route retirement path.
        val values = ContentValues().apply {
            put("restored_at", System.currentTimeMillis())
        }
        val updated = store.writableDatabase.update(
            "suspended_archive",
            values,
            "restored_at IS NULL",
            null,
        )
        assertEquals("fixture must restore exactly one archived route", 1, updated)
        assertTrue(store.unrestoredSuspendedArchiveCardIds().isEmpty())
    }

    private fun invalidOrdOneFailsClosed() {
        LocalStore(context).use { store ->
            val before = targetItem(store)
            val beforeRows = store.dashboardRows().map { it.kanji to it.matureSupportCount }
            val result = runSync(store)
            assertFalse("unsupported ord-1 fixture must fail closed", result.success)
            val after = targetItem(store)
            assertEquals(before.state, after.state)
            assertEquals(before.schedulerRevision, after.schedulerRevision)
            assertEquals(before.answerSignature, after.answerSignature)
            assertEquals(beforeRows, store.dashboardRows().map { it.kanji to it.matureSupportCount })
            assertDurablePreserved(after)
            assertTransitionCounts(store, retired = 2, reopened = 2)
            assertTargetReminderEligible(store, expected = true)
        }
    }

    private fun assertSuccessfulSync(store: LocalStore) {
        val result = runSync(store)
        assertTrue("real provider sync failed: ${result.message}", result.success)
    }

    private fun runSync(store: LocalStore) = ManualSyncEngine(
        context,
        store,
        AnkiDroidGateway(context),
        lifecycleSettings(),
    ).run()

    private fun assertProviderSupport(store: LocalStore, expectedMature: Int) {
        val row = store.dashboardRows().single { it.kanji == TARGET_KANJI }
        assertEquals(2, row.activeExampleCount)
        assertEquals(1, row.suspendedExampleCount)
        assertEquals(expectedMature, row.matureSupportCount)
    }

    private fun targetItem(store: LocalStore): RecordsStudyModels.StudyItem {
        val items = store.studyItems().filter { it.kanji == TARGET_KANJI }
        assertEquals("target must keep exactly one durable item", 1, items.size)
        return items.single()
    }

    private fun rememberDurable(item: RecordsStudyModels.StudyItem) {
        preferences.edit()
            .putInt("total_reviews", item.totalReviews)
            .putInt("lapses", item.lapses)
            .putLong("created_at", item.createdAtMillis)
            .putString("answer_signature", item.answerSignature)
            .putString("memory", item.kanjiMeaningMemory.encode())
            .putString("rung", item.rung.name)
            .putString("phase", item.phase.name)
            .putInt("pass_streak", item.realPassStreak)
            .putInt("again_streak", item.realAgainStreak)
            .putLong("last_real_due", item.lastRealReviewDueAtMillis)
            .putLong("baseline_revision", item.schedulerRevision)
            .commit()
    }

    private fun assertDurablePreserved(item: RecordsStudyModels.StudyItem) {
        assertEquals(preferences.getInt("total_reviews", -1), item.totalReviews)
        assertEquals(preferences.getInt("lapses", -1), item.lapses)
        assertEquals(preferences.getLong("created_at", -1L), item.createdAtMillis)
        assertEquals(preferences.getString("answer_signature", null), item.answerSignature)
        assertEquals(preferences.getString("memory", null), item.kanjiMeaningMemory.encode())
        assertEquals(preferences.getString("rung", null), item.rung.name)
        assertEquals(preferences.getString("phase", null), item.phase.name)
        assertEquals(preferences.getInt("pass_streak", -1), item.realPassStreak)
        assertEquals(preferences.getInt("again_streak", -1), item.realAgainStreak)
        assertEquals(preferences.getLong("last_real_due", -1L), item.lastRealReviewDueAtMillis)
    }

    private fun assertTransitionCounts(store: LocalStore, retired: Int, reopened: Int) {
        val events = store.timelineForKanji(TARGET_KANJI).events
        assertEquals(retired, events.count { it.eventType == RETIRED })
        assertEquals(reopened, events.count { it.eventType == "reopened" })
    }

    private fun assertTargetReminderEligible(store: LocalStore, expected: Boolean) {
        val eligible = ReminderEligibilityPolicy.eligibleReminderItems(
            store.studyItems(),
            store.activeDashboardRows(),
            store.studyLadderSettings(),
        )
        assertEquals(expected, eligible.any { it.kanji == TARGET_KANJI })
    }

    private fun lifecycleSettings(): RecordsSyncModels.Settings {
        val base = RecordsSyncModels.Settings.kikuDefaults()
        return RecordsSyncModels.Settings(
            base.modelName,
            base.templateName,
            base.expressionField,
            base.readingField,
            base.meaningField,
            base.sentenceField,
            base.frequencyField,
            base.frequencySortField,
            base.matureDays,
            base.matureSupportThreshold,
            base.suspendedRankMin,
            base.suspendedRankMax,
            base.activeQueueCap,
            base.newPerDay,
            base.writingTriggerMissDays,
            base.recognitionPromotionPasses,
            base.realDueReviewsToMove,
            true,
            base.importSuspendedCards,
            base.importTaggedCards,
            base.importTags,
            base.importWeakCards,
            base.importWeakFsrsDifficultyThreshold,
            base.importWeakLapsesThreshold,
            base.importMinMatchingCardsPerKanji,
            base.importBrowserQueryCards,
            base.importBrowserQuery,
            base.newCardSortMode,
            base.ladderPromotionIntervalDays,
            base.ladderDemotionFailStreak,
            base.ladderPromotionMinPasses,
        )
    }

    companion object {
        private const val DATABASE_NAME = "kanji_anki_simple.db"
        private const val TARGET_KANJI = "橋"
        private const val RETIRED = "retired"
    }
}
