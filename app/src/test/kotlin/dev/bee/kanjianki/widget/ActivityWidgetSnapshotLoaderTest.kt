package dev.bee.kanjianki.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreSchema
import dev.bee.kanjianki.theme.KaniThemeChoice
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ActivityWidgetSnapshotLoaderTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        context.getDatabasePath(LocalStoreSchema.DB_NAME).deleteRecursively()
    }

    @Test
    fun missingDatabaseReturnsNotSetUp() {
        val snapshot = ActivityWidgetSnapshotLoader.load(context, NOW)

        assertEquals(ActivityWidgetState.NOT_SET_UP, snapshot.state)
        assertEquals(emptyList<Int>(), snapshot.last35DayCounts)
    }

    @Test
    fun validStoreEmitsExactlyThirtyFiveChronologicalBuckets() {
        LocalStore(context).use { store -> store.writableDatabase }

        val snapshot = ActivityWidgetSnapshotLoader.load(context, NOW)

        assertEquals(ActivityWidgetState.NO_HISTORY, snapshot.state)
        assertEquals(ActivityWidgetSnapshotLoader.HISTORY_DAYS, snapshot.last35DayCounts.size)
        assertEquals(List(ActivityWidgetSnapshotLoader.HISTORY_DAYS) { 0 }, snapshot.last35DayCounts)
        assertEquals(0, snapshot.reviewsToday)
        assertEquals(0, snapshot.last7DayTotal)
        assertEquals(0, snapshot.last35DayTotal)
        assertEquals(0, snapshot.bestStreakDays)
    }

    @Test
    fun storedThemeChoiceIsLoadedWithoutStudyData() {
        LocalStore(context).use { store ->
            store.putStringSetting(KaniThemeChoice.SETTING_KEY, KaniThemeChoice.DARK.storageKey)
        }

        val snapshot = ActivityWidgetSnapshotLoader.load(context, NOW)

        assertEquals(KaniThemeChoice.DARK, snapshot.themeChoice)
    }

    companion object {
        private const val NOW = 1_800_000_000_000L
    }
}
