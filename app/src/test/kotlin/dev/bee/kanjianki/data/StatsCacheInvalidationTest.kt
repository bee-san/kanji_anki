package dev.bee.kanjianki.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.AppliedReviewSnapshot
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.core.SyncSettings
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StatsCacheInvalidationTest {
    private lateinit var context: Context
    private lateinit var store: LocalStore
    private lateinit var cacheStore: StatsCacheStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        store = LocalStore(context)
        store.writableDatabase
        cacheStore = StatsCacheStore(store)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @Test
    fun saveReviewMarksStatsCacheDirty() {
        val before = sourceVersion()

        store.saveReview(reviewRequest("痛", "token-review"), "good", 2_000L)

        assertEquals(before + 1L, sourceVersion())
    }

    @Test
    fun replaceStudyItemsMarksStatsCacheDirty() {
        val before = sourceVersion()

        store.replaceStudyItems(listOf(studyItem("痛")))

        assertEquals(before + 1L, sourceVersion())
    }

    @Test
    fun saveStudyItemMarksStatsCacheDirty() {
        val before = sourceVersion()

        store.saveStudyItem(studyItem("弱"))

        assertEquals(before + 1L, sourceVersion())
    }

    @Test
    fun undoLastAppliedReviewRestoresStudyItemAndMarksStatsCacheDirty() {
        val before = studyItem("痛")
        val after = before.copyBuilder().totalReviews(2).build()

        store.saveStudyItem(after)
        store.saveReview(reviewRequest("痛", "token-review"), "good", 2_000L, before, after)
        val versionBeforeUndo = sourceVersion()

        val undone = store.undoLastAppliedReview(
            AppliedReviewSnapshot("token-review", before, after),
        )

        assertTrue(undone)
        assertStudyItemRestored(before, store.studyItemsForKanji(listOf("痛")).single())
        assertEquals(versionBeforeUndo + 1L, sourceVersion())
        val reviewRows = store.readableDatabase.query(
            LocalStoreBase.TABLE_REVIEW_LOG,
            arrayOf(LocalStoreBase.COLUMN_TOKEN),
            "${LocalStoreBase.COLUMN_TOKEN} = ?",
            arrayOf("token-review"),
            null,
            null,
            null,
        ).use { it.count }
        assertEquals(0, reviewRows)
        assertTrue(store.timelineForKanji("痛").events.none { it.dedupeKey == "review:token-review" })
    }

    @Test
    fun saveSuccessfulSyncMarksStatsCacheDirty() {
        val before = sourceVersion()

        store.saveSuccessfulSync(
            RecordsSyncModels.CollectionSnapshot(emptyList(), emptyList()),
            emptyList(),
            emptyList(),
            RecordsSyncModels.Settings.kikuDefaults(),
            1_000L,
            2_000L,
            null,
        )

        assertEquals(before + 1L, sourceVersion())
    }

    @Test
    fun ladderThresholdSettingChangesMarkStatsCacheDirty() {
        assertSettingDirty(SyncSettings.LADDER_PROMOTION_INTERVAL_DAYS_SETTING_KEY, 22)
        assertSettingDirty(SyncSettings.LADDER_DEMOTION_FAIL_STREAK_SETTING_KEY, 4)
        assertSettingDirty(SyncSettings.REAL_DUE_REVIEWS_TO_MOVE_SETTING_KEY, 5)
    }

    private fun assertSettingDirty(key: String, value: Int) {
        val before = sourceVersion()

        store.putIntSetting(key, value)

        assertEquals("$key should dirty stats cache", before + 1L, sourceVersion())
    }

    private fun sourceVersion(): Long = cacheStore.currentSourceVersion(store.readableDatabase)

    private fun assertStudyItemRestored(expected: RecordsStudyModels.StudyItem, actual: RecordsStudyModels.StudyItem) {
        assertEquals(expected.kanji, actual.kanji)
        assertEquals(expected.state, actual.state)
        assertEquals(expected.dueAtMillis, actual.dueAtMillis)
        assertEquals(expected.stability, actual.stability, 0.0)
        assertEquals(expected.difficulty, actual.difficulty, 0.0)
        assertEquals(expected.totalReviews, actual.totalReviews)
        assertEquals(expected.lapses, actual.lapses)
        assertEquals(expected.learningStep, actual.learningStep)
        assertEquals(expected.writingLevel, actual.writingLevel)
        assertEquals(expected.recognitionStage, actual.recognitionStage)
        assertEquals(expected.consecutiveFailedRecognitionDays, actual.consecutiveFailedRecognitionDays)
        assertEquals(expected.lastFailedRecognitionDayMillis, actual.lastFailedRecognitionDayMillis)
        assertEquals(expected.writingRemediationPending, actual.writingRemediationPending)
        assertEquals(expected.suppressedByTaskType, actual.suppressedByTaskType)
        assertEquals(expected.suppressedAtMillis, actual.suppressedAtMillis)
        assertEquals(expected.matureIntervalDays, actual.matureIntervalDays)
        assertEquals(expected.answerSignature, actual.answerSignature)
        assertEquals(expected.activeToken, actual.activeToken)
        assertEquals(expected.createdAtMillis, actual.createdAtMillis)
        assertEquals(expected.rung, actual.rung)
        assertEquals(expected.phase, actual.phase)
        assertEquals(expected.realPassStreak, actual.realPassStreak)
        assertEquals(expected.realAgainStreak, actual.realAgainStreak)
        assertEquals(expected.lastRealReviewDueAtMillis, actual.lastRealReviewDueAtMillis)
        assertEquals(expected.hasSimilarKanji, actual.hasSimilarKanji)
        assertEquals(expected.typingMeaningMemory.encode(), actual.typingMeaningMemory.encode())
        assertEquals(expected.meaningKanjiMemory.encode(), actual.meaningKanjiMemory.encode())
        assertEquals(expected.kanjiMeaningMemory.encode(), actual.kanjiMeaningMemory.encode())
        assertEquals(expected.fontMeaningMemory.encode(), actual.fontMeaningMemory.encode())
        assertEquals(expected.wordReadingMemory.encode(), actual.wordReadingMemory.encode())
        assertEquals(expected.writingRemediationMemory.encode(), actual.writingRemediationMemory.encode())
        assertEquals(expected.similarKanjiMemory.encode(), actual.similarKanjiMemory.encode())
    }

    private fun reviewRequest(kanji: String, token: String): RecordsSchedulerModels.ReviewRequest {
        return RecordsSchedulerModels.ReviewRequest(kanji, token, "good", false, true, false, 0)
    }

    private fun studyItem(kanji: String): RecordsStudyModels.StudyItem {
        return RecordsStudyModels.StudyItem(kanji, "review", 1_000L, 1.0, 2.0, 1, 0, 0, 0, "", 1_000L)
            .copyBuilder()
            .rung(RecordsBase.LadderRung.KANJI_MEANING)
            .phase(RecordsBase.SchedulerPhase.REVIEW)
            .activeToken("token-$kanji")
            .build()
    }
}
