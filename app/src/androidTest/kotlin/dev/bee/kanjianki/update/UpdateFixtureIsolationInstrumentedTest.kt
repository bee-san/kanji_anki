package dev.bee.kanjianki.update

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.kanjianki.KaniTestDatabase
import dev.bee.kanjianki.KaniTestDeviceSettings
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.data.LocalStoreBase
import dev.bee.kanjianki.testing.DeviceRisk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Pins the fixture reset the update tests depend on.
 *
 * The device-risk suite failed with `An automatic no-route outage must not create a
 * Home retry nag expected:<0> but was:<1785864639226>`. Nothing was wrong with the
 * updater: an AUTOMATIC retryable failure correctly neither sets nor clears the
 * persisted retry flag, so the assertion only holds from a fresh device — and
 * `KaniTestDatabase.delete` does not produce one, because that flag is in
 * SharedPreferences rather than the database. A sibling test's MANUAL failure lit the
 * flag and it was still lit when the automatic test read it.
 *
 * The failure is order-dependent, which is the reason it is worth a test of its own
 * rather than only a fixed `@Before`. Run alone the automatic test passes; run after
 * its sibling it fails. So a green suite proved nothing about the isolation, and the
 * reset could be dropped later without anything going red until a shard order changed.
 * Each test here reproduces the leak explicitly and then asserts the reset closes it.
 */
@RunWith(AndroidJUnit4::class)
class UpdateFixtureIsolationInstrumentedTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        KaniTestDatabase.delete(context)
        KaniTestDeviceSettings.clearUpdateState(context)
    }

    @After
    fun tearDown() {
        KaniTestDatabase.delete(context)
        KaniTestDeviceSettings.clearUpdateState(context)
    }

    @Test
    @DeviceRisk
    fun theRetryFlagOutlivesADatabaseDeleteAndIsClearedByTheDeviceSettingsReset() {
        // The exact sequence the suite hit, in one test: a MANUAL offline check lights
        // the flag, the database is deleted as every `@Before` does, and the flag is
        // still there. That surviving value is what the automatic test then read as its
        // own result.
        LocalStore(context).use { store -> store.recordUpdateCheckFailed(1785864639226L) }
        assertTrue(LocalStore(context).use { it.updateCheckFailedAt() } > 0L)

        KaniTestDatabase.delete(context)

        assertEquals(
            "the retry flag is device-local, so deleting the database cannot reset it",
            1785864639226L,
            LocalStore(context).use { it.updateCheckFailedAt() },
        )

        KaniTestDeviceSettings.clearUpdateState(context)

        assertEquals(
            "clearUpdateState must leave the store reading as a first-run device",
            0L,
            LocalStore(context).use { it.updateCheckFailedAt() },
        )
    }

    @Test
    @DeviceRisk
    fun theRecordedCheckResultAlsoOutlivesADatabaseDeleteAndIsCleared() {
        // The same leak reaches the status string, which another test in the update
        // class asserts the first-run default of. Covered here so the reset is pinned
        // for the whole key group, not only the one key that happened to fail.
        LocalStore(context).use { store ->
            store.recordAutoUpdateResult(1L, "leaked result", "v9.9.9", "kani.apk", "leaked pending")
        }
        KaniTestDatabase.delete(context)

        val leaked = LocalStore(context).use { it.autoUpdateStatus() }
        assertEquals("leaked result", leaked.lastResult)

        KaniTestDeviceSettings.clearUpdateState(context)

        val reset = LocalStore(context).use { it.autoUpdateStatus() }
        assertEquals(
            "the first-run default must be restored, not a zeroed check",
            "No automatic update check has run yet.",
            reset.lastResult,
        )
        assertEquals("", reset.lastVersion)
    }

    @Test
    @DeviceRisk
    fun clearingUpdateStateLeavesUnrelatedDeviceSettingsAlone() {
        // The reset is per key group on purpose. Clearing the whole store would also
        // drop the legacy-migration marker and the user's unrelated device settings,
        // turning a test-isolation helper into a much broader reset than any caller
        // asked for.
        LocalStore(context).use { store ->
            store.saveReminderSettings(LocalStoreBase.ReminderSettings(true, 7, 30))
            store.recordUpdateCheckFailed(42L)
        }

        KaniTestDeviceSettings.clearUpdateState(context)

        LocalStore(context).use { store ->
            val reminder = store.reminderSettings()
            assertTrue(
                "an unrelated device setting must survive the update-state reset",
                reminder.enabled,
            )
            assertEquals(7, reminder.hour)
            assertEquals(0L, store.updateCheckFailedAt())
        }
    }
}
