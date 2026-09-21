package dev.bee.kanjianki.data.sql

import dev.bee.kanjianki.core.RecordsSyncModels

fun interface MigrationClock {
    fun nowMillis(): Long
}

data class MigrationDefaults(
    val settings: RecordsSyncModels.Settings = RecordsSyncModels.Settings.kikuDefaults(),
    val statsSourceVersionKey: String = "stats_source_version",
    val statsSourceVersion: Long = 1L,
    val downgradeSettingKey: String = "downgraded_from_version",
    val androidLegacyMigrationKey: String = "collection_source_binding.android_legacy_migration",
    val androidLegacyMigrationEligibleValue: String = "eligible",
    val successfulSyncStatus: String = "success",
)

data class MigrationContext(
    val clock: MigrationClock,
    val defaults: MigrationDefaults = MigrationDefaults(),
) {
    companion object {
        fun system(): MigrationContext =
            MigrationContext(clock = MigrationClock(System::currentTimeMillis))
    }
}
