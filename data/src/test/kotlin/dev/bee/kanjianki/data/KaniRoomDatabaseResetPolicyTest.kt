package dev.bee.kanjianki.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class KaniRoomDatabaseResetPolicyTest {
    @Test
    fun roomDatabaseDoesNotReuseLegacyLocalStoreFile() {
        assertEquals("kanji_anki_room.db", KaniRoomDatabase.DATABASE_NAME)
        assertEquals("kanji_anki_simple.db", KaniRoomDatabase.LEGACY_DATABASE_NAME)
        assertNotEquals(KaniRoomDatabase.LEGACY_DATABASE_NAME, KaniRoomDatabase.DATABASE_NAME)
    }

    @Test
    fun resetPolicyClassifiesCurrentLegacyAndUnknownDatabaseNames() {
        val policy = KaniRoomDatabaseResetPolicy()

        assertEquals(
            KaniRoomDatabaseDisposition.CURRENT_ROOM_DATABASE,
            policy.classify("kanji_anki_room.db"),
        )
        assertEquals(
            KaniRoomDatabaseDisposition.LEGACY_LOCAL_STORE_DATABASE,
            policy.classify("kanji_anki_simple.db"),
        )
        assertEquals(
            KaniRoomDatabaseDisposition.UNKNOWN_DATABASE,
            policy.classify("other.db"),
        )
    }

    @Test
    fun resetPolicyRejectsAmbiguousDatabaseOwnership() {
        assertThrows(IllegalArgumentException::class.java) {
            KaniRoomDatabaseResetPolicy(
                roomDatabaseName = "kanji_anki_simple.db",
                legacyDatabaseNames = setOf("kanji_anki_simple.db"),
            )
        }
    }
}
