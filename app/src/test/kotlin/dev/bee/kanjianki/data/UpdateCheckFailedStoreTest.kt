package dev.bee.kanjianki.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import dev.bee.kanjianki.core.HomeTextCopy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UpdateCheckFailedStoreTest {
    private lateinit var store: LocalStore

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        store = LocalStore(context)
    }

    @After
    fun tearDown() {
        store.close()
    }

    @Test
    fun recordsNetworkFailureTimestamp() {
        val now = 1_700_000_000_000L
        store.recordUpdateCheckFailed(now)
        assertEquals(now, store.updateCheckFailedAt())
    }

    @Test
    fun clearRemovesFailureTimestamp() {
        store.recordUpdateCheckFailed(1_700_000_000_000L)
        store.clearUpdateCheckFailed()
        assertEquals(0L, store.updateCheckFailedAt())
    }

    @Test
    fun failureWithin24HoursShowsLine() {
        val failedAt = System.currentTimeMillis() - (12L * 60 * 60 * 1000)
        store.recordUpdateCheckFailed(failedAt)
        val line = updateCheckFailedLineOrNull(store.updateCheckFailedAt(), System.currentTimeMillis())
        assertEquals(HomeTextCopy.updateCheckFailedLine(), line)
    }

    @Test
    fun failureOlderThan24HoursHidesLine() {
        val failedAt = System.currentTimeMillis() - (25L * 60 * 60 * 1000)
        store.recordUpdateCheckFailed(failedAt)
        val line = updateCheckFailedLineOrNull(store.updateCheckFailedAt(), System.currentTimeMillis())
        assertNull(line)
    }

    @Test
    fun noFailureShowsNoLine() {
        val line = updateCheckFailedLineOrNull(store.updateCheckFailedAt(), System.currentTimeMillis())
        assertNull(line)
    }

    private fun updateCheckFailedLineOrNull(failedAt: Long, now: Long): String? {
        val expiryMs = 24L * 60 * 60 * 1000
        return if (failedAt > 0L && (now - failedAt) < expiryMs) {
            HomeTextCopy.updateCheckFailedLine()
        } else {
            null
        }
    }
}
