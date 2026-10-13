package dev.bee.kanjianki

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.core.AdaptiveLoadPlanner
import dev.bee.kanjianki.core.BridgeScheduler
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsImportModels
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SimilarKanjiIndex
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.core.SyncSettings
import dev.bee.kanjianki.testing.DeviceRisk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.StringReader
import java.util.LinkedHashMap


/**
 * End-to-end instrumented tests for the ladder scheduler. These tests exercise
 * the full lifecycle: sync → LocalStore persistence → BridgeScheduler state
 * transitions → review persistence → rung promotion/demotion → DB round-trip.
 *
 * Unlike the pure-JVM LadderSchedulerTest, these verify that ladder state
 * (rung, phase, realPassStreak, realAgainStreak, lastRealReviewDueAtMillis,
 * hasSimilarKanji) survives SQLite round-trips correctly.
 *
 * Most rung-transition cases below preserve the legacy v30 compatibility
 * model. The device-risk lane marks only contracts that remain canonical in
 * v31; exhaustive adaptive routing coverage runs in the deterministic JVM
 * gate.
 */
@RunWith(AndroidJUnit4::class)
class LadderSchedulerEndToEndTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore
    private lateinit var scheduler: BridgeScheduler
    private var helperTokenCounter = 0

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        KaniTestDatabase.delete(context)
        store = LocalStore(context)
        scheduler = BridgeScheduler()
        helperTokenCounter = 0
    }

    @After
    fun tearDown() {
        if (::store.isInitialized) {
            store.close()
        }
        if (::context.isInitialized) {
            KaniTestDatabase.delete(context)
        }
    }

    // ---- Learning graduation persists ladder state ----

    @DeviceRisk
    @Test
    fun newCardLearningGraduationPersistsRungAndPhaseToDb() {
        seedSyncWithKanji("裂")
        val items = store.studyItems()
        assertEquals(1, items.size)
        var item = items[0]
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, item.rung)
        assertEquals(RecordsBase.SchedulerPhase.NEW_LEARNING, item.phase)

        // Graduate through 2 default new-learning steps (1m, 10m)
        val consumed = hashSetOf<String>()
        val now = System.currentTimeMillis()
        item = item.withToken("g1")
        val r1 = scheduler.applyReview(
            item,
            passRequest("裂", "g1"),
            consumed,
            now,
        )
        assertEquals(RecordsBase.SchedulerPhase.NEW_LEARNING, r1.item.phase)
        assertEquals(1, r1.item.learningStep)

        val r2 = scheduler.applyReview(
            r1.item.withToken("g2"),
            passRequest("裂", "g2"),
            consumed,
            now + 60_000L,
        )
        assertEquals(RecordsBase.SchedulerPhase.REVIEW, r2.item.phase)
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, r2.item.rung)
        assertEquals("review", r2.item.state)

        // Persist and reload from DB
        store.saveStudyItem(r2.item)
        val reloaded = store.studyItems()
        assertEquals(1, reloaded.size)
        val persisted = reloaded[0]
        assertEquals(RecordsBase.SchedulerPhase.REVIEW, persisted.phase)
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, persisted.rung)
        assertEquals("review", persisted.state)
        assertTrue(persisted.dueAtMillis > now)
    }

    // ---- Due-review passes promote rung ----

    @Test
    fun dueReviewPassWithMatureFsrsIntervalPromotesRung() {
        val dueAt = System.currentTimeMillis() - 1000L
        val item = reviewItemOnRung("裂", RecordsBase.LadderRung.KANJI_MEANING, dueAt)
        store.replaceStudyItems(listOf(item))

        val consumed = hashSetOf<String>()
        val now = System.currentTimeMillis()
        var current = store.studyItems()[0]
        val settings = settingsWithLadderThresholds(1, RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK)

        current = dueWithToken(current, "pass", now - 1L)
        val result = scheduler.applyReview(
            current,
            passRequest("裂", "pass"),
            consumed,
            now,
            RecordsSchedulerModels.SchedulerParameters.defaults(),
            settings,
        )
        current = result.item
        store.saveStudyItem(current)

        // Verify promotion persisted
        val reloaded = store.studyItems()
        assertEquals(1, reloaded.size)
        val promoted = reloaded[0]
        assertEquals(RecordsBase.LadderRung.FONT_MEANING, promoted.rung)
        assertEquals(RecordsBase.SchedulerPhase.REVIEW, promoted.phase)
        assertEquals(0, promoted.realPassStreak) // reset after promotion
        assertEquals(0, promoted.realAgainStreak)
    }

    // ---- Due-review Agains demote rung ----

    @Test
    fun threeConsecutiveDueReviewAgainsDemoteRung() {
        val dueAt = System.currentTimeMillis() - 1000L
        val item = reviewItemOnRung("裂", RecordsBase.LadderRung.KANJI_MEANING, dueAt)
        store.replaceStudyItems(listOf(item))

        val consumed = hashSetOf<String>()
        val now = System.currentTimeMillis()
        var current = store.studyItems()[0]

        // 3 Agains. Each Again enters relearning; need to graduate back out.
        for (i in 0 until 3) {
            val token = "fail$i"
            current = current.withToken(token)
            val result = scheduler.applyReview(
                current,
                failRequest("裂", token),
                consumed,
                now + i * 60_000L,
            )
            current = result.item

            // If entered relearning, graduate back to review for the next due attempt
            if (current.phase == RecordsBase.SchedulerPhase.RELEARNING) {
                current = graduateFromRelearning(current, consumed, now + i * 60_000L + 30_000L)
            }
            // Make it due again for the next iteration
            current = current.copyBuilder().dueAtMillis(now + (i + 1) * 60_000L - 500L).build()
            store.saveStudyItem(current)
        }

        // Verify demotion persisted (KANJI_MEANING -> MEANING_KANJI).
        val reloaded = store.studyItems()
        assertEquals(1, reloaded.size)
        val demoted = reloaded[0]
        assertEquals(RecordsBase.LadderRung.MEANING_KANJI, demoted.rung)
    }

    // ---- Similar kanji rung skipped when hasSimilarKanji is false ----

    @Test
    fun promotionSkipsSimilarKanjiRungWhenFlagIsFalse() {
        val dueAt = System.currentTimeMillis() - 1000L
        // Start on TYPE_MEANING, hasSimilarKanji=false
        val item = reviewItemOnRung("裂", RecordsBase.LadderRung.TYPE_MEANING, dueAt)
            .copyBuilder().hasSimilarKanji(false).build()
        store.replaceStudyItems(listOf(item))

        val consumed = hashSetOf<String>()
        val now = System.currentTimeMillis()
        var current = store.studyItems()[0]
        assertFalse(current.hasSimilarKanji)

        val settings = settingsWithLadderThresholds(1, RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK)
        current = dueWithToken(current, "p", now - 1L)
        val result = scheduler.applyReview(
            current,
            passRequest("裂", "p"),
            consumed,
            now,
            RecordsSchedulerModels.SchedulerParameters.defaults(),
            settings,
        )
        current = result.item
        store.saveStudyItem(current)

        val reloaded = store.studyItems()
        val promoted = reloaded[0]
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, promoted.rung)
    }

    // ---- Similar kanji rung included when hasSimilarKanji is true ----

    @Test
    fun promotionIncludesSimilarKanjiRungWhenFlagIsTrue() {
        // Seed with similar_kanji_pairs so hasSimilarKanji is true
        seedSyncWithSimilarPairs("裂", "烈")
        val dueAt = System.currentTimeMillis() - 1000L
        val item = reviewItemOnRung("裂", RecordsBase.LadderRung.WRITE_KANJI, dueAt)
            .copyBuilder().hasSimilarKanji(true).build()
        store.replaceStudyItems(listOf(item))

        val consumed = hashSetOf<String>()
        val now = System.currentTimeMillis()
        var current = store.studyItems()[0]
        assertTrue(current.hasSimilarKanji)

        val settings = settingsWithLadderThresholds(1, RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK)
        current = dueWithToken(current, "p", now - 1L)
        val result = scheduler.applyReview(
            current,
            passRequest("裂", "p"),
            consumed,
            now,
            RecordsSchedulerModels.SchedulerParameters.defaults(),
            settings,
        )
        current = result.item
        store.saveStudyItem(current)

        val reloaded = store.studyItems()
        val promoted = reloaded[0]
        assertEquals(RecordsBase.LadderRung.SIMILAR_KANJI, promoted.rung)
    }

    // ---- Relearning is practice-only ----

    @Test
    fun relearningPhaseDoesNotAdvanceLadderStreaks() {
        val dueAt = System.currentTimeMillis() - 1000L
        val item = reviewItemOnRung("裂", RecordsBase.LadderRung.KANJI_MEANING, dueAt)
            .copyBuilder().realPassStreak(2).build()
        store.replaceStudyItems(listOf(item))

        val consumed = hashSetOf<String>()
        val now = System.currentTimeMillis()
        var current = store.studyItems()[0]

        // One Again on a due review enters relearning
        current = current.withToken("lapse1")
        val lapseResult = scheduler.applyReview(
            current,
            failRequest("裂", "lapse1"),
            consumed,
            now,
        )
        val inRelearning = lapseResult.item
        assertEquals(RecordsBase.SchedulerPhase.RELEARNING, inRelearning.phase)
        store.saveStudyItem(inRelearning)

        // Relearning Good (practice-only, should not affect pass streak)
        current = inRelearning.withToken("rl1")
        val rlResult = scheduler.applyReview(
            current,
            passRequest("裂", "rl1"),
            consumed,
            now + 5000L,
        )
        store.saveStudyItem(rlResult.item)

        // Reload and verify streaks not advanced toward promotion
        val reloaded = store.studyItems()
        val afterRelearning = reloaded[0]
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, afterRelearning.rung)
        // The pass streak was 2 before the lapse. Lapse resets it.
        assertEquals(0, afterRelearning.realPassStreak)
        // The again streak incremented from the due-review Again, but relearning Good did NOT increment pass streak
        // After graduating relearning, the card is back in review phase
    }

    // ---- Ladder floor (WRITE_KANJI) prevents demotion ----

    @Test
    fun ladderFloorPreventsDemotionBelowWriteKanji() {
        val dueAt = System.currentTimeMillis() - 1000L
        val item = reviewItemOnRung("裂", RecordsBase.LadderRung.WRITE_KANJI, dueAt)
        store.replaceStudyItems(listOf(item))

        val consumed = hashSetOf<String>()
        val now = System.currentTimeMillis()
        var current = store.studyItems()[0]

        // 3+ Agains at WRITE_KANJI should keep it there
        for (i in 0 until 5) {
            val token = "floor$i"
            current = current.withToken(token)
            val result = scheduler.applyReview(
                current,
                failRequest("裂", token),
                consumed,
                now + i * 60_000L,
            )
            current = result.item
            if (current.phase == RecordsBase.SchedulerPhase.RELEARNING) {
                current = graduateFromRelearning(current, consumed, now + i * 60_000L + 30_000L)
            }
            current = current.copyBuilder().dueAtMillis(now + (i + 1) * 60_000L - 500L).build()
            store.saveStudyItem(current)
        }

        val reloaded = store.studyItems()
        val floored = reloaded[0]
        assertEquals(RecordsBase.LadderRung.WRITE_KANJI, floored.rung)
    }

    // ---- Ladder ceiling (WORD_READING) prevents promotion ----

    @Test
    fun ladderCeilingPreventsPromotionAboveWordReading() {
        val dueAt = System.currentTimeMillis() - 1000L
        val item = reviewItemOnRung("裂", RecordsBase.LadderRung.WORD_READING, dueAt)
        store.replaceStudyItems(listOf(item))

        val consumed = hashSetOf<String>()
        val now = System.currentTimeMillis()
        var current = store.studyItems()[0]

        // 5 consecutive passes at the ceiling
        for (i in 0 until 5) {
            val token = "ceiling$i"
            current = dueWithToken(current, token, now + i * 1000L - 1L)
            val result = scheduler.applyReview(
                current,
                passRequest("裂", token),
                consumed,
                now + i * 1000L,
            )
            current = result.item
            store.saveStudyItem(current)
        }

        val reloaded = store.studyItems()
        val capped = reloaded[0]
        assertEquals(RecordsBase.LadderRung.WORD_READING, capped.rung)
    }

    // ---- Custom ladder settings ----

    @Test
    fun customLadderPromotionIntervalHonorsSettingFromSyncSettings() {
        val dueAt = System.currentTimeMillis() - 1000L
        val item = reviewItemOnRung("裂", RecordsBase.LadderRung.KANJI_MEANING, dueAt)
        store.replaceStudyItems(listOf(item))

        persistSetting(SyncSettings.LADDER_PROMOTION_INTERVAL_DAYS_SETTING_KEY, "1")
        val settings = SyncSettings.fromStore(store)
        assertEquals(1, settings.ladderPromotionIntervalDays)

        val consumed = hashSetOf<String>()
        val now = System.currentTimeMillis()
        var current = store.studyItems()[0]

        val token = "custom"
        current = dueWithToken(current, token, now - 1L)
        val result = scheduler.applyReview(
            current,
            passRequest("裂", token),
            consumed,
            now,
            RecordsSchedulerModels.SchedulerParameters.defaults(),
            settings,
        )
        current = result.item
        store.saveStudyItem(current)

        val reloaded = store.studyItems()
        val promoted = reloaded[0]
        assertEquals(RecordsBase.LadderRung.FONT_MEANING, promoted.rung)
    }

    // ---- Full multi-rung E2E lifecycle ----

    @Test
    fun fullLifecycleSyncGraduatesAndPromotesThroughMultipleRungs() {
        // Sync seeds a new card
        seedSyncWithKanji("裂")
        val initial = store.studyItems()
        assertEquals(1, initial.size)
        var current = initial[0]
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, current.rung)
        assertEquals(RecordsBase.SchedulerPhase.NEW_LEARNING, current.phase)

        val consumed = hashSetOf<String>()
        val now = System.currentTimeMillis()
        var tokenCounter = 0

        // Phase 1: Graduate through new-learning (2 Goods)
        for (i in 0 until 2) {
            val token = "learn${tokenCounter++}"
            current = current.withToken(token)
            val result = scheduler.applyReview(
                current,
                passRequest("裂", token),
                consumed,
                now + tokenCounter * 1000L,
            )
            current = result.item
        }
        assertEquals(RecordsBase.SchedulerPhase.REVIEW, current.phase)
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, current.rung)
        store.saveStudyItem(current)
        val promotionSettings = settingsWithLadderThresholds(1, RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK)

        // Phase 2: mature FSRS due-review pass -> promote to FONT_MEANING
        current = current.copyBuilder().dueAtMillis(now).build()
        store.saveStudyItem(current)
        current = store.studyItems()[0]
        val promo1Token = "promo1_${tokenCounter++}"
        val promo1At = now + tokenCounter * 1000L
        current = dueWithToken(current, promo1Token, promo1At - 1L)
        val promo1 = scheduler.applyReview(
            current,
            passRequest("裂", promo1Token),
            consumed,
            promo1At,
            RecordsSchedulerModels.SchedulerParameters.defaults(),
            promotionSettings,
        )
        current = promo1.item
        store.saveStudyItem(current)
        assertEquals(RecordsBase.LadderRung.FONT_MEANING, store.studyItems()[0].rung)

        // Phase 3: another mature FSRS pass -> promote to WORD_READING
        current = store.studyItems()[0].copyBuilder().dueAtMillis(now).build()
        store.saveStudyItem(current)
        current = store.studyItems()[0]
        val promo2Token = "promo2_${tokenCounter++}"
        val promo2At = now + tokenCounter * 1000L
        current = dueWithToken(current, promo2Token, promo2At - 1L)
        val promo2 = scheduler.applyReview(
            current,
            passRequest("裂", promo2Token),
            consumed,
            promo2At,
            RecordsSchedulerModels.SchedulerParameters.defaults(),
            promotionSettings,
        )
        current = promo2.item
        store.saveStudyItem(current)
        assertEquals(RecordsBase.LadderRung.WORD_READING, store.studyItems()[0].rung)

        // Phase 4: At ceiling, another mature pass still stays at WORD_READING
        current = store.studyItems()[0].copyBuilder().dueAtMillis(now).build()
        store.saveStudyItem(current)
        current = store.studyItems()[0]
        val ceilToken = "ceil_${tokenCounter++}"
        val ceilAt = now + tokenCounter * 1000L
        current = dueWithToken(current, ceilToken, ceilAt - 1L)
        val ceiling = scheduler.applyReview(
            current,
            passRequest("裂", ceilToken),
            consumed,
            ceilAt,
            RecordsSchedulerModels.SchedulerParameters.defaults(),
            promotionSettings,
        )
        current = ceiling.item
        store.saveStudyItem(current)
        assertEquals(RecordsBase.LadderRung.WORD_READING, store.studyItems()[0].rung)
    }

    @DeviceRisk
    @Test
    fun seedQueueAdmitsNewItemWithCorrectLadderDefaults() {
        val row = dashboardRow("裂", "split")
        val now = System.currentTimeMillis()
        val startOfDay = now - (now % 86_400_000L)
        val seeded = scheduler.seedQueue(
            listOf(row),
            emptyList(),
            RecordsSyncModels.Settings.kikuDefaults(),
            now,
            startOfDay,
        )
        assertEquals(1, seeded.size)
        val admitted = seeded[0]
        assertEquals("裂", admitted.kanji)
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, admitted.rung)
        assertEquals(RecordsBase.SchedulerPhase.NEW_LEARNING, admitted.phase)
        assertEquals(0, admitted.realPassStreak)
        assertEquals(0, admitted.realAgainStreak)

        // Persist and round-trip
        store.replaceStudyItems(seeded)
        val reloaded = store.studyItems()[0]
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, reloaded.rung)
        assertEquals(RecordsBase.SchedulerPhase.NEW_LEARNING, reloaded.phase)
    }

    @DeviceRisk
    @Test
    fun hasSimilarKanjiAnnotatedFromSimilarPairsOnRead() {
        seedSyncWithSimilarPairs("裂", "烈")
        val item = RecordsStudyModels.StudyItem(
            "裂",
            "new",
            0L,
            0.4,
            5.0,
            0,
            0,
            0,
            0,
            null,
            System.currentTimeMillis(),
        )
        store.replaceStudyItems(listOf(item))

        // On read, hasSimilarKanji should be true because similar_kanji_pairs has (裂, 烈)
        val reloaded = store.studyItems()[0]
        assertTrue("hasSimilarKanji should be true when pairs exist", reloaded.hasSimilarKanji)
    }

    @DeviceRisk
    @Test
    fun hasSimilarKanjiFalseWhenNoPairsExist() {
        seedSyncWithKanji("裂")
        val reloaded = store.studyItems()[0]
        assertFalse("hasSimilarKanji should be false when no pairs exist", reloaded.hasSimilarKanji)
    }

    @DeviceRisk
    @Test
    fun annotateSimilarKanjiAvailabilityWorksAfterSeedQueue() {
        seedSyncWithSimilarPairs("裂", "烈")
        val row = dashboardRow("裂", "split")
        val now = System.currentTimeMillis()
        val startOfDay = now - (now % 86_400_000L)

        val seeded = scheduler.seedQueue(
            listOf(row),
            emptyList(),
            RecordsSyncModels.Settings.kikuDefaults(),
            now,
            startOfDay,
        )
        // Before annotation, hasSimilarKanji defaults to false
        assertFalse(seeded[0].hasSimilarKanji)

        // Annotate from DB
        val annotated = store.annotateSimilarKanjiAvailability(seeded)
        assertTrue(annotated[0].hasSimilarKanji)

        // Persist annotated, reload, confirm
        store.replaceStudyItems(annotated)
        assertTrue(store.studyItems()[0].hasSimilarKanji)
    }

    @Test
    fun demotionFromKanjiMeaningSkipsDisabledMeaningKanji() {
        seedSyncWithSimilarPairs("裂", "烈")
        val dueAt = System.currentTimeMillis() - 1000L
        val item = reviewItemOnRung("裂", RecordsBase.LadderRung.KANJI_MEANING, dueAt)
            .copyBuilder().hasSimilarKanji(true).build()
        store.replaceStudyItems(listOf(item))

        val consumed = hashSetOf<String>()
        val now = System.currentTimeMillis()
        var current = store.studyItems()[0]
        assertTrue(current.hasSimilarKanji)

        // 3 Agains with relearning graduation between each
        for (i in 0 until 3) {
            val token = "demote$i"
            current = current.withToken(token)
            val result = scheduler.applyReview(
                current,
                failRequest("裂", token),
                consumed,
                now + i * 60_000L,
            )
            current = result.item
            if (current.phase == RecordsBase.SchedulerPhase.RELEARNING) {
                current = graduateFromRelearning(current, consumed, now + i * 60_000L + 30_000L)
            }
            current = current.copyBuilder().dueAtMillis(now + (i + 1) * 60_000L - 500L).build()
            store.saveStudyItem(current)
        }

        // MEANING_KANJI now sits between type and recognition, so KANJI_MEANING demotes there first.
        val reloaded = store.studyItems()
        assertEquals(RecordsBase.LadderRung.MEANING_KANJI, reloaded[0].rung)
    }

    @Test
    fun passStreakResetsAfterPromotion() {
        val dueAt = System.currentTimeMillis() - 1000L
        val item = reviewItemOnRung("裂", RecordsBase.LadderRung.KANJI_MEANING, dueAt)
            .copyBuilder().realPassStreak(2).build()
        store.replaceStudyItems(listOf(item))

        val consumed = hashSetOf<String>()
        val now = System.currentTimeMillis()
        val beforePromotion = store.studyItems()[0]
        assertEquals(2, beforePromotion.realPassStreak)
        assertEquals(RecordsBase.LadderRung.KANJI_MEANING, beforePromotion.rung)

        val current = dueWithToken(store.studyItems()[0], "streak", now - 1L)
        val settings = settingsWithLadderThresholds(1, RecordsBase.DEFAULT_LADDER_DEMOTION_FAIL_STREAK)
        val promoResult = scheduler.applyReview(
            current,
            passRequest("裂", "streak"),
            consumed,
            now,
            RecordsSchedulerModels.SchedulerParameters.defaults(),
            settings,
        )
        store.saveStudyItem(promoResult.item)

        val afterPromotion = store.studyItems()[0]
        assertEquals(RecordsBase.LadderRung.FONT_MEANING, afterPromotion.rung)
        assertEquals(0, afterPromotion.realPassStreak)
    }

    @Test
    fun failResetsPassStreakWithoutDemotion() {
        val dueAt = System.currentTimeMillis() - 1000L
        val item = reviewItemOnRung("裂", RecordsBase.LadderRung.FONT_MEANING, dueAt)
            .copyBuilder().realPassStreak(2).build()
        store.replaceStudyItems(listOf(item))

        val consumed = hashSetOf<String>()
        val now = System.currentTimeMillis()
        var current = store.studyItems()[0]
        assertEquals(2, current.realPassStreak)

        // One Again resets the pass streak but doesn't demote (need 3 Agains)
        current = current.withToken("break")
        val result = scheduler.applyReview(
            current,
            failRequest("裂", "break"),
            consumed,
            now,
        )
        store.saveStudyItem(result.item)

        val after = store.studyItems()[0]
        assertEquals(RecordsBase.LadderRung.FONT_MEANING, after.rung) // no demotion yet
        assertEquals(0, after.realPassStreak) // pass streak reset
        assertEquals(1, after.realAgainStreak) // again streak started
    }

    @Test
    fun lastRealReviewDueAtMillisPersistsThroughDbRoundTrip() {
        val dueAt = System.currentTimeMillis() - 5000L
        val item = reviewItemOnRung("裂", RecordsBase.LadderRung.KANJI_MEANING, dueAt)
        store.replaceStudyItems(listOf(item))

        val consumed = hashSetOf<String>()
        val now = System.currentTimeMillis()
        var current = store.studyItems()[0]

        // Apply a due review so the scheduler records lastRealReviewDueAtMillis
        current = current.withToken("due1")
        val result = scheduler.applyReview(
            current,
            passRequest("裂", "due1"),
            consumed,
            now,
        )
        store.saveStudyItem(result.item)

        val reloaded = store.studyItems()[0]
        assertTrue(
            "lastRealReviewDueAtMillis should be set after a due review",
            reloaded.lastRealReviewDueAtMillis > 0L,
        )
        assertEquals(1, reloaded.realPassStreak)
    }

    // ---- Helpers ----

    private fun seedSyncWithKanji(kanji: String) {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val note = note(1L, kanji + "語", "reading", "meaning", kanji + "の例文。")
        val card = RecordsSyncModels.Card(10L, 1L, 0, "Kiku", 2, 2, 0, 3, 4, 1, false)
        val row = dashboardRow(kanji, "meaning")
        store.saveSuccessfulSync(
            RecordsSyncModels.CollectionSnapshot(listOf(note), listOf(card)),
            emptyList(),
            listOf(row),
            settings,
            System.currentTimeMillis() - 1000L,
            System.currentTimeMillis(),
            null,
        )
        val now = System.currentTimeMillis()
        store.replaceStudyItems(
            listOf(
                RecordsStudyModels.StudyItem(kanji, "new", now, 0.4, 5.0, 0, 0, 0, 0, null, now),
            ),
        )
    }

    private fun seedSyncWithSimilarPairs(kanjiA: String, kanjiB: String) {
        val settings = RecordsSyncModels.Settings.kikuDefaults()
        val noteA = note(1L, kanjiA + "語", "reading", "meaning", kanjiA + "の例文。")
        val noteB = note(2L, kanjiB + "語", "reading2", "meaning2", kanjiB + "の例文。")
        val cardA = RecordsSyncModels.Card(10L, 1L, 0, "Kiku", 2, 2, 0, 3, 4, 1, false)
        val cardB = RecordsSyncModels.Card(20L, 2L, 0, "Kiku", 2, 2, 0, 3, 4, 1, false)
        val rowA = dashboardRow(kanjiA, "meaning")
        val index = SimilarKanjiIndex.parseTsv(StringReader(kanjiA + "\t" + kanjiB + "\tfixture\n"))
        store.saveSuccessfulSync(
            RecordsSyncModels.CollectionSnapshot(listOf(noteA, noteB), listOf(cardA, cardB)),
            emptyList(),
            listOf(rowA),
            settings,
            LocalStoreBase.SyncTiming(System.currentTimeMillis() - 1000L, System.currentTimeMillis()),
            null,
            index,
        )
    }

    private fun reviewItemOnRung(
        kanji: String,
        rung: RecordsBase.LadderRung,
        dueAtMillis: Long,
    ): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(
            kanji,
            "review",
            dueAtMillis,
            1.2,
            5.0,
            1,
            0,
            2,
            if (rung == RecordsBase.LadderRung.WRITE_KANJI) 1 else 0,
            0,
            0,
            0L,
            rung == RecordsBase.LadderRung.WRITE_KANJI,
            null,
            0L,
        ).withRungAndPhase(rung, RecordsBase.SchedulerPhase.REVIEW)
    }

    private fun graduateFromRelearning(
        item: RecordsStudyModels.StudyItem,
        consumed: MutableSet<String>,
        nowMillis: Long,
    ): RecordsStudyModels.StudyItem {
        // Graduate relearning with repeated Goods until back in review phase
        var current = item
        for (i in 0 until 5) {
            if (current.phase != RecordsBase.SchedulerPhase.RELEARNING) {
                break
            }
            val token = "rl_grad_${helperTokenCounter++}"
            current = current.withToken(token)
            val result = scheduler.applyReview(
                current,
                passRequest(current.kanji, token),
                consumed,
                nowMillis + i * 1000L,
            )
            current = result.item
        }
        return current
    }

    private fun dashboardRow(kanji: String, meaning: String): RecordsImportModels.DashboardRow {
        val example = RecordsImportModels.Example(
            "active",
            10L,
            1L,
            kanji + "語",
            "reading",
            meaning,
            kanji + "の例文。",
            false,
            1,
        )
        return RecordsImportModels.DashboardRow(
            kanji,
            3401,
            meaning,
            "reading",
            "deck:Kiku $kanji",
            88,
            "suspended_archive",
            "Imported from suspended cards.",
            1,
            1,
            0,
            listOf(example),
        )
    }

    private fun note(
        id: Long,
        expression: String,
        reading: String,
        meaning: String,
        sentence: String,
    ): RecordsSyncModels.Note {
        val fields: MutableMap<String, String> = LinkedHashMap()
        fields["Expression"] = expression
        fields["ExpressionReading"] = reading
        fields["MainDefinition"] = meaning
        fields["Sentence"] = sentence
        fields["Frequency"] = "1000"
        fields["FreqSort"] = "1000"
        return RecordsSyncModels.Note(id, "Kiku", fields, emptyList())
    }

    private fun passRequest(kanji: String, token: String): RecordsSchedulerModels.ReviewRequest {
        return RecordsSchedulerModels.ReviewRequest(kanji, token, "good", false, false, false, 0)
    }

    private fun failRequest(kanji: String, token: String): RecordsSchedulerModels.ReviewRequest {
        return RecordsSchedulerModels.ReviewRequest(kanji, token, "again", false, false, false, 0)
    }

    private fun settingsWithLadderThresholds(
        promotionDays: Int,
        failStreak: Int,
    ): RecordsSyncModels.Settings {
        val defaults = RecordsSyncModels.Settings.kikuDefaults()
        return RecordsSyncModels.Settings(
            defaults.modelName,
            defaults.templateName,
            defaults.expressionField,
            defaults.readingField,
            defaults.meaningField,
            defaults.sentenceField,
            defaults.frequencyField,
            defaults.frequencySortField,
            defaults.matureDays,
            defaults.matureSupportThreshold,
            defaults.suspendedRankMin,
            defaults.suspendedRankMax,
            defaults.activeQueueCap,
            defaults.newPerDay,
            defaults.writingTriggerMissDays,
            defaults.recognitionPromotionPasses,
            defaults.realDueReviewsToMove,
            defaults.importActiveCards,
            defaults.importSuspendedCards,
            defaults.importTaggedCards,
            defaults.importTags,
            defaults.importWeakCards,
            defaults.importWeakFsrsDifficultyThreshold,
            defaults.importWeakLapsesThreshold,
            defaults.importMinMatchingCardsPerKanji,
            defaults.importBrowserQueryCards,
            defaults.importBrowserQuery,
            defaults.newCardSortMode,
            promotionDays,
            failStreak,
        )
    }

    private fun dueWithToken(
        item: RecordsStudyModels.StudyItem,
        token: String,
        dueAtMillis: Long,
    ): RecordsStudyModels.StudyItem {
        return item.copyBuilder()
            .activeToken(token)
            .dueAtMillis(dueAtMillis)
            .build()
    }

    private fun persistSetting(key: String, value: String) {
        val db = store.writableDatabase
        val cv = ContentValues()
        cv.put("key", key)
        cv.put("value", value)
        cv.put("updated_at", System.currentTimeMillis())
        db.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }
}
