package dev.bee.kanjianki.core

import java.util.Locale

/** Platform contract for live SQLite snapshots used by backup, export, and restore. */
object DatabaseBackupAvailabilityPolicy {
    const val MIN_SAFE_ANDROID_API: Int = 30

    enum class AvailabilityId {
        AVAILABLE,
        UNSUPPORTED_ANDROID_VERSION,
    }

    data class Availability(
        val id: AvailabilityId,
        val operationsAllowed: Boolean,
        val message: String?,
    )

    private const val JAPANESE_LANGUAGE = "ja"

    @JvmStatic
    fun forAndroidApi(apiLevel: Int): Availability {
        if (apiLevel >= MIN_SAFE_ANDROID_API) {
            return Availability(AvailabilityId.AVAILABLE, true, null)
        }
        return Availability(
            AvailabilityId.UNSUPPORTED_ANDROID_VERSION,
            false,
            localizedText(
                "Backup & restore requires Android 11 or later. On Android 8–10, " +
                    "Kani leaves your current data and existing backup files unchanged " +
                    "because this Android version cannot make the safe live snapshot " +
                    "required for recovery.",
                "バックアップと復元には Android 11 以降が必要です。Android 8〜10 では、" +
                    "安全な復旧に必要なスナップショットを作成できないため、現在のデータと" +
                    "既存のバックアップファイルは変更しません。",
            ),
        )
    }

    @JvmStatic
    fun unavailableActionMessage(): String = localizedText(
        "Backup & restore is unavailable on this Android version. " +
            "Your current data and existing backup files were not changed.",
        "この Android バージョンではバックアップと復元を利用できません。" +
            "現在のデータと既存のバックアップファイルは変更されていません。",
    )

    private fun localizedText(english: String, japanese: String): String {
        return if (Locale.getDefault().language == JAPANESE_LANGUAGE) japanese else english
    }
}
