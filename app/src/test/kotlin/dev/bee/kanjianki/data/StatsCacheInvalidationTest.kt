package dev.bee.kanjianki.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.core.RecordsBase
import dev.bee.kanjianki.core.RecordsSchedulerModels
import dev.bee.kanjianki.core.RecordsStudyModels
import dev.bee.kanjianki.core.RecordsSyncModels
import dev.bee.kanjianki.sync.SyncSettings
import org.junit.After
import org.junit.Assert.assertEquals
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
