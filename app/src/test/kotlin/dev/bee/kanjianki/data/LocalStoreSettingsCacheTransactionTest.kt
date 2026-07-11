package dev.bee.kanjianki.data

import android.content.Context
import androidx.core.database.sqlite.transaction
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LocalStoreSettingsCacheTransactionTest {
    private lateinit var context: Context
    private lateinit var firstStore: LocalStore
    private lateinit var secondStore: LocalStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
        firstStore = LocalStore(context)
        secondStore = LocalStore(context)
    }

    @After
    fun tearDown() {
        secondStore.close()
        firstStore.close()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @Test
    fun transactionOwnerReadsItsWritesWithoutLeakingThemAndRollbackRestoresCommittedValue() {
        val key = "transaction_cache_probe"
        firstStore.putStringSetting(key, "committed")
        assertEquals("committed", firstStore.getStringSetting(key, "missing"))
        assertEquals("committed", secondStore.getStringSetting(key, "missing"))

        try {
            firstStore.writableDatabase.transaction {
                firstStore.putStringSetting(key, "uncommitted")

                assertEquals("uncommitted", firstStore.getStringSetting(key, "missing"))
                assertEquals("committed", secondStore.getStringSetting(key, "missing"))
                throw RollbackProbe()
            }
            fail("transaction should have rolled back")
        } catch (_: RollbackProbe) {
            // Expected: androidx transaction rolls back when the block throws.
        }

        assertEquals("committed", firstStore.getStringSetting(key, "missing"))
        assertEquals("committed", secondStore.getStringSetting(key, "missing"))
    }

    @Test
    fun groupedSettingsCommitInvalidatesAnotherStoreSnapshot() {
        val before = secondStore.reminderSettings()
        assertEquals(false, before.enabled)

        firstStore.saveReminderSettings(LocalStoreBase.ReminderSettings(true, 9, 35))

        val after = secondStore.reminderSettings()
        assertEquals(true, after.enabled)
        assertEquals(9, after.hour)
        assertEquals(35, after.minute)
    }

    private class RollbackProbe : RuntimeException()
}
