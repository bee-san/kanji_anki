package dev.bee.kanjianki.core

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseBackupAvailabilityPolicyTest {
    @Test
    fun rejectsStockAndroidVersionsWithoutVacuumInto() {
        for (apiLevel in listOf(-1, 0, 26, 27, 28, 29)) {
            val availability = DatabaseBackupAvailabilityPolicy.forAndroidApi(apiLevel)

            assertEquals(
                "availability id at API $apiLevel",
                DatabaseBackupAvailabilityPolicy.AvailabilityId.UNSUPPORTED_ANDROID_VERSION,
                availability.id,
            )
            assertFalse("operations at API $apiLevel", availability.operationsAllowed)
            assertTrue("message at API $apiLevel", availability.message?.isNotBlank() == true)
        }
    }

    @Test
    fun acceptsAndroidElevenAndLater() {
        for (apiLevel in listOf(30, 35, Int.MAX_VALUE)) {
            val availability = DatabaseBackupAvailabilityPolicy.forAndroidApi(apiLevel)

            assertEquals(
                DatabaseBackupAvailabilityPolicy.AvailabilityId.AVAILABLE,
                availability.id,
            )
            assertTrue(availability.operationsAllowed)
            assertNull(availability.message)
        }
    }

    @Test
    fun unsupportedCopyExplainsPreservationInEnglishAndJapanese() {
        val originalLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.ENGLISH)
            assertEquals(
                "Backup & restore requires Android 11 or later. On Android 8–10, " +
                    "Kani leaves your current data and existing backup files unchanged " +
                    "because this Android version cannot make the safe live snapshot " +
                    "required for recovery.",
                DatabaseBackupAvailabilityPolicy.forAndroidApi(29).message,
            )
            assertEquals(
                "Backup & restore is unavailable on this Android version. " +
                    "Your current data and existing backup files were not changed.",
                DatabaseBackupAvailabilityPolicy.unavailableActionMessage(),
            )

            Locale.setDefault(Locale.JAPANESE)
            assertEquals(
                "バックアップと復元には Android 11 以降が必要です。Android 8〜10 では、" +
                    "安全な復旧に必要なスナップショットを作成できないため、現在のデータと" +
                    "既存のバックアップファイルは変更しません。",
                DatabaseBackupAvailabilityPolicy.forAndroidApi(29).message,
            )
        } finally {
            Locale.setDefault(originalLocale)
        }
    }
}
