package dev.bee.kanjianki.data

import android.os.SystemClock
import dev.bee.kanjianki.core.SettingValuePolicy
import java.util.Collections
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

internal class SettingsRepository(
    private val storage: SettingsStorage,
    private val diagnosticLogger: DiagnosticLogger = NoOpDiagnosticLogger,
) {
    private val cacheLock = Any()

    @Volatile
    private var cachedValues: CachedValues? = null
    private val lastLoggedHitGeneration = AtomicLong(-1L)

    fun getInt(key: String?, fallback: Int): Int {
        return value(key)?.let { SettingValuePolicy.parseInt(it, fallback) } ?: fallback
    }

    fun getLong(key: String?, fallback: Long): Long {
        return value(key)?.let { SettingValuePolicy.parseLong(it, fallback) } ?: fallback
    }

    fun getString(key: String?, fallback: String?): String? {
        return value(key) ?: fallback
    }

    fun getDouble(key: String?, fallback: Double): Double {
        return value(key)?.let { SettingValuePolicy.parseDouble(it, fallback) } ?: fallback
    }

    fun putInt(key: String?, value: Int) {
        put(key, value.toString())
    }

    fun putLong(key: String?, value: Long) {
        put(key, value.toString())
    }

    fun putString(key: String?, value: String?) {
        put(key, value ?: "")
    }

    fun putDouble(key: String?, value: Double) {
        put(key, String.format(Locale.ROOT, "%.4f", value))
    }

    fun put(key: String?, value: String?) {
        // Never hold cacheLock across storage I/O. Settings groups acquire SQLite's write
        // transaction before reaching this method, so taking cacheLock first here would invert
        // the lock order and could deadlock a concurrent standalone write.
        storage.put(key, value)
        invalidate()
    }

    /** Invalidates snapshots after a transaction writes the settings table directly. */
    fun invalidate() {
        invalidateAllRepositories()
        synchronized(cacheLock) {
            cachedValues = null
        }
    }

    private fun value(key: String?): String? {
        // A bulk snapshot is process-visible, so it must never contain uncommitted values. A
        // transaction-owning thread instead reads its own SQLite connection directly, which also
        // preserves read-after-write semantics for reminder counters and other grouped settings.
        if (key == null || storage.isTransactionOwner()) {
            return storage.get(key)
        }
        val snapshot = valuesSnapshot()
        return if (snapshot != null) snapshot[key] else storage.get(key)
    }

    /**
     * Loads the complete settings table once per repository generation. The generation is shared
     * across LocalStore instances, so a receiver/maintenance store write invalidates an Activity
     * store snapshot too. A write racing the bulk query forces a retry rather than publishing a
     * mixed or stale point-in-time map.
     */
    private fun valuesSnapshot(): Map<String, String>? {
        while (true) {
            val generationBeforeLoad = cacheGeneration.get()
            cachedValues?.takeIf { it.generation == generationBeforeLoad }?.let {
                logCacheHitOnce(it)
                return it.values
            }

            val captureTiming = diagnosticLogger.isCapturing()
            val startedAtNanos = if (captureTiming) monotonicNanos() else 0L
            // The query may block on SQLite and therefore deliberately runs outside cacheLock.
            val loaded = storage.getAll() ?: return null
            val immutable = Collections.unmodifiableMap(LinkedHashMap(loaded))
            var publishedFromStorage = false
            val accepted = synchronized(cacheLock) {
                val generationAfterLoad = cacheGeneration.get()
                if (generationBeforeLoad != generationAfterLoad) {
                    null
                } else {
                    cachedValues?.takeIf { it.generation == generationAfterLoad } ?: CachedValues(
                        generationAfterLoad,
                        immutable,
                    ).also {
                        cachedValues = it
                        publishedFromStorage = true
                    }
                }
            }
            if (accepted == null) {
                continue
            }
            if (captureTiming && publishedFromStorage) {
                diagnosticLogger.log(
                    String.format(
                        Locale.US,
                        "settings snapshot source=storage rows=%d duration_ms=%.2f",
                        accepted.values.size,
                        (monotonicNanos() - startedAtNanos) / 1_000_000.0,
                    ),
                )
            } else if (!publishedFromStorage) {
                logCacheHitOnce(accepted)
            }
            return accepted.values
        }
    }

    private fun logCacheHitOnce(values: CachedValues) {
        if (!diagnosticLogger.isCapturing()) {
            return
        }
        val previous = lastLoggedHitGeneration.get()
        if (previous == values.generation || !lastLoggedHitGeneration.compareAndSet(previous, values.generation)) {
            return
        }
        diagnosticLogger.log("settings snapshot source=cache rows=${values.values.size} duration_ms=0.00")
    }

    private data class CachedValues(
        val generation: Long,
        val values: Map<String, String>,
    )

    private companion object {
        val cacheGeneration = AtomicLong(0L)

        fun invalidateAllRepositories() {
            cacheGeneration.incrementAndGet()
        }

        fun monotonicNanos(): Long {
            return runCatching { SystemClock.elapsedRealtimeNanos() }.getOrDefault(System.nanoTime())
        }
    }
}
