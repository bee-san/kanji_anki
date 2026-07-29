package dev.bee.kanjianki.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BetaUpdateSettingsStoreTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(LocalStoreSchema.DB_NAME)
    }

    @Test
    fun betaUpdatesDefaultOffAndPersistWhenEnabled() {
        LocalStore(context).use { store ->
            assertFalse(store.betaUpdatesEnabled())
            store.saveBetaUpdatesEnabled(true)
            assertTrue(store.betaUpdatesEnabled())
        }

        LocalStore(context).use { reopened ->
            assertTrue(reopened.betaUpdatesEnabled())
        }
    }
}
