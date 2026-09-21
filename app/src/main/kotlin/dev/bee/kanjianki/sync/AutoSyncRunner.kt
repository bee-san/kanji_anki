package dev.bee.kanjianki.sync

import dev.bee.kanjianki.application.SyncUseCases
import dev.bee.kanjianki.core.AutoSyncSchedulePolicy
import dev.bee.kanjianki.data.RecordSyncFailureCommand
import dev.bee.kanjianki.data.SettingsSnapshot
import dev.bee.kanjianki.syncapi.CollectionGateway
import dev.bee.kanjianki.platform.AppClock
import kotlinx.coroutines.runBlocking

internal class AutoSyncRunner(
    private val syncUseCases: SyncUseCases,
    private val gateway: CollectionGateway,
    private val autoSyncState: AutoSyncState,
    private val manualSyncFactory: ManualSyncFactory,
    clock: AppClock? = AppClock.systemClock(),
) {
    private val clock: AppClock = AppClock.orSystem(clock)

    fun run(): Result {
        return run(clock.nowMillis(), clock)
    }

    fun run(now: Long): Result {
        return run(now, AppClock { now })
    }

    private fun run(now: Long, syncClock: AppClock): Result {
        if (!autoSyncState.isEnabled()) {
            return Result.skipped("Daily sync is off.")
        }
        val latestSuccessfulSync = runBlocking {
            syncUseCases.loadStoredState().latestSuccessfulSyncAtMillis
        }
        if (latestSuccessfulSync != null &&
            latestSuccessfulSync >= AutoSyncSchedulePolicy.localDayStart(now)
        ) {
            return Result.skipped("The collection already synced today.")
        }
        val provider = gateway.status()
        if (!provider.isReady()) {
            autoSyncState.recordAttempt(now, false)
            runBlocking {
                syncUseCases.recordFailure(
                    RecordSyncFailureCommand(
                        now,
                        now,
                        "config_error",
                        "permanent",
                        provider.message,
                    ),
                )
            }
            return Result.failed(provider.message)
        }

        val settings = runBlocking { syncUseCases.loadSettings() }
        val sync = manualSyncFactory.create(settings, syncClock).run()
        if (!sync.skipped) {
            autoSyncState.recordAttempt(now, sync.success)
        }
        if (sync.success) {
            return Result.success(sync.message ?: "")
        }
        if (sync.skipped) {
            return if (sync.retryable) {
                Result.deferred(sync.message ?: "")
            } else {
                Result.skipped(sync.message ?: "")
            }
        }
        return if (sync.retryable) {
            Result.retryableFailure(sync.message ?: "")
        } else {
            Result.failed(sync.message ?: "")
        }
    }

    class Result private constructor(
        @JvmField val ran: Boolean,
        @JvmField val success: Boolean,
        @JvmField val message: String,
        @JvmField val retryable: Boolean,
    ) {
        companion object {
            @JvmStatic
            fun success(message: String): Result {
                return Result(true, true, message, false)
            }

            @JvmStatic
            fun failed(message: String): Result {
                return Result(true, false, message, false)
            }

            @JvmStatic
            fun retryableFailure(message: String): Result {
                return Result(true, false, message, true)
            }

            @JvmStatic
            fun deferred(message: String): Result {
                return Result(false, false, message, true)
            }

            @JvmStatic
            fun skipped(message: String): Result {
                return Result(false, false, message, false)
            }
        }
    }

    internal interface AutoSyncState {
        fun isEnabled(): Boolean

        fun recordAttempt(attemptedAt: Long, success: Boolean)
    }

    internal fun interface ManualSyncFactory {
        fun create(settings: SettingsSnapshot, clock: AppClock): ManualSyncEngine
    }
}
