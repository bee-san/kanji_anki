package dev.bee.kanjianki.sync

import android.content.Context
import dev.bee.kanjianki.anki.AnkiDroidGateway
import dev.bee.kanjianki.anki.CollectionGateway
import dev.bee.kanjianki.core.AutoSyncSchedulePolicy
import dev.bee.kanjianki.data.LocalStore
import dev.bee.kanjianki.time.AppClock

internal class AutoSyncRunner @JvmOverloads constructor(
    context: Context,
    private val store: LocalStore,
    private val gateway: CollectionGateway,
    clock: AppClock? = AppClock.systemClock(),
) {
    private val context: Context = context.applicationContext
    private val clock: AppClock = AppClock.orSystem(clock)

    fun run(): Result {
        return run(clock.nowMillis(), clock)
    }

    fun run(now: Long): Result {
        return run(now, AppClock { now })
    }

    private fun run(now: Long, syncClock: AppClock): Result {
        val settings = store.autoSyncSettings()
        if (!settings.enabled) {
            return Result.skipped("Daily sync is off.")
        }
        if (store.hasSuccessfulSyncSince(AutoSyncSchedulePolicy.localDayStart(now))) {
            return Result.skipped("AnkiDroid already synced today.")
        }
        if (gateway is AnkiDroidGateway) {
            val provider = gateway.status()
            if (!provider.canSync) {
                store.recordAutoSyncAttempt(now, false)
                store.saveFailedSync(now, now, "config_error", "permanent", provider.message)
                return Result.failed(provider.message)
            }
        }

        val sync = ManualSyncEngine(
            context,
            store,
            gateway,
            SyncSettings.fromStore(store),
            SyncProgress.NONE,
            syncClock,
            repairedWriteBackAuthorized = false,
        ).run()
        if (!sync.skipped) {
            store.recordAutoSyncAttempt(now, sync.success)
        }
        if (sync.success) {
            return Result.success(sync.message ?: "")
        }
        if (sync.skipped) {
            return Result.skipped(sync.message ?: "")
        }
        return Result.failed(sync.message ?: "")
    }

    class Result private constructor(
        @JvmField val ran: Boolean,
        @JvmField val success: Boolean,
        @JvmField val message: String,
    ) {
        companion object {
            @JvmStatic
            fun success(message: String): Result {
                return Result(true, true, message)
            }

            @JvmStatic
            fun failed(message: String): Result {
                return Result(true, false, message)
            }

            @JvmStatic
            fun skipped(message: String): Result {
                return Result(false, false, message)
            }
        }
    }
}
