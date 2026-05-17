package dev.bee.kanjianki.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KaniLocalDataResetCoordinatorTest {
    @Test
    fun defaultPolicyRejectsLegacyDatabaseBeforeRoomOpen() {
        val store = FakeDatabaseStore(existingFamilies = setOf(KaniRoomDatabase.LEGACY_DATABASE_NAME))
        val error = assertThrows(IllegalStateException::class.java) {
            KaniLocalDataResetCoordinator(KaniRoomDatabaseResetPolicy(), store).prepareForRoomOpen()
        }

        assertTrue(error.message.orEmpty().contains("legacy reset is not allowed"))
        assertEquals(emptyList<String>(), store.deletedFamilies)
        assertTrue(store.hasDatabaseFamily(KaniRoomDatabase.LEGACY_DATABASE_NAME))
    }

    @Test
    fun cleanRewriteDeletesLegacyDatabaseFamilyAndLeavesOtherDatabases() {
        val store = FakeDatabaseStore(
            existingFamilies = setOf(
                KaniRoomDatabase.LEGACY_DATABASE_NAME,
                KaniRoomDatabase.DATABASE_NAME,
                "unrelated.db",
            ),
        )

        val report = KaniLocalDataResetCoordinator(
            KaniRoomDatabaseResetPolicy.CLEAN_REWRITE,
            store,
        ).prepareForRoomOpen()

        assertTrue(report.resetRequired)
        assertEquals(listOf(KaniRoomDatabase.LEGACY_DATABASE_NAME), report.legacyDatabasesFound)
        assertEquals(listOf(KaniRoomDatabase.LEGACY_DATABASE_NAME), report.legacyDatabasesDeleted)
        assertEquals(listOf(KaniRoomDatabase.LEGACY_DATABASE_NAME), store.deletedFamilies)
        assertFalse(store.hasDatabaseFamily(KaniRoomDatabase.LEGACY_DATABASE_NAME))
        assertTrue(store.hasDatabaseFamily(KaniRoomDatabase.DATABASE_NAME))
        assertTrue(store.hasDatabaseFamily("unrelated.db"))
    }

    @Test
    fun roomSandboxAllowsLegacyDatabaseFamilyToRemainUntilRuntimeCutover() {
        val store = FakeDatabaseStore(existingFamilies = setOf(KaniRoomDatabase.LEGACY_DATABASE_NAME))

        val report = KaniLocalDataResetCoordinator(
            KaniRoomDatabaseResetPolicy.ROOM_SANDBOX_DURING_LEGACY_RUNTIME,
            store,
        ).prepareForRoomOpen()

        assertTrue(report.resetRequired)
        assertEquals(listOf(KaniRoomDatabase.LEGACY_DATABASE_NAME), report.legacyDatabasesFound)
        assertEquals(emptyList<String>(), report.legacyDatabasesDeleted)
        assertEquals(emptyList<String>(), store.deletedFamilies)
        assertTrue(store.hasDatabaseFamily(KaniRoomDatabase.LEGACY_DATABASE_NAME))
    }

    @Test
    fun cleanRewriteFailsWhenLegacyDatabaseFamilyCannotBeDeleted() {
        val store = FakeDatabaseStore(
            existingFamilies = setOf(KaniRoomDatabase.LEGACY_DATABASE_NAME),
            undeletableFamilies = setOf(KaniRoomDatabase.LEGACY_DATABASE_NAME),
        )

        val error = assertThrows(IllegalStateException::class.java) {
            KaniLocalDataResetCoordinator(
                KaniRoomDatabaseResetPolicy.CLEAN_REWRITE,
                store,
            ).prepareForRoomOpen()
        }

        assertTrue(error.message.orEmpty().contains("Unable to remove legacy Kani database"))
        assertEquals(listOf(KaniRoomDatabase.LEGACY_DATABASE_NAME), store.deletedFamilies)
        assertTrue(store.hasDatabaseFamily(KaniRoomDatabase.LEGACY_DATABASE_NAME))
    }

    @Test
    fun cleanRewriteNoopsWhenLegacyDatabaseIsAbsent() {
        val store = FakeDatabaseStore(existingFamilies = setOf(KaniRoomDatabase.DATABASE_NAME))

        val report = KaniLocalDataResetCoordinator(
            KaniRoomDatabaseResetPolicy.CLEAN_REWRITE,
            store,
        ).prepareForRoomOpen()

        assertFalse(report.resetRequired)
        assertEquals(emptyList<String>(), report.legacyDatabasesFound)
        assertEquals(emptyList<String>(), store.deletedFamilies)
        assertTrue(store.hasDatabaseFamily(KaniRoomDatabase.DATABASE_NAME))
    }

    private class FakeDatabaseStore(
        existingFamilies: Set<String>,
        private val undeletableFamilies: Set<String> = emptySet(),
    ) : KaniDatabaseStore {
        private val existing = existingFamilies.toMutableSet()
        val deletedFamilies = mutableListOf<String>()

        override fun hasDatabaseFamily(databaseName: String): Boolean =
            databaseName in existing

        override fun deleteDatabaseFamily(databaseName: String): Boolean {
            deletedFamilies += databaseName
            if (databaseName in undeletableFamilies) {
                return false
            }
            return existing.remove(databaseName)
        }
    }
}
