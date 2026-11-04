package dev.bee.kanjianki.data.desktop

import dev.bee.kanjianki.backup.core.CrossPlatformRestorePlanner
import dev.bee.kanjianki.data.sql.SqlDatabase

/**
 * Applies a [CrossPlatformRestorePlanner.RestorePlan] to a freshly restored
 * desktop profile database. A backup from another host may carry device-local
 * settings rows (reminders, auto-sync/-update schedules, provider references)
 * in its `settings` table; on this host those rows describe the wrong device, so
 * this deletes them and lets the destination fall back to its own defaults. The
 * plan also decides whether provider projections must be revalidated before the
 * next sync — surfaced here for the caller to act on.
 *
 * The applier ([DesktopStagedRestoreApplier]) swaps the file before the database
 * opens; this runs once the profile is open, so it uses the ordinary write path.
 */
object DesktopCrossPlatformRestoreFinalizer {
    data class Outcome(
        val resetKeys: Set<String>,
        val requiresProviderRevalidation: Boolean,
    )

    /**
     * Reads the restored database's settings keys, plans the cross-platform
     * reset, deletes the device-local rows, and returns what happened. A
     * same-host restore of a clean backup deletes nothing.
     */
    suspend fun finalize(
        database: SqlDatabase,
        backupHost: CrossPlatformRestorePlanner.Host,
        destinationHost: CrossPlatformRestorePlanner.Host,
    ): Outcome {
        val keys = database.readSnapshot {
            val collected = ArrayList<String>()
            prepare("SELECT key FROM settings").use { statement ->
                statement.query().use { rows ->
                    while (rows.next()) {
                        collected.add(rows.row.text(0))
                    }
                }
            }
            collected
        }

        val plan = CrossPlatformRestorePlanner.plan(backupHost, destinationHost, keys)
        if (plan.keysToReset.isNotEmpty()) {
            database.write {
                prepare("DELETE FROM settings WHERE key = ?").use { statement ->
                    for (key in plan.keysToReset) {
                        statement.reset()
                        statement.clearBindings()
                        statement.bindText(1, key)
                        statement.execute()
                    }
                }
            }
        }
        return Outcome(
            resetKeys = plan.keysToReset,
            requiresProviderRevalidation = plan.requiresProviderRevalidation,
        )
    }
}
